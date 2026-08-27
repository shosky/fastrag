<script setup lang="ts">
/**
 * 通用指标卡片组（运营模块共享）
 *
 * feedback 页与 model-monitor 页样式/结构完全一致，抽为共享组件。
 * - feedback：5 列，value 后可带 suffix（如 %）
 * - model-monitor：4 列，value 下可带环比 change 与 trend
 */
interface MetricCardItem {
  label: string
  value: string | number
  /** 数值后缀，如 '%' */
  suffix?: string
  /** 环比变化文本，如 '+12.3%'（不传则不渲染） */
  change?: string
  /** 趋势方向（up/down 决定变化文本颜色） */
  trend?: 'up' | 'down'
}

withDefaults(defineProps<{ items: MetricCardItem[]; columns?: number }>(), {
  columns: 4,
})
</script>

<template>
  <div class="metric-cards" :style="{ gridTemplateColumns: `repeat(${columns}, 1fr)` }">
    <div v-for="m in items" :key="m.label" class="metric-card">
      <div class="metric-label">{{ m.label }}</div>
      <div class="metric-value">{{ m.value }}{{ m.suffix || '' }}</div>
      <div v-if="m.change !== undefined" class="metric-change" :class="m.trend">
        {{ m.change }}
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.metric-cards {
  display: grid;
  gap: $spacing-base;
  margin-bottom: $spacing-base;
}

.metric-card {
  background: $bg-white;
  border-radius: $radius-base;
  padding: $spacing-lg;
  .metric-label { font-size: 13px; color: $text-secondary; margin-bottom: $spacing-sm; }
  .metric-value { font-size: 24px; font-weight: 700; margin-bottom: $spacing-xs; }
  .metric-change {
    font-size: 12px;
    &.up { color: $color-danger; }
    &.down { color: $color-success; }
  }
}
</style>
