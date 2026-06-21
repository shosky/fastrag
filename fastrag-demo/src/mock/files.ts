import type { KnowledgeFile } from '@/types/knowledge'
import { addOperationLog } from '@/mock/kb-logs'

// ===========================================================================
// 文件管理数据层 —— 全局唯一数据源
// 取代 FileManager.vue 里的硬编码 files ref 和 MoveFileDialog 里的硬编码文件夹树。
// 所有文件操作（CRUD/批量/重试/移动/复制）都走本文件，保证状态一致。
// 变更记录统一写入 knowledge-update.ts 的 logStore。
// ===========================================================================

export interface FolderNode {
  id: string
  label: string
  children?: FolderNode[]
}

/** 文件夹树（迁移自 MoveFileDialog 硬编码） */
const mockFolders: Record<string, FolderNode[]> = {
  default: [
    {
      id: 'root',
      label: '根目录',
      children: [
        {
          id: '1',
          label: '产品文档',
          children: [
            { id: '1-1', label: '用户手册' },
            { id: '1-2', label: 'API文档' },
          ],
        },
        {
          id: '2',
          label: '技术文档',
          children: [
            { id: '2-1', label: '架构设计' },
            { id: '2-2', label: '开发规范' },
          ],
        },
        { id: '3', label: '客户案例' },
        { id: '4', label: '培训资料' },
      ],
    },
  ],
}

/** 默认知识库的文件种子（迁移自 FileManager.vue） */
function seedFiles(): KnowledgeFile[] {
  const now = new Date().toLocaleString('zh-CN')
  return [
    {
      id: '1',
      name: 'AIS 平台用户手册.pdf',
      category: 'document',
      extension: '.pdf',
      size: 2456789,
      url: '',
      status: 'completed',
      progress: 100,
      stage: '存储',
      pages: 45,
      parseStrategyId: '3',
      parseStrategyName: 'PDF解析方法',
      chunkCount: 45,
      folderId: '1-1',
      createdAt: '2026/01/10 09:30',
      updatedAt: '2026/01/10 09:35',
    },
    {
      id: '2',
      name: '产品演示视频.mp4',
      category: 'video',
      extension: '.mp4',
      size: 15678912,
      url: '',
      status: 'completed',
      progress: 100,
      stage: '存储',
      duration: 765,
      parseStrategyId: '4',
      parseStrategyName: '视频解析方法',
      chunkCount: 128,
      folderId: '4',
      createdAt: '2026/01/12 14:20',
      updatedAt: '2026/01/12 14:45',
    },
    {
      id: '3',
      name: '会议录音.mp3',
      category: 'audio',
      extension: '.mp3',
      size: 3567890,
      url: '',
      status: 'completed',
      progress: 100,
      stage: '存储',
      duration: 332,
      parseStrategyId: '5',
      parseStrategyName: '音频解析方法',
      chunkCount: 32,
      folderId: '4',
      createdAt: '2026/01/13 10:15',
      updatedAt: '2026/01/13 10:22',
    },
    {
      id: '4',
      name: '架构图.png',
      category: 'image',
      extension: '.png',
      size: 567890,
      url: '',
      status: 'completed',
      progress: 100,
      stage: '存储',
      parseStrategyId: '1',
      parseStrategyName: '默认解析方法',
      chunkCount: 1,
      folderId: '2-1',
      createdAt: '2026/01/14 11:00',
      updatedAt: '2026/01/14 11:02',
    },
    {
      id: '5',
      name: '需求文档.docx',
      category: 'document',
      extension: '.docx',
      size: 1234567,
      url: '',
      status: 'failed',
      progress: 30,
      stage: '分块',
      parseStrategyId: '1',
      parseStrategyName: '默认解析方法',
      folderId: '1-1',
      createdAt: '2026/01/15 16:30',
      updatedAt: '2026/01/15 16:32',
    },
  ].map((f) => ({ ...f, createdAt: f.createdAt, updatedAt: f.updatedAt })) as KnowledgeFile[]
}

