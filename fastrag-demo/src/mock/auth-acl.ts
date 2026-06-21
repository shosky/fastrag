import type { KBAclEntry, KBRole } from '@/types/auth'

// ===========================================================================
// 知识库 ACL mock 数据层
//
// 管理"哪个用户对哪个知识库有什么角色"。
// 全局共享用 userId='*' 表示。
// ===========================================================================

const aclStore: KBAclEntry[] = [
  // 知识库 1（产品知识库）：admin 是 owner，张三是 editor，所有人可看
  { kbId: '1', userId: '*', kbRole: 'viewer', grantedBy: '1', grantedAt: '2026-01-15' },
  { kbId: '1', userId: '1', kbRole: 'owner', grantedBy: '1', grantedAt: '2026-01-15' },
  { kbId: '1', userId: '2', kbRole: 'editor', grantedBy: '1', grantedAt: '2026-02-01' },
  // 知识库 2（技术知识库）：张三是 owner，李四是 editor
  { kbId: '2', userId: '2', kbRole: 'owner', grantedBy: '1', grantedAt: '2026-02-01' },
  { kbId: '2', userId: '3', kbRole: 'editor', grantedBy: '2', grantedAt: '2026-02-15' },
  // 知识库 3（客户案例库）：李四是 owner
  { kbId: '3', userId: '3', kbRole: 'owner', grantedBy: '1', grantedAt: '2026-02-15' },
  // 知识库 5（个人笔记）：admin 是 owner，不共享
  { kbId: '5', userId: '1', kbRole: 'owner', grantedBy: '1', grantedAt: '2026-03-01' },
]

/** 获取某知识库的 ACL 列表 */
export function getKBAcl(kbId: string): KBAclEntry[] {
  return aclStore.filter((e) => e.kbId === kbId).map((e) => ({ ...e }))
}

/** 获取某用户在某知识库的角色 */
export function getKBRole(userId: string, kbId: string): KBRole | null {
  // 先查精确匹配
  const exact = aclStore.find((e) => e.kbId === kbId && e.userId === userId)
  if (exact) return exact.kbRole
  // 再查通配符（全局共享）
  const wildcard = aclStore.find((e) => e.kbId === kbId && e.userId === '*')
  return wildcard ? wildcard.kbRole : null
}

/** 获取某用户可访问的所有知识库 ID */
export function getAccessibleKBIds(userId: string): string[] {
  const ids = new Set<string>()
  aclStore.forEach((e) => {
    if (e.userId === userId || e.userId === '*') {
      ids.add(e.kbId)
    }
  })
  return Array.from(ids)
}

/** 设置知识库 ACL（覆盖） */
export function setKBAcl(kbId: string, entries: Omit<KBAclEntry, 'grantedAt'>[]): void {
  // 删除该知识库的旧条目
  for (let i = aclStore.length - 1; i >= 0; i--) {
    if (aclStore[i].kbId === kbId) aclStore.splice(i, 1)
  }
  const now = new Date().toLocaleString('zh-CN')
  entries.forEach((e) => {
    aclStore.push({ ...e, grantedAt: now })
  })
}

/** 添加单条 ACL */
export function addAclEntry(entry: Omit<KBAclEntry, 'grantedAt'>): void {
  // 避免重复
  const exists = aclStore.find(
    (e) => e.kbId === entry.kbId && e.userId === entry.userId && e.kbRole === entry.kbRole,
  )
  if (!exists) {
    aclStore.push({ ...entry, grantedAt: new Date().toLocaleString('zh-CN') })
  }
}

/** 移除 ACL 条目 */
export function removeAclEntry(kbId: string, userId: string): void {
  const idx = aclStore.findIndex((e) => e.kbId === kbId && e.userId === userId)
  if (idx !== -1) aclStore.splice(idx, 1)
}
