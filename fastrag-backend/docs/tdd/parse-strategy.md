# 解析策略（parse-strategy）— 测试设计

## 1. 切片规划

按 contract/interfaces.md `## parse-strategy` 的三个契约接口分组；HITL 在前。AFK=可自动化 / HITL=需人决策。excel_row（按行切片）增量切片见 §5（2026-11-04，REQ-H 系列，全 AFK）。

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
| [校验] excel_row 携带非 Excel 扩展名 [拒绝] | ParseStrategyService（IF-006 消费面） | StrategyCrudModule (S) | REQ-H02 |
| [装配] 显式表头两行按 4 行切片 [每片带表头] | ExcelRowSlicer（缝 4） | 表格切片方式缝 | REQ-H06 |
| [豁免] 4800 切片不被原子溢出二次拆碎 | structuralChunk（excel_row 分支） | 分片器分派缝 | REQ-H06、ADR-0012 |
| [兜底] 非 Excel 文件命中 excel_row [降级 rule_fixed] | IngestionConsumer 分派 | 分片器分派缝 | REQ-H09 |

## 4. 待实施说明

## 4. 待实施说明

- 红绿循环未执行（本链不写测试代码）；实施时从 S1 Tracer Bullet 起步，先让「换绑→重切→清理」主链在真实边界环境走通，再下沉 S2~S5 单元切片。
- 既有 StrategyConfigResolverTest（3 例）与 ParseStrategyServiceImplTest（7 例）全部保留为回归基线；S3/S5 补齐用例时不得删改既有断言。
- S6 与 S1 为 HITL：S6 需先裁决「resolveByExtension 与 resolveStrategy 哪套语义是意图」（现状分叉：ParseStrategyServiceImpl.java:128-131 vs FileServiceImpl.java:920-947），裁决前不得写断言固化任一方；S1 需 docker-compose 全套基础设施与人工核对清理结果。
- 缺口：reChunkFile/retry/resolveRequestedStrategy/update 守卫当前零测试覆盖（S2 全部为缺口）；ParseMethodRegistry 无直接测试（S4 全为缺口）。

## 5. excel_row 增量切片（2026-11-04，REQ-H 系列）

> 契约接口：IF-006 StrategyConfigResolver（ExcelRow 类型组）/ IF-007 ParseMethodRegistry（扩展名域）/ 缝 4 表格切片方式（ExcelRowSlicer + parseExcelContent 分支）/ structuralChunk excel_row 豁免——modules/parse-strategy.md；对外校验面 interfaces.md API-013~015（ERR-PS-* 四码）。
> **mock 边界（只落在真缝）**：ExcelRowSlicer、structuralChunk、StrategyConfigResolver、ParseMethodRegistry 一律真实现；Excel 字节用 POI 内存构簿构造（不落盘 fixture）；mock 仅 mapper/SysConfig/MinIO/Milvus/GraphStore/MessagePublisher；禁 mock DocumentParser/ChunkingService 内部逻辑。期望值全部已定（4800 硬上限、区间 ≤5 行、rowsPerSlice 缺省 100——spec REQ-H 定值），无 HITL 项。
> **本节红绿循环已执行（2026-11-04 /idd-implement）**：S7~S11 全部落地为自动化测试并绿——knowledge 模块全量回归 **217/217 零失败**（BUILD SUCCESS）；新增/扩展测试类 7 个（ExcelRowSlicerTest 6、ExcelRowSliceParseTest 7、ExcelRowChunkingTest 5、ExcelRowConfigResolverTest 4、ExtractionUnitAssemblerOversizeTest 2、ParseStrategyTemplateControllerTest 1、ParseStrategyServiceImplTest +14）。实现偏差见 modules/parse-strategy.md Logic 节 Decisions & Deviations（要点：excelRow 组仅新分组承载；小切片强制独占分片；REQ-H09 守卫落解析侧；QA 回退通道守护与零 DDL 对账未自动化，理由在该节）。

### 5.1 切片规划

