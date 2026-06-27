import { checkApiPermission } from './interceptor'

// ===========================================================================
// 类型定义
// ===========================================================================

export interface SearchResult {
  id: string
  title: string
  content: string
  summary: string
  highlights: string[]
  score: number
  source: string
  category: string
  tags: string[]
  type: 'document' | 'qa' | 'table' | 'image' | 'audio' | 'video'
  createdAt: string
  updatedAt: string
}

export interface SearchSuggestion {
  type: 'content' | 'rule' | 'keyword' | 'attachment' | 'time' | 'knowledgeType'
  text: string
  highlight: string
}

export interface SearchHistory {
  id: string
  userId: string
  keyword: string
  resultCount: number
  clickedResults: string[]
  searchedAt: string
}

export interface SearchPreference {
  id: string
  userId: string
  defaultSort: 'relevance' | 'time' | 'popularity'
  defaultScope: string[]
  resultCount: number
  highlightMode: 'full' | 'summary'
  language: string
  customFilters: { name: string; value: string }[]
}

export interface SearchLog {
  id: string
  userId: string
  username: string
  keyword: string
  resultCount: number
  clickedResults: string[]
  searchTime: number
  source: string
  timestamp: string
}

export interface KnowledgePush {
  id: string
  name: string
  targetType: 'user' | 'role' | 'department'
  targetIds: string[]
  targetNames: string[]
  knowledgeIds: string[]
  knowledgeTitles: string[]
  triggerCondition: string
  status: 'active' | 'paused'
  pushCount: number
  createdAt: string
  updatedAt: string
}

export interface MultimodalSearchConfig {
  id: string
  name: string
  modality: 'document' | 'image' | 'audio' | 'video'
  enabled: boolean
  model: string
  threshold: number
  maxResults: number
  description: string
}

export interface TagSearchNode {
  id: string
  name: string
  parentId?: string
  children?: TagSearchNode[]
  knowledgeCount: number
}

export interface ModelTrainingTask {
  id: string
  name: string
  modelType: 'embedding' | 'rerank' | 'correction'
  status: 'pending' | 'training' | 'completed' | 'failed'
  progress: number
  dataset: string
  metrics?: { accuracy: number; recall: number; f1: number }
  createdAt: string
  completedAt?: string
}

export interface QueryRewriteRule {
  id: string
  name: string
  type: 'rewrite' | 'expand'
  sourcePattern: string
  targetPattern: string
  enabled: boolean
  hitCount: number
  createdAt: string
}

export interface RetrievalStrategy {
  id: string
  name: string
  description: string
  mode: 'vector' | 'fulltext' | 'hybrid'
  topK: number
  scoreThreshold: number
  rerankEnabled: boolean
  rerankModel?: string
  isDefault: boolean
  createdAt: string
}

// ===========================================================================
// 常量
// ===========================================================================

export const SEARCH_TYPE_OPTIONS = [
  { label: '文档', value: 'document' },
  { label: '问答', value: 'qa' },
  { label: '表格', value: 'table' },
  { label: '图片', value: 'image' },
  { label: '音频', value: 'audio' },
  { label: '视频', value: 'video' },
]

export const SEARCH_TYPE_LABELS: Record<string, string> = {
  document: '文档', qa: '问答', table: '表格', image: '图片', audio: '音频', video: '视频',
}

export const PUSH_STATUS_LABELS: Record<string, string> = {
  active: '启用', paused: '暂停',
}

export const PUSH_STATUS_COLORS: Record<string, string> = {
  active: 'success', paused: 'info',
}

export const TRAINING_STATUS_LABELS: Record<string, string> = {
  pending: '等待中', training: '训练中', completed: '已完成', failed: '失败',
}

export const TRAINING_STATUS_COLORS: Record<string, string> = {
  pending: 'info', training: 'warning', completed: 'success', failed: 'danger',
}

export const MODALITY_OPTIONS = [
  { label: '文档', value: 'document' },
  { label: '图片', value: 'image' },
  { label: '音频', value: 'audio' },
  { label: '视频', value: 'video' },
]

// ===========================================================================
// 内存存储
// ===========================================================================

