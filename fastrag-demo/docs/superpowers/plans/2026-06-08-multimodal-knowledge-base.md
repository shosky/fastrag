# 多模态知识库管理模块实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 fastrag-demo 项目实现多模态知识库管理前端，支持视频、音频、Office、PDF、图片等文件的上传、预览、处理状态追踪和检索配置。

**Architecture:** 改造现有知识库详情页为 Tab 结构（文件管理 | 检索测试 | 设置），新增文件管理模块（列表+上传+预览+状态追踪），新增检索配置侧栏。所有组件基于 Vue 3 + Element Plus 实现，Mock 数据内联。

**Tech Stack:** Vue 3, TypeScript, Element Plus, SCSS

---

## 文件结构

```
src/views/knowledge/detail/
├── index.vue                          # 改造：Tab 结构主容器
├── components/
│   ├── FileManager.vue                # 新增：文件管理模块
│   ├── FileUploader.vue               # 新增：文件上传弹窗
│   ├── FilePreviewDialog.vue          # 新增：文件预览弹窗
│   ├── AudioPlayer.vue                # 新增：音频播放器
│   ├── VideoPlayer.vue                # 新增：视频播放器
│   ├── RetrievalConfigPanel.vue       # 新增：检索配置侧栏
│   ├── ProcessStatusBar.vue           # 新增：处理进度组件
│   └── FileTable.vue                  # 新增：文件列表表格
├── debug.vue                          # 保留：检索调试
└── api-doc.vue                        # 保留：API文档

src/types/knowledge.ts                 # 修改：新增文件相关类型
```

---

### Task 1: 扩展类型定义

**Files:**
- Modify: `src/types/knowledge.ts`

- [ ] **Step 1: 添加文件相关类型定义**

在 `src/types/knowledge.ts` 末尾追加：

```typescript
/** 文件类型分类 */
export type FileCategory = 'document' | 'image' | 'audio' | 'video'

/** 文件处理状态 */
export type ProcessStatus = 'pending' | 'processing' | 'completed' | 'failed'

/** 知识库文件 */
export interface KnowledgeFile {
  id: string
  name: string
  category: FileCategory
  extension: string
  size: number
  url: string
  status: ProcessStatus
  progress: number
  stage?: string
  duration?: number
  pages?: number
  createdAt: string
  updatedAt: string
}

/** 检索配置 */
export interface RetrievalConfig {
  mode: 'vector' | 'fulltext' | 'hybrid'
  topK: number
  similarityThreshold: number
  bm25RecallCount: number
  vectorWeight: number
  bm25Weight: number
  bm25SparseDropRate: number
}

/** 处理引擎配置 */
export interface ProcessingConfig {
  ocrEngine: string
  asrEngine: string
  videoStrategy: 'keyframe_asr' | 'asr_only' | 'uniform_sample'
  keyframeInterval?: number
}

/** 文件类型图标映射 */
export const FILE_CATEGORY_ICONS: Record<FileCategory, string> = {
  document: '📄',
  image: '🖼️',
  audio: '🎵',
  video: '🎬',
}

/** 文件扩展名到分类的映射 */
export const EXTENSION_TO_CATEGORY: Record<string, FileCategory> = {
  '.pdf': 'document', '.docx': 'document', '.doc': 'document',
  '.xlsx': 'document', '.xls': 'document', '.pptx': 'document', '.ppt': 'document',
  '.md': 'document', '.txt': 'document', '.csv': 'document',
  '.jpg': 'image', '.jpeg': 'image', '.png': 'image', '.bmp': 'image', '.tiff': 'image', '.gif': 'image',
  '.mp3': 'audio', '.wav': 'audio', '.m4a': 'audio', '.aac': 'audio', '.ogg': 'audio',
  '.mp4': 'video', '.avi': 'video', '.mov': 'video', '.mkv': 'video', '.flv': 'video',
}

/** 获取文件分类 */
export function getFileCategory(filename: string): FileCategory {
  const ext = '.' + filename.split('.').pop()?.toLowerCase()
  return EXTENSION_TO_CATEGORY[ext] || 'document'
}

/** 格式化文件大小 */
export function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

/** 格式化时长 */
export function formatDuration(seconds: number): string {
  const mins = Math.floor(seconds / 60)
  const secs = Math.floor(seconds % 60)
  return `${mins}:${secs.toString().padStart(2, '0')}`
}
```

- [ ] **Step 2: 验证类型定义**

Run: `cd D:/Workspace/java/github/rag/fastrag/fastrag-demo && npx vue-tsc --noEmit 2>&1 | head -20`

Expected: 无错误或仅有已存在的警告

- [ ] **Step 3: Commit**

```bash
git add src/types/knowledge.ts
git commit -m "feat: add multimodal file types and utility functions"
```

---

### Task 2: 创建处理进度组件

**Files:**
- Create: `src/views/knowledge/detail/components/ProcessStatusBar.vue`

- [ ] **Step 1: 创建 ProcessStatusBar 组件**

```vue
<script setup lang="ts">
import { computed } from 'vue'
import type { ProcessStatus } from '@/types/knowledge'

const props = defineProps<{
  status: ProcessStatus
  progress: number
  stage?: string
  category?: string
}>()

const statusConfig = computed(() => {
  const map: Record<ProcessStatus, { color: string; label: string; icon: string }> = {
    pending: { color: '#909399', label: '待处理', icon: 'Clock' },
    processing: { color: '#409eff', label: '处理中', icon: 'Loading' },
    completed: { color: '#67c23a', label: '已完成', icon: 'CircleCheck' },
    failed: { color: '#f56c6c', label: '失败', icon: 'CircleClose' },
  }
  return map[props.status]
})

const stageSteps = computed(() => {
  const stageMap: Record<string, string[]> = {
    document: ['文本提取', '分块', 'Embedding', '存储'],
    image: ['OCR识别', '描述生成', 'Embedding', '存储'],
    audio: ['ASR转写', '文本清理', '分块', 'Embedding', '存储'],
    video: ['关键帧提取', '帧理解', 'ASR转写', '合并', '分块', 'Embedding', '存储'],
  }
  return stageMap[props.category || 'document'] || stageMap.document
})

const currentStageIndex = computed(() => {
  if (!props.stage) return -1
  return stageSteps.value.findIndex(s => s === props.stage)
})
</script>

<template>
  <div class="process-status-bar">
    <template v-if="status === 'processing'">
      <div class="progress-info">
        <el-progress
          :percentage="progress"
          :stroke-width="8"
          :color="statusConfig.color"
          style="flex: 1"
        />
        <span class="stage-text">{{ stage || '处理中' }}</span>
      </div>
      <div class="stage-steps">
        <div
          v-for="(step, index) in stageSteps"
          :key="step"
          class="stage-step"
          :class="{
            completed: index < currentStageIndex,
            active: index === currentStageIndex,
            pending: index > currentStageIndex
          }"
        >
          <el-icon v-if="index < currentStageIndex"><CircleCheck /></el-icon>
          <el-icon v-else-if="index === currentStageIndex" class="rotating"><Loading /></el-icon>
          <el-icon v-else><Clock /></el-icon>
          <span>{{ step }}</span>
        </div>
      </div>
    </template>
    <template v-else>
      <el-tag :type="status === 'completed' ? 'success' : status === 'failed' ? 'danger' : 'info'" size="small">
        <el-icon><component :is="statusConfig.icon" /></el-icon>
        {{ statusConfig.label }}
      </el-tag>
    </template>
  </div>
</template>

<style lang="scss" scoped>
.process-status-bar {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 200px;
}

.progress-info {
  display: flex;
  align-items: center;
  gap: 12px;

  .stage-text {
    font-size: 12px;
    color: #909399;
    white-space: nowrap;
  }
}

.stage-steps {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;

  .stage-step {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 11px;
    padding: 2px 6px;
    border-radius: 4px;
    background: #f5f7fa;
    color: #909399;

    &.completed {
      background: #f0f9eb;
      color: #67c23a;
    }

    &.active {
      background: #ecf5ff;
      color: #409eff;
    }
  }
}

.rotating {
  animation: rotating 1s linear infinite;
}

@keyframes rotating {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
```

