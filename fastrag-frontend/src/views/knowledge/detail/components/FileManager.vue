<script setup lang="ts">
import type { KnowledgeFile } from '@/types/knowledge'
import { getFileCategory } from '@/types/knowledge'
import { Document, CircleCheck, TrendCharts, Upload, FolderAdd, Delete, Rank } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import FileTable from './FileTable.vue'
import FileUploader from './FileUploader.vue'
import type { UploadConfig } from './FileUploader.vue'
import FilePreviewDialog from './FilePreviewDialog.vue'
import ChunkManagementPanel from './ChunkManagementPanel.vue'
import MoveFileDialog from './MoveFileDialog.vue'
import RenameFileDialog from './RenameFileDialog.vue'
import RecycleBinDialog from './RecycleBinDialog.vue'
import { useFiles } from '@/composables/useFiles'
import { useParseStrategy } from '@/composables/useParseStrategy'

const props = defineProps<{
  kbId?: string
}>()

const router = useRouter()
const kbId = props.kbId || 'default'

// --- Data via composable ---
const {
  files,
  loading,
  selectedFiles,
  deletedFiles,
  load,
  refresh,
  remove,
  bulkDelete,
  rename,
  move,
  bulkMove,
  copy,
  retry,
  upload,
  changeStrategy,
  createFolder,
  loadDeletedFiles,
  restore,
  permanentDelete,
  emptyBin,
} = useFiles(kbId)

// 解析策略（用于上传时把 strategyId 解析成 name）
const { strategies, resolveByExtension } = useParseStrategy(kbId)

// --- Stat cards ---
const totalFiles = computed(() => files.value.length)
const completedFiles = computed(() =>
  files.value.filter((f) => f.status === 'completed').length,
)
const completionRate = computed(() => {
  if (files.value.length === 0) return 0
  return Math.round((completedFiles.value / files.value.length) * 100)
})

// --- Upload dialog ---
const uploaderVisible = ref(false)

function openUploader() {
  uploaderVisible.value = true
}

function handleUpload(selectedFiles: File[], config: UploadConfig) {
  // 解析策略：优先用用户在向导里选的；否则按扩展名匹配；都没有则用默认
  const metas = selectedFiles.map((file) => {
    const category = getFileCategory(file.name)
    const extension = '.' + (file.name.split('.').pop()?.toLowerCase() || '')
    let strategyId: string | undefined = config.parseStrategyId || undefined
    let strategyName = ''
    if (strategyId) {
      const s = strategies.value.find((x) => x.id === strategyId)
      strategyName = s?.name || ''
    } else {
      const matched = resolveByExtension(extension)
      strategyId = matched?.id
      strategyName = matched?.name || '默认解析方法'
    }
    return { name: file.name, category, extension, size: file.size, parseStrategyId: strategyId, parseStrategyName: strategyName }
  })
  upload(metas)
  ElMessage.success(`已添加 ${selectedFiles.length} 个文件到知识库`)
}

// --- New folder ---
const newFolderDialogVisible = ref(false)
const newFolderName = ref('')
const newFolderParentId = ref('root')

function handleNewFolder() {
  newFolderName.value = ''
  newFolderParentId.value = 'root'
  newFolderDialogVisible.value = true
}

function handleNewFolderConfirm() {
  if (!newFolderName.value.trim()) {
    ElMessage.warning('请输入文件夹名称')
    return
  }
  createFolder(newFolderName.value.trim(), newFolderParentId.value)
  newFolderDialogVisible.value = false
  ElMessage.success(`已创建文件夹：${newFolderName.value}`)
}

// --- FileTable event handlers ---
function handlePreview(file: KnowledgeFile) {
  previewFile.value = file
  previewVisible.value = true
}

