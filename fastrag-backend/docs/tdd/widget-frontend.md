# Widget 前端（widget-frontend）— 测试设计

> 对应契约：docs/ui/ui.md「widget 链」四节（UI 契约 / 状态契约 / 数据映射 / API 依赖，消费 interfaces.md API-001~012 v1.0.0）与 docs/modules/widget-frontend.md `## Contract` 单块。**本文件为测试设计，红绿循环未执行**（由 /idd-implement 按本设计执行；nav-shell-frontend.md 同口径）。
> **mock 边界**：API 层 mock（API-001~010 以 fixture 走 axios mock；API-011/012 以 mock SSE 事件序列走两 composable 既有测试模式）；渲染器、注册表、转换点（toWidgetSnapshot/toPreviewSnapshot/buildWidgetAction）一律真实现直测；禁 mock 内部组件。

## 1. 切片规划

| 切片 | 用户任务 | 测试层 | 覆盖状态 | 执行性 |
|---|---|---|---|---|
| S1 转换点与渲染递归 | TASK-005 底座 | VM/adapter + Component | toWidgetSnapshot/toPreviewSnapshot/buildWidgetAction 纯函数 + WidgetCardRenderer 组件树/插值/合并次序 | AFK |
| S2 白名单↔注册表守护 + 降级矩阵 | TASK-005/007 | 静态断言 + Component | 双端集合相等守护 + STATE-208 四分支降级 | AFK |
| S3 交互窗口与动作语义 | TASK-006 | Component | STATE-209/210 + alwaysEnabled 豁免 + go_to_url/download 本地处理 | AFK |
| S4 回传链路 | TASK-006 | VM + Component | STATE-211/212 + buildWidgetAction 组装 + 防重 + 失败恢复 | AFK |
| S5 工作台三页 | TASK-001/002/003 | Page + Component | STATE-201~206：双 tab/空态二分/行操作/校验错误落 tab/预览态 | AFK |
| S6 绑定面板 | TASK-004 | Component | 候选求差/勾选/enabled/保存 diff/上限 toast | AFK |
| S7 历史回看与双通道同构 | TASK-007 | Component + mock SSE | 历史快照渲染/资产删除仍渲染/会话流与调试流行为一致 | AFK |
| S8 浏览器关键路径 | TASK-005/006 | Browser | 挂应用对话真实出卡 + 提交回传推进 + 历史回看 | 条件（HITL 前置：后端 8081 + 已配置模型 + 绑定资产） |
| S9 视觉与键盘走查 | — | Visual/a11y | 编辑页 768 断点预览折叠 + 卡片交互元素真实 button/input + 焦点随 DOM + 禁 v-html 断言 | HITL |

选择原则：每切片取「能证明该行为的最浅层」——转换点下沉 VM 层纯函数（S1），降级与交互用组件挂载（S2-S4），页面组合与路由进 Page 层（S5/S6），仅真实对话链路进浏览器（S8）；快照渲染正确性是本特性前端最重风险面，S1/S2 最先做。

## 2. 测试用例（逐切片）

### S1 转换点与渲染递归（契约锚点：ui.md「widget 链映射」；modules/widget-frontend.md ## Contract）

文件：`src/utils/widget/snapshot.test.ts`、`src/views/conversation/components/widget/WidgetCardRenderer.test.ts`

- T1.1 `toWidgetSnapshot()`：tool_result.data 五要素逐字段透传；success=false 无 data 时返回 null（STATE-207 路径不产卡）〔API-012〕
- T1.2 `toPreviewSnapshot()`：template+defaultJson → snapshot 且 preview 语义就位（编辑页预览态数据源）〔API-002〕
- T1.3 `buildWidgetAction()`：snapshot + 表单收集值 → `{widgetId,widgetKey,action:'sys.submit',payload}`，表单值并入 payload〔API-011〕
- T1.4 插值合并次序：字符串 prop `{{var}}` 被 default 先填充、data 覆盖——default 独有变量出 default 值、同名出 data 值、两侧皆无为空串〔快照 #16〕
- T1.5 递归渲染：Card>Row>Col>Title/Text 嵌套树渲染出对应组件树；ListView/ListViewItem 列表渲染；Form 内表单控件可输入并可收集〔快照 #15/#17〕
- T1.6 Markdown 组件经 renderMarkdown 输出（含恶意 script 被消毒）；其余组件文本节点渲染无 v-html（DOM 断言 innerHTML 无 raw 注入）〔快照 #18〕
- T1.7 Image src 白名单：http(s) 渲染、javascript:/data: 渲染占位〔快照 #18〕

### S2 白名单↔注册表守护 + 降级矩阵（契约锚点：ui.md STATE-208；快照 #9/#17/#30）

文件：`src/views/conversation/components/widget/registry.test.ts`、`WidgetCardRenderer.test.ts`

- T2.1 守护测试：registry 键集 === 后端白名单常量集（后端常量以 fixture JSON 同步进前端仓或读接口元数据，实现期定通道；单侧多出即失败）〔REQ-WG-A06 对应的前端半边〕
- T2.2 未知组件名 → 卡内占位框 + console.warn，兄弟组件照常渲染（STATE-208）
- T2.3 模板结构坏（children 非数组/props 缺失）→ 占位不抛未捕获异常
- T2.4 快照缺失/null → 整卡占位、正文消息不受影响（TOOLCALL 位其余渲染正常）
- T2.5 加载失败图片 → 占位图，不重试风暴（单次渲染语义）