let searchResultStore: SearchResult[] = []
let historyStore: SearchHistory[] = []
let preferenceStore: SearchPreference[] = []
let logStore: SearchLog[] = []
let pushStore: KnowledgePush[] = []
let multimodalStore: MultimodalSearchConfig[] = []
let tagStore: TagSearchNode[] = []
let trainingStore: ModelTrainingTask[] = []
let rewriteStore: QueryRewriteRule[] = []
let strategyStore: RetrievalStrategy[] = []
let pushSeq = 100
let logSeq = 100
let trainSeq = 100
let rewriteSeq = 100
let strategySeq = 100

function initStore() {
  if (searchResultStore.length > 0) return
  const now = new Date().toISOString()

  searchResultStore = Array.from({ length: 15 }, (_, i) => ({
    id: `sr-${i + 1}`,
    title: `知识文档标题${i + 1}`,
    content: `这是知识文档${i + 1}的完整内容，包含了详细的信息和说明。`,
    summary: `这是知识文档${i + 1}的摘要，概括了文档的主要内容。`,
    highlights: [`高亮片段${i + 1}A`, `高亮片段${i + 1}B`],
    score: Math.round((0.95 - i * 0.03) * 100) / 100,
    source: ['文档库', 'FAQ库', '知识图谱'][i % 3],
    category: ['产品说明', '使用指南', '常见问题', '技术文档'][i % 4],
    tags: [`标签${(i % 5) + 1}`, `标签${(i % 3) + 6}`],
    type: (['document', 'qa', 'table', 'image', 'audio', 'video'] as const)[i % 6],
    createdAt: now,
    updatedAt: now,
  }))

  historyStore = Array.from({ length: 10 }, (_, i) => ({
    id: `hist-${i + 1}`,
    userId: '1',
    keyword: `搜索关键词${i + 1}`,
    resultCount: Math.floor(Math.random() * 50) + 1,
    clickedResults: [`sr-${(i % 5) + 1}`],
    searchedAt: new Date(Date.now() - i * 3600000).toISOString(),
  }))

  preferenceStore = [
    {
      id: 'pref-1', userId: '1', defaultSort: 'relevance', defaultScope: ['全部'],
      resultCount: 20, highlightMode: 'summary', language: 'zh-CN',
      customFilters: [{ name: '知识类型', value: '全部' }],
    },
  ]

  logStore = Array.from({ length: 20 }, (_, i) => ({
    id: `log-${i + 1}`,
    userId: `user-${(i % 5) + 1}`,
    username: `用户${(i % 5) + 1}`,
    keyword: `搜索词${i + 1}`,
    resultCount: Math.floor(Math.random() * 100),
    clickedResults: i % 3 === 0 ? [`sr-${(i % 5) + 1}`] : [],
    searchTime: Math.floor(Math.random() * 500) + 50,
    source: ['在线客服', '智能机器人', '知识门户'][i % 3],
    timestamp: new Date(Date.now() - i * 1800000).toISOString(),
  }))

  pushStore = Array.from({ length: 6 }, (_, i) => ({
    id: `push-${++pushSeq}`,
    name: `推送规则${i + 1}`,
    targetType: (['user', 'role', 'department'] as const)[i % 3],
    targetIds: [`target-${i + 1}`],
    targetNames: [`目标${i + 1}`],
    knowledgeIds: [`kb-${i + 1}`, `kb-${i + 2}`],
    knowledgeTitles: [`知识${i + 1}`, `知识${i + 2}`],
    triggerCondition: ['新知识发布', '知识更新', '用户搜索'][i % 3],
    status: i % 3 === 0 ? 'paused' : 'active',
    pushCount: Math.floor(Math.random() * 500),
    createdAt: now,
    updatedAt: now,
  }))

  multimodalStore = [
    { id: 'mm-1', name: '文档检索', modality: 'document', enabled: true, model: 'text-embedding-3', threshold: 0.7, maxResults: 20, description: '基于文本嵌入的文档语义检索' },
    { id: 'mm-2', name: '图片检索', modality: 'image', enabled: true, model: 'clip-vit', threshold: 0.6, maxResults: 10, description: '基于CLIP的图片语义检索' },
    { id: 'mm-3', name: '音频检索', modality: 'audio', enabled: false, model: 'whisper-v3', threshold: 0.5, maxResults: 10, description: '基于语音转文字的音频内容检索' },
    { id: 'mm-4', name: '视频检索', modality: 'video', enabled: false, model: 'video-clip', threshold: 0.5, maxResults: 5, description: '基于视频理解的多模态检索' },
  ]

  tagStore = [
    { id: 'tag-1', name: '产品', knowledgeCount: 45, children: [
      { id: 'tag-1-1', name: '功能说明', parentId: 'tag-1', knowledgeCount: 20 },
      { id: 'tag-1-2', name: '版本更新', parentId: 'tag-1', knowledgeCount: 15 },
      { id: 'tag-1-3', name: '产品对比', parentId: 'tag-1', knowledgeCount: 10 },
    ]},
    { id: 'tag-2', name: '技术', knowledgeCount: 38, children: [
      { id: 'tag-2-1', name: 'API文档', parentId: 'tag-2', knowledgeCount: 18 },
      { id: 'tag-2-2', name: '部署指南', parentId: 'tag-2', knowledgeCount: 12 },
      { id: 'tag-2-3', name: '故障排查', parentId: 'tag-2', knowledgeCount: 8 },
    ]},
    { id: 'tag-3', name: '业务', knowledgeCount: 52, children: [
      { id: 'tag-3-1', name: '流程规范', parentId: 'tag-3', knowledgeCount: 25 },
      { id: 'tag-3-2', name: '培训资料', parentId: 'tag-3', knowledgeCount: 15 },
      { id: 'tag-3-3', name: '案例分享', parentId: 'tag-3', knowledgeCount: 12 },
    ]},
  ]

  trainingStore = Array.from({ length: 5 }, (_, i) => ({
    id: `train-${++trainSeq}`,
    name: `训练任务${i + 1}`,
    modelType: (['embedding', 'rerank', 'correction'] as const)[i % 3],
    status: (['pending', 'training', 'completed', 'failed'] as const)[i % 4],
    progress: i % 4 === 2 ? 100 : i % 4 === 1 ? Math.floor(Math.random() * 80) + 10 : 0,
    dataset: `数据集${i + 1}`,
    metrics: i % 4 === 2 ? { accuracy: 0.92, recall: 0.88, f1: 0.90 } : undefined,
    createdAt: new Date(Date.now() - i * 86400000).toISOString(),
    completedAt: i % 4 === 2 ? now : undefined,
  }))

  rewriteStore = Array.from({ length: 8 }, (_, i) => ({
    id: `rw-${++rewriteSeq}`,
    name: `${i % 2 === 0 ? '重写' : '扩写'}规则${i + 1}`,
    type: (i % 2 === 0 ? 'rewrite' : 'expand') as 'rewrite' | 'expand',
    sourcePattern: `源模式${i + 1}`,
    targetPattern: `目标模式${i + 1}`,
    enabled: i % 3 !== 0,
    hitCount: Math.floor(Math.random() * 200),
    createdAt: now,
  }))

  strategyStore = [
    { id: 'strat-1', name: '默认向量检索', description: '基于向量相似度的语义检索', mode: 'vector', topK: 10, scoreThreshold: 0.7, rerankEnabled: false, isDefault: true, createdAt: now },
    { id: 'strat-2', name: '全文检索', description: '基于关键词的全文检索', mode: 'fulltext', topK: 20, scoreThreshold: 0.5, rerankEnabled: false, isDefault: false, createdAt: now },
    { id: 'strat-3', name: '混合检索', description: '向量+全文混合检索，带重排序', mode: 'hybrid', topK: 15, scoreThreshold: 0.6, rerankEnabled: true, rerankModel: 'bge-reranker', isDefault: false, createdAt: now },
  ]
}

