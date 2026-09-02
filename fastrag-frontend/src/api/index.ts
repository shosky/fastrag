import request from '@/utils/request'
import type { GraphData, GraphStats, PprRankItem } from '@/types/evaluation'
import type { AiChunkApplyPayload, GraphExpansionResult, ParseStrategy, ParseStrategyForm, ParseStrategyMeta, KnowledgeBase } from '@/types/knowledge'
import type { ChatSession, UserFeedback, FeedbackOverview, FeedbackStatistics } from '@/types/feedback'
import type { ModelMonitorOverview } from '@/types/monitor'

// ===========================================================================
// 首页 API
// ===========================================================================

export async function getHomeData() {
  return request.get('/home')
}

// ===========================================================================
// 运营分析 API
// ===========================================================================

export async function getKbAnalytics() {
  return request.get('/analytics/kb')
}
import type {
  Benchmark,
  BenchmarkQuestion,
  BenchmarkGenerateConfig,
  Evaluation,
  EvaluationDetail,
  EvaluationStartConfig,
  SearchResultItem,
  RetrievalRequest,
} from '@/types/evaluation'
import type { FolderNode } from '@/mock/files'

// ===========================================================================
// 认证 API
// ===========================================================================

export async function login(username: string, password: string) {
  return request.post('/auth/login', { username, password })
}

export async function getUserInfo() {
  return request.get('/auth/userinfo')
}

export async function logout() {
  return request.post('/auth/logout')
}

export async function sendCode(email: string, purpose: string) {
  return request.post('/auth/send-code', { email, purpose })
}

export async function register(data: { username: string; email: string; password: string; code: string }) {
  return request.post('/auth/register', data)
}

export async function resetPassword(data: { email: string; code: string; newPassword: string }) {
  return request.post('/auth/reset-password', data)
}

export async function getWechatQrScene() {
  return request.get('/auth/wechat/qr-scene')
}

export async function pollWechatQrStatus(scene: string) {
  return request.get('/auth/wechat/qr-status', { params: { scene } })
}

// ===========================================================================
// 知识库 API
// ===========================================================================

export async function getKnowledgeBases(params?: { keyword?: string; category?: string; page?: number; pageSize?: number }) {
  return request.get<unknown, { list: KnowledgeBase[]; total: number }>('/kb', { params })
}

export async function getKnowledgeBaseCategories() {
  return request.get('/kb/categories')
}
export async function getKbCategories() {
  return request.get('/kb-categories')
}
export async function createKbCategory(data: Record<string, unknown>) {
  return request.post('/kb-categories', data)
}
export async function updateKbCategory(id: string, data: Record<string, unknown>) {
  return request.put(`/kb-categories/${id}`, data)
}
export async function deleteKbCategory(id: string) {
  return request.delete(`/kb-categories/${id}`)
}

export async function getAllKbTags(kbId?: string) {
  return request.get('/kb-tags', { params: { kbId } })
}

// ===========================================================================
// 知识库标签 CRUD（分册四：KbTagController 写端点激活）
// ===========================================================================

export async function createKbTag(data: { kbId: string; name: string; color?: string; description?: string; tagTypeId?: string }) {
  return request.post('/kb-tags', data)
}
export async function updateKbTag(id: string, data: { name?: string; color?: string; description?: string; tagTypeId?: string }) {
  return request.put(`/kb-tags/${id}`, data)
}
export async function deleteKbTag(id: string) {
  return request.delete(`/kb-tags/${id}`)
}

export async function getKnowledgeBaseDetail(id: string) {
  return request.get(`/kb/${id}`)
}

export async function createKnowledgeBase(data: Record<string, unknown>) {
  return request.post('/kb', data)
}

export async function updateKnowledgeBase(id: string, data: Record<string, unknown>) {
  return request.put(`/kb/${id}`, data)
}

export async function deleteKnowledgeBase(id: string) {
  return request.delete(`/kb/${id}`)
}

// ===========================================================================
// 文件 API
// ===========================================================================

export async function getFiles(kbId: string, params?: { fileId?: string; page?: number; pageSize?: number }) {
  return request.get(`/kb/${kbId}/files`, { params })
}

export async function getDeletedFiles(kbId: string) {
  return request.get(`/kb/${kbId}/files/deleted`)
}

