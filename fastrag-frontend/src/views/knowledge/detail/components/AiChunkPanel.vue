<script setup lang="ts">
import type { KnowledgeFile, AiChunkParagraphType, AiChunkResult, AiChunkParagraph, AiChunkLayoutBlock } from '@/types/knowledge'
import { Refresh, Document, Check, MagicStick, CopyDocument, Edit, Plus, Scissor, ArrowUp, ArrowDown, RefreshLeft, Picture } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { storage } from '@/utils/storage'
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
  /** chunk 卡被点击：Office 文件由内嵌 OnlyOfficeViewer 跳页，父组件可自行消费 */
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

// ---- 左侧视图：一比一原件渲染（PDF=pdf.js 版面框可点选 / Office=OnlyOffice 选区桥）----
const realLoading = ref(false)
const realError = ref('')
const objectUrl = ref('')          // PDF blob url
const isPdf = computed(() => (props.file?.name.split('.').pop() || '').toLowerCase() === 'pdf')
const isOffice = computed(() => isOfficeFile(props.file?.name || ''))  // OnlyOffice 支持的文件类型
// PDF 已选中内容 id（结构块 paragraphId + 图片盒 img:page:i 共用一个选择集；点击框选中/取消）
const pdfSelectedIds = ref<Set<string>>(new Set())
const pdfSelectedCount = computed(() => pdfSelectedIds.value.size)
/** 「生成分片」按钮可点性：Office 有捕获选区 或 PDF 有结构块/图片盒/区域选中 或有文字选区捕获 */
const selectedKeysDisabled = computed(
  () => pdfSelectedCount.value === 0 && pdfRegionsCount.value === 0 && !officeCapturedText.value && !pdfCapturedText.value.trim(),
)
/** 图片盒 OCR 进行中（生成分片按钮转圈） */
const ocrLoading = ref(false)
/** 扫描件延迟解析：打开时探测到无文字层 → 不自动 OCR，等用户点「一键自动分片」 */
const pdfScanDeferred = ref(false)

/** 原生拖选文字（TextLayer 选区，跨页可选）→ 捕获文本，随「生成分片」直接成片 */
const pdfCapturedText = ref('')
const pdfCapturedPage = ref(1)

/** 原生拖选完成：捕获选中文本（同页多次拖选会各自成片，可删除） */
function onPdfTextSelect(payload: { text: string; page: number }) {
  pdfCapturedText.value = payload.text
  pdfCapturedPage.value = payload.page
  status.value = 'done'
  ElMessage.success(`已捕获选中文本（${payload.text.length} 字），可点下方「生成分片」`)
}

/**
 * Overlay 文字层探测回调：过半页面无文字层判定为扫描件。
 * 打开即解析（整本 OCR）动辄分钟级且非用户本意——扫描件延迟到点「一键自动分片」时；
 * 有文字层的 PDF 解析是秒级文本提取，照旧立即加载（版面框选段/联动可用）。
 */
function onPdfScanCheck(payload: { pages: number; scannedPages: number }) {
  if (status.value === 'parsing' || status.value === 'loadingLeft') return
  const needsOcr = payload.pages > 0 && payload.scannedPages * 2 >= payload.pages
  if (needsOcr) {
    pdfScanDeferred.value = true
  } else {
    pdfScanDeferred.value = false
    loadRealLeft()
  }
}
// OnlyOffice 已捕获的选区文本（常驻防抖捕获，父页面「创建分片」按钮消费；'' = 无有效选区）
const officeCapturedText = ref('')
// 选区桥存活状态：插件小窗被关闭时变 false，提示用户恢复方式
const selBridgeDown = ref(false)

function onBridgeState(alive: boolean) {
  selBridgeDown.value = !alive
  console.log('[OO] bridge-state ->', alive ? 'alive' : 'DOWN')
  if (!alive && isOffice.value) {
    // 选区桥断开：明确告知恢复方式（不再有结构视图兜底）
    ElMessage.warning('原件选区监听已断开：请在编辑器「插件」选项卡重新打开 ChunkBoundary，或点底部「重置」重新加载')
  }
}
// OnlyOffice 跳转目标 chunk（chunk-click 触发）
const officeFocusChunkId = ref<string | null>(null)

// ---- 数据 ----
const dirtyCount = ref(0)
const editingChunkId = ref<string | null>(null)
const editText = ref('')
let chunkSeq = 0

// ---- 右侧分片卡（手动 + 自动） ----
interface ChunkBlock { paragraphId: string; type: AiChunkParagraphType; text: string; page: number; label?: string; imageKey?: string }
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
watch([officeCapturedText, pdfSelectedIds], () => {
  console.log('[OO] 「生成分片」按钮状态:', {
    capturedLen: officeCapturedText.value?.length ?? 0,
    pdfSelected: pdfSelectedIds.value.size,
    disabled: selectedKeysDisabled.value,
  })
})

/** 通用「生成分片」入口：Office 走捕获选区对齐；PDF 走统一选择合并（结构块 + 文字捕获 + 区域/图片盒 → 一张卡） */
function handleCreateChunk() {
  if (isOffice.value) {
    if (officeCapturedText.value) {
      addCapturedToDraft()
    } else {
      ElMessage.warning('请先在文档中选中内容（三击整段或框选文字）')
    }
    return
  }
  if (pdfRegionsCount.value === 0 && pdfSelectedCount.value === 0 && !pdfCapturedText.value.trim()) {
    ElMessage.warning('请先在左侧选中内容（点击结构块/图片框、拖拽框选区域或拖选文字）')
    return
  }
  createChunkFromSelection()
}

