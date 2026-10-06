# OnlyOffice 在线编辑（onlyoffice-editing）— 实现文档

> 记录既有代码如何实现 `contract/interfaces.md` `## onlyoffice-editing` 节的四个契约。镜像 design/onlyoffice-editing.md §1 的 Module Map。

## 1. Code File Map

```text
fastrag-backend/
├── fastrag-security/src/main/java/com/fastrag/security/
│   ├── util/OnlyOfficeJwtUtil.java          OoTrustToken 契约实现（签发/验签/异常类型，~128 行）
│   └── config/SecurityConfig.java           【片段】raw/callback permitAll 白名单（:71-74，语义归 iam.SecurityGate 契约）
├── fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/
│   ├── controller/OnlyOfficeController.java HTTP 外部面：config/forcesave/raw/callback 四端点 + 角色近似/mediaType 辅助（~216 行）
│   ├── service/OnlyOfficeService.java       集成服务接口（6 方法 + 线程模型 javadoc）
│   ├── service/impl/OnlyOfficeServiceImpl.java  EditorSession/Callback/ForceSave 三契约实现 + activeSessionKeys（~473 行）
│   └── model/OnlyOfficeCallbackPayload.java 回调 payload DTO（11 字段，ignoreUnknown）
├── fastrag-bootstrap/src/main/resources/application.yml  【片段】onlyoffice.* 配置键（:153-171）
docker-compose.yml                           【外部系统边界】onlyoffice-docserver:8.3.3 容器拓扑（:175-222）
```

消费（非本链所有，见上游契约）：`FileServiceImpl.replaceOriginalFile`（FileServiceImpl.java:280-314，覆盖 MinIO 原对象+size/updatedAt）、`FileService.reChunkFile`（:364-369，strategyId=null 沿用绑定）、`MarkdownService.persistParsedMarkdown`（MarkdownService.java:52-70，parsed.md 落盘永不抛）、`MinioService.download`（raw 回源）。

## 2. Interface Implementations

### OoTrustToken（from contract: onlyoffice-editing.OoTrustToken，实现类 OnlyOfficeJwtUtil）

**Logic Flow:**
1. `signPayload(payload, ttlMillis)`：Jackson 序列化 payload → `Jwts.builder().header().add("typ","JWT")` → subject=JSON 串 → iat=now → exp=now+ttlMillis → `signWith(hmacShaKeyFor(secret bytes), HS256)` → compact；序列化异常 → `OnlyOfficeTokenException("Failed to sign…", e)`。〔OnlyOfficeJwtUtil.java:65-78 ‹CERTAIN›〕
2. `signRawFileToken(kbId, fileId, ttlSeconds)`：claims = {kbId, fileId, scope:"onlyoffice-raw"} → 委托 signPayload（毫秒=秒×1000）。〔:84-90 ‹CERTAIN›〕
3. `verify(token)`：`Jwts.parser().verifyWith(key).clockSkewSeconds(300).build().parseSignedClaims(token)` → 取 subject → null/空 → 抛「Empty OnlyOffice token subject」→ Jackson 反解为 Map 返回；JwtException → 包为「Invalid OnlyOffice token: …」；其他异常 → 「Failed to verify…」。〔:97-117 ‹CERTAIN›〕
4. 密钥：`@Value("${onlyoffice.jwt-secret:fastrag-onlyoffice-dev-secret-change-me}")` → UTF-8 字节 → HMAC 密钥。〔:48-56 ‹CERTAIN›〕

**Module Interactions:**
- Calls：无外部模块（无状态 @Component，仅 Jackson/JJWT 库）。
- Called by：OnlyOfficeServiceImpl（raw token 签发 :141、config 签名 :220、callback 验签 :264、命令签名 :406）、OnlyOfficeController（raw 验签 :126）。
- Shared state：无。

**Decisions & Deviations:** ① 标准 3 段 JWS 是实测修正——曾用 2 段自定义格式被 OO 端 jsonwebtoken 拒（`jwt malformed`，javadoc :32-36 记录踩坑史）；② payload 编码于 **subject claim**（非顶层 claims），verify 只认 subject——与 OO 的互操作约定；③ leeway=300s 与 OO 服务端默认对齐（:51-52 注释）；④ 失败归一为 RuntimeException 子类，调用方以 catch(Exception) 粗粒度消化（控制器 raw 处 catch 后直接 401）——无细分异常体系，契约的 E_TOKEN_INVALID 单点承接。无其他偏离。

**Parent Trace:** OnlyOfficeJwtUtil → 契约 onlyoffice-editing.OoTrustToken → Design.OoTrustTokenModule + 缝 2 → REQ-E02、REQ-E03。

