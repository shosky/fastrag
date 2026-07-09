<script setup lang="ts">
import type { KnowledgeFile } from '@/types/knowledge'
import { getFileCategory } from '@/types/knowledge'
import { Document, CircleCheck, TrendCharts, Upload, FolderAdd, Delete, Rank, FolderOpened, Folder, MoreFilled, Edit } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import * as api from '@/api'
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

// --- 状态轮询 ---
let pollingTimer: ReturnType<typeof setInterval> | null = null
// 追踪本次触发处理的文件 ID，轮询只针对这些文件判断是否完成
const pendingProcessFileIds = ref<Set<string>>(new Set())

function startPolling(fileIds?: string[]) {
  stopPolling()
  if (fileIds && fileIds.length > 0) {
    pendingProcessFileIds.value = new Set(fileIds)
  }
  pollingTimer = setInterval(async () => {
    await load()
    const trackingSet = pendingProcessFileIds.value
    const hasProcessing = trackingSet.size > 0
      && [...trackingSet].some(id =>
        files.value.some(f => f.id === id && f.status === 'processing'))
    if (!hasProcessing) {
      stopPolling()
    }
  }, 3000)
}

function stopPolling() {
  if (pollingTimer !== null) {
    clearInterval(pollingTimer)
    pollingTimer = null
  }
  pendingProcessFileIds.value.clear()
}

const props = defineProps<{
  kbId?: string
}>()

const router = useRouter()
const kbId = props.kbId || 'default'

