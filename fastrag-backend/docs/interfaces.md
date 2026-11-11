---
name: Interfaces
summary: FastRAG 外部 HTTP/SSE API 契约——Widget 资产对话域与解析策略 excel_row 保存校验域。
type: api
id: DOC-INTERFACES
status: draft
updated: 2026-11-04
satisfies: [SPEC-WIDGET, SPEC-PARSE-STRATEGY]
decisions: [ADR-0010-WIDGET-TOOL-CHANNEL-SNAPSHOT-PROTOCOL, ADR-0012-EXCEL-ROW-SLICE-AT-PARSE]
declares: [ERR-WG-SCHEMA-INVALID, ERR-WG-NOT-IN-BINDING, ERR-WG-BOUND-DELETE, ERR-WG-OVERSIZE, ERR-WG-ACTION-OVERSIZE, ERR-WG-BINDING-LIMIT, ERR-PS-EXT-DOMAIN, ERR-PS-HEADER-RANGE, ERR-PS-SLICE-START, ERR-PS-ROWS-PER-SLICE]
hash: "bcdd11599bb3"
summaryHash: "bcdd11599bb3"
---

# API Contract — FastRAG

**Version:** v1.1.0  **Source:** `docs/spec/widget.md` + `modules/widget.md`（Widget 域）；`docs/spec/parse-strategy.md` §4.8 + `modules/parse-strategy.md`（解析策略 excel_row 增量域，API-013~015）
**Compatibility:** Widget 域全部为新增面；v1.1.0 追加解析策略保存端点的 excel_row 校验契约（存量 CRUD 面早在 v1.0 前存在，本文仅登记 excel_row 增量拒绝面）；后续只增不改（字段退役走硬删除 + 版本主位递增）
**Wire 约定:** 全部 HTTP 端点返回统一包装 `ApiResponse{code:int, data:T, message:string}`——**code=200 成功**，业务错误 = HTTP 200 + 非 200 code（400 校验 / 403 越权 / 404 不存在 / 409 冲突）；`401` 保留给鉴权失效。SSE 端点响应流为 `text/event-stream`，事件词汇见 API-011/012。

## 共享类型 {#shared-types}

```typescript
// 全文 IDL 为 TypeScript（消费方为前端仓）；跨端字段命名驼峰
interface ApiResponse<T> {
  code: number;      // 200=成功；业务错误见各端点 Errors
  message: string;   // 提示信息；校验失败时为逐项行级错误（\n 分隔）
  data: T | null;    // 失败恒为 null
}

interface WidgetSummary {
  id: string;            // String ASSIGN_ID
  name: string;
  description: string | null;
  icon: string | null;   // 前端回退缺省图标
  isBuiltin: boolean;    // true=官方模板行（只读，仅可复制）
  creator: string;       // 属主 userId；"system"=官方播种
  createdAt: string;     // ISO datetime
  updatedAt: string;
}

interface WidgetDetail extends WidgetSummary {
  template: string;      // 组件树 JSON 字符串（{type,props,children[]}，{{var}} 插值）
  schemaJson: string;    // 变量声明 JSON 字符串
  defaultJson: string | null; // 占位数据 JSON 字符串
}

interface WidgetSaveRequest {
  name: string;          // 必填，非空白
  description?: string;
  icon?: string;
  template: string;      // 必填，合法 JSON 组件树
  schemaJson: string;    // 必填，合法 JSON 变量声明
  defaultJson?: string;  // 可选
}

interface WidgetActionPayload {
  widgetId: string;      // 必填，卡片资产标识
  widgetKey: string;     // 必填，卡片替换键（LLM 生成，随快照下发）
  action: string;        // 必填，仅接受 'sys.submit'（回传智能体）
  payload: Record<string, unknown>; // 表单收集值并入的动作负载
}

interface WidgetSnapshot {
  widgetId: string;      // 资产标识
  widgetKey: string;     // 卡片替换键：同键后到快照替换旧卡（过程反馈）
  name: string;          // 展示名
  template: string;      // emit 时刻定格的组件树 JSON（历史回看不受资产编辑影响）
  data: Record<string, unknown>; // 本次渲染数据（default 先填充、data 覆盖的合并前源数据）
}

interface AppWidgetBindingView {
  id: string;            // 绑定行标识
  widgetId: string;
  widgetName: string;
  enabled: boolean;
  createdAt: string;
}
```

## API-001 — 资产列表 {#api-001}

