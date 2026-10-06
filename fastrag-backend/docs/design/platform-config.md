# 模型与平台配置 — 架构设计

## 1. Module Map

| 模块 | Responsibility | Files | Depth Rationale |
|---|---|---|---|
| **ModelRegistryModule** | 模型注册表的唯一读写口：CRUD/启停/导入导出维护「平台接入了哪些模型、哪些在线」的全仓路由事实源 | ModelController.java、service/ModelService.java、service/impl/ModelServiceImpl.java、entity/ModelRecord.java、mapper/ModelRecordMapper.java | 深模块——「路由数据一致性」（status 口径、code 路由键、密钥防误清空）被 5+ 消费链隐式依赖，全部藏在 CRUD 之后 |
| **ModelConnectivityTester** | 模型连通性验证：三类测试（对话/向量/排序）路由到 fastrag-ai 对应服务，统一计时、统一成功/失败结果包 | ModelServiceImpl.java:37-140（与 Registry 同类，测试面独立成方法族） | 深模块——三类网关差异、失败归一化、前置校验全部折叠进 testModel 一个入口 |
| **ModelTrainingFacade (S)** | 模型训练/测试报告的记录面 | ConfigManageServiceImpl.java:14-38 | **(S)** 浅模块——train/test 为硬编码指标无真实编排，exportModels 的 ids 参数被忽略；建议真实训练落地前勿再扩端点 |
| **SysConfigStoreModule** | 系统参数的存储与审计：upsert 语义、变更历史强制记账、默认值标记 | ConfigManageController.java:53-90、ConfigManageServiceImpl.java:39-109、entity/SysConfig.java、SysConfigHistory.java | 深模块——「配置变更必有历史」的不变式与 upsert/导入/开关端点共享同一 saveConfig 收口 |
| **StrategyRegistryModule (S)** | 安全策略与发布策略两张注册表的 CRUD | ConfigManageController.java:91-106、ConfigManageServiceImpl.java:110-127 | **(S)** 浅模块——无消费方的纯注册面，CRUD 包装；若长期无执行方建议内联合并 |
| **DictionaryModule (S)** | 数据字典的分组查询与 CRUD | DictionaryController.java、DictionaryService.java、impl/DictionaryServiceImpl.java | **(S)** 浅模块——全仓无后端消费方（grep 实测），前端下拉选项数据面；label 回退是唯一适配逻辑 |
| **SensitiveWordFilterModule** | 内容安全过滤管线：启用词表缓存、三级分类解析、输入拦截/输出替换/通用三模式原语 | SensitiveWordController.java、SensitiveWordService.java、impl/SensitiveWordServiceImpl.java、entity/SensitiveWord.java | 深模块——「逐消息检查」的性能（缓存）、命中词不回显的安全语义、替换兜底全部内藏 |
| **TermSynonymModule** | 术语库与同义供给：库-词条两级维护、term_count 守恒、启用词条缓存、双向同义匹配 | TermController.java、TermService.java、impl/TermServiceImpl.java、entity/TermLibrary.java、TermRecord.java | 深模块——「仅启用词条参与扩展」的治理口径与缓存一致性是检索侧的隐式依赖 |
| **QueryRuleModule (S)** | 查询规则数据面 CRUD 与启停 | QueryRuleController.java、QueryRuleService.java、impl/QueryRuleServiceImpl.java、entity/QueryRule.java | **(S)** 浅模块——零消费方（retrieval 链已登记占位），纯存储 |
| **NotificationModule (S)** | 系统通知的查询/已读/删除与生产写入 | NotificationController.java（直用 mapper，无 service）、entity/SysNotification.java | **(S)** 浅模块——控制器直用 SysNotificationMapper；生产方在 knowledge 侧（IngestionConsumer） |
| **BrandUploadService (S)** | 品牌图片单阶段上传（类型/大小校验→MinIO→URL） | UploadController.java | **(S)** 浅模块——单端点校验+存储包装 |

与 ADR 要点的关系：ADR-0001（绑定-重切一致）不涉及本特性；ADR-0002（图存储收敛 Neo4j）不涉及。本特性自身无既有 ADR。

## 2. Interface Contracts

> 契约正文在 contract/interfaces.md `## platform-config` 节（ModelRegistry / ModelConnectivityTester / SysConfigStore / SensitiveWordFilter / TermSynonymService 五个）；本节列公共接口暴露面。(S) 模块不立契约，端点即公共接口。

