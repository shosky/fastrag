import { checkApiPermission } from './interceptor'

export interface StorageItem {
  id: string; name: string; type: 'document' | 'image' | 'video' | 'audio'
  url: string; size: number; mimeType: string; folderId?: string
  tags: string[]; creator: string; createdAt: string; updatedAt: string
}

export interface StorageFolder {
  id: string; name: string; parentId?: string; children?: StorageFolder[]
  itemCount: number; createdAt: string
}

export interface KnowledgeGroup {
  id: string; name: string; description: string; knowledgeBaseIds: string[]
  knowledgeBaseNames: string[]; memberCount: number; creator: string; createdAt: string
}

export interface KnowledgeMaintenance {
  id: string; knowledgeBaseId: string; knowledgeBaseName: string
  action: 'reindex' | 'cleanup' | 'optimize' | 'backup'
  status: 'pending' | 'running' | 'completed' | 'failed'
  progress: number; message?: string; createdAt: string; completedAt?: string
}

export interface TagType {
  id: string; name: string; description: string; color: string
  tags: TagItem[]; createdAt: string
}

export interface TagItem {
  id: string; name: string; tagTypeId: string; usageCount: number; createdAt: string
}

export interface Note {
  id: string; title: string; content: string; tags: string[]
  relatedKnowledgeId?: string; creator: string; createdAt: string; updatedAt: string
}

export const STORAGE_TYPE_LABELS: Record<string, string> = { document: '文档', image: '图片', video: '视频', audio: '音频' }
export const STORAGE_TYPE_COLORS: Record<string, string> = { document: 'info', image: 'success', video: 'primary', audio: 'warning' }
export const MAINTENANCE_ACTION_LABELS: Record<string, string> = { reindex: '重建索引', cleanup: '清理垃圾', optimize: '优化存储', backup: '数据备份' }
export const MAINTENANCE_STATUS_LABELS: Record<string, string> = { pending: '等待中', running: '执行中', completed: '已完成', failed: '失败' }
export const MAINTENANCE_STATUS_COLORS: Record<string, string> = { pending: 'info', running: 'warning', completed: 'success', failed: 'danger' }

let storageStore: StorageItem[] = []
let folderStore: StorageFolder[] = []
let groupStore: KnowledgeGroup[] = []
let maintenanceStore: KnowledgeMaintenance[] = []
let tagTypeStore: TagType[] = []
let noteStore: Note[] = []
let seq = 100