**Endpoint:** `GET /api/widgets`
**Auth:** 登录（JWT / API Token）  **Content-Type:** 无
**Path/Query:** `builtin` (boolean, optional) — true=官方模板视图（is_builtin=1），缺省 false=我的资产（属主过滤，管理员全量）
**Purpose:** 工作台列表页与官方模板 tab 的数据源；属主隔离在服务端裁决。

**Request:** query

```json
{ "builtin": false }
```

**Response:** `200` [`ApiResponse<WidgetSummary[]>`]

```json
{ "code": 200, "message": "success", "data": [ { "id": "1937...", "name": "订单确认卡", "description": "点单信息确认", "icon": null, "isBuiltin": false, "creator": "u_1001", "createdAt": "2026-10-31T10:00:00", "updatedAt": "2026-10-31T10:00:00" } ] }
```

**Errors:**
- `401` 鉴权失效（HTTP 401）— trigger: 未登录/token 失效；recovery: 重新登录。

**Producer constraints:** 全量返回不分页（工作台一期限量资产，对齐技能列表惯例）。
**Invariants:**
- 官方模板视图对全员可见；我的视图仅属主可见资产（管理员加全员）

## API-002 — 资产详情 {#api-002}

**Endpoint:** `GET /api/widgets/{id}`
**Auth:** 登录  **Content-Type:** 无
**Path/Query:** `id` (string, required) — 资产标识
**Purpose:** 编辑页与官方模板查看态加载三件套全量。

**Request:** none

**Response:** `200` [`ApiResponse<WidgetDetail>`]

```json
{ "code": 200, "message": "success", "data": { "id": "1937...", "name": "订单确认卡", "description": null, "icon": null, "isBuiltin": false, "creator": "u_1001", "createdAt": "2026-10-31T10:00:00", "updatedAt": "2026-10-31T10:00:00", "template": "{\"type\":\"Card\",\"props\":{},\"children\":[]}", "schemaJson": "{}", "defaultJson": "{}" } }
```

**Errors:**
- `404`（HTTP 200 + code 404）— trigger: 资产不存在；recovery: 返回列表刷新。
- `403`（HTTP 200 + code 403）— trigger: 非属主读他人自建资产（官方模板行除外）；recovery: 返回列表。

**Producer constraints:** 官方模板行全员可读（只读）。
**Invariants:**
- 非属主不可见他人自建资产，官方行除外（REQ-WG-A03）

## API-003 — 创建资产 {#api-003}

**Endpoint:** `POST /api/widgets`
**Auth:** 登录  **Content-Type:** `application/json`
**Path/Query:** 无
**Purpose:** 新建页提交；服务端三档结构校验通过后落库，保存即生效。

