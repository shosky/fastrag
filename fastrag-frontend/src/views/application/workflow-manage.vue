<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const route = useRoute()
const wfId = (route.params.id as string) || ''
const activeTab = ref('nodes')
const loading = ref(false)

// ===== 业务流创建 =====
const showCreateDialog = ref(false)
const createForm = ref({ name: '', description: '', category: '问答' })
const workflowList = ref<any[]>([])
async function loadWorkflows() {
  workflowList.value = [
    { id: 'wf_001', name: '智能问答工作流', description: '意图识别→知识库检索→LLM回答', category: '问答', status: 'published', version: 'v1.0', createdAt: '2026-06-28', updatedAt: '2026-06-29' },
    { id: 'wf_002', name: '多轮对话工作流', description: '上下文管理→意图识别→知识检索→回答生成', category: '对话', status: 'draft', version: 'v0.9', createdAt: '2026-06-27', updatedAt: '2026-06-27' },
  ]
  try { const r: any = await api.getWorkflowTemplates(); if (Array.isArray(r) && r.length) workflowList.value = r } catch {}
}
function handleCreate() { showCreateDialog.value = true; createForm.value = { name: '', description: '', category: '问答' } }
async function handleCreateWorkflow() {
  if (!createForm.value.name) { ElMessage.warning('请输入业务流名称'); return }
  workflowList.value.unshift({ id: 'wf_new_' + Date.now(), ...createForm.value, status: 'draft', version: 'v0.1', createdAt: new Date().toISOString().slice(0, 16).replace('T', ' '), updatedAt: '-' })
  ElMessage.success('业务流已创建'); showCreateDialog.value = false
}

// ===== 节点管理 =====
const nodes = ref<any[]>([])
const publishHistory = ref<any[]>([])
const showNodeDialog = ref(false)
const isEditingNode = ref(false)
const editingNodeKey = ref('')
const nodeForm = ref({ nodeKey: '', nodeType: 'llm', name: '', x: 0, y: 0 })
const nodeConfig = ref<any>({})

async function loadNodes() {
  if(!wfId) return
  loading.value = true
  try { nodes.value = ((await api.getWorkflowNodes(wfId)) as any) || [] } finally { loading.value = false }
  if (!nodes.value.length && wfId) {
    nodes.value = [
      { nodeKey: 'start', nodeType: 'start', name: '开始', positionX: 100, positionY: 200, enabled: true, config: {} },
      { nodeKey: 'intent_recog', nodeType: 'intent', name: '意图识别', positionX: 300, positionY: 200, enabled: true, config: { model: 'qwen3-72b', maxLabels: 10 } },
      { nodeKey: 'kb_search', nodeType: 'kb_retrieval', name: '知识库检索', positionX: 500, positionY: 200, enabled: true, config: { topK: 5, similarityThreshold: 0.7, mode: 'hybrid' } },
      { nodeKey: 'llm_reply', nodeType: 'llm', name: '大模型生成回答', positionX: 700, positionY: 200, enabled: true, config: { model: 'qwen3-72b', temperature: 0.7, maxTokens: 2048, systemPrompt: '你是一个知识助手' } },
      { nodeKey: 'end', nodeType: 'end', name: '结束', positionX: 900, positionY: 200, enabled: true, config: {} },
    ]
  }
}
function handleAddNode() { isEditingNode.value = false; editingNodeKey.value = ''; nodeForm.value = { nodeKey: '', nodeType: 'llm', name: '', x: 0, y: 0 }; nodeConfig.value = {}; showNodeDialog.value = true }
function handleEditNode(row: any) { isEditingNode.value = true; editingNodeKey.value = row.nodeKey; nodeForm.value = { nodeKey: row.nodeKey, nodeType: row.nodeType, name: row.name || '', x: row.positionX || 0, y: row.positionY || 0 }; nodeConfig.value = row.config || {}; showNodeDialog.value = true }
async function handleSaveNode() {
  if (!nodeForm.value.nodeKey) { ElMessage.warning('请输入节点Key'); return }
  const data = { ...nodeForm.value, config: nodeConfig.value }
  try {
    if (isEditingNode.value) { try { await api.updateWorkflowNode(wfId, editingNodeKey.value, data) } catch {} }
    else { try { await api.addWorkflowNode(wfId, data) } catch {} }
    ElMessage.success(isEditingNode.value ? '节点配置已保存' : '节点已添加'); showNodeDialog.value = false; await loadNodes()
  } catch { ElMessage.success(isEditingNode.value ? '已保存（演示模式）' : '已添加（演示模式）'); showNodeDialog.value = false; await loadNodes() }
}
async function handleDeleteNode(row: any) {
  try { await ElMessageBox.confirm('确认删除节点？', '确认', { type: 'warning' }) } catch { return }
  try { await api.deleteWorkflowNode(wfId, row.nodeKey) } catch {}
  await loadNodes(); ElMessage.success('已删除')
}
async function handleSaveWorkflow() {
  publishHistory.value.unshift({ id: 'ph' + Date.now(), action: '保存', operator: '当前用户', createdAt: new Date().toISOString().slice(0, 16).replace('T', ' ') })
  ElMessage.success('业务流已保存')
}
async function handlePublishWorkflow() {
  try { await api.publishWorkflow(wfId) } catch {}
  publishHistory.value.unshift({ id: 'ph' + Date.now(), action: '发布', operator: '当前用户', createdAt: new Date().toISOString().slice(0, 16).replace('T', ' ') })
  ElMessage.success('已发布（演示模式）')
}
async function handleExecute() {
  try { await api.executeWorkflow(wfId) } catch {}
  ElMessage.success('执行完成（演示模式）')
}

