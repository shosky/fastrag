<script setup lang="ts">
import type { ReviewTask } from '@/types/audit'
import { REVIEW_STATUS_LABELS, REVIEW_STATUS_COLORS } from '@/types/audit'
import { Select, CloseBold } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const activeTab = ref('pending')
const reviewTasks = ref<ReviewTask[]>([])
const showCommentDialog = ref(false)
const commentAction = ref<'approve' | 'reject'>('approve')
const commentTarget = ref('')
const commentText = ref('')

async function refresh() {
  reviewTasks.value = (await api.getReviews()) as any || []
}

onMounted(refresh)

const filteredTasks = computed(() => {
  if (activeTab.value === 'pending') return reviewTasks.value.filter((r) => r.status === 'pending')
  if (activeTab.value === 'approved') return reviewTasks.value.filter((r) => r.status === 'approved')
  if (activeTab.value === 'rejected') return reviewTasks.value.filter((r) => r.status === 'rejected')
  return reviewTasks.value
})

function handleApprove(task: ReviewTask) {
  commentAction.value = 'approve'
  commentTarget.value = task.id
  commentText.value = ''
  showCommentDialog.value = true
}

function handleReject(task: ReviewTask) {
  commentAction.value = 'reject'
  commentTarget.value = task.id
  commentText.value = ''
  showCommentDialog.value = true
}

async function handleCommentSubmit() {
  if (commentAction.value === 'approve') {
    await api.approveReview(commentTarget.value)
    ElMessage.success('审核通过')
  } else {
    await api.rejectReview(commentTarget.value, commentText.value)
    ElMessage.success('已驳回')
  }
  showCommentDialog.value = false
  await refresh()
}
</script>

<template>
  <div class="page-container">
    <div class="section-header">
      <h3>审核中心</h3>
    </div>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="待审核" name="pending" />
      <el-tab-pane label="已通过" name="approved" />
      <el-tab-pane label="已驳回" name="rejected" />
      <el-tab-pane label="全部" name="all" />
    </el-tabs>

    <el-table :data="filteredTasks" stripe>
      <el-table-column prop="kbName" label="知识库" min-width="120" />
      <el-table-column label="版本" width="80" align="center">
        <template #default="{ row }">v{{ row.version }}</template>
      </el-table-column>
      <el-table-column prop="applicant" label="申请人" width="100" />
      <el-table-column prop="reviewer" label="审核人" width="100" />
      <el-table-column prop="status" label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="REVIEW_STATUS_COLORS[row.status as keyof typeof REVIEW_STATUS_COLORS] || 'info'" size="small">
            {{ REVIEW_STATUS_LABELS[row.status as keyof typeof REVIEW_STATUS_LABELS] || row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="comment" label="审核备注" min-width="150" show-overflow-tooltip />
      <el-table-column prop="createdAt" label="提交时间" width="170" />
      <el-table-column prop="reviewedAt" label="审核时间" width="170" />
      <el-table-column label="操作" width="160" align="center">
        <template #default="{ row }">
          <template v-if="row.status === 'pending'">
            <el-button v-permission="'review:approve'" type="success" link size="small" @click="handleApprove(row as ReviewTask)">
              <el-icon><Select /></el-icon> 通过
            </el-button>
            <el-button v-permission="'review:reject'" type="danger" link size="small" @click="handleReject(row as ReviewTask)">
              <el-icon><CloseBold /></el-icon> 驳回
            </el-button>
          </template>
          <span v-else class="review-processed">已处理</span>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="filteredTasks.length === 0" description="暂无审核记录" />

    <!-- 审核备注弹窗 -->
    <el-dialog v-model="showCommentDialog" :title="commentAction === 'approve' ? '审核通过' : '审核驳回'" width="420px">
      <el-input
        v-model="commentText"
        type="textarea"
        :rows="3"
        :placeholder="commentAction === 'approve' ? '审核意见（可选）' : '请填写驳回原因'"
      />
      <template #footer>
        <el-button @click="showCommentDialog = false">取消</el-button>
        <el-button :type="commentAction === 'approve' ? 'success' : 'danger'" @click="handleCommentSubmit">
          {{ commentAction === 'approve' ? '确认通过' : '确认驳回' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.review-processed {
  font-size: 12px;
  color: $text-secondary;
}
</style>
