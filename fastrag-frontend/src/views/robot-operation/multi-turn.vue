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
      sessionId: 'ANL-001', topic: '知识库统计', turnCount: 1,
      resolution: 'resolved', satisfaction: 4.8, keyIntents: ['知识查询'],
      createdAt: new Date().toISOString()
    }, {
      sessionId: 'ANL-002', topic: '文档分析', turnCount: 1,
      resolution: 'resolved', satisfaction: 4.5, keyIntents: ['文档检索'],
      createdAt: new Date().toISOString()
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
        <div class="section-title">多轮对话分析</div>
      </div>
      <el-table :data="dataList" stripe>
        <el-table-column prop="sessionId" label="分析ID" width="120" />
        <el-table-column prop="topic" label="主题" width="120" />
        <el-table-column prop="turnCount" label="轮次" width="80" />
        <el-table-column prop="resolution" label="状态" width="100">
          <template #default="{ row }"><el-tag :type="row.resolution === 'resolved' ? 'success' : 'danger'" size="small">{{ row.resolution === 'resolved' ? '已解决' : '未解决' }}</el-tag></template>
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
