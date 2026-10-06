# 模型与平台配置（platform-config）— 测试设计

> 五个契约接口（platform-config.ModelRegistry / ModelConnectivityTester / SysConfigStore / SensitiveWordFilter / TermSynonymService，见 contract/interfaces.md `## platform-config`）+ 六个 (S) 模块按 design §2 公共接口分组切片。**敏感词过滤边界（REQ-H 族）与模型测试代理（REQ-B 族，fastrag-ai 网关是真缝）是重点用例族**（任务书指派）。mock 只允许在真缝/系统边界：fastrag-ai 服务（LlmService/EmbeddingService/RerankService——外部网关边界）、MinIO（上传）、时间（可注入或测试库真实时间）；禁 mock 内部模块；MySQL 用测试库真表（建表语句取 schema.sql 对应节；**model_call_log 因 DDL 重复列缺陷无法建表，其用例仅保留「表不可建」的现状断言**）。现状已有 4 个单元测试（SensitiveWordServiceTest 7 用例 / SensitiveWordControllerTest / TermServiceTest 7 用例 / DictionaryServiceTest，全为 Mockito 纯单元）——本设计的用例族在其实测行为上扩展为接口级验证，红绿循环未执行。

## 1. 切片规划

| 切片 | 接口组 | AFK/HITL | 说明 |
|---|---|---|---|
| S0 假成功族裁决前置 | — | **HITL** | 四项 GAP 裁决决定期望值：①presets 是否落地持久化；②train/test 是否接真实编排；③resetToDefault 死逻辑是否修复；④model_call_log 写入点归属。裁决前按现状口径断言 |
| S1 模型注册表 | ModelRegistry（contract）+ presets/export 端点 | AFK | **重点用例族前置**：status 口径、密钥防误清空、路由键数据质量（消费方联动验证） |
| S2 模型连通性测试代理 | ModelConnectivityTester（contract） | AFK | **重点用例族 B**：三类网关路由、失败归一、测时与预览——fastrag-ai 三服务 mock 于真缝 |
| S3 系统配置存储 | SysConfigStore（contract） | AFK | upsert+历史强制记账、LIMIT 100、reset 死逻辑现状断言 |
| S4 敏感词过滤管线 | SensitiveWordFilter（contract） | AFK | **重点用例族 A**：分类解析边界、缓存失效时效、替换兜底、禁用词退出 |
| S5 术语同义供给 | TermSynonymService（contract） | AFK | 双向匹配、启用状态生效面、term_count 守恒 |
| S6 浅模块数据面回归 | Dictionary/QueryRule/Notification/Upload/SecurityPolicy 端点 | AFK | 现状口径断言（blockSearch 死标志、query_rule 零消费、通知广播语义） |

## 2. 测试用例

### S1 ModelRegistry（contract: platform-config.ModelRegistry）

