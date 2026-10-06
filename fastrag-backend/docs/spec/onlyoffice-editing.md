# OnlyOffice 在线编辑（onlyoffice-editing）— 需求规格

## 1. 特性与范围

OnlyOffice 在线编辑特性把外部 OnlyOffice Document Server（下称 Document Server，docker 镜像 8.3.3，:8082）接入知识库文件域：浏览器经本特性签发的编辑器配置在 Document Server 中在线渲染并编辑知识库内的六种 Office 文档（doc/docx/xls/xlsx/ppt/pptx），Document Server 从后端拉取 MinIO 原文件、在用户保存/强制保存时回调后端，后端完成「下载新文件 → 覆盖 MinIO 原件 → Markdown 重落盘 → 触发重新分片（Re-chunk）」的联动闭环，使编辑后的文档内容与知识分片保持一致。显式排除：MinIO 对象读写与 Markdown 落盘的实现（归 file-ingestion 的 StorageServiceImpl/MarkdownService，本特性为消费方）、重分片的绑定裁决与物理执行（归 parse-strategy.ParseStrategyBindingGuard 与 chunk-management.RechunkOrchestrator，本特性只表达「按当前绑定重切」）、前端编辑器页面与 OO 选区桥插件（插件源码在前端仓 `public/onlyoffice-plugin/`，后端仅在 docker-compose 挂载托管）、用户认证体系本身（归 iam，本特性定义其免登录面的替代信任机制）。

## 2. Out of Scope

- **存储与解析实现**：原文件如何上传/下载/覆盖（StorageServiceImpl、replaceOriginalFile）与 Office 文档如何解析出 Markdown（DocumentParser）——归 file-ingestion。本链只声明调用关系与「替换后 updatedAt 必变」这一依赖（FileServiceImpl.java:308-309 ‹CERTAIN›）。
- **重分片语义**：换绑校验、删 auto 留 manual、对账重排——归 parse-strategy/chunk-management（ADR-0001）。本链只要求「保存后触发 strategyId=null 的 re-chunk」（OnlyOfficeServiceImpl.java:337 ‹CERTAIN›）。
- **Document Server 内部**：协同编辑算法、文档格式转换缓存（/cache/files 与 secure_link 防盗链）、OO 侧重试策略 〔决议 2026-09-24：按 OO Document Server 官方行为口径——callback 应答非 `{error:0}` 时 DS 在协同会话保持期内按内部策略重试（间隔递增），放弃后文档留在会话中待下次保存；本后端契约不依赖重试语义，error=1 即视该次保存未落地。理由：DS 为外部容器（docker-compose.yml:175-213，8.3.3），其内部行为不受本仓控制，按官方文档口径取值；现状差距：无（Out of Scope 维持，仅作联调排障口径）（代裁，用户可推翻）〕、OO 插件（选区桥/分片边界）的前端实现。
- **权限体系**：@KbAuth 判定协议归 iam（复用，不重新定义）；本链只定义「viewer 看 / editor 改」的映射与免登录面的替代信任。
- **历史版本与协作**：回调 payload 中的 users/history/actions 字段仅声明接收、暂不消费（OnlyOfficeCallbackPayload.java:60-76 ‹CERTAIN›）〔决议 2026-09-24：取「仅声明接收、不消费、无排期」——payload 的 users/history/actions 字段保持反序列化兼容（前向兼容 OO 报文），版本留痕产品化不在任何链路排期。理由：最小改动原则，且字段消费需版本存储与 UI 配套，属独立新特性；现状差距：无（Out of Scope 维持）（代裁，用户可推翻）〕。
- **渲染降级路径**：`onlyoffice.enabled=false` 时前端回退 mammoth/xlsx/pptx-preview 渲染——前端行为，后端仅提供开关（application.yml:151-152 注释 ‹CERTAIN›）。

## 3. 领域术语

引用 `fastrag-backend/CONTEXT.md`：**知识库（Knowledge Base）**、**知识分片（Chunk）**、**解析策略（Parse Strategy）**、**策略绑定（Strategy Binding）**、**重新分片（Re-chunk）**、**Ingestion Pipeline**、**统一响应（ApiResponse）**。本特性引入的集成术语（仅本链文档使用）：**Document Server**（外部 OO 容器服务）、**document.key**（文档编辑会话身份，fileId+updatedAt 派生）、**forcesave**（OO CommandService 强制保存命令）、**免登录面**（raw/callback 两个不要求用户 JWT 的端点）。

