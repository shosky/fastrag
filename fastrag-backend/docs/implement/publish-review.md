# 发布、版本与知识审核（publish-review）— 实现文档

> 本特性仅 KbOperationLogService 有契约级实现记录；VersionModule/ReviewModule/PublishManageModule 无独立契约（端点排除，见 contract/interfaces.md `## publish-review`），§2 按 design §2 公共接口逐个记录既有实现。

## 1. Code File Map

```
fastrag-modules/fastrag-publish/src/main/java/com/fastrag/module/publish/
├── aspect/KbLogAspect.java        @Loggable @Around 切面：SpEL/kbId/操作人/模块名解析，双路径记账+审计事件（镜像 LogAuditModule）
├── controller/PublishController.java   版本 5 端点 + 审核 5 端点，含动作名→状态名映射（镜像 VersionModule+ReviewModule）
├── controller/LogController.java  日志 6 端点：分页查询/统计/更新日志读写（镜像 LogAuditModule）
├── service/PublishService.java    版本 5 方法接口
├── service/ReviewService.java     审核 6 方法接口
├── service/LogService.java        日志 8 方法接口（契约 KbOperationLogService 的 Java 面）
├── service/impl/PublishServiceImpl.java  版本号分配/三查询/流转+旁写历史
├── service/impl/ReviewServiceImpl.java   审核提交/终态覆写/三查询
├── service/impl/LogServiceImpl.java      双入口写入/降级/分页统计
├── entity/KbVersion.java          五态版本实体（tags JSON 双兼容 setter）
├── entity/KbReviewTask.java       三态审核任务实体
├── entity/KbPublishHistory.java   发布历史实体（与 knowledge 模块同表双实体之一）
├── entity/KbLog.java / KbUpdateLog.java  两张日志表实体
└── mapper/KbVersionMapper.java 等 5 个空 BaseMapper
fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/
├── controller/PublishManageController.java  发布管理 13 端点（@KbAuth+@Loggable 全覆盖）
├── service/PublishManageService.java + impl/PublishManageServiceImpl.java  14 方法：历史2写/计划3/推导3/重置3/更新日志分页
├── entity/KbPublishHistory.java + mapper/KbPublishHistoryMapper2.java  同表双实体的第二份（类名避让加 2）
├── entity/KbPublishPlan.java + mapper/KbPublishPlanMapper.java  发布计划
├── entity/KbResetConfig.java + mapper/KbResetConfigMapper.java  重置配置
└── entity/KbReviewStrategy|KbReviewTemplate|KbReviewNode|KbComplianceRule|KbQualityRule.java + 同名 Mapper  DDL-only 死代码（零消费者）
fastrag-modules/fastrag-platform/src/main/java/com/fastrag/module/platform/
└── controller/ConfigManageController.java:70-90  发布/审核开关六键写读（消费点，归属 platform-config）
init-scripts/schema.sql:303-358,810-902,927-935  12 张表 DDL（7 运行时 + 5 预留）
fastrag-bootstrap/src/main/java/com/fastrag/config/SchemaInitializer.java:102-121  kb_publish_history 第二份等价 DDL
```

跨域消费方（非本特性文件）：RetrievalServiceImpl/UpdateRemindServiceImpl（retrieval，直用 Mapper）、HomeController/UnifiedLogServiceImpl（operation，直用 Mapper）、graph-eval 3 控制器+3 服务、IngestionConsumer/GraphBuildConsumer/FileController/FolderController/KbController/KbCategoryController/QaPairController/ParseStrategyController/FileServiceImpl/QaPairServiceImpl（knowledge，LogService 直调）。

## 2. Interface Implementations

### KbOperationLogService（from contract: publish-review.KbOperationLogService → design: LogAuditModule）

