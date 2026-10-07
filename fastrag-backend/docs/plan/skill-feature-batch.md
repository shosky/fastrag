# 技能特性批次设计裁决 — @提及 / 脚本执行 / 导入两步化（2026-10-07）

> 三个登记在案的延后特性随本批次实施（含前端）。本文档为 grill-lite 裁决记录（沿用 2026-10-06 技能域裁决口径），实施前定生死、实施后补 CONTEXT.md 词条。

## F1 @提及技能（Skill Pinning）

- **语义**：用户在输入框 @ 选择技能 → 随请求提交 `skillNames` → 该轮对话对所选技能施加 `<must_use>` 强提示（「本轮应优先读取技能 SKILL.md 并遵循其指引」）。**pinned 不收窄白名单、不新增能力**（对齐 WeKnora：pinned 只加强提示；技能仍须在挂载/可见集合内，集外 pinned 静默忽略并 warn）。
- **后端**：`ConversationRequestState` 增 `skillNames: List<String>`（去重、上限 10，与既有 kbIds 同款契约校验）；会话直聊与应用双入口解析后写入 `runtimeState.pinnedSkillNames`；SkillsMiddleware 在 prompt 注入阶段（单 run 一次，复用 INJECTED_FLAG 模式）追加 must_use 块；请求态持久化（恢复输入框联动）。
- **前端**：聊天输入框 `@` 触发技能下拉（数据源 `GET /api/skills/accessible`），选中项以标签呈现、随消息进 `skillNames`；重开会话按请求态回显。
- **不做**：must_use 不落历史消息（随请求态可恢复）；跨会话技能推荐（无检索选择，双方均无）。

## F2 技能附属文件沙箱执行（轻量全目录物化）

- **语义**：syncSkills 从单文件升级为**全目录物化**——磁盘目录 `{data-dir}/skills/{slug}/` 复制进沙箱 `/workspace/skills/{slug}/`；SKILL.md 仍以 DB content 渲染（正文事实源裁决不变），附属文件（scripts/references 等）以磁盘为源。
- **限额**（对齐 WeKnora staging）：排除 `.git/node_modules/venv/__pycache__/` 与隐藏文件；单文件 ≤2MB、总量 ≤20MB、文件数 ≤200，超限跳过并 warn。
- **执行**：模型经既有 shell 工具执行脚本；**依赖不做自动安装**（无安装 agent 裁决不变）——Deviation 登记：脚本依赖须自带安装引导（如 SKILL.md 指引 pip install），运行失败属技能质量问题。
- **前端**：无。

## F3 技能导入两步化

- **语义**：上传与安装拆开——`POST /api/skills/import/upload`（新，仅建草稿，返回 draftId + items 预览）→ 用户确认 → `POST /api/skills/install-drafts/{draftId}/confirm`（既有端点）。`/import/prepare` 单步保留兼容并标 deprecated。
- **留痕**：confirm 的逐技能 DraftItem（success/errorMsg/metadata）即安装结果明细，前端完整展示；不再另建 transcript 表。
- **不做**：安装进度 SSE（当前同步安装秒级完成，异步化待技能包体积/数量增长后立项登记）；自动覆盖确认（slug 冲突时前端显式提示"覆盖重装"再确认）。
- **前端**：导入组件改两步交互（上传→预览清单（含冲突提示）→确认安装→展示逐项结果）。

## 实施切分

- BE1：F1 后端（application 请求态 + SkillsMiddleware must_use）
- BE2：F2 物化（agent sandbox syncSkills）+ F3 后端（tools 两步端点）
- FE：F1 @ 选择器 + F3 两步导入（fastrag-frontend，Vue3 + Element Plus）
