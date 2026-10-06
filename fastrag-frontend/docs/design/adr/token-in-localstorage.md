# ADR: 令牌存储于 localStorage（非 cookie）

**Date:** 2026-09-21
**Status:** Accepted（沿用现状；安全性取舍与刷新令牌取舍联动，后端侧为已记录 GAP）

## Context

登录令牌需要在页面刷新后存活；应用为同源 SPA，API 走 `/api` 同域代理，无跨站共享登录态需求。

## Decision

token 与 userInfo 存 localStorage（`ais_` 前缀，utils/storage.ts 封装，stores/user.ts:10-11），由 user store 作为唯一写入/清理点；401 与登出共用同一清理路径。

## Alternatives Considered

**HttpOnly Cookie** — 被拒绝：需要后端 Set-Cookie 与 CSRF 防护配套，后端现状为 Bearer 头方案（Authorization 头），改造跨入后端链范围。
**sessionStorage** — 被拒绝：新标签页丢失登录态，体验不可接受。

## Consequences

XSS 一旦得手可直接读 token（无 HttpOnly 隔离）——缓解依赖无富文本注入面 + 常规 CSP，当前无 CSP 配置 [GAP]；无刷新令牌机制，令牌过期即重新登录（后端 GAP 项联动）。
