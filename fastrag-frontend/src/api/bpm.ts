// ===========================================================================
// BPM 业务流程管理 — API 客户端(对应后端 /api/bpm/*)
// ===========================================================================

import request from '@/utils/request'
import type {
  FlowDefPageReq, FlowDefRequest, FlowDefVO, FlowVersionVO, VersionDetailVO,
  NodeRequest, NodeVO, EdgeRequest, EdgeVO,
  CanvasSaveRequest, CanvasResponse, CanvasValidateResultVO,
  NodeTypeVO, FlowTemplateVO,
  InstanceListReq, InstanceTriggerRequest, InstanceVO, SubmitInputRequest, InstanceEventVO,
  PermissionRequest, PermissionVO,
  TestCaseRequest, TestCaseVO,
  FlowExportVO, ImportFlowRequest,
  GlobalStatsVO, FlowStatsVO, InstanceTimelineVO,
  PageResult, VersionCreateRequest,
} from '@/types/bpm'

// ===========================================================================
// 流程定义 CRUD
// ===========================================================================

/** 分页查询流程定义 */
export function pageFlowDefs(params: FlowDefPageReq) {
  return request.get<PageResult<FlowDefVO>, PageResult<FlowDefVO>>('bpm/flows', { params })
}

/** 简易列表(用于下拉选择) */
export function listSimpleFlows(params?: { keyword?: string; visibility?: string }) {
  return request.get<FlowDefVO[], FlowDefVO[]>('bpm/flows/list', { params })
}

/** 流程详情 */
export function getFlowDef(id: string) {
  return request.get<FlowDefVO, FlowDefVO>(`bpm/flows/${id}`)
}

/** 创建流程 */
export function createFlowDef(data: FlowDefRequest) {
  return request.post<FlowDefVO, FlowDefVO>('bpm/flows', data)
}

/** 更新流程 */
export function updateFlowDef(id: string, data: FlowDefRequest) {
  return request.put<FlowDefVO, FlowDefVO>(`bpm/flows/${id}`, data)
}

/** 删除流程 */
export function deleteFlowDef(id: string) {
  return request.delete<void, void>(`bpm/flows/${id}`)
}

/** 复制流程 */
export function copyFlowDef(id: string, newName?: string) {
  return request.post<FlowDefVO, FlowDefVO>(`bpm/flows/${id}/copy`, { name: newName })
}

// ===========================================================================
// 版本管理
// ===========================================================================

/** 版本列表 */
export function listVersions(flowDefId: string) {
  return request.get<FlowVersionVO[], FlowVersionVO[]>(`bpm/flows/${flowDefId}/versions`)
}

/** 版本详情(包含 nodes/edges/validation) */
export function getVersionDetail(flowDefId: string, versionNo: number) {
  return request.get<VersionDetailVO, VersionDetailVO>(`bpm/flows/${flowDefId}/versions/${versionNo}`)
}

/** 创建新草稿版本 */
export function createDraftVersion(flowDefId: string, body?: VersionCreateRequest) {
  return request.post<{ flowDefId: string; versionNo: number }, { flowDefId: string; versionNo: number }>(
    `bpm/flows/${flowDefId}/versions`, body || {},
  )
}

/** 发布版本 */
export function publishVersion(flowDefId: string, versionNo: number) {
  return request.post<FlowVersionVO, FlowVersionVO>(`bpm/flows/${flowDefId}/versions/${versionNo}/publish`)
}

/** 回滚到指定版本 */
export function rollbackVersion(flowDefId: string, versionNo: number) {
  return request.post<FlowVersionVO, FlowVersionVO>(`bpm/flows/${flowDefId}/versions/${versionNo}/rollback`)
}

/** 归档版本 */
export function archiveVersion(flowDefId: string, versionNo: number) {
  return request.post<void, void>(`bpm/flows/${flowDefId}/versions/${versionNo}/archive`)
}

// ===========================================================================
// 画布
// ===========================================================================

/** 获取画布全图 */
export function getCanvas(flowDefId: string, versionId: string) {
  return request.get<CanvasResponse, CanvasResponse>(`bpm/flows/${flowDefId}/versions/${versionId}/canvas`)
}

/** 原子保存画布(仅 draft 状态可用) */
export function saveCanvas(flowDefId: string, versionId: string, body: CanvasSaveRequest) {
  return request.put<CanvasValidateResultVO, CanvasValidateResultVO>(
    `bpm/flows/${flowDefId}/versions/${versionId}/canvas`, body,
  )
}

