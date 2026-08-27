<script setup lang="ts">
import type { KnowledgeFile, AiChunkParagraph, AiChunkLayoutBlock } from "@/types/knowledge"
import { ElMessage } from 'element-plus'
import { storage } from "@/utils/storage"

const props = defineProps<{
  file: KnowledgeFile | null
  paragraphs: AiChunkParagraph[]
  activeParagraphIds: string[]
  kbId?: string
  /** 父组件已下载好的 blob URL，传入则直接复用，避免二次下载 */
  src?: string
  /** 内容图片位置（归一化 0~1；VLM 版面分析不可用时的兜底数据） */
  imageBoxes?: Array<{ page: number; x: number; y: number; width: number; height: number }>
  /** VLM 版面分析块（归一化 0~1；优先数据源，SSE 渐进推送时逐步出现） */
  layoutBlocks?: AiChunkLayoutBlock[]
}>()

/**
 * 块类型 → 框颜色与标签（版面分析可视化）。
 * key 同时兼容 VLM 版面类型（title/text）与段落模型类型（heading/paragraph）。
 */
const BOX_STYLES: Record<string, { color: string; label: string }> = {
  title: { color: '#f59e0b', label: 'Title' },
  heading: { color: '#f59e0b', label: 'Title' },
  text: { color: '#3b82f6', label: 'Text' },
  paragraph: { color: '#3b82f6', label: 'Text' },
  image: { color: '#10b981', label: 'image' },
  table: { color: '#8b5cf6', label: 'table' },
  code: { color: '#06b6d4', label: 'code' },
  formula: { color: '#ec4899', label: 'formula' },
  list: { color: '#eab308', label: 'list' },
  caption: { color: '#9ca3af', label: 'caption' },
}

/** 图例只展示当前数据源实际出现的类型 */
const legendItems = computed(() => {
  const present = new Set<string>()
  if (props.layoutBlocks?.length) {
    props.layoutBlocks.forEach((b) => present.add(b.type))
  } else {
    props.paragraphs.forEach((p) => present.add(p.type))
    if (props.imageBoxes?.length) present.add('image')
  }
  return Object.entries(BOX_STYLES).filter(([t]) => present.has(t))
})

function normText(s?: string): string {
  return (s ?? '').replace(/\u3000/g, ' ').replace(/\s+/g, ' ').trim()
}

/** 版面块 → 段落联动匹配：块文本与段落文本归一化互含（双向均 ≥6 字符才判定） */
function matchParagraphId(blockText?: string): string | null {
  const bt = normText(blockText)
  if (bt.length < 6) return null
  for (const p of props.paragraphs) {
    const pt = normText(p.text)
    if (pt.length < 6) continue
    if (pt.includes(bt) || bt.includes(pt)) return p.id
  }
  return null
}

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

/**
 * 画一个内容块框：坐标已归一化 0~1（顶左原点），全部用百分比定位 →
 * overview 层与 canvas 等宽（CSS width:100%），框随页宽自动等比缩放（响应式）。
 */
function makeBox(rect: { x: number; y: number; width: number; height: number }, type: string, active: boolean, text = ''): HTMLElement | null {
  if (rect.width <= 0 || rect.height <= 0) return null
  const style = BOX_STYLES[type] ?? BOX_STYLES.text
  const box = document.createElement('div')
  box.className = 'ai-pdf-box'
  box.style.setProperty('--box-color', style.color)
  box.style.left = `${rect.x * 100}%`
  box.style.top = `${rect.y * 100}%`
  box.style.width = `${rect.width * 100}%`
  box.style.height = `${rect.height * 100}%`
  const label = document.createElement('span')
  label.className = 'ai-pdf-box__label'
  label.textContent = style.label
  box.appendChild(label)
  // 有文本的块：hover 框时在框下方浮出操作条（复制块内容）
  if (text && text.trim()) {
    const actions = document.createElement('div')
    actions.className = 'ai-pdf-box__actions'
    const btn = document.createElement('button')
    btn.type = 'button'
    btn.textContent = '复制'
    btn.addEventListener('click', (e) => {
      e.stopPropagation()
      copyText(text)
    })
    actions.appendChild(btn)
    box.appendChild(actions)
  }
  if (active) box.classList.add('is-active')
  return box
}

/** 复制文本到剪贴板（clipboard API，降级 execCommand） */
async function copyText(t: string) {
  const s = t.trim()
  if (!s) return
  try {
    await navigator.clipboard.writeText(s)
    ElMessage.success('已复制')
  } catch {
    const ta = document.createElement('textarea')
    ta.value = s
    ta.style.position = 'fixed'
    ta.style.opacity = '0'
    document.body.appendChild(ta)
    ta.select()
    try {
      document.execCommand('copy')
      ElMessage.success('已复制')
    } catch {
      ElMessage.warning('复制失败')
    }
    ta.remove()
  }
}

