# WeKnora 对标缺口评审报告 — idd-review（2026-10-06/07）

> 五单元并行评审（技能与工具治理 / Agent 运行时 / 沙箱 / 检索管线 / 安全与工程化），对标 `docs/research/weknora-vs-fastrag.md` 与 WeKnora 本地检出。27 项 finding。
> **处置终态（2026-10-07 backlog 批次收口）**：✅ 已修 23 项 ｜ ◐ 部分 1 项 ｜ ⬜ 待立项 3 项（P1-12 可观测性、P2-2 安装进度 SSE、P2-4 沙箱 checkpoint——均需采集端/产品/存储决策）。
> 另：评审后实施期间，启动 smoke 额外发现并修复 1 项 P0 回归（M2-4 引入的构造期 Bean 循环依赖，应用无法启动——lombok.config @Lazy 复制 + AgentRunServiceImpl 断环）。

## P0 — 功能正确性（8/8 已修）

| # | 位置 | 问题 | 状态 |
|---|---|---|---|
| P0-1 | AgentEngine / SandboxExecutionService truncate | UTF-16 substring 切断代理对 | ✅ backlog I1：common OutputBudget（offsetByCodePoints + 头尾保留） |
| P0-2 | AgentRunServiceImpl | cancelRun 只改 DB、终态无条件覆盖 cancelled | ✅ 快修（回写前查库）；引擎内真中断待立项（登记） |
| P0-3 | RunEventPublisher | 事件内存 Map 写而不读、seq 重号、无清理；SSE 只轮询状态 | ✅ backlog I2：StreamManager 接口 + memory/Redis 双实现（RPush/LTrim 500/TTL 24h/粘性降级）+ streamEvents 真接事件通道（afterSeq 增量 + 终态兜底）+ 宽限延迟清理；run 内容不落库另立项 |
| P0-4 | AgentEngine executeSync | 耗尽轮次空答案 success("") | ✅ 快修（stepsExhaustedFallback 对齐流式） |
| P0-5 | KnowledgeToolExecutor / AppServiceImpl | 引用无句柄协议、投影丢 file_id | ✅ 快修（file_id）+ backlog R2（cN 句柄 + runtimeState 映射 + fail-closed，结构化通道保留真实 id） |
| P0-6 | AppServiceImpl:574 | historyMessages 死代码，多轮历史未注入 | ✅ backlog R1 最小恢复版：projectHistory 预填（6000 字符预算裁旧）+ HistoryMiddleware(order=12) system 附加块；真多轮 messages 待引擎立项 |
| P0-7 | schema.sql 缺 sys_api_token | MySQL 新装建不出表 | ✅ backlog S1：CREATE TABLE + 双索引入 schema.sql（真库列结构核实版）；遗留：SysApiToken 实体缺 kbId 字段（IAM 侧待补） |
| P0-8 | ApiTokenAuthFilter | 不读 permission 统一授 kb:manage，read 可自我提权 | ✅ 快修（read→kb:read）；DataScope 全局语义待 capability 立项 |

## P1 — 质量与治理（11/12 已修）

| # | 问题 | 状态 |
|---|---|---|
| P1-1 | 输出预算单层（shell 只保头/SSE 注释失实/事件表全量/HTTP 无上限） | ✅ backlog I3：HttpToolExecutor 2MB 上限、truncateMiddle 头尾保留、SSE 注释修正、事件只存截断后内容 |
| P1-2 | toOpenAITools 不排序，prompt cache 全量 miss | ✅ 快修（按名稳定排序） |
| P1-3 | 同名工具无 first-wins | ✅ backlog I4：统一出口按名归并（先到者胜 + warn） |
| P1-4 | 四类防卡死守卫仅存其一，finishReason 不消费 | ✅ backlog I5：RoundGuard（length 丢弃不完整 toolCall+注入重试、3 次终止；空响应 nudge≤2；复读有界推进）；溢出压缩重试登记不做 |
| P1-5 | 压缩只在循环外调一次从不生效；摘要模型硬编码必 404 | ✅ backlog I6：beforeRound 钩子移入每轮；模型 modelConfig→context 回退、失败 warn |
| P1-6 | retryConfig 写而不读（无效配置开关） | ✅ backlog I7：引擎消费实现瞬时异常指数退避（含 fullAnswer 回滚防重复） |
| P1-7 | 沙箱活动标记无未来时间戳防御且在工作区内 | ✅ backlog S3：>now+2min 回退 StartedAt；.fastrag_activity 列保留路径拒写 |
| P1-8 | /workspace/output 只进不出，产物随回收销毁 | ✅ backlog S4 最小版：删除/回收/身份变更前 tar 打包落 {data-dir}/sandbox-outputs/（失败不阻断，8M 上限）；前端展示/消息绑定待后续 |
| P1-9 | rerank 无阈值降级链、无 passage 工程 | ✅ backlog R3：阈值→×0.7→top-1→原序四级链 + 【fileName】passage + [RerankGate] 诊断 |
| P1-10 | 默认 hybrid 加权融合；multiChannel 池深=topK | ✅ backlog R4：主路径 RRF 路权变体 + 池深 max(5×topK,50) 上限 500 + EmbeddingTopK≥RerankTopK 钳制 |
| P1-11 | 审计两态、同步监听器吞错 | ✅ backlog S5：@Async + 失败计数周期告警 + AuditOutcome 枚举；敏感词拦截埋点待立项 |
| P1-12 | 可观测性零落地；actuator permitAll | ⬜ 待立项（需 OTLP 采集端部署决策；actuator 收敛需核对 compose healthcheck 依赖） |

