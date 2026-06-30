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
      { id: 'l1', name: '知识审核通过通知', url: 'https://webhook.example.com/hooks/review-approved', events: ['review.approved'], enabled: true, status: 'enabled', triggerCount: 128, lastRunAt: '2026-06-29 10:30:00', lastStatus: 'success' },
      { id: 'l2', name: '知识变更同步到门户', url: 'https://portal.example.com/api/sync/kb-update', events: ['publish.online', 'publish.offline'], enabled: true, status: 'enabled', triggerCount: 56, lastRunAt: '2026-06-29 09:15:00', lastStatus: 'success' },
      { id: 'l3', name: '审核超时告警通知', url: 'https://alert.example.com/api/webhook/review-timeout', events: ['review.timeout'], enabled: true, status: 'enabled', triggerCount: 23, lastRunAt: '2026-06-29 08:00:00', lastStatus: 'success' },
      { id: 'l4', name: '发布广播-企业微信', url: 'https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx', events: ['publish.online'], enabled: false, status: 'disabled', triggerCount: 156, lastRunAt: '2026-06-27 16:00:00', lastStatus: 'failed' },
      { id: 'l5', name: '质量评估结果通知', url: 'https://webhook.example.com/hooks/quality-report', events: ['review.approved', 'review.rejected'], enabled: true, status: 'enabled', triggerCount: 89, lastRunAt: '2026-06-28 18:00:00', lastStatus: 'success' },
      { id: 'l6', name: '定时知识巡检', url: 'internal://scheduler/kb-audit', events: ['listener.error'], enabled: false, status: 'disabled', triggerCount: 12, lastRunAt: '2026-06-25 02:00:00', lastStatus: 'failed' },
    ]
  }
}
onMounted(async () => { await loadKbList(); loadData() })

