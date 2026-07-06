<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

const pageTitle = computed(() => (route.meta.title as string) || '')
const pageDesc = computed(() => (route.meta.description as string) || '')
const breadcrumbs = computed(() => {
  const matched = route.matched.filter(item => item.meta?.title)
  return matched.map(item => ({
    title: item.meta.title as string,
    path: item.path,
  }))
})
</script>

<template>
  <div class="header">
    <div class="header-left">
      <h1 class="page-title">{{ pageTitle }}</h1>
      <span v-if="pageDesc" class="page-desc">{{ pageDesc }}</span>
      <el-breadcrumb v-if="breadcrumbs.length > 1" separator="/">
        <el-breadcrumb-item :to="{ path: '/home' }">首页</el-breadcrumb-item>
        <el-breadcrumb-item v-for="(crumb, idx) in breadcrumbs" :key="idx">
          {{ crumb.title }}
        </el-breadcrumb-item>
      </el-breadcrumb>
    </div>
    <div class="header-right">
      <!-- Teleport 目标：页面通过 <Teleport to="#header-actions"> 注入操作按钮 -->
      <div id="header-actions" class="header__teleport"></div>
      <slot name="actions"></slot>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 48px;
  padding: $spacing-md $spacing-xl;
  background: $bg-white;
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: $spacing-base;
  min-width: 0;
}

.page-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: $text-primary;
  line-height: 1.4;
  white-space: nowrap;
}

.page-desc {
  font-size: 12px;
  color: $text-secondary;
  white-space: nowrap;
}

.header-right {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  flex-shrink: 0;
}

// Teleport 目标
.header__teleport {
  display: contents;
}

// 面包屑覆盖
:deep(.el-breadcrumb) {
  font-size: 12px;

  .el-breadcrumb__inner {
    color: $text-secondary;

    &.is-link {
      color: $text-placeholder;

      &:hover {
        color: $color-primary;
      }
    }
  }

  .el-breadcrumb__separator {
    color: $border-base;
    font-weight: 400;
  }
}
</style>
