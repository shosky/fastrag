# CONTEXT.md — FastRAG 后端领域术语表

> 本文件由 idd-grill 维护，fastrag-backend 范围内所有模块文档共享此术语定义。
> 上位术语表：[../CONTEXT.md](../CONTEXT.md)（backend+frontend 全仓视角；两者冲突时以后端代码可观测事实为准并回改上位表）。

> **IDD Metadata**（由 idd-onboard 于 2026-09-21 初始化写入，范围 = fastrag-backend 目录）
> - **Repository Role:** `backend`（CERTAIN — Java 17 / Spring Boot 3.2.5 / 15 个 Maven 模块 / 55 个控制器 / ~68.9k LOC；目录内无任何前端代码）
> - **API Contract Source:** 本仓库运行时生成 — springdoc-openapi 2.5.0（`GET /v3/api-docs`、`/swagger-ui.html`，服务端口 8081）；仓库内无静态 OpenAPI 文件，控制器无手写 `@Tag`/`@Operation` 注解（pom.xml、fastrag-bootstrap/src/main/resources/application.yml）
> - **文档语言：** 中文

## 核心领域术语

**知识库（Knowledge Base）** — 用户创建的知识管理单元，包含文件、分片、QA 对、图谱等子资源。表 `kb`，端点前缀 `/api/kb`。

**知识分片（Chunk）** — 文档经过解析和切分后的文本片段，是向量检索和图谱构建的最小单元。分为**父分片（Parent Chunk）**与**子分片（Child Chunk）**两级：子分片用于向量召回，父分片（按标题层级聚合）作为命中后的上下文返回。表 `kb_chunk`。

**解析策略（Parse Strategy）** — 定义文档切分规则的配置，包含分片长度、重叠、分隔符、LLM/VLM 模型等参数。表 `kb_parse_strategy`。

**策略绑定（Strategy Binding）** — 文件与解析策略的关联。分为两级：知识库级默认绑定（按文件扩展名自动匹配）与文档级覆盖（用户显式指定，优先于自动匹配）。绑定必须与实际分片结果一致：变更绑定必然触发重新分片（ADR-0001），不允许只改绑定不重切的漂移状态。

**自动匹配（Auto Match）** — 未指定文档级覆盖时，按文件扩展名在知识库内匹配解析策略的回退机制。

**重新分片（Re-chunk）** — 删除文档既有分片与向量后，按当前（或新指定的）策略绑定重跑「解析→分片→向量化」流水线的操作。原文档与 QA 对保留，图谱数据随分片重建。

**AI分片（AI Chunking）** — 知识库文件的一种加工工作流：预览原件 → OCR 解析 → 结构级跨页合并 → 自动（LLM）或手动分片 → 应用落库，以用户确认的分片边界为唯一事实源替换落库。区别于「重新分片」（后者会重新解析原文件并重新切分，可能冲掉跨页合并与手工边界）。

**问答对提炼（QA Generation）** — 用 LLM 以父分片为提取单位、从分片内容提炼完整问答对（问题+答案）的 AI 加工工作流，产物落 `kb_qa_pair`（source=ai，初始 draft）；`processing_mode=qa` 的文件在摄入完成后自动触发，其余文件经文件级手动触发（含多选批量）〔2026-09-26 grill 裁决〕。Avoid: 问答对提取（KbFile.java:27 历史注释用语，同义）、Doc2Query（WeKnora 只生成问题不生成答案的模式，本功能不采用）。

**段落对齐模型（Paragraph Alignment Model）** — 后端产出的结构化段落序列，每段含 id/type/text/page/坐标/所属 chunk，作为 AI分片 的单一事实源。Markdown 是其序列化，Chunk 是其切分结果。

**结构级跨页合并（Structural Cross-page Merge）** — 解析层把被页边界/分页符割裂的同一逻辑块（表格、代码块、列表、段落、题注）并回完整结构的处理，使 Markdown 与 Chunk 不再被页边界割裂。

