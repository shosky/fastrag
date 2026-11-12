# -*- coding: utf-8 -*-
"""一次性补丁：修订 #17 文档同步（管理分组级联子菜单）。执行后删除本文件。"""

# ---- CONTEXT.md ----
p = '../../CONTEXT.md'
t = open(p, encoding='utf-8').read()
lines = t.split('\n')
idx = None
for i, ln in enumerate(lines):
    if ln.startswith('16. 下拉语义分组'):
        idx = i
        break
assert idx is not None, 'CONTEXT: #16 未找到'
d17 = ('17. 管理分组级联子菜单（2026-10-26 实现期六次迭代，ADP「企业管理」式对标）：用户下拉内「管理」分组'
       '渲染为一行（标题+左箭头），悬停向左弹出 170px 子面板列出管理域 12 项（自绘绝对定位面板，'
       '行悬停展开、离行 150ms 收回、移入面板取消收回，点击子项跳转并关闭）；门户资源分组保持平铺。'
       '权限键与权限树零改动。')
lines.insert(idx + 1, d17)
open(p, 'w', encoding='utf-8', newline='\n').write('\n'.join(lines))
print('CONTEXT #17 added')

# ---- spec ----
p = 'spec/nav-shell-frontend.md'
t = open(p, encoding='utf-8').read()
old = '- 下拉「管理」分组（修订 #15/#16） — 通用设置/沙箱设置/知识库配置/敏感词设置/字典管理/术语管理/角色管理/组织管理/人员管理/权限管理/系统日志/开放密钥十二项，自管理段移入；显示：逐项按原权限键过滤，全隐时分组标签不渲染；交互：点击跳转对应路由。'
new = '''- 下拉「管理」分组（修订 #15/#17，级联子菜单） — 下拉内仅渲染一行（「管理」+ 左箭头），悬停向左弹出 170px 子面板列出通用设置/沙箱设置/知识库配置/敏感词设置/字典管理/术语管理/角色管理/组织管理/人员管理/权限管理/系统日志/开放密钥十二项；显示：子面板内逐项按原权限键过滤，全隐时不渲染该行；交互：悬停展开/移开收回，点击子项跳转并关闭。'''
assert old in t, 'spec: 管理分组行'
t = t.replace(old, new)

old = '- [ ] 用户下拉语义分组顺序：头部 → 用户信息 → 门户资源 → 管理 → 退出系统恒在最底（分隔线之下）'
new = '''- [ ] 用户下拉语义分组顺序：头部 → 用户信息 → 门户资源 → 管理（级联行）→ 退出系统恒在最底（分隔线之下）
- [ ] 悬停「管理」级联行向左弹出子面板（12 项管理功能），移开或点击子项后收回；主下拉体内不平铺管理功能项（修订 #17）'''
assert old in t, 'spec: 语义分组验收项'
t = t.replace(old, new)

old = '- 下拉语义分组与折叠悬停浮出 → CONTEXT.md 决策 #16（实现期五次迭代）。'
new = '''- 下拉语义分组与折叠悬停浮出 → CONTEXT.md 决策 #16（实现期五次迭代）。
- 管理分组级联子菜单 → CONTEXT.md 决策 #17（实现期六次迭代，ADP「企业管理」式对标）。'''
assert old in t, 'spec: 追溯 #16 行'
t = t.replace(old, new)
open(p, 'w', encoding='utf-8', newline='\n').write(t)
print('spec updated')

# ---- ui.md ----
p = 'ui/ui.md'
t = open(p, encoding='utf-8').read()
old = '- 下拉「管理」分组（修订 #15/#16） — 源：`nav-segments.ts` `userMenuGroups[1]`；显示：位于下拉内、逐项按原权限键过滤，全隐时标签不渲染；交互：点击跳转对应路由；侧边栏内不出现管理域菜单。'
new = '''- 下拉「管理」级联行（修订 #17） — 源：`nav-segments.ts` `userMenuGroups[1]`（cascade=true）；显示：下拉内一行（「管理」+ 左箭头），子面板项逐项按原权限键过滤；交互：悬停向左弹出 170px 子面板（自绘绝对定位，行展开/离行 150ms 收回/移入面板取消），点击子项跳转并关闭；侧边栏内不出现管理域菜单。'''
assert old in t, 'ui.md: 管理行'
t = t.replace(old, new)
open(p, 'w', encoding='utf-8', newline='\n').write(t)
print('ui.md updated')

