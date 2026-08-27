/**
 * [DEBUG-pdf] headless 复现循环：pdf.js 渲染用户真实 PDF → 统计首屏 canvas 非白像素比例
 * 用法：node scripts/dbg-pdf-render.mjs
 * 退出码：0 = canvas 有内容(非白像素>阈值)，1 = 空白
 */
import http from 'node:http'
import { readFileSync, existsSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import puppeteer from 'puppeteer-core'

const __dir = dirname(fileURLToPath(import.meta.url))
const root = join(__dir, '..')
const PDF_PATH_RAW = 'C:\\Users\\63179\\Downloads\\AI-Agent\u5e73\u53f0\u5916\u90e8\u7cfb\u7edf\u63a5\u5165\u6307\u5357.pdf'
const pdfPath = existsSync(PDF_PATH_RAW) ? PDF_PATH_RAW : PDF_PATH_RAW
const chrome = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'

const mime = { '.mjs': 'text/javascript; charset=utf-8', '.pdf': 'application/pdf', '.wasm': 'application/wasm', '.pfb': 'application/octet-stream' }
const server = http.createServer((req, res) => {
  const url = new URL(req.url, 'http://x')
  const p = decodeURIComponent(url.pathname)
  let file
  if (p === '/') {
    res.writeHead(200, { 'Content-Type': 'text/html' }); res.end('<!doctype html><canvas id="c"></canvas>'); return
  }
  if (p.startsWith('/fonts/')) {
    file = join(root, 'node_modules', 'pdfjs-dist', 'standard_fonts', p.slice('/fonts/'.length))
  } else if (p.startsWith('/pdfjs/')) {
    file = join(root, 'node_modules', 'pdfjs-dist', 'build', p.slice('/pdfjs/'.length))
  } else if (p === '/test.pdf') {
    file = pdfPath
  } else {
    res.writeHead(404); res.end('nf'); return
  }
  if (!existsSync(file)) { res.writeHead(404); res.end('no file'); return }
  const ext = p.endsWith('.mjs') ? '.mjs' : p.endsWith('.wasm') ? '.wasm' : p.endsWith('.pfb') ? '.pfb' : '.pdf'
  res.writeHead(200, { 'Content-Type': mime[ext] || 'application/pdf' })
  res.end(readFileSync(file))
})

const port = 18765
async function main() {
  await new Promise((r) => server.listen(port, r))
  const browser = await puppeteer.launch({ executablePath: chrome, headless: 'new', args: ['--no-sandbox', '--disable-gpu'] })
  const page = await browser.newPage()
  const consoleMsgs = []
  const respFail = []
  page.on('console', (m) => consoleMsgs.push(`[${m.type()}] ${m.text()}`))
  page.on('pageerror', (e) => consoleMsgs.push(`[pageerror] ${e.message}`))
  page.on('response', (r) => { if (r.status() >= 400) respFail.push(`${r.status()} ${r.url()}`) })
  page.on('requestfailed', (r) => respFail.push(`REQFAIL ${r.url()} ${r.failure()?.errorText}`))

  await page.goto(`http://127.0.0.1:${port}/`)
  const result = await page.evaluate(async ({ port }) => {
    const pdfjs = await import(`http://127.0.0.1:${port}/pdfjs/pdf.mjs`)
    pdfjs.GlobalWorkerOptions.workerSrc = `http://127.0.0.1:${port}/pdfjs/pdf.worker.min.mjs`
    pdfjs.GlobalWorkerOptions.standardFontDataUrl = `http://127.0.0.1:${port}/fonts/`
    const resp = await fetch(`http://127.0.0.1:${port}/test.pdf`)
    const ab = await resp.arrayBuffer()
    const doc = await pdfjs.getDocument({ data: ab }).promise
    const page = await doc.getPage(1)
    const viewport = page.getViewport({ scale: 1.5 })
    const canvas = document.getElementById('c')
    canvas.width = viewport.width; canvas.height = viewport.height
    const ctx = canvas.getContext('2d')
    await page.render({ canvasContext: ctx, viewport }).promise
    const data = ctx.getImageData(0, 0, canvas.width, canvas.height).data
    let dark = 0, total = data.length / 4
    for (let i = 0; i < data.length; i += 4) {
      if (data[i] < 200 && data[i + 1] < 200 && data[i + 2] < 200) dark++
    }
    return { pages: doc.numPages, viewport: [canvas.width, canvas.height], darkRatio: dark / total, darkPixels: dark }
  }, { port })

  console.log(JSON.stringify({ pdf: pdfPath, totalPages: result.pages, canvasSize: result.viewport, darkRatio: Number(result.darkRatio.toFixed(4)), respFail, consoleMsgs }, null, 2))
  await browser.close(); server.close()
  process.exit(result.darkRatio > 0.0005 ? 0 : 1)
}
main().catch((e) => { console.error('LOOP ERROR', e); server.close(); process.exit(2) })