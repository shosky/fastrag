# Widget 后端（widget）— 测试设计

> 契约接口：widget.WidgetAssetService（IF-004：resolveSnapshot / buildCatalog / validateDraft）与 widget.WidgetActionProcessor（IF-005：prepare）——modules/widget.md `## Contract` 双定义单元；REST 面（interfaces.md API-001~010）按端点排除规则不设深接口用例，经服务层公共接口覆盖；对话流扩展（API-011/012）经编排层与引擎既有测试缝覆盖。
> **mock 边界（只落在真缝）**：LlmService（AgentEngine 既有测试模式，S6 需要时）、AgentMiddlewareChain（真链收集中间件，WidgetMiddleware 以真实现参与）、ToolExecutorFactory（真工厂收集，render_widget 以 type="widget" 真注册）；MySQL 用真库（MyBatis-Plus）；禁 mock 内部模块——校验器、白名单、播种器、快照组装一律直用真实现。
> **本文件为测试设计，红绿循环未执行**（由 /idd-implement 按本设计执行；全仓 22 篇 tdd 同口径）。

## 1. 切片规划

| 切片 | 接口组 | AFK/HITL | 说明 |
|---|---|---|---|
| S0 产品裁决前置 | — | **无阻塞** | 快照 #1-34 全部锁定（根 CONTEXT.md Widget 决策快照），期望值全部已定；无 HITL 项 |
| S1 三档保存校验 | WidgetAssetService.validateDraft | AFK | ERR-WG-SCHEMA-INVALID 的用例化：逐档取样 + 行级错误聚合 + 体积/名称边界 |
| S2 资产生命周期 | WidgetAssetService（CRUD/复制） | AFK | 属主隔离三分支、删除保护、复制语义、内置行只读 |
| S3 官方模板播种幂等 | WidgetBuiltinSeeder（启动缝） | AFK | 首启插入、重启跳过不覆盖（SkillDataInitializer 口径） |
| S4 绑定上限与开关 | 绑定服务层（第六类绑定） | AFK | 上限 10、唯一对、禁用语义、权限 |
| S5 对话协议装配 | buildCatalog + WidgetMiddleware（真链） | AFK | 绑定非空注入+注册、直聊不注入、禁用不注入、5000 截断、已删降级 |
| S6 render_widget 执行链 | resolveSnapshot（经 ToolExecutor 真缝） | AFK | **本特性最重的正确性断言**：fail-closed、校验自纠、快照定格、32KB 护栏 |
| S7 回传推进 | WidgetActionProcessor.prepare + 编排层 | AFK | 可见消息合成、块注入、16KB 拒绝、非 sys.submit 防御 |
| S8 历史快照守恒 | 消息落库内容 | AFK | 快照随 toolCalls 落库、资产删除后历史可渲 |
| S9 双处/双端守护 | DDL + 白名单常量 | AFK | SkillDdlParityTest 式：双表 DDL 对账；后端白名单 ↔ 前端注册表集合相等（前端目录不可达环境标注跳过） |

选择原则：全部经公共接口（服务层/执行器缝/启动缝）观察，不测私有方法；期望值直接取自快照编号与 interfaces.md 字段定义，无待裁项。

## 2. 测试用例（逐切片）

### S1 三档保存校验（contract: IF-004.validateDraft；spec REQ-WG-A02）

| 测试名 | Given | When | Then（断言） | 追溯 | 边界mock |
|---|---|---|---|---|---|
| [校验] 模板坏 JSON 时 [第一档拒绝] | template="{type:Card" | validateDraft | 业务异常 code=400；message 含「模板」定位；单件报错 | A02 | 无 |
| [校验] 白名单外组件时 [第二档拒绝] | template 含 type="Chart" | validateDraft | code=400；message 含未知组件名与位置 | A02 | 无 |
| [校验] 必填属性缺失时 [第二档拒绝] | Button 无 action 配置 | validateDraft | code=400；message 指明组件与缺失属性 | A02 | 无 |
| [校验] 变量未声明时 [第三档拒绝] | 模板引 {{total}}，schema 无此变量 | validateDraft | code=400；message 含变量名 | A02 | 无 |
| [校验] default 不合 schema 时 [第三档拒绝] | schema 要求 number，default 给字符串 | validateDraft | code=400；message 含字段路径 | A02 | 无 |
| [校验] 三处同坏时 [行级聚合] | 三件套各埋一处错 | validateDraft | message 含全部三条行级错误（\n 分隔），非首错即停 | A02 | 无 |
| [校验] 单件超 64KB 时 [拒绝] | template 序列化 65536+ 字节 | validateDraft | code=400；message 含体积上界语义 | A02/约束 | 无 |
| [校验] name 空白时 [拒绝] | name="  " | validateDraft | code=400 | A01/A02 | 无 |
| [校验] 最小合法骨架时 [通过] | 内置骨架示例（新建页预填物） | validateDraft | 不抛异常 | A02 | 无 |

