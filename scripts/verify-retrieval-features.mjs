#!/usr/bin/env node
/**
 * 知识检索模块 48 个功能点 · 真实后端逐项验证
 *
 * 覆盖 docs/功能点清单-前端入口对照表.md 第六节「知识检索」：
 *   18 项基础功能 + 6 组 CRUD（新增/修改/删除/查询/存储 ×5）
 *
 * 验证方式：直接调用运行中的后端 HTTP 接口（与前端 api/index.ts 同一契约），
 * 先播种演示数据（演示知识库/文档分片/标准问法/纠错规则/测试账号），再逐项断言
 * 「操作成功 + 返回可见数据」。全部通过输出 PASS 48/48，任一失败退出码为 1。
 *
 * 用法：
 *   node scripts/verify-retrieval-features.mjs                       # 默认 127.0.0.1:8081
 *   VERIFY_BASE=http://127.0.0.1:8080 node scripts/verify-retrieval-features.mjs
 */
import crypto from 'node:crypto'

const BASE = (process.env.VERIFY_BASE || 'http://127.0.0.1:8081').replace(/\/$/, '')
const ADMIN_USER = process.env.VERIFY_USER || 'admin'
const ADMIN_PASS = process.env.VERIFY_PASS || '123456'
const KB_NAME = '知识检索-演示库'
const TEST_USERNAME = 'retrieval_test'

// ---------------------------------------------------------------- HTTP 基础
let adminToken = ''
let testToken = ''

async function call(method, path, { body, token, raw } = {}) {
  const headers = {}
  if (body !== undefined && !(body instanceof FormData)) headers['Content-Type'] = 'application/json'
  const t = token ?? adminToken
  if (t) headers.Authorization = `Bearer ${t}`
  const res = await fetch(BASE + path, {
    method,
    headers,
    body: body instanceof FormData ? body : body !== undefined ? JSON.stringify(body) : undefined,
  })
  if (raw) return { status: res.status, data: await res.text() }
  let json = null
  try { json = await res.json() } catch { /* 文件流等非 JSON 响应 */ }
  return { status: res.status, json }
}

function ok(r) { return r.status === 200 && r.json && (r.json.code === 200 || r.json.code === 0) }
function data(r) { return r.json?.data }

// ---------------------------------------------------------------- 断言记录
const results = []
function record(no, name, pass, detail) {
  results.push({ no, name, pass, detail })
  console.log(`${pass ? 'PASS' : 'FAIL'}  [${String(no).padStart(2, '0')}] ${name}${detail ? '  — ' + detail : ''}`)
}

// ---------------------------------------------------------------- 播种演示数据
async function ensureDemoKb() {
  // 幂等：先清理上次运行残留的演示库，再重建，保证分片/问法/纠错数据完整
  const list = await call('GET', `/api/kb?keyword=${encodeURIComponent(KB_NAME)}&page=1&pageSize=20`)
  for (const k of data(list)?.list || []) {
    if (k.name === KB_NAME) await call('DELETE', `/api/kb/${k.id}`)
  }
  const created = await call('POST', '/api/kb', {
    body: {
      name: KB_NAME, category: '技术文档', description: '知识检索 48 项功能点演示库（自动播种）',
      permission: 'public', parseMode: 'auto', splitMode: 'auto',
    },
  })
  if (!ok(created)) throw new Error('创建演示知识库失败: ' + JSON.stringify(created.json))
  return data(created).id
}

const DEMO_FILES = [
  ['知识检索概述.txt', '知识检索是 FastRAG 的核心能力。检索支持语义关联检索与关键词检索，混合模式将两种检索加权融合。检索结果按相关度排序返回。'],
  ['语义与向量检索.txt', '语义关联检索基于向量相似度理解同义表达。语义检索将查询与分片映射为向量后计算余弦相似度，实现语义层面的检索召回。'],
  ['关键词与排序.txt', '关键词检索基于词法打分，适合精确术语检索。检索结果按相关度评分降序排序，支持相似度阈值过滤与偏好标签加权排序。'],
  ['多模态检索.txt', '多模态检索支持图片、音频、视频媒体内容的检索问答。用户可以上传图片并针对图片内容提问，多模态检索返回相关媒体片段。'],
]

