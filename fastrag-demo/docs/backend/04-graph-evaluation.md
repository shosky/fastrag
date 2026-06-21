# 模块四：知识图谱与 RAG 评估

> 对应前端：`KnowledgeGraphPanel.vue`、`NodeDetailPanel.vue`、`GraphSettingsPopup.vue`、`IndexManagementPanel.vue`、`RagEvaluationPanel.vue`、`EvaluationBenchmarkPanel.vue`、`SearchTestPanel.vue`（图谱扩展接入检索）
> 数据契约：`src/mock/knowledge-graph.ts`、`src/mock/graph-expansion.ts`、`src/mock/chunks.ts`（graph 通道）、`src/mock/benchmark.ts`、`src/mock/evaluation.ts`、`src/types/evaluation.ts`、`src/types/knowledge.ts`

---

## 一、业务概述

本模块分两块：**知识图谱**（构建/可视化/两套增强检索机制）与 **RAG 评估**（基准集 + 离线评估打分）。

### 1.1 知识图谱（重点：实现细节）

知识图谱包含**实体抽取→图存储→可视化→检索增强**全链路。审核发现前端存在**两套相互独立的"图谱增强检索"机制**，后端必须区分：

| 机制 | 名称 | 触发位置 | 工作方式 | 配置 |
|------|------|----------|----------|------|
| **机制A** | 图谱扩展（Query Expansion） | 检索预处理阶段 | 识别 query 中的实体 → 子图查询 → 把实体名拼接到 query → 走单一检索通道 | `enableGraphExpansion`(默认开) / `graphExpansionDepth`(1-2) / `graphMaxEntities`(1-20) |
| **机制B** | 图通道召回（Graph Recall） | 多路召回阶段 | 从实体出发沿关系到关联 chunk → 作为独立召回通道参与多路融合 | `enableMultiRetrieval` 开启时 + `graphRecallCount`(0-50) |

> ⚠️ 前端 mock 中两套机制用的是**不同**的硬编码实体列表，且与主图谱实体集不一致。后端必须用**单一实体数据源**，消除前端的不一致债。

**可视化要点（后端无关，避免过度设计）**：
- 节点坐标**完全由前端计算**（确定性圆形布局，非力导向）→ **后端不需要返回 x/y 坐标字段**
- 节点拖拽坐标**不持久化**（纯内存 Map）→ **后端不需要保存位置接口**
- 实体类型颜色是**前端硬编码常量**（`ENTITY_TYPE_COLORS`，12 类）→ 后端可选提供颜色配置存储，非必须
- 选中态高亮、缩放/平移、类型筛选在前端是**未完成/坏的**，后端勿据此设计

### 1.2 RAG 评估

| 能力 | 说明 |
|------|------|
| 基准集管理 | 上传 / 自动生成基准集（vector / graph 两种构建方式） |
| 评估运行 | 跑基准集 → 召回 → 生成答案 → 判定 → 汇总指标 |
| 指标 | Recall@1/3/5/10、答案准确率、综合分（70% recall@10 + 30% accuracy） |

---

## 二、数据模型

### 2.1 知识图谱存储（Neo4j）

> ⚠️ 重要：边的 `source/target` 用**实体 name**（非 id）引用，对齐前端 `GraphEdge`。后端必须保证实体名在 KB 内唯一，或提供 name→id 解析。

```cypher
// 实体节点（对齐 GraphNode，label 字段存实体类型如"服务/系统/客户类型"）
(:Entity {
  id,           // 内部 id（Neo4j elementId 风格，前端伪造为 4:{id}:{...}）
  kbId,
  name,         // 唯一（KB内），边按 name 引用
  normalized_name,  // 归一化名（大小写/别名/全半角）—— NodeDetailPanel 暴露此字段
  label,        // 实体类型名：服务/客户类型/方法论步骤/解决方案/编码/操作流程/功能/组织/系统/网络/业务/...
  type: 'entity',
  attributes: []    // 自定义属性数组（前端当前为空，需预留）
})

// 分片节点（前端 type='chunk' 暴露，GraphSettingsPopup.excludeChunkNodes 佐证存在）
(:Chunk {id, kbId, fileId, chunkIndex, content})

// 关系（label 是自由文本，无预定义枚举：使用/提供/依赖/管理/运营/负责/需要/支持/前置/关联/涉及/激励...）
(:Entity)-[:RELATES {label, weight}]->(:Entity)
(:Entity)-[:MENTIONS]->(:Chunk)   // 实体在哪些 chunk 中出现（图通道召回依据）
```

