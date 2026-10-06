# 会话归档 — 2026-09-28 沙箱实装 grill 裁决（WeKnora 对齐）

## 需求

用户指令：在应用里增加沙箱，完全参考 WeKnora（`D:\Workspace\python\github\WeKnora`，**Go 项目**）的实现。经 5 批 grill 全部裁定，25 项决策，12 个未决分支全关闭。术语已入 CONTEXT.md（沙箱/沙箱配置/绑定/对账/加固基线/拨号防护/配置验证/附件暂存/降级）。

## 参照系调研结论（WeKnora 侧）

- 多后端抽象：`SandboxType` = docker/cube/e2b/host/disabled（internal/sandbox/sandbox.go:14-35）；host 仅桌面客户端（build tag），服务端不含。
- Docker 后端：容器内零服务端，宿主机直打 Engine API（ExecCreate/Attach/Inspect + archive + `find -printf`，docker_remote_client.go），in-container `timeout` 包装 + 10s grace（dockerExecGrace）。
- 绑定：tenant+session → sandbox 版本化 JSON 落 Redis（session_binding.go），CAS 换绑；多副本必须共享 Redis（部署文档）。
- 配置：命名实体 `tenant_sandbox_config` 落库（凭据加密、软删、cordon），agent 定义引用 `sandbox_config_id`（custom_agent.go:194）；路由 `/sandbox-configs` CRUD + `/:id/sandboxes` 库存 + `templates/query`（routes_infra.go:53-68）。
- 生命周期：exited 不算终态、Connect 自动重启；空闲回收 = exec 包装器内嵌 touch 活动标记 + 清扫读 mtime + TTL 落 label（docker_idle_sweeper.go），默认 30min；孤儿对账 = provider List 按 metadata 过滤 vs 绑定比对 + grace（orphan_reaper.go）。
- 安全：CapDrop ALL + 7 caps + no-new-privileges + Init=true + 禁 swap，默认 2C/2GiB/512pids（docker_engine.go:108-124、docker_remote_client.go hostConfig）；网络仅 bridge/none；OutboundURLPolicy SSRF 防护（link-local 恒拒）；危险命令校验只在 legacy Manager 路径（manager.go:129），现代会话路径零内容审查。
- 工作区：`/workspace` + input（只读附件）/output（产物），WriteRoots=[/workspace,/workspace/output]、ReadRoots=[output,input,root]（workspace_layout.go:58-68）。
- 后置能力：交互终端/图形桌面（仅 Cube/E2B 支持）、技能快照（docker commit + 指纹）。

## 裁决清单

### 第一批：范围与架构基线

1. **后端类型**：docker/cube/e2b/disabled 四类型，host 不做。
2. **接入层级**：新建 `sandbox_config` 配置实体（user_id 归属，对齐 fastrag 资源归属惯例），**应用定义引用 sandbox_config_id**；管理员全局启用开关放 platform-config。
3. **容器侧协议**：废弃容器内 HTTP 设计；每适配器各自协议（Docker=Engine API 直连；Cube/E2B=控制面 REST + envd）；SandboxBackend 7 动作接口保持、实现重写。
4. **镜像与布局**：镜像/模板进配置实体字段；自建 `fastrag-sandbox` 镜像复刻 WeKnora runtime target（python3.12-slim+node20+uv+pnpm+同 apt 包列表+user 1000）；布局切 `/workspace`。

### 第二批：会话绑定与生命周期

5. **绑定键**：(userId, sessionId) 会话级；同会话多轮共享容器（状态保持），新会话新箱；`fileThreadId` 键废止（修订 REQ-H01）。
6. **绑定存储**：Redis 版本化 binding（version/provider/sandboxId/configId/createdAt/staleAt），key `sandbox:binding:{userId}:{sessionId}`。
7. **生命周期语义**：跨 turn 状态保持；exited 自动重启续用；配置身份变更→stale→下次 resolve 删旧建新（重建对用户透明，SSE 提醒后置）；会话删除挂钩释放沙箱。
8. **空闲回收**：活动标记 + TTL label 机制对齐；默认 TTL 1800s（WeKnora 同款，替代 600s）；Cube/E2B 走 provider TTL。
9. **孤儿对账**：@Scheduled 周期，managed label + configId 过滤（含 paused）vs 绑定比对，grace 10min。

### 第三批：安全与资源基线

