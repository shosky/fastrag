# M2 任务书 — 技能运行时断链接通（skill-runtime-remediation M2）

> 输入：docs/plan/skill-runtime-remediation.md §5-M2（裁决 2026-10-06）。前置：M1 已完成（tools 模块 191/191 绿，全仓绿）。
> 你是实现代理：按 /idd-tdd（先读 C:\Users\63179\.zcode\skills\idd-tdd\SKILL.md）实施。
> 模块：fastrag-modules/fastrag-agent 为主 + fastrag-modules/fastrag-application（挂载上限）+ fastrag-modules/fastrag-tools（SkillService 可能加只读方法）。**禁止 git commit**。

## 背景（2026-09-28 工作树实测行号，动手前重新核实）

- **D6**：SkillsMiddleware.interceptToolCall（:73-99）命中 `/workspace/skills/{slug}/SKILL.md` 时短路返回 getSkillContent（:278-288）= 仅 `# name + description`，正文永远到不了模型；沙箱物化（SandboxExecutionService.syncSkills :338-367，渲染 DB content 写 `/workspace/skills/{slug}/SKILL.md`）被短路白做。
- **D1**：AppServiceImpl :593 `context.setSkills(AppSkillBinding::getSkillId 列表)` 传的是**主键 id**；SkillsMiddleware.fillRuntimeSkillContext（:104-145）与 ContextBuilder（:105-112）把它当 **slug** 过滤 listAccessible → id≠slug 的绑定被静默丢弃。
- **D7**：loadActivatedSkillDependencies（:294-315）激活后改 context.tools，但 AgentEngine :126-145 只在循环前 resolveTools→openaiTools 一次，循环内不重算。
- **D12**：ContextBuilder.prepareRuntimeContext（:98-142）与 SkillsMiddleware 技能装配重复且无依赖闭包（:117 直接 validSlugs）；AgentRunServiceImpl（:55,:204）仍用 deprecated AgentExecutor（AgentExecutor.java:66-67 空中间件链）→ 智能体 REST 路径技能全不生效。
- **D13**：beforeModelCall（:47-70）每轮模型调用都 fillRuntimeSkillContext（全表 listAccessible :109）+ syncSkillsToSandbox（:151-164），一个 run 内技能集合不变。
- **D8**：buildSkillsPrompt（:221-240）全量拼接，描述不截断、数量无上限；AppConfigServiceImpl 绑定端点（:162 附近）无数量校验。
- BaseContext：skills 为 @ConfigField（:74-75），promptSkills/readableSkills 等为 transient（:91-93）；ChatBotContext 有 sandboxConfigId/subagents（:37-49）。
- SubAgentMiddleware 走 AgentEngine.executeSync（:134,:141）——不受本任务影响，勿破坏。

## 实施项（按序，每项先写失败测试；技能上下文状态一律存 context（每 run 一份），严禁存中间件实例字段——中间件是单例多 run 共享）

### M2-1 拦截返回全文（D6，裁决 1A：拦截保留、DB content 为源）
- getSkillContent 改为返回：`# {name}\n\n{description}\n\n---\n\n{content}`（content=skill.content，即技能正文指令）；name/description/content 取运行时技能上下文中缓存的 Skill 实体（见 M2-5）。
- interceptToolCall 的命中条件、skip 语义不变；沙箱物化保留不动。

### M2-2 绑定按 id 解析（D1，裁决 2A：统一到 id）
- 约定：`context.getSkills()` 语义 = 技能主键 id 列表（与 app_skill_binding.skill_id 一致；在 BaseContext.skills 的字段注释上写明）。
- SkillsMiddleware 装配改为：ids → `skillService` 新增 `listByIds(Collection<String> ids)`（tools 模块 SkillService 接口+实现，selectBatchIds）→ 过滤 enabled=1 → 与 listAccessible（可见+启用，用户域）交集得 seed slugs（被丢的 warn 日志含 id）→ 既有 expandSkillClosure 依赖闭包展开。
- ContextBuilder 同步改（在 M2-4 中整体处理）。

### M2-3 激活后重算工具（D7）
- BaseContext 增 transient `toolsDirty` 标志；loadActivatedSkillDependencies 追加依赖后置 true。
- AgentEngine 循环内每次模型调用前：若 toolsDirty → 重走 resolveTools→openai 转换并清标志（只重算一次/置位一次，勿每轮全量重查）。

### M2-4 装配单点（D12）
- AgentRunServiceImpl 切换到 AgentEngine（参照 AppServiceImpl :653 的调用形态 executeStream/executeSync + 中间件链来源），保留 agent_run 生命周期与 SSE 事件语义（RunEventPublisher 事件照旧）；切换后删除 AgentExecutor 类及其测试（全仓 grep 确认无引用后删，javadoo 引用一并清理）。
- ContextBuilder.prepareRuntimeContext 删除技能装配段（skills 相关全部），保留其余职责；技能语义唯一归属 SkillsMiddleware。若删除后 prepareRuntimeContext 无存在意义则整体删除并清理调用链（登记 Deviation）。

### M2-5 run 级缓存（D13）
- 技能上下文（可见技能实体列表/seed slugs/闭包结果/promptSkills/readableSkills/DepMap/lastSyncedSlugs）每 run 首轮计算一次，存 BaseContext transient（新增 `skillsLoaded` boolean 与 `syncedSkillSlugs` 集合）；后续轮次 slug 集不变则跳过 listAccessible/listByIds/syncSkills。
- 技能读取（M2-1 getSkillContent）从缓存实体取，不再查库。

### M2-6 注入预算（D8，裁决 2A）
- buildSkillsPrompt：description 渲染前截断 600 字符（对齐 WeKnora prompts.go:253）。
- AppConfigServiceImpl 技能挂载写入路径（bindSkill）：当前应用 enabled 技能绑定数 ≥20 时拒绝新增（BusinessException，文案含上限值）；上限常量 `MAX_SKILL_BINDINGS = 20`。

### M2-7 测试
- SkillsMiddleware 测试：全文返回断言（含正文指令片段）；id≠slug 绑定生效（id=数字型主键、slug 完全不同）；可见性交集丢弃有 warn；缓存生效（第二轮零 skillService 调用，用 Mockito verify 计数）；toolsDirty 重算（AgentEngine 侧用可注入 fake 或集成切片）；600 截断。
- AppConfigService 测试：第 21 个挂载被拒。
- AgentRunServiceImpl 切换后：agent 模块既有 148 用例回归全绿（新哑替身按需）。

## 约束
- 遵守 docs/conventions.md；中间件单例——状态必须在 context。
- 不动 tools 模块除 SkillService.listByIds 新增外的任何文件（M1 成果勿回退）；不动 SandboxExecutionService。
- 测试：`mvn -q test -pl fastrag-modules/fastrag-agent -am -f pom.xml`（fastrag-backend 目录下）；收工前加跑 `-pl fastrag-modules/fastrag-application,fastrag-modules/fastrag-tools` 的模块测试。基线当前为全绿（M1 后实测），收工时必须仍全绿。
- 若 AgentEngine 与 RunEventPublisher/事件语义冲突导致切换风险超预期（连续 3 次失败），降级方案：保留 AgentExecutor 但注入与 App 路径相同的中间件链，并在报告标记降级 Deviation。

## 交付报告格式
1. 基线/最终测试对比（agent+application+tools）；2. 逐项改动文件+行号区间+测试清单；3. Deviations；4. 未决问题。
