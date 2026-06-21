# 知识库系统增强实现计划

> 依据 `docs/清单.xlsx`（544 条需求项，其中 214 条满足、330 条未满足）与现有代码库的差距分析制定。
> 实施顺序：第一梯队（快赢）→ 第二梯队（高价值大块）→ 第三梯队（补全）。

---

## 背景与差距总览

清单定义了一个**企业级智能知识库平台**，现有系统覆盖率约 **39%**。三大结构性空白：

| 空白块 | 缺口 | 性质 |
|---|---|---|
| 知识审核发布 | 93 条 | 内容治理（审核/发布/版本/合规/质量评估） |
| 业务流编排 | 106 条 | 智能体平台（画布/节点/意图分支） |
| 检索增强 | 41 条 | 智能搜索（联想/纠错/重写） |

现有系统**过度倾斜于文件管理**（FileManager/FileTable/FileUploader），而清单权重在**知识治理与知识应用**。

---

## 文件结构规划

```
src/
├── types/
│   ├── knowledge.ts        # 扩展：回收站、QA对、检索联想类型
│   └── audit.ts            # 新建：审核/发布/版本类型
├── mock/
│   ├── files.ts            # 扩展：软删除/回收站/采编记录
│   ├── chunks.ts           # 扩展：QA抽取、关键词高亮
│   ├── terminology.ts      # 新建：术语同义词统一数据层
│   ├── qa-pairs.ts         # 新建：问答对数据层
│   └── audit.ts            # 新建：审核发布流程数据层
├── composables/
│   ├── useRecycleBin.ts    # 新建：回收站逻辑
│   ├── useSynonyms.ts      # 新建：同义词联想注入检索
│   └── useQaExtraction.ts  # 新建：QA抽取
├── views/knowledge/detail/components/
│   ├── SearchTestPanel.vue         # 改：高亮+溯源+同义词
│   ├── FileManager.vue             # 改：软删除+回收站入口
│   └── RecycleBinDialog.vue        # 新建：回收站弹窗
└── views/admin/system/
    └── terminology.vue             # 改：接入统一数据层
```

---

# 第一梯队：快赢功能（高价值、复用现有、低成本）

## ✅ Task 1：回答溯源高亮

**目标**：检索结果展示命中文本的高亮 + 可点击溯源到原文件切片。

**Files:**
- Modify: `src/types/evaluation.ts:59-69`（SearchResultItem 加高亮字段）
- Modify: `src/mock/chunks.ts`（检索时计算高亮片段）
- Modify: `src/views/knowledge/detail/components/SearchTestPanel.vue:200-218`（结果区渲染高亮+溯源）

- [ ] **Step 1：扩展 SearchResultItem 类型**
  增加 `highlights: string[]`（命中的关键短语列表）和 `previewSnippet: string`（带标记的预览片段）。

- [ ] **Step 2：mock 层实现高亮计算**
  在 `searchChunks` 中，对 query 分词后，在 chunk.content 中定位命中位置，生成 `previewSnippet`（用 `<mark>` 包裹命中词）和 `highlights`。

- [ ] **Step 3：SearchTestPanel 渲染高亮**
  结果卡片的 content 区改为 `v-html` 渲染 `previewSnippet`，命中词黄色背景。

- [ ] **Step 4：溯源跳转**
  来源区 `文件ID/块索引` 改为可点击 `el-link`，点击后 `router.push` 到 `/knowledge/:id/chunks/:fileId` 并定位到对应 chunk。

---

## ✅ Task 2：知识回收站（软删除）

**目标**：文件/分片删除改为软删除，提供回收站列表 + 恢复 + 彻底删除。

**Files:**
- Modify: `src/types/knowledge.ts:122-149`（KnowledgeFile 加 `deletedAt?: string`）
- Modify: `src/mock/files.ts:249-253`（deleteFile 改软删，新增 restore/permanentDelete/getDeletedFiles）
- Modify: `src/composables/useFiles.ts`（暴露回收站方法）
- Create: `src/views/knowledge/detail/components/RecycleBinDialog.vue`
- Modify: `src/views/knowledge/detail/components/FileManager.vue`（加回收站入口按钮）

