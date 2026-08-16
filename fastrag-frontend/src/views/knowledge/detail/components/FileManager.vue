<script setup lang="ts">
import type { KnowledgeFile } from '@/types/knowledge'
import { Upload, FolderAdd, Delete, Rank, FolderOpened } from '@element-plus/icons-vue'
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
import StrategyChangeDialog from './StrategyChangeDialog.vue'
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
  createFolder,
  renameFolder,
  deleteFolder,
  moveFileToKb,
  loadDeletedFiles,
  restore,
  permanentDelete,
  emptyBin,
} = useFiles(kbId)

// 解析策略（用于上传时把 strategyId 解析成 name）
const { strategies, resolveByExtension } = useParseStrategy(kbId)

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
    // 全量转发上传向导配置（引擎/语言/编码/优先级/重试/媒体配置/解析策略），后端持久化并消费
    const processConfig: Record<string, unknown> = {}
    if (config.processingMode && config.processingMode !== 'chunk') {
      processConfig.processingMode = config.processingMode
    }
    if (config.parseStrategyId) {
      processConfig.parseStrategyId = config.parseStrategyId
    }
    if (config.language) {
      processConfig.language = config.language
    }
    if (config.encoding) {
      processConfig.encoding = config.encoding
    }
    if (config.priority) {
      processConfig.priority = config.priority
    }
    if (config.retryCount != null) {
      processConfig.retryCount = config.retryCount
    }
    if (config.engineConfig) {
      processConfig.engineConfig = config.engineConfig
    }
    if (config.mediaConfig) {
      processConfig.mediaConfig = config.mediaConfig
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
  newFolderParentId.value = selectedFolderId.value || 'root'
  newFolderDialogVisible.value = true
}

async function handleNewFolderConfirm() {
  if (!newFolderName.value.trim()) {
    ElMessage.warning('请输入文件夹名称')
    return
  }
  try {
    await createFolder(newFolderName.value.trim(), newFolderParentId.value)
    newFolderDialogVisible.value = false
    ElMessage.success(`已创建文件夹：${newFolderName.value}`)
  } catch (e: any) {
    ElMessage.error(e?.message || '创建文件夹失败')
  }
}

// --- Folder tree selection ---
const selectedFolderId = ref<string | null>(null)

const filteredFilesByFolder = computed(() => {
  if (!selectedFolderId.value || selectedFolderId.value === 'root') {
    // 根目录：只显示不属于任何文件夹的文件
    return files.value.filter(f => !f.folderId)
  }
  return files.value.filter(f => f.folderId === selectedFolderId.value)
})

function selectFolder(folderId: string | null) {
  selectedFolderId.value = folderId
}

// --- Breadcrumb navigation ---
interface BreadcrumbItem {
  id: string | null
  label: string
}

const breadcrumbs = computed<BreadcrumbItem[]>(() => {
  const crumbs: BreadcrumbItem[] = []
  const targetId = selectedFolderId.value

  if (!targetId || targetId === 'root') {
    return crumbs // At root — no crumbs needed
  }

  // Walk the tree to build path from root to targetId
  function findPath(nodes: any[], path: BreadcrumbItem[]): BreadcrumbItem[] | null {
    for (const node of nodes) {
      const current: BreadcrumbItem = { id: node.id, label: node.label }
      if (node.id === targetId) {
        return [...path, current]
      }
      if (node.children) {
        const result = findPath(node.children, [...path, current])
        if (result) return result
      }
    }
    return null
  }

  return findPath(folders.value, crumbs) || []
})

function navigateToFolder(folderId: string | null) {
  selectedFolderId.value = folderId
}

// Current sub-folders for the selected directory
const currentSubFolders = computed<Array<{ id: string; label: string; createdAt?: string; updatedAt?: string }>>(() => {
  const targetId = selectedFolderId.value

  // At root level — return top-level folders (children of root node)
  if (!targetId || targetId === 'root') {
    // folders.value is the tree array, top-level nodes are root's children
    return folders.value.map(f => ({ id: f.id, label: f.label, createdAt: f.createdAt, updatedAt: f.updatedAt }))
  }

  // Find the node and return its children
  function findNode(nodes: any[]): any[] | null {
    for (const node of nodes) {
      if (node.id === targetId) {
        return node.children || []
      }
      if (node.children) {
        const result = findNode(node.children)
        if (result) return result
      }
    }
    return null
  }

  const children = findNode(folders.value)
  return children ? children.map((f: any) => ({ id: f.id, label: f.label, createdAt: f.createdAt, updatedAt: f.updatedAt })) : []
})

function enterFolder(folderId: string) {
  selectedFolderId.value = folderId
}

