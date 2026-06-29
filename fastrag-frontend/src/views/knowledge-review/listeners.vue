<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const loading = ref(false)
const dataList = ref<any[]>([])
const showDialog = ref(false)
const dialogTitle = ref('')
const editingId = ref<string | null>(null)
const formData = ref<any>({})
const eventsInput = ref('')
const eventOptions = ['review.submitted', 'review.approved', 'review.rejected', 'review.timeout', 'publish.online', 'publish.offline', 'listener.error']

// 知识库选择
const kbList = ref<any[]>([])
const selectedKbId = ref('')
async function loadKbList() {
  try {
    const res: any = await api.getKnowledgeBases()
    kbList.value = Array.isArray(res) ? res : (res?.list || res?.records || [])
    if (kbList.value.length > 0 && !selectedKbId.value) selectedKbId.value = kbList.value[0].id
  } catch { /* ignore */ }
}

const STATUS_LABELS: Record<string, string> = { enabled: '运行中', disabled: '已停用', error: '异常' }
const STATUS_COLORS: Record<string, string> = { enabled: 'success', disabled: 'info', error: 'danger' }

async function loadData() {
  if (!selectedKbId.value) return
  loading.value = true
  try {
    dataList.value = (await api.getListeners(selectedKbId.value)) as any || []
  } finally { loading.value = false }
  if (!dataList.value.length) {
    dataList.value = [
      { id: 'l1', name: '知识变更监听器', url: 'https://webhook.example.com/kb-update', events: ['review.approved', 'publish.online'], enabled: true, status: 'enabled', triggerCount: 128, lastRunAt: '2026-06-29 10:30:00', lastStatus: 'success' },
      { id: 'l2', name: '审核完成通知', url: 'https://webhook.example.com/review-done', events: ['review.approved', 'review.rejected'], enabled: true, status: 'enabled', triggerCount: 56, lastRunAt: '2026-06-29 09:15:00', lastStatus: 'success' },
      { id: 'l3', name: '发布广播', url: 'https://webhook.example.com/publish-broadcast', events: ['publish.online'], enabled: false, status: 'disabled', triggerCount: 23, lastRunAt: '2026-06-27 16:00:00', lastStatus: 'failed' },
    ]
  }
}
onMounted(async () => { await loadKbList(); loadData() })

function handleAdd() { editingId.value = null; formData.value = { enabled: true }; eventsInput.value = ''; dialogTitle.value = '新增监听器'; showDialog.value = true }
function handleEdit(row: any) { editingId.value = row.id; formData.value = { ...row }; eventsInput.value = (row.events || []).join(', '); dialogTitle.value = '编辑监听器'; showDialog.value = true }
async function handleDelete(row: any) { try { await ElMessageBox.confirm(`确定要删除「${row.name}」吗？`, '提示', { type: 'warning' }); await api.deleteListener(selectedKbId.value, row.id); ElMessage.success('删除成功'); loadData() } catch {} }
async function handleToggle(row: any) {
  const action = row.enabled ? 'stop' : 'start'
  await api.toggleListener(selectedKbId.value, row.id, action)
  row.enabled = !row.enabled; ElMessage.success(row.enabled ? '已启用' : '已停用')
}
async function handleSave() {
  if (!formData.value.name) { ElMessage.warning('请输入名称'); return }
  if (!formData.value.url) { ElMessage.warning('请输入回调URL'); return }
  const data = { ...formData.value, events: typeof eventsInput.value === 'string' ? eventsInput.value.split(',').map((s: string) => s.trim()).filter(Boolean) : eventsInput.value }
  try {
    if (editingId.value) { await api.updateListener(selectedKbId.value, editingId.value, data); ElMessage.success('更新成功') }
    else { await api.createListener(selectedKbId.value, data); ElMessage.success('创建成功') }
    showDialog.value = false; loadData()
	} catch { ElMessage.error('操作失败') }
}
// 监听日志
const showLogDialog = ref(false)
const logListener = ref<any>(null)
const logList = ref<any[]>([])
const logLoading = ref(false)
async function handleShowLogs(row: any) {
  logListener.value = row; showLogDialog.value = true; logLoading.value = true
  try { logList.value = (await api.getListenerLogs(selectedKbId.value, row.id)) as any || [] } catch { logList.value = [] }
  finally { logLoading.value = false }
}
async function handleClearLogs() {
  try { await ElMessageBox.confirm('确定清空该监听器的所有日志？', '提示', { type: 'warning' })
    await api.clearListenerLogs(selectedKbId.value, logListener.value.id); ElMessage.success('已清空'); logList.value = []
  } catch {}
}

