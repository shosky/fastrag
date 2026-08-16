<script setup lang="ts">
import type { KnowledgeFile } from '@/types/knowledge'
import {
  CHUNK_LENGTH_OPTIONS_PARENT_CHILD,
  DELIMITER_OPTIONS,
  PARENT_AGG_LEVEL_OPTIONS,
} from '@/types/knowledge'
import { ElMessage } from 'element-plus'
import * as api from '@/api'
import ChunkLengthSelect from './ChunkLengthSelect.vue'

/**
 * 文件分片策略设置对话框（字段与「解析策略管理」页各策略参数面板对齐）。
 * 切换分片方式时联动显示该方式的专属参数；保存后持久化为该文件的专属策略
 * （kb_parse_strategy.file_id 隐藏记录），并按新配置立即重新分片（ADR-0001）。
 *
 * 保存语义：以当前生效配置为基线合并（未在弹框暴露的字段如 embeddingModel/parse 层
 * 原样继承），避免保存后隐式回落默认值。解析方式沿用 default（按扩展名自动识别）。
 */
const props = defineProps<{
  kbId: string
  file: KnowledgeFile | null
  modelValue: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'submitted', fileId: string): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v),
})

type StrategyType = 'rule_fixed' | 'rule_recursive' | 'structure_aware' | 'semantic' | 'parent_child'

// ---- 表单 ----
const strategy = ref<StrategyType>('rule_fixed')
const chunkLength = ref(1000)
const overlap = ref(100)
const delimiters = ref<string[]>(['\n\n'])
const titlePrefix = ref(true)
const headingPath = ref(true)
const embeddingModel = ref('')
const semanticThreshold = ref(30)
const parentMaxChunkLength = ref(2000)
const parentAggLevel = ref('auto')

const submitting = ref(false)
const embedModels = ref<any[]>([])

const fileLocked = computed(() => props.file?.status === 'processing')
/** QA 模式文件不受分片策略影响，不开放设置（与后端守卫一致） */
const qaMode = computed(() => props.file?.processingMode === 'qa')

/** 分片方式选项：label 为名称，lengthLabel 为长度字段文案（与策略管理页一致） */
const STRATEGY_OPTIONS: Array<{ value: StrategyType; label: string; lengthLabel: string }> = [
  { value: 'rule_fixed', label: '固定长度', lengthLabel: '分片长度（字符数）' },
  { value: 'rule_recursive', label: '递归分隔符', lengthLabel: '最大块长度（字符数）' },
  { value: 'structure_aware', label: '结构感知', lengthLabel: '单块最大长度（字符数）' },
  { value: 'semantic', label: '语义切分', lengthLabel: '单块最大长度（字符数）' },
  { value: 'parent_child', label: '父子切片', lengthLabel: '子分片长度（字符数）' },
]

const lengthLabel = computed(
  () => STRATEGY_OPTIONS.find((o) => o.value === strategy.value)?.lengthLabel || '分片长度（字符数）',
)

/** 按分片方式联动显示的参数（与策略管理页各面板字段一致） */
const showOverlap = computed(() =>
  strategy.value === 'rule_fixed' || strategy.value === 'rule_recursive' || strategy.value === 'parent_child')
const showDelimiters = computed(() =>
  strategy.value === 'rule_fixed' || strategy.value === 'rule_recursive' || strategy.value === 'parent_child')
const showTitleSwitches = computed(() => strategy.value === 'structure_aware')
const showSemanticParams = computed(() => strategy.value === 'semantic')
const showParentParams = computed(() => strategy.value === 'parent_child')
const isParentChild = computed(() => strategy.value === 'parent_child')

/** 当前生效的 chunk 配置（回填与保存合并的基线，含文件级专属策略） */
const currentChunk = computed(() => props.file?.parseStrategyAdvanced?.chunk)

/**
 * 构造保存配置：以当前生效 advanced 为基线合并，弹框暴露的字段覆盖，
 * 未暴露字段（如 semantic 的 embeddingModel、parse 层 tableMode）原样继承。
 */
