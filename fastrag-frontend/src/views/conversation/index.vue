<script setup lang="ts">
import { ref, reactive, computed, onMounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ChatLineRound, ArrowLeft, MoreFilled } from '@element-plus/icons-vue'
import * as api from '@/api'
import { useConversationStream } from '@/composables/useConversationStream'
import { usePagination } from '@/composables/usePagination'
import { useConfirm } from '@/composables/useConfirm'
import { copyToClipboard } from '@/utils/chatActions'
import { buildSessionMarkdown } from '@/utils/sessionMarkdown'
import SessionListPanel from './components/SessionListPanel.vue'
import ConversationChat from './components/ConversationChat.vue'
import ChatComposer from './components/ChatComposer.vue'
import type { ConversationSummary, ConversationChatMessage, ToolCallItem, PresetApp } from '@/types/conversation'
import type { SourceItem } from '@/composables/useConversationStream'

// ===========================================================================
// 会话中心（/conversation）——页面骨架三栏 + 页面态编排
//
// 单一事实源：composerState（kbIds/modelId/appId）是页面唯一范围状态，来源有三——
// URL ?appId= 初始化、getConversation 还原（A04）、用户改动；发送时快照随消息提交，
// 成功后不本地回写，以列表静默刷新（后端固化 request_state 为准）。
// 流式状态归 useConversationStream，页面只读。
// ===========================================================================

const route = useRoute()
const router = useRouter()
const { confirm } = useConfirm()

// ── 会话列表态 ──
const sessions = ref<ConversationSummary[]>([])
const listLoading = ref(false)
const listError = ref('')
const activeConversation = ref<ConversationSummary | null>(null)

const { currentPage, pageSize, total, handleCurrentChange, handleSizeChange } = usePagination(20)

// ── 聊天态 ──
const messages = ref<ConversationChatMessage[]>([])
const historyLoading = ref(false)
const editingMessageId = ref<string | null>(null)

// ── 输入框范围选择（页面级单一状态）──
// toolIds/mcpIds/skillIds 为直聊消费字段（B02 演进）：随 stream 请求体装配执行上下文，
// 挂应用时能力走应用绑定（状态清空、UI 隐藏）。
// skillNames 为 F1 @提及技能（pinned 提示性字段）：挂应用也保留透传（双入口有效），
// 随消息提交后以后端固化 request_state 回显为准。
const composerState = reactive<{
  kbIds: string[]
  modelId: string
  appId: string | null
  appName: string
  toolIds: string[]
  mcpIds: string[]
  skillIds: string[]
  skillNames: string[]
}>({ kbIds: [], modelId: '', appId: null, appName: '', toolIds: [], mcpIds: [], skillIds: [], skillNames: [] })

const presetApp = computed<PresetApp | null>(() =>
  composerState.appId ? { id: composerState.appId, name: composerState.appName || composerState.appId } : null
)

// ── 流式（useConversationStream 持有 isStreaming/abort，页面只读）──
const { isStreaming, send, stopStream } = useConversationStream()

// ── 检索等待（纯前端状态：直聊 kbIds 非空发送后置位，sources/首个 message 增量/end/error 复位）──
const searching = ref(false)

const chatRef = ref<InstanceType<typeof ConversationChat> | null>(null)
const composerRef = ref<InstanceType<typeof ChatComposer> | null>(null)
const panelOpen = ref(true)

// ── 来源抽屉（引用角标点击打开；受控开关经 ConversationChat 透传至 SourcesBlock）──
const sourcesDrawerVisible = ref(false)

const emptyTitle = computed(() => {
  if (activeConversation.value) return '会话已清空，继续提问'
  return presetApp.value ? `正在按应用「${presetApp.value.name}」对话` : '您好，有什么可以帮您？'
})
const emptySubtitle = computed(() =>
  activeConversation.value ? '' : '可在左侧挑选知识库范围与模型，输入问题开始对话'
)

// ===========================================================================
// 会话列表（TASK-002）
// ===========================================================================

async function loadSessions(silent = false) {
  if (!silent) listLoading.value = true
  listError.value = ''
  try {
    const res: any = await api.listConversations({ page: currentPage.value, pageSize: pageSize.value })
    sessions.value = res?.list || []
    total.value = res?.total || 0
  } catch {
    if (!silent) listError.value = '会话列表加载失败'
  } finally {
    if (!silent) listLoading.value = false
  }
}

