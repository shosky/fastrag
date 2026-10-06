# AI 分片 — Frontend Spec（ai-chunking-frontend）

> 由 /idd-spec 于 2026-09-21 生成（onboard 特征化模式）。Parent Trace ← `docs/feature-map.md` 特性 7 证据包；术语 ← `CONTEXT.md`（AI分片/段落对齐模型/结构级跨页合并）。

## 1. Scope

覆盖 AiChunkPanel 工作流（打开→解析→手动/自动分片→校对→应用落库）、AiChunkPdfOverlay（结构块/文字/区域框选/扫描件）、SSE 双流消费、段落对齐联动、Office 选区衔接。**排除**：分片管理页（chunk-management 链）；OnlyOffice 集成本体（onlyoffice-editing 链——本链只定义 Viewer 宿主契约）；`api.createChunk`（属 chunk-management）。

## 2. User Tasks

- **TASK-001 打开面板** — FileTable 行「AI分片」（gating：document 且扩展名 ∈ [.pdf,.doc,.docx,.ppt,.pptx]；processing 置灰，failed 可入作重处理途径）→ 90% 宽弹窗；initPanel 全量重置；PDF 先渲染原件再由文字层探测决定是否解析，Office 立即解析（FileTable:194-200,455-462; Panel:569-600,1189-1192 ‹CERTAIN›）。
- **TASK-002 解析原件** — `aiChunkPreview(chunk=false)`（timeout 300s）→ 段落对齐模型 + 版面块填充；layout 缓存未命中 → 启动 layout SSE（按键合并去重）；失败 → error「解析数据加载失败：版面框选段与联动不可用，仍可一键自动分片」；1s ticker 显示耗时（Panel:604-638,649-691 ‹CERTAIN›）。
- **TASK-003 扫描件探测** — Overlay 逐页 getTextContent（<50 字符记扫描页，与后端阈值一致）；扫描页过半 → 延迟 OCR（不自动整本，纯截图交互，生成分片时才 OCR）；探测异常按有文字层处理（Overlay:596-622; Panel:74-83 ‹CERTAIN›）。
- **TASK-004 手动生成分片** — 选区来源四类：结构块点击（几何命中嵌套取最小）、文字拖选（window.getSelection ≥2 字）、区域框选（跨页拖拽：拖拽矩形与每页求交 ≥1%×1% 逐页 emit；边缘 56px 自动翻页 rAF，速度随距离递增上限 24px）、绿色图片盒点击 → 四者统一「生成分片」→ `aiChunkImageOcr`（三级提取：文字层行→内容图直达→OCR 兜底）→ 合并一张手动卡 + 选区清空（Overlay:331-463,487-508; Panel:150-164,767-850,805-850 ‹CERTAIN›）。
- **TASK-005 一键自动分片** — 已有分片时 confirm「重新自动分片将清空现有分片（含手动）」→ status=parsing → SSE `stream`（事件 progress{percent,stage}/chunk/done{chunkSource}/error；stageMap：OCR 解析/结构级跨页合并/LLM 自动分片）；后端静默降级链 llm→embedding→rule 以完成提示显式告知；**SSE 失败/提前结束 → warning 回退一次性 preview（chunk=true，每卡 sleep(500) 假进度渐进出卡）**；回退再失败 → status 卡在 parsing 无兜底（Panel:855-977 ‹CERTAIN›；INFERRED-1）。
- **TASK-006 校对** — 编辑（图片块原位保留）/块界拆分（>400 字单块「✂ 在此拆分」热区）/句界拆分/合并/删除/上移下移/复制/撤销（Ctrl+Z，栈上限 20）/Delete 批删；每步 snapshot 快照 + dirtyCount++（:344-486,1087-1150 ‹CERTAIN›）。
- **TASK-007 应用落库** — 空分片拦截；dirtyCount>0 → confirm「内容已修改 N 处，应用后按当前分片替换旧数据」→ `aiChunkApply`（后端「尊重边界只做向量化与落库」）→ success、清撤销栈、关窗、emit applied → FileManager 重载（Panel:1155-1186; FileManager:468-470 ‹CERTAIN›）。
- **TASK-008 段落对齐联动** — 右→左：卡 hover/click → activeChunkId → 激活卡覆盖的全部 paragraphId → Overlay toggle 类（不重建 DOM）；左→右：hover 块段落反向点亮卡片；锚点统一 paragraphId（跨页合并块多页片段共享同一 id）（Panel:1045-1082; Overlay:161-172 ‹CERTAIN›）。
- **TASK-009 Office 选区衔接** — Viewer 捕获文本 → 跨段连续运行匹配（归一化累加串 includes，穿插补全图片段落）→ 命中按类型重建 Markdown（表格→管道表/标题→##/列表→-/代码→围栏）；未命中退化为原文 + info（Panel:195-234,242-328 ‹CERTAIN›）。

## 3. Pages & Routes

无独立路由：AiChunkPanel = el-dialog（90%/92vh，destroy-on-close）挂 FileManager（:715-720）；入口在 `/knowledge/:id` 宿主。

## 4. Elements & Interaction