- [ ] **Step 2: 验证组件编译**

Run: `cd D:/Workspace/java/github/rag/fastrag/fastrag-demo && npx vite build 2>&1 | tail -5`

Expected: 构建成功

- [ ] **Step 3: Commit**

```bash
git add src/views/knowledge/detail/components/ProcessStatusBar.vue
git commit -m "feat: add ProcessStatusBar component for file processing tracking"
```

---

### Task 3: 创建文件列表表格组件

**Files:**
- Create: `src/views/knowledge/detail/components/FileTable.vue`

- [ ] **Step 1: 创建 FileTable 组件**

```vue
<script setup lang="ts">
import { ref, computed } from 'vue'
import type { KnowledgeFile, FileCategory, ProcessStatus } from '@/types/knowledge'
import { FILE_CATEGORY_ICONS, formatFileSize, formatDuration } from '@/types/knowledge'
import ProcessStatusBar from './ProcessStatusBar.vue'

const props = defineProps<{
  files: KnowledgeFile[]
}>()

const emit = defineEmits<{
  preview: [file: KnowledgeFile]
  download: [file: KnowledgeFile]
  delete: [file: KnowledgeFile]
  retry: [file: KnowledgeFile]
  selectionChange: [files: KnowledgeFile[]]
}>()

const searchQuery = ref('')
const filterCategory = ref<FileCategory | ''>('')
const filterStatus = ref<ProcessStatus | ''>('')
const selectedFiles = ref<KnowledgeFile[]>([])

const categoryOptions = [
  { value: '', label: '全部类型' },
  { value: 'document', label: '📄 文档' },
  { value: 'image', label: '🖼️ 图片' },
  { value: 'audio', label: '🎵 音频' },
  { value: 'video', label: '🎬 视频' },
]

const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'pending', label: '待处理' },
  { value: 'processing', label: '处理中' },
  { value: 'completed', label: '已完成' },
  { value: 'failed', label: '失败' },
]

const filteredFiles = computed(() => {
  return props.files.filter(file => {
    const matchSearch = !searchQuery.value || file.name.toLowerCase().includes(searchQuery.value.toLowerCase())
    const matchCategory = !filterCategory.value || file.category === filterCategory.value
    const matchStatus = !filterStatus.value || file.status === filterStatus.value
    return matchSearch && matchCategory && matchStatus
  })
})

function handleSelectionChange(val: KnowledgeFile[]) {
  selectedFiles.value = val
  emit('selectionChange', val)
}

function getFileExtension(name: string): string {
  return name.split('.').pop()?.toUpperCase() || ''
}
</script>

<template>
  <div class="file-table">
    <!-- 工具栏 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <el-input
          v-model="searchQuery"
          placeholder="搜索文件名"
          clearable
          style="width: 240px"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="filterCategory" placeholder="全部类型" style="width: 120px">
          <el-option
            v-for="opt in categoryOptions"
            :key="opt.value"
            :value="opt.value"
            :label="opt.label"
          />
        </el-select>
        <el-select v-model="filterStatus" placeholder="全部状态" style="width: 120px">
          <el-option
            v-for="opt in statusOptions"
            :key="opt.value"
            :value="opt.value"
            :label="opt.label"
          />
        </el-select>
      </div>
      <div class="toolbar-right">
        <el-button text @click="$emit('refresh')">
          <el-icon><Refresh /></el-icon>
        </el-button>
      </div>
    </div>

    <!-- 表格 -->
    <el-table
      :data="filteredFiles"
      stripe
      style="width: 100%"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="40" />
      <el-table-column label="文件名" min-width="280">
        <template #default="{ row }">
          <div class="file-name-cell">
            <span class="file-icon">{{ FILE_CATEGORY_ICONS[row.category] }}</span>
            <div class="file-info">
              <span class="file-name">{{ row.name }}</span>
              <span class="file-ext">{{ getFileExtension(row.name) }}</span>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <ProcessStatusBar
            :status="row.status"
            :progress="row.progress"
            :stage="row.stage"
            :category="row.category"
          />
        </template>
      </el-table-column>
      <el-table-column label="时长/页数" width="100" align="center">
        <template #default="{ row }">
          <span v-if="row.duration">{{ formatDuration(row.duration) }}</span>
          <span v-else-if="row.pages">{{ row.pages }}页</span>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="大小" width="100" align="right">
        <template #default="{ row }">
          {{ formatFileSize(row.size) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="emit('preview', row)">
            预览
          </el-button>
          <el-button link type="primary" size="small" @click="emit('download', row)">
            下载
          </el-button>
          <el-button
            v-if="row.status === 'failed'"
            link
            type="warning"
            size="small"
            @click="emit('retry', row)"
          >
            重试
          </el-button>
          <el-button link type="danger" size="small" @click="emit('delete', row)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style lang="scss" scoped>
.file-table {
  background: white;
  border-radius: 8px;
  padding: 16px;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;

  .toolbar-left {
    display: flex;
    gap: 12px;
    align-items: center;
  }
}

.file-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;

  .file-icon {
    font-size: 20px;
  }

  .file-info {
    display: flex;
    flex-direction: column;

    .file-name {
      font-size: 13px;
      font-weight: 500;
    }

    .file-ext {
      font-size: 11px;
      color: #909399;
    }
  }
}
</style>
```

- [ ] **Step 2: 验证组件编译**

Run: `cd D:/Workspace/java/github/rag/fastrag/fastrag-demo && npx vite build 2>&1 | tail -5`

Expected: 构建成功

- [ ] **Step 3: Commit**

```bash
git add src/views/knowledge/detail/components/FileTable.vue
git commit -m "feat: add FileTable component with search, filter and selection"
```

---

### Task 4: 创建文件上传弹窗组件

**Files:**
- Create: `src/views/knowledge/detail/components/FileUploader.vue`

- [ ] **Step 1: 创建 FileUploader 组件**