function handlePageChange(page: number) {
  handleCurrentChange(page)
  loadSessions()
}

function handlePageSizeChange(size: number) {
  handleSizeChange(size)
  loadSessions()
}

// ===========================================================================
// 选中会话（TASK-003 重开恢复范围 + TASK-008 历史引用回看）
// ===========================================================================

async function selectSession(session: ConversationSummary) {
  if (isStreaming.value) { ElMessage.warning('请等待当前回复完成'); return }
  activeConversation.value = { ...session }
  editingMessageId.value = null
  messages.value = []
  sourcesDrawerVisible.value = false
  historyLoading.value = true
  try {
    const detail: any = await api.getConversation(session.id)
    // A04：requestState → composerState（三处写同一次）
    applyRequestState(detail?.requestState, detail)
    messages.value = mapHistoryMessages(detail?.messages || [])
    nextTick(() => chatRef.value?.forceScrollToBottom())
  } catch {
    ElMessage.error('会话详情加载失败')
  } finally {
    historyLoading.value = false
  }
}

/** 挂应用 → 预设 chip（KB/能力由应用决定，禁用多选）；直聊 → 还原 kbIds/modelId/能力三字段（D03 降级） */
function applyRequestState(rs: any, detail?: any) {
  const state = rs || {}
  if (state.appId) {
    const label = detail?.sourceLabel
    composerState.appId = state.appId
    composerState.appName = label && label !== '直聊' ? label : state.appId
    composerState.kbIds = []
    composerState.toolIds = []
    composerState.mcpIds = []
    composerState.skillIds = []
  } else {
    composerState.appId = null
    composerState.appName = ''
    composerState.kbIds = Array.isArray(state.kbIds) ? [...state.kbIds] : []
    // 直聊能力三字段还原（B02 演进：后端固化 request_state 已含）
    composerState.toolIds = Array.isArray(state.toolIds) ? [...state.toolIds] : []
    composerState.mcpIds = Array.isArray(state.mcpIds) ? [...state.mcpIds] : []
    composerState.skillIds = Array.isArray(state.skillIds) ? [...state.skillIds] : []
  }
  // F1 @提及技能回显（请求态随持久化 JSON 返回；双入口均回显）
  composerState.skillNames = Array.isArray(state.skillNames) ? [...state.skillNames] : []
  composerState.modelId = state.modelId || ''
}

/** 工具调用数据标准化（兼容旧版扁平结构和新版嵌套 result 结构，沿 runtime）
 *  app-chat-optimize 二批：result.data 结构化数据透传——嵌套结构原样保留；
 *  扁平结构若顶层带 data，收编进 result（防专用渲染器拿不到结构化数据） */
function normalizeToolCalls(tcs: any[]): ToolCallItem[] {
  return (tcs || []).map(tc => {
    if (tc.result === undefined && (tc.success !== undefined || tc.output !== undefined || tc.durationMs !== undefined)) {
      const { success, output, durationMs, error, data, ...rest } = tc
      const result: ToolCallItem['result'] = { success, output, durationMs, error }
      if (data !== undefined) result.data = data
      return { ...rest, result } as ToolCallItem
    }
    return tc as ToolCallItem
  })
}

function parseMaybeJson(value: any): any {
  if (value == null) return undefined
  if (typeof value === 'string') {
    try { return JSON.parse(value) } catch { return undefined }
  }
  return value
}

function mapHistoryMessages(list: any[]): ConversationChatMessage[] {
  return (list || []).map((m: any) => ({
    id: m.id,
    role: m.role,
    content: m.content || '',
    thinkingContent: m.thinkingContent || undefined,
    toolCalls: m.toolCalls ? normalizeToolCalls(parseMaybeJson(m.toolCalls) || []) : undefined,
    references: parseMaybeJson(m.references) || undefined,
    // 轮数回显（收尾批）：历史消息 iterations 列（AppConversationMessage），非数字跳过——
    // MessageBubble summaryChips 据此显示「M 轮」（与流内 end 事件 data.iterations 同消费）
    iterations: typeof m.iterations === 'number' ? m.iterations : undefined,
    time: m.latencyMs ? `${(m.latencyMs / 1000).toFixed(1)}s` : undefined,
    feedback: m.feedback || undefined,
  }))
}

