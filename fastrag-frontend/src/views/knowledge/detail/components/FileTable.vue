<script setup lang="ts">
import type { KnowledgeFile, FileCategory, ProcessStatus, ParseStrategy } from '@/types/knowledge'
import { FILE_CATEGORY_ICONS, formatFileSize, formatDuration } from '@/types/knowledge'
import { Refresh, Search, MoreFilled, View, Download, RefreshRight, Delete, Grid, Rank, Edit, Document, Setting, MagicStick } from '@element-plus/icons-vue'
import { usePagination } from '@/composables/usePagination'
import ProcessStatusBar from './ProcessStatusBar.vue'

const props = defineProps<{
  files: KnowledgeFile[]
  folders?: Array<{ id: string; label: string; createdAt?: string; updatedAt?: string }>
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
  (e: 'metadata', file: KnowledgeFile): void
  (e: 'changeStrategy', file: KnowledgeFile): void
  (e: 'aiChunk', file: KnowledgeFile): void
  (e: 'editOffice', file: KnowledgeFile): void
  (e: 'toggleGraphBuild', file: KnowledgeFile, enabled: boolean): void
  (e: 'enterFolder', folderId: string): void
  (e: 'renameFolder', folderId: string, label: string): void
  (e: 'deleteFolder', folderId: string): void
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

// Type filter (all / folder / file)
type TypeFilter = 'all' | 'folder' | 'file'
const typeFilter = ref<TypeFilter>('all')
const typeOptions = [
  { label: '全部', value: 'all' as TypeFilter },
  { label: '文件夹', value: 'folder' as TypeFilter },
  { label: '文件', value: 'file' as TypeFilter },
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
const { currentPage, pageSize, total, handleCurrentChange, handleSizeChange } = usePagination(10)
watch(() => filteredFiles.value.length, (n) => {
  total.value = n
  currentPage.value = 1
})

// 分页后的数据
const pagedFiles = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredFiles.value.slice(start, start + pageSize.value)
})

// Folder rows: convert folders prop to row objects, always shown before file rows
interface FolderRow {
  isFolder: true
  id: string
  label: string
  createdAt?: string
  updatedAt?: string
}

const folderRows = computed<FolderRow[]>(() => {
  return (props.folders || []).map((f) => ({
    isFolder: true as const,
    id: f.id,
    label: f.label,
    createdAt: f.createdAt,
    updatedAt: f.updatedAt,
  }))
})