/** 按 kbId 存放的文件列表 */
const fileStore: Record<string, KnowledgeFile[]> = {
  default: seedFiles(),
}

let fileSeq = 1000

function getStore(kbId: string): KnowledgeFile[] {
  if (!fileStore[kbId]) fileStore[kbId] = seedFiles()
  return fileStore[kbId]
}

function now(): string {
  return new Date().toLocaleString('zh-CN')
}

function genId(): string {
  return `file_${(++fileSeq).toString(36)}${Date.now().toString(36).slice(-4)}`
}

/** 获取文件列表（不含已删除的文件） */
export function getFiles(kbId: string): KnowledgeFile[] {
  return getStore(kbId).filter((f) => !f.deletedAt).map((f) => ({ ...f }))
}

/** 获取回收站文件列表（只有已删除的文件） */
export function getDeletedFiles(kbId: string): KnowledgeFile[] {
  return getStore(kbId).filter((f) => !!f.deletedAt).map((f) => ({ ...f }))
}

/** 获取文件夹树 */
export function getFolders(kbId: string): FolderNode[] {
  return (mockFolders[kbId] || mockFolders.default).map((f) => ({ ...f }))
}

/** 查找文件夹名称（递归） */
export function findFolderName(kbId: string, folderId: string): string {
  function search(nodes: FolderNode[]): string | null {
    for (const n of nodes) {
      if (n.id === folderId) return n.label
      if (n.children) {
        const r = search(n.children)
        if (r) return r
      }
    }
    return null
  }
  return search(mockFolders[kbId] || mockFolders.default) || '根目录'
}

/** 新建文件夹 */
export function createFolder(kbId: string, name: string, parentId: string = 'root'): void {
  const tree = mockFolders[kbId] || mockFolders.default
  const newFolder: FolderNode = { id: `folder_${++fileSeq}`, label: name }

  function addTo(nodes: FolderNode[]): boolean {
    for (const n of nodes) {
      if (n.id === parentId) {
        n.children = n.children || []
        n.children.push(newFolder)
        return true
      }
      if (n.children && addTo(n.children)) return true
    }
    return false
  }
  // 若 parentId 未找到，加到 root 顶层
  if (!addTo(tree)) {
    tree.push(newFolder)
  }
}

/** 新增文件（上传后调用），返回创建的文件 */
export function addFile(
  kbId: string,
  meta: {
    name: string
    category: KnowledgeFile['category']
    extension: string
    size: number
    parseStrategyId?: string
    parseStrategyName?: string
  },
): KnowledgeFile {
  const store = getStore(kbId)
  const file: KnowledgeFile = {
    id: genId(),
    name: meta.name,
    category: meta.category,
    extension: meta.extension,
    size: meta.size,
    url: '',
    status: 'pending',
    progress: 0,
    parseStrategyId: meta.parseStrategyId,
    parseStrategyName: meta.parseStrategyName,
    folderId: 'root',
    createdAt: now(),
    updatedAt: now(),
  }
  store.unshift(file)
  addOperationLog(kbId, 'file_added', file.name, `上传文件「${file.name}」`)
  return file
}

/** 更新文件（局部字段） */
export function updateFile(kbId: string, fileId: string, patch: Partial<KnowledgeFile>): KnowledgeFile | null {
  const store = getStore(kbId)
  const idx = store.findIndex((f) => f.id === fileId)
  if (idx === -1) return null
  const old = store[idx]
  store[idx] = { ...old, ...patch, updatedAt: now() }
  // 审计：区分重命名、移动、一般更新
  if (patch.name && patch.name !== old.name) {
    addOperationLog(kbId, 'file_updated', patch.name, `重命名：「${old.name}」→「${patch.name}」`, { oldValue: old.name, newValue: patch.name })
  } else if (patch.folderId && patch.folderId !== old.folderId) {
    addOperationLog(kbId, 'file_updated', old.name, `移动文件「${old.name}」`)
  } else {
    addOperationLog(kbId, 'file_updated', old.name, `更新文件「${old.name}」的属性`)
  }
  return { ...store[idx] }
}