function buildAdvanced() {
  const base = { ...(props.file?.parseStrategyAdvanced || {}) }
  const chunk: Record<string, unknown> = { ...(currentChunk.value || {}) }
  chunk.strategy = strategy.value
  chunk.chunkLength = chunkLength.value
  if (showOverlap.value) chunk.overlap = overlap.value
  if (showDelimiters.value) chunk.delimiters = delimiters.value
  if (showTitleSwitches.value) {
    chunk.titlePrefix = titlePrefix.value
    chunk.headingPath = headingPath.value
  }
  if (showSemanticParams.value) {
    chunk.semanticThreshold = semanticThreshold.value
    chunk.embeddingModel = embeddingModel.value
  }
  if (showParentParams.value) {
    chunk.parentMaxChunkLength = parentMaxChunkLength.value
    chunk.parentAggLevel = parentAggLevel.value
  }
  return { ...base, chunk }
}

watch(() => props.modelValue, (v) => {
  if (v && props.file) {
    // 打开时回填当前生效的分片参数（含文件级专属策略）
    const c = currentChunk.value
    strategy.value = (c?.strategy as StrategyType) || 'rule_fixed'
    chunkLength.value = c?.chunkLength || 1000
    overlap.value = c?.overlap ?? 100
    delimiters.value = c?.delimiters?.length ? [...c.delimiters] : ['\n\n']
    titlePrefix.value = c?.titlePrefix ?? true
    headingPath.value = c?.headingPath ?? true
    embeddingModel.value = c?.embeddingModel || ''
    semanticThreshold.value = c?.semanticThreshold ?? 30
    parentMaxChunkLength.value = c?.parentMaxChunkLength ?? 2000
    parentAggLevel.value = c?.parentAggLevel || 'auto'
  }
})

/** 加载 Embedding 模型列表（语义切片用：对话模型不能用于向量化，须区分 purpose） */
async function loadModels() {
  if (embedModels.value.length > 0) return
  try {
    const res = await api.getModels({ purpose: 'EMBEDDING' }).catch(() => [])
    embedModels.value = (res as any)?.list || res || []
  } catch {
    // ignore
  }
}

watch(visible, (v) => {
  if (v) loadModels()
})