export async function uploadFile(kbId: string, formData: FormData) {
  return request.post(`/kb/${kbId}/files`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export async function processFile(kbId: string, fileId: string, config?: Record<string, unknown>) {
  return request.post(`/kb/${kbId}/files/${fileId}/process`, config)
}

export async function updateFile(kbId: string, fileId: string, data: Record<string, unknown>) {
  return request.put(`/kb/${kbId}/files/${fileId}`, data)
}

export async function retryFile(kbId: string, fileId: string) {
  return request.post(`/kb/${kbId}/files/${fileId}/retry`)
}

export async function deleteFile(kbId: string, fileId: string) {
  return request.delete(`/kb/${kbId}/files/${fileId}`)
}

export async function restoreFile(kbId: string, fileId: string) {
  return request.post(`/kb/${kbId}/files/${fileId}/restore`)
}

export async function permanentDeleteFile(kbId: string, fileId: string) {
  return request.delete(`/kb/${kbId}/files/${fileId}/permanent`)
}

export async function emptyRecycleBin(kbId: string) {
  return request.delete(`/kb/${kbId}/files/recycle-bin`)
}

export async function copyFile(kbId: string, fileId: string) {
  return request.post(`/kb/${kbId}/files/${fileId}/copy`)
}

export async function moveFileToKb(kbId: string, fileId: string, targetKbId: string, targetFolderId?: string) {
  return request.post(`/kb/${kbId}/files/${fileId}/move`, { targetKbId, targetFolderId: targetFolderId || null })
}

export async function getFileProcessingStatus(kbId: string, fileId: string) {
  return request.get(`/kb/${kbId}/files/${fileId}/processing-status`)
}

export async function previewFileChunks(
  kbId: string,
  fileId: string,
  strategyId?: string,
  customConfig?: { parseMethod?: string; advanced?: Record<string, unknown> },
) {
  const params: Record<string, string> = {}
  if (strategyId) params.strategyId = strategyId
  // 自定义临时策略预览（未落库）：{parseMethod, advanced}，非空时优先于 strategyId
  if (customConfig && (customConfig.parseMethod || customConfig.advanced)) {
    params.customConfig = JSON.stringify(customConfig)
  }
  return request.get(`/kb/${kbId}/files/${fileId}/preview`, { params: Object.keys(params).length ? params : undefined })
}

/**
 * 保存文件级专属自定义解析/分片策略并重新分片（原子完成）。
 * body 结构同解析策略表单：name/description/parseMethod/extensions/advanced/llmModel
 */
export async function saveFileStrategy(
  kbId: string,
  fileId: string,
  data: {
    name?: string
    description?: string
    parseMethod: string
    extensions: string[]
    advanced?: Record<string, unknown>
    llmModel?: string
  },
) {
  return request.post(`/kb/${kbId}/files/${fileId}/strategy`, data)
}

export async function downloadFile(kbId: string, fileId: string) {
  return request.get(`/kb/${kbId}/files/${fileId}/download`, { responseType: 'blob' })
}

// ===========================================================================
// AI分片 API（预览 + OCR + 语义分片 + 结构级跨页合并）
// ===========================================================================

/** AI分片 同步预览：chunk=false 仅返回段落对齐模型（渲染左侧原件，不触发 LLM 分片）；chunk=true 含自动分片结果（SSE 不可用时兜底） */
export async function aiChunkPreview(kbId: string, fileId: string, chunk = false) {
  // 扫描件/PDF 全本 OCR 同步执行，耗时可达分钟级——单独放宽到 5 分钟
  // （axios 实例全局 30s 超时会把慢 OCR 误杀成 timeout，后端实际仍在继续）
  return request.post(`/kb/${kbId}/files/${fileId}/ai-chunk-preview`, null, {
    params: { chunk },
    timeout: 300_000,
  })
}

/** AI分片 应用：提交用户确认的分片列表；后端尊重边界只做向量化与落库（不重新切分、不重新解析原文件） */
export async function aiChunkApply(kbId: string, fileId: string, payload: AiChunkApplyPayload) {
  // 落库含全量分片向量化（embedding API 逐/批调用）+ Milvus 写入 + parsed 工件写盘，
  // 分片多/文本长时可达分钟级——放宽到 5 分钟（全局 30s 会误报 timeout，后端实际仍在继续）
  return request.post(`/kb/${kbId}/files/${fileId}/ai-chunk-apply`, payload, {
    timeout: 300_000,
  })
}

/**
 * AI分片 SSE 流地址（GET {id}/ai-chunk/stream）。供 fetch + ReadableStream 使用（fetch 不走
 * axios baseURL，故此处含 /api 前缀；事件协议见设计文档 §7.2）。
 */
export function aiChunkStreamUrl(kbId: string, fileId: string) {
  return `/api/kb/${kbId}/files/${fileId}/ai-chunk/stream`
}

/**
 * 版面分析 SSE 流地址（GET {id}/ai-chunk/layout）。layout 事件逐页推送内容块
 * （类型 + 归一化 bbox），done 收尾；MinIO 缓存命中时一次全量推送。
 */
export function aiChunkLayoutStreamUrl(kbId: string, fileId: string) {
  return `/api/kb/${kbId}/files/${fileId}/ai-chunk/layout`
}

// ===========================================================================
// 文件元数据管理 API（分册四 rag-file-metadata-management.md）
// ===========================================================================

/** 查看文件元数据（固定字段 + 标签 + 自定义属性 + 状态） */
export async function getFileMetadata(kbId: string, fileId: string) {
  return request.get(`/kb/${kbId}/files/${fileId}/metadata`)
}

/** 全量更新文件元数据（schema 驱动：values 按字段名提交，检索感知字段自动投影；写入即视为人工校订 revised/manual） */
export async function updateFileMetadata(
  kbId: string,
  fileId: string,
  data: {
    values: Record<string, unknown>
  },
) {
  return request.put(`/kb/${kbId}/files/${fileId}/metadata`, data)
}

/** 替换式设置单文件标签 */
export async function setFileTags(kbId: string, fileId: string, tagIds: string[]) {
  return request.put(`/kb/${kbId}/files/${fileId}/tags`, { tagIds })
}

/** 批量打标（增量加/删） */
export async function batchSetFileTags(
  kbId: string,
  data: { fileIds: string[]; addTagIds?: string[]; removeTagIds?: string[] },
) {
  return request.post(`/kb/${kbId}/files/batch-tags`, data)
}

/** 存量文件元数据补抽（规则抽取；仅覆盖 none/partial，force=true 强制重抽） */
export async function extractFileMetadata(kbId: string, fileIds: string[], force = false) {
  return request.post(`/kb/${kbId}/files/metadata/extract`, { fileIds, force })
}

// ===========================================================================
// 文件夹 API
// ===========================================================================

export async function fetchFolders(kbId: string): Promise<FolderNode[]> {
  return request.get(`/kb/${kbId}/folders`)
}

export async function createFolderApi(kbId: string, name: string, parentId?: string | null): Promise<void> {
  // 根级文件夹传 null（后端 buildTree 只认 null 为根节点）
  const pid = (!parentId || parentId === 'root') ? null : parentId
  return request.post(`/kb/${kbId}/folders`, { name, parentId: pid })
}

export async function renameFolderApi(kbId: string, folderId: string, name: string): Promise<void> {
  return request.put(`/kb/${kbId}/folders/${folderId}`, { name })
}

export async function deleteFolderApi(kbId: string, folderId: string): Promise<void> {
  return request.delete(`/kb/${kbId}/folders/${folderId}`)
}

export async function fetchFolderName(kbId: string, folderId: string): Promise<string> {
  return request.get(`/kb/${kbId}/folders/${folderId}/name`)
}

// ===========================================================================
// Chunks API
// ===========================================================================

export async function getChunks(kbId: string, params?: { fileId?: string; page?: number; pageSize?: number }) {
  return request.get(`/kb/${kbId}/chunks`, { params })
}

// ===========================================================================
// 文件 Markdown 全文 API（解析阶段落盘的完整 markdown，供分片管理页读写）
// ===========================================================================

export async function getKbMarkdown(kbId: string, fileId: string): Promise<string> {
  return request.get(`/kb/${kbId}/files/${fileId}/markdown`)
}

export async function saveKbMarkdown(
  kbId: string,
  fileId: string,
  markdown: string,
  options?: { rechunk?: boolean },
) {
  return request.put(`/kb/${kbId}/files/${fileId}/markdown`, markdown, {
    params: { rechunk: options?.rechunk ?? false },
    headers: { 'Content-Type': 'text/markdown; charset=utf-8' },
  })
}

export async function fetchChunkCount(kbId: string): Promise<number> {
  return request.get(`/kb/${kbId}/chunks/count`)
}

export async function getChunk(kbId: string, chunkId: string) {
  return request.get(`/kb/${kbId}/chunks/${chunkId}`)
}

export async function createChunk(kbId: string, data: {
  fileId: string
  content: string
  insertAfterIndex?: number
  startTime?: number
  endTime?: number
  chunkType?: string
  pageNumber?: number
}) {
  return request.post(`/kb/${kbId}/chunks`, data)
}

export async function updateChunk(kbId: string, chunkId: string, data: Record<string, any>) {
  return request.put(`/kb/${kbId}/chunks/${chunkId}`, data)
}

export async function deleteChunk(kbId: string, chunkId: string) {
  return request.delete(`/kb/${kbId}/chunks/${chunkId}`)
}

export async function batchDeleteChunks(kbId: string, ids: string[]) {
  return request.post(`/kb/${kbId}/chunks/batch-delete`, ids)
}

/**
 * 重新分片：删除旧分片与向量后重跑 解析→分片→向量化。
 * @param config.strategyId 三态：缺省=沿用当前绑定；非空 id=换绑重切；空串=清除覆盖回自动匹配重切
 * @param config.presetStrategy 预设策略 key（如 structure_aware）：仅当未传 strategyId 时生效，
 *   由服务端自动确保文件绑定该策略（「按结构分片」一键入口）
 */
export async function reChunkFile(kbId: string, fileId: string, config?: { strategyId?: string; presetStrategy?: string }) {
  return request.post(`/kb/${kbId}/files/${fileId}/re-chunk`, config)
}

// ===========================================================================
// OnlyOffice Document Server API
// ===========================================================================

import type { OnlyOfficeConfig } from '@/types/onlyoffice'

/**
 * 拉取 OnlyOffice 编辑器配置（已签名）。
 * viewer 角色 → config.editorConfig.mode = 'view'
 * editor 角色 → config.editorConfig.mode = 'edit'（保存触发重分片）
 */
export async function getOnlyOfficeConfig(kbId: string, fileId: string): Promise<OnlyOfficeConfig> {
  return request.get(`/kb/${kbId}/files/${fileId}/onlyoffice/config`)
}

/**
 * 手动保存：对当前 OO 编辑会话执行 forcesave。
 * 受理后 OO 以 status=6 回调后端 → 落盘 + 触发重新分片。
 * 返回 result: initiated（已发起）/ no-changes（无修改）/ no-session（会话不存在）
 *        / no-file / disabled / failed
 */
export async function forceOnlyOfficeSave(
  kbId: string,
  fileId: string,
): Promise<{ result: string; ooError?: number; message?: string }> {
  return request.post(`/kb/${kbId}/files/${fileId}/onlyoffice/forcesave`)
}

/**
 * 区域内容提取（结构化优先，OCR 兜底）：
 * 文字层行命中区域 → 结构化块（表格/段落/标题，精确文本零 OCR）；
 * 区域内内容图片 → 裁剪调 OCR 模型；纯图形/扫描页 → 整区域渲染 OCR。
 * region 坐标为归一化 0~1（cropBox 顶左原点），与 ai-chunk-preview 返回的 imageBoxes 同坐标系。
 */
export async function aiChunkImageOcr(
  kbId: string,
  fileId: string,
  region: { page: number; x: number; y: number; width: number; height: number },
): Promise<{ blocks: Array<{ type: string; text: string }> }> {
  return request.post(`/kb/${kbId}/files/${fileId}/ai-chunk/image-ocr`, region)
}

// ===========================================================================
// QA 对 API
// ===========================================================================

export async function getQaPairs(kbId: string, params?: { fileId?: string; page?: number; pageSize?: number }) {
  return request.get(`/kb/${kbId}/qa-pairs`, { params })
}

export async function createQaPair(kbId: string, data: { question: string; answer: string }) {
  return request.post(`/kb/${kbId}/qa-pairs`, data)
}

export async function updateQaPair(kbId: string, id: string, data: { question: string; answer: string }) {
  return request.put(`/kb/${kbId}/qa-pairs/${id}`, data)
}

export async function deleteQaPair(kbId: string, id: string) {
  return request.delete(`/kb/${kbId}/qa-pairs/${id}`)
}

export async function confirmQaPair(kbId: string, id: string) {
  return request.post(`/kb/${kbId}/qa-pairs/${id}/confirm`)
}

// 下载问答对导入模板
export async function downloadQaImportTemplate(kbId: string) {
  return request.get(`/kb/${kbId}/qa-pairs/import-template`, { responseType: 'blob' })
}

// 批量导入问答对 Excel
export async function importQaPairs(kbId: string, file: File, overwrite: boolean) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('overwrite', String(overwrite))
  return request.post(`/kb/${kbId}/qa-pairs/import`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// ===========================================================================
// 解析策略 API
// ===========================================================================

export async function fetchParseStrategyTemplates() {
  return request.get('/parse-strategy-templates')
}

/** 解析方法（文档类型）元数据：类型 ↔ 扩展名 的唯一权威映射，来自后端 ParseMethodRegistry */
export async function getParseStrategyMeta(): Promise<ParseStrategyMeta> {
  return request.get('/parse-strategies/meta')
}

export async function fetchStrategies(kbId: string): Promise<ParseStrategy[]> {
  return request.get(`/kb/${kbId}/parse-strategies`)
}

export async function fetchStrategyDetail(kbId: string, id: string): Promise<ParseStrategy> {
  return request.get(`/kb/${kbId}/parse-strategies/${id}`)
}

export async function createStrategyApi(kbId: string, form: ParseStrategyForm): Promise<ParseStrategy> {
  return request.post(`/kb/${kbId}/parse-strategies`, form)
}

export async function updateStrategyApi(kbId: string, id: string, form: ParseStrategyForm): Promise<ParseStrategy> {
  return request.put(`/kb/${kbId}/parse-strategies/${id}`, form)
}

export async function deleteStrategyApi(kbId: string, id: string): Promise<void> {
  return request.delete(`/kb/${kbId}/parse-strategies/${id}`)
}

export async function setDefaultStrategyApi(kbId: string, id: string): Promise<void> {
  return request.post(`/kb/${kbId}/parse-strategies/${id}/set-default`)
}

export async function resolveStrategy(kbId: string, extension: string): Promise<ParseStrategy> {
  return request.get(`/kb/${kbId}/parse-strategies/resolve`, { params: { extension } })
}

export async function detectStrategyConflictsApi(
  kbId: string,
  extensions: string[],
  excludeId?: string,
): Promise<ParseStrategy[]> {
  return request.post(`/kb/${kbId}/parse-strategies/conflicts`, { extensions, excludeId })
}

// ===========================================================================
// 检索 API
// ===========================================================================

export async function searchRetrieval(req: RetrievalRequest): Promise<SearchResultItem[]> {
  return request.post('/retrieval/search', req)
}

export async function expandQueryWithGraph(
  kbId: string,
  query: string,
  depth: number = 2,
  maxEntities: number = 20,
): Promise<GraphExpansionResult> {
  return request.post('/graph/expand', { kbId, query, depth, maxEntities })
}

export async function querySuggest(query: string): Promise<string> {
  return request.post('/query/suggest', { query })
}

export async function expandSynonyms(query: string): Promise<string[]> {
  return request.post('/query/expand-synonyms', { query })
}

export async function applyQueryRules(query: string): Promise<string> {
  return request.post('/query-rules/apply', { query })
}

// ===========================================================================
// 知识图谱 API
// ===========================================================================

export async function fetchGraphData(kbId: string, excludeChunks: boolean = true): Promise<GraphData> {
  return request.get(`/kb/${kbId}/graph`, { params: { excludeChunks } })
}

/** 图谱邻居展开（检索增强）：以实体为种子展开 1-2 跳子图 */
export async function expandGraphSubgraph(
  kbId: string,
  query: string,
  depth: number = 2,
  maxEntities: number = 20,
): Promise<GraphData> {
  return request.post(`/graph/expand`, { kbId, query, depth, maxEntities })
}

export async function fetchGraphStats(kbId: string): Promise<GraphStats> {
  return request.get(`/kb/${kbId}/graph/stats`)
}

export async function getGraphNodeDetail(kbId: string, nodeId: string) {
  return request.get(`/kb/${kbId}/graph/nodes/${nodeId}`)
}

export async function getGraphIndexStatus(kbId: string) {
  return request.get(`/kb/${kbId}/graph/index`)
}

export async function buildGraphIndex(kbId: string, fileIds?: string[], mode?: string) {
  return request.post(`/kb/${kbId}/graph/index/build`, fileIds ? { fileIds, mode: mode || 'full' } : undefined)
}

export async function getGraphBuildStatus(kbId: string) {
  return request.get(`/kb/${kbId}/graph/index/build-status`)
}

export async function getGraphSettings(kbId: string) {
  return request.get(`/kb/${kbId}/graph/settings`)
}

export async function saveGraphSettings(kbId: string, settings: Record<string, unknown>) {
  return request.put(`/kb/${kbId}/graph/settings`, settings)
}

/** 关键词子图搜索 */
export async function searchGraphNodes(kbId: string, keyword: string, maxNodes: number = 50): Promise<GraphData> {
  return request.get(`/kb/${kbId}/graph/search`, { params: { keyword, maxNodes } })
}

/** 获取实体类型标签列表 */
export async function fetchGraphLabels(kbId: string): Promise<string[]> {
  return request.get(`/kb/${kbId}/graph/labels`)
}

/** 删除文件关联的图谱数据 */
export async function deleteFileGraph(kbId: string, fileId: string) {
  return request.delete(`/kb/${kbId}/graph/file/${fileId}`)
}

/** 重试图谱构建 */
export async function retryGraphBuild(kbId: string) {
  return request.post(`/kb/${kbId}/graph/index/retry`)
}

/** Personalized PageRank 排序（检索增强用） */
export async function rankChunksByPpr(
  kbId: string,
  entities: string[],
  topK: number = 10,
): Promise<PprRankItem[]> {
  return request.post(`/kb/${kbId}/graph/rank`, { entities, topK })
}

// ===========================================================================
// 评估基准 API
// ===========================================================================

export async function fetchBenchmarks(kbId: string): Promise<Benchmark[]> {
  return request.get(`/kb/${kbId}/benchmarks`)
}

export async function fetchBenchmarkDetail(kbId: string, benchId: string): Promise<BenchmarkQuestion[]> {
  return request.get(`/kb/${kbId}/benchmarks/${benchId}/questions`)
}

export async function createBenchmarkApi(
  kbId: string,
  form: { name: string; description: string },
  questionCount: number,
): Promise<Benchmark> {
  return request.post(`/kb/${kbId}/benchmarks`, { ...form, questionCount })
}

export async function generateBenchmarkApi(
  kbId: string,
  config: BenchmarkGenerateConfig,
): Promise<Benchmark> {
  return request.post(`/kb/${kbId}/benchmarks/generate`, config)
}

export async function deleteBenchmarkApi(kbId: string, benchId: string): Promise<void> {
  return request.delete(`/kb/${kbId}/benchmarks/${benchId}`)
}

// ===========================================================================
// RAG 评估 API
// ===========================================================================

export async function fetchEvaluations(kbId: string): Promise<Evaluation[]> {
  return request.get(`/kb/${kbId}/evaluations`)
}

export async function fetchEvaluationDetail(kbId: string, evalId: string): Promise<EvaluationDetail> {
  return request.get(`/kb/${kbId}/evaluations/${evalId}`)
}

export async function fetchEvaluationStatus(kbId: string, evalId: string): Promise<{ status: string }> {
  return request.get(`/kb/${kbId}/evaluations/${evalId}/status`)
}

export async function runEvaluationApi(
  kbId: string,
  config: EvaluationStartConfig,
): Promise<EvaluationDetail> {
  return request.post(`/kb/${kbId}/evaluations/run`, config)
}

export async function deleteEvaluationApi(kbId: string, evalId: string): Promise<void> {
  return request.delete(`/kb/${kbId}/evaluations/${evalId}`)
}

// ===========================================================================
// 发布审核 API
// ===========================================================================

export async function getVersions(kbId: string) {
  return request.get(`/kb/${kbId}/versions`)
}

export async function getLatestVersion(kbId: string) {
  return request.get(`/kb/${kbId}/versions/latest`)
}

export async function getPublishedVersion(kbId: string) {
  return request.get(`/kb/${kbId}/versions/published`)
}

export async function createVersion(kbId: string, data?: Record<string, unknown>) {
  return request.post(`/kb/${kbId}/versions`, data)
}

export async function transitionVersion(kbId: string, versionId: string, action: string) {
  return request.post(`/kb/${kbId}/versions/${versionId}/transition`, { action })
}

export async function getReviews(params?: { kbId?: string }) {
  return request.get('/reviews', { params })
}

export async function getPendingReviews() {
  return request.get('/reviews/pending')
}

export async function submitForReview(data: Record<string, unknown>) {
  return request.post('/reviews', data)
}

export async function approveReview(reviewId: string) {
  return request.post(`/reviews/${reviewId}/approve`)
}

export async function rejectReview(reviewId: string, reason?: string) {
  return request.post(`/reviews/${reviewId}/reject`, { reason })
}

// ===========================================================================
// 日志 API
// ===========================================================================

export async function getKbLogs(kbId: string, params?: { category?: string; keyword?: string; page?: number; pageSize?: number }) {
  return request.get(`/kb/${kbId}/logs`, { params })
}

export async function getKbLogStats(kbId: string) {
  return request.get(`/kb/${kbId}/logs/stats`)
}

export async function getKbUpdateLogs(kbId: string, params?: { type?: string; page?: number; pageSize?: number }) {
  return request.get(`/kb/${kbId}/update-logs`, { params })
}

export async function getKbUpdateLogsDiff(kbId: string) {
  return request.get(`/kb/${kbId}/update-logs/diff`)
}

// ===========================================================================
// 应用编排 API
// ===========================================================================

export async function getApps(params?: { keyword?: string; tag?: string }) {
  return request.get('/apps', { params })
}

export async function getAppDetail(id: string) {
  return request.get(`/apps/${id}`)
}

export async function createApp(data: Record<string, unknown>) {
  return request.post('/apps', data)
}

export async function updateApp(id: string, data: Record<string, unknown>) {
  return request.put(`/apps/${id}`, data)
}

export async function deleteApp(id: string) {
  return request.delete(`/apps/${id}`)
}

export async function getAppTemplates() {
  return request.get('/apps/templates')
}

export async function getAppConfig(id: string) {
  return request.get(`/apps/${id}/config`)
}

export async function saveAppConfig(id: string, config: Record<string, unknown>) {
  return request.put(`/apps/${id}/config`, config)
}

/** 保存系统提示词 */
export async function saveAppPrompt(appId: string, prompt: string) {
  return request.put(`/apps/${appId}/config/prompt`, { prompt })
}

/** 保存上下文压缩配置（阈值 + 提示词） */
export async function saveAppSummary(appId: string, data: { summaryThreshold: number; summaryPrompt: string }) {
  return request.put(`/apps/${appId}/config/summary`, data)
}

/** 保存最大轮数 */
export async function saveAppMaxTurns(appId: string, maxTurns: number) {
  return request.put(`/apps/${appId}/config/max-turns`, { maxTurns })
}

/** 保存最大执行步数 */
export async function saveAppMaxSteps(appId: string, maxSteps: number) {
  return request.put(`/apps/${appId}/config/max-steps`, { maxSteps })
}

/** 保存重试次数 */
export async function saveAppRetryTimes(appId: string, retryTimes: number) {
  return request.put(`/apps/${appId}/config/retry-times`, { retryTimes })
}

export async function runApp(id: string, query: string) {
  return request.post(`/apps/${id}/run`, { query })
}

// ===========================================================================
// 应用对话 API（流式 + 会话管理）
// ===========================================================================

/** 创建新会话 */
export async function createAppSession(appId: string) {
  return request.post(`/apps/${appId}/chat/sessions`)
}

/** 获取会话列表 */
export async function getAppSessions(appId: string) {
  return request.get(`/apps/${appId}/chat/sessions`)
}

/** 获取会话消息历史 */
export async function getAppSessionMessages(appId: string, sessionId: string) {
  return request.get(`/apps/${appId}/chat/sessions/${sessionId}/messages`)
}

/** 删除会话 */
export async function deleteAppSession(appId: string, sessionId: string) {
  return request.delete(`/apps/${appId}/chat/sessions/${sessionId}`)
}

/** 软删除单条消息 */
export async function deleteAppMessage(appId: string, messageId: string) {
  return request.delete(`/apps/${appId}/chat/messages/${messageId}`)
}

/** 消息反馈（like/dislike/null） */
export async function feedbackAppMessage(appId: string, messageId: string, feedback: string) {
  return request.post(`/apps/${appId}/chat/messages/${messageId}/feedback`, { feedback })
}

/** 编辑消息内容 */
export async function updateAppMessage(appId: string, messageId: string, content: string) {
  return request.put(`/apps/${appId}/chat/messages/${messageId}`, { content })
}

// ===========================================================================
// 工作流 API
// ===========================================================================

export async function getWorkflows() {
  return request.get('/workflows')
}

export async function getWorkflowDetail(id: string) {
  return request.get(`/workflows/${id}`)
}

export async function createWorkflow(data: Record<string, unknown>) {
  return request.post('/workflows', data)
}

export async function updateWorkflow(id: string, data: Record<string, unknown>) {
  return request.put(`/workflows/${id}`, data)
}

export async function deleteWorkflow(id: string) {
  return request.delete(`/workflows/${id}`)
}

export async function publishWorkflow(id: string) {
  return request.post(`/workflows/${id}/publish`)
}

export async function addWorkflowNode(id: string, data: Record<string, unknown>) {
  return request.post(`/workflows/${id}/nodes`, data)
}

export async function deleteWorkflowNode(id: string, nodeId: string) {
  return request.delete(`/workflows/${id}/nodes/${nodeId}`)
}

export async function addWorkflowEdge(id: string, data: Record<string, unknown>) {
  return request.post(`/workflows/${id}/edges`, data)
}

export async function deleteWorkflowEdge(id: string, edgeId: string) {
  return request.delete(`/workflows/${id}/edges/${edgeId}`)
}

// ===========================================================================
// 工具 API
// ===========================================================================

export async function getTools(params?: { keyword?: string; type?: string }) {
  return request.get('/tools', { params })
}

export async function getToolDetail(id: string) {
  return request.get(`/tools/${id}`)
}

export async function createTool(data: Record<string, unknown>) {
  return request.post('/tools', data)
}

export async function updateTool(id: string, data: Record<string, unknown>) {
  return request.put(`/tools/${id}`, data)
}

export async function deleteTool(id: string) {
  return request.delete(`/tools/${id}`)
}

export async function toggleTool(id: string) {
  return request.post(`/tools/${id}/toggle`)
}

/** 工具测试代理：后端转发 HTTP 请求，避免 CORS */
export async function testProxy(data: {
  method: string
  url: string
  headers: Record<string, string>
  body?: string
  timeout?: number
}) {
  return request.post('/tools/test-proxy', data)
}

// ===========================================================================
// 技能 API
// ===========================================================================

export async function getSkills(params?: { keyword?: string; category?: string }) {
  return request.get('/skills', { params })
}

export async function getSkillDetail(id: string) {
  return request.get(`/skills/${id}`)
}

export async function getSkillBySlug(slug: string) {
  return request.get(`/skills/slug/${slug}`)
}

/** 快速创建技能（只需 name/description/slug，其余字段自动生成） */
export async function createSkill(data: Record<string, unknown>) {
  return request.post('/skills', data)
}

export async function updateSkill(id: string, data: Record<string, unknown>) {
  return request.put(`/skills/${id}`, data)
}

export async function deleteSkill(id: string) {
  return request.delete(`/skills/${id}`)
}

export async function toggleSkill(id: string) {
  return request.post(`/skills/${id}/toggle`)
}

export async function setSkillEnabled(id: string, enabled: boolean) {
  return request.put(`/skills/${id}/enabled`, { enabled })
}

// -- 技能文件管理 --
export async function getSkillTree(slug: string) {
  return request.get(`/skills/${slug}/tree`)
}

export async function getSkillFile(slug: string, path: string) {
  return request.get(`/skills/${slug}/file`, { params: { path } })
}

export async function updateSkillFile(slug: string, path: string, content: string) {
  return request.put(`/skills/${slug}/file`, { path, content })
}

export async function createSkillFile(slug: string, path: string, isDir: boolean, content?: string) {
  return request.post(`/skills/${slug}/file`, { path, isDir, content })
}

export async function deleteSkillFile(slug: string, path: string) {
  return request.delete(`/skills/${slug}/file`, { params: { path } })
}

export async function exportSkillZip(slug: string) {
  return request.get(`/skills/${slug}/export`, { responseType: 'blob' })
}

// -- 依赖管理 --
export async function getSkillDependencies(id: string) {
  return request.get(`/skills/${id}/dependencies`)
}

export async function updateSkillDependencies(id: string, dependencies: any[]) {
  return request.put(`/skills/${id}/dependencies`, dependencies)
}

export async function getDependencyOptions(excludeSkillId?: string) {
  return request.get('/skills/dependency-options', { params: { excludeSkillId } })
}

// -- 分享配置 --
export async function updateSkillShareConfig(id: string, config: Record<string, any>) {
  return request.put(`/skills/${id}/share-config`, config)
}

// -- 草稿安装 --
export async function prepareSkillImport(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/skills/import/prepare', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export async function confirmSkillDraft(draftId: string, shareConfig?: Record<string, any>) {
  return request.post(`/skills/install-drafts/${draftId}/confirm`, { shareConfig })
}

export async function discardSkillDraft(draftId: string) {
  return request.delete(`/skills/install-drafts/${draftId}`)
}

// ===========================================================================
// MCP 服务 API
// ===========================================================================

export async function getMcpServices(params?: { keyword?: string }) {
  return request.get('/mcp-services', { params })
}

export async function getMcpServiceDetail(id: string) {
  return request.get(`/mcp-services/${id}`)
}

export async function createMcpService(data: Record<string, unknown>) {
  return request.post('/mcp-services', data)
}

export async function updateMcpService(id: string, data: Record<string, unknown>) {
  return request.put(`/mcp-services/${id}`, data)
}

export async function deleteMcpService(id: string) {
  return request.delete(`/mcp-services/${id}`)
}

export async function toggleMcpService(id: string) {
  return request.post(`/mcp-services/${id}/toggle`)
}

export async function getMcpServiceTools(id: string) {
  return request.get(`/mcp-services/${id}/tools`)
}

/** 解析 MCP URL：连接服务器发现工具，不持久化，用于创建前的「解析」按钮 */
export async function parseMcpUrl(data: Record<string, unknown>) {
  return request.post('/mcp-services/parse-url', data)
}

/** 刷新 MCP 服务：连接服务器、发现工具、更新状态 */
export async function refreshMcpService(id: string) {
  return request.post(`/mcp-services/${id}/refresh`)
}

/** 测试 MCP 工具调用 */
export async function testMcpTool(toolId: number, args: Record<string, unknown>) {
  return request.post(`/mcp-services/tools/${toolId}/test`, { arguments: args })
}

// ===========================================================================
// 模型 API
// ===========================================================================

export async function getModels(params?: { keyword?: string; purpose?: string }) {
  return request.get('/models', { params })
}

export async function getModelDetail(id: string) {
  return request.get(`/models/${id}`)
}

export async function createModel(data: Record<string, unknown>) {
  return request.post('/models', data)
}

export async function updateModel(id: string, data: Record<string, unknown>) {
  return request.put(`/models/${id}`, data)
}

export async function deleteModel(id: string) {
  return request.delete(`/models/${id}`)
}

// ===========================================================================
// 敏感词 API
// ===========================================================================

export async function getSensitiveWords() {
  return request.get('/sensitive-words')
}

export async function createSensitiveWord(data: Record<string, unknown>) {
  return request.post('/sensitive-words', data)
}

export async function updateSensitiveWord(id: string, data: Record<string, unknown>) {
  return request.put(`/sensitive-words/${id}`, data)
}

export async function deleteSensitiveWord(id: string) {
  return request.delete(`/sensitive-words/${id}`)
}

export async function downloadSensitiveWordTemplate() {
  return request.get('/sensitive-words/template', { responseType: 'blob' })
}

export async function importSensitiveWords(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/sensitive-words/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// ===========================================================================
// 字典 API
// ===========================================================================

export async function getDictionaries(params?: { type?: string }) {
  return request.get('/dictionaries', { params })
}

export async function getDictionaryTypes() {
  return request.get('/dictionaries/types')
}

export async function createDictionary(data: Record<string, unknown>) {
  return request.post('/dictionaries', data)
}

export async function updateDictionary(id: string, data: Record<string, unknown>) {
  return request.put(`/dictionaries/${id}`, data)
}

export async function deleteDictionary(id: string) {
  return request.delete(`/dictionaries/${id}`)
}

// ===========================================================================
// 安全策略 API
// ===========================================================================

export async function getSecurityPolicies(params?: { policyType?: string }) {
  return request.get('/security-policies', { params })
}
export async function createSecurityPolicy(data: Record<string, unknown>) {
  return request.post('/security-policies', data)
}
export async function updateSecurityPolicy(id: string, data: Record<string, unknown>) {
  return request.put(`/security-policies/${id}`, data)
}
export async function deleteSecurityPolicy(id: string) {
  return request.delete(`/security-policies/${id}`)
}

// ===========================================================================
// 发布策略 API
// ===========================================================================

export async function getPublishStrategies(params?: { strategyType?: string }) {
  return request.get('/publish-strategies', { params })
}
export async function createPublishStrategy(data: Record<string, unknown>) {
  return request.post('/publish-strategies', data)
}
export async function updatePublishStrategy(id: string, data: Record<string, unknown>) {
  return request.put(`/publish-strategies/${id}`, data)
}
export async function deletePublishStrategy(id: string) {
  return request.delete(`/publish-strategies/${id}`)
}

// ===========================================================================
// 术语 API
// ===========================================================================

export async function getTermLibraries() {
  return request.get('/terminology/libraries')
}

export async function createTermLibrary(data: Record<string, unknown>) {
  return request.post('/terminology/libraries', data)
}

export async function updateTermLibrary(id: string, data: Record<string, unknown>) {
  return request.put(`/terminology/libraries/${id}`, data)
}

export async function deleteTermLibrary(id: string) {
  return request.delete(`/terminology/libraries/${id}`)
}

export async function getTerms(params?: { libraryId?: string }) {
  return request.get('/terminology/terms', { params })
}

export async function createTerm(data: Record<string, unknown>) {
  return request.post('/terminology/terms', data)
}

export async function updateTerm(id: string, data: Record<string, unknown>) {
  return request.put(`/terminology/terms/${id}`, data)
}

export async function deleteTerm(id: string) {
  return request.delete(`/terminology/terms/${id}`)
}

// ===========================================================================
// 角色权限 API
// ===========================================================================

export async function getRoles() {
  return request.get('/roles')
}

export async function getRoleDetail(id: string) {
  return request.get(`/roles/${id}`)
}

export async function createRole(data: Record<string, unknown>) {
  return request.post('/roles', data)
}

export async function updateRole(id: string, data: Record<string, unknown>) {
  return request.put(`/roles/${id}`, data)
}

export async function deleteRole(id: string) {
  return request.delete(`/roles/${id}`)
}

export async function setDefaultRole(id: string) {
  return request.post(`/roles/${id}/set-default`)
}

export async function getPermissions() {
  return request.get('/permissions')
}

export async function getPermissionTree() {
  return request.get('/permissions/tree')
}

export async function createPermission(data: Record<string, unknown>) {
  return request.post('/permissions', data)
}

export async function updatePermission(id: number, data: Record<string, unknown>) {
  return request.put(`/permissions/${id}`, data)
}

export async function deletePermission(id: number) {
  return request.delete(`/permissions/${id}`)
}

// ===========================================================================
// 人员 API
// ===========================================================================

export async function getPersonnel(params?: { keyword?: string; page?: number; pageSize?: number }) {
  return request.get('/personnel', { params })
}

/** 轻量人员选项（共享设置/成员选择用）：登录即可访问，返回启用人员精简信息 */
export async function getPersonnelSimple() {
  return request.get('/personnel/simple')
}

export async function createPersonnel(data: Record<string, unknown>) {
  return request.post('/personnel', data)
}

export async function updatePersonnel(id: string, data: Record<string, unknown>) {
  return request.put(`/personnel/${id}`, data)
}

export async function assignRoles(personnelId: string, roleIds: string[]) {
  return request.post(`/personnel/${personnelId}/assign-roles`, { roleIds })
}

export async function updatePersonnelStatus(id: string, status: string) {
  return request.put(`/personnel/${id}/status`, { status })
}

export async function getPersonnelByUsername(username: string) {
  return request.get(`/personnel/by-username/${username}`)
}

// ===========================================================================
// 组织架构 API
// ===========================================================================

export async function getOrgTree() {
  return request.get('/org/tree')
}

export async function getOrgFlat() {
  return request.get('/org/flat')
}

export async function getDepartments() {
  return request.get('/org/departments')
}

export async function getDepartmentMembers(deptId: string) {
  return request.get(`/org/${deptId}/members`)
}

export async function createOrg(data: Record<string, unknown>) {
  return request.post('/org', data)
}

export async function updateOrg(id: string, data: Record<string, unknown>) {
  return request.put(`/org/${id}`, data)
}

export async function deleteOrg(id: string) {
  return request.delete(`/org/${id}`)
}

// ===========================================================================
// KB ACL API
// ===========================================================================

export async function getKbAcl(kbId: string) {
  return request.get(`/kb/${kbId}/acl`)
}

export async function setKbAcl(kbId: string, acl: Record<string, unknown>[]) {
  return request.put(`/kb/${kbId}/acl`, acl)
}

export async function addKbAclEntry(kbId: string, entry: Record<string, unknown>) {
  return request.post(`/kb/${kbId}/acl`, entry)
}

export async function removeKbAclEntry(kbId: string, userId: string) {
  return request.delete(`/kb/${kbId}/acl/${userId}`)
}

export async function getUserAccessibleKbs(userId: string) {
  return request.get(`/acl/users/${userId}/kbs`)
}

export async function getUserKbRole(userId: string, kbId: string) {
  return request.get(`/acl/users/${userId}/kbs/${kbId}/role`)
}

// ===========================================================================
// 审计日志 API
// ===========================================================================

export async function getAuditLogs(params?: { module?: string; limit?: number }) {
  return request.get('/audit/system-log', { params })
}

/**
 * 统一日志查询（支持多日志类型分页）
 */
export async function getLogs(params?: {
  category?: 'audit' | 'login' | 'operation'
  keyword?: string
  module?: string
  operator?: string
  kbId?: string
  page?: number
  pageSize?: number
}) {
  return request.get('/logs', { params })
}

/**
 * 查询登录日志
 */
export async function getLoginLogs(params?: { userId?: string; status?: string; limit?: number }) {
  return request.get('/audit/login-log', { params })
}

// ===========================================================================
// 反馈 API
// ===========================================================================

export async function getFeedback(params?: { kbId?: string }) {
  return request.get('/feedback', { params })
}

export async function submitFeedback(data: Record<string, unknown>) {
  return request.post('/feedback', data)
}

// ===========================================================================
// M1 知识库用户反馈（扩展）
// ===========================================================================

export async function getFeedbackPage(params?: { kbId?: string; feedback?: string; status?: string; page?: number; pageSize?: number }) {
  return request.get<unknown, { list: UserFeedback[]; total: number }>('/feedback', { params })
}

export async function getFeedbackStatistics(kbId?: string) {
  return request.get<unknown, FeedbackStatistics>('/feedback/statistics', { params: { kbId } })
}

export async function updateFeedback(id: number | string, data: Record<string, unknown>) {
  return request.put(`/feedback/${id}`, data)
}

export async function deleteFeedback(id: number | string) {
  return request.delete(`/feedback/${id}`)
}

export async function replyFeedback(id: number | string, data: { reply: string; operator?: string }) {
  return request.post(`/feedback/${id}/reply`, data)
}

export async function getFeedbackOverview(kbId?: string) {
  return request.get<unknown, FeedbackOverview>('/feedback/overview', { params: { kbId } })
}

export async function getChatSessions(params?: { keyword?: string; userId?: string; page?: number; pageSize?: number }) {
  return request.get<unknown, { list: ChatSession[]; total: number }>('/chat-sessions', { params })
}

// ===========================================================================
// M2 知识检索增强
// ===========================================================================

export async function getRetrievalLogs(params?: { kbId?: string; hasResult?: boolean; page?: number; pageSize?: number }) {
  return request.get('/retrieval/logs', { params })
}

export async function getRetrievalLogAnalysis(kbId?: string) {
  return request.get('/retrieval/logs/analysis', { params: { kbId } })
}

export async function updateRetrievalLog(id: number | string, data: Record<string, unknown>) {
  return request.put(`/retrieval/logs/${id}`, data)
}

export async function getUpdateRemindList(kbId?: string) {
  return request.get('/update-remind', { params: { kbId } })
}

export async function getKbUpdateRemind(kbId: string) {
  return request.get(`/kb/${kbId}/update-remind`)
}

export async function saveUpdateRemind(data: Record<string, unknown>) {
  return request.post('/update-remind', data)
}

export async function updateUpdateRemind(id: string, data: Record<string, unknown>) {
  return request.put(`/update-remind/${id}`, data)
}

export async function deleteUpdateRemind(id: string) {
  return request.delete(`/update-remind/${id}`)
}

// ===========================================================================
// M5 模型管理（扩展）
// ===========================================================================

export async function getToolApiConfig(toolId: string) {
  return request.get(`/tools/${toolId}/api-config`)
}

export async function saveToolApiConfig(toolId: string, data: Record<string, unknown>) {
  return request.put(`/tools/${toolId}/api-config`, data)
}

// ===========================================================================
// M5 模型管理（扩展）
// ===========================================================================

export async function toggleModel(id: string) {
  return request.post(`/models/${id}/toggle`)
}

export async function importModels(models: Record<string, unknown>[]) {
  return request.post('/models/import', models)
}

// ===========================================================================
// M6 数据挖掘
// ===========================================================================

export async function getDataMiningTasks(params?: { kbId?: string; keyword?: string }) {
  return request.get('/data-mining', { params })
}

export async function getDataMiningTask(id: string) {
  return request.get(`/data-mining/${id}`)
}

export async function createDataMiningTask(data: Record<string, unknown>) {
  return request.post('/data-mining', data)
}

export async function deleteDataMiningTask(id: string) {
  return request.delete(`/data-mining/${id}`)
}

export async function runDataMiningTask(id: string) {
  return request.post(`/data-mining/${id}/run`)
}

// ===========================================================================
// M14 系统设置管理
// ===========================================================================

export async function trainModel(modelId: string, data: Record<string, unknown>) {
  return request.post(`/models/${modelId}/train`, data)
}
export async function testModel(modelId: string, data: Record<string, unknown>) {
  return request.post(`/models/${modelId}/test`, data)
}
export async function testModelChat(modelId: string, prompt: string) {
  return request.post(`/models/${modelId}/test-chat`, { prompt })
}
export async function testModelEmbedding(modelId: string, text: string) {
  return request.post(`/models/${modelId}/test-embedding`, { text })
}
export async function testModelRerank(modelId: string, query: string, documents: string[]) {
  return request.post(`/models/${modelId}/test-rerank`, { query, documents })
}
export async function getModelTrainings(modelId: string) {
  return request.get(`/models/${modelId}/trainings`)
}
export async function getModelTestReports(modelId: string) {
  return request.get(`/models/${modelId}/test-reports`)
}
export async function exportModels(params?: { ids?: string; purpose?: string }) {
  return request.get('/models/export', { params })
}
export async function getSysConfigs(configType?: string) {
  return request.get('/config', { params: { configType } })
}
export async function getConfigHistory(configKey?: string) {
  return request.get('/config/history', { params: { configKey } })
}
export async function exportSysConfig(configType?: string) {
  return request.get('/config/export', { params: { configType } })
}
export async function getDefaultConfigs() {
  return request.get('/config/default')
}
export async function importSysConfig(data: Record<string, unknown>) {
  return request.post('/config/import', data)
}
export async function saveSysConfig(data: Record<string, unknown>) {
  return request.put('/config', data)
}
export async function setDefaultConfig(configKey: string) {
  return request.post(`/config/${configKey}/set-default`)
}
export async function resetDefaultConfig(configKey: string) {
  return request.post(`/config/${configKey}/reset-default`)
}
export async function getReviewFlowConfig() {
  return request.get('/config/review-flow')
}
export async function updateReviewFlowConfig(data: Record<string, unknown>) {
  return request.put('/config/review-flow', data)
}
export async function updatePublishSwitch(data: Record<string, unknown>) {
  return request.put('/config/publish-switch', data)
}
export async function updateReviewSwitch(data: Record<string, unknown>) {
  return request.put('/config/review-switch', data)
}
export async function updateDocGuideConfig(data: Record<string, unknown>) {
  return request.put('/config/doc-guide', data)
}
export async function getPublishStatus() {
  return request.get('/config/publish-status')
}
export async function getReviewStatus() {
  return request.get('/config/review-status')
}

// ===========================================================================
// 知识发布管理
// ===========================================================================

export async function getPublishHistory(kbId: string, knowledgeId?: string) {
  return request.get(`/kb/${kbId}/publish/history`, { params: { knowledgeId } })
}
export async function publishKnowledge(kbId: string, knowledgeId: string, data: Record<string, unknown>) {
  return request.post(`/kb/${kbId}/publish/${knowledgeId}`, data)
}
export async function revokeKnowledge(kbId: string, knowledgeId: string) {
  return request.post(`/kb/${kbId}/publish/${knowledgeId}/revoke`)
}
export async function getPublishPlans(kbId: string) {
  return request.get(`/kb/${kbId}/publish/plans`)
}
export async function createPublishPlan(kbId: string, data: Record<string, unknown>) {
  return request.post(`/kb/${kbId}/publish/plans`, data)
}
export async function getPublishPlanExecution(kbId: string, planId: string) {
  return request.get(`/kb/${kbId}/publish/plans/${planId}/execution`)
}
export async function getPublishStrategyEffect(kbId: string) {
  return request.get(`/kb/${kbId}/publish/strategy-effect`)
}
export async function getResetConfigs(kbId: string) {
  return request.get(`/kb/${kbId}/reset-configs`)
}
export async function saveResetConfig(kbId: string, data: Record<string, unknown>) {
  return request.post(`/kb/${kbId}/reset-configs`, data)
}
export async function resetKnowledge(kbId: string, knowledgeId: string) {
  return request.post(`/kb/${kbId}/reset/${knowledgeId}`)
}
export async function getOnlineVersion(kbId: string, knowledgeId?: string) {
  return request.get(`/kb/${kbId}/publish/online-version`, { params: { knowledgeId } })
}
export async function getOfflineVersion(kbId: string, knowledgeId?: string) {
  return request.get(`/kb/${kbId}/publish/offline-version`, { params: { knowledgeId } })
}

// ===========================================================================
// M16 应用配置（机器人配置）
// ===========================================================================

export async function getAppBasicConfig(appId: string) {
  return request.get(`/apps/${appId}/basic`)
}
export async function saveAppBasicConfig(appId: string, data: Record<string, unknown>) {
  return request.put(`/apps/${appId}/basic`, data)
}
export async function getAppDialogConfig(appId: string) {
  return request.get(`/apps/${appId}/dialog`)
}
export async function saveAppDialogConfig(appId: string, data: Record<string, unknown>) {
  return request.put(`/apps/${appId}/dialog/background`, data)
}
export async function getAppTriggers(appId: string) {
  return request.get(`/apps/${appId}/triggers`)
}
export async function createAppTrigger(appId: string, data: Record<string, unknown>) {
  return request.post(`/apps/${appId}/triggers`, data)
}
export async function updateAppTrigger(appId: string, id: string, data: Record<string, unknown>) {
  return request.put(`/apps/${appId}/triggers/${id}`, data)
}
export async function testAppTrigger(appId: string, id: string, input: string) {
  return request.post(`/apps/${appId}/triggers/${id}/test`, { input })
}
export async function runAppTrigger(appId: string, id: string, input: string) {
  return request.post(`/apps/${appId}/triggers/${id}/run`, { input })
}
export async function deleteAppTrigger(appId: string, id: string) {
  return request.delete(`/apps/${appId}/triggers/${id}`)
}
export async function getAppGlobalPolicy(appId: string) {
  return request.get(`/apps/${appId}/global-policy`)
}
export async function saveAppGlobalPolicy(appId: string, data: Record<string, unknown>) {
  return request.put(`/apps/${appId}/global-policy/safety`, data)
}
export async function getAppVariables(appId: string) {
  return request.get(`/apps/${appId}/global-policy/variables`)
}
export async function createAppVariable(appId: string, data: Record<string, unknown>) {
  return request.post(`/apps/${appId}/global-policy/variables`, data)
}
export async function deleteAppVariable(appId: string, id: string) {
  return request.delete(`/apps/${appId}/global-policy/variables/${id}`)
}
export async function getAppKbBindings(appId: string) {
  return request.get(`/apps/${appId}/knowledge-bases`)
}
export async function bindAppKb(appId: string, data: Record<string, unknown>) {
  return request.post(`/apps/${appId}/knowledge-bases`, data)
}
export async function unbindAppKb(appId: string, id: string) {
  return request.delete(`/apps/${appId}/knowledge-bases/${id}`)
}
export async function getAppDbBindings(appId: string) {
  return request.get(`/apps/${appId}/databases`)
}
export async function createAppDbBinding(appId: string, data: Record<string, unknown>) {
  return request.post(`/apps/${appId}/databases`, data)
}
export async function updateAppDbBinding(appId: string, id: string, data: Record<string, unknown>) {
  return request.put(`/apps/${appId}/databases/${id}`, data)
}
export async function deleteAppDbBinding(appId: string, id: string) {
  return request.delete(`/apps/${appId}/databases/${id}`)
}
export async function getAppPublishRecords(appId: string) {
  return request.get(`/apps/${appId}/publish/records`)
}
export async function publishApp(appId: string, data: Record<string, unknown>) {
  return request.post(`/apps/${appId}/publish/online`, data)
}
export async function getAppDialogTests(appId: string) {
  return request.get(`/apps/${appId}/dialog-tests`)
}
export async function createAppDialogTest(appId: string, data: Record<string, unknown>) {
  return request.post(`/apps/${appId}/dialog-tests`, data)
}
export async function updateAppDialogTest(appId: string, id: string, data: Record<string, unknown>) {
  return request.put(`/apps/${appId}/dialog-tests/${id}`, data)
}
export async function deleteAppDialogTest(appId: string, id: string) {
  return request.delete(`/apps/${appId}/dialog-tests/${id}`)
}
export async function exportAppDialogTests(appId: string) {
  return request.get(`/apps/${appId}/dialog-tests/export`, { responseType: 'blob' })
}
export async function getAppOptimizations(appId: string) {
  return request.get(`/apps/${appId}/optimizations`)
}
export async function createAppOptimization(appId: string, data: Record<string, unknown>) {
  return request.post(`/apps/${appId}/optimizations`, data)
}
export async function applyAppOptimization(appId: string, id: string) {
  return request.post(`/apps/${appId}/optimizations/${id}/apply`)
}
export async function updateAppOptimization(appId: string, id: string, data: Record<string, unknown>) {
  return request.put(`/apps/${appId}/optimizations/${id}`, data)
}
export async function deleteAppOptimization(appId: string, id: string) {
  return request.delete(`/apps/${appId}/optimizations/${id}`)
}
export async function exportAppOptimizations(appId: string) {
  return request.get(`/apps/${appId}/optimizations/export`, { responseType: 'blob' })
}

// ===========================================================================
// 技能/工具/MCP 绑定 API
// ===========================================================================
export async function getAppSkillBindings(appId: string) {
  return request.get(`/apps/${appId}/skills`)
}
export async function bindAppSkill(appId: string, data: Record<string, unknown>) {
  return request.post(`/apps/${appId}/skills`, data)
}
export async function updateAppSkillBinding(appId: string, id: string, data: Record<string, unknown>) {
  return request.put(`/apps/${appId}/skills/${id}`, data)
}
export async function unbindAppSkill(appId: string, id: string) {
  return request.delete(`/apps/${appId}/skills/${id}`)
}
export async function getAppToolBindings(appId: string) {
  return request.get(`/apps/${appId}/tools`)
}
export async function bindAppTool(appId: string, data: Record<string, unknown>) {
  return request.post(`/apps/${appId}/tools`, data)
}
export async function updateAppToolBinding(appId: string, id: string, data: Record<string, unknown>) {
  return request.put(`/apps/${appId}/tools/${id}`, data)
}
export async function unbindAppTool(appId: string, id: string) {
  return request.delete(`/apps/${appId}/tools/${id}`)
}
export async function getAppMcpBindings(appId: string) {
  return request.get(`/apps/${appId}/mcp-services`)
}
export async function bindAppMcp(appId: string, data: Record<string, unknown>) {
  return request.post(`/apps/${appId}/mcp-services`, data)
}
export async function updateAppMcpBinding(appId: string, id: string, data: Record<string, unknown>) {
  return request.put(`/apps/${appId}/mcp-services/${id}`, data)
}
export async function unbindAppMcp(appId: string, id: string) {
  return request.delete(`/apps/${appId}/mcp-services/${id}`)
}

// ===========================================================================
// M17 业务流管理（扩展）
// ===========================================================================

export async function getWorkflowNodes(wfId: string) {
  return request.get(`/workflows/${wfId}/nodes`)
}
	export async function addWorkflowNodeExt(wfId: string, data: Record<string, unknown>) {
	  return request.post(`/workflows/${wfId}/nodes`, data)
	}
	export async function deleteWorkflowNodeExt(wfId: string, nodeKey: string) {
	  return request.delete(`/workflows/${wfId}/nodes/${nodeKey}`)
	}
export async function moveWorkflowNode(wfId: string, nodeKey: string, data: { x: number; y: number }) {
  return request.put(`/workflows/${wfId}/nodes/${nodeKey}/position`, data)
}
export async function updateWorkflowNode(wfId: string, nodeKey: string, data: Record<string, unknown>) {
  return request.put(`/workflows/${wfId}/nodes/${nodeKey}`, data)
}
export async function getWorkflowNodeConfig(wfId: string, nodeKey: string, dimension: string) {
  return request.get(`/workflows/${wfId}/nodes/${nodeKey}/${dimension}`)
}
export async function saveWorkflowNodeConfig(wfId: string, nodeKey: string, dimension: string, data: Record<string, unknown>) {
  return request.put(`/workflows/${wfId}/nodes/${nodeKey}/${dimension}`, data)
}
export async function executeWorkflow(wfId: string, inputs?: Record<string, unknown>) {
  return request.post(`/workflows/${wfId}/execute`, inputs || {})
}
export async function testWorkflowNode(wfId: string, nodeKey: string, data?: Record<string, unknown>) {
  return request.post(`/workflows/${wfId}/nodes/${nodeKey}/test`, data || {})
}
export async function getWorkflowTemplates() {
  return request.get('/workflows/templates')
}
export async function createWorkflowTemplate(data: Record<string, unknown>) {
  return request.post('/workflows/templates', data)
}
export async function getWorkflowTestCases(wfId: string) {
  return request.get(`/workflows/${wfId}/test-cases`)
}
export async function createWorkflowTestCase(wfId: string, data: Record<string, unknown>) {
  return request.post(`/workflows/${wfId}/test-cases`, data)
}
export async function getWorkflowMigrations() {
  return request.get('/workflows/migrations')
}
export async function createWorkflowMigration(data: Record<string, unknown>) {
  return request.post('/workflows/migrations', data)
}
export async function getWorkflowMonitor(wfId: string) {
  return request.get(`/workflows/${wfId}/monitor`)
}

// ===========================================================================
// M19 数据库管理
// ===========================================================================

export async function getDatabases(params?: { keyword?: string; dbType?: string }) {
  return request.get('/databases', { params })
}
export async function getDatabase(id: string) {
  return request.get(`/databases/${id}`)
}
export async function createDatabase(data: Record<string, unknown>) {
  return request.post('/databases', data)
}
export async function updateDatabase(id: string, data: Record<string, unknown>) {
  return request.put(`/databases/${id}`, data)
}
export async function deleteDatabase(id: string) {
  return request.delete(`/databases/${id}`)
}
export async function testDatabaseConnection(id: string) {
  return request.post(`/databases/${id}/test-conn`)
}
export async function queryDatabase(id: string, sql: string) {
  return request.post(`/databases/${id}/query`, { sql })
}
export async function getDatabaseTables(id: string) {
  return request.get(`/databases/${id}/tables`)
}
export async function createDatabaseTable(id: string, data: Record<string, unknown>) {
  return request.post(`/databases/${id}/tables`, data)
}
export async function syncDatabaseTables(id: string) {
  return request.post(`/databases/${id}/sync-tables`)
}

// ===========================================================================
// 插件管理扩展
// ===========================================================================

export async function uploadPlugin(file: File, name?: string, description?: string) {
  const formData = new FormData()
  formData.append('file', file)
  if (name) formData.append('name', name)
  if (description) formData.append('description', description)
  return request.post('/tools/upload', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
}
export async function importPluginsFromJson(plugins: Record<string, unknown>[]) {
  return request.post('/tools/import-json', plugins)
}

// ===========================================================================
// M16 机器人配置扩展
// ===========================================================================

export async function saveAppAdvanced(appId: string, opts: Record<string, unknown>) {
  return request.put(`/apps/${appId}/basic/advanced`, opts)
}
export async function exportAppConfig(appId: string) {
  return request.get(`/apps/${appId}/basic/export`)
}
export async function importAppConfig(appId: string, data: Record<string, unknown>) {
  return request.post(`/apps/${appId}/basic/import`, data)
}
export async function exportAppDialog(appId: string) {
  return request.get(`/apps/${appId}/dialog/export`)
}
export async function importAppDialog(appId: string, data: Record<string, unknown>) {
  return request.post(`/apps/${appId}/dialog/import`, data)
}
export async function updateAppVariable(appId: string, id: string, data: Record<string, unknown>) {
  return request.put(`/apps/${appId}/global-policy/variables/${id}`, data)
}
export async function saveAppSensitiveWords(appId: string, cfg: Record<string, unknown>) {
  return request.put(`/apps/${appId}/global-policy/sensitive-words`, cfg)
}
export async function toggleAppPolicy(appId: string, cfg: Record<string, unknown>) {
  return request.post(`/apps/${appId}/global-policy/toggle`, cfg)
}
export async function saveAppUnmatchedConfig(appId: string, cfg: Record<string, unknown>) {
  return request.put(`/apps/${appId}/global-policy/unmatched`, cfg)
}
export async function getAppWorkflowConfig(appId: string) {
  return request.get(`/apps/${appId}/workflow-config`)
}
export async function saveAppWorkflowConfig(appId: string, cfg: Record<string, unknown>) {
  return request.post(`/apps/${appId}/workflow-config`, cfg)
}
export async function getAppMonitor(appId: string) {
  return request.get(`/apps/${appId}/monitor`)
}
export async function getAppDebugInfo(appId: string) {
  return request.get(`/apps/${appId}/debug`)
}
export async function saveAppDebugConfig(appId: string, cfg: Record<string, unknown>) {
  return request.post(`/apps/${appId}/debug`, cfg)
}
export async function triggerAppKnowledgeUpdate(appId: string, cfg: Record<string, unknown>) {
  return request.post(`/apps/${appId}/knowledge-update`, cfg)
}

// ===========================================================================
// M17 业务流管理扩展
// ===========================================================================

export async function updateWorkflowTemplate(id: string, data: Record<string, unknown>) {
  return request.put(`/workflows/templates/${id}`, data)
}
export async function deleteWorkflowTemplate(id: string) {
  return request.delete(`/workflows/templates/${id}`)
}
export async function getWorkflowDebugInfo(wfId: string) {
  return request.get(`/workflows/${wfId}/debug`)
}
export async function saveWorkflowDebugConfig(wfId: string, cfg: Record<string, unknown>) {
  return request.post(`/workflows/${wfId}/debug`, cfg)
}
export async function getWorkflowOptimizations(wfId: string) {
  return request.get(`/workflows/${wfId}/optimizations`)
}
export async function createWorkflowOptimization(wfId: string, data: Record<string, unknown>) {
  return request.post(`/workflows/${wfId}/optimizations`, data)
}
export async function applyWorkflowOptimization(optId: string) {
  return request.post(`/workflows/optimizations/${optId}/apply`)
}

// ===========================================================================
// M4 模型预置
// ===========================================================================

export async function getModelPresets() {
  return request.get('/models/presets')
}
export async function createModelPreset(data: Record<string, unknown>) {
  return request.post('/models/presets', data)
}
export async function updateModelPreset(id: string, data: Record<string, unknown>) {
  return request.put(`/models/presets/${id}`, data)
}
export async function deleteModelPreset(id: string) {
  return request.delete(`/models/presets/${id}`)
}

// ===========================================================================
// 模型监控 API
// ===========================================================================

export async function getModelMonitorOverview(params?: { timeRange?: number; keyword?: string; page?: number; pageSize?: number }) {
  return request.get<unknown, ModelMonitorOverview>('/monitor/model/overview', { params })
}

export async function getKnowledgeUpdateLogs(kbId: string, page?: number, pageSize?: number) {
  return request.get(`/kb/${kbId}/knowledge-update-logs`, { params: { page, pageSize } })
}
export async function markUpdateLogRead(kbId: string, id: string) {
  return request.put(`/kb/${kbId}/update-logs/${id}/read`)
}
export async function setAutoKnowledgeUpdate(appId: string, config: Record<string, unknown>) {
  return request.put(`/apps/${appId}/knowledge-update/config`, config)
}
export async function getAutoKnowledgeUpdate(appId: string) {
  return request.get(`/apps/${appId}/knowledge-update/config`)
}

// ===========================================================================
// 对话记录 API
// ===========================================================================
export async function getAppConversations(appId: string, params?: { keyword?: string; rating?: number; startDate?: string; endDate?: string; page?: number; size?: number }) {
  return request.get(`/apps/${appId}/conversations`, { params })
}
export async function getAppConversationDetail(appId: string, convId: string) {
  return request.get(`/apps/${appId}/conversations/${convId}`)
}
export async function deleteAppConversation(appId: string, convId: string) {
  return request.delete(`/apps/${appId}/conversations/${convId}`)
}
export async function exportAppConversations(appId: string) {
  return request.get(`/apps/${appId}/conversations/export`, { responseType: 'blob' })
}

// ===========================================================================
// 通知 API
// ===========================================================================
export async function getNotifications(userId?: string, status?: string) {
  return request.get('/notifications', { params: { userId, status } })
}
export async function getUnreadNotificationCount(userId?: string) {
  return request.get('/notifications/unread-count', { params: { userId } })
}
export async function createNotification(data: Record<string, unknown>) {
  return request.post('/notifications', data)
}
export async function markNotificationRead(id: string) {
  return request.put(`/notifications/${id}/read`)
}
export async function markAllNotificationsRead(userId?: string) {
  return request.put('/notifications/read-all', null, { params: { userId } })
}
export async function deleteNotification(id: string) {
  return request.delete(`/notifications/${id}`)
}

// ===========================================================================
// 默认导出（兼容旧代码）
// ===========================================================================
export default request

// ===========================================================================
// API Token 管理（平台级全局 Token）
// ===========================================================================
/** 创建 API Token */
export async function createApiToken(data: Record<string, any>) {
  return request.post('/api-tokens', data)
}

/** 获取 API Token 列表 */
export async function getApiTokens() {
  return request.get('/api-tokens')
}

/** 撤销（删除）API Token */
export async function deleteApiToken(tokenId: string) {
  return request.delete(`/api-tokens/${tokenId}`)
}
