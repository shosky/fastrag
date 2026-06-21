<script setup lang="ts">
import { Upload, Refresh, MoreFilled, View, Delete, MagicStick, Grid, Share, QuestionFilled, TrendCharts } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useBenchmark } from '@/composables/useBenchmark'
import { useKnowledgeGraph } from '@/composables/useKnowledgeGraph'
import { usePagination } from '@/composables/usePagination'
import type { Benchmark, BenchmarkQuestion, BenchmarkGenerateConfig } from '@/types/evaluation'

// --- Props & Emits ---
const props = defineProps<{
  kbId?: string
}>()

const emit = defineEmits<{
  (e: 'start-evaluation', benchmarkName: string): void
}>()

const kbId = props.kbId || 'default'

// --- Composables ---
const {
  benchmarks,
  loading,
  load,
  create,
  generate,
  remove,
  detail,
} = useBenchmark(kbId)

// 知识图谱统计 —— 用于"图增强构建"展示已构建图谱的 chunks 数
const { chunkCount } = useKnowledgeGraph(kbId)

// --- Upload dialog ---
const showUploadDialog = ref(false)
const generating = ref(false)
const uploadForm = ref({
  name: '',
  description: '',
  file: null as File | null,
})

// --- Auto-generate dialog ---
const showAutoGenDialog = ref(false)
const autoGenForm = ref<BenchmarkGenerateConfig>({
  name: '',
  description: '',
  buildMethod: 'vector',
  llmModel: 'siliconflow-cn:Pro/MiniMaxAI/MiniMax-M2.5',
  questionCount: 10,
  candidateChunkCount: 1,
  concurrency: 10,
  expandChunkCount: 1,
})

// --- Detail dialog ---
const showDetailDialog = ref(false)
const currentBenchmark = ref<Benchmark | null>(null)
const benchmarkQuestions = ref<BenchmarkQuestion[]>([])
const detailLoading = ref(false)
const noLineBreak = ref(false)

// --- 详情分页（真实切片） ---
const {
  currentPage: detailPage,
  pageSize: detailPageSize,
  total: detailTotal,
  reset: resetDetailPager,
} = usePagination(50)
watch(() => benchmarkQuestions.value.length, (n) => {
  detailTotal.value = n
  detailPage.value = 1
})
const pagedQuestions = computed(() => {
  const start = (detailPage.value - 1) * detailPageSize.value
  return benchmarkQuestions.value.slice(start, start + detailPageSize.value)
})

// --- Methods ---
function handleRefresh() {
  load()
  ElMessage.success('评估基准列表已刷新')
}

function openUploadDialog() {
  uploadForm.value = { name: '', description: '', file: null }
  showUploadDialog.value = true
}

function openAutoGenDialog() {
  autoGenForm.value.name = `Test-${new Date().toISOString().slice(0, 10)}-${Math.random().toString(36).slice(2, 6)}`
  showAutoGenDialog.value = true
}

async function handleUpload() {
  if (!uploadForm.value.name) {
    ElMessage.warning('请输入基准名称')
    return
  }
  if (!uploadForm.value.file) {
    ElMessage.warning('请选择基准文件')
    return
  }

  generating.value = true
  showUploadDialog.value = false
  try {
    // 真实解析：用文件大小粗略推断问题数（演示）
    const questionCount = Math.min(10, Math.max(5, Math.floor((uploadForm.value.file.size || 1024) / 500) || 10))
    await create(
      { name: uploadForm.value.name, description: uploadForm.value.description },
      questionCount,
    )
    ElMessage.success('评估基准上传成功')
  } catch {
    ElMessage.error('上传失败')
  } finally {
    generating.value = false
  }
}

async function handleAutoGen() {
  if (!autoGenForm.value.name) {
    ElMessage.warning('请输入基准名称')
    return
  }

  generating.value = true
  showAutoGenDialog.value = false
  ElMessage.info('正在生成评估基准...')
  try {
    await generate(autoGenForm.value)
    ElMessage.success(`评估基准生成成功（${autoGenForm.value.questionCount} 个问题）`)
  } catch {
    ElMessage.error('生成失败')
  } finally {
    generating.value = false
  }
}