**Request:** body [`WidgetSaveRequest`](#shared-types)

```json
{ "name": "订单确认卡", "description": "点单信息确认", "template": "{\"type\":\"Card\",\"props\":{},\"children\":[]}", "schemaJson": "{}", "defaultJson": "{}" }
```

**Response:** `200` [`ApiResponse<{ id: string }>`]

```json
{ "code": 200, "message": "success", "data": { "id": "1938..." } }
```

**Errors:**
- `ERR-WG-SCHEMA-INVALID` (HTTP 200 + code 400) — trigger: 三档校验任一失败（坏 JSON / 白名单外组件或缺必填属性 / 变量与模板引用不一致或 default 不可反序列化）或 name 空白；recovery: `message` 为逐项行级错误（\n 分隔），前端落对应 tab。
**Producer constraints:** 校验白名单与前端渲染注册表由守护测试钉住一致（REQ-WG-A06）。
**Invariants:**
- 保存成功后立即以新内容服务后续对话（REQ-WG-A04）；新建行 is_builtin=0、creator=当前用户

## API-004 — 修改资产 {#api-004}

**Endpoint:** `PUT /api/widgets/{id}`
**Auth:** 登录  **Content-Type:** `application/json`
**Path/Query:** `id` (string, required) — 资产标识
**Purpose:** 编辑页保存三件套与基础信息；语义与创建同族（全量草稿替换）。

**Request:** body [`WidgetSaveRequest`](#shared-types)（同 API-003 示例）

**Response:** `200` [`ApiResponse<null>`]

```json
{ "code": 200, "message": "success", "data": null }
```

**Errors:**
- `ERR-WG-SCHEMA-INVALID` (HTTP 200 + code 400) — 同 API-003。
- `404` — trigger: 资产不存在；recovery: 返回列表。
- `403` — trigger: 非属主写；recovery: 提示无权限。
- `409`（HTTP 200 + code 409）— trigger: 目标为官方模板行（is_builtin=1 只读）；recovery: 仅可复制。

**Producer constraints:** 无版本流，保存即生效；并发保存后写覆盖（last-write-wins，工作台单人编辑场景）。
**Invariants:**
- 已发出消息的卡片按内嵌快照渲染，不受本次修改影响（REQ-WG-A04）

## API-005 — 删除资产 {#api-005}

**Endpoint:** `DELETE /api/widgets/{id}`
**Auth:** 登录
**Path/Query:** `id` (string, required) — 资产标识
**Purpose:** 删除未绑定的自建资产；绑定保护在服务端裁决。

**Request:** none

**Response:** `200` [`ApiResponse<null>`]

```json
{ "code": 200, "message": "success", "data": null }
```

**Errors:**
- `ERR-WG-BOUND-DELETE` (HTTP 200 + code 409) — trigger: 资产被任一应用绑定（含禁用绑定）；recovery: 提示先在应用配置中解绑。
- `404` / `403` — 同 API-004。
- `409` — trigger: 官方模板行不可删除；recovery: 仅可复制。

**Producer constraints:** 解绑后可删；删除不级联绑定行以外的数据（REQ-WG-A05）。
**Invariants:**
- 删除后运行时按 id 查不到资产，对话不再注入；历史消息按快照照常渲染

## API-006 — 复制资产 {#api-006}

**Endpoint:** `POST /api/widgets/{id}/copy`
**Auth:** 登录
**Path/Query:** `id` (string, required) — 源资产标识（官方模板行或自建资产）
**Purpose:** 官方模板进入使用的唯一通道；自建资产亦可复制派生。

**Request:** none

**Response:** `200` [`ApiResponse<{ id: string }>`]

```json
{ "code": 200, "message": "success", "data": { "id": "1939..." } }
```

**Errors:**
- `404` — trigger: 源资产不存在；recovery: 返回列表刷新。

**Producer constraints:** 深拷贝三件套；新行 is_builtin=0、creator=当前用户（REQ-WG-B03）。
**Invariants:**
- 复制件立即可编辑、可绑定；源官方行保持只读

## API-007 — 绑定列表 {#api-007}

**Endpoint:** `GET /api/apps/{appId}/widgets`
**Auth:** 登录 + `app:use`
**Path/Query:** `appId` (string, required) — 应用标识
**Purpose:** 应用编辑器绑定面板回显现绑定。

**Request:** none

**Response:** `200` [`ApiResponse<AppWidgetBindingView[]>`]

```json
{ "code": 200, "message": "success", "data": [ { "id": "b_001", "widgetId": "1937...", "widgetName": "订单确认卡", "enabled": true, "createdAt": "2026-10-31T10:00:00" } ] }
```

**Errors:**
- `403` — trigger: 无 app:use 权限；recovery: 联系应用编辑者。

**Producer constraints:** 含禁用绑定行（enabled=false 照实返回）。
**Invariants:**
- 候选集合不经本端点——前端以 API-001（自建资产）与现绑定求差集

## API-008 — 新增绑定 {#api-008}

**Endpoint:** `POST /api/apps/{appId}/widgets`
**Auth:** 登录 + `app:edit`
**Content-Type:** `application/json`
**Path/Query:** `appId` (string, required) — 应用标识
**Purpose:** 把自建资产挂到应用；上限与候选口径在服务端裁决。

**Request:** body

```typescript
{
  widgetId: string;  // 必填，须为当前用户可见的自建资产（is_builtin=0）
  enabled?: boolean; // 可选，缺省 true
}
```

```json
{ "widgetId": "1937...", "enabled": true }
```

**Response:** `200` [`ApiResponse<{ id: string }>`]

```json
{ "code": 200, "message": "success", "data": { "id": "b_002" } }
```

**Errors:**
- `ERR-WG-BINDING-LIMIT` (HTTP 200 + code 409) — trigger: 该应用绑定总数（含禁用）达上限 10；recovery: 先解绑再挂。
- `400` — trigger: widgetId 非法 / 指向官方模板行 / 非候选（他人资产或已删除）；recovery: 更换候选。
- `403` — trigger: 无 app:edit 权限；recovery: 联系管理员。
- `409` — trigger: (appId, widgetId) 重复绑定；recovery: 改用 API-009 调整 enabled。

**Producer constraints:** N:N，同对唯一（uk_app_widget）。
**Invariants:**
- 禁用绑定等同未绑定：不注入清单、不渲染卡片（REQ-WG-C01）

## API-009 — 修改绑定 {#api-009}

**Endpoint:** `PUT /api/apps/{appId}/widgets/{bindingId}`
**Auth:** 登录 + `app:edit`
**Content-Type:** `application/json`
**Path/Query:** `appId` (string, required) — 应用标识；`bindingId` (string, required) — 绑定行标识
**Purpose:** 启用/禁用开关切换；禁用即两条链路（注入+渲染）失效。

**Request:** body

```typescript
{ enabled: boolean } // 必填
```

```json
{ "enabled": false }
```

**Response:** `200` [`ApiResponse<null>`]

```json
{ "code": 200, "message": "success", "data": null }
```

**Errors:**
- `404` — trigger: 绑定行不存在或不属于该应用；recovery: 刷新列表。
- `403` — trigger: 无 app:edit 权限。

**Producer constraints:** 不支持换绑 widgetId（换 = 删 + 增）。
**Invariants:**
- enabled=false 后，后续对话不注入该资产清单、卡片不渲染（REQ-WG-C01）

## API-010 — 删除绑定 {#api-010}

**Endpoint:** `DELETE /api/apps/{appId}/widgets/{bindingId}`
**Auth:** 登录 + `app:edit`
**Path/Query:** `appId` (string, required)；`bindingId` (string, required)
**Purpose:** 解绑；是删除资产（API-005）的前置动作。

**Request:** none

**Response:** `200` [`ApiResponse<null>`]

```json
{ "code": 200, "message": "success", "data": null }
```

**Errors:**
- `404` / `403` — 同 API-009。

**Producer constraints:** 纯解绑，不动资产。
**Invariants:**
- 解绑后资产可被删除（解除 ERR-WG-BOUND-DELETE 的触发条件）

## API-011 — 对话流回传字段（两通道请求体扩展）{#api-011}

**Endpoint:** `POST /api/apps/{appId}/chat/stream` 与 `POST /api/conversations/{id}/stream`（SSE）
**Auth:** 应用通道 `app:use`；会话通道登录  **Content-Type:** `application/json`
**Path/Query:** 请求体在既有 `query`(string, required) + `sessionId`(string, optional) 基础上**新增**可选字段 `widgetAction`
**Purpose:** 用户在卡片上提交（sys.submit）时把结构化负载随消息推进新一轮对话；编排层合成可见用户消息并把负载注入引擎上下文。

**Request:** body（扩展字段）

```typescript
{
  query: string;                      // 必填；回传时前端传空串，服务端以 widgetAction 合成
                                      // visibleMessageText 作为当轮用户消息（REQ-WG-E02）
  sessionId?: string;                 // 语义不变（应用通道可选，缺省新建会话）
  widgetAction?: WidgetActionPayload; // 可选；存在时本消息为 Widget 回传推进
}
```

```json
{ "query": "", "sessionId": "s_001", "widgetAction": { "widgetId": "1937...", "widgetKey": "order-confirm", "action": "sys.submit", "payload": { "dine_in": true, "note": "少辣" } } }
```

**Response:** SSE 流（`text/event-stream`），事件词汇与无 widgetAction 的对话完全一致（init/message/thinking/tool_call/tool_result/sources/end/error）。

**Errors:**
- `ERR-WG-ACTION-OVERSIZE` (HTTP 200 + code 400，建流前返回普通 JSON) — trigger: payload 序列化 > 16KB 或 action ≠ 'sys.submit'；recovery: 前端保持卡片可交互，用户可修改重试。
- `401` / `403` — 同各通道既有鉴权。

**Producer constraints:** 回传沿用通道既有鉴权与停止/错误语义，不新增端点（REQ-WG-E04）。
**Invariants:**
- 回传成功必产生一条可见用户消息（合成摘要）落库（REQ-WG-E02）；失败不推进消息序，前端交互窗口不关闭
- 非 sys.submit 动作由前端本地处理（跳转/下载），不进入本字段

## API-012 — 对话流 Widget 快照负载（SSE tool_result 事件扩展）{#api-012}

**Endpoint:** 同 API-011 两通道（SSE 响应负载契约，非新端点）
**Auth:** 同通道
**Path/Query:** 无——本节约定 `tool_call` / `tool_result` 事件上 `name === 'render_widget'` 时的负载形态
**Purpose:** 智能体经 render_widget 工具输出卡片；前端按本节负载渲染 WidgetCardRenderer。零新事件类型（ADR-0010-WIDGET-TOOL-CHANNEL-SNAPSHOT-PROTOCOL）。

**Request:** none（服务端行为契约）

**Response:** SSE 事件负载

```typescript
// tool_call 事件（name='render_widget'）：前端隐藏 calling chip，显示轻量占位（STATE-207）
{ id: string; name: 'render_widget'; arguments: object; index: number; status: 'calling' }

// tool_result 事件（name='render_widget'）：data 为 WidgetSnapshot，其余字段与既有 tool_result 一致
{
  id: string;
  name: 'render_widget';
  success: boolean;      // 校验失败时 false——此时无 data，output 为回灌 LLM 的 Error 文本（前端忽略不渲染）
  output: string;
  durationMs: number;
  data?: WidgetSnapshot; // success=true 时必带；历史消息在 toolCalls JSON 列的 result.data 同构
}
```

```json
{ "id": "tc_9", "name": "render_widget", "success": true, "output": "已生成卡片", "durationMs": 45, "data": { "widgetId": "1937...", "widgetKey": "order-confirm", "name": "订单确认卡", "template": "{\"type\":\"Card\",\"props\":{\"status\":\"pending\"},\"children\":[]}", "data": { "order": "A-1024", "total": 42 } } }
```

**Errors:**
- `ERR-WG-NOT-IN-BINDING` / `ERR-WG-SCHEMA-INVALID` / `ERR-WG-OVERSIZE` — 对话流内工具路径错误（**非 HTTP**）：以 `success:false` + Error 文本回灌智能体自纠，前端无卡片可渲染、对话不中断（REQ-WG-D04）。
**Producer constraints:** 快照五要素在工具执行时刻定格并随 toolCalls 落库；同 widgetKey 的后续快照由前端替换旧卡（过程反馈，REQ-WG-D06）。
**Invariants:**
- 含卡片消息与不含卡片消息在 SSE 事件词汇上无差别（REQ-WG-D07）；历史回放经消息 toolCalls 的 result.data 与实时同构

---

# 解析策略 excel_row 域（API-013~015）

> excel_row（Excel 按行切片）分片类型的策略保存校验契约。存量策略 CRUD 端点语义不变，本节只登记 excel_row 增量拒绝面；分片行为契约见 modules/parse-strategy.md 与 adr/0012。

## API-013 — 创建解析策略 {#api-013}

**Endpoint:** `POST /api/kb/{kbId}/parse-strategies`
**Auth:** KB editor  **Content-Type:** `application/json`
**Path/Query:** `kbId` (string, required) — 知识库标识
**Purpose:** 知识库级命名策略创建；excel_row 增量 = 第六种分片类型策略的三参配置与保存期校验（REQ-H01~H04），创建的策略一律非默认（REQ-A03）。

**Request:** body `ParseStrategySaveRequest`（excel_row 相关面；其余字段沿用既有保存语义）

```typescript
interface ParseStrategySaveRequest {
  name: string;            // 必填 ≤50 字符（知识库级；文件级可空缺省「自定义」）
  description?: string;    // 可选 ≤200 字符（知识库级超长拒绝；文件级静默截断）
  parseMethod: string;     // 必填，九型注册表内；excel_row 建议 'xlsx'（'default' 亦可，判定看扩展名）
  extensions: string[];    // 必填，带点小写；chunk.strategy=excel_row 时必须 ⊆ ['.xlsx','.xls']
  llmModel?: string;       // 可选
  advanced?: {             // 自由 JSON（Map 透传），分组结构 parse/chunk/index
    chunk?: {
      strategy?: string;   // 六型之一（REQ-H01）
      excelRow?: {
        headerStartRow: number;  // strategy=excel_row 时必填，1-based ≥1
        headerEndRow: number;    // 必填，≥headerStartRow 且区间 ≤5 行
        sliceStartRow: number;   // 必填，> headerEndRow
        rowsPerSlice?: number;   // 可选，缺省 100，≥1
      };
    };
  };
}
```

```json
{ "name": "Excel 按行切片", "parseMethod": "xlsx", "extensions": [".xlsx"], "advanced": { "chunk": { "strategy": "excel_row", "excelRow": { "headerStartRow": 1, "headerEndRow": 2, "sliceStartRow": 3, "rowsPerSlice": 50 } } } }
```

**Response:** `200` `ApiResponse<ParseStrategyDto>`（data 含新策略 id 与回读字段）

**Errors:**
- `ERR-PS-EXT-DOMAIN` (HTTP 200 + code 400) — trigger: chunk.strategy=excel_row 且 extensions 含 `.xlsx/.xls` 之外扩展名（REQ-H02）；recovery: 收窄扩展名或改选其他分片类型。
- `ERR-PS-HEADER-RANGE` (HTTP 200 + code 400) — trigger: headerStartRow > headerEndRow，或区间行数 > 5（REQ-H03）；recovery: 修正表头起止行。
- `ERR-PS-SLICE-START` (HTTP 200 + code 400) — trigger: sliceStartRow ≤ headerEndRow（REQ-H03）；recovery: 切分起始行必须大于表头结束行。
- `ERR-PS-ROWS-PER-SLICE` (HTTP 200 + code 400) — trigger: rowsPerSlice < 1（REQ-H04）；recovery: 改为 ≥1；上限由 4800 字符硬限兜底。
- `400` — trigger: name 缺失/超长、extensions 空、parseMethod 不在注册表或与扩展名不兼容（REQ-A03、REQ-G04）；recovery: 按 message 修正。

**Producer constraints:** excel_row 校验按「请求优先、否则沿用」的生效值执行（更新场景 REQ-A04）；扩展名冲突检测为独立软约束接口，存在冲突不阻断保存（REQ-F02）。
**Invariants:**
- 校验对 advanced 新分组与旧平铺双格式一致生效（REQ-G02）
- 创建成功的策略 is_default=0；库内配置运行时被信任、不再二次校验

## API-014 — 更新解析策略 {#api-014}

**Endpoint:** `PUT /api/kb/{kbId}/parse-strategies/{id}`
**Auth:** KB editor  **Content-Type:** `application/json`
**Path/Query:** `kbId` (string, required) — 知识库标识；`id` (string, required) — 策略标识
**Purpose:** 部分更新：请求 null 字段沿用现有值，解析方法与分片参数按生效值校验（REQ-A04）；excelRow 组在切回其他策略类型时残留无副作用。

**Request:** body `ParseStrategySaveRequest`（同 API-013；null/缺省字段沿用现有值）

**Response:** `200` `ApiResponse<ParseStrategyDto>`

**Errors:**
- `ERR-PS-EXT-DOMAIN` / `ERR-PS-HEADER-RANGE` / `ERR-PS-SLICE-START` / `ERR-PS-ROWS-PER-SLICE` (HTTP 200 + code 400) — 同 API-013，按「请求优先、否则沿用」的生效值触发（REQ-A04）。
- `404` — trigger: 策略不存在（REQ-A02）；recovery: 返回列表。
- `400` — 同 API-013 基础校验。

**Producer constraints:** advanced 整组按请求覆盖；excel_row → 其他策略类型切换时 excelRow 组不删除、仅不再消费。
**Invariants:**
- 同 API-013；更新不改变默认策略语义（设默认走独立端点 REQ-B01）

## API-015 — 保存文件级自定义策略 {#api-015}

**Endpoint:** `POST /api/kb/{kbId}/files/{id}/strategy`
**Auth:** KB editor  **Content-Type:** `application/json`
**Path/Query:** `kbId` (string, required) — 知识库标识；`id` (string, required) — 文件标识
**Purpose:** 文档级覆盖载体：upsert 保存文件级专属策略（REQ-D01）并在同一接口原子完成重切（REQ-E04）；excel_row 校验面与 API-013 同族。

**Request:** body `ParseStrategySaveRequest`（同 API-013；name 可空缺省「自定义」、description 超长静默截断至 200，REQ-D02）

**Response:** `200` `ApiResponse<FileDto>`（保存即重切，返回重切后的文件 DTO）

**Errors:**
- `ERR-PS-EXT-DOMAIN` / `ERR-PS-HEADER-RANGE` / `ERR-PS-SLICE-START` / `ERR-PS-ROWS-PER-SLICE` (HTTP 200 + code 400) — 同 API-013。
- `404` — trigger: 文件不存在；recovery: 返回文件列表。

**Producer constraints:** 保存与重切原子完成，无「已保存未重切」中间态（REQ-E04）；同文件重复保存为 upsert（REQ-D01）。
**Invariants:**
- 文件级策略不入管理列表、不参与自动匹配、随文件删除级联清理（REQ-D01、REQ-C02）
