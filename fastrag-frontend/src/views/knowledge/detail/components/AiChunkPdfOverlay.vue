<script setup lang="ts">
/**
 * AI分片 原件预览 overlay（pdf.js 渲染 + 原生文字选区 + 图片框点选）。
 *
 * <p>交互模型（极简）：
 * <ul>
 *   <li>pdf.js TextLayer 文字层：像普通 PDF 阅读器一样拖选文字（文字层与渲染像素级对齐，
 *       由 pdf.js 官方定位逻辑保证），松开鼠标 → emit text-select，
 *       父组件以选中文本直接生成文字分片（所见即所得，零 OCR、零坐标转换）；</li>
 *   <li>绿色图片框：点选 → 生成分片时对该图片区域 OCR（内容图/截图）；</li>
 *   <li>文字层探测：打开时统计无文字层的页占比 → 扫描件延迟解析（不自动整本 OCR）。</li>
 * </ul>
 * 文字/标题/表格的几何碎框已移除（对设计型/表格型页面是噪声）。
 */
import { storage } from "@/utils/storage"
import type { KnowledgeFile } from '@/types/knowledge'

const props = defineProps<{
  file: KnowledgeFile | null
  /** 渲染源（blob: 或鉴权 URL） */
  src?: string
  /** 内容图片盒（归一化 0~1，与渲染同坐标系），点选可生成图片分片 */
  imageBoxes?: Array<{ page: number; x: number; y: number; width: number; height: number }>
  /** 已选中（待「生成分片」）的图片盒 id */
  selectedImageBoxIds?: string[]
  /** 已框选的区域（红色高亮，待「生成分片」统一成片）；点框可取消 */
  regions?: Array<{ id: string; page: number; x: number; y: number; width: number; height: number; chars?: number }>
}>()

const emit = defineEmits<{
  /** 点击图片盒：切换选中态（生成分片时对该区域 OCR） */
  (e: 'toggle-select', boxId: string): void
  /** 原生拖选文字完成（≥2 字符） */
  (e: 'text-select', payload: { text: string; page: number }): void
  /** 拖拽框选区域完成（≥1%×1%）：面板记录并高亮，待用户点「生成分片」统一成片 */
  (e: 'region-select', region: { id: string; page: number; x: number; y: number; width: number; height: number; chars: number; text?: string }): void
  /** 点击已框选区域框：取消该区域 */
  (e: 'region-remove', id: string): void
  /** 文字层探测结果：父组件据此决定打开时是否解析（扫描件延迟到一键自动分片） */
  (e: 'scan-check', payload: { pages: number; scannedPages: number }): void
}>()

/** 图片盒框样式 */
const BOX_STYLE = { color: '#10b981', label: 'image' }

const legendItems = computed<Array<[string, { color: string; label: string }]>>(() => {
  return props.imageBoxes?.length ? [['image', BOX_STYLE]] : []
})

function layerOf(page: number): HTMLElement | null {
  return wrapRef.value?.querySelector(`.ai-pdf-page-wrap[data-page="${page}"] .ai-pdf-overlay`) as HTMLElement | null
}

const wrapRef = ref<HTMLDivElement | null>(null)
const scale = ref(1.5)
let pdfDoc: any = null
const renderedPages = ref(0)
const renderError = ref("")
let disposed = false
let rendering = false

/** 画图片盒：可点击选中（选中态高亮 + 提示） */
function makeBox(
  rect: { x: number; y: number; width: number; height: number },
  pid: string,
  selected: boolean,
): HTMLElement {
  const box = document.createElement('div')
  box.className = 'ai-pdf-box'
  box.style.setProperty('--box-color', BOX_STYLE.color)
  box.style.left = `${rect.x * 100}%`
  box.style.top = `${rect.y * 100}%`
  box.style.width = `${rect.width * 100}%`
  box.style.height = `${rect.height * 100}%`
  const label = document.createElement('span')
  label.className = 'ai-pdf-box__label'
  label.textContent = BOX_STYLE.label
  box.appendChild(label)
  box.classList.add('is-selectable')
  box.title = '点击选中/取消该图片（生成分片时自动 OCR 识别内容）'
  box.addEventListener('click', (e) => {
    e.stopPropagation()
    emit('toggle-select', pid)
  })
  if (selected) box.classList.add('is-selected')
  return box
}

