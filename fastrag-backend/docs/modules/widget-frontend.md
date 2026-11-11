---
name: Widget Frontend
summary: Widget 前端架构——工作台三页、应用编辑器绑定面板、对话卡片渲染组件族与回传链路；WidgetCardRenderer 为三处消费的唯一渲染入口。
type: module
id: MOD-WIDGET-FRONTEND
profile: frontend
status: draft
updated: 2026-10-31
satisfies: [SPEC-WIDGET-FRONTEND]
decisions: [ADR-0010-WIDGET-TOOL-CHANNEL-SNAPSHOT-PROTOCOL, ADR-0011-WIDGET-TEMPLATE-DSL]
hash: "ebd8505c6711"
summaryHash: "ebd8505c6711"
---

## 职责 {#responsibility}

Widget 前端三面：开发工作台（/widget 页面族承载三件套 JSON 编辑与静态预览）、应用编辑器绑定面板（第六类绑定 UI）、对话卡片渲染组件族（toolDisplay 增 `widget` 型路由 → WidgetCardRenderer 递归渲染 + 交互窗口 + 回传），渲染组件为会话流/调试流/工作台预览三处共用的唯一入口。

## 契约 {#contract}
**属性：**
```ts
// WidgetCardRenderer —— 卡片渲染唯一入口（conversation 消息流 / AppChat 调试流 / 工作台预览 三处消费）
interface WidgetCardRendererProps {
  snapshot: WidgetSnapshot;   // 五要素：widgetId/widgetKey/name/template/data（工具结果结构化通道 or 预览派生）
  interactive: boolean;       // 交互窗口开闭：仅最新一条助手消息为 true（STATE-209/210）
  preview?: boolean;          // 预览态：sys.submit 类动作禁用 + 提示文案（STATE-206，工作台专用）
}
```
**事件：**
- `action(action: WidgetActionPayload)` — 用户点击 sys.submit 类动作且 interactive && !preview 时触发，由消息流层组装回传（工作台预览不触发）
**插槽：** 无（卡片内部组件树由 snapshot.template 驱动，不接受外部插槽注入——安全边界）
**类型：**
```ts
// src/types/widget.ts —— 全部 Widget VM 类型唯一定义点
interface WidgetSnapshot { widgetId: string; widgetKey: string; name: string; template: string; data: Record<string, unknown>; }
interface WidgetActionPayload { widgetId: string; widgetKey: string; action: 'sys.go_to_url' | 'sys.download' | 'sys.submit'; payload: Record<string, unknown>; }
// src/utils/toolDisplay.ts —— ToolDisplayType 联合类型增 'widget'；resolveToolDisplayType 对工具名 render_widget 全等命中
// src/views/conversation/components/widget/registry.ts —— 组件名 → SFC 注册表，与后端白名单由守护测试钉住一致
```
**不变量：**
- 未知组件名 / 模板结构坏 / 快照缺失 → 卡内占位不崩整卡（fail-soft，console.warn，STATE-208）；正文渲染不受影响
- 渲染数据 = default 先填充、data 覆盖的合并结果（{{var}} 插值仅作用于字符串 prop）；除 Markdown 组件经 renderMarkdown 消毒外禁 v-html；Image src 仅 http(s)
- 同一消息流内同 widgetKey 后到快照替换旧卡（过程反馈，替换动画见视觉节）
- 交互窗口按消息序判定：卡片所在消息之后出现任何新消息即置灰（alwaysEnabled 动作豁免）

## 接口 {#interface}
### WidgetConfig 面板
**Purpose:** 应用编辑器「配置」组第六项，第六类绑定 UI（照 SkillConfig 模式）。
**Signature:**
```ts
// props 与五兄弟一致；保存 = 候选勾选集 × enabled 与现绑定 diff 增删
defineProps<{ appInfo: AppInfo }>()
```

## 页面 {#page}

