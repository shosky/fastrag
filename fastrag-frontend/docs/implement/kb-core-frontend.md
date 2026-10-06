# 知识库管理 — Implementation Document（kb-core-frontend）

> 由 /idd-implement 于 2026-09-21 生成（onboard 特征化模式）。

## 1. Code File Map

```
src/views/knowledge/
├── index.vue          — PAGE-001 列表（卡片网格 + 前端筛选分页 usePagination(12)）
├── categories.vue     — PAGE-002 分类扁平表格 CRUD
├── create.vue         — PAGE-003 薄壳（提交 + 自动建默认策略编排）
├── edit.vue           — PAGE-004 薄壳（详情加载 + 保存/删除编排）
└── form.vue           — 共享表单体（1501 行；四 Tab + ACL 构造/回填 + 元数据编辑器）
src/views/home/index.vue — PAGE-005 门户聚合
src/types/knowledge.ts   — KnowledgeBase/KnowledgeBaseForm 类型（771 行）
src/utils/constants.ts   — ~~KB_TYPES~~（死常量，INFERRED-6）
```

## 2. Interface Implementations

### 列表筛选链（contract: PAGE-001）

**Logic Flow:**
```
filtered = kbs
  |> tab==='my' ? (creator===userId || creatorName===username)
  |> searchKeyword ? name.includes(kw)
  |> selectedCategory ? category===cat        // 单选 toggle
  |> selectedTags.some(t => kb.tags.includes(t))
paged = filtered.slice((page-1)*12, page*12)
watch(tab/keyword): page=1
```
**Module Interactions:** getKnowledgeBases/getKbCategories；**API 筛选参数从未传**（INFERRED-1）。

### 创建编排（create.vue）

**Logic Flow:**
```
submit → emit(form 数据) → createKnowledgeBase(POST /kb)
1. IF 成功 THEN:
   1.1 懒加载策略模板缓存 → 按 data.parseMode 匹配（fallback 第一个）
   1.2 createStrategyApi(kbId, {name,description,extensions,parseMethod})
   1.3 策略失败 → catch 静默（不阻断创建成功）
2. toast「知识库创建成功」→ 回列表
3. ACL：不写（仅 permission 字段随 POST；INFERRED-2）
```

### ACL 构造与回填（contract: 共享设置核心）

**Logic Flow:**
```
buildAclOnSave():                        // form.vue:520-565（edit only）
1. acl = [{userId: me, kbRole: 'owner'}]
2. IF global THEN push {userId:'*', kbRole:'viewer'}
3. IF department THEN FOR dept: getDepartmentMembers → 去重排除自己 → viewer
4. IF specified THEN 每人 {userId, kbRole: editor|viewer}（自己跳过）
5. setKbAcl；失败静默（:562-564）
hydrateShareFromAcl(acl):                // :321-349（edit 回填）
1. IF 含 userId:'*' THEN shareType=global
2. ELSE 非自己条目 → specified（kbRole 回填；人名反查 getPersonnelSimple）
3. 无 department 分支（INFERRED-3）
```

### 元数据字段规整（提交前）

```
customAttrSchema: 过滤空 name；options 仅 select 类型按 [，,] 拆数组；searchable = (type==='region') || 勾选
```

### 页面登记表

| 页面 | 状态归属 | 要点 | 证据 |
|---|---|---|---|
| index.vue | server + 页内筛选 | 双搜索框共享 keyword（INFERRED-5）；空态 el-empty；无 catch | :35-111,115,212 |
| form.vue | form（四 Tab 共享 model） | 部门/指定人 popover 互斥；默认部门/自己不可移除；模型字段记忆 embeddingModelTouched | :419-456,478 |
| edit.vue | server + 页面 loading | 查不到 →「知识库不存在」重定向 | :25-29 |
| categories.vue | server + dialog | 普通用户 orgId 强制本组织 | :17-20,58 |
| home/index.vue | server 一次性 | 四区块独立 el-empty | :37-39,86-130 |
| workspace 两页 | **无 API（纯本地原型）** | 不入契约（INFERRED-4） | index.vue:8-48 |

## 3. Decisions & Deviations

- **勘误落地**：form.vue 非孤儿（首批调研 grep 漏相对导入 `./form.vue`）——CONTEXT/feature-map 已更正。
- **偏差（对 conventions「新 API 不新增 mock 消费」）**：无新增；存量 mock 引用与本域无关。
- **静默失败清单**（特征化记录，非本链擅改）：form 初始加载、ACL 写、部门成员拉取、策略自动创建、分类快捷新建。
- **死代码**：KB_TYPES、`getKnowledgeBaseCategories`、index.vue 解构未用的 `hasPermission`（INFERRED-6）。
- 无 contract↔代码矛盾（特征化模式）。

## 4. Test Evidence

tdd 全局 blocked。解阻后优先切片：筛选链（tab/keyword/category/tags 组合）、shareType↔permission 映射、ACL 构造三模式（global/department/specified）、customAttrs 规整。

## 5. Implementation Trace

```
TASK-001 → PAGE-001 → 筛选链/分页 → index.vue:58-111
TASK-002 → PAGE-003 → 创建编排+自动策略 → create.vue:26-61
TASK-004 → 共享三卡 → ACL 构造/回填具名点 → form.vue:321-349,511-565
TASK-005 → 危险区 → canDelete + 删除 → edit.vue:58-75
TASK-006/007 → 分类 CRUD/快捷新建 → categories.vue:71-94, form.vue:390-411
TASK-008 → 首页聚合 → getHomeData → home/index.vue:30-132
```
