<script setup lang="ts">
/**
 * 发布策略效果分析（知识审核管理-知识发布管理-查看发布策略执行效果）
 * 独立分析页：效果指标卡 + 月度「发布/审核/通过率」组合趋势图(echarts) + 发布结果分布 + 最近发布记录
 * 数据源（全部真实接口）：GET /kb/{kbId}/strategy-effect、/quality-trend、/publish/history
 */
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'
import * as api from '@/api'

const kbList = ref<any[]>([])
const kbId = ref('')
const loading = ref(false)
const effect = ref<any>({ totalPublish: 0, successCount: 0, revokeCount: 0, avgReviewHours: '-' })
const trend = ref<any[]>([])
const history = ref<any[]>([])
const chartRef = ref<HTMLElement>()
let chart: echarts.ECharts | null = null

const successRate = computed(() => {
  const total = effect.value.totalPublish || 0
  return total > 0 ? Math.round((effect.value.successCount || 0) / total * 1000) / 10 : 0
})
const dist = computed(() => ({
  published: history.value.filter((h: any) => h.status === 'published').length,
  pending: history.value.filter((h: any) => h.status === 'pending').length,
  revoked: history.value.filter((h: any) => h.status === 'revoked').length,
}))

async function loadKbs() {
  try {
    const res: any = await api.getKnowledgeBases()
    kbList.value = Array.isArray(res) ? res : res?.list || []
    if (!kbId.value) kbId.value = kbList.value[0]?.id || ''
  } catch { kbList.value = [] }
}

async function loadData() {
  if (!kbId.value) return
  loading.value = true
  try {
    const [eff, tr, his] = await Promise.all([
      api.getPublishStrategyEffect(kbId.value).catch(() => null),
      api.getQualityTrend(kbId.value, 6).catch(() => []),
      api.getPublishHistory(kbId.value).catch(() => []),
    ])
    effect.value = eff || { totalPublish: 0, successCount: 0, revokeCount: 0, avgReviewHours: '-' }
    trend.value = Array.isArray(tr) ? tr : []
    history.value = Array.isArray(his) ? his : his?.list || []
    renderChart()
  } finally { loading.value = false }
}

function renderChart() {
  if (!chartRef.value) return
  chart = chart || echarts.init(chartRef.value)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['发布数', '审核数', '通过率(%)'], top: 0 },
    grid: { left: 48, right: 52, top: 36, bottom: 28 },
    xAxis: { type: 'category', data: trend.value.map((p: any) => p.month) },
    yAxis: [
      { type: 'value', name: '次数', minInterval: 1 },
      { type: 'value', name: '通过率', max: 100, axisLabel: { formatter: '{value}%' } },
    ],
    series: [
      { name: '发布数', type: 'bar', barWidth: 20, itemStyle: { color: '#409eff' }, data: trend.value.map((p: any) => p.published || 0) },
      { name: '审核数', type: 'bar', barWidth: 20, itemStyle: { color: '#67c23a' }, data: trend.value.map((p: any) => p.reviewTotal || 0) },
      { name: '通过率(%)', type: 'line', yAxisIndex: 1, smooth: true, itemStyle: { color: '#e6a23c' }, data: trend.value.map((p: any) => p.passRate || 0) },
    ],
  })
  chart.resize()
}
function handleResize() { chart?.resize() }

function fmtTime(t: any) { return t ? String(t).replace('T', ' ').slice(0, 16) : '-' }
function statusTag(s: any): any { return s === 'published' ? 'success' : s === 'revoked' ? 'danger' : s === 'pending' ? 'warning' : 'info' }
function statusText(s: any) { return s === 'published' ? '已发布' : s === 'revoked' ? '已撤回' : s === 'pending' ? '待发布' : (s || '-') }

onMounted(async () => {
  await loadKbs()
  await loadData()
  window.addEventListener('resize', handleResize)
})
onBeforeUnmount(() => { window.removeEventListener('resize', handleResize); chart?.dispose(); chart = null })
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="card-panel" style="margin-bottom:16px;display:flex;align-items:center;gap:12px;flex-wrap:wrap">
      <span class="section-title">发布策略效果分析</span>
      <el-select v-model="kbId" filterable placeholder="选择知识库" style="width:240px" @change="loadData">
        <el-option v-for="kb in kbList" :key="kb.id" :label="kb.name" :value="kb.id" />
      </el-select>
      <span style="font-size:12px;color:#909399">近 6 个月发布/审核真实聚合</span>
    </div>

    <!-- 效果指标卡 -->
    <div class="metric-cards">
      <div class="metric-card"><div class="metric-label">累计发布</div><div class="metric-value">{{ effect.totalPublish ?? 0 }}</div></div>
      <div class="metric-card"><div class="metric-label">发布成功率</div><div class="metric-value">{{ successRate }}%</div></div>
      <div class="metric-card"><div class="metric-label">累计撤回</div><div class="metric-value">{{ effect.revokeCount ?? 0 }}</div></div>
      <div class="metric-card"><div class="metric-label">平均审核耗时(h)</div><div class="metric-value">{{ effect.avgReviewHours ?? '-' }}</div></div>
    </div>

    <!-- 月度趋势图 -->
    <div class="card-panel" style="margin:16px 0">
      <div class="section-title" style="margin-bottom:8px">月度发布 / 审核趋势</div>
      <div ref="chartRef" style="width:100%;height:320px" />
      <el-empty v-if="!trend.length" description="暂无趋势数据（需有审核任务或发布记录）" :image-size="60" style="margin-top:-280px;pointer-events:none" />
    </div>

    <!-- 发布结果分布 + 最近发布记录 -->
    <div class="card-panel">
      <div class="section-title" style="margin-bottom:10px">发布结果分布与明细</div>
      <div style="display:flex;gap:12px;margin-bottom:12px">
        <el-tag type="success">已发布 {{ dist.published }}</el-tag>
        <el-tag type="warning">待发布 {{ dist.pending }}</el-tag>
        <el-tag type="danger">已撤回 {{ dist.revoked }}</el-tag>
      </div>
      <el-table :data="history" stripe size="small">
        <el-table-column prop="knowledgeId" label="知识ID" width="180" show-overflow-tooltip />
        <el-table-column prop="onlineVersion" label="上线版本" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.onlineVersion || '-' }}</template>
        </el-table-column>
        <el-table-column label="类型" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.publishType === 'publish' ? 'primary' : 'warning'" size="small" effect="plain">{{ row.publishType === 'publish' ? '发布' : '撤回' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }"><el-tag :type="statusTag(row.status)" size="small">{{ statusText(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="发布时间" width="160">
          <template #default="{ row }">{{ fmtTime(row.publishedAt || row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="operator" label="操作人" width="100" />
      </el-table>
      <el-empty v-if="!history.length" description="暂无发布记录" :image-size="60" />
    </div>
  </div>
</template>

<style lang="scss" scoped>
.section-title { font-size: 15px; font-weight: 600; }
.metric-cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 12px; }
.metric-card { background: #fff; border: 1px solid #ebeef5; border-radius: 6px; padding: 16px; text-align: center; }
.metric-label { font-size: 13px; color: #909399; }
.metric-value { font-size: 24px; font-weight: 700; margin-top: 6px; }
</style>