## 4. 功能需求

### 4.1 编辑器配置签发（config）

浏览器打开文档预览/编辑时，向后端请求编辑器配置；后端按文件与当前用户角色组装 Document Server 官方约定的 config 对象（含 document.key、document.url、callbackUrl），整个 config 以 OO 共享密钥签名后由前端直传 `DocsAPI.DocEditor`。document.key 的派生规则是「保存后强制重载新版本」机制的根基。

- **REQ-A01**：特性有全局启用开关 `onlyoffice.enabled`；关闭时 config 与 forcesave 端点以业务错误（body.code=503）拒绝服务，`isSupportedFile` 恒返回 false。〔证据：OnlyOfficeController.java:61-62,71-73,103-105、OnlyOfficeServiceImpl.java:129-131 ‹CERTAIN›〕
- **REQ-A02**：仅 doc/docx/xls/xlsx/ppt/pptx 六种扩展名受支持；config 请求对文件不存在（404）或不支持类型（400）拒绝。〔证据：OnlyOfficeServiceImpl.java:74-75,172-175、OnlyOfficeController.java:74-81 ‹CERTAIN›〕
- **REQ-A03**：config 返回 Document Server 直接可消费的完整对象：document{fileType,key,title,url,permissions} + editorConfig{callbackUrl,mode,user,lang=zh-CN,customization{autosave,forcesave,compactHeader,toolbar}} + documentType/type（word/cell/slide 按扩展名映射）+ 宽高。〔证据：OnlyOfficeServiceImpl.java:186-216 ‹CERTAIN›〕
- **REQ-A04**：编辑权限由 KB 角色决定：editor/owner 得 mode=edit 且 permissions.edit=true；viewer 得 mode=view。customization 强制开启 autosave 与 forcesave（防用户忘记保存）。〔证据：OnlyOfficeController.java:87-89、OnlyOfficeServiceImpl.java:191-195,199,204-208 ‹CERTAIN›；角色解析实现为权限近似而非 KBRole 直读——见 REQ-E05 债务〕
- **REQ-A05**：document.key = SHA-256(fileId + updatedAt) 的前 32 个十六进制字符（异常时退化为 `fileId_当前毫秒`）：文件被保存替换后 updatedAt 变化 ⇒ 下次打开派生新 key ⇒ Document Server 强制重载新文件，防浏览器缓存旧版本。〔证据：OnlyOfficeServiceImpl.java:145-162 及 javadoc :53-59 ‹CERTAIN›〕
- **REQ-A06**：config 签发时把 fileId→documentKey 登记为活跃编辑会话，供 forcesave 定位会话、callback 兼容「同一会话内多次保存」（首次保存后 updatedAt 已变而会话 key 不变）。〔证据：OnlyOfficeServiceImpl.java:123-126,178-179 ‹CERTAIN›〕

### 4.2 原文件供拉取（raw）

Document Server 服务端（非浏览器）按 config.document.url 拉取原始文件；它无法携带用户 JWT，因此以一次性查询 token 证明身份，后端从 MinIO 取原文件流式返回。

- **REQ-B01**：document.url 指向后端 raw 端点并携带 token 与 key 双查询参数（OO 7.1+ 约定：token 防滥用、key 校验文档身份）。〔证据：OnlyOfficeServiceImpl.java:180-183 ‹CERTAIN›〕
- **REQ-B02**：raw 端点校验 token：签名有效且 claims 满足 scope=onlyoffice-raw、kbId/fileId 与路径一致，否则 401（验签失败）/403（不匹配）；通过后从 MinIO 按 objectKey 拉取原文件，以 inline Content-Disposition、no-store 缓存、按扩展名映射的 media type 流式返回；文件不存在 404、MinIO 异常 500。〔证据：OnlyOfficeController.java:118-158 ‹CERTAIN›〕
- **REQ-B03**：下载 token 有效期 1800 秒。〔证据：OnlyOfficeServiceImpl.java:140-142 ‹CERTAIN›〕

### 4.3 强制保存（forcesave）

