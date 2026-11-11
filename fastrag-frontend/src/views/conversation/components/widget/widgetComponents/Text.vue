<script setup lang="ts">
import { propString, type WidgetComponentProps } from '../registry'

// Text：正文文本（{{var}} 插值已在树解析层完成；文本节点插值输出，禁 v-html）
const props = defineProps<WidgetComponentProps>()
const text = propString(props.node, 'text')
</script>

<template>
  <div class="widget-w-text">{{ text }}</div>
</template>

<style scoped>
.widget-w-text {
  font-size: 14px;
  color: var(--el-text-color-regular, #606266);
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
