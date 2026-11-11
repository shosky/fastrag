---
name: Nav Perm Tree Decoupling
summary: 平台菜单四段重组时冻结权限体系——导航展示树与权限树解耦，menu:* 键与 sys_permission 存量零迁移，菜单分组与权限分组从此不对齐。
type: adr
id: ADR-NAV-PERM-TREE-DECOUPLING
status: draft
updated: 2026-10-26
hash: "5558b53ad728"
summaryHash: "5558b53ad728"
---

## 背景 {#context}

平台导航壳改造（SPEC-NAV-SHELL-FRONTEND）把菜单重组为对话/构建/资源/管理四段后，菜单分组与权限分组不再一一对应：模型管理归**资源**段，但其权限键 `menu:admin:platform:model` 属管理域；知识库分析归**管理**段「数据分析」，但其键 `menu:operation:kb-analytics` 沿用知识库域前缀。

权限侧有三层强绑定（`menu-perm-tree.ts` 头注自述）：`MENU_PERMISSION_MAP` 以 path→键驱动菜单可见性；`MENU_PERM_TREE` 以菜单页面为单位驱动角色权限配置页的级联勾选；后端 `sys_permission` 存有全部键的记录与存量角色的已授权数据。三层的键集一致是存量角色授权不丢的前提。

## 决策 {#decision}

导航展示树与权限树解耦：菜单归属在 `nav-segments.ts` 自由重排；`menu:*` 权限键集、`MENU_PERMISSION_MAP` 键集、`MENU_PERM_TREE` 页面节点 `keys/actions/apis`、后端 `sys_permission` 记录**全部冻结零迁移**。权限过滤改为「按 path 查键决定渲染」的分组无关实现；`MENU_PERM_TREE` 仅分组节点标题随四段重排（纯展示层）。菜单分组名与权限键字面从此允许不对齐。

## 后果 {#consequences}

- 零数据迁移、零后端改动；存量角色授权与菜单可见性判定（`menu:*` 键校验）完全不变。
- 后续菜单再重排不再触碰权限体系——重排成本降为改一个前端常量数组。
- 菜单分组与权限分组永久不对齐：`menu:admin:platform:model` 永远挂在资源段菜单上；新人凭菜单位置推断权限键域会出错，需读本 ADR。
- 权限管理页分组标题与导航一致、键前缀与导航不一致，按域筛选权限键时不能以菜单分组为依据。

被否决方案：**重排权限键并迁移 `sys_permission` 存量**——需要数据迁移脚本、全角色授权回归与权限配置页快照比对，风险与收益不成比例，且以后每次调布局都要重付；**维持旧五段菜单**——放弃本次布局改造目标（ADP 对标的动线四段）。