| 切片 | 接口组 | AFK/HITL | 说明 |
|---|---|---|---|
| S7 excel_row 保存校验 | ParseStrategyService（IF-006 消费面，API-013~015） | AFK | ERR-PS-* 四码用例化：扩展名域/表头三参/行数下限/生效值校验/双格式一致 |
| S8 解析期装配 | ExcelRowSlicer + parseExcelContent excel_row 分支（缝 4） | AFK | **Tracer Bullet 在此**：真 Excel 字节 → 显式表头 → 多片 TABLE 节点；软目标行数与 4800 硬上限双闸；单行硬切续段带表头 |
| S9 多 Sheet 与容错 | parseExcelContent Sheet 循环 | AFK | 统一生效、隐藏 Sheet、行数不足跳过告警、tableMode=ignore 不丢片 |
| S10 分片侧与元数据 | IngestionConsumer 分派 + structuralChunk 豁免 | AFK | 原子 TABLE ≡ 分片、非 Excel 降级 rule_fixed、溢出豁免（最易回归点）、元数据沿用、零 DDL 对账 |
| S11 预览同路径与下游零适配 | FileController preview + QA 提炼/图谱组装既有测试 | AFK | 预览切片与摄取一致；QA 回退通道纳入 table 分片；超长切片独占抽取单元；模板四分类不变 |

实施顺序：S8 Tracer Bullet 先行（接口端到端验证）→ S7 校验面 → S9/S10 → S11 回归确认。既有测试（StrategyConfigResolverTest 3 例、ParseStrategyServiceImplTest 7 例等）全部保留为回归基线。

### 5.2 测试用例（逐切片）

#### S7 excel_row 保存校验（contract: IF-006 ExcelRow 组 + interfaces API-013~015；spec REQ-H02/H03/H04）

| 测试名 | Given | When | Then（断言） | 追溯 | 边界mock |
|---|---|---|---|---|---|
| [校验] excel_row 携带 pdf 扩展名时 [拒绝保存] | advanced.chunk.strategy=excel_row；extensions=[".xlsx",".pdf"] | create | 业务异常 400 ERR-PS-EXT-DOMAIN；库内无记录 | REQ-H02 | mapper |
| [校验] 扩展名混合大小写时 [按归一后判定] | extensions=[".XLSX",".PDF"] | create | 归一后触发 ERR-PS-EXT-DOMAIN（判定基于带点小写） | REQ-H02、REQ-B04 | mapper |
| [校验] 表头起始大于结束时 [拒绝保存] | excelRow{start:3,end:2} | create | 400 ERR-PS-HEADER-RANGE | REQ-H03 | mapper |
| [校验] 表头区间恰 6 行时 [拒绝保存] | excelRow{start:1,end:6} | create | 400 ERR-PS-HEADER-RANGE | REQ-H03 | mapper |
| [校验] 表头区间恰 5 行时 [通过] | excelRow{start:1,end:5} | create | 保存成功；回读 advanced 三参保真 | REQ-H03 | mapper |
| [校验] 切分起始行等于表头结束时 [拒绝保存] | excelRow{start:1,end:2,sliceStart:2} | create | 400 ERR-PS-SLICE-START | REQ-H03 | mapper |
| [校验] 切分起始行为表头结束+1 时 [通过] | excelRow{start:1,end:2,sliceStart:3} | create | 保存成功 | REQ-H03 | mapper |
| [校验] rowsPerSlice 缺省时 [生效为 100] | strategy=excel_row 无 rowsPerSlice | create 后 resolve | ParseStrategyConfig.ExcelRow.rowsPerSlice=100 | REQ-H04 | mapper |
| [校验] rowsPerSlice 为 0 时 [拒绝保存] | rowsPerSlice=0 | create | 400 ERR-PS-ROWS-PER-SLICE | REQ-H04 | mapper |
| [校验] rowsPerSlice 为 10000 时 [通过] | rowsPerSlice=10000 | create | 保存成功（无行数上限硬校验，4800 兜底） | REQ-H04 | mapper |
| [校验] 非 excel_row 策略携带残缺 excelRow 时 [不校验放行] | strategy=rule_fixed；excelRow{start:9,end:1} | create | 保存成功（组不被消费） | REQ-H01 | mapper |
| [更新] 仅改 name 时 [excel_row 生效值不回退触发校验失败] | 库内 excel_row 策略；patch 仅 name | update | 更新成功（生效值校验 REQ-A04） | REQ-A04、REQ-H02 | mapper |
| [文件级] 保存 excel_row 文件级策略时 [同族校验且保存即重切] | 文件 F 为 .xlsx；body 同 API-015 | saveFileStrategy | 校验同族生效；返回 FileDto 且触发重切 | REQ-H02、REQ-E04 | mapper/MessagePublisher |
| [平铺] 旧平铺位置出现 excel_row 时 [三参缺失即拒绝] | advanced 平铺 strategy=excel_row（无 chunk 分组） | create | 400「必须配置表头起始行」（excelRow 组仅新分组承载，无平铺形态——实现偏差裁决） | REQ-G02、REQ-H03 | mapper |

