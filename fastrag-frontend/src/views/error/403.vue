<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

function goHome() {
  router.push('/home')
}

function goBack() {
  router.back()
}
</script>

<template>
  <div class="error-403">
    <div class="error-403__content">
      <div class="error-403__icon">🔒</div>
      <h1 class="error-403__title">403</h1>
      <p class="error-403__desc">抱歉，您没有权限访问此页面</p>
      <p class="error-403__hint">
        当前角色：<el-tag size="small">{{ userStore.userInfo?.roles?.[0] || '未知' }}</el-tag>
        如需更高权限，请联系管理员。
      </p>
      <div class="error-403__actions">
        <el-button @click="goBack">返回上一页</el-button>
        <el-button type="primary" @click="goHome">回到首页</el-button>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.error-403 {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: $bg-page;

  &__content {
    text-align: center;
    padding: 48px;
    background: $bg-white;
    border-radius: 12px;
    box-shadow: $shadow-base;
    max-width: 480px;
  }

  &__icon {
    font-size: 64px;
    margin-bottom: 16px;
  }

  &__title {
    font-size: 72px;
    font-weight: 700;
    color: $color-warning;
    margin: 0 0 8px;
  }

  &__desc {
    font-size: 18px;
    color: $text-primary;
    margin: 0 0 12px;
  }

  &__hint {
    font-size: 14px;
    color: $text-secondary;
    margin: 0 0 24px;
  }

  &__actions {
    display: flex;
    justify-content: center;
    gap: 12px;
  }
}
</style>
