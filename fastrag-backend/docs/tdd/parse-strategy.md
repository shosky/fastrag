# 解析策略（parse-strategy）— 测试设计

## 1. 切片规划

按 contract/interfaces.md `## parse-strategy` 的三个契约接口分组；HITL 在前。AFK=可自动化 / HITL=需人决策。

| 切片 | 契约接口 | 内容 | AFK/HITL | 现状 |
|---|---|---|---|---|
| S1 | ParseStrategyBindingGuard | **Tracer Bullet**：绑定一致性端到端（上传→换绑重切→三分存储清理核对）——需真实 MinIO/Milvus/Neo4j/RabbitMQ 环境，清理结果需人工核对 | **HITL** | 未实施 |
| S2 | ParseStrategyBindingGuard | 守卫单元：换绑归属校验/三态语义/元数据改绑拒绝/真源覆盖/文件级保存即重切/预设三步/回退四级——mock 系统边界 | AFK | 未实施（reChunkFile 无直接测试） |
| S3 | StrategyConfigResolver | 三层合并优先级/双格式兼容/容错降级 | AFK | 部分覆盖：StrategyConfigResolverTest 3 例（chunk 组新字段、代码默认、null 策略） |
| S4 | ParseMethodRegistry | 归一化/兼容矩阵/推断回退/全集一致性 | AFK | 无直接测试（经保存校验间接覆盖） |
| S5 | StrategyCrudModule（经 ParseStrategyService 公共接口） | 校验面/默认唯一性/删除守卫/冲突检测/引用计数/文件级 upsert | AFK | 部分覆盖：ParseStrategyServiceImplTest 7 例（advanced 透传与校验拒绝） |
| S6 | ParseStrategyBindingGuard | **双自动匹配语义差异仲裁**：resolveByExtension 与 resolveStrategy 对同一 (kbId, ext) 返回不同策略——需先人工决策「哪套是意图」再固化断言 | **HITL** | 未实施（现状分叉无测试锁定） |

mock 边界纪律：仅 MinIO/Milvus/Neo4j(GraphStore)/RabbitMQ(MessagePublisher)/SysConfig(ConfigManageService)/mapper 持久层；禁 mock ChunkingService、DocumentParser 等内部逻辑模块。

## 2. 测试用例（逐接口）

### ParseStrategyBindingGuard（contract: parse-strategy.ParseStrategyBindingGuard）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [换绑] 目标策略属他库时 [拒绝并保持原绑定] | 文件绑定策略A；策略B属另一 kb | reChunkFile(kbId, fileId, "B") | 抛 400「解析策略不存在或不属于该知识库」；parse_strategy_id 未变 | REQ-D04、E02 | mapper |
| [换绑] 本文件专属策略 [放行并重切] | 文件 F 有 file_id=F 的专属策略 | reChunkFile 换绑该策略 | 绑定写回；auto 分片删除、manual 保留、重发摄取消息 | REQ-D01、E02 | Milvus/GraphStore/MessagePublisher |
| [换绑] 空串 strategyId [清除覆盖回自动匹配] | 文件绑定策略A | reChunkFile(kbId, fileId, "") | parse_strategy_id=null；消息 strategyId=自动匹配结果 | REQ-D04、C01 | mapper/MessagePublisher |
| [重切] 处理中文件 [拒绝] | 文件 status=processing | reChunkFile | 400「文件正在处理中…」 | REQ-E02 | 无 |
| [重切] QA 模式文件 [拒绝] | processingMode=qa | reChunkFile | 400「QA 模式文件不支持重新分片」 | REQ-E02 | 无 |
| [重切] 回收站文件 [拒绝] | deletedAt 非空 | reChunkFile | 400「文件已在回收站中…」 | REQ-E02 | 无 |
| [改绑] 元数据接口携带新 parseStrategyId [拒绝并引导 re-chunk] | 文件已绑定A；patch parseStrategyId=B | update patch | 400「解析策略变更须与重新分片一起完成…」 | REQ-E01 | 无 |
| [改绑] patch 值等于当前绑定 [放行其余字段] | patch parseStrategyId=A（当前即 A） | update patch | 更新成功 | REQ-E01 | 无 |
| [重试] 快照内嵌历史策略 [以文件绑定覆盖] | processing_config 内 strategyId=旧S；文件绑定=新S | retryFile → rebuildProcessRequest | 发出的 process 请求 parseStrategyId=新S | REQ-E03 | MessagePublisher |
| [保存即重切] 文件级自定义策略 [保存后原子重切] | 文件 F 无专属策略 | saveFileStrategy(kbId, F, req) | 先 upsert 一条 file_id=F 策略，再以该 id 调 reChunkFile | REQ-D01、E04 | mapper/MessagePublisher |
| [预设] 当前绑定已生效为目标策略 [直接复用不新建] | 绑定策略 advanced.chunk.strategy=structure_aware | reChunkFile(preset="structure_aware") | 返回当前绑定 id，不 insert 新策略 | REQ-E05 | mapper |
| [预设] 库内无生效策略 [创建文件级预设（非 KB 级）] | 无任何 strategy=semantic 策略 | reChunkFile(preset="semantic") | 新策略 file_id=文件、is_default=0、advanced 含 chunk.strategy=semantic；不入自动匹配候选 | REQ-E05、C02 | mapper |
| [回退] 请求策略查无 [warn 回退自动匹配] | 请求 parseStrategyId=X 不存在 | process | 返回四级回退结果，不抛错 | REQ-D03、C04 | mapper |
| [回退] 四级匹配顺序 [默认+扩展名 → 扩展名 → 默认 → 首个] | 库内四类策略齐备 | resolveStrategy(kbId, ext) | 依次构造缺失场景断言逐级回退；全空返回 null | REQ-C01 | mapper |
| [回退] 文件级策略 [永不参与自动匹配] | 库内仅 file_id=他文件 的策略含该扩展名 | resolveStrategy | 不命中文件级，走默认/空 | REQ-C02 | mapper |
| [删除回退] 绑定策略被删后 [文件回退自动匹配] | 文件绑定策略已物理删除 | resolveRequestedStrategy(kbId, fileId, ext, 已删id) | warn 日志 + 自动匹配结果 | REQ-C04 | mapper |
| [E2E] 换绑重切后 [三分存储与绑定一致]（Tracer Bullet） | 真实环境：文件含 auto+manual 分片、向量、图谱 | reChunkFile 换绑 | Milvus 仅 auto 向量被删；图谱文件数据重建；manual 分片仍在且 graphIndexed 复位；绑定=新策略 | REQ-E02 | **真实 MinIO/Milvus/Neo4j/MQ**（HITL 人工核对） |
| [仲裁] 两匹配实现语义分叉 [按决策固化单一断言] | 默认策略含 .pdf 且另有非默认策略含 .pdf | resolveByExtension vs resolveStrategy | 先 HITL 决策意图；断言锁定胜者语义（现状：两实现返回可不同） | REQ-C03 | mapper（HITL 决策） |