**向量检索（Vector Retrieval）** — 通过文本向量化后在 Milvus 中进行相似度搜索的检索方式。

**混合检索（Hybrid Retrieval）** — 向量检索 + 关键词检索（MySQL LIKE，ngram FULLTEXT 索引加速，见 kb_chunk.idx_content_fulltext）+ 重排序（Rerank）组合的检索策略。

**召回准入（Recall Admission）** — QA 对进入检索 qa 通道的资格规则：`source='manual' OR status='confirmed'`——人工创建的 QA 对创建即可召回，AI 提炼的 QA 对须经确认后才召回〔2026-09-26 grill 裁决，修订 qa-pairs spec REQ-D04 的「确认仅为管理标记」口径——对 manual 数据语义不变〕。Avoid: 审核流（本项目无多级审核，仅单向 confirm）。

**知识图谱（Knowledge Graph）** — 从分片中抽取实体和关系构建的图结构，存储在 Neo4j Graph Store 中（唯一实现，启动 fail-fast）。

**抽取单元（Extraction Unit）** — 图谱抽取专用的重组文本块：把约 500 字的检索分片重组为约 2000 字符、逻辑完整的单元后送 LLM 抽取三元组，仅服务图谱抽取、不改变检索分片本身（ExtractionUnitAssembler，v1.2 架构）。缓存与 replay 落 `kb_graph_unit`。

**实体合并（Entity Merge）** — 基于实体向量相似度发现合并候选（EntitySimilarity 余弦计算），将同义/同名实体合并入库；配套实体名清洗闸门（EntityNameGuard）拦截值型内容与 OCR 幻觉乱码入图。⚠ 审计写入未接线：mergeEntities/mergeEntity 现不写 `kb_graph_merge_audit`（契约先行欠账，现状见 docs/contract/data.md knowledge-graph 节），回滚/追溯依赖该表者须先补实现。

**图谱评估（Graph Evaluation）** — 通过基准测试（Benchmark）和评测（Evaluation）衡量知识库检索质量。

**发布（Publish）** — 将知识库从编辑状态切换为线上服务状态，支持立即上线、下线、定时发布、版本回滚。

**应用（App）** — 面向终端用户的应用单元，绑定知识库、工具、MCP 服务等能力，提供对话式交互。表 `app`。〔2026-09-25 决议（WeKnora 对齐）：应用从"对话容器"降级为"会话可选预设"——对齐 WeKnora 会话请求态中的 agent_enabled+agent_id 旋钮（WeKnora internal/types/session.go:334-347），会话不再强制挂应用。〕

**新建对话（New Conversation）** — 会话优先的对话入口：用户不预配应用，直接在会话上选定知识库范围与模型开聊（对齐 WeKnora session-first 设计）。Avoid: 应用运行、免应用问答。

**会话请求态（Session Request State）** — 持久化在会话上的最后一次对话输入范围（知识库 ID 列表、模型、可选挂载的应用等），重开会话时据此恢复输入框状态；对齐 WeKnora SessionLastRequestState（复用旧列存 JSON，避免新迁移）。Avoid: 会话配置、输入框快照。

**知识库直聊（Direct Chat）** — 不挂应用的会话对话通道：能力范围取会话请求态（知识库+模型），prompt/预算用内置缺省值；挂应用时应用配置作为预设注入同一条编排链。Avoid: 裸会话、临时对话。

**会话中心（Session Center）** — 按用户全量列出会话的统一对话入口：置顶优先、updatedAt 倒序，每条带来源标记（直聊/来自应用 X）；存量按应用创建的会话自然并入。Avoid: 会话列表页、应用运行入口。

**消息引用（Message References）** — 随助手消息落库的检索来源记录（知识库/分片/文件名/相似度/定位），重开会话历史可回看；对齐 WeKnora messages.knowledge_references。Avoid: sources 事件（专指流内一次性推送，不落库）。

