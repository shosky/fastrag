<script setup lang="ts">
import type { KnowledgeFile, AiChunkParagraphType, AiChunkResult, AiChunkParagraph, AiChunkLayoutBlock } from '@/types/knowledge'
import { Refresh, Document, Check, MagicStick, CopyDocument, Edit, Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { storage } from '@/utils/storage'
import mammoth from 'mammoth'
import { init as initPptx } from 'pptx-preview'
import AiChunkPdfOverlay from './AiChunkPdfOverlay.vue'
import OnlyOfficeViewer from './OnlyOfficeViewer.vue'
import { isOfficeFile } from '@/config'
import * as api from '@/api'

const props = defineProps<{
  modelValue: boolean
  kbId?: string
  file: KnowledgeFile | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'applied', fileId: string): void
  /** chunk 卡被点击：父组件可触发 OnlyOfficeEditorDialog 跳转到对应页 */
  (e: 'chunk-click', chunk: { id: string; index: number; pageNumber?: number; title?: string }): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v),
})

// ---- 状态机：idle（右侧空） -> parsing（自动分片中）/ loading（左件拉取中） -> done -> applying ----
type Status = 'idle' | 'loadingLeft' | 'parsing' | 'done' | 'applying'
const status = ref<Status>('idle')
const progress = ref(0)
const stage = ref('')

// ---- 双向联动锚点（一律按 paragraphId 匹配；跨页合并块多页片段共享同一 id）----
const hoverParagraphId = ref<string | null>(null)
const activeChunkId = ref<string | null>(null)

// ---- 左侧视图：真实文件渲染（默认，一比一原件） / 结构视图（选中分片） ----
const leftView = ref<'real' | 'model'>('real')
const realLoading = ref(false)
const realError = ref('')
const objectUrl = ref('')          // PDF blob url
const officeHtml = ref('')         // DOCX mammoth HTML（OnlyOffice 不可用时的兜底）
const officeHoverable = ref(false) // 段落锚点是否注入
const officeDocRef = ref<HTMLDivElement | null>(null)
const officePptxRef = ref<HTMLDivElement | null>(null)
const isPptx = computed(() => {
  const ext = (props.file?.name.split('.').pop() || '').toLowerCase()
  return ext === 'pptx' || ext === 'ppt'
})
const isOffice = computed(() => isOfficeFile(props.file?.name || ''))  // OnlyOffice 支持的文件类型
const isPdf = computed(() => (props.file?.name.split('.').pop() || '').toLowerCase() === 'pdf')
// OnlyOffice 已捕获的选区文本（常驻防抖捕获，父页面「创建分片」按钮消费；'' = 无有效选区）
const officeCapturedText = ref('')
// 选区桥存活状态：插件小窗被关闭时变 false，提示用户恢复方式
const selBridgeDown = ref(false)

function onBridgeState(alive: boolean) {
  selBridgeDown.value = !alive
  console.log('[OO] bridge-state ->', alive ? 'alive' : 'DOWN')
  if (!alive && isOffice.value && leftView.value === 'real') {
    // B 线兜底：插件桥断开时自动切到结构视图，段落选择不中断
    leftView.value = 'model'
    ElMessage.info('原件选区监听已断开，已切换到结构视图——点击段落即可生成分片')
  }
}
// OnlyOffice 跳转目标 chunk（chunk-click 触发）
const officeFocusChunkId = ref<string | null>(null)
let pptxPreviewer: any = null

// ---- 数据 ----
const leftError = ref('')   // 左侧结构视图加载失败提示（不展示演示数据）
const dirtyCount = ref(0)
const editingChunkId = ref<string | null>(null)
const editText = ref('')
let chunkSeq = 0

// ---- 左侧片段与选中 ----
interface LeftFragment {
  key: string            // page#index 唯一键（仅渲染 key，联动一律用 paragraphId）
  paragraphId: string
  type: AiChunkParagraphType
  text: string
  label: string
  page: number
  crossPage?: boolean
  imageKey?: string
}
interface LeftPage { page: number; fragments: LeftFragment[] }
const leftPages = ref<LeftPage[]>([])
const selectedKeys = ref<Set<string>>(new Set())

// ---- 右侧分片卡（手动 + 自动） ----
interface ChunkBlock { paragraphId: string; type: AiChunkParagraphType; text: string; page: number; label?: string }
interface ChunkCard {
  id: string
  index: number
  source: 'manual' | 'auto'
  blocks: ChunkBlock[]
  pageRange: string
  edited?: boolean
}
const chunkCards = ref<ChunkCard[]>([])

/** 喂给 OnlyOfficeViewer 的精简 chunks（用于 chunk-click 跳转） */
const officeChunks = computed(() =>
  chunkCards.value.map((c) => ({
    id: c.id,
    index: c.index,
    pageNumber: c.blocks[0]?.page,
    title: c.blocks.map((b) => b.text).join(' ').slice(0, 60),
  })),
)

/** OnlyOffice 选区创建 chunk 后的回调 */
async function onOfficeChunkCreated() {
  // 重新拉取 chunk 列表以同步本地状态
  await initPanel()
}

/** OnlyOffice 选区捕获结果：防抖后由 OnlyOfficeViewer 上报，空串表示无有效选区 */
function onOfficeCaptured(text: string) {
  console.log('[OO] AiChunkPanel.onOfficeCaptured 收到:', text ? `${text.length} 字` : '(空)')
  officeCapturedText.value = text
}

// 调试：生成分片按钮可点性状态（选中/捕获 任一有值即点亮）
watch([officeCapturedText, selectedKeys], () => {
  console.log('[OO] 「生成分片」按钮状态:', {
    capturedLen: officeCapturedText.value?.length ?? 0,
    selectedKeys: selectedKeys.value.size,
    disabled: selectedKeys.value.size === 0 && !officeCapturedText.value,
  })
})

/** 通用「生成分片」入口：原件渲染(OFFICE)有捕获选区 → 对齐创建；结构视图有勾选段落 → 段创建 */
function handleCreateChunk() {
  if (officeCapturedText && isOffice.value && leftView.value === 'real') {
    createOfficeChunk()
  } else if (selectedKeys.value.size > 0) {
    createManualChunk()
  } else {
    ElMessage.warning('请先在左侧选中内容（结构视图点选段落，或在原件中三击/框选文字）')
  }
}

/**
 * 把 OO 划选/三击得到的文本，对齐到解析模型段落（alignParagraphs）。
 * 与结构视图「选段」一致：命中后取整段（或多段）全文作为 chunk 内容，页码取模型值。
 * @returns 对齐结果：paragraphIds / 段全文 / 起始页；未命中返回 null（退化为自由文本）
 */
