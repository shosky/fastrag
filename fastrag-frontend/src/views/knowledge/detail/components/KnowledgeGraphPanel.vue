<script setup lang="ts">
import { Search, Refresh, Setting, Document, Loading } from '@element-plus/icons-vue'
import { useForceGraph } from '@/composables/useForceGraph'
import type { GraphNode, GraphEdge, GraphBuildStatus } from '@/types/evaluation'
import { ElMessage } from 'element-plus'
import { retryGraphBuild } from '@/api'

// --- Props & Emits ---
const props = defineProps<{
  kbId?: string
  buildStatus: GraphBuildStatus
}>()

const emit = defineEmits<{
  (e: 'select-node', node: GraphNode | null): void
  (e: 'select-edge', edge: GraphEdge | null): void
  (e: 'open-settings'): void
  (e: 'open-index'): void
}>()

// --- G6 container ref ---
const graphContainer = ref<HTMLDivElement>()

// --- Reactive kbId ---
const kbIdRef = computed(() => props.kbId || 'default')

// --- Data via composable ---
const {
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
} = useForceGraph(graphContainer, kbIdRef, (node) => {
  selectNode(node)
  emit('select-node', node)
}, (edge) => {
  emit('select-edge', edge)
})

// 构建状态从 props 获取
const isBuilding = computed(() => props.buildStatus.status === 'building')

// 标准化 entityTypes：兼容后端返回 string[] 和 EntityType[]
const normalizedEntityTypes = computed(() => {
  const raw = entityTypes.value
  if (!raw || raw.length === 0) return []
  const colors = ['#409EFF','#67C23A','#E6A23C','#F56C6C','#909399','#B37FEB','#36CFC9','#F2A8B8']
  // 如果是字符串数组（旧版后端返回的 entity_type 列表）
  if (typeof raw[0] === 'string') {
    return (raw as unknown as string[]).map((name, i) => ({
      name,
      count: 0,
      color: colors[i % colors.length],
    }))
  }
  // 已是 {name, count} 对象数组（新版后端）：补齐 color
  return (raw as unknown as { name: string; count: number }[]).map((t, i) => ({
    name: t.name,
    count: t.count,
    color: colors[i % colors.length],
  }))
})

// --- Search ---
const searchQuery = ref('')
const searchResults = ref<GraphNode[]>([])
const showSearchResults = ref(false)

function handleSearch() {
  if (!searchQuery.value.trim()) {
    searchResults.value = []
    showSearchResults.value = false
    load()
    return
  }
  // 先本地搜索显示下拉
  searchResults.value = searchNodes(searchQuery.value)
  showSearchResults.value = true
}

function handleSearchSelect(node: GraphNode) {
  selectNode(node)
  emit('select-node', node)
  showSearchResults.value = false
}

