// ===========================================================================
// 模型数据层 —— 全局唯一数据源
//
// 取代 model-management.vue 的内联 models ref 与 form.vue 的硬编码 el-option。
// 模型管理页 / 知识库表单 / 应用配置 共享本文件。
// ===========================================================================

export type ModelPurpose = '大语言模型' | 'Embedding模型' | 'Rerank模型' | 'OCR识别'
export type ModelStatus = 'online' | 'offline'

export interface ModelRecord {
  id: string
  name: string
  code: string
  purpose: ModelPurpose
  brand: string
  apiUrl: string
  status: ModelStatus
}

const modelStore: ModelRecord[] = [
  { id: '1', name: 'qwen3-32b', code: 'qwen3-32b', purpose: '大语言模型', brand: '通义千问', apiUrl: 'https://api.qwen.com', status: 'online' },
  { id: '2', name: 'DeepSeek-V3', code: 'deepseek-v3', purpose: '大语言模型', brand: 'DeepSeek', apiUrl: 'https://api.deepseek.com', status: 'online' },
  { id: '3', name: 'text-embedding-v4', code: 'text-embedding-v4', purpose: 'Embedding模型', brand: '通义千问', apiUrl: 'https://api.qwen.com', status: 'online' },
  { id: '4', name: 'text-embedding-v3', code: 'text-embedding-v3', purpose: 'Embedding模型', brand: '通义千问', apiUrl: 'https://api.qwen.com', status: 'online' },
  { id: '5', name: 'bge-m3', code: 'bge-m3', purpose: 'Embedding模型', brand: '智谱AI', apiUrl: 'https://api.zhipu.com', status: 'online' },
  { id: '6', name: 'bge-reranker-v2-m3', code: 'bge-reranker-v2-m3', purpose: 'Rerank模型', brand: '智谱AI', apiUrl: 'https://api.zhipu.com', status: 'offline' },
]

let modelSeq = 100

/** 获取所有模型 */
export function getModels(): ModelRecord[] {
  return modelStore.map((m) => ({ ...m }))
}

/** 获取单个模型 */
export function getModel(id: string): ModelRecord | null {
  const m = modelStore.find((x) => x.id === id)
  return m ? { ...m } : null
 }

/** 按用途过滤模型（默认只返回在线模型） */
export function getModelsByPurpose(purpose: ModelPurpose, onlineOnly = true): ModelRecord[] {
  return modelStore
    .filter((m) => m.purpose === purpose && (!onlineOnly || m.status === 'online'))
    .map((m) => ({ ...m }))
}

/** 获取可用的 Embedding 模型 code 列表（知识库表单用） */
export function getEmbeddingModelCodes(onlineOnly = true): string[] {
  return getModelsByPurpose('Embedding模型', onlineOnly).map((m) => m.code)
}

/** 创建模型 */
export function createModel(form: Omit<ModelRecord, 'id'>): ModelRecord {
  const model: ModelRecord = { ...form, id: String(++modelSeq) }
  modelStore.push(model)
  return { ...model }
}

/** 更新模型 */
export function updateModel(id: string, patch: Partial<ModelRecord>): ModelRecord | null {
  const idx = modelStore.findIndex((m) => m.id === id)
  if (idx === -1) return null
  modelStore[idx] = { ...modelStore[idx], ...patch }
  return { ...modelStore[idx] }
}

/** 删除模型 */
export function deleteModel(id: string): boolean {
  const idx = modelStore.findIndex((m) => m.id === id)
  if (idx === -1) return false
  modelStore.splice(idx, 1)
  return true
}

/** 切换模型上下架状态 */
export function toggleModelStatus(id: string): ModelRecord | null {
  const idx = modelStore.findIndex((m) => m.id === id)
  if (idx === -1) return null
  modelStore[idx].status = modelStore[idx].status === 'online' ? 'offline' : 'online'
  return { ...modelStore[idx] }
}

export const MODEL_PURPOSES: ModelPurpose[] = ['大语言模型', 'Embedding模型', 'Rerank模型', 'OCR识别']

export const MODEL_BRANDS = [
  '火山引擎', '通义千问', 'DeepSeek', '月之暗面', '智谱AI',
  '零一万物', '百川智能', 'MiniMax', '文心一言',
]

/** el-tag 的 type 取值（与 Element Plus 当前版本对齐） */
export type TagType = 'success' | 'warning' | 'danger' | 'info' | 'primary'

export const MODEL_PURPOSE_COLORS: Record<ModelPurpose, TagType> = {
  '大语言模型': 'primary',
  'Embedding模型': 'success',
  'Rerank模型': 'warning',
  'OCR识别': 'danger',
}