/** 区域内容提取结果 → 分片块。OCR 空时含占位块（不跳过——用户选择的对象必有所属卡） */
function regionBlocksOf(selId: string, page: number, res: any): ChunkBlock[] {
  const contentBlocks = (Array.isArray(res?.blocks) ? res.blocks : [])
    .map((b: any, k: number) => ({
      paragraphId: `${selId}_${k}`,
      type: (b.type as AiChunkParagraphType) || 'paragraph',
      text: String(b.text ?? ''),
      page,
      // 后端裁剪图已落 MinIO（{kbId}/{fileId}/images/{key}），分片卡可直接渲染
      imageKey: b.imageKey ? String(b.imageKey) : undefined,
    }))
    .filter((b: any) => b.text || b.imageKey)
  if (contentBlocks.length === 0) {
    contentBlocks.push({
      paragraphId: selId,
      type: 'image' as AiChunkParagraphType,
      text: '（该区域未识别到内容，请手动编辑）',
      page,
    })
  }
  return contentBlocks
}

/**
 * 把 OO 划选/三击得到的文本，对齐到解析模型段落（alignParagraphs）。
 * 与 PDF 版面框「点选」一致：命中后取整段（或多段）全文作为 chunk 内容，页码取模型值。
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
        // ★ 选区范围内（order 介于首末命中段落之间）的图片段落（docx 图片不进
        //   选区文本，对齐结果天然缺失）→ 按 order 穿插补全，随分片按顺序渲染
        const all = alignParagraphs.value ?? []
        const orders = objs.map((o) => o.order).filter((n) => Number.isFinite(n))
        if (orders.length > 0) {
          const lo = Math.min(...orders)
          const hi = Math.max(...orders)
          const imgs = all.filter(
            (p) => p.order >= lo && p.order <= hi && p.type === 'image' && p.imageKey && !objs.includes(p),
          )
          if (imgs.length > 0) {
            const merged = [...objs, ...imgs].sort((a, b) => (a.order ?? 0) - (b.order ?? 0))
            return {
              paragraphIds: merged.map((o) => o.id),
              paras: merged,
              page: paras[i].page ?? 1,
            }
          }
        }
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
/** OO 选区加入草稿（不立即落库）：对齐解析段落→按类型重建 Markdown→追加为手动草稿卡，随「应用 AI分片」统一落库 */
function addCapturedToDraft() {
  if (!props.file) return
  const text = officeCapturedText.value?.trim()
  if (!text || text.length < 2) {
    ElMessage.warning('请先在文档中选中内容（三击整段或框选文字）')
    return
  }
  const aligned = matchCapturedToParagraphs(text)
  const content = aligned ? parasToMarkdown(aligned.paras) : text
  const pageNumber = aligned ? aligned.page : undefined
  if (!aligned) {
    ElMessage.info('未精确匹配到解析段落，按选中文本原文加入')
  }
  const blocks: ChunkBlock[] = aligned
    ? aligned.paras.map((p) => ({
        paragraphId: p.id,
        type: p.type as AiChunkParagraphType,
        text: p.text,
        page: p.page ?? 1,
        // docx 图片段落：MinIO key，分片卡按 /files/{fileId}/images/{key} 渲染
        imageKey: p.imageKey,
      }))
    : [{ paragraphId: 'manual', type: 'paragraph', text, page: pageNumber ?? 1 }]
  chunkCards.value.push({
    id: props.file.id + '_manual_' + Date.now(),
    index: chunkCards.value.length,
    source: 'manual',
    blocks,
    pageRange: pageRangeOfBlocks(blocks),
  })
  officeCapturedText.value = ''
  dirtyCount.value++
  ElMessage.success('已加入草稿（' + blocks.length + ' 块），可继续选择或点「应用 AI分片」落库')
}

  // ---- 合并选择 ----
  const mergeSelIds = ref(new Set<string>())
  function toggleMergeSel(card: ChunkCard) {
    const next = new Set(mergeSelIds.value)
    next.has(card.id) ? next.delete(card.id) : next.add(card.id)
    mergeSelIds.value = next
  }

  function reindexCards() {
    chunkCards.value.forEach((c, i) => { c.index = i })
  }

  // ---- 撤销（校对类操作全部可逆） ----
  const undoStack = ref<ChunkCard[][]>([])
  const MAX_UNDO = 20
  /** 变更前快照（深拷贝卡片与块） */
  function snapshotChunks() {
    undoStack.value.push(chunkCards.value.map((c) => ({ ...c, blocks: c.blocks.map((b) => ({ ...b })) })))
    if (undoStack.value.length > MAX_UNDO) undoStack.value.shift()
  }
  function undoChunkOp() {
    const prev = undoStack.value.pop()
    if (!prev) {
      ElMessage.info('没有可撤销的操作')
      return
    }
    chunkCards.value = prev
    reindexCards()
    mergeSelIds.value = new Set()
    if (chunkCards.value.length > 0) status.value = 'done'
  }

  // ---- 多选批量操作 ----
  function selectAllCards() {
    mergeSelIds.value = new Set(chunkCards.value.map((c) => c.id))
  }
  function invertCardSelection() {
    const s = new Set<string>()
    for (const c of chunkCards.value) if (!mergeSelIds.value.has(c.id)) s.add(c.id)
    mergeSelIds.value = s
  }
  function deleteSelectedCards() {
    if (mergeSelIds.value.size === 0) return
    snapshotChunks()
    chunkCards.value = chunkCards.value.filter((c) => !mergeSelIds.value.has(c.id))
    reindexCards()
    mergeSelIds.value = new Set()
    dirtyCount.value++
    ElMessage.success('已删除所选分片')
  }
  function onPanelKeydown(e: KeyboardEvent) {
    if (!props.modelValue) return
    if ((e.ctrlKey || e.metaKey) && (e.key === 'z' || e.key === 'Z')) {
      e.preventDefault()
      undoChunkOp()
    } else if (e.key === 'Delete' && mergeSelIds.value.size > 0) {
      e.preventDefault()
      deleteSelectedCards()
    }
  }
  onMounted(() => window.addEventListener('keydown', onPanelKeydown))
  onBeforeUnmount(() => window.removeEventListener('keydown', onPanelKeydown))

  /** 卡片上移/下移（分片顺序影响阅读与召回） */
  function moveCard(card: ChunkCard, dir: -1 | 1) {
    const idx = chunkCards.value.findIndex((c) => c.id === card.id)
    const target = idx + dir
    if (idx < 0 || target < 0 || target >= chunkCards.value.length) return
    snapshotChunks()
    const [c] = chunkCards.value.splice(idx, 1)
    chunkCards.value.splice(target, 0, c)
    reindexCards()
    dirtyCount.value++
  }

  /** 在卡片第 at 块边界处一分为二（两卡继承源卡片属性） */
  function splitChunkCard(card: ChunkCard, at: number) {
    snapshotChunks()
    const idx = chunkCards.value.findIndex((c) => c.id === card.id)
    if (idx < 0 || at <= 0 || at >= card.blocks.length) return
    const head = card.blocks.slice(0, at)
    const tail = card.blocks.slice(at)
    if (!head.length || !tail.length) return
    const mk = (blocks: ChunkBlock[], suffix: string): ChunkCard => ({
      id: card.id + suffix + '_' + Date.now(),
      index: idx,
      source: card.source,
      blocks,
      pageRange: pageRangeOfBlocks(blocks),
      edited: true,
    })
    chunkCards.value.splice(idx, 1, mk(head, '_sa'), mk(tail, '_sb'))
    reindexCards()
    dirtyCount.value++
    ElMessage.success('已拆分为 2 个分片')
  }

  const SENTENCE_ENDS = '。！？；.!?;'

  /** 单块卡句界拆分：整段成卡时没有块间拆分热区（超长段二次切分的补救入口），在文本中点最近的句界处一分为二 */
  function splitSingleBlockCard(card: ChunkCard) {
    snapshotChunks()
    if (card.blocks.length !== 1) return
    const text = card.blocks[0].text
    const mid = Math.floor(text.length / 2)
    let cut = -1
    for (let d = 0; d < text.length; d++) {
      const li = mid - d
      const ri = mid + d
      if (li >= 0 && SENTENCE_ENDS.includes(text[li])) { cut = li + 1; break }
      if (ri < text.length && SENTENCE_ENDS.includes(text[ri])) { cut = ri + 1; break }
    }
    if (cut < 0) cut = mid
    const left = text.slice(0, cut).trim()
    const right = text.slice(cut).trim()
    if (!left || !right) {
      ElMessage.warning('未找到合适的句界拆分点')
      return
    }
    const mk = (t: string, suffix: string): ChunkCard => ({
      id: card.id + suffix + '_' + Date.now(),
      index: 0,
      source: card.source,
      blocks: [{ ...card.blocks[0], text: t }],
      pageRange: card.pageRange,
      edited: true,
    })
    const idx = chunkCards.value.findIndex((c) => c.id === card.id)
    if (idx < 0) return
    chunkCards.value.splice(idx, 1, mk(left, '_a'), mk(right, '_b'))
    reindexCards()
    dirtyCount.value++
    ElMessage.success('已按句界拆分为 2 个分片')
  }

  /** 合并勾选的卡片：按当前顺序拼接，落在首个被勾选卡的位置 */
  function mergeSelectedCards() {
    snapshotChunks()
    const sel = chunkCards.value.filter((c) => mergeSelIds.value.has(c.id))
    if (sel.length < 2) return
    const firstIdx = chunkCards.value.findIndex((c) => c.id === sel[0].id)
    const blocks = sel.flatMap((c) => c.blocks)
    const merged: ChunkCard = {
      id: sel[0].id + '_m_' + Date.now(),
      index: firstIdx,
      source: sel.every((c) => c.source === 'manual') ? 'manual' : 'auto',
      blocks,
      pageRange: pageRangeOfBlocks(blocks),
      edited: true,
    }
    chunkCards.value = chunkCards.value.filter((c) => !mergeSelIds.value.has(c.id))
    chunkCards.value.splice(firstIdx, 0, merged)
    mergeSelIds.value = new Set()
    reindexCards()
    dirtyCount.value++
    ElMessage.success('已合并为 1 个分片（' + charCountOf(merged) + ' 字符）')
  }

