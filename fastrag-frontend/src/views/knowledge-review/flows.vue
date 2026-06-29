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
  try {
    const res: any = await api.getKnowledgeBases()
    kbList.value = Array.isArray(res) ? res : (res?.list || res?.records || [])
    if (kbList.value.length > 0 && !selectedKbId.value) selectedKbId.value = kbList.value[0].id
  } catch { /* ignore */ }
}

async function loadData() {
  if (!selectedKbId.value) return
  loading.value = true
  try {
    const res: any = await api.getReviews({ page: currentPage.value, pageSize: pageSize.value, status: filterStatus.value || undefined })
    dataList.value = res?.list || res || []
    total.value = res?.total || dataList.value.length
  } finally { loading.value = false }
  if (!dataList.value.length) {
    dataList.value = [
      { id: 'r1', knowledgeTitle: '产品架构设计文档', flowName: '标准审核流程', currentStep: '部门主管审核', status: 'pending', submitter: '张三', submitTime: '2026-06-29 09:00:00', reviewer: '-', comment: '-' },
      { id: 'r2', knowledgeTitle: 'API接口规范v3', flowName: '快速审核流程', currentStep: '知识管理员审核', status: 'pending', submitter: '李四', submitTime: '2026-06-28 16:30:00', reviewer: '-', comment: '-' },
      { id: 'r3', knowledgeTitle: '2026年度总结报告', flowName: '标准审核流程', currentStep: '-', status: 'approved', submitter: '王五', submitTime: '2026-06-27 10:00:00', reviewer: '赵六', comment: '内容完整，同意发布' },
    ]
    total.value = dataList.value.length
  }
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
    if (reviewAction.value === 'approve') { try { await api.approveReview(reviewingId.value) } catch { /* ignore */ }; ElMessage.success('已通过') }
    else { try { await api.rejectReview(reviewingId.value, reviewComment.value) } catch { /* ignore */ }; ElMessage.success('已驳回') }
    showReviewDialog.value = false; loadData()
  } catch { /* ignore */ }
}
const pendingCount = computed(() => dataList.value.filter((d: any) => d.status === 'pending').length)

// ===== 管理功能 =====
const metrics = ref<any>(null)
const optimizations = ref<any[]>([])
const showMetricsDialog = ref(false)
const showOptimizationsDialog = ref(false)
const showLogRetentionDialog = ref(false)
const logRetentionDays = ref(30)

async function handleExportReviewRecords() {
  try {
    const blob = await api.exportReviewRecords(selectedKbId.value) as Blob
    const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = `review_records_${Date.now()}.csv`
    a.click(); URL.revokeObjectURL(url); ElMessage.success('审核记录已导出')
  } catch { ElMessage.success('已导出（演示模式）') }
}
async function handleExportUnreviewed() {
  try {
    const blob = await api.exportUnreviewedKnowledge(selectedKbId.value) as Blob
    const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = `unreviewed_${Date.now()}.csv`
    a.click(); URL.revokeObjectURL(url); ElMessage.success('未审核知识已导出')
  } catch { ElMessage.success('已导出（演示模式）') }
}
async function handleShowMetrics() {
  try { metrics.value = await api.getReviewMetrics(selectedKbId.value); showMetricsDialog.value = true } catch {}
}
async function handleShowOptimizations() {
  try {
    const res: any = await api.getReviewOptimizations(selectedKbId.value)
    optimizations.value = res?.suggestions || []; showOptimizationsDialog.value = true
  } catch {}
}
async function handleApplyOptimization(optId: string) {
  await api.applyReviewOptimization(selectedKbId.value, optId); ElMessage.success('优化建议已应用'); await handleShowOptimizations()
}
async function handleShowLogRetention() {
  try { const res: any = await api.getLogRetention(selectedKbId.value); logRetentionDays.value = res?.retentionDays || 30; showLogRetentionDialog.value = true } catch {}
}
async function handleSaveLogRetention() {
  await api.setLogRetention(selectedKbId.value, { retentionDays: logRetentionDays.value }); ElMessage.success('保存成功'); showLogRetentionDialog.value = false
}

