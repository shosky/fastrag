<script setup lang="ts">
import { useSystemStore } from '@/stores/system'

defineProps<{
  mini?: boolean
}>()

const systemStore = useSystemStore()
</script>

<template>
  <div class="logo" :class="{ mini }">
    <div class="logo-icon">
      <img v-if="systemStore.logoUrl" :src="systemStore.logoUrl" alt="Logo" />
      <svg v-else viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg">
        <rect width="40" height="40" rx="8" fill="#1E88E5"/>
        <path d="M12 10L12 30L20 30L20 24L18 24L18 16L22 16L22 24L20 24L20 30L28 30L28 24L26 24L26 16L28 16L28 10L22 10L22 16L18 16L18 10Z" fill="white"/>
      </svg>
    </div>
    <span v-if="!mini" class="logo-text">{{ systemStore.systemName }}</span>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.logo {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-base;

  &.mini {
    justify-content: center;
    padding: $spacing-base $spacing-sm;
  }
}

.logo-icon {
  width: 32px;
  height: 32px;
  flex-shrink: 0;

  img, svg {
    width: 100%;
    height: 100%;
    object-fit: contain;
  }
}

.logo-text {
  font-size: 18px;
  font-weight: 700;
  color: $color-primary;
  white-space: nowrap;
}
</style>
