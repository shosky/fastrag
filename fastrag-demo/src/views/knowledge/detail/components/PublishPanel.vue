<script setup lang="ts">
import type { KnowledgeVersion, ReviewTask } from '@/types/audit'
import { PUBLISH_STATUS_LABELS, PUBLISH_STATUS_COLORS } from '@/types/audit'
import { CircleCheck, Select, CloseBold, RefreshRight, InfoFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getFiles } from '@/mock/files'
import { getKnowledgeBase } from '@/mock/knowledge-bases'
import { getUpdateLogs } from '@/mock/knowledge-update'
import type { UpdateLog } from '@/mock/knowledge-update'
import {
  getVersionsByKb,
  createVersion,
  submitForReview,
  transitionStatus,
  getPublishedVersion,
  getReviewTasks,
  approveReview,
  rejectReview,
} from '@/mock/audit'
import { useAuth } from '@/composables/useAuth'

const props = defineProps<{
  kbId: string
  kbName?: string
}>()

const { hasPermission } = useAuth()
const versions = ref<KnowledgeVersion[]>([])
const publishedVersion = ref<KnowledgeVersion | null>(null)
const reviewTasks = ref<ReviewTask[]>([])

function refresh() {
  versions.value = getVersionsByKb(props.kbId)
  publishedVersion.value = getPublishedVersion(props.kbId)
  reviewTasks.value = getReviewTasks(props.kbId)
}

onMounted(refresh)

// ===== 创建发布弹窗 =====
const showCreateDialog = ref(false)
const createForm = ref({ description: '', tags: '' })
const changeSummary = ref({ fileAdded: 0, fileUpdated: 0, fileRemoved: 0, configChanged: 0 })
const changeLogs = ref<UpdateLog[]>([])

function openCreateDialog() {
  // 计算自上次发布以来的变更
  const logs = getUpdateLogs(props.kbId)
  changeLogs.value = logs // 显示全部日志供预览
  changeSummary.value = {
    fileAdded: logs.filter((l) => l.updateType === 'file_added').length,
    fileUpdated: logs.filter((l) => l.updateType === 'file_updated').length,
    fileRemoved: logs.filter((l) => l.updateType === 'file_removed').length,
    configChanged: logs.filter((l) => l.updateType === 'config_changed').length,
  }
  createForm.value = { description: '', tags: '' }
  showCreateDialog.value = true
}

function handleConfirmCreate() {
  const files = getFiles(props.kbId)
  const completedFiles = files.filter((f) => f.status === 'completed')
  const totalChunks = completedFiles.reduce((sum, f) => sum + (f.chunkCount || 0), 0)
  const kb = getKnowledgeBase(props.kbId)

  const version = createVersion(props.kbId, {
    name: props.kbName || kb?.name || '知识库',
    description: createForm.value.description || '手动创建',
    tags: createForm.value.tags ? createForm.value.tags.split(/[,，]/).map((s) => s.trim()) : kb?.tags || [],
    fileCount: completedFiles.length,
    chunkCount: totalChunks,
  })
  showCreateDialog.value = false
  ElMessage.success(`已创建版本 v${version.version}（${completedFiles.length} 文件，${totalChunks} 切片）`)
  refresh()
}

// ===== 审核备注弹窗 =====
const showCommentDialog = ref(false)
const commentAction = ref<'approve' | 'reject'>('approve')
const commentTarget = ref('')
const commentText = ref('')

// ===== 操作函数 =====
async function handleSubmitReview(version: KnowledgeVersion) {
  try {
    await ElMessageBox.confirm(`将版本 v${version.version} 提交审核？`, '提交审核', {
      confirmButtonText: '提交', cancelButtonText: '取消', type: 'info',
    })
    submitForReview(props.kbId, props.kbName || '知识库', version.id, version.version, '当前用户')
    transitionStatus(props.kbId, version.id, 'pending_review', '当前用户')
    ElMessage.success('已提交审核')
    refresh()
  } catch {}
}