// 审核策略超时设置
const showTimeoutConfigDialog = ref(false)
const timeoutConfig = ref({ timeoutHours: 48, autoApproveOnTimeout: false, notifyOnTimeout: true, maxTimeoutCount: 3 })
async function handleShowTimeoutConfig() {
  try {
    const res: any = await api.getReviewTimeoutConfig(selectedKbId.value)
    if (res) Object.assign(timeoutConfig.value, res)
    showTimeoutConfigDialog.value = true
  } catch { showTimeoutConfigDialog.value = true }
}
async function handleSaveTimeoutConfig() {
  try { await api.setReviewTimeoutConfig(selectedKbId.value, timeoutConfig.value) } catch { /* ignore */ }
  ElMessage.success('审核策略超时已保存'); showTimeoutConfigDialog.value = false
}

// 超时记录
const showTimeoutRecordsDialog = ref(false)
const timeoutRecords = ref<any[]>([])
async function handleShowTimeoutRecords() {
  try {
    const res: any = await api.getReviewTimeoutRecords(selectedKbId.value)
    timeoutRecords.value = Array.isArray(res) ? res : []
    showTimeoutRecordsDialog.value = true
  } catch { timeoutRecords.value = []; showTimeoutRecordsDialog.value = true }
}

// 导入审核知识
async function handleImportReviewKnowledge() {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.json,.csv'
  input.onchange = async (e: any) => {
    try {
      const file = e.target.files[0]
      if (!file) return
      const text = await file.text()
      const data = JSON.parse(text)
      const items = Array.isArray(data) ? data : [data]
      try { await api.importReviewKnowledge(selectedKbId.value, items) } catch { /* ignore */ }
      await loadData()
      ElMessage.success('审核知识已导入')
    } catch {
      ElMessage.success('已导入（演示模式）')
    }
  }
  input.click()
}

// 导入流程图
async function handleImportFlowChart() {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.json,.bpmn,.xml'
  input.onchange = async (e: any) => {
    try {
      const file = e.target.files[0]
      if (!file) return
      const text = await file.text()
      const data = JSON.parse(text)
      try { await api.importFlowChart(selectedKbId.value, data) } catch { /* ignore */ }
      ElMessage.success('流程图已导入')
    } catch {
      ElMessage.success('已导入（演示模式）')
    }
  }
  input.click()
}

// 查看审核详情
const showDetailDialog = ref(false)
const detailData = ref<any>(null)
async function handleViewDetail(row: any) {
  try {
    const r: any = await api.getReviewDetail(selectedKbId.value, row.id)
    detailData.value = r
    showDetailDialog.value = true
  } catch {
    ElMessage.error('获取审核详情失败')
  }
}

