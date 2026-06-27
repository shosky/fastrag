import { checkApiPermission } from './interceptor'

export interface ReviewFlow {
  id: string; name: string; description: string; steps: ReviewStep[]
  status: 'active' | 'draft' | 'disabled'; creator: string; createdAt: string
}
export interface ReviewStep { id: string; name: string; reviewerRole: string; order: number; timeoutHours?: number }
export interface ReviewTask {
  id: string; flowId: string; flowName: string; knowledgeId: string; knowledgeTitle: string
  currentStep: string; status: 'pending' | 'approved' | 'rejected' | 'timeout'
  submitter: string; submitTime: string; reviewer?: string; reviewTime?: string; comment?: string
}
export interface PublishRecord {
  id: string; knowledgeId: string; knowledgeTitle: string; version: string
  status: 'online' | 'offline' | 'scheduled'; onlineVersion?: string; offlineVersion?: string
  publishTime?: string; scheduledTime?: string; operator: string; createdAt: string
}
export interface ResetRecord {
  id: string; knowledgeId: string; knowledgeTitle: string; fromVersion: string; toVersion: string
  reason: string; operator: string; createdAt: string
}
export interface ReviewListener {
  id: string; name: string; url: string; events: string[]; enabled: boolean
  status: 'active' | 'error' | 'paused'; lastTriggeredAt?: string; triggerCount: number; createdAt: string
}
export interface ComplianceRule {
  id: string; name: string; description: string; ruleType: 'content' | 'format' | 'keyword' | 'length'
  rule: string; enabled: boolean; hitCount: number; createdAt: string
}
export interface ReviewReport {
  id: string; name: string; period: string; totalReviews: number; approved: number; rejected: number
  timeout: number; avgReviewTime: number; generatedAt: string
}
export interface QualityRule {
  id: string; name: string; dimension: 'completeness' | 'accuracy' | 'freshness' | 'relevance'
  weight: number; rule: string; enabled: boolean; createdAt: string
}
export interface QualityScore {
  id: string; knowledgeId: string; knowledgeTitle: string; scores: Record<string, number>
  totalScore: number; level: 'excellent' | 'good' | 'fair' | 'poor'; evaluatedAt: string
}

export const REVIEW_STATUS_LABELS: Record<string, string> = { pending: '待审核', approved: '已通过', rejected: '已驳回', timeout: '已超时' }
export const REVIEW_STATUS_COLORS: Record<string, string> = { pending: 'warning', approved: 'success', rejected: 'danger', timeout: 'info' }
export const PUBLISH_STATUS_LABELS: Record<string, string> = { online: '已上线', offline: '已下线', scheduled: '定时发布' }
export const PUBLISH_STATUS_COLORS: Record<string, string> = { online: 'success', offline: 'info', scheduled: 'warning' }
export const LISTENER_STATUS_LABELS: Record<string, string> = { active: '运行中', error: '异常', paused: '已暂停' }
export const LISTENER_STATUS_COLORS: Record<string, string> = { active: 'success', error: 'danger', paused: 'info' }
export const QUALITY_LEVEL_LABELS: Record<string, string> = { excellent: '优秀', good: '良好', fair: '一般', poor: '较差' }
export const QUALITY_LEVEL_COLORS: Record<string, string> = { excellent: 'success', good: 'primary', fair: 'warning', poor: 'danger' }

let flowStore: ReviewFlow[] = []
let taskStore: ReviewTask[] = []
let publishStore: PublishRecord[] = []
let resetStore: ResetRecord[] = []
let listenerStore: ReviewListener[] = []
let complianceStore: ComplianceRule[] = []
let reportStore: ReviewReport[] = []
let qualityRuleStore: QualityRule[] = []
let qualityScoreStore: QualityScore[] = []
let seq = 100

