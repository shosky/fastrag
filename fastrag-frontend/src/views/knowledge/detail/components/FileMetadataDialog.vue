<script setup lang="ts">
import type { KnowledgeFile } from '@/types/knowledge'
import FileMetadataEditor from './FileMetadataEditor.vue'

/**
 * 文件元数据编辑弹窗（分册四）。
 * 外壳：包一层 el-dialog；核心编辑逻辑在 FileMetadataEditor（列表弹窗与 chunks 侧栏共用）。
 */
const props = defineProps<{
  visible: boolean
  file: KnowledgeFile | null
  kbId?: string
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'saved', fileId: string): void
}>()

const dialogVisible = computed({
  get: () => props.visible,
  set: (v: boolean) => emit('update:visible', v),
})

function handleSaved(fileId: string) {
  dialogVisible.value = false
  emit('saved', fileId)
}
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :title="`文件元数据 - ${file?.name || ''}`"
    width="620px"
    :close-on-click-modal="false"
    destroy-on-close
    class="file-metadata-dialog"
  >
    <FileMetadataEditor
      v-if="dialogVisible && file && kbId"
      :kb-id="kbId"
      :file-id="file.id"
      @saved="handleSaved"
    />
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.file-metadata-dialog {
  :deep(.el-dialog__body) {
    max-height: 60vh;
    overflow-y: auto;
  }
}
</style>
