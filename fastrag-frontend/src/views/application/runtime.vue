<script setup lang="ts">
import { ref, nextTick, onMounted, computed, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox, ElDropdown, ElDropdownMenu, ElDropdownItem, ElSelect, ElOption } from 'element-plus'
import { ArrowLeft, Plus, ChatLineRound, Promotion, User, Loading, Close, FolderOpened, PictureFilled } from '@element-plus/icons-vue'
import { marked } from 'marked'
import * as api from '@/api'
import { useAppChatStream } from '@/composables/useAppChatStream'
import { copyToClipboard, downloadAsDocx } from '@/utils/chatActions'
import ThinkingBlock from '@/components/ThinkingBlock.vue'
import ToolCallRenderer from '@/components/ToolCallRenderer.vue'

// ===========================================================================
// Types
// ===========================================================================
interface ToolCallItem { id: string; name: string; arguments: string; result?: { success: boolean; output: string; durationMs: number; error?: string } }
interface ChatMessage { id?: string; role: 'user' | 'assistant' | 'system'; content: string; time?: string; streaming?: boolean; thinkingContent?: string; isThinking?: boolean; toolCalls?: ToolCallItem[]; feedback?: string }
interface Session { conversationId: string; sessionId: string; title: string; messages: ChatMessage[]; messageCount: number }
interface AppItem { id: string; name: string; type: string }
interface ModelOption { code: string; name: string; brand?: string }

const router = useRouter()
const route = useRoute()

const apps = ref<AppItem[]>([])
const selectedAppId = ref('')
const selectedAppName = ref('')
const models = ref<ModelOption[]>([])
const selectedModel = ref('')
const sessions = ref<Session[]>([])
const activeSessionId = ref('')
const inputText = ref('')
const messagesContainer = ref<HTMLElement | null>(null)
const loading = ref(true)
const attachments = ref<{ name: string; size: number }[]>([])
const showSidePanel = ref(false)
const appGreeting = ref('')
const editingMessageId = ref<string | null>(null)

const { isStreaming, sendMessage, stopStream } = useAppChatStream()
const activeSession = computed(() => sessions.value.find(s => s.sessionId === activeSessionId.value))

marked.setOptions({ breaks: true, gfm: true })
function renderMarkdown(content: string): string {
  if (!content) return ''
  try { const r = marked.parse(content); return typeof r === 'string' ? r : String(r) } catch { return content }
}

// ── Data Loading ──
async function loadApps() {
  try {
    const res: any = await api.getApps()
    const list = (res as any)?.list || res || []
    apps.value = Array.isArray(list) ? list : []
    const qAppId = route.query.appId as string
    if (qAppId && apps.value.some(a => a.id === qAppId)) await selectApp(qAppId)
    else if (apps.value.length > 0) await selectApp(apps.value[0].id)
  } catch { console.error('load apps failed') }
}

async function loadModels() {
  try {
    const res: any = await api.getModels({ purpose: 'LLM' })
    const list = (res as any)?.list || res || []
    models.value = Array.isArray(list) ? list.map((m: any) => ({ code: m.code, name: m.name, brand: m.brand })) : []
    if (models.value.length > 0 && !selectedModel.value) selectedModel.value = models.value[0].code
  } catch { console.error('load models failed') }
}

async function selectApp(appId: string) {
  if (isStreaming.value) { ElMessage.warning('请等待当前回复完成'); return }
  const app = apps.value.find(a => a.id === appId)
  if (!app) return
  selectedAppId.value = appId; selectedAppName.value = app.name
  sessions.value = []; activeSessionId.value = ''
  appGreeting.value = ''
  await Promise.all([loadSessions(), loadGreeting()])
}

async function loadGreeting() {
  if (!selectedAppId.value) return
  try {
    const r: any = await api.getAppBasicConfig(selectedAppId.value)
    appGreeting.value = r?.greeting || ''
  } catch { /* ignore */ }
}

async function loadSessions() {
  if (!selectedAppId.value) return
  try {
    const res: any = await api.getAppSessions(selectedAppId.value)
    if (Array.isArray(res)) {
      sessions.value = res.map((i: any) => ({ conversationId: i.conversationId || i.id, sessionId: i.sessionId, title: i.title || '新对话', messages: [], messageCount: i.messageCount || 0 }))
      if (sessions.value.length > 0 && !activeSessionId.value) { activeSessionId.value = sessions.value[0].sessionId; await loadSessionMessages(sessions.value[0].sessionId) }
    }
  } catch {}
}

