<script setup lang="ts">
import { ref, onMounted } from 'vue'
import * as api from '@/mock/robot-operation'
import { RESOLUTION_LABELS, RESOLUTION_COLORS } from '@/mock/robot-operation'

const loading = ref(false)
const dataList = ref<any[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)

async function loadData() {
  loading.value = true
  try {
    const res = api.getFaqAnalysisList({ page: currentPage.value, pageSize: pageSize.value })
    dataList.value = res.list; total.value = res.total
  } finally { loading.value = false }
}
onMounted(loadData)

function handlePageChange(p: number) { currentPage.value = p; loadData() }
function handleSizeChange(s: number) { pageSize.value = s; currentPage.value = 1; loadData() }
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="card-panel">
      <div class="section-header">
        <div class="section-title">FAQ知识分析</div>
      </div>
      <el-table :data="dataList" stripe>
        <el-table-column prop="question" label="问题" min-width="250" show-overflow-tooltip />
        <el-table-column prop="hitCount" label="命中数" width="80" />
        <el-table-column prop="missCount" label="未命中" width="80" />
        <el-table-column prop="hitRate" label="命中率" width="100">
          <template #default="{ row }">
            <el-progress :percentage="Math.round(row.hitRate * 100)" :stroke-width="6" style="width: 80px" />
          </template>
        </el-table-column>
        <el-table-column prop="avgSatisfaction" label="满意度" width="100">
          <template #default="{ row }">{{ row.avgSatisfaction.toFixed(1) }}</template>
        </el-table-column>
        <el-table-column prop="period" label="周期" width="100" />
      </el-table>
      <div class="table-footer" v-if="total > pageSize">
        <el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize" :total="total" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next" @current-change="handlePageChange" @size-change="handleSizeChange" />
      </div>
    </div>
  </div>
</template>
