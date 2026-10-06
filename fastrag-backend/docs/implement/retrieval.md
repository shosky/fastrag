# 检索与查询增强（retrieval）— 实现文档

> 记录既有代码（HEAD d8f30cd）如何实现 contract/interfaces.md `## retrieval` 节四个契约接口。路径前缀 `fastrag-modules/fastrag-retrieval/src/main/java/com/fastrag/module/retrieval/`。

## 1. Code File Map

```
retrieval/
├── controller/
│   └── RetrievalController.java        15 端点参数搬运（search/计数/查询增强直达/graph expand/日志 CRUD+analysis/更新提醒全套）——HTTP 层，镜像 Design 的 (S) 透传模块
├── service/
│   ├── RetrievalService.java           RetrievalSearchEngine 公共接口（search + getChunkCount 两方法）
│   ├── QueryEnhanceService.java        QueryEnhancePipeline 公共接口（五方法）
│   ├── RetrievalLogService.java        RetrievalLogModule 公共接口（四方法）
│   ├── UpdateRemindService.java        UpdateRemindModule 公共接口（五方法）
│   └── impl/
│       ├── RetrievalServiceImpl.java   检索编排器实现（1785 行：六通道/三融合/后处理/组装/降级/双写日志）
│       ├── QueryEnhanceServiceImpl.java 查询增强管线实现（NER/降级分词/Multi-Query/图谱扩展 + 两 stub）
│       ├── RetrievalLogServiceImpl.java 日志实现（组织隔离分页/聚合分析）
│       └── UpdateRemindServiceImpl.java 更新提醒实现（配置 CRUD/触发计数）
├── mapper/
│   ├── KbRetrievalLogMapper.java       BaseMapper + 三条聚合统计 SQL（@Select 注解）
│   └── KbUpdateRemindMapper.java       BaseMapper 直通
├── entity/
│   ├── KbRetrievalLog.java             kb_retrieval_log 实体（9 字段）
│   └── KbUpdateRemind.java             kb_update_remind 实体（8 字段）
└── model/
    ├── RetrievalRequest.java           请求 + RetrievalConfig（~35 可空配置字段）
    ├── SearchResultItem.java           统一结果条目（15 字段，五来源七通道）
    └── GraphExpansionResult.java       图谱扩展结果 DTO（注：服务实际返回 Map，此 DTO 当前零消费〔grep 实测 ‹CERTAIN›——死模型债务〕）
```

测试：`src/test/java/.../impl/RetrievalServiceImplTest.java`（15 用例）、`QueryEnhanceServiceImplTest.java`（2 用例）。

## 2. Interface Implementations

### RetrievalSearchEngine（from contract: retrieval.RetrievalSearchEngine）