function redraw() {
  if (!wrapRef.value) return
  wrapRef.value.querySelectorAll('.ai-pdf-box').forEach((n) => n.remove())
  wrapRef.value.querySelectorAll('.ai-pdf-region-box').forEach((n) => n.remove())
  // 极简：只画图片盒（可点选 → OCR 生成图片分片）
  for (let i = 0; i < (props.imageBoxes ?? []).length; i++) {
    const r = props.imageBoxes![i]
    if (r.width <= 0 || r.height <= 0) continue
    const pid = `img:${r.page}:${i}`
    const box = makeBox(r, pid, (props.selectedImageBoxIds ?? []).includes(pid))
    if (box) layerOf(r.page)?.appendChild(box)
  }
  // 已框选区域：红色实线持久高亮（待「生成分片」统一成片；点框取消）
  for (const r of props.regions ?? []) {
    if (r.width <= 0 || r.height <= 0) continue
    const box = document.createElement('div')
    box.className = 'ai-pdf-region-box'
    box.style.left = `${r.x * 100}%`
    box.style.top = `${r.y * 100}%`
    box.style.width = `${r.width * 100}%`
    box.style.height = `${r.height * 100}%`
    const label = document.createElement('span')
    label.className = 'ai-pdf-region-box__label'
    label.textContent = r.chars ? `${r.chars} 字` : '区域'
    box.appendChild(label)
    box.title = '已框选区域（点此取消）'
    box.addEventListener('click', (e) => {
      e.stopPropagation()
      emit('region-remove', r.id)
    })
    layerOf(r.page)?.appendChild(box)
  }
}

watch(() => props.selectedImageBoxIds, () => redraw(), { deep: true })
watch(() => props.imageBoxes, () => redraw(), { deep: true })
watch(() => props.regions, () => redraw(), { deep: true })

interface PageRect {
  wrap: HTMLElement
  page: number
  left: number
  top: number
  right: number
  bottom: number
  width: number
  height: number
}
interface RegionDrag {
  startPage: number
  startX: number // 内容坐标（视口 + 容器滚动偏移）
  startY: number
  pages: PageRect[]
  divs: Map<number, HTMLDivElement>
  moved: boolean
}
let regionDrag: RegionDrag | null = null

/** 获取当前已渲染的页面 wrapper 列表（data-page 为 1-based 页码） */
function pageWraps(): HTMLElement[] {
  return Array.from(wrapRef.value?.querySelectorAll<HTMLElement>('.ai-pdf-page-wrap') ?? [])
}

/** 视口 client 坐标 → 容器内容坐标（+scrollTop/scrollLeft），滚动稳定 */
function toContent(clientX: number, clientY: number): { x: number; y: number } {
  return {
    x: clientX + (wrapRef.value?.scrollLeft ?? 0),
    y: clientY + (wrapRef.value?.scrollTop ?? 0),
  }
}

/** 拖拽矩形（内容坐标）与页面求交，返回该页归一化区域（0~1，无交返回 null） */
function intersectRegion(page: PageRect, x1: number, y1: number, x2: number, y2: number) {
  const ix1 = Math.max(x1, page.left)
  const iy1 = Math.max(y1, page.top)
  const ix2 = Math.min(x2, page.right)
  const iy2 = Math.min(y2, page.bottom)
  if (ix2 <= ix1 || iy2 <= iy1) return null
  return {
    x: (ix1 - page.left) / page.width,
    y: (iy1 - page.top) / page.height,
    width: (ix2 - ix1) / page.width,
    height: (iy2 - iy1) / page.height,
  }
}

