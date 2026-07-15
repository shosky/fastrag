<script setup lang="ts">
import type { KnowledgeFile } from '@/types/knowledge'
import type { FolderNode } from '@/mock/files'
import { FolderOpened, Folder } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import * as api from '@/api'

// --- Props & Emits ---
const props = defineProps<{
  visible: boolean
  kbId?: string
  file: KnowledgeFile | null
  folders?: FolderNode[]
  title?: string
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'confirm', fileId: string, targetFolderId: string, targetKbId?: string): void
}>()

// KB list & selection
const kbList = ref<Array<{ id: string; name: string }>>([])
const selectedKbId = ref('')
const loadingFolders = ref(false)
const targetFolders = ref<FolderNode[]>([])

// Load KB list when dialog opens
watch(() => props.visible, async (val) => {
  if (val) {
    selectedKbId.value = props.kbId || ''
    targetFolders.value = props.folders || []
    try {
      const res: any = await api.getKnowledgeBases({ pageSize: 100 })
      const list = res?.list || res || []
      kbList.value = list.map((kb: any) => ({ id: kb.id, name: kb.name }))
    } catch {
      kbList.value = []
    }
  }
})

// Load folders when KB changes
watch(selectedKbId, async (newKbId) => {
  if (!newKbId) return
  if (newKbId === props.kbId) {
    // 当前 KB，使用传入的 folders
    targetFolders.value = props.folders || []
    return
  }
  // 其他 KB，从 API 加载文件夹树
  loadingFolders.value = true
  try {
    const res: any = await api.fetchFolders(newKbId)
    targetFolders.value = (res as any) || []
  } catch {
    targetFolders.value = []
    ElMessage.warning('加载目标知识库文件夹失败')
  } finally {
    loadingFolders.value = false
  }
})

// Dialog visibility
const dialogVisible = computed({
  get: () => props.visible,
  set: (val: boolean) => emit('update:visible', val),
})

// Selected folder
const selectedFolderId = ref('root')

// 对话框打开时重置选择到根目录
watch(
  () => props.visible,
  (val) => {
    if (val) {
      selectedFolderId.value = 'root'
      selectedKbId.value = props.kbId || ''
    }
  },
)

// 是否选中的是当前文件所在文件夹（仅同 KB 移动时判断）
const isCurrentFolder = computed(() => {
  if (selectedKbId.value !== props.kbId) return false
  return props.file?.folderId === selectedFolderId.value
})

// Is cross-KB move
const isCrossKb = computed(() => {
  return selectedKbId.value && selectedKbId.value !== props.kbId
})

// Handle node click
function handleNodeClick(data: { id: string }) {
  selectedFolderId.value = data.id
}

// Confirm move
function handleConfirm() {
  if (!props.file) return
  if (isCrossKb.value && !selectedKbId.value) {
    ElMessage.warning('请选择目标知识库')
    return
  }
  if (isCurrentFolder.value) return
  emit('confirm', props.file.id, selectedFolderId.value, selectedKbId.value || props.kbId)
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

      <!-- KB selector -->
      <div class="move-file-dialog__kb-select">
        <span class="move-file-dialog__label">目标知识库</span>
        <el-select v-model="selectedKbId" placeholder="选择知识库" style="width: 100%">
          <el-option
            v-for="kb in kbList"
            :key="kb.id"
            :label="kb.name"
            :value="kb.id"
          />
        </el-select>
      </div>

      <div class="move-file-dialog__hint">
        <template v-if="isCrossKb">
          将移动到其他知识库，文件将重新处理
        </template>
        <template v-else>
          请选择目标文件夹
        </template>
      </div>

      <el-tree
        :data="targetFolders"
        :props="{ label: 'label', children: 'children' }"
        node-key="id"
        default-expand-all
        highlight-current
        :current-node-key="selectedFolderId"
        @node-click="handleNodeClick"
        class="move-file-dialog__tree"
        v-loading="loadingFolders"
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

      <!-- Cross-KB warning -->
      <div v-if="isCrossKb" class="move-file-dialog__warning move-file-dialog__warning--info">
        跨知识库移动后，文件需要重新处理，旧知识库中的分片和向量将被清理
      </div>

      <!-- Current folder hint -->
      <div v-else-if="isCurrentFolder" class="move-file-dialog__warning">
        该文件已在此文件夹中，请选择其他文件夹
      </div>
    </div>

    <template #footer>
      <div class="move-file-dialog__footer">
        <el-button @click="handleCancel">取消</el-button>
        <el-button type="primary" :disabled="isCurrentFolder" @click="handleConfirm">
          {{ isCrossKb ? '移动并重新处理' : '确定移动' }}
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

  &__kb-select {
    margin-bottom: $spacing-base;
  }

  &__label {
    display: block;
    font-size: 13px;
    color: $text-secondary;
    margin-bottom: $spacing-xs;
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
    max-height: 260px;
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

    &--info {
      background: #e3f2fd;
      color: #1e88e5;
    }
  }

  &__footer {
    display: flex;
    justify-content: flex-end;
    gap: $spacing-sm;
  }
}
</style>
