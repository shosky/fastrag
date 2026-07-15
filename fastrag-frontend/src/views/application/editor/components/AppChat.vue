<script setup lang="ts">
import { ref, nextTick, onMounted, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete, ChatLineRound, Promotion, User, Loading } from '@element-plus/icons-vue'
import { marked } from 'marked'
import * as api from '@/api'
import { useAppChatStream } from '@/composables/useAppChatStream'
import ThinkingBlock from '@/components/ThinkingBlock.vue'
import ToolCallRenderer from '@/components/ToolCallRenderer.vue'

const props = defineProps<{ appInfo: { id: string; name: string } }>()
const appId = () => props.appInfo.id

// ===========================================================================
// 类型定义
// ===========================================================================
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

interface ChatMessage {
  id?: string
  role: 'user' | 'assistant' | 'system'
  content: string
  time?: string
  streaming?: boolean  // 是否正在流式输出中
  thinkingContent?: string  // 思考过程内容
  isThinking?: boolean      // 是否正在思考中
  toolCalls?: ToolCallItem[] // 工具调用列表
}

interface Session {
  conversationId: string
  sessionId: string
  title: string
  messages: ChatMessage[]
  messageCount: number
  createdAt: string
  updatedAt: string
}

// ===========================================================================
// 状态
// ===========================================================================
const sessions = ref<Session[]>([])
const activeSessionId = ref<string>('')
const inputText = ref('')
const messagesContainer = ref<HTMLElement | null>(null)
const sessionsLoading = ref(false)

// 流式对话 composable
const { isStreaming, sendMessage, stopStream } = useAppChatStream()

// 当前激活的会话
const activeSession = computed(() => {
  return sessions.value.find(s => s.sessionId === activeSessionId.value)
})

// ===========================================================================
// Markdown 渲染
// ===========================================================================
// 配置 marked，启用 GFM
marked.setOptions({
  breaks: true,
  gfm: true,
})

function renderMarkdown(content: string): string {
  if (!content) return ''
  try {
    return marked.parse(content) as string
  } catch {
    return content
  }
}

// ===========================================================================
// 会话管理
// ===========================================================================

/** 从后端加载会话列表 */
async function loadSessions() {
  sessionsLoading.value = true
  try {
    const res: any = await api.getAppSessions(appId())
    if (Array.isArray(res)) {
      sessions.value = res.map((item: any) => ({
        conversationId: item.conversationId || item.id,
        sessionId: item.sessionId,
        title: item.title || '新对话',
        messages: [],
        messageCount: item.messageCount || 0,
        createdAt: item.createdAt,
        updatedAt: item.updatedAt,
      }))
      // 默认选中第一个
      if (sessions.value.length > 0 && !activeSessionId.value) {
        activeSessionId.value = sessions.value[0].sessionId
        loadSessionMessages(sessions.value[0].sessionId)
      }
    }
  } catch (e: any) {
    console.error('加载会话列表失败:', e)
  } finally {
    sessionsLoading.value = false
  }
}

/** 从后端创建新会话 */
async function createNewSession() {
  try {
    const res: any = await api.createAppSession(appId())
    if (res) {
      const session: Session = {
        conversationId: res.conversationId,
        sessionId: res.sessionId,
        title: res.title || '新对话',
        messages: [],
        messageCount: 0,
        createdAt: res.createdAt || new Date().toLocaleString(),
        updatedAt: res.createdAt || new Date().toLocaleString(),
      }
      sessions.value.unshift(session)
      activeSessionId.value = session.sessionId
      nextTick(() => scrollToBottom())
    }
  } catch (e: any) {
    ElMessage.error('创建会话失败: ' + (e?.message || '未知错误'))
  }
}

