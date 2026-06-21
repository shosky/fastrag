<script setup lang="ts">
import { ref, onMounted } from 'vue'
import * as echarts from 'echarts'

const chartRef = ref<HTMLElement>()
const timeRange = ref('本周')

const metrics = ref([
  { label: '总知识库数量', value: 128, change: '+12', trend: 'up' },
  { label: '总文档数', value: 3560, change: '+156', trend: 'up' },
  { label: '活跃文档数量', value: 2890, change: '+89', trend: 'up' },
  { label: '知识引用率', value: '78.5%', change: '+2.3%', trend: 'up' },
])

const hotKBs = ref([
  { rank: 1, name: '产品知识库', docCount: 156, viewCount: 3280 },
  { rank: 2, name: '技术知识库', docCount: 89, viewCount: 2150 },
  { rank: 3, name: '客户案例库', docCount: 45, viewCount: 1890 },
  { rank: 4, name: '培训资料库', docCount: 32, viewCount: 1200 },
  { rank: 5, name: '运营知识库', docCount: 28, viewCount: 980 },
])

const hotDocs = ref([
  { rank: 1, name: 'AIS 平台用户手册.pdf', kbName: '产品知识库', viewCount: 1280 },
  { rank: 2, name: 'API 接口文档.md', kbName: '技术知识库', viewCount: 856 },
  { rank: 3, name: '产品使用指南.docx', kbName: '产品知识库', viewCount: 623 },
  { rank: 4, name: '部署运维手册.pdf', kbName: '技术知识库', viewCount: 445 },
  { rank: 5, name: '客户需求分析.xlsx', kbName: '客户案例库', viewCount: 320 },
])

onMounted(() => {
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
</script>

<template>
  <div class="page-container">
    <div class="section-header">
      <h3>知识资产分析</h3>
      <el-radio-group v-model="timeRange" size="small">
        <el-radio-button label="今天" />
        <el-radio-button label="本周" />
        <el-radio-button label="本月" />
      </el-radio-group>
    </div>

    <!-- 指标卡片 -->
    <div class="metric-cards">
      <div v-for="m in metrics" :key="m.label" class="metric-card">
        <div class="metric-label">{{ m.label }}</div>
        <div class="metric-value">{{ m.value }}</div>
        <div class="metric-change" :class="m.trend">
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
        <div v-for="kb in hotKBs" :key="kb.rank" class="rank-item">
          <span class="rank" :class="{ 'top-3': kb.rank <= 3 }">{{ kb.rank }}</span>
          <span class="name">{{ kb.name }}</span>
          <span class="count">{{ kb.docCount }} 篇</span>
          <span class="views">{{ kb.viewCount }} 次</span>
        </div>
      </div>

      <!-- 热门文档排行 -->
      <div class="card-panel">
        <div class="section-title">热门文档排行</div>
        <div v-for="doc in hotDocs" :key="doc.rank" class="rank-item">
          <span class="rank" :class="{ 'top-3': doc.rank <= 3 }">{{ doc.rank }}</span>
          <span class="name">{{ doc.name }}</span>
          <span class="count">{{ doc.kbName }}</span>
          <span class="views">{{ doc.viewCount }} 次</span>
        </div>
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
