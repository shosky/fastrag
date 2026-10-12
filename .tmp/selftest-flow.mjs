// qa-generation 端到端自测：登录 → 选文件 → 触发 → 轮询 → 草稿落库 → 确认前不召回 → 确认后召回
const BASE = 'http://127.0.0.1:8083'

const j = async (method, path, body, token) => {
  const res = await fetch(BASE + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  })
  const text = await res.text()
  let json = null
  try { json = JSON.parse(text) } catch {}
  return { http: res.status, json, text }
}
const unwrap = (r) => r.json?.data ?? r.json

// 1 登录
const login = await j('POST', '/api/auth/login', { username: 'admin', password: 'admin123' })
const token = unwrap(login)?.token
if (!token) { console.log('[1] 登录失败', JSON.stringify(login).slice(0, 200)); process.exit(1) }
console.log('[1] 登录 OK')

// 2 知识库列表
const kbs = unwrap(await j('GET', '/api/kb', null, token))
const kbList = kbs?.list || kbs || []
console.log('[2] 知识库数:', Array.isArray(kbList) ? kbList.length : '未知结构', JSON.stringify(kbList).slice(0, 120))
if (!Array.isArray(kbList) || kbList.length === 0) process.exit(1)

// 3 找已完成文件（命令行指定 file_id kb_id 时直取；否则全库扫描）
const argFile = process.argv[2]
const argKb = process.argv[3]
let candidates = []
if (argFile && argKb) {
  candidates = [{ kbId: argKb, kbName: '指定库', file: { id: argFile, name: '指定文件 ' + argFile } }]
} else {
  for (const kb of kbList) {
    const files = unwrap(await j('GET', `/api/kb/${kb.id}/files`, null, token))
    const list = files?.list || files || []
    if (!Array.isArray(list)) continue
    for (const f of list) if (f.status === 'completed') candidates.push({ kbId: kb.id, kbName: kb.name, file: f })
  }
}
console.log('[3] 候选已完成文件:', candidates.length, '个')
if (candidates.length === 0) process.exit(1)

// 4 逐个尝试触发，直到找到有父分片（totalChunks>0）的文件
let trg = null
let target = null
for (const c of candidates.slice(0, 8)) {
  const r = unwrap(await j('POST', `/api/kb/${c.kbId}/qa-pairs/generation/${c.file.id}`, { count: 3, mode: 'replace' }, token))
  console.log('    尝试', c.file.name, '→ totalChunks=' + (r?.totalChunks ?? JSON.stringify(r)?.slice(0, 80)))
  if (r?.status === 'running' && r.totalChunks > 0) { trg = r; target = c; break }
}
if (!trg) { console.log('[4] 所有候选文件均无父分片（老版本摄入产物），自测中止'); process.exit(1) }
console.log('[4] 触发受理: status=running, totalChunks=' + trg.totalChunks + ', config=' + JSON.stringify(trg.config))

// 5 轮询终态
let final = null
for (let i = 0; i < 40; i++) {
  await new Promise((r) => setTimeout(r, 2000))
  const st = unwrap(await j('GET', `/api/kb/${target.kbId}/qa-pairs/generation/${target.file.id}`, null, token))
  if (['success', 'partial', 'failed'].includes(st?.status)) { final = st; break }
  if (i % 3 === 0) process.stdout.write(`    轮询#${i + 1} status=${st?.status} ${st?.succeededChunks}/${st?.totalChunks}\n`)
}
console.log('[5] 终态:', JSON.stringify(final))
if (!final || final.status === 'failed') process.exit(1)

// 6 该文件 QA 对清单
const qa = unwrap(await j('GET', `/api/kb/${target.kbId}/qa-pairs?fileId=${target.file.id}`, null, token))
const qaList = qa?.list || qa || []
const aiPairs = (Array.isArray(qaList) ? qaList : []).filter((p) => p.source === 'ai')
console.log('[6] 该文件 QA 对共', (Array.isArray(qaList) ? qaList.length : 0), '条；ai 来源', aiPairs.length,
  '条（draft:', aiPairs.filter((p) => p.status === 'draft').length, '/ confirmed:', aiPairs.filter((p) => p.status === 'confirmed').length, '）')
if (aiPairs.length > 0) console.log('    样例:', JSON.stringify(aiPairs[0]).slice(0, 180))

// 7 确认前：draft 不应出现在 qa 通道
const kw = aiPairs[0]?.question?.slice(0, 6)
if (kw) {
  const s1 = unwrap(await j('POST', '/api/retrieval/search', { knowledgeId: target.kbId, query: kw, config: { enableKeywordMatch: true, qaRecallCount: 5 } }, token))
  const items1 = s1?.results || s1?.items || (Array.isArray(s1) ? s1 : [])
  console.log('[7] 确认前检索「' + kw + '」→ qa 通道命中', items1.filter((i) => i.source === 'qa').length, '条（期望 0）')

  // 8 确认后：应召回
  await j('POST', `/api/kb/${target.kbId}/qa-pairs/${aiPairs[0].id}/confirm`, null, token)
  const s2 = unwrap(await j('POST', '/api/retrieval/search', { knowledgeId: target.kbId, query: kw, config: { enableKeywordMatch: true, qaRecallCount: 5 } }, token))
  const items2 = s2?.results || s2?.items || (Array.isArray(s2) ? s2 : [])
  console.log('[8] 确认后检索同词 → qa 通道命中', items2.filter((i) => i.source === 'qa').length, '条（期望 ≥1）')
} else {
  console.log('[7/8] 无 ai 产出可验证召回（partial 且全被闸门拦截？）')
}
console.log('SELFTEST_DONE')
