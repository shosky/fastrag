# OnlyOffice 在线编辑（onlyoffice-editing）— 架构设计

> 对应 Spec：`spec/onlyoffice-editing.md`（REQ-A01~E06）；契约：`contract/interfaces.md` / `contract/data.md` 的 `## onlyoffice-editing` 节。
> 上游 ADR 引用：**ADR-0001**（策略绑定变更必须触发重新分片）——本设计的保存联动以 `reChunkFile(strategyId=null)` 承接其精神变体「内容变更必须触发重新分片」：编辑保存后的文件内容已偏离既有分片，唯一合法出路是按当前绑定重切（OnlyOfficeServiceImpl.java:334-341 ‹CERTAIN›），绑定裁决归 parse-strategy.ParseStrategyBindingGuard、物理执行归 chunk-management.RechunkOrchestrator，本设计只表达触发。**ADR-0002**（图存储收敛 Neo4j 唯一实现，启动 fail-fast）——与本特性无直接交集（重切后的图谱重建在 chunk-management/graph 链），仅声明无冲突。原 `docs/legacy/adr/` 归档文件已丢失，仅引要点编号。
> **外部系统边界**：OnlyOffice Document Server 是本特性存在理由本身——独立 docker 容器（documentserver:8.3.3，公开 :8082、内部 :8000，独立 Node 进程，自带转换缓存与协同引擎），不是本仓任何 Maven 模块。它与本后端构成 **双向信任关系**（共享密钥 JWT，见缝 2）而非单向依赖：后端签 config 让 OO 验证（正向），OO 回调/拉文件带 token 让后端验证（反向）。docker-compose.yml:175-222 与 application.yml:153-171 是边界契约的配置面。

## 1. Module Map

| 模块 | Responsibility（一行） | Files | Depth Rationale |
|---|---|---|---|
| **EditorSessionModule** | 编辑会话开立与文档身份的唯一裁决点：组装 OO config（key 派生/URL 拼接/角色→mode/整包签名）、登记活跃会话、判定可编辑文件 | `controller/OnlyOfficeController.java`（config 端点段）、`service/OnlyOfficeService.java`、`service/impl/OnlyOfficeServiceImpl.java`（buildEditorConfig/buildDocumentKey/isSupportedFile/signRawFileToken 段 + activeSessionKeys） | **深**：一个 buildEditorConfig 背后是 document.key 派生规则（保存后强制重载机制）、OO 协议字段映射、会话登记的三重职责；调用方（浏览器/前端）只见「拿 config 开编辑器」 |
| **CallbackOrchestrator** | 保存回调的唯一编排点：status 机翻译 → token/key 双因子准入 → 下载新文件 → 覆盖原文件 → Markdown 重落盘 → 触发重分片，且把「部分联动失败」降级为对 OO 的成功应答 | `controller/OnlyOfficeController.java`（callback 端点段）、`service/impl/OnlyOfficeServiceImpl.java`（handleCallback/downloadFromUrl 段）、`model/OnlyOfficeCallbackPayload.java` | **深**：一次 status=6 回调牵动 MinIO 覆盖写、解析重跑、MQ 重切三个下游，误差路径（下载失败/替换失败/辅助降级/未知 status）全部在此收口；OO 只见 error 0/1 |
| **ForceSaveCommander** | 手动保存命令的唯一出口：定位活跃会话 → 组装 CommandService 命令（附签名 token）→ OO 错误码翻译为 result 语义 | `controller/OnlyOfficeController.java`（forcesave 端点段）、`service/impl/OnlyOfficeServiceImpl.java`（forceSave/postCommand 段） | **深（候选 1）**：与 EditorSessionModule 共享会话登记（forceSave 依赖 buildEditorConfig 的登记），两者实为同一「编辑会话生命周期」的发起端与消费端（见 §4 深化） |
| **OoTrustTokenModule** | OO 双向信任令牌的唯一签发/验签点：标准 JWS(HS256)、payload 整体编码为 subject claim、raw token 的 scope 三元组、300s 时钟漂移容忍 | `fastrag-security/src/main/java/com/fastrag/security/util/OnlyOfficeJwtUtil.java` | **深**：与 OO Node 端 jsonwebtoken 的格式互操作细节（3 段 JWS 而非 2 段——实测踩坑记录在 javadoc :32-36）全部隐藏；消费方只见 sign/verify |
| RawFileServing **(S)** | raw 端点的薄服务面：token 三元组校验 → MinIO 流式回源（media type 映射/inline/no-store） | `controller/OnlyOfficeController.java`（rawFile/mediaTypeFor 段 :118-159,193-207） | 浅：验签 + 透传 MinIO 流，无隐藏状态；建议维持现状不独立成类，若补 key 校验/限流再深化（见 §4） |
| （共享边界，非本特性模块）FileService.replaceOriginalFile / reChunkFile、MinioService、SecurityConfig permitAll 片段 | 保存落盘、重分片触发、对象存储、免登录白名单 | `FileService.java`/`FileServiceImpl.java`、`infra/.../MinioService.java`、`SecurityConfig.java:71-74` | 按宪法归上游/共享层；本链是消费方，白名单语义在 `## iam` 节 SecurityGate 契约已登记 |

