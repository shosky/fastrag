/**
 * OnlyOffice Plugin: ChunkBoundary —— 选区桥（后台型插件，无 UI）
 *
 * <p>职责：在编辑器内部轮询 executeMethod('GetSelectedText')，把用户选中文本
 * 实时广播给宿主页面（FastRAG AiChunkPanel），用于「原件渲染内按段落生成分片」。</p>
 *
 * <p>背景：OO 文档编辑器外层 DocsAPI 既无 onSelectionChanged 事件、也无
 * getSelectedText 方法，宿主无法获取选区；插件运行在编辑器内部，是唯一通道。
 * DS ≥8.3 支持 isVisual:false 后台 autostart —— 无窗口、无法被关闭、持续常驻。</p>
 */
;(function () {
  'use strict'

  // 最先执行点：确认插件脚本是否被 OO 启动
  console.log('[OO] plugin script executed (top)')

  // ==================== 状态 ====================
  let selBroadcast = null
  let selRelayWin = null
  let resolvedHostOrigin = ''
  let selInFlight = false
  let selSeq = 0
  let selLast = null
  let evRegistered = false

  function ensureSdk() {
    // 编辑器 init 消息会调用 Asc.plugin.init（必须定义，否则 SDK 抛 init is not a function）
    if (window.Asc?.plugin && !window.Asc.plugin.init) {
      window.Asc.plugin.init = function () {}
    }
  }

  /** 宿主来源解析：宿主注入的 user.id 前缀 → referrer 的 parentOrigin → 本地兜底 */
  function detectHostOrigin() {
    try {
      const uid = String(window.Asc?.plugin?.info?.userId || '')
      const pipe = uid.indexOf('|')
      if (pipe > 0 && /^https?:\/\//.test(uid.slice(0, pipe))) return uid.slice(0, pipe)
    } catch (e) { /* ignore */ }
    try {
      const m = /[?&]parentOrigin=([^&]+)/.exec(document.referrer || '')
      if (m) return decodeURIComponent(m[1])
    } catch (e) { /* ignore */ }
    return 'http://localhost:3000'
  }

  function broadcast(msg) {
    try { if (selBroadcast) selBroadcast.postMessage(msg) } catch (e) {}
    try { if (selRelayWin) selRelayWin.postMessage(msg, resolvedHostOrigin) } catch (e) {}
    try { parent.postMessage(msg, '*') } catch (e) {}
    try { parent.postMessage({ type: 'onExternalPluginMessageCallback', data: msg }, '*') } catch (e) {}
  }

  // ==================== 选区轮询 ====================
  // in-flight 锁 + 5s 悬挂自愈，防止 executeMethod 卡死堵死队列
  function readSelection() {
    if (!window.Asc?.plugin?.executeMethod) return
    if (selInFlight) return
    selInFlight = true
    const seq = ++selSeq
    setTimeout(() => {
      if (selInFlight && selSeq === seq) {
        selInFlight = false
        console.log('[OO] plugin GetSelectedText 悬挂 5s，解锁重试 seq=' + seq)
      }
    }, 5000)
    try {
      window.Asc.plugin.executeMethod('GetSelectedText', [], (res) => {
        if (selSeq !== seq) return
        selInFlight = false
        const raw = String(res || '').trim()
        // 每次回调都广播存活 ping（与文本变化无关）——回调本身即引擎存活的证据
        broadcast({ type: 'oo-selection-ping', text: '' })
        if (raw !== selLast) {
          selLast = raw
          console.log(
            '[OO] plugin GetSelectedText =>',
            typeof res,
            raw ? '"' + raw.slice(0, 40) + '…"（' + raw.length + ' 字）' : '(空)',
          )
          broadcast({ type: 'oo-selection', text: raw.length >= 2 ? raw : '' })
        }
      })
    } catch (e) {
      selInFlight = false
      console.log('[OO] plugin readSelection 异常:', e)
    }
  }

  // ==================== 事件注册（可重试直至成功） ====================
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

  // ==================== 启动 ====================
  let selStarted = false

  function startSelectionBridge() {
    resolvedHostOrigin = detectHostOrigin()
    // 同源中继：docserver 托管(8082源)与宿主跨源时，
    // 注入「宿主动态来源」隐藏 relay iframe → 同源 BroadcastChannel → 宿主
    try {
      if (location.origin !== resolvedHostOrigin) {
        const relay = document.createElement('iframe')
        relay.src = resolvedHostOrigin + '/__oo_relay.html'
        relay.style.cssText = 'position:absolute;width:0;height:0;border:0;visibility:hidden'
        relay.addEventListener('load', () => {
          selRelayWin = relay.contentWindow
          console.log('[OO] plugin 同源中继 iframe 就绪:', resolvedHostOrigin)
          setTimeout(readSelection, 150)
        })
        document.body.appendChild(relay)
      }
    } catch (e) {
      console.log('[OO] plugin 中继注入失败:', e)
    }
    try {
      selBroadcast = new BroadcastChannel('fastrag-oo-selection')
    } catch (e) { /* 广播不可用时由其余通道兜底 */ }
    console.log('[OO] plugin 选区桥已启动（BroadcastChannel 就绪=', !!selBroadcast, '）')

    setInterval(ensureEventRegister, 2000) // onEndAction 注册可能因就绪时序失败——持续重试
    ensureEventRegister()

    readSelection()
    setInterval(readSelection, 600)

    // 复制/选中等任何操作完成都会触发 onEndAction → 立即读取一次（对应「复制后捕获」）
  }

  ;(function waitBootstrap() {
    console.log('[OO] plugin script loaded, Asc=', typeof window.Asc, 'plugin=', typeof window.Asc?.plugin)
    if (window.Asc?.plugin?.executeMethod) {
      ensureSdk()
      startSelectionBridge()
    } else {
      setTimeout(waitBootstrap, 500)
    }
  })()

  window.addEventListener('message', (e) => {
    if (e?.data?.type === 'oo-chunk-refresh') readSelection()
  })
})()
