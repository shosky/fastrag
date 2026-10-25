---
name: Portal Frontend
summary: 首页门户层前端——PortalLayout 顶部导航壳承载门户首页/操作手册/产品动态三页，菜单数组驱动，内容全部构建期静态化，与平台壳平级并存。
type: module
id: MOD-PORTAL-FRONTEND
profile: frontend
status: draft
updated: 2026-10-24
satisfies: [SPEC-PORTAL-FRONTEND]
decisions: [ADR-PORTAL-INDEPENDENT-SHELL]
hash: "77549e4e3137"
summaryHash: "77549e4e3137"
---

# Portal Frontend — 首页门户前端

## 职责 {#responsibility}

门户层前端：PortalLayout 顶部导航壳 + 门户首页/操作手册/产品动态三页，把现有平台收纳为单个菜单入口；全部内容静态化，零后端接口。

## 契约 {#contract}

**属性：**

```ts
// PortalLayout 为路由驱动布局，不接收任何 props
interface PortalLayoutProps {}
```

**事件：** 无对外事件——页面跳转由 router-link 完成，退出登录只消费现有 userStore。

**插槽：** `default` — `<router-view />`，承载门户三页（见「页面」节）。

**类型：**

```ts
// 顶导航菜单单一事实源（src/constants/portalMenus.ts）
interface PortalMenuItem {
  key: string;    // 菜单标识：'home' | 'platform' | 'manual' | 'changelog'
  title: string;  // 显示文字：首页 / 进入平台 / 操作手册 / 产品动态
  path: string;   // 跳转路由：/portal / /home / /portal/manual / /portal/changelog
}
```

**不变量：**

- 顶导航菜单渲染完全由 `PortalMenuItem[]` 驱动，新增菜单只改数组不改布局组件。
- 门户三页零后端接口；任何数据必须来自构建期打包内容或现有 userStore。
- 门户三页公开访问（requiresAuth: false，游客可浏览，顶导航游客态显示「登录」）；「进入平台」由现有守卫要求登录。
- 门户路由族与 AppLayout 平台壳互不嵌套（ADR-PORTAL-INDEPENDENT-SHELL）；门户路由不注册 MENU_PERMISSION_MAP 与 menu-perm-tree.ts。

## 接口 {#interface}

无
<!-- 普通接口：每个接口一个 `###` 单元，写 `Purpose:` 与 `Signature:`；没有则保留「无」。 -->

## 页面 {#page}

- **PAGE-001 首页门户** — 路由 `/portal`（`/` 的 redirect 目标），公开访问。入口：登录成功、已登录访问 /login（守卫）、404 兜底、平台侧边栏 logo、顶导航「首页」、游客直访。退出：顶导航其余菜单。
- **PAGE-002 操作手册** — 路由 `/portal/manual`，公开访问。入口：顶导航、首页资源卡、hero 次按钮。返回：顶导航「首页」或 logo。
- **PAGE-003 产品动态** — 路由 `/portal/changelog`，公开访问。入口：顶导航、首页资源卡。返回：顶导航「首页」或 logo。

## 布局 {#layout}

**PortalLayout（三页共用壳）：**