// --- Folder context menu ---
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
    if (selectedFolderId.value === folderId) {
      selectedFolderId.value = null
    }
    ElMessage.success('文件夹已删除')
  } catch (e: any) {
    // cancel → 用户取消，静默；其他错误由 request 拦截器统一提示
    if (e === 'cancel') return
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

async function handleBulkMoveConfirm(_fileId: string, targetFolderId: string, targetKbId?: string) {
  try {
    if (targetKbId && targetKbId !== kbId) {
      // 跨 KB 批量移动
      for (const f of selectedFiles.value) {
        await moveFileToKb(f.id, targetKbId, targetFolderId)
      }
      selectedFiles.value = []
      ElMessage.success('已移动选中文件到其他知识库')
    } else {
      await bulkMove(targetFolderId)
      ElMessage.success('已移动选中文件')
    }
    bulkMoveTargetVisible.value = false
  } catch (e: any) {
    ElMessage.error(e?.message || '批量移动失败')
  }
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

async function handleMoveConfirm(fileId: string, targetFolderId: string, targetKbId?: string) {
  try {
    if (targetKbId && targetKbId !== kbId) {
      await moveFileToKb(fileId, targetKbId, targetFolderId)
      ElMessage.success('文件已移动到其他知识库，正在重新处理')
    } else {
      await move(fileId, targetFolderId)
      ElMessage.success('文件已移动')
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '移动失败')
  }
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

// --- Change strategy（换策略重新分片对话框，ADR-0001：绑定变更与重切原子完成） ---
const strategyChangeVisible = ref(false)
const strategyChangeFile = ref<KnowledgeFile | null>(null)

function handleChangeStrategy(file: KnowledgeFile) {
  strategyChangeFile.value = file
  strategyChangeVisible.value = true
}

async function handleStrategyChangeSubmitted(fileId: string) {
  // 重切任务已入队：刷新列表并轮询处理状态
  await load()
  startPolling([fileId])
}

// --- Toggle graph build ---
async function handleToggleGraphBuild(file: KnowledgeFile, enabled: boolean) {
  try {
    await toggleGraphBuild(file.id, enabled)
    if (enabled) {
      // 仅触发图谱构建（基于已有 chunks），不重新解析/分块/向量化
      await api.buildGraphIndex(props.kbId!, [file.id], 'full')
      ElMessage.success(`已开启「${file.name}」的知识图谱构建`)
    } else {
      // 关闭时清理该文件已有的图谱数据
      await api.deleteFileGraph(props.kbId!, file.id)
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
      <!-- Main content -->
      <div class="file-manager__main">
        <!-- Action bar (merged with breadcrumb) -->
        <div class="file-manager__actions">
          <div class="file-manager__actions-left">
            <el-button v-permission="'kb:upload'" type="primary" :icon="Upload" @click="openUploader">
              上传
            </el-button>
            <el-button :icon="Delete" @click="openRecycleBin">
              回收站
            </el-button>
          </div>
          <!-- Breadcrumb + 新建文件夹 -->
          <div class="file-manager__actions-right">
            <el-breadcrumb separator="/">
              <el-breadcrumb-item @click="navigateToFolder(null)">
                <el-icon><FolderOpened /></el-icon> 全部文件
              </el-breadcrumb-item>
              <el-breadcrumb-item
                v-for="crumb in breadcrumbs"
                :key="crumb.id"
                @click="navigateToFolder(crumb.id)"
              >
                {{ crumb.label }}
              </el-breadcrumb-item>
            </el-breadcrumb>
            <el-button :icon="FolderAdd" size="small" @click="handleNewFolder">新建文件夹</el-button>
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
          :folders="currentSubFolders"
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
          @enter-folder="enterFolder"
          @rename-folder="handleFolderRename"
          @delete-folder="handleDeleteFolder"
        />

      </div> <!-- /.file-manager__main -->
    </div> <!-- /.file-manager__body -->

    <!-- Upload dialog -->
    <FileUploader
      v-model:visible="uploaderVisible"
      :kb-id="kbId"
      :existing-file-names="files.map((f) => f.name)"
      :folder-id="selectedFolderId"
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
      :folders="folders"
      @confirm="handleMoveConfirm"
    />

    <!-- 分片策略设置对话框（点击文件列表策略 tag 打开，保存为文件专属策略并重切） -->
    <StrategyChangeDialog
      v-model="strategyChangeVisible"
      :kb-id="kbId"
      :file="strategyChangeFile"
      @submitted="handleStrategyChangeSubmitted"
    />

    <!-- Batch move target dialog (复用文件树选择) -->
    <MoveFileDialog
      v-model:visible="bulkMoveTargetVisible"
      :kb-id="kbId"
      :file="selectedFiles[0] || null"
      :folders="folders"
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
    flex: 1;
    min-height: 0;
  }

  // --- Main content area ---
  &__main {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: $spacing-base;
  }

  // --- Breadcrumb navigation (inline in action bar) ---
  &__actions-right {
    display: flex;
    align-items: center;
    gap: 12px;
    flex: 1;
    justify-content: flex-end;
    margin-left: 24px;

    :deep(.el-breadcrumb) {
      font-size: 14px;

      .el-breadcrumb__item {
        .el-breadcrumb__inner {
          cursor: pointer;
          display: flex;
          align-items: center;
          gap: 4px;

          &:hover {
            color: $color-primary;
          }
        }
      }
    }
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
