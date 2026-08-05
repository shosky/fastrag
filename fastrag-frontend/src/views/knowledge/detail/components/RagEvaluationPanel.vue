<script setup lang="ts">
import { Refresh, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useEvaluation } from '@/composables/useEvaluation'
import { useBenchmark } from '@/composables/useBenchmark'
import { usePagination } from '@/composables/usePagination'
import type { Evaluation, EvaluationDetail } from '@/types/evaluation'
import { watch, onMounted, onUnmounted, computed, ref } from 'vue'
import * as api from '@/api'

// --- Props & Emits ---
const props = defineProps<{
  kbId?: string
  /** 预选基准 ID（来自基准面板"发起评估"快捷入口） */
  preselectBenchmark?: string
}>()

const emit = defineEmits<(e: 'consume-preselect') => void>()

const kbId = props.kbId || 'default'

// --- Composables ---
const {
  evaluations,
  latest,
  loading,
  running,
  load,
  start,
  remove,
  getCachedDetail,
  startPolling,
  stopPolling,
} = useEvaluation(kbId)

const { benchmarkOptions, load: loadBenchmarks } = useBenchmark(kbId)

// --- 列表分页（真实切片） ---
const {
  currentPage: listPage,
  pageSize: listPageSize,
  total: listTotal,
  handleCurrentChange: onListPageChange,
  handleSizeChange: onListSizeChange,
} = usePagination(10)
const pagedEvaluations = computed(() => {
  const startIdx = (listPage.value - 1) * listPageSize.value
  return evaluations.value.slice(startIdx, startIdx + listPageSize.value)
})

// 监听 evaluations 长度变化同步 total
watch(() => evaluations.value.length, (n) => {
  listTotal.value = n
})

// --- Start evaluation dialog ---
const showStartDialog = ref(false)
const evaluationConfig = ref({
  name: '',
  benchmark: '',
  answerModel: '',
  judgeModel: '',
  retrievalMode: 'hybrid',
  embeddingModel: '',
  enableRerank: false,
  rerankModel: '',
})

/** 从 API 加载各类型模型列表 */
const llmModelOptions = ref<{ label: string; value: string }[]>([])
const embeddingModelOptions = ref<{ label: string; value: string }[]>([])
const rerankModelOptions = ref<{ label: string; value: string }[]>([])

async function loadModelOptions() {
  try {
    const [llmRes, embedRes, rerankRes] = await Promise.all([
      api.getModels({ purpose: 'LLM' }).catch(() => []),
      api.getModels({ purpose: 'EMBEDDING' }).catch(() => []),
      api.getModels({ purpose: 'RERANK' }).catch(() => []),
    ])
    const toOpts = (res: any) => (res?.list || res || []).map((m: any) => ({
      label: m.name || m.code,
      value: m.code,
    }))
    llmModelOptions.value = toOpts(llmRes)
    embeddingModelOptions.value = toOpts(embedRes)
    rerankModelOptions.value = toOpts(rerankRes)
    // 默认选中第一个可用 LLM 模型
    if (llmModelOptions.value.length > 0 && !evaluationConfig.value.answerModel) {
      evaluationConfig.value.answerModel = llmModelOptions.value[0].value
    }
  } catch {
    // API 不可用时用空列表
  }
}

function genDefaultName() {
  return `eval-${new Date().toISOString().slice(0, 10).replace(/-/g, '')}-${Math.random().toString(36).slice(2, 8)}`
}

function openStartDialog() {
  evaluationConfig.value.name = genDefaultName()
  // 默认选第一个基准
  evaluationConfig.value.benchmark = benchmarkOptions.value[0]?.value || ''
  // 处理预选
  if (props.preselectBenchmark) {
    evaluationConfig.value.benchmark = props.preselectBenchmark
    emit('consume-preselect')
  }
  showStartDialog.value = true
}

