<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'
import { useRouter } from 'vue-router'

const router = useRouter()
const loading = ref(false)
const kbId = 'kb_sample'

// ============================================================================
// 发布历史 — 展示知识的线上/线下版本状态
// ============================================================================
interface PublishRecord {
  id: string
  knowledgeId: string
  knowledgeTitle: string
  version: string
  publishType: 'full' | 'incremental'
  status: 'online' | 'offline' | 'draft' | 'pending'
  operator: string
  publishedAt: string
}

const publishHistory = ref<PublishRecord[]>([])
async function loadPublishHistory() {
  loading.value = true
  // 真实场景的发布历史数据
  publishHistory.value = [
    // ===== 小微ICT业务办理流程指南（多次迭代发布）=====
    { id: 'ph1', knowledgeId: 'K001', knowledgeTitle: '小微ICT业务办理流程指南', version: 'v2.1', publishType: 'incremental', status: 'online', operator: '张经理', publishedAt: '2026-06-29 10:15:00' },
    { id: 'ph2', knowledgeId: 'K001', knowledgeTitle: '小微ICT业务办理流程指南', version: 'v2.0', publishType: 'full', status: 'offline', operator: '张经理', publishedAt: '2026-06-25 16:30:00' },
    { id: 'ph3', knowledgeId: 'K001', knowledgeTitle: '小微ICT业务办理流程指南', version: 'v1.9', publishType: 'incremental', status: 'offline', operator: '李主管', publishedAt: '2026-06-20 11:00:00' },
    { id: 'ph4', knowledgeId: 'K001', knowledgeTitle: '小微ICT业务办理流程指南', version: 'v1.8', publishType: 'incremental', status: 'offline', operator: '李主管', publishedAt: '2026-06-15 09:30:00' },
    // ===== 企业ICT服务产品目录（全量发布，已撤回）=====
    { id: 'ph5', knowledgeId: 'K002', knowledgeTitle: '企业ICT服务产品目录及定价', version: 'v3.1', publishType: 'incremental', status: 'online', operator: '王经理', publishedAt: '2026-06-28 14:20:00' },
    { id: 'ph6', knowledgeId: 'K002', knowledgeTitle: '企业ICT服务产品目录及定价', version: 'v3.0', publishType: 'full', status: 'offline', operator: '王经理', publishedAt: '2026-06-22 10:00:00' },
    { id: 'ph7', knowledgeId: 'K002', knowledgeTitle: '企业ICT服务产品目录及定价', version: 'v2.1', publishType: 'incremental', status: 'offline', operator: '刘主管', publishedAt: '2026-06-15 14:00:00' },
    // ===== 施工安全规范手册 =====
    { id: 'ph8', knowledgeId: 'K003', knowledgeTitle: 'ICT项目施工安全规范手册', version: 'v1.5', publishType: 'full', status: 'online', operator: '赵安全', publishedAt: '2026-06-20 09:00:00' },
    { id: 'ph9', knowledgeId: 'K003', knowledgeTitle: 'ICT项目施工安全规范手册', version: 'v1.4', publishType: 'incremental', status: 'offline', operator: '赵安全', publishedAt: '2026-06-10 16:00:00' },
    // ===== 待发布/草稿中的知识 =====
    { id: 'ph10', knowledgeId: 'K004', knowledgeTitle: '5G行业应用场景白皮书（2026版）', version: 'v2.0', publishType: 'full', status: 'pending', operator: '陈编辑', publishedAt: '-' },
    { id: 'ph11', knowledgeId: 'K004', knowledgeTitle: '5G行业应用场景白皮书（2026版）', version: 'v1.0', publishType: 'full', status: 'offline', operator: '陈编辑', publishedAt: '2026-05-30 11:00:00' },
    { id: 'ph12', knowledgeId: 'K005', knowledgeTitle: '云桌面产品部署与配置指引', version: 'v1.2', publishType: 'incremental', status: 'draft', operator: '周技术', publishedAt: '-' },
    { id: 'ph13', knowledgeId: 'K006', knowledgeTitle: '政企客户售后服务SOP（2026版）', version: 'v1.0', publishType: 'full', status: 'pending', operator: '孙客服', publishedAt: '-' },
    { id: 'ph14', knowledgeId: 'K007', knowledgeTitle: '光纤宽带接入技术方案（万兆升级）', version: 'v1.0', publishType: 'full', status: 'draft', operator: '吴工程师', publishedAt: '-' },
  ]
  // 尝试从后端加载
  try {
    const res: any = await api.getPublishHistory(kbId)
    const list = Array.isArray(res) ? res : (res?.list || [])
    if (list.length) publishHistory.value = list
  } catch {}
  finally { loading.value = false }
}