- [ ] **Step 1：扩展 KnowledgeFile 类型**
  增加 `deletedAt?: string`（软删除时间戳，undefined 表示未删除）。

- [ ] **Step 2：files mock 改造**
  - `deleteFile` → 设 `deletedAt`，不 splice
  - `getFiles` → 过滤 `!deletedAt`
  - 新增 `getDeletedFiles(kbId)` / `restoreFile(kbId, fileId)` / `permanentlyDeleteFile(kbId, fileId)` / `emptyRecycleBin(kbId)`

- [ ] **Step 3：useFiles composable 暴露回收站**
  增加 `recycleBin` ref + `loadDeletedFiles` / `restoreFile` / `permanentDelete` / `emptyBin` 方法。

- [ ] **Step 4：RecycleBinDialog 组件**
  el-table 列表（文件名/删除时间/原位置），恢复/彻底删除/清空操作，权限守卫 `kb:upload`（恢复需写权限）。

- [ ] **Step 5：FileManager 加入口**
  文件列表上方加"回收站"按钮，打开 RecycleBinDialog。

---

## ✅ Task 3：搜索结果联想（同义词/术语归一）

**目标**：术语库的同义词在检索时自动扩展查询词，提升召回率。

**Files:**
- Create: `src/mock/terminology.ts`（统一数据层，取代 terminology.vue 内联数据）
- Modify: `src/views/admin/system/terminology.vue`（接入 mock）
- Create: `src/composables/useSynonyms.ts`
- Modify: `src/mock/chunks.ts`（searchChunks 注入同义词扩展）
- Modify: `src/views/knowledge/detail/components/SearchTestPanel.vue`（展示扩展词）

- [ ] **Step 1：建 terminology.ts 数据层**
  从 terminology.vue 提取 termLibraries / termList，导出 `getTerms` / `getSynonymMap`（返回 `Map<string, string[]>`，如 "发票"→["报销单","费用凭证"]）。

- [ ] **Step 2：terminology.vue 接入**
  移除内联 ref，改用 mock 层 CRUD。

- [ ] **Step 3：useSynonyms composable**
  `expandQuery(query: string): { original: string; expanded: string; addedTerms: string[] }`，把 query 中的词替换/追加同义词。

- [ ] **Step 4：searchChunks 注入**
  检索前调用 `expandQuery`，用扩展后的 query 匹配，返回结果带 `usedSynonyms` 标记。

- [ ] **Step 5：SearchTestPanel 展示**
  检索后显示"已联想词：发票 → 报销单、费用凭证"提示条。

---

## ✅ Task 4：问答抽取（QA 对生成）

**目标**：从文档智能抽取 Q-A 对，形成结构化知识点，供问答检索专用。

**Files:**
- Modify: `src/types/knowledge.ts`（新增 QaPair 类型）
- Create: `src/mock/qa-pairs.ts`
- Create: `src/composables/useQaExtraction.ts`
- Create: `src/views/knowledge/detail/components/QaExtractionPanel.vue`
- Modify: `src/views/knowledge/detail/index.vue`（加 Tab 入口）

- [ ] **Step 1：定义 QaPair 类型**
  `{ id, kbId, fileId, question, answer, source: 'manual'|'ai', status: 'draft'|'confirmed', createdAt }`

- [ ] **Step 2：qa-pairs.ts 数据层**
  `getQaPairs(kbId)` / `addQaPair` / `updateQaPair` / `deleteQaPair` / `extractFromFiles(kbId, fileIds)`（mock：从 chunks 中按"？/什么是/如何"句式抽取候选 QA）。

- [ ] **Step 3：useQaExtraction composable**
  封装抽取流程：选文件 → 调 extractFromFiles → 返回候选列表 → 用户确认入库。