**Logic Flow:**
1. `addLog` 兼容入口：1. parseLogCategory/parseActionType 将字符串 try-valueOf 解析枚举（失败→null）；2. 委托七参枚举入口（status 固定 "success"）。
2. `addLog` 枚举入口：1. new KbLog 填 8 字段（category/action 存枚举 name()；status null→"success"；extra 原样；timestamp=now）；2. TRY insert；3. CATCH 任意异常 → log.error 后**吞掉返回**（LogServiceImpl.java:106-122）。
3. `addUpdateLog`：同 2 的字段映射（updateType/oldValue/newValue），CATCH 吞掉（:125-140）。
4. `listLogs`：1. wrapper eq(kbId) + 条件 eq(category)；2. IF keyword 非空 THEN and(like(target).or().like(detail).or().like(operator))；3. selectPage(Page{page,pageSize}) 按 timestamp 倒序；4. RETURN PageResult.of(records,total,page,pageSize)（:53-64）。
5. `countByCategory`：selectCount(kbId) → total；再对 LogCategory 三个枚举值逐个 selectCount → LinkedHashMap{total,operation,retrieval,publish}（:67-77）——共 4 次 COUNT 查询。
6. `listUpdateLogs`：eq(kbId) + 条件 eq(updateType)，timestamp 倒序全量（:80-84）。
7. `markUpdateLogRead`：**空方法体**（:145-147）。
8. `KbLogAspect.around`（声明式入口）：1. 前置解析：operator/userId（SecurityUtil，异常→"system"）、kbId（参数名 kbId 匹配→首个非空 String 参数→"unknown"）、target/detail（SpEL 以 #pN/#参数名 双变量法求值，失败或非 # 开头回落 fallback）、ip、module（ActionType 前缀表 26 项→category 表 3 项→"其他"）；2. TRY pjp.proceed() → addLog(status=success) → publishEvent(SysAuditLogEvent success) → RETURN 业务结果；3. CATCH → detail 空时以异常消息兜底 → addLog(status=failed) → publishEvent(failed) → **RETHROW 原异常**（KbLogAspect.java:91-118）。

**Module Interactions:**
- Calls：KbLogMapper/KbUpdateLogMapper（MyBatis-Plus）、ApplicationEventPublisher（SysAuditLogEvent → operation 侧监听）、SecurityUtil（fastrag-security）、IpUtil（common）。
- Called by：LogController 6 端点、KbLogAspect；跨模块直调——knowledge（IngestionConsumer/GraphBuildConsumer/FileController/FolderController/KbController/KbCategoryController/QaPairController/ParseStrategyController/FileServiceImpl/QaPairServiceImpl/PublishManageController）、graph-eval 6 类；跨模块绕过直用 Mapper——retrieval 2 类、operation 2 类。
- Shared state：表 kb_log、kb_update_log（读写主表）；无缓存/队列/主题。

**Decisions & Deviations:** 与契约的偏离登记：① markUpdateLogRead 契约名承诺已读持久化、实现为空（已读归前端，LogServiceImpl.java:146 注释自述）；② 4 处消费方绕过契约直用 Mapper（口径漂移风险，见 contract 登记债务）；③ countByCategory 每分类一次 COUNT（4 次往返），大数据量下可合并为 GROUP BY——现状取舍为代码简单性。符合项：写失败降级、失败路径 RETHROW、枚举容错均与契约 Error modes 一致。

**Parent Trace:** LogServiceImpl+KbLogAspect → 契约 KbOperationLogService → design.LogAuditModule（缝 2/缝 3）→ REQ-F01~F07。

### PublishService（from contract: 无契约 → design: VersionModule）

**Logic Flow:**
1. `createVersion(kbId,data)`：1. 强制 data.kbId=路径 kbId、publishStatus="draft"；2. 查该 KB version 最大行（orderByDesc(version) LIMIT 1）；3. data.version = max+1 或 1；4. insert → RETURN data。**不触碰 fileCount/chunkCount/tags**（请求体原样落库）；无事务、无锁（PublishServiceImpl.java:37-43）。
2. `transitionStatus(kbId,vid,status)`：1. selectById(vid)；2. IF null THEN return（静默）；3. set publishStatus=status → updateById（**无状态合法性校验、不比对 kbId**）；4. IF status=="published" THEN new KbPublishHistory{kbId, knowledgeId=versionId, version, publishType="publish", status="published", operator=createdBy, publishedAt=now} → insert（:44-56）。
3. `listVersions`：eq(kbId) 按 createdAt 倒序 selectList（:34）。`getLatestVersion`：orderByDesc(version) LIMIT 1（:35）。`getPublishedVersion`：eq(publishStatus,"published") LIMIT 1（:36）。
4. 状态转移图（实况）：draft/pending_review/approved/published/rejected 五态**全连通**（任意→任意）；published 边附带历史写入副作用。