### ModelRegistryModule（contract: platform-config.ModelRegistry）

- Public interface：`list(keyword, purpose)` / `get(id)` / `create(fields)` / `update(id, fields)`（部分语义）/ `delete(id)` / `toggle(id)` / `importModels(list)` / `exportModels(ids, purpose)`——HTTP 面 ModelController 15 端点中 CRUD/导入导出/toggle 十个；presets 四端点属假成功面（implement §2 登记）。
- Hidden implementation：apiKey/apiKeyRef 双字段名归一；status 缺省 offline；code 即路由键的口径约束（不强制唯一，消费方 LIMIT 1）。
- Depth rationale：消费链只认「code+status=online → apiUrl/apiKeyRef」，注册表内部字段演化（contextWindow/enableThinking 运行时增补列）不波及消费方。

### ModelConnectivityTester（contract: platform-config.ModelConnectivityTester）

- Public interface：`testChat(modelId, prompt)` / `testEmbedding(modelId, text)` / `testRerank(modelId, query, documents)` → 统一 `Map{success, modelName, modelCode, purpose, ..., elapsedMs, apiUrl}` 或 `Map{success:false, error}`。
- Hidden implementation：按端点（非模型 purpose）路由到 LlmService.chat / EmbeddingService.embed / RerankService.rerank；耗时计时；异常 catch 归一。
- Depth rationale：调用方（前端模型管理页）无需感知三类网关的方法签名差异与失败形态。

### SysConfigStoreModule（contract: platform-config.SysConfigStore）

- Public interface：`listConfigs(configType)` / `getConfig(configKey)` / `saveConfig(key, value, type, desc, operator)` / `listHistory(key, type)` / `exportConfig(configType)` / `importConfig(items, operator)` / `getDefaultConfigs()` / `setDefault(keys)` / `resetToDefault(keys, operator)`；HTTP 面含七个业务开关专用端点（固定键转调 saveConfig）。
- Hidden implementation：历史强制记账（create/update/reset 三型）；operator 由控制器硬编码 "admin"（未接会话身份）。
- Depth rationale：七类开关端点、导入、单键设默认全部折叠为同一 upsert+history 路径——新增配置键零新代码。

### SensitiveWordFilterModule（contract: platform-config.SensitiveWordFilter）

- Public interface：`checkInput(text)` / `filterOutput(text)` / `checkAndFilter(text, mode)`（通用底座）/ `list()` / `save/update/delete` / `invalidate()`。
- Hidden implementation：volatile 启用词缓存（写失效读加载）；category JSON 字符串包含匹配解析；命中词不回显；替换缺省 ***。
- Depth rationale：对话链逐消息调用只看到 `Optional<String>`（拦截→回复语；过滤→替换后文本）；词表规模、缓存、分类解析全在缝内。

### TermSynonymModule（contract: platform-config.TermSynonymService）

- Public interface：`listLibraries/createLibrary/updateLibrary/deleteLibrary` / `listTerms/createTerm/updateTerm/deleteTerm` / `expandSynonyms(query)`。
- Hidden implementation：启用词条 volatile 缓存；term_count 守恒调整（移动/删除 Math.max(0,…)）；别名中英文逗号切分；双向匹配去重。
- Depth rationale：检索侧 QueryEnhancePipeline 只调 expandSynonyms 拿追加词列表，「启用状态治理、缓存、匹配规则」不可见。

## 3. Seam Diagram

### 缝 1：模型动态路由缝（全仓共享，本链为生产方）

- **What varies**：模型供应商、地址、密钥、思考模式开关——接入新模型/换供应商不改任何消费代码。
- **Adapters**：① 生产方 ModelRegistryModule（写 model 表）；② 消费方解析器（各链自建：AiChunkServiceImpl.resolveRecord:1582-1592、ChunkingServiceImpl:236-246、RetrievalServiceImpl 等——同口径 code+status='online' LIMIT 1）；③ fastrag-ai 网关方法族（`(model, …, apiUrl, apiKey[, enableThinking])` 重载）。≥2 适配器，真缝。
- **Stable side**：fastrag-ai 服务方法签名与「无在线记录→null 下传→默认网关 `ai.gateway.url`（缺省 http://localhost:11434）」的降级协议（LlmService.java:77-78,101）。
- **Placement rationale**：路由数据生产收敛在 platform 唯一写入面；解析留在消费侧是**既成事实而非设计优选**——消费方各写一份 selectOne 导致口径漂移（benchmark 无 online 过滤，BenchmarkGenerationHelper.java:196 裸 SQL），收敛方向见 §4。

