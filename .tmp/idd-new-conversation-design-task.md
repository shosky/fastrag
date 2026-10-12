# 设计任务：new-conversation 对话编排接口设计（双约束对比）

## 背景
FastRAG 后端正在做「新建对话」改造（spec：`fastrag-backend/docs/spec/new-conversation.md`；裁决记录：`fastrag/.tmp/idd-new-conversation-grill-decisions.md`；app-center 既有设计：`fastrag-backend/docs/design/app-center.md`）。核心：会话升一等公民，App 降级为会话可选预设；对话执行复用既有 AppDialogOrchestrator（AppServiceImpl.runStream，12 步编排）。

## 你的任务
为「会话级覆盖入参如何进入对话编排」设计模块与接口。必读三份文档 + 实读代码求证（每条现状断言附 file:line）。

## 代码锚点（起点，不限于）
- `fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/service/impl/AppServiceImpl.java`（runStream 编排 :368-844、绑定装配 :570-624、检索 :716-800、模型路由 :434-475、isSafetyEnabled :85-90、落库 :822-844）
- `.../application/controller/AppChatController.java`、`.../entity/AppConversation.java`、`AppConversationMessage.java`
- `fastrag-security/src/main/java/com/fastrag/security/service/KbAccessChecker.java`（KB ACL 编程式校验入口）
- fastrag-ai 的 LLM 网关（起标题用）

## 设计必须落地的 spec REQ（Parent Trace 锚）
- REQ-B01/B02 请求态固化与消费范围（kb_ids+model 消费，tool/mcp/skill/web 只存）
- REQ-B03 请求时逐 KB viewer 校验、无权跳过告警
- REQ-C01 直聊缺省常量（prompt/maxTokens=2048/temperature=0.7）
- REQ-C02 复用编排链不新增事件
- REQ-C04 敏感词默认开
- REQ-C05 请求态模型优先，缺省走现行四级回退
- REQ-D01/D02 应用预设挂载、范围来源互斥（挂 App 用绑定，请求态 KB 不消费）
- REQ-E02 停止生成（单实例取消标志）
- REQ-F01/F02 消息引用落库与历史返回

## 输出格式（中文，严格按设计文档语言）
1. **模块清单**：每个模块给 Responsibility（一行）/ Files（建议路径）/ Depth Rationale；通不过删除测试的标 (S) 并给内联建议
2. **核心接口**：关键方法签名草案（设计级，不是契约级）+ 隐藏实现 + 深度理由
3. **缝的判定**：哪里是真缝（≥2 适配器）、哪里是假设缝不立缝
4. **Parent Trace**：模块/缝 → spec REQ
5. **风险与开放问题**：你方案里最脆弱的一环
