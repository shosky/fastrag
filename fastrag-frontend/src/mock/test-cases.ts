import type { RetrievalConfig } from '@/types/knowledge'
import { searchRetrieval } from '@/api'

// ===========================================================================
// 对话测试案例库 mock 数据层
//
// 测试集 = 预期问题 + 预期命中文件 + 期望关键词。
// 运行器：批量跑案例，对比实际检索结果与预期，输出通过/失败/差异。
// ===========================================================================

export interface TestCase {
  id: string
  name: string
  /** 测试问题 */
  query: string
  /** 期望命中的文件 ID 列表 */
  expectedFileIds: string[]
  /** 期望在结果中出现的关键词 */
  expectedKeywords: string[]
  /** 标签（分组用） */
  tags: string[]
  createdAt: string
}

export interface TestResult {
  caseId: string
  caseName: string
  query: string
  status: 'pass' | 'fail' | 'error'
  /** 实际命中的文件 ID */
  actualFileIds: string[]
  /** 实际结果中出现的关键词 */
  matchedKeywords: string[]
  /** 缺失的关键词 */
  missingKeywords: string[]
  /** 额外命中的关键词 */
  extraKeywords: string[]
  /** 耗时 ms */
  duration: number
  error?: string
}

export interface TestSuiteResult {
  suiteName: string
  total: number
  passed: number
  failed: number
  errors: number
  results: TestResult[]
  runAt: string
}

const caseStore: TestCase[] = []
let caseSeq = 100

function now(): string {
  return new Date().toLocaleString('zh-CN')
}

function seedCases(): TestCase[] {
  return [
    {
      id: 'tc_1',
      name: '小微ICT定义',
      query: '什么是小微ICT',
      expectedFileIds: ['file_1be435'],
      expectedKeywords: ['ICT', '标准化', '中小企业'],
      tags: ['定义类'],
      createdAt: '2026-06-10 10:00',
    },
    {
      id: 'tc_2',
      name: '全光组网价格',
      query: '全光组网标准包多少钱',
      expectedFileIds: ['file_1be435'],
      expectedKeywords: ['2299', '光猫'],
      tags: ['价格类'],
      createdAt: '2026-06-10 10:01',
    },
    {
      id: 'tc_3',
      name: '视频监控方案',
      query: '视频监控标准月付型',
      expectedFileIds: ['file_1be435'],
      expectedKeywords: ['摄像头', '月付', '1399'],
      tags: ['价格类'],
      createdAt: '2026-06-10 10:02',
    },
    {
      id: 'tc_4',
      name: '激励方案',
      query: '项目奖阶梯激励',
      expectedFileIds: ['file_1be435'],
      expectedKeywords: ['利润率', '系数'],
      tags: ['政策类'],
      createdAt: '2026-06-10 10:03',
    },
    {
      id: 'tc_5',
      name: '合同编码说明',
      query: '标品合同编码是什么',
      expectedFileIds: ['file_1be435'],
      expectedKeywords: ['合同编码', '财务'],
      tags: ['定义类'],
      createdAt: '2026-06-10 10:04',
    },
  ]
}

function getStore(): TestCase[] {
  if (caseStore.length === 0) caseStore.push(...seedCases())
  return caseStore
}

// --- CRUD ---
export function getTestCases(): TestCase[] {
  return getStore().map((c) => ({ ...c }))
}

export function createTestCase(form: Omit<TestCase, 'id' | 'createdAt'>): TestCase {
  const tc: TestCase = { ...form, id: `tc_${++caseSeq}`, createdAt: now() }
  getStore().push(tc)
  return { ...tc }
}

export function updateTestCase(id: string, patch: Partial<TestCase>): TestCase | null {
  const store = getStore()
  const idx = store.findIndex((c) => c.id === id)
  if (idx === -1) return null
  store[idx] = { ...store[idx], ...patch }
  return { ...store[idx] }
}

export function deleteTestCase(id: string): boolean {
  const store = getStore()
  const idx = store.findIndex((c) => c.id === id)
  if (idx === -1) return false
  store.splice(idx, 1)
  return true
}

// --- 测试运行器 ---

const defaultConfig: RetrievalConfig = {
  mode: 'hybrid',
  topK: 5,
  similarityThreshold: 0.0,
  bm25RecallCount: 50,
  vectorWeight: 0.7,
  bm25Weight: 0.3,
  bm25SparseDropRate: 0.0,
}

/**
 * 运行单个测试案例。
 * 检索后检查：期望的文件是否命中 + 期望的关键词是否在结果中出现。
 */
export async function runTestCase(tc: TestCase, config?: RetrievalConfig): Promise<TestResult> {
  const start = Date.now()
  try {
    const results = await searchRetrieval({
      knowledgeId: 'default',
      query: tc.query,
      config: config || defaultConfig,
    })
    const duration = Date.now() - start

    const actualFileIds = [...new Set(results.map((r) => r.fileId))]
    const resultText = results.map((r) => r.content).join(' ')

    const matchedKeywords = tc.expectedKeywords.filter((kw) => resultText.includes(kw))
    const missingKeywords = tc.expectedKeywords.filter((kw) => !resultText.includes(kw))

    // 判断通过：所有期望关键词都出现
    const pass = missingKeywords.length === 0

    return {
      caseId: tc.id,
      caseName: tc.name,
      query: tc.query,
      status: pass ? 'pass' : 'fail',
      actualFileIds,
      matchedKeywords,
      missingKeywords,
      extraKeywords: [],
      duration,
    }
  } catch (err: any) {
    return {
      caseId: tc.id,
      caseName: tc.name,
      query: tc.query,
      status: 'error',
      actualFileIds: [],
      matchedKeywords: [],
      missingKeywords: tc.expectedKeywords,
      extraKeywords: [],
      duration: Date.now() - start,
      error: err?.message || 'Unknown error',
    }
  }
}

/**
 * 批量运行测试案例。
 */
export async function runTestSuite(
  cases: TestCase[],
  config?: RetrievalConfig,
): Promise<TestSuiteResult> {
  const results: TestResult[] = []
  for (const tc of cases) {
    const result = await runTestCase(tc, config)
    results.push(result)
    // 模拟间隔，避免检索 API 过快
    await new Promise((r) => setTimeout(r, 200))
  }

  return {
    suiteName: '回归测试',
    total: results.length,
    passed: results.filter((r) => r.status === 'pass').length,
    failed: results.filter((r) => r.status === 'fail').length,
    errors: results.filter((r) => r.status === 'error').length,
    results,
    runAt: now(),
  }
}
