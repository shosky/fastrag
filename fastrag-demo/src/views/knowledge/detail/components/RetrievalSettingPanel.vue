<script setup lang="ts">
import { ref, computed } from 'vue'
import type { RetrievalSettingConfig } from '@/types/knowledge'

const props = defineProps<{
  config: RetrievalSettingConfig
}>()

const emit = defineEmits<{
  (e: 'update:config', config: RetrievalSettingConfig): void
}>()

const localConfig = ref<RetrievalSettingConfig>({ ...props.config })

watch(() => props.config, (newConfig) => {
  localConfig.value = { ...newConfig }
}, { deep: true })

function emitUpdate() {
  emit('update:config', { ...localConfig.value })
}

function updateNum(key: keyof RetrievalSettingConfig, val: number | number[] | undefined) {
  const num = Array.isArray(val) ? val[0] : val
  if (num !== undefined) {
    ;(localConfig.value as any)[key] = num
    emitUpdate()
  }
}

function updateBool(key: keyof RetrievalSettingConfig, val: unknown) {
  ;(localConfig.value as any)[key] = !!val
  emitUpdate()
}

function updateStr(key: keyof RetrievalSettingConfig, val: unknown) {
  ;(localConfig.value as any)[key] = val
  emitUpdate()
}

const keywordWeight = computed(() => (1 - (localConfig.value.semanticWeight ?? 0.7)).toFixed(1))
</script>

