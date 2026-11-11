<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import type { WidgetActionPayload, WidgetSnapshot } from '@/types/widget'
import {
  buildWidgetAction,
  interpolateTree,
  isLocalAction,
  isSubmitAction,
  isUnknownAction,
  parseTemplate,
  type WidgetActionSpec,
  type WidgetNode as WidgetNodeData,
} from '@/utils/widget/snapshot'
import { registry, type WidgetRenderContext } from './registry'
import WidgetNode from './WidgetNode.vue'

// ===========================================================================
// WidgetCardRenderer —— 卡片渲染唯一入口（MOD-WIDGET-FRONTEND ## 契约）
//
// 三处消费：会话中心消息流 / 应用编辑器调试流（经 ToolCallRenderer 分发）/
// 工作台编辑页预览（interactive=false + preview=true 变体，非平行实现）。
//
// 不变量（契约）：
// - 未知组件名/模板结构坏/快照缺失 → 卡内占位不崩整卡（fail-soft + console.warn，STATE-208）
// - 渲染数据 = default 先填充、data 覆盖的合并结果；{{var}} 插值仅作用字符串 prop
// - 除 Markdown 组件经 renderMarkdown 消毒外禁 v-html；Image src 仅 http(s)
// - 交互窗口按消息序判定（props.interactive）；preview 仅作视觉变体——按钮正常渲染、不出现禁用与提示（2026-10-31 联调裁决），回传 emit 仍被 interactive=false 挡住
// - 回传生命周期（STATE-211/212）由本件本地 submitting 承载：交互窗口 false→true
//   重开（回传失败回滚消息序）时复位，卡片保持可交互可直接重试
// ===========================================================================

const props = withDefaults(
  defineProps<{
    /** 快照五要素；缺失/null → 整卡占位（STATE-208） */
    snapshot?: WidgetSnapshot | null
    /** 交互窗口开闭：仅最新一条助手消息为 true（STATE-209/210） */
    interactive?: boolean
    /** 预览态：sys.submit 禁用 + 提示文案（STATE-206，工作台专用） */
    preview?: boolean
  }>(),
  { snapshot: null, interactive: false, preview: false }
)

const emit = defineEmits<{
  /** 用户点击 sys.submit 且 interactive && !preview 时触发，由消息流层组装回传 */
  (e: 'action', action: WidgetActionPayload): void
}>()

// ── 模板解析 + 插值（渲染数据 = 快照 data，后端已按 default 先填充、data 覆盖合并）──
const tree = computed<WidgetNodeData | null>(() => parseTemplate(props.snapshot?.template || ''))
const data = computed<Record<string, unknown>>(() =>
  props.snapshot && props.snapshot.data && typeof props.snapshot.data === 'object'
    ? props.snapshot.data
    : {}
)
const resolvedTree = computed<WidgetNodeData | null>(() =>
  tree.value ? interpolateTree(tree.value, data.value) : null
)

/** 根 Card 的 status 状态文本（状态文本区：snapshot.name + Card.status） */
const cardStatus = computed(() => {
  const s = tree.value?.props?.status
  return typeof s === 'string' ? s : ''
})

/** 根 Card 动作行配置（actions: [{type,text,url,alwaysEnabled}]，可选） */
const rootActions = computed<WidgetActionSpec[]>(() => {
  const a = tree.value?.props?.actions
  return Array.isArray(a) ? (a as WidgetActionSpec[]) : []
})

// ── 表单收集值 + 回传生命周期（STATE-211/212）──
const formValues = reactive<Record<string, unknown>>({})
const submitting = ref(false)

const ctx: WidgetRenderContext = {
  get interactive() {
    return !!props.interactive
  },
  get preview() {
    return !!props.preview
  },
  get submitting() {
    return submitting.value
  },
  getFormValue(name: string, fallback?: unknown) {
    return name in formValues ? formValues[name] : fallback
  },
  setFormValue(name: string, value: unknown) {
    formValues[name] = value
  },
  runAction(spec: WidgetActionSpec) {
    runAction(spec)
  },
}

/** 快照替换（同 widgetKey 后到快照）/ 回传失败窗口重开 → 提交态与表单复位 */
watch(
  () => props.snapshot,
  () => {
    submitting.value = false
    for (const k of Object.keys(formValues)) delete formValues[k]
  }
)

// 回传失败：消息序回滚 → 交互窗口 false→true 重开 → 解除提交中、恢复可交互可重试（STATE-212）
watch(
  () => props.interactive,
  (now, before) => {
    if (now && before === false) submitting.value = false
  }
)

