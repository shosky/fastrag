---
name: ParseStrategy
summary: 策略配置面唯一权威——CRUD 与绑定守卫、三层配置合并（IF-006~008）、Excel 按行切片（解析期装配，见 adr/0012）。
type: module
id: MOD-PARSE-STRATEGY
profile: backend
status: draft
updated: 2026-11-04
satisfies: [SPEC-PARSE-STRATEGY]
decisions: [ADR-0012-EXCEL-ROW-SLICE-AT-PARSE]
declares: []  # excel_row 保存校验错误码由 docs/interfaces.md 声明（contract 阶段），本档仅引用
hash: "2505f24efca3"
summaryHash: "2505f24efca3"
---

# 解析策略（parse-strategy）— 模块设计

## Responsibility {#responsibility}

策略配置面的唯一权威：策略 CRUD 与绑定-重切一致性守卫、advanced 三层合并，以及 Excel 按行切片（excel_row）的参数校验与解析期装配。

## Contract {#contract}

### IF-006 — StrategyConfigResolver {#if-strategyconfigresolver}

**Purpose:** 全管线唯一的策略配置读取口——把「代码默认 → 系统配置 → 策略 advanced」三层按固定优先级合并为类型化 `ParseStrategyConfig`；解析/分片/索引/AI分片/图谱/预览六类消费方不见 JSON 与双格式历史包袱。

**Signature:**
```java
// com.fastrag.module.knowledge.config
public class StrategyConfigResolver {
    /** 管线主入口：按策略 id 合并配置；id 为 null 仅系统默认层 */
    public ParseStrategyConfig resolve(String strategyId) { ... }
    /** 预览/预设场景：对未落库的临时策略对象合并配置 */
    public ParseStrategyConfig resolve(KbParseStrategy strategy) { ... }
}
```

**Types:**
```java
// com.fastrag.module.knowledge.model —— 全量类型化配置；字段语义以行内注释为准
public class ParseStrategyConfig {
    private ParseConfig parse;
    private ChunkConfig chunk;
    private IndexConfig index;
}
public static class ParseConfig {
    private String tableMode = "structured";  // structured | markdown | ignore；excel_row 下被忽略（REQ-H08）
    private Integer keyframeIntervalSeconds;  // 可选，仅 video/audio 生效
    private Double keyframeHashThreshold;     // 可选，仅 video 生效
}
public static class ChunkConfig {
    private String strategy = "rule_fixed";   // rule_fixed/rule_recursive/structure_aware/semantic/parent_child/excel_row（REQ-H01）
    private int chunkLength = 1000;           // excel_row 下不消费（切片长度由 4800 硬上限定义，adr/0012）
    private int overlap = 100;                // excel_row 下不消费
    private boolean titlePrefix = true;
    private boolean headingPath = true;
    private List<String> delimiters;          // 纯文本兜底路径分隔符
    private int parentMaxChunkLength = 2000;  // 仅 parent_child
    private String parentAggLevel = "auto";   // 仅 parent_child：H1/H2/H3/auto
    private int semanticThreshold = 30;       // 仅 semantic（0~100 映射 0.0~1.0）
    private String embeddingModel = "";       // 仅 semantic，空=系统默认
    private ExcelRow excelRow;                // 仅 strategy=excel_row 消费，其余策略忽略（部分更新残留无副作用）
}
public static class ExcelRow {
    private int headerStartRow;               // 必填（保存期校验），1-based ≥1（REQ-H03）
    private int headerEndRow;                 // 必填，≥headerStartRow 且区间 ≤5 行（REQ-H03）
    private int sliceStartRow;                // 必填，> headerEndRow（REQ-H03）
    private int rowsPerSlice = 100;           // 可选，缺省 100，仅下限 ≥1 强校验（REQ-H04）；上限由 4800 硬限兜底
}
public static class IndexConfig {
    private List<String> embedFields;         // 一期固定 content/headingPath/fileName
}
```

**Error modes:**
- advanced JSON 损坏或单项非法：warn 并沿用已合并值/默认值，不阻断、不设新错误码（REQ-G03）
- SysConfig 读取失败：降级代码内置默认层（general_chunk_size=1000 / general_chunk_overlap=100）
- 绑定策略已被删除：由 IF-008 守卫回退自动匹配；resolver 对 null id 仅合并系统默认层
- excel_row 保存期校验拒绝（扩展名域/表头三参/切分行数下限）在 CRUD 面执行，错误码见 interfaces.md API-013~015

