<script setup lang="ts">
import { propString, type WidgetComponentProps } from '../registry'

// Divider：分隔线（可选 text 内容分隔）
const props = defineProps<WidgetComponentProps>()
const text = propString(props.node, 'text')
</script>

<template>
  <el-divider v-if="text" class="widget-w-divider" content-position="left">{{ text }}</el-divider>
  <el-divider v-else class="widget-w-divider" />
</template>

<style scoped>
.widget-w-divider {
  margin: 8px 0;
}
</style>
