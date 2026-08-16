<script setup lang="ts">
import { nextTick, onMounted, watch } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import * as api from '@/api'
import { usePagination } from '@/composables/usePagination'

const props = defineProps<{
  kbId: string
}>()

// --- 筛选 ---
const activeCategory = ref<string>('all')
const searchKeyword = ref('')

const logs = ref<any[]>([])
const loading = ref(false)

// --- 分类统计（服务端分页后由接口返回） ---
const stats = ref<Record<string, number>>({ total: 0, operation: 0, retrieval: 0, publish: 0 })

// --- 分页（统一 usePagination） ---
const {
  currentPage,
  pageSize,
  total,
} = usePagination(10)

// EP 在切换 page-size 且当前页超出新页数时会同步补发一次 current-change，
// 用该标志跳过这次多余的翻页请求，保持「切 size 回到第一页」的约定
let suppressCurrentChange = false

async function fetchLogs() {
  loading.value = true
  try {
    const params: any = { page: currentPage.value, pageSize: pageSize.value }
    if (activeCategory.value !== 'all') params.category = activeCategory.value
    const keyword = searchKeyword.value.trim()
    if (keyword) params.keyword = keyword
    const res = await api.getKbLogs(props.kbId, params)
    logs.value = (res as any)?.list || []
    total.value = Number((res as any)?.total ?? logs.value.length)
  } finally {
    loading.value = false
  }
}

// 分类统计独立拉取，失败时保留默认值，不影响日志列表
async function fetchStats() {
  try {
    const statsRes = await api.getKbLogStats(props.kbId)
    stats.value = { total: 0, operation: 0, retrieval: 0, publish: 0, ...(statsRes as any) }
  } catch {
    // 忽略统计接口异常
  }
}

function refresh() {
  fetchLogs()
  fetchStats()
}

// 页码变化时重新拉取
function handleCurrentChange(page: number) {
  if (suppressCurrentChange) {
    // 切 page-size 时 EP 补发的 current-change，跳过并保持第一页
    suppressCurrentChange = false
    currentPage.value = 1
    return
  }
  currentPage.value = page
  fetchLogs()
}

// 每页条数变化时重置到第一页并拉取
function handleSizeChange(size: number) {
  pageSize.value = size
  currentPage.value = 1
  suppressCurrentChange = true
  nextTick(() => { suppressCurrentChange = false })
  fetchLogs()
}

// 切换分类：回到第一页并刷新
watch(activeCategory, () => {
  currentPage.value = 1
  fetchLogs()
})

// 搜索：300ms 防抖，回到第一页并刷新（服务端搜索）
let searchTimer: ReturnType<typeof setTimeout> | undefined
watch(searchKeyword, () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    currentPage.value = 1
    fetchLogs()
  }, 300)
})

onMounted(refresh)

// --- 类型配置 ---
const categoryConfig: Record<string, { label: string; color: 'primary' | 'success' | 'warning' | 'danger' | 'info' }> = {
  operation: { label: '操作', color: 'primary' },
  retrieval: { label: '检索', color: 'success' },
  publish: { label: '发布', color: 'warning' },
}

const actionLabels: Record<string, string> = {
  file_added: '新增文件',
  file_removed: '删除文件',
  file_updated: '更新文件',
  chunk_added: '新增切片',
  chunk_removed: '删除切片',
  chunk_updated: '更新切片',
  config_changed: '配置变更',
  search: '检索查询',
  version_created: '创建版本',
  submitted: '提交审核',
  approved: '审核通过',
  rejected: '审核驳回',
  published: '版本发布',
  reverted: '版本回退',
}

function getActionLabel(action: string): string {
  return actionLabels[action] || action
}
</script>

<template>
  <div class="log-panel">
    <!-- 筛选栏 -->
    <div class="log-panel__filter">
      <el-radio-group v-model="activeCategory" size="small">
        <el-radio-button value="all">全部 ({{ stats.total }})</el-radio-button>
        <el-radio-button value="operation">操作 ({{ stats.operation }})</el-radio-button>
        <el-radio-button value="retrieval">检索 ({{ stats.retrieval }})</el-radio-button>
        <el-radio-button value="publish">发布 ({{ stats.publish }})</el-radio-button>
      </el-radio-group>
      <div class="log-panel__filter-actions">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索对象/详情/操作人"
          clearable
          size="small"
          style="width: 220px"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-tooltip content="刷新" placement="top">
          <el-button size="small" circle :icon="Refresh" :loading="loading" @click="refresh" />
        </el-tooltip>
      </div>
    </div>

    <!-- 日志表格 -->
    <el-table v-loading="loading" :data="logs" stripe size="small">
      <el-table-column label="类型" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="categoryConfig[(row as any).category]?.color || 'info'" size="small">
            {{ categoryConfig[(row as any).category]?.label || (row as any).category }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          {{ getActionLabel((row as any).action) }}
        </template>
      </el-table-column>

      <el-table-column label="对象" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          {{ (row as any).target }}
        </template>
      </el-table-column>

      <el-table-column label="详情" min-width="200">
        <template #default="{ row }">
          <div class="log-panel__detail">
            <span>{{ (row as any).detail }}</span>
            <!-- 检索日志扩展信息 -->
            <template v-if="(row as any).category === 'retrieval' && (row as any).extra">
              <span class="log-panel__extra">
                模式: {{ (row as any).extra?.mode }} · TopK: {{ (row as any).extra?.topK }} · 耗时: {{ ((row as any).extra?.duration / 1000)?.toFixed(1) }}s
              </span>
            </template>
            <!-- diff 信息 -->
            <template v-if="(row as any).extra?.oldValue">
              <span class="log-panel__diff">
                <span class="log-panel__diff-old">{{ (row as any).extra?.oldValue }}</span>
                →
                <span class="log-panel__diff-new">{{ (row as any).extra?.newValue }}</span>
              </span>
            </template>
          </div>
        </template>
      </el-table-column>

      <el-table-column label="操作人" width="90">
        <template #default="{ row }">
          {{ (row as any).operator }}
        </template>
      </el-table-column>

      <el-table-column label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag
            v-if="(row as any).status"
            :type="(row as any).status === 'success' ? 'success' : (row as any).status === '已发布' ? 'primary' : 'info'"
            size="small"
          >
            {{ (row as any).status }}
          </el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>

      <el-table-column label="时间" width="160">
        <template #default="{ row }">
          {{ (row as any).timestamp || (row as any).createdAt }}
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="logs.length === 0 && !loading" description="暂无日志记录" />

    <!-- 分页（统一模板） -->
    <div class="log-panel__pagination">
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
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.log-panel {
  display: flex;
  flex-direction: column;
  gap: $spacing-base;

  // 统一分页样式（AGENTS.md 规范，直接复制）
  &__pagination {
    display: flex;
    justify-content: flex-end;
    margin-top: 16px;
  }
}

.log-panel__filter {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $spacing-base;
}

.log-panel__filter-actions {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
}

.log-panel__detail {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.log-panel__extra {
  font-size: 11px;
  color: $text-secondary;
}

.log-panel__diff {
  font-size: 11px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.log-panel__diff-old {
  color: $color-danger;
  text-decoration: line-through;
}

.log-panel__diff-new {
  color: $color-success;
}
</style>