Header（分片数/已编辑处数/含手动 tag + 阶段文本 + 进度条）；左栏原件预览（PDF=Overlay / Office=Viewer / 其他 empty）+ 手动分片操作条；右栏「一键自动分片」+ 批量操作条（全选/反选/合并/删除/取消）+ 分片卡列表（勾选/序号/手动-自动 tag/页码范围+字符数/跨页标记/操作七项）；块类型渲染 heading/table/code/image/paragraph；Footer（覆盖提示 + 撤销/重置/取消/应用）（Panel:1199-1483 ‹CERTAIN›）。结构块框 pointer-events:none，命中由容器几何计算驱动，不拦文字层（Overlay:18,123 ‹CERTAIN›）。

## 5. State Matrix

状态机：`idle → parsing | loadingLeft → done → applying`（:29-31）。

| 数据流 | loading | empty | error | 证据 |
|---|---|---|---|---|
| 解析 | 阶段文本 + 1s ticker（s→m+s）+ 进度条 | — | error toast + 清模型回 idle | :604-638 |
| 原件渲染 | 「正在渲染原件…」 | 其他类型 el-empty | realError + warning（仍可自动分片） | :713-723,1255-1287 |
| 自动分片 SSE | 按钮 loading + progress/stage | — | warning 回退 preview；回退失败卡 parsing（INFERRED-1） | :862-977 |
| layout SSE | 静默合并 | — | **静默保留已收框**（abort 仅有此流） | :649-691 |
| 生成分片 | ocrLoading | 无选中 warning | 逐区域 try/catch；全空 warning+弹撤销快照 | :150-164,767-850 |
| 应用 | applying | 空分片 warning | error toast、status 回 done | :1155-1186 |
| 图片块 | 「图片加载中…」占位 | — | console.warn 无重试 | :510-531 |

自动分片 SSE **无 abort**——parsing 中关窗后读流闭包成孤儿（INFERRED-2）。

## 6. Data Mapping

| 视图数据 | 消费代码（CERTAIN） | API Contract |
|---|---|---|
| 解析结果 | `aiChunkPreview` POST `…/ai-chunk-preview?chunk=`（api:229-236）→ `AiChunkResult{alignParagraphs,imageBoxes,layoutBlocks,markdown?,document?}`（markdown/document 前端零消费） | TBD — pending API Contract |
| 应用 | `aiChunkApply` POST（api:239-245）payload `{chunks:[{paragraphIds(去重),text('\n\n' 拼接),source,pageRange(类型未声明 INFERRED-3)}]}` | TBD |
| 自动分片流 | GET `…/ai-chunk/stream`（**fetch 硬编码 /api 前缀绕过 axios**，api:249-253；手写 `\n\n` 分帧解析） | TBD |
| 版面流 | GET `…/ai-chunk/layout`（同上；`layout` 事件按键 `page#x|y|w|h` 合并去重整表替换） | TBD |
| 区域 OCR | POST `…/ai-chunk/image-ocr` → `{blocks:[{type,text}]}`（api:425-437） | TBD |
| 图片渲染 | GET `…/images/{imageKey}` 带 Bearer → blob（MinIO key 约定） | TBD |
| 类型 | AiChunkParagraph/AiChunkLayoutBlock/AiChunkResult/AiChunkApplyPayload（types:683-771；SSE chunk 事件实发字段 `text/paragraphIds` 与 `AiChunk` 声明不匹配 INFERRED-4） | — |

## 7. Responsive & Accessibility

弹窗 90%/92vh 桌面形态；Overlay 拖拽/翻页为鼠标交互 [GAP：触屏/键盘可达性]。

## 8. Acceptance

- [ ] 入口 gating 正确（5 扩展名/processing 置灰/failed 可入）
- [ ] PDF 有文字层秒开解析；扫描件延迟到生成分片时 OCR 且有提示；解析失败仍可一键自动分片
- [ ] 四类选区来源均能生成手动卡；区域框选跨页逐页命中；自动翻页边缘触发
- [ ] 自动分片 SSE 阶段文案与进度正确；降级链完成提示显式告知来源；SSE 失败回退 preview
- [ ] 校对九种操作均可撤销（≤20 步）；拆分热区仅 >400 字单块
- [ ] 应用：空拦截 + 脏确认 + 关窗后列表刷新；语义=尊重边界只落库
- [ ] 段落联动双向高亮；Office 选区匹配命中重建 Markdown、未命中降级提示

## INFERRED 裁决（✅ 已全量裁决 2026-09-21，用户授权代决）

> 逐条结论见 [../decisions/inferred-adjudication.md](../decisions/inferred-adjudication.md)。以下原清单保留作对照。

## Stage INFERRED 清单（原清单，已被裁决覆盖）

INFERRED-1 SSE 回退链尾部无兜底（status 卡 parsing 需手动重置）——是否补恢复？INFERRED-2 自动分片 SSE 无 AbortController（关窗后孤儿闭包）——补 abort？INFERRED-3 apply payload `pageRange` 未在类型声明——修类型 or 删字段？INFERRED-4 SSE chunk 事件字段与 `AiChunk` 类型不匹配——修类型？INFERRED-5 回退 preview 假进度（sleep 500 渐进）——保留体感 or 去假？INFERRED-6 status 语义过载（零分片选区也置 done）——收敛枚举？INFERRED-7 `emit('chunk-click')` 父级无消费者（预留）、`chunk-created` 死事件、调试 log 残留——清理？INFERRED-8 图片块加载失败无重试/占位——补？
