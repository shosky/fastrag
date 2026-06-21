import { createMockAxios } from '@/mock/interceptor'
import type { GraphExpansionResult, ParseStrategy, ParseStrategyForm } from '@/types/knowledge'
import { mockRecognizeEntities, mockQueryGraph } from '@/mock/graph-expansion'
import type {
  GraphData,
  GraphStats,
  Benchmark,
  BenchmarkQuestion,
  BenchmarkGenerateConfig,
  Evaluation,
  EvaluationDetail,
  EvaluationStartConfig,
  SearchResultItem,
  RetrievalRequest,
} from '@/types/evaluation'
import { getGraphData, getGraphStats } from '@/mock/knowledge-graph'
import { searchChunks as mockSearchChunks, getChunkCount } from '@/mock/chunks'
import {
  getBenchmarks as mockGetBenchmarks,
  getBenchmarkDetail as mockGetBenchmarkDetail,
  createBenchmark as mockCreateBenchmark,
  generateBenchmark as mockGenerateBenchmark,
  deleteBenchmark as mockDeleteBenchmark,
} from '@/mock/benchmark'
import {
  getEvaluations as mockGetEvaluations,
  runEvaluation as mockRunEvaluation,
  deleteEvaluation as mockDeleteEvaluation,
  cacheEvaluationDetail,
} from '@/mock/evaluation'
import {
  getStrategies as mockGetStrategies,
  createStrategy as mockCreateStrategy,
  updateStrategy as mockUpdateStrategy,
  deleteStrategy as mockDeleteStrategy,
  setDefault as mockSetDefault,
  detectConflicts as mockDetectConflicts,
} from '@/mock/parse-strategy'
import {
  getFiles as mockGetFiles,
  getFolders as mockGetFolders,
  createFolder as mockCreateFolder,
  findFolderName as mockFindFolderName,
} from '@/mock/files'
import type { FolderNode } from '@/mock/files'
import { addRetrievalLog } from '@/mock/kb-logs'

const request = createMockAxios()

// ===========================================================================
// 图谱扩展 API（mock 实现）
// ===========================================================================
export async function expandQueryWithGraph(
  knowledgeId: string,
  query: string
): Promise<GraphExpansionResult> {
  await new Promise(r => setTimeout(r, 100))

  const entities = mockRecognizeEntities(query)

  if (entities.length === 0) {
    return {
      entities: [],
      relations: [],
      expandedQuery: query,
    }
  }

  const allEntities: GraphExpansionResult['entities'] = []
  const allRelations: GraphExpansionResult['relations'] = []

  for (const entity of entities) {
    const result = mockQueryGraph(entity)
    if (result) {
      allEntities.push(...result.entities)
      allRelations.push(...result.relations)
    }
  }

  const uniqueEntities = [...new Set(allEntities.map(e => e.name))]

  return {
    entities: uniqueEntities.map((name, i) => ({
      id: String(i + 1),
      name,
      type: '扩展实体',
    })),
    relations: allRelations,
    expandedQuery: uniqueEntities.length > 0
      ? `${query} ${uniqueEntities.join(' ')}`
      : query,
  }
}

// ===========================================================================
// 知识图谱 API
// ===========================================================================
export async function fetchGraphData(kbId: string): Promise<GraphData> {
  await new Promise(r => setTimeout(r, 200))
  return getGraphData(kbId)
}

export async function fetchGraphStats(kbId: string): Promise<GraphStats> {
  await new Promise(r => setTimeout(r, 150))
  return getGraphStats(kbId)
}

export async function fetchChunkCount(kbId: string): Promise<number> {
  await new Promise(r => setTimeout(r, 100))
  return getChunkCount(kbId)
}

// ===========================================================================
// 检索 API
// ===========================================================================
export async function searchRetrieval(req: RetrievalRequest): Promise<SearchResultItem[]> {
  const start = Date.now()
  await new Promise(r => setTimeout(r, 600))
  const results = mockSearchChunks(req.query, req.config, req.knowledgeId)
  const duration = Date.now() - start

  // 写入检索日志
  addRetrievalLog(
    req.knowledgeId || 'default',
    req.query,
    `命中 ${results.length} 条，耗时 ${(duration / 1000).toFixed(1)}s`,
    {
      query: req.query,
      mode: req.config.mode,
      topK: req.config.topK,
      hits: results.length,
      duration,
    },
  )

  return results
}