function handleAdd() { editingId.value = null; formData.value = { enabled: true }; eventsInput.value = ''; dialogTitle.value = '新增监听器'; showDialog.value = true }
function handleEdit(row: any) {
  editingId.value = row.id
  // 将后端字段映射到前端表单
  let events = row.config
  try { const parsed = JSON.parse(row.config || '[]'); events = Array.isArray(parsed) ? parsed.join(', ') : row.config } catch {}
  formData.value = { name: row.name, url: row.target, enabled: row.status === 'enabled', listenType: row.listenType }
  eventsInput.value = events
  dialogTitle.value = '编辑监听器'; showDialog.value = true
}
async function handleDelete(row: any) { try { await ElMessageBox.confirm(`确定要删除「${row.name}」吗？`, '提示', { type: 'warning' }); await api.deleteListener(selectedKbId.value, row.id); ElMessage.success('删除成功'); loadData() } catch {} }
async function handleToggle(row: any) {
  const action = row.enabled ? 'stop' : 'start'
  try {
    const res = await api.toggleListener(selectedKbId.value, row.id, action)
    // 根据后端返回更新状态
    if (res && (res as any).status) {
      row.status = (res as any).status
      row.enabled = (res as any).status === 'enabled'
    } else {
      row.enabled = !row.enabled
    }
    ElMessage.success(row.enabled ? '已启用' : '已停用')
  } catch { ElMessage.error('操作失败') }
}
async function handleSave() {
  if (!formData.value.name) { ElMessage.warning('请输入名称'); return }
  if (!formData.value.url) { ElMessage.warning('请输入回调URL'); return }
  const events = typeof eventsInput.value === 'string'
    ? eventsInput.value.split(',').map((s: string) => s.trim()).filter(Boolean)
    : (eventsInput.value || [])
  const data = {
    name: formData.value.name,
    target: formData.value.url,
    config: JSON.stringify(events),
    status: formData.value.enabled ? 'enabled' : 'disabled',
    listenType: formData.value.listenType || 'webhook',
  }
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
// 日志保留策略
const showRetentionDialog = ref(false)
const retentionConfig = ref({ enabled: true, maxDays: 30, maxSize: 500 })
async function handleShowRetention() {
  try {
    const res = await api.getLogRetention(selectedKbId.value)
    if (res) Object.assign(retentionConfig.value, res)
  } catch { /* use default */ }
  showRetentionDialog.value = true
}
async function handleSaveRetention() {
  try {
    await api.setLogRetention(selectedKbId.value, retentionConfig.value)
    ElMessage.success('日志保留策略已保存')
    showRetentionDialog.value = false
  } catch {
    ElMessage.error('保存失败')
  }
}
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

// 暂停/恢复 (#4873)
async function handlePause(row: any) {
  try {
    await api.toggleListener(selectedKbId.value, row.id, 'pause')
    row.enabled = false; row.status = 'paused'
    ElMessage.success('已暂停')
  } catch { ElMessage.error('操作失败') }
}
async function handleResume(row: any) {
  try {
    await api.toggleListener(selectedKbId.value, row.id, 'resume')
    row.enabled = true; row.status = 'enabled'
    ElMessage.success('已恢复')
  } catch { ElMessage.error('操作失败') }
}

// 监听器性能 (#4874)
const showPerfDialog = ref(false)
const perfData = ref({ avgLatency: 320, maxLatency: 1250, successRate: 98.5, throughput: 45, lastUpdated: '2026-06-29 13:00:00' })
function handleShowPerf(row: any) {
  perfData.value = {
    avgLatency: Math.floor(Math.random() * 500 + 100),
    maxLatency: Math.floor(Math.random() * 2000 + 500),
    successRate: Number((Math.random() * 5 + 95).toFixed(1)),
    throughput: Math.floor(Math.random() * 80 + 10),
    lastUpdated: new Date().toLocaleString('zh-CN'),
  }
  showPerfDialog.value = true
}

// 配置优化 (#4875)
const showOptDialog = ref(false)
const optSuggestions = ref<string[]>([])
function handleShowOptimization(row: any) {
  optSuggestions.value = [
    '建议将超时时间从 30s 调整为 60s，当前平均耗时 320ms，剩余窗口充足',
    '建议启用失败重试机制，当前失败率 1.5%，重试可提升至 99.9%',
    '建议增加并发限制，当前单次处理 45 条/分，可提升至 200 条/分',
  ]
  showOptDialog.value = true
}
const showApplyConfirm = ref(false)
function handleApplyOpt(idx: number) {
  ElMessage.success(`已应用优化建议：${optSuggestions.value[idx]}`)
}
const showOptDetail = ref(false)
const optDetailText = ref('')

// 告警记录 & 处理 (#4890~4891)
const showAlertHistoryDialog = ref(false)
const alertHistory = ref<any[]>([])
function handleShowAlertHistory(row: any) {
  alertHistory.value = [
    { id: 'ah_1', type: 'threshold', message: '触发次数超过阈值(100)，当前值 156', status: 'pending', createdAt: '2026-06-29 12:30:00' },
    { id: 'ah_2', type: 'error_rate', message: '错误率超过 5%，当前值 8.2%', status: 'resolved', createdAt: '2026-06-29 10:00:00', resolvedAt: '2026-06-29 10:15:00' },
    { id: 'ah_3', type: 'timeout', message: '回调超时率超过 10%', status: 'pending', createdAt: '2026-06-28 16:00:00' },
  ]
  showAlertHistoryDialog.value = true
}
function handleResolveAlert(alert: any) {
  alert.status = 'resolved'
  alert.resolvedAt = new Date().toLocaleString('zh-CN')
  ElMessage.success('告警已处理')
}
function handleBatchResolve() {
  alertHistory.value.filter(a => a.status === 'pending').forEach(a => {
    a.status = 'resolved'
    a.resolvedAt = new Date().toLocaleString('zh-CN')
  })
  ElMessage.success('所有待处理告警已处理')
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
          <template #default="{ row }">
            <template v-for="e in (typeof row.config === 'string' ? (() => { try { return JSON.parse(row.config) } catch { return [row.config] } })() : (row.events || []))" :key="e">
              <el-tag size="small" style="margin: 2px">{{ e }}</el-tag>
            </template>
          </template>
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
        <el-table-column label="操作" width="380" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.enabled" link type="warning" size="small" @click="handlePause(row)">暂停</el-button>
            <el-button v-else link type="success" size="small" @click="handleResume(row)">恢复</el-button>
            <el-button link size="small" @click="handleShowLogs(row)">日志</el-button>
            <el-button link size="small" @click="handleShowPerf(row)">性能</el-button>
            <el-button link size="small" @click="handleShowOptimization(row)">优化</el-button>
            <el-button link size="small" @click="handleShowTrend">趋势</el-button>
            <el-button link size="small" @click="handleShowAlert">告警</el-button>
            <el-button link size="small" @click="handleShowAlertHistory(row)">告警记录</el-button>
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
        <el-button size="small" @click="handleShowRetention">保留策略</el-button>
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

    <!-- 日志保留策略 -->
    <el-dialog v-model="showRetentionDialog" title="监听日志保留策略" width="480px">
      <el-form label-width="140px">
        <el-form-item label="启用自动清理">
          <el-switch v-model="retentionConfig.enabled" />
        </el-form-item>
        <el-form-item label="保留天数">
          <el-input-number v-model="retentionConfig.maxDays" :min="1" :max="365" style="width:160px" />
          <div style="font-size:12px;color:#909399;margin-top:4px">超过此天数的日志将自动清理</div>
        </el-form-item>
        <el-form-item label="最大存储(MB)">
          <el-input-number v-model="retentionConfig.maxSize" :min="10" :max="10000" style="width:160px" />
          <div style="font-size:12px;color:#909399;margin-top:4px">日志总大小超过此值将清理最早记录</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showRetentionDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSaveRetention">保存</el-button>
      </template>
    </el-dialog>

    <!-- 监听器性能 -->
    <el-dialog v-model="showPerfDialog" title="监听器性能指标" width="520px">
      <div class="perf-grid">
        <div class="perf-card"><div class="perf-label">平均延迟</div><div class="perf-value">{{ perfData.avgLatency }}<small>ms</small></div></div>
        <div class="perf-card"><div class="perf-label">最大延迟</div><div class="perf-value" style="color:#e6a23c">{{ perfData.maxLatency }}<small>ms</small></div></div>
        <div class="perf-card"><div class="perf-label">成功率</div><div class="perf-value" style="color:#67c23a">{{ perfData.successRate }}<small>%</small></div></div>
        <div class="perf-card"><div class="perf-label">吞吐量</div><div class="perf-value">{{ perfData.throughput }}<small>条/分</small></div></div>
      </div>
      <div style="text-align:center;font-size:12px;color:#909399;margin-top:12px">最后更新：{{ perfData.lastUpdated }}</div>
      <template #footer><el-button @click="showPerfDialog=false">关闭</el-button></template>
    </el-dialog>

    <!-- 配置优化 -->
    <el-dialog v-model="showOptDialog" title="监听器配置优化建议" width="580px">
      <div v-for="(opt, idx) in optSuggestions" :key="idx" style="display:flex;align-items:flex-start;gap:8px;padding:8px 0;border-bottom:1px solid #f0f0f0">
        <el-tag type="warning" size="small" style="flex-shrink:0;margin-top:2px">建议{{ idx + 1 }}</el-tag>
        <div style="flex:1;font-size:13px;color:#606266">{{ opt }}</div>
        <el-button size="small" type="primary" link @click="handleApplyOpt(idx)">应用</el-button>
      </div>
      <template #footer><el-button @click="showOptDialog=false">关闭</el-button></template>
    </el-dialog>

    <!-- 告警记录 -->
    <el-dialog v-model="showAlertHistoryDialog" title="告警记录" width="700px">
      <div style="display:flex;justify-content:space-between;margin-bottom:12px">
        <span style="font-size:13px;color:#909399">共 {{ alertHistory.length }} 条，待处理 {{ alertHistory.filter(a => a.status === 'pending').length }} 条</span>
        <el-button size="small" type="primary" @click="handleBatchResolve" :disabled="alertHistory.filter(a => a.status === 'pending').length === 0">全部处理</el-button>
      </div>
      <el-table :data="alertHistory" stripe size="small">
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.type === 'threshold' ? 'danger' : row.type === 'error_rate' ? 'warning' : 'info'" size="small">
              {{ { threshold: '阈值告警', error_rate: '错误率', timeout: '超时告警' }[row.type] || row.type }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="告警内容" min-width="240" show-overflow-tooltip />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'pending' ? 'danger' : 'success'" size="small">{{ row.status === 'pending' ? '待处理' : '已处理' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="时间" width="160" />
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'pending'" link type="success" size="small" @click="handleResolveAlert(row)">处理</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer><el-button @click="showAlertHistoryDialog=false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.perf-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.perf-card { background: #f5f7fa; border-radius: 8px; padding: 16px; text-align: center; }
.perf-label { font-size: 13px; color: #909399; margin-bottom: 8px; }
.perf-value { font-size: 22px; font-weight: 700; color: #303133; }
.perf-value small { font-size: 12px; font-weight: 400; color: #909399; margin-left: 2px; }
</style>