function initStore() {
  if (storageStore.length > 0) return
  const now = new Date().toISOString()

  folderStore = [
    { id: 'fld-1', name: '文档', itemCount: 15, createdAt: now },
    { id: 'fld-2', name: '图片', itemCount: 8, createdAt: now },
    { id: 'fld-3', name: '音频', itemCount: 5, createdAt: now },
    { id: 'fld-4', name: '视频', itemCount: 3, createdAt: now },
  ]

  storageStore = Array.from({ length: 20 }, (_, i) => ({
    id: `si-${i + 1}`, name: `存储文件${i + 1}.${['pdf', 'jpg', 'mp4', 'mp3'][i % 4]}`,
    type: (['document', 'image', 'video', 'audio'] as const)[i % 4],
    url: `/storage/file-${i + 1}`, size: Math.floor(Math.random() * 50000000) + 100000,
    mimeType: ['application/pdf', 'image/jpeg', 'video/mp4', 'audio/mpeg'][i % 4],
    folderId: `fld-${(i % 4) + 1}`, tags: [`标签${(i % 5) + 1}`],
    creator: 'admin', createdAt: now, updatedAt: now,
  }))

  groupStore = Array.from({ length: 5 }, (_, i) => ({
    id: `kg-${i + 1}`, name: `知识库分组${i + 1}`, description: `分组描述${i + 1}`,
    knowledgeBaseIds: [`kb-${i * 2 + 1}`, `kb-${i * 2 + 2}`],
    knowledgeBaseNames: [`知识库${i * 2 + 1}`, `知识库${i * 2 + 2}`],
    memberCount: Math.floor(Math.random() * 10) + 2, creator: 'admin', createdAt: now,
  }))

  maintenanceStore = Array.from({ length: 6 }, (_, i) => ({
    id: `mnt-${i + 1}`, knowledgeBaseId: `kb-${i + 1}`, knowledgeBaseName: `知识库${i + 1}`,
    action: (['reindex', 'cleanup', 'optimize', 'backup'] as const)[i % 4],
    status: (['pending', 'running', 'completed', 'failed'] as const)[i % 4],
    progress: i % 4 === 2 ? 100 : i % 4 === 1 ? Math.floor(Math.random() * 80) + 10 : 0,
    message: i % 4 === 3 ? '执行失败：磁盘空间不足' : undefined,
    createdAt: now, completedAt: i % 4 === 2 ? now : undefined,
  }))

  tagTypeStore = [
    { id: 'tt-1', name: '知识分类', description: '知识条目的分类标签', color: '#409EFF',
      tags: [
        { id: 't-1', name: '产品', tagTypeId: 'tt-1', usageCount: 45, createdAt: now },
        { id: 't-2', name: '技术', tagTypeId: 'tt-1', usageCount: 38, createdAt: now },
        { id: 't-3', name: '业务', tagTypeId: 'tt-1', usageCount: 52, createdAt: now },
      ], createdAt: now },
    { id: 'tt-2', name: '优先级', description: '知识条目的优先级标签', color: '#E6A23C',
      tags: [
        { id: 't-4', name: '高', tagTypeId: 'tt-2', usageCount: 20, createdAt: now },
        { id: 't-5', name: '中', tagTypeId: 'tt-2', usageCount: 50, createdAt: now },
        { id: 't-6', name: '低', tagTypeId: 'tt-2', usageCount: 30, createdAt: now },
      ], createdAt: now },
    { id: 'tt-3', name: '来源', description: '知识条目的来源标签', color: '#67C23A',
      tags: [
        { id: 't-7', name: '人工录入', tagTypeId: 'tt-3', usageCount: 60, createdAt: now },
        { id: 't-8', name: 'AI生成', tagTypeId: 'tt-3', usageCount: 25, createdAt: now },
        { id: 't-9', name: '文档导入', tagTypeId: 'tt-3', usageCount: 40, createdAt: now },
      ], createdAt: now },
  ]

  noteStore = Array.from({ length: 10 }, (_, i) => ({
    id: `note-${i + 1}`, title: `笔记标题${i + 1}`, content: `笔记内容${i + 1}，记录了一些重要的知识要点和学习心得。`,
    tags: [`标签${(i % 5) + 1}`], relatedKnowledgeId: i % 3 === 0 ? `kb-${i + 1}` : undefined,
    creator: 'admin', createdAt: now, updatedAt: now,
  }))
}

export function getStorageList(params?: { page?: number; pageSize?: number; type?: string; keyword?: string }) {
  initStore(); let list = [...storageStore]
  if (params?.type) list = list.filter(s => s.type === params.type)
  if (params?.keyword) list = list.filter(s => s.name.includes(params.keyword!))
  const total = list.length; const page = params?.page || 1; const pageSize = params?.pageSize || 20
  return { list: list.slice((page - 1) * pageSize, page * pageSize), total }
}
export function getStorageFolders(): StorageFolder[] { initStore(); return folderStore }
export function createStorage(data: Partial<StorageItem>): StorageItem {
  checkApiPermission('kb:write'); initStore()
  const item: StorageItem = { id: `si-${++seq}`, name: data.name || '', type: data.type || 'document', url: data.url || '', size: data.size || 0, mimeType: data.mimeType || '', folderId: data.folderId, tags: data.tags || [], creator: 'admin', createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() }
  storageStore.push(item); return item
}
export function deleteStorage(id: string): boolean {
  checkApiPermission('kb:delete'); initStore()
  const idx = storageStore.findIndex(s => s.id === id); if (idx === -1) return false
  storageStore.splice(idx, 1); return true
}

export function getGroupList(): KnowledgeGroup[] { initStore(); return groupStore }
export function createGroup(data: Partial<KnowledgeGroup>): KnowledgeGroup {
  checkApiPermission('kb:write'); initStore()
  const item: KnowledgeGroup = { id: `kg-${++seq}`, name: data.name || '', description: data.description || '', knowledgeBaseIds: data.knowledgeBaseIds || [], knowledgeBaseNames: data.knowledgeBaseNames || [], memberCount: 0, creator: 'admin', createdAt: new Date().toISOString() }
  groupStore.push(item); return item
}
export function updateGroup(id: string, data: Partial<KnowledgeGroup>): KnowledgeGroup | null {
  checkApiPermission('kb:write'); initStore()
  const idx = groupStore.findIndex(g => g.id === id); if (idx === -1) return null
  groupStore[idx] = { ...groupStore[idx], ...data }; return groupStore[idx]
}
export function deleteGroup(id: string): boolean {
  checkApiPermission('kb:delete'); initStore()
  const idx = groupStore.findIndex(g => g.id === id); if (idx === -1) return false
  groupStore.splice(idx, 1); return true
}