/** 把对齐段落按各自类型重建为 Markdown（表格→管道表 / 标题→## / 列表→- / 代码→围栏） */
function parasToMarkdown(paras: AiChunkParagraph[]): string {
  const parts: string[] = []
  for (const p of paras) {
    if (!p.text) continue
    switch (p.type) {
      case 'heading':
        parts.push('## ' + p.text.replace(/^#+\s*/, ''))
        break
      case 'table': {
        const rows = p.text.split('\n').filter((r) => r.trim())
        const cellRows = rows.map((r) =>
          r.split('\t').map((c) => c.trim().replace(/\|/g, '\\|')),
        )
        if (cellRows.length === 0 || cellRows[0].length === 0) {
          parts.push(p.text)
          break
        }
        const cols = Math.max(...cellRows.map((r) => r.length))
        const pad = (row: string[]) => row.concat(Array(cols).fill('')).slice(0, cols)
        parts.push('| ' + pad(cellRows[0]).join(' | ') + ' |')
        parts.push('|' + Array(cols).fill(' --- ').join('|') + '|')
        for (let ri = 1; ri < cellRows.length; ri++) {
          parts.push('| ' + pad(cellRows[ri]).join(' | ') + ' |')
        }
        break
      }
      case 'list':
        for (const line of p.text.split('\n')) {
          if (line.trim()) parts.push('- ' + line.trim().replace(/^[-•·]\s*/, ''))
        }
        break
      case 'code':
        parts.push('```\n' + p.text + '\n```')
        break
      default:
        parts.push(p.text)
    }
  }
  return parts.join('\n\n')
}

interface AlignedCapture {
  paragraphIds: string[]
  paras: AiChunkParagraph[]
  page: number
}

function matchCapturedToParagraphs(raw: string): AlignedCapture | null {
  const norm = (t: string) => t.replace(/\s+/g, '')
  const target = norm(raw)
  if (!target) return null
  const paras = alignParagraphs.value?.filter((p) => p.text) ?? []
  if (paras.length === 0) return null

  // ★ 首选：跨段连续运行匹配。从每个起点累加归一化段落文本，
  //   只要累计串包含选区（涵盖：恰好整段集 / 始末于段中 / 单段自身），即取
  //   「整段集合」为结果——这是多段选择时的正确形态（此前版本在此处会错误地
  //   提前命中"单段包含"分支导致只取第一段）。
  for (let i = 0; i < paras.length; i++) {
    let acc = ''
    const objs: AiChunkParagraph[] = []
    for (let j = i; j < paras.length; j++) {
      const pj = norm(paras[j].text)
      if (!pj) continue
      acc += pj
      objs.push(paras[j])
      if (acc.includes(target)) {
        return {
          paragraphIds: objs.map((o) => o.id),
          paras: objs,
          page: paras[i].page ?? 1,
        }
      }
      if (acc.length >= target.length) break
    }
  }
  return null
}

/** 用已捕获的 OO 选区创建分片：优先对齐解析段落（段落级 chunk），未命中退化为选中原文（立即落库，origin=manual） */
async function createOfficeChunk() {
  if (!props.kbId || !props.file) return
  const text = officeCapturedText.value?.trim()
  if (!text || text.length < 2) {
    ElMessage.warning('请先在文档中选中内容（三击整段或框选文字）')
    return
  }
  const aligned = matchCapturedToParagraphs(text)
  // 对齐成功 → 按解析段落类型重建 Markdown（表格还原为管道表、标题/列表/代码保形）
  // 未对齐 → 退化为选中文本原文
  const content = aligned ? parasToMarkdown(aligned.paras) : text
  const pageNumber = aligned ? aligned.page : undefined
  if (!aligned) {
    ElMessage.info('未精确匹配到解析段落，按选中文本原文创建')
  }
  try {
    const created: any = await api.createChunk(props.kbId, {
      fileId: props.file.id,
      content,
      chunkType: 'text',
      ...(pageNumber ? { pageNumber } : {}),
    })
    officeCapturedText.value = ''
    ElMessage.success('已创建分片')
    // 直接在右侧追加手动卡（不要 initPanel 全量重置：那会清空草稿区且不回显已落库分片）
    chunkCards.value.push({
      id: String(created?.id ?? `${props.file.id}_manual_${chunkCards.value.length + 1}`),
      index: chunkCards.value.length,
      source: 'manual',
      blocks: [{ paragraphId: aligned?.paragraphIds?.[0] ?? 'manual', type: 'paragraph', text: content, page: pageNumber ?? 1 }],
      pageRange: String(pageNumber ?? '—'),
    })
  } catch (e: any) {
    ElMessage.error(`创建失败: ${e?.message || e}`)
  }
}

/**
 * 按结构自动分片（规则流，不调 LLM）：让服务端确保文件绑定 structure_aware 策略后
 * 走 re-chunk 全流水线重切；手动分片保留。有未应用草稿时先确认丢弃。
 */
async function structureChunk() {
  if (!props.kbId || !props.file) return
  if (chunkCards.value.length > 0) {
    try {
      await ElMessageBox.confirm(
        '将丢弃当前未应用的分片草稿，并按文档结构（标题/小节）重新分片（手动分片会保留）。继续？',
        '按结构分片',
        { type: 'warning', confirmButtonText: '按结构分片', cancelButtonText: '取消' },
      )
    } catch {
      return
    }
  }
  try {
    await api.reChunkFile(props.kbId, props.file.id, { presetStrategy: 'structure_aware' })
    // 立即清空本地草稿（re-chunk 为异步 pipeline，完成后文件状态回 completed）
    chunkCards.value = []
    selectedKeys.value = new Set()
    officeCapturedText.value = ''
    ElMessage.success('已触发按结构分片，处理中…')
    await initPanel()
  } catch (e: any) {
    ElMessage.error(`触发失败: ${e?.message || e}`)
  }
}

// 后端返回的对齐模型（用于左侧渲染与分片块取材）
const alignParagraphs = ref<AiChunkParagraph[]>([])
// PDF 内容图片渲染位置（旁路可视化数据：原件渲染画 image 框，不参与段落对齐模型）
const imageBoxes = ref<AiChunkResult['imageBoxes']>([])
// VLM 版面分析块（原件渲染分块画框；缓存命中随 preview 返回，否则走 SSE 按需生成）
const layoutBlocks = ref<AiChunkLayoutBlock[]>([])
// 版面分析 SSE 中断句柄（面板关闭时 abort）
let layoutAbort: AbortController | null = null
// 结构视图图片 blob url 缓存（imageKey → blob url，带 Auth 拉取）
const imageBlobs = ref<Record<string, string>>({})

// ===========================================================================
// 基础工具
// ===========================================================================
function parseTable(text: string): string[][] {
  return text.split('\n').map((r) => r.split('\t'))
}

function pageRangeOfBlocks(blocks: ChunkBlock[]): string {
  const pages = [...new Set(blocks.map((b) => b.page))].sort((a, b) => a - b)
  return pages.length > 1 ? `${pages[0]}-${pages[pages.length - 1]}` : `${pages[0] ?? '—'}`
}

function charCountOf(card: ChunkCard): number {
  return card.blocks.reduce((n, b) => n + b.text.length, 0)
}

function typeLabel(type: AiChunkParagraphType): string {
  const m: Record<AiChunkParagraphType, string> = { paragraph: '段落', heading: '标题', table: '表格', code: '代码块', list: '列表', image: '图片', caption: '题注' }
  return m[type]
}

function truncate(s: string, n: number): string {
  return s.length <= n ? s : s.slice(0, n) + '…'
}

function sleep(ms: number) {
  return new Promise((r) => setTimeout(r, ms))
}

// ===========================================================================
// 打开：拉取原件（对齐模型 fragment）→ 右侧保持空；后端不可用回退演示
// ===========================================================================
async function initPanel() {
  status.value = 'idle'
  progress.value = 0
  stage.value = ''
  chunkCards.value = []
  alignParagraphs.value = []
  selectedKeys.value = new Set()
  dirtyCount.value = 0
  editingChunkId.value = null
  hoverParagraphId.value = null
  activeChunkId.value = null
  chunkSeq = 0
  leftError.value = ''
  Object.values(imageBlobs.value).forEach((u) => URL.revokeObjectURL(u))
  imageBlobs.value = {}
  leftView.value = 'real'
  realLoading.value = false
  realCleanup()
  alignParagraphs.value = []
  imageBoxes.value = []
  layoutAbort?.abort()
  layoutAbort = null
  layoutBlocks.value = []

  if (props.kbId && props.file) {
    await loadRealLeft()
  } else {
    leftError.value = '缺少知识库/文件信息，无法加载解析数据'
  }
}

/** 真实左侧：ai-chunk-preview 返回对齐模型 → 每段按 pages 在每页渲染一个片段（跨页共享 paragraphId） */
async function loadRealLeft() {
  if (!props.kbId || !props.file) return
  status.value = 'loadingLeft'
  stage.value = '加载原件…'
  try {
    // 拦截器已解包 {code,data,message} → res 即 AiChunkResult 本体
    const data = (await api.aiChunkPreview(props.kbId, props.file.id, false)) as unknown as AiChunkResult | null
    if (!data || !Array.isArray(data.paragraphs)) {
      throw new Error('无解析结果')
    }
    alignParagraphs.value = data.paragraphs
    imageBoxes.value = data.imageBoxes ?? []
    layoutBlocks.value = data.layoutBlocks ?? []
    leftPages.value = paragraphsToLeftPages(data.paragraphs)
    // 版面分析缓存未命中（首开）→ 走 SSE 按需逐页生成
    if (layoutBlocks.value.length === 0) {
      startLayoutStream()
    }
    leftError.value = ''
    status.value = 'idle'
    await loadFragmentImages() // 结构视图图片渲染（异步，失败不阻断）
  } catch (e: any) {
    // 后端不可用 → 展示错误，不回落演示数据
    console.error('[AiChunk] loadRealLeft failed:', e)
    leftPages.value = []
    leftError.value = e?.message || '无法加载解析数据'
    status.value = 'idle'
  }
}

/** 版面分析 SSE：逐页 VLM 识别内容块，layout 事件累计追加，done/error/面板关闭结束 */
async function startLayoutStream() {
  if (!props.kbId || !props.file || props.file.name.split('.').pop()?.toLowerCase() !== 'pdf') return
  layoutAbort?.abort()
  const ac = new AbortController()
  layoutAbort = ac
  const token = storage.get('token')
  try {
    const response = await fetch(api.aiChunkLayoutStreamUrl(props.kbId, props.file.id), {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      signal: ac.signal,
    })
    if (!response.ok || !response.body) return
    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      let idx: number
      while ((idx = buffer.indexOf('\n\n')) >= 0) {
        const frame = buffer.slice(0, idx)
        buffer = buffer.slice(idx + 2)
        const evtLine = frame.split('\n').find((l) => l.startsWith('event:'))
        const dataLine = frame.split('\n').find((l) => l.startsWith('data:'))
        const event = evtLine ? evtLine.slice(6).trim() : ''
        const data = dataLine ? dataLine.slice(5).trim() : ''
        if (!data) continue
        const payload = JSON.parse(data)
        if (event === 'layout' && Array.isArray(payload)) {
          // 逐页追加（同一页多帧去重：按 page 合并）
          const merged = new Map(layoutBlocks.value.map((b) => [`${b.page}#${b.x}|${b.y}|${b.width}|${b.height}`, b]))
          for (const b of payload as AiChunkLayoutBlock[]) {
            merged.set(`${b.page}#${b.x}|${b.y}|${b.width}|${b.height}`, b)
          }
          layoutBlocks.value = [...merged.values()]
        } else if (event === 'done' || event === 'error') {
          break
        }
      }
    }
  } catch {
    // abort 或网络异常：静默，保留已收到的框（fallback rects 仍会兜底显示）
  }
}

function paragraphsToLeftPages(paragraphs: AiChunkParagraph[]): LeftPage[] {
  const pages = new Map<number, LeftPage>()
  for (const p of paragraphs) {
    const pageList = (p.pages && p.pages.length > 0) ? p.pages : [p.page]
    for (const page of pageList) {
      if (!pages.has(page)) pages.set(page, { page, fragments: [] })
      pages.get(page)!.fragments.push({
        key: `${page}#${pages.get(page)!.fragments.length}`,
        paragraphId: p.id,
        type: (p.type as AiChunkParagraphType) || 'paragraph',
        // 跨页合并块在每页展示该页片段文本（自首见：全部页展示全文，标注↔跨页合并）
        text: p.text,
        label: `${typeLabel((p.type as AiChunkParagraphType) || 'paragraph')} · 页${page}`,
        page,
        crossPage: p.crossPage || pageList.length > 1,
        imageKey: p.imageKey,
      })
    }
  }
  return [...pages.values()].sort((a, b) => a.page - b.page)
}

// ===========================================================================
// 结构视图增强：图片真实显示 + 标题层级（尽可能还原原件观感）
// ===========================================================================
/** 预加载结构视图中的图片：带 Auth 拉取 {kbId}/{fileId}/images/{key} → blob url */
async function loadFragmentImages() {
  if (!props.kbId || !props.file) return
  const keys = [...new Set(alignParagraphs.value
    .filter((p) => p.type === 'image' && p.imageKey)
    .map((p) => p.imageKey as string))]
  const token = storage.get('token')
  await Promise.all(keys.map(async (key) => {
    if (imageBlobs.value[key]) return
    try {
      const resp = await fetch(
        `/api/kb/${props.kbId}/files/${props.file!.id}/images/${encodeURIComponent(key)}`,
        { headers: token ? { Authorization: `Bearer ${token}` } : {} },
      )
      if (!resp.ok) return
      const blobUrl = URL.createObjectURL(await resp.blob())
      imageBlobs.value = { ...imageBlobs.value, [key]: blobUrl }
    } catch { /* 单张失败不阻断 */ }
  }))
}

function imageSrc(f: LeftFragment): string {
  return (f.imageKey && imageBlobs.value[f.imageKey]) || ''
}

/** 标题层级：按 headingPath 的 ">" 分段数 推定 1~6 级（无层级默认 1） */
function headingLevel(p: AiChunkParagraph | LeftFragment): number {
  const path = (p as any).headingPath
  if (typeof path === 'string' && path) {
    const depth = path.split('>').length
    return Math.min(6, Math.max(1, depth))
  }
  return 1
}

// ===========================================================================
// 左侧真实文件渲染（PDF=pdf.js / DOCX=mammoth / PPTX=pptx-preview）→ 一比一原件
// ===========================================================================
function realCleanup() {
  if (objectUrl.value) {
    URL.revokeObjectURL(objectUrl.value)
    objectUrl.value = ''
  }
  officeHtml.value = ''
  officeHoverable.value = false
  pptxPreviewer = null
  realError.value = ''
}

function hoverParagraphs(ids: string[]) {
  if (ids.length === 0) {
    hoverParagraphId.value = null
    activeChunkId.value = null
    return
  }
  activeChunkId.value = chunkOfFragment(ids[0])?.id ?? null
  hoverParagraphId.value = ids[0]
}

async function loadRealFile() {
  if (!props.file?.url && !isOffice.value) {
    realError.value = '文件无可预览地址'
    return
  }
  realLoading.value = true
  realError.value = ''

  // Office 文件直接交给 OnlyOfficeViewer，无需预先拉字节流（OO 服务端自己拉）
  if (isOffice.value) {
    realLoading.value = false
    return
  }

  const ext = '.' + (props.file.name.split('.').pop() || '').toLowerCase()
  try {
    const token = storage.get('token')
    const resp = await fetch(props.file.url, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    })
    if (!resp.ok) throw new Error(`加载失败（HTTP ${resp.status}）`)
    const ab = await resp.arrayBuffer()
    await renderRealFile(ab, ext)
  } catch (e: any) {
    realError.value = e?.message || '原件加载失败'
    ElMessage.warning('原件渲染失败，可切到结构视图继续')
  } finally {
    realLoading.value = false
  }
}

