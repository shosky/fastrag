// ===========================================================================
// Widget 资产与绑定 API（widget 链，MOD-WIDGET-FRONTEND ## 数据）
//
// 契约：fastrag-backend docs/interfaces.md API-001~010（v1.0.0，只增不改）。
// 命名导出风格照 src/api/index.ts 惯例；request 响应拦截器解包 ApiResponse，
// 业务错误（409 上限 / 400 校验等）reject Error(message) 由调用方 catch 呈现。
// VM 转换点 toWidgetListItem / toWidgetDraft 同文件导出（ui.md「widget 链映射」）。
// ===========================================================================
import request from '@/utils/request'
import type {
  AppWidgetBindingView,
  WidgetDetail,
  WidgetSaveRequest,
  WidgetSummary,
} from '@/types/widget'

// ===========================================================================
// 资产 CRUD + 复制（API-001~006）
// ===========================================================================

/** API-001 资产列表：builtin=true 官方模板视图，缺省 false=我的资产（属主过滤在服务端） */
export async function getWidgets(builtin?: boolean) {
  return request.get<unknown, WidgetSummary[]>('/widgets', { params: builtin === undefined ? undefined : { builtin } })
}

/** API-002 资产详情（编辑页与官方查看态加载三件套全量） */
export async function getWidget(id: string) {
  return request.get<unknown, WidgetDetail>(`/widgets/${id}`)
}

/** API-003 创建资产（保存即生效；400=ERR-WG-SCHEMA-INVALID，message \n 分行） */
export async function createWidget(body: WidgetSaveRequest) {
  return request.post<unknown, { id: string }>('/widgets', body)
}

/** API-004 修改资产（全量草稿替换；409=官方模板行只读） */
export async function updateWidget(id: string, body: WidgetSaveRequest) {
  return request.put<unknown, null>(`/widgets/${id}`, body)
}

/** API-005 删除资产（409 ERR-WG-BOUND-DELETE=被绑定须先解绑） */
export async function deleteWidget(id: string) {
  return request.delete<unknown, null>(`/widgets/${id}`)
}

/** API-006 复制资产（官方模板进入使用唯一通道；返回新 id） */
export async function copyWidget(id: string) {
  return request.post<unknown, { id: string }>(`/widgets/${id}/copy`, {})
}

// ===========================================================================
// 应用绑定四动词（API-007~010；上限 10 由服务端裁决）
// ===========================================================================

/** API-007 绑定列表（含禁用行；候选集合不经本端点，前端以 API-001 求差） */
export async function getAppWidgets(appId: string) {
  return request.get<unknown, AppWidgetBindingView[]>(`/apps/${appId}/widgets`)
}

/** API-008 新增绑定（409 ERR-WG-BINDING-LIMIT=达上限 10） */
export async function bindAppWidget(appId: string, body: { widgetId: string; enabled?: boolean }) {
  return request.post<unknown, { id: string }>(`/apps/${appId}/widgets`, body)
}

/** API-009 修改绑定（启用/禁用开关；不支持换绑 widgetId） */
export async function updateAppWidgetBinding(appId: string, bindingId: string, enabled: boolean) {
  return request.put<unknown, null>(`/apps/${appId}/widgets/${bindingId}`, { enabled })
}

/** Widget 绑定候选（REQ-WG-C02：自建资产 is_builtin=0，管理员另加全员可见） */
export async function getWidgetCandidates(appId: string) {
  return request.get<unknown, WidgetSummary[]>(`/apps/${appId}/widgets/candidates`)
}

/** API-010 删除绑定（解绑；删除资产 API-005 的前置动作） */
export async function deleteAppWidgetBinding(appId: string, bindingId: string) {
  return request.delete<unknown, null>(`/apps/${appId}/widgets/${bindingId}`)
}

// ===========================================================================
// VM 转换点（ui.md「widget 链映射」钉名）
// ===========================================================================

/** 图标为空回退 Element Plus 图标名 Postcard（列表/卡片共用，MOD ## 视觉） */
export const WIDGET_FALLBACK_ICON = 'Postcard'

/** 列表行 VM：WidgetSummary → 行渲染所需投影（icon 空回退、时间直读） */
export function toWidgetListItem(row: WidgetSummary) {
  return {
    id: String(row.id),
    name: row.name,
    description: row.description || '',
    icon: row.icon || WIDGET_FALLBACK_ICON,
    isBuiltin: !!row.isBuiltin,
    updatedAt: row.updatedAt || '',
  }
}

/** 编辑表单草稿 VM：WidgetDetail → 表单三件套 + 基础信息（官方行只读标记透传） */
export function toWidgetDraft(detail: WidgetDetail) {
  return {
    id: String(detail.id),
    name: detail.name,
    description: detail.description || '',
    icon: detail.icon || '',
    isBuiltin: !!detail.isBuiltin,
    template: detail.template || '',
    schemaJson: detail.schemaJson || '',
    defaultJson: detail.defaultJson || '',
  }
}

/** WidgetForm 提交草稿形状（WidgetSaveRequest 的表单态投影） */
export interface WidgetFormDraft {
  id?: string
  name: string
  description: string
  icon: string
  template: string
  schemaJson: string
  defaultJson: string
}

/** 表单草稿 → API-003/004 请求体（空串可选字段裁剪，不占契约字段面） */
export function toWidgetSaveRequest(draft: WidgetFormDraft): WidgetSaveRequest {
  return {
    name: draft.name,
    description: draft.description || undefined,
    icon: draft.icon || undefined,
    template: draft.template,
    schemaJson: draft.schemaJson,
    defaultJson: draft.defaultJson || undefined,
  }
}