export function getMaintenanceList(): KnowledgeMaintenance[] { initStore(); return maintenanceStore }
export function createMaintenance(data: Partial<KnowledgeMaintenance>): KnowledgeMaintenance {
  checkApiPermission('kb:write'); initStore()
  const item: KnowledgeMaintenance = { id: `mnt-${++seq}`, knowledgeBaseId: data.knowledgeBaseId || '', knowledgeBaseName: data.knowledgeBaseName || '', action: data.action || 'reindex', status: 'pending', progress: 0, createdAt: new Date().toISOString() }
  maintenanceStore.push(item); return item
}

export function getTagTypeList(): TagType[] { initStore(); return tagTypeStore }
export function createTagType(data: Partial<TagType>): TagType {
  checkApiPermission('kb:write'); initStore()
  const item: TagType = { id: `tt-${++seq}`, name: data.name || '', description: data.description || '', color: data.color || '#409EFF', tags: [], createdAt: new Date().toISOString() }
  tagTypeStore.push(item); return item
}
export function updateTagType(id: string, data: Partial<TagType>): TagType | null {
  checkApiPermission('kb:write'); initStore()
  const idx = tagTypeStore.findIndex(t => t.id === id); if (idx === -1) return null
  tagTypeStore[idx] = { ...tagTypeStore[idx], ...data }; return tagTypeStore[idx]
}
export function deleteTagType(id: string): boolean {
  checkApiPermission('kb:delete'); initStore()
  const idx = tagTypeStore.findIndex(t => t.id === id); if (idx === -1) return false
  tagTypeStore.splice(idx, 1); return true
}
export function createTag(tagTypeId: string, data: Partial<TagItem>): TagItem {
  checkApiPermission('kb:write'); initStore()
  const tt = tagTypeStore.find(t => t.id === tagTypeId); if (!tt) return {} as TagItem
  const item: TagItem = { id: `t-${++seq}`, name: data.name || '', tagTypeId, usageCount: 0, createdAt: new Date().toISOString() }
  tt.tags.push(item); return item
}
export function deleteTag(tagTypeId: string, tagId: string): boolean {
  checkApiPermission('kb:delete'); initStore()
  const tt = tagTypeStore.find(t => t.id === tagTypeId); if (!tt) return false
  const idx = tt.tags.findIndex(t => t.id === tagId); if (idx === -1) return false
  tt.tags.splice(idx, 1); return true
}

export function getNoteList(params?: { page?: number; pageSize?: number; keyword?: string }) {
  initStore(); let list = [...noteStore]
  if (params?.keyword) list = list.filter(n => n.title.includes(params.keyword!))
  const total = list.length; const page = params?.page || 1; const pageSize = params?.pageSize || 20
  return { list: list.slice((page - 1) * pageSize, page * pageSize), total }
}
export function createNote(data: Partial<Note>): Note {
  checkApiPermission('kb:write'); initStore()
  const now = new Date().toISOString()
  const item: Note = { id: `note-${++seq}`, title: data.title || '', content: data.content || '', tags: data.tags || [], relatedKnowledgeId: data.relatedKnowledgeId, creator: 'admin', createdAt: now, updatedAt: now }
  noteStore.push(item); return item
}
export function updateNote(id: string, data: Partial<Note>): Note | null {
  checkApiPermission('kb:write'); initStore()
  const idx = noteStore.findIndex(n => n.id === id); if (idx === -1) return null
  noteStore[idx] = { ...noteStore[idx], ...data, updatedAt: new Date().toISOString() }; return noteStore[idx]
}
export function deleteNote(id: string): boolean {
  checkApiPermission('kb:delete'); initStore()
  const idx = noteStore.findIndex(n => n.id === id); if (idx === -1) return false
  noteStore.splice(idx, 1); return true
}
