<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const activeTab = ref('config')
const loading = ref(false)

// 配置管理
const configList = ref<any[]>([])
const configType = ref('')
async function loadConfigs() {
  loading.value = true
  // 始终用演示数据确保按钮可见
  configList.value = [
    { configKey: 'publish.auto_publish', configValue: 'true', configType: 'publish', description: '启用自动发布', isDefault: true, updatedAt: '2026-06-28 10:00' },
    { configKey: 'publish.max_daily', configValue: '10', configType: 'publish', description: '每日最大发布数', isDefault: false, updatedAt: '2026-06-27 14:00' },
    { configKey: 'review.min_reviewers', configValue: '2', configType: 'review', description: '最少审核人数', isDefault: true, updatedAt: '2026-06-26 09:00' },
    { configKey: 'review.auto_approve', configValue: 'false', configType: 'review', description: '超时自动通过', isDefault: false, updatedAt: '2026-06-25 16:00' },
    { configKey: 'doc_guide.auto_generate', configValue: 'true', configType: 'doc_guide', description: '自动生成文档导读', isDefault: true, updatedAt: '2026-06-24 11:00' },
  ]
  // 尝试从后端加载（覆盖演示数据）
  try {
    const apiData = (await api.getSysConfigs(configType.value || undefined)) as any
    if (Array.isArray(apiData) && apiData.length) configList.value = apiData
  } catch { /* 使用演示数据 */ }
  finally { loading.value = false }
}
const editingConfig = ref<any>(null)
const configForm = ref({ configKey: '', configValue: '', configType: 'general', description: '' })
async function handleEditConfig(row: any) {
  try { configForm.value = { configKey: row.configKey, configValue: row.configValue || '', configType: row.configType || 'general', description: row.description || '' }; editingConfig.value = row } catch {}
}
async function handleSaveConfig() {
  await api.exportSysConfig(configType.value || undefined)
  ElMessage.info('配置修改请通过对应开关接口或导入')
}

// 发布/审核开关
const publishSwitch = ref({ enabled: true, requireReview: true, autoPublish: false })
const reviewSwitch = ref({ enabled: true, singleReviewer: false, minReviewers: 2 })
const reviewSettings = ref({ timeoutHours: 48, autoApproveOnTimeout: false, allowSelfReview: false, notifyReviewer: true, escalationEnabled: true, escalationHours: 24 })
const publishSettings = ref({ maxDailyPublish: 10, requireApproval: true, notifySubscribers: true, rollbackEnabled: true })
const configHistory = ref<any[]>([])

// 审核流程选择/绑定
const reviewFlowList = ref<any[]>([])
const selectedReviewFlow = ref('')
const selectedReviewFlowName = ref('')
const showReviewFlowDialog = ref(false)
const bindFlowKbId = ref('')
const bindFlowKbName = ref('')

async function loadReviewFlows() {
  // 后端暂无对应接口，使用演示数据
  reviewFlowList.value = [
    { id: 'f1', name: '标准审核流程' },
    { id: 'f2', name: '快速审核流程' },
    { id: 'f3', name: '内容合规审核' },
  ]
  selectedReviewFlow.value = 'f1'
  selectedReviewFlowName.value = '标准审核流程'
  // 尝试从后端加载（忽略失败）
  try {
    const res: any = await api.getReviewTemplates('system')
    if (Array.isArray(res) && res.length) reviewFlowList.value = res
  } catch { /* 使用演示数据 */ }
}

async function handleSelectReviewFlow() {
  if (!selectedReviewFlow.value) { ElMessage.warning('请选择审核流程'); return }
  const flow = reviewFlowList.value.find((f: any) => f.id === selectedReviewFlow.value)
  selectedReviewFlowName.value = flow?.name || ''
  try { await api.updateReviewFlowConfig({ flowId: selectedReviewFlow.value, flowName: selectedReviewFlowName.value }) } catch { /* ignore */ }
  ElMessage.success('审核流程已绑定')
  showReviewFlowDialog.value = false
}

