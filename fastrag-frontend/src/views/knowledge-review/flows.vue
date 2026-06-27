<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { getReviewTaskList, approveReviewTask, rejectReviewTask, REVIEW_STATUS_LABELS, REVIEW_STATUS_COLORS } from '@/mock/knowledge-review'

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

async function loadData() {
  loading.value = true
  try {
    const res = getReviewTaskList({ page: currentPage.value, pageSize: pageSize.value, status: filterStatus.value || undefined })
    dataList.value = res.list; total.value = res.total
  } finally { loading.value = false }
}
onMounted(loadData)

function handleFilter() { currentPage.value = 1; loadData() }
function handlePageChange(p: number) { currentPage.value = p; loadData() }
function handleSizeChange(s: number) { pageSize.value = s; currentPage.value = 1; loadData() }

function handleApprove(row: any) {
  reviewingId.value = row.id; reviewAction.value = 'approve'; reviewComment.value = ''; showReviewDialog.value = true
}
function handleReject(row: any) {
  reviewingId.value = row.id; reviewAction.value = 'reject'; reviewComment.value = ''; showReviewDialog.value = true
}
function handleSubmitReview() {
  if (reviewAction.value === 'reject' && !reviewComment.value) { ElMessage.warning('驳回时请填写原因'); return }
  if (reviewAction.value === 'approve') { approveReviewTask(reviewingId.value); ElMessage.success('已通过') }
  else { rejectReviewTask(reviewingId.value, reviewComment.value); ElMessage.success('已驳回') }
  showReviewDialog.value = false; loadData()
}
const pendingCount = computed(() => dataList.value.filter(d => d.status === 'pending').length)
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="card-panel">
      <div class="section-header">
        <div class="section-title">审核任务</div>
        <el-badge :value="pendingCount" :hidden="pendingCount === 0" type="warning"><el-tag type="warning">待审核</el-tag></el-badge>
      </div>
      <div class="filter-bar">
        <el-select v-model="filterStatus" placeholder="状态筛选" clearable style="width: 140px" @change="handleFilter">
          <el-option label="待审核" value="pending" /><el-option label="已通过" value="approved" />
          <el-option label="已驳回" value="rejected" /><el-option label="已超时" value="timeout" />
        </el-select>
      </div>
      <el-table :data="dataList" stripe>
        <el-table-column prop="knowledgeTitle" label="知识标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="flowName" label="审核流程" width="150" />
        <el-table-column prop="currentStep" label="当前步骤" width="100" />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }"><el-tag :type="REVIEW_STATUS_COLORS[row.status] as any" size="small">{{ REVIEW_STATUS_LABELS[row.status] || row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="submitter" label="提交人" width="100" />
        <el-table-column prop="submitTime" label="提交时间" width="160" show-overflow-tooltip />
        <el-table-column prop="reviewer" label="审核人" width="100" />
        <el-table-column prop="comment" label="审核意见" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'pending'">
              <el-button link type="success" size="small" @click="handleApprove(row)">通过</el-button>
              <el-button link type="danger" size="small" @click="handleReject(row)">驳回</el-button>
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
  </div>
</template>
