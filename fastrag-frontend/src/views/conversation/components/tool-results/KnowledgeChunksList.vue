<script setup lang="ts">
import { computed } from 'vue'

// ===========================================================================
// 知识库检索结果专用渲染器（app-chat-optimize 二批）
//
// 消费结构化 data（后端 KnowledgeToolExecutor structured 透传）：
//   { display_type:'knowledge_chunks', kb_id, query,
//     chunks: [{ content, score, source, ... }] }
// 头部一行摘要 + 分片列表（score 百分比 / content 截 200 / source）+ 空态。
// ===========================================================================

/** 分片对象：content 必有，score/source 后端可选提供 */
export interface KnowledgeChunk {
  content?: unknown
  score?: unknown
  source?: unknown
  [key: string]: unknown
}

/** content 截断长度（任务约定 200 字） */
const CONTENT_MAX = 200

const props = defineProps<{
  data?: Record<string, unknown>
}>()

const query = computed(() => {
  const q = props.data?.query
  return typeof q === 'string' ? q : ''
})

const chunks = computed<KnowledgeChunk[]>(() => {
  const raw = props.data?.chunks
  return Array.isArray(raw) ? (raw as KnowledgeChunk[]) : []
})

const isAbnormal = computed(() => !Array.isArray(props.data?.chunks))

/** score（0..1 相似度）→ 百分比文本；非数值防御返回空 */
function scorePercent(score: unknown): string {
  return typeof score === 'number' && Number.isFinite(score)
    ? `${Math.round(score * 100)}%`
    : ''
}

function contentText(chunk: KnowledgeChunk): string {
  const c = chunk?.content
  const text = c === null || c === undefined ? '' : String(c)
  return text.length > CONTENT_MAX ? text.slice(0, CONTENT_MAX) + '...' : text
}

function sourceText(chunk: KnowledgeChunk): string {
  const s = chunk?.source
  return s === null || s === undefined ? '' : String(s)
}
</script>

<template>
  <div class="kb-chunks-list">
    <!-- 空态 / 异常 -->
    <div v-if="isAbnormal" class="kbc-abnormal">结果数据格式异常，请查看原始输出</div>
    <div v-else-if="chunks.length === 0" class="kbc-empty">
      <el-icon :size="20"><Collection /></el-icon>
      <span>未命中任何分片</span>
    </div>

    <template v-else>
      <!-- 头部一行摘要 -->
      <div class="kbc-summary">
        命中 {{ chunks.length }} 条分片<template v-if="query"> · 查询：{{ query }}</template>
      </div>
      <!-- 分片列表：score 百分比 + content 截 200 + source -->
      <ol class="kbc-list">
        <li v-for="(chunk, i) in chunks" :key="i" class="kbc-item">
          <div class="kbc-item-head">
            <span class="kbc-rank">#{{ i + 1 }}</span>
            <span v-if="scorePercent(chunk.score)" class="kbc-score">{{ scorePercent(chunk.score) }}</span>
          </div>
          <p class="kbc-content">{{ contentText(chunk) }}</p>
          <div v-if="sourceText(chunk)" class="kbc-source">来源：{{ sourceText(chunk) }}</div>
        </li>
      </ol>
    </template>
  </div>
</template>

<style lang="scss" scoped>
// 配色沿用 MessageBubble 体系
$primary: #046a82;
$primary-light: #3996ae;
$text-primary: #151616;
$text-muted: #979999;
$border: #eef0f0;
$bg-hover: #f5f7f7;

.kb-chunks-list { font-size: 12px; }

.kbc-summary {
  color: $text-primary;
  font-weight: 500;
  padding: 6px 8px;
  background: $bg-hover;
  border-radius: 4px;
  margin-bottom: 6px;
}

.kbc-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 280px;
  overflow-y: auto;
}

.kbc-item {
  border: 1px solid $border;
  border-radius: 4px;
  padding: 6px 8px;
  background: #fff;
  &:hover { border-color: $primary-light; }
}

.kbc-item-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.kbc-rank {
  color: $text-muted;
  font-size: 11px;
}

.kbc-score {
  color: $primary;
  font-weight: 500;
  font-size: 11px;
  background: rgba(4, 106, 130, 0.08);
  border-radius: 3px;
  padding: 0 5px;
  line-height: 1.6;
}

.kbc-content {
  margin: 0;
  color: $text-primary;
  line-height: 1.5;
  word-break: break-word;
  white-space: pre-wrap;
}

.kbc-source {
  margin-top: 4px;
  font-size: 11px;
  color: $text-muted;
}

.kbc-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 18px 12px;
  border: 1px dashed $border;
  border-radius: 4px;
  color: $text-muted;
}

.kbc-abnormal {
  padding: 10px 12px;
  border: 1px dashed $border;
  border-radius: 4px;
  color: $text-muted;
}
</style>
