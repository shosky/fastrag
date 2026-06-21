<script setup lang="ts">
import { ref } from 'vue'

const filterStatus = ref('')

const downloadList = ref([
  { id: '1', time: '2026-06-04 10:30', username: 'admin', name: '知识库导出_产品知识库', module: '知识库', status: '成功' },
  { id: '2', time: '2026-06-03 16:45', username: '张三', name: '对话记录导出', module: '运营中心', status: '成功' },
  { id: '3', time: '2026-06-02 14:20', username: 'admin', name: '人员列表导出', module: '账号权限', status: '失败' },
])
</script>

<template>
  <div class="page-container">
    <div class="card-panel">
      <div class="section-title">下载中心</div>
      <div class="filter-bar">
        <el-date-picker type="daterange" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" style="width: 240px" />
        <el-input placeholder="名称" clearable style="width: 200px" />
        <el-select v-model="filterStatus" placeholder="状态" clearable style="width: 100px">
          <el-option label="待执行" value="待执行" />
          <el-option label="成功" value="成功" />
          <el-option label="失败" value="失败" />
        </el-select>
        <el-button type="primary">查询</el-button>
        <el-button>重置</el-button>
      </div>
      <el-table :data="downloadList" stripe>
        <el-table-column prop="time" label="导出时间" width="180" />
        <el-table-column prop="username" label="用户名" width="100" />
        <el-table-column prop="name" label="名称" show-overflow-tooltip />
        <el-table-column prop="module" label="导出模块" width="120" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === '成功' ? 'success' : row.status === '失败' ? 'danger' : 'warning'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button link type="primary" size="small" :disabled="row.status !== '成功'">下载</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>
