# 发布、版本与知识审核（publish-review）— 测试设计

> 本特性仅一个契约接口（publish-review.KbOperationLogService，见 contract/interfaces.md `## publish-review`），其余按端点排除口径以 design §2 公共接口分组切片。**状态机非法迁移（REQ-A06/D04）与并发发布（版本号分配、历史双写）是重点用例族**（任务书指派）。mock 只允许在系统边界（时间用可注入 Clock 或测试库真实时间、SecurityContext 用测试鉴权上下文、SysAuditLogEvent 用 @RecordApplicationEvents 捕获），禁 mock 内部模块；MySQL 用测试库真表（建表语句取 schema.sql 对应节）。

## 1. 切片规划

| 切片 | 接口组 | AFK/HITL | 说明 |
|---|---|---|---|
| S0 现状口径裁决前置 | — | **HITL** | 三项 GAP 裁决决定期望值：①非法迁移是否修为拒绝（现状放行）；②审核开关是否落地为后端 gate；③双发布口径是否收敛。裁决前 S1/S3 按现状口径断言 |
| S1 版本状态机与并发发布 | VersionModule（PublishService + versions 端点） | AFK | **重点用例族 A**：五态流转矩阵、动作名映射、并发 createVersion |
| S2 审核任务生命周期 | ReviewModule（ReviewService + reviews 端点） | AFK | **重点用例族 B**：终态覆写、与版本零联动边界 |
| S3 发布历史与推导视图 | PublishManageModule（publish/revoke/online/offline/strategy-effect） | AFK | 同表双写一致性 + 三视图推导 |
| S4 发布计划登记 | PublishManageModule（createPlan/listPlans/getPlanExecution） | AFK | 含「无执行器」现状断言（到期不变迁） |
| S5 知识重置 | PublishManageModule（reset-configs/reset） | AFK | 复制语义 + 无可回退错误路径 + 配置脱钩断言 |
| S6 日志契约（跨模块） | publish-review.KbOperationLogService（contract）+ @Loggable 切面 | AFK | 双路径记账、降级、消费方回归（retrieval/operation） |
| S7 鉴权面回归 | PublishManage 端点 @KbAuth；PublishController 无鉴权现状 | AFK | 半覆盖债务的可观测断言 |

## 2. 测试用例

### S1 VersionModule（contract: 无独立契约；design: VersionModule）

| 测试名 | Given | When | Then（断言） | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [创建版本] 库内已有 2 版时 [分配 3 号 draft] | KB 内已有 version 1,2 | createVersion(kbId,{name}) | 返回 version=3、publishStatus=draft；库中可查 | A01 | 无 |
| [创建版本] 空 KB 时 [分配 1 号] | KB 无版本 | createVersion | version=1 | A01 | 无 |
| [创建版本] 并发双建时 [现状可重号]（重点族 A） | 库内 max=1；两线程同时 createVersion | 并发 createVersion×2 | 现状断言：可能出现两条 version=2（无唯一约束，债务④登记；修复后应一拒一等） | A01 | 无（真实测试库行锁行为） |
| [查询版本] 混合状态库时 [latest 取号最大而非最新建] | version 2 先建、version 1 后建 | getLatestVersion | 返回 version=2 的记录 | A03 | 无 |
| [查询版本] 无 published 时 [published 查询返回 null] | 全 draft 库 | getPublishedVersion | null（HTTP 层 success+data:null） | A03 | 无 |
| [流转] 传 action=submit 时 [映射 pending_review]（重点族 A） | draft 版本 | transition{action:"submit"} | publishStatus=pending_review | A04 | 无 |
| [流转] 全动作名矩阵时 [逐一落对应状态]（重点族 A） | draft 版本×5 | submit/approve/publish/reject/draft | 分别=pending_review/approved/published/rejected/draft | A04 | 无 |
| [流转] 传未知值时 [原样作状态落库] | draft 版本 | transition{targetStatus:"frozen"} | publishStatus=frozen（无枚举校验，现状口径） | A04/A06 | 无 |
| [流转] rejected→publish 时 [现状放行并写历史]（非法迁移现状） | rejected 版本 | transition{action:"publish"} | publishStatus=published 且 kb_publish_history 新增一条（修复裁决后此断言反转为拒绝+无历史） | A05/A06 | 无 |
| [流转] 到 published 时 [写 operator=createdBy 的历史] | createdBy=alice 的版本 | transition{action:"publish"} | history：publishType=publish、status=published、operator=alice、publishedAt 非空 | A05 | 无（时间用测试库真实时间） |
| [流转] 版本 id 查无时 [静默成功无历史] | 不存在 vid | transition | HTTP success；history 表无新增 | A06 | 无 |
| [并发发布] 两版本同时流转 published 时 [历史两条不丢]（重点族 A） | 同 KB 两个 draft 版本 | 并发 transition publish×2 | history 恰增 2 条 publish 记录；两版本均 published | A05 | 无 |

