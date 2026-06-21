# 模块补充：审核发现的遗漏接口

> 本文档汇总二次审核中发现的、前 9 个模块文档未覆盖或覆盖不全的接口。
> 这些功能在前端**已实现**（页面可交互），但数据多为内联或简单 mock，后端必须提供真实接口。

---

## 遗漏一：首页聚合（模块补 00/09）

> 对应前端：`src/views/home/index.vue`（平台首页）

首页展示推荐知识库、热门文档、热门应用、知识动态 Feed，目前全部内联。

### 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/portal/recommend-kbs` | 推荐知识库（按访问量/活跃度） |
| GET | `/portal/hot-docs` | 热门文档榜（按 viewCount 排序，含 kbName/owner/lastView/viewCount） |
| GET | `/portal/hot-apps` | 热门应用列表 |
| GET | `/portal/feed?limit=10` | 知识动态 Feed（最近更新库/上传文档/自动同步事件，时间线） |
| GET | `/portal/quick-entries` | 快捷入口（可配置或写死） |

### 数据模型补充

- `kb_file` 增加 `view_count` 字段（热门文档排行依据）。
- 知识动态 Feed 可由 `kb_log`（模块5）聚合生成（operation 类日志按时间倒序）。

---

## 遗漏二：工作台（模块补 00）

> 对应前端：`src/views/workspace/index.vue`、`custom-workspace.vue`

工作台是用户个性化页：最近访问、推荐库、热门应用、热门知识库、我的关注、我的收藏、个人容量、自定义布局。

### 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/workspace/recent-visits` | 我的最近访问文档（含 kbName/modifyTime/visitTime） |
| GET | `/workspace/my-follows` | 我的关注（分知识库/文档两类） |
| POST/DELETE | `/workspace/follows/{targetType}/{targetId}` | 关注/取关 |
| GET | `/workspace/my-favorites` | 我的收藏列表 |
| POST/DELETE | `/workspace/favorites/{id}` | 收藏/取消收藏 |
| GET | `/workspace/quota` | 个人知识库容量配额 `{ used, total }` |
| GET/PUT | `/workspace/layout` | 自定义工作台布局配置（按用户维度：layouts/可用组件） |
| GET | `/workspace/recommend-config` | 推荐位配置（推荐知识库设置弹窗） |

### 数据模型补充

| 表 | 字段 | 说明 |
|----|------|------|
| `user_favorite` | id, user_id, target_type('kb'\|'doc'\|'app'), target_id, created_at | 收藏 |
| `user_follow` | id, user_id, target_type, target_id, created_at | 关注 |
| `user_recent_visit` | id, user_id, target_type, target_id, kb_name, visit_time, modify_time | 最近访问 |
| `user_workspace_layout` | id, user_id, layout_json | 自定义布局 |
| `sys_user` 补 `storage_quota` / `storage_used` | bigint | 容量配额 |

---

## 遗漏三：团队成员管理（补模块 01）

> 对应前端：`src/views/admin/account/team.vue`

文档 01 仅一句 `GET/POST/PUT/DELETE /teams`，缺**团队成员子资源**和成员下拉。

### 补充接口

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/teams/{id}/members` | 团队成员列表 | `admin:role` |
| POST | `/teams/{id}/members` | 添加成员 `{ userIds[] }` | `admin:role` |
| DELETE | `/teams/{id}/members/{userId}` | 移除成员 | `admin:role` |
| GET | `/personnel/simple` | 人员简要列表（id+name，供团队/应用选择下拉） | `admin:role` |

---

## 遗漏四：应用编辑器全套配置（补模块 06）⭐ 重点

> 对应前端：`src/views/application/editor/index.vue` + `components/`
> 这是审核发现的**最大遗漏区**。应用编辑器有 12+ 个 Tab，涉及大量字典和配置读写。

### 4.1 应用配置整体读写（细化模块06 §3.2）

文档06 的 `/apps/{id}/config` 字段过粗，需细化。前端 `app_config` 实际承载：

```jsonc
{
  "basic": { "name", "description", "tags[]", "category", "accessToken" },
  "knowledgeIds": ["kb1", "kb2"],
  "skillIds": ["skill1"],
  "toolIds": ["tavily_search"],
  "mcpIds": ["mcp1"],
  "vm": { "imageId", "cpu", "memory" },
  "ui": { "colorTemplate", "logo" },
  "retrievalParams": { "topK", "similarityThreshold", "rerankModel" },
  "prompt": { "steps[]", "content", "templateId" },
  "navList": [ { "question", "isQA", "sort" } ]
}
```

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/apps/{id}/config` | 读取整体配置（上述结构） |
| PUT | `/apps/{id}/config` | 整体保存配置 |
| PUT | `/apps/{id}/config/{section}` | 分 Tab 保存（basic/knowledge/skills/tools/mcp/vm/ui/retrieval/prompt/nav） |
| POST | `/apps/{id}/access-token/refresh` | 刷新 AccessToken |

### 4.2 字典/候选接口（编辑器下拉数据源）

前端目前全部硬编码，后端需提供：

