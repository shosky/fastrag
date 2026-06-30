<script setup lang="ts">
import type { RetrievalConfig, RetrievalSettingConfig } from '@/types/knowledge'
import type { SearchResultItem } from '@/types/evaluation'
import { Search, Close, FolderOpened, Loading, Refresh, PriceTag, Upload, Clock, Delete, Picture } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { useGraphExpansion } from '@/composables/useGraphExpansion'
import { useSynonyms } from '@/composables/useSynonyms'
import { searchRetrieval } from '@/api'
import { autoCorrect as localAutoCorrect, rewriteQuery as localRewrite } from '@/services/query-preprocess'

const router = useRouter()

// --- Props & Emits ---
const props = defineProps<{
  config: RetrievalConfig
  kbId?: string
  retrievalSettings?: RetrievalSettingConfig
}>()

const emit = defineEmits<{
  (e: 'update:config', value: RetrievalConfig): void
  (e: 'save', config: RetrievalConfig): void
}>()

// --- 图谱扩展 & 同义词 ---
const { expandQuery: graphExpand, isExpanding, expandedQuery, expansionResult } = useGraphExpansion(props.kbId || '1')
const { matchedTerms, addedTerms, expandedQuery: synonymExpanded, expandQuery: synonymExpand, clear: clearSynonyms } = useSynonyms()

// --- 搜索状态 ---
const searchQuery = ref('')
const searchResults = ref<SearchResultItem[]>([])
const searchLoading = ref(false)
const hasSearched = ref(false)
const searchMode = ref<'text' | 'image'>('text')

// --- 纠错 ---
const correctionSuggestion = ref<string | null>(null)
const correctionReason = ref('')

// --- 重写 ---
const rewriteRules = ref<string[]>([])

// --- 图片检索（支持多图）---
interface ImageItem {
  file: File
  url: string
}

const imageItems = ref<ImageItem[]>([])
const imageInputRef = ref<HTMLInputElement | null>(null)
const maxImages = 5

function handleImageSelect(e: Event) {
  const input = e.target as HTMLInputElement
  const files = input.files
  if (!files) return

  for (const file of Array.from(files)) {
    if (!file.type.startsWith('image/')) continue
    if (file.size > 10 * 1024 * 1024) {
      ElMessage.warning(`「${file.name}」超过 10MB，已跳过`)
      continue
    }
    if (imageItems.value.length >= maxImages) {
      ElMessage.warning(`最多上传 ${maxImages} 张图片`)
      break
    }
    // 去重
    if (imageItems.value.some((i) => i.file.name === file.name && i.file.size === file.size)) continue
    imageItems.value.push({ file, url: URL.createObjectURL(file) })
  }

  if (imageItems.value.length > 0) searchMode.value = 'image'
  // 清空 input 以允许重复选择同一文件
  if (imageInputRef.value) imageInputRef.value.value = ''
}

function removeImage(index: number) {
  URL.revokeObjectURL(imageItems.value[index].url)
  imageItems.value.splice(index, 1)
  if (imageItems.value.length === 0) searchMode.value = 'text'
}

function clearAllImages() {
  imageItems.value.forEach((i) => URL.revokeObjectURL(i.url))
  imageItems.value = []
  searchMode.value = 'text'
}

// --- 检索预处理开关 ---
const preprocess = computed(() => ({
  autoCorrection: props.retrievalSettings?.enableAutoCorrection ?? true,
  queryRewrite: props.retrievalSettings?.enableQueryRewrite ?? true,
  graphExpansion: props.retrievalSettings?.enableGraphExpansion ?? true,
  synonymExpansion: props.retrievalSettings?.enableSynonymExpansion ?? true,
}))

// --- 搜索历史 ---
interface SearchHistoryItem {
  id: number
  query: string
  mode: 'text' | 'image'
  imageFileName?: string
  resultCount: number
  duration: number
  timestamp: string
}

const searchHistory = ref<SearchHistoryItem[]>([])
const historySeq = ref(0)
const showHistory = ref(false)