**关系类型**：前端边 label 是**自由文本**（非枚举）。后端若要规范化可建关系类型字典，但接口仍需接受自由文本 label。

### 2.2 图谱索引状态（MySQL，新增 —— 前端 IndexManagementPanel 当前全 Mock）

> 前端 `IndexManagementPanel` 的 `pendingBuild` 永远 0、状态永远"已就绪"、开始索引是假动作。后端必须实现真实状态机。

`kb_graph_index` 表：

| 字段 | 类型 | 说明 |
|------|------|------|
| kb_id | varchar(32) PK | |
| status | enum('idle','building','ready','failed') | 索引状态 |
| build_progress | int | 0-100（实体抽取+关系抽取总进度） |
| entity_extract_progress | int | 实体抽取进度 |
| relation_extract_progress | int | 关系抽取进度 |
| total_chunks | int | 待处理分片总数 |
| built_chunks | int | 已处理分片数 |
| entity_count | int | 已抽取实体数 |
| relation_count | int | 已抽取关系数 |
| index_version | int | 索引版本号（重建自增） |
| last_built_at | datetime | 最近构建完成时间 |
| build_error | text | 失败原因 |

### 2.3 图谱可视化设置（MySQL，新增 —— 前端 GraphSettingsPopup 当前未接通）

> 前端 `GraphSettingsPopup` 有 3 个配置（maxNodes/searchDepth/excludeChunkNodes）但 `apply` 只 Toast 不持久化。后端预留存储，待前端接通。

`kb_graph_settings` 表：`kb_id, max_nodes(default 100), search_depth(default 2), exclude_chunk_nodes(default 1)`

### 2.4 图扩展结果（对齐 `GraphExpansionResult`，运行时计算不持久化）

```typescript
{
  entities: GraphEntity[],     // {id, name, type} —— 注意 type 字段（非 label）
  relations: GraphRelation[],  // {source, target, label} —— source/target 用 name
  expandedQuery: string        // query + 实体名拼接
}
```

> ⚠️ 类型不一致：`GraphNode.label` 存实体类型，`GraphEntity.type` 也存实体类型 —— 两套类型定义字段名不同，后端 DTO 需统一或在接口层适配。

### 2.5 `kb_benchmark` / `kb_benchmark_question` 基准集

（见下文评估章节，对齐 `Benchmark` / `BenchmarkQuestion`）

---

### 2.3 `kb_benchmark` 基准集（对齐 `Benchmark`）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | varchar(32) PK | |
| kb_id | varchar(32) | |
| name / description | varchar | |
| has_gold_chunks | tinyint | 有标准答案分片 |
| has_gold_answer | tinyint | 有标准答案 |
| is_auto_generated | tinyint | 自动生成 |
| question_count | int | |
| created_at | datetime | |

### 2.4 `kb_benchmark_question` 基准题（对齐 `BenchmarkQuestion`）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint PK | |
| benchmark_id | varchar(32) | |
| question_index | int | |
| question | text | |
| gold_chunks | varchar(512) | 标准分片 id（逗号） |
| gold_answer | text | 标准答案 |

### 2.5 `kb_evaluation` 评估记录（对齐 `Evaluation`）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | varchar(32) PK | |
| kb_id | varchar(32) | |
| name | varchar(128) | |
| benchmark | varchar(32) | 基准集 id |
| benchmark_count | int | |
| data_count / completed_count | int | |
| duration | bigint | ms |
| recall_at_1/3/5/10 | decimal(5,4) | |
| answer_accuracy | decimal(5,4) | |
| overall_score | decimal(5,4) | 70% recall@10 + 30% accuracy |
| status | enum('pending','running','completed','failed') | |
| run_id | varchar(32) | 运行批次 |
| answer_model / judge_model | varchar(64) | |
| created_at | datetime | |

### 2.6 `kb_evaluation_result` 逐题结果（对齐 `EvaluationResult`）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint PK | |
| evaluation_id | varchar(32) | |
| question | text | |
| generated_answer | text | |
| retrieval_metrics | json | `{ recallAt1, recallAt3, recallAt5, recallAt10 }` |
| is_correct | tinyint | 答案判定 |
| judge_reason | varchar(255) | |

---

## 三、接口设计（API）

### 3.1 知识图谱数据与统计