```vue
<script setup lang="ts">
import { ref } from 'vue'
import type { ProcessingConfig } from '@/types/knowledge'

const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  upload: [files: File[], config: ProcessingConfig]
}>()

const activeTab = ref('file')
const autoIndex = ref(false)
const urlInput = ref('')

const processingConfig = ref<ProcessingConfig>({
  ocrEngine: 'deepseek-ocr',
  asrEngine: 'funasr',
  videoStrategy: 'keyframe_asr',
  keyframeInterval: 5,
})

const fileList = ref<File[]>([])
const isDragging = ref(false)

const ocrEngines = [
  { value: 'deepseek-ocr', label: 'DeepSeek OCR' },
  { value: 'paddleocr', label: 'PaddleOCR' },
]

const asrEngines = [
  { value: 'funasr', label: 'FunASR' },
  { value: 'whisper', label: 'Whisper' },
]

const videoStrategies = [
  { value: 'keyframe_asr', label: '关键帧+ASR（推荐）' },
  { value: 'asr_only', label: '仅ASR（音频优先）' },
  { value: 'uniform_sample', label: '均匀采样（画面优先）' },
]

const supportedFormats = {
  document: '.pdf, .docx, .xlsx, .pptx, .md, .txt, .csv',
  image: '.jpg, .png, .bmp, .tiff, .gif',
  audio: '.mp3, .wav, .m4a, .aac, .ogg',
  video: '.mp4, .avi, .mov, .mkv, .flv',
}

const acceptFormats = Object.values(supportedFormats).join(', ')

function handleDragOver(e: DragEvent) {
  e.preventDefault()
  isDragging.value = true
}

function handleDragLeave() {
  isDragging.value = false
}

function handleDrop(e: DragEvent) {
  e.preventDefault()
  isDragging.value = false
  const files = Array.from(e.dataTransfer?.files || [])
  addFiles(files)
}

function handleFileSelect(e: Event) {
  const input = e.target as HTMLInputElement
  const files = Array.from(input.files || [])
  addFiles(files)
  input.value = ''
}

function addFiles(newFiles: File[]) {
  fileList.value.push(...newFiles)
}

function removeFile(index: number) {
  fileList.value.splice(index, 1)
}

function handleCancel() {
  fileList.value = []
  emit('update:visible', false)
}

function handleUpload() {
  if (fileList.value.length === 0) return
  emit('upload', fileList.value, { ...processingConfig.value })
  fileList.value = []
  emit('update:visible', false)
}

function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    @update:model-value="emit('update:visible', $event)"
    title="添加文件"
    width="680px"
    :close-on-click-modal="false"
  >
    <!-- 上传方式 Tab -->
    <el-tabs v-model="activeTab" class="upload-tabs">
      <el-tab-pane label="上传文件" name="file" />
      <el-tab-pane label="上传文件夹" name="folder" />
      <el-tab-pane label="解析 URL" name="url" />
      <el-tab-pane label="工作区" name="workspace" />
    </el-tabs>

    <!-- 自动入库开关 -->
    <div class="auto-index-row">
      <el-checkbox v-model="autoIndex">上传后自动入库</el-checkbox>
    </div>

    <!-- 处理引擎配置 -->
    <div class="engine-config">
      <el-row :gutter="16">
        <el-col :span="12">
          <div class="config-item">
            <label>OCR 引擎 <span class="hint">（仅 PDF/图片）</span></label>
            <el-select v-model="processingConfig.ocrEngine" style="width: 100%">
              <el-option
                v-for="eng in ocrEngines"
                :key="eng.value"
                :value="eng.value"
                :label="eng.label"
              />
            </el-select>
          </div>
        </el-col>
        <el-col :span="12">
          <div class="config-item">
            <label>ASR 引擎 <span class="hint">（音频/视频）</span></label>
            <el-select v-model="processingConfig.asrEngine" style="width: 100%">
              <el-option
                v-for="eng in asrEngines"
                :key="eng.value"
                :value="eng.value"
                :label="eng.label"
              />
            </el-select>
          </div>
        </el-col>
      </el-row>
      <el-row :gutter="16" style="margin-top: 12px">
        <el-col :span="12">
          <div class="config-item">
            <label>视频策略</label>
            <el-select v-model="processingConfig.videoStrategy" style="width: 100%">
              <el-option
                v-for="s in videoStrategies"
                :key="s.value"
                :value="s.value"
                :label="s.label"
              />
            </el-select>
          </div>
        </el-col>
        <el-col :span="12" v-if="processingConfig.videoStrategy === 'keyframe_asr'">
          <div class="config-item">
            <label>关键帧间隔（秒）</label>
            <el-input-number
              v-model="processingConfig.keyframeInterval"
              :min="1"
              :max="30"
              style="width: 100%"
            />
          </div>
        </el-col>
      </el-row>
    </div>

    <!-- 文件上传区（文件 Tab） -->
    <div
      v-if="activeTab === 'file'"
      class="upload-zone"
      :class="{ dragging: isDragging }"
      @dragover="handleDragOver"
      @dragleave="handleDragLeave"
      @drop="handleDrop"
      @click="($refs.fileInput as HTMLInputElement).click()"
    >
      <input
        ref="fileInput"
        type="file"
        multiple
        :accept="acceptFormats"
        style="display: none"
        @change="handleFileSelect"
      />
      <el-icon :size="48" color="#c0c4cc"><Upload /></el-icon>
      <p class="upload-text">点击或将文件拖拽到此处</p>
      <p class="format-hint">
        支持格式: {{ Object.values(supportedFormats).join(' ') }}
      </p>
    </div>

    <!-- URL 输入 -->
    <div v-if="activeTab === 'url'" class="url-input-area">
      <el-input
        v-model="urlInput"
        placeholder="请输入网页 URL，例如 https://example.com/article"
      >
        <template #prepend>URL</template>
      </el-input>
    </div>

    <!-- 文件夹上传 -->
    <div
      v-if="activeTab === 'folder'"
      class="upload-zone"
      :class="{ dragging: isDragging }"
      @dragover="handleDragOver"
      @dragleave="handleDragLeave"
      @drop="handleDrop"
      @click="($refs.folderInput as HTMLInputElement).click()"
    >
      <input
        ref="folderInput"
        type="file"
        webkitdirectory
        multiple
        style="display: none"
        @change="handleFileSelect"
      />
      <el-icon :size="48" color="#c0c4cc"><FolderOpened /></el-icon>
      <p class="upload-text">点击选择文件夹</p>
    </div>

    <!-- 已选文件列表 -->
    <div v-if="fileList.length > 0" class="file-list">
      <div class="file-list-header">
        <span>已选择 {{ fileList.length }} 个文件</span>
        <el-button link type="danger" size="small" @click="fileList = []">清空</el-button>
      </div>
      <el-scrollbar max-height="150">
        <div v-for="(file, index) in fileList" :key="index" class="file-item">
          <span class="file-name">{{ file.name }}</span>
          <span class="file-size">{{ formatFileSize(file.size) }}</span>
          <el-button link type="danger" size="small" @click="removeFile(index)">
            <el-icon><Delete /></el-icon>
          </el-button>
        </div>
      </el-scrollbar>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="handleCancel">取消</el-button>
        <el-button type="primary" :disabled="fileList.length === 0" @click="handleUpload">
          添加到知识库
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.upload-tabs {
  margin-bottom: 16px;
}

.auto-index-row {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 16px;
}

.engine-config {
  background: #f5f7fa;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 16px;

  .config-item {
    display: flex;
    flex-direction: column;
    gap: 6px;

    label {
      font-size: 13px;
      font-weight: 500;

      .hint {
        color: #909399;
        font-weight: normal;
      }
    }
  }
}

.upload-zone {
  border: 2px dashed #dcdfe6;
  border-radius: 8px;
  padding: 40px;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s;

  &:hover, &.dragging {
    border-color: #409eff;
    background: #ecf5ff;
  }

  .upload-text {
    margin: 12px 0 4px;
    font-size: 14px;
    color: #606266;
  }

  .format-hint {
    font-size: 12px;
    color: #909399;
  }
}

.url-input-area {
  margin-bottom: 16px;
}

.file-list {
  margin-top: 16px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 12px;

  .file-list-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 8px;
    font-size: 13px;
    color: #606266;
  }

  .file-item {
    display: flex;
    align-items: center;
    padding: 6px 0;
    border-bottom: 1px solid #f5f7fa;

    &:last-child {
      border-bottom: none;
    }

    .file-name {
      flex: 1;
      font-size: 13px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .file-size {
      margin: 0 12px;
      font-size: 12px;
      color: #909399;
    }
  }
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
```

- [ ] **Step 2: 验证组件编译**

Run: `cd D:/Workspace/java/github/rag/fastrag/fastrag-demo && npx vite build 2>&1 | tail -5`

Expected: 构建成功

- [ ] **Step 3: Commit**

```bash
git add src/views/knowledge/detail/components/FileUploader.vue
git commit -m "feat: add FileUploader component with multi-mode upload and engine config"
```

---

### Task 5: 创建文件预览弹窗组件

**Files:**
- Create: `src/views/knowledge/detail/components/FilePreviewDialog.vue`

- [ ] **Step 1: 创建 FilePreviewDialog 组件**