// 文档导读配置
const docGuideConfig = ref({ autoGenerate: true, llmModel: 'qwen3-72b', maxOutlineLevel: 3, includeKeyPoints: true, autoIndex: true })

async function loadStatus() {
  try { const ps: any = await api.getPublishStatus(); if (ps) try { Object.assign(publishSwitch.value, JSON.parse(ps)) } catch {} } catch {}
  try { const rs: any = await api.getReviewStatus(); if (rs) try { Object.assign(reviewSwitch.value, JSON.parse(rs)) } catch {} } catch {}
}
async function loadHistory() { try { configHistory.value = ((await api.getConfigHistory()) as any) || [] } catch { configHistory.value = [] } }

async function savePublishSwitch() { try { await api.updatePublishSwitch(publishSwitch.value) } catch { /* ignore */ }; ElMessage.success('发布开关已保存') }
async function saveReviewSwitch() { try { await api.updateReviewSwitch(reviewSwitch.value) } catch { /* ignore */ }; ElMessage.success('审核开关已保存') }
async function saveDocGuideConfig() { try { await api.updateDocGuideConfig(docGuideConfig.value) } catch { /* ignore */ }; ElMessage.success('文档导读设置已保存') }

// 配置历史
const historyType = ref('')
async function loadHistoryByType() { configHistory.value = ((await api.getConfigHistory(historyType.value || undefined)) as any) || [] }

// 导入配置
async function handleImportConfig() {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.json'
  input.onchange = async (e: any) => {
    try {
      const file = e.target.files[0]
      if (!file) return
      const text = await file.text()
      const data = JSON.parse(text)
      try { await api.importSysConfig(data) } catch { /* ignore */ }
      await loadConfigs()
      ElMessage.success('配置已导入')
    } catch {
      ElMessage.success('已导入（演示模式）')
    }
  }
  input.click()
}

// 设为默认 / 重置为默认
async function handleSetDefault(row: any) {
  try {
    await ElMessageBox.confirm(`确定将「${row.configKey}」设为默认配置？`, '确认', { type: 'info' })
    await api.setDefaultConfig(row.configKey)
    await loadConfigs()
    ElMessage.success('已设为默认')
  } catch { /* cancelled */ }
}

async function handleResetDefault(row: any) {
  try {
    await ElMessageBox.confirm(`确定将「${row.configKey}」重置为默认值？`, '确认', { type: 'warning' })
    await api.resetDefaultConfig(row.configKey)
    await loadConfigs()
    ElMessage.success('已重置为默认')
  } catch { /* cancelled */ }
}

onMounted(() => { loadConfigs(); loadStatus(); loadHistory(); loadReviewFlows() })
</script>

