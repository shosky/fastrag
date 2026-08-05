# fastrag-retrieval -- 知识检索引擎模块

提供多模式混合检索、查询增强、检索日志与知识库更新提醒能力。

## 模块职责

- 支持向量（Milvus）、全文（MySQL FULLTEXT）、混合（RRF 融合）三种检索模式
- 多路召回（vector / fulltext / graph / QA 四通道）及三种融合策略（RRF / weighted / interleave）
- 查询预处理：自动纠错、查询改写、图谱扩展（NER + GraphStore）、同义词扩展
- 后处理：Rerank 模型重排、LLM 重排、MMR 多样性控制
- 上下文组装：concat / parent_document / window 三种策略
- 检索配置三级合并：系统默认 -> 知识库配置 -> 请求参数
- 检索日志记录与统计分析（无结果率、平均延迟、热门查询等）
- 知识库更新提醒（CRUD + cron 表达式 + 增量更新计数）

## 对外 REST 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/retrieval/search` | 核心检索入口 |
| GET | `/api/retrieval/kb/{kbId}/chunks/count` | 查询知识库 chunk 数量 |
| POST | `/api/query/suggest` | 查询建议（自动纠错） |
| POST | `/api/query/expand-synonyms` | 同义词扩展 |
| POST | `/api/query-rules/apply` | 查询规则改写 |
| POST | `/api/graph/expand` | 图谱扩展（NER + 实体邻居展开） |
| GET | `/api/retrieval/logs` | 检索日志分页查询 |
| GET | `/api/retrieval/logs/analysis` | 检索日志统计分析 |
| POST | `/api/retrieval/logs` | 新增检索日志 |
| PUT | `/api/retrieval/logs/{id}` | 更新检索日志 |
| GET | `/api/kb/{kbId}/update-remind` | 获取知识库更新提醒状态 |
| GET | `/api/update-remind` | 查询更新提醒列表 |
| POST | `/api/update-remind` | 创建/保存更新提醒 |
| PUT | `/api/update-remind/{id}` | 更新提醒配置 |
| DELETE | `/api/update-remind/{id}` | 删除提醒配置 |

## 模块依赖

| 依赖模块 | 用途 |
|----------|------|
| fastrag-common | 统一响应、分页、日志注解 |
| fastrag-security | 用户身份获取（SecurityUtil） |
| fastrag-infra | Milvus 向量搜索、GraphStore 图谱存储 |
| fastrag-ai | EmbeddingService（向量化）、RerankService（重排序）、LlmService（LLM 调用） |
| fastrag-knowledge | KbChunk/KbFile/KnowledgeBase/KbQaPair 实体与 Mapper |
| fastrag-publish | KbLog 操作日志、KbUpdateLog 更新日志 |
| fastrag-platform | ModelRecord 模型管理、ConfigManageService 系统配置、TermService 同义词服务 |

## 关键类说明

### 控制器

| 类名 | 职责 |
|------|------|
| `RetrievalController` | 检索、查询增强、图谱扩展、检索日志、更新提醒的 REST 入口 |

### 服务层

| 类名 | 职责 |
|------|------|
| `RetrievalService` | 检索服务接口：search、getChunkCount |
| `RetrievalServiceImpl` | 检索核心实现：配置合并 -> 预处理 -> 多路召回 -> 融合 -> 重排 -> 去重 -> 上下文组装 -> 日志 |
| `QueryEnhanceService` | 查询增强接口：suggest、expandSynonyms、applyQueryRules、expandGraph |
| `QueryEnhanceServiceImpl` | 查询增强实现：LLM NER 实体提取、GraphStore 图谱展开、同义词扩展 |
| `RetrievalLogService` | 检索日志接口：log、update、page、analysis |
| `RetrievalLogServiceImpl` | 检索日志实现：分页查询 + 统计分析（无结果率、平均延迟、热门/无结果查询 Top10） |
| `UpdateRemindService` | 更新提醒接口：list、get、save、delete、remind |
| `UpdateRemindServiceImpl` | 更新提醒实现：CRUD + 增量更新计数（对比 lastRemindAt） |

### 数据模型

| 类名 | 说明 |
|------|------|
| `RetrievalRequest` | 检索请求体：knowledgeId、query、RetrievalConfig（含 30+ 配置项） |
| `RetrievalRequest.RetrievalConfig` | 检索配置：模式、topK、阈值、预处理开关、多路召回参数、BM25 参数、Rerank、MMR、上下文组装 |
| `SearchResultItem` | 检索结果项：index、similarity、content、source、channel、fileId、chunkIndex、distance、highlights、previewSnippet |
| `GraphExpansionResult` | 图谱扩展结果：entities、relations、expandedQuery |
| `KbRetrievalLog` | 检索日志实体：kbId、query、userId、hitCount、latencyMs、topScore、hasResult、createdAt |
| `KbUpdateRemind` | 更新提醒实体：kbId、enabled、cronExpr、channels、lastRemindAt |

### 数据访问层

| 类名 | 说明 |
|------|------|
| `KbRetrievalLogMapper` | 检索日志 MyBatis-Plus Mapper |
| `KbUpdateRemindMapper` | 更新提醒 MyBatis-Plus Mapper |