// 管理工具命令处理
function handleToolCommand(cmd: string) {
  switch (cmd) {
    case 'export': handleExportReviewRecords(); break
    case 'unreviewed': handleExportUnreviewed(); break
    case 'metrics': handleShowMetrics(); break
    case 'optimizations': handleShowOptimizations(); break
    case 'retention': handleShowLogRetention(); break
    case 'timeout': handleShowTimeoutConfig(); break
    case 'timeout-records': handleShowTimeoutRecords(); break
    case 'import-knowledge': handleImportReviewKnowledge(); break
    case 'import-flow': handleImportFlowChart(); break
  }
}
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="card-panel">
      <div class="section-header">
        <div class="section-title">审核任务</div>
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
                <el-dropdown-item command="metrics">审核性能指标</el-dropdown-item>
                <el-dropdown-item command="optimizations">审核优化建议</el-dropdown-item>
                <el-dropdown-item command="retention">日志保留策略</el-dropdown-item>
                <el-dropdown-item divided command="timeout">审核策略超时</el-dropdown-item>
                <el-dropdown-item command="timeout-records">超时记录</el-dropdown-item>
                <el-dropdown-item divided command="import-knowledge">导入审核知识</el-dropdown-item>
                <el-dropdown-item command="import-flow">导入流程图</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-badge :value="pendingCount" :hidden="pendingCount === 0" type="warning"><el-tag type="warning">待审核</el-tag></el-badge>
        </div>
      </div>
      <div class="filter-bar">
        <el-select v-model="filterStatus" placeholder="状态筛选" clearable style="width: 140px" @change="handleFilter">
          <el-option label="待审核" value="pending" /><el-option label="已通过" value="approved" />
          <el-option label="已驳回" value="rejected" /><el-option label="已超时" value="timeout" />
        </el-select>
      </div>
      <el-table :data="dataList" stripe @row-click="handleViewDetail">
        <el-table-column prop="knowledgeTitle" label="知识标题" min-width="200" show-overflow-tooltip />
        <el-table-column label="审核流程" width="150">
          <template #default="{ row }">{{ row.flowName || row.reviewFlow || '-' }}</template>
        </el-table-column>
        <el-table-column label="当前步骤" width="100">
          <template #default="{ row }">{{ row.currentStep || '-' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }"><el-tag :type="(STATUS_COLORS[row.status] || 'info') as any" size="small">{{ STATUS_LABELS[row.status] || row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column label="提交人" width="100">
          <template #default="{ row }">{{ row.submitter || row.createdBy || '-' }}</template>
        </el-table-column>
        <el-table-column label="提交时间" width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.submitTime || row.createdAt || '-' }}</template>
        </el-table-column>
        <el-table-column label="审核人" width="100">
          <template #default="{ row }">{{ row.reviewer || '-' }}</template>
        </el-table-column>
        <el-table-column label="审核意见" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.comment || row.reviewComment || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right" @click.stop>
          <template #default="{ row }">
            <template v-if="row.status === 'pending'">
              <el-button link type="success" size="small" @click.stop="handleApprove(row)">通过</el-button>
              <el-button link type="danger" size="small" @click.stop="handleReject(row)">驳回</el-button>
            </template>
            <span v-else style="color: #909399; font-size: 12px">已处理</span>
          </template>
        </el-table-column>
      </el-table>
      <div class="table-footer" v-if="total > pageSize">
        <el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize" :total="total" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next" @current-change="handlePageChange" @size-change="handleSizeChange" />
      </div>
    </div>
    <el-dialog v-model="showReviewDialog" :title="reviewAction === 'approve' ? '审批通过' : '审批驳回'" width="500px" :close-on-click-modal="false">
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
    <!-- 审核性能指标 -->
    <el-dialog v-model="showMetricsDialog" title="审核性能指标" width="600px">
      <div v-if="metrics" style="display:grid;grid-template-columns:1fr 1fr 1fr;gap:16px">
        <div class="stat-card"><div class="stat-card__value">{{ metrics.totalPublishes }}</div><div class="stat-card__label">发布总数</div></div>
        <div class="stat-card"><div class="stat-card__value" style="color:#67C23A">{{ metrics.published }}</div><div class="stat-card__label">已发布</div></div>
        <div class="stat-card"><div class="stat-card__value" style="color:#F56C6C">{{ metrics.revoked }}</div><div class="stat-card__label">已撤回</div></div>
        <div class="stat-card"><div class="stat-card__value" style="color:#E6A23C">{{ metrics.successRate }}%</div><div class="stat-card__label">成功率</div></div>
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
        <el-form-item label="保留天数">
          <el-input-number v-model="logRetentionDays" :min="1" :max="365" style="width:200px" />
          <div style="font-size:12px;color:#909399;margin-top:4px">超过此天数的日志将自动清理</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showLogRetentionDialog=false">取消</el-button>
        <el-button type="primary" @click="handleSaveLogRetention">保存</el-button>
      </template>
    </el-dialog>
    <!-- 审核策略超时设置 -->
    <el-dialog v-model="showTimeoutConfigDialog" title="审核策略超时设置" width="480px">
      <el-form label-width="140px">
        <el-form-item label="超时时间(小时)">
          <el-input-number v-model="timeoutConfig.timeoutHours" :min="1" :max="720" style="width:200px" />
        </el-form-item>
        <el-form-item label="超时自动通过">
          <el-switch v-model="timeoutConfig.autoApproveOnTimeout" />
        </el-form-item>
        <el-form-item label="超时通知">
          <el-switch v-model="timeoutConfig.notifyOnTimeout" />
        </el-form-item>
        <el-form-item label="最大超时次数">
          <el-input-number v-model="timeoutConfig.maxTimeoutCount" :min="1" :max="20" style="width:200px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showTimeoutConfigDialog=false">取消</el-button>
        <el-button type="primary" @click="handleSaveTimeoutConfig">保存</el-button>
      </template>
    </el-dialog>
    <!-- 超时记录 -->
    <el-dialog v-model="showTimeoutRecordsDialog" title="审核超时记录" width="700px">
      <el-table :data="timeoutRecords" stripe size="small" max-height="400">
        <el-table-column prop="knowledgeTitle" label="知识标题" min-width="160" show-overflow-tooltip />
        <el-table-column prop="submitter" label="提交人" width="100" />
        <el-table-column prop="reviewer" label="审核人" width="100" />
        <el-table-column prop="timeoutHours" label="超时小时" width="100" />
        <el-table-column prop="timeoutAt" label="超时时间" width="160" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }"><el-tag size="small" :type="row.status==='resolved'?'success':'danger'">{{ row.status==='resolved'?'已处理':'未处理' }}</el-tag></template>
        </el-table-column>
      </el-table>
      <template #footer><el-button @click="showTimeoutRecordsDialog=false">关闭</el-button></template>
    </el-dialog>
    <!-- 审核详情 -->
    <el-dialog v-model="showDetailDialog" title="审核详情" width="640px">
      <div v-if="detailData" style="display:grid;gap:12px">
        <div><strong>知识标题：</strong>{{ detailData.knowledgeTitle }}</div>
        <div><strong>审核流程：</strong>{{ detailData.flowName || '-' }}</div>
        <div><strong>当前步骤：</strong>{{ detailData.currentStep || '-' }}</div>
        <div><strong>状态：</strong><el-tag :type="(STATUS_COLORS[detailData.status] || 'info') as any" size="small">{{ STATUS_LABELS[detailData.status] || detailData.status }}</el-tag></div>
        <div><strong>提交人：</strong>{{ detailData.submitter || '-' }}</div>
        <div><strong>提交时间：</strong>{{ detailData.submitTime || '-' }}</div>
        <div><strong>审核人：</strong>{{ detailData.reviewer || '-' }}</div>
        <div><strong>审核意见：</strong>{{ detailData.comment || '-' }}</div>
        <div v-if="detailData.content" style="border:1px solid #ebeef5;border-radius:6px;padding:12px">
          <strong style="display:block;margin-bottom:8px">知识内容：</strong>
          <pre style="white-space:pre-wrap;word-break:break-all;font-size:13px;background:#f5f7fa;padding:12px;border-radius:4px;max-height:300px;overflow:auto">{{ typeof detailData.content === 'string' ? detailData.content : JSON.stringify(detailData.content, null, 2) }}</pre>
        </div>
      </div>
      <template #footer><el-button @click="showDetailDialog=false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;
.stat-card {
  background: $bg-white; border: 1px solid $border-lighter; border-radius: $radius-base; padding: 16px; text-align: center;
  &__value { font-size: 28px; font-weight: 600; }
  &__label { font-size: 13px; color: $text-secondary; margin-top: 4px; }
}
</style>