## P2 — 择机（4/7 已修）

| # | 问题 | 状态 |
|---|---|---|
| P2-1 | 工具串行执行无只读并发 | ✅ backlog I8：8 工具只读白名单、信号量 8 并发、写类屏障、toolCallId 对齐回填 |
| P2-2 | 技能导入同步单步无进度 SSE/transcript | ⬜ 待立项（需前端契约） |
| P2-3 | writeFile 绕过活动标记 touch | ✅ 快修（mkdir+touch 复合命令） |
| P2-4 | 沙箱无 checkpoint | ⬜ 待立项（镜像 git 能力/存储策略待定） |
| P2-5 | 融合/去重无确定性 tie-break | ✅ backlog R5：fileId→chunkIndex→chunkType 次级键接入三处排序 |
| P2-6 | 查询改写双 stub + 重复嵌入 + 多 KB 无共享向量 | ✅ backlog R6：suggest 并入 expandQueries、query_rule 正则级接线、图谱通道复用主向量、(model,query) 60s LRU 共享 |
| P2-7 | 权限执行点/装配层零测试；parity 仅 4 表；Flyway 未引入 | ◐ security/bootstrap/operation 已建首批测试（4/6/5 用例）+ SchemaSqlSmokeTest；parity 全表化与 Flyway 待立项 |

## 已确认闭合 / 无需动作

技能描述截断、@提及登记在案、Schema 校验+JSON 修复、per-config sweep 与确定性容器名、特权安装模式与脚本物化（裁决性 OUT-of-scope）、MMR（默认关）、QA 召回准入（ADR-0005）、闲聊零检索（agentic 门控）、邻块窗口扩展。

## 实施批次登记

- **快修批次（2026-10-06）**：7 项（QF-1~7 → P0-5 止血/P0-9/P0-8/P0-4/P0-2/P1-2/P2-3），全仓绿。见会话档案 `fastrag/.tmp/idd-quickfix-task.md`。
- **backlog 批次（2026-10-07）**：三路并行 20 项（I1~I8 / R1~R6 / S1~S6）全部完成；另修复评审期间启动 smoke 发现的构造期循环依赖（lombok.config + AgentRunServiceImpl @Lazy）。测试终态：agent 244+11skip / tools 246 / retrieval 48 / application 85 / common 10 / security 4 / bootstrap 6 / operation 5。任务书：`fastrag/.tmp/idd-backlog-task.md`。

## 遗留决策清单（需用户/产品拍板，未擅动）

1. **宪法级**：业务错误是否从「HTTP 200 + body.code」改为真实状态码（research doc §6.3；涉及全前端契约，建议独立评审）。
2. ~~**@提及技能**~~ **已实施（2026-10-07 特性批次）**：请求态 skillNames + SkillsMiddleware must_use 注入 + 前端 @ 选择器（见 docs/plan/skill-feature-batch.md）。附带拍板：skill slug 强制全局唯一（uk_slug）、api-token 非 KB 数据语义维持现状（capability 立项首项）。
3. **可观测性 P1-12**：需定 OTLP 采集端（Langfuse？）与默认开关策略。
4. **Flyway 版本化迁移**（P2-7 深化）与 parity 全表化：迁移纪律项目。
5. **沙箱 checkpoint（P2-4）**：镜像 git 能力与存储成本。
6. **40+ 存量特性债务**：见 feature-map 各链登记，按链立项。
