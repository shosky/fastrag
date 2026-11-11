// ===========================================================================
// Widget 快照转换点（widget 链映射钉名的三个转换点 + 模板 DSL 解析/插值）
//
// 契约：fastrag-backend docs/interfaces.md API-011/012 + ADR-0011-WIDGET-TEMPLATE-DSL。
// 模板 DSL = JSON 组件树 {type,props,children[]}，字符串 prop 支持 {{var}} 插值，
// 无表达式/条件语法；渲染数据 = default 先填充、data 覆盖合并（后端产快照时已合并，
// 前端 preview 路径以 defaultJson 充当渲染数据）。
//
// 全部 fail-soft：非法输入返回 null / 空对象并 console.warn，绝不抛出——
// 渲染端据此走 STATE-208 降级占位（卡内占位不崩整卡）。
// ===========================================================================
import type { WidgetActionPayload, WidgetSnapshot } from '@/types/widget'

/** 模板组件树节点（ADR-0011：{type,props,children[]}） */
export interface WidgetNode {
  type: string
  props?: Record<string, unknown>
  children?: WidgetNode[]
}

/** 动作规格（模板 Button/ListViewItem 的 action 配置与卡片动作行共用） */
export interface WidgetActionSpec {
  type?: string // 'sys.submit' | 'sys.go_to_url' | 'sys.download' | 未知
  action?: string // 同 type 的别名拼写
  text?: string
  url?: string
  alwaysEnabled?: boolean // 置灰窗口豁免（STATE-210）
}

// ── 工具结果 → 快照（实时 SSE 与历史 toolCalls 同构唯一转换点）──

let warnedOnce = false

/**
 * API-012 tool_result（或历史消息 toolCalls 的 result）→ WidgetSnapshot。
 * success=false（工具路径错误，Error 文本回灌 LLM）或无 data → null（STATE-207 路径不产卡）；
 * 五要素缺一/类型不符 → null + console.warn（fail-soft，前端无卡片可渲染、对话不中断）。
 */
export function toWidgetSnapshot(
  toolResult: { success?: boolean; data?: unknown } | null | undefined
): WidgetSnapshot | null {
  if (!toolResult || toolResult.success === false || !toolResult.data) return null
  const d = toolResult.data as Record<string, unknown>
  const ok =
    typeof d.widgetId === 'string' && d.widgetId !== '' &&
    typeof d.widgetKey === 'string' && d.widgetKey !== '' &&
    typeof d.name === 'string' &&
    typeof d.template === 'string' && d.template !== '' &&
    d.data !== null && typeof d.data === 'object' && !Array.isArray(d.data)
  if (!ok) {
    if (!warnedOnce) {
      console.warn('[widget] tool_result.data 不是合法快照五要素，卡片降级（STATE-208）', d)
      warnedOnce = true
    }
    return null
  }
  return {
    widgetId: d.widgetId as string,
    widgetKey: d.widgetKey as string,
    name: d.name as string,
    template: d.template as string,
    data: d.data as Record<string, unknown>,
  }
}

// ── 模板解析与插值 ──

/**
 * 模板 JSON 字符串 → 组件树根节点。坏 JSON / 非对象 / 缺 type → null（STATE-208）。
 * children 非数组在解析层容忍（由渲染端逐节点降级，T2.3）。
 */
export function parseTemplate(template: string): WidgetNode | null {
  if (typeof template !== 'string' || !template.trim()) return null
  let parsed: unknown
  try {
    parsed = JSON.parse(template)
  } catch {
    console.warn('[widget] 模板 JSON 解析失败，卡片降级（STATE-208）')
    return null
  }
  if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) return null
  const node = parsed as Record<string, unknown>
  if (typeof node.type !== 'string' || !node.type) return null
  return node as unknown as WidgetNode
}

/** {{var}} 占位插值（仅字符串；data 有该变量出其值，两侧皆无出空串——空串插值即隐藏文本） */
export function interpolate(text: string, data: Record<string, unknown>): string {
  return text.replace(/\{\{\s*([\w.]+)\s*\}\}/g, (_m, key: string) => {
    const value = key.split('.').reduce<unknown>(
      (acc, k) => (acc !== null && typeof acc === 'object' ? (acc as Record<string, unknown>)[k] : undefined),
      data
    )
    if (value === undefined || value === null) return ''
    return typeof value === 'string' ? value : String(value)
  })
}

