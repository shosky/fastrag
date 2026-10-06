# 新建对话与会话中心（new-conversation） — 测试设计

> 前瞻模式：本文件是测试**设计**，不写测试代码，红绿循环未执行（待 implement 阶段，代码尚不存在）。切片按 contract `## new-conversation` 三接口 + 跨链延伸触点分组；mock 仅出现在真缝/系统边界（ExecutionEngine、RetrievalSearchEngine、SensitiveWordService、KbAccessChecker、数据库 Mapper、LlmService），禁 mock 内部模块。
> 测试命名：`[域动作] under 条件 produces 结果`，域词取 CONTEXT.md（新建对话/会话请求态/知识库直聊/会话中心/消息引用）。

## 1. 切片规划

| # | 切片 | 对应契约接口 | AFK/HITL | 说明 |
|---|---|---|---|---|
| S0 | 直聊端到端人工验收（真实模型流） | SessionCenter 全链 | **HITL** | 范围选择生效观感、预设 chip 移除转直聊、引用气泡渲染、停止即时性需人眼判定；自动化仅断言事件名/标记位/库行 |
| S1 | 会话预设解析矩阵（深核心，最大风险面） | DialogPresetResolver | AFK | 三源矩阵、D02 结构化互斥、C05 五级回退、B03 两来源 ACL、死字段不入配置、存量降级 |
| S2 | 会话中心生命周期 | SessionCenter | AFK | 创建可空、列表三排序要素+来源标记+PageResult、清空级联语义、起标题降级、归属守卫 |
| S3 | 新通道流式编排 + 固化 + 引用 | 编排器跨链延伸（executeDialog 双入口） | AFK | 空 KB 零检索、每次都检索、事件词汇封闭、三落库点固化、引用投影两通道、直聊会话主键定位 |
| S4 | 停止注册表并发协议 | StopRegistry | AFK | cancel 幂等、覆盖语义、unregister 清理、双落库恰一次 |
| S5 | 请求态形状属主 | SessionRequestStateStore | AFK | NULL→空、坏 JSON 容错、全形状往返对称、死字段保留 |
| S6 | DDL 一致性与定位键迁移 | 账本跨链延伸 | AFK | schema.sql↔SchemaInitializer 对账（4 列+索引）；app_id NULL 行全链可行 |
| S7 | 旧通道等价性回归（G01） | 编排器扩展（PRESET_ONLY 哨兵） | AFK | 旧壳产物与现行配置读取 diff 为空；既有 app-center 测试保持绿 |

## 2. 测试用例（逐接口）

### DialogPresetResolver（contract: new-conversation.DialogPresetResolver）

| 测试名 | Given | When | Then（断言） | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [缺省] 空请求态直聊产出内置缺省配置 | 新会话、requestState 空、turnState 空直聊 | resolve | kbScope=[]、systemPrompt=现行缺省串、temperature=0.70、maxTokens=2048、memoryRounds=5、safetyEnabled=true、greeting=null、bindings 四空集 | REQ-C01/C04 | KbAccessChecker |
| [缺省] 内置缺省常量与 AppConfig 六默认值同源 | 任意直聊输入 | resolve + 对照 AppConfigServiceImpl 缺省常量 | 逐值相等（断言引用同一常量持有者，禁止字面量副本） | REQ-C01 | Mapper |
| [回退] 请求态模型优先于应用配置模型（C05×D01 交界） | 挂载应用（app_config.model=A）、turnState.modelId=B、两者均有 online 记录 | resolve | modelRoute.model=B；source=APP_PRESET | REQ-C05/D01 | ModelRegistry 数据 |
| [回退] 模型五级链逐级命中 | 五种前置缺失场景各一测 | resolve | 依次命中 app_config.model / advancedOptions.model / online 记录 / 默认网关；apiUrl/apiKey 恒非空 | REQ-C05 | ModelRegistry 数据 |
| [互斥] 挂载应用不消费请求态知识库范围 | 挂 App（绑定 KB-1）、turnState.kbIds=[KB-2] | resolve | kbScope=[KB-1]（结构断言：挂 App 分支无 turnState.kbIds 读取行——反射/覆盖率佐证） | REQ-D02 | KbAccessChecker |
| [互斥] 直聊知识库范围取请求态 | 无挂载、turnState.kbIds=[KB-2] | resolve | kbScope=[KB-2] | REQ-D02 | KbAccessChecker |
| [ACL] 请求态源无权 KB 跳过并告警 | kbIds=[有权,无权]（resolveRole: viewer / null） | resolve | kbScope=[有权]；warn 日志含被跳 kbId；不抛错 | REQ-B03 | KbAccessChecker |
| [ACL] 应用绑定源同样过滤 | 挂 App 绑定 [有权,无权]（新通道） | resolve | kbScope=[有权] | REQ-B03、G02 | KbAccessChecker |
| [死字段] B02 死字段不进入有效配置 | turnState.toolIds/mcpIds/skillIds/webSearchEnabled 非默认值 | resolve + EffectiveDialogConfig 字段面反射断言 | 配置对象无对应字段/属性；序列化产物不含死字段键 | REQ-B02 | 无 |
| [降级] 存量会话按挂载应用配置解析 | requestState=NULL、app_id 非空 | resolve | 等价于 PRESET_ONLY 同应用产物 | REQ-D03 | Mapper |
| [哨兵] PRESET_ONLY 产物与现行读取链 diff 为空 | 任一已配置应用（含 advancedOptions.model/策略行） | resolve(PRESET_ONLY) vs 现行 runStream 内联读取值 | 逐字段相等（G01 等价性核心断言） | REQ-G01 | Mapper |
| [门禁] 挂载应用不可见抛 E_APP_NOT_VISIBLE | 挂载 app 对 user 不可见 | resolve | notFound/forbidden 业务错误（错误码复用 `## app-center`） | REQ-D01 | DataScope |

