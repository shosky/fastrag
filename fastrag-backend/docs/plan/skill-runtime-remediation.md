# 技能域修复方案 — skill-runtime-remediation

> **实施状态（2026-10-06 同日完成）**：M1~M3 全部落地，16 模块 `mvn test` BUILD SUCCESS（tools 19 类 226 用例 / agent 160+11 Docker 环境跳过 / application 76，修复前基线全绿零回归）。每项先红后绿（TDD）。实现记录：[implement/tools-skills-mcp.md](../implement/tools-skills-mcp.md)、[implement/agent.md](../implement/agent.md) 2026-10-06 增量节；未提交，改动留在工作树。Out of Scope 各项维持登记。
>
> 生成：2026-10-06，idd-grill 四批 16 项裁决全部 resolved 后成文。
> 范围：技能（Skill）管理与技能在对话/智能体中的使用——跨 feature #13 tools-skills-mcp（技能域）与 #17 agent（技能运行时相关部分）。
> 调研输入：两路源码级实测（fastrag 工作树 + WeKnora 本地检出全仓）＋ [docs/research/weknora-vs-fastrag.md](../research/weknora-vs-fastrag.md)（2026-09-25 架构级对比，本方案不重复其内容）。
> 裁决流水：`fastrag/.tmp/idd-skill-runtime-grill-decisions.md`；术语变更见 [CONTEXT.md](../../CONTEXT.md)（技能主词条拆词 + 6 新词条）。

---

## 1. 背景与结论

技能管理面（SkillController 24 端点、文件树编辑、ZIP 导入导出、依赖/作用域/分享）已成型，但**技能在对话里的链路四处断裂**：模型永远读不到 SKILL.md 正文（拦截短路只返回名称+描述）、绑定 id 与运行时 slug 语义错位导致绑定技能被静默丢弃、分享与依赖是空头承诺（写了没接线）、文件端点无属主校验构成现实越权面。

与 WeKnora 对比的定性：WeKnora 的技能是「镜像安装 + Progressive Disclosure」的完整闭环（元数据全注入 → 模型 `read_file(skill://…)` 主动读正文 → `shell_exec(skill_name=)` 执行），胜在通路完整与生命周期状态机；fastrag 的问题**不是缺架构，而是每个环节都写了但没通电**（14 项缺陷中 5 项属"零调用/不落库/桩"性质）。因此本方案的总原则是：

1. **接线优先于重写**——全部 16 项裁决均选择修复既有协议，不引入 WeKnora 的镜像安装模型或 skill:// 虚拟协议；
2. **唯一事实源**——SKILL.md 正文以 DB `skill.content` 为唯一事实源，绑定以主键 id 为唯一语义；
3. **安全先行**——越权与路径穿越先于功能价值交付。

## 2. 缺陷清单（14 项 + 2 项老账复核）