async function renderRealFile(ab: ArrayBuffer, ext: string) {
  realCleanup()
  if (ext === '.pdf') {
    objectUrl.value = URL.createObjectURL(new Blob([ab], { type: 'application/pdf' }))
    officeHoverable.value = false
  } else if (isOffice.value) {
    // OnlyOffice 渲染走 OnlyOfficeViewer，不在此处处理
    return
  } else if (ext === '.docx' || ext === '.doc') {
    const res = await mammoth.convertToHtml({ arrayBuffer: ab })
    officeHtml.value = res.value
    await nextTick()
    officeHoverable.value = tryInjectDocxHover()
  } else if (isPptx.value) {
    await nextTick()
    if (officePptxRef.value) {
      if (!pptxPreviewer) pptxPreviewer = initPptx(officePptxRef.value, { width: 960, height: 540 })
      pptxPreviewer.preview(ab)
      await nextTick()
      bindPptxSlideHover()
      officeHoverable.value = true
    } else {
      throw new Error('PPTX 预览容器未就绪')
    }
  } else {
    throw new Error('该类型不支持原件渲染')
  }
}

/** DOCX：mammoth 的 <p> 与对齐模型的 paragraph/heading/list 段落按序对齐时注入 hover */
function tryInjectDocxHover(): boolean {
  const root = officeDocRef.value
  const modelPs = alignParagraphs.value.filter((p) => ['paragraph', 'heading', 'list'].includes(p.type))
  if (!root || modelPs.length === 0) return false
  const ps = root.querySelectorAll('p')
  if (ps.length !== modelPs.length) return false
  ps.forEach((el, i) => {
    const pid = modelPs[i].id
    el.classList.add('ai-chunk-hover')
    el.setAttribute('data-pid', pid)
    el.addEventListener('mouseenter', () => hoverParagraphs([pid]))
    el.addEventListener('mouseleave', () => hoverParagraphs([]))
  })
  return true
}

