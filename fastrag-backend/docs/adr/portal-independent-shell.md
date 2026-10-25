---
name: 门户独立布局壳与平台壳并存
summary: 门户以独立顶层布局壳承载 /portal 路由族，与 AppLayout 平台壳平级并存，平台整体作为门户的一个菜单进入。
type: adr
id: ADR-PORTAL-INDEPENDENT-SHELL
status: active
updated: 2026-10-24
hash: "392eb4bb8565"
summaryHash: "392eb4bb8565"
---

# 门户独立布局壳与平台壳并存

**Date:** 2026-10-24
**Status:** Accepted

## 背景 {#context}

首页门户特性要求登录后落地一个全宽门户（顶部主导航：首页 / 进入平台 / 操作手册 / 产品动态），现有平台（AppLayout：左侧边栏 + 卡片式内容区，60 条路由）整体降为门户的一个菜单。此前前端只有 AppLayout 一个布局壳，`/` 直接 redirect /home，新门户与既有壳的关系必须先行定界。

## 决策 {#decision}

新增 PortalLayout 作为与 AppLayout 平级的第二布局壳，承载顶层 `/portal` 路由族；`/` 改为 redirect `/portal`；平台路由子树零改动，唯一回门户入口是平台侧边栏 logo。

## 替代方案 {#alternatives-considered}

**将门户首页与手册/动态作为 AppLayout 侧边栏的菜单组嵌入平台壳** — 否决：门户需要全宽 hero 与顶部主导航的门户视觉，AppLayout 的侧边栏 + 卡片内容区与之冲突；且「平台整体作为一个菜单」的定位会被拆散成多个平台内菜单项，与 grill 决策 1 相悖。

## 后果 {#consequences}

路由顶层从此存在两棵互不嵌套的子树，新成员读代码会问「为什么 `/` 不直接进平台」——本 ADR 即答案。约束：新增门户页面必须挂 `/portal` 族并复用 PortalLayout；平台内部禁止反向嵌入门户元素；入口流（登录成功、404 兜底）指向 `/portal` 是本决策的直接推论。

门户页面的**可见性**（游客可浏览，仅「进入平台」要求登录）是同日另行确定的独立决策（CONTEXT.md 决策快照 9，2026-10-24 修订），不属本 ADR「一决策」的范围；本 ADR 只约束布局壳的拓扑。
