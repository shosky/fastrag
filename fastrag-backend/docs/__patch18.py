# -*- coding: utf-8 -*-
"""一次性补丁：修订 #18 文档同步。执行后删除本文件。"""

# ---- spec ----
p = 'spec/nav-shell-frontend.md'
t = open(p, encoding='utf-8').read()

old = '管理段四处运营页聚拢为「数据分析」（修订 #14 收进下拉后，#15 改回**侧边栏第四段**）；管理段 12 项收进右上角用户下拉「管理」分组（修订 #15，见「元素与交互」）；'
new = '管理段四处运营页聚拢为「数据分析」（修订 #14 收进下拉后，#15 改回**侧边栏第四段**）；管理段 12 项收进右上角用户下拉「管理」分组（修订 #15，见「元素与交互」）；修订 #18：知识库分类收敛进知识库页左侧分类面板（首个页面级例外，限知识库列表页，见「元素与交互」）；'
assert old in t, 'spec: 范围行'
t = t.replace(old, new)

old = '''  - 资源：知识库分类 — `/knowledge/categories` — `menu:knowledge:categories`'''
new = '''  - 资源：知识库分类 — **修订 #18：自侧边栏移除，收敛进知识库页左侧分类面板**（路由与权限树保留）'''
assert old in t, 'spec: 资源段表行'
t = t.replace(old, new)

old = '- 不出现的菜单项：「业务流」「测试案例」「应用运行」（入口已收敛会话中心）、「运营中心」分组名。'
new = '''- 不出现的菜单项：「业务流」「测试案例」「应用运行」（入口已收敛会话中心）、「运营中心」分组名、「知识库分类」（修订 #18：收敛进知识库页左侧分类面板）。

**知识库列表页分类面板（修订 #18，首个页面级例外）**

- CategoryPanel 组件（`views/knowledge/components/CategoryPanel.vue`） — 位于知识库列表页左侧；结构：面板头（「分类」标题 + 新增/收起操作）→ 分类名搜索（独立于知识库搜索词）→ 分类列表（全部/未分类恒显 + 各分类，计数为前端口径）。
- 分类选择 — 点击分类行 emit 过滤值（再点取消）；「未分类」以 `__uncategorized__` 哨兵传递，父页按「无分类字段或分类不在清单」过滤。
- 行内管理（ADP「…」式） — 分类行悬停显示「…」，点击弹出操作菜单（编辑/删除），逐项受 `kb:category:edit`/`kb:category:delete` 门禁；新增受 `kb:category:create` 门禁；对话框字段：名称/描述/颜色/排序/所属组织（仅超管可选组织，自 categories.vue 迁入）。
- 折叠 — 面板头「收起」切为 40px 窄条（竖排「分类」字样 + 展开按钮）。
- 原 `/knowledge/categories` 路由与页面保留但无菜单入口（待清理项）；权限树「知识库分类」页面节点与键冻结不变。'''
assert old in t, 'spec: 不出现项行'
t = t.replace(old, new)

old = '- [ ] 「提示词」出现在资源段，点击到达 `/application/prompt-templates`'
new = '''- [ ] 「提示词」出现在资源段，点击到达 `/application/prompt-templates`
- [ ] 「知识库分类」不再出现在侧边栏；知识库列表页左侧分类面板渲染全部/未分类/各分类（前端计数），点击分类过滤右侧列表，「未分类」按无分类字段口径过滤（修订 #18）
- [ ] 分类面板：搜索只过滤分类列表；折叠为窄条可展开；分类行悬停「…」弹出编辑/删除菜单（超管/有权限者），新增/编辑对话框含名称/描述/颜色/排序/所属组织；CRUD 后列表刷新
- [ ] `/knowledge/categories` 路由仍可达（兼容），但侧边栏无入口；角色权限配置页「知识库分类」页面节点不变'''
assert old in t, 'spec: 提示词验收项'
t = t.replace(old, new)

old = '- 管理分组级联子菜单 → CONTEXT.md 决策 #17（实现期六次迭代，ADP「企业管理」式对标）。'
new = '''- 管理分组级联子菜单 → CONTEXT.md 决策 #17（实现期六次迭代，ADP「企业管理」式对标）。
- 知识库分类收敛进知识库页（CategoryPanel） → CONTEXT.md 决策 #18（实现期七次迭代，ADP 知识库页对标；修订 #10 的首个页面级例外）。'''
assert old in t, 'spec: 追溯 #17 行'
t = t.replace(old, new)

open(p, 'w', encoding='utf-8', newline='\n').write(t)
print('spec updated')

