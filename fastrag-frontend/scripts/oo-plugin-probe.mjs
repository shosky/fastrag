/**
 * OO 插件加载探针（diagnosing-bugs Phase 1 反馈回路）。
 *
 * 用无头 Edge 打开 public/__oo_probe.html（最小化 DocEditor + autostart 配置），
 * 捕获所有 frame 的 console / 网络响应，断言「插件脚本第一行日志」是否出现。
 *
 * 退出码：0 = 插件脚本已执行（绿）；1 = 未执行（红）。
 *
 * 用法：
 *   node scripts/oo-plugin-probe.mjs [--kb ID] [--file ID] [--wait MS]
 *                                   [--guid GUID]         覆盖 autostart 条目
 *                                   [--client-plugins-data JSON] 同时注入客户端 pluginsData
 */
import { chromium } from 'playwright-core'
import { createHmac } from 'node:crypto'

const KB = process.env.OO_PROBE_KB || '2091807361146351617'
const FILE = process.env.OO_PROBE_FILE || '2092581970738507777'
const args = process.argv.slice(2)
function argOf(name, fallback) {
  const i = args.indexOf(name)
  return i >= 0 && args[i + 1] ? args[i + 1] : fallback
}
const waitMs = Number(argOf('--wait', '25000'))
const guidOverride = argOf('--guid', '')
const clientPluginsData = argOf('--client-plugins-data', '')

const SECRET = 'fastrag-onlyoffice-dev-secret-change-me'
const b64u = (buf) => Buffer.from(buf).toString('base64url')
const nowSec = Math.floor(Date.now() / 1000)
const header = b64u(JSON.stringify({ alg: 'HS256', typ: 'JWT' }))
const payloadObj = {
  // 与 Java OnlyOfficeJwtUtil.signPayload 一致：claims JSON 放在 subject
  sub: JSON.stringify({
    kbId: argOf('--kb', KB),
    fileId: argOf('--file', FILE),
    scope: 'onlyoffice-raw',
  }),
}
payloadObj.iat = nowSec
payloadObj.exp = nowSec + 1800
const payload = b64u(JSON.stringify(payloadObj))
const data = `${header}.${payload}`
const sig = b64u(createHmac('sha256', Buffer.from(SECRET, 'utf8')).update(data).digest())
const token = `${data}.${sig}`

const KB_ID = argOf('--kb', KB)
const FILE_ID = argOf('--file', FILE)
const probeUrl =
  `http://localhost:3000/__oo_probe.html?kbId=${KB_ID}&fileId=${FILE_ID}&token=${encodeURIComponent(token)}` +
  (guidOverride ? `&guid=${encodeURIComponent(guidOverride)}` : '') +
  (clientPluginsData ? `&clientPluginsData=${encodeURIComponent(clientPluginsData)}` : '')

console.log(`[probe] url=${probeUrl}`)
const logs = []
const browser = await chromium.launch({
  executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe',
  headless: true,
})
try {
  const page = await browser.newPage()
  const redirect = (type) => (msg) => {
    const line = `[${type}] ${msg.text()}`
    logs.push(line)
    console.log(line)
  }
  page.on('console', (m) => redirect(m.type())(m))
  page.on('pageerror', (e) => {
    const line = `[PAGEERROR] ${e.message}`
    logs.push(line)
    console.log(line)
  })
  page.on('requestfailed', (r) => {
    if (/plugin|sdkjs-plugins/i.test(r.url())) {
      const line = `[REQFAIL] ${r.status() || ''} ${r.url()} :: ${r.failure()?.errorText ?? ''}`
      logs.push(line)
      console.log(line)
    }
  })
  page.on('response', (r) => {
    if (/plugins\.json$|sdkjs-plugins/i.test(r.url())) {
      const line = `[NET] ${r.status()} ${r.url()}`
      logs.push(line)
      console.log(line)
    }
  })

  await page.goto(probeUrl, { waitUntil: 'domcontentloaded', timeout: 30000 })
  await page.waitForTimeout(waitMs)

  // === 交互验证：聚焦编辑器后 Ctrl+S 全选（任何能取到选区的实现都应返回非空） ===
  let selectionText = ''
  try {
    await page.screenshot({ path: 'probe-shot-before.png' })
    await page.mouse.click(600, 400, { delay: 30 })
    await page.waitForTimeout(500)
    await page.keyboard.press('Control+a')
    await page.waitForTimeout(1200)
    const hit = logs.find((l) => /GetSelectedText => ".*[^"]"/.test(l))
    if (hit && !/\(空/.test(hit)) {
      selectionText = hit
      console.log(`[probe] 全选探测命中: ${hit}`)
    } else {
      // 兜底：再试一次三击常见正文点位
      for (const [x, y] of [[550, 300], [550, 380], [550, 460]]) {
        await page.mouse.click(x, y, { clickCount: 3, delay: 40 })
        await page.waitForTimeout(800)
        const hit2 = logs.find((l) => /GetSelectedText => ".*[^"]"/.test(l))
        if (hit2 && !/\(空/.test(hit2)) {
          selectionText = hit2
          console.log(`[probe] 三击(${x},${y})命中: ${hit2}`)
          break
        }
      }
    }
    if (!selectionText) console.log('[probe] 选中文本探测: 未取得文本')
    await page.screenshot({ path: 'probe-shot-after.png' })
  } catch (e) {
    console.log('[probe] 交互失败:', e?.message)
  }

  // === 深度诊断：进入插件 frame，读取它真实收到的 HTML 与脚本环境 ===
  try {
    const frames = page.frames()
    console.log('='.repeat(60))
    console.log(`[DIAG] frames(${frames.length}):`)
    for (const f of frames) {
      console.log(`[DIAG]   url=${f.url()}`)
      if (/chunk-boundary/.test(f.url())) {
        const info = await f.evaluate(async () => {
          const scripts = [...document.querySelectorAll('script')].map((s) => s.src || '(inline)')
          const resp = await fetch(location.href)
          const body = await resp.text()
          return {
            ascType: typeof window.Asc,
            ascPluginType: typeof window.Asc?.plugin,
            scripts,
            selfHtmlHasV1: body.includes('v1/plugins.js'),
            selfHtmlLen: body.length,
          }
        })
        console.log('[DIAG] plugin frame:', JSON.stringify(info, null, 2))
      }
    }
  } catch (e) {
    console.log('[DIAG] failed:', e?.message)
  }

  const executed = logs.some((l) => l.includes('[OO] plugin script executed'))
  console.log('='.repeat(60))
  const green = executed && selectionText
  console.log(green ? '[probe] GREEN — 插件执行 + 取到选中文本' : '[probe] RED — ' + (executed ? '插件已执行但未取到选中文本' : '插件脚本未执行'))
  process.exitCode = green ? 0 : 1
} finally {
  await browser.close()
}
