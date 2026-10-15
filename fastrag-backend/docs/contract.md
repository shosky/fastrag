---
name: Contract Overview
summary: 跨模块契约聚合视图——手写 Modules 分解图（design 拥有）+ 工具投影的 IF-* 定义单元索引。
type: contract
id: CONTRACT-OVERVIEW
status: active
updated: 2026-10-14
---

# Contract Overview

> 本文件由 idd 工具维护聚合视图：`## Modules` 为设计阶段手写的分解图（design 拥有），`## IF-*` 节由 `idd index --write` 从各模块 `## Contract` 定义单元投影生成。
> 布局说明：本仓 20 个存量特性的设计文档位于旧布局 `docs/design/`（未被 5.4.0 工具发现层收录）；`docs/modules/` 为新链文档目录，随特性逐步迁入。

## Modules

系统按「能力域」拆分模块：每个模块隐藏一个变化源（存储协议、执行机制、治理规则），调用方只依赖其定义单元。当前新链仅对话审计一个模块落 `modules/`，其余特性待后续链推进时迁入。

**conversation-trace** — 对话执行的内容级步骤留痕与回看：引擎观察点采集（IF-001，隐藏增量协议/截断/批量写/失败容忍）与查询侧重放组装（IF-002，隐藏增量重放算法），附步骤表实体与内联留存清理 → modules/conversation-trace.md

## IF-001 — ConversationTraceRecorder {#if-001}

**Definition:** [`modules/conversation-trace.md#if-001`](modules/conversation-trace.md#if-001)

**Purpose:** 引擎侧唯一采集入口——一次运行的逐步观察进、traceId 出；调用方（AgentEngine 双执行入口）对增量存储、截断、缓冲、批量写与失败容忍完全无感。

**Signature:**
```java
// com.fastrag.module.agent.trace
public interface ConversationTraceRecorder {
    /** 引擎在 executeStream/executeSync 入口调用；一次运行恰 begin 一次 */
    TraceRun begin(TraceRunContext ctx);
}

// com.fastrag.module.agent.trace
public interface TraceRun {
    /** 每次模型调用完成后调用一次（引擎已持有请求消息与聚合后的响应） */
    void onLlmCall(LlmCallObservation obs);
    /** 每次工具执行完成后调用一次；rawOutput 为回传模型语义的截断前原始输出 */
    void onToolCall(ToolCallObservation obs);
    /** 运行终态调用（finally 位）：批量落表并返回本运行 traceId；落表失败吞错返回 null */
    String complete();
}
```

## IF-002 — TraceTimelineAssembler {#if-002}

**Definition:** [`modules/conversation-trace.md#if-002`](modules/conversation-trace.md#if-002)

**Purpose:** 查询侧唯一读取入口——把增量存储的步骤行重放组装为每步完整请求的时间线；增量协议不出此接口、不对前端暴露（REQ-CT-B04）。

**Signature:**
```java
// com.fastrag.module.agent.trace
public interface TraceTimelineAssembler {
    /** 按 step_seq 升序返回解析后的步骤；traceId 不存在或已被清理返回空列表 */
    List<ResolvedStep> assemble(String traceId);
}
```
