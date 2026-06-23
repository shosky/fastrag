<script setup lang="ts">
import { ref, onMounted } from 'vue'
import * as api from '@/api'

const filterStatus = ref('')
const loading = ref(false)
const downloadList = ref<any[]>([])

async function loadDownloads() {
  loading.value = true
  try {
    const res: any = await api.getDictionaries({ type: '下载记录' })
    downloadList.value = res?.['下载记录'] || []
  } finally {
    loading.value = false
  }
}

onMounted(loadDownloads)
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="card-panel">
      <div class="section-title">下载中心</div>
      <div class="filter-bar">
        <el-input placeholder="名称" clearable style="width: 200px" />
        <el-select v-model="filterStatus" placeholder="状态" clearable style="width: 100px">
          <el-option label="成功" value="成功" />
          <el-option label="失败" value="失败" />
        </el-select>
        <el-button type="primary">查询</el-button>
        <el-button>重置</el-button>
      </div>
      <el-table :data="downloadList" stripe>
        <el-table-column prop="key" label="名称" show-overflow-tooltip />
        <el-table-column prop="value" label="详情" show-overflow-tooltip />
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button link type="primary" size="small">下载</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!downloadList.length && !loading" description="暂无下载记录" />
    </div>
  </div>
</template>