### StrategyConfigResolver（contract: parse-strategy.StrategyConfigResolver）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [合并] 策略新分组字段 [覆盖系统默认] | SysConfig chunk=1000；策略 chunk.chunkLength=2000 | resolve("s1") | chunkLength=2000，其余默认保留 | REQ-G01 | mapper/ConfigManageService |
| [合并] SysConfig 未配置 [用代码兜底 1000/100] | getConfig 返回 null | resolve(null) | chunkLength=1000, overlap=100 | REQ-G01 | ConfigManageService |
| [合并] SysConfig 值非法 [降级代码兜底] | getConfig 抛异常/值非数字 | resolve(null) | 兜底值，不抛错 | REQ-G01、E_SYSCONFIG_UNREADABLE | ConfigManageService |
| [合并] parse/index 组字段 [正确映射] | 策略 advanced 含 parse.tableMode=ignore、index.embedFields | resolve("s1") | parse.tableMode=ignore；embedFields 列表完整 | REQ-G02 | mapper |
| [兼容] 旧平铺字段 [同组缺失时回退映射] | advanced={"chunkLength":800,"delimiter":"\n"}（无 chunk 组） | resolve("s1") | chunkLength=800；delimiters=["\n"]（单数转数组） | REQ-G02 | mapper |
| [兼容] 新旧并存 [新分组优先] | advanced 同时含 chunk 组与平铺 chunkLength | resolve("s1") | 取 chunk 组值，平铺被忽略 | REQ-G02 | mapper |
| [容错] advanced 损坏 JSON [降级系统默认不抛错] | advanced="{bad json" | resolve("s1") | 返回默认配置；无异常 | REQ-G03、E_ADV_MALFORMED | mapper |
| [容错] 单项值类型非法 [跳过该项其余生效] | chunk.overlap="abc" | resolve("s1") | overlap 保持默认；chunkLength 照常生效 | REQ-G03、E_CONFIG_ITEM_INVALID | mapper |

### ParseMethodRegistry（contract: parse-strategy.ParseMethodRegistry）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [归一化] 大写/无点扩展名 [等价判定] | "PDF"、"pdf"、".PDF" | isSupported 各形态 | 全部 true | REQ-B04 | 无 |
| [兼容] default 方法 [兼容全部受支持扩展名] | 任一 supportedExtensions 元素 | isCompatible("default", ext) | 全部 true | REQ-B04、G04 | 无 |
| [兼容] 专用方法 [拒绝超出自身集的扩展名] | method=pdf, ext=".docx" | isCompatible | false | REQ-G04 | 无 |
| [推断] 未识别扩展名 [回退 default] | ext=".xyz"/空串/null | resolveByExtension | "default" | REQ-B04 | 无 |
| [一致性] DEFAULT 全集 [等于 supportedExtensions] | — | 对比 DEFAULT.extensions 与 supportedExtensions() | 集合相等且去重保序 | REQ-B03 | 无 |