### EditorSessionService（from contract: onlyoffice-editing.EditorSessionService，实现类 OnlyOfficeServiceImpl + 控制器前置段）

**Logic Flow:**
1. `isSupportedFile(fileName)`：IF !enabled 或 blank → false；取最后一点后缀，无点/点结尾 → false；后缀小写 ∈ SUPPORTED_EXTS{doc,docx,xls,xlsx,ppt,pptx} → true。〔OnlyOfficeServiceImpl.java:129-137 ‹CERTAIN›〕
2. `buildDocumentKey(fileId, updatedAt)`：SHA-256 依次 update(fileId UTF-8)、updatedAt 非 null 时 update(updatedAt.toString()) → 摘要前 16 字节逐字节 `%02x` 拼 32 字符；任何异常 → `fileId + "_" + System.currentTimeMillis()`。〔:145-162 ‹CERTAIN›〕
3. `buildEditorConfig(kbId, fileId, userId, userName, editable)`：
   1. fileMapper 联查（id+kbId 双条件）→ null → 抛 IllegalArgumentException("File not found")；〔:166-171〕
   2. 扩展名小写 ∉ 六值集 → 抛 IllegalArgumentException；〔:172-175〕
   3. documentKey = buildDocumentKey(fileId, f.updatedAt)；`activeSessionKeys.put(fileId, documentKey)` 登记会话；〔:177-179〕
   4. documentUrl = storage-base-url + `/api/kb/{kbId}/files/{fileId}/onlyoffice/raw` + `?token=` + signRawFileToken(kbId,fileId)（TTL 1800s）+ `&key=` + documentKey；callbackUrl = callback-base-url + callback 路径；〔:180-184〕
   5. document = {fileType:EXT_TO_FILE_TYPE[ext], key, title:f.name, url, permissions:{edit:editable, download:true, print:true, copy:true}}；〔:186-195〕
   6. editorConfig = {callbackUrl, mode: editable?"edit":"view", user:{id:userId?:"anonymous", name:userName?:"Anonymous"}, lang:"zh-CN", customization:{autosave:true, forcesave:true, compactHeader:true, toolbar:true}}；〔:197-208〕
   7. root = {document, editorConfig, documentType:EXT_TO_DOC_TYPE[ext]（word/cell/slide）, type:同 documentType, width/height:"100%"}；〔:210-216〕
   8. IF jwtEnabled THEN root.token = signPayload(root, 1800×1000 ms)（注意：签的是**当时的 root**，含 document/editorConfig 全字段）；info 日志 → RETURN root。〔:218-226〕
4. 控制器前置（config 端点，OnlyOfficeController.getConfig）：!ooEnabled → ApiResponse.error(503,「OnlyOffice 未启用」)；文件查无 → error(404)；!isSupportedFile → badRequest（附扩展名）；随后 resolveCurrentUserRole：LoginUser null → viewer；`hasPermission("*")` → editor；`hasAnyPermission("kb:manage","kb:write","kb:edit")` → editor；否则 viewer → editable=(editor||owner 语义)。〔OnlyOfficeController.java:70-93,182-191 ‹CERTAIN›〕

**Module Interactions:**
- Calls：OnlyOfficeJwtUtil（signRawFileToken/signPayload）、KbFileMapper（文件联查）。
- Called by：OnlyOfficeController（config 端点）。
- Shared state：`activeSessionKeys`（写登记；供 ForceSaveCommander 定位、CallbackSaveOrchestrator 兼容读取）。

**Decisions & Deviations:** ① document.key 掺入 updatedAt 是「保存后强制重载」机制的实现核心（replaceOriginalFile 每次写 updatedAt=now，FileServiceImpl.java:309）——key 变化即 OO 缓存失效；② 会话登记无逐出（进程内存 Map，NFR-4 单实例语义）；③ customization.forcesave=true 显式开启（注释：避免用户忘记 Ctrl+S）；④ **偏离一（登记债务）**：角色解析为权限字符串近似而非 KBRole 直读，javadoc「KbAuthAspect 已把角色存入 LoginUser」与实现不符（切面只读不写角色，KbAuthAspect.java:64）——KB editor 若无 kb:* 权限会被误判 viewer；⑤ **偏离二（登记债务）**：document.url 携带 key 参数（注释称 OO 7.1+ 要求、防滥用），但 raw 服务端不校验该参数（声明-实现差距）；⑥ config 签名在 root 组装完成后整体签名再挂 token 字段——token 自身不在签名载荷内（OO 校验语义允许）。

