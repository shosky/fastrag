import { describe, it, expect } from 'vitest'
import {
  PUBLISH_STATUS_LABELS,
  PUBLISH_STATUS_COLORS,
  PUBLISH_TRANSITIONS,
} from './audit'

// 契约锚点：docs/contract/ui.md 特性 12（publish-review）状态机
// 真源：src/types/audit.ts PUBLISH_TRANSITIONS（PublishPanel 行操作按此渲染）

const ALL_STATUSES = ['draft', 'pending_review', 'approved', 'published', 'rejected'] as const

describe('发布状态机 PUBLISH_TRANSITIONS', () => {
  it('五种状态均有标签与颜色映射', () => {
    for (const s of ALL_STATUSES) {
      expect(PUBLISH_STATUS_LABELS[s], `缺少 ${s} 的标签`).toBeTruthy()
      expect(PUBLISH_STATUS_COLORS[s], `缺少 ${s} 的颜色`).toBeTruthy()
    }
  })

  it('流转表覆盖全部状态且目标状态合法', () => {
    for (const s of ALL_STATUSES) {
      expect(PUBLISH_TRANSITIONS[s], `缺少 ${s} 的流转定义`).toBeDefined()
      for (const target of PUBLISH_TRANSITIONS[s]) {
        expect(ALL_STATUSES, `${s} → ${target} 的目标状态非法`).toContain(target)
      }
    }
  })

  it('核心流转链：draft→pending_review→approved→published→draft', () => {
    expect(PUBLISH_TRANSITIONS.draft).toContain('pending_review')
    expect(PUBLISH_TRANSITIONS.pending_review).toContain('approved')
    expect(PUBLISH_TRANSITIONS.pending_review).toContain('rejected')
    expect(PUBLISH_TRANSITIONS.approved).toContain('published')
    expect(PUBLISH_TRANSITIONS.published).toContain('draft')
    expect(PUBLISH_TRANSITIONS.rejected).toContain('draft')
  })

  it('不可跨越审核（draft 不可直达 published/approved）', () => {
    expect(PUBLISH_TRANSITIONS.draft).not.toContain('published')
    expect(PUBLISH_TRANSITIONS.draft).not.toContain('approved')
    expect(PUBLISH_TRANSITIONS.approved).not.toContain('draft')
  })
})
