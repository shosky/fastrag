<script setup lang="ts">
import * as api from '@/api'

const props = defineProps<{
  kbId: string
}>()

// --- 筛选 ---
const activeCategory = ref<string>('all')
const searchKeyword = ref('')

const allLogs = ref<any[]>([])
const loading = ref(false)

async function refresh() {
  loading.value = true
  try {
    const params: any = { page: 1, pageSize: 100 }
    if (activeCategory.value !== 'all') params.category = activeCategory.value
    const res = await api.getKbLogs(props.kbId, params)
    allLogs.value = (res as any)?.list || (res as any) || []
  } finally {
    loading.value = false
  }
}

watch(activeCategory, refresh)
onMounted(refresh)

const filteredLogs = computed(() => {
  if (!searchKeyword.value) return allLogs.value
  const kw = searchKeyword.value.toLowerCase()
  return allLogs.value.filter((l: any) =>
    (l.target || '').toLowerCase().includes(kw) ||
    (l.detail || '').toLowerCase().includes(kw) ||
    (l.operator || '').toLowerCase().includes(kw),
  )
})

// --- 统计 ---
const stats = computed(() => {
  const all = allLogs.value
  return {
    total: all.length,
    operation: all.filter((l: any) => l.category === 'operation').length,
    retrieval: all.filter((l: any) => l.category === 'retrieval').length,
    publish: all.filter((l: any) => l.category === 'publish').length,
  }
})

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
      <el-input
        v-model="searchKeyword"
        placeholder="搜索对象/详情/操作人"
        clearable
        size="small"
        style="width: 220px"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
    </div>

    <!-- 日志表格 -->
    <el-table v-loading="loading" :data="filteredLogs" stripe size="small">
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

    <el-empty v-if="filteredLogs.length === 0 && !loading" description="暂无日志记录" />
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.log-panel {
  display: flex;
  flex-direction: column;
  gap: $spacing-base;
}

.log-panel__filter {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $spacing-base;
  background: $bg-white;
  border-radius: $radius-base;
  padding: $spacing-sm $spacing-base;
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
