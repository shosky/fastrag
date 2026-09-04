<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount, computed, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as bpm from '@/api/bpm'
import {
  BPM_NODE_TYPE_LABELS, BPM_NODE_TYPE_COLORS, BPM_NODE_TYPE_ICONS,
  VERSION_STATUS_LABELS, VERSION_STATUS_TAG_TYPES,
  type FlowDefVO, type FlowVersionVO, type NodeTypeVO, type NodeConfigField,
  type NodeVO, type EdgeVO, type CanvasResponse, type CanvasValidateResultVO,
  type BpmNodeType, type NodeRequest, type EdgeRequest,
} from '@/types/bpm'

const route = useRoute()
const router = useRouter()

// ============================================================================
// 边类型辅助(颜色 + 标签)
// ============================================================================
const EDGE_KIND_COLOR: Record<string, string> = {
  default_edge: '#409EFF',
  condition: '#E6A23C',
  parallel: '#67C23A',
  exception: '#F56C6C',
}
const EDGE_KIND_LABEL_MAP: Record<string, string> = {
  default_edge: '默认',
  condition: '条件',
  parallel: '并行',
  exception: '异常',
}

// ============================================================================
// 路由参数
// ============================================================================
const flowDefId = computed(() => String(route.params.flowDefId || ''))
const initialVersionId = computed(() => String(route.params.versionId || ''))

// ============================================================================
// 状态
// ============================================================================
const loading = ref(false)
const saving = ref(false)
const flowDef = ref<FlowDefVO | null>(null)
const versionList = ref<FlowVersionVO[]>([])
const currentVersionId = ref('')
const currentVersionNo = ref<number | null>(null)
const currentVersionStatus = ref<string>('')
const nodeTypes = ref<NodeTypeVO[]>([])

// 画布数据(内存中的 nodes/edges,与后端同步)
const nodes = ref<NodeVO[]>([])
const edges = ref<EdgeVO[]>([])
const validation = ref<CanvasValidateResultVO | null>(null)

// 节点类型元数据(解析后的 schema)
const nodeTypeSchemas = ref<Record<string, NodeConfigField[]>>({})

// 选中
const selectedNodeKey = ref<string | null>(null)
const selectedEdgeId = ref<string | null>(null)
const selectedNode = computed(() => nodes.value.find(n => n.nodeKey === selectedNodeKey.value) || null)
const selectedEdge = computed(() => edges.value.find(e => e.id === selectedEdgeId.value) || null)

// 节点配置编辑表单
const nodeEditForm = reactive<{
  name: string
  config: Record<string, any>
  timeoutMs: number
  retryCount: number
  retryIntervalMs: number
  onFailure: 'fail' | 'ignore' | 'branch'
  failureBranchNodeKey: string
}>({
  name: '', config: {}, timeoutMs: 60000, retryCount: 0, retryIntervalMs: 1000,
  onFailure: 'fail', failureBranchNodeKey: '',
})

// 边编辑表单
const edgeEditForm = reactive<{
  label: string
  edgeKind: string
  conditionExpr: string
  priority: number
}>({ label: '', edgeKind: 'default_edge', conditionExpr: '', priority: 0 })

// 添加节点对话框
const showAddNode = ref(false)
const newNodeForm = reactive({
  nodeType: 'llm' as BpmNodeType,
  nodeKey: '',
  name: '',
})

const showValidationDetail = ref(false)
const dirty = ref(false)
const svgRef = ref<SVGSVGElement | null>(null)
const NODE_W = 160
const NODE_H = 60

// ============================================================================
// 加载数据
// ============================================================================
async function loadAll() {
  if (!flowDefId.value) return
  loading.value = true
  try {
    flowDef.value = await bpm.getFlowDef(flowDefId.value)
    versionList.value = await bpm.listVersions(flowDefId.value)
    nodeTypes.value = await bpm.listNodeTypes()
    parseNodeTypeSchemas()
  } catch (e: any) {
    ElMessage.error(e?.message || '加载流程失败')
  } finally { loading.value = false }
}

function parseNodeTypeSchemas() {
  const map: Record<string, NodeConfigField[]> = {}
  for (const nt of nodeTypes.value) {
    if (!nt.configSchema) continue
    try {
      const arr = JSON.parse(nt.configSchema)
      if (Array.isArray(arr)) map[nt.type] = arr
    } catch { /* ignore */ }
  }
  if (!map.start) map.start = []
  if (!map.end) map.end = []
  if (!map.user_input) map.user_input = [
    { key: 'fields', label: '输入字段(JSON)', type: 'json' },
    { key: 'submitLabel', label: '提交按钮文本', type: 'input' },
  ]
  nodeTypeSchemas.value = map
}