**Invariants:**
- 合并优先级固定：代码默认 → SysConfig → 策略 advanced（REQ-G01）
- 新分组优先，旧平铺字段仅同组缺失时回退映射（REQ-G02）
- strategy=excel_row 时 excelRow 组保证非空（rowsPerSlice 缺省 100）；运行时信任库内配置不再二次校验（保存期闸门 REQ-H02/H03/H04）
- excelRow 组仅 strategy=excel_row 消费，其余策略忽略该组

**Public vs hidden:** 调用方拿类型化对象 / SysConfig 读取、新旧双格式兼容、逐项容错全部隐藏。

### IF-007 — ParseMethodRegistry {#if-parsemethodregistry}

**Purpose:** 「文档类型 ↔ 扩展名 ↔ 解析方法」的单一事实源映射——meta 端点、保存校验、扩展名推断三方共享同一口径。

**Signature:**
```java
// com.fastrag.module.knowledge.parser
public enum ParseMethodRegistry {
    of(String code),                    // 方法代码查枚举
    resolveByExtension(String ext),     // 扩展名推断方法，未识别回退 default
    isSupported(String ext),            // 扩展名是否受系统支持
    isCompatible(String methodCode, String ext), // 保存校验双闸之一
    supportedExtensions()               // meta 端点数据源
}
```

**Types:** 枚举常量与兼容扩展名集（带点小写）：
```java
DEFAULT("default")  // 兼容全部受支持扩展名
PDF("pdf")          // .pdf
DOC("doc")          // .doc
DOCX("docx")        // .docx
PPTX("pptx")        // .pptx、.ppt
XLSX("xlsx")        // .xlsx、.xls —— excel_row 扩展名域校验的判定基础（REQ-H02）
VIDEO("video")      // .mp4 .avi .mov .mkv .flv .wmv .webm
AUDIO("audio")      // .mp3 .wav .m4a .aac .ogg .flac .wma
IMAGE("image")      // .jpg .jpeg .png .bmp .tiff .gif .webp
```

**Error modes:**
- resolveByExtension 未识别扩展名：回退 default 纯文本解析路径
- isCompatible(default, ext)：ext 受系统支持即真；专用方法仅兼容自身扩展名集（REQ-G04）

**Invariants:**
- 归一化：去空格、转小写、补点；存储与比较统一带点小写（REQ-B04）
- 兼容矩阵只此一处；meta 端点（supportedExtensions）与保存校验（isCompatible）同源

### IF-008 — ParseStrategyBindingGuard {#if-parsestrategybindingguard}

**Purpose:** ADR-0001 的守卫面——绑定合法性、换绑三态、重切真源、元数据改绑拦截，维护「绑定 ⟺ 分片结果一致」不变式。

**Signature:**
```java
// 语义级契约；实现为 FileServiceImpl 私有方法（尚未成类，深化候选）
resolveRequestedStrategy(kbId, fileId, extension, requestedId) -> strategyId|null  // :207-220
reChunkFile 换绑段（三态校验与绑定写回）                                           // :405-419
rebuildProcessRequest(file)（重切真源覆盖快照）                                    // :226-247
update patch 守卫（拒绝元数据接口改绑）                                            // :642-649
```

**Types:** 无独立类型——语义契约，出入参为既有实体与 ID。

**Error modes:**
- 请求他库策略：告警并回退扩展名自动匹配（REQ-D03）
- 绑定的策略被删除：文件下次处理自动回退自动匹配，不阻断（REQ-C04）
- 默认策略删除：拒绝（REQ-A05）
- re-chunk 携带他库或不存在 strategyId：拒绝换绑（REQ-D04）

**Invariants:**
- 绑定变更必然触发重新分片，不允许漂移态（ADR-0001）；四处守卫共同维护该不变式
- 换绑三态：未传=沿用当前绑定；非空 id=校验归属后换绑；空串=清除覆盖恢复自动匹配（REQ-D04）
- 重切清理保留 manual 分片，仅重置图谱标记（REQ-E02）
- 文件级自定义策略保存与重切同一接口原子完成（REQ-E04）

**Implemented by:** `fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/service/impl/FileServiceImpl.java`（该文件主归属 file-ingestion 链；守卫四处为本模块的语义落点，成类收敛为深化候选）

## Interface {#interface}

无

## Entities {#entities}