```
┌────────────────────────────────────────────────────────────────┐
│ PortalTopNav                                                   │
│ [logo FastRAG] 首页·操作手册·产品动态    [登录|进入平台] <用户名> [退出] │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│   <router-view />（门户三页之一）                                 │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

**PAGE-001 门户首页（desktop，FastGPT 官网风格：白底黑字克制用色、大字号排版、卡片内嵌迷你 UI 插画、深色 CTA/页脚收尾）：**

```
┌────────────────────────────────────────────────────────────────┐
│ PortalTopNav                                                   │
├───────────────────────────────┬────────────────────────────────┤
│ HeroCopy（白底）                │ BrowserFrame（真实会话大截图）     │
│  徽章：企业级检索增强生成平台     │  ┌ mac 圆点 + 标题栏      ┐       │
│  让企业知识 一问即答（64px 大标题）│  │ 会话中心 18 条真实对话   │       │
│  描述 + [立即开始|进入平台][手册] │  └───────────────────┘       │
├───────────────────────────────┴────────────────────────────────┤
│  核心能力（眉标+大标题） bento 网格 3 列 × 7 卡（真实界面截图）：      │
│   ┌知识库管理┐┌智能分片┐┌多种切片方式┐                              │
│   ┌知识图谱 ┐┌应用对话┐┌检索增强┐┌运营分析┐                       │
├────────────────────────────────────────────────────────────────┤
│  业务场景（灰底）：场景卡 ×4（定制矢量插画+图标+说明+示例名）          │
├────────────────────────────────────────────────────────────────┤
│  快速开始（白底）：三步卡（步骤插画 ①云上传 ②应用窗 ③对话）          │
├────────────────────────────────────────────────────────────────┤
│  为什么选 FastRAG（灰底）：卖点卡 ×4（开箱即用/私有化/可溯源/安全）    │
├────────────────────────────────────────────────────────────────┤
│  资源（白底）：操作手册卡 / 产品动态卡 + v*徽标                     │
├────────────────────────────────────────────────────────────────┤
│  常见问题（灰底窄栏）：el-collapse FAQ ×5                          │
├────────────────────────────────────────────────────────────────┤
│  CTA 深色圆角横幅：让企业知识，开始对话 + [登录|进入平台] + 点阵背景  │
├────────────────────────────────────────────────────────────────┤
│  页脚（深色）：FR FastRAG · 操作手册/产品动态/登录 · © 2026        │
└────────────────────────────────────────────────────────────────┘
```

<!-- tablet ≤1024px：hero 竖排（mock 限宽在下），bento 2 列（宽卡跨 2），三步纵向，页脚纵排 -->
<!-- mobile ≤768px：bento/资源卡单列，分区留白收紧，hero 标题 40px -->

**PAGE-002 操作手册（desktop）：**

```
┌────────────────────────────────────────────────────────────────┐
│ PortalTopNav                                                   │
├───────────────┬────────────────────────────────────────────────┤
│ ManualToc     │ 手册内容区（.manual-doc 阅读排版）                │
│ (sticky)      │                                                │
│ ┌──────────┐  │                                                │
│ │用户手册v* │←─┼─ 版本头卡（changelog 首条版本号）                 │
│ └──────────┘  │                                                │
│ ▾第一部分      │                                                │
│  第1章 主页 ◀─┼─ 滚动联动：激活章高亮 + 自动展开节列表              │
│  第2章 工作台 >│   ## 章标题（锚点 id）                           │
│  …            │   ### 节标题 · 正文 · 表格 · 代码块               │
└───────────────┴────────────────────────────────────────────────┘
```

<!-- MaxKB 文档站式：部分分组带折叠箭头、章带节展开箭头、激活章浅蓝高亮 -->
<!-- ≤1024px：目录折叠为内容区顶部的章节下拉选择器（按部分分组） -->

**PAGE-003 产品动态：**

```
┌────────────────────────────────────────────────────────────────┐
│ PortalTopNav                                                   │
├────────────────────────────────────────────────────────────────┤
│          ChangelogTimeline（el-timeline，居中窄栏）               │
│          ● v1.4.0  2026-10-20  [新功能]                          │
│          │    · 要点一  · 要点二                                 │
│          ● v1.3.0  …                                            │
└────────────────────────────────────────────────────────────────┘
```

<!-- ≤768px：时间线内边距收紧至 $spacing-md，页面容器内边距同步收紧 -->

组件映射（单行职责）：

- `components/portal/PortalLayout.vue` — 门户壳：顶导航 + `<router-view>`，模块唯一对外形态。
- `components/portal/PortalTopNav.vue` — 顶导航：菜单数组渲染与高亮、游客「立即开始」/已登录「进入平台」、用户名回退、退出确认（仅 PortalLayout 消费，模块内部组件不外泄）。
- `components/portal/ManualToc.vue` — 手册目录：版本头卡、部分分组折叠、章/节两级导航、锚点滚动与滚动联动高亮（行为深度：scroll-spy，成立为独立组件）。
- 首页配图：hero/业务场景/bento 均使用平台真实界面截图（public/portal/ 下 scene-chat/scene-knowledge/scene-chunks/scene-parse/scene-graph/crop-chatcolumn/crop-kb/crop-cite/crop-analytics/cap-retrieval 等），无虚构插画。
- `views/portal/index.vue` · `views/portal/manual.vue` · `views/portal/changelog.vue` — 三页视图；首页卡片均为一次性内联标记，不抽共享卡片组件。

## 状态 {#states}

- **STATE-001 手册渲染回退态** — 归属：client。ManualPage 组件局部 ref，renderMarkdown 异常时回退原文。
- **STATE-002 产品动态空态** — 归属：client。构建期常量 `changelog.length === 0` 判定，无服务器参与。
- 顶导航当前高亮 — 归属：URL。由 `route.path` 派生，非独立状态。
- 手册目录滚动高亮 — 归属：client。ManualToc 局部，监听滚动位置计算当前章节。

无 loading / server / form 状态——门户零异步请求；disabled / offline 不适用。

## 数据 {#data}

```
manual.md（fastrag-frontend/src/content/manual.md，Vite ?raw 打包）
  → parseManualToc(raw)                      // src/utils/manualToc.ts —— 唯一命名转换点：
                                             //    提取「## 章 / ### 节」标题树 + 锚点 id
  → { tocTree, html: renderMarkdown(raw) }   // renderMarkdown 复用 src/utils/markdown.ts 单源渲染器
  → manual.vue 内容区 + ManualToc 目录树

