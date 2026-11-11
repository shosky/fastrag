<script setup lang="ts">
import { computed } from 'vue'
import { propString, type WidgetComponentProps } from '../registry'
import { isLocalAction, isSubmitAction } from '@/utils/widget/snapshot'

// Button：动作按钮（sys.submit 回传 / sys.go_to_url、sys.download 本地处理；
// 交互窗口置灰 STATE-210 + alwaysEnabled 豁免；preview 不再参与禁用（2026-10-31 联调裁决：预览正常渲染）；
// 未知 action 类型点击忽略 + warn 不崩卡）
const props = defineProps<WidgetComponentProps>()

const spec = computed(() => ({
  type: propString(props.node, 'action') || propString(props.node, 'type'),
  url: propString(props.node, 'url'),
  alwaysEnabled: props.node?.props?.alwaysEnabled === true,
}))

const text = propString(props.node, 'text', '操作')
const submitKind = computed(() => isSubmitAction(spec.value))
const primary = props.node?.props?.primary === true

/** 原生 disabled 态（置灰视觉走 --el-button-disabled-text-color，MOD ## 视觉） */
const disabled = computed(() => {
  // preview 变体：视觉可点、零副作用（runAction 顶部短路），不参与禁用（2026-10-31 联调裁决）
  if (props.ctx.preview) return false
  if (submitKind.value) {
    // 提交类：交互窗口关闭 / 提交中禁用
    return !props.ctx.interactive || props.ctx.submitting
  }
  // 本地类（跳转/下载）：置灰窗口内 alwaysEnabled 豁免保持可点
  return !(props.ctx.interactive || spec.value.alwaysEnabled)
})

function onClick() {
  props.ctx.runAction(spec.value)
}
</script>

<template>
  <el-button
    class="widget-w-button"
    size="small"
    :type="primary ? 'primary' : 'default'"
    :class="{ 'is-exempt': !ctx.interactive && spec.alwaysEnabled }"
    :disabled="disabled"
    @click="onClick"
  >
    {{ text }}{{ submitKind && ctx.submitting ? '…' : '' }}
  </el-button>
</template>

<style scoped>
/* 置灰窗口内 alwaysEnabled 豁免动作保持 enabled 视觉并加弱高亮边区分（T9.3 可辨识） */
.widget-w-button.is-exempt {
  border-color: var(--el-color-primary-light-5, #a0cfff);
}
</style>
