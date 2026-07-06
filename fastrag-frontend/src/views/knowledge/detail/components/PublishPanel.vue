<script setup lang="ts">
import { PUBLISH_STATUS_LABELS, PUBLISH_STATUS_COLORS } from '@/types/audit'
import { CircleCheck, Select, CloseBold, RefreshRight, Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'
import { useAuth } from '@/composables/useAuth'

const props = defineProps<{
  kbId: string
  kbName?: string
}>()

const { hasPermission } = useAuth()
const versions = ref<any[]>([])
const publishedVersion = ref<any>(null)
const reviewTasks = ref<any[]>([])
const loading = ref(false)

async function refresh() {
  loading.value = true
  try {
    // 从后端加载版本数据
    const [allVersions, pubVer] = await Promise.all([
      api.getVersions(props.kbId).catch(() => []),
      api.getPublishedVersion(props.kbId).catch(() => null),
    ])
    const list = Array.isArray(allVersions) ? allVersions : (allVersions as any)?.list || []
    versions.value = list
    publishedVersion.value = pubVer
  } catch {
    versions.value = []
    publishedVersion.value = null
  } finally {
    loading.value = false
  }
}

onMounted(refresh)

// ===== 创建发布弹窗 =====
const showCreateDialog = ref(false)
const createForm = ref({ description: '', tags: '', publishNow: false })
const changeSummary = ref({ fileAdded: 0, fileUpdated: 0, fileRemoved: 0, configChanged: 0 })
const changeLogs = ref<any[]>([])

async function openCreateDialog() {
  // 计算自上次发布以来的变更
  try {
    const logRes = await api.getKbUpdateLogs(props.kbId)
    const logs = (logRes as any)?.list || (logRes as any) || []
    changeLogs.value = logs
    changeSummary.value = {
      fileAdded: logs.filter((l: any) => l.updateType === 'file_added').length,
      fileUpdated: logs.filter((l: any) => l.updateType === 'file_updated').length,
      fileRemoved: logs.filter((l: any) => l.updateType === 'file_removed').length,
      configChanged: logs.filter((l: any) => l.updateType === 'config_changed').length,
    }
  } catch {
    changeLogs.value = []
  }
  createForm.value = { description: '', tags: '', publishNow: false }
  showCreateDialog.value = true
}

async function handleConfirmCreate() {
  try {
    const version: any = await api.createVersion(props.kbId, {
      name: props.kbName || '知识库',
      description: createForm.value.description || '手动创建',
      tags: createForm.value.tags ? createForm.value.tags.split(/[,，]/).map((s) => s.trim()) : [],
    })
    showCreateDialog.value = false
    // 如果勾选了"立即发布"，自动走 提交审核 → 发布 流程
    if (createForm.value.publishNow) {
      try {
        await api.submitForReview({ kbId: props.kbId, versionId: version.id || version.data?.id })
        await api.transitionVersion(props.kbId, version.id || version.data?.id, 'submit')
        await api.transitionVersion(props.kbId, version.id || version.data?.id, 'approve')
        const oldPub = publishedVersion.value
        if (oldPub) await api.transitionVersion(props.kbId, oldPub.id, 'draft')
        await api.transitionVersion(props.kbId, version.id || version.data?.id, 'publish')
        ElMessage.success('版本已创建并发布上线')
      } catch {
        ElMessage.success('版本已创建但自动发布失败，请手动发布')
      }
    } else {
      ElMessage.success('版本已创建')
    }
    await refresh()
  } catch (e: any) {
    ElMessage.error(e.message || '创建版本失败')
  }
}

// ===== 审核备注弹窗 =====
const showCommentDialog = ref(false)
const commentAction = ref<'approve' | 'reject'>('approve')
const commentTarget = ref('')
const commentText = ref('')

// ===== 操作函数 =====
async function handleSubmitReview(version: any) {
  try {
    await ElMessageBox.confirm(`将版本 v${version.version} 提交审核？`, '提交审核', {
      confirmButtonText: '提交', cancelButtonText: '取消', type: 'info',
    })
    await api.submitForReview({ kbId: props.kbId, versionId: version.id })
    await api.transitionVersion(props.kbId, version.id, 'submit')
    ElMessage.success('已提交审核')
    await refresh()
  } catch {}
}

function handleInlineApprove(version: any) {
  commentAction.value = 'approve'
  commentTarget.value = version.id
  commentText.value = ''
  showCommentDialog.value = true
}

function handleInlineReject(version: any) {
  commentAction.value = 'reject'
  commentTarget.value = version.id
  commentText.value = ''
  showCommentDialog.value = true
}

async function handleCommentSubmit() {
  const task = reviewTasks.value.find((t: any) => t.versionId === commentTarget.value && t.status === 'pending')
  if (task) {
    if (commentAction.value === 'approve') {
      await api.approveReview(task.id)
    } else {
      await api.rejectReview(task.id, commentText.value)
    }
  } else {
    const action = commentAction.value === 'approve' ? 'approve' : 'reject'
    await api.transitionVersion(props.kbId, commentTarget.value, action)
  }
  ElMessage.success(commentAction.value === 'approve' ? '审核通过' : '已驳回')
  showCommentDialog.value = false
  await refresh()
}

async function handlePublish(version: any) {
  const oldPublished = publishedVersion.value
  if (oldPublished && oldPublished.id !== version.id) {
    await api.transitionVersion(props.kbId, oldPublished.id, 'draft')
  }
  await api.transitionVersion(props.kbId, version.id, 'publish')
  ElMessage.success(`版本 v${version.version} 已发布`)
  await refresh()
}

async function handleRevertToDraft(version: any) {
  try {
    await ElMessageBox.confirm(`将 v${version.version} 回退到草稿？回退后线上将无已发布版本。`, '回退确认', {
      confirmButtonText: '回退', cancelButtonText: '取消', type: 'warning',
    })
    await api.transitionVersion(props.kbId, version.id, 'draft')
    ElMessage.success('已回退到草稿')
    await refresh()
  } catch {}
}

function getReviewTask(versionId: string): any {
  return reviewTasks.value.find((t: any) => t.versionId === versionId)
}

const canReview = computed(() => hasPermission('review:approve'))

// ===== 子 Tab 切换 =====
const subTab = ref('versions')
const publishHistory = ref<any[]>([])
const publishPlans = ref<any[]>([])

async function loadPublishHistory() {
  try {
    const res = await api.getPublishHistory(props.kbId)
    publishHistory.value = Array.isArray(res) ? res : (res as any)?.list || []
  } catch { /* 保持现有数据 */ }
}

async function loadPublishPlans() {
  try {
    const res = await api.getPublishPlans(props.kbId)
    publishPlans.value = Array.isArray(res) ? res : (res as any)?.list || []
  } catch {
    publishPlans.value = []
  }
}

// ===== 发布历史：查看线上/线下版本 =====
const versionDetail = ref<any>(null)
const showVersionDetail = ref(false)
const versionDetailTitle = ref('')

async function handleViewOnline(row: any) {
  try {
    const res = await api.getOnlineVersion(props.kbId, row.knowledgeId)
    versionDetail.value = res
    versionDetailTitle.value = '线上版本'
    showVersionDetail.value = true
  } catch {
    ElMessage.info('暂无线上版本数据')
  }
}

async function handleViewOffline(row: any) {
  try {
    const res = await api.getOfflineVersion(props.kbId, row.knowledgeId)
    versionDetail.value = res
    versionDetailTitle.value = '线下版本'
    showVersionDetail.value = true
  } catch {
    ElMessage.info('暂无线下版本数据')
  }
}

// ===== 发布计划 =====
const showPlanDialog = ref(false)
const planForm = ref({ name: '', publishTime: '', description: '', knowledgeId: '' })
const planExecution = ref<any>(null)
const showExecutionDialog = ref(false)

function handleNewPlan() {
  planForm.value = { name: '', publishTime: '', description: '', knowledgeId: '', }
  showPlanDialog.value = true
}

async function handleCreatePlan() {
  if (!planForm.value.name || !planForm.value.publishTime) {
    ElMessage.warning('请填写计划名称和发布时间')
    return
  }
  try {
    await api.createPublishPlan(props.kbId, {
      name: planForm.value.name,
      scheduledAt: planForm.value.publishTime,
      description: planForm.value.description || '',
    })
    ElMessage.success('发布计划已创建')
    showPlanDialog.value = false
    await loadPublishPlans()
  } catch {
    ElMessage.error('创建计划失败')
  }
}

async function handleViewExecution(plan: any) {
  try {
    const res = await api.getPublishPlanExecution(props.kbId, plan.id || plan.planId)
    planExecution.value = res
    showExecutionDialog.value = true
  } catch {
    ElMessage.info('暂无执行记录')
  }
}

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

function onTabChange(name: string) {
  if (name === 'history') { loadPublishHistory(); loadPublishedVersion() }
  else if (name === 'plans') loadPublishPlans()
}

// ===== 发布历史：发布知识 / 撤回 =====
const canPublishLatest = computed(() => versions.value.some((v: any) => v.publishStatus === 'approved'))

async function handlePublishLatest() {
  const approved = versions.value.find((v: any) => v.publishStatus === 'approved')
  if (!approved) { ElMessage.warning('没有可发布的版本'); return }
  await handlePublish(approved)
  await loadPublishHistory()
}

async function handleRevoke(row: any) {
  try {
    await ElMessageBox.confirm(`确定撤回该发布？撤回后线上版本将回退到上一个已发布版本。`, '撤回确认', {
      confirmButtonText: '撤回', cancelButtonText: '取消', type: 'warning',
    })
    await api.revokeKnowledge(props.kbId, row.knowledgeId || row.id)
    ElMessage.success('已撤回发布')
    await refresh()
    await loadPublishHistory()
  } catch { /* cancelled */ }
}

async function handleResetToPrev(row: any) {
  try {
    await ElMessageBox.confirm(`确定将知识重置为上一次发布的版本？此操作不可撤回。`, '重置确认', {
      confirmButtonText: '重置', cancelButtonText: '取消', type: 'warning',
    })
    await api.resetKnowledge(props.kbId, row.knowledgeId || row.id)
    ElMessage.success('知识已重置为上一个已发布版本')
    await loadPublishHistory()
  } catch { /* cancelled */ }
}

async function loadPublishedVersion() {
  try {
    publishedVersion.value = await api.getPublishedVersion(props.kbId).catch(() => null)
  } catch { /* ignore */ }
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

    <el-tabs v-model="subTab" @tab-change="onTabChange">
      <!-- ===== Tab 1: 版本管理 ===== -->
      <el-tab-pane label="版本管理" name="versions">
        <el-table :data="versions" stripe>
          <el-table-column label="版本" width="80" align="center">
            <template #default="{ row }">
              <span class="publish-panel__ver">v{{ (row as any).version }}</span>
            </template>
          </el-table-column>
          <el-table-column label="类型" width="70" align="center">
            <template #default="{ row }">
              <el-tag v-if="publishedVersion && (row as any).id === publishedVersion.id" type="success" size="small" effect="dark">线上</el-tag>
              <el-tag v-else type="info" size="small" plain>线下</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="(PUBLISH_STATUS_COLORS as any)[(row as any).publishStatus]" size="small">
                {{ (PUBLISH_STATUS_LABELS as any)[(row as any).publishStatus] }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="文件/切片" width="100" align="center">
            <template #default="{ row }">{{ (row as any).fileCount }} / {{ (row as any).chunkCount }}</template>
          </el-table-column>
          <el-table-column label="说明" min-width="140">
            <template #default="{ row }">{{ (row as any).description || '-' }}</template>
          </el-table-column>
          <el-table-column label="创建人" width="90">
            <template #default="{ row }">{{ (row as any).createdBy }}</template>
          </el-table-column>
          <el-table-column label="创建时间" width="160">
            <template #default="{ row }">{{ (row as any).createdAt }}</template>
          </el-table-column>
          <el-table-column label="状态说明" min-width="180">
            <template #default="{ row }">
              <span class="publish-panel__hint">
                <template v-if="(row as any).publishStatus === 'draft'">可提交审核</template>
                <template v-else-if="(row as any).publishStatus === 'pending_review'">
                  等待 {{ getReviewTask((row as any).id)?.reviewer || '管理员' }} 审核
                </template>
                <template v-else-if="(row as any).publishStatus === 'approved'">审核通过，可发布</template>
                <template v-else-if="(row as any).publishStatus === 'published'">线上版本，用户可见</template>
                <template v-else-if="(row as any).publishStatus === 'rejected'">
                  已驳回{{ getReviewTask((row as any).id)?.comment ? '：' + getReviewTask((row as any).id)!.comment : '' }}
                </template>
              </span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-button v-if="(row as any).publishStatus === 'draft'" type="primary" link size="small" @click="handleSubmitReview(row as any)">提交审核</el-button>
              <template v-if="(row as any).publishStatus === 'pending_review' && canReview">
                <el-button type="success" link size="small" @click="handleInlineApprove(row as any)"><el-icon><Select /></el-icon> 通过</el-button>
                <el-button type="danger" link size="small" @click="handleInlineReject(row as any)"><el-icon><CloseBold /></el-icon> 驳回</el-button>
              </template>
              <el-button v-if="(row as any).publishStatus === 'approved'" type="success" link size="small" @click="handlePublish(row as any)"><el-icon><CircleCheck /></el-icon> 发布</el-button>
              <el-button v-if="(row as any).publishStatus === 'published'" type="warning" link size="small" @click="handleRevertToDraft(row as any)"><el-icon><RefreshRight /></el-icon> 回退</el-button>
              <el-button v-if="(row as any).publishStatus === 'rejected'" type="warning" link size="small" @click="handleSubmitReview(row as any)"><el-icon><RefreshRight /></el-icon> 重新提交</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="versions.length === 0" description="暂无版本，点击上方「创建发布」开始" :image-size="60" />
      </el-tab-pane>

      <!-- ===== Tab 2: 发布历史 ===== -->
      <el-tab-pane label="发布历史" name="history">
        <div class="card-panel">
          <div class="section-header">
            <div class="section-title">发布历史记录</div>
            <div style="display:flex;gap:8px">
              <el-button size="small" type="primary" @click="handlePublishLatest" :disabled="!canPublishLatest">
                <el-icon><CircleCheck /></el-icon> 发布知识
              </el-button>
              <el-button size="small" @click="loadPublishHistory"><el-icon><RefreshRight /></el-icon> 刷新</el-button>
            </div>
          </div>
          <div v-if="!canPublishLatest" style="font-size:12px;color:#909399;margin-bottom:12px">
            提示：当前没有可发布的版本（需要有一个"审核通过"的版本才能发布）
          </div>
          <el-table :data="publishHistory" stripe size="small">
            <el-table-column prop="id" label="编号" width="80" show-overflow-tooltip />
            <el-table-column label="发布类型" width="90">
              <template #default="{ row }">
                <el-tag :type="(row as any).publishType === 'publish' ? 'success' : (row as any).publishType === 'revoke' ? 'danger' : 'warning'" size="small">
                  {{ (row as any).publishType === 'publish' ? '发布' : (row as any).publishType === 'revoke' ? '撤回' : (row as any).publishType }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="version" label="版本" width="60" align="center" />
            <el-table-column label="发布状态" width="100">
              <template #default="{ row }">
                <el-tag :type="(row as any).status === 'published' ? 'success' : (row as any).status === 'revoked' ? 'danger' : 'info'" size="small">
                  {{ (row as any).status === 'published' ? '已发布' : (row as any).status === 'revoked' ? '已撤回' : (row as any).status }}
                </el-tag>
                <div style="font-size:11px;color:#909399;margin-top:2px">
                  {{ (row as any).publishType === 'publish' ? '用户可见' : (row as any).publishType === 'revoke' ? '用户不可见' : '' }}
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="publishedAt" label="操作时间" width="160" />
            <el-table-column prop="operator" label="操作人" width="100" />
            <el-table-column prop="description" label="说明" min-width="140" show-overflow-tooltip />
            <el-table-column label="操作" width="260" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="handleViewOnline(row)">线上版本</el-button>
                <el-button link type="primary" size="small" @click="handleViewOffline(row)">线下版本</el-button>
                <el-button v-if="(row as any).publishType === 'publish' && (row as any).status === 'published'" link type="danger" size="small" @click="handleRevoke(row)">撤回</el-button>
                <el-button v-if="(row as any).publishType === 'publish' && (row as any).status === 'published'" link type="warning" size="small" @click="handleResetToPrev(row)">重置</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="publishHistory.length === 0" description="暂无发布历史" :image-size="60" />
        </div>
      </el-tab-pane>

      <!-- ===== Tab 3: 发布计划 ===== -->
      <el-tab-pane label="发布计划" name="plans">
        <div class="card-panel">
          <div class="section-header">
            <div class="section-title">发布计划列表</div>
            <el-button size="small" type="primary" @click="handleNewPlan"><el-icon><Plus /></el-icon> 新建计划</el-button>
          </div>
          <el-table :data="publishPlans" stripe size="small">
            <el-table-column prop="name" label="计划名称" min-width="140" />
            <el-table-column prop="scheduledAt" label="计划时间" width="160" />
            <el-table-column prop="status" label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="(row as any).status === 'completed' ? 'success' : (row as any).status === 'running' ? 'warning' : 'info'" size="small">
                  {{ (row as any).status === 'completed' ? '已完成' : (row as any).status === 'running' ? '执行中' : '待执行' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="description" label="说明" min-width="140" show-overflow-tooltip />
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="handleViewExecution(row)">执行情况</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="publishPlans.length === 0" description="暂无发布计划" :image-size="60" />
        </div>
      </el-tab-pane>

    </el-tabs>

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
        <el-form-item label="发布方式">
          <el-radio-group v-model="createForm.publishNow">
            <el-radio :value="false" style="margin-bottom:8px">
              <div><b>保存为草稿</b><div style="font-size:12px;color:#909399">仅创建版本，后续手动提交审核和发布</div></div>
            </el-radio>
            <el-radio :value="true">
              <div><b>创建并发布上线</b><div style="font-size:12px;color:#909399">自动提交审核、通过并发布到线上</div></div>
            </el-radio>
          </el-radio-group>
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

    <!-- 版本详情弹窗（线上/线下版本） -->
    <el-dialog v-model="showVersionDetail" :title="versionDetailTitle" width="520px">
      <div v-if="versionDetail" class="version-detail">
        <div class="version-detail__row"><label>版本号</label><span>v{{ (versionDetail as any).version }}</span></div>
        <div class="version-detail__row"><label>名称</label><span>{{ (versionDetail as any).name }}</span></div>
        <div class="version-detail__row"><label>状态</label><el-tag size="small">{{ (versionDetail as any).status }}</el-tag></div>
        <div class="version-detail__row"><label>说明</label><span>{{ (versionDetail as any).description || '-' }}</span></div>
        <div class="version-detail__row"><label>创建时间</label><span>{{ (versionDetail as any).createdAt }}</span></div>
        <div class="version-detail__row"><label>文件/切片</label><span>{{ (versionDetail as any).fileCount || 0 }} / {{ (versionDetail as any).chunkCount || 0 }}</span></div>
      </div>
      <template #footer><el-button @click="showVersionDetail = false">关闭</el-button></template>
    </el-dialog>

    <!-- 新建发布计划弹窗 -->
    <el-dialog v-model="showPlanDialog" title="新建发布计划" width="480px">
      <el-form label-width="100px">
        <el-form-item label="计划名称" required>
          <el-input v-model="planForm.name" placeholder="例如：每周例行发布" />
        </el-form-item>
        <el-form-item label="发布时间" required>
          <el-date-picker v-model="planForm.publishTime" type="datetime" placeholder="选择发布时间" value-format="YYYY-MM-DD HH:mm" style="width:100%" />
        </el-form-item>
        <el-form-item label="备注说明">
          <el-input v-model="planForm.description" type="textarea" :rows="2" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showPlanDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreatePlan">创建计划</el-button>
      </template>
    </el-dialog>

    <!-- 计划执行情况弹窗 -->
    <el-dialog v-model="showExecutionDialog" title="计划执行情况" width="480px">
      <div v-if="planExecution" class="version-detail">
        <div class="version-detail__row"><label>计划名称</label><span>{{ (planExecution as any).name || (planExecution as any).planName }}</span></div>
        <div class="version-detail__row"><label>执行状态</label>
          <el-tag :type="(planExecution as any).status === 'completed' ? 'success' : 'warning'" size="small">
            {{ (planExecution as any).status === 'completed' ? '已完成' : (planExecution as any).status === 'running' ? '执行中' : '待执行' }}
          </el-tag>
        </div>
        <div class="version-detail__row"><label>执行时间</label><span>{{ (planExecution as any).executedAt || '-' }}</span></div>
        <div class="version-detail__row"><label>执行结果</label><span>{{ (planExecution as any).result || (planExecution as any).message || '-' }}</span></div>
      </div>
      <template #footer><el-button @click="showExecutionDialog = false">关闭</el-button></template>
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

// --- 子 Tab 通用 ---
.card-panel {
  background: $bg-white;
  border-radius: $radius-base;
  padding: $spacing-base;
}
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-base;
}
.section-title {
  font-size: 15px;
  font-weight: 600;
}

// --- 版本详情 ---
.version-detail {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}
.version-detail__row {
  display: flex;
  align-items: center;
  label { width: 80px; font-size: 13px; color: $text-secondary; flex-shrink: 0; }
  span { font-size: 13px; color: $text-primary; }
}
</style>