async function loadCanvas(versionId: string) {
  if (!flowDefId.value || !versionId) return
  loading.value = true
  try {
    const res: CanvasResponse = await bpm.getCanvas(flowDefId.value, versionId)
    nodes.value = res.detail?.nodes || []
    edges.value = res.detail?.edges || []
    validation.value = res.validation
    currentVersionId.value = versionId
    const v = versionList.value.find(vv => vv.id === versionId)
    currentVersionNo.value = v?.versionNo || null
    currentVersionStatus.value = v?.status || ''
    selectedNodeKey.value = null
    selectedEdgeId.value = null
    dirty.value = false
  } catch (e: any) {
    ElMessage.error(e?.message || '加载画布失败')
  } finally { loading.value = false }
}

async function selectVersion(versionId: string) {
  if (dirty.value) {
    try {
      await ElMessageBox.confirm('画布有未保存的修改,是否放弃?', '确认切换版本', { type: 'warning' })
    } catch { return }
  }
  await loadCanvas(versionId)
}

// ============================================================================
// 工具函数
// ============================================================================
function clientToSvg(evt: MouseEvent): { x: number; y: number } {
  const svg = svgRef.value
  if (!svg) return { x: 0, y: 0 }
  const pt = svg.createSVGPoint()
  pt.x = evt.clientX
  pt.y = evt.clientY
  const ctm = svg.getScreenCTM()
  if (!ctm) return { x: 0, y: 0 }
  const inv = ctm.inverse()
  const p = pt.matrixTransform(inv)
  return { x: p.x, y: p.y }
}

function getNodeColor(type: string) {
  return (BPM_NODE_TYPE_COLORS as any)[type] || '#909399'
}
function getNodeIcon(type: string) {
  return (BPM_NODE_TYPE_ICONS as any)[type] || '⬡'
}
function getNodeLabel(type: string) {
  return (BPM_NODE_TYPE_LABELS as any)[type] || type
}

function getEdgePath(e: EdgeVO): string {
  const src = nodes.value.find(n => n.nodeKey === e.sourceNodeKey)
  const tgt = nodes.value.find(n => n.nodeKey === e.targetNodeKey)
  if (!src || !tgt) return ''
  const x1 = (src.positionX || 0) + NODE_W
  const y1 = (src.positionY || 0) + NODE_H / 2
  const x2 = tgt.positionX || 0
  const y2 = (tgt.positionY || 0) + NODE_H / 2
  const cx = Math.abs(x2 - x1) / 2 + 30
  return `M ${x1} ${y1} C ${x1 + cx} ${y1}, ${x2 - cx} ${y2}, ${x2} ${y2}`
}

function getArrowPoints(e: EdgeVO): string {
  const src = nodes.value.find(n => n.nodeKey === e.sourceNodeKey)
  const tgt = nodes.value.find(n => n.nodeKey === e.targetNodeKey)
  if (!src || !tgt) return ''
  const x2 = tgt.positionX || 0
  const y2 = (tgt.positionY || 0) + NODE_H / 2
  const size = 6
  return `${x2},${y2} ${x2 - size},${y2 - size} ${x2 - size},${y2 + size}`
}

function getEdgeLabelPos(e: EdgeVO): { x: number; y: number } {
  const src = nodes.value.find(n => n.nodeKey === e.sourceNodeKey)
  const tgt = nodes.value.find(n => n.nodeKey === e.targetNodeKey)
  if (!src || !tgt) return { x: 0, y: 0 }
  const x1 = (src.positionX || 0) + NODE_W
  const y1 = (src.positionY || 0) + NODE_H / 2
  const x2 = tgt.positionX || 0
  const y2 = (tgt.positionY || 0) + NODE_H / 2
  return { x: (x1 + x2) / 2, y: (y1 + y2) / 2 - 6 }
}

// ============================================================================
// 节点拖拽
// ============================================================================
const dragState = ref<{ nodeKey: string; offsetX: number; offsetY: number } | null>(null)

function onNodeMouseDown(e: MouseEvent, n: NodeVO) {
  if ((e.target as HTMLElement).closest('.node-port, .node-actions')) return
  const pt = clientToSvg(e)
  dragState.value = {
    nodeKey: n.nodeKey,
    offsetX: pt.x - (n.positionX || 0),
    offsetY: pt.y - (n.positionY || 0),
  }
  selectedNodeKey.value = n.nodeKey
  selectedEdgeId.value = null
  syncNodeEditForm(n)
  document.addEventListener('mousemove', onDocMouseMove)
  document.addEventListener('mouseup', onDocMouseUp)
}

function onDocMouseMove(e: MouseEvent) {
  if (!dragState.value) return
  const n = nodes.value.find(nn => nn.nodeKey === dragState.value!.nodeKey)
  if (!n) return
  const pt = clientToSvg(e)
  n.positionX = Math.max(0, Math.round(pt.x - dragState.value.offsetX))
  n.positionY = Math.max(0, Math.round(pt.y - dragState.value.offsetY))
  dirty.value = true
}

function onDocMouseUp() {
  dragState.value = null
  document.removeEventListener('mousemove', onDocMouseMove)
  document.removeEventListener('mouseup', onDocMouseUp)
}

