import { ref } from 'vue'
import type { KnowledgeFile } from '@/types/knowledge'
import * as api from '@/api'
import type { FolderNode } from '@/mock/files'

// ===========================================================================
// 文件管理 composable
//
// 负责：加载文件/文件夹、CRUD、批量操作。
// 已切换为真实 API 调用。
// ===========================================================================

export function useFiles(kbId: string = 'default') {
  const files = ref<KnowledgeFile[]>([])
  const folders = ref<FolderNode[]>([])
  const loading = ref(false)
  const selectedFiles = ref<KnowledgeFile[]>([])
  const deletedFiles = ref<KnowledgeFile[]>([])

  async function load() {
    loading.value = true
    try {
      const [fileRes, folderRes] = await Promise.all([
        api.getFiles(kbId),
        api.fetchFolders(kbId),
      ])
      files.value = (fileRes as any)?.list || (fileRes as any) || []
      folders.value = (folderRes as any) || []
    } finally {
      loading.value = false
    }
  }

  function refresh() {
    return load()
  }

  async function remove(id: string) {
    await api.deleteFile(kbId, id)
    await load()
  }

  async function bulkDelete() {
    await Promise.all(selectedFiles.value.map((f) => api.deleteFile(kbId, f.id)))
    selectedFiles.value = []
    await load()
  }

  async function rename(id: string, newName: string) {
    await api.updateFile(kbId, id, { name: newName })
    await load()
  }

  async function move(id: string, targetFolderId: string) {
    await api.updateFile(kbId, id, { folderId: targetFolderId })
    await load()
  }

  async function bulkMove(targetFolderId: string) {
    await Promise.all(selectedFiles.value.map((f) => api.updateFile(kbId, f.id, { folderId: targetFolderId })))
    selectedFiles.value = []
    await load()
  }

  async function copy(id: string) {
    await api.copyFile(kbId, id)
    await load()
  }

  async function retry(id: string) {
    await api.retryFile(kbId, id)
    await load()
  }

  async function toggleGraphBuild(id: string, enabled: boolean) {
    await api.updateFile(kbId, id, { enableGraphBuild: enabled ? 1 : 0 })
    await load()
  }

  async function upload(
    files: File[],
    metas?: Array<{
      name: string
      category: KnowledgeFile['category']
      extension: string
      size: number
      parseStrategyId?: string
      parseStrategyName?: string
    }>,
  ) {
    // 上传文件二进制
    for (let i = 0; i < files.length; i++) {
      const file = files[i]
      const formData = new FormData()
      formData.append('file', file)
      await api.uploadFile(kbId, formData)
    }
    await load()
  }

  async function changeStrategy(id: string, strategyId: string, strategyName: string) {
    await api.updateFile(kbId, id, { parseStrategyId: strategyId, parseStrategyName: strategyName })
    await load()
  }

  async function createFolder(name: string, parentId?: string | null) {
    // 根级文件夹传 null（后端 buildTree 只认 null 为根节点）
    await api.createFolderApi(kbId, name, parentId || null)
    const folderRes = await api.fetchFolders(kbId)
    folders.value = (folderRes as any) || []
  }

  async function renameFolder(folderId: string, newName: string) {
    await api.renameFolderApi(kbId, folderId, newName)
    const folderRes = await api.fetchFolders(kbId)
    folders.value = (folderRes as any) || []
  }

  async function deleteFolder(folderId: string) {
    await api.deleteFolderApi(kbId, folderId)
    const folderRes = await api.fetchFolders(kbId)
    folders.value = (folderRes as any) || []
  }

  async function getFolderName(folderId: string): Promise<string> {
    return api.fetchFolderName(kbId, folderId)
  }

  // --- 回收站 ---
  async function loadDeletedFiles() {
    const res = await api.getDeletedFiles(kbId)
    deletedFiles.value = (res as any)?.list || (res as any) || []
  }

  async function restore(id: string) {
    await api.restoreFile(kbId, id)
    await loadDeletedFiles()
    await load()
  }

  async function permanentDelete(id: string) {
    await api.permanentDeleteFile(kbId, id)
    await loadDeletedFiles()
  }

  async function emptyBin() {
    await api.emptyRecycleBin(kbId)
    deletedFiles.value = []
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
    renameFolder,
    deleteFolder,
    getFolderName,
    loadDeletedFiles,
    restore,
    permanentDelete,
    emptyBin,
    stopAllProcessing: () => {}, // 保留接口兼容，真实场景由后端驱动
  }
}
