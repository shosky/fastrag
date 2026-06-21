# 模块三：检索与查询增强

> 对应前端：检索调试页 `debug.vue`、`SearchTestPanel.vue`、检索设置 `RetrievalSettingPanel.vue`
> 数据契约：`src/api/index.ts#searchRetrieval`、`src/mock/chunks.ts`、`src/mock/search-correction.ts`、`src/mock/query-rules.ts`、`src/mock/terminology.ts`、`src/mock/graph-expansion.ts`、`src/types/knowledge.ts#RetrievalSettingConfig`

---

## 一、业务概述

检索是 RAG 系统的能力核心，本模块承担「查询 → 召回 → 融合 → 重排 → 结果」全流程，并提供查询前增强（纠错/改写/同义词/图扩展）。

### 1.1 检索模式（对齐 `RetrievalSettingConfig.mode`）

| 模式 | 说明 |
|------|------|
| `vector` | 纯向量召回（Milvus ANN） |
| `fulltext` | 纯全文召回（ES BM25） |
| `hybrid` | 向量 + 全文混合 |

### 1.2 多路召回（对齐 `enableMultiRetrieval`）

开启后从四路召回并融合：

| 通道 | 数据源 | 召回量 |
|------|--------|--------|
| vector | Milvus | `vectorRecallCount` |
| fulltext | ES BM25 | `fulltextRecallCount` |
| graph | Neo4j 图扩展 | `graphRecallCount` |
| qa | QA 对库 | `qaRecallCount` |

融合策略（`fusionStrategy`）：
- **RRF**（Reciprocal Rank Fusion）：`score = Σ 1/(k+rank)`，k=60
- **weighted**：按 `vectorWeight` / `bm25Weight` 加权
- **interleave**：交错合并各路结果

### 1.3 重排序（对齐 rerank 配置）

| 方式 | 说明 |
|------|------|
| model rerank | 调 Rerank 模型（bge-reranker）二次打分 |
| LLM rerank | LLM 对候选打分（对齐 `llmRerank`） |
| MMR | 最大边际相关性，`lambda` 控制相关 vs 多样 |

### 1.4 查询增强（预处理，可组合开启）

| 增强 | 来源 | 对齐配置 |
|------|------|----------|
| 拼写纠错 + 拼音 | `search-correction.ts` | `autoCorrection` |
| 查询改写/扩展 | `query-rules.ts`（rewrite/expand） | `queryRewrite` |
| 同义词扩展 | `terminology.ts` | `synonyms` |
| 图谱扩展 | `graph-expansion.ts` | `graphExpansion.enabled/depth/maxEntities` |

### 1.5 上下文组装（context assembly）

| 策略 | 说明 |
|------|------|
| concat | 直接拼接 |
| parent_document | 扩展到父文档 |
| window | 滑动窗口（`windowSize`） |

参数：`maxTokens`、`order`（相关度/原始顺序）。

---

## 二、核心配置契约（`RetrievalSettingConfig`）

> 这是检索的权威配置结构，存储于 `kb.retrieval_config`，随每次检索请求传入。

```typescript
interface RetrievalSettingConfig {
  // 基础
  mode: 'vector' | 'fulltext' | 'hybrid'
  topK: number
  similarityThreshold: number
  scoreThreshold: number
  // 预处理
  autoCorrection: boolean
  queryRewrite: boolean
  graphExpansion: { enabled: boolean; depth: number; maxEntities: number }
  synonyms: boolean
  // 多路召回
  enableMultiRetrieval: boolean
  vectorRecallCount: number
  fulltextRecallCount: number
  graphRecallCount: number
  qaRecallCount: number
  fusionStrategy: 'rrf' | 'weighted' | 'interleave'
  // 重排
  rerankModel?: string
  enableLlmRerank: boolean
  enableMmr: boolean
  mmrLambda: number
  // 上下文组装
  contextStrategy: 'concat' | 'parent_document' | 'window'
  contextWindowSize: number
  contextMaxTokens: number
  contextOrder: 'relevance' | 'original'
  // BM25
  bm25RecallCount: number
  vectorWeight: number
  bm25Weight: number
  bm25SparseDropRate: number
}
```

