import type { RoleMeta, SystemRole } from '@/types/auth'
import { ROLE_PERMISSIONS, ROLE_LABELS } from '@/types/auth'

// ===========================================================================
// 角色 + 人员 mock 数据层
// ===========================================================================

/** 角色列表（模拟后端持久化） */
const roleStore: RoleMeta[] = [
  {
    id: '1',
    key: 'super_admin',
    name: ROLE_LABELS.super_admin,
    description: '拥有系统所有权限',
    permissions: ROLE_PERMISSIONS.super_admin,
    isDefault: true,
    createdAt: '2026-01-01',
    updatedAt: '2026-01-01',
  },
  {
    id: '2',
    key: 'kb_admin',
    name: ROLE_LABELS.kb_admin,
    description: '可管理所有知识库和部分管理功能',
    permissions: ROLE_PERMISSIONS.kb_admin,
    isDefault: false,
    createdAt: '2026-01-15',
    updatedAt: '2026-01-15',
  },
  {
    id: '3',
    key: 'kb_user',
    name: ROLE_LABELS.kb_user,
    description: '可操作被授权的知识库和应用',
    permissions: ROLE_PERMISSIONS.kb_user,
    isDefault: false,
    createdAt: '2026-02-01',
    updatedAt: '2026-02-01',
  },
  {
    id: '4',
    key: 'readonly',
    name: ROLE_LABELS.readonly,
    description: '只能查看和检索，不能编辑',
    permissions: ROLE_PERMISSIONS.readonly,
    isDefault: false,
    createdAt: '2026-03-01',
    updatedAt: '2026-03-01',
  },
]

let roleSeq = 100

/** 获取所有角色 */
export function getRoles(): RoleMeta[] {
  return roleStore.map((r) => ({ ...r, permissions: [...r.permissions] }))
}

/** 获取单个角色 */
export function getRole(id: string): RoleMeta | null {
  return roleStore.find((r) => r.id === id) || null
}

/** 创建角色 */
export function createRole(form: { name: string; description: string; permissions: string[] }): RoleMeta {
  const role: RoleMeta = {
    id: String(++roleSeq),
    key: `custom_${roleSeq}` as SystemRole,
    name: form.name,
    description: form.description,
    permissions: [...form.permissions],
    isDefault: false,
    createdAt: new Date().toLocaleString('zh-CN'),
    updatedAt: new Date().toLocaleString('zh-CN'),
  }
  roleStore.push(role)
  return { ...role, permissions: [...role.permissions] }
}

/** 更新角色（名称/描述/权限） */
export function updateRole(id: string, form: { name: string; description: string; permissions: string[] }): RoleMeta | null {
  const idx = roleStore.findIndex((r) => r.id === id)
  if (idx === -1) return null
  roleStore[idx] = {
    ...roleStore[idx],
    name: form.name,
    description: form.description,
    permissions: [...form.permissions],
    updatedAt: new Date().toLocaleString('zh-CN'),
  }
  return { ...roleStore[idx], permissions: [...roleStore[idx].permissions] }
}

/** 删除角色（默认角色不可删） */
export function deleteRole(id: string): { success: boolean; message?: string } {
  const target = roleStore.find((r) => r.id === id)
  if (!target) return { success: false, message: '角色不存在' }
  if (target.isDefault) return { success: false, message: '默认角色不可删除' }
  const idx = roleStore.indexOf(target)
  roleStore.splice(idx, 1)
  return { success: true }
}

/** 设为默认角色 */
export function setDefaultRole(id: string): void {
  roleStore.forEach((r) => {
    r.isDefault = r.id === id
  })
}

// ===========================================================================
// 人员 mock 数据
// ===========================================================================

export interface PersonnelRecord {
  id: string
  username: string
  realName: string
  phone: string
  email: string
  orgName: string
  roleId: string
  roleName: string
  status: 'enabled' | 'disabled'
  createdAt: string
}

const personnelStore: PersonnelRecord[] = [
  { id: '1', username: 'admin', realName: '超级管理员', phone: '13800000001', email: 'admin@example.com', orgName: '公司总部', roleId: '1', roleName: '超级管理员', status: 'enabled', createdAt: '2026-01-01' },
  { id: '2', username: 'zhangsan', realName: '张三', phone: '13800000002', email: 'zhangsan@example.com', orgName: '技术部/前端组', roleId: '3', roleName: '知识库用户', status: 'enabled', createdAt: '2026-02-01' },
  { id: '3', username: 'lisi', realName: '李四', phone: '13800000003', email: 'lisi@example.com', orgName: '产品部', roleId: '2', roleName: '知识库管理员', status: 'enabled', createdAt: '2026-02-15' },
  { id: '4', username: 'wangwu', realName: '王五', phone: '13800000004', email: 'wangwu@example.com', orgName: '外部', roleId: '4', roleName: '只读用户', status: 'disabled', createdAt: '2026-03-01' },
]

let personnelSeq = 100

export function getPersonnel(): PersonnelRecord[] {
  return personnelStore.map((p) => ({ ...p }))
}

export function createPersonnel(form: Omit<PersonnelRecord, 'id' | 'createdAt'>): PersonnelRecord {
  const record: PersonnelRecord = { ...form, id: String(++personnelSeq), createdAt: new Date().toLocaleString('zh-CN') }
  personnelStore.push(record)
  return { ...record }
}

export function updatePersonnel(id: string, patch: Partial<PersonnelRecord>): PersonnelRecord | null {
  const idx = personnelStore.findIndex((p) => p.id === id)
  if (idx === -1) return null
  personnelStore[idx] = { ...personnelStore[idx], ...patch }
  return { ...personnelStore[idx] }
}

export function assignRole(personnelId: string, roleId: string, roleName: string): boolean {
  const idx = personnelStore.findIndex((p) => p.id === personnelId)
  if (idx === -1) return false
  personnelStore[idx].roleId = roleId
  personnelStore[idx].roleName = roleName
  return true
}

/** 根据用户名查找人员（登录用） */
export function findByUsername(username: string): PersonnelRecord | null {
  return personnelStore.find((p) => p.username === username) || null
}
