# 知识库管理（kb-core）— 测试设计

> onboard 模式：**不写测试代码**（本链禁止改源码），产出测试设计。执行时按 §4 从 Tracer Bullet 起步。
> mock 纪律：只允许在真缝/系统边界 mock——Milvus/Neo4j(GraphStore)/MinIO/RabbitMQ/时间/iam 判定缝（KbAccessChecker/KbAclService 为 security 声明接口，属合法缝）；**禁 mock 内部模块**（TagRelationHelper、KbCategoryMapper 等不得 mock，用真实实例+内存库）。
> 现状：fastrag-knowledge 测试全为 parser/chunking/strategy（22 个测试类实读），**本特性零测试覆盖**，全部用例为新增。

## 1. 切片规划（HITL 在前）

| 切片 | 覆盖契约接口 | AFK/HITL | 理由 |
|---|---|---|---|
| S1 删除级联全清与降级 | kb-core.KbLifecycleService.delete | **HITL** | 跨 Milvus/Neo4j/MinIO 三方真实清理的结果判定与「部分失败可接受」的产品决策需人确认；自动化部分（DB 侧清空、mock 边界降级）可先跑 |
| S2 生命周期装配 | kb-core.KbLifecycleService.create/update/get | AFK | 纯 DB 行为，内存库可验 |
| S3 列表可见性与筛选 | kb-core.KbLifecycleService.list/getCategories | AFK | iam 判定缝 mock 三态即可 |
| S4 标签一致性 | kb-core.TagConsistencyService 全方法 | AFK | 不变式驱动，纯 DB |
| S5 标签本体与全局唯一名 | kb-core.TagConsistencyService（标签 CRUD 面） | AFK | 含手动判定缝 mock（KbAccessChecker） |
| S6 分类组织隔离与计数 | Design.CategoryModule（浅模块，测权限与过滤规则） | AFK | 控制器层测试（MockMvc/直调） |

## 2. 测试用例（逐接口）

### S1/S2/S3：KbLifecycleService（contract: kb-core.KbLifecycleService）

| 测试名 | Given | When | Then（=断言） | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| 创建知识库仅名称时缺省装配生效 | 已登录用户（orgId=org1） | create(name="库A") | kb 落库：permission=private、type=personal、graphAutoBuild=0、usedSize/totalSize=0、orgId=org1；kb_acl 出现 (kbId, creator, owner) 行 | REQ-A01/A02/A03 | 无（时间固定可免） |
| private 可见性派生 personal、team 可见性派生 team | 同上 | create(permission="team") | type=team | REQ-A04 | 无 |
| 更新只覆盖非 null 字段 | 既有库（description="旧"、retrievalConfig=JSON 串） | update(仅 description="新") | description 变、retrievalConfig 原值不变、type/creator/orgId 不变 | REQ-A05 | 无 |
| 详情不存在的库报运行时异常 | 无该 id | get("missing") | RuntimeException（HTTP 200 + code=500 兜底语义），非 404——锁死现状债务行为直至修复决策 | REQ-A05（错误面） | 无 |
| 删除级联清空 DB 全部衍生数据 | 库含 2 文件（1 个软删 deleted_at 非空）、各 3 分片、2 QA 对、KB 级+文件级标签关联 | delete(kbId) | kb_chunk/kb_file（含软删）/kb_qa_pair/kb_tag_relation(targetType=kb 与 file)/kb 无该库行；被关联标签 usage_count 归 0；零引用标签本体保留 | REQ-A06 | Milvus/GraphStore/MinIO（verify 调用） |
| 删除时外部清理失败不阻断 | 同上，Milvus.deleteByFileId 抛 RuntimeException | delete(kbId) | 不抛异常；warn 记账；DB 侧全部清理成功 | REQ-A07 | Milvus（仅此一个抛错） |
| 删除逐文件清理向量与对象 | 库含 2 文件（objectKey=obj1/obj2） | delete(kbId) | Milvus 收到 2 次 deleteByFileId(collection=kb_{id})；GraphStore 收到 2 次 deleteFileGraph；MinIO 收到 objectKey 删 + 2 次 prefix 删 | REQ-A06 | Milvus/GraphStore/MinIO |
| API Token 列表全局可见 | 判定缝：token 身份 | list(1,20) | 不调 getAccessibleKbIds；total=全库数 | REQ-B02 | 判定缝（security 接口） |
| 普通用户列表=组织∪ACL 且空集短路 | 判定缝：getAccessibleKbIds→[kb1,kb2] / →[] | list | 前者仅含 kb1/kb2 且按 createdAt 倒序；后者返回 {list:[],total:0} 且 mapper 未发查询 | REQ-B01/B02 | 判定缝 |
| keyword 与 category 双条件叠加 | 可见 3 库（名称/描述含"报告"混杂，category 两值） | list(kw="报告", cat=cat1) | SQL 含 name like OR description like 且 category=cat1；结果为交集 | REQ-B03 | 判定缝 |
| 分类聚合只统计可访问库 | 同上 | getCategories() | 返回按 category 值分组 {id,name,count}，仅可访问库参与 | REQ-B04/B05 | 判定缝 |

### S4/S5：TagConsistencyService（contract: kb-core.TagConsistencyService）