| 编号 | 分级 | 问题 | 关键证据 | 裁决 | 分期 |
|---|---|---|---|---|---|
| D6 | 运行时断裂 | 拦截 read_file 命中 SKILL.md 只返回 name+description，正文永远到不了模型 | `SkillsMiddleware.java:278`（拦截）、`:226`（注入）、`SandboxExecutionService.java:338`（白物化） | 拦截保留、返回 DB content 全文 | M2 |
| D1 | 运行时断裂 | 绑定存主键 id，运行时按 slug 过滤，id≠slug 的绑定被静默丢弃 | `AppConfigServiceImpl.java:162` vs `SkillsMiddleware.java:110`、`ContextBuilder.java:105` | 运行时按 id 解析 slug | M2 |
| D7 | 运行时断裂 | 激活技能后依赖工具改的是 context.tools，openaiTools 循环前一次生成不再重算 | `SkillsMiddleware.java:302`、`AgentEngine.java:126` | 激活后重算 openaiTools | M2 |
| D12 | 运行时断裂 | ContextBuilder 与 SkillsMiddleware 双份装配且无依赖闭包；deprecated AgentExecutor（空中间件链）仍被 AgentRunServiceImpl 使用，智能体 REST 路径技能全不生效 | `ContextBuilder.java:98-142`、`AgentExecutor.java:67`、`AgentRunServiceImpl.java:204` | 单一装配（切 AgentEngine + 装配收敛 SkillsMiddleware） | M2 |
| D13 | 性能 | 每轮模型调用前重复物化 + 全表 listAccessible | `SkillsMiddleware.java:61,109` | run 级缓存 + selectBatchIds | M2 |
| D8 | 性能/预算 | prompt 注入无 token 预算、无挂载数上限、描述不截断 | `SkillsMiddleware.java:226-237`、`AppConfigServiceImpl.java:162` | 描述截 600 + 上限 20 | M2 |
| D4 | 安全 | 文件五端点（树/读/建/改/删）无属主校验，operator 硬编码 "system"，任意登录用户可读写删任意技能目录 | `SkillController.java:187-253,291`、`SkillFileServiceImpl` 全程无 requireManage | 读=visible / 写删=manageable，operator 真实化 | M1 |
| D5 | 安全 | slug 与 draftId 零校验，`DELETE /api/skills/install-drafts/{../../xx}` 可删 data 目录任意子树 | `SkillFileServiceImpl.java:54`、`SkillDraftServiceImpl.java:115,229` | 入口白名单正则 | M1 |
| D3 | 安全 | 导入确认不设 creator/orgId，导入技能对所有用户（含上传者）不可见不可管理 | `SkillDraftServiceImpl.java:181-195` | confirmDraft 补 creator/orgId | M1 |
| D2 | 未接线 | 分享配置全链路不落库：shareConfig exist=false、AccessManager 零生产调用、Mapper 查 created_by 实际列 creator 吞异常 | `Skill.java:93`、`SkillServiceImpl.java:300-309`、`SkillMapper.java:19`、`SkillAccessManager.java:91-97` | 按 spec REQ-B09 补齐接线 | M3 |
| D9 | 未接线 | frontmatter 依赖字段不解析；saveDependencies 写入不过校验器（校验器只喂前端下拉） | `SkillMarkdownParser.java:58-63`、`SkillServiceImpl.java:194-216` | 依赖三环修齐 | M3 |
| D14 | 未接线 | install_skill 恒注入所有会话且实为指引桩 | `ToolRegistry.java:30`、`InstallSkillToolExecutor.java` | 条件注入 + 内容坐实 | M3 |
| D10 | 一致性 | 磁盘/DB/沙箱三源漂移：启动覆盖磁盘副本、文件 API 只写磁盘、物化读 DB | `SkillDataInitializer.java:158`、`SandboxExecutionService.java:509` | DB content 唯一事实源 | M3 |
| D11 | 一致性 | 覆盖重装 deleteById 旧技能产生孤儿绑定；绑定时也不校验技能存在 | `SkillDraftServiceImpl.java:168`、`AppConfigServiceImpl.java:162` | 覆盖重装改 update-in-place + 绑定校验 | M3 |
| V1/V2 | 老账 | AccessManager 零调用 / Mapper 错列名（并入 D2） | 同上 | 并入 M3 | M3 |
| V3 | 老账 | stdio MCP 拒绝桩（MCP 域，非本方案范围，维持登记） | `McpProtocolClient.java:70` | Out of Scope | — |

死代码登记：`SkillReadonlyBackend` + `FileSystemManager`（虚拟文件系统）全仓无生产调用；`SkillToolExecutor`（read_skill/activate）无任何 `skill_*` 注入路径（REQ-B10 原承诺无实现）——随 M3 清理（spec REQ-B10 已改为「技能正文拦截读」口径）。

## 3. 裁决记录（四批 16 项，全部 resolved 2026-10-06）

**批次 1／技能域目标形态**
1. 正文协议 = 拦截保留、返回 SKILL.md 全文（DB content 为源）。弃：沙箱直读（沙箱 disabled 时技能全灭）、skill:// 虚拟协议（改动大、收益重合）。
2. 绑定语义 = 统一到 id（运行时 id→slug，零迁移）。弃：绑定表迁 slug。
3. 分享模型 = 按 spec REQ-B09 补齐接线。弃：砍成两极（推翻已决议）。
4. 依赖机制 = 保留并修齐三环（解析/校验/激活重算）。弃：砍依赖模型。