- **PAGE-201 Widget 列表页** — `/widget`；资源段第 9 项菜单进入（nav-segments.ts 资源段 items 追加）；页面族返回目标；无菜单权限键不入权限树。
- **PAGE-202 Widget 新建页** — `/widget/create`；列表「新建」进入；保存成功/取消回 PAGE-201；meta `{ title: '新建 Widget', hidden: true }`（照技能路由块，AppLayout children 平铺）。
- **PAGE-203 Widget 编辑页** — `/widget/:id`；列表行进入（官方复制成功跳转）；返回 PAGE-201。
- **PAGE-204 应用编辑器 Widget 绑定面板** ———非路由，editor/index.vue 菜单组「配置」增 `{ key: 'widget', label: 'Widget' }`，右侧区域 v-if 切换渲染。
- **PAGE-205 对话卡片渲染面** ———非路由，MessageBubble 的 ToolCallRenderer 分发链上以 `displayType === 'widget'` 渲染 WidgetCardRenderer；会话中心与应用调试对话两处自动同构。

## 布局 {#layout}

**PAGE-201 列表页（tabs + 表格）：**
```
┌──────────────────────────────────────────────┐
│ WidgetListPage                               │
│ ┌────────────────┬──────────────────┐        │
│ │ 我的 Widget     │ 官方模板          │  tabs  │
│ └────────────────┴──────────────────┘        │
│ ┌──────────────────────────────────────────┐ │
│ │ [新建]（仅我的 tab）                       │ │
│ ├──────────────────────────────────────────┤ │
│ │ 名称 | 描述 | 图标 | 更新时间 | 操作       │ │
│ │  …   | …    | …    | …       | 编辑/删除/复制│
│ └──────────────────────────────────────────┘ │
└──────────────────────────────────────────────┘
```
操作列随 tab 变化：我的=编辑/删除；官方=复制（只读行标识）。

**PAGE-202 新建页（表单）：**
```
┌──────────────────────────────────────────────┐
│ WidgetCreatePage                             │
│ 名称*  [__________]  描述 [__________]       │
│ 图标   [选择/留空回退]                        │
│ 三件套初始骨架（模板/数据结构/默认值 三文本域，│
│  预填最小合法示例，可清空改写）                │
│            [取消]  [保存]                     │
└──────────────────────────────────────────────┘
```

**PAGE-203 编辑页（三 tab + 预览，desktop）：**
```
┌──────────────────────────────────────────────┐
│ WidgetEditPage   名称/描述/图标（可编辑）      │
├──────────────┬───────────────────────────────┤
│ [模板]        │  预览面板（WidgetCardRenderer）│
│ [数据结构]    │  ┌─────────────────────────┐ │
│ [默认值]      │  │ default 填充静态渲染      │ │
│              │  │ sys.submit 禁用+提示       │ │
│ ┌──────────┐ │  └─────────────────────────┘ │
│ │JSON 文本域│ │  [刷新预览]                   │
│ │[格式化]   │ │                              │
│ │错误区(行级)│ │                              │
│ └──────────┘ │                              │
│        [保存] │                              │
└──────────────┴───────────────────────────────┘
```
<!-- mobile (≤768px)：预览折叠到编辑区下方单列 -->

**PAGE-204 绑定面板（checkbox 卡片列表）：**
```
┌──────────────────────────────────────────────┐
│ WidgetConfig（编辑器右侧区域）                 │
│ 候选（仅自建资产）                             │
│ ┌────────────────────────────────────────┐   │
│ │ [x] 订单确认卡   描述…   启用 [switch]   │   │
│ │ [ ] 播客进度卡   描述…                   │   │
│ └────────────────────────────────────────┘   │
│ 上限 10 · [保存]                              │
└──────────────────────────────────────────────┘
```

