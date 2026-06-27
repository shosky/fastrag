import { checkApiPermission } from './interceptor'

export interface MediaItem {
  id: string; name: string; type: 'image' | 'audio' | 'video' | 'document'
  url: string; size: number; status: 'processing' | 'ready' | 'failed'
  description?: string; tags: string[]; creator: string; createdAt: string
}

export interface KnowledgeChannel {
  id: string; name: string; type: 'api' | 'crawler' | 'import' | 'manual'
  endpoint?: string; schedule?: string; enabled: boolean; lastSyncAt?: string
  itemCount: number; status: 'active' | 'error' | 'paused'; createdAt: string
}

export interface DepartmentSharing {
  id: string; sourceDept: string; targetDept: string; knowledgeBaseId: string
  knowledgeBaseName: string; permission: 'read' | 'write'; status: 'active' | 'paused'; createdAt: string
}

export interface QaExtraction {
  id: string; sourceFile: string; sourceType: 'document' | 'manual'
  question: string; answer: string; confidence: number
  status: 'extracted' | 'confirmed' | 'imported' | 'rejected'; createdAt: string
}

export const MEDIA_TYPE_LABELS: Record<string, string> = { image: '图片', audio: '音频', video: '视频', document: '文档' }
export const MEDIA_TYPE_COLORS: Record<string, string> = { image: 'success', audio: 'warning', video: 'primary', document: 'info' }
export const CHANNEL_TYPE_LABELS: Record<string, string> = { api: 'API接口', crawler: '爬虫', import: '文件导入', manual: '手动录入' }
export const EXTRACTION_STATUS_LABELS: Record<string, string> = { extracted: '已抽取', confirmed: '已确认', imported: '已入库', rejected: '已拒绝' }
export const EXTRACTION_STATUS_COLORS: Record<string, string> = { extracted: 'info', confirmed: 'warning', imported: 'success', rejected: 'danger' }

let mediaStore: MediaItem[] = []
let channelStore: KnowledgeChannel[] = []
let sharingStore: DepartmentSharing[] = []
let extractionStore: QaExtraction[] = []
let seq = 100

function initStore() {
  if (mediaStore.length > 0) return
  const now = new Date().toISOString()

  mediaStore = Array.from({ length: 20 }, (_, i) => ({
    id: `media-${i + 1}`, name: `媒体文件${i + 1}.${['jpg', 'mp3', 'mp4', 'pdf'][i % 4]}`,
    type: (['image', 'audio', 'video', 'document'] as const)[i % 4],
    url: `/uploads/media-${i + 1}`, size: Math.floor(Math.random() * 10000000) + 100000,
    status: (['processing', 'ready', 'failed'] as const)[i % 3],
    description: `媒体文件描述${i + 1}`, tags: [`标签${(i % 5) + 1}`],
    creator: 'admin', createdAt: now,
  }))

  channelStore = Array.from({ length: 5 }, (_, i) => ({
    id: `ch-${i + 1}`, name: `知识渠道${i + 1}`,
    type: (['api', 'crawler', 'import', 'manual'] as const)[i % 4],
    endpoint: i % 4 < 2 ? `https://source.example.com/channel${i + 1}` : undefined,
    schedule: i % 4 < 2 ? '每天 02:00' : undefined,
    enabled: i % 3 !== 0, lastSyncAt: i % 3 !== 0 ? now : undefined,
    itemCount: Math.floor(Math.random() * 500) + 10,
    status: i % 3 === 2 ? 'error' : i % 3 === 0 ? 'paused' : 'active', createdAt: now,
  }))

  sharingStore = Array.from({ length: 6 }, (_, i) => ({
    id: `ds-${i + 1}`, sourceDept: ['技术部', '产品部', '运营部'][i % 3],
    targetDept: ['客服部', '销售部', '市场部'][i % 3],
    knowledgeBaseId: `kb-${i + 1}`, knowledgeBaseName: `知识库${i + 1}`,
    permission: i % 2 === 0 ? 'read' : 'write', status: i % 3 === 0 ? 'paused' : 'active', createdAt: now,
  }))

  extractionStore = Array.from({ length: 15 }, (_, i) => ({
    id: `qe-${++seq}`, sourceFile: `文档${(i % 5) + 1}.pdf`,
    sourceType: i % 3 === 0 ? 'manual' : 'document',
    question: `抽取的问题${i + 1}？`, answer: `抽取的答案${i + 1}`,
    confidence: Math.round((0.6 + Math.random() * 0.35) * 100) / 100,
    status: (['extracted', 'confirmed', 'imported', 'rejected'] as const)[i % 4], createdAt: now,
  }))
}