/** 校验画布结构 */
export function validateCanvas(flowDefId: string, versionId: string) {
  return request.get<CanvasValidateResultVO, CanvasValidateResultVO>(
    `bpm/flows/${flowDefId}/versions/${versionId}/validate`,
  )
}

// ===========================================================================
// 节点
// ===========================================================================

/** 节点列表 */
export function listNodes(flowDefId: string, versionId: string) {
  return request.get<NodeVO[], NodeVO[]>(`bpm/flows/${flowDefId}/versions/${versionId}/nodes`)
}

/** 节点详情 */
export function getNode(flowDefId: string, versionId: string, nodeKey: string) {
  return request.get<NodeVO, NodeVO>(`bpm/flows/${flowDefId}/versions/${versionId}/nodes/${nodeKey}`)
}

/** 创建节点 */
export function createNode(flowDefId: string, versionId: string, body: NodeRequest) {
  return request.post<NodeVO, NodeVO>(`bpm/flows/${flowDefId}/versions/${versionId}/nodes`, body)
}

/** 更新节点 */
export function updateNode(flowDefId: string, versionId: string, nodeKey: string, body: NodeRequest) {
  return request.put<NodeVO, NodeVO>(`bpm/flows/${flowDefId}/versions/${versionId}/nodes/${nodeKey}`, body)
}

/** 移动节点位置 */
export function moveNode(flowDefId: string, versionId: string, nodeKey: string, x: number, y: number) {
  return request.put<void, void>(`bpm/flows/${flowDefId}/versions/${versionId}/nodes/${nodeKey}/position`, { x, y })
}

/** 更新节点配置(快捷方式) */
export function updateNodeConfig(flowDefId: string, versionId: string, nodeKey: string, config: unknown) {
  return request.put<void, void>(
    `bpm/flows/${flowDefId}/versions/${versionId}/nodes/${nodeKey}/config`,
    { config: typeof config === 'string' ? config : JSON.stringify(config) },
  )
}

/** 删除节点 */
export function deleteNode(flowDefId: string, versionId: string, nodeKey: string) {
  return request.delete<void, void>(`bpm/flows/${flowDefId}/versions/${versionId}/nodes/${nodeKey}`)
}

// ===========================================================================
// 边
// ===========================================================================

/** 边列表 */
export function listEdges(flowDefId: string, versionId: string) {
  return request.get<EdgeVO[], EdgeVO[]>(`bpm/flows/${flowDefId}/versions/${versionId}/edges`)
}

/** 边详情 */
export function getEdge(flowDefId: string, versionId: string, edgeId: string) {
  return request.get<EdgeVO, EdgeVO>(`bpm/flows/${flowDefId}/versions/${versionId}/edges/${edgeId}`)
}

/** 创建边 */
export function createEdge(flowDefId: string, versionId: string, body: EdgeRequest) {
  return request.post<EdgeVO, EdgeVO>(`bpm/flows/${flowDefId}/versions/${versionId}/edges`, body)
}

/** 更新边 */
export function updateEdge(flowDefId: string, versionId: string, edgeId: string, body: EdgeRequest) {
  return request.put<EdgeVO, EdgeVO>(`bpm/flows/${flowDefId}/versions/${versionId}/edges/${edgeId}`, body)
}

/** 删除边 */
export function deleteEdge(flowDefId: string, versionId: string, edgeId: string) {
  return request.delete<void, void>(`bpm/flows/${flowDefId}/versions/${versionId}/edges/${edgeId}`)
}

// ===========================================================================
// 节点类型元数据
// ===========================================================================

/** 节点类型列表 */
export function listNodeTypes() {
  return request.get<NodeTypeVO[], NodeTypeVO[]>('bpm/node-types')
}

/** 节点类型详情 */
export function getNodeType(type: string) {
  return request.get<NodeTypeVO, NodeTypeVO>(`bpm/node-types/${type}`)
}

// ===========================================================================
// 流程模板
// ===========================================================================

/** 模板列表 */
export function listTemplates(params?: { builtinOnly?: boolean; category?: string }) {
  return request.get<FlowTemplateVO[], FlowTemplateVO[]>('bpm/templates', { params })
}

/** 模板详情 */
export function getTemplate(id: string) {
  return request.get<FlowTemplateVO, FlowTemplateVO>(`bpm/templates/${id}`)
}

/** 应用模板到流程(创建/拷贝) */
export function applyTemplate(id: string, flowDefId?: string) {
  return request.post<FlowDefVO, FlowDefVO>(`bpm/templates/${id}/apply`, { flowDefId: flowDefId || '' })
}