**Parent Trace:** OnlyOfficeServiceImpl.buildEditorConfig/buildDocumentKey/isSupportedFile/signRawFileToken + OnlyOfficeController.getConfig/resolveCurrentUserRole → 契约 onlyoffice-editing.EditorSessionService → Design.EditorSessionModule → REQ-A01~A06、REQ-B01、REQ-B03（角色段 REQ-A04/REQ-E05）。

### CallbackSaveOrchestrator（from contract: onlyoffice-editing.CallbackSaveOrchestrator，实现类 OnlyOfficeServiceImpl.handleCallback）

**Logic Flow:**
1. payload==null 或 status==null → warn → RETURN {error:1}。〔:232-236 ‹CERTAIN›〕
2. IF status ∈ {1,4} → RETURN {error:0}（零副作用 ack）。〔:243-246〕
3. IF status ∈ {3,7} → warn（保存错误上报）→ RETURN {error:1}。〔:249-253〕
4. IF status ∈ {2,6} → 保存编排：
   1. **token 准入**：IF jwtEnabled → verify(token)；异常（E_TOKEN_INVALID）→ warn → {error:1}；claims kbId/fileId 与路径不符 → warn → {error:1}。ELSE IF token 非空 → info「JWT disabled, skipping」（OO 关 JWT 后回调不携 token；残留 token 忽略）。〔:256-279〕
   2. **key 准入**：fileMapper 联查 → null → {error:1}；expectedKey = buildDocumentKey(fileId, f.updatedAt)；sessionKey = activeSessionKeys.get(fileId)；keyTrusted = (key==null) || (key==expectedKey) || (key==sessionKey)；!keyTrusted → warn（保守拒绝：文件被不知情改动）→ {error:1}。〔:284-302〕
   3. **下载**：url blank → warn → {error:1}；downloadFromUrl(url)（GET，30s 连接/60s 读，HTTP≥400 → 抛）失败 → error log → {error:1}。〔:305-317〕
   4. **替换+联动**（外层 try）：
      - contentType = guessContentType(ext)；`f = fileService.replaceOriginalFile(kbId, fileId, newBytes, contentType)`（内部：空字节抛 IAE；查无/回收站抛 RE；MinIO 先删旧——异常忽略即幂等——再传新；size/updatedAt 更新落库）→ info 记账；〔:319-323、FileServiceImpl.java:280-314〕
      - 内层 try：`documentParser.parse(新字节流, f.extension, f.parseStrategyId, null)` → `markdownService.persistParsedMarkdown(kbId, fileId, ext, parsed)`；任一异常 → warn（Markdown 重落盘失败不阻断）；〔:325-332〕
      - 内层 try：`fileService.reChunkFile(kbId, fileId, null)`——MQ 异步重切，**不等执行完成**；异常 → warn（重切触发失败不阻断）；〔:334-341〕
      - RETURN {error:0}。〔:348-349〕
   5. 外层 catch（replaceOriginalFile 或未预期异常）→ error log → {error:1}。〔:342-346〕
5. ELSE（未知 status）→ warn → RETURN {error:0}（前向兼容容忍）。〔:352-355〕
6. `downloadFromUrl`：HttpURLConnection GET → code≥400 抛 RuntimeException("HTTP n") → readAllBytes → finally disconnect；**无主机白名单**。〔:363-378 ‹CERTAIN›〕

**Module Interactions:**
- Calls：OnlyOfficeJwtUtil（verify）、KbFileMapper（联查）、FileService（replaceOriginalFile / reChunkFile）、DocumentParser（重解析）、MarkdownService（parsed.md 落盘）、OO 临时下载 URL（外部 HTTP）。
- Called by：OnlyOfficeController（callback 端点，permitAll 直达）。
- Shared state：activeSessionKeys（读）；经 FileService 触达 kb_file 行、MinIO 原对象与 `{kbId}/{fileId}/parsed.md`；经 reChunkFile 投递 RabbitMQ `fastrag.ingestion.queue`（异步）。

**Decisions & Deviations:** ① **线程模型**：callback 内 reChunkFile 后不等 MQ 完成立即应答（接口 javadoc :17-21 明示——避免阻塞 OO 回调线程）；② 双层降级结构是「原文件是保存事实源」的实现：replace 成功后 Markdown/重切失败仅 warn，应答不变（契约 D_AUX_STEP_DEGRADED）；③ key 双源准入（expected + session）解决「同会话第二次保存被 expectedKey 误拒」——首次保存后 updatedAt 已变而会话 key 不变（:281-283 注释明示动机）；④ **偏离一（登记债务）**：key==null 容忍通过（:293 宽松分支）；⑤ **偏离二（登记债务）**：下载 URL 无 SSRF 主机白名单，jwt 关闭时 URL 完全由回调方控制；⑥ 无按 key/status 判重（幂等靠重复执行无害，契约幂等语义节已声明）；⑦ 回调响应不包 ApiResponse（OO 协议原样，控制器 :163 注释）。