10. **容器加固**：照搬 hostConfig（CapDrop ALL+7caps、no-new-privileges、Init=true、memory=memorySwap、2C/2GiB/512pids 默认，三值进配置实体）。
11. **网络模式**：仅 bridge/none（保存时校验拒绝其他）；域名级出网策略仅 Cube/E2B 支持，docker 配置拒收域名列表。
12. **Dial Guard**：endpoint SSRF 防护——link-local/metadata 恒拒、私网须显式 AllowPrivate、保存时校验+建连时 IP 复查（Java 工具类）。
13. **不引入命令内容审查**：对齐 WeKnora 实际行为（validator 仅 legacy 路径）；现有 `contains("..")` 升级为路径钳制（清洗+规范+保留路径拒绝）。
14. **读写根**：可读 /workspace 全部；可写 /workspace 但拒写 /workspace/input 与 /workspace/skills；收进 SandboxWorkspaceLayout 常量类。

### 第四批：配置面与部署

15. **表结构**：`sandbox_config` 表——id/user_id/name/type/enabled + 通用字段（default_timeout、allow_private_endpoints、network JSON、env_vars JSON、cordon/stale、时间戳）+ 三 provider 块 JSON 列；DDL 双处同步（schema.sql + SchemaInitializer，勿重蹈 agent/agent_run 覆辙）。
16. **凭据引用式**：存 `api_key_ref`（环境变量名）运行时解析，响应脱敏回显 ref 名；不新建加密组件（与 WeKnora 落库加密不同，走 fastrag api_key_ref 惯例）。
17. **API 面**：`/api/sandbox-configs` CRUD + `POST /verify`（探活+列模板）+ `GET /{id}/sandboxes`（库存）；归属者/管理员权限；不引入 workspace-policy。
18. **总开关**：platform-config `sandbox.enabled`（默认 false）；SysConfig > yml（agent.sandbox.* 回退）> 内置默认；per-config enabled 为细粒度第二层。
19. **部署**：fastrag-backend/docker/sandbox.Dockerfile + 根 docker-compose 挂 docker.sock（GID 方案，禁 chmod 666）+ 排障文档；CI 推镜像后置。

### 第五批：集成面

20. **附件暂存**：第一版做——每轮把 uploads 拷入 /workspace/input（只读），AttachmentMiddleware 注入路径改沙箱内路径。
21. **产物收集**：布局保留（镜像内建 output 目录），收集/下载链路后置二期；一期出口 = read_file + execute stdout。
22. **路径迁移**：/home/gem 5 处引用（ContextBuilder:124、SkillReadonlyBackend:25-27、SandboxToolExecutor:75、SandboxBackend:27-32）收敛到 SandboxWorkspaceLayout；listDirectory 缺省改 /workspace。
23. **降级三分支**：总开关关 / 应用未引用或配置禁用 / provider 不可用——全走「Error: …」文本不阻断 run，文本区分原因；E_SANDBOX_DISABLED 拆三条码。
24. **技能执行**：无新机制——激活链路不动，脚本经 execute 落沙箱（镜像自带 python/node/uv）；技能包安装/快照随二期。

## 对既有文档的修订清单（交 /idd-spec / /idd-design / /idd-contract）

- **spec/agent.md**：REQ-H01 修订（绑定键、多后端、配置实体引用；确定性 ID 条款废止）；REQ-H02 修订（TTL 1800s、活动标记机制）；REQ-H03 扩展（降级三分支）；新增 REQ：配置实体 CRUD/验证/库存、绑定存储、孤儿对账、附件暂存、拨号防护。
- **contract/interfaces.md** agent.SandboxLifecycle：现状声明条款随实装重写（门禁①）；idleReaper 删除路径补齐（门禁②）；门禁③由「沙箱 HTTP API 契约」改写为「Engine API 直连 + envd 双适配器契约」；E_SANDBOX_DISABLED 拆三条；技能路径 invariant `/home/gem/skills/{slug}` → `/workspace/skills/{slug}`；虚拟文件系统挂载点 invariant 同步。
- **design/agent.md**：SandboxModule 重设计（多后端缝：SandboxProviderClient 接口 + Docker/Cube/E2B 三适配器；系统边界从「降级桩」改「真容器」）。
- **data.md + schema**：sandbox_config 表契约；agent/agent_run DDL 缺 schema.sql 的勘误一并处理。
- **feature-map.md**：#agent 链勘误（沙箱降级桩→实装）待实现后更新。
- **app-center**：App 实体/契约增 sandbox_config_id 引用。

## 未落（明确后置）

交互终端、图形桌面、技能快照（docker commit）、workspace git checkpoint、会话 rewind、SANDBOX_REBUILT SSE 提醒、产物下载链路、CI 镜像发布。
