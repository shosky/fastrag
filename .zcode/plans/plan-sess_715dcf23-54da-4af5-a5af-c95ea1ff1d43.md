# 原件渲染分块画框 · 分层改进（正规 PDF 走 PDFBox 几何，扫描页才 OCR）

## 思路（采纳你的建议）

**先逐页判定是否有文字层**：
- **有文字层的页面** → 用 **PDFBox 精确坐标做几何版面分析**（快、像素精确、0 VLM 调用）标出每块内容并区分类型
- **无文字层的页面**（扫描件/图片页）→ 才走 **VLM OCR 版面分析**（现有实现）

正规 PDF（绝大多数）从此**零 OCR 成本、毫秒出框、坐标像素精确**，直接解决当前的「慢、不准、花预算」三个痛点；只有真正的扫描件才花 OCR 钱。

## 关键事实（已确认）

- `extractPageLines`（`AiChunkServiceImpl.java:1091+`）已用 PDFTextStripper 逐页产出**行盒 `[rect+text]`**，坐标精确；只需再捕获 `getFontSize()`/`getFont().getName()` 即可支撑字体级分类
- `extractContentImageBoxes`（`MediaExtractor`）已有**图片精确坐标**（CTM）
- 前端 `layoutBlocks` 渲染已按类型分色 + 归一化百分比，**无论块源是 PDFBox 还是 VLM，形状完全一致 → 前端零改动**
- `PdfImageExtractionTest` 有 `PDPageContentStream` 造 PDF 的现成手法可复用于单测

## 改动清单

### 后端（核心：新组件 `PdfLayoutAnalyzer`）

**1. 新增 `service/impl/PdfLayoutAnalyzer.java`（纯静态工具，可单测）**
- 入参：某页的 `lines[]`（扩展为 rect + text + fontSize + fontName）+ 页面宽高 + 该页图片盒
- 出参：`List<AiChunkLayoutBlock>`（归一化、顶左、带 type）
- 几何聚类算法（全部用精确坐标）：
  - **title**：fontSize ≥ 页中位数 ×1.25，或字体名含 Bold 且短行的连续行 → 标题块
  - **text**：其余行按「同栏（x 范围重叠）+ 行距 ≤ 1.6×行高」聚类成段（双栏靠 x 重叠判列，避免跨栏并块）
  - **table**：≥2 连续行且每行都有 ≥2 个稳定列起点（x 桶聚类、跨行列一致）→ 表格块
  - **code**：等宽字体（字体名匹配 `mono/consolas/courier/menlo/code` 等）或连续 ≥3 行同 x 同字号 → 代码块
  - **image**：直接复用 CTM 图片盒（type=image）
  - **caption**：位于 image/table 紧邻上/下方的短行 → 图注块
  - 兜底：未分类行归 text（保证不漏块）；块按 y 自上而下排序，行不跨块重叠

**2. `AiChunkServiceImpl.extractPageLines` 扩展**：行盒追加 `fontSize`、`fontName`（改 `PageLine` record）

**3. `AiChunkServiceImpl.streamLayout` 页循环改为逐页 hybrid**：
- 每页先判 `hasText`（该页文本字符数 > 阈值如 200）
- 有文字层 → `PdfLayoutAnalyzer` 几何分析（单线程即快，无需并发/网络）
- 无文字层 → 该页渲染 PNG → 现有 `LayoutAnalysisService`（并发池，per-task doc 那份非线程安全的加载已修）
- 统一产出 `AiChunkLayoutBlock[]`；沿用「全页成功才写缓存」守卫
- `layout.json` 增加可选 `source` 字段（pdfbox|vlm|hybrid，供排障），缓存命中逻辑不变
- `analyze()` 缓存读取不变

### 前端
**无改动**（块形状/类型已兼容）。

## 明确边界（如实说明）

| 类型 | 正规 PDF（几何） | 扫描 PDF（OCR） |
|------|------|------|
| 标题/正文/图片 | ✅ 像素精确 | ✅ VLM |
| 表格 | ✅ 列对齐启发式（常规制表可靠；复杂合并单元格可能漏判为 text） | ✅ VLM |
| 代码 | ✅ 等宽字体判定可靠 | ✅ VLM |
| 公式 | ⚠️ 正规 PDF 无标准标记，几何难稳定识别 → 归 text/image；**这正是「公式页可能需要 OCR」的动机** | ✅ VLM |

## 验证

1. `PdfLayoutAnalyzerTest`：用 `PDPageContentStream` 造一个含「大字号标题 + 正文段 + 列对齐表格 + 等宽代码 + 内嵌图」的 PDF，断言各块类型与 bbox 大致正确（复用 PdfImageExtractionTest 手法）
2. `mvn -am test` + `vue-tsc`（前端无改动，回归即可）
3. 手动：正规 PDF 打开即精确出框（无等待、Network 面板无 VLM 调用）；扫描 PDF 仍逐页出框；混合 PDF 各自正确处理

## 收益

- 正规 PDF：**0 VLM 调用、毫秒级、像素精确** —— 彻底解决「不准确 + 慢」
- 扫描 PDF：仅在必要时才 OCR，预算集中在真正需要的地方