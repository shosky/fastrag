<script setup lang="ts">
import { ref, reactive, computed, watch, nextTick, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import * as api from '@/api'

const props = defineProps<{ appInfo: { id: string }; tab: string }>()
const appId = () => props.appInfo.id

// ===== 查看对话（记录监控管理） =====
const chatLoading = ref(false)
const chatRecords = ref<any[]>([])
const chatStats = ref({ total: 0, todayCount: 0, avgTurns: 0, abnormalCount: 0 })
const chatKeyword = ref('')
const chatStatus = ref('')
const showChatDrawer = ref(false)
const chatDetail = ref<any>(null)

async function loadChatRecords() {
  chatLoading.value = true
  try {
    const res: any = await api.getAppMonitorChatRecords(appId(), { keyword: chatKeyword.value || undefined, status: chatStatus.value || undefined })
    chatRecords.value = res?.list || []
    chatStats.value = { total: res?.total || 0, todayCount: res?.todayCount || 0, avgTurns: res?.avgTurns || 0, abnormalCount: res?.abnormalCount || 0 }
  } catch { chatRecords.value = [] } finally { chatLoading.value = false }
}
function handleChatQuery() { loadChatRecords() }
function handleChatReset() { chatKeyword.value = ''; chatStatus.value = ''; loadChatRecords() }
function viewChatDetail(row: any) { chatDetail.value = row; showChatDrawer.value = true }

// ===== 分析对话（数据监控管理） =====
const analysis = ref<any>(null)
const trendChartRef = ref<HTMLElement>()
const typeChartRef = ref<HTMLElement>()
let trendChart: echarts.ECharts | null = null
let typeChart: echarts.ECharts | null = null

async function loadAnalysis() {
  try { analysis.value = (await api.getAppMonitorDataAnalysis(appId())) as any } catch { analysis.value = null }
  await nextTick()
  renderAnalysisCharts()
}
function renderAnalysisCharts() {
  if (props.tab !== 'monitor-data' || !analysis.value) return
  if (trendChartRef.value) {
    trendChart?.dispose()
    trendChart = echarts.init(trendChartRef.value)
    trendChart.setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: 40, right: 20, top: 30, bottom: 30 },
      xAxis: { type: 'category', data: (analysis.value.dailyTrend || []).map((d: any) => d.date) },
      yAxis: { type: 'value' },
      series: [{ type: 'bar', data: (analysis.value.dailyTrend || []).map((d: any) => d.count), itemStyle: { color: '#409eff', borderRadius: [4, 4, 0, 0] } }],
    })
  }
  if (typeChartRef.value) {
    typeChart?.dispose()
    typeChart = echarts.init(typeChartRef.value)
    typeChart.setOption({
      tooltip: { trigger: 'item' },
      legend: { bottom: 0 },
      series: [{ type: 'pie', radius: ['40%', '65%'], data: (analysis.value.questionTypes || []).map((t: any) => ({ name: t.type, value: Number(t.percent) })) }],
    })
  }
}

// ===== 设置数据（告警监控管理） =====
const alertLoading = ref(false)
const alertConfig = reactive({
  enabled: true,
  errorRateThreshold: 5,
  latencyThreshold: 2000,
  qpsThreshold: 50,
  silentMinutes: 30,
  channels: ['email'] as string[],
  receivers: '',
})
const recentAlerts = ref<any[]>([])
async function loadAlertConfig() {
  alertLoading.value = true
  try {
    const res: any = await api.getAppMonitorAlertConfig(appId())
    Object.assign(alertConfig, {
      enabled: !!res?.enabled, errorRateThreshold: res?.errorRateThreshold ?? 5, latencyThreshold: res?.latencyThreshold ?? 2000,
      qpsThreshold: res?.qpsThreshold ?? 50, silentMinutes: res?.silentMinutes ?? 30, channels: res?.channels || ['email'], receivers: res?.receivers || '',
    })
    recentAlerts.value = res?.recentAlerts || []
  } catch {} finally { alertLoading.value = false }
}
async function handleSaveAlertConfig() {
  try { await api.saveAppMonitorAlertConfig(appId(), { ...alertConfig }); ElMessage.success('告警监控配置已保存') } catch { ElMessage.error('保存失败') }
}

