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
    await api.updateFile(kbId, id, { status: 'pending', progress: 0, stage: undefined })
    await load()
  }

  async function upload(
    metas: Array<{
      name: string
      category: KnowledgeFile['category']
      extension: string
      size: number
      parseStrategyId?: string
      parseStrategyName?: string
    }>,
  ) {
    // 上传文件元数据（实际项目中应配合 FormData 上传文件二进制）
    for (const meta of metas) {
      const formData = new FormData()
      Object.entries(meta).forEach(([key, value]) => {
        if (value !== undefined) formData.append(key, String(value))
      })
      await api.uploadFile(kbId, formData)
    }
    await load()
  }

  async function changeStrategy(id: string, strategyId: string, strategyName: string) {
    await api.updateFile(kbId, id, { parseStrategyId: strategyId, parseStrategyName: strategyName })
    await load()
  }

  async function createFolder(name: string, parentId: string = 'root') {
    await api.createFolderApi(kbId, name, parentId)
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
    getFolderName,
    loadDeletedFiles,
    restore,
    permanentDelete,
    emptyBin,
    stopAllProcessing: () => {}, // 保留接口兼容，真实场景由后端驱动
  }
}
