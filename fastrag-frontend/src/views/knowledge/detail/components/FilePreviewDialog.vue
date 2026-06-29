<script setup lang="ts">
import type { KnowledgeFile } from '@/types/knowledge'
import DocumentPreviewPanel from './DocumentPreviewPanel.vue'
import mammoth from 'mammoth'
import * as XLSX from 'xlsx'
import { init as initPptx } from 'pptx-preview'
import { storage } from '@/utils/storage'

const props = defineProps<{
  visible: boolean
  file: KnowledgeFile | null
  kbId?: string
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'download', file: KnowledgeFile): void
}>()

// Two-way binding for el-dialog v-model
const dialogVisible = computed({
  get: () => props.visible,
  set: (val: boolean) => emit('update:visible', val),
})

// ---------- Preview type derivation ----------

type PreviewType =
  | 'pdf'
  | 'image'
  | 'office'
  | 'text'
  | 'audio'
  | 'video'
  | 'unknown'

const EXTENSION_TO_PREVIEW: Record<string, PreviewType> = {
  // PDF
  '.pdf': 'pdf',
  // Images
  '.jpg': 'image',
  '.jpeg': 'image',
  '.png': 'image',
  '.bmp': 'image',
  '.tiff': 'image',
  '.tif': 'image',
  '.gif': 'image',
  // Office documents
  '.docx': 'office',
  '.doc': 'office',
  '.xlsx': 'office',
  '.xls': 'office',
  '.pptx': 'office',
  '.ppt': 'office',
  // Text
  '.md': 'text',
  '.txt': 'text',
  // Audio
  '.mp3': 'audio',
  '.wav': 'audio',
  '.m4a': 'audio',
  '.aac': 'audio',
  '.ogg': 'audio',
  // Video
  '.mp4': 'video',
  '.avi': 'video',
  '.mov': 'video',
  '.mkv': 'video',
  '.flv': 'video',
}

const previewType = computed<PreviewType>(() => {
  if (!props.file) return 'unknown'
  const ext = '.' + props.file.name.split('.').pop()?.toLowerCase()
  return EXTENSION_TO_PREVIEW[ext] ?? 'unknown'
})

// ---------- Document preview panel：仅文本文件（.md / .txt）走 DocumentPreviewPanel ----------
const showDocumentPreview = computed(() => {
  return previewType.value === 'text'
})

function handleDownload() {
  if (props.file) {
    emit('download', props.file)
  }
}

/** 在新窗口打开（用于 PDF 等） */
function openInNewTab() {
  if (props.file?.url) {
    window.open(props.file.url, '_blank')
  }
}

// ---------- Blob URL for authenticated media (PDF/image/audio/video) ----------
// 浏览器原生 <iframe>/<video>/<audio>/<el-image> 无法携带 Authorization header，
// 因此先通过 fetch 带 token 获取二进制数据，再用 URL.createObjectURL 生成 blob URL 作为 src。

const objectUrl = ref('')
const objectUrlError = ref(false)

async function loadObjectUrl() {
  if (!props.file?.url) return
  // Revoke previous URL
  if (objectUrl.value) {
    URL.revokeObjectURL(objectUrl.value)
    objectUrl.value = ''
  }
  objectUrlError.value = false

  try {
    const token = storage.get('token')
    const response = await fetch(props.file.url, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    })
    if (!response.ok) {
      // 如果带 token 失败，尝试不带 token（部分场景可匿名访问）
      const retry = await fetch(props.file.url)
      if (!retry.ok) {
        objectUrlError.value = true
        return
      }
      const blob = await retry.blob()
      objectUrl.value = URL.createObjectURL(blob)
      return
    }
    const blob = await response.blob()
    objectUrl.value = URL.createObjectURL(blob)
  } catch {
    objectUrlError.value = true
  }
}

// ---------- Office preview state ----------

const officeHtml = ref('')
const officeLoading = ref(false)
const officeError = ref('')
const officePptxRef = ref<HTMLDivElement | null>(null)
let pptxPreviewer: any = null

const isPptx = computed(() => {
  if (!props.file) return false
  const ext = '.' + props.file.name.split('.').pop()?.toLowerCase()
  return ext === '.pptx' || ext === '.ppt'
})

async function loadOfficeContent() {
  if (!props.file?.url) return
  officeHtml.value = ''
  officeError.value = ''
  officeLoading.value = true

  try {
    const token = storage.get('token')
    const response = await fetch(props.file.url, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    })
    if (!response.ok) {
      officeError.value = `文件加载失败（HTTP ${response.status}）`
      officeLoading.value = false
      return
    }
    const arrayBuffer = await response.arrayBuffer()

    const ext = '.' + props.file.name.split('.').pop()?.toLowerCase()

    if (ext === '.docx' || ext === '.doc') {
      const result = await mammoth.convertToHtml({ arrayBuffer })
      officeHtml.value = result.value
    } else if (ext === '.xlsx' || ext === '.xls') {
      const workbook = XLSX.read(arrayBuffer, { type: 'array' })
      const worksheet = workbook.Sheets[workbook.SheetNames[0]]
      officeHtml.value = XLSX.utils.sheet_to_html(worksheet)
    } else if (ext === '.pptx' || ext === '.ppt') {
      // 关闭 loading 状态，让模板渲染出 pptx 容器 div
      officeLoading.value = false
      await nextTick()
      if (officePptxRef.value) {
        if (!pptxPreviewer) {
          pptxPreviewer = initPptx(officePptxRef.value, {
            width: 960,
            height: 540,
          })
        }
        pptxPreviewer.preview(arrayBuffer)
      } else {
        officeError.value = 'PPTX 预览容器未就绪'
      }
      return // pptx 已设置 officeLoading = false
    }
  } catch (e: any) {
    officeError.value = e.message || 'Office 文件预览加载失败'
  }
  officeLoading.value = false
}

