<template>
  <div v-if="displayContent" class="thinking-block">
    <el-collapse v-model="activeKey" class="thinking-collapse">
      <el-collapse-item name="thinking">
        <template #title>
          <div class="thinking-header">
            <el-icon class="thinking-icon" :class="{ spinning: isThinking }">
              <InfoFilled />
            </el-icon>
            <span class="thinking-title">{{ isThinking ? '正在思考...' : '推理过程' }}</span>
          </div>
        </template>
        <div class="thinking-content">
          <pre>{{ displayContent }}</pre>
        </div>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { InfoFilled } from '@element-plus/icons-vue'

const props = withDefaults(defineProps<{
  content: string
  isThinking?: boolean
}>(), {
  isThinking: false
})

const activeKey = ref<string[]>(['thinking'])

watch(() => props.isThinking, (val) => {
  if (val) activeKey.value = ['thinking']
})

const displayContent = computed(() => {
  if (!props.content) return ''
  const thinkMatch = props.content.match(/<think[^>]*>([\s\S]*?)(?:<\/think>|$)/)
  if (thinkMatch && thinkMatch[1]) return thinkMatch[1].trim()
  return props.content
})
</script>

<style scoped>
.thinking-block {
  margin: 8px 0;
  border-left: 3px solid #e6a700;
  border-radius: 4px;
  overflow: hidden;
}
.thinking-header {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #e6a700;
  font-size: 14px;
}
.thinking-icon.spinning {
  animation: spin 1s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}
.thinking-title { font-weight: 500; }
.thinking-content {
  background: #fefce8;
  padding: 12px;
  font-size: 13px;
  line-height: 1.6;
  color: #666;
  max-height: 400px;
  overflow-y: auto;
}
.thinking-content pre {
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
  font-family: inherit;
}
:deep(.el-collapse-item__header) {
  background: transparent;
  border-bottom: none;
  font-size: 14px;
  height: 36px;
  line-height: 36px;
}
:deep(.el-collapse-item__wrap) {
  border-bottom: none;
}
</style>
