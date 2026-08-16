<script setup lang="ts">
import { CHUNK_LENGTH_OPTIONS } from '@/types/knowledge'

export interface ChunkLengthOption {
  label: string
  value: number
}

/** 分片长度快捷选择：预设按钮 + 自定义输入（value=0 时显示输入框） */
const props = defineProps<{
  modelValue: number
  /** 预设选项（默认通用 2000/1000/500，父子切片等场景可传入专用预设） */
  options?: ChunkLengthOption[]
}>()
const emit = defineEmits<{ (e: 'update:modelValue', value: number): void }>()

const lengthOptions = computed<ChunkLengthOption[]>(() => props.options || CHUNK_LENGTH_OPTIONS)

// 自定义输入缓存（仅在 modelValue===0 时展示）
const customText = ref('')

function select(value: number) {
  if (value !== 0) customText.value = ''
  emit('update:modelValue', value)
}

function applyCustom(value: string) {
  emit('update:modelValue', Number(value) || 1000)
}

// blur / Enter 时用输入框当前值提交（EP el-input 的 change 事件在部分版本不触发，改用这两个事件）
function commitCustom() {
  applyCustom(customText.value)
}
</script>

<template>
  <div class="chunk-length-select">
    <el-button
      v-for="opt in lengthOptions"
      :key="opt.value"
      :type="props.modelValue === opt.value ? 'primary' : 'default'"
      size="small"
      @click="select(opt.value)"
    >
      {{ opt.label }}
    </el-button>
    <el-input
      v-if="props.modelValue === 0"
      v-model="customText"
      placeholder="自定义"
      size="small"
      style="width: 100px"
      @keyup.enter="commitCustom"
      @blur="commitCustom"
    />
  </div>
</template>

<style scoped>
.chunk-length-select {
  display: flex;
  gap: 8px;
  align-items: center;
}
</style>