// Display rows: folders first, then paged files (respecting type filter)
const displayRows = computed(() => {
  const showFolders = typeFilter.value !== 'file'
  const showFiles = typeFilter.value !== 'folder'
  return [
    ...(showFolders ? folderRows.value : []),
    ...(showFiles ? pagedFiles.value : []),
  ]
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

// Handle table selection change（表格行可能含 FolderRow，但 selectable 已禁用文件夹，实际选中皆为文件）
function handleSelectionChange(selectedFiles: any[]) {
  emit('selectionChange', selectedFiles as KnowledgeFile[])
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
type Command = 'preview' | 'manageChunks' | 'download' | 'retry' | 'delete' | 'move' | 'rename' | 'copy' | 'metadata' | 'aiChunk' | 'editOffice'
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
    case 'metadata': emit('metadata', file); break
    case 'aiChunk': emit('aiChunk', file); break
    case 'editOffice': emit('editOffice', file); break
  }
}

// AI分片：文档类 word/ppt/pdf 可用；processing 置灰（disabled），failed 亦可进入（可作重处理途径）
const AI_CHUNK_EXTS = ['.pdf', '.doc', '.docx', '.ppt', '.pptx']
function showAiChunk(file: KnowledgeFile): boolean {
  if (file.category !== 'document') return false
  const ext = '.' + (file.extension || file.name.split('.').pop() || '').toLowerCase()
  return AI_CHUNK_EXTS.includes(ext)
}

// OnlyOffice 在线编辑：仅 OO 支持的扩展名 + completed/pending 状态可编辑
const OFFICE_EDIT_EXTS = ['.doc', '.docx', '.xls', '.xlsx', '.ppt', '.pptx']
function showOfficeEdit(file: KnowledgeFile): boolean {
  if (file.category !== 'document') return false
  if (file.deletedAt) return false
  const ext = '.' + (file.extension || file.name.split('.').pop() || '').toLowerCase()
  return OFFICE_EDIT_EXTS.includes(ext) && file.status !== 'processing'
}

// 换策略入口：QA 模式文件不受分片策略影响，处理中/回收站文件由对话框内守卫与后端兜底
function canChangeStrategy(file: KnowledgeFile): boolean {
  return file.processingMode !== 'qa'
}

// 点击策略 tag → 打开「换策略重新分片」对话框（由 FileManager 承载）
function handleChangeStrategy(file: KnowledgeFile) {
  emit('changeStrategy', file)
}

// 切换知识图谱开关
function handleGraphToggle(file: KnowledgeFile, enabled: boolean) {
  emit('toggleGraphBuild', file, enabled)
}

// Handle folder dropdown command
function handleFolderCommand(cmd: string, folder: FolderRow) {
  if (cmd === 'rename') emit('renameFolder', folder.id, folder.label)
  else if (cmd === 'delete') emit('deleteFolder', folder.id)
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

        <el-select
          v-model="typeFilter"
          placeholder="全部"
          class="file-table__type-select"
        >
          <el-option
            v-for="option in typeOptions"
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
      :data="displayRows"
      stripe
      row-key="id"
      :row-style="{ height: '60px' }"
      :cell-style="{ padding: '8px 0' }"
      @selection-change="handleSelectionChange"
      class="file-table__table"
    >
      <!-- Selection column -->
      <el-table-column type="selection" width="50" align="center" :selectable="(row: any) => !row.isFolder" />

      <!-- File name column with category icon -->
      <el-table-column prop="name" label="文件名" min-width="260" show-overflow-tooltip>
        <template #default="{ row }">
          <!-- Folder row -->
          <div v-if="row.isFolder" class="file-table__name-cell">
            <span class="file-table__icon">📁</span>
            <span class="file-table__name file-table__name--folder" @click="emit('enterFolder', row.id)">
              {{ row.label }}
            </span>
          </div>
          <!-- File row -->
          <div v-else class="file-table__name-cell">
            <span class="file-table__icon">{{ getCategoryIcon(row.category) }}</span>
            <span
              class="file-table__name"
              :class="{ 'file-table__name--link': row.status === 'completed' }"
              @click="handleNameClick(row as KnowledgeFile)"
            >{{ row.name }}</span>
            <el-tag v-if="(row as any).processingMode === 'qa'" type="warning" size="small" class="file-table__qa-badge">QA</el-tag>
          </div>
        </template>
      </el-table-column>

      <!-- Status column using ProcessStatusBar -->
      <el-table-column label="状态" width="220">
        <template #default="{ row }">
          <span v-if="row.isFolder" class="file-table__no-data">-</span>
          <ProcessStatusBar
            v-else
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
          <span v-if="row.isFolder" class="file-table__no-data">-</span>
          <span v-else>{{ getDurationOrPages(row as KnowledgeFile) }}</span>
        </template>
      </el-table-column>

      <!-- Parse strategy column：点击打开「换策略重新分片」对话框（ADR-0001：绑定变更与重切原子完成） -->
      <el-table-column label="处理策略" width="160" align="center">
        <template #default="{ row }">
          <span v-if="row.isFolder" class="file-table__no-data">-</span>
          <template v-else>
          <el-tag
            v-if="row.parseStrategyName"
            :type="isDefaultStrategy(row as KnowledgeFile) ? 'info' : 'primary'"
            size="small"
            :class="['file-table__strategy-tag', { 'is-clickable': canChangeStrategy(row as KnowledgeFile) }]"
            @click="canChangeStrategy(row as KnowledgeFile) && handleChangeStrategy(row as KnowledgeFile)"
          >
            {{ row.parseStrategyName }}
            <el-icon v-if="canChangeStrategy(row as KnowledgeFile)" class="file-table__strategy-caret"><Setting /></el-icon>
          </el-tag>
          <span v-else class="file-table__no-strategy">未设置</span>
          </template>
        </template>
      </el-table-column>

      <!-- Chunk count column -->
      <el-table-column label="切片数" width="80" align="center" sortable :sort-method="(a: any, b: any) => ((a as KnowledgeFile).chunkCount || 0) - ((b as KnowledgeFile).chunkCount || 0)">
        <template #default="{ row }">
          <span v-if="row.isFolder" class="file-table__no-data">-</span>
          <span v-else-if="row.chunkCount !== undefined">{{ row.chunkCount }}</span>
          <span v-else class="file-table__no-data">-</span>
        </template>
      </el-table-column>

      <!-- File size column -->
      <el-table-column label="大小" width="100" align="center" sortable :sort-method="(a: any, b: any) => (a as KnowledgeFile).size - (b as KnowledgeFile).size">
        <template #default="{ row }">
          <span v-if="row.isFolder" class="file-table__no-data">-</span>
          <span v-else>{{ formatFileSize(row.size) }}</span>
        </template>
      </el-table-column>

      <!-- 知识图谱开关列 -->
      <el-table-column label="知识图谱" width="100" align="center">
        <template #default="{ row }">
          <span v-if="row.isFolder" class="file-table__no-data">-</span>
          <el-switch
            v-else
            :model-value="(row as any).enableGraphBuild"
            :active-value="1"
            :inactive-value="0"
            @change="handleGraphToggle(row as KnowledgeFile, ($event as number) === 1)"
            size="small"
          />
        </template>
      </el-table-column>

      <!-- Upload time column -->
      <el-table-column label="上传时间" width="160" align="center" sortable :sort-method="(a: any, b: any) => ((a.isFolder ? (a.createdAt || '') : a.createdAt) || '').localeCompare((b.isFolder ? (b.createdAt || '') : b.createdAt) || '')">
        <template #default="{ row }">
          <span v-if="row.isFolder">{{ (row as FolderRow).createdAt || '-' }}</span>
          <span v-else>{{ row.createdAt }}</span>
        </template>
      </el-table-column>

      <!-- Update time column -->
      <el-table-column label="更新时间" width="160" align="center" sortable :sort-method="(a: any, b: any) => ((a.isFolder ? (a.updatedAt || '') : a.updatedAt) || '').localeCompare((b.isFolder ? (b.updatedAt || '') : b.updatedAt) || '')">
        <template #default="{ row }">
          <span v-if="row.isFolder">{{ (row as FolderRow).updatedAt || '-' }}</span>
          <span v-else>{{ row.updatedAt }}</span>
        </template>
      </el-table-column>

      <!-- Actions column -->
      <el-table-column label="操作" width="80" align="center" fixed="right">
        <template #default="{ row }">
          <!-- Folder row actions -->
          <template v-if="row.isFolder">
            <el-dropdown trigger="hover" @command="(cmd: string) => handleFolderCommand(cmd, row as FolderRow)">
              <el-button :icon="MoreFilled" link class="file-table__more-btn" />
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="rename" :icon="Edit">
                    重命名
                  </el-dropdown-item>
                  <el-dropdown-item command="delete" :icon="Delete" divided>
                    <span class="file-table__delete-text">删除</span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <!-- File row actions -->
          <template v-else>
          <el-dropdown trigger="hover" @command="(cmd: Command) => handleCommand(cmd, row as KnowledgeFile)">
            <el-button :icon="MoreFilled" link class="file-table__more-btn" />
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="preview" :icon="View">
                  预览
                </el-dropdown-item>
                <el-dropdown-item
                  v-if="showOfficeEdit(row as KnowledgeFile)"
                  command="editOffice"
                  :icon="Edit"
                >
                  在线编辑 (OnlyOffice)
                </el-dropdown-item>
                <el-dropdown-item
                  v-if="showAiChunk(row as KnowledgeFile)"
                  command="aiChunk"
                  :icon="MagicStick"
                  :disabled="(row as KnowledgeFile).status === 'processing'"
                >
                  AI分片
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
                <el-dropdown-item command="metadata" :icon="Edit">
                  元数据
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
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="file-table__pagination">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        layout="total, sizes, prev, pager, next, jumper"
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

  &__type-select {
    width: 100px;
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

    &--folder {
      cursor: pointer;
      font-weight: 500;
      color: $text-primary;

      &:hover {
        color: $color-primary;
      }
    }
  }

  &__qa-badge {
    flex-shrink: 0;
    margin-left: 6px;
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
    cursor: default;
    display: inline-flex;
    align-items: center;
    gap: 2px;

    &.is-clickable {
      cursor: pointer;

      &:hover {
        opacity: 0.8;
      }
    }
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
    margin-top: 16px;
  }
}
</style>