// ===========================================================================
// 权限
// ===========================================================================

/** 权限列表 */
export function listPermissions(flowDefId: string) {
  return request.get<PermissionVO[], PermissionVO[]>(`bpm/flows/${flowDefId}/permissions`)
}

/** 授予权限 */
export function grantPermission(flowDefId: string, body: PermissionRequest) {
  return request.post<void, void>(`bpm/flows/${flowDefId}/permissions`, body)
}

/** 撤销权限 */
export function revokePermission(flowDefId: string, body: PermissionRequest) {
  return request.delete<void, void>(`bpm/flows/${flowDefId}/permissions`, { data: body })
}

/** 检查当前用户权限 */
export function checkPermission(flowDefId: string, permission = 'view') {
  return request.get<{ hasPermission: boolean }, { hasPermission: boolean }>(
    `bpm/flows/${flowDefId}/permissions/check`, { params: { permission } },
  )
}

// ===========================================================================
// 测试用例
// ===========================================================================

/** 测试用例列表 */
export function listTestCases(flowDefId: string) {
  return request.get<TestCaseVO[], TestCaseVO[]>(`bpm/flows/${flowDefId}/test-cases`)
}

/** 创建测试用例 */
export function createTestCase(flowDefId: string, body: TestCaseRequest) {
  return request.post<TestCaseVO, TestCaseVO>(`bpm/flows/${flowDefId}/test-cases`, body)
}

/** 删除测试用例 */
export function deleteTestCase(flowDefId: string, id: string) {
  return request.delete<void, void>(`bpm/flows/${flowDefId}/test-cases/${id}`)
}

/** 运行测试用例 */
export function runTestCase(flowDefId: string, id: string) {
  return request.post<{ instanceId: string; outputs?: Record<string, unknown> }, { instanceId: string; outputs?: Record<string, unknown> }>(
    `bpm/flows/${flowDefId}/test-cases/${id}/run`,
  )
}

// ===========================================================================
// 导入导出
// ===========================================================================

/** 导出流程 */
export function exportFlow(flowDefId: string) {
  return request.get<FlowExportVO, FlowExportVO>(`bpm/flows/${flowDefId}/export`)
}

/** 导入流程 */
export function importFlow(body: ImportFlowRequest) {
  return request.post<{ flowDefId: string }, { flowDefId: string }>('bpm/flows/import', body)
}

// ===========================================================================
// 实例监控
// ===========================================================================

/** 触发流程实例 */
export function triggerInstance(body: InstanceTriggerRequest) {
  return request.post<{ instanceId: string }, { instanceId: string }>('bpm/instances/trigger', body)
}

/** 实例分页列表 */
export function listInstances(params: InstanceListReq) {
  return request.get<PageResult<InstanceVO>, PageResult<InstanceVO>>('bpm/instances', { params })
}

/** 实例详情 */
export function getInstance(id: string) {
  return request.get<InstanceVO, InstanceVO>(`bpm/instances/${id}`)
}

/** 实例事件流 */
export function getInstanceEvents(id: string) {
  return request.get<InstanceEventVO[], InstanceEventVO[]>(`bpm/instances/${id}/events`)
}

/** 暂停实例 */
export function pauseInstance(id: string) {
  return request.post<void, void>(`bpm/instances/${id}/pause`)
}

/** 恢复实例 */
export function resumeInstance(id: string) {
  return request.post<void, void>(`bpm/instances/${id}/resume`)
}

/** 终止实例 */
export function cancelInstance(id: string, reason?: string) {
  return request.post<void, void>(`bpm/instances/${id}/cancel`, { reason: reason || '' })
}

/** 提交用户输入 */
export function submitUserInput(body: SubmitInputRequest) {
  return request.post<void, void>('bpm/instances/input/submit', body)
}

// ===========================================================================
// 统计
// ===========================================================================

/** 全局统计 */
export function globalStats() {
  return request.get<GlobalStatsVO, GlobalStatsVO>('bpm/stats')
}

/** 流程统计 */
export function flowStats(flowDefId: string) {
  return request.get<FlowStatsVO, FlowStatsVO>(`bpm/stats/flows/${flowDefId}`)
}

/** 实例时间线 */
export function instanceTimeline(instanceId: string) {
  return request.get<InstanceTimelineVO, InstanceTimelineVO>(`bpm/stats/instances/${instanceId}/timeline`)
}