#### S8 解析期装配（contract: 缝 4 ExcelRowSlicer；spec REQ-H05/H06）——Tracer Bullet 切片

| 测试名 | Given | When | Then（断言） | 追溯 | 边界mock |
|---|---|---|---|---|---|
| [装配] 显式表头 2 行 10 数据行按 4 行切片时 [产 3 片且每片带表头] | POI 构簿 12 行；excelRow{start:1,end:2,sliceStart:3,rowsPerSlice:4} | parseExcelContent（excel_row 策略） | 3 个 TABLE 节点；每节点 headers=前 2 行展开；行数 4/4/2 | REQ-H06 | 无（纯内存） |
| [装配] 累计文本达 4800 字符时 [提前于行数封片] | 宽行数据使 2 行即近 4800 | parseExcelContent | 封片以 4800 上限为准（含表头计） | REQ-H06 | 无 |
| [装配] 单行超 4800 字符时 [硬切多段且续段重复表头] | 某行 10000 字符 | parseExcelContent | 该行拆为多段；每续段 TABLE 仍带完整表头 | REQ-H06 | 无 |
| [装配] 表头结束与切分起始之间有行时 [跳过该区间] | 第 3~4 行为废弃行；sliceStart=5 | parseExcelContent | 切片不含第 3~4 行内容 | REQ-H05 | 无 |
| [装配] strategy=excel_row 时 [不触发自动检测与 LLM] | 复杂合并表头构簿；llmModel 已配 | parseExcelContent | detectHeaderRegion/resolveHeadersWithLlm 零调用（确定性路径）；表头=显式范围原文 | REQ-H05 | LlmService（断言零交互） |
| [装配] 表头行含合并单元格时 [按左上角值展开] | 表头区 2 行纵向合并 | parseExcelContent | headers 取合并区域左上角值，行数按区间计 | REQ-H06 | 无 |

#### S9 多 Sheet 与容错（spec REQ-H07/H08）

| 测试名 | Given | When | Then（断言） | 追溯 | 边界mock |
|---|---|---|---|---|---|
| [装配] 双 Sheet 工作簿时 [统一按同参切片] | 两个可见 Sheet 各若干行 | parseExcelContent | 两 Sheet 各产 HEADING+切片序列，参数一致 | REQ-H07 | 无 |
| [装配] 隐藏 Sheet 时 [跳过不产节点] | Sheet2 置隐藏 | parseExcelContent | 仅 Sheet1 产节点（既有语义沿用） | REQ-H07 | 无 |
| [装配] Sheet 行数不足切分起始行时 [跳过并告警] | Sheet2 仅 2 行；sliceStart=5 | parseExcelContent | Sheet2 仅 HEADING 无切片；整体解析成功；warn 记录 | REQ-H07 | 无 |
| [装配] tableMode=ignore 时 [切片不丢失] | config.parse.tableMode=ignore | structuralChunk（excel_row 节点） | TABLE 节点全部入分片（excel_row 忽略 tableMode） | REQ-H08 | 无 |