function handleRemoteSearch() {
  searchAndRender(searchQuery.value)
  showSearchResults.value = false
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

// --- 节点详情弹窗 ---
const showEntityTypePopup = ref(false)

function handleEntityTypeClick() {
  showEntityTypePopup.value = !showEntityTypePopup.value
}

function closeEntityTypePopup() {
  showEntityTypePopup.value = false
}

function closeEdgePopup() {
  emit('select-edge', null)
}

// --- 构建状态条 ---
const showBuildBanner = ref(false)
const bannerDismissTimer = ref<number | null>(null)

watch(() => props.buildStatus.status, (newStatus) => {
  if (newStatus === 'building') {
    showBuildBanner.value = true
  } else if (newStatus === 'completed' || newStatus === 'failed') {
    // 完成后 8 秒自动隐藏
    if (bannerDismissTimer.value) clearTimeout(bannerDismissTimer.value)
    bannerDismissTimer.value = window.setTimeout(() => {
      showBuildBanner.value = false
    }, 8000)
  }
}, { immediate: true })

async function handleBannerRetry() {
  if (!props.kbId) return
  try {
    await retryGraphBuild(props.kbId)
    ElMessage.success('已重试图谱构建')
  } catch (e: any) {
    ElMessage.error('重试失败: ' + (e.message || e))
  }
}

// --- Lifecycle ---
onMounted(() => {
  nextTick(() => {
    initGraph()
    load()
  })
})

onBeforeUnmount(() => {
  if (bannerDismissTimer.value) clearTimeout(bannerDismissTimer.value)
  destroyGraph()
})
</script>

<template>
  <div class="knowledge-graph">
    <!-- Graph visualization area -->
    <div class="knowledge-graph__canvas" v-loading="loading">
      <!-- Build status banner -->
      <Transition name="banner-slide">
        <div
          v-if="showBuildBanner && (isBuilding || buildStatus.status === 'completed' || buildStatus.status === 'failed')"
          class="knowledge-graph__build-banner"
          :class="{
            'knowledge-graph__build-banner--building': isBuilding,
            'knowledge-graph__build-banner--completed': buildStatus.status === 'completed',
            'knowledge-graph__build-banner--failed': buildStatus.status === 'failed',
          }"
        >
          <template v-if="isBuilding">
            <el-icon class="is-loading"><Loading /></el-icon>
            <span class="knowledge-graph__banner-text">知识图谱构建中</span>
            <el-progress
              :percentage="buildStatus.progress"
              :stroke-width="6"
              :show-text="false"
              class="knowledge-graph__banner-progress"
            />
            <span class="knowledge-graph__banner-stats">
              {{ buildStatus.entityCount }} 实体 · {{ buildStatus.relationCount }} 关系
              · {{ buildStatus.builtChunks }}/{{ buildStatus.totalChunks }} chunks
            </span>
          </template>
          <template v-else-if="buildStatus.status === 'completed'">
            <span style="font-size:16px;">✅</span>
            <span class="knowledge-graph__banner-text">图谱构建完成</span>
            <span class="knowledge-graph__banner-stats">
              {{ buildStatus.entityCount }} 实体 · {{ buildStatus.relationCount }} 关系
            </span>
          </template>
          <template v-else-if="buildStatus.status === 'failed'">
            <span style="font-size:16px;">❌</span>
            <span class="knowledge-graph__banner-text">图谱构建失败</span>
            <span v-if="buildStatus.buildError" class="knowledge-graph__banner-error">
              {{ buildStatus.buildError }}
            </span>
            <el-button size="small" type="warning" plain @click="handleBannerRetry">
              重试
            </el-button>
          </template>
        </div>
      </Transition>

      <!-- G6 渲染容器 -->
      <div ref="graphContainer" class="knowledge-graph__g6-container" />

      <!-- Floating search bar -->
      <div class="knowledge-graph__search-float">
        <el-input
          v-model="searchQuery"
          placeholder="搜索实体"
          clearable
          @input="handleSearch"
          @keyup.enter="handleRemoteSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button :icon="Search" circle size="small" @click="handleRemoteSearch" />
        <el-button :icon="Refresh" circle size="small" @click="handleRefresh" />
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

      <!-- Stats bar (bottom left) -->
      <div class="knowledge-graph__stats-float">
        <span class="knowledge-graph__stat-item" @click="handleEntityTypeClick">
          实体 <strong>{{ entityCount }}</strong>
          <template v-if="isSearchActive">
            <span class="knowledge-graph__stat-sep">/</span>
            <span class="knowledge-graph__stat-visible">{{ visibleEntityCount }}</span>
          </template>
        </span>
        <span class="knowledge-graph__stat-item">
          关系 <strong>{{ relationCount }}</strong>
          <template v-if="isSearchActive">
            <span class="knowledge-graph__stat-sep">/</span>
            <span class="knowledge-graph__stat-visible">{{ visibleRelationCount }}</span>
          </template>
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
              v-for="(type, idx) in normalizedEntityTypes"
              :key="idx"
              class="knowledge-graph__entity-type-item"
            >
              <span class="knowledge-graph__entity-dot" :style="{ backgroundColor: type.color }" />
              <span class="knowledge-graph__entity-name">{{ type.name }}</span>
              <span class="knowledge-graph__entity-count">{{ type.count }}</span>
            </div>
          </div>
        </div>
      </Transition>

      <!-- Relation detail popup -->
      <Transition name="popup-fade">
        <div v-if="selectedEdge" class="knowledge-graph__edge-popup">
          <div class="knowledge-graph__edge-popup-header">
            <h4>关系详情</h4>
            <el-button link @click="closeEdgePopup">×</el-button>
          </div>
          <div class="knowledge-graph__edge-popup-body">
            <div class="knowledge-graph__edge-row">
              <span class="knowledge-graph__edge-label">关系类型</span>
              <el-tag size="small" type="warning">{{ selectedEdge.label || 'RELATED' }}</el-tag>
            </div>
            <div class="knowledge-graph__edge-row">
              <span class="knowledge-graph__edge-label">源实体</span>
              <span class="knowledge-graph__edge-value">{{ selectedEdge.source }}</span>
            </div>
            <div class="knowledge-graph__edge-row">
              <span class="knowledge-graph__edge-label">目标实体</span>
              <span class="knowledge-graph__edge-value">{{ selectedEdge.target }}</span>
            </div>
            <div class="knowledge-graph__edge-row">
              <span class="knowledge-graph__edge-label">关系描述</span>
              <span class="knowledge-graph__edge-value">
                {{ selectedEdge.source }} → {{ selectedEdge.label || 'RELATED' }} → {{ selectedEdge.target }}
              </span>
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

  &__g6-container {
    width: 100%;
    height: 100%;
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

  &__actions-float {
    position: absolute;
    top: $spacing-base;
    right: $spacing-base;
    z-index: 100;
    display: flex;
    gap: $spacing-xs;
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

  // --- Relation detail popup ---
  &__edge-popup {
    position: absolute;
    bottom: 60px;
    left: $spacing-base;
    z-index: 200;
    width: 280px;
    background: $bg-white;
    border-radius: $radius-base;
    box-shadow: $shadow-lg;
    overflow: hidden;
  }

  &__edge-popup-header {
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

  &__edge-popup-body {
    padding: $spacing-sm;
    display: flex;
    flex-direction: column;
    gap: $spacing-xs;
  }

  &__edge-row {
    display: flex;
    flex-direction: column;
    gap: 2px;
    padding: $spacing-xs $spacing-sm;
  }

  &__edge-label {
    font-size: 12px;
    color: $text-secondary;
    font-family: monospace;
  }

  &__edge-value {
    font-size: 13px;
    color: $text-primary;
    word-break: break-all;
  }

  &__stat-sep {
    color: $text-secondary;
    margin: 0 2px;
    font-size: 12px;
  }

  &__stat-visible {
    color: $color-primary;
    font-size: 12px;
    font-weight: 400;
  }

  // --- Build banner ---
  &__build-banner {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    z-index: 150;
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    padding: $spacing-sm $spacing-base;
    font-size: 13px;
    color: #fff;

    &--building {
      background: linear-gradient(90deg, #409EFF, #67C23A);
    }

    &--completed {
      background: $color-success;
    }

    &--failed {
      background: $color-danger;
    }
  }

  &__banner-text {
    font-weight: 600;
    white-space: nowrap;
  }

  &__banner-progress {
    flex: 1;
    max-width: 200px;

    :deep(.el-progress-bar__outer) {
      background-color: rgba(255, 255, 255, 0.3);
    }

    :deep(.el-progress-bar__inner) {
      background-color: #fff;
    }
  }

  &__banner-stats {
    opacity: 0.9;
    font-size: 12px;
    white-space: nowrap;
  }

  &__banner-error {
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    opacity: 0.9;
    font-size: 12px;
  }
}

.banner-slide-enter-active,
.banner-slide-leave-active {
  transition: all 0.3s ease;
}

.banner-slide-enter-from,
.banner-slide-leave-to {
  transform: translateY(-100%);
  opacity: 0;
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