// ===========================================================================
// 新建对话（TASK-001；无选中会话态，随首条消息建会话）
// ===========================================================================

function createNewConversation() {
  if (isStreaming.value) { ElMessage.warning('请等待当前回复完成'); return }
  activeConversation.value = null
  messages.value = []
  editingMessageId.value = null
  sourcesDrawerVisible.value = false
  nextTick(() => composerRef.value?.focus())
}

// ===========================================================================
// 发送（TASK-004 流式对话）与停止（TASK-005）
// ===========================================================================

function truncate30(text: string): string {
  return text.length > 30 ? text.substring(0, 30) + '...' : text
}

async function handleSend(text: string) {
  if (!text.trim() || isStreaming.value) return

  // 编辑重发：移除原消息及其后所有消息后重发（沿 runtime:167-171）
  const isEdit = editingMessageId.value !== null
  if (isEdit) {
    const editIdx = messages.value.findIndex(m => m.id === editingMessageId.value)
    if (editIdx >= 0) messages.value.splice(editIdx)
    editingMessageId.value = null
  }

  // 无活跃会话时随首条消息建会话（A01 惯例改造 ensureSession）
  let conversationId = activeConversation.value?.id
  if (!conversationId) {
    try {
      const created: any = await api.createConversation({
        appId: composerState.appId || undefined,
        kbIds: composerState.appId ? undefined : composerState.kbIds,
        modelId: composerState.modelId || undefined,
      })
      if (!created?.id) { ElMessage.error('创建会话失败'); return }
      activeConversation.value = { ...created, messageCount: 0 }
      conversationId = String(created.id)
    } catch (e: any) {
      ElMessage.error('创建会话失败: ' + (e?.message || ''))
      return
    }
  }
  if (!conversationId) return

  // 本轮请求态快照（发送成功不本地回写，以后端固化为准）
  // 直聊能力三字段：挂应用时清空发送（能力走应用绑定），直聊随 body 透传（空数组由
  // useConversationStream 归一为 undefined，不占契约字段面）
  // skillNames（F1 @提及）：pinned 提示性字段，挂应用也透传（集外点名后端静默忽略）
  const payload = {
    appId: composerState.appId || null,
    kbIds: composerState.appId ? [] : [...composerState.kbIds],
    modelId: composerState.modelId || null,
    toolIds: composerState.appId ? [] : [...composerState.toolIds],
    mcpIds: composerState.appId ? [] : [...composerState.mcpIds],
    skillIds: composerState.appId ? [] : [...composerState.skillIds],
    skillNames: [...composerState.skillNames],
  }

  messages.value.push({ role: 'user', content: text })
  if (!activeConversation.value!.title) activeConversation.value!.title = truncate30(text)
  const am: ConversationChatMessage = { role: 'assistant', content: '', streaming: true, thinkingContent: '', isThinking: false, toolCalls: [] }
  // 检索空态提示（透明化"为什么没有来源"）：直聊未选知识库时预先告知
  const directNoKb = !composerState.appId && composerState.kbIds.length === 0
  if (directNoKb) am.retrievalHint = '未选择知识库，本次直接由模型回答；选择知识库可获得带引用的来源'
  messages.value.push(am)
  const mi = messages.value.length - 1
  const st = Date.now()
  let gotSources = false
  // 检索等待：仅直聊挂了 kbIds 时展示（挂应用检索范围由应用决定，前端无从预判）
  searching.value = !composerState.appId && composerState.kbIds.length > 0
  chatRef.value?.forceScrollToBottom()

  await send(conversationId, { query: text, ...payload }, {
    onChunk: (c: string, isReplace?: boolean) => {
      searching.value = false // 首个 message 增量即视为检索完成
      messages.value[mi].content = isReplace ? c : messages.value[mi].content + c
      nextTick(() => chatRef.value?.scrollToBottom())
    },
    onSources: (s: SourceItem[]) => {
      gotSources = true
      searching.value = false
      if (s.length) messages.value[mi].sources = s
    },
    onEnd: (_fullContent: string, data: Record<string, unknown>) => {
      searching.value = false
      messages.value[mi].streaming = false
      messages.value[mi].isThinking = false
      // 会话汇总行：本轮工具调用轮数（后端 end 事件 data.iterations，非数字忽略）
      if (typeof data.iterations === 'number') messages.value[mi].iterations = data.iterations
      // 直聊挂了知识库但零命中 → 空态提示（替换未选库提示）
      if (!composerState.appId && composerState.kbIds.length > 0 && !gotSources) {
        messages.value[mi].retrievalHint = '知识库中未检索到相关来源'
      }
      if (!messages.value[mi].time) messages.value[mi].time = `${((Date.now() - st) / 1000).toFixed(1)}s`
      // 静默刷新列表（标题/计数/updatedAt 服务端为准；发送成功不本地回写请求态）
      loadSessions(true)
      nextTick(() => chatRef.value?.scrollToBottom())
    },
    onError: (msg: string) => {
      searching.value = false
      messages.value[mi].content = `抱歉，AI 服务暂时不可用：${msg}`
      messages.value[mi].streaming = false
      messages.value[mi].isThinking = false
      messages.value[mi].time = '-'
      nextTick(() => chatRef.value?.scrollToBottom())
    },
    onThinking: (c: string) => {
      messages.value[mi].thinkingContent = (messages.value[mi].thinkingContent || '') + c
      messages.value[mi].isThinking = true
      nextTick(() => chatRef.value?.scrollToBottom())
    },
    onToolCall: (tc: any) => {
      if (!messages.value[mi].toolCalls) messages.value[mi].toolCalls = []
      const ex = messages.value[mi].toolCalls!.find(t => t.id === tc.id)
      if (ex) ex.arguments = tc.arguments
      else messages.value[mi].toolCalls!.push({ id: tc.id, name: tc.name, arguments: tc.arguments })
      nextTick(() => chatRef.value?.scrollToBottom())
    },
    onToolResult: (r: any) => {
      if (messages.value[mi].toolCalls) {
        const tc = messages.value[mi].toolCalls!.find(t => t.id === r.id)
        if (tc) tc.result = r
      }
      nextTick(() => chatRef.value?.scrollToBottom())
    },
  })
}

