<script setup lang="ts">
import type { KnowledgeFile } from '@/types/knowledge'
import { FolderOpened, Folder } from '@element-plus/icons-vue'
import { useFiles } from '@/composables/useFiles'

// --- Props & Emits ---
const props = defineProps<{
  visible: boolean
  kbId?: string
  file: KnowledgeFile | null
  title?: string
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'confirm', fileId: string, targetFolderId: string): void
}>()

// 从 composable 取真实文件夹树（替代硬编码）
const { folders } = useFiles(props.kbId || 'default')

// Dialog visibility
const dialogVisible = computed({
  get: () => props.visible,
  set: (val: boolean) => emit('update:visible', val),
})

// Selected folder
const selectedFolderId = ref('root')

// 对话框打开时重置选择到根目录（避免上次选择残留）
watch(
  () => props.visible,
  (val) => {
    if (val) {
      selectedFolderId.value = 'root'
    }
  },
)

// 是否选中的是当前文件所在文件夹（禁用确定）
const isCurrentFolder = computed(() => {
  return props.file?.folderId === selectedFolderId.value
})

// Handle node click
function handleNodeClick(data: { id: string }) {
  selectedFolderId.value = data.id
}

// Confirm move
function handleConfirm() {
  if (!props.file || isCurrentFolder.value) return
  emit('confirm', props.file.id, selectedFolderId.value)
  dialogVisible.value = false
}

// Cancel
function handleCancel() {
  dialogVisible.value = false
}
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :title="title || '移动文件'"
    width="480px"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <div class="move-file-dialog">
      <div class="move-file-dialog__file-info" v-if="file && !title">
        <el-icon :size="16"><Document /></el-icon>
        <span class="move-file-dialog__file-name">{{ file.name }}</span>
      </div>

      <div class="move-file-dialog__hint">请选择目标文件夹</div>

      <el-tree
        :data="folders"
        :props="{ label: 'label', children: 'children' }"
        node-key="id"
        default-expand-all
        highlight-current
        :current-node-key="selectedFolderId"
        @node-click="handleNodeClick"
        class="move-file-dialog__tree"
      >
        <template #default="{ node }">
          <div class="move-file-dialog__tree-node">
            <el-icon class="move-file-dialog__tree-icon">
              <FolderOpened />
            </el-icon>
            <span>{{ node.label }}</span>
          </div>
        </template>
      </el-tree>

      <!-- 当前文件夹提示 -->
      <div v-if="isCurrentFolder" class="move-file-dialog__warning">
        该文件已在此文件夹中，请选择其他文件夹
      </div>
    </div>

    <template #footer>
      <div class="move-file-dialog__footer">
        <el-button @click="handleCancel">取消</el-button>
        <el-button type="primary" :disabled="isCurrentFolder" @click="handleConfirm">
          确定移动
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.move-file-dialog {
  &__file-info {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    padding: $spacing-sm $spacing-base;
    background: $bg-hover;
    border-radius: $radius-base;
    margin-bottom: $spacing-base;
  }

  &__file-name {
    font-size: 14px;
    color: $text-primary;
    font-weight: 500;
  }

  &__hint {
    font-size: 13px;
    color: $text-secondary;
    margin-bottom: $spacing-base;
  }

  &__tree {
    border: 1px solid $border-lighter;
    border-radius: $radius-base;
    padding: $spacing-sm;
    max-height: 300px;
    overflow-y: auto;
  }

  &__tree-node {
    display: flex;
    align-items: center;
    gap: $spacing-xs;
    font-size: 14px;
  }

  &__tree-icon {
    color: $color-warning;
  }

  &__warning {
    margin-top: $spacing-base;
    padding: $spacing-sm $spacing-base;
    background: #fff3e0;
    color: $color-warning;
    border-radius: $radius-sm;
    font-size: 13px;
  }

  &__footer {
    display: flex;
    justify-content: flex-end;
    gap: $spacing-sm;
  }
}
</style>
