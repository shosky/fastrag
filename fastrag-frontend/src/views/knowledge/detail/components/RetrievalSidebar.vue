<script setup lang="ts">
import type { RetrievalConfig } from '@/types/knowledge'
import { Check } from '@element-plus/icons-vue'

const props = defineProps<{
  config: RetrievalConfig
}>()

const emit = defineEmits<{
  (e: 'update:config', value: RetrievalConfig): void
  (e: 'save'): void
}>()

// --- Local config state（不再扩展 enableGraphSearch/enableRerank；
// 图谱扩展由 SearchTestPanel 内部 useGraphExpansion 永久启用并可视化） ---
const localConfig = ref<RetrievalConfig>({ ...props.config })

// Watch for external config changes
watch(
  () => props.config,
  (val) => {
    localConfig.value = { ...val }
  },
  { deep: true },
)

// --- Mode options ---
const modeOptions = [
  { label: '向量检索', value: 'vector' },
  { label: '全文检索', value: 'fulltext' },
  { label: '混合检索', value: 'hybrid' },
]

// --- Computed: show hybrid-only fields ---
const isHybridMode = computed(() => localConfig.value.mode === 'hybrid')

// --- Update config ---
function updateField<K extends keyof RetrievalConfig>(key: K, value: RetrievalConfig[K]) {
  localConfig.value[key] = value
  emit('update:config', { ...localConfig.value })
}

// --- Save ---
function handleSave() {
  emit('save')
}
</script>

