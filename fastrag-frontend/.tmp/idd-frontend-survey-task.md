# 任务书：fastrag-frontend 前端 IDD 调研（2026-09-21）

## 背景

`fastrag-frontend`（`D:\Workspace\java\github\rag\fastrag\fastrag-frontend`，Vue 3.5 + TS ~6.0 + Vite 6 + Element Plus 2.14 + pinia + vue-router 4，包名 `fastrag-demo-tmp`）要在**本目录下**建立独立的 IDD 文档树（`docs/`）。仓库根的 `docs/` **不在本次范围**，不得读取或引用。

已核实事实（可直接引用，不必重复推导）：
- `src/router/routes.ts`：`path:` 记录 70 = **64 条页面路由** + 1 根布局 + 4 redirect 壳（operation/admin/robot-operation/knowledge-review）+ 1 catch-all；守卫读 `MENU_PERMISSION_MAP`（`types/auth.ts`，692 行），未登记路由默认放行。
- `src/views` 133 个 .vue 共 **52,964 行**；`src/` 全部 .vue+.ts = 69,636 行。
- `src/api/index.ts` 单文件 ~2075 行，baseURL `/api`（dev 代理 → localhost:8081）；SSE 用 fetch 直连绕过 axios（:251-261 附近）。
- 孤儿（无路由挂载）：`views/admin/content/` 5 页、`admin/platform/third-party.vue`、`knowledge/form.vue`（1501 行）、`notification/index.vue`；`knowledge-review/*` 7 页与 `robot-operation/*` 4 页在路由但未接侧边栏。
- package.json scripts：dev / build（vue-tsc -b && vite build）/ preview；**无 lint、无 test**；playwright-core 在 devDependencies（仅用于探针脚本）。
- 自动导入：unplugin-auto-import + unplugin-vue-components（auto-imports.d.ts / components.d.ts）。
- 60 天热点目录：views/knowledge/detail 54（components 45）、composables 8、mock 7、data 7、api 6、router 4、types 11。
- `PLAN.md`（12 阶段「AIS Mock 演示系统」计划）为前 IDD 原始文档。

## 调研问题（全部只读）

### A. 路由→页面清单（FRONTEND.md 验收要求：每条路由有页面候选 + 入口/权限记录）
64 条页面路由逐条给出行：`path | view 文件 | 菜单挂载（menu-perm-tree.ts 是否登记）| 孤儿/redirect 归属`。过于琐碎的嵌套可按目录聚合，但**孤儿判定必须逐页**。

### B. 组件资产
1. `src/components/` 清单（逐文件 + 行数，用途一句话）。
2. `src/views/knowledge/detail/components/` 逐文件 + 行数（已知 AiChunkPanel 1965、QaPanel、GraphSettingsPopup、KnowledgeGraphPanel、FileTable、ApiDocPanel 等，补全）。
3. `src/views/application/editor/` 面板组件清单 + 行数（已知 ~15 面板）。
4. `src/views/operation/components/`（MetricCards/RankList/WordCloud）。

### C. 状态与逻辑层
1. `src/stores/`：user.ts、system.ts 的 state/actions 概要（是否仍仅 2 个）。
2. `src/composables/` 完整清单：文件名 + 行数 + 一句话用途（已知 usePagination/useOnlyOffice/useAppChatStream/useKnowledgeGraph/useForceGraph/useGraphExpansion/useParseStrategy/useFiles/useEvaluation/useBenchmark/useConversation/useAuth/useSynonyms，核实并补全）。
3. `src/directives/`（v-permission）与 `src/utils/`（request.ts 行数、解包与 401 行为）。

### D. API 消费面与类型
1. `src/api/index.ts` 按函数/URL 前缀聚类的消费组（kb/file/chunk/ai-chunk/onlyoffice/qa/strategy/app/agent/tool/skill/mcp/db/model/config/operation/auth…），每组端点数。
2. `src/types/` 逐文件 + 行数 + 内容一句话。
3. `src/services/`（query-preprocess.ts 等）与 mock 的关系。

### E. mock 与数据层
1. `src/mock/` 完整清单（30+ 文件），**谁在 import mock**（引用点列出）。
2. `src/data/` 是什么（7 次变更的热点）、与 mock 的区别。

### F. 样式与配置
1. 全局样式：`src/assets/` 结构、scss 变量/设计 token 是否存在（CERTAIN 仅当 token 在代码中）。
2. `vite.config.ts`：代理、别名、自动导入配置要点。
3. `config/`：index.ts（OO Server URL :8082 等）、menu-perm-tree.ts（451 行，结构：菜单→页面操作→API 联动组）。
4. `${block}__pagination` BEM 用法在视图中的实数（此前记 19 个视图，请复核）。

### G. QA 资产与杂项
1. 有无任何 test/e2e/storybook/axe 资产（预期无 → 标 GAP 并列 playwright-core 实际用途）。
2. `scripts/` 目录逐文件用途。
3. `index.html` title、`public/`（onlyoffice-plugin 结构）。
4. `.env*` 文件有无。

## 输出格式

结构化 markdown：`## A 路由` … `## G 杂项`，每条结论附证据（file:line 或命令输出摘要）；孤儿/无测试等「不存在」的结论必须给出你检查过的命令。最后 `## 结论`：前端特性聚类建议（哪些 view 目录聚成一个特性、有无本任务书未预料的新聚类）。证据分层 CERTAIN/INFERRED/GAP，不得臆造。
