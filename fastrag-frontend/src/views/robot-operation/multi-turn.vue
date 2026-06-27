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
    const res = api.getMultiTurnList({ page: currentPage.value, pageSize: pageSize.value })
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
        <div class="section-title">多轮对话分析</div>
      </div>
      <el-table :data="dataList" stripe>
        <el-table-column prop="sessionId" label="会话ID" width="120" />
        <el-table-column prop="topic" label="主题" width="120" />
        <el-table-column prop="turnCount" label="轮次" width="80" />
        <el-table-column prop="resolution" label="解决状态" width="100">
          <template #default="{ row }"><el-tag :type="RESOLUTION_COLORS[row.resolution] as any" size="small">{{ RESOLUTION_LABELS[row.resolution] || row.resolution }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="satisfaction" label="满意度" width="100">
          <template #default="{ row }">{{ row.satisfaction != null ? row.satisfaction.toFixed(1) : '-' }}</template>
        </el-table-column>
        <el-table-column label="关键意图" min-width="200">
          <template #default="{ row }"><el-tag v-for="i in (row.keyIntents || [])" :key="i" size="small" style="margin: 2px">{{ i }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="createdAt" label="时间" width="160" show-overflow-tooltip />
      </el-table>
      <div class="table-footer" v-if="total > pageSize">
        <el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize" :total="total" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next" @current-change="handlePageChange" @size-change="handleSizeChange" />
      </div>
    </div>
  </div>
</template>
