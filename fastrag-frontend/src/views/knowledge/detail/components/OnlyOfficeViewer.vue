<script setup lang="ts">
/**
 * 嵌入式 OnlyOffice Viewer（非全屏，精简查看器）。
 *
 * <p>与 {@link OnlyOfficeEditorDialog} 的区别：
 * <ul>
 *   <li>无 fullscreen 包裹，直接渲染到父容器（典型用于 AiChunkPanel 左栏）</li>
 *   <li><b>UI 精简</b>：隐藏工具栏/标题栏；不强制 view 模式（只读态外层不派发选区事件，
 *       选区捕获需要在编辑构建下工作），面板不提供主动编辑入口</li>
 *   <li>支持 {@code focusChunkId} 触发滚动定位</li>
 *   <li>选区常驻捕获：用户三击整段/拖选范围后，防抖上报文本（{@code captured} 事件），
 *       父页面负责对齐解析模型段落并创建分片（无模式开关）</li>
 * </ul>
 *
 * <p>用法：
 * <pre>
 *   <OnlyOfficeViewer
 *     :file="file"
 *     :kb-id="kbId"
 *     :chunks="chunks"
 *     :focus-chunk-id="focusChunkId"
 *     @captured="onCaptured"
 *   />
 * </pre>
 */
import { ref, watch, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import type { KnowledgeFile } from '@/types/knowledge'
import { useOnlyOfficeEditor } from '@/composables/useOnlyOffice'
import * as api from '@/api'

interface ChunkCardLite {
  id: string
  index: number
  pageNumber?: number
  title?: string
}

const props = defineProps<{
  kbId?: string
  file: KnowledgeFile
  /** 由父页面传入的所有 chunks（用于 chunk-click 跳转） */
  chunks?: ChunkCardLite[]
  /** 当前要高亮跳转到哪个 chunk；变化时编辑器滚动到对应页 */
  focusChunkId?: string | null
}>()

const emit = defineEmits<{
  /** 选区创建 chunk 后通知父页面（父页面决定是否刷新列表） */
  (e: 'chunk-created', chunk: unknown): void
  /** 编辑器加载完成（可隐藏 loading 等） */
  (e: 'ready'): void
  /** 编辑器报错 */
  (e: 'error', message: string): void
  /**
   * 选区捕获结果（防抖 800ms，常驻监听）。空串表示无有效选区（清空），
   * 非空表示用户选中的文本。父页面需对齐解析模型段落后调用 api.createChunk。
   */
  (e: 'captured', text: string): void
  /** 插件选区桥存活状态变化：true=心跳正常，false=插件窗口被关闭/断开 */
  (e: 'bridge-state', alive: boolean): void
}>()

const PLACEHOLDER_ID = 'onlyoffice-viewer-placeholder'

const {
  init,
  destroy,
  scrollToPage,
  isReady,
  error: ooError,
} = useOnlyOfficeEditor()

const loading = ref(true)
const loadProgress = ref(0)

const statusText = computed(() => {
  if (ooError.value) return `错误：${ooError.value}`
  if (!isReady.value) return loading.value ? `加载中… ${loadProgress.value}%` : '初始化…'
  return '就绪'
})

// ---- 加载 ----
watch(
  () => [props.file?.id, props.kbId] as const,
  async ([fileId, kbId]) => {
    if (!fileId || !kbId) return
    await loadEditor()
  },
  { immediate: false },
)

onMounted(async () => {
  await loadEditor()
})

onBeforeUnmount(() => {
  closeSelectionChannel()
  destroy()
})

async function loadEditor() {
  if (!props.kbId || !props.file) return
  loading.value = true
  loadProgress.value = 10
  try {
    const config = await api.getOnlyOfficeConfig(props.kbId, props.file.id)
    loadProgress.value = 60
    // ★ 把宿主页面 origin 塞给编辑器（随 user.id 下发），插件由此确定回传目标，
    //   彻底解决 localhost/127.0.0.1/局域网 IP 等访问方式下的源不一致问题。
    //   格式："<origin>|<原userId>"；仅本前端与插件桥消费，不影响 OO 行为。
    const hostOriginKey = `${location.protocol}//${location.hostname}` +
      (location.port ? `:${location.port}` : '')
    if (config.editorConfig?.user?.id != null) {
      config.editorConfig.user.id = `${hostOriginKey}|${config.editorConfig.user.id}`
      config.editorConfig.user.name = `${config.editorConfig.user.name || 'user'}`
    }
    // 调试：确认后端下发的模式/权限（view 构建下外层不派发 onSelectionChanged）
    console.log(
      '[OO] config loaded:',
      'mode=', config.editorConfig?.mode,
      'permissions=', JSON.stringify(config.document?.permissions),
      'file=', props.file.name,
    )
    // 精简 UI：隐藏编辑工具栏/标题栏（编译式调整）。
    // ⚠ 不强制 mode=view：OO 只读模式下外层 onSelectionChanged 不派发（community 已知限制），
    //   选区捕获走外层事件链条时必须保持编辑构建；工具栏已隐藏，面板不提供主动编辑入口
    config.editorConfig = config.editorConfig || {}
    config.editorConfig.customization = {
      ...(config.editorConfig.customization || {}),
      autosave: false,
      forcesave: false,
      compactHeader: true,
      compactToolbar: true,
      hideRulers: true,
      hideRightMenu: true,
      goback: false,
      help: false,
      feedback: false,
      about: false,
    }
    // 插件桥：chunk-boundary 插件已由 Document Server 服务端注册（docker-compose 挂载到
// sdkjs-plugins/ + local.json autostart），插件窗口与脚本均从服务端清单加载。
// ⚠ 不要在此注入 editorConfig.plugins：客户端清单会在编辑器合并去重时抢占服务端
// 完整清单（config.json 含 size/isVisual 等必要字段），导致插件窗口无法打开（已用
// scripts/oo-plugin-probe.mjs 差分验证：注入=不执行，服务端=执行）。
    // 注入选区捕获 / 状态事件回调
    const evts: any = config.editorConfig
    evts.events = evts.events || {}
    evts.events.onAppReady = () => {
      console.log('[OO] onAppReady（编辑器已就绪）')
      loading.value = false
      loadProgress.value = 100
      emit('ready')
    }
    evts.events.onDocumentReady = () => {
      loading.value = false
    }
    evts.events.onError = (e: any) => {
      const msg = e?.errorDescription || '未知错误'
      emit('error', msg)
    }
    await nextTick()
    await init(PLACEHOLDER_ID, config)
    console.log('[OO] init 完成，isReady=', isReady.value)
    openSelectionChannel()
  } catch (e: any) {
    const msg = e?.message || String(e)
    console.log('[OO] loadEditor 异常:', msg)
    emit('error', msg)
    ElMessage.error(`OnlyOffice 加载失败: ${msg}`)
  } finally {
    loading.value = false
  }
}

// ---- chunk → 跳转 ----
watch(
  () => props.focusChunkId,
  (id) => {
    if (!id || !props.chunks) return
    const card = props.chunks.find((c) => c.id === id)
    if (!card) return
    const page = card.pageNumber ?? 1
    scrollToPage(page)
  },
)

// ---- 选区 → 创建 chunk ----
// 事实：OO 7.4 文档编辑器外层 DocsAPI 既无 onSelectionChanged 事件、也无 getSelectedText 方法
// （已在容器 web-apps 的 api.js / word 引擎 app.js 核实字符串 0 次命中）。
// 解决方案：chunk-boundary 插件运行在编辑器内部，轮询 executeMethod('GetSelectedText')，
// 经 BroadcastChannel（插件与前端同为 Vite 3000 同源）广播选中文本到本组件。
const SEL_CHANNEL = 'fastrag-oo-selection'
let selChannel: BroadcastChannel | null = null
let lastPingTs = 0
/** 桥状态机：warming=从未收到过任何信号（插件可能在冷启/或 OO autostart 竞态没派发）；alive=心跳正常；down=曾经活跃后失联 */
type BridgeState = 'warming' | 'alive' | 'down'
let bridgeState: BridgeState = 'warming'
let bridgeWatch: ReturnType<typeof setInterval> | null = null

/** 任一通道收到存活信号（ping/选区）时调用 */
function markBridgeAlive() {
  const first = bridgeState !== 'alive'
  lastPingTs = Date.now()
  if (first) {
    bridgeState = 'alive'
    console.log('[OO] bridge-state -> alive（首次收到存活信号）')
    emit('bridge-state', true)
  }
}

function onSelectionBroadcast(e: MessageEvent) {
  const d = (e as MessageEvent).data
  if (d?.type === 'oo-selection-ping') {
    markBridgeAlive()
    return
  }
  if (d?.type === 'oo-selection') {
    console.log('[OO] 插件桥收到选区:', d.text ? `"${d.text.slice(0, 40)}…"（${d.text.length} 字）` : '(空)')
    markBridgeAlive()
    emit('captured', d.text || '')
  }
}

function onPluginRelayedMessage(e: MessageEvent) {
  const d: any = e?.data
  const msg = d?.type === 'oo-selection' ? d : d?.data?.type === 'oo-selection' ? d.data : null
  if (msg) {
    console.log('[OO] 宿主消息通道收到选区:', msg.text ? `${msg.text.length} 字` : '(空)')
    markBridgeAlive()
    emit('captured', msg.text || '')
  } else if (d?.type === 'oo-selection-ping') {
    markBridgeAlive()
  } else if (d?.data?.type === 'oo-selection-ping') {
    markBridgeAlive()
  }
}

function openSelectionChannel() {
  try {
    selChannel = new BroadcastChannel(SEL_CHANNEL)
    selChannel.onmessage = onSelectionBroadcast
    console.log('[OO] 选区通道（BroadcastChannel）已开启')
  } catch (e) {
    console.log('[OO] BroadcastChannel 不可用，将无选区捕获:', e)
  }
  // 服务端托管插件（8082）与宿主跨源：BroadcastChannel 失效，
  // 依赖 OO 官方 onExternalPluginMessageCallback 转发（宿主顶层 window 也能收到该 postMessage）
  window.addEventListener('message', onPluginRelayedMessage)
  // 心跳监测（状态机）：
  //   warming：从未收到任何信号 —— 静默等待 60s。期间绝不报 DOWN、不触发兜底切换
  //   （插件冷启需数秒；OO 7.4 autostart 竞态也会导致永远不启动——后者由超时兜底接管）。
  //   alive：收到过信号；OO 会暂停不活跃插件 iframe 的定时器/回调（实测最长停摆十几秒），
  //          故判死阈值放宽到 20s —— 窗口被真正关闭时 postMessage 全灭,20s 必然暴露。
  //   down：emit false（面板提示恢复 / 自动切结构视图）；信号重现 → 回 alive。
  let warmingDeadline = Date.now() + 60_000
  bridgeWatch = setInterval(() => {
    const now = Date.now()
    if (bridgeState === 'warming') {
      if (now > warmingDeadline) {
        bridgeState = 'down'
        console.log('[OO] bridge-state -> down（60s 内未出现任何存活信号，判定插件未启动）')
        emit('bridge-state', false)
      }
      return
    }
    const alive = now - lastPingTs < 20_000
    if (!alive && bridgeState === 'alive') {
      bridgeState = 'down'
      console.log('[OO] bridge-state -> down（存活信号消失 >20s，小窗可能被关闭或挂起）')
      emit('bridge-state', false)
    } else if (alive && bridgeState === 'down') {
      bridgeState = 'alive'
      emit('bridge-state', true)
    }
  }, 2000)
}

function closeSelectionChannel() {
  if (selChannel) {
    selChannel.close()
    selChannel = null
  }
  if (bridgeWatch) clearInterval(bridgeWatch)
  bridgeWatch = null
  window.removeEventListener('message', onPluginRelayedMessage)
}
</script>

<template>
  <div class="oo-viewer">
    <div :id="PLACEHOLDER_ID" class="oo-viewer__iframe" />
    <div v-if="loading" class="oo-viewer__loading">
      <span>{{ statusText }}</span>
    </div>
  </div>
</template>

<style scoped lang="scss">
.oo-viewer {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 360px;
  background: #fff;
  border-radius: 4px;
  overflow: hidden;
}

.oo-viewer__iframe {
  width: 100%;
  height: 100%;
}

.oo-viewer__loading {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.92);
  color: #909399;
  font-size: 13px;
  pointer-events: none;
}
</style>
