# 技能域修复方案 grill 裁决档案（idd-grill，2026-10-06 起）

> 主题：fastrag-backend 技能管理与技能在对话中的使用——缺陷修复 + WeKnora 对齐方案。
> 调研输入：两路 Explore 实测（WeKnora 全仓 / fastrag 技能链路工作树）+ docs/research/weknora-vs-fastrag.md + spec/tools-skills-mcp.md。
> 状态标记：resolved / open。本文档为批次裁决流水，最终方案文档成稿后本档案只作过程留痕。

## 缺陷登记（调研证实，工作树现状）

- **运行时断裂**：D6 拦截仅返回 name+description 正文到不了模型（SkillsMiddleware.java:278）；D1 绑定存 id 运行时按 slug 过滤静默丢弃（AppConfigServiceImpl.java:162 vs SkillsMiddleware.java:110）；D7 激活后依赖工具不重算（AgentEngine.java:126）；D12 ContextBuilder 与 SkillsMiddleware 双份装配不一致 + AgentExecutor 空链死链（AgentExecutor.java:67、AgentRunServiceImpl.java:204）。
- **安全**：D4 技能文件五端点无属主校验 operator="system"（SkillController.java:187-253,291）；D5 slug/draftId 路径穿越（SkillFileServiceImpl.java:54、SkillDraftServiceImpl.java:115）；D3 导入技能 creator=NULL 全员不可见（SkillDraftServiceImpl.java:181）。
- **未接线**：V1 AccessManager 零生产调用；V2 SkillMapper 查 created_by 实际列 creator（SkillMapper.java:19）；D2 shareConfig exist=false 全链路不落库（REQ-B09 空头）；D9 frontmatter 依赖不解析（SkillMarkdownParser.java:58）+ 写入不过校验器；D14 install_skill 恒注入桩（ToolRegistry.java:30）；SkillReadonlyBackend/FileSystemManager 未接线；SkillToolExecutor（REQ-B10）无注入路径。
- **一致性/性能**：D10 磁盘/DB/沙箱三源漂移（SkillDataInitializer.java:158、SandboxExecutionService.java:509）；D11 覆盖重装孤儿绑定；D13 每轮物化+全表查（SkillsMiddleware.java:61,109）；D8 无 token 预算/数量上限/描述不截断。
- **老账复核**：V3 stdio MCP 拒绝桩仍成立；V5 fastrag-tools 测试失配已修复（未提交）。

## 批次 1／技能域目标形态 — resolved（2026-10-06，用户「ok」全采纳）

1. **正文协议 → 拦截保留、返回 SKILL.md 全文（DB content 为源）**。弃：沙箱直读（沙箱 disabled 时技能全灭）、skill:// 虚拟协议接线（收益与拦截重合）。→ 新术语「技能正文拦截读」。
2. **绑定语义 → 统一到 id**：运行时按 id 解析 slug（批量查询），绑定表不动、零迁移。→ 新术语「技能挂载」。
3. **分享模型 → 按 spec REQ-B09 补齐接线**：share_config 落库 + Mapper 修列 + 导入补 creator + AccessManager 接入。→ 新术语「技能分享」。
4. **依赖机制 → 保留并修齐三环**：frontmatter 依赖解析 + saveDependencies 过校验器 + 激活后重算 openaiTools（spec REQ-B03/B08/B10 既定）。

CONTEXT.md 已同步更新（技能词条拆词 + 三新词条）。

## 批次 2／运行时治理 — resolved（2026-10-06，用户「按你推荐」全采纳）

1. **install_skill → 条件注入 + 内容坐实**：仅当上下文有技能挂载（或沙箱启用）才注入；执行内容改为真实导入流水线步骤；spec REQ-E03 同步修订。→ 新术语「技能安装引导」。
2. **注入预算 → 描述截 600 字符（对齐 WeKnora prompts.go:253）+ 挂载数上限缺省 20（超出报业务错误）**。
3. **@提及/must_use → 本期不做**，登记为技能域后续特性走独立 IDD 链（ConversationRequestState 加 skillNames + must_use 注入 + 前端 @ 选择器；WeKnora 对标 observe.go:685、session_agent_qa.go:506）。
4. **缓存 → run 级缓存**：技能上下文每 run 算一次，slug 集未变跳过物化；绑定解析改 selectBatchIds 按 id 批量查。
5. **双装配收敛 → 单一装配**：AgentRunServiceImpl 切到 AgentEngine（清偿「编排层仍用 @Deprecated 旧执行器」已登记债务），ContextBuilder 技能装配段删除收敛到 SkillsMiddleware 单点。

## 批次 3／安全与一致性口径 — resolved（2026-10-06，用户「按你推荐」全采纳）

1. **文件端点鉴权 → 对齐本模块 DataScope 口径**：读=visible（分享可见即可读），写/删=manageable，operator 取真实用户（同模块先例 DbInstanceServiceImpl.java:39-45）。
2. **slug/draftId → 入口白名单正则**（`^[a-z0-9][a-z0-9._-]{0,63}$` 形）四入口统一强制，对齐 WeKnora IsValidSkillName。→ 新术语「技能 Slug」。
3. **正文事实源 → DB skill.content 唯一事实源**：文件 API 写 SKILL.md 同步回写 DB（frontmatter 变更同步元数据列），启动播种不覆盖。→ 新术语「技能正文事实源」。
4. **审计 → 本方案一并补 @Loggable**：SkillController 写端点 + 文件写/删，对齐 DbInstanceController 先例。

## 批次 4／方案交付形式 — resolved（2026-10-06，用户「按你推荐」采纳）

1. **交付形式 → 独立方案文档 `docs/plan/skill-runtime-remediation.md` + spec 同步修订（REQ-E03/B09/B10 及验收清单 E03 行）+ feature-map #13 登记新缺陷与方案链接**。弃：只写文档不改 spec（CERTAIN 引证源与决议漂移）、完整五阶链（缺陷修复过重）。
2. **分期 → 安全先行**：M1 安全三缺陷 + 审计（D3/D4/D5 + @Loggable）→ M2 运行时断链六项（D6/D1/D7/D12/D13/D8）→ M3 接线补齐（D2/D9/D14/D10/D11 + 死代码清理）。
3. **工程约定 → DDL 双处对齐（skill 域四表 + 新增 share_config）+ 每项修复带测试（安全项加越权/穿越渗透用例；M2 后 fastrag-tools + fastrag-agent 全量回归）**。

## 会话终态

四批 16 项裁决全部 resolved（2026-10-06）。CONTEXT.md 新增 6 词条（技能分享 / 技能挂载 / 技能正文拦截读 / 技能安装引导 / 技能 Slug / 技能正文事实源）+ 技能主词条拆词。交付物：`fastrag-backend/docs/plan/skill-runtime-remediation.md`。idd-grill 会话终止。

## 实施完成（2026-10-06 同日 /idd-implement 补记）

M1（安全三缺陷+审计，191 绿）→ M2（运行时断链六项，agent 163/app 74/tools 194 绿）→ M3（接线补齐+死代码清理+DDL 双处对齐，tools 226/agent 160+11skip/app 76 绿）全部完成；最终全仓 16 模块 mvn test BUILD SUCCESS。要点：M1 发现 prepareUpload 文件名派生 slug 为第 4 个穿越入口并一并修复；M3 代理中途挂起后由续作代理盘点接管完成；M2 发现 `tool:` 依赖前缀与 ToolRegistry 解析不通，已并入 M3-2 规范化修复。未 git commit。
