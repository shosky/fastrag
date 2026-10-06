# OnlyOffice 在线编辑（onlyoffice-editing）— 测试设计

> 契约来源：`contract/interfaces.md` / `contract/data.md` 的 `## onlyoffice-editing` 节。mock 纪律：只允许在真缝/系统边界 mock——FileService（file-ingestion 契约边界）、DocumentParser/MarkdownService（解析与 MinIO 能力边界）、KbFileMapper（数据边界，沿用既有链「mock mapper 构造」惯例）、下载与 CommandService 两条 HTTP 通道（用本地 `com.sun.net.httpserver.HttpServer` stub 真实出网 localhost，免改源码）；**OoTrustToken 用真实 OnlyOfficeJwtUtil（配测试密钥），不 mock**——令牌正确性本身是被测对象。禁 mock 内部模块。

## 1. 切片规划

| # | 切片 | 契约接口 | AFK/HITL | 说明 |
|---|---|---|---|---|
| S0 | 免登录面安全策略人工确认 | OoTrustToken / CallbackSaveOrchestrator | **HITL** | 生产 jwt-enabled=true 的强制化与密钥轮换流程；key=null 容忍、raw 不校验 key 两处宽松面的收紧决策——先于人确认再固化断言 |
| S1 | 双向信任令牌 | OoTrustToken | AFK | 纯 JWT 工具，真实实现零外部依赖 |
| S2 | 编辑会话开立 | EditorSessionService | AFK | config 组装 + key 派生 + 会话登记 |
| S3 | 保存回调编排（**幂等用例族为重中之重**） | CallbackSaveOrchestrator | AFK | status 分发/双因子准入/四步编排/降级——**现状零测试，最大缺口** |
| S4 | 强制保存命令 | ForceSaveCommander | AFK | CommandService 协议与结果映射 |
| S5 | 免登录面端点安全 | （raw 白名单三态 + config/forcesave 鉴权门槛） | AFK | SecurityConfig 白名单精确性的回归防线 |

执行顺序建议：S0（HITL 在前）→ S1（红绿最快）→ S3（缺口最大、价值最高）→ S2 → S4 → S5。

## 2. 测试用例（逐接口）

### OoTrustToken（contract: onlyoffice-editing.OoTrustToken）— HITL S0 + AFK S1

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [信任] 生产 JWT 开关策略 [产出] 强制启用与密钥轮换流程确认（人工） | 生产部署清单 | 人工评审 jwt-enabled=false 的现网风险与 ONLYOFFICE_JWT_SECRET 轮换流程 | 结论回填本文件：生产必须 jwt-enabled=true + 专用密钥，并给出轮换步骤 | REQ-E03、NFR-2 | 无（HITL） |
| [签发] 中文与嵌套结构 payload [产出] 3 段 JWS 且往返还原一致 | Map 含中文/嵌套 Map | signPayload(map, 60000) → verify | token 为 3 段；verify 返回 Map 深度等于原 payload | REQ-E02 | 无（真实实现+测试密钥） |
| [签发] TTL 到期后 [产出] verify 抛 OnlyOfficeTokenException | ttlMillis=1 | 等待过期后 verify | E_TOKEN_INVALID 路径；±300s leeway 内的临界用例单列 | REQ-E02 | 时间（短 TTL 自然过期） |
| [验签] 篡改签名/换密钥签名/2 段自造格式/subject 空 [产出] 全部拒绝 | 四种坏 token | verify | 均抛 OnlyOfficeTokenException（2 段格式是 OO 实测踩坑回归） | REQ-E02 | 无 |
| [raw token] 签发后 [产出] claims 恰含 scope=onlyoffice-raw+kbId+fileId | kbId/fileId | signRawFileToken → verify | 三 claims 齐全且 scope 固定 | REQ-B02、REQ-E02 | 无 |

### EditorSessionService（contract: onlyoffice-editing.EditorSessionService）— AFK S2

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [派生] 同 fileId+updatedAt 两次调用 [产出] key 恒定且 32 hex | 固定 (fileId, updatedAt) | buildDocumentKey ×2 | 两值相等、长度 32、十六进制字符集 | REQ-A05 | 无 |
| [派生] updatedAt 变化 [产出] key 变化（强制重载根基） | updatedAt₁ ≠ updatedAt₂ | buildDocumentKey ×2 | 两 key 不等 | REQ-A05 | 无 |
| [config] editor 角色 [产出] edit 模式全套 | editable=true 的 docx 文件 | buildEditorConfig | mode=edit、permissions.edit=true、customization.forcesave=true、documentType=word、lang=zh-CN | REQ-A03、A04 | KbFileMapper |
| [config] viewer 角色 [产出] view 模式 | editable=false | buildEditorConfig | mode=view、permissions.edit=false、download/print/copy 仍 true | REQ-A04 | KbFileMapper |
| [config] jwtEnabled 双模 [产出] token 有无同步切换 | jwtEnabled=true / false 各一次 | buildEditorConfig | true：token 字段存在且 verify 还原整个 config；false：无 token 字段 | REQ-E03 | KbFileMapper |
| [config] 六扩展名映射 [产出] docType/fileType 正确，非法扩展拒绝 | docx/xlsx/pptx/…/txt 各一 | buildEditorConfig | 六值各自映射 word/cell/slide；txt 抛 IllegalArgumentException | REQ-A02、A03 | KbFileMapper |
| [config] 签发成功 [产出] 会话登记可被消费 | docx 文件 | buildEditorConfig 后读 activeSessionKeys | fileId→documentKey 已登记且与 config.document.key 一致 | REQ-A06 | KbFileMapper |
| [支持] enabled=false 或无扩展名 [产出] 不可编辑判定 | enabled=false / "README" / "file." | isSupportedFile | 恒 false（六扩展名白名单之外一律 false） | REQ-A01、A02 | 无 |