/** 从后端加载会话消息 */
async function loadSessionMessages(sessionId: string) {
  try {
    const res: any = await api.getAppSessionMessages(appId(), sessionId)
    if (Array.isArray(res)) {
      const session = sessions.value.find(s => s.sessionId === sessionId)
      if (session) {
        session.messages = res.map((msg: any) => ({
          id: msg.id,
          role: msg.role,
          content: msg.content,
          time: msg.latencyMs ? `${(msg.latencyMs / 1000).toFixed(1)}s` : undefined,
        }))
        nextTick(() => scrollToBottom())
      }
    }
  } catch (e: any) {
    console.error('加载消息失败:', e)
  }
}

/** 切换会话 */
function switchSession(sessionId: string) {
  if (isStreaming.value) {
    ElMessage.warning('请等待当前回复完成')
    return
  }
  activeSessionId.value = sessionId
  const session = sessions.value.find(s => s.sessionId === sessionId)
  if (session && session.messages.length === 0) {
    loadSessionMessages(sessionId)
  }
  nextTick(() => scrollToBottom())
}

/** 删除会话 */
async function deleteSession(sessionId: string, event: Event) {
  event.stopPropagation()
  if (isStreaming.value) {
    ElMessage.warning('请等待当前回复完成')
    return
  }
  try {
    await ElMessageBox.confirm('确定删除此对话？删除后不可恢复。', '确认', { type: 'warning' })
    await api.deleteAppSession(appId(), sessionId)
    const idx = sessions.value.findIndex(s => s.sessionId === sessionId)
    if (idx < 0) return
    sessions.value.splice(idx, 1)
    if (activeSessionId.value === sessionId) {
      activeSessionId.value = sessions.value.length > 0 ? sessions.value[0].sessionId : ''
      if (activeSessionId.value) {
        loadSessionMessages(activeSessionId.value)
      }
    }
    ElMessage.success('已删除')
  } catch {
    /* cancelled */
  }
}

// ===========================================================================
// 对话（SSE 流式）
// ===========================================================================

/** 发送通知（静默失败，不影响对话） */
async function sendNotification(title: string, content: string) {
  try {
    const summary = content.length > 100 ? content.substring(0, 100) + '...' : content
    await api.createNotification({
      title: `应用对话回复 - ${title}`,
      content: summary,
      notifyType: 'app_chat',
      sourceType: 'app',
      sourceId: appId(),
    })
  } catch {
    // 通知发送失败不影响对话体验
  }
}

async function handleSend() {
  const session = activeSession.value
  if (!inputText.value.trim() || isStreaming.value || !session) return

  const question = inputText.value.trim()
  inputText.value = ''

  // 添加用户消息到界面
  session.messages.push({ role: 'user', content: question })

  // 更新会话标题（如果是第一条用户消息）
  if (!session.messages.some(m => m.role === 'user' && m !== session.messages[session.messages.length - 1])) {
    session.title = question.length > 30 ? question.substring(0, 30) + '...' : question
  }

  // 添加一个空的助手消息用于流式填充
  const assistantMsg: ChatMessage = {
    role: 'assistant',
    content: '',
    streaming: true,
    thinkingContent: '',
    isThinking: false,
    toolCalls: [],
  }
  session.messages.push(assistantMsg)

  const msgIndex = session.messages.length - 1
  const startTime = Date.now()

  await sendMessage(
    appId(),
    question,
    session.sessionId,
    // onChunk: 追加增量文本
    (content: string) => {
      session.messages[msgIndex].content += content
      nextTick(() => scrollToBottom())
    },
    // onEnd: 流式完成
    (fullContent: string, data: Record<string, unknown>) => {
      const elapsed = ((Date.now() - startTime) / 1000).toFixed(1)
      session.messages[msgIndex].streaming = false
      session.messages[msgIndex].isThinking = false
      session.messages[msgIndex].time = `${elapsed}s`
      session.messageCount = (data.messageCount as number) || session.messages.length
      nextTick(() => scrollToBottom())
      // 发送通知：助手回复完成
      sendNotification(session.title || '对话', fullContent)
    },
    // onError: 错误处理
    (message: string) => {
      session.messages[msgIndex].content = `抱歉，AI 服务暂时不可用：${message}`
      session.messages[msgIndex].streaming = false
      session.messages[msgIndex].isThinking = false
      session.messages[msgIndex].time = '-'
      nextTick(() => scrollToBottom())
    },
    // onThinking: 思考过程增量
    (content: string) => {
      session.messages[msgIndex].thinkingContent += content
      session.messages[msgIndex].isThinking = true
      nextTick(() => scrollToBottom())
    },
    // onToolCall: 工具调用开始
    (toolCall: any) => {
      if (!session.messages[msgIndex].toolCalls) {
        session.messages[msgIndex].toolCalls = []
      }
      const existing = session.messages[msgIndex].toolCalls!.find((tc: any) => tc.id === toolCall.id)
      if (existing) {
        existing.arguments = toolCall.arguments
      } else {
        session.messages[msgIndex].toolCalls!.push({
          id: toolCall.id,
          name: toolCall.name,
          arguments: toolCall.arguments,
        })
      }
      nextTick(() => scrollToBottom())
    },
    // onToolResult: 工具执行结果
    (result: any) => {
      if (session.messages[msgIndex].toolCalls) {
        const tc = session.messages[msgIndex].toolCalls!.find((tc: any) => tc.id === result.id)
        if (tc) tc.result = result
      }
      nextTick(() => scrollToBottom())
    }
  )
}