**Logic Flow:**
```
search(req):
 1. checkKbAccess(kbId): 空 kbId 放行 → 超管(*)/api-token: 前缀放行 → Redis GET kb:acl:{kbId}:{userId}，null 则抛 forbidden
 2. query 空白 → RETURN 空列表
 3. config = loadAndMergeConfig: applySystemDefaults（sys_config 三键，异常回退硬编码）→ merge(kb.retrievalConfig JSON，异常仅 warn) → merge(request.config)；merge 只复制非 null 字段（topK 需 >0）
 4. query = preprocessQuery: IF enableAutoCorrection THEN suggest（异常 warn 沿用）→ IF enableQueryRewrite THEN applyQueryRules（同）→ IF enableSynonymExpansion THEN expandSynonyms（同）
 5. results = enableMultiQuery ? multiQuerySearch : executeSearch
    executeSearch: enableMultiRetrieval → multiChannelRecall；ELSE switch(mode){vector|fulltext|默认 hybrid}
    multiQuerySearch: expandQueries（异常→[原query]）→ 变体<2 退 executeSearch → 每变体 safeSearch(executeSearch) → rrfFusionMulti(max(topK*3,50))
    multiChannelRecall: vector 通道 + fulltext 通道 + (qaRecallCount>0 → QA 通道) → switch(fusionStrategy){weighted|interleave|默认 rrf}
 6. IF enableGraphExpand（默认 true）THEN:
      graphHits = safeSearch(graphChannelRecall)
      IF graphHits == null THEN graphChannelDegraded=true; graphHits=[]; warn
      ELSE IF 非空 THEN results = rrfFusionMulti([results, graphHits], topK)
    graphChannelRecall: expandGraph(文本实体) → 实体名≥3字入集合 → KB 有嵌入模型时向量实体匹配 searchEntitiesByVector(5) 追加（失败 warn 降级）
      → 集合空 RETURN [] → 每实体名 LIKE 召回非 parent 分片（限额均分）跳过软删文件 → buildResultItem(0.5,"graph","graph")
      → 任一异常 RETURN null（降级标记）
 7. IF enableClauseRecall（默认 true）THEN clauseRecall: 提取「第X条/款/章/节」提及 → 每提及 LIKE 锚点 + selectByFileId 取前后 clauseWindowSize 邻居（衰减 0.9-0.2*dist，下限 0.3）→ 无锚点命中时标题/headingPath LIKE 召回（剔除 content 已命中 token 的项）→ 非空则 rrfFusionMulti(topK*2)
 8. postProcess: IF 非(组装后重排) 且 enableRerank 且有 rerankModel THEN rerankResults → IF enableLLMRerank THEN llmRerankResults（stub：topK 截断）→ IF enableMMR THEN mmrDiversity(λ 默认 0.7)
    rerankResults: model 表(code,status=online) 解析 apiUrl/apiKey → 查无/离线/空返回/异常 一律 RETURN topK 截断 → 成功按 index 回填 similarity/distance
 9. results = deduplicateResults（键=前500字符+长度，留 similarity 最高，降序）
10. results = assembleContext(strategy): concat/空 → 原样 | parent_chunk → 命中子片替换父块全文（父块缺失退化自身）| parent_document → 按 fileId 聚整篇 | window → 邻居窗口 | auto → assembleAuto（L1 父块在预算内 → L2 窗口候选统一组装 → L3 无 fileId 原样）
11. IF rerankAfterAssembly(strategy∈{parent_chunk,window,auto} 且 enableRerank) THEN 再 rerankResults
12. IF enableKeywordMatch（默认 true）THEN qaChannelRecall（token OR-LIKE 候选 → 命中 token 数降序取 N）→ 非空则按 fileId_chunkIndex 去重后 QA 置顶拼接
13. 全结果统一 index=0..n-1 → resolveFileNames 批量回填 fileName
14. recordLog: 写 KbRetrievalLog（graphHits=source=graph 计数）+ 写 KbLog(category=retrieval, status=success|degraded, extra 七字段) —— try 包裹，失败仅 warn
15. RETURN results

getChunkCount(kbId): 查未软删文件 id 集 → 空 RETURN 0 → count(kb_id=kbId AND file_id IN 活跃 AND chunkType≠'parent')
```

**Module Interactions:**
- Calls：MilvusService.search（缝 1 邻接，collection 名按 chunk-management 约定拼装）、EmbeddingService.embed、RerankService.rerank、QueryEnhanceService 五方法（内部模块，真实注入）、GraphStore.searchEntitiesByVector、ConfigManageService.getConfig、KbChunkMapper（selectByIds/selectList/selectByFileId/fulltextSearch/selectCount）、KbFileMapper、KnowledgeBaseMapper、KbQaPairMapper、ModelRecordMapper、RetrievalLogService.log、KbLogMapper.insert、StringRedisTemplate（kb:acl 读）、SecurityUtil。
- Called by：RetrievalController.search/count；app-center.AppServiceImpl（真实注入，AppServiceImpl.java:59,737 调 search）。‹CERTAIN（2026-09-24 深证，修正原推断）：benchmark 评测（graph-eval）**不经注入消费**本模块——其内部自实现向量召回（EvaluationExecutionHelper.java:267-268 retrieveChunksWithVector、:448 自拼 collection），与本模块无代码依赖；KbOperationServiceImpl 仅 TODO 占位未接入（:49-50）›
- Shared state：读 Redis `kb:acl:*`；写 kb_retrieval_log、kb_log；只读 kb_chunk/kb_file/kb/kb_qa_pair/kb_update_log 无关表不触碰；Milvus 只读。

**Decisions & Deviations:**
- 与契约无偏离。关键取舍：① 图谱通道方案 A（ADR-R1）——expandGraph 的拼词产出在主流程弃用（:462-463 注释），仅保留实体匹配读法；② 融合分不覆盖原始 similarity（:1043-1045 注释）保住消费方语义；③ Rerank 延后到组装后（rerankAfterAssembly）解决「切断块进不了 rerank 视野」；④ LLM 重排/suggest/applyQueryRules 三处 stub 按原样登记（债务见 design §1），接口面不变。
- 权限校验下沉服务层的原因（代码注释明示）：body 传 kbId，KbAuthAspect 的 URI 提取不适用（:109-112）。

