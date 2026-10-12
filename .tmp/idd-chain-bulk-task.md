# 任务书：FastRAG 后端 IDD 完整链批量产出（2026-09-21）

## 背景与模式

- 仓库：`D:\Workspace\java\github\rag\fastrag`，后端目录 `fastrag-backend/`（Java 17 / Spring Boot 3.2.5 / 15 Maven 模块 / 55 控制器 / ~68.9k LOC），HEAD d8f30cd。
- IDD 文档树（本次产出目标）：`fastrag-backend/docs/{spec,design,contract,tdd,implement}/` + `fastrag-backend/CONTEXT.md`。
- **模式 = onboard mode**：grilling 问题一律从代码证据回答，不阻塞、不臆造。证据三层：
  - **CERTAIN**：代码直接可观测 → 自动写入，必须引 `文件路径:行号`；
  - **INFERRED**：结构强推断 → 自动写入，行内标注 `〔INFERRED：一句话理由〕`，汇总到返回值待用户确认；
  - **GAP**：代码外信息（意图/SLA/阈值取舍/被否方案）→ 行内 `[GAP: 问题 — 证据不在代码中]` 占位，继续写，并汇总到返回值。
- 文档语言：**中文**（术语用 `fastrag-backend/CONTEXT.md`，不得用其「避免」同义词；统一返回叫 `ApiResponse` 不叫 Result/R，分页 `PageResult{list,total,page,pageSize}`）。
- 本任务只写文档，**不修改任何源代码/测试代码**。

## 必读输入（每个子代理开工前按序读）

1. 本任务书（规则+全部格式，**不必**再去读 ~/.zcode/skills/idd-* 的 SKILL.md）
2. `fastrag-backend/CONTEXT.md`（术语+元数据）
3. `fastrag-backend/docs/feature-map.md`（找到本特性的条目=证据包起点；只读，不改）
4. `fastrag-backend/docs/conventions.md`
5. 上游链文档（按依赖序已完成，见各波次指派）
6. 特性相关源码（各波次列出的模块路径，**必须实读代码再写文档**）

## 输出物与格式（ canonical 结构，逐条遵守）

### A. `docs/spec/<feature>.md` — 需求规格

```markdown
# [特性名] — 需求规格
## 1. 特性与范围（一段话：是什么、显式排除什么）
## 2. Out of Scope（具体到能拒绝一个需求请求）
## 3. 领域术语（引用 CONTEXT.md 条目）
## 4. 功能需求
### 4.1 [功能域 A]（叙述段：这部分做什么、为何重要）
- **REQ-A01**：可证伪的一句话需求。〔证据：path:line ‹CERTAIN› / 〔INFERRED：…〕 / [GAP: …]〕
- **REQ-A02**：…（粒度=设计/契约可追溯的行为簇，不原子化琐碎项）
### 4.2 [功能域 B] …
## 5. 非功能约束（仅代码/配置可证者；性能阈值无证据就 GAP）
## 6. 验收清单（每条可证伪断言一个 checkbox：- [ ] 问题式断言）
## 7. 测试策略（经由公共接口测什么最重要）
```
规则：REQ 稳定 ID（REQ-<域><序号>）；**不得**混入设计决策（"用 Redis 缓存"属 design）与接口签名（"POST /api/x"属 contract）；无 metadata 头（来源/优先级）。

### B. `docs/design/<feature>.md` — 架构设计

```markdown
# [特性名] — 架构设计
## 1. Module Map（逐模块：Responsibility 一行 / Files / Depth Rationale；浅模块标 **(S)** 并建议内联，不给接口节）
## 2. Interface Contracts（逐模块：Public interface 方法列表（一行一个=用途） / Hidden implementation / Depth rationale）
## 3. Seam Diagram（逐缝：What varies / Adapters（2+=真缝，1=假设缝不立缝）/ Stable side / Placement rationale）
## 4. Deepening Opportunities（逐模块：还能吞什么复杂度；无则"None"）
## 5. Parent Trace（模块/缝/深化 → Spec REQ-XXX）
```
模块名用 CONTEXT.md 术语（ChunkModule 而非 XxxHandler）；对既有 ADR 的**要点**要引用并说明符合/冲突——ADR-0001（解析策略绑定变更必须触发重新分片）、ADR-0002（图存储收敛 Neo4j，唯一实现，启动 fail-fast）：⚠ 原 `docs/legacy/adr/` 归档文件已随根 docs/ 树删除而丢失，只引要点编号，**不要**给文件路径。重大取舍（难逆转+费解+真权衡三者齐备）可记 ADR 于文末「## 6. ADR」小节，否则不记。