| 方法 | 路径 | 说明 | 备注 |
|------|------|------|------|
| GET | `/kb/{kbId}/graph?maxNodes=&excludeChunks=` | 图谱数据 `{ nodes[], edges[] }` | 支持 maxNodes 限制；excludeChunks 过滤 chunk 节点 |
| GET | `/kb/{kbId}/graph/stats` | 统计 `{ entityCount, relationCount, entityTypes[] }` | entityTypes 按 count 降序；color 由前端按 label 映射，后端可不返回 |
| GET | `/kb/{kbId}/graph/nodes/{nodeId}` | 节点详情（含 normalized_name/attributes/关联 chunk） | NodeDetailPanel 需要 |

**响应说明**：
- `nodes[]`：`{ id, name, label, type }`，**不含 x/y 坐标**（前端自算布局）
- `edges[]`：`{ source, target, label }`，source/target 用**实体 name**
- `entityTypes[]`：`{ name, count }`（color 可选）

### 3.2 图谱构建与索引（新增 —— 前端当前全 Mock）

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/kb/{kbId}/graph/index` | 索引状态（status/progress/entity_count/relation_count/version） | KB viewer |
| POST | `/kb/{kbId}/graph/index/build` | 触发构建/重建（全量）`{ mode: 'full' }` | KB editor |
| POST | `/kb/{kbId}/graph/index/build` | 增量构建 `{ mode: 'incremental', fileIds[] }` | KB editor |
| GET | `/kb/{kbId}/graph/index/build-status` | 构建进度轮询（SSE 或轮询） | KB viewer |
| GET | `/kb/{kbId}/graph/settings` | 图谱设置（maxNodes/searchDepth/excludeChunkNodes） | KB viewer |
| PUT | `/kb/{kbId}/graph/settings` | 保存图谱设置 | KB editor |

### 3.3 图扩展（机制A：Query Expansion）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/graph/expand` | 图扩展 `{ kbId, query, depth, maxEntities }` → `GraphExpansionResult` |

**请求**：`{ kbId, query, depth(默认1), maxEntities(默认5) }`
> ⚠️ 前端 `expandQueryWithGraph` 签名收 `knowledgeId` 但实现完全没用（假隔离）。后端**必须按 kbId 真正隔离**图谱。

**响应**：`{ entities: [{id,name,type}], relations: [{source,target,label}], expandedQuery }`

### 3.4 图通道召回（机制B：Graph Recall）

图通道召回**不单独暴露接口**，作为检索模块（模块3）`POST /retrieval/search` 多路召回的内部通道。后端在检索服务内部实现：

```
graphRecall(kbId, query, count):
  1. 在 query 中识别实体（复用机制A的实体识别）
  2. 从命中实体出发，沿 MENTIONS 关系找到关联 chunk
  3. 按关系跳数/权重排序，取前 count 个
  4. 统一打分规范（与向量/BM25 通道量纲一致）
```

> ⚠️ 前端 mock 的图通道是"chunk 内容命中实体关键词"（非真子图遍历），且用第三套硬编码实体词表。后端必须实现真正的"实体→关系→chunk"子图遍历，并复用统一实体源。

**与机制A的区别**：
- 机制A 改写 query（拼接实体名），结果进入单一检索通道
- 机制B 是独立召回通道，参与多路融合（RRF/weighted/interleave）
- 两者可同时开启（前端默认机制A开），后端需支持并发

### 3.5 基准集与评估（同前）

### 3.2 基准集

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/kb/{kbId}/benchmarks` | 列表 |
| GET | `/kb/{kbId}/benchmarks/{id}` | 题目详情 |
| POST | `/kb/{kbId}/benchmarks` | 上传基准 `{ name, description, questionCount }` |
| POST | `/kb/{kbId}/benchmarks/generate` | 自动生成（见下） |
| DELETE | `/kb/{kbId}/benchmarks/{id}` | 删除 |

**POST `/benchmarks/generate`** 请求（对齐 `BenchmarkGenerateConfig`）：
```json
{
  "name": "...", "description": "...",
  "buildMethod": "vector|graph",
  "llmModel": "qwen3-32b",
  "questionCount": 10,
  "candidateChunkCount": 50,
  "concurrency": 4,
  "expandChunkCount": 3
}
```
逻辑：抽取候选分片（graph 模式扩大召回）→ LLM 生成问答对 → 落库。

### 3.3 RAG 评估

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/kb/{kbId}/evaluations` | 评估记录列表 |
| GET | `/kb/{kbId}/evaluations/{id}` | 评估详情（含逐题） |
| POST | `/kb/{kbId}/evaluations/run` | 运行评估（异步，返回详情） |
| DELETE | `/kb/{kbId}/evaluations/{id}` | 删除 |