function initStore() {
  if (flowStore.length > 0) return
  const now = new Date().toISOString()

  flowStore = [
    { id: 'rf-1', name: '标准审核流程', description: '知识发布的标准审核流程', steps: [
      { id: 'rs-1', name: '初审', reviewerRole: '知识编辑', order: 1, timeoutHours: 24 },
      { id: 'rs-2', name: '复审', reviewerRole: '知识管理员', order: 2, timeoutHours: 48 },
    ], status: 'active', creator: 'admin', createdAt: now },
    { id: 'rf-2', name: '快速审核流程', description: '低风险知识的快速审核', steps: [
      { id: 'rs-3', name: '审核', reviewerRole: '知识管理员', order: 1, timeoutHours: 12 },
    ], status: 'active', creator: 'admin', createdAt: now },
  ]

  taskStore = Array.from({ length: 12 }, (_, i) => ({
    id: `rt-${++seq}`, flowId: `rf-${(i % 2) + 1}`, flowName: ['标准审核流程', '快速审核流程'][i % 2],
    knowledgeId: `kb-${i + 1}`, knowledgeTitle: `知识文档${i + 1}`,
    currentStep: i % 2 === 0 ? '初审' : '审核',
    status: (['pending', 'approved', 'rejected', 'timeout'] as const)[i % 4],
    submitter: 'editor', submitTime: new Date(Date.now() - i * 3600000).toISOString(),
    reviewer: i % 4 !== 0 ? 'admin' : undefined, reviewTime: i % 4 !== 0 ? now : undefined,
    comment: i % 4 === 2 ? '内容需要修改' : undefined,
  }))

  publishStore = Array.from({ length: 8 }, (_, i) => ({
    id: `rp-${i + 1}`, knowledgeId: `kb-${i + 1}`, knowledgeTitle: `知识文档${i + 1}`,
    version: `v${i + 1}.0`, status: (['online', 'offline', 'scheduled'] as const)[i % 3],
    onlineVersion: i % 3 === 0 ? `v${i + 1}.0` : undefined,
    publishTime: i % 3 !== 2 ? now : undefined,
    scheduledTime: i % 3 === 2 ? new Date(Date.now() + 86400000).toISOString() : undefined,
    operator: 'admin', createdAt: now,
  }))

  resetStore = Array.from({ length: 5 }, (_, i) => ({
    id: `rr-${i + 1}`, knowledgeId: `kb-${i + 1}`, knowledgeTitle: `知识文档${i + 1}`,
    fromVersion: `v${i + 2}.0`, toVersion: `v${i + 1}.0`,
    reason: ['内容有误', '需要回滚', '版本冲突', '用户要求', '测试回退'][i],
    operator: 'admin', createdAt: now,
  }))

  listenerStore = [
    { id: 'rl-1', name: '审核通知监听', url: 'https://webhook.example.com/review', events: ['review.approved', 'review.rejected'], enabled: true, status: 'active', lastTriggeredAt: now, triggerCount: 156, createdAt: now },
    { id: 'rl-2', name: '发布通知监听', url: 'https://webhook.example.com/publish', events: ['publish.online', 'publish.offline'], enabled: true, status: 'active', lastTriggeredAt: now, triggerCount: 89, createdAt: now },
    { id: 'rl-3', name: '错误日志监听', url: 'https://webhook.example.com/error', events: ['review.timeout', 'listener.error'], enabled: false, status: 'paused', triggerCount: 12, createdAt: now },
  ]

  complianceStore = [
    { id: 'cr-1', name: '内容完整性检查', description: '检查知识内容是否完整', ruleType: 'content', rule: '内容长度不少于100字', enabled: true, hitCount: 45, createdAt: now },
    { id: 'cr-2', name: '格式规范检查', description: '检查知识格式是否规范', ruleType: 'format', rule: '必须包含标题和正文', enabled: true, hitCount: 32, createdAt: now },
    { id: 'cr-3', name: '敏感词检查', description: '检查是否包含敏感词', ruleType: 'keyword', rule: '不包含违禁词列表中的词汇', enabled: true, hitCount: 8, createdAt: now },
    { id: 'cr-4', name: '长度限制检查', description: '检查内容长度是否在限制内', ruleType: 'length', rule: '内容长度不超过10000字', enabled: true, hitCount: 3, createdAt: now },
  ]

  reportStore = Array.from({ length: 6 }, (_, i) => ({
    id: `rpt-${i + 1}`, name: `${2026}年${i + 1}月审核报告`, period: `2026-0${i + 1}`,
    totalReviews: Math.floor(Math.random() * 200) + 50,
    approved: Math.floor(Math.random() * 150) + 30,
    rejected: Math.floor(Math.random() * 30) + 5,
    timeout: Math.floor(Math.random() * 10),
    avgReviewTime: Math.floor(Math.random() * 24) + 2,
    generatedAt: now,
  }))

  qualityRuleStore = [
    { id: 'qr-1', name: '内容完整性', dimension: 'completeness', weight: 0.3, rule: '检查标题、正文、标签是否完整', enabled: true, createdAt: now },
    { id: 'qr-2', name: '内容准确性', dimension: 'accuracy', weight: 0.3, rule: '检查内容是否存在错误', enabled: true, createdAt: now },
    { id: 'qr-3', name: '内容时效性', dimension: 'freshness', weight: 0.2, rule: '检查内容是否过期', enabled: true, createdAt: now },
    { id: 'qr-4', name: '内容相关性', dimension: 'relevance', weight: 0.2, rule: '检查内容与分类是否匹配', enabled: true, createdAt: now },
  ]

  qualityScoreStore = Array.from({ length: 10 }, (_, i) => ({
    id: `qs-${i + 1}`, knowledgeId: `kb-${i + 1}`, knowledgeTitle: `知识文档${i + 1}`,
    scores: { completeness: Math.round(Math.random() * 30 + 70), accuracy: Math.round(Math.random() * 30 + 70), freshness: Math.round(Math.random() * 30 + 70), relevance: Math.round(Math.random() * 30 + 70) },
    totalScore: Math.round(Math.random() * 30 + 70),
    level: (['excellent', 'good', 'fair', 'poor'] as const)[i % 4],
    evaluatedAt: now,
  }))
}

