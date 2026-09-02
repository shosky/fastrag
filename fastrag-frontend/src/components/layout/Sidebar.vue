<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useAuth } from '@/composables/useAuth'
import { useConfirm } from '@/composables/useConfirm'
import { MENU_PERMISSION_MAP, ROLE_LABELS } from '@/types/auth'
import type { MenuPermission } from '@/types/auth'
import * as api from '@/api'
import Logo from './Logo.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { hasPermission, hasRole } = useAuth()
const { confirm } = useConfirm()

// ============ 用户信息（头像 + 弹出菜单） ============
const userInfoDialog = ref(false)
const orgNameMap = ref<Record<string, string>>({})

/** 头像显示文本：优先真实姓名首字，其次用户名首字 */
const avatarText = computed(() => {
  const name = userStore.userInfo?.realName || userStore.userInfo?.username || 'U'
  return name.charAt(0).toUpperCase()
})

/** 头像背景色：按用户名哈希取稳定颜色（无头像资源时用首字占位） */
const avatarColor = computed(() => {
  const name = userStore.userInfo?.username || 'u'
  const colors = ['#1890ff', '#722ed1', '#13c2c2', '#52c41a', '#fa8c16', '#eb2f96', '#2f54eb']
  let h = 0
  for (const c of name) h = (h * 31 + c.charCodeAt(0)) % 997
  return colors[h % colors.length]
})

/** 角色中文名（多角色取第一个有映射的） */
const roleLabel = computed(() => {
  const roles = userStore.roles || []
  if (roles.length === 0) return '-'
  return roles.map((r) => ROLE_LABELS[r as keyof typeof ROLE_LABELS] || r).join('、')
})

/** 组织显示名：orgId → 组织名（懒加载一次映射表） */
const myOrgName = computed(() => {
  const orgId = userStore.userInfo?.orgId
  if (!orgId) return '未分配'
  return orgNameMap.value[orgId] || orgId
})

function handleShowUserInfo() {
  userInfoDialog.value = true
  // 懒加载组织名映射（仅一次）
  if (Object.keys(orgNameMap.value).length === 0) {
    api.getOrgFlat().then((res: any) => {
      const list = Array.isArray(res) ? res : []
      const map: Record<string, string> = {}
      list.forEach((o: any) => { if (o.id) map[o.id] = o.name })
      orgNameMap.value = map
    }).catch(() => {})
  }
}

// 系统级模块（窄条）
interface NavModule {
  key: string
  title: string
  icon: string
  path?: string
  requirePerm?: string
  children?: SubMenuItem[]
}

interface SubMenuItem {
  path: string
  title: string
  icon?: string
  requirePerm?: string
  children?: SubMenuItem[]
}