**PAGE-205 卡片渲染面（消息气泡内）：**
```
│ ┌────────────────────────────────┐ │
│ │ WidgetCard        状态文本区     │ │ ← snapshot.name + Card.status
│ ├────────────────────────────────┤ │
│ │ 组件树递归渲染区                 │ │ ← registry 按 type 分发
│ │ (Title/Text/Form/Input/…)      │ │
│ ├────────────────────────────────┤ │
│ │ [取消]  [确认]  [下载]           │ │ ← 动作行；置灰=disabled
│ └────────────────────────────────┘ │
```

组件清单（一行职责）：`WidgetListPage`（双 tab 过滤与行操作）、`WidgetCreatePage`/`WidgetEditPage` 共享 `WidgetForm`（基础信息+三件套三 tab+校验错误区）、`PreviewPanel`（预览变体）、`WidgetConfig`（绑定 diff）、`WidgetCardRenderer`（递归渲染+降级+交互窗口）、`widgetComponents/*`（19 组件 SFC）、`registry.ts`（注册表）。

## 状态 {#states}

- **STATE-201 列表加载** — 归属：server（API in flight）→ 页面本地 ref。骨架屏。
- **STATE-202 我的空态** — 归属：server（列表返回空）。「新建 / 去官方模板复制」双引导。
- **STATE-203 官方空态** — 归属：server（播种失败兜底）。重试拉取。
- **STATE-204 接口失败** — 归属：server（非 0 body.code / 网络错误）。toast + 列表类重试。
- **STATE-205 校验失败行级** — 归属：form（保存业务错误逐项信息）。错误区落对应 tab，点击聚焦。
- **STATE-206 预览态** — 归属：form（编辑内容派生，手动刷新触发）。preview=true 变体。
- **STATE-207 调用中占位** — 归属：server 流（tool_call 事件）。chip 隐藏/轻量占位。
- **STATE-208 降级占位** — 归属：client（渲染时判定）。卡内占位不崩整卡。
- **STATE-209 卡片可交互** — 归属：client（消息序派生：所在消息=最新助手消息）。动作正常态。
- **STATE-210 卡片置灰** — 归属：client（消息序派生 + 动作 alwaysEnabled）。disabled；豁免动作可点。
- **STATE-211 回传提交中** — 归属：form（提交动作本地态）。loading 防重。
- **STATE-212 回传失败** — 归属：server 流（error 事件）。卡片恢复可交互可重试。

无状态双 owner：交互窗口（209/210）由消息数组派生的计算属性唯一判定；回传生命周期（211/212）由卡片动作组件本地态承载。

## 数据 {#data}

- **API Contract 全部 TBD**（widget 契约待 `/idd-contract`）——以下为命名转换点，契约产出后仅填字段名不改结构：
- `src/api/widget.ts`（新文件，命名导出照 api/index.ts 惯例）：getWidgets/getWidget/createWidget/updateWidget/deleteWidget/copyWidget + 绑定四动词 + 候选列表 → **VM 转换点 `toWidgetListItem()` / `toWidgetDraft()`**（同一文件内导出）
- 实时卡片：SSE `tool_result` 事件 `data` → **`toWidgetSnapshot()`**（`src/utils/widget/snapshot.ts`，唯一转换点）→ WidgetCardRenderer.snapshot
- 历史卡片：消息 toolCalls JSON（parseMaybeJson 既有路径）→ result.data → 同一 `toWidgetSnapshot()`（实时/历史同构）
- 预览：编辑页 draft.template + default_json → **`toPreviewSnapshot()`**（同文件）→ snapshot + preview=true
- 回传：卡片动作 → **`buildWidgetAction()`**（snapshot + 表单收集值）→ `sendMessage(query, sessionId, widgetAction?)`（useAppChatStream.ts:85 签名加可选参；useConversationStream.ts:80 请求体同字段）
- 绑定面板：候选列表 + 现绑定 → 本地勾选集 → diff(保存时) → 四动词（照 SkillConfig 模式）

## 视觉 {#visual}

