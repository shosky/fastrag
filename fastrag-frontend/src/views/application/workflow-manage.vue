<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const route = useRoute()
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
    nodes.value = (Array.isArray(nodeRes) ? nodeRes : []).map((n: any) => ({ ...n, config: parseCfg(n.config) }))
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

function parseCfg(c: any): Record<string, any> {
  if (!c) return {}
  if (typeof c === 'object') return c
  try { return JSON.parse(c) } catch { return {} }
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
          x: node.positionX, y: node.positionY, config: parseCfg(node.config),
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

// ===== 复制 / 剪切 / 粘贴节点 =====
const nodeClipboard = ref<any>(null)

function handleCopyNode(node: any) {
  nodeClipboard.value = JSON.parse(JSON.stringify(node))
  ElMessage.success(`已复制「${node.name || node.nodeKey}」，点击「粘贴节点」添加副本`)
}

function handleCutNode(node: any) {
  if (!node) return
  nodeClipboard.value = JSON.parse(JSON.stringify(node))
  nodes.value = nodes.value.filter(n => n.nodeKey !== node.nodeKey)
  edges.value = edges.value.filter(e => e.source !== node.nodeKey && e.target !== node.nodeKey)
  if (selectedNode.value?.nodeKey === node.nodeKey) selectedNode.value = null
  ElMessage.success(`已剪切「${node.name || node.nodeKey}」，点击「粘贴节点」放置`)
}

async function handlePasteNode() {
  if (!nodeClipboard.value || !wfId.value) return
  const src = nodeClipboard.value
  const pasted = {
    nodeKey: 'node_' + Date.now(),
    nodeType: src.nodeType,
    name: (src.name || src.nodeKey) + ' 副本',
    positionX: (src.positionX || 0) + 30,
    positionY: (src.positionY || 0) + 30,
    enabled: 1,
    config: parseCfg(src.config),
  }
  nodes.value.push(pasted)
  try { await api.addWorkflowNode(wfId.value, { nodeKey: pasted.nodeKey, type: pasted.nodeType, name: pasted.name, x: pasted.positionX, y: pasted.positionY }) } catch {}
  ElMessage.success(`已粘贴为「${pasted.name}」`)
}

// ===== 节点列表（查看 / 上移 / 下移） =====
const showNodeList = ref(false)

async function handleMoveNodeOrder(index: number, dir: -1 | 1) {
  const j = index + dir
  if (j < 0 || j >= nodes.value.length) return
  const a = nodes.value[index], b = nodes.value[j]
  // 画布位置随顺序交换并持久化
  const ax = a.positionX, ay = a.positionY
  a.positionX = b.positionX; a.positionY = b.positionY
  b.positionX = ax; b.positionY = ay
  nodes.value[index] = b; nodes.value[j] = a
  try {
    await api.moveWorkflowNode(wfId.value, a.nodeKey, { x: a.positionX, y: a.positionY })
    await api.moveWorkflowNode(wfId.value, b.nodeKey, { x: b.positionX, y: b.positionY })
  } catch {}
  ElMessage.success(dir < 0 ? '已上移' : '已下移')
}

// ===== 导出 / 导入节点配置 =====
const importFileRef = ref<HTMLInputElement>()

function downloadJson(data: any, filename: string) {
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url; a.download = filename; a.click()
  URL.revokeObjectURL(url)
}

function nodeExportShape(node: any) {
  return { nodeKey: node.nodeKey, nodeType: node.nodeType, name: node.name, positionX: node.positionX, positionY: node.positionY, config: parseCfg(node.config) }
}

function handleExportNodes() {
  if (!nodes.value.length) { ElMessage.warning('画布为空，无可导出的节点'); return }
  downloadJson(
    { workflowId: wfId.value, workflowName: wfName.value, exportedAt: new Date().toISOString(), nodes: nodes.value.map(nodeExportShape), edges: edges.value },
    `workflow-${wfName.value || wfId.value}-nodes.json`)
  ElMessage.success('节点配置已导出')
}

function handleExportNode(node: any) {
  downloadJson(nodeExportShape(node), `node-${node.nodeKey}.json`)
  ElMessage.success(`「${node.name || node.nodeKey}」配置已导出`)
}

function handleImportNodes() { importFileRef.value?.click() }

async function onImportFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || !wfId.value) return
  let data: any
  try { data = JSON.parse(await file.text()) } catch { ElMessage.error('文件不是合法的 JSON'); return }
  const list: any[] = Array.isArray(data) ? data : Array.isArray(data?.nodes) ? data.nodes : data?.nodeKey ? [data] : []
  if (!list.length) { ElMessage.warning('文件中没有节点配置'); return }
  let added = 0, updated = 0
  for (const item of list) {
    if (!item?.nodeKey) continue
    const exist = nodes.value.find(n => n.nodeKey === item.nodeKey)
    if (exist) {
      if (item.name != null) exist.name = item.name
      if (item.nodeType != null) exist.nodeType = item.nodeType
      if (item.config != null) exist.config = parseCfg(item.config)
      if (item.positionX != null) exist.positionX = item.positionX
      if (item.positionY != null) exist.positionY = item.positionY
      updated++
    } else {
      const n = { nodeKey: item.nodeKey, nodeType: item.nodeType || 'llm', name: item.name || item.nodeKey, positionX: item.positionX ?? 120, positionY: item.positionY ?? 120, enabled: 1, config: parseCfg(item.config) }
      nodes.value.push(n)
      try { await api.addWorkflowNode(wfId.value, { nodeKey: n.nodeKey, type: n.nodeType, name: n.name, x: n.positionX, y: n.positionY }) } catch {}
      added++
    }
  }
  await saveCanvas()
  ElMessage.success(`导入完成：新增 ${added} 个，更新 ${updated} 个`)
}

// ===== 测试节点 =====
const showTestDialog = ref(false)
const testTarget = ref<any>(null)
const testInput = ref('{}')
const testResult = ref<any>(null)
const testRunning = ref(false)

function openTestDialog(node: any) {
  testTarget.value = node
  testInput.value = '{\n  "query": "你好"\n}'
  testResult.value = null
  showTestDialog.value = true
}

async function runNodeTest() {
  if (!testTarget.value) return
  let input: any
  try { input = JSON.parse(testInput.value || '{}') } catch { ElMessage.warning('输入必须是合法 JSON'); return }
  testRunning.value = true
  try {
    testResult.value = await api.testWorkflowNode(wfId.value, testTarget.value.nodeKey, { input })
  } catch (e: any) { testResult.value = { status: 'failed', error: e?.message || '执行失败' } }
  finally { testRunning.value = false }
}

