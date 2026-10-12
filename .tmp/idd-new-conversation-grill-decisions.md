# 「新建对话」改造 — idd-grill 裁决记录（移交 /idd-spec）

> 日期：2026-09-25；范围：fastrag-backend（Role: backend）；对齐目标：WeKnora v0.8.2（本地检出 D:\Workspace\python\github\WeKnora）。
> 背景：把"应用运行"改造为 WeKnora 式新建对话设计（用户指令"尽量按照 WeKnora 来改造"）。
> 对比调研全文：`fastrag-backend/docs/research/weknora-vs-fastrag.md`。
> 状态：全部 5 批分支 resolved（2026-09-25），本文件为移交 /idd-spec 的唯一裁决输入。

## 批次 1：改造定位

1. **会话一等公民**：目标态对话体验统一 session-first；"应用运行"入口改造为"带 App 预设的新建对话"；会话不再强制挂应用。
2. **核心映射（可推翻的解释，用户未否决）**：WeKnora agent（会话请求态中 agent_enabled+agent_id 旋钮，session.go:334-347）↔ FastRAG **App**——应用从"对话容器"降级为"会话可选预设"，配置载体而非容器。
3. **请求态持久化**：会话记住最后一次请求态，重开恢复（对齐 WeKnora 复用旧列 agent_config JSONB 的先例，session.go:95-101）。
4. **执行复用**：复用 AppDialogOrchestrator，新增会话级覆盖入参；不另写编排器（避免双实现漂移，design §3 缝 1 同理）。

## 批次 2：数据模型与配置来源

1. `app_conversation.app_id` 放宽为**可空** + 新增 `request_state` JSON 列；不建新表，复用 AppConversationLedger 归属校验/级联删/软删/反馈全链。
2. `request_state` 字段**一次建齐全形状**（app_id/app_enabled/kb_ids/model_id/tool/mcp/skill ids/web_search_enabled 预留死字段，对齐 WeKnora SessionLastRequestState）；第一版仅消费 kb_ids + model + app 挂载。
3. 直聊缺省配置 = **内置缺省常量**（编排器现成兜底：prompt 缺省、maxTokens=2048、temperature=0.7，REQ-B02/E10）；不消费 agent 域默认智能体、不建系统预设 App（后续增强另议）。
4. KB 授权在**对话请求时逐 KB 校验 viewer**，无权 KB 跳过并告警（不建会话锁死；与 REQ-C04 单库失败不中断精神一致；WeKnora 同构 knowledgebase_search.go:192-201）。

## 批次 3：会话能力面

1. **LLM 起标题**：新增显式触发端点（对齐 WeKnora generate_title，routes_chat.go:60），失败回退截断首问 30 字（REQ-E04 兜底保留）。
2. **停止生成（stop）**：本轮新增端点；第一版单实例取消标志即可，不依赖 StreamManager 改造。
3. **会话管理补充**：清空消息（保会话）、批量删除会话、置顶/取消置顶（对齐 WeKnora routes_chat.go:54,59 + is_pinned session.go:90-93）。
4. **fork/rewind 不做**：spec 登记为后续独立增强（避免拖核心交付）。
5. **会话附件（临时文档）不做**：另立特性链，依赖 file-ingestion 临时解析通道。

## 批次 4：执行链与特性边界

1. **消息引用落库**：检索来源（kbId/chunkId/文件名/相似度/定位）随助手消息持久化，历史回看可回看（对齐 WeKnora messages.knowledge_references + chunk StartAt/EndAt 坐标）；数据源 = 现有 SearchResultItem 字段（设计缝 2），纯增列。
2. **直聊默认开敏感词**：与 WeKnora 的**显式偏离**（WeKnora 无敏感词体系），理由 = FastRAG 已裁决的安全默认取向（REQ-D01：未配置视为开启）；复用编排器缝 3 两接入点自然继承。
3. **不做意图分类/闲聊零检索**：检索行为原样消费 RetrievalService（每次都检索）；意图分类+查询改写归 retrieval 特性链（spec §2 Out of Scope 既定；该链查询改写为已登记 stub）。
4. **StreamManager 本特性不动**：SSE 直推现状保持，事件模型改造归 agent 链（对比报告 P0 建议）；stop 落单实例取消标志。

## 批次 5：兼容、迁移与权限

1. 旧端点 `POST /api/apps/{appId}/chat/stream` **保留 + @Deprecated**，前端全部切换后另排下线（前端在另一仓，节奏不受本链控制）。
2. **API Token 支持新通道**：绕过会话归属校验（NFR-02 机器对机器语义），KB 授权仍逐 KB 校验（对齐 WeKnora apiKeyChat sessions 组，routes_chat.go:50-51）。
3. **统一会话列表端点**：按用户全量、置顶优先/updatedAt 倒序、来源标记（"来自应用 X"/直聊）；存量 app 会话自然并入，request_state=NULL 恢复时降级为"挂 App 用 App 配置"，**无存量数据迁移**。
4. **新通道端点权限 = 登录即可**（JWT/API Token），无 app:* 权限注解；资源授权下沉到 KB 检索时校验（对齐 WeKnora sessions 组 Viewer 角色）。

## 移交 /idd-spec 的备注

- **术语**：CONTEXT.md 新增 5 条（新建对话/会话请求态/知识库直聊/会话中心/消息引用），应用条目已加降级注记。
- **显式偏离 WeKnora 清单**（spec 需逐条记理由）：敏感词默认开；不做意图分类；不做 fork/rewind/附件/web 搜索；StreamManager 不动。
- **后续关联链**：意图分类+查询改写（retrieval）；StreamManager 事件模型（agent）；会话附件临时文档（file-ingestion 依赖）；fork/rewind（本域后续增强）。
- **宪法义务**：DDL 双处同步（schema.sql ↔ SchemaInitializer：app_id 可空 + request_state 列 + 消息引用列）；统一 ApiResponse/PageResult（新端点不得复现 listConversations 的 {total,items,page,size} 违例）；CUD 审计沿用 @Loggable。
- **明确排除**：前端改造归前端仓特性链（本仓 Role: backend）；检索管线增强（RRF/防截断等，见对比报告 P0）不在本特性。
