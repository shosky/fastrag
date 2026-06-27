import { checkApiPermission } from './interceptor'

export interface EvalDataset {
  id: string; name: string; description: string; questionCount: number; category: string
  status: 'active' | 'draft' | 'archived'; creator: string; createdAt: string
}

export interface EvalTask {
  id: string; name: string; datasetId: string; datasetName: string; modelId: string; modelName: string
  status: 'pending' | 'running' | 'completed' | 'failed'; progress: number
  metrics?: { accuracy: number; recall: number; f1: number; avgLatency: number }
  createdAt: string; completedAt?: string
}

export interface RobotRelease {
  id: string; robotName: string; version: string; environment: 'staging' | 'production'
  status: 'pending' | 'released' | 'rolled_back'; releaseNotes: string; releasedAt?: string; createdAt: string
}

export const EVAL_STATUS_LABELS: Record<string, string> = { active: '启用', draft: '草稿', archived: '已归档' }
export const EVAL_STATUS_COLORS: Record<string, string> = { active: 'success', draft: 'info', archived: 'danger' }
export const TASK_STATUS_LABELS: Record<string, string> = { pending: '等待中', running: '运行中', completed: '已完成', failed: '失败' }
export const TASK_STATUS_COLORS: Record<string, string> = { pending: 'info', running: 'warning', completed: 'success', failed: 'danger' }
export const RELEASE_STATUS_LABELS: Record<string, string> = { pending: '待发布', released: '已发布', rolled_back: '已回滚' }
export const RELEASE_STATUS_COLORS: Record<string, string> = { pending: 'warning', released: 'success', rolled_back: 'danger' }

let datasetStore: EvalDataset[] = []
let taskStore: EvalTask[] = []
let releaseStore: RobotRelease[] = []
let seq = 100

function initStore() {
  if (datasetStore.length > 0) return
  const now = new Date().toISOString()

  datasetStore = Array.from({ length: 6 }, (_, i) => ({
    id: `eds-${i + 1}`, name: `评估数据集${i + 1}`, description: `数据集描述${i + 1}`,
    questionCount: Math.floor(Math.random() * 500) + 50,
    category: ['通用问答', '产品知识', '技术支持', '业务流程'][i % 4],
    status: (['active', 'draft', 'archived'] as const)[i % 3], creator: 'admin', createdAt: now,
  }))

  taskStore = Array.from({ length: 8 }, (_, i) => ({
    id: `et-${++seq}`, name: `评估任务${i + 1}`,
    datasetId: `eds-${(i % 6) + 1}`, datasetName: `评估数据集${(i % 6) + 1}`,
    modelId: `model-${(i % 3) + 1}`, modelName: ['GPT-4o', 'Claude-3.5', 'Qwen-2.5'][i % 3],
    status: (['pending', 'running', 'completed', 'failed'] as const)[i % 4],
    progress: i % 4 === 2 ? 100 : i % 4 === 1 ? Math.floor(Math.random() * 80) + 10 : 0,
    metrics: i % 4 === 2 ? { accuracy: 0.92, recall: 0.88, f1: 0.90, avgLatency: 1200 } : undefined,
    createdAt: new Date(Date.now() - i * 86400000).toISOString(),
    completedAt: i % 4 === 2 ? now : undefined,
  }))

  releaseStore = Array.from({ length: 5 }, (_, i) => ({
    id: `rel-${i + 1}`, robotName: `机器人${(i % 2) + 1}`, version: `v1.${i}.0`,
    environment: i % 2 === 0 ? 'production' : 'staging',
    status: (['pending', 'released', 'rolled_back'] as const)[i % 3],
    releaseNotes: `版本${i + 1}更新说明`, createdAt: now,
    releasedAt: i % 3 === 1 ? now : undefined,
  }))
}

export function getEvalDatasetList(params?: { page?: number; pageSize?: number; keyword?: string }) {
  initStore(); let list = [...datasetStore]
  if (params?.keyword) list = list.filter(d => d.name.includes(params.keyword!))
  const total = list.length; const page = params?.page || 1; const pageSize = params?.pageSize || 20
  return { list: list.slice((page - 1) * pageSize, page * pageSize), total }
}
export function createEvalDataset(data: Partial<EvalDataset>): EvalDataset {
  checkApiPermission('kb:write'); initStore()
  const item: EvalDataset = { id: `eds-${++seq}`, name: data.name || '', description: data.description || '', questionCount: 0, category: data.category || '', status: 'draft', creator: 'admin', createdAt: new Date().toISOString() }
  datasetStore.push(item); return item
}
export function updateEvalDataset(id: string, data: Partial<EvalDataset>): EvalDataset | null {
  checkApiPermission('kb:write'); initStore()
  const idx = datasetStore.findIndex(d => d.id === id); if (idx === -1) return null
  datasetStore[idx] = { ...datasetStore[idx], ...data }; return datasetStore[idx]
}
export function deleteEvalDataset(id: string): boolean {
  checkApiPermission('kb:delete'); initStore()
  const idx = datasetStore.findIndex(d => d.id === id); if (idx === -1) return false
  datasetStore.splice(idx, 1); return true
}

export function getEvalTaskList(params?: { page?: number; pageSize?: number; status?: string }) {
  initStore(); let list = [...taskStore]
  if (params?.status) list = list.filter(t => t.status === params.status)
  const total = list.length; const page = params?.page || 1; const pageSize = params?.pageSize || 20
  return { list: list.slice((page - 1) * pageSize, page * pageSize), total }
}
export function createEvalTask(data: Partial<EvalTask>): EvalTask {
  checkApiPermission('kb:write'); initStore()
  const item: EvalTask = { id: `et-${++seq}`, name: data.name || '', datasetId: data.datasetId || '', datasetName: data.datasetName || '', modelId: data.modelId || '', modelName: data.modelName || '', status: 'pending', progress: 0, createdAt: new Date().toISOString() }
  taskStore.push(item); return item
}
export function deleteEvalTask(id: string): boolean {
  checkApiPermission('kb:delete'); initStore()
  const idx = taskStore.findIndex(t => t.id === id); if (idx === -1) return false
  taskStore.splice(idx, 1); return true
}

export function getRobotReleaseList(): RobotRelease[] { initStore(); return releaseStore }
export function createRobotRelease(data: Partial<RobotRelease>): RobotRelease {
  checkApiPermission('app:write'); initStore()
  const item: RobotRelease = { id: `rel-${++seq}`, robotName: data.robotName || '', version: data.version || '', environment: data.environment || 'staging', status: 'pending', releaseNotes: data.releaseNotes || '', createdAt: new Date().toISOString() }
  releaseStore.push(item); return item
}
export function releaseRobot(id: string): RobotRelease | null {
  checkApiPermission('app:write'); initStore()
  const idx = releaseStore.findIndex(r => r.id === id); if (idx === -1) return null
  releaseStore[idx] = { ...releaseStore[idx], status: 'released', releasedAt: new Date().toISOString() }; return releaseStore[idx]
}
