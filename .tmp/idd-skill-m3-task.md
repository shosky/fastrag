# M3 任务书 — 技能域接线补齐 + 死代码清理 + DDL 双处对齐（skill-runtime-remediation M3）

> 输入：docs/plan/skill-runtime-remediation.md §5-M3（裁决 2026-10-06）。前置：M1（tools 191→现 194 绿）、M2（agent 163 绿 / application 74 绿）已完成。
> 你是实现代理：按 /idd-tdd（先读 C:\Users\63179\.zcode\skills\idd-tdd\SKILL.md）实施。**禁止 git commit**。
> 模块：fastrag-tools / fastrag-bootstrap / fastrag-agent（仅删除件）/ fastrag-common（如需）。

## 背景要点（行号为既往实测，动手前重新核实）

- D2：Skill.shareConfig 字段 `exist=false` 不落库（entity/Skill.java:93）；updateShareConfig 改了不入库字段；SkillAccessManager 零生产调用且其 getCreatorId 走 SkillMapper.java:19-20 的 `SELECT created_by`（实际列 creator，吞异常静默返回 ""）。
- D9：SkillMarkdownParser 只取 name/slug/description/version/category/icon 六字段（:58-63 旧值），frontmatter 的 tool_dependencies/skill_dependencies/mcp_dependencies 不解析；saveDependencies（SkillServiceImpl:194-216）不过 SkillDependencyValidator（校验器只被 dependency-options 端点用）。builtin-skills/web-search/SKILL.md 头部已有依赖声明样例。
- D14：ToolRegistry.java:30-31 恒注入 install_skill；InstallSkillToolExecutor 为指引桩。
- D10：SkillDataInitializer 每次启动 REPLACE_EXISTING 整目录复制（:158 附近）+ DB content 更新语义；文件 API 只写磁盘。裁决：**DB content 唯一事实源；启动播种仅在 DB 无记录时执行、不覆盖已有**（此裁决取代 spec REQ-B05 的"MD5 变化更新"语义，登记 Deviation）。
- D11：confirmDraft slug 已存在时 deleteById+insert（SkillDraftServiceImpl:161-169）→ app_skill_binding 孤儿；bindSkill 不校验技能存在。
- M2 交付的衔接缺口：loadActivatedSkillDependencies 追加的依赖串形如 `tool:web_search`，而 ToolRegistry.resolveSingleTool 只识别 `mcp:`/`skill:` 前缀与裸 id——`tool:` 前缀解析不到，须在本期规范化。
- M1 遗留：prepareUpload 非法文件名被拒时，已创建的空草稿目录残留（cleanupExpiredDrafts 不清理无 metadata.json 的目录）。
- M2 交付：SkillsMiddleware 缓存实体于 context.runtimeSkillEntities；AgentEngine.recomputeToolsIfDirty（:683-702）。

## 实施项（按序，先红后绿）

### M3-1 分享接线（D2/V1/V2，裁决 1-3A：按 spec REQ-B09 补齐）
1. share_config 落库：Skill.shareConfig 字段改为持久化映射（参照同类 JSON 字段 metadata 的既有 typeHandler/autoResultMap 模式）；新列 `share_config`（类型对齐 metadata 列的选择）。
2. SkillMapper.getCreatorId 修正 `created_by`→`creator`。
3. 可见性接线：SkillServiceImpl.visible 扩展——`DataScope.visible(...)` 之外，经 SkillAccessManager（注入使用，消灭零调用）判分享可见：accessLevel=global → 全员；department → user.orgId ∈ departmentIds；user → user.userId ∈ userUids（缺省/解析失败按不可见）。list 与 listAccessible 与 get/getBySlug 同步生效。
4. 降级语义：updateShareConfig 与 confirmDraft 的 shareConfig 入参——非管理员（无 `*` 权限且非 API Token）提交 accessLevel=global/department 强制降级 user（对齐 AccessManager 既有判定逻辑，复用勿重写）。

### M3-2 依赖三环修齐（D9 + M2 衔接缺口）
1. SkillMarkdownParser 解析三个依赖 frontmatter 字段（格式以 builtin-skills/web-search/SKILL.md 实际写法为准，兼容 string 与 list）入 SkillParseResult。
2. confirmDraft 与 SkillDataInitializer 将解析出的依赖写入 Skill.dependencies（JSON），且 confirmDraft 落 skill_dependency 行（经校验器）。
3. saveDependencies（create/update 两路径）先过 SkillDependencyValidator 校验（四类存在性/去重/自引用/未知类型），非法抛 BusinessException；校验器逻辑复用勿复制。
4. 依赖串规范化：SkillsMiddleware.loadActivatedSkillDependencies 追加前，把依赖引用规范化为 ToolRegistry 可解析形态——type=tool 的 `tool:xxx`/`xxx` → 裸工具 id；type=mcp → `mcp:{name}`；type=skill → `skill:{slug}`（以 ToolRegistry.resolveSingleTool 实际识别规则为准，先读代码再定格式）；规范化函数加单测。

