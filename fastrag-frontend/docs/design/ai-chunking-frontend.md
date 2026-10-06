# AI 分片 — Frontend Architecture Design（ai-chunking-frontend）

> 由 /idd-design 于 2026-09-21 生成（onboard 模式）。上游：`docs/spec/ai-chunking-frontend.md`。

## 1. Page Map

| PAGE | 宿主 | 权限 | 入口 | 退出 |
|---|---|---|---|---|
| AiChunkPanel 弹窗 | FileManager（宿主 `/knowledge/:id`） | 入口 gating（5 扩展名/非 processing） | FileTable 行「AI分片」 | 取消/应用成功（emit applied → 列表刷新） |
| AiChunkPdfOverlay | Panel 左栏（PDF 分支） | — | 随面板 | 随面板 |

## 2. Page/Layout/Component Map

```
┌──────────────────────────────────────────────────────────────┐
│ Header: [分片数][已编辑][含手动]tags  阶段文本+进度条   [×]      │
├───────────────────────────────┬──────────────────────────────┤
│ 左栏 原件预览                   │ 右栏 分片结果(N)                │
│ （提示文案区：状态动态）          │ [一键自动分片(AI 语义分组)]      │
│ ┌───────────────────────────┐ │ 空态/分片中态                   │
│ │ PDF=AiChunkPdfOverlay      │ │ 批量条: 全选|反选|合并|删除|取消  │
│ │  (结构块框/图片盒/文字层     │ │ ┌─────────────────────────┐  │
│ │   区域拖拽/边缘自动翻页)     │ │ │☐#1 [手动]p3-4·120字 [编辑]│  │
│ │ Office=OnlyOfficeViewer    │ │ │  浏览态: 块类型渲染        │  │
│ │  (选区桥 captured)         │ │ │  编辑态: textarea+图只读   │  │
│ │ 其他=el-empty              │ │ │  块间「✂ 在此拆分」热区     │  │
│ └───────────────────────────┘ │ └─────────────────────────┘  │
│ [生成分片(动态文案)] [清空…]    │ 分页无（虚拟滚动无，平铺列表）    │
├───────────────────────────────┴──────────────────────────────┤
│ Footer: 提示「应用后以当前分片替换旧数据」 [撤销][重置][取消][应用] │
└──────────────────────────────────────────────────────────────┘
```

组件判定：AiChunkPanel（1965 行，单宿主但行为深度极高——状态机+双 SSE+对齐模型，成立）；Overlay（跨页拖拽/自动翻页/文字层探测，独立交互域，成立）。**不再拆分**（拆分将破坏快照/对齐的原子性）。

## 3. State Ownership

| STATE | 所有者 | 说明 |
|---|---|---|
| 面板状态机 idle/loadingLeft/parsing/done/applying | client（单一 status ref） | 驱动全部按钮 loading/disabled |
| 段落对齐模型 alignParagraphs/imageBoxes/layoutBlocks | server（preview/layout SSE）→ 页内模型 | 单一事实源 |
| 分片卡列表 + 撤销栈（≤20） | client（snapshot 快照制） | 每操作先快照 |
| dirtyCount | client | 驱动应用确认 |
| 选区集（结构块/文字/区域/图片盒） | Overlay → emit → Panel | 生成分片后清空 |
| activeChunkId / activePdfIds | client（联动高亮） | 双向对齐唯一通道 |
| layout 缓存 | 后端（未命中才起 SSE） | — |
| 桥状态/选中文本 | onlyoffice 链（Viewer）→ captured | 跨链契约 |

## 4. Data Flow

```
preview(chunk=false) ──→ 段落对齐模型（anchorText/rects/pages/imageKey/crossPage）
layout SSE（未命中缓存）──→ layoutBlocks 按键合并去重整表替换
自动分片: stream SSE（progress/chunk/done/error）→ 收集 → done 统一 append
          └ 失败 → preview(chunk=true) 一次性回退（假进度渐进出卡）
区域/图片盒 → imageOcr 三级提取 → 手动卡
应用: chunks = 卡列表 → {paragraphIds 去重, text '\n\n' 拼接, source, pageRange}
命名转换点: SSE 帧解析（手写 \n\n 分帧——stream 与 layout 两份重复实现，INFERRED 收敛候选）；
          parasToMarkdown（Office 选区→Markdown 重建）；mapChunk 类归一不涉及本链
```

## 5. Visual System

深色 Overlay 草稿（红色虚线）/激活（is-active/is-selected 类）体系；图例条含「结构框」开关；图片盒 z-index:3 压文字层。桌面弹窗形态，无响应式（全站一致）。

## 6. Parent Trace

- 状态机五态 + 快照撤销 → spec TASK-006
- SSE 双流 + 回退链 → spec TASK-005（INFERRED-1/2 悬置）
- 四类选区统一 imageOcr 三级提取 → spec TASK-004
- paragraphId 单一锚点双向联动 → spec TASK-008
- 应用覆盖语义（尊重边界只落库）→ spec TASK-007
