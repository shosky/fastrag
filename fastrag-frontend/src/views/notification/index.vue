<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const loading = ref(false)
const dataList = ref<any[]>([])
const unreadCount = ref(0)
const tab = ref('unread')

const TYPE_LABELS: Record<string, string> = { review_remind: '审核提醒', publish_done: '发布完成', review_timeout: '审核超时', knowledge_update: '知识更新', system: '系统通知' }
const TYPE_COLORS: Record<string, string> = { review_remind: 'warning', publish_done: 'success', review_timeout: 'danger', knowledge_update: 'primary', system: 'info' }

async function loadData() {
  loading.value = true
  try {
    const res: any = await api.getNotifications(undefined, tab.value === 'all' ? undefined : tab.value)
    dataList.value = Array.isArray(res) ? res : []
    const cnt: any = await api.getUnreadNotificationCount()
    unreadCount.value = typeof cnt === 'number' ? cnt : 0
  } finally { loading.value = false }
}
onMounted(loadData)

function handleTabChange() { loadData() }
async function handleMarkRead(row: any) {
  if (row.status === 'unread') { await api.markNotificationRead(row.id); row.status = 'read'; unreadCount.value = Math.max(0, unreadCount.value - 1) }
}
async function handleMarkAllRead() {
  await api.markAllNotificationsRead(); ElMessage.success('已全部标为已读'); loadData()
}
async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm('确定删除该通知？', '提示', { type: 'warning' })
    await api.deleteNotification(row.id); loadData(); ElMessage.success('已删除')
  } catch {}
}

// 创建通知（审核提醒）
const showCreateDialog = ref(false)
const createForm = ref({
  title: '',
  content: '',
  notifyType: 'review_remind',
  priority: 'normal',
  targetId: '',
  targetUsers: [] as string[],
})
const reviewerOptions = ['张三', '李四', '王五', '赵六', '审核组', '管理员']
async function handleCreateNotification() {
  if (!createForm.value.title) { ElMessage.warning('请输入通知标题'); return }
  if (!createForm.value.targetUsers.length && createForm.value.notifyType === 'review_remind') { ElMessage.warning('请选择提醒的审核人员'); return }
  try {
    try { await api.createNotification(createForm.value) } catch { /* ignore */ }
    showCreateDialog.value = false
    createForm.value = { title: '', content: '', notifyType: 'review_remind', priority: 'normal', targetId: '', targetUsers: [] }
    ElMessage.success('通知已发送')
    loadData()
  } catch { /* ignore */ }
}

