import { ref } from 'vue'
import type { KnowledgeFile } from '@/types/knowledge'
import {
  getFiles,
  getDeletedFiles,
  getFolders,
  findFolderName,
  createFolder as mockCreateFolder,
  addFile,
  updateFile,
  deleteFile,
  restoreFile as mockRestoreFile,
  permanentlyDeleteFile,
  emptyRecycleBin,
  copyFile,
  advanceProcessing,
  type FolderNode,
} from '@/mock/files'

// ===========================================================================
// 文件管理 composable
//
// 负责：加载文件/文件夹、CRUD、批量操作、上传后驱动处理进度仿真。
// 进度仿真用 setInterval 按 category 走 PROCESS_STAGES，到达 completed 停止。
// ===========================================================================

export function useFiles(kbId: string = 'default') {
  const files = ref<KnowledgeFile[]>([])
  const folders = ref<FolderNode[]>([])
  const loading = ref(false)
  const selectedFiles = ref<KnowledgeFile[]>([])
  const deletedFiles = ref<KnowledgeFile[]>([])

  /** 正在仿真推进的文件 id -> timer，避免重复启动 */
  const processingTimers = new Map<string, ReturnType<typeof setInterval>>()

  async function load() {
    loading.value = true
    try {
      files.value = getFiles(kbId)
      folders.value = getFolders(kbId)
    } finally {
      loading.value = false
    }
  }

  function refresh() {
    return load()
  }

  function remove(id: string) {
    stopProcessing(id)
    deleteFile(kbId, id)
    files.value = getFiles(kbId)
  }

  function bulkDelete() {
    selectedFiles.value.forEach((f) => stopProcessing(f.id))
    selectedFiles.value.forEach((f) => deleteFile(kbId, f.id))
    files.value = getFiles(kbId)
    selectedFiles.value = []
  }

  function rename(id: string, newName: string) {
    updateFile(kbId, id, { name: newName })
    files.value = getFiles(kbId)
  }

  function move(id: string, targetFolderId: string) {
    updateFile(kbId, id, { folderId: targetFolderId })
    files.value = getFiles(kbId)
  }

  function bulkMove(targetFolderId: string) {
    selectedFiles.value.forEach((f) => updateFile(kbId, f.id, { folderId: targetFolderId }))
    files.value = getFiles(kbId)
    selectedFiles.value = []
  }

  function copy(id: string) {
    const copied = copyFile(kbId, id)
    files.value = getFiles(kbId)
    // 副本也走处理流程
    if (copied) startProcessing(copied.id)
  }

  /** 重试：重置为 pending 并重新启动仿真 */
  function retry(id: string) {
    updateFile(kbId, id, { status: 'pending', progress: 0, stage: undefined })
    files.value = getFiles(kbId)
    startProcessing(id)
  }

  /** 上传：批量新增文件并启动各自的处理仿真 */
  function upload(
    metas: Array<{
      name: string
      category: KnowledgeFile['category']
      extension: string
      size: number
      parseStrategyId?: string
      parseStrategyName?: string
    }>,
  ) {
    metas.forEach((meta) => {
      const file = addFile(kbId, meta)
      startProcessing(file.id)
    })
    files.value = getFiles(kbId)
  }

  /** 修改解析策略 */
  function changeStrategy(id: string, strategyId: string, strategyName: string) {
    updateFile(kbId, id, { parseStrategyId: strategyId, parseStrategyName: strategyName })
    files.value = getFiles(kbId)
  }

  /** 新建文件夹 */
  function createFolder(name: string, parentId: string = 'root') {
    mockCreateFolder(kbId, name, parentId)
    folders.value = getFolders(kbId)
  }

  /** 获取文件夹名 */
  function getFolderName(folderId: string): string {
    return findFolderName(kbId, folderId)
  }

  // --- 回收站 ---
  function loadDeletedFiles() {
    deletedFiles.value = getDeletedFiles(kbId)
  }

  function restore(id: string) {
    mockRestoreFile(kbId, id)
    deletedFiles.value = getDeletedFiles(kbId)
    files.value = getFiles(kbId)
  }

  function permanentDelete(id: string) {
    permanentlyDeleteFile(kbId, id)
    deletedFiles.value = getDeletedFiles(kbId)
  }

  function emptyBin() {
    emptyRecycleBin(kbId)
    deletedFiles.value = []
  }

  // --- 处理进度仿真 ---
  function startProcessing(fileId: string) {
    if (processingTimers.has(fileId)) return
    const timer = setInterval(() => {
      const file = files.value.find((f) => f.id === fileId)
      if (!file) {
        stopProcessing(fileId)
        return
      }
      const patch = advanceProcessing(file)
      if (Object.keys(patch).length === 0) {
        stopProcessing(fileId)
        return
      }
      updateFile(kbId, fileId, patch)
      files.value = getFiles(kbId)
      if (patch.status === 'completed' || patch.status === 'failed') {
        stopProcessing(fileId)
      }
    }, 1500)
    processingTimers.set(fileId, timer)
  }

  function stopProcessing(fileId: string) {
    const timer = processingTimers.get(fileId)
    if (timer) {
      clearInterval(timer)
      processingTimers.delete(fileId)
    }
  }

  function stopAllProcessing() {
    processingTimers.forEach((t) => clearInterval(t))
    processingTimers.clear()
  }

  return {
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
    upload,
    changeStrategy,
    createFolder,
    getFolderName,
    loadDeletedFiles,
    restore,
    permanentDelete,
    emptyBin,
    stopAllProcessing,
  }
}
