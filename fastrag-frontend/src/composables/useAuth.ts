import { computed, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useUserStore } from '@/stores/user'
import type { SystemRole, Permission, KBRole } from '@/types/auth'
import { ROLE_PERMISSIONS, KB_ROLE_PERMISSIONS } from '@/types/auth'
import * as api from '@/api'

export function useAuth() {
  const userStore = useUserStore()
  const { roles, permissions, userInfo } = storeToRefs(userStore)

  // KB 角色缓存（运行时填充，避免同步 mock）
  const kbRoleCache = ref<Record<string, KBRole>>({})

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
   *       否则 → 检查全局权限 + KB ACL（优先走缓存，异步更新）
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

    // ACL 检查（优先使用缓存；缓存未命中时返回 true 避免误拦截，同时异步刷新）
    const cached = kbRoleCache.value[kbId]
    if (cached) {
      return KB_ROLE_PERMISSIONS[cached]?.includes(perm) || false
    }
    // 异步刷新 KB 角色（不阻塞当前渲染）
    refreshKbRole(kbId)
    return true
  }

  /**
   * 获取当前用户在某知识库的角色（优先走缓存）
   */
  function getMyKBRole(kbId: string): KBRole | null {
    return kbRoleCache.value[kbId] || null
  }

  /**
   * 异步刷新 KB 角色缓存
   */
  async function refreshKbRole(kbId: string): Promise<void> {
    const userId = userInfo.value?.id || ''
    if (!userId) return
    try {
      const res = await api.getUserKbRole(userId, kbId)
      const role = (res as any)?.kbRole || (res as any)?.role || 'viewer'
      kbRoleCache.value[kbId] = role as KBRole
    } catch {
      // 接口不可用时给一个兜底角色
      kbRoleCache.value[kbId] = 'viewer'
    }
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
    refreshKbRole,
  }
}
