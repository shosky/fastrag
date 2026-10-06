# 应用中心 — Frontend Architecture Design（app-center-frontend）

> 由 /idd-design 于 2026-09-21 生成（onboard 模式）。上游：`docs/spec/app-center-frontend.md`。

## 1. Page Map

| PAGE | 路由 | 可达性 |
|---|---|---|
| 应用列表 | `/application` | ✅ Sidebar:126 |
| 编辑器 | `/application/:id/editor` | ✅ 卡片点击 |
| app-config | `/application/:id/config` | ❌ 孤儿路由 |
| runtime | `/application/runtime`（✅）+ `/:id/runtime`（❌ 参数被忽略） | query.appId 驱动 |

## 2. Page/Layout/Component Map

```
列表: [搜索][标签▾][添加应用(三类型)] 卡片网格(类型徽标/标签/creator)
      卡片: [▶运行][复制ID][删除] · 卡体→编辑器 · el-empty
编辑器: ┌菜单(配置7+对话2)─┬──────────────────┐
        │ BasicConfig      │ 按 activeMenu v-if │
        │ Knowledge/Skill/ │ 挂 9 面板           │
        │ Tool/Mcp/Database│（6 孤儿面板不在菜单）│
        │ Member/ChatLogs  │                    │
        │ AppChat          │                    │
        └──────────────────┴────────────────────┘
runtime: 顶栏(返回/会话▾/侧栏开关) + 消息流(ThinkingBlock/ToolCallRenderer/
         来源+图片) + 输入区(附件/模型/应用/发送停止) + 右侧会话面板
共享件: ToolCallRenderer（工具卡折叠）/ ThinkingBlock（思考折叠）——双消费者 ✅
```

## 3. State Ownership

| STATE | 所有者 | 说明 |
|---|---|---|
| 应用列表/筛选 | server → 页内 ref | 标签=已有应用聚合 |
| 编辑器 activeMenu | client | 驱动面板 v-if |
| 各面板配置 | server（分段保存 API）→ 面板 form | BasicConfig 串行 7 API |
| 绑定集（KB/技能/工具/MCP/DB） | server → diff 保存（勾选集 vs 已绑定） | 失败回滚重拉 |
| 成员 | **basic.advancedOptions.members JSON**（非独立实体，INFERRED-3） | MemberConfig |
| 会话/消息 | server + client 消息流（乐观插入） | 流式中锁会话操作 |
| 流式中止 | client（AbortController） | stopped 标记 |
| 当前应用 | URL query.appId（watch 联动） | :id 参数被忽略（INFERRED-4） |
| 来源图片缓存 | client（objectURL Map） | Bearer fetch |

## 4. Data Flow

```
SSE: useAppChatStream（唯一解析点）→ 8/6 路回调 → 气泡/思考/工具卡增量
绑定: KnowledgeConfig diff 保存（toRemove→findBindingByKbId→unbind；toAdd→bind priority 0；失败回滚）
      Skill/Tool/Mcp 同 diff 模式
配置导入导出: Blob 下载 + 动态 input[type=file]（basic/dialog 触发器/KB 绑定）
命名转换点: useAppChatStream（SSE）；diff 计算（各绑定面板内）；DialogOptimize 字段映射 title/suggestionType（孤儿）
```

## 5. Visual System

编辑器两级折叠菜单；卡片网格；对话流三件套（气泡/思考黄块/工具卡）；streaming 光标动画。断点无。

## 6. Parent Trace

- 9 接线面板 vs 6 孤儿 → spec INFERRED-1（editor 菜单无对应项）
- diff 绑定保存模式 → spec TASK-002
- runtime 双路由与 query 驱动 → spec INFERRED-4
- SSE 契约 → contract 特性 15/17（agent 链共享）
- 11 死导出 + publishApp 缺位 → INFERRED-6/7
