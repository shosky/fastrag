// ===========================================================================
// Widget 组件注册表（组件名 → SFC，MOD-WIDGET-FRONTEND ## seam「组件注册表缝」）
//
// 稳定侧：WidgetCardRenderer props（snapshot/interactive/preview）与
// resolveToolDisplayType 的 'widget' 型；本注册表是渲染分发的可扩展侧——
// 新增组件 = whitelist.ts 加白名单项 + 此处注册 + registry.test.ts 断言，零改动渲染器。
// ===========================================================================
import type { Component } from 'vue'
import type { WidgetActionSpec, WidgetNode } from '@/utils/widget/snapshot'

// ── 容器 ──
import WidgetCard from './widgetComponents/Card.vue'
import WidgetListView from './widgetComponents/ListView.vue'
import WidgetListViewItem from './widgetComponents/ListViewItem.vue'
// ── 布局 ──
import WidgetRow from './widgetComponents/Row.vue'
import WidgetCol from './widgetComponents/Col.vue'
import WidgetDivider from './widgetComponents/Divider.vue'
import WidgetForm from './widgetComponents/Form.vue'
// ── 文本 ──
import WidgetTitle from './widgetComponents/Title.vue'
import WidgetText from './widgetComponents/Text.vue'
import WidgetCaption from './widgetComponents/Caption.vue'
import WidgetMarkdown from './widgetComponents/Markdown.vue'
// ── 内容 ──
import WidgetBadge from './widgetComponents/Badge.vue'
import WidgetIcon from './widgetComponents/Icon.vue'
import WidgetImage from './widgetComponents/Image.vue'
// ── 交互 ──
import WidgetButton from './widgetComponents/Button.vue'
// ── 表单 ──
import WidgetInput from './widgetComponents/Input.vue'
import WidgetSelect from './widgetComponents/Select.vue'
import WidgetRadioGroup from './widgetComponents/RadioGroup.vue'
import WidgetCheckbox from './widgetComponents/Checkbox.vue'

/**
 * 渲染上下文（WidgetCardRenderer 注入给全部 19 组件的唯一交互面）：
 * 交互窗口/预览/提交中状态 + 表单收集值读写 + 动作分发。
 */
export interface WidgetRenderContext {
  /** 交互窗口开闭（STATE-209/210：仅最新一条助手消息为 true） */
  interactive: boolean
  /** 预览态（STATE-206：sys.submit 禁用 + 提示，工作台专用） */
  preview: boolean
  /** 回传提交中（STATE-211：loading 防重） */
  submitting: boolean
  /** 表单收集值（Form 控件按 props.name 读写） */
  getFormValue(name: string, fallback?: unknown): unknown
  setFormValue(name: string, value: unknown): void
  /** 动作分发（submit 回传 / go_to_url、download 本地处理 / 未知忽略+warn） */
  runAction(spec: WidgetActionSpec): void
}

/** 组件节点的公共 props 形状（全部 19 组件一致） */
export interface WidgetComponentProps {
  node: WidgetNode
  ctx: WidgetRenderContext
}

/** 组件名 → SFC 注册表（键集与后端白名单由 registry.test.ts 守护相等） */
export const registry: Record<string, Component> = {
  Card: WidgetCard,
  ListView: WidgetListView,
  ListViewItem: WidgetListViewItem,
  Row: WidgetRow,
  Col: WidgetCol,
  Divider: WidgetDivider,
  Form: WidgetForm,
  Title: WidgetTitle,
  Text: WidgetText,
  Caption: WidgetCaption,
  Markdown: WidgetMarkdown,
  Badge: WidgetBadge,
  Icon: WidgetIcon,
  Image: WidgetImage,
  Button: WidgetButton,
  Input: WidgetInput,
  Select: WidgetSelect,
  RadioGroup: WidgetRadioGroup,
  Checkbox: WidgetCheckbox,
}

/** 注册表键集（守护测试与后端白名单快照比对） */
export function registryKeys(): string[] {
  return Object.keys(registry).sort()
}

/** children 安全读取（坏结构 children 非数组 → 空数组，STATE-208 fail-soft 不抛） */
export function childrenOf(node: WidgetNode | null | undefined): WidgetNode[] {
  return node && Array.isArray(node.children) ? node.children : []
}

/** props 安全读取字符串属性（缺失/非字符串 → ''，STATE-208 fail-soft） */
export function propString(node: WidgetNode | null | undefined, key: string, fallback = ''): string {
  const v = node?.props?.[key]
  return typeof v === 'string' ? v : v === undefined || v === null ? fallback : String(v)
}

/** props 安全读取数组属性（选项列表等；非数组 → []） */
export function propArray<T = Record<string, unknown>>(node: WidgetNode | null | undefined, key: string): T[] {
  const v = node?.props?.[key]
  return Array.isArray(v) ? (v as T[]) : []
}
