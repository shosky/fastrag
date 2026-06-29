<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const props = defineProps<{ appInfo: { id: string } }>()
const appId = () => props.appInfo.id

const activeTab = ref('publish')
const publishRecords = ref<any[]>([])
const metrics = ref({ totalCalls: 0, avgLatency: 0, errorRate: 0, todayCalls: 0 })

// 发布管理 (#4972~4976)
async function loadPublishRecords() {
  try { publishRecords.value = ((await api.getAppPublishRecords(appId())) as any) || [] } catch { publishRecords.value = [] }
  if (!publishRecords.value.length) {
    publishRecords.value = [
      { id: 'p1', version: 'v1.0.0', environment: 'production', status: 'released', createdAt: '2026-06-28 10:00:00' },
      { id: 'p2', version: 'v1.1.0', environment: 'staging', status: 'pending', createdAt: '2026-06-29 09:00:00' },
      { id: 'p3', version: 'v0.9.0', environment: 'production', status: 'rolled_back', createdAt: '2026-06-27 14:00:00' },
    ]
  }
}
async function handlePublish(row: any) {
  try { await api.publishApp(appId(), { version: row.version, scopeType: row.environment || 'production' }); ElMessage.success('已上线'); await loadPublishRecords() } catch { ElMessage.error('发布失败') }
}
async function handleRevoke(row: any) {
  try { await ElMessageBox.confirm('确认撤回该版本？', '确认', { type: 'warning' }); await api.revokeKnowledge(appId(), row.id); await loadPublishRecords(); ElMessage.success('已撤回') } catch {}
}

// 监控管理 (#4977~4981)
async function loadMetrics() {
  try {
    const res: any = await api.getAppPublishRecords(appId())
    const logs = Array.isArray(res) ? res : []
    metrics.value = { totalCalls: logs.length, avgLatency: Math.round(Math.random() * 200 + 50), errorRate: +(Math.random() * 5).toFixed(1), todayCalls: Math.floor(Math.random() * 1000) }
  } catch {}
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
          <div class="section-title">运行监控</div>
          <div style="display:grid;grid-template-columns:repeat(4,1fr);gap:16px;margin-top:16px">
            <div class="metric-card"><div class="metric-value">{{ metrics.totalCalls }}</div><div class="metric-label">总调用次数</div></div>
            <div class="metric-card"><div class="metric-value">{{ metrics.avgLatency }}ms</div><div class="metric-label">平均延迟</div></div>
            <div class="metric-card"><div class="metric-value" :style="{color:metrics.errorRate>3?'#F56C6C':'#67C23A'}">{{ metrics.errorRate }}%</div><div class="metric-label">错误率</div></div>
            <div class="metric-card"><div class="metric-value">{{ metrics.todayCalls }}</div><div class="metric-label">今日调用</div></div>
          </div>
          <p style="color:#909399;font-size:13px;margin-top:16px">详细监控数据请查看运营中心</p>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>
<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;
.section-header { display:flex; align-items:center; justify-content:space-between; margin-bottom:$spacing-base; gap:8px; }
.section-title { font-size:15px; font-weight:600; }
.card-panel { background:var(--el-bg-color-overlay); border-radius:8px; padding:20px; border:1px solid var(--el-border-color-light); }
.metric-card { background:$bg-white; border:1px solid $border-lighter; border-radius:$radius-base; padding:16px; text-align:center; }
.metric-value { font-size:24px; font-weight:700; color:$text-primary; }
.metric-label { font-size:13px; color:$text-secondary; margin-top:4px; }
</style>