- [ ] **Step 4：QaExtractionPanel 组件**
  - 左：文件选择 + "智能抽取"按钮
  - 中：候选 QA 卡片列表（可编辑 question/answer，确认/丢弃）
  - 右：已入库 QA 管理（搜索/编辑/删除/导出）

- [ ] **Step 5：detail/index.vue 加 Tab**
  新增"问答知识"Tab，挂载 QaExtractionPanel。

---

## ✅ Task 5：采编记录库（操作审计）

**目标**：记录谁在何时对文件/分片做了什么操作，满足审计合规。

**Files:**
- Create: `src/types/audit.ts`（EditRecord 类型）
- Modify: `src/mock/files.ts`（关键操作写入记录）
- Create: `src/views/knowledge/detail/components/EditHistoryDialog.vue`
- Modify: `src/views/knowledge/detail/components/FileManager.vue`（加历史入口）

- [ ] **Step 1：定义 EditRecord 类型**
  `{ id, kbId, fileId, operator, action: 'create'|'update'|'delete'|'restore'|'rename'|'move', detail, timestamp }`

- [ ] **Step 2：files mock 埋点**
  在 createFile/updateFile/deleteFile/restoreFile/renameFile/moveFile 中 push EditRecord。

- [ ] **Step 3：getEditHistory API**
  `getEditHistory(kbId, fileId?)` 按文件或全库查询。

- [ ] **Step 4：EditHistoryDialog 组件**
  时间线展示操作记录（操作人/动作/详情/时间），支持按文件过滤。

- [ ] **Step 5：FileManager 加入口**
  文件右键菜单/操作列加"操作历史"，打开 EditHistoryDialog。

---

# 第二梯队：高价值大块（需规划，工作量大）

## 🔶 Task 6：多轮问答对话

**目标**：debug 页从单轮检索升级为多轮对话，支持上下文记忆。

**Files:**
- Create: `src/mock/conversation.ts`（对话状态 mock）
- Create: `src/composables/useConversation.ts`
- Modify: `src/views/knowledge/detail/debug.vue`（改对话式 UI）

- [ ] **Step 1：对话状态模型** — 定义 ConversationMessage（role/content/sources/contextRef）+ ConversationSession。
- [ ] **Step 2：useConversation composable** — 管理消息列表、上下文窗口（保留近 N 轮）、多轮检索（当前问题 + 历史摘要拼接查询）。
- [ ] **Step 3：debug.vue 改造** — 从单输入单结果改为聊天流式界面，回答带溯源。
- [ ] **Step 4：上下文消解** — "它的价格是多少"中的"它"用上一轮实体替换。

---

## 🔶 Task 7：知识审核发布流程

**目标**：实现知识发布生命周期（草稿→待审→通过→发布）+ 版本管理 + 审核工单。

**Files:**
- Create: `src/types/audit.ts`（PublishStatus / ReviewTask / KnowledgeVersion 类型）
- Create: `src/mock/audit.ts`（发布状态机 + 审核工单 + 版本快照）
- Create: `src/views/knowledge/detail/components/PublishPanel.vue`
- Create: `src/views/admin/audit/review-center.vue`（审核中心）

- [ ] **Step 1：发布状态机** — 定义 `draft|pending_review|approved|published|rejected` + 状态流转规则 + 权限矩阵。
- [ ] **Step 2：版本快照** — 每次发布保存 KnowledgeVersion（内容快照 + 版本号），支持回滚。
- [ ] **Step 3：审核工单** — ReviewTask（申请人/审核人/状态/备注/历史），支持通过/驳回/超时。
- [ ] **Step 4：PublishPanel** — 知识库详情内的发布管理（提交审核/查看版本/回滚）。
- [ ] **Step 5：审核中心** — 管理后台的待审列表（批量审核/审核备注/审核报告）。

> ⚠️ 这是最大块（清单 93 条），建议拆为子阶段：先发布状态+版本（基础），再审核工单，最后合规/质量评估。

---

## 🔶 Task 8：业务流画布编排

**目标**：可视化拖拽编排 Agent 工作流（开始/大模型/知识库检索/意图识别/选择器/结束节点）。

