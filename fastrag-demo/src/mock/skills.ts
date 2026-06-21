// ===========================================================================
// 技能数据层 —— 全局唯一数据源
//
// 取代 skill-management.vue 的内联 ref 数据。
// 技能管理页 / 应用编排 共享本文件。
// ===========================================================================

/** 技能来源类型 */
export type SkillSource = 'builtin' | 'custom' | 'plugin'

/** 代码载体类型 */
export type SkillCodeType = 'python' | 'yaml' | 'json' | 'markdown'

/** 生效范围类型 */
export type SkillScope = 'global' | 'app' | 'kb'

export interface SkillDependency {
  id: string
  /** 依赖类型：工具 / 模型 / MCP / 其他技能 */
  type: 'tool' | 'model' | 'mcp' | 'skill'
  name: string
  /** 是否必选 */
  required: boolean
}

export interface SkillScopeBinding {
  /** 绑定 ID：global 为 '' */
  id: string
  /** 绑定名称 */
  name: string
  /** 是否启用 */
  enabled: boolean
}

export interface Skill {
  id: string
  /** 技能名称 */
  name: string
  /** 唯一标识 */
  identifier: string
  /** 描述 */
  description: string
  /** 图标色（卡片头背景） */
  icon: string
  /** 来源 */
  source: SkillSource
  /** 分类标签 */
  category: string
  /** 触发场景描述 */
  trigger: string
  /** 技能内容 / 提示词 */
  content: string
  /** 代码类型 */
  codeType: SkillCodeType
  /** 代码内容 */
  code: string
  /** 入参示例（JSON） */
  inputs: string
  /** 出参示例（JSON） */
  outputs: string
  /** 是否启用 */
  enabled: boolean
  /** 是否推荐 */
  recommended: boolean
  /** 依赖项 */
  dependencies: SkillDependency[]
  /** 生效范围绑定 */
  scopes: SkillScopeBinding[]
  /** 调用次数 */
  usageCount: number
  /** 作者 */
  author: string
  /** 版本号 */
  version: string
  /** 更新时间 */
  updatedAt: string
}

/** 技能分类 */
export const SKILL_CATEGORIES: { label: string; value: string }[] = [
  { label: '全部', value: '' },
  { label: '信息检索', value: 'retrieval' },
  { label: '内容生成', value: 'generation' },
  { label: '数据分析', value: 'analysis' },
  { label: '工具调用', value: 'tool' },
  { label: '文档处理', value: 'document' },
]

/** 来源选项 */
export const SKILL_SOURCE_OPTIONS: { label: string; value: SkillSource; tagType: string }[] = [
  { label: '内置技能', value: 'builtin', tagType: 'primary' },
  { label: '自定义技能', value: 'custom', tagType: 'success' },
  { label: '插件技能', value: 'plugin', tagType: 'warning' },
]

export function getSourceMeta(source: SkillSource) {
  return SKILL_SOURCE_OPTIONS.find((s) => s.value === source) || SKILL_SOURCE_OPTIONS[0]
}

export function getCategoryLabel(value: string): string {
  return SKILL_CATEGORIES.find((c) => c.value === value)?.label || value
}

// --- 种子数据 ---

const DEFAULT_CODE = `def run(query: str) -> dict:
    """技能入口函数"""
    return {"result": query}`