function handleInlineApprove(version: KnowledgeVersion) {
  commentAction.value = 'approve'
  commentTarget.value = version.id
  commentText.value = ''
  showCommentDialog.value = true
}

function handleInlineReject(version: KnowledgeVersion) {
  commentAction.value = 'reject'
  commentTarget.value = version.id
  commentText.value = ''
  showCommentDialog.value = true
}

function handleCommentSubmit() {
  const task = reviewTasks.value.find((t) => t.versionId === commentTarget.value && t.status === 'pending')
  if (task) {
    if (commentAction.value === 'approve') {
      approveReview(task.id, commentText.value)
    } else {
      rejectReview(task.id, commentText.value)
    }
  } else {
    const targetStatus = commentAction.value === 'approve' ? 'approved' : 'draft'
    transitionStatus(props.kbId, commentTarget.value, targetStatus, '当前用户')
  }
  ElMessage.success(commentAction.value === 'approve' ? '审核通过' : '已驳回')
  showCommentDialog.value = false
  refresh()
}

async function handlePublish(version: KnowledgeVersion) {
  const oldPublished = publishedVersion.value
  if (oldPublished && oldPublished.id !== version.id) {
    transitionStatus(props.kbId, oldPublished.id, 'draft', '当前用户')
  }
  transitionStatus(props.kbId, version.id, 'published', '当前用户')
  ElMessage.success(`版本 v${version.version} 已发布`)
  refresh()
}

async function handleRevertToDraft(version: KnowledgeVersion) {
  try {
    await ElMessageBox.confirm(`将 v${version.version} 回退到草稿？回退后线上将无已发布版本。`, '回退确认', {
      confirmButtonText: '回退', cancelButtonText: '取消', type: 'warning',
    })
    transitionStatus(props.kbId, version.id, 'draft', '当前用户')
    ElMessage.success('已回退到草稿')
    refresh()
  } catch {}
}

function getReviewTask(versionId: string): ReviewTask | undefined {
  return reviewTasks.value.find((t) => t.versionId === versionId)
}

const canReview = computed(() => hasPermission('review:approve'))

// 变更类型标签
const changeTypeLabels: Record<string, string> = {
  file_added: '新增文件',
  file_removed: '删除文件',
  file_updated: '更新文件',
  chunk_added: '新增切片',
  chunk_removed: '删除切片',
  chunk_updated: '更新切片',
  config_changed: '配置变更',
}

const changeTypeColors: Record<string, 'success' | 'danger' | 'primary' | 'warning'> = {
  file_added: 'success',
  file_removed: 'danger',
  file_updated: 'primary',
  chunk_added: 'success',
  chunk_removed: 'danger',
  chunk_updated: 'primary',
  config_changed: 'warning',
}
</script>