# ---- tdd ----
p = 'tdd/nav-shell-frontend.md'
t = open(p, encoding='utf-8').read()
old = '- T5.1 用户入口（UserMenu，右上角）：下拉头部姓名+@用户名、用户信息单项、门户资源/管理语义分组（修订 #16：退出系统断言为最后一项；受限账号管理组仅剩恒显项沙箱设置、门户资源恒显）'
new = '''- T5.1 用户入口（UserMenu，右上角）：下拉头部姓名+@用户名、用户信息单项、门户资源平铺分组、管理级联行（修订 #17：主下拉体不平铺管理功能项、cascade 行渲染、受控子面板挂载/卸载；受限账号管理组仅剩恒显项沙箱设置、门户资源恒显）'''
assert old in t, 'tdd: T5.1 行'
t = t.replace(old, new)
open(p, 'w', encoding='utf-8', newline='\n').write(t)
print('tdd updated')

# ---- module doc ----
p = 'modules/nav-shell-frontend.md'
t = open(p, encoding='utf-8').read()

old = '''/** 用户下拉内的权限门禁子分组（如「管理」，非交互标签） */
export interface NavSubgroup {
  title: string
  items: NavItem[]
}'''
new = '''/** 用户下拉内的子分组（cascade=true 时渲染为级联子菜单行，修订 #17） */
export interface NavSubgroup {
  title: string
  items: NavItem[]
  cascade?: boolean
}'''
assert old in t, 'module: NavSubgroup 类型'
t = t.replace(old, new)

old = '  userMenuGroups: NavSubgroup[]       // 下拉语义分组（修订 #16：门户资源[恒显] + 管理[权限门禁 12 项]）'
new = '  userMenuGroups: NavSubgroup[]       // 下拉语义分组（修订 #16/#17：门户资源[恒显平铺] + 管理[级联子菜单，权限门禁 12 项]）'
assert old in t, 'module: NavConfig 行'
t = t.replace(old, new)

old = '''├──────────────┤
│ 管理          │ ← 权限门禁分组（修订 #15，全隐时整组不渲染）
│  通用设置     │ → /admin/system/general-settings
│  沙箱设置 …   │ → /admin/system/sandbox-settings 等
│  开放密钥     │ → /admin/platform/api-keys
├──────────────┤
│ ⏻ 退出系统   │ → 确认框 → 清会话 → /login（恒在最底）
└──────────────┘
```'''
new = '''├──────────────┤
│ 管理       ‹  │ ← 级联行（修订 #17）：悬停向左弹出子面板
├──────────────┤
│ ⏻ 退出系统   │ → 确认框 → 清会话 → /login（恒在最底）
└──────────────┘
  管理 ‹ 悬停弹出子面板（左，170px）：
  ┌────────────┐
  │ 管理        │
  │  通用设置   │ → /admin/system/general-settings
  │  沙箱设置 … │ → 逐项权限过滤（全隐整组不渲染）
  │  开放密钥   │ → /admin/platform/api-keys
  └────────────┘
```'''
assert old in t, 'module: 下拉线框'
t = t.replace(old, new)

old = '- 修订 #16（同日五次迭代）：用户下拉语义分组（头部 → 用户信息 → 门户资源 → 管理 → 底部独立退出，NavConfig 拆为 userMenuProfile/userMenuGroups/userMenuLogout 三字段）；折叠态悬停浮出完整菜单（.nav-flyout 覆盖内容区，替代图标 tooltip 方案），Sidebar 容器 mouseenter/mouseleave 驱动。'
new = old + '\n- 修订 #17（同日六次迭代）：下拉「管理」分组改级联子菜单（NavSubgroup.cascade 标记；自绘绝对定位子面板——行悬停展开、离行 150ms 收回、移入面板取消，不用嵌套 el-popover 受控 popper 的 jsdom 不可测实现）；门户资源组保持平铺。'
assert old in t, 'module: 追溯 #16 行'
t = t.replace(old, new)

open(p, 'w', encoding='utf-8', newline='\n').write(t)
print('module doc updated')