页面「保存」按钮不直接写库，而是向 Document Server 的 CommandService 下发 forcesave 命令；Document Server 受理后以 status=6 回调 callback，真正的落盘发生在回调编排中——发起与落盘解耦。

- **REQ-C01**：forcesave 端点面向浏览器，editor/owner 可调用，viewer 以 403 语义拒绝（「viewer 角色无编辑权限，无法保存」）。〔证据：OnlyOfficeController.java:100-112 ‹CERTAIN›〕
- **REQ-C02**：强制保存命令受理后，Document Server 的强制保存回调到达后端并触发重分片（报文契约见 contract/interfaces.md onlyoffice-editing 节 ForceSaveCommander）。〔证据：OnlyOfficeServiceImpl.java:395-407,431-459 ‹CERTAIN›〕
- **REQ-C03**：命令结果映射为 result 语义：OO error 0→initiated（已受理）/4→no-changes（无修改）/1→no-session（会话不存在）/其他→failed（附 ooError）；未启用→disabled、文件不存在→no-file、命令请求异常→failed。〔证据：OnlyOfficeServiceImpl.java:383-423 ‹CERTAIN›〕
- **REQ-C04**：forcesave 只发起不等待：受理后由 Document Server 以 status=6 回调 callback 完成落盘与重分片。〔证据：OnlyOfficeController.java:95-98 javadoc、OnlyOfficeService.java:59-67 javadoc ‹CERTAIN›〕

### 4.4 保存回调处理与重分片联动（callback）

Document Server 在编辑生命周期各时点回调 callback（status 1/2/3/4/6/7）；status=2（关闭前保存）与 status=6（强制保存，autosave/手动 forcesave 均触发）是仅有的两个需要落盘的状态。保存编排 = 校验（token+key）→ 下载新文件 → 覆盖 MinIO 原件 → Markdown 重落盘 → 触发重分片，前三步是保存事实，后两步是检索一致性联动（ADR-0001：内容变了必须重切，不允许「已编辑未重切」的漂移态长期存在）。

- **REQ-D01**：callback 响应遵循 OO 协议 `{error: 0|1}`（可选 actions），不包 ApiResponse——Document Server 是机器调用方，不受统一响应宪法约束。〔证据：OnlyOfficeController.java:161-175、OnlyOfficeCallbackPayload.java:25 javadoc ‹CERTAIN›〕
- **REQ-D02**：status 分发：1/4（编辑中/关闭无修改）→ ack error=0 且零副作用；3/7（保存出错）→ error=1 拒绝；2/6 → 执行完整保存编排；未知 status → warn 记账 + error=0。〔证据：OnlyOfficeServiceImpl.java:242-255,352-355 ‹CERTAIN›〕
- **REQ-D03**：status=2/6 时下载 URL 必填：缺失（blank）或下载失败（HTTP ≥400/网络异常）→ error=1 拒绝，不触碰存储。〔证据：OnlyOfficeServiceImpl.java:305-317 ‹CERTAIN›〕
- **REQ-D04**：保存编排固定四步：① 下载 Document Server 临时存放的新文件（短时有效 URL，30s 连接/60s 读超时）；② replaceOriginalFile 覆盖 MinIO 原对象并更新 size/updatedAt（回收站文件拒绝替换）；③ 用新字节重新解析并以文件当前解析策略落盘 Markdown（失败仅告警）；④ 调 re-chunk（strategyId=null 沿用当前绑定，MQ 异步、不等执行完成）后应答 error=0。〔证据：OnlyOfficeServiceImpl.java:319-349,363-378、FileServiceImpl.java:280-314,290-292、MarkdownService.java:52-70 ‹CERTAIN›〕
- **REQ-D05**：重分片联动承接 ADR-0001：编辑保存必然触发「按当前策略绑定重切」，不存在「内容已编辑、分片仍旧」的合法终态；manual 分片保留语义归 chunk-management.RechunkOrchestrator（本链不重复定义）。〔证据：OnlyOfficeServiceImpl.java:334-341、FileService.java:55 javadoc ‹CERTAIN›〕
- **REQ-D06**：replaceOriginalFile 成功后，Markdown 重落盘与重分片触发任一失败都不改变对 OO 的成功应答（仍 error=0）——已覆盖的原文件是保存事实源，检索一致性可由人工 re-chunk 补救。〔证据：OnlyOfficeServiceImpl.java:319-346 ‹CERTAIN（2026-09-24 深证：仅 replaceOriginalFile 失败置 error=1（:342-346）；Markdown 重落盘/重分片失败仅 warn 并照常 ack，注释明示「失败仅告警，不阻塞主流程」:325、「即使 MQ 不可用也能 ack 给 OO」:334-335——「保存事实优先、检索联动可补救」的分层是有意设计）〕
- **REQ-D07**：回调幂等语义：同一编辑会话内允许多次有效保存回调（autosave 周期触发 status=6 + 手动 forcesave + 关闭时 status=2），每次都完整执行保存编排；重复执行最终一致无害（覆盖同内容、重切为确定性操作），但现状无按 key/status 的判重去抖——重复回调以重复资源消耗为代价换取正确性。〔证据：OnlyOfficeServiceImpl.java:256-350 无判重逻辑（结构观测）、:124-125 与 :281-283 注释明示同会话多次保存须各自受理而非误拒 ‹CERTAIN（2026-09-24 深证）›；「最终一致无害」由结构坐实——MinIO 覆盖写幂等，重切为「删全部 auto 分片后重建」（FileServiceImpl.java:421-436），同内容重复执行终态收敛〕