function runAction(spec: WidgetActionSpec) {
  if (isUnknownAction(spec)) {
    // 未知 action type：忽略 + warn，不误跳转不崩卡
    console.warn(`[widget] 未知动作类型「${spec.type || spec.action}」，已忽略`)
    return
  }
  // preview 变体：完整渲染、零副作用——任何动作点击在此短路（视觉仍为可点态）
  if (props.preview) return
  if (isSubmitAction(spec)) {
    if (!props.interactive || submitting.value) return
    submitting.value = true // 提交即 loading 防重（STATE-211）
    emit('action', buildWidgetAction(props.snapshot!, { ...formValues }))
    return
  }
  if (isLocalAction(spec)) {
    // 置灰窗口豁免：alwaysEnabled 的 go_to_url/download 仍可点
    if (!props.interactive && !spec.alwaysEnabled) return
    const url = typeof spec.url === 'string' ? spec.url : ''
    if (!/^https?:\/\//i.test(url)) {
      console.warn(`[widget] 动作 url 非法（仅 http/https），已忽略：${url}`)
      return
    }
    if (spec.type === 'sys.go_to_url') {
      window.open(url, '_blank', 'noopener')
    } else {
      // sys.download：临时 a[download] 触发下载
      const a = document.createElement('a')
      a.href = url
      a.download = ''
      document.body.appendChild(a)
      a.click()
      a.remove()
    }
  }
}

function onRootAction(spec: WidgetActionSpec) {
  runAction(spec)
}

/** 动作按钮禁用态（与 Button 组件同语义，卡片动作行共用） */
function isActionDisabled(spec: WidgetActionSpec): boolean {
  if (props.preview) return false
  if (isSubmitAction(spec)) return !props.interactive || submitting.value
  if (isLocalAction(spec)) return !(props.interactive || spec.alwaysEnabled)
  return false // 未知类型保持可点（点击 warn 忽略），不误禁豁免视觉
}

/** 动作按钮文案：提交中追加 loading 省略号（STATE-211 可见反馈） */
function actionLabel(spec: WidgetActionSpec): string {
  return (spec.text || '操作') + (isSubmitAction(spec) && submitting.value ? '…' : '')
}
</script>

<template>
  <div
    class="widget-card"
    :class="{ 'is-disabled': !interactive && !preview, 'is-preview': preview }"
  >
    <!-- 快照缺失：整卡占位，正文消息不受影响（STATE-208） -->
    <div v-if="!snapshot" class="widget-card__fallback">卡片数据缺失，无法渲染</div>

    <template v-else>
      <!-- 状态文本区：snapshot.name + Card.status -->
      <div class="widget-card__status">
        <span class="widget-card__name">{{ snapshot.name }}</span>
        <span v-if="cardStatus" class="widget-card__status-text">{{ cardStatus }}</span>
      </div>

      <!-- 组件树递归渲染区；模板结构坏 → 卡内占位（STATE-208） -->
      <div class="widget-card__body">
        <WidgetNode v-if="resolvedTree" :node="resolvedTree" :ctx="ctx" />
        <div v-else class="widget-card__fallback">卡片模板无法渲染</div>
      </div>

      <!-- 动作按钮行（根 Card actions 配置；模板内 Button 组件亦各自可配动作） -->
      <div v-if="rootActions.length" class="widget-card__actions">
        <el-button
          v-for="(spec, i) in rootActions"
          :key="i"
          size="small"
          :class="{ 'is-exempt': !interactive && spec.alwaysEnabled }"
          :disabled="isActionDisabled(spec)"
          @click="onRootAction(spec)"
        >
          {{ actionLabel(spec) }}
        </el-button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.widget-card {
  border: 1px solid var(--el-border-color-lighter, #ebeef5);
  border-radius: var(--el-border-radius-base, 4px);
  background: var(--el-fill-color-blank, #ffffff);
  padding: 16px;
  max-width: 100%;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

/* 状态文本区：次级色 12px，置卡片顶部（MOD ## 视觉） */
.widget-card__status {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
}
.widget-card__name {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
}

.widget-card__body {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.widget-card__actions {
  display: flex;
  gap: 8px;
  border-top: 1px solid var(--el-border-color-extra-light, #f2f6fc);
  padding-top: 10px;
}
.widget-card__actions .is-exempt {
  border-color: var(--el-color-primary-light-5, #a0cfff);
}

/* 卡内降级占位（STATE-208）：不崩整卡 */
.widget-card__fallback {
  border: 1px dashed var(--el-border-color-darker, #dcdfe6);
  border-radius: var(--el-border-radius-base, 4px);
  background: var(--el-fill-color-light, #f5f7fa);
  color: var(--el-text-color-secondary, #909399);
  font-size: 12px;
  padding: 12px 10px;
  text-align: center;
}
</style>
