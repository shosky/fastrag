# FastRAG 前端（fastrag-frontend）— 项目总览与文档索引

> 由 idd-onboard 于 2026-09-21 生成（前端单侧 IDD 接入）。本文档树仅覆盖 `fastrag-frontend`；仓库根另有全仓文档体系，不在本树范围。

## Parent Trace

[CONTEXT.md](../CONTEXT.md)（前端领域语言 + IDD 元数据）

## 1. 项目概览

FastRAG（产品展示名 AIS）知识库管理平台的前端单页应用：围绕知识库（文件上传/解析/分片/AI分片/图谱/评测/发布）与应用中心（可视化编排 + SSE 对话 + 智能体）两大主场景，提供 64 条页面路由的管理界面。数据全部来自后端 API（dev 代理 `/api → localhost:8081`），`src/mock/` 为历史模拟层（大部分已成死代码）。

## 2. 技术栈

- **语言：** TypeScript ~6.0 + Vue 3.5（`<script setup>` SFC）
- **框架：** Vue 3.5 + Vite 6 + vue-router 4 + pinia 3 + Element Plus 2.14（自动导入）
- **可视化：** ECharts 6、@antv/g6 4（知识图谱力导图）、pdfjs-dist / mammoth / pptx-preview / xlsx（文档预览）
- **构建/包管理：** Vite 6 + vue-tsc；npm（package-lock.json）

## 3. Quick Start

```bash
# 开发（端口 3000，host '::'，/api 代理到 localhost:8081）
npm install
npm run dev

# 构建（含 vue-tsc -b 类型检查）——兼作 typecheck
npm run build

# 预览构建产物
npm run preview
```

- 前端测试：`npm run test`（vitest，node 环境；**40 用例**=发布状态机 5 + new-conversation SSE/会话流 12 + app-chat-optimize 渲染消毒/会话导出/模板/分桶 23；markdown 测试用 jsdom 局部环境）；E2E：playwright-core 仅用于 `scripts/oo-plugin-probe|introspect.mjs` 探针。
- Lint：**[GAP]** 无 ESLint/Prettier 配置。
- API 契约：后端 springdoc 运行时生成（启动后端后访问 `http://localhost:8081/swagger-ui.html`）；消费侧事实源 `src/api/index.ts`。

## 4. 文档索引

