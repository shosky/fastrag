import type {
  WorkflowDefinition,
  WorkflowNode,
  WorkflowEdge,
  WorkflowStatus,
  NodeType,
} from '@/types/workflow'
import { DEFAULT_NODE_PARAMS } from '@/types/workflow'
import { checkApiPermission } from '@/mock/interceptor'

// ===========================================================================
// 业务流 mock 数据层
// ===========================================================================

const workflowStore: WorkflowDefinition[] = []
let wfSeq = 100
let nodeSeq = 1000
let edgeSeq = 1000

function now(): string {
  return new Date().toLocaleString('zh-CN')
}

function seedWorkflows(): WorkflowDefinition[] {
  return [
    {
      id: 'wf_1',
      name: '客服问答流程',
      description: '根据用户意图走不同检索和回答路径',
      status: 'published',
      nodes: [
        { id: 'n_1', type: 'start', label: '开始', x: 100, y: 200, params: DEFAULT_NODE_PARAMS.start, inputs: [], outputs: [{ id: 'n_1_out', label: '输出', type: 'output' }] },
        { id: 'n_2', type: 'intent', label: '意图识别', x: 300, y: 200, params: { ...DEFAULT_NODE_PARAMS.intent, intents: ['产品咨询', '价格查询', '售后服务', '其他'] }, inputs: [{ id: 'n_2_in', label: '输入', type: 'input' }], outputs: [{ id: 'n_2_out_1', label: '产品咨询', type: 'output' }, { id: 'n_2_out_2', label: '价格查询', type: 'output' }, { id: 'n_2_out_3', label: '其他', type: 'output' }] },
        { id: 'n_3', type: 'knowledge_retrieval', label: '产品知识检索', x: 520, y: 100, params: { ...DEFAULT_NODE_PARAMS.knowledge_retrieval, kbId: '1', topK: 5 }, inputs: [{ id: 'n_3_in', label: '输入', type: 'input' }], outputs: [{ id: 'n_3_out', label: '结果', type: 'output' }] },
        { id: 'n_4', type: 'knowledge_retrieval', label: '价格知识检索', x: 520, y: 300, params: { ...DEFAULT_NODE_PARAMS.knowledge_retrieval, kbId: '2', topK: 3 }, inputs: [{ id: 'n_4_in', label: '输入', type: 'input' }], outputs: [{ id: 'n_4_out', label: '结果', type: 'output' }] },
        { id: 'n_5', type: 'llm', label: '智能回答', x: 740, y: 200, params: { ...DEFAULT_NODE_PARAMS.llm, systemPrompt: '你是一个专业的客服助手。' }, inputs: [{ id: 'n_5_in', label: '输入', type: 'input' }], outputs: [{ id: 'n_5_out', label: '回答', type: 'output' }] },
        { id: 'n_6', type: 'end', label: '结束', x: 940, y: 200, params: DEFAULT_NODE_PARAMS.end, inputs: [{ id: 'n_6_in', label: '输入', type: 'input' }], outputs: [] },
      ],
      edges: [
        { id: 'e_1', sourceNodeId: 'n_1', sourcePortId: 'n_1_out', targetNodeId: 'n_2', targetPortId: 'n_2_in' },
        { id: 'e_2', sourceNodeId: 'n_2', sourcePortId: 'n_2_out_1', targetNodeId: 'n_3', targetPortId: 'n_3_in' },
        { id: 'e_3', sourceNodeId: 'n_2', sourcePortId: 'n_2_out_2', targetNodeId: 'n_4', targetPortId: 'n_4_in' },
        { id: 'e_4', sourceNodeId: 'n_2', sourcePortId: 'n_2_out_3', targetNodeId: 'n_5', targetPortId: 'n_5_in', condition: '其他' },
        { id: 'e_5', sourceNodeId: 'n_3', sourcePortId: 'n_3_out', targetNodeId: 'n_5', targetPortId: 'n_5_in' },
        { id: 'e_6', sourceNodeId: 'n_4', sourcePortId: 'n_4_out', targetNodeId: 'n_5', targetPortId: 'n_5_in' },
        { id: 'e_7', sourceNodeId: 'n_5', sourcePortId: 'n_5_out', targetNodeId: 'n_6', targetPortId: 'n_6_in' },
      ],
      createdAt: '2026-06-10 09:00',
      updatedAt: '2026-06-15 14:30',
      createdBy: '管理员',
    },
  ]
}

function getStore(): WorkflowDefinition[] {
  if (workflowStore.length === 0) {
    workflowStore.push(...seedWorkflows())
  }
  return workflowStore
}

// --- CRUD ---
export function getWorkflows(): WorkflowDefinition[] {
  return getStore().map((w) => ({ ...w, nodes: [...w.nodes], edges: [...w.edges] }))
}

export function getWorkflow(id: string): WorkflowDefinition | null {
  const wf = getStore().find((w) => w.id === id)
  return wf ? { ...wf, nodes: [...wf.nodes], edges: [...wf.edges] } : null
}

