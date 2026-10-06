# WeKnora 对标缺口评审报告 — idd-review（2026-10-06）

> 五单元并行评审（技能与工具治理 / Agent 运行时 / 沙箱 / 检索管线 / 安全与工程化），对标 `docs/research/weknora-vs-fastrag.md` 与 WeKnora 本地检出。27 项 finding：P0×8（合并后）/ P1×12 / P2×7。
> **快修批次（同日实施）**：下表 P0-1/P0-2/P0-4/P0-5/P0-7~P0-9 与 P1-2、P2-3 共 8 项已实施（见文末登记），其余为待立项 backlog。

## P0 — 功能正确性

| # | 位置 | 问题 | 后果 | 状态 |
|---|---|---|---|---|
| P0-1 | AgentEngine.java:905 / SandboxExecutionService.java:440 | 工具输出截断按 UTF-16 substring，代理对中间切断 | 序列化非法字符，部分厂商 API 400，该轮结果丢失 | ✅ 快修（rune 安全） |
| P0-2 | AgentRunServiceImpl.java:120-130,208 | cancelRun 只改 DB；异步终态无条件覆盖 cancelled；引擎无取消检查点 | 取消后继续烧 token，状态被改回成功 | ✅ 快修（回写前查库保护；引擎内真中断待立项） |
| P0-3 | RunEventPublisher.java:44-81 | 事件表内存 Map、seq 同毫秒重号、无上限无清理；SSE 只轮询 run 表不读事件 | 前端全程黑盒、断线不可续传、多副本失效（=research P0-3） | ⬜ 立项：StreamManager |
| P0-4 | AgentEngine.java:443,519 | executeSync 耗尽轮次且空答案返回 success("") | 成功+空内容入库，监控失真 | ✅ 快修（兜底文案对齐流式分支） |
| P0-5 | KnowledgeToolExecutor.java:98 / AppServiceImpl.java:713-746 | 引用无请求级句柄协议，UUID 进 prompt；投影丢 file_id | 同 KB 多文件引用溯源歧义、无法精确回跳 | ✅ 快修（file_id 已补）；句柄协议待立项 |
| P0-6 | AppServiceImpl.java:574-580 | historyMessages 死代码，多轮历史未注入引擎，memoryRounds 失效 | 应用对话每轮失忆（用户可直接感知） | ⬜ 立项首位：历史注入 |
| P0-7 | schema-h2.sql:757 vs schema.sql | sys_api_token 不在 MySQL 建库链路 | MySQL 部署 API Token 功能报表不存在 | ✅ 快修（DDL 补入+全表 parity 方向） |
| P0-8 | ApiTokenAuthFilter.java:74-88 | 不读 permission 字段统一授 kb:manage | read token 可自我提权、管理全部非 KB 数据 | ✅ 快修（read→kb:read） |

## P1 — 本轮该修

| # | 位置 | 问题 | 状态 |
|---|---|---|---|
| P1-1 | AgentEngine.java:282,835 | 输出预算单层：shell 只保头、SSE 截 5000 与注释矛盾、全量进内存事件、HTTP 无大小限制 | ⬜ 随 StreamManager/预算分层立项 |
| P1-2 | ToolRegistry.java:121-144 | toOpenAITools 不排序，prompt cache 每轮全量 miss | ✅ 快修（按名稳定排序） |
| P1-3 | ToolRegistry.java:26-44 + 六中间件 | 同名工具无 first-wins，可被抢占执行路径 | ⬜ |
| P1-4 | AgentEngine.java:180,461,543 | 四类防卡死守卫仅存其一；finishReason 已捕获不消费 | ⬜ 随 Agent 治理迭代 |
| P1-5 | SummarizationMiddleware.java:36-104 | 压缩只在循环外调一次从不生效；摘要模型硬编码 gpt-4o-mini 必 404 且静默吞 | ⬜ |
| P1-6 | ModelRetryMiddleware.java:41 | retryConfig 写而不读，管理端配置是无效开关 | ⬜ |
| P1-7 | DockerSandboxAdapter.java:498-521 | 活动标记在工作区内且无未来时间戳防御，可被模型伪造免回收 | ⬜（建议随沙箱迭代） |
| P1-8 | SandboxWorkspaceLayout.java:22 | /workspace/output 只进不出，产物随回收销毁 | ⬜ 立项：产物回传 |
| P1-9 | RetrievalServiceImpl.java:1139-1192 | rerank 无阈值降级链、无 passage 工程 | ⬜ 随检索质量包 |
| P1-10 | RetrievalServiceImpl.java:674,687 | 默认 hybrid 仍加权融合；multiChannel 池深=topK | ⬜ 随检索质量包 |
| P1-11 | 审计三日志 | status 两态、监听器同步吞错，安全事件不留痕 | ⬜ |
| P1-12 | 全仓 pom | 可观测性零落地；/actuator/** permitAll | ⬜ 立项：micrometer-tracing opt-in |

## P2 — 择机

| # | 问题 | 状态 |
|---|---|---|
| P2-1 | 工具串行执行，无只读白名单并发（WeKnora 上限 8） | ⬜ |
| P2-2 | 技能导入同步单步，无进度 SSE/安装 transcript | ⬜ |
| P2-3 | writeFile 走 tar 绕过活动标记 touch，纯写会话被误回收 | ✅ 快修（mkdir+touch 复合命令） |
| P2-4 | 沙箱无 checkpoint（轮末 git commit），误操作不可撤销 | ⬜ |
| P2-5 | 融合/去重排序无确定性 tie-break，评测指标被抖动污染 | ⬜ |
| P2-6 | 查询改写双 stub（suggest/applyQueryRules）+ 图谱通道重复嵌入 + 多 KB 无共享向量 | ⬜ |
| P2-7 | 权限执行点与装配层零测试（本批已建 security/bootstrap 首批测试）；parity 测试仅 4 表；Flyway 未引入 | ◐ 部分缓解 |

## 已确认闭合 / 无需动作

技能描述截断、@提及登记在案、Schema 校验+JSON 修复、per-config sweep 与确定性容器名、特权安装模式与脚本物化（裁决性 OUT-of-scope）、MMR（默认关）、QA 召回准入（ADR-0005）、闲聊零检索（agentic 门控）、邻块窗口扩展、宪法级 HTTP 状态码议题维持待决议。

## 快修批次实施登记（2026-10-06）

7 项（QF-1~QF-7 对应 P0-5/P0-9/P0-8/P0-4/P0-2/P1-2/P2-3）经 /idd-tdd 先红后绿完成；全仓 16 模块 mvn test BUILD SUCCESS（agent 167 / tools 229 / application 78 / security 首建 4 用例 / bootstrap 首建 1 用例）。改动 13 个已跟踪文件 + 5 个新文件。Deviation：DataScope api-token 全局语义、引擎内真中断、model_call_log.org_id 列缺口——均登记待后续。