| 测试名 | Given | When | Then（断言） | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [创建模型] 未传 status 时 [落 offline] | create{name,code,purpose,apiUrl} | create | 返回 status="offline"；库中可查 | A02 | 无 |
| [创建模型] 传 apiKey 名时 [归一落 api_key_ref 列] | create{...,apiKey:"sk-1"} | create | api_key_ref="sk-1" | A02 | 无 |
| [更新模型] 传空 apiUrl 时 [既有地址保留] | 既有 apiUrl=http://a 的模型 | update{id,apiUrl:""} | apiUrl 仍 http://a | A03 | 无 |
| [更新模型] 传空 apiKey 时 [既有密钥保留] | 既有 api_key_ref 的模型 | update{id,apiKey:""} | api_key_ref 不变 | A03 | 无 |
| [切换状态] online 模型 toggle 时 [转 offline] | status=online | toggle | status=offline | A04/C01 | 无 |
| [切换状态] offline 模型 toggle 时 [转 online] | status=offline | toggle | status=online | A04/C01 | 无 |
| [切换状态] id 查无时 [静默无副作用] | 不存在 id | toggle | 不抛错、表无变化 | A04 | 无 |
| [批量导入] 缺 purpose/status 时 [落 chat/offline 缺省] | import[{name,code}] | importModels | purpose="chat"、status="offline" | A05 | 无 |
| [批量导入] 重复 code 两行时 [两行共存]（路由键数据质量） | import 含同 code 两行均 status=online | importModels 后按消费口径查询 | 两行均在库；消费口径（code+online LIMIT 1）取值非确定——数据质量风险断言 | A05/C02 | 无 |
| [过滤列表] purpose 传 "Embedding" 与 "EMBEDDING" 时 [结果随库排序规则]（大小写依赖现状） | 种子 purpose='Embedding' | list(purpose="EMBEDDING") | 现状断言：ci 排序规则下命中（修复方向=值集归一） | A01/C02 | 无 |
| [导出] 传 ids 时 [ids 被忽略按 purpose 过滤]（现状口径） | 库内混合 purpose | exportModels(ids:"1,2",purpose:"LLM") | items 仅含 LLM 行；count 与之一致 | A05 | 无 |
| [路由联动] online/offline 翻转时 [消费方解析结果即时变化]（缝 1 联动） | code=c1 online，消费口径 selectOne | toggle 后再按消费口径查询 | 翻转后查无记录（null→默认网关降级路径） | C01 | 无 |

### S2 ModelConnectivityTester（contract: platform-config.ModelConnectivityTester；重点用例族 B）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [对话测试] 网关正常应答时 [返回应答与测时] | online 模型含 apiUrl/apiKey；LlmService.chat mock 返回 "你好" | testChat(id) | success=true、answer="你好"、purpose="LLM"、elapsedMs≥0、apiUrl 回显 | B01 | LlmService（真缝） |
| [向量测试] 网关返回 5 维向量时 [维度与预览如实] | EmbeddingService.embed mock 返回 5 维 | testEmbedding(id) | dimensions=5、vectorPreview 长度 5、purpose="Embedding" | B02 | EmbeddingService |
| [向量测试] 高维向量时 [预览截断 10 维] | mock 返回 1024 维 | testEmbedding | vectorPreview 长度=10 | B02 | EmbeddingService |
| [排序测试] 空文档列表时 [补缺省 3 文档再测] | documents=[] | testRerank | RerankService 收到 3 个缺省文档；documentCount=3 | B03 | RerankService |
| [路由口径] 对 purpose=Embedding 的模型调 testChat 时 [仍走 LLM 通道]（javadoc 失真现状） | 模型 purpose="Embedding" | testChat(id) | LlmService.chat 被调用、EmbeddingService 不被调；purpose 字段="LLM" | B04 | Llm+Embedding |
| [前置校验] 模型不存在时 [结构化失败非异常] | 不存在 id | testChat | {success:false, error:"模型不存在"}；HTTP 200 | B04/B05 | 无 |
| [前置校验] apiUrl 空白时 [拒绝并提示] | apiUrl="" 的模型 | testEmbedding | {success:false, error:"模型未配置接口地址"} | B05 | 无 |
| [失败归一] 网关抛 RuntimeException 时 [错误消息入 success=false 包]（重点族 B） | LlmService.chat mock 抛异常 | testChat | success=false、error=异常消息；无 HTTP 4xx/5xx | B05 | LlmService |
| [失败归一] 网关超时异常时 [同包归一] | mock 抛 TimeoutException | testRerank | success=false 结构一致（三方法同口径） | B05 | RerankService |