function handleClose() {
  emit('update:visible', false)
}

// ---------- Text preview ----------

const textContent = ref('')
const textLoading = ref(false)
const textError = ref('')

async function loadTextContent() {
  if (!props.file) return
  textLoading.value = true
  textError.value = ''
  try {
    // 带 Authorization header + 检查状态码
    const token = storage.get('token')
    const response = await fetch(props.file.url, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    })
    if (!response.ok) {
      if (response.status === 401 || response.status === 403) {
        textError.value = '无权限访问该文件（401/403）'
      } else if (response.status === 404) {
        textError.value = '文件不存在（404）'
      } else {
        textError.value = `加载失败（HTTP ${response.status}）`
      }
      return
    }
    textContent.value = await response.text()
  } catch {
    textError.value = '网络错误，无法加载文件内容'
  } finally {
    textLoading.value = false
  }
}

// Load content when file changes and dialog is visible
watch(
  () => [props.file, previewType.value, props.visible],
  () => {
    if (props.visible && props.file) {
      const type = previewType.value
      if (type === 'text') {
        loadTextContent()
      } else if (type === 'office') {
        loadOfficeContent()
      } else if (type === 'pdf' || type === 'image' || type === 'audio' || type === 'video') {
        loadObjectUrl()
      }
    }
  },
  { immediate: true },
)

// Reset state when dialog closes
watch(
  () => props.visible,
  (val) => {
    if (!val) {
      textContent.value = ''
      officeHtml.value = ''
      officeError.value = ''
      officeLoading.value = false
      pptxPreviewer = null
      if (objectUrl.value) {
        URL.revokeObjectURL(objectUrl.value)
        objectUrl.value = ''
      }
    }
  },
)
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :title="file?.name ?? '文件预览'"
    width="80%"
    top="5vh"
    destroy-on-close
    class="file-preview-dialog"
  >
    <div v-if="file" class="file-preview-dialog__content">
      <!-- Document preview panel for text files (.md, .txt) -->
      <DocumentPreviewPanel
        v-if="showDocumentPreview"
        :file="file"
        :kb-id="kbId"
        @download="handleDownload"
        @close="handleClose"
      />

      <!-- Office document preview (docx/xlsx/pptx via client-side libraries) -->
      <div v-else-if="previewType === 'office'" class="file-preview-dialog__office-wrapper">
        <div v-if="officeLoading" class="file-preview-dialog__office-loading">
          <el-empty description="正在加载 Office 文档…" :image-size="80" />
        </div>
        <div v-else-if="officeError" class="file-preview-dialog__office-error">
          <el-empty :description="officeError" :image-size="80" />
          <div class="file-preview-dialog__office-actions">
            <el-button type="primary" @click="handleDownload">下载文件</el-button>
          </div>
        </div>
        <!-- PPTX slides rendered by pptx-preview -->
        <div v-else-if="isPptx" ref="officePptxRef" class="file-preview-dialog__office-pptx" />
        <!-- DOCX / XLSX rendered HTML -->
        <div v-else-if="officeHtml" class="file-preview-dialog__office-html" v-html="officeHtml" />
        <!-- Fallback -->
        <div v-else class="file-preview-dialog__office-error">
          <el-empty description="Office 文件预览加载失败" :image-size="80" />
          <div class="file-preview-dialog__office-actions">
            <el-button type="primary" @click="handleDownload">下载文件</el-button>
          </div>
        </div>
      </div>

      <!-- PDF preview: iframe embed + 新窗口/下载 fallback -->
      <div v-else-if="previewType === 'pdf'" class="file-preview-dialog__pdf-wrapper">
        <iframe
          v-if="objectUrl"
          :src="objectUrl"
          class="file-preview-dialog__iframe"
          frameborder="0"
        />
        <!-- PDF 加载失败时的 fallback -->
        <div v-else class="file-preview-dialog__pdf-fallback">
          <el-empty v-if="objectUrlError" description="PDF 预览加载失败" :image-size="120" />
          <el-empty v-else description="正在加载 PDF…" :image-size="120" />
          <div class="file-preview-dialog__pdf-actions">
            <el-button v-if="file.url" @click="openInNewTab">在新窗口打开</el-button>
            <el-button type="primary" @click="handleDownload">下载文件</el-button>
          </div>
        </div>
      </div>

      <!-- Image preview -->
      <div
        v-else-if="previewType === 'image'"
        class="file-preview-dialog__image-wrapper"
      >
        <el-image
          :src="objectUrl || file.url"
          :alt="file.name"
          fit="contain"
          class="file-preview-dialog__image"
        >
          <template #error>
            <div class="file-preview-dialog__image-error">
              <span>图片加载失败</span>
            </div>
          </template>
        </el-image>
      </div>

      <!-- Audio preview -->
      <div v-else-if="previewType === 'audio'" class="file-preview-dialog__media-wrapper">
        <AudioPlayer
          v-if="objectUrl || file.url"
          :src="objectUrl || file.url"
          :title="file.name"
        />
      </div>

      <!-- Video preview -->
      <div v-else-if="previewType === 'video'" class="file-preview-dialog__media-wrapper">
        <VideoPlayer
          v-if="objectUrl || file.url"
          :src="objectUrl || file.url"
          :title="file.name"
        />
      </div>

      <!-- Fallback: download link for unknown types -->
      <div v-else class="file-preview-dialog__fallback">
        <el-empty description="不支持预览此文件类型" :image-size="120" />
        <el-button type="primary" tag="a" :href="file.url" :download="file.name">
          下载文件
        </el-button>
      </div>
    </div>
  </el-dialog>
