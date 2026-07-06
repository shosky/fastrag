<script setup lang="ts">
import Sidebar from './Sidebar.vue'
import Header from './Header.vue'
</script>

<template>
  <div class="app-layout">
    <Sidebar />
    <div class="app-main">
      <!-- 灰色底色活动区 -->
      <div class="app-content">
        <div class="content-card">
          <!-- 卡片头部：标题 + 面包屑 + 操作按钮 -->
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

.app-content {
  flex: 1;
  overflow: hidden;
  background: $bg-page;
  padding: 0;
  display: flex;
  flex-direction: column;
}

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

.card-body {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 0;
}
</style>

<!-- 全局覆盖 -->
<style lang="scss">
@use '@/assets/styles/variables' as *;

/* ===== card-header: 紧凑标题栏 ===== */
.content-card > .card-header {
  flex-shrink: 0;
  height: auto;
  padding: $spacing-lg $spacing-xl $spacing-md !important;
  background: transparent;
  border-bottom: 1px solid $border-light;
}

/* ===== card-body: 滚动区 =====
   padding-top = 0，让页面级 tabs/filter-bar 紧贴 header
   页面内部通过 .page-content 区域获取 padding
*/
.content-card > .card-body {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
}

/* ===== page-container: 页面内容容器 =====
   card-body 的直接子元素，提供 padding
   让内容区有呼吸感，tabs/filter 紧贴顶部
*/
.page-container {
  min-height: 100%;
}

/* ===== 页面级 tabs: 紧贴 Header 底部，形成一体感 ===== */
.page-container > .section-header,
.page-container > .el-tabs,
.page-container > .knowledge-header,
.page-container > .card-panel > .section-header {
  /* 这些区域紧贴 card-body 顶部 */
}

/* ===== 页面级 filter-bar: tabs 下方保留间距 ===== */
.page-container > .filter-bar,
.page-container > .card-panel > .filter-bar {
  margin-top: $spacing-base;
}

/* ===== card-panel 间距 ===== */
.page-container > .card-panel {
  padding: $spacing-xl;
}

/* ===== tab-pane 内容区间距 ===== */
.el-tab-pane {
  > .section-header {
    margin-bottom: $spacing-base;
    margin-top: $spacing-base;
  }

  > .filter-bar {
    margin-bottom: $spacing-lg;
  }

  > .el-table {
    margin-top: $spacing-base;
  }

  > .table-footer {
    margin-top: $spacing-lg;
    padding-top: $spacing-base;
  }
}
</style>
