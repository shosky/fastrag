<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const props = defineProps<{ appInfo: { id: string } }>()
const appId = () => props.appInfo.id

const activeTab = ref('publish')
const publishRecords = ref<any[]>([])
const publishStatus = ref<any>(null)
const metrics = ref({ totalPublish: 0, releasedCount: 0, rollbackCount: 0, successRate: 0, totalCalls: 0, avgResponseTime: '-', todayCalls: 0 })

const scopeTypeOptions = [
  { value: 'all', label: '全部用户' },
  { value: 'department', label: '指定部门' },
  { value: 'user', label: '指定用户' },
]
const scopeTypeText: Record<string, string> = { all: '全部用户', department: '指定部门', user: '指定用户', production: '全部用户' }
function parseScope(v: any): string[] {
  if (!v) return []
  try { const p = typeof v === 'string' ? JSON.parse(v) : v; return Array.isArray(p) ? p.map(String) : [String(p)] } catch { return [String(v)] }
}
function scopeText(row: any) {
  const t = scopeTypeText[row.scopeType] || row.scopeType || '-'
  const vs = parseScope(row.scopeValue)
  return vs.length ? `${t}（${vs.join('、')}）` : t
}

// ===== 发布管理 =====
async function loadPublishRecords() {
  try { publishRecords.value = ((await api.getAppPublishRecords(appId())) as any) || [] } catch { publishRecords.value = [] }
}
// 查看发布状态
async function loadPublishStatus() {
  try { publishStatus.value = ((await api.getAppPublishStatus(appId())) as any) || null } catch { publishStatus.value = null }
}
async function reload() { await Promise.all([loadPublishRecords(), loadPublishStatus(), loadMetrics()]) }

// 保存配置：把当前应用配置快照 + 发布范围存为待发布草稿
const savingConfig = ref(false)
async function handleSaveConfig() {
  savingConfig.value = true
  try {
    await api.saveAppPublishConfig(appId(), { scopeType: scopeForm.value.scopeType, scopeValue: scopeForm.value.scopeValue })
    ElMessage.success('发布配置已保存'); await reload()
  } catch { ElMessage.error('保存失败') } finally { savingConfig.value = false }
}

// 上线机器人 / 更新发布：先选择发布范围
const scopeDialog = ref(false)
const publishAction = ref<'online' | 'update'>('online')
const scopeForm = ref<{ scopeType: string; scopeValue: string[] }>({ scopeType: 'all', scopeValue: [] })
function openPublishDialog(action: 'online' | 'update') {
  publishAction.value = action
  const src = action === 'update' ? publishStatus.value?.online : publishStatus.value?.draft
  scopeForm.value = { scopeType: src?.scopeType || 'all', scopeValue: parseScope(src?.scopeValue) }
  scopeDialog.value = true
}
async function confirmPublish() {
  try {
    const payload = { scopeType: scopeForm.value.scopeType, scopeValue: scopeForm.value.scopeValue }
    if (publishAction.value === 'online') {
      const draftId = publishStatus.value?.draft?.id
      await api.publishApp(appId(), draftId ? { ...payload, id: draftId } : payload)
      ElMessage.success('机器人已上线')
    } else {
      await api.republishApp(appId(), payload)
      ElMessage.success('已更新发布')
    }
    scopeDialog.value = false
    await reload()
  } catch { ElMessage.error('发布失败') }
}
// 记录行内：草稿直接上线
async function handlePublish(row: any) {
  try { await api.publishApp(appId(), { id: row.id, version: row.version, scopeType: row.scopeType || 'all', scopeValue: row.scopeValue }); ElMessage.success('已上线'); await reload() } catch { ElMessage.error('发布失败') }
}
async function handleRevoke(row: any) {
  try {
    await ElMessageBox.confirm(`确认撤回版本 v${row.version}？撤回后机器人将下线。`, '确认', { type: 'warning' })
    await api.revokeAppPublish(appId(), row.id); await reload(); ElMessage.success('已撤回')
  } catch {}
}

