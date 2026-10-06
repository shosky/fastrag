# ADR: 图存储收敛 Neo4j —— GraphStore 唯一实现、启动 fail-fast（graph-store-consolidate-on-neo4j）

> **重构说明**：本 ADR 原文（上位 legacy 归档）已随根 `docs/` 树于 2026-09-21 删除丢失（回收站无记录）。本文于 2026-09-24 由链内权威要点源重构：CONTEXT.md 词条（Graph Store/知识图谱/实体合并）、feature-map #10、各设计文档的一致转述、contract/data.md `## knowledge-graph` 节与 contract/interfaces.md `## knowledge-graph` 节。内容仅含上述来源已登记的事实与要点，不臆造新细节；不可考处以 `[GAP]` 标注。

**Date:** 决策落地证据 `migration-20260905-drop-mysql-graph-store.sql`（2026-09-05 移除 MySQL 图表）；原文撰写日期不可考〔GAP: 原文已丢失〕；本文重构于 2026-09-24
**Status:** Accepted（已落地：MysqlGraphStore 已删除、`neo4j.enabled` 开关移除、Neo4j 启动 fail-fast——feature-map #10、CONTEXT.md「Graph Store」词条）

## 背景（Context）

知识图谱（实体/关系/证据链）需要图存储。历史上存在双实现并存：MySQL 实现（MysqlGraphStore + entity/relation/entity_mention/triple_mention 四张 MySQL 图表）与 Neo4j 实现（Neo4jGraphStore），并以 `neo4j.enabled` 开关使图存储整体可关。双实现与可选开关使图谱能力存在「静默缺席」的运行态，且 MySQL 侧遗留物与 DDL 漂移不断（schema.sql 曾同时保留已废弃 MySQL 图表 CREATE、与迁移脚本矛盾，2026-09-21 修复删除）。图存储选择难逆转（数据真身与全部读写路径绑定其上），故立 ADR。

## 决策（Decision）

**图存储收敛于 Neo4j**：infra 的 GraphStore 接口唯一实现为 Neo4jGraphStore；MysqlGraphStore 与 4 张 MySQL 图表（entity/relation/entity_mention/triple_mention）删除（`migration-20260905-drop-mysql-graph-store.sql`，schema.sql:235-236 注释）；`neo4j.enabled` 开关移除，Neo4j 成为必需依赖，**启动 fail-fast**（配置缺失/不可达即启动失败）。配套 DDL：3 张运行时 kb_graph_* 表（stitch/unit/merge_audit）补入 init-scripts/schema.sql 与 SchemaInitializer 双处一致（2026-09-21 对齐）。

接口存续口径（design/knowledge-graph.md §3、design/retrieval.md §3 登记）：第二适配器消失后 **GraphStore 接口仍保留、不降为具体类**——防实现回摆；接口同时固定失败语义（构建写入失败上抛 / 查询维护失败空结果）与 mention/溯源/合并的语义契约，并为「更大规模用向量库近邻检索替换 O(n²) 两两比对」（EntitySimilarity.java:218-221 注释明示的演进方向）等真实未来变体留缝。

## 被否备选（Alternatives Considered）

**MySQL 图存储（MysqlGraphStore + 4 张 MySQL 图表）** — 已按本决策删除（迁移脚本见上）；其遗留 6 个 MySQL 图表实体/mapper 成死代码（三件齐备、全仓无消费方，contract/data.md knowledge-graph 节死代码警示）。原始否决论证随原文丢失。〔GAP: 原文已丢失，此节为要点重构〕

**保留 `neo4j.enabled` 开关（图存储可选）** — 已随本决策移除开关、改为必需依赖 + 启动 fail-fast；原始论证不可考，仅存链内反向印证：fail-fast 管「配置错误启动即暴露」，运行期不可达另由各消费方通道降级承接，两层容错互补（design/retrieval.md §5）。〔GAP: 原文已丢失，此节为要点重构〕

## 后果（Consequences）

- 部署约束变硬：Neo4j 为必需依赖，缺席即启动失败；运行期不可达不归本 ADR 管，由消费方通道降级承接（如 retrieval 图通道 `graphHits` 空回退 + `graphChannelDegraded` 记账）——与 fail-fast 分层互补不冲突（design/retrieval.md §5）。
- 防回摆：MySQL 图表 DDL 已删、死表 CREATE 与迁移脚本的矛盾已修（2026-09-21）；AGENTS.md 硬约定「图存储：Neo4j 唯一实现（GraphStore，启动 fail-fast），勿再引入 MySQL 图表」。
- 遗留债务（链内登记，非本 ADR 推翻）：kb_graph_merge_audit 合并审计零消费（合并写入未接线，契约先行欠账）；6 个 MySQL 图表实体/mapper 死代码待清；kb_graph_index.settings 仅存于全量脚本 ALTER（升级库漂移）；`graph_auto_build` DDL 缺省 1 与代码缺省 0 矛盾（design/kb-core.md §6 仲裁为以运行时为准，待用户确认）。
- 多库共存口径：全部节点/关系携带 kbId 属性命名空间隔离（Neo4jGraphStore.java:20-21 ‹CERTAIN›）。
