<script setup lang="ts">
/**
 * 质量趋势分析（知识审核管理-知识审核流程-查看知识质量趋势）
 * 独立分析页：汇总指标 + 近6月「审核数/通过率/发布数」组合趋势图(echarts) + 月度明细表
 * 数据源（真实接口）：GET /kb/{kbId}/quality-trend?months=6（按月聚合审核任务与发布记录）
 */
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'
import * as api from '@/api'

const kbList = ref<any[]>([])
const kbId = ref('')
const loading = ref(false)
const trend = ref<any[]>([])
const chartRef = ref<HTMLElement>()
let chart: echarts.ECharts | null = null

const summary = computed(() => {
  const total = trend.value.reduce((s: number, p: any) => s + (p.reviewTotal || 0), 0)
  const published = trend.value.reduce((s: number, p: any) => s + (p.published || 0), 0)
  const rated = trend.value.filter((p: any) => (p.reviewTotal || 0) > 0)
  const avgRate = rated.length ? Math.round(rated.reduce((s: number, p: any) => s + (p.passRate || 0), 0) / rated.length) : 0
  const best = trend.value.filter((p: any) => (p.reviewTotal || 0) > 0).sort((a: any, b: any) => (b.passRate || 0) - (a.passRate || 0))[0]
  return { total, published, avgRate, bestMonth: best?.month || '-', bestRate: best?.passRate ?? 0 }
})

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
    const res: any = await api.getQualityTrend(kbId.value, 6)
    trend.value = Array.isArray(res) ? res : []
    renderChart()
  } finally { loading.value = false }
}

function renderChart() {
  if (!chartRef.value) return
  chart = chart || echarts.init(chartRef.value)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['审核数', '发布数', '通过率(%)'], top: 0 },
    grid: { left: 48, right: 52, top: 36, bottom: 28 },
    xAxis: { type: 'category', data: trend.value.map((p: any) => p.month) },
    yAxis: [
      { type: 'value', name: '次数', minInterval: 1 },
      { type: 'value', name: '通过率', max: 100, axisLabel: { formatter: '{value}%' } },
    ],
    series: [
      { name: '审核数', type: 'bar', barWidth: 22, itemStyle: { color: '#409eff' }, data: trend.value.map((p: any) => p.reviewTotal || 0) },
      { name: '发布数', type: 'bar', barWidth: 22, itemStyle: { color: '#67c23a' }, data: trend.value.map((p: any) => p.published || 0) },
      { name: '通过率(%)', type: 'line', yAxisIndex: 1, smooth: true, itemStyle: { color: '#e6a23c' }, areaStyle: { opacity: 0.12 }, data: trend.value.map((p: any) => p.passRate || 0) },
    ],
  })
  chart.resize()
}
function handleResize() { chart?.resize() }

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
      <span class="section-title">知识质量趋势分析</span>
      <el-select v-model="kbId" filterable placeholder="选择知识库" style="width:240px" @change="loadData">
        <el-option v-for="kb in kbList" :key="kb.id" :label="kb.name" :value="kb.id" />
      </el-select>
      <span style="font-size:12px;color:#909399">近 6 个月审核任务与发布记录按月真实聚合</span>
    </div>

    <!-- 汇总指标 -->
    <div class="metric-cards">
      <div class="metric-card"><div class="metric-label">近6月审核总数</div><div class="metric-value">{{ summary.total }}</div></div>
      <div class="metric-card"><div class="metric-label">平均通过率</div><div class="metric-value">{{ summary.avgRate }}%</div></div>
      <div class="metric-card"><div class="metric-label">近6月发布总数</div><div class="metric-value">{{ summary.published }}</div></div>
      <div class="metric-card"><div class="metric-label">最佳月份</div><div class="metric-value" style="font-size:18px">{{ summary.bestMonth }}（{{ summary.bestRate }}%）</div></div>
    </div>

    <!-- 趋势图 -->
    <div class="card-panel" style="margin:16px 0">
      <div class="section-title" style="margin-bottom:8px">审核 / 发布 / 通过率趋势</div>
      <div ref="chartRef" style="width:100%;height:340px" />
      <el-empty v-if="!trend.length" description="暂无趋势数据（需有审核任务记录）" :image-size="60" style="margin-top:-300px;pointer-events:none" />
    </div>

    <!-- 月度明细 -->
    <div class="card-panel">
      <div class="section-title" style="margin-bottom:10px">月度明细</div>
      <el-table :data="trend" stripe size="small">
        <el-table-column prop="month" label="月份" width="120" />
        <el-table-column prop="reviewTotal" label="审核任务数" width="120" align="center" />
        <el-table-column label="通过率" min-width="220">
          <template #default="{ row }">
            <el-progress :percentage="Number(row.passRate) || 0" :stroke-width="10" />
          </template>
        </el-table-column>
        <el-table-column prop="published" label="发布数" width="100" align="center" />
      </el-table>
      <el-empty v-if="!trend.length" description="暂无数据" :image-size="60" />
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
