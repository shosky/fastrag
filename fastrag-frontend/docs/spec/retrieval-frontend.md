# 检索与查询增强 — Frontend Spec（retrieval-frontend）

> 由 /idd-spec 于 2026-09-21 生成（onboard 特征化模式）。Parent Trace ← `docs/feature-map.md` 特性 9 证据包；术语 ← `CONTEXT.md`（向量检索/混合检索/查询增强）。

## 1. Scope

覆盖检索测试面板（含预处理管线/历史/溯源）、运行时参数侧栏、知识库表单内检索设置面板、检索日志分析页。**排除**：debug 调试页（死入口，URL 直达——INFERRED-4 处置）；图谱扩展算法（knowledge-graph 链——本链只消费其 expand composable）；Rerank/融合算法本身（后端）。

## 2. User Tasks

- **TASK-001 检索测试** — 详情页「检索测试」Tab：输入/Enter/图片（≤5 张、10MB/张、去重、objectURL 释放；有图自动切 image 模式——**mock 语义：effectiveQuery="[图片] 文件名"**，非真以图搜图）→ 预处理管线（纠错默认开/重写默认开/图谱扩展**默认关**/同义词联想默认开）→ `searchRetrieval` → 结果卡片（序号/图谱召回·QA tag/相似度 %/预览 v-html+高亮/`<details>` 全文/溯源链接）+ 历史写入（localStorage 按 kbId 隔离、上限 50）（SearchTestPanel:52-99,180-303 ‹CERTAIN›；INFERRED-5：图片检索为占位实现）。
- **TASK-002 预处理管线** — `autoCorrect`（本地错别字表 36 条+拼音首字母，mock 委托）/`rewriteQuery`（本地规则 rewrite+expand，mock 委托）/图谱扩展（useGraphExpansion，Map 缓存，无 catch 冒泡外层）/同义词（useSynonyms，catch 静默回退原 query；**matchedTerms 直接=addedTerms 同一数组**）（services/query-preprocess:8-33; SearchTestPanel:199-237 ‹CERTAIN›；**双轨并存：真实 API querySuggest/applyQueryRules 仅 debug 页消费**，INFERRED-6）。
- **TASK-003 配置变更自动重搜** — config 深度 watch → 已搜索过且有输入时自动 handleSearch（:256-264 ‹CERTAIN›）。
- **TASK-004 运行时调参并保存** — RetrievalSidebar（320px sticky）：mode/topK 1-100/相似度阈值 0-1/BM25 召回 1-200/hybrid 权重（互补展示 1-w）/图谱通道 switch/关键词匹配+QA 召回数 → 显式「保存」→ updateKnowledgeBase 持久化 retrievalConfig（Sidebar:49-216; detail/index.vue:143-155 ‹CERTAIN›）。
- **TASK-005 初始化检索设置** — RetrievalSettingPanel（表单 Tab 4，受控组件无保存钮，随表单提交）：六段 = 模式/基础参数（分数阈值开关+值、语义权重 slider）/预处理（纠错/重写/图谱扩展+深度+最大实体/同义词/多查询改写+变体数）/重排序（Rerank 开关+模型、LLM 重排、MMR+lambda）/多路召回（四通道召回数、RRF/加权/交错）/上下文组装（concat/parent_chunk/parent_document/window + 窗口 + 最大 Token + 排序）（RetrievalSettingPanel:59-247 ‹CERTAIN›）。
- **TASK-006 检索日志分析** — `/operation/retrieval-analysis`：5 指标卡（总查询/无结果数/无结果率/平均耗时/平均命中数）+ 热门/无结果 Top10 列表（**非图表库，纯列表**）+ 日志明细表（KB/结果状态筛选 + 分页 10-100）（retrieval-analysis:133-241 ‹CERTAIN›）。
- **TASK-007 溯源跳转** — 结果卡溯源 → `/knowledge/:kbId/chunks/:fileId`（携 chunkIndex/distance 展示）（:277-280 ‹CERTAIN›）。

## 3. Pages & Routes

- PAGE-001 检索测试 Tab（宿主 `/knowledge/:id`，:265-288）＝ SearchTestPanel + RetrievalSidebar；配置回填：KB 详情 retrievalConfig 非空字段合并默认值（detail/index.vue:73-112 ‹CERTAIN›）。
- PAGE-002 检索设置（宿主 `/knowledge/:id/edit` form Tab 4）。
- PAGE-003 检索日志分析 `/operation/retrieval-analysis`（Sidebar「知识库」组：108；权限 menu:operation:retrieval-analysis）。

## 4. Elements & Interaction

