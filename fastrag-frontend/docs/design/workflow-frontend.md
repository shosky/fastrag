# 工作流编排 — Frontend Architecture Design（workflow-frontend）

> 由 /idd-design 于 2026-09-21 生成（onboard 模式）。上游：`docs/spec/workflow-frontend.md`。

## 1. Page Map

| PAGE | 路由 | 权限 | 入口 | 退出 |
|---|---|---|---|---|
| 业务流管理 | `/application/:id/workflow-manage`（hidden） | 权限节点在（menu:application:workflow），路由无入口 | 仅 URL 直达 | 返回 |

## 2. Page/Layout/Component Map

```
┌──────────────────────────────────────────────────────────┐
│ 画布头: [业务流名|未选择] [选择][新建][保存]                │
├─────────┬────────────────────────────────────────────────┤
│ 节点工具箱│ 画布区（无第三方库）                              │
│ 🔍过滤    │  SVG 贝塞尔连线（hover 变红提示可删）              │
│ □start   │  ┌节点卡(绝对定位,可拖拽)┐ ┌节点卡┐              │
│ □end     │  └─────────────────────┘ └─────┘              │
│ □llm     │  空态 el-empty                                 │
│ □kb_ret… │                                                │
├─────────┴────────────────────────────────────────────────┤
│ 属性面板(选中节点): Key/类型/位置/配置JSON/入边(✕)/出边(✕)    │
│ 弹窗: 业务流选择(表格) | 创建(名称*) | 节点编辑(按类型字段)    │
└──────────────────────────────────────────────────────────┘
```

组件判定：单页自包含（8 类节点配置字段表 nodeTypeConfigs 页内常量）；无抽组件价值。

## 3. State Ownership

| STATE | 所有者 | 说明 |
|---|---|---|
| 当前流 wfId/wfName | client | 驱动加载与保存 |
| nodes/edges | server → 页内 ref（edges 存 JSON 字符串于 workflow 记录） | 删除节点级联删边 |
| 连接中状态 | client（connectingFrom 两击式） | 查重/自环拦截 |
| 拖拽 | client（mousemove + mouseup 落位 API） | 失败静默 |
| 选中节点 | client | 属性面板 |

## 4. Data Flow

```
loadCanvas: getWorkflowNodes + getWorkflowDetail.edges(JSON.parse) → refs
addNode: 空位计算(防重叠) → 弹窗选类型 → 本地 push（保存时才 updateWorkflowNode 逐个落库）
连线: connectingFrom → 目标 → 查重/自环 → edges.push
保存: FOR node → updateWorkflowNode(吞错) → updateWorkflow({edges: JSON.stringify})
命名转换点: 边序列化（JSON.stringify 整体存 workflow.edges 字段——无边端点）
```

## 5. Visual System

手写画布体系（节点卡片 + SVG 连线 + hover 删边提示）；无设计 token 依赖（页内样式）。桌面优先。

## 6. Parent Trace

- 手写画布（无库） → spec §6（与 feature-map「画布编排」预期对照）
- 边整体 JSON 存储 → spec TASK-003（无独立边端点消费）
- 25 死 API → spec INFERRED-1
