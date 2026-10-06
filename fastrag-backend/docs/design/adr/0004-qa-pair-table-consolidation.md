# ADR: AI 问答对与人工问答对共用 kb_qa_pair 单表，以 source 列区分

**Date:** 2026-09-26
**Status:** Accepted
**Research:** WeKnora 对比调研（2026-09-26 Explore 会话：其 Doc2Query 挂 chunk metadata、FAQ 独立 FAQChunkMetadata）；grill 裁决记录 `fastrag/.tmp/idd-qa-generation-grill-decisions.md`（D1/D12/D14）

## Context

问答对提炼需要持久化 LLM 产出（问题+答案+溯源）。`kb_qa_pair` 已被检索 qa 通道（RetrievalServiceImpl.java:798-839，权重 0.3）与 QA 管理面板消费，实体早已预留 `source`（manual/ai）与 `status`（draft/confirmed）两列（KbQaPair.java:36-37）但 ai 来源无写入方（qa-pairs spec REQ-E03 GAP）。提炼产出的持久化形态因此有三个候选。

## Decision

AI 产出直接落 `kb_qa_pair`（source=ai），复用既有确认状态机与 qa 召回通道；不新建表，不挂 chunk 元数据。

## Alternatives Considered

**独立提炼产出表（如 kb_qa_generation_item）** — 否决：qa 通道要么 UNION 双表要么双读，QA 面板与导入判重基线同样双读；draft→confirmed 状态机、级联清理（KB 删/文件删/跨库移动）三套全部翻倍；而两表唯一的收益——隔离 AI 脏数据——已由准入规则（ADR-0005）与静态闸门达成，隔离不必靠物理分表。

**WeKnora 式挂 chunk metadata（generated_questions JSON 存 chunk 行）** — 否决：该形态与 WeKnora「问题命中→回链 chunk」架构配套（问题独立向量索引，无答案列）；本项目 qa 通道以独立行参与召回与融合（Q/A 拼接内容、channel=qa 来源标记），chunk 元数据承载不了 answer 语义与确认状态机，且会使 QA 管理面板失去统一行视图。

## Consequences

换来 qa 通道、管理面板、导入判重、级联清理四处零改造复用；新表 DDL 收敛为一张任务账本（kb_qa_generation_task）。代价是 `kb_qa_pair` 的行语义从此被两条链共享——manual 链（qa-pairs）与 ai 链（qa-generation）任何一方加列或改状态语义都影响另一方，表级变更须双链评审（后果同 ADR-0003 的账本共享）；溯源列（source_chunk_id）加入后，重切悬空语义也成为共享契约的一部分。
