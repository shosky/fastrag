// ===========================================================================
// MCP 服务数据层 —— 全局唯一数据源
//
// 取代 mcp-services.vue 的内联 ref 数据。
// MCP 管理列表 / 创建 / 编辑 / 详情 共享本文件。
// ===========================================================================

export type McpStatus = 'online' | 'offline'
export type McpAuthType = 'Bearer' | 'API Key' | 'none'

export interface McpToolParam {
  name: string
  type: string
  description: string
  required: boolean
}

export interface McpTool {
  name: string
  description: string
  /** 参数定义 */
  params: McpToolParam[]
}

export interface McpCallLog {
  id: string
  /** 调用方应用 / 技能 */
  caller: string
  /** 调用的工具 */
  tool: string
  /** 状态 */
  status: 'success' | 'error'
  /** 耗时(ms) */
  duration: number
  timestamp: string
}

export interface McpService {
  id: string
  /** 服务名称 */
  name: string
  /** MCP 服务地址 */
  mcpUrl: string
  /** 鉴权类型 */
  authType: McpAuthType
  /** 鉴权值 */
  authValue: string
  /** 服务状态 */
  status: McpStatus
  /** 是否启用 */
  enabled: boolean
  /** 暴露的工具列表 */
  toolsList: McpTool[]
  /** 最近使用时间 */
  lastUsed: string
  /** 创建时间 */
  createdAt: string
  /** 调用日志 */
  callLogs: McpCallLog[]
}

// --- 状态映射 ---
export const STATUS_LABELS: Record<McpStatus, string> = {
  online: '在线',
  offline: '离线',
}

export const AUTH_TYPE_OPTIONS: { label: string; value: McpAuthType }[] = [
  { label: 'Bearer', value: 'Bearer' },
  { label: 'API Key', value: 'API Key' },
  { label: '无', value: 'none' },
]

export function getStatusLabel(status: McpStatus): string {
  return STATUS_LABELS[status] || status
}

// --- 种子数据 ---

const seedTools1: McpTool[] = [
  {
    name: 'bing_search',
    description: '使用必应中文搜索引擎搜索信息。返回搜索结果包括标题、链接和摘要。',
    params: [
      { name: 'query', type: 'string', description: '搜索关键词', required: true },
      { name: 'top_k', type: 'number', description: '返回结果数量', required: false },
    ],
  },
  {
    name: 'crawl_webpage',
    description: '根据搜索结果的 UUID 抓取网页内容。支持批量抓取多个网页。会自动过滤黑名单中的网站。',
    params: [
      { name: 'uuid', type: 'string', description: '搜索结果 UUID', required: true },
    ],
  },
]

const seedTools2: McpTool[] = [
  {
    name: 'execute_python',
    description: '在沙箱中执行 Python 代码并返回标准输出。',
    params: [{ name: 'code', type: 'string', description: 'Python 代码', required: true }],
  },
  {
    name: 'execute_javascript',
    description: '在沙箱中执行 JavaScript 代码并返回结果。',
    params: [{ name: 'code', type: 'string', description: 'JavaScript 代码', required: true }],
  },
]

const seedTools3: McpTool[] = [
  {
    name: 'query_sql',
    description: '对授权数据源执行只读 SQL 查询。',
    params: [
      { name: 'sql', type: 'string', description: 'SQL 语句', required: true },
      { name: 'datasource', type: 'string', description: '数据源标识', required: true },
    ],
  },
  {
    name: 'query_nosql',
    description: '对文档型数据库执行查询。',
    params: [{ name: 'filter', type: 'object', description: '查询条件', required: true }],
  },
]

function now(offsetDays = 0): string {
  const d = new Date()
  d.setDate(d.getDate() - offsetDays)
  return d.toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-')
}

const mcpStore: McpService[] = [
  {
    id: '1',
    name: 'leowang',
    mcpUrl: 'https://mcp.api-inference.modelscope.net/75cb7553dc8c4f/mcp',
    authType: 'Bearer',
    authValue: 'sk-mcp-***',
    status: 'online',
    enabled: true,
    toolsList: seedTools1,
    lastUsed: now(0),
    createdAt: now(30),
    callLogs: [
      { id: 'l1', caller: '联网搜索', tool: 'bing_search', status: 'success', duration: 320, timestamp: now(0) },
      { id: 'l2', caller: '智能问答助手', tool: 'crawl_webpage', status: 'success', duration: 580, timestamp: now(0) },
      { id: 'l3', caller: '数据分析', tool: 'bing_search', status: 'error', duration: 120, timestamp: now(1) },
    ],
  },
  {
    id: '2',
    name: 'code_executor',
    mcpUrl: 'https://mcp.example.com/code-exec',
    authType: 'API Key',
    authValue: 'ak-code-***',
    status: 'online',
    enabled: true,
    toolsList: seedTools2,
    lastUsed: now(0),
    createdAt: now(20),
    callLogs: [
      { id: 'l4', caller: '代码生成', tool: 'execute_python', status: 'success', duration: 890, timestamp: now(0) },
    ],
  },
  {
    id: '3',
    name: 'database_query',
    mcpUrl: 'https://mcp.example.com/db-query',
    authType: 'Bearer',
    authValue: 'sk-db-***',
    status: 'offline',
    enabled: false,
    toolsList: seedTools3,
    lastUsed: now(5),
    createdAt: now(60),
    callLogs: [
      { id: 'l5', caller: '数据分析', tool: 'query_sql', status: 'error', duration: 60, timestamp: now(5) },
    ],
  },
]