**Parent Trace:** OnlyOfficeServiceImpl.handleCallback/downloadFromUrl + OnlyOfficeController.callback → 契约 onlyoffice-editing.CallbackSaveOrchestrator → Design.CallbackOrchestrator + 缝 1 → REQ-D01~D07、REQ-E04。

### ForceSaveCommander（from contract: onlyoffice-editing.ForceSaveCommander，实现类 OnlyOfficeServiceImpl.forceSave + postCommand）

**Logic Flow:**
1. IF !enabled → {result:"disabled"}；文件联查 null → {result:"no-file"}。〔:383-393 ‹CERTAIN›〕
2. key = activeSessionKeys.getOrDefault(fileId, buildDocumentKey(fileId, f.updatedAt))——会话登记优先，缺省退化为派生值。〔:396 ‹CERTAIN›〕
3. body = `c=forcesave&key=` + urlencode(key)；IF jwtEnabled → body += `&token=` + urlencode(signPayload({c:"forcesave", key}, 60_000ms))（payload 镜像命令参数）。〔:399-407 ‹CERTAIN›〕
4. postCommand(body)：POST `ooInternalUrl + commandPath`（form-urlencoded，10s 连接/30s 读）→ HTTP≥400 抛（附响应体）→ 解析响应 `{"error": n}`，解析失败保守返回 -1。〔:431-459 ‹CERTAIN›〕
5. SWITCH ooError：0→initiated；4→no-changes；1→no-session；default→failed（附 ooError）。〔:410-418 ‹CERTAIN›〕
6. 请求异常 catch → error log → {result:"failed", message}。〔:419-423 ‹CERTAIN›〕
7. 控制器前置（forcesave 端点）：!ooEnabled → 503 业务错误；resolveCurrentUserRole ∉ {editor,owner}（实现为权限近似）→ ApiResponse.forbidden(「viewer 角色无编辑权限，无法保存」)。〔OnlyOfficeController.java:102-111 ‹CERTAIN›〕

**Module Interactions:**
- Calls：OnlyOfficeJwtUtil（命令签名）、KbFileMapper（联查）、OO CommandService（外部 HTTP，internal-url + command-path）。
- Called by：OnlyOfficeController（forcesave 端点）。
- Shared state：activeSessionKeys（读）。

**Decisions & Deviations:** ① 发起与落盘解耦——本方法只发命令，落盘由随后 status=6 回调的 CallbackSaveOrchestrator 完成（接口 javadoc :59-67 明示）；② 命令协议用表单体而非 JSON（CommandService.ashx 约定），command-path 可配置以兼容 OO ≥8.3 的 /command 新端点（application.yml:159-161 注释、docker-compose 8.3.3 升级注释同源）；③ OO 错误码 -1（解析失败）落入 default→failed，错误信息不丢失（附 ooError 原码）；④ 无偏离。

**Parent Trace:** OnlyOfficeServiceImpl.forceSave/postCommand + OnlyOfficeController.forceSave → 契约 onlyoffice-editing.ForceSaveCommander → Design.ForceSaveCommander → REQ-C01~C04。

## 3. Testing

引用 `tdd/onlyoffice-editing.md` 的切片规划（S0 HITL + S1~S5 AFK）：

- **现有覆盖**：零——全仓无任何 OnlyOffice 相关测试类（grep `*Test.java` 实测），S1~S5 全部为待新增缺口；也无手工/集成验证脚本入库 〔决议 2026-09-24：取「docker-compose.yml 注释（:176-213 升级 7.4→8.3.3、secure_link 403、SSRF 放行、插件缓存钉死等踩坑）+ docs/sessions/ 会话纪要为现行载体；系统化联调验证记录随 S1~S5 测试补齐时落在本文档验证记录小节」。理由：最小改动、踩坑知识与运行配置同址；现状差距：系统化验证记录缺失，与零测试缺口同账偿还（代裁，用户可推翻）〕。
- **间接覆盖**：被消费的上游能力各有测试归其链——DocumentParser（parser/ 19 测试，file-ingestion 链）、reChunkFile 编排（chunk-management 链 TDD S5 设计）；本链对它们的调用参数正确性（strategyId=null、字节直传）由 S3 用例补齐。
- **缺口优先级**：S3 幂等用例族 > S1 令牌 > S2 config > S4 命令 > S5 白名单——回调是唯一无人工兜底的写路径（直接覆盖用户原文件），且运行于免登录面。
