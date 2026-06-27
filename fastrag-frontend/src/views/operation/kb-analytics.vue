<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Top, Bottom, ArrowDown } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import * as api from '@/api'

const chartRef = ref<HTMLElement>()
const timeRange = ref('本周')
const loading = ref(true)

const metrics = ref<any[]>([])
const hotKBs = ref<any[]>([])
const hotDocs = ref<any[]>([])

async function loadAnalytics() {
  loading.value = true
  try {
    const data: any = await api.getKbAnalytics()
    if (data) {
      metrics.value = data.metrics || []
      hotKBs.value = data.hotKBs || []
      hotDocs.value = data.hotDocs || []
    }
  } catch {
    // 加载失败
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await loadAnalytics()

  if (chartRef.value) {
    const chart = echarts.init(chartRef.value)
    chart.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['文档数', '知识库数'] },
      xAxis: {
        type: 'category',
        data: ['周一', '周二', '周三', '周四', '周五', '周六', '周日'],
      },
      yAxis: { type: 'value' },
      series: [
        {
          name: '文档数',
          type: 'line',
          smooth: true,
          data: [120, 132, 101, 134, 90, 230, 210],
          itemStyle: { color: '#409eff' },
        },
        {
          name: '知识库数',
          type: 'line',
          smooth: true,
          data: [5, 6, 4, 7, 3, 8, 6],
          itemStyle: { color: '#67c23a' },
        },
      ],
    })
  }
})

// 获取指标的数值显示（区分普通数值和百分比）
function getMetricDisplay(m: any): string {
  if (m.label === '知识引用率') {
    return m.change || '0%'
  }
  return String(m.value)
}