// ===========================================================================
// 搜索结果
// ===========================================================================

export function search(params?: { keyword?: string; type?: string; category?: string; page?: number; pageSize?: number }) {
  initStore()
  let list = [...searchResultStore]
  if (params?.keyword) list = list.filter(r => r.title.includes(params.keyword!) || r.content.includes(params.keyword!))
  if (params?.type) list = list.filter(r => r.type === params.type)
  if (params?.category) list = list.filter(r => r.category === params.category)
  list.sort((a, b) => b.score - a.score)
  const total = list.length
  const page = params?.page || 1
  const pageSize = params?.pageSize || 10
  return { list: list.slice((page - 1) * pageSize, page * pageSize), total }
}

export function getSuggestions(keyword: string): SearchSuggestion[] {
  initStore()
  if (!keyword) return []
  return [
    { type: 'content', text: `${keyword}相关文档`, highlight: keyword },
    { type: 'keyword', text: `${keyword}的同义词`, highlight: keyword },
    { type: 'knowledgeType', text: `在"技术文档"中搜索${keyword}`, highlight: keyword },
  ]
}

// ===========================================================================
// 搜索历史
// ===========================================================================

export function getSearchHistory(): SearchHistory[] {
  initStore()
  return historyStore
}

export function clearSearchHistory(): void {
  initStore()
  historyStore = []
}