### S3 SysConfigStore（contract: platform-config.SysConfigStore）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [保存配置] 新键时 [插入并留 create 历史] | sys_config 无 k1 | saveConfig(k1,"v1","general",…,"admin") | 行落库；history 恰 1 条 change_type=create、new_value=v1、old_value 空 | E01 | 无（时间用测试库真实时间） |
| [保存配置] 既有键时 [覆盖并留 update 历史含旧值] | k1=v1 | saveConfig(k1,"v2",…) | value=v2；history+1 条 change_type=update、old_value=v1、new_value=v2 | E01 | 无 |
| [查询历史] 连续保存 3 次时 [历史 3 条倒序] | k1 三次保存 | listHistory(k1) | 3 条、timestamp 倒序、change_type 依序 create/update/update | E02 | 无 |
| [查询历史] 105 条历史时 [截断 100] | 造 105 条 | listHistory | 恰 100 条 | E02 | 无 |
| [查询历史] 传 configType 时 [参数被忽略]（现状口径） | 既有历史 | listHistory(null,"brand") | 返回不过滤类型的全量（截断内） | E02 | 无 |
| [导入配置] 混合新旧键时 [逐键 upsert 且逐条历史] | k1 既有、k2 新 | importConfig([k1,k2]) | 两行正确；history 两条（update+create） | E03 | 无 |
| [设默认] 未设默认的键时 [is_default 置 1] | k1 is_default=0 | setDefault([k1]) | is_default=1；getDefaultConfigs 含 k1 | E04 | 无 |
| [重置默认] 当前行 is_default=1 时 [判为已默认不动作]（死逻辑现状·S0③） | k1 is_default=1 且 value 已改 | resetToDefault([k1]) | 现状断言：value 不变、history 无新增（修复裁决后应恢复默认值并留 reset 历史） | E05 | 无 |
| [重置默认] 当前行 is_default=0 时 [静默无动作]（死逻辑现状·S0③） | k1 is_default=0（无并行默认行，UNIQUE 保证） | resetToDefault([k1]) | 现状断言：返回空、零副作用 | E05 | 无 |
| [业务开关] publish-switch 保存时 [落固定键 publish] | PUT /config/publish-switch | publishSwitch(body) | sys_config 出现 config_key=publish_switch、value=body 的 JSON 串 | E06 | 无 |

### S4 SensitiveWordFilter（contract: platform-config.SensitiveWordFilter；重点用例族 A）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [输入拦截] 命中 blockInput 词且有指定回复时 [返回回复不回显命中词]（重点族 A） | 启用词 w{blockInput:true, replacement:"请文明发言"} | checkInput("含 w 的输入") | present="请文明发言"；返回值不含 "w" | H04 | 无 |
| [输入拦截] 指定回复为空白时 [用固定兜底文案] | replacement="" | checkInput 命中 | present="您的输入包含受限内容，无法处理" | H04 | 无 |
| [输入拦截] 词仅 replaceAnswer 时 [输入放行] | w{replaceAnswer:true} | checkInput 含 w | empty | H04/H02 | 无 |
| [输出过滤] 命中 replaceAnswer 词时 [替换为指定回复] | w{replaceAnswer:true, replacement:"good"} | filterOutput("bad 词句") | present="good 词句" | H05 | 无 |
| [输出过滤] replacement 为 null 时 [替换为 ***] | replacement=null（DB 缺省 *** 之外的显式 null 路径） | filterOutput 命中 | 结果含 "***" | H05 | 无 |
| [输出过滤] 无命中时 [原样放行返回 empty] | 词表非空无命中 | filterOutput | empty（原文不经改写返回路径） | H05 | 无 |
| [禁用词退出] enabled=0 的词 [不参与检查]（缓存口径） | 词表含禁用命中词 | checkInput/filterOutput | 均 empty | H06 | 无 |
| [缓存失效] 新增命中词后 [检查即时生效]（写失效读加载） | 缓存已加载旧词表 | save(新命中词) 后 checkInput | present（新词参与拦截） | H06 | 无 |
| [缓存失效] 删除词后 [检查即时退出] | 缓存含词 w | delete(w) 后 checkInput 含 w 文本 | empty | H06 | 无 |
| [分类解析] category 键序颠倒时 [现状解析失败]（子串匹配脆弱性） | category='{"replaceAnswer":true,"blockInput":false}'（键序非约定序） | list 控制器适配 | 现状断言：contains("\"blockInput\":true") 不命中该词序变化场景——解析对空格/键序脆弱（design §4 深化项；修复后按 JSON 反序列化断言） | H01/H02 | 无 |
| [部分更新] update 未传三标志时 [标志被重置 false]（债务②现状） | 既有 w{blockInput:true} | update{id,word:"w2"}（无标志字段） | 现状断言：category 三标志全 false（拦截面被关闭；修复方向=保留原标志） | H02 | 无 |
| [CSV 导入] 3 行数据+表头时 [导入 3 并跳头] | CSV 模板格式文件 | batchImport | imported=3；category 三标志逐行正确落库 | H03 | 无 |
| [CSV 导入] 空文件时 [业务错误非异常] | file.isEmpty() | batchImport | HTTP 200 + serverError 语义「请上传文件」 | H03 | 无 |
| [通用底座] mask 模式命中时 [首字保留余字打码] | w="机密" | checkAndFilter(text,"mask") | "机**"（等长打码） | H07 | 无 |
| [通用底座] reject 模式未命中时 [empty] | 无命中 | checkAndFilter(text,"reject") | empty | H07 | 无 |
| [blockSearch] 三标志词入库回显时 [标志可读]（死标志现状·S0 外 GAP） | w{blockSearch:true} | 控制器 list | blockSearch=true 回显；全仓无拦截行为断言（消费方缺失，REQ-H08 GAP 登记） | H08 | 无 |

