<script setup lang="ts">
import { computed } from 'vue'
import { propString, type WidgetComponentProps } from '../registry'
import { renderMarkdown } from '@/utils/markdown'

// Markdown：富文本块——全渲染器族唯一 v-html 点，经 renderMarkdown 单源（marked +
// DOMPurify 消毒，script/style/iframe 与内联事件属性禁用，javascript:/data: 协议拦截）
const props = defineProps<WidgetComponentProps>()
const html = computed(() => renderMarkdown(propString(props.node, 'text')))
</script>

<template>
  <!-- 安全注释：renderMarkdown 单源消毒（MOD ## 契约不变量），勿改为插值文本渲染 -->
  <div class="widget-w-markdown markdown-body" v-html="html" />
</template>

<style scoped>
.widget-w-markdown {
  font-size: 13px;
  color: var(--el-text-color-regular, #606266);
  word-break: break-word;
}
.widget-w-markdown :deep(p) {
  margin: 0 0 6px;
  &:last-child { margin-bottom: 0; }
}
.widget-w-markdown :deep(ul),
.widget-w-markdown :deep(ol) {
  padding-left: 18px;
  margin: 6px 0;
}
.widget-w-markdown :deep(code) {
  background: var(--el-fill-color-light, #f5f7fa);
  padding: 1px 5px;
  border-radius: 3px;
  font-size: 12px;
}
</style>