---

## 三、检索结果契约（`SearchResultItem`）

对齐 `src/types/evaluation.ts`：

```typescript
interface SearchResultItem {
  index: number
  similarity: number        // 0-1
  content: string           // 分片内容（已高亮）
  source: string            // 文件名
  fileId: string
  chunkIndex: number
  distance: number
  highlights?: string[]     // 命中片段
  previewSnippet?: string   // 预览摘要
}
```

高亮：命中文本用 `<mark>` 包裹（对齐前端 `escapeHtml` + `<mark>` 逻辑）。

---

## 四、接口设计（API）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/retrieval/search` | 核心检索（写检索日志） |
| GET | `/kb/{kbId}/chunks/count` | 分片总数 |
| POST | `/query/suggest` | 纠错/拼音建议 |
| POST | `/query/expand-synonyms` | 同义词扩展 |
| POST | `/query-rules/apply` | 应用查询规则 |
| POST | `/graph/expand` | 图扩展（见模块4） |

### POST `/retrieval/search`

**请求**（对齐 `RetrievalRequest`）：
```json
{
  "knowledgeId": "1",
  "query": "小微ICT是什么",
  "config": { /* RetrievalSettingConfig */ }
}
```

**响应**：`ApiResponse<SearchResultItem[]>`

**逻辑**：检索 → 写 `addRetrievalLog(kbId, query, detail, extra)`（对齐前端 api 写日志）。日志内容：query / mode / topK / hits / duration。

---

## 五、核心逻辑设计

### 5.1 检索编排（Pipeline）

```java
@Service
public class RetrievalServiceImpl implements RetrievalService {
    public List<SearchResultItem> search(RetrievalRequest req) {
        long start = System.currentTimeMillis();
        RetrievalSettingConfig cfg = req.getConfig();
        String query = req.getQuery();

        // 1. 预处理（按开关组合）
        QueryContext qc = QueryContext.of(query);
        if (cfg.isAutoCorrection()) qc.apply(correctionService::suggest);
        if (cfg.isQueryRewrite())  qc.apply(ruleService::apply);
        if (cfg.isGraphExpansion().isEnabled()) qc.apply(g -> graphService.expand(req.getKnowledgeId(), g));
        if (cfg.isSynonyms())      qc.apply(synonymService::expand);

        // 2. 召回
        List<RecallItem> recalled;
        if (cfg.isEnableMultiRetrieval()) {
            recalled = multiRecall(req.getKnowledgeId(), qc, cfg);
        } else {
            recalled = singleRecall(req.getKnowledgeId(), qc, cfg); // 按模式选 vector/fulltext/hybrid
        }

        // 3. 融合
        List<RecallItem> fused = fuse(recalled, cfg.getFusionStrategy(), cfg);

        // 4. 重排
        List<RecallItem> reranked = rerank(qc, fused, cfg);

        // 5. 截断 + 高亮 + 组装
        List<SearchResultItem> items = toItems(reranked, cfg.getTopK(), qc);

        // 6. 写日志
        logService.addRetrievalLog(req.getKnowledgeId(), query,
            buildDetail(items), Map.of("mode", cfg.getMode(), "topK", cfg.getTopK(),
                "hits", items.size(), "duration", System.currentTimeMillis() - start));
        return items;
    }
}
```

### 5.2 单路召回

```java
List<RecallItem> singleRecall(String kbId, QueryContext qc, RetrievalSettingConfig cfg) {
    return switch (cfg.getMode()) {
        case VECTOR   -> vectorRecall(kbId, qc, cfg.getTopK());
        case FULLTEXT -> bm25Recall(kbId, qc, cfg.getTopK());
        case HYBRID   -> fuse(List.of(vectorRecall(...), bm25Recall(...)), cfg.getFusionStrategy(), cfg);
    };
}
```