**批次 2／运行时治理**
1. install_skill = 条件注入 + 内容坐实（有挂载或沙箱启用才注册）。弃：保留恒注入桩、彻底移除。
2. 注入预算 = 描述截 600 字符（对齐 WeKnora `prompts.go:253`）+ 挂载数上限缺省 20（超出报业务错误）。
3. @提及 pinned + `<must_use>` = 本期不做，登记后续独立 IDD 链（WeKnora 对标 `observe.go:685`、`session_agent_qa.go:506-536`）。
4. 缓存 = run 级缓存（slug 集未变跳过物化；selectBatchIds 替代全表扫）。
5. 装配 = 单点（AgentRunServiceImpl 切 AgentEngine；ContextBuilder 技能段删除）。

**批次 3／安全与一致性**
1. 文件端点鉴权 = 读 visible / 写删 manageable（对齐同模块 DbInstance/Mcp/Tool 先例 `DbInstanceServiceImpl.java:39-45`）。弃：全端点 manageable（与分享语义冲突）。
2. slug/draftId = 入口白名单正则 `^[a-z0-9][a-z0-9._-]{0,63}$`（对齐 WeKnora `IsValidSkillName`，skill_paths.go:40-49）。弃：仅出口 normalize 堵穿越。
3. 正文事实源 = DB skill.content 唯一事实源（文件 API 写 SKILL.md 同步回写；启动播种不覆盖）。弃：磁盘为源。
4. 审计 = 本方案一并补 @Loggable（对齐 DbInstanceController 先例）。

**批次 4／交付形式**
1. 独立方案文档（本文）+ spec 同步修订（REQ-E03/B09/B10 + 验收清单 E03 行，已随文完成）+ feature-map #13 登记（已完成）。弃：只写文档不改 spec、完整五阶链。
2. 分期 = 安全先行（M1→M2→M3）。
3. 工程约定 = DDL 双处对齐 + 每项修复带测试。

## 4. 与 WeKnora 对比：抄什么、不抄什么

**采纳（已入裁决）**
- Progressive Disclosure 的「元数据全量注入 + 模型主动读正文」骨架——fastrag 本来就是这个形状，只是读的通路断了（D6）；
- 描述截断 600 字符（`prompts.go:253`）；
- slug 白名单校验（`IsValidSkillName`，`skill_paths.go:40-49`）；
- 能力探测注册思想（"没这能力就不注册工具"）——落到 install_skill 条件注入；
- 管理面权限口径对齐（WeKnora 路由级守卫 ↔ fastrag DataScope 服务层口径补齐）。

**不抄（含理由）**
- **镜像安装模型**（`/opt/weknora/tenant/skills/<name>` + bundle_sha256 + snapshots 账本 + installing/ready/failed/removing 状态机）：重，且与 fastrag「DB 为事实源」冲突——fastrag 技能正文经拦截读不依赖沙箱存活，镜像就绪门槛反而制造不可用窗口；
- **LLM 安装 agent 装依赖**（pip/npm 现场装 + requirements.json）：fastrag 沙箱无 root 安装模式，且声明式依赖闭包资产（expandSkillClosure）已在，修三环成本远低于造安装 agent；
- **租户 catalog + 沙箱配置级安装**：fastrag 无租户体系，作用域由「分享（可见性）×挂载（应用绑定）」承担（CONTEXT.md 拆词）；
- WeKnora 无导出端点——fastrag 的 ZIP 导出是相对优势，保留。

**登记为后续独立特性候选（本方案 Out of Scope）**
- @提及技能（请求态 skillNames + `<must_use>` 每轮强提示 + 前端 @ 选择器）；
- 技能安装进度 SSE + 安装 transcript（对齐 WeKnora install-events/transcript）；
- 技能附属脚本沙箱执行（全目录物化 + 依赖安装，WeKnora 形态的轻量版）；
- stdio MCP（MCP 域独立议题）。

## 5. 分阶段工作项

### M1 — 安全三缺陷 + 审计（先行合入，改动面最小）

