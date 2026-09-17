<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'

/**
 * 零依赖富文本编辑器（contenteditable + execCommand）
 * 工具栏：加粗 / 斜体 / 下划线 / 删除线 / 有序列表 / 无序列表 / 链接 / 清除格式 / HTML 源码切换
 */
const props = defineProps<{ modelValue: string; placeholder?: string; minHeight?: number }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: string): void }>()

const editorRef = ref<HTMLDivElement | null>(null)
const showSource = ref(false)
const sourceText = ref('')

onMounted(() => {
  if (editorRef.value) editorRef.value.innerHTML = props.modelValue || ''
})

watch(() => props.modelValue, (v) => {
  if (editorRef.value && v !== editorRef.value.innerHTML) editorRef.value.innerHTML = v || ''
})

function sync() {
  if (editorRef.value) emit('update:modelValue', editorRef.value.innerHTML)
}

function exec(command: string, value?: string) {
  editorRef.value?.focus()
  document.execCommand(command, false, value)
  sync()
}

function addLink() {
  const url = window.prompt('链接地址（URL）')
  if (url) exec('createLink', url)
}

function toggleSource() {
  if (!showSource.value) {
    sourceText.value = editorRef.value?.innerHTML || ''
    showSource.value = true
  } else {
    if (editorRef.value) editorRef.value.innerHTML = sourceText.value
    showSource.value = false
    sync()
  }
}
</script>

<template>
  <div class="rich-editor">
    <div class="rich-editor__toolbar">
      <button type="button" title="加粗" @mousedown.prevent @click="exec('bold')"><strong>B</strong></button>
      <button type="button" title="斜体" @mousedown.prevent @click="exec('italic')"><i>I</i></button>
      <button type="button" title="下划线" @mousedown.prevent @click="exec('underline')"><u>U</u></button>
      <button type="button" title="删除线" @mousedown.prevent @click="exec('strikeThrough')"><s>S</s></button>
      <span class="rich-editor__sep" />
      <button type="button" title="无序列表" @mousedown.prevent @click="exec('insertUnorderedList')">•≡</button>
      <button type="button" title="有序列表" @mousedown.prevent @click="exec('insertOrderedList')">1≡</button>
      <button type="button" title="链接" @mousedown.prevent @click="addLink">🔗</button>
      <span class="rich-editor__sep" />
      <button type="button" title="清除格式" @mousedown.prevent @click="exec('removeFormat')">⌫</button>
      <button type="button" :title="showSource ? '返回可视化编辑' : 'HTML 源码'" @click="toggleSource">{{ showSource ? '👁' : '<>' }}</button>
    </div>
    <div v-show="!showSource" ref="editorRef" class="rich-editor__body" contenteditable="true"
      :style="{ minHeight: (minHeight || 120) + 'px' }" :data-placeholder="placeholder" @input="sync" @blur="sync" />
    <textarea v-show="showSource" v-model="sourceText" class="rich-editor__source"
      :style="{ minHeight: (minHeight || 120) + 'px' }" spellcheck="false" />
  </div>
</template>

<style lang="scss" scoped>
.rich-editor {
  width: 100%;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  overflow: hidden;
}
.rich-editor__toolbar {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 4px 6px;
  border-bottom: 1px solid #ebeef5;
  background: #f5f7fa;
  button {
    min-width: 26px;
    height: 24px;
    border: none;
    background: transparent;
    border-radius: 3px;
    cursor: pointer;
    font-size: 12px;
    color: #606266;
    &:hover { background: #e4e7ed; }
  }
}
.rich-editor__sep { width: 1px; height: 14px; background: #dcdfe6; margin: 0 4px; }
.rich-editor__body {
  padding: 8px 10px;
  font-size: 13px;
  line-height: 1.6;
  color: #303133;
  outline: none;
  overflow-y: auto;
  &:empty::before { content: attr(data-placeholder); color: #c0c4cc; }
  :deep(a) { color: #409eff; }
  :deep(ol), :deep(ul) { padding-left: 20px; }
}
.rich-editor__source {
  width: 100%;
  border: none;
  outline: none;
  padding: 8px 10px;
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  color: #303133;
  resize: vertical;
  box-sizing: border-box;
}
</style>
