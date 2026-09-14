<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const loading = ref(false)
const activeTab = ref('security')

// ============================================================================
// 1. 安全策略（列表式 CRUD）
// ============================================================================
const policyList = ref<any[]>([])
const showPolicyDialog = ref(false)
const policyForm = ref<any>({})
const editingPolicyId = ref<string | null>(null)

const policyTypeOptions = [
  { label: 'IP 黑名单', value: 'ip_blacklist' },
  { label: 'IP 白名单', value: 'ip_whitelist' },
  { label: '访问频率限制', value: 'rate_limit' },
  { label: '敏感内容过滤', value: 'sensitive_filter' },
  { label: '登录安全策略', value: 'login_policy' },
  { label: '数据脱敏规则', value: 'data_masking' },
]
const policyActionOptions = [
  { label: '允许', value: 'allow' },
  { label: '拒绝', value: 'block' },
  { label: '告警', value: 'alert' },
  { label: '记录日志', value: 'log' },
]

async function loadPolicies() {
  try {
    const res = await api.getSecurityPolicies()
    policyList.value = Array.isArray(res) ? res : (res as any)?.list || []
  } catch {
    policyList.value = []
  }
}

function handleAddPolicy() {
  editingPolicyId.value = null
  policyForm.value = { policyType: 'ip_blacklist', action: 'block', enabled: 1, priority: 0 }
  showPolicyDialog.value = true
}
function handleEditPolicy(row: any) {
  editingPolicyId.value = row.id
  policyForm.value = { ...row, enabled: row.enabled === 1 || row.enabled === true ? 1 : 0 }
  showPolicyDialog.value = true
}
async function handleSavePolicy() {
  if (!policyForm.value.name) { ElMessage.warning('请输入策略名称'); return }
  try {
    if (editingPolicyId.value) {
      await api.updateSecurityPolicy(editingPolicyId.value, policyForm.value)
      ElMessage.success('更新成功')
    } else {
      await api.createSecurityPolicy(policyForm.value)
      ElMessage.success('创建成功')
    }
    showPolicyDialog.value = false
    loadPolicies()
  } catch { ElMessage.error('操作失败') }
}
async function handleDeletePolicy(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除安全策略「${row.name}」？`, '提示', { type: 'warning' })
    await api.deleteSecurityPolicy(row.id)
    ElMessage.success('删除成功')
    loadPolicies()
  } catch {}
}
async function handleTogglePolicy(row: any) {
  try {
    await api.updateSecurityPolicy(row.id, { ...row, enabled: row.enabled ? 0 : 1 })
    row.enabled = row.enabled ? 0 : 1
    ElMessage.success(row.enabled ? '已启用' : '已停用')
  } catch {}
}

// ============================================================================
// 2. 发布策略（列表式 CRUD）
// ============================================================================
const strategyList = ref<any[]>([])
const showStrategyDialog = ref(false)
const strategyForm = ref<any>({})
const editingStrategyId = ref<string | null>(null)

const strategyTypeOptions = [
  { label: '自动发布', value: 'auto_publish' },
  { label: '定时发布', value: 'scheduled_publish' },
  { label: '审核后发布', value: 'review_required' },
  { label: '增量发布', value: 'incremental' },
  { label: '全量发布', value: 'full_publish' },
]

async function loadStrategies() {
  try {
    const res = await api.getPublishStrategies()
    strategyList.value = Array.isArray(res) ? res : (res as any)?.list || []
  } catch {
    strategyList.value = []
  }
}

function handleAddStrategy() {
  editingStrategyId.value = null
  strategyForm.value = { strategyType: 'review_required', enabled: 1, priority: 0, config: '{}' }
  showStrategyDialog.value = true
}
function handleEditStrategy(row: any) {
  editingStrategyId.value = row.id
  strategyForm.value = { ...row, enabled: row.enabled === 1 || row.enabled === true ? 1 : 0 }
  showStrategyDialog.value = true
}
async function handleSaveStrategy() {
  if (!strategyForm.value.name) { ElMessage.warning('请输入策略名称'); return }
  try {
    if (editingStrategyId.value) {
      await api.updatePublishStrategy(editingStrategyId.value, strategyForm.value)
      ElMessage.success('更新成功')
    } else {
      await api.createPublishStrategy(strategyForm.value)
      ElMessage.success('创建成功')
    }
    showStrategyDialog.value = false
    loadStrategies()
  } catch { ElMessage.error('操作失败') }
}
async function handleDeleteStrategy(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除发布策略「${row.name}」？`, '提示', { type: 'warning' })
    await api.deletePublishStrategy(row.id)
    ElMessage.success('删除成功')
    loadStrategies()
  } catch {}
}
async function handleToggleStrategy(row: any) {
  try {
    await api.updatePublishStrategy(row.id, { ...row, enabled: row.enabled ? 0 : 1 })
    row.enabled = row.enabled ? 0 : 1
    ElMessage.success(row.enabled ? '已启用' : '已停用')
  } catch {}
}

