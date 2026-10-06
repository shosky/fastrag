// ===========================================================================
// 会话列表日期分桶（app-chat-optimize Step 4，对齐 WeKnora menu.vue:117-185）
//
// 纯函数、无 DOM 依赖（node 环境可测）；now 可注入以便边界测试。
// 桶序固定：置顶（不受日期影响，恒在最前）→ 今天 → 近 7 天 → 更早；
// 空桶不输出。updatedAt 缺失或不可解析归入「更早」。
// ===========================================================================

/** 分桶键（label 面向 UI 的分节头文案） */
export type SessionGroupKey = 'pinned' | 'today' | 'week' | 'earlier'

export interface SessionGroup<T> {
  key: SessionGroupKey
  label: string
  sessions: T[]
}

/** 分桶元素的最小形状（ConversationSummary 的结构子集） */
export interface GroupableSession {
  updatedAt?: string
  isPinned?: boolean
}

const DAY_MS = 24 * 60 * 60 * 1000

const GROUP_LABELS: Record<SessionGroupKey, string> = {
  pinned: '置顶',
  today: '今天',
  week: '近 7 天',
  earlier: '更早',
}

const GROUP_ORDER: SessionGroupKey[] = ['pinned', 'today', 'week', 'earlier']

/** 解析 updatedAt（兼容 ISO 与 'YYYY-MM-DD HH:mm:ss' 两种常见后端格式；本地时区） */
function parseTime(value?: string): number | null {
  if (!value) return null
  const normalized = value.includes(' ') && !value.includes('T') ? value.replace(' ', 'T') : value
  const t = new Date(normalized).getTime()
  return Number.isNaN(t) ? null : t
}

/**
 * 按 updatedAt 将会话分桶：今天 / 近 7 天 / 更早；置顶会话独立成桶恒在最前。
 * @param sessions 会话列表（保持入参相对顺序输出）
 * @param now 当前时间（默认取系统时间；测试可注入固定值）
 * @returns 非空分桶数组，按 pinned → today → week → earlier 排序
 */
export function groupSessionsByDate<T extends GroupableSession>(
  sessions: T[],
  now: Date = new Date()
): SessionGroup<T>[] {
  const buckets = new Map<SessionGroupKey, T[]>()

  const put = (key: SessionGroupKey, item: T) => {
    const list = buckets.get(key)
    if (list) list.push(item)
    else buckets.set(key, [item])
  }

  // 以「本地日历日」为界：今天 = 与 now 同一自然日；近 7 天 = 今天 0 点往前推 6 个自然日起（不含今天）
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  const weekStart = startOfToday - 6 * DAY_MS

  for (const session of sessions || []) {
    if (session.isPinned) {
      put('pinned', session)
      continue
    }
    const t = parseTime(session.updatedAt)
    if (t === null) put('earlier', session)
    else if (t >= startOfToday) put('today', session)
    else if (t >= weekStart) put('week', session)
    else put('earlier', session)
  }

  return GROUP_ORDER.filter(key => buckets.has(key)).map(key => ({
    key,
    label: GROUP_LABELS[key],
    sessions: buckets.get(key)!,
  }))
}