function onCanvasClick(e: MouseEvent) {
  if ((e.target as HTMLElement).closest('g.canvas-node, g.canvas-edge')) return
  selectedNodeKey.value = null
  selectedEdgeId.value = null
  pendingConnectSource.value = null
}

function onNodeClick(n: NodeVO) {
  selectedNodeKey.value = n.nodeKey
  selectedEdgeId.value = null
  syncNodeEditForm(n)
}

// ============================================================================
// 连线
// ============================================================================
const pendingConnectSource = ref<string | null>(null)

function startConnect(n: NodeVO) {
  if (pendingConnectSource.value === n.nodeKey) {
    pendingConnectSource.value = null
    return
  }
  pendingConnectSource.value = n.nodeKey
  ElMessage.info('请点击目标节点完成连线')
}

function completeConnect(target: NodeVO) {
  if (!pendingConnectSource.value) return
  if (pendingConnectSource.value === target.nodeKey) {
    pendingConnectSource.value = null
    return
  }
  const exists = edges.value.some(e =>
    e.sourceNodeKey === pendingConnectSource.value && e.targetNodeKey === target.nodeKey,
  )
  if (exists) { ElMessage.warning('连线已存在'); pendingConnectSource.value = null; return }
  edges.value.push({
    sourceNodeKey: pendingConnectSource.value,
    targetNodeKey: target.nodeKey,
    edgeKind: 'default_edge',
  })
  pendingConnectSource.value = null
  dirty.value = true
  ElMessage.success('已连线(请保存画布)')
}

function onEdgeClick(e: EdgeVO) {
  selectedEdgeId.value = e.id || null
  selectedNodeKey.value = null
  edgeEditForm.label = e.label || ''
  edgeEditForm.edgeKind = e.edgeKind || 'default_edge'
  edgeEditForm.conditionExpr = e.conditionExpr || ''
  edgeEditForm.priority = e.priority || 0
}

function deleteSelectedEdge() {
  if (!selectedEdgeId.value) return
  const idx = edges.value.findIndex(e => e.id === selectedEdgeId.value)
  if (idx >= 0) edges.value.splice(idx, 1)
  selectedEdgeId.value = null
  dirty.value = true
}

// ============================================================================
// 添加节点
// ============================================================================
function openAddNode(type: BpmNodeType) {
  newNodeForm.nodeType = type
  newNodeForm.nodeKey = `${type}_${Date.now().toString(36)}`
  newNodeForm.name = getNodeLabel(type)
  showAddNode.value = true
}

async function submitAddNode() {
  if (!newNodeForm.nodeKey) { ElMessage.warning('请输入节点 Key'); return }
  if (!currentVersionId.value) { ElMessage.warning('请先选择版本'); return }
  if (nodes.value.some(n => n.nodeKey === newNodeForm.nodeKey)) {
    ElMessage.warning('节点 Key 已存在')
    return
  }
  try {
    const x = 80 + Math.floor(Math.random() * 300)
    const y = 80 + Math.floor(Math.random() * 200)
    const body: NodeRequest = {
      nodeKey: newNodeForm.nodeKey,
      nodeType: newNodeForm.nodeType,
      name: newNodeForm.name || getNodeLabel(newNodeForm.nodeType),
      positionX: x,
      positionY: y,
      config: '{}',
      enabled: true,
    }
    const created = await bpm.createNode(flowDefId.value, currentVersionId.value, body)
    nodes.value.push(created)
    selectedNodeKey.value = created.nodeKey
    syncNodeEditForm(created)
    showAddNode.value = false
    dirty.value = true
    ElMessage.success('已添加(请保存画布)')
  } catch (e: any) { ElMessage.error(e?.message || '添加失败') }
}

async function deleteNode(n: NodeVO) {
  try {
    await ElMessageBox.confirm(`确定删除节点「${n.name || n.nodeKey}」?相关连线也会被级联删除`, '确认', { type: 'warning' })
    if (!currentVersionId.value) return
    await bpm.deleteNode(flowDefId.value, currentVersionId.value, n.nodeKey)
    nodes.value = nodes.value.filter(nn => nn.nodeKey !== n.nodeKey)
    edges.value = edges.value.filter(e => e.sourceNodeKey !== n.nodeKey && e.targetNodeKey !== n.nodeKey)
    if (selectedNodeKey.value === n.nodeKey) selectedNodeKey.value = null
    dirty.value = true
    ElMessage.success('已删除')
  } catch (e: any) { if (e !== 'cancel' && e?.message) ElMessage.error(e.message) }
}