let mcpSeq = 100

function clone(s: McpService): McpService {
  return {
    ...s,
    toolsList: s.toolsList.map((t) => ({ ...t, params: t.params.map((p) => ({ ...p })) })),
    callLogs: s.callLogs.map((l) => ({ ...l })),
  }
}

// --- CRUD ---

/** 获取所有 MCP 服务 */
export function getMcpServices(): McpService[] {
  return mcpStore.map(clone)
}

/** 获取单个 MCP 服务 */
export function getMcpService(id: string): McpService | null {
  const s = mcpStore.find((x) => x.id === id)
  return s ? clone(s) : null
}

/** 创建 MCP 服务 */
export function createMcpService(form: Omit<McpService, 'id' | 'lastUsed' | 'createdAt' | 'callLogs'>): McpService {
  const nowStr = new Date().toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-')
  const service: McpService = {
    ...form,
    id: String(++mcpSeq),
    lastUsed: '-',
    createdAt: nowStr,
    callLogs: [],
    toolsList: (form.toolsList || []).map((t) => ({ ...t, params: t.params.map((p) => ({ ...p })) })),
  }
  mcpStore.unshift(service)
  return clone(service)
}

/** 更新 MCP 服务 */
export function updateMcpService(id: string, patch: Partial<McpService>): McpService | null {
  const idx = mcpStore.findIndex((s) => s.id === id)
  if (idx === -1) return null
  mcpStore[idx] = {
    ...mcpStore[idx],
    ...patch,
    toolsList: patch.toolsList ? patch.toolsList.map((t) => ({ ...t, params: t.params.map((p) => ({ ...p })) })) : mcpStore[idx].toolsList,
    callLogs: patch.callLogs ? patch.callLogs.map((l) => ({ ...l })) : mcpStore[idx].callLogs,
  }
  return clone(mcpStore[idx])
}

/** 删除 MCP 服务 */
export function deleteMcpService(id: string): boolean {
  const idx = mcpStore.findIndex((s) => s.id === id)
  if (idx === -1) return false
  mcpStore.splice(idx, 1)
  return true
}

/** 切换启用状态 */
export function toggleMcpEnabled(id: string): McpService | null {
  const s = mcpStore.find((x) => x.id === id)
  if (!s) return null
  s.enabled = !s.enabled
  return clone(s)
}

/** 切换在线状态（模拟连通性检测） */
export function toggleMcpStatus(id: string): McpService | null {
  const s = mcpStore.find((x) => x.id === id)
  if (!s) return null
  s.status = s.status === 'online' ? 'offline' : 'online'
  return clone(s)
}

// --- 工具辅助 ---

/**
 * 解析 MCP 地址，拉取其暴露的工具列表。
 * mock 实现：按地址返回一组示例工具。
 */
export function parseMcpUrl(url: string): { tools: McpTool[]; message: string } {
  if (!url || !/^https?:\/\//.test(url)) {
    return { tools: [], message: '地址格式不正确，需以 http(s):// 开头' }
  }
  // 根据地址特征返回不同示例
  const lower = url.toLowerCase()
  if (lower.includes('search') || lower.includes('mcp')) {
    return {
      tools: seedTools1.map((t) => ({ ...t, params: t.params.map((p) => ({ ...p })) })),
      message: '解析成功，共发现 2 个工具',
    }
  }
  if (lower.includes('code') || lower.includes('exec')) {
    return {
      tools: seedTools2.map((t) => ({ ...t, params: t.params.map((p) => ({ ...p })) })),
      message: '解析成功，共发现 2 个工具',
    }
  }
  if (lower.includes('db') || lower.includes('query')) {
    return {
      tools: seedTools3.map((t) => ({ ...t, params: t.params.map((p) => ({ ...p })) })),
      message: '解析成功，共发现 2 个工具',
    }
  }
  // 默认返回通用工具
  return {
    tools: [
      { name: 'default_tool', description: 'MCP 服务默认暴露的工具。', params: [{ name: 'input', type: 'string', description: '输入参数', required: true }] },
    ],
    message: '解析成功，共发现 1 个工具',
  }
}

/**
 * 刷新 MCP 服务：重新拉取工具列表 + 检测连通性 + 更新最近使用时间。
 * mock 实现返回刷新后的服务。
 */
export function refreshMcpService(id: string): McpService | null {
  const s = mcpStore.find((x) => x.id === id)
  if (!s) return null
  const { tools } = parseMcpUrl(s.mcpUrl)
  s.toolsList = tools.length ? tools : s.toolsList
  s.status = Math.random() > 0.15 ? 'online' : 'offline'
  s.lastUsed = new Date().toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-')
  return clone(s)
}
