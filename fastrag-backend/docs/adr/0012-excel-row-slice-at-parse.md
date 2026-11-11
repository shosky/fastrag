---
name: Excel 按行切片在解析期装配——分片器分派缝不变
summary: excel_row 切片由解析期 ExcelRowSlicer 装配多 TABLE 节点，分片器分派缝不变，豁免原子溢出二次拆分。
type: adr
id: ADR-0012-EXCEL-ROW-SLICE-AT-PARSE
status: draft
updated: 2026-11-04
hash: "9940895c08fe"
summaryHash: "9940895c08fe"
---

## 背景 {#context}

REQ-H05~H08（spec/parse-strategy.md §4.8）定义了 excel_row 的切片行为：显式表头范围唯一划定表头、数据行按「表头 + 至多切分行数」组装、4800 字符硬上限、多 Sheet 统一生效。实现落点有两个候选层。解析层：DocumentParserImpl.parseExcelContent 在 POI 行读取后直接组装切片 TABLE 节点。分片层：解析保持整表单 TABLE 节点，由 ChunkingService 按行拆分。两者都能满足行为语义——DocNode TABLE 本就携带 headers/rows 结构化行列表，分片层拆分在数据上可行。

## 决策 {#decision}

切片在解析期装配。excel_row 策略下 parseExcelContent 跳过自动表头检测与 LLM 表头合并，按显式表头范围切出表头行；数据行交 ExcelRowSlicer（parser 包内纯函数助手）产出「每片 = 表头行 + 数据行」的多个 TABLE 节点。ChunkingService 不新增分片方法。excel_row 复用 structuralChunk 的原子 TABLE 路径（一个 TABLE 节点 ≡ 一个分片），仅对 excel_row 豁免 handleAtomicOverflow 二次拆分。

选解析期的三点理由。
①文件预览与摄取天然同路径（REQ-H11）：预览只跑解析，切片在解析期意味着预览所见即摄取所得；分片层方案需预览端点补跑分片。
②元数据零新增（REQ-H10）：Sheet 名 HEADING → title/headingPath、TABLE 原子 → chunkType=table 全部走既有机制；分片层方案需新增行拆分与表头回填的元数据装配。
③REQ-H07 的多 Sheet 遍历、行数不足跳过与告警在解析层已有宿主（parseExcelContent 的 Sheet 循环），无需跨层传递参数。

## 后果 {#consequences}

- chunk.strategy=excel_row 的「切片行为」由解析层实现，spec §2「分片算法本体归 chunk-management」的表述按字面理解存在归属张力——本 ADR 即该归属裁决的记录：分片器分派缝（既有缝 1）稳定不变、五种分片器不动，新增的是解析期的表格装配方式变化（modules/parse-strategy.md 表格切片方式缝）。
- handleAtomicOverflow 必须对 excel_row 豁免，否则默认 chunkLength=1000 会把 4800 字符切片二次拆碎。该豁免是 excel_row 语义的一部分，漏做即行为回归，tdd 需显式用例钉住。
- 内嵌表格不受影响：PDF/DOCX/PPTX 内嵌 Excel 复用 parseExcelContent 但不带策略对象，走 default 分支的自动表头路径，与 spec §2 排除项一致。
- ExcelRowSlicer 为单调用方隐藏助手，不立契约单元；4800 字符常量收敛于该类，不开放配置（REQ-H06）。