### S2 ReviewModule（contract: 无独立契约；design: ReviewModule）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [提交审核] 合法入参时 [落 pending 单] | kbId/versionId/applicant 齐备 | submitForReview | 返回任务 status=pending、createdAt 非空 | D01 | 无 |
| [审批] pending 任务批准时 [记 comment 与时间] | pending 任务 | approveReview{comment:"ok"} | status=approved、comment=ok、reviewedAt 非空 | D02 | 无 |
| [审批] 已批准再驳回时 [现状覆写成功]（重点族 B/非法迁移） | approved 任务 | rejectReview{comment:"反悔"} | 现状断言：status=rejected（终态可覆写；修复后应拒绝） | D04 | 无 |
| [审批] 任务 id 查无时 [静默无副作用] | 不存在 id | approveReview | 不抛异常、表无变化 | D04 | 无 |
| [联动] 批准版本关联任务时 [版本状态不变]（重点族 B） | versionId 指向 draft 版本的 pending 任务 | approveReview | kb_version.publishStatus 仍 draft（零联动边界） | D05 | 无 |
| [查询] 无 pending 任务时 [待审列表为空] | 全部终态 | getPendingReviews | 空列表 | D03 | 无 |
| [开关] review_switch 配置为关时 [提交审核仍成功]（GAP②现状） | sys_config review_switch={"enabled":false} | submitForReview | 任务照常创建（开关无后端 gate；若 S0 裁决落地 gate 则反转为拒绝） | D06 | 无 |

### S3 PublishManageModule 历史与视图（contract: 无独立契约；design: PublishManageModule）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [发布条目] 未传版本号时 [落 version=1] | 请求体无 version | publish(kbId,kid,body) | history：publish/published、version=1、publishedAt 非空 | B01 | 无 |
| [发布条目] 显式传 version=5 时 [原样落库不校验] | 请求体 version=5（无对应 kb_version 行） | publish | version=5 落库（弱引用不校验） | B01 | 无 |
| [撤销发布] 已发布条目撤销时 [追加 revoke 行原行不变] | 既有 published 行 | revoke | 新行 revoke/revoked；旧行逐字段不变 | B02 | 无 |
| [线上版本] 有 published 历史时 [返回最近一条] | 两条 published（时间先后） | getOnlineVersion | online=true 且为 publishedAt 较新者 | B04 | 无 |
| [线上版本] 仅 revoke 历史时 [online=false] | 只有 revoked 行 | getOnlineVersion | online=false、version=null | B04 | 无 |
| [线下版本] 混合历史时 [返回非 published 最近 10 条] | 12 条 revoked + 1 条 published | getOfflineVersion | offlineCount=10、无 published 行混入 | B04 | 无 |
| [策略效果] 三类历史齐备时 [三计数分账] | 2 publish+1 revoke+1 其他 status | getStrategyEffect | totalPublish=4、successCount=2、revokeCount=1 | B05 | 无 |
| [双口径] 版本流转与条目发布同天双写时 [两行互不干扰]（缝 4） | 既有版本 V1 | V1 transition publish + publish(kbId,"itemA") | history 两条：operator 分别=createdBy 与请求体；knowledge_id 分别=V1.id 与 "itemA" | A05/B01 | 无 |