**Module Interactions:**
- Calls：KbVersionMapper、KbPublishHistoryMapper（publish 模块份）。
- Called by：PublishController versions 族 5 端点。无其他调用方。
- Shared state：表 kb_version、kb_publish_history（与 PublishManageServiceImpl **同表双写**，填充口径不同——见缝 4）。

**Decisions & Deviations:** ① 无状态机——spec REQ-A06 按现状登记为「任意态互转」；② 版本号分配为「查后插」非原子，并发可重号（表无 uk 兜底）；③ 流转不校验版本归属 KB（跨库 vid 可流转）；④ published 历史的 operator 取 createdBy 而非当前操作人、且不经 @Loggable（该副作用无审计行）。均与上游 REQ 现状口径一致，无超出登记的偏离。

**Parent Trace:** PublishServiceImpl → design.VersionModule → REQ-A01~A06（REQ-A05 经缝 4 关联 REQ-B01）。

### ReviewService（from contract: 无契约 → design: ReviewModule）

**Logic Flow:**
1. `submitForReview(kbId,versionId,applicant)`：new KbReviewTask{kbId,versionId,applicant,status="pending",createdAt=now} → insert → RETURN（**不校验 versionId 存在、不填 kbName/reviewer/version**，ReviewServiceImpl.java:29）。
2. `approveReview/rejectReview(id,comment)`：selectById → IF null THEN return（静默）→ set status=approved|rejected、comment、reviewedAt=now → updateById（**无前置状态校验**，终态可覆写，:30-31）。
3. `listReviews(kbId)`：条件 eq(kbId) 按 createdAt 倒序；`getPendingReviews`：eq(status,"pending") 全库（:27-28）；`getReview`：selectById（:32）。

**Module Interactions:**
- Calls：KbReviewTaskMapper。Called by：PublishController reviews 族 5 端点。
- Shared state：表 kb_review_task。与 kb_version **零代码交互**（联动由前端编排两次调用达成）。

**Decisions & Deviations:** ① 审核人与版本号字段（reviewer/version）无写入方，恒 NULL——api 消费者不能依赖；② 不联动版本状态为 spec REQ-D05 登记的设计事实而非缺陷。无其他偏离。

**Parent Trace:** ReviewServiceImpl → design.ReviewModule → REQ-D01~D05。

### PublishManageService（from contract: 无契约 → design: PublishManageModule）

**Logic Flow:**
1. `publish(kbId,kid,history)`：1. 强制 kbId/knowledgeId、publishType="publish"、status="published"、publishedAt=now；2. IF version==null THEN version=1；3. insert → RETURN（PublishManageServiceImpl.java:25-34）。
2. `revoke(kbId,kid)`：new history{publishType="revoke",status="revoked",publishedAt=now}（version 不设）→ insert → RETURN（:35-44）。
3. `listPublishHistory`：条件 eq(kbId)+eq(knowledgeId)，createdAt 倒序（:19-24）。
4. `createPlan(plan)`：IF executionStatus==null THEN "pending" → insert（:48-52）。`listPlans`：条件 eq(kbId) 倒序（:56-61）。`getPlanExecution`：selectById（:53-55）。**无执行器**——无任何组件迁移 executionStatus 或写 successCount/failCount/executionDetail/executedTime。
5. `getStrategyEffect(kbId)`：3 次 selectCount → {totalPublish, successCount(publish+published), revokeCount(publishType=revoke)}（:62-73）。
6. `getOnlineVersion`：eq(kbId)+eq(status,"published")[+eq(knowledgeId)] 按 publishedAt 倒序 LIMIT 1 → {online:非空, version:行|null}（:76-85）。`getOfflineVersion`：eq(kbId)+**ne(status,"published")** LIMIT 10 → {offlineCount, versions}（:86-95）。
7. `saveResetConfig(c)`：IF canReset==null THEN 0；IF maxResetCount==null THEN 3；IF id 非空 THEN updateById ELSE insert → RETURN（:101-107）。
8. `resetKnowledge(kbId,kid)`：1. 查该条目最近 published（eq 三键+orderByDesc(publishedAt) LIMIT 1）；2. IF 空 THEN throw RuntimeException("没有可回退的已发布版本")（→全局兜底 500 语义）；3. new history{publishType="publish",status="published",version=源.version,publishedAt=now,operator="system"} → insert（**不读 kb_reset_config、无次数计数、不标 revoked 任何行**，:108-129）。
9. `getKnowledgeUpdateLogs(kbId,page,pageSize)`：eq(kbId) 倒序 + **手拼 "LIMIT x OFFSET y"** → list+count 组装裸 Map{list,total,page,pageSize}（:132-142）。

