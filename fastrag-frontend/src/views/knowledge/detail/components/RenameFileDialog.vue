<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import type { KnowledgeFile } from '@/types/knowledge'

const props = defineProps<{
  visible: boolean
  file: KnowledgeFile | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'confirm', fileId: string, newName: string): void
}>()

// Dialog visibility
const dialogVisible = computed({
  get: () => props.visible,
  set: (val: boolean) => emit('update:visible', val),
})

// New name input
const newName = ref('')

// Watch file change to set initial name
watch(() => props.file, (file) => {
  if (file) {
    // Remove extension from name
    const lastDotIndex = file.name.lastIndexOf('.')
    newName.value = lastDotIndex > 0 ? file.name.substring(0, lastDotIndex) : file.name
  }
}, { immediate: true })

// Get file extension
const fileExtension = computed(() => {
  if (props.file) {
    const lastDotIndex = props.file.name.lastIndexOf('.')
    return lastDotIndex > 0 ? props.file.name.substring(lastDotIndex) : ''
  }
  return ''
})

// Confirm rename
function handleConfirm() {
  if (props.file && newName.value.trim()) {
    emit('confirm', props.file.id, newName.value.trim() + fileExtension.value)
    dialogVisible.value = false
  }
}

// Cancel
function handleCancel() {
  dialogVisible.value = false
}
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    title="重命名"
    width="400px"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <div class="rename-file-dialog">
      <div class="rename-file-dialog__label">请输入新的文件名</div>
      <el-input
        v-model="newName"
        placeholder="请输入文件名"
        maxlength="100"
        show-word-limit
        @keyup.enter="handleConfirm"
      >
        <template #append>{{ fileExtension }}</template>
      </el-input>
    </div>

    <template #footer>
      <div class="rename-file-dialog__footer">
        <el-button @click="handleCancel">取消</el-button>
        <el-button type="primary" @click="handleConfirm">确定</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.rename-file-dialog {
  &__label {
    font-size: 14px;
    color: $text-primary;
    margin-bottom: $spacing-base;
  }

  &__footer {
    display: flex;
    justify-content: flex-end;
    gap: $spacing-sm;
  }
}
</style>