// ============================================================================
// 节点编辑表单
// ============================================================================
function syncNodeEditForm(n: NodeVO) {
  nodeEditForm.name = n.name || ''
  let cfg: Record<string, any> = {}
  if (n.config) {
    try { cfg = typeof n.config === 'string' ? JSON.parse(n.config) : (n.config as any) } catch {}
  }
  nodeEditForm.config = cfg
  nodeEditForm.timeoutMs = n.timeoutMs || 60000
  nodeEditForm.retryCount = n.retryCount || 0
  nodeEditForm.retryIntervalMs = n.retryIntervalMs || 1000
  nodeEditForm.onFailure = (n.onFailure as any) || 'fail'
  nodeEditForm.failureBranchNodeKey = n.failureBranchNodeKey || ''
}

function getNodeSchema(type: string): NodeConfigField[] {
  return nodeTypeSchemas.value[type] || []
}

function saveNodeEdit() {
  if (!selectedNode.value) return
  const n = selectedNode.value
  n.name = nodeEditForm.name
  n.config = JSON.stringify(nodeEditForm.config)
  n.timeoutMs = nodeEditForm.timeoutMs
  n.retryCount = nodeEditForm.retryCount
  n.retryIntervalMs = nodeEditForm.retryIntervalMs
  n.onFailure = nodeEditForm.onFailure
  n.failureBranchNodeKey = nodeEditForm.failureBranchNodeKey
  dirty.value = true
  ElMessage.success('节点已修改(请保存画布)')
}

function saveEdgeEdit() {
  if (!selectedEdge.value) return
  const e = selectedEdge.value
  e.label = edgeEditForm.label
  e.edgeKind = edgeEditForm.edgeKind as any
  e.conditionExpr = edgeEditForm.conditionExpr
  e.priority = edgeEditForm.priority
  dirty.value = true
  ElMessage.success('连线已修改(请保存画布)')
}