function addHistory(query: string, mode: 'text' | 'image', resultCount: number, duration: number) {
  searchHistory.value.unshift({
    id: ++historySeq.value,
    query: query || (imageItems.value.map((i) => i.file.name).join(', ') || ''),
    mode,
    imageFileName: imageItems.value.map((i) => i.file.name).join(', '),
    resultCount,
    duration,
    timestamp: new Date().toLocaleTimeString('zh-CN'),
  })
  // 只保留最近 50 条
  if (searchHistory.value.length > 50) {
    searchHistory.value = searchHistory.value.slice(0, 50)
  }
}

function handleHistoryClick(item: SearchHistoryItem) {
  searchQuery.value = item.query
  showHistory.value = false
  handleSearch()
}

async function clearHistory() {
  searchHistory.value = []
  ElMessage.success('历史记录已清空')
}

// --- 示例问题 ---
const exampleQuestions = ref([
  '小微企业申请ICT服务需要准备哪些材料？',
  '小微ICT业务的技术支持范围是什么？',
  '小微ICT业务与传统企业业务有什么区别？',
  '如何查询小微ICT业务的办理进度？',
  '小微ICT业务提供哪些主要产品和服务？',
  '小微ICT业务的服务对象包括哪些企业类型？',
  '小微ICT业务的合同期限和续约流程是什么？',
  '小微ICT业务的售后服务包括哪些内容？',
  '小微ICT业务的办理流程是怎样的？',
  '小微ICT业务的收费标准是怎样的？',
])

// --- 搜索 ---
async function handleSearch() {
  const isImageSearch = searchMode.value === 'image' && imageItems.value.length > 0
  if (!isImageSearch && !searchQuery.value.trim()) {
    ElMessage.warning('请输入检索内容或上传图片')
    return
  }

  let effectiveQuery = searchQuery.value

  // 图片检索：用文件名列表作为 query（mock 实现）
  if (isImageSearch) {
    const names = imageItems.value.map((i) => i.file.name).join(' ')
    effectiveQuery = `[图片] ${names}`
  }

  // 纠错（本地 mock）
  correctionSuggestion.value = null
  correctionReason.value = ''
  rewriteRules.value = []
  if (!isImageSearch && preprocess.value.autoCorrection) {
    const result = localAutoCorrect(searchQuery.value)
    if (result) {
      correctionSuggestion.value = result.corrected
      correctionReason.value = result.reason
      effectiveQuery = result.corrected
    }
  }

  // 查询重写（本地 mock）
  rewriteRules.value = []
  if (!isImageSearch && preprocess.value.queryRewrite) {
    const { rewritten, appliedRules } = localRewrite(effectiveQuery)
    if (rewritten !== effectiveQuery) {
      rewriteRules.value = appliedRules
      effectiveQuery = rewritten
    }
  }

  searchLoading.value = true
  hasSearched.value = true
  const startTime = Date.now()

  try {
    // 图谱扩展
    let graphExpandedQuery = effectiveQuery
    if (!isImageSearch && preprocess.value.graphExpansion) {
      graphExpandedQuery = await graphExpand(effectiveQuery)
    } else {
      expandedQuery.value = ''
    }

    // 同义词扩展
    let finalQuery = graphExpandedQuery
    if (!isImageSearch && preprocess.value.synonymExpansion) {
      finalQuery = await synonymExpand(graphExpandedQuery)
    } else {
      clearSynonyms()
    }

    searchResults.value = await searchRetrieval({
      knowledgeId: props.kbId || '1',
      query: finalQuery,
      config: props.config,
    })

    const duration = Date.now() - startTime
    addHistory(effectiveQuery, searchMode.value, searchResults.value.length, duration)
  } catch {
    ElMessage.error('检索失败，请重试')
    searchResults.value = []
  } finally {
    searchLoading.value = false
  }
}

// --- 配置变更自动重搜 ---
watch(
  () => props.config,
  () => {
    if (hasSearched.value && (searchQuery.value.trim() || imageItems.value.length > 0)) {
      handleSearch()
    }
  },
  { deep: true },
)