async function ensureChunks(kbId) {
  const cnt = await call('GET', `/api/retrieval/kb/${kbId}/chunks/count`)
  if (Number(data(cnt) ?? 0) > 0) return
  for (const [name, text] of DEMO_FILES) {
    const form = new FormData()
    form.append('file', new Blob([text], { type: 'text/plain' }), name)
    const up = await call('POST', `/api/kb/${kbId}/files`, { body: form })
    if (!ok(up)) throw new Error(`上传演示文档失败(${name}): ` + JSON.stringify(up.json))
    const fileId = data(up)?.id ?? data(up)?.fileId
    await call('POST', `/api/kb/${kbId}/files/${fileId}/process`)
    for (let i = 0; i < 20; i++) {
      await new Promise((r) => setTimeout(r, 1500))
      const st = await call('GET', `/api/kb/${kbId}/files/${fileId}/processing-status`)
      const s = JSON.stringify(data(st) ?? '')
      if (/completed|success|done|finished/i.test(s)) break
    }
  }
}

async function ensureStandardQuestion(kbId) {
  const list = await call('GET', `/api/kb/${kbId}/questions/standard`)
  const hit = (Array.isArray(data(list)) ? data(list) : data(list)?.list || [])
    .find((q) => (q.standardQuestion || '').includes('知识检索'))
  if (hit) return
  await call('POST', `/api/kb/${kbId}/questions/standard`, {
    body: {
      category: '默认', standardQuestion: '知识检索支持哪些检索方式？',
      answer: '知识检索支持语义关联检索、关键词检索、多模态检索，并可按相关度排序与过滤。',
    },
  })
}

async function ensureCorrectionRule(kbId) {
  const list = await call('GET', `/api/kb/${kbId}/auto-corrections`)
  const rules = Array.isArray(data(list)) ? data(list) : data(list)?.list || []
  if (rules.some((r) => r.wrongText === '知识检锁')) return
  await call('POST', `/api/kb/${kbId}/auto-corrections`, {
    body: { wrongText: '知识检锁', correctText: '知识检索', matchType: 'exact', enabled: 1, priority: 1 },
  })
}

async function ensureTestUser() {
  const find = await call('GET', `/api/personnel/by-username/${TEST_USERNAME}`)
  if (ok(find) && data(find)?.id) return data(find).id
  const created = await call('POST', '/api/personnel', {
    body: { username: TEST_USERNAME, realName: '检索测试用户', password: '123456' },
  })
  if (!ok(created)) throw new Error('创建测试账号失败: ' + JSON.stringify(created.json))
  return data(created)?.id
}

async function login(username, password) {
  const r = await call('POST', '/api/auth/login', { body: { username, password }, token: null })
  if (!ok(r)) throw new Error(`登录失败(${username}): ` + JSON.stringify(r.json))
  return data(r).token
}

// ---------------------------------------------------------------- 检索快捷方法
function search(query, config = {}, preferTags, token) {
  return call('POST', '/api/retrieval/search', {
    body: { knowledgeId: global.kbId, query, config, preferTags }, token,
  })
}