// 全部模块定义（静态），通过 computed 过滤后渲染
const allModules: NavModule[] = [
  { key: 'home', title: '首页', icon: 'HomeFilled', path: '/home' },

  // ===== 知识库 =====
  {
    key: 'knowledge',
    title: '知识库',
    icon: 'Collection',
    path: '/knowledge',
    children: [
      {
        path: '/knowledge',
        title: '知识库管理',
        icon: 'FolderOpened',
        children: [
          { path: '/knowledge', title: '知识库列表', icon: 'Document' },
          { path: '/knowledge/categories', title: '知识库分类', icon: 'Grid' },
        ],
      },
      {
        path: '/operation',
        title: '运营中心',
        icon: 'DataAnalysis',
        children: [
          { path: '/operation/kb-analytics', title: '知识库分析', icon: 'DataAnalysis' },
          { path: '/operation/retrieval-analysis', title: '检索日志分析', icon: 'Search' },
        ],
      },
    ],
  },

  // ===== 应用 =====
  {
    key: 'application',
    title: '应用',
    icon: 'Grid',
    path: '/application',
    children: [
      {
        path: '/application',
        title: '应用管理',
        icon: 'Grid',
        children: [
          { path: '/application', title: '应用中心', icon: 'Grid' },
          { path: '/application/runtime', title: '应用运行', icon: 'VideoPlay' },
        ],
      },
      {
        path: '/application/tools',
        title: '工具与服务',
        icon: 'Tools',
        children: [
          { path: '/application/my-tools', title: '我的工具', icon: 'Tools' },
          { path: '/application/mcp-management', title: 'MCP管理', icon: 'Connection' },
          { path: '/application/skill-management', title: '技能管理', icon: 'MagicStick' },
          { path: '/application/database-management', title: '数据库管理', icon: 'Coin' },
        ],
      },
      {
        path: '/operation',
        title: '运营中心',
        icon: 'DataAnalysis',
        children: [
          { path: '/operation/feedback', title: '反馈管理', icon: 'ChatLineSquare' },
          { path: '/operation/model-monitor', title: '模型监控', icon: 'Monitor' },
        ],
      },
    ],
  },

  // ===== 管理 =====
  {
    key: 'admin',
    title: '管理',
    icon: 'Setting',
    path: '/admin',
    requirePerm: 'admin:access',
    children: [
      {
        path: '/admin/system',
        title: '系统管理',
        icon: 'Setting',
        requirePerm: 'admin:system',
        children: [
          { path: '/admin/system/general-settings', title: '通用设置', icon: 'Setting' },
          { path: '/admin/system/kb-config', title: '知识库配置', icon: 'Collection' },
          { path: '/admin/system/sensitive-words', title: '敏感词设置', icon: 'WarningFilled' },
          { path: '/admin/system/dictionary', title: '字典管理', icon: 'Notebook' },
	          { path: '/admin/system/terminology', title: '术语管理', icon: 'Reading' },
        ],
      },
      {
        path: '/admin/account',
        title: '账号权限',
        icon: 'User',
        requirePerm: 'admin:role',
        children: [
          { path: '/admin/account/roles', title: '角色管理', icon: 'User' },
          { path: '/admin/account/organization', title: '组织管理', icon: 'OfficeBuilding' },
          { path: '/admin/account/personnel', title: '人员管理', icon: 'UserFilled' },
          { path: '/admin/permissions', title: '权限管理', icon: 'Key' },
        ],
      },
      {
        path: '/admin/audit',
        title: '安全审计',
        icon: 'Lock',
        requirePerm: 'admin:audit',
        children: [
	          { path: '/admin/audit/system-log', title: '系统日志', icon: 'Document' },
        ],
      },
      {
        path: '/admin/platform',
        title: '开放平台',
        icon: 'Connection',
        requirePerm: 'admin:system',
        children: [
          { path: '/admin/platform/model-management', title: '模型管理', icon: 'Cpu' },
          { path: '/admin/platform/api-keys', title: '开放密钥', icon: 'Key' },
        ],
      },
    ],
  },
]

/**
 * 从 MENU_PERMISSION_MAP 中查找菜单路径对应的所需权限。
 * 递归搜索 children。
 */
function findRequiredPerms(path: string, map: MenuPermission[] = MENU_PERMISSION_MAP): string[] {
  for (const item of map) {
    if (item.path === path) return item.requiredPerms
    if (item.children) {
      const found = findRequiredPerms(path, item.children)
      if (found.length > 0) return found
    }
  }
  return []
}

/** 根据权限过滤菜单项（优先用 MENU_PERMISSION_MAP，fallback 到 requirePerm） */
function filterMenuItems(items: SubMenuItem[]): SubMenuItem[] {
  return items
    .map((item: SubMenuItem) => {
      // 优先从权限映射表查找
      const mapPerms = findRequiredPerms(item.path)
      const requiredPerm = mapPerms.length > 0 ? mapPerms[0] : item.requirePerm
      if (requiredPerm && !hasPermission(requiredPerm as any)) return null
      if (item.children) {
        const filteredChildren = filterMenuItems(item.children)
        if (filteredChildren.length === 0) return null
        return { ...item, children: filteredChildren }
      }
      return item
    })
    .filter(Boolean) as SubMenuItem[]
}

/** 根据权限过滤顶级模块 */
const navModules = computed<NavModule[]>(() => {
  return allModules
    .map((module: NavModule) => {
      const mapPerms = module.path ? findRequiredPerms(module.path) : []
      const requiredPerm = mapPerms.length > 0 ? mapPerms[0] : module.requirePerm
      if (requiredPerm && !hasPermission(requiredPerm as any)) return null
      if (module.children) {
        const filteredChildren = filterMenuItems(module.children)
        if (filteredChildren.length === 0) return null
        return { ...module, children: filteredChildren }
      }
      return module
    })
    .filter(Boolean) as NavModule[]
})

