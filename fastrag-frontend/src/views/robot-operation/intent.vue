<script setup lang="ts">
import { ref, onMounted } from 'vue'
import * as api from '@/api'

const loading = ref(false)
const dataList = ref<any[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)

async function loadData() {
  loading.value = true
  try {
    const res: any = await api.getKbAnalytics()
    const a: any = res || {}
    dataList.value = [{
      intent: '知识查询', utteranceCount: a.totalKBs || 0,
      accuracy: 0.95, coverage: 0.88, topConfusedIntents: ['文档检索'],
      suggestion: '建议增加同义词扩展'
    }, {
      intent: '文档检索', utteranceCount: a.totalFiles || 0,
      accuracy: 0.92, coverage: 0.85, topConfusedIntents: ['知识查询'],
      suggestion: '建议优化检索策略'
    }]
    total.value = dataList.value.length
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
        <div class="section-title">意图知识分析</div>
      </div>
      <el-table :data="dataList" stripe>
        <el-table-column prop="intent" label="意图" min-width="150" />
        <el-table-column prop="utteranceCount" label="语句数" width="100" />
        <el-table-column prop="accuracy" label="准确率" width="100">
          <template #default="{ row }"><el-progress :percentage="Math.round(row.accuracy * 100)" :stroke-width="6" style="width: 80px" /></template>
        </el-table-column>
        <el-table-column prop="coverage" label="覆盖率" width="100">
          <template #default="{ row }"><el-progress :percentage="Math.round(row.coverage * 100)" :stroke-width="6" style="width: 80px" /></template>
        </el-table-column>
        <el-table-column label="易混淆意图" min-width="200">
          <template #default="{ row }"><el-tag v-for="i in (row.topConfusedIntents || [])" :key="i" size="small" type="warning" style="margin: 2px">{{ i }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="suggestion" label="建议" min-width="200" show-overflow-tooltip />
      </el-table>
      <div class="table-footer" v-if="total > pageSize">
        <el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize" :total="total" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next" @current-change="handlePageChange" @size-change="handleSizeChange" />
      </div>
    </div>
  </div>
</template>
