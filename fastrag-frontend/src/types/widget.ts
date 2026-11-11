// ===========================================================================
// Widget 特性领域类型（共享 IDL 唯一定义点，MOD-WIDGET-FRONTEND ## 契约）
//
// 字段级形状照抄 fastrag-backend docs/interfaces.md「共享类型」（v1.0.0，跨端驼峰）；
// 消费方：src/api/widget.ts（资产 + 绑定四动词）、utils/widget/snapshot.ts（三个转换点）、
// WidgetCardRenderer（卡片渲染唯一入口）、WidgetConfig（绑定面板）。
// ===========================================================================

/** 统一响应包装（request.ts 响应拦截器解包 {code,data,message}，前端直接消费 data） */
export interface WidgetApiResponse<T> {
  code: number // 200=成功；业务错误 = HTTP 200 + 非 200 code
  message: string // 校验失败时为逐项行级错误（\n 分隔，ERR-WG-SCHEMA-INVALID）
  data: T | null
}

/** 资产列表行（API-001） */
export interface WidgetSummary {
  id: string // String ASSIGN_ID
  name: string
  description: string | null
  icon: string | null // 前端回退缺省图标（Postcard）
  isBuiltin: boolean // true=官方模板行（只读，仅可复制）
  creator: string // 属主 userId；"system"=官方播种
  createdAt: string // ISO datetime
  updatedAt: string
}

/** 资产详情（API-002）= 列表行 + 三件套全量 */
export interface WidgetDetail extends WidgetSummary {
  template: string // 组件树 JSON 字符串（{type,props,children[]}，{{var}} 插值）
  schemaJson: string // 变量声明 JSON 字符串
  defaultJson: string | null // 占位数据 JSON 字符串
}

/** 保存请求体（API-003/004 同族，全量草稿替换） */
export interface WidgetSaveRequest {
  name: string // 必填，非空白
  description?: string
  icon?: string
  template: string // 必填，合法 JSON 组件树
  schemaJson: string // 必填，合法 JSON 变量声明
  defaultJson?: string // 可选
}

/** 卡片动作负载（API-011 请求体扩展字段；一期仅 sys.submit 回传） */
export interface WidgetActionPayload {
  widgetId: string // 必填，卡片资产标识
  widgetKey: string // 必填，卡片替换键（LLM 生成，随快照下发）
  action: string // 必填，回传仅接受 'sys.submit'
  payload: Record<string, unknown> // 表单收集值并入的动作负载
}

/** 卡片快照五要素（API-012 tool_result.data；历史消息 toolCalls result.data 同构） */
export interface WidgetSnapshot {
  widgetId: string // 资产标识
  widgetKey: string // 卡片替换键：同键后到快照替换旧卡（过程反馈）
  name: string // 展示名
  template: string // emit 时刻定格的组件树 JSON（历史回看不受资产编辑影响）
  data: Record<string, unknown> // 本次渲染数据（default 先填充、data 覆盖的合并结果）
}

/** 应用绑定行视图（API-007） */
export interface AppWidgetBindingView {
  id: string // 绑定行标识
  widgetId: string
  widgetName: string
  enabled: boolean
  createdAt: string
}
