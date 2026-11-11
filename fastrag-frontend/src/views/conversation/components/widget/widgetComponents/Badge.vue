<script setup lang="ts">
import { computed } from 'vue'
import { propString, type WidgetComponentProps } from '../registry'

// Badge：状态标签（el-tag，type 缺省 info，仅认合法值防透传注入）
const props = defineProps<WidgetComponentProps>()
const text = propString(props.node, 'text')
const rawType = propString(props.node, 'type', 'info')
const tagType = (['success', 'warning', 'danger', 'info', 'primary'] as const).includes(
  rawType as 'success'
)
  ? (rawType as 'success' | 'warning' | 'danger' | 'info' | 'primary')
  : 'info'
const cls = computed(() => `el-tag--${tagType}`)
</script>

<template>
  <span class="widget-w-badge el-tag el-tag--small el-tag--light" :class="cls">{{ text }}</span>
</template>

<style scoped>
.widget-w-badge {
  align-self: flex-start;
  border-radius: var(--el-border-radius-base, 4px);
  padding: 0 8px;
}
</style>
