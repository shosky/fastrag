<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const loading = ref(false)
const showWorkflowList = ref(false)

// ============================================================================
// 业务流列表（弹窗形式）
// ============================================================================
const workflowList = ref<any[]>([])
const showCreateDialog = ref(false)
const createForm = ref({ name: '', description: '', category: '问答' })

async function loadWorkflows() {
  try {
    const res: any = await api.getWorkflows()
    workflowList.value = Array.isArray(res) ? res : []
  } catch { workflowList.value = [] }
}

function handleCreate() {
  showCreateDialog.value = true
  createForm.value = { name: '', description: '', category: '问答' }
}

async function handleCreateWorkflow() {
  if (!createForm.value.name) { ElMessage.warning('请输入业务流名称'); return }
  try {
    await api.createWorkflow(createForm.value)
    await loadWorkflows()
    ElMessage.success('业务流已创建')
    showCreateDialog.value = false
  } catch (e: any) { ElMessage.error(e?.message || '创建失败') }
}

function handleSelectWorkflow(row: any) {
  wfId.value = row.id
  wfName.value = row.name
  showWorkflowList.value = false
  loadCanvas()
}

async function handleDeleteWorkflow(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除业务流「${row.name}」？`, '确认', { type: 'warning' })
    await api.deleteWorkflow(row.id)
    await loadWorkflows()
    ElMessage.success('已删除')
  } catch {}
}

// ============================================================================
// Tab 2: 工作流画布 — 自由编排
// ============================================================================
const wfId = ref('')
const wfName = ref('')
const nodes = ref<any[]>([])
const edges = ref<Array<{ source: string; target: string }>>([])
const selectedNode = ref<any>(null)
const connectingFrom = ref<string | null>(null)

// 从后端加载画布数据
async function loadCanvas() {
  if (!wfId.value) return
  loading.value = true
  try {
    // 加载节点
    const nodeRes: any = await api.getWorkflowNodes(wfId.value)
    nodes.value = Array.isArray(nodeRes) ? nodeRes : []
    // 加载连线 (edges 存储在 workflow 的 edges 字段)
    const wfDetail: any = await api.getWorkflowDetail(wfId.value)
    if (wfDetail?.edges) {
      try { edges.value = JSON.parse(typeof wfDetail.edges === 'string' ? wfDetail.edges : '[]') } catch { edges.value = [] }
    } else { edges.value = [] }
  } catch {
    nodes.value = []
    edges.value = []
  } finally { loading.value = false }
}

// 保存画布到后端
async function saveCanvas() {
  if (!wfId.value) return
  try {
    // 保存节点位置和配置（逐个更新）
    for (const node of nodes.value) {
      try {
        await api.updateWorkflowNode(wfId.value, node.nodeKey, {
          nodeKey: node.nodeKey, type: node.nodeType, name: node.name,
          x: node.positionX, y: node.positionY, config: node.config || {},
        })
      } catch {}
    }
    // 保存连线关系 (存入 workflow.edges)
    await api.updateWorkflow(wfId.value, { edges: JSON.stringify(edges.value) })
    ElMessage.success('画布已保存')
  } catch (e: any) { ElMessage.error(e?.message || '保存失败') }
}

// 移除对 mainTab 的引用
// 回业务流列表 — 改为打开选择弹窗
// 已由 showWorkflowList 代替

// ===== 节点操作 =====
const NODE_TYPES = [
  { value: 'start', label: '开始', color: '#67C23A', icon: '▶' },
  { value: 'end', label: '结束', color: '#F56C6C', icon: '⏹' },
  { value: 'llm', label: '大模型', color: '#409EFF', icon: '🤖' },
  { value: 'kb_retrieval', label: '知识库检索', color: '#E6A23C', icon: '📚' },
  { value: 'intent', label: '意图识别', color: '#9B59B6', icon: '🎯' },
  { value: 'selector', label: '选择器', color: '#1ABC9C', icon: '🔀' },
  { value: 'function_request', label: '功能请求', color: '#FF6B6B', icon: '⚡' },
  { value: 'sub_workflow', label: '子工作流', color: '#00B4D8', icon: '🔗' },
]
const nodeTypeMap = Object.fromEntries(NODE_TYPES.map(t => [t.value, t]))

const showNodeDialog = ref(false)
const editingNode = ref<any>(null)
const nodeForm = ref({ nodeKey: '', nodeType: 'llm', name: '' })
const nodeConfig = ref<any>({})

const nodeTypeConfigs: Record<string, any> = {
  llm: { fields: [
    { key: 'model', label: '模型', type: 'input' },
    { key: 'temperature', label: '温度', type: 'slider', min: 0, max: 2, step: 0.1 },
    { key: 'maxTokens', label: '最大Token', type: 'number', min: 128, max: 8192 },
    { key: 'systemPrompt', label: '系统提示词', type: 'textarea' },
    { key: 'outputFormat', label: '输出格式', type: 'select', options: [{ label: '文本', value: 'text' }, { label: 'JSON', value: 'json' }, { label: 'Markdown', value: 'markdown' }] },
  ]},
  kb_retrieval: { fields: [
    { key: 'kbId', label: '知识库ID', type: 'input' },
    { key: 'topK', label: 'Top K', type: 'number', min: 1, max: 50 },
    { key: 'similarityThreshold', label: '相似度阈值', type: 'slider', min: 0, max: 1, step: 0.05 },
    { key: 'mode', label: '检索策略', type: 'select', options: [{ label: '向量', value: 'vector' }, { label: '全文', value: 'fulltext' }, { label: '混合', value: 'hybrid' }] },
  ]},
  intent: { fields: [
    { key: 'model', label: '模型', type: 'input' },
    { key: 'maxLabels', label: '最大分类数', type: 'number', min: 1, max: 50 },
    { key: 'confidenceThreshold', label: '置信度阈值', type: 'slider', min: 0, max: 1, step: 0.05 },
  ]},
  selector: { fields: [
    { key: 'condition', label: '条件表达式', type: 'input' },
    { key: 'defaultBranch', label: '默认分支', type: 'input' },
  ]},
  start: { fields: [
    { key: 'inputParams', label: '输入参数(JSON)', type: 'textarea' },
  ]},
  end: { fields: [
    { key: 'outputFormat', label: '返回方式', type: 'select', options: [{ label: '文本', value: 'text' }, { label: 'JSON', value: 'json' }, { label: '流式', value: 'stream' }] },
  ]},
  function_request: { fields: [
    { key: 'functionName', label: '函数名称', type: 'input' },
    { key: 'requestUrl', label: '请求URL', type: 'input' },
    { key: 'requestMethod', label: '请求方法', type: 'select', options: [{ label: 'GET', value: 'GET' }, { label: 'POST', value: 'POST' }, { label: 'PUT', value: 'PUT' }, { label: 'DELETE', value: 'DELETE' }] },
  ]},
  sub_workflow: { fields: [
    { key: 'workflowId', label: '子工作流ID', type: 'input' },
    { key: 'inputMapping', label: '输入映射(JSON)', type: 'textarea' },
  ]},
}

function handleAddNode() {
  editingNode.value = null
  // 找空白位置
  const usedPositions = nodes.value.map(n => ({ x: n.positionX, y: n.positionY }))
  let x = 100, y = 100
  while (usedPositions.some(p => Math.abs(p.x - x) < 80 && Math.abs(p.y - y) < 80)) { x += 120; if (x > 800) { x = 100; y += 100 } }
  nodeForm.value = { nodeKey: 'node_' + Date.now(), nodeType: 'llm', name: '' }
  nodeConfig.value = {}
  selectedNode.value = { nodeKey: nodeForm.value.nodeKey, nodeType: 'llm', name: '', positionX: x, positionY: y, config: {} }
  showNodeDialog.value = true
}

function handleEditNode(node: any) {
  editingNode.value = node
  nodeForm.value = { nodeKey: node.nodeKey, nodeType: node.nodeType, name: node.name || '' }
  nodeConfig.value = node.config ? (typeof node.config === 'string' ? JSON.parse(node.config) : { ...node.config }) : {}
  showNodeDialog.value = true
}

function handleSaveNode() {
  if (!nodeForm.value.nodeKey) { ElMessage.warning('请输入节点Key'); return }
  const x = editingNode.value?.positionX || (selectedNode.value?.positionX || 100)
  const y = editingNode.value?.positionY || (selectedNode.value?.positionY || 100)
  const nodeData = {
    nodeKey: nodeForm.value.nodeKey,
    nodeType: nodeForm.value.nodeType,
    name: nodeForm.value.name || nodeTypeMap[nodeForm.value.nodeType]?.label || nodeForm.value.nodeType,
    positionX: x,
    positionY: y,
    enabled: 1,
    config: nodeConfig.value,
  }
  if (editingNode.value) {
    Object.assign(editingNode.value, nodeData)
  } else {
    nodes.value.push(nodeData)
  }
  showNodeDialog.value = false
  selectedNode.value = null
  ElMessage.success(editingNode.value ? '节点已更新' : `已添加「${nodeData.name}」节点`)
}

function handleDeleteNode(node: any) {
  if (!node) return
  nodes.value = nodes.value.filter(n => n.nodeKey !== node.nodeKey)
  edges.value = edges.value.filter(e => e.source !== node.nodeKey && e.target !== node.nodeKey)
  if (selectedNode.value?.nodeKey === node.nodeKey) selectedNode.value = null
  ElMessage.success(`已删除「${node.name}」`)
}

function handleNodeClick(node: any) {
  selectedNode.value = node
}

function handleCanvasClick() {
  selectedNode.value = null
  connectingFrom.value = null
}

// ===== 自由连线 =====
function handleStartConnect(sourceKey: string) {
  connectingFrom.value = sourceKey
  ElMessage.info(`请点击目标节点完成连线，点击空白处取消`)
}

function handleEndConnect(targetKey: string) {
  if (!connectingFrom.value || connectingFrom.value === targetKey) {
    connectingFrom.value = null
    return
  }
  // 检查是否已存在
  const exists = edges.value.some(e => e.source === connectingFrom.value && e.target === targetKey)
  if (exists) { ElMessage.warning('这两个节点已连接'); connectingFrom.value = null; return }
  edges.value.push({ source: connectingFrom.value, target: targetKey })
  ElMessage.success(`已连线`)
  connectingFrom.value = null
}

function handleDeleteEdge(edge: { source: string; target: string }) {
  edges.value = edges.value.filter(e => e.source !== edge.source || e.target !== edge.target)
}

// 根据节点位置计算连线路径
function getEdgePath(edge: { source: string; target: string }) {
  const src = nodes.value.find(n => n.nodeKey === edge.source)
  const tgt = nodes.value.find(n => n.nodeKey === edge.target)
  if (!src || !tgt) return ''
  const x1 = src.positionX + 75, y1 = src.positionY + 25
  const x2 = tgt.positionX + 75, y2 = tgt.positionY
  // 贝塞尔曲线
  const cy = Math.abs(y2 - y1) / 2 + 20
  return `M ${x1} ${y1} C ${x1} ${y1 + cy}, ${x2} ${y2 - cy}, ${x2} ${y2}`
}

// ===== 拖拽 =====
const draggingNode = ref<any>(null)
const dragOffset = ref({ x: 0, y: 0 })

function handleMouseDown(e: MouseEvent, node: any) {
  if ((e.target as HTMLElement)?.closest('.node-actions')) return
  draggingNode.value = node
  dragOffset.value = { x: e.clientX - node.positionX, y: e.clientY - node.positionY }
  document.addEventListener('mousemove', handleMouseMove)
  document.addEventListener('mouseup', handleMouseUp)
}
function handleMouseMove(e: MouseEvent) {
  if (!draggingNode.value) return
  draggingNode.value.positionX = Math.max(0, e.clientX - dragOffset.value.x)
  draggingNode.value.positionY = Math.max(0, e.clientY - dragOffset.value.y)
}
function handleMouseUp() {
  if (draggingNode.value) {
    api.moveWorkflowNode(wfId.value, draggingNode.value.nodeKey, {
      x: draggingNode.value.positionX, y: draggingNode.value.positionY,
    }).catch(() => {})
  }
  draggingNode.value = null
  document.removeEventListener('mousemove', handleMouseMove)
  document.removeEventListener('mouseup', handleMouseUp)
}

// ===== 节点工具箱 =====
const showToolbox = ref(false)
const nodeTypeFilter = ref('')
const filteredTypes = computed(() => {
  return nodeTypeFilter.value
    ? NODE_TYPES.filter(t => t.label.includes(nodeTypeFilter.value))
    : NODE_TYPES
})

onMounted(() => { loadWorkflows() })
</script>

<template>
  <div class="page-container" v-loading="loading">
    <!-- 画布头部 -->
    <div class="canvas-header">
      <div class="canvas-title">
        <el-button text @click="showWorkflowList = true; loadWorkflows()" size="small">📂 切换业务流</el-button>
        <span style="margin-left:8px;font-weight:600" v-if="wfId">{{ wfName }}</span>
        <span v-else style="margin-left:8px;color:#909399">请选择或创建一个业务流</span>
        <el-tag v-if="wfId" size="small" style="margin-left:8px" type="info">{{ nodes.length }} 个节点 · {{ edges.length }} 条连线</el-tag>
      </div>
      <div class="canvas-actions" v-if="wfId">
        <el-button size="small" @click="showToolbox = !showToolbox">📦 节点工具箱</el-button>
        <el-button size="small" @click="handleAddNode">＋ 添加节点</el-button>
        <el-button size="small" type="warning" @click="saveCanvas">💾 保存画布</el-button>
      </div>
    </div>

    <!-- 无业务流时的引导 -->
    <div v-if="!wfId" class="welcome-panel">
      <el-empty description="请先选择或创建一个业务流" :image-size="80">
        <el-button type="primary" @click="showWorkflowList = true; loadWorkflows()">选择业务流</el-button>
        <el-button style="margin-left:8px" @click="handleCreate">创建新业务流</el-button>
      </el-empty>
    </div>

    <!-- 画布区域 -->
    <template v-if="wfId">
      <div class="canvas-layout">
        <div v-if="showToolbox" class="toolbox-panel">
          <div class="toolbox-header">
            <span>节点类型</span>
            <el-input v-model="nodeTypeFilter" size="small" placeholder="搜索..." clearable style="width:120px" />
          </div>
          <div v-for="t in filteredTypes" :key="t.value" class="toolbox-item" @click="handleAddNode(); nodeForm.nodeType = t.value; showToolbox = false">
            <span class="toolbox-icon">{{ t.icon }}</span><span>{{ t.label }}</span>
          </div>
        </div>
        <div class="canvas-area" @click="handleCanvasClick">
          <svg class="canvas-svg">
            <g v-for="(edge, i) in edges" :key="'e'+i">
              <path :d="getEdgePath(edge)" fill="none" stroke="#409EFF" stroke-width="2" class="edge-path" />
              <title>{{ edge.source }} → {{ edge.target }}</title>
            </g>
          </svg>
          <div v-for="node in nodes" :key="node.nodeKey"
            class="canvas-node"
            :class="{
              'is-selected': selectedNode?.nodeKey === node.nodeKey,
              'is-connecting': connectingFrom === node.nodeKey,
              'is-connect-target': connectingFrom && connectingFrom !== node.nodeKey
            }"
            :style="{ left: node.positionX + 'px', top: node.positionY + 'px' }"
            @mousedown.stop="(e) => handleMouseDown(e, node)"
            @click.stop="handleNodeClick(node)">
            <div class="node-header" :style="{ borderLeft: '4px solid ' + (nodeTypeMap[node.nodeType]?.color || '#909399') }">
              <span class="node-icon">{{ nodeTypeMap[node.nodeType]?.icon || '⬡' }}</span>
              <span class="node-type-tag" :style="{ background: nodeTypeMap[node.nodeType]?.color || '#909399' }">{{ nodeTypeMap[node.nodeType]?.label || node.nodeType }}</span>
            </div>
            <div class="node-name">{{ node.name || node.nodeKey }}</div>
            <div class="node-key">{{ node.nodeKey }}</div>
            <div class="node-actions">
              <el-button link size="small" @click.stop="handleEditNode(node)">编辑</el-button>
              <el-button v-if="!connectingFrom" link size="small" type="primary" @click.stop="handleStartConnect(node.nodeKey)">连线</el-button>
              <el-button v-else-if="connectingFrom !== node.nodeKey" link size="small" type="success" @click.stop="handleEndConnect(node.nodeKey)">连接</el-button>
              <el-button link size="small" type="danger" @click.stop="handleDeleteNode(node)">删除</el-button>
            </div>
          </div>
          <div v-if="!nodes.length" class="canvas-empty">
            <el-empty description="画布为空，点击上方「添加节点」或从工具箱添加" :image-size="80" />
          </div>
        </div>
      </div>
      <div v-if="selectedNode" class="node-props-panel">
        <div class="props-header">
          <span>📐 {{ selectedNode.name || selectedNode.nodeKey }}</span>
          <el-button text size="small" @click="selectedNode = null">✕</el-button>
        </div>
        <div class="props-body">
          <div class="props-row"><label>Key</label><span>{{ selectedNode.nodeKey }}</span></div>
          <div class="props-row"><label>类型</label><span>{{ nodeTypeMap[selectedNode.nodeType]?.label || selectedNode.nodeType }}</span></div>
          <div class="props-row"><label>位置</label><span>({{ selectedNode.positionX }}, {{ selectedNode.positionY }})</span></div>
          <div class="props-row" v-if="selectedNode.config && Object.keys(selectedNode.config).length">
            <label>配置</label><pre>{{ JSON.stringify(selectedNode.config, null, 2) }}</pre>
          </div>
          <div class="props-row"><label>入边</label><span>{{ edges.filter(e => e.target === selectedNode.nodeKey).map(e => e.source).join(', ') || '无' }}</span></div>
          <div class="props-row"><label>出边</label>
            <span v-if="edges.filter(e => e.source === selectedNode.nodeKey).length">
              <template v-for="(e, i) in edges.filter(e => e.source === selectedNode.nodeKey)" :key="i">
                {{ e.target }}<el-button link size="small" type="danger" @click="handleDeleteEdge(e)" style="margin-left:2px">✕</el-button>
                <span v-if="i < edges.filter(ee => ee.source === selectedNode.nodeKey).length - 1">, </span>
              </template>
            </span>
            <span v-else>无</span>
          </div>
        </div>
      </div>
    </template>

    <!-- 业务流选择弹窗 -->
    <el-dialog v-model="showWorkflowList" title="选择业务流" width="700px">
      <div class="section-header" style="margin-bottom:12px">
        <span style="font-size:14px;color:#909399">共 {{ workflowList.length }} 个业务流</span>
        <el-button size="small" type="primary" @click="handleCreate(); showWorkflowList = false">创建新业务流</el-button>
      </div>
      <el-table :data="workflowList" stripe @row-dblclick="handleSelectWorkflow" max-height="400">
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column prop="description" label="描述" show-overflow-tooltip min-width="200" />
        <el-table-column prop="category" label="分类" width="80" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'published' ? 'success' : 'info'" size="small">{{ row.status === 'published' ? '已发布' : '草稿' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleSelectWorkflow(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDeleteWorkflow(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!workflowList.length" description="暂无业务流" :image-size="50" />
    </el-dialog>

    <!-- 创建业务流弹窗 -->
    <el-dialog v-model="showCreateDialog" title="创建业务流" width="480px">
      <el-form label-width="90px">
        <el-form-item label="名称" required><el-input v-model="createForm.name" placeholder="如：智能问答工作流" /></el-form-item>
        <el-form-item label="分类"><el-select v-model="createForm.category" style="width:160px"><el-option label="问答" value="问答" /><el-option label="对话" value="对话" /><el-option label="数据处理" value="数据处理" /></el-select></el-form-item>
        <el-form-item label="描述"><el-input v-model="createForm.description" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showCreateDialog=false">取消</el-button><el-button type="primary" @click="handleCreateWorkflow">创建</el-button></template>
    </el-dialog>

    <!-- 节点编辑弹窗 -->
    <el-dialog v-model="showNodeDialog" :title="editingNode ? '编辑节点' : '添加节点'" width="480px">
      <el-form label-width="90px">
        <el-form-item label="节点Key" required><el-input v-model="nodeForm.nodeKey" :disabled="!!editingNode" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="nodeForm.nodeType" style="width:160px" :disabled="!!editingNode">
            <el-option v-for="t in NODE_TYPES" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="名称"><el-input v-model="nodeForm.name" /></el-form-item>
        <el-divider v-if="nodeTypeConfigs[nodeForm.nodeType]?.fields?.length">节点配置</el-divider>
        <template v-for="field in (nodeTypeConfigs[nodeForm.nodeType]?.fields || [])" :key="field.key">
          <el-form-item :label="field.label">
            <el-input v-if="field.type === 'input'" v-model="nodeConfig[field.key]" style="width:260px" />
            <el-input-number v-else-if="field.type === 'number'" v-model="nodeConfig[field.key]" :min="field.min||0" :max="field.max||9999" style="width:200px" />
            <el-slider v-else-if="field.type === 'slider'" v-model="nodeConfig[field.key]" :min="field.min||0" :max="field.max||2" :step="field.step||0.1" show-input />
            <el-select v-else-if="field.type === 'select'" v-model="nodeConfig[field.key]" style="width:200px">
              <el-option v-for="opt in (field.options||[])" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
            <el-input v-else-if="field.type === 'textarea'" v-model="nodeConfig[field.key]" type="textarea" :rows="2" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="showNodeDialog=false">取消</el-button>
        <el-button type="primary" @click="handleSaveNode">{{ editingNode ? '保存' : '添加' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: $spacing-base; }
.section-title { font-size: 15px; font-weight: 600; }

// ====== 画布头部 ======
.canvas-header {
  display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; flex-wrap: wrap; gap: 8px;
}
.canvas-title { display: flex; align-items: center; font-size: 15px; }
.canvas-actions { display: flex; gap: 8px; }

// ====== 画布布局 ======
.canvas-layout { display: flex; gap: 12px; }

// ====== 工具箱 ======
.toolbox-panel {
  width: 160px; flex-shrink: 0; background: #fff; border: 1px solid #ebeef5; border-radius: 8px; padding: 8px; max-height: 500px; overflow-y: auto;
}
.toolbox-header { display: flex; flex-direction: column; gap: 6px; margin-bottom: 8px; font-size: 13px; font-weight: 600; }
.toolbox-item {
  display: flex; align-items: center; gap: 6px; padding: 6px 8px; border-radius: 4px; cursor: pointer; font-size: 13px;
  &:hover { background: #f0f5ff; color: #409eff; }
}
.toolbox-icon { font-size: 16px; }

// ====== 画布区域 ======
.canvas-area {
  flex: 1; position: relative; height: 520px; border: 1px solid #ebeef5; border-radius: 8px;
  background: #fafafa; overflow: hidden;
}
.canvas-svg { position: absolute; top: 0; left: 0; width: 100%; height: 100%; pointer-events: none; }
.edge-path { pointer-events: stroke; cursor: pointer; }
.edge-path:hover { stroke: #f56c6c; stroke-width: 3; }
.canvas-empty { position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%); }
.welcome-panel { margin-top: 60px; display: flex; justify-content: center; }

// ====== 节点卡片 ======
.canvas-node {
  position: absolute; width: 150px; background: #fff; border: 2px solid #dcdfe6; border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06); cursor: move; z-index: 1; transition: box-shadow 0.15s;
  &:hover { box-shadow: 0 4px 16px rgba(0,0,0,0.12); z-index: 2; }
  &.is-selected { border-color: #409eff; box-shadow: 0 0 0 2px rgba(64,158,255,0.2); z-index: 3; }
  &.is-connecting { border-color: #e6a23c; box-shadow: 0 0 0 2px rgba(230,162,60,0.3); }
  &.is-connect-target { border-color: #67c23a; box-shadow: 0 0 0 2px rgba(103,194,58,0.3); cursor: pointer; }
}
.node-header { display: flex; align-items: center; gap: 4px; padding: 6px 8px; }
.node-icon { font-size: 14px; }
.node-type-tag { font-size: 10px; color: #fff; padding: 1px 6px; border-radius: 4px; margin-left: auto; }
.node-name { font-size: 13px; font-weight: 600; padding: 0 8px 2px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.node-key { font-size: 11px; color: #909399; padding: 0 8px 4px; }
.node-actions { display: flex; gap: 2px; padding: 4px 6px; border-top: 1px solid #f0f0f0; flex-wrap: wrap; }
.node-actions :deep(.el-button) { font-size: 11px; }

// ====== 节点属性面板 ======
.node-props-panel {
  margin-top: 12px; background: #fff; border: 1px solid #ebeef5; border-radius: 8px; padding: 12px; max-width: 400px;
}
.props-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; font-weight: 600; }
.props-body { display: flex; flex-direction: column; gap: 6px; }
.props-row { display: flex; gap: 8px; font-size: 13px; }
.props-row label { color: #909399; min-width: 40px; flex-shrink: 0; }
.props-row pre { background: #f5f7fa; padding: 4px 8px; border-radius: 4px; font-size: 11px; max-height: 100px; overflow: auto; margin: 0; }
</style>
