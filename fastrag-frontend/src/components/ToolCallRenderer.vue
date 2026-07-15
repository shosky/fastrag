<template>
  <div class="tool-call-list" v-if="toolCalls && toolCalls.length > 0">
    <div v-for="tc in toolCalls" :key="tc.id" class="tool-call-card">
      <div class="tool-header" @click="toggleExpand(tc.id)">
        <el-icon><Position /></el-icon>
        <span class="tool-name">{{ tc.name }}</span>
        <el-tag v-if="tc.result" :type="tc.result.success ? 'success' : 'danger'" size="small" effect="plain">
          {{ tc.result.success ? '成功' : '失败' }}
        </el-tag>
        <el-tag v-else type="warning" size="small" effect="plain">执行中...</el-tag>
        <span v-if="tc.result" class="tool-duration">{{ tc.result.durationMs }}ms</span>
        <el-icon class="expand-icon">
          <ArrowDown v-if="expandedIds.has(tc.id)" />
          <ArrowRight v-else />
        </el-icon>
      </div>
      <div v-if="expandedIds.has(tc.id)" class="tool-body">
        <div class="tool-section">
          <span class="section-label">参数</span>
          <pre class="tool-pre">{{ formatJson(tc.arguments) }}</pre>
        </div>
        <div v-if="tc.result" class="tool-section">
          <span class="section-label">结果</span>
          <pre class="tool-pre tool-output">{{ tc.result.output }}</pre>
        </div>
        <div v-if="tc.result && tc.result.error" class="tool-section error-section">
          <span class="section-label">错误</span>
          <pre class="tool-pre">{{ tc.result.error }}</pre>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Position, ArrowDown, ArrowRight } from '@element-plus/icons-vue'

interface ToolCallItem {
  id: string
  name: string
  arguments: string
  result?: {
    success: boolean
    output: string
    durationMs: number
    error?: string
  }
}

defineProps<{ toolCalls: ToolCallItem[] }>()

const expandedIds = ref(new Set<string>())

function toggleExpand(id: string) {
  if (expandedIds.value.has(id)) {
    expandedIds.value.delete(id)
  } else {
    expandedIds.value.add(id)
  }
  // Trigger reactivity
  expandedIds.value = new Set(expandedIds.value)
}

function formatJson(str: string): string {
  if (!str) return ''
  try { return JSON.stringify(JSON.parse(str), null, 2) }
  catch { return str }
}
</script>

<style scoped>
.tool-call-list { margin: 8px 0; }
.tool-call-card {
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  margin: 4px 0;
  overflow: hidden;
}
.tool-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  cursor: pointer;
  background: #fafafa;
  font-size: 13px;
  user-select: none;
}
.tool-header:hover { background: #f5f5f5; }
.tool-name { font-weight: 500; flex: 1; }
.tool-duration { color: #999; font-size: 12px; }
.expand-icon { color: #999; font-size: 12px; }
.tool-body { padding: 12px; border-top: 1px solid #e4e7ed; }
.tool-section { margin: 8px 0; }
.error-section { color: #f56c6c; }
.section-label {
  font-size: 12px;
  color: #999;
  margin-bottom: 4px;
  display: block;
  font-weight: 500;
}
.tool-pre {
  background: #f5f5f5;
  padding: 8px;
  border-radius: 4px;
  font-size: 12px;
  max-height: 200px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
  font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
}
.tool-output { max-height: 300px; }
</style>
