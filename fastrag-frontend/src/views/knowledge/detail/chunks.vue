<script setup lang="ts">
import { useRouter, useRoute } from 'vue-router'
import { onBeforeUnmount, watch } from 'vue'
import { ArrowLeft, Download, Search, Edit, Grid, ArrowDown, ArrowUp, Delete, Upload, Setting, VideoPlay, VideoPause } from '@element-plus/icons-vue'
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
  chunkType?: string        // "text" | "image"
  imageKeys?: string[]      // PDF 提取的图片 key 列表
  pageNumber?: number       // 所属页码
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
  chunkSize: 500,
  overlapSize: 50,
  embeddingModel: 'text-embedding-v4',
  url: '',
  createdAt: '',
  updatedAt: '',
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

async function loadChunks() {
  try {
    const res: any = await api.getChunks(kbId, { fileId, page: currentPage.value, pageSize: pageSize.value })
    const list = res?.list || res || []
    totalChunks.value = res?.total || 0
    chunks.value = list.map((mc: any, i: number) => ({
      id: mc.id || `chunk_${mc.chunkIndex || i}`,
      index: mc.chunkIndex || i,
      content: mc.content || '',
      startTime: mc.startTime ?? undefined,
      endTime: mc.endTime ?? undefined,
      chunkType: mc.chunkType || 'text',
      imageKeys: mc.imageKeys ? (typeof mc.imageKeys === 'string' ? JSON.parse(mc.imageKeys) : mc.imageKeys) : undefined,
      pageNumber: mc.pageNumber ?? undefined,
      metadata: {
        fileId: mc.fileId || fileId,
        fileName: mc.fileName || '',
        chunkIndex: mc.chunkIndex || i,
        tokenCount: (mc.content || '').length,
        createdAt: mc.createdAt || '',
        updatedAt: mc.updatedAt || '',
      },
    }))
    // 生成 markdown 内容
    if (chunks.value.length > 0) {
      markdownContent.value = chunks.value.map((c) => `## 切片 ${c.index}\n\n${c.content}`).join('\n\n---\n\n')
    }
  } catch {
    // ignore
  }
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

function saveEdit() {
  if (!editingChunk.value) return
  editingChunk.value.content = editingContent.value
  editingChunk.value.metadata.updatedAt = new Date().toLocaleString('zh-CN')
  editDialogVisible.value = false
  editingChunk.value = null
  editingContent.value = ''
  ElMessage.success('分片内容已更新')
}

function handleDownload() {
  ElMessage.success('开始下载分片数据')
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
      '确定要删除这个切片吗？',
      '删除确认',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
    chunks.value = chunks.value.filter(c => c.id !== chunkId)
    if (mediaSelectedChunkId.value === chunkId) {
      mediaSelectedChunkId.value = null
    }
    ElMessage.success('删除成功')
  } catch {
    // User cancelled
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
      `确定要删除选中的 ${selectedChunks.value.size} 个分片吗？`,
      '批量删除',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
    chunks.value = chunks.value.filter(c => !selectedChunks.value.has(c.id))
    selectedChunks.value.clear()
    selectAll.value = false
    ElMessage.success('删除成功')
  } catch {
    // User cancelled
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
        <el-button :icon="Download" @click="handleDownload">下载分片</el-button>
      </div>
    </div>

    <!-- Content area -->
    <div class="chunks-page__content">
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
                    :class="{
                      'chunks-page__chunk-item--active': selectedChunk?.id === chunk.id,
                      'chunks-page__chunk-item--has-children': chunk.children && chunk.children.length > 0,
                    }"
                    @click="toggleExpand(chunk)"
                  >
                    <div class="chunks-page__chunk-header">
                      <el-checkbox
                        :model-value="selectedChunks.has(chunk.id)"
                        @click.stop
                        @change="toggleChunkSelection(chunk.id)"
                      />
                      <span class="chunks-page__chunk-index">#{{ chunk.index }}</span>
                      <el-tag v-if="chunk.chunkType === 'image'" size="small" type="warning">图片</el-tag>
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
                    >
                      <div class="chunks-page__chunk-header">
                        <el-checkbox
                          :model-value="selectedChunks.has(child.id)"
                          @click.stop
                          @change="toggleChunkSelection(child.id)"
                        />
                        <span class="chunks-page__chunk-index">#{{ chunk.index }}.{{ child.index }}</span>
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
                  <span class="chunks-page__metadata-value">{{ fileInfo.strategy }}</span>
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
          <el-button type="primary" @click="saveEdit">保存</el-button>
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

  &__chunk-index {
    font-weight: 600;
    color: $color-primary;
    font-size: 14px;
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
</style>
