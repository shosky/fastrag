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
        <div
          v-for="tc in toolCalls"
          :key="tc.id"
          class="tool-call-card"
          :class="cardState(tc)"
        >
          <!-- 标题行：前缀图标 + 动态文案 + 收起态常驻一行摘要 -->
          <div class="tool-header" @click="toggleExpand(tc.id)">
            <el-icon class="tool-icon"><component :is="toolIcon(tc.name)" /></el-icon>
            <span class="tool-name" :class="{ 'is-failed-name': isFailed(tc) }">{{ headerTitle(tc) }}</span>
            <!-- 兜底形态 tag：MCP 工具 / 技能（按工具名识别，与二批结构化渲染互补） -->
            <el-tag v-if="displayType(tc) === 'mcp'" type="warning" size="small" effect="plain" class="tool-kind-tag">MCP 工具</el-tag>
            <el-tag v-else-if="displayType(tc) === 'skill_md'" type="success" size="small" effect="plain" class="tool-kind-tag">技能</el-tag>
            <span class="tool-summary" :title="summarizeToolCall(tc.name, tc.result)">
              {{ summarizeToolCall(tc.name, tc.result) }}
            </span>
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
              <span class="section-label">{{ resultLabel(tc) }}</span>
              <!-- 结构化渲染路由：database_query / knowledge_chunks 走专用渲染器；
                   mcp / skill_md 走三批兜底形态（MCP 轻结构化或原始输出、技能 Markdown），
                   generic 回退原始输出 pre -->
              <DatabaseQueryTable
                v-if="displayType(tc) === 'database_query'"
                :data="tc.result.data"
              />
              <KnowledgeChunksList
                v-else-if="displayType(tc) === 'knowledge_chunks'"
                :data="tc.result.data"
              />
              <!-- 技能输出：后端技能读取返回 Markdown，renderMarkdown 单源（DOMPurify 消毒） -->
              <div
                v-else-if="displayType(tc) === 'skill_md'"
                class="skill-md markdown-body"
                v-html="renderMarkdown(tc.result.output)"
              />
              <!-- MCP：output 可解析为对象/数组 → 键值/列表化；否则维持原始输出 pre -->
              <div v-else-if="displayType(tc) === 'mcp' && mcpRows(tc.result.output)" class="mcp-structured">
                <div v-for="row in mcpRows(tc.result.output)" :key="row.key" class="mcp-row">
                  <span class="mcp-key">{{ row.key }}</span>
                  <span class="mcp-value">{{ row.value }}</span>
                </div>
              </div>
              <pre v-else class="tool-pre tool-output">{{ tc.result.output }}</pre>
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
import {
  Position,
  ArrowDown,
  ArrowRight,
  Coin,
  Collection,
  Connection,
  MagicStick,
  SetUp,
} from '@element-plus/icons-vue'
import DatabaseQueryTable from '@/views/conversation/components/tool-results/DatabaseQueryTable.vue'
import KnowledgeChunksList from '@/views/conversation/components/tool-results/KnowledgeChunksList.vue'
import { resolveToolDisplayType, summarizeToolCall, mcpStructuredRows } from '@/utils/toolDisplay'
import { renderMarkdown } from '@/utils/markdown'
import type { ToolCallItem } from '@/types/conversation'

// ===========================================================================
// 工具调用卡片列表（共享件，runtime.vue / AppChat.vue / MessageBubble 共用）
//
// app-chat-optimize 二批展示增强（不改事件数据流，props 面保持 toolCalls/autoCollapse）：
// - 按 resolveToolDisplayType 分发结果区：database_query → DatabaseQueryTable、
//   knowledge_chunks → KnowledgeChunksList、generic → 原始输出 pre
// - 卡片标题行：工具名前缀图标 + 动态文案（pending=「正在调用 {name}…」/ 完成=工具名）
//   + 三态样式（执行中蓝描边 / 失败红字 / 默认）
// - 收起状态常驻 summarizeToolCall 一行摘要（收起也有信息量）
//
// 三批兜底形态（仍不改数据流，仅结果区展示分支）：
// - mcp：「MCP 工具」tag；output 可 JSON.parse 且为对象/数组 → 键值/列表化，否则 pre
//   （MCP 形状取决于远端，不强解析）
// - skill_md：「技能」tag；后端技能读取返回 Markdown，结果经 renderMarkdown 单源渲染
// ===========================================================================

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

// ── 卡片骨架（app-chat-optimize 二批）──

/** 渲染路由结果（缓存于模板多次调用的开销可忽略：纯字符串判定） */
function displayType(tc: ToolCallItem) {
  return resolveToolDisplayType(tc.name, tc.result)
}

