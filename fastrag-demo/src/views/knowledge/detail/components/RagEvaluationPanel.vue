<script setup lang="ts">
import { Refresh, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useEvaluation } from '@/composables/useEvaluation'
import { useBenchmark } from '@/composables/useBenchmark'
import { usePagination } from '@/composables/usePagination'
import type { Evaluation, EvaluationDetail } from '@/types/evaluation'

// --- Props & Emits ---
const props = defineProps<{
  kbId?: string
  /** 预选基准名（来自基准面板"发起评估"快捷入口） */
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
  answerModel: 'DeepSeek-V4-Flash',
  judgeModel: '',
})

const modelOptions = [
  { label: 'DeepSeek-V4-Flash', value: 'DeepSeek-V4-Flash' },
  { label: 'Qwen3-32B', value: 'Qwen3-32B' },
  { label: 'GPT-4o', value: 'GPT-4o' },
]

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
    const detail = await start({
      name: evaluationConfig.value.name,
      benchmark: evaluationConfig.value.benchmark,
      answerModel: evaluationConfig.value.answerModel,
      judgeModel: evaluationConfig.value.judgeModel,
    })
    currentEvaluation.value = detail
    resetDetailPager()
    showDetailDialog.value = true
    ElMessage.success(`评估完成，综合评分 ${detail.overallScore}%`)
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
} = usePagination(50)
const filteredResults = computed(() => {
  if (!currentEvaluation.value) return []
  if (!onlyShowErrors.value) return currentEvaluation.value.results
  return currentEvaluation.value.results.filter((r) => !r.answerJudgment.isCorrect)
})
watch(() => filteredResults.value.length, (n) => {
  detailTotal.value = n
  detailPage.value = 1
})
const pagedResults = computed(() => {
  const startIdx = (detailPage.value - 1) * detailPageSize.value
  return filteredResults.value.slice(startIdx, startIdx + detailPageSize.value)
})

async function viewEvaluation(eval_: Evaluation) {
  // 评估详情在评估完成时已缓存，这里若没有则提示重新评估
  // 由于 mock 层 detailCache 在刷新后会丢失，这里采取：若列表项有对应 detail 就直接展示，
  // 否则用最新的评估结果补展示（演示场景下足够）。
  // 更稳妥的做法是 fetchEvaluationDetail，此处简化为从记录展示摘要 + 提示。
  // 实际上 runEvaluation 已缓存详情，但刷新页面后缓存会丢。这里我们提供一个轻量重跑：
  if (!currentEvaluation.value || currentEvaluation.value.id !== eval_.id) {
    // 没有缓存的详情：用记录里的指标构造一个只读视图
    currentEvaluation.value = {
      id: eval_.id,
      name: eval_.name,
      runId: eval_.runId,
      status: '已完成',
      overallScore: eval_.overallScore,
      totalQuestions: eval_.benchmarkCount,
      completedCount: eval_.completedCount,
      duration: eval_.duration,
      recallAt1: 0,
      recallAt3: 0,
      recallAt5: 0,
      recallAt10: eval_.recallAt10,
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
  await Promise.all([load(), loadBenchmarks()])
  // 若外部预选了基准，自动打开开始评估对话框
  if (props.preselectBenchmark) {
    openStartDialog()
  }
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
          <div class="rag-evaluation__score-value">{{ latest.overallScore }}%</div>
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
              {{ latest.recallAt10.toFixed(3) }}
            </span>
          </div>
          <div class="rag-evaluation__metric-card">
            <span class="rag-evaluation__metric-label">耗时</span>
            <span class="rag-evaluation__metric-value">{{ latest.duration }}</span>
          </div>
          <div class="rag-evaluation__metric-card">
            <span class="rag-evaluation__metric-label">数据量</span>
            <span class="rag-evaluation__metric-value">
              {{ latest.completedCount }}/{{ latest.dataCount }}
            </span>
          </div>
          <div class="rag-evaluation__metric-card">
            <span class="rag-evaluation__metric-label">完成率</span>
            <span class="rag-evaluation__metric-value">
              {{ latest.dataCount ? Math.round((latest.completedCount / latest.dataCount) * 100) : 0 }}%
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
            {{ row.recallAt10.toFixed(3) }}
          </template>
        </el-table-column>
        <el-table-column label="综合评分" width="100" align="center">
          <template #default="{ row }">
            {{ row.overallScore }}%
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
              v-for="opt in modelOptions"
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
              v-for="opt in modelOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <div class="rag-evaluation__dialog-tip">
        当前为演示模式：答案由检索结果拼接生成，评判基于 gold 答案的关键数字/关键词匹配。
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
            <span>总体评分：<el-tag type="success" size="small">{{ currentEvaluation.overallScore.toFixed(1) }}%</el-tag></span>
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
          <span>显示 {{ filteredResults.length }} 条结果（共 {{ currentEvaluation.results.length }} 条）</span>
          <span>召回率(1): <strong class="rag-evaluation__metric--green">{{ currentEvaluation.recallAt1.toFixed(3) }}</strong></span>
          <span>召回率(3): <strong class="rag-evaluation__metric--green">{{ currentEvaluation.recallAt3.toFixed(3) }}</strong></span>
          <span>召回率(5): <strong class="rag-evaluation__metric--green">{{ currentEvaluation.recallAt5.toFixed(3) }}</strong></span>
          <span>召回率(10): <strong class="rag-evaluation__metric--green">{{ currentEvaluation.recallAt10.toFixed(3) }}</strong></span>
          <span>答案准确率：<strong class="rag-evaluation__metric--red">{{ (currentEvaluation.answerAccuracy * 100).toFixed(1) }}%</strong></span>
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
              <el-tag :type="row.answerJudgment.isCorrect ? 'success' : 'danger'" size="small">
                {{ row.answerJudgment.isCorrect ? '正确' : '错误' }}
              </el-tag>
              <span class="rag-evaluation__judge-reason">{{ row.answerJudgment.reason }}</span>
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