// ============================================================================
// 保存 / 校验
// ============================================================================
async function saveCanvas() {
  if (!currentVersionId.value) return
  if (currentVersionStatus.value !== 'draft') {
    ElMessage.warning('只有草稿状态才能保存')
    return
  }
  saving.value = true
  try {
    const nodeReqs: NodeRequest[] = nodes.value.map(n => ({
      nodeKey: n.nodeKey,
      nodeType: n.nodeType,
      name: n.name,
      positionX: n.positionX,
      positionY: n.positionY,
      config: n.config || '{}',
      timeoutMs: n.timeoutMs,
      retryCount: n.retryCount,
      retryIntervalMs: n.retryIntervalMs,
      onFailure: n.onFailure,
      failureBranchNodeKey: n.failureBranchNodeKey,
      enabled: n.enabled,
    }))
    const edgeReqs: EdgeRequest[] = edges.value.map(e => ({
      sourceNodeKey: e.sourceNodeKey,
      targetNodeKey: e.targetNodeKey,
      edgeKind: e.edgeKind,
      conditionExpr: e.conditionExpr,
      conditionParams: e.conditionParams,
      label: e.label,
      priority: e.priority,
    }))
    const canvasData = JSON.stringify({ nodes: nodeReqs, edges: edgeReqs })
    const result = await bpm.saveCanvas(flowDefId.value, currentVersionId.value, {
      canvasData,
      nodes: nodeReqs,
      edges: edgeReqs,
    })
    validation.value = result
    dirty.value = false
    await loadCanvas(currentVersionId.value)
    ElMessage.success('画布已保存')
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally { saving.value = false }
}

async function runValidate() {
  if (!currentVersionId.value) return
  try {
    validation.value = await bpm.validateCanvas(flowDefId.value, currentVersionId.value)
    showValidationDetail.value = true
  } catch (e: any) { ElMessage.error(e?.message || '校验失败') }
}

// ============================================================================
// 触发流程(快速测试)
// ============================================================================
const showTrigger = ref(false)
const triggerForm = reactive({
  inputParams: '{"query":"你好"}',
  versionNo: undefined as number | undefined,
})

async function submitTrigger() {
  if (!flowDefId.value) return
  try {
    let inputs: any = {}
    try { inputs = JSON.parse(triggerForm.inputParams) } catch { ElMessage.warning('输入参数 JSON 格式错误'); return }
    const res = await bpm.triggerInstance({
      flowDefId: flowDefId.value,
      versionNo: triggerForm.versionNo,
      triggerType: 'manual',
      inputParams: inputs,
    })
    ElMessage.success(`已触发实例 ${res.instanceId}`)
    showTrigger.value = false
    router.push({ name: 'BpmInstance', query: { instanceId: res.instanceId } })
  } catch (e: any) { ElMessage.error(e?.message || '触发失败') }
}

// ============================================================================
// 创建草稿版本
// ============================================================================
async function createDraft() {
  try {
    await bpm.createDraftVersion(flowDefId.value, { fromPublished: false })
    await loadAll()
    // 重新加载后默认选中第一个 draft 版本
    const drafts = versionList.value.filter(v => v.status === 'draft')
    if (drafts.length) await loadCanvas(drafts[0].id)
    ElMessage.success('已创建草稿')
  } catch (e: any) { ElMessage.error(e?.message || '创建草稿失败') }
}

// ============================================================================
// 生命周期
// ============================================================================
const viewBox = computed(() => {
  const maxX = nodes.value.length ? Math.max(...nodes.value.map(n => (n.positionX || 0) + 200)) + 200 : 1600
  const maxY = nodes.value.length ? Math.max(...nodes.value.map(n => (n.positionY || 0) + 200)) + 200 : 900
  return `0 0 ${Math.max(1600, maxX)} ${Math.max(900, maxY)}`
})

watch(() => initialVersionId.value, (v) => { if (v) loadCanvas(v) }, { immediate: false })

onMounted(async () => {
  await loadAll()
  const initId = initialVersionId.value || flowDef.value?.currentVersionId
  if (initId) await loadCanvas(initId)
})

onBeforeUnmount(() => {
  document.removeEventListener('mousemove', onDocMouseMove)
  document.removeEventListener('mouseup', onDocMouseUp)
  window.removeEventListener('beforeunload', beforeUnloadHandler)
})

function beforeUnloadHandler(e: BeforeUnloadEvent) {
  if (dirty.value) { e.preventDefault(); e.returnValue = '' }
}
onMounted(() => window.addEventListener('beforeunload', beforeUnloadHandler))

// ============================================================================
// 节点分类(用于左侧工具箱)
const toolboxGroups = computed(() => {
  const groups: Record<string, NodeTypeVO[]> = {}
  for (const nt of nodeTypes.value) {
    const k = nt.category || '其他'
    if (!groups[k]) groups[k] = []
    groups[k].push(nt)
  }
  return groups
})
</script>

<template>
  <div class="canvas-page" v-loading="loading">
    <!-- ====== 顶部信息栏 ====== -->
    <div class="canvas-header">
      <div class="title">
        <el-button text size="small" @click="router.push({ name: 'BpmFlowList' })">📂 流程列表</el-button>
        <span class="name" v-if="flowDef">{{ flowDef.name }}</span>
        <el-tag v-if="currentVersionNo" :type="(VERSION_STATUS_TAG_TYPES as any)[currentVersionStatus] || 'info'" size="small">
          v{{ currentVersionNo }} · {{ VERSION_STATUS_LABELS[currentVersionStatus as keyof typeof VERSION_STATUS_LABELS] || currentVersionStatus }}
        </el-tag>
        <span v-if="dirty" class="dirty-tip">● 未保存</span>
      </div>
      <div class="actions">
        <el-select :model-value="currentVersionId" placeholder="切换版本" size="small" style="width:180px" @change="selectVersion">
          <el-option v-for="v in versionList" :key="v.id" :label="`v${v.versionNo} · ${VERSION_STATUS_LABELS[v.status as keyof typeof VERSION_STATUS_LABELS] || v.status}`" :value="v.id" />
        </el-select>
        <el-button size="small" @click="createDraft">＋ 新草稿</el-button>
        <el-button size="small" @click="runValidate">✓ 校验</el-button>
        <el-button size="small" type="primary" :loading="saving" @click="saveCanvas" :disabled="currentVersionStatus !== 'draft'">💾 保存画布</el-button>
        <el-button size="small" type="success" @click="showTrigger = true">▶ 触发测试</el-button>
      </div>
    </div>

    <!-- ====== 主体布局 ====== -->
    <div class="canvas-main">
      <!-- 左侧工具箱 -->
      <div class="toolbox">
        <div class="toolbox-title">节点类型</div>
        <div v-for="(group, key) in toolboxGroups" :key="key" class="toolbox-group">
          <div class="group-title">{{ key }}</div>
          <div v-for="nt in group" :key="nt.type" class="toolbox-item" @click="openAddNode(nt.type as BpmNodeType)">
            <span class="icon" :style="{ background: nt.color || getNodeColor(nt.type) }">
              {{ nt.icon || getNodeIcon(nt.type) }}
            </span>
            <div class="meta">
              <div class="label">{{ nt.label || getNodeLabel(nt.type) }}</div>
              <div class="desc">{{ nt.description || '' }}</div>
            </div>
          </div>
        </div>
      </div>

      <!-- 中央画布 -->
      <div class="canvas-container">
        <svg ref="svgRef" class="canvas-svg" :viewBox="viewBox" preserveAspectRatio="xMinYMin meet" @click="onCanvasClick">
          <!-- 边 -->
          <g v-for="(edge, idx) in edges" :key="edge.id || `e_${idx}`" class="canvas-edge" :class="{ 'is-selected': selectedEdgeId === edge.id }" @click.stop="onEdgeClick(edge)">
            <path :d="getEdgePath(edge)" fill="none" :stroke="selectedEdgeId === edge.id ? '#F56C6C' : (EDGE_KIND_COLOR[edge.edgeKind] || '#409EFF')" :stroke-width="selectedEdgeId === edge.id ? 3 : 2" />
            <polygon :points="getArrowPoints(edge)" :fill="selectedEdgeId === edge.id ? '#F56C6C' : (EDGE_KIND_COLOR[edge.edgeKind] || '#409EFF')" />
            <text v-if="edge.label" :x="getEdgeLabelPos(edge).x" :y="getEdgeLabelPos(edge).y" text-anchor="middle" fill="#333" font-size="12">
              {{ edge.label }}
            </text>
          </g>

          <!-- 节点 -->
          <g v-for="n in nodes" :key="n.nodeKey" class="canvas-node" :class="{
            'is-selected': selectedNodeKey === n.nodeKey,
            'is-connecting': pendingConnectSource === n.nodeKey,
            'is-connect-target': pendingConnectSource && pendingConnectSource !== n.nodeKey,
          }" @mousedown.stop="onNodeMouseDown($event, n)" @click.stop="onNodeClick(n)">
            <rect :x="n.positionX" :y="n.positionY" :width="NODE_W" :height="NODE_H" :rx="6" ry="6" fill="#fff" :stroke="getNodeColor(n.nodeType)" stroke-width="2" />
            <rect :x="n.positionX" :y="n.positionY" width="6" :height="NODE_H" :fill="getNodeColor(n.nodeType)" />
            <text :x="(n.positionX || 0) + 14" :y="(n.positionY || 0) + 22" font-size="14">{{ getNodeIcon(n.nodeType) }}</text>
            <text :x="(n.positionX || 0) + 36" :y="(n.positionY || 0) + 22" font-size="13" font-weight="600" fill="#333">{{ n.name || getNodeLabel(n.nodeType) }}</text>
            <text :x="(n.positionX || 0) + 14" :y="(n.positionY || 0) + 42" font-size="11" fill="#909399">{{ getNodeLabel(n.nodeType) }}</text>
            <text :x="(n.positionX || 0) + 14" :y="(n.positionY || 0) + 56" font-size="10" fill="#bbb">{{ n.nodeKey }}</text>

            <circle class="node-port port-out" :cx="(n.positionX || 0) + NODE_W" :cy="(n.positionY || 0) + NODE_H / 2" r="5" fill="#67C23A" @click.stop="completeConnect(n)" />
            <circle class="node-port port-in" :cx="(n.positionX || 0)" :cy="(n.positionY || 0) + NODE_H / 2" r="5" fill="#909399" />

            <g class="node-actions" v-if="selectedNodeKey === n.nodeKey" :transform="`translate(${(n.positionX || 0) + NODE_W - 90}, ${(n.positionY || 0) - 16})`">
              <rect width="80" height="14" rx="3" fill="#f0f9ff" stroke="#409EFF" />
              <text x="6" y="10" font-size="9" fill="#409EFF" @click.stop="startConnect(n)">{{ pendingConnectSource === n.nodeKey ? '取消' : '连线' }}</text>
              <text x="44" y="10" font-size="9" fill="#F56C6C" @click.stop="deleteNode(n)">删除</text>
            </g>
          </g>
        </svg>
      </div>

      <!-- 右侧属性面板 -->
      <div class="props-panel">
        <template v-if="selectedNode">
          <div class="props-header">
            <span>📐 节点属性</span>
            <el-button text size="small" @click="selectedNodeKey = null">✕</el-button>
          </div>
          <div class="props-body">
            <div class="props-row">
              <label>Key</label>
              <el-tag size="small">{{ selectedNode.nodeKey }}</el-tag>
            </div>
            <div class="props-row">
              <label>类型</label>
              <el-tag size="small" :style="{ background: getNodeColor(selectedNode.nodeType), color: '#fff', border: 'none' }">{{ getNodeLabel(selectedNode.nodeType) }}</el-tag>
            </div>
            <div class="form-row">
              <label>名称</label>
              <el-input v-model="nodeEditForm.name" size="small" />
            </div>
            <el-divider>节点配置</el-divider>
            <template v-for="field in getNodeSchema(selectedNode.nodeType)" :key="field.key">
              <div class="form-row">
                <label>{{ field.label }}</label>
                <el-input v-if="field.type === 'input'" v-model="nodeEditForm.config[field.key]" size="small" :placeholder="String(field.defaultValue || '')" />
                <el-input-number v-else-if="field.type === 'number'" v-model="nodeEditForm.config[field.key]" size="small" :min="field.validation?.min || 0" :max="field.validation?.max || 999999" :step="1" />
                <el-select v-else-if="field.type === 'select'" v-model="nodeEditForm.config[field.key]" size="small" style="width:100%">
                  <el-option v-for="o in (field.options || [])" :key="String(o.value)" :label="o.label" :value="o.value" />
                </el-select>
                <el-input v-else-if="field.type === 'textarea'" v-model="nodeEditForm.config[field.key]" type="textarea" :rows="3" size="small" />
                <el-switch v-else-if="field.type === 'switch'" v-model="nodeEditForm.config[field.key]" />
                <el-input v-else-if="field.type === 'json'" v-model="nodeEditForm.config[field.key as any]" type="textarea" :rows="4" size="small" placeholder='{"key":"value"}' />
                <el-input v-else v-model="nodeEditForm.config[field.key]" size="small" />
              </div>
            </template>
            <el-divider>高级</el-divider>
            <div class="form-row"><label>超时(ms)</label><el-input-number v-model="nodeEditForm.timeoutMs" :min="0" size="small" style="width:100%" /></div>
            <div class="form-row"><label>重试次数</label><el-input-number v-model="nodeEditForm.retryCount" :min="0" size="small" style="width:100%" /></div>
            <div class="form-row"><label>重试间隔(ms)</label><el-input-number v-model="nodeEditForm.retryIntervalMs" :min="0" size="small" style="width:100%" /></div>
            <div class="form-row">
              <label>失败策略</label>
              <el-select v-model="nodeEditForm.onFailure" size="small" style="width:100%">
                <el-option label="失败终止" value="fail" />
                <el-option label="忽略继续" value="ignore" />
                <el-option label="跳转分支" value="branch" />
              </el-select>
            </div>
            <div class="form-row" v-if="nodeEditForm.onFailure === 'branch'">
              <label>失败分支</label>
              <el-select v-model="nodeEditForm.failureBranchNodeKey" size="small" style="width:100%">
                <el-option v-for="n in nodes" :key="n.nodeKey" :label="`${n.name || n.nodeKey} (${n.nodeKey})`" :value="n.nodeKey" />
              </el-select>
            </div>
            <el-button type="primary" size="small" style="margin-top:8px" @click="saveNodeEdit">应用(需保存画布)</el-button>
          </div>
        </template>

        <template v-else-if="selectedEdge">
          <div class="props-header">
            <span>↔ 连线属性</span>
            <el-button text size="small" @click="selectedEdgeId = null">✕</el-button>
          </div>
          <div class="props-body">
            <div class="props-row"><label>源</label><el-tag size="small">{{ selectedEdge.sourceNodeKey }}</el-tag></div>
            <div class="props-row"><label>目标</label><el-tag size="small">{{ selectedEdge.targetNodeKey }}</el-tag></div>
            <div class="form-row">
              <label>标签</label>
              <el-input v-model="edgeEditForm.label" size="small" placeholder="如:通过、不通过" />
            </div>
            <div class="form-row">
              <label>连线类型</label>
              <el-select v-model="edgeEditForm.edgeKind" size="small" style="width:100%">
                <el-option v-for="(v, k) in EDGE_KIND_LABEL_MAP" :key="k" :label="v" :value="k" />
              </el-select>
            </div>
            <div class="form-row">
              <label>条件表达式</label>
              <el-input v-model="edgeEditForm.conditionExpr" type="textarea" :rows="3" size="small" placeholder="SpEL 表达式,如:#input.score > 80" />
            </div>
            <div class="form-row"><label>优先级</label><el-input-number v-model="edgeEditForm.priority" size="small" :min="0" style="width:100%" /></div>
            <div class="form-actions">
              <el-button type="primary" size="small" @click="saveEdgeEdit">应用</el-button>
              <el-button type="danger" size="small" @click="deleteSelectedEdge">删除连线</el-button>
            </div>
          </div>
        </template>

        <template v-else>
          <div class="empty-tip">
            <div style="font-size:48px;text-align:center;color:#dcdfe6">📐</div>
            <p style="text-align:center;color:#909399">从左侧节点工具箱添加节点<br>点击节点编辑属性</p>
          </div>
        </template>
      </div>
    </div>

    <!-- ====== 添加节点对话框 ====== -->
    <el-dialog v-model="showAddNode" title="添加节点" width="480px">
      <el-form label-width="90px">
        <el-form-item label="节点类型">
          <el-select v-model="newNodeForm.nodeType" style="width:200px">
            <el-option v-for="nt in nodeTypes" :key="nt.type" :label="nt.label" :value="nt.type" />
          </el-select>
        </el-form-item>
        <el-form-item label="节点 Key" required>
          <el-input v-model="newNodeForm.nodeKey" />
        </el-form-item>
        <el-form-item label="节点名称">
          <el-input v-model="newNodeForm.name" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddNode = false">取消</el-button>
        <el-button type="primary" @click="submitAddNode">添加</el-button>
      </template>
    </el-dialog>

    <!-- ====== 校验结果对话框 ====== -->
    <el-dialog v-model="showValidationDetail" title="画布校验结果" width="540px">
      <div v-if="validation">
        <el-alert v-if="validation.valid" type="success" :closable="false" show-icon>
          画布结构有效 · {{ validation.nodeCount }} 节点 · {{ validation.edgeCount }} 连线
        </el-alert>
        <el-alert v-else type="error" :closable="false" show-icon>
          画布存在问题:
          <ul style="margin: 4px 0 0 16px">
            <li v-for="(err, i) in validation.errors" :key="i">{{ err }}</li>
          </ul>
        </el-alert>
        <template v-if="validation.warnings?.length">
          <el-divider>警告</el-divider>
          <ul style="color:#E6A23C;margin:0">
            <li v-for="(w, i) in validation.warnings" :key="i">{{ w }}</li>
          </ul>
        </template>
      </div>
      <template #footer>
        <el-button @click="showValidationDetail = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- ====== 触发测试对话框 ====== -->
    <el-dialog v-model="showTrigger" title="触发流程测试" width="540px">
      <el-form label-width="100px">
        <el-form-item label="版本号">
          <el-input-number v-model="triggerForm.versionNo" :min="1" size="small" placeholder="不填则用当前已发布" />
        </el-form-item>
        <el-form-item label="输入参数">
          <el-input v-model="triggerForm.inputParams" type="textarea" :rows="6" placeholder='{"query":"你好"}' />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showTrigger = false">取消</el-button>
        <el-button type="primary" @click="submitTrigger">触发</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.canvas-page {
  display: flex; flex-direction: column; height: 100%; padding: $spacing-base;
}

