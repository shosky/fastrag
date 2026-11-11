<script setup lang="ts">
import { computed } from 'vue'
import { registry, type WidgetComponentProps } from './registry'

// ===========================================================================
// Widget 节点递归渲染器（WidgetCardRenderer 内部件，非 DSL 白名单组件）
//
// 注册表解析 + 未知组件名降级（STATE-208：卡内占位 + console.warn，兄弟组件
// 照常渲染——降级发生在节点级而非整卡级）。19 个 DSL 组件经本件递归渲染 children。
// ===========================================================================

const props = defineProps<WidgetComponentProps>()

const comp = computed(() => (props.node && registry[props.node.type]) || null)

// 未知组件名：渲染期一次性告警（守护降级可观测，不抛错不崩卡）
if (props.node && !registry[props.node.type]) {
  // eslint-disable-next-line no-console
  console.warn(`[widget] 未注册的组件名「${props.node.type}」，降级占位（STATE-208）`)
}
</script>

<template>
  <div v-if="!comp" class="widget-w-unknown">不支持的组件：{{ node?.type || '?' }}</div>
  <component :is="comp" v-else :node="node" :ctx="ctx" />
</template>

<style scoped>
.widget-w-unknown {
  border: 1px dashed var(--el-border-color-darker, #dcdfe6);
  border-radius: var(--el-border-radius-base, 4px);
  background: var(--el-fill-color-light, #f5f7fa);
  color: var(--el-text-color-secondary, #909399);
  font-size: 12px;
  padding: 8px 10px;
  margin: 4px 0;
}
</style>
