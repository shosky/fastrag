# 应用中心（app-center） — 测试设计

> onboard 模式：本文件是测试**设计**，不写测试代码，红绿循环未执行。切片按 contract `## app-center` 四接口分组；mock 仅出现在真缝/系统边界（RetrievalService、SensitiveWordService、AgentEngine、数据库 Mapper），禁 mock 内部模块。

## 1. 切片规划

| # | 切片 | 对应契约接口 | AFK/HITL | 说明 |
|---|---|---|---|---|
| S0 | SSE 对话端到端人工验收（真实模型流） | AppDialogOrchestrator | **HITL** | 事件序列观感、replace 气泡替换、sources 卡片渲染需人眼判定；自动化仅能断言事件名与标记位 |
| S1 | SSE 会话状态机（重点用例族 A） | AppDialogOrchestrator | AFK | blocked/greeting 短路、首条回填、计数记账、超时/异常路径——全部可经 mock 缝后断言库行与事件序列 |
| S2 | 绑定装配与检索透传一致性（重点用例族 B） | AppDialogOrchestrator + AppBindingRegistry | AFK | enabled=1 才入上下文、topK=5 + KB 配置透传、单库失败降级 |
| S3 | 模型路由与安全双接入点 | AppDialogOrchestrator | AFK | code 解析四级回退；输入拦截/输出替换 × 开关两态 |
| S4 | 会话账本归属与级联 | AppConversationLedger | AFK | 归属三分支（属主/他人/API Token）、软删过滤、级联物理删 |
| S5 | 配置面存储语义 | AppConfigStore | AFK | upsert 缺省六元组、CSV↔JSON 双形状、advancedOptions 段共存、导入导出对称性缺口 |
| S6 | 五类绑定注册表 | AppBindingRegistry | AFK | 兜底值、kb 唯一约束、kb 无 update、解绑物理删 |
| S7 | 台账 CRUD 与数据范围 | AppDialogOrchestrator（经 HTTP 层） | AFK | DataScope 三分支、权限注解、@Deprecated run 通道固化断言（已有 TC-MOCK-001） |

现有测试（implement §3 详述）覆盖 S5/S6 的一部分与源码级结构断言；S1~S4 为最大缺口。

## 2. 测试用例（逐接口）

### AppDialogOrchestrator（contract: app-center.AppDialogOrchestrator）

| 测试名 | Given | When | Then（断言） | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [拦截] 输入命中黑名单词条时产出阻断事件且零副作用 | 应用可见、策略开启、checkInput 命中 | runStream(query=命中词) | 事件序列=message(blocked:true)+end；app_conversation_message 无新行；AgentEngine 未被调用 | REQ-E03 | SensitiveWordService |
| [拦截] 安全开关关闭时同输入放行 | safetyEnabled=0（或无策略行且字段空仍视为开——两态各一测） | runStream | 无 blocked 事件；用户消息落库 | REQ-E03/REQ-D01 | SensitiveWordService |
| [短路] 首条消息为打招呼且配置开场白时返回开场白 | 新会话、basicConfig.greeting 非空、query=「你好」 | runStream | message(isGreeting:true)+end；助手消息=开场白文案；AgentEngine 未被调用 | REQ-E05 | 无（纯短路） |
| [短路] 非首条消息打招呼不触发开场白 | 会话 messageCount>0 | runStream(query=「你好」) | 走完整编排路径 | REQ-E05 | AgentEngine/RetrievalService |
| [状态机] 首条消息回填 firstQuestion 与 title 且分别截断 200/30 字 | 新会话 | runStream(query=201 字串) | conv.firstQuestion=前 200 字、conv.title=前 30 字+"..."；user 消息行先于引擎调用存在 | REQ-E04 | AgentEngine |
| [状态机] 正常对话产出 sources 前置事件与成对记账 | 绑定 1 个 KB、检索有结果 | runStream | 事件序=sources→…→end；库中 user+assistant 两行；conv.messageCount=2；answerSummary=回答前 200 字 | REQ-E08/E12 | RetrievalService+AgentEngine |
| [状态机] 输出命中替换词条时补发 replace 且落库同文 | filterOutput 返回替换全文 | runStream | 引擎 end 前出现 message(replace:true)；assistant.content=替换后全文 | REQ-E11 | SensitiveWordService+AgentEngine |
| [超时] SSE 超时保留部分答案 | 引擎流中途超时、fullAnswer 非空 | 触发 onTimeout | assistant 落库=过滤后部分答案 | REQ-E12 | AgentEngine+时间 |
| [异常] 编排异常时客户端收到 error 事件 | 引擎抛 RuntimeException | runStream | 事件序含 error{message 含「对话执行失败」}且流 complete；无 assistant 行 | REQ-E13 | AgentEngine |
| [遗留] run 旧通道纯关键词匹配无模型调用 | 任意应用 | POST /{id}/run | 返回 {sessionId,answer}；无库写入；answer 来自 QA_RULES 或兜底文案（源码级已有 TC-MOCK-001 固化） | REQ-A07 | 无 |

### AppConversationLedger（contract: app-center.AppConversationLedger）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [归属] 非属主访问会话被拒且 API Token 放行 | 会话属 userA | userB 与 apiToken 分别访问 getSessionMessages | userB=forbidden；apiToken=正常返回 | REQ-E01/E15 | 登录态构造 |
| [归属] 消息级操作校验消息→会话→属主链 | userB 持有 userA 会话中的 messageId | deleteMessage/feedbackMessage/updateMessage | 三者均 forbidden | REQ-E15 | 登录态构造 |
| [级联] 删除会话连带物理删除其消息 | 会话含 2 消息 | deleteSession | conv 行与 2 消息行均消失 | REQ-E14 | Mapper（真库或 mock 均可） |
| [软删] 软删消息从历史消失但行保留 | 会话含 3 消息 | deleteMessage(msg2) 后 getSessionMessages | 返回 2 条且不含 msg2；msg2 行 deletedAt 非空 | REQ-E15 | Mapper |
| [反馈] feedback 空串清除点赞 | 消息 feedback=like | feedbackMessage("") | feedback 列为空串 | REQ-E15 | Mapper |
| [列表] 会话列表按 updatedAt 倒序且仅含本人 | userA 有 3 会话 | listSessions | 顺序断言；userB 调用返回其自己（空）列表 | REQ-E14 | Mapper |

