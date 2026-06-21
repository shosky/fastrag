import type { PersonnelRecord } from './auth-roles'
import { getPersonnel } from './auth-roles'

// ===========================================================================
// 组织架构 mock 数据层
//
// 统一组织树数据，organization.vue / personnel.vue / form.vue 共享。
// ===========================================================================

export interface OrgNode {
  id: string
  name: string
  alias?: string
  parentId?: string
  level: number
  memberCount: number
  relatedCount: number
  children?: OrgNode[]
}

const orgTree: OrgNode[] = [
  {
    id: '1',
    name: '公司总部',
    level: 1,
    memberCount: 0,
    relatedCount: 0,
    children: [
      {
        id: '1-1',
        name: '技术部',
        parentId: '1',
        level: 2,
        memberCount: 0,
        relatedCount: 0,
        children: [
          { id: '1-1-1', name: '前端组', parentId: '1-1', level: 3, memberCount: 0, relatedCount: 0 },
          { id: '1-1-2', name: '后端组', parentId: '1-1', level: 3, memberCount: 0, relatedCount: 0 },
          { id: '1-1-3', name: '测试组', parentId: '1-1', level: 3, memberCount: 0, relatedCount: 0 },
        ],
      },
      { id: '1-2', name: '产品部', parentId: '1', level: 2, memberCount: 0, relatedCount: 0 },
      { id: '1-3', name: '运营部', parentId: '1', level: 2, memberCount: 0, relatedCount: 0 },
      { id: '1-4', name: '市场部', parentId: '1', level: 2, memberCount: 0, relatedCount: 0 },
    ],
  },
]

let orgSeq = 100

/** 计算每个部门的人员数（从 personnel mock 动态统计） */
function refreshMemberCounts(): void {
  const personnel = getPersonnel()
  const countMap = new Map<string, number>()

  // 统计直接匹配
  personnel.forEach((p) => {
    if (p.status !== 'enabled') return
    const parts = p.orgName.split('/')
    // 完整路径匹配（如 "技术部/前端组"）
    countMap.set(p.orgName, (countMap.get(p.orgName) || 0) + 1)
    // 也统计上级部门
    parts.forEach((part) => {
      countMap.set(part, (countMap.get(part) || 0) + 1)
    })
  })

  function applyCounts(nodes: OrgNode[]): void {
    nodes.forEach((node) => {
      node.memberCount = countMap.get(node.name) || 0
      node.relatedCount = node.memberCount
      if (node.children) applyCounts(node.children)
    })
  }

  applyCounts(orgTree)
}

/** 获取组织树（深拷贝，带人员统计） */
export function getOrgTree(): OrgNode[] {
  refreshMemberCounts()
  return JSON.parse(JSON.stringify(orgTree))
}

/** 获取扁平化的部门列表（用于下拉选择） */
export function getFlatOrgList(): { id: string; name: string; path: string; level: number }[] {
  const result: { id: string; name: string; path: string; level: number }[] = []

  function walk(nodes: OrgNode[], parentPath: string): void {
    nodes.forEach((node) => {
      const path = parentPath ? `${parentPath}/${node.name}` : node.name
      result.push({ id: node.id, name: node.name, path, level: node.level })
      if (node.children) walk(node.children, path)
    })
  }

  walk(orgTree, '')
  return result
}

/** 获取所有叶子部门的名称列表（用于人员表单的部门下拉） */
export function getDepartmentNames(): string[] {
  return getFlatOrgList().map((o) => o.name)
}

/**
 * 获取部门及其所有子部门的成员列表
 * 匹配规则：人员的 orgName 路径包含部门名（或其任意祖先部门名）即视为该部门成员
 */
export function getDepartmentMembers(deptId: string): PersonnelRecord[] {
  const node = findNode(orgTree, deptId)
  if (!node) return []

  // 收集该部门及其所有后代部门的名字
  const names = new Set<string>()
  function collectNames(n: OrgNode): void {
    names.add(n.name)
    n.children?.forEach(collectNames)
  }
  collectNames(node)

  // 人员的 orgName 形如 "技术部/前端组"，按 '/' 拆分后任一段命中即属于该部门树
  return getPersonnel().filter((p) => {
    if (p.status !== 'enabled') return false
    const parts = p.orgName.split('/')
    return parts.some((part) => names.has(part))
  })
}

/** 递归查找节点 */
function findNode(nodes: OrgNode[], id: string): OrgNode | null {
  for (const node of nodes) {
    if (node.id === id) return node
    if (node.children) {
      const found = findNode(node.children, id)
      if (found) return found
    }
  }
  return null
}

/** 新增组织 */
export function createOrg(form: { name: string; alias?: string; parentId?: string }): OrgNode {
  const parent = form.parentId ? findNode(orgTree, form.parentId) : null
  const level = parent ? parent.level + 1 : 1
  const newOrg: OrgNode = {
    id: String(++orgSeq),
    name: form.name,
    alias: form.alias,
    parentId: form.parentId,
    level,
    memberCount: 0,
    relatedCount: 0,
  }
  if (parent) {
    parent.children = parent.children || []
    parent.children.push(newOrg)
  } else {
    orgTree.push(newOrg)
  }
  return { ...newOrg }
}

/** 编辑组织 */
export function updateOrg(id: string, form: { name: string; alias?: string }): boolean {
  const node = findNode(orgTree, id)
  if (!node) return false
  node.name = form.name
  node.alias = form.alias
  return true
}

/** 删除组织（不可删除有子节点的组织） */
export function deleteOrg(id: string): { success: boolean; message?: string } {
  const node = findNode(orgTree, id)
  if (!node) return { success: false, message: '组织不存在' }
  if (node.children && node.children.length > 0) {
    return { success: false, message: '该组织下有子组织，不可删除' }
  }

  function removeFrom(nodes: OrgNode[]): boolean {
    const idx = nodes.findIndex((n) => n.id === id)
    if (idx !== -1) {
      nodes.splice(idx, 1)
      return true
    }
    for (const n of nodes) {
      if (n.children && removeFrom(n.children)) return true
    }
    return false
  }

  removeFrom(orgTree)
  return { success: true }
}
