<script setup lang="ts">
import type { KnowledgeFile } from '@/types/knowledge'
import { formatFileSize } from '@/types/knowledge'
import { Document, Download, Close, CopyDocument, FullScreen } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const props = defineProps<{
  file: KnowledgeFile | null
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'download', file: KnowledgeFile): void
}>()

// --- View mode ---
type ViewMode = 'markdown' | 'chunks'
const activeView = ref<ViewMode>('markdown')

// --- Document content ---
const markdownContent = ref('')
const chunksContent = ref<{ index: number; content: string }[]>([])
const loading = ref(false)
const error = ref('')

// --- Fullscreen state ---
const isFullscreen = ref(false)

// --- File info ---
const fileInfo = computed(() => {
  if (!props.file) return null
  return {
    name: props.file.name,
    size: formatFileSize(props.file.size),
    extension: props.file.extension,
    category: props.file.category,
  }
})

// --- Character count ---
const charCount = computed(() => {
  if (!markdownContent.value) return 0
  return markdownContent.value.length
})

const formattedCharCount = computed(() => {
  const count = charCount.value
  if (count >= 1000) {
    return (count / 1000).toFixed(1) + 'k 字符'
  }
  return count + ' 字符'
})

// --- Load document content ---
async function loadDocumentContent() {
  if (!props.file) return

  loading.value = true
  error.value = ''
  markdownContent.value = ''
  chunksContent.value = []

  try {
    // Simulate API call to get document content
    // In real app, this would call your backend API
    await new Promise(resolve => setTimeout(resolve, 500))

    // Mock data for demonstration
    if (props.file.category === 'document') {
      markdownContent.value = `# ${props.file.name.replace(/\.[^/.]+$/, '')}

## 概述

以下是关于该文档的详细介绍内容。文档包含了多个章节和详细的技术说明。

### 第一部分：背景介绍

本章节介绍了项目的背景信息和目标。在当前数字化转型的浪潮中，企业需要不断提升自身的信息化水平，以适应快速变化的市场需求。

### 第二部分：核心内容

详细说明了主要的技术方案和实施步骤。包括系统架构设计、模块划分、接口定义等关键内容。

### 第三部分：实施计划

制定了详细的项目实施计划，包括时间表、资源分配、风险评估等。

## 总结

本文档全面介绍了项目的各个方面，为相关人员提供了详细的指导和参考。`

      // Mock chunks data
      chunksContent.value = [
        { index: 0, content: '本章节介绍了项目的背景信息和目标。在当前数字化转型的浪潮中，企业需要不断提升自身的信息化水平，以适应快速变化的市场需求。' },
        { index: 1, content: '详细说明了主要的技术方案和实施步骤。包括系统架构设计、模块划分、接口定义等关键内容。' },
        { index: 2, content: '制定了详细的项目实施计划，包括时间表、资源分配、风险评估等。' },
        { index: 3, content: '本文档全面介绍了项目的各个方面，为相关人员提供了详细的指导和参考。' },
      ]
    } else {
      error.value = '该文件类型暂不支持预览'
    }
  } catch (e) {
    error.value = '加载文档内容失败'
    console.error('Failed to load document content:', e)
  } finally {
    loading.value = false
  }
}

// --- Watch for file changes ---
watch(
  () => props.file,
  () => {
    if (props.file) {
      loadDocumentContent()
    }
  },
  { immediate: true }
)

// --- Actions ---
function handleDownload() {
  if (props.file) {
    emit('download', props.file)
  }
}

function handleClose() {
  emit('close')
}

function handleCopyContent() {
  const content = activeView.value === 'markdown' ? markdownContent.value : chunksContent.value.map(c => c.content).join('\n\n')
  navigator.clipboard.writeText(content).then(() => {
    ElMessage.success('内容已复制到剪贴板')
  }).catch(() => {
    ElMessage.error('复制失败')
  })
}

function toggleFullscreen() {
  isFullscreen.value = !isFullscreen.value
}