function handleClear() {
  searchResults.value = []
  hasSearched.value = false
  searchQuery.value = ''
  clearAllImages()
  clearSynonyms()
  correctionSuggestion.value = null
  correctionReason.value = ''
  rewriteRules.value = []
}

function handleExampleClick(question: string) {
  searchQuery.value = question
  searchMode.value = 'text'
  clearAllImages()
  handleSearch()
}

function handleRegenerate() {
  exampleQuestions.value = [...exampleQuestions.value].sort(() => Math.random() - 0.5)
  ElMessage.success('示例问题已刷新')
}

function handleSourceClick(result: SearchResultItem) {
  if (!props.kbId || !result.fileId) return
  router.push(`/knowledge/${props.kbId}/chunks/${result.fileId}`)
}

function highlightFull(result: SearchResultItem): string {
  if (!result.highlights || result.highlights.length === 0) return result.content
  let html = result.content
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
  const sorted = [...result.highlights].sort((a, b) => b.length - a.length)
  sorted.forEach((token) => {
    const escaped = token.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    const wrapped = '<mark>' + escaped + '</mark>'
    html = html.split(escaped).join(wrapped)
  })
  return html
}

function handleKeydown(e: Event | KeyboardEvent) {
  if (!(e instanceof KeyboardEvent)) return
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSearch()
  }
}
</script>