function handleDownload(file: KnowledgeFile) {
  // 真实下载：mock 场景生成一个文本 blob（含文件元信息）供下载
  const content = `[演示下载] 文件：${file.name}\n类型：${file.category}\n大小：${file.size} bytes\n解析策略：${file.parseStrategyName || '默认'}\n切片数：${file.chunkCount || 0}\n\n（真实环境应从 ${file.url || '文件存储'} 拉取原始文件）`
  const blob = new Blob([content], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = file.name + '.txt'
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
  ElMessage.success(`开始下载: ${file.name}`)
}

async function handleDelete(file: KnowledgeFile) {
  try {
    await ElMessageBox.confirm(
      `确定要删除文件「${file.name}」吗？删除后可在回收站恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
    remove(file.id)
    ElMessage.success('文件已删除')
  } catch {
    // 用户取消
  }
}

function handleRetry(file: KnowledgeFile) {
  retry(file.id)
  ElMessage.info(`正在重试处理: ${file.name}`)
}

function handleRefresh() {
  refresh()
}

// --- 批量操作 ---
async function handleBulkDelete() {
  if (selectedFiles.value.length === 0) return
  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${selectedFiles.value.length} 个文件吗？此操作不可恢复。`,
      '批量删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
    bulkDelete()
    ElMessage.success('已删除选中文件')
  } catch {
    // 用户取消
  }
}

function handleBulkMove() {
  if (selectedFiles.value.length === 0) return
  bulkMoveTargetVisible.value = true
}

// --- 批量移动对话框复用 MoveFileDialog 的文件夹树，简化为选目标文件夹 ---
const bulkMoveTargetVisible = ref(false)

function handleBulkMoveConfirm(_fileId: string, targetFolderId: string) {
  bulkMove(targetFolderId)
  bulkMoveTargetVisible.value = false
  ElMessage.success('已移动选中文件')
}

function handleSelectionChange(selected: KnowledgeFile[]) {
  selectedFiles.value = selected
}

// --- 回收站 ---
const recycleBinVisible = ref(false)

function openRecycleBin() {
  loadDeletedFiles()
  recycleBinVisible.value = true
}

function handleRecycleRestore(fileId: string) {
  restore(fileId)
  loadDeletedFiles()
}

function handleRecyclePermanentDelete(fileId: string) {
  permanentDelete(fileId)
}

function handleRecycleEmpty() {
  emptyBin()
}

// --- File preview dialog ---
const previewVisible = ref(false)
const previewFile = ref<KnowledgeFile | null>(null)

// --- Chunk management panel ---
const chunkPanelVisible = ref(false)
const chunkPanelFile = ref<KnowledgeFile | null>(null)

function handleManageChunks(file: KnowledgeFile) {
  if (props.kbId) {
    router.push({
      path: `/knowledge/${props.kbId}/chunks/${file.id}`,
      query: { category: file.category },
    })
  } else {
    chunkPanelFile.value = file
    chunkPanelVisible.value = true
  }
}

// --- Move file dialog ---
const moveDialogVisible = ref(false)
const moveFile = ref<KnowledgeFile | null>(null)

function handleMove(file: KnowledgeFile) {
  moveFile.value = file
  moveDialogVisible.value = true
}

function handleMoveConfirm(fileId: string, targetFolderId: string) {
  move(fileId, targetFolderId)
  ElMessage.success('文件已移动')
}

// --- Rename file dialog ---
const renameDialogVisible = ref(false)
const renameFile = ref<KnowledgeFile | null>(null)

function handleRename(file: KnowledgeFile) {
  renameFile.value = file
  renameDialogVisible.value = true
}

function handleRenameConfirm(fileId: string, newName: string) {
  rename(fileId, newName)
  ElMessage.success('文件已重命名')
}

// --- Copy file ---
function handleCopy(file: KnowledgeFile) {
  copy(file.id)
  ElMessage.success(`已复制文件: ${file.name}`)
}

// --- Change strategy ---
function handleChangeStrategy(file: KnowledgeFile, strategyId: string, strategyName: string) {
  changeStrategy(file.id, strategyId, strategyName)
  ElMessage.success(`已修改解析策略：${strategyName}`)
}

// --- Lifecycle ---
onMounted(() => {
  load()
})

onBeforeUnmount(() => {
  // 组件卸载时无需特殊清理（mock 数据在内存中保留）
})
</script>

<template>
  <div class="file-manager">
    <!-- Stat cards -->
    <div class="file-manager__stats">
      <div class="file-manager__stat-card">
        <div class="file-manager__stat-icon file-manager__stat-icon--blue">
          <el-icon :size="24"><Document /></el-icon>
        </div>
        <div class="file-manager__stat-info">
          <span class="file-manager__stat-value">{{ totalFiles }}</span>
          <span class="file-manager__stat-label">文件总数</span>
        </div>
      </div>

      <div class="file-manager__stat-card">
        <div class="file-manager__stat-icon file-manager__stat-icon--green">
          <el-icon :size="24"><CircleCheck /></el-icon>
        </div>
        <div class="file-manager__stat-info">
          <span class="file-manager__stat-value">{{ completedFiles }}</span>
          <span class="file-manager__stat-label">已处理</span>
        </div>
      </div>

      <div class="file-manager__stat-card">
        <div class="file-manager__stat-icon file-manager__stat-icon--orange">
          <el-icon :size="24"><TrendCharts /></el-icon>
        </div>
        <div class="file-manager__stat-info">
          <span class="file-manager__stat-value">{{ completionRate }}%</span>
          <span class="file-manager__stat-label">完成进度</span>
        </div>
      </div>
    </div>

    <!-- Action bar -->
    <div class="file-manager__actions">
      <div class="file-manager__actions-left">
        <el-button v-permission="'kb:upload'" type="primary" :icon="Upload" @click="openUploader">
          上传
        </el-button>
        <el-button :icon="FolderAdd" @click="handleNewFolder">
          新建文件夹
        </el-button>
        <el-button :icon="Delete" @click="openRecycleBin">
          回收站
        </el-button>
      </div>
      <!-- 批量操作栏：仅当有选中文件时显示 -->
      <div v-if="selectedFiles.length > 0" class="file-manager__bulk-actions">
        <span class="file-manager__bulk-count">已选 {{ selectedFiles.length }} 项</span>
        <el-button :icon="Rank" size="small" @click="handleBulkMove">批量移动</el-button>
        <el-button :icon="Delete" size="small" type="danger" @click="handleBulkDelete">批量删除</el-button>
      </div>
    </div>

    <!-- File table -->
    <FileTable
      :files="files"
      :loading="loading"
      :kb-id="kbId"
      :strategies="strategies"
      @preview="handlePreview"
      @download="handleDownload"
      @delete="handleDelete"
      @retry="handleRetry"
      @refresh="handleRefresh"
      @manage-chunks="handleManageChunks"
      @move="handleMove"
      @rename="handleRename"
      @copy="handleCopy"
      @selection-change="handleSelectionChange"
      @change-strategy="handleChangeStrategy"
    />

    <!-- Upload dialog -->
    <FileUploader
      v-model:visible="uploaderVisible"
      :kb-id="kbId"
      :existing-file-names="files.map((f) => f.name)"
      @upload="handleUpload"
    />

    <!-- Preview dialog -->
    <FilePreviewDialog
      v-model:visible="previewVisible"
      :file="previewFile"
      @download="handleDownload"
    />

    <!-- Chunk management panel -->
    <ChunkManagementPanel
      v-model:visible="chunkPanelVisible"
      :file-name="chunkPanelFile?.name || ''"
      :file-type="(chunkPanelFile?.category || 'document') as 'video' | 'audio' | 'document'"
    />

    <!-- Move file dialog -->
    <MoveFileDialog
      v-model:visible="moveDialogVisible"
      :kb-id="kbId"
      :file="moveFile"
      @confirm="handleMoveConfirm"
    />

    <!-- Batch move target dialog (复用文件树选择) -->
    <MoveFileDialog
      v-model:visible="bulkMoveTargetVisible"
      :kb-id="kbId"
      :file="selectedFiles[0] || null"
      :title="`批量移动 ${selectedFiles.length} 个文件到`"
      @confirm="handleBulkMoveConfirm"
    />

    <!-- Rename file dialog -->
    <RenameFileDialog
      v-model:visible="renameDialogVisible"
      :file="renameFile"
      @confirm="handleRenameConfirm"
    />

    <!-- New folder dialog -->
    <el-dialog v-model="newFolderDialogVisible" title="新建文件夹" width="420px">
      <el-form label-position="top">
        <el-form-item label="文件夹名称" required>
          <el-input v-model="newFolderName" placeholder="请输入文件夹名称" maxlength="50" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="newFolderDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleNewFolderConfirm">创建</el-button>
      </template>
    </el-dialog>

    <!-- 回收站 -->
    <RecycleBinDialog
      v-model:visible="recycleBinVisible"
      :deleted-files="deletedFiles"
      @restore="handleRecycleRestore"
      @permanent-delete="handleRecyclePermanentDelete"
      @empty="handleRecycleEmpty"
    />
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.file-manager {
  display: flex;
  flex-direction: column;
  gap: $spacing-base;

  // --- Stat cards ---
  &__stats {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: $spacing-base;
  }

  &__stat-card {
    display: flex;
    align-items: center;
    gap: $spacing-md;
    padding: $spacing-base $spacing-lg;
    background: $bg-white;
    border-radius: $radius-base;
    box-shadow: $shadow-sm;
  }

  &__stat-icon {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 48px;
    height: 48px;
    border-radius: $radius-lg;
    flex-shrink: 0;

    &--blue {
      background: #e3f2fd;
      color: #1e88e5;
    }

    &--green {
      background: #e8f5e9;
      color: #43a047;
    }

    &--orange {
      background: #fff3e0;
      color: #fb8c00;
    }
  }

  &__stat-info {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  &__stat-value {
    font-size: 24px;
    font-weight: 600;
    color: $text-primary;
    line-height: 1.2;
  }

  &__stat-label {
    font-size: 13px;
    color: $text-secondary;
  }

  // --- Action bar ---
  &__actions {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: $spacing-sm;
    flex-wrap: wrap;
  }

  &__actions-left {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
  }

  &__bulk-actions {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    padding: $spacing-xs $spacing-base;
    background: $bg-active;
    border-radius: $radius-base;
  }

  &__bulk-count {
    font-size: 13px;
    color: $color-primary;
    font-weight: 500;
  }
}
</style>
