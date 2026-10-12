# M1 任务书 — 技能域安全三缺陷 + 审计（skill-runtime-remediation M1）

> 输入：docs/plan/skill-runtime-remediation.md §5-M1（裁决 2026-10-06，四批 grill 已闭环）。
> 你是实现代理：按 /idd-tdd（红-绿-重构）逐项实施。先读 SKILL.md：C:\Users\63179\.zcode\skills\idd-tdd\SKILL.md 并遵循其循环。
> 模块：fastrag-modules/fastrag-tools（+fastrag-common 枚举可能新增值）。**禁止 git commit**，改动留在工作树。

## 背景（已证实现状，行号为 2026-10-06 工作树实测）

- SkillController 文件五端点（`{slug}/tree|file GET/POST/PUT/DELETE`、`{slug}/export`）直接调 SkillFileService，**无任何属主校验**；operator 来自私有方法 getCurrentUserId() 硬编码返回 "system"（SkillController.java:291-294）。同模块 Db/Mcp/Tool 三服务均已用 `DataScope.visible/manageable`（fastrag-security/src/main/java/com/fastrag/security/util/DataScope.java:52-64）+ `SecurityUtil.getCurrentUser()`。
- SkillFileServiceImpl.getSkillDir 直接 `Paths.get(dataDir,"skills",slug)`（:54-56），slug 零校验；SkillDraftServiceImpl 的 draftId 直接 `getDraftsRoot().resolve(draftId)`（:115,:229,:352），`DELETE /api/skills/install-drafts/{../../xx}` 可删 data 目录任意子树；prepareUpload 单文件分支 slug=文件名去扩展名直接当目录名（:70-73），同样可穿越。extractZip 有双检（:248-253）。
- confirmDraft 落库**不设 creator/orgId**（SkillDraftServiceImpl.java:181-197 只设 author=operator），而可见性 SkillServiceImpl.visible 按 creator/orgId/isBuiltin 判定 → 导入技能全员不可见。
- SkillController 已 import @Loggable/ActionType/LogCategory（:65-67）但零使用；ActionType 已有 skill_created/skill_updated/skill_deleted（ActionType.java:210-212）。
- SkillServiceImpl.get/getBySlug 已有 visible 校验（:96-102）——文件端点绕过服务直连文件库是缺口根源。
- SkillFileServiceImpl 已注入 SkillMapper 但**从未使用**（接线后即可用）。

## 实施项（按序，每项先写失败测试）

### M1-1 文件端点鉴权（裁决：读=visible，写/删=manageable，operator 真实化）
- SkillFileServiceImpl 六个公开方法（getTree/readFile/createNode/updateFile/deleteNode/exportZip）入口加校验：
  - 经 skillMapper.selectBySlug(slug) 查技能（**slug 先过 M1-2 校验**）；查无 → BusinessException（"技能不存在: {slug}"）。
  - 读类（getTree/readFile/exportZip）：`DataScope.visible(user, creator, orgId, isBuiltin)` 不过 → `BusinessException.forbidden("无权访问该技能")`（对齐 SkillServiceImpl:99 文案口径）。
  - 写类（createNode/updateFile/deleteNode）：`DataScope.manageable(user, creator)` 不过 → forbidden（对齐 DbInstanceServiceImpl:45 文案口径）。
  - user 取 `SecurityUtil.getCurrentUser()`；内置技能 creator='system'/isBuiltin=1 → 全员可读、仅 `*` 权限或 API Token 可写。
- SkillController 文件端点 operator 改 `SecurityUtil.getCurrentUserId()`，删除 getCurrentUserId() 占位方法。