### SessionCenter（contract: new-conversation.SessionCenter）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [创建] 空 form 建直聊会话 | 登录用户 | createConversation({}) | app_id=NULL、request_state 空、title=「新对话」 | REQ-A01 | Mapper |
| [列表] 置顶优先+updatedAt 倒序 | 用户 4 会话（1 置顶但最旧） | listConversations | 顺序=[置顶, 其余按 updatedAt] | REQ-A03/E05 | Mapper |
| [列表] 来源标记与批量应用名查询 | 直聊+挂应用会话混合一页 | listConversations | sourceLabel=直聊/应用名；应用名查询**每页恰一次**（防 N+1） | REQ-A03 | Mapper |
| [列表] PageResult 形状 | 任意 | listConversations | {list,total,page,pageSize}（字段名逐字断言，NFR-01） | NFR-01 | Mapper |
| [列表] 仅本人 + API Token 旁路 | userA 3 会话；userB / apiToken 各访问 | listConversations | userB 空列表；apiToken 按旁路语义返回 | REQ-G02 | 登录态构造 |
| [详情] requestState NULL 还原为空请求态 | 存量会话 | getConversation | requestState=空形状（非 null），前端可直用 | REQ-A04/G03 | Mapper |
| [清空] 全删含软删+计数归零+摘要置空 | 会话 3 消息（1 已软删） | clearMessages | 消息行 0（含软删行）；messageCount=0；firstQuestion/answerSummary=NULL | REQ-E03 | Mapper |
| [批量] 批量删与单删级联语义一致 | 2 会话各 2 消息 | deleteConversations([a,b]) | 2 conv 行 + 4 消息行全消失 | REQ-E04 | Mapper |
| [标题] LLM 成功更新 / 失败回退截断 | 模型正常 / LlmService 抛异常 | generateTitle | 成功=title 更新；失败=首问截 30 字+"..."，**不抛错**；模型取请求态 modelId 缺省 default | REQ-E01 | LlmService |
| [停止] 无活跃流 no-op | 该会话无注册句柄 | stop | 不抛错、库无变化 | REQ-E02 | StopRegistry |

### 编排执行核（contract: new-conversation.SessionCenter.runStreamByConversation + 编排器跨链延伸）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [直聊] 空 kbScope 零检索直接生成 | 直聊会话、kbIds=[] | runStreamByConversation | RetrievalSearchEngine **零调用**；事件序=init→message…→end；无 sources 事件 | REQ-C01/C03 | ExecutionEngine |
| [直聊] 有范围时每次都检索（无意图短路） | kbIds=[KB] 闲聊输入「你好」 | runStreamByConversation | RetrievalService 恰调用 1 次 | REQ-C03 | ExecutionEngine+RetrievalService |
| [事件] 事件词汇封闭集零新增 | 任意直聊对话 | 捕获全部事件名 | ⊆ {sources,init,message,thinking,tool_call,tool_result,agent_state,end,error} | REQ-C02 | ExecutionEngine |
| [固化] 三落库点均固化请求态 | 正常完成 / onTimeout / stop 三场景各一测 | 对话终态 | conv.requestState=本轮 turnState（三测同断言） | REQ-B01 | ExecutionEngine |
| [引用] 助手消息 references=投影子集 | 检索 2 命中（sources 事件含 content） | 对话完成 | assistant.references=[{kbId,fileId,fileName,chunkIndex,chunkType,score,imageKeys}]，**无 content 键** | REQ-F01 | RetrievalService+ExecutionEngine |
| [引用] 无命中写空数组 | 空 kbScope 或零命中 | 对话完成 | references='[]'（非 NULL） | REQ-F01 | ExecutionEngine |
| [引用] 旧通道同样落引用 | 挂应用旧 runStream | 对话完成 | assistant.references 同形状（纯增列，G01 其余行为不变） | REQ-F01/G01 | 全缝 |
| [安全] 直聊缺省开敏感词拦截 | 直聊、checkInput 命中 | runStreamByConversation | message(blocked:true)+end；无用户消息行；引擎零调用 | REQ-C04 | SensitiveWordService+ExecutionEngine |
| [安全] 输出替换落库同文 | filterOutput 返回替换文 | 直聊对话 | replace 事件 + assistant.content=替换文 | REQ-C04 | SensitiveWordService+ExecutionEngine |
| [定位] 直聊会话可跨轮继续对话 | app_id=NULL 会话、第二轮 | runStreamByConversation(同 conversationId) | 主键定位成功；历史窗口含第一轮消息（7 处 eq(app_id) 条件化回归） | REQ-A02、S6 | ExecutionEngine |
| [旧壳] runStream 行为逐字节不变 | 既有 app-center S1~S4 用例集 | 全量重跑 | 保持绿色（G01 回归面） | REQ-G01 | 同 app-center |

