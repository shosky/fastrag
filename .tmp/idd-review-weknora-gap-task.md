# idd-review 任务书 — 对标 WeKnora 的优化缺口评审（2026-10-06）

> 背景：技能域修复（skill-runtime-remediation M1~M3）+ 沙箱实装（09-28）+ new-conversation/qa-generation 已全部入库（commit 5f678a0..709562c）。本轮要回答：**以 WeKnora 为对标，fastrag-backend 还有哪些值得优化的点**。
> 基准证据（已存在，勿重复调研，可抽查核实）：
> ① `fastrag-backend/docs/research/weknora-vs-fastrag.md`（2026-09-25 全域对比 + P0/P1/P2 借鉴路线）
> ② `fastrag/.tmp/idd-skill-runtime-grill-decisions.md`（技能域缺陷登记 D1~D14 + WeKnora 技能模型 file:line）
> ③ WeKnora 本地检出：`D:\Workspace\python\github\WeKnora`（抽查用，勿全仓扫描）
> 评审对象：fastrag-backend 当前工作树（=已提交状态）。
> 输出：四要素段落（[P级] [文件:行] / Problem / Consequence / Action + 尾部标签），开头给 3 行小结。P0=功能正确性风险；P1=本轮该修；P2=择机。**只报对标后仍真实存在的缺口**——凡 fastrag 已落地对标的，明确说"已闭合"不占段落（在小结里计数即可）。不要修复任何东西。

## 五个评审单元的基准清单

