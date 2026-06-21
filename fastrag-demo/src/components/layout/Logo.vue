<script setup lang="ts">
defineProps<{
  mini?: boolean
}>()
</script>

<template>
  <div class="logo" :class="{ mini }">
    <div class="logo-icon">
      <svg viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg">
        <rect width="40" height="40" rx="8" fill="#1E88E5"/>
        <path d="M12 10L12 30L20 30L20 24L18 24L18 16L22 16L22 24L20 24L20 30L28 30L28 24L26 24L26 16L28 16L28 10L22 10L22 16L18 16L18 10Z" fill="white"/>
      </svg>
    </div>
    <span v-if="!mini" class="logo-text">TORCHV</span>
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

  svg {
    width: 100%;
    height: 100%;
  }
}

.logo-text {
  font-size: 18px;
  font-weight: 700;
  color: $color-primary;
  white-space: nowrap;
}
</style>
