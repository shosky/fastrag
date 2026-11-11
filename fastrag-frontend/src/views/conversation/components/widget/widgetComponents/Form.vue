<script setup lang="ts">
import { childrenOf, type WidgetComponentProps } from '../registry'
import WidgetNode from '../WidgetNode.vue'

// Form：表单容器（表单值收集在 WidgetCardRenderer 的 ctx.formValues，
// 按 Input/Select/RadioGroup/Checkbox 的 props.name 归集——值收集不依赖 Form 包裹）
const props = defineProps<WidgetComponentProps>()
const children = childrenOf(props.node)
</script>

<template>
  <div class="widget-w-form">
    <WidgetNode v-for="(child, i) in children" :key="i" :node="child" :ctx="ctx" />
  </div>
</template>

<style scoped>
.widget-w-form {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
</style>
