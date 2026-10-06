# FastRAG 后端（fastrag-backend）— 目录级代理配置

> 全局宪法与工作区约定见 [../AGENTS.md](../AGENTS.md) 与 [../AGENT.md](../AGENT.md)，两者对本目录同样生效；本文件只做后端目录的索引与摘要，冲突时以上位文件为准。

## IDD 文档（本目录，2026-09-21 初始化）

| 文件 | 用途 |
|------|------|
| [CONTEXT.md](./CONTEXT.md) | 后端领域术语表 + IDD 元数据（Role: backend）——命名前必读 |
| [docs/feature-map.md](./docs/feature-map.md) | 19 项特性聚类 + 证据包 + 链状态（**链进度唯一事实源**） |
| [docs/README.md](./docs/README.md) | 后端总览 + 技术栈 + Quick Start + 文档索引 |
| [docs/conventions.md](./docs/conventions.md) | 后端开发约定（命名/模式/反模式/错误处理） |

全仓视角（含前端）文档在上位仓 `docs/`；`docs/legacy/` 存放前 IDD 时代文档归档。

## 硬约定摘要（继承自宪法，全文见上位文件）

- 统一返回 `ApiResponse`（不是 Result/R）；分页统一 `PageResult{list,total,page,pageSize}`
- 业务错误 = HTTP 200 + body.code；调用方必须判断 body.code
- 禁止 Elasticsearch 相关开发；禁止在 fastrag-ai / fastrag-infra 直接查业务库
- DDL 双处同步：`init-scripts/schema.sql` 全量 ↔ `SchemaInitializer` 运行时幂等增补
- 图存储：Neo4j 唯一实现（GraphStore，启动 fail-fast），勿再引入 MySQL 图表
- IDD 工作流：新特性走 `/idd-grill → /idd-spec → /idd-design → /idd-contract → /idd-tdd → /idd-implement`；缺陷走 `/idd-diagnose`
