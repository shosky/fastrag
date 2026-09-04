// ===========================================================================
// BPM 业务流程管理 — TypeScript 类型定义(对应 fastrag-bpm 后端 DTO)
// ===========================================================================

/** 节点类型(固定 9 种,枚举见后端 BpmNodeTypeExecutorRegistry) */
export type BpmNodeType =
  | 'start'
  | 'end'
  | 'user_input'
  | 'llm'
  | 'kb_retrieval'
  | 'intent'
  | 'http'
  | 'condition'
  | 'subflow'

/** 节点类型中文标签 */
export const BPM_NODE_TYPE_LABELS: Record<BpmNodeType, string> = {
  start: '开始',
  end: '结束',
  user_input: '用户输入',
  llm: '大模型',
  kb_retrieval: '知识库检索',
  intent: '意图识别',
  http: 'HTTP 请求',
  condition: '条件分支',
  subflow: '子流程',
}

/** 节点类型默认颜色(画布展示用) */
export const BPM_NODE_TYPE_COLORS: Record<BpmNodeType, string> = {
  start: '#67C23A',
  end: '#F56C6C',
  user_input: '#909399',
  llm: '#409EFF',
  kb_retrieval: '#E6A23C',
  intent: '#9B59B6',
  http: '#1ABC9C',
  condition: '#FF9800',
  subflow: '#00B4D8',
}

/** 节点类型默认图标 */
export const BPM_NODE_TYPE_ICONS: Record<BpmNodeType, string> = {
  start: '▶',
  end: '⏹',
  user_input: '⌨',
  llm: '🤖',
  kb_retrieval: '📚',
  intent: '🎯',
  http: '🌐',
  condition: '🔀',
  subflow: '🔗',
}

/** 版本状态 */
export type VersionStatus = 'draft' | 'published' | 'archived' | 'disabled'

/** 版本状态标签 */
export const VERSION_STATUS_LABELS: Record<VersionStatus, string> = {
  draft: '草稿',
  published: '已发布',
  archived: '已归档',
  disabled: '已停用',
}

/** 版本状态对应 Element Plus tag type */
export const VERSION_STATUS_TAG_TYPES: Record<VersionStatus, 'success' | 'info' | 'warning' | 'danger'> = {
  draft: 'info',
  published: 'success',
  archived: 'warning',
  disabled: 'danger',
}

/** 实例状态 */
export type InstanceStatus =
  | 'pending'
  | 'running'
  | 'paused'
  | 'completed'
  | 'cancelled'
  | 'failed'

export const INSTANCE_STATUS_LABELS: Record<InstanceStatus, string> = {
  pending: '待执行',
  running: '运行中',
  paused: '已暂停',
  completed: '已完成',
  cancelled: '已取消',
  failed: '失败',
}

export const INSTANCE_STATUS_TAG_TYPES: Record<InstanceStatus, 'success' | 'info' | 'warning' | 'danger' | 'primary'> = {
  pending: 'info',
  running: 'primary',
  paused: 'warning',
  completed: 'success',
  cancelled: 'info',
  failed: 'danger',
}

/** 边种类 */
export type EdgeKind = 'default_edge' | 'condition' | 'parallel' | 'exception'

export const EDGE_KIND_LABELS: Record<EdgeKind, string> = {
  default_edge: '默认',
  condition: '条件',
  parallel: '并行',
  exception: '异常',
}

/** 可见性 */
export type FlowVisibility = 'private_flow' | 'team' | 'public_flow'

export const FLOW_VISIBILITY_LABELS: Record<FlowVisibility, string> = {
  private_flow: '私有',
  team: '团队',
  public_flow: '公开',
}

/** 触发方式 */
export type TriggerType = 'manual' | 'api' | 'scheduled' | 'event'

export const TRIGGER_TYPE_LABELS: Record<TriggerType, string> = {
  manual: '手动触发',
  api: 'API 触发',
  scheduled: '定时触发',
  event: '事件触发',
}

/** 事件类型(实例时间线) */
export type EventType =
  | 'FLOW_CREATED'
  | 'FLOW_STARTED'
  | 'FLOW_COMPLETED'
  | 'FLOW_FAILED'
  | 'FLOW_PAUSED'
  | 'FLOW_RESUMED'
  | 'FLOW_CANCELLED'
  | 'NODE_ENTERED'
  | 'NODE_COMPLETED'
  | 'NODE_FAILED'
  | 'INPUT_REQUESTED'
  | 'INPUT_SUBMITTED'

