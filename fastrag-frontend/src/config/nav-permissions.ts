import { MENU_PERMISSION_MAP } from '@/types/auth'
import type { MenuPermission, Permission } from '@/types/auth'

/**
 * 导航权限过滤（nav-shell：ADR-NAV-PERM-TREE-DECOUPLING，键集冻结）。
 * 供 Sidebar（侧边栏菜单）与 UserMenu（用户下拉分组）共用——按 path 查
 * MENU_PERMISSION_MAP，无记录即登录恒显。
 */

/** 从 MENU_PERMISSION_MAP 递归查找路径对应的所需权限 */
export function findRequiredPerms(path: string, map: MenuPermission[] = MENU_PERMISSION_MAP): string[] {
  for (const item of map) {
    if (item.path === path) return item.requiredPerms
    if (item.children) {
      const found = findRequiredPerms(path, item.children)
      if (found.length > 0) return found
    }
  }
  return []
}

/** 菜单项可见性：按 path 查键，无键即登录恒显 */
export function canShowPath(path: string, hasPermission: (p: Permission) => boolean): boolean {
  const perms = findRequiredPerms(path)
  if (perms.length > 0 && !hasPermission(perms[0] as Permission)) return false
  return true
}