### S5 TermSynonymService（contract: platform-config.TermSynonymService）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [同义扩展] 查询含术语时 [追加其全部未含别名] | 启用词条 t{alias:"人工智能,机器智能"} | expandSynonyms("什么是AI技术") | [人工智能, 机器智能] 保序 | I04 | 无 |
| [同义扩展] 查询含别名时 [追加术语本体]（反向） | 同上词条 | expandSynonyms("什么是人工智能") | [AI] | I04 | 无 |
| [同义扩展] 别名已在查询中时 [不重复追加] | 同上 | expandSynonyms("AI 与人工智能") | 空列表 | I04 | 无 |
| [同义扩展] 禁用词条时 [不参与扩展]（治理口径） | t{status:0} | expandSynonyms 含 t | 空列表 | I03/I04 | 无 |
| [缓存失效] 词条改禁用后 [扩展即时退出] | 缓存含启用 t | updateTerm(status:0) 后 expandSynonyms | 空列表 | I05 | 无 |
| [同义扩展] 空白/null 查询时 [空列表] | — | expandSynonyms("")/null | List.of() | I04 | 无 |
| [计数守恒] 词条跨库移动时 [两库一减一增]（term_count 守恒） | lib1(count=5)/lib2(count=0) | updateTerm{libraryId:lib2} | lib1=4、lib2=1 | I02 | 无 |
| [计数守恒] 删除至 0 再删时 [不为负] | lib(count=0) 的词条 t | deleteTerm(t) | count 仍 0（Math.max 钳制） | I02 | 无 |
| [级联删除] 删除术语库时 [其下词条清空且缓存失效] | lib 含 3 词条 | deleteLibrary | term_record 无 libraryId 残留；expandSynonyms 不再命中 | I01/I05 | 无 |