<template>
  <div class="search-test">
    <!-- 搜索输入区 -->
    <div class="search-test__input-wrapper">
      <div class="search-test__input-box">
        <!-- 多图预览条 -->
        <div v-if="imageItems.length > 0" class="search-test__images">
          <div v-for="(item, idx) in imageItems" :key="idx" class="search-test__image-item">
            <img :src="item.url" :alt="item.file.name" />
            <el-button :icon="Close" circle size="small" class="search-test__image-remove" @click="removeImage(idx)" />
            <span class="search-test__image-name">{{ item.file.name }}</span>
          </div>
          <div v-if="imageItems.length < maxImages" class="search-test__image-add" @click="imageInputRef?.click()">
            <el-icon><Upload /></el-icon>
          </div>
        </div>

        <el-input
          v-model="searchQuery"
          :placeholder="imageItems.length > 0 ? '补充文字描述（可选），Enter 检索' : '输入查询内容，支持文字和图片搜索，Enter 检索'"
          :prefix-icon="Search"
          size="large"
          class="search-test__input"
          @keydown="handleKeydown"
        />

        <!-- 图谱扩展中 -->
        <div v-if="isExpanding" class="search-test__expanding">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>正在利用知识图谱扩展查询...</span>
        </div>
        <div v-else-if="expandedQuery && expandedQuery !== searchQuery" class="search-test__expanded">
          <span class="search-test__expanded-label">扩展查询:</span>
          <span class="search-test__expanded-query">{{ expandedQuery }}</span>
        </div>

        <!-- 纠错提示 -->
        <div v-if="correctionSuggestion && correctionReason" class="search-test__correction">
          <span>{{ correctionReason }}</span>
          <el-button type="primary" link size="small" @click="correctionSuggestion = null">已采用</el-button>
        </div>

        <!-- 重写提示 -->
        <div v-if="rewriteRules.length > 0" class="search-test__rewrite">
          <el-icon><Refresh /></el-icon>
          <span>查询重写：</span>
          <span v-for="(rule, idx) in rewriteRules" :key="idx">
            <el-tag size="small" type="warning">{{ rule }}</el-tag>
            <span v-if="idx < rewriteRules.length - 1"> </span>
          </span>
        </div>

        <!-- 同义词联想 -->
        <div v-if="matchedTerms.length > 0 && !isExpanding" class="search-test__synonyms">
          <el-icon><PriceTag /></el-icon>
          <span>已联想：</span>
          <template v-for="(term, idx) in matchedTerms" :key="term">
            <strong>{{ term }}</strong>
            <span v-if="idx < matchedTerms.length - 1">、</span>
          </template>
          <span v-if="addedTerms.length > 0"> → {{ addedTerms.join('、') }}</span>
        </div>

        <!-- 操作按钮行 -->
        <div class="search-test__input-actions">
          <!-- 图片上传 -->
          <input ref="imageInputRef" type="file" accept="image/*" multiple style="display: none" @change="handleImageSelect" />
          <el-tooltip content="上传图片检索">
            <el-button :icon="Picture" circle @click="imageInputRef?.click()" />
          </el-tooltip>
          <!-- 历史记录 -->
          <el-tooltip content="检索历史">
            <el-badge :value="searchHistory.length" :hidden="searchHistory.length === 0" :max="99" class="search-test__history-badge">
              <el-button :icon="Clock" circle @click="showHistory = !showHistory" />
            </el-badge>
          </el-tooltip>
          <!-- 搜索 -->
          <el-button
            type="primary"
            :icon="Search"
            circle
            :loading="searchLoading"
            class="search-test__search-btn"
            @click="handleSearch"
          />
        </div>
      </div>

      <!-- 检索历史面板 -->
      <div v-if="showHistory" class="search-test__history">
        <div class="search-test__history-header">
          <span>检索历史</span>
          <el-button link type="danger" size="small" @click="clearHistory">清空</el-button>
        </div>
        <div v-if="searchHistory.length === 0" class="search-test__history-empty">暂无检索历史</div>
        <div
          v-for="item in searchHistory"
          :key="item.id"
          class="search-test__history-item"
          @click="handleHistoryClick(item)"
        >
          <el-icon v-if="item.mode === 'image'" class="search-test__history-icon"><Picture /></el-icon>
          <el-icon v-else class="search-test__history-icon"><Search /></el-icon>
          <span class="search-test__history-query">{{ item.query }}</span>
          <span class="search-test__history-meta">{{ item.resultCount }} 条 · {{ item.duration }}ms</span>
          <span class="search-test__history-time">{{ item.timestamp }}</span>
        </div>
      </div>
    </div>

    <!-- 示例问题（未搜索时显示） -->
    <div v-if="!hasSearched" class="search-test__examples">
      <h4 class="search-test__examples-title">示例问题</h4>
      <div class="search-test__examples-list">
        <div
          v-for="(question, index) in exampleQuestions"
          :key="index"
          class="search-test__example-item"
          @click="handleExampleClick(question)"
        >
          <el-icon class="search-test__example-icon"><Search /></el-icon>
          <span>{{ question }}</span>
        </div>
      </div>
      <el-button link type="primary" class="search-test__regenerate" @click="handleRegenerate">
        <el-icon><Refresh /></el-icon>
        重新生成
      </el-button>
    </div>

    <!-- 搜索结果 -->
    <div v-if="hasSearched" class="search-test__results">
      <div class="search-test__results-header">
        <span class="search-test__results-count">
          检索到 <strong>{{ searchResults.length }}</strong> 个相关文档块
          <el-tag v-if="searchMode === 'image'" size="small" type="warning" style="margin-left: 8px">图片检索</el-tag>
        </span>
        <el-button link type="primary" @click="handleClear">清空</el-button>
      </div>

      <div v-if="searchLoading" class="search-test__loading">
        <el-icon class="is-loading" :size="24"><Loading /></el-icon>
        <span>{{ searchMode === 'image' ? '正在分析图片并检索...' : '正在检索中...' }}</span>
      </div>

      <div v-else class="search-test__results-list">
        <div
          v-for="result in searchResults"
          :key="result.index"
          class="search-test__result-card"
        >
          <div class="search-test__result-header">
            <span class="search-test__result-index">#{{ result.index }}</span>
            <el-tag size="small" type="info" effect="plain">相似度: {{ result.similarity.toFixed(2) }}%</el-tag>
          </div>

          <div class="search-test__result-content search-test__result-preview" v-html="result.previewSnippet || result.content" />
          <details v-if="result.previewSnippet && result.content.length > 120" class="search-test__result-details">
            <summary>查看完整内容</summary>
            <div class="search-test__result-full" v-html="result.previewSnippet ? highlightFull(result) : result.content" />
          </details>

          <div v-if="expansionResult && expansionResult.entities.length > 0" class="search-test__expansion-source">
            <el-tag size="small" type="success">图谱扩展</el-tag>
            <span>基于实体: {{ expansionResult.entities.map(e => e.name).join(', ') }}</span>
          </div>
          <div class="search-test__result-source">
            <el-link type="primary" :underline="false" class="search-test__source-link" @click="handleSourceClick(result)">
              <el-icon><FolderOpened /></el-icon>
              来源: {{ result.source }}（点击溯源）
            </el-link>
            <span>块索引: {{ result.chunkIndex }}</span>
            <span>距离: {{ result.distance }}</span>
          </div>
        </div>
      </div>

      <div v-if="!searchLoading && searchResults.length === 0" class="search-test__empty">
        <el-empty description="未找到相关文档块" />
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;
@use 'sass:color';