### S2 资产生命周期（contract: IF-004 CRUD；interfaces API-002~006；spec REQ-WG-A01/A03/A05/B02/B03）

| 测试名 | Given | When | Then | 追溯 | 边界mock |
|---|---|---|---|---|---|
| [创建] 合法草稿时 [落库归属当前用户] | 用户 A 登录 | create(合法草稿) | 库行 is_builtin=0、creator=A、三件套逐字节一致 | A01 | 无 |
| [读取] 非属主读他人自建时 [拒绝] | 资产属 A | B read | 业务错误 403；官方行 B 可读 | A03 | 无 |
| [列表] 属主过滤时 [只见自己+官方] | A、B 各建一资产 + 官方行 | A list / A list(builtin=true) | 前者仅 A 的；后者仅官方行 | A03/B04 | 无 |
| [修改] 保存即生效时 [新内容立即可读] | 资产已存在 | update(新模板) → 再 read | 读到新模板；无版本残留 | A04 | 无 |
| [删除] 被绑定时 [拒绝保护] | 资产被应用 X 绑定（enabled=false 也算） | delete | ERR-WG-BOUND-DELETE 409；库行仍在；解绑后 delete 成功 | A05 | 无 |
| [修改/删除] 官方模板行时 [只读拒绝] | is_builtin=1 行 | update / delete | 均 409；行原样 | B02 | 无 |
| [复制] 官方行时 [生成独立资产] | 官方行 O | copy(O) | 新行三件套与 O 相同、is_builtin=0、creator=当前用户；O 原样 | B03 | 无 |
| [复制] 自建资产时 [可复制派生] | A 的资产 | A copy | 同上语义 | B03 | 无 |

### S3 官方模板播种幂等（启动缝；spec REQ-WG-B01）

| 测试名 | Given | When | Then | 追溯 | 边界mock |
|---|---|---|---|---|---|
| [播种] 空库启动时 [全量插入] | widget 表无官方行 | seeder.run() | 预置模板集全部插入、is_builtin=1、creator=system | B01 | 无 |
| [播种] 重启时 [存在即跳过] | 官方行已在且被管理员改过描述 | seeder.run() | 行不被覆盖（描述保持改后值）；不新增重复行 | B01 | 无 |
| [播种] 部分缺失时 [只补缺失] | 官方集 4 条仅存 2 条 | seeder.run() | 只插缺失 2 条；既有 2 条原样 | B01 | 无 |

### S4 绑定上限与开关（contract: 绑定服务层；interfaces API-007~010；spec REQ-WG-C01~C04）

| 测试名 | Given | When | Then | 追溯 | 边界mock |
|---|---|---|---|---|---|
| [绑定] 第 11 条时 [上限拒绝] | 应用已有 10 条绑定 | bind | ERR-WG-BINDING-LIMIT 409；库行数仍 10 | C03 | 无 |
| [绑定] 同对重复时 [唯一拒绝] | (app,widget) 已存在 | bind 同对 | 409（提示改用启用开关） | C01 | 无 |
| [绑定] 官方行/他人资产时 [候选拒绝] | widgetId 指向内置行或他人资产 | bind | 400；无行落库 | C02/B04 | 无 |
| [绑定] 禁用行时 [照实返回] | 绑定 enabled=false | list | 返回含该行 enabled=false | C01 | 无 |
| [开关] 禁用后 [两链路失效] | 绑定 enabled=false | 跑 S5 装配用例口径 | 不进 buildCatalog 输入、不进清单 | C01 | 无 |
| [权限] 无 app:edit 时 [写拒绝] | 仅 app:use 用户 | bind/update/delete | 403 | C04 | 无 |

### S5 对话协议装配（contract: IF-004.buildCatalog + WidgetMiddleware 真链；interfaces API-011/012；spec REQ-WG-D01）

| 测试名 | Given | When | Then | 追溯 | 边界mock |
|---|---|---|---|---|---|
| [装配] 绑定非空时 [清单注入+工具注册] | 应用挂 2 条启用绑定 | run 对话（真 middleware 链，LlmService mock 捕获消息与工具表） | system 消息含两资产清单要素（id/名称/用途/schema/default 示例）；工具表含 render_widget | D01 | LlmService |
| [装配] 纯直聊时 [零注入不注册] | 无应用（直聊通道） | 同上 | 无清单块、无 render_widget；事件词汇无差别 | D01 | LlmService |
| [装配] 绑定全禁用时 [等同未绑定] | 绑定存在但 enabled=false | 同上 | 不注入、不注册 | C01/D01 | LlmService |
| [清单] 超 5000 字符时 [按序截断] | 绑定资产清单文本超限 | buildCatalog + 渲染 | 注入文本 ≤5000 且截断发生在尾部（前序资产完整） | D05/约束 | 无 |
| [清单] 含已删资产时 [跳过降级] | 绑定行指向已删 widgetId | buildCatalog | 返回清单缺该项、不抛错（告警） | D01 | 无 |