// 导出功能
function downloadCsv(filename: string, header: string, rows: string[][]) {
  const BOM = '\uFEFF'
  const csv = BOM + header + '\n' + rows.map(r => r.join(',')).join('\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

function handleExport(command: string) {
  const ts = new Date().toISOString().slice(0, 10)
  if (command === 'metrics') {
    const rows = metrics.value.map((m: any) => [m.label, getMetricDisplay(m), m.change || '-', m.trend === 'up' ? '上升' : m.trend === 'down' ? '下降' : '-'])
    downloadCsv(`知识资产指标_${ts}.csv`, '指标,数值,变化,趋势', rows)
  } else if (command === 'hotKBs') {
    const rows = hotKBs.value.map((kb: any) => [kb.rank, kb.name, kb.docCount, kb.viewCount])
    downloadCsv(`热门知识库排行_${ts}.csv`, '排名,知识库名称,文档数,访问次数', rows)
  } else if (command === 'hotDocs') {
    const rows = hotDocs.value.map((doc: any) => [doc.rank, doc.name, doc.kbName, doc.viewCount])
    downloadCsv(`热门文档排行_${ts}.csv`, '排名,文档名称,所属知识库,访问次数', rows)
  } else if (command === 'all') {
    // 导出全部数据
    let content = '=== 知识资产指标 ===\n指标,数值,变化,趋势\n'
    metrics.value.forEach((m: any) => { content += `${m.label},${getMetricDisplay(m)},${m.change || '-'},${m.trend === 'up' ? '上升' : m.trend === 'down' ? '下降' : '-'}\n` })
    content += '\n=== 热门知识库排行 ===\n排名,知识库名称,文档数,访问次数\n'
    hotKBs.value.forEach((kb: any) => { content += `${kb.rank},${kb.name},${kb.docCount},${kb.viewCount}\n` })
    content += '\n=== 热门文档排行 ===\n排名,文档名称,所属知识库,访问次数\n'
    hotDocs.value.forEach((doc: any) => { content += `${doc.rank},${doc.name},${doc.kbName},${doc.viewCount}\n` })
    const BOM = '\uFEFF'
    const blob = new Blob([BOM + content], { type: 'text/csv;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url; a.download = `知识资产分析_全部_${ts}.csv`; a.click()
    URL.revokeObjectURL(url)
  }
  ElMessage.success('导出成功')
}
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="section-header">
      <h3>知识资产分析</h3>
      <div style="display: flex; gap: 12px; align-items: center">
        <el-radio-group v-model="timeRange" size="small">
          <el-radio-button label="今天" />
          <el-radio-button label="本周" />
          <el-radio-button label="本月" />
        </el-radio-group>
        <el-dropdown @command="handleExport">
          <el-button size="small">导出 <el-icon style="margin-left: 4px"><ArrowDown /></el-icon></el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="metrics">导出指标数据</el-dropdown-item>
              <el-dropdown-item command="hotKBs">导出热门知识库</el-dropdown-item>
              <el-dropdown-item command="hotDocs">导出热门文档</el-dropdown-item>
              <el-dropdown-item command="all" divided>导出全部数据</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

    <!-- 指标卡片 -->
    <div class="metric-cards">
      <div v-for="m in metrics" :key="m.label" class="metric-card">
        <div class="metric-label">{{ m.label }}</div>
        <div class="metric-value">{{ getMetricDisplay(m) }}</div>
        <div class="metric-change" :class="m.trend" v-if="m.change && m.label !== '知识引用率'">
          <el-icon><Top v-if="m.trend === 'up'" /><Bottom v-else /></el-icon>
          {{ m.change }}
        </div>
      </div>
    </div>

    <!-- 趋势图 -->
    <div class="card-panel">
      <div class="section-title">知识资产增长趋势</div>
      <div ref="chartRef" style="height: 300px"></div>
    </div>

    <div class="rank-grid">
      <!-- 热门知识库排行 -->
      <div class="card-panel">
        <div class="section-title">热门知识库排行</div>
        <div v-if="hotKBs.length">
          <div v-for="kb in hotKBs" :key="kb.rank" class="rank-item">
            <span class="rank" :class="{ 'top-3': kb.rank <= 3 }">{{ kb.rank }}</span>
            <span class="name">{{ kb.name }}</span>
            <span class="count">{{ kb.docCount }} 篇</span>
            <span class="views">{{ kb.viewCount }} 次</span>
          </div>
        </div>
        <el-empty v-else description="暂无数据" :image-size="60" />
      </div>

      <!-- 热门文档排行 -->
      <div class="card-panel">
        <div class="section-title">热门文档排行</div>
        <div v-if="hotDocs.length">
          <div v-for="doc in hotDocs" :key="doc.rank" class="rank-item">
            <span class="rank" :class="{ 'top-3': doc.rank <= 3 }">{{ doc.rank }}</span>
            <span class="name">{{ doc.name }}</span>
            <span class="count">{{ doc.kbName }}</span>
            <span class="views">{{ doc.viewCount }} 次</span>
          </div>
        </div>
        <el-empty v-else description="暂无数据" :image-size="60" />
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-lg;
  h3 { margin: 0; }
}

.metric-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: $spacing-base;
  margin-bottom: $spacing-base;
}

.metric-card {
  background: $bg-white;
  border-radius: $radius-base;
  padding: $spacing-lg;

  .metric-label { font-size: 13px; color: $text-secondary; margin-bottom: $spacing-sm; }
  .metric-value { font-size: 28px; font-weight: 700; margin-bottom: $spacing-xs; }
  .metric-change {
    font-size: 12px;
    display: flex;
    align-items: center;
    gap: 2px;
    &.up { color: $color-success; }
    &.down { color: $color-danger; }
  }
}

.rank-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: $spacing-base;
}

.rank-item {
  display: flex;
  align-items: center;
  gap: $spacing-base;
  padding: $spacing-sm 0;
  border-bottom: 1px solid $border-extra-light;

  .rank {
    width: 24px;
    height: 24px;
    border-radius: 50%;
    background: $border-lighter;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 12px;
    font-weight: 600;
    color: $text-secondary;
    &.top-3 { background: $color-primary; color: #fff; }
  }

  .name { flex: 1; font-size: 13px; }
  .count, .views { font-size: 12px; color: $text-secondary; }
}
</style>
