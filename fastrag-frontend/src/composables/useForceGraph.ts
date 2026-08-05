import { ref, computed, onMounted, onBeforeUnmount, type Ref } from 'vue'
import G6 from '@antv/g6'
import type { GraphNode, GraphEdge, GraphStats, EntityType } from '@/types/evaluation'
import { fetchGraphData, fetchGraphStats, searchGraphNodes } from '@/api'
import { ENTITY_TYPE_COLORS } from '@/mock/knowledge-graph'

export function useForceGraph(
  containerRef: Ref<HTMLElement | undefined>,
  kbId: Ref<string>,
  onNodeClick?: (node: GraphNode) => void,
  onEdgeClick?: (edge: GraphEdge) => void,
) {
  const nodes = ref<GraphNode[]>([])
  const edges = ref<GraphEdge[]>([])
  const stats = ref<GraphStats>({ entityCount: 0, relationCount: 0, entityTypes: [] })
  const loading = ref(false)
  const selectedNode = ref<GraphNode | null>(null)
  const selectedEdge = ref<GraphEdge | null>(null)

  let graph: G6.Graph | null = null

  // 搜索是否激活（用于区分"全量"和"搜索结果"显示）
  const isSearchActive = ref(false)

  // 总数（来自 stats API）
  const entityCount = computed(() => stats.value.entityCount)
  const relationCount = computed(() => stats.value.relationCount)
  // 当前可视数（受搜索过滤影响）
  const visibleEntityCount = computed(() => nodes.value.length)
  const visibleRelationCount = computed(() => edges.value.length)
  const entityTypes = computed<EntityType[]>(() => stats.value.entityTypes)

  /** 初始化 G6 图实例 */
  function initGraph() {
    if (!containerRef.value || graph) return

    const container = containerRef.value
    const width = container.offsetWidth || 900
    const height = container.offsetHeight || 600

    graph = new G6.Graph({
      container: container as HTMLElement,
      width,
      height,
      fitView: true,
      fitViewPadding: 40,
      animate: true,
      modes: {
        default: ['drag-canvas', 'zoom-canvas', 'drag-node'],
      },
      layout: {
        type: 'force',
        preventOverlap: true,
        nodeSize: 40,
        nodeSpacing: 20,
        linkDistance: 150,
        nodeStrength: -300,
        edgeStrength: 0.1,
        collideStrength: 0.8,
        alphaDecay: 0.028,
        alphaMin: 0.01,
        forceSimulation: null,
      },
      defaultNode: {
        size: 24,
        style: {
          fill: '#1E88E5',
          stroke: '#fff',
          lineWidth: 2,
        },
        labelCfg: {
          style: {
            fill: '#333',
            fontSize: 12,
          },
          position: 'bottom',
          offset: 8,
        },
      },
      defaultEdge: {
        type: 'line',
        style: {
          stroke: '#ccc',
          lineWidth: 1.5,
          endArrow: {
            path: G6.Arrow.triangle(6, 8, 8),
            fill: '#ccc',
          },
        },
        labelCfg: {
          refY: 5,
          style: {
            fill: '#999',
            fontSize: 10,
            background: {
              fill: '#fff',
              padding: [2, 4, 2, 4],
              radius: 2,
            },
          },
        },
      },
    })

    // 节点点击事件
    graph.on('node:click', (evt: any) => {
      const model = evt.item?.getModel()
      if (model) {
        const nodeData = nodes.value.find((n) => n.id === model.id)
        selectedNode.value = nodeData || null
        selectedEdge.value = null
        if (nodeData && onNodeClick) onNodeClick(nodeData)
      }
    })

    // 关系点击事件
    graph.on('edge:click', (evt: any) => {
      const model = evt.item?.getModel()
      if (model) {
        const edgeData = edges.value.find((e) => (e.id || `edge_${edges.value.indexOf(e)}`) === model.id)
          || edges.value.find((e) => e.source === model.source && e.target === model.target)
        selectedEdge.value = edgeData || null
        selectedNode.value = null
        if (edgeData && onEdgeClick) onEdgeClick(edgeData)
      }
    })

    // 画布点击取消选中
    graph.on('canvas:click', () => {
      selectedNode.value = null
      selectedEdge.value = null
    })

    // 窗口尺寸变化时 resize
    const resizeObserver = new ResizeObserver(() => {
      if (graph && containerRef.value) {
        const w = containerRef.value.offsetWidth
        const h = containerRef.value.offsetHeight
        graph.changeSize(w, h)
        graph.fitView()
      }
    })
    resizeObserver.observe(container)
  }

  /** 将后端数据转换为 G6 格式 */
  function transformData(rawNodes: GraphNode[], rawEdges: GraphEdge[]) {
    const nodeMap = new Map<string, GraphNode>()
    rawNodes.forEach((n) => {
      // 后端返回的 entity_type 可能是 snake_case
      const label = n.label || n.entity_type || 'Entity'
      nodeMap.set(n.id, { ...n, label })
    })

    const g6Nodes = rawNodes.map((n) => {
      const label = n.label || n.entity_type || 'Entity'
      const color = ENTITY_TYPE_COLORS[label] || '#1E88E5'
      return {
        id: n.id,
        label: n.name,
        type: 'circle',
        style: {
          fill: color,
          stroke: '#fff',
          lineWidth: 2,
        },
        size: 20 + Math.min(nodeMap.size * 0.3, 16),
        labelCfg: {
          style: { fill: '#333', fontSize: 12 },
          position: 'bottom' as const,
          offset: 8,
        },
        // 自定义数据，用于 tooltip 和选中
        originalData: n,
      }
    })

    // source/target 映射：后端用 name，G6 用 id
    const nameToId = new Map<string, string>()
    rawNodes.forEach((n) => {
      nameToId.set(n.name, n.id)
    })

    const g6Edges = rawEdges.map((e, i) => {
      const sourceId = nameToId.get(e.source) || e.source
      const targetId = nameToId.get(e.target) || e.target
      return {
        id: e.id || `edge_${i}`,
        source: sourceId,
        target: targetId,
        label: e.label,
      }
    })

    return { nodes: g6Nodes, edges: g6Edges }
  }

  /** 加载并渲染图谱 */
  async function load() {
    loading.value = true
    try {
      const [data, s] = await Promise.all([
        fetchGraphData(kbId.value),
        fetchGraphStats(kbId.value),
      ])

      nodes.value = data.nodes
      edges.value = data.edges
      stats.value = s
      isSearchActive.value = false
      selectedNode.value = null

      if (graph) {
        const g6Data = transformData(data.nodes, data.edges)
        graph.data(g6Data)
        graph.render()
        graph.fitView()
      }
    } catch (e) {
      console.warn('[ForceGraph] Failed to load graph data:', e)
      nodes.value = []
      edges.value = []
      stats.value = { entityCount: 0, relationCount: 0, entityTypes: [] }
    } finally {
      loading.value = false
    }
  }

  function selectNode(node: GraphNode | null) {
    selectedNode.value = node
    if (graph && node) {
      // 高亮选中的节点
      graph.setItemState(node.id, 'selected', true)
      // 让节点聚焦到视口中央
      graph.focusItem(node.id, true, { easing: 'easeCubic', duration: 300 })
    }
    if (graph && !node) {
      // 取消所有节点选中状态
      graph.getNodes().forEach((n) => {
        graph.clearItemStates(n, 'selected')
      })
    }
  }

  function clearSelection() {
    selectedNode.value = null
    selectedEdge.value = null
    if (graph) {
      graph.getNodes().forEach((n) => {
        graph.clearItemStates(n, 'selected')
      })
    }
  }

  /** 搜索节点（本地 + 远程） */
  function searchNodes(query: string): GraphNode[] {
    if (!query.trim()) return []
    return nodes.value.filter(
      (n) => n.name.includes(query) || (n.label || '').includes(query) || (n.entity_type || '').includes(query),
    )
  }

  /** 搜索并渲染子图（使用后端 API） */
  async function searchAndRender(keyword: string) {
    if (!keyword.trim() || !graph) return
    loading.value = true
    try {
      // 使用后端 API 进行模糊匹配搜索（Cypher regex）
      const data = await searchGraphNodes(kbId.value, keyword, 100)
      nodes.value = data.nodes
      edges.value = data.edges
      isSearchActive.value = true

      const g6Data = transformData(data.nodes, data.edges)
      graph.data(g6Data)
      graph.render()
      graph.fitView()
    } catch (e) {
      console.warn('[ForceGraph] Search failed:', e)
    } finally {
      loading.value = false
    }
  }

  /** 销毁图实例 */
  function destroyGraph() {
    if (graph) {
      graph.destroy()
      graph = null
    }
  }

  return {
    nodes,
    edges,
    stats,
    loading,
    selectedNode,
    selectedEdge,
    entityCount,
    relationCount,
    visibleEntityCount,
    visibleRelationCount,
    isSearchActive,
    entityTypes,
    initGraph,
    load,
    selectNode,
    clearSelection,
    searchNodes,
    searchAndRender,
    destroyGraph,
  }
}
