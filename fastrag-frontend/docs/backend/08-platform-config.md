# 模块八：平台配置

> 对应前端：`src/views/admin/system/*`、`src/views/admin/content/*`、`src/views/admin/platform/*`
> 数据契约：`src/mock/models.ts`、`src/mock/terminology.ts`、`src/mock/query-rules.ts`、`src/mock/search-correction.ts`（数据源）、`src/utils/constants.ts`

---

## 一、业务概述

系统级全局配置，主要面向 `super_admin` / `kb_admin`。分为三组：

| 组 | 子域 |
|----|------|
| 系统管理 | 通用设置、知识库配置、敏感词、字典管理、术语库、查询规则 |
| 内容与工具 | 通知、标签、提示词、文档模板、下载中心 |
| 开放平台 | 模型管理、三方平台、开放密钥 |

### 1.1 模型管理（对齐 `models.ts`）

| 模型用途（对齐 `MODEL_PURPOSES`） | 说明 |
|------|------|
| 大语言模型 | 问答/生成 |
| Embedding模型 | 向量化 |
| Rerank模型 | 重排序 |
| OCR识别 | 图像识别 |

模型品牌（对齐 `MODEL_BRANDS`）：通义千问/DeepSeek/智谱AI 等 9 个。

### 1.2 术语与查询规则（检索增强数据源）

- **术语库**：术语 + 别名（同义词），供模块3 `synonyms` 扩展。
- **查询规则**：rewrite（改写）/ expand（扩展），供模块3 `queryRewrite`。
- **纠错/拼音**：拼写纠错表 + 拼音首字母缩写表，供模块3 `autoCorrection`。

---

## 二、数据模型（Entity）

### 2.1 `model` 模型（对齐 `ModelRecord`）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | varchar(32) PK | |
| name / code | varchar | 名称 / 唯一 code |
| purpose | enum('大语言模型','Embedding模型','Rerank模型','OCR识别') | |
| brand | varchar | |
| api_url | varchar(512) | |
| api_key_ref | varchar(128) | 密钥引用（不明文存） |
| status | enum('online','offline') | |

### 2.2 `model_training` / `model_test_report` / `model_call_log`（对齐生命周期）

训练记录、测试报告、调用日志三表（结构略，对齐 `TrainingRecord`/`TestReport`/`CallLog`）。

### 2.3 `term_library` / `term_record`（对齐 `TermLibrary`/`TermRecord`）

术语库 + 术语（name/library/alias/status/definition）。

### 2.4 `query_rule`（对齐 `QueryRule`）

| 字段 | 类型 |
|------|------|
| id | varchar(32) PK |
| name | varchar |
| type | enum('rewrite','expand') |
| status | enum('enabled','disabled') |
| pattern / replacement | varchar |
| priority | int |

### 2.5 `search_correction`（纠错/拼音，对齐 `search-correction.ts`）

两张表：`typo_correction`（错词→正确）、`pinyin_abbreviation`（缩写→候选词）。

### 2.6 `sensitive_word` 敏感词

| 字段 | 类型 |
|------|------|
| id | bigint PK |
| word | varchar |
| category | varchar |

### 2.7 `sys_dictionary` 字典

通用字典表（`dict_type` + `dict_key` + `dict_value`）。

### 2.8 内容类（5 张表）

`notification`（通知）、`tag`（标签）、`prompt`（提示词模板）、`doc_template`（文档模板）、`download_resource`（下载资源）。

### 2.9 `third_party_integration` / `api_key`（开放平台）

三方平台集成（OAuth/SSO 配置）、开放 API 密钥（含调用方、权限范围、过期时间）。

---

## 三、接口设计（API）