| # | 工作项 | 落点 | 要点 |
|---|---|---|---|
| M1-1 | 文件端点鉴权（D4） | `SkillController`、`SkillFileServiceImpl` | 五端点接 DataScope：读（树/读文件）= `DataScope.visible`（分享可见即可读，global/department/user 按 REQ-B09 口径），写/删 = `DataScope.manageable`；operator 从 SecurityContext 取真实用户，替换硬编码 `"system"` |
| M1-2 | slug/draftId 白名单（D5） | 新增 `SlugValidator`（tools/skill/） | 正则 `^[a-z0-9][a-z0-9._-]{0,63}$`，四入口统一强制：create/update、导入 confirm、文件访问 getSkillDir 前、draftId 解析前；违规报业务错误 |
| M1-3 | 导入补属主（D3） | `SkillDraftServiceImpl.confirmDraft` | set creator/orgId 取当前用户；批量导入逐条继承 |
| M1-4 | 审计埋点 | `SkillController` 写端点 + `SkillFileServiceImpl` 写/删 | @Loggable，审计含 slug+操作+operator（对齐 DbInstanceController 先例） |
| M1-5 | 测试 | SkillFileServiceImplTest 等 | 越权（他人技能读/写/删）、穿越（`../`、绝对路径、URL 编码）、降级（非管理员 global→user）渗透用例 |

### M2 — 运行时断链接通（核心价值交付）

| # | 工作项 | 落点 | 要点 |
|---|---|---|---|
| M2-1 | 拦截返回全文（D6） | `SkillsMiddleware.getSkillContent/interceptToolCall` | 命中 SKILL.md 时返回 `# {name}\n\n{description}\n\n---\n\n{content}`（content 取 DB），可附附属文件树；不再短路成 name+description |
| M2-2 | 绑定按 id 解析（D1） | `SkillsMiddleware.fillRuntimeSkillContext`、`AppServiceImpl` | context.setSkills 传 id 列表；`selectBatchIds` 批量查得 id→slug 映射后再进闭包展开；ContextBuilder 同步（见 M2-4） |
| M2-3 | 激活后重算工具（D7） | `AgentEngine` | 循环内工具调用后若 context.tools 被中间件修改（脏标记/版本号），重走 `ToolRegistry.resolveTools` 重生成 openaiTools |
| M2-4 | 装配单点（D12） | `AgentRunServiceImpl`、`ContextBuilder` | AgentRunServiceImpl 切 AgentEngine（清偿「编排层仍用 @Deprecated 旧执行器」债务）；ContextBuilder.prepareRuntimeContext 技能装配段删除，技能语义唯一归属 SkillsMiddleware |
| M2-5 | run 级缓存（D13） | `SkillsMiddleware` | 技能上下文（promptSkills/物化 slug 集）每 run 计算一次存 run 级状态；slug 集不变跳过 syncSkills 与重查询 |
| M2-6 | 注入预算（D8） | `SkillsMiddleware.buildSkillsPrompt`、`AppConfigServiceImpl.bindSkill` | 描述截 600 字符；挂载数上限 20（常量可配），超出报业务错误 |
| M2-7 | 测试 | SkillsMiddlewareTest、AgentEngine 相关 | 全文返回断言、id≠slug 绑定生效、激活后工具出现、同 run 第二轮零物化零全表查（日志断言）、截断与上限；**完成后跑 fastrag-tools + fastrag-agent 全量回归（137+148 用例基线）** |

### M3 — 接线补齐 + 清理

