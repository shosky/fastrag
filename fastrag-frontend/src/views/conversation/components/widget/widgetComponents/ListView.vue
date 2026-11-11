<script setup lang="ts">
import { childrenOf, propString, type WidgetComponentProps } from '../registry'
import WidgetNode from '../WidgetNode.vue'

// ListView：列表容器（children 结构化表达列表，非语法层循环——ADR-0011）
const props = defineProps<WidgetComponentProps>()
const children = childrenOf(props.node)
const gap = propString(props.node, 'gap', '8px')
</script>

<template>
  <div class="widget-w-list" :style="{ gap }">
    <WidgetNode v-for="(child, i) in children" :key="i" :node="child" :ctx="ctx" />
  </div>
</template>

<style scoped>
.widget-w-list {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
</style>