## 2. Interface Contracts

### EditorSessionModule

**Public interface（OnlyOfficeService，OnlyOfficeService.java:22-69）：**
- `buildEditorConfig(kbId, fileId, userId, userName, editable) → Map` — 组装 OO config（含整包签名与会话登记）
- `buildDocumentKey(fileId, updatedAt) → string` — 文档身份派生（SHA-256 前 32 hex）
- `signRawFileToken(kbId, fileId) → string` — document.url 的查询 token（scope=onlyoffice-raw，1800s）
- `isSupportedFile(fileName) → boolean` — 六扩展名 + 开关联合判定

**Hidden implementation：** document.key 派生与异常回退、EXT_TO_DOC_TYPE/EXT_TO_FILE_TYPE 双映射、activeSessionKeys 登记时机与消费约定（forcesave/callback 两处读取）、config 字段全集（permissions/customization/documentType）、jwtEnabled 分支签名、URL 前缀拼接（storage/callback-base-url 分离）。

**Depth rationale：** 浏览器拿到 Map 直接喂 `DocsAPI.DocEditor`；「为什么保存后能强制重载新版本」（key 含 updatedAt）与「为什么同一会话二次保存不被拒」（会话 key 登记）两个机制性问题全部藏在模块内。

### CallbackOrchestrator

**Public interface（语义面）：**
- `handleCallback(kbId, fileId, OnlyOfficeCallbackPayload) → Map{error, actions?}` — 保存回调编排唯一入口（OnlyOfficeService.java:46）

**Hidden implementation：** status 六值分发（1/4 ack、3/7 拒绝、2/6 编排、未知容忍）、双因子准入（token claim 比对 + expectedKey/sessionKey 二选一 + null key 容忍）、downloadFromUrl 超时与 HTTP≥400 判定、replaceOriginalFile→解析→Markdown→re-chunk 的四步序与双层降级（辅助步骤失败不改应答）。

**Depth rationale：** 「用户在浏览器点了一次保存」到「MinIO 原件更新 + 分片重建消息入队」的全部复杂度（含与 OO 的异步解耦：不等重切完成即应答）收在一个方法内；重切细节外泄为零。

### ForceSaveCommander

**Public interface（语义面）：**
- `forceSave(kbId, fileId) → Map{result, ooError?, message?}` — 手动保存命令（OnlyOfficeService.java:68）

**Hidden implementation：** 会话 key 定位（登记优先/派生退化）、CommandService 表单协议（c=forcesave&key=…&token=…）、OO error 0/4/1/其他 → initiated/no-changes/no-session/failed 映射、disabled/no-file 前置守卫、HTTP 异常降级。

**Depth rationale：** 前端「保存」按钮只认 result 字符串；CommandService 协议、命令 token 镜像 payload（{c,key}）、错误码语义全部隐藏。

