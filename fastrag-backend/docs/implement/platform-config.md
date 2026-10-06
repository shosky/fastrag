# 模型与平台配置（platform-config）— 实现文档

## 1. Code File Map

```
fastrag-modules/fastrag-platform/src/main/java/com/fastrag/module/platform/
├── controller/
│   ├── ModelController.java          /api/models 15 端点：CRUD+测试三族+toggle+import+presets（镜像 ModelRegistryModule/ModelConnectivityTester）
│   ├── ConfigManageController.java   /api 下 ~30 端点：模型训练测试导出+配置 CRUD+七业务开关+安全/发布策略（镜像 ModelTrainingFacade/SysConfigStoreModule/StrategyRegistryModule）
│   ├── DictionaryController.java     /api/dictionaries 5 端点：分组查询+types+CRUD（DictionaryModule (S)）
│   ├── SensitiveWordController.java  /api/sensitive-words 6 端点：CRUD+template+import（SensitiveWordFilterModule 的 HTTP 面）
│   ├── TermController.java           /api/terminology 8 端点：库/词条两级（TermSynonymModule 的 HTTP 面）
│   ├── QueryRuleController.java      /api/query-rules 4 端点（QueryRuleModule (S)）
│   ├── NotificationController.java   /api/notifications 6 端点，直用 mapper 无 service（NotificationModule (S)）
│   └── UploadController.java         /api/upload 单端点品牌图（BrandUploadService (S)）
├── service/
│   ├── ModelService.java / ConfigManageService.java / DictionaryService.java /
│   │   SensitiveWordService.java / TermService.java / QueryRuleService.java（六接口）
│   └── impl/
│       ├── ModelServiceImpl.java         模型 CRUD+testModel 三族路由（test-chat/embedding/rerank→fastrag-ai）
│       ├── ConfigManageServiceImpl.java  train/test 假成功+配置 upsert/历史/默认+两策略 CRUD
│       ├── DictionaryServiceImpl.java    字典查询/分组/内部读口
│       ├── SensitiveWordServiceImpl.java 启用词缓存+checkInput/filterOutput/checkAndFilter
│       ├── TermServiceImpl.java          启用词条缓存+expandSynonyms+term_count 守恒
│       └── QueryRuleServiceImpl.java     规则 CRUD/toggle
├── entity/  ModelRecord/ModelTraining/ModelTestReport/ModelCallLog/SysConfig/SysConfigHistory/SysDictionary/SensitiveWord/TermLibrary/TermRecord/QueryRule/SysNotification/SysSecurityPolicy/SysPublishStrategy（14 实体）
└── mapper/  对应 14 个 MyBatis-Plus mapper
src/test/java/com/fastrag/module/platform/
├── controller/SensitiveWordControllerTest.java   前端格式适配（reply 映射/字段不外泄）
└── service/  SensitiveWordServiceTest(7)/TermServiceTest(7)/DictionaryServiceTest   Mockito 纯单元
```

## 2. Interface Implementations

### ModelRegistry.list/get/create/update/delete/toggle/importModels/exportModels（from contract: platform-config.ModelRegistry）

**Logic Flow:**
1. `list`：组装 LambdaQueryWrapper → keyword 非白则 like name、purpose 非白则 eq purpose（原样字符串，无大小写归一）→ selectList 返回全量（无分页）。
2. `get`：selectById，查无返回 null（HTTP 层 success+data:null）。
3. `create`：new ModelRecord 逐字段取 Map → apiKey 空白则回读 apiKeyRef（双名归一）→ status null 时置 "offline" → insert 返回实体。
4. `update`：selectById 查无返回 null；否则按 containsKey 逐字段覆盖——apiUrl 与 apiKey 双名归一后**空白值跳过不覆盖**；contextWindow 仅 Number 类型接受；status 原样接受 → updateById。
5. `delete`：deleteById 物理删除，无引用检查、无级联。
6. `toggle`：selectById → `"online".equals(status) ? "offline" : "online"` 翻转 → updateById；查无静默。
7. `importModels`：逐条走 create 同款字段装配（purpose 缺省 "chat"、status 缺省 "offline"、无查重）逐条 insert，返回插入清单。
8. `exportModels`：purpose 过滤 selectList → {format:"json", count, items}；**ids 参数被忽略**。
9. 错误恢复：全路径无显式异常处理——依赖 GlobalExceptionHandler 兜底（业务侧无校验错误）。

