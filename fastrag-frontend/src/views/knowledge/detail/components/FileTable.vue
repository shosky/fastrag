<script setup lang="ts">
import type { KnowledgeFile, FileCategory, ProcessStatus, ParseStrategy } from '@/types/knowledge'
import { FILE_CATEGORY_ICONS, formatFileSize, formatDuration } from '@/types/knowledge'
import { Refresh, Search, MoreFilled, View, Download, RefreshRight, Delete, Grid, Rank, Edit, Document, Setting } from '@element-plus/icons-vue'
import { usePagination } from '@/composables/usePagination'
import ProcessStatusBar from './ProcessStatusBar.vue'

const props = defineProps<{
  files: KnowledgeFile[]
  loading?: boolean
  kbId?: string
  strategies?: ParseStrategy[]
}>()

const emit = defineEmits<{
  (e: 'preview', file: KnowledgeFile): void
  (e: 'download', file: KnowledgeFile): void
  (e: 'delete', file: KnowledgeFile): void
  (e: 'retry', file: KnowledgeFile): void
  (e: 'refresh'): void
  (e: 'selectionChange', files: KnowledgeFile[]): void
  (e: 'manageChunks', file: KnowledgeFile): void
  (e: 'move', file: KnowledgeFile): void
  (e: 'rename', file: KnowledgeFile): void
  (e: 'copy', file: KnowledgeFile): void
  (e: 'changeStrategy', file: KnowledgeFile, strategyId: string, strategyName: string): void
}>()

// Filter state
const searchText = ref('')
const categoryFilter = ref<FileCategory | ''>('')
const statusFilter = ref<ProcessStatus | ''>('')

// Category options for the dropdown
const categoryOptions = [
  { label: '全部类型', value: '' },
  { label: '文档', value: 'document' as FileCategory },
  { label: '图片', value: 'image' as FileCategory },
  { label: '音频', value: 'audio' as FileCategory },
  { label: '视频', value: 'video' as FileCategory },
]

// Status options for the dropdown
const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '待处理', value: 'pending' as ProcessStatus },
  { label: '处理中', value: 'processing' as ProcessStatus },
  { label: '已完成', value: 'completed' as ProcessStatus },
  { label: '失败', value: 'failed' as ProcessStatus },
]

// Filtered files based on search and filters
const filteredFiles = computed(() => {
  return props.files.filter((file) => {
    // Search filter
    if (searchText.value && !file.name.toLowerCase().includes(searchText.value.toLowerCase())) {
      return false
    }
    // Category filter
    if (categoryFilter.value && file.category !== categoryFilter.value) {
      return false
    }
    // Status filter
    if (statusFilter.value && file.status !== statusFilter.value) {
      return false
    }
    return true
  })
})

// 分页（真实切片）
const { currentPage, pageSize, total, handleCurrentChange, handleSizeChange } = usePagination(20)
watch(() => filteredFiles.value.length, (n) => {
  total.value = n
  currentPage.value = 1
})

// 分页后的数据
const pagedFiles = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredFiles.value.slice(start, start + pageSize.value)
})

// Get category icon for a file
function getCategoryIcon(category: FileCategory): string {
  return FILE_CATEGORY_ICONS[category] || '📄'
}

// Get display text for duration/pages column
function getDurationOrPages(file: KnowledgeFile): string {
  if (file.category === 'audio' || file.category === 'video') {
    return file.duration ? formatDuration(file.duration) : '-'
  }
  if (file.category === 'document') {
    return file.pages ? `${file.pages} 页` : '-'
  }
  return '-'
}

// Determine if retry action should be shown
function showRetry(file: KnowledgeFile): boolean {
  return file.status === 'failed'
}

// 策略是否为默认（基于 id 匹配默认策略，而非字符串 includes）
function isDefaultStrategy(file: KnowledgeFile): boolean {
  if (!props.strategies) return file.parseStrategyName?.includes('默认') || false
  const def = props.strategies.find((s) => s.isDefault)
  return def ? file.parseStrategyId === def.id : false
}

// Handle table selection change
function handleSelectionChange(selectedFiles: KnowledgeFile[]) {
  emit('selectionChange', selectedFiles)
}

// Handle file name click - navigate to chunk management
function handleNameClick(file: KnowledgeFile) {
  if (file.status === 'completed') {
    emit('manageChunks', file)
  }
}