**Module Interactions:**
- Calls：KbPublishHistoryMapper2、KbPublishPlanMapper、KbResetConfigMapper（knowledge 模块）、KbUpdateLogMapper（**跨模块直引 publish 模块 Mapper**）。
- Called by：PublishManageController 13 端点。
- Shared state：表 kb_publish_history（与 publish 模块同表双写）、kb_publish_plan、kb_reset_config、kb_update_log（只读）。

**Decisions & Deviations:** ① publish/revoke 是「事实追加」语义——撤销/重置都不修改旧行，全表只追加；② 同表双实体（KbPublishHistoryMapper vs Mapper2）为类名冲突的权宜，两实体字段一致；③ getKnowledgeUpdateLogs 返回裸 Map 非 PageResult（字段名兼容，类型偏离统一约定，债务⑧）；④ resetKnowledge 注释自述「标记 revoked 再复制」但实现只做了复制（:109-110 注释与 :118-128 实现不一致——以实现为准）；⑤ kb_reset_config 与执行路径零交互（REQ-E04）；⑥（2026-09-24 裁决补登）前端创建计划传 `scheduledAt` 而后端绑定字段为 `scheduledTime`（PublishPanel.vue:239 vs KbPublishPlan.java:29）——Spring Boot 默认忽略未知属性，计划时间静默丢弃，kb_publish_plan.scheduled_time 实际亦恒 NULL；修复方向=前端改传 scheduledTime（REQ-C01/C03 关联）。

**Parent Trace:** PublishManageServiceImpl → design.PublishManageModule → REQ-B01~B06、C01~C03、E01~E04。

### PublishConfigGate（from contract: 无（消费点登记）→ design: PublishConfigGate）

**Logic Flow:** 六个 PUT 端点（publish-switch/review-switch/review-flow/review-flow-binding/publish-settings/review-settings）统一 `saveConfig(键, toJson(body), 类型, 描述, "admin")` 落 sys_config；两个 GET（publish-status/review-status）`getConfig(键)` 返回 configValue 或 "{}"（ConfigManageController.java:72-86）。无其他逻辑。

**Module Interactions:** Calls ConfigManageService（platform）；Called by 前端设置页；Shared state：sys_config 表六键。**无 publish/review 后端流程消费这些键**（grep 实测）。

**Decisions & Deviations:** 开关只存不读为 REQ-D06 登记现状；配置实体的归属契约在 `## platform-config` 链（本链不重复定义 sys_config 结构）。

**Parent Trace:** ConfigManageController:70-90 → design.PublishConfigGate（缝 5）→ REQ-D06。

## 3. Testing

引用 tdd/publish-review.md 七切片。**现有测试覆盖：零**——fastrag-publish 模块无 src/test 目录（conventions.md §2 与 find 实测一致），fastrag-knowledge/fastrag-platform 的既有测试不触及本特性任何类。**缺口：全部**——S1 版本状态机矩阵与并发创建、S2 审核终态覆写、S3 同表双写一致性、S5 重置回退、S6 日志双路径与降级均无任何自动化覆盖；S6 的消费方回归可部分复用 RetrievalServiceImplTest（retrieval 链既有测试缝，其中检索日志写入为副作用断言点）。实施时按该文件 §4 从 S6 Tracer Bullet 起步。
