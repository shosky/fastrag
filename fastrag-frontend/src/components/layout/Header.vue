<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

const pageTitle = computed(() => (route.meta.title as string) || '')
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
      <el-breadcrumb separator="/">
        <el-breadcrumb-item :to="{ path: '/home' }">首页</el-breadcrumb-item>
        <el-breadcrumb-item v-for="(crumb, idx) in breadcrumbs" :key="idx">
          {{ crumb.title }}
        </el-breadcrumb-item>
      </el-breadcrumb>
    </div>
    <div class="header-right">
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
  padding: $spacing-base $spacing-lg;
  background: $bg-white;
  border-bottom: 1px solid $border-lighter;
}

.header-left {
  display: flex;
  flex-direction: column;
  gap: $spacing-xs;
}

.page-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: $text-primary;
}

.header-right {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
}
</style>