// ============================================================================
// 3. 配置项管理（文档导读设置 / 配置历史 / 导出导入 / 默认配置 / 重置）
// ============================================================================
const configList = ref<any[]>([])
const configQuery = ref({ configType: '' })
const configTypeOptions = [
  { label: '文档导读', value: 'doc_guide' },
  { label: '发布', value: 'publish' },
  { label: '审核', value: 'review' },
  { label: '通用', value: 'general' },
]
const CONFIG_TYPE_LABEL: Record<string, string> = { doc_guide: '文档导读', publish: '发布', review: '审核', general: '通用' }

async function loadConfigs() {
  try {
    configList.value = ((await api.getSysConfigs(configQuery.value.configType || undefined)) as any) || []
  } catch { configList.value = [] }
}

// 解析 configValue JSON
function parseJsonValue(val: string): any {
  try { return JSON.parse(val) } catch { return val }
}

// 文档导读设置弹窗
const showDocGuideDialog = ref(false)
const docGuideForm = ref({ autoGenerate: true, llmModel: 'qwen3-72b', maxOutlineLevel: 3 })
const LLM_MODEL_OPTIONS = ['qwen3-72b', 'qwen3-32b', 'qwen3-7b', 'bge-m3', 'deepseek-v3'].map(m => ({ label: m, value: m }))

async function handleEditDocGuide() {
  try {
    const configs: any[] = ((await api.getSysConfigs('doc_guide')) as any) || []
    const item = configs.find((c: any) => c.configKey === 'doc_guide')
    if (item) {
      const parsed = parseJsonValue(item.configValue)
      docGuideForm.value = { autoGenerate: parsed.autoGenerate ?? true, llmModel: parsed.llmModel ?? 'qwen3-72b', maxOutlineLevel: parsed.maxOutlineLevel ?? 3 }
    }
    showDocGuideDialog.value = true
  } catch { ElMessage.error('加载文档导读配置失败') }
}

async function handleSaveDocGuide() {
  try {
    await api.updateDocGuideConfig(docGuideForm.value)
    showDocGuideDialog.value = false
    await loadConfigs()
    ElMessage.success('文档导读设置已保存')
  } catch { ElMessage.error('保存失败') }
}

// 配置历史弹窗
const showHistoryDialog = ref(false)
const historyList = ref<any[]>([])
const historyTitle = ref('')

async function handleShowHistory(row: any) {
  historyTitle.value = row.configKey
  try {
    historyList.value = ((await api.getConfigHistory(row.configKey)) as any) || []
    showHistoryDialog.value = true
  } catch { ElMessage.error('加载历史记录失败') }
}

const CHANGE_TYPE_LABEL: Record<string, string> = { create: '创建', update: '修改', reset: '重置' }
const CHANGE_TYPE_TAG: Record<string, string> = { create: 'success', update: 'primary', reset: 'warning' }

