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

export async function processFile(kbId: string, fileId: string) {
  return request.post(`/kb/${kbId}/files/${fileId}/process`)
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

export async function downloadFile(kbId: string, fileId: string) {
  return request.get(`/kb/${kbId}/files/${fileId}/download`, { responseType: 'blob' })
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

export async function fetchParseStrategyTemplates() {
  return request.get('/parse-strategy-templates')
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
// M1 知识库用户反馈（扩展）
// ===========================================================================

export async function getFeedbackPage(params?: { kbId?: string; feedback?: string; status?: string; page?: number; pageSize?: number }) {
  return request.get('/feedback', { params })
}

export async function getFeedbackStatistics(kbId?: string) {
  return request.get('/feedback/statistics', { params: { kbId } })
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
// M3 实体管理
// ===========================================================================

export async function getEntities(kbId: string, keyword?: string) {
  return request.get(`/kb/${kbId}/entities`, { params: { keyword } })
}

export async function getEntity(kbId: string, id: string) {
  return request.get(`/kb/${kbId}/entities/${id}`)
}

export async function createEntity(kbId: string, data: Record<string, unknown>) {
  return request.post(`/kb/${kbId}/entities`, data)
}

export async function updateEntity(kbId: string, id: string, data: Record<string, unknown>) {
  return request.put(`/kb/${kbId}/entities/${id}`, data)
}

export async function deleteEntity(kbId: string, id: string) {
  return request.delete(`/kb/${kbId}/entities/${id}`)
}

// ===========================================================================
// M4 API插件配置
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
// M7 知识生产与获取（多媒体存储 + 问答抽取）
// ===========================================================================

export async function getMediaList(kbId: string, mediaType: string, params?: { keyword?: string; status?: string; page?: number; pageSize?: number }) {
  return request.get(`/kb/${kbId}/media/${mediaType}`, { params })
}

export async function importMedia(kbId: string, mediaType: string, items: Record<string, unknown>[]) {
  return request.post(`/kb/${kbId}/media/${mediaType}/import`, items)
}

export async function exportMedia(kbId: string, mediaType: string) {
  return request.get(`/kb/${kbId}/media/${mediaType}/export`)
}

export async function getDocumentList(kbId: string, params?: { keyword?: string; page?: number; pageSize?: number }) {
  return request.get(`/kb/${kbId}/documents`, { params })
}

export async function importDocuments(kbId: string, items: Record<string, unknown>[]) {
  return request.post(`/kb/${kbId}/documents/import`, items)
}

export async function exportDocuments(kbId: string) {
  return request.get(`/kb/${kbId}/documents/export`)
}

export async function getStorageList(kbId: string, mediaType: string, params?: { keyword?: string; page?: number; pageSize?: number }) {
  return request.get(`/kb/${kbId}/storage/${mediaType}`, { params })
}

export async function createStorage(kbId: string, mediaType: string, data: Record<string, unknown>) {
  return request.post(`/kb/${kbId}/storage/${mediaType}`, data)
}

export async function updateStorage(kbId: string, mediaType: string, id: string, data: Record<string, unknown>) {
  return request.put(`/kb/${kbId}/storage/${mediaType}/${id}`, data)
}

export async function deleteStorage(kbId: string, mediaType: string, id: string) {
  return request.delete(`/kb/${kbId}/storage/${mediaType}/${id}`)
}

// 问答抽取
export async function getQaExtractTasks(kbId: string, status?: string) {
  return request.get(`/kb/${kbId}/qa-extract`, { params: { status } })
}

export async function startQaExtract(kbId: string, data: Record<string, unknown>) {
  return request.post(`/kb/${kbId}/qa-extract/start`, data)
}

export async function stopQaExtract(kbId: string, taskId: string) {
  return request.post(`/kb/${kbId}/qa-extract/${taskId}/stop`)
}
export async function updateQaExtract(kbId: string, taskId: string, data: Record<string, unknown>) {
  return request.put(`/kb/${kbId}/qa-extract/${taskId}`, data)
}

export async function deleteQaExtract(kbId: string, taskId: string) {
  return request.delete(`/kb/${kbId}/qa-extract/${taskId}`)
}

// ===========================================================================
// M8 知识加工与采编
// ===========================================================================

export async function getKnowledgeEdits(kbId: string, params?: { status?: string; editor?: string }) {
  return request.get(`/kb/${kbId}/knowledge-edits`, { params })
}

export async function exportKnowledgeEdits(kbId: string, params?: { ids?: string; status?: string; editor?: string }) {
  return request.get(`/kb/${kbId}/knowledge-edits/export`, { params })
}

export async function createKnowledgeEdit(kbId: string, data: Record<string, unknown>) {
  return request.post(`/kb/${kbId}/knowledge-edits`, data)
}

export async function updateKnowledgeEdit(kbId: string, id: string, data: Record<string, unknown>) {
  return request.put(`/kb/${kbId}/knowledge-edits/${id}`, data)
}

export async function deleteKnowledgeEdit(kbId: string, id: string) {
  return request.delete(`/kb/${kbId}/knowledge-edits/${id}`)
}

export async function submitKnowledgeEdit(kbId: string, id: string) {
  return request.post(`/kb/${kbId}/knowledge-edits/${id}/submit`)
}

export async function approveKnowledgeEdit(kbId: string, id: string, data?: { reviewer?: string }) {
  return request.post(`/kb/${kbId}/knowledge-edits/${id}/approve`, data || {})
}

export async function rejectKnowledgeEdit(kbId: string, id: string, data: { reviewer?: string; comment?: string }) {
  return request.post(`/kb/${kbId}/knowledge-edits/${id}/reject`, data)
}

// 存量知识点校验
export async function getKnowledgeValidates(kbId: string) {
  return request.get(`/kb/${kbId}/knowledge-validate`)
}

export async function checkKnowledgeValidate(kbId: string, data: Record<string, unknown>) {
  return request.post(`/kb/${kbId}/knowledge-validate/check`, data)
}

// ===========================================================================
// M9 知识存储管理（标签类型 / 标签 / 笔记）
// ===========================================================================

export async function getTagTypes(kbId: string) {
  return request.get(`/kb/${kbId}/tag-types`)
}

export async function createTagType(kbId: string, data: Record<string, unknown>) {
  return request.post(`/kb/${kbId}/tag-types`, data)
}

export async function updateTagType(kbId: string, id: string, data: Record<string, unknown>) {
  return request.put(`/kb/${kbId}/tag-types/${id}`, data)
}

export async function deleteTagType(kbId: string, id: string) {
  return request.delete(`/kb/${kbId}/tag-types/${id}`)
}

export async function getTags(kbId: string, params?: { tagTypeId?: string; keyword?: string }) {
  return request.get(`/kb/${kbId}/tags`, { params })
}

export async function createTag(kbId: string, data: Record<string, unknown>) {
  return request.post(`/kb/${kbId}/tags`, data)
}

export async function updateTag(kbId: string, id: string, data: Record<string, unknown>) {
  return request.put(`/kb/${kbId}/tags/${id}`, data)
}

export async function deleteTag(kbId: string, id: string) {
  return request.delete(`/kb/${kbId}/tags/${id}`)
}
export async function getTagKnowledge(kbId: string, tagId: string) {
  return request.get(`/kb/${kbId}/tags/${tagId}/knowledge`)
}
export async function disassociateTag(kbId: string, tagId: string, knowledgeId: string) {
  return request.delete(`/kb/${kbId}/tags/${tagId}/knowledge/${knowledgeId}`)
}

export async function getNotes(kbId: string, keyword?: string) {
  return request.get(`/kb/${kbId}/notes`, { params: { keyword } })
}

export async function createNote(kbId: string, data: Record<string, unknown>) {
  return request.post(`/kb/${kbId}/notes`, data)
}

export async function updateNote(kbId: string, id: string, data: Record<string, unknown>) {
  return request.put(`/kb/${kbId}/notes/${id}`, data)
}

export async function deleteNote(kbId: string, id: string) {
  return request.delete(`/kb/${kbId}/notes/${id}`)
}

export async function exportNotes(kbId: string, ids?: string) {
  return request.get(`/kb/${kbId}/notes/export`, { params: { ids } })
}

// ===========================================================================
// M10 知识管理 + M11 知识更新
// ===========================================================================

export async function getKnowledgeList(kbId: string, params?: { keyword?: string; category?: string }) {
  return request.get(`/kb/${kbId}/knowledge`, { params })
}
export async function createKnowledge(kbId: string, data: Record<string, unknown>) {
  return request.post(`/kb/${kbId}/knowledge`, data)
}
export async function updateKnowledge(kbId: string, id: string, data: Record<string, unknown>) {
  return request.put(`/kb/${kbId}/knowledge/${id}`, data)
}
export async function deleteKnowledge(kbId: string, id: string) {
  return request.delete(`/kb/${kbId}/knowledge/${id}`)
}
export async function getKnowledgeTests(kbId: string, knowledgeId?: string) {
  return request.get(`/kb/${kbId}/knowledge-tests`, { params: { knowledgeId } })
}
export async function createKnowledgeTest(kbId: string, data: Record<string, unknown>) {
  return request.post(`/kb/${kbId}/knowledge-tests`, data)
}
export async function updateKnowledgeTest(kbId: string, id: string, data: Record<string, unknown>) {
  return request.put(`/kb/${kbId}/knowledge-tests/${id}`, data)
}

export async function deleteKnowledgeTest(kbId: string, id: string) {
  return request.delete(`/kb/${kbId}/knowledge-tests/${id}`)
}
export async function judgeKnowledgeDialog(kbId: string, id: string, data: { query: string; model?: string }) {
  return request.post(`/kb/${kbId}/knowledge-dialogs/${id}/judge`, data)
}

export async function getKnowledgeDialogs(kbId: string, knowledgeId?: string) {
  return request.get(`/kb/${kbId}/knowledge-dialogs`, { params: { knowledgeId } })
}

export async function createKnowledgeDialog(kbId: string, data: Record<string, unknown>) {
  return request.post(`/kb/${kbId}/knowledge-dialogs`, data)
}

export async function deleteKnowledgeDialog(kbId: string, id: string) {
  return request.delete(`/kb/${kbId}/knowledge-dialogs/${id}`)
}

// M11 知识更新
export async function getKnowledgeUpdates(kbId: string, params?: { knowledgeId?: string; updateType?: string; status?: string; page?: number; pageSize?: number }) {
  return request.get(`/kb/${kbId}/knowledge-updates`, { params })
}
export async function createKnowledgeUpdate(kbId: string, data: Record<string, unknown>) {
  return request.post(`/kb/${kbId}/knowledge-updates`, data)
}
export async function updateKnowledgeUpdate(kbId: string, id: string, data: Record<string, unknown>) {
  return request.put(`/kb/${kbId}/knowledge-updates/${id}`, data)
}
export async function deleteKnowledgeUpdate(kbId: string, id: string) {
  return request.delete(`/kb/${kbId}/knowledge-updates/${id}`)
}
export async function applyKnowledgeUpdate(kbId: string, id: string) {
  return request.post(`/kb/${kbId}/knowledge-updates/${id}/apply`)
}
export async function rollbackKnowledgeUpdate(kbId: string, id: string) {
  return request.post(`/kb/${kbId}/knowledge-updates/${id}/rollback`)
}

// ===========================================================================
// M12 智能搜索
// ===========================================================================

export async function getSearchAssociations(kbId: string, params?: { dimension?: string; keyword?: string; page?: number; pageSize?: number }) {
  return request.get(`/kb/${kbId}/search-associations`, { params })
}
export async function createSearchAssociation(kbId: string, data: Record<string, unknown>) {
  return request.post(`/kb/${kbId}/search-associations`, data)
}
export async function updateSearchAssociation(kbId: string, id: string, data: Record<string, unknown>) {
  return request.put(`/kb/${kbId}/search-associations/${id}`, data)
}
export async function deleteSearchAssociation(kbId: string, id: string) {
  return request.delete(`/kb/${kbId}/search-associations/${id}`)
}
export async function searchAssociations(kbId: string, q: string, dimension?: string) {
  return request.get(`/kb/${kbId}/search-associations/search`, { params: { q, dimension } })
}
export async function judgeSearchAssociation(kbId: string, dimension: string, query: string, targetText?: string) {
  return request.post(`/kb/${kbId}/search-associations/judge`, { dimension, query, targetText })
}
export async function searchAutoCorrect(kbId: string, q: string) {
  return request.get(`/kb/${kbId}/search-associations/auto-correct`, { params: { q } })
}
export async function getAutoCorrections(kbId: string) {
  return request.get(`/kb/${kbId}/auto-corrections`)
}
export async function createAutoCorrection(kbId: string, data: Record<string, unknown>) {
  return request.post(`/kb/${kbId}/auto-corrections`, data)
}
export async function updateAutoCorrection(kbId: string, id: string, data: Record<string, unknown>) {
  return request.put(`/kb/${kbId}/auto-corrections/${id}`, data)
}
export async function deleteAutoCorrection(kbId: string, id: string) {
  return request.delete(`/kb/${kbId}/auto-corrections/${id}`)
}

// ===========================================================================
// M13 知识问答
// ===========================================================================

export async function getMultiTurnQa(kbId: string) {
  return request.get(`/kb/${kbId}/multi-turn-qa`)
}
export async function createMultiTurnQa(kbId: string, data: Record<string, unknown>) {
  return request.post(`/kb/${kbId}/multi-turn-qa`, data)
}
export async function updateMultiTurnQa(kbId: string, id: string, data: Record<string, unknown>) {
  return request.put(`/kb/${kbId}/multi-turn-qa/${id}`, data)
}
export async function deleteMultiTurnQa(kbId: string, id: string) {
  return request.delete(`/kb/${kbId}/multi-turn-qa/${id}`)
}
export async function getMultimodalQa(kbId: string) {
  return request.get(`/kb/${kbId}/multimodal-qa`)
}
export async function createMultimodalQa(kbId: string, data: Record<string, unknown>) {
  return request.post(`/kb/${kbId}/multimodal-qa`, data)
}
export async function updateMultimodalQa(kbId: string, id: string, data: Record<string, unknown>) {
  return request.put(`/kb/${kbId}/multimodal-qa/${id}`, data)
}
export async function deleteMultimodalQa(kbId: string, id: string) {
  return request.delete(`/kb/${kbId}/multimodal-qa/${id}`)
}
export async function getDocGuides(kbId: string) {
  return request.get(`/kb/${kbId}/doc-guides`)
}
export async function createDocGuide(kbId: string, data: Record<string, unknown>) {
  return request.post(`/kb/${kbId}/doc-guides`, data)
}
export async function updateDocGuide(kbId: string, id: string, data: Record<string, unknown>) {
  return request.put(`/kb/${kbId}/doc-guides/${id}`, data)
}
export async function deleteDocGuide(kbId: string, id: string) {
  return request.delete(`/kb/${kbId}/doc-guides/${id}`)
}
export async function indexDocGuide(kbId: string, id: string) {
  return request.post(`/kb/${kbId}/doc-guides/${id}/index`)
}
export async function multimodalSearch(kbId: string, modality: string, data: Record<string, unknown>) {
  return request.post(`/kb/${kbId}/multimodal-retrieval/${modality}/search`, data)
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
export async function getNotificationConfig() {
  return request.get('/config/notification')
}
export async function saveNotificationConfig(data: Record<string, unknown>) {
  return request.put('/config/notification', data)
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
// M15 知识审核管理
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
export async function getReviewStrategies(kbId: string) {
  return request.get(`/kb/${kbId}/review-strategies`)
}
export async function createReviewStrategy(kbId: string, data: Record<string, unknown>) {
  return request.post(`/kb/${kbId}/review-strategies`, data)
}
export async function deleteReviewStrategy(kbId: string, id: string) {
  return request.delete(`/kb/${kbId}/review-strategies/${id}`)
}
export async function getComplianceRules(kbId: string) {
  return request.get(`/kb/${kbId}/compliance-rules`)
}
export async function createComplianceRule(kbId: string, data: Record<string, unknown>) {
  return request.post(`/kb/${kbId}/compliance-rules`, data)
}
export async function updateComplianceRule(kbId: string, id: string, data: Record<string, unknown>) {
  return request.put(`/kb/${kbId}/compliance-rules/${id}`, data)
}
export async function deleteComplianceRule(kbId: string, id: string) {
  return request.delete(`/kb/${kbId}/compliance-rules/${id}`)
}
export async function getQualityRules(kbId: string) {
  return request.get(`/kb/${kbId}/quality-rules`)
}
export async function createQualityRule(kbId: string, data: Record<string, unknown>) {
  return request.post(`/kb/${kbId}/quality-rules`, data)
}
export async function updateQualityRule(kbId: string, id: string, data: Record<string, unknown>) {
  return request.put(`/kb/${kbId}/quality-rules/${id}`, data)
}
export async function deleteQualityRule(kbId: string, id: string) {
  return request.delete(`/kb/${kbId}/quality-rules/${id}`)
}
export async function getReviewTemplates(kbId: string) {
  return request.get(`/kb/${kbId}/review-templates`)
}
export async function createReviewTemplate(kbId: string, data: Record<string, unknown>) {
  return request.post(`/kb/${kbId}/review-templates`, data)
}
export async function deleteReviewTemplate(kbId: string, id: string) {
  return request.delete(`/kb/${kbId}/review-templates/${id}`)
}
export async function updateReviewTemplate(kbId: string, id: string, data: Record<string, unknown>) {
  return request.put(`/kb/${kbId}/review-templates/${id}`, data)
}
export async function getReviewNodes(kbId: string, templateId: string) {
  return request.get(`/kb/${kbId}/review-templates/${templateId}/nodes`)
}
export async function getListeners(kbId: string) {
  return request.get(`/kb/${kbId}/listeners`)
}
export async function createListener(kbId: string, data: Record<string, unknown>) {
  return request.post(`/kb/${kbId}/listeners`, data)
}
export async function updateListener(kbId: string, id: string, data: Record<string, unknown>) {
  return request.put(`/kb/${kbId}/listeners/${id}`, data)
}
export async function toggleListener(kbId: string, id: string, action: string) {
  return request.post(`/kb/${kbId}/listeners/${id}/${action}`)
}
export async function deleteListener(kbId: string, id: string) {
  return request.delete(`/kb/${kbId}/listeners/${id}`)
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
// M9 存储上传/下载
// ===========================================================================

export async function uploadStorageFile(kbId: string, mediaType: string, file: File, description?: string, tags?: string) {
  const formData = new FormData()
  formData.append('file', file)
  if (description) formData.append('description', description)
  if (tags) formData.append('tags', tags)
  return request.post(`/kb/${kbId}/storage/${mediaType}/upload`, formData, { headers: { 'Content-Type': 'multipart/form-data' } })
}
export function getStorageDownloadUrl(kbId: string, mediaType: string, id: string) {
  return `/api/kb/${kbId}/storage/${mediaType}/${id}/download`
}

// ===========================================================================
// M15 知识审核管理扩展
// ===========================================================================

export async function getListenerLogs(kbId: string, listenerId: string, params?: { level?: string; page?: number; pageSize?: number }) {
  return request.get(`/kb/${kbId}/listeners/${listenerId}/logs`, { params })
}
export async function clearListenerLogs(kbId: string, listenerId: string, beforeDate?: string) {
  return request.delete(`/kb/${kbId}/listeners/${listenerId}/logs`, { params: { beforeDate } })
}
export async function getListenerStats(kbId: string, listenerId: string) {
  return request.get(`/kb/${kbId}/listeners/${listenerId}/stats`)
}
export async function getListenerTrends(kbId: string, listenerId: string, days?: number) {
  return request.get(`/kb/${kbId}/listeners/${listenerId}/trends`, { params: { days } })
}
export async function setListenerAlerts(kbId: string, listenerId: string, config: Record<string, unknown>) {
  return request.post(`/kb/${kbId}/listeners/${listenerId}/alerts`, config)
}
export async function getOnlineVersion(kbId: string, knowledgeId?: string) {
  return request.get(`/kb/${kbId}/publish/online-version`, { params: { knowledgeId } })
}
export async function getOfflineVersion(kbId: string, knowledgeId?: string) {
  return request.get(`/kb/${kbId}/publish/offline-version`, { params: { knowledgeId } })
}
export async function getReviewHistory(kbId: string, strategyId: string) {
  return request.get(`/kb/${kbId}/review-strategies/${strategyId}/history`)
}
export async function setReviewTimeout(kbId: string, strategyId: string, config: Record<string, unknown>) {
  return request.put(`/kb/${kbId}/review-strategies/${strategyId}/timeout`, config)
}
export async function generatePublishReport(kbId: string) {
  return request.get(`/kb/${kbId}/publish/report`)
}
export async function exportPublishData(kbId: string, format?: string) {
  return request.get(`/kb/${kbId}/publish/export`, { params: { format } })
}
export async function getPublishEfficiency(kbId: string) {
  return request.get(`/kb/${kbId}/publish/efficiency`)
}
export async function getPublishFlowChart(kbId: string) {
  return request.get(`/kb/${kbId}/publish/flow-chart`)
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
// P3 新功能 API
// ===========================================================================
export async function exportReviewRecords(kbId: string) {
  return request.get(`/kb/${kbId}/publish/export-review-records`, { responseType: 'blob' })
}
export async function importReviewKnowledge(kbId: string, items: Record<string, unknown>[]) {
  return request.post(`/kb/${kbId}/publish/import-review-knowledge`, items)
}
export async function exportUnreviewedKnowledge(kbId: string) {
  return request.get(`/kb/${kbId}/publish/export-unreviewed`, { responseType: 'blob' })
}
export async function importFlowChart(kbId: string, data: Record<string, unknown>) {
  return request.post(`/kb/${kbId}/publish/import-flow-chart`, data)
}
export async function getLogRetention(kbId: string) {
  return request.get(`/kb/${kbId}/publish/log-retention`)
}
export async function setLogRetention(kbId: string, config: Record<string, unknown>) {
  return request.put(`/kb/${kbId}/publish/log-retention`, config)
}
export async function getReviewMetrics(kbId: string) {
  return request.get(`/kb/${kbId}/publish/review-metrics`)
}
export async function getReviewOptimizations(kbId: string) {
  return request.get(`/kb/${kbId}/publish/review-optimizations`)
}
export async function applyReviewOptimization(kbId: string, optId: string) {
  return request.post(`/kb/${kbId}/publish/review-optimizations/${optId}/apply`)
}
export async function getReviewTimeoutRecords(kbId: string) {
  return request.get(`/kb/${kbId}/review/timeout-records`)
}
export async function setReviewTimeoutConfig(kbId: string, data: Record<string, unknown>) {
  return request.put(`/kb/${kbId}/review/timeout-config`, data)
}
export async function getReviewTimeoutConfig(kbId: string) {
  return request.get(`/kb/${kbId}/review/timeout-config`)
}
export async function getPublishDetail(kbId: string, id: string) {
  return request.get(`/kb/${kbId}/publish/${id}`)
}
export async function getReviewDetail(kbId: string, id: string) {
  return request.get(`/kb/${kbId}/review/${id}`)
}
export async function exportPublishReport(kbId: string) {
  return request.get(`/kb/${kbId}/publish/report/export`, { responseType: 'blob' })
}
export async function getReviewCoverage(kbId: string) {
  return request.get(`/kb/${kbId}/review/coverage`)
}
export async function getKnowledgeUpdateLogs(kbId: string, page?: number, pageSize?: number) {
  return request.get(`/kb/${kbId}/knowledge-update-logs`, { params: { page, pageSize } })
}
export async function markUpdateLogRead(kbId: string, id: string) {
  return request.put(`/kb/${kbId}/update-logs/${id}/read`)
}
export async function compareKnowledgeContent(kbId: string, oldId: string, newId: string) {
  return request.get(`/kb/${kbId}/knowledge-compare`, { params: { oldId, newId } })
}
export async function setAutoKnowledgeUpdate(appId: string, config: Record<string, unknown>) {
  return request.put(`/apps/${appId}/knowledge-update`, config)
}
export async function getAutoKnowledgeUpdate(appId: string) {
  return request.get(`/apps/${appId}/knowledge-update`)
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
