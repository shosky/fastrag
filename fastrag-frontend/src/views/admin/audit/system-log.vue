<script setup lang="ts">
import { ref, onMounted } from 'vue'
import * as api from '@/api'

const searchKeyword = ref('')
const filterModule = ref('')
const loading = ref(false)

const modules = ['登录认证', '首页管理', '知识仓库管理', '应用中心', '系统与权限管理', '智能问答与交互', '监测中心', '知识加工']

const logList = ref<any[]>([])

async function loadLogs() {
  loading.value = true
  try {
    const res = await api.getAuditLogs({ limit: 100 })
    logList.value = (res as any)?.list || (res as any) || []
  } finally {
    loading.value = false
  }
}

onMounted(loadLogs)

const expandedRows = ref<string[]>([])

async function handleSearch() {
  await loadLogs()
}

function handleReset() {
  searchKeyword.value = ''
  filterModule.value = ''
  loadLogs()
}

function toggleExpand(id: string) {
  const idx = expandedRows.value.indexOf(id)
  if (idx > -1) {
    expandedRows.value.splice(idx, 1)
  } else {
    expandedRows.value.push(id)
  }
}
</script>

<template>
  <div class="page-container">
    <div class="card-panel">
      <div class="section-title">系统日志</div>
      <div class="filter-bar">
        <el-input v-model="searchKeyword" placeholder="搜索关键词" clearable style="width: 200px" />
        <el-select v-model="filterModule" placeholder="模块名称" clearable style="width: 150px">
          <el-option v-for="m in modules" :key="m" :label="m" :value="m" />
        </el-select>
        <el-date-picker type="daterange" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" style="width: 240px" />
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>
      <el-table :data="logList" stripe>
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="expand-detail">
              <el-descriptions :column="3" border size="small">
                <el-descriptions-item label="日志ID">{{ row.id }}</el-descriptions-item>
                <el-descriptions-item label="模块名称">{{ row.module }}</el-descriptions-item>
                <el-descriptions-item label="操作状态">
                  <el-tag :type="row.status === '成功' ? 'success' : 'danger'" size="small">{{ row.status }}</el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="浏览器">{{ row.browser }}</el-descriptions-item>
                <el-descriptions-item label="平台">{{ row.platform }}</el-descriptions-item>
                <el-descriptions-item label="IP地址">{{ row.ip }}</el-descriptions-item>
              </el-descriptions>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="username" label="用户名" width="100" />
        <el-table-column prop="module" label="模块名称" width="150" />
        <el-table-column prop="detail" label="操作明细" show-overflow-tooltip />
        <el-table-column prop="ip" label="IP地址" width="130" />
        <el-table-column prop="time" label="操作时间" width="180" />
      </el-table>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.expand-detail {
  padding: $spacing-base;
}
</style>