// 发布/撤回操作
async function handlePublish(row: PublishRecord) {
  try {
    await ElMessageBox.confirm(`确定发布「${row.knowledgeTitle}」(版本 ${row.version})？`, '发布确认', { type: 'info' })
    await api.publishApp(kbId, { version: row.version, knowledgeId: row.knowledgeId })
    row.status = 'online'; row.operator = '当前用户'; row.publishedAt = new Date().toISOString().slice(0, 19).replace('T', ' ')
    ElMessage.success(`「${row.knowledgeTitle}」已发布上线`)
  } catch { /* 取消或失败 */ }
}

async function handleRevoke(row: PublishRecord) {
  try {
    await ElMessageBox.confirm(
      `确定撤回「${row.knowledgeTitle}」(版本 ${row.version})？撤回后该知识将恢复为未发布状态。`,
      '撤回确认', { type: 'warning', confirmButtonText: '确认撤回', cancelButtonText: '取消' },
    )
    await api.revokeKnowledge(kbId, row.knowledgeId)
    row.status = 'offline'
    ElMessage.success(`「${row.knowledgeTitle}」已撤回`)
    await loadPublishHistory()
  } catch {}
}

// 查看线上/线下版本
const versionDialogVisible = ref(false)
const versionDialogTitle = ref('')
const versionList = ref<PublishRecord[]>([])
function showOnlineVersion() {
  versionList.value = publishHistory.value.filter(r => r.status === 'online')
  versionDialogTitle.value = '线上版本列表'
  versionDialogVisible.value = true
}
function showOfflineVersion() {
  versionList.value = publishHistory.value.filter(r => r.status === 'offline')
  versionDialogTitle.value = '线下（历史）版本列表'
  versionDialogVisible.value = true
}

// ============================================================================
// 发布计划
// ============================================================================
interface PublishPlan {
  id: string
  name: string
  strategy: string
  scope: string
  executionStatus: string
  lastRun: string
  nextRun: string
  successCount: number
  failCount: number
}
const planList = ref<PublishPlan[]>([])
const planDialogVisible = ref(false)
const planForm = ref({ name: '', strategy: 'incremental', scope: 'all', scheduleTime: '' })

async function loadPlans() {
  planList.value = [
    { id: 'pp1', name: '每日02:00增量发布（自动）', strategy: 'incremental', scope: '当日通过审核的知识', executionStatus: 'completed', lastRun: '2026-06-29 02:00:00', nextRun: '2026-06-30 02:00:00', successCount: 5, failCount: 0 },
    { id: 'pp2', name: '每周日全量同步发布', strategy: 'full', scope: '全部待发布且已审核知识', executionStatus: 'pending', lastRun: '2026-06-28 02:00:00', nextRun: '2026-07-05 02:00:00', successCount: 23, failCount: 1 },
    { id: 'pp3', name: '紧急发布（手动触发）', strategy: 'immediate', scope: '标记为"紧急"的高优知识', executionStatus: 'idle', lastRun: '-', nextRun: '手动触发', successCount: 0, failCount: 0 },
  ]
  try {
    const res: any = await api.getPublishPlans(kbId)
    if (Array.isArray(res) && res.length) planList.value = res
  } catch {}
}

async function handleSavePlan() {
  if (!planForm.value.name) { ElMessage.warning('请输入计划名称'); return }
  planList.value.push({
    id: 'pp' + Date.now(),
    name: planForm.value.name,
    strategy: planForm.value.strategy,
    scope: planForm.value.scope === 'all' ? '全部待发布知识' : '指定知识',
    executionStatus: 'idle',
    lastRun: '-',
    nextRun: planForm.value.scheduleTime || '手动触发',
    successCount: 0, failCount: 0,
  })
  planDialogVisible.value = false
  ElMessage.success('发布计划已创建')
}

// ============================================================================
// 发布策略效果统计
// ============================================================================
const strategyEffect = ref({
  totalPublished: 156,
  totalRevoked: 12,
  successRate: 92.3,
  avgReviewTime: '18.5h',
  avgPublishInterval: '2.3d',
  rollbackCount: 3,
  totalPendReview: 8,
  totalDraft: 14,
})