- 卡片容器：`--el-border-radius-base` 圆角 + `--el-border-color-lighter` 边框 + `--el-fill-color-blank` 底；内边距 16px；最大宽度取消息气泡既有宽度（不新增断点体系）
- 状态文本区：`--el-text-color-secondary`、12px、置卡片顶部
- 动作按钮：`el-button size="small"`；置灰用原生 disabled 态（`--el-button-disabled-text-color`）；`alwaysEnabled` 动作在置灰窗口内保持 enabled 视觉
- 卡片替换过渡：Vue `<Transition name="widget-fade">`，opacity 150ms ease（同键快照替换）
- 断点：768 以下编辑页预览折叠至编辑区下方（平台既有断点档，不新增 token 文件）
- 图标：资产 icon 为空回退 Element Plus `Postcard`；列表/卡片共用
- 表单组件：一律 Element Plus 真实控件（el-input/el-select/el-radio-group/el-checkbox），焦点顺序随 DOM；不做 skip-link/ARIA 增强（门户链口径）

## 边界设计 {#seam}

**组件注册表缝** — registry.ts（组件名 → SFC）与后端白名单双份常量 + 守护测试；新增组件 = 两端各加一项 + 测试各加一条，渲染器与分发链零改动。稳定侧：WidgetCardRenderer props（snapshot/interactive/preview 三参）与 `resolveToolDisplayType` 的 `'widget'` 型。

**渲染位置缝** — 三消费方（会话中心流 / 应用调试流 / 工作台预览）共用同一 WidgetCardRenderer：行为一致性（降级、消毒、插值、交互窗口）由同组件保证；预览 = `interactive=false + preview=true` 变体而非平行实现。

**流通道缝** — 两 composable 的 `sendMessage` 加可选 widgetAction 参数：请求体构造点是唯一改动位；流内事件词汇零变化（ADR-0010-WIDGET-TOOL-CHANNEL-SNAPSHOT-PROTOCOL）。

**深化机会** — 前端保存前预校验（注册表元数据携带必填属性清单，保存前本地报错减少一次往返）、组件属性面板化（未来可视化编辑器的种子）均可吸收进 registry 元数据，不改渲染接口。

## 追溯 {#trace}

- 渲染正确性（S1/S2）→ TASK-005/007（WidgetCardRenderer.test + registry.test + snapshot.test）
- 交互窗口与动作词汇（S3）→ TASK-006（WidgetCardRenderer.test STATE-209/210/alwaysEnabled 断言）
- 回传链路（S4）→ TASK-006（snapshot.test buildWidgetAction + MessageBubble/两 composable 接线）
- 工作台三页（S5）→ TASK-001/002/003（index.test.ts STATE-201~204 + WidgetForm 本地校验/预览）
- 绑定面板（S6）→ TASK-004（WidgetConfig.test.ts 候选/diff/上限）
- 历史同构（S7）→ TASK-007（registry/渲染器测试 + toolCalls result.data 同构断言）
- 质量门：vitest 24 文件 200/200 绿（含 widget 域 9 个测试文件）；vue-tsc -b 零错误
- 浏览器关键路径（S8，条件执行）与视觉键盘走查（S9，HITL）未执行——真实联调（后端 8081 + 模型 + 绑定资产）待用户指令

## 代码 {#code}

