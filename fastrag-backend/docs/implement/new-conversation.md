# 新建对话与会话中心（new-conversation） — 实现文档

> **M2 完成**（2026-09-25）：后端全量落地，模块测试 71/71 绿（新增 38 + 存量 33）。输入：contract `## new-conversation` 节、design/new-conversation.md、tdd/new-conversation.md（8 切片 44 用例）。

## 1. Code File Map

```
fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/
├── controller/SessionCenterController.java                    [M2 ✅] 12 端点（CRUD/流式/起标题/停止/消息级）
├── dto/
│   ├── ConversationRequestState.java                          [M1 ✅] 请求态单一形状两用 + PRESET_ONLY 哨兵 + validate()
│   ├── ConversationDTO.java                                   [M2 ✅] 列表/详情 DTO（appId+appName+direct 来源标记三件）
│   └── AppChatRequest.java                                    [不变] 旧通道请求体
├── entity/
│   ├── AppConversation.java                                   [M1 ✅] +requestState/isPinned（app_id 语义放宽 NULL=直聊）
│   └── AppConversationMessage.java                            [M1 ✅] +references（@TableField 反引号，MySQL 保留字）
├── service/
│   ├── AppService.java                                        [M2 ✅] +runStreamByConversation
│   ├── impl/AppServiceImpl.java                               [M2 ✅] 双入口单执行核（15 触点换 cfg/StopRegistry 接线/
│   │                                                                 persistAnswer 恰一次/引用投影/主键定位守卫）
│   ├── preset/
│   │   ├── DialogDefaults.java                                [M1 ✅] 缺省常量持有者（单源）
│   │   ├── EffectiveDialogConfig.java                         [M1 ✅] 有效配置只读值对象（ModelRoute/Bindings 内嵌）
│   │   └── DialogPresetResolver.java                          [M1 ✅] 三源解析（D02 结构互斥/C05 五级回退/B03 两来源 ACL/哨兵）
│   └── session/
│       ├── SessionCenterService.java                          [M2 ✅] 生命周期 + 统一列表 + 直聊消息级补口（委托账本）
│       ├── SessionRequestStateStore.java                      [M1 ✅] request_state JSON 形状唯一属主
│       └── StopRegistry.java                                  [M1 ✅] 停止注册表（覆盖语义 + 双落库幂等守卫）
init-scripts/schema.sql                                        [M1 ✅] app_conversation 4 变更 + message.references
fastrag-bootstrap/.../SchemaInitializer.java                   [M1 ✅] MODIFY app_id 可空 + 3 addColumn + idx_conv_user（1061 幂等）
```

测试（test 树）：DialogPresetResolverTest 12 ✅ / StopRegistryTest 5 ✅ / SessionRequestStateStoreTest 5 ✅ / SessionCenterServiceTest 11 ✅ / ConversationStreamTracerTest 3 ✅（Tracer Bullet）/ NewConversationSchemaSyncTest 2 ✅ —— **合计 38 新用例 + 存量 33 = 模块 71/71 绿（2026-09-25）**。

## 2. Interface Implementations

### resolve（from contract: new-conversation.DialogPresetResolver）✅ M1

```
IF turnState == null OR turnState.presetOnly:            # 旧通道哨兵
    mountedAppId = conversation.appId
ELSE:
    turnState.validate()                                  # kbIds≤50 拒绝（不静默截断）
    mountedAppId = turnState.appId 非空 ? turnState.appId : null   # 新通道整包覆盖语义
IF mountedAppId 非空:                                     # ---- 应用预设源 ----
    app = selectById; null→notFound; !DataScope.visible→forbidden
    appConfig/basicConfig 按 appId 单查（可空）
    modelCode = (非哨兵且 turnState.modelId) ?? appConfig.model ?? advancedOptions.model   # C05：请求态模型最高优先
    kbScope = 绑定(enabled=1).kbIds; IF 非哨兵: kbScope = ACL过滤(kbScope)   # D02 互斥=不读 turnState.kbIds；B03 仅新通道过滤（G01）
    safety = policy null→true（现行口径）; greeting = basic.greeting
ELSE:                                                     # ---- 知识库直聊源 ----
    effective = (turnState 非哨兵) ? turnState : persisted形状
    kbScope = ACL过滤(effective.kbIds); bindings=四空集; safety=true; greeting=null
    modelCode = effective.modelId
modelRoute = modelCode ? online记录(apiUrl/apiKeyRef/enableThinking) : 缺失→warn+默认网关   # 五级回退终点
缺省: prompt/0.7/2048/5 全部引用 DialogDefaults（单源常量持有者）
```

### runStream / runStreamByConversation / executeDialog（双入口单执行核）✅ M2