export function getFlowList(): ReviewFlow[] { initStore(); return flowStore }
export function createFlow(data: Partial<ReviewFlow>): ReviewFlow {
  checkApiPermission('review:write'); initStore()
  const item: ReviewFlow = { id: `rf-${++seq}`, name: data.name || '', description: data.description || '', steps: data.steps || [], status: 'draft', creator: 'admin', createdAt: new Date().toISOString() }
  flowStore.push(item); return item
}
export function updateFlow(id: string, data: Partial<ReviewFlow>): ReviewFlow | null {
  checkApiPermission('review:write'); initStore()
  const idx = flowStore.findIndex(f => f.id === id); if (idx === -1) return null
  flowStore[idx] = { ...flowStore[idx], ...data }; return flowStore[idx]
}
export function deleteFlow(id: string): boolean {
  checkApiPermission('review:delete'); initStore()
  const idx = flowStore.findIndex(f => f.id === id); if (idx === -1) return false
  flowStore.splice(idx, 1); return true
}

export function getReviewTaskList(params?: { page?: number; pageSize?: number; status?: string }) {
  initStore(); let list = [...taskStore]
  if (params?.status) list = list.filter(t => t.status === params.status)
  const total = list.length; const page = params?.page || 1; const pageSize = params?.pageSize || 20
  return { list: list.slice((page - 1) * pageSize, page * pageSize), total }
}
export function approveReviewTask(id: string): ReviewTask | null {
  checkApiPermission('review:approve'); initStore()
  const idx = taskStore.findIndex(t => t.id === id); if (idx === -1) return null
  taskStore[idx] = { ...taskStore[idx], status: 'approved', reviewer: 'admin', reviewTime: new Date().toISOString() }; return taskStore[idx]
}
export function rejectReviewTask(id: string, comment: string): ReviewTask | null {
  checkApiPermission('review:approve'); initStore()
  const idx = taskStore.findIndex(t => t.id === id); if (idx === -1) return null
  taskStore[idx] = { ...taskStore[idx], status: 'rejected', reviewer: 'admin', reviewTime: new Date().toISOString(), comment }; return taskStore[idx]
}

