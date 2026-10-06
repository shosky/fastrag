# AI 分片 — Implementation Document（ai-chunking-frontend）

> 由 /idd-implement 于 2026-09-21 生成（onboard 特征化模式）。

## 1. Code File Map

```
src/views/knowledge/detail/components/
├── AiChunkPanel.vue        — 弹窗面板（1965：状态机/双 SSE/快照校对/联动/应用）
└── AiChunkPdfOverlay.vue   — PDF 交互层（878：结构块/图片盒/区域拖拽/自动翻页/文字层探测）
src/api/index.ts            — ai-chunk 端点组 + 两条 SSE URL（fetch 硬编码 /api）
src/types/knowledge.ts      — AiChunkParagraph/LayoutBlock/Result/ApplyPayload（:683-771）
```

## 2. Interface Implementations

### 面板状态机（design §3 核心）

**Logic Flow:**
```
open(modelValue) → initPanel(): 全量重置（撤销栈/选区/模型/abort）
  Office → loadRealLeft(preview chunk=false)      # OCR 已在入库流水线完成
  PDF    → loadRealFile(blob) → Overlay 渲染 + probeTextLayer
             ├ 有文字层 → loadRealLeft
             └ 扫描件过半 → pdfScanDeferred=true（生成分片时才 OCR）
生成分片: FOR 选区: imageOcr(三级提取) 逐个 try/catch → 合并一卡(source=manual) → 清选区
自动分片: confirm(已有) → status=parsing → realAutoChunkSSE
           ├ 事件 progress/chunk(收集)/done{chunkSource}(统一 append + 来源提示)
           └ 失败/提前结束 → warning → realAutoChunkPreview(chunk=true, sleep(500) 假进度)
                              └ 再失败 → throw（status 卡 parsing，INFERRED-1）
应用: 空拦截 → dirty confirm → status=applying → aiChunkApply → 关窗+applied
校对: 每操作 snapshotChunks() 先行（栈 ≤20）→ dirtyCount++
```

### SSE 帧解析（手写，两处重复实现）

**Logic Flow:**
```
fetch(url) → response.body.getReader()
loop: 累积 buffer → 按 '\n\n' 切帧 → 取 event:/data: 行 → JSON.parse(data) → 分发
stream: progress{percent,stage} / chunk / done / error→throw
layout: layout[] 按键合并去重整表替换；error 仅 break 帧循环
layout 有 AbortController（重开/卸载 abort）；stream 无（INFERRED-2）
```

### 段落联动

**Logic Flow:**
```
右→左: 卡 hover/click → activeChunkId → 卡覆盖 paragraphId 去重 = activePdfIds → Overlay toggle 类
左→右: hover 块 paragraphId ∈ 某卡 blocks → 点亮该卡
锚点: paragraphId（跨页合并块多页片段共享）
```

### 页面登记表

| 能力 | 状态归属 | 要点 | 证据 |
|---|---|---|---|
| 跨页拖拽 | Overlay | mousedown 固化各页 rect、求交 ≥1%、边缘 56px rAF 翻页 | Overlay:331-463 |
| 图片块渲染 | 页内缓存 | 未命中占位「加载中」；失败 console.warn 无重试 | Panel:510-531 |
| Office 匹配 | Panel | 归一化累加串 includes + 穿插补图；未命中退化原文+info | :242-328 |
| 跳页 | props | officeFocusChunkId → Viewer scrollToPage | :1056-1073 |

## 3. Decisions & Deviations

- **特征化记录（不擅改）**：回退假进度；SSE 无 abort（孤儿闭包风险）；status 零分片置 done 语义过载；`pageRange`/SSE 字段类型漂移；`chunk-click` 预留无消费者；调试 log 残留（全部 INFERRED-1..7 待裁决）。
- **契约一致性**：apply「尊重边界只落库」语义与 api:238 后端注释一致（本链核心语义）。
- mock 层零消费（全真实后端）——本链不涉 mock 治理。

## 4. Test Evidence

tdd 全局 blocked。解阻后优先切片：SSE 帧解析器（事件分发/分帧容错）、快照撤销栈、四类选区 → 手动卡合并、apply payload 构造（paragraphIds 去重/拼接）。

## 5. Implementation Trace

```
TASK-001 → 入口 gating + initPanel → FileTable:194-200 + Panel:569-600
TASK-002/003 → 解析/扫描件探测 → Panel:604-638 + Overlay:596-622
TASK-004 → 四类选区 → Overlay:331-480 + Panel:767-850
TASK-005 → 自动分片双通道 → Panel:862-977
TASK-006 → 校对九操作 → Panel:344-486,1087-1150
TASK-007 → 应用覆盖语义 → Panel:1155-1186
TASK-008/009 → 联动/Office 匹配 → Panel:195-328,1045-1082
```