```vue
<script setup lang="ts">
import { computed } from 'vue'
import type { KnowledgeFile } from '@/types/knowledge'
import AudioPlayer from './AudioPlayer.vue'
import VideoPlayer from './VideoPlayer.vue'

const props = defineProps<{
  visible: boolean
  file: KnowledgeFile | null
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

const previewType = computed(() => {
  if (!props.file) return 'unknown'
  const ext = props.file.extension.toLowerCase()
  if (ext === '.pdf') return 'pdf'
  if (['.jpg', '.jpeg', '.png', '.bmp', '.tiff', '.gif'].includes(ext)) return 'image'
  if (['.docx', '.doc', '.xlsx', '.xls', '.pptx', '.ppt'].includes(ext)) return 'office'
  if (['.md', '.txt'].includes(ext)) return 'text'
  if (['.mp3', '.wav', '.m4a', '.aac', '.ogg'].includes(ext)) return 'audio'
  if (['.mp4', '.avi', '.mov', '.mkv', '.flv'].includes(ext)) return 'video'
  if (ext === '.html') return 'html'
  return 'unknown'
})

const officeViewerUrl = computed(() => {
  if (!props.file?.url) return ''
  return `https://view.officeapps.live.com/op/embed.aspx?src=${encodeURIComponent(props.file.url)}`
})
</script>

<template>
  <el-dialog
    :model-value="visible"
    @update:model-value="emit('update:visible', $event)"
    :title="file?.name || '文件预览'"
    width="80%"
    top="5vh"
    destroy-on-close
  >
    <div class="preview-container">
      <!-- PDF 预览 -->
      <iframe
        v-if="previewType === 'pdf'"
        :src="file?.url"
        class="preview-frame"
        title="PDF预览"
      />

      <!-- 图片预览 -->
      <div v-else-if="previewType === 'image'" class="image-preview">
        <img :src="file?.url" :alt="file?.name" />
      </div>

      <!-- Office 预览 -->
      <iframe
        v-else-if="previewType === 'office'"
        :src="officeViewerUrl"
        class="preview-frame"
        title="Office预览"
      />

      <!-- 纯文本预览 -->
      <div v-else-if="previewType === 'text'" class="text-preview">
        <pre>{{ file?.url }}</pre>
      </div>

      <!-- 音频预览 -->
      <AudioPlayer
        v-else-if="previewType === 'audio'"
        :src="file?.url || ''"
        :title="file?.name || ''"
      />

      <!-- 视频预览 -->
      <VideoPlayer
        v-else-if="previewType === 'video'"
        :src="file?.url || ''"
        :title="file?.name || ''"
      />

      <!-- 不支持的格式 -->
      <div v-else class="unknown-preview">
        <el-icon :size="64" color="#c0c4cc"><Document /></el-icon>
        <p>暂不支持此格式文件的在线预览</p>
        <a :href="file?.url" target="_blank" class="download-link">下载文件</a>
      </div>
    </div>
  </el-dialog>
</template>

<style lang="scss" scoped>
.preview-container {
  min-height: 400px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.preview-frame {
  width: 100%;
  height: 60vh;
  border: none;
  border-radius: 8px;
}

.image-preview {
  display: flex;
  align-items: center;
  justify-content: center;
  max-height: 60vh;
  overflow: auto;

  img {
    max-width: 100%;
    max-height: 60vh;
    object-fit: contain;
    border-radius: 8px;
  }
}

.text-preview {
  width: 100%;
  max-height: 60vh;
  overflow: auto;
  background: #f5f7fa;
  border-radius: 8px;
  padding: 16px;

  pre {
    margin: 0;
    font-family: 'Monaco', 'Menlo', monospace;
    font-size: 13px;
    line-height: 1.6;
    white-space: pre-wrap;
    word-break: break-all;
  }
}

.unknown-preview {
  text-align: center;
  padding: 40px;

  p {
    margin: 16px 0;
    color: #909399;
  }

  .download-link {
    color: #409eff;
    text-decoration: none;

    &:hover {
      text-decoration: underline;
    }
  }
}
</style>
```

- [ ] **Step 2: 验证组件编译**

Run: `cd D:/Workspace/java/github/rag/fastrag/fastrag-demo && npx vite build 2>&1 | tail -5`

Expected: 构建成功

- [ ] **Step 3: Commit**

```bash
git add src/views/knowledge/detail/components/FilePreviewDialog.vue
git commit -m "feat: add FilePreviewDialog component with multi-format preview"
```

---

### Task 6: 创建音频播放器组件

**Files:**
- Create: `src/views/knowledge/detail/components/AudioPlayer.vue`

- [ ] **Step 1: 创建 AudioPlayer 组件**

```vue
<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'

const props = defineProps<{
  src: string
  title: string
}>()

const audioRef = ref<HTMLAudioElement>()
const isPlaying = ref(false)
const currentTime = ref(0)
const duration = ref(0)
const volume = ref(0.8)
const isMuted = ref(false)

// Mock ASR 转写结果
const transcripts = ref([
  { time: 0, text: '大家好，欢迎参加今天的会议。' },
  { time: 15, text: '首先我们来看一下上季度的数据。' },
  { time: 32, text: '接下来讨论新产品的开发计划。' },
  { time: 48, text: '最后是关于团队建设的安排。' },
])

function togglePlay() {
  if (!audioRef.value) return
  if (isPlaying.value) {
    audioRef.value.pause()
  } else {
    audioRef.value.play()
  }
  isPlaying.value = !isPlaying.value
}

function handleTimeUpdate() {
  if (!audioRef.value) return
  currentTime.value = audioRef.value.currentTime
}

function handleLoadedMetadata() {
  if (!audioRef.value) return
  duration.value = audioRef.value.duration
}

function handleSeek(e: Event) {
  const input = e.target as HTMLInputElement
  const time = parseFloat(input.value)
  if (audioRef.value) {
    audioRef.value.currentTime = time
  }
  currentTime.value = time
}

function toggleMute() {
  if (!audioRef.value) return
  audioRef.value.muted = !audioRef.value.muted
  isMuted.value = !isMuted.value
}

function handleVolumeChange(e: Event) {
  const input = e.target as HTMLInputElement
  const vol = parseFloat(input.value)
  if (audioRef.value) {
    audioRef.value.volume = vol
  }
  volume.value = vol
}

function formatTime(seconds: number): string {
  const mins = Math.floor(seconds / 60)
  const secs = Math.floor(seconds % 60)
  return `${mins}:${secs.toString().padStart(2, '0')}`
}

onMounted(() => {
  if (audioRef.value) {
    audioRef.value.volume = volume.value
  }
})
</script>

<template>
  <div class="audio-player">
    <audio
      ref="audioRef"
      :src="src"
      @timeupdate="handleTimeUpdate"
      @loadedmetadata="handleLoadedMetadata"
      @ended="isPlaying = false"
    />

    <!-- 播放器控制栏 -->
    <div class="player-controls">
      <el-button :type="isPlaying ? 'warning' : 'primary'" circle @click="togglePlay">
        <el-icon><component :is="isPlaying ? 'VideoPause' : 'VideoPlay'" /></el-icon>
      </el-button>

      <div class="time-display">
        {{ formatTime(currentTime) }} / {{ formatTime(duration) }}
      </div>

      <el-slider
        :model-value="currentTime"
        :max="duration || 100"
        :show-tooltip="false"
        style="flex: 1"
        @input="handleSeek"
      />

      <div class="volume-control">
        <el-button link @click="toggleMute">
          <el-icon>
            <component :is="isMuted || volume === 0 ? 'Mute' : 'Microphone'" />
          </el-icon>
        </el-button>
        <el-slider
          :model-value="isMuted ? 0 : volume"
          :max="1"
          :step="0.1"
          :show-tooltip="false"
          style="width: 80px"
          @input="handleVolumeChange"
        />
      </div>
    </div>

    <!-- ASR 转写结果 -->
    <div class="transcript-section">
      <div class="section-title">ASR 转写结果</div>
      <div class="transcript-list">
        <div
          v-for="(item, index) in transcripts"
          :key="index"
          class="transcript-item"
          :class="{ active: currentTime >= item.time && (index === transcripts.length - 1 || currentTime < transcripts[index + 1].time) }"
        >
          <span class="time-tag">{{ formatTime(item.time) }}</span>
          <span class="text">{{ item.text }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.audio-player {
  display: flex;
  flex-direction: column;
  gap: 24px;
  padding: 20px;
  background: #f5f7fa;
  border-radius: 12px;
}

.player-controls {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);

  .time-display {
    font-size: 13px;
    color: #606266;
    font-family: monospace;
    min-width: 90px;
  }

  .volume-control {
    display: flex;
    align-items: center;
    gap: 4px;
  }
}

.transcript-section {
  background: white;
  border-radius: 8px;
  padding: 16px;

  .section-title {
    font-size: 14px;
    font-weight: 600;
    margin-bottom: 12px;
    color: #303133;
  }
}

.transcript-list {
  max-height: 300px;
  overflow-y: auto;
}

.transcript-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 8px 12px;
  border-radius: 6px;
  transition: background 0.2s;

  &.active {
    background: #ecf5ff;

    .time-tag {
      background: #409eff;
      color: white;
    }
  }

  .time-tag {
    font-size: 12px;
    padding: 2px 8px;
    border-radius: 4px;
    background: #f0f2f5;
    color: #606266;
    font-family: monospace;
    white-space: nowrap;
  }

  .text {
    font-size: 14px;
    line-height: 1.6;
    color: #303133;
  }
}
</style>
```

- [ ] **Step 2: 验证组件编译**

Run: `cd D:/Workspace/java/github/rag/fastrag/fastrag-demo && npx vite build 2>&1 | tail -5`

Expected: 构建成功

- [ ] **Step 3: Commit**

```bash
git add src/views/knowledge/detail/components/AudioPlayer.vue
git commit -m "feat: add AudioPlayer component with playback controls and ASR transcript"
```

---

### Task 7: 创建视频播放器组件

**Files:**
- Create: `src/views/knowledge/detail/components/VideoPlayer.vue`

- [ ] **Step 1: 创建 VideoPlayer 组件**

```vue
<script setup lang="ts">
import { ref, computed } from 'vue'