// 当前选中的模块
const activeModule = computed(() => {
  const path = route.path
  if (path.startsWith('/admin')) return 'admin'
  if (path.startsWith('/application')) return 'application'
	  if (path.startsWith('/operation/kb-analytics')) return 'knowledge'
  if (path.startsWith('/operation/retrieval-analysis')) return 'knowledge'
  if (path.startsWith('/operation')) return 'application'
  if (path.startsWith('/knowledge')) return 'knowledge'
  return 'home'
})

// 当前模块
const currentModule = computed(() => navModules.value.find((m) => m.key === activeModule.value))

// 当前模块的子菜单
const currentSubMenus = computed(() => currentModule.value?.children || [])

// 是否有二级菜单
const hasSubMenu = computed(() => currentSubMenus.value.length > 0)

// 展开所有二级菜单
const expandedMenus = ref<string[]>([])

// 将所有有子菜单的路径加入展开列表
function initExpandedMenus() {
  expandedMenus.value = []
  currentSubMenus.value.forEach(menu => {
    if (menu.children && menu.children.length > 0) {
      expandedMenus.value.push(menu.path)
    }
  })
}

// 监听路由变化，自动展开对应菜单
watch(() => route.path, () => {
  initExpandedMenus()
}, { immediate: true })

// 监听模块切换，展开第一个有子菜单的项
watch(activeModule, () => {
  const firstMenuWithChildren = currentSubMenus.value.find(m => m.children && m.children.length > 0)
  if (firstMenuWithChildren && !expandedMenus.value.includes(firstMenuWithChildren.path)) {
    expandedMenus.value.push(firstMenuWithChildren.path)
  }
})

function handleModuleClick(module: NavModule) {
  if (module.path) {
    router.push(module.path)
    return
  }
  if (module.children && module.children.length > 0) {
    router.push(module.children[0].path)
  }
}

function toggleSubMenu(path: string) {
  const idx = expandedMenus.value.indexOf(path)
  if (idx > -1) {
    expandedMenus.value.splice(idx, 1)
  } else {
    expandedMenus.value.push(path)
  }
}

function handleMenuClick(path: string) {
  router.push(path)
}

function isActive(path: string): boolean {
  if (route.path === path) return true
  if (!route.path.startsWith(path + '/')) return false
  const leafPaths: string[] = []
  const collect = (items: SubMenuItem[]) => {
    items.forEach((item) => {
      if (item.children && item.children.length > 0) {
        collect(item.children)
      } else {
        leafPaths.push(item.path)
      }
    })
  }
  collect(currentSubMenus.value)
  const moreSpecific = leafPaths.some((p) => p !== path && p.startsWith(path + '/') && route.path.startsWith(p))
  return !moreSpecific
}

async function handleLogout() {
  const ok = await confirm('确定要退出登录吗？')
  if (ok) {
    userStore.logout()
    router.push('/login')
  }
}
</script>

