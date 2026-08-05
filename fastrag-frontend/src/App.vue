<script setup lang="ts">
import { onMounted } from 'vue'
import { RouterView } from 'vue-router'
import { useSystemStore } from '@/stores/system'
import { useUserStore } from '@/stores/user'

const systemStore = useSystemStore()
const userStore = useUserStore()

onMounted(() => {
  systemStore.loadConfig()
  // 刷新页面后向后端同步最新权限（localStorage 中的 userInfo 可能是旧数据）
  if (userStore.isLoggedIn) {
    userStore.fetchUserInfo()
  }
})
</script>

<template>
  <RouterView />
</template>

<style>
</style>