```sql
-- 策略单表双态：file_id 空否区分知识库级/文件级；excel_row 零 DDL（REQ-H10）
CREATE TABLE kb_parse_strategy (
    id VARCHAR(32) PRIMARY KEY,
    kb_id VARCHAR(32) NOT NULL,
    file_id VARCHAR(32) DEFAULT NULL,   -- 非空=文件级专属策略（不参与自动匹配与管理列表）
    name VARCHAR(128) NOT NULL,
    description VARCHAR(256),
    extensions JSON,                    -- 适用扩展名数组；excel_row 要求 ⊆ {.xlsx,.xls}（REQ-H02）
    parse_method VARCHAR(16) DEFAULT 'default',
    is_default TINYINT DEFAULT 0,
    advanced JSON,                      -- parse/chunk/index 三分组；chunk.strategy=excel_row + chunk.excelRow 三参
    llm_model VARCHAR(128),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

- Relationships: 知识库 1—N 策略（扩展名自动匹配选路）；文件级策略随文件删除级联清理（REQ-D01）。

## Logic {#logic}

excel_row 已实现（2026-11-04 红绿循环，knowledge 模块 217 测试全绿）。实际逻辑流（在 design 伪代码基础上收敛）：

```
parse(fileStream, extension, strategy, options):                 # DocumentParserImpl.parse
  method = strategy.parseMethod or resolveByExtension(extension)
  if method == "xlsx" and isExcelRowStrategy(strategy) and not isExcelExtension(extension):
      warn; method = "default"                                   # REQ-H09 解析侧守卫（降级不阻断）
  switch method: ... case "xlsx" → parseExcel(...)

parseExcel(stream, extension, strategy):
  er = resolveExcelRow(strategy)                                 # configResolver.resolve(strategy)，非 excel_row 返回 null
  nodes = parseExcelContent(stream, null, llmModel, llmConfig, er)
  ...序列化/页数同既有

parseExcelContent(stream, filter, llmModel, llmConfig, excelRow): # 新重载；旧签名委托 null
  for sheet in workbook.sheets:
      隐藏 Sheet 跳过；HEADING(sheetName)；空 Sheet 跳过；effectiveLastRow = min(lastRow, MAX_ROWS_PER_SHEET)
      mergedRegionIndex = buildMergedRegionIndex(sheet)
      if excelRow != null:
          emitExplicitHeaderSlices(...)                          # 显式表头 + 按行切片（REQ-H05~H07）
          sheetIndex++; continue
      ...既有自动表头路径
emitExplicitHeaderSlices:
  headerStart/End/SliceStart 转 0-based（1-based 口径，REQ-H03）
  if effectiveLastRow < dataStart: warn; return                  # 行数不足跳过（REQ-H07），仅留 HEADING
  headers = mergeMultiRowHeaders(sheet, headerStart, depth)      # 沿用既有「-」连接合并语义；不跑 detectHeaderRegion/LLM（REQ-H05）
  dataRows = [r in dataStart..effectiveLastRow: 非隐藏且非空]     # 字面行模式，无表尾说明行启发式
  for slice in ExcelRowSlicer.slice(dataRows, headers, rowsPerSlice):
      emit TABLE(headers=headers, rows=slice, pageNumber=sheetIndex+1)   # 每片自带表头（REQ-H06）

ExcelRowSlicer.slice(dataRows, headers, rowsPerSlice):           # 纯函数，4800 常量
  budget = 4800 - headerBlockChars(headers)
  for row: 单行超 budget → 硬切多段（续段重复表头）
           else 累计至 rowsPerSlice（软目标）或超 budget（硬上限）封片

structuralChunk(nodes, strategyId):                              # ChunkingServiceImpl（excelRowMode 分支）
  excelRowMode = chunk.strategy == "excel_row"
  effectiveOverlap = excelRowMode ? 0 : overlap                  # 切片自包含，不回填 overlap
  if excelRowMode and ignoreTables: ignoreTables = false         # REQ-H08
  原子分支 flush 条件：hasBodyContent and (excelRowMode or 超 chunkLength)  # 每切片独占分片
  尾部：if not excelRowMode: result = handleAtomicOverflow(result, chunkLength)  # 豁免二次拆分

ParseStrategyServiceImpl.validateExcelRowParams(advanced, extensions):   # 保存期校验（create/update/saveFileStrategy 三入口）
  生效 chunk.strategy == excel_row 才校验；extensions 归一后 ⊆ {.xlsx,.xls}（ERR-PS-EXT-DOMAIN）
  三参必填 + 1-based + 起≤终 + 区间≤5                                    # ERR-PS-HEADER-RANGE
  sliceStart > headerEnd                                                 # ERR-PS-SLICE-START
  rows ≥ 1（缺省 100，无上限硬校验）                                      # ERR-PS-ROWS-PER-SLICE

