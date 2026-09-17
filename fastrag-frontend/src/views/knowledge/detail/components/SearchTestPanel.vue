<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import type { RetrievalConfig, RetrievalSettingConfig } from '@/types/knowledge'
import type { SearchResultItem } from '@/types/evaluation'
import { Search, Close, FolderOpened, Loading, Refresh, PriceTag, Upload, Clock, Delete, Picture, WarningFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { useGraphExpansion } from '@/composables/useGraphExpansion'
import { useSynonyms } from '@/composables/useSynonyms'
import {
  searchRetrieval,
  getRetrievalLogs,
  getKeywordRecommendations,
  judgeKeywords,
  // 知识标签检索
  getTags,
  createTag,
  updateTag,
  deleteTag,
  getTagTypes,
  getTagKnowledge,
  associateTagKnowledge,
  disassociateTag,
  getKnowledgeList,
  // 检索日志分析
  createRetrievalLog,
  updateRetrievalLog,
  deleteRetrievalLog,
  getRetrievalLogAnalysis,
} from '@/api'
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
const correctionOriginal = ref('')

/** 撤销纠错：清掉纠错态并用原词重新检索 */
async function handleUndoCorrection() {
  const original = correctionOriginal.value
  correctionSuggestion.value = null
  correctionReason.value = ''
  if (original) {
    searchQuery.value = original
  }
  searchSkippedCorrection.value = true
  try {
    await handleSearch()
  } finally {
    searchSkippedCorrection.value = false
  }
}
/** 撤销纠错期间跳过自动纠错，避免又被纠正回去 */
const searchSkippedCorrection = ref(false)

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

// 从后端检索日志加载用户历史（持久化），失败则仅保留会话内记录
async function loadHistoryFromServer() {
  try {
    const res: any = await getRetrievalLogs({ page: 1, pageSize: 20 })
    const list = res?.list || res?.records || res || []
    const items: SearchHistoryItem[] = list
      .filter((l: any) => l && l.query)
      .map((l: any, idx: number) => ({
        id: Date.now() + idx,
        query: l.query,
        mode: 'text' as const,
        resultCount: l.hitCount ?? 0,
        duration: l.latencyMs ?? 0,
        timestamp: l.createdAt ? String(l.createdAt).replace('T', ' ').slice(5, 16) : '',
      }))
    searchHistory.value = items
    historySeq.value = items.length
  } catch {
    // 后端不可用时静默降级为会话内历史
  }
}
onMounted(loadHistoryFromServer)
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

// --- 关键词推荐 ---
// 示例问题（基线缺失声明，此处补齐）
const exampleQuestions = ref<string[]>([
  "如何快速上手本知识库的检索功能？",
  "知识库支持哪些文档格式？",
  "如何配置解析策略？",
  "智能搜索的纠错规则怎么维护？",
])
const recommendedKeywords = ref<string[]>([])
const keywordLoading = ref(false)

async function loadKeywordRecommendations(query: string) {
  if (!query || !props.kbId) { recommendedKeywords.value = []; return }
  keywordLoading.value = true
  try {
    const res: any = await getKeywordRecommendations(props.kbId, query, 10)
    const list = Array.isArray(res) ? res : (res?.list || [])
    recommendedKeywords.value = list.map((item: any) => item.text || item.question || String(item)).filter(Boolean)
  } catch {
    recommendedKeywords.value = []
  } finally {
    keywordLoading.value = false
  }
}

// --- 关键词推荐-判断：判定检索词是否命中已配置关键词/标准问法 ---
const judgeLoading = ref(false)
const judgeResult = ref<any>(null)
async function handleJudgeKeywords() {
  if (!props.kbId) return
  const q = searchQuery.value?.trim()
  if (!q) { ElMessage.warning('请先输入检索词'); return }
  judgeLoading.value = true
  try {
    const res: any = await judgeKeywords(props.kbId, q)
    judgeResult.value = res || null
  } catch (e: any) {
    judgeResult.value = null
    ElMessage.error('判断失败：' + (e?.message || ''))
  } finally {
    judgeLoading.value = false
  }
}

function handleKeywordClick(keyword: string) {
  searchQuery.value = keyword
  handleSearch()
}

// --- 搜索 ---
async function handleSearch() {
  const isImageSearch = searchMode.value === 'image' && imageItems.value.length > 0
  if (!isImageSearch && !searchQuery.value.trim()) {
    ElMessage.warning('请输入检索内容或上传图片')
    return
  }

  let effectiveQuery = searchQuery.value

  if (isImageSearch) {
    const names = imageItems.value.map((i) => i.file.name).join(' ')
    effectiveQuery = `[图片] ${names}`
  }

  correctionSuggestion.value = null
  correctionReason.value = ''
  correctionOriginal.value = ''
  rewriteRules.value = []
  if (!isImageSearch && preprocess.value.autoCorrection && !searchSkippedCorrection.value) {
    const original = searchQuery.value
    const result = await localAutoCorrect(original, props.kbId || undefined)
    if (result) {
      correctionOriginal.value = original
      // 检索测试输入框直接改为纠正后的检索词（发现错别字即原地改正）
      searchQuery.value = result.corrected
      correctionSuggestion.value = result.corrected
      correctionReason.value = result.reason
      effectiveQuery = result.corrected
    }
  }
  rewriteRules.value = []
  if (!isImageSearch && preprocess.value.queryRewrite) {
    const { rewritten, appliedRules } = await localRewrite(effectiveQuery)
    if (rewritten !== effectiveQuery) {
      rewriteRules.value = appliedRules
      effectiveQuery = rewritten
    }
  }

  searchLoading.value = true
  hasSearched.value = true
  const startTime = Date.now()

  try {
    let graphExpandedQuery = effectiveQuery
    if (!isImageSearch && preprocess.value.graphExpansion) {
      graphExpandedQuery = await graphExpand(effectiveQuery)
    } else {
      expandedQuery.value = ''
    }

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

    // 搜索完成后加载关键词推荐（闭环）
    if (!isImageSearch && searchQuery.value.trim()) {
      loadKeywordRecommendations(searchQuery.value.trim())
    }
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
  recommendedKeywords.value = []
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
// ============================================================================
const kbId = computed(() => props.kbId || '')
// 知识标签检索（新增 / 修改 / 删除 / 查询 / 存储）—— 在检索测试页内直接维护并验证
// ============================================================================
const extraTab = ref('tag')
const tagLoading = ref(false)
const tagList = ref<any[]>([])
const tagTypes = ref<any[]>([])
const tagKeyword = ref('')
const tagTypeFilter = ref('')
const tagDialog = ref(false)
const tagEditingId = ref('')
const tagForm = ref<any>({ name: '', tagTypeId: '' })
const tagStoreVisible = ref(false)
const tagStoreRow = ref<any>(null)
const tagStoreKnowledgeId = ref('')
const knowledgeOptions = ref<any[]>([])
const tagKnowledge = ref<any[]>([])

async function loadTagTypes() {
  try { tagTypes.value = ((await getTagTypes(kbId.value)) as any) || [] } catch { tagTypes.value = [] }
}
async function loadTags() {
  if (!kbId.value) return
  tagLoading.value = true
  try {
    const res: any = await getTags(kbId.value, { keyword: tagKeyword.value || undefined, tagTypeId: tagTypeFilter.value || undefined })
    tagList.value = Array.isArray(res) ? res : (res?.list || res?.records || [])
  } catch { tagList.value = [] } finally { tagLoading.value = false }
}
async function loadKnowledgeOptions() {
  try {
    const res: any = await getKnowledgeList(kbId.value)
    knowledgeOptions.value = Array.isArray(res) ? res : (res?.list || res?.records || [])
  } catch { knowledgeOptions.value = [] }
}
function openTagDialog(row?: any) {
  tagEditingId.value = row?.id || ''
  tagForm.value = row ? { name: row.name, tagTypeId: row.tagTypeId || '' } : { name: '', tagTypeId: tagTypes.value[0]?.id || '' }
  tagDialog.value = true
}
async function saveTag() {
  if (!tagForm.value.name) { ElMessage.warning('请输入标签名称'); return }
  try {
    if (tagEditingId.value) { await updateTag(kbId.value, tagEditingId.value, tagForm.value); ElMessage.success('标签已修改') }
    else { await createTag(kbId.value, tagForm.value); ElMessage.success('标签已新增') }
    tagDialog.value = false
    await loadTags()
  } catch (e: any) { ElMessage.error('保存失败：' + (e?.message || '')) }
}
async function removeTag(row: any) {
  try { await ElMessageBox.confirm(`确定删除标签「${row.name}」吗？`, '提示', { type: 'warning' }) } catch { return }
  try { await deleteTag(kbId.value, row.id); ElMessage.success('标签已删除'); await loadTags() }
  catch (e: any) { ElMessage.error('删除失败：' + (e?.message || '')) }
}
/** 存储：把知识关联到标签（写入标签-知识关系） */
async function openTagStore(row: any) {
  tagStoreRow.value = row
  tagStoreKnowledgeId.value = ''
  await Promise.all([loadKnowledgeOptions(), refreshTagKnowledge(row)])
  tagStoreVisible.value = true
}
async function refreshTagKnowledge(row: any) {
  try { tagKnowledge.value = ((await getTagKnowledge(kbId.value, row.id)) as any) || [] } catch { tagKnowledge.value = [] }
}
async function saveTagStore() {
  if (!tagStoreKnowledgeId.value) { ElMessage.warning('请选择要关联的知识'); return }
  try {
    await associateTagKnowledge(kbId.value, tagStoreRow.value.id, tagStoreKnowledgeId.value)
    ElMessage.success('已存储标签-知识关联')
    tagStoreKnowledgeId.value = ''
    await Promise.all([refreshTagKnowledge(tagStoreRow.value), loadTags()])
  } catch (e: any) { ElMessage.error('存储失败：' + (e?.message || '')) }
}
async function removeTagKnowledge(k: any) {
  try { await disassociateTag(kbId.value, tagStoreRow.value.id, k.id); ElMessage.success('已解除关联'); await refreshTagKnowledge(tagStoreRow.value) }
  catch (e: any) { ElMessage.error('解除失败：' + (e?.message || '')) }
}

// ============================================================================
// 检索日志分析（新增 / 修改 / 删除 / 查询 / 存储）—— 在检索测试页内闭环
// ============================================================================
const rlogLoading = ref(false)
const rlogList = ref<any[]>([])
const rlogKeyword = ref('')
const rlogAnalysis = ref<any>(null)
const rlogDialog = ref(false)
const rlogEditing = ref(false)
const rlogForm = ref<any>({ id: null, query: '', hasResult: true, hitCount: 0, maxSimilarity: 0, durationMs: 0 })
const filteredRlogs = computed(() => rlogKeyword.value ? rlogList.value.filter((l: any) => (l.query || '').includes(rlogKeyword.value)) : rlogList.value)

async function loadRlogs() {
  if (!kbId.value) return
  rlogLoading.value = true
  try {
    const res: any = await getRetrievalLogs({ kbId: kbId.value, page: 1, pageSize: 50 })
    rlogList.value = res?.list || res?.records || res || []
  } catch { rlogList.value = [] } finally { rlogLoading.value = false }
}
async function loadRlogAnalysis() {
  try { rlogAnalysis.value = (await getRetrievalLogAnalysis(kbId.value)) as any } catch { rlogAnalysis.value = null }
}
function openRlog(row?: any) {
  rlogEditing.value = !!row
  rlogForm.value = row
    ? { id: row.id, query: row.query, hasResult: row.hasResult !== false, hitCount: row.hitCount ?? 0, maxSimilarity: row.maxSimilarity ?? 0, durationMs: row.durationMs ?? 0 }
    : { id: null, query: searchQuery.value || '', hasResult: true, hitCount: 0, maxSimilarity: 0, durationMs: 0 }
  rlogDialog.value = true
}
async function saveRlog() {
  if (!rlogForm.value.query) { ElMessage.warning('请输入检索词'); return }
  const payload = { kbId: kbId.value, query: rlogForm.value.query, hasResult: !!rlogForm.value.hasResult, hitCount: Number(rlogForm.value.hitCount) || 0, maxSimilarity: Number(rlogForm.value.maxSimilarity) || 0, durationMs: Number(rlogForm.value.durationMs) || 0, userId: 'user_admin' }
  try {
    if (rlogEditing.value) { await updateRetrievalLog(rlogForm.value.id, payload); ElMessage.success('检索日志已修改') }
    else { await createRetrievalLog(payload); ElMessage.success('检索日志已新增（存储成功）') }
    rlogDialog.value = false
    await Promise.all([loadRlogs(), loadRlogAnalysis()])
  } catch (e: any) { ElMessage.error('保存失败：' + (e?.message || '')) }
}
async function removeRlog(row: any) {
  try { await ElMessageBox.confirm(`确定删除检索日志「${row.query}」吗？`, '提示', { type: 'warning' }) } catch { return }
  try { await deleteRetrievalLog(row.id); ElMessage.success('检索日志已删除'); await Promise.all([loadRlogs(), loadRlogAnalysis()]) }
  catch (e: any) { ElMessage.error('删除失败：' + (e?.message || '')) }
}
/** 存储验证：执行一次真实检索（主链路自动落日志） */
async function storeBySearch() {
  try {
    await searchRetrieval({ knowledgeId: kbId.value, query: searchQuery.value || '宽带 资费', config: { topK: 5 } } as any)
    ElMessage.success('已执行检索，日志已自动落库')
    await Promise.all([loadRlogs(), loadRlogAnalysis()])
  } catch { ElMessage.error('检索执行失败') }
}

watch(() => props.kbId, () => {
  loadTags(); loadTagTypes(); loadRlogs(); loadRlogAnalysis()
})
onMounted(() => {
  loadTags(); loadTagTypes(); loadRlogs(); loadRlogAnalysis()
})
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
          <el-icon><WarningFilled /></el-icon>
          <span>{{ correctionReason }}</span>
          <el-button type="primary" link size="small" @click="handleUndoCorrection">撤销纠错，按原词检索</el-button>
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
          <template v-if="searchResults.some((r) => r.fallback)">未找到精确结果，为您推荐：</template>
          <template v-else>检索到 <strong>{{ searchResults.length }}</strong> 个相关文档块</template>
          <el-tag v-if="searchMode === 'image'" size="small" type="warning" style="margin-left: 8px">图片检索</el-tag>
        </span>
        <div style="display:flex;gap:8px">
          <el-button v-if="recommendedKeywords.length" link type="primary" size="small" @click="loadKeywordRecommendations(searchQuery)">
            刷新推荐
          </el-button>
          <el-button link type="primary" @click="handleClear">清空</el-button>
        </div>
      </div>

      <!-- 关键词推荐 -->
      <div v-if="recommendedKeywords.length && !searchLoading" class="search-test__keywords">
        <span class="search-test__keywords-label">相关关键词：</span>
        <el-tag
          v-for="kw in recommendedKeywords"
          :key="kw"
          class="search-test__keyword-tag"
          type="info"
          effect="plain"
          @click="handleKeywordClick(kw)"
        >
          {{ kw }}
        </el-tag>
      </div>

      <!-- 关键词推荐-判断：是否命中已配置关键词/标准问法 -->
      <div class="search-test__judge-kw">
        <el-button size="small" :loading="judgeLoading" @click.stop="handleJudgeKeywords">
          判断关键词（是否命中配置）
        </el-button>
        <template v-if="judgeResult">
          <el-tag :type="judgeResult.matched ? 'success' : 'info'" size="small">
            {{ judgeResult.matched ? '已命中配置关键词' : '未命中配置关键词' }}
          </el-tag>
          <span style="font-size: 12px; color: #909399">判定检索词：{{ judgeResult.query || searchQuery }}</span>
        </template>
      </div>
      <div v-if="judgeResult && (judgeResult.keywords || []).length" class="search-test__judge-detail">
        <div v-for="(h, i) in judgeResult.keywords" :key="i">
          <el-tag size="small" :type="h.source === 'standard' ? 'primary' : 'warning'" effect="plain">
            {{ h.source === 'standard' ? '标准问法' : '问答对关键词' }}
          </el-tag>
          <span style="margin-left: 6px">{{ h.text }}</span>
          <span style="margin-left: 8px; color: #909399; font-size: 12px">置信度 {{ h.score }}</span>
        </div>
      </div>
      <div v-else-if="judgeResult && !judgeResult.matched" class="search-test__judge-detail" style="color:#909399">
        该检索词未匹配到已配置的关键词/标准问法（可在「智能搜索」Tab 的问答对关键词或标准问法中添加）
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

    <!-- ===== 知识标签检索 / 检索日志分析（页内闭环，5 操作各一组）===== -->
    <div class="search-test__extra">
      <el-tabs v-model="extraTab">
        <!-- 知识标签检索：新增 / 修改 / 删除 / 查询 / 存储 -->
        <el-tab-pane label="知识标签检索" name="tag">
          <div class="search-test__extra-toolbar">
            <el-input v-model="tagKeyword" placeholder="按标签名称查询" clearable size="small" style="width: 200px" @keyup.enter="loadTags">
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <el-select v-model="tagTypeFilter" placeholder="标签类型" clearable size="small" style="width: 150px" @change="loadTags">
              <el-option v-for="t in tagTypes" :key="t.id" :label="t.name" :value="t.id" />
            </el-select>
            <el-button size="small" @click="loadTags">查询</el-button>
            <span style="color: var(--el-text-color-secondary); font-size: 12px">共 {{ tagList.length }} 个标签</span>
            <el-button size="small" type="primary" @click="openTagDialog()">
              <el-icon><Plus /></el-icon>新增标签
            </el-button>
          </div>
          <el-table :data="tagList" size="small" stripe v-loading="tagLoading">
            <el-table-column prop="name" label="标签名称" min-width="150" />
            <el-table-column label="标签类型" width="130">
              <template #default="{ row }">{{ tagTypes.find((t: any) => t.id === row.tagTypeId)?.name || row.tagTypeId || '-' }}</template>
            </el-table-column>
            <el-table-column prop="knowledgeCount" label="关联知识数" width="110" align="center">
              <template #default="{ row }">{{ row.knowledgeCount ?? '-' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="240" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openTagStore(row)">存储（关联知识）</el-button>
                <el-button link type="primary" size="small" @click="openTagDialog(row)">修改</el-button>
                <el-button link type="danger" size="small" @click="removeTag(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!tagList.length && !tagLoading" description="暂无标签，点击「新增标签」创建" :image-size="60" />
        </el-tab-pane>

        <!-- 检索日志分析：新增 / 修改 / 删除 / 查询 / 存储 -->
        <el-tab-pane label="检索日志分析" name="rlog">
          <div class="search-test__extra-toolbar">
            <el-tag size="small" type="info">总检索 {{ rlogAnalysis?.totalQueries ?? 0 }} 次</el-tag>
            <el-tag size="small" type="warning">无结果率 {{ rlogAnalysis?.noResultRate ?? 0 }}%</el-tag>
            <el-tag size="small">平均耗时 {{ rlogAnalysis?.avgLatencyMs ?? 0 }}ms</el-tag>
            <el-tag size="small" type="success">平均命中 {{ rlogAnalysis?.avgHitCount ?? 0 }} 条</el-tag>
          </div>
          <div class="search-test__extra-toolbar">
            <el-input v-model="rlogKeyword" placeholder="按检索词查询" clearable size="small" style="width: 200px" />
            <span style="color: var(--el-text-color-secondary); font-size: 12px">共 {{ filteredRlogs.length }} 条</span>
            <el-button size="small" @click="loadRlogs(); loadRlogAnalysis()">刷新</el-button>
            <el-button size="small" @click="storeBySearch">执行检索并落库（存储）</el-button>
            <el-button size="small" type="primary" @click="openRlog()">新增检索日志</el-button>
          </div>
          <el-table :data="filteredRlogs" size="small" stripe v-loading="rlogLoading">
            <el-table-column prop="query" label="检索词" min-width="180" show-overflow-tooltip />
            <el-table-column label="有结果" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="row.hasResult ? 'success' : 'danger'" size="small">{{ row.hasResult ? '是' : '否' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="hitCount" label="命中" width="70" align="center" />
            <el-table-column label="最高相似度" width="100" align="center">
              <template #default="{ row }">{{ row.maxSimilarity ?? '-' }}</template>
            </el-table-column>
            <el-table-column label="耗时(ms)" width="90" align="center">
              <template #default="{ row }">{{ row.durationMs ?? '-' }}</template>
            </el-table-column>
            <el-table-column prop="createdAt" label="时间" min-width="160" />
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openRlog(row)">修改</el-button>
                <el-button link type="danger" size="small" @click="removeRlog(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!filteredRlogs.length && !rlogLoading" description="暂无检索日志，执行一次检索即会自动落库" :image-size="60" />
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 标签检索-存储（关联知识）对话框 -->
    <el-dialog v-model="tagStoreVisible" :title="`存储标签关联 — ${tagStoreRow?.name || ''}`" width="560px">
      <div style="display:flex;gap:8px;margin-bottom:12px">
        <el-select v-model="tagStoreKnowledgeId" placeholder="选择要关联的知识" filterable style="width:360px">
          <el-option v-for="k in knowledgeOptions" :key="k.id" :label="k.title" :value="k.id" />
        </el-select>
        <el-button type="primary" @click="saveTagStore">关联并存储</el-button>
      </div>
      <div style="font-weight:600;font-size:13px;margin-bottom:6px">已关联知识（{{ tagKnowledge.length }}）</div>
      <el-table :data="tagKnowledge" size="small" stripe>
        <el-table-column prop="title" label="知识标题" min-width="220" show-overflow-tooltip />
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button link type="danger" size="small" @click="removeTagKnowledge(row)">解除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!tagKnowledge.length" description="暂无关联知识" :image-size="50" />
      <template #footer><el-button @click="tagStoreVisible = false">关闭</el-button></template>
    </el-dialog>

    <!-- 标签新增/修改 -->
    <el-dialog v-model="tagDialog" :title="tagEditingId ? '修改标签' : '新增标签'" width="420px">
      <el-form label-width="90px">
        <el-form-item label="标签名称"><el-input v-model="tagForm.name" placeholder="如：宽带业务" /></el-form-item>
        <el-form-item label="标签类型">
          <el-select v-model="tagForm.tagTypeId" style="width:100%">
            <el-option v-for="t in tagTypes" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="tagDialog = false">取消</el-button>
        <el-button type="primary" @click="saveTag">保存</el-button>
      </template>
    </el-dialog>

    <!-- 检索日志新增/修改 -->
    <el-dialog v-model="rlogDialog" :title="rlogEditing ? '修改检索日志' : '新增检索日志'" width="460px">
      <el-form label-width="100px">
        <el-form-item label="检索词"><el-input v-model="rlogForm.query" /></el-form-item>
        <el-form-item label="是否有结果"><el-switch v-model="rlogForm.hasResult" /></el-form-item>
        <el-form-item label="命中数"><el-input-number v-model="rlogForm.hitCount" :min="0" :max="999" /></el-form-item>
        <el-form-item label="最高相似度"><el-input-number v-model="rlogForm.maxSimilarity" :min="0" :max="1" :step="0.01" :precision="2" /></el-form-item>
        <el-form-item label="耗时(ms)"><el-input-number v-model="rlogForm.durationMs" :min="0" :max="60000" :step="10" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rlogDialog = false">取消</el-button>
        <el-button type="primary" @click="saveRlog">保存</el-button>
      </template>
    </el-dialog>
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

// --- 关键词推荐 ---
.search-test__judge-kw {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  padding: $spacing-sm 0 0;
  flex-wrap: wrap;
}

.search-test__extra {
  margin-top: $spacing-base;
  padding: $spacing-base;
  background: $bg-white;
  border-radius: $radius-base;
  border: 1px solid $border-lighter;

  .search-test__extra-toolbar {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    flex-wrap: wrap;
    margin-bottom: 10px;
  }
}

.search-test__judge-detail {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: $spacing-xs 0 $spacing-sm;
  font-size: 13px;
}

.search-test__keywords {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  flex-wrap: wrap;
  padding: $spacing-sm 0;
  font-size: 12px;

  .search-test__keywords-label {
    color: $text-secondary;
    margin-right: $spacing-xs;
  }

  .search-test__keyword-tag {
    cursor: pointer;
    transition: all 0.15s;

    &:hover {
      border-color: $color-primary;
      color: $color-primary;
    }
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