**工作流（Workflow）** — 应用内的可视化编排图，由节点（LLM、检索、重排序、模板、代码、条件、开始/结束）和边组成。表 `wf_*`。

**智能体（Agent）** — 具有自主决策能力的 AI 实体，通过 Backend + Graph + Middleware + State 架构运行，支持多后端沙箱（docker/cube/e2b/disabled，2026-09-28 实装）。

**工具（Tool）** — 可被应用或智能体调用的外部能力，分为内置工具、API 工具、MCP 工具、技能（Skill）四类。

**MCP 服务（MCP Service）** — 基于 Model Context Protocol 的外部服务，支持 stdio 和 SSE 两种传输方式。

**技能（Skill）** — 文件化（Markdown+frontmatter）的可导入导出能力包，具有依赖声明；可见性由「技能分享」治理、能力交付由「技能挂载」治理（见下方两词条，2026-10-06 拆词取代旧「作用域（应用级/知识库级）」混称）。

**技能分享（Skill Visibility）** — 技能可见性的三级模型：global（全员）/ department（指定部门）/ user（缺省，仅属主与指定用户可见），非管理员提交强制降级 user 级（spec REQ-B09）。〔2026-10-06 grill 裁决：按 spec 补齐接线不砍——share_config 落库 + SkillMapper 错列名（created_by→creator）修复 + 导入补 creator + AccessManager 接入 list/requireManage〕Avoid: 作用域、权限级别、scope。

**技能挂载（Skill Binding）** — 应用经 `app_skill_binding` 表对技能的引用（enabled 开关 + 可选 params）。绑定存技能主键 id，运行时由 id 解析 slug 后参与编排与物化。〔2026-10-06 grill 裁决：绑定语义统一到 id，零迁移〕Avoid: 技能作用域、技能授权、作用域绑定。

**技能正文拦截读（Intercepted Skill Read）** — 对话中 `read_file` 命中 `/workspace/skills/{slug}/SKILL.md` 时由 SkillsMiddleware 拦截短路，以 DB `skill.content` 为源返回 SKILL.md 全文（不依赖沙箱存活）；沙箱侧物化仍执行，服务目录可见与 shell 场景。〔2026-10-06 grill 裁决：采纳拦截全文协议，修复「拦截仅返回 name+description」断裂〕Avoid: 沙箱直读协议、skill:// 虚拟协议、虚拟文件系统读。

**技能安装引导（install_skill）** — 内置工具，在对话中引导用户经技能导入流水线（ZIP/SKILL.md 上传→草稿→确认）完成安装。〔2026-10-06 grill 裁决：改为条件注入——仅当本次上下文存在技能挂载（或沙箱启用）时注册，spec REQ-E03「无条件注入」同步修订；执行内容从指引桩改为真实导入步骤〕Avoid: 技能自动安装、运行时安装（对话内不落库装技能）。

**技能 Slug** — 技能的文件系统安全标识，兼作磁盘目录名与沙箱虚拟路径段（`/workspace/skills/{slug}`）。〔2026-10-06 grill 裁决：入口白名单正则（形如 `^[a-z0-9][a-z0-9._-]{0,63}$`）在创建/导入/文件访问/草稿四入口统一强制，draftId 同字符集；弃「仅出口 normalize 堵穿越」方案〕Avoid: 技能名（name 是展示名，slug 才进路径）。

**技能正文事实源（Skill Content Source of Truth）** — DB `skill.content` 是 SKILL.md 正文的唯一事实源：拦截读以它为源，文件 API 写 SKILL.md 时同步回写（frontmatter 变更顺带更新元数据列），内置技能启动播种只在 DB 无记录时执行、不覆盖。磁盘目录仅为附属文件（脚本/资源）工作区。〔2026-10-06 grill 裁决〕Avoid: 磁盘为源、三源并存。

