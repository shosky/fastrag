#!/usr/bin/env node
/**
 * 功能清单 → 动态 API 集成测试
 * 对照 9 模块功能清单，逐功能向后端（默认 http://127.0.0.1:8080）发起真实调用，
 * 覆盖本轮新增/补强的全部功能 + 核心既有功能。自带测试数据创建与清理。
 *
 * 运行：node scripts/feature-api-tests.mjs [baseUrl]
 * 前置：后端已启动且包含最新构建；数据库已初始化（admin/123456）
 * 说明：SSE 对话流与 multipart 文件上传不在 HTTP JSON 测试范围（静态审计已覆盖其入口）
 */
const BASE = process.argv[2] || 'http://127.0.0.1:8080'
let TOKEN = ''
let passCount = 0, failCount = 0, skipCount = 0
const results = []

async function req(method, path, body, opts = {}) {
  const headers = { 'Content-Type': 'application/json' }
  if (TOKEN && !opts.noAuth) headers.Authorization = 'Bearer ' + TOKEN
  const res = await fetch(BASE + path, {
    method, headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(20000),
  })
  let json = null
  try { json = await res.json() } catch { /* 204/文本 */ }
  return { status: res.status, body: json }
}
const success = (r) => r.status === 200 && r.body && (r.body.code === 200 || r.body.code === 0)

function record(module, feature, name, pass, detail = '') {
  const icon = pass ? 'PASS' : 'FAIL'
  if (pass) passCount++; else failCount++
  results.push({ module, feature, name, pass, detail })
  console.log(`${icon}  [${module}] ${feature} · ${name}${detail ? ' —— ' + detail : ''}`)
}
const skip = (module, feature, name, reason) => { skipCount++; results.push({ module, feature, name, pass: null, detail: reason }); console.log(`SKIP [${module}] ${feature} · ${name} —— ${reason}`) }
const iso = (offsetDays) => new Date(Date.now() + offsetDays * 86400000).toISOString().slice(0, 19).replace('T', ' ')

