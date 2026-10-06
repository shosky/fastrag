# OnlyOffice 在线编辑 — Frontend Spec（onlyoffice-editing-frontend）

> 由 /idd-spec 于 2026-09-21 生成（onboard 特征化模式）。Parent Trace ← `docs/feature-map.md` 特性 6 证据包；术语 ← `CONTEXT.md`。

## 1. Scope

覆盖在线编辑页（`/knowledge/:id/office/:fileId`，DocsAPI 集成）、手动/原生保存链路、AI 分片内嵌精简查看器（OnlyOfficeViewer）与 chunk-boundary 选区桥的前端行为、插件探针脚本。**排除**：Document Server 与回调落盘/重分片执行（后端侧；前端仅以 updatedAt 变化感知保存）；`.gz` 预压缩产物（gitignore 项）。

## 2. User Tasks

- **TASK-001 进入在线编辑** — FileTable 下拉「在线编辑 (OnlyOffice)」或预览弹窗按钮 → 跳编辑页；gating：category=document、未删除、非 processing、`isOfficeFile`（doc/docx/xls/xlsx/ppt/pptx 六扩展，config/index.ts:28-38 ‹CERTAIN›；非 Office → warning，FileManager:460-466 ‹CERTAIN›）。
- **TASK-002 加载编辑器** — config 接口 → 注入 api.js（`VITE_ONLYOFFICE_URL` 缺省 `hostname:8082`）→ `new DocsAPI.DocEditor`；前端覆盖 customization（autosave=false/forcesave=true/精简帮助项）+ 注入 events；进度为合成值 10→50→100（步骤跳变非真实进度）；失败 → Notification「加载失败」+「重新加载」（destroy 重载）（office-edit.vue:89-142; useOnlyOffice:23-46,92-102 ‹CERTAIN›；onAppReady 不触发时无看门狗超时 [GAP]）。
- **TASK-003 手动保存（forcesave）** — POST forcesave → `result` 五分支：initiated → 2s×10 轮询 updatedAt 变化 → Notification「文档已保存…已自动触发重新切片」（8s 去重）；no-changes → info；no-session → warning+提示重载；no-file/disabled/其它 → error（带 ooError 码）；20s 未确认 → warning（office-edit.vue:161-201 ‹CERTAIN›；updatedAt 基线拉取失败时轮询恒假、必然 20s 超时——边界缺陷 INFERRED-1）。
- **TASK-004 原生保存感知** — 编辑器工具栏/Ctrl+S（config forcesave=true）→ `onDocumentStateChange` modified→clean → notifySaved（去重）；onError → Notification（:96-127 ‹CERTAIN›）。
- **TASK-005 修改态指示** — `modified` 驱动状态文本「● 有修改（未自动保存…）」+ 页头黄色高亮（:61-67,316-323 ‹CERTAIN›）。
- **TASK-006 重分片感知** — 重分片由后端 callback（OO status=6）触发，**前端不触发也无进度感知**，唯一信号 = updatedAt 变化（:10-13,117-119 注释 ‹CERTAIN›；GAP：无重分片进度）。
- **TASK-007 AI 分片内嵌查看器** — OnlyOfficeViewer（AiChunkPanel 挂载）：精简 UI、`user.id = "<hostOrigin>|<原id>"`（插件回传源解析关键）、**不注入 plugins 清单**（客户端清单会抢占服务端合并、插件窗口打不开——探针差分结论）、**不强制 mode=view**（只读态无选区事件）（OnlyOfficeViewer:104-158 ‹CERTAIN›）。
- **TASK-008 选区捕获（chunk-boundary 插件桥）** — OO 外层 API 无选区事件（DocsAPI 无 onSelectionChanged），插件在编辑器内部 600ms 轮询取选区，三通道上行（同源 relay iframe→BroadcastChannel / parent.postMessage / OO 官方 external message）；协议 `oo-selection-ping`（存活）+ `oo-selection{text}`（文本变化且长度≥2）；宿主桥状态机 `warming(60s 宽限)→alive(20s 判死)→down(可自动恢复)`；captured → AiChunkPanel 生成分片（OnlyOfficeViewer:197-277; r2/plugin.js ‹CERTAIN›）。
- **TASK-009 chunk 跳页** — 分片卡点击 → `focusChunkId` → `scrollToPage(pageNumber??1)`（OnlyOfficeViewer:178-187 ‹CERTAIN›）。
- **TASK-010 插件探针** — `scripts/oo-plugin-probe.mjs`（无头 Chrome + `__oo_probe.html`，断言插件执行 + 选区，退出码 0=GREEN）、`oo-plugin-introspect.mjs`（hook asc_pluginsRegister 诊断 autostart 竞态）——项目唯一浏览器级测试资产（‹CERTAIN›；硬编码 dev secret/路径，INFERRED-2）。

