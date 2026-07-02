<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useAuth } from '@/composables/useAuth'
import { useConfirm } from '@/composables/useConfirm'
import { MENU_PERMISSION_MAP } from '@/types/auth'
import type { MenuPermission } from '@/types/auth'
import Logo from './Logo.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { hasPermission, hasRole } = useAuth()
const { confirm } = useConfirm()

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
    children: [
      {
        path: '/knowledge',
        title: '知识库管理',
        icon: 'FolderOpened',
        children: [
          { path: '/knowledge', title: '知识库列表', icon: 'Document' },
          { path: '/knowledge/categories', title: '知识库分类', icon: 'Grid' },
          { path: '/knowledge/tags', title: '知识库标签', icon: 'PriceTag' },
        ],
      },
      {
        path: '/knowledge-review',
        title: '知识审核',
        icon: 'Edit',
        children: [
          { path: '/knowledge-review/management', title: '审核管理', icon: 'Setting' },
          { path: '/knowledge-review/flows', title: '审核流程管理', icon: 'Connection' },
          { path: '/knowledge-review/flow-design', title: '审核流程设计', icon: 'EditPen' },
          { path: '/knowledge-review/listeners', title: '监听管理', icon: 'Bell' },
          { path: '/knowledge-review/compliance', title: '合规性检查', icon: 'Shield' },
          { path: '/knowledge-review/reports', title: '审核报告', icon: 'DataBoard' },
          { path: '/knowledge-review/quality', title: '质量评估', icon: 'Star' },
        ],
      },
    ],
  },

  // ===== 应用 =====
  {
    key: 'application',
    title: '应用',
    icon: 'Grid',
    children: [
      {
        path: '/application',
        title: '应用管理',
        icon: 'Grid',
        children: [
          { path: '/application', title: '应用中心', icon: 'Grid' },
          { path: '/publish-eval/release', title: '发布与评估', icon: 'Upload' },
        ],
      },
      {
        path: '/application/tools',
        title: '工具与服务',
        icon: 'Tools',
        children: [
          { path: '/application/my-tools', title: '我的工具', icon: 'Tools' },
          { path: '/application/mcp-management', title: 'MCP管理', icon: 'Connection' },
          { path: '/plugin-db/plugins', title: '插件管理', icon: 'Coin' },
          { path: '/application/skill-management', title: '技能管理', icon: 'MagicStick' },
          { path: '/plugin-db/databases', title: '数据库管理', icon: 'Database' },
        ],
      },
      {
        path: '/operation',
        title: '运营中心',
        icon: 'DataAnalysis',
        children: [
          { path: '/operation/feedback', title: '反馈管理', icon: 'ChatLineSquare' },
          { path: '/operation/qa-detail', title: '问答明细', icon: 'ChatDotRound' },
          { path: '/operation/retrieval-analysis', title: '检索日志分析', icon: 'Search' },
          { path: '/robot-operation/faq-analysis', title: 'FAQ知识分析', icon: 'Document' },
          { path: '/robot-operation/multi-turn', title: '多轮对话分析', icon: 'ChatLineSquare' },
          { path: '/robot-operation/intent', title: '意图知识分析', icon: 'Aim' },
          { path: '/robot-operation/data-mining', title: '数据挖掘', icon: 'DataAnalysis' },
        ],
      },
    ],
  },

  // ===== 管理 =====
  {
    key: 'admin',
    title: '管理',
    icon: 'Setting',
    requirePerm: 'admin:access',
    children: [
      { path: '/admin/index', title: '管理中心概览', icon: 'DataBoard' },
      {
        path: '/admin/system',
        title: '系统管理',
        icon: 'Setting',
        requirePerm: 'admin:system',
        children: [
          { path: '/admin/system/general-settings', title: '通用设置', icon: 'Setting' },
          { path: '/admin/system/config-management', title: '配置管理', icon: 'Tools' },
          { path: '/admin/system/kb-config', title: '知识库配置', icon: 'Collection' },
          { path: '/admin/system/sensitive-words', title: '敏感词设置', icon: 'WarningFilled' },
          { path: '/admin/system/dictionary', title: '字典管理', icon: 'Notebook' },
          { path: '/admin/system/terminology', title: '术语管理', icon: 'Reading' },
          { path: '/admin/system/query-rules', title: '查询规则', icon: 'List' },
          { path: '/operation/model-monitor', title: '模型监控', icon: 'Monitor' },
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
          { path: '/admin/account/team', title: '团队管理', icon: 'Users' },
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
          { path: '/admin/audit/device-login', title: '设备登录分析', icon: 'Monitor' },
          { path: '/admin/audit/login-security', title: '登录安全配置', icon: 'Lock' },
          { path: '/admin/audit/review-center', title: '审核中心', icon: 'View' },
        ],
      },
      {
        path: '/admin/content',
        title: '内容与工具',
        icon: 'FolderOpened',
        children: [
          { path: '/admin/content/notification', title: '通知管理', icon: 'Bell' },
          { path: '/admin/notifications', title: '通知中心', icon: 'Bell' },
          { path: '/admin/content/prompts', title: '提示词', icon: 'Edit' },
          { path: '/application/prompt-templates', title: 'Prompt模板', icon: 'CopyDocument' },
          { path: '/admin/content/templates', title: '文档模板', icon: 'Document' },
          { path: '/admin/content/download', title: '下载中心', icon: 'Download' },
        ],
      },
      {
        path: '/admin/analytics',
        title: '数据分析',
        icon: 'DataAnalysis',
        children: [
          { path: '/operation/kb-analytics', title: '知识库分析', icon: 'DataAnalysis' },
        ],
      },
      {
        path: '/admin/platform',
        title: '开放平台',
        icon: 'Connection',
        requirePerm: 'admin:system',
        children: [
          { path: '/admin/platform/third-party', title: '三方平台', icon: 'Connection' },
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
  if (
    path.startsWith('/application') ||
    path.startsWith('/publish-eval') ||
    path.startsWith('/robot-operation') ||
    path.startsWith('/plugin-db') ||
    path.startsWith('/operation')
  ) return 'application'
  if (
    path.startsWith('/knowledge') ||
    path.startsWith('/knowledge-review')
  ) return 'knowledge'
  return 'home'
})

// 当前模块
const currentModule = computed(() => navModules.value.find((m) => m.key === activeModule.value))

// 当前模块的子菜单
const currentSubMenus = computed(() => currentModule.value?.children || [])

// 是否有二级菜单
const hasSubMenu = computed(() => currentSubMenus.value.length > 0)

// 展开的一级菜单
const expandedMenus = ref<string[]>([])

// 根据当前路由初始化展开的菜单
function initExpandedMenus() {
  const path = route.path
  expandedMenus.value = []
  currentSubMenus.value.forEach(menu => {
    if (menu.children && menu.children.length > 0) {
      if (path.startsWith(menu.path) || menu.children.some(c => path.startsWith(c.path))) {
        expandedMenus.value.push(menu.path)
      }
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
        <el-tooltip content="退出登录" placement="right">
          <div class="strip-item logout" @click="handleLogout">
            <div class="strip-item-inner">
              <el-icon :size="20"><SwitchButton /></el-icon>
            </div>
          </div>
        </el-tooltip>
      </div>
    </div>

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
