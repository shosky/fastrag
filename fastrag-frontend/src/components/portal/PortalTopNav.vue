<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { PORTAL_MENUS } from '@/constants/portalMenus'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// 门户公开访问：游客显示「登录」，已登录显示用户名 + 退出
const isLoggedIn = computed(() => userStore.isLoggedIn)

// 当前路由归属的菜单项：取匹配的最长 path（/portal/manual 归「操作手册」而非「首页」）
const activeKey = computed(() => {
  const matched = PORTAL_MENUS.filter(
    (m) => route.path === m.path || route.path.startsWith(m.path + '/')
  ).sort((a, b) => b.path.length - a.path.length)
  return matched[0]?.key ?? ''
})

async function handleLogout() {
  const ok = await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => true)
    .catch(() => false)
  if (ok) {
    userStore.logout()
    router.push('/login')
  }
}
</script>

<template>
  <header class="portal-nav">
    <div class="portal-nav__inner">
      <router-link to="/portal" class="portal-nav__brand">
        <span class="portal-nav__logo">FR</span>
        <span class="portal-nav__name">FastRAG</span>
      </router-link>

      <nav class="portal-nav__menus" aria-label="门户导航">
        <router-link
          v-for="menu in PORTAL_MENUS"
          :key="menu.key"
          :to="menu.path"
          class="portal-nav__item"
          :class="{ active: activeKey === menu.key }"
        >
          {{ menu.title }}
        </router-link>
      </nav>

      <div class="portal-nav__user">
        <template v-if="isLoggedIn">
          <el-button type="primary" class="portal-nav__enter" @click="router.push('/home')">
            进入平台
          </el-button>
          <span class="portal-nav__username">{{ userStore.username }}</span>
          <el-button text class="portal-nav__logout" @click="handleLogout">退出登录</el-button>
        </template>
        <el-button v-else type="primary" class="portal-nav__login" @click="router.push('/login')">
          立即开始
        </el-button>
      </div>
    </div>
  </header>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.portal-nav {
  flex-shrink: 0;
  background: $bg-white;
  border-bottom: 1px solid $border-light;
}

.portal-nav__inner {
  display: flex;
  align-items: center;
  gap: $spacing-xl;
  height: 56px;
  padding: 0 $spacing-xl;
}

.portal-nav__brand {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  text-decoration: none;
  flex-shrink: 0;
}

.portal-nav__logo {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: $radius-base;
  background: $color-primary;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
}

.portal-nav__name {
  font-size: 16px;
  font-weight: 600;
  color: $text-primary;
}

.portal-nav__menus {
  display: flex;
  align-items: center;
  gap: $spacing-base;
  flex: 1;
  overflow-x: auto;
  white-space: nowrap;
}

.portal-nav__item {
  padding: 6px $spacing-base;
  border-radius: $radius-base;
  color: $text-regular;
  text-decoration: none;
  font-size: 14px;
  transition: background 0.2s, color 0.2s;

  &:hover {
    background: $bg-hover;
    color: $color-primary;
  }

  &.active {
    background: $color-primary-light;
    color: $color-primary;
    font-weight: 600;
  }
}

.portal-nav__user {
  display: flex;
  align-items: center;
  gap: $spacing-base;
  flex-shrink: 0;
}

.portal-nav__username {
  font-size: 13px;
  color: $text-secondary;
}
</style>
