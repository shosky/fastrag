<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as bpm from '@/api/bpm'
import { type GlobalStatsVO, type FlowStatsVO } from '@/types/bpm'

const route = useRoute()
const router = useRouter()
const flowDefId = computed(() => String(route.params.flowDefId || ''))

const loading = ref(false)
const globalStats = ref<GlobalStatsVO | null>(null)
const flowStats = ref<FlowStatsVO | null>(null)

async function load() {
  loading.value = true
  try {
    globalStats.value = await bpm.globalStats()
    if (flowDefId.value) {
      flowStats.value = await bpm.flowStats(flowDefId.value)
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  } finally { loading.value = false }
}

function formatNumber(n?: number) {
  if (n === undefined || n === null) return '-'
  return n.toLocaleString()
}

function formatDuration(ms?: number) {
  if (!ms || ms <= 0) return '-'
  if (ms < 1000) return `${ms}ms`
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`
  if (ms < 3600000) return `${Math.floor(ms / 60000)}m${Math.floor((ms % 60000) / 1000)}s`
  return `${Math.floor(ms / 3600000)}h${Math.floor((ms % 3600000) / 60000)}m`
}

const trendChart = computed(() => {
  if (!flowStats.value?.trend?.length) return null
  const max = Math.max(...flowStats.value.trend.map((t: any) => t.count), 1)
  return flowStats.value.trend.map((t: any) => ({
    date: t.date,
    count: t.count,
    pct: (t.count / max) * 100,
  }))
})

onMounted(load)
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="toolbar">
      <div class="left">
        <el-button text @click="router.push({ name: 'BpmFlowList' })">📂 流程列表</el-button>
        <span class="title">{{ flowDefId ? '流程统计' : '全局统计' }}</span>
      </div>
      <el-button @click="load">🔄 刷新</el-button>
    </div>

    <!-- 全局统计卡片 -->
    <div class="section">
      <div class="section-title">📊 全局概览</div>
      <el-row :gutter="12">
        <el-col :xs="12" :sm="8" :md="6">
          <div class="stat-card">
            <div class="stat-label">总流程数</div>
            <div class="stat-value">{{ formatNumber(globalStats?.totalFlows) }}</div>
          </div>
        </el-col>
        <el-col :xs="12" :sm="8" :md="6">
          <div class="stat-card">
            <div class="stat-label">总实例数</div>
            <div class="stat-value">{{ formatNumber(globalStats?.totalInstances) }}</div>
          </div>
        </el-col>
        <el-col :xs="12" :sm="8" :md="6">
          <div class="stat-card primary">
            <div class="stat-label">运行中</div>
            <div class="stat-value">{{ formatNumber(globalStats?.runningInstances) }}</div>
          </div>
        </el-col>
        <el-col :xs="12" :sm="8" :md="6">
          <div class="stat-card success">
            <div class="stat-label">已完成</div>
            <div class="stat-value">{{ formatNumber(globalStats?.completedInstances) }}</div>
          </div>
        </el-col>
        <el-col :xs="12" :sm="8" :md="6">
          <div class="stat-card danger">
            <div class="stat-label">失败</div>
            <div class="stat-value">{{ formatNumber(globalStats?.failedInstances) }}</div>
          </div>
        </el-col>
        <el-col :xs="12" :sm="8" :md="6">
          <div class="stat-card">
            <div class="stat-label">今日实例</div>
            <div class="stat-value">{{ formatNumber(globalStats?.todayInstances) }}</div>
          </div>
        </el-col>
        <el-col :xs="12" :sm="8" :md="6">
          <div class="stat-card warning">
            <div class="stat-label">平均耗时</div>
            <div class="stat-value">{{ formatDuration(globalStats?.averageDurationMs) }}</div>
          </div>
        </el-col>
      </el-row>
    </div>

    <!-- 单流程统计 -->
    <template v-if="flowStats">
      <div class="section">
        <div class="section-title">📈 当前流程</div>
        <el-row :gutter="12">
          <el-col :xs="12" :sm="8" :md="6">
            <div class="stat-card">
              <div class="stat-label">总实例</div>
              <div class="stat-value">{{ formatNumber(flowStats.totalInstances) }}</div>
            </div>
          </el-col>
          <el-col :xs="12" :sm="8" :md="6">
            <div class="stat-card primary">
              <div class="stat-label">运行中</div>
              <div class="stat-value">{{ formatNumber(flowStats.runningInstances) }}</div>
            </div>
          </el-col>
          <el-col :xs="12" :sm="8" :md="6">
            <div class="stat-card success">
              <div class="stat-label">已完成</div>
              <div class="stat-value">{{ formatNumber(flowStats.completedInstances) }}</div>
            </div>
          </el-col>
          <el-col :xs="12" :sm="8" :md="6">
            <div class="stat-card danger">
              <div class="stat-label">失败</div>
              <div class="stat-value">{{ formatNumber(flowStats.failedInstances) }}</div>
            </div>
          </el-col>
          <el-col :xs="12" :sm="8" :md="6">
            <div class="stat-card warning">
              <div class="stat-label">平均耗时</div>
              <div class="stat-value">{{ formatDuration(flowStats.averageDurationMs) }}</div>
            </div>
          </el-col>
        </el-row>
      </div>

      <div class="section" v-if="trendChart">
        <div class="section-title">📅 最近趋势</div>
        <div class="trend-chart">
          <div v-for="t in trendChart" :key="t.date" class="trend-bar-wrap">
            <div class="trend-bar" :style="{ height: t.pct + '%' }" :title="`${t.date}: ${t.count} 次`"></div>
            <div class="trend-label">{{ t.date.substring(5) }}</div>
            <div class="trend-count">{{ t.count }}</div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.page-container { padding: $spacing-base; }

.toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; gap: 8px; }
.toolbar .left { display: flex; align-items: center; gap: 8px; }
.toolbar .title { font-weight: 600; font-size: 15px; }

.section { margin-bottom: 20px; }
.section-title { font-size: 14px; font-weight: 600; margin-bottom: 12px; color: #303133; }

.stat-card {
  background: #fff; border: 1px solid #ebeef5; border-radius: 8px;
  padding: 16px; text-align: center; transition: all 0.15s;
  &:hover { box-shadow: 0 2px 12px rgba(0,0,0,0.08); }
  &.primary { border-left: 4px solid #409EFF; }
  &.success { border-left: 4px solid #67C23A; }
  &.danger { border-left: 4px solid #F56C6C; }
  &.warning { border-left: 4px solid #E6A23C; }
}
.stat-label { font-size: 12px; color: #909399; margin-bottom: 6px; }
.stat-value { font-size: 24px; font-weight: 600; color: #303133; }

.trend-chart {
  display: flex; gap: 8px; align-items: flex-end; height: 200px;
  background: #fff; border: 1px solid #ebeef5; border-radius: 8px;
  padding: 16px; overflow-x: auto;
}
.trend-bar-wrap {
  flex: 1; min-width: 40px; display: flex; flex-direction: column;
  align-items: center; height: 100%; justify-content: flex-end;
}
.trend-bar {
  width: 100%; background: linear-gradient(to top, #409EFF, #66b1ff);
  border-radius: 4px 4px 0 0; transition: height 0.3s; min-height: 2px;
}
.trend-label { font-size: 10px; color: #909399; margin-top: 4px; }
.trend-count { font-size: 11px; font-weight: 600; color: #303133; }
</style>