async function handleStartEvaluation() {
  if (!evaluationConfig.value.benchmark) {
    ElMessage.warning('请选择评估基准')
    return
  }

  showStartDialog.value = false
  ElMessage.info('开始评估...')

  try {
      const payload = {
        name: evaluationConfig.value.name,
        benchmark: evaluationConfig.value.benchmark,
        answerModel: evaluationConfig.value.answerModel,
        judgeModel: evaluationConfig.value.judgeModel,
        retrievalMode: evaluationConfig.value.retrievalMode,
        embeddingModel: evaluationConfig.value.embeddingModel,
        enableRerank: evaluationConfig.value.enableRerank,
        rerankModel: evaluationConfig.value.rerankModel,
      }
      console.log('[Evaluation] Starting evaluation with config:', payload)
      const detail = await start(payload)
    // 开始轮询评估状态
    startPolling(detail.id)
    // API 返回的 KbEvaluation 不含 results，需补充空数组避免模板报错
    currentEvaluation.value = { ...detail, results: detail.results || [] }
    resetDetailPager()
    showDetailDialog.value = true
    const scoreText = detail.overallScore != null ? `，综合评分 ${detail.overallScore}%` : ''
    ElMessage.success(`评估已启动${scoreText}，请稍候查看结果`)
  } catch {
    ElMessage.error('评估失败，请重试')
  }
}

// --- Evaluation detail dialog ---
const showDetailDialog = ref(false)
const currentEvaluation = ref<EvaluationDetail | null>(null)
const onlyShowErrors = ref(false)
const noLineBreak = ref(false)

// --- 详情分页（真实切片） ---
const {
  currentPage: detailPage,
  pageSize: detailPageSize,
  total: detailTotal,
  reset: resetDetailPager,
} = usePagination(10)
const filteredResults = computed(() => {
  if (!currentEvaluation.value) return []
  const results = currentEvaluation.value.results || []
  if (!onlyShowErrors.value) return results
  return results.filter((r) => !r.isCorrect)
})
watch(() => filteredResults.value.length, (n) => {
  detailTotal.value = n
  detailPage.value = 1
})

// 当列表中当前查看的评估状态变更时，同步更新详情弹窗
watch(evaluations, async (newEvals) => {
  if (!currentEvaluation.value || !showDetailDialog.value) return
  const updated = newEvals.find((e: any) => e.id === currentEvaluation.value!.id)
  if (!updated) return
  const oldStatus = currentEvaluation.value.status
  const newStatus = updated.status
  if (oldStatus !== newStatus && (newStatus === 'completed' || newStatus === 'failed')) {
    // 列表接口不含逐题 results：完成时拉取完整详情，弹框才能展示真实结果明细
    const full = await api.fetchEvaluationDetail(kbId, updated.id).catch(() => null)
    if (full) {
      currentEvaluation.value = full
      return
    }
    // 详情拉取失败时降级：用列表指标更新
    currentEvaluation.value = {
      ...currentEvaluation.value,
      status: newStatus,
      overallScore: updated.overallScore ?? currentEvaluation.value.overallScore,
      completedCount: updated.completedCount ?? currentEvaluation.value.completedCount,
      duration: updated.duration ?? currentEvaluation.value.duration,
      benchmarkCount: updated.benchmarkCount ?? currentEvaluation.value.totalQuestions,
      recallAt1: updated.recallAt1 ?? currentEvaluation.value.recallAt1,
      recallAt3: updated.recallAt3 ?? currentEvaluation.value.recallAt3,
      recallAt5: updated.recallAt5 ?? currentEvaluation.value.recallAt5,
      recallAt10: updated.recallAt10 ?? currentEvaluation.value.recallAt10,
      answerAccuracy: updated.answerAccuracy ?? currentEvaluation.value.answerAccuracy,
      // 如果列表项已有完整结果，直接使用
      results: updated.results || currentEvaluation.value.results,
    }
  }
})
const pagedResults = computed(() => {
  const startIdx = (detailPage.value - 1) * detailPageSize.value
  return filteredResults.value.slice(startIdx, startIdx + detailPageSize.value)
})