// --- File type icon ---
const fileTypeIcon = computed(() => {
  if (!props.file) return '📄'
  const ext = props.file.extension.toLowerCase()
  const iconMap: Record<string, string> = {
    '.pdf': '📕',
    '.docx': '📘',
    '.doc': '📘',
    '.xlsx': '📗',
    '.xls': '📗',
    '.pptx': '📙',
    '.ppt': '📙',
    '.md': '📝',
    '.txt': '📄',
  }
  return iconMap[ext] || '📄'
})
</script>

<template>
  <div
    class="document-preview-panel"
    :class="{ 'document-preview-panel--fullscreen': isFullscreen }"
    v-if="file"
  >
    <!-- Header -->
    <div class="document-preview-panel__header">
      <div class="document-preview-panel__file-info">
        <span class="document-preview-panel__file-icon">{{ fileTypeIcon }}</span>
        <div class="document-preview-panel__file-details">
          <h3 class="document-preview-panel__file-name">{{ fileInfo?.name }}</h3>
          <span class="document-preview-panel__file-size">{{ formattedCharCount }}</span>
        </div>
      </div>

      <div class="document-preview-panel__actions">
        <el-radio-group v-model="activeView" size="small" class="document-preview-panel__view-toggle">
          <el-radio-button value="markdown">Markdown</el-radio-button>
          <el-radio-button value="chunks">Chunks</el-radio-button>
        </el-radio-group>

        <el-button
          :icon="CopyDocument"
          circle
          @click="handleCopyContent"
          title="复制内容"
        />

        <el-button
          :icon="Download"
          circle
          @click="handleDownload"
          title="下载文件"
        />

        <el-button
          :icon="FullScreen"
          circle
          @click="toggleFullscreen"
          :title="isFullscreen ? '退出全屏' : '全屏预览'"
          :class="{ 'is-active': isFullscreen }"
        />

        <el-button
          :icon="Close"
          circle
          @click="handleClose"
          title="关闭预览"
        />
      </div>
    </div>

    <!-- Content area -->
    <div class="document-preview-panel__content">
      <!-- Loading state -->
      <div v-if="loading" class="document-preview-panel__loading">
        <el-skeleton :rows="10" animated />
      </div>

      <!-- Error state -->
      <div v-else-if="error" class="document-preview-panel__error">
        <el-empty :description="error" :image-size="100" />
      </div>

      <!-- Markdown view -->
      <div v-else-if="activeView === 'markdown'" class="document-preview-panel__markdown">
        <div class="document-preview-panel__markdown-content" v-html="renderMarkdown(markdownContent)" />
      </div>

      <!-- Chunks view -->
      <div v-else-if="activeView === 'chunks'" class="document-preview-panel__chunks">
        <div
          v-for="chunk in chunksContent"
          :key="chunk.index"
          class="document-preview-panel__chunk"
        >
          <div class="document-preview-panel__chunk-header">
            <span class="document-preview-panel__chunk-index">Chunk #{{ chunk.index + 1 }}</span>
          </div>
          <div class="document-preview-panel__chunk-content">
            {{ chunk.content }}
          </div>
        </div>

        <el-empty
          v-if="chunksContent.length === 0"
          description="暂无分块数据"
          :image-size="100"
        />
      </div>
    </div>
  </div>
</template>