### S6 render_widget 执行链（contract: IF-004.resolveSnapshot 经执行器真缝；interfaces API-012；spec REQ-WG-D02~D05）

| 测试名 | Given | When | Then | 追溯 | 边界mock |
|---|---|---|---|---|---|
| [执行] 绑定集合内合法调用时 [快照五要素] | 应用挂资产 W，LLM 调 render_widget(W, key, 合法 data) | executeTool（真工厂路由 type="widget"） | ToolResult.success=true；structured 五要素齐（widgetId/widgetKey/name/template/data）；template 为执行时刻资产内容 | D02/D03 | LlmService |
| [执行] 集合外 widgetId 时 [fail-closed] | 调用指向存在但未绑定的资产 | 同上 | success=false；Error 文本含「未绑定」语义；**不渲染集合外资产** | D02/约束 | LlmService |
| [执行] data 不合 schema 时 [回灌自纠] | data 缺 schema 必填变量 | 同上 | success=false；Error 文本含变量级错误语义；对话上下文可继续（引擎回灌路径，工具不抛断对话） | D04 | LlmService |
| [执行] data 超 32KB 时 [护栏拒绝] | data 序列化 32769+ 字节 | 同上 | success=false；Error 含体积语义 | D05 | LlmService |
| [执行] 资产执行时刻被删时 [fail-closed] | 调用与删除并发（先删后执行） | 同上 | success=false（同 NOT-IN-BINDING 族） | D02 | 无 |
| [快照] 同键两次调用时 [两次定格互不覆写] | 同 widgetKey 连续两次调用（中间改资产） | 两次 execute | 两次快照各自持有调用时刻 template（toolCallRecords 两条记录内容不同） | D03/D06 | LlmService |
| [执行] 无沙箱副作用时 [零写操作] | 任意合法调用 | execute | 无沙箱调用、无外部请求、无业务表写入（仅读） | 约束 | 沙箱 provider mock 断言零交互 |

### S7 回传推进（contract: IF-005.prepare + 编排层；interfaces API-011；spec REQ-WG-E01~E03）

| 测试名 | Given | When | Then | 追溯 | 边界mock |
|---|---|---|---|---|---|
| [回传] 合法 submit 时 [合成推进] | widgetAction(action=sys.submit, payload 表单值) | 编排层带 widgetAction 起流 | 当轮用户消息 = 合成摘要文本（含资产名与动作语义）落库；引擎输入含 [WIDGET_ACTION] 标注块（payload 原文可见）；对话正常产出回复 | E01/E02 | LlmService |
| [回传] payload 超 16KB 时 [拒绝不推进] | payload 16385+ 字节 | prepare / 编排 | ERR-WG-ACTION-OVERSIZE 400（建流前普通 JSON）；无消息落库；会话消息数不变 | E03 | 无 |
| [回传] 非 sys.submit 时 [服务端防御] | action="sys.go_to_url" | prepare | 400；不推进 | E01/IF-005 | 无 |
| [回传] query 为空串时 [以合成为准] | query=""，widgetAction 合法 | 编排 | 落库消息为合成文本（非空串）；无重复消息 | E02 | LlmService |
| [回传] 失败后重试时 [幂等推进] | 第一次 16KB 被拒 | 修正 payload 再发 | 正常推进；无残留半落库状态 | E03/约束 | LlmService |

### S8 历史快照守恒（contract: 消息落库内容；spec REQ-WG-D06）

| 测试名 | Given | When | Then | 追溯 | 边界mock |
|---|---|---|---|---|---|
| [落库] 含卡片消息时 [快照随 toolCalls] | 一轮含 render_widget 的对话完成 | 读 app_conversation_message | toolCalls JSON 含 result.data 五要素（与 SSE tool_result.data 同构） | D06 | LlmService |
| [守恒] 资产删除后 [历史快照不变] | 上述消息已落库 | delete 资产（先解绑）→ 再读消息 | 消息内快照五要素逐字段不变 | D06/A04 | 无 |
| [守恒] 资产修改后 [历史快照不变] | 同上 | update 资产 → 再读消息 | 同上 | A04/D06 | 无 |

### S9 双处/双端守护（约束；对齐 SkillDdlParityTest 先例）

| 测试名 | Given | When | Then | 追溯 | 边界mock |
|---|---|---|---|---|---|
| [DDL] 双处对账时 [零漂移] | schema.sql 与 SchemaInitializer 源码 | 守护测试解析两处 | widget 与 app_widget_binding 的列集/类型/唯一键一致；缺任一处即失败 | 约束 | 无（纯源码断言） |
| [白名单] 双端集合相等时 [通过] | 后端白名单常量 + 前端 registry.ts 源文件 | 守护测试解析两侧组件名集合 | 集合双向相等；单侧多出即失败（前端目录不可达环境 @Tag 跳过并登记） | A06 | 无 |