### 3.1 模型管理（`/admin/platform/model-management`）

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/models` | 模型列表（`?purpose=&online=`） | `admin:system` |
| GET | `/models/{id}` | 详情 | `admin:system` |
| POST | `/models` | 注册模型 | `admin:system` |
| PUT | `/models/{id}` | 更新 | `admin:system` |
| DELETE | `/models/{id}` | 删除 | `admin:system` |
| POST | `/models/{id}/toggle-status` | 上下架 | `admin:system` |
| GET | `/models/embedding-codes` | 可用 Embedding code（供 KB 表单） | 已认证 |
| GET | `/models/{id}/training` | 训练记录 | `admin:system` |
| GET | `/models/{id}/test-reports` | 测试报告 | `admin:system` |
| GET | `/models/{id}/call-logs` | 调用日志 | `admin:system` |

### 3.2 术语（`/admin/system/terminology`）

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET/POST | `/terminology/libraries` | 术语库列表/新建 | `admin:system` |
| PUT/DELETE | `/terminology/libraries/{id}` | 改/删（级联删术语） | `admin:system` |
| GET/POST | `/terminology/terms` | 术语列表/新建（`?library=`） | `admin:system` |
| PUT/DELETE | `/terminology/terms/{id}` | 改/删 | `admin:system` |

### 3.3 查询规则（`/admin/system/query-rules`）

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET/POST | `/query-rules` | 列表（`?type=`）/新建 | `admin:system` |
| PUT/DELETE | `/query-rules/{id}` | 改/删 | `admin:system` |
| POST | `/query-rules/{id}/toggle` | 启停 | `admin:system` |
| POST | `/query-rules/apply` | 应用规则（供模块3） | 已认证 |

### 3.4 敏感词 / 字典（`/admin/system/*`）

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET/POST/PUT/DELETE | `/sensitive-words` | 敏感词 CRUD | `admin:system` |
| GET | `/dictionaries/{type}` | 字典查询 | `admin:system` |
| POST/PUT/DELETE | `/dictionaries/{type}/{key}` | 字典项 CRUD | `admin:system` |

### 3.5 知识库配置 / 通用设置（`/admin/system/kb-config`、`general-settings`）

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET/PUT | `/config/kb` | 全局 KB 配置 | `admin:system` |
| GET/PUT | `/config/general` | 通用设置 | super_admin |

### 3.6 内容（`/admin/content/*`）

通知/标签/提示词/模板/下载 均为标准 CRUD：`GET/POST/PUT/DELETE /{resource}`，权限 `admin:system`。

### 3.7 开放平台（`/admin/platform/*`）

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET/POST/PUT/DELETE | `/third-party` | 三方平台集成 | super_admin |
| GET/POST/DELETE | `/api-keys` | 密钥签发/吊销 | super_admin |
| POST | `/api-keys/{id}/rotate` | 轮换密钥 | super_admin |

---

## 四、核心逻辑设计

### 4.1 模型调用网关（`fastrag-ai`）

模型不直接暴露，统一经 `aiClient` 调用，按 `purpose` 路由：

```java
@Service
public class AiClient {
    public float[] embed(String modelCode, String text) {
        Model m = modelMapper.findByCode(modelCode);
        return http.post(m.getApiUrl() + "/embeddings", buildReq(m, text))
            .parseEmbedding();
    }
    public String chat(String modelCode, String prompt) { ... }
    public List<RecallItem> rerank(String modelCode, String q, List<RecallItem> items) { ... }
    public String ocr(String modelCode, byte[] image) { ... }
}
```

每次调用写 `model_call_log`（对齐 `models.ts` 调用日志）。

### 4.2 术语同义词映射（对齐 `getSynonymMap`）

术语变更后重建同义词映射缓存（Redis）：`Map<token, relatedTokens[]>`，供检索模块快速扩展。

### 4.3 查询规则应用（对齐 `applyQueryRules`）

```java
public ApplyResult apply(String query) {
    String rewritten = query;
    List<QueryRule> applied = new ArrayList<>();
    // 1. rewrite 规则（按 priority，in-place 替换）
    for (QueryRule r : enabledRewrites(rewriteType)) {
        if (rewritten.contains(r.getPattern())) {
            rewritten = rewritten.replace(r.getPattern(), r.getReplacement());
            applied.add(r);
        }
    }
    // 2. expand 规则（追加唯一 token）
    for (QueryRule r : enabledRules(expandType)) {
        if (rewritten.contains(r.getPattern()) && !rewritten.contains(r.getReplacement())) {
            rewritten += " " + r.getReplacement();
            applied.add(r);
        }
    }
    return new ApplyResult(rewritten, applied);
}
```

### 4.4 敏感词过滤

检索/问答结果返回前过敏感词（命中替换为 `***` 或拦截），作为出站拦截器。

### 4.5 API 密钥鉴权

开放密钥用于外部系统调检索/问答 API，独立于 JWT 鉴权（`X-Api-Key` header），按密钥绑定的权限范围校验。

---

## 五、前端覆盖核对表

| 前端能力 | 后端接口 |
|----------|----------|
| `getModels/getModel/createModel/updateModel/deleteModel/toggleModelStatus` | `/models*` |
| `getTrainingRecords/getTestReports/getCallLogs` | `/models/{id}/training|test-reports|call-logs` |
| `getEmbeddingModelCodes` | `/models/embedding-codes` |
| `getTermLibraries/createTermLibrary/.../getTerms/createTerm/...` | `/terminology/*` |
| `getQueryRules/createQueryRule/.../toggleRuleStatus/applyQueryRules` | `/query-rules*` |
| `correctQuery/matchPinyin/getSuggestion` | 纠错数据由 `/sensitive-words`+拼音表管理，逻辑在模块3 `/query/suggest` |
| 通知/标签/提示词/模板/下载 | `/content/*` |
| 三方/密钥 | `/third-party`、`/api-keys*` |
