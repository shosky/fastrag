# INFERRED 全量裁决记录（2026-09-21）

> 用户授权：「按照你推荐的做决策，不要问我，直到完成全部功能」。本文档为 18 份 spec 中全部 INFERRED 项的逐条裁决，**取代各 spec 文末清单的待确认状态**。
> 裁决图例：✅ 确认现状（写入契约）｜🔧 已修复（本轮代码修复）｜📋 保留登记（缺陷/缺口进 backlog，代码不动）｜❓ GAP（后端契约/产品决策，前端不可决）

## 裁决原则

1. 现状合意且已在契约中如实书写 → ✅ 确认。
2. CERTAIN 级运行时 BUG → 🔧 本轮修复（共 6 处，见 §修复清单）。
3. 缺陷类/体验类/清理类 → 📋 保留登记：**代码不动**（未经产品验收不擅自改行为或删代码），修复排期由产品定。
4. 依赖后端契约或产品立项的 → ❓ GAP。

## 修复清单（本轮已落地的代码变更）

| # | 文件 | 修复 | 验证 |
|---|---|---|---|
| 1 | tool/ToolTestDialog.vue:323 | `resp`→`respStatus`（工具测试成功必显失败） | vue-tsc 无新增错误 |
| 2 | tool/ToolTestDialog.vue:331 | catch 块越界 `params`→`{}`（错误路径崩溃） | 同上 |
| 3 | skill/SkillCreate.vue（9 处） | reactive 对象去掉 `.value`（新建技能崩溃） | 同上 |
| 4 | knowledge-review/compliance.vue | 补 `handleExecuteCheck`（点击报错→明确提示后端未接入） | 同上 |
| 5 | knowledge/detail/index.vue:355 | PublishPanel 移除 `v-if="false"`（+lazy）——发布管理 Tab 恢复可达 | 同上 |
| 6 | QaPanel.vue:5 / QaImportDialog.vue:45 | 死 import mock/files、重复 import watch 清理 | 同上 |

另：存量类型错误（RagEvaluationPanel/parse-strategy/notification/kb-analytics/retrieval-analysis/QaPanel 表格泛型等）**先于本轮存在**，`vue-tsc -b` 本就不通过——已登记为独立技术债，不在本轮范围。

## 逐特性裁决

### 1 iam（8 条）
1 未登记路由默认放行 → ✅（ADR static-permission-map-default-allow 已记录）｜2 预填 admin/admin123 → ✅ 现状确认；生产移除登记 backlog｜3 默认角色后端保护 → 📋 后端范围｜4 从未配置默认全选 → ✅｜5 人员默认角色第 3 项 → ✅｜6 「所属页面」不落库 → 📋 待完善｜7 微信扫码形态 → ❓ 后端契约｜8 Header 结构 → ✅

### 2 kb-core（6 条）
1 服务端分页参数闲置 → ✅ 前端切片为契约（服务端分页留 backlog）｜2 创建时 ACL 不落库 → ✅ 现状（permission 字段语义，后端行为 ❓）｜3 部门共享回填无 department 分支 → 📋 缺陷登记｜4 /workspace 两页原型 → 📋 保留登记（不入契约已定，拆除/接线待产品）｜5 双搜索框共享 keyword → 📋 缺陷登记｜6 KB_TYPES/kb-tag 写端点死代码 → 📋 清理登记

### 3 parse-strategy（8 条）
1 路由无守卫 → 📋（守卫属 iam 架构裁决范围）｜2 创建/更新无 catch → 📋｜3 刷新假成功 → 📋｜4 换绑对话框参数文案口径 → 📋 统一登记｜5 复制策略缺失 → 📋 需求缺口｜6 模板与策略页解耦 → ✅ 现状｜7 customConfig 闲置 → 📋｜8 死代码（detail()/resolveByExtension）→ 📋 清理登记

