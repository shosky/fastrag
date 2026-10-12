# 任务书：INFERRED/GAP 全量裁决（idd-onboard 代裁模式，2026-09-24）

## 授权与模式

用户明确授权：**不再等用户确认，由代理分析代码实现后代为拍板**（「你分析代码实现拍板」）。本任务将 18 特性链文档中全部 109 条 INFERRED 标注与 87 处 [GAP] 占位裁决闭环。裁决不是臆造——每条必须落为以下四类之一，且标注清晰可辨：

| 类别 | 判据 | 改写方式 |
|---|---|---|
| **R1 深证升级** | 再读实现路径后找到直接证据 | 删〔INFERRED：…〕，改 ‹CERTAIN（2026-09-24 深证：file:line）› + 如需补一句证据摘要 |
| **R2 现状裁定** | 代码只支持现状，意图不可考 | 改 〔决议 2026-09-24：按现状为准——<一句话裁定>（代裁，用户可推翻）〕；若现状属缺陷/债务，注明「已登记债务见 X」 |
| **R3 GAP 实证** | 更深入搜索代码/配置/schema 后找到答案 | 删 [GAP: …]，写入答案 + ‹CERTAIN 补证：file:line› |
| **R4 GAP 代裁** | 确属代码外信息（SLA/产品意图/标准选型） | 改 〔决议 2026-09-24：取 <具体值/策略>；理由：<业界惯例/一致性/最小改动依据>；现状差距：<登记债务或"无">（代裁，用户可推翻）〕 |

**禁止**：把 R4 写成事实语气；删除 REQ 正文；改动 REQ ID 与既有 CERTAIN 证据；编造 file:line。

## 判定辅助规则

- 行为类 INFERRED（「X 是否有意」）：若代码有注释/测试断言/对称实现佐证 → R1；否则 **一律 R2 按现状为准**（现状即事实源），如与契约目标态相悖，确认债务已在 implement/contract 登记，未登记则顺手补登记行。
- 缺口类 GAP（阈值/SLA/保留期/上限）：按「业界惯例 + 本仓一致性」取保守默认值，R4 裁决；现状未实现该值的 → 现状差距栏登记债务。
- 选型类 GAP（标准/协议/策略）：取主流默认（如无障碍=WCAG 2.1 AA、JWT 密钥=环境变量注入不入库），R4。
- 「是否有消费方/写入方」类：深 grep 全仓（含 test、前端仓 `fastrag-frontend/src`）后定 R1/R3。
- 裁决若推翻既有文档断言（如某「恒空」其实有生产者）：以代码为准改正断言本体，并在返回中说明。

## 分工（7 代理，文件集互不重叠；共享文件只动本特性章节）

| 代理 | 特性 |
|---|---|
| 1 | iam、kb-core、qa-pairs |
| 2 | parse-strategy、file-ingestion、chunk-management |
| 3 | onlyoffice-editing、ai-chunking、retrieval |
| 4 | knowledge-graph、benchmark-evaluation |
| 5 | publish-review、platform-config、tools-skills-mcp |
| 6 | workflow、agent |
| 7 | app-center、operation-analytics |

每个代理：对名下特性的 spec/design/contract（本特性章节）/tdd/implement 五处，grep `〔INFERRED` 与 `[GAP:` 枚举全部条目 → 逐条按上表裁决改写。**时间纪律**：逐特性处理、处理完一个特性落盘一次；不要长时间只读不写。

## 返回格式（每代理，≤35 行）

- 处理条目总数：INFERRED n（R1 n / R2 n）+ GAP n（R3 n / R4 n）
- **重要裁决清单**（只列影响语义或推翻既有断言的，逐条一行：位置 — 裁决 — 依据）
- 新登记债务（R2/R4 顺手补登的）
- 未能裁决的例外（理论上应为 0；若有，说明为何）