// ===== 节点日志（查看 / 清理） =====
const showLogDialog = ref(false)
const logTarget = ref<any>(null)
const nodeLogs = ref<string[]>([])

async function openLogDialog(node: any) {
  logTarget.value = node
  showLogDialog.value = true
  await refreshNodeLogs()
}

async function refreshNodeLogs() {
  if (!logTarget.value) return
  try {
    const res: any = await api.getWorkflowNodeLogs(wfId.value, logTarget.value.nodeKey)
    nodeLogs.value = Array.isArray(res) ? res : []
  } catch { nodeLogs.value = [] }
}

async function handleClearNodeLogs() {
  if (!logTarget.value) return
  try {
    await api.clearWorkflowNodeLogs(wfId.value, logTarget.value.nodeKey)
    nodeLogs.value = []
    ElMessage.success('节点日志已清理')
  } catch (e: any) { ElMessage.error(e?.message || '清理失败') }
}

// ===== 节点扩展配置（条件/循环/延时/资源/权限/日志级别/环境变量/数据保留策略/数据备份） =====
const EXT_DIMENSIONS = [
  { key: 'conditions', label: '节点条件' },
  { key: 'loops', label: '节点循环' },
  { key: 'delays', label: '节点延时' },
  { key: 'resources', label: '节点资源' },
  { key: 'permissions', label: '节点权限' },
  { key: 'logLevel', label: '日志级别' },
  { key: 'envVars', label: '环境变量' },
  { key: 'dataPolicies', label: '数据保留策略' },
  { key: 'backups', label: '数据备份' },
]
// URL 路径段与存储键不同名的维度（其余同名）
const DIM_URL: Record<string, string> = { logLevel: 'log-level', envVars: 'env-vars', dataPolicies: 'data-policies' }
const dimUrl = (key: string) => DIM_URL[key] || key

const showExtDialog = ref(false)
const extTarget = ref<any>(null)
const activeDim = ref('conditions')
const extLoading = ref(false)
const extForm = ref<Record<string, any>>({})

function defaultDim(key: string): any {
  switch (key) {
    case 'conditions': return { expression: '', targetNode: '' }
    case 'loops': return { type: 'count', count: 1, condition: '' }
    case 'delays': return { duration: 0, unit: 'seconds' }
    case 'resources': return { timeoutMs: 30000, retryCount: 0, retryIntervalMs: 1000 }
    case 'permissions': return { visibleRoles: '', editableRoles: '' }
    case 'logLevel': return 'info'
    case 'envVars': return []
    case 'dataPolicies': return { retentionDays: 30, autoArchive: false }
    case 'backups': return { autoBackup: false, frequency: 'daily', snapshots: [] as Array<{ at: string; data: string }> }
    default: return {}
  }
}

function normalizeDim(key: string, val: any): any {
  const base = defaultDim(key)
  if (key === 'envVars') {
    if (Array.isArray(val)) return val
    return Object.entries(val || {}).map(([k, v]) => ({ key: k, value: String(v ?? '') }))
  }
  if (key === 'logLevel') return val || base
  if (typeof val === 'object' && val !== null && typeof base === 'object' && !Array.isArray(base)) return { ...base, ...val }
  return val
}

function openExtDialog(node: any) {
  extTarget.value = node
  for (const d of EXT_DIMENSIONS) if (!extForm.value[d.key]) extForm.value[d.key] = defaultDim(d.key)
  showExtDialog.value = true
  selectDim(activeDim.value)
}

async function selectDim(key: string) {
  activeDim.value = key
  extLoading.value = true
  try {
    // 查看：从后端读取该维度当前配置
    const res: any = await api.getWorkflowNodeConfig(wfId.value, extTarget.value.nodeKey, dimUrl(key))
    const val = res && typeof res === 'object' && 'value' in res ? res.value : res?.[key]
    extForm.value[key] = val == null ? defaultDim(key) : normalizeDim(key, val)
  } catch { extForm.value[key] = defaultDim(key) }
  finally { extLoading.value = false }
}

function setNodeConfigKey(node: any, key: string, val: any) {
  const cfg = parseCfg(node.config)
  if (val === undefined) delete cfg[key]
  else cfg[key] = val
  node.config = cfg
}

function dimValueToSave(key: string) {
  const v = extForm.value[key]
  if (key === 'envVars') {
    const obj: Record<string, string> = {}
    for (const kv of v || []) if (kv.key) obj[kv.key] = kv.value
    return obj
  }
  return v
}

async function saveDim(key: string) {
  if (!extTarget.value) return
  try {
    // 设置 / 修改：按维度保存，后端合并进节点配置，互不覆盖
    await api.saveWorkflowNodeConfig(wfId.value, extTarget.value.nodeKey, dimUrl(key), { [key]: dimValueToSave(key) })
    setNodeConfigKey(extTarget.value, key, dimValueToSave(key))
    ElMessage.success(`「${EXT_DIMENSIONS.find(d => d.key === key)?.label}」已保存`)
  } catch (e: any) { ElMessage.error(e?.message || '保存失败') }
}

async function clearDim(key: string) {
  if (!extTarget.value) return
  try {
    // 删除：移除该维度配置
    await api.deleteWorkflowNodeConfig(wfId.value, extTarget.value.nodeKey, dimUrl(key))
    extForm.value[key] = defaultDim(key)
    setNodeConfigKey(extTarget.value, key, undefined)
    ElMessage.success(`「${EXT_DIMENSIONS.find(d => d.key === key)?.label}」已清除`)
  } catch (e: any) { ElMessage.error(e?.message || '清除失败') }
}