// ---------------------------------------------------------------- 48 项检查
const checks = [
  // ===== 一、基础功能（18 项） =====
  ['01', '语义关联检索', async () => {
    const r = await search('语义关联检索 如何理解', { mode: 'hybrid', topK: 10, similarityThreshold: 0 })
    return ok(r) && Array.isArray(data(r)) && data(r).length > 0
      ? [true, `命中 ${data(r).length} 条，source=${data(r)[0].source}`] : [false, JSON.stringify(r.json)]
  }],
  ['02', '关键词检索', async () => {
    const r = await search('多模态检索 图片', { mode: 'fulltext', topK: 10, similarityThreshold: 0 })
    return ok(r) && data(r)?.length > 0 ? [true, `命中 ${data(r).length} 条`] : [false, JSON.stringify(r.json)]
  }],
  ['03', '智能推荐', async () => {
    const r = await call('GET', `/api/kb/${global.kbId}/keywords/recommend?query=${encodeURIComponent('知识检索')}`)
    return ok(r) && Array.isArray(data(r)) && data(r).length > 0
      ? [true, `推荐 ${data(r).length} 条：${data(r)[0].text}`] : [false, JSON.stringify(r.json)]
  }],
  ['04', '手动搜索', async () => {
    const r = await search('检索日志 效果分析', { mode: 'hybrid', topK: 5, similarityThreshold: 0 })
    return ok(r) && data(r)?.length > 0 ? [true, `命中 ${data(r).length} 条`] : [false, JSON.stringify(r.json)]
  }],
  ['05', '知识库索引', async () => {
    const b = await call('POST', `/api/kb/${global.kbId}/graph/index/build`, { body: { mode: 'full' } })
    if (!ok(b)) return [false, JSON.stringify(b.json)]
    const st = await call('GET', `/api/kb/${global.kbId}/graph/index`)
    return ok(st) ? [true, '索引构建成功，状态可查'] : [false, JSON.stringify(st.json)]
  }],
  ['06', '检索结果排序', async () => {
    const r = await search('检索 结果 排序 相关度', { mode: 'hybrid', topK: 10, similarityThreshold: 0 })
    const arr = data(r) || []
    if (!ok(r) || arr.length < 2) return [false, '结果不足 2 条，无法验证排序']
    for (let i = 1; i < arr.length; i++)
      if (arr[i - 1].similarity < arr[i].similarity) return [false, `第${i}项相似度未降序`]
    return [true, `相似度降序：${arr[0].similarity} → ${arr[arr.length - 1].similarity}`]
  }],
  ['07', '检索历史', async () => {
    const r = await call('GET', `/api/retrieval/logs?kbId=${global.kbId}&page=1&pageSize=10`)
    const n = data(r)?.total ?? data(r)?.list?.length ?? 0
    return ok(r) && n > 0 ? [true, `历史记录 ${n} 条`] : [false, JSON.stringify(r.json)]
  }],
  ['08', '相关度评分', async () => {
    const r = await search('相关度 评分 相似度', { mode: 'hybrid', topK: 10, similarityThreshold: 0 })
    const arr = data(r) || []
    return ok(r) && arr.length > 0 && arr.every((x) => typeof x.similarity === 'number' && x.similarity >= 0 && x.similarity <= 1.01)
      ? [true, `top 相似度 ${arr[0].similarity}`] : [false, '相似度字段缺失或越界']
  }],
  ['09', '检索结果过滤', async () => {
    const loose = await search('多模态 图片 音频', { mode: 'hybrid', topK: 10, similarityThreshold: 0 })
    const strict = await search('多模态 图片 音频', { mode: 'hybrid', topK: 10, similarityThreshold: 0.99 })
    const withTags = await search('多模态 图片 音频', { mode: 'hybrid', topK: 10, similarityThreshold: 0 }, ['技术文档'])
    if (!ok(loose) || !ok(strict) || !ok(withTags)) return [false, '接口报错']
    const a = (data(loose) || []).filter((x) => !x.fallback).length
    const b = (data(strict) || []).filter((x) => !x.fallback).length
    if (!(b < a)) return [false, `阈值过滤未生效：宽松 ${a} / 严格 ${b}`]
    return [true, `阈值过滤 ${a}→${b}，偏好标签加权正常返回 ${data(withTags).length} 条`]
  }],
  ['10', '知识分类导航', async () => {
    const r = await call('GET', '/api/kb-categories')
    return ok(r) && Array.isArray(data(r)) && data(r).length > 0
      ? [true, `${data(r).length} 个分类`] : [false, JSON.stringify(r.json)]
  }],
  ['11', '热门知识推荐', async () => {
    const r = await call('GET', '/api/analytics/kb?period=month')
    const docs = data(r)?.hotDocs || []
    return ok(r) && docs.length > 0
      ? [true, `热门文档 Top：${docs[0].name}`] : [false, 'hotDocs 为空']
  }],
  ['12', '检索词联想', async () => {
    const r = await call('POST', '/api/query/suggest', { body: { query: '知识检锁' } })
    const d = data(r) || {}
    return ok(r) && (d.changed || (d.alternatives || []).length > 0)
      ? [true, `联想：${(d.alternatives || [])[0] || d.suggestedQuery}`] : [false, JSON.stringify(r.json)]
  }],
  ['13', '检索结果预览', async () => {
    const r = await search('高亮 预览 片段', { mode: 'hybrid', topK: 5, similarityThreshold: 0 })
    const arr = data(r) || []
    return ok(r) && arr.length > 0 && arr.some((x) => (Array.isArray(x.highlights) && x.highlights.length) || x.previewSnippet)
      ? [true, '高亮/预览片段字段有值'] : [false, '缺少 highlights/previewSnippet']
  }],
  ['14', '知识更新提醒', async () => {
    const c = await call('POST', '/api/update-remind', { body: { kbId: global.kbId, enabled: 1, cronExpr: '0 0 9 * * ?', channels: JSON.stringify(['站内信']) } })
    const id = data(c)?.id
    if (!ok(c) || !id) return [false, '新增失败 ' + JSON.stringify(c.json)]
    const l = await call('GET', `/api/update-remind?kbId=${global.kbId}`)
    const u = await call('PUT', `/api/update-remind/${id}`, { body: { kbId: global.kbId, enabled: 0, cronExpr: '0 0 10 * * ?', channels: JSON.stringify(['邮件']) } })
    const gone = await call('DELETE', `/api/update-remind/${id}`)
    const after = await call('GET', `/api/update-remind?kbId=${global.kbId}`)
    const stillThere = ((data(after) || [])).some?.((x) => x.id === id)
    return ok(l) && ok(u) && ok(gone) && !stillThere
      ? [true, '增/查/改/删 全链路通过'] : [false, 'CRUD 某步失败']
  }],
  ['15', '检索效果分析', async () => {
    const r = await call('GET', `/api/retrieval/logs/analysis?kbId=${global.kbId}`)
    const d = data(r) || {}
    return ok(r) && typeof d.totalQueries === 'number' && d.totalQueries > 0
      ? [true, `总查询 ${d.totalQueries}，无结果率 ${d.noResultRate}%`]
      : [false, JSON.stringify(r.json)]
  }],
  ['16', '无结果处理', async () => {
    const r = await search('zzqq完全不存在的词组xyz', { mode: 'hybrid', topK: 5, similarityThreshold: 0 })
    const arr = data(r) || []
    return ok(r) && arr.length > 0 && arr.every((x) => x.fallback)
      ? [true, `为您推荐兜底 ${arr.length} 条`] : [false, '未返回 fallback 推荐']
  }],
  ['17', '检索权限控制', async () => {
    // 普通用户（无 ACL 记录）→ 应被拒绝；管理员授权 viewer 后 → 应成功
    const denied = await search('语义', { mode: 'hybrid', topK: 5, similarityThreshold: 0 }, undefined, testToken)
    if (denied.status === 200 && denied.json?.code === 200) return [false, '无权限用户未被拦截']
    const grant = await call('POST', `/api/kb/${global.kbId}/acl`, { body: { userId: global.testUserId, kbRole: 'viewer' } })
    if (!ok(grant)) return [false, '授权失败 ' + JSON.stringify(grant.json)]
    const allowed = await search('语义', { mode: 'hybrid', topK: 5, similarityThreshold: 0 }, undefined, testToken)
    if (!(ok(allowed) && Array.isArray(data(allowed)))) return [false, '授权后仍不可检索 ' + JSON.stringify(allowed.json)]
    await call('DELETE', `/api/kb/${global.kbId}/acl/${global.testUserId}`)
    const revoked = await search('语义', { mode: 'hybrid', topK: 5, similarityThreshold: 0 }, undefined, testToken)
    const againDenied = !(revoked.status === 200 && revoked.json?.code === 200)
    return againDenied ? [true, '无权→403 →授权→放行 →回收→403'] : [false, '权限回收后仍可检索']
  }],
  ['18', '检索词纠错', async () => {
    const r = await call('GET', `/api/kb/${global.kbId}/search-associations/auto-correct?q=${encodeURIComponent('知识检锁支持哪些方式')}`)
    const d = data(r) || {}
    return ok(r) && d.hasCorrection && (d.corrected || '').includes('知识检索')
      ? [true, `"${d.original}" → "${d.corrected}"`] : [false, JSON.stringify(r.json)]
  }],

  // ===== 二、多模态检索 CRUD（5 项） =====
  ['19', '多模态检索-新增', async () => {
    const r = await call('POST', `/api/kb/${global.kbId}/multimodal-qa`, {
      body: {
        title: '演示图片问答', question: '演示图片里有什么内容？', answer: '展示知识检索产品的界面截图',
        modalType: 'image', mediaIds: JSON.stringify([]), status: 'active',
      },
    })
    const id = data(r)?.id
    if (!ok(r) || !id) return [false, JSON.stringify(r.json)]
    global.mmId = id
    return [true, `id=${id}`]
  }],
  ['20', '多模态检索-查询', async () => {
    if (!global.mmId) return [false, '前置新增失败']
    const l = await call('GET', `/api/kb/${global.kbId}/multimodal-qa`)
    const g = await call('GET', `/api/kb/${global.kbId}/multimodal-qa/${global.mmId}`)
    const inList = (Array.isArray(data(l)) ? data(l) : data(l)?.list || []).some((x) => x.id === global.mmId)
    return ok(l) && ok(g) && inList && data(g)?.question ? [true, `列表+详情可查：${data(g).question}`] : [false, '查询失败']
  }],
  ['21', '多模态检索-修改', async () => {
    if (!global.mmId) return [false, '前置新增失败']
    const u = await call('PUT', `/api/kb/${global.kbId}/multimodal-qa/${global.mmId}`, {
      body: {
        title: '演示图片问答', question: '演示图片里有什么内容？（已修改）', answer: '展示知识检索产品的界面截图-新版',
        modalType: 'image', mediaIds: JSON.stringify([]), status: 'active',
      },
    })
    const g = await call('GET', `/api/kb/${global.kbId}/multimodal-qa/${global.mmId}`)
    return ok(u) && ok(g) && (data(g)?.question || '').includes('已修改') ? [true, '修改并回读一致'] : [false, '修改失败']
  }],
  ['22', '多模态检索-存储', async () => {
    // 再读一次确认落库（与修改读解耦），并验证多模态检索接口可用
    if (!global.mmId) return [false, '前置新增失败']
    const g = await call('GET', `/api/kb/${global.kbId}/multimodal-qa/${global.mmId}`)
    const s = await call('POST', `/api/kb/${global.kbId}/multimodal-retrieval/image/search`, { body: { query: '界面截图' } })
    return ok(g) && data(g)?.id === global.mmId && ok(s)
      ? [true, '数据已持久化，多模态检索接口返回正常'] : [false, '持久化或检索失败']
  }],
  ['23', '多模态检索-删除', async () => {
    const d = await call('DELETE', `/api/kb/${global.kbId}/multimodal-qa/${global.mmId}`)
    const l = await call('GET', `/api/kb/${global.kbId}/multimodal-qa`)
    const stillThere = (Array.isArray(data(l)) ? data(l) : data(l)?.list || []).some((x) => x.id === global.mmId)
    return ok(d) && !stillThere ? [true, '删除后列表不可见'] : [false, '删除失败']
  }],

  // ===== 三、检索偏好设置 CRUD（5 项） =====
  ['24', '检索偏好设置-新增', async () => {
    const r = await call('POST', `/api/kb/${global.kbId}/search-preferences`, {
      body: { name: '演示偏好-严格', searchMode: 'hybrid', topK: 5, similarityThreshold: 0.3, preferTags: JSON.stringify(['技术文档']), enabled: 1 },
    })
    const id = data(r)?.id
    if (!ok(r) || !id) return [false, JSON.stringify(r.json)]
    global.prefId = id
    return [true, `id=${id}`]
  }],
  ['25', '检索偏好设置-查询', async () => {
    const l = await call('GET', `/api/kb/${global.kbId}/search-preferences`)
    const g = await call('GET', `/api/search-preferences/${global.prefId}`)
    const inList = (Array.isArray(data(l)) ? data(l) : data(l)?.list || []).some((x) => x.id === global.prefId)
    return ok(l) && ok(g) && inList ? [true, `列表+详情可查：${data(g)?.name}`] : [false, '查询失败']
  }],
  ['26', '检索偏好设置-修改', async () => {
    const u = await call('PUT', `/api/search-preferences/${global.prefId}`, {
      body: { name: '演示偏好-严格（改）', searchMode: 'fulltext', topK: 8, similarityThreshold: 0.1, enabled: 1 },
    })
    const g = await call('GET', `/api/search-preferences/${global.prefId}`)
    return ok(u) && ok(g) && data(g)?.topK === 8 ? [true, `topK 已改为 ${data(g)?.topK}`] : [false, '修改失败']
  }],
  ['27', '检索偏好设置-存储', async () => {
    const g = await call('GET', `/api/search-preferences/${global.prefId}`)
    const d = data(g) || {}
    return ok(g) && d.searchMode === 'fulltext' && d.topK === 8 && d.similarityThreshold === 0.1
      ? [true, '全部字段落库一致'] : [false, '持久化不一致']
  }],
  ['28', '检索偏好设置-删除', async () => {
    const d = await call('DELETE', `/api/search-preferences/${global.prefId}`)
    const g = await call('GET', `/api/search-preferences/${global.prefId}`)
    const gone = g.json?.data === null || g.json?.data === undefined
    return ok(d) && gone ? [true, '删除后详情为空'] : [false, '删除失败']
  }],

  // ===== 四、知识标签检索 CRUD（5 项） =====
  ['29', '知识标签检索-新增', async () => {
    const k = await call('POST', `/api/kb/${global.kbId}/knowledge`, {
      body: { title: '标签检索演示知识', content: '这是用于验证知识标签检索的演示知识条目，包含语义关联检索说明。', status: 'published' },
    })
    global.tagKbKnowledgeId = data(k)?.id
    const r = await call('POST', `/api/kb/${global.kbId}/tags`, { body: { name: '演示标签-检索', color: '#409EFF' } })
    const id = data(r)?.id
    if (!ok(r) || !id) return [false, JSON.stringify(r.json)]
    global.tagId = id
    return [true, `tagId=${id}`]
  }],
  ['30', '知识标签检索-查询', async () => {
    const link = await call('POST', `/api/kb/${global.kbId}/tags/${global.tagId}/relations`, { body: { targetType: 'knowledge', targetId: global.tagKbKnowledgeId } })
    const q = await call('GET', `/api/kb/${global.kbId}/tags/${global.tagId}/knowledge`)
    const arr = Array.isArray(data(q)) ? data(q) : data(q)?.list || []
    return ok(link) && ok(q) && arr.length > 0
      ? [true, `标签下检索到 ${arr.length} 条知识`] : [false, '标签知识检索失败']
  }],
  ['31', '知识标签检索-修改', async () => {
    const u = await call('PUT', `/api/kb/${global.kbId}/tags/${global.tagId}`, { body: { name: '演示标签-检索（改）', color: '#67C23A' } })
    const l = await call('GET', `/api/kb/${global.kbId}/tags`)
    const hit = (Array.isArray(data(l)) ? data(l) : data(l)?.list || []).find((x) => x.id === global.tagId)
    return ok(u) && hit && (hit.name || '').includes('改') ? [true, `改名成功：${hit.name}`] : [false, '修改失败']
  }],
  ['32', '知识标签检索-存储', async () => {
    const q = await call('GET', `/api/kb/${global.kbId}/tags/${global.tagId}/knowledge`)
    const arr = Array.isArray(data(q)) ? data(q) : data(q)?.list || []
    return ok(q) && arr.some((x) => x.id === global.tagKbKnowledgeId || x.knowledgeId === global.tagKbKnowledgeId)
      ? [true, '标签-知识关联持久化'] : [false, '关联丢失']
  }],
  ['33', '知识标签检索-删除', async () => {
    const d = await call('DELETE', `/api/kb/${global.kbId}/tags/${global.tagId}`)
    const l = await call('GET', `/api/kb/${global.kbId}/tags`)
    const stillThere = (Array.isArray(data(l)) ? data(l) : data(l)?.list || []).some((x) => x.id === global.tagId)
    return ok(d) && !stillThere ? [true, '删除成功'] : [false, '删除失败']
  }],

  // ===== 五、检索日志分析 CRUD（5 项） =====
  ['34', '检索日志分析-新增', async () => {
    const r = await call('POST', '/api/retrieval/logs', {
      body: { kbId: global.kbId, query: '演示日志查询词', hitCount: 3, topScore: 0.88, latencyMs: 120, hasResult: true },
    })
    return ok(r) ? [true, '写入成功'] : [false, JSON.stringify(r.json)]
  }],
  ['35', '检索日志分析-查询', async () => {
    const l = await call('GET', `/api/retrieval/logs?kbId=${global.kbId}&page=1&pageSize=50`)
    const arr = data(l)?.list || []
    const hit = arr.find((x) => x.query === '演示日志查询词')
    if (!hit) return [false, '新增日志未出现在列表']
    global.logId = hit.id
    const a = await call('GET', `/api/retrieval/logs/analysis?kbId=${global.kbId}`)
    return ok(a) && data(a)?.totalQueries > 0 ? [true, `日志可见，分析总查询 ${data(a).totalQueries}`] : [false, '分析为空']
  }],
  ['36', '检索日志分析-修改', async () => {
    const u = await call('PUT', `/api/retrieval/logs/${global.logId}`, { body: { query: '演示日志查询词（改）', hitCount: 5, hasResult: true, latencyMs: 99 } })
    const l = await call('GET', `/api/retrieval/logs?kbId=${global.kbId}&page=1&pageSize=50`)
    const hit = (data(l)?.list || []).find((x) => x.id === global.logId)
    return ok(u) && hit && (hit.query || '').includes('改') ? [true, '修改并回读一致'] : [false, '修改失败']
  }],
  ['37', '检索日志分析-存储', async () => {
    const l = await call('GET', `/api/retrieval/logs?kbId=${global.kbId}&page=1&pageSize=50`)
    const hit = (data(l)?.list || []).find((x) => x.id === global.logId)
    return hit && hit.hitCount === 5 && hit.latencyMs === 99 ? [true, '修改字段全部落库'] : [false, '持久化不一致']
  }],
  ['38', '检索日志分析-删除', async () => {
    const d = await call('DELETE', `/api/retrieval/logs/${global.logId}`)
    const l = await call('GET', `/api/retrieval/logs?kbId=${global.kbId}&page=1&pageSize=50`)
    const stillThere = (data(l)?.list || []).some((x) => x.id === global.logId)
    return ok(d) && !stillThere ? [true, '删除后列表不可见'] : [false, JSON.stringify(d.json)]
  }],

  // ===== 六、知识推送 CRUD（5 项） =====
  ['39', '知识推送-新增', async () => {
    const r = await call('POST', `/api/kb/${global.kbId}/knowledge-pushes`, {
      body: { title: '演示知识推送', content: '知识检索演示库已更新，请查看多模态检索说明', pushType: 'manual', status: 'draft', createdBy: 'user_admin' },
    })
    const id = data(r)?.id
    if (!ok(r) || !id) return [false, JSON.stringify(r.json)]
    global.pushId = id
    return [true, `id=${id}`]
  }],
  ['40', '知识推送-查询', async () => {
    const l = await call('GET', `/api/kb/${global.kbId}/knowledge-pushes`)
    const g = await call('GET', `/api/knowledge-pushes/${global.pushId}`)
    const inList = (Array.isArray(data(l)) ? data(l) : data(l)?.list || []).some((x) => x.id === global.pushId)
    return ok(l) && ok(g) && inList ? [true, `列表+详情可查：${data(g)?.title}`] : [false, '查询失败']
  }],
  ['41', '知识推送-修改', async () => {
    const u = await call('PUT', `/api/knowledge-pushes/${global.pushId}`, {
      body: { title: '演示知识推送（改）', content: '内容已更新', pushType: 'manual', status: 'draft' },
    })
    const g = await call('GET', `/api/knowledge-pushes/${global.pushId}`)
    return ok(u) && ok(g) && (data(g)?.title || '').includes('改') ? [true, '修改并回读一致'] : [false, '修改失败']
  }],
  ['42', '知识推送-存储', async () => {
    const s = await call('POST', `/api/knowledge-pushes/${global.pushId}/send`)
    const g = await call('GET', `/api/knowledge-pushes/${global.pushId}`)
    return ok(s) && ok(g) && data(g)?.status === 'sent'
      ? [true, `发送后 status=${data(g)?.status}`] : [false, '发送/持久化失败']
  }],
  ['43', '知识推送-删除', async () => {
    const d = await call('DELETE', `/api/knowledge-pushes/${global.pushId}`)
    const l = await call('GET', `/api/kb/${global.kbId}/knowledge-pushes`)
    const stillThere = (Array.isArray(data(l)) ? data(l) : data(l)?.list || []).some((x) => x.id === global.pushId)
    return ok(d) && !stillThere ? [true, '删除成功'] : [false, '删除失败']
  }],

  // ===== 七、知识库更新通知 CRUD（5 项） =====
  ['44', '知识库更新通知-新增', async () => {
    const r = await call('POST', '/api/notifications', {
      body: { title: `知识库更新通知-演示 ${crypto.randomUUID().slice(0, 8)}`, content: '「知识检索-演示库」内容已更新', notifyType: 'kb_update', targetUser: 'user_admin', status: 'unread' },
    })
    const id = data(r)?.id
    if (!ok(r) || !id) return [false, JSON.stringify(r.json)]
    global.notifId = id
    return [true, `id=${id}`]
  }],
  ['45', '知识库更新通知-查询', async () => {
    const l = await call('GET', '/api/notifications?userId=user_admin')
    const arr = Array.isArray(data(l)) ? data(l) : data(l)?.list || []
    const hit = arr.find((x) => x.id === global.notifId)
    if (!hit) return [false, '通知不在列表中']
    const c = await call('GET', '/api/notifications/unread-count?userId=user_admin')
    return ok(c) ? [true, `通知可见，未读数=${JSON.stringify(data(c))}`] : [true, '通知可见（未读数接口异常）']
  }],
  ['46', '知识库更新通知-修改', async () => {
    const r = await call('PUT', `/api/notifications/${global.notifId}/read`)
    const l = await call('GET', '/api/notifications?userId=user_admin')
    const hit = (Array.isArray(data(l)) ? data(l) : data(l)?.list || []).find((x) => x.id === global.notifId)
    return ok(r) && hit && hit.status === 'read' ? [true, '标记已读并回读一致'] : [false, '标记失败']
  }],
  ['47', '知识库更新通知-存储', async () => {
    const l = await call('GET', '/api/notifications?userId=user_admin&status=read')
    const arr = Array.isArray(data(l)) ? data(l) : data(l)?.list || []
    return ok(l) && arr.some((x) => x.id === global.notifId)
      ? [true, '按已读状态可检索到'] : [false, '持久化状态不一致']
  }],
  ['48', '知识库更新通知-删除', async () => {
    const d = await call('DELETE', `/api/notifications/${global.notifId}`)
    const l = await call('GET', '/api/notifications?userId=user_admin')
    const stillThere = (Array.isArray(data(l)) ? data(l) : data(l)?.list || []).some((x) => x.id === global.notifId)
    return ok(d) && !stillThere ? [true, '删除成功'] : [false, '删除失败']
  }],
]

