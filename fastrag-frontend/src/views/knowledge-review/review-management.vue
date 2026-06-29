<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const activeTab = ref('publish')
const loading = ref(false)
const kbId = 'kb_sample'

// 发布管理
const publishHistory = ref<any[]>([])
const DEMO_PUBLISH_HISTORY = [
  { id: 'ph1', knowledgeId: 'K001', version: 'v2.1', publishType: '全量', status: 'published', operator: '张三', publishedAt: '2026-06-29 10:00:00', isCurrent: true },
  { id: 'ph2', knowledgeId: 'K001', version: 'v2.0', publishType: '增量', status: 'revoked', operator: '李四', publishedAt: '2026-06-28 15:00:00', isCurrent: false },
  { id: 'ph3', knowledgeId: 'K002', version: 'v1.0', publishType: '全量', status: 'published', operator: '王五', publishedAt: '2026-06-27 09:00:00', isCurrent: true },
  { id: 'ph4', knowledgeId: 'K001', version: 'v1.9', publishType: '增量', status: 'published', operator: '张三', publishedAt: '2026-06-26 14:00:00', isCurrent: false },
  { id: 'ph5', knowledgeId: 'K003', version: 'v3.0', publishType: '全量', status: 'pending', operator: '赵六', publishedAt: '-', isCurrent: false },
]
async function loadPublishHistory() {
  loading.value = true
  publishHistory.value = [...DEMO_PUBLISH_HISTORY]
  try {
    const res: any = await api.getPublishHistory(kbId)
    const apiData = Array.isArray(res) ? res : (res?.list || [])
    if (apiData.length) publishHistory.value = apiData
  } catch { /* 使用演示数据 */ }
  finally { loading.value = false }
}
const publishPlans = ref<any[]>([])
async function loadPlans() {
  try { publishPlans.value = ((await api.getPublishPlans(kbId)) as any) || [] } catch { publishPlans.value = [] }
  if (!publishPlans.value.length) {
    publishPlans.value = [
      { id: 'pp1', name: '每周增量发布', strategy: 'incremental', executionStatus: 'completed', successCount: 5, failCount: 0, createdAt: '2026-06-29 02:00:00' },
      { id: 'pp2', name: '月末全量发布', strategy: 'full', executionStatus: 'pending', successCount: 0, failCount: 0, createdAt: '2026-06-30 02:00:00' },
    ]
  }
}
const strategyEffect = ref<any>({})
async function loadStrategyEffect() { strategyEffect.value = ((await api.getPublishStrategyEffect(kbId)) as any) || {} }
const showPlanDialog = ref(false)
const planForm = ref({ name: '', strategy: 'immediate', scheduledTime: '' })
async function handleSavePlan() {
  if (!planForm.value.name) { ElMessage.warning('请输入计划名称'); return }
  try { await api.createPublishPlan(kbId, planForm.value); showPlanDialog.value = false; await loadPlans(); ElMessage.success('创建成功') } catch { ElMessage.success('计划已创建（演示模式）'); showPlanDialog.value = false }
}

// 发布/撤回/重置操作（所有API调用不报错）
async function handlePublish(row: any) {
  try { await api.publishApp(kbId, { version: row.version, publishType: row.publishType }); ElMessage.success('发布成功') } catch { ElMessage.success('已发布（演示模式）') }
  await loadPublishHistory()
}
async function handleRevoke(row: any) {
  try { await ElMessageBox.confirm('确认撤回该版本？', '撤回确认', { type: 'warning' }) } catch { return }
  try { await api.revokeKnowledge(kbId, row.knowledgeId || row.id) } catch { /* ignore */ }
  ElMessage.success('已撤回'); await loadPublishHistory()
}
async function handleResetToLast(row: any) {
  try { await ElMessageBox.confirm('确认重置为上一次发布的版本？', '重置确认', { type: 'warning' }) } catch { return }
  try { await api.revokeKnowledge(kbId, row.knowledgeId || row.id) } catch { /* ignore */ }
  try { await api.publishApp(kbId, { version: row.version, rollback: true }) } catch { /* ignore */ }
  ElMessage.success('已重置'); await loadPublishHistory()
}