/** 标题动态文案：执行中 =「正在调用 {name}…」，完成 = 工具名 */
function headerTitle(tc: ToolCallItem): string {
  return tc.result ? tc.name : `正在调用 ${tc.name}…`
}

/** 卡片三态：执行中蓝描边 / 失败红字 / 默认 */
function cardState(tc: ToolCallItem): string {
  if (!tc.result) return 'is-running'
  return tc.result.success ? '' : 'is-failed'
}

function isFailed(tc: ToolCallItem): boolean {
  return !!tc.result && !tc.result.success
}

/** 结果区标签：generic 加「原始输出」标签头，结构化/兜底形态给语义标签 */
function resultLabel(tc: ToolCallItem): string {
  switch (displayType(tc)) {
    case 'database_query': return '查询结果'
    case 'knowledge_chunks': return '检索结果'
    case 'mcp': return '返回结果'
    case 'skill_md': return '技能输出'
    default: return '原始输出'
  }
}

/** MCP 轻结构化：可解析为对象/数组的 output → 键值行；否则 undefined（回退 pre） */
function mcpRows(output: string) {
  return mcpStructuredRows(output)
}

/** 数据库工具族名匹配：database 前缀 / {dbId}_query_database|list_tables|describe_table 后缀 */
const DB_TOOL_RE = /_(query_database|list_tables|describe_table)$/

/** 按工具名取图标：database→Coin / query_kb→Collection / 含 mcp→Connection / 含 skill→MagicStick / 其他→SetUp */
function toolIcon(name: string) {
  if (typeof name === 'string' && (name.startsWith('database') || DB_TOOL_RE.test(name))) return Coin
  if (typeof name === 'string' && name.startsWith('query_kb')) return Collection
  if (typeof name === 'string' && name.includes('mcp')) return Connection
  if (typeof name === 'string' && name.includes('skill')) return MagicStick
  return SetUp
}
</script>

<style scoped lang="scss">
// markdown 渲染样式单源（技能输出 skill_md 消费 .markdown-body）；
// 含 :deep()，按 MessageBubble 先例须置于本块规则前引入
@use '@/assets/styles/chat-markdown' as *;

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

/* 单个工具卡片：三态（执行中蓝描边 / 失败红字 / 默认） */
.tool-call-card {
  border-bottom: 1px solid #f0f0f0;
  border-left: 2px solid transparent;
  transition: border-color 0.15s;
}
.tool-call-card:last-child {
  border-bottom: none;
}
.tool-call-card.is-running {
  border-left-color: #409eff;
  background: #f0f7ff;
}
.tool-call-card.is-failed {
  border-left-color: #f56c6c;
}

.tool-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px 8px 22px;
  cursor: pointer;
  font-size: 13px;
  user-select: none;
  transition: background 0.15s;
}
.tool-header:hover { background: #f5f5f5; }
.tool-icon {
  color: #606266;
  font-size: 14px;
  flex-shrink: 0;
}
.tool-name {
  font-weight: 500;
  color: #303133;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.tool-name.is-failed-name { color: #f56c6c; }
/* 收起状态常驻一行摘要（收起也有信息量） */
.tool-summary {
  flex: 1;
  min-width: 0;
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  text-align: left;
}
.is-running .tool-summary { color: #409eff; }
.is-failed .tool-summary { color: #f56c6c; }
.tool-duration { color: #999; font-size: 12px; flex-shrink: 0; }
.expand-icon { color: #999; font-size: 12px; transition: transform 0.2s; flex-shrink: 0; }
.tool-body { padding: 12px 12px 12px 22px; border-top: 1px solid #f0f0f0; background: #fafafa; }
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

/* ── 三批兜底形态：MCP / 技能 ── */
.tool-kind-tag { flex-shrink: 0; }

/* 技能输出：Markdown 渲染容器（排版由 .markdown-body 单源提供） */
.skill-md {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 8px 10px;
  max-height: 300px;
  overflow-y: auto;
  font-size: 13px;
  word-break: break-word;
}

/* MCP 轻结构化：顶层键值对 / 数组索引行；解析失败回退 .tool-pre */
.mcp-structured {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  max-height: 300px;
  overflow-y: auto;
  font-size: 12px;
}
.mcp-row {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 3px 8px;
  border-bottom: 1px solid #f0f0f0;
  line-height: 1.5;
}
.mcp-row:last-child { border-bottom: none; }
.mcp-key {
  flex-shrink: 0;
  min-width: 24px;
  max-width: 40%;
  font-weight: 500;
  color: #606266;
  font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
  word-break: break-all;
}
.mcp-value {
  flex: 1;
  min-width: 0;
  color: #303133;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