### S3 交互窗口与动作语义（契约锚点：ui.md STATE-209/210；快照 #14/#19）

文件：`WidgetCardRenderer.test.ts`、`MessageBubble` 集成挂载

- T3.1 卡片在最新助手消息 → interactive=true，动作可点（STATE-209）
- T3.2 其后追加任意新消息 → 置灰 disabled；点击无效果（STATE-210）
- T3.3 `alwaysEnabled=true` 的 go_to_url/download 在置灰窗口内仍可点：新窗打开（window.open 断言）/ 触发下载（a[download] 断言）（STATE-210 豁免）
- T3.4 未知 action type → 忽略 + console.warn，不误跳转不崩卡〔快照 #19〕
- T3.5 preview=true → 全部 sys.submit 类动作禁用 + 提示文案（工作台预览变体，STATE-206）

### S4 回传链路（契约锚点：ui.md STATE-211/212 与 API-011；快照 #13/#31）

文件：`snapshot.test.ts`、消息流组件挂载（mock sendMessage）

- T4.1 sys.submit 点击 → `buildWidgetAction()` 产出 → `sendMessage('', sessionId, widgetAction)` 调用断言（query 空串、字段齐）〔API-011〕
- T4.2 提交即 loading：二次点击不产生第二次 sendMessage（防重，STATE-211）
- T4.3 SSE error 事件 → 提示展示、loading 解除、卡片保持可交互、可直接重试（STATE-212）
- T4.4 回传成功 → 下一轮 assistant 消息到达后原卡片转 STATE-210 置灰（窗口关闭语义）

### S5 工作台三页（契约锚点：ui.md「Widget 工作台」+ 状态契约；TASK-001/002/003）

文件：`src/views/widget/WidgetListPage.test.ts`、`WidgetForm.test.ts`（Page 层挂 router mock + API fixture）

- T5.1 列表加载骨架屏（STATE-201）；双 tab 切换换 builtin 参数重拉（API-001）
- T5.2 我的空态双引导（STATE-202）；官方空态重试（STATE-203）
- T5.3 行操作：编辑跳详情 / 删除 confirm → API-005；409 ERR-WG-BOUND-DELETE → toast「先解绑」文案（TASK-003）
- T5.4 官方行只有查看+复制；复制 → API-006 → 跳新 id 编辑页（TASK-001）
- T5.5 新建页预填骨架示例直接保存成功 → 跳编辑页（TASK-001）
- T5.6 保存 ERR-WG-SCHEMA-INVALID(400) message 按 \n 拆行 → 错误列表落对应 tab、点击错误聚焦 tab、不弹窗（STATE-205，TASK-002）
- T5.7 预览面板：刷新按钮 → toPreviewSnapshot 重渲；sys.submit 禁用 + 提示（STATE-206）
- T5.8 接口失败 toast + 列表类重试按钮（STATE-204）

### S6 绑定面板（契约锚点：ui.md「Widget 绑定面板」；TASK-004）

文件：`src/views/application/editor/components/WidgetConfig.test.ts`

- T6.1 候选 = API-001 自建资产 与 API-007 现绑定求差（内置行/他人资产不出现）
- T6.2 勾选 + enabled 开关 + 保存 → diff 出 POST/PUT/DELETE 精确调用集（照 SkillConfig 模式断言三动词次数与参数）
- T6.3 ERR-WG-BINDING-LIMIT(409) → toast 上限文案（含「10」）
- T6.4 菜单挂点：editor 菜单组出现「配置 > Widget」项，右侧区域切换渲染（PAGE-204 路由不变量）

### S7 历史回看与双通道同构（契约锚点：ui.md「对话卡片渲染面」；TASK-007）

文件：`MessageBubble.test.ts` 扩展 + mock SSE 序列

- T7.1 历史消息 toolCalls 含 result.data 五要素 → 卡片渲染与实时流到达渲染逐 DOM 相同（同构断言）〔API-012〕
- T7.2 mock 资产已删：历史快照仍照常渲染（快照自包含，不发资产详情请求）
- T7.3 同 widgetKey 第二次快照 → 旧卡替换为新卡（单卡 + 内容为新值，含 widget-fade 过渡挂载）
- T7.4 会话中心流与编辑器调试流对同一 tool_result 序列渲染结果一致（同一渲染组件断言）

### S8 浏览器关键路径（条件执行；TASK-005/006）

前置：后端 8081 起、模型可用、绑定一个含表单的测试资产。

- T8.1 编辑器调试对话发「展示订单确认卡片」→ 卡片出现 → 填表提交 → 可见回传消息 + 下一轮回复（真实 SSE 全链）
- T8.2 刷新页面重进会话 → 历史卡片按快照渲染、置灰语义成立
- 无 console error、无未处理网络失败

### S9 视觉与键盘走查（HITL）

- T9.1 编辑页 ≤768 断点预览折叠至内容下方；列表表格横向滚动
- T9.2 卡片交互元素 Tab 焦点顺序随 DOM；真实 button/input 元素（tag 断言已在组件测试覆盖，此处人工复核）
- T9.3 置灰视觉（disabled 样式）与 alwaysEnabled 豁免视觉区分可辨识
