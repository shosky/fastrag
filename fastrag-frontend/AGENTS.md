# FastRAG 前端（fastrag-frontend）— 智能体配置

> 本文件管辖 `fastrag-frontend` 目录。仓库根 `AGENTS.md`（全仓 IDD 体系 + FastRAG 前端分页规范）继续适用，两者叠加；冲突时以更局部（本文件）为准。

## IDD 文档体系（前端树）

本项目在 `docs/` 维护**前端单侧** IDD 文档（仓库根 `docs/` 不在本树范围，勿读写）：

| 阶段 | 位置 | 用途 |
|------|------|------|
| 总览 | `docs/README.md` | 前端概览 + 文档索引 |
| 特性地图 | `docs/feature-map.md` | 18 项特性、前端证据包、链状态（**链进度以此处 Chain status 为准**） |
| 规范 | `docs/conventions.md` | 前端开发约定（命名/模式/反模式） |
| 术语 | `CONTEXT.md`（本目录） | 领域术语 + IDD 元数据（Role: frontend） |
| Spec | `docs/spec/` | 需求（GWT + 页面态矩阵），文档名带 `-frontend` 后缀 |
| Design | `docs/design/` | 前端架构 + ADR |
| Contract | `docs/contract/` | UI Contract：页面态矩阵 + API 数据映射（API 契约以后端 springdoc `/api → :8081` 为准） |
| TDD | `tests/`（代码） | 红-绿-重构（当前无测试资产 [GAP]） |
| Implement | `docs/implement/` | 实现记录 |
| Legacy | `docs/legacy/` | 前 IDD 原始文档 |

## 关键技能

- **`/idd-grill`** — 对齐需求、维护术语表；任何新工作先做这个。
- **`/idd-autopilot`** — 「下一步做什么」（读 `docs/feature-map.md` 的 Chain status）。
- **`/idd-spec → /idd-design → /idd-contract → /idd-tdd → /idd-implement`** — 单特性链，按依赖顺序构建。
- **`/idd-diagnose`** — 缺陷调试；**`/idd-review`** — 文档一致性抽查。

## 变更如何流转

新特性：`/idd-grill → /idd-spec → /idd-design → /idd-contract → /idd-tdd → /idd-implement`；需求变更：更新 spec 后逐层向下重新 grill；缺陷：`/idd-diagnose` → 架构根因则 `/idd-design` → `/idd-tdd` 补回归。

## 领域语言

命名任何东西之前先读本目录 `CONTEXT.md`；术语表标注「避免」的同义词不得使用。分页是 `PageResult{list,total,page,pageSize}`（消费侧经 usePagination），统一响应 `ApiResponse`（request.ts 解包 `{code,data,message}`）。

## 回答置信度分层

从代码回答问题用三层标注：**CERTAIN**（引 file:line）、**INFERRED**（阶段末向用户确认，否决降级 GAP）、**GAP**（内联 `[GAP: ...]` 占位，绝不臆造）。

## 前端特有纪律（详见 docs/conventions.md）

- 分页一律 `usePagination` + BEM `${block}__pagination` 模板（根 AGENTS.md「FastRAG 前端规范」全文适用）。
- 禁止新增 `src/mock/` 消费（16/31 模块已死代码）。
- 新 API 写入 `src/api/index.ts`；SSE 用 fetch 直连（有先例）。
- 不新建孤儿文件：无路由挂载的页面/组件必须说明归属或删除。