StrategyConfigResolver: chunk.excelRow 嵌套组逐项容错合并；strategy=excel_row 且组缺失时归一化补默认（rowsPerSlice=100）
```

**Decisions & Deviations**（实现相对 design/契约的偏差与补充裁决）：
- **excelRow 组仅新分组承载**：REQ-G02 双格式只覆盖既有字段（chunkLength/overlap/tableMode 等），excelRow 为新增参数无存量平铺形态——旧平铺位置出现 strategy=excel_row 时按三参缺失拒绝（有守护测试）。
- **小切片强制独占分片**：设计伪代码未显式覆盖——excel_row 模式下原子分支 flush 条件追加 `excelRowMode`，否则 ≤chunkLength 的相邻切片会被合并、破坏「每片自带表头」语义。
- **overlap 在 excel_row 模式不回填**：flushWithOverlap 传 0——切片间若回填前片尾部会污染「每片自带表头」。
- **多行显式表头沿用 mergeMultiRowHeaders 的「-」连接合并语义**（与自动路径一致），不另造展开规则。
- **REQ-H09 守卫落点在解析侧**（parse() 分发前降级 default 路径）：非 Excel 文件走 parseExcel 会 POI 崩溃，必须在解析前拦截；消费端分派无需改动——excel_row 落默认分支（有节点走 structuralChunk）自然承接。
- **REQ-H13/QA 回退通道守护未自动化**：loadExtractChunks 为私有方法且无集成 harness，行为零改动（chunkType=table 分片天然入选），由 ExcelRowChunkingTest 的 chunkType 断言与查询条件代码实读保障；**零 DDL 对账**同理由「不新增迁移」构造保证。
- **S11 预览一致性**由同路径构造保证（preview 与摄取共用 parse(strategy)→parseExcelContent），不设独立端点用例。

## Seam {#seam}

**缝 1（既有，真缝）分片策略分派**：五种分片器为适配器，`chunk.strategy` 是稳定词汇。excel_row 不新增分片器适配器、复用 structuralChunk 原子 TABLE 路径（adr/0012），分派缝稳定不动。HTTP 端点（策略 8 端点 + meta/模板 + re-chunk/strategy/preview 绑定面）均为薄包装，外部契约归 docs/interfaces.md。

**缝 4（新，真缝）表格切片方式**：What varies——Excel 表格装配为切片节点的方式（自动表头整表 vs 显式表头按行）。Adapters——既有自动表头路径（detectHeaderRegion + LLM 兜底 + 整表单 TABLE）与 ExcelRowSlicer（显式表头 + 多 TABLE），2+ 适配器成真缝。Stable side——DocNode TABLE 形状（headers/rows）与 structuralChunk 原子处理，解析/分片/预览消费方不变。Placement rationale——解析期装配使预览与摄取同路径、元数据零新增、复用 Sheet 循环宿主（adr/0012）。

**深化方向**（继承 design/parse-strategy.md §4）：BindingGuard 成类并统一双自动匹配实现；CRUD 两级校验差异收敛；StrategyConfigResolver 吞掉未实现的「请求参数覆盖层」。

## Trace {#trace}

- StrategyConfigResolver（契约单元一）→ REQ-G01、REQ-G02
- StrategyConfigResolver（合并容错与 llmModel 下游生效）→ REQ-G03、REQ-G06
- StrategyConfigResolver（excel_row 经同一三层合并与类型组）→ REQ-H01
- ParseMethodRegistry（契约单元二）→ REQ-B03、REQ-B04
- ParseMethodRegistry（兼容判定共享口径）→ REQ-G04
- ParseStrategyBindingGuard（契约单元三，回退与改绑拦截）→ REQ-C01、REQ-C02
- ParseStrategyBindingGuard（回退语义与预设三步）→ REQ-C04、REQ-E05
- ParseStrategyBindingGuard（换绑三态与文件级原子保存）→ REQ-D03、REQ-D04
- ParseStrategyBindingGuard（重切清理与真源覆盖）→ REQ-E02、REQ-E03
- ParseStrategyBindingGuard（元数据改绑拒绝与保存即重切）→ REQ-E01、REQ-E04
- StrategyCrud (S)（CRUD 与引用计数）→ REQ-A01~A05
- StrategyCrud (S)（默认唯一与冲突软约束）→ REQ-B01、REQ-F01
- StrategyCrud (S)（冲突软约束与物理约束校验）→ REQ-F02、REQ-G05
- StrategyCrud (S)（excel_row 保存期扩展名域与表头三参校验）→ REQ-H02、REQ-H03
- StrategyCrud (S)（切分行数默认与下限）→ REQ-H04
- 表格切片方式缝（显式表头取代自动检测）→ REQ-H05、REQ-H06
- 表格切片方式缝（多 Sheet 统一与忽略 tableMode）→ REQ-H07、REQ-H08
- 表格切片方式缝（预览同路径）→ REQ-H11
- 分派侧运行时兜底（非 Excel 降级 rule_fixed）→ REQ-H09
- 原子 TABLE ≡ 分片（元数据沿用零 DDL）→ REQ-H10
- 模板四分类不变 → REQ-H12
- 下游零适配（QA 回退通道与图谱单元组装不改动）→ REQ-H13
- 解析期装配落点 → ADR-0012-EXCEL-ROW-SLICE-AT-PARSE

## Code {#code}

```yaml
- path: fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/config/StrategyConfigResolver.java
  description: 三层配置合并唯一入口（IF-006 实现），excel_row 经 ChunkConfig.ExcelRow 透传