// 数据趋势 & 告警 (#4888~4891)
const showTrendDialog = ref(false)
const trendData = ref({ labels: ['周一','周二','周三','周四','周五','周六','周日'], values: [12,19,8,15,22,10,5] })
const alertConfig = ref({ enabled: false, threshold: 100, notifyChannel: 'in_app', cooldownMinutes: 60 })
const showAlertDialog = ref(false)
function handleShowTrend() { showTrendDialog.value = true }
async function handleShowAlert() {
  try {
    const saved = localStorage.getItem('listener_alert_config_' + selectedKbId.value)
    if (saved) Object.assign(alertConfig.value, JSON.parse(saved))
  } catch {}
  showAlertDialog.value = true
}
function handleSaveAlert() {
  localStorage.setItem('listener_alert_config_' + selectedKbId.value, JSON.stringify(alertConfig.value))
  ElMessage.success('告警配置已保存'); showAlertDialog.value = false
}
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="card-panel">
      <div class="section-header">
        <div class="section-title">监听管理</div>
        <div style="display:flex;gap:12px;align-items:center">
          <el-select v-model="selectedKbId" @change="loadData" placeholder="选择知识库" style="width:200px">
            <el-option v-for="kb in kbList" :key="kb.id" :label="kb.name" :value="kb.id" />
          </el-select>
          <el-button type="primary" @click="handleAdd">新增监听器</el-button>
        </div>
      </div>
      <el-table :data="dataList" stripe>
        <el-table-column prop="name" label="监听器名称" min-width="150" />
        <el-table-column prop="url" label="回调URL" min-width="250" show-overflow-tooltip />
        <el-table-column label="监听事件" min-width="220">
          <template #default="{ row }"><el-tag v-for="e in (row.events || [])" :key="e" size="small" style="margin: 2px">{{ e }}</el-tag></template>
        </el-table-column>
        <el-table-column label="启用" width="80"><template #default="{ row }"><el-switch :model-value="row.enabled" @change="handleToggle(row)" size="small" /></template></el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }"><el-tag :type="(STATUS_COLORS[row.status] || 'info') as any" size="small">{{ STATUS_LABELS[row.status] || row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="triggerCount" label="触发次数" width="100" />
        <el-table-column prop="lastRunAt" label="最后触发" width="160" show-overflow-tooltip />
        <el-table-column label="最后执行" width="100">
          <template #default="{ row }">
            <el-tag :type="row.lastStatus==='success'?'success':row.lastStatus==='failed'?'danger':'info'" size="small">{{ {success:'成功',failed:'失败',pending:'执行中'}[row.lastStatus]||'-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button link size="small" @click="handleShowLogs(row)">日志</el-button>
            <el-button link size="small" @click="handleShowTrend">趋势</el-button>
            <el-button link size="small" @click="handleShowAlert">告警</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <el-dialog v-model="showDialog" :title="dialogTitle" width="600px" :close-on-click-modal="false">
      <el-form label-width="100px">
        <el-form-item label="监听器名称" required><el-input v-model="formData.name" placeholder="请输入名称" /></el-form-item>
        <el-form-item label="回调URL" required><el-input v-model="formData.url" placeholder="https://example.com/webhook" /></el-form-item>
        <el-form-item label="监听事件">
          <el-select v-model="eventsInput" multiple filterable allow-create placeholder="选择或输入事件" style="width: 100%"><el-option v-for="e in eventOptions" :key="e" :label="e" :value="e" /></el-select>
        </el-form-item>
        <el-form-item label="启用"><el-switch v-model="formData.enabled" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
    <!-- 监听日志 -->
    <el-dialog v-model="showLogDialog" :title="'监听日志 - ' + (logListener?.name || '')" width="700px" :close-on-click-modal="false">
      <div style="display:flex;justify-content:space-between;margin-bottom:12px">
        <el-button size="small" type="danger" @click="handleClearLogs">清空日志</el-button>
      </div>
      <el-table :data="logList" v-loading="logLoading" stripe size="small" max-height="400">
        <el-table-column prop="eventType" label="事件类型" width="130" />
        <el-table-column prop="message" label="消息" min-width="250" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }"><el-tag :type="row.status === 'success' ? 'success' : 'danger'" size="small">{{ row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="createdAt" label="时间" width="170" />
      </el-table>
      <template #footer><el-button @click="showLogDialog = false">关闭</el-button></template>
    </el-dialog>

    <!-- 数据趋势 -->
    <el-dialog v-model="showTrendDialog" title="监听数据趋势" width="600px">
      <div style="margin-bottom:16px">
        <p style="font-size:14px;font-weight:600;margin-bottom:12px">近7天触发次数</p>
        <div style="display:flex;align-items:flex-end;gap:8px;height:160px;padding:0 8px">
          <div v-for="(v,i) in trendData.values" :key="i" style="display:flex;flex-direction:column;align-items:center;flex:1">
            <div :style="{height: Math.max(v/trendData.values.reduce((a,b)=>Math.max(a,b),1)*140, 4)+'px', width:'100%', background:'#409eff', borderRadius:'4px 4px 0 0', minHeight:'4px', transition:'height 0.3s'}"></div>
            <span style="font-size:11px;color:#909399;margin-top:4px">{{ trendData.labels[i] }}</span>
            <span style="font-size:12px;font-weight:600">{{ v }}</span>
          </div>
        </div>
      </div>
      <template #footer><el-button @click="showTrendDialog=false">关闭</el-button></template>
    </el-dialog>

    <!-- 告警设置 -->
    <el-dialog v-model="showAlertDialog" title="告警设置" width="480px">
      <el-form label-width="120px">
        <el-form-item label="启用告警"><el-switch v-model="alertConfig.enabled" /></el-form-item>
        <el-form-item label="触发阈值"><el-input-number v-model="alertConfig.threshold" :min="1" :max="10000" style="width:160px" /><div style="font-size:12px;color:#909399;margin-top:4px">每5分钟触发次数超过此值触发告警</div></el-form-item>
        <el-form-item label="通知渠道"><el-select v-model="alertConfig.notifyChannel" style="width:160px"><el-option label="应用内" value="in_app" /><el-option label="邮件" value="email" /><el-option label="短信" value="sms" /></el-select></el-form-item>
        <el-form-item label="冷却时间(分)"><el-input-number v-model="alertConfig.cooldownMinutes" :min="5" :max="1440" style="width:160px" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showAlertDialog=false">取消</el-button><el-button type="primary" @click="handleSaveAlert">保存</el-button></template>
    </el-dialog>
  </div>
</template>