async function loadSessionMessages(sessionId: string) {
  if (!selectedAppId.value) return
  try {
    const res: any = await api.getAppSessionMessages(selectedAppId.value, sessionId)
    if (Array.isArray(res)) {
      const s = sessions.value.find(s => s.sessionId === sessionId)
      if (s) { s.messages = res.map((m: any) => ({ id: m.id, role: m.role, content: m.content, time: m.latencyMs ? `${(m.latencyMs/1000).toFixed(1)}s` : undefined, feedback: m.feedback || undefined })); nextTick(scrollToBottom) }
    }
  } catch {}
}

async function createNewSession() {
  if (!selectedAppId.value) { ElMessage.warning('请先选择应用'); return }
  try {
    const res: any = await api.createAppSession(selectedAppId.value)
    if (res) {
      const s: Session = { conversationId: res.conversationId, sessionId: res.sessionId, title: res.title || '新对话', messages: [], messageCount: 0 }
      sessions.value.unshift(s); activeSessionId.value = s.sessionId; nextTick(scrollToBottom)
    }
  } catch (e: any) { ElMessage.error('创建会话失败: ' + (e?.message || '')) }
}

function switchSession(sessionId: string) {
  if (isStreaming.value) { ElMessage.warning('请等待回复完成'); return }
  activeSessionId.value = sessionId
  const s = sessions.value.find(s => s.sessionId === sessionId)
  if (s && s.messages.length === 0) loadSessionMessages(sessionId)
  nextTick(scrollToBottom)
}

async function deleteSession(sessionId: string) {
  if (!selectedAppId.value || isStreaming.value) return
  try {
    ElMessageBox.confirm('确定删除此对话？', '确认', { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' }).then(async () => {
      await api.deleteAppSession(selectedAppId.value, sessionId)
      const idx = sessions.value.findIndex(s => s.sessionId === sessionId)
      if (idx < 0) return
      sessions.value.splice(idx, 1)
      if (activeSessionId.value === sessionId) { activeSessionId.value = sessions.value.length > 0 ? sessions.value[0].sessionId : ''; if (activeSessionId.value) loadSessionMessages(activeSessionId.value) }
      ElMessage.success('已删除')
    }).catch(() => {})
  } catch {}
}

// ── Send ──
async function handleSend() {
  if (!inputText.value.trim() || isStreaming.value || !selectedAppId.value) return
  // 无活跃会话时自动创建
  if (!activeSession.value) await createNewSession()
  const session = activeSession.value
  if (!session) return
  const question = inputText.value.trim(); inputText.value = ''
  const isEdit = editingMessageId.value !== null
  editingMessageId.value = null

  if (isEdit) {
    // 编辑模式：找到原消息，移除该消息及其后所有消息，重新发送
    const editIdx = session.messages.findIndex(m => m.id === editingMessageId.value)
    if (editIdx >= 0) session.messages.splice(editIdx)
  }

  session.messages.push({ role: 'user', content: question })
  const userCnt = session.messages.filter(m => m.role === 'user').length
  if (userCnt === 1) session.title = question.length > 30 ? question.substring(0, 30) + '...' : question
  const am: ChatMessage = { role: 'assistant', content: '', streaming: true, thinkingContent: '', isThinking: false, toolCalls: [] }
  session.messages.push(am)
  const mi = session.messages.length - 1, st = Date.now()
  await sendMessage(
    selectedAppId.value, question, session.sessionId,
    (c: string) => { session.messages[mi].content += c; nextTick(scrollToBottom) },
    () => { session.messages[mi].streaming = false; session.messages[mi].isThinking = false; session.messages[mi].time = `${((Date.now()-st)/1000).toFixed(1)}s`; nextTick(scrollToBottom) },
    (msg: string) => { session.messages[mi].content = `抱歉，AI 服务暂时不可用：${msg}`; session.messages[mi].streaming = false; session.messages[mi].isThinking = false; session.messages[mi].time = '-'; nextTick(scrollToBottom) },
    (c: string) => { session.messages[mi].thinkingContent += c; session.messages[mi].isThinking = true; nextTick(scrollToBottom) },
    (tc: any) => { if (!session.messages[mi].toolCalls) session.messages[mi].toolCalls = []; const ex = session.messages[mi].toolCalls!.find(t => t.id === tc.id); if (ex) ex.arguments = tc.arguments; else session.messages[mi].toolCalls!.push({ id: tc.id, name: tc.name, arguments: tc.arguments }); nextTick(scrollToBottom) },
    (r: any) => { if (session.messages[mi].toolCalls) { const tc = session.messages[mi].toolCalls!.find(t => t.id === r.id); if (tc) tc.result = r } nextTick(scrollToBottom) }
  )
}

