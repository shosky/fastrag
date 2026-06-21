<script setup lang="ts">
import type { KnowledgeFile } from '@/types/knowledge'
import { formatFileSize } from '@/types/knowledge'
import { Delete, RefreshRight, WarningFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

// --- Props & Emits ---
const props = defineProps<{
  visible: boolean
  deletedFiles: KnowledgeFile[]
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'restore', fileId: string): void
  (e: 'permanent-delete', fileId: string): void
  (e: 'empty'): void
}>()

// Dialog visibility
const dialogVisible = computed({
  get: () => props.visible,
  set: (val: boolean) => emit('update:visible', val),
})

// --- 恢复 ---
async function handleRestore(file: KnowledgeFile) {
  emit('restore', file.id)
  ElMessage.success(`已恢复「${file.name}」`)
}

// --- 彻底删除 ---
async function handlePermanentDelete(file: KnowledgeFile) {
  try {
    await ElMessageBox.confirm(
      `确定要彻底删除「${file.name}」吗？此操作不可恢复！`,
      '彻底删除确认',
      { confirmButtonText: '彻底删除', cancelButtonText: '取消', type: 'warning' },
    )
    emit('permanent-delete', file.id)
    ElMessage.success(`已彻底删除「${file.name}」`)
  } catch {
    // 用户取消
  }
}

// --- 清空回收站 ---
async function handleEmpty() {
  if (props.deletedFiles.length === 0) return
  try {
    await ElMessageBox.confirm(
      `确定要清空回收站中的全部 ${props.deletedFiles.length} 个文件吗？此操作不可恢复！`,
      '清空回收站',
      { confirmButtonText: '清空', cancelButtonText: '取消', type: 'warning' },
    )
    emit('empty')
    ElMessage.success('回收站已清空')
  } catch {
    // 用户取消
  }
}
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    title="回收站"
    width="680px"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <!-- 空状态 -->
    <el-empty
      v-if="deletedFiles.length === 0"
      description="回收站为空"
      :image-size="100"
    />

    <template v-else>
      <div class="recycle-bin__header">
        <span class="recycle-bin__count">共 {{ deletedFiles.length }} 个文件</span>
        <el-button type="danger" link @click="handleEmpty">
          <el-icon><Delete /></el-icon>
          清空回收站
        </el-button>
      </div>

      <el-table :data="deletedFiles" stripe size="small" max-height="400">
        <el-table-column prop="name" label="文件名" min-width="200" show-overflow-tooltip />
        <el-table-column label="大小" width="90" align="right">
          <template #default="{ row }">
            {{ formatFileSize(row.size) }}
          </template>
        </el-table-column>
        <el-table-column prop="deletedAt" label="删除时间" width="170" />
        <el-table-column label="操作" width="160" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleRestore(row as KnowledgeFile)">
              <el-icon><RefreshRight /></el-icon>
              恢复
            </el-button>
            <el-button type="danger" link size="small" @click="handlePermanentDelete(row as KnowledgeFile)">
              <el-icon><Delete /></el-icon>
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.recycle-bin {
  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: $spacing-base;
  }

  &__count {
    font-size: 13px;
    color: $text-secondary;
  }
}
</style>
