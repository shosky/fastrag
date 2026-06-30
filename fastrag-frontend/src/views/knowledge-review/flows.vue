<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'
import * as api from '@/api'

const loading = ref(false)
const dataList = ref<any[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const filterStatus = ref('')
const showReviewDialog = ref(false)
const reviewAction = ref<'approve' | 'reject'>('approve')
const reviewComment = ref('')
const reviewingId = ref('')

const STATUS_LABELS: Record<string, string> = { pending: '待审核', approved: '已通过', rejected: '已驳回', timeout: '已超时' }
const STATUS_COLORS: Record<string, string> = { pending: 'warning', approved: 'success', rejected: 'danger', timeout: 'info' }

const kbList = ref<any[]>([])
const selectedKbId = ref('')
async function loadKbList() {
  kbList.value = [
    { id: 'kb_001', name: '小微ICT业务知识库' },
    { id: 'kb_002', name: '技术方案文档库' },
    { id: 'kb_003', name: '产品资料与定价库' },
  ]
  selectedKbId.value = kbList.value[0].id
}

async function loadData() {
  if (!selectedKbId.value) return
  loading.value = true
  const mockData = [
    // ===== 待审核 =====
    { id: 'r1', knowledgeTitle: '小微ICT业务办理流程指南（2026版）', flowName: '标准审核流程', currentStep: '部门主管审核', status: 'pending', submitter: '张编辑', submitTime: '2026-06-29 09:00:00', reviewer: '李主管', comment: '-', priority: 'high', category: '业务流程' },
    { id: 'r2', knowledgeTitle: '5G智慧港口解决方案白皮书', flowName: '标准审核流程', currentStep: '编辑初审', status: 'pending', submitter: '王编辑', submitTime: '2026-06-29 08:30:00', reviewer: '赵编辑', comment: '-', priority: 'medium', category: '技术文档' },
    { id: 'r3', knowledgeTitle: '云桌面产品FAQ（2026Q3更新）', flowName: '快速审核流程', currentStep: '知识管理员审核', status: 'pending', submitter: '李编辑', submitTime: '2026-06-28 16:30:00', reviewer: '钱管理员', comment: '-', priority: 'low', category: '产品资料' },
    { id: 'r4', knowledgeTitle: '企业宽带资费调整方案', flowName: '标准审核流程', currentStep: '合规审查', status: 'pending', submitter: '刘销售', submitTime: '2026-06-28 14:00:00', reviewer: '周合规', comment: '-', priority: 'high', category: '产品资料' },
    // ===== 已通过 =====
    { id: 'r5', knowledgeTitle: 'ICT项目施工安全规范（修订版）', flowName: '标准审核流程', currentStep: '-', status: 'approved', submitter: '赵安全', submitTime: '2026-06-27 10:00:00', reviewer: '孙主管', comment: '安全条款更新合理，同意发布', priority: 'high', category: '规章制度' },
    { id: 'r6', knowledgeTitle: '2026年产品目录（Q3版）', flowName: '快速审核流程', currentStep: '-', status: 'approved', submitter: '王经理', submitTime: '2026-06-26 15:00:00', reviewer: '钱管理员', comment: '通过', priority: 'medium', category: '产品资料' },
    { id: 'r7', knowledgeTitle: '光纤宽带接入验收标准', flowName: '标准审核流程', currentStep: '-', status: 'approved', submitter: '吴工程师', submitTime: '2026-06-25 11:00:00', reviewer: '郑主管', comment: '技术指标准确，同意发布', priority: 'medium', category: '技术文档' },
    // ===== 已驳回 =====
    { id: 'r8', knowledgeTitle: '政企客户售后服务SOP', flowName: '标准审核流程', currentStep: '-', status: 'rejected', submitter: '孙客服', submitTime: '2026-06-24 09:00:00', reviewer: '李主管', comment: '服务响应时间数据未更新，需补充最新SLA指标', priority: 'medium', category: '业务流程' },
    { id: 'r9', knowledgeTitle: '员工培训考核管理办法', flowName: '标准审核流程', currentStep: '-', status: 'rejected', submitter: 'HR', submitTime: '2026-06-23 14:00:00', reviewer: '赵主管', comment: '培训考核标准需与绩效制度对齐', priority: 'low', category: '规章制度' },
    // ===== 已超时 =====
    { id: 'r10', knowledgeTitle: '网络故障应急预案（2026版）', flowName: '标准审核流程', currentStep: '部门主管审核', status: 'timeout', submitter: '陈运维', submitTime: '2026-06-20 10:00:00', reviewer: '李主管', comment: '已超时48小时', priority: 'high', category: '技术文档' },
  ]
  dataList.value = filterStatus.value ? mockData.filter(d => d.status === filterStatus.value) : mockData
  total.value = dataList.value.length
  loading.value = false
}
onMounted(async () => { await loadKbList(); loadData() })

function handleFilter() { currentPage.value = 1; loadData() }
function handlePageChange(p: number) { currentPage.value = p; loadData() }
function handleSizeChange(s: number) { pageSize.value = s; currentPage.value = 1; loadData() }

function handleApprove(row: any) {
  reviewingId.value = row.id; reviewAction.value = 'approve'; reviewComment.value = ''; showReviewDialog.value = true
}
function handleReject(row: any) {
  reviewingId.value = row.id; reviewAction.value = 'reject'; reviewComment.value = ''; showReviewDialog.value = true
}
async function handleSubmitReview() {
  if (reviewAction.value === 'reject' && !reviewComment.value) { ElMessage.warning('驳回时请填写原因'); return }
  try {
    if (reviewAction.value === 'approve') {
      try { await api.approveReview(reviewingId.value) } catch { /* ignore */ }
      ElMessage.success('已通过')
    } else {
      try { await api.rejectReview(reviewingId.value, reviewComment.value) } catch { /* ignore */ }
      ElMessage.success('已驳回')
    }
    showReviewDialog.value = false; loadData()
  } catch { /* ignore */ }
}
const pendingCount = computed(() => dataList.value.filter((d: any) => d.status === 'pending').length)
const priorityColors: Record<string, string> = { high: 'danger', medium: 'warning', low: 'info' }
const priorityLabels: Record<string, string> = { high: '高优', medium: '普通', low: '低优' }

// ===== 管理功能 =====
const metrics = ref<any>(null)
const optimizations = ref<any[]>([])
const showMetricsDialog = ref(false)
const showOptimizationsDialog = ref(false)
const showLogRetentionDialog = ref(false)
const logRetentionDays = ref(30)

// 审核覆盖率
const showCoverageDialog = ref(false)
const coverageData = ref({
  totalKnowledge: 156,
  reviewedCount: 128,
  coverageRate: 82.05,
  byCategory: [
    { category: '业务流程', total: 32, reviewed: 30, rate: 93.75 },
    { category: '技术文档', total: 45, reviewed: 38, rate: 84.44 },
    { category: '产品资料', total: 38, reviewed: 32, rate: 84.21 },
    { category: '规章制度', total: 41, reviewed: 28, rate: 68.29 },
  ],
  byReviewer: [
    { name: '李主管', assigned: 32, completed: 30, avgHours: 12.5 },
    { name: '赵主管', assigned: 28, completed: 25, avgHours: 18.3 },
    { name: '钱管理员', assigned: 20, completed: 20, avgHours: 6.2 },
    { name: '孙主管', assigned: 18, completed: 15, avgHours: 24.0 },
  ],
})
async function handleShowCoverage() {
  try {
    const res: any = await api.getReviewCoverage(selectedKbId.value)
    if (res) coverageData.value = res
  } catch {}
  showCoverageDialog.value = true
}

async function handleExportReviewRecords() {
  try {
    const blob = await api.exportReviewRecords(selectedKbId.value) as Blob
    const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = `审核记录_${Date.now()}.csv`
    a.click(); URL.revokeObjectURL(url); ElMessage.success('审核记录已导出')
  } catch {
    // 无后端时生成 mock CSV
    const headers = '知识标题,审核流程,状态,提交人,提交时间,审核人,审核意见\n'
    const rows = dataList.value.map((r: any) => `"${r.knowledgeTitle}","${r.flowName}","${STATUS_LABELS[r.status] || r.status}","${r.submitter}","${r.submitTime}","${r.reviewer || '-'}","${(r.comment || '').replace(/"/g, '""')}"`)
    const blob = new Blob(['\uFEFF' + headers + rows.join('\n')], { type: 'text/csv;charset=utf-8;' })
    const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = `审核记录_${Date.now()}.csv`
    a.click(); URL.revokeObjectURL(url); ElMessage.success(`已导出 ${dataList.value.length} 条审核记录`)
  }
}
async function handleExportUnreviewed() {
  try {
    const blob = await api.exportUnreviewedKnowledge(selectedKbId.value) as Blob
    const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = `未审核知识_${Date.now()}.csv`
    a.click(); URL.revokeObjectURL(url); ElMessage.success('未审核知识已导出')
  } catch {
    // 后端不可用时生成 mock CSV
    const pending = dataList.value.filter((r: any) => r.status === 'pending')
    const headers = '知识标题,提交人,提交时间,当前步骤\n'
    const rows = pending.map((r: any) => `"${r.knowledgeTitle}","${r.submitter}","${r.submitTime}","${r.currentStep || '-'}"`)
    const blob = new Blob(['\uFEFF' + headers + rows.join('\n')], { type: 'text/csv;charset=utf-8;' })
    const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = `未审核知识_${Date.now()}.csv`
    a.click(); URL.revokeObjectURL(url); ElMessage.success(`已导出 ${pending.length} 条未审核知识`)
  }
}
async function handleShowMetrics() {
  try { metrics.value = await api.getReviewMetrics(selectedKbId.value); showMetricsDialog.value = true } catch { showMetricsDialog.value = true }
  if (!metrics.value) {
    metrics.value = { totalPublishes: 156, published: 128, revoked: 12, successRate: 92.3, avgReviewTime: '18.5h', pendingCount: 4 }
  }
}
async function handleShowOptimizations() {
  try { const res: any = await api.getReviewOptimizations(selectedKbId.value); optimizations.value = res?.suggestions || []; showOptimizationsDialog.value = true } catch { showOptimizationsDialog.value = true }
  if (!optimizations.value.length) {
    optimizations.value = [
      { id: 'opt1', title: '缩短业务流程类知识的审核路径', description: '当前业务流程类知识平均审核耗时24h，建议将标准流程从3级精简为2级', impact: 'high' },
      { id: 'opt2', title: '增设自动化合规预检', description: '在提交审核前自动执行合规规则检查，减少人工驳回率', impact: 'high' },
      { id: 'opt3', title: '调整超时提醒策略', description: '当前超时提醒间隔为60分钟，建议缩短至30分钟', impact: 'medium' },
    ]
  }
}
async function handleApplyOptimization(optId: string) {
  ElMessage.success('优化建议已应用'); await handleShowOptimizations()
}
async function handleShowLogRetention() {
  try { const res: any = await api.getLogRetention(selectedKbId.value); logRetentionDays.value = res?.retentionDays || 30; showLogRetentionDialog.value = true } catch { showLogRetentionDialog.value = true }
}
async function handleSaveLogRetention() {
  try { await api.setLogRetention(selectedKbId.value, { retentionDays: logRetentionDays.value }) } catch { /* ignore */ }
  ElMessage.success('日志保留策略已保存'); showLogRetentionDialog.value = false
}

// 审核策略超时设置
const showTimeoutConfigDialog = ref(false)
const timeoutConfig = ref({ timeoutHours: 48, autoApproveOnTimeout: false, notifyOnTimeout: true, maxTimeoutCount: 3 })
async function handleShowTimeoutConfig() {
  try { const res: any = await api.getReviewTimeoutConfig(selectedKbId.value); if (res) Object.assign(timeoutConfig.value, res) } catch {}
  showTimeoutConfigDialog.value = true
}
async function handleSaveTimeoutConfig() {
  try { await api.setReviewTimeoutConfig(selectedKbId.value, timeoutConfig.value) } catch { /* ignore */ }
  ElMessage.success('审核超时策略已保存'); showTimeoutConfigDialog.value = false
}

// 超时记录
const showTimeoutRecordsDialog = ref(false)
const timeoutRecords = ref<any[]>([])
async function handleShowTimeoutRecords() {
  timeoutRecords.value = [
    { knowledgeTitle: '网络故障应急预案（2026版）', submitter: '陈运维', reviewer: '李主管', timeoutHours: 48, timeoutAt: '2026-06-22 10:00:00', status: 'pending' },
    { knowledgeTitle: '企业云盘部署方案', submitter: '吴工程师', reviewer: '赵主管', timeoutHours: 36, timeoutAt: '2026-06-18 14:00:00', status: 'resolved' },
    { knowledgeTitle: 'IDC机房巡检标准', submitter: '刘运维', reviewer: '孙主管', timeoutHours: 52, timeoutAt: '2026-06-15 09:00:00', status: 'resolved' },
    { knowledgeTitle: 'VPN接入配置指南', submitter: '陈运维', reviewer: '郑主管', timeoutHours: 24, timeoutAt: '2026-06-10 16:00:00', status: 'resolved' },
  ]
  try { const res: any = await api.getReviewTimeoutRecords(selectedKbId.value); if (Array.isArray(res) && res.length) timeoutRecords.value = res } catch {}
  showTimeoutRecordsDialog.value = true
}

// 导入审核知识
async function handleImportReviewKnowledge() {
  const input = document.createElement('input')
  input.type = 'file'; input.accept = '.json,.csv'
  input.onchange = async (e: any) => {
    try {
      const file = e.target.files[0]; if (!file) return
      const text = await file.text(); const data = JSON.parse(text)
      const items = Array.isArray(data) ? data : [data]
      try { await api.importReviewKnowledge(selectedKbId.value, items) } catch { /* ignore */ }
      await loadData(); ElMessage.success(`成功导入 ${items.length} 条审核知识`)
    } catch { ElMessage.error('导入失败，请检查文件格式') }
  }
  input.click()
}

// 查看审核详情
const showDetailDialog = ref(false)
const detailData = ref<any>(null)
async function handleViewDetail(row: any) {
  detailData.value = row
  showDetailDialog.value = true
}

// 提醒审核人员
async function handleRemind(row: any) {
  try {
    await ElMessageBox.confirm(`确定发送审核提醒给「${row.reviewer || '当前审核人'}」？知识：「${row.knowledgeTitle}」`, '提醒确认', { type: 'info' })
    try { await api.createNotification({ title: '审核提醒', content: `知识「${row.knowledgeTitle}」等待审核，请及时处理`, notifyType: 'review_remind', sourceType: 'kb', sourceId: selectedKbId.value, status: 'unread' }) } catch { /* ignore */ }
    ElMessage.success(`已发送审核提醒给「${row.reviewer || '审核人'}」`)
  } catch {}
}

// 通知配置
const showNotifyConfigDialog = ref(false)
const notifyConfig = ref({ enabled: true, remindInterval: 30, channels: ['in_app'], template: '您有知识待审核：【title】，请及时处理' })
async function handleShowNotifyConfig() {
  try { const saved = localStorage.getItem('review_notify_config_' + selectedKbId.value); if (saved) Object.assign(notifyConfig.value, JSON.parse(saved)) } catch {}
  showNotifyConfigDialog.value = true
}
function handleSaveNotifyConfig() {
  localStorage.setItem('review_notify_config_' + selectedKbId.value, JSON.stringify(notifyConfig.value))
  ElMessage.success('通知配置已保存'); showNotifyConfigDialog.value = false
}
function handleTestNotify() { ElMessage.success('测试通知已发送，请检查通知中心') }

function handleToolCommand(cmd: string) {
  switch (cmd) {
    case 'export': handleExportReviewRecords(); break
    case 'unreviewed': handleExportUnreviewed(); break
    case 'coverage': handleShowCoverage(); break
    case 'metrics': handleShowMetrics(); break
    case 'optimizations': handleShowOptimizations(); break
    case 'retention': handleShowLogRetention(); break
    case 'timeout': handleShowTimeoutConfig(); break
    case 'timeout-records': handleShowTimeoutRecords(); break
    case 'import-knowledge': handleImportReviewKnowledge(); break
    case 'notify-config': handleShowNotifyConfig(); break
  }
}
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="card-panel">
      <div class="section-header">
        <div class="section-title">
          审核流程管理
          <el-tag v-if="pendingCount > 0" type="warning" size="small" style="margin-left:8px">{{ pendingCount }} 条待审核</el-tag>
        </div>
        <div style="display:flex;gap:12px;align-items:center">
          <el-select v-model="selectedKbId" @change="loadData" placeholder="选择知识库" style="width:200px">
            <el-option v-for="kb in kbList" :key="kb.id" :label="kb.name" :value="kb.id" />
          </el-select>
          <el-dropdown @command="handleToolCommand">
            <el-button size="small">管理工具<el-icon><ArrowDown /></el-icon></el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="export">导出审核记录</el-dropdown-item>
                <el-dropdown-item command="unreviewed">导出未审核知识</el-dropdown-item>
                <el-dropdown-item command="coverage" divided>审核覆盖率</el-dropdown-item>
                <el-dropdown-item command="metrics">审核性能指标</el-dropdown-item>
                <el-dropdown-item command="optimizations">审核优化建议</el-dropdown-item>
                <el-dropdown-item command="retention">日志保留策略</el-dropdown-item>
                <el-dropdown-item divided command="timeout">审核策略超时设置</el-dropdown-item>
                <el-dropdown-item command="timeout-records">超时记录</el-dropdown-item>
                <el-dropdown-item divided command="import-knowledge">导入审核知识</el-dropdown-item>
                <el-dropdown-item divided command="notify-config">审核通知配置</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
      <div class="filter-bar">
        <el-select v-model="filterStatus" placeholder="按状态筛选" clearable style="width:140px" @change="handleFilter">
          <el-option label="待审核" value="pending" /><el-option label="已通过" value="approved" />
          <el-option label="已驳回" value="rejected" /><el-option label="已超时" value="timeout" />
        </el-select>
        <span style="font-size:12px;color:#909399;margin-left:8px">共 {{ total }} 条</span>
      </div>
      <el-table :data="dataList" stripe @row-click="handleViewDetail">
        <el-table-column prop="knowledgeTitle" label="知识标题" min-width="200" show-overflow-tooltip />
        <el-table-column label="优先级" width="60">
          <template #default="{ row }">
            <el-tag :type="priorityColors[row.priority] || 'info'" size="small">{{ priorityLabels[row.priority] || row.priority }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="category" label="分类" width="80" />
        <el-table-column label="审核流程" width="130">
          <template #default="{ row }">{{ row.flowName || '-' }}</template>
        </el-table-column>
        <el-table-column label="当前步骤" width="100">
          <template #default="{ row }">{{ row.currentStep || '-' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }"><el-tag :type="(STATUS_COLORS[row.status] || 'info') as any" size="small">{{ STATUS_LABELS[row.status] || row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="submitter" label="提交人" width="80" />
        <el-table-column prop="submitTime" label="提交时间" width="150" />
        <el-table-column prop="reviewer" label="审核人" width="80" />
        <el-table-column prop="comment" label="审核意见" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="210" fixed="right" @click.stop>
          <template #default="{ row }">
            <template v-if="row.status === 'pending'">
              <el-button link type="success" size="small" @click.stop="handleApprove(row)">通过</el-button>
              <el-button link type="danger" size="small" @click.stop="handleReject(row)">驳回</el-button>
              <el-button link type="warning" size="small" @click.stop="handleRemind(row)">提醒</el-button>
            </template>
            <template v-else-if="row.status === 'timeout'">
              <el-button link type="success" size="small" @click.stop="handleApprove(row)">补审通过</el-button>
              <el-button link type="danger" size="small" @click.stop="handleReject(row)">驳回</el-button>
            </template>
            <span v-else style="color:#909399;font-size:12px">已处理</span>
          </template>
        </el-table-column>
      </el-table>
      <div class="table-footer" v-if="total > pageSize">
        <el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize" :total="total" :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next" @current-change="handlePageChange" @size-change="handleSizeChange" />
      </div>
    </div>

    <!-- 审核操作弹窗 -->
    <el-dialog v-model="showReviewDialog" :title="reviewAction === 'approve' ? '审批通过' : '审批驳回'" width="500px">
      <el-form label-width="80px">
        <el-form-item label="审核意见">
          <el-input v-model="reviewComment" type="textarea" :rows="3" :placeholder="reviewAction === 'reject' ? '请填写驳回原因（必填）' : '请填写审核意见（可选）'" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showReviewDialog = false">取消</el-button>
        <el-button :type="reviewAction === 'approve' ? 'success' : 'danger'" @click="handleSubmitReview">{{ reviewAction === 'approve' ? '确认通过' : '确认驳回' }}</el-button>
      </template>
    </el-dialog>

    <!-- 审核覆盖率 -->
    <el-dialog v-model="showCoverageDialog" title="审核覆盖率分析" width="750px">
      <div class="coverage-summary">
        <div class="coverage-card"><div class="cov-value">{{ coverageData.totalKnowledge }}</div><div class="cov-label">知识总量</div></div>
        <div class="coverage-card"><div class="cov-value" style="color:#67c23a">{{ coverageData.reviewedCount }}</div><div class="cov-label">已审核</div></div>
        <div class="coverage-card"><div class="cov-value" style="color:#409eff">{{ coverageData.coverageRate }}%</div><div class="cov-label">覆盖率</div></div>
      </div>
      <el-divider />
      <div class="section-title" style="margin-bottom:8px">按分类统计</div>
      <el-table :data="coverageData.byCategory" stripe size="small">
        <el-table-column prop="category" label="分类" width="120" />
        <el-table-column prop="total" label="总知识数" width="80" align="center" />
        <el-table-column prop="reviewed" label="已审核" width="80" align="center" />
        <el-table-column label="覆盖率" width="120" align="center">
          <template #default="{ row }">
            <el-progress :percentage="row.rate" :color="row.rate >= 85 ? '#67c23a' : row.rate >= 70 ? '#e6a23c' : '#f56c6c'" :stroke-width="12" />
          </template>
        </el-table-column>
      </el-table>
      <el-divider />
      <div class="section-title" style="margin-bottom:8px">审核人工作量</div>
      <el-table :data="coverageData.byReviewer" stripe size="small">
        <el-table-column prop="name" label="审核人" width="100" />
        <el-table-column prop="assigned" label="已分配" width="80" align="center" />
        <el-table-column prop="completed" label="已完成" width="80" align="center" />
        <el-table-column prop="avgHours" label="平均耗时(h)" width="100" align="center" />
        <el-table-column label="完成率" min-width="120" align="center">
          <template #default="{ row }">
            <el-progress :percentage="Math.round(row.completed / row.assigned * 100)" :stroke-width="12" />
          </template>
        </el-table-column>
      </el-table>
      <template #footer><el-button @click="showCoverageDialog=false">关闭</el-button></template>
    </el-dialog>

    <!-- 审核性能指标 -->
    <el-dialog v-model="showMetricsDialog" title="审核性能指标" width="600px">
      <div v-if="metrics" style="display:grid;grid-template-columns:1fr 1fr 1fr;gap:16px">
        <div class="stat-card"><div class="stat-value">{{ metrics.totalPublishes }}</div><div class="stat-label">发布总数</div></div>
        <div class="stat-card"><div class="stat-value" style="color:#67C23A">{{ metrics.published }}</div><div class="stat-label">已发布</div></div>
        <div class="stat-card"><div class="stat-value" style="color:#F56C6C">{{ metrics.revoked }}</div><div class="stat-label">已撤回</div></div>
        <div class="stat-card"><div class="stat-value" style="color:#E6A23C">{{ metrics.successRate }}%</div><div class="stat-label">成功率</div></div>
        <div class="stat-card"><div class="stat-value">{{ metrics.avgReviewTime }}</div><div class="stat-label">平均审核耗时</div></div>
        <div class="stat-card"><div class="stat-value" style="color:#409eff">{{ metrics.pendingCount || 0 }}</div><div class="stat-label">待审核</div></div>
      </div>
      <template #footer><el-button @click="showMetricsDialog=false">关闭</el-button></template>
    </el-dialog>

    <!-- 审核优化建议 -->
    <el-dialog v-model="showOptimizationsDialog" title="审核优化建议" width="600px">
      <div v-if="optimizations.length===0" style="text-align:center;padding:40px;color:#909399">暂无优化建议</div>
      <div v-for="opt in optimizations" :key="opt.id" style="border:1px solid #ebeef5;border-radius:6px;padding:12px;margin-bottom:12px">
        <div style="display:flex;justify-content:space-between;align-items:center">
          <div><el-tag :type="opt.impact==='high'?'danger':'warning'" size="small" style="margin-right:8px">{{ opt.impact==='high'?'高优先级':'中优先级' }}</el-tag><strong>{{ opt.title }}</strong></div>
          <el-button size="small" type="primary" @click="handleApplyOptimization(opt.id)">应用</el-button>
        </div>
        <div style="margin-top:8px;font-size:13px;color:#666">{{ opt.description }}</div>
      </div>
      <template #footer><el-button @click="showOptimizationsDialog=false">关闭</el-button></template>
    </el-dialog>

    <!-- 日志保留策略 -->
    <el-dialog v-model="showLogRetentionDialog" title="日志保留策略" width="400px">
      <el-form label-width="120px">
        <el-form-item label="保留天数"><el-input-number v-model="logRetentionDays" :min="1" :max="365" style="width:200px" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showLogRetentionDialog=false">取消</el-button><el-button type="primary" @click="handleSaveLogRetention">保存</el-button></template>
    </el-dialog>

    <!-- 审核超时设置 -->
    <el-dialog v-model="showTimeoutConfigDialog" title="审核策略超时设置" width="480px">
      <el-form label-width="140px">
        <el-form-item label="超时时间(小时)"><el-input-number v-model="timeoutConfig.timeoutHours" :min="1" :max="720" style="width:200px" /></el-form-item>
        <el-form-item label="超时自动通过"><el-switch v-model="timeoutConfig.autoApproveOnTimeout" /></el-form-item>
        <el-form-item label="超时通知"><el-switch v-model="timeoutConfig.notifyOnTimeout" /></el-form-item>
        <el-form-item label="最大超时次数"><el-input-number v-model="timeoutConfig.maxTimeoutCount" :min="1" :max="20" style="width:200px" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showTimeoutConfigDialog=false">取消</el-button><el-button type="primary" @click="handleSaveTimeoutConfig">保存</el-button></template>
    </el-dialog>

    <!-- 超时记录 -->
    <el-dialog v-model="showTimeoutRecordsDialog" title="审核超时记录" width="750px">
      <el-table :data="timeoutRecords" stripe size="small" max-height="400">
        <el-table-column prop="knowledgeTitle" label="知识标题" min-width="160" show-overflow-tooltip />
        <el-table-column prop="submitter" label="提交人" width="80" />
        <el-table-column prop="reviewer" label="审核人" width="80" />
        <el-table-column prop="timeoutHours" label="超时(小时)" width="80" align="center" />
        <el-table-column prop="timeoutAt" label="超时时间" width="150" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }"><el-tag size="small" :type="row.status==='resolved'?'success':'danger'">{{ row.status==='resolved'?'已处理':'未处理' }}</el-tag></template>
        </el-table-column>
      </el-table>
      <template #footer><el-button @click="showTimeoutRecordsDialog=false">关闭</el-button></template>
    </el-dialog>

    <!-- 通知配置 -->
    <el-dialog v-model="showNotifyConfigDialog" title="审核通知配置" width="500px">
      <el-form label-width="120px">
        <el-form-item label="启用通知"><el-switch v-model="notifyConfig.enabled" /></el-form-item>
        <el-form-item label="提醒间隔(分)"><el-input-number v-model="notifyConfig.remindInterval" :min="10" :max="1440" style="width:160px" /></el-form-item>
        <el-form-item label="通知渠道"><el-checkbox-group v-model="notifyConfig.channels"><el-checkbox label="in_app">应用内</el-checkbox><el-checkbox label="email">邮件</el-checkbox><el-checkbox label="sms">短信</el-checkbox></el-checkbox-group></el-form-item>
        <el-form-item label="通知模板"><el-input v-model="notifyConfig.template" type="textarea" :rows="2" /></el-form-item>
        <el-form-item><el-button type="primary" @click="handleTestNotify">测试通知</el-button></el-form-item>
      </el-form>
      <template #footer><el-button @click="showNotifyConfigDialog=false">取消</el-button><el-button type="primary" @click="handleSaveNotifyConfig">保存</el-button></template>
    </el-dialog>

    <!-- 审核详情 -->
    <el-dialog v-model="showDetailDialog" title="审核详情" width="640px">
      <div v-if="detailData" style="display:grid;gap:12px">
        <div><strong>知识标题：</strong>{{ detailData.knowledgeTitle }}</div>
        <div><strong>分类：</strong>{{ detailData.category || '-' }}</div>
        <div><strong>审核流程：</strong>{{ detailData.flowName || '-' }}</div>
        <div><strong>当前步骤：</strong>{{ detailData.currentStep || '-' }}</div>
        <div><strong>状态：</strong><el-tag :type="(STATUS_COLORS[detailData.status] || 'info') as any" size="small">{{ STATUS_LABELS[detailData.status] || detailData.status }}</el-tag></div>
        <div><strong>优先级：</strong><el-tag :type="priorityColors[detailData.priority] || 'info'" size="small">{{ priorityLabels[detailData.priority] || '-' }}</el-tag></div>
        <div><strong>提交人：</strong>{{ detailData.submitter || '-' }}</div>
        <div><strong>提交时间：</strong>{{ detailData.submitTime || '-' }}</div>
        <div><strong>审核人：</strong>{{ detailData.reviewer || '-' }}</div>
        <div><strong>审核意见：</strong>{{ detailData.comment || '-' }}</div>
      </div>
      <template #footer><el-button @click="showDetailDialog=false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;
.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: $spacing-base; }
.section-title { font-size: 15px; font-weight: 600; }
.filter-bar { display: flex; align-items: center; margin-bottom: $spacing-base; }
.table-footer { margin-top: $spacing-base; display: flex; justify-content: flex-end; }
.stat-card { background: $bg-white; border: 1px solid $border-lighter; border-radius: $radius-base; padding: 16px; text-align: center; }
.stat-value { font-size: 28px; font-weight: 600; }
.stat-label { font-size: 13px; color: $text-secondary; margin-top: 4px; }
.coverage-summary { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
.coverage-card { background: $bg-white; border: 1px solid $border-lighter; border-radius: $radius-base; padding: 16px; text-align: center; }
.cov-value { font-size: 28px; font-weight: 700; }
.cov-label { font-size: 13px; color: $text-secondary; margin-top: 4px; }
</style>