| 路径 | 特性 / 领域 | 状态 |
|------|------------|------|
| `feature-map.md` | 19 项特性聚类 + 前端证据包 + 链状态（链进度的唯一事实源） | CERTAIN |
| `conventions.md` | 前端开发约定（命名/模式/反模式/错误处理） | CERTAIN + INFERRED |
| `decisions/inferred-adjudication.md` | 全量 INFERRED 裁决记录（约 118 条，2026-09-21 用户授权代决） | CERTAIN |
| `spec/iam-frontend.md` | 特性 1 IAM — 前端需求（13 TASK / 10 PAGE / 状态矩阵 / 10 验收项） | CERTAIN + INFERRED×8 + GAP |
| `spec/kb-core-frontend.md` | 特性 2 知识库管理（8 TASK / 5 PAGE / 6 INFERRED） | CERTAIN + INFERRED |
| `spec/parse-strategy-frontend.md` | 特性 3 解析策略（7 TASK / 3 PAGE / 8 INFERRED） | CERTAIN + INFERRED |
| `spec/file-ingestion-frontend.md` | 特性 4 文件上传与摄取（9 TASK / 8 INFERRED） | CERTAIN + INFERRED |
| `spec/chunk-management-frontend.md` | 特性 5 分片管理（13 TASK / 1 PAGE 四分支 / 8 INFERRED） | CERTAIN + INFERRED |
| `spec/onlyoffice-editing-frontend.md` | 特性 6 OnlyOffice（10 TASK / 2 PAGE / 8 INFERRED） | CERTAIN + INFERRED |
| `spec/ai-chunking-frontend.md` | 特性 7 AI 分片（9 TASK / 弹窗+Overlay / 8 INFERRED） | CERTAIN + INFERRED |
| `spec/retrieval-frontend.md` | 特性 9 检索与查询增强（7 TASK / 3 PAGE / 8 INFERRED） | CERTAIN + INFERRED |
| `spec/knowledge-graph-frontend.md` | 特性 10 知识图谱（9 TASK / 8 INFERRED） | CERTAIN + INFERRED |
| `spec/benchmark-evaluation-frontend.md` | 特性 11 评测与基准（8 TASK / 8 INFERRED） | CERTAIN + INFERRED |
| `spec/qa-pairs-frontend.md` | 特性 8 问答对（6 TASK / 5 INFERRED） | CERTAIN + INFERRED |
| `spec/publish-review-frontend.md` | 特性 12 发布与审核（11 TASK / 6 INFERRED） | CERTAIN + INFERRED |
| `spec/platform-config-frontend.md` | 特性 14 模型与平台配置（8 TASK / 9 INFERRED） | CERTAIN + INFERRED |
| `spec/tools-skills-mcp-frontend.md` | 特性 13 工具/技能/MCP/DB（5 TASK / 8 INFERRED） | CERTAIN + INFERRED |
| `spec/workflow-frontend.md` | 特性 16 工作流编排（4 TASK / 5 INFERRED） | CERTAIN + INFERRED |
| `spec/agent-frontend.md` | 特性 17 智能体运行时（4 TASK / 5 INFERRED） | CERTAIN + INFERRED |
| `spec/operation-analytics-frontend.md` | 特性 18 运营分析（7 TASK / 8 INFERRED） | CERTAIN + INFERRED |
| `spec/new-conversation-frontend.md` | 特性 19 新建对话/会话中心（10 TASK / 3 PAGE / 14 验收 / 6 INFERRED 已裁决 2026-09-25） | CERTAIN |
| `design/iam-frontend.md` | 特性 1 IAM — 前端架构（Page Map / 线框 / 状态所有权 / 数据流 / 视觉系统） | CERTAIN + GAP |
| `design/kb-core-frontend.md` | 特性 2 — 架构（卡片列表 + 共享表单体） | CERTAIN |
| `design/parse-strategy-frontend.md` | 特性 3 — 架构（策略弹窗 + 换绑对话框） | CERTAIN |
| `design/file-ingestion-frontend.md` | 特性 4 — 架构（面板编排 + 上传向导 + 轮询） | CERTAIN |
| `design/chunk-management-frontend.md` | 特性 5 — 架构（四类目分支 + 展开行） | CERTAIN |
| `design/onlyoffice-editing-frontend.md` | 特性 6 — 架构（DocsAPI 装配 + 选区桥） | CERTAIN |
| `design/ai-chunking-frontend.md` | 特性 7 — 架构（状态机 + SSE 双流 + 快照校对） | CERTAIN |
| `design/retrieval-frontend.md` | 特性 9 — 架构（预处理管线 + 三面板） | CERTAIN |
| `design/knowledge-graph-frontend.md` | 特性 10 — 架构（G6 + 降级 + 轮询） | CERTAIN |
| `design/benchmark-evaluation-frontend.md` | 特性 11 — 架构（双面板 + 轮询缓存） | CERTAIN |
| `design/qa-pairs-frontend.md` / `publish-review-frontend.md` / `platform-config-frontend.md` / `tools-skills-mcp-frontend.md` / `workflow-frontend.md` / `agent-frontend.md` / `operation-analytics-frontend.md` | 特性 8/12/14/13/16/17/18 — 架构（Page Map / 线框 / 状态所有权 / 数据流） | CERTAIN |
| `design/adr/static-permission-map-default-allow.md` | ADR：静态权限映射 + 未登记默认放行守卫 | Accepted |
| `design/adr/token-in-localstorage.md` | ADR：令牌存 localStorage | Accepted |
| `contract/ui.md` | UI Contract（各特性节：UI/状态契约 + 数据映射 + API 依赖；特性 19 new-conversation 2026-09-25，后端契约已实现） | CERTAIN + TBD（API 契约未发布） |
| `implement/iam-frontend.md` | 特性 1 IAM — 实现记录（特征化：代码 → 契约回溯） | CERTAIN + GAP |
| `sessions/` | 对话归档（宪法要求的会话记录） | 持续追加 |
| `legacy/PLAN.md` | 前 IDD 原始文档（12 阶段「AIS Mock 演示系统」计划，2026-09-21 归档） | 历史 |

IDD 链产出（spec/design/contract/implement）将按特性落在 `spec/`、`design/`、`contract/`、`implement/` 子目录，随链推进在本表登记；前端链文档带 `-frontend` 后缀。

## 5. 关键技能

本项目最常用：`/idd-autopilot`（下一步推荐）、`/idd-spec`（需求，GWT + 页面态矩阵）、`/idd-contract`（UI Contract：页面态 + API 数据映射）、`/idd-diagnose`（调试）；完整清单见仓库根 `AGENTS.md`「关键技能」。

## 验收对照

- 新成员 2 分钟内可理解项目用途（第 1 节）。✅
- 已标注命令均来自 package.json scripts 实测（dev/build/preview）；无法确认的（测试/lint）已标 `[GAP]`。✅
- 文档索引与 `docs/` 实际内容一致（2026-09-21 快照）。✅