<template>
  <div class="retrieval-sidebar">
    <!-- Header -->
    <div class="retrieval-sidebar__header">
      <div class="retrieval-sidebar__title-group">
        <h3 class="retrieval-sidebar__title">检索配置</h3>
        <el-button type="primary" size="small" @click="handleSave">
          <el-icon><Check /></el-icon>
          保存
        </el-button>
      </div>
      <p class="retrieval-sidebar__desc">调整当前知识库的检索参数。</p>
    </div>

    <!-- Body -->
    <div class="retrieval-sidebar__body">
      <!-- 检索模式 -->
      <div class="retrieval-sidebar__field">
        <label class="retrieval-sidebar__label">检索模式</label>
        <el-select
          :model-value="localConfig.mode"
          style="width: 100%"
          @update:model-value="(v: string) => updateField('mode', v as RetrievalConfig['mode'])"
        >
          <el-option
            v-for="opt in modeOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
        <span class="retrieval-sidebar__hint">选择检索模式</span>
      </div>

      <!-- 最终返回 Chunk 数 -->
      <div class="retrieval-sidebar__field">
        <label class="retrieval-sidebar__label">最终返回 Chunk 数</label>
        <el-input-number
          :model-value="localConfig.topK"
          :min="1"
          :max="100"
          :step="1"
          controls-position="right"
          style="width: 100%"
          @update:model-value="(v: number | undefined) => { if (v !== undefined) updateField('topK', v) }"
        />
        <span class="retrieval-sidebar__hint">重排序后返回给前端的文档数量</span>
      </div>

      <!-- 相似度阈值 -->
      <div class="retrieval-sidebar__field">
        <label class="retrieval-sidebar__label">相似度阈值 (0-1)</label>
        <el-input-number
          :model-value="localConfig.similarityThreshold"
          :min="0"
          :max="1"
          :step="0.1"
          :precision="2"
          controls-position="right"
          style="width: 100%"
          @update:model-value="(v: number | undefined) => { if (v !== undefined) updateField('similarityThreshold', v) }"
        />
        <span class="retrieval-sidebar__hint">过滤相似度低于此值的结果</span>
      </div>

      <!-- BM25 召回数量 -->
      <div class="retrieval-sidebar__field">
        <label class="retrieval-sidebar__label">BM25 召回数量</label>
        <el-input-number
          :model-value="localConfig.bm25RecallCount"
          :min="1"
          :max="200"
          :step="10"
          controls-position="right"
          style="width: 100%"
          @update:model-value="(v: number | undefined) => { if (v !== undefined) updateField('bm25RecallCount', v) }"
        />
        <span class="retrieval-sidebar__hint">BM25 全文检索和混合检索中的 BM25 候选数量</span>
      </div>

      <!-- Hybrid-only fields -->
      <template v-if="isHybridMode">
        <!-- 向量检索权重 -->
        <div class="retrieval-sidebar__field">
          <label class="retrieval-sidebar__label">向量检索权重</label>
          <el-input-number
            :model-value="localConfig.vectorWeight"
            :min="0"
            :max="1"
            :step="0.1"
            :precision="2"
            controls-position="right"
            style="width: 100%"
            @update:model-value="(v: number | undefined) => { if (v !== undefined) updateField('vectorWeight', v) }"
          />
          <span class="retrieval-sidebar__hint">混合检索中向量召回结果的融合权重</span>
        </div>

        <!-- BM25 权重 -->
        <div class="retrieval-sidebar__field">
          <label class="retrieval-sidebar__label">BM25 权重</label>
          <el-input-number
            :model-value="localConfig.bm25Weight"
            :min="0"
            :max="1"
            :step="0.1"
            :precision="2"
            controls-position="right"
            style="width: 100%"
            @update:model-value="(v: number | undefined) => { if (v !== undefined) updateField('bm25Weight', v) }"
          />
          <span class="retrieval-sidebar__hint">混合检索中 BM25 召回结果的融合权重</span>
        </div>

        <!-- BM25 稀疏项丢弃比例 -->
        <div class="retrieval-sidebar__field">
          <label class="retrieval-sidebar__label">BM25 稀疏项丢弃比例</label>
          <el-input-number
            :model-value="localConfig.bm25SparseDropRate"
            :min="0"
            :max="1"
            :step="0.1"
            :precision="2"
            controls-position="right"
            style="width: 100%"
            @update:model-value="(v: number | undefined) => { if (v !== undefined) updateField('bm25SparseDropRate', v) }"
          />
          <span class="retrieval-sidebar__hint">BM25 检索时丢弃低分稀疏项的比例，数值越大检索越快但可能降低召回</span>
        </div>
      </template>

      <!-- 关键词匹配 -->
      <el-divider style="margin: 12px 0" />
      <div class="retrieval-sidebar__field">
        <label class="retrieval-sidebar__label">关键词匹配</label>
        <el-switch
          :model-value="(localConfig as any).enableKeywordMatch ?? false"
          @update:model-value="(v: boolean) => { (localConfig as any).enableKeywordMatch = v; emit('update:config', { ...localConfig }) }"
        />
        <span class="retrieval-sidebar__hint">启用后，用户输入命中问答对的触发关键词时优先返回匹配结果</span>
      </div>
      <div class="retrieval-sidebar__field">
        <label class="retrieval-sidebar__label">QA 对召回数</label>
        <el-input-number
          :model-value="localConfig.qaRecallCount ?? 5"
          :min="0"
          :max="50"
          :step="1"
          controls-position="right"
          style="width: 100%"
          @update:model-value="(v: number | undefined) => { if (v !== undefined) updateField('qaRecallCount' as any, v) }"
        />
        <span class="retrieval-sidebar__hint">多路召回中问答对的最大召回数量</span>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.retrieval-sidebar {
  width: 320px;
  background: $bg-white;
  border-radius: $radius-base;
  box-shadow: $shadow-sm;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  height: fit-content;
  position: sticky;
  top: $spacing-base;

  // --- Header ---
  &__header {
    padding: $spacing-lg;
    border-bottom: 1px solid $border-lighter;
  }

  &__title-group {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: $spacing-xs;
  }

  &__title {
    margin: 0;
    font-size: 16px;
    font-weight: 600;
    color: $text-primary;
  }

  &__desc {
    margin: 0;
    font-size: 13px;
    color: $text-secondary;
    line-height: 1.5;
  }

  // --- Body ---
  &__body {
    padding: $spacing-lg;
    display: flex;
    flex-direction: column;
    gap: $spacing-base;
    max-height: calc(100vh - 300px);
    overflow-y: auto;
  }

  &__field {
    display: flex;
    flex-direction: column;
    gap: $spacing-xs;
  }

  &__label {
    font-size: 14px;
    font-weight: 500;
    color: $text-primary;
  }

  &__hint {
    font-size: 12px;
    color: $text-placeholder;
    line-height: 1.4;
  }
}
</style>
