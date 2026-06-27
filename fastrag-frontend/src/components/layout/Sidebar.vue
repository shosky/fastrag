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
  requirePerm?: string
  children?: SubMenuItem[]
}

// 全部模块定义（静态），通过 computed 过滤后渲染
// 三大顶级模块：知识库、应用、管理
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
        children: [
          { path: '/knowledge', title: '知识库列表' },
          { path: '/knowledge/categories', title: '知识库分类' },
          { path: '/knowledge/tags', title: '知识库标签' },
        ],
      },
      {
        path: '/knowledge-review',
        title: '知识审核',
        children: [
          { path: '/knowledge-review/flows', title: '审核流程管理' },
          { path: '/knowledge-review/flow-design', title: '审核流程设计' },
          { path: '/knowledge-review/listeners', title: '监听管理' },
          { path: '/knowledge-review/compliance', title: '合规性检查' },
          { path: '/knowledge-review/reports', title: '审核报告' },
          { path: '/knowledge-review/quality', title: '质量评估' },
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
        children: [
          { path: '/application', title: '应用中心' },
          { path: '/publish-eval/release', title: '发布与评估' },
        ],
      },
      {
        path: '/application/tools',
        title: '工具与服务',
        children: [
          { path: '/application/my-tools', title: '我的工具' },
          { path: '/application/mcp-management', title: 'MCP管理' },
          { path: '/plugin-db/plugins', title: '插件管理' },
          { path: '/application/skill-management', title: '技能管理' },
          { path: '/plugin-db/databases', title: '数据库管理' },
        ],
      },
      {
        path: '/operation',
        title: '运营中心',
        children: [
          { path: '/operation/feedback', title: '反馈管理' },
          { path: '/robot-operation/faq-analysis', title: 'FAQ知识分析' },
          { path: '/robot-operation/multi-turn', title: '多轮对话分析' },
          { path: '/robot-operation/intent', title: '意图知识分析' },
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
      { path: '/admin/index', title: '管理中心概览' },
      {
        path: '/admin/system',
        title: '系统管理',
        requirePerm: 'admin:system',
        children: [
          { path: '/admin/system/general-settings', title: '通用设置' },
          { path: '/admin/system/kb-config', title: '知识库配置' },
          { path: '/admin/system/sensitive-words', title: '敏感词设置' },
          { path: '/admin/system/dictionary', title: '字典管理' },
          { path: '/admin/system/terminology', title: '术语管理' },
          { path: '/admin/system/query-rules', title: '查询规则' },
          { path: '/operation/model-monitor', title: '模型监控' },
        ],
      },
      {
        path: '/admin/account',
        title: '账号权限',
        requirePerm: 'admin:role',
        children: [
          { path: '/admin/account/roles', title: '角色管理' },
          { path: '/admin/account/organization', title: '组织管理' },
          { path: '/admin/account/team', title: '团队管理' },
          { path: '/admin/account/personnel', title: '人员管理' },
          { path: '/admin/permissions', title: '权限管理' },
        ],
      },
      {
        path: '/admin/audit',
        title: '安全审计',
        requirePerm: 'admin:audit',
        children: [
          { path: '/admin/audit/system-log', title: '系统日志' },
          { path: '/admin/audit/device-login', title: '设备登录分析' },
          { path: '/admin/audit/login-security', title: '登录安全配置' },
          { path: '/admin/audit/review-center', title: '审核中心' },
        ],
      },
      {
        path: '/admin/content',
        title: '内容与工具',
        children: [
          { path: '/admin/content/notification', title: '通知管理' },
          { path: '/admin/content/prompts', title: '提示词' },
          { path: '/application/prompt-templates', title: 'Prompt模板' },
          { path: '/admin/content/templates', title: '文档模板' },
          { path: '/admin/content/download', title: '下载中心' },
        ],
      },
      {
        path: '/admin/analytics',
        title: '数据分析',
        children: [
          { path: '/operation/kb-analytics', title: '知识库分析' },
        ],
      },
      {
        path: '/admin/platform',
        title: '开放平台',
        requirePerm: 'admin:system',
        children: [
          { path: '/admin/platform/third-party', title: '三方平台' },
          { path: '/admin/platform/model-management', title: '模型管理' },
          { path: '/admin/platform/api-keys', title: '开放密钥' },
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

// 当前选中的模块（三大顶级模块：knowledge / application / admin）
const activeModule = computed(() => {
  const path = route.path
  // 管理
  if (path.startsWith('/admin')) return 'admin'
  // 应用（含机器人配置、业务流、对话知识、发布评估、运营中心、工具/MCP/插件/技能/数据库）
  if (
    path.startsWith('/application') ||
    path.startsWith('/publish-eval') ||
    path.startsWith('/robot-operation') ||
    path.startsWith('/plugin-db') ||
    path.startsWith('/operation')
  ) return 'application'
  // 知识库（含应答知识库、知识引用、知识生产、知识存储、知识审核、知识更新、知识加工）
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
  // 如果有直接路径，跳转
  if (module.path) {
    router.push(module.path)
    return
  }
  // 如果有子菜单，跳转到第一个子菜单
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

/**
 * 判断菜单项是否高亮。
 * 规则：
 *  - 精确匹配 → 高亮
 *  - 当前路径是该项的子路径（path + '/...'）→ 高亮
 *    但要排除「同级兄弟也是前缀」的情况，例如 /application 是 /application/my-tools 的前缀，
 *    但二者是平级菜单，不应互相吸收。
 */
function isActive(path: string): boolean {
  if (route.path === path) return true
  if (!route.path.startsWith(path + '/')) return false
  // 收集当前模块下所有叶子菜单路径
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
  // 若存在比 path 更具体的兄弟菜单也匹配当前路由，则 path 不应高亮（让更具体的那个高亮）
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
    <!-- 左侧窄条 -->
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
          <el-icon :size="20"><component :is="module.icon" /></el-icon>
          <span class="strip-label">{{ module.title }}</span>
          <div class="active-indicator"></div>
        </div>
      </div>

      <div class="strip-footer">
        <el-tooltip content="退出登录" placement="right">
          <div class="strip-item" @click="handleLogout">
            <el-icon :size="20"><SwitchButton /></el-icon>
          </div>
        </el-tooltip>
      </div>
    </div>

    <!-- 右侧二级菜单 -->
    <div v-if="hasSubMenu" class="sub-menu-panel">
      <div class="sub-menu-header">
        <span>{{ currentModule?.title }}</span>
      </div>

      <el-scrollbar class="sub-menu-body">
        <div class="menu-list">
          <template v-for="menu in currentSubMenus" :key="menu.path">
            <!-- 无子菜单 -->
            <div
              v-if="!menu.children || menu.children.length === 0"
              class="menu-item"
              :class="{ active: isActive(menu.path) }"
              @click="handleMenuClick(menu.path)"
            >
              <span>{{ menu.title }}</span>
            </div>

            <!-- 有子菜单 -->
            <div v-else class="menu-group">
              <div
                class="menu-group-title"
                :class="{ expanded: expandedMenus.includes(menu.path) }"
                @click="toggleSubMenu(menu.path)"
              >
                <span>{{ menu.title }}</span>
                <el-icon class="expand-icon"><ArrowDown /></el-icon>
              </div>
              <div v-show="expandedMenus.includes(menu.path)" class="menu-group-items">
                <div
                  v-for="child in menu.children"
                  :key="child.path"
                  class="menu-item sub"
                  :class="{ active: isActive(child.path) }"
                  @click="handleMenuClick(child.path)"
                >
                  <span class="active-dot"></span>
                  <span>{{ child.title }}</span>
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

// 左侧窄条
.nav-strip {
  width: 64px;
  background: #F8F9FA;
  display: flex;
  flex-direction: column;
  border-right: 1px solid $border-lighter;
}

.strip-logo {
  padding: $spacing-sm 0;
  display: flex;
  justify-content: center;
  border-bottom: 1px solid $border-lighter;
}

.strip-items {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: $spacing-sm 0;
  overflow-y: auto;
}

.strip-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 10px $spacing-xs;
  cursor: pointer;
  position: relative;
  color: $text-secondary;
  transition: all 0.2s;

  &:hover {
    color: $color-primary;
    background: $bg-hover;
  }

  &.active {
    color: $color-primary;
    background: $bg-active;

    .active-indicator {
      position: absolute;
      left: 0;
      top: 50%;
      transform: translateY(-50%);
      width: 3px;
      height: 24px;
      background: $color-primary;
      border-radius: 0 2px 2px 0;
    }
  }
}

.strip-label {
  font-size: 11px;
  line-height: 1.2;
}

.strip-footer {
  padding: $spacing-sm 0;
  border-top: 1px solid $border-lighter;
  display: flex;
  justify-content: center;
}

// 右侧二级菜单
.sub-menu-panel {
  width: 200px;
  background: $bg-white;
  display: flex;
  flex-direction: column;
  border-right: 1px solid $border-lighter;
}

.sub-menu-header {
  height: $header-height;
  display: flex;
  align-items: center;
  padding: 0 $spacing-base;
  font-weight: 600;
  font-size: 15px;
  color: $text-primary;
  border-bottom: 1px solid $border-lighter;
  flex-shrink: 0;
}

.sub-menu-body {
  flex: 1;
  overflow: hidden;
}

.menu-list {
  padding: $spacing-sm 0;
}

.menu-group {
  margin-bottom: 2px;
}

.menu-group-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px $spacing-base;
  font-size: 13px;
  color: $text-regular;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    background: $bg-hover;
  }

  .expand-icon {
    transition: transform 0.3s;
    font-size: 12px;
    color: $text-secondary;
  }

  &.expanded .expand-icon {
    transform: rotate(180deg);
  }
}

.menu-group-items {
  overflow: hidden;
}

.menu-item {
  display: flex;
  align-items: center;
  padding: 10px $spacing-base;
  padding-left: $spacing-xl;
  font-size: 13px;
  color: $text-regular;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    background: $bg-hover;
    color: $text-primary;
  }

  &.active {
    background: $bg-active;
    color: $color-primary;
    font-weight: 500;

    .active-dot {
      display: inline-block;
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background: $color-primary;
      margin-right: $spacing-sm;
    }
  }

  &.sub {
    padding-left: 36px;
  }
}
</style>
