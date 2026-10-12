# backlog 任务书 — 评审缺口三路并行实施（2026-10-07）

> 输入：docs/plan/weknora-gap-review.md（27 findings）。本轮实施其中可机械完成的 18 项，按模块分三路并行。
> 你是实现代理：按 /idd-tdd（先读 C:\Users\63179\.zcode\skills\idd-tdd\SKILL.md）先红后绿。**禁止 git commit**。
> 基线：全仓 16 模块绿（agent 167+11skip / tools 229 / application 78 / security 4 / bootstrap 1）。收工时你负责的模块必须全绿。
> 防挂起：单条 bash 控制在几分钟内，优先 `-Dtest` 与 `-pl`；测试命令一律带 `-am`（fastrag-backend 目录下）。
> **文件所有权（并行三路，越界会互相覆盖）**：AgentEngine.java/RunEventPublisher.java/event 包/ToolRegistry.java/各 middleware 既有文件 = 单元 I 专属；RetrievalServiceImpl/QueryEnhanceServiceImpl/AppServiceImpl/KnowledgeToolExecutor + 新增 HistoryMiddleware = 单元 R 专属；sandbox/**、security/**、bootstrap/**、init-scripts/schema.sql、operation listener = 单元 S 专属。BaseContext.java：仅单元 R 允许新增字段；SandboxExecutionService.java：单元 I 只许改 truncate 方法区域，单元 S 只许改 release/output 打包区域（不同区域，勿动对方锚点）。

## 单元 I — Agent 运行时治理（fastrag-agent + ToolRegistry）

- **I1（P0-1）rune 安全截断**：新增统一工具（agent 模块 util，如 OutputBudget）：按 code point 截断（offsetByCodePoints），AgentEngine.truncate(:905 附近) 与 SandboxExecutionService.truncate(:440 附近，仅此方法) 改用。
- **I2（P0-3）事件可重放 StreamManager**：新接口 AppendEvent/GetEvents(fromOffset)；memory 实现 + Redis 实现（StringRedisTemplate，key 含 runId，RPush/LRange，TTL 24h，Redis 不可用降级 memory 并 warn）；RunEventPublisher 的 seq 改自增单调（AtomicLong），事件存储接 StreamManager；容量上限（如 500 条/run，超限丢最旧）+ run 终态时清理；**streamEvents（AgentRunServiceImpl:133-163）改为：优先按 afterSeq 增量拉事件推 SSE（500ms 轮询事件通道），run 表轮询仅作终态兜底**。
- **I3（P1-1）输出预算分层**：执行器层（HttpToolExecutor 响应体上限如 2MB；沙箱输出 I1 的 rune 预算 + **头尾保留**格式 `...(中间 N 字符省略)...`，错误常在尾部）；SSE tool_result 的注释/行为对齐（:293 注释失实修正——前端拿全量的承诺要么实现（事件通道存全文时自然满足）要么修正注释）；事件表只存截断后内容（与 I2 容量配合）。
- **I4（P1-3）first-wins 归并**：toOpenAITools 输出前按 name 去重（先到者胜 + warn 列出被丢弃者）；resolveTools 与中间件追加路径不动（统一出口归并已覆盖）。
- **I5（P1-4）防卡死守卫**：finish_reason=length 时（consumeStream 已捕获）——若本次含未完成 toolCall 则丢弃该调用并注入「输出被截断，请缩减单轮输出/拆分工具调用」system 消息重试（连续 3 次 length 则终止本轮工具循环输出兜底）；空响应 nudge：无工具调用且 content 空 → 注入提示重试，≤2 次；复读检测：连续 2 轮 content 逐字相同且无工具调用 → 注入「检测到重复输出」推进；上下文溢出压缩重试本期不做（登记）。
- **I6（P1-5）压缩修复**：SummarizationMiddleware 调用点移入每轮循环（executeAfter 后或本轮消息回灌后、下轮模型调用前），阈值判断不变；摘要模型 FALLBACK_MODEL 硬编码改为从 modelConfig/网关取（读 BaseContext 可用模型，无则跳过并 warn），失败 warn 不静默吞。
- **I7（P1-6）retry 消费或删**：优先实现——AgentEngine 两处 LLM 调用读 runtimeState("retryConfig")，对瞬时异常（IOException/超时/5xx 类）指数退避重试 maxRetries 次；若现场判断风险大则删除 ModelRetryMiddleware + BaseContext.modelRetryTimes 配置项（消除无效开关），二选一报告注明。
- **I8（P2-1，可选压轴）只读并行**：无副作用白名单（read_file/list_directory/grep/glob/list_kbs/query_kb/list_tables/describe_table）单轮多调用用 CompletableFuture+信号量 8 并发，写类与混合轮保持串行；结果按 toolCallId 对齐回填。时间不足可降级为登记。

## 单元 R — 检索质量包（fastrag-retrieval + application + KnowledgeToolExecutor）

- **R1（P0-6）历史注入最小恢复版**：**禁止改 AgentEngine**。方案 A（优先）：新增 HistoryMiddleware（agent 模块新文件，order 避开 0-11 现有值，取 12），从 BaseContext.runtimeState 取 AppServiceImpl 预填的最近 N 轮历史（AppServiceImpl 的 historyMessages 死代码复活：投影 [{role,content}] 存 runtimeState，预算上限如 6000 字符，超限裁最旧），middleware 把历史作为 system prompt 附加块（「以下是本会话历史，仅供上下文参考」）；方案 B（A 的取数不便时）：AppServiceImpl 侧直接拼入 query 前的上下文块。Deviation 登记：真多轮 messages 注入待引擎侧立项。
- **R2（P0-5）请求级句柄最小版**：KnowledgeToolExecutor.query_kb 输出列表改为「[c1] fileName | 标题/摘要…」编号句柄（模型可见文本不含 UUID），请求级 Map（runtimeState）cN→{kbId,fileId,chunkIndex}；references 投影按 cN 解析回真实 id；模型引用未识别句柄时投影响不到即自然丢弃（fail-closed）。结构化通道保留真实 id（前端用）。
- **R3（P1-9）rerank 质量闸门**：rerank 分阈值（低于阈值丢弃，可配缺省 0.05 级别——现场按分数分布定）；四级降级：阈值过滤→全灭时×0.7 放行→仍空 top-1 保底→模型异常原序返回+诊断标记；passage 拼装：content 前拼 fileName 标题行。
- **R4（P1-10）主路径 RRF 与池深**：hybridSearch 默认路径 weightedFusion→rrfFusionMulti（vectorWeight/bm25Weight 归一为 RRF 路权）；multiChannelRecall 各路池深 max(5×topK,50) 上限 500；补 EmbeddingTopK≥RerankTopK 的入参钳制。
- **R5（P2-5）tie-break**：rrfFusionMulti/deduplicateResults/MMR 的排序比较器追加 fileId→chunkIndex→chunkType 次级键。
- **R6（P2-6，可选压轴）**：图谱通道复用主查询向量（方法参数传入）；suggest 并入 expandQueries 统一实现；query_rule 最小接线（检索主路径正则级改写）；多 KB 请求级 embedding 缓存。时间不足可降级为登记。

## 单元 S — 沙箱+安全+审计（agent sandbox + security + bootstrap/schema + operation listener）

- **S1（P0-7）sys_api_token DDL 入 schema.sql**：对照 schema-h2.sql:757-769 与实体 SysApiToken（含真实列 kb_id NOT NULL/permission default read/revoked 等——以真库 DESCRIBE 为准：id/kb_id/name/token/permission/expires_at/revoked/created_by/created_at/updated_at），在 init-scripts/schema.sql 增补 CREATE TABLE（IF NOT EXISTS 风格与全文件一致 + 索引）。注意真库已存在该表（存量），schema.sql 只服务新装。
- **S2 model_call_log.org_id 列补 schema.sql**（快修遗留；与已入库的 status 去重同文件）。
- **S3（P1-7）活动标记防御**：DockerSandboxAdapter.lastActivity 解析后校验 >now+2min 则 warn 并回退 StartedAt；SandboxWorkspaceLayout.clamp 增保留路径 /workspace/.fastrag_activity（拒写删）；（可选）标记移出 workspace 涉及兼容性，不做，登记。
- **S4（P1-8 最小版）产物回传兜底**：SandboxLifecycleServiceImpl 的 release/身份变更销毁/空闲回收删除前，对 /workspace/output 非空时打包（exec `tar cf - -C /workspace/output .` base64 流回服务端，或逐文件 readFile）落 `{data-dir}/sandbox-outputs/{sessionKey}-{timestamp}/`，log.info 记录路径与文件数；失败仅 warn 不阻断删除。前端展示与消息绑定登记后续。
- **S5（P1-11）审计 outcome 与异步**：SysAuditLogEventListener 改 @Async（确认 @EnableAsync 已存在，AppServiceImpl 有 @Async 先例）；写失败计数器（AtomicLong）+ 周期 warn；status 列语义扩展为 outcome（代码层：拦截/拒绝类事件写 rejected/filtered——敏感词 checkInput 拦截处若已有审计埋点则补 outcome，无则仅把异步化与 outcome 枚举常量落位，敏感词埋点登记）。
- **S6 SchemaInitializer 吞错降级**：Index/Column ensure skipped 的 log.info→log.warn 并带根因异常类名（本次 smoke 实证 kb_parse_strategy.file_id 索引失败被 INFO 掩盖）。

## 统一要求
- 每项先红后绿；收工跑自有模块 `mvn -q test -pl <模块> -am` 全绿，并在报告中给出四节（基线/最终对比、逐项清单、Deviations、未决）。
- 遇到与「文件所有权」冲突的必要性修改：不要做，写入未决问题。
- schema.sql 改动（S1/S2）只许单元 S 做；两单元都涉及的 SharedFile 见所有权表。
