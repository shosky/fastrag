#!/usr/bin/env node
/**
 * 功能→前端入口 静态完整性审计
 * 对照 docs/feature-entry-map.md 的 9 模块功能清单，逐功能校验五层对齐：
 *   1) 前端视图文件存在  2) 路由注册（页面级）或 Tab/组件注册（详情页级）
 *   3) 侧边栏菜单可达（页面级）  4) 前端 API 函数存在  5) 后端端点存在
 * 运行：node scripts/feature-entry-audit.mjs   （无需启动任何服务）
 */
import { existsSync, readFileSync } from 'node:fs'
import { join } from 'node:path'

const ROOT = join(import.meta.dirname, '..')
const FE = join(ROOT, 'fastrag-frontend/src')
const BE = join(ROOT, 'fastrag-backend')
const read = (p) => { try { return readFileSync(p, 'utf8') } catch { return '' } }

const ROUTES = read(join(FE, 'router/routes.ts'))
const SIDEBAR = read(join(FE, 'components/layout/Sidebar.vue'))
const DETAIL = read(join(FE, 'views/knowledge/detail/index.vue'))
const API = read(join(FE, 'api/index.ts'))

let passCount = 0, failCount = 0
const failures = []

/** tab 功能：marker 需在 detail/index.vue 或指定宿主文件中出现 */
function check(f) {
  const problems = []
  // 1. 视图文件
  for (const file of f.files) {
    if (!existsSync(join(FE, file))) problems.push(`视图文件缺失: ${file}`)
  }
  // 2. 路由 / Tab
  if (f.route) { const rp = f.route.replace(/^\//, ''); if (!ROUTES.includes(f.route) && !ROUTES.includes(rp)) problems.push(`路由未注册: ${f.route}`) }
  if (f.tab) {
    const host = f.tabHost ? read(join(FE, f.tabHost)) : DETAIL
    for (const marker of f.tab) if (!host.includes(marker)) problems.push(`Tab/组件未注册: ${marker}`)
  }
  // 3. 菜单
  if (f.menu && !SIDEBAR.includes(f.menu)) problems.push(`侧边栏无入口: ${f.menu}`)
  // 4. API 函数
  for (const api of f.api || []) {
    if (!API.includes(`function ${api}`)) problems.push(`API 函数缺失: ${api}()`)
  }
  // 5. 后端端点（文件 + 路径字符串）
  for (const [file, path] of f.backend || []) {
    const src = read(join(BE, file))
    if (!src) problems.push(`后端文件缺失: ${file}`)
    else if (!src.includes(path)) problems.push(`后端端点缺失: ${file} 中无 ${path}`)
  }
  if (problems.length === 0) { passCount++; console.log(`PASS  [${f.module}] ${f.feature}`) }
  else { failCount++; failures.push(f.feature); console.log(`FAIL  [${f.module}] ${f.feature}\n      - ${problems.join('\n      - ')}`) }
}

const F = []
const A = (module, feature, spec) => F.push({ module, feature, ...spec })

/* ============ 一、知识管理 ============ */
A('知识管理', '知识库结构设计', { files: ['views/knowledge/index.vue'], route: '/knowledge', menu: '/knowledge', api: ['getKnowledgeBases'], backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/KbController.java', '@RequestMapping("/api/kb")']] })
A('知识管理', '知识条目创建', { files: ['views/knowledge/detail/knowledge-manage.vue'], tab: ['KnowledgeManagePanel', 'knowledge-manage'], api: ['createKnowledge'], backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/KnowledgeManageController.java', '@PostMapping("/knowledge")']] })
A('知识管理', '知识分类管理', { files: ['views/knowledge/categories.vue'], route: '/knowledge/categories', menu: '/knowledge/categories', api: ['getKnowledgeBaseCategories'], backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/KbCategoryController.java', '@RequestMapping("/api/kb-categories")']] })
A('知识管理', '知识审核流程', { files: ['views/knowledge-review/flows.vue'], route: '/knowledge-review/flows', menu: '/knowledge-review/flows', backend: [['fastrag-modules/fastrag-publish/src/main/java/com/fastrag/module/publish/controller/PublishController.java', '/api/reviews']] })
A('知识管理', '知识更新记录', { files: ['views/knowledge/detail/knowledge-manage.vue'], tab: ['KnowledgeManagePanel'], backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/KnowledgeUpdateController.java', '/knowledge-updates']] })
A('知识管理', '知识标签管理', { files: ['views/knowledge/detail/tags-notes.vue'], tab: ['TagsNotesPanel'], route: '/knowledge/tags', backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/TagController.java', '@GetMapping("/tags")']] })
A('知识管理', '知识搜索优化', { files: ['views/knowledge/detail/smart-search.vue'], tab: ['SmartSearchPanel'], backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/SmartSearchController.java', '/search-associations']] })
A('知识管理', '知识关联推荐', { files: ['views/knowledge/detail/knowledge-manage.vue'], tab: ['handleViewDetail'], tabHost: 'views/knowledge/detail/knowledge-manage.vue', api: ['getRelatedKnowledge'], backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/RelatedKnowledgeController.java', '/knowledge/{id}/related']] })
A('知识管理', '知识导入导出', { files: ['views/knowledge/edit.vue'], api: ['exportKnowledgeBase'], backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/KbController.java', '/export']] })
A('知识管理', '知识权限管理', { files: ['views/admin/permissions/index.vue'], route: '/admin/permissions', menu: '/admin/permissions', backend: [['fastrag-modules/fastrag-iam/src/main/java/com/fastrag/module/iam/controller/KbAclController.java', '/acl']] })
A('知识管理', '知识质量评估', { files: ['views/knowledge-review/quality.vue'], route: '/knowledge-review/quality', menu: '/knowledge-review/quality', backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/PublishManageController.java', '/quality-rules']] })
A('知识管理', '知识库同步机制', { files: ['views/knowledge/sync.vue'], route: '/knowledge/sync', menu: '/knowledge/sync', api: ['getKbSyncConfigs', 'runKbSync', 'getKbSyncRecords'], backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/KbSyncController.java', '@RequestMapping("/api/kb-sync")'], ['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/service/impl/KbSyncServiceImpl.java', '@Scheduled']] })
A('知识管理', '知识库报表分析', { files: ['views/operation/kb-analytics.vue'], route: '/operation/kb-analytics', menu: '/operation/kb-analytics', api: ['getKbAnalytics'], backend: [['fastrag-modules/fastrag-operation/src/main/java/com/fastrag/module/operation/controller/AnalyticsController.java', '@GetMapping("/kb")']] })
A('知识管理', '知识库用户反馈', { files: ['views/operation/feedback.vue'], route: '/operation/feedback', menu: '/operation/feedback', backend: [['fastrag-modules/fastrag-operation/src/main/java/com/fastrag/module/operation/controller/FeedbackController.java', '/api/feedback']] })

/* ============ 二、应答知识库管理 ============ */
const QAPANEL = 'views/knowledge/detail/components/QaPanel.vue'
A('应答知识库管理', 'FAQ知识应答', { files: [QAPANEL], tab: ['QaPanel'], api: ['getQaPairs', 'createQaPair'], backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/QaPairController.java', '@RequestMapping("/api/kb/{kbId}/qa-pairs")']] })
A('应答知识库管理', 'FAQ常见问题', { files: [QAPANEL], tab: ['常见问题'], tabHost: 'views/knowledge/detail/components/QaPanel.vue', backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/entity/KbQaPair.java', 'faqType']] })
A('应答知识库管理', 'FAQ非常见问题', { files: [QAPANEL], tab: ['非常见问题'], tabHost: 'views/knowledge/detail/components/QaPanel.vue', backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/entity/KbQaPair.java', 'faqType']] })
A('应答知识库管理', 'FAQ知识库', { files: ['views/knowledge/form.vue'], tab: ['FAQ 问答库'], tabHost: 'views/knowledge/form.vue', backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/model/KbCreateRequest.java', 'kbType'], ['fastrag-bootstrap/src/main/java/com/fastrag/config/SchemaInitializer.java', '"kb", "kb_type"']] })
A('应答知识库管理', '按应答添加知识', { files: [QAPANEL], tab: ['按应答添加知识'], tabHost: 'views/knowledge/detail/components/QaPanel.vue', api: ['qaPairToKnowledge'], backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/QaPairController.java', '/to-knowledge']] })
A('应答知识库管理', '多关键词配置知识', { files: [QAPANEL], tab: ['多关键词'], tabHost: 'views/knowledge/detail/components/QaPanel.vue', backend: [['fastrag-modules/fastrag-retrieval/src/main/java/com/fastrag/module/retrieval/service/impl/RetrievalServiceImpl.java', 'searchQaPairs']] })
A('应答知识库管理', '关联知识', { files: [QAPANEL], tab: ['关联知识'], tabHost: 'views/knowledge/detail/components/QaPanel.vue', api: ['getRelatedKnowledge'], backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/entity/KbQaPair.java', 'relatedKnowledgeIds']] })
A('应答知识库管理', '生效时间设置', { files: [QAPANEL], tab: ['生效时间'], tabHost: 'views/knowledge/detail/components/QaPanel.vue', backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/entity/KbQaPair.java', 'effectiveStart']] })
A('应答知识库管理', '生效功能设置', { files: [QAPANEL], tab: ['生效功能'], tabHost: 'views/knowledge/detail/components/QaPanel.vue', backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/entity/KbQaPair.java', 'effectiveScope']] })
A('应答知识库管理', '属性管理', { files: ['views/knowledge/detail/knowledge-manage.vue'], tab: ['属性管理', 'handleOpenAttrs'], tabHost: 'views/knowledge/detail/knowledge-manage.vue', api: ['getAttributeDefs'], backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/AttributeDefController.java', '@RequestMapping("/api/kb/{kbId}/attributes")']] })
A('应答知识库管理', '添加属性', { files: ['views/knowledge/detail/knowledge-manage.vue'], tab: ['添加属性'], tabHost: 'views/knowledge/detail/knowledge-manage.vue', api: ['createAttributeDef'], backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/AttributeDefController.java', '@PostMapping']] })
A('应答知识库管理', '导入', { files: [QAPANEL], tab: ['AI 抽取'], tabHost: 'views/knowledge/detail/components/QaPanel.vue', api: ['qaExtract'], backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/QaPairController.java', '/qa-extract']] })
A('应答知识库管理', '导出', { files: ['views/knowledge/detail/knowledge-edit.vue'], api: ['exportKnowledgeEdits'], backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/KnowledgeEditController.java', '/export']] })
A('应答知识库管理', '新增表格', { files: ['views/knowledge/detail/components/TableKnowledgePanel.vue'], tab: ['TableKnowledgePanel', 'name="tables"'], api: ['getAnswerTables', 'createAnswerTable', 'deleteAnswerTable'], backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/AnswerTableController.java', '@RequestMapping("/api/kb/{kbId}/answer-tables")']] })
A('应答知识库管理', '新增文档', { files: ['views/knowledge/detail/components/FileManager.vue'], tab: ['FileManager'], backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/FileController.java', '@RequestMapping("/api/kb/{kbId}/files")']] })
A('应答知识库管理', '表格增加列', { files: ['views/knowledge/detail/components/TableKnowledgePanel.vue'], tab: ['表格增加列'], tabHost: 'views/knowledge/detail/components/TableKnowledgePanel.vue', api: ['addAnswerTableColumn'], backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/AnswerTableController.java', '/columns']] })
A('应答知识库管理', '表格内容', { files: ['views/knowledge/detail/components/TableKnowledgePanel.vue'], tab: ['添加行'], tabHost: 'views/knowledge/detail/components/TableKnowledgePanel.vue', api: ['addAnswerTableRow', 'updateAnswerTableRow'], backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/AnswerTableController.java', '/rows']] })

/* ============ 三、业务管理 ============ */
A('业务管理', '知识搜索查询', { files: ['views/search-center/index.vue'], route: '/search-center', menu: '/search-center', api: ['searchRetrieval', 'querySuggest'], backend: [['fastrag-modules/fastrag-retrieval/src/main/java/com/fastrag/module/retrieval/controller/RetrievalController.java', '/api/retrieval/search']] })
A('业务管理', '知识搜索结果列表展示', { files: ['views/search-center/index.vue'], tab: ['搜索结果'], tabHost: 'views/search-center/index.vue', tabHost: 'views/search-center/index.vue' })

/* ============ 四、座席端管理 ============ */
A('座席端管理', '知识推荐（识别/推荐）', { files: ['views/agent-console/index.vue'], route: '/agent-console', menu: '/agent-console', api: ['judgeKeywords', 'searchRetrieval'], backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/KeywordController.java', '/judge']] })
A('座席端管理', '知识流程搜索', { files: ['views/agent-console/index.vue'], tab: ['知识流程搜索'], tabHost: 'views/agent-console/index.vue', tabHost: 'views/agent-console/index.vue', api: [], backend: [['fastrag-modules/fastrag-bpm/src/main/java/com/fastrag/module/bpm/controller/FlowDefController.java', '@GetMapping("/list")']] })

/* ============ 五、管理端管理 ============ */
A('管理端管理', '标准问答', { files: ['views/knowledge/detail/smart-search.vue'], tab: ['标准问法'], tabHost: 'views/knowledge/detail/smart-search.vue', backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/QuestionController.java', '@RequestMapping("/api/kb/{kbId}/questions")']] })
A('管理端管理', '相似问法', { files: ['views/knowledge/detail/smart-search.vue'], tab: ['相似问法'], tabHost: 'views/knowledge/detail/smart-search.vue', backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/QuestionController.java', '@RequestMapping("/api/kb/{kbId}/questions")']] })
A('管理端管理', '标准回复', { files: ['components/common/RichTextEditor.vue', 'views/knowledge/detail/smart-search.vue'], tab: ['RichTextEditor'], tabHost: 'views/knowledge/detail/smart-search.vue' })
A('管理端管理', '关键词推荐', { files: ['views/knowledge/detail/components/SearchTestPanel.vue'], tab: ['getKeywordRecommendations', 'loadKeywordRecommendations'], tabHost: 'views/knowledge/detail/components/SearchTestPanel.vue', tabHost: 'views/knowledge/detail/components/SearchTestPanel.vue', api: ['getKeywordRecommendations', 'judgeKeywords'], backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/KeywordController.java', '/recommend']] })
A('管理端管理', '知识搜索', { files: ['views/knowledge/detail/components/SearchTestPanel.vue'], tab: ['SearchTestPanel'], api: ['searchRetrieval'], backend: [['fastrag-modules/fastrag-retrieval/src/main/java/com/fastrag/module/retrieval/controller/RetrievalController.java', '/api/retrieval/search']] })
A('管理端管理', '知识库排行', { files: ['views/operation/kb-analytics.vue'], tab: ['排行'], tabHost: 'views/operation/kb-analytics.vue', api: ['getKbAnalytics'], backend: [['fastrag-modules/fastrag-operation/src/main/java/com/fastrag/module/operation/controller/AnalyticsController.java', 'windowStart']] })
A('管理端管理', '数据展示管理', { files: ['views/operation/kb-analytics.vue'], tab: ['metric-card'], tabHost: 'views/operation/kb-analytics.vue' })
A('管理端管理', '知识库维护', { files: ['views/knowledge/index.vue', 'views/knowledge/detail/knowledge-manage.vue'], tab: ['回收站'], tabHost: 'views/knowledge/detail/knowledge-manage.vue', api: ['deleteKnowledgeBase'], backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/KnowledgeManageController.java', '/restore']] })
A('管理端管理', '问答知识管理', { files: ['views/knowledge/categories.vue'], route: '/knowledge/categories', backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/KbCategoryController.java', '@PostMapping']] })
A('管理端管理', '知识录入', { files: ['views/knowledge/detail/knowledge-manage.vue'], tab: ['handleViewDetail', '新增知识'], tabHost: 'views/knowledge/detail/knowledge-manage.vue', backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/KnowledgeManageController.java', '@GetMapping("/knowledge/{id}")']] })
A('管理端管理', '标准问法管理', { files: ['views/knowledge/detail/smart-search.vue'], tab: ['stdForm'], tabHost: 'views/knowledge/detail/smart-search.vue' })
A('管理端管理', '相似问法管理', { files: ['views/knowledge/detail/smart-search.vue'], tab: ['simForm'], tabHost: 'views/knowledge/detail/smart-search.vue', tabHost: 'views/knowledge/detail/smart-search.vue' })
A('管理端管理', '标准回复管理', { files: ['views/knowledge/detail/smart-search.vue'], tab: ['RichTextEditor'], tabHost: 'views/knowledge/detail/smart-search.vue', tabHost: 'views/knowledge/detail/smart-search.vue' })

/* ============ 六、知识检索 ============ */
const STP = 'views/knowledge/detail/components/SearchTestPanel.vue'
const RSI = 'fastrag-modules/fastrag-retrieval/src/main/java/com/fastrag/module/retrieval/service/impl/RetrievalServiceImpl.java'
A('知识检索', '语义关联检索', { files: [STP], tab: ['searchRetrieval'], tabHost: STP, backend: [[RSI, 'resolveVectors'], ['fastrag-ai/src/main/java/com/fastrag/ai/embedding/EmbeddingService.java', '/v1/embeddings']] })
A('知识检索', '关键词检索', { files: [STP], tab: ['handleSearch'], tabHost: 'views/knowledge/detail/components/SearchTestPanel.vue', tabHost: STP, backend: [[RSI, 'keywordScore']] })
A('知识检索', '智能推荐', { files: [STP], tab: ['关键词'], tabHost: STP, api: ['getKeywordRecommendations'], backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/KeywordController.java', '/recommend']] })
A('知识检索', '手动搜索', { files: [STP], tab: ['handleSearch'], tabHost: STP, api: ['searchRetrieval'] })
A('知识检索', '知识库索引', { files: ['views/knowledge/detail/components/KnowledgeGraphPanel.vue'], tab: ['KnowledgeGraphPanel'], backend: [['fastrag-modules/fastrag-graph-eval/src/main/java/com/fastrag/module/graph/controller/GraphController.java', '/index/build']] })
A('知识检索', '检索结果排序', { files: [STP], backend: [[RSI, 'results.sort']] })
A('知识检索', '检索历史', { files: [STP], tab: ['loadHistoryFromServer'], tabHost: 'views/knowledge/detail/components/SearchTestPanel.vue', tabHost: STP, api: ['getRetrievalLogs'], backend: [['fastrag-modules/fastrag-retrieval/src/main/java/com/fastrag/module/retrieval/controller/RetrievalController.java', '/api/retrieval/logs']] })
A('知识检索', '相关度评分', { files: [STP], tab: ['相似度'], tabHost: 'views/knowledge/detail/components/SearchTestPanel.vue', tabHost: STP, backend: [[RSI, 'setSimilarity']] })
A('知识检索', '检索结果过滤', { files: [STP], backend: [[RSI, 'getSimilarityThreshold'], [RSI, 'preferTags']] })
A('知识检索', '知识分类导航', { files: ['views/knowledge/categories.vue'], route: '/knowledge/categories', backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/KbCategoryController.java', '/api/kb-categories']] })
A('知识检索', '热门知识推荐', { files: ['views/operation/kb-analytics.vue'], tab: ['hotDocs'], tabHost: 'views/operation/kb-analytics.vue', api: ['getKbAnalytics'], backend: [['fastrag-modules/fastrag-operation/src/main/java/com/fastrag/module/operation/controller/AnalyticsController.java', 'hotDocs']] })
A('知识检索', '检索词联想', { files: ['views/knowledge/detail/smart-search.vue'], tab: ['联想'], tabHost: 'views/knowledge/detail/smart-search.vue', tabHost: 'views/knowledge/detail/smart-search.vue', api: ['querySuggest'], backend: [['fastrag-modules/fastrag-retrieval/src/main/java/com/fastrag/module/retrieval/service/impl/QueryEnhanceServiceImpl.java', 'suggestedQuery']] })
A('知识检索', '检索结果预览', { files: [STP], tab: ['previewSnippet'], tabHost: 'views/knowledge/detail/components/SearchTestPanel.vue', tabHost: STP, backend: [[RSI, 'applyHighlight']] })
A('知识检索', '知识更新提醒', { files: ['views/knowledge/detail/smart-search.vue'], tab: ['更新提醒'], tabHost: 'views/knowledge/detail/smart-search.vue', tabHost: 'views/knowledge/detail/smart-search.vue', backend: [['fastrag-modules/fastrag-retrieval/src/main/java/com/fastrag/module/retrieval/controller/RetrievalController.java', '/api/update-remind']] })
A('知识检索', '检索效果分析', { files: ['views/operation/retrieval-analysis.vue'], route: '/operation/retrieval-analysis', menu: '/operation/retrieval-analysis', api: ['getRetrievalLogAnalysis'], backend: [['fastrag-modules/fastrag-retrieval/src/main/java/com/fastrag/module/retrieval/controller/RetrievalController.java', '/analysis']] })
A('知识检索', '无结果处理', { files: [STP], tab: ['为您推荐'], tabHost: 'views/knowledge/detail/components/SearchTestPanel.vue', tabHost: STP, backend: [[RSI, 'fallbackMode']] })
A('知识检索', '检索权限控制', { files: [STP], backend: [['fastrag-modules/fastrag-retrieval/src/main/java/com/fastrag/module/retrieval/controller/RetrievalController.java', 'checkKbAcl']] })
A('知识检索', '检索词纠错', { files: ['services/query-preprocess.ts', STP], tab: ['autoCorrect'], tabHost: 'views/knowledge/detail/components/SearchTestPanel.vue', tabHost: STP, backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/SmartSearchController.java', '/auto-corrections']] })
A('知识检索', '多模态检索', { files: ['views/knowledge/detail/knowledge-qa.vue'], tab: ['KnowledgeQaPanel'], backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/service/impl/KnowledgeQaServiceImpl.java', 'multimodalSearch']] })
A('知识检索', '检索偏好设置', { files: ['views/knowledge/detail/smart-search.vue'], tab: ['检索偏好'], tabHost: 'views/knowledge/detail/smart-search.vue', tabHost: 'views/knowledge/detail/smart-search.vue', backend: [['fastrag-modules/fastrag-retrieval/src/main/java/com/fastrag/module/retrieval/controller/RetrievalController.java', '/search-preferences']] })
A('知识检索', '知识标签检索', { files: ['views/knowledge/detail/tags-notes.vue'], tab: ['TagsNotesPanel'], backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/TagController.java', '/tags/{id}/knowledge']] })
A('知识检索', '检索日志分析', { files: ['views/operation/retrieval-analysis.vue'], route: '/operation/retrieval-analysis', api: ['getRetrievalLogs'], backend: [['fastrag-modules/fastrag-retrieval/src/main/java/com/fastrag/module/retrieval/controller/RetrievalController.java', '@GetMapping("/api/retrieval/logs")']] })
A('知识检索', '知识推送', { files: ['views/knowledge/detail/smart-search.vue'], tab: ['知识推送'], tabHost: 'views/knowledge/detail/smart-search.vue', tabHost: 'views/knowledge/detail/smart-search.vue', api: ['createKnowledgePush', 'sendKnowledgePush'], backend: [['fastrag-modules/fastrag-retrieval/src/main/java/com/fastrag/module/retrieval/controller/RetrievalController.java', '/knowledge-pushes/{id}/send']] })
A('知识检索', '知识库更新通知', { files: ['views/admin/audit/system-log.vue'], route: 'notifications', menu: '/admin/notifications', backend: [['fastrag-modules/fastrag-platform/src/main/java/com/fastrag/module/platform/controller/NotificationController.java', '/api/notifications']] })

/* ============ 七、机器人管理 ============ */
A('机器人管理', '知识配置（增删改查/导入/判断）', { files: ['views/knowledge/detail/knowledge-manage.vue'], tab: ['新增知识', 'Edit'], tabHost: 'views/knowledge/detail/knowledge-manage.vue', backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/KnowledgeValidateController.java', '/knowledge-validate']] })

/* ============ 八、对话知识 ============ */
A('对话知识', '知识新增', { files: ['views/knowledge/detail/knowledge-manage.vue'], tab: ['新增知识'], tabHost: 'views/knowledge/detail/knowledge-manage.vue', backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/KnowledgeManageController.java', '@PostMapping("/knowledge")']] })
A('对话知识', '知识导入', { files: ['views/knowledge/detail/knowledge-edit.vue'], tab: ['KnowledgeEditPanel'], backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/KnowledgeEditController.java', '/import']] })
A('对话知识', '智能推荐', { files: ['views/knowledge/detail/smart-search.vue'], tab: ['推荐相似'], tabHost: 'views/knowledge/detail/smart-search.vue', tabHost: 'views/knowledge/detail/smart-search.vue', api: ['recommendSimilarQuestions'], backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/QuestionController.java', '/recommend']] })
A('对话知识', '对话流配置', { files: ['views/application/editor/components/WorkflowConfig.vue'], tab: ['WorkflowConfig'], tabHost: 'views/application/editor/index.vue', backend: [['fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/controller/AppConfigController.java', 'workflow-config']] })
A('对话知识', '语义理解', { files: ['views/application/editor/components/SemanticConfig.vue'], tab: ['SemanticConfig'], tabHost: 'views/application/editor/index.vue', api: ['getSemanticConfig', 'judgeSemantic'], backend: [['fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/controller/SemanticConfigController.java', '@RequestMapping("/api/apps/{appId}/semantic-config")']] })
A('对话知识', '语义定制', { files: ['views/application/editor/components/SemanticConfig.vue'], tab: ['SemanticConfig'], tabHost: 'views/application/editor/index.vue', api: ['saveSemanticConfig'], backend: [['fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/entity/AppSemanticConfig.java', 'customSynonyms']] })
A('对话知识', '全局变量', { files: ['views/application/editor/components/GlobalConfig.vue'], tab: ['GlobalConfig'], tabHost: 'views/application/editor/index.vue', backend: [['fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/controller/AppConfigController.java', '/global-policy/variables']] })
A('对话知识', '实体管理', { files: ['views/knowledge/detail/entities.vue'], route: '/knowledge/:id/entities', api: [], backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/EntityController.java', '/entities']] })
A('对话知识', 'API插件（配置/编辑）', { files: ['views/application/my-tools.vue'], route: '/application/my-tools', menu: '/application/my-tools', backend: [['fastrag-modules/fastrag-tools/src/main/java/com/fastrag/module/tools/controller/ToolController.java', '/api/tools']] })
A('对话知识', '模型预置（编辑）', { files: ['views/admin/platform/model-management.vue'], tab: ['模型预置'], tabHost: 'views/admin/platform/model-management.vue', api: [], backend: [['fastrag-modules/fastrag-platform/src/main/java/com/fastrag/module/platform/controller/ModelController.java', '/presets']] })
A('对话知识', '模型阈值设置', { files: ['views/admin/platform/model-management.vue'], tab: ['handleThreshold', 'showThresholdDialog'], tabHost: 'views/admin/platform/model-management.vue', api: ['getModelThreshold', 'updateModelThreshold'], backend: [['fastrag-modules/fastrag-platform/src/main/java/com/fastrag/module/platform/controller/ModelController.java', '/threshold']] })
A('对话知识', '模型导入（导入/编辑）', { files: ['views/admin/platform/model-management.vue'], tab: ['showImportDialog'], tabHost: 'views/admin/platform/model-management.vue', backend: [['fastrag-modules/fastrag-platform/src/main/java/com/fastrag/module/platform/controller/ModelController.java', '@PostMapping("/import")']] })

/* ============ 九、机器人运营 ============ */
A('机器人运营', '数据挖掘（新增/智能/一键判断）', { files: ['views/robot-operation/data-mining.vue'], route: '/robot-operation/data-mining', menu: '/robot-operation/data-mining', backend: [['fastrag-modules/fastrag-operation/src/main/java/com/fastrag/module/operation/controller/DataMiningController.java', '/api/data-mining'], ['fastrag-modules/fastrag-operation/src/main/java/com/fastrag/module/operation/service/impl/DataMiningServiceImpl.java', 'topKeywords']] })
A('机器人运营', '知识优化', { files: ['views/application/editor/components/DialogOptimize.vue'], tab: ['DialogOptimize'], tabHost: 'views/application/editor/index.vue', backend: [['fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/controller/AppConfigController.java', 'optimizations']] })
A('机器人运营', 'FAQ知识分析', { files: ['views/robot-operation/faq-analysis.vue'], route: '/robot-operation/faq-analysis', menu: '/robot-operation/faq-analysis', api: ['getFaqAnalysis'], backend: [['fastrag-modules/fastrag-operation/src/main/java/com/fastrag/module/operation/controller/AnalyticsController.java', '@GetMapping("/faq")']] })
A('机器人运营', '多轮知识分析', { files: ['views/robot-operation/multi-turn.vue'], route: '/robot-operation/multi-turn', menu: '/robot-operation/multi-turn', api: ['getMultiTurnAnalysis'], backend: [['fastrag-modules/fastrag-operation/src/main/java/com/fastrag/module/operation/controller/AnalyticsController.java', '@GetMapping("/multi-turn")']] })
A('机器人运营', '意图知识分析', { files: ['views/robot-operation/intent.vue'], route: '/robot-operation/intent', menu: '/robot-operation/intent', api: ['getIntentAnalysis'], backend: [['fastrag-modules/fastrag-operation/src/main/java/com/fastrag/module/operation/controller/AnalyticsController.java', '@GetMapping("/intent")']] })

/* ============ 十、知识加工与采编 ============ */
A('知识加工与采编', '多媒体处理-编辑多媒体处理', { files: ['views/knowledge/detail/production.vue'], tab: ['handleEdit', '编辑多媒体'], tabHost: 'views/knowledge/detail/production.vue', api: ['updateStorage', 'getMediaList'], backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/MediaStorageController.java', '@PutMapping']] })
A('知识加工与采编', '知识采编管理-导入', { files: ['views/knowledge/detail/knowledge-edit.vue'], tab: ['handleImport'], tabHost: 'views/knowledge/detail/knowledge-edit.vue', api: ['importKnowledgeEdits'], backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/KnowledgeEditController.java', '/import']] })
A('知识加工与采编', '知识工单-新增', { files: ['views/knowledge/detail/knowledge-edit.vue'], tab: ['新增工单', 'handleAddTicket'], tabHost: 'views/knowledge/detail/knowledge-edit.vue', api: ['createKnowledgeTicket'], backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/KnowledgeTicketController.java', '@PostMapping']] })
A('知识加工与采编', '知识工单-编辑', { files: ['views/knowledge/detail/knowledge-edit.vue'], tab: ['handleEditTicket'], tabHost: 'views/knowledge/detail/knowledge-edit.vue', api: ['updateKnowledgeTicket'], backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/KnowledgeTicketController.java', '@PutMapping']] })
A('知识加工与采编', '知识工单-删除', { files: ['views/knowledge/detail/knowledge-edit.vue'], tab: ['handleDeleteTicket'], tabHost: 'views/knowledge/detail/knowledge-edit.vue', api: ['deleteKnowledgeTicket'], backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/KnowledgeTicketController.java', '@DeleteMapping']] })
A('知识加工与采编', '知识工单-查看', { files: ['views/knowledge/detail/knowledge-edit.vue'], tab: ['handleViewTicket'], tabHost: 'views/knowledge/detail/knowledge-edit.vue', api: ['getKnowledgeTicket'], backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/KnowledgeTicketController.java', '@GetMapping']] })
A('知识加工与采编', '知识回收-编辑/恢复', { files: ['views/knowledge/detail/knowledge-manage.vue'], tab: ['回收站', 'handleEditRecycle'], tabHost: 'views/knowledge/detail/knowledge-manage.vue', api: ['restoreKnowledge', 'permanentDeleteKnowledge'], backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/KnowledgeManageController.java', '/restore']] })
A('知识加工与采编', '事项知识关联管理-增删改查', { files: ['views/knowledge/detail/knowledge-manage.vue'], tab: ['事项关联', 'handleAddMatter', 'handleViewMatter', 'handleEditMatter', 'handleDeleteMatter'], tabHost: 'views/knowledge/detail/knowledge-manage.vue', api: ['getMatterKnowledgeRels', 'createMatterKnowledgeRel', 'updateMatterKnowledgeRel', 'deleteMatterKnowledgeRel'], backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/MatterKnowledgeRelController.java', '@RequestMapping']] })
A('知识加工与采编', '存量知识点校验-判断', { files: ['views/knowledge/detail/knowledge-edit.vue'], tab: ['发起校验', 'handleStartCheck'], tabHost: 'views/knowledge/detail/knowledge-edit.vue', api: ['checkKnowledgeValidate'], backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/KnowledgeValidateController.java', '/check']] })

/* ============ 十一、知识更新 ============ */
A('知识更新', '知识更新处理-编辑', { files: ['views/knowledge/detail/knowledge-manage.vue'], tab: ['知识更新', 'handleEditUpdate'], tabHost: 'views/knowledge/detail/knowledge-manage.vue', api: ['getKnowledgeUpdates', 'updateKnowledgeUpdate', 'applyKnowledgeUpdate', 'rollbackKnowledgeUpdate'], backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/KnowledgeUpdateController.java', '/knowledge-updates']] })

/* ============ 十二、知识问答 ============ */
const KQA = 'views/knowledge/detail/knowledge-qa.vue'
const KQA_BE = ['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/KnowledgeQaController.java', '@RequestMapping']
A('知识问答', '文档问答-编辑', { files: [KQA], tab: ['多轮问答', 'handleAddMt'], tabHost: KQA, api: ['getMultiTurnQa', 'createMultiTurnQa', 'updateMultiTurnQa', 'deleteMultiTurnQa'], backend: [[KQA_BE[0], '/multi-turn-qa']] })
A('知识问答', '图片问答-增删改查', { files: [KQA], tab: ['图片问答', 'handleAddMm'], tabHost: KQA, api: ['getMultimodalQa', 'createMultimodalQa', 'updateMultimodalQa', 'deleteMultimodalQa'], backend: [[KQA_BE[0], '/multimodal-qa']] })
A('知识问答', '表格问答-增删改查', { files: [KQA], tab: ['表格问答'], tabHost: KQA, api: ['getMultimodalQa'], backend: [[KQA_BE[0], '/multimodal-qa']] })
A('知识问答', '文本问答-增删改查', { files: [KQA], tab: ['文本问答'], tabHost: KQA, api: ['getMultimodalQa'], backend: [[KQA_BE[0], '/multimodal-qa']] })
A('知识问答', '高亮定位-判断', { files: [KQA], tab: ['高亮定位', 'handleLocate'], tabHost: KQA, api: ['searchRetrieval'], backend: [['fastrag-modules/fastrag-retrieval/src/main/java/com/fastrag/module/retrieval/controller/RetrievalController.java', '/api/retrieval/search']] })
A('知识问答', '文档导读-编辑', { files: [KQA], tab: ['文档导读', 'handleAddDg'], tabHost: KQA, api: ['getDocGuides', 'createDocGuide', 'updateDocGuide', 'deleteDocGuide'], backend: [[KQA_BE[0], '/doc-guides']] })

/* ============ 十三、知识生产与获取 ============ */
A('知识生产与获取', '问答抽取-停止抽取', { files: ['views/knowledge/detail/production.vue'], tab: ['handleStopTask', '停止'], tabHost: 'views/knowledge/detail/production.vue', api: ['stopQaExtract'], backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/QaExtractController.java', '/stop']] })
A('知识生产与获取', '问答抽取-全部入库', { files: ['views/knowledge/detail/production.vue'], tab: ['全部入库', 'handleImportAll'], tabHost: 'views/knowledge/detail/production.vue', api: ['confirmAllQaPairs'], backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/QaPairController.java', '/confirm-all']] })

/* ============ 十四、系统设置管理 ============ */
const MM = 'views/admin/platform/model-management.vue'
A('系统设置管理', '模型管理-模型测试', { files: [MM], tab: ['handleRunChatTest'], tabHost: MM, api: ['testModel'], backend: [['fastrag-modules/fastrag-platform/src/main/java/com/fastrag/module/platform/controller/ConfigManageController.java', '/test']] })
A('系统设置管理', '模型管理-模型调用', { files: [MM], tab: ['handleInvoke'], tabHost: MM, api: ['invokeModel'], backend: [['fastrag-modules/fastrag-platform/src/main/java/com/fastrag/module/platform/controller/ModelController.java', '/invoke']] })
A('系统设置管理', '模型管理-查看模型日志', { files: [MM], tab: ['调用日志'], tabHost: MM, api: ['getModelCallLogs'], backend: [['fastrag-modules/fastrag-platform/src/main/java/com/fastrag/module/platform/controller/ModelController.java', '/call-logs']] })
A('系统设置管理', '配置管理-绑定审核流程', { files: ['views/admin/system/config-management.vue'], tab: ['绑定审核流程', 'handleSaveBinding'], tabHost: 'views/admin/system/config-management.vue', api: ['getReviewFlowBinding', 'updateReviewFlowBinding'], backend: [['fastrag-modules/fastrag-platform/src/main/java/com/fastrag/module/platform/controller/ConfigManageController.java', '/review-flow-binding']] })
A('系统设置管理', '配置管理-查看发布状态', { files: ['views/admin/system/config-management.vue'], tab: ['发布状态'], tabHost: 'views/admin/system/config-management.vue', api: ['getPublishStatus'], backend: [['fastrag-modules/fastrag-platform/src/main/java/com/fastrag/module/platform/controller/ConfigManageController.java', 'publish_switch']] })
A('系统设置管理', '配置管理-查看审核状态', { files: ['views/admin/system/config-management.vue'], tab: ['审核状态'], tabHost: 'views/admin/system/config-management.vue', api: ['getReviewStatus'], backend: [['fastrag-modules/fastrag-platform/src/main/java/com/fastrag/module/platform/controller/ConfigManageController.java', 'review_switch']] })

/* ============ 十五、知识审核管理 ============ */
const KRM = 'views/knowledge-review/review-management.vue'
A('知识审核管理', '审核流程设计-添加监听器', { files: ['views/knowledge-review/flow-design.vue'], tab: ['监听器'], tabHost: 'views/knowledge-review/flow-design.vue', api: ['getListeners'], backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/PublishManageController.java', '/listeners']] })
A('知识审核管理', '审核流程设计-优化流程节点配置', { files: ['views/knowledge-review/flow-design.vue'], tab: ['效率分析', 'handleOptimizeNodeTimeout'], tabHost: 'views/knowledge-review/flow-design.vue', api: ['getNodeOptimizationSuggestions'], backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/PublishManageController.java', '/optimizations']] })
A('知识审核管理', '监听管理-配置监听URL', { files: ['views/knowledge-review/listeners.vue'], tab: ['新增监听器', '回调URL'], tabHost: 'views/knowledge-review/listeners.vue', api: ['createListener', 'updateListener'], backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/PublishManageController.java', '/listeners']] })
A('知识审核管理', '监听管理-查看监听URL执行情况', { files: ['views/knowledge-review/listeners.vue'], tab: ['handleShowLogs'], tabHost: 'views/knowledge-review/listeners.vue', api: ['getListenerLogs', 'getListenerTrends'], backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/PublishManageController.java', '/listeners/{id}/logs']] })
A('知识审核管理', '知识审核流程-审核备注', { files: ['views/knowledge-review/flows.vue'], tab: ['handleSubmitReview', 'reviewComment'], tabHost: 'views/knowledge-review/flows.vue', api: ['approveReview', 'rejectReview'], backend: [['fastrag-modules/fastrag-publish/src/main/java/com/fastrag/module/publish/controller/PublishController.java', '/approve']] })
A('知识审核管理', '知识审核流程-设置审核策略', { files: ['views/knowledge-review/flows.vue'], tab: ['审核策略管理', 'createReviewStrategy'], tabHost: 'views/knowledge-review/flows.vue', api: ['getReviewStrategies', 'createReviewStrategy'], backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/PublishManageController.java', '/review-strategies']] })
A('知识审核管理', '知识审核流程-查看审核策略执行情况', { files: ['views/knowledge-review/flows.vue'], tab: ['getReviewHistory'], tabHost: 'views/knowledge-review/flows.vue', api: ['getReviewHistory'], backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/PublishManageController.java', '/history']] })
A('知识审核管理', '知识审核流程-设置审核流程通知', { files: ['views/knowledge-review/flows.vue'], tab: ['审核通知配置', 'handleSaveNotifyConfig'], tabHost: 'views/knowledge-review/flows.vue', api: ['getNotificationConfig', 'saveNotificationConfig'], backend: [['fastrag-modules/fastrag-platform/src/main/java/com/fastrag/module/platform/controller/ConfigManageController.java', 'notification_config']] })
A('知识审核管理', '知识审核流程-测试通知配置', { files: ['views/knowledge-review/flows.vue'], tab: ['handleTestNotify'], tabHost: 'views/knowledge-review/flows.vue', api: ['createNotification'], backend: [['fastrag-modules/fastrag-platform/src/main/java/com/fastrag/module/platform/controller/NotificationController.java', '@PostMapping']] })
A('知识审核管理', '知识发布管理-查看发布策略执行效果', { files: ['views/knowledge-review/review-management.vue'], tab: ['发布策略效果', 'loadStrategyEffect'], tabHost: 'views/knowledge-review/review-management.vue', api: ['getPublishStrategyEffect'], backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/PublishManageController.java', '/strategy-effect']] })
A('知识审核管理', '知识审核流程-查看知识质量趋势', { files: ['views/knowledge-review/quality.vue'], tab: ['质量趋势', 'handleRunScore'], tabHost: 'views/knowledge-review/quality.vue', api: ['getQualityTrend'], backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/PublishManageController.java', '/quality-trend']] })

/* ============ 十六、机器人配置管理 ============ */
const ED = 'views/application/editor/'
const ACC = 'fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/controller/AppConfigController.java'
A('机器人配置管理', '基础信息配置-重置配置', { files: [ED + 'components/BasicConfig.vue'], tab: ['重置配置', 'handleResetBasic'], tabHost: ED + 'components/BasicConfig.vue', api: ['resetAppBasicConfig'], backend: [[ACC, '/basic/reset']] })
A('机器人配置管理', '对话配置-配置兜底话术', { files: [ED + 'components/DialogConfig.vue'], tab: ['兜底话术', 'saveFallback'], tabHost: ED + 'components/DialogConfig.vue', api: ['saveAppFallback'], backend: [[ACC, '/global-policy/fallback']] })
A('机器人配置管理', '对话配置-配置答复后建议', { files: [ED + 'components/DialogConfig.vue'], tab: ['答复后建议'], tabHost: ED + 'components/DialogConfig.vue', api: ['saveAppDialogConfig'], backend: [[ACC, '/dialog/background']] })
A('机器人配置管理', '对话配置-重置对话配置', { files: [ED + 'components/DialogConfig.vue'], tab: ['handleResetDialog'], tabHost: ED + 'components/DialogConfig.vue', api: ['resetAppDialogConfig'], backend: [[ACC, '/dialog/reset']] })
A('机器人配置管理', '全局策略-配置安全策略', { files: [ED + 'components/GlobalConfig.vue'], tab: ['安全策略'], tabHost: ED + 'components/GlobalConfig.vue', api: ['saveAppGlobalPolicy'], backend: [[ACC, '/global-policy']] })
A('机器人配置管理', '插件配置-配置插件参数', { files: ['views/plugin-db/plugins.vue'], tab: ['apiConfigForm'], tabHost: 'views/plugin-db/plugins.vue', api: ['getToolApiConfig', 'saveToolApiConfig'], backend: [['fastrag-modules/fastrag-tools/src/main/java/com/fastrag/module/tools/controller/ToolController.java', '/api-config']] })
A('机器人配置管理', '插件配置-查看插件详情', { files: ['views/plugin-db/plugins.vue'], tab: ['插件详情', 'handleViewDetail'], tabHost: 'views/plugin-db/plugins.vue', api: ['getToolApiConfig'], backend: [['fastrag-modules/fastrag-tools/src/main/java/com/fastrag/module/tools/controller/ToolController.java', '/api/tools']] })
A('机器人配置管理', '对话测试-测试案例库', { files: [ED + 'components/DialogTest.vue'], tab: ['DialogTest'], tabHost: 'views/application/editor/index.vue', api: ['getAppDialogTests', 'createAppDialogTest', 'runAppDialogTest'], backend: [[ACC, '/dialog-tests']] })
A('机器人配置管理', '对话调试-设置调试级别', { files: ['views/application/app-config.vue'], tab: ['调试级别'], tabHost: 'views/application/app-config.vue', api: ['saveAppDebugConfig'], backend: [[ACC, '/debug']] })
A('机器人配置管理', '对话调试-导出调试日志', { files: ['views/application/app-config.vue'], tab: ['handleExportDebugLogs'], tabHost: 'views/application/app-config.vue', api: ['exportAppDebugLogs'], backend: [[ACC, '/debug/export']] })
A('机器人配置管理', '对话调试-清理调试日志', { files: ['views/application/app-config.vue'], tab: ['handleClearDebugLogs'], tabHost: 'views/application/app-config.vue', api: ['clearAppDebugLogs'], backend: [[ACC, '/debug/logs']] })
A('机器人配置管理', '对话优化-优化对话流程', { files: [ED + 'components/DialogOptimize.vue'], tab: ['DialogOptimize'], tabHost: 'views/application/editor/index.vue', api: ['getAppOptimizations'], backend: [[ACC, '/optimizations']] })

/* ============ 十七、业务流管理 ============ */
const WM = 'views/application/workflow-manage.vue'
A('业务流管理', '对话测试-录制测试案例', { files: [WM], tab: ['录制测试案例', 'handleRecordWfTest'], tabHost: WM, api: ['executeWorkflow', 'createWorkflowTestCase'], backend: [['fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/controller/WorkflowController.java', '/test-cases']] })
A('业务流管理', '对话测试-查看测试案例', { files: [WM], tab: ['handleViewWfTest'], tabHost: WM, api: ['getWorkflowTestCases'], backend: [['fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/controller/WorkflowController.java', '@GetMapping("/{id}/test-cases")']] })
A('业务流管理', '对话测试-删除测试案例', { files: [WM], tab: ['handleDeleteWfTest'], tabHost: WM, api: ['deleteWorkflowTestCase'], backend: [['fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/controller/WorkflowController.java', '@DeleteMapping("/{id}/test-cases/{tcId}")']] })
A('业务流管理', '对话调试-查看调试信息', { files: [WM], tab: ['对话调试', 'loadWfDebug'], tabHost: WM, api: ['getWorkflowDebugInfo'], backend: [['fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/controller/WorkflowController.java', '@GetMapping("/{id}/debug")']] })
A('业务流管理', '对话调试-设置调试级别', { files: [WM], tab: ['handleSaveWfLevel'], tabHost: WM, api: ['saveWorkflowDebugConfig'], backend: [['fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/controller/WorkflowController.java', '@PostMapping("/{id}/debug")']] })
A('业务流管理', '对话调试-导出调试日志', { files: [WM], tab: ['handleExportWfDebug'], tabHost: WM, api: ['exportWorkflowDebugLogs'], backend: [['fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/controller/WorkflowController.java', '/debug/export']] })
A('业务流管理', '对话调试-清理调试日志', { files: [WM], tab: ['handleClearWfDebug'], tabHost: WM, api: ['clearWorkflowDebugLogs'], backend: [['fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/controller/WorkflowController.java', '@DeleteMapping("/{id}/debug")']] })
A('业务流管理', '知识更新-手动更新知识库', { files: [WM], tab: ['handleWfManualUpdate'], tabHost: WM, api: ['triggerAppKnowledgeUpdate'], backend: [[ACC, '/knowledge-update']] })
A('业务流管理', '知识更新-设置自动更新', { files: [WM], tab: ['handleWfSaveAutoConfig'], tabHost: WM, api: ['setAutoKnowledgeUpdate'], backend: [[ACC, '@PutMapping("/{appId}/knowledge-update")']] })
A('业务流管理', '知识更新-查看更新日志', { files: [WM], tab: ['loadWfUpdateLogs'], tabHost: WM, api: ['getKnowledgeUpdateLogs'], backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/PublishManageController.java', '/knowledge-update-logs']] })
A('业务流管理', '知识更新-比较更新内容', { files: [WM], tab: ['handleWfCompare'], tabHost: WM, api: ['compareKnowledgeContent'], backend: [['fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/PublishManageController.java', '/knowledge-compare']] })
A('业务流管理', '对话优化-分析对话数据', { files: [WM], tab: ['handleAnalyzeWf'], tabHost: WM, api: ['analyzeWorkflowOptimization'], backend: [['fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/controller/WorkflowController.java', '/optimizations/analyze']] })
A('业务流管理', '对话优化-优化对话流程', { files: [WM], tab: ['handleCreateWfOpt'], tabHost: WM, api: ['createWorkflowOptimization'], backend: [['fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/controller/WorkflowController.java', '@PostMapping("/{id}/optimizations")']] })
A('业务流管理', '对话优化-查看优化建议', { files: [WM], tab: ['wfOptList'], tabHost: WM, api: ['getWorkflowOptimizations'], backend: [['fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/controller/WorkflowController.java', '@GetMapping("/{id}/optimizations")']] })
A('业务流管理', '对话优化-应用优化建议', { files: [WM], tab: ['handleApplyWfOpt'], tabHost: WM, api: ['applyWorkflowOptimization'], backend: [['fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/controller/WorkflowController.java', '/apply']] })
A('业务流管理', '对话优化-测试优化效果', { files: [WM], tab: ['handleTestWfOpt'], tabHost: WM, api: ['testWorkflowOptimization'], backend: [['fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/controller/WorkflowController.java', '/test']] })
A('业务流管理', '对话优化-导出优化报告', { files: [WM], tab: ['handleExportWfOpts'], tabHost: WM, api: ['exportWorkflowOptimizations'], backend: [['fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/controller/WorkflowController.java', '/optimizations/export']] })
A('业务流管理', '配置迁移-查看迁移日志', { files: [WM], tab: ['迁移日志', 'loadWfMigs'], tabHost: WM, api: ['getWorkflowMigrations'], backend: [['fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/controller/WorkflowController.java', '@GetMapping("/migrations")']] })
A('业务流管理', '配置迁移-设置迁移策略', { files: [WM], tab: ['handleCreateWfMig'], tabHost: WM, api: ['createWorkflowMigration'], backend: [['fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/controller/WorkflowController.java', '@PostMapping("/migrations")']] })
A('业务流管理', '配置迁移-查看迁移进度', { files: [WM], tab: ['el-progress'], tabHost: WM, api: ['getWorkflowMigrations'] })
A('业务流管理', '节点管理-剪切节点', { files: [WM], tab: ['剪切节点', 'handleCutNode'], tabHost: WM, api: ['addWorkflowNode'], backend: [['fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/controller/WorkflowController.java', '@PostMapping("/{id}/nodes")']] })
A('业务流管理', '节点管理-清理节点日志', { files: [WM], tab: ['handleClearNodeLogs'], tabHost: WM, api: ['clearWorkflowNodeLogs'], backend: [['fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/controller/WorkflowController.java', '/logs"']] })
A('业务流管理', '节点管理-节点延时(设/查/改/删)', { files: [WM], tab: ['节点延时', 'saveDim', 'clearDim'], tabHost: WM, api: ['getWorkflowNodeConfig', 'saveWorkflowNodeConfig', 'deleteWorkflowNodeConfig'], backend: [['fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/controller/WorkflowController.java', '/{dimension}']] })
A('业务流管理', '节点管理-节点资源(设/查/改/删)', { files: [WM], tab: ['节点资源'], tabHost: WM, api: ['getWorkflowNodeConfig', 'saveWorkflowNodeConfig'], backend: [['fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/controller/WorkflowController.java', '/{dimension}']] })
A('业务流管理', '节点管理-节点权限(设/查/改/删)', { files: [WM], tab: ['节点权限'], tabHost: WM, api: ['getWorkflowNodeConfig', 'saveWorkflowNodeConfig'], backend: [['fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/controller/WorkflowController.java', '/{dimension}']] })
A('业务流管理', '节点管理-节点日志级别(设/查/改/删)', { files: [WM], tab: ['日志级别'], tabHost: WM, api: ['getWorkflowNodeConfig', 'saveWorkflowNodeConfig'], backend: [['fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/controller/WorkflowController.java', '/{dimension}']] })
A('业务流管理', '节点管理-数据保留策略(设/查/改/删)', { files: [WM], tab: ['数据保留'], tabHost: WM, api: ['getWorkflowNodeConfig', 'saveWorkflowNodeConfig'], backend: [['fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/controller/WorkflowController.java', '/{dimension}']] })
A('业务流管理', '节点管理-数据备份(设/查/恢复/删)', { files: [WM], tab: ['数据备份', 'handleBackupNow', 'handleRestoreBackup', 'handleDeleteBackup'], tabHost: WM, api: ['updateWorkflowNode'], backend: [['fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/controller/WorkflowController.java', '@PutMapping("/{id}/nodes/{nodeKey}")']] })

console.log(`\n===== 静态入口完整性审计：共 ${F.length} 个功能 =====\n`)
F.forEach(check)
console.log(`\n===== 结果：PASS ${passCount} / FAIL ${failCount}（共 ${F.length}）=====`)
if (failures.length) { console.log('失败功能：'); failures.forEach((f) => console.log('  ✗ ' + f)) }
process.exit(failCount > 0 ? 1 : 0)