// ===========================================================================
// 视图对象 VO
// ===========================================================================

/** 流程版本 VO */
export interface FlowVersionVO {
  id: string
  flowDefId: string
  versionNo: number
  status: VersionStatus
  remark?: string
  publisherId?: string
  publishedAt?: string
  createdAt?: string
}

/** 流程定义 VO */
export interface FlowDefVO {
  id: string
  name: string
  description?: string
  category?: string
  ownerId?: string
  visibility: FlowVisibility
  currentVersionId?: string
  currentVersionNo?: number
  currentVersionStatus?: VersionStatus
  timeoutMs?: number
  nodeCount?: number
  edgeCount?: number
  triggerType?: TriggerType
  logSnapshotEnabled?: boolean
  createdAt?: string
  updatedAt?: string
  recentVersions?: FlowVersionVO[]
}

/** 节点 VO */
export interface NodeVO {
  id?: string
  versionId?: string
  nodeKey: string
  nodeType: BpmNodeType
  name?: string
  positionX: number
  positionY: number
  /** JSON 字符串(后端存的是字符串,前端用时再 JSON.parse) */
  config?: string
  timeoutMs?: number
  retryCount?: number
  retryIntervalMs?: number
  onFailure?: 'fail' | 'ignore' | 'branch'
  failureBranchNodeKey?: string
  enabled?: boolean
  createdAt?: string
}

/** 边 VO */
export interface EdgeVO {
  id?: string
  versionId?: string
  sourceNodeKey: string
  targetNodeKey: string
  edgeKind: EdgeKind
  conditionExpr?: string
  conditionParams?: string
  label?: string
  priority?: number
  createdAt?: string
}

/** 画布校验结果 */
export interface CanvasValidateResultVO {
  valid: boolean
  errors: string[]
  warnings: string[]
  nodeCount: number
  edgeCount: number
}

/** 版本详情 */
export interface VersionDetailVO {
  version: FlowVersionVO
  nodes: NodeVO[]
  edges: EdgeVO[]
  validation?: CanvasValidateResultVO
}

/** 画布/版本详情组合响应(对应 GET /canvas) */
export interface CanvasResponse {
  detail: VersionDetailVO
  validation: CanvasValidateResultVO
}

/** 节点类型元数据 VO(来自 bpm_node_type_meta) */
export interface NodeTypeVO {
  type: BpmNodeType
  label: string
  icon?: string
  color?: string
  category?: string
  description?: string
  /** JSON 字符串:节点配置 schema [{key,label,type,defaultValue,options,validation}] */
  configSchema?: string
  /** JSON 字符串:节点默认配置 */
  defaultConfig?: string
  enabled?: boolean
  sortOrder?: number
}

/** 节点配置字段(由 configSchema 解析后) */
export interface NodeConfigField {
  key: string
  label: string
  type: 'input' | 'number' | 'select' | 'textarea' | 'switch' | 'json'
  defaultValue?: unknown
  options?: Array<{ label: string; value: string | number }>
  validation?: { required?: boolean; min?: number; max?: number; pattern?: string }
  description?: string
}

/** 流程模板 VO */
export interface FlowTemplateVO {
  id: string
  name: string
  category?: string
  description?: string
  canvasData?: string
  thumbnailUrl?: string
  createdBy?: string
  isBuiltin?: boolean
  createdAt?: string
}

/** 实例 VO */
export interface InstanceVO {
  id: string
  traceId?: string
  flowDefId: string
  flowVersionId?: string
  flowVersionNo?: number
  status: InstanceStatus
  subStatus?: string
  startUserId?: string
  triggerType?: TriggerType
  failureReason?: string
  inputParams?: string
  outputParams?: string
  variables?: string
  currentNodeKeys?: string
  pendingInputToken?: string
  pendingInputForm?: string
  inputTimeoutMs?: number
  inputDeadline?: string
  timeoutAt?: string
  startedAt?: string
  finishedAt?: string
  createdAt?: string
  durationMs?: number
  inputs?: Record<string, unknown>
  outputs?: Record<string, unknown>
}

/** 实例事件流 */
export interface InstanceEventVO {
  id: string
  instanceId: string
  eventType: EventType
  nodeKey?: string
  message?: string
  payload?: string
  occurredAt: string
  actorId?: string
}

/** 用户输入表单定义 */
export interface UserInputFormField {
  key: string
  label: string
  type: 'text' | 'number' | 'date' | 'select' | 'textarea'
  required?: boolean
  options?: Array<{ label: string; value: string }>
}

