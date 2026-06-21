<script setup lang="ts">
import { Search, Refresh, Setting, Document } from '@element-plus/icons-vue'
import { useKnowledgeGraph } from '@/composables/useKnowledgeGraph'
import type { GraphNode, GraphViewNode } from '@/types/evaluation'

// --- Props & Emits ---
const props = defineProps<{
  kbId?: string
}>()

const emit = defineEmits<{
  (e: 'select-node', node: GraphNode | null): void
  (e: 'open-settings'): void
  (e: 'open-index'): void
}>()

// --- Data via composable ---
const {
  nodes,
  edges,
  viewNodes,
  entityCount,
  relationCount,
  entityTypes,
  load,
  selectNode,
  clearSelection,
  updateNodePosition,
  searchNodes,
} = useKnowledgeGraph(props.kbId || 'default')

// --- Search ---
const searchQuery = ref('')
const searchResults = ref<GraphNode[]>([])
const showSearchResults = ref(false)

function handleSearch() {
  if (!searchQuery.value.trim()) {
    searchResults.value = []
    showSearchResults.value = false
    return
  }
  searchResults.value = searchNodes(searchQuery.value)
  showSearchResults.value = true
}

function handleRefresh() {
  searchQuery.value = ''
  searchResults.value = []
  showSearchResults.value = false
  clearSelection()
  load()
}

function handleNodeSelect(node: GraphNode) {
  selectNode(node)
  emit('select-node', node)
}

function handleClearSelection() {
  clearSelection()
  emit('select-node', null)
}

// --- Computed viewBox for centering ---
const viewBox = computed(() => {
  const vns = viewNodes.value
  if (vns.length === 0) return '0 0 900 600'

  let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity
  vns.forEach((n) => {
    minX = Math.min(minX, n.x - n.size - 20)
    maxX = Math.max(maxX, n.x + n.size + 20)
    minY = Math.min(minY, n.y - n.size - 20)
    maxY = Math.max(maxY, n.y + n.size + 20)
  })

  const padding = 80
  const width = maxX - minX + padding * 2
  const height = maxY - minY + padding * 2
  return `${minX - padding} ${minY - padding} ${width} ${height}`
})

// --- SVG ref + coordinate conversion ---
// 关键修复：用 getScreenCTM().inverse() 把屏幕像素坐标精确转换为 SVG 内部坐标，
// 解决之前裸用 clientX - rect.left 在 viewBox 缩放下坐标错乱、节点乱飞的问题。
const svgRef = ref<SVGSVGElement>()

/** 屏幕坐标 -> SVG 用户坐标 */
function clientToSvg(clientX: number, clientY: number): { x: number; y: number } {
  const svg = svgRef.value
  if (!svg) return { x: 0, y: 0 }
  const pt = svg.createSVGPoint()
  pt.x = clientX
  pt.y = clientY
  const ctm = svg.getScreenCTM()
  if (!ctm) return { x: 0, y: 0 }
  const transformed = pt.matrixTransform(ctm.inverse())
  return { x: transformed.x, y: transformed.y }
}

// --- Dragging state ---
const dragNode = ref<GraphViewNode | null>(null)
const dragOffset = ref({ x: 0, y: 0 })

function handleNodeMouseDown(e: MouseEvent, node: GraphViewNode) {
  e.stopPropagation()
  dragNode.value = node
  const svgCoord = clientToSvg(e.clientX, e.clientY)
  dragOffset.value = {
    x: svgCoord.x - node.x,
    y: svgCoord.y - node.y,
  }
  handleNodeSelect(node)
}

function handleMouseMove(e: MouseEvent) {
  if (!dragNode.value) return
  const { x, y } = clientToSvg(e.clientX, e.clientY)
  updateNodePosition(dragNode.value.id, x - dragOffset.value.x, y - dragOffset.value.y)
}

function handleMouseUp() {
  dragNode.value = null
}

// 节点详情弹窗（实体类型分布）
const showEntityTypePopup = ref(false)

function handleEntityTypeClick() {
  showEntityTypePopup.value = !showEntityTypePopup.value
}

function closeEntityTypePopup() {
  showEntityTypePopup.value = false
}

// viewNodes 用于模板渲染
const viewNodesById = computed(() => {
  const map = new Map<string, GraphViewNode>()
  viewNodes.value.forEach((n) => map.set(n.id, n))
  return map
})

// --- Lifecycle ---
onMounted(() => {
  load()
})
</script>