| 测试名 | Given | When | Then（=断言） | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| 替换打标差量更新计数 | 目标已有标签 [t1,t2]，库中另有 t3 | replaceTags(target,[t2,t3]) | t1 关联删+计数-1、t2 不动、t3 关联增+计数+1；无 uk 冲突异常 | REQ-D04 | 无 |
| 增量添加幂等 | t1 已关联目标 | addTag(target,t1) | 关联数不变、usage_count 不变、无异常 | REQ-D04 | 无 |
| 增量移除不误减 | t1 未关联目标（usage=1 来自别处） | removeTag(target,t1) | usage_count 仍为 1 | REQ-D04 | 无 |
| 并发重复插入冲突容忍 | uk_tag_target 已存在同键行（并发窗口模拟：insert 抛 DuplicateKey） | addTag(target,t1) | 捕获后跳过，usage_count 不重复累加 | REQ-D04 | 无（模拟重复键即可） |
| 被引用标签拒绝删除 | t1 被 2 目标引用 | deleteTag(t1) | 400 语义「仍被 2 个目标引用」，标签与关联均存留 | REQ-D03 | 无 |
| usage_count 异常非零二次拦截 | t2 关联 0 但 usage_count=3 | deleteTag(t2) | 400「usage_count 异常非零」 | REQ-D03 | 无 |
| 零引用标签删除成功 | t3 关联 0、usage=0 | deleteTag(t3) | kb_tag 无 t3 行 | REQ-D03 | 无 |
| 标签重名跨 KB 拒绝 | KB-A 有标签"机密" | KbTagController.create(kbId=KB-B, name="机密") | 400「标签名称已存在」；DB 无重名行 | REQ-D01 | KbAccessChecker（返回 editor） |
| 标签写入需归属库 editor | 判定缝：resolveRole→viewer / →null | KbTagController.create / update / delete | 均 403「知识库权限不足（需要编辑权限）」 | REQ-D02 | KbAccessChecker |
| API Token 标签写入放行 | 身份 userId=api-token:x | create | 不调 resolveRole，创建成功、createdBy 记录身份 | REQ-D02 | 判定缝（不触发即可） |
| KB 打标自动建标签并计数 | 新库，create(tags=["新标"]) | create | kb_tag 新增（kbId=该库，usage=1）+ 关联一行；kb.tags JSON 同步 | REQ-D05 | 无 |
| ⚠已知缺陷锁定用例 | 既有库已打标 [t1]，再次 update(tags=[t1]) | update | **当前实现 usage_count 虚增（先删后查死代码）**——本用例按契约断言 count 不变，预期红；修复 syncTagRelations 后转绿（见 implement §2 偏差 1） | REQ-D04/D05 | 无 |

### S6：CategoryModule（浅模块，规则测试；无契约）

| 测试名 | Given | When | Then（=断言） | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| 无组织用户列表为空 | 会话 orgId=null | GET list | 空数组，不查询 | REQ-C01 | 无 |
| 分类计数组织一致 | org1 有分类 c1，kb(category=c1,org1)×2、kb(category=c1,org2)×1 | org1 用户 list | count=2；API Token list count=3 | REQ-C05 | 无 |
| 创建强制归属本组织 | org1 用户，请求体 orgId=org2 | POST create | 落库 orgId=org1（请求值被覆盖） | REQ-C02 | 无 |
| 无 orgId 创建被拒 | API Token，orgId 空 | POST create | 400「请选择所属组织」 | REQ-C02 | 无 |
| 跨组织更新/删除 403 且不可迁移 | 分类属 org2 | org1 用户 PUT/DELETE | 403；PUT 即便带 orgId=org1 也回写 org2 | REQ-C03 | 无 |
| 写分类需平台权限键 | 无 kb:category:create | POST | 403（@perm.has 拒绝） | REQ-C04 | 无（PermissionChecker 真实或缝上模拟权限集） |
| usage 对不存在分类返回 0 | id 不存在 | GET /{id}/usage | {count:0} 非 404 | REQ-C05 | 无 |

## 3. 追溯（抽样完整链）

| 测试 | 契约接口 | Design 模块 | Spec REQ |
|---|---|---|---|
| 删除级联清空 DB 全部衍生数据 | kb-core.KbLifecycleService | KnowledgeBaseModule | REQ-A06 |
| 删除时外部清理失败不阻断 | kb-core.KbLifecycleService | KnowledgeBaseModule（删除级联存储缝） | REQ-A07 |
| 普通用户列表=组织∪ACL | kb-core.KbLifecycleService | KnowledgeBaseModule（KB 判定缝消费） | REQ-B02 |
| 被引用标签拒绝删除 | kb-core.TagConsistencyService | TagModule | REQ-D03 |
| KB 打标自动建标签并计数 | kb-core.TagConsistencyService | TagModule（标签目标缝） | REQ-D05 |
| 分类计数组织一致 | （浅模块规则测试） | CategoryModule | REQ-C05 |

## 4. 待实施说明

- 红-绿-重构**未执行**（本链不写代码）。执行时从 Tracer Bullet「创建知识库仅名称时缺省装配生效」起步（最小闭环：真实内存库 + 真实 KbServiceImpl），再横向铺 S2→S4→S3→S5→S6，最后攻 S1（HITL 部分需真实 MinIO/Milvus/Neo4j 环境，先以边界 mock 完成 AFK 部分）。
- 「⚠已知缺陷锁定用例」是唯一预设红的用例：它把 implement §2 偏差 1（syncTagRelations 计数漂移）固化为回归目标；修复前运行应红，属预期。
- 质量门：`mvn test -f fastrag-backend/pom.xml`；新增测试镜像包结构置于 `fastrag-modules/fastrag-knowledge/src/test/java/com/fastrag/module/knowledge/…`（conventions.md §2）。
- S6 的权限键用例若走 Spring 上下文需 security 过滤器链，建议直调控制器方法 + 手动构造 LoginUser，避免引入整链认证成本。