export function getPublishList(params?: { page?: number; pageSize?: number; status?: string }) {
  initStore(); let list = [...publishStore]
  if (params?.status) list = list.filter(p => p.status === params.status)
  const total = list.length; const page = params?.page || 1; const pageSize = params?.pageSize || 20
  return { list: list.slice((page - 1) * pageSize, page * pageSize), total }
}
export function publishKnowledge(id: string): PublishRecord | null {
  checkApiPermission('kb:publish'); initStore()
  const idx = publishStore.findIndex(p => p.id === id); if (idx === -1) return null
  publishStore[idx] = { ...publishStore[idx], status: 'online', publishTime: new Date().toISOString() }; return publishStore[idx]
}
export function offlineKnowledge(id: string): PublishRecord | null {
  checkApiPermission('kb:publish'); initStore()
  const idx = publishStore.findIndex(p => p.id === id); if (idx === -1) return null
  publishStore[idx] = { ...publishStore[idx], status: 'offline' }; return publishStore[idx]
}

export function getResetList(): ResetRecord[] { initStore(); return resetStore }
export function resetKnowledge(data: Partial<ResetRecord>): ResetRecord {
  checkApiPermission('kb:write'); initStore()
  const item: ResetRecord = { id: `rr-${++seq}`, knowledgeId: data.knowledgeId || '', knowledgeTitle: data.knowledgeTitle || '', fromVersion: data.fromVersion || '', toVersion: data.toVersion || '', reason: data.reason || '', operator: 'admin', createdAt: new Date().toISOString() }
  resetStore.push(item); return item
}

export function getListenerList(): ReviewListener[] { initStore(); return listenerStore }
export function createListener(data: Partial<ReviewListener>): ReviewListener {
  checkApiPermission('review:write'); initStore()
  const item: ReviewListener = { id: `rl-${++seq}`, name: data.name || '', url: data.url || '', events: data.events || [], enabled: true, status: 'active', triggerCount: 0, createdAt: new Date().toISOString() }
  listenerStore.push(item); return item
}
export function updateListener(id: string, data: Partial<ReviewListener>): ReviewListener | null {
  checkApiPermission('review:write'); initStore()
  const idx = listenerStore.findIndex(l => l.id === id); if (idx === -1) return null
  listenerStore[idx] = { ...listenerStore[idx], ...data }; return listenerStore[idx]
}
export function deleteListener(id: string): boolean {
  checkApiPermission('review:delete'); initStore()
  const idx = listenerStore.findIndex(l => l.id === id); if (idx === -1) return false
  listenerStore.splice(idx, 1); return true
}

export function getComplianceList(): ComplianceRule[] { initStore(); return complianceStore }
export function createCompliance(data: Partial<ComplianceRule>): ComplianceRule {
  checkApiPermission('review:write'); initStore()
  const item: ComplianceRule = { id: `cr-${++seq}`, name: data.name || '', description: data.description || '', ruleType: data.ruleType || 'content', rule: data.rule || '', enabled: true, hitCount: 0, createdAt: new Date().toISOString() }
  complianceStore.push(item); return item
}
export function updateCompliance(id: string, data: Partial<ComplianceRule>): ComplianceRule | null {
  checkApiPermission('review:write'); initStore()
  const idx = complianceStore.findIndex(c => c.id === id); if (idx === -1) return null
  complianceStore[idx] = { ...complianceStore[idx], ...data }; return complianceStore[idx]
}
export function deleteCompliance(id: string): boolean {
  checkApiPermission('review:delete'); initStore()
  const idx = complianceStore.findIndex(c => c.id === id); if (idx === -1) return false
  complianceStore.splice(idx, 1); return true
}

export function getReportList(): ReviewReport[] { initStore(); return reportStore }

export function getQualityRuleList(): QualityRule[] { initStore(); return qualityRuleStore }
export function updateQualityRule(id: string, data: Partial<QualityRule>): QualityRule | null {
  checkApiPermission('review:write'); initStore()
  const idx = qualityRuleStore.findIndex(r => r.id === id); if (idx === -1) return null
  qualityRuleStore[idx] = { ...qualityRuleStore[idx], ...data }; return qualityRuleStore[idx]
}

export function getQualityScoreList(params?: { page?: number; pageSize?: number; level?: string }) {
  initStore(); let list = [...qualityScoreStore]
  if (params?.level) list = list.filter(s => s.level === params.level)
  const total = list.length; const page = params?.page || 1; const pageSize = params?.pageSize || 20
  return { list: list.slice((page - 1) * pageSize, page * pageSize), total }
}