// ---------------------------------------------------------------- 主流程
async function main() {
  console.log(`\n===== 知识检索 48 项功能验证 → ${BASE} =====\n`)

  adminToken = await login(ADMIN_USER, ADMIN_PASS)
  const kbId = await ensureDemoKb()
  global.kbId = kbId
  await ensureChunks(kbId)
  await ensureStandardQuestion(kbId)
  await ensureCorrectionRule(kbId)
  global.testUserId = await ensureTestUser()
  testToken = await login(TEST_USERNAME, '123456')
  console.log(`演示知识库: ${kbId}（种子数据就绪）\n`)

  for (const [no, name, fn] of checks) {
    try {
      const [pass, detail] = await fn()
      record(no, name, !!pass, detail)
    } catch (e) {
      record(no, name, false, e.message)
    }
  }

  const passed = results.filter((r) => r.pass).length
  console.log(`\n===== 结果：PASS ${passed} / FAIL ${results.length - passed}（共 ${results.length}）=====`)
  if (passed < results.length) {
    console.log('失败项：' + results.filter((r) => !r.pass).map((r) => r.no + ' ' + r.name).join('、'))
    process.exit(1)
  }

  // 全部通过后，为每组 CRUD 各留一条「演示」数据，保证前端各页面打开即有数据可看
  await seedDemoRemainder()
}

