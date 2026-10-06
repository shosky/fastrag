// ===========================================================================
// 工具结果渲染路由（app-chat-optimize 二批；三批扩展 mcp / skill_md 兜底形态）
//
// 后端结构化通道（模块测试 72/72 绿）：tool_result 事件与历史 toolCalls 的
// result 均可携带 data 字段（fastrag-backend AgentEngine.java:280-310，structured
// 同写入事件与库）。已落地的两类结构：
// - 数据库查询（工具名 *_query_database）：
//     data = { display_type:'database_query', columns, rows, row_count, elapsed_ms }
// - 知识库检索（工具名 query_kb）：
//     data = { display_type:'knowledge_chunks', kb_id, query, chunks:[{content,score,source,...}] }
// 三批兜底两类（无 data，按工具名识别）：
// - MCP 工具（工具名含 mcp）：output 若可 JSON.parse 且为数组/对象 → 键值/列表化
//   （MCP 形状取决于远端，不强解析，解析失败维持原始输出 pre）
// - 技能（工具名含 skill，或 read_file / read）：后端技能读取返回 Markdown，结果按
//   markdown 渲染（renderMarkdown 单源已过 DOMPurify）
// 其余工具 → generic 回退。
// ===========================================================================

export type ToolDisplayType = 'database_query' | 'knowledge_chunks' | 'mcp' | 'skill_md' | 'generic'

/** 工具结果形状（ToolCallItem.result / ToolResultEvent 的公共投影，只读消费） */
export interface ToolDisplayResult {
  success?: boolean
  output?: string
  durationMs?: number
  error?: string
  data?: Record<string, unknown>
}

/** 技能族裸工具名（后端技能读取实现名；用全等避免 'read' 误伤 spread/thread 等） */
const SKILL_BARE_NAMES = new Set(['read_file', 'read'])

/** 工具名 → mcp 兜底形态（含 mcp 即命中，覆盖 mcp 前缀与 {server}_mcp_* 变体） */
export function isMcpToolName(toolName: string): boolean {
  return typeof toolName === 'string' && toolName.includes('mcp')
}

/** 工具名 → skill_md 兜底形态（含 skill，或 read_file / read 裸名） */
export function isSkillToolName(toolName: string): boolean {
  if (typeof toolName !== 'string') return false
  return toolName.includes('skill') || SKILL_BARE_NAMES.has(toolName)
}

/**
 * 渲染路由：优先级（对齐 WeKnora resolveToolDisplayType）——
 * 1. result.data.display_type（后端显式标记，最可信；仅认已落地两类，未知值降级继续判定）
 * 2. 工具名后缀 `_query_database`（数据库查询族，旧记录无 data 时仍可路由）
 * 3. 工具名 `query_kb`（知识库检索）
 * 4. 工具名含 `mcp`（MCP 工具兜底）
 * 5. 工具名含 `skill` / `read_file` / `read`（技能 Markdown 兜底）
 * 6. generic 回退
 */
export function resolveToolDisplayType(
  toolName: string,
  result?: ToolDisplayResult | null
): ToolDisplayType {
  const marked = result?.data?.display_type
  if (marked === 'database_query' || marked === 'knowledge_chunks') return marked
  if (typeof toolName === 'string' && toolName.endsWith('_query_database')) return 'database_query'
  if (toolName === 'query_kb') return 'knowledge_chunks'
  if (isMcpToolName(toolName)) return 'mcp'
  if (isSkillToolName(toolName)) return 'skill_md'
  return 'generic'
}

/** 失败摘要截断上限（对齐任务约定：error 文本截 40） */
const SUMMARY_MAX = 40

function truncate(text: string, max = SUMMARY_MAX): string {
  return text.length > max ? text.slice(0, max) + '...' : text
}

/**
 * 一行摘要（ToolCallRenderer 收起状态常驻展示，收起也有信息量）——
 * pending（无 result）=「执行中…」；失败 = error 文本截 40（历史落库失败时后端把
 * error 写进 output，兜底取 output）；database_query =「查询返回 {row_count} 行」；
 * query_kb =「命中 {chunks.length} 条分片」；成功 generic =「执行完成」。
 */
export function summarizeToolCall(toolName: string, result?: ToolDisplayResult | null): string {
  if (!result) return '执行中…'
  if (result.success === false) {
    return truncate(result.error || result.output || '执行失败')
  }
  switch (resolveToolDisplayType(toolName, result)) {
    case 'database_query': {
      const rowCount = result.data?.row_count
      return typeof rowCount === 'number' ? `查询返回 ${rowCount} 行` : '执行完成'
    }
    case 'knowledge_chunks': {
      const chunks = result.data?.chunks
      return Array.isArray(chunks) ? `命中 ${chunks.length} 条分片` : '执行完成'
    }
    default:
      // mcp / skill_md / generic 无结构化 data，摘要统一「执行完成」
      return '执行完成'
  }
}

// ── MCP 结果轻结构化（app-chat-optimize 三批；MCP 形状取决于远端，不强解析）──

/** 单行展示上限（防超大结果撑爆卡片；超出部分折叠提示） */
const MCP_ROWS_MAX = 50

/** 键值行（mcpStructuredRows 的元素；value 已字符串化供直接渲染） */
export interface McpRow {
  key: string
  value: string
}

function stringifyCell(v: unknown): string {
  if (v === null) return 'null'
  if (typeof v === 'string') return v
  if (typeof v === 'number' || typeof v === 'boolean') return String(v)
  try { return JSON.stringify(v) } catch { return String(v) }
}

/**
 * MCP output 轻结构化：可 JSON.parse 且为对象/数组时返回键值行列表，否则 undefined
 * （调用方回退原始输出 pre）。对象 → 顶层键值对；数组 → 索引 + 单元格字符串化。
 * 标量顶层（string/number/...）不算结构化，返回 undefined 维持 pre。
 */
export function mcpStructuredRows(output: string, maxRows: number = MCP_ROWS_MAX): McpRow[] | undefined {
  if (typeof output !== 'string' || !output.trim()) return undefined
  let parsed: unknown
  try { parsed = JSON.parse(output) } catch { return undefined }
  if (Array.isArray(parsed)) {
    return parsed.slice(0, maxRows).map((item, i) => ({ key: `[${i}]`, value: stringifyCell(item) }))
  }
  if (parsed !== null && typeof parsed === 'object') {
    return Object.entries(parsed as Record<string, unknown>)
      .slice(0, maxRows)
      .map(([k, v]) => ({ key: k, value: stringifyCell(v) }))
  }
  return undefined
}
