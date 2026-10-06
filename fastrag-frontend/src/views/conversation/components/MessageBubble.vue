<script setup lang="ts">
import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import { copyToClipboard, downloadAsDocx } from '@/utils/chatActions'
import { renderMarkdown } from '@/utils/markdown'
import { injectCitationRefs } from '@/utils/citeSources'
import ThinkingBlock from '@/components/ThinkingBlock.vue'
import ToolCallRenderer from '@/components/ToolCallRenderer.vue'
import SourcesBlock from './SourcesBlock.vue'
import type { ConversationChatMessage } from '@/types/conversation'

// ===========================================================================
// 单条消息气泡：markdown 渲染 + 思考/工具卡 + 来源块 + 操作条
// 交互平移 runtime.vue:267-308（复制/下载/删除/点赞点踩/编辑重发）。
// app-chat-optimize 三批：会话汇总行（工具/轮次/耗时）+ 引用角标 [N]（点击开来源抽屉）。
// ===========================================================================

const props = defineProps<{
  message: ConversationChatMessage
  disabled?: boolean
  /** 来源抽屉受控开关（未传时 SourcesBlock 内部自管） */
  drawerVisible?: boolean
}>()

const emit = defineEmits<{
  (e: 'delete', message: ConversationChatMessage): void
  (e: 'feedback', message: ConversationChatMessage, type: string): void
  (e: 'edit', message: ConversationChatMessage): void
  (e: 'open-sources'): void
  (e: 'update:drawerVisible', visible: boolean): void
}>()

const isAssistant = computed(() => props.message.role === 'assistant')
const canAct = computed(() => isAssistant.value && !props.message.streaming && !!props.message.content && !props.disabled)

// ── 会话汇总行（assistant 完成后，footer 上方；缺失项跳过）──
const summaryChips = computed<string[]>(() => {
  const m = props.message
  if (!isAssistant.value || m.streaming) return []
  const chips: string[] = []
  if (m.toolCalls?.length) chips.push(`🔧 ${m.toolCalls.length} 次工具调用`)
  if (m.iterations) chips.push(`${m.iterations} 轮`)
  // time 为 '(已停止)' 等停止标注时不作耗时展示
  if (m.time && !m.time.startsWith('(')) chips.push(`耗时 ${m.time}`)
  return chips
})

// ── 引用角标：来源数（流内 sources 优先，历史 references 兜底）──
const citeCount = computed(() =>
  isAssistant.value ? props.message.sources?.length || props.message.references?.length || 0 : 0
)

/** renderMarkdown 消毒后，按来源数注入 [N] 上标角标（仅自有安全 HTML，N 超界不替换防幻觉） */
const renderedContent = computed(() => {
  const html = renderMarkdown(props.message.content)
  return citeCount.value > 0 ? injectCitationRefs(html, citeCount.value) : html
})

/** 事件委托：点击角标 → 打开来源抽屉（v-html 内容无法逐元素绑事件） */
function onContentClick(e: MouseEvent) {
  if (!citeCount.value) return
  const target = e.target as HTMLElement | null
  if (target?.closest('.cite-ref')) emit('open-sources')
}

async function copyMessage() {
  const ok = await copyToClipboard(props.message.content)
  if (ok) ElMessage.success('已复制')
}

async function downloadMessageDocx() {
  const filename = `message_${props.message.id || Date.now()}`
  await downloadAsDocx(props.message.content, filename)
  ElMessage.success('已下载')
}
</script>

