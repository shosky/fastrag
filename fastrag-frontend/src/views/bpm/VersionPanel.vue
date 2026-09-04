<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as bpm from '@/api/bpm'
import {
  VERSION_STATUS_LABELS, VERSION_STATUS_TAG_TYPES,
  type FlowDefVO, type FlowVersionVO,
} from '@/types/bpm'

const route = useRoute()
const router = useRouter()
const flowDefId = computed(() => String(route.params.flowDefId || ''))

const loading = ref(false)
const flowDef = ref<FlowDefVO | null>(null)
const list = ref<FlowVersionVO[]>([])
const selectedVersion = ref<FlowVersionVO | null>(null)
const previewNodes = ref<any[]>([])
const previewEdges = ref<any[]>([])
const showCreate = ref(false)
const createForm = reactive({ remark: '', fromPublished: false })

async function load() {
  if (!flowDefId.value) return
  loading.value = true
  try {
    flowDef.value = await bpm.getFlowDef(flowDefId.value)
    list.value = await bpm.listVersions(flowDefId.value)
    if (list.value.length && !selectedVersion.value) {
      selectVersion(list.value[0])
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  } finally { loading.value = false }
}

async function selectVersion(v: FlowVersionVO) {
  selectedVersion.value = v
  try {
    const detail = await bpm.getVersionDetail(flowDefId.value, v.versionNo)
    previewNodes.value = detail.nodes || []
    previewEdges.value = detail.edges || []
  } catch {
    previewNodes.value = []
    previewEdges.value = []
  }
}

async function handleCreate() {
  try {
    const res = await bpm.createDraftVersion(flowDefId.value, createForm)
    ElMessage.success(`已创建草稿 v${res.versionNo}`)
    showCreate.value = false
    createForm.remark = ''
    createForm.fromPublished = false
    await load()
  } catch (e: any) { ElMessage.error(e?.message || '创建失败') }
}

async function handlePublish(v: any) {
  try {
    await ElMessageBox.confirm(`确定发布版本 v${v.versionNo}?发布后将替换线上版本`, '发布确认', { type: 'warning' })
    await bpm.publishVersion(flowDefId.value, v.versionNo)
    ElMessage.success('已发布')
    await load()
  } catch (e: any) { if (e !== 'cancel' && e?.message) ElMessage.error(e.message) }
}

async function handleRollback(v: any) {
  try {
    await ElMessageBox.confirm(`回滚到版本 v${v.versionNo}?会基于该版本创建一份新草稿`, '回滚确认', { type: 'warning' })
    const newVersion = await bpm.rollbackVersion(flowDefId.value, v.versionNo)
    ElMessage.success(`已基于 v${v.versionNo} 创建新草稿 v${newVersion.versionNo}`)
    await load()
  } catch (e: any) { if (e !== 'cancel' && e?.message) ElMessage.error(e.message) }
}

async function handleArchive(v: any) {
  try {
    await ElMessageBox.confirm(`归档版本 v${v.versionNo}?归档后不可再编辑`, '归档确认', { type: 'warning' })
    await bpm.archiveVersion(flowDefId.value, v.versionNo)
    ElMessage.success('已归档')
    await load()
  } catch (e: any) { if (e !== 'cancel' && e?.message) ElMessage.error(e.message) }
}

function handleEditCanvas(v: any) {
  router.push({ name: 'BpmCanvas', params: { flowDefId: flowDefId.value, versionId: v.id } })
}

function formatDate(s?: string) {
  if (!s) return '-'
  return s.replace('T', ' ').substring(0, 19)
}

// ============================================================================
// 预览 SVG 工具函数
// ============================================================================
const NODE_COLOR: Record<string, string> = {
  start: '#67C23A', end: '#F56C6C', user_input: '#909399', llm: '#409EFF',
  kb_retrieval: '#E6A23C', intent: '#9B59B6', http: '#1ABC9C',
  condition: '#FF9800', subflow: '#00B4D8',
}

function getNodeColor(type: string) {
  return NODE_COLOR[type] || '#909399'
}

function getEdgePath(e: any): string {
  const src = previewNodes.value.find(n => n.nodeKey === e.sourceNodeKey)
  const tgt = previewNodes.value.find(n => n.nodeKey === e.targetNodeKey)
  if (!src || !tgt) return ''
  const x1 = (src.positionX || 0) + 150
  const y1 = (src.positionY || 0) + 25
  const x2 = tgt.positionX || 0
  const y2 = (tgt.positionY || 0) + 25
  const cx = Math.abs(x2 - x1) / 2 + 30
  return `M ${x1} ${y1} C ${x1 + cx} ${y1}, ${x2 - cx} ${y2}, ${x2} ${y2}`
}

const previewViewBox = computed(() => {
  if (!previewNodes.value.length) return '0 0 800 500'
  const maxX = Math.max(...previewNodes.value.map((n: any) => (n.positionX || 0) + 200)) + 100
  const maxY = Math.max(...previewNodes.value.map((n: any) => (n.positionY || 0) + 200)) + 100
  return `0 0 ${Math.max(800, maxX)} ${Math.max(500, maxY)}`
})

onMounted(load)
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="toolbar">
      <div class="left">
        <el-button text @click="router.push({ name: 'BpmFlowList' })">📂 流程列表</el-button>
        <span class="title" v-if="flowDef">{{ flowDef.name }} · 版本管理</span>
      </div>
      <el-button type="primary" @click="showCreate = true">＋ 新建草稿版本</el-button>
    </div>

    <div class="vp-layout">
      <div class="vp-list">
        <div class="vp-list-title">历史版本</div>
        <el-table :data="list" stripe size="small" highlight-current-row @row-click="selectVersion" empty-text="暂无版本">
          <el-table-column prop="versionNo" label="版本" width="80">
            <template #default="{ row }">v{{ row.versionNo }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="(VERSION_STATUS_TAG_TYPES as any)[row.status] || 'info'" size="small">
                {{ VERSION_STATUS_LABELS[row.status as keyof typeof VERSION_STATUS_LABELS] || row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
          <el-table-column label="发布时间" width="150">
            <template #default="{ row }">{{ formatDate(row.publishedAt) }}</template>
          </el-table-column>
          <el-table-column label="创建时间" width="150">
            <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="240" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click.stop="handleEditCanvas(row)" v-if="row.status === 'draft'">画布</el-button>
              <el-button link type="primary" size="small" @click.stop="handlePublish(row)" v-if="row.status === 'draft'">发布</el-button>
              <el-button link type="primary" size="small" @click.stop="handleRollback(row)" v-if="row.status === 'published'">回滚</el-button>
              <el-button link type="warning" size="small" @click.stop="handleArchive(row)" v-if="row.status === 'draft' || row.status === 'published'">归档</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="vp-preview">
        <div class="vp-preview-title">
          <span v-if="selectedVersion">预览 v{{ selectedVersion.versionNo }}</span>
          <span v-else>请选择版本</span>
        </div>
        <div v-if="selectedVersion" class="vp-meta">
          <el-tag :type="(VERSION_STATUS_TAG_TYPES as any)[selectedVersion.status] || 'info'" size="small">
            {{ VERSION_STATUS_LABELS[selectedVersion.status as keyof typeof VERSION_STATUS_LABELS] || selectedVersion.status }}
          </el-tag>
          <span class="meta-item">发布人: {{ selectedVersion.publisherId || '-' }}</span>
          <span class="meta-item">发布时间: {{ formatDate(selectedVersion.publishedAt) }}</span>
          <span class="meta-item">节点 {{ previewNodes.length }} · 连线 {{ previewEdges.length }}</span>
        </div>
        <div v-if="previewNodes.length" class="vp-canvas">
          <svg :viewBox="previewViewBox" class="vp-svg">
            <g v-for="(e, i) in previewEdges" :key="`e${i}`">
              <path :d="getEdgePath(e)" stroke="#409EFF" stroke-width="2" fill="none" />
            </g>
            <g v-for="n in previewNodes" :key="n.nodeKey">
              <rect :x="n.positionX" :y="n.positionY" width="150" height="50" rx="4" fill="#fff" :stroke="getNodeColor(n.nodeType)" stroke-width="2" />
              <text :x="(n.positionX || 0) + 10" :y="(n.positionY || 0) + 22" font-size="13" font-weight="600">{{ n.name || n.nodeKey }}</text>
              <text :x="(n.positionX || 0) + 10" :y="(n.positionY || 0) + 40" font-size="11" fill="#909399">{{ n.nodeKey }} · {{ n.nodeType }}</text>
            </g>
          </svg>
        </div>
        <el-empty v-else description="该版本无节点" :image-size="60" />
      </div>
    </div>

    <el-dialog v-model="showCreate" title="新建草稿版本" width="480px">
      <el-form label-width="100px">
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" type="textarea" :rows="3" placeholder="本次草稿的变更说明" />
        </el-form-item>
        <el-form-item label="基于已发布">
          <el-switch v-model="createForm.fromPublished" />
          <span style="margin-left:8px;color:#909399;font-size:12px">开启后从已发布版本复制;否则基于当前最新草稿</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.page-container { padding: $spacing-base; height: 100%; display: flex; flex-direction: column; }

.toolbar {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 12px; flex-wrap: wrap; gap: 8px;
}
.toolbar .left { display: flex; align-items: center; gap: 8px; }
.toolbar .title { font-weight: 600; font-size: 15px; }

.vp-layout {
  display: flex; gap: 12px; flex: 1; min-height: 0;
}
.vp-list {
  width: 720px; flex-shrink: 0; background: #fff; border: 1px solid #ebeef5;
  border-radius: 8px; padding: 12px; overflow: auto; max-height: calc(100vh - 180px);
}
.vp-list-title { font-size: 14px; font-weight: 600; margin-bottom: 8px; }

.vp-preview {
  flex: 1; background: #fff; border: 1px solid #ebeef5; border-radius: 8px;
  padding: 12px; overflow: auto; min-width: 0; max-height: calc(100vh - 180px);
}
.vp-preview-title { font-size: 14px; font-weight: 600; margin-bottom: 8px; }
.vp-meta { display: flex; gap: 12px; align-items: center; margin-bottom: 8px; font-size: 12px; color: #606266; flex-wrap: wrap; }
.meta-item { color: #909399; }
.vp-canvas { background: #fafafa; border-radius: 6px; padding: 12px; overflow: auto; }
.vp-svg { display: block; width: 100%; min-height: 400px; }
</style>