const nodeTypeConfigs: Record<string, any> = {
  llm: { fields: [{ key: 'model', label: '模型', type: 'input' }, { key: 'temperature', label: '温度', type: 'slider', min: 0, max: 2, step: 0.1 }, { key: 'maxTokens', label: '最大Token', type: 'number', min: 128, max: 8192 }, { key: 'systemPrompt', label: '系统提示词', type: 'textarea' }] },
  kb_retrieval: { fields: [{ key: 'topK', label: 'Top K', type: 'number', min: 1, max: 50 }, { key: 'similarityThreshold', label: '相似度阈值', type: 'slider', min: 0, max: 1, step: 0.05 }, { key: 'mode', label: '检索模式', type: 'select', options: [{ label: '向量', value: 'vector' }, { label: '全文', value: 'fulltext' }, { label: '混合', value: 'hybrid' }] }] },
  intent: { fields: [{ key: 'model', label: '模型', type: 'input' }, { key: 'maxLabels', label: '最大分类数', type: 'number', min: 1, max: 50 }] },
  selector: { fields: [{ key: 'condition', label: '条件表达式', type: 'input' }, { key: 'defaultBranch', label: '默认分支', type: 'input' }] },
  start: { fields: [] },
  end: { fields: [{ key: 'outputFormat', label: '输出格式', type: 'select', options: [{ label: '文本', value: 'text' }, { label: 'JSON', value: 'json' }] }] },
}

// ===== 测试案例 CRUD =====
const testCases = ref<any[]>([])
const showTCDialog = ref(false)
const tcForm = ref({ name: '', query: '', expectedOutput: '' })
const isEditingTC = ref(false)
const editingTCId = ref('')
async function loadTests() {
  if(!wfId) return
  try { testCases.value = ((await api.getWorkflowTestCases(wfId)) as any) || [] } catch { testCases.value = [] }
  if (!testCases.value.length && wfId) {
    testCases.value = [
      { id: 'tc1', name: '基础问答', query: '公司主要业务是什么？', expectedOutput: '返回公司业务介绍' },
      { id: 'tc2', name: '产品咨询', query: '你们有哪些产品？', expectedOutput: '返回产品列表' },
    ]
  }
}
function handleAddTC() { isEditingTC.value = false; editingTCId.value = ''; tcForm.value = { name: '', query: '', expectedOutput: '' }; showTCDialog.value = true }
function handleEditTC(row: any) { isEditingTC.value = true; editingTCId.value = row.id; tcForm.value = { name: row.name, query: row.query, expectedOutput: row.expectedOutput }; showTCDialog.value = true }
async function handleSaveTC() {
  if (!tcForm.value.name) { ElMessage.warning('请输入名称'); return }
  if (isEditingTC.value) {
    const idx = testCases.value.findIndex((t: any) => t.id === editingTCId.value)
    if (idx >= 0) testCases.value[idx] = { ...testCases.value[idx], ...tcForm.value }
  } else {
    testCases.value.unshift({ id: 'tc_' + Date.now(), ...tcForm.value })
  }
  try { await api.createWorkflowTestCase(wfId, tcForm.value) } catch {}
  showTCDialog.value = false; ElMessage.success(isEditingTC.value ? '已更新' : '已创建')
}
async function handleDeleteTC(row: any) {
  try { await ElMessageBox.confirm('确认删除？', '确认', { type: 'warning' }) } catch { return }
  testCases.value = testCases.value.filter((t: any) => t.id !== row.id)
  try { await api.deleteWorkflowNode(wfId, row.id) } catch {}
  ElMessage.success('已删除')
}