async function viewEvaluation(eval_: Evaluation) {
  // 优先从 localStorage 缓存取详情（仅评估仍在运行时有用）
  const cached = getCachedDetail(eval_.id)
  const isTerminal = eval_.status === 'completed' || eval_.status === 'failed'
  if (cached && !isTerminal) {
    currentEvaluation.value = cached
  } else if (!currentEvaluation.value || currentEvaluation.value.id !== eval_.id || isTerminal) {
    // 无缓存 / 评估已结束：用列表中的指标 + API 详情构造视图
    const detail = cached && isTerminal
      ? cached  // 缓存虽然过期，但总比空数据好（极少触发）
      : await api.fetchEvaluationDetail(kbId, eval_.id).catch(() => null)
    currentEvaluation.value = detail || {
      id: eval_.id,
      name: eval_.name,
      runId: eval_.runId,
      status: eval_.status === 'completed' ? '已完成' : eval_.status === 'running' ? '进行中' : eval_.status === 'failed' ? '失败' : '待处理',
      overallScore: eval_.overallScore,
      totalQuestions: eval_.benchmarkCount,
      completedCount: eval_.completedCount,
      duration: eval_.duration,
      recallAt1: 0,
      recallAt3: 0,
      recallAt5: 0,
      recallAt10: eval_.recallAt10 || 0,
      answerAccuracy: 0,
      results: [],
    }
  }
  resetDetailPager()
  onlyShowErrors.value = false
  showDetailDialog.value = true
}

