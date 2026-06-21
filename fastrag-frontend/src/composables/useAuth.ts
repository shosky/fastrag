import { computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useUserStore } from '@/stores/user'
import type { SystemRole, Permission, KBRole } from '@/types/auth'
import { ROLE_PERMISSIONS, KB_ROLE_PERMISSIONS } from '@/types/auth'
import { getKBRole as mockGetKBRole } from '@/mock/auth-acl'

export function useAuth() {
  const userStore = useUserStore()
  const { roles, permissions, userInfo } = storeToRefs(userStore)

  /** 当前用户的最高角色 */
  const currentRole = computed<SystemRole>(() => {
    if (roles.value.includes('super_admin')) return 'super_admin'
    if (roles.value.includes('kb_admin')) return 'kb_admin'
    if (roles.value.includes('kb_user')) return 'kb_user'
    return 'readonly'
  })

  /** 是否为超级管理员 */
  const isSuperAdmin = computed(() => roles.value.includes('super_admin'))

  /**
   * 全局角色判断
   * 超级管理员自动拥有所有角色的权限
   */
  function hasRole(role: SystemRole): boolean {
    if (roles.value.includes('super_admin')) return true
    return roles.value.includes(role)
  }

  /**
   * 全局权限判断（不涉及具体知识库）
   * '*' 通配符表示全部权限
   */
  function hasPermission(perm: Permission): boolean {
    if (permissions.value.includes('*')) return true
    return permissions.value.includes(perm)
  }

  /**
   * 知识库级权限判断
   * 逻辑：超级管理员/知识库管理员 → 所有 KB 都有权限
   *       否则 → 检查全局权限 + KB ACL
   */
  function hasKBPermission(perm: Permission, kbId: string): boolean {
    // 超级管理员/知识库管理员：跳过 ACL 检查
    if (roles.value.includes('super_admin') || roles.value.includes('kb_admin')) {
      return permissions.value.includes('*') || permissions.value.includes(perm)
    }

    // 全局权限检查
    if (!permissions.value.includes('*') && !permissions.value.includes(perm)) {
      return false
    }

    // ACL 检查
    const userId = userInfo.value?.id || ''
    const kbRole = mockGetKBRole(userId, kbId)
    if (!kbRole) return false

    return KB_ROLE_PERMISSIONS[kbRole]?.includes(perm) || false
  }

  /**
   * 获取当前用户在某知识库的角色
   */
  function getMyKBRole(kbId: string): KBRole | null {
    const userId = userInfo.value?.id || ''
    return mockGetKBRole(userId, kbId)
  }

  return {
    currentRole,
    isSuperAdmin,
    roles,
    permissions,
    hasRole,
    hasPermission,
    hasKBPermission,
    getMyKBRole,
  }
}