async function handleSubmit() {
  if (!props.file) return
  submitting.value = true
  try {
    // 保存为该文件的专属策略（持久化）并立即重新分片（后端原子完成）；
    // 解析方式传 default（按扩展名自动识别），与原解析行为一致，本对话框只改分片参数
    await api.saveFileStrategy(props.kbId, props.file.id, {
      name: '自定义',
      description: '文件自定义分片策略',
      parseMethod: 'default',
      extensions: ['.' + (props.file.extension || '').replace(/^\./, '')],
      advanced: buildAdvanced(),
    })
    ElMessage.success('已保存分片策略并提交重新分片任务')
    visible.value = false
    emit('submitted', props.file.id)
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    title="分片策略设置"
    width="620px"
    :close-on-click-modal="false"
    append-to-body
  >
    <div v-if="file" class="strategy-change">
      <div v-if="fileLocked" class="strategy-change__locked">
        文件正在处理中，请等待处理完成后再设置。
      </div>
      <div v-else-if="qaMode" class="strategy-change__locked">
        QA 模式文件不受分片策略影响，不支持设置分片策略。
      </div>

      <template v-else>
        <el-alert type="warning" :closable="false" class="strategy-change__alert">
          <template #title>
            保存后将按新配置重新分片：删除「{{ file.name }}」的现有分片与向量并重新分片、向量化（消耗嵌入调用）。
            原文件与 QA 对保留。
          </template>
        </el-alert>

        <div class="strategy-change__form">
          <label class="strategy-change__label">分片策略</label>
          <el-select v-model="strategy" style="width: 100%">
            <el-option
              v-for="o in STRATEGY_OPTIONS"
              :key="o.value"
              :label="o.label + '（' + o.value + '）'"
              :value="o.value"
            />
          </el-select>

          <!-- 长度：预设快捷选择（父子切片用专用预设），与管理页一致 -->
          <label class="strategy-change__label strategy-change__label-mt">{{ lengthLabel }}</label>
          <ChunkLengthSelect
            v-model="chunkLength"
            :options="isParentChild ? CHUNK_LENGTH_OPTIONS_PARENT_CHILD : undefined"
          />

          <div class="strategy-change__grid">
            <div v-if="showOverlap">
              <label class="strategy-change__label">重叠字符数</label>
              <el-input-number v-model="overlap" :min="0" :max="500" :step="10" style="width: 100%" />
            </div>
            <div v-if="showSemanticParams">
              <label class="strategy-change__label">Embedding 模型</label>
              <el-select v-model="embeddingModel" clearable placeholder="选择 Embedding 模型" style="width: 100%">
                <el-option label="不指定（使用知识库级 Embedding 模型）" value="" />
                <el-option
                  v-for="m in embedModels"
                  :key="m.id"
                  :label="`${m.name} (${m.brand || m.code})`"
                  :value="m.code || m.name"
                />
              </el-select>
            </div>
            <div v-if="showParentParams">
              <label class="strategy-change__label">父分片最大长度（字符数）</label>
              <el-input-number v-model="parentMaxChunkLength" :min="500" :max="10000" :step="500" style="width: 100%" />
            </div>
          </div>

          <template v-if="showSemanticParams">
            <label class="strategy-change__label strategy-change__label-mt">语义突变阈值</label>
            <div class="strategy-change__slider-row">
              <el-slider v-model="semanticThreshold" :min="0" :max="100" :step="1" :format-tooltip="(v: number) => (v / 100).toFixed(2)" />
              <span class="strategy-change__slider-value">{{ (semanticThreshold / 100).toFixed(2) }}</span>
            </div>
            <div class="strategy-change__hint">相似度低于此阈值时视为语义断点，值越大切分越细</div>
          </template>

          <template v-if="showParentParams">
            <label class="strategy-change__label strategy-change__label-mt">聚合层级</label>
            <el-radio-group v-model="parentAggLevel">
              <el-radio-button v-for="opt in PARENT_AGG_LEVEL_OPTIONS" :key="opt.value" :value="opt.value">
                {{ opt.label }}
              </el-radio-button>
            </el-radio-group>
            <div class="strategy-change__hint">按指定级别的标题将子分片聚合为父分片；「自动」由系统根据文档结构智能选择</div>
          </template>

          <template v-if="showTitleSwitches">
            <div class="strategy-change__label-mt strategy-change__switch-row">
              <el-switch v-model="titlePrefix" />
              <span>标题前缀：正文内嵌标题前缀（chunk 自包含章节上下文）</span>
            </div>
            <div class="strategy-change__switch-row">
              <el-switch v-model="headingPath" />
              <span>层级路径：生成层级路径元数据（如 第一章 &gt; 1.1 背景）</span>
            </div>
          </template>

          <template v-if="showDelimiters">
            <label class="strategy-change__label strategy-change__label-mt">
              {{ isParentChild ? '兜底分隔符（无结构文档按分隔符切分）' : '分隔符（按优先级排列）' }}
            </label>
            <el-select
              v-model="delimiters"
              multiple
              filterable
              allow-create
              placeholder="选择或输入分隔符"
              style="width: 100%"
            >
              <el-option v-for="opt in DELIMITER_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
            <div v-if="!isParentChild" class="strategy-change__hint">
              优先使用第一个分隔符切分，切出过长块时用下一个，以此类推
            </div>
          </template>

          <div class="strategy-change__hint">
            保存后为该文件创建专属分片策略（不出现在解析策略管理列表），仅影响此文件。
          </div>
        </div>
      </template>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button
        type="primary"
        :loading="submitting"
        :disabled="fileLocked || qaMode"
        @click="handleSubmit"
      >
        保存并重新分片
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.strategy-change {
  &__alert {
    margin-bottom: 16px;
  }

  &__locked {
    padding: 24px 0;
    text-align: center;
    color: var(--el-text-color-secondary);
  }

  &__form {
    margin-bottom: 8px;
  }

  &__grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 12px 16px;
    margin-top: 14px;
  }

  &__label {
    display: block;
    margin-bottom: 8px;
    font-size: 14px;
    color: var(--el-text-color-regular);
  }

  &__label-mt {
    margin-top: 14px;
  }

  &__slider-row {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  &__slider-value {
    font-size: 13px;
    color: var(--el-color-primary);
    min-width: 40px;
  }

  &__switch-row {
    display: flex;
    align-items: center;
    gap: 10px;
    margin: 8px 0;
    font-size: 13px;
    color: var(--el-text-color-regular);
  }

  &__hint {
    margin-top: 10px;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}
</style>