// 后端返回的对齐模型（用于左侧渲染与分片块取材）
const alignParagraphs = ref<AiChunkParagraph[]>([])
// PDF 内容图片渲染位置（旁路可视化数据：原件渲染画 image 框，不参与段落对齐模型）
const imageBoxes = ref<AiChunkResult['imageBoxes']>([])

/**
 * 结构块（PDF 结构化框选的数据源）：文字层 PDF 的解析段落（含每段归一化 rects）。
 * 仅对非扫描件启用（pdfScanDeferred 时保持纯截图，即使后来整本解析也不出框）；
 * image 段落排除——其 rects 是文字锚定失败的兜底横带（位置不可靠），图片统一用 imageBoxes 绿框。
 */
const pdfBlocks = computed(() => {
  if (!isPdf.value || pdfScanDeferred.value) return []
  return alignParagraphs.value
    .filter((p) => p.type !== 'image' && (p.anchorText || p.text)?.trim() && p.rects?.length)
    .map((p) => ({ id: p.id, type: p.type as string, rects: p.rects! }))
})

// ---- 分片卡图片渲染：/files/{fileId}/images/{key} 带 token 拉取 → objectURL 缓存 ----
const chunkImageUrls = ref<Record<string, string>>({})
const chunkImageLoading = new Set<string>()

/** 分片块图片 src：命中缓存直接返回，否则异步拉取（拉取完成后响应式更新） */
function blockImageSrc(b: ChunkBlock): string | undefined {
  if (!b.imageKey || !props.kbId || !props.file) return undefined
  const cacheKey = `${props.file.id}_${b.imageKey}`
  const hit = chunkImageUrls.value[cacheKey]
  if (hit) return hit
  void loadChunkImage(props.kbId, props.file.id, b.imageKey, cacheKey)
  return undefined
}