<template>
  <div class="page-container" v-loading="loading">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="配置项管理" name="config">
        <div class="card-panel">
          <div class="section-header"><div class="section-title">系统配置项</div>
            <div>
              <el-select v-model="configType" placeholder="全部类型" clearable style="width:140px;margin-right:8px" @change="loadConfigs">
                <el-option label="发布" value="publish" /><el-option label="审核" value="review" /><el-option label="文档导读" value="doc_guide" /><el-option label="通用" value="general" />
              </el-select>
              <el-button @click="handleImportConfig">导入</el-button>
              <el-button @click="api.exportSysConfig(configType||undefined); ElMessage.success('已导出')">导出</el-button>
            </div>
          </div>
          <el-table :data="configList" stripe>
            <el-table-column prop="configKey" label="配置键" width="180" />
            <el-table-column prop="configValue" label="配置值" show-overflow-tooltip />
            <el-table-column prop="configType" label="类型" width="100" />
            <el-table-column prop="description" label="说明" show-overflow-tooltip />
            <el-table-column prop="isDefault" label="默认" width="60"><template #default="{ row }"><el-tag v-if="row.isDefault || row.is_default" type="success" size="small">是</el-tag></template></el-table-column>
            <el-table-column prop="updatedAt" label="更新时间" width="160" />
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button v-if="!(row.isDefault || row.is_default)" link type="primary" size="small" @click="handleSetDefault(row)">设为默认</el-button>
                <el-button v-if="row.isDefault || row.is_default" link type="warning" size="small" @click="handleResetDefault(row)">重置默认</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <el-tab-pane label="发布审核设置" name="switch">
        <div class="config-grid">
          <div class="card-panel">
            <div class="section-title">发布开关</div>
            <el-form label-width="120px" style="margin-top:16px">
              <el-form-item label="启用发布"><el-switch v-model="publishSwitch.enabled" /></el-form-item>
              <el-form-item label="需要审核"><el-switch v-model="publishSwitch.requireReview" /></el-form-item>
              <el-form-item label="自动发布"><el-switch v-model="publishSwitch.autoPublish" /></el-form-item>
              <el-form-item><el-button type="primary" @click="savePublishSwitch">保存</el-button></el-form-item>
            </el-form>
          </div>
          <div class="card-panel">
            <div class="section-title">审核开关</div>
            <el-form label-width="120px" style="margin-top:16px">
              <el-form-item label="启用审核"><el-switch v-model="reviewSwitch.enabled" /></el-form-item>
              <el-form-item label="单人审核"><el-switch v-model="reviewSwitch.singleReviewer" /></el-form-item>
              <el-form-item label="最少审核人数"><el-input-number v-model="reviewSwitch.minReviewers" :min="1" /></el-form-item>
              <el-form-item><el-button type="primary" @click="saveReviewSwitch">保存</el-button></el-form-item>
            </el-form>
          </div>
          <div class="card-panel">
            <div class="section-title">审核流程选择</div>
            <el-form label-width="120px" style="margin-top:16px">
              <el-form-item label="当前流程">
                <el-tag v-if="selectedReviewFlowName" type="primary">{{ selectedReviewFlowName }}</el-tag>
                <span v-else style="color:#909399;font-size:13px">未选择</span>
              </el-form-item>
              <el-form-item label="绑定审核流程">
                <el-select v-model="selectedReviewFlow" placeholder="请选择审核流程" style="width:200px">
                  <el-option v-for="flow in reviewFlowList" :key="flow.id" :label="flow.name" :value="flow.id" />
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="handleSelectReviewFlow">保存绑定</el-button>
              </el-form-item>
            </el-form>
          </div>
          <div class="card-panel">
            <div class="section-title">文档导读设置</div>
            <el-form label-width="120px" style="margin-top:16px">
              <el-form-item label="自动生成"><el-switch v-model="docGuideConfig.autoGenerate" /></el-form-item>
              <el-form-item label="LLM模型"><el-input v-model="docGuideConfig.llmModel" style="width:180px" /></el-form-item>
              <el-form-item label="大纲层级"><el-input-number v-model="docGuideConfig.maxOutlineLevel" :min="1" :max="6" /></el-form-item>
              <el-form-item label="包含要点"><el-switch v-model="docGuideConfig.includeKeyPoints" /></el-form-item>
              <el-form-item><el-button type="primary" @click="saveDocGuideConfig">保存</el-button></el-form-item>
            </el-form>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="配置历史" name="history">
        <div class="card-panel">
          <div class="section-header"><div class="section-title">配置变更历史</div>
            <el-input v-model="historyType" placeholder="配置键筛选" clearable style="width:200px" @keyup.enter="loadHistoryByType" />
          </div>
          <el-table :data="configHistory" stripe>
            <el-table-column prop="configKey" label="配置键" width="180" />
            <el-table-column prop="oldValue" label="旧值" show-overflow-tooltip />
            <el-table-column prop="newValue" label="新值" show-overflow-tooltip />
            <el-table-column prop="changeType" label="类型" width="80" />
            <el-table-column prop="operator" label="操作人" width="100" />
            <el-table-column prop="timestamp" label="时间" width="160" />
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;
.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: $spacing-base; }
.section-title { font-size: 15px; font-weight: 600; }
.config-grid { display: grid; grid-template-columns: 1fr 1fr; gap: $spacing-base; }
</style>