/** 停止生成 */
function handleStop() {
  stopStream()
  const session = activeSession.value
  if (session) {
    const lastMsg = session.messages[session.messages.length - 1]
    if (lastMsg && lastMsg.role === 'assistant' && lastMsg.streaming) {
      lastMsg.streaming = false
      lastMsg.time = '(已停止)'
    }
  }
}

function scrollToBottom() {
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

function handleClear() {
  const session = activeSession.value
  if (session) {
    session.messages = []
  }
}

function handleRetry() {
  const session = activeSession.value
  if (!session || isStreaming.value) return
  // 找到最后一条用户消息
  const lastUserMsgIndex = [...session.messages].reverse().findIndex(m => m.role === 'user')
  if (lastUserMsgIndex < 0) return
  const actualIndex = session.messages.length - 1 - lastUserMsgIndex
  const question = session.messages[actualIndex].content
  // 移除最后一条用户消息和之后的所有消息
  session.messages = session.messages.slice(0, actualIndex)
  inputText.value = question
  handleSend()
}

// 监听 Shift+Enter 换行，Enter 发送
function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}

onMounted(() => {
  loadSessions()
})
</script>

<template>
  <div class="app-chat">
    <div class="chat-layout">
      <!-- 左侧会话列表 -->
      <div class="chat-sidebar">
        <div class="sidebar-header">
          <span class="sidebar-title">对话列表</span>
          <el-button
            size="small"
            type="primary"
            :icon="Plus"
            circle
            :disabled="isStreaming"
            @click="createNewSession"
          />
        </div>
        <div v-loading="sessionsLoading" class="session-list">
          <div
            v-for="session in sessions"
            :key="session.sessionId"
            class="session-item"
            :class="{ active: session.sessionId === activeSessionId }"
            @click="switchSession(session.sessionId)"
          >
            <el-icon class="session-icon"><ChatLineRound /></el-icon>
            <div class="session-info">
              <div class="session-title">{{ session.title }}</div>
              <div class="session-time">
                {{ session.updatedAt || session.createdAt }}
              </div>
            </div>
            <el-button
              class="session-delete"
              link
              type="danger"
              size="small"
              :icon="Delete"
              :disabled="isStreaming"
              @click="deleteSession(session.sessionId, $event)"
            />
          </div>
          <el-empty v-if="!sessions.length && !sessionsLoading" description="暂无对话" :image-size="40" />
        </div>
      </div>

      <!-- 右侧聊天区域 -->
      <div class="chat-main">
        <!-- 聊天头部 -->
        <div class="chat-header">
          <span class="chat-header-title">
            {{ activeSession?.title || '对话' }}
          </span>
          <div class="chat-header-actions">
            <el-button v-if="isStreaming" size="small" type="danger" text @click="handleStop">
              停止生成
            </el-button>
            <el-button v-else size="small" text @click="handleRetry">重试</el-button>
            <el-button size="small" text @click="handleClear">清空</el-button>
          </div>
        </div>

        <!-- 消息列表 -->
        <div ref="messagesContainer" class="chat-messages">
          <template v-if="activeSession">
            <div
              v-for="(msg, idx) in activeSession.messages"
              :key="idx"
              :class="['message', msg.role]"
            >
              <!-- 助手头像 -->
              <div v-if="msg.role === 'assistant'" class="msg-avatar bot">
                <el-icon :size="16" color="#fff"><ChatLineRound /></el-icon>
              </div>
              <!-- 消息内容 -->
              <div class="msg-bubble">
                <!-- 思考过程（显示在回答之前） -->
                <ThinkingBlock
                  v-if="msg.role === 'assistant' && msg.thinkingContent"
                  :content="msg.thinkingContent"
                  :is-thinking="msg.isThinking"
                />
                <!-- 工具调用渲染（显示在回答之后） -->
                <ToolCallRenderer
                  v-if="msg.role === 'assistant' && msg.toolCalls && msg.toolCalls.length > 0"
                  :tool-calls="msg.toolCalls"
                />
                <div v-if="msg.role === 'assistant'" class="msg-content markdown-body" v-html="renderMarkdown(msg.content)" />
                <div v-else class="msg-content">{{ msg.content }}</div>
                <!-- 流式光标动画 -->
                <span v-if="msg.streaming" class="streaming-cursor">▊</span>
                <div v-if="msg.time" class="msg-time">{{ msg.time }}</div>
              </div>
              <!-- 用户头像 -->
              <div v-if="msg.role === 'user'" class="msg-avatar user">
                <el-icon :size="16" color="#fff"><User /></el-icon>
              </div>
            </div>
          </template>
          <el-empty v-else description="请新建对话" :image-size="60" />
        </div>

        <!-- 输入区域 -->
        <div class="chat-input-area">
          <div class="input-row">
            <el-input
              v-model="inputText"
              type="textarea"
              :rows="3"
              resize="none"
              placeholder="输入您的问题，按 Enter 发送..."
              :disabled="isStreaming"
              @keydown="handleKeydown"
            />
            <div class="input-actions">
              <!-- 发送 / 停止 按钮 -->
              <el-button
                v-if="isStreaming"
                class="action-btn stop-btn"
                type="danger"
                :icon="Loading"
                @click="handleStop"
              >
                停止
              </el-button>
              <el-button
                v-else
                class="action-btn send-btn"
                type="primary"
                :icon="Promotion"
                :disabled="!inputText.trim()"
                @click="handleSend"
              >
                发送
              </el-button>
            </div>
          </div>
          <div class="input-hint">按 Enter 发送，Shift+Enter 换行</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.app-chat {
  height: calc(100vh - 180px);
  border: 1px solid $border-lighter;
  border-radius: $radius-lg;
  overflow: hidden;
}