// ===========================================================================
// 评估基准 API
// ===========================================================================
export async function fetchBenchmarks(kbId: string): Promise<Benchmark[]> {
  await new Promise(r => setTimeout(r, 200))
  return mockGetBenchmarks(kbId)
}

export async function fetchBenchmarkDetail(
  kbId: string,
  benchId: string,
): Promise<BenchmarkQuestion[]> {
  await new Promise(r => setTimeout(r, 200))
  return mockGetBenchmarkDetail(kbId, benchId)
}

export async function createBenchmarkApi(
  kbId: string,
  form: { name: string; description: string },
  questionCount: number,
): Promise<Benchmark> {
  await new Promise(r => setTimeout(r, 1200))
  return mockCreateBenchmark(kbId, form, questionCount)
}

export async function generateBenchmarkApi(
  kbId: string,
  config: BenchmarkGenerateConfig,
): Promise<Benchmark> {
  await new Promise(r => setTimeout(r, 1800))
  return mockGenerateBenchmark(kbId, config)
}

export async function deleteBenchmarkApi(kbId: string, benchId: string): Promise<void> {
  await new Promise(r => setTimeout(r, 200))
  mockDeleteBenchmark(kbId, benchId)
}

// ===========================================================================
// RAG 评估 API
// ===========================================================================
export async function fetchEvaluations(kbId: string): Promise<Evaluation[]> {
  await new Promise(r => setTimeout(r, 200))
  return mockGetEvaluations(kbId)
}

export async function runEvaluationApi(
  kbId: string,
  config: EvaluationStartConfig,
): Promise<EvaluationDetail> {
  await new Promise(r => setTimeout(r, 1500))
  const detail = mockRunEvaluation(kbId, config)
  cacheEvaluationDetail(detail.id, detail)
  return detail
}

export async function deleteEvaluationApi(kbId: string, evalId: string): Promise<void> {
  await new Promise(r => setTimeout(r, 200))
  mockDeleteEvaluation(kbId, evalId)
}

// ===========================================================================
// 解析策略 API
// ===========================================================================
export async function fetchStrategies(kbId: string): Promise<ParseStrategy[]> {
  await new Promise(r => setTimeout(r, 200))
  return mockGetStrategies(kbId)
}

export async function createStrategyApi(kbId: string, form: ParseStrategyForm): Promise<ParseStrategy> {
  await new Promise(r => setTimeout(r, 400))
  return mockCreateStrategy(kbId, form)
}

export async function updateStrategyApi(kbId: string, id: string, form: ParseStrategyForm): Promise<ParseStrategy | null> {
  await new Promise(r => setTimeout(r, 400))
  return mockUpdateStrategy(kbId, id, form)
}

export async function deleteStrategyApi(kbId: string, id: string): Promise<{ success: boolean; message?: string }> {
  await new Promise(r => setTimeout(r, 300))
  return mockDeleteStrategy(kbId, id)
}

export async function setDefaultStrategyApi(kbId: string, id: string): Promise<{ success: boolean; message?: string }> {
  await new Promise(r => setTimeout(r, 300))
  return mockSetDefault(kbId, id)
}

export async function detectStrategyConflictsApi(
  kbId: string,
  extensions: string[],
  excludeId?: string,
): Promise<ParseStrategy[]> {
  await new Promise(r => setTimeout(r, 150))
  return mockDetectConflicts(kbId, extensions, excludeId)
}

// ===========================================================================
// 文件夹 API
// ===========================================================================
export async function fetchFolders(kbId: string): Promise<FolderNode[]> {
  await new Promise(r => setTimeout(r, 200))
  return mockGetFolders(kbId)
}

export async function createFolderApi(kbId: string, name: string, parentId: string = 'root'): Promise<void> {
  await new Promise(r => setTimeout(r, 300))
  mockCreateFolder(kbId, name, parentId)
}

export async function fetchFolderName(kbId: string, folderId: string): Promise<string> {
  await new Promise(r => setTimeout(r, 100))
  return mockFindFolderName(kbId, folderId)
}

export default request
