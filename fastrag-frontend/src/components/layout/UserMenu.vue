<script lang="ts">
/** 头像显示文本：优先真实姓名首字，其次用户名首字 */
export function avatarTextOf(userInfo: { realName?: string; username?: string } | null): string {
  const name = userInfo?.realName || userInfo?.username || 'U'
  return name.charAt(0).toUpperCase()
}
</script>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useConfirm } from '@/composables/useConfirm'
import { useAuth } from '@/composables/useAuth'
import * as api from '@/api'
import { navConfig } from '@/config/nav-segments'
import type { UserMenuItem } from '@/config/nav-segments'
import { canShowPath } from '@/config/nav-permissions'

/**
 * 顶栏右上角用户入口（nav-shell 修订 #14 二次迭代：自侧栏移至内容卡头右上角，铃铛右侧）。
 * 头像 chip 点击弹出下拉：头部（姓名+用户名）+ 固定四项 + 权限门禁分组（数据分析）。
 */
const router = useRouter()
const userStore = useUserStore()
const { confirm } = useConfirm()
const { hasPermission } = useAuth()

const userMenuVisible = ref(false)
const userInfoDialog = ref(false)
const orgNameMap = ref<Record<string, string>>({})

/** 管理级联子面板可见性（修订 #17，受控）：行悬停展开，离行 150ms 后收回（移入面板则取消） */
const cascadeVisible = ref(false)
let cascadeCloseTimer: ReturnType<typeof setTimeout> | null = null

const cascadeTop = ref(0)

function openCascade(evt?: MouseEvent) {
  if (cascadeCloseTimer) {
    clearTimeout(cascadeCloseTimer)
    cascadeCloseTimer = null
  }
  // 面板顶边对齐触发行（offsetParent = 下拉容器）
  const row = evt?.currentTarget as HTMLElement | undefined
  if (row) cascadeTop.value = row.offsetTop
  cascadeVisible.value = true
}

function scheduleCascadeClose() {
  if (cascadeCloseTimer) clearTimeout(cascadeCloseTimer)
  cascadeCloseTimer = setTimeout(() => {
    cascadeVisible.value = false
    cascadeCloseTimer = null
  }, 150)
}

function cancelCascadeClose() {
  if (cascadeCloseTimer) {
    clearTimeout(cascadeCloseTimer)
    cascadeCloseTimer = null
  }
}

const displayName = computed(() => userStore.userInfo?.realName || userStore.userInfo?.username || 'U')
const avatarText = computed(() => avatarTextOf(userStore.userInfo))

/** 头像背景色：按用户名哈希取稳定颜色（无头像资源时用首字占位） */
const avatarColor = computed(() => {
  const name = userStore.userInfo?.username || 'u'
  const colors = ['#1890ff', '#722ed1', '#13c2c2', '#52c41a', '#fa8c16', '#eb2f96', '#2f54eb']
  let h = 0
  for (const c of name) h = (h * 31 + c.charCodeAt(0)) % 997
  return colors[h % colors.length]
})

/** 角色中文名（多角色拼接） */
const roleLabel = computed(() => {
  const roles = userStore.roles || []
  if (roles.length === 0) return '-'
  return roles
    .map((r) => {
      const labels: Record<string, string> = {
        super_admin: '超级管理员',
        kb_admin: '知识库管理员',
        kb_user: '知识库用户',
        readonly: '只读用户',
      }
      return labels[r as string] || r
    })
    .join('、')
})

/** 组织显示名：orgId → 组织名（懒加载一次映射表） */
const myOrgName = computed(() => {
  const orgId = userStore.userInfo?.orgId
  if (!orgId) return '未分配'
  return orgNameMap.value[orgId] || orgId
})

/** 用户下拉分组：逐项权限过滤，全隐时整组剔除（修订 #16） */
const visibleDropdownGroups = computed(() => {
  return navConfig.userMenuGroups
    .map((group) => ({ ...group, items: group.items.filter((item) => canShowPath(item.path, hasPermission)) }))
    .filter((group) => group.items.length > 0)
})

/** 平铺分组（门户资源）与级联分组（管理，修订 #17） */
const inlineGroups = computed(() => visibleDropdownGroups.value.filter((g) => !g.cascade))
const cascadeGroups = computed(() => visibleDropdownGroups.value.filter((g) => g.cascade))

function handleShowUserInfo() {
  userInfoDialog.value = true
  // 懒加载组织名映射（仅一次）
  if (Object.keys(orgNameMap.value).length === 0) {
    api
      .getOrgFlat()
      .then((res: any) => {
        const list = Array.isArray(res) ? res : []
        const map: Record<string, string> = {}
        list.forEach((o: any) => {
          if (o.id) map[o.id] = o.name
        })
        orgNameMap.value = map
      })
      .catch(() => {})
  }
}

async function handleLogout() {
  const ok = await confirm('确定要退出登录吗？')
  if (ok) {
    userStore.logout()
    router.push('/login')
  }
}

function handleProfile() {
  userMenuVisible.value = false
  cascadeVisible.value = false
  handleShowUserInfo()
}

function handleLogoutClick() {
  userMenuVisible.value = false
  cascadeVisible.value = false
  handleLogout()
}

function handleDropdownGroupClick(path: string) {
  userMenuVisible.value = false
  cascadeVisible.value = false
  router.push(path)
}

// 测试缝：popover/dialog 在 jsdom 内交互受限，暴露状态与处理器直驱
defineExpose({
  visibleDropdownGroups,
  inlineGroups,
  cascadeGroups,
  cascadeVisible,
  userMenuVisible,
  userInfoDialog,
  handleShowUserInfo,
  handleProfile,
  handleLogoutClick,
})
</script>

