/**
 * OnlyOffice Plugin: ChunkBoundary —— 选区桥（Selection Bridge）
 *
 * <p>职责：在编辑器内部轮询 executeMethod('GetSelectedText')，把用户选中文本
 * 实时广播给宿主页面（FastRAG AiChunkPanel），用于「原件渲染内按段落生成分片」。</p>
 *
 * <p>背景：OO 7.4/8.3 文档编辑器外层 DocsAPI 既无 onSelectionChanged 事件、也无
 * getSelectedText 方法，宿主无法获取选区；插件运行在编辑器内部，是唯一通道。</p>
 *
 * <p>传输：插件页由 docserver 托管时与宿主跨源。三条并发上行通道：
 * <ol>
 *   <li>注入同源中继 iframe（__oo_relay.html，宿主源）→ 同源 BroadcastChannel</li>
 *   <li>parent.postMessage 直发（同源部署时有效）</li>
 *   <li>parent.postMessage 包装 onExternalPluginMessageCallback（OO 官方转发形态）</li>
 * </ol></p>
 */
;(function () {
  'use strict'

  // 最先执行点：确认插件脚本是否被 OO 启动
  console.log('[OO] plugin script executed (top)')

  const statusEl = document.getElementById('sel-status')

  const IDLE_HTML =
    '选区监听运行中…<br /><span style="font-size:11px;color:#909399">在文档中框选 / 三击 / 复制即可捕获</span>'

  // ==================== 状态 ====================
  let selBroadcast = null
  let selRelayWin = null
  let resolvedHostOrigin = ''
  let selInFlight = false
  let selSeq = 0
  let selLast = null
  let selLastLog = 0
  let evRegistered = false

  // ==================== 工具 ====================
  function updateStatus(html) {
    if (statusEl) statusEl.innerHTML = html
  }

  /** 宿主来源解析：优先读宿主注入的 user.id 前缀，其次 referrer 的 parentOrigin，
   *  最后回退本地默认端口。彻底解决 localhost / 127.0.0.1 / 局域网来源错位问题。 */
  function detectHostOrigin() {
    try {
      const uid = String(window.Asc?.plugin?.info?.userId || '')
      const pipe = uid.indexOf('|')
      if (pipe > 0 && /^https?:\/\//.test(uid.slice(0, pipe))) {
        return uid.slice(0, pipe)
      }
    } catch (e) {
      /* ignore */
    }
    try {
      const m = /[?&]parentOrigin=([^&]+)/.exec(document.referrer || '')
      if (m) return decodeURIComponent(m[1])
    } catch (e) {
      /* ignore */
    }
    return 'http://localhost:3000'
  }

  function ensureSdk() {
    // 编辑器 init 消息会调用 Asc.plugin.init（必须定义，否则 SDK 抛 init is not a function）
    if (window.Asc?.plugin && !window.Asc.plugin.init) {
      window.Asc.plugin.init = function () {}
    }
  }

  function updateStatusCapture(raw) {
    const preview = raw.slice(0, 24).replace(/[<>&]/g, ' ')
    updateStatus(
      raw
        ? `已捕获 <b>${raw.length}</b> 字：<br /><span style="font-size:11px;color:#67c23a">${preview}…</span>`

  function ensureEventRegister() {
    if (evRegistered || !window.Asc?.plugin?.event?.register) return
    try {
      window.Asc.plugin.event.register('onEndAction', readSelection)
      evRegistered = true
      console.log('[OO] plugin 已注册 onEndAction 触发')
    } catch (e) {
      console.log('[OO] plugin onEndAction 注册失败（将重试）:', e)
    }
  }
        : IDLE_HTML,
    )
  }

  function broadcast(msg) {
    try {
      if (selBroadcast) selBroadcast.postMessage(msg)
    } catch (e) {
      /* ignore */
    }
    try {
      if (selRelayWin) selRelayWin.postMessage(msg, resolvedHostOrigin)
    } catch (e) {
      /* ignore */
    }
    try {
      parent.postMessage(msg, '*')
    } catch (e) {
      /* ignore */
    }
    try {
      parent.postMessage({ type: 'onExternalPluginMessageCallback', data: msg }, '*')
    } catch (e) {
      /* ignore */
    }
  }

  // ==================== 选区轮询 ====================
  // in-flight 锁 + 序号悬挂自愈：
  //   OO 插件的 executeMethod 一次只能有一个请求在途；响应卡死时叠加调用会永久堵死队列。
  //   每个请求带序号，5s 未回调即解锁放行下一次（自愈重试）。
  function readSelection() {
    if (!window.Asc?.plugin?.executeMethod) return
    if (selInFlight) return
    selInFlight = true
    const seq = ++selSeq
    setTimeout(() => {
      if (selInFlight && selSeq === seq) {
        selInFlight = false
        console.log('[OO] plugin GetSelectedText 悬挂 5s，解锁重试（seq=', seq + '）')
      }
    }, 5000)
    try {
      window.Asc.plugin.executeMethod('GetSelectedText', [], (res) => {
        if (selSeq !== seq) return // 过期响应（悬挂后超时解锁产生的迟到回调）
        selInFlight = false
        const raw = String(res || '').trim()
        const now = Date.now()
        // 每次回调都广播存活 ping（与文本是否变化无关）
        broadcast({ type: 'oo-selection-ping', text: '' })
        if (raw !== selLast) {
          selLast = raw
          selLastLog = now
          updateStatusCapture(raw)
          console.log(
            '[OO] plugin GetSelectedText =>',
            typeof res,
            raw ? `"${raw.slice(0, 40)}…"（${raw.length} 字）` : '(空)',
          )
          broadcast({
            type: 'oo-selection',
            text: raw.length >= 2 ? raw : '',
          })
        } else if (now - selLastLog > 15000) {
          selLastLog = now
          console.log('[OO] plugin GetSelectedText => (空，持续无选区)')
        }
      })
    } catch (e) {
      selInFlight = false
      console.log('[OO] plugin readSelection 异常:', e)
    }
  }

  // ==================== 生命周期 ====================
  let selStarted = false

  function startSelectionBridge() {
    if (selStarted) return
    selStarted = true

    // 动态解析宿主来源并注入同源中继
    resolvedHostOrigin = detectHostOrigin()
    try {
      if (location.origin !== resolvedHostOrigin) {
        const relay = document.createElement('iframe')
        relay.src = `${resolvedHostOrigin}/__oo_relay.html`
        relay.style.cssText = 'position:absolute;width:0;height:0;border:0;visibility:hidden'
        relay.addEventListener('load', () => {
          selRelayWin = relay.contentWindow
          console.log('[OO] plugin 同源中继 iframe 就绪:', resolvedHostOrigin)
          setTimeout(readSelection, 150) // 中继就绪后立即补发一次存活
        })
        document.body.appendChild(relay)
      }
    } catch (e) {
      console.log('[OO] plugin 中继注入失败:', e)
    }
    try {
      selBroadcast = new BroadcastChannel('fastrag-oo-selection')
    } catch (e) {
      /* 广播不可用时由其余通道兜底 */
    }
    console.log('[OO] plugin 选区桥已启动（BroadcastChannel 就绪=', !!selBroadcast, '）')

    // onEndAction：复制/选中等操作完成时触发 → 立即读取。
    // SDK 就绪时序可能让首次注册静默失败——持续重试直至成功。
    setInterval(ensureEventRegister, 2000)
    ensureEventRegister()

    readSelection()
    setInterval(readSelection, 600)

    // 把插件窗压到最小（与清单 size:[220,64] 对齐）
    setTimeout(() => {
      try {
        window.Asc?.plugin?.executeMethod?.('ResizeWindow', [220, 64, 160, 40], (r) => {
          console.log('[OO] plugin ResizeWindow(mini) 回调:', JSON.stringify(r))
        })
        console.log('[OO] plugin ResizeWindow(mini) 已请求')
      } catch (e) {
        console.log('[OO] plugin ResizeWindow 失败（忽略）:', e)
      }
    }, 400)
  }



  // 插件随文档自动启动（autostart）时脚本可能早于 SDK 就绪
  ;(function waitBootstrap() {
    console.log('[OO] plugin script loaded, Asc=', typeof window.Asc, 'plugin=', typeof window.Asc?.plugin)
    if (window.Asc?.plugin?.executeMethod) {
      ensureSdk()
      startSelectionBridge()
    } else {
      setTimeout(waitBootstrap, 500)
    }
  })()

  // 兼容宿主的旧刷新请求
  window.addEventListener('message', (e) => {
    if (e?.data?.type === 'oo-chunk-refresh') {
      readSelection()
    }
  })
})()
