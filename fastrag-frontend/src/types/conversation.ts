// ===========================================================================
// 会话中心（new-conversation）领域类型
//
// 数据形状对齐后端契约 fastrag-backend docs/contract/data.md `## new-conversation`
// （ConversationDTO / ConversationRequestState / MessageReference）。
// HTTP 路径以 springdoc 实现为准（前瞻契约，代码未实现）。
import type { SourceItem } from '@/composables/useConversationStream'
// ===========================================================================

/**
 * 会话请求态（request_state JSON 形状）。
 * toolIds/mcpIds/skillIds 为直聊消费字段——B02 演进（2026-09-25 用户裁决）：直聊随 stream
 * 请求体装配进执行上下文（与绑定同路径），挂应用时能力走应用绑定、前端不发送；
 * webSearchEnabled 仍为唯一死字段，前端不建模不发送。
 * skillNames 为 F1 @提及技能字段（2026-10-07 裁决）：pinned 只加强提示不收窄白名单，
 * 后端去重、上限 10，集外点名由 SkillsMiddleware 静默忽略并 warn。
 */
export interface ConversationRequestState {
  appId?: string | null
  appEnabled?: boolean
  kbIds?: string[]
  modelId?: string | null
  /** 直聊消费（B02 演进）：装配进执行上下文的工具/MCP/技能 ID */
  toolIds?: string[]
  mcpIds?: string[]
  skillIds?: string[]
  /** F1 @提及技能：本轮施加 must_use 强提示的技能名（后端去重、上限 10） */
  skillNames?: string[]
}

/** 会话列表项 / 详情（ConversationDTO） */
export interface ConversationSummary {
  id: string
  title: string
  /** 来源标记文案：直聊 / 应用名（后端逐页批量查询补齐；应用已删显示「已删应用」） */
  sourceLabel?: string
  appId?: string | null
  isPinned?: boolean
  messageCount?: number
  firstQuestion?: string
  answerSummary?: string
  updatedAt?: string
  requestState?: ConversationRequestState | null
}

/** 历史消息引用（references JSON 元素；sources 事件的投影子集，无 content） */
export interface MessageReference {
  kbId: string
  fileId: string
  fileName?: string
  chunkIndex?: number
  chunkType?: string
  score?: number
  imageKeys?: string[]
}

// ===========================================================================
// 页面 UI 消息模型（会话中心聊天区；形状沿 runtime.vue ChatMessage 惯例）
// ===========================================================================

/** 应用预设（composerState.appId 的 UI 形态：chip 显示名 + id） */
export interface PresetApp {
  id: string
  name: string
}

/** 工具调用条目（兼容 ToolCallRenderer 的嵌套 result 结构） */
export interface ToolCallItem {
  id: string
  name: string
  arguments: string
  /** data：结构化数据（后端透传，渲染路由 toolDisplay.resolveToolDisplayType 消费） */
  result?: { success: boolean; output: string; durationMs: number; error?: string; data?: Record<string, unknown> }
}

/** 聊天消息（乐观气泡 + 历史消息共用；sources 为流内形态，references 为历史形态） */
export interface ConversationChatMessage {
  id?: string
  role: 'user' | 'assistant' | 'system'
  content: string
  time?: string
  streaming?: boolean
  thinkingContent?: string
  isThinking?: boolean
  toolCalls?: ToolCallItem[]
  feedback?: string
  /** 流内知识来源（sources 事件，含 content 全文） */
  sources?: SourceItem[]
  /** 历史知识来源（消息 references，无 content） */
  references?: MessageReference[]
  /** 检索空态提示（未选知识库直接回答 / 检索零命中），渲染于答案与来源区之后 */
  retrievalHint?: string
  /** 本轮工具调用轮数（end 事件 data.iterations，AgentEngine totalIterations；供会话汇总行） */
  iterations?: number
}