### M3-3 install_skill 条件注入（D14，spec REQ-E03 已修订）
1. AgentEngine 装配 openai 工具后过滤：`install_skill` 仅当 `context.getSkills() 非空 || 沙箱启用` 时保留（沙箱启用的判定读 ChatBotContext.sandboxConfigId / runtimeState 既有写法，先核实取数路径）；否则从工具列表剔除（ToolRegistry 恒注入行为不动，在引擎侧过滤，登记 Deviation）。
2. InstallSkillToolExecutor 内容坐实：输出真实导入流水线指引（支持格式 .zip/.md、上传 `POST /api/skills/import/prepare` 自动安装、或草稿两步 `install-drafts/{draftId}/confirm`、slug 规则 `^[a-z0-9][a-z0-9._-]{0,63}$`、管理页路径提示），保持 ToolResult success 语义。

### M3-4 正文事实源（D10，裁决 3A）
1. SkillFileServiceImpl.updateFile/createNode：当 relativePath 为根 `SKILL.md` 时，写盘后经 SkillMarkdownParser 重解析并回写 DB（content/contentHash/name/description/version/category/metadata 随 frontmatter 更新；用 M1 已有的当前用户语义）；解析失败则文件写入回滚（或先解析后写盘，取实现简单者）并抛错。
2. SkillDataInitializer 改语义：DB 无该 slug 记录 → insert + 复制目录；DB 已有 → 整体跳过（不更新 DB、不覆盖磁盘目录）。删除每启动 REPLACE_EXISTING 行为。Deviation 登记相对 REQ-B05。
3. M1 遗留：prepareUpload 异常通路删除已创建的空草稿目录（best-effort）。

### M3-5 覆盖重装与绑定校验（D11）
1. confirmDraft slug 已存在 → update-in-place：保留主键 updateById（name/description/content/contentHash/version/category/metadata/author），磁盘目录删旧拷新，依赖行先删后插（经校验器），scope 行保留；app_skill_binding 因 id 不变天然存活。
2. AppConfigServiceImpl.bindSkill：selectById 校验技能存在且 enabled=1，否则 BusinessException（与 M2 的 20 上限校验并列）。

### M3-6 死代码清理（裁决：默认删除）
- 删除：executor/SkillToolExecutor.java + 其在 ToolRegistry/ToolExecutorFactory 的注册与 type=skill 路由 + ToolRegistry.resolveTools 的 `skill_`/`skill:` 解析分支与技能定义构建（:123-161 附近）+ fs/SkillReadonlyBackend.java + fs/FileSystemManager.java + 对应测试类（SkillReadonlyBackendTest 等）。
- 前置：全仓 grep 确认无生产引用（编译通过即证）；Deviation 登记：契约 REQ-E01/E02 的 skill 前缀路由与 REQ-E04 五类执行器收敛为四类，待下次契约同步修订（implement 文档登记，不在本次改 contract 文档）。

### M3-7 DDL 双处对齐（硬约定）
1. 逐列 diff SchemaInitializer 的 skill 表增补与 init-scripts/schema.sql 的 skill 表 CREATE，把仅存在于 SchemaInitializer 的列（slug/source_type/dir_path/dependencies/content_hash/is_builtin/metadata/created_at/creator/org_id）+ 新增 share_config 回写 schema.sql；skill_dependency/skill_scope/app_skill_binding 三表做同核对。
2. slug 若无索引则补（唯一索引 uk_slug 若现存数据可能重复则降级普通索引——查种子与写入路径后自判并报告选择）。
3. 新增 parity 单测（可放 tools 或 bootstrap 测试）：解析 schema.sql 的 skill 表段与 SchemaInitializer 的列名集合，断言一致（防再漂移）。

## 约束
- 遵守 docs/conventions.md（DDL 双处同步、HTTP 200+body.code、统一 ApiResponse）。
- 不回退 M1/M2 成果；不动 SandboxExecutionService、SkillsMiddleware 的 M2 缓存结构（仅 M3-2 规范化处小改）。
- 测试：`mvn -q test -pl fastrag-modules/fastrag-tools -am` 与 `-pl fastrag-bootstrap -am`（bootstrap 无测试则验证编译+parity 测试落 tools）；收工全绿（当前基线：tools 194 / agent 163 / application 74 全绿）。
- 环境问题导致测试无法运行时如实报告，不伪造。

## 交付报告格式
1. 基线/最终测试对比；2. 逐项改动文件+行号区间+测试清单；3. Deviations（预期：REQ-B05 语义被裁决取代、install_skill 引擎侧过滤、E01/E02/E04 契约偏离登记、slug 索引选择）；4. 未决问题。