// --- Data via composable ---
const {
  files,
  folders,
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
  toggleGraphBuild,
  upload,
  changeStrategy,
  createFolder,
  renameFolder,
  deleteFolder,
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

async function handleUpload(_selectedFiles: File[], config: UploadConfig & { fileIds?: string[] }) {
  // 先刷新列表，让已上传文件（pending 状态）立即可见
  await load()

  // 触发已上传文件的处理流程
  const fileIds = config.fileIds || []
  if (fileIds.length > 0) {
    // fire-and-forget：触发处理请求（后端通过 RabbitMQ 异步执行，请求立即返回）
    const processConfig: Record<string, unknown> = {}
    if (config.processingMode && config.processingMode !== 'chunk') {
      processConfig.processingMode = config.processingMode
    }
    if (config.qaConfig) {
      processConfig.qaConfig = config.qaConfig
    }

    fileIds.forEach((fid) => {
      api.processFile(kbId, fid, Object.keys(processConfig).length > 0 ? processConfig : undefined).catch((e) => {
        console.error('Failed to trigger process for file:', fid, e)
      })
    })
    ElMessage.success(`已提交 ${fileIds.length} 个文件的处理任务`)

    // 立即开始轮询状态（追踪本次提交的文件 ID）
    startPolling(fileIds)
  }
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

// --- Folder tree selection ---
const selectedFolderId = ref<string | null>(null)

const filteredFilesByFolder = computed(() => {
  if (!selectedFolderId.value || selectedFolderId.value === 'root') {
    return files.value
  }
  return files.value.filter(f => f.folderId === selectedFolderId.value)
})

const folderFileCount = (folderId: string): number => {
  if (!folderId || folderId === 'root') return files.value.length
  return files.value.filter(f => f.folderId === folderId).length
}

function selectFolder(folderId: string | null) {
  selectedFolderId.value = folderId
}

// --- Folder context menu ---
const contextFolderId = ref<string | null>(null)
const contextFolderName = ref('')

// Rename dialog
const folderRenameDialogVisible = ref(false)
const folderRenameId = ref<string>('')
const folderRenameName = ref('')

function handleFolderRename(folderId: string, currentName: string) {
  folderRenameId.value = folderId
  folderRenameName.value = currentName
  folderRenameDialogVisible.value = true
}

async function handleFolderRenameConfirm() {
  if (!folderRenameName.value.trim()) { ElMessage.warning('文件夹名称不能为空'); return }
  try {
    await renameFolder(folderRenameId.value, folderRenameName.value.trim())
    folderRenameDialogVisible.value = false
    ElMessage.success('文件夹已重命名')
  } catch (e: any) {
    ElMessage.error(e?.message || '重命名失败')
  }
}

async function handleDeleteFolder(folderId: string) {
  try {
    await ElMessageBox.confirm(
      '确定要删除该文件夹吗？\n要求：文件夹必须为空（无子文件夹，无文件）才能删除。',
      '删除文件夹',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
    await deleteFolder(folderId)
    // 如果删的是当前选中的文件夹，重置筛选
    if (selectedFolderId.value === folderId) {
      selectedFolderId.value = null
    }
    ElMessage.success('文件夹已删除')
  } catch (e: any) {
    if (e !== 'cancel' && e?.message) {
      ElMessage.error(e?.message || '删除失败')
    }
  }
}

function handleCreateSubFolder(folderId: string) {
  contextFolderId.value = folderId
  newFolderParentId.value = folderId
  newFolderName.value = ''
  newFolderDialogVisible.value = true
}

function handleFolderCommand(cmd: string, data: { id: string; label: string }) {
  if (cmd === 'createSub') {
    handleCreateSubFolder(data.id)
  } else if (cmd === 'rename') {
    handleFolderRename(data.id, data.label)
  } else if (cmd === 'delete') {
    handleDeleteFolder(data.id)
  }
}

// --- FileTable event handlers ---
function handlePreview(file: KnowledgeFile) {
  previewFile.value = file
  previewVisible.value = true
}

async function handleDownload(file: KnowledgeFile) {
  try {
    const response = await api.downloadFile(kbId, file.id)
    const blob = new Blob([response])
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    // 优先使用 Content-Disposition 中的文件名，否则回退到 file.name
    const disposition = (response as any)?.headers?.['content-disposition']
    let fileName = file.name
    if (disposition) {
      const match = disposition.match(/filename\*?=(?:UTF-8'')?([^;\s]+)/i)
      if (match) fileName = decodeURIComponent(match[1])
    }
    a.download = fileName
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    ElMessage.success(`开始下载: ${file.name}`)
  } catch {
    ElMessage.error(`下载失败: ${file.name}`)
  }
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

async function handleBulkExport() {
  if (selectedFiles.value.length === 0) return
  try {
    await ElMessageBox.confirm(
      `确认导出选中的 ${selectedFiles.value.length} 个文件？`,
      '批量导出确认',
      { type: 'info', confirmButtonText: '导出', cancelButtonText: '取消' },
    )
    for (const file of selectedFiles.value) {
      try {
        const response = await api.downloadFile(kbId, file.id)
        const blob = new Blob([response])
        const url = URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = file.name
        document.body.appendChild(a)
        a.click()
        document.body.removeChild(a)
        URL.revokeObjectURL(url)
      } catch {
        ElMessage.error(`导出失败: ${file.name}`)
      }
    }
    ElMessage.success(`共导出 ${selectedFiles.value.length} 个文件`)
  } catch {
    // 用户取消
  }
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

// --- Toggle graph build ---
async function handleToggleGraphBuild(file: KnowledgeFile, enabled: boolean) {
  try {
    await toggleGraphBuild(file.id, enabled)
    if (enabled) {
      // 开启图谱后自动重新处理文件，触发知识图谱提取
      await api.processFile(props.kbId!, file.id)
      startPolling([file.id])
      ElMessage.success(`已开启「${file.name}」的知识图谱构建，正在重新处理...`)
    } else {
      ElMessage.success(`已关闭「${file.name}」的知识图谱构建`)
    }
  } catch {
    ElMessage.error('操作失败')
  }
}

// --- Lifecycle ---
onMounted(() => {
  load()
})

onBeforeUnmount(() => {
  stopPolling()
})
</script>

<template>
  <div class="file-manager">
    <div class="file-manager__body">
      <!-- Folder tree sidebar -->
      <div class="file-manager__sidebar">
        <div class="file-manager__sidebar-title">文件目录</div>
        <div class="file-manager__tree-wrapper">
          <div
            class="file-manager__tree-node"
            :class="{ 'is-active': selectedFolderId === null || selectedFolderId === 'root' }"
            @click="selectFolder(null)"
          >
            <el-icon class="file-manager__tree-icon"><FolderOpened /></el-icon>
            <span class="file-manager__tree-label">全部文件</span>
            <span class="file-manager__tree-count">{{ files.length }}</span>
          </div>
          <el-tree
            :data="folders"
            :props="{ label: 'label', children: 'children' }"
            node-key="id"
            default-expand-all
            highlight-current
            :current-node-key="selectedFolderId"
            @node-click="(data: any) => selectFolder(data.id)"
            class="file-manager__tree"
          >
            <template #default="{ node, data }">
              <div class="file-manager__tree-node-inner">
                <el-icon class="file-manager__tree-icon"><Folder /></el-icon>
                <span class="file-manager__tree-label">{{ node.label }}</span>
                <span class="file-manager__tree-count">{{ folderFileCount(data.id) }}</span>
                <el-dropdown trigger="click" @command="(cmd: string) => handleFolderCommand(cmd, data)">
                  <el-button class="file-manager__tree-menu-btn" :icon="MoreFilled" link size="small" @click.stop />
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="createSub">新建子文件夹</el-dropdown-item>
                      <el-dropdown-item command="rename">重命名</el-dropdown-item>
                      <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </template>
          </el-tree>
        </div>
      </div>

      <!-- Main content (right side) -->
      <div class="file-manager__main">
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
            <el-button size="small" @click="handleBulkExport">导出</el-button>
          </div>
        </div>

        <!-- File table -->
        <FileTable
          :files="filteredFilesByFolder"
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
          @toggle-graph-build="handleToggleGraphBuild"
        />

      </div> <!-- /.file-manager__main -->
    </div> <!-- /.file-manager__body -->

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
      :kb-id="kbId"
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

    <!-- Folder rename dialog -->
    <el-dialog v-model="folderRenameDialogVisible" title="重命名文件夹" width="420px">
      <el-form label-position="top">
        <el-form-item label="文件夹名称" required>
          <el-input v-model="folderRenameName" placeholder="请输入新名称" maxlength="50" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="folderRenameDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleFolderRenameConfirm">确认</el-button>
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
  height: 100%;

  &__body {
    display: flex;
    gap: $spacing-base;
    flex: 1;
    min-height: 0;
  }

  // --- Folder tree sidebar ---
  &__sidebar {
    width: 220px;
    min-width: 220px;
    background: $bg-white;
    border-radius: $radius-base;
    box-shadow: $shadow-sm;
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }

  &__sidebar-title {
    font-size: 14px;
    font-weight: 600;
    color: $text-primary;
    padding: $spacing-base $spacing-base $spacing-sm;
    border-bottom: 1px solid $border-lighter;
  }

  &__tree-wrapper {
    flex: 1;
    overflow-y: auto;
    padding: $spacing-xs;
  }

  &__tree-node {
    display: flex;
    align-items: center;
    gap: $spacing-xs;
    padding: 8px 10px;
    border-radius: $radius-base;
    cursor: pointer;
    transition: background 0.15s;
    margin-bottom: 2px;

    &:hover {
      background: $bg-hover;
    }

    &.is-active {
      background: $bg-active;
      color: $color-primary;
      font-weight: 500;
    }
  }

  &__tree-node-inner {
    display: flex;
    align-items: center;
    gap: $spacing-xs;
    flex: 1;
    min-width: 0;
  }

  &__tree-icon {
    color: $color-warning;
    flex-shrink: 0;
  }

  &__tree-label {
    font-size: 13px;
    color: $text-primary;
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__tree-count {
    font-size: 12px;
    color: $text-secondary;
    background: $bg-hover;
    padding: 0 6px;
    border-radius: 10px;
    line-height: 18px;
    flex-shrink: 0;
  }

  &__tree-menu-btn {
    visibility: hidden;
    margin-left: 2px;
    flex-shrink: 0;
  }

  &__tree-node-inner:hover &__tree-menu-btn {
    visibility: visible;
  }

  // --- Tree overrides ---
  :deep(.file-manager__tree) {
    background: transparent;
    border: none;

    .el-tree-node__content {
      height: auto;
      padding: 2px 0;
      border-radius: $radius-base;

      &:hover {
        background: $bg-hover;
      }
    }

    .el-tree-node.is-current > .el-tree-node__content {
      background: $bg-active;
    }
  }

  // --- Main content area ---
  &__main {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: $spacing-base;
  }

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