export function createWorkflow(form: { name: string; description: string }): WorkflowDefinition {
  checkApiPermission('workflow:create')
  const startNode: WorkflowNode = {
    id: `n_${++nodeSeq}`,
    type: 'start',
    label: '开始',
    x: 100,
    y: 200,
    params: DEFAULT_NODE_PARAMS.start,
    inputs: [],
    outputs: [{ id: `n_${nodeSeq}_out`, label: '输出', type: 'output' }],
  }
  const endNode: WorkflowNode = {
    id: `n_${++nodeSeq}`,
    type: 'end',
    label: '结束',
    x: 600,
    y: 200,
    params: DEFAULT_NODE_PARAMS.end,
    inputs: [{ id: `n_${nodeSeq}_in`, label: '输入', type: 'input' }],
    outputs: [],
  }
  const wf: WorkflowDefinition = {
    id: `wf_${++wfSeq}`,
    name: form.name,
    description: form.description,
    status: 'draft',
    nodes: [startNode, endNode],
    edges: [],
    createdAt: now(),
    updatedAt: now(),
    createdBy: '当前用户',
  }
  getStore().push(wf)
  return { ...wf }
}

export function updateWorkflow(id: string, patch: Partial<WorkflowDefinition>): WorkflowDefinition | null {
  const store = getStore()
  const idx = store.findIndex((w) => w.id === id)
  if (idx === -1) return null
  store[idx] = { ...store[idx], ...patch, updatedAt: now() }
  return { ...store[idx] }
}

export function deleteWorkflow(id: string): boolean {
  checkApiPermission('workflow:delete')
  const store = getStore()
  const idx = store.findIndex((w) => w.id === id)
  if (idx === -1) return false
  store.splice(idx, 1)
  return true
}

export function publishWorkflow(id: string): boolean {
  checkApiPermission('workflow:publish')
  const wf = getStore().find((w) => w.id === id)
  if (!wf) return false
  wf.status = 'published'
  wf.updatedAt = now()
  return true
}

// --- 节点操作 ---
export function addNode(wfId: string, type: NodeType, x: number, y: number): WorkflowNode | null {
  const wf = getStore().find((w) => w.id === wfId)
  if (!wf) return null
  const nodeId = `n_${++nodeSeq}`
  const node: WorkflowNode = {
    id: nodeId,
    type,
    label: `${type === 'llm' ? '大模型' : type === 'knowledge_retrieval' ? '知识检索' : type === 'intent' ? '意图识别' : type === 'selector' ? '选择器' : type === 'plugin' ? '插件' : type}节点`,
    x,
    y,
    params: { ...DEFAULT_NODE_PARAMS[type] },
    inputs: [{ id: `${nodeId}_in`, label: '输入', type: 'input' }],
    outputs: type === 'end' ? [] : [{ id: `${nodeId}_out`, label: '输出', type: 'output' }],
  }
  // 意图识别和选择器有多个输出
  if (type === 'intent') {
    node.outputs = [
      { id: `${nodeId}_out_1`, label: '意图A', type: 'output' },
      { id: `${nodeId}_out_2`, label: '意图B', type: 'output' },
      { id: `${nodeId}_out_default`, label: '默认', type: 'output' },
    ]
  } else if (type === 'selector') {
    node.outputs = [
      { id: `${nodeId}_out_true`, label: '是', type: 'output' },
      { id: `${nodeId}_out_false`, label: '否', type: 'output' },
    ]
  }
  wf.nodes.push(node)
  wf.updatedAt = now()
  return { ...node }
}

export function updateNodePosition(wfId: string, nodeId: string, x: number, y: number): void {
  const wf = getStore().find((w) => w.id === wfId)
  if (!wf) return
  const node = wf.nodes.find((n) => n.id === nodeId)
  if (node) {
    node.x = x
    node.y = y
  }
}

export function deleteNode(wfId: string, nodeId: string): void {
  const wf = getStore().find((w) => w.id === wfId)
  if (!wf) return
  wf.nodes = wf.nodes.filter((n) => n.id !== nodeId)
  wf.edges = wf.edges.filter((e) => e.sourceNodeId !== nodeId && e.targetNodeId !== nodeId)
  wf.updatedAt = now()
}

export function updateNodeParams(wfId: string, nodeId: string, params: Record<string, any>): void {
  const wf = getStore().find((w) => w.id === wfId)
  if (!wf) return
  const node = wf.nodes.find((n) => n.id === nodeId)
  if (node) {
    node.params = { ...node.params, ...params }
    wf.updatedAt = now()
  }
}

// --- 边操作 ---
export function addEdge(wfId: string, edge: Omit<WorkflowEdge, 'id'>): WorkflowEdge | null {
  const wf = getStore().find((w) => w.id === wfId)
  if (!wf) return null
  // 防止重复连线
  const exists = wf.edges.some(
    (e) => e.sourceNodeId === edge.sourceNodeId && e.sourcePortId === edge.sourcePortId
      && e.targetNodeId === edge.targetNodeId && e.targetPortId === edge.targetPortId,
  )
  if (exists) return null
  // 防止自环
  if (edge.sourceNodeId === edge.targetNodeId) return null
  const newEdge: WorkflowEdge = { ...edge, id: `e_${++edgeSeq}` }
  wf.edges.push(newEdge)
  wf.updatedAt = now()
  return { ...newEdge }
}

export function deleteEdge(wfId: string, edgeId: string): void {
  const wf = getStore().find((w) => w.id === wfId)
  if (!wf) return
  wf.edges = wf.edges.filter((e) => e.id !== edgeId)
  wf.updatedAt = now()
}