```
runStream(appId,...)（旧壳）:
    requireVisible(app) → conv = locateSessionForApp(...) → cfg = resolve(conv, PRESET_ONLY, user)
    → executeDialog(conv, cfg, query, userId, persistState=null)

runStreamByConversation(conversationId, query, turnState, user):
    conv = requireOwnConversationById(conversationId, user)        # 主键定位不变量（7 处 eq(app_id) 条件化的锚）
    turnState = turnState ?? store.load(conversationId)
    cfg = resolve(conv, turnState, user)
    → executeDialog(conv, cfg, query, userId, persistState=turnState)

executeDialog(conv, cfg, query, userId, persistState):
    checkInput 命中 → message{blocked}+end，零副作用
    用户消息落库 + 首问/标题回填（同现行）
    greeting = cfg.greeting 非空∧首轮∧打招呼 → message{isGreeting}+end，greeting 落助手消息
    handle = CancelHandle{ partialSave = persistAnswer(全量部分答案) + emitter.complete() }
    stopRegistry.register(conv.id, handle); onCompletion → unregister
    onTimeout → partialSave
    异步: retrievalCtx = retrieveContext(cfg.kbScope, query); retrievalRef.set(...)
          sources 事件 → 历史窗口(cfg.memoryRounds*2) → BaseContext(cfg.bindings 四类 + knowledges=cfg.kbScope)
          预算(cfg 四字段) → ModelConfig(cfg.modelRoute/temperature/maxTokens)
          agentEngine.executeStream(..., callback → persistAnswer(finalAnswer 含 replace 事件, sources, ...))
    异常: handle.cancelled ? complete（抑制 error，NFR-03）: error 事件+complete

persistAnswer(...): saved.CAS(f,t) 失败→return; 空答案→标记后 return;
    saveAssistantMessage(..., references=投影(sources 去 content)); persistState!=null → store.persistOnSuccess
```

### SessionCenter 九方法 + 消息级三方法（contract: new-conversation.SessionCenter）✅ M2

```
createConversation(form): appId 可空可见性校验 → 建行(直聊/挂载) + request_state 初值固化
listConversations(page,size): 非API Token eq(userId) → isPinned DESC, updatedAt DESC → LIMIT 手工分页
                             → PageResult.of；appName 每页一次批量查询（防 N+1）
getConversation(id): 详情态 requestState(空形状容错) + messages(含 references) 组装
pin / clearMessages(全删含软删+计数0+摘要置空) / deleteConversations(逐个级联)
generateTitle: llmService.chat(modelId?:"default", 标题 prompt) → 清洗引号句号 → ≤30 截断
              → 失败/空返回静默回退原标题（不抛）
stop: requireOwn + stopRegistry.cancel（无活跃流 false no-op）
deleteMessage/feedbackMessage/updateMessage: 委托 AppService 同名（appId 参数遗留 no-op，
    方法体仅 requireOwnMessage 消息→会话→属主链——直聊消息级补口，债务 #4）
```

## 3. Testing

- **M2 绿（2026-09-25）**：模块全量 **71/71**——新增 38（Resolver 12 / StopRegistry 5 / Store 5 / SessionCenter 11 / Tracer 3 / SchemaSync 2）+ 存量 33（AppModuleValidationTest 19 源码级断言 = G01 自动化护栏、AppConfigIntegrationTest 7、AppConfigServiceTest 7）。
- Tracer Bullet「空范围直聊全链」：创建态会话 → 零检索断言 → 引擎恰一次 → 两消息落库 + references="[]" → persistOnSuccess 固化——一条测试贯穿解析消费面/执行核/注册表/请求态属主/账本。
- 关键坑（已解，记录给后续链）：**`.m2` 陈旧快照陷阱**——plain `-pl <module> test` 用本地仓库旧 jar（曾致 SearchResultItem.getFileName / ToolRegistry NoClassDefFoundError 假错）；改模块上游依赖后必须 `-am install -DskipTests` 刷新再跑。前端并行实现的同类问题在 vite 侧不存在。

## 4. Deviations / 债务登记

1. ~~缺省常量收敛未完成~~ **已关闭（M2）**：旧执行核内联配置块整体删除，AppServiceImpl 不再持有缺省字面量；AppConfigServiceImpl 六默认值为配置面正典（与 DialogDefaults 的等值由 DialogPresetResolverTest.defaultsAreSingleSourceOfTruth 护栏断言），无需反向引用。
2. **解析器为实现类非接口**：单实现不立接口（假设缝不立缝，design §3）；contract 的"接口名"由类公共方法承载。〔偏差已记录，无消费者影响〕
3. ~~Store.exists~~ **已删除（M2）**：无消费方，防 pass-through 腐化（design 删除测试）。
4. ~~直聊会话消息级操作无端点~~ **已关闭（M2）**：SessionCenter 增补三个消息级方法（委托账本，appId 遗留 no-op 语义已在 SessionCenterService javadoc 登记）；控制器 3 端点暴露。
5. **tools 模块 13 个存量测试失败（未登录）**：与本特性无关的存量环境依赖失败（SkillServiceImplTest 缺登录态构造），reactor `-am test` 会在 tools 处中断——已有问题，归 tools 链处理。
