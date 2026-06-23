<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import * as api from '@/api'

const timeRange = ref('今天')
const filterStatus = ref('')
const filterDevice = ref('')
const loading = ref(false)

const stats = ref({
  onlineUsers: 0,
  activeDevices: 0,
  todayLogins: 0,
  failedLogins: 0,
})

const deviceDistribution = ref<any[]>([])
const loginDetails = ref<any[]>([])

async function loadLoginLogs() {
  loading.value = true
  try {
    const res: any = await api.getLoginLogs()
    loginDetails.value = (res as any)?.list || (res as any) || []
  } finally {
    loading.value = false
  }
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

    <div class="stat-cards">
      <div class="stat-card">
        <div class="stat-label">在线用户</div>
        <div class="stat-value">{{ stats.onlineUsers }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">活跃设备</div>
        <div class="stat-value">{{ stats.activeDevices }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">今日登录</div>
        <div class="stat-value">{{ stats.todayLogins }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">登录失败</div>
        <div class="stat-value danger">{{ stats.failedLogins }}</div>
      </div>
    </div>

    <div class="content-grid">
      <div class="card-panel">
        <div class="section-title">设备类型分布</div>
        <div v-for="d in deviceDistribution" :key="d.type" class="device-item">
          <div class="device-header">
            <span>{{ d.type }}</span>
            <span>{{ d.count }} 台 ({{ d.percentage }}%)</span>
          </div>
          <el-progress :percentage="d.percentage" :show-text="false" />
        </div>
        <el-empty v-if="!deviceDistribution.length" description="暂无数据" :image-size="40" />
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
          <el-button type="primary">查询</el-button>
          <el-button @click="filterStatus = ''">重置</el-button>
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

.stat-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: $spacing-base;
  margin-bottom: $spacing-base;
}

.stat-card {
  background: $bg-white;
  border-radius: $radius-base;
  padding: $spacing-lg;
  .stat-label { font-size: 13px; color: $text-secondary; margin-bottom: $spacing-sm; }
  .stat-value { font-size: 28px; font-weight: 700; &.danger { color: $color-danger; } }
}

.content-grid {
  display: grid;
  grid-template-columns: 300px 1fr;
  gap: $spacing-base;
}

.device-item {
  margin-bottom: $spacing-base;
  .device-header { display: flex; justify-content: space-between; font-size: 13px; margin-bottom: $spacing-xs; }
}
</style>