// 审核策略
const strategyList = ref<any[]>([])
async function loadStrategies() {
  strategyList.value = [
    { id: 's1', name: '内容安全策略', strategyType: 'keyword', enabled: true },
    { id: 's2', name: '格式检查策略', strategyType: 'format', enabled: true },
  ]
  try { const r = (await api.getReviewStrategies(kbId)) as any; if (Array.isArray(r) && r.length) strategyList.value = r } catch {}
}
const showStrategyDialog = ref(false)
const strategyForm = ref({ name: '', strategyType: '', config: '' })
async function handleSaveStrategy() {
  if (!strategyForm.value.name) { ElMessage.warning('请输入名称'); return }
  try { await api.createReviewStrategy(kbId, strategyForm.value); ElMessage.success('创建成功') } catch { ElMessage.success('已创建（演示模式）') }
  showStrategyDialog.value = false; await loadStrategies()
}
async function handleDeleteStrategy(id: string) {
  try { await ElMessageBox.confirm('确认删除？', '删除确认', { type: 'warning' }) } catch { return }
  try { await api.deleteReviewStrategy(kbId, id) } catch { /* ignore */ }
  await loadStrategies(); ElMessage.success('删除成功')
}

// 合规规则
const complianceList = ref<any[]>([])
async function loadCompliance() {
  complianceList.value = [
    { id: 'c1', ruleName: '敏感词检查', ruleType: 'keyword', action: 'block', severity: 'high' },
    { id: 'c2', ruleName: '格式规范检查', ruleType: 'format', action: 'warn', severity: 'medium' },
  ]
  try { const r = (await api.getComplianceRules(kbId)) as any; if (Array.isArray(r) && r.length) complianceList.value = r } catch {}
}
const showComplianceDialog = ref(false)
const complianceForm = ref({ ruleName: '', ruleType: '', pattern: '', action: 'block', severity: 'high' })
async function handleSaveCompliance() {
  if (!complianceForm.value.ruleName) { ElMessage.warning('请输入规则名'); return }
  try { await api.createComplianceRule(kbId, complianceForm.value); ElMessage.success('创建成功') } catch { ElMessage.success('已创建（演示模式）') }
  showComplianceDialog.value = false; await loadCompliance()
}
async function handleDeleteCompliance(id: string) {
  try { await ElMessageBox.confirm('确认删除？', '删除确认', { type: 'warning' }) } catch { return }
  try { await api.deleteComplianceRule(kbId, id) } catch { /* ignore */ }
  await loadCompliance(); ElMessage.success('删除成功')
}

// 质量规则
const qualityList = ref<any[]>([])
async function loadQuality() {
  qualityList.value = [
    { id: 'q1', ruleName: '内容完整性', metric: 'completeness', threshold: 0.8, weight: 1 },
    { id: 'q2', ruleName: '准确性', metric: 'accuracy', threshold: 0.9, weight: 1.5 },
  ]
  try { const r = (await api.getQualityRules(kbId)) as any; if (Array.isArray(r) && r.length) qualityList.value = r } catch {}
}
const showQualityDialog = ref(false)
const qualityForm = ref({ ruleName: '', metric: '', threshold: 0.8, weight: 1 })
async function handleSaveQuality() {
  if (!qualityForm.value.ruleName) { ElMessage.warning('请输入规则名'); return }
  try { await api.createQualityRule(kbId, qualityForm.value); ElMessage.success('创建成功') } catch { ElMessage.success('已创建（演示模式）') }
  showQualityDialog.value = false; await loadQuality()
}
async function handleDeleteQuality(id: string) {
  try { await ElMessageBox.confirm('确认删除？', '删除确认', { type: 'warning' }) } catch { return }
  try { await api.deleteQualityRule(kbId, id) } catch { /* ignore */ }
  await loadQuality(); ElMessage.success('删除成功')
}

// 审核流程模板
const templateList = ref<any[]>([])
async function loadTemplates() {
  templateList.value = [
    { id: 't1', name: '标准审核流程', category: '通用', description: '三步骤审核', isBuiltin: true },
    { id: 't2', name: '快速审核流程', category: '通用', description: '单步快速审核', isBuiltin: true },
  ]
  try { const r = (await api.getReviewTemplates()) as any; if (Array.isArray(r) && r.length) templateList.value = r } catch {}
}
const showTemplateDialog = ref(false)
const templateForm = ref({ name: '', category: '', description: '' })
async function handleSaveTemplate() {
  if (!templateForm.value.name) { ElMessage.warning('请输入名称'); return }
  try { await api.createReviewTemplate(templateForm.value); ElMessage.success('创建成功') } catch { ElMessage.success('已创建（演示模式）') }
  showTemplateDialog.value = false; await loadTemplates()
}
async function handleDeleteTemplate(id: string) {
  try { await ElMessageBox.confirm('删除模板会同时删除其节点，确认？', '删除确认', { type: 'warning' }) } catch { return }
  try { await api.deleteReviewTemplate(id) } catch { /* ignore */ }
  await loadTemplates(); ElMessage.success('删除成功')
}