#### S10 分片侧与元数据（contract: 分派豁免与 IF-006 消费面；spec REQ-H09/H10）

| 测试名 | Given | When | Then（断言） | 追溯 | 边界mock |
|---|---|---|---|---|---|
| [分派] excel_row 策略时 [走 structuralChunk 一 TABLE 一分片] | parseResult.nodes=HEADING+3 切片 TABLE | IngestionConsumer 分派 | chunks=3；chunkIndex 连续 | REQ-H01、adr/0012 | MessagePublisher |
| [分派] 非 Excel 文件命中 excel_row 时 [降级 rule_fixed 并告警] | .pdf 文件绑定 excel_row 策略 | 摄取分派 | warn 记录；走 rule_fixed 路径；摄取不中断 | REQ-H09 | mapper |
| [豁免] 4800 字符切片时 [不被 handleAtomicOverflow 二次拆碎] | chunkLength=1000；切片≈4800 | structuralChunk（excel_row） | 每切片保持整片（不按 chunkLength 行拆） | REQ-H06、adr/0012 | 无 |
| [元数据] 切片落库时 [title=Sheet 名/chunkType=table] | 3 切片解析结果 | structuralChunk | 各分片 title=Sheet 名；headingPath=[Sheet 名]；chunkType=table | REQ-H10 | 无 |
| [元数据] kb_chunk 列集 [零 DDL 对账]（未自动化，由「不新增迁移」构造保证，见 §5.3 偏差） | SchemaInitializer 与 schema.sql | DDL 对账测试式断言 | 无 excel_row 新增列（REQ-H10 零迁移） | REQ-H10 | 无 |

#### S11 预览同路径与下游零适配（spec REQ-H11/H12/H13）

| 测试名 | Given | When | Then（断言） | 追溯 | 边界mock |
|---|---|---|---|---|---|
| [预览] excel_row 策略文件预览时 [切片与摄取一致] | 同一 .xlsx 字节与策略 | preview 端点 vs 摄取分片 | 切片数量与内容逐片一致（同解析路径） | REQ-H11 | mapper/MinIO |
| [下游] excel_row 切片进 QA 提炼时 [回退通道纳入]（未自动化，见 §5.3 偏差） | 文件分片全为 chunkType=table | loadExtractChunks 回退通道 | table 切片入选提取单位（过滤仅排 image） | REQ-H13 | mapper/LlmService |
| [下游] excel_row 切片进图谱构建时 [超长切片独占单元] | 切片≈4800 字符 | ExtractionUnitAssembler.assemble | 该切片独占一个抽取单元、内容不截断 | REQ-H13 | 无（纯函数） |
| [模板] 模板列表 [仍为四分类无 Excel 项] | — | 模板端点 | 恰 auto/pdf/pptx/media 四项（REQ-H12 现状断言） | REQ-H12 | 无 |

### 5.3 增量实施说明

- 红绿循环未执行（由 /idd-implement 执行）；Tracer Bullet = S8 首例「显式表头 2 行 10 数据行按 4 行切片」，一步打通「策略配置 → 解析期装配 → 切片节点」端到端。
- S8/S9 以 POI 内存构簿构造 Excel 字节（XSSFWorkbook 直建），不落盘 fixture、不依赖真实文件；S10 豁免用例是 ADR-0012 后果 2 的行为回归钉，漏做即 4800 切片被 chunkLength 拆碎。
- 既有测试全保留：StrategyConfigResolverTest 补 ExcelRow 组断言时不删改既有 3 例；ParseStrategyServiceImplTest 补 S7 用例时不删改既有 7 例。
- S11 的下游两例为「确认无需工作」的守护断言（REQ-H13 零适配），防未来过滤条件变更悄悄破坏 table 分片消费。