const props = defineProps<{
  src: string
  title: string
}>()

const videoRef = ref<HTMLVideoElement>()
const isPlaying = ref(false)
const currentTime = ref(0)
const duration = ref(0)
const volume = ref(0.8)
const isMuted = ref(false)
const isFullscreen = ref(false)
const activeTab = ref('keyframes')

// Mock 关键帧数据
const keyframes = ref([
  { time: 5, thumbnail: '', description: '产品架构介绍' },
  { time: 30, thumbnail: '', description: '数据库设计' },
  { time: 75, thumbnail: '', description: 'API接口说明' },
  { time: 120, thumbnail: '', description: '部署流程' },
])

// Mock ASR 转写
const transcripts = ref([
  { time: 0, text: '今天我们来讲一下系统架构。' },
  { time: 15, text: '首先是前端部分，使用 Vue 3 框架。' },
  { time: 32, text: '后端采用 Python FastAPI。' },
  { time: 48, text: '数据库使用 PostgreSQL。' },
  { time: 65, text: '部署方面使用 Docker 容器化。' },
])

function togglePlay() {
  if (!videoRef.value) return
  if (isPlaying.value) {
    videoRef.value.pause()
  } else {
    videoRef.value.play()
  }
  isPlaying.value = !isPlaying.value
}

function handleTimeUpdate() {
  if (!videoRef.value) return
  currentTime.value = videoRef.value.currentTime
}

function handleLoadedMetadata() {
  if (!videoRef.value) return
  duration.value = videoRef.value.duration
}

function handleSeek(e: Event) {
  const input = e.target as HTMLInputElement
  const time = parseFloat(input.value)
  if (videoRef.value) {
    videoRef.value.currentTime = time
  }
  currentTime.value = time
}

function toggleMute() {
  if (!videoRef.value) return
  videoRef.value.muted = !videoRef.value.muted
  isMuted.value = !isMuted.value
}

function handleVolumeChange(e: Event) {
  const input = e.target as HTMLInputElement
  const vol = parseFloat(input.value)
  if (videoRef.value) {
    videoRef.value.volume = vol
  }
  volume.value = vol
}

function toggleFullscreen() {
  isFullscreen.value = !isFullscreen.value
}

function seekToTime(time: number) {
  if (videoRef.value) {
    videoRef.value.currentTime = time
  }
  currentTime.value = time
}

function formatTime(seconds: number): string {
  const mins = Math.floor(seconds / 60)
  const secs = Math.floor(seconds % 60)
  return `${mins}:${secs.toString().padStart(2, '0')}`
}
</script>

<template>
  <div class="video-player">
    <video
      ref="videoRef"
      :src="src"
      class="video-element"
      @timeupdate="handleTimeUpdate"
      @loadedmetadata="handleLoadedMetadata"
      @ended="isPlaying = false"
    />

    <!-- 播放器控制栏 -->
    <div class="player-controls">
      <el-button :type="isPlaying ? 'warning' : 'primary'" circle @click="togglePlay">
        <el-icon><component :is="isPlaying ? 'VideoPause' : 'VideoPlay'" /></el-icon>
      </el-button>

      <div class="time-display">
        {{ formatTime(currentTime) }} / {{ formatTime(duration) }}
      </div>

      <el-slider
        :model-value="currentTime"
        :max="duration || 100"
        :show-tooltip="false"
        style="flex: 1"
        @input="handleSeek"
      />

      <div class="volume-control">
        <el-button link @click="toggleMute">
          <el-icon>
            <component :is="isMuted || volume === 0 ? 'Mute' : 'Microphone'" />
          </el-icon>
        </el-button>
        <el-slider
          :model-value="isMuted ? 0 : volume"
          :max="1"
          :step="0.1"
          :show-tooltip="false"
          style="width: 80px"
          @input="handleVolumeChange"
        />
      </div>

      <el-button link @click="toggleFullscreen">
        <el-icon><FullScreen /></el-icon>
      </el-button>
    </div>

    <!-- 内容展示 Tab -->
    <el-tabs v-model="activeTab" class="content-tabs">
      <el-tab-pane label="关键帧" name="keyframes">
        <div class="keyframe-timeline">
          <div
            v-for="(frame, index) in keyframes"
            :key="index"
            class="keyframe-item"
            :class="{ active: currentTime >= frame.time && (index === keyframes.length - 1 || currentTime < keyframes[index + 1].time) }"
            @click="seekToTime(frame.time)"
          >
            <div class="thumbnail-placeholder">
              <el-icon :size="24"><Picture /></el-icon>
              <span class="time-badge">{{ formatTime(frame.time) }}</span>
            </div>
            <span class="description">{{ frame.description }}</span>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="ASR 转写" name="transcript">
        <div class="transcript-list">
          <div
            v-for="(item, index) in transcripts"
            :key="index"
            class="transcript-item"
            :class="{ active: currentTime >= item.time && (index === transcripts.length - 1 || currentTime < transcripts[index + 1].time) }"
            @click="seekToTime(item.time)"
          >
            <span class="time-tag">{{ formatTime(item.time) }}</span>
            <span class="text">{{ item.text }}</span>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="合并视图" name="combined">
        <div class="combined-view">
          <div
            v-for="(frame, index) in keyframes"
            :key="index"
            class="combined-item"
          >
            <div class="frame-section">
              <div class="thumbnail-placeholder small">
                <el-icon :size="16"><Picture /></el-icon>
              </div>
              <div class="frame-info">
                <span class="time-tag">{{ formatTime(frame.time) }}</span>
                <span class="description">{{ frame.description }}</span>
              </div>
            </div>
            <div class="transcript-section">
              <span class="text">
                {{ transcripts.find(t => t.time >= frame.time)?.text || '...' }}
              </span>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style lang="scss" scoped>
