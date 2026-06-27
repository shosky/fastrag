import { checkApiPermission } from './interceptor'

export interface UpdateInitiation {
  id: string; knowledgeBaseId: string; knowledgeBaseName: string
  type: 'manual' | 'scheduled' | 'triggered'; reason: string
  status: 'pending' | 'processing' | 'completed' | 'failed'
  progress: number; initiator: string; createdAt: string; completedAt?: string
}

export interface UpdateProcessing {
  id: string; initiationId: string; knowledgeBaseName: string
  action: 'add' | 'update' | 'delete' | 'merge'
  itemCount: number; processedCount: number
  status: 'pending' | 'processing' | 'completed' | 'failed'
  details: string; createdAt: string; completedAt?: string
}

export interface UpdateQuery {
  id: string; knowledgeBaseId: string; knowledgeBaseName: string
  updateType: string; updateTime: string; itemCount: number; operator: string
}

export const UPDATE_STATUS_LABELS: Record<string, string> = { pending: '等待中', processing: '处理中', completed: '已完成', failed: '失败' }
export const UPDATE_STATUS_COLORS: Record<string, string> = { pending: 'info', processing: 'warning', completed: 'success', failed: 'danger' }
export const UPDATE_ACTION_LABELS: Record<string, string> = { add: '新增', update: '更新', delete: '删除', merge: '合并' }

let initiationStore: UpdateInitiation[] = []
let processingStore: UpdateProcessing[] = []
let queryStore: UpdateQuery[] = []
let seq = 100

function initStore() {
  if (initiationStore.length > 0) return
  const now = new Date().toISOString()

  initiationStore = Array.from({ length: 8 }, (_, i) => ({
    id: `ui-${++seq}`, knowledgeBaseId: `kb-${i + 1}`, knowledgeBaseName: `知识库${i + 1}`,
    type: (['manual', 'scheduled', 'triggered'] as const)[i % 3],
    reason: ['手动更新', '定时更新', '新文档上传触发'][i % 3],
    status: (['pending', 'processing', 'completed', 'failed'] as const)[i % 4],
    progress: i % 4 === 2 ? 100 : i % 4 === 1 ? Math.floor(Math.random() * 80) + 10 : 0,
    initiator: 'admin', createdAt: now, completedAt: i % 4 === 2 ? now : undefined,
  }))

  processingStore = Array.from({ length: 10 }, (_, i) => ({
    id: `up-${++seq}`, initiationId: initiationStore[i % initiationStore.length].id,
    knowledgeBaseName: `知识库${(i % 5) + 1}`,
    action: (['add', 'update', 'delete', 'merge'] as const)[i % 4],
    itemCount: Math.floor(Math.random() * 100) + 10,
    processedCount: Math.floor(Math.random() * 80) + 5,
    status: (['pending', 'processing', 'completed', 'failed'] as const)[i % 4],
    details: `处理${['新增', '更新', '删除', '合并'][i % 4]}操作`,
    createdAt: now, completedAt: i % 4 === 2 ? now : undefined,
  }))

  queryStore = Array.from({ length: 12 }, (_, i) => ({
    id: `uq-${i + 1}`, knowledgeBaseId: `kb-${(i % 5) + 1}`, knowledgeBaseName: `知识库${(i % 5) + 1}`,
    updateType: ['手动更新', '定时同步', '增量更新', '全量更新'][i % 4],
    updateTime: new Date(Date.now() - i * 3600000).toISOString(),
    itemCount: Math.floor(Math.random() * 200) + 20, operator: 'admin',
  }))
}

export function getInitiationList(params?: { page?: number; pageSize?: number; status?: string }) {
  initStore(); let list = [...initiationStore]
  if (params?.status) list = list.filter(u => u.status === params.status)
  const total = list.length; const page = params?.page || 1; const pageSize = params?.pageSize || 20
  return { list: list.slice((page - 1) * pageSize, page * pageSize), total }
}
export function createInitiation(data: Partial<UpdateInitiation>): UpdateInitiation {
  checkApiPermission('kb:write'); initStore()
  const item: UpdateInitiation = { id: `ui-${++seq}`, knowledgeBaseId: data.knowledgeBaseId || '', knowledgeBaseName: data.knowledgeBaseName || '', type: data.type || 'manual', reason: data.reason || '', status: 'pending', progress: 0, initiator: 'admin', createdAt: new Date().toISOString() }
  initiationStore.push(item); return item
}

export function getProcessingList(initiationId?: string): UpdateProcessing[] {
  initStore(); if (initiationId) return processingStore.filter(p => p.initiationId === initiationId)
  return processingStore
}

export function getUpdateQueryList(params?: { page?: number; pageSize?: number; keyword?: string }) {
  initStore(); let list = [...queryStore]
  if (params?.keyword) list = list.filter(q => q.knowledgeBaseName.includes(params.keyword!))
  const total = list.length; const page = params?.page || 1; const pageSize = params?.pageSize || 20
  return { list: list.slice((page - 1) * pageSize, page * pageSize), total }
}