/** 自动翻页滚动：距容器上/下边缘该距离内触发 */
const AUTO_SCROLL_EDGE = 56

/** 在页面上按下鼠标：进入区域拖拽（红色虚线草稿随光标变化，跨页各页同显，拖到边缘自动翻页） */
function onWrapMouseDown(e: MouseEvent, page: number) {
  if (e.button !== 0) return
  // 点击已框选区域框 / 图片盒 → 由各自的 click 处理（取消/切换），不进入新拖拽
  const t = e.target as HTMLElement
  if (t.closest?.('.ai-pdf-region-box, .ai-pdf-box')) return
  const start = toContent(e.clientX, e.clientY)
  // 按下时固化各页内容坐标 rect：拖拽全程滚动也不漂移
  const pages: PageRect[] = pageWraps().map((w) => {
    const r = w.getBoundingClientRect()
    const c = toContent(r.left, r.top)
    return {
      wrap: w,
      page: Number(w.dataset.page),
      left: c.x,
      top: c.y,
      right: c.x + r.width,
      bottom: c.y + r.height,
      width: r.width,
      height: r.height,
    }
  })
  regionDrag = { startPage: page, startX: start.x, startY: start.y, pages, divs: new Map(), moved: false }

  let lastMouseX = e.clientX
  let lastMouseY = e.clientY
  let autoScrollRaf: number | null = null

  /** 用指定鼠标位置（视口坐标）重算各页草稿 */
  const updateDrafts = (clientX: number, clientY: number) => {
    if (!regionDrag) return
    const cur = toContent(clientX, clientY)
    const x1 = Math.min(regionDrag.startX, cur.x)
    const y1 = Math.min(regionDrag.startY, cur.y)
    const x2 = Math.max(regionDrag.startX, cur.x)
    const y2 = Math.max(regionDrag.startY, cur.y)
    if (!regionDrag.moved && x2 - x1 < 4 && y2 - y1 < 4) return
    regionDrag.moved = true
    // 跨页：拖拽矩形与每一页求交，逐页画草稿
    const seen = new Set<number>()
    for (const p of regionDrag.pages) {
      const r = intersectRegion(p, x1, y1, x2, y2)
      if (!r) continue
      seen.add(p.page)
      let div = regionDrag.divs.get(p.page)
      if (!div) {
        div = document.createElement('div')
        div.className = 'ai-pdf-region-draft'
        p.wrap.querySelector(':scope > .ai-pdf-overlay')?.appendChild(div)
        regionDrag.divs.set(p.page, div)
      }
      div.style.left = `${r.x * 100}%`
      div.style.top = `${r.y * 100}%`
      div.style.width = `${r.width * 100}%`
      div.style.height = `${r.height * 100}%`
    }
    // 清理拖出范围的页草稿
    for (const [pid, div] of regionDrag.divs) {
      if (!seen.has(pid)) {
        div.remove()
        regionDrag.divs.delete(pid)
      }
    }
  }

  const stopAutoScroll = () => {
    if (autoScrollRaf !== null) {
      cancelAnimationFrame(autoScrollRaf)
      autoScrollRaf = null
    }
  }

  /** 拖到容器边缘自动翻页滚动（自绘框选不会触发浏览器原生滚动，需自行驱动） */
  const autoScrollTick = () => {
    if (!regionDrag || !wrapRef.value) {
      autoScrollRaf = null
      return
    }
    const el = wrapRef.value
    const r = el.getBoundingClientRect()
    let delta = 0
    if (lastMouseY < r.top + AUTO_SCROLL_EDGE) {
      delta = -Math.ceil(Math.min(24, (r.top + AUTO_SCROLL_EDGE - lastMouseY) / 3))
    } else if (lastMouseY > r.bottom - AUTO_SCROLL_EDGE) {
      delta = Math.ceil(Math.min(24, (lastMouseY - (r.bottom - AUTO_SCROLL_EDGE)) / 3))
    }
    if (delta !== 0 && el.scrollHeight > el.clientHeight) {
      const before = el.scrollTop
      el.scrollTop = Math.max(0, Math.min(el.scrollHeight - el.clientHeight, before + delta))
      if (el.scrollTop !== before) updateDrafts(lastMouseX, lastMouseY)
    }
    autoScrollRaf = requestAnimationFrame(autoScrollTick)
  }

  const onMove = (ev: MouseEvent) => {
    if (!regionDrag) return
    lastMouseX = ev.clientX
    lastMouseY = ev.clientY
    updateDrafts(ev.clientX, ev.clientY)
    // 接近容器上/下边缘 → 启动自动翻页；离开边缘 → 停止
    const el = wrapRef.value
    const canScroll = el && el.scrollHeight > el.clientHeight
    if (canScroll) {
      const r = el.getBoundingClientRect()
      const nearEdge = lastMouseY < r.top + AUTO_SCROLL_EDGE || lastMouseY > r.bottom - AUTO_SCROLL_EDGE
      if (nearEdge && autoScrollRaf === null) autoScrollRaf = requestAnimationFrame(autoScrollTick)
      if (!nearEdge && autoScrollRaf !== null) stopAutoScroll()
    }
  }

  const onUp = (ev: MouseEvent) => {
    window.removeEventListener('mousemove', onMove)
    window.removeEventListener('mouseup', onUp)
    stopAutoScroll()
    if (!regionDrag) return
    const cur = toContent(ev.clientX, ev.clientY)
    const x1 = Math.min(regionDrag.startX, cur.x)
    const y1 = Math.min(regionDrag.startY, cur.y)
    const x2 = Math.max(regionDrag.startX, cur.x)
    const y2 = Math.max(regionDrag.startY, cur.y)
    regionDrag.divs.forEach((d) => d.remove())
    regionDrag.divs.clear()
    const drag = regionDrag
    regionDrag = null
    let emitted = 0
    // 跨页：每页相交区各自成一个 region（≥1%×1%）
    for (const p of drag.pages) {
      const r = intersectRegion(p, x1, y1, x2, y2)
      if (!r || r.width < 0.01 || r.height < 0.01) continue
      const regionInfo = countRegionChars(p.page, r.x, r.y, r.width, r.height)
      emit('region-select', {
        id: `region_${Date.now()}_${p.page}_${Math.round(r.x * 1000)}${Math.round(r.y * 1000)}`,
        page: p.page, x: r.x, y: r.y, width: r.width, height: r.height,
        chars: regionInfo.chars,
        text: regionInfo.text,
      })
      emitted++
    }
    if (emitted > 0) return
    // 未成区域（点击/小拖拽）→ 退化为原生文字选中 → 文字分片
    const sel = window.getSelection()
    const text = sel ? sel.toString().trim() : ""
    if (text.length >= 2) emit("text-select", { text, page: drag.startPage })
  }
  window.addEventListener('mousemove', onMove)
  window.addEventListener('mouseup', onUp)
}