- `vectorRecall`：调 `fastrag-ai` Embedding → Milvus ANN topK → 读分片原文。
- `bm25Recall`：ES `match` 查询 + BM25 评分 + `highlight` 取高亮片段。

### 5.3 多路召回与融合

```java
List<RecallItem> multiRecall(String kbId, QueryContext qc, RetrievalSettingConfig cfg) {
    List<RecallItem> all = new ArrayList<>();
    all.addAll(vectorRecall(kbId, qc, cfg.getVectorRecallCount()));
    all.addAll(bm25Recall(kbId, qc, cfg.getFulltextRecallCount()));
    if (cfg.getGraphRecallCount() > 0) all.addAll(graphRecall(kbId, qc, cfg.getGraphRecallCount()));
    if (cfg.getQaRecallCount() > 0)    all.addAll(qaRecall(kbId, qc, cfg.getQaRecallCount()));
    return all;
}

List<RecallItem> fuse(List<RecallItem> items, String strategy, RetrievalSettingConfig cfg) {
    return switch (strategy) {
        case "rrf"        -> rrfFuse(items, 60);
        case "weighted"   -> weightedFuse(items, cfg.getVectorWeight(), cfg.getBm25Weight());
        case "interleave" -> interleaveFuse(items);
        default           -> rrfFuse(items, 60);
    };
}
```

对齐 `chunks.ts` 的 `fuseRRF` / `fuseWeighted` / `fuseInterleave`。

### 5.4 重排序

```java
List<RecallItem> rerank(QueryContext qc, List<RecallItem> items, RetrievalSettingConfig cfg) {
    if (StringUtils.hasText(cfg.getRerankModel()))
        items = aiClient.rerank(cfg.getRerankModel(), qc.getFinalQuery(), items);  // bge-reranker
    if (cfg.isEnableLlmRerank())
        items = llmRerank(qc.getFinalQuery(), items);  // 对齐 llmRerank（token 命中+长度惩罚 可替换为真实 LLM）
    if (cfg.isEnableMmr())
        items = applyMmr(items, cfg.getMmrLambda(), cfg.getTopK()); // 对齐 applyMMR
    return items;
}
```

**MMR**（对齐 `applyMMR`）：
```
while 选不满 topK:
    argmax_i [ λ·sim(query, d_i) − (1−λ)·max_{d_j∈S} sim(d_i, d_j) ]
```

### 5.5 查询增强链

每个增强独立可组合，顺序：纠错 → 规则改写 → 图扩展 → 同义词。最终 `QueryContext.finalQuery` 进入召回。各增强实现见对应子服务（`search-correction` / `query-rules` / `terminology` / `graph-expansion`），其规则数据由模块8的平台配置管理。

### 5.6 高亮与摘要

- 高亮：BM25 通道用 ES `highlight`；向量通道在内存对命中 token 加 `<mark>`（对齐 `buildPreviewSnippet`）。
- 摘要：截取命中位置前后窗口文本（对齐 mock 的 previewSnippet 逻辑）。

---

## 六、性能与缓存

| 场景 | 策略 |
|------|------|
| 高频相同查询 | Redis 缓存检索结果（key = kbId + query + config hash，TTL 5min） |
| Embedding | 批量调用，结果缓存（同 query 复用向量） |
| ES 高亮 | 限制 `fragment_size` 与 `number_of_fragments` |
| Milvus | HNSW 索引，`ef` 随 topK 调整 |

---

## 七、前端覆盖核对表

| 前端能力 | 后端接口 |
|----------|----------|
| `searchRetrieval(req)` | `POST /retrieval/search` |
| `getChunkCount(kbId)` | `GET /kb/{kbId}/chunks/count` |
| `getSuggestion(query)` | `POST /query/suggest` |
| `expandQueryWithSynonyms(query)` | `POST /query/expand-synonyms` |
| `applyQueryRules(query)` | `POST /query-rules/apply` |
| `expandQueryWithGraph(kbId, query)` | `POST /graph/expand` |
| 检索日志写入 | 内部 `addRetrievalLog`（见模块5） |