**POST `/evaluations/run`** 请求（对齐 `EvaluationStartConfig`）：
```json
{ "name": "...", "benchmark": "bench-1", "answerModel": "qwen3-32b", "judgeModel": "qwen3-32b" }
```

---

## 四、核心逻辑设计

### 4.1 图扩展（对齐 `graph-expansion.ts`）

### 4.1 图谱构建流水线（新增 —— 前端 IndexManagementPanel 全 Mock）

文件加工完成（模块2）后，触发图谱构建。**异步任务 + 状态机 + 进度上报**：

```java
@Service
public class GraphBuildService {
    @Transactional
    public void build(String kbId, BuildMode mode, List<String> fileIds) {
        GraphIndex idx = indexMapper.selectByKb(kbId);
        if (idx.getStatus() == BUILDING) throw new BizException("图谱正在构建中");
        idx.markBuilding(mode);  // status=building, progress=0
        indexMapper.update(idx);
        // 发 MQ 触发异步构建
        rabbitTemplate.convertAndSend("graph.build.queue",
            new GraphBuildTask(kbId, mode, fileIds));
    }
}

@RabbitListener(queues = "graph.build.queue")
public class GraphBuildConsumer {
    public void onBuild(GraphBuildTask task) {
        String kbId = task.getKbId();
        GraphIndex idx = indexMapper.selectByKb(kbId);
        try {
            // 1. 选取待处理分片（全量=所有 chunk；增量=指定 fileIds 的 chunk）
            List<Chunk> chunks = task.getMode() == FULL
                ? chunkMapper.selectAll(kbId)
                : chunkMapper.selectByFiles(kbId, task.getFileIds());
            idx.setTotalChunks(chunks.size());

            int built = 0;
            for (Chunk c : chunks) {
                // 2. 实体抽取（NER）：LLM 或规则，识别实体 + 类型 + 归一化名
                List<ExtractedEntity> entities = nerService.extract(c.getContent());
                // 3. 关系抽取：识别实体间关系（label + weight）
                List<ExtractedRelation> rels = relationService.extract(c.getContent(), entities);
                // 4. 写 Neo4j：upsert 实体节点（按 normalized_name 去重）+ 关系边 + MENTIONS(chunk)
                neo4jRepo.upsertEntities(kbId, entities);
                neo4jRepo.upsertRelations(kbId, rels);
                neo4jRepo.linkChunks(kbId, entities, c.getId());  // 供图通道召回

                built++;
                idx.updateProgress(built);  // 更新 entity/relation count + progress
                indexMapper.update(idx);
            }
            idx.markReady();  // status=ready, progress=100, version++
            indexMapper.update(idx);
        } catch (Exception e) {
            idx.markFailed(e.getMessage());
            indexMapper.update(idx);
        }
    }
}
```

**关键设计**：
- 实体节点按 `normalized_name` 在 KB 内去重（保证 name 唯一，边按 name 引用）。
- `(:Entity)-[:MENTIONS]->(:Chunk)` 关系是**图通道召回的基础**（机制B 从实体找 chunk）。
- 构建进度通过 `GET /graph/index/build-status` 轮询或 SSE 推送给 IndexManagementPanel。
- 增量构建：文件更新后只重建该文件的实体/关系（按 fileIds 过滤）。

### 4.2 实体识别（NER）

> 前端 `mockRecognizeEntities` 是 6 个词的 `includes` 匹配。后端需真实 NER。

```java
@Service
public class NerService {
    /** 从文本/查询中识别已知实体（命中图谱中的实体） */
    public List<GraphEntity> recognizeKnown(String kbId, String text) {
        // 方案1：Neo4j 全文索引（实体 name 建全文索引）+ 模糊匹配
        // 方案2：字典/AC自动机（实体名 + alias 别名表）
        // 方案3：LLM NER（对长文本）
        return neo4jRepo.matchEntitiesByText(kbId, text);
    }

    /** 从分片内容抽取新实体（构建时用） */
    public List<ExtractedEntity> extract(String content) {
        // LLM Prompt: "抽取以下文本中的实体及其类型(服务/系统/客户类型/...)"
        return aiClient.extractEntities(content);
    }
}
```