/** 统计框选区域内文字层 span 的字符数（高亮提示用，仅前端文本层，零后端开销） */
function countRegionChars(page: number, x: number, y: number, w: number, h: number): { chars: number; text: string } {
  const wrap = wrapRef.value?.querySelector(`.ai-pdf-page-wrap[data-page="${page}"]`)
  const spans = wrap?.querySelectorAll('.textLayer span')
  if (!spans || !wrap) return { chars: 0, text: '' }
  const wrapRect = wrap.getBoundingClientRect()
  let chars = 0
  const textParts: string[] = []
  spans.forEach((sp) => {
    const el = sp as HTMLElement
    const r = el.getBoundingClientRect()
    if (!r.width || !r.height) return
    // 行盒中心落在区域内的文字计入
    const cx = ((r.left - wrapRect.left) / wrapRect.width)
    const cy = ((r.top - wrapRect.top) / wrapRect.height)
    if (cx >= x && cx <= x + w && cy >= y && cy <= y + h) {
      const t = (el.textContent || '').trim()
      chars += t.length
      if (t) textParts.push(t)
    }
  })
  return { chars, text: textParts.join(' ') }
}

async function render() {
  if (rendering || disposed) return
  const url = props.src || props.file?.url
  if (!url) {
    renderError.value = "文件无可预览地址"
    return
  }
  rendering = true
  try {
    const pdfjs: any = await import("pdfjs-dist")
    const workerSrc = (await import("pdfjs-dist/build/pdf.worker.min.mjs?url")).default
    pdfjs.GlobalWorkerOptions.workerSrc = workerSrc

    // blob: URL 为本地对象，复用父组件已下载的内容且无需鉴权头
    const isBlobUrl = url.startsWith("blob:")
    const token = storage.get("token")
    const resp = await fetch(url, {
      headers: !isBlobUrl && token ? { Authorization: `Bearer ${token}` } : {},
    })
    if (!resp.ok) throw new Error(`加载失败（HTTP ${resp.status}）`)
    const ab = await resp.arrayBuffer()
    if (!ab || ab.byteLength < 10) throw new Error("PDF 内容为空（鉴权或 CORS 问题）")

    pdfDoc = await pdfjs.getDocument({ data: ab }).promise
    if (disposed) return
    for (let p = 1; p <= pdfDoc.numPages; p++) {
      const page = await pdfDoc.getPage(p)
      const viewport = page.getViewport({ scale: scale.value })
      const wrapper = document.createElement("div")
      wrapper.className = "ai-pdf-page-wrap"
      wrapper.setAttribute("data-page", String(p))
      // --scale-factor：pdf.js 文字层定位所必需（与渲染 viewport 同比例）
      wrapper.style.setProperty("--scale-factor", String(scale.value))
      wrapper.style.width = "100%"
      wrapper.style.maxWidth = `${viewport.width}px`
      const canvas = document.createElement("canvas")
      canvas.width = viewport.width
      canvas.height = viewport.height
      // canvas CSS 等比：height:auto 保持纵横比（flex 弹窗挂载初期容器宽度可能为 0，
      // 用 maxWidth 保底避免被 shrink-to-fit 压成 ~0 宽空白细线，见 flex-shrink:0）
      canvas.style.width = "100%"
      canvas.style.height = "auto"
      const ctx = canvas.getContext("2d")
      if (!ctx) throw new Error("canvas context 创建失败")
      await page.render({ canvasContext: ctx, viewport }).promise
      if (disposed) return
      wrapper.appendChild(canvas)

      // 原生文字层：透明文字 span 覆盖在渲染结果上，支持像普通 PDF 阅读器一样拖选文字
      const textLayerDiv = document.createElement("div")
      textLayerDiv.className = "textLayer"
      wrapper.appendChild(textLayerDiv)
      // v4 的 TextLayer 需要 viewport（内部读取 scale/rotation/rawDims 定位文字）
      const textLayer = new pdfjs.TextLayer({ textContentSource: page.streamTextContent(), container: textLayerDiv, viewport })
      // ★ 关键：pdf.js 会用 setLayerDimensions 给容器写入固定内联尺寸
      //   (calc(var(--scale-factor) * pageWidth))，与 canvas 的 width:100%/height:auto
      //   实际尺寸不一致 → 文字层与 canvas 坐标错位。这里强制容器铺满 wrapper：
      //   文字层 span 是百分比定位，容器与 canvas 同框后天然对齐，与 scale/dpr 无关。
      textLayerDiv.style.position = 'absolute'
      textLayerDiv.style.inset = '0'
      textLayerDiv.style.width = '100%'
      textLayerDiv.style.height = '100%'
      textLayer.render().catch((e: any) => console.warn("[AiChunkPdfOverlay] textLayer:", e))

      const layer = document.createElement("div")
      layer.className = "ai-pdf-overlay"
      wrapper.appendChild(layer)
      // 按下进入区域拖拽；松开时 onUp 统一处理：成区域→region-select（跨页逐页），否则退化为文字选中→text-select
      wrapper.addEventListener("mousedown", (e) => onWrapMouseDown(e, p))
      wrapRef.value?.appendChild(wrapper)
    }
    renderedPages.value = pdfDoc.numPages
    redraw()
    probeTextLayer()
  } catch (e: any) {
    if (disposed) return
    renderError.value = (e?.name ? e.name + ": " : "") + (e?.message || "pdf.js 渲染失败")
    console.error("[AiChunkPdfOverlay] render error:", e)
  } finally {
    rendering = false
  }
}