export function addSearchHistory(data: Partial<SearchHistory>): SearchHistory {
  initStore()
  const item: SearchHistory = {
    id: `hist-${Date.now()}`,
    userId: '1',
    keyword: data.keyword || '',
    resultCount: data.resultCount || 0,
    clickedResults: data.clickedResults || [],
    searchedAt: new Date().toISOString(),
  }
  historyStore.unshift(item)
  return item
}

// ===========================================================================
// 检索偏好
// ===========================================================================

export function getSearchPreference(userId: string): SearchPreference | null {
  initStore()
  return preferenceStore.find(p => p.userId === userId) || null
}

export function updateSearchPreference(userId: string, data: Partial<SearchPreference>): SearchPreference {
  checkApiPermission('kb:write')
  initStore()
  const idx = preferenceStore.findIndex(p => p.userId === userId)
  if (idx === -1) {
    const item: SearchPreference = { id: `pref-${Date.now()}`, userId, ...data } as SearchPreference
    preferenceStore.push(item)
    return item
  }
  preferenceStore[idx] = { ...preferenceStore[idx], ...data }
  return preferenceStore[idx]
}

// ===========================================================================
// 检索日志
// ===========================================================================

export function getSearchLogs(params?: { page?: number; pageSize?: number; keyword?: string }) {
  initStore()
  let list = [...logStore]
  if (params?.keyword) list = list.filter(l => l.keyword.includes(params.keyword!))
  const total = list.length
  const page = params?.page || 1
  const pageSize = params?.pageSize || 20
  return { list: list.slice((page - 1) * pageSize, page * pageSize), total }
}

// ===========================================================================
// 知识推送
// ===========================================================================

export function getPushList(params?: { page?: number; pageSize?: number; keyword?: string }) {
  initStore()
  let list = [...pushStore]
  if (params?.keyword) list = list.filter(p => p.name.includes(params.keyword!))
  const total = list.length
  const page = params?.page || 1
  const pageSize = params?.pageSize || 20
  return { list: list.slice((page - 1) * pageSize, page * pageSize), total }
}

export function createPush(data: Partial<KnowledgePush>): KnowledgePush {
  checkApiPermission('kb:write')
  initStore()
  const item: KnowledgePush = {
    id: `push-${++pushSeq}`,
    name: data.name || '',
    targetType: data.targetType || 'user',
    targetIds: data.targetIds || [],
    targetNames: data.targetNames || [],
    knowledgeIds: data.knowledgeIds || [],
    knowledgeTitles: data.knowledgeTitles || [],
    triggerCondition: data.triggerCondition || '',
    status: data.status || 'active',
    pushCount: 0,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  }
  pushStore.push(item)
  return item
}

export function updatePush(id: string, data: Partial<KnowledgePush>): KnowledgePush | null {
  checkApiPermission('kb:write')
  initStore()
  const idx = pushStore.findIndex(p => p.id === id)
  if (idx === -1) return null
  pushStore[idx] = { ...pushStore[idx], ...data, updatedAt: new Date().toISOString() }
  return pushStore[idx]
}

export function deletePush(id: string): boolean {
  checkApiPermission('kb:delete')
  initStore()
  const idx = pushStore.findIndex(p => p.id === id)
  if (idx === -1) return false
  pushStore.splice(idx, 1)
  return true
}

// ===========================================================================
// 多模态检索配置
// ===========================================================================

export function getMultimodalConfigs(): MultimodalSearchConfig[] {
  initStore()
  return multimodalStore
}

export function updateMultimodalConfig(id: string, data: Partial<MultimodalSearchConfig>): MultimodalSearchConfig | null {
  checkApiPermission('kb:write')
  initStore()
  const idx = multimodalStore.findIndex(c => c.id === id)
  if (idx === -1) return null
  multimodalStore[idx] = { ...multimodalStore[idx], ...data }
  return multimodalStore[idx]
}

