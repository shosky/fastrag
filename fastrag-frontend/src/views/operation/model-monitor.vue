<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import * as api from '@/api'
import { usePagination } from '@/composables/usePagination'
import { ElMessage } from 'element-plus'
import type { MetricItem, ModelMonitorOverview } from '@/types/monitor'
import MetricCards from './components/MetricCards.vue'
import RankList from './components/RankList.vue'

const timeRange = ref(7)
const overviewData = ref<Partial<ModelMonitorOverview>>({})
const overviewLoading = ref(false)
const searchModel = ref('')

const {
  currentPage, pageSize, total,
  handleCurrentChange: onPageChange,
  handleSizeChange: onSizeChange,
} = usePagination(10)

const metrics = computed<MetricItem[]>(() => overviewData.value?.metrics || [])
const modelUsage = computed(() => overviewData.value?.distribution || [])
const highConsumeApps = computed(() => overviewData.value?.topApps || [])
const modelStats = computed(() => overviewData.value?.stats?.list || [])

async function loadOverview() {
  overviewLoading.value = true
  try {
    const res = await api.getModelMonitorOverview({
      timeRange: timeRange.value,
      keyword: searchModel.value || undefined,
      page: currentPage.value,
      pageSize: pageSize.value,
    })
    overviewData.value = res || {}
    total.value = res?.stats?.total || 0
  } catch {
    ElMessage.error('加载模型监控数据失败')
    overviewData.value = {}
  } finally {
    overviewLoading.value = false
  }
}

function handleSearch() {
  currentPage.value = 1
  loadOverview()
}

function handleReset() {
  searchModel.value = ''
  currentPage.value = 1
  loadOverview()
}

/** 事件驱动：页码变化时重新拉取（同时更新 composable 状态） */
function handleCurrentChange(page: number) {
  onPageChange(page)
  loadOverview()
}

/** 事件驱动：每页条数变化时重置到第一页并拉取 */
function handleSizeChange(size: number) {
  onSizeChange(size)
  loadOverview()
}

// 时间范围变化时重置到第一页并重新加载
watch(timeRange, () => {
  currentPage.value = 1
  loadOverview()
})

onMounted(loadOverview)
</script>

<template>
  <div class="page-container" v-loading="overviewLoading">
    <div class="section-header">
      <h3>模型监控分析</h3>
      <el-select v-model="timeRange" size="small" style="width: 120px">
        <el-option :value="7" label="近7天" />
        <el-option :value="30" label="近30天" />
        <el-option :value="180" label="近6个月" />
      </el-select>
    </div>

    <!-- 指标卡片 -->
    <MetricCards :items="metrics" :columns="4" />

    <div class="monitor-grid">
      <!-- 模型使用分布 -->
      <div class="card-panel">
        <div class="section-title">模型使用分布</div>
        <div v-if="modelUsage.length">
          <div v-for="model in modelUsage" :key="model.name" class="usage-item">
            <div class="usage-header">
              <span>{{ model.name }}</span>
              <span>{{ model.percentage }}%</span>
            </div>
            <el-progress :percentage="model.percentage" :show-text="false" />
            <div class="usage-token">Token: {{ model.token }}</div>
          </div>
        </div>
        <el-empty v-else description="暂无数据" :image-size="50" />
      </div>

      <!-- 高消耗应用排行 -->
      <div class="card-panel">
        <div class="section-title">高消耗应用排行</div>
        <RankList v-if="highConsumeApps.length" :items="highConsumeApps">
          <template #default="{ item }">
            <span class="token">{{ item.token }}</span>
            <span class="cost">{{ item.cost }}</span>
          </template>
        </RankList>
        <el-empty v-else description="暂无数据" :image-size="50" />
      </div>
    </div>

    <!-- 模型调用统计 -->
    <div class="card-panel">
      <div class="section-title">模型调用统计</div>
      <div class="filter-bar">
        <el-input v-model="searchModel" placeholder="搜索模型 Code" clearable style="width: 200px" />
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>
      <el-table :data="modelStats" stripe>
        <el-table-column prop="code" label="模型 Code" />
        <el-table-column prop="calls" label="调用总量" width="120" />
        <el-table-column prop="fails" label="失败量" width="100" />
        <el-table-column prop="token" label="Token 消耗" width="150" />
        <el-table-column prop="cost" label="消耗金额" width="150" />
      </el-table>
      <el-empty v-if="!overviewLoading && modelStats.length === 0" description="暂无数据" :image-size="60" />
      <div class="model-monitor__pagination">
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
  h3 { margin: 0; }
}

.monitor-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: $spacing-base;
  margin-bottom: $spacing-base;
}

.usage-item {
  margin-bottom: $spacing-base;
  .usage-header { display: flex; justify-content: space-between; font-size: 13px; margin-bottom: $spacing-xs; }
  .usage-token { font-size: 12px; color: $text-secondary; margin-top: $spacing-xs; }
}

// RankList 插槽附加字段（排行行内样式）
.token, .cost { font-size: 12px; color: $text-secondary; }

// 分页 BEM 风格 — 遵循 AGENTS.md 规范
.model-monitor__pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