onMounted(render)

/**
 * 文字层探测：逐页 getTextContent 统计字符数（不渲染、不 OCR，开销远小于解析）。
 * 判定阈值与后端解析 OCR 兜底一致（<50 字符视作扫描页）。
 * 父组件据此决定打开时是否解析——扫描件整本 OCR 动辄分钟级，不应在打开时自动发生。
 */
async function probeTextLayer() {
  try {
    const total = pdfDoc?.numPages ?? 0
    if (!total || disposed) {
      emit('scan-check', { pages: 0, scannedPages: 0 })
      return
    }
    let scannedPages = 0
    for (let p = 1; p <= total; p++) {
      if (disposed) return
      const page = await pdfDoc.getPage(p)
      const tc = await page.getTextContent()
      const text = (tc.items as Array<{ str?: string }>).map((i) => i.str ?? '').join('')
      if (text.trim().length < 50) scannedPages++
    }
    if (!disposed) emit('scan-check', { pages: total, scannedPages })
  } catch (e) {
    console.warn('[AiChunkPdfOverlay] text layer probe failed:', e)
    // 探测失败按"有文字层"处理（pages=0 → 父组件走原解析路径，保持旧行为）
    if (!disposed) emit('scan-check', { pages: 0, scannedPages: 0 })
  }
}