### 4.5 免登录面安全与双向信任（JWT）

raw/callback 是 `/api/kb/**` 中仅有的两个不要求用户登录态的端点——因为调用方是 Document Server 容器（服务端对服务端），无法携带用户 JWT。替代信任 = 三处同密钥 JWT（config 签名 / raw 查询 token / callback body token）+ document.key 文档身份校验；JWT 可全局关闭（与 OO 容器 token.enable 同步），关闭时 document.key 校验仍保留为最后防线。

- **REQ-E01**：raw/callback 两端点在 SecurityConfig 显式 permitAll（全后端 `/api/kb/**` 唯一的免登录面），白名单外的 `/api/**` 其余端点不受影响仍要求认证。〔证据：SecurityConfig.java:71-74,77 ‹CERTAIN›〕
- **REQ-E02**：双向信任令牌为标准 3 段 JWS（HS256），密钥 `onlyoffice.jwt-secret` 与 Document Server `JWT_SECRET` 同源共用：config 整体签名由 Document Server 验证（正向），raw/callback token 由后端验证（反向）；验签容忍 300 秒时钟漂移。〔证据：OnlyOfficeJwtUtil.java:24-29,38-41,52,65-78,97-117 ‹CERTAIN›〕
- **REQ-E03**：JWT 开关 `onlyoffice.jwt-enabled` 必须与 OO 容器 token.enable 双端同步：false 时 config 不签名、callback 跳过 token 校验（OO 关闭 JWT 后回调不携带 token 字段）；document.key 校验不受开关影响始终执行。〔证据：OnlyOfficeServiceImpl.java:107-113,219-222,262-279 ‹CERTAIN›；开发环境现状 jwt-enabled=false〔application.yml:164-167、docker-compose.yml:188-189 ‹CERTAIN›〕〕
- **REQ-E04**：callback 的保存准入为双因子：① token（jwtEnabled 时）验签且 kbId/fileId claim 与路径一致；② document.key 等于「当前 updatedAt 派生 key」或「登记的活跃会话 key」之一；任一不满足 → error=1 拒绝；**key 字段缺失（null）时容忍通过**（宽松面，登记债务）。〔证据：OnlyOfficeServiceImpl.java:262-302 ‹CERTAIN›〕
- **REQ-E05**：浏览器面两端点（config/forcesave）走标准 @KbAuth（viewer 门槛）+ 会话权限近似角色映射：permissions 含 `*` 或 kb:manage/kb:write/kb:edit 视为 editor，否则 viewer；未登录上下文按 viewer 处理。〔证据：OnlyOfficeController.java:68,100,182-191 ‹CERTAIN›；javadoc「KbAuthAspect 已把角色存入 LoginUser」与实现不符（切面只读 LoginUser 不写角色）——注释失真登记债务，KbAuthAspect.java:64 ‹CERTAIN›〕
- **REQ-E06**：raw 下载的安全边界 = token 三元组校验（scope/kbId/fileId），下载动作本身不经 document.key 复核——组装侧把 key 放入 document.url 意在「文档身份校验」，但服务端未消费该参数，两者存在声明与实现的差距（登记债务）。〔证据：OnlyOfficeController.java:118-137 仅校验 token、OnlyOfficeServiceImpl.java:180-183 组装带 key ‹CERTAIN›；「意图」出自代码注释〕