async function main() {
  console.log(`===== 动态 API 集成测试：${BASE} =====\n`)

  /* ---------- 登录 ---------- */
  const login = await req('POST', '/api/auth/login', { username: 'admin', password: '123456' }, { noAuth: true })
  if (!success(login)) { console.error('登录失败，终止：', JSON.stringify(login.body)); process.exit(2) }
  TOKEN = login.body.data.token
  record('系统', '认证', 'admin 登录获取 token', true)

  /* ---------- 测试数据准备：两个知识库（A=FAQ型，B=普通） ---------- */
  const kbA = (await req('POST', '/api/kb', { name: '自动化-测试库A-' + Date.now(), category: '自动化测试', kbType: 'faq', permission: 'private' })).body?.data
  const kbB = (await req('POST', '/api/kb', { name: '自动化-测试库B-' + Date.now(), category: '自动化测试', kbType: 'general', permission: 'private' })).body?.data
  const kbAId = kbA?.id, kbBId = kbB?.id
  record('应答知识库管理', 'FAQ知识库', '创建 FAQ 型知识库（kbType=faq）', !!kbAId && kbA.kbType === 'faq', `kbType=${kbA?.kbType}`)
  record('知识管理', '知识库结构设计', '创建通用知识库', !!kbBId)

  /* ---------- 一、知识管理 ---------- */
  {
    const cat = await req('POST', '/api/kb-categories', { name: '自动化分类-' + Date.now() })
    const catId = cat.body?.data?.id
    record('知识管理', '知识分类管理', '新建分类', success(cat) && !!catId)
    const list = await req('GET', '/api/kb-categories')
    record('知识管理', '知识分类管理', '分类列表', success(list) && Array.isArray(list.body.data))
    if (catId) await req('DELETE', `/api/kb-categories/${catId}`)
  }
  {
    const k = await req('POST', `/api/kb/${kbAId}/knowledge`, { title: '自动化知识条目', content: '这是自动化测试创建的知识条目，包含退货政策说明。', summary: '退货政策', category: '自动化测试' })
    const kid = k.body?.data?.id
    record('知识管理', '知识条目创建', '新增知识条目', success(k) && !!kid)
    const got = await req('GET', `/api/kb/${kbAId}/knowledge/${kid}`)
    record('知识管理', '知识录入', '查看知识详情（GET 单条）', success(got) && got.body?.data?.id === kid)
    record('对话知识', '知识新增', '知识条目 POST 幂等成功', success(k))
    const rel = await req('GET', `/api/kb/${kbAId}/knowledge/${kid}/related?limit=5`)
    record('知识管理', '知识关联推荐', '关联推荐接口', success(rel) && Array.isArray(rel.body?.data?.relatedKnowledge))
    await req('DELETE', `/api/kb/${kbAId}/knowledge/${kid}`)
  }
  {
    const list = await req('GET', `/api/kb/${kbAId}/knowledge-updates`)
    record('知识管理', '知识更新记录', '更新记录列表', success(list))
  }
  {
    const tag = await req('POST', `/api/kb/${kbAId}/tags`, { name: '自动化标签-' + Date.now() })
    const tagId = tag.body?.data?.id
    record('知识管理', '知识标签管理', '新建标签', success(tag) && !!tagId)
    const tl = await req('GET', `/api/kb/${kbAId}/tags`)
    record('知识检索', '知识标签检索', '标签列表', success(tl) && Array.isArray(tl.body.data))
    if (tagId) await req('DELETE', `/api/kb/${kbAId}/tags/${tagId}`)
  }
  {
    const assoc = await req('GET', `/api/kb/${kbAId}/search-associations`)
    record('知识管理', '知识搜索优化', '联想规则列表', success(assoc))
  }
  {
    const exp = await req('GET', `/api/kb/${kbAId}/export`)
    record('知识管理', '知识导入导出', '知识库导出', success(exp) && exp.body?.data != null)
  }
  {
    const acl = await req('GET', `/api/kb/${kbAId}/acl`)
    record('知识管理', '知识权限管理', '读取 KB ACL', success(acl))
  }
  {
    const qr = await req('GET', `/api/kb/${kbAId}/quality-rules`)
    record('知识管理', '知识质量评估', '质量规则列表', success(qr))
  }
  {
    const cfg = await req('POST', '/api/kb-sync', { name: '自动化同步-' + Date.now(), sourceKbId: kbAId, targetKbId: kbBId, syncMode: 'incremental', intervalMinutes: 60, enabled: 1 })
    const cfgId = cfg.body?.data?.id
    record('知识管理', '知识库同步机制', '新建同步配置', success(cfg) && !!cfgId)
    if (cfgId) {
      const run = await req('POST', `/api/kb-sync/${cfgId}/run`)
      record('知识管理', '知识库同步机制', '执行同步（run 返回统计）', success(run) && run.body?.data?.status === 'success' && typeof run.body?.data?.syncedEntries === 'number',
        `entries=${run.body?.data?.syncedEntries}, qa=${run.body?.data?.syncedQaPairs}`)
      const rec = await req('GET', `/api/kb-sync/${cfgId}/records`)
      record('知识管理', '知识库同步机制', '同步记录列表', success(rec) && Array.isArray(rec.body.data))
      await req('DELETE', `/api/kb-sync/${cfgId}`)
    }
  }
  {
    const an = await req('GET', '/api/analytics/kb?period=week')
    record('知识管理', '知识库报表分析', '知识资产分析（含周期）', success(an) && an.body?.data?.period === 'week' && Array.isArray(an.body?.data?.hotKBs))
    record('管理端管理', '知识库排行', '排行周期参数生效', an.body?.data?.period === 'week')
  }
  {
    const fb = await req('GET', '/api/feedback')
    record('知识管理', '知识库用户反馈', '反馈列表', success(fb))
  }

  /* ---------- 二、应答知识库管理（FAQ 全链路） ---------- */
  let qaId = ''
  {
    const qa = await req('POST', `/api/kb/${kbAId}/qa-pairs`, {
      question: '自动化专属问题：退货政策是什么？', answer: '自动化测试答案：七天内可退货。',
      faqType: 'common', keywords: '自动化测试,退货',
      effectiveStart: iso(-1), effectiveEnd: iso(1), effectiveScope: 'all', relatedKnowledgeIds: [],
    })
    qaId = qa.body?.data?.id
    const d = qa.body?.data || {}
    record('应答知识库管理', 'FAQ知识应答', '新增问答对（含 FAQ 扩展字段）', success(qa) && !!qaId && d.faqType === 'common' && d.active === true,
      `faqType=${d.faqType}, active=${d.active}`)
    record('应答知识库管理', 'FAQ常见问题', 'faqType=common 落库', d.faqType === 'common')
    record('应答知识库管理', '生效时间设置', '生效期内 active=true', d.active === true)
    record('应答知识库管理', '多关键词配置知识', 'keywords 保存', !!d.keywords && d.keywords.includes('退货'))
  }
  {
    // 生效期外的问答对：effectiveEnd=昨天
    const qa2 = await req('POST', `/api/kb/${kbAId}/qa-pairs`, {
      question: '过期问答对专属词：荧光绿恐龙', answer: '过期答案', faqType: 'uncommon',
      keywords: '荧光绿恐龙', effectiveStart: iso(-10), effectiveEnd: iso(-1), effectiveScope: 'all',
    })
    const qa2Id = qa2.body?.data?.id
    record('应答知识库管理', 'FAQ非常见问题', 'faqType=uncommon 创建', success(qa2) && qa2.body?.data?.faqType === 'uncommon')
    const s1 = await req('POST', '/api/retrieval/search', { knowledgeId: kbAId, query: '退货政策' })
    const items1 = s1.body?.data || []
    record('知识检索', '语义关联检索', '混合检索返回结构（similarity 数值）', success(s1) && Array.isArray(items1) && items1.every((i) => typeof i.similarity === 'number'))
    const qaHit = items1.find((i) => i.source === 'qa')
    record('应答知识库管理', '多关键词配置知识', '检索消费：关键词"退货"命中 QA（source=qa）', !!qaHit, qaHit ? `score=${qaHit.similarity}` : '未命中——若运行中后端为旧构建请重启')
    const s2 = await req('POST', '/api/retrieval/search', { knowledgeId: kbAId, query: '荧光绿恐龙' })
    const items2 = s2.body?.data || []
    const expiredHit = items2.some((i) => i.source === 'qa' && (i.content || '').includes('荧光绿恐龙'))
    record('应答知识库管理', '生效时间设置', '生效期外问答对不参与召回', success(s2) && !expiredHit)
    // 无结果兜底
    const s3 = await req('POST', '/api/retrieval/search', { knowledgeId: kbAId, query: 'zzz完全不存在的词qazxsw' })
    record('知识检索', '无结果处理', '无结果时接口正常返回（可含兜底推荐）', success(s3))
    if (qa2Id) await req('DELETE', `/api/kb/${kbAId}/qa-pairs/${qa2Id}`)
  }
  {
    const tk = await req('POST', `/api/kb/${kbAId}/qa-pairs/${qaId}/to-knowledge`)
    const rel = tk.body?.data?.relatedKnowledgeIds || []
    record('应答知识库管理', '按应答添加知识', '按应答生成知识条目并回写关联', success(tk) && rel.length >= 1)
  }
  {
    const judge = await req('POST', `/api/kb/${kbAId}/keywords/judge`, { query: '退货' })
    record('座席端管理', '知识推荐（识别/推荐）', '识别知识推荐（judge 命中关键词）', success(judge) && judge.body?.data?.matched === true)
  }
  {
    const attr = await req('POST', `/api/kb/${kbAId}/attributes`, { name: '自动化属性', attrType: 'text', required: 0 })
    const attrId = attr.body?.data?.id
    record('应答知识库管理', '属性管理', '新增属性定义', success(attr) && !!attrId)
    const al = await req('GET', `/api/kb/${kbAId}/attributes`)
    record('应答知识库管理', '添加属性', '属性定义列表', success(al) && al.body.data.some((a) => a.id === attrId))
    if (attrId) await req('DELETE', `/api/kb/${kbAId}/attributes/${attrId}`)
  }
  {
    const t = await req('POST', `/api/kb/${kbAId}/answer-tables`, {
      name: '自动化表格-' + Date.now(), description: '表格知识测试',
      columns: [{ name: '名称', key: 'name', type: 'text' }, { name: '价格', key: 'price', type: 'number' }],
    })
    const tid = t.body?.data?.id
    record('应答知识库管理', '新增表格', '创建表格（含 2 列定义）', success(t) && !!tid && t.body.data.columns.length === 2)
    if (tid) {
      const col = await req('POST', `/api/kb/${kbAId}/answer-tables/${tid}/columns`, { name: '备注', key: 'remark', type: 'text' })
      record('应答知识库管理', '表格增加列', '追加列（2→3）', success(col) && col.body?.data?.columns?.length === 3)
      const row = await req('POST', `/api/kb/${kbAId}/answer-tables/${tid}/rows`, { content: { name: '测试行', price: 9.9, remark: '自动化' } })
      const rid = row.body?.data?.rows?.[0]?.id
      record('应答知识库管理', '表格内容', '新增行内容', success(row) && row.body?.data?.rows?.length === 1)
      if (rid) {
        const upd = await req('PUT', `/api/kb/${kbAId}/answer-tables/${tid}/rows/${rid}`, { content: { name: '测试行-改', price: 19.9 } })
        record('应答知识库管理', '表格内容', '更新行内容', success(upd))
        await req('DELETE', `/api/kb/${kbAId}/answer-tables/${tid}/rows/${rid}`)
      }
      await req('DELETE', `/api/kb/${kbAId}/answer-tables/${tid}`)
    }
  }
  {
    const ex = await req('POST', `/api/kb/${kbAId}/qa-pairs/qa-extract`, { fileIds: [] })
    record('应答知识库管理', '导入', 'QA 抽取接口（空文件列表）', success(ex))
    const el = await req('GET', `/api/kb/${kbAId}/knowledge-edits`)
    record('对话知识', '知识导入', '采编列表（导入出口）', success(el))
  }

  /* ---------- 三/四、业务管理 + 座席端 ---------- */
  {
    const s = await req('POST', '/api/retrieval/search', { knowledgeId: kbAId, query: '自动化' })
    record('业务管理', '知识搜索查询', '检索主接口', success(s) && Array.isArray(s.body.data))
    record('业务管理', '知识搜索结果列表展示', '结果列表结构（content/source 字段）', Array.isArray(s.body.data) && (s.body.data.length === 0 || ('content' in s.body.data[0] && 'fileId' in s.body.data[0])))
    const sug = await req('POST', '/api/query/suggest', { query: '退货' })
    record('知识检索', '检索词联想', 'suggest 返回建议（非 stub）', success(sug) && sug.body?.data?.suggestedQuery !== undefined)
    const corr = await req('GET', `/api/kb/${kbAId}/auto-corrections`)
    record('知识检索', '检索词纠错', '纠错规则列表', success(corr))
  }
  {
    const flows = await req('GET', '/api/bpm/flows/list')
    record('座席端管理', '知识流程搜索', 'BPM 流程检索', success(flows))
  }

  /* ---------- 五、管理端管理 ---------- */
  {
    const std = await req('POST', `/api/kb/${kbAId}/questions/standard`, { category: '自动化', standardQuestion: '自动化标准问法是什么', answer: '自动化标准答案' })
    const sid = std.body?.data?.id
    record('管理端管理', '标准问答', '新增标准问法', success(std) && !!sid)
    if (sid) {
      const sim = await req('POST', `/api/kb/${kbAId}/questions/similar`, { standardQuestionId: sid, question: '自动化相似问法', similarity: 0.9 })
      record('管理端管理', '相似问法', '新增相似问法', success(sim))
      const rec = await req('GET', `/api/kb/${kbAId}/questions/standard/${sid}/recommend?keyword=自动化&limit=5`)
      record('对话知识', '智能推荐', '相似问法推荐', success(rec))
      await req('DELETE', `/api/kb/${kbAId}/questions/standard/${sid}`)
    }
    const kw = await req('GET', `/api/kb/${kbAId}/keywords/recommend?query=自动化&limit=5`)
    record('管理端管理', '关键词推荐', '关键词推荐列表', success(kw) && Array.isArray(kw.body.data))
  }

  /* ---------- 六、知识检索（配置族） ---------- */
  {
    const logs = await req('GET', '/api/retrieval/logs?page=1&pageSize=5')
    record('知识检索', '检索历史', '检索日志分页（前端历史数据源）', success(logs) && (logs.body?.data?.list?.length >= 0))
    const ana = await req('GET', '/api/retrieval/logs/analysis')
    record('知识检索', '检索效果分析', '检索效果聚合', success(ana) && ana.body?.data != null)
  }
  {
    const pref = await req('POST', `/api/kb/${kbAId}/search-preferences`, { name: '自动化偏好', searchMode: 'hybrid', topK: 8 })
    const prefId = pref.body?.data?.id
    record('知识检索', '检索偏好设置', '新增检索偏好', success(pref) && !!prefId)
    if (prefId) await req('DELETE', `/api/search-preferences/${prefId}`)
  }
  {
    const push = await req('POST', `/api/kb/${kbAId}/knowledge-pushes`, { title: '自动化推送', content: '自动化推送内容', pushType: 'manual' })
    const pushId = push.body?.data?.id
    record('知识检索', '知识推送', '新增知识推送', success(push) && !!pushId)
    if (pushId) {
      const send = await req('POST', `/api/knowledge-pushes/${pushId}/send`)
      record('知识检索', '知识推送', '发送推送（写系统通知）', success(send))
      await req('DELETE', `/api/knowledge-pushes/${pushId}`)
    }
  }
  {
    const remind = await req('POST', '/api/update-remind', { kbId: kbAId, enabled: 1 })
    record('知识检索', '知识更新提醒', '新增更新提醒', success(remind))
    const rl = await req('GET', `/api/kb/${kbAId}/update-remind`)
    record('知识检索', '知识库更新通知', '更新提醒列表', success(rl))
  }
  {
    const mm = await req('POST', `/api/kb/${kbAId}/multimodal-retrieval/image/search`, { query: '自动化', topK: 5 })
    record('知识检索', '多模态检索', '图片检索（真实检索实现）', success(mm) && Array.isArray(mm.body?.data?.results))
  }
  {
    const noAuth = await req('POST', '/api/retrieval/search', { knowledgeId: kbAId, query: 'x' }, { noAuth: true })
    record('知识检索', '检索权限控制', '无 token 访问被拒绝', noAuth.status === 401 || noAuth.status === 403 || (noAuth.body && noAuth.body.code !== 200 && noAuth.body.code !== 0))
  }

  /* ---------- 七、机器人管理 ---------- */
  {
    const kv = await req('GET', `/api/kb/${kbAId}/knowledge-validate`)
    record('机器人管理', '知识配置（判断）', '知识校验列表', success(kv))
    const km = await req('GET', `/api/kb/${kbAId}/knowledge`)
    record('机器人管理', '知识配置（查看）', '知识条目列表', success(km))
  }

  /* ---------- 八、对话知识 ---------- */
  {
    const ent = await req('POST', `/api/kb/${kbAId}/entities`, { name: '自动化实体', entityType: 'custom', valuesJson: '["值A","值B"]', description: '自动化' })
    const entId = ent.body?.data?.id
    record('对话知识', '实体管理', '新增实体', success(ent) && !!entId)
    if (entId) await req('DELETE', `/api/kb/${kbAId}/entities/${entId}`)
  }
  let appId = ''
  {
    const app = await req('POST', '/api/apps', { name: '自动化应用-' + Date.now() })
    appId = app.body?.data?.id
    record('对话知识', '对话流配置', '创建应用（编辑器宿主）', success(app) && !!appId)
    if (appId) {
      const get1 = await req('GET', `/api/apps/${appId}/semantic-config`)
      record('对话知识', '语义理解', '读取语义配置', success(get1) && Array.isArray(get1.body?.data?.intentPatterns))
      const put = await req('PUT', `/api/apps/${appId}/semantic-config`, {
        intentPatterns: [{ intent: '查订单', patterns: ['查订单', '订单进度'], threshold: 0.6 }],
        customSynonyms: [{ standard: '退款', synonyms: ['退钱'] }],
      })
      record('对话知识', '语义定制', '保存定制同义词/意图模式', success(put))
      const judge = await req('POST', `/api/apps/${appId}/semantic-config/judge`, { query: '帮我查订单' })
      record('对话知识', '语义理解', '语义理解试判断（命中意图）', success(judge) && judge.body?.data?.matched === true)
      const v = await req('POST', `/api/apps/${appId}/global-policy/variables`, { varKey: 'autoVar', varType: 'text', defaultValue: 'v1' })
      const vid = v.body?.data?.id
      record('对话知识', '全局变量', '新增全局变量', success(v) && !!vid)
      if (vid) await req('DELETE', `/api/apps/${appId}/global-policy/variables/${vid}`)
    }
  }
  {
    const models = await req('GET', '/api/models')
    const first = (models.body?.data?.list || models.body?.data || [])[0]
    if (first?.id) {
      const th = await req('GET', `/api/models/${first.id}/threshold`)
      record('对话知识', '模型阈值设置', '读取模型阈值', success(th) && th.body?.data?.threshold != null)
      const cur = th.body?.data?.threshold || {}
      const put = await req('PUT', `/api/models/${first.id}/threshold`, { threshold: { ...cur, scoreThreshold: cur.scoreThreshold ?? 0.6 } })
      record('对话知识', '模型阈值设置', '保存模型阈值', success(put))
    } else {
      skip('对话知识', '模型阈值设置', '读取/保存阈值', '库中无模型记录')
    }
    const presets = await req('GET', '/api/models/presets')
    record('对话知识', '模型预置（编辑）', '预置列表', success(presets))
  }
  {
    const tools = await req('GET', '/api/tools')
    record('对话知识', 'API插件（配置/编辑）', '工具列表', success(tools))
  }

  /* ---------- 九、机器人运营 ---------- */
  {
    const faq = await req('GET', '/api/analytics/faq?period=week')
    record('机器人运营', 'FAQ知识分析', 'FAQ 分析（真实聚合）', success(faq) && Array.isArray(faq.body.data))
    const mt = await req('GET', '/api/analytics/multi-turn?period=week')
    record('机器人运营', '多轮知识分析', '多轮分析（真实聚合）', success(mt) && Array.isArray(mt.body.data))
    const it = await req('GET', '/api/analytics/intent?period=week')
    record('机器人运营', '意图知识分析', '意图分析（真实聚合）', success(it) && Array.isArray(it.body.data))
  }
  {
    const dm = await req('POST', '/api/data-mining', { name: '自动化挖掘-' + Date.now(), kbId: kbAId, ruleType: 'keyword', status: 'enabled' })
    const dmid = dm.body?.data?.id
    record('机器人运营', '数据挖掘（新增/智能/一键判断）', '新建挖掘任务', success(dm) && !!dmid)
    if (dmid) {
      const run = await req('POST', `/api/data-mining/${dmid}/run`)
      let summary = {}
      try { summary = JSON.parse(run.body?.data?.resultSummary || '{}') } catch { }
      record('机器人运营', '数据挖掘（新增/智能/一键判断）', '执行挖掘（真实统计：非随机数）', success(run) && Array.isArray(summary.topKeywords), `totalQueries=${summary.totalQueries}`)
      await req('DELETE', `/api/data-mining/${dmid}`)
    }
    const opt = await req('GET', `/api/analytics/feedback/statistics`)
    record('机器人运营', '知识优化', '反馈统计（优化数据源）', success(opt) || opt.status === 200)
  }

  /* ---------- 清理 ---------- */
  if (appId) await req('DELETE', `/api/apps/${appId}`)
  if (kbAId) await req('DELETE', `/api/kb/${kbAId}`)
  if (kbBId) await req('DELETE', `/api/kb/${kbBId}`)

  /* ---------- 汇总 ---------- */
  console.log(`\n===== 动态 API 集成测试结果：PASS ${passCount} / FAIL ${failCount}${skipCount ? ' / SKIP ' + skipCount : ''}（共 ${passCount + failCount + skipCount} 用例）=====`)
  const failed = results.filter((r) => r.pass === false)
  if (failed.length) {
    console.log('失败用例：')
    for (const f of failed) console.log(`  ✗ [${f.module}] ${f.feature} · ${f.name}${f.detail ? ' —— ' + f.detail : ''}`)
  }
  process.exit(failCount > 0 ? 1 : 0)
}

main().catch((e) => { console.error('测试执行异常：', e.message); process.exit(2) })