const skillStore: Skill[] = [
  {
    id: '1',
    name: '联网搜索',
    identifier: 'web_search',
    description: '当用户询问实时信息、新闻、天气、股价等内容时，自动检索互联网获取最新结果。',
    icon: '#409eff',
    source: 'builtin',
    category: 'retrieval',
    trigger: '用户询问新闻、天气、股价、赛事等实时信息',
    content: '调用搜索 API 查询最新信息，按相关性排序后整理为结构化摘要返回。',
    codeType: 'python',
    code: DEFAULT_CODE,
    inputs: '{"query": "string", "top_k": "number"}',
    outputs: '{"result": "string", "sources": "array"}',
    enabled: true,
    recommended: true,
    dependencies: [
      { id: 'd1', type: 'tool', name: 'Tavily 网页搜索', required: true },
      { id: 'd2', type: 'model', name: 'qwen3-32b', required: true },
    ],
    scopes: [
      { id: '', name: '全局生效', enabled: true },
      { id: 'app1', name: '智能问答助手', enabled: true },
    ],
    usageCount: 2340,
    author: '系统',
    version: '1.2.0',
    updatedAt: '2026-06-15 10:20',
  },
  {
    id: '2',
    name: '代码生成',
    identifier: 'code_generate',
    description: '根据自然语言需求生成符合最佳实践的代码片段，支持多种编程语言。',
    icon: '#67c23a',
    source: 'builtin',
    category: 'generation',
    trigger: '用户请求编写、生成、补全代码',
    content: '解析需求，选择技术栈，生成可运行的代码并附简要说明。',
    codeType: 'python',
    code: DEFAULT_CODE,
    inputs: '{"requirement": "string", "language": "string"}',
    outputs: '{"code": "string", "explanation": "string"}',
    enabled: true,
    recommended: true,
    dependencies: [
      { id: 'd1', type: 'model', name: 'qwen3-32b', required: true },
    ],
    scopes: [
      { id: '', name: '全局生效', enabled: true },
    ],
    usageCount: 1890,
    author: '系统',
    version: '1.0.3',
    updatedAt: '2026-06-12 14:08',
  },
  {
    id: '3',
    name: '数据分析',
    identifier: 'data_analysis',
    description: '对用户上传的数据文件进行统计分析，并生成可视化图表。',
    icon: '#e6a23c',
    source: 'custom',
    category: 'analysis',
    trigger: '用户上传数据文件或提供数据集',
    content: '解析数据结构，进行描述性统计与相关性分析，输出图表与结论。',
    codeType: 'python',
    code: DEFAULT_CODE,
    inputs: '{"file_url": "string", "question": "string"}',
    outputs: '{"charts": "array", "summary": "string"}',
    enabled: true,
    recommended: false,
    dependencies: [
      { id: 'd1', type: 'model', name: 'DeepSeek-V3', required: true },
      { id: 'd2', type: 'tool', name: '展示交付物', required: false },
    ],
    scopes: [
      { id: 'app3', name: '客服机器人', enabled: false },
    ],
    usageCount: 560,
    author: '张三',
    version: '0.9.1',
    updatedAt: '2026-06-10 09:30',
  },
  {
    id: '4',
    name: '文档翻译',
    identifier: 'doc_translate',
    description: '将文档翻译为目标语言，保留原文版式与术语一致性。',
    icon: '#f56c6c',
    source: 'plugin',
    category: 'document',
    trigger: '用户请求翻译文档',
    content: '调用翻译模型，按段落翻译并保持格式，输出对照结果。',
    codeType: 'yaml',
    code: 'name: doc_translate\ntrigger: 翻译文档\nsteps:\n  - parse: document\n  - translate: target_lang',
    inputs: '{"file_url": "string", "target_lang": "string"}',
    outputs: '{"translated_url": "string"}',
    enabled: false,
    recommended: false,
    dependencies: [
      { id: 'd1', type: 'mcp', name: '翻译服务 MCP', required: true },
    ],
    scopes: [],
    usageCount: 320,
    author: '插件市场',
    version: '2.0.0',
    updatedAt: '2026-05-28 16:45',
  },
  {
    id: '5',
    name: '会议纪要',
    identifier: 'meeting_minutes',
    description: '自动从会议录音或文字记录中提取关键信息，生成结构化纪要。',
    icon: '#909399',
    source: 'custom',
    category: 'document',
    trigger: '用户上传会议录音或文字记录',
    content: '提取参会人、议题、决议、待办，生成 Markdown 纪要。',
    codeType: 'python',
    code: DEFAULT_CODE,
    inputs: '{"transcript": "string"}',
    outputs: '{"minutes": "string", "todos": "array"}',
    enabled: true,
    recommended: false,
    dependencies: [
      { id: 'd1', type: 'model', name: 'qwen3-32b', required: true },
    ],
    scopes: [
      { id: 'app2', name: '文档写作助手', enabled: true },
    ],
    usageCount: 450,
    author: '李四',
    version: '1.1.0',
    updatedAt: '2026-06-05 11:12',
  },
  {
    id: '6',
    name: '知识库问答',
    identifier: 'kb_qa',
    description: '基于指定知识库进行 RAG 检索，回答用户的领域问题。',
    icon: '#409eff',
    source: 'builtin',
    category: 'retrieval',
    trigger: '用户提出需要领域知识的问题',
    content: '在知识库中检索相关片段，结合大模型生成答案并附溯源。',
    codeType: 'python',
    code: DEFAULT_CODE,
    inputs: '{"question": "string", "kb_id": "string"}',
    outputs: '{"answer": "string", "sources": "array"}',
    enabled: true,
    recommended: true,
    dependencies: [
      { id: 'd1', type: 'tool', name: 'Query Kb', required: true },
      { id: 'd2', type: 'model', name: 'qwen3-32b', required: true },
    ],
    scopes: [
      { id: '', name: '全局生效', enabled: true },
    ],
    usageCount: 3120,
    author: '系统',
    version: '1.3.2',
    updatedAt: '2026-06-18 08:50',
  },
]

