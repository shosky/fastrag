<script setup lang="ts">
import { ref, nextTick } from 'vue'
import { ChatLineRound } from '@element-plus/icons-vue'
import MessageBubble from './MessageBubble.vue'
import type { ConversationChatMessage } from '@/types/conversation'

// ===========================================================================
// 消息滚动区：消息 v-for + 智能滚动跟随 + 生成中指示 + 空态
//
// 智能滚动算法平移 runtime.vue:241-263（距底 <80px 才自动跟随，
// 强制滚动用于切换会话/发送消息时重置跟随状态）。
// ===========================================================================

const props = defineProps<{
  messages: ConversationChatMessage[]
  isStreaming?: boolean
  /** 检索等待中（直聊 kbIds 非空发送后置位，sources/首个 message 增量/end/error 复位） */
  searching?: boolean
  /** 历史消息加载中（骨架） */
  loading?: boolean
  /** 空态文案（区分「新建未发首条」与「会话已清空」） */
  emptyTitle?: string
  emptySubtitle?: string
  /** 来源抽屉受控开关（引用角标点击打开；透传 MessageBubble → SourcesBlock） */
  sourcesDrawerVisible?: boolean
}>()

const emit = defineEmits<{
  (e: 'delete-message', message: ConversationChatMessage): void
  (e: 'feedback-message', message: ConversationChatMessage, type: string): void
  (e: 'edit-message', message: ConversationChatMessage): void
  (e: 'open-sources'): void
  (e: 'update:sourcesDrawerVisible', visible: boolean): void
}>()

const messagesContainer = ref<HTMLElement | null>(null)

// ── 来源抽屉受控（引用角标点击打开）──
// 父级 sourcesDrawerVisible 是全局「有抽屉打开」信号；activeSourcesMsg 定位到具体
// 消息，二者合取下传——否则所有气泡的 SourcesBlock 抽屉会同时打开。
const activeSourcesMsg = ref<ConversationChatMessage | null>(null)

/** 未受控（父级未传）时返回 undefined 透传，SourcesBlock 走内部自管 */
function drawerVisibleOf(msg: ConversationChatMessage): boolean | undefined {
  if (props.sourcesDrawerVisible === undefined) return undefined
  return props.sourcesDrawerVisible && activeSourcesMsg.value === msg
}

function handleOpenSources(msg: ConversationChatMessage) {
  activeSourcesMsg.value = msg
  emit('open-sources')
}

function handleDrawerVisible(msg: ConversationChatMessage, visible: boolean) {
  // visible=true：块尾「查看全部来源」入口（受控态）；false：用户关闭
  if (visible) {
    activeSourcesMsg.value = msg
  } else if (activeSourcesMsg.value === msg) {
    activeSourcesMsg.value = null
  }
  emit('update:sourcesDrawerVisible', visible)
}

// ── 智能滚动跟随 ──
// 用户手动上翻查看历史时暂停自动滚动，滚回底部附近后恢复跟随
const SCROLL_FOLLOW_THRESHOLD = 80
const followScroll = ref(true)

function updateFollowState() {
  const el = messagesContainer.value
  if (!el) return
  followScroll.value = el.scrollHeight - el.scrollTop - el.clientHeight < SCROLL_FOLLOW_THRESHOLD
}

/** 输出内容时滚动到底部（仅当用户位于底部附近时跟随，避免打断上翻查看） */
function scrollToBottom() {
  if (!followScroll.value) return
  const el = messagesContainer.value
  if (el) el.scrollTop = el.scrollHeight
}

/** 强制滚动到底部（切换会话 / 发送消息时用，重置跟随状态） */
function forceScrollToBottom() {
  followScroll.value = true
  nextTick(() => {
    if (messagesContainer.value) messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  })
}

defineExpose({ scrollToBottom, forceScrollToBottom })
</script>

<template>
  <div class="chat-main" ref="messagesContainer" @scroll="updateFollowState">
    <div v-if="loading" class="history-skeleton">
      <div v-for="i in 3" :key="i" class="skeleton-row" :class="{ mine: i % 2 === 0 }">
        <el-skeleton :rows="2" animated />
      </div>
    </div>
    <div v-else-if="messages.length" class="chat-box">
      <template v-for="(msg, idx) in messages" :key="msg.id || idx">
        <!-- 检索等待指示：流式气泡上方，助手尚无内容时显示，首增量后消失 -->
        <div v-if="searching && msg.streaming && !msg.content" class="searching-indicator">
          <div class="loading-dots"><div></div><div></div><div></div></div>
          <span class="searching-text">正在检索知识库…</span>
        </div>
        <MessageBubble
          :message="msg"
          :disabled="isStreaming"
          :drawer-visible="drawerVisibleOf(msg)"
          @delete="emit('delete-message', $event)"
          @feedback="(m, t) => emit('feedback-message', m, t)"
          @edit="emit('edit-message', $event)"
          @open-sources="handleOpenSources(msg)"
          @update:drawer-visible="(v: boolean) => handleDrawerVisible(msg, v)"
        />
      </template>
      <div v-if="isStreaming && !searching" class="generating-indicator">
        <div class="loading-dots"><div></div><div></div><div></div></div>
        <span class="generating-text">正在生成回复</span>
      </div>
    </div>
    <div v-else class="empty-state">
      <div class="empty-icon"><el-icon :size="40"><ChatLineRound /></el-icon></div>
      <h2 class="greeting">{{ emptyTitle || '您好，有什么可以帮您？' }}</h2>
      <p class="greeting-sub">{{ emptySubtitle || '输入问题开始对话' }}</p>
    </div>
  </div>
</template>

<style lang="scss" scoped>
$primary: #046a82;
$primary-light: #3996ae;
$text-primary: #151616;
$text-secondary: #4c4d4d;
$text-muted: #979999;
$border: #eef0f0;

.chat-main {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  scrollbar-width: thin;
}

.history-skeleton {
  width: 92%;
  max-width: 1200px;
  margin: 0 auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  .skeleton-row {
    max-width: 70%;
    &.mine { align-self: flex-end; }
  }
}

.chat-box {
  width: 92%;
  max-width: 1200px;
  margin: 0 auto;
  padding: 16px 16px 12px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
}

// ── Empty States ──
.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: $text-muted;
}
.empty-icon { color: $border; margin-bottom: 8px; }
.greeting { font-size: 1.4rem; font-weight: 500; color: $text-primary; margin: 0; }
.greeting-sub { font-size: 14px; color: $text-muted; margin: 0; }

// ── Generating ──
.generating-indicator {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 0;
}
.loading-dots {
  display: inline-flex; gap: 3px;
  div {
    width: 6px; height: 6px;
    background: linear-gradient(135deg, $primary, $primary-light);
    border-radius: 50%;
    animation: dotPulse 1.4s infinite ease-in-out both;
    &:nth-child(1) { animation-delay: 0s; }
    &:nth-child(2) { animation-delay: 0.16s; }
    &:nth-child(3) { animation-delay: 0.32s; }
  }
}
@keyframes dotPulse { 0%,80%,100% { transform: scale(0.6); opacity: 0.4; } 40% { transform: scale(1); opacity: 1; } }
.generating-text { font-size: 14px; font-weight: 500; color: $text-secondary; }

// ── Searching（检索等待：流式气泡上方，助手首增量后消失）──
.searching-indicator {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 0;
}
.searching-text { font-size: 14px; font-weight: 500; color: $text-secondary; }
</style>
