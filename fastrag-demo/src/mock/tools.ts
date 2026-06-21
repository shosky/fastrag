// ===========================================================================
// 工具数据层 —— 全局唯一数据源
//
// 取代 my-tools.vue 的内联 ref 数据。
// 我的工具列表 / 创建 / 编辑 共享本文件。
// ===========================================================================

export type ToolType = 'builtin' | 'knowledge' | 'http'

export interface ToolInputParam {
  name: string
  description: string
  type: string
  /** 是否工具参数 */
  isToolParam: boolean
}

export interface KeyValue {
  key: string
  value: string
}

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE'
export type AuthType = 'none' | 'apiKey' | 'bearer' | 'oauth2'
export type BodyType = 'none' | 'form-data' | 'x-www-form-urlencoded' | 'json' | 'xml' | 'raw-text'

export interface HttpToolConfig {
  method: HttpMethod
  url: string
  authType: AuthType
  params: KeyValue[]
  bodyType: BodyType
  body: string
  headers: KeyValue[]
}

export interface Tool {
  id: string
  /** 工具名称 */
  name: string
  /** 唯一标识 */
  identifier: string
  /** 描述 */
  description: string
  /** 类型 */
  type: ToolType
  /** 标签 */
  tags: string[]
  /** 图标色 */
  icon: string
  /** HTTP 工具的配置（仅 type === 'http' 有值） */
  httpConfig?: HttpToolConfig
  /** 输入参数 */
  inputs: ToolInputParam[]
  /** 是否启用 */
  enabled: boolean
  /** 创建时间 */
  createdAt: string
}

// --- 工具分类标签 ---
export const TOOL_CATEGORIES: { label: string; value: ToolType }[] = [
  { label: '内置工具', value: 'builtin' },
  { label: '知识库', value: 'knowledge' },
  { label: 'HTTP工具', value: 'http' },
]

export function getTypeLabel(type: ToolType): string {
  return TOOL_CATEGORIES.find((t) => t.value === type)?.label || type
}

export const HTTP_METHOD_OPTIONS: HttpMethod[] = ['GET', 'POST', 'PUT', 'DELETE']
export const AUTH_TYPE_OPTIONS: { label: string; value: AuthType }[] = [
  { label: '无', value: 'none' },
  { label: 'API Key', value: 'apiKey' },
  { label: 'Bearer Token', value: 'bearer' },
  { label: 'OAuth 2.0', value: 'oauth2' },
]
export const BODY_TYPE_OPTIONS: { label: string; value: BodyType }[] = [
  { label: 'none', value: 'none' },
  { label: 'form-data', value: 'form-data' },
  { label: 'x-www-form-urlencoded', value: 'x-www-form-urlencoded' },
  { label: 'json', value: 'json' },
  { label: 'xml', value: 'xml' },
  { label: 'raw-text', value: 'raw-text' },
]

// --- 种子数据 ---

const builtinTools: Tool[] = [
  {
    id: '1',
    name: '安装技能',
    identifier: 'install_skill',
    description: '安装新的 Skill 到当前用户的私有空间，并在当前主智能体会话中激活。',
    type: 'builtin',
    tags: ['内置工具', 'skill', '安装'],
    icon: '#409eff',
    inputs: [],
    enabled: true,
    createdAt: '2026-05-01 10:00',
  },
  {
    id: '2',
    name: 'Tavily 网页搜索',
    identifier: 'tavily_search',
    description: 'A search engine optimized for comprehensive, accurate, and trusted results. Useful for when you need current information.',
    type: 'builtin',
    tags: ['内置工具', '搜索'],
    icon: '#67c23a',
    inputs: [],
    enabled: true,
    createdAt: '2026-05-01 10:00',
  },
  {
    id: '3',
    name: '展示交付物',
    identifier: 'present_artifacts',
    description: '将已经生成好的结果文件展示给用户。使用场景：你已经在工作目录生成了结果文件。',
    type: 'builtin',
    tags: ['内置工具', '文件', '交付物'],
    icon: '#e6a23c',
    inputs: [],
    enabled: true,
    createdAt: '2026-05-01 10:00',
  },
  {
    id: '4',
    name: '向用户提问',
    identifier: 'ask_user_question',
    description: '在执行过程中，当你需要用户做决定或补充需求时，使用这个工具向用户提问。',
    type: 'builtin',
    tags: ['内置工具', '交互'],
    icon: '#f56c6c',
    inputs: [],
    enabled: true,
    createdAt: '2026-05-01 10:00',
  },
]