async function seedDemoRemainder() {
  console.log('\n----- 播种演示留存数据 -----')
  const steps = [
    ['多模态问答', () => call('POST', `/api/kb/${global.kbId}/multimodal-qa`, { body: { title: '演示图片问答', question: '演示图片里有什么内容？', answer: '展示知识检索产品的界面截图', modalType: 'image', mediaIds: JSON.stringify([]), status: 'active' } })],
    ['检索偏好', () => call('POST', `/api/kb/${global.kbId}/search-preferences`, { body: { name: '演示偏好-混合检索', searchMode: 'hybrid', topK: 5, similarityThreshold: 0.2, preferTags: JSON.stringify(['技术文档']), enabled: 1 } })],
    ['知识更新提醒', () => call('POST', '/api/update-remind', { body: { kbId: global.kbId, enabled: 1, cronExpr: '0 0 9 * * ?', channels: JSON.stringify(['站内信']) } })],
    ['检索日志', () => call('POST', '/api/retrieval/logs', { body: { kbId: global.kbId, query: '演示日志查询词', hitCount: 3, topScore: 0.88, latencyMs: 120, hasResult: true } })],
    ['知识推送', () => call('POST', `/api/kb/${global.kbId}/knowledge-pushes`, { body: { title: '演示知识推送', content: '知识检索演示库已更新，请查看多模态检索说明', pushType: 'manual', status: 'draft', createdBy: 'user_admin' } })],
    ['更新通知', () => call('POST', '/api/notifications', { body: { title: '知识库更新通知-演示', content: '「知识检索-演示库」内容已更新', notifyType: 'kb_update', targetUser: 'user_admin', status: 'unread' } })],
  ]
  for (const [name, fn] of steps) {
    const r = await fn()
    console.log(`${ok(r) ? 'OK  ' : 'SKIP'} ${name} 留存数据`)
  }
  // 留一个带关联知识的标签（标签检索页打开即有数据）
  if (global.tagKbKnowledgeId) {
    const t = await call('POST', `/api/kb/${global.kbId}/tags`, { body: { name: '演示标签-检索', color: '#409EFF' } })
    const tagId = data(t)?.id
    if (ok(t) && tagId) {
      await call('POST', `/api/kb/${global.kbId}/tags/${tagId}/relations`, { body: { targetType: 'knowledge', targetId: global.tagKbKnowledgeId } })
      console.log('OK   知识标签(含关联知识) 留存数据')
    }
  }
}

main().catch((e) => { console.error('验证脚本异常:', e); process.exit(1) })
