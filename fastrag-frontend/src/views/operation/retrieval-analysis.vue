<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { usePagination } from '@/composables/usePagination'
import * as api from '@/api'

// ========== 类型定义 ==========

interface AnalysisData {
  totalQueries: number
  noResultCount: number
  noResultRate: number
  avgLatencyMs: number
  avgHitCount: number
  topQueries: { query: string; count: number }[]
  noResultQueries: { query: string; count: number }[]
}

interface RetrievalLog {
  id: number | string
  kbId: string
  query: string
  hitCount: number
  topScore: number
  latencyMs: number
  hasResult: boolean
  createdAt: string
}

interface KbOption {
  id: string
  name: string
}

// ========== 状态 ==========

const loading = ref(false)
const analysis = ref<AnalysisData>({
  totalQueries: 0,
  noResultCount: 0,
  noResultRate: 0,
  avgLatencyMs: 0,
  avgHitCount: 0,
  topQueries: [],
  noResultQueries: [],
})
const logList = ref<RetrievalLog[]>([])
const kbId = ref('')
const hasResult = ref('')
const kbOptions = ref<KbOption[]>([])
const kbNameMap = computed(() => {
  const map: Record<string, string> = {}
  kbOptions.value.forEach((kb) => {
    map[kb.id] = kb.name
  })
  return map
})

const { currentPage, pageSize, total, handleCurrentChange, handleSizeChange, reset } = usePagination(10)

// ========== 数据加载 ==========

async function loadKbOptions() {
  try {
    const res: any = await api.getKnowledgeBases({ page: 1, pageSize: 999 })
    kbOptions.value = (res?.list || []).map((kb: any) => ({
      id: String(kb.id),
      name: kb.name || kb.id,
    }))
  } catch {
    // ignore
  }
}

async function loadAnalysis() {
  try {
    const res = (await api.getRetrievalLogAnalysis(kbId.value || undefined)) as AnalysisData | null
    if (res) {
      analysis.value = {
        totalQueries: res.totalQueries ?? 0,
        noResultCount: res.noResultCount ?? 0,
        noResultRate: res.noResultRate ?? 0,
        avgLatencyMs: res.avgLatencyMs ?? 0,
        avgHitCount: res.avgHitCount ?? 0,
        topQueries: res.topQueries || [],
        noResultQueries: res.noResultQueries || [],
      }
    }
  } catch {
    // ignore
  }
}

async function loadLogs() {
  loading.value = true
  try {
    const res: any = await api.getRetrievalLogs({
      kbId: kbId.value || undefined,
      hasResult: hasResult.value === '' ? undefined : hasResult.value === 'true',
      page: currentPage.value,
      pageSize: pageSize.value,
    })
    logList.value = res?.list || []
    total.value = res?.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  reset()
  loadAnalysis()
  loadLogs()
}

function handleReset() {
  kbId.value = ''
  hasResult.value = ''
  handleSearch()
}

// 页码/条数变化时重新拉取日志列表
watch([currentPage, pageSize], () => {
  loadLogs()
})

onMounted(() => {
  loadKbOptions()
  loadAnalysis()
  loadLogs()
})
</script>

<template>
  <div class="page-container" v-loading="loading">
    <!-- 页面标题 -->
    <div class="section-header">
      <h3>检索日志分析</h3>
    </div>

    <!-- 统计指标卡片 -->
    <div class="metric-cards">
      <div class="metric-card">
        <div class="metric-label">总查询次数</div>
        <div class="metric-value">{{ analysis.totalQueries }}</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">无结果数</div>
        <div class="metric-value">{{ analysis.noResultCount }}</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">无结果率</div>
        <div class="metric-value">{{ analysis.noResultRate }}%</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">平均耗时(ms)</div>
        <div class="metric-value">{{ analysis.avgLatencyMs }}</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">平均命中数</div>
        <div class="metric-value">{{ analysis.avgHitCount }}</div>
      </div>
    </div>

    <!-- 热门查询 / 无结果查询 -->
    <div class="analysis-grid">
      <div class="card-panel">
        <div class="section-title">热门查询 Top10</div>
        <div v-if="analysis.topQueries.length">
          <div v-for="(item, idx) in analysis.topQueries" :key="item.query" class="rank-item">
            <span class="rank" :class="{ 'top-3': idx < 3 }">{{ idx + 1 }}</span>
            <span class="name">{{ item.query }}</span>
            <span class="count">{{ item.count }} 次</span>
          </div>
        </div>
        <el-empty v-else description="暂无数据" :image-size="60" />
      </div>
      <div class="card-panel">
        <div class="section-title">无结果查询 Top10</div>
        <div v-if="analysis.noResultQueries.length">
          <div v-for="(item, idx) in analysis.noResultQueries" :key="item.query" class="rank-item">
            <span class="rank" :class="{ 'top-3': idx < 3 }">{{ idx + 1 }}</span>
            <span class="name">{{ item.query }}</span>
            <span class="count">{{ item.count }} 次</span>
          </div>
        </div>
        <el-empty v-else description="暂无数据" :image-size="60" />
      </div>
    </div>

    <!-- 检索日志明细 -->
    <div class="card-panel">
      <div class="section-title">检索日志明细</div>
      <div class="filter-bar">
        <el-select v-model="kbId" placeholder="选择知识库" clearable filterable style="width: 200px">
          <el-option
            v-for="kb in kbOptions"
            :key="kb.id"
            :label="kb.name"
            :value="kb.id"
          />
        </el-select>
        <el-select v-model="hasResult" placeholder="结果状态" clearable style="width: 140px">
          <el-option label="有结果" value="true" />
          <el-option label="无结果" value="false" />
        </el-select>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>
      <el-table :data="logList" stripe size="small">
        <el-table-column prop="query" label="查询内容" show-overflow-tooltip />
        <el-table-column label="知识库" width="180" show-overflow-tooltip>
          <template #default="{ row }">
            {{ kbNameMap[row.kbId] || (row.kbId ? `已删除（${row.kbId}）` : '-') }}
          </template>
        </el-table-column>
        <el-table-column prop="hitCount" label="命中数" width="80" align="center" />
        <el-table-column prop="topScore" label="最高分" width="80" align="center" />
        <el-table-column prop="latencyMs" label="耗时(ms)" width="90" align="center" />
        <el-table-column label="结果" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.hasResult ? 'success' : 'danger'" size="small">
              {{ row.hasResult ? '有' : '无' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="时间" width="170" />
      </el-table>
      <div class="log-table__pagination">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="handleCurrentChange"
          @size-change="handleSizeChange"
        />
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
  grid-template-columns: repeat(5, 1fr);
  gap: $spacing-base;
  margin-bottom: $spacing-base;
}

.metric-card {
  background: $bg-white;
  border-radius: $radius-base;
  padding: $spacing-lg;

  .metric-label {
    font-size: 13px;
    color: $text-secondary;
    margin-bottom: $spacing-sm;
  }
  .metric-value {
    font-size: 28px;
    font-weight: 700;
  }
}

.analysis-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: $spacing-base;
  margin-bottom: $spacing-base;
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

  &:last-child { border-bottom: none; }

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
  .name {
    flex: 1;
    font-size: 13px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .count {
    font-size: 12px;
    color: $text-secondary;
    flex-shrink: 0;
  }
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  margin-bottom: $spacing-base;
  flex-wrap: wrap;
}

.log-table__pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