<template>
  <div class="sidebar">
    <!-- 左侧窄条 - 极简导航 -->
    <div class="nav-strip">
      <div class="strip-logo">
        <Logo :mini="true" />
      </div>

      <div class="strip-items">
        <div
          v-for="module in navModules"
          :key="module.key"
          class="strip-item"
          :class="{ active: activeModule === module.key }"
          @click="handleModuleClick(module)"
        >
          <!-- 选中态：浮动卡片包裹 -->
          <div class="strip-item-inner">
            <el-icon :size="22">
              <component :is="module.icon" />
            </el-icon>
            <span class="strip-label">{{ module.title }}</span>
          </div>
        </div>
      </div>

      <div class="strip-footer">
        <el-popover placement="right-end" :width="150" trigger="click" popper-class="user-menu-popover">
          <template #reference>
            <div class="strip-item user-avatar">
              <div class="strip-item-inner">
                <div class="avatar-circle" :style="{ background: avatarColor }">
                  <img v-if="userStore.userInfo?.avatar" :src="userStore.userInfo.avatar" alt="avatar" />
                  <span v-else>{{ avatarText }}</span>
                </div>
              </div>
            </div>
          </template>
          <div class="user-menu">
            <div class="user-menu__item" @click="handleShowUserInfo">
              <el-icon :size="15"><User /></el-icon>
              <span>用户信息</span>
            </div>
            <div class="user-menu__item user-menu__item--danger" @click="handleLogout">
              <el-icon :size="15"><SwitchButton /></el-icon>
              <span>退出系统</span>
            </div>
          </div>
        </el-popover>
      </div>
    </div>

    <!-- 用户信息弹窗 -->
    <el-dialog v-model="userInfoDialog" title="用户信息" width="400px" :close-on-click-modal="false">
      <div class="user-info-dialog">
        <div class="user-info-dialog__avatar" :style="{ background: avatarColor }">
          <img v-if="userStore.userInfo?.avatar" :src="userStore.userInfo.avatar" alt="avatar" />
          <span v-else>{{ avatarText }}</span>
        </div>
        <div class="user-info-dialog__fields">
          <div class="user-info-dialog__field">
            <span class="user-info-dialog__label">用户名</span>
            <span>{{ userStore.userInfo?.username || '-' }}</span>
          </div>
          <div class="user-info-dialog__field">
            <span class="user-info-dialog__label">姓名</span>
            <span>{{ userStore.userInfo?.realName || '-' }}</span>
          </div>
          <div class="user-info-dialog__field">
            <span class="user-info-dialog__label">角色</span>
            <span>{{ roleLabel }}</span>
          </div>
          <div class="user-info-dialog__field">
            <span class="user-info-dialog__label">所属组织</span>
            <span>{{ myOrgName }}</span>
          </div>
          <div class="user-info-dialog__field">
            <span class="user-info-dialog__label">邮箱</span>
            <span>{{ userStore.userInfo?.email || '-' }}</span>
          </div>
          <div class="user-info-dialog__field">
            <span class="user-info-dialog__label">手机</span>
            <span>{{ userStore.userInfo?.phone || '-' }}</span>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button type="primary" @click="userInfoDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 右侧二级菜单 -->
    <div v-if="hasSubMenu" class="sub-menu-panel">
      <div class="sub-menu-header">
        <el-icon :size="18" class="header-icon"><component :is="currentModule?.icon" /></el-icon>
        <span>{{ currentModule?.title }}</span>
      </div>

      <el-scrollbar class="sub-menu-body">
        <div class="menu-list">
          <template v-for="menu in currentSubMenus" :key="menu.path">
            <!-- 叶子菜单（无子菜单） -->
            <div
              v-if="!menu.children || menu.children.length === 0"
              class="menu-item"
              :class="{ active: isActive(menu.path) }"
              @click="handleMenuClick(menu.path)"
            >
              <el-icon v-if="menu.icon" :size="16" class="menu-icon">
                <component :is="menu.icon" />
              </el-icon>
              <span class="menu-text">{{ menu.title }}</span>
            </div>

            <!-- 有子菜单的菜单组 -->
            <div v-else class="menu-group">
              <div
                class="menu-group-title"
                :class="{ expanded: expandedMenus.includes(menu.path) }"
                @click="toggleSubMenu(menu.path)"
              >
                <div class="menu-group-title-left">
                  <el-icon v-if="menu.icon" :size="16" class="menu-icon">
                    <component :is="menu.icon" />
                  </el-icon>
                  <span>{{ menu.title }}</span>
                </div>
                <el-icon class="expand-icon" :size="12"><ArrowDown /></el-icon>
              </div>
              <div v-show="expandedMenus.includes(menu.path)" class="menu-group-items">
                <div
                  v-for="child in menu.children"
                  :key="child.path"
                  class="menu-item sub"
                  :class="{ active: isActive(child.path) }"
                  @click="handleMenuClick(child.path)"
                >
                  <el-icon v-if="child.icon" :size="16" class="menu-icon">
                    <component :is="child.icon" />
                  </el-icon>
                  <span class="menu-text">{{ child.title }}</span>
                </div>
              </div>
            </div>
          </template>
        </div>
      </el-scrollbar>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.sidebar {
  display: flex;
  height: 100%;
  flex-shrink: 0;
}

// ============ 左侧窄条导航 ============
.nav-strip {
  width: $nav-strip-width;
  background: $nav-strip-bg;
  display: flex;
  flex-direction: column;
  border-right: 1px solid $border-base;
  z-index: 10;
}

.strip-logo {
  padding: $spacing-md 0;
  display: flex;
  justify-content: center;
  border-bottom: 1px solid $border-light;
}

.strip-items {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: $spacing-sm 0;
  overflow-y: auto;
  gap: 4px;
}

.strip-item {
  display: flex;
  justify-content: center;
  width: 100%;
  padding: 2px 0;
  cursor: pointer;
  position: relative;
  transition: all 0.2s ease;

  &.active {
    .strip-item-inner {
      background: $bg-white;
      border-radius: $radius-lg;
      box-shadow: $shadow-nav-card;
      color: $nav-icon-active;
    }
  }

  &.logout {
    .strip-item-inner {
      color: $text-secondary;
    }
  }
}