async function loadChunkImage(kbId: string, fileId: string, imageKey: string, cacheKey: string) {
  if (chunkImageUrls.value[cacheKey] || chunkImageLoading.has(cacheKey)) return
  chunkImageLoading.add(cacheKey)
  try {
    const token = storage.get('token')
    const resp = await fetch(`/api/kb/${kbId}/files/${fileId}/images/${imageKey}`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    })
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
    const blob = await resp.blob()
    chunkImageUrls.value[cacheKey] = URL.createObjectURL(blob)
  } catch (e) {
    console.warn('[AiChunk] load chunk image failed:', imageKey, e)
  } finally {
    chunkImageLoading.delete(cacheKey)
  }
}
// VLM 版面分析块（原件渲染分块画框；缓存命中随 preview 返回，否则走 SSE 按需生成）
const layoutBlocks = ref<AiChunkLayoutBlock[]>([])
// 版面分析 SSE 中断句柄（面板关闭时 abort）
let layoutAbort: AbortController | null = null

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
  pdfSelectedIds.value = new Set()
  pdfRegions.value = []
  undoStack.value = []
  dirtyCount.value = 0
  editingChunkId.value = null
  hoverParagraphId.value = null
  activeChunkId.value = null
  chunkSeq = 0
  realLoading.value = false
  realCleanup()
  alignParagraphs.value = []
  imageBoxes.value = []
  layoutAbort?.abort()
  layoutAbort = null
  layoutBlocks.value = []

  if (props.kbId && props.file) {
    if (isOffice.value) {
      // Office 解析无 OCR（docx/pptx 图片 OCR 发生在入库流水线而非此处），立即加载选区对齐模型
      await loadRealLeft()
    } else if (isPdf.value && props.file.url) {
      // PDF：先渲染原件 → overlay 文字层探测后决定是否解析（扫描件不自动 OCR，见 onPdfScanCheck）
      pdfScanDeferred.value = false
      await loadRealFile()
    }
  }
}

/** 真实左侧：ai-chunk-preview 返回对齐模型 → 每段按 pages 在每页渲染一个片段（跨页共享 paragraphId） */
// 解析用时计时器：扫描件/大文档的解析（含全本 OCR）可能分钟级，让用户看到在进行而非卡死
let parseTicker: ReturnType<typeof setInterval> | null = null