.video-player {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.video-element {
  width: 100%;
  max-height: 50vh;
  background: #000;
  border-radius: 8px;
}

.player-controls {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 16px;
  background: #1a1a1a;
  border-radius: 8px;

  :deep(.el-button) {
    color: white;
  }

  .time-display {
    font-size: 13px;
    color: #ccc;
    font-family: monospace;
    min-width: 90px;
  }

  .volume-control {
    display: flex;
    align-items: center;
    gap: 4px;
  }
}

.content-tabs {
  background: white;
  border-radius: 8px;
  padding: 16px;
}

.keyframe-timeline {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 12px;
}

.keyframe-item {
  cursor: pointer;
  border-radius: 8px;
  overflow: hidden;
  border: 2px solid transparent;
  transition: all 0.2s;

  &:hover {
    border-color: #409eff;
  }

  &.active {
    border-color: #409eff;
    box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2);
  }

  .thumbnail-placeholder {
    aspect-ratio: 16/9;
    background: #f5f7fa;
    display: flex;
    align-items: center;
    justify-content: center;
    position: relative;
    color: #c0c4cc;

    .time-badge {
      position: absolute;
      bottom: 4px;
      right: 4px;
      font-size: 11px;
      padding: 2px 6px;
      background: rgba(0, 0, 0, 0.6);
      color: white;
      border-radius: 4px;
    }

    &.small {
      width: 80px;
      height: 45px;
    }
  }

  .description {
    display: block;
    padding: 8px;
    font-size: 12px;
    text-align: center;
    color: #606266;
  }
}

.transcript-list {
  max-height: 300px;
  overflow-y: auto;
}

.transcript-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 8px 12px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.2s;

  &:hover {
    background: #f5f7fa;
  }

  &.active {
    background: #ecf5ff;

    .time-tag {
      background: #409eff;
      color: white;
    }
  }

  .time-tag {
    font-size: 12px;
    padding: 2px 8px;
    border-radius: 4px;
    background: #f0f2f5;
    color: #606266;
    font-family: monospace;
    white-space: nowrap;
  }

  .text {
    font-size: 14px;
    line-height: 1.6;
    color: #303133;
  }
}

.combined-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.combined-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 8px;

  .frame-section {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .frame-info {
    display: flex;
    flex-direction: column;
    gap: 4px;

    .time-tag {
      font-size: 12px;
      color: #409eff;
      font-family: monospace;
    }

    .description {
      font-size: 13px;
      color: #303133;
    }
  }

  .transcript-section {
    .text {
      font-size: 13px;
      color: #606266;
      line-height: 1.5;
    }
  }
}
</style>
```

- [ ] **Step 2: 验证组件编译**

Run: `cd D:/Workspace/java/github/rag/fastrag/fastrag-demo && npx vite build 2>&1 | tail -5`

Expected: 构建成功

- [ ] **Step 3: Commit**

```bash
git add src/views/knowledge/detail/components/VideoPlayer.vue
git commit -m "feat: add VideoPlayer component with keyframes timeline and ASR transcript"
```

---

### Task 8: 创建检索配置面板组件

**Files:**
- Create: `src/views/knowledge/detail/components/RetrievalConfigPanel.vue`

- [ ] **Step 1: 创建 RetrievalConfigPanel 组件**

```vue
<script setup lang="ts">
import { ref } from 'vue'
import type { RetrievalConfig } from '@/types/knowledge'

const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  save: [config: RetrievalConfig]
}>()

const config = ref<RetrievalConfig>({
  mode: 'vector',
  topK: 10,
  similarityThreshold: 0.0,
  bm25RecallCount: 50,
  vectorWeight: 0.7,
  bm25Weight: 0.3,
  bm25SparseDropRate: 0.0,
})

const modeOptions = [
  { value: 'vector', label: '向量检索' },
  { value: 'fulltext', label: '全文检索' },
  { value: 'hybrid', label: '混合检索' },
]

function handleSave() {
  emit('save', { ...config.value })
}
</script>

<template>
  <transition name="slide">
    <div v-if="visible" class="retrieval-config-panel">
      <div class="panel-header">
        <h3>检索配置</h3>
        <el-button text @click="emit('update:visible', false)">
          <el-icon><Close /></el-icon>
        </el-button>
      </div>

      <div class="panel-content">
        <p class="hint">调整当前知识库的检索参数。</p>

        <!-- 检索模式 -->
        <div class="config-section">
          <label class="config-label">检索模式</label>
          <el-select v-model="config.mode" style="width: 100%">
            <el-option
              v-for="opt in modeOptions"
              :key="opt.value"
              :value="opt.value"
              :label="opt.label"
            />
          </el-select>
          <span class="config-desc">选择检索模式</span>
        </div>

        <!-- 最终返回 Chunk 数 -->
        <div class="config-section">
          <label class="config-label">最终返回 Chunk 数</label>
          <el-input-number
            v-model="config.topK"
            :min="1"
            :max="100"
            style="width: 100%"
          />
          <span class="config-desc">重排序后返回给前端的文档数量</span>
        </div>

        <!-- 相似度阈值 -->
        <div class="config-section">
          <label class="config-label">相似度阈值 (0-1)</label>
          <el-input-number
            v-model="config.similarityThreshold"
            :min="0"
            :max="1"
            :step="0.1"
            :precision="1"
            style="width: 100%"
          />
          <span class="config-desc">过滤相似度低于此值的结果</span>
        </div>

        <!-- BM25 召回数量 -->
        <div class="config-section">
          <label class="config-label">BM25 召回数量</label>
          <el-input-number
            v-model="config.bm25RecallCount"
            :min="1"
            :max="200"
            style="width: 100%"
          />
          <span class="config-desc">BM25 全文检索和混合检索中的 BM25 候选数量</span>
        </div>

        <!-- 混合模式专属配置 -->
        <template v-if="config.mode === 'hybrid'">
          <div class="config-section">
            <label class="config-label">向量检索权重</label>
            <el-input-number
              v-model="config.vectorWeight"
              :min="0"
              :max="1"
              :step="0.1"
              :precision="1"
              style="width: 100%"
            />
            <span class="config-desc">混合检索中向量召回结果的融合权重</span>
          </div>

          <div class="config-section">
            <label class="config-label">BM25 权重</label>
            <el-input-number
              v-model="config.bm25Weight"
              :min="0"
              :max="1"
              :step="0.1"
              :precision="1"
              style="width: 100%"
            />
            <span class="config-desc">混合检索中 BM25 召回结果的融合权重</span>
          </div>

          <div class="config-section">
            <label class="config-label">BM25 稀疏项丢弃比例</label>
            <el-input-number
              v-model="config.bm25SparseDropRate"
              :min="0"
              :max="1"
              :step="0.1"
              :precision="1"
              style="width: 100%"
            />
            <span class="config-desc">过滤低频稀疏项的比例</span>
          </div>
        </template>
      </div>

      <div class="panel-footer">
        <el-button type="primary" @click="handleSave">
          <el-icon><Check /></el-icon> 保存
        </el-button>
      </div>
    </div>
  </transition>
</template>

<style lang="scss" scoped>
.retrieval-config-panel {
  position: fixed;
  right: 0;
  top: 60px;
  bottom: 0;
  width: 360px;
  background: white;
  box-shadow: -4px 0 12px rgba(0, 0, 0, 0.1);
  z-index: 1000;
  display: flex;
  flex-direction: column;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid #ebeef5;

  h3 {
    margin: 0;
    font-size: 16px;
    font-weight: 600;
  }
}

.panel-content {
  flex: 1;
  overflow-y: auto;
  padding: 20px;

  .hint {
    font-size: 13px;
    color: #909399;
    margin-bottom: 20px;
  }
}

.config-section {
  margin-bottom: 20px;

  .config-label {
    display: block;
    font-size: 14px;
    font-weight: 500;
    margin-bottom: 8px;
    color: #303133;
  }

  .config-desc {
    display: block;
    font-size: 12px;
    color: #909399;
    margin-top: 6px;
  }
}

.panel-footer {
  padding: 16px 20px;
  border-top: 1px solid #ebeef5;
  display: flex;
  justify-content: center;
}

.slide-enter-active,
.slide-leave-active {
  transition: transform 0.3s ease;
}