// ===== 查看性能（指标监控管理） =====
const perf = ref<any>(null)
const perfChartRef = ref<HTMLElement>()
let perfChart: echarts.ECharts | null = null
async function loadPerf() {
  try { perf.value = (await api.getAppMonitorPerfMetrics(appId())) as any } catch { perf.value = null }
  await nextTick()
  renderPerfChart()
}
function renderPerfChart() {
  if (props.tab !== 'monitor-perf' || !perf.value || !perfChartRef.value) return
  perfChart?.dispose()
  perfChart = echarts.init(perfChartRef.value)
  const trend = perf.value.hourlyTrend || []
  perfChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { top: 0 },
    grid: { left: 50, right: 50, top: 36, bottom: 30 },
    xAxis: { type: 'category', data: trend.map((d: any) => d.hour) },
    yAxis: [{ type: 'value', name: '延迟(ms)' }, { type: 'value', name: 'QPS', position: 'right' }],
    series: [
      { name: '平均延迟(ms)', type: 'line', smooth: true, data: trend.map((d: any) => d.latency), itemStyle: { color: '#409eff' } },
      { name: 'QPS', type: 'bar', yAxisIndex: 1, data: trend.map((d: any) => d.qps), itemStyle: { color: 'rgba(103,194,58,0.5)' } },
    ],
  })
}

// ===== 优化性能（配置监控管理） =====
const optLoading = ref(false)
const optConfig = reactive({
  cacheEnabled: true,
  cacheTtlMinutes: 30,
  streamOutput: true,
  maxConcurrency: 20,
  timeoutSeconds: 60,
  historyRounds: 5,
})
const optSuggestions = ref<any[]>([])
async function loadOptimizeConfig() {
  optLoading.value = true
  try {
    const res: any = await api.getAppMonitorOptimizeConfig(appId())
    Object.assign(optConfig, {
      cacheEnabled: !!res?.cacheEnabled, cacheTtlMinutes: res?.cacheTtlMinutes ?? 30, streamOutput: !!res?.streamOutput,
      maxConcurrency: res?.maxConcurrency ?? 20, timeoutSeconds: res?.timeoutSeconds ?? 60, historyRounds: res?.historyRounds ?? 5,
    })
    optSuggestions.value = res?.suggestions || []
  } catch {} finally { optLoading.value = false }
}
async function handleSaveOptimizeConfig() {
  try { await api.saveAppMonitorOptimizeConfig(appId(), { ...optConfig }); ElMessage.success('性能优化配置已保存') } catch { ElMessage.error('保存失败') }
}
function applySuggestion(title: string) {
  if (title.includes('缓存')) optConfig.cacheEnabled = true
  if (title.includes('轮次')) optConfig.historyRounds = 5
  ElMessage.success(`已应用建议：${title}，请保存配置生效`)
}

// ===== 加载与图表生命周期 =====
const loaders: Record<string, () => void | Promise<void>> = {
  'monitor-chat': loadChatRecords,
  'monitor-data': loadAnalysis,
  'monitor-alert': loadAlertConfig,
  'monitor-perf': loadPerf,
  'monitor-opt': loadOptimizeConfig,
}
watch(() => props.tab, (t) => { loaders[t]?.() }, { immediate: true })

const resizeHandler = () => { trendChart?.resize(); typeChart?.resize(); perfChart?.resize() }
window.addEventListener('resize', resizeHandler)
onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeHandler)
  trendChart?.dispose(); typeChart?.dispose(); perfChart?.dispose()
})

const statusLabel: Record<string, string> = { normal: '正常', slow: '响应慢', error: '异常' }
const statusType: Record<string, string> = { normal: 'success', slow: 'warning', error: 'danger' }
</script>