**Module Interactions:** Calls：ModelRecordMapper（唯一数据面）、（无 fastrag-ai 依赖，测试面除外）。Called by：ModelController；model 表数据被 retrieval/knowledge/graph-eval/application/agent 各链按 code+status 只读消费。Shared state：model 表（全仓路由事实源，本模块唯一写入方）。

**Decisions & Deviations:**
- 与契约无偏离（契约按实现口径冻结）；登记债务：presets 四端点为假成功——listPresets 硬编码 3 组、createPreset/updatePreset 仅回显（生成 id、置 created/updated 标志）不落库、deletePreset 空方法体（ModelServiceImpl.java:241-259）；exportModels ids 忽略；code 无唯一性保证；purpose 自由串依赖 DB 大小写不敏感排序规则与消费方字面值匹配。
- update 空白不覆盖是有意防误清空设计〔ModelServiceImpl.java:171-180 空白守卫 + 注释「兼容前端发送的 apiKey / apiKeyRef 两种字段名」:152,175 佐证防御意图 ‹CERTAIN›〕。〔决议 2026-09-24：presets 持久化意图裁定为**只读静态推荐清单**（原上位文档已失考，按代码与前端消费形态代裁）——GET 为唯一有效端点，三写端点属超配残面；理由：listPresets 硬编码、前端仅展示消费、无任何落库诉求证据；现状差距：三写端点假成功（债务①已登记），收敛方向=下线写端点（spec REQ-A06 同源裁决，用户可推翻）〕。

**Parent Trace:** [ModelServiceImpl CRUD 族] → [platform-config.ModelRegistry] → [Design.ModelRegistryModule+缝 1] → [REQ-A01~A06、REQ-C01~C02]

### ModelConnectivityTester.testChat/testEmbedding/testRerank（from contract: platform-config.ModelConnectivityTester）

**Logic Flow:**
1. 三入口各自硬编码 purpose 常量（"LLM"/"Embedding"/"Rerank"）转调 testModel(modelId, purpose, params)。
2. testModel：selectById → 查无返回 {success:false, error:"模型不存在"}；apiUrl 空白返回 {success:false, error:"模型未配置接口地址"}（两分支均在 try 之外，无测时）。
3. 进入 try，switch(purpose.toUpperCase())：
   - EMBEDDING：embed(code, [text], apiUrl, apiKey) 计时 → 取首向量 → 返回 dimensions/vectorPreview(前 min(10,size) 维)/elapsedMs/apiUrl。
   - RERANK：docs 空则补 3 缺省文档 → rerank(code, query, docs, docs.size(), apiUrl, apiKey) 计时 → 返回 results/elapsedMs。
   - ASR/TTS/OCR：返回「暂不支持在线测试」失败结构（HTTP 无入口，死分支）。
   - default（含 LLM）：chat(code, prompt, apiUrl, apiKey) 计时 → 返回 answer/elapsedMs。
4. 错误恢复：catch(Exception) → {success:false, modelName, modelCode, error:e.getMessage()}，HTTP 恒 200。

**Module Interactions:** Calls：ModelRecordMapper、fastrag-ai 的 LlmService.chat / EmbeddingService.embed / RerankService.rerank（带 apiUrl/apiKey 重载——缝 1 stable side）。Called by：ModelController 三测试端点。Shared state：无（无库写、无缓存）。

