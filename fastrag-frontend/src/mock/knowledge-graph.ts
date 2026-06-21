import type { GraphNode, GraphEdge, GraphStats, EntityType } from '@/types/evaluation'

// ===========================================================================
// 知识图谱数据 —— 全局唯一数据源
//
// 设计目标：消除三处硬编码的不一致（KnowledgeGraphPanel 18 节点 / IndexManagementPanel
// 349 实体 / graph-expansion.ts 的另一套实体）。这里以一个小微 ICT 知识库为样本，
// 所有面板与 composable 都从本文件取数。
// ===========================================================================

/** 实体类型 -> 颜色映射（与原 KnowledgeGraphPanel 保持一致的视觉） */
export const ENTITY_TYPE_COLORS: Record<string, string> = {
  '服务': '#FFC107',
  '客户类型': '#E91E63',
  '方法论步骤': '#FF5722',
  '解决方案': '#00BCD4',
  '编码': '#009688',
  '操作流程': '#795548',
  '功能': '#607D8B',
  '组织': '#F44336',
  '系统': '#4CAF50',
  '网络': '#9C27B0',
  '业务': '#FF9800',
  '扩展实体': '#1E88E5',
}

/** 默认知识库的图谱节点 */
const defaultNodes: GraphNode[] = [
  { id: '1', name: '小微ICT', label: '业务', type: 'entity' },
  { id: '2', name: 'CRM系统', label: '系统', type: 'entity' },
  { id: '3', name: '政企客户', label: '客户类型', type: 'entity' },
  { id: '4', name: '电信网络', label: '网络', type: 'entity' },
  { id: '5', name: '省客支', label: '组织', type: 'entity' },
  { id: '6', name: '校园客户', label: '客户类型', type: 'entity' },
  { id: '7', name: '视频监控', label: '服务', type: 'entity' },
  { id: '8', name: '智能组网', label: '服务', type: 'entity' },
  { id: '9', name: '机房托管', label: '服务', type: 'entity' },
  { id: '10', name: '极速专线', label: '服务', type: 'entity' },
  { id: '11', name: '综合布线', label: '服务', type: 'entity' },
  { id: '12', name: '成本核算', label: '操作流程', type: 'entity' },
  { id: '13', name: '方案选择', label: '操作流程', type: 'entity' },
  { id: '14', name: '补贴管理', label: '解决方案', type: 'entity' },
  { id: '15', name: '设备升级', label: '服务', type: 'entity' },
  { id: '16', name: '标品合同编码', label: '编码', type: 'entity' },
  { id: '17', name: '六促销售', label: '方法论步骤', type: 'entity' },
  { id: '18', name: '阶梯激励', label: '解决方案', type: 'entity' },
]

/** 默认知识库的关系（source/target 用实体名称） */
const defaultEdges: GraphEdge[] = [
  { source: '小微ICT', target: 'CRM系统', label: '使用' },
  { source: '小微ICT', target: '政企客户', label: '服务' },
  { source: 'CRM系统', target: '政企客户', label: '管理' },
  { source: '政企客户', target: '电信网络', label: '依赖' },
  { source: '省客支', target: '小微ICT', label: '运营' },
  { source: '省客支', target: '政企客户', label: '负责' },
  { source: '校园客户', target: '视频监控', label: '需要' },
  { source: '视频监控', target: '电信网络', label: '依赖' },
  { source: '智能组网', target: '电信网络', label: '依赖' },
  { source: '机房托管', target: '电信网络', label: '依赖' },
  { source: '极速专线', target: '电信网络', label: '依赖' },
  { source: '小微ICT', target: '智能组网', label: '提供' },
  { source: '小微ICT', target: '机房托管', label: '提供' },
  { source: '小微ICT', target: '极速专线', label: '提供' },
  { source: '小微ICT', target: '综合布线', label: '提供' },
  { source: '成本核算', target: '小微ICT', label: '支持' },
  { source: '方案选择', target: '成本核算', label: '前置' },
  { source: '补贴管理', target: '小微ICT', label: '关联' },
  { source: '设备升级', target: '电信网络', label: '涉及' },
  { source: '标品合同编码', target: 'CRM系统', label: '关联' },
  { source: '六促销售', target: '综合布线', label: '运用' },
  { source: '阶梯激励', target: '小微ICT', label: '激励' },
]

/** 按 kbId 存放的图谱数据（当前所有知识库共用默认样本） */
const graphStore: Record<string, { nodes: GraphNode[]; edges: GraphEdge[] }> = {
  default: { nodes: defaultNodes, edges: defaultEdges },
}

function getStore(kbId: string) {
  return graphStore[kbId] || graphStore.default
}

/** 获取图谱数据 */
export function getGraphData(kbId: string): { nodes: GraphNode[]; edges: GraphEdge[] } {
  const store = getStore(kbId)
  // 返回浅拷贝，避免外部直接改写
  return {
    nodes: store.nodes.map((n) => ({ ...n })),
    edges: store.edges.map((e) => ({ ...e })),
  }
}

/** 获取图谱统计（实体数、关系数、按类型聚合） */
export function getGraphStats(kbId: string): GraphStats {
  const { nodes, edges } = getStore(kbId)
  const typeMap = new Map<string, number>()
  nodes.forEach((n) => {
    typeMap.set(n.label, (typeMap.get(n.label) || 0) + 1)
  })
  const entityTypes: EntityType[] = Array.from(typeMap.entries())
    .map(([name, count]) => ({
      name,
      count,
      color: ENTITY_TYPE_COLORS[name] || '#1E88E5',
    }))
    .sort((a, b) => b.count - a.count)
  return {
    entityCount: nodes.length,
    relationCount: edges.length,
    entityTypes,
  }
}