### OoTrustTokenModule

**Public interface（OnlyOfficeJwtUtil，OnlyOfficeJwtUtil.java:65-117）：**
- `signPayload(payload, ttlMillis) → string` — 任意对象签为标准 JWS（JSON 作 subject claim）
- `signRawFileToken(kbId, fileId, ttlSeconds) → string` — 带 scope 三元组 claims 的 raw 专用 token
- `verify(token) → Map` — 验签并反解 subject；失败抛 OnlyOfficeTokenException

**Hidden implementation：** HS256 密钥派生、subject-as-JSON 编码约定（与 OO Node jsonwebtoken 互操作）、exp/iat 写入、300s clockSkew、异常归一（JwtException→OnlyOfficeTokenException）。

**Depth rationale：** 三处消费（config 签名/raw 验签/callback 验签）+ 一处对端（OO 验 config）共用同一密钥与格式；「为什么是 3 段 JWS」的踩坑史不外泄。

### RawFileServing (S)

**Public interface（HTTP 面）：** `GET /api/kb/{kbId}/files/{id}/onlyoffice/raw?token=…`（permitAll + 上述 token 校验 + MinIO 流式回源）。暴露面列出仅为本节完整，按端点排除铁律不立契约。

**Depth rationale：** 无（浅模块，维持现状）。

## 3. Seam Diagram

**缝 1：Document Server 协议适配缝（外部系统边界）**
- What varies：OO 版本与协议细节——8.3.3 vs 7.4（镜像可回滚，docker-compose.yml:176-179）、JWT on/off 双模、CommandService 端点（.ashx / ≥8.3 可切 /command）、回调 status 语义、config 字段约定
- Adapters（4+，真缝）：① EXT_TO_DOC_TYPE/EXT_TO_FILE_TYPE 字段映射；② jwtEnabled 双模分支（签名/验签/命令 token 三处同步切换）；③ command-path 配置化；④ callback status 机翻译（OO 六值 → 后端三动作）
- Stable side：后端文件域（FileService/MinioService/kb_file）——OO 如何表达「该保存了」不影响存储语义
- Placement rationale：外部系统是变更主源（镜像升级记录在 docker-compose 注释），协议差异必须隔离在网关模块内，文件域零感知

**缝 2：OO 信任令牌缝（双向信任）**
- What varies：信任凭证的方向与形态——正向（config 整包签名、命令 token）与反向（raw 查询 token、callback body token）；JWT 全局开关
- Adapters（2+，真缝）：signPayload（对 OO Node jsonwebtoken 的格式适配：subject-as-JSON）、verify（leeway 容忍 + OnlyOfficeTokenException 归一）
- Stable side：EditorSessionModule / CallbackOrchestrator / ForceSaveCommander——只调 sign/verify，不知密钥来源与格式史
- Placement rationale：密钥与格式是与 OO 的共享秘密（双端同步），收敛到 fastrag-security 单点，防散落；免登录白名单本身归 iam.SecurityGate 契约（不在此重复）

**依赖声明（假设缝，不立缝）：** `FileService.replaceOriginalFile`（单实现——保存落盘是 file-ingestion 既有能力的复用，无变化维度）；`FileService.reChunkFile(null)`（单实现——重切物理语义归 chunk-management.RechunkOrchestrator 契约，本链纯触发方）；`MinioService`（infra 单实现）。

## 4. Deepening Opportunities

