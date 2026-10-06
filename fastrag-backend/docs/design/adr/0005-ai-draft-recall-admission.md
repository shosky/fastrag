# ADR: AI 问答对确认前不参与召回（source='manual' OR status='confirmed' 准入规则）

**Date:** 2026-09-26
**Status:** Accepted
**Research:** grill 裁决记录 `fastrag/.tmp/idd-qa-generation-grill-decisions.md`（D10，修订 qa-pairs spec REQ-D04）

## Context

现状 qaChannelRecall 仅按 kbId 过滤，draft 与 confirmed 同权召回（qa-pairs REQ-D04，2026-09-24 代裁维持「确认仅为管理标记」）。该口径对人工逐条创建成立——创建动作本身即隐含人工信任。问答对提炼落地后，一次任务可产出几十条 draft；若照搬现状口径，未审核的 AI 内容直达检索结果，spec REQ-C01 的人工确认环节形同虚设。

## Decision

qa 召回通道准入规则改为 `source='manual' OR status='confirmed'`：manual 来源维持现状（draft 即召回），ai 来源必须经确认（draft→confirmed 单向转移）才进入召回。

## Alternatives Considered

**维持现状（不加过滤）** — 否决：审核环节空转，用户点「确认」与不点对检索完全等价，提炼功能的审核语义不成立。

**全表 confirmed-only（连 manual draft 也踢出召回）** — 否决：无必要地改变现有人工行为口径——2026-09-24 代裁明确「确认语义仅为管理标记」且存量 manual draft 依赖现状召回；准入收紧应只作用于新引入的自动产出，不应追溯既有数据的行为。

## Consequences

换来「审核有牙」：确认成为 ai 内容进入检索的唯一通道，审核动作有了真实的检索语义。代价有三：①qaChannelRecall 查询增加一个 OR 条件，P95 ≤ 200ms 基线需回归确认（kb 内千级 QA 对、LIKE 全表扫为主项，条件增量可忽略但仍列为观测义务）；②「草稿也能搜到」的面板直觉对 ai 来源不成立，前端需明示「确认后生效」；③该规则是对已裁决 spec 条款（REQ-D04）的修订，回改需走 spec 修订而非随手删过滤条件——这正是立此 ADR 的原因：未来维护者看到这行过滤的第一反应很可能是「冗余，删掉」。