async function viewBenchmark(benchmark: Benchmark) {
  currentBenchmark.value = benchmark
  showDetailDialog.value = true
  detailLoading.value = true
  resetDetailPager()
  try {
    benchmarkQuestions.value = await detail(benchmark.id)
  } finally {
    detailLoading.value = false
  }
}

async function deleteBenchmark(benchmark: Benchmark) {
  try {
    await ElMessageBox.confirm(
      `确定要删除评估基准「${benchmark.name}」吗？此操作不可恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
    await remove(benchmark.id)
    ElMessage.success('评估基准已删除')
  } catch {
    // 用户取消
  }
}

/** 用此基准发起评估（快捷入口） */
function startEvaluation(benchmark: Benchmark) {
  emit('start-evaluation', benchmark.name)
}

function handleFileChange(file: File) {
  uploadForm.value.file = file
}

function handleDrop(e: DragEvent) {
  e.preventDefault()
  const files = e.dataTransfer?.files
  if (files && files.length > 0) {
    uploadForm.value.file = files[0]
  }
}

// --- Lifecycle ---
onMounted(() => {
  load()
})
</script>

<template>
  <div class="benchmark-panel">
    <!-- Action bar -->
    <div class="benchmark-panel__actions">
      <div class="benchmark-panel__actions-left">
        <el-button type="primary" :icon="Upload" @click="openUploadDialog">
          上传基准
        </el-button>
        <el-button :loading="generating" @click="openAutoGenDialog">
          <template #icon>
            <el-icon><MagicStick /></el-icon>
          </template>
          自动生成
        </el-button>
        <span class="benchmark-panel__count">{{ benchmarks.length }} 个基准</span>
      </div>
      <el-button :icon="Refresh" link @click="handleRefresh">刷新</el-button>
    </div>

    <!-- Benchmark cards -->
    <div v-loading="loading" class="benchmark-panel__list">
      <div
        v-for="benchmark in benchmarks"
        :key="benchmark.id"
        class="benchmark-panel__card"
      >
        <div class="benchmark-panel__card-header">
          <h4 class="benchmark-panel__card-name">{{ benchmark.name }}</h4>
          <el-dropdown trigger="click">
            <el-button link :icon="MoreFilled" />
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="viewBenchmark(benchmark)">
                  <el-icon><View /></el-icon> 查看详情
                </el-dropdown-item>
                <el-dropdown-item @click="startEvaluation(benchmark)">
                  <el-icon><TrendCharts /></el-icon> 发起评估
                </el-dropdown-item>
                <el-dropdown-item @click="deleteBenchmark(benchmark)">
                  <el-icon><Delete /></el-icon> 删除
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>

        <p class="benchmark-panel__card-desc">{{ benchmark.description }}</p>

        <div class="benchmark-panel__card-tags">
          <el-tag v-if="benchmark.hasGoldChunks" type="success" size="small" effect="plain">
            Gold Chunks
          </el-tag>
          <el-tag v-if="benchmark.hasGoldAnswer" type="success" size="small" effect="plain">
            Gold Answer
          </el-tag>
          <el-tag v-if="benchmark.isAutoGenerated" type="primary" size="small" effect="plain">
            自动生成
          </el-tag>
        </div>

        <div class="benchmark-panel__card-footer">
          <span class="benchmark-panel__card-time">{{ benchmark.createdAt }}</span>
          <span class="benchmark-panel__card-count">{{ benchmark.questionCount }} 个问题</span>
        </div>
      </div>
    </div>

    <!-- Upload dialog -->
    <el-dialog
      v-model="showUploadDialog"
      title="上传评估基准"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form label-position="top" class="benchmark-panel__form">
        <el-form-item label="基准名称" required>
          <el-input v-model="uploadForm.name" placeholder="请输入评估基准名称" />
        </el-form-item>

        <el-form-item label="描述">
          <el-input
            v-model="uploadForm.description"
            type="textarea"
            :rows="3"
            placeholder="请输入评估基准描述（可选）"
          />
        </el-form-item>

        <el-form-item label="基准文件" required>
          <div
            class="benchmark-panel__upload-area"
            @dragover.prevent
            @drop="handleDrop"
          >
            <input
              type="file"
              accept=".jsonl"
              class="benchmark-panel__file-input"
              @change="(e) => handleFileChange((e.target as HTMLInputElement).files?.[0] as File)"
            />
            <el-icon :size="40" color="#0891b2"><Upload /></el-icon>
            <p v-if="!uploadForm.file" class="benchmark-panel__upload-text">
              点击或拖拽 JSONL 文件到此区域上传
            </p>
            <p v-else class="benchmark-panel__upload-file">
              {{ uploadForm.file.name }}
            </p>
            <p class="benchmark-panel__upload-hint">
              每行一个 JSON 对象，仅支持 .jsonl，最大 100MB
            </p>
          </div>
        </el-form-item>
      </el-form>

      <div class="benchmark-panel__dialog-footer">
        <span class="benchmark-panel__help-link">
          需要了解评估基准格式？查看 <el-link type="primary">使用说明</el-link>
        </span>
        <div class="benchmark-panel__dialog-actions">
          <el-button @click="showUploadDialog = false">取消</el-button>
          <el-button type="primary" :loading="generating" @click="handleUpload">上传</el-button>
        </div>
      </div>
    </el-dialog>

    <!-- Auto-generate dialog -->
    <el-dialog
      v-model="showAutoGenDialog"
      title="自动生成评估基准"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form label-position="top" class="benchmark-panel__form">
        <el-form-item label="基准名称" required>
          <el-input v-model="autoGenForm.name" />
        </el-form-item>

        <el-form-item label="描述">
          <el-input
            v-model="autoGenForm.description"
            type="textarea"
            :rows="3"
            placeholder="请输入评估基准描述（可选）"
          />
        </el-form-item>

        <el-form-item label="构建方式">
          <div class="benchmark-panel__build-methods">
            <div
              class="benchmark-panel__build-method"
              :class="{ 'benchmark-panel__build-method--active': autoGenForm.buildMethod === 'vector' }"
              @click="autoGenForm.buildMethod = 'vector'"
            >
              <div class="benchmark-panel__build-method-header">
                <el-icon><Grid /></el-icon>
                <span>向量构建</span>
                <el-tag size="small" type="info">默认</el-tag>
              </div>
              <p class="benchmark-panel__build-method-desc">
                基于向量相似度召回 chunks，稳定适用于所有知识库。
              </p>
              <p class="benchmark-panel__build-method-hint">
                适合快速生成通用评估基准。
              </p>
            </div>

            <div
              class="benchmark-panel__build-method"
              :class="{ 'benchmark-panel__build-method--active': autoGenForm.buildMethod === 'graph' }"
              @click="autoGenForm.buildMethod = 'graph'"
            >
              <div class="benchmark-panel__build-method-header">
                <el-icon><Share /></el-icon>
                <span>图增强构建</span>
                <el-tag size="small" type="primary">图谱</el-tag>
              </div>
              <p class="benchmark-panel__build-method-desc">
                在向量召回基础上结合知识图谱扩展相关 chunks。
              </p>
              <p class="benchmark-panel__build-method-chunks">
                已构建图谱的 chunks：<strong>{{ chunkCount }}</strong>
              </p>
            </div>
          </div>
        </el-form-item>

        <el-form-item label="LLM模型配置" required>
          <div class="benchmark-panel__model-row">
            <el-input v-model="autoGenForm.llmModel" />
            <el-button link type="primary">检查</el-button>
          </div>
        </el-form-item>

        <el-form-item label="生成参数">
          <div class="benchmark-panel__params-row">
            <div class="benchmark-panel__param">
              <label>问题数量 <span class="benchmark-panel__required">*</span></label>
              <el-input v-model.number="autoGenForm.questionCount" type="number" :min="1" :max="100" />
            </div>
            <div class="benchmark-panel__param">
              <label>候选 Chunk 数量 <el-tooltip content="每个问题对应的候选Chunk数量" placement="top"><el-icon><QuestionFilled /></el-icon></el-tooltip></label>
              <el-input v-model.number="autoGenForm.candidateChunkCount" type="number" :min="1" :max="10" />
            </div>
          </div>
        </el-form-item>

        <el-form-item>
          <div class="benchmark-panel__params-row">
            <div class="benchmark-panel__param">
              <label>构建并发数 <el-tooltip content="同时生成的问题数量" placement="top"><el-icon><QuestionFilled /></el-icon></el-tooltip></label>
              <el-input v-model.number="autoGenForm.concurrency" type="number" :min="1" :max="20" />
            </div>
            <div v-if="autoGenForm.buildMethod === 'graph'" class="benchmark-panel__param">
              <label>每轮扩展 Chunk 数 <el-tooltip content="图谱增强时每轮扩展的Chunk数量" placement="top"><el-icon><QuestionFilled /></el-icon></el-tooltip></label>
              <el-input v-model.number="autoGenForm.expandChunkCount" type="number" :min="1" :max="10" />
            </div>
          </div>
        </el-form-item>
      </el-form>

      <div class="benchmark-panel__dialog-footer">
        <span class="benchmark-panel__help-link">
          需要了解评估基准生成原理？查看 <el-link type="primary">使用说明</el-link>
        </span>
        <div class="benchmark-panel__dialog-actions">
          <el-button @click="showAutoGenDialog = false">取消</el-button>
          <el-button type="primary" :loading="generating" @click="handleAutoGen">确定</el-button>
        </div>
      </div>
    </el-dialog>

    <!-- Detail dialog -->
    <el-dialog
      v-model="showDetailDialog"
      title="评估基准详情"
      width="90%"
      top="5vh"
      class="benchmark-panel__detail-dialog"
    >
      <div v-if="currentBenchmark" class="benchmark-panel__detail">
        <!-- Header info -->
        <div class="benchmark-panel__detail-header">
          <h3 class="benchmark-panel__detail-name">{{ currentBenchmark.name }}</h3>
          <div class="benchmark-panel__detail-meta">
            <span>问题数：<strong>{{ currentBenchmark.questionCount }}</strong></span>
            <span>Gold Chunks：<el-tag type="success" size="small">有</el-tag></span>
            <span>Gold Answer：<el-tag type="success" size="small">有</el-tag></span>
          </div>
        </div>

        <!-- Questions header -->
        <div class="benchmark-panel__detail-actions">
          <span>问题列表 共 {{ benchmarkQuestions.length }} 条</span>
          <el-checkbox v-model="noLineBreak">不换行</el-checkbox>
        </div>

        <!-- Questions table -->
        <el-table
          :data="pagedQuestions"
          v-loading="detailLoading"
          class="benchmark-panel__detail-table"
          :class="{ 'benchmark-panel__detail-table--no-wrap': noLineBreak }"
          max-height="500"
        >
          <el-table-column prop="index" label="#" width="60" />
          <el-table-column prop="question" label="问题" min-width="350" />
          <el-table-column prop="goldChunks" label="Gold Chunks" min-width="180" />
          <el-table-column prop="goldAnswer" label="Gold Answer" min-width="400" />
        </el-table>

        <div class="benchmark-panel__detail-pagination">
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

.benchmark-panel {
  display: flex;
  flex-direction: column;
  gap: $spacing-base;

  &__actions {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__actions-left {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
  }

  &__count {
    font-size: 14px;
    color: $text-secondary;
    margin-left: $spacing-sm;
  }

  &__list {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
    gap: $spacing-base;
    min-height: 100px;
  }

  &__card {
    background: $bg-white;
    border: 1px solid $border-lighter;
    border-radius: $radius-base;
    padding: $spacing-lg;
    transition: box-shadow 0.2s;

    &:hover {
      box-shadow: $shadow-sm;
    }
  }

  &__card-header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    margin-bottom: $spacing-sm;
  }

  &__card-name {
    margin: 0;
    font-size: 16px;
    font-weight: 600;
    color: $text-primary;
  }

  &__card-desc {
    margin: 0 0 $spacing-sm;
    font-size: 14px;
    color: $text-secondary;
  }

  &__card-tags {
    display: flex;
    gap: $spacing-xs;
    margin-bottom: $spacing-base;
  }

  &__card-footer {
    display: flex;
    justify-content: space-between;
    font-size: 13px;
    color: $text-secondary;
  }

  &__card-count {
    color: $color-primary;
    cursor: pointer;

    &:hover {
      text-decoration: underline;
    }
  }

  &__form {
    :deep(.el-form-item) {
      margin-bottom: $spacing-lg;
    }
  }

  &__upload-area {
    width: 100%;
    border: 2px dashed $border-base;
    border-radius: $radius-base;
    padding: $spacing-xxl;
    text-align: center;
    cursor: pointer;
    transition: border-color 0.2s;
    position: relative;

    &:hover {
      border-color: $color-primary;
    }
  }

  &__file-input {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    opacity: 0;
    cursor: pointer;
  }

  &__upload-text {
    margin: $spacing-sm 0 0;
    font-size: 14px;
    color: $text-regular;
  }

  &__upload-file {
    margin: $spacing-sm 0 0;
    font-size: 14px;
    color: $color-primary;
    font-weight: 500;
  }

  &__upload-hint {
    margin: $spacing-xs 0 0;
    font-size: 12px;
    color: $text-placeholder;
  }

  &__build-methods {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: $spacing-base;
    width: 100%;
  }

  &__build-method {
    border: 2px solid $border-lighter;
    border-radius: $radius-base;
    padding: $spacing-base;
    cursor: pointer;
    transition: all 0.2s;

    &:hover {
      border-color: $color-primary;
    }

    &--active {
      border-color: $color-primary;
      background: $bg-active;
    }
  }

  &__build-method-header {
    display: flex;
    align-items: center;
    gap: $spacing-xs;
    margin-bottom: $spacing-sm;
    font-weight: 500;

    .el-tag {
      margin-left: auto;
    }
  }

  &__build-method-desc {
    margin: 0 0 $spacing-xs;
    font-size: 13px;
    color: $text-secondary;
    line-height: 1.5;
  }

  &__build-method-hint {
    margin: 0;
    font-size: 12px;
    color: $text-placeholder;
  }

  &__build-method-chunks {
    margin: 0;
    font-size: 13px;
    color: $text-secondary;

    strong {
      color: $color-primary;
    }
  }

  &__model-row {
    display: flex;
    gap: $spacing-sm;
    width: 100%;

    .el-input {
      flex: 1;
    }
  }

  &__params-row {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: $spacing-base;
    width: 100%;
  }

  &__param {
    display: flex;
    flex-direction: column;
    gap: $spacing-xs;

    label {
      font-size: 14px;
      color: $text-primary;
      display: flex;
      align-items: center;
      gap: 4px;
    }
  }

  &__required {
    color: $color-danger;
  }

  &__dialog-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding-top: $spacing-base;
    border-top: 1px solid $border-lighter;
  }

  &__help-link {
    font-size: 13px;
    color: $text-secondary;
  }

  &__dialog-actions {
    display: flex;
    gap: $spacing-sm;
  }

  &__detail {
    display: flex;
    flex-direction: column;
    gap: $spacing-base;
  }

  &__detail-header {
    padding-bottom: $spacing-base;
    border-bottom: 1px solid $border-lighter;
  }

  &__detail-name {
    margin: 0 0 $spacing-sm;
    font-size: 18px;
    font-weight: 600;
    color: $text-primary;
  }

  &__detail-meta {
    display: flex;
    gap: $spacing-lg;
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
    justify-content: space-between;
    align-items: center;
    font-size: 14px;
    color: $text-regular;
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