<script lang="ts">
// Simple markdown renderer (for demonstration)
function renderMarkdown(text: string): string {
  if (!text) return ''

  // Basic markdown rendering
  let html = text
    // Headers (must be ordered from most specific to least)
    .replace(/^#### (.*$)/gim, '<h4>$1</h4>')
    .replace(/^### (.*$)/gim, '<h3>$1</h3>')
    .replace(/^## (.*$)/gim, '<h2>$1</h2>')
    .replace(/^# (.*$)/gim, '<h1>$1</h1>')
    // Bold
    .replace(/\*\*(.*?)\*\*/gim, '<strong>$1</strong>')
    // Italic
    .replace(/\*(.*?)\*/gim, '<em>$1</em>')
    // Inline code
    .replace(/`(.*?)`/gim, '<code>$1</code>')
    // Line breaks (preserve paragraphs)
    .replace(/\n\n/gim, '</p><p>')
    .replace(/\n/gim, '<br>')

  // Wrap in paragraph tags
  html = '<p>' + html + '</p>'

  return html
}

export default {
  name: 'DocumentPreviewPanel',
  methods: {
    renderMarkdown,
  },
}
</script>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.document-preview-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: $bg-white;
  border-radius: $radius-base;
  overflow: hidden;

  // --- Fullscreen mode ---
  &--fullscreen {
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    z-index: 9999;
    border-radius: 0;
    height: 100vh;
    width: 100vw;
  }

  // --- Header ---
  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: $spacing-base $spacing-lg;
    border-bottom: 1px solid $border-light;
    background: $bg-white;
    flex-shrink: 0;
  }

  &__file-info {
    display: flex;
    align-items: center;
    gap: $spacing-md;
    flex: 1;
    min-width: 0;
  }

  &__file-icon {
    font-size: 32px;
    flex-shrink: 0;
  }

  &__file-details {
    display: flex;
    flex-direction: column;
    gap: 2px;
    min-width: 0;
  }

  &__file-name {
    margin: 0;
    font-size: 16px;
    font-weight: 600;
    color: $text-primary;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  &__file-size {
    font-size: 13px;
    color: $text-secondary;
  }

  &__actions {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    flex-shrink: 0;
  }

  &__view-toggle {
    margin-right: $spacing-sm;
  }

  // --- Active state for buttons ---
  .is-active {
    background: $color-primary;
    border-color: $color-primary;
    color: white;
  }

  // --- Content area ---
  &__content {
    flex: 1;
    overflow: auto;
    padding: $spacing-lg;
    background: $bg-page;
  }

  &__loading {
    padding: $spacing-lg;
  }

  &__error {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 100%;
    min-height: 200px;
  }

  // --- Markdown view ---
  &__markdown {
    height: 100%;
  }

  &__markdown-content {
    background: $bg-white;
    border-radius: $radius-base;
    padding: $spacing-xl;
    line-height: 1.8;
    color: $text-primary;
    font-size: 14px;

    :deep(h1) {
      font-size: 24px;
      font-weight: 600;
      margin: 0 0 $spacing-lg 0;
      color: $text-primary;
    }

    :deep(h2) {
      font-size: 20px;
      font-weight: 600;
      margin: $spacing-xl 0 $spacing-base 0;
      color: $text-primary;
      padding-bottom: $spacing-sm;
      border-bottom: 1px solid $border-light;
    }

    :deep(h3) {
      font-size: 16px;
      font-weight: 600;
      margin: $spacing-lg 0 $spacing-sm 0;
      color: $text-primary;
    }

    :deep(h4) {
      font-size: 14px;
      font-weight: 600;
      margin: $spacing-base 0 $spacing-sm 0;
      color: $text-primary;
    }

    :deep(p) {
      margin: 0 0 $spacing-base 0;
    }

    :deep(strong) {
      font-weight: 600;
    }

    :deep(code) {
      background: $bg-hover;
      padding: 2px 6px;
      border-radius: $radius-sm;
      font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, Courier, monospace;
      font-size: 13px;
      color: $color-danger;
    }
  }

  // --- Chunks view ---
  &__chunks {
    display: flex;
    flex-direction: column;
    gap: $spacing-base;
  }

  &__chunk {
    background: $bg-white;
    border-radius: $radius-base;
    overflow: hidden;
    box-shadow: $shadow-sm;
  }

  &__chunk-header {
    display: flex;
    align-items: center;
    padding: $spacing-sm $spacing-md;
    background: $bg-hover;
    border-bottom: 1px solid $border-light;
  }

  &__chunk-index {
    font-size: 12px;
    font-weight: 600;
    color: $color-primary;
    text-transform: uppercase;
  }

  &__chunk-content {
    padding: $spacing-md;
    font-size: 14px;
    line-height: 1.6;
    color: $text-primary;
    white-space: pre-wrap;
  }
}
</style>
