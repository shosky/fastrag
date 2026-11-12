# -*- coding: utf-8 -*-
"""一次性补丁：修订 #16 文档同步（下拉语义分组 + 折叠悬停浮出）。执行后删除本文件。"""

# ---- CONTEXT.md ----
p = '../../CONTEXT.md'
t = open(p, encoding='utf-8').read()
lines = t.split('\n')
idx = None
for i, ln in enumerate(lines):
    if ln.startswith('15. 管理域下沉'):
        idx = i
        break
assert idx is not None, 'CONTEXT: #15 未找到'
d16 = ('16. 下拉语义分组与折叠悬停浮出（2026-10-26 实现期五次迭代）：用户下拉结构定为 '
       '头部（姓名+@用户名）→ 用户信息 → 「门户资源」分组（操作手册/产品动态，无权限键恒显）→ '
       '「管理」分组（12 项权限门禁）→ 底部独立退出系统（danger，恒在最底）；'
       '折叠态交互为**悬停浮出**——鼠标悬停图标栏时在右侧浮出 208px 完整菜单（flyout 覆盖内容区，'
       '鼠标离开或点击菜单项即收回），替代图标 tooltip 方案。权限键与权限树零改动。')
lines.insert(idx + 1, d16)
open(p, 'w', encoding='utf-8', newline='\n').write('\n'.join(lines))
print('CONTEXT #16 added')

# ---- spec ----
p = 'spec/nav-shell-frontend.md'
t = open(p, encoding='utf-8').read()

old = '''**用户入口下拉（PAGE-101 内容卡头右上角，铃铛右侧；2026-10-26 修订 #14，同日三次修订修正位置）**

- 用户头像 chip（头像 + 姓名） — 来源：userStore（`realName || username` 回退）；显示：登录恒显，位于内容卡头右上角（铃铛右侧，窗口右上角）；交互：点击弹出下拉。
- 下拉头部 — 来源：userStore；显示：姓名 + 用户名；交互：无。
- 下拉固定四项 — 用户信息（打开既有弹窗，六字段与懒加载组织名逻辑不变）、操作手册（跳 `/portal/manual`）、产品动态（跳 `/portal/changelog`）、退出系统（确认后清会话跳 `/login`，取消停留）。
- 下拉「管理」分组（修订 #15） — 通用设置/沙箱设置/知识库配置/敏感词设置/字典管理/术语管理/角色管理/组织管理/人员管理/权限管理/系统日志/开放密钥十二项，自管理段移入；显示：逐项按原权限键过滤，全隐时分组标签不渲染；交互：点击跳转对应路由。'''
new = '''**用户入口下拉（PAGE-101 内容卡头右上角，铃铛右侧；修订 #14/#16 语义分组）**

- 用户头像 chip（头像） — 来源：userStore（`realName || username` 回退）；显示：登录恒显，位于内容卡头右上角（铃铛右侧，窗口右上角）；交互：点击弹出下拉。
- 下拉头部 — 来源：userStore；显示：姓名 + 用户名；交互：无（非交互）。
- 用户信息 — 头部正下方单项；交互：打开既有弹窗（六字段与懒加载组织名逻辑不变）。
- 下拉「门户资源」分组（修订 #16） — 操作手册（跳 `/portal/manual`）、产品动态（跳 `/portal/changelog`）两项，无权限键恒显；交互：点击跳转。
- 下拉「管理」分组（修订 #15/#16） — 通用设置/沙箱设置/知识库配置/敏感词设置/字典管理/术语管理/角色管理/组织管理/人员管理/权限管理/系统日志/开放密钥十二项，自管理段移入；显示：逐项按原权限键过滤，全隐时分组标签不渲染；交互：点击跳转对应路由。
- 退出系统 — 恒在下拉最底部（分隔线之下，danger 样式）；交互：确认后清会话跳 `/login`，取消停留。'''
assert old in t, 'spec: 下拉块不匹配'
t = t.replace(old, new)

old = '- 折叠开关（修订 #15） — 来源：静态；显示：侧边栏底部恒显；交互：点击切换图标态（64px，段标题与菜单文字隐藏、菜单项仅图标 + 原生 title 提示），状态持久化 localStorage。'
new = '''- 折叠开关（修订 #15） — 来源：静态；显示：侧边栏底部恒显；交互：点击切换图标态（64px，段标题与菜单文字隐藏、菜单项仅图标），状态持久化 localStorage。
- 折叠态悬停浮出（修订 #16） — 触发：折叠态下鼠标悬停侧边栏；可见反馈：右侧浮出 208px 完整菜单（覆盖内容区、带投影，含段标题与文字菜单项）；收回：鼠标离开或点击任一菜单项（点击同时完成跳转）。展开态悬停无浮出层。'''
assert old in t, 'spec: 折叠开关行不匹配'
t = t.replace(old, new)

