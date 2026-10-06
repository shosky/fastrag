# ADR: 解析策略绑定变更必须触发重新分片（strategy-binding-requires-rechunk）

> **重构说明**：本 ADR 原文（上位 legacy 归档）已随根 `docs/` 树于 2026-09-21 删除丢失（回收站无记录，恢复线索仅 IDEA Local History 或外部备份——见 feature-map.md 卷首注）。本文于 2026-09-24 由链内权威要点源重构：CONTEXT.md 词条（策略绑定/重新分片/AI分片/解析策略）、feature-map #3/#5、各设计文档的一致转述、contract/interfaces.md `## parse-strategy`/`## chunk-management`/`## onlyoffice-editing` 节与 contract/data.md 相关节。内容仅含上述来源已登记的事实与要点，不臆造新细节；不可考处以 `[GAP]` 标注。

**Date:** 原文撰写日期不可考〔GAP: 原文已丢失〕；本文重构于 2026-09-24
**Status:** Accepted（依链内 17 份设计文档一致以其为「既有决议/符合性基准」的口径确认；原文已丢失，本文为要点重构）

## 背景（Context）

解析策略是文档切分规则的配置（分片长度、重叠、分隔符、LLM/VLM 模型等参数，CONTEXT.md「解析策略」词条）。文件与策略的绑定（Strategy Binding）分两级：知识库级默认绑定（按扩展名自动匹配）与文档级覆盖（用户显式指定，优先于自动匹配）。分片与向量是摄取流水线在**当时绑定**下产出的衍生数据：绑定变更后，既有分片仍是旧规则的产物，若只改绑定不重切，「当前绑定 ⟺ 分片结果」即失配，进入漂移状态。该一致性是检索正确性的前提，难以为下游感知与修补，必须作为架构级不变式强制。

## 决策（Decision）

**绑定变更必然触发重新分片（Re-chunk）**——删除文档既有分片与向量后，按当前（或新指定的）策略绑定重跑「解析→分片→向量化」流水线；不允许只改绑定不重切的漂移状态。不变式全称（contract/interfaces.md `## parse-strategy` 节 ParseStrategyBindingGuard）：在任意操作序列（上传指定/元数据编辑/重试/重切/文件级自定义保存/删除回退）下，「kb_file.parse_strategy_id（当前绑定）与该文件分片结果一致」恒成立，绑定真源唯一。

链内分工（各设计/契约文档已登记）：

- **裁决面** — parse-strategy.ParseStrategyBindingGuard：绑定合法性校验、换绑三态、重切真源修正、元数据改绑拦截（实现散布于 FileServiceImpl 四处私有方法，已登记为首要深化候选，design/parse-strategy.md §4）。
- **执行面** — chunk-management.RechunkOrchestrator：状态守卫→删 auto 留 manual→清向量/图谱→重置文件→重发摄取→完成后 manual 对账；不存在「已清理未重跑」的合法终态（design/chunk-management.md §1，contract/interfaces.md `## chunk-management` 节）。
- **触发面** — file-ingestion 的 re-chunk/retry/saveFileStrategy（FileServiceImpl.java:369-482、555-560 ‹CERTAIN›）；onlyoffice-editing 的编辑保存回调 `reChunkFile(strategyId=null)`——承接精神变体「**内容变更必须触发重新分片**」：编辑后的内容已偏离既有分片，唯一合法出路是按当前绑定重切（design/onlyoffice-editing.md 卷首）。

## 被否备选（Alternatives Considered）

**允许「只改绑定不重切」的漂移态（绑定与重切解耦）** — 即本决策明令禁止的状态：改绑仅更新 parse_strategy_id，既有分片与向量保留至下次自然重跑。CONTEXT.md「策略绑定」词条与 chunk-management 链均以其为被禁形态登记，但原始否决论证（性能/复杂度/一致性的具体权衡表述）随原文丢失。〔GAP: 原文已丢失，此节为要点重构——可考的备选仅此一项，由被禁形态反推；其余备选不可考。〕

## 后果（Consequences）

- 改绑成本 = 一次全量重切（删分片/清向量/图谱随分片重建，原文档与 QA 对保留），换取系统恒不存在「已编辑未重切」「已清理未重跑」的合法终态；绑定变更与旧数据清退收拢在同一流程事务语义里完成（design/parse-strategy.md §3：清理收拢在重切流程内是本 ADR 的物理保证）。
- 相容特化两条（链内登记）：① QA 模式文件拒绝重切——QA 对不受分片策略影响，不存在绑定漂移问题（FileServiceImpl.java:382-384 ‹CERTAIN›）；② AI 分片替换落库不走重切流水线——其「结果」由用户确认边界定义（姊妹不变式「用户确认边界=唯一事实源」），策略绑定在下次 Re-chunk 重新生效，与本不变式不冲突（CONTEXT.md「AI分片」词条即此裁决）。
- 局部性代价：一致性守卫散布于 FileServiceImpl 多处私有方法，跨 parse-strategy/chunk-management/file-ingestion/onlyoffice-editing 四链协作——已登记为深化候选（design/parse-strategy.md §4）。