.slide-enter-from,
.slide-leave-to {
  transform: translateX(100%);
}
</style>
```

- [ ] **Step 2: 验证组件编译**

Run: `cd D:/Workspace/java/github/rag/fastrag/fastrag-demo && npx vite build 2>&1 | tail -5`

Expected: 构建成功

- [ ] **Step 3: Commit**

```bash
git add src/views/knowledge/detail/components/RetrievalConfigPanel.vue
git commit -m "feat: add RetrievalConfigPanel component with search mode and BM25 config"
```

---

### Task 9: 创建文件管理模块组件

**Files:**
- Create: `src/views/knowledge/detail/components/FileManager.vue`

- [ ] **Step 1: 创建 FileManager 组件**

```vue
<script setup lang="ts">
import { ref, computed } from 'vue'
import type { KnowledgeFile, FileCategory, ProcessStatus } from '@/types/knowledge'
import { FILE_CATEGORY_ICONS, formatFileSize } from '@/types/knowledge'
import FileTable from './FileTable.vue'
import FileUploader from './FileUploader.vue'
import FilePreviewDialog from './FilePreviewDialog.vue'

const showUploader = ref(false)
const showPreview = ref(false)
const previewFile = ref<KnowledgeFile | null>(null)

// Mock 文件数据
const files = ref<KnowledgeFile[]>([
  {
    id: '1', name: 'AIS 平台用户手册.pdf', category: 'document', extension: '.pdf',
    size: 2150000, url: '', status: 'completed', progress: 100, pages: 12,
    createdAt: '2026-06-04', updatedAt: '2026-06-04',
  },
  {
    id: '2', name: '产品演示视频.mp4', category: 'video', extension: '.mp4',
    size: 89000000, url: '', status: 'processing', progress: 45, stage: '帧理解', duration: 765,
    createdAt: '2026-06-04', updatedAt: '2026-06-04',
  },
  {
    id: '3', name: '会议录音.mp3', category: 'audio', extension: '.mp3',
    size: 5300000, url: '', status: 'completed', progress: 100, duration: 332,
    createdAt: '2026-06-03', updatedAt: '2026-06-03',
  },
  {
    id: '4', name: '架构图.png', category: 'image', extension: '.png',
    size: 1200000, url: '', status: 'completed', progress: 100,
    createdAt: '2026-06-03', updatedAt: '2026-06-03',
  },
  {
    id: '5', name: '需求文档.docx', category: 'document', extension: '.docx',
    size: 850000, url: '', status: 'failed', progress: 30, stage: '分块',
    createdAt: '2026-06-02', updatedAt: '2026-06-02',
  },
])

const totalFiles = computed(() => files.value.length)
const processedFiles = computed(() => files.value.filter(f => f.status === 'completed').length)
const processRate = computed(() => {
  if (totalFiles.value === 0) return 0
  return Math.round((processedFiles.value / totalFiles.value) * 100)
})

function handlePreview(file: KnowledgeFile) {
  previewFile.value = file
  showPreview.value = true
}

function handleDownload(file: KnowledgeFile) {
  console.log('下载文件:', file.name)
}

function handleDelete(file: KnowledgeFile) {
  ElMessageBox.confirm(`确定要删除文件 "${file.name}" 吗？`, '删除确认', {
    type: 'warning',
  }).then(() => {
    files.value = files.value.filter(f => f.id !== file.id)
    ElMessage.success('删除成功')
  }).catch(() => {})
}

function handleRetry(file: KnowledgeFile) {
  file.status = 'processing'
  file.progress = 0
  ElMessage.info('已重新开始处理')
}

function handleUpload(uploadFiles: File[]) {
  const newFiles: KnowledgeFile[] = uploadFiles.map((file, index) => ({
    id: `new-${Date.now()}-${index}`,
    name: file.name,
    category: getFileCategory(file.name),
    extension: '.' + file.name.split('.').pop()?.toLowerCase(),
    size: file.size,
    url: URL.createObjectURL(file),
    status: 'pending' as ProcessStatus,
    progress: 0,
    createdAt: new Date().toISOString().split('T')[0],
    updatedAt: new Date().toISOString().split('T')[0],
  }))
  files.value.push(...newFiles)
  ElMessage.success(`已添加 ${newFiles.length} 个文件`)
}

function getFileCategory(filename: string): FileCategory {
  const ext = '.' + filename.split('.').pop()?.toLowerCase()
  const map: Record<string, FileCategory> = {
    '.pdf': 'document', '.docx': 'document', '.doc': 'document',
    '.xlsx': 'document', '.xls': 'document', '.pptx': 'document', '.ppt': 'document',
    '.md': 'document', '.txt': 'document', '.csv': 'document',
    '.jpg': 'image', '.jpeg': 'image', '.png': 'image', '.bmp': 'image', '.tiff': 'image',
    '.mp3': 'audio', '.wav': 'audio', '.m4a': 'audio', '.aac': 'audio', '.ogg': 'audio',
    '.mp4': 'video', '.avi': 'video', '.mov': 'video', '.mkv': 'video',
  }
  return map[ext] || 'document'
}

function handleRefresh() {
  ElMessage.success('已刷新')
}
</script>

<template>
  <div class="file-manager">
    <!-- 统计卡片 -->
    <div class="stat-cards">
      <div class="stat-card">
        <el-icon :size="24" color="#409eff"><Document /></el-icon>
        <div class="stat-info">
          <span class="stat-value">{{ totalFiles }}</span>
          <span class="stat-label">文件总数</span>
        </div>
      </div>
      <div class="stat-card">
        <el-icon :size="24" color="#67c23a"><CircleCheck /></el-icon>
        <div class="stat-info">
          <span class="stat-value">{{ processedFiles }}</span>
          <span class="stat-label">已处理</span>
        </div>
      </div>
      <div class="stat-card">
        <el-icon :size="24" color="#e6a23c"><TrendCharts /></el-icon>
        <div class="stat-info">
          <span class="stat-value">{{ processRate }}%</span>
          <span class="stat-label">完成进度</span>
        </div>
      </div>
    </div>

    <!-- 操作栏 -->
    <div class="action-bar">
      <el-button type="primary" @click="showUploader = true">
        <el-icon><Upload /></el-icon> 上传
      </el-button>
      <el-button @click="ElMessage.info('新建文件夹')">
        <el-icon><FolderAdd /></el-icon> 新建文件夹
      </el-button>
    </div>

    <!-- 文件列表 -->
    <FileTable
      :files="files"
      @preview="handlePreview"
      @download="handleDownload"
      @delete="handleDelete"
      @retry="handleRetry"
      @refresh="handleRefresh"
    />

    <!-- 上传弹窗 -->
    <FileUploader
      v-model:visible="showUploader"
      @upload="handleUpload"
    />

    <!-- 预览弹窗 -->
    <FilePreviewDialog
      v-model:visible="showPreview"
      :file="previewFile"
    />
  </div>
</template>

<style lang="scss" scoped>
.file-manager {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.stat-cards {
  display: flex;
  gap: 16px;
}

.stat-card {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);

  .stat-info {
    display: flex;
    flex-direction: column;

    .stat-value {
      font-size: 24px;
      font-weight: 600;
      color: #303133;
    }

    .stat-label {
      font-size: 13px;
      color: #909399;
    }
  }
}

.action-bar {
  display: flex;
  gap: 12px;
}
</style>
```

- [ ] **Step 2: 验证组件编译**

Run: `cd D:/Workspace/java/github/rag/fastrag/fastrag-demo && npx vite build 2>&1 | tail -5`

Expected: 构建成功

- [ ] **Step 3: Commit**

```bash
git add src/views/knowledge/detail/components/FileManager.vue
git commit -m "feat: add FileManager component with stats, upload and file table"
```

---

### Task 10: 改造知识库详情页为 Tab 结构

**Files:**
- Modify: `src/views/knowledge/detail/index.vue`

- [ ] **Step 1: 重写详情页主容器**

```vue
<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import FileManager from './components/FileManager.vue'
import RetrievalConfigPanel from './components/RetrievalConfigPanel.vue'

