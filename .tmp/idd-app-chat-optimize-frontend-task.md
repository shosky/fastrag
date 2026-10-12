# 实现任务：app-chat-optimize 前端（Step 1-4 分步交付）

## 任务
在 D:\Workspace\java\github\rag\fastrag\fastrag-frontend 按 **Step 1 → 2 → 3 → 4 顺序**实现优化包。设计蓝图：`docs/design/app-chat-optimize-frontend.md`（先读，含每步验收与 WeKnora 锚点）。每完成一步跑一次自验（vitest + vue-tsc），四步全完后总验收并报告。

## 仓规（必守）
- 旧通道 runtime.vue / AppChat.vue **零改动**（G01）
- 分页 usePagination、无孤儿文件、新 API 写 src/api/index.ts、vi.mock 顶层提升
- 测试：vitest（node 环境，@ 别名已配置）；类型：`npx vue-tsc -b --force` 与基线对比零新增（基线=162 条存量错误）

## Step 1 渲染安全与单源
1. `npm i dompurify`（3.x 自带类型）
2. 新建 `src/utils/markdown.ts`：`renderMarkdown(md)` = marked(gfm/breaks，与现有一致) → DOMPurify.sanitize（FORBID_TAGS: script/style/iframe；FORBID_ATTR: onerror/onclick/onload）
3. `src/views/conversation/components/MessageBubble.vue` 改用 renderMarkdown（其余 v-html 处不动）
4. 新建 `src/utils/markdown.test.ts`：三类 XSS 样例（script/inline onerror/javascript: href）消毒断言 + 正常 md（表格/代码/链接）不变断言

## Step 2 引用与导出
1. `SourcesBlock.vue` 升级：来源按 fileName 分组展示；新增「查看全部 N 条来源」→ el-drawer 右抽屉（列表：文件/得分/跳 chunks 按钮，跳转逻辑复用现有 jumpToChunk）
2. 新建 `src/utils/sessionMarkdown.ts`：`buildSessionMarkdown(messages: {role,content,sources?}[])` → markdown 文本（# 会话标题、**用户：**/助手分节、来源文件名列表）
3. `conversation/index.vue`（或聊天区头部）：「⋯」菜单加「复制会话」→ buildSessionMarkdown → navigator.clipboard.writeText + ElMessage.success
4. 测试：`sessionMarkdown.test.ts`（空消息/含来源/含思考三态）

## Step 3 配置面实效化
**3a 有效性预览**（后端端点已实现并测试通过）：
- 接口契约：`GET /api/apps/{appId}/bindings/effectiveness` → `ApiResponse<Array<{type:'kb'|'tool'|'skill'|'mcp'|'db', id, name, exists, enabled, reason?}>>`（name 失效时为 null）
- `src/api/index.ts` 加 `getBindingsEffectiveness(appId)`
- 四个绑定面板（KnowledgeConfig/SkillConfig/ToolConfig/McpConfig）+ DatabaseConfig 顶部加「有效性」chip 条：有效 n（绿）/ 失效 m（红，title 显示"资源已删除"）；数据源 getBindingsEffectiveness 按 type 过滤；打开面板时拉取

**3b 孤儿面板处置（方案 A 已裁决）**：
- 接线：`editor/index.vue` menuGroups「配置」组加 `{key:'global', label:'安全策略', icon:'Lock'}`（挂 GlobalConfig.vue）；「对话」组加 `{key:'dialog-test', label:'对话测试', icon:'Checked'}`（挂 DialogTest.vue）；面板挂载区同步注册这两个组件
- 下架：删除 `VmConfig.vue`、`DebugChat.vue`、`DialogOptimize.vue`、`KnowledgeUpdate.vue` 四文件及其全部 import/注册引用（git 历史即备份）；在 `docs/implement/` 或设计文档追加一行下架记录
- ⚠ 删除前 grep 全仓确认零 import（除 editor/index.vue 的挂载点一并清理）

**3c 提示词模板库**：
- `BasicConfig.vue` 系统提示词 textarea 上方加模板下拉（el-select）：内置 6 常量（RAG 问答/制度查询/多轮客服/内容摘要/对比分析/表格解读，各写 3-5 句可用文案）→ 选中追加/替换 textarea 内容（替换前 ElMessageBox 确认）
- 常量放 `src/constants/promptTemplates.ts` + 契约测试 `promptTemplates.test.ts`（6 个模板非空、含变量占位说明）

## Step 4 会话日期分组
- `SessionListPanel.vue`：列表按 updatedAt 分桶「今天 / 近 7 天 / 更早」，分节头样式（小字灰色）；置顶区不受影响（置顶桶在最前）
- 分桶纯函数放 `src/utils/sessionGrouping.ts` + 测试（今天边界/跨天/置顶不入桶）

## 总验收（报告必附）
1. `npx vitest run` 全绿（新增 markdown/sessionMarkdown/promptTemplates/会话分桶测试 + 既有 17 用例不回归）
2. `npx vue-tsc -b --force` 与基线 162 条对比零新增
3. `npx vite build` 通过
4. 孤儿面板四文件删除后全仓零残留引用
5. 报告（中文）：分步交付清单（Step→文件→要点）、每步自验结果、偏离设计决定及理由、遗留问题