### 缝 2：内容安全过滤缝

- **What varies**：敏感词词表内容、三级标志组合、替换文案——运营自助治理，不改代码。
- **Adapters**：① AppServiceImpl（对话输入 checkInput:394 + 输出 filterOutput:524,673，应用级 isSafetyEnabled 开关裁决）；② 潜在的检索屏蔽消费方（blockSearch，当前无）。2 个登记 1 个缺位——缝成立（对话侧已真实消费）。
- **Stable side**：`Optional<String>` 返回协议（empty=放行；present=拦截回复/替换后文本）。
- **Placement rationale**：过滤管线属平台内容安全底座，被多应用共享；放 app 侧会随应用复制。

### 缝 3：查询同义扩展缝

- **What varies**：术语库内容与启用状态——术语治理运营自助。
- **Adapters**：① QueryEnhanceServiceImpl.expandSynonyms:78-89（拼接追加词）；② benchmark/其他潜在消费方（当前无第二消费方——**假设缝**‹CERTAIN（2026-09-24 深证）：grep 全仓 expandSynonyms，TermService 的生产调用方仅 QueryEnhanceServiceImpl.java:79 一处；其上游有两个触发路——专用端点 RetrievalController.java:47 与检索预处理管线 RetrievalServiceImpl.java:468（enableSynonymExpansion 门控），均经同一适配器类，适配器数=1 结论维持；缝宽度存疑但术语治理与检索解耦的结构收益明确，保留缝›）。
- **Stable side**：`List<String> expandSynonyms(query)` 追加词语义（不含原查询）。
- **Placement rationale**：term 治理在 platform、消费在 retrieval，与 feature-map「Shared by: retrieval（term 启用状态）」一致。

### 缝 4：系统默认值缝

- **What varies**：平台默认参数（general_chunk_size/general_chunk_overlap 等种子键）。
- **Adapters**：① StrategyConfigResolver 三层合并的系统默认层（StrategyConfigResolver.java:181 经 ConfigManageService.getConfig 读取）；② 前端品牌/参数展示面。
- **Stable side**：`getConfig(configKey) → SysConfig|null` 读口。
- **Placement rationale**：默认值唯一事实源在 sys_config；消费方只在合并协议中读，不直连表。

## 4. Deepening Opportunities

- **ModelRegistryModule**：把「code+status=online LIMIT 1 → apiUrl/apiKey」的解析原语收进本模块（如 `resolveOnline(code)`），替换 5+ 处消费方各自手写的 selectOne，统一 benchmark 缺失的 online 过滤口径——这是全仓共享契约口径不一致的唯一根治点（上游 benchmark-evaluation 节登记的「无 status=online 过滤」与本链生产方口径在此合流）。
- **SensitiveWordFilterModule**：吞掉 blockSearch 的拦截行为（联网检索屏蔽目前只有存储标志无执行方）；category 解析从字符串包含匹配升级为 JSON 反序列化（当前 `"blockInput":true` 子串匹配对键序/空格脆弱）。
- **SysConfigStoreModule**：吞掉 operator 的会话身份解析（当前控制器硬编码 "admin"）与 resetToDefault 的真实默认值快照语义。
- **ModelConnectivityTester**：吞掉 model_call_log 写入（连通性测试与业务调用统一记账，修复监控数据源死表）。
- **ModelTrainingFacade**：真实训练编排落地前，None（不建议在假实现上加深）。

## 5. Parent Trace