// ===========================================================================
// 模型生命周期：训练记录 / 测试报告 / 调用日志
// ===========================================================================

/** 训练记录 */
export interface TrainingRecord {
  id: string
  modelId: string
  modelName: string
  status: 'running' | 'completed' | 'failed'
  /** 训练数据量 */
  dataSize: number
  /** 训练轮次 */
  epochs: number
  /** 训练指标 */
  metrics: { accuracy?: number; loss?: number }
  startedAt: string
  completedAt?: string
}

/** 测试报告 */
export interface TestReport {
  id: string
  modelId: string
  modelName: string
  /** 测试集名称 */
  testSet: string
  /** 测试指标 */
  metrics: { accuracy: number; precision: number; recall: number; f1: number }
  testedAt: string
}

/** 调用日志 */
export interface CallLog {
  id: string
  modelId: string
  modelName: string
  caller: string
  /** 输入 token 数 */
  inputTokens: number
  /** 输出 token 数 */
  outputTokens: number
  /** 耗时 ms */
  duration: number
  status: 'success' | 'error'
  timestamp: string
}

const trainingStore: TrainingRecord[] = []
const testStore: TestReport[] = []
const callLogStore: CallLog[] = []
let trainSeq = 100
let testSeq = 100
let callSeq = 100

function seedLifecycle() {
  if (trainingStore.length > 0) return
  trainingStore.push(
    { id: 'tr_1', modelId: '1', modelName: 'qwen3-32b', status: 'completed', dataSize: 100000, epochs: 3, metrics: { accuracy: 0.92, loss: 0.08 }, startedAt: '2026-05-01 08:00', completedAt: '2026-05-03 16:00' },
    { id: 'tr_2', modelId: '3', modelName: 'text-embedding-v4', status: 'completed', dataSize: 500000, epochs: 5, metrics: { accuracy: 0.95, loss: 0.05 }, startedAt: '2026-05-10 09:00', completedAt: '2026-05-12 18:00' },
  )
  testStore.push(
    { id: 'ts_1', modelId: '1', modelName: 'qwen3-32b', testSet: '通用问答测试集', metrics: { accuracy: 0.89, precision: 0.91, recall: 0.87, f1: 0.89 }, testedAt: '2026-05-04 10:00' },
    { id: 'ts_2', modelId: '3', modelName: 'text-embedding-v4', testSet: '语义相似度测试集', metrics: { accuracy: 0.93, precision: 0.94, recall: 0.92, f1: 0.93 }, testedAt: '2026-05-13 14:00' },
  )
  callLogStore.push(
    { id: 'cl_1', modelId: '1', modelName: 'qwen3-32b', caller: '智能问答助手', inputTokens: 256, outputTokens: 512, duration: 1200, status: 'success', timestamp: '2026-06-17 09:15' },
    { id: 'cl_2', modelId: '1', modelName: 'qwen3-32b', caller: '客服机器人', inputTokens: 128, outputTokens: 256, duration: 800, status: 'success', timestamp: '2026-06-17 09:20' },
    { id: 'cl_3', modelId: '3', modelName: 'text-embedding-v4', caller: '知识库检索', inputTokens: 64, outputTokens: 0, duration: 50, status: 'success', timestamp: '2026-06-17 09:25' },
    { id: 'cl_4', modelId: '2', modelName: 'DeepSeek-V3', caller: '文档写作助手', inputTokens: 1024, outputTokens: 2048, duration: 3500, status: 'success', timestamp: '2026-06-17 10:00' },
    { id: 'cl_5', modelId: '1', modelName: 'qwen3-32b', caller: '智能问答助手', inputTokens: 512, outputTokens: 1024, duration: 0, status: 'error', timestamp: '2026-06-17 10:30' },
  )
}

seedLifecycle()

export function getTrainingRecords(modelId?: string): TrainingRecord[] {
  const list = modelId ? trainingStore.filter((r) => r.modelId === modelId) : trainingStore
  return [...list].reverse().map((r) => ({ ...r }))
}

export function getTestReports(modelId?: string): TestReport[] {
  const list = modelId ? testStore.filter((r) => r.modelId === modelId) : testStore
  return [...list].reverse().map((r) => ({ ...r }))
}

export function getCallLogs(modelId?: string): CallLog[] {
  const list = modelId ? callLogStore.filter((r) => r.modelId === modelId) : callLogStore
  return [...list].reverse().map((r) => ({ ...r }))
}