/** PPTX：pptx-preview 的 slide 容器 → 每页映射到 page 匹配的段落（hover 整页） */
function bindPptxSlideHover() {
  const root = officePptxRef.value
  if (!root) return
  const slides = root.querySelectorAll(':scope > *')
  slides.forEach((el, i) => {
    const ids = alignParagraphs.value.filter((p) => p.page === i + 1).map((p) => p.id)
    if (ids.length === 0) return
    el.classList.add('ai-chunk-slide-hover')
    el.addEventListener('mouseenter', () => hoverParagraphs(ids))
    el.addEventListener('mouseleave', () => hoverParagraphs([]))
  })
}

watch(() => [props.modelValue, leftView.value], () => {
  if (props.modelValue && leftView.value === 'real' && props.file?.url) {
    loadRealFile()
  }
})

// ===========================================================================
// 左侧选中 → 手动分片
// ===========================================================================
function toggleSelect(f: LeftFragment) {
  const s = new Set(selectedKeys.value)
  if (s.has(f.key)) s.delete(f.key)
  else s.add(f.key)
  selectedKeys.value = s
}

function isSelected(f: LeftFragment): boolean {
  return selectedKeys.value.has(f.key)
}

function selectedFragmentsInOrder(): LeftFragment[] {
  return leftPages.value.flatMap((p) => p.fragments).filter((f) => selectedKeys.value.has(f.key))
}

function createManualChunk() {
  const frags = selectedFragmentsInOrder()
  if (frags.length === 0) {
    ElMessage.warning('请先在左侧点击选中要分片的内容')
    return
  }
  // 去重同 paragraphId（跨页片段共享 id）→ 用全量对齐段落文本（跨页已合并）
  const seen = new Set<string>()
  const blocks: ChunkBlock[] = []
  for (const f of frags) {
    if (seen.has(f.paragraphId)) continue
    seen.add(f.paragraphId)
    const p = alignParagraphs.value.find((x) => x.id === f.paragraphId)
    blocks.push({
      paragraphId: f.paragraphId,
      type: f.type,
      text: p ? p.text : f.text,
      page: f.page,
      label: f.label,
    })
  }
  chunkCards.value.push({
    id: `chunk_${++chunkSeq}`,
    index: chunkCards.value.length,
    source: 'manual',
    blocks,
    pageRange: pageRangeOfBlocks(blocks),
  })
  selectedKeys.value = new Set()
  status.value = 'done'
  ElMessage.success(`已创建手动分片 #${chunkCards.value.length}（${blocks.length} 段）`)
}

// ===========================================================================
// 一键自动分片：真实走 SSE（fragment/chunk 逐个出），失败回退 preview；演示走模拟
// ===========================================================================
async function autoChunk() {
  if (status.value === 'parsing' || status.value === 'loadingLeft') return
  if (chunkCards.value.length > 0) {
    try {
      await ElMessageBox.confirm('右侧已有分片，重新自动分片将清空现有分片（含手动分片）。继续？', '一键自动分片', { type: 'warning', confirmButtonText: '重新分析', cancelButtonText: '取消' })
    } catch { return }
  }
  chunkCards.value = []
  selectedKeys.value = new Set()
  dirtyCount.value = 0
  editingChunkId.value = null
  status.value = 'parsing'
  progress.value = 0

  if (!props.kbId || !props.file) {
    ElMessage.warning('缺少知识库/文件信息，无法自动分片')
    status.value = 'idle'
    return
  }
  try {
    await realAutoChunkSSE()
  } catch {
    ElMessage.warning('SSE 不可用，回退一次性预览')
    await realAutoChunkPreview()
  }
}