### ParseStrategyService（StrategyCrudModule 公共接口，HTTP 见 design §2）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [创建] 空名称/空扩展名列表/缺失扩展名 [拒绝]（既有 3 例） | 各非法请求 | create | BusinessException 且文案定位字段 | REQ-A03 | mapper |
| [创建] 不兼容扩展名 [拒绝] | method=pdf, extensions=[.docx] | create | 400「扩展名 .docx 与文档类型「PDF」不兼容」 | REQ-G04 | mapper |
| [创建] 新策略 [恒非默认] | 合法请求 | create | 落库实体 is_default=0 | REQ-A03 | mapper |
| [创建] overlap≥chunkLength [拒绝] | advanced.chunk={chunkLength:100, overlap:100} | create | 400 提示重叠须小于分片长度 | REQ-G05 | mapper |
| [创建] overlap≥chunkLength 旧平铺格式 [同样拒绝] | advanced={chunkLength:100, overlap:100} | create | 拒绝（分组/平铺双格式取值） | REQ-G05 | mapper |
| [更新] 仅带 advanced [沿用现有方法校验并整体替换 advanced]（既有 1 例） | 现有策略 advanced 旧值 | update | updateById 的 advanced 为新 JSON | REQ-A04 | mapper |
| [更新] 提供空名称/空扩展名 [拒绝]（既有 2 例） | name=""/extensions=[] | update | BusinessException | REQ-A04 | mapper |
| [默认] set-default 后 [库内默认唯一] | 策略A已是默认 | setDefault(B) | A.is_default=0 且 B.is_default=1 | REQ-B01 | mapper |
| [删除] 默认策略 [拒绝] | 目标 is_default=1 | delete | 400「默认策略不允许删除」 | REQ-A05 | mapper |
| [删除] 非默认策略 [放行且不校验引用] | fileCount>0 | delete | 删除成功（回退语义在读取侧） | REQ-A05 | mapper |
| [列表] file_id 非空策略 [不出现且带 fileCount] | 库内 1 KB 级 + 1 文件级策略；2 文件引用 KB 级 | list | 仅 KB 级且 fileCount=2 | REQ-A01 | mapper |
| [详情] 不存在或文件级 id [404 语义拒绝] | id 指向文件级策略 | get | 「解析策略不存在」 | REQ-A02 | mapper |
| [冲突] 重叠扩展名 [返回冲突策略名] | 策略A含 .pdf | detectConflicts([.pdf,.md]) | 含「A」；excludeId 命中时不含自身 | REQ-F01 | mapper |
| [冲突] 空扩展名入参 [返回空列表] | extensions=[] | detectConflicts | 空列表不查库 | REQ-F01 | mapper |
| [文件级] 重复保存 [upsert 不产生第二条]（既有 upsert 路径） | 已存在 file_id=F 策略 | saveFileStrategy | updateById 原记录，无 insert | REQ-D01 | mapper |
| [文件级] 名称可空描述超长 [缺省「自定义」并截断 200] | name=null, description 300 字 | saveFileStrategy | name=「自定义」；description=200 字 | REQ-D02 | mapper |

## 3. 追溯（抽样）

| 测试 | 契约接口 | Design 模块 | Spec REQ |
|---|---|---|---|
| [改绑] 元数据接口携带新 parseStrategyId [拒绝] | ParseStrategyBindingGuard | ParseStrategyBindingGuard | REQ-E01 |
| [回退] 四级匹配顺序 | ParseStrategyBindingGuard | ParseStrategyBindingGuard | REQ-C01 |
| [合并] SysConfig 未配置 [用代码兜底] | StrategyConfigResolver | StrategyConfigResolver | REQ-G01 |
| [兼容] 旧平铺字段回退 | StrategyConfigResolver | StrategyConfigResolver | REQ-G02 |
| [兼容] 专用方法拒绝越集扩展名 | ParseMethodRegistry | ParseMethodRegistry | REQ-G04、B04 |
| [默认] set-default 后库内默认唯一 | ParseStrategyService | StrategyCrudModule (S) | REQ-B01 |
| [文件级] 重复保存 upsert | ParseStrategyService | StrategyCrudModule (S) | REQ-D01 |

## 4. 待实施说明

- 红绿循环未执行（本链不写测试代码）；实施时从 S1 Tracer Bullet 起步，先让「换绑→重切→清理」主链在真实边界环境走通，再下沉 S2~S5 单元切片。
- 既有 StrategyConfigResolverTest（3 例）与 ParseStrategyServiceImplTest（7 例）全部保留为回归基线；S3/S5 补齐用例时不得删改既有断言。
- S6 与 S1 为 HITL：S6 需先裁决「resolveByExtension 与 resolveStrategy 哪套语义是意图」（现状分叉：ParseStrategyServiceImpl.java:128-131 vs FileServiceImpl.java:920-947），裁决前不得写断言固化任一方；S1 需 docker-compose 全套基础设施与人工核对清理结果。
- 缺口：reChunkFile/retry/resolveRequestedStrategy/update 守卫当前零测试覆盖（S2 全部为缺口）；ParseMethodRegistry 无直接测试（S4 全为缺口）。