### CallbackSaveOrchestrator（contract: onlyoffice-editing.CallbackSaveOrchestrator）— AFK S3（幂等用例族）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [幂等] 同会话首次 status=6 [产出] 完整四步编排 | 会话已登记、jwtEnabled=true、token/key/url 齐备、本地 HttpServer 提供下载 | handleCallback | error=0；replaceOriginalFile 以下载字节调用一次；MarkdownService 被调；reChunkFile(kbId,fileId,null) 恰一次 | REQ-D04、D05、E04 | KbFileMapper、FileService、DocumentParser、MarkdownService、本地 HttpServer |
| [幂等] 同会话二次保存（updatedAt 已变、key=会话 key）[产出] 不被误拒 | 首次保存后文件 updatedAt 已变 | handleCallback（同 key） | error=0；第二次完整编排执行（会话 key 兼容路径） | REQ-D07、A06、E04 | 同上 |
| [幂等] 同 key 的 status=6 重复投递两次 [产出] 两次均 error=0 且 reChunkFile 恰两次（固化现状：无判重） | 两次相同 payload | handleCallback ×2 | 两次 error=0；replace/reChunk 各 2 次——现状行为固化为回归基线，未来加去抖时改此断言 | REQ-D07 | 同上 |
| [幂等] status=1/4 各重复投递 [产出] ack 且下游零交互 | payload status=1 / status=4 | handleCallback ×2/各 | 恒 error=0；FileService/MarkdownService/下载通道零交互 | REQ-D02 | 同上 |
| [准入] token 验签失败 [产出] 拒绝且存储零改动 | 坏签名 token、其余齐备 | handleCallback | error=1；replaceOriginalFile 零调用 | REQ-E04 | 同上 |
| [准入] token claims 与路径不符 [产出] 拒绝 | token 的 fileId≠路径 fileId | handleCallback | error=1；零改动 | REQ-E04 | 同上 |
| [准入] key 三态 [产出] 匹配通过/陌生拒绝/null 现状容忍 | key=expectedKey；key="forged"；key=null（其余齐备） | handleCallback ×3 | 前者 error=0；中者 error=1 零改动；后者现状 error=0（宽松面固化，S0 收紧后改断言） | REQ-E04 | 同上 |
| [准入] jwtEnabled=false 无 token [产出] document.key 兜底准入 | jwtEnabled=false、payload 无 token、key 匹配 | handleCallback | error=0 且编排完整执行（开发现状路径） | REQ-E03、E04 | 同上 |
| [编排] url blank / 下载 HTTP 500 / 下载超时 [产出] 拒绝且 MinIO 零交互 | 三种坏下载场景（stub 返回 500 / 不响应） | handleCallback | 均 error=1；replaceOriginalFile 零调用 | REQ-D03 | 本地 HttpServer |
| [编排] replaceOriginalFile 抛异常（文件已进回收站）[产出] error=1 | FileService.replaceOriginalFile 抛 RuntimeException | handleCallback | error=1（E_REPLACE_FAILED 收口，不外抛） | REQ-D04 | FileService |
| [编排] Markdown 重落盘失败 [产出] 仍 error=0 且重切已触发 | MarkdownService.persistParsedMarkdown 抛异常 | handleCallback | error=0；reChunkFile 已调用（降级不阻断） | REQ-D06 | MarkdownService |
| [编排] reChunkFile 触发失败 [产出] 仍 error=0 | FileService.reChunkFile 抛异常 | handleCallback | error=0（D_AUX_STEP_DEGRADED） | REQ-D06 | FileService |
| [分发] status=3/7 与未知 status 与空 payload [产出] 各自正确应答且零动作 | status=3 / status=7 / status=5 / payload=null / status=null | handleCallback ×5 | 3/7→error=1；5→error=0；null 类→error=1；全部下游零交互 | REQ-D02 | 同上 |