### 单元 A：技能与工具治理（对标 WeKnora internal/agent/skills + tools 治理）
fastrag 现状锚点：`fastrag-modules/fastrag-tools`（registry/ToolRegistry、executor/*、skill/*）、`fastrag-modules/fastrag-agent/.../middleware/SkillsMiddleware.java`。
WeKnora 对标点（核实 fastrag 是否已有，缺则立项）：
1. 工具输出预算：WeKnora act.go 输出预算 16KB rune 计数、shell 输出头尾保留、JSON 截断判定则拒执行——fastrag AgentEngine 工具结果仅"截 5000 字符回灌"（agent.md :65），有无 rune 安全截断/超预算事件？
2. 工具定义按名排序命中厂商 prompt cache（WeKnora registry.go:120-127）——fastrag toOpenAITools 是否排序？
3. first-wins 防同名工具劫持（WeKnora registry.go:54-57）——fastrag ToolRegistry/中间件追加工具时同名谁赢？
4. JSON Schema 校验失败返回格式化错误省一轮执行、参数类型矫正（WeKnora act.go:420-436）——fastrag 有 JsonSchemaValidator + tryRepairJsonArguments（agent.md :65），缺什么？
5. 技能元数据注入格式（WeKnora XML `<skill name path>` + description 截 600）：fastrag 已截 600（M2-6）✓，但注入为纯文本列表——是否值得对齐 XML/结构化（低优）。
6. @提及 pinned + `<must_use>`：fastrag 已裁决后置（OUT of scope）——仅确认登记在案即可。
7. 技能安装进度 SSE / 安装 transcript（WeKnora install-events/transcript）：fastrag 导入是同步单步——确认差距并给建议。
8. 并行工具调用 + 读写屏障（WeKnora act.go:292-333 只读白名单并发上限 8）：fastrag 串行执行工具调用——确认差距。

### 单元 B：Agent 运行时治理（对标 WeKnora internal/agent engine.go/compaction/steer）
fastrag 现状锚点：`executor/AgentEngine.java`、`event/RunEventPublisher.java`、`middleware/*`、`service/impl/AgentRunServiceImpl.java`。
1. 四类防卡死守卫（空响应 nudge≤2 / 复读检测 / completion-token 截断 3 轮放弃 / 上下文溢出一次压缩重试，WeKnora const.go:40-64）：fastrag 只有失败计数≥3 注入停止消息——逐项核对缺哪些。
2. 上下文压缩：fastrag SummarizationMiddleware（估算超 8000 压缩中段）vs WeKnora 压缩停止条件 + checkpoint 续起 + token 估算自校准。
3. steer 运行中插话（WeKnora agent/steer.go）：fastrag 取消无信号传播、无插话通道（agent.md AgentRunGateway Deviations②③）。
4. 事件可重放：RunEventPublisher 本地内存 Map（写而不读）+ streamEvents 是 DB 轮询 2s——WeKnora StreamManager Redis append-only + offset 续读（research doc §2.4 已给方案）。fastrag 多副本部署下 SSE 断线不可恢复——确认现状与改造成本。
5. executeSync 达最大轮次终态=completed("")（M2 切引擎后的行为注记）——是否应视为 failed/部分成功，对齐旧语义或显式产品化。
6. ModelRetryMiddleware 写而不读（agent.md Deviations②）——引擎未消费 retryConfig。

### 单元 C：沙箱（对标 WeKnora internal/sandbox + localsandbox）
fastrag 现状锚点：`fastrag-modules/fastrag-agent/.../sandbox/**`（SandboxExecutionService、provider/docker/DockerSandboxAdapter、SandboxLifecycleServiceImpl）。
1. 会话 checkpoint：WeKnora 每轮后 /workspace git commit 支持 fork 回滚——fastrag 无 checkpoint（确认）。
2. 空闲回收防伪造：WeKnora docker_idle_sweeper 拒绝 mtime 在未来的活动标记（:177-207）——fastrag lastActivity 用 stat 标记文件，有无未来时间戳防御？
3. 产物抽取：WeKnora /workspace/output 自动抽取绑定 MessageArtifacts——fastrag stageAttachments 只进不出？产出物如何回传（确认差距，牵涉前端）。
4. 特权安装模式（WeKnora EnableSkillInstallMode 仅授予安装 agent root）：fastrag 无运行时安装（裁决不采用 WeKnora 安装 agent）——确认"技能脚本执行"整体仍是 OUT of scope 登记。
5. per-config sweep、确定性容器名、防误删兄弟配置 daemon：agent.md 记载已闭合（2026-09-29 Windows 验收）——核实代码与记载一致即可。
6. 技能脚本执行依赖全目录物化：fastrag 物化仅 SKILL.md——技能含 scripts/ 时沙箱内不可执行（与裁决一致，确认登记）。

### 单元 D：检索管线（对标 research doc §4.2 的 11 项可迁移）
fastrag 现状锚点：`fastrag-modules/fastrag-retrieval/.../RetrievalServiceImpl.java`、`QueryEnhanceServiceImpl`、引用落库（AppConversationMessage.references）。
逐项核对 research doc §4.2 表格（RRF 融合 / 过检索池 max(5×TopK,50) / EmbeddingTopK≥RerankTopK / rerank 四级降级 / passage 工程 / MMR / 短文本邻块扩展 / 查询改写+意图分类 / 引用私有句柄 cN / 历史引用折扣 / 确定性 tie-break / 多 KB 共享查询向量）：哪些已落地（qa-generation 后可能改了部分）、哪些仍是缺口。已知未动项大概率仍是缺口——核实后按收益排序输出。

### 单元 E：安全、权限与工程化（对标 WeKnora RBAC/审计/迁移纪律/OTel）
fastrag 现状锚点：`fastrag-security/**`、`fastrag-common/**`、`SchemaInitializer`、`SkillDdlParityTest`。
1. 错误语义三分（查无 404 / 无权 403 / 依赖故障 503）：fastrag BusinessException 体系 HTTP 200+body.code（宪法）——WeKnora 真实状态码议题是宪法级，research doc §6.3 建议走 IDD 决议——确认仍是待决议事项即可。
2. API Token capability 粒度 + 默认拒绝 + 启动自检（WeKnora ~24 capability、路由策略表启动 panic）：fastrag sys_api_token 现状核对。
3. 审计 append-only schema（WeKnora audit_log 单调 ID 游标、六种 outcome）：fastrag 三日志表现状。
4. DDL 纪律：SkillDdlParityTest 只护 skill 四表——其余表（mcp_service 等仅 SchemaInitializer 列）漂移面仍在（M3 报告未决问题 2）；Flyway/版本化迁移未引入。
5. 可观测性：WeKnora OTel→Langfuse opt-in 全链路 span；fastrag 零链路追踪（research doc P1-9）——最小路径建议（chat turn=root trace）。
6. 测试分布：fastrag 中间件/路由/装配层测试薄弱（WeKnora 有 router/container 测试）——现状核对（agent 模块 160 用例后是否改善）。
7. 敏感词/术语/query_rule stub 债务（research doc #14 platform-config）——若仍 stub，列缺口。

## 统一要求
- 每单元最多 8 条 finding，挑真实有行动价值的；已闭合的对标项在小结里给一行计数（如"已闭合 3/8"）。
- 语言：中文。证据用当前工作树 file:line（行号要实测，不要引用旧报告的行号）。
- WeKnora 侧引用沿用任务书给的 file:line（如需抽查可读 D:\Workspace\python\github\WeKnora 对应文件）。