- path: fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/model/ParseStrategyConfig.java
  description: 类型化配置模型；新增 strategy 第六枚举 excel_row 与 ExcelRow 三参组（REQ-H01/H03/H04）
- path: fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/parser/ParseMethodRegistry.java
  description: 文档类型/扩展名/解析方法唯一权威映射（IF-007 实现）
- path: fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/parser/DocumentParserImpl.java
  description: parseExcelContent 增加 excel_row 重载与显式表头分支（emitExplicitHeaderSlices）；parse() 前置 REQ-H09 守卫；内嵌表格走自动分支不受影响
- path: fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/parser/ExcelRowSlicer.java
  description: 按行切片纯函数助手——切分行数软目标 + 4800 字符硬上限（含表头）组装切片，单行超长硬切续段（REQ-H06 核心）
- path: fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/chunking/ChunkingServiceImpl.java
  description: structuralChunk excelRowMode——忽略 tableMode、切片强制独占分片、零 overlap 回填、豁免 handleAtomicOverflow（adr/0012）
- path: fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/service/impl/ParseStrategyServiceImpl.java
  description: 策略 CRUD 与保存期校验宿主（StrategyCrud (S)）；validateExcelRowParams 三入口校验落点（ERR-PS-* 对应面）
- path: fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/service/impl/FileServiceImpl.java
  description: BindingGuard 四处守卫的实现宿主（IF-008 Implemented by；该文件主归属 file-ingestion 链）
- path: fastrag-modules/fastrag-knowledge/src/test/java/com/fastrag/module/knowledge/parser/ExcelRowSlicerTest.java
  description: 切片器纯函数测试——软目标封片/4800 硬上限/单行硬切/钳位/空输入（S8）
- path: fastrag-modules/fastrag-knowledge/src/test/java/com/fastrag/module/knowledge/parser/ExcelRowSliceParseTest.java
  description: 解析期装配测试——POI 构簿断言多 TABLE 切片产出、区间跳过、多 Sheet 统一、合并表头展开（S8/S9）
- path: fastrag-modules/fastrag-knowledge/src/test/java/com/fastrag/module/knowledge/chunking/ExcelRowChunkingTest.java
  description: 分片侧测试——整片入分片/零 overlap 回填/忽略 tableMode/元数据沿用/rule_fixed 对照（S10）
- path: fastrag-modules/fastrag-knowledge/src/test/java/com/fastrag/module/knowledge/config/ExcelRowConfigResolverTest.java
  description: 配置合并测试——excelRow 组映射/归一化补默认/单项容错/非 excel_row 不生成（S7 归一侧）
- path: fastrag-modules/fastrag-knowledge/src/test/java/com/fastrag/module/knowledge/chunking/ExtractionUnitAssemblerOversizeTest.java
  description: 图谱下游守护——4800 切片独占抽取单元不截断（S11，REQ-H13）
- path: fastrag-modules/fastrag-knowledge/src/test/java/com/fastrag/module/knowledge/controller/ParseStrategyTemplateControllerTest.java
  description: 模板四分类现状断言——不含 excel_row 模板（S11，REQ-H12）
- path: fastrag-modules/fastrag-knowledge/src/test/java/com/fastrag/module/knowledge/service/impl/ParseStrategyServiceImplTest.java
  description: 保存校验测试——ERR-PS-* 四码用例化 + 生效值校验 + 文件级同族（S7，既有 7 例全保留）
```