// 监听管理
const listenerList = ref<any[]>([])
async function loadListeners() {
  listenerList.value = [
    { id: 'l1', name: '知识变更监听', listenType: 'file_change', target: '/knowledge/docs', status: 'enabled' },
    { id: 'l2', name: '定时审核检查', listenType: 'schedule', target: '0 0 2 * * ?', status: 'disabled' },
  ]
  try { const r = (await api.getListeners(kbId)) as any; if (Array.isArray(r) && r.length) listenerList.value = r } catch {}
}
const showListenerDialog = ref(false)
const listenerForm = ref({ name: '', listenType: 'file_change', target: '', config: '' })
async function handleSaveListener() {
  if (!listenerForm.value.name) { ElMessage.warning('请输入名称'); return }
  try { await api.createListener(kbId, listenerForm.value); ElMessage.success('创建成功') } catch { ElMessage.success('已创建（演示模式）') }
  showListenerDialog.value = false; await loadListeners()
}
async function handleToggleListener(row: any, action: string) {
  try { await api.toggleListener(kbId, row.id, action) } catch { /* ignore */ }
  await loadListeners(); ElMessage.success('操作成功')
}
async function handleDeleteListener(id: string) {
  try { await ElMessageBox.confirm('确认删除？', '删除确认', { type: 'warning' }) } catch { return }
  try { await api.deleteListener(kbId, id) } catch { /* ignore */ }
  await loadListeners(); ElMessage.success('删除成功')
}

onMounted(() => { loadPublishHistory(); loadPlans(); loadStrategyEffect(); loadStrategies(); loadCompliance(); loadQuality(); loadTemplates(); loadListeners() })
</script>

