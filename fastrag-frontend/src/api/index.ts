import request from '@/utils/request'
import type { GraphExpansionResult, ParseStrategy, ParseStrategyForm } from '@/types/knowledge'

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

// ===========================================================================
// 知识库 API
// ===========================================================================

export async function getKnowledgeBases(params?: { keyword?: string; category?: string; page?: number; pageSize?: number }) {
  return request.get('/kb', { params })
}

export async function getKnowledgeBaseCategories() {
  return request.get('/kb/categories')
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

export async function updateFile(kbId: string, fileId: string, data: Record<string, unknown>) {
  return request.put(`/kb/${kbId}/files/${fileId}`, data)
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

export async function getFileProcessingStatus(kbId: string, fileId: string) {
  return request.get(`/kb/${kbId}/files/${fileId}/processing-status`)
}

// ===========================================================================
// 文件夹 API
// ===========================================================================

export async function fetchFolders(kbId: string): Promise<FolderNode[]> {
  return request.get(`/kb/${kbId}/folders`)
}

export async function createFolderApi(kbId: string, name: string, parentId: string = 'root'): Promise<void> {
  return request.post(`/kb/${kbId}/folders`, { name, parentId })
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

export async function fetchChunkCount(kbId: string): Promise<number> {
  return request.get(`/kb/${kbId}/chunks/count`)
}

// ===========================================================================
// QA 对 API
// ===========================================================================

export async function getQaPairs(kbId: string, params?: { page?: number; pageSize?: number }) {
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

export async function qaExtract(kbId: string, data: { fileId?: string }) {
  return request.post(`/kb/${kbId}/qa-pairs/qa-extract`, data)
}

// ===========================================================================
// 解析策略 API
// ===========================================================================

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

export async function querySuggest(query: string): Promise<string[]> {
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

export async function fetchGraphData(kbId: string): Promise<GraphData> {
  return request.get(`/kb/${kbId}/graph`)
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

export async function buildGraphIndex(kbId: string) {
  return request.post(`/kb/${kbId}/graph/index/build`)
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

// ===========================================================================
// 评估基准 API
// ===========================================================================

export async function fetchBenchmarks(kbId: string): Promise<Benchmark[]> {
  return request.get(`/kb/${kbId}/benchmarks`)
}

export async function fetchBenchmarkDetail(kbId: string, benchId: string): Promise<BenchmarkQuestion[]> {
  return request.get(`/kb/${kbId}/benchmarks/${benchId}`)
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

export async function getKbLogs(kbId: string, params?: { category?: string; page?: number; pageSize?: number }) {
  return request.get(`/kb/${kbId}/logs`, { params })
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

export async function runApp(id: string, query: string) {
  return request.post(`/apps/${id}/run`, { query })
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

// ===========================================================================
// 技能 API
// ===========================================================================

export async function getSkills(params?: { keyword?: string; category?: string }) {
  return request.get('/skills', { params })
}

export async function getSkillDetail(id: string) {
  return request.get(`/skills/${id}`)
}

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
// 查询规则 API
// ===========================================================================

export async function getQueryRules(params?: { type?: string }) {
  return request.get('/query-rules', { params })
}

export async function createQueryRule(data: Record<string, unknown>) {
  return request.post('/query-rules', data)
}

export async function deleteQueryRule(id: string) {
  return request.delete(`/query-rules/${id}`)
}

export async function toggleQueryRule(id: string) {
  return request.post(`/query-rules/${id}/toggle`)
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
// 术语 API
// ===========================================================================

export async function getTermLibraries() {
  return request.get('/terminology/libraries')
}

export async function createTermLibrary(data: Record<string, unknown>) {
  return request.post('/terminology/libraries', data)
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

// ===========================================================================
// 人员 API
// ===========================================================================

export async function getPersonnel(params?: { keyword?: string; page?: number; pageSize?: number }) {
  return request.get('/personnel', { params })
}

export async function createPersonnel(data: Record<string, unknown>) {
  return request.post('/personnel', data)
}

export async function updatePersonnel(id: string, data: Record<string, unknown>) {
  return request.put(`/personnel/${id}`, data)
}

export async function assignRole(personnelId: string, roleId: string) {
  return request.post(`/personnel/${personnelId}/assign-role`, { roleId })
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
// 团队 API
// ===========================================================================

export async function getTeams() {
  return request.get('/teams')
}

export async function getTeamDetail(id: string) {
  return request.get(`/teams/${id}`)
}

export async function createTeam(data: Record<string, unknown>) {
  return request.post('/teams', data)
}

export async function updateTeam(id: string, data: Record<string, unknown>) {
  return request.put(`/teams/${id}`, data)
}

export async function deleteTeam(id: string) {
  return request.delete(`/teams/${id}`)
}

export async function getTeamMembers(id: string) {
  return request.get(`/teams/${id}/members`)
}

export async function addTeamMember(teamId: string, userId: string) {
  return request.post(`/teams/${teamId}/members`, { userId })
}

export async function removeTeamMember(teamId: string, userId: string) {
  return request.delete(`/teams/${teamId}/members/${userId}`)
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

export async function getLoginLogs(params?: Record<string, unknown>) {
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
// 默认导出（兼容旧代码）
// ===========================================================================
export default request
