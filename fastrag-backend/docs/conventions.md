# 后端开发规范（fastrag-backend，检测于 2026-09-21）

> 上位约定：[../AGENTS.md](../AGENTS.md)（全局宪法摘要 + 前端规范）、[../../docs/conventions.md](../../docs/conventions.md)（全仓约定）。
> 证据分层：CERTAIN = 配置/代码直接可观测；INFERRED = 从模式归纳；GAP = 不可检测，不臆造。

## 1. 命名规范（CERTAIN，宪法 + 代码观测一致）

- 类名：大驼峰、名词性（`DocumentParserImpl`、`MediaExtractor`）
- 方法名：小驼峰、动词开头（`extractKeyframes`、`mergeSegmentsWithKeyframes`）
- 包名：`com.fastrag.module.<域>.<层>` 五段式（controller / service+impl / mapper / entity / model）
- 数据库表：业务域前缀 + 小写下划线（`kb_`、`app_`、`sys_`、`wf_`、`agent`、`mcp_`、`skill_`、`term_`）
- 环境变量：全大写下划线
- 测试类：`*Test.java`，包路径与被测类镜像（CERTAIN，63 个测试类观测）
- 术语：命名必须使用 [../CONTEXT.md](../CONTEXT.md) 术语表中的词（如 Chunk/Parse Strategy/Re-chunk），不得使用「避免」同义词
- 文档引用规范（2026-09-24 review 增补）：**跨特性引用 spec REQ 必须带特性名限定**（如 `kb-core REQ-B02`），不得裸 ID——17 份 spec 均从 REQ-A01 起编、跨文档重名；iam 为搬入件用扁平 REQ-001~033，同受此规则约束
- design 文档 (S) 模块口径（2026-09-24 review 增补）：浅模块在 §1 标 **(S)** 并建议内联，**不立完整契约节**；若其公共表面含真实结构事实（如无 Service 的控制器直出面），在 §2 以「(S) 表面注记」单行列出并指明为何不立契约——不得给 (S) 模块写 Public/Hidden/Depth 全套接口节

## 2. 文件与目录组织（CERTAIN）

- 模块内固定分层 `controller → service(接口) + service/impl → mapper → entity`，DTO 在 `model/`，跨域协作在 `manager/`
- mapper 由 `@MapperScan("com.fastrag.module.*.mapper")` 汇聚
- DDL：`init-scripts/schema.sql` 全量 + `init-scripts/` 下增量迁移；运行时幂等增列/建表走 `SchemaInitializer`（启动时执行，替代 Flyway）——**两处必须保持一致**（2026-09-21 图谱表曾出现漂移，已修复，勿再引入）
- 测试位置：`src/test/java` 镜像包结构；iam/publish/operation/common/security/infra/bootstrap 六模块目前无测试（补链时按 /idd-tdd 补齐）

## 3. 正确模式（自现有代码提取）

```java
// GOOD: 外部依赖调用优雅降级——失败不阻断主流程（全仓 435 处 catch 的主流形态）
try {
    config = loadKbRetrievalConfig(kbId);
} catch (Exception e) {
    log.warn("Failed to load KB retrieval config ..., use defaults", e); // RetrievalServiceImpl
    config = defaultConfig();
}
```

```java
// GOOD: 统一返回 ApiResponse + 静态工厂；分页 PageResult{list,total,page,pageSize}
return ApiResponse.success(pageResult);
```

- SSE 实时推送：`SseEmitter` + Redis pub/sub 跨实例转发（`RunEventPublisher` 模式）；重活走 RabbitMQ 异步（摄取 `fastrag.ingestion.queue` / 图谱 `fastrag.graph-build.queue` 两队列）（CERTAIN）
- KB 资源端点鉴权：类/方法上 `@KbAuth` 切面 + `@PreAuthorize("@perm.has('api:xxx:yyy')")`（CERTAIN）
- 外部基础设施地址走 application.yml `@Value`；模型地址/密钥查 `model` 表动态路由（CERTAIN）

## 4. 反模式 / 禁止（CERTAIN，宪法明令）

```text
AVOID: 任何 Elasticsearch 相关开发（ES 已移除，全文检索走 MySQL LIKE）
AVOID: 在 fastrag-ai / fastrag-infra 层直接查询业务库；model 表查询放 caller 侧
AVOID: 硬编码外部依赖地址/密钥（⚠ 现状违规：.env.example 内含真实公网 IP 与开发凭据，勿模仿）
AVOID: 业务错误返回非 2xx——统一 HTTP 200 + body.code，调用方必须判断 body.code
AVOID: schema.sql 与 SchemaInitializer 双处 DDL 漂移（新表/新列两处同步）
```

## 5. 错误处理（CERTAIN）

- `GlobalExceptionHandler`（@RestControllerAdvice，fastrag-common）：`BusinessException` → HTTP 200 + body{code,message}；`AccessDeniedException` → 403；参数校验失败 → 400 语义（body 承载）；兜底 `Exception` → HTTP 200 + `serverError("服务异常")`
- 日志级别：正常 info/debug、降级 warn、不可恢复 error（宪法）

## 6. Lint / Format / 质量门

```bash
mvn test -f fastrag-backend/pom.xml          # 后端质量门（无 checkstyle/spotless → [GAP]）
```

- 静态检查：**[GAP]** 未配置任何 lint/format 工具
- Git 提交信息：**[GAP]** 现状极不规范（2026-09-21：全仓 34 条中 16 条为 "f"）；建议采用 Conventional Commits（与既有有效提交 `feat(scope): ...` 一致）
