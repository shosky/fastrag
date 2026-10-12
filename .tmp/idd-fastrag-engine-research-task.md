# 调研任务：FastRAG AgentEngine 引擎处理用户问题的完整执行链（引擎级深挖）

## 背景
对比 WeKnora agent 引擎（其侧已有深度报告：ReAct 循环、20 轮上限、四类防卡死守卫、上下文压缩+Checkpoint、模型句柄注册表、工具注册表 first-wins/输出预算 16KB/JSON 修复/只读并行白名单 8、审批门、Langfuse 分层 span）与 FastRAG 自己的 AgentEngine，需要先把**我方引擎的真实执行链**摸到同等深度。只读，不改任何文件。

## 调研对象
`fastrag-backend/fastrag-modules/fastrag-agent/src/main/java/com/fastrag/module/agent/`（全部子目录）+ 相关配置。核心入口：`executor/AgentEngine.executeStream(BaseContext, SseEmitter, ModelConfig, AgentRun, StreamCompleteCallback)`（AgentEngine.java:112 附近）。

## 必答问题（按处理一次用户提问的生命周期）
1. **接收与上下文构建**：executeStream 拿到 BaseContext 后先做什么？历史消息/记忆从哪来（是编排层塞还是引擎拉）？systemPrompt 如何生效？
2. **主循环形态**：是不是 ReAct？循环上限（maxSteps/maxTurns 语义区别）？每轮做什么（LLM 调用→解析→工具→观察）？LLM 调用用什么客户端（fastrag-ai LlmService? 流式怎么接）？
3. **工具执行**：tool_call 怎么产生（模型输出格式？函数调用还是文本协议？）；ToolRegistry.resolveTools 如何把 ID 解析成可执行工具；执行超时/失败怎么处理；工具结果如何回填上下文；并行还是串行；MCP 工具怎么执行。
4. **上下文/记忆治理**：summaryThreshold=8000 触发什么（摘要压缩？谁执行、用什么模型、压缩后放哪）；runtimeState 里有什么；有没有像 WeKnora 的 Checkpoint/守卫（空响应重试、复读检测、截断放弃）？
5. **事件输出**：事件发射点清单（init/message/thinking/tool_call/tool_result/agent_state/end/error 各在什么条件发）；agent_state 事件内容是什么（为何前端无消费方）；end 事件的 content/finish 语义。
6. **中间件链**：AgentMiddlewareChain 有哪些中间件、各自职责、执行时机（前置/后置/环绕）；@Deprecated 旧执行器（AgentExecutor?）与新引擎关系。
7. **状态与持久化**：AgentRun/fakeRun 的使用；RunEventPublisher 现状（内存 Map?）；state/ 包是什么；agent/agent_run 表谁写。
8. **异常与取消**：LLM 失败/超时/工具异常/用户断开 SSE 各走什么路径；有没有取消能力（cancel 标志/中断传播）；部分答案怎么交回。
9. **安全与预算**：工具白名单/权限校验有没有；token 预算控制；maxTokens 用在哪层。
10. **测试与已知债务**：8 个测试覆盖什么；源码里的 TODO/FIXME 清单；哪些是占位实现。

## 输出（中文）
- **执行链流程图**（文字版，标注 file:line）
- **逐问题回答**（每条带 file:line 证据，关键代码摘 3-5 行）
- **能力盘点表**：能力 → 有/无/占位 → 证据
- **与 WeKnora 对比用的差距线索**：你视角看哪些点明显弱于"ReAct+守卫+压缩+句柄"那一套（不用读 WeKnora 代码，按上述背景描述对照）
- 禁止臆测；读不到的标 GAP
你的最终回复就是调研报告全文。