function handleStop() {
  stopStream(); const s = activeSession.value
  if (s) { const lm = s.messages[s.messages.length - 1]; if (lm && lm.role === 'assistant' && lm.streaming) { lm.streaming = false; lm.time = '(已停止)' } }
}

function scrollToBottom() { if (messagesContainer.value) messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight }
function handleKeydown(e: any) { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handleSend() } }

// ── 消息操作 ──
async function copyMessage(msg: ChatMessage) {
  const ok = await copyToClipboard(msg.content)
  if (ok) ElMessage.success('已复制')
}

async function downloadMessageDocx(msg: ChatMessage) {
  const filename = `message_${msg.id || Date.now()}`
  await downloadAsDocx(msg.content, filename)
  ElMessage.success('已下载')
}

async function deleteMessage(msg: ChatMessage, idx: number) {
  const session = activeSession.value
  if (!session || !msg.id || !selectedAppId.value) return
  try {
    ElMessageBox.confirm('确定删除此消息？', '确认', { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' })
      .then(async () => {
        await api.deleteAppMessage(selectedAppId.value, msg.id!)
        session.messages.splice(idx, 1)
        ElMessage.success('已删除')
      }).catch(() => {})
  } catch {}
}

async function feedbackMessage(msg: ChatMessage, type: string) {
  if (!msg.id || !selectedAppId.value) return
  try {
    const newFeedback = msg.feedback === type ? null : type
    await api.feedbackAppMessage(selectedAppId.value, msg.id, newFeedback || '')
    msg.feedback = newFeedback || undefined
  } catch { ElMessage.error('操作失败') }
}

function editMessage(msg: ChatMessage) {
  inputText.value = msg.content
  editingMessageId.value = msg.id || null
  // 聚焦输入框
  nextTick(() => {
    const textarea = document.querySelector('.user-input') as HTMLTextAreaElement
    if (textarea) textarea.focus()
  })
}

/** 聚焦输入框时，若尚无活跃会话则自动创建一个 */
async function ensureSession() {
  if (selectedAppId.value && !activeSession.value && !isStreaming.value) {
    await createNewSession()
  }
}

// ── Attachments ──
function handleFileUpload() {
  const input = document.createElement('input'); input.type = 'file'; input.multiple = true
  input.onchange = () => { if (input.files) for (const f of input.files) attachments.value.push({ name: f.name, size: f.size }) }
  input.click()
}
function handleImageUpload() {
  const input = document.createElement('input'); input.type = 'file'; input.accept = 'image/*'; input.multiple = true
  input.onchange = () => { if (input.files) for (const f of input.files) attachments.value.push({ name: f.name, size: f.size }) }
  input.click()
}
function removeAttachment(idx: number) { attachments.value.splice(idx, 1) }
function formatSize(b: number): string { if (b < 1024) return b+' B'; if (b < 1048576) return (b/1024).toFixed(1)+' KB'; return (b/1048576).toFixed(1)+' MB' }

watch(() => route.query.appId, (n) => { if (n && n !== selectedAppId.value && apps.value.some(a => a.id === n)) selectApp(n as string) })

onMounted(async () => { loading.value = true; await Promise.all([loadApps(), loadModels()]); loading.value = false })
</script>

<template>
  <div class="agent-view">
    <!-- Header -->
    <header class="agent-header">
      <div class="header-left">
        <button class="icon-btn" @click="router.push('/application')"><el-icon :size="18"><ArrowLeft /></el-icon></button>
        <el-dropdown v-if="activeSession" trigger="click" @command="switchSession">
          <span class="conversation-title">
            {{ activeSession.title }}
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="chevron"><polyline points="6 9 12 15 18 9"/></svg>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-for="s in sessions" :key="s.sessionId" :command="s.sessionId" :class="{ active: s.sessionId === activeSessionId }">{{ s.title }}</el-dropdown-item>
              <el-dropdown-item divided @click="createNewSession"><el-icon :size="14"><Plus /></el-icon> 新建对话</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <span v-else class="conversation-title">新对话</span>
      </div>
      <div class="header-right">
        <button class="icon-btn" :class="{ active: showSidePanel }" @click="showSidePanel = !showSidePanel" title="对话列表"><el-icon :size="18"><ChatLineRound /></el-icon></button>
      </div>
    </header>

    <!-- Body -->
    <div class="agent-body">
      <!-- Chat area (column: messages + input) -->
      <div class="chat-area">
        <!-- Chat Main (scrollable) -->
        <div class="chat-main" ref="messagesContainer">
        <div v-if="loading" class="loading-state"><div class="spinner"><el-icon :size="24" class="is-loading"><Loading /></el-icon></div></div>
        <template v-else-if="selectedAppId && activeSession">
          <div class="chat-box">
            <div v-for="(msg, idx) in activeSession.messages" :key="idx" class="message-row" :class="msg.role">
              <div v-if="msg.role === 'assistant'" class="msg-avatar"><el-icon :size="16"><ChatLineRound /></el-icon></div>
              <div class="msg-body">
                <ThinkingBlock v-if="msg.role === 'assistant' && msg.thinkingContent" :content="msg.thinkingContent" :is-thinking="msg.isThinking" />
                <ToolCallRenderer v-if="msg.role === 'assistant' && msg.toolCalls?.length" :tool-calls="msg.toolCalls" />
                <div v-if="msg.role === 'assistant'" class="msg-content markdown-body" v-html="renderMarkdown(msg.content)" />
                <div v-else class="msg-content">{{ msg.content }}</div>
                <!-- 时间 + 操作按钮 -->
                <div v-if="msg.time || (!msg.streaming && msg.content)" class="msg-footer">
                  <span v-if="msg.time" class="msg-time">{{ msg.time }}</span>
                  <!-- assistant 操作 -->
                  <div v-if="msg.role === 'assistant' && !msg.streaming && msg.content" class="msg-actions">
                    <button class="action-btn" title="复制" @click="copyMessage(msg)">复制</button>
                    <button class="action-btn" title="下载" @click="downloadMessageDocx(msg)">下载</button>
                    <button class="action-btn" title="删除" @click="deleteMessage(msg, idx)">删除</button>
                    <button class="action-btn" :class="{ active: msg.feedback === 'like' }" title="有帮助" @click="feedbackMessage(msg, 'like')">👍</button>
                    <button class="action-btn" :class="{ active: msg.feedback === 'dislike' }" title="无帮助" @click="feedbackMessage(msg, 'dislike')">👎</button>
                  </div>
                  <!-- user 操作 -->
                  <div v-if="msg.role === 'user' && !msg.streaming" class="msg-actions">
                    <button class="action-btn" title="复制" @click="copyMessage(msg)">复制</button>
                    <button class="action-btn" title="编辑" @click="editMessage(msg)">编辑</button>
                  </div>
                </div>
              </div>
            </div>
            <div v-if="isStreaming" class="generating-indicator">
              <div class="loading-dots"><div></div><div></div><div></div></div>
              <span class="generating-text">正在生成回复</span>
            </div>
          </div>
        </template>
        <div v-else-if="!selectedAppId" class="empty-state">
          <div class="empty-icon"><el-icon :size="40"><ChatLineRound /></el-icon></div>
          <h2 class="greeting">选择应用开始对话</h2>
          <p class="greeting-sub">请在上方选择应用后开始使用</p>
        </div>
        <div v-else class="empty-state">
          <div v-if="appGreeting" class="app-greeting-card">
            <el-icon :size="24" class="greeting-icon"><ChatLineRound /></el-icon>
            <div class="greeting-text">{{ appGreeting }}</div>
          </div>
          <h2 v-else class="greeting">您好，有什么可以帮您？</h2>
          <p class="greeting-sub">输入问题开始对话</p>
        </div>
      </div>

      <!-- Input Area -->
      <div class="input-area-wrapper">
        <div class="input-container">
          <!-- Attachments preview -->
          <div v-if="attachments.length > 0" class="attachment-strip">
            <div v-for="(att, idx) in attachments" :key="idx" class="attachment-chip">
              <span class="chip-name">{{ att.name }}</span>
              <span class="chip-size">{{ formatSize(att.size) }}</span>
              <button class="chip-remove" @click="removeAttachment(idx)"><el-icon :size="12"><Close /></el-icon></button>
            </div>
          </div>

          <!-- Input box -->
          <div class="input-box" :class="{ focused: inputText.length > 0 }">
            <div class="input-toolbar">
              <button class="toolbar-btn" @click="handleFileUpload" title="上传文件"><el-icon :size="16"><FolderOpened /></el-icon></button>
              <button class="toolbar-btn" @click="handleImageUpload" title="上传图片"><el-icon :size="16"><PictureFilled /></el-icon></button>
              <div class="toolbar-divider"></div>
              <el-select v-model="selectedModel" placeholder="模型" size="small" class="model-picker" @change="(v: string) => selectedModel = v">
                <el-option v-for="m in models" :key="m.code" :label="m.name" :value="m.code" />
              </el-select>
            </div>
            <textarea
              v-model="inputText"
              class="user-input"
              placeholder="输入您的问题..."
              :disabled="!selectedAppId || isStreaming"
              @keydown="handleKeydown"
              @focus="ensureSession"
              rows="1"
            ></textarea>
            <div class="input-footer-bar">
              <div class="footer-left">
                <el-select v-model="selectedAppId" placeholder="选择应用" size="small" class="app-picker" @change="selectApp" :disabled="isStreaming">
                  <el-option v-for="app in apps" :key="app.id" :label="app.name" :value="app.id" />
                </el-select>
              </div>
              <div class="footer-right">
                <button
                  v-if="!isStreaming"
                  class="send-button"
                  :disabled="!inputText.trim() || !selectedAppId"
                  @click="handleSend"
                >
                  <el-icon :size="16"><Promotion /></el-icon>
                </button>
                <button v-else class="send-button stop" @click="handleStop">
                  <el-icon :size="16"><Close /></el-icon>
                </button>
              </div>
            </div>
          </div>
          <div class="input-hint">
            <span>当前应用：{{ selectedAppName || '未选择' }}</span>
            <span>Enter 发送 · Shift+Enter 换行</span>
          </div>
        </div>
      </div>
      </div><!-- end chat-area -->

      <!-- Side Panel -->
      <transition name="panel">
        <div v-if="showSidePanel" class="side-panel">
          <div class="panel-header">
            <span class="panel-title">对话列表</span>
            <button class="icon-btn small" @click="showSidePanel = false"><el-icon :size="14"><Close /></el-icon></button>
          </div>
          <div class="panel-body">
            <button class="new-chat-panel-btn" @click="createNewSession"><el-icon :size="14"><Plus /></el-icon> 新建对话</button>
            <div v-for="s in sessions" :key="s.sessionId" class="panel-item" :class="{ active: s.sessionId === activeSessionId }" @click="switchSession(s.sessionId)">
              <el-icon :size="14" class="panel-item-icon"><ChatLineRound /></el-icon>
              <div class="panel-item-info">
                <div class="panel-item-title">{{ s.title }}</div>
                <div class="panel-item-meta">{{ s.messageCount }} 条消息</div>
              </div>
              <button class="panel-item-del" @click.stop="deleteSession(s.sessionId)"><el-icon :size="12"><Close /></el-icon></button>
            </div>
            <div v-if="!sessions.length" class="panel-empty">暂无对话</div>
          </div>
        </div>
      </transition>
    </div>
  </div>
</template>

<style lang="scss" scoped>
// ── Color Palette (Yuxi-inspired) ──
$primary: #046a82;
$primary-light: #3996ae;
$primary-bg: #f7fbfd;
$text-primary: #151616;
$text-secondary: #4c4d4d;
$text-muted: #979999;
$text-placeholder: #bdbfbf;
$border: #eef0f0;
$border-light: #f0f2f2;
$bg-page: #ffffff;
$bg-card: #ffffff;
$bg-hover: #f5f7f7;
$bg-user-msg: #046a82;
$shadow-sm: 0 1px 3px rgba(0,0,0,0.04);
$shadow-md: 0 2px 8px rgba(0,0,0,0.05);
$shadow-lg: 0 8px 24px rgba(0,0,0,0.06);

.agent-view {
  height: calc(100vh - 60px);
  display: flex;
  flex-direction: column;
  background: $bg-page;
  overflow: hidden;
  font-family: -apple-system, BlinkMacSystemFont, 'Noto Sans SC', 'Roboto', 'Segoe UI', sans-serif;
}

// ── Header ──
.agent-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 45px;
  padding: 0 12px;
  border-bottom: 1px solid $border-light;
  flex-shrink: 0;
  user-select: none;
}

.header-left, .header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.conversation-title {
  font-size: 15px;
  font-weight: 400;
  color: $text-primary;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  .chevron { color: $text-muted; }
}

.icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: $text-secondary;
  cursor: pointer;
  transition: all 0.15s ease;
  &:hover { background: $bg-hover; color: $text-primary; }
  &.active { background: $primary-bg; color: $primary; }
  &.small { width: 24px; height: 24px; }
}