### S4 发布计划（contract: 无独立契约；design: PublishManageModule）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [创建计划] 未传执行状态时 [落 pending] | body 无 executionStatus | createPlan | executionStatus=pending、knowledgeIds JSON 原样 | C01 | 无 |
| [创建计划] 缺 name 时 [DB 约束拒绝] | name=null | createPlan | 插入失败（NOT NULL，schema.sql:831）——业务错误语义登记 | C01 | 无 |
| [计划到期] scheduledTime 已过时 [状态仍 pending]（现状断言） | scheduledTime=过去时刻的计划 | 等待任意时间后 getPlanExecution | executionStatus 仍 pending、successCount=0（无执行器；S0③裁决落地后改为断言执行迁移） | C03 | 无 |
| [查询计划] 空 KB 时 [空列表] | 无计划 | listPlans | 空列表 | C02 | 无 |

### S5 知识重置（contract: 无独立契约；design: PublishManageModule）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [重置] 有 published 历史时 [复制为新行 operator=system] | 条目最新 published 行 version=3 | resetKnowledge | 新行：publish/published、version=3、operator=system；旧行不变 | E02 | 无 |
| [重置] 无 published 历史时 [业务失败 500 兜底]（现状口径） | 仅 revoked 行 | resetKnowledge | RuntimeException「没有可回退的已发布版本」→ HTTP 200 + serverError | E03 | 无 |
| [重置] canReset=0 时 [现状仍执行]（配置脱钩断言） | kb_reset_config canReset=0 | resetKnowledge | 现状断言：重置成功（配置不消费；REQ-E04 修复后应拒绝） | E04 | 无 |
| [重置] 超过 maxResetCount 次后 [现状仍执行] | maxResetCount=1 且已重置 2 次（历史含 3 条 published） | resetKnowledge | 现状断言：第 3 次仍成功（无计数校验） | E04 | 无 |
| [重置配置] 保存无 id 配置时 [插入含缺省] | body 无 id | saveResetConfig | canReset=0、maxResetCount=3 落库 | E01 | 无 |
| [重置配置] 携带既有 id 保存时 [更新原行] | 已存在配置行 | saveResetConfig{id} | updateById 生效、无新行 | E01 | 无 |

### S6 KbOperationLogService（contract: publish-review.KbOperationLogService）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [记日志] 枚举入口写成功路径时 [落 success 行] | 合法七参 | addLog(...,success,null) | kb_log 行：category/action 存枚举名、status=success、timestamp 非空 | F03 | 无 |
| [记日志] 兼容入口传未知枚举串时 [落 null 字段不报错] | category="hack" | addLog(String 重载) | category=null、其余字段完整 | E_ENUM_UNKNOWN | 无 |
| [记日志] 表被人为置错时 [宿主照常返回] | 临时改名 kb_log 表 | addLog + 断言不抛 | 无异常上抛；error 日志存在（恢复表名） | E_LOG_WRITE_FAILED/F04 | 无（真实测试库） |
| [切面] 标注方法正常返回时 [写 success + 发审计事件] | @Loggable 测试方法 | 调用后查库+事件 | status=success 行；@RecordApplicationEvents 捕获 SysAuditLogEvent（module 按前缀表） | F03 | 事件捕获用 Spring 测试设施 |
| [切面] 标注方法抛异常时 [写 failed 行且异常上抛]（双路径） | 抛 IllegalStateException 的 @Loggable 方法 | 调用捕获异常 | failed 行 detail=异常消息；原异常类型原样 | F03 | 无 |
| [切面] SpEL 引用不存在参数时 [回落字面值不断业务] | target="#noSuchVar" | 调用 | 业务返回正常；target 落 fallback 提取值 | E_SPEL_EVAL_FAILED | 无 |
| [查询日志] keyword 命中 detail 时 [三列 OR 模糊] | operator 含词、target/detail 含词的三行 | listLogs(keyword) | 三行齐出；timestamp 倒序；PageResult 四字段 | F01 | 无 |
| [统计] 三分类混合时 [total 与分类计数自洽] | operation×2、retrieval×1、publish×1、category="x"×1 | countByCategory | total=5、三分项 2/1/1（"x" 仅入 total） | F02 | 无 |
| [更新日志] 带 oldValue/newValue 写入时 [对比可查] | — | addUpdateLog(old,new) 后 listUpdateLogs(type) | 行含 old/new；type 过滤生效；倒序 | F05 | 无 |
| [消费方回归] retrieval 直写检索日志时 [落库可被本契约查出] | RetrievalServiceImpl 检索一次（检索链 mock Milvus/Embedding，回归 `## retrieval` 缝） | listLogs(category=retrieval) | 检索日志行出现（共享表面回归） | F07 | Milvus/Embedding/Rerank（上游链边界） |