/** 删除文件（软删除：移入回收站） */
export function deleteFile(kbId: string, fileId: string): void {
  const store = getStore(kbId)
  const file = store.find((f) => f.id === fileId)
  if (file) {
    file.deletedAt = now()
    addOperationLog(kbId, 'file_removed', file.name, `删除文件「${file.name}」，移入回收站`)
  }
}

/** 从回收站恢复文件 */
export function restoreFile(kbId: string, fileId: string): boolean {
  const store = getStore(kbId)
  const file = store.find((f) => f.id === fileId && !!f.deletedAt)
  if (!file) return false
  file.deletedAt = undefined
  file.updatedAt = now()
  addOperationLog(kbId, 'file_added', file.name, `从回收站恢复文件「${file.name}」`)
  return true
}

/** 彻底删除文件（不可恢复） */
export function permanentlyDeleteFile(kbId: string, fileId: string): void {
  const store = getStore(kbId)
  const idx = store.findIndex((f) => f.id === fileId && !!f.deletedAt)
  if (idx !== -1) store.splice(idx, 1)
}

/** 清空回收站 */
export function emptyRecycleBin(kbId: string): void {
  const store = getStore(kbId)
  for (let i = store.length - 1; i >= 0; i--) {
    if (store[i].deletedAt) store.splice(i, 1)
  }
}

/** 复制文件（新文件从 pending 开始，不复制 chunkCount） */
export function copyFile(kbId: string, fileId: string): KnowledgeFile | null {
  const store = getStore(kbId)
  const src = store.find((f) => f.id === fileId)
  if (!src) return null
  const copy: KnowledgeFile = {
    ...src,
    id: genId(),
    name: `${src.name.replace(src.extension, '')} (副本)${src.extension}`,
    status: 'pending',
    progress: 0,
    stage: undefined,
    chunkCount: undefined,
    createdAt: now(),
    updatedAt: now(),
  }
  store.unshift(copy)
  addOperationLog(kbId, 'file_added', copy.name, `复制文件「${src.name}」→「${copy.name}」`)
  return copy
}

// ===========================================================================
// 处理进度仿真：按 category 走 stageSteps 推进 status/progress/stage
// ===========================================================================

/** 各类别的处理阶段（与 ProcessStatusBar 的 stageSteps 对齐） */
export const PROCESS_STAGES: Record<string, string[]> = {
  document: ['文本提取', '分块', 'Embedding', '存储'],
  image: ['OCR识别', '描述生成', 'Embedding', '存储'],
  audio: ['ASR转写', '文本清理', '分块', 'Embedding', '存储'],
  video: ['关键帧提取', '帧理解', 'ASR转写', '合并', '分块', 'Embedding', '存储'],
}

/**
 * 推进一步处理进度。返回更新后的部分字段（供 composable patch 回 store）。
 * 到达最后阶段并 progress=100 时返回 status='completed'。
 */
export function advanceProcessing(file: KnowledgeFile): Partial<KnowledgeFile> {
  const stages = PROCESS_STAGES[file.category] || PROCESS_STAGES.document
  let { progress, stage } = file

  // pending -> processing，进入第一阶段
  if (file.status === 'pending') {
    return { status: 'processing', progress: 5, stage: stages[0] }
  }

  // 已完成/失败不推进
  if (file.status !== 'processing') return {}

  // 每步推进的进度增量（按阶段数均分）
  const stageIdx = stages.findIndex((s) => stage && (s === stage || s.includes(stage) || stage.includes(s)))
  const perStage = Math.floor(95 / stages.length)
  progress = Math.min(100, progress + perStage + Math.floor(Math.random() * 5))

  // 进入下一阶段
  const nextStageIdx = Math.min(stages.length - 1, Math.floor((progress / 100) * stages.length))
  stage = stages[nextStageIdx]

  if (progress >= 100) {
    return {
      status: 'completed',
      progress: 100,
      stage: stages[stages.length - 1],
      chunkCount: Math.floor(Math.random() * 50) + 10,
    }
  }
  return { progress, stage }
}
