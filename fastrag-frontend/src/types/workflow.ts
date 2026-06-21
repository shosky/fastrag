// ===========================================================================
// 业务流（Workflow）类型定义
// ===========================================================================

/** 节点类型 */
export type NodeType = 'start' | 'end' | 'llm' | 'knowledge_retrieval' | 'intent' | 'selector' | 'plugin'

/** 节点类型中文标签 */
export const NODE_TYPE_LABELS: Record<NodeType, string> = {
  start: '开始节点',
  end: '结束节点',
  llm: '大模型节点',
  knowledge_retrieval: '知识库检索',
  intent: '意图识别',
  selector: '选择器',
  plugin: '插件节点',
}

/** 节点类型颜色 */
export const NODE_TYPE_COLORS: Record<NodeType, string> = {
  start: '#43A047',
  end: '#E53935',
  llm: '#1E88E5',
  knowledge_retrieval: '#FB8C00',
  intent: '#8E24AA',
  selector: '#00ACC1',
  plugin: '#6D4C41',
}

/** 节点端口 */
export interface NodePort {
  id: string
  label: string
  /** 端口方向：输入/输出 */
  type: 'input' | 'output'
}

/** 工作流节点 */
export interface WorkflowNode {
  id: string
  type: NodeType
  label: string
  /** 画布坐标 */
  x: number
  y: number
  /** 节点参数（不同节点类型结构不同） */
  params: Record<string, any>
  /** 输入端口 */
  inputs: NodePort[]
  /** 输出端口 */
  outputs: NodePort[]
}

/** 工作流边（连线） */
export interface WorkflowEdge {
  id: string
  /** 源节点 ID */
  sourceNodeId: string
  /** 源端口 ID */
  sourcePortId: string
  /** 目标节点 ID */
  targetNodeId: string
  /** 目标端口 ID */
  targetPortId: string
  /** 条件表达式（选择器节点用） */
  condition?: string
}

/** 工作流状态 */
export type WorkflowStatus = 'draft' | 'published' | 'running' | 'stopped'

export const WORKFLOW_STATUS_LABELS: Record<WorkflowStatus, string> = {
  draft: '草稿',
  published: '已发布',
  running: '运行中',
  stopped: '已停止',
}

export const WORKFLOW_STATUS_COLORS: Record<WorkflowStatus, 'info' | 'success' | 'primary' | 'danger'> = {
  draft: 'info',
  published: 'primary',
  running: 'success',
  stopped: 'danger',
}

/** 工作流定义 */
export interface WorkflowDefinition {
  id: string
  name: string
  description: string
  status: WorkflowStatus
  nodes: WorkflowNode[]
  edges: WorkflowEdge[]
  createdAt: string
  updatedAt: string
  createdBy: string
}

/** 默认节点参数模板 */
export const DEFAULT_NODE_PARAMS: Record<NodeType, Record<string, any>> = {
  start: { inputVars: ['query'] },
  end: { outputFormat: 'text' },
  llm: { model: 'qwen3-32b', systemPrompt: '', userPrompt: '', temperature: 0.7, maxTokens: 2048 },
  knowledge_retrieval: { kbId: '', topK: 5, mode: 'hybrid', similarityThreshold: 0.0 },
  intent: { model: 'qwen3-32b', intents: ['default'] },
  selector: { conditions: [] },
  plugin: { pluginId: '', toolName: '' },
}
