<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import * as api from '@/api'
import { usePagination } from '@/composables/usePagination'

const searchKeyword = ref('')
const filterCategory = ref('audit')
const filterModule = ref('')
const loading = ref(false)

const modules = ['登录认证', '知识库', '知识检索', '知识发布', '知识加工', '应用中心', '系统与权限管理']

const logList = ref<any[]>([])

const {
  currentPage,
  pageSize,
  total,
  handleCurrentChange,
  handleSizeChange,
} = usePagination(20)

function formatTime(ts: string): string {
  if (!ts) return '-'
  return ts.replace('T', ' ').substring(0, 19)
}

async function loadLogs() {
  loading.value = true
  try {
    const params: any = {
      category: filterCategory.value,
      keyword: searchKeyword.value || undefined,
      module: filterModule.value || undefined,
      page: currentPage.value,
      pageSize: pageSize.value,
    }
    const res: any = await api.getLogs(params)
    const pageData = res?.records || res?.list || res || []
    logList.value = Array.isArray(pageData) ? pageData : []
    total.value = res?.total || res?.count || logList.value.length
  } catch (e) {
    logList.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

onMounted(loadLogs)

// 切换分类时重置到第一页
watch(filterCategory, () => {
  currentPage.value = 1
  loadLogs()
})

async function handleSearch() {
  currentPage.value = 1
  await loadLogs()
}

function handleReset() {
  searchKeyword.value = ''
  filterModule.value = ''
  filterCategory.value = 'audit'
  currentPage.value = 1
  loadLogs()
}
</script>

<template>
  <div class="page-container">
    <div class="card-panel">
      <div class="section-title">系统日志</div>

      <!-- 分类标签 -->
      <el-radio-group v-model="filterCategory" style="margin-bottom: 16px">
        <el-radio-button value="audit">审计日志</el-radio-button>
        <el-radio-button value="login">登录日志</el-radio-button>
        <el-radio-button value="operation">操作日志</el-radio-button>
      </el-radio-group>

      <div class="filter-bar">
        <el-input v-model="searchKeyword" placeholder="搜索关键词" clearable style="width: 200px" @clear="handleSearch" />
        <el-select v-model="filterModule" placeholder="模块名称" clearable style="width: 150px">
          <el-option v-for="m in modules" :key="m" :label="m" :value="m" />
        </el-select>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>

      <el-table :data="logList" stripe v-loading="loading">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="expand-detail">
              <el-descriptions :column="3" border size="small">
                <el-descriptions-item label="日志ID">{{ row.id }}</el-descriptions-item>
                <el-descriptions-item label="分类">{{ row.category }}</el-descriptions-item>
                <el-descriptions-item label="动作">
                  <el-tag size="small">{{ row.action || '-' }}</el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="目标">{{ row.target || '-' }}</el-descriptions-item>
                <el-descriptions-item label="状态" v-if="row.status">
                  <el-tag :type="row.status === 'success' ? 'success' : 'danger'" size="small">{{ row.status }}</el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="IP地址">{{ row.ip || '-' }}</el-descriptions-item>
              </el-descriptions>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="username" label="用户名" width="100" />
        <el-table-column prop="module" label="模块" width="120" />
        <el-table-column prop="action" label="动作" width="160" />
        <el-table-column prop="detail" label="操作明细" show-overflow-tooltip />
        <el-table-column prop="ip" label="IP地址" width="130" />
        <el-table-column label="操作时间" width="180">
          <template #default="{ row }">{{ formatTime(row.timestamp) }}</template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="system-log__pagination">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="handleCurrentChange"
          @size-change="handleSizeChange"
        />
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.expand-detail {
  padding: $spacing-base;
}

.system-log__pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