const knowledgeTools: Tool[] = [
  {
    id: '5',
    name: 'List Kbs',
    identifier: 'list_kbs',
    description: '列出当前用户可访问的知识库列表。返回用户基于权限可访问的知识库名称列表。',
    type: 'knowledge',
    tags: ['知识库'],
    icon: '#909399',
    inputs: [],
    enabled: true,
    createdAt: '2026-05-02 09:00',
  },
  {
    id: '6',
    name: 'Get Mindmap',
    identifier: 'get_mindmap',
    description: '获取指定知识库的思维导图结构。当用户想了解知识库的整体结构时使用。',
    type: 'knowledge',
    tags: ['知识库'],
    icon: '#909399',
    inputs: [],
    enabled: true,
    createdAt: '2026-05-02 09:00',
  },
  {
    id: '7',
    name: 'Query Kb',
    identifier: 'query_kb',
    description: '在指定知识库中检索内容。当用户需要查询具体内容时使用此工具。',
    type: 'knowledge',
    tags: ['知识库'],
    icon: '#909399',
    inputs: [],
    enabled: true,
    createdAt: '2026-05-02 09:00',
  },
  {
    id: '8',
    name: 'Open Kb Document',
    identifier: 'open_kb_document',
    description: '按行窗口打开知识库文档原文。当 query_kb 返回的片段不足以回答问题时使用。',
    type: 'knowledge',
    tags: ['知识库'],
    icon: '#909399',
    inputs: [],
    enabled: true,
    createdAt: '2026-05-02 09:00',
  },
  {
    id: '9',
    name: 'Find Kb Document',
    identifier: 'find_kb_document',
    description: '在已知知识库文件内做关键词或正则定位。当需要精确定位文件内文本时使用。',
    type: 'knowledge',
    tags: ['知识库'],
    icon: '#909399',
    inputs: [],
    enabled: true,
    createdAt: '2026-05-02 09:00',
  },
]

// HTTP 工具示例
const httpTools: Tool[] = [
  {
    id: '10',
    name: '天气查询',
    identifier: 'weather_query',
    description: '通过 HTTP 接口查询指定城市的实时天气信息。',
    type: 'http',
    tags: ['HTTP工具', '天气'],
    icon: '#409eff',
    httpConfig: {
      method: 'GET',
      url: 'https://api.weather.example.com/v1/current',
      authType: 'apiKey',
      params: [{ key: 'city', value: 'beijing' }, { key: 'unit', value: 'metric' }],
      bodyType: 'none',
      body: '',
      headers: [{ key: 'Accept', value: 'application/json' }],
    },
    inputs: [
      { name: 'city', description: '城市名称', type: 'string', isToolParam: true },
    ],
    enabled: true,
    createdAt: '2026-06-01 14:00',
  },
]

const toolStore: Tool[] = [...builtinTools, ...knowledgeTools, ...httpTools]

let toolSeq = 100

function clone(t: Tool): Tool {
  return {
    ...t,
    tags: [...t.tags],
    inputs: t.inputs.map((i) => ({ ...i })),
    httpConfig: t.httpConfig
      ? {
          ...t.httpConfig,
          params: t.httpConfig.params.map((p) => ({ ...p })),
          headers: t.httpConfig.headers.map((h) => ({ ...h })),
        }
      : undefined,
  }
}

// --- CRUD ---

/** 获取所有工具 */
export function getTools(): Tool[] {
  return toolStore.map(clone)
}

/** 获取单个工具 */
export function getTool(id: string): Tool | null {
  const t = toolStore.find((x) => x.id === id)
  return t ? clone(t) : null
}

/** 创建工具 */
export function createTool(form: Omit<Tool, 'id' | 'createdAt'>): Tool {
  const now = new Date().toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-')
  const tool: Tool = {
    ...form,
    id: String(++toolSeq),
    createdAt: now,
    tags: [...form.tags],
    inputs: form.inputs.map((i) => ({ ...i })),
    httpConfig: form.httpConfig
      ? {
          ...form.httpConfig,
          params: form.httpConfig.params.map((p) => ({ ...p })),
          headers: form.httpConfig.headers.map((h) => ({ ...h })),
        }
      : undefined,
  }
  toolStore.unshift(tool)
  return clone(tool)
}

/** 更新工具 */
export function updateTool(id: string, patch: Partial<Tool>): Tool | null {
  const idx = toolStore.findIndex((t) => t.id === id)
  if (idx === -1) return null
  toolStore[idx] = {
    ...toolStore[idx],
    ...patch,
    tags: patch.tags ? [...patch.tags] : toolStore[idx].tags,
    inputs: patch.inputs ? patch.inputs.map((i) => ({ ...i })) : toolStore[idx].inputs,
    httpConfig: patch.httpConfig
      ? {
          ...patch.httpConfig,
          params: patch.httpConfig.params.map((p) => ({ ...p })),
          headers: patch.httpConfig.headers.map((h) => ({ ...h })),
        }
      : toolStore[idx].httpConfig,
  }
  return clone(toolStore[idx])
}

/** 删除工具 */
export function deleteTool(id: string): boolean {
  const idx = toolStore.findIndex((t) => t.id === id)
  if (idx === -1) return false
  toolStore.splice(idx, 1)
  return true
}

/** 切换启用状态 */
export function toggleToolEnabled(id: string): Tool | null {
  const t = toolStore.find((x) => x.id === id)
  if (!t) return null
  t.enabled = !t.enabled
  return clone(t)
}

/** 默认的 HTTP 工具表单值 */
export function defaultHttpConfig(): HttpToolConfig {
  return {
    method: 'POST',
    url: '',
    authType: 'none',
    params: [{ key: '', value: '' }],
    bodyType: 'json',
    body: '',
    headers: [{ key: '', value: '' }],
  }
}

/** 默认的输入参数 */
export function defaultInputs(): ToolInputParam[] {
  return [{ name: '', description: '', type: 'string', isToolParam: true }]
}