export function getMediaList(params?: { page?: number; pageSize?: number; type?: string; keyword?: string }) {
  initStore(); let list = [...mediaStore]
  if (params?.type) list = list.filter(m => m.type === params.type)
  if (params?.keyword) list = list.filter(m => m.name.includes(params.keyword!))
  const total = list.length; const page = params?.page || 1; const pageSize = params?.pageSize || 20
  return { list: list.slice((page - 1) * pageSize, page * pageSize), total }
}
export function createMedia(data: Partial<MediaItem>): MediaItem {
  checkApiPermission('kb:write'); initStore()
  const item: MediaItem = { id: `media-${++seq}`, name: data.name || '', type: data.type || 'image', url: data.url || '', size: data.size || 0, status: 'processing', description: data.description, tags: data.tags || [], creator: 'admin', createdAt: new Date().toISOString() }
  mediaStore.push(item); return item
}
export function deleteMedia(id: string): boolean {
  checkApiPermission('kb:delete'); initStore()
  const idx = mediaStore.findIndex(m => m.id === id); if (idx === -1) return false
  mediaStore.splice(idx, 1); return true
}

export function getChannelList(): KnowledgeChannel[] { initStore(); return channelStore }
export function createChannel(data: Partial<KnowledgeChannel>): KnowledgeChannel {
  checkApiPermission('kb:write'); initStore()
  const item: KnowledgeChannel = { id: `ch-${++seq}`, name: data.name || '', type: data.type || 'manual', endpoint: data.endpoint, schedule: data.schedule, enabled: true, itemCount: 0, status: 'active', createdAt: new Date().toISOString() }
  channelStore.push(item); return item
}
export function updateChannel(id: string, data: Partial<KnowledgeChannel>): KnowledgeChannel | null {
  checkApiPermission('kb:write'); initStore()
  const idx = channelStore.findIndex(c => c.id === id); if (idx === -1) return null
  channelStore[idx] = { ...channelStore[idx], ...data }; return channelStore[idx]
}
export function deleteChannel(id: string): boolean {
  checkApiPermission('kb:delete'); initStore()
  const idx = channelStore.findIndex(c => c.id === id); if (idx === -1) return false
  channelStore.splice(idx, 1); return true
}

export function getSharingList(): DepartmentSharing[] { initStore(); return sharingStore }
export function createSharing(data: Partial<DepartmentSharing>): DepartmentSharing {
  checkApiPermission('kb:write'); initStore()
  const item: DepartmentSharing = { id: `ds-${++seq}`, sourceDept: data.sourceDept || '', targetDept: data.targetDept || '', knowledgeBaseId: data.knowledgeBaseId || '', knowledgeBaseName: data.knowledgeBaseName || '', permission: data.permission || 'read', status: 'active', createdAt: new Date().toISOString() }
  sharingStore.push(item); return item
}
export function updateSharing(id: string, data: Partial<DepartmentSharing>): DepartmentSharing | null {
  checkApiPermission('kb:write'); initStore()
  const idx = sharingStore.findIndex(s => s.id === id); if (idx === -1) return null
  sharingStore[idx] = { ...sharingStore[idx], ...data }; return sharingStore[idx]
}
export function deleteSharing(id: string): boolean {
  checkApiPermission('kb:delete'); initStore()
  const idx = sharingStore.findIndex(s => s.id === id); if (idx === -1) return false
  sharingStore.splice(idx, 1); return true
}

export function getExtractionList(params?: { page?: number; pageSize?: number; status?: string }) {
  initStore(); let list = [...extractionStore]
  if (params?.status) list = list.filter(e => e.status === params.status)
  const total = list.length; const page = params?.page || 1; const pageSize = params?.pageSize || 20
  return { list: list.slice((page - 1) * pageSize, page * pageSize), total }
}
export function confirmExtraction(id: string): QaExtraction | null {
  checkApiPermission('kb:write'); initStore()
  const idx = extractionStore.findIndex(e => e.id === id); if (idx === -1) return null
  extractionStore[idx] = { ...extractionStore[idx], status: 'confirmed' }; return extractionStore[idx]
}
export function importExtraction(id: string): QaExtraction | null {
  checkApiPermission('kb:write'); initStore()
  const idx = extractionStore.findIndex(e => e.id === id); if (idx === -1) return null
  extractionStore[idx] = { ...extractionStore[idx], status: 'imported' }; return extractionStore[idx]
}
export function rejectExtraction(id: string): QaExtraction | null {
  checkApiPermission('kb:write'); initStore()
  const idx = extractionStore.findIndex(e => e.id === id); if (idx === -1) return null
  extractionStore[idx] = { ...extractionStore[idx], status: 'rejected' }; return extractionStore[idx]
}
export function deleteExtraction(id: string): boolean {
  checkApiPermission('kb:delete'); initStore()
  const idx = extractionStore.findIndex(e => e.id === id); if (idx === -1) return false
  extractionStore.splice(idx, 1); return true
}