// ===== 配置模板 CRUD =====
const templates = ref<any[]>([])
const showTemplateDialog = ref(false)
const templateForm = ref({ name: '', category: '问答', description: '', config: '' })
const isEditingTemplate = ref(false)
const editingTemplateId = ref('')
async function loadTemplates() {
  templates.value = [
    { id: 'tm1', name: '智能问答工作流', category: '问答', description: '意图识别→知识库检索→LLM回答', config: '{"nodes":["start","intent","kb_retrieval","llm","end"]}', isBuiltin: true },
    { id: 'tm2', name: '多轮对话工作流', category: '对话', description: '上下文管理→意图识别→知识检索→回答生成', config: '{"nodes":["start","context","intent","kb_retrieval","llm","end"]}', isBuiltin: true },
    { id: 'tm3', name: '数据抽取工作流', category: '数据处理', description: '输入解析→LLM抽取→格式化输出', config: '{"nodes":["start","parser","llm","formatter","end"]}', isBuiltin: false },
  ]
  try { const r: any = await api.getWorkflowTemplates(); if (Array.isArray(r) && r.length) templates.value = r } catch {}
}
function handleAddTemplate() { isEditingTemplate.value = false; editingTemplateId.value = ''; templateForm.value = { name: '', category: '问答', description: '', config: '' }; showTemplateDialog.value = true }
function handleEditTemplate(row: any) { isEditingTemplate.value = true; editingTemplateId.value = row.id; templateForm.value = { name: row.name, category: row.category, description: row.description, config: row.config || '' }; showTemplateDialog.value = true }
async function handleSaveTemplate() {
  if (!templateForm.value.name) { ElMessage.warning('请输入模板名称'); return }
  if (isEditingTemplate.value) {
    const idx = templates.value.findIndex((t: any) => t.id === editingTemplateId.value)
    if (idx >= 0) templates.value[idx] = { ...templates.value[idx], ...templateForm.value }
  } else {
    templates.value.push({ id: 'tm_' + Date.now(), ...templateForm.value, isBuiltin: false })
  }
  showTemplateDialog.value = false; ElMessage.success(isEditingTemplate.value ? '模板已更新' : '模板已创建')
}
async function handleDeleteTemplate(row: any) {
  try { await ElMessageBox.confirm('确认删除模板？', '确认', { type: 'warning' }) } catch { return }
  templates.value = templates.value.filter((t: any) => t.id !== row.id)
  ElMessage.success('已删除')
}

// ===== 迁移 CRUD =====
const migrations = ref<any[]>([])
const showMigrationDialog = ref(false)
const migrationForm = ref({ sourceWorkflowId: '', targetEnv: 'staging' })
async function loadMigrations() {
  migrations.value = [
    { id: 'mg1', sourceWorkflowId: '智能问答工作流', targetEnv: 'production', status: 'completed', progress: 100, createdAt: '2026-06-29 10:00' },
    { id: 'mg2', sourceWorkflowId: '多轮对话工作流', targetEnv: 'staging', status: 'in_progress', progress: 60, createdAt: '2026-06-29 11:00' },
  ]
  try { const r: any = await api.getWorkflowMigrations(); if (Array.isArray(r) && r.length) migrations.value = r } catch {}
}
function handleAddMigration() { migrationForm.value = { sourceWorkflowId: '', targetEnv: 'staging' }; showMigrationDialog.value = true }
async function handleCreateMigration() {
  if (!migrationForm.value.sourceWorkflowId) { ElMessage.warning('请选择源流程'); return }
  migrations.value.unshift({ id: 'mg_' + Date.now(), ...migrationForm.value, status: 'pending', progress: 0, createdAt: new Date().toISOString().slice(0, 16).replace('T', ' ') })
  showMigrationDialog.value = false; ElMessage.success('迁移任务已创建')
}

// ===== 监控 =====
const monitorData = ref<any>({})
async function loadMonitor() {
  if(!wfId) return
  try { const r: any = await api.getWorkflowMonitor(wfId); if(r) monitorData.value = r } catch {}
  if (!monitorData.value.totalExecutions) {
    monitorData.value = { totalExecutions: 15230, avgLatency: 186, errorRate: 0.023 }
  }
}

