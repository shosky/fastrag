/**
 * 顶导航菜单单一事实源（契约：docs/modules/portal-frontend.md ## 契约 PortalMenuItem）。
 * 新增门户菜单只改本数组，不改 PortalLayout / PortalTopNav。
 * 平台不设门户菜单——登录即进入平台（游客点「登录」，已登录点「进入平台」按钮）。
 */
export interface PortalMenuItem {
  /** 菜单标识 */
  key: string
  /** 显示文字 */
  title: string
  /** 跳转路由 */
  path: string
}

export const PORTAL_MENUS: PortalMenuItem[] = [
  { key: 'home', title: '首页', path: '/portal' },
  { key: 'manual', title: '操作手册', path: '/portal/manual' },
  { key: 'changelog', title: '产品动态', path: '/portal/changelog' },
]
