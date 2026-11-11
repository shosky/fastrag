<script setup lang="ts">
import { childrenOf, type WidgetComponentProps } from '../registry'
import WidgetNode from '../WidgetNode.vue'

// Row：横向布局行（el-row 承载，children 需为 Col）
const props = defineProps<WidgetComponentProps>()
const children = childrenOf(props.node)
const gutter = typeof props.node?.props?.gutter === 'number' ? props.node.props.gutter : 12
</script>

<template>
  <el-row class="widget-w-row" :gutter="gutter">
    <WidgetNode v-for="(child, i) in children" :key="i" :node="child" :ctx="ctx" />
  </el-row>
</template>

<style scoped>
.widget-w-row {
  min-width: 0;
}
</style>
