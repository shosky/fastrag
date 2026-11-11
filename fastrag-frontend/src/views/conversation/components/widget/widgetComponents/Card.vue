<script setup lang="ts">
import { childrenOf, propString, type WidgetComponentProps } from '../registry'
import WidgetNode from '../WidgetNode.vue'

// Card：卡片容器（可选 title 标题行 + children 堆叠）
const props = defineProps<WidgetComponentProps>()
const title = propString(props.node, 'title')
const children = childrenOf(props.node)
</script>

<template>
  <div class="widget-w-card-box">
    <div v-if="title" class="widget-w-card-box__title">{{ title }}</div>
    <WidgetNode v-for="(child, i) in children" :key="i" :node="child" :ctx="ctx" />
  </div>
</template>

<style scoped>
.widget-w-card-box {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.widget-w-card-box__title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
}
</style>