.chat-layout {
  display: flex;
  height: 100%;
}

// ===== 左侧 =====
.chat-sidebar {
  width: 260px;
  background: $bg-white;
  border-right: 1px solid $border-lighter;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $spacing-base;
  border-bottom: 1px solid $border-lighter;
}

.sidebar-title {
  font-weight: 600;
  font-size: 14px;
}

.session-list {
  flex: 1;
  overflow-y: auto;
  padding: $spacing-sm;
}

.session-item {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-sm;
  border-radius: $radius-sm;
  cursor: pointer;
  margin-bottom: 2px;
  transition: background 0.2s;

  &:hover {
    background: $bg-hover;
    .session-delete { opacity: 1; }
  }

  &.active {
    background: $bg-active;
  }
}

.session-icon {
  font-size: 18px;
  color: $color-primary;
  flex-shrink: 0;
}

.session-info {
  flex: 1;
  min-width: 0;
}

.session-title {
  font-size: 13px;
  color: $text-primary;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-time {
  font-size: 11px;
  color: $text-secondary;
  margin-top: 2px;
}

.session-delete {
  opacity: 0;
  transition: opacity 0.2s;
  flex-shrink: 0;
}

// ===== 右侧 =====
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $spacing-base $spacing-lg;
  background: $bg-white;
  border-bottom: 1px solid $border-lighter;
}

