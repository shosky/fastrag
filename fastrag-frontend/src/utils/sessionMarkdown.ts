// ===========================================================================
// 会话导出 Markdown 拼装（app-chat-optimize Step 2，对齐 WeKnora ChatHeader
// buildSessionMarkdown:61-64 的纯函数形态）
//
// 输入为页面 UI 消息的结构子集（role/content/thinking/来源），不依赖 DOM 与
// 组件——node 环境可测。来源文件名取流内 sources，缺省回退历史 references。
// ===========================================================================

/** 会话导出的消息输入形状（ConversationChatMessage 的结构子集） */
export interface SessionExportMessage {
  role: string
  content: string
  /** 思考过程（助手消息可选） */
  thinkingContent?: string
  /** 流内知识来源（优先） */
  sources?: Array<{ fileName?: string }>
  /** 历史知识来源（sources 为空时的回退） */
  references?: Array<{ fileName?: string }>
}

/** 角色前缀（直聊 UI 词汇；system 等未知角色按原值展示） */
function roleLabel(role: string): string {
  if (role === 'user') return '用户'
  if (role === 'assistant') return '助手'
  return role
}

/** 单条消息的来源文件名（去重保序；流内 sources 优先，回退历史 references） */
function collectSourceFileNames(message: SessionExportMessage): string[] {
  const list =
    message.sources && message.sources.length ? message.sources : message.references || []
  const names: string[] = []
  for (const src of list) {
    const name = src?.fileName
    if (name && !names.includes(name)) names.push(name)
  }
  return names
}

/**
 * 拼装会话为 Markdown 文本（复制会话用）
 * @param messages 消息历史（按时间正序）
 * @param title 会话标题（可省；省略时不输出一级标题行）
 * @returns markdown 文本；无标题且无消息时返回空串
 */
export function buildSessionMarkdown(messages: SessionExportMessage[], title?: string): string {
  const parts: string[] = []
  if (title) parts.push(`# ${title}`)

  for (const message of messages || []) {
    let block = `**${roleLabel(message.role)}：**\n\n${message.content || ''}`
    if (message.thinkingContent) {
      block += `\n\n**思考过程：**\n\n${message.thinkingContent}`
    }
    const files = collectSourceFileNames(message)
    if (files.length) {
      block += `\n\n> 来源：${files.join('、')}`
    }
    parts.push(block)
  }

  return parts.join('\n\n---\n\n')
}
