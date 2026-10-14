// 配置迁移后端真实化 —— 真机验证脚本（GET/POST/DELETE /api/workflows/migrations）
// 前置：后端运行于 127.0.0.1:8081（主配置 + MySQL），admin/123456 可登录。
const BASE = 'http://127.0.0.1:8081'
let TOKEN = ''
let pass = 0, fail = 0
const ok = (name, cond, detail = '') => {
  console.log(`${cond ? 'PASS' : 'FAIL'}  ${name}${detail ? '  | ' + detail : ''}`)
  cond ? pass++ : fail++
}
async function req(method, path, body) {
  const res = await fetch(BASE + path, {
    method,
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${TOKEN}` },
    body: body ? JSON.stringify(body) : undefined,
  })
  let j = null
  try { j = await res.json() } catch { /* 非 JSON */ }
  return { status: res.status, body: j }
}
const data = (r) => r.body?.data

// 1) 登录
const login = await req('POST', '/api/auth/login', { username: 'admin', password: '123456' })
TOKEN = login.body?.data?.token || ''
ok('登录获取 token', !!TOKEN)

// 2) 迁移日志初始状态
let r = await req('GET', '/api/workflows/migrations')
const before = data(r) || []
ok('GET /migrations 返回数组（接口已真实化，不再是恒空桩）', Array.isArray(before), `当前 ${before.length} 条`)

// 3) 找源业务流（含节点者），新建目标业务流
r = await req('GET', '/api/workflows')
const flows = Array.isArray(data(r)) ? data(r) : (data(r)?.list || data(r)?.records || [])
ok('业务流列表可读', flows.length > 0, `共 ${flows.length} 条`)
let src = null
for (const f of flows) {
  const nodes = data(await req('GET', `/api/workflows/${f.id}/nodes`)) || []
  if (Array.isArray(nodes) && nodes.length > 0) { src = { flow: f, nodes }; break }
}
ok('找到含节点的源业务流', !!src, src ? `${src.flow.name}（${src.nodes.length} 节点）` : '')
const tgtName = `迁移验证目标-${new Date().toISOString().slice(0, 10)}`
r = await req('POST', '/api/workflows', { name: tgtName, description: '配置迁移真机验证目标' })
const tgtId = data(r)?.id
ok('创建目标业务流', !!tgtId, tgtId || JSON.stringify(r.body))

// 4) 源节点扩展配置采样（迁移后比对）
const dims = ['delays', 'resources', 'permissions', 'log-level', 'data-policies']
const sample = {}
for (const n of src.nodes) {
  for (const d of dims) {
    const v = data(await req('GET', `/api/workflows/${src.flow.id}/nodes/${n.nodeKey}/${d}`))
    if (v?.value) sample[`${n.nodeKey}.${d}`] = v.value
  }
}
console.log('  源扩展配置采样:', JSON.stringify(sample))

// 5) 覆盖策略迁移
r = await req('POST', '/api/workflows/migrations', {
  sourceWorkflowId: src.flow.id, targetWorkflowId: tgtId,
  targetEnv: 'test', strategy: 'overwrite', remark: '真机验证-覆盖策略',
})
const mig = data(r)
ok('POST /migrations 返回 200', r.status === 200 && r.body?.code === 200, JSON.stringify(r.body).slice(0, 160))
ok('覆盖迁移 completed 且 progress=100', mig?.status === 'completed' && Number(mig?.progress) === 100, `status=${mig?.status} progress=${mig?.progress}`)
ok('节点迁移数与源一致', Number(mig?.nodeCount) === src.nodes.length, `nodeCount=${mig?.nodeCount}`)
ok('扩展配置迁移计数>0（或源本无扩展配置）', Number(mig?.extCount) > 0 || Object.keys(sample).length === 0, `extCount=${mig?.extCount}`)

// 6) 目标侧回读比对
const tgtNodes = data(await req('GET', `/api/workflows/${tgtId}/nodes`)) || []
ok('目标节点数与源一致', tgtNodes.length === src.nodes.length, `目标 ${tgtNodes.length} 个`)
const tgtVerList = data(await req('GET', `/api/bpm/flows/${tgtId}/versions`))
const tgtVer = Array.isArray(tgtVerList) ? tgtVerList.find(v => v.status === 'draft') : null
const tgtEdges = tgtVer ? (data(await req('GET', `/api/bpm/flows/${tgtId}/versions/${tgtVer.id}/edges`)) || []) : []
const srcEdges = data(await req('GET', `/api/bpm/flows/${src.flow.id}/versions`)).then ? [] : []
const srcVerList = data(await req('GET', `/api/bpm/flows/${src.flow.id}/versions`))
const srcVer = Array.isArray(srcVerList) ? srcVerList.find(v => v.status === 'draft') : null
const srcEdgeList = srcVer ? (data(await req('GET', `/api/bpm/flows/${src.flow.id}/versions/${srcVer.id}/edges`)) || []) : []
ok('目标连线数与源一致', tgtEdges.length === srcEdgeList.length, `目标 ${tgtEdges.length} / 源 ${srcEdgeList.length}`)
for (const [key] of Object.entries(sample).slice(0, 3)) {
  const [nk, d] = key.split('.')
  const v = data(await req('GET', `/api/workflows/${tgtId}/nodes/${nk}/${d}`))
  ok(`目标侧回读 ${key}`, JSON.stringify(v?.value) === JSON.stringify(sample[key]), JSON.stringify(v?.value))
}

// 7) 合并策略二次迁移（应全部跳过）
r = await req('POST', '/api/workflows/migrations', {
  sourceWorkflowId: src.flow.id, targetWorkflowId: tgtId,
  targetEnv: 'test', strategy: 'merge', remark: '真机验证-合并策略',
})
const mig2 = data(r)
ok('合并迁移 completed 且 nodeCount=0', mig2?.status === 'completed' && Number(mig2?.nodeCount) === 0, `nodeCount=${mig2?.nodeCount} skipped=${mig2?.skipped}`)
const tgtNodes2 = data(await req('GET', `/api/workflows/${tgtId}/nodes`)) || []
ok('合并迁移未重复加节点', tgtNodes2.length === src.nodes.length, `目标仍 ${tgtNodes2.length} 个`)

// 8) 迁移日志
r = await req('GET', '/api/workflows/migrations')
const rows = data(r) || []
ok('迁移日志含两条真实记录', rows.length >= before.length + 2, `当前 ${rows.length} 条`)
const last = rows[0]
ok('日志行含 计数/进度/操作人/验证结果', last && last.nodeCount !== undefined && last.progress !== undefined && !!last.operator && !!last.validateResult,
  `validateResult=${String(last?.validateResult).slice(0, 80)}`)

// 9) 清空日志
r = await req('DELETE', '/api/workflows/migrations')
ok('DELETE /migrations 清空成功', r.body?.code === 200)
r = await req('GET', '/api/workflows/migrations')
ok('清空后日志为空', (data(r) || []).length === 0)

console.log(`\n合计: ${pass} PASS / ${fail} FAIL`)
process.exit(fail ? 1 : 0)