### S7 鉴权面（contract: 无；design: 缝 1）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [鉴权] viewer 调发布管理写端点时 [拒绝] | viewer 角色会话 | POST /publish/{kid} | 403（@KbAuth editor） | B06 | 鉴权上下文（security 测试设施） |
| [鉴权] viewer 调发布管理读端点时 [放行] | viewer 会话 | GET /publish/history | 200 | B06 | 同上 |
| [鉴权] 匿名调 PublishController 版本端点时 [现状放行]（债务①现状断言） | 无会话 | GET /api/kb/{kbId}/versions | 现状：非 403（若 S0 修复裁决落地则反转为 401/403） | A 域债务 | 无 |

## 3. 追溯（抽样）

| 测试 | 契约接口 | Design 模块 | Spec REQ |
|---|---|---|---|
| S1 并发双建重号 | 无（端点排除）→ PublishService.createVersion | VersionModule（缝 4） | REQ-A01（债务④） |
| S1 rejected→publish 放行 | 无 → PublishService.transitionStatus | VersionModule | REQ-A05/A06 |
| S2 批准不联动版本 | 无 → ReviewService.approveReview | ReviewModule | REQ-D05 |
| S3 双口径同表双写 | 无 → PublishManageService.publish | PublishManageModule（缝 4） | REQ-A05↔REQ-B01 |
| S5 canReset=0 仍执行 | 无 → PublishManageService.resetKnowledge | PublishManageModule | REQ-E04 |
| S6 切面 failed 行+异常上抛 | publish-review.KbOperationLogService.addLog | LogAuditModule（缝 2） | REQ-F03 |
| S6 消费方回归 | publish-review.KbOperationLogService（消费面） | LogAuditModule（缝 3） | REQ-F07 |

## 4. 待实施说明
- 孤儿 REQ 豁免：REQ-F06（更新日志已读标记 markUpdateLogRead 为空实现、由前端本地处理后端不存储——占位/负面登记，无下游承接为有意；见 implement 文档）

- 红绿循环未执行（本任务不写测试代码）；实施时从 S6（契约切片）的 Tracer Bullet「枚举入口 success 行」起步——它同时验证测试库建表与 Spring 上下文装配，再向 S1 重点族展开。
- fastrag-publish 模块当前零测试（conventions.md §2 实测），本设计即补测蓝图；S6/S7 需要Spring 上下文切片测试（@SpringBootTest + 测试库），S1~S5 可用 @MybatisPlusTest 级别的窄切片。
- 三项 S0 裁决（非法迁移、开关 gate、双口径收敛）任一落地时，对应「现状口径」断言须同步反转为修复口径，并在本文件登记反转日期。
