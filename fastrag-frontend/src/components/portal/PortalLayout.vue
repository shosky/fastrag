<script setup lang="ts">
import PortalTopNav from './PortalTopNav.vue'
</script>

<template>
  <div class="portal-layout">
    <PortalTopNav />
    <main class="portal-layout__main">
      <router-view />
    </main>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.portal-layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: $bg-page;
}

.portal-layout__main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
</style>
