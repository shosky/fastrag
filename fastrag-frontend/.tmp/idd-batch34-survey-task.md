# 任务书：第 3+4 批特性链证据调研（2026-09-21）

## 背景

`fastrag-frontend`（Vue3 + TS，路径均相对本目录）IDD 特性链批次 3/4。每特性按以下框架返回结构化 markdown（全部 file:line 证据；查不到标 GAP；证据分层 CERTAIN/INFERRED/GAP）：

- `## 页面与路由`（路由/入口/页内结构：区域/表格列/表单字段/对话框/按钮）
- `## 任务清单`（TASK 候选：动作 → 成功结果 → 失败恢复）
- `## 状态与反馈`（各查询/提交的 loading/empty/error 实况；无则标「无专门处理」）
- `## API 消费`（api/index.ts 函数名 + URL + 响应字段消费点）
- `## 交互要点`（非显然交互逻辑，伪代码级）
- `## 异常与疑点`（stub/mock/死代码/不一致）

**硬性边界：只读 `src/**` 与 `docs/feature-map.md` 本特性小节；不读仓库根 docs/。**

---

## A. onlyoffice-editing（OnlyOffice 在线编辑）

文件：`src/views/knowledge/detail/office-edit.vue`（363）、`src/views/knowledge/detail/components/OnlyOfficeViewer.vue`（327）、`src/composables/useOnlyOffice.ts`（161）、`src/config/index.ts`（38）、`src/types/onlyoffice.ts`（147）、`public/onlyoffice-plugin/chunk-boundary*/`（插件源码结构即可）、`scripts/oo-plugin-{probe,introspect}.mjs`。

专项：1) 编辑页路由/入口/返回路径；2) DocsAPI 文档服务器配置（document.url/JWT/token/回调）如何拼装；3) 保存回调（forcesave/callback）前端如何感知保存结果与触发重分片（若前端有感知）；4) 插件 chunk-boundary 的作用（选区桥：与 AI 分片如何衔接）、probe 脚本用途；5) 加载失败/超时处理；6) api 消费（onlyoffice config/forcesave/raw 端点）。

## B. ai-chunking（AI 分片）

文件：`src/views/knowledge/detail/components/AiChunkPanel.vue`（1965）、`AiChunkPdfOverlay.vue`（878）、`src/api/index.ts:249-263`（SSE 直连）。

专项：1) 面板工作流阶段（预览→OCR→结构合并→LLM 自动/手动分片→应用落库）的 UI 步骤与状态机；2) SSE 流（stream/layout）的事件类型、前端如何消费增量、断线处理；3) 区域框选（PdfOverlay）：跨页拖拽/自动翻页/扫描件 OCR/图片直达分片的交互细节；4) 段落对齐联动（hover/点击双向高亮）实现；5) 应用落库的确认流（覆盖语义）；6) api 消费全清单（ai-chunk 端点组）。

## C. retrieval（检索与查询增强）

文件：`src/views/knowledge/detail/components/{SearchTestPanel.vue 877, RetrievalSidebar.vue 272, RetrievalSettingPanel.vue 278}`、`src/views/operation/retrieval-analysis.vue`（349）、`src/services/query-preprocess.ts`（33，委托 mock）、`src/composables/useSynonyms.ts`（54）。

专项：1) 检索测试面板：输入/参数（topK/阈值/混合权重?）/结果展示（命中分片卡片?分数?）/状态处理；2) RetrievalSidebar 与 RetrievalSettingPanel 的职责与挂载点；3) query-preprocess 本地纠错/改写与 mock 委托实况（消费哪些 mock 函数）；4) useSynonyms 联想交互；5) 检索分析页（operation）图表与数据源；6) api 消费（search/suggest/expand-synonyms/query-rules/logs）。

## D. knowledge-graph（知识图谱）

文件：`src/views/knowledge/detail/components/{KnowledgeGraphPanel.vue 727, GraphSettingsPopup.vue 213, NodeDetailPanel.vue 225}`、`src/composables/{useKnowledgeGraph.ts 124, useForceGraph.ts 432（G6）, useGraphExpansion.ts 59}`。

专项：1) 图谱面板：构建触发/进度、画布交互（缩放/拖拽/点选）、节点/边渲染配置；2) GraphSettingsPopup 设置项；3) NodeDetailPanel 展示内容与操作（合并入口?）；4) useForceGraph 的 G6 配置要点（布局/样式/事件）；5) 图谱扩展（expand）交互与缓存；6) api 消费（graph data/stats/search/labels/build/settings/merge 全套）。

## E. benchmark-evaluation（评测与基准）

文件：`src/views/knowledge/detail/components/{EvaluationBenchmarkPanel.vue 865, RagEvaluationPanel.vue 830}`、`src/composables/{useEvaluation.ts 149, useBenchmark.ts 76}`。

专项：1) 基准集管理（CRUD/LLM 生成问题）；2) 评测运行（异步状态轮询?）与结果展示（指标/明细）；3) 两面板与两 composable 的职责分工；4) api 消费（benchmarks/evaluation 全套）；5) 状态处理实况。