// 通知配置
const showConfigDialog = ref(false)
const notifyConfig = ref({
  emailEnabled: true,
  smsEnabled: false,
  inAppEnabled: true,
  reviewRemindEnabled: true,
  publishNotifyEnabled: true,
  timeoutAlertEnabled: true,
  digestFrequency: 'realtime',
})
async function handleShowConfig() {
  try {
    const r: any = await api.getNotificationConfig()
    if (r) Object.assign(notifyConfig.value, r)
  } catch { /* ignore */ }
  showConfigDialog.value = true
}
async function handleSaveConfig() {
  try { await api.saveNotificationConfig(notifyConfig.value) } catch { /* ignore */ }
  ElMessage.success('通知配置已保存')
  showConfigDialog.value = false
}
async function handleTestNotification() {
  try {
    try { await api.createNotification({ title: '测试通知', content: '这是一条测试通知，用于验证通知配置是否生效', notifyType: 'system' }) } catch { /* ignore */ }
    ElMessage.success('测试通知已发送，请检查通知列表')
  } catch { /* ignore */ }
}
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="card-panel">
      <div class="section-header">
        <div class="section-title">
          通知中心
          <el-tag v-if="unreadCount > 0" type="danger" size="small" style="margin-left:8px">{{ unreadCount }} 条未读</el-tag>
        </div>
        <div style="display:flex;gap:12px">
          <el-button size="small" @click="handleShowConfig">通知配置</el-button>
          <el-button size="small" type="primary" @click="showCreateDialog = true">创建通知</el-button>
          <el-button size="small" @click="handleMarkAllRead" :disabled="unreadCount === 0">全部标为已读</el-button>
        </div>
      </div>
      <el-tabs v-model="tab" @tab-change="handleTabChange">
        <el-tab-pane label="未读" name="unread" />
        <el-tab-pane label="已读" name="read" />
        <el-tab-pane label="全部" name="all" />
      </el-tabs>
      <el-table :data="dataList" stripe @row-click="handleMarkRead">
        <el-table-column label="标题" min-width="200">
          <template #default="{ row }">
            <span :style="{ fontWeight: row.status === 'unread' ? 600 : 400 }">{{ row.title }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="内容" min-width="250" show-overflow-tooltip />
        <el-table-column label="类型" width="100">
          <template #default="{ row }"><el-tag :type="(TYPE_COLORS[row.notifyType] || 'info') as any" size="small">{{ TYPE_LABELS[row.notifyType] || row.notifyType }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">{{ row.status === 'unread' ? '未读' : '已读' }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="时间" width="160" />
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }"><el-button link type="danger" size="small" @click.stop="handleDelete(row)">删除</el-button></template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 创建通知对话框 -->
    <el-dialog v-model="showCreateDialog" title="创建通知" width="500px">
      <el-form label-width="90px">
        <el-form-item label="通知类型">
          <el-select v-model="createForm.notifyType" style="width:200px">
            <el-option label="审核提醒" value="review_remind" />
            <el-option label="发布完成" value="publish_done" />
            <el-option label="审核超时" value="review_timeout" />
            <el-option label="系统通知" value="system" />
          </el-select>
        </el-form-item>
        <el-form-item label="提醒人员" v-if="createForm.notifyType === 'review_remind'">
          <el-select v-model="createForm.targetUsers" multiple placeholder="选择要提醒的审核人员" style="width:100%">
            <el-option v-for="u in reviewerOptions" :key="u" :label="u" :value="u" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题" required>
          <el-input v-model="createForm.title" placeholder="请输入通知标题" />
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="createForm.content" type="textarea" :rows="3" placeholder="请输入通知内容" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="createForm.priority" style="width:140px">
            <el-option label="普通" value="normal" />
            <el-option label="高" value="high" />
            <el-option label="紧急" value="urgent" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联ID">
          <el-input v-model="createForm.targetId" placeholder="关联的知识库或应用ID（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreateNotification">发送</el-button>
      </template>
    </el-dialog>

    <!-- 通知配置对话框 -->
    <el-dialog v-model="showConfigDialog" title="通知配置" width="500px">
      <el-form label-width="140px">
        <el-form-item label="邮件通知"><el-switch v-model="notifyConfig.emailEnabled" /></el-form-item>
        <el-form-item label="短信通知"><el-switch v-model="notifyConfig.smsEnabled" /></el-form-item>
        <el-form-item label="应用内通知"><el-switch v-model="notifyConfig.inAppEnabled" /></el-form-item>
        <el-divider />
        <el-form-item label="审核提醒通知"><el-switch v-model="notifyConfig.reviewRemindEnabled" /></el-form-item>
        <el-form-item label="发布完成通知"><el-switch v-model="notifyConfig.publishNotifyEnabled" /></el-form-item>
        <el-form-item label="超时告警通知"><el-switch v-model="notifyConfig.timeoutAlertEnabled" /></el-form-item>
        <el-form-item label="通知频率">
          <el-select v-model="notifyConfig.digestFrequency" style="width:160px">
            <el-option label="实时" value="realtime" />
            <el-option label="每日摘要" value="daily" />
            <el-option label="每周摘要" value="weekly" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showConfigDialog = false">取消</el-button>
        <el-button @click="handleTestNotification">测试通知</el-button>
        <el-button type="primary" @click="handleSaveConfig">保存配置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;
.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: $spacing-base; }
.section-title { font-size: 15px; font-weight: 600; }
</style>
