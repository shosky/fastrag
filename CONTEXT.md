# CONTEXT.md — FastRAG 领域术语表

> 本文件由 idd-grill 维护，所有模块文档共享此术语定义。

## 核心领域术语

**知识库（Knowledge Base）** — 用户创建的知识管理单元，包含文件、分片、QA 对、图谱等子资源。

**知识分片（Chunk）** — 文档经过解析和切分后的文本片段，是向量检索和图谱构建的最小单元。分为**父分片（Parent Chunk）**与**子分片（Child Chunk）**两级：子分片用于向量召回，父分片（按标题层级聚合）作为命中后的上下文返回。

**解析策略（Parse Strategy）** — 定义文档切分规则的配置，包含分片长度、重叠、分隔符、LLM/VLM 模型等参数。

**策略绑定（Strategy Binding）** — 文件与解析策略的关联。分为两级：知识库级默认绑定（按文件扩展名自动匹配）与文档级覆盖（用户显式指定，优先于自动匹配）。绑定必须与实际分片结果一致：变更绑定必然触发重新分片，不允许只改绑定不重切的漂移状态。

**自动匹配（Auto Match）** — 未指定文档级覆盖时，按文件扩展名在知识库内匹配解析策略的回退机制。

**重新分片（Re-chunk）** — 删除文档既有分片与向量后，按当前（或新指定的）策略绑定重跑「解析→分片→向量化」流水线的操作。原文档与 QA 对保留，图谱数据随分片重建。

**向量检索（Vector Retrieval）** — 通过文本向量化后在 Milvus 中进行相似度搜索的检索方式。

**混合检索（Hybrid Retrieval）** — 向量检索 + 关键词检索 + 重排序（Rerank）组合的检索策略。

**知识图谱（Knowledge Graph）** — 从分片中抽取实体和关系构建的图结构，存储在 Neo4j 或 MySQL Graph Store 中。

**图谱评估（Graph Evaluation）** — 通过基准测试（Benchmark）和评测（Evaluation）衡量知识库检索质量。

**发布（Publish）** — 将知识库从编辑状态切换为线上服务状态，支持立即上线、下线、定时发布、版本回滚。

**应用（App）** — 面向终端用户的应用单元，绑定知识库、工具、MCP 服务等能力，提供对话式交互。

**工作流（Workflow）** — 应用内的可视化编排图，由节点（LLM、检索、重排序、模板、代码、条件、开始/结束）和边组成。

**智能体（Agent）** — 具有自主决策能力的 AI 实体，通过 Backend + Graph + Middleware + State 架构运行。

**工具（Tool）** — 可被应用或智能体调用的外部能力，分为内置工具、API 工具、MCP 工具、技能（Skill）四类。

**MCP 服务（MCP Service）** — 基于 Model Context Protocol 的外部服务，支持 stdio 和 SSE 两种传输方式。

**技能（Skill）** — 可导入导出的能力包，具有依赖声明和作用域（应用级/知识库级）。

**操作审计（Audit Log）** — 通过 @Loggable 注解 + AOP 切面自动记录的知识库操作日志。

**知识库权限（KB ACL）** — 基于 Redis 缓存的知识库级 RBAC，角色分为 owner > editor > viewer。

**查询增强（Query Enhancement）** — 对用户查询进行改写、同义扩展、规则应用等预处理，提升检索质量。

**更新提醒（Update Remind）** — 基于定时任务的文档新鲜度检查机制，提醒用户知识库内容可能过时。

## 技术术语

**SSE（Server-Sent Events）** — 服务端推送事件流，用于 LLM 流式输出和 Agent 实时响应。

**Graph Store** — 知识图谱存储抽象接口，有 MySQL 实现和 Neo4j 实现两种。

**Vector Store** — 向量存储抽象接口，当前仅有 Milvus 实现。

**Ingestion Pipeline** — 文档摄入流水线：上传 → 解析 → 切分 → 向量化 → 存储，通过 RabbitMQ 异步执行。