<template>
  <div class="publish-panel">
    <!-- 顶部信息条 -->
    <div class="publish-panel__bar">
      <div class="publish-panel__bar-left">
        <span class="publish-panel__bar-label">当前线上版本：</span>
        <template v-if="publishedVersion">
          <el-tag type="success" size="small">v{{ publishedVersion.version }}</el-tag>
          <span>{{ publishedVersion.name }}</span>
          <span class="publish-panel__bar-meta">
            {{ publishedVersion.createdAt }} · 文件 {{ publishedVersion.fileCount }} · 切片 {{ publishedVersion.chunkCount }}
          </span>
        </template>
        <span v-else class="publish-panel__bar-empty">尚未发布</span>
      </div>
      <el-button size="small" type="primary" @click="openCreateDialog">创建发布</el-button>
    </div>

    <!-- 版本表格 -->
    <el-table :data="versions" stripe>
      <el-table-column label="版本" width="80" align="center">
        <template #default="{ row }">
          <span class="publish-panel__ver">v{{ (row as KnowledgeVersion).version }}</span>
        </template>
      </el-table-column>

      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="(PUBLISH_STATUS_COLORS as any)[(row as KnowledgeVersion).publishStatus]" size="small">
            {{ (PUBLISH_STATUS_LABELS as any)[(row as KnowledgeVersion).publishStatus] }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column label="文件/切片" width="100" align="center">
        <template #default="{ row }">
          {{ (row as KnowledgeVersion).fileCount }} / {{ (row as KnowledgeVersion).chunkCount }}
        </template>
      </el-table-column>

      <el-table-column label="说明" min-width="140">
        <template #default="{ row }">
          {{ (row as KnowledgeVersion).description || '-' }}
        </template>
      </el-table-column>

      <el-table-column label="创建人" width="90">
        <template #default="{ row }">{{ (row as KnowledgeVersion).createdBy }}</template>
      </el-table-column>

      <el-table-column label="创建时间" width="160">
        <template #default="{ row }">{{ (row as KnowledgeVersion).createdAt }}</template>
      </el-table-column>

      <el-table-column label="状态说明" min-width="180">
        <template #default="{ row }">
          <span class="publish-panel__hint">
            <template v-if="(row as KnowledgeVersion).publishStatus === 'draft'">可提交审核</template>
            <template v-else-if="(row as KnowledgeVersion).publishStatus === 'pending_review'">
              等待 {{ getReviewTask((row as KnowledgeVersion).id)?.reviewer || '管理员' }} 审核
            </template>
            <template v-else-if="(row as KnowledgeVersion).publishStatus === 'approved'">审核通过，可发布</template>
            <template v-else-if="(row as KnowledgeVersion).publishStatus === 'published'">线上版本，用户可见</template>
            <template v-else-if="(row as KnowledgeVersion).publishStatus === 'rejected'">
              已驳回{{ getReviewTask((row as KnowledgeVersion).id)?.comment ? '：' + getReviewTask((row as KnowledgeVersion).id)!.comment : '' }}
            </template>
          </span>
        </template>
      </el-table-column>

      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button v-if="(row as KnowledgeVersion).publishStatus === 'draft'" type="primary" link size="small" @click="handleSubmitReview(row as KnowledgeVersion)">
            提交审核
          </el-button>
          <template v-if="(row as KnowledgeVersion).publishStatus === 'pending_review' && canReview">
            <el-button type="success" link size="small" @click="handleInlineApprove(row as KnowledgeVersion)">
              <el-icon><Select /></el-icon> 通过
            </el-button>
            <el-button type="danger" link size="small" @click="handleInlineReject(row as KnowledgeVersion)">
              <el-icon><CloseBold /></el-icon> 驳回
            </el-button>
          </template>
          <el-button v-if="(row as KnowledgeVersion).publishStatus === 'approved'" type="success" link size="small" @click="handlePublish(row as KnowledgeVersion)">
            <el-icon><CircleCheck /></el-icon> 发布
          </el-button>
          <el-button v-if="(row as KnowledgeVersion).publishStatus === 'published'" type="warning" link size="small" @click="handleRevertToDraft(row as KnowledgeVersion)">
            <el-icon><RefreshRight /></el-icon> 回退
          </el-button>
          <el-button v-if="(row as KnowledgeVersion).publishStatus === 'rejected'" type="warning" link size="small" @click="handleSubmitReview(row as KnowledgeVersion)">
            <el-icon><RefreshRight /></el-icon> 重新提交
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="versions.length === 0" description="暂无版本，点击上方「创建发布」开始" :image-size="60" />

    <!-- ===== 创建发布弹窗 ===== -->
    <el-dialog v-model="showCreateDialog" title="创建发布" width="560px" :close-on-click-modal="false">
      <!-- 变更摘要 -->
      <div class="publish-dialog__summary">
        <div class="publish-dialog__summary-title">自上次发布以来的变更</div>
        <div class="publish-dialog__summary-stats">
          <el-tag v-if="changeSummary.fileAdded > 0" type="success" size="small">+{{ changeSummary.fileAdded }} 新增文件</el-tag>
          <el-tag v-if="changeSummary.fileUpdated > 0" type="primary" size="small">{{ changeSummary.fileUpdated }} 更新文件</el-tag>
          <el-tag v-if="changeSummary.fileRemoved > 0" type="danger" size="small">-{{ changeSummary.fileRemoved }} 删除文件</el-tag>
          <el-tag v-if="changeSummary.configChanged > 0" type="warning" size="small">{{ changeSummary.configChanged }} 配置变更</el-tag>
          <span v-if="changeSummary.fileAdded + changeSummary.fileUpdated + changeSummary.fileRemoved + changeSummary.configChanged === 0" class="publish-dialog__no-change">
            无变更
          </span>
        </div>
      </div>

      <!-- 变更明细 -->
      <div v-if="changeLogs.length > 0" class="publish-dialog__logs">
        <div class="publish-dialog__logs-title">变更明细</div>
        <div class="publish-dialog__logs-list">
          <div v-for="log in changeLogs.slice(0, 10)" :key="log.id" class="publish-dialog__log-item">
            <el-tag :type="changeTypeColors[log.updateType] || 'info'" size="small" style="flex-shrink: 0">
              {{ changeTypeLabels[log.updateType] || log.updateType }}
            </el-tag>
            <span class="publish-dialog__log-target">{{ log.target }}</span>
            <span class="publish-dialog__log-detail">{{ log.detail }}</span>
            <span class="publish-dialog__log-time">{{ log.timestamp }}</span>
          </div>
          <div v-if="changeLogs.length > 10" class="publish-dialog__log-more">
            还有 {{ changeLogs.length - 10 }} 条变更记录...
          </div>
        </div>
      </div>

      <!-- 发布信息 -->
      <el-form label-position="top" style="margin-top: 16px">
        <el-form-item label="版本说明">
          <el-input v-model="createForm.description" type="textarea" :rows="2" placeholder="描述本次发布的主要变更内容" />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="createForm.tags" placeholder="多个标签用逗号分隔" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="handleConfirmCreate">确认创建</el-button>
      </template>
    </el-dialog>

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

.publish-panel {
  display: flex;
  flex-direction: column;
  gap: $spacing-base;
}

// --- 顶部信息条 ---
.publish-panel__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $spacing-sm $spacing-base;
  background: $bg-white;
  border-radius: $radius-base;
  border: 1px solid $border-lighter;
}

