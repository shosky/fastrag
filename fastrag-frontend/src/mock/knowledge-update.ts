// ===========================================================================
// 知识更新 mock 数据层
//
// 更新日志：记录每次文件/切片级别的变更。
// Diff：对比两个版本之间的差异。
// ===========================================================================

/** 更新类型 */
export type UpdateType = 'file_added' | 'file_removed' | 'file_updated' | 'chunk_added' | 'chunk_removed' | 'chunk_updated' | 'config_changed'

/** 更新日志条目 */
export interface UpdateLog {
  id: string
  kbId: string
  /** 变更类型 */
  updateType: UpdateType
  /** 变更对象（文件名/切片ID/配置项） */
  target: string
  /** 变更详情 */
  detail: string
  /** 变更前的值（diff 用） */
  oldValue?: string
  /** 变更后的值（diff 用） */
  newValue?: string
  operator: string
  timestamp: string
}

/** 版本间 diff 摘要 */
export interface VersionDiff {
  kbId: string
  fromVersion: number
  toVersion: number
  added: number
  removed: number
  updated: number
  logs: UpdateLog[]
}

const logStore: Record<string, UpdateLog[]> = {}
let logSeq = 100

function now(): string {
  return new Date().toLocaleString('zh-CN')
}

function getStore(kbId: string): UpdateLog[] {
  if (!logStore[kbId]) {
    logStore[kbId] = seedLogs(kbId)
  }
  return logStore[kbId]
}

function seedLogs(kbId: string): UpdateLog[] {
  return [
    { id: 'ul_1', kbId, updateType: 'file_added', target: '小微ICT业务 FAQ-V1-0306.docx', detail: '新增文件', operator: '张三', timestamp: '2026-06-01 10:00' },
    { id: 'ul_2', kbId, updateType: 'chunk_added', target: 'chunk_0~19', detail: '解析生成 20 个切片', operator: '系统', timestamp: '2026-06-01 10:05' },
    { id: 'ul_3', kbId, updateType: 'file_updated', target: '小微ICT业务 FAQ-V1-0306.docx', detail: '重新解析文件', oldValue: 'v1 版本', newValue: 'v2 版本', operator: '张三', timestamp: '2026-06-10 14:30' },
    { id: 'ul_4', kbId, updateType: 'chunk_updated', target: 'chunk_5', detail: '切片内容更新', oldValue: '综合布线标准化礼包：20点位标准包 2599 元', newValue: '综合布线标准化礼包：20点位标准包 2799 元', operator: '系统', timestamp: '2026-06-10 14:35' },
    { id: 'ul_5', kbId, updateType: 'config_changed', target: '检索模式', detail: '检索模式变更', oldValue: 'vector', newValue: 'hybrid', operator: '李四', timestamp: '2026-06-12 09:00' },
    { id: 'ul_6', kbId, updateType: 'file_added', target: '产品操作手册.pdf', detail: '新增文件', operator: '王五', timestamp: '2026-06-15 11:00' },
    { id: 'ul_7', kbId, updateType: 'chunk_added', target: 'chunk_20~35', detail: '解析生成 16 个切片', operator: '系统', timestamp: '2026-06-15 11:05' },
  ]
}

// --- 查询 ---
export function getUpdateLogs(kbId: string): UpdateLog[] {
  return [...getStore(kbId)].reverse().map((l) => ({ ...l }))
}

export function getUpdateLogsByType(kbId: string, type: UpdateType): UpdateLog[] {
  return getUpdateLogs(kbId).filter((l) => l.updateType === type)
}

/**
 * 获取两个版本之间的 diff 摘要。
 * 模拟：统计日志中各种变更类型的数量。
 */
export function getVersionDiff(kbId: string, fromVersion: number, toVersion: number): VersionDiff {
  const logs = getUpdateLogs(kbId)
  const added = logs.filter((l) => l.updateType === 'file_added' || l.updateType === 'chunk_added').length
  const removed = logs.filter((l) => l.updateType === 'file_removed' || l.updateType === 'chunk_removed').length
  const updated = logs.filter((l) => l.updateType === 'file_updated' || l.updateType === 'chunk_updated' || l.updateType === 'config_changed').length

  return {
    kbId,
    fromVersion,
    toVersion,
    added,
    removed,
    updated,
    logs,
  }
}

// --- 写入 ---
// --- 写入 ---
export function addUpdateLog(log: Omit<UpdateLog, 'id' | 'timestamp'>): UpdateLog {
  const entry: UpdateLog = { ...log, id: `ul_${++logSeq}`, timestamp: now() }
  getStore(log.kbId).push(entry)
  return { ...entry }
}