**操作审计（Audit Log）** — 通过 @Loggable 注解 + AOP 切面自动记录的知识库操作日志。

**沙箱（Sandbox）** — 为智能体工具代码执行提供的隔离环境，多后端抽象对齐 WeKnora：`docker`/`cube`/`e2b`/`disabled` 四类型（`host` 为桌面客户端专属，不做）。容器侧协议按后端各自实现——Docker = Docker Engine API 直连（exec/archive，容器内零服务端），Cube/E2B = 控制面 REST + 容器内 envd 数据面；fastrag 早期「容器内 HTTP API」设计已废弃〔2026-09-27 grill 裁决〕。

**沙箱配置（Sandbox Config）** — 命名的沙箱后端配置实体（新表：类型、endpoint、凭据、镜像/模板、资源上限、网络策略），带 `user_id` 归属（创建者可见，对齐 fastrag 资源归属惯例）；应用定义通过 `sandbox_config_id` 引用（对齐 WeKnora custom_agent.sandbox_config_id），未选或禁用时工具执行降级。管理员级全局启用开关放 platform-config（对齐 WeKnora 系统设置全局开关）。Avoid: 沙箱设置（不指全局 yml 配置）、租户沙箱配置（fastrag 无租户体系）。

**沙箱工作区布局（Sandbox Workspace Layout）** — 沙箱容器内路径约定，对齐 WeKnora：`/workspace` 根 + `/workspace/input`（只读附件）+ `/workspace/output`（产物收集），技能迁 `/workspace/skills/{slug}`；替代旧 `/home/gem/*` 约定〔2026-09-27 grill 裁决〕。工具面读写根：可读 = `/workspace` 全部，可写 = `/workspace` 但拒写 `/workspace/input` 与 `/workspace/skills`（只读子树）〔2026-09-28 grill 裁决〕。

**沙箱容器加固基线（Sandbox Container Hardening）** — Docker 沙箱容器创建参数基线，照搬 WeKnora hostConfig：CapDrop ALL + 仅加 CHOWN/DAC_OVERRIDE/FOWNER/FSETID/SETGID/SETUID/KILL 七项 caps、no-new-privileges、Init=true（tini 防僵尸）、memory=memorySwap（禁 swap）、默认 2 核/2GiB/512 pids；资源三值为 sandbox_config 实体字段〔2026-09-28 grill 裁决〕。Avoid: 命令内容黑名单（WeKnora 现代会话路径不做危险命令校验，隔离靠容器加固+路径钳制）。

**Endpoint 拨号防护（Dial Guard）** — 沙箱后端 endpoint（Docker daemon TCP/CubeAPI/E2B API）的 SSRF 防护：云 metadata/link-local 地址恒拒（显式允许私网也拒）、私网段默认拒须配置显式放行、配置保存时校验 + 建连时对解析 IP 复查〔2026-09-28 grill 裁决〕。

**沙箱配置验证（Sandbox Config Verify）** — 配置保存前的探活动作：Docker ping daemon / Cube·E2B 控制面鉴权 + 可选列模板清单；配置名下活沙箱库存（含 paused）为独立查询端点，换凭据前须先查库存确认无遗留〔2026-09-28 grill 裁决，对齐 WeKnora templates/query + /:id/sandboxes〕。Avoid: 连接并继续（WeKnora UI 用语）。

**附件暂存（Sandbox Attachment Staging）** — 每轮对话开始把用户上传附件实体拷入沙箱 `/workspace/input`（只读）的动作；AttachmentMiddleware 注入 prompt 的路径随之改为沙箱内路径〔2026-09-28 grill 裁决〕。Avoid: 附件注入（仅指 prompt 文本注入，不含实体拷贝）。

