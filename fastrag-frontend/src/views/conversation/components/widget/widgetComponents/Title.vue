<script setup lang="ts">
import { computed } from 'vue'
import { propString, type WidgetComponentProps } from '../registry'

// Title：标题文本（level 1~3 三档字号）
const props = defineProps<WidgetComponentProps>()
const text = propString(props.node, 'text')
const rawLevel = props.node?.props?.level
const level = rawLevel === 1 || rawLevel === 2 || rawLevel === 3 ? rawLevel : 2
const cls = computed(() => `is-level-${level}`)
</script>

<template>
  <div class="widget-w-title" :class="cls">{{ text }}</div>
</template>

<style scoped>
.widget-w-title {
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
  line-height: 1.4;
  &.is-level-1 { font-size: 17px; }
  &.is-level-2 { font-size: 15px; }
  &.is-level-3 { font-size: 13px; }
}
</style>