<template>
  <div class="monitor-manage">
    <!-- ===== 查看对话（记录监控管理） ===== -->
    <div v-if="tab === 'monitor-chat'" class="config-section">
      <div class="section-header">
        <div><div class="section-title">查看对话 · 记录监控管理</div><p class="desc">监控应用的全部对话记录，及时发现异常会话</p></div>
      </div>
      <div class="metric-grid">
        <div class="metric-card"><div class="metric-value">{{ chatStats.todayCount }}</div><div class="metric-label">今日对话数</div></div>
        <div class="metric-card"><div class="metric-value">{{ chatStats.total }}</div><div class="metric-label">记录总数</div></div>
        <div class="metric-card"><div class="metric-value">{{ chatStats.avgTurns }}</div><div class="metric-label">平均对话轮次</div></div>
        <div class="metric-card"><div class="metric-value" :style="{ color: chatStats.abnormalCount > 0 ? '#F56C6C' : '#67C23A' }">{{ chatStats.abnormalCount }}</div><div class="metric-label">异常会话数</div></div>
      </div>
      <div class="toolbar">
        <el-input v-model="chatKeyword" placeholder="搜索对话内容..." clearable style="width:240px" size="small" @keyup.enter="handleChatQuery" />
        <el-select v-model="chatStatus" placeholder="会话状态" clearable style="width:130px" size="small">
          <el-option label="正常" value="normal" /><el-option label="响应慢" value="slow" /><el-option label="异常" value="error" />
        </el-select>
        <el-button size="small" type="primary" @click="handleChatQuery">查询</el-button>
        <el-button size="small" @click="handleChatReset">重置</el-button>
      </div>
      <el-table v-loading="chatLoading" :data="chatRecords" border stripe size="small" style="width:100%;margin-top:12px">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="sessionId" label="会话ID" width="180" show-overflow-tooltip />
        <el-table-column prop="user" label="用户" width="110" />
        <el-table-column prop="question" label="提问" min-width="200" show-overflow-tooltip />
        <el-table-column prop="answer" label="回答摘要" min-width="220" show-overflow-tooltip />
        <el-table-column prop="turns" label="轮次" width="70" align="center" />
        <el-table-column prop="tokens" label="Token" width="80" align="center" />
        <el-table-column prop="latency" label="耗时" width="80" align="center" />
        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }"><el-tag :type="(statusType[row.status] || 'info') as any" size="small">{{ statusLabel[row.status] || row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="time" label="时间" width="160" />
        <el-table-column label="操作" width="70" fixed="right">
          <template #default="{ row }"><el-button link type="primary" size="small" @click="viewChatDetail(row)">详情</el-button></template>
        </el-table-column>
      </el-table>
    </div>

    <!-- ===== 分析对话（数据监控管理） ===== -->
    <div v-if="tab === 'monitor-data'" class="config-section">
      <div class="section-header"><div><div class="section-title">分析对话 · 数据监控管理</div><p class="desc">对对话数据进行统计分析，掌握应用运行全貌</p></div></div>
      <template v-if="analysis">
        <div class="metric-grid">
          <div class="metric-card"><div class="metric-value">{{ analysis.totalConversations }}</div><div class="metric-label">累计对话数</div></div>
          <div class="metric-card"><div class="metric-value">{{ analysis.totalMessages }}</div><div class="metric-label">累计消息数</div></div>
          <div class="metric-card"><div class="metric-value">{{ analysis.resolutionRate }}%</div><div class="metric-label">问题解决率</div></div>
          <div class="metric-card"><div class="metric-value">{{ analysis.avgSatisfaction }}</div><div class="metric-label">平均满意度</div></div>
        </div>
        <div class="chart-row">
          <div class="chart-card"><div class="chart-title">近7天对话量趋势</div><div ref="trendChartRef" class="chart-box" /></div>
          <div class="chart-card"><div class="chart-title">问题类型分布</div><div ref="typeChartRef" class="chart-box" /></div>
        </div>
        <div class="panel-card" style="margin-top:16px">
          <div class="chart-title">热点问题 TOP5</div>
          <el-table :data="analysis.hotQuestions || []" size="small" stripe style="width:100%;margin-top:8px">
            <el-table-column type="index" label="#" width="50" />
            <el-table-column prop="question" label="问题" min-width="280" />
            <el-table-column prop="count" label="提问次数" width="120" align="center" />
          </el-table>
        </div>
      </template>
      <el-empty v-else description="暂无分析数据" />
    </div>

    <!-- ===== 设置数据（告警监控管理） ===== -->
    <div v-if="tab === 'monitor-alert'" class="config-section">
      <div class="section-header"><div><div class="section-title">设置数据 · 告警监控管理</div><p class="desc">配置数据告警阈值与通知方式，及时感知应用异常</p></div></div>
      <div v-loading="alertLoading" class="panel-card">
        <el-form label-width="140px" style="max-width:560px">
          <el-form-item label="启用告警"><el-switch v-model="alertConfig.enabled" /></el-form-item>
          <el-form-item label="错误率阈值(%)"><el-input-number v-model="alertConfig.errorRateThreshold" :min="1" :max="100" /></el-form-item>
          <el-form-item label="延迟阈值(ms)"><el-input-number v-model="alertConfig.latencyThreshold" :min="100" :max="60000" :step="100" /></el-form-item>
          <el-form-item label="QPS阈值"><el-input-number v-model="alertConfig.qpsThreshold" :min="1" :max="10000" /></el-form-item>
          <el-form-item label="告警静默(分钟)"><el-input-number v-model="alertConfig.silentMinutes" :min="1" :max="1440" /></el-form-item>
          <el-form-item label="通知方式">
            <el-checkbox-group v-model="alertConfig.channels">
              <el-checkbox label="email" value="email">邮件</el-checkbox>
              <el-checkbox label="sms" value="sms">短信</el-checkbox>
              <el-checkbox label="webhook" value="webhook">Webhook</el-checkbox>
            </el-checkbox-group>
          </el-form-item>
          <el-form-item label="通知接收人"><el-input v-model="alertConfig.receivers" placeholder="多个用逗号分隔" /></el-form-item>
          <el-form-item><el-button type="primary" @click="handleSaveAlertConfig">保存告警配置</el-button></el-form-item>
        </el-form>
      </div>
      <div class="panel-card" style="margin-top:16px">
        <div class="chart-title">最近告警记录</div>
        <el-table :data="recentAlerts" size="small" stripe style="width:100%;margin-top:8px">
          <el-table-column prop="name" label="告警名称" min-width="160" />
          <el-table-column prop="level" label="级别" width="100" align="center">
            <template #default="{ row }"><el-tag :type="row.level === 'critical' ? 'danger' : 'warning'" size="small">{{ row.level === 'critical' ? '严重' : '警告' }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="100" align="center">
            <template #default="{ row }"><el-tag :type="row.status === 'firing' ? 'danger' : 'success'" size="small">{{ row.status === 'firing' ? '告警中' : '已恢复' }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="value" label="触发值" width="100" align="center" />
          <el-table-column prop="time" label="时间" width="170" />
        </el-table>
      </div>
    </div>

    <!-- ===== 查看性能（指标监控管理） ===== -->
    <div v-if="tab === 'monitor-perf'" class="config-section">
      <div class="section-header"><div><div class="section-title">查看性能 · 指标监控管理</div><p class="desc">实时查看应用响应性能与吞吐指标</p></div></div>
      <template v-if="perf">
        <div class="metric-grid">
          <div class="metric-card"><div class="metric-value">{{ perf.avgLatencyMs }}<span class="metric-unit">ms</span></div><div class="metric-label">平均响应时间</div></div>
          <div class="metric-card"><div class="metric-value">{{ perf.p95LatencyMs }}<span class="metric-unit">ms</span></div><div class="metric-label">P95 延迟</div></div>
          <div class="metric-card"><div class="metric-value" :style="{ color: perf.errorRate > 3 ? '#F56C6C' : '#67C23A' }">{{ perf.errorRate }}%</div><div class="metric-label">错误率</div></div>
          <div class="metric-card"><div class="metric-value">{{ perf.qps }}</div><div class="metric-label">当前 QPS</div></div>
          <div class="metric-card"><div class="metric-value">{{ perf.concurrency }}</div><div class="metric-label">并发数</div></div>
          <div class="metric-card"><div class="metric-value">{{ perf.tokenSpeed }}<span class="metric-unit">tokens/s</span></div><div class="metric-label">生成速度</div></div>
        </div>
        <div class="panel-card" style="margin-top:16px">
          <div class="chart-title">近24小时性能趋势</div>
          <div ref="perfChartRef" style="width:100%;height:300px" />
        </div>
        <div class="panel-card" style="margin-top:16px">
          <div class="chart-title">模型维度指标</div>
          <el-table :data="perf.modelMetrics || []" size="small" stripe style="width:100%;margin-top:8px">
            <el-table-column prop="name" label="模型" min-width="140" />
            <el-table-column prop="callCount" label="调用次数" width="120" align="center" />
            <el-table-column prop="avgLatency" label="平均耗时" width="120" align="center" />
            <el-table-column prop="successRate" label="成功率" width="120" align="center" />
          </el-table>
        </div>
      </template>
      <el-empty v-else description="暂无性能数据" />
    </div>

    <!-- ===== 优化性能（配置监控管理） ===== -->
    <div v-if="tab === 'monitor-opt'" class="config-section">
      <div class="section-header"><div><div class="section-title">优化性能 · 配置监控管理</div><p class="desc">调整运行参数优化应用性能表现</p></div></div>
      <div v-loading="optLoading" class="panel-card">
        <el-form label-width="160px" style="max-width:600px">
          <el-form-item label="语义缓存">
            <el-switch v-model="optConfig.cacheEnabled" />
            <span class="form-tip">开启后相同/相似问题直接返回缓存结果</span>
          </el-form-item>
          <el-form-item v-if="optConfig.cacheEnabled" label="缓存有效期(分钟)">
            <el-input-number v-model="optConfig.cacheTtlMinutes" :min="1" :max="1440" />
          </el-form-item>
          <el-form-item label="流式输出">
            <el-switch v-model="optConfig.streamOutput" />
            <span class="form-tip">逐字输出回答，降低用户等待感知</span>
          </el-form-item>
          <el-form-item label="最大并发数"><el-input-number v-model="optConfig.maxConcurrency" :min="1" :max="500" /></el-form-item>
          <el-form-item label="请求超时(秒)"><el-input-number v-model="optConfig.timeoutSeconds" :min="5" :max="600" /></el-form-item>
          <el-form-item label="上下文记忆轮次"><el-input-number v-model="optConfig.historyRounds" :min="1" :max="20" /></el-form-item>
          <el-form-item><el-button type="primary" @click="handleSaveOptimizeConfig">保存优化配置</el-button></el-form-item>
        </el-form>
      </div>
      <div class="panel-card" style="margin-top:16px">
        <div class="chart-title">性能优化建议</div>
        <div v-for="(s, i) in optSuggestions" :key="i" class="suggestion-item">
          <div class="suggestion-info"><div class="suggestion-title">{{ s.title }}</div><div class="suggestion-desc">{{ s.desc }}</div></div>
          <el-button size="small" type="primary" link @click="applySuggestion(s.title)">应用</el-button>
        </div>
      </div>
    </div>

    <!-- 对话详情抽屉 -->
    <el-drawer v-model="showChatDrawer" title="对话记录详情" size="480px">
      <div v-if="chatDetail">
        <div class="detail-meta">会话ID：{{ chatDetail.sessionId }} | 用户：{{ chatDetail.user }} | 时间：{{ chatDetail.time }}</div>
        <div class="detail-msg"><el-tag size="small" type="info" round>用户</el-tag><div class="detail-content">{{ chatDetail.question }}</div></div>
        <div class="detail-msg"><el-tag size="small" type="primary" round>AI</el-tag><div class="detail-content">{{ chatDetail.answer }}</div></div>
        <div class="detail-meta">轮次：{{ chatDetail.turns }} | Token：{{ chatDetail.tokens }} | 耗时：{{ chatDetail.latency }} | 状态：{{ statusLabel[chatDetail.status] }}</div>
      </div>
    </el-drawer>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.config-section { padding: 4px 0; }
