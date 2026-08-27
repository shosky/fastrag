import { chromium } from 'playwright-core'
import { createHmac } from 'node:crypto'
const S = 'fastrag-onlyoffice-dev-secret-change-me'
const b64u = (b) => Buffer.from(b).toString('base64url')
const h = b64u(JSON.stringify({ alg: 'HS256', typ: 'JWT' }))
const p = b64u(JSON.stringify({ sub: JSON.stringify({ kbId: '2091807361146351617', fileId: '2092581970738507777', scope: 'onlyoffice-raw' }), iat: Math.floor(Date.now() / 1000), exp: Math.floor(Date.now() / 1000) + 1800 }))
const token = `${h}.${p}.${b64u(createHmac('sha256', Buffer.from(S)).update(h + '.' + p).digest())}`
const GUID = 'asc.{FB71C2D3-9A64-4F0E-B18E-C1A71097E201}'
const browser = await chromium.launch({ executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', headless: true })
const page = await browser.newPage()
page.on('console', (m) => console.log('[c]', m.text().slice(0, 140)))
await page.goto(`http://localhost:3000/__oo_probe.html?kbId=2091807361146351617&fileId=2092581970738507777&token=${encodeURIComponent(token)}&guid=${encodeURIComponent(GUID)}`, { waitUntil: 'domcontentloaded', timeout: 30000 })
await page.waitForTimeout(10000)
const ed = page.frames().find((f) => /documenteditor\/main/.test(f.url()))
const res1 = await ed.evaluate((g) => {
  const out = {}
  try {
    const app = window.DE
    const pc = app.getController('Common.Controllers.Plugins')
    const main = app.getController('Main')
    out.mainApiType = typeof main.api
    const api = main.api || (window.Asc && window.Asc.editor ? window.Asc.editor : null)
    out.apiViaAsc = !!window.Asc?.editor
    if (api && api.asc_pluginRun) {
      // 实验：从集合模型强制重建 CPlugin 并注册，再运行（绕开常规注册时序）
      try {
        const col = app.getCollection('Common.Collections.Plugins')
        const target = col.models.find((m) => m.get('guid').includes('FB71C2D3'))
        if (target) {
          const cp = new (Object.getPrototypeOf(api && window.DE && window.DE.prototype ? Object : Object))()
          const registered = []
          const ascC = window.Asc && window.Asc.CPlugin ? new window.Asc.CPlugin() : null
          if (ascC) {
            const orig = target.get('original') || {}
            ascC.deserialize(orig)
            registered.push(ascC)
            api.asc_pluginsRegister('', registered)
            console.log('[FORCE] registered:', JSON.stringify(registered.map(r => r.get_Name?.())))
          }
        }
      } catch (eForce) {
        console.log('[FORCE-ERR]', eForce.message)
      }
      // hook 一下看插件 manager 是否收到
      const orig = api.asc_pluginsRegister
      console.log('[HOOK] asc_pluginsRegister exists:', typeof orig)
      out.runResult = String(api.asc_pluginRun(g, 0, ''))
      out.ranOk = 'called'
    }
  } catch (e) {
    out.err = e.message
  }
  return out
}, 'asc.{FB71C2D3-9A64-4F0E-B18E-C1A71097E201}')
console.log('[STEP1]', JSON.stringify(res1))
await page.waitForTimeout(6000)
const frames = page.frames().filter((f) => /chunk-boundary/.test(f.url())).map((f) => f.url().slice(0, 90))
console.log('[FRAMES]', JSON.stringify(frames))
console.log(frames.length > 0 ? 'RESULT GREEN-ish(窗口创建)' : 'RESULT STILL-SILENT')
await browser.close()
process.exit(frames.length > 0 ? 0 : 1)