<template>
  <div class="message-row" :class="message.role">
    <div v-if="isAssistant" class="msg-avatar"><el-icon :size="16"><ChatLineRound /></el-icon></div>
    <div class="msg-body">
      <ThinkingBlock
        v-if="isAssistant && message.thinkingContent"
        :content="message.thinkingContent"
        :is-thinking="message.isThinking"
        :force-collapsed="message.content.length > 0"
      />
      <ToolCallRenderer
        v-if="isAssistant && message.toolCalls?.length"
        :tool-calls="message.toolCalls"
        :auto-collapse="message.content.length > 0 && message.thinkingContent !== undefined"
      />
      <div v-if="isAssistant" class="msg-content markdown-body" v-html="renderedContent" @click="onContentClick" />
      <div v-else class="msg-content">{{ message.content }}</div>
      <!-- 知识来源：流内 sources（含 content）优先，历史回看用 references -->
      <SourcesBlock
        v-if="isAssistant && message.sources?.length"
        :sources="message.sources"
        variant="stream"
        :drawer-visible="drawerVisible"
        @update:drawer-visible="emit('update:drawerVisible', $event)"
      />
      <SourcesBlock
        v-else-if="isAssistant && message.references?.length"
        :sources="message.references"
        variant="history"
        :drawer-visible="drawerVisible"
        @update:drawer-visible="emit('update:drawerVisible', $event)"
      />
      <!-- 检索空态提示：未选知识库 / 零命中（透明化"为什么没有来源"） -->
      <div v-if="isAssistant && message.retrievalHint" class="msg-hint">ℹ {{ message.retrievalHint }}</div>
      <!-- 会话汇总行：工具调用次数 / 轮数 / 耗时（缺失项跳过） -->
      <div v-if="summaryChips.length" class="msg-summary">
        <span v-for="chip in summaryChips" :key="chip" class="summary-chip">{{ chip }}</span>
      </div>
      <!-- 时间 + 操作按钮 -->
      <div v-if="message.time || (!message.streaming && message.content)" class="msg-footer">
        <span v-if="message.time" class="msg-time">{{ message.time }}</span>
        <!-- assistant 操作 -->
        <div v-if="canAct" class="msg-actions">
          <button class="action-btn" title="复制" @click="copyMessage">复制</button>
          <button class="action-btn" title="下载" @click="downloadMessageDocx">下载</button>
          <button class="action-btn" title="删除" @click="emit('delete', message)">删除</button>
          <button class="action-btn" :class="{ active: message.feedback === 'like' }" title="有帮助" @click="emit('feedback', message, 'like')">👍</button>
          <button class="action-btn" :class="{ active: message.feedback === 'dislike' }" title="无帮助" @click="emit('feedback', message, 'dislike')">👎</button>
        </div>
        <!-- user 操作 -->
        <div v-else-if="message.role === 'user' && !message.streaming && !disabled" class="msg-actions">
          <button class="action-btn" title="复制" @click="copyMessage">复制</button>
          <button class="action-btn" title="编辑" @click="emit('edit', message)">编辑</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
// markdown 渲染样式单源：@/assets/styles/chat-markdown.scss（含 :deep()，须置于规则前引入）
@use '@/assets/styles/chat-markdown' as *;

// ── Color Palette（沿 runtime.vue 聊天区配色）──
$primary: #046a82;
$text-primary: #151616;
$text-secondary: #4c4d4d;
$text-muted: #979999;
$border: #eef0f0;
$bg-card: #ffffff;
$bg-hover: #f5f7f7;
$bg-user-msg: #046a82;
$shadow-sm: 0 1px 3px rgba(0,0,0,0.04);

.message-row {
  display: flex;
  gap: 10px;
  padding: 4px 0;
  max-width: 92%;

  &.user {
    align-self: flex-end;
    flex-direction: row-reverse;
  }
  &.assistant {
    align-self: flex-start;
  }
}

.msg-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: $primary;
  color: #fff;
}

.message-row.user .msg-avatar {
  background: #409eff;
}

.msg-body {
  padding: 6px 10px;
  border-radius: 10px;
  font-size: 14px;
  line-height: 1.3;
  word-break: break-word;
}

.message-row.assistant .msg-body {
  background: $bg-card;
  color: $text-primary;
  border-bottom-left-radius: 4px;
  box-shadow: $shadow-sm;
}

.message-row.user .msg-body {
  background: $bg-user-msg;
  color: #fff;
  border-bottom-right-radius: 4px;
}

.msg-content { white-space: pre-wrap; }

// ── 引用角标（v-html 注入内容不带 scoped 属性，须 :deep 穿透）──
.msg-content :deep(sup.cite-ref) {
  color: $primary;
  font-weight: 600;
  font-size: 0.75em;
  margin: 0 1px;
  cursor: pointer;
  user-select: none;
  &:hover { text-decoration: underline; }
}

.msg-hint {
  margin-top: 4px;
  font-size: 12px;
  color: $text-muted;
  background: $bg-hover;
  border-radius: 4px;
  padding: 2px 8px;
}

// ── 会话汇总行（工具调用 / 轮数 / 耗时 chip，footer 上方）──
.msg-summary {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
}
.summary-chip {
  font-size: 11px;
  line-height: 1.6;
  color: $text-secondary;
  background: $bg-hover;
  border: 1px solid $border;
  border-radius: 10px;
  padding: 0 8px;
  white-space: nowrap;
}

.msg-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
  margin-top: 2px;
}
.msg-time { font-size: 11px; opacity: 0.7; }

.msg-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  opacity: 0;
  transition: opacity 0.15s ease;
  margin-left: 8px;
}
.message-row:hover .msg-actions { opacity: 1; }

.action-btn {
  font-size: 12px;
  padding: 1px 6px;
  border: none;
  background: transparent;
  color: $text-muted;
  cursor: pointer;
  border-radius: 4px;
  line-height: 1.6;
  white-space: nowrap;
  &:hover { background: $bg-hover; color: $text-primary; }
  &.active { color: $primary; }
}
</style>
