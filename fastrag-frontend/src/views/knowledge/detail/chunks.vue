<script setup lang="ts">
import { useRouter, useRoute } from 'vue-router'
import { nextTick, onBeforeUnmount, watch } from 'vue'
import { ArrowLeft, Download, Search, Edit, Grid, ArrowDown, ArrowUp, ArrowRight, Delete, Upload, Setting, VideoPlay, VideoPause, Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const route = useRoute()
const router = useRouter()
const kbId = route.params.id as string
const fileId = route.params.fileId as string

// --- Types ---
interface Chunk {
  id: string
  index: number
  content: string
  parentId?: string
  children?: Chunk[]
  startTime?: number
  endTime?: number
  metadata: ChunkMetadata
  selected?: boolean
  chunkType?: string        // "text" | "image" | "table" | "code"
  imageKeys?: string[]      // PDF 提取的图片 key 列表
  pageNumber?: number       // 所属页码
  title?: string            // 所属最近标题
  headingPath?: string      // 层级路径，如 "第一章 > 1.1 背景"
}

interface ChunkMetadata {
  fileId: string
  fileName: string
  chunkIndex: number
  tokenCount: number
  createdAt: string
  updatedAt: string
}

// --- 从 API 加载文件信息 ---
const routeCategory = (route.query.category as string) || 'document'

const fileInfo = ref({
  id: fileId,
  name: `文件_${fileId.slice(-6)}`,
  category: routeCategory as 'video' | 'audio' | 'document' | 'image',
  extension: '.pdf',
  size: 0,
  chunkCount: 0,
  strategy: 'General',
  parseStrategyName: '',
  chunkSize: 2000,
  overlapSize: 100,
  embeddingModel: 'text-embedding-v4',
  url: '',
  createdAt: '',
  updatedAt: '',
  processingMode: 'chunk',
})

async function loadFileInfo() {
  try {
    const res: any = await api.getFiles(kbId)
    const files = res?.list || res || []
    const file = files.find((f: any) => f.id === fileId)
    if (file) {
      fileInfo.value.name = file.name || fileInfo.value.name
      fileInfo.value.extension = file.extension || fileInfo.value.extension
      fileInfo.value.size = file.size || fileInfo.value.size
      fileInfo.value.chunkCount = file.chunkCount || fileInfo.value.chunkCount
      fileInfo.value.url = file.url || ''
      fileInfo.value.processingMode = file.processingMode || 'chunk'
      fileInfo.value.parseStrategyName = file.parseStrategyName || ''
      fileInfo.value.createdAt = file.createdAt || fileInfo.value.createdAt
      fileInfo.value.updatedAt = file.updatedAt || fileInfo.value.updatedAt
    }
  } catch {
    // ignore
  }
}

loadFileInfo().then(() => {
  loadImage()
  loadMedia()
  if (isQaMode.value) {
    loadQaPairs()
  }
})

// --- Active tab ---
const activeTab = ref<'markdown' | 'chunks'>('chunks')

// --- Search ---
const searchQuery = ref('')
const searchMode = ref<'id' | 'content'>('id')

// --- Edit state ---
const editDialogVisible = ref(false)
const editingChunk = ref<Chunk | null>(null)
const editingContent = ref('')
const editSaving = ref(false)

// --- Create state ---
const createDialogVisible = ref(false)
const createSaving = ref(false)
const newChunkContent = ref('')
const newChunkInsertMode = ref<'append' | 'after'>('append')
const newChunkInsertIndex = ref<number>(0)
const newChunkType = ref<'text' | 'image'>('text')
const newChunkPageNumber = ref<number>(0)
const newChunkStartTime = ref<number>(0)
const newChunkEndTime = ref<number>(0)

// --- UI state ---
const metadataCollapsed = ref(false)
const selectedChunks = ref<Set<string>>(new Set())
const selectAll = ref(false)

// --- Markdown state ---

// --- Media player state ---
const audioRef = ref<HTMLAudioElement | null>(null)
const videoRef = ref<HTMLVideoElement | null>(null)
const mediaPlaying = ref(false)
const mediaCurrentTime = ref(0)
const mediaDuration = ref(0)
const mediaSelectedChunkId = ref<string | null>(null)
const audioBlobUrl = ref('')
const videoBlobUrl = ref('')

async function loadMedia() {
  if (!fileInfo.value.url) return
  const raw = localStorage.getItem('ais_token') || ''
  const token = raw.startsWith('"') ? JSON.parse(raw) : raw
  try {
    const resp = await fetch(fileInfo.value.url, {
      headers: { 'Authorization': `Bearer ${token}` }
    })
    if (resp.ok) {
      const blob = await resp.blob()
      if (fileInfo.value.category === 'audio') {
        if (audioBlobUrl.value) URL.revokeObjectURL(audioBlobUrl.value)
        audioBlobUrl.value = URL.createObjectURL(blob)
      } else if (fileInfo.value.category === 'video') {
        if (videoBlobUrl.value) URL.revokeObjectURL(videoBlobUrl.value)
        videoBlobUrl.value = URL.createObjectURL(blob)
      }
    }
  } catch {
    // ignore
  }
}

function onAudioMetadataLoaded() {
  if (audioRef.value) {
    mediaDuration.value = audioRef.value.duration || 0
  }
}

function onAudioTimeUpdate() {
  if (audioRef.value) {
    mediaCurrentTime.value = audioRef.value.currentTime || 0
  }
}

function onAudioEnded() {
  mediaPlaying.value = false
  mediaCurrentTime.value = 0
}

function onVideoMetadataLoaded() {
  if (videoRef.value) {
    mediaDuration.value = videoRef.value.duration || 0
  }
}

function onVideoTimeUpdate() {
  if (videoRef.value) {
    mediaCurrentTime.value = videoRef.value.currentTime || 0
  }
}

function onVideoEnded() {
  mediaPlaying.value = false
  mediaCurrentTime.value = 0
}

// --- Markdown 内容 ---
const markdownContent = ref('')

// --- 图片预览 URL（带 token 加载） ---
const imageUrl = ref('')

async function loadImage() {
  if (!fileInfo.value.url || fileInfo.value.category !== 'image') return
  try {
    const raw = localStorage.getItem('ais_token') || ''
    const token = raw.startsWith('"') ? JSON.parse(raw) : raw
    const resp = await fetch(fileInfo.value.url, {
      headers: { 'Authorization': `Bearer ${token}` }
    })
    if (resp.ok) {
      const blob = await resp.blob()
      imageUrl.value = URL.createObjectURL(blob)
    }
  } catch {
    // ignore
  }
}

// --- 分页状态 ---
const currentPage = ref(1)
const pageSize = ref(10)
const totalChunks = ref(0)

// --- 从 API 加载 chunks ---
const chunks = ref<Chunk[]>([])

// 来源定位：从 query 读取目标切片 id（如 {fileId}_chunk_{index}）
const targetChunkId = ref<string>((route.query.chunkId as string) || '')

async function loadChunks() {
  try {
    const res: any = await api.getChunks(kbId, { fileId, page: currentPage.value, pageSize: pageSize.value })
    const list = res?.list || res || []
    totalChunks.value = res?.total || 0
    chunks.value = (list || []).map((mc: any, i: number) => mapChunk(mc, i))
    // 生成 markdown 内容（如果 chunk 有 headingPath 则在内容前加层级路径注释）
    if (chunks.value.length > 0) {
      markdownContent.value = chunks.value.map((c) => {
        const header = c.headingPath ? `> ${c.headingPath}\n\n` : ''
        return header + c.content
      }).join('\n\n---\n\n')
    }
    // 有定位目标时滚动高亮（当前页未命中则翻到目标所在页）
    if (targetChunkId.value) locateTargetChunk()
  } catch {
    // ignore
  }
}

/** 定位到目标切片：展开所属父分片 + 滚动到视口高亮 */
function locateTargetChunk() {
  const findIn = (list: Chunk[]): Chunk | null =>
    list.find(c => c.id === targetChunkId.value) || null
  const findParent = (list: Chunk[]): Chunk | null =>
    list.find(c => c.children?.some(child => child.id === targetChunkId.value)) || null

  const direct = findIn(chunks.value)
  const parent = direct ? null : findParent(chunks.value)

  // 当前页没有目标：从 id 解析 index 并翻页（chunkId 格式 {fileId}_chunk_{index}）
  if (!direct && !parent) {
    const m = targetChunkId.value.match(/_chunk_(\d+)$/)
    if (m) {
      const targetPage = Math.floor(Number(m[1]) / pageSize.value) + 1
      if (targetPage !== currentPage.value && targetPage <= Math.ceil(totalChunks.value / pageSize.value)) {
        currentPage.value = targetPage
        loadChunks()
      }
    }
    return
  }

  if (parent) selectedChunk.value = parent
  nextTick(() => {
    const el = document.querySelector(`[data-chunk-id="${targetChunkId.value}"]`) as HTMLElement | null
    el?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  })
}

// 支持同一页面内再次跳转定位（如从来源面板点击不同切片）
watch(() => route.query.chunkId, (v) => {
  targetChunkId.value = (v as string) || ''
  if (targetChunkId.value) locateTargetChunk()
})

/**
 * 分片数据映射：兼容单层与父子分片两种结构。
 * - 单层模式：普通分片行
 * - 父子模式：父分片行（chunkType='parent'）内嵌 children（子分片数组），
 *   孤立子分片（无父分片的单分片章节）作为普通行混排
 */
function mapChunk(mc: any, i: number): Chunk {
  const base: Chunk = {
    id: mc.id || `chunk_${mc.chunkIndex || i}`,
    index: mc.chunkIndex || i,
    content: mc.content || '',
    startTime: mc.startTime ?? undefined,
    endTime: mc.endTime ?? undefined,
    chunkType: mc.chunkType || 'text',
    parentId: mc.parentId || undefined,
    imageKeys: mc.imageKeys ? (typeof mc.imageKeys === 'string' ? JSON.parse(mc.imageKeys) : mc.imageKeys) : undefined,
    pageNumber: mc.pageNumber ?? undefined,
    title: mc.title || undefined,
    headingPath: mc.headingPath || undefined,
    metadata: {
      fileId: mc.fileId || fileId,
      fileName: mc.fileName || '',
      chunkIndex: mc.chunkIndex || i,
      tokenCount: (mc.content || '').length,
      createdAt: mc.createdAt || '',
      updatedAt: mc.updatedAt || '',
    },
  }
  // 父子模式：父分片行内嵌子分片
  if (mc.children && Array.isArray(mc.children) && mc.children.length > 0) {
    base.children = mc.children.map((child: any, ci: number) => mapChunk(child, ci))
  }
  return base
}

function handlePageChange(page: number) {
  currentPage.value = page
  loadChunks()
}

function handleSizeChange(size: number) {
  pageSize.value = size
  currentPage.value = 1
  loadChunks()
}

loadChunks()

// --- 问答对状态（仅 QA 模式文件使用） ---
const isQaMode = computed(() => fileInfo.value.processingMode === 'qa')
const qaList = ref<any[]>([])
const qaLoading = ref(false)
const qaCurrentPage = ref(1)
const qaPageSize = ref(10)
const qaTotal = ref(0)
const editingQaId = ref<string | null>(null)
const editingQaQuestion = ref('')
const editingQaAnswer = ref('')

async function loadQaPairs() {
  if (!isQaMode.value) return
  qaLoading.value = true
  try {
    const params: any = { fileId, page: qaCurrentPage.value, pageSize: qaPageSize.value }
    const res: any = await api.getQaPairs(kbId, params)
    const list = res?.list || res || []
    qaTotal.value = res?.total || list.length
    qaList.value = list
  } finally {
    qaLoading.value = false
  }
}

function onQaPageChange() { loadQaPairs() }
function onQaPageSizeChange() { qaCurrentPage.value = 1; loadQaPairs() }

async function handleSaveQaEdit() {
  if (!editingQaId.value) return
  if (!editingQaQuestion.value.trim()) { ElMessage.warning('请输入问题'); return }
  await api.updateQaPair(kbId, editingQaId.value, {
    question: editingQaQuestion.value,
    answer: editingQaAnswer.value,
  })
  ElMessage.success('问答对已更新')
  editingQaId.value = null
  loadQaPairs()
}

async function handleConfirmQa(row: any) {
  await api.confirmQaPair(kbId, row.id)
  ElMessage.success('已确认')
  loadQaPairs()
}

async function handleDeleteQa(row: any) {
  try {
    await ElMessageBox.confirm('确定要删除该问答对吗？', '提示', { type: 'warning' })
    await api.deleteQaPair(kbId, row.id)
    ElMessage.success('删除成功')
    // 如果正在编辑这条，取消编辑状态
    if (editingQaId.value === row.id) editingQaId.value = null
    loadQaPairs()
  } catch {}
}

function startEditQa(row: any) {
  editingQaId.value = row.id
  editingQaQuestion.value = row.question
  editingQaAnswer.value = row.answer
}

// --- 音视频 chunks（从 API 加载） ---
const mediaChunks = computed(() => chunks.value.filter(c => c.startTime != null))

// --- 传递给播放器的 ASR transcripts ---
const asrTranscripts = computed(() =>
  mediaChunks.value.map(c => ({ time: c.startTime || 0, text: c.content }))
)

// --- Image info ---
const imageInfo = ref({
  width: 1200,
  height: 800,
  format: 'PNG',
})

// --- Current selected chunk ---
const selectedChunk = ref<Chunk | null>(null)

// --- Computed ---
const chunkCount = computed(() => totalChunks.value || chunks.value.length)

const parentChunkCount = computed(() => chunks.value.length)

const totalTokens = computed(() => {
  let total = 0
  chunks.value.forEach(c => {
    total += c.metadata.tokenCount
    if (c.children) {
      c.children.forEach(child => {
        total += child.metadata.tokenCount
      })
    }
  })
  return total
})

const filteredChunks = computed(() => {
  if (!searchQuery.value) return chunks.value
  const query = searchQuery.value.toLowerCase()
  return chunks.value.filter(c => {
    if (searchMode.value === 'id') {
      return c.id.toLowerCase().includes(query)
    }
    return c.content.toLowerCase().includes(query) ||
      (c.children?.some(child => child.content.toLowerCase().includes(query)) ?? false)
  })
})

const selectedCount = computed(() => selectedChunks.value.size)

/** 选中项中是否包含父分片（批量删除提示级联） */
const isParentSelected = computed(() =>
  chunks.value.some(c => c.chunkType === 'parent' && selectedChunks.value.has(c.id))
)

// --- Methods ---
function goBack() {
  router.push(`/knowledge/${kbId}`)
}

function toggleExpand(chunk: Chunk) {
  if (selectedChunk.value?.id === chunk.id) {
    selectedChunk.value = null
  } else {
    selectedChunk.value = chunk
  }
}

function startEdit(chunk: Chunk) {
  if (chunk.chunkType === 'parent') {
    ElMessage.warning('父分片由子分片自动聚合生成，请展开后编辑子分片')
    return
  }
  editingChunk.value = chunk
  editingContent.value = chunk.content
  editDialogVisible.value = true
}

function cancelEdit() {
  editDialogVisible.value = false
  editingChunk.value = null
  editingContent.value = ''
}

// --- 图片缩略图 ---
const imageBlobUrls = ref<Record<string, string>>({})

function getImageUrl(imageKey: string): string | undefined {
  return imageBlobUrls.value[imageKey]
}

async function loadImageThumb(imageKey: string) {
  if (imageBlobUrls.value[imageKey]) return
  try {
    const raw = localStorage.getItem('ais_token') || ''
    const token = raw.startsWith('"') ? JSON.parse(raw) : raw
    const resp = await fetch(`/api/kb/${kbId}/files/${fileId}/images/${imageKey}`, {
      headers: { 'Authorization': `Bearer ${token}` }
    })
    if (resp.ok) {
      const blob = await resp.blob()
      imageBlobUrls.value[imageKey] = URL.createObjectURL(blob)
    }
  } catch {
    // ignore
  }
}

function previewImage(imageKey: string) {
  const url = getImageUrl(imageKey)
  if (url) window.open(url, '_blank')
}

// 加载所有图片分片的缩略图
watch(() => chunks.value, (list) => {
  for (const chunk of list) {
    if (chunk.chunkType === 'image' && chunk.imageKeys) {
      chunk.imageKeys.forEach(key => loadImageThumb(key))
    }
  }
}, { immediate: true })

async function saveEdit() {
  if (!editingChunk.value) return
  if (!editingContent.value.trim()) { ElMessage.warning('分片内容不能为空'); return }
  editSaving.value = true
  try {
    await api.updateChunk(kbId, editingChunk.value.id, { content: editingContent.value })
    editDialogVisible.value = false
    editingChunk.value = null
    editingContent.value = ''
    ElMessage.success('分片内容已更新')
    loadChunks()
  } catch (e: any) {
    ElMessage.error(e?.message || '更新失败')
  } finally {
    editSaving.value = false
  }
}

// --- Create chunk methods ---
function openCreateDialog() {
  newChunkContent.value = ''
  newChunkInsertMode.value = 'append'
  newChunkInsertIndex.value = 0
  newChunkType.value = 'text'
  newChunkPageNumber.value = 0
  newChunkStartTime.value = 0
  newChunkEndTime.value = 0
  createDialogVisible.value = true
}

async function handleCreateChunk() {
  if (!newChunkContent.value.trim()) { ElMessage.warning('分片内容不能为空'); return }
  createSaving.value = true
  try {
    const data: any = {
      fileId,
      content: newChunkContent.value,
      chunkType: newChunkType.value,
    }
    if (newChunkInsertMode.value === 'after') {
      data.insertAfterIndex = newChunkInsertIndex.value
    }
    if (fileInfo.value.category === 'document') {
      if (newChunkType.value === 'image' && newChunkPageNumber.value > 0) {
        data.pageNumber = newChunkPageNumber.value
      }
    }
    if (fileInfo.value.category === 'audio' || fileInfo.value.category === 'video') {
      if (newChunkStartTime.value > 0) data.startTime = newChunkStartTime.value
      if (newChunkEndTime.value > 0) data.endTime = newChunkEndTime.value
    }
    await api.createChunk(kbId, data)
    createDialogVisible.value = false
    ElMessage.success('分片创建成功')
    currentPage.value = 1
    loadChunks()
  } catch (e: any) {
    ElMessage.error(e?.message || '创建失败')
  } finally {
    createSaving.value = false
  }
}

function handleDownload() {
  ElMessage.success('开始下载分片数据')
}

// --- 重新分片：按当前解析策略重跑 解析→分片→向量化（删除旧分片与向量） ---
const reChunking = ref(false)
async function handleReChunk() {
  try {
    await ElMessageBox.confirm(
      '重新分片将删除该文件全部旧分片与向量数据，并按当前解析策略重新解析、分片和向量化。' +
      '如果修改了解析策略（如子分片长度），需要重新分片后才会生效。确定继续吗？',
      '重新分片',
      { confirmButtonText: '重新分片', cancelButtonText: '取消', type: 'warning' },
    )
    reChunking.value = true
    await api.reChunkFile(kbId, fileId)
    ElMessage.success('已触发重新分片，处理完成后列表将自动更新')
    // 轮询文件状态，完成后刷新列表
    pollReChunkStatus()
  } catch (e: any) {
    if (e !== 'cancel' && e?.message) {
      ElMessage.error(e?.message || '重新分片失败')
    }
  } finally {
    reChunking.value = false
  }
}

let reChunkTimer: ReturnType<typeof setInterval> | null = null
function pollReChunkStatus() {
  if (reChunkTimer) clearInterval(reChunkTimer)
  reChunkTimer = setInterval(async () => {
    try {
      const res: any = await api.getFileProcessingStatus(kbId, fileId)
      const status = res?.status || ''
      if (status === 'completed') {
        if (reChunkTimer) { clearInterval(reChunkTimer); reChunkTimer = null }
        ElMessage.success('重新分片完成')
        loadChunks()
        loadFileInfo()
      } else if (status === 'failed') {
        if (reChunkTimer) { clearInterval(reChunkTimer); reChunkTimer = null }
        ElMessage.error('重新分片失败，请查看文件处理状态')
      }
    } catch {
      // 忽略轮询错误，继续等待
    }
  }, 3000)
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

function getCategoryIcon(category: string): string {
  const icons: Record<string, string> = {
    document: '📄',
    video: '🎬',
    audio: '🎵',
    image: '🖼️',
  }
  return icons[category] || '📄'
}

// --- Markdown methods ---
function saveMarkdownEdit() {
  ElMessage.success('Markdown 内容已保存')
}

function copyMarkdown() {
  navigator.clipboard.writeText(markdownContent.value).then(() => {
    ElMessage.success('已复制到剪贴板')
  }).catch(() => {
    ElMessage.info('复制失败，请手动复制')
  })
}

function exportMarkdown() {
  const blob = new Blob([markdownContent.value], { type: 'text/markdown' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${fileInfo.value.name.replace(/\.[^/.]+$/, '')}.md`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('导出成功')
}

// --- Media player methods ---
function formatTime(seconds: number): string {
  if (!seconds || isNaN(seconds)) return '00:00'
  const mins = Math.floor(seconds / 60)
  const secs = Math.floor(seconds % 60)
  return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
}

function togglePlay() {
  const el = audioRef.value || videoRef.value
  if (!el) return
  if (mediaPlaying.value) {
    el.pause()
  } else {
    el.play().catch(() => {})
  }
  mediaPlaying.value = !mediaPlaying.value
}

function selectMediaChunk(chunk: Chunk) {
  mediaSelectedChunkId.value = chunk.id
  if (chunk.startTime !== undefined) {
    const el = audioRef.value || videoRef.value
    if (el) {
      el.currentTime = chunk.startTime
      mediaCurrentTime.value = chunk.startTime
      if (!mediaPlaying.value) {
        el.play().catch(() => {})
        mediaPlaying.value = true
      }
    }
  }
}

function seekTo(time: number) {
  const t = Math.max(0, Math.min(time, mediaDuration.value))
  mediaCurrentTime.value = t
  const el = audioRef.value || videoRef.value
  if (el) {
    el.currentTime = t
  }
}

function onTimelineClick(e: MouseEvent) {
  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
  const x = e.clientX - rect.left
  const percentage = x / rect.width
  seekTo(percentage * mediaDuration.value)
}

const mediaCurrentChunk = computed(() => {
  return mediaChunks.value.find(c =>
    mediaCurrentTime.value >= c.startTime! &&
    mediaCurrentTime.value < c.endTime!
  )
})

async function deleteMediaChunk(chunkId: string) {
  try {
    await ElMessageBox.confirm(
      '确定要删除这个切片吗？删除后将同时移除其向量数据。',
      '删除确认',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
    await api.deleteChunk(kbId, chunkId)
    chunks.value = chunks.value.filter(c => c.id !== chunkId)
    if (mediaSelectedChunkId.value === chunkId) {
      mediaSelectedChunkId.value = null
    }
    ElMessage.success('删除成功')
    loadChunks()
  } catch (e: any) {
    if (e !== 'cancel' && e?.message) {
      ElMessage.error(e?.message || '删除失败')
    }
  }
}

// --- Selection methods ---
function toggleChunkSelection(chunkId: string) {
  if (selectedChunks.value.has(chunkId)) {
    selectedChunks.value.delete(chunkId)
  } else {
    selectedChunks.value.add(chunkId)
  }
  // Update selectAll state
  const allIds = chunks.value.map(c => c.id)
  selectAll.value = allIds.every(id => selectedChunks.value.has(id))
}

function toggleSelectAll() {
  if (selectAll.value) {
    selectedChunks.value.clear()
    selectAll.value = false
  } else {
    chunks.value.forEach(c => selectedChunks.value.add(c.id))
    selectAll.value = true
  }
}

async function batchDelete() {
  if (selectedChunks.value.size === 0) {
    ElMessage.warning('请先选择要删除的分片')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${selectedChunks.value.size} 个分片吗？删除后将同时移除其向量数据。` +
      (isParentSelected.value ? '（选中的父分片将连同其全部子分片一起删除）' : ''),
      '批量删除',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
    await api.batchDeleteChunks(kbId, [...selectedChunks.value])
    selectedChunks.value.clear()
    selectAll.value = false
    ElMessage.success('删除成功')
    loadChunks()
  } catch (e: any) {
    if (e !== 'cancel' && e?.message) {
      ElMessage.error(e?.message || '批量删除失败')
    }
  }
}

function batchExport() {
  if (selectedChunks.value.size === 0) {
    ElMessage.warning('请先选择要导出的分片')
    return
  }
  ElMessage.success(`已导出 ${selectedChunks.value.size} 个分片`)
}

onBeforeUnmount(() => {
  if (reChunkTimer) clearInterval(reChunkTimer)
  if (audioBlobUrl.value) URL.revokeObjectURL(audioBlobUrl.value)
  if (videoBlobUrl.value) URL.revokeObjectURL(videoBlobUrl.value)
  Object.values(imageBlobUrls.value).forEach(url => URL.revokeObjectURL(url))
})
</script>

<template>
  <div class="chunks-page">
    <!-- Breadcrumb -->
    <div class="chunks-page__breadcrumb">
      <el-breadcrumb separator="/">
        <el-breadcrumb-item :to="{ path: '/home' }">首页</el-breadcrumb-item>
        <el-breadcrumb-item :to="{ path: '/knowledge' }">知识库</el-breadcrumb-item>
        <el-breadcrumb-item :to="{ path: `/knowledge/${kbId}` }">{{ fileInfo.name }}</el-breadcrumb-item>
        <el-breadcrumb-item>分片管理</el-breadcrumb-item>
      </el-breadcrumb>
    </div>

    <!-- Page header -->
    <div class="chunks-page__header">
      <div class="chunks-page__header-left">
        <el-button :icon="ArrowLeft" link class="chunks-page__back" @click="goBack">
          返回
        </el-button>
        <el-divider direction="vertical" />
        <el-icon :size="20" class="chunks-page__icon"><Grid /></el-icon>
        <div class="chunks-page__title-group">
          <h2 class="chunks-page__name">
            <span class="chunks-page__category-icon">{{ getCategoryIcon(fileInfo.category) }}</span>
            {{ fileInfo.name }}
          </h2>
          <span class="chunks-page__meta">分片管理 · {{ chunkCount }} 个片段</span>
        </div>
      </div>
      <div class="chunks-page__header-right">
        <el-button type="primary" :icon="Plus" @click="openCreateDialog">新增分片</el-button>
        <el-button :loading="reChunking" @click="handleReChunk">重新分片</el-button>
        <el-button :icon="Download" @click="handleDownload">下载分片</el-button>
      </div>
    </div>

    <!-- Content area -->
    <div class="chunks-page__content">
      <!-- QA 模式：问答对视图 -->
      <template v-if="isQaMode">
        <div class="chunks-page__qa-view">
          <!-- QA header -->
          <div class="chunks-page__doc-header">
            <div class="chunks-page__doc-tabs">
              <span class="chunks-page__qa-badge">
                <el-tag type="warning" size="small">QA 模式</el-tag>
              </span>
              <span class="chunks-page__qa-title">问答对管理</span>
              <span class="chunks-page__qa-count">共 {{ qaTotal }} 条</span>
            </div>
          </div>

          <div class="chunks-page__qa-content">
            <div v-loading="qaLoading">
              <el-table :data="qaList" stripe size="small" empty-text="暂无问答对数据">
                <el-table-column type="index" width="50" />
                <el-table-column prop="question" label="问题" min-width="250" show-overflow-tooltip>
                  <template #default="{ row }">
                    <div v-if="editingQaId === row.id" class="qa-edit-cell">
                      <el-input v-model="editingQaQuestion" size="small" placeholder="请输入问题" />
                    </div>
                    <span v-else style="font-weight: 500">{{ row.question }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="answer" label="答案" min-width="300" show-overflow-tooltip>
                  <template #default="{ row }">
                    <div v-if="editingQaId === row.id" class="qa-edit-cell">
                      <el-input v-model="editingQaAnswer" type="textarea" :rows="3" size="small" placeholder="请输入答案" />
                    </div>
                    <span v-else style="color: #606266">{{ row.answer }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="status" label="状态" width="80" align="center">
                  <template #default="{ row }">
                    <el-tag :type="row.status === 'confirmed' ? 'success' : 'info'" size="small">
                      {{ row.status === 'confirmed' ? '已确认' : '草稿' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="source" label="来源" width="80" align="center">
                  <template #default="{ row }">
                    <el-tag :type="row.source === 'ai' ? 'warning' : 'info'" size="small">
                      {{ row.source === 'ai' ? 'AI抽取' : '手动' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="createdAt" label="创建时间" width="160" show-overflow-tooltip />
                <el-table-column label="操作" width="180" fixed="right" align="center">
                  <template #default="{ row }">
                    <template v-if="editingQaId === row.id">
                      <el-button link type="primary" size="small" @click="handleSaveQaEdit">保存</el-button>
                      <el-button link size="small" @click="editingQaId = null">取消</el-button>
                    </template>
                    <template v-else>
                      <el-button v-if="row.status === 'draft'" link type="success" size="small" @click="handleConfirmQa(row)">确认</el-button>
                      <el-button link type="primary" size="small" @click="startEditQa(row)">编辑</el-button>
                      <el-button link type="danger" size="small" @click="handleDeleteQa(row)">删除</el-button>
                    </template>
                  </template>
                </el-table-column>
              </el-table>

              <!-- 分页 -->
              <div class="chunks-page__pagination" v-if="qaTotal > qaPageSize">
                <el-pagination v-model:current-page="qaCurrentPage" v-model:page-size="qaPageSize"
                  :total="qaTotal" :page-sizes="[10, 20, 50, 100]"
                  layout="total, sizes, prev, pager, next, jumper"
                  @current-change="onQaPageChange" @size-change="onQaPageSizeChange" />
              </div>
            </div>
          </div>
        </div>
      </template>

      <!-- 非QA模式：原有布局 -->
      <template v-else>
        <!-- Document type layout -->
      <template v-if="fileInfo.category === 'document'">
        <!-- Tabs header -->
        <div class="chunks-page__doc-header">
          <div class="chunks-page__doc-tabs">
            <el-radio-group v-model="activeTab" size="small">
              <el-radio-button value="markdown">Markdown</el-radio-button>
              <el-radio-button value="chunks">Chunks</el-radio-button>
            </el-radio-group>

            <!-- Search in chunks mode -->
            <div v-if="activeTab === 'chunks'" class="chunks-page__search">
              <el-select v-model="searchMode" size="small" style="width: 90px">
                <el-option label="按ID" value="id" />
                <el-option label="按内容" value="content" />
              </el-select>
              <el-input
                v-model="searchQuery"
                :placeholder="searchMode === 'id' ? '搜索切片ID' : '搜索内容关键词'"
                size="small"
                clearable
                :prefix-icon="Search"
                style="width: 200px"
              />
            </div>
          </div>

          <!-- Batch actions -->
          <div v-if="activeTab === 'chunks'" class="chunks-page__batch-actions">
            <el-checkbox v-model="selectAll" @change="toggleSelectAll">全选</el-checkbox>
            <span v-if="selectedCount > 0" class="chunks-page__selected-count">
              已选 {{ selectedCount }} 项
            </span>
            <el-button
              v-if="selectedCount > 0"
              type="danger"
              size="small"
              :icon="Delete"
              @click="batchDelete"
            >
              批量删除
            </el-button>
            <el-button
              v-if="selectedCount > 0"
              size="small"
              :icon="Upload"
              @click="batchExport"
            >
              批量导出
            </el-button>
          </div>
        </div>

        <div class="chunks-page__doc-content">
          <!-- Main content -->
          <div class="chunks-page__main-content">
            <!-- Markdown editor -->
            <div v-if="activeTab === 'markdown'" class="chunks-page__markdown">
              <!-- Markdown toolbar -->
              <div class="chunks-page__markdown-toolbar">
                <span class="chunks-page__markdown-title">Markdown 编辑器</span>
                <div class="chunks-page__markdown-toolbar-right">
                  <el-button size="small" @click="copyMarkdown">复制</el-button>
                  <el-button size="small" @click="exportMarkdown">导出</el-button>
                  <el-button type="primary" size="small" @click="saveMarkdownEdit">保存</el-button>
                </div>
              </div>

              <!-- Markdown editor -->
              <div class="chunks-page__markdown-editor">
                <el-input
                  v-model="markdownContent"
                  type="textarea"
                  :rows="20"
                  placeholder="编辑 Markdown 内容..."
                  class="chunks-page__markdown-textarea"
                />
              </div>
            </div>

            <!-- Chunks view -->
            <div v-else class="chunks-page__chunks">
              <div class="chunks-page__chunks-list" style="flex: 1; overflow-y: auto;">
                <div
                  v-for="chunk in filteredChunks"
                  :key="chunk.id"
                  class="chunks-page__chunk-wrapper"
                >
                  <!-- Parent chunk -->
                  <div
                    class="chunks-page__chunk-item"
                    :data-chunk-id="chunk.id"
                    :class="{
                      'chunks-page__chunk-item--active': selectedChunk?.id === chunk.id,
                      'chunks-page__chunk-item--has-children': chunk.children && chunk.children.length > 0,
                      'chunks-page__chunk-item--highlight': targetChunkId === chunk.id,
                    }"
                    @click="toggleExpand(chunk)"
                  >
                    <div class="chunks-page__chunk-header">
                      <el-checkbox
                        :model-value="selectedChunks.has(chunk.id)"
                        @click.stop
                        @change="toggleChunkSelection(chunk.id)"
                      />
                      <!-- 父子模式：父分片行可展开/收起子分片 -->
                      <el-icon
                        v-if="chunk.children && chunk.children.length > 0"
                        class="chunks-page__chunk-expand"
                      >
                        <ArrowRight v-if="selectedChunk?.id !== chunk.id" />
                        <ArrowDown v-else />
                      </el-icon>
                      <span class="chunks-page__chunk-index">#{{ chunk.index }}</span>
                      <el-tag v-if="chunk.chunkType === 'parent'" size="small" type="primary">父分片</el-tag>
                      <el-tag v-if="chunk.chunkType === 'image'" size="small" type="warning">图片</el-tag>
                      <el-tag v-else-if="chunk.chunkType === 'table'" size="small" type="success">表格</el-tag>
                      <el-tag v-else-if="chunk.chunkType === 'code'" size="small" type="info">代码</el-tag>
                      <span v-if="chunk.title" class="chunks-page__chunk-title" :title="chunk.headingPath || ''">{{ chunk.title }}</span>
                      <span class="chunks-page__chunk-id">ID: {{ chunk.id }}</span>
                      <el-button
                        :icon="Edit"
                        link
                        size="small"
                        @click.stop="startEdit(chunk)"
                      />
                    </div>

                    <div class="chunks-page__chunk-content">
                      {{ chunk.content }}
                    </div>

                    <!-- 图片分片：展示缩略图 -->
                    <div v-if="chunk.chunkType === 'image' && chunk.imageKeys && chunk.imageKeys.length > 0" class="chunks-page__chunk-images">
                      <img
                        v-for="(key, idx) in chunk.imageKeys"
                        :key="idx"
                        :src="getImageUrl(key)"
                        class="chunks-page__chunk-thumb"
                        @click="previewImage(key)"
                      />
                    </div>

                    <!-- Metadata -->
                    <div class="chunks-page__chunk-meta">
                      <span>Token: {{ chunk.metadata.tokenCount }}</span>
                      <span>更新: {{ chunk.metadata.updatedAt }}</span>
                      <span v-if="chunk.children && chunk.children.length > 0" class="chunks-page__chunk-children-count">
                        子分片: {{ chunk.children.length }}
                      </span>
                    </div>
                  </div>

                  <!-- Children chunks -->
                  <div v-if="chunk.children && chunk.children.length > 0 && selectedChunk?.id === chunk.id" class="chunks-page__children">
                    <div
                      v-for="child in chunk.children"
                      :key="child.id"
                      class="chunks-page__chunk-item chunks-page__chunk-item--child"
                      :data-chunk-id="child.id"
                      :class="{ 'chunks-page__chunk-item--highlight': targetChunkId === child.id }"
                    >
                      <div class="chunks-page__chunk-header">
                        <el-checkbox
                          :model-value="selectedChunks.has(child.id)"
                          @click.stop
                          @change="toggleChunkSelection(child.id)"
                        />
                        <span class="chunks-page__chunk-index">#{{ chunk.index }}.{{ child.index }}</span>
                        <el-tag v-if="child.chunkType === 'image'" size="small" type="warning">图片</el-tag>
                        <el-tag v-else-if="child.chunkType === 'table'" size="small" type="success">表格</el-tag>
                        <el-tag v-else-if="child.chunkType === 'code'" size="small" type="info">代码</el-tag>
                        <span class="chunks-page__chunk-id">ID: {{ child.id }}</span>
                        <el-button
                          :icon="Edit"
                          link
                          size="small"
                          @click.stop="startEdit(child)"
                        />
                      </div>

                      <div class="chunks-page__chunk-content">
                        {{ child.content }}
                      </div>

                      <div class="chunks-page__chunk-meta">
                        <span>Token: {{ child.metadata.tokenCount }}</span>
                        <span>更新: {{ child.metadata.updatedAt }}</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
              <!-- Pagination -->
              <div class="chunks-page__pagination">
                <el-pagination
                  v-model:current-page="currentPage"
                  v-model:page-size="pageSize"
                  :page-sizes="[10, 20, 50, 100]"
                  :total="totalChunks"
                  layout="total, sizes, prev, pager, next, jumper"
                  @current-change="handlePageChange"
                  @size-change="handleSizeChange"
                />
              </div>
            </div>
          </div>

          <!-- Metadata sidebar (right) -->
          <div class="chunks-page__metadata" :class="{ 'is-collapsed': metadataCollapsed }">
            <div class="chunks-page__metadata-header" @click="metadataCollapsed = !metadataCollapsed">
              <span class="chunks-page__metadata-title">元数据信息</span>
              <el-icon>
                <ArrowUp v-if="!metadataCollapsed" />
                <ArrowDown v-else />
              </el-icon>
            </div>

            <div v-show="!metadataCollapsed" class="chunks-page__metadata-body">
              <!-- File info -->
              <div class="chunks-page__metadata-section">
                <div class="chunks-page__metadata-item">
                  <span class="chunks-page__metadata-label">文件名</span>
                  <span class="chunks-page__metadata-value">{{ fileInfo.name }}</span>
                </div>
                <div class="chunks-page__metadata-item">
                  <span class="chunks-page__metadata-label">文件类型</span>
                  <span class="chunks-page__metadata-value">{{ fileInfo.extension }}</span>
                </div>
                <div class="chunks-page__metadata-item">
                  <span class="chunks-page__metadata-label">文件大小</span>
                  <span class="chunks-page__metadata-value">{{ formatFileSize(fileInfo.size) }}</span>
                </div>
              </div>

              <el-divider />

              <!-- Chunk stats -->
              <div class="chunks-page__metadata-section">
                <div class="chunks-page__metadata-item">
                  <span class="chunks-page__metadata-label">总分片数</span>
                  <span class="chunks-page__metadata-value">{{ chunkCount }}</span>
                </div>
                <div class="chunks-page__metadata-item">
                  <span class="chunks-page__metadata-label">父分片数</span>
                  <span class="chunks-page__metadata-value">{{ parentChunkCount }}</span>
                </div>
                <div class="chunks-page__metadata-item">
                  <span class="chunks-page__metadata-label">总 Token</span>
                  <span class="chunks-page__metadata-value">{{ totalTokens.toLocaleString() }}</span>
                </div>
              </div>

              <el-divider />

              <!-- Strategy info -->
              <div class="chunks-page__metadata-section">
                <div class="chunks-page__metadata-item">
                  <span class="chunks-page__metadata-label">分块策略</span>
                  <span class="chunks-page__metadata-value">{{ fileInfo.parseStrategyName || fileInfo.strategy }}</span>
                </div>
                <div class="chunks-page__metadata-item">
                  <span class="chunks-page__metadata-label">分块大小</span>
                  <span class="chunks-page__metadata-value">{{ fileInfo.chunkSize }}</span>
                </div>
                <div class="chunks-page__metadata-item">
                  <span class="chunks-page__metadata-label">重叠大小</span>
                  <span class="chunks-page__metadata-value">{{ fileInfo.overlapSize }}</span>
                </div>
                <div class="chunks-page__metadata-item">
                  <span class="chunks-page__metadata-label">嵌入模型</span>
                  <span class="chunks-page__metadata-value">{{ fileInfo.embeddingModel }}</span>
                </div>
              </div>

              <el-divider />

              <!-- Time info -->
              <div class="chunks-page__metadata-section">
                <div class="chunks-page__metadata-item">
                  <span class="chunks-page__metadata-label">创建时间</span>
                  <span class="chunks-page__metadata-value">{{ fileInfo.createdAt }}</span>
                </div>
                <div class="chunks-page__metadata-item">
                  <span class="chunks-page__metadata-label">更新时间</span>
                  <span class="chunks-page__metadata-value">{{ fileInfo.updatedAt }}</span>
                </div>
              </div>

              <!-- Selected chunk info -->
              <template v-if="selectedChunk">
                <el-divider />
                <h5 class="chunks-page__metadata-subtitle">当前选中分片</h5>
                <div class="chunks-page__metadata-section">
                  <div class="chunks-page__metadata-item">
                    <span class="chunks-page__metadata-label">分片 ID</span>
                    <span class="chunks-page__metadata-value chunks-page__metadata-value--id">{{ selectedChunk.id }}</span>
                  </div>
                  <div class="chunks-page__metadata-item">
                    <span class="chunks-page__metadata-label">分片索引</span>
                    <span class="chunks-page__metadata-value">{{ selectedChunk.index }}</span>
                  </div>
                  <div class="chunks-page__metadata-item">
                    <span class="chunks-page__metadata-label">Token 数</span>
                    <span class="chunks-page__metadata-value">{{ selectedChunk.metadata.tokenCount }}</span>
                  </div>
                  <div class="chunks-page__metadata-item">
                    <span class="chunks-page__metadata-label">父分片</span>
                    <span class="chunks-page__metadata-value">{{ selectedChunk.parentId || '无' }}</span>
                  </div>
                </div>
              </template>
            </div>
          </div>
        </div>
      </template>

      <!-- Image type -->
      <template v-else-if="fileInfo.category === 'image'">
        <div class="chunks-page__image-layout">
          <!-- Left: Image preview -->
          <div class="chunks-page__image-left">
            <div class="chunks-page__image-preview">
              <img
                v-if="imageUrl"
                :src="imageUrl"
                :alt="fileInfo.name"
                class="chunks-page__image-img"
              />
              <div v-else class="chunks-page__image-placeholder">
                <el-icon :size="64"><ArrowLeft /></el-icon>
                <span>图片预览</span>
              </div>
            </div>
            <!-- Image metadata -->
            <div class="chunks-page__image-meta">
              <div class="chunks-page__image-meta-item">
                <span class="chunks-page__image-meta-label">尺寸</span>
                <span class="chunks-page__image-meta-value">{{ imageInfo.width }} x {{ imageInfo.height }}</span>
              </div>
              <div class="chunks-page__image-meta-item">
                <span class="chunks-page__image-meta-label">格式</span>
                <span class="chunks-page__image-meta-value">{{ imageInfo.format }}</span>
              </div>
              <div class="chunks-page__image-meta-item">
                <span class="chunks-page__image-meta-label">大小</span>
                <span class="chunks-page__image-meta-value">{{ formatFileSize(fileInfo.size) }}</span>
              </div>
            </div>
          </div>

          <!-- Right: OCR results -->
          <div class="chunks-page__image-right">
            <div class="chunks-page__image-ocr-header">
              <span>OCR 识别结果</span>
              <span class="chunks-page__image-ocr-count">共 {{ chunks.length }} 个文本块</span>
            </div>
            <div class="chunks-page__image-ocr-list">
              <div
                v-for="chunk in chunks"
                :key="chunk.id"
                class="chunks-page__image-ocr-item"
                :class="{ 'is-selected': selectedChunk?.id === chunk.id }"
                @click="toggleExpand(chunk)"
              >
                <div class="chunks-page__image-ocr-header">
                  <span class="chunks-page__image-ocr-index">#{{ chunk.index }}</span>
                  <span class="chunks-page__image-ocr-id">ID: {{ chunk.id }}</span>
                  <el-button :icon="Edit" link size="small" @click.stop="startEdit(chunk)" />
                </div>
                <div class="chunks-page__image-ocr-content">
                  {{ chunk.content }}
                </div>
                <div class="chunks-page__image-ocr-meta">
                  <span>Token: {{ chunk.metadata.tokenCount }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </template>

      <!-- Video/Audio type -->
      <template v-else>
        <div class="chunks-page__media-layout">
          <!-- Left: Player + Timeline -->
          <div class="chunks-page__media-left">
            <!-- Video player -->
            <div v-if="fileInfo.category === 'video'" class="chunks-page__video">
              <div v-if="videoBlobUrl" class="chunks-page__video-player">
                <video
                  ref="videoRef"
                  :src="videoBlobUrl"
                  controls
                  preload="metadata"
                  @loadedmetadata="onVideoMetadataLoaded"
                  @timeupdate="onVideoTimeUpdate"
                  @ended="onVideoEnded"
                  @play="mediaPlaying = true"
                  @pause="mediaPlaying = false"
                  class="chunks-page__video-element"
                />
              </div>
              <div v-else class="chunks-page__video-placeholder">
                <el-icon :size="64"><ArrowLeft /></el-icon>
                <span>正在加载视频...</span>
              </div>
            </div>

            <!-- Audio player -->
            <div v-else class="chunks-page__audio">
              <div v-if="audioBlobUrl" class="chunks-page__audio-player">
                <audio
                  ref="audioRef"
                  :src="audioBlobUrl"
                  preload="metadata"
                  @loadedmetadata="onAudioMetadataLoaded"
                  @timeupdate="onAudioTimeUpdate"
                  @ended="onAudioEnded"
                  @play="mediaPlaying = true"
                  @pause="mediaPlaying = false"
                  style="display:none"
                />
                <div class="chunks-page__audio-waveform">
                  <div class="chunks-page__audio-wave-icon">
                    <el-icon :size="32" :class="{ rotating: mediaPlaying }"><ArrowLeft /></el-icon>
                  </div>
                  <div class="chunks-page__audio-wave-text">
                    <span>{{ fileInfo.name }}</span>
                    <span class="chunks-page__audio-wave-time">{{ formatTime(mediaCurrentTime) }} / {{ formatTime(mediaDuration) }}</span>
                  </div>
                </div>
              </div>
              <div v-else class="chunks-page__audio-placeholder">
                <el-icon :size="48"><ArrowLeft /></el-icon>
                <span>{{ fileInfo.category === 'audio' ? '正在加载音频...' : '音频暂不可用' }}</span>
              </div>
            </div>

            <!-- Player controls -->
            <div class="chunks-page__player-controls">
              <el-button :icon="mediaPlaying ? VideoPause : VideoPlay" circle @click="togglePlay" />
              <span class="chunks-page__player-time">{{ formatTime(mediaCurrentTime) }}</span>
              <el-slider
                :model-value="mediaCurrentTime"
                :max="mediaDuration"
                :show-tooltip="false"
                @input="(v: number | number[]) => seekTo(Array.isArray(v) ? v[0] : v)"
                class="chunks-page__player-progress"
              />
              <span class="chunks-page__player-time">{{ formatTime(mediaDuration) }}</span>
            </div>

            <!-- Timeline -->
            <div class="chunks-page__timeline">
              <div class="chunks-page__timeline-header">
                <span>时间轴</span>
                <span class="chunks-page__timeline-info">点击切片跳转</span>
              </div>
              <div class="chunks-page__timeline-track" @click="onTimelineClick">
                <!-- Chunk segments on timeline -->
                <div
                  v-for="chunk in mediaChunks"
                  :key="chunk.id"
                  class="chunks-page__timeline-segment"
                  :class="{
                    'is-active': mediaCurrentChunk?.id === chunk.id,
                    'is-selected': mediaSelectedChunkId === chunk.id,
                  }"
                  :style="{
                    left: `${(chunk.startTime! / mediaDuration) * 100}%`,
                    width: `${((chunk.endTime! - chunk.startTime!) / mediaDuration) * 100}%`,
                  }"
                  @click.stop="selectMediaChunk(chunk)"
                >
                  <span class="chunks-page__timeline-segment-label">#{{ chunk.index }}</span>
                </div>
                <!-- Current position indicator -->
                <div
                  class="chunks-page__timeline-cursor"
                  :style="{ left: `${(mediaCurrentTime / mediaDuration) * 100}%` }"
                />
              </div>
            </div>
          </div>

          <!-- Right: Chunk list -->
          <div class="chunks-page__media-right">
            <div class="chunks-page__media-chunks-header">
              <span>时间轴切片</span>
              <span class="chunks-page__media-chunks-count">共 {{ mediaChunks.length }} 个片段</span>
            </div>
            <div class="chunks-page__media-chunks-list">
              <div
                v-for="chunk in mediaChunks"
                :key="chunk.id"
                class="chunks-page__media-chunk-item"
                :class="{
                  'is-active': mediaCurrentChunk?.id === chunk.id,
                  'is-selected': mediaSelectedChunkId === chunk.id,
                }"
                @click="selectMediaChunk(chunk)"
              >
                <div class="chunks-page__media-chunk-header">
                  <span class="chunks-page__media-chunk-index">#{{ chunk.index }}</span>
                  <span class="chunks-page__media-chunk-time">
                    {{ formatTime(chunk.startTime!) }} - {{ formatTime(chunk.endTime!) }}
                  </span>
                  <div class="chunks-page__media-chunk-actions">
                    <el-button :icon="Edit" link size="small" @click.stop="startEdit(chunk)" />
                    <el-button :icon="Delete" link size="small" type="danger" @click.stop="deleteMediaChunk(chunk.id)" />
                  </div>
                </div>
                <div class="chunks-page__media-chunk-content">
                  {{ chunk.content }}
                </div>
              </div>
            </div>
          </div>
        </div>
      </template>
      </template> <!-- close 非QA模式 -->
    </div>

    <!-- Edit Dialog -->
    <el-dialog
      v-model="editDialogVisible"
      title="编辑分片"
      width="70%"
      style="max-width: 900px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <div class="edit-dialog" v-if="editingChunk">
        <div class="edit-dialog__info">
          <span class="edit-dialog__label">分片 ID:</span>
          <span class="edit-dialog__value edit-dialog__value--id">{{ editingChunk.id }}</span>
          <span class="edit-dialog__label" style="margin-left: 16px">索引:</span>
          <span class="edit-dialog__value">{{ editingChunk.index }}</span>
          <span class="edit-dialog__label" style="margin-left: 16px">Token:</span>
          <span class="edit-dialog__value">{{ editingChunk.metadata.tokenCount }}</span>
        </div>
        <el-input
          v-model="editingContent"
          type="textarea"
          :rows="16"
          placeholder="编辑分片内容..."
          class="edit-dialog__textarea"
        />
      </div>
      <template #footer>
        <div class="edit-dialog__footer">
          <el-button @click="cancelEdit">取消</el-button>
          <el-button type="primary" :loading="editSaving" @click="saveEdit">保存</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- Create Chunk Dialog -->
    <el-dialog
      v-model="createDialogVisible"
      title="新增分片"
      width="70%"
      style="max-width: 900px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <div class="create-dialog">
        <el-form label-position="top">
          <el-form-item label="分片内容" required>
            <el-input
              v-model="newChunkContent"
              type="textarea"
              :rows="10"
              placeholder="请输入分片内容..."
            />
          </el-form-item>

          <el-form-item label="插入位置">
            <div class="create-dialog__insert-position">
              <el-radio-group v-model="newChunkInsertMode">
                <el-radio value="append">追加到末尾</el-radio>
                <el-radio value="after">指定索引之后</el-radio>
              </el-radio-group>
              <el-input-number
                v-if="newChunkInsertMode === 'after'"
                v-model="newChunkInsertIndex"
                :min="0"
                :max="totalChunks"
                placeholder="chunkIndex"
                style="margin-left: 16px; width: 180px"
              />
            </div>
          </el-form-item>

          <el-form-item label="分片类型" v-if="fileInfo.category === 'document'">
            <el-radio-group v-model="newChunkType">
              <el-radio value="text">文本</el-radio>
              <el-radio value="image">图片</el-radio>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="页码" v-if="fileInfo.category === 'document' && newChunkType === 'image'">
            <el-input-number v-model="newChunkPageNumber" :min="1" placeholder="PDF 页码" style="width: 180px" />
          </el-form-item>

          <template v-if="fileInfo.category === 'audio' || fileInfo.category === 'video'">
            <el-form-item label="开始时间（秒）">
              <el-input-number v-model="newChunkStartTime" :min="0" :precision="1" placeholder="开始时间" style="width: 180px" />
            </el-form-item>
            <el-form-item label="结束时间（秒）">
              <el-input-number v-model="newChunkEndTime" :min="0" :precision="1" placeholder="结束时间" style="width: 180px" />
            </el-form-item>
          </template>
        </el-form>
      </div>
      <template #footer>
        <div class="edit-dialog__footer">
          <el-button @click="createDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="createSaving" @click="handleCreateChunk">创建</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.chunks-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: $bg-white;
  border-radius: $radius-base;
  box-shadow: $shadow-sm;
  overflow: hidden;

  // --- Breadcrumb ---
  &__breadcrumb {
    padding: $spacing-sm $spacing-lg;
    border-bottom: 1px solid $border-lighter;
    background: $bg-hover;
  }

  // --- Header ---
  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: $spacing-base $spacing-lg;
    border-bottom: 1px solid $border-lighter;
    flex-shrink: 0;
  }

  &__header-left {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
  }

  &__back {
    font-size: 14px;
  }

  &__icon {
    color: $color-primary;
  }

  &__title-group {
    display: flex;
    align-items: baseline;
    gap: $spacing-sm;
  }

  &__name {
    margin: 0;
    font-size: 18px;
    font-weight: 600;
    color: $text-primary;
    line-height: 1.3;
    display: flex;
    align-items: center;
    gap: $spacing-xs;
  }

  &__category-icon {
    font-size: 20px;
  }

  &__meta {
    font-size: 13px;
    color: $text-secondary;
  }

  &__header-right {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
  }

  // --- Content ---
  &__content {
    flex: 1;
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }

  // --- Document header ---
  &__doc-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: $spacing-base $spacing-lg;
    border-bottom: 1px solid $border-lighter;
    flex-shrink: 0;
    gap: $spacing-base;
  }

  &__doc-tabs {
    display: flex;
    align-items: center;
    gap: $spacing-base;
  }

  &__search {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
  }

  &__batch-actions {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
  }

  &__selected-count {
    font-size: 13px;
    color: $text-secondary;
  }

  // --- Document content ---
  &__doc-content {
    flex: 1;
    display: flex;
    overflow: hidden;
  }

  // --- Metadata sidebar (right) ---
  &__metadata {
    width: 280px;
    flex-shrink: 0;
    border-left: 1px solid $border-lighter;
    display: flex;
    flex-direction: column;
    background: $bg-hover;
    transition: width 0.3s ease;

    &.is-collapsed {
      width: 48px;
    }
  }

  &__metadata-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: $spacing-base;
    cursor: pointer;
    border-bottom: 1px solid $border-lighter;
    background: $bg-white;

    &:hover {
      background: $bg-hover;
    }
  }

  &__metadata-title {
    font-size: 14px;
    font-weight: 600;
    color: $text-primary;
    white-space: nowrap;
    overflow: hidden;

    .is-collapsed & {
      writing-mode: vertical-rl;
      text-orientation: mixed;
    }
  }

  &__metadata-body {
    flex: 1;
    overflow-y: auto;
    padding: $spacing-base;
  }

  &__metadata-subtitle {
    margin: 0 0 $spacing-sm;
    font-size: 13px;
    font-weight: 600;
    color: $text-primary;
  }

  &__metadata-section {
    display: flex;
    flex-direction: column;
    gap: $spacing-sm;
  }

  &__metadata-item {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  &__metadata-label {
    font-size: 11px;
    color: $text-placeholder;
  }

  &__metadata-value {
    font-size: 13px;
    color: $text-primary;
    word-break: break-all;

    &--id {
      font-family: monospace;
      font-size: 11px;
      background: $bg-white;
      padding: 2px 6px;
      border-radius: $radius-sm;
    }
  }

  // --- Main content ---
  &__main-content {
    flex: 1;
    display: flex;
    flex-direction: column;
    overflow: hidden;
    min-width: 0;
  }

  // --- Markdown editor ---
  &__markdown {
    flex: 1;
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }

  &__markdown-toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: $spacing-sm $spacing-lg;
    border-bottom: 1px solid $border-lighter;
    background: $bg-white;
    flex-shrink: 0;
  }

  &__markdown-title {
    font-size: 14px;
    font-weight: 500;
    color: $text-primary;
  }

  &__markdown-toolbar-right {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
  }

  &__markdown-editor {
    flex: 1;
    display: flex;
    flex-direction: column;
    overflow: hidden;
    padding: $spacing-base;
  }

  &__markdown-textarea {
    flex: 1;

    :deep(.el-textarea__inner) {
      height: 100% !important;
      font-family: monospace;
      font-size: 14px;
      line-height: 1.6;
      resize: none;
    }
  }

  // --- Chunks view ---
  &__chunks {
    flex: 1;
    display: flex;
    flex-direction: column;
    overflow: hidden;
    padding: $spacing-base;
  }

  // --- Pagination ---
  &__pagination {
    display: flex;
    justify-content: flex-end;
    padding: $spacing-base 0;
    border-top: 1px solid $border-lighter;
    flex-shrink: 0;
  }

  &__chunks-list {
    display: flex;
    flex-direction: column;
    gap: $spacing-sm;
  }

  &__chunk-wrapper {
    display: flex;
    flex-direction: column;
  }

  &__chunk-item {
    border: 1px solid $border-lighter;
    border-radius: $radius-base;
    padding: $spacing-base;
    background: $bg-white;
    cursor: pointer;
    transition: all 0.2s;

    &:hover {
      border-color: $color-primary;
    }

    &--active {
      border-color: $color-primary;
      background: #E3F2FD;
    }

    &--highlight {
      border-color: $color-warning;
      background: #FFF8E1;
      box-shadow: 0 0 0 2px rgba(250, 173, 20, 0.25);
    }

    &--has-children {
      border-left: 3px solid $color-primary;
    }

    &--child {
      margin-left: $spacing-xl;
      border-left: 2px solid $border-base;
      background: $bg-hover;
    }
  }

  &__chunk-header {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    margin-bottom: $spacing-sm;
  }

  &__chunk-expand {
    cursor: pointer;
    color: $color-primary;
    font-size: 14px;
  }

  &__chunk-index {
	    font-weight: 600;
	    color: $color-primary;
	    font-size: 14px;
	  }

	  &__chunk-title {
	    font-size: 13px;
	    color: $color-primary;
	    font-weight: 500;
	    max-width: 300px;
	    overflow: hidden;
	    text-overflow: ellipsis;
	    white-space: nowrap;
	    cursor: help;
	    border-left: 2px solid $color-primary;
	    padding-left: $spacing-sm;
	  }

	  &__chunk-id {
    font-size: 12px;
    color: $text-secondary;
    font-family: monospace;
  }

  &__chunk-content {
    font-size: 13px;
    color: $text-regular;
    line-height: 1.6;
    display: -webkit-box;
    -webkit-line-clamp: 3;
    -webkit-box-orient: vertical;
    overflow: hidden;
    word-break: break-word;
  }

  &__chunk-images {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-top: 8px;
  }

  &__chunk-thumb {
    width: 120px;
    height: 90px;
    object-fit: cover;
    border-radius: 4px;
    border: 1px solid #eee;
    cursor: pointer;
    transition: transform 0.2s;

    &:hover {
      transform: scale(1.05);
      box-shadow: 0 2px 8px rgba(0,0,0,0.15);
    }
  }

  &__chunk-edit {
    display: flex;
    flex-direction: column;
    gap: $spacing-sm;
  }

  &__chunk-edit-actions {
    display: flex;
    justify-content: flex-end;
    gap: $spacing-sm;
  }

  &__chunk-meta {
    display: flex;
    gap: $spacing-base;
    margin-top: $spacing-sm;
    font-size: 11px;
    color: $text-placeholder;
  }

  &__chunk-children-count {
    color: $color-primary;
  }

  &__children {
    margin-top: $spacing-xs;
  }

  // --- Media layout ---
  &__media-layout {
    flex: 1;
    display: flex;
    overflow: hidden;
  }

  &__media-left {
    flex: 1;
    display: flex;
    flex-direction: column;
    border-right: 1px solid $border-lighter;
    overflow: hidden;
  }

  &__media-right {
    width: 360px;
    flex-shrink: 0;
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }

  // --- Video player ---
  &__video {
    flex: 1;
    background: #000;
    min-height: 300px;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  &__video-element {
    width: 100%;
    height: 100%;
    max-height: 500px;
    object-fit: contain;
  }

  &__video-player {
    width: 100%;
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  &__video-placeholder {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: $spacing-sm;
    color: #fff;
  }

  &__video-time {
    font-size: 14px;
    font-family: monospace;
  }

  // --- Audio player ---
  &__audio {
    padding: $spacing-lg;
    background: $bg-hover;
  }

  &__audio-player {
    display: flex;
    flex-direction: column;
    gap: $spacing-base;
  }

  &__audio-placeholder {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: $spacing-sm;
    color: $text-secondary;
    padding: $spacing-lg 0;
  }

  &__audio-waveform {
    display: flex;
    align-items: flex-end;
    gap: 2px;
    height: 60px;
    padding: $spacing-sm;
    background: $bg-white;
    border-radius: $radius-base;
  }

  &__wave-bar {
    width: 4px;
    background: $color-primary;
    border-radius: 2px;
    opacity: 0.6;

    &:hover {
      opacity: 1;
    }
  }

  // --- Player controls ---
  &__player-controls {
    display: flex;
    align-items: center;
    gap: $spacing-base;
    padding: $spacing-sm $spacing-lg;
    border-top: 1px solid $border-lighter;
    background: $bg-white;
  }

  &__player-progress {
    flex: 1;
  }

  &__player-time {
    font-size: 12px;
    font-family: monospace;
    color: $text-secondary;
    min-width: 45px;
  }

  // --- Timeline ---
  &__timeline {
    border-top: 1px solid $border-lighter;
    background: $bg-white;
  }

  &__timeline-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: $spacing-sm $spacing-lg;
    font-size: 13px;
    font-weight: 500;
    color: $text-primary;
    border-bottom: 1px solid $border-lighter;
  }

  &__timeline-info {
    font-size: 12px;
    font-weight: 400;
    color: $text-secondary;
  }

  &__timeline-track {
    position: relative;
    height: 48px;
    margin: $spacing-sm $spacing-lg;
    background: $bg-hover;
    border-radius: $radius-base;
    cursor: pointer;
    overflow: hidden;
  }

  &__timeline-segment {
    position: absolute;
    top: 4px;
    bottom: 4px;
    background: $color-primary;
    opacity: 0.3;
    border-radius: $radius-sm;
    cursor: pointer;
    transition: all 0.2s;
    display: flex;
    align-items: center;
    justify-content: center;

    &:hover {
      opacity: 0.6;
    }

    &.is-active {
      opacity: 0.8;
      background: $color-success;
    }

    &.is-selected {
      opacity: 1;
      box-shadow: 0 0 0 2px $color-primary;
    }
  }

  &__timeline-segment-label {
    font-size: 10px;
    color: #fff;
    font-weight: 500;
  }

  &__timeline-cursor {
    position: absolute;
    top: 0;
    bottom: 0;
    width: 2px;
    background: $color-danger;
    transform: translateX(-1px);
    pointer-events: none;
  }

  // --- Media chunks list ---
  &__media-chunks-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: $spacing-base $spacing-lg;
    font-size: 14px;
    font-weight: 600;
    color: $text-primary;
    border-bottom: 1px solid $border-lighter;
    background: $bg-white;
  }

  &__media-chunks-count {
    font-size: 12px;
    font-weight: 400;
    color: $text-secondary;
  }

  &__media-chunks-list {
    flex: 1;
    overflow-y: auto;
    padding: $spacing-sm;
  }

  &__media-chunk-item {
    padding: $spacing-base;
    border: 1px solid $border-lighter;
    border-radius: $radius-base;
    margin-bottom: $spacing-sm;
    cursor: pointer;
    transition: all 0.2s;

    &:hover {
      border-color: $color-primary;
    }

    &.is-active {
      border-color: $color-success;
      background: #E8F5E9;
    }

    &.is-selected {
      border-color: $color-primary;
      background: #E3F2FD;
    }
  }

  &__media-chunk-header {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    margin-bottom: $spacing-xs;
  }

  &__media-chunk-index {
    font-weight: 600;
    color: $color-primary;
    font-size: 13px;
  }

  &__media-chunk-time {
    font-size: 12px;
    font-family: monospace;
    color: $text-secondary;
  }

  &__media-chunk-actions {
    margin-left: auto;
    display: flex;
    gap: $spacing-xs;
  }

  &__media-chunk-content {
    font-size: 13px;
    color: $text-regular;
    line-height: 1.5;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  // --- Image layout ---
  &__image-layout {
    flex: 1;
    display: flex;
    overflow: hidden;
  }

  &__image-left {
    flex: 1;
    display: flex;
    flex-direction: column;
    border-right: 1px solid $border-lighter;
    overflow: hidden;
  }

  &__image-right {
    width: 360px;
    flex-shrink: 0;
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }

  &__image-preview {
    flex: 1;
    background: $bg-hover;
    display: flex;
    align-items: center;
    justify-content: center;
    min-height: 300px;
    overflow: hidden;
  }

  &__image-img {
    max-width: 100%;
    max-height: 100%;
    object-fit: contain;
  }

  &__image-placeholder {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: $spacing-sm;
    color: $text-secondary;
  }

  &__image-info {
    font-size: 12px;
    color: $text-placeholder;
  }

  &__image-meta {
    display: flex;
    gap: $spacing-lg;
    padding: $spacing-base $spacing-lg;
    background: $bg-white;
    border-top: 1px solid $border-lighter;
  }

  &__image-meta-item {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  &__image-meta-label {
    font-size: 11px;
    color: $text-placeholder;
  }

  &__image-meta-value {
    font-size: 13px;
    color: $text-primary;
    font-weight: 500;
  }

  &__image-ocr-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: $spacing-base $spacing-lg;
    font-size: 14px;
    font-weight: 600;
    color: $text-primary;
    border-bottom: 1px solid $border-lighter;
    background: $bg-white;
  }

  &__image-ocr-count {
    font-size: 12px;
    font-weight: 400;
    color: $text-secondary;
  }

  &__image-ocr-list {
    flex: 1;
    overflow-y: auto;
    padding: $spacing-sm;
  }

  &__image-ocr-item {
    padding: $spacing-base;
    border: 1px solid $border-lighter;
    border-radius: $radius-base;
    margin-bottom: $spacing-sm;
    cursor: pointer;
    transition: all 0.2s;

    &:hover {
      border-color: $color-primary;
    }

    &.is-selected {
      border-color: $color-primary;
      background: #E3F2FD;
    }
  }

  &__image-ocr-header {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    margin-bottom: $spacing-xs;
  }

  &__image-ocr-index {
    font-weight: 600;
    color: $color-primary;
    font-size: 13px;
  }

  &__image-ocr-id {
    font-size: 12px;
    color: $text-secondary;
    font-family: monospace;
  }

  &__image-ocr-content {
    font-size: 13px;
    color: $text-regular;
    line-height: 1.5;
    white-space: pre-wrap;
    margin-bottom: $spacing-xs;
  }

  &__image-ocr-meta {
    font-size: 11px;
    color: $text-placeholder;
  }
}

// --- Edit Dialog ---
.edit-dialog {
  &__info {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    padding: $spacing-base;
    background: $bg-hover;
    border-radius: $radius-base;
    margin-bottom: $spacing-base;
  }

  &__label {
    font-size: 13px;
    color: $text-secondary;
  }

  &__value {
    font-size: 13px;
    color: $text-primary;
    font-weight: 500;

    &--id {
      font-family: monospace;
      background: $bg-white;
      padding: 2px 8px;
      border-radius: $radius-sm;
    }
  }

  &__textarea {
    :deep(.el-textarea__inner) {
      font-size: 14px;
      line-height: 1.8;
      font-family: monospace;
    }
  }

  &__footer {
    display: flex;
    justify-content: flex-end;
    gap: $spacing-sm;
  }
}

// --- Create Chunk Dialog ---
.create-dialog {
  &__insert-position {
    display: flex;
    align-items: center;
  }
}

// --- QA view styles ---
.chunks-page__qa-view {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: $spacing-base;

  &-badge {
    margin-right: $spacing-sm;
  }

  &-title {
    font-size: 14px;
    font-weight: 600;
    color: $text-primary;
  }

  &-count {
    font-size: 13px;
    color: $text-secondary;
    margin-left: $spacing-sm;
  }
}

.qa-edit-cell {
  display: flex;
  align-items: flex-start;
}
</style>