**Decisions & Deviations:**
- 与契约无偏离；关键取舍：**测试类型由端点决定而非模型 purpose**（对 Embedding 模型调 testChat 会以模型地址发对话请求）——类 javadoc「根据模型 purpose 路由到对应服务」与实现不符，javadoc 失真登记（ModelServiceImpl.java:54-55 vs :57,71）。
- 测试结果不落 model_call_log/model_test_report（纯实时验证，与 ConfigManage 的假成功「测试报告」互不相关）。

**Parent Trace:** [ModelServiceImpl.testModel 族] → [platform-config.ModelConnectivityTester] → [Design.ModelConnectivityTester] → [REQ-B01~B05]

### SysConfigStore.saveConfig/listHistory/getDefaultConfigs/setDefault/resetToDefault/importConfig（from contract: platform-config.SysConfigStore）

**Logic Flow:**
1. saveConfig(key,value,type,desc,operator)：getConfig 查行 → 无：insert 新行（type/desc/operator 直落）+recordHistory(type=create, old=null)；有：保存旧值 → value 必覆、type/desc 仅非 null 覆 → updateById + recordHistory(type=update, old/new)。
2. recordHistory（私有）：new SysConfigHistory 六字段 + insert——**每次保存必留历史，无旁路**。
3. listHistory：configKey 非白 eq 过滤（**configType 参数被忽略**）→ timestamp 倒序 last("LIMIT 100")。
4. importConfig：逐 item 转 saveConfig（String.valueOf(value)），operator 由调用方传（控制器硬编码 "admin"）。
5. getDefaultConfigs：is_default=1 selectList。
6. setDefault(keys)：逐键 getConfig → 命中则 isDefault=1 updateById（不触碰 value）→ 返回 getDefaultConfigs。
7. resetToDefault(keys,operator)：逐键：行存在且 isDefault==1 → 判「当前值就是默认」加入结果**不动作**；行存在且 ≠1 → 查同键 is_default=1 行（**config_key UNIQUE 下恒空**）→ 空则静默跳过；非空分支（死代码）才会覆值+recordHistory(reset)。
8. 业务开关七端点（控制器层）：body 经静态 ObjectMapper 序列化（失败回 "{}"）→ 固定键 saveConfig；publish-status/review-status 读 getConfig 返回原值串或 "{}"。

**Module Interactions:** Calls：SysConfigMapper、SysConfigHistoryMapper、ModelRecordMapper/ModelTrainingMapper/ModelTestReportMapper（同服务内 train/test/exportModels）、SysSecurityPolicyMapper/SysPublishStrategyMapper。Called by：ConfigManageController；getConfig 被跨模块消费——knowledge 的 StrategyConfigResolver（general_chunk_size/general_chunk_overlap 系统默认层，StrategyConfigResolver.java:181）。Shared state：sys_config/sys_config_history；发布的 publish_switch 等六键被 publish-review 链登记为「无后端 gate 消费」。

**Decisions & Deviations:**
- 与契约无偏离（死逻辑已冻结入契约 Invariants）；登记债务：① resetToDefault 在 UNIQUE 约束下不可能产生实际重置（第一分支 no-op、第二分支恒空——数据模型缺陷：默认值无独立快照行）；② operator 硬编码 "admin" 未接 SecurityUtil（与全仓「切面取会话身份」惯例脱节）；③ listHistory 的 configType 过滤参数失效。
- train/test 硬编码指标（metrics '{"loss":0.32,"accuracy":0.91}'、score "0.92"、status 直写 running/completed 不流转、duration/created_by 无写入方）——假成功债务，与 presets 同族（"假成功缺陷部分修复"指字典/术语/敏感词三族已真字段落库，本族未修）。

**Parent Trace:** [ConfigManageServiceImpl 配置族] → [platform-config.SysConfigStore] → [Design.SysConfigStoreModule+缝 4] → [REQ-E01~E06]

### SensitiveWordFilter.checkInput/filterOutput/checkAndFilter/缓存（from contract: platform-config.SensitiveWordFilter）