async function loadRealLeft() {
  if (!props.kbId || !props.file) return
  status.value = 'loadingLeft'
  const parseStart = Date.now()
  stage.value = '解析原件…'
  parseTicker = setInterval(() => {
    const sec = Math.round((Date.now() - parseStart) / 1000)
    stage.value =
      sec < 60
        ? `解析原件… ${sec}s（扫描件/大文档 OCR 可能需要更久）`
        : `解析原件… ${Math.floor(sec / 60)}m${sec % 60}s`
  }, 1000)
  try {
    // 拦截器已解包 {code,data,message} → res 即 AiChunkResult 本体
    const data = (await api.aiChunkPreview(props.kbId, props.file.id, false)) as unknown as AiChunkResult | null
    if (!data || !Array.isArray(data.paragraphs)) {
      throw new Error('无解析结果')
    }
    alignParagraphs.value = data.paragraphs
    imageBoxes.value = data.imageBoxes ?? []
    layoutBlocks.value = data.layoutBlocks ?? []
    // 版面分析缓存未命中（首开）→ 走 SSE 按需生成
    if (layoutBlocks.value.length === 0) {
      startLayoutStream()
    }
    status.value = 'idle'
  } catch (e: any) {
    console.error('[AiChunk] loadRealLeft failed:', e)
    alignParagraphs.value = []
    imageBoxes.value = []
    layoutBlocks.value = []
    ElMessage.error('解析数据加载失败：版面框选段与联动不可用，仍可一键自动分片')
    status.value = 'idle'
  } finally {
    if (parseTicker) {
      clearInterval(parseTicker)
      parseTicker = null
    }
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

// ===========================================================================
// 左侧真实文件渲染（PDF=pdf.js）→ 一比一原件，版面框点击可选中建分片
// ===========================================================================
function realCleanup() {
  if (objectUrl.value) {
    URL.revokeObjectURL(objectUrl.value)
    objectUrl.value = ''
  }
  realError.value = ''
}

async function loadRealFile() {
  if (!props.file?.url) {
    realError.value = '文件无可预览地址'
    return
  }
  realLoading.value = true
  realError.value = ''
  try {
    const token = storage.get('token')
    const resp = await fetch(props.file.url, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    })
    if (!resp.ok) throw new Error(`加载失败（HTTP ${resp.status}）`)
    const ab = await resp.arrayBuffer()
    realCleanup()
    objectUrl.value = URL.createObjectURL(new Blob([ab], { type: 'application/pdf' }))
  } catch (e: any) {
    realError.value = e?.message || '原件加载失败'
    ElMessage.warning('原件渲染失败，仍可通过一键自动分片生成分片')
  } finally {
    realLoading.value = false
  }
}

// ===========================================================================
// PDF 版面框点击选中 → 手动分片（结构视图移除后的手动选段入口）
// ===========================================================================
/** 已框选待生成区域（红色高亮，点「生成分片」统一调后端 OCR/结构化成片） */
interface PdfRegion { id: string; page: number; x: number; y: number; width: number; height: number; chars: number; text?: string }
const pdfRegions = ref<PdfRegion[]>([])
const pdfRegionsCount = computed(() => pdfRegions.value.length)
const pdfRegionsChars = computed(() => pdfRegions.value.reduce((n, r) => n + (r.chars || 0), 0))

/** 拖拽框选区域完成：只记录并高亮（不立即成片），提示字数与选中内容，用户点「生成分片」统一生成 */
function onRegionSelect(region: { id: string; page: number; x: number; y: number; width: number; height: number; chars: number; text?: string }) {
  pdfRegions.value.push({ ...region })
  status.value = 'done'
  const preview = (region.text || '').trim()
  const tip = preview
    ? `已框选第 ${region.page} 页区域（约 ${region.chars ?? 0} 字）：「${preview.length > 40 ? preview.slice(0, 40) + '…' : preview}」，可点下方「生成分片」`
    // 无文字层（扫描件/纯图片区）：前端数不出字属正常，内容由生成分片时 OCR 识别
    : `已框选第 ${region.page} 页区域，生成分片时将由 OCR 识别内容`
  ElMessage.success(tip)
}

/** 点击已框选区域框：取消该区域 */
function onRegionRemove(id: string) {
  pdfRegions.value = pdfRegions.value.filter((r) => r.id !== id)
  ElMessage.info('已取消该框选区域')
}

function onPdfToggleSelect(paragraphId: string) {
  const s = new Set(pdfSelectedIds.value)
  s.has(paragraphId) ? s.delete(paragraphId) : s.add(paragraphId)
  pdfSelectedIds.value = s
}

  /**
   * 统一「生成分片」：结构块（段落/标题/表格…）直取 anchorText（零 OCR）+ 原生文字捕获
   * + 区域/图片盒后端结构化提取，全部合并为**一张**分片卡。
   * 块内容存解析原文（表格为 \t 列文本），分片卡按类型渲染（表格→HTML 表、标题→标题样式）。
   */
  async function createChunkFromSelection() {
    if (!props.kbId || !props.file) return
    snapshotChunks()
    ocrLoading.value = true
    try {
      interface SortableBlock { page: number; y: number; block: ChunkBlock }
      const items: SortableBlock[] = []

      // ① 结构块：按 paragraphId 直取整段（跨页合并块共享 paragraphId，天然去重）。
      //    内容优先 anchorText（所见即所得，行链路已清理页眉脚），解析全文仅作兜底
      const paraMap = new Map(alignParagraphs.value.map((p) => [p.id, p]))
      for (const pid of pdfSelectedIds.value) {
        if (pid.startsWith('img:')) continue
        const p = paraMap.get(pid)
        if (!p) continue
        const text = (p.anchorText && p.anchorText.trim()) || p.text
        if (!text.trim()) continue
        const rect0 = p.rects?.[0]
        items.push({
          page: rect0?.page ?? p.page ?? 1,
          y: rect0?.y ?? 0,
          block: { paragraphId: p.id, type: (p.type as AiChunkParagraphType) || 'paragraph', text, page: p.page ?? 1 },
        })
      }

      // ② 原生拖选文字捕获（块间空白处的细粒度选择路径）
      if (pdfCapturedText.value.trim()) {
        const page = pdfCapturedPage.value
        items.push({
          page, y: 0,
          block: { paragraphId: `sel_${Date.now()}`, type: 'paragraph' as AiChunkParagraphType, text: pdfCapturedText.value, page },
        })
      }

      // ③ 区域框选 + 绿色图片盒：调后端三级提取（文字层行 → 内容图直达 → OCR 兜底）
      const extractJobs = [...pdfRegions.value]
        .sort((a, b) => a.page - b.page || a.y - b.y)
        .map((r) => ({ id: r.id, page: r.page, x: r.x, y: r.y, width: r.width, height: r.height }))
      for (const imgId of pdfSelectedIds.value) {
        if (!imgId.startsWith('img:')) continue
        const box = imageBoxes.value?.find((b, i) => `img:${b.page}:${i}` === imgId)
        if (box) extractJobs.push({ id: imgId, page: box.page, x: box.x, y: box.y, width: box.width, height: box.height })
      }
      for (const job of extractJobs) {
        try {
          const res: any = await api.aiChunkImageOcr(props.kbId, props.file.id, job)
          for (const b of regionBlocksOf(job.id, job.page, res)) {
            items.push({ page: job.page, y: job.y, block: b })
          }
        } catch (e: any) {
          ElMessage.error(`区域内容提取失败：${e?.message || e}`)
        }
      }

      if (items.length === 0) {
        ElMessage.warning('所选内容均未识别到内容')
        undoStack.value.pop() // 未产生卡片，弹掉本次快照避免空撤销步
        return
      }

      // 阅读顺序：页序 → 页内位置（Array.sort 稳定，结构块同键时保持文档序）
      items.sort((a, b) => a.page - b.page || a.y - b.y)
      const blocks = items.map((it) => it.block)
      chunkCards.value.push({
        id: `chunk_${++chunkSeq}`,
        index: chunkCards.value.length,
        source: 'manual',
        blocks,
        pageRange: pageRangeOfBlocks(blocks),
      })
      pdfRegions.value = []
      pdfSelectedIds.value = new Set()
      pdfCapturedText.value = ''
      status.value = 'done'
      const chars = blocks.reduce((n, b) => n + b.text.length, 0)
      ElMessage.success(
        extractJobs.length > 0
          ? `已创建分片 #${chunkCards.value.length}（${blocks.length} 块，${chars} 字，含 ${extractJobs.length} 个区域/图片提取）`
          : `已创建分片 #${chunkCards.value.length}（${blocks.length} 块，${chars} 字）`,
      )
    } finally {
      ocrLoading.value = false
    }
  }

// ===========================================================================
// 一键自动分片：真实走 SSE（fragment/chunk 逐个出），失败回退 preview；演示走模拟
// ===========================================================================
/** 自动分片实际来源 → 用户可读标签（后端 LLM 不可用时静默降级，完成后显式告知） */
const CHUNK_SOURCE_LABELS: Record<string, string> = {
  llm: 'LLM 语义分组',
  embedding: 'Embedding 相似度切分（未配置 LLM，已降级）',
  rule: '规则长度切分（未配置 LLM/Embedding，已降级）',
}

async function autoChunk() {
  if (status.value === 'parsing' || status.value === 'loadingLeft') return
  if (chunkCards.value.length > 0) {
    try {
      await ElMessageBox.confirm('右侧已有分片，重新自动分片将清空现有分片（含手动分片）。继续？', '一键自动分片', { type: 'warning', confirmButtonText: '重新分析', cancelButtonText: '取消' })
    } catch { return }
  }
    snapshotChunks()
    chunkCards.value = []
    pdfSelectedIds.value = new Set()
    pdfRegions.value = []
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
          // 后端 LLM 不可用时会静默降级（embedding/规则），此处显式告知实际切分来源
          const src = CHUNK_SOURCE_LABELS[payload.chunkSource as string]
          ElMessage.success(`自动分片完成（${chunkCards.value.length} 个分片${src ? ' · ' + src : ''}）`)
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
  const src = CHUNK_SOURCE_LABELS[data.chunkSource as string]
  ElMessage.success(`自动分片完成（${chunkCards.value.length} 个分片${src ? ' · ' + src : ''}）`)
}

/**
 * 后端 chunk 事件/预览项 {id,index,source,paragraphIds,text,pageRange} → 前端卡。
 *
 * <p>块内容取值优先级：对齐段落全文（拿得到类型/页码，展示最准）→
 * payload 自带 text（后端永远携带！对齐模型缺失/失败时的可靠兜底——
 * 此前完全依赖反查，模型一旦缺失卡片就变成"有头无身"的空分片）。</p>
 *
 * <p>二次切分片（p_xxxx_sN，§6.3 超长段句界切分）/overlap 片（p_xxxx_oN，§6.3 回退路径重叠）
 * 不在对齐模型中：类型/页码回溯基段落，文本取 payload 按 \n\n 对应的片段
 * （后端 group.text 即成员文本按 \n\n 连接，成员数与片段数一致）。</p>
 */
function chunkFromPayload(c: any): ChunkCard {
  const seen = new Set<string>()
  const blocks: ChunkBlock[] = []
  const pids: string[] = c.paragraphIds ?? []
  const fallbackText: string = String(c.text ?? '')
  const parts = fallbackText.split(/\n{2,}/)
  const pieceTextOf = (i: number) => (parts.length === pids.length ? (parts[i] ?? '') : '')
  const baseOf = (pid: string): { para?: AiChunkParagraph; piece: boolean } => {
    const exact = alignParagraphs.value.find((x) => x.id === pid)
    if (exact) return { para: exact, piece: false }
    const m = /^(p_\d+)_(?:s|o)\d+$/.exec(pid)
    if (m) {
      const base = alignParagraphs.value.find((x) => x.id === m[1])
      if (base) return { para: base, piece: true }
    }
    return { para: undefined, piece: false }
  }

  for (let i = 0; i < pids.length; i++) {
    const pid = pids[i]
    if (seen.has(pid)) continue
    seen.add(pid)
    const { para: p, piece } = baseOf(pid)
    const text = piece ? (pieceTextOf(i) || p?.text || fallbackText) : (p?.text || pieceTextOf(i) || fallbackText)
    blocks.push({
      paragraphId: pid,
      type: (p?.type as AiChunkParagraphType) || 'paragraph',
      text,
      page: p?.page ?? 1,
      // 图片段落：MinIO key，分片卡按 /files/{fileId}/images/{key} 渲染
      imageKey: p?.imageKey,
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
function isChunkActive(card: ChunkCard): boolean {
  if (activeChunkId.value === card.id) return true
  return !!hoverParagraphId.value && card.blocks.some((b) => b.paragraphId === hoverParagraphId.value)
}

function hoverChunk(card: ChunkCard | null) {
  activeChunkId.value = card?.id ?? null
  hoverParagraphId.value = card?.blocks[0]?.paragraphId ?? null
}

/** chunk 卡 click 事件：Office 文件驱动嵌入式 OnlyOfficeViewer 跳页，并通知父组件 */
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
  // 通知父组件（目前无消费者，保留事件供后续扩展）
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
  // 编辑态只编辑文本块；图片块原位保留（保存时按块序合并回，不丢图）
  editText.value = card.blocks
    .filter((b) => !(b.type === 'image' && b.imageKey))
    .map((b) => b.text)
    .join('\n\n')
}

  function saveEditChunk(card: ChunkCard) {
    snapshotChunks()
    const text = editText.value.trim()
  if (!text) {
    ElMessage.warning('内容不能为空')
    return
  }
  const parts = text.split(/\n\n+/).map((s) => s.trim()).filter(Boolean)
  const isImageBlock = (b: ChunkBlock) => b.type === 'image' && !!b.imageKey
  const textBlocks = card.blocks.filter((b) => !isImageBlock(b))
  // 按原块序重建：图片块原位保留，文本块依次回填编辑后的段落
  const rebuilt: ChunkBlock[] = []
  let pi = 0
  for (const b of card.blocks) {
    if (isImageBlock(b)) {
      rebuilt.push(b)
      continue
    }
    if (pi < parts.length) {
      rebuilt.push({ ...b, text: parts[pi] })
      pi++
    }
    // parts 不足时多余的文本块丢弃（用户删段）
  }
  // parts 多于原文本块：多余段落并入末尾新增文本块
  if (pi < parts.length) {
    const last = textBlocks[textBlocks.length - 1] ?? card.blocks[0]
    for (; pi < parts.length; pi++) {
      rebuilt.push({ ...last, paragraphId: `${last.paragraphId}_e${pi}`, text: parts[pi], label: undefined })
    }
  }
  card.blocks = rebuilt
  card.edited = true
  editingChunkId.value = null
  dirtyCount.value++
}

  async function deleteChunk(card: ChunkCard) {
    snapshotChunks()
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
    undoStack.value = []
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
      <!-- LEFT: 原件预览（一比一原件；PDF 版面框点击选中 / Office 选区捕获 → 生成分片） -->
      <div class="ai-chunk-panel__left">
        <div class="ai-chunk-panel__pane-title">
          <el-icon><Document /></el-icon>
          <span>原件预览</span>
<!-- Office 文件：选区常驻捕获提示（无独立按钮，统一走下方「生成分片」） -->
          <span class="ai-chunk-panel__pane-hint">
            <template v-if="isOffice">
              <template v-if="selBridgeDown">⚠ 选区监听已断开：请在编辑器顶部「插件」选项卡重新打开 ChunkBoundary</template>
              <template v-else-if="officeCapturedText">已捕获 {{ officeCapturedText.length }} 字，可点下方<b>生成分片</b></template>
              <template v-else>在文档中三击选中整段或框选文字，即可用下方<b>生成分片</b>创建</template>
            </template>
            <template v-else>
              <template v-if="pdfRegionsCount > 0">
                已框选 {{ pdfRegionsCount }} 个区域（共 {{ pdfRegionsChars }} 字{{ pdfScanDeferred ? '，松手后由 OCR 识别内容' : '' }}），可点下方<b>生成分片</b>；点红色框可取消
              </template>
              <template v-else-if="pdfScanDeferred">
                <template v-if="chunkCards.length === 0">扫描件：可直接<b>拖拽框选</b>任意区域，点下方<b>生成分片</b>由 OCR 提取内容；或点「一键自动分片」解析全文</template>
                <template v-else>扫描件已解析：也可继续拖拽框选区域分片；右侧可编辑 / 拆分 / 合并分片后应用</template>
              </template>
              <template v-else-if="pdfSelectedCount > 0">已选 {{ pdfSelectedCount }} 块，可点下方<b>生成分片</b>（多选合并为一个分片）</template>
              <template v-else-if="pdfBlocks.length > 0"><b>点击</b>蓝/橙结构块或绿色图片框选中（可多选合并）；<b>拖拽</b>框选任意区域截图提取；点下方<b>生成分片</b>；点红色框可取消</template>
              <template v-else><b>拖拽框选</b>任意区域提取内容（文字层精确提取，图片自动 OCR）；右侧勾选可合并/删除</template>
            </template>
          </span>
        </div>

        <!-- 原件渲染（一比一原件） -->
        <div class="ai-chunk-panel__real">
          <div v-if="realLoading" class="ai-chunk-panel__real-loading">
            <el-empty description="正在渲染原件…" :image-size="80" />
          </div>
          <template v-else-if="realError">
            <el-empty :description="realError" :image-size="80" />
          </template>
          <AiChunkPdfOverlay
            v-else-if="isPdf && objectUrl"
            :file="file"
            :src="objectUrl"
            :image-boxes="imageBoxes"
            :blocks="pdfBlocks"
            :selected-ids="[...pdfSelectedIds]"
            :active-ids="activePdfIds"
            :regions="pdfRegions"
            @toggle-select="onPdfToggleSelect"
            @region-select="onRegionSelect"
            @region-remove="onRegionRemove"
            @scan-check="onPdfScanCheck"
            @text-select="onPdfTextSelect"
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
          <el-empty v-else description="无可渲染内容" :image-size="80" />
        </div>
        <!-- 手动分片操作条：PDF 版面框选中 或 OnlyOffice 捕获选区 → 生成分片 -->
        <div class="ai-chunk-panel__left-actions">
          <el-button
            type="primary"
            :icon="Plus"
            :disabled="selectedKeysDisabled"
            :loading="ocrLoading"
            @click="handleCreateChunk"
          >
            <template v-if="officeCapturedText && isOffice">
              生成分片（已捕获 {{ officeCapturedText.length }} 字）
            </template>
            <template v-else-if="pdfSelectedCount > 0 && pdfRegionsCount > 0">
              生成分片（{{ pdfSelectedCount }} 块 + {{ pdfRegionsCount }} 个区域）
            </template>
            <template v-else-if="pdfRegionsCount > 0">
              生成分片（{{ pdfRegionsCount }} 个区域 · {{ pdfRegionsChars }} 字）
            </template>
            <template v-else-if="pdfSelectedCount > 0">
              生成分片（已选 {{ pdfSelectedCount }} 块）
            </template>
            <template v-else-if="pdfCapturedText.trim()">
              生成分片（已捕获 {{ pdfCapturedText.length }} 字）
            </template>
            <template v-else>生成分片</template>
          </el-button>
          <el-button v-if="pdfRegionsCount > 0" text @click="pdfRegions = []">清空区域</el-button>
          <el-button v-if="pdfSelectedCount > 0" text @click="pdfSelectedIds = new Set()">清空选择</el-button>
          <el-button v-if="officeCapturedText && isOffice" text @click="officeCapturedText = ''">清除选区</el-button>
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
            {{ chunkCards.length > 0 ? '重新自动分片' : '一键自动分片' }}（AI 语义分组）
          </el-button>
        </div>

        <div class="ai-chunk-panel__chunks-view">
          <!-- 空态 -->
          <div v-if="chunkCards.length === 0 && status !== 'parsing'" class="ai-chunk-panel__empty-wrap">
            <el-empty :image-size="110" description="暂无分片">
              <template #description>
                <div class="ai-chunk-panel__empty-text">
                  从<b>左侧</b>选中内容后「生成分片」，<br>或一键自动分片（AI 语义分组）
                </div>
              </template>
            </el-empty>
          </div>

          <!-- 分片中 -->
          <div v-if="status === 'parsing'" class="ai-chunk-panel__parsing">
            <el-icon class="is-loading"><Refresh /></el-icon>
            <span>{{ stage }}，分片将逐个出现在此处…</span>
          </div>

          <!-- 批量操作工具条（常显）：勾选卡片后合并/删除，或全选/反选 -->
          <div v-if="chunkCards.length > 0" class="ai-chunk-panel__merge-bar">
            <el-button size="small" text @click="selectAllCards">全选</el-button>
            <el-button size="small" text @click="invertCardSelection">反选</el-button>
            <span class="ai-chunk-panel__merge-hint">已勾选 {{ mergeSelIds.size }} 片（合并后按当前顺序拼接）</span>
            <el-button size="small" type="primary" :disabled="mergeSelIds.size < 2" @click="mergeSelectedCards">合并所选</el-button>
            <el-button size="small" type="danger" :disabled="mergeSelIds.size === 0" @click="deleteSelectedCards">删除所选</el-button>
            <el-button size="small" text @click="mergeSelIds = new Set()">取消勾选</el-button>
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
              <el-checkbox
              class="ai-chunk-panel__card-sel"
              :model-value="mergeSelIds.has(card.id)"
              title="勾选用于合并"
              @change="toggleMergeSel(card)"
              @click.stop
            />
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
                <el-button size="small" text :icon="ArrowUp" title="上移（调整分片顺序）" @click.stop="moveCard(card, -1)" />
                <el-button size="small" text :icon="ArrowDown" title="下移（调整分片顺序）" @click.stop="moveCard(card, 1)" />
                <el-button
                  v-if="card.blocks.length === 1 && charCountOf(card) > 400"
                  size="small"
                  text
                  :icon="Scissor"
                  title="按句界把此分片一分为二"
                  @click.stop="splitSingleBlockCard(card)"
                >拆分</el-button>
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
              <!-- 编辑态只编辑文字；图片块原位保留（保存时按块序合并回），此处只读预览 -->
              <div v-if="card.blocks.some((b) => b.type === 'image' && b.imageKey)" class="ai-chunk-panel__editing-images">
                <div class="ai-chunk-panel__editing-images-tip">
                  <el-icon><Picture /></el-icon>
                  分片包含 {{ card.blocks.filter((b) => b.type === 'image' && b.imageKey).length }} 张图片，编辑保存后原位保留
                </div>
                <div class="ai-chunk-panel__editing-images-list">
                  <img
                    v-for="(b, bi) in card.blocks.filter((b) => b.type === 'image' && b.imageKey)"
                    :key="bi"
                    :src="blockImageSrc(b)"
                    alt="分片图片"
                  />
                </div>
              </div>
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
                <!-- 图片块：MinIO 裁剪/提取图直接渲染，OCR 文本作说明 -->
                <figure v-else-if="b.type === 'image' && b.imageKey" class="ai-chunk-panel__block ai-chunk-panel__block--image">
                  <img v-if="blockImageSrc(b)" :src="blockImageSrc(b)" alt="分片图片" />
                  <div v-else class="ai-chunk-panel__block-image-loading">图片加载中…</div>
                  <figcaption v-if="b.text">{{ b.text }}</figcaption>
                </figure>
                <p v-else class="ai-chunk-panel__block ai-chunk-panel__block--paragraph">{{ b.text }}</p>
                <div
                  v-if="bi < card.blocks.length - 1"
                  class="ai-chunk-panel__split-zone"
                  title="在此拆分为两个分片"
                  @click.stop="splitChunkCard(card, bi + 1)"
                >
                  <span>✂ 在此拆分</span>
                </div>
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
    <el-button :icon="RefreshLeft" :disabled="undoStack.length === 0" title="撤销上一步操作（Ctrl+Z）" @click="undoChunkOp">撤销</el-button>
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

  // ---------- Left: 原件渲染（一比一原件） ----------
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

  &__card-sel {
    margin-right: 2px;
    height: auto;
  }

  &__split-zone {
    position: relative;
    height: 4px;
    margin: 2px 0;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    opacity: 0;
    transition: all 0.15s;

    &::before {
      content: '';
      position: absolute;
      left: 0;
      right: 0;
      top: 50%;
      border-top: 1px dashed #b0c4de;
    }

    span {
      position: relative;
      z-index: 1;
      font-size: 11px;
      color: #409eff;
      background: #f0f7ff;
      border: 1px solid #c6e2ff;
      border-radius: 4px;
      padding: 1px 8px;
    }

    &:hover {
      opacity: 1;
      height: 24px;
      background: #ecf5ff;
    }
  }

  &__merge-bar {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 6px 12px;
    margin-bottom: 10px;
    background: #ecf5ff;
    border: 1px solid #d9ecff;
    border-radius: 6px;
    font-size: 12px;
    color: #409eff;

    span:first-child {
      flex: 1;
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

  // 编辑态图片块预览：图片原位保留提示 + 缩略图列表
  &__editing-images {
    display: flex;
    flex-direction: column;
    gap: 6px;

    &-tip {
      display: flex;
      align-items: center;
      gap: 4px;
      font-size: 12px;
      color: $text-secondary;
    }

    &-list {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;

      img {
        max-width: 180px;
        max-height: 90px;
        object-fit: contain;
        border: 1px solid $border-base;
        border-radius: $radius-sm;
        background: #fff;
      }
    }
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

    // 正文段落：保留 OCR/解析文本中的换行
    &--paragraph {
      white-space: pre-wrap;
      word-break: break-word;
      line-height: 1.7;
    }

    &--image {
      max-width: 100%;
      margin: 4px 0;

      img {
        display: block;
        max-width: 100%;
        border-radius: $radius-sm;
        border: 1px solid $border-base;
        box-shadow: $shadow-sm;
      }

      // 图片拉取中占位
      .ai-chunk-panel__block-image-loading {
        display: flex;
        align-items: center;
        justify-content: center;
        min-height: 64px;
        font-size: 12px;
        color: $text-secondary;
        background: $bg-page;
        border: 1px dashed $border-base;
        border-radius: $radius-sm;
      }

      figcaption {
        margin-top: 4px;
        font-size: 12px;
        line-height: 1.6;
        color: $text-regular;
        white-space: pre-wrap;
        word-break: break-word;
      }
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