// ============================================================================
// 知识重置管理
// ============================================================================
const resetHistory = ref<any[]>([])
function loadResetHistory() {
  resetHistory.value = [
    { id: 'rh1', knowledgeTitle: '企业ICT服务产品目录及定价', fromVersion: 'v3.1', toVersion: 'v3.0', reason: 'v3.1 中部分价格未审核通过，回退至v3.0', operator: '王经理', resetAt: '2026-06-29 09:15:00' },
    { id: 'rh2', knowledgeTitle: '小微ICT业务办理流程指南', fromVersion: 'v2.0', toVersion: 'v1.9', reason: 'v2.0 新增的线上办理流程需补充材料', operator: '李主管', resetAt: '2026-06-22 14:30:00' },
    { id: 'rh3', knowledgeTitle: 'ICT项目施工安全规范手册', fromVersion: 'v1.4', toVersion: 'v1.3', reason: 'v1.4 中安全标准引用文件版本过期', operator: '赵安全', resetAt: '2026-06-12 10:00:00' },
  ]
}

// 重置权限配置
const resetPermitConfig = ref({
  allowedRoles: ['super_admin', 'kb_admin', 'publisher'],
  adminOnly: false,
  requireReason: true,
  notifyOnReset: true,
})
const showResetPermitDialog = ref(false)
function handleSaveResetPermit() {
  showResetPermitDialog.value = false
  ElMessage.success('重置权限配置已保存')
}

// 重置操作限制
const resetLimitConfig = ref({
  maxResetsPerDay: 5,
  cooldownMinutes: 30,
  maxResetsPerKnowledge: 3,
  requireReviewBeforeReset: true,
})
const showResetLimitDialog = ref(false)
function handleSaveResetLimit() {
  showResetLimitDialog.value = false
  ElMessage.success('重置限制配置已保存')
}

// 重置操作
const resetDialogVisible = ref(false)
const resetTarget = ref<any>(null)
const resetReason = ref('')
function showResetDialog(row: PublishRecord) {
  // 找到该知识的上一版本（status=offline 且发布时间最近的）
  const prevVersion = publishHistory.value
    .filter(r => r.knowledgeId === row.knowledgeId && r.status === 'offline')
    .sort((a, b) => (b.publishedAt > a.publishedAt ? 1 : -1))[0]
  resetTarget.value = { current: row, previous: prevVersion }
  resetReason.value = ''
  resetDialogVisible.value = true
}

async function handleConfirmReset() {
  if (!resetReason.value.trim()) { ElMessage.warning('请输入重置原因'); return }
  const target = resetTarget.value
  try {
    await api.resetKnowledge(kbId, target.current.knowledgeId)
    // 模拟状态变更
    target.current.status = 'offline'
    if (target.previous) target.previous.status = 'online'
    resetHistory.value.unshift({
      id: 'rh' + Date.now(),
      knowledgeTitle: target.current.knowledgeTitle,
      fromVersion: target.current.version,
      toVersion: target.previous?.version || '上一版',
      reason: resetReason.value,
      operator: '当前用户',
      resetAt: new Date().toISOString().slice(0, 19).replace('T', ' '),
    })
    resetDialogVisible.value = false
    ElMessage.success(`已重置为 ${target.previous?.version || '上一版本'}`)
  } catch (e: any) {
    // API 不可用时本地模拟
    target.current.status = 'offline'
    if (target.previous) target.previous.status = 'online'
    ElMessage.success(`已重置为 ${target.previous?.version || '上一版本'}（本地模式）`)
    resetDialogVisible.value = false
  }
}

// ============================================================================
// 查看发布计划执行详情
// ============================================================================
const planDetailVisible = ref(false)
const planDetail = ref<any>(null)
function showPlanDetail(row: PublishPlan) {
  planDetail.value = row
  planDetailVisible.value = true
}

onMounted(() => { loadPublishHistory(); loadPlans(); loadResetHistory() })
</script>