.canvas-header {
  display: flex; align-items: center; justify-content: space-between;
  padding-bottom: 12px; flex-wrap: wrap; gap: 8px;
  border-bottom: 1px solid #ebeef5; margin-bottom: 12px;
}
.canvas-header .title { display: flex; align-items: center; gap: 8px; }
.canvas-header .name { font-weight: 600; font-size: 15px; }
.canvas-header .actions { display: flex; gap: 8px; align-items: center; }
.dirty-tip { color: #E6A23C; font-size: 12px; }

.canvas-main {
  display: flex; gap: 12px; flex: 1; min-height: 0;
}

.toolbox {
  width: 220px; flex-shrink: 0; background: #fff; border: 1px solid #ebeef5;
  border-radius: 8px; padding: 12px; overflow-y: auto; max-height: calc(100vh - 200px);
}
.toolbox-title { font-size: 14px; font-weight: 600; margin-bottom: 12px; }
.toolbox-group { margin-bottom: 12px; }
.group-title { font-size: 11px; color: #909399; margin-bottom: 4px; }
.toolbox-item {
  display: flex; gap: 8px; align-items: flex-start; padding: 6px 8px; border-radius: 6px;
  cursor: pointer; transition: all 0.15s;
  &:hover { background: #f0f5ff; }
}
.toolbox-item .icon {
  width: 24px; height: 24px; border-radius: 4px; display: flex; align-items: center; justify-content: center;
  color: #fff; font-size: 12px; flex-shrink: 0;
}
.toolbox-item .meta { flex: 1; min-width: 0; }
.toolbox-item .label { font-size: 12px; font-weight: 500; }
.toolbox-item .desc { font-size: 10px; color: #909399; line-height: 1.3; margin-top: 2px; }

.canvas-container {
  flex: 1; background: #fafafa; border: 1px solid #ebeef5; border-radius: 8px;
  overflow: auto; min-width: 0;
}
.canvas-svg {
  display: block; width: 100%; min-height: 600px;
  background-image:
    linear-gradient(0deg, transparent 24%, rgba(0,0,0,.04) 25%, rgba(0,0,0,.04) 26%, transparent 27%, transparent 74%, rgba(0,0,0,.04) 75%, rgba(0,0,0,.04) 76%, transparent 77%, transparent),
    linear-gradient(90deg, transparent 24%, rgba(0,0,0,.04) 25%, rgba(0,0,0,.04) 26%, transparent 27%, transparent 74%, rgba(0,0,0,.04) 75%, rgba(0,0,0,.04) 76%, transparent 77%, transparent);
  background-size: 40px 40px;
}
.canvas-node { cursor: move; transition: filter 0.15s; }
.canvas-node.is-selected { filter: drop-shadow(0 0 4px rgba(64,158,255,0.5)); }
.canvas-node.is-connecting { filter: drop-shadow(0 0 6px rgba(230,162,60,0.6)); }
.canvas-node.is-connect-target { cursor: pointer; }
.canvas-edge { cursor: pointer; }
.canvas-edge:hover path { stroke-width: 3; }
.node-port { cursor: pointer; }

.props-panel {
  width: 320px; flex-shrink: 0; background: #fff; border: 1px solid #ebeef5;
  border-radius: 8px; padding: 12px; overflow-y: auto; max-height: calc(100vh - 200px);
}
.props-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; font-weight: 600; font-size: 14px; }
.props-body { display: flex; flex-direction: column; gap: 6px; }
.props-row { display: flex; gap: 8px; font-size: 13px; align-items: center; }
.props-row label { color: #909399; min-width: 40px; flex-shrink: 0; }
.form-row { display: flex; flex-direction: column; gap: 4px; margin-bottom: 6px; }
.form-row label { font-size: 12px; color: #606266; }
.form-actions { display: flex; gap: 8px; margin-top: 12px; }
.empty-tip { padding: 60px 12px; }
</style>