// 监控管理 — 从 getAppMonitor 获取真实数据
async function loadMetrics() {
  try {
    const res: any = await api.getAppMonitor(appId())
    if (res) {
      metrics.value = {
        totalPublish: res.totalPublish ?? 0,
        releasedCount: res.releasedCount ?? 0,
        rollbackCount: res.rollbackCount ?? 0,
        successRate: res.successRate ?? 0,
        totalCalls: res.totalCalls ?? 0,
        avgResponseTime: res.avgResponseTime ?? '-',
        todayCalls: res.todayCalls ?? 0,
      }
    }
  } catch {}
}

// 告警设置
const showAlertDialog = ref(false)
const alertConfig = ref({
  errorRateThreshold: 5,
  avgLatencyThreshold: 2000,
  notifyChannels: ['email'],
  enabled: true,
})
function openAlertConfig() {
  showAlertDialog.value = true
}
function handleSaveAlert() {
  showAlertDialog.value = false
  ElMessage.success('告警配置已保存')
}

onMounted(() => { reload() })
</script>
<template>
  <div class="config-section">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="发布管理" name="publish">
        <div class="card-panel">
          <!-- 发布状态 -->
          <div class="section-header"><div class="section-title">发布状态</div></div>
          <div class="status-bar">
            <div class="status-item">
              <span class="status-label">当前状态</span>
              <el-tag :type="publishStatus?.status === 'released' ? 'success' : 'info'" size="small">{{ publishStatus?.status === 'released' ? '已上线' : '未发布' }}</el-tag>
            </div>
            <div class="status-item"><span class="status-label">线上版本</span><span class="status-value">v{{ publishStatus?.onlineVersion ?? '-' }}</span></div>
            <div class="status-item">
              <span class="status-label">待发布配置</span>
              <el-tag :type="publishStatus?.hasPendingConfig ? 'warning' : 'info'" size="small">{{ publishStatus?.hasPendingConfig ? '有' : '无' }}</el-tag>
            </div>
            <div class="status-item"><span class="status-label">累计版本</span><span class="status-value">{{ publishStatus?.totalVersions ?? 0 }}</span></div>
            <div class="status-actions">
              <el-button size="small" :loading="savingConfig" @click="handleSaveConfig">保存配置</el-button>
              <el-button size="small" type="primary" @click="openPublishDialog('online')">上线机器人</el-button>
              <el-button size="small" type="success" :disabled="!publishStatus?.online && !publishStatus?.draft" @click="openPublishDialog('update')">更新发布</el-button>
            </div>
          </div>

          <!-- 发布记录 -->
          <div class="section-header" style="margin-top:16px"><div class="section-title">发布记录</div></div>
          <el-table :data="publishRecords" stripe size="small" style="margin-top:12px">
            <el-table-column prop="version" label="版本" width="80"><template #default="{row}">v{{ row.version }}</template></el-table-column>
            <el-table-column label="发布范围" min-width="160"><template #default="{row}">{{ scopeText(row) }}</template></el-table-column>
            <el-table-column prop="status" label="状态" width="100"><template #default="{row}"><el-tag :type="row.status==='released'?'success':row.status==='rolled_back'?'danger':'info'" size="small">{{ ({released:'已发布',rolled_back:'已回滚',pending:'待发布',draft:'草稿'} as Record<string,string>)[row.status]||row.status }}</el-tag></template></el-table-column>
            <el-table-column prop="operator" label="操作人" width="100" />
            <el-table-column prop="publishedAt" label="发布时间" width="160"><template #default="{row}">{{ row.publishedAt || row.createdAt || '-' }}</template></el-table-column>
            <el-table-column label="操作" width="140"><template #default="{row}"><el-button v-if="row.status==='draft'||row.status==='pending'" link type="success" size="small" @click="handlePublish(row)">发布</el-button><el-button v-if="row.status==='released'" link type="warning" size="small" @click="handleRevoke(row)">撤回</el-button></template></el-table-column>
          </el-table>
          <el-empty v-if="!publishRecords.length" description="暂无发布记录" :image-size="60" />
        </div>
      </el-tab-pane>
      <el-tab-pane label="监控管理" name="monitor">
        <div class="card-panel">
          <div class="section-header">
            <div class="section-title">运行监控</div>
            <el-button size="small" @click="openAlertConfig">告警设置</el-button>
          </div>
          <div style="display:grid;grid-template-columns:repeat(4,1fr);gap:16px;margin-top:16px">
            <div class="metric-card"><div class="metric-value">{{ metrics.successRate }}%</div><div class="metric-label">发布成功率</div></div>
            <div class="metric-card"><div class="metric-value">{{ metrics.releasedCount }}</div><div class="metric-label">成功发布</div></div>
            <div class="metric-card"><div class="metric-value" :style="{color:metrics.rollbackCount>3?'#F56C6C':'#67C23A'}">{{ metrics.rollbackCount }}</div><div class="metric-label">回滚次数</div></div>
            <div class="metric-card"><div class="metric-value">{{ metrics.totalCalls }}</div><div class="metric-label">总调用次数</div></div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 选择发布范围弹窗 -->
    <el-dialog v-model="scopeDialog" :title="publishAction==='online' ? '上线机器人' : '更新发布'" width="480px">
      <el-form label-width="100px">
        <el-form-item label="发布范围">
          <el-radio-group v-model="scopeForm.scopeType">
            <el-radio v-for="o in scopeTypeOptions" :key="o.value" :value="o.value">{{ o.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="scopeForm.scopeType !== 'all'" label="范围对象">
          <el-select v-model="scopeForm.scopeValue" multiple filterable allow-create default-first-option placeholder="输入部门/用户后回车添加" style="width:100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="scopeDialog=false">取消</el-button>
        <el-button type="primary" @click="confirmPublish">{{ publishAction==='online' ? '确认上线' : '确认更新' }}</el-button>
      </template>
    </el-dialog>

    <!-- 告警设置弹窗 -->
    <el-dialog v-model="showAlertDialog" title="数据告警设置" width="480px">
      <el-form label-width="120px">
        <el-form-item label="启用告警">
          <el-switch v-model="alertConfig.enabled" />
        </el-form-item>
        <el-form-item label="错误率阈值(%)">
          <el-input-number v-model="alertConfig.errorRateThreshold" :min="1" :max="100" />
        </el-form-item>
        <el-form-item label="延迟阈值(ms)">
          <el-input-number v-model="alertConfig.avgLatencyThreshold" :min="100" :max="10000" :step="100" />
        </el-form-item>
        <el-form-item label="通知方式">
          <el-checkbox-group v-model="alertConfig.notifyChannels">
            <el-checkbox label="email">邮件</el-checkbox>
            <el-checkbox label="sms">短信</el-checkbox>
            <el-checkbox label="webhook">Webhook</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAlertDialog=false">取消</el-button>
        <el-button type="primary" @click="handleSaveAlert">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;
.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom:$spacing-base; gap:8px; }
.section-title { font-size:15px; font-weight:600; }
.card-panel { background:var(--el-bg-color-overlay); border-radius:8px; padding:20px; border:1px solid var(--el-border-color-light); }
.metric-card { background:$bg-white; border:1px solid $border-lighter; border-radius:$radius-base; padding:16px; text-align:center; }
.metric-value { font-size:24px; font-weight:700; color:$text-primary; }
.metric-label { font-size:13px; color:$text-secondary; margin-top:4px; }
.status-bar { display:flex; align-items:center; flex-wrap:wrap; gap:24px; background:$bg-white; border:1px solid $border-lighter; border-radius:$radius-base; padding:14px 20px; }
.status-item { display:flex; align-items:center; gap:8px; }
.status-label { font-size:13px; color:$text-secondary; }
.status-value { font-size:14px; font-weight:600; color:$text-primary; }
.status-actions { margin-left:auto; display:flex; gap:8px; }
</style>