**Files:**
- Create: `src/types/workflow.ts`（节点/边/流程定义类型）
- Create: `src/mock/workflow.ts`
- Create: `src/views/workflow/` 目录
- 使用 `@vue-flow/core` 或自研 SVG 画布

- [ ] **Step 1：流程数据模型** — WorkflowNode（type/params/position）+ WorkflowEdge（source/target/condition）+ WorkflowDefinition。
- [ ] **Step 2：画布框架** — 拖拽节点、连线、配置抽屉、保存/版本。
- [ ] **Step 3：节点类型实现** — 开始/结束/大模型/知识库检索/意图识别/选择器 各自的配置面板。
- [ ] **Step 4：流程列表页** — CRUD + 发布 + 监控执行记录。
- [ ] **Step 5：对话测试** — 在画布内调试运行流程。

> ⚠️ 工作量最大（清单 106 条），建议独立立项，可能需要引入图编排库。

---

# 第三梯队：补全功能（价值中等）

## 🔵 Task 9：自动纠错 + 拼音搜索

- [ ] 建 `src/mock/search-correction.ts`（常见错别字表 + 拼音映射）
- [ ] 检索前纠错（"发票"←"发飘"），展示"您是不是要找：发票"
- [ ] 拼音首字母匹配（输入 "fp" 匹配 "发票"）

## 🔵 Task 10：查询重写/扩写规则库

- [ ] 建 `src/mock/query-rules.ts`（重写规则 = 正则/模板，扩写规则 = 上下文补充）
- [ ] 管理 UI（规则 CRUD + 测试）+ 检索注入

## 🔵 Task 11：对话测试案例库

- [ ] 建 `src/mock/test-cases.ts`（测试集 = 预期问题+预期命中）
- [ ] 回归测试运行器（批量跑案例，对比结果差异）

## 🔵 Task 12：知识更新（diff+日志）

- [ ] 依赖 Task 7 的版本快照，实现"比较更新内容"
- [ ] 更新日志 + 自动更新计划（定时同步）

## 🔵 Task 13：模型生命周期管理

- [ ] 扩展 `src/mock/models.ts`：训练记录/测试报告/发布版本/调用日志
- [ ] model-management.vue 增加生命周期 Tab

---

# 实施策略

1. **逐个实现，每个 Task 完成后 `npm run build` 验证 + git commit**
2. **第一梯队优先**（Task 1-5），预计 1-2 周，建立产品可信度基础
3. **第二梯队按立项优先级**（Task 7 审核发布 或 Task 8 业务流先做）
4. **第三梯队穿插进行**，作为体验优化
5. **数据层先行**：每个 Task 先建/改 mock，再改 UI，保证数据一致
6. **权限同步**：新功能对应的权限项加入 `src/types/auth.ts` 的 PERMISSION_TREE

## 建议的落地批次

| 批次 | 内容 | 预估 | 产出 |
|---|---|---|---|
| 批次 1 | Task 1 + Task 2 | 2-3 天 | 溯源 + 回收站，立即提升可信度 |
| 批次 2 | Task 3 + Task 5 | 2-3 天 | 同义词 + 审计，召回+合规 |
| 批次 3 | Task 4 | 3-4 天 | QA 抽取，知识质量 |
| 批次 4 | Task 6 | 4-5 天 | 多轮对话，体验升级 |
| 批次 5 | Task 7 | 1-2 周 | 审核发布，企业级治理 |
| 批次 6 | Task 8 | 2-3 周 | 业务流，智能体平台 |
| 批次 7 | Task 9-13 | 穿插 | 补全优化 |

---

## 进度跟踪

- [x] 需求解析与差距分析
- [ ] Task 1：回答溯源高亮
- [ ] Task 2：知识回收站
- [ ] Task 3：搜索结果联想
- [ ] Task 4：问答抽取
- [ ] Task 5：采编记录库
- [ ] Task 6：多轮问答对话
- [ ] Task 7：知识审核发布
- [ ] Task 8：业务流画布编排
- [ ] Task 9-13：补全功能
