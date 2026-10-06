# OnlyOffice 在线编辑 — Implementation Document（onlyoffice-editing-frontend）

> 由 /idd-implement 于 2026-09-21 生成（onboard 特征化模式）。

## 1. Code File Map

```
src/views/knowledge/detail/office-edit.vue     — PAGE-001 编辑页（363）
src/views/knowledge/detail/components/OnlyOfficeViewer.vue — PAGE-002 内嵌查看器 + 选区桥宿主（327）
src/composables/useOnlyOffice.ts               — api.js 幂等加载 + DocEditor 生命周期（161）
src/config/index.ts                            — VITE_ONLYOFFICE_URL/officeExtensions/isOfficeFile（38）
src/types/onlyoffice.ts                        — config/事件类型（147；onDocumentStateChange 类型失真 INFERRED-4）
public/__oo_probe.html、__oo_relay.html        — 探针宿主页 + 同源 BroadcastChannel 中继
public/onlyoffice-plugin/chunk-boundary/       — v1 插件（**plugin.js:71-89 语法错误，整 IIFE 不可解析**）
public/onlyoffice-plugin/chunk-boundary-r2/    — r2 插件（后台常驻 isVisual:false，v1.2.0，node --check 通过）
scripts/oo-plugin-{probe,introspect}.mjs       — 无头探针/注册时序诊断（playwright-core）
```

## 2. Interface Implementations

### 编辑器装配（contract: PAGE-001 容器）

**Logic Flow:**
```
loadEditor():
1. getOnlyOfficeConfig → baseline = getFiles().updatedAt（失败 null）
2. 覆盖 customization{autosave:false, forcesave:true, goback/help/feedback/about:false}
3. 注入 events{onAppReady→progress=100, onDocumentReady, onDocumentStateChange, onError}
4. api.js: loadOnlyOfficeApi（幂等 promise，失败重置可重试）→ new DocEditor(placeholder, config)
5. finally loading=false
重载: destroy → 重跑（「重新加载」）
```

### 保存链路

**Logic Flow:**
```
手动: POST forcesave
  ├ initiated: FOR 2s×10: IF getFiles().updatedAt ≠ baseline → notifySaved（8s 去重，
  │            文案「已保存…已自动触发重新切片」）; baseline=null → 轮询恒假 → 20s 超时 warning（INFERRED-1）
  ├ no-changes→info / no-session→warning+重载 / 其余→error(+ooError)
原生: onDocumentStateChange modified→clean → notifySaved
重分片: 后端 callback（OO status=6）触发；前端仅 updatedAt 可感知（无进度，GAP）
```

### 选区桥（Viewer 宿主侧）

**Logic Flow:**
```
双收: BroadcastChannel('fastrag-oo-selection') + window message
消息: oo-selection-ping → 桥存活证据（20s 判死/60s warming 宽限/恢复信号自动复活）
      oo-selection{text≥2} → 防抖 800ms → emit('captured')
用户源解析: user.id 管道前缀 → referrer parentOrigin → 兜底 localhost:3000
插件侧（r2/plugin.js）: executeMethod 就绪轮询(500ms) → 注入 relay iframe →
  600ms GetSelectedText 轮询 + onEndAction；in-flight 锁 + 序号 5s 悬挂自愈
```

### 页面登记表

| 能力 | 状态归属 | 要点 | 证据 |
|---|---|---|---|
| 修改态 | server 事件→ref | 黄条+状态文本 | office-edit:61-67,316-323 |
| chunk 跳页 | props→Viewer | pageNumber??1 | OnlyOfficeViewer:178-187 |
| 探针 | 脚本 | 退出码 0=GREEN；硬编码 dev secret | probe:17-45 |

## 3. Decisions & Deviations

- **死代码**：v1 插件语法错误（node --check 失败）；`chunk-created` emit 声明未发（消费端 onOfficeChunkCreated 不可达）；调试 console.log 残留（Viewer/Panel 多处）。
- **双插件版本不可见（GAP）**：docker-compose 挂载在仓库边界外，服务端用 v1 还是 r2 无法从本仓证实——桥断开提示语（「插件选项卡重开」）仅对 v1 visual 版成立（INFERRED-3/5）。
- **类型失真**：onDocumentStateChange 类型 `{isModified}` vs 实况 `!!e?.data`（INFERRED-4）。
- 无 contract↔代码矛盾（特征化模式）。

## 4. Test Evidence

tdd 全局 blocked。现役浏览器级资产 = 两个探针脚本（项目唯一 E2E 形态）。解阻后优先切片：config 覆盖函数纯函数化测试、保存五分支映射、桥状态机（warming/alive/down 迁移）。

## 5. Implementation Trace

```
TASK-001 → 入口 gating → FM:460-466 + FT:204-208
TASK-002 → 装配 → useOnlyOffice + office-edit:89-142
TASK-003/004 → 保存链路 → office-edit:96-201
TASK-006 → 重分片感知 → 注释 api:413-415
TASK-007/008 → Viewer 覆盖 + 桥 → OnlyOfficeViewer:104-277
TASK-010 → 探针 → scripts/oo-plugin-*.mjs
```