**Logic Flow:**
1. checkInput(text)：text 空返回 empty → list()（启用词缓存）→ 逐词：isBlockInput（category 含 `"blockInput":true` 子串）且 text.contains(word) → 返回 Optional.of(replacement 非空白 ? replacement : "您的输入包含受限内容，无法处理")——**首个命中即返回，命中词不回显**；循环结束无命中 empty。
2. filterOutput(text)：空返回 empty → result=text 逐词：isReplaceAnswer 且 contains → result=result.replace(word, replacement!=null ? replacement : "***")、found=true → found ? Optional.of(result) : empty（**全部命中词都替换**，非首中即止）。
3. checkAndFilter(text,mode)：reject 首中即返回「输入包含敏感词: {word}」；replace 逐词替换为 replacement（null→""）；mask 替换为首字+"*"×(len-1)；reject 走完全部未命中 empty，其余模式必返回 present(result)。
4. 缓存：list() 读 volatile enabledCache，null 时 selectList(enabled=1) 回填；invalidate() 置 null；save/update/delete 及控制器 batchImport 的 finally 全部触发 invalidate。
5. 错误恢复：无 try/catch——DB 异常向 GlobalExceptionHandler 兜底；空词表/空文本均为放行语义非错误。

**Module Interactions:** Calls：SensitiveWordMapper。Called by：SensitiveWordController（list/create/update/delete/import）、application 的 AppServiceImpl（checkInput :394、filterOutput :524/:673——isSafetyEnabled 应用级开关在消费方裁决）。Shared state：sensitive_word 表 + 进程内 volatile 缓存（多实例不联动）。

**Decisions & Deviations:**
- 与契约无偏离；关键取舍：① category 用字符串子串匹配而非 JSON 反序列化（对键序/空格脆弱，键序颠倒即失配——test 用例 S4[分类解析]登记）；② 控制器 update 的 buildCategoryJson(body) 全量重建——未传标志被重置 false（部分更新丢拦截面，债务登记）；③ CSV 导入 split(",") 不支持引号转义、Boolean.parseBoolean 宽容解析（非 true 一律 false）；④ blockSearch 标志存储回显但全仓无消费方；⑤ checkAndFilter 生产无调用方（保留通用底座+测试覆盖）。

**Parent Trace:** [SensitiveWordServiceImpl 过滤管线] → [platform-config.SensitiveWordFilter] → [Design.SensitiveWordFilterModule+缝 2] → [REQ-H01~H08]

### TermSynonymService.expandSynonyms/库词条维护（from contract: platform-config.TermSynonymService）

**Logic Flow:**
1. expandSynonyms(query)：query 空白返回 List.of() → enabledTerms()（volatile 缓存，selectList(status=1) 回填）→ 逐词条：query.contains(term) → 别名按 `[,，]` 切分，非空且 query 不含的追加入 LinkedHashSet；query.contains(别名) 且 !query.contains(term) → 追加 term → 返回 ArrayList(set)（保序去重）。
2. createLibrary/createTerm/updateTerm/deleteTerm：库 create 置 termCount=0/时间戳；词条 create 解析 status（Number/Boolean 兼容，缺省 1）+adjustTermCount(+1)+invalidate。
3. updateTerm：查无返回 null；containsKey 逐字段覆盖；libraryId 变更且 ≠ 旧值 → adjustTermCount(旧,−1)+adjustTermCount(新,+1)+换挂 → updateById+invalidate。
4. deleteTerm：selectById 取 libraryId → deleteById → adjustTermCount(−1)（查无词条时 libraryId=null 跳过）→ invalidate。
5. deleteLibrary：先删该库全部词条（delete by libraryId）再删库 → invalidate（**级联物理删除**）。
6. adjustTermCount：lib 查无跳过 → count=max(0, count+delta) → updatedAt=now → updateById。
7. 错误恢复：无显式异常处理；updateLibrary/updateTerm 查无返回 null 非报错。

