<script setup lang="ts">
import { propString, type WidgetComponentProps } from '../registry'

// Caption：辅助说明文本（次级色小字）
const props = defineProps<WidgetComponentProps>()
const text = propString(props.node, 'text')
</script>

<template>
  <div class="widget-w-caption">{{ text }}</div>
</template>

<style scoped>
.widget-w-caption {
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
