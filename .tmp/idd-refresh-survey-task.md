# 任务书：IDD Refresh 调研（2026-09-21）

## 背景

FastRAG 仓库（`D:\Workspace\java\github\rag\fastrag`）于 2026-09-16 完成 IDD 接入：
- `docs/feature-map.md` 记录 **18 项特性**（含证据包/依赖/热度/Chain status）。
- `CONTEXT.md` 含 IDD 元数据（Role: backend+frontend）+ 术语表。
- 特性 #1 iam 已建 spec（`docs/spec/iam.md` + `iam-frontend.md`），blocked 于 14 条 INFERRED 确认。

自基线提交 `4ed27bb`（2026-08-30，ai-chunk 区域框选）以来有 4 个 commit（至 `d8f30cd` 2026-09-16 19:29），**97 文件 +5581/−2288**。本次调研目标：**核对 18 项特性地图是否仍与代码一致**，刷新证据与热度，不是从零重扫。

## 自上次调研以来的漂移区域（git 4ed27bb..HEAD 目录聚合）

- backend：`fastrag-modules/fastrag-graph-eval`（graph/util ×5+5、mapper ×3、entity ×3）、`fastrag-modules/fastrag-platform`（controller ×3、service+impl ×5、entity ×2）、`fastrag-modules/fastrag-knowledge`（chunking/consumer/service + 同名 test）、`fastrag-infra`（neo4j ×3、graph ×3）、`init-scripts` ×2
- frontend：`src/views/admin/system` ×5、`src/views/admin/account` ×4、`src/views/knowledge/detail/components` ×4、`src/composables` ×3、`src/router` ×2、`src/api` ×1、`src/types` ×1、`src/config` ×1
- 其他：`.tmp` ×2（忽略）

## 调研问题

### A. 后端（Backend 调研员回答）

1. **控制器清单是否仍为 ~55 个、模块划分是否与 feature-map「模块地图」一致？** 逐模块数 Controller 类（`fastrag-backend/fastrag-modules/*/src/main/java/**/controller/*.java` + fastrag-infra/security/ai/bootstrap 无 controller 的话说明）。列出与地图记载（iam 7、knowledge、retrieval、graph-eval、publish、application、agent、tools、platform、operation 各自控制器数）不符之处。
2. **漂移区域改了什么（按特性归因）**：graph-eval 的 graph/util、platform 的 controller/service、knowledge 的 chunking/consumer、infra 的 neo4j/graph —— 各自的新增/修改要点（类名、方法级迹象即可），归入 18 特性中的哪一项；若出现地图未覆盖的**新能力**（新控制器、新表、新 MQ 队列、新外部集成）单独列出。
3. **init-scripts 的 2 个变更**：改了哪张表/新表？是否仍是无 Java 实体的 DDL-only？
4. **各 Maven 模块 Java 源文件行数**（`find … -name "*.java" | xargs wc -l` 粗算）与地图记载（knowledge 23.6k、agent 8.8k、tools 8.9k、graph-eval 5.0k、retrieval 3.5k、application 3.4k、platform 2.8k、iam 2.6k、operation 2.1k、common/security/infra/ai）是否有 >20% 漂移。

### B. 前端 + 文档/热度（Frontend 调研员回答）

1. **路由数是否仍为 60？** `fastrag-frontend/src/router/routes.ts` 实数；孤儿路由（knowledge-review、robot-operation、admin/content 等）现状是否与 CONTEXT.md「命名冲突与勘误」一致。
2. **漂移区域改了什么**：admin/system ×5、admin/account ×4、knowledge/detail/components ×4（AiChunkPanel/AiChunkPdfOverlay/GraphSettingsPopup/KnowledgeGraphPanel）、composables ×3、router ×2 —— 按特性归因；新页面/新 composable 单列。
3. **60 天热度刷新**：`git log --since="60 days ago" --name-only` 按目录聚合，给出 Top10 热点目录及其归因特性；与地图记载（file-ingestion 219 次、iam 87、graph 74、platform 66、ai-chunk components 45…）对比是否换位。
4. **docs/README.md 与 docs/conventions.md 是否与 feature-map 现状一致**（特性数、链状态、索引条目）；docs/legacy/ 是否仍完好归档。

## 输出格式

返回结构化 markdown：
- `## 后端核对` / `## 前端核对`：逐问题答案，每条结论附证据（file:line 或命令输出摘要）
- `## 特性归因表`：漂移 → 特性编号/名称 → 变更要点 → 是否影响地图条目（无影响/需更新证据/需新增特性）
- `## 结论`：18 项聚类是否成立（成立/需合并/需拆分/需新增），一句话理由
- 证据分层：直接可观测标 CERTAIN 并引 file:line；结构推断标 INFERRED；查不到的标 GAP，**不得臆造**