// ── Body ──
.agent-body {
  flex: 1;
  display: flex;
  overflow: hidden;
  position: relative;
}

// Chat area: messages scrollable, input fixed at bottom
.chat-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

// ── Chat Main (scrollable) ──
.chat-main {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  scrollbar-width: thin;
}

.loading-state {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  .spinner { animation: spin 1s linear infinite; color: $text-muted; }
}
@keyframes spin { to { transform: rotate(360deg); } }

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

.app-greeting-card {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  max-width: 80%;
  padding: 20px 24px;
  background: $bg-card;
  border-radius: 12px;
  box-shadow: $shadow-md;
  text-align: left;
  .greeting-icon { color: $primary; flex-shrink: 0; margin-top: 2px; }
  .greeting-text { font-size: 15px; line-height: 1.7; color: $text-primary; white-space: pre-wrap; }
}

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

// ── Markdown ──
.markdown-body {
  :deep(h1), :deep(h2), :deep(h3), :deep(h4), :deep(h5), :deep(h6) {
    margin: 0; font-weight: 600; line-height: 1.1; color: $text-primary;
    padding-top: 4px;
    &:first-child { margin-top: 0; padding-top: 0; }
  }
  :deep(h1) { font-size: 1.25em; }
  :deep(h2) { font-size: 1.12em; }
  :deep(h3) { font-size: 1.04em; }
  :deep(h4), :deep(h5), :deep(h6) { font-size: 1em; }
  :deep(p) { margin: 0; }
  :deep(pre) { background: #f6f8fa; border-radius: 6px; padding: 6px 8px; overflow-x: auto; margin: 0; font-size: 13px; line-height: 1.3; }
  :deep(code) { background: #f0f0f0; padding: 0 3px; border-radius: 3px; font-size: 13px; }
  :deep(pre code) { background: none; padding: 0; }
  :deep(ul), :deep(ol) { padding-left: 16px; margin: 0; }
  :deep(ul) { list-style: disc; }
  :deep(ol) { list-style: decimal; }
  :deep(li) { margin: 0; line-height: 1.3; }
  :deep(li > ul), :deep(li > ol) { margin: 0; }
  :deep(blockquote) { border-left: 3px solid $border; padding-left: 6px; color: $text-muted; margin: 0; font-size: 13px; line-height: 1.3; }
  :deep(table) { border-collapse: collapse; margin: 0; font-size: 13px; line-height: 1.3; th, td { border: 1px solid $border; padding: 3px 5px; } th { background: #f5f7fa; font-weight: 500; } }
  :deep(a) { color: $primary; text-decoration: none; &:hover { text-decoration: underline; } }
  :deep(hr) { border: none; border-top: 1px solid $border; margin: 4px 0; }
  :deep(img) { max-width: 100%; border-radius: 6px; margin: 2px 0; }
  :deep(strong) { font-weight: 600; }
  :deep(em) { font-style: italic; }
}

// ── Input Area ──
.input-area-wrapper {
  flex-shrink: 0;
  padding: 0 24px 16px;
  background: $bg-page;
}
.input-container {
  width: 92%;
  max-width: 1200px;
  margin: 0 auto;
}

.attachment-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}
.attachment-chip {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px 4px 10px;
  border-radius: 8px;
  background: $bg-hover;
  font-size: 12px;
  color: $text-secondary;
  .chip-name { max-width: 120px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .chip-size { color: $text-muted; }
  .chip-remove {
    display: inline-flex; align-items: center; justify-content: center;
    width: 18px; height: 18px; border: none; border-radius: 50%;
    background: transparent; color: $text-muted; cursor: pointer;
    &:hover { background: #e8e8e8; color: #e74c3c; }
  }
}

.input-box {
  border: 1px solid $border;
  border-radius: 0.8rem;
  box-shadow: $shadow-md;
  background: $bg-card;
  padding: 0.5rem 0.75rem 0.4rem;
  transition: all 0.3s ease;
  &:focus-within { border-color: $primary-light; box-shadow: 0 0 0 2px rgba(4,106,130,0.08); }
}

.input-toolbar {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 4px;
}

.toolbar-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px; height: 28px;
  border: none; border-radius: 6px;
  background: transparent; color: $text-muted;
  cursor: pointer; transition: all 0.15s ease;
  &:hover { background: $bg-hover; color: $text-primary; }
}

.toolbar-divider {
  width: 1px; height: 16px;
  background: $border; margin: 0 4px;
}

.model-picker {
  width: 110px;
  :deep(.el-select__wrapper) { border: none; box-shadow: none; min-height: 28px; padding: 0 4px; font-size: 12px; }
  :deep(.el-select__placeholder) { font-size: 12px; }
}

.user-input {
  width: 100%;
  min-height: 44px;
  max-height: 200px;
  padding: 0;
  border: none;
  background: transparent;
  color: $text-primary;
  font-size: 15px;
  line-height: 1.5;
  font-family: inherit;
  outline: none;
  resize: none;
  overflow-y: auto;
  &::placeholder { color: $text-placeholder; }
}

.input-footer-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 6px;
}

.footer-left {
  display: flex;
  align-items: center;
}

.app-picker {
  width: 130px;
  :deep(.el-select__wrapper) { border: none; box-shadow: none; min-height: 28px; padding: 0 4px; font-size: 12px; }
  :deep(.el-select__placeholder) { font-size: 12px; }
}

.send-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px; height: 32px;
  border: none; border-radius: 50%;
  background: $primary;
  color: #fff;
  cursor: pointer;
  transition: all 0.2s ease;
  box-shadow: 0 2px 6px rgba(4,106,130,0.15);
  &:hover { background: $primary-light; box-shadow: 0 4px 10px rgba(4,106,130,0.2); }
  &:disabled { opacity: 0.4; cursor: not-allowed; box-shadow: none; }
  &.stop { background: #e74c3c; &:hover { background: #c0392b; } }
}

.input-hint {
  display: flex;
  justify-content: space-between;
  margin-top: 6px;
  font-size: 12px;
  color: $text-muted;
  padding: 0 4px;
}

// ── Side Panel ──
.side-panel {
  width: 280px;
  background: $bg-card;
  border-left: 1px solid $border;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  overflow: hidden;
}
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid $border-light;
}
.panel-title { font-size: 14px; font-weight: 600; color: $text-primary; }
.panel-body { flex: 1; overflow-y: auto; padding: 8px; display: flex; flex-direction: column; gap: 4px; }

.new-chat-panel-btn {
  display: flex; align-items: center; gap: 6px;
  width: 100%; padding: 8px; border: 1px dashed $border; border-radius: 8px;
  background: transparent; font-size: 13px; color: $text-muted; cursor: pointer;
  &:hover { background: $bg-hover; color: $primary; border-color: $primary-light; }
}

.panel-item {
  display: flex; align-items: center; gap: 8px;
  padding: 8px; border-radius: 8px; cursor: pointer;
  &:hover { background: $bg-hover; }
  &.active { background: $primary-bg; }
  .panel-item-icon { color: $primary; flex-shrink: 0; }
  .panel-item-info { flex: 1; min-width: 0; }
  .panel-item-title { font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: $text-primary; }
  .panel-item-meta { font-size: 11px; color: $text-muted; }
  .panel-item-del {
    display: none; align-items: center; justify-content: center;
    width: 20px; height: 20px; border: none; border-radius: 4px;
    background: transparent; color: $text-muted; cursor: pointer; flex-shrink: 0;
    &:hover { background: #ffe8e8; color: #e74c3c; }
  }
  &:hover .panel-item-del { display: inline-flex; }
}
.panel-empty { text-align: center; color: $text-muted; font-size: 13px; padding: 24px 0; }

.panel-enter-active, .panel-leave-active { transition: all 0.25s ease; }
.panel-enter-from, .panel-leave-to { width: 0; opacity: 0; }
</style>