</template>

<!-- Lazy-resolve AudioPlayer and VideoPlayer once they are created in Tasks 6 & 7 -->
<script lang="ts">
import { defineAsyncComponent } from 'vue'

export default {
  name: 'FilePreviewDialog',
  components: {
    AudioPlayer: defineAsyncComponent(() => import('./AudioPlayer.vue')),
    VideoPlayer: defineAsyncComponent(() =>
      import('./VideoPlayer.vue').catch(() => ({
        name: 'VideoPlayerStub',
        template: '<div>视频播放器加载中…</div>',
      } as any)),
    ),
  },
}
</script>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.file-preview-dialog {
  :deep(.el-dialog__body) {
    padding: 0;
    min-height: 400px;
    max-height: calc(90vh - 120px);
    overflow: auto;
  }

  &__content {
    display: flex;
    flex-direction: column;
    width: 100%;
    min-height: 400px;
  }

  &__iframe {
    width: 100%;
    height: calc(90vh - 160px);
    min-height: 400px;
    border: none;
  }

  &__pdf-wrapper {
    display: flex;
    flex-direction: column;
    width: 100%;
    min-height: 400px;
  }

  &__pdf-fallback {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: $spacing-lg;
    padding: $spacing-xxl;
    min-height: 400px;
  }

  &__pdf-actions {
    display: flex;
    gap: $spacing-base;
  }

  &__image-wrapper {
    display: flex;
    align-items: center;
    justify-content: center;
    padding: $spacing-base;
    min-height: 400px;
    max-height: calc(90vh - 160px);
    overflow: auto;
    background: #1a1a1a;
  }

  &__image {
    max-width: 100%;
    max-height: calc(90vh - 200px);
  }

  &__image-error {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 200px;
    color: $text-secondary;
  }

  &__office-wrapper {
    display: flex;
    flex-direction: column;
    width: 100%;
    min-height: 400px;
  }

  &__office-loading {
    display: flex;
    align-items: center;
    justify-content: center;
    padding: $spacing-xxl;
    min-height: 400px;
  }

  &__office-error {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: $spacing-lg;
    padding: $spacing-xxl;
    min-height: 400px;
  }

  &__office-actions {
    display: flex;
    gap: $spacing-base;
  }

  &__office-pptx {
    width: 100%;
    min-height: 400px;
    overflow: auto;
  }

  &__office-html {
    padding: $spacing-base;
    min-height: 400px;
    overflow: auto;

    // DOCX mammoth 渲染样式
    :deep(img) {
      max-width: 100%;
      height: auto;
    }

    :deep(table) {
      border-collapse: collapse;
      width: 100%;
    }

    :deep(td),
    :deep(th) {
      border: 1px solid $border-lighter;
      padding: 6px 10px;
      vertical-align: top;
    }

    :deep(th) {
      background: $bg-page;
      font-weight: 600;
    }

    // XLSX SheetJS 渲染表格
    :deep(.xls-sheet) {
      overflow: auto;
    }
  }

  &__text-wrapper {
    padding: $spacing-base;
    min-height: 400px;
    max-height: calc(90vh - 160px);
    overflow: auto;
    background: $bg-page;
  }

  &__text {
    font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, Courier, monospace;
    font-size: 13px;
    line-height: 1.6;
    white-space: pre-wrap;
    word-break: break-all;
    margin: 0;
    padding: $spacing-base;
    background: $bg-white;
    border-radius: $radius-base;
    border: 1px solid $border-lighter;
  }

  &__media-wrapper {
    display: flex;
    align-items: center;
    justify-content: center;
    padding: $spacing-xl;
    min-height: 300px;
  }

  &__fallback {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: $spacing-lg;
    padding: $spacing-xxl;
    min-height: 300px;
  }
}
</style>