/** 真实 SSE：fetch + ReadableStream（带 Authorization），fragment/chunk/progress/done 逐步渲染 */
async function realAutoChunkSSE() {
  if (!props.kbId || !props.file) return
  const url = api.aiChunkStreamUrl(props.kbId, props.file.id)
  const token = storage.get('token')
  const response = await fetch(url, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  if (!response.ok || !response.body) {
    throw new Error(`SSE 请求失败（HTTP ${response.status}）`)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  // 此轮 SSE 收到的 chunk 事件先收集，完成后统一 map 成卡（fragment 事件用于左侧，打开时已就绪可忽略）
  const chunksFromStream: any[] = []
  const stageMap: Record<string, string> = { parse: 'OCR 解析', merge: '结构级跨页合并', 'llm-chunk': 'LLM 自动分片' }

  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    // 按空行切分 SSE 帧
    let idx: number
    while ((idx = buffer.indexOf('\n\n')) >= 0) {
      const frame = buffer.slice(0, idx)
      buffer = buffer.slice(idx + 2)
      const evtLine = frame.split('\n').find((l) => l.startsWith('event:'))
      const dataLine = frame.split('\n').find((l) => l.startsWith('data:'))
      const event = evtLine ? evtLine.slice(6).trim() : ''
      const data = dataLine ? dataLine.slice(5).trim() : ''
      if (!data) continue
      const payload = JSON.parse(data)
      switch (event) {
        case 'progress':
          progress.value = payload.percent ?? progress.value
          stage.value = stageMap[payload.stage] ?? payload.stage ?? stage.value
          break
        case 'chunk':
          chunksFromStream.push(payload)
          break
        case 'done':
          appendChunkPayloads(chunksFromStream)
          progress.value = 100
          stage.value = '完成'
          ElMessage.success(`LLM 自动分片完成（${chunkCards.value.length} 个分片${payload.chunkSource ? `，${payload.chunkSource === 'llm' ? 'LLM' : payload.chunkSource}` : ''}）`)
          status.value = 'done'
          return
        case 'error':
          throw new Error(payload.message || 'AI分片 流式解析失败')
      }
    }
  }
  // 流结束但未收到 done → 视作异常走回退
  throw new Error('SSE 流提前结束')
}

/** 真实回退：ai-chunk-preview 一次性取结果，渐进出卡 */
async function realAutoChunkPreview() {
  if (!props.kbId || !props.file) return
  // 拦截器已解包 → 返回 AiChunkResult 本体
  const data = (await api.aiChunkPreview(props.kbId, props.file.id, true)) as unknown as AiChunkResult | null
  if (!data || !Array.isArray(data.chunks) || data.chunks.length === 0) {
    throw new Error('自动分片无结果')
  }
  if (Array.isArray(data.paragraphs) && data.paragraphs.length > 0) {
    alignParagraphs.value = data.paragraphs
    imageBoxes.value = data.imageBoxes ?? []
    layoutBlocks.value = data.layoutBlocks ?? []
    leftPages.value = paragraphsToLeftPages(data.paragraphs)
  }
  stage.value = 'LLM 自动分片'
  progress.value = 20
  const delta = Math.floor(80 / data.chunks.length)
  for (const c of data.chunks) {
    await sleep(500)
    chunkCards.value.push(chunkFromPayload(c))
    progress.value = 20 + Math.min(100, (chunkCards.value.length) * delta)
  }
  progress.value = 100
  stage.value = '完成'
  status.value = 'done'
  ElMessage.success(`自动分片完成（${chunkCards.value.length} 个分片）`)
}

/**
 * 后端 chunk 事件/预览项 {id,index,source,paragraphIds,text,pageRange} → 前端卡。
 *
 * <p>块内容取值优先级：对齐段落全文（拿得到类型/页码，展示最准）→
 * payload 自带 text（后端永远携带！对齐模型缺失/失败时的可靠兜底——
 * 此前完全依赖反查，模型一旦缺失卡片就变成"有头无身"的空分片）。</p>
 */
function chunkFromPayload(c: any): ChunkCard {
  const seen = new Set<string>()
  const blocks: ChunkBlock[] = []
  const pids: string[] = c.paragraphIds ?? []
  const fallbackText: string = String(c.text ?? '')
  // 无对齐模型时，按双换行把 payload 文本切成块（保持视觉分段）
  const fallbackParts = pids.length > 0 && !alignParagraphs.value.length
    ? fallbackText.split(/\n{2,}/)
    : []

  for (let i = 0; i < pids.length; i++) {
    const pid = pids[i]
    if (seen.has(pid)) continue
    seen.add(pid)
    const p = alignParagraphs.value.find((x) => x.id === pid)
    const fb = fallbackParts.length === pids.length ? (fallbackParts[i] ?? '') : ''
    const text = p?.text || fb || fallbackText
    blocks.push({
      paragraphId: pid,
      type: (p?.type as AiChunkParagraphType) || 'paragraph',
      text,
      page: p?.page ?? 1,
    })
  }
  // 极端兜底：连 paragraphIds 都没有时，整段文本作为单块
  if (blocks.length === 0 && fallbackText) {
    blocks.push({ paragraphId: 'payload', type: 'paragraph', text: fallbackText, page: 1 })
  }
  return {
    id: c.id ?? `chunk_${++chunkSeq}`,
    index: c.index ?? chunkCards.value.length,
    source: c.source === 'manual' ? 'manual' : 'auto',
    blocks,
    pageRange: c.pageRange ?? pageRangeOfBlocks(blocks),
  }
}

function appendChunkPayloads(items: any[]) {
  for (const c of items) {
    chunkCards.value.push(chunkFromPayload(c))
  }
}

// ===========================================================================
// 联动
// ===========================================================================
function chunkOfFragment(paragraphId: string | null): ChunkCard | undefined {
  if (!paragraphId) return undefined
  return chunkCards.value.find((c) => c.blocks.some((b) => b.paragraphId === paragraphId))
}

function isLeftActive(f: LeftFragment): boolean {
  if (activeChunkId.value) {
    const card = chunkCards.value.find((c) => c.id === activeChunkId.value)
    if (card?.blocks.some((b) => b.paragraphId === f.paragraphId)) return true
  }
  return hoverParagraphId.value === f.paragraphId
}

function isChunkActive(card: ChunkCard): boolean {
  if (activeChunkId.value === card.id) return true
  return !!hoverParagraphId.value && card.blocks.some((b) => b.paragraphId === hoverParagraphId.value)
}

function hoverFragment(f: LeftFragment | null) {
  hoverParagraphId.value = f?.paragraphId ?? null
  activeChunkId.value = f ? (chunkOfFragment(f.paragraphId)?.id ?? null) : null
}

function hoverChunk(card: ChunkCard | null) {
  activeChunkId.value = card?.id ?? null
  hoverParagraphId.value = card?.blocks[0]?.paragraphId ?? null
}

/** chunk 卡 click 事件：通知父组件（FileManager）打开 OnlyOfficeEditorDialog 并跳转 */
function onChunkClick(card: ChunkCard | null) {
  activeChunkId.value = card?.id ?? null
  hoverParagraphId.value = card?.blocks[0]?.paragraphId ?? null
  if (!card) return
  const firstBlock = card.blocks[0]
  const payload = {
    id: card.id,
    index: card.index,
    pageNumber: firstBlock?.page,
    title: card.blocks.map((b) => b.text).join(' ').slice(0, 60),
  }
  // 当前文件是 Office 类型：直接驱动嵌入式 OnlyOfficeViewer 跳转
  if (isOffice.value) {
    officeFocusChunkId.value = payload.id
  }
  // 始终通知父组件（FileManager 可选地打开全屏 OnlyOfficeEditorDialog）
  emit('chunk-click', payload)
}

/** 传给 PDF overlay 的激活段落 id（当前分片覆盖的全部段落，或 hover 的首段） */
const activePdfIds = computed<string[]>(() => {
  if (activeChunkId.value) {
    const card = chunkCards.value.find((c) => c.id === activeChunkId.value)
    if (card) return [...new Set(card.blocks.map((b) => b.paragraphId))]
  }
  return hoverParagraphId.value ? [hoverParagraphId.value] : []
})

// ===========================================================================
// 分片卡操作
// ===========================================================================
function copyChunk(card: ChunkCard) {
  const text = card.blocks.map((b) => b.text).join('\n\n')
  navigator.clipboard.writeText(text).then(() => {
    ElMessage.success(`已复制分片 #${card.index + 1}`)
  }).catch(() => {
    ElMessage.error('复制失败')
  })
}

function startEditChunk(card: ChunkCard) {
  editingChunkId.value = card.id
  editText.value = card.blocks.map((b) => b.text).join('\n\n')
}

function saveEditChunk(card: ChunkCard) {
  const text = editText.value.trim()
  if (!text) {
    ElMessage.warning('内容不能为空')
    return
  }
  const parts = text.split(/\n\n+/).filter(Boolean)
  if (parts.length === card.blocks.length) {
    card.blocks.forEach((b, k) => { b.text = parts[k] })
  } else {
    const first = card.blocks[0]
    card.blocks = parts.map((t, k) => ({ ...first, text: t, label: k === 0 ? first.label : undefined }))
  }
  card.edited = true
  editingChunkId.value = null
  dirtyCount.value++
}

async function deleteChunk(card: ChunkCard) {
  try {
    await ElMessageBox.confirm(`确定删除分片 #${card.index + 1}？`, '删除分片', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
  } catch { return }
  chunkCards.value = chunkCards.value.filter((c) => c.id !== card.id)
  chunkCards.value.forEach((c, i) => { c.index = i })
  dirtyCount.value++
}

// ===========================================================================
// 应用
// ===========================================================================
async function handleApply() {
  if (!props.file) return
  if (chunkCards.value.length === 0) {
    ElMessage.warning('尚无分片：请先在左侧选中内容手动分片，或点击一键自动分片')
    return
  }
  if (dirtyCount.value > 0) {
    try {
      await ElMessageBox.confirm(`内容已修改 ${dirtyCount.value} 处，应用后将按当前分片替换旧数据。继续？`, '应用确认', { type: 'warning', confirmButtonText: '应用', cancelButtonText: '取消' })
    } catch { return }
  }
  status.value = 'applying'
  try {
    if (!props.kbId) throw new Error('缺少知识库信息，无法应用')
    const payload = {
      chunks: chunkCards.value.map((c) => ({
        paragraphIds: [...new Set(c.blocks.map((b) => b.paragraphId))],
        text: c.blocks.map((b) => b.text).join('\n\n'),
        source: c.source,
        pageRange: c.pageRange,
      })),
    }
    await api.aiChunkApply(props.kbId, props.file.id, payload)
    ElMessage.success('AI分片 已应用并落库')
    emit('applied', props.file.id)
    visible.value = false
  } catch (e: any) {
    ElMessage.error(e?.message || '应用失败')
    status.value = 'done'
  }
}

// ---- 打开时初始化 ----
watch(() => props.modelValue, (v) => {
  if (v) initPanel()
  else realCleanup()
}, { immediate: true })

// 关闭弹窗（destroy-on-close 卸载）时中断版面分析 SSE
onUnmounted(() => layoutAbort?.abort())
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="`AI分片 · ${file?.name || ''}`"
    width="90%"
    destroy-on-close
    class="ai-chunk-panel"
  >
    <!-- Header / progress -->
    <div class="ai-chunk-panel__header">
      <div class="ai-chunk-panel__meta">
        <el-tag :type="chunkCards.length > 0 ? 'success' : 'info'" size="small">
          {{ chunkCards.length > 0 ? `已生成 ${chunkCards.length} 个分片` : '待分片' }}
        </el-tag>
        <el-tag v-if="dirtyCount > 0" type="warning" size="small">已编辑 {{ dirtyCount }} 处</el-tag>
        <el-tag v-if="chunkCards.some((c) => c.source === 'manual')" type="primary" size="small">含手动分片</el-tag>
        <span v-if="status === 'parsing' || status === 'loadingLeft'" class="ai-chunk-panel__stage">{{ stage }}</span>
        <el-progress
          v-if="status === 'parsing'"
          :percentage="progress"
          :stroke-width="6"
          class="ai-chunk-panel__progress"
        />
      </div>
    </div>

    <!-- Body: split panel -->
    <div class="ai-chunk-panel__body">
      <!-- LEFT: 原件预览（真实文件渲染，一比一原件；可切结构视图做选中分片） -->
      <div class="ai-chunk-panel__left">
        <div class="ai-chunk-panel__pane-title">
          <el-icon><Document /></el-icon>
          <span>原件预览</span>
          <el-radio-group v-model="leftView" size="small" class="ai-chunk-panel__view-toggle">
            <el-radio-button value="real">原件渲染</el-radio-button>
            <el-radio-button value="model">结构视图</el-radio-button>
          </el-radio-group>
<!-- Office 文件：选区常驻捕获提示（无独立按钮，统一走下方「生成分片」） -->
          <span class="ai-chunk-panel__pane-hint">
            <template v-if="leftView === 'model'">点击选中内容 → <b>生成分片</b></template>
            <template v-else-if="isOffice">
              <template v-if="selBridgeDown">⚠ 选区监听已断开：请在编辑器顶部「插件」选项卡重新打开 ChunkBoundary</template>
              <template v-else-if="officeCapturedText">已捕获 {{ officeCapturedText.length }} 字，可点下方<b>生成分片</b></template>
              <template v-else>在文档中三击选中整段或框选文字，即可用下方<b>生成分片</b>创建</template>
            </template>
            <template v-else>悬停<template v-if="!officeHoverable">（PDF 仅浏览）</template>联动右侧</template>
          </span>
        </div>

        <!-- 真实文件渲染（一比一原件） -->
        <div v-if="leftView === 'real'" class="ai-chunk-panel__real">
          <div v-if="realLoading" class="ai-chunk-panel__real-loading">
            <el-empty description="正在渲染原件…" :image-size="80" />
          </div>
          <template v-else-if="realError">
            <el-empty :description="realError" :image-size="80" />
          </template>
          <AiChunkPdfOverlay
            v-if="isPdf && objectUrl"
            :file="file"
            :src="objectUrl"
            :paragraphs="alignParagraphs"
            :active-paragraph-ids="activePdfIds"
            :image-boxes="imageBoxes"
            :layout-blocks="layoutBlocks"
          />
          <OnlyOfficeViewer
            v-else-if="isOffice && file"
            :file="file"
            :kb-id="kbId"
            :chunks="officeChunks"
            :focus-chunk-id="officeFocusChunkId"
            @chunk-created="onOfficeChunkCreated"
            @captured="onOfficeCaptured"
            @bridge-state="onBridgeState"
            class="ai-chunk-panel__real-office"
          />
          <div v-else-if="officeHtml" ref="officeDocRef" class="ai-chunk-panel__real-doc" v-html="officeHtml" />
          <div v-else-if="isPptx" ref="officePptxRef" class="ai-chunk-panel__real-pptx" />
          <el-empty v-else description="无可渲染内容" :image-size="80" />
        </div>

        <!-- 结构化段落视图（真实解析内容，尽量还原原件样式） -->
        <div v-else class="ai-chunk-panel__pages">
          <div
            v-for="page in leftPages"
            :key="page.page"
            class="ai-chunk-panel__page"
          >
            <div class="ai-chunk-panel__page-head">第 {{ page.page }} 页</div>
            <div
              v-for="f in page.fragments"
              :key="f.key"
              class="ai-chunk-panel__fragment"
              :class="{ 'is-active': isLeftActive(f), 'is-selected': isSelected(f) }"
              @click="toggleSelect(f)"
              @mouseenter="hoverFragment(f)"
              @mouseleave="hoverFragment(null)"
            >
              <span class="ai-chunk-panel__fragment-label">
                {{ f.label }}
                <el-icon v-if="isSelected(f)" class="ai-chunk-panel__fragment-check"><Check /></el-icon>
              </span>
              <component
                :is="'h' + headingLevel(f)"
                v-if="f.type === 'heading'"
                class="ai-chunk-panel__block"
                :class="`ai-chunk-panel__block--h${headingLevel(f)}`"
              >{{ f.text }}</component>
              <img
                v-else-if="f.type === 'image' && imageSrc(f)"
                :src="imageSrc(f)"
                class="ai-chunk-panel__block ai-chunk-panel__block--image"
                alt=""
              />
              <pre v-else-if="f.type === 'code'" class="ai-chunk-panel__block ai-chunk-panel__block--code">{{ f.text }}</pre>
              <table v-else-if="f.type === 'table'" class="ai-chunk-panel__block ai-chunk-panel__block--table">
                <tbody>
                  <tr v-for="(row, ri) in parseTable(f.text).slice(0, 8)" :key="ri">
                    <td v-for="(cell, ci) in row" :key="ci">{{ cell }}</td>
                  </tr>
                </tbody>
              </table>
              <ul v-else-if="f.type === 'list'" class="ai-chunk-panel__block ai-chunk-panel__block--list">
                <li v-for="(l, i) in f.text.split('\n')" :key="i">{{ l }}</li>
              </ul>
              <p v-else class="ai-chunk-panel__block" :class="{ 'has-image-key': f.imageKey }">{{ truncate(f.text, 300) }}</p>
              <span v-if="f.crossPage" class="ai-chunk-panel__merged-flag">↔ 跨页内容</span>
            </div>
          </div>
          <el-empty v-if="leftPages.length === 0 && status === 'loadingLeft'" description="正在加载原件…" :image-size="80" />
          <el-empty v-if="leftPages.length === 0 && status === 'idle' && leftError" :description="leftError" :image-size="80" />
        </div>
        <!-- 手动分片操作条：结构视图选段 或 OnlyOffice 捕获选区（原始渲染）→ 生成分片 -->
        <div class="ai-chunk-panel__left-actions">
          <el-button
            type="primary"
            :icon="Plus"
            :disabled="selectedKeys.size === 0 && !officeCapturedText"
            @click="handleCreateChunk"
          >
            <template v-if="officeCapturedText && isOffice && leftView === 'real'">
              生成分片（已捕获 {{ officeCapturedText.length }} 字）
            </template>
            <template v-else-if="selectedKeys.size > 0">
              生成分片（已选 {{ selectedKeys.size }} 段）
            </template>
            <template v-else>生成分片</template>
          </el-button>
          <el-button v-if="selectedKeys.size > 0" text @click="selectedKeys = new Set()">清空选择</el-button>
          <el-button v-if="officeCapturedText && isOffice && leftView === 'real'" text @click="officeCapturedText = ''">清除选区</el-button>
        </div>
      </div>

      <!-- RIGHT: 分片结果（默认空，逐个出块） -->
      <div class="ai-chunk-panel__right">
        <div class="ai-chunk-panel__pane-title">
          <el-icon><MagicStick /></el-icon>
          <span>分片结果（{{ chunkCards.length }}）</span>
          <el-button
            class="ai-chunk-panel__auto-btn"
            type="primary"
            size="small"
            :icon="MagicStick"
            :loading="status === 'parsing'"
            :disabled="status === 'loadingLeft'"
            @click="autoChunk"
          >
            {{ chunkCards.length > 0 ? '重新自动分片' : '一键自动分片' }}（OCR 大模型分析）
          </el-button>
          <!-- 按结构分片：规则流（不调 LLM），复用 structure_aware 策略重切；全部文档类型可见 -->
          <el-button
            class="ai-chunk-panel__structure-btn"
            size="small"
            :icon="Document"
            :disabled="status === 'loadingLeft'"
            @click="structureChunk"
          >
            按结构分片
          </el-button>
        </div>

        <div class="ai-chunk-panel__chunks-view">
          <!-- 空态 -->
          <div v-if="chunkCards.length === 0 && status !== 'parsing'" class="ai-chunk-panel__empty-wrap">
            <el-empty :image-size="110" description="暂无分片">
              <template #description>
                <div class="ai-chunk-panel__empty-text">
                  从<b>左侧</b>点击选中内容后「生成分片」，<br>或一键自动分片让 OCR 大模型分析
                </div>
              </template>
            </el-empty>
          </div>

          <!-- 分片中 -->
          <div v-if="status === 'parsing'" class="ai-chunk-panel__parsing">
            <el-icon class="is-loading"><Refresh /></el-icon>
            <span>{{ stage }}，分片将逐个出现在此处…</span>
          </div>

          <!-- 分片卡 -->
          <div
            v-for="card in chunkCards"
            :key="card.id"
            class="ai-chunk-panel__card"
            :class="{ 'is-active': isChunkActive(card), 'is-manual': card.source === 'manual' }"
            @mouseenter="hoverChunk(card)"
            @mouseleave="hoverChunk(null)"
            @click="onChunkClick(card)"
          >
            <div class="ai-chunk-panel__card-head">
              <span class="ai-chunk-panel__card-index">#{{ card.index + 1 }}</span>
              <el-tag size="small" :type="card.source === 'manual' ? 'warning' : 'info'" effect="plain">
                {{ card.source === 'manual' ? '手动' : '自动' }}
              </el-tag>
              <span class="ai-chunk-panel__card-meta">页 {{ card.pageRange }} · {{ charCountOf(card) }} 字符</span>
              <span v-if="card.edited" class="ai-chunk-panel__edited-flag">已编辑</span>
              <span v-if="card.pageRange.includes('-')" class="ai-chunk-panel__merged-flag">↔ 跨页合并</span>
              <span class="ai-chunk-panel__card-actions">
                <el-button size="small" text :icon="CopyDocument" title="复制分片内容" @click.stop="copyChunk(card)">复制</el-button>
                <el-button size="small" text :icon="Edit" title="编辑分片内容" @click.stop="startEditChunk(card)">编辑</el-button>
                <el-button size="small" text type="danger" @click.stop="deleteChunk(card)">删除</el-button>
              </span>
            </div>

            <!-- 编辑态 -->
            <div v-if="editingChunkId === card.id" class="ai-chunk-panel__editing">
              <el-input
                v-model="editText"
                type="textarea"
                :autosize="{ minRows: 3, maxRows: 16 }"
                placeholder="编辑分片内容；空行分段，表格行以 Tab 分隔"
              />
              <div class="ai-chunk-panel__editing-actions">
                <el-button size="small" @click="editingChunkId = null">取消</el-button>
                <el-button size="small" type="primary" :icon="Check" @click="saveEditChunk(card)">保存</el-button>
              </div>
            </div>
            <!-- 浏览态 -->
            <template v-else>
              <template v-for="(b, bi) in card.blocks" :key="bi">
                <component :is="'h3'" v-if="b.type === 'heading'" class="ai-chunk-panel__block ai-chunk-panel__block--heading">{{ b.text }}</component>
                <table v-else-if="b.type === 'table'" class="ai-chunk-panel__block ai-chunk-panel__block--table">
                  <thead><tr><th v-for="(cell, ci) in parseTable(b.text)[0]" :key="ci">{{ cell }}</th></tr></thead>
                  <tbody>
                    <tr v-for="(row, ri) in parseTable(b.text).slice(1)" :key="ri">
                      <td v-for="(cell, ci) in row" :key="ci">{{ cell }}</td>
                    </tr>
                  </tbody>
                </table>
                <pre v-else-if="b.type === 'code'" class="ai-chunk-panel__block ai-chunk-panel__block--code">{{ b.text }}</pre>
                <p v-else class="ai-chunk-panel__block ai-chunk-panel__block--paragraph">{{ b.text }}</p>
              </template>
              <span class="ai-chunk-panel__block-meta">{{ card.blocks.map((b) => typeLabel(b.type)).join(' + ') }}</span>
            </template>
          </div>
        </div>
      </div>
    </div>

    <!-- Footer -->
    <template #footer>
      <div class="ai-chunk-panel__footer">
        <span class="ai-chunk-panel__footer-tip"><el-icon><MagicStick /></el-icon> 支持手动选中分片 + 一键自动分片；应用后以当前分片替换旧数据</span>
        <div>
          <el-button :icon="Refresh" :disabled="status === 'parsing' || status === 'applying' || status === 'loadingLeft'" @click="initPanel">重置</el-button>
          <el-button @click="visible = false">取消</el-button>
          <el-button type="primary" :icon="Check" :loading="status === 'applying'" :disabled="chunkCards.length === 0" @click="handleApply">应用 AI分片</el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.ai-chunk-panel {
  // 弹窗壳规则见文件末尾非 scoped 块（.ai-chunk-panel.el-dialog 直命中根元素）
  // 本 scoped 块只负责栏内布局；__left/__right/__pages/__chunks-view 各自滚动
  &__header {
    display: flex;
    align-items: center;
    margin-bottom: $spacing-base;
  }

  &__meta {
    display: flex;
    align-items: center;
    gap: $spacing-md;
    width: 100%;
  }

  &__stage {
    font-size: 13px;
    color: $text-secondary;
    white-space: nowrap;
  }

  &__progress {
    flex: 1;
    margin-left: $spacing-sm;
  }

  &__body {
    display: flex;
    flex: 1;
    min-height: 0;
    gap: $spacing-base;
  }

  // ---------- Left ----------
  &__left,
  &__right {
    flex: 1;
    min-width: 0;
    min-height: 0;          // 允许栏自身收缩，栏内滚动容器才能生效
    display: flex;
    flex-direction: column;
    border: 1px solid $border-light;
    border-radius: $radius-base;
    background: $bg-white;
    overflow: hidden;
  }

  &__left { flex: 1.1; }

  &__pane-title {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    padding: $spacing-sm $spacing-base;
    border-bottom: 1px solid $border-light;
    background: $bg-page;
    font-size: 13px;
    color: $text-regular;
    font-weight: 600;
    flex-shrink: 0;
  }

  &__pane-hint {
    margin-left: auto;
    font-size: 12px;
    font-weight: 400;
    color: $text-secondary;

    b { color: $color-primary; }
  }

  &__auto-btn { margin-left: auto; }

  &__view-toggle { margin-left: $spacing-sm; }

  // ---------- Left: 真实文件渲染（一比一原件） ----------
  &__real {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
    background: $bg-page;
    overflow: auto;
    overscroll-behavior: contain;
  }

  &__real-loading {
    display: flex;
    align-items: center;
    justify-content: center;
    flex: 1;
  }

  &__real-pdf {
    width: 100%;
    height: 100%;
    min-height: 480px;
    border: none;
    flex: 1;
  }

  &__real-doc {
    flex: 1;
    padding: $spacing-xl;
    background: $bg-white;

    :deep(p) {
      margin: 0 0 12px;
      line-height: 1.75;
      font-size: 14px;
    }

    :deep(.ai-chunk-hover) {
      border: 1px solid transparent;
      border-radius: $radius-sm;
      padding: 2px 4px;
      cursor: pointer;
      transition: all 0.15s;

      &:hover {
        border-color: $color-primary;
        background: $color-primary-light;
      }
    }

    :deep(img) { max-width: 100%; height: auto; }
    :deep(table) { border-collapse: collapse; width: 100%; margin-bottom: 12px; }
    :deep(td), :deep(th) { border: 1px solid $border-base; padding: 4px 8px; }
  }

  &__real-pptx {
    flex: 1;
    padding: $spacing-base;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: $spacing-base;

    :deep(.ai-chunk-slide-hover) {
      outline: 1px solid transparent;
      border-radius: $radius-base;
      transition: outline-color 0.15s;

      &:hover {
        outline-color: $color-primary;
      }
    }
  }

  &__pages {
    flex: 1;
    min-height: 0;          // 关键：允许收缩，让滚动发生在栏内而非撑高弹窗
    overflow: auto;
    overscroll-behavior: contain;
    padding: $spacing-base;
    display: flex;
    flex-direction: column;
    gap: $spacing-base;
    background: $bg-page;
  }

  &__page {
    background: $bg-white;
    border-radius: $radius-base;
    padding: $spacing-md;
    box-shadow: $shadow-sm;
    display: flex;
    flex-direction: column;
    gap: $spacing-sm;
  }

  &__page-head {
    font-size: 12px;
    color: $text-placeholder;
    font-weight: 600;
    text-transform: uppercase;
  }

  &__fragment {
    border: 1px solid $border-light;
    border-radius: $radius-sm;
    padding: $spacing-sm;
    cursor: pointer;
    transition: all 0.15s;

    &:hover {
      border-color: $color-primary;
      background: $color-primary-lighter;
    }

    &.is-active {
      border-color: $color-primary;
      background: $color-primary-light;
    }

    &.is-selected {
      border-color: $color-primary;
      background: $color-primary-light;
      box-shadow: inset 0 0 0 1px $color-primary;
    }
  }

  &__fragment-label {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 11px;
    color: $text-placeholder;
    margin-bottom: 4px;
  }

  &__fragment-check {
    color: $color-primary;
    font-size: 13px;
  }

  &__merged-flag {
    display: inline-block;
    margin-left: $spacing-sm;
    font-size: 11px;
    color: $color-success;
    background: rgba(16, 185, 129, 0.1);
    border-radius: $radius-sm;
    padding: 1px 6px;
  }

  &__edited-flag {
    display: inline-block;
    margin-left: $spacing-sm;
    font-size: 11px;
    color: $color-warning;
    background: rgba(245, 158, 11, 0.12);
    border-radius: $radius-sm;
    padding: 1px 6px;
  }

  &__left-actions {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    padding: $spacing-sm $spacing-base;
    border-top: 1px solid $border-light;
    background: $bg-page;
    flex-shrink: 0;
  }

  // ---------- Right ----------
  &__chunks-view {
    flex: 1;
    min-height: 0;          // 关键：允许收缩，滚动发生在栏内而非撑高弹窗
    overflow: auto;
    overscroll-behavior: contain;
    padding: $spacing-base;
    display: flex;
    flex-direction: column;
    gap: $spacing-base;
    background: $bg-page;
  }

  &__empty-wrap {
    display: flex;
    align-items: center;
    justify-content: center;
    flex: 1;
  }

  &__empty-text {
    font-size: 13px;
    color: $text-secondary;
    line-height: 1.8;

    b { color: $color-primary; }
  }

  &__parsing {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: $spacing-sm;
    padding: $spacing-xl;
    font-size: 13px;
    color: $text-secondary;
  }

  &__card {
    background: $bg-white;
    border: 1px solid $border-base;
    border-left: 3px solid $color-primary;
    border-radius: $radius-base;
    padding: $spacing-md;
    transition: all 0.15s;
    animation: ai-chunk-in 0.3s ease;

    &.is-manual {
      border-left-color: $color-warning;
    }

    &:hover {
      box-shadow: $shadow-card-hover;
    }

    &.is-active {
      border-color: $color-primary;
      background: $color-primary-lighter;
    }
  }

  &__card-head {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    margin-bottom: $spacing-sm;
  }

  &__card-index {
    font-size: 12px;
    font-weight: 700;
    color: $color-primary;
    background: $color-primary-light;
    border-radius: $radius-sm;
    padding: 1px 8px;
  }

  &__card-meta {
    font-size: 12px;
    color: $text-placeholder;
  }

  &__card-actions {
    margin-left: auto;
    display: flex;
    gap: 2px;
    opacity: 0;
    transition: opacity 0.15s;

    .ai-chunk-panel__card:hover & {
      opacity: 1;
    }
  }

  &__editing {
    display: flex;
    flex-direction: column;
    gap: $spacing-sm;
  }

  &__editing-actions {
    display: flex;
    justify-content: flex-end;
    gap: $spacing-sm;
  }

  &__block {
    padding: 0;
    margin: 0 0 $spacing-sm;

    &:last-child { margin-bottom: 0; }

    &--heading {
      font-weight: 600;
      color: $text-primary;
    }

    // 标题按层级还原原文观感：h1 最大，逐级递减，深层级加缩进
    &--h1 { font-size: 24px; font-weight: 700; margin: 8px 0; }
    &--h2 { font-size: 20px; font-weight: 700; margin: 8px 0 4px; }
    &--h3 { font-size: 17px; font-weight: 600; margin: 6px 0 4px; }
    &--h4 { font-size: 15px; font-weight: 600; margin: 4px 0; }
    &--h5 { font-size: 14px; font-weight: 600; margin: 4px 0; }
    &--h6 { font-size: 13px; font-weight: 600; margin: 4px 0; }

    &--image {
      max-width: 100%;
      border-radius: $radius-sm;
      box-shadow: $shadow-sm;
      margin: 4px 0;
    }

    &--list {
      padding-left: 20px;

      li { margin: 2px 0; }
    }

    &--code {
      font-family: 'SFMono-Regular', Consolas, monospace;
      font-size: 12px;
      background: $bg-page;
      border-radius: $radius-sm;
      padding: $spacing-sm;
      overflow: auto;
      white-space: pre;
      color: $text-regular;
    }

    &--table {
      width: 100%;
      border-collapse: collapse;

      th, td {
        border: 1px solid $border-base;
        padding: 4px 8px;
        text-align: left;
        font-size: 12px;
        vertical-align: top;
      }
      th { background: $bg-page; font-weight: 600; }
    }
  }

  &__block-meta {
    display: block;
    margin-top: 4px;
    font-size: 11px;
    color: $text-placeholder;
  }

  // ---------- Footer ----------
  &__footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    width: 100%;
  }

  &__footer-tip {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: 12px;
    color: $text-secondary;
  }
}

@keyframes ai-chunk-in {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>

<!-- 弹窗壳布局：非 scoped（Element Plus 把 ai-chunk-panel 加在 .el-dialog 同一元素上，
     scoped 的 :deep 后代选择器命中不了自身；此处直命中根元素） -->
<style lang="scss">
.ai-chunk-panel.el-dialog {
  height: 92vh;
  display: flex;
  flex-direction: column;
  margin: 4vh auto 0;
  overflow: hidden;
}

.ai-chunk-panel .el-dialog__header {
  flex-shrink: 0;
  padding-bottom: 8px;
}

.ai-chunk-panel .el-dialog__body {
  padding: 16px;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.ai-chunk-panel .el-dialog__footer {
  flex-shrink: 0;
  padding-top: 8px;
}
</style>