## 5. 非功能约束（仅代码/配置可证）

- **NFR-1（响应形态豁免）**：raw 返回原始字节流（ResponseEntity）、callback 返回 OO 协议 Map——两免登录端点豁免统一响应 ApiResponse 包装；浏览器面两端点仍走 ApiResponse。〔证据：OnlyOfficeController.java:118-159,163-175 vs :70-93,100-112 ‹CERTAIN›〕
- **NFR-2（密钥同源配置）**：jwt-secret 双端（后端/Document Server 容器）必须配置同值；开发缺省值 `fastrag-onlyoffice-dev-secret-change-me` 不得带入生产。〔证据：OnlyOfficeJwtUtil.java:48-49、application.yml:162-163、docker-compose.yml:187 ‹CERTAIN›〕
- **NFR-3（外部调用超时预算）**：回调下载 30s 连接/60s 读；CommandService 命令 10s 连接/30s 读。〔证据：OnlyOfficeServiceImpl.java:367-368,436-437 ‹CERTAIN›〕
- **NFR-4（会话登记单实例语义）**：活跃会话表为进程内 ConcurrentHashMap（fileId→documentKey，登记后无逐出）：多实例部署下 forcesave 定位与同会话 key 兼容失效；条目无清理存在缓慢累积。〔证据 ‹CERTAIN（2026-09-24 深证：OnlyOfficeServiceImpl.java:126,179,292,396——activeSessionKeys 全文件仅 put/get，无 remove/逐出/持久化，多实例失效与缓慢累积的影响评估为结构直读事实）〕
- **NFR-5（容器拓扑假设）**：callback-base-url/storage-base-url 缺省指向 `host.docker.internal:8081`（Document Server 容器视角回访宿主后端），OO 容器需 SSRF 放行私有 IP；fastRAG → OO 走 :8082 公开端口，拉取保存文件走 :8000 内部端口。〔证据：application.yml:168-171、docker-compose.yml:181-183,200-213 ‹CERTAIN›〕
- **性能阈值**：config 签发/回调保存/重分片联动的时延预算 〔决议 2026-09-24：取保守预算——config 签发 P95 ≤ 500ms（1 次 DB 查询 + SHA-256 + JWT 签名）；callback 保存编排同步段（下载+MinIO 覆盖+重落盘+MQ 投递）P95 ≤ 5s，硬防线即既有 30s/60s 下载超时（NFR-3）；重分片异步完成不设同步 SLA（MQ 解耦，ADR-0001 仅约束最终一致）。理由：业界惯例（交互配置秒级可用、机器回调须在 DS 重试窗内应答）+ 与 NFR-3 一致；现状差距：无埋点与阈值断言，登记债务——随 tdd/onlyoffice-editing.md 测试补齐时一并建立（代裁，用户可推翻）〕。
- **文档大小上限**：可在线编辑文档的字节上限 〔决议 2026-09-24：取 200MB——与全仓文件域唯一上传上限对齐（application.yml:61-62 multipart max-file-size/max-request-size=200MB），在线编辑不设更严的独立校验，超限由容器层自然拒绝。理由：本仓一致性（避免「能上传不能编辑」的边界错位）；现状差距：代码无显式校验，依赖容器层约束——登记为观察项而非缺陷（代裁，用户可推翻）〕。

## 6. 验收清单