**Module Interactions:** Calls：TermLibraryMapper、TermRecordMapper。Called by：TermController；expandSynonyms 被 retrieval 的 QueryEnhanceServiceImpl.expandSynonyms 消费（QueryEnhanceServiceImpl.java:79——`## retrieval` 节 QueryEnhancePipeline 拼接追加词）。Shared state：term_library/term_record + 进程内 volatile 启用词条缓存。

**Decisions & Deviations:**
- 与契约无偏离；登记债务：① term_count 为手工守恒计数（直接 SQL 删数据即漂移，无对账——非权威字段）；② owner/category 两字段实体有、创建路径不写（恒 null）；③ 不存在的 libraryId 创建词条照常插入（无归属校验）。

**Parent Trace:** [TermServiceImpl 供给面] → [platform-config.TermSynonymService] → [Design.TermSynonymModule+缝 3] → [REQ-I01~I05]

### (S) 浅模块实现要点（无契约，端点即公共接口）

- **NotificationController**：直用 SysNotificationMapper；read-all 用「status=unread（+userId 条件）」整体 update（setStatus("read") 只带 status 字段）；markRead 查无静默；生产方在 knowledge IngestionConsumer:470-481（target_user 不设=广播）。与 Model/ConfigManage 不同，全控制器无 @Loggable。
- **DictionaryController**：list 分组返回（无 type 时 listAllGroupedByType，LinkedHashMap 保序、null type 归「未分类」）；label 回退 dict_key；parseEnabled 三态兼容；list 整体 try/catch 转 serverError（唯一有整体异常翻译的查询端点）。
- **QueryRuleController/ServiceImpl**：create 只写 name/ruleType(入参 type)/pattern/enabled=1——description/action/priority 三列无任何写入路径；toggle 双值翻转。
- **UploadController**：contentType 白名单（image/jpeg/image/png）→ 1MB 上限 → objectKey=`uploads/brand/{uuid}.{ext}` → MinioService.upload → 返回 `/api/files/{objectKey}`（URL 为应用内代理路径，非 MinIO 直链）；异常转 serverError。任务指派中「两阶段上传」描述与代码不符——两阶段在 knowledge FileController（file-ingestion 链）。
- **StrategyRegistryModule**（ConfigManage 内）：两策略 create 缺省 enabled=1/priority=0；update 全量覆写（p.setId(id) 后 updateById，未传字段按实体 null 覆盖——MyBatis-Plus null 不更新列，故实际为部分语义）。

## 3. Testing

引用 `tdd/platform-config.md` 的 S0~S6 切片。现状 4 个测试（均为 Mockito 纯单元，红绿循环未执行）覆盖情况：

- **已覆盖（映射切片 S4/S5/S6 的单元层子集）**：SensitiveWordServiceTest 7 用例（enabled 过滤、checkAndFilter reject/replace/mask、checkInput 命中、filterOutput 替换）→ S4 的原语子集；SensitiveWordControllerTest（list 前端格式适配、reply 映射、category/replacement 不外泄）→ S4 适配面；TermServiceTest 7 用例（termCount 增减、expandSynonyms 正反向/无命中/空/null）→ S5 的扩展子集；DictionaryServiceTest（getDictValue 命中/未查、getDictMap）→ S6 字典单元层。
- **缺口（无任何测试）**：ModelRegistry 全部（status 口径/密钥防误清空/导入缺省/路由联动——S1）、ModelConnectivityTester 全部（三族路由/失败归一/测时——S2，需 fastrag-ai 真缝 mock）、SysConfigStore 全部（upsert+历史/默认/reset 死逻辑——S3）、SensitiveWord 缓存失效时序与 CSV 导入（S4 剩余）、TermSynonym 缓存失效/跨库移动/级联删除（S5 剩余）、QueryRule/Notification/Upload/两策略/假成功族现状断言（S6 剩余）。无任何接口级（测试库真表）测试；model_call_log 受 DDL 重复列缺陷阻塞。
