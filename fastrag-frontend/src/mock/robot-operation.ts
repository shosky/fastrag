import { checkApiPermission } from './interceptor'

export interface FaqAnalysis {
  id: string; faqId: string; question: string; hitCount: number; missCount: number
  hitRate: number; avgSatisfaction: number; period: string; createdAt: string
}

export interface MultiTurnAnalysis {
  id: string; sessionId: string; turnCount: number; topic: string; resolution: 'resolved' | 'unresolved' | 'transferred'
  satisfaction?: number; keyIntents: string[]; createdAt: string
}

export interface IntentAnalysis {
  id: string; intent: string; utteranceCount: number; accuracy: number; coverage: number
  topConfusedIntents: string[]; suggestion: string; createdAt: string
}

export const RESOLUTION_LABELS: Record<string, string> = { resolved: '已解决', unresolved: '未解决', transferred: '已转人工' }
export const RESOLUTION_COLORS: Record<string, string> = { resolved: 'success', unresolved: 'danger', transferred: 'warning' }

let faqAnalysisStore: FaqAnalysis[] = []
let multiTurnStore: MultiTurnAnalysis[] = []
let intentStore: IntentAnalysis[] = []

function initStore() {
  if (faqAnalysisStore.length > 0) return
  const now = new Date().toISOString()

  faqAnalysisStore = Array.from({ length: 10 }, (_, i) => ({
    id: `fa-${i + 1}`, faqId: `faq-${i + 1}`, question: `常见问题${i + 1}？`,
    hitCount: Math.floor(Math.random() * 500) + 50, missCount: Math.floor(Math.random() * 50),
    hitRate: Math.round((0.6 + Math.random() * 0.35) * 100) / 100,
    avgSatisfaction: Math.round((3.5 + Math.random() * 1.5) * 10) / 10,
    period: '2026-06', createdAt: now,
  }))

  multiTurnStore = Array.from({ length: 8 }, (_, i) => ({
    id: `mta-${i + 1}`, sessionId: `sess-${i + 1}`, turnCount: Math.floor(Math.random() * 10) + 2,
    topic: ['退款咨询', '产品对比', '技术问题', '账户管理'][i % 4],
    resolution: (['resolved', 'unresolved', 'transferred'] as const)[i % 3],
    satisfaction: i % 3 === 0 ? Math.round((3 + Math.random() * 2) * 10) / 10 : undefined,
    keyIntents: ['查询', '操作', '咨询'].slice(0, (i % 3) + 1), createdAt: now,
  }))

  intentStore = Array.from({ length: 8 }, (_, i) => ({
    id: `ia-${i + 1}`, intent: ['查询订单', '退款申请', '账户问题', '产品咨询', '技术支持', '投诉建议', '物流查询', '支付问题'][i],
    utteranceCount: Math.floor(Math.random() * 1000) + 100,
    accuracy: Math.round((0.7 + Math.random() * 0.25) * 100) / 100,
    coverage: Math.round((0.5 + Math.random() * 0.4) * 100) / 100,
    topConfusedIntents: [['退款申请', '账户问题'], ['查询订单', '物流查询'], ['技术支持', '产品咨询']][i % 3],
    suggestion: '建议增加训练语料', createdAt: now,
  }))
}

export function getFaqAnalysisList(params?: { page?: number; pageSize?: number }) {
  initStore(); const total = faqAnalysisStore.length; const page = params?.page || 1; const pageSize = params?.pageSize || 20
  return { list: faqAnalysisStore.slice((page - 1) * pageSize, page * pageSize), total }
}

export function getMultiTurnList(params?: { page?: number; pageSize?: number }) {
  initStore(); const total = multiTurnStore.length; const page = params?.page || 1; const pageSize = params?.pageSize || 20
  return { list: multiTurnStore.slice((page - 1) * pageSize, page * pageSize), total }
}

export function getIntentList(params?: { page?: number; pageSize?: number }) {
  initStore(); const total = intentStore.length; const page = params?.page || 1; const pageSize = params?.pageSize || 20
  return { list: intentStore.slice((page - 1) * pageSize, page * pageSize), total }
}
