<script setup lang="ts">
import { ref, onMounted, reactive } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox, ElUpload } from 'element-plus'
import * as api from '@/api'
import { UploadFilled, Download, Edit, Delete, Plus, ZoomIn, VideoPlay } from '@element-plus/icons-vue'

const route = useRoute()
const appId = (route.params.id as string) || 'app_001'
const activeTab = ref('basic')
const loading = ref(false)

// ===========================================================================
// 基础配置
// ===========================================================================
const basicForm = ref({ memoryRounds: 5, outputFormat: 'markdown', timeoutSeconds: 30, greeting: '', goodbyeMessage: '' })
const advancedForm = ref({ model: '', temperature: 0.7, maxTokens: 2048, topP: 1, frequencyPenalty: 0, presencePenalty: 0 })
const showAdvanced = ref(false)

async function loadBasic() {
  try { const r: any = await api.getAppBasicConfig(appId); if (r) Object.assign(basicForm.value, r); if (r?.advanced) Object.assign(advancedForm.value, r.advanced) } catch {}
}
async function saveBasic() { await api.saveAppBasicConfig(appId, basicForm.value); ElMessage.success('基础配置已保存') }
async function saveAdvancedOpts() { await api.saveAppAdvanced(appId, advancedForm.value); ElMessage.success('高级选项已保存') }

