# OnlyOffice 在线编辑 — Frontend Architecture Design（onlyoffice-editing-frontend）

> 由 /idd-design 于 2026-09-21 生成（onboard 模式）。上游：`docs/spec/onlyoffice-editing-frontend.md`。

## 1. Page Map

| PAGE | 路由/宿主 | 权限 | 入口 | 退出 |
|---|---|---|---|---|
| PAGE-001 编辑页 | `/knowledge/:id/office/:fileId`（hidden，AppLayout 内） | 入口 gating（document+非 processing+isOfficeFile） | FileTable 下拉、FPD 预览按钮 | 返回/面包屑 → 详情 |
| PAGE-002 内嵌查看器 | AiChunkPanel 宿主 | 随宿主 | AI 分片面板 | 随弹窗关闭 |

## 2. Page/Layout/Component Map

### PAGE-001 编辑页
```
┌────────────────────────────────────────────────────────┐
│ 面包屑: 首页/知识库/知识库详情/在线编辑                     │
│ 页头: [←] [图标 文件名/副标题]  状态文本(●修改态黄条)       │
│       [保存(saving)] [重新加载]                           │
├────────────────────────────────────────────────────────┤
│ #office-edit-placeholder（DocsAPI DocEditor 挂载点）       │
│ 加载遮罩: 合成进度条 10→50→100（config/ready 驱动）         │
└────────────────────────────────────────────────────────┘
```

### PAGE-002 内嵌查看器（AiChunkPanel 左栏 Office 分支）
```
┌──────────────────────────────┐
│ OnlyOfficeViewer（精简 UI）    │ ← props: file/kbId/chunks/focus-chunk-id
│ DocsAPI 容器（编辑构建，       │ ← events: captured(选区)/bridge-state/error
│  不注入 plugins/不强制 view）  │    chunk-created（死事件 INFERRED-6）
└──────────────────────────────┘
```

组件判定：`useOnlyOffice`（api.js 幂等加载 + DocEditor 生命周期封装，≥2 消费者，成立）。

## 3. State Ownership

| STATE | 所有者 | 说明 |
|---|---|---|
| 编辑器实例 | client（useOnlyOffice 封装，destroy 责任在调用方） | api.js promise 幂等 + 失败可重置重试 |
| loading/合成进度 | client（步骤跳变：config=50、onAppReady=100） | 非真实进度（现状记录） |
| modified 状态 | server 事件（onDocumentStateChange）→ 页内 ref | 驱动黄条与状态文本 |
| 保存确认基线 | server（getFiles.updatedAt）+ 页内轮询 | 基线 null → 恒超时（INFERRED-1） |
| 桥状态 warming/alive/down | client 状态机（Viewer） | ping 阈值 20s、宽限 60s |
| 选中文本 | OO 插件 → 三通道消息 → Viewer captured | 唯一上游是插件 |
| 跳页目标 | form(props.focusChunkId) | 父面板驱动 |

## 4. Data Flow

```
getOnlyOfficeConfig → 前端最小改写（edit: customization+events；viewer: user.id 前缀）
  → DocsAPI.DocEditor（api.js 由 VITE_ONLYOFFICE_URL||:8082 注入，幂等）
保存流: forcesave POST → result 分支 → updatedAt 轮询 → notifySaved（8s 去重）
原生流: onDocumentStateChange(modified→clean) → notifySaved
选区流: 插件 600ms 轮询 → {relay BroadcastChannel ‖ postMessage ‖ external message}
        → Viewer 双收去重 → 防抖 800ms → captured → AiChunkPanel 匹配段落
命名转换点: config 覆盖各自页内具名函数（不共享深拷贝工具——两页面覆盖集不同，无抽象价值）
```

## 5. Visual System

编辑器 iframe 自适应（ResizeObserver→fitView）；修改态黄条（is-modified 类）；加载遮罩进度条。token/断点全站一致。

## 6. Parent Trace

- 不注入 plugins/不强制 view 的 Viewer 覆盖 → spec TASK-007（探针差分结论，契约级约束）
- 选区桥三通道 + 状态机 → spec TASK-008（跨域 iframe 的固有防御）
- 保存确认轮询 updatedAt → spec TASK-003/006（重分片感知的唯一手段）