public/manual/*.png（真实平台界面截图，随构建打包）
  → manual.md 内嵌引用（![章 界面](/manual/chN-*.png)），marked 渲染为 img

changelog.ts（src/constants/changelog.ts，静态数组，version 倒序）
  → changelog.vue 时间线直读
  → 首页资源卡徽标 = changelog[0].version，空数组隐藏

userStore.username 计算属性（realName 缺失回退 username，store 内定义，直接消费不新建副本）
  → PortalTopNav 用户名（仅已登录显示）
```

零 API-\*：全部数据不经过任何 request 调用；无 View Model 适配层需求（唯一的结构转换是 parseManualToc）。

能力/步骤配图（public/portal/cap-*.svg、step-*.svg，同风格定制矢量插画）：
  cap-kb / cap-chat / cap-flow / cap-retrieval → bento 四卡
  step-upload / step-app / step-ask → 三步卡

首页配图（public/portal/，真实平台界面裁切特写，随构建打包）：
  crop-chatcolumn.jpg → hero 浏览器窗框 + 智能客服/应用对话卡（对话列特写：RAG 回答+引用+模型选择）
  crop-kb.png → 企业知识问答/知识库管理卡（知识库卡片网格）
  crop-analytics.png → 数据问答/运营分析卡（知识资产分析统计+排行）
  crop-cite.jpg → 检索增强卡（知识来源 14 条引用特写）

## 视觉 {#visual}

```scss
// variables.scss 增补一个 token（mock 渐变深端），其余全部复用现有 token
$color-primary-dark: #1E3A8A;

// 用色纪律（FastGPT 式克制）：白底 + 近黑文字（#09090B/#52525B），蓝仅做按钮/引用/点缀
// hero：白底 radial 蓝晕；mock 应用头用 $color-primary-dark→$color-primary 渐变小方块
// 卡片：1px #E4E4E7 边框、radius 14、静止无阴影，hover 才出阴影 + translateY(-2px)
// CTA 横幅：#09090B 深色圆角 20 + 点阵背景（radial-gradient 平铺 + mask 渐隐）
// 页脚：#09090B 深色，链接 #A1A1AA hover #FAFAFA
// 卡片内迷你 UI 插画：#FAFAFA 底 + #F0F0F1 边框的 11px 微缩界面（文件行/气泡/节点/引用 chip）
```

- 字号阶梯：hero 大标题 56px/700（≤768px 40px）；分区标题 34px/700 + 眉标 13px（灰字宽字距）；卡片标题 16px/600；正文与描述 13-15px `#52525B`。
- 间距：区块纵向 88px（≤768px 收紧 56px），内容列 max-width 1160px 居中；卡片静止无阴影、hover 出阴影 + 位移。
- type 标签：el-tag 语义色 success（新功能）/ primary（优化）/ warning（修复）/ danger（安全）。
- 断点：≤1024px hero 竖排、bento 2 列（宽卡跨 2）、三步纵向、手册目录折叠下拉；≤768px bento/资源卡单列、时间线内边距收紧至 `$spacing-md`、顶导航横向滚动（无汉堡菜单）。
- 动效清单（全 CSS/IntersectionObserver，无动画库）：
  - 入场：hero 徽章→标题→描述→按钮 rise-in 交错浮现（0.9s，延迟 0.05-0.45s）；标题「一问即答」蓝青渐变文字
  - 滚动渐显：`.reveal` + IntersectionObserver（threshold 0.15，进入视口加 is-visible），网格内以 reveal-d0/d1/d2 交错延迟
  - 常驻：mock 卡片 float-y 悬浮（7s）、打字点闪烁、hero 双极光 orb-drift 漂移（blur 70px）、CTA 点阵 dots-drift 平移 + 辉光 orb、立即开始按钮 cta-pulse 光环脉冲
  - 降级：`prefers-reduced-motion: reduce` 时全部动画/过渡/位移禁用（直接呈现最终态）

## 边界设计 {#seam}

**门户菜单集合（变化点）** — 菜单增删是未来最可能的变化。适配方式是 `portalMenus.ts` 数组常量：当前只有一个"实现"（静态配置），按 one-adapter 原则不抽象接口；稳定侧是 PortalLayout 只消费数组。

**产品动态数据源（变化点）** — 当前为构建期常量；若未来升级为后端 CRUD（运营维护），替换点收敛在 changelog.ts 的消费边界。Deepening 机会：届时把「取倒序动态列表」收敛为一个 composable，两处消费方（首页徽标、时间线）不改。

**手册内容（变化点）** — 单文件 manual.md 替换即生效；目录生成的稳定侧是「`##` 章 / `###` 节」结构约定本身。

**双壳关系（稳定）** — 门户壳与平台壳在路由顶层两棵互不嵌套的子树，是本模块最重要的稳定决策，理由与被否决方案见 ADR-PORTAL-INDEPENDENT-SHELL。

## 追溯 {#trace}

- 门户三页与两个界面状态 → SPEC-PORTAL-FRONTEND 的页面与状态全集（见该文档「页面与路由」「状态矩阵」两节）。
- 顶导航游客/登录双态 → spec 决策 9（2026-10-24 修订，公开访问）与 TASK-001~005。
- 菜单数组驱动 → spec「元素与交互」顶导航节。
- 双壳并存与平台零改动 → spec 决策 3、6、7 + ADR-PORTAL-INDEPENDENT-SHELL。

## 代码 {#code}

```yaml
- path: ../fastrag-frontend/src/router/routes.ts
  description: 门户路由族挂载点——顶层新增 /portal 族（公开访问，requiresAuth 置 false）并将 / 与 404 兜底迁至 /portal，平台 60 条路由零改动
- path: ../fastrag-frontend/src/components/portal/PortalLayout.vue
  description: 门户壳组件——顶导航 + router-view，模块唯一对外形态
- path: ../fastrag-frontend/src/components/portal/PortalTopNav.vue
  description: 顶导航——菜单数组渲染与当前路由高亮、游客「登录」/已登录用户名与退出确认
- path: ../fastrag-frontend/src/components/portal/ManualToc.vue
  description: 手册目录——标题树渲染、锚点滚动与 scroll-spy 联动高亮
- path: ../fastrag-frontend/src/views/portal/index.vue
  description: PAGE-001 门户首页——hero 横幅、能力四卡与资源卡（含版本徽标）
- path: ../fastrag-frontend/src/views/portal/manual.vue
  description: PAGE-002 操作手册——renderMarkdown 渲染 + sticky 目录 + 滚动联动，渲染异常回退原文（STATE-001）
- path: ../fastrag-frontend/src/views/portal/changelog.vue
  description: PAGE-003 产品动态——el-timeline 版本时间线、类型语义色标签与空态（STATE-002）
- path: ../fastrag-frontend/src/constants/portalMenus.ts
  description: 顶导航菜单单一事实源（PortalMenuItem 数组）
- path: ../fastrag-frontend/src/constants/changelog.ts
  description: 产品动态静态数据（version 倒序条目数组，首条驱动首页徽标）
- path: ../fastrag-frontend/src/content/manual.md
  description: 结构化手册素材（## 章 / ### 节规范 Markdown，15 章，产品名 FastRAG，由 docs/manual.md 生成）
- path: ../fastrag-frontend/src/utils/manualToc.ts
  description: parseManualToc/injectHeadingIds——从手册 Markdown 提取章/节标题树并注入一致锚点 id
```
