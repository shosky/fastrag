<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { getReportList } from '@/mock/knowledge-review'

const loading = ref(false)
const dataList = ref<any[]>([])

async function loadData() { loading.value = true; try { dataList.value = getReportList() } finally { loading.value = false } }
onMounted(loadData)

const summary = computed(() => {
  const total = dataList.value.reduce((s, r) => s + (r.totalReviews || 0), 0)
  const approved = dataList.value.reduce((s, r) => s + (r.approved || 0), 0)
  const rejected = dataList.value.reduce((s, r) => s + (r.rejected || 0), 0)
  const avgTime = dataList.value.length > 0 ? Math.round(dataList.value.reduce((s, r) => s + (r.avgReviewTime || 0), 0) / dataList.value.length) : 0
  return { total, approved, rejected, avgTime }
})

function handleExport() { ElMessage.success('报告导出功能开发中') }
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="card-panel">
      <div class="section-header">
        <div class="section-title">审核报告</div>
        <el-button @click="handleExport">导出报告</el-button>
      </div>
      <!-- 统计卡片 -->
      <div style="display: flex; gap: 16px; margin-bottom: 20px">
        <div class="stat-card">
          <div class="stat-card__value">{{ summary.total }}</div>
          <div class="stat-card__label">审核总数</div>
        </div>
        <div class="stat-card">
          <div class="stat-card__value" style="color: #67C23A">{{ summary.approved }}</div>
          <div class="stat-card__label">通过数</div>
        </div>
        <div class="stat-card">
          <div class="stat-card__value" style="color: #F56C6C">{{ summary.rejected }}</div>
          <div class="stat-card__label">驳回数</div>
        </div>
        <div class="stat-card">
          <div class="stat-card__value" style="color: #E6A23C">{{ summary.avgTime }}h</div>
          <div class="stat-card__label">平均审核时长</div>
        </div>
      </div>
      <el-table :data="dataList" stripe>
        <el-table-column prop="name" label="报告名称" min-width="200" />
        <el-table-column prop="period" label="周期" width="100" />
        <el-table-column prop="totalReviews" label="审核总数" width="100" />
        <el-table-column prop="approved" label="通过" width="80">
          <template #default="{ row }"><span style="color: #67C23A">{{ row.approved }}</span></template>
        </el-table-column>
        <el-table-column prop="rejected" label="驳回" width="80">
          <template #default="{ row }"><span style="color: #F56C6C">{{ row.rejected }}</span></template>
        </el-table-column>
        <el-table-column prop="timeout" label="超时" width="80">
          <template #default="{ row }"><span style="color: #E6A23C">{{ row.timeout }}</span></template>
        </el-table-column>
        <el-table-column prop="avgReviewTime" label="平均时长(h)" width="120" />
        <el-table-column prop="generatedAt" label="生成时间" width="160" show-overflow-tooltip />
      </el-table>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;
.stat-card {
  flex: 1; background: $bg-white; border: 1px solid $border-lighter; border-radius: $radius-base;
  padding: 16px; text-align: center;
  &__value { font-size: 28px; font-weight: 600; color: $text-primary; }
  &__label { font-size: 13px; color: $text-secondary; margin-top: 4px; }
}
</style>
