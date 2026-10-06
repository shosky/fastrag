# 工作流编排 — Implementation Document（workflow-frontend）

> 由 /idd-implement 于 2026-09-21 生成（onboard 特征化模式）。

## 1. Code File Map

```
src/views/application/workflow-manage.vue — 唯一页面（525：手写画布/工具箱/属性面板/3 弹窗）
src/api/index.ts                          — workflows 33 端点（实消费 8）
src/types/workflow.ts                     — ~~死类型~~（唯一消费方 mock/workflow.ts 亦死）
src/mock/workflow.ts                      — ~~死 mock~~（219 行零 import）
```

## 2. Interface Implementations

### 画布交互

**Logic Flow:**
```
addNode: 找空位(x/y 网格防重叠) → 类型弹窗 → 本地 push（保存时才落库）
连线: connectingFrom = 源 → 点目标「连接」→ IF 重复边 OR 自环 THEN toast 拦截 ELSE edges.push
拖拽: mousedown 记 offset → document mousemove 改 positionX/Y → mouseup → moveWorkflowNode（失败静默）
删除节点: 节点 + 关联边级联移除
保存: FOR node → updateWorkflowNode(node)（单节点 catch{} 吞错）
      → updateWorkflow(wfId, {edges: JSON.stringify(edges)}) → toast「画布已保存」
```

### 页面登记表

| 能力 | 要点 | 证据 |
|---|---|---|
| 类型配置字段 | llm（model/temperature/maxTokens/systemPrompt/outputFormat）、kb_retrieval（kbId/topK/similarityThreshold/mode）、function_request 等 | :124-162 |
| 属性面板 | 只读 Key/类型/位置/配置 JSON/入出边 ✕ | :369-392 |
| 三处路径不一致 | 路由 `/:id/workflow-manage` ≠ 权限页选项（无 :id）≠ 权限表 `/application/workflow`（不存在） | routes:216; permissions:87; auth:508 |

## 3. Decisions & Deviations

- **25/33 死 API**（发布/执行/调试/用例/监控/模板/迁移/优化 + add/deleteNode/Edge + nodeConfig）——「UI 明显落后于 API」量化成立（INFERRED-1）。
- **死类型/死 mock**：types/workflow（NodeType 双枚举不一致：页面 8 种 vs 类型 7 种含 plugin）+ mock/workflow（INFERRED-4）。
- **命名三称**：页面「业务流」/权限「业务流」/feature-map「工作流」——术语裁决归 CONTEXT（INFERRED-5）。
- 无 contract↔代码矛盾（特征化模式）。

## 4. Test Evidence

tdd 全局 blocked。解阻后优先切片：连线查重/自环、边序列化往返（parse/stringify）、删除节点级联。

## 5. Implementation Trace

```
TASK-001 → 选择/创建/删除 → :28-52,315-320,396-428
TASK-002 → 画布编排 → :107-283
TASK-003 → 保存 → :84-100
TASK-004 → 属性面板 → :369-392
```