<template>
  <el-form :model="localConfig" label-width="120px" class="retrieval-form">
    <!-- ===== 检索模式 ===== -->
    <el-form-item label="检索模式">
      <el-radio-group :model-value="localConfig.mode" @change="(v: any) => updateStr('mode', v)">
        <el-radio value="vector">向量搜索</el-radio>
        <el-radio value="fulltext">全文搜索</el-radio>
        <el-radio value="hybrid">混合搜索</el-radio>
      </el-radio-group>
      <div class="retrieval-form__hint">
        <template v-if="localConfig.mode === 'vector'">生成查询嵌入，搜索语义最相似的文本块</template>
        <template v-else-if="localConfig.mode === 'fulltext'">基于关键词索引，精确匹配检索</template>
        <template v-else>同时执行向量+全文检索，融合排序取最佳结果</template>
      </div>
    </el-form-item>

    <!-- ===== 基础参数 ===== -->
    <el-divider content-position="left">基础参数</el-divider>

    <el-form-item label="Top K">
      <el-input-number :model-value="localConfig.topK" :min="1" :max="100" @change="(v: number | undefined) => updateNum('topK', v)" />
      <span class="retrieval-form__suffix">返回最相似的 K 个结果</span>
    </el-form-item>

    <el-form-item label="相似度阈值">
      <el-input-number :model-value="localConfig.similarityThreshold ?? 0" :min="0" :max="1" :step="0.05" @change="(v: number | undefined) => updateNum('similarityThreshold', v)" />
      <span class="retrieval-form__suffix">低于此阈值的结果被过滤</span>
    </el-form-item>

    <el-form-item label="分数阈值">
      <el-switch :model-value="localConfig.enableScoreThreshold ?? false" @change="(v: any) => updateBool('enableScoreThreshold', v)" />
      <el-input-number
        v-if="localConfig.enableScoreThreshold"
        :model-value="localConfig.scoreThreshold ?? 0"
        :min="0" :max="1" :step="0.1"
        style="margin-left: 12px"
        @change="(v: number | undefined) => updateNum('scoreThreshold', v)"
      />
    </el-form-item>

    <el-form-item v-if="localConfig.mode === 'hybrid'" label="语义权重">
      <el-slider :model-value="localConfig.semanticWeight ?? 0.7" :min="0" :max="1" :step="0.1" style="width: 200px; margin-right: 12px" @change="(v: number | number[]) => { localConfig.semanticWeight = Array.isArray(v) ? v[0] : v; emitUpdate() }" />
      <span class="retrieval-form__suffix">语义 {{ (localConfig.semanticWeight ?? 0.7).toFixed(1) }} / 关键词 {{ keywordWeight }}</span>
    </el-form-item>

    <!-- ===== 检索预处理 ===== -->
    <el-divider content-position="left">检索预处理</el-divider>

    <el-form-item label="自动纠错">
      <el-switch :model-value="localConfig.enableAutoCorrection ?? true" @change="(v: any) => updateBool('enableAutoCorrection', v)" />
      <span class="retrieval-form__suffix">错别字纠正 + 拼音首字母匹配</span>
    </el-form-item>

    <el-form-item label="查询重写">
      <el-switch :model-value="localConfig.enableQueryRewrite ?? true" @change="(v: any) => updateBool('enableQueryRewrite', v)" />
      <span class="retrieval-form__suffix">口语化表述转换为规范术语</span>
    </el-form-item>

    <el-form-item label="图谱扩展">
      <el-switch :model-value="localConfig.enableGraphExpansion ?? true" @change="(v: any) => updateBool('enableGraphExpansion', v)" />
      <template v-if="localConfig.enableGraphExpansion">
        <el-input-number :model-value="localConfig.graphExpansionDepth ?? 1" :min="1" :max="2" style="margin-left: 12px" @change="(v: number | undefined) => updateNum('graphExpansionDepth', v)" />
        <span class="retrieval-form__suffix">深度</span>
        <el-input-number :model-value="localConfig.graphMaxEntities ?? 5" :min="1" :max="20" style="margin-left: 8px" @change="(v: number | undefined) => updateNum('graphMaxEntities', v)" />
        <span class="retrieval-form__suffix">最大实体</span>
      </template>
    </el-form-item>

    <el-form-item label="同义词联想">
      <el-switch :model-value="localConfig.enableSynonymExpansion ?? true" @change="(v: any) => updateBool('enableSynonymExpansion', v)" />
      <span class="retrieval-form__suffix">术语库同义词扩展查询</span>
    </el-form-item>

    <!-- ===== 重排序 ===== -->
    <el-divider content-position="left">重排序</el-divider>

    <el-form-item label="Rerank 模型">
      <el-switch :model-value="localConfig.enableRerank ?? false" @change="(v: any) => updateBool('enableRerank', v)" />
      <el-select
        v-if="localConfig.enableRerank"
        :model-value="localConfig.rerankModel ?? 'bge-reranker-v2-m3'"
        style="width: 180px; margin-left: 12px"
        @change="(v: any) => updateStr('rerankModel', v)"
      >
        <el-option label="bge-reranker-v2-m3" value="bge-reranker-v2-m3" />
        <el-option label="bge-reranker-base" value="bge-reranker-base" />
      </el-select>
    </el-form-item>

    <el-form-item label="LLM 重排序">
      <el-switch :model-value="localConfig.enableLLMRerank ?? false" @change="(v: any) => updateBool('enableLLMRerank', v)" />
      <span class="retrieval-form__suffix">大模型逐条评分，精度高但耗时较长</span>
    </el-form-item>

    <el-form-item label="MMR 多样性">
      <el-switch :model-value="localConfig.enableMMR ?? false" @change="(v: any) => updateBool('enableMMR', v)" />
      <template v-if="localConfig.enableMMR">
        <el-slider :model-value="localConfig.mmrLambda ?? 0.5" :min="0" :max="1" :step="0.1" style="width: 150px; margin-left: 12px" @change="(v: number | number[]) => { localConfig.mmrLambda = Array.isArray(v) ? v[0] : v; emitUpdate() }" />
        <span class="retrieval-form__suffix">多样性 {{ (1 - (localConfig.mmrLambda ?? 0.5)).toFixed(1) }} / 相关性 {{ (localConfig.mmrLambda ?? 0.5).toFixed(1) }}</span>
      </template>
    </el-form-item>

    <!-- ===== 多路召回 ===== -->
    <el-divider content-position="left">多路召回</el-divider>

    <el-form-item label="启用多路召回">
      <el-switch :model-value="localConfig.enableMultiRetrieval ?? false" @change="(v: any) => updateBool('enableMultiRetrieval', v)" />
      <span class="retrieval-form__suffix">多通道并行召回 + 融合排序</span>
    </el-form-item>

    <template v-if="localConfig.enableMultiRetrieval">
      <el-form-item label="向量召回数">
        <el-input-number :model-value="localConfig.vectorRecallCount ?? 20" :min="5" :max="100" @change="(v: number | undefined) => updateNum('vectorRecallCount', v)" />
      </el-form-item>

      <el-form-item label="全文召回数">
        <el-input-number :model-value="localConfig.fulltextRecallCount ?? 20" :min="5" :max="100" @change="(v: number | undefined) => updateNum('fulltextRecallCount', v)" />
      </el-form-item>

      <el-form-item label="图谱召回数">
        <el-input-number :model-value="localConfig.graphRecallCount ?? 10" :min="0" :max="50" @change="(v: number | undefined) => updateNum('graphRecallCount', v)" />
      </el-form-item>

      <el-form-item label="QA 对召回数">
        <el-input-number :model-value="localConfig.qaRecallCount ?? 5" :min="0" :max="50" @change="(v: number | undefined) => updateNum('qaRecallCount', v)" />
      </el-form-item>

      <el-form-item label="融合策略">
        <el-radio-group :model-value="localConfig.fusionStrategy ?? 'rrf'" @change="(v: any) => updateStr('fusionStrategy', v)">
          <el-radio value="rrf">RRF</el-radio>
          <el-radio value="weighted">加权</el-radio>
          <el-radio value="interleave">交错</el-radio>
        </el-radio-group>
        <div class="retrieval-form__hint">
          <template v-if="(localConfig.fusionStrategy ?? 'rrf') === 'rrf'">根据各通道排名倒数求和，适合排名敏感场景</template>
          <template v-else-if="(localConfig.fusionStrategy ?? 'rrf') === 'weighted'">向量 0.4 + 全文 0.3 + 图谱 0.2 + QA 0.1</template>
          <template v-else>轮流从各通道取结果，保证各通道均有代表</template>
        </div>
      </el-form-item>
    </template>

    <!-- ===== 上下文组装 ===== -->
    <el-divider content-position="left">上下文组装</el-divider>

    <el-form-item label="组装策略">
      <el-radio-group :model-value="localConfig.contextAssemblyStrategy ?? 'concat'" @change="(v: any) => updateStr('contextAssemblyStrategy', v)">
        <el-radio value="concat">直接拼接</el-radio>
        <el-radio value="parent_document">父文档</el-radio>
        <el-radio value="window">窗口扩展</el-radio>
      </el-radio-group>
      <div class="retrieval-form__hint">
        <template v-if="(localConfig.contextAssemblyStrategy ?? 'concat') === 'concat'">直接拼接命中 chunk 的内容作为上下文</template>
        <template v-else-if="(localConfig.contextAssemblyStrategy ?? 'concat') === 'parent_document'">返回命中 chunk 所属的完整文档/段落</template>
        <template v-else>返回命中 chunk 及其前后各 N 个相邻 chunk</template>
      </div>
    </el-form-item>

    <el-form-item v-if="(localConfig.contextAssemblyStrategy ?? 'concat') === 'window'" label="窗口大小">
      <el-input-number :model-value="localConfig.contextWindowSize ?? 1" :min="1" :max="5" @change="(v: number | undefined) => updateNum('contextWindowSize', v)" />
      <span class="retrieval-form__suffix">前后各取 N 个相邻 chunk</span>
    </el-form-item>

    <el-form-item label="最大 Token">
      <el-input-number :model-value="localConfig.maxContextTokens ?? 4096" :min="512" :max="32768" :step="512" @change="(v: number | undefined) => updateNum('maxContextTokens', v)" />
      <span class="retrieval-form__suffix">上下文的总 token 上限</span>
    </el-form-item>

    <el-form-item label="排序方式">
      <el-radio-group :model-value="localConfig.contextOrder ?? 'relevance'" @change="(v: any) => updateStr('contextOrder', v)">
        <el-radio value="relevance">按相关性</el-radio>
        <el-radio value="document_order">按文档顺序</el-radio>
      </el-radio-group>
    </el-form-item>
  </el-form>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.retrieval-form {
  &__hint {
    font-size: 12px;
    color: $text-secondary;
    line-height: 1.5;
    margin-top: 4px;
  }

  &__suffix {
    font-size: 12px;
    color: $text-secondary;
    margin-left: 8px;
  }

  // el-divider 样式统一
  :deep(.el-divider__text) {
    font-size: 13px;
    font-weight: 600;
    color: $text-primary;
  }

  // el-radio 文字大小统一
  :deep(.el-radio) {
    margin-right: $spacing-lg;
  }
}
</style>
