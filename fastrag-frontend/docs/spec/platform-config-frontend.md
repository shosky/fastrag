# 模型与平台配置 — Frontend Spec（platform-config-frontend）

> 由 /idd-spec 于 2026-09-21 生成（onboard 特征化模式）。Parent Trace ← `docs/feature-map.md` 特性 14 证据包；术语 ← `CONTEXT.md`。

## 1. Scope

覆盖模型管理、开放密钥页（实管字典）、系统设置五页、提示词模板页（整页 mock）、品牌 store。**排除**：孤儿页 config-management/third-party（无路由零引用——INFERRED-6 处置）；notifications 组（消费方在 notification/flows/AppChat 等，非本特性页面）。

## 2. User Tasks

- **TASK-001 模型管理** — 卡片列表 + 新增/编辑/复刻共用弹窗 + 删除（confirm，**catch{} 吞错**）+ 上下架 toggle + 批量导入 JSON + 导出（**响应未处理无落盘**，仅 toast）；连通性测试按 purpose 分支：EMBEDDING→test-embedding（docs 非空校验）/RERANK→test-rerank/其余→test-chat，结果 el-descriptions 展示 answer/dimensions/vectorPreview/results；**apiKey 非空才进 payload（防清空）**（model-management:75-259,100-129,233-236,360-418 ‹CERTAIN›；INFERRED-3 导出无落盘）。
- **TASK-002 开放密钥（实管字典 type='API密钥'）** — 列表 getDictionaries；创建/编辑（**key 前端 `Math.random` 生成 `sk-ais-…`**；expiry/permissions 字段从不提交）；复制（clipboard）；**alert 称「生成后不再显示」但表格明文展示 value 且可复制——语义矛盾**（api-keys:29-105 ‹CERTAIN›，INFERRED-4）。
- **TASK-003 知识库空间治理（kb-config）** — 列表 + 行内编辑空间限制 → updateKnowledgeBase（**改的是展示串 spaceLimit 但提交原值 totalSize——编辑永不生效**）；**「默认配置」「批量修改」保存均为假成功（无 API 仅弹 success）**（kb-config:33-105,75-91 ‹CERTAIN›，INFERRED-7）。
- **TASK-004 通用设置** — 品牌（getSysConfigs('brand') 回填）+ 语言时区 + 通用参数；保存=12 个 saveSysConfig 并发；重置=getDefaultConfigs 取 keys → resetDefaultConfig（**传数组给单 key URL 参数——疑不可用**，INFERRED-8）；Logo 上传直连 POST /api/upload 带 Bearer（general-settings:43-174,20-24 ‹CERTAIN›）。
- **TASK-005 敏感词** — CRUD + 模板下载（blob CSV）+ 动态 file input 导入；「联网检索屏蔽」开关 disabled（注释「预留功能」）（sensitive-words:43-191 ‹CERTAIN›）。
- **TASK-006 字典** — 类型左栏切换（getDictionaryTypes+getDictionaries 并发）+ 条目 CRUD + 前端关键词过滤（dictionary:27-110 ‹CERTAIN›）。
- **TASK-007 术语库** — 双 Tab（库卡片/词条表格）；库 CRUD + 词条 CRUD（库筛选走后端 getTerms({libraryId})）；批量导入= TODO stub「开发中」（terminology:90-176 ‹CERTAIN›）。
- **TASK-008 提示词模板（整页 mock）** — 数据全来自 `@/mock/answer-kb`；**删除/保存找复数函数名（deleteTemplates 等）不存在 → 回退到 FAQ mock（deleteFaq）——保存「应答模板」实际写入 FAQ store，列表永不更新**（templates:4,39-57; mock 函数名单复数不匹配，INFERRED-9）；无 el-empty。

## 3. Pages & Routes

8 路由全接侧边栏（管理→系统设置 5 页：167-171；平台：模型管理/开放密钥 201-202；`/application/prompt-templates` **在路由但侧边栏与权限表均未登记**——retrieval 批次已记）。品牌 store 消费方：App.vue/Logo/login/register/forgot-password（‹CERTAIN›）。同目录孤儿页 ×2：config-management（安全/发布策略/配置历史，消费 config 组孤儿端点）、third-party（三方平台走字典 API，每次保存都 create 无更新路径）。

## 4. State Matrix

| 数据流 | loading | empty | error | 证据 |
|---|---|---|---|---|
| 各页 | loading+v-loading 齐全 | 模型/密钥/敏感词/字典/术语均有 el-empty；**templates 无** | ElMessage.error 为主；**删除/密钥保存 catch{} 或无 catch** | 各页 |
| 模型测试 | 测试弹窗 | — | 错误展示 | :89-129 |
| 通用设置保存 | — | — | 并发 12 请求无聚合错误处理 | :118-153 |

## 5. Data Mapping

| 视图数据 | 消费代码（CERTAIN） | API Contract |
|---|---|---|
| 模型 | GET/POST/PUT/DELETE `/models`、toggle、import、export、test-chat/embedding/rerank（api:1088-1110,1501-1558） | TBD — pending API Contract |
| 系统配置 | GET `/config?configType=`、PUT `/config`、GET `/config/default`、POST `/{key}/reset-default`（api:1561-1582） | TBD |
| 字典 5 端点 | api:1144-1162（dictionary + api-keys 页） | TBD |
| 敏感词 6 端点 | api:1112-1138 | TBD |
| 术语 8 端点 | api:1202-1232 | TBD |
| 品牌 store | getSysConfigs('brand') → systemName/slogan/copyright/logoUrl/orgName（system.ts:20-33；未登录跳过） | TBD |
| ~~models 死导出 ×5~~ | getModelDetail/testModel/trainModel/getModelTrainings/getModelTestReports（INFERRED-5） | — |

## 6. Responsive & Accessibility

桌面优先 [GAP]。

## 7. Acceptance

- [ ] 模型 CRUD/上下架/导入/测试三分支（apiKey 防清空）
- [ ] 密钥页现状语义（前端生成/明文展示——INFERRED-4 裁决前维持）
- [ ] kb-config 行内编辑现状（不生效）与假成功弹窗（INFERRED-7 裁决前维持）
- [ ] 通用设置 12 项并发保存；Logo 上传；重置（INFERRED-8 现状）
- [ ] 敏感词/字典/术语 CRUD 与导入（术语批量导入为 stub 现状）
- [ ] templates 整页 mock 现状（INFERRED-9 裁决前维持）

## 8. INFERRED 裁决（✅ 已全量裁决 2026-09-21，用户授权代决）

> 逐条结论见 [../decisions/inferred-adjudication.md](../decisions/inferred-adjudication.md)。以下原清单保留作对照。

### 原清单（已被裁决覆盖）

INFERRED-1 孤儿页 config-management/third-party 处置？INFERRED-2 purpose 大小写混用（常量与 toUpperCase 并存，:290 原值比较脆弱）——统一？INFERRED-3 exportModels 无落盘——补下载 or 删按钮？INFERRED-4 开放密钥语义矛盾（前端生成密钥+明文展示 vs「不再显示」提示；expiry/permissions 不提交）——接后端生成 or 改文案？INFERRED-5 models 死导出 ×5 清理？INFERRED-6 孤儿页消费的 config 组端点（history/export/import/review-flow 等）随页处置？INFERRED-7 kb-config 假成功 ×2 + 行内编辑不生效——修？INFERRED-8 resetDefaultConfig 数组传参疑不可用——修？INFERRED-9 templates.vue 整页 mock + 死链回退（写入 FAQ store）——接真 or 下线？