.search-test {
  display: flex;
  flex-direction: column;
  gap: $spacing-lg;
  height: 100%;
}

// --- 搜索输入区 ---
.search-test__input-wrapper {
  position: relative;
}

.search-test__input-box {
  background: $bg-white;
  border-radius: $radius-lg;
  padding: $spacing-lg;
  border: 1px solid $border-lighter;
  transition: border-color 0.2s;

  &:focus-within {
    border-color: $color-primary;
  }
}

// --- 多图预览条 ---
.search-test__images {
  display: flex;
  gap: $spacing-xs;
  margin-bottom: $spacing-sm;
  padding: $spacing-xs;
  background: $bg-hover;
  border-radius: $radius-sm;
  overflow-x: auto;
}

.search-test__image-item {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;

  img {
    width: 56px;
    height: 56px;
    object-fit: cover;
    border-radius: $radius-sm;
    border: 1px solid $border-lighter;
  }
}

.search-test__image-remove {
  position: absolute;
  top: -4px;
  right: -4px;
  width: 18px;
  height: 18px;
}

.search-test__image-name {
  font-size: 10px;
  color: $text-secondary;
  max-width: 56px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-align: center;
}

.search-test__image-add {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border: 1px dashed $border-lighter;
  border-radius: $radius-sm;
  cursor: pointer;
  color: $text-secondary;
  flex-shrink: 0;
  transition: all 0.15s;

  &:hover {
    border-color: $color-primary;
    color: $color-primary;
  }
}

.search-test__input {
  :deep(.el-input__wrapper) {
    box-shadow: none !important;
    border: none;
    padding: 0;
  }
}

.search-test__input-actions {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  margin-top: $spacing-sm;
  justify-content: flex-end;
}

.search-test__search-btn {
  width: 40px;
  height: 40px;
}

.search-test__history-badge {
  :deep(.el-badge__content) {
    font-size: 10px;
  }
}

// --- 图谱扩展 ---
.search-test__expanding {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  font-size: 12px;
  color: $text-secondary;
  margin-top: $spacing-xs;
}

.search-test__expanded {
  font-size: 12px;
  color: $text-secondary;
  margin-top: $spacing-xs;
}

.search-test__expanded-label {
  font-weight: 500;
  color: $text-primary;
}

.search-test__expanded-query {
  color: $text-primary;
}

// --- 纠错提示 ---
.search-test__correction {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  font-size: 12px;
  color: $color-warning;
  margin-top: $spacing-xs;
  padding: 4px $spacing-sm;
  background: #fff8e1;
  border-radius: $radius-sm;
}

// --- 重写提示 ---
.search-test__rewrite {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  font-size: 12px;
  color: $text-secondary;
  margin-top: $spacing-xs;
}

// --- 同义词联想 ---
.search-test__synonyms {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  font-size: 12px;
  color: $color-warning;
  margin-top: $spacing-xs;
  padding: 4px $spacing-sm;
  background: #fff8e1;
  border-radius: $radius-sm;
  flex-wrap: wrap;

  strong {
    color: $text-primary;
  }
}

// --- 检索历史面板 ---
.search-test__history {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  z-index: 100;
  background: $bg-white;
  border: 1px solid $border-lighter;
  border-radius: $radius-base;
  box-shadow: $shadow-base;
  max-height: 300px;
  overflow-y: auto;
  margin-top: 4px;
}

