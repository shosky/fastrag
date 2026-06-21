import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserInfo } from '@/types/user'
import type { SystemRole } from '@/types/auth'
import { ROLE_PERMISSIONS, ROLE_LABELS } from '@/types/auth'
import { findByUsername } from '@/mock/auth-roles'
import { getRole } from '@/mock/auth-roles'
import { storage } from '@/utils/storage'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(storage.get('token') || '')
  // 页面刷新后从 localStorage 恢复 userInfo（否则侧边栏权限过滤失效）
  const savedUserInfo = storage.get('userInfo')
  const userInfo = ref<UserInfo | null>(savedUserInfo ? JSON.parse(savedUserInfo) : null)

  const isLoggedIn = computed(() => !!token.value)
  const username = computed(() => userInfo.value?.realName || userInfo.value?.username || '')
  const roles = computed(() => userInfo.value?.roles || [])
  const permissions = computed(() => userInfo.value?.permissions || [])
  const isSuperAdmin = computed(() => roles.value.includes('super_admin'))

  function setToken(newToken: string) {
    token.value = newToken
    storage.set('token', newToken)
  }

  function setUserInfo(info: UserInfo) {
    userInfo.value = info
    // 持久化到 localStorage，供路由守卫读取角色信息
    storage.set('userInfo', JSON.stringify(info))
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    storage.remove('token')
    storage.remove('userInfo')
  }

  /**
   * 模拟登录
   * 从人员 mock 中查找用户，分配真实角色和权限。
   * 未注册用户默认为 readonly 角色。
   */
  async function login(loginUsername: string, password: string): Promise<boolean> {
    if (!loginUsername || !password) return false

    const mockToken = 'mock_token_' + Date.now()
    setToken(mockToken)

    // 从人员表查找
    const person = findByUsername(loginUsername)

    if (person) {
      // 已注册用户：使用其真实角色
      const roleData = getRole(person.roleId)
      const roleKey = (roleData?.key || 'readonly') as SystemRole
      const perms = roleData?.permissions || ROLE_PERMISSIONS.readonly

      setUserInfo({
        id: person.id,
        username: person.username,
        realName: person.realName,
        roles: [roleKey],
        permissions: [...perms],
        phone: person.phone,
        email: person.email,
      })
    } else {
      // 未注册用户：默认只读
      setUserInfo({
        id: '999',
        username: loginUsername,
        realName: loginUsername,
        roles: ['readonly'],
        permissions: [...ROLE_PERMISSIONS.readonly],
        phone: '',
        email: `${loginUsername}@example.com`,
      })
    }

    return true
  }

  return {
    token,
    userInfo,
    isLoggedIn,
    username,
    roles,
    permissions,
    isSuperAdmin,
    setToken,
    setUserInfo,
    logout,
    login,
  }
})