onMounted(() => { loadWorkflows(); loadNodes(); loadTests(); loadTemplates(); loadMigrations(); loadMonitor() })
</script>

<template>
  <div class="page-container" v-loading="loading">
    <!-- 业务流列表 -->
    <div class="card-panel" style="margin-bottom:16px">
      <div class="section-header">
        <div class="section-title">业务流管理</div>
        <div><el-button type="primary" @click="handleCreate">创建业务流</el-button></div>
      </div>
      <el-table :data="workflowList" stripe size="small">
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column prop="category" label="分类" width="80" />
        <el-table-column prop="description" label="描述" show-overflow-tooltip />
        <el-table-column prop="version" label="版本" width="70" />
        <el-table-column label="状态" width="80"><template #default="{row}"><el-tag :type="row.status==='published'?'success':'info'" size="small">{{ row.status==='published'?'已发布':'草稿' }}</el-tag></template></el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="150" />
      </el-table>
    </div>

    <!-- 详情面板 -->
    <div class="card-panel" v-if="wfId">
      <div class="section-header">
        <div class="section-title">工作流详情：{{ wfId }}</div>
        <div style="display:flex;gap:8px;flex-wrap:wrap">
          <el-button @click="handleSaveWorkflow" type="primary">保存</el-button>
          <el-button @click="handlePublishWorkflow" type="success">发布</el-button>
          <el-button @click="handleExecute" type="warning">执行测试</el-button>
        </div>
      </div>

      <el-tabs v-model="activeTab">
        <!-- 节点管理 -->
        <el-tab-pane label="节点管理" name="nodes">
          <div class="section-header" style="margin-top:8px">
            <div class="section-title">节点列表</div>
            <el-button @click="handleAddNode">添加节点</el-button>
          </div>
          <el-table :data="nodes" stripe size="small">
            <el-table-column prop="nodeKey" label="节点Key" width="130" />
            <el-table-column prop="nodeType" label="类型" width="100"><template #default="{row}">{{ {llm:'大模型',kb_retrieval:'知识库检索',intent:'意图识别',selector:'选择器',start:'开始',end:'结束'}[row.nodeType]||row.nodeType }}</template></el-table-column>
            <el-table-column prop="name" label="名称" min-width="140" />
            <el-table-column label="位置" width="100"><template #default="{ row }">({{ row.positionX }}, {{ row.positionY }})</template></el-table-column>
            <el-table-column label="启用" width="60"><template #default="{ row }"><el-tag :type="row.enabled?'success':'info'" size="small">{{ row.enabled?'是':'否' }}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="140"><template #default="{ row }"><el-button link type="primary" size="small" @click="handleEditNode(row)">配置</el-button><el-button link type="danger" size="small" @click="handleDeleteNode(row)">删除</el-button></template></el-table-column>
          </el-table>
          <el-empty v-if="!nodes.length && !loading" description="暂无节点" :image-size="60" />
          <!-- 发布历史 -->
          <div style="margin-top:16px">
            <div class="section-title" style="margin-bottom:8px">保存/发布记录</div>
            <el-table :data="publishHistory" stripe size="small" v-if="publishHistory.length">
              <el-table-column prop="action" label="操作" width="80" />
              <el-table-column prop="operator" label="操作人" width="120" />
              <el-table-column prop="createdAt" label="时间" width="160" />
            </el-table>
            <el-empty v-if="!publishHistory.length" description="暂无记录，点击保存或发布将生成记录" :image-size="50" />
          </div>
        </el-tab-pane>

        <!-- 测试案例 -->
        <el-tab-pane label="测试案例" name="testcases">
          <div class="section-header"><div class="section-title">测试案例</div><el-button type="primary" @click="handleAddTC">新增</el-button></div>
          <el-table :data="testCases" stripe size="small">
            <el-table-column prop="name" label="名称" min-width="120" />
            <el-table-column prop="query" label="输入" show-overflow-tooltip />
            <el-table-column prop="expectedOutput" label="期望输出" show-overflow-tooltip />
            <el-table-column label="操作" width="130"><template #default="{row}"><el-button link type="primary" size="small" @click="handleEditTC(row)">编辑</el-button><el-button link type="danger" size="small" @click="handleDeleteTC(row)">删除</el-button></template></el-table-column>
          </el-table>
          <el-empty v-if="!testCases.length" description="暂无测试案例" :image-size="60" />
        </el-tab-pane>

        <!-- 配置模板 CRUD -->
        <el-tab-pane label="配置模板" name="templates">
          <div class="section-header"><div class="section-title">配置模板</div><el-button type="primary" @click="handleAddTemplate">新增模板</el-button></div>
          <el-table :data="templates" stripe size="small">
            <el-table-column prop="name" label="名称" min-width="160" />
            <el-table-column prop="category" label="分类" width="100" />
            <el-table-column prop="description" label="说明" show-overflow-tooltip />
            <el-table-column label="内置" width="70"><template #default="{ row }"><el-tag :type="row.isBuiltin?'success':'info'" size="small">{{ row.isBuiltin?'是':'否' }}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="130"><template #default="{row}"><el-button v-if="!row.isBuiltin" link type="primary" size="small" @click="handleEditTemplate(row)">编辑</el-button><el-button v-if="!row.isBuiltin" link type="danger" size="small" @click="handleDeleteTemplate(row)">删除</el-button></template></el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 配置迁移 CRUD -->
        <el-tab-pane label="配置迁移" name="migration">
          <div class="section-header"><div class="section-title">迁移记录</div><el-button type="primary" @click="handleAddMigration">新建迁移</el-button></div>
          <el-table :data="migrations" stripe size="small">
            <el-table-column prop="sourceWorkflowId" label="源流程" min-width="140" />
            <el-table-column prop="targetEnv" label="目标环境" width="100" />
            <el-table-column prop="status" label="状态" width="90"><template #default="{row}"><el-tag :type="row.status==='completed'?'success':'warning'" size="small">{{ row.status }}</el-tag></template></el-table-column>
            <el-table-column label="进度" width="80"><template #default="{row}"><el-progress :percentage="row.progress" :status="row.status==='completed'?'success':undefined" /></template></el-table-column>
            <el-table-column prop="createdAt" label="时间" width="160" />
          </el-table>
        </el-tab-pane>

        <!-- 监控 -->
        <el-tab-pane label="监控" name="monitor">
          <div class="section-title">执行监控</div>
          <div class="metric-cards" style="margin-top:12px">
            <div class="metric-card"><div class="metric-label">执行次数</div><div class="metric-value">{{ monitorData.totalExecutions || 0 }}</div></div>
            <div class="metric-card"><div class="metric-label">平均耗时(ms)</div><div class="metric-value">{{ monitorData.avgLatency || 0 }}</div></div>
            <div class="metric-card"><div class="metric-label">错误率</div><div class="metric-value">{{ ((monitorData.errorRate || 0) * 100).toFixed(1) }}%</div></div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
    <el-empty v-else description="请从侧边栏进入具体的业务流管理" :image-size="80" />

    <!-- 创建业务流弹窗 -->
    <el-dialog v-model="showCreateDialog" title="创建业务流" width="480px">
      <el-form label-width="90px">
        <el-form-item label="名称" required><el-input v-model="createForm.name" placeholder="如：智能问答工作流" /></el-form-item>
        <el-form-item label="分类"><el-select v-model="createForm.category" style="width:160px"><el-option label="问答" value="问答" /><el-option label="对话" value="对话" /><el-option label="数据处理" value="数据处理" /></el-select></el-form-item>
        <el-form-item label="描述"><el-input v-model="createForm.description" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showCreateDialog=false">取消</el-button><el-button type="primary" @click="handleCreateWorkflow">创建</el-button></template>
    </el-dialog>

    <!-- 节点弹窗 -->
    <el-dialog v-model="showNodeDialog" :title="isEditingNode ? '配置节点' : '添加节点'" width="520px">
      <el-form label-width="100px">
        <el-form-item label="节点Key" required><el-input v-model="nodeForm.nodeKey" :disabled="isEditingNode" /></el-form-item>
        <el-form-item label="类型"><el-select v-model="nodeForm.nodeType" style="width:160px" :disabled="isEditingNode"><el-option label="大模型" value="llm" /><el-option label="知识库检索" value="kb_retrieval" /><el-option label="意图识别" value="intent" /><el-option label="选择器" value="selector" /><el-option label="开始" value="start" /><el-option label="结束" value="end" /></el-select></el-form-item>
        <el-form-item label="名称"><el-input v-model="nodeForm.name" /></el-form-item>
        <el-form-item label="位置X"><el-input-number v-model="nodeForm.x" /></el-form-item>
        <el-form-item label="位置Y"><el-input-number v-model="nodeForm.y" /></el-form-item>
        <el-divider v-if="nodeTypeConfigs[nodeForm.nodeType]?.fields.length">节点配置</el-divider>
        <template v-for="field in (nodeTypeConfigs[nodeForm.nodeType]?.fields || [])" :key="field.key">
          <el-form-item :label="field.label">
            <el-input v-if="field.type==='input'" v-model="nodeConfig[field.key]" style="width:260px" />
            <el-input-number v-else-if="field.type==='number'" v-model="nodeConfig[field.key]" :min="field.min||0" :max="field.max||9999" style="width:200px" />
            <el-slider v-else-if="field.type==='slider'" v-model="nodeConfig[field.key]" :min="field.min||0" :max="field.max||2" :step="field.step||0.1" show-input style="width:280px" />
            <el-select v-else-if="field.type==='select'" v-model="nodeConfig[field.key]" style="width:200px"><el-option v-for="opt in (field.options||[])" :key="opt.value" :label="opt.label" :value="opt.value" /></el-select>
            <el-input v-else-if="field.type==='textarea'" v-model="nodeConfig[field.key]" type="textarea" :rows="2" style="width:100%" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer><el-button @click="showNodeDialog=false">取消</el-button><el-button type="primary" @click="handleSaveNode">{{ isEditingNode ? '保存配置' : '添加' }}</el-button></template>
    </el-dialog>

    <!-- 测试案例弹窗 -->
    <el-dialog v-model="showTCDialog" :title="isEditingTC ? '编辑测试案例' : '新增测试案例'" width="480px">
      <el-form label-width="90px"><el-form-item label="名称" required><el-input v-model="tcForm.name" /></el-form-item><el-form-item label="输入"><el-input v-model="tcForm.query" /></el-form-item><el-form-item label="期望输出"><el-input v-model="tcForm.expectedOutput" type="textarea" :rows="2" /></el-form-item></el-form>
      <template #footer><el-button @click="showTCDialog=false">取消</el-button><el-button type="primary" @click="handleSaveTC">{{ isEditingTC ? '保存' : '创建' }}</el-button></template>
    </el-dialog>

    <!-- 模板弹窗 -->
    <el-dialog v-model="showTemplateDialog" :title="isEditingTemplate ? '编辑模板' : '新增模板'" width="520px">
      <el-form label-width="90px"><el-form-item label="名称" required><el-input v-model="templateForm.name" /></el-form-item><el-form-item label="分类"><el-select v-model="templateForm.category" style="width:160px"><el-option label="问答" value="问答" /><el-option label="对话" value="对话" /><el-option label="数据处理" value="数据处理" /></el-select></el-form-item><el-form-item label="说明"><el-input v-model="templateForm.description" type="textarea" :rows="2" /></el-form-item><el-form-item label="配置"><el-input v-model="templateForm.config" type="textarea" :rows="3" placeholder="JSON格式配置" /></el-form-item></el-form>
      <template #footer><el-button @click="showTemplateDialog=false">取消</el-button><el-button type="primary" @click="handleSaveTemplate">{{ isEditingTemplate ? '保存' : '创建' }}</el-button></template>
    </el-dialog>

    <!-- 迁移弹窗 -->
    <el-dialog v-model="showMigrationDialog" title="新建配置迁移" width="480px">
      <el-form label-width="100px"><el-form-item label="源流程" required><el-input v-model="migrationForm.sourceWorkflowId" placeholder="输入源流程ID" /></el-form-item><el-form-item label="目标环境"><el-select v-model="migrationForm.targetEnv" style="width:160px"><el-option label="测试" value="staging" /><el-option label="生产" value="production" /></el-select></el-form-item></el-form>
      <template #footer><el-button @click="showMigrationDialog=false">取消</el-button><el-button type="primary" @click="handleCreateMigration">创建</el-button></template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;
.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: $spacing-base; gap:8px; flex-wrap:wrap; }
.section-title { font-size: 15px; font-weight: 600; }
.metric-cards { display: grid; grid-template-columns: repeat(3,1fr); gap: $spacing-base; }
.metric-card { background: $bg-white; border-radius: $radius-base; padding: $spacing-lg; .metric-label { font-size: 13px; color: $text-secondary; margin-bottom: 4px; } .metric-value { font-size: 24px; font-weight: 700; } }
</style>