let skillSeq = 100

/** 获取所有技能（返回副本） */
export function getSkills(): Skill[] {
  return skillStore.map((s) => ({ ...s, dependencies: s.dependencies.map((d) => ({ ...d })), scopes: s.scopes.map((sc) => ({ ...sc })) }))
}

/** 获取单个技能 */
export function getSkill(id: string): Skill | null {
  const s = skillStore.find((x) => x.id === id)
  if (!s) return null
  return { ...s, dependencies: s.dependencies.map((d) => ({ ...d })), scopes: s.scopes.map((sc) => ({ ...sc })) }
}

/** 创建技能 */
export function createSkill(form: Omit<Skill, 'id' | 'usageCount' | 'updatedAt'>): Skill {
  const now = new Date().toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-')
  const skill: Skill = {
    ...form,
    id: String(++skillSeq),
    usageCount: 0,
    updatedAt: now,
    dependencies: (form.dependencies || []).map((d) => ({ ...d })),
    scopes: (form.scopes || []).map((sc) => ({ ...sc })),
  }
  skillStore.unshift(skill)
  return { ...skill, dependencies: skill.dependencies.map((d) => ({ ...d })), scopes: skill.scopes.map((sc) => ({ ...sc })) }
}

/** 更新技能 */
export function updateSkill(id: string, patch: Partial<Skill>): Skill | null {
  const idx = skillStore.findIndex((s) => s.id === id)
  if (idx === -1) return null
  const now = new Date().toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-')
  skillStore[idx] = {
    ...skillStore[idx],
    ...patch,
    updatedAt: now,
    dependencies: patch.dependencies ? patch.dependencies.map((d) => ({ ...d })) : skillStore[idx].dependencies,
    scopes: patch.scopes ? patch.scopes.map((sc) => ({ ...sc })) : skillStore[idx].scopes,
  }
  return { ...skillStore[idx], dependencies: skillStore[idx].dependencies.map((d) => ({ ...d })), scopes: skillStore[idx].scopes.map((sc) => ({ ...sc })) }
}

/** 删除技能 */
export function deleteSkill(id: string): boolean {
  const idx = skillStore.findIndex((s) => s.id === id)
  if (idx === -1) return false
  skillStore.splice(idx, 1)
  return true
}

/** 切换启用状态 */
export function toggleSkillEnabled(id: string): Skill | null {
  const s = skillStore.find((x) => x.id === id)
  if (!s) return null
  s.enabled = !s.enabled
  return { ...s, dependencies: s.dependencies.map((d) => ({ ...d })), scopes: s.scopes.map((sc) => ({ ...sc })) }
}

/** 切换推荐状态 */
export function toggleSkillRecommended(id: string): Skill | null {
  const s = skillStore.find((x) => x.id === id)
  if (!s) return null
  s.recommended = !s.recommended
  return { ...s, dependencies: s.dependencies.map((d) => ({ ...d })), scopes: s.scopes.map((sc) => ({ ...sc })) }
}

/** 可绑定的应用 / 知识库候选项（mock） */
export const SCOPE_CANDIDATES: { id: string; name: string; type: 'app' | 'kb' }[] = [
  { id: 'app1', name: '智能问答助手', type: 'app' },
  { id: 'app2', name: '文档写作助手', type: 'app' },
  { id: 'app3', name: '客服机器人', type: 'app' },
  { id: 'kb1', name: '产品知识库', type: 'kb' },
  { id: 'kb2', name: '运维手册库', type: 'kb' },
]

/** 可添加的依赖候选项（mock） */
export const DEPENDENCY_CANDIDATES: { type: SkillDependency['type']; name: string }[] = [
  { type: 'tool', name: 'Tavily 网页搜索' },
  { type: 'tool', name: 'Query Kb' },
  { type: 'tool', name: '展示交付物' },
  { type: 'tool', name: '向用户提问' },
  { type: 'model', name: 'qwen3-32b' },
  { type: 'model', name: 'DeepSeek-V3' },
  { type: 'mcp', name: '翻译服务 MCP' },
  { type: 'mcp', name: '邮件服务 MCP' },
  { type: 'skill', name: '联网搜索' },
  { type: 'skill', name: '代码生成' },
]
