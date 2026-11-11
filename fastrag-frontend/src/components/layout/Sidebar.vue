<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuth } from '@/composables/useAuth'
import { storage } from '@/utils/storage'
import { navConfig } from '@/config/nav-segments'
import { canShowPath } from '@/config/nav-permissions'
import Logo from './Logo.vue'

const route = useRoute()
const router = useRouter()
const { hasPermission } = useAuth()

// ============ 折叠态（修订 #15：图标态 64px，状态持久化；#16：悬停浮出完整菜单） ============

const COLLAPSE_KEY = 'nav.sidebarCollapsed'
const collapsed = ref<boolean>(storage.get<boolean>(COLLAPSE_KEY) === true)

/** 折叠态悬停浮出层（flyout）：鼠标进入侧栏弹出、离开收回，点击菜单项后亦收回 */
const flyoutVisible = ref(false)

function toggleCollapsed() {
  collapsed.value = !collapsed.value
  flyoutVisible.value = false
  storage.set(COLLAPSE_KEY, collapsed.value)
}

function onSidebarEnter() {
  if (collapsed.value) flyoutVisible.value = true
}

function onSidebarLeave() {
  flyoutVisible.value = false
}

function closeFlyout() {
  flyoutVisible.value = false
}

// ============ 权限过滤（逻辑平移自旧 Sidebar.vue:219-263，键集冻结 ADR-NAV-PERM-TREE-DECOUPLING） ============

/** 过滤后的段树：无权限项剔除，段内全空时整段（含标题）剔除 */
const visibleSegments = computed(() => {
  return navConfig.segments
    .map((seg) => ({ ...seg, items: seg.items.filter((item) => canShowPath(item.path, hasPermission)) }))
    .filter((seg) => seg.items.length > 0)
})

const homeVisible = computed(() => canShowPath(navConfig.home.path, hasPermission))

// ============ 高亮（叶子优先，逻辑平移自旧 Sidebar.vue:336-352） ============

/** 全部菜单叶子路径（含顶置主页），用于叶子优先回溯 */
const leafPaths: string[] = [navConfig.home.path, ...navConfig.segments.flatMap((seg) => seg.items.map((item) => item.path))]

function isActive(path: string): boolean {
  if (route.path === path) return true
  if (!route.path.startsWith(path + '/')) return false
  const moreSpecific = leafPaths.some((p) => p !== path && p.startsWith(path + '/') && route.path.startsWith(p))
  return !moreSpecific
}

// 测试缝：组件测试直接驱动过滤结果与折叠态
defineExpose({
  visibleSegments,
  homeVisible,
  isActive,
  collapsed,
  flyoutVisible,
  toggleCollapsed,
})
</script>

<template>
  <div class="sidebar" @mouseenter="onSidebarEnter" @mouseleave="onSidebarLeave">
    <div class="nav-sidebar" :class="{ collapsed }">
      <!-- logo：点击回门户（门户链决策 #8）；用户入口在内容卡头右上角（UserMenu，修订 #14） -->
      <div class="nav-logo" :title="collapsed ? '返回门户首页' : undefined" @click="router.push('/portal')">
        <Logo :mini="true" />
      </div>

      <el-scrollbar class="nav-body">
        <!-- 顶置主页（四段之上，决策 #5） -->
        <router-link
          v-if="homeVisible"
          :to="navConfig.home.path"
          class="nav-item nav-home"
          :class="{ active: isActive(navConfig.home.path) }"
          :title="collapsed ? navConfig.home.title : undefined"
        >
          <el-icon v-if="navConfig.home.icon" :size="16" class="nav-icon">
            <component :is="navConfig.home.icon" />
          </el-icon>
          <span v-if="!collapsed" class="nav-text">{{ navConfig.home.title }}</span>
        </router-link>
        <div v-if="homeVisible" class="nav-divider"></div>

        <!-- 导航四段：对话 / 构建 / 资源 / 数据分析（修订 #15） -->
        <template v-for="seg in visibleSegments" :key="seg.key">
          <div v-if="!collapsed" class="nav-segment-title">{{ seg.title }}</div>

          <router-link
            v-for="item in seg.items"
            :key="item.key"
            :to="item.path"
            class="nav-item"
            :class="{ active: isActive(item.path) }"
            :title="collapsed ? item.title : undefined"
          >
            <el-icon v-if="item.icon" :size="16" class="nav-icon">
              <component :is="item.icon" />
            </el-icon>
            <span v-if="!collapsed" class="nav-text">{{ item.title }}</span>
          </router-link>
        </template>
      </el-scrollbar>

      <!-- 折叠/展开开关（修订 #15，对齐 ADP「收起」） -->
      <div class="nav-collapse" :title="collapsed ? '展开菜单' : '收起菜单'" @click="toggleCollapsed">
        <el-icon :size="16">
          <Expand v-if="collapsed" />
          <Fold v-else />
        </el-icon>
        <span v-if="!collapsed" class="nav-text">收起菜单</span>
      </div>
    </div>

    <!-- 折叠态悬停浮出完整菜单（修订 #16）：覆盖内容区，鼠标离开即收回 -->
    <div v-if="collapsed && flyoutVisible" class="nav-flyout">
      <el-scrollbar class="nav-body">
        <router-link
          v-if="homeVisible"
          :to="navConfig.home.path"
          class="nav-item nav-home"
          :class="{ active: isActive(navConfig.home.path) }"
          @click="closeFlyout"
        >
          <el-icon v-if="navConfig.home.icon" :size="16" class="nav-icon">
            <component :is="navConfig.home.icon" />
          </el-icon>
          <span class="nav-text">{{ navConfig.home.title }}</span>
        </router-link>
        <div v-if="homeVisible" class="nav-divider"></div>

        <template v-for="seg in visibleSegments" :key="seg.key">
          <div class="nav-segment-title">{{ seg.title }}</div>
          <router-link
            v-for="item in seg.items"
            :key="item.key"
            :to="item.path"
            class="nav-item"
            :class="{ active: isActive(item.path) }"
            @click="closeFlyout"
          >
            <el-icon v-if="item.icon" :size="16" class="nav-icon">
              <component :is="item.icon" />
            </el-icon>
            <span class="nav-text">{{ item.title }}</span>
          </router-link>
        </template>
      </el-scrollbar>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.sidebar {
  position: relative; // 折叠态浮出层（.nav-flyout）的定位锚点
  display: flex;
  height: 100%;
  flex-shrink: 0;
}