**实体归一化**：name 经大小写/全半角/别名映射得 `normalized_name`，保证同一实体不重复入库。

### 4.3 图扩展（机制A）

对齐 `graph-expansion.ts` + `expandQueryWithGraph`：

```java
@Service
public class GraphExpansionService {
    public GraphExpansionResult expand(String kbId, String query, int depth, int maxEntities) {
        // 1. 实体识别：在 query 中命中图谱已知实体
        List<GraphEntity> entities = nerService.recognizeKnown(kbId, query);
        if (entities.isEmpty()) return GraphExpansionResult.empty(query);

        // 2. 子图查询：从命中实体出发，depth 跳内扩展（BFS），限制 maxEntities
        Set<GraphEntity> allEntities = new LinkedHashSet<>(entities);
        List<GraphRelation> relations = new ArrayList<>();
        bfsExpand(kbId, entities, depth, maxEntities, allEntities, relations);

        // 3. 扩展查询：把扩展到的实体名拼接到 query（前端逻辑：expandedQuery = query + " " + names.join(" "))
        String expanded = query + " " + allEntities.stream()
            .map(GraphEntity::getName).distinct()
            .collect(joining(" "));
        return new GraphExpansionResult(new ArrayList<>(allEntities), relations, expanded);
    }
}
```

> 前端 mock 字典查表只有 3 个实体有数据；后端需真实图遍历（BFS/DFS 带 depth/maxNodes 限制）。
> ⚠️ 缓存：前端 `useGraphExpansion` 用内存 Map（无 TTL/LRU）。后端建议 Redis 缓存（key = kbId+query+depth+maxEntities，TTL 10min）。

### 4.4 图通道召回（机制B）

对齐 `chunks.ts#graphSubgraphRecall`，但改为真实子图遍历：

```java
public class GraphRecallChannel implements RecallChannel {
    public List<RecallItem> recall(String kbId, String query, int count) {
        // 1. 识别 query 中的实体（复用 NER）
        List<GraphEntity> entities = nerService.recognizeKnown(kbId, query);
        if (entities.isEmpty()) return List.of();  // 前端逻辑：没命中返回空，不参与融合

        // 2. 从实体沿 MENTIONS 关系找到关联 chunk（真实子图召回）
        List<Chunk> chunks = neo4jRepo.findChunksByEntities(kbId,
            entities.stream().map(GraphEntity::getName).toList(), count);

        // 3. 打分（关系跳数 + 出现频次），并归一化到与向量/BM25 一致的量纲
        return chunks.stream()
            .map(c -> new RecallItem(c, normalizeScore(c)))
            .toList();
    }
}
```

> ⚠️ 前端 mock 是"chunk 内容命中实体词 +2 分"（整数，量纲与向量不一致）。后端必须：1) 真正子图遍历；2) 统一打分规范（否则 weighted 融合受影响）。
> ⚠️ 图通道返回空时不参与融合（前端逻辑），后端保持一致。

**机制A vs 机制B 协同**：两者可同时开启。机制A 在检索**前**改写 query，机制B 在检索**中**作为独立通道。后端检索服务（模块3）的编排顺序：预处理（含机制A）→ 多路召回（含机制B）→ 融合 → 重排。

### 4.5 评估运行（核心，异步）

对齐 `evaluation.ts#runEvaluation`：

```java
@Service
public class EvaluationService {
    @Async
    public EvaluationDetail run(String kbId, EvaluationStartConfig cfg) {
        Evaluation eval = Evaluation.pending(kbId, cfg);
        evaluationMapper.insert(eval);
        long start = System.currentTimeMillis();
        Benchmark bench = benchmarkMapper.selectById(cfg.getBenchmark());
        List<BenchmarkQuestion> questions = bench.questions();

        int completed = 0;
        for (BenchmarkQuestion q : questions) {
            // 1. 召回（调模块3）
            List<SearchResultItem> hits = retrievalService.search(
                new RetrievalRequest(kbId, q.getQuestion(), defaultRetrievalConfig()));
            // 2. 生成答案
            String answer = aiClient.generate(cfg.getAnswerModel(), q.getQuestion(), hits);
            // 3. 判定
            AnswerJudgeResult judge = judge(cfg.getJudgeModel(), answer, q.getGoldAnswer());
            // 4. 计算 recall@k（命中 gold_chunks）
            RetrievalMetrics metrics = computeRecall(hits, q.getGoldChunks());
            eval.addResult(q, answer, metrics, judge);
            completed++;
            evaluationMapper.updateProgress(eval.getId(), completed);
        }
        // 5. 汇总
        eval.aggregate(start);  // recall@1/3/5/10 + accuracy + overall(0.7*r@10+0.3*acc)
        eval.setStatus(COMPLETED);
        evaluationMapper.update(eval);
        return eval.toDetail();
    }
}
```

