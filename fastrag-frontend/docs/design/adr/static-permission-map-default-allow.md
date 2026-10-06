# ADR: 静态权限映射 + 未登记默认放行的守卫架构

**Date:** 2026-09-21
**Status:** Accepted（沿用现状代码行为，本 ADR 记录其架构理由；「默认放行是否为最终设计」待用户裁决 → spec INFERRED-1）

## Context

前端需要在不依赖每次请求后端的前提下做路由级访问控制；权限数据有「菜单→页面操作→API」三层结构，且存在后端权限树加载失败的场景。

## Decision

路由守卫以**前端静态映射表** `MENU_PERMISSION_MAP`（types/auth.ts:481-560）为唯一判权数据源，纯本地查表：命中且无权限 → `/403`；**未登记路径默认放行**（router/index.ts:92-93）。权限管理页以 `MENU_PERM_TREE + ACTION_API_LINKS`（config/menu-perm-tree.ts）为渲染真源，后端权限树加载失败时回退前端全量树、不阻塞编辑。

## Alternatives Considered

**默认拒绝（未登记 → 403）** — 被拒绝：新增页面漏登记即不可用，开发期摩擦大；与现状代码行为相反，翻转成本高。
**每次导航向后端鉴权** — 被拒绝：守卫变同步请求串行点，弱网下导航体验劣化；后端 @PreAuthorize 才是安全边界，前端查表只做体验层引导。

## Consequences

权限变更需重新登录（或刷新用户信息）才在前端生效；前端判权可被绕过（安全完全依赖后端 @PreAuthorize，这与后端「粗粒度键」现状叠加成已记录的风险，见根仓库 iam spec 观测-1）。新增受控页面必须同步登记 MENU_PERMISSION_MAP，该纪律写入 conventions。