| # | 工作项 | 落点 | 要点 |
|---|---|---|---|
| M3-1 | 分享接线（D2/V1/V2） | SchemaInitializer + schema.sql、`SkillServiceImpl`、`SkillAccessManager`、`SkillMapper` | 新增 `skill.share_config` JSON 列（**DDL 双处同步**）；updateShareConfig 落库；AccessManager 接入 list 过滤与 requireManage；Mapper `created_by`→`creator` 修正；非管理员 global/department 强制降级 user（REQ-B09 原语义） |
| M3-2 | 依赖三环（D9） | `SkillMarkdownParser`、`SkillServiceImpl.saveDependencies` | 解析 frontmatter `tool_dependencies/skill_dependencies/mcp_dependencies` 入 Skill.dependencies（builtin-skills/web-search 已有此头，立即受益）；saveDependencies 过 SkillDependencyValidator |
| M3-3 | install_skill 条件注入（D14） | `ToolRegistry`、`InstallSkillToolExecutor` | 注入决策上移调用方：context 技能挂载非空或沙箱启用才加入 tools；执行器内容改为真实导入流水线步骤（ZIP/SKILL.md 上传→草稿→确认，指向 /api/skills 端点） |
| M3-4 | 正文事实源（D10） | `SkillDataInitializer`、`SkillFileServiceImpl` | 文件 API 写 SKILL.md 同步回写 DB content（frontmatter 变更顺带更新元数据列）并重写磁盘副本；启动同步按 REQ-B05 语义更新 DB（保留用户可改字段），磁盘播种仅在目录缺失时执行、不 REPLACE_EXISTING 整目录 |
| M3-5 | 覆盖重装与孤儿绑定（D11） | `SkillDraftServiceImpl.confirmDraft`、`AppConfigServiceImpl.bindSkill` | slug 冲突改 update-in-place（保留主键 → 绑定天然存活），无记录才插入；bindSkill 校验技能存在且启用 |
| M3-6 | 死代码清理 | `SkillToolExecutor`、`SkillReadonlyBackend`、`FileSystemManager`（+对应测试） | 默认删除（项目孤儿代码处置先例）；实施时若测试资产有复用价值可改 @Deprecated 保留 |
| M3-7 | DDL 双处对齐 | `init-scripts/schema.sql`、`SchemaInitializer` | skill/skill_dependency/skill_scope/app_skill_binding 四表 DDL 双处对齐（skill 表 8 个仅 SchemaInitializer 增补的列回写 schema.sql + 新增 share_config），顺手清偿存量漂移 |

**每项均带测试**；M3 完成后重跑 fastrag-tools 全量回归。

## 6. 验收清单

**M1**
- [ ] 非属主读他人技能文件被拒；global 分享的技能对可见用户可读、不可写删（M1-1）
- [ ] 文件操作审计日志 operator = 真实登录用户（M1-1/M1-4）
- [ ] 创建/导入/访问含 `..`、`/`、大写、特殊字符的 slug 一律被拒；`install-drafts/{../../xx}` 被拒（M1-2）
- [ ] ZIP 导入的技能出现在导入者技能列表且可管理（M1-3）

**M2**
- [ ] 对话中模型 read_file 命中 SKILL.md 返回含正文指令的全文（M2-1）
- [ ] id≠slug 的绑定技能在对话中生效（M2-2）
- [ ] 技能激活后其声明依赖的工具出现在下一轮 tools 列表（M2-3）
- [ ] 智能体 REST run 路径技能 prompt 注入生效（M2-4）
- [ ] 同一 run 第二轮起无重复物化与全表查询（M2-5）
- [ ] 描述超 600 字符被截断；第 21 个技能挂载被业务错误拒绝（M2-6）

**M3**
- [ ] 分享配置保存后重读一致；非管理员提交 global 降级 user；Mapper 修复后 AccessManager 正常返回 creator（M3-1）
- [ ] 含依赖 frontmatter 的 SKILL.md 导入后依赖入库；引用禁用工具/自身的依赖写入被拒（M3-2）
- [ ] 无挂载且沙箱未启用的会话工具列表无 install_skill；有挂载时调用返回真实导入步骤（M3-3）
- [ ] 文件 API 编辑 SKILL.md 后 DB content 同步、重启后编辑仍在（M3-4）
- [ ] 覆盖重装后既有应用绑定仍生效（M3-5）
- [ ] schema.sql 与 SchemaInitializer 对技能四表逐列 diff 为空（M3-7）

## 7. Out of Scope（登记后续独立链）

- @提及技能 pinned + `<must_use>` 强提示（需请求态加字段 + 前端 @ 选择器 + 注入协议，走独立 IDD 链）
- 技能附属脚本沙箱执行（全目录物化 + 沙箱内依赖安装）
- 安装进度 SSE / 安装 transcript
- stdio MCP 传输（MCP 域）
- 技能向量/语义检索选择（WeKnora 亦无，双方均未规划）