- [ ] enabled=false 时 config/forcesave 返回 body.code=503 的业务错误，HTTP 仍 200（REQ-A01）
- [ ] viewer 打开文档得 mode=view，editor/owner 得 mode=edit 且 permissions.edit=true（REQ-A04）
- [ ] 同一文件未保存时多次打开 config，document.key 恒定；保存替换后 key 改变（REQ-A05）
- [ ] raw 请求无 token/坏签名返回 401，token 与路径 kbId/fileId 不符返回 403，匹配时返回原文件字节流（REQ-B02）
- [ ] viewer 调 forcesave 被拒（403 语义）；editor 调用后 OO 受理得 result=initiated，随后收到 status=6 回调（REQ-C01、REQ-C04）
- [ ] status=2/6 回调：token 与 key 双校验通过 → MinIO 原对象被新文件覆盖、size/updatedAt 更新、parsed.md 重新落盘、重分片消息已投递 → 应答 error=0（REQ-D02、REQ-D03、REQ-D04）
- [ ] 同一会话内第二次保存（key=会话 key 而 updatedAt 已变）不被误拒（REQ-A06、REQ-E04）
- [ ] 坏签名 token / claims 不匹配 token / 不认识的 document.key 的回调均被 error=1 拒绝且存储零改动（REQ-E04、REQ-D02）
- [ ] status=1/4 回调 ack error=0 且 MinIO/MySQL/MQ 零交互；status=3/7 返回 error=1（REQ-D02）
- [ ] 下载 URL 缺失或下载失败时 error=1，MinIO 原对象保持不变（REQ-D03）
- [ ] Markdown 重落盘或重分片触发失败时，回调仍应答 error=0 且原文件替换已生效（REQ-D06）
- [ ] jwt-enabled=false 时无 token 的回调仍可完成保存（document.key 校验兜底），config 不含 token 字段（REQ-E03）
- [ ] 不支持扩展名的 config 请求被 400 拒绝、文件不存在被 404 拒绝（REQ-A02）
- [ ] config 返回 Document Server 直接可消费的完整对象（document/editorConfig/documentType/宽高齐备）（REQ-A03）
- [ ] document.url 携带 token 与 key 双查询参数，token 有效期 1800 秒（REQ-B01、REQ-B03）
- [ ] 强制保存回调到达后端并触发重分片（REQ-C02）
- [ ] OO error 0/4/1/其他分别映射 initiated/no-changes/no-session/failed，未启用/文件不存在/请求异常各得其 result（REQ-C03）
- [ ] callback 应答为 OO 协议 {error: 0|1} 而非统一 ApiResponse 包装（REQ-D01）
- [ ] 编辑保存必然按当前策略绑定触发重切，不存在「内容已编辑、分片仍旧」终态（REQ-D05）
- [ ] 同一会话内 autosave 周期保存、手动 forcesave 与关闭保存多次回调均完整执行编排（无判重现状断言）（REQ-D07）
- [ ] raw/callback 在白名单内免登录可达，白名单外其余 /api/** 仍要求认证（REQ-E01）
- [ ] raw/callback token 验签通过，篡改或超 ±300 秒时钟漂移被拒，密钥与 OO 容器同源（REQ-E02）
- [ ] 浏览器面 config/forcesave 经 @KbAuth + 权限近似角色映射（`*`/kb:manage/kb:write/kb:edit 视为 editor，未登录按 viewer）（REQ-E05）
- [ ] raw 下载仅校验 token 三元组、不做 document.key 复核（声明与实现差距现状断言）（REQ-E06）

## 7. 测试策略

经由公共接口（OnlyOfficeService 六方法 + OnlyOfficeJwtUtil 三方法 + 免登录面端点行为）测试，优先级：

1. **callback 保存编排与幂等族**（handleCallback，最高价值）：status 分发、双因子校验（token/key/会话 key/null key）、下载失败拒绝、替换成功后辅助步骤降级、同会话多次保存、重复 status=6 重复执行无害性——mock 边界：FileService、DocumentParser、MinioService、下载 HTTP 通道。现状**零测试**，最大缺口。
2. **令牌双向信任**（OnlyOfficeJwtUtil）：签发-验签往返、篡改/过期/空 subject 拒绝、scope claim、时钟漂移容忍——纯函数级，无 mock。
3. **config 组装**（buildEditorConfig/buildDocumentKey）：角色→mode 映射、key 派生稳定性与变化性、jwtEnabled 分支、六扩展名映射与拒绝。
4. **forcesave 命令**（forceSave）：ooError→result 四映射、disabled/no-file 前置、命令 token 附带、HTTP 异常降级——mock CommandService HTTP 边界。
5. **免登录面安全**（端点级）：permitAll 白名单精确性（config/forcesave 不在白名单）、raw 三态（401/403/200）。
6. **HITL 人工确认**：生产环境 jwt-enabled=true + 密钥轮换流程的接受度（开发现状 false 的安全权衡）；document.key=null 容忍与 raw 不校验 key 两处宽松面的收紧决策。