/** 停止 = AbortController.abort() + POST stop（双保险）；已生成部分保留标「（已停止）」 */
function handleStop() {
  stopStream()
  const lm = messages.value[messages.value.length - 1]
  if (lm && lm.role === 'assistant' && lm.streaming) {
    lm.streaming = false
    lm.isThinking = false
    lm.time = '(已停止)'
  }
  if (activeConversation.value?.id) {
    api.stopConversation(activeConversation.value.id).catch(() => {})
  }
}

// ===========================================================================
// 复制会话（app-chat-optimize Step 2：头部「⋯」菜单 → Markdown 入剪贴板）
// ===========================================================================

async function handleHeaderCommand(command: string) {
  if (command !== 'copy-session') return
  if (!messages.value.length) { ElMessage.warning('暂无可复制的会话内容'); return }
  const md = buildSessionMarkdown(
    messages.value,
    activeConversation.value?.title || undefined
  )
  const ok = await copyToClipboard(md)
  if (ok) ElMessage.success('会话已复制为 Markdown')
  else ElMessage.error('复制失败')
}

// ===========================================================================
// 会话管理（TASK-006 起标题 / TASK-007 置顶·清空·批量删）
// ===========================================================================

/** LLM 起标题：成功更新标题，失败静默（后端自带首问截 30 字回退，不抛业务错） */
async function handleGenerateTitle(session: ConversationSummary) {
  try {
    const title: any = await api.generateConversationTitle(session.id)
    if (typeof title === 'string' && title) {
      const item = sessions.value.find(s => s.id === session.id)
      if (item) item.title = title
      if (activeConversation.value?.id === session.id) activeConversation.value.title = title
      ElMessage.success('标题已更新')
    }
  } catch {
    // 失败静默保持原标题（TASK-006）
  }
}

/** 置顶 / 取消置顶：置顶即时重排（服务端排序，静默刷新） */
async function handlePin(session: ConversationSummary) {
  try {
    const next = !session.isPinned
    await api.pinConversation(session.id, next)
    await loadSessions(true)
  } catch {
    ElMessage.error('操作失败')
  }
}

/** 清空会话全部消息（会话保留；摘要置空以列表刷新为准） */
async function handleClear(session: ConversationSummary) {
  if (session.id === activeConversation.value?.id && isStreaming.value) {
    ElMessage.warning('请等待当前回复完成')
    return
  }
  const ok = await confirm(`确定清空会话「${session.title || '新对话'}」的全部消息？`, '清空确认')
  if (!ok) return
  try {
    await api.clearConversationMessages(session.id)
    if (activeConversation.value?.id === session.id) {
      messages.value = []
    }
    await loadSessions(true)
    ElMessage.success('已清空')
  } catch {
    ElMessage.error('清空失败')
  }
}