### AppConfigStore（contract: app-center.AppConfigStore）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [缺省] 首次单项保存填充缺省六元组 | 无 app_config 行 | saveMaxSteps(20) | insert 行含 maxSteps=20 且 maxTurns=10/retryTimes=2/summaryThreshold=8000/maxTokens=2048/temperature=0.70 | REQ-B02 | Mapper（现有 AppConfigIntegrationTest.testSaveMaxSteps_CreateNew 已覆盖此形态） |
| [upsert] 二次保存走 update 且不新建行 | 已有行 | savePrompt 再次调用 | updateById 调用、insert 零调用 | REQ-B01/B02 | Mapper（现有覆盖） |
| [策略缺省] 无策略记录时保存兜底 safetyEnabled=1 | 无 app_global_policy 行 | saveUnmatchedConfig | insert 行 safetyEnabled=1 | REQ-D01/D02 | Mapper |
| [双形状] allowedTables 进出对称 | CSV「t1,t2」 | bindDb 后 listDbBindings | 库内为 JSON 数组串、读出还原「t1,t2」；非法 JSON 原样透传 | REQ-C01 | Mapper |
| [段共存] 保存 workflow 段不冲掉 debug 段 | advancedOptions 含 debug 段 | saveWorkflowConfig | 更新后 JSON 同时含 workflow 与 debug | REQ-B06 | Mapper |
| [导入缺口] 导入 skills/tools/mcps/dbs 被忽略 | data 含全部键 | importConfig | 仅六组写方法被调用 | REQ-B05 | Mapper |
| [吞噬] 绑定 list 异常返回空列表而非报错 | skillBindMapper 抛异常 | listSkillBindings | 返回空 List 不抛（现有行为固化） | REQ-C01（缺口登记） | Mapper |
| [记录] 对话记录筛选分页四维度 | keyword/rating/startDate/endDate | listConversations | SQL 条件拼装正确；返回 {total,items,page,size}（偏离 PageResult 形状如实断言） | REQ-H01 | Mapper |

### AppBindingRegistry（contract: app-center.AppBindingRegistry）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [装配] 仅 enabled=1 绑定进入执行上下文 | kb/tool/skill/mcp/db 各 2 行（各禁用 1） | runStream 内部装配点 | BaseContext 五集合各恰含启用的那个 ID | REQ-C02 | RetrievalService+AgentEngine |
| [透传] KB 保存配置透传仅覆盖 topK | KB.retrievalConfig 含 contextAssemblyStrategy=parent_chunk、enableRerank=true | runStream | 捕获 RetrievalRequest：topK=5、两透传字段原值、其余字段默认 | REQ-C03 | RetrievalService |
| [降级] 单库检索失败不中断 | 2 个 KB 绑定、第一个 search 抛异常 | runStream | sources 仅含第二库结果；对话正常完成 | REQ-C04 | RetrievalService |
| [唯一] 重复绑定同 KB 被数据库拒绝 | (app,kb) 已存在 | bindKb 同对 | DB 异常上抛（应用层不吞） | REQ-C01 | 真库（H2） |
| [兜底] bind 填充 enabled/priority 缺省 | 各类绑定最小入参 | bindX | enabled=1；kb 另 priority=0 | REQ-C01 | Mapper |

## 3. 追溯（抽样）

| 测试 | 契约接口 | Design 模块 | Spec REQ |
|---|---|---|---|
| [拦截] 输入命中黑名单词条… | AppDialogOrchestrator.E_INPUT_BLOCKED | AppDialogOrchestrator（缝 3） | REQ-E03 |
| [装配] 仅 enabled=1… | AppBindingRegistry + AppDialogOrchestrator Post | AppBindingRegistry（缝 5） | REQ-C02 |
| [缺省] 首次单项保存… | AppConfigStore.saveMaxSteps | AppConfigFacade | REQ-B02 |
| [归属] 非属主访问会话被拒… | AppConversationLedger.E_CONVERSATION_NOT_OWNED | AppConversationLedger | REQ-E01/E15 |
| [降级] 单库检索失败不中断 | AppDialogOrchestrator 非错误约定 | AppDialogOrchestrator（缝 2） | REQ-C04 |

## 4. 待实施说明

- 红绿循环**未执行**（本任务不改源码）。执行时从 Tracer Bullet 起步：建议首条 =「[状态机] 正常对话产出 sources 前置事件与成对记账」（贯穿缝 1/2/3 与账本，是全链骨架）；SSE 事件断言用 SseEmitter 包装捕获或在 MockMvc 下异步收集。
- 事件序列断言的关键：AgentEngine mock 需按真实词汇（init/message/thinking/tool_call/tool_result/agent_state/end/error，见 contract `## agent`）回放，避免测试钉死在引擎实现细节。
- HITL S0 不进 CI；其断言清单（blocked 文案、replace 气泡、sources 卡片）挂 issue 跟踪。
- 现有 AppModuleValidationTest.TC-MOCK-001/002/003 与 TC-REG-004 是源码级固化断言，重构编排器时必须保持绿色。
