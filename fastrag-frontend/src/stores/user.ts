import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserInfo } from '@/types/user'
import * as api from '@/api'
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
    // 调用后端登出接口（忽略错误）
    api.logout().catch(() => {})
    token.value = ''
    userInfo.value = null
    storage.remove('token')
    storage.remove('userInfo')
  }

  /**
   * 真实登录：调用后端 /api/auth/login
   */
  async function login(loginUsername: string, password: string): Promise<boolean> {
    if (!loginUsername || !password) return false

    try {
      const res: any = await api.login(loginUsername, password)
      // 后端返回 { token, userInfo } 或类似结构
      if (res?.token) {
        setToken(res.token)
      }
      if (res?.userInfo) {
        setUserInfo(res.userInfo as UserInfo)
      } else {
        // 如果 login 只返回 token，再请求 userinfo
        const info: any = await api.getUserInfo()
        if (info) {
          setUserInfo(info as UserInfo)
        }
      }
      return true
    } catch {
      return false
    }
  }

  /**
   * 刷新用户信息（页面刷新后调用）
   */
  async function fetchUserInfo(): Promise<void> {
    try {
      const info: any = await api.getUserInfo()
      if (info) {
        setUserInfo(info as UserInfo)
      }
    } catch {
      // token 失效，清除登录状态
      token.value = ''
      userInfo.value = null
      storage.remove('token')
      storage.remove('userInfo')
    }
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
    fetchUserInfo,
  }
})