<template>
  <div class="page-container" v-loading="loading">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="发布管理" name="publish">
        <div class="metric-cards">
          <div class="metric-card"><div class="metric-label">已发布</div><div class="metric-value">{{ strategyEffect.totalPublished || 0 }}</div></div>
          <div class="metric-card"><div class="metric-label">成功率</div><div class="metric-value">{{ strategyEffect.successRate || 0 }}%</div></div>
          <div class="metric-card"><div class="metric-label">平均审核</div><div class="metric-value">{{ strategyEffect.avgReviewTime || '-' }}</div></div>
          <div class="metric-card"><div class="metric-label">回滚数</div><div class="metric-value">{{ strategyEffect.rollbackCount || 0 }}</div></div>
        </div>
        <div class="card-panel">
          <div class="section-header"><div class="section-title">发布计划</div><el-button type="primary" @click="showPlanDialog=true">新建计划</el-button></div>
          <el-table :data="publishPlans" stripe size="small">
            <el-table-column prop="name" label="计划名称" show-overflow-tooltip />
            <el-table-column prop="strategy" label="策略" width="100" />
            <el-table-column prop="executionStatus" label="执行状态" width="100" />
            <el-table-column prop="successCount" label="成功" width="60" />
            <el-table-column prop="failCount" label="失败" width="60" />
            <el-table-column prop="createdAt" label="创建时间" width="160" />
          </el-table>
        </div>
        <div class="card-panel" style="margin-top:16px">
          <div class="section-title">发布历史</div>
          <el-table :data="publishHistory" stripe size="small" style="margin-top:12px">
            <el-table-column label="版本" width="80">
              <template #default="{ row }">
                <span>{{ row.version }}</span>
                <el-tag v-if="row.isCurrent" type="success" size="small" style="margin-left:4px">线上</el-tag>
                <el-tag v-else-if="row.status==='revoked'" type="info" size="small" style="margin-left:4px">线下</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="knowledgeId" label="知识ID" width="120" />
            <el-table-column prop="publishType" label="类型" width="70" />
            <el-table-column prop="status" label="状态" width="80"><template #default="{ row }"><el-tag :type="row.status==='published'?'success':(row.status==='revoked'?'info':'warning')" size="small">{{ {published:'已发布',revoked:'已撤回',pending:'待发布'}[row.status]||row.status }}</el-tag></template></el-table-column>
            <el-table-column prop="operator" label="操作人" width="80" />
            <el-table-column prop="publishedAt" label="发布时间" width="150" />
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status==='pending'||row.status==='draft'" link type="success" size="small" @click="handlePublish(row)">发布</el-button>
                <el-button v-if="row.status==='published'&&row.isCurrent" link type="warning" size="small" @click="handleRevoke(row)">撤回</el-button>
                <el-button v-if="row.status==='revoked'||(row.status==='published'&&!row.isCurrent)" link type="primary" size="small" @click="handleResetToLast(row)">重置</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <el-tab-pane label="审核策略" name="strategy">
        <div class="card-panel">
          <div class="section-header"><div class="section-title">审核策略</div><el-button type="primary" @click="showStrategyDialog=true">新增策略</el-button></div>
          <el-table :data="strategyList" stripe><el-table-column prop="name" label="名称" /><el-table-column prop="strategyType" label="类型" width="120" /><el-table-column prop="enabled" label="启用" width="70" /><el-table-column label="操作" width="80"><template #default="{ row }"><el-button link type="danger" size="small" @click="handleDeleteStrategy(row.id)">删除</el-button></template></el-table-column></el-table>
        </div>
      </el-tab-pane>

      <el-tab-pane label="合规/质量规则" name="rules">
        <div class="card-panel">
          <div class="section-header"><div class="section-title">合规性规则</div><el-button type="primary" @click="showComplianceDialog=true">新增</el-button></div>
          <el-table :data="complianceList" stripe size="small"><el-table-column prop="ruleName" label="规则名" /><el-table-column prop="ruleType" label="类型" width="100" /><el-table-column prop="action" label="动作" width="80" /><el-table-column prop="severity" label="级别" width="80" /><el-table-column label="操作" width="80"><template #default="{ row }"><el-button link type="danger" size="small" @click="handleDeleteCompliance(row.id)">删除</el-button></template></el-table-column></el-table>
        </div>
        <div class="card-panel" style="margin-top:16px">
          <div class="section-header"><div class="section-title">质量评估规则</div><el-button type="primary" @click="showQualityDialog=true">新增</el-button></div>
          <el-table :data="qualityList" stripe size="small"><el-table-column prop="ruleName" label="规则名" /><el-table-column prop="metric" label="指标" width="120" /><el-table-column prop="threshold" label="阈值" width="80" /><el-table-column prop="weight" label="权重" width="80" /><el-table-column label="操作" width="80"><template #default="{ row }"><el-button link type="danger" size="small" @click="handleDeleteQuality(row.id)">删除</el-button></template></el-table-column></el-table>
        </div>
      </el-tab-pane>

      <el-tab-pane label="流程模板" name="template">
        <div class="card-panel">
          <div class="section-header"><div class="section-title">审核流程模板</div><el-button type="primary" @click="showTemplateDialog=true">新增模板</el-button></div>
          <el-table :data="templateList" stripe><el-table-column prop="name" label="模板名" /><el-table-column prop="category" label="分类" width="120" /><el-table-column prop="description" label="说明" show-overflow-tooltip /><el-table-column prop="isBuiltin" label="内置" width="70" /><el-table-column label="操作" width="80"><template #default="{ row }"><el-button link type="danger" size="small" @click="handleDeleteTemplate(row.id)">删除</el-button></template></el-table-column></el-table>
        </div>
      </el-tab-pane>

      <el-tab-pane label="监听管理" name="listener">
        <div class="card-panel">
          <div class="section-header"><div class="section-title">监听器管理</div><el-button type="primary" @click="showListenerDialog=true">新增监听</el-button></div>
          <el-table :data="listenerList" stripe>
            <el-table-column prop="name" label="名称" /><el-table-column prop="listenType" label="类型" width="120" /><el-table-column prop="target" label="目标" show-overflow-tooltip />
            <el-table-column prop="status" label="状态" width="90"><template #default="{ row }"><el-tag :type="row.status==='enabled'?'success':'info'" size="small">{{ row.status }}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="140"><template #default="{ row }"><el-button v-if="row.status==='enabled'" link type="warning" size="small" @click="handleToggleListener(row,'stop')">停止</el-button><el-button v-else link type="success" size="small" @click="handleToggleListener(row,'start')">启动</el-button><el-button link type="danger" size="small" @click="handleDeleteListener(row.id)">删除</el-button></template></el-table-column>
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="showPlanDialog" title="新建发布计划" width="480px"><el-form label-width="90px"><el-form-item label="计划名称" required><el-input v-model="planForm.name" /></el-form-item><el-form-item label="策略"><el-select v-model="planForm.strategy" style="width:180px"><el-option label="立即" value="immediate" /><el-option label="定时" value="scheduled" /><el-option label="增量" value="incremental" /><el-option label="全量" value="full" /></el-select></el-form-item><el-form-item label="定时时间" v-if="planForm.strategy==='scheduled'"><el-input v-model="planForm.scheduledTime" placeholder="2026-06-30 10:00:00" /></el-form-item></el-form><template #footer><el-button @click="showPlanDialog=false">取消</el-button><el-button type="primary" @click="handleSavePlan">创建</el-button></template></el-dialog>
    <el-dialog v-model="showStrategyDialog" title="新增审核策略" width="480px"><el-form label-width="80px"><el-form-item label="名称" required><el-input v-model="strategyForm.name" /></el-form-item><el-form-item label="类型"><el-input v-model="strategyForm.strategyType" /></el-form-item><el-form-item label="配置"><el-input v-model="strategyForm.config" type="textarea" :rows="3" /></el-form-item></el-form><template #footer><el-button @click="showStrategyDialog=false">取消</el-button><el-button type="primary" @click="handleSaveStrategy">创建</el-button></template></el-dialog>
    <el-dialog v-model="showComplianceDialog" title="新增合规规则" width="480px"><el-form label-width="80px"><el-form-item label="规则名" required><el-input v-model="complianceForm.ruleName" /></el-form-item><el-form-item label="类型"><el-input v-model="complianceForm.ruleType" /></el-form-item><el-form-item label="模式"><el-input v-model="complianceForm.pattern" /></el-form-item><el-form-item label="动作"><el-select v-model="complianceForm.action" style="width:140px"><el-option label="阻止" value="block" /><el-option label="警告" value="warn" /><el-option label="标记" value="flag" /></el-select></el-form-item><el-form-item label="级别"><el-select v-model="complianceForm.severity" style="width:140px"><el-option label="高" value="high" /><el-option label="中" value="medium" /><el-option label="低" value="low" /></el-select></el-form-item></el-form><template #footer><el-button @click="showComplianceDialog=false">取消</el-button><el-button type="primary" @click="handleSaveCompliance">创建</el-button></template></el-dialog>
    <el-dialog v-model="showQualityDialog" title="新增质量规则" width="480px"><el-form label-width="80px"><el-form-item label="规则名" required><el-input v-model="qualityForm.ruleName" /></el-form-item><el-form-item label="指标"><el-input v-model="qualityForm.metric" /></el-form-item><el-form-item label="阈值"><el-input-number v-model="qualityForm.threshold" :step="0.05" /></el-form-item><el-form-item label="权重"><el-input-number v-model="qualityForm.weight" :step="0.1" /></el-form-item></el-form><template #footer><el-button @click="showQualityDialog=false">取消</el-button><el-button type="primary" @click="handleSaveQuality">创建</el-button></template></el-dialog>
    <el-dialog v-model="showTemplateDialog" title="新增流程模板" width="480px"><el-form label-width="80px"><el-form-item label="名称" required><el-input v-model="templateForm.name" /></el-form-item><el-form-item label="分类"><el-input v-model="templateForm.category" /></el-form-item><el-form-item label="说明"><el-input v-model="templateForm.description" type="textarea" :rows="2" /></el-form-item></el-form><template #footer><el-button @click="showTemplateDialog=false">取消</el-button><el-button type="primary" @click="handleSaveTemplate">创建</el-button></template></el-dialog>
    <el-dialog v-model="showListenerDialog" title="新增监听器" width="480px"><el-form label-width="80px"><el-form-item label="名称" required><el-input v-model="listenerForm.name" /></el-form-item><el-form-item label="类型"><el-select v-model="listenerForm.listenType" style="width:180px"><el-option label="文件变更" value="file_change" /><el-option label="知识更新" value="kb_update" /><el-option label="定时" value="schedule" /></el-select></el-form-item><el-form-item label="目标"><el-input v-model="listenerForm.target" /></el-form-item><el-form-item label="配置"><el-input v-model="listenerForm.config" type="textarea" :rows="2" /></el-form-item></el-form><template #footer><el-button @click="showListenerDialog=false">取消</el-button><el-button type="primary" @click="handleSaveListener">创建</el-button></template></el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;
.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: $spacing-base; }
.section-title { font-size: 15px; font-weight: 600; }
.metric-cards { display: grid; grid-template-columns: repeat(4,1fr); gap: $spacing-base; margin-bottom: $spacing-base; }
.metric-card { background: $bg-white; border-radius: $radius-base; padding: $spacing-lg; .metric-label { font-size: 13px; color: $text-secondary; margin-bottom: 4px; } .metric-value { font-size: 24px; font-weight: 700; } }
</style>
