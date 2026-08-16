import { ref, computed, onMounted, onBeforeUnmount, type Ref } from 'vue'
import G6 from '@antv/g6'
import type { GraphNode, GraphEdge, GraphStats, EntityType } from '@/types/evaluation'
import { fetchGraphData, fetchGraphStats, searchGraphNodes, expandGraphSubgraph } from '@/api'
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

  // @antv/g6 默认导出为值对象（非命名空间），类型位置需用 InstanceType 推导图实例类型
  let graph: InstanceType<typeof G6.Graph> | null = null

  // 搜索是否激活（用于区分"全量"和"搜索结果"显示）
  const isSearchActive = ref(false)
  // 是否展示 Chunk 节点（对齐 Yuxi exclude_chunk 参数）
  const showChunks = ref(false)

  // 最近一次加载的完整数据集（类型过滤在其上操作，避免过滤后无法恢复）
  let allNodes: GraphNode[] = []
  let allEdges: GraphEdge[] = []
  // 当前类型过滤（null = 不过滤）
  let currentTypeFilter: string | null = null

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

  /**
   * 将后端数据转换为 G6 格式。
   * KG-01：边端点优先使用后端返回的 source_id/target_id（实体确定性 ID），
   * 避免同名不同型实体被 nameToId 映射连错；无 id 的历史数据回退名称映射。
   */
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

    // source/target 名称映射（仅作为无 id 历史数据的兜底）
    const nameToId = new Map<string, string>()
    rawNodes.forEach((n) => {
      nameToId.set(n.name, n.id)
    })

    const g6Edges = rawEdges.map((e, i) => {
      const sourceId = (e.source_id && nodeMap.has(e.source_id))
        ? e.source_id
        : (nameToId.get(e.source) || e.source)
      const targetId = (e.target_id && nodeMap.has(e.target_id))
        ? e.target_id
        : (nameToId.get(e.target) || e.target)
      return {
        id: e.id || `edge_${i}`,
        source: sourceId,
        target: targetId,
        label: e.label,
      }
    })

    return { nodes: g6Nodes, edges: g6Edges }
  }

  /** 按当前数据集 + 类型过滤渲染（过滤后仅保留两端都可见的边） */
  function applyRender() {
    if (!graph) return
    const filteredNodes = currentTypeFilter
      ? allNodes.filter((n) => (n.label || n.entity_type) === currentTypeFilter)
      : allNodes
    const visibleIds = new Set(filteredNodes.map((n) => n.id))
    const filteredEdges = allEdges.filter((e) => {
      const sourceId = e.source_id && visibleIds.has(e.source_id) ? e.source_id : e.source
      const targetId = e.target_id && visibleIds.has(e.target_id) ? e.target_id : e.target
      return visibleIds.has(sourceId) && visibleIds.has(targetId)
    })

    nodes.value = filteredNodes
    edges.value = filteredEdges

    const g6Data = transformData(filteredNodes, filteredEdges)
    graph.data(g6Data)
    graph.render()
    graph.fitView()
  }

  /** 加载并渲染图谱 */
  async function load(excludeChunks: boolean = true) {
    loading.value = true
    try {
      const [data, s] = await Promise.all([
        fetchGraphData(kbId.value, excludeChunks),
        fetchGraphStats(kbId.value),
      ])

      allNodes = data.nodes
      allEdges = data.edges
      stats.value = s
      isSearchActive.value = false
      currentTypeFilter = null
      selectedNode.value = null

      applyRender()
    } catch (e) {
      console.warn('[ForceGraph] Failed to load graph data:', e)
      allNodes = []
      allEdges = []
      nodes.value = []
      edges.value = []
      stats.value = { entityCount: 0, relationCount: 0, entityTypes: [] }
    } finally {
      loading.value = false
    }
  }

  /** 按实体类型过滤下钻（null 恢复全量） */
  function filterByType(type: string | null) {
    currentTypeFilter = type
    applyRender()
  }

  /** 切换 Chunk 节点展示（对齐 Yuxi exclude_chunk 参数） */
  async function toggleChunks(show: boolean) {
    showChunks.value = show
    await load(!show)
  }

  /** 以选中节点为种子展开邻居子图（检索增强） */
  async function expandNeighbors(node: GraphNode, depth: number = 2) {
    if (!node?.name || !graph) return
    loading.value = true
    try {
      const raw = await expandGraphSubgraph(kbId.value, node.name, depth, 30) as any
      // 后端 expandGraph 返回 {entities, relations, expandedQuery}，映射为前端 {nodes, edges}
      allNodes = (raw?.entities || []).map((e: any) => ({
        id: e.entity_id || e.id,
        name: e.name,
        entity_type: e.entity_type,
        label: e.entity_type || 'Entity',
        type: 'entity',
        normalizedName: e.normalized_name,
      }))
      allEdges = (raw?.relations || []).map((r: any) => ({
        id: r.triple_id,
        source: r.source,
        target: r.target,
        source_id: r.source_id,
        target_id: r.target_id,
        label: r.label,
      }))
      isSearchActive.value = true
      currentTypeFilter = null
      applyRender()
    } catch (e) {
      console.warn('[ForceGraph] Expand neighbors failed:', e)
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
      // 局部常量捕获：闭包内可空收窄失效（TS18047）
      const g = graph
      g.getNodes().forEach((n) => {
        g.clearItemStates(n, 'selected')
      })
    }
  }

  function clearSelection() {
    selectedNode.value = null
    selectedEdge.value = null
    if (graph) {
      const g = graph
      g.getNodes().forEach((n) => {
        g.clearItemStates(n, 'selected')
      })
    }
  }

  /** 搜索节点（本地 + 远程） */
  function searchNodes(query: string): GraphNode[] {
    if (!query.trim()) return []
    return allNodes.filter(
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
      allNodes = data.nodes
      allEdges = data.edges
      isSearchActive.value = true
      currentTypeFilter = null

      applyRender()
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
    showChunks,
    entityTypes,
    initGraph,
    load,
    filterByType,
    toggleChunks,
    expandNeighbors,
    selectNode,
    clearSelection,
    searchNodes,
    searchAndRender,
    destroyGraph,
  }
}
