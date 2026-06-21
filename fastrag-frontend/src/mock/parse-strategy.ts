import type { ParseStrategy, ParseStrategyForm } from '@/types/knowledge'
import { DEFAULT_ADVANCED } from '@/types/knowledge'

// ===========================================================================
// 解析策略数据层 —— 全局唯一数据源
//
// 取代 parse-strategy.vue 里的硬编码 strategies 和 create-parse-strategy.vue 的孤立状态。
// 两条创建路径（内联对话框 + 向导页）共享本文件，保证列表一致。
// ===========================================================================

function seedStrategies(): ParseStrategy[] {
  const now = new Date().toLocaleString('zh-CN')
  return [
    {
      id: '1',
      name: '默认解析方法',
      description: '通用文档解析策略，适用于常见文档类型',
      extensions: ['.pdf', '.docx', '.doc', '.txt', '.md'],
      parseMethod: 'default',
      isDefault: true,
      createdAt: now,
      updatedAt: now,
      advanced: { ...DEFAULT_ADVANCED },
    },
    {
      id: '2',
      name: 'PPT解析方法',
      description: '专门针对 PPT 文件的解析策略，按页解析',
      extensions: ['.pptx', '.ppt'],
      parseMethod: 'pptx',
      isDefault: false,
      createdAt: now,
      updatedAt: now,
      advanced: { ...DEFAULT_ADVANCED, enablePptWholePage: true },
    },
    {
      id: '3',
      name: 'PDF解析方法',
      description: '针对 PDF 文件的增强解析，支持表格和图文混排',
      extensions: ['.pdf'],
      parseMethod: 'pdf',
      isDefault: false,
      createdAt: now,
      updatedAt: now,
      advanced: { ...DEFAULT_ADVANCED, enableDocSummary: true },
    },
    {
      id: '4',
      name: '视频解析方法',
      description: '视频文件解析，提取关键帧和语音',
      extensions: ['.mp4', '.avi', '.mov', '.mkv', '.flv'],
      parseMethod: 'video',
      isDefault: false,
      createdAt: now,
      updatedAt: now,
      advanced: { ...DEFAULT_ADVANCED, splitMethod: 'delimiter', delimiters: ['。', '\n\n'] },
    },
    {
      id: '5',
      name: '音频解析方法',
      description: '音频文件解析，ASR 语音转文字',
      extensions: ['.mp3', '.wav', '.m4a', '.aac'],
      parseMethod: 'audio',
      isDefault: false,
      createdAt: now,
      updatedAt: now,
      advanced: { ...DEFAULT_ADVANCED, chunkLength: 1000, enableDocSummary: true },
    },
  ]
}

const strategyStore: Record<string, ParseStrategy[]> = {
  default: seedStrategies(),
}

let strategySeq = 100

function getStore(kbId: string): ParseStrategy[] {
  if (!strategyStore[kbId]) strategyStore[kbId] = seedStrategies()
  return strategyStore[kbId]
}

function now(): string {
  return new Date().toLocaleString('zh-CN')
}

/** 获取策略列表 */
export function getStrategies(kbId: string): ParseStrategy[] {
  return getStore(kbId).map((s) => ({ ...s }))
}

/** 获取单个策略 */
export function getStrategy(kbId: string, id: string): ParseStrategy | null {
  const s = getStore(kbId).find((x) => x.id === id)
  return s ? { ...s } : null
}

/** 创建策略 */
export function createStrategy(kbId: string, form: ParseStrategyForm): ParseStrategy {
  const strategy: ParseStrategy = {
    id: `strategy_${++strategySeq}`,
    name: form.name,
    description: form.description,
    extensions: normalizeExtensions(form.extensions),
    parseMethod: form.parseMethod,
    isDefault: false,
    createdAt: now(),
    updatedAt: now(),
    advanced: form.advanced ? { ...form.advanced } : { ...DEFAULT_ADVANCED },
  }
  getStore(kbId).unshift(strategy)
  return strategy
}

/** 更新策略 */
export function updateStrategy(kbId: string, id: string, form: ParseStrategyForm): ParseStrategy | null {
  const store = getStore(kbId)
  const idx = store.findIndex((s) => s.id === id)
  if (idx === -1) return null
  store[idx] = {
    ...store[idx],
    name: form.name,
    description: form.description,
    extensions: normalizeExtensions(form.extensions),
    parseMethod: form.parseMethod,
    advanced: form.advanced ? { ...form.advanced } : store[idx].advanced,
    updatedAt: now(),
  }
  return { ...store[idx] }
}

/** 删除策略（默认策略不可删） */
export function deleteStrategy(kbId: string, id: string): { success: boolean; message?: string } {
  const store = getStore(kbId)
  const target = store.find((s) => s.id === id)
  if (!target) return { success: false, message: '策略不存在' }
  if (target.isDefault) return { success: false, message: '不能删除默认策略' }
  const idx = store.findIndex((s) => s.id === id)
  store.splice(idx, 1)
  return { success: true }
}

/** 设为默认（保证 ≤1 default 不变量） */
export function setDefault(kbId: string, id: string): { success: boolean; message?: string } {
  const store = getStore(kbId)
  const target = store.find((s) => s.id === id)
  if (!target) return { success: false, message: '策略不存在' }
  store.forEach((s) => {
    s.isDefault = s.id === id
    s.updatedAt = now()
  })
  return { success: true }
}

/** 按扩展名匹配策略（优先非默认） */
export function resolveByExtension(kbId: string, extension: string): ParseStrategy | null {
  const store = getStore(kbId)
  const ext = extension.toLowerCase()
  // 先找非默认的精确匹配
  const matched = store.find((s) => !s.isDefault && s.extensions.includes(ext))
  if (matched) return { ...matched }
  // 再找默认策略
  const def = store.find((s) => s.isDefault)
  return def ? { ...def } : null
}

/** 检测扩展名冲突：返回与给定策略（排除自身）扩展名重叠的其他策略 */
export function detectConflicts(
  kbId: string,
  extensions: string[],
  excludeId?: string,
): ParseStrategy[] {
  const store = getStore(kbId)
  const extSet = new Set(normalizeExtensions(extensions))
  return store
    .filter((s) => s.id !== excludeId)
    .filter((s) => s.extensions.some((e) => extSet.has(e)))
}

/** 扩展名归一化：小写、确保以 . 开头 */
function normalizeExtensions(exts: string[]): string[] {
  return exts
    .map((e) => (e.startsWith('.') ? e : `.${e}`).toLowerCase())
    .filter((e, i, arr) => arr.indexOf(e) === i) // 去重
}
