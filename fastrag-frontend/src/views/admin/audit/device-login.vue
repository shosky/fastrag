<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

const timeRange = ref('今天')
const filterStatus = ref('')
const filterDevice = ref('')

const stats = ref({
  onlineUsers: 23,
  activeDevices: 45,
  todayLogins: 156,
  failedLogins: 8,
})

const deviceDistribution = ref([
  { type: 'Windows', count: 35, percentage: 45 },
  { type: 'Mac', count: 28, percentage: 36 },
  { type: 'iOS', count: 8, percentage: 10 },
  { type: 'Android', count: 7, percentage: 9 },
])

const loginDetails = ref([
  { id: '1', username: 'admin', device: 'Windows PC', browser: 'Chrome 120', ip: '192.168.1.100', status: '成功', time: '2026-06-04 10:30:00' },
  { id: '2', username: '张三', device: 'MacBook Pro', browser: 'Safari 17', ip: '192.168.1.101', status: '成功', time: '2026-06-04 09:15:00' },
  { id: '3', username: '李四', device: 'iPhone 15', browser: 'Safari Mobile', ip: '10.0.0.50', status: '成功', time: '2026-06-04 08:45:00' },
  { id: '4', username: '王五', device: 'Windows PC', browser: 'Edge 120', ip: '192.168.1.102', status: '失败', time: '2026-06-04 08:30:00' },
  { id: '5', username: 'admin', device: 'MacBook Air', browser: 'Chrome 120', ip: '192.168.1.100', status: '成功', time: '2026-06-03 17:20:00' },
])

function handleExport() {
  ElMessage.success('导出成功')
}
</script>

<template>
  <div class="page-container">
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