function redraw(activeIds: Set<string>) {
  if (!wrapRef.value) return
  wrapRef.value.querySelectorAll('.ai-pdf-box').forEach((n) => n.remove())
  // 优先：VLM 版面分析块（块文本匹配段落实现 hover 联动，匹配不上仅展示）
  if (props.layoutBlocks?.length) {
    for (const b of props.layoutBlocks) {
      if (b.width <= 0 || b.height <= 0) continue
      const pid = matchParagraphId(b.text)
      const box = makeBox(b, b.type, pid != null && activeIds.has(pid), b.text)
      if (box) layerOf(b.page)?.appendChild(box)
    }
    return
  }
  // 兜底：段落文字盒（rects）+ 内容图片盒（同为归一化坐标系）
  for (const p of props.paragraphs) {
    if (!p.rects) continue
    for (const r of p.rects) {
      // 面积过滤只针对文字段落盒（防行盒错位框住整页）；图片盒大图合法
      if (p.type !== 'image' && r.width * r.height > 0.7) continue
      const box = makeBox(r, p.type, activeIds.has(p.id), p.text)
      if (box) layerOf(r.page)?.appendChild(box)
    }
  }
  for (const r of props.imageBoxes ?? []) {
    const box = makeBox(r, 'image', false, '')
    if (box) layerOf(r.page)?.appendChild(box)
  }
}

watch(() => props.activeParagraphIds, (ids) => {
  redraw(new Set(ids))
}, { deep: true })

// SSE 渐进推送 layout 事件时逐页补框（初始 paragraph watch 覆盖不到首帧前的布局）
watch(() => props.layoutBlocks, () => {
  redraw(new Set(props.activeParagraphIds))
}, { deep: true })

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
      // 响应式：横向撑满容器（受 inline maxWidth = 渲染自然像素上限约束，不过度放大），
      // canvas/overlay 等比缩放，百分比定位的框自动跟随页宽
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
      const layer = document.createElement("div")
      layer.className = "ai-pdf-overlay"
      wrapper.appendChild(layer)
      wrapRef.value?.appendChild(wrapper)
    }
    renderedPages.value = pdfDoc.numPages
    redraw(new Set(props.activeParagraphIds))
  } catch (e: any) {
    if (disposed) return
    renderError.value = (e?.name ? e.name + ": " : "") + (e?.message || "pdf.js 渲染失败")
    console.error("[AiChunkPdfOverlay] render error:", e)
  } finally {
    rendering = false
  }
}

onMounted(render)

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
    // 宽度自适应由内联 width:100% + maxWidth=渲染像素上限接管；
    // overlay 用百分比定位，无需与 canvas 严格 1:1

    canvas { display: block; }
  }

  :deep(.ai-pdf-overlay) {
    position: absolute;
    inset: 0;
    pointer-events: none;
  }

  :deep(.ai-pdf-box) {
    position: absolute;
    // 版面分析可视化：每块内容常显彩色边框，颜色由 --box-color 按类型注入
    border: 1.5px solid var(--box-color, #3b82f6);
    border-radius: 2px;
    // 允许鼠标悬停变色（pointer-events 从 overlay 层继承为 none，这里单独放开）
    pointer-events: auto;
    cursor: default;
    opacity: 0.85;
    transition: all 0.12s;

    &:hover {
      // 鼠标放到框上：框内类型色填充 + 边框加粗，突出选中内容
      opacity: 1;
      border-width: 2.5px;
      background: color-mix(in srgb, var(--box-color, #3b82f6) 30%, transparent);
      z-index: 3;
    }

    &.is-active {
      // 右侧分片卡 hover 联动：更强高亮
      opacity: 1;
      border-width: 2.5px;
      background: color-mix(in srgb, var(--box-color, #3b82f6) 38%, transparent);
      z-index: 2;
    }
  }

  :deep(.ai-pdf-box__label) {
    position: absolute;
    top: -1px;
    left: -1px;
    background: var(--box-color, #3b82f6);
    color: #fff;
    font-size: 10px;
    line-height: 1;
    font-weight: 500;
    padding: 2px 5px;
    border-radius: 2px 0 3px 0;
    white-space: nowrap;
  }

  // 悬停框时在框下方浮出操作条（复制块内容）
  :deep(.ai-pdf-box__actions) {
    position: absolute;
    top: calc(100% + 2px);
    left: 0;
    z-index: 6;
    display: none;
    align-items: center;
    gap: 2px;
    padding: 2px;
    background: #fff;
    border: 1px solid #e4e7ed;
    border-radius: 4px;
    box-shadow: $shadow-base;

    button {
      border: none;
      background: transparent;
      font-size: 11px;
      line-height: 1;
      color: #409eff;
      cursor: pointer;
      padding: 4px 8px;
      border-radius: 3px;

      &:hover {
        background: #ecf5ff;
      }
    }
  }

  :deep(.ai-pdf-box:hover .ai-pdf-box__actions) {
    display: flex;
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