<template>
  <div class="knowledge-graph">
    <!-- Graph visualization area -->
    <div class="knowledge-graph__canvas" v-loading="false">
      <!-- Floating search bar -->
      <div class="knowledge-graph__search-float">
        <el-input
          v-model="searchQuery"
          placeholder="搜索实体"
          clearable
          @input="handleSearch"
          @keyup.enter="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button :icon="Search" circle size="small" @click="handleSearch" />
        <el-button :icon="Refresh" circle size="small" @click="handleRefresh" />

        <!-- Search results dropdown -->
        <div v-if="showSearchResults && searchResults.length > 0" class="knowledge-graph__search-results">
          <div
            v-for="node in searchResults"
            :key="node.id"
            class="knowledge-graph__search-item"
            @click="handleNodeSelect(node)"
          >
            <span
              class="knowledge-graph__search-dot"
              :style="{ backgroundColor: viewNodesById.get(node.id)?.color || '#1E88E5' }"
            />
            <span>{{ node.name }}</span>
            <el-tag size="small" type="info">{{ node.label }}</el-tag>
          </div>
        </div>
      </div>

      <!-- Floating action buttons (top right) -->
      <div class="knowledge-graph__actions-float">
        <el-tooltip content="索引管理" placement="bottom">
          <el-button circle @click="emit('open-index')">
            <el-icon><Document /></el-icon>
          </el-button>
        </el-tooltip>
        <el-tooltip content="图谱设置" placement="bottom">
          <el-button circle @click="emit('open-settings')">
            <el-icon><Setting /></el-icon>
          </el-button>
        </el-tooltip>
      </div>

      <!-- SVG Graph -->
      <svg
        ref="svgRef"
        class="knowledge-graph__svg"
        :viewBox="viewBox"
        preserveAspectRatio="xMidYMid meet"
        @mousemove="handleMouseMove"
        @mouseup="handleMouseUp"
        @mouseleave="handleMouseUp"
        @click="handleClearSelection"
      >
        <!-- Edges -->
        <g class="knowledge-graph__edges">
          <line
            v-for="(edge, idx) in edges"
            :key="idx"
            :x1="viewNodesById.get(nodes.find((n) => n.name === edge.source)?.id || '')?.x || 0"
            :y1="viewNodesById.get(nodes.find((n) => n.name === edge.source)?.id || '')?.y || 0"
            :x2="viewNodesById.get(nodes.find((n) => n.name === edge.target)?.id || '')?.x || 0"
            :y2="viewNodesById.get(nodes.find((n) => n.name === edge.target)?.id || '')?.y || 0"
            class="knowledge-graph__edge"
          />
        </g>

        <!-- Nodes -->
        <g class="knowledge-graph__nodes">
          <g
            v-for="node in viewNodes"
            :key="node.id"
            :transform="`translate(${node.x}, ${node.y})`"
            class="knowledge-graph__node"
            :class="{ 'knowledge-graph__node--selected': false }"
            @mousedown="handleNodeMouseDown($event, node)"
            @click.stop="handleNodeSelect(node)"
          >
            <circle
              :r="node.size"
              :fill="node.color"
              stroke="#fff"
              :stroke-width="0"
              class="knowledge-graph__node-circle"
            />
            <text
              :dy="node.size + 14"
              text-anchor="middle"
              class="knowledge-graph__node-label"
            >
              {{ node.name }}
            </text>
          </g>
        </g>
      </svg>

      <!-- Stats bar (bottom left) -->
      <div class="knowledge-graph__stats-float">
        <span class="knowledge-graph__stat-item" @click="handleEntityTypeClick">
          实体 <strong>{{ entityCount }}</strong>
        </span>
        <span class="knowledge-graph__stat-item">
          关系 <strong>{{ relationCount }}</strong>
        </span>
      </div>

      <!-- Entity type popup -->
      <Transition name="popup-fade">
        <div v-if="showEntityTypePopup" class="knowledge-graph__entity-popup">
          <div class="knowledge-graph__entity-popup-header">
            <h4>实体类型分布</h4>
            <el-button link @click="closeEntityTypePopup">×</el-button>
          </div>
          <div class="knowledge-graph__entity-popup-body">
            <div
              v-for="type in entityTypes"
              :key="type.name"
              class="knowledge-graph__entity-type-item"
            >
              <span class="knowledge-graph__entity-dot" :style="{ backgroundColor: type.color }" />
              <span class="knowledge-graph__entity-name">{{ type.name }}</span>
              <span class="knowledge-graph__entity-count">{{ type.count }}</span>
            </div>
          </div>
        </div>
      </Transition>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.knowledge-graph {
  width: 100%;
  height: calc(100vh - 280px);
  min-height: 500px;

  &__canvas {
    width: 100%;
    height: 100%;
    background: $bg-white;
    border-radius: $radius-base;
    box-shadow: $shadow-sm;
    position: relative;
    overflow: hidden;
  }

  &__search-float {
    position: absolute;
    top: $spacing-base;
    left: $spacing-base;
    z-index: 100;
    display: flex;
    gap: $spacing-xs;
    align-items: center;

    .el-input {
      width: 200px;
    }
  }

  &__search-results {
    position: absolute;
    top: 40px;
    left: 0;
    right: 0;
    min-width: 280px;
    background: $bg-white;
    border: 1px solid $border-base;
    border-radius: $radius-base;
    box-shadow: $shadow-base;
    max-height: 300px;
    overflow-y: auto;
    z-index: 200;
  }

  &__search-item {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    padding: $spacing-sm $spacing-base;
    cursor: pointer;
    font-size: 14px;
    color: $text-primary;
    transition: background-color 0.2s;

    &:hover {
      background: $bg-hover;
    }
  }

  &__search-dot {
    width: 10px;
    height: 10px;
    border-radius: 50%;
    flex-shrink: 0;
  }

  &__actions-float {
    position: absolute;
    top: $spacing-base;
    right: $spacing-base;
    z-index: 100;
    display: flex;
    gap: $spacing-xs;
  }

  &__svg {
    width: 100%;
    height: 100%;
    cursor: grab;
    display: block;

    &:active {
      cursor: grabbing;
    }
  }

  &__edge {
    stroke: $border-base;
    stroke-width: 1.5;
    stroke-dasharray: 4 2;
  }

  &__node {
    cursor: pointer;

    &:hover {
      .knowledge-graph__node-circle {
        filter: brightness(1.1);
      }
    }
  }

  &__node--selected {
    .knowledge-graph__node-circle {
      filter: brightness(1.2);
    }
  }

  &__node-circle {
    transition: filter 0.2s;
    filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.15));
  }

  &__node-label {
    font-size: 12px;
    fill: $text-regular;
    pointer-events: none;
    user-select: none;
  }

  &__stats-float {
    position: absolute;
    bottom: $spacing-base;
    left: $spacing-base;
    z-index: 100;
    display: flex;
    gap: $spacing-lg;
    padding: $spacing-sm $spacing-base;
    background: rgba(255, 255, 255, 0.95);
    border-radius: $radius-base;
    box-shadow: $shadow-sm;
    font-size: 13px;
    color: $text-secondary;
  }

  &__stat-item {
    cursor: pointer;
    transition: color 0.2s;

    &:hover {
      color: $color-primary;
    }

    strong {
      color: $text-primary;
      font-weight: 600;
      margin-left: 4px;
    }
  }

  &__entity-popup {
    position: absolute;
    bottom: 60px;
    left: $spacing-base;
    z-index: 200;
    width: 240px;
    background: $bg-white;
    border-radius: $radius-base;
    box-shadow: $shadow-lg;
    overflow: hidden;
  }

  &__entity-popup-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: $spacing-sm $spacing-base;
    border-bottom: 1px solid $border-lighter;
    background: $bg-hover;

    h4 {
      margin: 0;
      font-size: 14px;
      font-weight: 600;
      color: $text-primary;
    }
  }

  &__entity-popup-body {
    padding: $spacing-sm;
    max-height: 300px;
    overflow-y: auto;
  }

  &__entity-type-item {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    padding: $spacing-xs $spacing-sm;
    font-size: 14px;
    color: $text-primary;

    &:hover {
      background: $bg-hover;
      border-radius: $radius-sm;
    }
  }

  &__entity-dot {
    width: 10px;
    height: 10px;
    border-radius: 50%;
    flex-shrink: 0;
  }

  &__entity-name {
    flex: 1;
  }

  &__entity-count {
    color: $text-secondary;
    font-size: 13px;
  }
}

.popup-fade-enter-active,
.popup-fade-leave-active {
  transition: opacity 0.2s ease;
}

.popup-fade-enter-from,
.popup-fade-leave-to {
  opacity: 0;
}
</style>