### C. `docs/contract/interfaces.md` 与 `docs/contract/data.md` — 契约（**共享生长文件**）

- 文件已存在 → **追加/修改本特性章节**（用 `## <特性名>` 顶层分节），保留其他特性章节原样；不存在 → 创建（文件头一行说明这是全后端共享契约工件）。
- **端点排除规则（后端铁律）**：不给单个 HTTP 端点、简单 CRUD 包装、透传模块写契约——它们是所属模块的实现细节。契约只写给"删除测试通过"的深模块接口。HTTP 层在 design 的 Interface Contracts 里以公共接口形式出现即可。
- interfaces.md 每接口：**Purpose（1-2 句业务流中的存在理由）/ Input（字段名（类型, 必填?, 约束）—含义）/ Output / Error modes（E_XXX 名称 — 触发: … 恢复: …）/ Invariants（Pre/Post）/ Parent Trace（接口 → Design 模块 → Spec REQ-XXX）**。
- data.md 每实体：字段（类型, 必填/唯一/索引）— validation / Lifecycle；**Relationships**（基数/经由字段/级联）；Parent Trace。表结构实读 `init-scripts/schema.sql` 与实体类，勿凭记忆。
- 字段/错误模式**必须列全**；代码推导不出的写 `[GAP: …]`，不省略不臆造。

### D. `docs/tdd/<feature>.md` — 测试设计（onboard 模式特殊化）

**不写测试代码**（本任务禁止改源码）。产出测试设计文档：
```markdown
# [特性名] — 测试设计
## 1. 切片规划（按契约接口分组；每片标 AFK=可自动化 / HITL=需人决策；HITL 在前）
## 2. 测试用例（逐接口）
### [接口名]（contract: <Module>.<Interface>）
| 测试名（域语言：[领域动作] [条件下] [产出结果]） | Given | When | Then（=断言） | 追溯 REQ | 边界mock |
（mock 只允许在真缝/系统边界：外部API、MinIO/Milvus/Neo4j/RabbitMQ/时间；禁 mock 内部模块）
## 3. 追溯（测试 → 契约接口 → Design 模块 → Spec REQ 完整链抽样表）
## 4. 待实施说明（红绿循环未执行；执行时按本文件从 Tracer Bullet 起步）
```

### E. `docs/implement/<feature>.md` — 实现记录（记录**既有代码**如何实现契约）

```markdown
# [特性名] — 实现文档
## 1. Code File Map（代码树（只列本特性相关文件）+ 一行职责；镜像 design Module Map）
## 2. Interface Implementations（逐契约接口）
### [接口名]（from contract: [Module.Interface]）
**Logic Flow:**（伪代码级：1. … 2. IF … THEN … ELSE … 3. 状态转移 4. 错误恢复 5. RETURN；每个分支和错误路径都要出现，禁止"校验并处理"式含糊）
**Module Interactions:**（Calls / Called by / Shared state（哪张表/缓存/队列/主题）——逐条列全，无则写"无外部模块交互"）
**Decisions & Deviations:**（实现与契约的偏离+理由；无偏离则明说；含代码中的关键取舍）
**Parent Trace:** [实现单元] → [契约接口] → [Design 模块] → [Spec REQ-XXX]
## 3. Testing（引用 tdd/<feature>.md 的切片；说明现有测试覆盖了哪些、缺口哪些）
```
禁止：贴整段源码、import/类型声明、fixture 细节。

## 铁律汇总

1. 先读代码再写文档；每个 CERTAIN 必须有 file:line。
2. spec 不漏 design/contract 内容；contract 遵守端点排除；implement 伪代码级。
3. 术语从 CONTEXT.md；中文；不臆造；INFERRED/GAP 显式标注。
4. 共享文件（contract/interfaces.md、contract/data.md）只追加/修改本特性章节。
5. 不改任何源代码、测试代码、feature-map、README（链状态由主控统一更新）。
6. 若上游文档缺失或互相矛盾 → 按证据仲裁并在返回中说明，不静默。
7. **增量写盘**：每份文档起草完立即 Write 落盘再写下一份，不要攒到最后一起写。
8. **最终返回保持精短**（≤40 行：计数 + INFERRED/GAP 清单 + 仲裁说明），不要在返回里复述文档内容。

## 返回格式（每个子代理）

```markdown
## <feature> 链完成报告
- 产出：5 个文档路径 + 各自行数
- 计数：REQ n / 契约接口 n / 数据实体 n / 测试用例 n
- INFERRED 清单（逐条：ID — 断言 — 理由）
- GAP 清单（逐条：ID — 问题 — 占位位置）
- 仲裁说明（与上游/ADR 冲突时）
```