old = '- [ ] 折叠开关：点击后侧边栏进入图标态（约 64px，段标题/菜单文字隐藏、菜单项 title 提示），再点恢复；刷新后保持上次折叠状态（localStorage）'
new = '''- [ ] 折叠开关：点击后侧边栏进入图标态（约 64px，段标题/菜单文字隐藏），再点恢复；刷新后保持上次折叠状态（localStorage）
- [ ] 折叠态悬停侧边栏浮出完整菜单（含段标题与文字），鼠标离开或点击菜单项后收回；展开态悬停无浮出层（修订 #16）
- [ ] 用户下拉语义分组顺序：头部 → 用户信息 → 门户资源 → 管理 → 退出系统恒在最底（分隔线之下）'''
assert old in t, 'spec: 折叠验收项不匹配'
t = t.replace(old, new)

old = '- 用户入口右上角（铃铛右侧）与「数据分析」收进下拉 → CONTEXT.md 决策 #14（实现期修订，ADP 截图 2 对标；同日三次修订修正位置）。'
new = '''- 用户入口右上角（铃铛右侧）与「数据分析」收进下拉 → CONTEXT.md 决策 #14（实现期修订，ADP 截图 2 对标；同日三次修订修正位置）。
- 下拉语义分组与折叠悬停浮出 → CONTEXT.md 决策 #16（实现期五次迭代）。'''
assert old in t, 'spec: 追溯行不匹配'
t = t.replace(old, new)

open(p, 'w', encoding='utf-8', newline='\n').write(t)
print('spec updated')

# ---- ui.md ----
p = 'ui/ui.md'
t = open(p, encoding='utf-8').read()

old = '''- 下拉固定四项 — 用户信息（既有六字段弹窗，组织名懒加载不变）/ 操作手册（跳 `/portal/manual`）/ 产品动态（跳 `/portal/changelog`）/ 退出系统（确认后清会话跳 `/login`，取消停留）。
- 下拉「管理」分组（修订 #15） — 源：`nav-segments.ts` `userMenuGroups`；显示：位于下拉内、逐项按原权限键过滤，全隐时标签不渲染；交互：点击跳转对应路由；侧边栏内不出现管理域菜单。'''
new = '''- 下拉用户信息 — 头部正下方单项（既有六字段弹窗，组织名懒加载不变）。
- 下拉「门户资源」分组（修订 #16） — 源：`nav-segments.ts` `userMenuGroups[0]`；操作手册（跳 `/portal/manual`）/ 产品动态（跳 `/portal/changelog`），无权限键恒显。
- 下拉「管理」分组（修订 #15/#16） — 源：`nav-segments.ts` `userMenuGroups[1]`；显示：位于下拉内、逐项按原权限键过滤，全隐时标签不渲染；交互：点击跳转对应路由；侧边栏内不出现管理域菜单。
- 下拉退出系统 — 恒在最底（分隔线之下，danger）；交互：确认后清会话跳 `/login`。
- 折叠态悬停浮出（修订 #16） — 触发：折叠态鼠标悬停侧边栏；可见反馈：右侧 208px 完整菜单浮层（含段标题与文字项）；收回：鼠标离开或点击菜单项。'''
assert old in t, 'ui.md: 下拉块不匹配'
t = t.replace(old, new)

open(p, 'w', encoding='utf-8', newline='\n').write(t)
print('ui.md updated')

# ---- tdd ----
p = 'tdd/nav-shell-frontend.md'
t = open(p, encoding='utf-8').read()

old = '- T5.1 用户入口（UserMenu，右上角）：下拉头部姓名+@用户名、固定四项、管理分组 12 项（受控渲染 + 分组权限过滤；受限账号仅剩恒显项沙箱设置）'
new = '''- T5.1 用户入口（UserMenu，右上角）：下拉头部姓名+@用户名、用户信息单项、门户资源/管理语义分组（修订 #16：退出系统断言为最后一项；受限账号管理组仅剩恒显项沙箱设置、门户资源恒显）
- T5.6c 折叠态悬停浮出（修订 #16）：折叠 + mouseenter → 浮出层含完整菜单文字；mouseleave/点击菜单项收回；展开态悬停无浮出层'''
assert old in t, 'tdd: T5.1 行不匹配'
t = t.replace(old, new)

open(p, 'w', encoding='utf-8', newline='\n').write(t)
print('tdd updated')