const route = useRoute()
const router = useRouter()
const kbId = route.params.id as string

// 知识库基础信息
const kbInfo = ref({
  id: kbId,
  name: '产品知识库',
  creator: '管理员',
  createdAt: '2026-01-15',
  model: 'text-embedding-v4',
  dimension: 1024,
  usedSize: '1.54 MB',
})

// Tab 配置
const activeTab = ref('files')
const showRetrievalConfig = ref(false)

const tabs = [
  { name: 'files', label: '文件管理', icon: 'Document' },
  { name: 'retrieval', label: '检索测试', icon: 'Search' },
  { name: 'graph', label: '知识图谱', icon: 'Share', disabled: true },
  { name: 'evaluation', label: 'RAG 评估', icon: 'TrendCharts', disabled: true },
  { name: 'settings', label: '设置', icon: 'Setting' },
]

function goToEdit() {
  router.push(`/knowledge/${kbId}/edit`)
}

function goToDebug() {
  router.push(`/knowledge/${kbId}/debug`)
}

function handleCopyId() {
  navigator.clipboard.writeText(kbId)
  ElMessage.success('已复制知识库 ID')
}

function handleSaveRetrievalConfig(config: any) {
  console.log('保存检索配置:', config)
  ElMessage.success('检索配置已保存')
  showRetrievalConfig.value = false
}
</script>

<template>
  <div class="knowledge-detail-page">
    <!-- 顶部信息栏 -->
    <div class="detail-header">
      <div class="header-left">
        <el-button text @click="router.push('/knowledge')">
          <el-icon><ArrowLeft /></el-icon> 返回
        </el-button>
        <el-divider direction="vertical" />
        <el-icon :size="24" color="#409eff"><Notebook /></el-icon>
        <div class="kb-title">
          <h2>{{ kbInfo.name }}</h2>
          <span class="kb-meta">知识库 · {{ kbInfo.usedSize }}</span>
        </div>
      </div>
      <div class="header-right">
        <el-button @click="handleCopyId">
          <el-icon><CopyDocument /></el-icon> 复制 ID
        </el-button>
        <el-button type="primary" @click="goToEdit">
          <el-icon><Edit /></el-icon> 编辑
        </el-button>
      </div>
    </div>

    <!-- Tab 导航 -->
    <el-tabs v-model="activeTab" class="detail-tabs">
      <el-tab-pane
        v-for="tab in tabs"
        :key="tab.name"
        :name="tab.name"
        :disabled="tab.disabled"
      >
        <template #label>
          <span class="tab-label">
            <el-icon><component :is="tab.icon" /></el-icon>
            {{ tab.label }}
          </span>
        </template>
      </el-tab-pane>
    </el-tabs>

    <!-- Tab 内容 -->
    <div class="tab-content">
      <!-- 文件管理 -->
      <FileManager v-if="activeTab === 'files'" />

      <!-- 检索测试 -->
      <div v-if="activeTab === 'retrieval'" class="retrieval-test">
        <el-button type="primary" @click="showRetrievalConfig = true">
          <el-icon><Setting /></el-icon> 检索配置
        </el-button>
        <p style="color: #909399; margin-top: 12px;">点击右上角"检索配置"按钮调整参数，或前往调试页面进行检索测试。</p>
        <el-button @click="goToDebug" style="margin-top: 12px;">
          <el-icon><Monitor /></el-icon> 打开调试页面
        </el-button>
      </div>

      <!-- 知识图谱（P2） -->
      <div v-if="activeTab === 'graph'" class="coming-soon">
        <el-icon :size="64" color="#c0c4cc"><Share /></el-icon>
        <h3>知识图谱</h3>
        <p>功能开发中，敬请期待...</p>
      </div>

      <!-- RAG 评估（P2） -->
      <div v-if="activeTab === 'evaluation'" class="coming-soon">
        <el-icon :size="64" color="#c0c4cc"><TrendCharts /></el-icon>
        <h3>RAG 评估</h3>
        <p>功能开发中，敬请期待...</p>
      </div>

      <!-- 设置 -->
      <div v-if="activeTab === 'settings'" class="settings-content">
        <el-button type="primary" @click="goToEdit">
          <el-icon><Edit /></el-icon> 编辑知识库设置
        </el-button>
      </div>
    </div>

    <!-- 检索配置侧栏 -->
    <RetrievalConfigPanel
      v-model:visible="showRetrievalConfig"
      @save="handleSaveRetrievalConfig"
    />
  </div>
</template>

<style lang="scss" scoped>
.knowledge-detail-page {
  padding: 16px 24px;
  background: #f5f7fa;
  min-height: 100vh;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;
  margin-bottom: 8px;

  .header-left {
    display: flex;
    align-items: center;
    gap: 12px;

    .kb-title {
      display: flex;
      flex-direction: column;

      h2 {
        margin: 0;
        font-size: 18px;
        font-weight: 600;
      }

      .kb-meta {
        font-size: 13px;
        color: #909399;
      }
    }
  }

  .header-right {
    display: flex;
    gap: 8px;
  }
}

.detail-tabs {
  background: white;
  border-radius: 8px;
  padding: 0 16px;
  margin-bottom: 16px;

  .tab-label {
    display: flex;
    align-items: center;
    gap: 6px;
  }
}

.tab-content {
  min-height: 400px;
}

.retrieval-test {
  background: white;
  border-radius: 8px;
  padding: 24px;
}

.coming-soon {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px;
  background: white;
  border-radius: 8px;

  h3 {
    margin: 16px 0 8px;
    color: #303133;
  }

  p {
    color: #909399;
  }
}

.settings-content {
  background: white;
  border-radius: 8px;
  padding: 24px;
}
</style>
```

- [ ] **Step 2: 验证页面编译**

Run: `cd D:/Workspace/java/github/rag/fastrag/fastrag-demo && npx vite build 2>&1 | tail -5`

Expected: 构建成功

- [ ] **Step 3: Commit**

```bash
git add src/views/knowledge/detail/index.vue
git commit -m "feat: refactor knowledge detail page with Tab structure and file management"
```

---

### Task 11: 验证完整构建

**Files:**
- None (verification only)

- [ ] **Step 1: 运行完整构建**

Run: `cd D:/Workspace/java/github/rag/fastrag/fastrag-demo && npx vite build`

Expected: 构建成功，无错误

- [ ] **Step 2: 启动开发服务器验证**

Run: `cd D:/Workspace/java/github/rag/fastrag/fastrag-demo && npx vite`

Expected: 开发服务器启动成功，访问 http://localhost:5173/knowledge/1 可看到新的 Tab 结构详情页

- [ ] **Step 3: 最终 Commit**

```bash
git add -A
git commit -m "feat: complete multimodal knowledge base management module

- Add file types and utility functions
- Add ProcessStatusBar for processing tracking
- Add FileTable with search, filter and selection
- Add FileUploader with multi-mode upload and engine config
- Add FilePreviewDialog with 6 format support
- Add AudioPlayer with playback and ASR transcript
- Add VideoPlayer with keyframes timeline and ASR
- Add RetrievalConfigPanel with search mode config
- Add FileManager with stats and file management
- Refactor knowledge detail page with Tab structure"
```

---

## 实现总结

| Task | 组件 | 优先级 | 状态 |
|---|---|---|---|
| 1 | 类型定义扩展 | P0 | - |
| 2 | ProcessStatusBar | P0 | - |
| 3 | FileTable | P0 | - |
| 4 | FileUploader | P0 | - |
| 5 | FilePreviewDialog | P0 | - |
| 6 | AudioPlayer | P1 | - |
| 7 | VideoPlayer | P1 | - |
| 8 | RetrievalConfigPanel | P1 | - |
| 9 | FileManager | P0 | - |
| 10 | 详情页改造 | P0 | - |
| 11 | 完整验证 | P0 | - |