**Recall@K 计算**：`recall@k = |hitChunks ∩ goldChunks| / |goldChunks|`，hitChunks 为前 K 个命中的 chunk_id。

**答案判定（对齐 `judgeAnswer`）**：规则版——数字命中 ≥60% + 关键词命中阈值；生产可替换为 LLM-as-Judge。

**综合分**：`overallScore = 0.7 * recallAt10 + 0.3 * answerAccuracy`。

---

## 五、前端覆盖核对表（含二次审核修正）

| 前端组件/mock | 后端接口 | 状态 |
|---------------|----------|------|
| `getGraphData(kbId)` | `GET /kb/{kbId}/graph?maxNodes=&excludeChunks=` | ✅ |
| `getGraphStats(kbId)` | `GET /kb/{kbId}/graph/stats` | ✅ |
| NodeDetailPanel 节点详情 | `GET /kb/{kbId}/graph/nodes/{nodeId}` | ✅ 新增 |
| `expandQueryWithGraph` | `POST /graph/expand`（按 kbId 真隔离） | ✅ 修正 |
| `mockRecognizeEntities` | NER 服务（recognizeKnown） | ✅ 新增 |
| `mockQueryGraph` | 子图 BFS 遍历 | ✅ 新增 |
| IndexManagementPanel 索引管理（前端全 Mock） | `/kb/{kbId}/graph/index[/build]` | ✅ 新增 |
| GraphSettingsPopup 设置（前端未接通） | `GET/PUT /kb/{kbId}/graph/settings` | ✅ 新增 |
| `graphSubgraphRecall`（图通道，第三套硬编码词） | 检索内部 GraphRecallChannel（真实子图） | ✅ 新增 |
| `getBenchmarks/.../generateBenchmark/deleteBenchmark` | `/kb/{kbId}/benchmarks*` | ✅ |
| `runEvaluation/getEvaluations/deleteEvaluation` | `/kb/{kbId}/evaluations*` | ✅ |

---

## 六、二次审核发现的问题与修正

### 6.1 已修正的错误

| 问题 | 修正 |
|------|------|
| 文档原设计 `PUT /graph/nodes/{id}/position` 持久化坐标 | ❌ 删除。坐标前端自算、拖拽不持久化，后端无需此接口 |
| 文档原 node 类型含 `x/y` | ❌ 移除。前端确定性圆形布局，后端不返回坐标 |
| 未区分两套图谱增强机制 | ✅ 明确机制A（Query Expansion）vs 机制B（Graph Recall） |
| 图扩展 `knowledgeId` 参数假隔离 | ✅ 标注后端必须按 kbId 真隔离 |

### 6.2 前端实现债（后端需消除）

| 债务 | 后端对策 |
|------|----------|
| 三套硬编码实体词表不一致（knowledge-graph / graph-expansion / chunks.entityKeywords） | 单一实体数据源（Neo4j），消除不一致 |
| `GraphNode.label` vs `GraphEntity.type` 字段名不同 | DTO 层统一或适配 |
| `getChunkCount` 忽略 kbId（所有 KB 返回同数） | 按 kbId 真隔离 |
| 索引管理全 Mock（pendingBuild 永远 0） | 实现真实状态机 + 进度上报 |
| 图通道打分量纲不一致（整数 +2 vs 浮点） | 统一打分规范 |
| 图扩展缓存无 TTL/LRU | Redis 缓存 + TTL |
| 选中态高亮 CSS 坏的、类型筛选未实现 | 前端待修，后端无关 |

### 6.3 预留字段（NodeDetailPanel 暴露的后端 schema 暗示）

NodeDetailPanel 显示的字段暗示后端图数据库 schema：
- `normalized_name` —— 实体名归一化（后端 NER 必做）
- `attributes: []` —— 实体自定义属性数组（当前空，需预留）
- Neo4j 风格 ID `4:{id}:{...}` —— 内部 ID 体系
- `kb_id` —— 节点归属知识库（前端硬编码，后端动态）