onUnmounted(() => {
  disposed = true
  try {
    pdfDoc?.destroy?.()
  } catch {
    /* ignore */
  }
  pdfDoc = null
})
</script>

<template>
  <div ref="wrapRef" class="ai-chunk-pdf-overlay">
    <div v-if="renderedPages > 0 && legendItems.length" class="ai-chunk-pdf-overlay__legend">
      <span v-for="[type, s] in legendItems" :key="type" class="ai-chunk-pdf-overlay__legend-item">
        <i :style="{ background: s.color }"></i>{{ s.label }}
      </span>
    </div>
    <el-empty v-if="renderError" :description="renderError" :image-size="80" />
    <el-empty v-else-if="renderedPages === 0" description="正在用 pdf.js 渲染页面..." :image-size="80" />
  </div>
</template>

<style lang="scss" scoped>
@use "@/assets/styles/variables" as *;
.ai-chunk-pdf-overlay {
  flex: 1;
  min-height: 0;
  overflow: auto;
  overscroll-behavior: contain;
  background: $bg-page;
  padding: $spacing-base;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $spacing-base;

  :deep(.ai-pdf-page-wrap) {
    // 禁止 flex 纵轴压缩：容器是纵向 flex 且高度被 92vh 弹窗链硬约束，
    // 页数多时 flex-shrink 默认 1 会把每页压成 ~53px 的空白细条
    flex-shrink: 0;
    position: relative;
    background: #fff;
    box-shadow: $shadow-base;
    border-radius: $radius-sm;
    overflow: hidden;
    // 像截图工具一样：整个页面可拖拽框选区域
    cursor: crosshair;
    // 宽度自适应由内联 width:100% + maxWidth=渲染像素上限接管；
    // overlay 用百分比定位，无需与 canvas 严格 1:1

    canvas { display: block; }
  }

  :deep(.ai-pdf-overlay) {
    position: absolute;
    inset: 0;
    pointer-events: none;
  }

  // 内容图片盒：绿色边框，可点击选中（选中 → 生成分片时对该区域 OCR）
  :deep(.ai-pdf-box) {
    position: absolute;
    border: 1.5px solid var(--box-color, #10b981);
    border-radius: 2px;
    pointer-events: auto;
    cursor: pointer;
    opacity: 0.85;
    transition: all 0.12s;
    // 必须高于文字层（z-index:2）——否则文字层的透明 span 拦截点击，图片框无法选中
    z-index: 3;

    &:hover {
      opacity: 1;
      border-width: 2.5px;
      background: rgba(16, 185, 129, 0.15);
      z-index: 4;
    }

    &.is-selected {
      opacity: 1;
      border-width: 2.5px;
      background: rgba(16, 185, 129, 0.2);
      box-shadow: 0 0 0 2px rgba(16, 185, 129, 0.45);
      z-index: 5;
    }
  }

  // 拖拽框选区域的草稿（红色虚线，拖拽中）
  :deep(.ai-pdf-region-draft) {
    position: absolute;
    border: 2px dashed #f56c6c;
    background: rgba(245, 108, 108, 0.1);
    z-index: 6;
    pointer-events: none;
  }

  // 已框选区域的持久高亮（红色实线，点框取消）
  :deep(.ai-pdf-region-box) {
    position: absolute;
    border: 2px solid #f56c6c;
    background: rgba(245, 108, 108, 0.12);
    border-radius: 2px;
    z-index: 5;
    pointer-events: auto;
    cursor: pointer;
    transition: all 0.12s;

    &:hover {
      background: rgba(245, 108, 108, 0.22);
      border-width: 3px;
      z-index: 7;
    }
  }

  :deep(.ai-pdf-region-box__label) {
    position: absolute;
    top: -19px;
    left: -2px;
    background: #f56c6c;
    color: #fff;
    font-size: 10px;
    line-height: 1;
    font-weight: 500;
    padding: 3px 6px;
    border-radius: 3px 3px 0 0;
    white-space: nowrap;
  }

  :deep(.ai-pdf-box__label) {
    position: absolute;
    top: -1px;
    left: -1px;
    background: var(--box-color, #10b981);
    color: #fff;
    font-size: 10px;
    line-height: 1;
    font-weight: 500;
    padding: 2px 5px;
    border-radius: 2px 0 3px 0;
    white-space: nowrap;
  }

  // 原生文字层（pdf.js TextLayer）：透明文字 span，支持浏览器原生拖选
  :deep(.textLayer) {
    position: absolute;
    inset: 0;
    overflow: hidden;
    line-height: 1;
    text-size-adjust: none;
    forced-color-adjust: none;
    z-index: 2;

    span,
    br {
      color: transparent;
      position: absolute;
      white-space: pre;
      cursor: crosshair;
      transform-origin: 0% 0%;
    }

    span::selection {
      background: rgba(59, 130, 246, 0.3);
    }
  }

  &__legend {
    position: sticky;
    top: 0;
    z-index: 5;
    display: flex;
    flex-wrap: wrap;
    gap: 4px 14px;
    padding: 5px 12px;
    background: rgba(255, 255, 255, 0.94);
    border: 1px solid #e4e7ed;
    border-radius: 6px;
    box-shadow: $shadow-base;
    font-size: 11px;
    color: #6b7280;

    &-item {
      display: inline-flex;
      align-items: center;
      gap: 4px;

      i {
        width: 8px;
        height: 8px;
        border-radius: 2px;
      }
    }
  }
}
</style>
