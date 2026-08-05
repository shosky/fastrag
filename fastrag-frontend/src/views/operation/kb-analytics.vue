<script setup lang="ts">
import { ref, onMounted } from 'vue'
import * as api from '@/api'

interface MetricItem {
  label: string
  value: number
  displayValue: string
  trend?: 'up' | 'down'
  change?: string
}

interface HotKb {
  rank: number
  name: string
  docCount: number
}

interface HotDoc {
  rank: number
  name: string
  kbName: string
  viewCount: number
}

interface AnalyticsData {
  metrics: MetricItem[]
  hotKBs: HotKb[]
  hotDocs: HotDoc[]
}

const loading = ref(true)
const metrics = ref<MetricItem[]>([])
const hotKBs = ref<HotKb[]>([])
const hotDocs = ref<HotDoc[]>([])

async function loadAnalytics() {
  loading.value = true
  try {
    const data = (await api.getKbAnalytics()) as AnalyticsData | null
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

onMounted(loadAnalytics)

// 获取指标的显示值：百分比指标优先展示 displayValue
function getMetricDisplay(m: MetricItem): string {
  return m.displayValue || String(m.value)
}
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="section-header">
      <h3>知识资产分析</h3>
    </div>

    <!-- 指标卡片 -->
    <div class="metric-cards">
      <div v-for="m in metrics" :key="m.label" class="metric-card">
        <div class="metric-label">{{ m.label }}</div>
        <div class="metric-value">{{ getMetricDisplay(m) }}</div>
        <div
          v-if="m.trend && m.change"
          class="metric-change"
          :class="m.trend"
        >
          <el-icon><Top v-if="m.trend === 'up'" /><Bottom v-else /></el-icon>
          {{ m.change }}
        </div>
      </div>
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

.card-panel {
  background: $bg-white;
  border-radius: $radius-base;
  padding: $spacing-lg;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: $spacing-base;
  padding-bottom: $spacing-sm;
  border-bottom: 1px solid $border-extra-light;
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
    flex-shrink: 0;
    &.top-3 { background: $color-primary; color: #fff; }
  }

  .name { flex: 1; font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .count, .views { font-size: 12px; color: $text-secondary; flex-shrink: 0; }
}
</style>