async function deleteEvaluation(eval_: Evaluation) {
  try {
    await ElMessageBox.confirm(
      `确定要删除评估记录「${eval_.name}」吗？此操作不可恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
    await remove(eval_.id)
    ElMessage.success('评估记录已删除')
  } catch {
    // 用户取消
  }
}

function handleRefresh() {
  load()
  loadBenchmarks()
  ElMessage.success('评估记录已刷新')
}

function getStatusType(status: string) {
  switch (status) {
    case 'completed': return 'success'
    case 'running': return 'warning'
    case 'failed': return 'danger'
    default: return 'info'
  }
}

function getStatusText(status: string) {
  switch (status) {
    case 'completed': return '已完成'
    case 'running': return '进行中'
    case 'failed': return '失败'
    default: return '待处理'
  }
}

// --- Lifecycle ---
onMounted(async () => {
  await Promise.all([load(), loadBenchmarks(), loadModelOptions()])
  // 若外部预选了基准，自动打开开始评估对话框
  if (props.preselectBenchmark) {
    openStartDialog()
  }
})

onUnmounted(() => {
  stopPolling()
})
</script>

<template>
  <div class="rag-evaluation">
    <!-- Latest evaluation card -->
    <div class="rag-evaluation__latest">
      <div class="rag-evaluation__latest-header">
        <h3 class="rag-evaluation__section-title">最后一次评估</h3>
        <el-button type="primary" :loading="running" @click="openStartDialog">
          开始评估
        </el-button>
      </div>

      <div v-if="latest" class="rag-evaluation__latest-content">
        <!-- Score circle -->
        <div class="rag-evaluation__score-circle">
          <div class="rag-evaluation__score-value">{{ latest.overallScore != null ? (latest.overallScore * 100).toFixed(1) + '%' : '评估中' }}</div>
        </div>

        <!-- Evaluation info -->
        <div class="rag-evaluation__latest-info">
          <div class="rag-evaluation__latest-name">
            <span class="rag-evaluation__eval-name">{{ latest.name }}</span>
            <el-tag :type="getStatusType(latest.status)" size="small">
              {{ getStatusText(latest.status) }}
            </el-tag>
          </div>
          <div class="rag-evaluation__latest-meta">
            {{ latest.benchmark }} · {{ latest.createdAt }} · {{ latest.runId }}
          </div>
        </div>

        <!-- Metrics cards -->
        <div class="rag-evaluation__metrics">
          <div class="rag-evaluation__metric-card">
            <span class="rag-evaluation__metric-label">Recall@10</span>
            <span class="rag-evaluation__metric-value rag-evaluation__metric-value--green">
              {{ (latest.recallAt10 ?? 0).toFixed(3) }}
            </span>
          </div>
          <div class="rag-evaluation__metric-card">
            <span class="rag-evaluation__metric-label">耗时</span>
            <span class="rag-evaluation__metric-value">{{ latest.duration || '-' }}</span>
          </div>
          <div class="rag-evaluation__metric-card">
            <span class="rag-evaluation__metric-label">数据量</span>
            <span class="rag-evaluation__metric-value">
              {{ latest.completedCount ?? 0 }}/{{ latest.dataCount ?? 0 }}
            </span>
          </div>
          <div class="rag-evaluation__metric-card">
            <span class="rag-evaluation__metric-label">完成率</span>
            <span class="rag-evaluation__metric-value">
              {{ latest.dataCount ? Math.round(((latest.completedCount ?? 0) / latest.dataCount) * 100) : 0 }}%
            </span>
          </div>
        </div>
      </div>

      <div v-else class="rag-evaluation__empty">
        <el-empty description="还没有评估记录，点击「开始评估」生成第一份" />
      </div>
    </div>

    <!-- History records -->
    <div class="rag-evaluation__history">
      <div class="rag-evaluation__history-header">
        <h3 class="rag-evaluation__section-title">历史评估记录</h3>
        <el-button :icon="Refresh" link @click="handleRefresh">刷新</el-button>
      </div>

      <el-table :data="pagedEvaluations" v-loading="loading" class="rag-evaluation__table">
        <el-table-column prop="name" label="评估名称" min-width="180" />
        <el-table-column prop="benchmark" label="评估基准" min-width="180" />
        <el-table-column label="数据量" width="100" align="center">
          <template #default="{ row }">
            {{ row.completedCount }}/{{ row.dataCount }}
          </template>
        </el-table-column>
        <el-table-column prop="duration" label="耗时" width="100" align="center" />
        <el-table-column label="Recall@10" width="120" align="center">
          <template #default="{ row }">
            {{ (row.recallAt10 ?? 0).toFixed(3) }}
          </template>
        </el-table-column>
        <el-table-column label="综合评分" width="100" align="center">
          <template #default="{ row }">
            {{ row.overallScore != null ? (row.overallScore * 100).toFixed(1) + '%' : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="viewEvaluation(row as Evaluation)">
              查看
            </el-button>
            <el-button link type="danger" size="small" @click="deleteEvaluation(row as Evaluation)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="rag-evaluation__pagination">
        <el-pagination
          v-model:current-page="listPage"
          v-model:page-size="listPageSize"
          layout="total, prev, pager, next"
          :total="listTotal"
          @current-change="onListPageChange"
          @size-change="onListSizeChange"
        />
      </div>
    </div>

    <!-- Start evaluation dialog -->
    <el-dialog
      v-model="showStartDialog"
      title="配置本次评估"
      width="500px"
      :close-on-click-modal="false"
    >
      <div class="rag-evaluation__dialog-hint">
        选择评估基准与可选模型后开始评估
      </div>

      <el-form label-width="120px" class="rag-evaluation__form">
        <el-form-item label="评估名称：">
          <el-input v-model="evaluationConfig.name" maxlength="100" show-word-limit />
        </el-form-item>

        <el-form-item label="评估基准：">
          <el-select v-model="evaluationConfig.benchmark" style="width: 100%">
            <el-option
              v-for="opt in benchmarkOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="答案生成模型：">
          <el-select v-model="evaluationConfig.answerModel" style="width: 100%">
            <el-option
              v-for="opt in llmModelOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="答案评判模型：">
          <el-select
            v-model="evaluationConfig.judgeModel"
            placeholder="可选，留空则用规则评判"
            style="width: 100%"
            clearable
          >
            <el-option
              v-for="opt in llmModelOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="检索模式：">
          <el-select v-model="evaluationConfig.retrievalMode" style="width: 100%">
            <el-option label="混合检索（向量+全文）" value="hybrid" />
            <el-option label="向量检索" value="vector" />
            <el-option label="全文检索" value="fulltext" />
          </el-select>
        </el-form-item>

        <el-form-item label="向量化模型：">
          <el-select v-model="evaluationConfig.embeddingModel" clearable placeholder="可选，默认使用 Gateway" style="width: 100%">
            <el-option
              v-for="opt in embeddingModelOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="Rerank 重排序：">
          <el-switch v-model="evaluationConfig.enableRerank" />
        </el-form-item>

        <el-form-item v-if="evaluationConfig.enableRerank" label="Rerank 模型：">
          <el-select v-model="evaluationConfig.rerankModel" style="width: 100%">
            <el-option
              v-for="opt in rerankModelOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <div class="rag-evaluation__dialog-tip">
        <strong>评估流程说明：</strong><br>
        1. <strong>检索</strong> — 对每个问题在知识库中检索 top-K chunks（支持向量/混合/全文模式）<br>
        2. <strong>召回计算</strong> — 对比 goldChunks 计算 Recall@1/3/5/10<br>
        3. <strong>生成</strong> — 以检索结果为上下文，调用 LLM 生成答案<br>
        4. <strong>评判</strong> — LLM judge 或规则评判（数字/关键词匹配）答案正确性<br>
        5. <strong>聚合</strong> — overallScore = Recall@10 × 0.7 + AnswerAccuracy × 0.3
      </div>

      <template #footer>
        <el-button type="primary" class="rag-evaluation__start-btn" :loading="running" @click="handleStartEvaluation">
          开始评估
        </el-button>
      </template>
    </el-dialog>

    <!-- Evaluation detail dialog -->
    <el-dialog
      v-model="showDetailDialog"
      :title="`评估结果 - ${currentEvaluation?.name || ''}`"
      width="90%"
      top="5vh"
      class="rag-evaluation__detail-dialog"
    >
      <div v-if="currentEvaluation" class="rag-evaluation__detail">
        <!-- Header info -->
        <div class="rag-evaluation__detail-header">
          <div class="rag-evaluation__detail-meta">
            <span>运行ID：{{ currentEvaluation.runId }}</span>
            <span>状态：<el-tag type="success" size="small">{{ currentEvaluation.status }}</el-tag></span>
            <span>总体评分：<el-tag type="success" size="small">{{ ((currentEvaluation.overallScore ?? 0) * 100).toFixed(1) }}%</el-tag></span>
            <span>总问题数：{{ currentEvaluation.totalQuestions }}</span>
            <span>完成数：{{ currentEvaluation.completedCount }}</span>
            <span>总耗时：{{ currentEvaluation.duration }}</span>
          </div>
          <div class="rag-evaluation__detail-actions">
            <el-checkbox v-model="onlyShowErrors">仅查看错误</el-checkbox>
            <el-checkbox v-model="noLineBreak">不换行</el-checkbox>
          </div>
        </div>

        <!-- Metrics summary -->
        <div class="rag-evaluation__detail-metrics">
          <span>显示 {{ filteredResults.length }} 条结果（共 {{ (currentEvaluation.results || []).length }} 条）</span>
          <span>召回率(1): <strong class="rag-evaluation__metric--green">{{ (currentEvaluation.recallAt1 ?? 0).toFixed(3) }}</strong></span>
          <span>召回率(3): <strong class="rag-evaluation__metric--green">{{ (currentEvaluation.recallAt3 ?? 0).toFixed(3) }}</strong></span>
          <span>召回率(5): <strong class="rag-evaluation__metric--green">{{ (currentEvaluation.recallAt5 ?? 0).toFixed(3) }}</strong></span>
          <span>召回率(10): <strong class="rag-evaluation__metric--green">{{ (currentEvaluation.recallAt10 ?? 0).toFixed(3) }}</strong></span>
          <span>答案准确率：<strong class="rag-evaluation__metric--red">{{ ((currentEvaluation.answerAccuracy ?? 0) * 100).toFixed(1) }}%</strong></span>
        </div>

        <!-- Results table -->
        <el-table
          :data="pagedResults"
          class="rag-evaluation__detail-table"
          :class="{ 'rag-evaluation__detail-table--no-wrap': noLineBreak }"
          max-height="500"
        >
          <el-table-column prop="question" label="问题" min-width="300" />
          <el-table-column prop="generatedAnswer" label="生成答案" min-width="200" />
          <el-table-column prop="retrievalMetrics" label="检索指标" min-width="250" />
          <el-table-column label="答案评判" min-width="180">
            <template #default="{ row }">
              <el-tag :type="row.isCorrect ? 'success' : 'danger'" size="small">
                {{ row.isCorrect ? '正确' : '错误' }}
              </el-tag>
              <span class="rag-evaluation__judge-reason">{{ row.judgeReason }}</span>
            </template>
          </el-table-column>
        </el-table>

        <div class="rag-evaluation__detail-pagination">
          <el-pagination
            v-model:current-page="detailPage"
            v-model:page-size="detailPageSize"
            layout="total, prev, pager, next, sizes"
            :total="detailTotal"
            :page-sizes="[10, 20, 50]"
          />
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.rag-evaluation {
  display: flex;
  flex-direction: column;
  gap: $spacing-lg;

  &__section-title {
    margin: 0;
    font-size: 16px;
    font-weight: 600;
    color: $text-primary;
  }

  &__latest {
    background: $bg-white;
    border-radius: $radius-base;
    box-shadow: $shadow-sm;
    padding: $spacing-lg;
  }

  &__latest-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: $spacing-base;
  }

  &__latest-content {
    display: flex;
    align-items: center;
    gap: $spacing-lg;
    flex-wrap: wrap;
  }

  &__empty {
    padding: $spacing-xl 0;
  }

  &__score-circle {
    width: 80px;
    height: 80px;
    border-radius: 50%;
    background: linear-gradient(135deg, #4CAF50 0%, #8BC34A 100%);
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
  }

  &__score-value {
    font-size: 20px;
    font-weight: 700;
    color: #fff;
  }

  &__latest-info {
    flex: 1;
    min-width: 200px;
  }

  &__latest-name {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    margin-bottom: 4px;
  }

  &__eval-name {
    font-size: 16px;
    font-weight: 600;
    color: $text-primary;
  }

  &__latest-meta {
    font-size: 13px;
    color: $text-secondary;
  }

  &__metrics {
    display: flex;
    gap: $spacing-base;
    flex-wrap: wrap;
  }

  &__metric-card {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 4px;
    padding: $spacing-base $spacing-lg;
    background: $bg-hover;
    border-radius: $radius-base;
    min-width: 100px;
  }

  &__metric-label {
    font-size: 13px;
    color: $text-secondary;
  }

  &__metric-value {
    font-size: 18px;
    font-weight: 600;
    color: $text-primary;

    &--green {
      color: $color-success;
    }
  }

  &__history {
    background: $bg-white;
    border-radius: $radius-base;
    box-shadow: $shadow-sm;
    padding: $spacing-lg;
  }

  &__history-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: $spacing-base;
  }

  &__table {
    width: 100%;
  }

  &__pagination {
    display: flex;
    justify-content: flex-end;
    margin-top: $spacing-base;
  }

  &__dialog-hint {
    font-size: 14px;
    color: $text-secondary;
    margin-bottom: $spacing-lg;
  }

  &__form {
    :deep(.el-form-item) {
      margin-bottom: $spacing-base;
    }
  }

  &__dialog-tip {
    font-size: 13px;
    color: $color-primary;
    background: $bg-active;
    padding: $spacing-sm $spacing-base;
    border-radius: $radius-sm;
    margin-top: $spacing-base;
  }

  &__start-btn {
    width: 100%;
    height: 40px;
    font-size: 15px;
  }

  &__detail {
    display: flex;
    flex-direction: column;
    gap: $spacing-base;
  }

  &__detail-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    flex-wrap: wrap;
    gap: $spacing-base;
    padding-bottom: $spacing-base;
    border-bottom: 1px solid $border-lighter;
  }

  &__detail-meta {
    display: flex;
    flex-wrap: wrap;
    gap: $spacing-base;
    font-size: 14px;
    color: $text-regular;

    span {
      display: inline-flex;
      align-items: center;
      gap: 4px;
    }
  }

  &__detail-actions {
    display: flex;
    gap: $spacing-base;
  }

  &__detail-metrics {
    display: flex;
    flex-wrap: wrap;
    gap: $spacing-base;
    font-size: 14px;
    color: $text-regular;
    padding: $spacing-sm $spacing-base;
    background: $bg-hover;
    border-radius: $radius-base;

    span {
      display: inline-flex;
      align-items: center;
      gap: 4px;
    }
  }

  &__metric--green {
    color: $color-success;
  }

  &__metric--red {
    color: $color-danger;
  }

  &__judge-reason {
    margin-left: $spacing-xs;
    font-size: 12px;
    color: $text-secondary;
  }

  &__detail-table {
    &--no-wrap {
      :deep(.cell) {
        white-space: nowrap;
      }
    }
  }

  &__detail-pagination {
    display: flex;
    justify-content: flex-end;
    margin-top: $spacing-base;
  }
}
</style>
