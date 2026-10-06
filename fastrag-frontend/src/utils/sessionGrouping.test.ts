import { describe, it, expect } from 'vitest'
import { groupSessionsByDate, type GroupableSession } from './sessionGrouping'

// 契约锚点：docs/design/app-chat-optimize-frontend.md Step 4——
// 按 updatedAt 分桶「今天 / 近 7 天 / 更早」，置顶桶恒在最前且不受日期影响；
// now 注入以锁定今天边界 / 跨天 / 近 7 天边界三类用例。

/** now = 2026-09-25 10:30（本地时区） */
const NOW = new Date(2026, 8, 25, 10, 30, 0)

function s(id: string, updatedAt?: string, isPinned?: boolean): GroupableSession & { id: string } {
  return { id, updatedAt, isPinned }
}

describe('groupSessionsByDate', () => {
  it('今天边界：同一自然日内（含凌晨 0 点）均归「今天」', () => {
    const groups = groupSessionsByDate(
      [s('a', '2026-09-25 00:00:00'), s('b', '2026-09-25 10:29:00')],
      NOW
    )
    expect(groups).toHaveLength(1)
    expect(groups[0].key).toBe('today')
    expect(groups[0].sessions.map(x => x.id)).toEqual(['a', 'b'])
  })

  it('跨天：昨天 23:59 不入「今天」，归「近 7 天」', () => {
    const groups = groupSessionsByDate(
      [s('a', '2026-09-25 00:00:00'), s('b', '2026-09-24 23:59:59')],
      NOW
    )
    const today = groups.find(g => g.key === 'today')
    const week = groups.find(g => g.key === 'week')
    expect(today?.sessions.map(x => x.id)).toEqual(['a'])
    expect(week?.sessions.map(x => x.id)).toEqual(['b'])
  })

  it('近 7 天边界：第 6 天前入桶、第 7 天前的归「更早」', () => {
    const groups = groupSessionsByDate(
      [s('in', '2026-09-19 00:00:00'), s('out', '2026-09-18 23:59:59')],
      NOW
    )
    const week = groups.find(g => g.key === 'week')
    const earlier = groups.find(g => g.key === 'earlier')
    expect(week?.sessions.map(x => x.id)).toEqual(['in'])
    expect(earlier?.sessions.map(x => x.id)).toEqual(['out'])
  })

  it('置顶不入日期桶：任意 updatedAt 均进「置顶」且分节最前', () => {
    const groups = groupSessionsByDate(
      [
        s('old', '2025-01-01 08:00:00'),
        s('pin-old', '2024-06-01 08:00:00', true),
        s('fresh', '2026-09-25 09:00:00'),
        s('pin-new', '2026-09-25 09:30:00', true),
      ],
      NOW
    )
    expect(groups.map(g => g.key)).toEqual(['pinned', 'today', 'earlier'])
    expect(groups[0].label).toBe('置顶')
    expect(groups[0].sessions.map(x => x.id)).toEqual(['pin-old', 'pin-new'])
  })

  it('updatedAt 缺失或不可解析归「更早」；空列表输出空数组', () => {
    const groups = groupSessionsByDate([s('n1'), s('bad', 'not-a-date')], NOW)
    expect(groups.map(g => g.key)).toEqual(['earlier'])
    expect(groups[0].sessions.map(x => x.id)).toEqual(['n1', 'bad'])
    expect(groupSessionsByDate([], NOW)).toEqual([])
  })

  it('ISO 带时区格式可解析；空桶不输出分节头', () => {
    const groups = groupSessionsByDate(
      [s('iso', '2026-09-25T09:00:00+08:00'), s('old', '2025-01-01T00:00:00Z')],
      NOW
    )
    expect(groups.map(g => g.key)).toEqual(['today', 'earlier'])
  })
})