/** 批量删除（级联删消息；确认在列表组件内已完成） */
async function handleDeleteSelected(ids: string[]) {
  if (!ids.length) return
  if (activeConversation.value && ids.includes(activeConversation.value.id) && isStreaming.value) {
    ElMessage.warning('请等待当前回复完成')
    return
  }
  try {
    await api.deleteConversations(ids)
    if (activeConversation.value && ids.includes(activeConversation.value.id)) {
      activeConversation.value = null
      messages.value = []
    }
    // 当前页删空则回退到剩余范围内的最后一页
    const remaining = total.value - ids.length
    const maxPage = Math.max(1, Math.ceil(remaining / pageSize.value))
    if (currentPage.value > maxPage) currentPage.value = maxPage
    await loadSessions()
    ElMessage.success('已删除')
  } catch {
    ElMessage.error('删除失败')
  }
}

// ===========================================================================
// 消息操作继承（TASK-010；新通道未立消息级端点——挂应用会话复用 app 通道
// 消息接口（同一账本），直聊降级为本地操作，springdoc 实现后再收口）
// ===========================================================================

async function handleDeleteMessage(msg: ConversationChatMessage) {
  const conv = activeConversation.value
  if (!conv) return
  if (isStreaming.value) { ElMessage.warning('请等待当前回复完成'); return }
  try {
    const ok = await ElMessageBox.confirm('确定删除此消息？', '确认', { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' })
      .then(() => true).catch(() => false)
    if (!ok) return
    if (msg.id && conv.appId) await api.deleteAppMessage(conv.appId, msg.id)
    const idx = messages.value.findIndex(m => m === msg)
    if (idx >= 0) messages.value.splice(idx, 1)
    ElMessage.success('已删除')
    loadSessions(true)
  } catch {}
}

async function handleFeedbackMessage(msg: ConversationChatMessage, type: string) {
  const conv = activeConversation.value
  if (!msg.id || !conv) return
  try {
    const newFeedback = msg.feedback === type ? null : type
    if (conv.appId) await api.feedbackAppMessage(conv.appId, msg.id, newFeedback || '')
    msg.feedback = newFeedback || undefined
  } catch {
    ElMessage.error('操作失败')
  }
}

function handleEditMessage(msg: ConversationChatMessage) {
  if (isStreaming.value) return
  composerRef.value?.setText(msg.content)
  editingMessageId.value = msg.id || null
  composerRef.value?.focus()
}

// ===========================================================================
// 入口：URL ?appId= 预设挂载（TASK-009 / D01）
// ===========================================================================

async function resolveAppName(appId: string): Promise<string> {
  try {
    const res: any = await api.getApps()
    const list = res?.list || res || []
    const app = (Array.isArray(list) ? list : []).find((a: any) => a.id === appId)
    if (app?.name) return app.name
  } catch { /* ignore */ }
  return appId
}

async function applyAppPreset(appId: string) {
  composerState.appId = appId
  composerState.appName = await resolveAppName(appId)
  composerState.kbIds = []
  // 预设切换开启新对话（沿用 runtime selectApp 重置会话语义）
  activeConversation.value = null
  messages.value = []
}

function removePreset() {
  composerState.appId = null
  composerState.appName = ''
  // 移除预设转直聊，KB 多选解禁（D02）；kbIds 保持当前值（直聊空范围则由用户自选）
}

/** 输入区应用下拉 change → composerState.appId（与预设 chip 单一事实源合一，双向同步） */
async function handleComposerAppChange(appId: string | null) {
  if (!appId) { removePreset(); return }
  if (appId === composerState.appId) return
  composerState.appId = appId
  // 挂应用 = KB 范围与能力均由应用绑定决定（D02 互斥保持；能力 popover 隐藏）
  composerState.kbIds = []
  composerState.toolIds = []
  composerState.mcpIds = []
  composerState.skillIds = []
  composerState.appName = await resolveAppName(appId)
}

watch(() => route.query.appId, (n) => {
  const appId = typeof n === 'string' ? n : ''
  if (appId && appId !== composerState.appId) applyAppPreset(appId)
})

onMounted(() => {
  loadSessions()
  const qAppId = typeof route.query.appId === 'string' ? route.query.appId : ''
  if (qAppId) applyAppPreset(qAppId)
})
</script>

<template>
  <div class="conversation-view">
    <!-- Header -->
    <header class="conversation-header">
      <div class="header-left">
        <button class="icon-btn" title="返回" @click="router.back()"><el-icon :size="18"><ArrowLeft /></el-icon></button>
        <span class="conversation-title">{{ activeConversation?.title || (presetApp ? `新对话 · ${presetApp.name}` : '新对话') }}</span>
      </div>
      <div class="header-right">
        <el-dropdown trigger="click" @command="handleHeaderCommand">
          <button class="icon-btn" title="更多操作">
            <el-icon :size="18"><MoreFilled /></el-icon>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="copy-session">复制会话</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <button class="icon-btn panel-toggle" :class="{ active: panelOpen }" title="会话列表" @click="panelOpen = !panelOpen">
          <el-icon :size="18"><ChatLineRound /></el-icon>
        </button>
      </div>
    </header>

    <!-- Body：左列表 + 右聊天 -->
    <div class="conversation-body">
      <SessionListPanel
        :sessions="sessions"
        :active-id="activeConversation?.id || ''"
        :loading="listLoading"
        :error="listError"
        :current-page="currentPage"
        :page-size="pageSize"
        :total="total"
        :class="{ open: panelOpen }"
        @select="selectSession"
        @create="createNewConversation"
        @retry="loadSessions()"
        @generate-title="handleGenerateTitle"
        @pin="handlePin"
        @clear="handleClear"
        @delete-selected="handleDeleteSelected"
        @page-change="handlePageChange"
        @size-change="handlePageSizeChange"
      />

      <!-- Chat area (column: messages + input) -->
      <div class="chat-area">
        <ConversationChat
          ref="chatRef"
          :messages="messages"
          :is-streaming="isStreaming"
          :searching="searching"
          :loading="historyLoading"
          :empty-title="emptyTitle"
          :empty-subtitle="emptySubtitle"
          v-model:sources-drawer-visible="sourcesDrawerVisible"
          @delete-message="handleDeleteMessage"
          @feedback-message="handleFeedbackMessage"
          @edit-message="handleEditMessage"
          @open-sources="sourcesDrawerVisible = true"
        />

        <ChatComposer
          ref="composerRef"
          v-model:kb-ids="composerState.kbIds"
          v-model:model-id="composerState.modelId"
          :app-id="composerState.appId"
          v-model:tool-ids="composerState.toolIds"
          v-model:mcp-ids="composerState.mcpIds"
          v-model:skill-ids="composerState.skillIds"
          v-model:skill-names="composerState.skillNames"
          :preset-app="presetApp"
          :disabled="isStreaming"
          @update:app-id="handleComposerAppChange"
          @submit="handleSend"
          @stop="handleStop"
          @remove-preset="removePreset"
        />
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
// ── Color Palette（沿 runtime.vue 三段式布局语义）──
$primary-bg: #f7fbfd;
$primary-light: #3996ae;
$text-primary: #151616;
$text-secondary: #4c4d4d;
$border: #eef0f0;
$border-light: #f0f2f2;
$bg-page: #ffffff;
$bg-hover: #f5f7f7;

.conversation-view {
  height: calc(100vh - 60px);
  display: flex;
  flex-direction: column;
  background: $bg-page;
  overflow: hidden;
}

// ── Header ──
.conversation-header {
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
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
  &.active { background: $primary-bg; color: $primary-light; }
}

// 响应式：<1024px 左栏抽屉化（沿 runtime Side Panel 开关交互）
.panel-toggle { display: none; }

// ── Body ──
.conversation-body {
  flex: 1;
  display: flex;
  overflow: hidden;
  position: relative;
}

.chat-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

@media (max-width: 1279px) {
  .panel-toggle { display: inline-flex; }
}

@media (max-width: 1023px) {
  .conversation-body > .session-panel {
    position: absolute;
    left: 0;
    top: 0;
    bottom: 0;
    z-index: 20;
    box-shadow: 4px 0 16px rgba(0,0,0,0.08);
    display: none;
    &.open { display: flex; }
  }
}
</style>