### S6 浅模块数据面回归（contract: 无独立契约；design §2 (S) 模块）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [字典查询] label 为空时 [回退显示 key] | 条目 label=null | 控制器 list | item.label=dict_key | G01 | 无 |
| [字典查询] 无 type 时 [按类型分组全量返回] | 两类型各 2 条 | list(null) | 分组 Map 两键，组内 sortOrder 升序 | G01/G03 | 无 |
| [字典启停] enabled 传 "false" 字符串时 [落 0] | update{enabled:"false"} | 控制器 update | 库中 enabled=0 | G03 | 无 |
| [查询规则] toggle 后 [enabled 翻转] | enabled=1 规则 | toggle | enabled=0；再 toggle 回 1 | J01 | 无 |
| [查询规则] 无消费方现状 [数据面仅存储]（GAP 现状断言） | 规则库非空 | 检索 applyQueryRules | appliedRules 恒空（retrieval 侧占位，跨链回归） | J01 | 无 |
| [通知] read-all 后 [unread 计数归零] | 3 条 unread | markAllRead 后 unreadCount | 0；list 全 read | K01/K03 | 无 |
| [通知] markRead 查无 id 时 [静默成功] | 不存在 id | markRead | HTTP success、无异常 | K03 | 无 |
| [通知生产] 摄取完成时 [广播通知无 target_user]（跨链现状） | IngestionConsumer 完成路径 | 消费一条文件消息 | sys_notification 新行 target_user=null、notify_type=knowledge_update | K02 | RabbitMQ（系统边界） |
| [上传] 非 png/jpg 时 [拒绝] | contentType="image/gif" | upload | badRequest「仅支持 jpg/jpeg/png 格式」 | L01 | MinIO（真缝，不触达） |
| [上传] 超 1MB 时 [拒绝] | 1MB+1 字节 png | upload | badRequest「图片大小不能超过 1MB」 | L01 | MinIO |
| [上传] 合法图片时 [落 uploads/brand/ 并返回 URL] | 100KB png | upload | MinioService 收到 uploads/brand/{uuid}.png；返回 /api/files/ 前缀 URL | L01 | MinIO |
| [假成功族] presets create 时 [仅回显不落库]（S0①现状） | 任意 preset body | createPreset | 返回 id=preset_xxx+created:true；无任何表新增行 | A06 | 无 |
| [假成功族] train 时 [指标硬编码]（S0②现状） | 任意模型 | train | metrics='{"loss":0.32,"accuracy":0.91}'、status="running"（恒不流转） | D01 | 无 |
| [安全策略] 创建未传 enabled/priority 时 [缺省 1/0] | 最小 body | createSecurityPolicy | enabled=1、priority=0；列表 priority 升序 | F01 | 无 |

## 3. 追溯（抽样）

| 测试 | 契约接口 | Design 模块 | Spec REQ |
|---|---|---|---|
| S4[输入拦截·不回显命中词] | platform-config.SensitiveWordFilter.checkInput | SensitiveWordFilterModule（缝 2） | REQ-H04 |
| S4[缓存失效·新增即时生效] | platform-config.SensitiveWordFilter.save/invalidate | SensitiveWordFilterModule | REQ-H06 |
| S2[路由口径·purpose 不参与] | platform-config.ModelConnectivityTester.testChat | ModelConnectivityTester | REQ-B04 |
| S1[路由联动·toggle 即时生效] | platform-config.ModelRegistry.toggle | ModelRegistryModule（缝 1） | REQ-C01 |
| S3[保存配置·强制历史] | platform-config.SysConfigStore.saveConfig | SysConfigStoreModule（缝 4） | REQ-E01 |
| S5[同义扩展·禁用退出] | platform-config.TermSynonymService.expandSynonyms | TermSynonymModule（缝 3） | REQ-I03/I04 |

## 4. 待实施说明
- 孤儿 REQ 豁免：REQ-H07（checkAndFilter 通用原语 reject/replace/mask 当前无生产调用方（仅测试消费），保留为通用底座的债务登记，无下游承接为有意；见 implement 文档）

- 红绿循环未执行；执行时按本文件从 Tracer Bullet 起步——建议 **S4[输入拦截·不回显命中词]**（重点族 A 最小穿越，现有 SensitiveWordServiceTest 直接扩展）与 **S2[失败归一]**（重点族 B 真缝 mock 范式确立）双起点。
- mock 白名单执行口径：fastrag-ai 三服务用 Mockito mock（网关边界）；MinIO 用 mock 或 testcontainers（S6 上传片）；时间用测试库真实时间（sys_config_history 排序断言）；**禁 mock** SensitiveWordServiceImpl/TermServiceImpl/ConfigManageServiceImpl 内部的 mapper 之外的任何协作对象——现有 4 个测试的 mapper mock 模式可沿用到切片 S3/S4/S5 的单元层，接口级用例换测试库真表。
- model_call_log 相关用例在 DDL 缺陷（status 重复列）修复前置为「建表失败现状断言」，不进入常规切片。