async function handleExportBasic() {
  try {
	    const r: any = await api.exportAppConfig(appId)
    const blob = new Blob([JSON.stringify(r, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = `basic_config_${appId}.json`
    a.click(); URL.revokeObjectURL(url); ElMessage.success('基础配置已导出')
  } catch { ElMessage.error('导出失败') }
}
async function handleImportBasic() {
  const input = document.createElement('input'); input.type = 'file'; input.accept = '.json'
  input.onchange = async (e: any) => {
    try {
      const file = e.target.files[0]; if (!file) return
      const text = await file.text(); const data = JSON.parse(text)
      await api.importAppConfig(appId, data); await loadBasic(); ElMessage.success('基础配置已导入')
    } catch { ElMessage.error('导入失败，请检查文件格式') }
  }
  input.click()
}

// ===========================================================================
// 对话配置
// ===========================================================================
const dialogForm = ref({ backgroundColor: '#f5f5f5', showAvatar: 1, showFeedback: 1, showSuggestions: 1 })
async function loadDialog() { try { const r: any = await api.getAppDialogConfig(appId); if (r) Object.assign(dialogForm.value, r) } catch {} }
async function saveDialog() { await api.saveAppDialogConfig(appId, dialogForm.value); ElMessage.success('对话配置已保存') }

async function handleExportDialog() {
  try {
    const r: any = await api.exportAppDialog(appId)
    const blob = new Blob([JSON.stringify(r, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = `dialog_config_${appId}.json`
    a.click(); URL.revokeObjectURL(url); ElMessage.success('对话配置已导出')
  } catch { ElMessage.error('导出失败') }
}
async function handleImportDialog() {
  const input = document.createElement('input'); input.type = 'file'; input.accept = '.json'
  input.onchange = async (e: any) => {
    try {
      const file = e.target.files[0]; if (!file) return
      const text = await file.text(); const data = JSON.parse(text)
      await api.importAppDialog(appId, data); await loadDialog(); ElMessage.success('对话配置已导入')
    } catch { ElMessage.error('导入失败，请检查文件格式') }
  }
  input.click()
}

// ===========================================================================
// 触发器管理
// ===========================================================================
const triggerList = ref<any[]>([])
const showTriggerDialog = ref(false)
const isEditingTrigger = ref(false)
const editingTriggerId = ref('')
const triggerFormDefault = { name: '', triggerType: 'keyword', matchContent: '', actionType: 'reply', actionConfig: '' }
const triggerForm = ref({ ...triggerFormDefault })

async function loadTriggers() { triggerList.value = ((await api.getAppTriggers(appId)) as any) || [] }

function openAddTrigger() {
  isEditingTrigger.value = false; editingTriggerId.value = ''
  triggerForm.value = { ...triggerFormDefault }; showTriggerDialog.value = true
}
function openEditTrigger(row: any) {
  isEditingTrigger.value = true; editingTriggerId.value = row.id
  triggerForm.value = { name: row.name, triggerType: row.triggerType, matchContent: row.matchContent, actionType: row.actionType, actionConfig: row.actionConfig || '' }
  showTriggerDialog.value = true
}

async function handleSaveTrigger() {
  if (!triggerForm.value.name) { ElMessage.warning('请输入名称'); return }
  // MySQL JSON 列不允许空字符串，转换为 null
  const payload = { ...triggerForm.value, actionConfig: triggerForm.value.actionConfig || null }
  if (isEditingTrigger.value) {
    await api.updateAppTrigger(appId, editingTriggerId.value, payload)
    ElMessage.success('更新成功')
  } else {
    await api.createAppTrigger(appId, payload)
    ElMessage.success('创建成功')
  }
  showTriggerDialog.value = false; await loadTriggers()
}

async function handleDeleteTrigger(row: any) {
  try { await ElMessageBox.confirm('确认删除？', '删除确认', { type: 'warning' }); await api.deleteAppTrigger(appId, row.id); await loadTriggers(); ElMessage.success('删除成功') } catch {}
}

async function handleToggleTrigger(row: any) {
  try {
    await api.updateAppTrigger(appId, row.id, { enabled: row.enabled ? 0 : 1 })
    await loadTriggers(); ElMessage.success(row.enabled ? '已禁用' : '已启用')
  } catch { ElMessage.error('操作失败') }
}

async function handleTestTrigger(row: any) {
  try {
    const { value } = await ElMessageBox.prompt('请输入测试输入', `测试触发器: ${row.name}`, { inputType: 'textarea', inputPlaceholder: '输入测试内容...' })
    if (value) {
      const r: any = await api.testAppTrigger(appId, row.id, value)
      ElMessageBox.alert(JSON.stringify(r, null, 2), '测试结果', { dangerouslyUseHTMLString: false })
    }
  } catch { /* cancelled */ }
}

async function handleRunTrigger(row: any) {
  try {
    const { value } = await ElMessageBox.prompt('请输入运行输入', `运行触发器: ${row.name}`, { inputType: 'textarea', inputPlaceholder: '输入运行内容...' })
    if (value) {
      const r: any = await api.runAppTrigger(appId, row.id, value)
      ElMessageBox.alert(JSON.stringify(r, null, 2), '运行结果', { dangerouslyUseHTMLString: false })
    }
  } catch { /* cancelled */ }
}

// ===========================================================================
// 全局策略
// ===========================================================================
const policyForm = ref({ safetyEnabled: 1, sensitiveWordMode: 'reject', fallbackText: '抱歉，没有理解您的问题', unmatchedEnabled: 1 })
async function loadPolicy() { try { const r: any = await api.getAppGlobalPolicy(appId); if (r) Object.assign(policyForm.value, r) } catch {} }
async function savePolicy() { await api.saveAppGlobalPolicy(appId, policyForm.value); ElMessage.success('策略已保存') }

// 变量管理
const variableList = ref<any[]>([])
const showVariableDialog = ref(false)
const isEditingVariable = ref(false)
const editingVariableId = ref('')
const variableFormDefault = { name: '', type: 'string', defaultValue: '', description: '' }
const variableForm = ref({ ...variableFormDefault })

async function loadVariables() { variableList.value = ((await api.getAppVariables(appId)) as any) || [] }

function openAddVariable() {
  isEditingVariable.value = false; editingVariableId.value = ''
  variableForm.value = { ...variableFormDefault }; showVariableDialog.value = true
}
function openEditVariable(row: any) {
  isEditingVariable.value = true; editingVariableId.value = row.id
  variableForm.value = { name: row.name, type: row.type, defaultValue: row.defaultValue, description: row.description }
  showVariableDialog.value = true
}

async function handleSaveVariable() {
  if (!variableForm.value.name) { ElMessage.warning('请输入变量名称'); return }
  if (isEditingVariable.value) {
    await api.updateAppVariable(appId, editingVariableId.value, variableForm.value)
    ElMessage.success('更新成功')
  } else {
    await api.createAppVariable(appId, variableForm.value)
    ElMessage.success('创建成功')
  }
  showVariableDialog.value = false; await loadVariables()
}

async function handleDeleteVariable(row: any) {
  try { await ElMessageBox.confirm('确认删除变量？', '确认', { type: 'warning' }); await api.deleteAppVariable(appId, row.id); await loadVariables(); ElMessage.success('删除成功') } catch {}
}

// 敏感词管理
const showSensitiveDialog = ref(false)
const sensitiveWordText = ref('')
const sensitiveWordMode = ref('reject')

async function handleSaveSensitiveWords() {
  if (!sensitiveWordText.value) { ElMessage.warning('请输入敏感词列表'); return }
  try {
    const words = sensitiveWordText.value.split('\n').map(s => s.trim()).filter(Boolean)
    await api.saveAppSensitiveWords(appId, { words, mode: sensitiveWordMode.value })
    showSensitiveDialog.value = false; ElMessage.success(`已保存 ${words.length} 个敏感词`)
  } catch { ElMessage.error('保存失败') }
}

// 未匹配策略管理
const showUnmatchedDialog = ref(false)
const unmatchedForm = ref({ fallbackText: '', redirectKbId: '', retryCount: 0 })

async function handleSaveUnmatched() {
  try {
    await api.saveAppUnmatchedConfig(appId, unmatchedForm.value)
    showUnmatchedDialog.value = false; ElMessage.success('未匹配策略已保存')
  } catch { ElMessage.error('保存失败') }
}

// 安全策略切换
async function handleTogglePolicy(field: string) {
  try {
    await api.toggleAppPolicy(appId, { field, enabled: (policyForm.value as any)[field] ? 0 : 1 })
    await loadPolicy(); ElMessage.success('策略已切换')
  } catch { ElMessage.error('操作失败') }
}

// ===========================================================================
// 知识库绑定
// ===========================================================================
const kbBindings = ref<any[]>([])
const showKbDialog = ref(false)
const kbForm = ref({ kbId: '', priority: 0 })

async function loadKbBindings() { kbBindings.value = ((await api.getAppKbBindings(appId)) as any) || [] }
async function handleBindKb() {
  if (!kbForm.value.kbId) { ElMessage.warning('请输入知识库ID'); return }
  await api.bindAppKb(appId, kbForm.value); showKbDialog.value = false; await loadKbBindings(); ElMessage.success('绑定成功')
}
async function handleUnbindKb(row: any) {
  try { await ElMessageBox.confirm('确认解绑？', '确认', { type: 'warning' }); await api.unbindAppKb(appId, row.id); await loadKbBindings(); ElMessage.success('已解绑') } catch {}
}
async function handleExportKbBindings() {
  const blob = new Blob([JSON.stringify(kbBindings.value, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = `kb_bindings_${appId}.json`
  a.click(); URL.revokeObjectURL(url); ElMessage.success('知识库绑定已导出')
}
async function handleImportKbBindings() {
  const input = document.createElement('input'); input.type = 'file'; input.accept = '.json'
  input.onchange = async (e: any) => {
    try {
      const file = e.target.files[0]; if (!file) return
      const bindings = JSON.parse(await file.text())
      if (Array.isArray(bindings)) {
        for (const b of bindings) { await api.bindAppKb(appId, { kbId: b.kbId, priority: b.priority || 0 }) }
        await loadKbBindings(); ElMessage.success('知识库绑定已导入')
      }
    } catch { ElMessage.error('导入失败') }
  }
  input.click()
}

// ===========================================================================
// 对话测试
// ===========================================================================
const testList = ref<any[]>([])
const showTestDialog = ref(false)
const isEditingTest = ref(false)
const editingTestId = ref('')
const testFormDefault = { name: '', query: '', expectedAnswer: '' }
const testForm = ref({ ...testFormDefault })

async function loadTests() { testList.value = ((await api.getAppDialogTests(appId)) as any) || [] }

function openAddTest() {
  isEditingTest.value = false; editingTestId.value = ''
  testForm.value = { ...testFormDefault }; showTestDialog.value = true
}
function openEditTest(row: any) {
  isEditingTest.value = true; editingTestId.value = row.id
  testForm.value = { name: row.name, query: row.query, expectedAnswer: row.expectedAnswer }
  showTestDialog.value = true
}

async function handleSaveTest() {
  if (!testForm.value.name) { ElMessage.warning('请输入名称'); return }
  if (isEditingTest.value) {
    await api.updateAppDialogTest(appId, editingTestId.value, testForm.value)
    ElMessage.success('更新成功')
  } else {
    await api.createAppDialogTest(appId, testForm.value)
    ElMessage.success('创建成功')
  }
  showTestDialog.value = false; await loadTests()
}

async function handleDeleteTest(row: any) {
  try { await ElMessageBox.confirm('确认删除？', '删除确认', { type: 'warning' }); await api.deleteAppDialogTest(appId, row.id); await loadTests(); ElMessage.success('删除成功') } catch {}
}
async function handleExportTests() {
  try {
    const blob = await api.exportAppDialogTests(appId) as Blob
    const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = `test_report_${Date.now()}.csv`
    a.click(); URL.revokeObjectURL(url); ElMessage.success('已导出')
  } catch { ElMessage.error('导出失败') }
}

onMounted(() => { loadBasic(); loadDialog(); loadTriggers(); loadPolicy(); loadVariables(); loadKbBindings(); loadTests() })
</script>

<template>
  <div class="page-container" v-loading="loading">
    <el-tabs v-model="activeTab">
      <!-- ===== 基础配置 ===== -->
      <el-tab-pane label="基础配置" name="basic">
        <div class="card-panel">
          <div class="section-header">
            <div class="section-title">基础信息配置</div>
            <div class="section-actions">
              <el-button size="small" :icon="Download" @click="handleExportBasic">导出配置</el-button>
              <el-button size="small" :icon="UploadFilled" @click="handleImportBasic">导入配置</el-button>
            </div>
          </div>
          <el-form label-width="140px" style="margin-top:16px">
            <el-form-item label="对话记忆轮数"><el-input-number v-model="basicForm.memoryRounds" :min="0" :max="50" /></el-form-item>
            <el-form-item label="输出格式"><el-select v-model="basicForm.outputFormat" style="width:160px"><el-option label="Markdown" value="markdown" /><el-option label="HTML" value="html" /><el-option label="纯文本" value="text" /></el-select></el-form-item>
            <el-form-item label="超时秒数"><el-input-number v-model="basicForm.timeoutSeconds" :min="5" :max="120" /></el-form-item>
            <el-form-item label="开场白"><el-input v-model="basicForm.greeting" type="textarea" :rows="2" /></el-form-item>
            <el-form-item label="结束语"><el-input v-model="basicForm.goodbyeMessage" type="textarea" :rows="2" /></el-form-item>
            <el-form-item><el-button type="primary" @click="saveBasic">保存基础配置</el-button></el-form-item>
          </el-form>
        </div>

        <div class="card-panel" style="margin-top:16px">
          <div class="section-header">
            <div class="section-title">高级选项</div>
            <el-button size="small" text @click="showAdvanced = !showAdvanced">{{ showAdvanced ? '收起' : '展开' }}</el-button>
          </div>
          <el-form v-if="showAdvanced" label-width="140px" style="margin-top:16px">
            <el-form-item label="模型选择"><el-input v-model="advancedForm.model" placeholder="默认使用系统模型" /></el-form-item>
            <el-form-item label="温度"><el-slider v-model="advancedForm.temperature" :min="0" :max="2" :step="0.1" show-input style="width:300px" /></el-form-item>
            <el-form-item label="最大Token数"><el-input-number v-model="advancedForm.maxTokens" :min="128" :max="8192" :step="128" /></el-form-item>
            <el-form-item label="Top P"><el-slider v-model="advancedForm.topP" :min="0" :max="1" :step="0.05" show-input style="width:300px" /></el-form-item>
            <el-form-item label="频率惩罚"><el-slider v-model="advancedForm.frequencyPenalty" :min="-2" :max="2" :step="0.1" show-input style="width:300px" /></el-form-item>
            <el-form-item label="存在惩罚"><el-slider v-model="advancedForm.presencePenalty" :min="-2" :max="2" :step="0.1" show-input style="width:300px" /></el-form-item>
            <el-form-item><el-button type="primary" @click="saveAdvancedOpts">保存高级选项</el-button></el-form-item>
          </el-form>
        </div>
      </el-tab-pane>

      <!-- ===== 对话设置 ===== -->
      <el-tab-pane label="对话设置" name="dialog">
        <div class="card-panel">
          <div class="section-header">
            <div class="section-title">对话配置</div>
            <div class="section-actions">
              <el-button size="small" :icon="Download" @click="handleExportDialog">导出</el-button>
              <el-button size="small" :icon="UploadFilled" @click="handleImportDialog">导入</el-button>
            </div>
          </div>
          <el-form label-width="140px" style="margin-top:16px">
            <el-form-item label="背景色"><el-color-picker v-model="dialogForm.backgroundColor" /></el-form-item>
            <el-form-item label="显示头像"><el-switch v-model="dialogForm.showAvatar" /></el-form-item>
            <el-form-item label="显示反馈"><el-switch v-model="dialogForm.showFeedback" /></el-form-item>
            <el-form-item label="显示推荐"><el-switch v-model="dialogForm.showSuggestions" /></el-form-item>
            <el-form-item><el-button type="primary" @click="saveDialog">保存</el-button></el-form-item>
          </el-form>
        </div>

        <div class="card-panel" style="margin-top:16px">
          <div class="section-header">
            <div class="section-title">触发器管理</div>
            <el-button type="primary" size="small" :icon="Plus" @click="openAddTrigger">新增触发器</el-button>
          </div>
          <el-table :data="triggerList" stripe size="small">
            <el-table-column prop="name" label="名称" min-width="120" />
            <el-table-column prop="triggerType" label="类型" width="80" />
            <el-table-column prop="actionType" label="动作类型" width="80" />
            <el-table-column label="启用" width="70">
              <template #default="{ row }">
                <el-switch :model-value="!!row.enabled" size="small" @change="handleToggleTrigger(row)" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="220" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" :icon="Edit" @click="openEditTrigger(row)">编辑</el-button>
                <el-button link type="success" size="small" :icon="VideoPlay" @click="handleTestTrigger(row)">测试</el-button>
                <el-button link type="warning" size="small" :icon="UploadFilled" @click="handleRunTrigger(row)">运行</el-button>
                <el-button link type="danger" size="small" :icon="Delete" @click="handleDeleteTrigger(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!triggerList.length" description="暂无触发器" :image-size="60" />
        </div>
      </el-tab-pane>

      <!-- ===== 全局策略 ===== -->
      <el-tab-pane label="全局策略" name="policy">
        <div class="card-panel">
          <div class="section-title">安全与策略</div>
          <el-form label-width="140px" style="margin-top:16px">
            <el-form-item label="安全策略启用">
              <el-switch v-model="policyForm.safetyEnabled" @change="handleTogglePolicy('safetyEnabled')" />
            </el-form-item>
            <el-form-item label="敏感词处理"><el-select v-model="policyForm.sensitiveWordMode" style="width:160px"><el-option label="拒绝" value="reject" /><el-option label="替换" value="replace" /><el-option label="遮盖" value="mask" /></el-select></el-form-item>
            <el-form-item label="兜底话术"><el-input v-model="policyForm.fallbackText" type="textarea" :rows="2" /></el-form-item>
            <el-form-item><el-button type="primary" @click="savePolicy">保存策略</el-button></el-form-item>
          </el-form>
        </div>

        <div class="card-panel" style="margin-top:16px">
          <div class="section-header">
            <div class="section-title">变量管理</div>
            <el-button type="primary" size="small" :icon="Plus" @click="openAddVariable">新增变量</el-button>
          </div>
          <el-table :data="variableList" stripe size="small">
            <el-table-column prop="name" label="名称" min-width="120" />
            <el-table-column prop="type" label="类型" width="80" />
            <el-table-column prop="defaultValue" label="默认值" show-overflow-tooltip />
            <el-table-column prop="description" label="描述" show-overflow-tooltip />
            <el-table-column label="操作" width="130">
              <template #default="{ row }">
                <el-button link type="primary" size="small" :icon="Edit" @click="openEditVariable(row)">编辑</el-button>
                <el-button link type="danger" size="small" :icon="Delete" @click="handleDeleteVariable(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!variableList.length" description="暂无变量" :image-size="60" />
        </div>

        <div class="card-panel" style="margin-top:16px">
          <div class="section-header">
            <div class="section-title">敏感词管理</div>
            <el-button size="small" :icon="Plus" @click="showSensitiveDialog = true">配置敏感词</el-button>
          </div>
          <el-form label-width="140px">
            <el-form-item label="敏感词模式"><el-select v-model="sensitiveWordMode" style="width:160px"><el-option label="拒绝" value="reject" /><el-option label="替换" value="replace" /><el-option label="遮盖" value="mask" /></el-select></el-form-item>
          </el-form>
        </div>

        <div class="card-panel" style="margin-top:16px">
          <div class="section-header">
            <div class="section-title">未匹配策略</div>
            <el-button size="small" :icon="Edit" @click="showUnmatchedDialog = true">配置未匹配</el-button>
          </div>
          <el-form label-width="140px">
            <el-form-item label="兜底话术"><el-input v-model="policyForm.fallbackText" type="textarea" :rows="1" disabled /></el-form-item>
          </el-form>
        </div>
      </el-tab-pane>

      <!-- ===== 知识库绑定 ===== -->
      <el-tab-pane label="知识库绑定" name="kbbind">
        <div class="card-panel">
          <div class="section-header">
            <div class="section-title">已关联知识库</div>
            <div class="section-actions">
              <el-button size="small" :icon="Download" @click="handleExportKbBindings">导出</el-button>
              <el-button size="small" :icon="UploadFilled" @click="handleImportKbBindings">导入</el-button>
              <el-button type="primary" size="small" :icon="Plus" @click="showKbDialog = true">关联知识库</el-button>
            </div>
          </div>
          <el-table :data="kbBindings" stripe size="small">
            <el-table-column prop="kbId" label="知识库ID" min-width="200" />
            <el-table-column prop="priority" label="优先级" width="80" />
            <el-table-column label="启用" width="70"><template #default="{ row }"><el-tag :type="row.enabled?'success':'info'" size="small">{{ row.enabled?'是':'否' }}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="80"><template #default="{ row }"><el-button link type="danger" size="small" :icon="Delete" @click="handleUnbindKb(row)">解绑</el-button></template></el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <!-- ===== 对话测试 ===== -->
      <el-tab-pane label="对话测试" name="test">
        <div class="card-panel">
          <div class="section-header">
            <div class="section-title">测试案例</div>
            <div class="section-actions">
              <el-button size="small" :icon="Download" @click="handleExportTests">导出测试报告</el-button>
              <el-button type="primary" size="small" :icon="Plus" @click="openAddTest">新增测试</el-button>
            </div>
          </div>
          <el-table :data="testList" stripe size="small">
            <el-table-column prop="name" label="名称" min-width="120" />
            <el-table-column prop="query" label="问题" show-overflow-tooltip />
            <el-table-column prop="expectedAnswer" label="期望答案" show-overflow-tooltip />
            <el-table-column label="操作" width="130">
              <template #default="{ row }">
                <el-button link type="primary" size="small" :icon="Edit" @click="openEditTest(row)">编辑</el-button>
                <el-button link type="danger" size="small" :icon="Delete" @click="handleDeleteTest(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 触发器对话框 -->
    <el-dialog v-model="showTriggerDialog" :title="isEditingTrigger ? '编辑触发器' : '新增触发器'" width="480px">
      <el-form label-width="90px">
        <el-form-item label="名称" required><el-input v-model="triggerForm.name" /></el-form-item>
        <el-form-item label="类型"><el-select v-model="triggerForm.triggerType" style="width:160px"><el-option label="关键词" value="keyword" /><el-option label="正则" value="regex" /><el-option label="意图" value="intent" /></el-select></el-form-item>
        <el-form-item label="匹配内容"><el-input v-model="triggerForm.matchContent" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="动作类型"><el-select v-model="triggerForm.actionType" style="width:160px"><el-option label="回复" value="reply" /><el-option label="业务流" value="workflow" /><el-option label="API" value="api" /></el-select></el-form-item>
        <el-form-item label="动作配置"><el-input v-model="triggerForm.actionConfig" type="textarea" :rows="2" placeholder="动作配置参数（JSON格式）" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showTriggerDialog=false">取消</el-button>
        <el-button type="primary" @click="handleSaveTrigger">{{ isEditingTrigger ? '保存' : '创建' }}</el-button>
      </template>
    </el-dialog>

    <!-- 变量对话框 -->
    <el-dialog v-model="showVariableDialog" :title="isEditingVariable ? '编辑变量' : '新增变量'" width="480px">
      <el-form label-width="90px">
        <el-form-item label="名称" required><el-input v-model="variableForm.name" placeholder="变量名称" /></el-form-item>
        <el-form-item label="类型"><el-select v-model="variableForm.type" style="width:160px"><el-option label="字符串" value="string" /><el-option label="数字" value="number" /><el-option label="布尔" value="boolean" /><el-option label="JSON" value="json" /></el-select></el-form-item>
        <el-form-item label="默认值"><el-input v-model="variableForm.defaultValue" placeholder="默认值" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="variableForm.description" type="textarea" :rows="2" placeholder="变量描述" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showVariableDialog=false">取消</el-button>
        <el-button type="primary" @click="handleSaveVariable">{{ isEditingVariable ? '保存' : '创建' }}</el-button>
      </template>
    </el-dialog>

    <!-- 敏感词对话框 -->
    <el-dialog v-model="showSensitiveDialog" title="配置敏感词" width="500px">
      <el-form label-width="90px">
        <el-form-item label="处理模式"><el-select v-model="sensitiveWordMode" style="width:160px"><el-option label="拒绝" value="reject" /><el-option label="替换" value="replace" /><el-option label="遮盖" value="mask" /></el-select></el-form-item>
        <el-form-item label="敏感词列表"><el-input v-model="sensitiveWordText" type="textarea" :rows="6" placeholder="每行一个敏感词&#10;例如:&#10;敏感词1&#10;敏感词2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showSensitiveDialog=false">取消</el-button>
        <el-button type="primary" @click="handleSaveSensitiveWords">保存</el-button>
      </template>
    </el-dialog>

    <!-- 未匹配策略对话框 -->
    <el-dialog v-model="showUnmatchedDialog" title="未匹配策略配置" width="480px">
      <el-form label-width="110px">
        <el-form-item label="兜底话术"><el-input v-model="unmatchedForm.fallbackText" type="textarea" :rows="2" placeholder="未匹配时的回复内容" /></el-form-item>
        <el-form-item label="重定向知识库"><el-input v-model="unmatchedForm.redirectKbId" placeholder="知识库ID（可选）" /></el-form-item>
        <el-form-item label="重试次数"><el-input-number v-model="unmatchedForm.retryCount" :min="0" :max="5" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showUnmatchedDialog=false">取消</el-button>
        <el-button type="primary" @click="handleSaveUnmatched">保存</el-button>
      </template>
    </el-dialog>

    <!-- 知识库绑定对话框 -->
    <el-dialog v-model="showKbDialog" title="关联知识库" width="420px">
      <el-form label-width="90px">
        <el-form-item label="知识库ID" required><el-input v-model="kbForm.kbId" /></el-form-item>
        <el-form-item label="优先级"><el-input-number v-model="kbForm.priority" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showKbDialog=false">取消</el-button>
        <el-button type="primary" @click="handleBindKb">绑定</el-button>
      </template>
    </el-dialog>

    <!-- 测试案例对话框 -->
    <el-dialog v-model="showTestDialog" :title="isEditingTest ? '编辑测试案例' : '新增测试案例'" width="500px">
      <el-form label-width="90px">
        <el-form-item label="名称" required><el-input v-model="testForm.name" /></el-form-item>
        <el-form-item label="测试问题"><el-input v-model="testForm.query" /></el-form-item>
        <el-form-item label="期望答案"><el-input v-model="testForm.expectedAnswer" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showTestDialog=false">取消</el-button>
        <el-button type="primary" @click="handleSaveTest">{{ isEditingTest ? '保存' : '创建' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-base;
  flex-wrap: wrap;
  gap: 8px;
}
.section-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.section-title {
  font-size: 15px;
  font-weight: 600;
}
.card-panel {
  background: var(--el-bg-color-overlay);
  border-radius: 8px;
  padding: 20px;
  border: 1px solid var(--el-border-color-light);
}
</style>
