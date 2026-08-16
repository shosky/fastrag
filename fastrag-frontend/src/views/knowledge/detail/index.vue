<script setup lang="ts">
import type { RetrievalConfig } from '@/types/knowledge'
import type { GraphNode, GraphBuildStatus } from '@/types/evaluation'
import { useRouter, useRoute } from 'vue-router'
import {
  ArrowLeft,
  Notebook,
  CopyDocument,
  Edit,
  Document,
  Search,
  Share,
  TrendCharts,
  Setting,
  Operation,
  Promotion,
  Timer,
  ChatDotRound,
  PriceTag,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import FileManager from './components/FileManager.vue'
import SearchTestPanel from './components/SearchTestPanel.vue'
import RetrievalSidebar from './components/RetrievalSidebar.vue'
import KnowledgeGraphPanel from './components/KnowledgeGraphPanel.vue'
import IndexManagementPanel from './components/IndexManagementPanel.vue'
import NodeDetailPanel from './components/NodeDetailPanel.vue'
import GraphSettingsPopup from './components/GraphSettingsPopup.vue'
import RagEvaluationPanel from './components/RagEvaluationPanel.vue'
import EvaluationBenchmarkPanel from './components/EvaluationBenchmarkPanel.vue'
import LogPanel from './components/LogPanel.vue'
import PublishPanel from './components/PublishPanel.vue'
import QaPanel from './components/QaPanel.vue'
import ApiDocPanel from './components/ApiDocPanel.vue'
import * as api from '@/api'

const route = useRoute()
const router = useRouter()
const kbId = route.params.id as string

// --- 从 API 加载知识库信息 ---
const kbInfo = ref({
  id: kbId,
  name: '加载中...',
  creator: '未知',
  createdAt: '',
  model: 'text-embedding-v4',
  dimension: 1024,
  usedSize: '计算中...',
})

async function loadKbInfo() {
  try {
    const [data, files]: any = await Promise.all([
      api.getKnowledgeBaseDetail(kbId),
      api.getFiles(kbId).catch(() => []),
    ])
    // 从文件列表累加实际容量
    const fileList = Array.isArray(files) ? files : (files?.list || files?.records || [])
    const totalBytes = fileList.reduce((sum: number, f: any) => sum + (parseInt(f.size) || 0), 0)
    const totalMB = totalBytes / (1024 * 1024)
    const usedSizeStr = totalMB >= 1024 ? (totalMB / 1024).toFixed(1) + ' GB' : Math.round(Math.max(totalMB, 0.1)) + ' MB'
    if (data) {
      kbInfo.value = {
        id: kbId,
        name: data.name || '未知知识库',
        creator: data.creator || '未知',
        createdAt: data.createdAt || '',
        model: data.embeddingModel || 'text-embedding-v4',
        dimension: data.dimension || 1024,
        usedSize: usedSizeStr,
      }
      // 回填知识库已保存的检索配置（覆盖默认值；null 字段跳过，避免清空已有默认）
      const savedCfg = data.retrievalConfig
      if (savedCfg && typeof savedCfg === 'object') {
        const merged: Record<string, unknown> = { ...searchRetrievalConfig.value }
        for (const [key, value] of Object.entries(savedCfg)) {
          if (value !== null && value !== undefined) merged[key] = value
        }
        searchRetrievalConfig.value = merged as unknown as RetrievalConfig
      }
    }
  } catch {
    // 后端不可用时使用 mock
    kbInfo.value = {
      id: kbId,
      name: '示例知识库',
      creator: '管理员',
      createdAt: '2026-06-28 19:09',
      model: 'text-embedding-v4',
      dimension: 1024,
      usedSize: '156 MB',
    }
  }
}

loadKbInfo()

// --- Active tab ---
const activeTab = ref('files')

// --- Search test retrieval config ---
const searchRetrievalConfig = ref<RetrievalConfig>({
  mode: 'vector',
  topK: 10,
  similarityThreshold: 0.0,
  bm25RecallCount: 50,
  vectorWeight: 0.7,
  bm25Weight: 0.3,
  enableKeywordMatch: true,
  enableGraphExpand: true,
})

// --- Actions ---
function goBack() {
  router.push('/knowledge')
}

function copyId() {
  navigator.clipboard.writeText(kbInfo.value.id).then(() => {
    ElMessage.success('已复制知识库 ID')
  }).catch(() => {
    ElMessage.info(`知识库 ID: ${kbInfo.value.id}`)
  })
}

function goToParseStrategy() {
  router.push(`/knowledge/${kbId}/parse-strategy`)
}

function goToEdit() {
  router.push(`/knowledge/${kbId}/edit`)
}

function goToDebug() {
  router.push(`/knowledge/${kbId}/debug`)
}

function handleSearchConfigUpdate(config: RetrievalConfig) {
  searchRetrievalConfig.value = config
}

async function handleSearchConfigSave() {
  try {
    // 持久化检索配置到知识库（retrievalConfig 字段），name 为 KbCreateRequest 必填
    await api.updateKnowledgeBase(kbId, {
      name: kbInfo.value.name,
      retrievalConfig: searchRetrievalConfig.value,
    })
    ElMessage.success('检索配置已保存')
  } catch (e) {
    ElMessage.error('检索配置保存失败')
    console.error('Failed to save search config:', e)
  }
}

// --- Knowledge Graph state ---
const selectedGraphNode = ref<GraphNode | null>(null)
const graphSettingsVisible = ref(false)
const indexManagementVisible = ref(false)

// --- 图谱构建状态（直接由父组件管理，避免 defineExpose 响应式问题） ---
const graphBuildStatusData = ref<GraphBuildStatus>({
  status: 'idle', progress: 0, entityCount: 0, relationCount: 0,
  totalChunks: 0, builtChunks: 0, failedChunks: 0,
  buildError: null, lastBuiltAt: null,
})
const isBuilding = computed(() => graphBuildStatusData.value.status === 'building')

let buildPollTimer: number | null = null

async function fetchBuildStatus() {
  try {
    // request 拦截器已解包 {code,data,message}，类型上仍是 AxiosResponse，这里断言为数据
    const res = (await api.getGraphBuildStatus(kbId || '')) as unknown as GraphBuildStatus
    if (res) graphBuildStatusData.value = res
  } catch (e) {
    console.warn('[GraphBuild] Failed to fetch build status:', e)
  }
}

// 构建中时自动轮询
watch(isBuilding, (building) => {
  if (building) {
    buildPollTimer = window.setInterval(fetchBuildStatus, 5000)
  } else {
    if (buildPollTimer) { clearInterval(buildPollTimer); buildPollTimer = null }
  }
})

onMounted(() => { fetchBuildStatus() })
onBeforeUnmount(() => { if (buildPollTimer) { clearInterval(buildPollTimer) } })

function handleRefreshBuildStatus() { fetchBuildStatus() }

function handleGraphNodeSelect(node: GraphNode | null) {
  selectedGraphNode.value = node
}

function handleOpenGraphSettings() {
  graphSettingsVisible.value = true
  indexManagementVisible.value = false
}

async function handleGraphSettingsApply(settings: any) {
  graphSettingsVisible.value = false
  if (!kbId) return
  try {
    // 持久化到 kb_graph_index.settings（含 entitySchema 等），下次图谱构建生效
    await api.saveGraphSettings(kbId, settings)
    ElMessage.success('图谱设置已保存，下次构建生效')
  } catch (e: any) {
    ElMessage.error('保存设置失败: ' + (e.message || e))
  }
}

function handleOpenIndexManagement() {
  indexManagementVisible.value = true
  graphSettingsVisible.value = false
}

// --- 评估面板：基准面板"发起评估"快捷入口的跨 tab 联动（传基准 id）---
const preselectBenchmark = ref('')

function handleStartEvaluationFromBenchmark(benchmarkId: string) {
  preselectBenchmark.value = benchmarkId
  activeTab.value = 'evaluation'
}
</script>

<template>
  <div class="kb-detail">
    <!-- Header -->
    <div class="kb-detail__header">
      <div class="kb-detail__header-left">
        <el-button :icon="ArrowLeft" link class="kb-detail__back" @click="goBack">
          返回
        </el-button>
        <el-divider direction="vertical" />
        <el-icon :size="20" class="kb-detail__icon"><Notebook /></el-icon>
        <div class="kb-detail__title-group">
          <h2 class="kb-detail__name">{{ kbInfo.name }}</h2>
          <span class="kb-detail__meta">知识库 · {{ kbInfo.usedSize }}</span>
        </div>
      </div>
      <div class="kb-detail__header-right">
        <el-button :icon="CopyDocument" @click="copyId">复制 ID</el-button>
        <el-button v-permission="'kb:manage_strategy'" :icon="Operation" @click="goToParseStrategy">解析策略</el-button>
        <el-button v-permission="'kb:edit'" type="primary" :icon="Edit" @click="goToEdit">编辑</el-button>
      </div>
    </div>

    <!-- Tab navigation -->
    <el-tabs v-model="activeTab" class="kb-detail__tabs">
      <el-tab-pane name="files">
        <template #label>
          <span class="kb-detail__tab-label">
            <el-icon><Document /></el-icon>
            文件管理
          </span>
        </template>
        <FileManager :kb-id="kbId" />
      </el-tab-pane>

      <el-tab-pane name="retrieval">
        <template #label>
          <span class="kb-detail__tab-label">
            <el-icon><Search /></el-icon>
            检索测试
          </span>
        </template>
        <div class="kb-detail__retrieval-layout">
          <!-- Left: Search panel -->
          <div class="kb-detail__retrieval-main">
            <SearchTestPanel
              :config="searchRetrievalConfig"
              :kb-id="kbId"
              @update:config="handleSearchConfigUpdate"
            />
          </div>
          <!-- Right: Config sidebar -->
          <RetrievalSidebar
            :config="searchRetrievalConfig"
            @update:config="handleSearchConfigUpdate"
            @save="handleSearchConfigSave"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane name="graph">
        <template #label>
          <span class="kb-detail__tab-label">
            <el-icon><Share /></el-icon>
            知识图谱
          </span>
        </template>
        <div class="kb-detail__graph-layout">
          <KnowledgeGraphPanel
            :kb-id="kbId"
            :build-status="graphBuildStatusData"
            @select-node="handleGraphNodeSelect"
            @open-settings="handleOpenGraphSettings"
            @open-index="handleOpenIndexManagement"
          />
          <NodeDetailPanel :node="selectedGraphNode" @close="selectedGraphNode = null" />
          <IndexManagementPanel
            v-model:visible="indexManagementVisible"
            :kb-id="kbId"
            :build-status="graphBuildStatusData"
            @open-settings="handleOpenGraphSettings"
            @refresh="handleRefreshBuildStatus"
          />
          <GraphSettingsPopup
            v-model:visible="graphSettingsVisible"
            :kb-id="kbId"
            @apply="handleGraphSettingsApply"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane name="evaluation">
        <template #label>
          <span class="kb-detail__tab-label">
            <el-icon><TrendCharts /></el-icon>
            评估
          </span>
        </template>
        <el-tabs type="border-card" style="margin-top: 8px">
          <el-tab-pane label="评估基准">
            <EvaluationBenchmarkPanel
              :kb-id="kbId"
              @start-evaluation="handleStartEvaluationFromBenchmark"
            />
          </el-tab-pane>
          <el-tab-pane label="评估结果">
            <RagEvaluationPanel
              :kb-id="kbId"
              :preselect-benchmark="preselectBenchmark"
              @consume-preselect="preselectBenchmark = ''"
            />
          </el-tab-pane>
        </el-tabs>
      </el-tab-pane>

      <el-tab-pane name="qa">
        <template #label>
          <span class="kb-detail__tab-label">
            <el-icon><ChatDotRound /></el-icon>
            问答对
          </span>
        </template>
        <QaPanel :kb-id="kbId" />
      </el-tab-pane>

      <el-tab-pane v-if="false" name="publish">
        <template #label>
          <span class="kb-detail__tab-label">
            <el-icon><Promotion /></el-icon>
            发布管理
          </span>
        </template>
        <PublishPanel :kb-id="kbId" :kb-name="kbInfo.name" />
      </el-tab-pane>

      <el-tab-pane name="log">
        <template #label>
          <span class="kb-detail__tab-label">
            <el-icon><Timer /></el-icon>
            日志管理
          </span>
        </template>
        <LogPanel :kb-id="kbId" />
      </el-tab-pane>

      <el-tab-pane name="api-doc">
        <template #label>
          <span class="kb-detail__tab-label">
            <el-icon><PriceTag /></el-icon>
            API 文档
          </span>
        </template>
        <ApiDocPanel :kb-id="kbId" />
      </el-tab-pane>

    </el-tabs>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.kb-detail {
  display: flex;
  flex-direction: column;
  gap: $spacing-base;

  // --- Header ---
  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    background: $bg-white;
    border-radius: $radius-base;
    padding: $spacing-base $spacing-lg;
    box-shadow: $shadow-sm;
  }

  &__header-left {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
  }

  &__back {
    font-size: 14px;
  }

  &__icon {
    color: $color-primary;
  }

  &__title-group {
    display: flex;
    align-items: baseline;
    gap: $spacing-sm;
  }

  &__name {
    margin: 0;
    font-size: 18px;
    font-weight: 600;
    color: $text-primary;
    line-height: 1.3;
  }

  &__meta {
    font-size: 13px;
    color: $text-secondary;
  }

  &__header-right {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
  }

  // --- Tabs ---
  &__tabs {
    background: $bg-white;
    border-radius: $radius-base;
    padding: 0 $spacing-lg;
    box-shadow: $shadow-sm;

    :deep(.el-tabs__header) {
      margin-bottom: 0;
    }

    :deep(.el-tabs__nav-wrap::after) {
      height: 1px;
    }

    :deep(.el-tab-pane) {
      padding: $spacing-lg 0;
    }
  }

  &__tab-label {
    display: inline-flex;
    align-items: center;
    gap: $spacing-xs;
    font-size: 14px;
  }

  // --- Tab content shared ---
  &__tab-content {
    display: flex;
    flex-direction: column;
    gap: $spacing-lg;
  }

  &__hint {
    font-size: 13px;
    color: $text-secondary;
    margin: 0;
    line-height: 1.6;
  }

  // --- Retrieval tab ---
  &__retrieval-layout {
    display: flex;
    gap: $spacing-lg;
    align-items: flex-start;
  }

  &__retrieval-main {
    flex: 1;
    min-width: 0;
  }

  &__retrieval-actions {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
  }

  // --- Graph tab ---
  &__graph-layout {
    position: relative;
    min-height: 500px;
  }

  // --- Settings tab ---
  &__settings-section {
    display: flex;
    flex-direction: column;
    gap: $spacing-base;

    h3 {
      margin: 0;
      font-size: 16px;
      font-weight: 600;
      color: $text-primary;
    }
  }
}
</style>
