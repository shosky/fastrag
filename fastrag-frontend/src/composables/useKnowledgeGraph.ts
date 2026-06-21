import { ref, computed } from 'vue'
import type {
  GraphNode,
  GraphEdge,
  GraphViewNode,
  GraphStats,
  EntityType,
} from '@/types/evaluation'
import {
  fetchGraphData,
  fetchGraphStats,
  fetchChunkCount,
} from '@/api'
import { ENTITY_TYPE_COLORS } from '@/mock/knowledge-graph'

// ===========================================================================
// 确定性布局：基于节点在数组中的索引做圆形分布。
// 不用 Math.random()，保证同一份数据每次渲染位置一致。
// ===========================================================================
function layoutCircular(nodes: GraphNode[]): GraphViewNode[] {
  const cx = 450
  const cy = 320
  const radius = Math.max(160, Math.min(280, nodes.length * 14))
  return nodes.map((node, i) => {
    const angle = (i / nodes.length) * Math.PI * 2 - Math.PI / 2
    // 节点大小：按关联边数粗略决定（中心节点更大），这里用简单规则
    const size = 16 + (i === 0 ? 12 : Math.max(0, 8 - i))
    const color = ENTITY_TYPE_COLORS[node.label] || '#1E88E5'
    return {
      ...node,
      x: Number((cx + radius * Math.cos(angle)).toFixed(1)),
      y: Number((cy + radius * Math.sin(angle)).toFixed(1)),
      color,
      size,
    }
  })
}

export function useKnowledgeGraph(kbId: string = 'default') {
  const nodes = ref<GraphNode[]>([])
  const edges = ref<GraphEdge[]>([])
  const stats = ref<GraphStats>({ entityCount: 0, relationCount: 0, entityTypes: [] })
  const chunkCount = ref(0)
  const loading = ref(false)
  const selectedNode = ref<GraphNode | null>(null)
  /** 用户手动拖动后的坐标覆盖（id -> {x,y}），保证拖动后位置不被布局重算覆盖 */
  const positionOverrides = ref<Record<string, { x: number; y: number }>>({})

  /** 视图节点：布局结果 + 手动覆盖 */
  const viewNodes = computed<GraphViewNode[]>(() => {
    const laid = layoutCircular(nodes.value)
    return laid.map((n) => {
      const ov = positionOverrides.value[n.id]
      return ov ? { ...n, x: ov.x, y: ov.y } : n
    })
  })

  const entityCount = computed(() => stats.value.entityCount)
  const relationCount = computed(() => stats.value.relationCount)
  const entityTypes = computed<EntityType[]>(() => stats.value.entityTypes)

  async function load() {
    loading.value = true
    try {
      const [data, s, cc] = await Promise.all([
        fetchGraphData(kbId),
        fetchGraphStats(kbId),
        fetchChunkCount(kbId),
      ])
      nodes.value = data.nodes
      edges.value = data.edges
      stats.value = s
      chunkCount.value = cc
      positionOverrides.value = {}
      selectedNode.value = null
    } finally {
      loading.value = false
    }
  }

  function selectNode(node: GraphNode | null) {
    selectedNode.value = node
  }

  function clearSelection() {
    selectedNode.value = null
  }

  /** 记录手动拖动位置 */
  function updateNodePosition(id: string, x: number, y: number) {
    positionOverrides.value = { ...positionOverrides.value, [id]: { x, y } }
  }

  /** 搜索节点（按名称/标签） */
  function searchNodes(query: string): GraphNode[] {
    if (!query.trim()) return []
    return nodes.value.filter(
      (n) => n.name.includes(query) || n.label.includes(query),
    )
  }

  return {
    // 数据
    nodes,
    edges,
    viewNodes,
    stats,
    chunkCount,
    loading,
    selectedNode,
    // 计算属性
    entityCount,
    relationCount,
    entityTypes,
    // 方法
    load,
    selectNode,
    clearSelection,
    updateNodePosition,
    searchNodes,
  }
}