```yaml
- path: ../fastrag-frontend/src/types/widget.ts
  description: Widget 全量 TS 类型（interfaces.md 共享类型的前端投影，唯一定义点）
- path: ../fastrag-frontend/src/api/widget.ts
  description: API-001~010 封装 + toWidgetListItem/toWidgetDraft 转换点 + 候选端点 + 回退图标常量
- path: ../fastrag-frontend/src/utils/widget/snapshot.ts
  description: 三个命名转换点 toWidgetSnapshot/toPreviewSnapshot/buildWidgetAction + 模板解析/插值/合并纯函数
- path: ../fastrag-frontend/src/utils/toolDisplay.ts
  description: 修改——ToolDisplayType 增 widget 型（render_widget 全等最高优先命中）
- path: ../fastrag-frontend/src/views/conversation/components/widget/WidgetCardRenderer.vue
  description: 卡片渲染唯一入口（递归渲染/fail-soft 降级/交互窗口/动作分发，三处消费）
- path: ../fastrag-frontend/src/views/conversation/components/widget/registry.ts
  description: 组件名→SFC 注册表（双端白名单守护的前端半边）
- path: ../fastrag-frontend/src/views/conversation/components/widget/whitelist.ts
  description: 前端白名单常量（与后端 WidgetComponentWhitelist 对账）
- path: ../fastrag-frontend/src/views/conversation/components/widget/WidgetNode.vue
  description: 递归节点渲染器（树遍历 + 插值 + 降级占位）
- path: ../fastrag-frontend/src/views/conversation/components/widget/widgetComponents/Badge.vue
  description: Widget 组件 Badge（模板 DSL 的 Badge 型渲染单元，一期 19 组件之一）
- path: ../fastrag-frontend/src/views/conversation/components/widget/widgetComponents/Button.vue
  description: Widget 组件 Button（模板 DSL 的 Button 型渲染单元，一期 19 组件之一）
- path: ../fastrag-frontend/src/views/conversation/components/widget/widgetComponents/Caption.vue
  description: Widget 组件 Caption（模板 DSL 的 Caption 型渲染单元，一期 19 组件之一）
- path: ../fastrag-frontend/src/views/conversation/components/widget/widgetComponents/Card.vue
  description: Widget 组件 Card（模板 DSL 的 Card 型渲染单元，一期 19 组件之一）
- path: ../fastrag-frontend/src/views/conversation/components/widget/widgetComponents/Checkbox.vue
  description: Widget 组件 Checkbox（模板 DSL 的 Checkbox 型渲染单元，一期 19 组件之一）
- path: ../fastrag-frontend/src/views/conversation/components/widget/widgetComponents/Col.vue
  description: Widget 组件 Col（模板 DSL 的 Col 型渲染单元，一期 19 组件之一）
- path: ../fastrag-frontend/src/views/conversation/components/widget/widgetComponents/Divider.vue
  description: Widget 组件 Divider（模板 DSL 的 Divider 型渲染单元，一期 19 组件之一）
- path: ../fastrag-frontend/src/views/conversation/components/widget/widgetComponents/Form.vue
  description: Widget 组件 Form（模板 DSL 的 Form 型渲染单元，一期 19 组件之一）
- path: ../fastrag-frontend/src/views/conversation/components/widget/widgetComponents/Icon.vue
  description: Widget 组件 Icon（模板 DSL 的 Icon 型渲染单元，一期 19 组件之一）
- path: ../fastrag-frontend/src/views/conversation/components/widget/widgetComponents/Image.vue
  description: Widget 组件 Image（模板 DSL 的 Image 型渲染单元，一期 19 组件之一）
- path: ../fastrag-frontend/src/views/conversation/components/widget/widgetComponents/Input.vue
  description: Widget 组件 Input（模板 DSL 的 Input 型渲染单元，一期 19 组件之一）
- path: ../fastrag-frontend/src/views/conversation/components/widget/widgetComponents/ListView.vue
  description: Widget 组件 ListView（模板 DSL 的 ListView 型渲染单元，一期 19 组件之一）
- path: ../fastrag-frontend/src/views/conversation/components/widget/widgetComponents/ListViewItem.vue
  description: Widget 组件 ListViewItem（模板 DSL 的 ListViewItem 型渲染单元，一期 19 组件之一）
- path: ../fastrag-frontend/src/views/conversation/components/widget/widgetComponents/Markdown.vue
  description: Widget 组件 Markdown（模板 DSL 的 Markdown 型渲染单元，一期 19 组件之一）
- path: ../fastrag-frontend/src/views/conversation/components/widget/widgetComponents/RadioGroup.vue
  description: Widget 组件 RadioGroup（模板 DSL 的 RadioGroup 型渲染单元，一期 19 组件之一）
- path: ../fastrag-frontend/src/views/conversation/components/widget/widgetComponents/Row.vue
  description: Widget 组件 Row（模板 DSL 的 Row 型渲染单元，一期 19 组件之一）
- path: ../fastrag-frontend/src/views/conversation/components/widget/widgetComponents/Select.vue
  description: Widget 组件 Select（模板 DSL 的 Select 型渲染单元，一期 19 组件之一）
- path: ../fastrag-frontend/src/views/conversation/components/widget/widgetComponents/Text.vue
  description: Widget 组件 Text（模板 DSL 的 Text 型渲染单元，一期 19 组件之一）
- path: ../fastrag-frontend/src/views/conversation/components/widget/widgetComponents/Title.vue
  description: Widget 组件 Title（模板 DSL 的 Title 型渲染单元，一期 19 组件之一）
- path: ../fastrag-frontend/src/components/ToolCallRenderer.vue
  description: 修改——widget 分发分支 + interactive 透传 + widget-action 事件（默认参保证既有调用方零影响）
- path: ../fastrag-frontend/src/views/conversation/components/MessageBubble.vue
  description: 修改——interactive/widget-action 穿透
- path: ../fastrag-frontend/src/views/conversation/index.vue
  description: 修改——最新助手消息 interactive 判定 + handleWidgetAction（query 空串 + widgetAction 回传）
- path: ../fastrag-frontend/src/composables/useAppChatStream.ts
  description: 修改——sendMessage 加 widgetAction 可选参 + 请求体扩展（API-011）
- path: ../fastrag-frontend/src/composables/useConversationStream.ts
  description: 修改——同上（会话通道）
- path: ../fastrag-frontend/src/views/application/editor/components/AppChat.vue
  description: 修改——调试对话 widgetAction 透传（快照 #29 调试即真实环境）
- path: ../fastrag-frontend/src/views/widget/index.vue
  description: 列表页（PAGE-201 双 tab + STATE-201~204 + 行操作）
- path: ../fastrag-frontend/src/views/widget/WidgetCreate.vue
  description: 新建页（PAGE-202，骨架示例预填）
- path: ../fastrag-frontend/src/views/widget/WidgetEdit.vue
  description: 编辑页（PAGE-203，详情回显 + 预览面板）
- path: ../fastrag-frontend/src/views/widget/WidgetForm.vue
  description: 共享表单（三件套三 tab JSON 文本域/本地预校验/行级错误落 tab/预览变体）
- path: ../fastrag-frontend/src/views/application/editor/components/WidgetConfig.vue
  description: 绑定面板（PAGE-204，候选求差 + enabled 开关 + 保存 diff + 上限预拦截）
- path: ../fastrag-frontend/src/router/routes.ts
  description: 修改——/widget 三路由（AppLayout children 平铺，meta hidden）
- path: ../fastrag-frontend/src/config/nav-segments.ts
  description: 修改——资源段末项 widget（PAGE-201，无权限键不入权限树）
- path: ../fastrag-frontend/src/views/application/editor/index.vue
  description: 修改——配置组 widget 菜单项 + WidgetConfig 挂载分支
- path: ../fastrag-frontend/src/utils/widget/snapshot.test.ts
  description: S1/S4 转换点直测（T1.1~T1.4/T4.1）
- path: ../fastrag-frontend/src/views/conversation/components/widget/WidgetCardRenderer.test.ts
  description: S2/S3 渲染/降级/交互窗口断言
- path: ../fastrag-frontend/src/views/conversation/components/widget/registry.test.ts
  description: S2 注册表守护
- path: ../fastrag-frontend/src/views/widget/index.test.ts
  description: S5 列表页状态矩阵断言（T5.1~T5.8）
- path: ../fastrag-frontend/src/views/application/editor/components/WidgetConfig.test.ts
  description: S6 绑定面板断言（T6.1~T6.3）
```