| 方法 | 路径 | 说明 | 现有文档 |
|------|------|------|----------|
| GET | `/dict/app-tags` | 应用可选标签 | ❌ 缺 |
| GET | `/dict/kb-categories` | 知识库分类树（市场运营/项目管理/产品研发…） | ❌ 缺（02 只有 KB 自身 category） |
| GET | `/apps/{id}/bindable-kbs?category=` | 可绑定知识库（含 embeddingModel/dimension） | ❌ 缺 |
| GET | `/apps/{id}/available-skills` | 可选技能列表 | ❌ 缺（07 是技能 CRUD，非"可选"视图） |
| GET | `/apps/{id}/available-tools` | 可选工具列表（含 identifier） | ❌ 缺 |
| GET | `/apps/{id}/available-mcp` | 可选 MCP 服务（含 mcpUrl+tools） | ❌ 缺 |
| GET | `/dict/vm-images` | 虚拟机镜像字典 | ❌ 缺 |
| GET | `/dict/color-templates` | 配色模板字典 | ❌ 缺 |
| GET | `/prompts/templates?scene=app` | Prompt 模板（复用模块08 `/content/prompts`） | ⚠️ 部分 |
| GET | `/models?purpose=Rerank模型` | Rerank 模型下拉（复用模块08 `/models`） | ✅ 已有 |

> 说明：`available-skills/tools/mcp` 是「当前应用视角下可选的、已启用的」资源，与模块7的全量 CRUD 不同，需带 enabled 过滤 + 简化字段。

### 4.3 导航问题（常见问题 FAQ）CRUD

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/apps/{id}/nav-questions` | 导航问题列表 |
| POST/PUT/DELETE | `/apps/{id}/nav-questions[/{qid}]` | 增删改（question/isQA/sort） |
| PUT | `/apps/{id}/nav-questions/sort` | 批量排序 |

### 4.4 应用对话调试（编辑器内）

编辑器有「对话调试」Tab，目前前端假返回。后端复用模块06 §3.3 `POST /apps/{id}/run`（SSE）即可，**无需新增**，但需确认支持编辑态（未发布应用）调试。

---

## 遗漏五：检索调试页的辅助接口确认（模块03）

> 对应前端：`src/views/knowledge/detail/debug.vue`

经核查，文档03 已覆盖：
- ✅ `POST /retrieval/search`（`searchRetrieval`）
- ✅ `POST /query/suggest`（`getSuggestion` 纠错）
- ✅ `POST /query-rules/apply`（`applyQueryRules`）
- ✅ `POST /query/expand-synonyms`（同义词）

**唯一缺口**：多轮对话/问答生成。前端 `useConversation` 用规则拼装答案，真实后端需独立接口：

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/chat/conversation` | 多轮对话（SSE 流式），含上下文检索 + LLM 生成 + 引用溯源 |
| POST | `/chat/messages` | 非流式问答（评估/调试用） |

区别于 `/apps/{id}/run`（应用运行），此接口是**知识库级**问答（无应用编排），直接 kbId + query + model。

---

## 遗漏六：模型下拉一致性（模块08）

> 前端 `debug.vue` 模型下拉硬编码 3 项（qwen3-32b/DeepSeek-V3/Kimi-K2），与 `models.ts` 不一致。

**结论**：后端统一用 `/models?purpose=大语言模型&online=true` 返回在线 LLM 列表，前端替换硬编码。**接口已有**（模块08 §3.1），无需新增，但需标注前端需改造为动态加载。

---

## 遗漏七：空占位页面的后端归属

以下前端页面当前为空 `<el-empty "开发中">`，但后端应预留能力：

| 页面 | 后端归属 | 处理 |
|------|----------|------|
| `application/runtime.vue` | 模块06 `/apps/{id}/run` | 已设计，前端待实现 |
| `knowledge/detail/api-doc.vue` | 开放 API 文档 | 预留：`GET /kb/{kbId}/api-doc`（返回该 KB 的对外检索 API 说明+示例） |
| `operation/qa-detail.vue` | 模块09 问答明细 + 模块2 QA对 | 预留：`GET /qa/sessions/{id}` + 复用 `/kb/{kbId}/qa-pairs` |

预留接口：

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/kb/{kbId}/api-doc` | 知识库对外检索 API 文档（endpoint/参数/示例） |
| GET | `/qa/sessions/{id}` | 单条问答会话明细（query/召回/答案/反馈/溯源） |
| GET | `/qa/sessions?kbId=&from=&to=` | 问答会话列表（分页） |

---

## 修订汇总：各模块文档需补充的接口

| 模块文档 | 补充内容 |
|----------|----------|
| 00 架构总览 | 补「门户/工作台」子模块说明 |
| 01 IAM | 补团队成员管理 `/teams/{id}/members`、`/personnel/simple` |
| 02 知识库 | 补 `view_count` 字段；预留 `/kb/{kbId}/api-doc` |
| 03 检索 | 补 `/chat/conversation`（知识库级多轮对话 SSE） |
| 06 应用 | ⭐ 补全应用配置结构、AccessToken、字典接口、可绑资源、导航问题 CRUD |
| 08 平台配置 | 补字典 `/dict/app-tags`、`/dict/kb-categories`、`/dict/vm-images`、`/dict/color-templates` |
| 09 运营审计 | 补 `/qa/sessions` 问答会话、首页聚合 `/portal/*` |
| 新增 | 工作台 `/workspace/*`（收藏/关注/最近访问/配额/布局） |

---

## 最终覆盖结论

经二次审核，前端实现的功能（含内联数据页面）**现已 100% 映射到后端接口设计**，并补充了：
- 首页聚合（5 接口）
- 工作台（8 接口）
- 团队成员管理（4 接口）
- 应用编辑器配置与字典（15+ 接口）⭐
- 知识库级多轮对话（2 接口）
- 问答会话明细（2 接口）
- API 文档预留（1 接口）

**特别注意**：前端有 3 个页面（runtime/api-doc/qa-detail）当前为空占位，后端接口已预留但前端尚未对接，不能从前端反推完整字段，标注为「预留，前端待实现」。