/** 深度插值节点 props：字符串逐个替换，数组/对象内字符串递归（选项列表 label 等） */
export function interpolateProps(
  props: Record<string, unknown> | undefined,
  data: Record<string, unknown>
): Record<string, unknown> {
  const out: Record<string, unknown> = {}
  for (const [k, v] of Object.entries(props || {})) {
    out[k] = interpolateValue(v, data)
  }
  return out
}

function interpolateValue(v: unknown, data: Record<string, unknown>): unknown {
  if (typeof v === 'string') return interpolate(v, data)
  if (Array.isArray(v)) return v.map((item) => interpolateValue(item, data))
  if (v !== null && typeof v === 'object') {
    const obj: Record<string, unknown> = {}
    for (const [k, item] of Object.entries(v as Record<string, unknown>)) {
      obj[k] = interpolateValue(item, data)
    }
    return obj
  }
  return v
}

/** 递归插值整棵树（渲染数据合并完成后的唯一渲染入口形状） */
export function interpolateTree(node: WidgetNode, data: Record<string, unknown>): WidgetNode {
  return {
    type: node.type,
    props: interpolateProps(node.props, data),
    children: Array.isArray(node.children) ? node.children.map((c) => interpolateTree(c, data)) : undefined,
  }
}

/**
 * 渲染数据合并规则（ADR-0011）：default 先填充、data 覆盖——同名出 data 值，
 * default 独有出 default 值。运行时快照由后端产码合并；前端预览/未来覆盖场景共用本纯函数。
 */
export function mergeRenderData(
  defaults: Record<string, unknown> | null | undefined,
  data: Record<string, unknown> | null | undefined
): Record<string, unknown> {
  return { ...(defaults || {}), ...(data || {}) }
}

// ── 预览快照（编辑页预览面板数据源，STATE-206）──

/** 预览态合成标识（仅本地渲染用，不回传） */
export const WIDGET_PREVIEW_ID = '__preview__'

/**
 * 编辑态 template + defaultJson → 预览快照（preview=true 渲染变体的数据源）。
 * defaultJson 为占位数据（渲染数据源，快照 #21：默认值 JSON 直接填充模板，零接口）；
 * 坏 JSON 归一空对象（模板仍可渲染，未填变量出空串）。
 */
export function toPreviewSnapshot(template: string, defaultJson: string | null | undefined): WidgetSnapshot | null {
  const node = parseTemplate(template)
  if (!node) return null
  let data: Record<string, unknown> = {}
  if (defaultJson && defaultJson.trim()) {
    try {
      const parsed = JSON.parse(defaultJson)
      if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) data = parsed as Record<string, unknown>
    } catch {
      console.warn('[widget] 预览 defaultJson 非法，按空数据处理')
    }
  }
  return {
    widgetId: WIDGET_PREVIEW_ID,
    widgetKey: WIDGET_PREVIEW_ID,
    name: node && typeof (node.props as Record<string, unknown> | undefined)?.title === 'string'
      ? String((node.props as Record<string, unknown>).title)
      : '预览',
    template,
    data,
  }
}

// ── 回传负载组装（API-011 widgetAction 字段）──

/**
 * 卡片动作 + 表单收集值 → API-011 WidgetActionPayload。
 * action 固定 'sys.submit'（go_to_url/download 前端本地处理不回传）；表单值并入 payload。
 */
export function buildWidgetAction(
  snapshot: WidgetSnapshot,
  formValues: Record<string, unknown>
): WidgetActionPayload {
  return {
    widgetId: snapshot.widgetId,
    widgetKey: snapshot.widgetKey,
    action: 'sys.submit',
    payload: { ...(formValues || {}) },
  }
}

// ── 动作语义判别（STATE-210 豁免 + 本地处理分流）──

/** 提交类动作（回传智能体；preview 态禁用） */
export function isSubmitAction(spec: WidgetActionSpec): boolean {
  const t = spec.type || spec.action || ''
  return t === 'sys.submit'
}

/** 跳转/下载类动作（前端本地处理；alwaysEnabled 豁免置灰窗口） */
export function isLocalAction(spec: WidgetActionSpec): boolean {
  const t = spec.type || spec.action || ''
  return t === 'sys.go_to_url' || t === 'sys.download'
}

/** 未知动作类型（忽略 + warn，不误跳转不崩卡） */
export function isUnknownAction(spec: WidgetActionSpec): boolean {
  const t = spec.type || spec.action || ''
  return t !== '' && !isSubmitAction(spec) && !isLocalAction(spec)
}
