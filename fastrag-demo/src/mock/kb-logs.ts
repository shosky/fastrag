// ===========================================================================
// 知识库统一日志数据层
//
// 三类日志共用一套 store：
//   operation — 文件/切片/配置变更（由 files.ts 写入）
//   retrieval — API 检索调用（由 api/index.ts 写入）
//   publish   — 版本发布/审核（由 audit.ts 写入）
// ===========================================================================

/** 日志大类 */
export type LogCategory = 'operation' | 'retrieval' | 'publish'

/** 日志条目 */
export interface KBLog {
  id: string
  kbId: string
  category: LogCategory
  /** 具体动作 */
  action: string
  /** 操作对象 */
  target: string
  /** 详情 */
  detail: string
  /** 操作人 / 调用方 */
  operator: string
  /** 状态（检索：success/error，发布：待审核/已通过 等） */
  status?: string
  /** 扩展字段 */
  extra?: Record<string, any>
  timestamp: string
}

// --- Store ---
const logStore: Record<string, KBLog[]> = {}
let logSeq = 1000

function now(): string {
  return new Date().toLocaleString('zh-CN')
}

function getStore(kbId: string): KBLog[] {
  if (!logStore[kbId]) {
    logStore[kbId] = seedLogs(kbId)
  }
  return logStore[kbId]
}

function seedLogs(kbId: string): KBLog[] {
  return [
    // 操作日志
    { id: 'kl_1', kbId, category: 'operation', action: 'file_added', target: 'AIS 平台用户手册.pdf', detail: '上传文件', operator: '张三', timestamp: '2026-06-01 10:00' },
    { id: 'kl_2', kbId, category: 'operation', action: 'chunk_added', target: 'chunk_0~9', detail: '解析生成 10 个切片', operator: '系统', timestamp: '2026-06-01 10:05' },
    { id: 'kl_3', kbId, category: 'operation', action: 'config_changed', target: '检索模式', detail: '检索模式变更', operator: '李四', status: '', extra: { oldValue: 'vector', newValue: 'hybrid' }, timestamp: '2026-06-12 09:00' },
    // 发布日志
    { id: 'kl_4', kbId, category: 'publish', action: 'version_created', target: 'v1', detail: '创建版本快照', operator: '管理员', status: '草稿', timestamp: '2026-06-01 10:00' },
    { id: 'kl_5', kbId, category: 'publish', action: 'submitted', target: 'v1', detail: '提交审核', operator: '管理员', status: '待审核', timestamp: '2026-06-01 14:00' },
    { id: 'kl_6', kbId, category: 'publish', action: 'approved', target: 'v1', detail: '审核通过', operator: '超级管理员', status: '已通过', timestamp: '2026-06-02 09:00' },
    { id: 'kl_7', kbId, category: 'publish', action: 'published', target: 'v1', detail: '版本发布', operator: '管理员', status: '已发布', timestamp: '2026-06-02 10:00' },
    // 检索日志
    { id: 'kl_8', kbId, category: 'retrieval', action: 'search', target: '什么是小微ICT', detail: '命中 5 条，耗时 0.3s', operator: 'API 调用', status: 'success', extra: { query: '什么是小微ICT', mode: 'hybrid', topK: 5, hits: 5, duration: 300 }, timestamp: '2026-06-15 14:20' },
    { id: 'kl_9', kbId, category: 'retrieval', action: 'search', target: '全光组网价格', detail: '命中 3 条，耗时 0.2s', operator: 'API 调用', status: 'success', extra: { query: '全光组网价格', mode: 'vector', topK: 10, hits: 3, duration: 200 }, timestamp: '2026-06-15 14:25' },
    { id: 'kl_10', kbId, category: 'retrieval', action: 'search', target: 'fp', detail: '拼音匹配→发票，命中 4 条，耗时 0.4s', operator: 'API 调用', status: 'success', extra: { query: 'fp', expandedQuery: '发票', mode: 'hybrid', topK: 5, hits: 4, duration: 400, preprocessed: { correction: true } }, timestamp: '2026-06-16 09:10' },
  ]
}

// --- 写入 ---

/** 写入一条日志（通用） */
export function addKBLog(log: Omit<KBLog, 'id' | 'timestamp'>): KBLog {
  const entry: KBLog = { ...log, id: `kl_${++logSeq}`, timestamp: now() }
  getStore(log.kbId).push(entry)
  return { ...entry }
}

/** 写入操作日志（files.ts 调用） */
export function addOperationLog(kbId: string, action: string, target: string, detail: string, extra?: Record<string, any>): KBLog {
  return addKBLog({ kbId, category: 'operation', action, target, detail, operator: '当前用户', extra })
}

/** 写入发布日志（audit.ts 调用） */
export function addPublishLog(kbId: string, action: string, target: string, detail: string, status: string, operator: string = '当前用户'): KBLog {
  return addKBLog({ kbId, category: 'publish', action, target, detail, operator, status })
}

/** 写入检索日志（api/index.ts 调用） */
export function addRetrievalLog(kbId: string, query: string, detail: string, extra?: Record<string, any>): KBLog {
  return addKBLog({ kbId, category: 'retrieval', action: 'search', target: query, detail, operator: 'API 调用', status: 'success', extra })
}

// --- 查询 ---

/** 获取知识库全部日志（按时间倒序） */
export function getKBLogs(kbId: string): KBLog[] {
  return [...getStore(kbId)].reverse()
}

/** 按类别查询 */
export function getKBLogsByCategory(kbId: string, category: LogCategory): KBLog[] {
  return getKBLogs(kbId).filter((l) => l.category === category)
}
