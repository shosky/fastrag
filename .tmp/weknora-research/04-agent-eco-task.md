# 调研任务：WeKnora Agent / 工具 / MCP / IM / 沙箱生态（维度 4/5）

## 背景
为 FastRAG 对标调研。FastRAG 现状锚点：AgentEngine+Middleware 链+State（8.8k LOC），Docker 沙箱实为降级桩，RunEventPublisher 是本地内存 Map（非 Redis pub/sub），技能=Markdown 文件化，MCP 客户端 Streamable HTTP（stdio 仅拒绝桩），外部数据库直接执行 SQL，应用触发器为占位，工作流执行引擎占位（恒返回硬编码完成态）。本维度聚焦 WeKnora 的 **Agent 运行时与工具/集成生态**，检索管线/解析/工程化由其他任务负责。

## 调研对象（只读）
- `D:\Workspace\python\github\WeKnora\internal\agent\`（运行时主战场，优先）
- `internal\mcp\`、`internal\mcpserver\`、`internal\im\`、`internal\browserskill\`、`internal\sandbox\`、`internal\localsandbox\`、`internal\modelcontext\`、`internal\runtime\`
- 根目录 `mcp-server/`、`test_agent_config.sh`、config 中 agent 相关配置

## 必答问题
1. Agent 运行时架构：ReAct/plan-execute/其他？主循环怎么写？工具调用协议？最大步数/超时治理？
2. 流式事件模型：Agent 步骤如何推给前端（SSE 事件类型设计）？对比 FastRAG 的 RunEventPublisher 内存 Map 方案，WeKnora 的事件分发怎么做的（进程内? Redis?）？
3. 工具体系：内置工具清单、工具注册/发现/执行机制、错误处理与重试？
4. MCP 双向：`mcpserver/`（把 WeKnora 知识库暴露给 Cursor/Claude 的 server）与 `internal/mcp/`（作为 client 连外部 MCP）各自实现与协议支持？
5. 技能（Skill）系统：README 提到技能执行——如何定义/加载/执行？
6. IM 渠道：`internal/im/` 如何抽象企业微信/飞书/Slack/Telegram？渠道适配器模式？消息→会话的映射？
7. 沙箱：`sandbox/` 与 `localsandbox/` 的区别、执行什么（代码?）、隔离与安全治理、资源回收？
8. `browserskill/` 浏览器技能是什么？
9. 长期记忆：跨会话记忆的存储/召回/确认机制？
10. Langfuse 追踪埋点在哪里、记录什么（步骤/token/任务流水线）？

## 输出要求（中文 markdown）
- **Agent 运行时地图**（组件→职责）
- **核心机制逐条**（带 `文件:行号` 证据，关键循环可摘代码）
- **优点清单**（机制→证据→为什么好→对 FastRAG 借鉴意义）
- **弱点/风险**
- 禁止臆测；区分"代码证实"与"README 宣称"