.search-test__history-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $spacing-sm $spacing-base;
  border-bottom: 1px solid $border-lighter;
  font-size: 13px;
  font-weight: 600;
  position: sticky;
  top: 0;
  background: $bg-white;
}

.search-test__history-empty {
  padding: $spacing-lg;
  text-align: center;
  font-size: 12px;
  color: $text-secondary;
}

.search-test__history-item {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  padding: $spacing-xs $spacing-base;
  cursor: pointer;
  font-size: 12px;

  &:hover { background: $bg-hover; }
}

.search-test__history-icon {
  color: $text-secondary;
  flex-shrink: 0;
}

.search-test__history-query {
  flex: 1;
  color: $text-primary;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.search-test__history-meta {
  color: $text-secondary;
  flex-shrink: 0;
}

.search-test__history-time {
  color: $text-placeholder;
  flex-shrink: 0;
  font-size: 11px;
}

// --- 示例问题 ---
.search-test__examples {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

.search-test__examples-title {
  font-size: 14px;
  font-weight: 600;
  color: $text-primary;
  margin: 0;
}

.search-test__examples-list {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: $spacing-sm;
}

.search-test__example-item {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  padding: $spacing-sm $spacing-base;
  background: $bg-white;
  border: 1px solid $border-lighter;
  border-radius: $radius-base;
  cursor: pointer;
  font-size: 13px;
  color: $text-primary;
  transition: all 0.2s;

  &:hover {
    border-color: $color-primary;
    color: $color-primary;
  }
}

.search-test__example-icon {
  color: $text-secondary;
  flex-shrink: 0;
}

.search-test__regenerate {
  align-self: flex-start;
}

// --- 搜索结果 ---
.search-test__results {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

.search-test__results-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.search-test__results-count {
  font-size: 14px;
  color: $text-primary;
}

.search-test__loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-xxl 0;
  color: $text-secondary;
}

.search-test__results-list {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

.search-test__result-card {
  background: $bg-white;
  border: 1px solid $border-lighter;
  border-radius: $radius-base;
  padding: $spacing-base;
  transition: border-color 0.2s;

  &:hover {
    border-color: color.adjust($color-primary, $lightness: 30%);
  }
}

.search-test__result-header {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  margin-bottom: $spacing-sm;
}

.search-test__result-index {
  font-size: 14px;
  font-weight: 600;
  color: $color-primary;
}

.search-test__result-content {
  font-size: 14px;
  color: $text-regular;
  line-height: 1.7;
  margin-bottom: $spacing-sm;
  word-break: break-word;
}

.search-test__result-preview {
  display: block;
  overflow: visible;

  :deep(mark) {
    background: #fff3cd;
    color: $text-primary;
    padding: 0 2px;
    border-radius: 2px;
    font-weight: 500;
  }
}

.search-test__result-details {
  margin-bottom: $spacing-sm;

  summary {
    cursor: pointer;
    font-size: 12px;
    color: $color-primary;
    user-select: none;
    margin-bottom: $spacing-xs;
  }
}

.search-test__result-full {
  font-size: 13px;
  color: $text-regular;
  line-height: 1.7;
  padding: $spacing-sm;
  background: $bg-hover;
  border-radius: $radius-sm;
  word-break: break-word;

  :deep(mark) {
    background: #fff3cd;
    color: $text-primary;
    padding: 0 2px;
    border-radius: 2px;
    font-weight: 500;
  }
}

.search-test__expansion-source {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  font-size: 12px;
  color: $text-secondary;
  padding: $spacing-sm $spacing-base;
  background: #f0f9eb;
  border-radius: $radius-sm;
  margin-bottom: $spacing-sm;
}

.search-test__result-source {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-base;
  font-size: 12px;
  color: $text-secondary;
  padding-top: $spacing-sm;
  border-top: 1px solid $border-lighter;

  span {
    display: inline-flex;
    align-items: center;
    gap: 4px;
  }
}

.search-test__source-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
}

.search-test__empty {
  padding: $spacing-xxl 0;
}
</style>
