<script setup lang="ts">
import { computed } from 'vue'
import { childrenOf, propString, type WidgetComponentProps } from '../registry'
import type { WidgetActionSpec } from '@/utils/widget/snapshot'
import WidgetNode from '../WidgetNode.vue'

// ListViewItem：列表项（支持 onClickAction——配置 action 时整行可点，交互窗口/预览语义同 Button）
const props = defineProps<WidgetComponentProps>()

const children = childrenOf(props.node)

const actionSpec = computed<WidgetActionSpec>(() => ({
  type: propString(props.node, 'action') || propString(props.node, 'type'),
  url: propString(props.node, 'url'),
  alwaysEnabled: props.node?.props?.alwaysEnabled === true,
}))

const clickable = computed(
  () => !!(actionSpec.value.type || actionSpec.value.action) && props.ctx.interactive && !props.ctx.preview
)

function onClick() {
  if (!clickable.value) return
  props.ctx.runAction(actionSpec.value)
}
</script>

<template>
  <div
    class="widget-w-list-item"
    :class="{ 'is-clickable': clickable }"
    :role="clickable ? 'button' : undefined"
    :tabindex="clickable ? 0 : undefined"
    @click="onClick"
    @keydown.enter="onClick"
  >
    <WidgetNode v-for="(child, i) in children" :key="i" :node="child" :ctx="ctx" />
  </div>
</template>

<style scoped>
.widget-w-list-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border: 1px solid var(--el-border-color-lighter, #ebeef5);
  border-radius: var(--el-border-radius-base, 4px);
  background: var(--el-fill-color-blank, #ffffff);
  flex-wrap: wrap;
}
.widget-w-list-item.is-clickable {
  cursor: pointer;
  transition: background 0.15s;
}
.widget-w-list-item.is-clickable:hover {
  background: var(--el-fill-color-light, #f5f7fa);
}
</style>