// ===========================================================================
// 标签检索
// ===========================================================================

export function getTagTree(): TagSearchNode[] {
  initStore()
  return tagStore
}

// ===========================================================================
// 检索模型训练
// ===========================================================================

export function getTrainingList(params?: { page?: number; pageSize?: number }) {
  initStore()
  const total = trainingStore.length
  const page = params?.page || 1
  const pageSize = params?.pageSize || 20
  return { list: trainingStore.slice((page - 1) * pageSize, page * pageSize), total }
}

export function createTrainingTask(data: Partial<ModelTrainingTask>): ModelTrainingTask {
  checkApiPermission('kb:write')
  initStore()
  const item: ModelTrainingTask = {
    id: `train-${++trainSeq}`,
    name: data.name || '',
    modelType: data.modelType || 'embedding',
    status: 'pending',
    progress: 0,
    dataset: data.dataset || '',
    createdAt: new Date().toISOString(),
  }
  trainingStore.push(item)
  return item
}

export function deleteTrainingTask(id: string): boolean {
  checkApiPermission('kb:delete')
  initStore()
  const idx = trainingStore.findIndex(t => t.id === id)
  if (idx === -1) return false
  trainingStore.splice(idx, 1)
  return true
}

// ===========================================================================
// 查询重写/扩写
// ===========================================================================

export function getRewriteList(params?: { page?: number; pageSize?: number; type?: string }) {
  initStore()
  let list = [...rewriteStore]
  if (params?.type) list = list.filter(r => r.type === params.type)
  const total = list.length
  const page = params?.page || 1
  const pageSize = params?.pageSize || 20
  return { list: list.slice((page - 1) * pageSize, page * pageSize), total }
}

export function createRewrite(data: Partial<QueryRewriteRule>): QueryRewriteRule {
  checkApiPermission('kb:write')
  initStore()
  const item: QueryRewriteRule = {
    id: `rw-${++rewriteSeq}`,
    name: data.name || '',
    type: data.type || 'rewrite',
    sourcePattern: data.sourcePattern || '',
    targetPattern: data.targetPattern || '',
    enabled: data.enabled ?? true,
    hitCount: 0,
    createdAt: new Date().toISOString(),
  }
  rewriteStore.push(item)
  return item
}

export function updateRewrite(id: string, data: Partial<QueryRewriteRule>): QueryRewriteRule | null {
  checkApiPermission('kb:write')
  initStore()
  const idx = rewriteStore.findIndex(r => r.id === id)
  if (idx === -1) return null
  rewriteStore[idx] = { ...rewriteStore[idx], ...data }
  return rewriteStore[idx]
}

export function deleteRewrite(id: string): boolean {
  checkApiPermission('kb:delete')
  initStore()
  const idx = rewriteStore.findIndex(r => r.id === id)
  if (idx === -1) return false
  rewriteStore.splice(idx, 1)
  return true
}

// ===========================================================================
// 检索策略
// ===========================================================================

export function getStrategyList(): RetrievalStrategy[] {
  initStore()
  return strategyStore
}

export function createStrategy(data: Partial<RetrievalStrategy>): RetrievalStrategy {
  checkApiPermission('kb:write')
  initStore()
  const item: RetrievalStrategy = {
    id: `strat-${++strategySeq}`,
    name: data.name || '',
    description: data.description || '',
    mode: data.mode || 'vector',
    topK: data.topK || 10,
    scoreThreshold: data.scoreThreshold || 0.7,
    rerankEnabled: data.rerankEnabled || false,
    rerankModel: data.rerankModel,
    isDefault: false,
    createdAt: new Date().toISOString(),
  }
  strategyStore.push(item)
  return item
}

export function updateStrategy(id: string, data: Partial<RetrievalStrategy>): RetrievalStrategy | null {
  checkApiPermission('kb:write')
  initStore()
  const idx = strategyStore.findIndex(s => s.id === id)
  if (idx === -1) return null
  strategyStore[idx] = { ...strategyStore[idx], ...data }
  return strategyStore[idx]
}

export function deleteStrategy(id: string): boolean {
  checkApiPermission('kb:delete')
  initStore()
  const idx = strategyStore.findIndex(s => s.id === id)
  if (idx === -1) return false
  strategyStore.splice(idx, 1)
  return true
}