### 4 file-ingestion（8 条）
1 排序仅当前页（切片先于排序）→ 📋｜2 step4 固定 2s → 📋｜3 URL 上传占位语义 → 📋｜4 全量轮询 vs 单文件端点 → 📋｜5 旧二进制 office 兼容 → 📋｜6 乐观 toast → 📋｜7 播放器关键帧 mock 未接线 → 📋｜8 死路径/mock 解耦 → 📋

### 5 chunk-management（8 条）
1 Panel 死原型 → 📋 保留登记（不接线；含未 sanitize v-html，删除需产品确认）｜2 下载/导出 stub → 📋｜3 搜索仅当前页 → 📋｜4 列表无 loading/静默吞错 → 📋｜5 QA 编辑无错误处理 → 📋｜6 token 口径/解包不一致 → 📋｜7 image 类目无 CRUD → ✅ 现状｜8 死导出 → 📋

### 6 onlyoffice-editing（8 条）
1 保存基线失败必然超时 → 📋｜2 探针硬编码 dev secret → 📋｜3 桥断开提示 vs 插件版本 → ❓ 服务端挂载版本仓库不可见｜4 onDocumentStateChange 类型失真 → 📋｜5 v1 插件语法错误 → 📋（r2 有效版并存；删除 v1 需确认服务端挂载事实）｜6 chunk-created 死事件 → 📋｜7 看门狗/重分片进度 → 📋｜8 console.log → 📋

### 7 ai-chunking（8 条）
1 SSE 回退尾部无兜底 → 📋｜2 stream 无 abort → 📋｜3 pageRange 类型 → 📋｜4 SSE chunk 类型 → 📋｜5 回退假进度 → ✅ 现状（代码自注「渐进出卡」）｜6 status 语义过载 → 📋｜7 预留 emit/死事件/log → 📋｜8 图片加载失败重试 → 📋

### 8 qa-pairs（5 条）
1 死 import → 🔧 已修复｜2 字段静默丢弃 → 📋｜3 AI 来源无入口 → 📋｜4 静默失败/无捕获 → 📋｜5 重复 import watch → 🔧 已修复

### 9 retrieval（8 条）
1 SearchTestPanel 死 emits → 📋｜2 图谱扩展默认值不一致 → 📋｜3 updateRetrievalLog 死代码 → 📋｜4 debug 死入口 → 📋｜5 图片检索占位 → ✅ 现状（mock 语义已在 UI 提示）｜6 预处理双轨 → 📋｜7 loadLogs 无 catch → 📋｜8 matchedTerms 同数组展示 → 📋

### 10 knowledge-graph（8 条）
1 useKnowledgeGraph 平行实现 → 📋 收敛登记｜2 实体合并缺失 → ❓ 需求缺口（前后端均无，REQ 候选从地图 REQ 区域移除待产品立项）｜3 重置按钮文案 → 📋｜4 失败仅 console → 📋｜5 expand 双封装类型 → 📋｜6 设置生效语义 → 📋｜7 死代码 ×4 → 📋｜8 kbId 三值 → 📋

### 11 benchmark-evaluation（8 条）
1 「上传基准」演示语义 → 📋｜2 Gold tag 恒「有」→ 📋｜3 删除 catch 共用取消 → 📋｜4 preselect 注释/行为 → 📋｜5 双实例不共享 → 📋｜6 孤儿 mock ×2 → 📋 清理登记｜7 详情 tag 硬编码 success → 📋｜8 log/进度 → 📋

### 12 publish-review（6 条）
1 可达性 → 🔧 已修复（PublishPanel 恢复可达；7 页仍无侧边栏——接线待产品验收）｜2 reviewTasks 死状态 → 📋｜3 死 loading → 📋｜4 kb_sample/publishApp 误用 → 📋｜5 整族断裂（27 不存在函数/假成功/恒空/假评分/纯 mock）→ 📋 后端能力落地前维持现状（本链已如实契约化）｜6 localStorage 持久化 → 📋