| Design 模块/缝 | Spec REQ |
|---|---|
| ModelRegistryModule（缝 1 生产方） | REQ-A01~A06、REQ-C01、REQ-C02 |
| ModelConnectivityTester | REQ-B01~B05 |
| 缝 1（模型动态路由） | REQ-C01、REQ-C02、REQ-C03（数据面） |
| ModelTrainingFacade (S) | REQ-D01、REQ-D02 |
| SysConfigStoreModule（缝 4） | REQ-E01~E06 |
| StrategyRegistryModule (S) | REQ-F01、REQ-F02 |
| DictionaryModule (S) | REQ-G01~G03 |
| SensitiveWordFilterModule（缝 2） | REQ-H01~H08 |
| TermSynonymModule（缝 3） | REQ-I01~I05 |
| QueryRuleModule (S) | REQ-J01 |
| NotificationModule (S) | REQ-K01~K03 |
| BrandUploadService (S) | REQ-L01 |
| ModelRegistryModule（缝 1 生产方） | REQ-A02（2026-09-24 review 补锚：创建模型 status 未指定落 offline（不上线即不被消费方路由），入参兼容 apiKey/apiKeyRef） |
| ModelRegistryModule（缝 1 生产方） | REQ-A03（2026-09-24 review 补锚：部分更新仅覆盖提供字段；apiUrl/apiKey 传空白值不覆盖既有值防误清空） |
| ModelRegistryModule（缝 1 生产方） | REQ-A04（2026-09-24 review 补锚：可删除模型与切换 online/offline；toggle 是 status 唯一翻转入口） |
| ModelRegistryModule（缝 1 生产方） | REQ-A05（2026-09-24 review 补锚：批量导入逐条插入（purpose 缺省 chat、status 缺省 offline），可按 purpose 过滤导出清单） |
| ModelConnectivityTester | REQ-B02（2026-09-24 review 补锚：向量化测试（自定义文本缺省「你好世界」）返回向量维度、前 10 维预览与耗时） |
| ModelConnectivityTester | REQ-B03（2026-09-24 review 补锚：重排序测试（query+文档列表，空列表补缺省 3 文档）返回排序结果与耗时） |
| ModelConnectivityTester | REQ-B05（2026-09-24 review 补锚：任何测试失败以统一失败结构（success=false+错误消息）返回，不抛出为 HTTP 错误） |
| SysConfigStoreModule（缝 4） | REQ-E02（2026-09-24 review 补锚：可查询配置变更历史（按 configKey 过滤、最多最近 100 条、时间倒序）） |
| SysConfigStoreModule（缝 4） | REQ-E03（2026-09-24 review 补锚：配置可导出 JSON 清单、批量导入逐键 upsert） |
| SysConfigStoreModule（缝 4） | REQ-E04（2026-09-24 review 补锚：配置项可标记默认（is_default=1）并查询默认配置清单） |
| SysConfigStoreModule（缝 4） | REQ-E06（2026-09-24 review 补锚：七类业务开关专用读写端点，值以 JSON 存储于固定 configKey） |
| DictionaryModule (S) | REQ-G02（2026-09-24 review 补锚：查询全部字典类型清单（去重）） |
| DictionaryModule (S) | REQ-G03（2026-09-24 review 补锚：字典条目增改删；enabled 入参兼容布尔/数字/字符串；列表按 sortOrder 与 id 排序） |
| SensitiveWordFilterModule（缝 2） | REQ-H02（2026-09-24 review 补锚：创建/导入的敏感词默认启用；更新时三级标志按请求体重建（未传视为 false）） |
| SensitiveWordFilterModule（缝 2） | REQ-H03（2026-09-24 review 补锚：CSV 导入模板下载与批量导入（跳过表头、逐行解析词/回复/三标志），导入后词表缓存失效） |
| SensitiveWordFilterModule（缝 2） | REQ-H05（2026-09-24 review 补锚：输出过滤——命中启用词的 replaceAnswer 标志时替换为指定回复（未配置用 *），有替换才返回新文本） |
| TermSynonymModule（缝 3） | REQ-I02（2026-09-24 review 补锚：术语条目增删改查、按 libraryId 过滤；词条跨库移动时两库 term_count 联动增减且不为负） |
| TermSynonymModule（缝 3） | REQ-I05（2026-09-24 review 补锚：词条缓存读加载、任何词条写操作失效） |
| NotificationModule (S) | REQ-K02（2026-09-24 review 补锚：系统各链可产生通知（如摄取完成的知识更新提醒），target_user 为空即全员广播） |
| NotificationModule (S) | REQ-K03（2026-09-24 review 补锚：标记单条已读/全部已读（可按 userId 限定）/删除通知；标记不存在的通知静默成功） |

## ADR 引用指针

本文所引 **ADR-0001**（解析策略绑定变更必须触发重新分片）与 **ADR-0002**（图存储收敛 Neo4j——GraphStore 唯一实现、启动 fail-fast）原文已随根 docs/ 树删除丢失，全文重构版见 [adr/0001-strategy-binding-requires-rechunk.md](adr/0001-strategy-binding-requires-rechunk.md) 与 [adr/0002-graph-store-consolidate-on-neo4j.md](adr/0002-graph-store-consolidate-on-neo4j.md)（2026-09-24 由链内要点源重构，不可考处 [GAP] 标注）；本文正文「仅引要点」处即指该两文。
