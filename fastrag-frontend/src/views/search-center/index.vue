<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import * as api from '@/api'

/**
 * 知识搜索中心（业务管理）：独立的知识搜索查询与结果列表展示页。
 * 支持检索词联想、关键词判断提示、结果列表（相似度/高亮/预览）与结果点击使用。
 */
const loading = ref(false)
const kbs = ref<any[]>([])
const kbId = ref('')
const query = ref('')
const suggestions = ref<string[]>([])
const results = ref<any[]>([])
const hasSearched = ref(false)
const usedIds = ref<string[]>([])

onMounted(async () => {
  try {
    const res: any = await api.getKnowledgeBases()
    kbs.value = res?.list || res || []
    if (kbs.value.length) kbId.value = kbs.value[0].id
  } catch { kbs.value = [] }
})

// 检索词联想（后端 /query/suggest：纠错 + 标准问法推荐）
async function loadSuggestions() {
  if (!query.value.trim()) { suggestions.value = []; return }
  try {
    const res: any = await api.querySuggest(query.value)
    const list = typeof res === 'string' ? (res ? [res] : [])
      : [...(res?.alternatives || []), ...(res?.suggestedQuery && res.suggestedQuery !== query.value ? [res.suggestedQuery] : [])]
    suggestions.value = [...new Set(list)].slice(0, 5)
  } catch { suggestions.value = [] }
}

async function handleSearch(q?: string) {
  const term = (q || query.value).trim()
  if (!term) { ElMessage.warning('请输入搜索内容'); return }
  if (!kbId.value) { ElMessage.warning('请选择知识库'); return }
  query.value = term
  loading.value = true
  hasSearched.value = true
  try {
    results.value = await api.searchRetrieval({
      knowledgeId: kbId.value,
      query: term,
      config: { topK: 20, similarityThreshold: 0 },
    } as any)
  } catch { ElMessage.error('搜索失败，请检查后端服务') } finally { loading.value = false }
}

function useResult(row: any) {
  if (!usedIds.value.includes(row.fileId + ':' + row.chunkIndex)) usedIds.value.push(row.fileId + ':' + row.chunkIndex)
  ElMessage.success(`已使用该结果（累计使用 ${usedIds.value.length} 条）`)
}
</script>

<template>
  <div class="search-center">
    <h2 class="search-center__title">知识搜索</h2>

    <div class="search-center__bar">
      <el-select v-model="kbId" placeholder="知识库" style="width: 220px">
        <el-option v-for="k in kbs" :key="k.id" :label="k.name" :value="k.id" />
      </el-select>
      <el-input v-model="query" size="large" placeholder="手动输入搜索内容..." clearable
        @keyup.enter="handleSearch()" @input="loadSuggestions" />
      <el-button type="primary" size="large" :icon="Search" :loading="loading" @click="handleSearch()">搜索</el-button>
    </div>

    <!-- 检索词联想 -->
    <div v-if="suggestions.length" class="search-center__suggestions">
      <span class="label">联想：</span>
      <el-tag v-for="s in suggestions" :key="s" class="suggestion" @click="handleSearch(s)">{{ s }}</el-tag>
    </div>

    <!-- 结果列表 -->
    <el-card v-if="hasSearched" shadow="never">
      <template #header>
        <span>搜索结果（{{ results.length }}）</span>
      </template>
      <div v-loading="loading">
        <div v-for="(r, i) in results" :key="i" class="result-item">
          <div class="result-item__meta">
            <el-tag size="small" :type="r.fallback ? 'info' : 'success'">
              {{ r.fallback ? '推荐' : '相似度 ' + r.similarity.toFixed(2) }}
            </el-tag>
            <span class="source">来源：{{ r.fileId }} · 分片 {{ r.chunkIndex }}</span>
          </div>
          <div class="result-item__content" v-html="r.previewSnippet || r.content" />
          <div class="result-item__actions">
            <el-button link type="primary" size="small" @click="useResult(r)">点击使用</el-button>
          </div>
        </div>
        <el-empty v-if="!loading && !results.length" description="未找到相关内容" />
      </div>
    </el-card>
  </div>
</template>

<style lang="scss" scoped>
.search-center { padding: 16px 20px; max-width: 1000px; margin: 0 auto; }
.search-center__title { font-size: 20px; font-weight: 600; margin: 0 0 16px; }
.search-center__bar { display: flex; gap: 8px; margin-bottom: 12px; }
.search-center__suggestions {
  margin-bottom: 12px; font-size: 13px;
  .label { color: #909399; margin-right: 6px; }
  .suggestion { cursor: pointer; margin-right: 8px; }
}
.result-item {
  padding: 12px 0; border-bottom: 1px solid #ebeef5;
  &:last-child { border-bottom: none; }
  &__meta { display: flex; align-items: center; gap: 10px; margin-bottom: 6px; }
  &__meta .source { font-size: 12px; color: #909399; }
  &__content { font-size: 13px; color: #606266; line-height: 1.7;
    :deep(mark) { background: #fdf6ec; color: #e6a23c; padding: 0 2px; }
  }
  &__actions { margin-top: 4px; }
}
</style>