- **EditorSessionModule + ForceSaveCommander → 合并为「编辑会话生命周期」单一深模块**：两者共享 activeSessionKeys（config 登记写、forcesave 定位读、callback 兼容读），现状分居三个方法段；合并后可把会话状态机（开立→编辑中→保存→关闭）显式化，并把 activeSessionKeys 迁入 Redis（TTL 逐出 + 多实例一致——修复 NFR-4 单实例语义）。
- **CallbackOrchestrator**：① 下载通道抽为可注入缝（HttpURLConnection 硬编码 :363-378 不可测、且无 SSRF 主机白名单——jwtEnabled=false 时下载 URL 完全由回调方控制）；② 按 key+status 的判重去抖（现重复回调重复执行，REQ-D07）；③ payload.users/history 的版本留痕消费（spec Out of Scope 待产品决策）。
- **RawFileServing (S)**：补 document.url 中 key 参数的校验（消除 spec REQ-E06 声明-实现差距）；若多实例化，raw 侧无状态不受影响，无需深化。
- **角色解析收敛**：OnlyOfficeController.resolveCurrentUserRole（:182-191）用权限字符串近似 KB 角色，与 iam.KbAccessChecker 的 ACL/组织兜底判定不同源——KB 内 editor 用户的会话 JWT 若无 kb:* 权限会被误判 viewer。收敛为直接消费 KbAccessChecker.resolveRole（与 @KbAuth 同源）。

## 5. Parent Trace

| Design 模块/缝 | Spec REQ |
|---|---|
| EditorSessionModule | REQ-A01~A06、REQ-B01、REQ-B03 |
| RawFileServing (S) | REQ-B02、REQ-E06 |
| ForceSaveCommander | REQ-C01~C04 |
| CallbackOrchestrator + 缝 1（status 翻译） | REQ-D01~D07 |
| OoTrustTokenModule + 缝 2（双向信任） | REQ-E02、REQ-E03 |
| SecurityConfig 白名单（iam 共享边界） | REQ-E01 |
| EditorSessionModule（角色映射段） | REQ-A04、REQ-E05 |
| CallbackOrchestrator（双因子准入段） | REQ-E04 |
| 缝 1（协议适配） | REQ-A03、REQ-D02、REQ-C02（command-path） |
| 依赖声明：reChunkFile（ADR-0001 承接） | REQ-D05 |
| 依赖声明：replaceOriginalFile / MarkdownService | REQ-D04、REQ-D06 |
| NFR-1~NFR-5 | 各模块注解面/配置面（不立新模块） |
| ForceSaveCommander | REQ-C04（2026-09-24 review 补锚：forcesave 只发起不等待——受理后由 Document Server 以 status=6 回调完成落盘与重分片） |

## 6. ADR

**ADR-OO1（本链登记）：免登录面以「OO 自签 JWT + document.key」双因子替代用户 JWT。**
- **背景**：Document Server 是服务端对服务端调用（拉原文件、保存回调），无法携带前端用户 JWT；但 raw/callback 位于 `/api/kb/**`——全后端权限模型的覆盖范围内。
- **取舍**：在 SecurityConfig 为两路径开 permitAll 白名单（SecurityConfig.java:73-74），安全责任移交控制器层双因子校验（token 验签 + document.key 文档身份）；代价是 JWT 关闭时（开发现状）准入退化为单因子 key 校验、且 key=null 容忍进一步放宽（OnlyOfficeServiceImpl.java:293-295）。
- **理由**：难逆转（白名单 + OO 集成约束是结构性的）+ 费解（`/api/kb` 下为何存在免认证端点，新人必问）+ 真权衡（集成可行性 vs 统一权限模型的纯洁性）三者齐备，故记录。**配套约束**：生产环境必须 jwt-enabled=true 且密钥与 OO 容器同步（NFR-2），key=null 容忍与 raw 不校验 key 两处宽松面待收紧（spec REQ-E04/E06 债务）。

## ADR 引用指针

本文所引 **ADR-0001**（解析策略绑定变更必须触发重新分片）与 **ADR-0002**（图存储收敛 Neo4j——GraphStore 唯一实现、启动 fail-fast）原文已随根 docs/ 树删除丢失，全文重构版见 [adr/0001-strategy-binding-requires-rechunk.md](adr/0001-strategy-binding-requires-rechunk.md) 与 [adr/0002-graph-store-consolidate-on-neo4j.md](adr/0002-graph-store-consolidate-on-neo4j.md)（2026-09-24 由链内要点源重构，不可考处 [GAP] 标注）；本文正文「仅引要点」处即指该两文。
