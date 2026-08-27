<script setup lang="ts" generic="T extends { rank: number; name: string }">
/**
 * 通用排行列表（运营模块共享）
 *
 * 渲染「序号徽章 + 名称」公共结构，附加字段（满意度/条数/Token/成本等）
 * 由父页面通过默认插槽注入，插槽作用域暴露当前 item（保留完整业务类型）。
 */
defineProps<{ items: T[] }>()
</script>

<template>
  <div>
    <div v-for="item in items" :key="item.rank" class="rank-item">
      <span class="rank" :class="{ 'top-3': item.rank <= 3 }">{{ item.rank }}</span>
      <span class="name">{{ item.name }}</span>
      <slot :item="item" />
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.rank-item {
  display: flex;
  align-items: center;
  gap: $spacing-base;
  padding: $spacing-sm 0;
  border-bottom: 1px solid $border-extra-light;
  .rank {
    width: 24px; height: 24px; border-radius: 50%; background: $border-lighter;
    display: flex; align-items: center; justify-content: center;
    font-size: 12px; font-weight: 600; color: $text-secondary;
    &.top-3 { background: $color-primary; color: #fff; }
  }
  .name { flex: 1; font-size: 13px; }
}
</style>
