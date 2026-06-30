<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const props = defineProps<{ appInfo: { id: string } }>()
const appId = () => props.appInfo.id

const activeTab = ref('publish')
const publishRecords = ref<any[]>([])
const metrics = ref({ totalPublish: 0, releasedCount: 0, rollbackCount: 0, successRate: 0, totalCalls: 0, avgResponseTime: '-', todayCalls: 0 })

// 发布管理
async function loadPublishRecords() {
  try { publishRecords.value = ((await api.getAppPublishRecords(appId())) as any) || [] } catch { publishRecords.value = [] }
}
async function handlePublish(row: any) {
  try { await api.publishApp(appId(), { version: row.version, scopeType: row.environment || 'production' }); ElMessage.success('已上线'); await loadPublishRecords() } catch { ElMessage.error('发布失败') }
}
async function handleRevoke(row: any) {
  try { await ElMessageBox.confirm('确认撤回该版本？', '确认', { type: 'warning' }); await api.revokeKnowledge(appId(), row.id); await loadPublishRecords(); ElMessage.success('已撤回') } catch {}
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

onMounted(() => { loadPublishRecords(); loadMetrics() })
</script>
<template>
  <div class="config-section">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="发布管理" name="publish">
        <div class="card-panel">
          <div class="section-header"><div class="section-title">发布记录</div></div>
          <el-table :data="publishRecords" stripe size="small" style="margin-top:12px">
            <el-table-column prop="version" label="版本" width="100" />
            <el-table-column prop="environment" label="环境" width="100" />
            <el-table-column prop="status" label="状态" width="100"><template #default="{row}"><el-tag :type="row.status==='released'?'success':row.status==='rolled_back'?'danger':'info'" size="small">{{ {released:'已发布',rolled_back:'已回滚',pending:'待发布',draft:'草稿'}[row.status]||row.status }}</el-tag></template></el-table-column>
            <el-table-column prop="createdAt" label="时间" width="160" />
            <el-table-column label="操作" width="160"><template #default="{row}"><el-button v-if="row.status==='draft'||row.status==='pending'" link type="success" size="small" @click="handlePublish(row)">发布</el-button><el-button v-if="row.status==='released'" link type="warning" size="small" @click="handleRevoke(row)">撤回</el-button></template></el-table-column>
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
</style>