// ===== 数据备份：立即备份 / 恢复 / 删除备份 =====
function fmtTime() {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

function handleBackupNow() {
  if (!extTarget.value) return
  const snaps = extForm.value.backups?.snapshots || []
  const cfg = parseCfg(extTarget.value.config)
  delete cfg.backups
  snaps.unshift({ at: fmtTime(), data: JSON.stringify(cfg) })
  extForm.value.backups.snapshots = snaps
  saveDim('backups')
}

async function handleRestoreBackup(snap: any) {
  if (!extTarget.value) return
  try {
    const data = JSON.parse(snap.data || '{}')
    extTarget.value.config = { ...data, backups: extForm.value.backups }
    await api.updateWorkflowNode(wfId.value, extTarget.value.nodeKey, {
      nodeKey: extTarget.value.nodeKey, type: extTarget.value.nodeType, name: extTarget.value.name,
      x: extTarget.value.positionX, y: extTarget.value.positionY, config: parseCfg(extTarget.value.config),
    })
    ElMessage.success(`已恢复到 ${snap.at} 的节点配置`)
  } catch (e: any) { ElMessage.error(e?.message || '恢复失败') }
}

function handleDeleteBackup(i: number) {
  extForm.value.backups?.snapshots?.splice(i, 1)
  saveDim('backups')
}

// ===== 节点「更多」菜单 =====
function handleNodeCommand(cmd: string, node: any) {
  switch (cmd) {
    case 'copy': handleCopyNode(node); break
    case 'cut': handleCutNode(node); break
    case 'ext': openExtDialog(node); break
    case 'test': openTestDialog(node); break
    case 'logs': openLogDialog(node); break
    case 'export': handleExportNode(node); break
  }
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

onMounted(async () => {
  await loadWorkflows()
  // 支持从应用编辑器「配置节点」带 ?wfId= 跳转，自动选中对应工作流
  const wfIdParam = route.query.wfId as string
  if (wfIdParam) {
    const target = workflowList.value.find((w: any) => w.id === wfIdParam)
    if (target) handleSelectWorkflow(target)
  }
})

// ============================================================================
// 业务流-对话测试（录制/查看/删除/运行）
// ============================================================================
const wfTestList = ref<any[]>([])
const wfTestLoading = ref(false)
const showWfTestDialog = ref(false)
const showWfTestCreate = ref(false)
const wfTestCreateLoading = ref(false)
const wfTestForm = ref({ name: '', query: '', expectedOutput: '' })
const wfRunningId = ref('')
function openWfTest() { showWfTestDialog.value = true; loadWfTests() }
async function loadWfTests() {
  if (!wfId.value) return
  wfTestLoading.value = true
  try { wfTestList.value = ((await api.getWorkflowTestCases(wfId.value)) as any) || [] } catch { wfTestList.value = [] } finally { wfTestLoading.value = false }
}
// 录制：真实执行业务流后保存为案例
async function handleRecordWfTest() {
  if (!wfTestForm.value.query) { ElMessage.warning('请输入测试输入'); return }
  wfTestCreateLoading.value = true
  try {
    const exec: any = await api.executeWorkflow(wfId.value, { query: wfTestForm.value.query })
    await api.createWorkflowTestCase(wfId.value, {
      name: wfTestForm.value.name || ('录制-' + wfTestForm.value.query.substring(0, 16)),
      query: wfTestForm.value.query,
      expectedOutput: wfTestForm.value.expectedOutput,
      actualOutput: typeof exec?.output === 'string' ? exec.output : JSON.stringify(exec?.steps || exec),
      inputs: JSON.stringify({ query: wfTestForm.value.query }),
    })
    ElMessage.success('测试案例已录制')
    showWfTestCreate.value = false
    await loadWfTests()
  } catch { ElMessage.error('录制失败') } finally { wfTestCreateLoading.value = false }
}
async function handleRunWfTest(row: any) {
  wfRunningId.value = row.id
  try {
    const r: any = await api.runWorkflowTestCase(wfId.value, row.id)
    ElMessage.success(r?.matched === 1 ? '运行通过' : '运行完成（未匹配期望输出）')
    await loadWfTests()
  } catch { ElMessage.error('运行失败') } finally { wfRunningId.value = '' }
}
async function handleDeleteWfTest(row: any) {
  try {
    await ElMessageBox.confirm(`确认删除案例「${row.name}」？`, '删除确认', { type: 'warning' })
    await api.deleteWorkflowTestCase(wfId.value, row.id)
    await loadWfTests()
    ElMessage.success('已删除')
  } catch {}
}
// 查看案例详情
const showWfTestView = ref(false)
const viewingWfTest = ref<any>(null)
function handleViewWfTest(row: any) { viewingWfTest.value = row; showWfTestView.value = true }

// ============================================================================
// 业务流-对话调试（级别设置/查看/导出/清理）
// ============================================================================
const showWfDebugDialog = ref(false)
const wfDebug = ref<{ level: string; logs: any[] }>({ level: 'info', logs: [] })
const wfDebugLoading = ref(false)
function openWfDebug() { showWfDebugDialog.value = true; loadWfDebug() }
async function loadWfDebug() {
  if (!wfId.value) return
  wfDebugLoading.value = true
  try {
    const r: any = await api.getWorkflowDebugInfo(wfId.value)
    wfDebug.value = { level: r?.level || 'info', logs: r?.logs || [] }
  } catch { wfDebug.value = { level: 'info', logs: [] } } finally { wfDebugLoading.value = false }
}
async function handleSaveWfLevel() {
  try { await api.saveWorkflowDebugConfig(wfId.value, { level: wfDebug.value.level }); ElMessage.success('调试级别已保存'); await loadWfDebug() } catch { ElMessage.error('保存失败') }
}
async function handleExportWfDebug() {
  try {
    const blob = await api.exportWorkflowDebugLogs(wfId.value) as unknown as Blob
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a'); a.href = url; a.download = `wf_debug_logs_${wfId.value}.csv`; a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('调试日志已导出')
  } catch { ElMessage.error('导出失败') }
}
async function handleClearWfDebug() {
  try { await ElMessageBox.confirm('确认清空该业务流全部调试日志？', '清理确认', { type: 'warning' }) } catch { return }
  try { await api.clearWorkflowDebugLogs(wfId.value); ElMessage.success('已清空'); await loadWfDebug() } catch { ElMessage.error('清理失败') }
}

// ============================================================================
// 业务流-知识更新（手动更新/自动更新/更新日志/比较）
// ============================================================================
const appId = (route.params.id as string) || ''
const wfKbList = ref<any[]>([])
const wfKbId = ref('')
const wfUpdateLogs = ref<any[]>([])
const wfUpdateLoading = ref(false)
const showWfUpdateDialog = ref(false)
const wfAutoConfig = ref({ enabled: false, schedule: '0 0 2 * * ?', incremental: true })
const showWfAutoConfig = ref(false)
function openWfKbUpdate() { showWfUpdateDialog.value = true; loadWfKbs(); }
async function loadWfKbs() {
  try {
    const res: any = await api.getAppKbBindings(appId)
    wfKbList.value = (Array.isArray(res) ? res : res?.list || []).map((b: any) => ({ id: b.kbId, name: b.kbName || b.kbId }))
    if (wfKbList.value.length && !wfKbId.value) wfKbId.value = wfKbList.value[0].id
  } catch { wfKbList.value = [] }
  await loadWfUpdateLogs()
}
async function loadWfUpdateLogs() {
  if (!wfKbId.value) { wfUpdateLogs.value = []; return }
  wfUpdateLoading.value = true
  try { const r: any = await api.getKnowledgeUpdateLogs(wfKbId.value, 1, 50); wfUpdateLogs.value = r?.list || [] } catch { wfUpdateLogs.value = [] } finally { wfUpdateLoading.value = false }
}
async function handleWfManualUpdate() {
  try {
    await ElMessageBox.confirm('确认手动触发知识更新？将向应用绑定的知识库写入更新记录。', '手动更新', { type: 'info' })
    const r: any = await api.triggerAppKnowledgeUpdate(appId, { remark: '业务流管理-手动更新' })
    ElMessage.success(`更新已完成（${r?.updated ?? 0} 个知识库）`)
    await loadWfUpdateLogs()
  } catch {}
}
async function handleWfSaveAutoConfig() {
  try { await api.setAutoKnowledgeUpdate(appId, wfAutoConfig.value); ElMessage.success('自动更新配置已保存'); showWfAutoConfig.value = false } catch { ElMessage.error('保存失败') }
}
async function handleWfCompare(row: any) {
  if (!row.oldId && !row.newId) { ElMessage.info('该记录无新旧版本信息'); return }
  try {
    const r: any = await api.compareKnowledgeContent(wfKbId.value, row.oldId, row.newId)
    ElMessageBox.alert(`旧版：${r?.oldVersion?.title || '-'}（${r?.oldLength ?? 0} 字）\n新版：${r?.newVersion?.title || '-'}（${r?.newLength ?? 0} 字）`, '内容比较')
  } catch { ElMessage.error('获取比较内容失败') }
}

// ============================================================================
// 业务流-对话优化（分析/查看建议/应用/测试/导出）
// ============================================================================
const showWfOptDialog = ref(false)
const wfOptList = ref<any[]>([])
const wfOptLoading = ref(false)
const wfAnalyze = ref<any>(null)
const wfOptForm = ref({ name: '', suggestionType: 'flow', description: '' })
const wfTestingOptId = ref('')
function openWfOpt() { showWfOptDialog.value = true; loadWfOpts() }
async function loadWfOpts() {
  if (!wfId.value) return
  wfOptLoading.value = true
  try { wfOptList.value = ((await api.getWorkflowOptimizations(wfId.value)) as any) || [] } catch { wfOptList.value = [] } finally { wfOptLoading.value = false }
}
async function handleAnalyzeWf() {
  try { wfAnalyze.value = await api.analyzeWorkflowOptimization(wfId.value); ElMessage.success('分析完成') } catch { ElMessage.error('分析失败') }
}
async function handleCreateWfOpt() {
  if (!wfOptForm.value.name) { ElMessage.warning('请输入建议标题'); return }
  try { await api.createWorkflowOptimization(wfId.value, wfOptForm.value); wfOptForm.value = { name: '', suggestionType: 'flow', description: '' }; await loadWfOpts(); ElMessage.success('优化建议已保存') } catch { ElMessage.error('保存失败') }
}
async function handleApplyWfOpt(row: any) {
  try { await api.applyWorkflowOptimization(row.id); await loadWfOpts(); ElMessage.success('已应用') } catch { ElMessage.error('应用失败') }
}
async function handleTestWfOpt(row: any) {
  wfTestingOptId.value = row.id
  try {
    const r: any = await api.testWorkflowOptimization(row.id)
    ElMessageBox.alert(`测试前：案例通过率 ${r?.before?.testCasePassRate ?? '-'}%\n测试后（预期）：${r?.after?.testCasePassRate ?? '-'}%`, '优化效果测试')
    await loadWfOpts()
  } catch { ElMessage.error('测试失败') } finally { wfTestingOptId.value = '' }
}
async function handleExportWfOpts() {
  try {
    const blob = await api.exportWorkflowOptimizations(wfId.value) as unknown as Blob
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a'); a.href = url; a.download = `wf_optimizations_${wfId.value}.csv`; a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('优化报告已导出')
  } catch { ElMessage.error('导出失败') }
}

// ============================================================================
// 业务流-配置迁移（查看迁移日志/进度/设置迁移策略）
// ============================================================================
const showWfMigDialog = ref(false)
const wfMigList = ref<any[]>([])
const wfMigLoading = ref(false)
const wfMigForm = ref({ targetWorkflowId: '', targetEnv: 'test', strategy: 'overwrite' })
const wfMigSubmitting = ref(false)
function openWfMig() { showWfMigDialog.value = true; loadWfMigs() }
async function loadWfMigs() {
  wfMigLoading.value = true
  try { wfMigList.value = ((await api.getWorkflowMigrations()) as any) || [] } catch { wfMigList.value = [] } finally { wfMigLoading.value = false }
}
async function handleCreateWfMig() {
  if (!wfMigForm.value.targetWorkflowId) { ElMessage.warning('请选择目标业务流'); return }
  if (wfMigForm.value.targetWorkflowId === wfId.value) { ElMessage.warning('目标业务流不能与源相同'); return }
  wfMigSubmitting.value = true
  try {
    await api.createWorkflowMigration({ sourceWorkflowId: wfId.value, ...wfMigForm.value, operator: 'admin' })
    ElMessage.success('迁移已完成')
    await loadWfMigs()
  } catch { ElMessage.error('迁移失败') } finally { wfMigSubmitting.value = false }
}
function wfMigStatus(s: string) { return ({ running: '执行中', pending: '待执行', completed: '已完成', failed: '失败' } as Record<string, string>)[s] || s }
function wfMigStatusColor(s: string) { return (({ completed: 'success', failed: 'danger', running: 'warning', pending: 'info' } as Record<string, string>)[s] || 'info') as any }
function wfNameById(id: string) { return workflowList.value.find((w: any) => w.id === id)?.name || id }
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
        <el-button size="small" @click="showToolbox = !showToolbox">📦 现有节点库</el-button>
        <el-button size="small" @click="handleAddNode">＋ 添加节点</el-button>
        <el-button size="small" @click="showNodeList = true">📋 节点列表</el-button>
        <el-button size="small" :disabled="!nodeClipboard" @click="handlePasteNode">📥 粘贴节点</el-button>
        <el-button size="small" @click="handleExportNodes">📤 导出配置</el-button>
        <el-button size="small" @click="handleImportNodes">🗂 导入配置</el-button>
        <el-button size="small" type="success" @click="openWfTest">🧪 对话测试</el-button>
        <el-button size="small" type="success" plain @click="openWfDebug">🐞 对话调试</el-button>
        <el-button size="small" type="primary" plain @click="openWfKbUpdate">🔄 知识更新</el-button>
        <el-button size="small" type="warning" plain @click="openWfOpt">📈 对话优化</el-button>
        <el-button size="small" type="info" plain @click="openWfMig">🔁 配置迁移</el-button>
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
              <el-dropdown trigger="click" @command="(cmd) => handleNodeCommand(cmd, node)">
                <el-button link size="small">更多 ▾</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="copy">复制节点</el-dropdown-item>
                    <el-dropdown-item command="cut">剪切节点</el-dropdown-item>
                    <el-dropdown-item command="ext" divided>扩展配置</el-dropdown-item>
                    <el-dropdown-item command="test">测试节点</el-dropdown-item>
                    <el-dropdown-item command="logs">节点日志</el-dropdown-item>
                    <el-dropdown-item command="export">导出节点配置</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
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

    <!-- 隐藏的导入文件选择器 -->
    <input ref="importFileRef" type="file" accept=".json,application/json" style="display:none" @change="onImportFileChange" />

    <!-- 节点列表抽屉（查看节点列表 / 上移 / 下移） -->
    <el-drawer v-model="showNodeList" title="节点列表" size="720px">
      <el-table :data="nodes" size="small" stripe>
        <el-table-column label="顺序" width="120">
          <template #default="{ $index }">
            <el-button link size="small" :disabled="$index === 0" @click="handleMoveNodeOrder($index, -1)">上移</el-button>
            <el-button link size="small" :disabled="$index === nodes.length - 1" @click="handleMoveNodeOrder($index, 1)">下移</el-button>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="名称" min-width="120" show-overflow-tooltip />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">{{ nodeTypeMap[row.nodeType]?.label || row.nodeType }}</template>
        </el-table-column>
        <el-table-column prop="nodeKey" label="Key" min-width="130" show-overflow-tooltip />
        <el-table-column label="位置" width="100">
          <template #default="{ row }">({{ row.positionX }}, {{ row.positionY }})</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleEditNode(row)">编辑</el-button>
            <el-button link size="small" @click="handleCopyNode(row)">复制</el-button>
            <el-button link size="small" @click="handleCutNode(row)">剪切</el-button>
            <el-button link type="danger" size="small" @click="handleDeleteNode(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!nodes.length" description="画布为空，请先添加节点" :image-size="60" />
    </el-drawer>

    <!-- 测试节点弹窗 -->
    <el-dialog v-model="showTestDialog" :title="`测试节点：${testTarget?.name || testTarget?.nodeKey || ''}`" width="560px">
      <el-form label-width="70px">
        <el-form-item label="输入">
          <el-input v-model="testInput" type="textarea" :rows="4" placeholder="JSON 格式的测试输入" />
        </el-form-item>
      </el-form>
      <el-button type="primary" size="small" :loading="testRunning" @click="runNodeTest">▶ 执行测试</el-button>
      <template v-if="testResult">
        <el-alert
          style="margin-top:12px"
          :type="testResult.status === 'completed' ? 'success' : 'error'"
          :closable="false" show-icon
          :title="`状态：${testResult.status || 'unknown'}`" />
        <pre class="result-pre">{{ JSON.stringify(testResult, null, 2) }}</pre>
      </template>
      <template #footer><el-button @click="showTestDialog=false">关闭</el-button></template>
    </el-dialog>

    <!-- 节点日志弹窗（查看 / 清理） -->
    <el-dialog v-model="showLogDialog" :title="`节点日志：${logTarget?.name || logTarget?.nodeKey || ''}`" width="560px">
      <div style="margin-bottom:8px;display:flex;gap:8px">
        <el-button size="small" @click="refreshNodeLogs">刷新</el-button>
        <el-button size="small" type="danger" @click="handleClearNodeLogs">清理日志</el-button>
      </div>
      <div class="log-panel">
        <div v-for="(line, i) in nodeLogs" :key="i" class="log-line">{{ line }}</div>
      </div>
      <el-empty v-if="!nodeLogs.length" description="暂无日志，可先「测试节点」产生日志" :image-size="60" />
      <template #footer><el-button @click="showLogDialog=false">关闭</el-button></template>
    </el-dialog>

    <!-- 节点扩展配置弹窗（条件/循环/延时/资源/权限/日志级别/环境变量/数据保留策略/数据备份） -->
    <el-dialog v-model="showExtDialog" :title="`扩展配置：${extTarget?.name || extTarget?.nodeKey || ''}`" width="720px">
      <div class="ext-layout" v-loading="extLoading">
        <div class="ext-menu">
          <div v-for="d in EXT_DIMENSIONS" :key="d.key" class="ext-menu-item" :class="{ active: activeDim === d.key }" @click="selectDim(d.key)">{{ d.label }}</div>
        </div>
        <div class="ext-form">
          <!-- 节点条件 -->
          <template v-if="activeDim === 'conditions'">
            <el-form label-width="90px">
              <el-form-item label="条件表达式"><el-input v-model="extForm.conditions.expression" placeholder="如：{{intent}} == '售后'" /></el-form-item>
              <el-form-item label="满足时跳转">
                <el-select v-model="extForm.conditions.targetNode" clearable placeholder="选择目标节点" style="width:220px">
                  <el-option v-for="n in nodes.filter(x => !extTarget || x.nodeKey !== extTarget.nodeKey)" :key="n.nodeKey" :label="n.name || n.nodeKey" :value="n.nodeKey" />
                </el-select>
              </el-form-item>
            </el-form>
          </template>
          <!-- 节点循环 -->
          <template v-else-if="activeDim === 'loops'">
            <el-form label-width="90px">
              <el-form-item label="循环类型">
                <el-select v-model="extForm.loops.type" style="width:160px">
                  <el-option label="固定次数" value="count" /><el-option label="条件循环" value="while" /><el-option label="遍历数组" value="foreach" />
                </el-select>
              </el-form-item>
              <el-form-item v-if="extForm.loops.type === 'count'" label="循环次数"><el-input-number v-model="extForm.loops.count" :min="1" :max="1000" /></el-form-item>
              <el-form-item v-else label="循环条件"><el-input v-model="extForm.loops.condition" placeholder="如：{{items.length}} > 0" /></el-form-item>
            </el-form>
          </template>
          <!-- 节点延时 -->
          <template v-else-if="activeDim === 'delays'">
            <el-form label-width="90px">
              <el-form-item label="延时时长"><el-input-number v-model="extForm.delays.duration" :min="0" :max="86400" /></el-form-item>
              <el-form-item label="单位">
                <el-select v-model="extForm.delays.unit" style="width:120px">
                  <el-option label="秒" value="seconds" /><el-option label="分钟" value="minutes" /><el-option label="小时" value="hours" />
                </el-select>
              </el-form-item>
            </el-form>
          </template>
          <!-- 节点资源 -->
          <template v-else-if="activeDim === 'resources'">
            <el-form label-width="90px">
              <el-form-item label="超时(ms)"><el-input-number v-model="extForm.resources.timeoutMs" :min="1000" :max="600000" :step="1000" /></el-form-item>
              <el-form-item label="重试次数"><el-input-number v-model="extForm.resources.retryCount" :min="0" :max="10" /></el-form-item>
              <el-form-item label="重试间隔(ms)"><el-input-number v-model="extForm.resources.retryIntervalMs" :min="0" :max="60000" :step="100" /></el-form-item>
            </el-form>
          </template>
          <!-- 节点权限 -->
          <template v-else-if="activeDim === 'permissions'">
            <el-form label-width="90px">
              <el-form-item label="可见角色"><el-input v-model="extForm.permissions.visibleRoles" placeholder="逗号分隔，如：admin,editor" /></el-form-item>
              <el-form-item label="可编辑角色"><el-input v-model="extForm.permissions.editableRoles" placeholder="逗号分隔，如：admin" /></el-form-item>
            </el-form>
          </template>
          <!-- 日志级别 -->
          <template v-else-if="activeDim === 'logLevel'">
            <el-form label-width="90px">
              <el-form-item label="日志级别">
                <el-radio-group v-model="extForm.logLevel">
                  <el-radio-button value="debug">DEBUG</el-radio-button>
                  <el-radio-button value="info">INFO</el-radio-button>
                  <el-radio-button value="warn">WARN</el-radio-button>
                  <el-radio-button value="error">ERROR</el-radio-button>
                </el-radio-group>
              </el-form-item>
            </el-form>
          </template>
          <!-- 环境变量 -->
          <template v-else-if="activeDim === 'envVars'">
            <div class="kv-row" v-for="(kv, i) in extForm.envVars" :key="i">
              <el-input v-model="kv.key" placeholder="变量名" style="width:150px" />
              <el-input v-model="kv.value" placeholder="值" style="width:180px" />
              <el-button link type="danger" size="small" @click="extForm.envVars.splice(i, 1)">删除</el-button>
            </div>
            <el-button size="small" @click="extForm.envVars.push({ key: '', value: '' })">＋ 添加变量</el-button>
          </template>
          <!-- 数据保留策略 -->
          <template v-else-if="activeDim === 'dataPolicies'">
            <el-form label-width="90px">
              <el-form-item label="保留天数"><el-input-number v-model="extForm.dataPolicies.retentionDays" :min="1" :max="3650" /></el-form-item>
              <el-form-item label="自动归档"><el-switch v-model="extForm.dataPolicies.autoArchive" /></el-form-item>
            </el-form>
          </template>
          <!-- 数据备份（设置 / 查看 / 恢复 / 删除） -->
          <template v-else-if="activeDim === 'backups'">
            <el-form label-width="90px">
              <el-form-item label="自动备份"><el-switch v-model="extForm.backups.autoBackup" /></el-form-item>
              <el-form-item label="备份频率">
                <el-select v-model="extForm.backups.frequency" style="width:160px">
                  <el-option label="每天" value="daily" /><el-option label="每周" value="weekly" /><el-option label="每月" value="monthly" />
                </el-select>
              </el-form-item>
            </el-form>
            <el-divider content-position="left">备份记录</el-divider>
            <el-button size="small" type="primary" plain @click="handleBackupNow">立即备份当前配置</el-button>
            <el-table :data="extForm.backups.snapshots || []" size="small" style="margin-top:8px" max-height="200">
              <el-table-column prop="at" label="备份时间" min-width="150" />
              <el-table-column label="操作" width="140">
                <template #default="{ $index }">
                  <el-button link type="primary" size="small" @click="handleRestoreBackup(extForm.backups.snapshots[$index])">恢复</el-button>
                  <el-button link type="danger" size="small" @click="handleDeleteBackup($index)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </template>
          <!-- 设置/修改 = 保存；删除 = 清除配置；查看 = 左侧切换即加载 -->
          <div class="ext-actions">
            <el-button type="primary" size="small" @click="saveDim(activeDim)">保存配置</el-button>
            <el-button size="small" type="danger" plain @click="clearDim(activeDim)">清除配置</el-button>
          </div>
        </div>
      </div>
      <template #footer><el-button @click="showExtDialog=false">关闭</el-button></template>
    </el-dialog>

    <!-- 业务流对话测试弹窗：录制/查看/运行/删除 -->
    <el-dialog v-model="showWfTestDialog" title="对话测试 - 测试案例" width="760px">
      <div style="display:flex;gap:8px;margin-bottom:12px">
        <el-button size="small" type="primary" @click="wfTestForm = { name: '', query: '', expectedOutput: '' }; showWfTestCreate = true">录制测试案例</el-button>
      </div>
      <el-table :data="wfTestList" stripe size="small" v-loading="wfTestLoading">
        <el-table-column prop="name" label="案例名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="query" label="测试输入" show-overflow-tooltip min-width="160" />
        <el-table-column prop="expectedOutput" label="期望输出" show-overflow-tooltip min-width="150" />
        <el-table-column prop="actualOutput" label="实际输出" show-overflow-tooltip min-width="150" />
        <el-table-column label="结果" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.matched !== null && row.matched !== undefined" :type="row.matched === 1 ? 'success' : 'danger'" size="small">{{ row.matched === 1 ? '通过' : '未通过' }}</el-tag>
            <span v-else style="color:#c0c4cc;font-size:12px">未运行</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleViewWfTest(row)">查看</el-button>
            <el-button link type="success" size="small" :loading="wfRunningId === row.id" @click="handleRunWfTest(row)">运行</el-button>
            <el-button link type="danger" size="small" @click="handleDeleteWfTest(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!wfTestList.length && !wfTestLoading" description="暂无测试案例（点击「录制测试案例」开始）" :image-size="60" />
    </el-dialog>

    <!-- 录制测试案例弹窗（真实执行业务流） -->
    <el-dialog v-model="showWfTestCreate" title="录制测试案例" width="520px" :close-on-click-modal="false">
      <el-form label-width="90px">
        <el-form-item label="案例名称"><el-input v-model="wfTestForm.name" placeholder="留空自动按输入生成" /></el-form-item>
        <el-form-item label="测试输入" required><el-input v-model="wfTestForm.query" type="textarea" :rows="2" placeholder="输入将真实执行当前业务流" /></el-form-item>
        <el-form-item label="期望输出"><el-input v-model="wfTestForm.expectedOutput" type="textarea" :rows="2" placeholder="可选，用于判定运行是否通过" /></el-form-item>
        <p style="font-size:12px;color:#909399">保存时会真实执行业务流并回填实际输出</p>
      </el-form>
      <template #footer><el-button @click="showWfTestCreate=false">取消</el-button><el-button type="primary" :loading="wfTestCreateLoading" @click="handleRecordWfTest">执行并保存</el-button></template>
    </el-dialog>

    <!-- 测试案例详情 -->
    <el-dialog v-model="showWfTestView" :title="'案例详情：' + (viewingWfTest?.name || '')" width="560px">
      <el-descriptions v-if="viewingWfTest" :column="1" border size="small">
        <el-descriptions-item label="测试输入">{{ viewingWfTest.query || '-' }}</el-descriptions-item>
        <el-descriptions-item label="期望输出">{{ viewingWfTest.expectedOutput || '-' }}</el-descriptions-item>
        <el-descriptions-item label="实际输出">{{ viewingWfTest.actualOutput || '-' }}</el-descriptions-item>
        <el-descriptions-item label="结果">
          <el-tag v-if="viewingWfTest.matched !== null && viewingWfTest.matched !== undefined" :type="viewingWfTest.matched === 1 ? 'success' : 'danger'" size="small">{{ viewingWfTest.matched === 1 ? '通过' : '未通过' }}</el-tag>
          <span v-else>未运行</span>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ viewingWfTest.createdAt || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer><el-button type="primary" @click="showWfTestView=false">关闭</el-button></template>
    </el-dialog>

    <!-- 业务流对话调试弹窗 -->
    <el-dialog v-model="showWfDebugDialog" title="对话调试" width="720px">
      <div style="display:flex;gap:12px;align-items:center;margin-bottom:12px">
        <span style="font-size:13px">调试级别：</span>
        <el-select v-model="wfDebug.level" style="width:140px">
          <el-option label="DEBUG" value="debug" /><el-option label="INFO" value="info" />
          <el-option label="WARN" value="warn" /><el-option label="ERROR" value="error" />
        </el-select>
        <el-button size="small" type="primary" @click="handleSaveWfLevel">保存级别</el-button>
        <el-button size="small" @click="loadWfDebug">刷新日志</el-button>
        <el-button size="small" @click="handleExportWfDebug">导出日志</el-button>
        <el-button size="small" type="danger" plain @click="handleClearWfDebug">清理日志</el-button>
      </div>
      <div style="border:1px solid #ebeef5;border-radius:8px;padding:12px;max-height:380px;overflow-y:auto;background:#0d1b2a;color:#a7f3d0;font-family:monospace;font-size:12px" v-loading="wfDebugLoading">
        <div v-if="!wfDebug.logs.length" style="color:#64748b;text-align:center;padding:40px">暂无调试日志（执行业务流或节点测试后生成）</div>
        <div v-for="(g, i) in wfDebug.logs" :key="g.id ?? i" style="line-height:1.7;white-space:pre-wrap;word-break:break-all">
          [{{ g.createdAt }}] [{{ (g.level || 'debug').toUpperCase() }}] {{ g.nodeKey ? '[' + g.nodeKey + '] ' : '' }}{{ g.message }}
        </div>
      </div>
    </el-dialog>

    <!-- 业务流知识更新弹窗 -->
    <el-dialog v-model="showWfUpdateDialog" title="知识更新" width="720px">
      <div style="display:flex;gap:8px;margin-bottom:12px;align-items:center">
        <el-select v-model="wfKbId" placeholder="选择知识库" style="width:240px" @change="loadWfUpdateLogs">
          <el-option v-for="kb in wfKbList" :key="kb.id" :label="kb.name" :value="kb.id" />
        </el-select>
        <el-button size="small" type="primary" @click="handleWfManualUpdate">手动更新</el-button>
        <el-button size="small" @click="showWfAutoConfig = true">设置自动更新</el-button>
      </div>
      <el-table :data="wfUpdateLogs" stripe size="small" v-loading="wfUpdateLoading" max-height="360">
        <el-table-column prop="updateType" label="类型" width="90"><template #default="{ row }">{{ ({ auto: '自动', manual: '手动', incremental: '增量' } as Record<string, string>)[row.updateType] || row.updateType }}</template></el-table-column>
        <el-table-column prop="target" label="目标" width="130" show-overflow-tooltip />
        <el-table-column prop="detail" label="详情" min-width="200" show-overflow-tooltip />
        <el-table-column prop="operator" label="操作人" width="90" />
        <el-table-column prop="timestamp" label="时间" width="160" />
        <el-table-column label="操作" width="80"><template #default="{ row }"><el-button link type="primary" size="small" @click="handleWfCompare(row)">比较</el-button></template></el-table-column>
      </el-table>
      <el-empty v-if="!wfUpdateLogs.length && !wfUpdateLoading" description="暂无更新记录（手动更新后可见）" :image-size="60" />
    </el-dialog>

    <!-- 自动更新配置 -->
    <el-dialog v-model="showWfAutoConfig" title="设置自动更新" width="440px">
      <el-form label-width="110px">
        <el-form-item label="启用自动更新"><el-switch v-model="wfAutoConfig.enabled" /></el-form-item>
        <el-form-item label="调度表达式"><el-input v-model="wfAutoConfig.schedule" placeholder="cron 表达式" /></el-form-item>
        <el-form-item label="增量更新"><el-switch v-model="wfAutoConfig.incremental" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showWfAutoConfig=false">取消</el-button><el-button type="primary" @click="handleWfSaveAutoConfig">保存</el-button></template>
    </el-dialog>

    <!-- 业务流对话优化弹窗 -->
    <el-dialog v-model="showWfOptDialog" title="对话优化" width="760px">
      <div style="display:flex;gap:8px;margin-bottom:12px;align-items:center">
        <el-button size="small" type="primary" @click="handleAnalyzeWf">分析对话数据</el-button>
        <div v-if="wfAnalyze" style="font-size:12px;color:#606266;display:flex;gap:12px">
          <span>节点 {{ wfAnalyze.nodeCount }}</span><span>案例 {{ wfAnalyze.testCaseCount }}</span>
          <span>通过率 {{ wfAnalyze.testCasePassRate }}%</span><span>待应用优化 {{ wfAnalyze.pendingOptimizations }}</span>
        </div>
      </div>
      <div style="display:flex;gap:8px;margin-bottom:12px">
        <el-input v-model="wfOptForm.name" placeholder="优化建议名称，如：压缩检索节点提示词" style="width:260px" />
        <el-select v-model="wfOptForm.suggestionType" style="width:130px">
          <el-option label="流程优化" value="flow" /><el-option label="提示词优化" value="prompt" />
          <el-option label="性能优化" value="performance" /><el-option label="准确性优化" value="accuracy" />
        </el-select>
        <el-button type="primary" @click="handleCreateWfOpt">新增建议</el-button>
        <el-button @click="handleExportWfOpts">导出报告</el-button>
      </div>
      <el-input v-model="wfOptForm.description" type="textarea" :rows="2" placeholder="建议说明（可选）" style="margin-bottom:12px" />
      <el-table :data="wfOptList" stripe size="small" v-loading="wfOptLoading">
        <el-table-column prop="name" label="建议名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="suggestionType" label="类型" width="100" />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'applied' ? 'success' : row.status === 'tested' ? 'primary' : 'warning'" size="small">{{ ({ pending: '待应用', applied: '已应用', tested: '已测试' } as Record<string, string>)[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="160" />
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button link type="success" size="small" @click="handleApplyWfOpt(row)">应用</el-button>
            <el-button link type="primary" size="small" :loading="wfTestingOptId === row.id" @click="handleTestWfOpt(row)">测试效果</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!wfOptList.length && !wfOptLoading" description="暂无优化建议（先「分析对话数据」再新增）" :image-size="60" />
    </el-dialog>
    <!-- 业务流配置迁移弹窗：查看迁移日志/进度/设置迁移策略 -->
    <el-dialog v-model="showWfMigDialog" title="配置迁移" width="780px">
      <div class="card-panel" style="margin-bottom:16px">
        <div class="section-title" style="margin-bottom:10px">设置迁移策略并执行（源：{{ wfName || '当前业务流' }}）</div>
        <el-form inline>
          <el-form-item label="目标业务流">
            <el-select v-model="wfMigForm.targetWorkflowId" filterable placeholder="选择目标业务流" style="width:200px">
              <el-option v-for="w in workflowList.filter(x => x.id !== wfId)" :key="w.id" :label="w.name" :value="w.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="目标环境">
            <el-select v-model="wfMigForm.targetEnv" style="width:110px">
              <el-option label="测试环境" value="test" /><el-option label="生产环境" value="production" />
            </el-select>
          </el-form-item>
          <el-form-item label="迁移策略">
            <el-radio-group v-model="wfMigForm.strategy">
              <el-radio value="overwrite">覆盖</el-radio>
              <el-radio value="merge">合并（保留已有）</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="wfMigSubmitting" @click="handleCreateWfMig">开始迁移</el-button>
          </el-form-item>
        </el-form>
      </div>
      <div class="section-title" style="margin-bottom:8px">迁移日志</div>
      <el-table :data="wfMigList" stripe size="small" v-loading="wfMigLoading">
        <el-table-column label="源 → 目标" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ wfNameById(row.sourceWorkflowId) }} → {{ wfNameById(row.targetWorkflowId) }}</template>
        </el-table-column>
        <el-table-column label="目标环境" width="90"><template #default="{ row }">{{ row.targetEnv === 'production' ? '生产' : '测试' }}</template></el-table-column>
        <el-table-column label="策略" width="80"><template #default="{ row }">{{ row.strategy === 'merge' ? '合并' : '覆盖' }}</template></el-table-column>
        <el-table-column label="进度" width="150">
          <template #default="{ row }"><el-progress :percentage="row.progress || 0" :status="row.status === 'failed' ? 'exception' : (row.progress >= 100 ? 'success' : undefined)" :stroke-width="10" /></template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }"><el-tag :type="wfMigStatusColor(row.status)" size="small">{{ wfMigStatus(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="validateResult" label="迁移结果" min-width="160" show-overflow-tooltip />
        <el-table-column prop="operator" label="操作人" width="80" />
        <el-table-column prop="createdAt" label="时间" width="160" />
      </el-table>
      <el-empty v-if="!wfMigList.length && !wfMigLoading" description="暂无迁移记录" :image-size="60" />
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

// ====== 测试结果 / 节点日志 ======
.result-pre { background: #f5f7fa; padding: 8px 10px; border-radius: 6px; font-size: 12px; max-height: 220px; overflow: auto; margin: 8px 0 0; }
.log-panel { background: #1e1e28; color: #d4d4d4; border-radius: 6px; padding: 10px 12px; max-height: 300px; overflow: auto; font-family: Consolas, Menlo, monospace; font-size: 12px; }
.log-line { line-height: 1.7; white-space: pre-wrap; word-break: break-all; }

// ====== 扩展配置弹窗 ======
.ext-layout { display: flex; gap: 12px; min-height: 320px; }
.ext-menu { width: 140px; flex-shrink: 0; border-right: 1px solid #ebeef5; padding-right: 8px; }
.ext-menu-item {
  padding: 8px 10px; border-radius: 6px; cursor: pointer; font-size: 13px; margin-bottom: 2px;
  &:hover { background: #f0f5ff; color: #409eff; }
  &.active { background: #409eff; color: #fff; }
}
.ext-form { flex: 1; min-width: 0; }
.ext-actions { margin-top: 12px; display: flex; gap: 8px; }
.kv-row { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
</style>
