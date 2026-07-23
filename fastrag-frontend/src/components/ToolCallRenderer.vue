<template>
  <div class="tool-call-list" v-if="toolCalls && toolCalls.length > 0">
    <el-collapse v-model="outerKey" class="tool-call-collapse">
      <el-collapse-item name="tools">
        <template #title>
          <div class="tool-collapse-header">
            <el-icon><Position /></el-icon>
            <span>工具调用</span>
            <el-tag size="small" type="info" effect="plain" class="tool-count-tag">
              {{ toolCalls.length }}
            </el-tag>
            <span v-if="isAllDone" class="tools-done-hint">已完成</span>
            <span v-else class="tools-running-hint">执行中...</span>
          </div>
        </template>
        <div v-for="tc in toolCalls" :key="tc.id" class="tool-call-card">
          <div class="tool-header" @click="toggleExpand(tc.id)">
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
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
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

const props = withDefaults(defineProps<{
  toolCalls: ToolCallItem[]
  autoCollapse?: boolean
}>(), {
  autoCollapse: false
})

const outerKey = ref<string[]>(['tools'])
const expandedIds = ref(new Set<string>())

/** 所有工具调用是否都已返回结果 */
const isAllDone = computed(() =>
  props.toolCalls.every(tc => tc.result !== undefined)
)

/** 自动折叠：当 autoCollapse 为 true 时收起外层面板 */
watch(() => props.autoCollapse, (val) => {
  if (val) {
    outerKey.value = []
  } else {
    outerKey.value = ['tools']
  }
})

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

/* 外层折叠面板 */
.tool-call-collapse {
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  overflow: hidden;
}
.tool-call-collapse :deep(.el-collapse-item__header) {
  background: #fafafa;
  border-bottom: 1px solid #e4e7ed;
  font-size: 13px;
  height: 36px;
  line-height: 36px;
  padding: 0 12px;
}
.tool-call-collapse :deep(.el-collapse-item__wrap) {
  border-bottom: none;
}
.tool-call-collapse :deep(.el-collapse-item__content) {
  padding: 0;
}

.tool-collapse-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 500;
  color: #606266;
}
.tool-count-tag {
  font-weight: 400;
}
.tools-done-hint {
  font-size: 12px;
  color: #67c23a;
  margin-left: auto;
}
.tools-running-hint {
  font-size: 12px;
  color: #e6a23c;
  margin-left: auto;
}

/* 单个工具卡片 */
.tool-call-card {
  border-bottom: 1px solid #f0f0f0;
}
.tool-call-card:last-child {
  border-bottom: none;
}
.tool-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px 8px 24px;
  cursor: pointer;
  font-size: 13px;
  user-select: none;
  transition: background 0.15s;
}
.tool-header:hover { background: #f5f5f5; }
.tool-name { font-weight: 500; flex: 1; color: #303133; }
.tool-duration { color: #999; font-size: 12px; }
.expand-icon { color: #999; font-size: 12px; transition: transform 0.2s; }
.tool-body { padding: 12px 12px 12px 24px; border-top: 1px solid #f0f0f0; background: #fafafa; }
.tool-section { margin: 8px 0; }
.tool-section:first-child { margin-top: 0; }
.error-section { color: #f56c6c; }
.section-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
  display: block;
  font-weight: 500;
}
.tool-pre {
  background: #fff;
  padding: 8px;
  border-radius: 4px;
  border: 1px solid #e4e7ed;
  font-size: 12px;
  max-height: 200px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
  font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
  line-height: 1.5;
}
.tool-output { max-height: 300px; }
</style>