/** 通用分页响应 */
export interface PageResult<T> {
  total: number
  page: number
  size: number
  records: T[]
}

// ===========================================================================
// 请求 DTO
// ===========================================================================

/** 流程定义分页查询 */
export interface FlowDefPageReq {
  page?: number
  size?: number
  keyword?: string
  category?: string
  visibility?: FlowVisibility
  mineOnly?: boolean
}

/** 流程定义创建/更新 */
export interface FlowDefRequest {
  name: string
  description?: string
  category?: string
  visibility?: FlowVisibility
  timeoutMs?: number
  triggerType?: TriggerType
  logSnapshotEnabled?: boolean
}

/** 节点请求 */
export interface NodeRequest {
  nodeKey: string
  nodeType: BpmNodeType
  name?: string
  positionX?: number
  positionY?: number
  /** JSON 字符串 */
  config?: string
  timeoutMs?: number
  retryCount?: number
  retryIntervalMs?: number
  onFailure?: 'fail' | 'ignore' | 'branch'
  failureBranchNodeKey?: string
  enabled?: boolean
}

/** 边请求 */
export interface EdgeRequest {
  sourceNodeKey: string
  targetNodeKey: string
  edgeKind?: EdgeKind
  conditionExpr?: string
  conditionParams?: string
  label?: string
  priority?: number
}

/** 画布原子保存请求 */
export interface CanvasSaveRequest {
  canvasData?: string
  nodes: NodeRequest[]
  edges: EdgeRequest[]
}

/** 版本创建请求 */
export interface VersionCreateRequest {
  remark?: string
  fromPublished?: boolean
}

/** 实例分页查询 */
export interface InstanceListReq {
  page?: number
  size?: number
  flowDefId?: string
  status?: InstanceStatus
  keyword?: string
  startUserId?: string
}

/** 实例触发请求 */
export interface InstanceTriggerRequest {
  flowDefId: string
  versionNo?: number
  triggerType?: TriggerType
  inputParams?: Record<string, unknown>
  startUserId?: string
}

/** 用户输入提交 */
export interface SubmitInputRequest {
  token: string
  inputs: Record<string, unknown>
}

/** 测试用例请求 */
export interface TestCaseRequest {
  name: string
  inputs?: Record<string, unknown>
  expectedOutput?: string
}

/** 测试用例 VO */
export interface TestCaseVO {
  id: string
  flowDefId: string
  name: string
  inputs?: string
  expectedOutput?: string
  lastRunStatus?: 'pass' | 'fail' | 'pending'
  lastRunAt?: string
  createdBy?: string
  createdAt?: string
}

/** 权限请求 */
export interface PermissionRequest {
  subjectType: 'user' | 'role'
  subjectId: string
  /** view/edit/execute/publish/delete */
  permission: 'view' | 'edit' | 'execute' | 'publish' | 'delete'
}

/** 权限项 */
export interface PermissionVO {
  id?: string
  flowDefId: string
  subjectType: 'user' | 'role'
  subjectId: string
  subjectName?: string
  permission: 'view' | 'edit' | 'execute' | 'publish' | 'delete'
  grantedBy?: string
  grantedAt?: string
}

/** 流程导出 JSON 文档 */
export interface FlowExportVO {
  exportVersion: number
  def: FlowDefVO
  currentVersion: FlowVersionVO
  nodes: NodeVO[]
  edges: EdgeVO[]
  exportedAt: string
  exportedBy: string
  extras?: Record<string, unknown>
}

/** 流程导入请求 */
export interface ImportFlowRequest {
  payload: string
  newName?: string
  overwrite?: boolean
}

/** 统计 — 全局 */
export interface GlobalStatsVO {
  totalFlows: number
  totalInstances: number
  runningInstances: number
  completedInstances: number
  failedInstances: number
  todayInstances: number
  averageDurationMs: number
}

/** 统计 — 单流程 */
export interface FlowStatsVO {
  flowDefId: string
  totalInstances: number
  runningInstances: number
  completedInstances: number
  failedInstances: number
  averageDurationMs: number
  /** 按天实例数(最近 N 天) */
  trend?: Array<{ date: string; count: number }>
}

/** 统计 — 实例时间线 */
export interface InstanceTimelineVO {
  instanceId: string
  events: InstanceEventVO[]
  /** 节点执行统计 */
  nodeStats?: Array<{ nodeKey: string; count: number; avgDurationMs: number }>
}