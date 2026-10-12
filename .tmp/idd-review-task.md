# 任务书：IDD 链文档完整性审计（idd-review，2026-09-24）

## 背景与范围

- 审计对象：`D:\Workspace\java\github\rag\fastrag\fastrag-backend\docs\`（**仅文档，不审源码**）。
- 该树由 idd-onboard 批量构建：18 特性 × 五阶链（spec/design/tdd/implement 各 18 份 + 共享契约 `contract/interfaces.md`（~1709 行/18 章）与 `contract/data.md`（~1451 行/18 章））+ 根文档（README/feature-map/conventions）+ sessions ×3。
- 构建 README 为准绳之一；格式准绳 = 五个阶段技能的输出模板（C:\Users\63179\.zcode\skills\idd-{spec,design,contract,tdd,implement}\SKILL.md）+ 本技能（idd-review）的 TAXONOMY.md 与 NOISE.md（同目录）。
- 仓库 Role = `backend`（CONTEXT.md IDD 元数据）。文档语言 = 中文。
- 已知背景（审计时知悉，勿重复报告为发现）：① 根 `docs/` 树已删除，本树 4 处 `../../docs/` 引用为已登记死链；② tdd 全部为测试设计（红绿循环未执行，有意为之）；③ INFERRED/GAP 行内标注是有意纪律，不算格式缺陷；④ iam spec 为上位仓搬入件（含搬入复核节）。

## 分域指派（5 个审计员，各审一类）

| 审计员 | 目录 | 格式准绳技能 | 重点 |
|---|---|---|---|
| A | spec/（18 份） | idd-spec | 7 节齐备（特性与范围/Out of Scope/领域术语/功能需求(域+REQ 两级)/非功能/验收清单/测试策略）；REQ 稳定 ID 且可证伪；验收清单覆盖全部可证伪断言；无 design 决策/接口签名泄漏；术语与 CONTEXT.md 一致 |
| B | design/（18 份） | idd-design | Module Map 每模块有 Responsibility/Files/Depth Rationale；浅模块标 (S) 且不给接口节；Interface Contracts 有 Public/Hidden/Depth rationale；Seam 四要素（varies/Adapters/Stable side/rationale）；ADR 三条件纪律；**Parent Trace 指向的 spec REQ 真实存在（抽查每个文件的 2-3 条到 spec 文件验证）**；回答「架构文档在哪里/是否名实相符」 |
| C | contract/interfaces.md + data.md | idd-contract | 18 章节齐（各特性一节）；每接口 Purpose/Input/Output/Error modes/Invariants/Parent Trace 六要素（合并标签算合规）；每实体字段类型/约束/生命周期 + Relationships 基数/级联；端点排除铁律（无单端点/CRUD 包装契约）；**抽查 Parent Trace → spec REQ 存在性** |
| D | tdd/（18 份） | idd-tdd | 4 节齐（切片规划 AFK/HITL/测试用例(表)/追溯/待实施说明）；测试名域语言（[领域动作][条件下][产出结果]）；mock 仅在系统边界；追溯链 Test→Contract→Design→REQ 抽样验证 REQ 存在 |
| E | implement/（18 份） | idd-implement | 3 节齐（Code File Map/Interface Implementations/Testing）；Logic Flow 伪代码级（IF/ELSE/RETURN，无「校验并处理」式含糊）；Module Interactions 三类齐全；Decisions & Deviations 显式；内容边界（无整段源码/import/boilerplate）；**抽查 Parent Trace → 契约接口存在** |

## 共同检查项（全员）

1. **完整性清点**：18 份齐不齐（对照 ls）；行数异常（<60 行的文件视为可疑，报告）。
2. **交叉层一致性抽查**（每文件抽 2 条即可，不必全量）：上游引用（REQ ID / 契约接口名）在下游文档中真实存在；跨层术语与 CONTEXT.md 一致。
3. **噪声检测**（NOISE.md N1-N5）：过期标记、重复叙述、超长文件。
4. **语言一致性**：中文为主，混语言报告 DI。
5. 发现缺陷/测试缺口 → 建议 /idd-diagnose、/idd-tdd（不修）。

## 输出格式（每个审计员）

- 顶部一段总结（该域几份、几个发现、按 P 级计数）。
- 逐发现四要素段（人话先行，taxonomy 代码放尾部 tag）：
  ```
  [P0/P1/P2] 文件:行号
  Problem: 用项目自己的名词说具体问题（真实文件/模块/术语名）。
  Consequence: 什么会坏、何时、对谁（禁止"可维护性差"式空话）。
  Action: 一个可执行的下一步。
  Tags: [类型码]
  ```
- P 级：P0=功能正确性受损（先修）；P1=可维护性受损（本周期修）；P2=质量改进（ eventual）。
- 精简：只报要紧的；总输出 ≤1500 字为宜。
