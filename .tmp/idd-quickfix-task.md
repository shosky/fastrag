# 快修任务书 — idd-review 对标评审快修批次（2026-10-06）

> 来源：idd-review 对标 WeKnora 评审（五单元 27 findings）的快修清单，7 项。
> 输入评审报告（四要素证据）见会话记录；本文书含每项的精确规格。
> 你是实现代理：按 /idd-tdd（先读 C:\Users\63179\.zcode\skills\idd-tdd\SKILL.md）逐项先红后绿。
> 模块跨度：application / bootstrap / security / agent / tools。**禁止 git commit**。
> 基线：全仓 16 模块 mvn test BUILD SUCCESS（tools 226 / agent 160+11skip / application 76），收工必须保持全绿。
> 测试命令（fastrag-backend 目录下）：`mvn -q test -pl <模块> -am`；单测可用 -Dtest=。防挂起：单条 bash 控制在几分钟内，优先 targeted -Dtest。

## QF-1 引用投影补 file_id（P0-5 止血）
- 位置：fastrag-modules/fastrag-application/.../service/impl/AppServiceImpl.java extractKbSources（约 :713-746）。
- 现状：投影只取 fileName/score/kbId/chunkIndex/content，把上游（RetrievalKbOperationService 结构化通道 :83-91 已含 file_id）返回的 file_id 丢了。
- 改法：投影补 fileId 字段（大小写以实际上游 Map key 为准，实测）；references 落库与前端契约自然受益。若消息引用 DTO 有独立类则同步加字段。
- 测试：AppServiceImpl 相关测试断言投影含 fileId（构造上游含 file_id 的输入，断言投影保留）。

## QF-2 schema.sql 重复列修复 + 全文件冒烟测试（P0-9）
- 位置：init-scripts/schema.sql:598 与 :601（model_call_log 两个 status 列）。
- 改法：删除其中一个 status 列（保留语义与实体 ModelCallLog 一致的那个——先读实体确定列名/类型/注释归属，勿删错）。
- 测试：新建 fastrag-bootstrap/src/test/java/com/fastrag/config/SchemaSqlSmokeTest.java：读 init-scripts/schema.sql，逐 CREATE TABLE 解析列名，断言无表内重复列（解析器可简单：按 CREATE TABLE 块切分、行首列名提取、容忍约束行）。bootstrap 模块若无 test 依赖则在模块 pom 补 spring-boot-starter-test（test scope）。

## QF-3 ApiTokenAuthFilter 按 permission 授权（P0-8）
- 位置：fastrag-security/.../filter/ApiTokenAuthFilter.java:74-88；SysApiToken 实体（permission 字段 read/write，:43-44）。
- 现状：只验 tokenId 存在性，统一授 `kb:manage`。
- 改法：验证通过后取实体 permission 字段——"read" 授 `kb:read`，"write"（或空，向后兼容）授 `kb:manage`。ApiTokenServiceImpl.findValidToken 若只回 boolean 则改为返回实体（或 Optional<SysApiToken>），调用方同步。@PreAuthorize 端点语义不变（kb:read 端点不受影响，kb:manage 端点对 read token 自动 403）。
- 测试：security 模块若无 src/test 则新建 + pom 补 test 依赖；ApiTokenAuthFilterTest：read token 请求 → authorities 含 kb:read 不含 kb:manage；write token → 含 kb:manage；无效 token → 不放行。
- Deviation 登记：DataScope.isApiToken 对非 KB 数据的"全局可见可管理"语义本次不动（属 capability 立项范围）。

## QF-4 executeSync 轮次耗尽兜底文案（P0-4）
- 位置：fastrag-modules/fastrag-agent/.../executor/AgentEngine.java executeSync（约 :443,519；对照 executeStream :356-364 兜底分支）。
- 改法：循环因达 maxSteps 结束且 finalAnswer 为空时，填入与 executeStream 一致的兜底文案（"已达到最大步数限制…"，以流式分支现文案为准），保持 success 语义不变（终态语义产品化另行立项）。
- 测试：AgentEngineTest 增用例：模拟循环耗尽且无 answer，断言 AgentResult.content 为兜底文案而非空串。

## QF-5 终态回写前查状态（P0-2 最小版）
- 位置：fastrag-modules/fastrag-agent/.../service/impl/AgentRunServiceImpl.java executeRunAsync（约 :208）。
- 改法：写终态前 selectById 读当前 status；若已非 running（如 cancelled）则不覆盖，log.info 记录"取消保留"。注意与现有状态机行为兼容：completed/failed 正常路径不变。
- 测试：AgentRunServiceImplTest 增用例：DB 预置 status=cancelled，引擎返回 success，断言最终 status 仍为 cancelled；正常路径 completed 不回归。
- Deviation 登记：引擎循环内取消检查点（真中断）不在本批，另行立项。

## QF-6 toOpenAITools 按名稳定排序（P1-2）
- 位置：fastrag-modules/fastrag-tools/.../registry/ToolRegistry.java toOpenAITools（约 :121-144）。
- 改法：返回前按 function name 稳定排序（Comparator.comparing）；两条消费路径自动受益。
- 测试：ToolRegistry 相关测试（或新建）断言输出按 name 升序且乱序输入仍有序。

## QF-7 writeFile 刷新活动标记（P2-3）
- 位置：fastrag-modules/fastrag-agent/.../sandbox/provider/docker/DockerSandboxAdapter.java writeFile（约 :387-407，tar 通道绕过 execWrapper 的 touch 前缀 :953-957）。
- 改法：写成功后补一次轻量活动刷新——优先把 makeDir 阶段的 exec 复合成 `mkdir -p …; touch <ACTIVITY_MARKER>`（零额外往返）；若 writeFile 路径无 exec 阶段则写后补一次裸 exec touch（与 lastActivity 同款包装）。
- 测试：DockerSandboxAdapterTest 增用例：验证 writeFile 路径构造的命令/后续调用包含对活动标记的 touch（按现有 mock 模式断言 exec 参数串）。

## 统一约束
- 遵守 docs/conventions.md；不改公共接口签名除非任务书明确（QF-3 允许 findValidToken 返回类型变更）。
- 每项先写失败测试；全部完成后跑 `mvn -q test -pl fastrag-modules/fastrag-agent,fastrag-modules/fastrag-tools,fastrag-modules/fastrag-application,fastrag-security,fastrag-bootstrap -am` 全绿，再报全会结果。
- 环境问题如实报告，不伪造测试结果。

## 交付报告格式
1. 基线/最终测试对比（五模块）；2. 逐项改动文件+行号区间+测试清单；3. Deviations；4. 未决问题。
