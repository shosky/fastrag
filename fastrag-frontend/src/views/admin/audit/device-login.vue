<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import * as api from '@/api'

const timeRange = ref('')
const filterStatus = ref('')
const filterDevice = ref('')
const loading = ref(false)
const loginDetails = ref<any[]>([])

async function loadLoginLogs() {
  loading.value = true
  try {
    const params: Record<string, unknown> = {}
    if (filterStatus.value) params.status = filterStatus.value
    if (timeRange.value) params.timeRange = timeRange.value
    if (filterDevice.value) params.device = filterDevice.value
    const res: any = await api.getLoginLogs(Object.keys(params).length ? params : undefined)
    loginDetails.value = (res as any)?.list || (res as any) || []
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  loadLoginLogs()
}

function handleReset() {
  filterStatus.value = ''
  timeRange.value = ''
  filterDevice.value = ''
  loadLoginLogs()
}

onMounted(loadLoginLogs)

function handleExport() {
  ElMessage.success('导出成功')
}
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="section-header">
      <h3>设备登录分析</h3>
      <el-button @click="handleExport">导出</el-button>
    </div>

    <div class="card-panel">
        <div class="section-title">登录明细</div>
        <div class="filter-bar">
          <el-select v-model="filterStatus" placeholder="状态" clearable style="width: 100px">
            <el-option label="仅成功" value="成功" />
            <el-option label="仅失败" value="失败" />
          </el-select>
          <el-select v-model="timeRange" style="width: 120px">
            <el-option label="今天" value="今天" />
            <el-option label="昨天" value="昨天" />
            <el-option label="最近7天" value="最近7天" />
            <el-option label="最近30天" value="最近30天" />
          </el-select>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>
        <el-table :data="loginDetails" stripe size="small">
          <el-table-column prop="username" label="用户名" width="80" />
          <el-table-column prop="device" label="设备" width="120" />
          <el-table-column prop="browser" label="浏览器" width="120" />
          <el-table-column prop="ip" label="IP地址" width="130" />
          <el-table-column prop="status" label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.status === '成功' ? 'success' : 'danger'" size="small">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="time" label="时间" />
        </el-table>
        <el-empty v-if="!loginDetails.length && !loading" description="暂无登录记录" />
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
</style>