- 结果高亮 `highlightFull`：先 HTML 转义再按 token 长度降序包 `<mark>` 防嵌套（:282-295 ‹CERTAIN›）。
- 历史：seq 从存量恢复、写入失败静默；记录的 query 是纠错/重写后的中间 query（:246 ‹CERTAIN›）。
- 权重互补展示 `1 - semanticWeight`、MMR lambda 同理（SettingPanel:56,101,169）。
- Rerank 模型列表加载失败 → 回退硬编码 bge-reranker-v2-m3/base（:18-28 ‹CERTAIN›）。

## 5. State Matrix

| 数据流 | loading | empty | error | 证据 |
|---|---|---|---|---|
| searchRetrieval | 按钮 loading + 结果区文案（区分图片/文本） | el-empty「未找到相关文档块」（hasSearched 门控） | ElMessage「检索失败，请重试」+ 清空结果；无超时处理 | :180-253,417-467 |
| 图谱扩展 | isExpanding 提示条 | — | **无 catch**（冒泡外层 catch） | :333-336; useGraphExpansion:27-42 |
| 同义词 | — | — | catch 静默回退 | useSynonyms:30-32 |
| 保存配置 | 保存钮 | — | 「检索配置保存失败」 | detail/index:143-155 |
| 分析页 logs | 全页 v-loading | 面板 el-empty | **try/finally 无 catch（unhandled rejection）**；loadAnalysis/loadKbOptions catch 静默 | retrieval-analysis:69-107,134 |
| 历史面板 | — | 「暂无检索历史」 | 写入静默 | :395-413 |

## 6. Data Mapping

| 视图数据 | 消费代码（CERTAIN） | API Contract |
|---|---|---|
| 检索结果 `SearchResultItem[]` | POST `/retrieval/search`（api:531-533）请求 `{knowledgeId,query,config}`；字段 index/similarity/content/source(fileId,chunkIndex,distance)/highlights?/previewSnippet?（types/evaluation:103-124）；source==='graph'/'qa' tag | TBD — pending API Contract |
| 同义词 | POST `/query/expand-synonyms`（api:548-550）；兼容 `string[]` 旧结构与 `{addedTerms}` | TBD |
| 图谱扩展 | POST `/graph/expand` → `{entities,relations,expandedQuery}`（api:535-542） | TBD |
| 纠错/规则（debug 页） | POST `/query/suggest`（取 suggestions[0]）、POST `/query-rules/apply`（api:544-554） | TBD |
| 日志 | GET `/retrieval/logs`（params kbId/hasResult/page）+ GET `/retrieval/logs/analysis`（逐字段 ??0 兜底）（api:1453-1459） | TBD |
| ~~updateRetrievalLog~~ | **零消费死代码**（api:1461-1463，INFERRED-7） | — |
| 配置类型 | `RetrievalConfig = RetrievalSettingConfig`（Sidebar 与 SettingPanel 共享同一类型，types/knowledge:323） | — |

mock 委托实况：`@/mock/search-correction`（错别字表+拼音表→`{suggestedQuery,reason}`）、`@/mock/query-rules`（10 条种子规则 rewrite+expand）；`src/mock` 无 retrieval 端点处理器——**主链路直连真实后端，仅预处理两函数走本地 mock**。

## 7. Responsive & Accessibility

桌面优先（Sidebar sticky 320px）；无断点 [GAP]。

## 8. Acceptance

- [ ] 空输入拦截；图片 ≤5/10MB/去重/切 image 模式
- [ ] 预处理四开关按配置生效；纠错/重写提示条展示；图谱扩展默认关且有提示
- [ ] 结果卡片 tag/相似度/高亮/全文/溯源正确；高亮防嵌套
- [ ] 历史按 kbId 隔离上限 50；点击回填自动重搜；清空生效
- [ ] Sidebar 保存持久化 retrievalConfig；config 变更自动重搜（已搜索过）
- [ ] SettingPanel 六段受控；Rerank 列表失败回退硬编码
- [ ] 分析页指标/Top10/明细筛选分页正确

## INFERRED 裁决（✅ 已全量裁决 2026-09-21，用户授权代决）

> 逐条结论见 [../decisions/inferred-adjudication.md](../decisions/inferred-adjudication.md)。以下原清单保留作对照。

## Stage INFERRED 清单（原清单，已被裁决覆盖）

INFERRED-1 SearchTestPanel 死 emits（update:config/save 声明未发）清理？INFERRED-2 图谱扩展默认值不一致（SearchTestPanel false vs SettingPanel true）——统一？INFERRED-3 updateRetrievalLog 死代码清理？INFERRED-4 debug 页死入口（goToDebug 未接；唯一真实 querySuggest/rules 消费方）——接线 or 删？INFERRED-5 图片检索为 mock 占位（"[图片] 文件名"）——接真实现 or 明示占位？INFERRED-6 预处理双轨（本地 mock vs debug 页真实 API）——收敛到真实 API？INFERRED-7 分析页 loadLogs 无 catch 修复？INFERRED-8 同义词 matchedTerms=addedTerms 同数组展示是否按缺陷修？
