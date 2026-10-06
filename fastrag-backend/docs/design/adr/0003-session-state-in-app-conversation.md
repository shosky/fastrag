# ADR: 会话一等公民落在 app_conversation 原表扩展，不新建独立会话表

**Date:** 2026-09-25
**Status:** Accepted
**Research:** [docs/research/weknora-vs-fastrag.md](../../research/weknora-vs-fastrag.md)（WeKnora v0.8.2 sessions 体系调研）；grill 裁决记录 `fastrag/.tmp/idd-new-conversation-grill-decisions.md`

## Context

「新建对话」改造要求会话脱离应用成为一等公民（直聊会话无 app_id），而现有 `app_conversation.app_id NOT NULL`（schema.sql:1115-1117），且归属校验、级联删除、软删、反馈等账本不变量全部以 `(appId, sessionId)` 为定位键。对齐 WeKnora 的做法是独立 `sessions` 表（其会话结构与挂载应用解耦），但这意味着新建一套会话账本并与旧账本共存。

## Decision

扩展原表：`app_id` 放宽为可空 + 新增 `request_state` JSON 列（另有 `is_pinned`、消息 `references` 列），归属校验收敛到「会话 ID 主键定位 + app 可空前置条件」变体；不新建独立会话表。

## Alternatives Considered

**新建独立 `chat_session` 表（对齐 WeKnora 命名与形状）** — 否决：AppConversationLedger 的归属校验/级联删/软删/反馈全链（AppServiceImpl.java:93-104,241-366,330-339）要么复制要么服务互调，且统一会话中心列表（REQ-A03）要求新旧会话并入同一视图，两张表反而需要 UNION 或双写。WeKnora 自己也有"复用旧列避免新迁移"的先例（其 SessionLastRequestState 复用 `agent_config` JSONB 列，internal/types/session.go:95-101）。

**仅前端伪装（新对话落到"系统默认应用"）** — 否决：会话仍强制挂应用，"会话一等公民"语义不成立，且默认应用会成为配置面与权限面的污染源。

## Consequences

换来的是：账本不变量单点维护、统一列表零迁移（REQ-G03）、DDL 变更收敛为三列（NFR-02 双处同步义务）。代价是：`app_id` 语义从"必有"变为"可选"，全部 `eq(app_id,…)` 查询（实测 7 处）必须条件化，且 app-center 旧通道与新通道共享一张表后，任何一方加列都影响另一方——账本层的变更从此必须双链评审。