// ============ 单列宽侧栏（nav-shell：替代窄条 + 二级面板双列） ============
.nav-sidebar {
  width: $sidebar-width;
  background: $nav-strip-bg;
  display: flex;
  flex-direction: column;
  border-right: 1px solid $border-base;
}

.nav-logo {
  padding: $spacing-md 0;
  display: flex;
  justify-content: center;
  border-bottom: 1px solid $border-light;
  cursor: pointer; // 点击返回门户首页（/portal）
}

.nav-body {
  flex: 1;
  padding: $spacing-sm 0;
}

// 段标题（非交互文本）
.nav-segment-title {
  padding: 10px $spacing-base 4px;
  font-size: 11px;
  font-weight: 500;
  color: $text-placeholder;
  letter-spacing: 0.5px;
  user-select: none;
}

// 主页项与四段的分隔线
.nav-divider {
  height: 1px;
  background: $border-light;
  margin: $spacing-sm $spacing-base;
}

// 菜单项
.nav-item {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  height: 32px;
  margin: 1px $spacing-sm;
  padding: 0 $spacing-base;
  border-radius: $radius-base;
  font-size: 13px;
  color: $text-regular;
  text-decoration: none;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    background: $bg-hover;
    color: $text-primary;
  }

  &.active {
    background: $sub-menu-active-bg;
    color: $sub-menu-active-color;
    font-weight: 500;

    .nav-icon {
      color: $sub-menu-active-color;
    }
  }
}

.nav-icon {
  color: $text-placeholder;
  flex-shrink: 0;
  transition: color 0.2s ease;
}

.nav-text {
  line-height: 1.4;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

// ============ 折叠态（修订 #15：图标模式 64px） ============
.nav-sidebar.collapsed {
  width: $nav-strip-width;

  .nav-item {
    justify-content: center;
    padding: 0;
    margin: 1px auto;
    width: 40px;
  }

  .nav-divider {
    margin: $spacing-sm auto;
    width: 40px;
  }

  .nav-segment-title {
    display: flex;
    justify-content: center;
    padding: 8px 0 2px;

    // 折叠态段分隔：以居中短横线代替文字标题
    &::before {
      content: '';
      display: block;
      width: 16px;
      height: 1px;
      background: $border-base;
    }
  }
}

// ============ 底部折叠开关（修订 #15） ============
.nav-collapse {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: $spacing-sm;
  height: 40px;
  margin: $spacing-sm;
  border-radius: $radius-base;
  color: $text-secondary;
  cursor: pointer;
  transition: all 0.2s ease;
  user-select: none;

  &:hover {
    background: $bg-hover;
    color: $text-primary;
  }
}

.nav-sidebar.collapsed .nav-collapse {
  margin: $spacing-sm auto;
  width: 40px;
}

// ============ 折叠态悬停浮出完整菜单（修订 #16） ============
.nav-flyout {
  position: absolute;
  left: $nav-strip-width;
  top: 0;
  bottom: 0;
  width: $sidebar-width;
  background: $nav-strip-bg;
  border-right: 1px solid $border-base;
  box-shadow: $shadow-lg;
  z-index: 100;
  display: flex;
  flex-direction: column;
  overflow: hidden;

  .nav-body {
    flex: 1;
    padding: $spacing-sm 0;
  }
}
</style>