.strip-item-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  padding: 8px 6px;
  width: 48px;
  color: $nav-icon-color;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);

  &:hover {
    color: $text-regular;
  }
}

.strip-label {
  font-size: 10px;
  line-height: 1.2;
  letter-spacing: 0.3px;
  white-space: nowrap;
}

.strip-footer {
  padding: $spacing-sm 0;
  border-top: 1px solid $border-light;
  display: flex;
  justify-content: center;
}

// 头像（替代原退出按钮）
.avatar-circle {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  overflow: hidden;
  user-select: none;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.user-avatar {
  &.strip-item:hover .strip-item-inner {
    color: $text-regular;
  }
}

// 用户信息弹窗
.user-info-dialog {
  display: flex;
  gap: $spacing-lg;
  padding: $spacing-sm $spacing-xs;

  &__avatar {
    width: 64px;
    height: 64px;
    border-radius: 50%;
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    font-size: 26px;
    font-weight: 600;
    overflow: hidden;

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
  }

  &__fields {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  &__field {
    display: flex;
    align-items: center;
    font-size: 13px;
  }

  &__label {
    width: 64px;
    color: $text-secondary;
    flex-shrink: 0;
  }
}

// ============ 右侧二级菜单 ============
.sub-menu-panel {
  width: $sub-menu-width;
  background: $sub-menu-bg;
  display: flex;
  flex-direction: column;
  border-right: 1px solid $border-base;
}

.sub-menu-header {
  height: $header-height;
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: 0 $spacing-base;
  font-weight: 600;
  font-size: 15px;
  color: $text-primary;
  border-bottom: 1px solid $border-light;
  flex-shrink: 0;

  .header-icon {
    color: $color-primary;
  }
}

.sub-menu-body {
  flex: 1;
  overflow: hidden;
}

.menu-list {
  padding: $spacing-sm 0;
}

.menu-group {
  margin-bottom: 1px;
}

// 菜单分组标题
.menu-group-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 9px $spacing-base;
  font-size: 13px;
  font-weight: 500;
  color: $text-regular;
  cursor: pointer;
  transition: all 0.2s;
  margin: 0 $spacing-sm;
  border-radius: $radius-base;

  &:hover {
    background: $bg-hover;
  }

  .menu-group-title-left {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    min-width: 0;
  }

  .expand-icon {
    transition: transform 0.25s;
    font-size: 12px;
    color: $text-placeholder;
    flex-shrink: 0;
  }

  &.expanded .expand-icon {
    transform: rotate(180deg);
  }
}

.menu-group-items {
  overflow: hidden;
}

// 菜单项
.menu-item {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: 8px $spacing-base;
  padding-left: calc($spacing-xl + 4px);
  font-size: 13px;
  color: $text-regular;
  cursor: pointer;
  transition: all 0.2s;
  margin: 1px $spacing-sm;
  border-radius: $radius-base;

  &:hover {
    background: $bg-hover;
    color: $text-primary;
  }

  &.active {
    background: $sub-menu-active-bg;
    color: $sub-menu-active-color;
    font-weight: 500;

    .menu-icon {
      color: $sub-menu-active-color;
    }
  }

  &.sub {
    padding-left: calc($spacing-xl + 4px);
  }

  // 叶子菜单项（无子菜单，在根层级）
  &:not(.sub) {
    padding-left: $spacing-base;
  }
}

.menu-icon {
  color: $text-placeholder;
  flex-shrink: 0;
  transition: color 0.2s;
}

.menu-text {
  line-height: 1.4;
}
</style>

<!-- 弹出菜单样式：popover teleport 到 body，须用全局样式 -->
<style lang="scss">
@use '@/assets/styles/variables' as *;

.user-menu-popover {
  padding: 6px !important;

  .user-menu {
    &__item {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 8px 10px;
      border-radius: $radius-sm;
      font-size: 13px;
      color: $text-regular;
      cursor: pointer;
      transition: background 0.15s;

      &:hover {
        background: $bg-hover;
        color: $text-primary;
      }

      &--danger {
        color: $color-danger;

        &:hover {
          background: rgba(245, 108, 108, 0.08);
          color: $color-danger;
        }
      }
    }
  }
}
</style>
