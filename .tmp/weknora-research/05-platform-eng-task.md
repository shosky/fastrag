# 调研任务：WeKnora 数据模型 / API 约定 / 多租户 / 工程化质量（维度 5/5）

## 背景
为 FastRAG 对标调研。FastRAG 现状锚点：JWT+API Token+@KbAuth KB 级 ACL（owner/editor/viewer）+RBAC+组织树；统一 ApiResponse/PageResult、业务错误=HTTP 200+body.code；DDL 双处同步（schema.sql↔SchemaInitializer）已产生多处漂移缺陷；测试分布极不均（tools 137 用例最多，多模块零测试）；已登记 40+ 代码债务；无链路追踪。本维度聚焦 WeKnora 的**数据模型、API/权限、安全与工程化**，各业务能力由其他任务负责。

## 调研对象（只读）
- `D:\Workspace\python\github\WeKnora\internal\models\`、`internal\database\`、`migrations\`（表清单与迁移纪律）
- `internal\handler\`、`internal\router\`、`internal\middleware\`（API 约定、鉴权、响应 envelope、错误码）
- `internal\ratelimit\`、`internal\ipclass\`、`internal\storageallowlist\`、`internal\storageurl\`、`internal\errors\`、`internal\tracing\`
- `tests/`、`docreader/tests/`、各包 `*_test.go`（抽样统计覆盖面）；`Makefile`、`.github/`（若有 CI）、`SECURITY.md`、`licenses/`、`THIRD_PARTY_NOTICES.md`
- `frontend/package.json`（只看技术栈，不深入）

## 必答问题
1. 数据模型全景：核心表/实体清单（知识库/chunk/会话/用户/空间…），多租户（空间 Space?）模型如何设计？
2. RBAC：README 说"四级角色、资源归属、空间审计日志"——代码如何落地？
3. API 约定：响应 envelope、错误码体系、分页约定；与 FastRAG 的 ApiResponse/PageResult/HTTP200+code 对比。
4. API Key 与 Principal 模型（程序化集成）如何设计？
5. 安全治理：`ratelimit/`、`ipclass/`、`storageallowlist/`、`storageurl/` 各防什么（SSRF? 内网穿透?）？上传/下载 URL 的安全处理？JWT/会话机制？
6. 迁移纪律：migrations 命名/版本化/回滚；对比 FastRAG 双处 DDL 漂移问题。
7. 测试与质量：Go 测试分布抽样（哪些层有测试）、docreader 测试、`tests/` 目录是什么（e2e?）、CI 流水线、Makefile 目标（lint/test/build）。
8. 可观测性：`tracing/` 是什么（OpenTelemetry? Langfuse?）、日志方案。
9. 合规与供应链：licenses/、THIRD_PARTY_NOTICES、SECURITY.md 的工程规范信号。
10. 前端技术栈一句话结论。

## 输出要求（中文 markdown）
- **表/实体清单**（核心 15-30 张，一张表）
- **API 与权限机制**（带 `文件:行号` 证据）
- **安全治理机制逐条**（证据）
- **工程化优点清单**（机制→证据→为什么好→对 FastRAG 借鉴意义）
- **弱点/风险**
- 禁止臆测；区分"代码证实"与"文档宣称"