**沙箱降级（Sandbox Degradation）** — 沙箱不可用时工具仍注册、执行返回「Error: …」文本不阻断 run 的行为，分三原因分支：总开关关闭 / 应用未引用或配置禁用 / provider 不可用，文本须区分原因〔2026-09-28 grill 裁决，扩展 REQ-H03〕。Avoid: fail-fast（沙箱非唯一实现，永不启动失败）。

**沙箱终端（Sandbox Terminal）/图形桌面** — 交互终端（PTY over WebSocket）与 noVNC 桌面，WeKnora 中仅 Cube/E2B 后端支持、Docker 不支持；fastrag 本次后置，仅在 provider 能力位（Capabilities）预留声明。Avoid: WebSSH。

**沙箱绑定（Sandbox Binding）** — 会话到沙箱实例的持久映射，落 Redis（`sandbox:binding:{userId}:{sessionId}`），字段对齐 WeKnora：version/provider/sandboxId/configId/createdAt/staleAt。绑定键 = (userId, sessionId) 会话级——同会话多轮共享容器（pip 安装/文件/进程跨轮保持），新开会话即新沙箱，旧 `fileThreadId` 绑定键废止〔2026-09-27 grill 裁决，修订 REQ-H01 确定性 ID 条款〕。

**孤儿沙箱对账（Orphan Sandbox Reconciliation）** — 周期任务：provider 侧 List 按 managed label + configId metadata 过滤（含 paused），对比绑定存储，删除未绑定沙箱，带宽限期保护新建未绑定实例〔2026-09-27 grill 裁决〕。

**知识库权限（KB ACL）** — 基于 Redis 缓存的知识库级 RBAC（`@KbAuth` 切面），角色分为 owner > editor > viewer。

**查询增强（Query Enhancement）** — 对用户查询进行改写、同义扩展、规则应用等预处理，提升检索质量。同义扩展仅消费启用状态的术语词条（`term_record.status`）。

**更新提醒（Update Remind）** — 基于定时任务的文档新鲜度检查机制，提醒用户知识库内容可能过时。

## 技术术语

**SSE（Server-Sent Events）** — 服务端推送事件流（SseEmitter），用于 LLM 流式输出和 Agent 实时响应；跨实例经 Redis pub/sub 转发。

**Graph Store** — 知识图谱存储抽象接口，唯一实现为 Neo4jGraphStore（ADR-0002 已落地：MySQL 实现已删除，Neo4j 为必需依赖，启动 fail-fast）。

**Vector Store** — 向量存储抽象接口，当前仅有 Milvus 实现。

**Ingestion Pipeline** — 文档摄入流水线：上传 → 解析 → 切分 → 向量化 → 存储，通过 RabbitMQ（`fastrag.ingestion.queue`）异步执行。

**SchemaInitializer** — 启动时执行的幂等增量 DDL 机制（替代 Flyway）；与 `init-scripts/schema.sql` 全量脚本须保持一致（2026-09-21 已对齐图谱表）。

**统一响应（ApiResponse）** — 全部控制器的返回包装；**业务错误 = HTTP 200 + body.code**，前端/调用方必须判断 body.code。分页统一 `PageResult{list,total,page,pageSize}`。

## 命名冲突与勘误（idd-onboard 检测）

- **产品命名漂移（INFERRED）**：代码与根 AGENT.md 使用 FastRAG，产品展示名 AIS（见根仓 manual.md / 前端标题）——两套命名并存，后端代码内统一为 `com.fastrag.*` 包名。
- **一致性确认（CERTAIN）**：统一返回 ApiResponse / PageResult 三方一致（common 定义 → 全部 55 控制器 → 前端 request.ts 解包），无冲突。
- **历史勘误（已消除）**：schema.sql 尾部 4 张已废弃 MySQL 图表 CREATE 与迁移脚本矛盾——已于 2026-09-21 修复（删除死表 DDL、补入 kb_graph_stitch/kb_graph_unit/kb_graph_merge_audit 3 张运行时表）。