### M1-2 SlugValidator 白名单（裁决：入口正则 `^[a-z0-9][a-z0-9._-]{0,63}$`）
- 新增 `com.fastrag.module.tools.skill.SlugValidator`：`isValid(String)` boolean + `validate(String)`（非法抛 BusinessException，消息含具体规则与实际值）+ `normalize(String name)`（小写、空白/连续非法字符转 `-`、掐头尾 `-`、截 64；结果仍非法返回 null）。
- 四类入口统一接线：
  1. SkillServiceImpl.applyBasicFields：form.slug 存在 → validate；缺省 → normalize(name)，null 时报错提示显式提供 slug（新增行为，登记 Deviation）。
  2. SkillMarkdownParser.parse：frontmatter slug（或缺省 name 派生）→ validate 失败按 E_SKILL_MD_INVALID 语义抛 IllegalArgumentException（消息含字段名）——prepareUpload/parseAndAdd 已有失败通路。
  3. SkillFileServiceImpl：六方法入口 validate(slug)（在查库前）。
  4. SkillDraftServiceImpl：draftId 在 confirmDraft/discardDraft/loadDraft 用前须匹配 UUID 格式（`^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$`，非法抛 RuntimeException"非法的草稿 ID"，登记 Deviation：比白名单更严因 draftId 本就由 UUID 生成）；prepareUpload 单文件分支 slug（文件名派生）与 reorganizeSingleSkill 的 frontmatter slug → validate。

### M1-3 导入补属主（裁决：confirmDraft 补 creator/orgId）
- confirmDraft 创建 Skill 时：`s.setCreator(operator)`（入参）+ orgId 取 `SecurityUtil.getCurrentUser().getOrgId()`（对齐 SkillServiceImpl.create 口径）。

### M1-4 审计埋点（裁决：本方案一并补）
- SkillController 写端点加 @Loggable（category=operation，对齐 DbInstanceController:37-48 风格，target 用 SpEL）：
  - create→skill_created、update/toggle/updateDependencies/share-config/enabled→skill_updated、delete→skill_deleted；
  - import/prepare 与 install-drafts/{draftId}/confirm→skill_created（target="#draftId"）；
  - 文件 POST/PUT/DELETE：ActionType 无对应值则**新增** `skill_file_created/skill_file_updated/skill_file_deleted`（fastrag-common enums，注意该文件工作树已有未提交修改，追加枚举值放技能区块尾部）；
  - discardDraft（删草稿非技能）不埋，登记 Deviation。

### M1-5 渗透测试（红先于绿）
- 新增 skill/SlugValidatorTest：合法/非法矩阵（`../`、大写、中文、超长、空、前导 `-`/`.`、`a b`）。
- SkillFileServiceImplTest 增：非属主读树/读文件被拒（BusinessException forbidden）；非属主写/删被拒；属主与 `*` 权限放行；`{slug:"../x"}` 被拒；查无技能报"技能不存在"。SecurityContext 注入参照 SkillServiceImplTest 既有修法（工作树未提交改动里已有样例）。
- SkillDraftServiceImplTest 增：draftId=`../x` confirm/discard 被拒；上传 `../../evil.md` prepareUpload 被拒；confirmDraft 后 DB 记录 creator=operator 且 orgId 非空。

## 约束
- conventions.md：HTTP 200+body.code（BusinessException 体系）、测试镜像包结构、命名用 CONTEXT.md 术语（技能分享/技能挂载/技能 Slug）。
- 不改 SkillFileService/SkillDraftService 接口签名（operator 参数保留，鉴权在实现内部用 SecurityContext，防伪造）。
- 测试命令：先 `mvn -q test -pl fastrag-modules/fastrag-tools -am -f pom.xml`（在 fastrag-backend 目录下）；若本地仓库已有依赖可退 `mvn -q test -f fastrag-modules/fastrag-tools/pom.xml`。**先跑一次基线并记录失败用例集**（存量有与本任务无关的失败，见 feature-map #19 遗留注记），最终失败集 ⊆ 基线失败集。
- 每项完成后记录：改动文件、测试名、红→绿证据（可摘要）。

## 交付报告格式
1. 基线失败集 / 最终失败集对比；2. 逐项改动文件+行号区间与测试清单；3. Deviations（预期 4 条：缺省 slug 派生、draftId UUID 严格校验、discard 不埋审计、文件端点鉴权置于 vault 内部）；4. 未决问题。
