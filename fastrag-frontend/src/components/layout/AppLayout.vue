<script setup lang="ts">
import Sidebar from './Sidebar.vue'
import Header from './Header.vue'
</script>

<template>
  <div class="app-layout">
    <Sidebar />
    <div class="app-main">
      <Header>
        <template #actions>
          <slot name="header-actions"></slot>
        </template>
      </Header>
      <div class="app-content">
        <router-view />
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
  overflow-y: auto;
  background: $bg-page;
}
</style>
