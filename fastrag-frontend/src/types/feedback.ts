// ===========================================================================
// 反馈模块类型（operation/feedback 页面）
// 与后端 ChatSession / UserFeedback 实体及 FeedbackController 返回结构对齐
// ===========================================================================

/** 聊天会话（问答明细，对应后端 chat_session 表） */
export interface ChatSession {
  id: string
  userId: string
  kbId?: string
  appId?: string
  query: string
  answer?: string
  retrievedChunks?: string
  model?: string
  /** 响应时长（毫秒） */
  duration?: number
  tokens?: number
  createdAt: string
}

/** 用户反馈（对应后端 user_feedback 表） */
export interface UserFeedback {
  id: number
  sessionId?: string
  userId?: string
  kbId?: string
  appId?: string
  query: string
  answer?: string
  feedback: string
  comment?: string
  category?: string
  status: string
  reply?: string
  processedBy?: string
  orgId?: string
  score?: number
  createdAt?: string
  processedAt?: string
}

/** 总览指标（FeedbackOverview.metrics） */
export interface FeedbackMetrics {
  totalQaCount: number
  totalFeedbackCount: number
  satisfactionRate: number
  feedbackRate: number
  unresolvedRate: number
}

/** 问题分类项 */
export interface QuestionCategory {
  name: string
  count: number
  percentage: number
}

/** 高频词项 */
export interface HotKeyword {
  word: string
  count: number
}

/** 应用满意度排行项 */
export interface AppSatisfactionRankItem {
  rank: number
  appId?: string
  name: string
  satisfaction: number
  feedbackCount: number
}

/** 反馈总览数据（GET /feedback/overview） */
export interface FeedbackOverview {
  metrics: FeedbackMetrics
  questionCategories: QuestionCategory[]
  hotKeywords: HotKeyword[]
  appSatisfactionRanking: AppSatisfactionRankItem[]
}

/** 反馈统计（GET /feedback/statistics） */
export interface FeedbackStatistics {
  total: number
  byType: Record<string, number>
  byStatus: Record<string, number>
  satisfactionRate: number
  avgScore: number
  resolvedRate: number
}