## 3. Pages & Routes

- **PAGE-001 在线编辑页** — `/knowledge/:id/office/:fileId`（routes.ts:116-121，hidden；AppLayout 内非全屏）。入口 ×2（表格下拉、预览弹窗）。退出：返回/面包屑 → 知识库详情（office-edit.vue:203-217）。
- **内嵌 PAGE-002 OnlyOfficeViewer** — 无路由，仅 AiChunkPanel 挂载（:1276-1286）。

## 4. Elements & Interaction

页头：返回｜图标+文件名+副标题｜状态文本｜「保存」「重新加载」；编辑器占位 div + 加载遮罩（office-edit.vue:208-249）。保存结果五分支的提示级别矩阵见 contract。

## 5. State Matrix

| 数据流 | loading | empty | error | 证据 |
|---|---|---|---|---|
| 编辑器加载 | loading+合成进度遮罩；finally 兜底不卡死 | — | Notification + 重新加载；**无看门狗超时 [GAP]** | :89-142,243-246 |
| 手动保存 | 保存钮 saving | no-changes info | no-session warning / no-file·disabled·其它 error / 20s 超时 warning | :161-201 |
| 修改态 | — | — | — | :61-67 |
| 选区桥 | warming（60s 宽限） | — | down → AiChunkPanel warning「插件选项卡重开 ChunkBoundary 或点重置」（提示语只对 visual 版插件成立，INFERRED-3） | OnlyOfficeViewer:197-277; AiChunkPanel:92-95 |
| 文件信息回填 | — | — | 静默 ignore（config 自带 title） | :81-83 |

## 6. Data Mapping

| 视图数据 | 消费代码（CERTAIN） | API Contract |
|---|---|---|
| 编辑器配置整包 | `getOnlyOfficeConfig` GET `…/onlyoffice/config`（api:408-410）→ 前端只覆盖 customization/events（edit）与 user.id（Viewer） | TBD — pending API Contract |
| 保存 | `forceOnlyOfficeSave` POST（api:418-423）→ `result` 枚举 + `ooError` | TBD |
| 文件名/updatedAt | `getFiles`（复用 file-ingestion 端点）：名称回填 + 保存基线 | TBD |
| ~~raw、callback~~ | **前端零运行时消费**——由 config 下发给 Document Server（types:22-23,36）；仅探针页直接拼 URL | — |
| JWT | 前端不签 token（config 下发）；探针脚本本地 HS256 硬编码 dev secret | — |

类型勘误（INFERRED-4）：`onDocumentStateChange` 类型声明为 `{isModified}`，实况消费 `!!e?.data`——类型是理想化子集。

## 7. Responsive & Accessibility

桌面优先（编辑器 iframe 自适应容器，ResizeObserver→fitView）[GAP 其余]。

## 8. Acceptance

- [ ] 非 Office/processing/回收站文件无编辑入口；强进 → warning
- [ ] 加载成功 → onAppReady 进度 100 → 可编辑；失败可「重新加载」
- [ ] 手动保存五分支提示正确；initiated 后 updatedAt 变化触发「已保存+已重切」通知（8s 去重）
- [ ] 原生保存 modified→clean 触发同款通知；修改态黄条正确切换
- [ ] Viewer 内选区 ≥2 字符 → captured → AiChunkPanel 生成分片；桥断开有恢复指引；chunk 卡跳页
- [ ] 探针脚本退出码语义（0=GREEN）

## INFERRED 裁决（✅ 已全量裁决 2026-09-21，用户授权代决）

> 逐条结论见 [../decisions/inferred-adjudication.md](../decisions/inferred-adjudication.md)。以下原清单保留作对照。

## Stage INFERRED 清单（原清单，已被裁决覆盖）

INFERRED-1 保存基线拉取失败→轮询恒假→必然 20s 超时警告（实际已保存）——是否修？INFERRED-2 探针脚本硬编码 dev secret/浏览器路径——环境解耦 or 保留？INFERRED-3 桥断开提示语仅适用 visual 版插件（r2 为后台常驻无 UI）——提示语与实际挂载版本需对齐；INFERRED-4 onDocumentStateChange 类型声明与实况不符——修类型？INFERRED-5 **v1 插件 plugin.js:71-89 语法错误（node --check 失败）整 IIFE 不可解析**——v1 删除 or 修复（r2 为有效后台版）；**服务端实际挂载版本仓库内不可见（GAP，docker-compose 挂载在边界外）**；INFERRED-6 死事件 `chunk-created`（Viewer 声明并消费但从未 emit）清理？INFERRED-7 编辑器加载无看门狗超时、重分片无进度感知——是否补？INFERRED-8 调试 console.log 残留清理。