// 导出配置
async function handleExportConfig() {
  try {
    const res: any = await api.exportSysConfig(configQuery.value.configType || undefined)
    const items = res?.items || res
    if (!items?.length) { ElMessage.warning('没有可导出的配置'); return }
    const blob = new Blob([JSON.stringify(items, null, 2)], { type: 'application/json;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `sys_config_${new Date().toISOString().slice(0, 10)}.json`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    ElMessage.success(`成功导出 ${Array.isArray(items) ? items.length : 0} 条配置`)
  } catch { ElMessage.error('导出失败') }
}

// 导入配置
const showImportDialog = ref(false)
const importFile = ref<File | null>(null)

function handleImportFileChange(file: any) { importFile.value = file.raw }
async function handleImportConfig() {
  if (!importFile.value) { ElMessage.warning('请先选择文件'); return }
  try {
    const text = await importFile.value.text()
    const items = JSON.parse(text)
    if (!Array.isArray(items)) { ElMessage.error('JSON 格式错误，需要数组'); return }
    const res = await api.importSysConfig({ items })
    showImportDialog.value = false
    importFile.value = null
    await loadConfigs()
    ElMessage.success(`成功导入 ${items.length} 条配置`)
  } catch { ElMessage.error('导入失败，请检查 JSON 格式') }
}

// 设置默认配置
async function handleSetDefault(row: any) {
  try {
    await api.setDefaultConfig(row.configKey)
    await loadConfigs()
    ElMessage.success(`已将「${row.configKey}」设为默认配置`)
  } catch { ElMessage.error('操作失败') }
}

// 查看默认配置弹窗
const showDefaultDialog = ref(false)
const defaultList = ref<any[]>([])
async function handleViewDefaults() {
  try {
    defaultList.value = ((await api.getDefaultConfigs()) as any) || []
    showDefaultDialog.value = true
  } catch { ElMessage.error('加载默认配置失败') }
}

// 重置为默认配置
async function handleResetDefault(row: any) {
  try {
    await ElMessageBox.confirm(`确定将「${row.configKey}」重置为默认值？`, '重置确认', { type: 'warning' })
    await api.resetDefaultConfig(row.configKey)
    await loadConfigs()
    ElMessage.success(`已将「${row.configKey}」重置为默认值`)
  } catch { /* cancelled */ }
}

onMounted(() => { loadPolicies(); loadStrategies(); loadConfigs(); loadKbs(); loadBinding(); loadStatuses() })

// ============================================================================
// 4. 审核流程绑定 + 发布/审核状态
// ============================================================================
const kbOptions = ref<any[]>([])
const templateOptions = ref<any[]>([])
const bindingForm = ref({ kbId: '', templateId: '', enabled: true })
const bindingSaving = ref(false)
const publishStatus = ref<any>(null)
const reviewStatus = ref<any>(null)

async function loadKbs() {
  try {
    const res: any = await api.getKnowledgeBases()
    kbOptions.value = Array.isArray(res) ? res : res?.list || []
  } catch { kbOptions.value = [] }
}
async function loadTemplates(kbId: string) {
  try {
    const res: any = await api.getReviewTemplates(kbId)
    templateOptions.value = Array.isArray(res) ? res : res?.list || []
  } catch { templateOptions.value = [] }
}
async function handleBindingKbChange(kbId: string) { bindingForm.value.templateId = ''; await loadTemplates(kbId) }
async function loadBinding() {
  try {
    const res: any = await api.getReviewFlowBinding()
    const b = typeof res === 'string' ? JSON.parse(res) : (res || {})
    bindingForm.value = { kbId: b.kbId || '', templateId: b.templateId || '', enabled: b.enabled !== false }
    if (bindingForm.value.kbId) await loadTemplates(bindingForm.value.kbId)
  } catch { /* 首次无配置 */ }
}
async function handleSaveBinding() {
  if (!bindingForm.value.kbId) { ElMessage.warning('请选择知识库'); return }
  if (!bindingForm.value.templateId) { ElMessage.warning('请选择审核流程模板'); return }
  bindingSaving.value = true
  try {
    await api.updateReviewFlowBinding({ ...bindingForm.value })
    ElMessage.success('审核流程绑定已保存')
  } catch { ElMessage.error('保存失败') } finally { bindingSaving.value = false }
}
function parseStatus(v: any) { try { return typeof v === 'string' ? JSON.parse(v) : (v || null) } catch { return null } }
async function loadStatuses() {
  try { publishStatus.value = parseStatus(await api.getPublishStatus()) } catch { publishStatus.value = null }
  try { reviewStatus.value = parseStatus(await api.getReviewStatus()) } catch { reviewStatus.value = null }
}
async function handleTogglePublish(val: boolean) {
  await api.updatePublishSwitch({ enabled: val })
  await loadStatuses()
  ElMessage.success('发布开关已更新')
}
async function handleToggleReview(val: boolean) {
  await api.updateReviewSwitch({ enabled: val })
  await loadStatuses()
  ElMessage.success('审核开关已更新')
}
</script>

<template>
  <div class="page-container" v-loading="loading">
    <el-tabs v-model="activeTab">
      <!-- ===== Tab 1: 安全策略（列表式 CRUD） ===== -->
      <el-tab-pane label="安全策略" name="security">
        <div class="card-panel">
          <div class="section-header">
            <div class="section-title">安全策略列表（共 {{ policyList.length }} 条）</div>
            <el-button type="primary" @click="handleAddPolicy">新增策略</el-button>
          </div>
          <el-table :data="policyList" stripe size="small">
            <el-table-column prop="name" label="策略名称" min-width="150" />
            <el-table-column label="策略类型" width="130">
              <template #default="{ row }">{{ policyTypeOptions.find(o => o.value === row.policyType)?.label || row.policyType }}</template>
            </el-table-column>
            <el-table-column prop="pattern" label="匹配规则" min-width="200" show-overflow-tooltip />
            <el-table-column label="执行动作" width="80">
              <template #default="{ row }">{{ policyActionOptions.find(o => o.value === row.action)?.label || row.action }}</template>
            </el-table-column>
            <el-table-column prop="priority" label="优先级" width="70" align="center" />
            <el-table-column label="启用" width="70">
              <template #default="{ row }"><el-switch :model-value="row.enabled === 1 || row.enabled === true" @change="handleTogglePolicy(row)" size="small" /></template>
            </el-table-column>
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="handleEditPolicy(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="handleDeletePolicy(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!policyList.length" description="暂无安全策略，点击上方「新增策略」添加" :image-size="60" />
        </div>
      </el-tab-pane>

      <!-- ===== Tab 2: 发布策略（列表式 CRUD） ===== -->
      <el-tab-pane label="发布策略" name="publish">
        <div class="card-panel">
          <div class="section-header">
            <div class="section-title">发布策略列表（共 {{ strategyList.length }} 条）</div>
            <el-button type="primary" @click="handleAddStrategy">新增策略</el-button>
          </div>
          <el-table :data="strategyList" stripe size="small">
            <el-table-column prop="name" label="策略名称" min-width="150" />
            <el-table-column label="策略类型" width="130">
              <template #default="{ row }">{{ strategyTypeOptions.find(o => o.value === row.strategyType)?.label || row.strategyType }}</template>
            </el-table-column>
            <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
            <el-table-column prop="priority" label="优先级" width="70" align="center" />
            <el-table-column label="启用" width="70">
              <template #default="{ row }"><el-switch :model-value="row.enabled === 1 || row.enabled === true" @change="handleToggleStrategy(row)" size="small" /></template>
            </el-table-column>
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="handleEditStrategy(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="handleDeleteStrategy(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!strategyList.length" description="暂无发布策略，点击上方「新增策略」添加" :image-size="60" />
        </div>
      </el-tab-pane>

      <!-- ===== Tab 3: 配置项管理 ===== -->
      <el-tab-pane label="配置项管理" name="config">
        <div class="card-panel">
          <div class="section-header">
            <div class="section-title">配置项列表（共 {{ configList.length }} 条）</div>
            <div style="display:flex;gap:8px">
              <el-button @click="handleViewDefaults">查看默认配置</el-button>
              <el-button @click="showImportDialog = true; importFile = null">导入配置</el-button>
              <el-button @click="handleExportConfig">导出配置</el-button>
              <el-button type="primary" @click="handleEditDocGuide">文档导读设置</el-button>
            </div>
          </div>
          <div class="filter-bar">
            <el-select v-model="configQuery.configType" placeholder="配置类型" clearable style="width:140px" @change="loadConfigs">
              <el-option v-for="t in configTypeOptions" :key="t.value" :label="t.label" :value="t.value" />
            </el-select>
            <el-button type="primary" @click="loadConfigs">查询</el-button>
          </div>
          <el-table :data="configList" stripe size="small">
            <el-table-column prop="configKey" label="配置键" width="160" show-overflow-tooltip />
            <el-table-column label="配置值" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">{{ (row.configValue || '').length > 60 ? row.configValue.slice(0, 60) + '...' : row.configValue }}</template>
            </el-table-column>
            <el-table-column label="类型" width="100">
              <template #default="{ row }"><el-tag size="small">{{ CONFIG_TYPE_LABEL[row.configType] || row.configType }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="description" label="描述" min-width="140" show-overflow-tooltip />
            <el-table-column label="默认" width="70" align="center">
              <template #default="{ row }"><el-tag v-if="row.isDefault === 1" type="success" size="small">是</el-tag><span v-else style="color:#c0c4cc">否</span></template>
            </el-table-column>
            <el-table-column label="操作" width="220" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="handleShowHistory(row)">历史</el-button>
                <el-button v-if="row.isDefault !== 1" link type="warning" size="small" @click="handleSetDefault(row)">设为默认</el-button>
                <el-button v-if="row.configKey !== 'doc_guide'" link type="primary" size="small" @click="handleResetDefault(row)">重置</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!configList.length" description="暂无配置项" :image-size="60" />
        </div>
      </el-tab-pane>

      <!-- ===== Tab 4: 审核流程绑定 + 发布/审核状态 ===== -->
      <el-tab-pane label="审核流程" name="review">
        <div class="card-panel" style="margin-bottom:16px">
          <div class="section-title" style="margin-bottom:12px">发布 / 审核状态</div>
          <div style="display:flex;gap:24px;flex-wrap:wrap">
            <div style="display:flex;align-items:center;gap:10px">
              <span>发布状态</span>
              <el-tag :type="publishStatus?.enabled ? 'success' : 'info'" size="small">{{ publishStatus?.enabled ? '已开启' : '已关闭' }}</el-tag>
              <el-switch :model-value="!!publishStatus?.enabled" size="small" @change="(v: any) => handleTogglePublish(!!v)" />
            </div>
            <div style="display:flex;align-items:center;gap:10px">
              <span>审核状态</span>
              <el-tag :type="reviewStatus?.enabled ? 'success' : 'info'" size="small">{{ reviewStatus?.enabled ? '已开启' : '已关闭' }}</el-tag>
              <el-switch :model-value="!!reviewStatus?.enabled" size="small" @change="(v: any) => handleToggleReview(!!v)" />
            </div>
          </div>
        </div>
        <div class="card-panel">
          <div class="section-title" style="margin-bottom:12px">绑定审核流程</div>
          <el-form label-width="110px" style="max-width:520px">
            <el-form-item label="知识库" required>
              <el-select v-model="bindingForm.kbId" filterable placeholder="选择知识库" style="width:100%" @change="handleBindingKbChange">
                <el-option v-for="k in kbOptions" :key="k.id" :label="k.name" :value="k.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="流程模板" required>
              <el-select v-model="bindingForm.templateId" filterable placeholder="选择审核流程模板" style="width:100%" :disabled="!bindingForm.kbId">
                <el-option v-for="t in templateOptions" :key="t.id" :label="t.name || t.templateName" :value="t.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="启用绑定">
              <el-switch v-model="bindingForm.enabled" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="bindingSaving" @click="handleSaveBinding">保存绑定</el-button>
            </el-form-item>
            <p class="desc-text">绑定后，该知识库的版本发布将按所选审核流程执行；流程节点在「知识审核 → 流程设计」中维护。</p>
          </el-form>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 安全策略编辑弹窗 -->
    <el-dialog v-model="showPolicyDialog" :title="editingPolicyId ? '编辑安全策略' : '新增安全策略'" width="560px" :close-on-click-modal="false">
      <el-form label-width="100px">
        <el-form-item label="策略名称" required><el-input v-model="policyForm.name" placeholder="如：内网IP白名单" /></el-form-item>
        <el-form-item label="策略类型">
          <el-select v-model="policyForm.policyType" style="width:100%">
            <el-option v-for="o in policyTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="匹配规则"><el-input v-model="policyForm.pattern" type="textarea" :rows="2" placeholder="IP 地址 / 正则表达式 / 关键词" /></el-form-item>
        <el-form-item label="执行动作">
          <el-select v-model="policyForm.action" style="width:100%">
            <el-option v-for="o in policyActionOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级"><el-input-number v-model="policyForm.priority" :min="0" :max="999" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="policyForm.description" type="textarea" :rows="2" placeholder="策略说明" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="policyForm.enabled" :true-value="1" :false-value="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showPolicyDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSavePolicy">保存</el-button>
      </template>
    </el-dialog>

    <!-- 发布策略编辑弹窗 -->
    <el-dialog v-model="showStrategyDialog" :title="editingStrategyId ? '编辑发布策略' : '新增发布策略'" width="560px" :close-on-click-modal="false">
      <el-form label-width="100px">
        <el-form-item label="策略名称" required><el-input v-model="strategyForm.name" placeholder="如：自动发布策略" /></el-form-item>
        <el-form-item label="策略类型">
          <el-select v-model="strategyForm.strategyType" style="width:100%">
            <el-option v-for="o in strategyTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="配置参数"><el-input v-model="strategyForm.config" type="textarea" :rows="3" placeholder='JSON 格式，如 {"maxPublish":10,"timeWindow":"08:00-20:00"}' /></el-form-item>
        <el-form-item label="优先级"><el-input-number v-model="strategyForm.priority" :min="0" :max="999" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="strategyForm.description" type="textarea" :rows="2" placeholder="策略说明" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="strategyForm.enabled" :true-value="1" :false-value="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showStrategyDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSaveStrategy">保存</el-button>
      </template>
    </el-dialog>

    <!-- 文档导读设置弹窗 -->
    <el-dialog v-model="showDocGuideDialog" title="文档导读设置" width="480px" :close-on-click-modal="false">
      <el-form label-width="110px">
        <el-form-item label="自动生成导读"><el-switch v-model="docGuideForm.autoGenerate" /></el-form-item>
        <el-form-item label="LLM 模型">
          <el-select v-model="docGuideForm.llmModel" style="width:100%">
            <el-option v-for="m in LLM_MODEL_OPTIONS" :key="m.value" :label="m.label" :value="m.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="最大大纲层级">
          <el-input-number v-model="docGuideForm.maxOutlineLevel" :min="1" :max="6" style="width:160px" />
          <span style="margin-left:8px;font-size:12px;color:#909399">文档导读大纲的最大层级深度</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDocGuideDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSaveDocGuide">保存设置</el-button>
      </template>
    </el-dialog>

    <!-- 配置历史弹窗 -->
    <el-dialog v-model="showHistoryDialog" :title="'配置变更历史 - ' + historyTitle" width="650px">
      <el-timeline v-if="historyList.length">
        <el-timeline-item v-for="h in historyList" :key="h.id" :timestamp="h.timestamp" placement="top">
          <p style="margin:0">
            <el-tag :type="(CHANGE_TYPE_TAG[h.changeType] || 'info') as any" size="small">{{ CHANGE_TYPE_LABEL[h.changeType] || h.changeType }}</el-tag>
            <span style="margin-left:8px;font-size:12px;color:#909399">操作人：{{ h.operator || '-' }}</span>
          </p>
          <div v-if="h.oldValue" style="margin-top:4px;font-size:12px">
            <span style="color:#f56c6c">旧值：</span><code>{{ (h.oldValue || '').length > 80 ? h.oldValue.slice(0, 80) + '...' : h.oldValue }}</code>
          </div>
          <div v-if="h.newValue" style="margin-top:2px;font-size:12px">
            <span style="color:#67c23a">新值：</span><code>{{ (h.newValue || '').length > 80 ? h.newValue.slice(0, 80) + '...' : h.newValue }}</code>
          </div>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无变更记录" :image-size="60" />
      <template #footer><el-button @click="showHistoryDialog = false">关闭</el-button></template>
    </el-dialog>

    <!-- 查看默认配置弹窗 -->
    <el-dialog v-model="showDefaultDialog" title="默认配置列表" width="600px">
      <el-table :data="defaultList" stripe size="small">
        <el-table-column prop="configKey" label="配置键" width="150" show-overflow-tooltip />
        <el-table-column label="类型" width="100">
          <template #default="{ row }"><el-tag size="small">{{ CONFIG_TYPE_LABEL[row.configType] || row.configType }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="140" show-overflow-tooltip />
        <el-table-column label="默认值" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ (row.configValue || '').length > 60 ? row.configValue.slice(0, 60) + '...' : row.configValue }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!defaultList.length" description="暂无默认配置" :image-size="60" />
      <template #footer><el-button @click="showDefaultDialog = false">关闭</el-button></template>
    </el-dialog>

    <!-- 导入配置弹窗 -->
    <el-dialog v-model="showImportDialog" title="导入配置" width="480px">
      <el-upload drag :auto-upload="false" :limit="1" accept=".json" :on-change="handleImportFileChange" style="width:100%">
        <div style="padding:20px">将 JSON 配置文件拖到此处，或 <em>点击上传</em></div>
        <template #tip><div style="font-size:12px;color:#909399">仅支持 .json 格式的配置文件</div></template>
      </el-upload>
      <template #footer>
        <el-button @click="showImportDialog = false">取消</el-button>
        <el-button type="primary" @click="handleImportConfig" :disabled="!importFile">开始导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.section-title { font-size: 15px; font-weight: 600; }
.desc-text { font-size: 13px; color: #909399; }
.card-panel { background: #fff; border-radius: 8px; padding: 16px; }
.page-container { padding: 16px; }
</style>