.chat-header-title {
  font-weight: 600;
  font-size: 15px;
}

.chat-header-actions {
  display: flex;
  gap: $spacing-sm;
}

// ===== 消息 =====
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: $spacing-lg;
  display: flex;
  flex-direction: column;
  gap: $spacing-base;
}

.message {
  display: flex;
  gap: $spacing-sm;
  max-width: 80%;

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

  &.bot {
    background: $color-primary;
  }

  &.user {
    background: #409eff;
  }
}

.msg-bubble {
  padding: $spacing-base;
  border-radius: $radius-base;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
}

.message.user .msg-bubble {
  background: $color-primary;
  color: #fff;
  border-bottom-right-radius: 4px;
  white-space: pre-wrap;
}

.message.assistant .msg-bubble {
  background: $bg-white;
  color: $text-primary;
  border-bottom-left-radius: 4px;
  box-shadow: 0 1px 2px rgba(0,0,0,0.06);
}

.msg-content {
  white-space: pre-wrap;
}

.msg-time {
  font-size: 11px;
  color: inherit;
  opacity: 0.7;
  margin-top: 4px;
  text-align: right;
}

// 流式光标动画
.streaming-cursor {
  display: inline-block;
  animation: blink 1s step-end infinite;
  color: $color-primary;
  font-weight: bold;
}

@keyframes blink {
  50% { opacity: 0; }
}

// ===== Markdown 样式 =====
.markdown-body {
  :deep(p) {
    margin: 0 0 8px;
    &:last-child { margin-bottom: 0; }
  }
  :deep(pre) {
    background: #f6f8fa;
    border-radius: 6px;
    padding: 12px;
    overflow-x: auto;
    margin: 8px 0;
  }
  :deep(code) {
    background: #f0f0f0;
    padding: 2px 6px;
    border-radius: 3px;
    font-size: 13px;
  }
  :deep(pre code) {
    background: none;
    padding: 0;
  }
  :deep(ul), :deep(ol) {
    padding-left: 20px;
    margin: 8px 0;
  }
  :deep(blockquote) {
    border-left: 3px solid #dcdfe6;
    padding-left: 12px;
    color: $text-secondary;
    margin: 8px 0;
  }
  :deep(table) {
    border-collapse: collapse;
    margin: 8px 0;
    th, td {
      border: 1px solid $border-lighter;
      padding: 6px 12px;
    }
    th {
      background: #f5f7fa;
    }
  }
}

// ===== 输入 =====
.chat-input-area {
  padding: $spacing-base $spacing-lg;
  background: $bg-white;
  border-top: 1px solid $border-lighter;
}

.input-row {
  display: flex;
  gap: $spacing-sm;
  align-items: flex-end;
}

.input-row :deep(.el-textarea__inner) {
  border-radius: $radius-base 0 0 $radius-base;
}

.input-row :deep(.el-textarea) {
  flex: 1;
}

.input-actions {
  display: flex;
  gap: $spacing-sm;
  flex-shrink: 0;
  padding-bottom: 0;
}

.action-btn {
  height: 40px;
  padding: 0 20px;
  border-radius: 0 $radius-base $radius-base 0;
  font-size: 14px;
  font-weight: 500;
  white-space: nowrap;
}

.send-btn {
  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}

.stop-btn {
  animation: pulse-stop 1.5s ease-in-out infinite;
}

@keyframes pulse-stop {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.7; }
}

.input-hint {
  font-size: 11px;
  color: $text-secondary;
  margin-top: $spacing-xs;
  text-align: right;
}
</style>