.section-header { margin-bottom: $spacing-base; }
.section-title { font-size: 16px; font-weight: 600; }
.desc { font-size: 13px; color: $text-secondary; margin: 4px 0 0; }
.metric-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-bottom: 4px; }
.metric-card { background: var(--el-bg-color-overlay); border: 1px solid $border-lighter; border-radius: $radius-base; padding: 16px; text-align: center; }
.metric-value { font-size: 24px; font-weight: 700; color: $text-primary; }
.metric-unit { font-size: 12px; font-weight: 400; color: $text-secondary; margin-left: 2px; }
.metric-label { font-size: 13px; color: $text-secondary; margin-top: 4px; }
.toolbar { display: flex; gap: 10px; align-items: center; margin-top: 12px; flex-wrap: wrap; }
.panel-card { background: var(--el-bg-color-overlay); border: 1px solid $border-lighter; border-radius: $radius-base; padding: 16px; }
.chart-row { display: grid; grid-template-columns: 1.4fr 1fr; gap: 16px; margin-top: 16px; }
.chart-card { background: var(--el-bg-color-overlay); border: 1px solid $border-lighter; border-radius: $radius-base; padding: 16px; }
.chart-title { font-size: 14px; font-weight: 600; margin-bottom: 8px; }
.chart-box { width: 100%; height: 260px; }
.form-tip { font-size: 12px; color: $text-secondary; margin-left: 10px; }
.suggestion-item { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 10px 0; border-bottom: 1px dashed $border-lighter;
  &:last-child { border-bottom: none; } }
.suggestion-title { font-size: 14px; font-weight: 500; }
.suggestion-desc { font-size: 12px; color: $text-secondary; margin-top: 2px; }
.detail-meta { font-size: 12px; color: $text-secondary; margin: 12px 0; }
.detail-msg { margin-bottom: 14px; .detail-content { margin-top: 6px; padding: 10px 12px; background: $bg-page; border-radius: 6px; font-size: 13px; line-height: 1.6; } }
</style>