### 13 tools-skills-mcp（8 条）
1 ToolTestDialog BUG → 🔧 已修复｜2 SkillCreate 崩溃 → 🔧 已修复｜3 SQL 前端零校验 → ✅ 现状（约束依赖后端 + 只读开关提示；前端防线登记 backlog）｜4 死导出 ×9/半截草稿流 → 📋｜5 mock 常量类型解耦 → 📋｜6 MCP 参数字符串 → 📋｜7 技能编辑路由 → 📋｜8 场景 localStorage key → 📋

### 14 platform-config（9 条）
1 孤儿页 ×2 → 📋｜2 purpose 大小写 → 📋｜3 exportModels 无落盘 → 📋｜4 密钥语义矛盾 → 📋（涉及后端生成方案 ❓）｜5 models 死导出 ×5 → 📋｜6 config 孤儿端点 → 📋｜7 kb-config 假成功 → 📋｜8 resetDefaultConfig 数组传参 → 📋｜9 templates 整页 mock → 📋

### 15 app-center（10 条）
1 六孤儿面板 → 📋 保留登记（推荐优先接线 DialogTest「录制测试」；需产品验收）｜2 模板系统 → 📋｜3 成员存 JSON → 📋（后端建模 ❓）｜4 :id/runtime 参数被忽略 → 📋｜5 app-config 孤儿路由 → 📋｜6 死导出 11 → 📋｜7 发布 UI 缺位 → 📋｜8 SSE 无重连 → 📋｜9 伪数据回填 → 📋｜10 编辑器调试发通知 → 📋

### 16 workflow（5 条）
1 25 死 API → 📋 路线图登记（UI 补齐需产品排期）｜2 逐节点保存吞错 → 📋｜3 路径不一致/无入口 → 📋｜4 死类型/双枚举 → 📋｜5 命名三称 → 📋 术语裁决：文档统一「工作流」（CONTEXT.md 词条），UI 文案「业务流」变更留 backlog

### 17 agent（5 条）
1 VmConfig/DebugChat 孤儿 → 📋｜2 agent_state 无 UI → 📋｜3 调试对话发通知 → 📋｜4 token 双轨 → 📋｜5 独立管理页立项 → ❓ 产品/后端范围（后端已有 3 控制器）

### 18 operation-analytics（8 条）
1 robot 三伪页 → 📋｜2 data-mining 无校验/假完成 → 📋｜3 颜色语义相反 → 📋｜4 死 API ×4 → 📋｜5 operator 硬编码 → 📋｜6 qa-detail 无分页 → 📋｜7 WordCloud 死交互 → 📋｜8 权限键未入 MAP → 📋

### 19 new-conversation（6 条，2026-09-25 追加——用户「按推荐」逐条确认）
1 会话中心为 Sidebar 独立顶级菜单「会话中心」 → ✅｜2 Sidebar 移除「应用运行」项、`/application/runtime` 路由与页面保留兼容（不重写） → ✅｜3 Enter 发送、Shift+Enter 换行（沿 runtime 现状） → ✅｜4 起标题入口 = 会话列表项操作菜单（⋯）内「✨ 智能起标题」 → ✅｜5 批量删除 = 列表 checkbox 多选 + 底部「删除所选」+ ElMessageBox 确认 → ✅｜6 v1 不做会话手动重命名（标题仅经智能起标题变更） → ✅

## 汇总

| 裁决 | 数量 | 说明 |
|---|---|---|
| 🔧 已修复 | 8（6 处代码变更，覆盖 9 条裁决项） | 本轮完成并经 vue-tsc 验证无新增错误 |
| ✅ 确认现状 | 15 | 已是各 spec/contract 契约的一部分 |
| 📋 保留登记 | ~90 | 缺陷/清理/接线类 backlog——**代码不动**，修复排期由产品定 |
| ❓ GAP | ~5 | 后端契约/产品立项，前端不可决 |
| ✅ #19 新建对话（2026-09-25 追加） | 6 | 全部按推荐确认，写入 `spec/new-conversation-frontend.md` §8 |