# ---- ui.md ----
p = 'ui/ui.md'
t = open(p, encoding='utf-8').read()
old = '- 菜单项 ×15（侧边栏）— 源：`nav-segments.ts` + 权限过滤；显示：有权限（STATE-102）；交互：router-link 跳转，当前项高亮；成员/路径/权限依据以 SPEC-NAV-SHELL-FRONTEND「元素与交互」清单为权威（修订 #15：对话 1 + 构建 1 + 资源 9 + 数据分析 4，管理域入下拉）。'
new = '''- 菜单项 ×14（侧边栏）— 源：`nav-segments.ts` + 权限过滤；显示：有权限（STATE-102）；交互：router-link 跳转，当前项高亮；成员/路径/权限依据以 SPEC-NAV-SHELL-FRONTEND「元素与交互」清单为权威（修订 #15/#18：对话 1 + 构建 1 + 资源 8 + 数据分析 4，管理域入下拉、知识库分类入知识库页）。
- 知识库列表页分类面板（修订 #18） — 源：CategoryPanel 组件（`views/knowledge/components/CategoryPanel.vue`）；结构：全部/未分类/各分类（前端计数徽标）+ 分类名搜索 + 面板折叠（40px 窄条）+ 行「…」操作菜单（编辑/删除，`kb:category:*` 门禁）+ 新增/编辑对话框（含所属组织[超管]）；分类选择经 `update:modelValue` 驱动父页过滤，「未分类」以 `__uncategorized__` 哨兵口径过滤。'''
assert old in t, 'ui.md: 菜单行'
t = t.replace(old, new)
open(p, 'w', encoding='utf-8', newline='\n').write(t)
print('ui.md updated')

# ---- tdd ----
p = 'tdd/nav-shell-frontend.md'
t = open(p, encoding='utf-8').read()
old = '- T2.1 数据完整性：`NavConfig` 恰含顶置主页 + 四段（顺序 对话/构建/资源/数据分析）；侧边栏 15 项 + 下拉管理 12 项；每项 `path` 均存在于路由表（结构化对账，不数数）'
new = '''- T2.1 数据完整性：`NavConfig` 恰含顶置主页 + 四段（顺序 对话/构建/资源/数据分析）；侧边栏 14 项 + 下拉管理 12 项；每项 `path` 均存在于路由表（结构化对账，不数数）
- T2.8 知识库分类面板（修订 #18）：CategoryPanel 树渲染与前端计数口径（全部/未分类/各分类）、搜索过滤、选择 emit（含 `__uncategorized__` 哨兵与取消语义）、折叠窄条、行「…」操作菜单权限显隐与编辑分流、新增/删除 CRUD（`__patch__` 见 CategoryPanel.test.ts）'''
assert old in t, 'tdd: T2.1 行'
t = t.replace(old, new)
open(p, 'w', encoding='utf-8', newline='\n').write(t)
print('tdd updated')

# ---- module doc ----
p = 'modules/nav-shell-frontend.md'
t = open(p, encoding='utf-8').read()

old = '  userMenuGroups: NavSubgroup[]       // 下拉语义分组（修订 #16/#17：门户资源[恒显平铺] + 管理[级联子菜单，权限门禁 12 项]）'
new = '''  userMenuGroups: NavSubgroup[]       // 下拉语义分组（修订 #16/#17：门户资源[恒显平铺] + 管理[级联子菜单，权限门禁 12 项]）
  // 修订 #18：侧边栏资源段 8 项（知识库分类移除，收敛进知识库页 CategoryPanel 面板）'''
assert old in t, 'module: NavConfig 行'
t = t.replace(old, new)

old = "- 修订 #17（同日六次迭代）：下拉「管理」分组改级联子菜单（NavSubgroup.cascade 标记；自绘绝对定位子面板——行悬停展开、离行 150ms 收回、移入面板取消，不用嵌套 el-popover 受控 popper 的 jsdom 不可测实现）；门户资源组保持平铺。"
new = old + '\n- 修订 #18（同日七次迭代）：知识库分类收敛进知识库列表页左侧 CategoryPanel 面板（全部/未分类/分类树 + 搜索 + 折叠 + 行「…」操作菜单 + CRUD 对话框，自 categories.vue 迁入）；侧边栏资源段 9→8 项；`/knowledge/categories` 路由保留无入口；修订 #10「壳为界」的首个页面级例外（限知识库列表页）。'
assert old in t, 'module: 追溯 #17 行'
t = t.replace(old, new)

old = '''- path: ../fastrag-frontend/src/assets/styles/variables.scss
  description: 设计令牌增补 sidebar-width（208px，其余复用现有 token）'''
new = '''- path: ../fastrag-frontend/src/views/knowledge/components/CategoryPanel.vue
  description: 知识库列表页左侧分类面板（修订 #18）——全部/未分类/分类树与前端计数、搜索、折叠窄条、行「…」操作菜单与 CRUD 对话框（自 categories.vue 迁入）
- path: ../fastrag-frontend/src/views/knowledge/components/CategoryPanel.test.ts
  description: 面板树渲染与计数口径、搜索过滤、选择/取消 emit、折叠切换、操作权限显隐、新增/删除 CRUD 流
- path: ../fastrag-frontend/src/assets/styles/variables.scss
  description: 设计令牌增补 sidebar-width（208px，其余复用现有 token）'''
assert old in t, 'module: variables 代码行'
t = t.replace(old, new)

open(p, 'w', encoding='utf-8', newline='\n').write(t)
print('module doc updated')
