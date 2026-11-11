<script setup lang="ts">
import { childrenOf, type WidgetComponentProps } from '../registry'
import WidgetNode from '../WidgetNode.vue'

// Col：列（span 1~24，缺省 24 单列堆叠）
const props = defineProps<WidgetComponentProps>()
const children = childrenOf(props.node)
const rawSpan = props.node?.props?.span
const span = typeof rawSpan === 'number' && rawSpan >= 1 && rawSpan <= 24 ? rawSpan : 24
</script>

<template>
  <el-col class="widget-w-col" :span="span">
    <WidgetNode v-for="(child, i) in children" :key="i" :node="child" :ctx="ctx" />
  </el-col>
</template>

<style scoped>
.widget-w-col {
  min-width: 0;
}
</style>