**Parent Trace:** [RetrievalServiceImpl] → [retrieval.RetrievalSearchEngine] → [Design.RetrievalSearchEngine（缝 1/2/4/5）] → [Spec REQ-A01~A07、B01~B13、C01~C09]

### QueryEnhancePipeline（from contract: retrieval.QueryEnhancePipeline）

**Logic Flow:**
```
suggest(query): RETURN {suggestedQuery: query, reason: ""}                       ← stub
expandSynonyms(query): added = termService.expandSynonyms(query)
  IF added 非空 THEN expandedQuery = query + " " + join(added) ELSE 原样
  RETURN {expandedQuery, matchedTerms: [], addedTerms}                          ← matchedTerms 恒空（TermService 不回传命中项）
applyQueryRules(query): RETURN {rewritten: query, appliedRules: []}              ← stub（query_rule 零消费）
expandQueries(query,count,model,kbId):
 1. query 空 → [query]；target = clamp(count,1,5)（缺省 3）；target≤1 → [query]
 2. model 空→"default"；非 default 时 model 表(code,status=online) 解析 apiUrl/apiKey，查无 warn 走默认网关
 3. prompt = 中文改写指令（target-1 个变体）
 4. try: response = llmService.chat → LinkedHashSet{query} ∪ 逐行清洗（去编号 ^\d+[.、)]、去首尾引号、长度 2~100、异于原查询）→ 达 target 截断
    → IF size≥2 RETURN；ELSE warn 落单元素
 5. catch: warn → RETURN [query]
extractEntities(query,nerModel,kbId):
 1. query 空 → []；nerModel 空 → fallbackSplit（跳过 LLM）
 2. model 表解析 → 查无 → fallbackSplit
 3. LLM chat（JSON 数组提示词）→ 剥 ``` 包裹 → 截取 [..] → Jackson 解析 → 保留长度≥2 实体
 4. 任一异常 → fallbackSplit
fallbackSplit: 英文/数字词(≥2) 整词；中文块 ≤4 字原样、>4 字保留原块 + 2~4 字 n-gram（每块 ≤8 个）；总量 ≤20 截断
expandGraph(kbId,query,depth,maxEntities[,nerModel]):
 1. query 空 → 四键空结构
 2. checkKbAccess（同检索同键）
 3. entities = extractEntities → 空 → 空结构
 4. graphResult = graphStore.expandGraph(kbId, entities, depth, maxEntities)
 5. expandedQuery = buildExpandedQuery: 原查询 + 未包含的实体名 + 关系标签（各自去重空格拼接）
 6. RETURN {originalQuery, expandedQuery, entities, relations}
```

**Module Interactions:**
- Calls：GraphStore.expandGraph、LlmService.chat、ModelRecordMapper、TermService.expandSynonyms、StringRedisTemplate、SecurityUtil。
- Called by：RetrievalController（suggest/synonyms/applyRules/expand 四直达端点）；RetrievalServiceImpl（预处理链 + 图谱通道 + Multi-Query）。
- Shared state：读 Redis kb:acl；无自有表写入。

**Decisions & Deviations:**
- 与契约无偏离；两 stub（suggest/applyQueryRules）+ matchedTerms 恒空是「接口稳定、实现欠账」的登记项（REQ-D02/D04）。
- n-gram 降级分词的存在理由（注释明示）：配合图谱 CONTAINS 模糊匹配命中「故障根因分析」→「故障根因分析（RCA）」类实体（:269-271）。

**Parent Trace:** [QueryEnhanceServiceImpl] → [retrieval.QueryEnhancePipeline] → [Design.QueryEnhancePipeline（缝 1/3）] → [Spec REQ-D01~D07]

### RetrievalLogModule（from contract: retrieval.RetrievalLogModule）

**Logic Flow:**
```
accessibleKbIds(): user=null → 空集 | userId 以 "api-token:" 开头 → null(不过滤) | ELSE accessChecker.getAccessibleKbIds(userId,orgId)
log/update: mapper.insert/updateById 直通
page(kbId,hasResult,page,pageSize):
 1. accessible = accessibleKbIds()
 2. IF kbId 指定: accessible≠null 且不含 kbId → RETURN 空页；ELSE wrapper.eq(kbId)
   ELSE IF accessible≠null: 空 → 空页；ELSE wrapper.in(kbId, accessible)
 3. hasResult≠null → eq；orderByDesc(createdAt) → selectPage → PageResult.of(records,total,page,pageSize)