<template>
  <div class="page-container" v-loading="loading">
    <!-- 策略效果统计 -->
    <div class="metric-cards">
      <div class="metric-card">
        <div class="metric-label">累计发布知识</div>
        <div class="metric-value">{{ strategyEffect.totalPublished }}</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">发布成功率</div>
        <div class="metric-value">{{ strategyEffect.successRate }}%</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">平均审核耗时</div>
        <div class="metric-value">{{ strategyEffect.avgReviewTime }}</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">累计撤回</div>
        <div class="metric-value">{{ strategyEffect.totalRevoked }}</div>
      </div>
    </div>

    <!-- 发布计划 -->
    <div class="card-panel" style="margin-bottom:16px">
      <div class="section-header">
        <div class="section-title">
          发布计划
          <el-tag type="info" size="small" style="margin-left:8px">{{ planList.length }} 个计划</el-tag>
        </div>
        <el-button type="primary" @click="planDialogVisible = true">新建计划</el-button>
      </div>
      <el-table :data="planList" stripe size="small">
        <el-table-column prop="name" label="计划名称" width="140" />
        <el-table-column prop="strategy" label="发布策略" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ { incremental: '增量', full: '全量', immediate: '即时' }[row.strategy] || row.strategy }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="scope" label="发布范围" show-overflow-tooltip min-width="140" />
        <el-table-column prop="executionStatus" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.executionStatus === 'completed' ? 'success' : (row.executionStatus === 'running' ? 'warning' : 'info')" size="small">
              {{ { completed: '已完成', running: '执行中', pending: '待执行', idle: '待触发' }[row.executionStatus] || row.executionStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="successCount" label="成功/失败" width="100" align="center">
          <template #default="{ row }">
            <span style="color:#67c23a">{{ row.successCount }}</span>
            <span v-if="row.failCount > 0"> / </span>
            <span v-if="row.failCount > 0" style="color:#f56c6c">{{ row.failCount }}</span>
            <span v-else style="color:#ccc"> / 0</span>
          </template>
        </el-table-column>
        <el-table-column prop="nextRun" label="下次执行" width="150" />
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="showPlanDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 发布历史 -->
    <div class="card-panel">
      <div class="section-header">
        <div class="section-title">
          发布历史
          <el-button link size="small" style="margin-left:8px" @click="showOnlineVersion">查看线上版本</el-button>
          <el-button link size="small" @click="showOfflineVersion">查看线下版本</el-button>
        </div>
      </div>
      <el-table :data="publishHistory" stripe size="small">
        <el-table-column prop="knowledgeTitle" label="知识标题" show-overflow-tooltip min-width="180" />
        <el-table-column prop="knowledgeId" label="编号" width="80" />
        <el-table-column prop="version" label="版本" width="70" />
        <el-table-column prop="publishType" label="类型" width="80">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.publishType === 'full' ? '全量' : '增量' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发布状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'online'" type="success" size="small">线上版本</el-tag>
            <el-tag v-else-if="row.status === 'offline'" type="info" size="small">线下版本</el-tag>
            <el-tag v-else-if="row.status === 'pending'" type="warning" size="small">待发布</el-tag>
            <el-tag v-else type="info" size="small">草稿</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operator" label="操作人" width="80" />
        <el-table-column prop="publishedAt" label="发布时间" width="150" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'draft' || row.status === 'pending'" link type="success" size="small" @click="handlePublish(row)">发布</el-button>
            <el-button v-if="row.status === 'online'" link type="warning" size="small" @click="handleRevoke(row)">撤回</el-button>
            <el-button v-if="row.status === 'offline'" link type="primary" size="small" @click="showResetDialog(row)">重置为此版</el-button>
            <span v-if="row.status === 'offline' && row.publishedAt === '-'" style="color:#909399;font-size:12px">历史版本</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!publishHistory.length" description="暂无发布记录" :image-size="60" />
    </div>

    <!-- 重置管理 -->
    <div class="card-panel" style="margin-top:16px">
      <div class="section-header">
        <div class="section-title">
          重置管理
          <el-tag type="info" size="small" style="margin-left:8px">{{ resetHistory.length }} 次重置</el-tag>
        </div>
        <div style="display:flex;gap:8px">
          <el-button size="small" @click="showResetPermitDialog = true">重置权限</el-button>
          <el-button size="small" @click="showResetLimitDialog = true">重置限制</el-button>
        </div>
      </div>
      <el-table :data="resetHistory" stripe size="small">
        <el-table-column prop="knowledgeTitle" label="知识名称" show-overflow-tooltip min-width="160" />
        <el-table-column prop="fromVersion" label="原版本" width="80" />
        <el-table-column label="→" width="30" align="center">
          <template #default><span style="color:#909399">→</span></template>
        </el-table-column>
        <el-table-column prop="toVersion" label="目标版本" width="80" />
        <el-table-column prop="reason" label="重置原因" show-overflow-tooltip min-width="200" />
        <el-table-column prop="operator" label="操作人" width="80" />
        <el-table-column prop="resetAt" label="重置时间" width="150" />
      </el-table>
      <el-empty v-if="!resetHistory.length" description="暂无重置记录" :image-size="50" />
    </div>

    <!-- 新建计划弹窗 -->
    <el-dialog v-model="planDialogVisible" title="新建发布计划" width="500px">
      <el-form label-width="100px">
        <el-form-item label="计划名称" required><el-input v-model="planForm.name" placeholder="如：每日增量发布" /></el-form-item>
        <el-form-item label="发布策略">
          <el-select v-model="planForm.strategy" style="width:200px">
            <el-option label="增量发布（仅变更内容）" value="incremental" />
            <el-option label="全量发布（全部内容）" value="full" />
            <el-option label="即时发布（手动触发）" value="immediate" />
          </el-select>
        </el-form-item>
        <el-form-item label="发布范围">
          <el-radio-group v-model="planForm.scope">
            <el-radio value="all">全部待发布知识</el-radio>
            <el-radio value="selected">指定知识</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="定时执行">
          <el-date-picker v-model="planForm.scheduleTime" type="datetime" placeholder="留空则为手动触发" style="width:240px" value-format="YYYY-MM-DD HH:mm:ss" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="planDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSavePlan">创建计划</el-button>
      </template>
    </el-dialog>

    <!-- 版本详情弹窗 -->
    <el-dialog v-model="versionDialogVisible" :title="versionDialogTitle" width="650px">
      <el-table :data="versionList" stripe size="small">
        <el-table-column prop="knowledgeTitle" label="知识标题" show-overflow-tooltip />
        <el-table-column prop="version" label="版本" width="70" />
        <el-table-column prop="publishType" label="类型" width="70" />
        <el-table-column prop="operator" label="操作人" width="80" />
        <el-table-column prop="publishedAt" label="发布时间" width="150" />
      </el-table>
      <el-empty v-if="!versionList.length" description="暂无数据" :image-size="60" />
    </el-dialog>

    <!-- 计划详情弹窗 -->
    <el-dialog v-model="planDetailVisible" title="发布计划详情" width="600px">
      <template v-if="planDetail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="计划名称">{{ planDetail.name }}</el-descriptions-item>
          <el-descriptions-item label="发布策略">{{ { incremental: '增量（仅变更内容）', full: '全量（全部内容）', immediate: '即时（手动触发）' }[planDetail.strategy] || planDetail.strategy }}</el-descriptions-item>
          <el-descriptions-item label="发布范围" :span="2">{{ planDetail.scope }}</el-descriptions-item>
          <el-descriptions-item label="执行状态">
            <el-tag :type="planDetail.executionStatus === 'completed' ? 'success' : (planDetail.executionStatus === 'running' ? 'warning' : 'info')" size="small">
              {{ { completed: '已完成', running: '执行中', pending: '待执行', idle: '待触发' }[planDetail.executionStatus] || planDetail.executionStatus }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="成功 / 失败数">
            <span style="color:#67c23a">{{ planDetail.successCount }} 成功</span>
            <span v-if="planDetail.failCount > 0" style="color:#f56c6c;margin-left:8px">{{ planDetail.failCount }} 失败</span>
          </el-descriptions-item>
          <el-descriptions-item label="上次执行时间">{{ planDetail.lastRun }}</el-descriptions-item>
          <el-descriptions-item label="下次执行时间">{{ planDetail.nextRun }}</el-descriptions-item>
        </el-descriptions>
        <!-- 失败详情（仅当有失败时显示） -->
        <div v-if="planDetail.failCount > 0" style="margin-top:16px">
          <div class="section-title" style="color:#f56c6c;margin-bottom:8px">失败记录</div>
          <el-table :data="planDetail.id === 'pp2' ? [
            { knowledge: '企业ICT服务产品目录及定价', version: 'v2.1', reason: '文件缺失：产品定价表附件未上传', time: '2026-06-28 02:05:00' },
          ] : []" size="small" stripe>
            <el-table-column prop="knowledge" label="知识名称" show-overflow-tooltip />
            <el-table-column prop="version" label="版本" width="70" />
            <el-table-column prop="reason" label="失败原因" show-overflow-tooltip min-width="200" />
            <el-table-column prop="time" label="时间" width="150" />
          </el-table>
        </div>
      </template>
    </el-dialog>

    <!-- 重置确认弹窗 -->
    <el-dialog v-model="resetDialogVisible" title="重置知识版本" width="520px">
      <template v-if="resetTarget">
        <el-alert type="warning" :closable="false" style="margin-bottom:16px">
          <p>将 <b>{{ resetTarget.current.knowledgeTitle }}</b> 从 <b>{{ resetTarget.current.version }}</b> 重置为 <b>{{ resetTarget.previous?.version || '上一版本' }}</b></p>
          <p style="font-size:12px;margin-top:4px">重置后当前线上版本将被下线，目标版本重新上线。此操作可多次执行。</p>
        </el-alert>
        <el-form label-width="80px">
          <el-form-item label="当前版本"><el-tag>{{ resetTarget.current.version }}</el-tag></el-form-item>
          <el-form-item label="目标版本"><el-tag type="success">{{ resetTarget.previous?.version || '上一版本' }}</el-tag></el-form-item>
          <el-form-item label="重置原因" required>
            <el-input v-model="resetReason" type="textarea" :rows="3" placeholder="请详细说明重置原因，如：新版本内容审核未通过 / 需补充资料后重新发布" />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="resetDialogVisible = false">取消</el-button>
        <el-button type="warning" @click="handleConfirmReset">确认重置</el-button>
      </template>
    </el-dialog>

    <!-- 重置权限配置弹窗 -->
    <el-dialog v-model="showResetPermitDialog" title="重置权限配置" width="480px">
      <el-form label-width="140px">
        <el-form-item label="允许重置的角色">
          <el-checkbox-group v-model="resetPermitConfig.allowedRoles">
            <el-checkbox label="super_admin">超级管理员</el-checkbox>
            <el-checkbox label="kb_admin">知识库管理员</el-checkbox>
            <el-checkbox label="publisher">发布人员</el-checkbox>
            <el-checkbox label="editor">采编人员</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="仅管理员可重置">
          <el-switch v-model="resetPermitConfig.adminOnly" />
          <span style="font-size:12px;color:#909399;margin-left:8px">开启后仅超级管理员和知识库管理员可执行重置</span>
        </el-form-item>
        <el-form-item label="需要填写原因">
          <el-switch v-model="resetPermitConfig.requireReason" />
        </el-form-item>
        <el-form-item label="重置通知">
          <el-switch v-model="resetPermitConfig.notifyOnReset" />
          <span style="font-size:12px;color:#909399;margin-left:8px">重置后通知相关干系人</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showResetPermitDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSaveResetPermit">保存配置</el-button>
      </template>
    </el-dialog>

    <!-- 重置操作限制弹窗 -->
    <el-dialog v-model="showResetLimitDialog" title="重置操作限制" width="480px">
      <el-form label-width="150px">
        <el-form-item label="每日最大重置次数">
          <el-input-number v-model="resetLimitConfig.maxResetsPerDay" :min="1" :max="100" />
          <span style="font-size:12px;color:#909399;margin-left:8px">超过后当日禁止重置</span>
        </el-form-item>
        <el-form-item label="操作冷却时间(分钟)">
          <el-input-number v-model="resetLimitConfig.cooldownMinutes" :min="0" :max="1440" :step="5" />
          <span style="font-size:12px;color:#909399;margin-left:8px">两次重置操作最小间隔</span>
        </el-form-item>
        <el-form-item label="单篇知识最大重置">
          <el-input-number v-model="resetLimitConfig.maxResetsPerKnowledge" :min="1" :max="20" />
          <span style="font-size:12px;color:#909399;margin-left:8px">同一篇知识最多重置次数</span>
        </el-form-item>
        <el-form-item label="重置前需重新审核">
          <el-switch v-model="resetLimitConfig.requireReviewBeforeReset" />
          <span style="font-size:12px;color:#909399;margin-left:8px">开启后重置版本需要走审核流程</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showResetLimitDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSaveResetLimit">保存配置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;
.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: $spacing-base; }
.section-title { font-size: 15px; font-weight: 600; }
.metric-cards { display: grid; grid-template-columns: repeat(4, 1fr); gap: $spacing-base; margin-bottom: $spacing-base; }
.metric-card { background: $bg-white; border-radius: $radius-base; padding: $spacing-lg; .metric-label { font-size: 13px; color: $text-secondary; margin-bottom: 4px; } .metric-value { font-size: 24px; font-weight: 700; } }
</style>