<template>
  <div class="user-entry" data-test="user-entry" title="用户菜单">
    <el-popover
      v-model:visible="userMenuVisible"
      placement="bottom-end"
      :width="220"
      trigger="click"
      popper-class="user-menu-popover"
    >
      <template #reference>
        <div class="avatar-chip">
          <div class="avatar-circle" :style="{ background: avatarColor }">
            <img v-if="userStore.userInfo?.avatar" :src="userStore.userInfo.avatar" alt="avatar" />
            <span v-else>{{ avatarText }}</span>
          </div>
          <el-icon :size="10" class="chip-arrow"><ArrowDown /></el-icon>
        </div>
      </template>
      <div class="user-menu">
        <div class="user-menu__header">
          <div class="user-menu__name">{{ displayName }}</div>
          <div class="user-menu__username">@{{ userStore.userInfo?.username || '-' }}</div>
        </div>
        <!-- 用户信息（头部正下方） -->
        <div class="user-menu__item" @click="handleUserMenu(navConfig.userMenuProfile)">
          <span>{{ navConfig.userMenuProfile.title }}</span>
        </div>
        <!-- 语义分组：门户资源（平铺）/ 管理（级联子菜单，修订 #17） -->
        <template v-for="group in inlineGroups" :key="group.title">
          <div class="user-menu__divider"></div>
          <div class="user-menu__group-title">{{ group.title }}</div>
          <div
            v-for="item in group.items"
            :key="item.key"
            class="user-menu__item"
            @click="handleDropdownGroupClick(item.path)"
          >
            <span>{{ item.title }}</span>
          </div>
        </template>
        <template v-for="group in cascadeGroups" :key="group.title">
          <div class="user-menu__divider"></div>
          <div
            class="user-menu__item user-menu__cascade-row"
            data-test="cascade-row"
            @mouseenter="openCascade"
            @mouseleave="scheduleCascadeClose"
          >
            <span>{{ group.title }}</span>
            <el-icon :size="12"><ArrowLeft /></el-icon>
          </div>
          <!-- 级联子面板：自绘绝对定位面板（修订 #17，ADP 企业管理式，向左弹出） -->
          <div
            v-if="cascadeVisible"
            class="cascade-panel"
            :style="{ top: cascadeTop + 'px' }"
            @mouseenter="cancelCascadeClose"
            @mouseleave="scheduleCascadeClose"
          >
            <div class="user-menu__group-title">{{ group.title }}</div>
            <div
              v-for="item in group.items"
              :key="item.key"
              class="user-menu__item"
              @click="handleDropdownGroupClick(item.path)"
            >
              <span>{{ item.title }}</span>
            </div>
          </div>
        </template>
        <!-- 退出系统：恒在最底（修订 #16） -->
        <div class="user-menu__divider"></div>
        <div class="user-menu__item user-menu__item--danger" @click="handleUserMenu(navConfig.userMenuLogout)">
          <span>{{ navConfig.userMenuLogout.title }}</span>
        </div>
      </div>
    </el-popover>

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
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.user-entry {
  display: flex;
  align-items: center;
}

// 级联子面板：主下拉容器内的绝对定位面板，向左弹出（修订 #17）
:deep(.user-menu) {
  position: relative;
}

.cascade-panel {
  position: absolute;
  right: calc(100% + 8px);
  min-width: 150px;
  background: $bg-white;
  border: 1px solid $border-light;
  border-radius: $radius-base;
  box-shadow: $shadow-lg;
  padding: 6px;
  z-index: 1;
}

.avatar-chip {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 3px 6px;
  border-radius: $radius-round;
  cursor: pointer;
  color: $text-secondary;
  transition: background 0.2s ease;

  &:hover {
    background: $bg-hover;
  }
}

.chip-arrow {
  transition: transform 0.2s ease;
}

.avatar-circle {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  flex-shrink: 0;
  overflow: hidden;
  user-select: none;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

// ============ 用户信息弹窗 ============
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
</style>

<!-- 弹出菜单样式：popover teleport 到 body，须用全局样式（自旧 Sidebar.vue 随迁） -->
<style lang="scss">
@use '@/assets/styles/variables' as *;

.user-menu-popover {
  padding: 6px !important;

  .user-menu__cascade-row {
    justify-content: space-between;

    .el-icon {
      color: $text-placeholder;
    }

    &:hover .el-icon {
      color: $text-primary;
    }
  }

  .user-menu {
    &__header {
      padding: 8px 10px 10px;
      border-bottom: 1px solid $border-light;
      margin-bottom: 4px;
    }

    &__name {
      font-size: 13px;
      font-weight: 600;
      color: $text-primary;
      line-height: 1.4;
    }

    &__username {
      font-size: 12px;
      color: $text-secondary;
      line-height: 1.4;
    }

    &__divider {
      height: 1px;
      background: $border-light;
      margin: 4px 0;
    }

    &__group-title {
      padding: 6px 10px 4px;
      font-size: 11px;
      font-weight: 500;
      color: $text-placeholder;
      letter-spacing: 0.5px;
      user-select: none;
    }

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

<style lang="scss">
/* 管理级联子面板（修订 #17）：独立 popper，复用菜单项款式 */
.admin-submenu-popover {
  padding: 6px !important;

  .user-menu__group-title {
    padding: 6px 10px 4px;
    font-size: 11px;
    font-weight: 500;
    color: #9ca3af;
    letter-spacing: 0.5px;
    user-select: none;
  }

  .user-menu__item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 10px;
    border-radius: 6px;
    font-size: 13px;
    color: #606266;
    cursor: pointer;
    transition: background 0.15s;

    &:hover {
      background: #f9fafb;
      color: #303133;
    }
  }
}
</style>
