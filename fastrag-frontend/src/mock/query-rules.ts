// ===========================================================================
// 查询重写/扩写规则 mock 数据层
//
// 重写规则：把口语/简写转成规范术语（如"wifi怎么装" → "WiFi 组网 安装"）
// 扩写规则：在 query 基础上追加上下文（如"价格" → "价格 报价 费用 方案"）
// ===========================================================================

/** 规则类型 */
export type RuleType = 'rewrite' | 'expand'

/** 规则状态 */
export type RuleStatus = 'enabled' | 'disabled'

/** 查询规则 */
export interface QueryRule {
  id: string
  name: string
  type: RuleType
  status: RuleStatus
  /** 匹配模式（正则或关键词） */
  pattern: string
  /** 替换/扩写结果 */
  replacement: string
  /** 优先级（数字越小越优先） */
  priority: number
  createdAt: string
}

const ruleStore: QueryRule[] = []
let ruleSeq = 100

function now(): string {
  return new Date().toLocaleString('zh-CN')
}

function seedRules(): QueryRule[] {
  return [
    // 重写规则：口语 → 规范术语
    { id: 'r_1', name: 'wifi→WiFi组网', type: 'rewrite', status: 'enabled', pattern: 'wifi', replacement: 'WiFi 组网', priority: 1, createdAt: '2026-06-01 10:00' },
    { id: 'r_2', name: '装监控→视频监控安装', type: 'rewrite', status: 'enabled', pattern: '装监控', replacement: '视频监控 安装', priority: 1, createdAt: '2026-06-01 10:01' },
    { id: 'r_3', name: '拉网线→综合布线', type: 'rewrite', status: 'enabled', pattern: '拉网线', replacement: '综合布线', priority: 1, createdAt: '2026-06-01 10:02' },
    { id: 'r_4', name: '多少钱→价格报价', type: 'rewrite', status: 'enabled', pattern: '多少钱', replacement: '价格 报价 费用', priority: 2, createdAt: '2026-06-02 09:00' },
    { id: 'r_5', name: '咋办→办理流程', type: 'rewrite', status: 'enabled', pattern: '咋办', replacement: '办理流程', priority: 2, createdAt: '2026-06-02 09:01' },
    { id: 'r_10', name: '总结小微ICT→帮忙总结内容', type: 'rewrite', status: 'enabled', pattern: '总结小微ICT', replacement: '帮忙总结小微ICT的内容', priority: 1, createdAt: '2026-06-29 12:00' },
    // 扩写规则：追加上下文词
    { id: 'r_6', name: '价格扩写', type: 'expand', status: 'enabled', pattern: '价格', replacement: '价格 报价 费用 方案 套餐', priority: 3, createdAt: '2026-06-03 14:00' },
    { id: 'r_7', name: '安装扩写', type: 'expand', status: 'enabled', pattern: '安装', replacement: '安装 部署 施工 调试', priority: 3, createdAt: '2026-06-03 14:01' },
    { id: 'r_8', name: '故障扩写', type: 'expand', status: 'enabled', pattern: '故障', replacement: '故障 问题 报修 维修', priority: 3, createdAt: '2026-06-03 14:02' },
    { id: 'r_9', name: '合同扩写', type: 'expand', status: 'disabled', pattern: '合同', replacement: '合同 协议 条款 签约', priority: 4, createdAt: '2026-06-04 10:00' },
  ]
}

function getStore(): QueryRule[] {
  if (ruleStore.length === 0) ruleStore.push(...seedRules())
  return ruleStore
}

// --- CRUD ---
export function getQueryRules(type?: RuleType): QueryRule[] {
  const list = type ? getStore().filter((r) => r.type === type) : getStore()
  return [...list].sort((a, b) => a.priority - b.priority).map((r) => ({ ...r }))
}

export function createQueryRule(form: Omit<QueryRule, 'id' | 'createdAt'>): QueryRule {
  const rule: QueryRule = { ...form, id: `r_${++ruleSeq}`, createdAt: now() }
  getStore().push(rule)
  return { ...rule }
}

export function updateQueryRule(id: string, patch: Partial<QueryRule>): QueryRule | null {
  const store = getStore()
  const idx = store.findIndex((r) => r.id === id)
  if (idx === -1) return null
  store[idx] = { ...store[idx], ...patch }
  return { ...store[idx] }
}

export function deleteQueryRule(id: string): boolean {
  const store = getStore()
  const idx = store.findIndex((r) => r.id === id)
  if (idx === -1) return false
  store.splice(idx, 1)
  return true
}

export function toggleRuleStatus(id: string): QueryRule | null {
  const rule = getStore().find((r) => r.id === id)
  if (!rule) return null
  rule.status = rule.status === 'enabled' ? 'disabled' : 'enabled'
  return { ...rule }
}

// --- 规则应用 ---

/**
 * 对 query 应用查询重写/扩写规则。
 * 1. 按优先级依次应用 rewrite 规则（替换匹配的关键词）
 * 2. 对结果应用 expand 规则（追加扩写词）
 */
export function applyQueryRules(query: string): {
  rewritten: string
  appliedRules: string[]
} {
  const enabledRules = getStore().filter((r) => r.status === 'enabled')
  const rewrites = enabledRules.filter((r) => r.type === 'rewrite').sort((a, b) => a.priority - b.priority)
  const expands = enabledRules.filter((r) => r.type === 'expand').sort((a, b) => a.priority - b.priority)

  let result = query
  const appliedRules: string[] = []

  // 应用重写规则
  for (const rule of rewrites) {
    if (result.includes(rule.pattern)) {
      result = result.replace(rule.pattern, rule.replacement)
      appliedRules.push(`重写：${rule.name}`)
    }
  }

  // 应用扩写规则（追加不重复的词）
  const addedWords = new Set<string>()
  for (const rule of expands) {
    if (result.includes(rule.pattern)) {
      const words = rule.replacement.split(/\s+/)
      words.forEach((w) => {
        if (!result.includes(w) && !addedWords.has(w)) {
          addedWords.add(w)
        }
      })
      appliedRules.push(`扩写：${rule.name}`)
    }
  }
  if (addedWords.size > 0) {
    result += ' ' + Array.from(addedWords).join(' ')
  }

  return { rewritten: result, appliedRules }
}
