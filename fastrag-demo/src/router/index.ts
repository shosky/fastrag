import { createRouter, createWebHistory } from 'vue-router'
import routes from './routes'
import { storage } from '@/utils/storage'
import type { SystemRole } from '@/types/auth'
import { MENU_PERMISSION_MAP, ROLE_PERMISSIONS } from '@/types/auth'
import type { MenuPermission } from '@/types/auth'

const router = createRouter({
  history: createWebHistory(),
  routes,
})

/**
 * 从 MENU_PERMISSION_MAP 中查找路由路径对应的所需权限。
 */
function findRoutePerms(path: string, map: MenuPermission[] = MENU_PERMISSION_MAP): string[] {
  for (const item of map) {
    if (item.path === path) return item.requiredPerms
    if (item.children) {
      const found = findRoutePerms(path, item.children)
      if (found.length > 0) return found
    }
  }
  return []
}

/**
 * 检查用户是否有指定权限。
 * 从 localStorage 读取（store 可能还未初始化）。
 */
function userHasPermission(perm: string): boolean {
  try {
    const userInfoStr = storage.get('userInfo')
    if (!userInfoStr) return false
    const userInfo = JSON.parse(userInfoStr)
    const userRoles: string[] = userInfo.roles || []
    const userPerms: string[] = userInfo.permissions || []

    // 通配符
    if (userPerms.includes('*')) return true

    // 直接权限
    if (userPerms.includes(perm)) return true

    // 角色继承的权限
    for (const role of userRoles) {
      if (role === 'super_admin') return true
      const rolePerms = ROLE_PERMISSIONS[role as SystemRole] || []
      if (rolePerms.includes('*') || rolePerms.includes(perm)) return true
    }
  } catch {
    // 解析失败
  }
  return false
}

// 路由守卫
router.beforeEach((to, _from, next) => {
  // 设置页面标题
  const title = to.meta.title as string
  document.title = title ? `${title} - AIS` : 'AIS'

  const token = storage.get('token')

  if (to.meta.requiresAuth === false) {
    // 不需要认证的页面
    if (token && to.path === '/login') {
      next('/home')
    } else {
      next()
    }
    return
  }

  // 需要认证的页面
  if (!token) {
    next('/login')
    return
  }

  // 角色检查（原有逻辑）
  const requiredRoles = to.meta.roles as SystemRole[] | undefined
  if (requiredRoles && requiredRoles.length > 0) {
    try {
      const userInfoStr = storage.get('userInfo')
      if (userInfoStr) {
        const userInfo = JSON.parse(userInfoStr)
        const userRoles: string[] = userInfo.roles || []
        const hasAccess = requiredRoles.some((r) => userRoles.includes(r) || userRoles.includes('super_admin'))
        if (!hasAccess) {
          next('/403')
          return
        }
      }
    } catch {
      // 解析失败，放行（由 store 层兜底）
    }
  }

  // 菜单权限检查（新增）
  const routePerms = findRoutePerms(to.path)
  if (routePerms.length > 0) {
    const hasMenuAccess = routePerms.some((p) => userHasPermission(p))
    if (!hasMenuAccess) {
      next('/403')
      return
    }
  }

  next()
})

export default router