analysis(kbId):
 1. 同上越权/空集合分支 → emptyAnalysis()（全零+空列表）
 2. stats = mapper.aggregateStats(kbId 或 accessible 集合) → 换型（BigDecimal/Number/字符串兼容）
 3. noResultRate = round(no_result*10000/total)/100；topQueries/noResultQueries = mapper 两 Top10 SQL
 4. RETURN 七键 Map
```

**Module Interactions:**
- Calls：KbRetrievalLogMapper（insert/updateById/selectPage/三条聚合 SQL）、KbAccessChecker.getAccessibleKbIds、SecurityUtil。
- Called by：RetrievalController（logs 四端点）；RetrievalServiceImpl.recordLog（log 方法）。
- Shared state：读写 kb_retrieval_log。

**Decisions & Deviations:**
- 与契约无偏离。关键取舍：指定越权库返回空页而非报错——防泄露库存在性（:69-71 注释）；log/update 写入口无权限校验属登记债务（契约 Invariants 已注明）。
- 统计值换型防御三种 JDBC 返回型（BigDecimal/Number/String，:140-151）。

**Parent Trace:** [RetrievalLogServiceImpl + KbRetrievalLogMapper] → [retrieval.RetrievalLogModule] → [Design.RetrievalLogModule] → [Spec REQ-E01~E06]

### UpdateRemindModule（from contract: retrieval.UpdateRemindModule）

**Logic Flow:**
```
list(kbId): kbId 非空 → eq；orderByDesc(createdAt) → selectList
get(kbId): selectOne(eq(kbId), LIMIT 1)
save(remind): id 空 → 补默认(enabled=1, cronExpr="0 9 * * *") → insert | 非空 → updateById；RETURN remind
delete(id): deleteById
remind(kbId):
 1. remind = get(kbId)
 2. result = {kbId, enabled=remind≠null&&enabled==1, lastRemindAt, cronExpr(缺省默认), message 固定文案}
 3. updateCount = remind≠null 且 lastRemindAt≠null ? count(kbId=kb AND timestamp>lastRemindAt) : count(kbId=kb)
 4. remind≠null → lastRemindAt=now → updateById（副作用：触发即刷新）
 5. RETURN result
```

**Module Interactions:**
- Calls：KbUpdateRemindMapper、KbUpdateLogMapper（计数）。
- Called by：RetrievalController（update-remind 五端点）。无其他消费方（无调度器）。
- Shared state：读写 kb_update_remind；只读 kb_update_log。

**Decisions & Deviations:**
- 与契约无偏离。债务：cron_expr 与 channels 字段零消费（无 @Scheduled 调度器、无渠道投递），提醒为拉取式；get(kbId) LIMIT 1 暗含「每库一条」语义但 DDL 无唯一约束（data.md 已登记）。

**Parent Trace:** [UpdateRemindServiceImpl] → [retrieval.UpdateRemindModule] → [Design.UpdateRemindModule] → [Spec REQ-F01~F04]

## 3. Testing

对照 tdd/retrieval.md 切片：

- **已覆盖**：S2 部分（T1/T2/T12 阈值 0 值与 KB 配置保持）、S3 部分（T4~T13 QA/图谱通道/hybrid 融合/去重语义）、S4 部分（T14~T18 auto 三级降级/条款召回/window 邻居）、S5 部分（T41~T42 降级分词）——即 RetrievalServiceImplTest 15 用例 + QueryEnhanceServiceImplTest 2 用例（两测试均以超管身份绕过 ACL 聚焦检索逻辑，RetrievalServiceImplTest.java:77-83）。
- **缺口（对应新增用例号）**：S1 降级路径族几乎全缺（T19~T26——现测试无任何异常注入路径）、S2 权限分支（T31/T32）、S3 融合策略对比与 RRF 保分断言部分（T27~T30/T40）、S4 token 预算与组装后重排（T36~T39）、S5 大部分（T43~T52）、S6 全缺（T53~T60——RetrievalLogServiceImpl 零测试）、S7 全缺（T61~T67——UpdateRemindServiceImpl 零测试）。S0 人评切片未组织。
- 实施提示：降级族（T19~T26）与本特性「任何通道失败不阻断」的核心不变式直接对应，建议最先补齐。