// Handle refresh click
function handleRefresh() {
  emit('refresh')
}

// Handle dropdown command
type Command = 'preview' | 'manageChunks' | 'download' | 'retry' | 'delete' | 'move' | 'rename' | 'copy'
function handleCommand(command: Command, file: KnowledgeFile) {
  switch (command) {
    case 'preview': emit('preview', file); break
    case 'manageChunks': emit('manageChunks', file); break
    case 'download': emit('download', file); break
    case 'retry': emit('retry', file); break
    case 'delete': emit('delete', file); break
    case 'move': emit('move', file); break
    case 'rename': emit('rename', file); break
    case 'copy': emit('copy', file); break
  }
}

// 修改策略
function handleChangeStrategy(file: KnowledgeFile, strategyId: string) {
  const s = props.strategies?.find((x) => x.id === strategyId)
  if (s) {
    emit('changeStrategy', file, s.id, s.name)
  }
}
</script>

<template>
  <div class="file-table">
    <!-- Toolbar: search, filters, refresh -->
    <div class="file-table__toolbar">
      <div class="file-table__filters">
        <el-input
          v-model="searchText"
          placeholder="搜索文件名"
          clearable
          class="file-table__search"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>

        <el-select
          v-model="categoryFilter"
          placeholder="全部类型"
          clearable
          class="file-table__category-select"
        >
          <el-option
            v-for="option in categoryOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>

        <el-select
          v-model="statusFilter"
          placeholder="全部状态"
          clearable
          class="file-table__status-select"
        >
          <el-option
            v-for="option in statusOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </div>

      <el-button :icon="Refresh" circle @click="handleRefresh" />
    </div>

    <!-- File table -->
    <el-table
      v-loading="loading"
      :data="pagedFiles"
      stripe
      row-key="id"
      :row-style="{ height: '60px' }"
      :cell-style="{ padding: '8px 0' }"
      @selection-change="handleSelectionChange"
      class="file-table__table"
    >
      <!-- Selection column -->
      <el-table-column type="selection" width="50" align="center" />

      <!-- File name column with category icon -->
      <el-table-column prop="name" label="文件名" min-width="260" show-overflow-tooltip>
        <template #default="{ row }">
          <div class="file-table__name-cell">
            <span class="file-table__icon">{{ getCategoryIcon(row.category) }}</span>
            <span
              class="file-table__name"
              :class="{ 'file-table__name--link': row.status === 'completed' }"
              @click="handleNameClick(row as KnowledgeFile)"
            >{{ row.name }}</span>
          </div>
        </template>
      </el-table-column>

      <!-- Status column using ProcessStatusBar -->
      <el-table-column label="状态" width="220">
        <template #default="{ row }">
          <ProcessStatusBar
            :status="row.status"
            :progress="row.progress"
            :stage="row.stage"
            :category="row.category"
          />
        </template>
      </el-table-column>

      <!-- Duration / Pages column -->
      <el-table-column label="时长/页数" width="100" align="center">
        <template #default="{ row }">
          {{ getDurationOrPages(row as KnowledgeFile) }}
        </template>
      </el-table-column>

      <!-- Parse strategy column -->
      <el-table-column label="处理策略" width="160" align="center">
        <template #default="{ row }">
          <el-dropdown
            v-if="row.parseStrategyName"
            trigger="click"
            @command="(cmd: string) => handleChangeStrategy(row as KnowledgeFile, cmd)"
          >
            <el-tag
              :type="isDefaultStrategy(row as KnowledgeFile) ? 'info' : 'primary'"
              size="small"
              class="file-table__strategy-tag"
            >
              {{ row.parseStrategyName }}
              <el-icon class="file-table__strategy-caret"><Setting /></el-icon>
            </el-tag>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item
                  v-for="s in strategies"
                  :key="s.id"
                  :command="s.id"
                >
                  {{ s.name }}
                  <el-tag v-if="s.isDefault" size="small" type="info" class="file-table__default-tag">默认</el-tag>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <span v-else class="file-table__no-strategy">未设置</span>
        </template>
      </el-table-column>

      <!-- Chunk count column -->
      <el-table-column label="切片数" width="80" align="center" sortable :sort-method="(a: any, b: any) => ((a as KnowledgeFile).chunkCount || 0) - ((b as KnowledgeFile).chunkCount || 0)">
        <template #default="{ row }">
          <span v-if="row.chunkCount !== undefined">{{ row.chunkCount }}</span>
          <span v-else class="file-table__no-data">-</span>
        </template>
      </el-table-column>

      <!-- File size column -->
      <el-table-column label="大小" width="100" align="center" sortable :sort-method="(a: any, b: any) => (a as KnowledgeFile).size - (b as KnowledgeFile).size">
        <template #default="{ row }">
          {{ formatFileSize(row.size) }}
        </template>
      </el-table-column>

      <!-- Upload time column -->
      <el-table-column label="上传时间" width="160" align="center" sortable :sort-method="(a: any, b: any) => (a as KnowledgeFile).createdAt.localeCompare((b as KnowledgeFile).createdAt)">
        <template #default="{ row }">
          {{ row.createdAt }}
        </template>
      </el-table-column>

      <!-- Update time column -->
      <el-table-column label="更新时间" width="160" align="center" sortable :sort-method="(a: any, b: any) => (a as KnowledgeFile).updatedAt.localeCompare((b as KnowledgeFile).updatedAt)">
        <template #default="{ row }">
          {{ row.updatedAt }}
        </template>
      </el-table-column>

      <!-- Actions column -->
      <el-table-column label="操作" width="80" align="center" fixed="right">
        <template #default="{ row }">
          <el-dropdown trigger="hover" @command="(cmd: Command) => handleCommand(cmd, row as KnowledgeFile)">
            <el-button :icon="MoreFilled" link class="file-table__more-btn" />
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="preview" :icon="View">
                  预览
                </el-dropdown-item>
                <el-dropdown-item
                  v-if="row.status === 'completed'"
                  command="manageChunks"
                  :icon="Grid"
                >
                  分片管理
                </el-dropdown-item>
                <!-- 失败文件不显示下载 -->
                <el-dropdown-item v-if="row.status !== 'failed'" command="download" :icon="Download">
                  下载
                </el-dropdown-item>
                <el-dropdown-item command="rename" :icon="Edit">
                  重命名
                </el-dropdown-item>
                <el-dropdown-item command="copy" :icon="Document">
                  复制
                </el-dropdown-item>
                <el-dropdown-item command="move" :icon="Rank">
                  移动
                </el-dropdown-item>
                <el-dropdown-item
                  v-if="showRetry(row as KnowledgeFile)"
                  command="retry"
                  :icon="RefreshRight"
                  divided
                >
                  重试
                </el-dropdown-item>
                <el-dropdown-item command="delete" :icon="Delete" divided>
                  <span class="file-table__delete-text">删除</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="file-table__pagination">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        layout="total, prev, pager, next, sizes"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        @current-change="handleCurrentChange"
        @size-change="handleSizeChange"
      />
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.file-table {
  display: flex;
  flex-direction: column;
  gap: 16px;

  &__toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
  }

  &__filters {
    display: flex;
    align-items: center;
    gap: 12px;
    flex: 1;
  }

  &__search {
    width: 240px;
  }

  &__category-select {
    width: 120px;
  }

  &__status-select {
    width: 120px;
  }

  &__table {
    width: 100%;

    :deep(.el-table__cell) {
      vertical-align: middle;
    }
  }

  &__name-cell {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  &__icon {
    font-size: 18px;
    flex-shrink: 0;
  }

  &__name {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;

    &--link {
      color: $color-primary;
      cursor: pointer;

      &:hover {
        text-decoration: underline;
      }
    }
  }

  &__more-btn {
    font-size: 18px;
    color: $text-secondary;

    &:hover {
      color: $color-primary;
    }
  }

  &__delete-text {
    color: $color-danger;
  }

  &__strategy-tag {
    cursor: pointer;
    display: inline-flex;
    align-items: center;
    gap: 2px;
  }

  &__strategy-caret {
    font-size: 12px;
  }

  &__default-tag {
    margin-left: 8px;
  }

  &__no-strategy {
    font-size: 12px;
    color: $text-secondary;
  }

  &__no-data {
    color: $text-secondary;
  }

  &__pagination {
    display: flex;
    justify-content: flex-end;
  }
}
</style>