### StopRegistry（contract: new-conversation.StopRegistry）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [幂等] cancel 首次生效恰一次落库 | 活跃句柄（partialSave 计数器） | cancel ×1 | 返回 true；partialSave 恰 1 次 | REQ-E02 | 无 |
| [幂等] 二次 cancel 无效果 | 已 cancel | cancel 再调 | 返回 false；计数仍 1 | REQ-E02 | 无 |
| [覆盖] 后注册取代取消目标 | 会话 A 活跃句柄 1 | register 句柄 2 → cancel | 句柄 2 的 partialSave 执行、句柄 1 不执行（并发双流互斥登记债务的契约固化） | REQ-E02 | 无 |
| [清理] unregister 后 cancel 无效 | 已 unregister | cancel | 返回 false | REQ-E02 | 无 |
| [并发] 双线程同 cancel 恰一次落库 | 两线程并发 cancel | 并发执行 | AtomicBoolean 保证恰 1 次 partialSave | REQ-E02/NFR-03 | 无 |

### SessionRequestStateStore（contract: data.md ConversationRequestState）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [容错] NULL 与坏 JSON 均还原空请求态 | request_state=NULL / "not-json" | load | 空形状（含全部缺省字段），不抛 | REQ-A04/G03 | Mapper |
| [对称] persist→load 往返等值 | 全形状 turnState（含死字段） | persistOnSuccess → load | 逐字段相等（死字段保留=「唯一证词」断言） | REQ-B01/B02 | Mapper |
| [形状] kbIds 上限 50 拒绝 | kbIds 长度 51 | persist/入参校验 | 参数校验业务错误（不静默截断） | data.md 约束 | 无 |

### DDL 与定位键（contract: data.md `## new-conversation` DDL 变更清单）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [对账] schema.sql ↔ SchemaInitializer 四列一索引一致 | 启动上下文 | 对账测试（沿图谱表对齐先例） | app_id 可空/request_state/is_pinned/references/idx_conv_user 双处齐备 | NFR-02 | 无 |
| [落库] app_id NULL 行插入与查询合法 | 直聊会话 | insert + 主键定位 + 列表 | 三操作成功（MyBatis-Plus eq(null) 恒假风险的直接回归） | REQ-A01/G03 | 真库（H2） |

## 3. 追溯（抽样）

| 测试 | 契约接口 | Design 模块 | Spec REQ |
|---|---|---|---|
| [回退] 请求态模型优先于应用配置模型 | DialogPresetResolver 五级回退 | DialogPresetResolverModule | REQ-C05×D01 |
| [互斥] 挂载应用不消费请求态知识库范围 | DialogPresetResolver Post 不变式 | DialogPresetResolverModule（结构化互斥） | REQ-D02 |
| [固化] 三落库点均固化请求态 | SessionCenter Post + SessionRequestStateStore | 编排器跨链延伸 + 请求态属主 | REQ-B01 |
| [幂等] cancel 首次生效恰一次落库 | StopRegistry.cancel | StopRegistryModule | REQ-E02、NFR-03 |
| [哨兵] PRESET_ONLY 产物 diff 为空 | DialogPresetResolver Pre 不变式 | 编排器双入口（旧壳纪律） | REQ-G01 |

## 4. 待实施说明

- 红绿循环**未执行**（本阶段不改源码）。Tracer Bullet 建议：**「[直聊] 空 kbScope 零检索直接生成」扩展为全链首测**——创建（app_id NULL）→ resolve 缺省 → 零检索 → 事件序列捕获 → 落库+固化+references='[]'，一条测试贯穿全部新模块与三条缝的 mock，最能暴露契约缝隙。
- mock 基建沿用 app-center 既有做法：ExecutionEngine 按真实词汇回放（contract `## agent` 封闭集）、SseEmitter 包装收集事件、登录态构造器复用；KbAccessChecker 为新增 mock 点（接口已在 fastrag-security 现成）。
- 旧通道回归面：`AppModuleValidationTest`（TC-MOCK-001/002/003、TC-REG-004）与 app-center tdd S1~S4 用例集必须在改造后保持绿色——这是 G01 的自动化护栏。
- S0（HITL）不进 CI：人工验收清单挂 issue（范围选择生效、预设 chip 移除转直聊、引用气泡、停止即时性、窄屏抽屉化）。
- 优先级建议：S1（解析矩阵）> S3（编排+固化）> S2 > S4 > S6 > S5 > S7——S1 的五级回退与结构化互斥是全特性最易错处，且 S3 依赖其产物。