### ForceSaveCommander（contract: onlyoffice-editing.ForceSaveCommander）— AFK S4

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [命令] OO 错误码四映射 [产出] result 语义正确 | 本地 stub CommandService 分别返回 {"error":0/4/1/7} | forceSave | initiated / no-changes / no-session / failed(+ooError=7) | REQ-C03 | 本地 HttpServer |
| [命令] 会话 key 定位 [产出] 登记优先、缺省退化 | activeSessionKeys 有/无登记 | forceSave | 有：命令 body key=登记值；无：key=按 updatedAt 派生值 | REQ-C02、A06 | KbFileMapper、本地 HttpServer |
| [命令] jwtEnabled [产出] 命令附 token 且 payload 镜像 {c,key} | jwtEnabled=true | forceSave → stub 收集请求体 | body 含 token；verify(token) 得 {c:"forcesave", key:命令 key}；TTL≤60s | REQ-C02、E02 | 同上 |
| [命令] 前置守卫 [产出] disabled/no-file 零网络 | enabled=false；fileId 查无 | forceSave ×2 | result=disabled / no-file；stub 零请求 | REQ-C03 | KbFileMapper |
| [命令] CommandService 不可用 [产出] failed 降级 | stub 返回 HTTP 500 / 拒连 / 返回非 JSON | forceSave ×3 | 均 result=failed（附 ooError 或 message；非 JSON 保守 -1→failed） | REQ-C03 | 本地 HttpServer |

### 免登录面端点安全（S5，MockMvc/切片集成风格）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [白名单] raw/callback 无 Authorization 头 [产出] 到达控制器而非过滤器链拒绝 | 无凭据请求 | GET raw（坏 token）/ POST callback | 响应来自控制器逻辑（401/协议应答），非 Spring 认证失败态——白名单精确覆盖两路径 | REQ-E01 | OoTrustToken 真实 |
| [白名单] config/forcesave 无凭据 [产出] 仍被认证要求拦截 | 无凭据请求 | GET config / POST forcesave | 认证失败（不在 permitAll 白名单）——防白名单误扩 | REQ-E01、E05 | 无 |
| [raw] 有效 token+路径匹配 [产出] 200 原文件字节流 | MinIO stub 返回流 | GET raw | 200、Content-Disposition inline、Cache-Control no-store、media type 按扩展名 | REQ-B02 | MinioService |
| [raw] 三态拒绝 [产出] 401/403/404 | 坏 token；scope 或 kbId/fileId 不匹配 token；token 有效但文件查无 | GET raw ×3 | 401 / 403 / 404 | REQ-B02 | MinioService |
| [forcesave] viewer 调用 [产出] 403 语义拒绝 | 会话权限无 kb:* | POST forcesave | ApiResponse.forbidden（viewer 无编辑权限） | REQ-C01、E05 | 无 |

## 3. 追溯（抽样）

| 测试 | 契约接口 | Design 模块 | Spec REQ |
|---|---|---|---|
| [幂等] 同会话二次保存… | onlyoffice-editing.CallbackSaveOrchestrator | CallbackOrchestrator | REQ-D07/A06/E04 |
| [准入] key 三态… | onlyoffice-editing.CallbackSaveOrchestrator | CallbackOrchestrator + 缝 1 | REQ-E04 |
| [config] jwtEnabled 双模… | onlyoffice-editing.EditorSessionService | EditorSessionModule + 缝 2 | REQ-E03 |
| [命令] OO 错误码四映射… | onlyoffice-editing.ForceSaveCommander | ForceSaveCommander | REQ-C03 |
| [raw] 三态拒绝… | （端点面，RawFileServing (S)） | RawFileServing | REQ-B02 |
| [验签] 篡改/2 段格式… | onlyoffice-editing.OoTrustToken | OoTrustTokenModule + 缝 2 | REQ-E02 |
| [白名单] config/forcesave 无凭据… | （iam.SecurityGate 回归） | iam 共享边界 | REQ-E01 |

## 4. 待实施说明

- 本文件为测试设计，红绿循环**未执行**（任务铁律：不改源码/测试代码）。执行时从 Tracer Bullet 起步：先走通「S1 签发→验签往返 → S3 [幂等] 首次 status=6」一条纵切（令牌是编排的准入前提），再横向铺开 S3 全族 → S2 → S4 → S5。
- **现状零覆盖**：全仓无任何 OnlyOffice 相关测试类（grep 实测）——S1~S5 全部为待新增，无回归基线可依。
- **免改源码的 HTTP 边界**：downloadFromUrl（OnlyOfficeServiceImpl.java:363-378）与 postCommand（:431-459）用裸 HttpURLConnection 且 URL 来自 @Value 配置——测试用本地 `HttpServer` stub 监听随机端口，经 onlyoffice.callback-base-url / internal-url / command-path 注入指向 stub，生产代码零改动。若后续抽缝（design §4 深化项），本文件的 stub 用例不变。
- **幂等族断言演化**：[幂等] 重复投递 与 [准入] key=null 两用例**固化的是现状行为**（无判重、null 容忍）；S0 HITL 收紧决策落地后，对应断言改为「重复投递去抖」「null key 拒绝」，并在本文件标记确认人与日期。
- mock 边界白名单（本链）：KbFileMapper、FileService、DocumentParser、MarkdownService、MinioService（均为上游契约/系统能力边界）；本地 HttpServer（HTTP 通道）；OoTrustToken 一律真实实现（测试密钥），禁 mock。
