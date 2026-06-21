<script setup lang="ts">
import type { KnowledgeFile } from '@/types/knowledge'
import DocumentPreviewPanel from './DocumentPreviewPanel.vue'

const props = defineProps<{
  visible: boolean
  file: KnowledgeFile | null
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

// ---------- Office 预览：本地 fallback（不再依赖微软在线服务） ----------
// 企业内网 RAG 文件多在鉴权后，view.officeapps.live.com 无法访问且涉及隐私，
// 改为提示下载查看。

// ---------- Document preview panel（仅文本走 DocumentPreviewPanel；
// Office 因无在线预览服务，单独走 fallback） ----------
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
    const token = localStorage.getItem('ais_token')
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

// Load text content when file changes and type is text
watch(
  () => [props.file, previewType.value],
  () => {
    if (props.visible && previewType.value === 'text' && props.file) {
      loadTextContent()
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
      <!-- Document preview panel for text and office files -->
      <DocumentPreviewPanel
        v-if="showDocumentPreview"
        :file="file"
        @download="handleDownload"
        @close="handleClose"
      />

      <!-- PDF preview: iframe embed + 新窗口/下载 fallback -->
      <div v-else-if="previewType === 'pdf'" class="file-preview-dialog__pdf-wrapper">
        <iframe
          v-if="file.url"
          :src="file.url"
          class="file-preview-dialog__iframe"
          frameborder="0"
        />
        <!-- PDF 无 url 或内嵌失败时的 fallback -->
        <div v-else class="file-preview-dialog__pdf-fallback">
          <el-empty description="PDF 预览不可用（文件未就绪）" :image-size="120" />
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
          :src="file.url"
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

      <!-- Audio preview: AudioPlayer (lazy-loaded, available after Task 6) -->
      <div v-else-if="previewType === 'audio'" class="file-preview-dialog__media-wrapper">
        <AudioPlayer
          v-if="file.url"
          :src="file.url"
          :title="file.name"
        />
      </div>

      <!-- Video preview: VideoPlayer (lazy-loaded, available after Task 7) -->
      <div v-else-if="previewType === 'video'" class="file-preview-dialog__media-wrapper">
        <VideoPlayer
          v-if="file.url"
          :src="file.url"
          :title="file.name"
        />
      </div>

      <!-- Office 文档：本地 fallback（不依赖微软在线服务） -->
      <div v-else-if="previewType === 'office'" class="file-preview-dialog__fallback">
        <el-empty description="Office 文档暂不支持在线预览，请下载后查看" :image-size="120" />
        <el-button type="primary" @click="handleDownload">下载文件</el-button>
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
