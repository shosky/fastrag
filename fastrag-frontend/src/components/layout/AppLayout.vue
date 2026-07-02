<script setup lang="ts">
import Sidebar from './Sidebar.vue'
import Header from './Header.vue'
</script>

<template>
  <div class="app-layout">
    <Sidebar />
    <div class="app-main">
      <!-- 灰色底色活动区，四周统一 24px padding 环绕白色大卡片 -->
      <div class="app-content">
        <div class="content-card">
          <!-- 卡片头部：标题 + 面包屑 + 操作按钮（嵌入卡片内） -->
          <Header class="card-header">
            <template #actions>
              <slot name="header-actions"></slot>
            </template>
          </Header>
          <!-- 卡片主体：页面内容，可滚动 -->
          <div class="card-body">
            <router-view />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.app-layout {
  display: flex;
  width: 100%;
  height: 100%;
}

.app-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
}

// 灰色底色仅作为背景衬托 — 白色卡片撑满整个活动区，圆角处自然透出灰色
.app-content {
  flex: 1;
  overflow: hidden;
  background: $bg-page;
  padding: 0;
  display: flex;
  flex-direction: column;
}

// 纯白大卡片 — 圆角 20px + 柔和阴影
.content-card {
  flex: 1;
  background: $bg-white;
  border-radius: $radius-card;
  box-shadow: $shadow-card;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

// 卡片主体 — 内 padding + 纵向滚动
.card-body {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: $spacing-xl;
}
</style>

<!-- 全局覆盖：Header 嵌入卡片时重新适配 -->
<style lang="scss">
@use '@/assets/styles/variables' as *;

.content-card .card-header {
  flex-shrink: 0;
  height: auto;
  padding: $spacing-xl $spacing-xl 0 !important;
  background: transparent !important;
  border-bottom: 1px solid $border-light;
  padding-bottom: $spacing-base !important;
  margin-bottom: 0;
}
</style>