.publish-panel__bar-left {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
}

.publish-panel__bar-label {
  font-size: 13px;
  color: $text-secondary;
}

.publish-panel__bar-meta {
  font-size: 12px;
  color: $text-secondary;
}

.publish-panel__bar-empty {
  font-size: 13px;
  color: $text-placeholder;
}

// --- 表格内 ---
.publish-panel__ver {
  font-weight: 600;
  color: $text-primary;
}

.publish-panel__hint {
  font-size: 12px;
  color: $text-secondary;
}

// --- 创建发布弹窗 ---
.publish-dialog__summary {
  padding: $spacing-base;
  background: $bg-hover;
  border-radius: $radius-base;
  margin-bottom: $spacing-base;
}

.publish-dialog__summary-title {
  font-size: 13px;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: $spacing-sm;
}

.publish-dialog__summary-stats {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-xs;
}

.publish-dialog__no-change {
  font-size: 12px;
  color: $text-secondary;
}

.publish-dialog__logs {
  margin-bottom: $spacing-sm;
}

.publish-dialog__logs-title {
  font-size: 13px;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: $spacing-xs;
}

.publish-dialog__logs-list {
  max-height: 200px;
  overflow-y: auto;
  border: 1px solid $border-lighter;
  border-radius: $radius-sm;
}

.publish-dialog__log-item {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  padding: 5px $spacing-sm;
  font-size: 12px;
  border-bottom: 1px solid $border-extra-light;

  &:last-child { border-bottom: none; }
}

.publish-dialog__log-target {
  font-weight: 500;
  color: $text-primary;
  flex-shrink: 0;
}

.publish-dialog__log-detail {
  color: $text-secondary;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.publish-dialog__log-time {
  color: $text-placeholder;
  flex-shrink: 0;
  font-size: 11px;
}

.publish-dialog__log-more {
  text-align: center;
  padding: $spacing-xs;
  font-size: 11px;
  color: $text-secondary;
}
</style>
