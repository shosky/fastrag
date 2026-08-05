# fastrag-graph-eval

> 知识图谱构建与检索质量评测模块。

## 模块职责

- **知识图谱构建**：从知识库分块中抽取实体和关系，写入 GraphStore（Neo4j/MySQL）
- **基准测试（Benchmark）**：手动创建或 LLM 自动生成 QA 对作为评测基准
- **评测执行**：运行检索链路计算 recall@1/3/5/10 等指标
- **图谱可视化**：提供节点/边数据供前端展示

## 对外 REST 端点

### 图谱管理

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/kb/{kbId}/graph/build` | 触发图谱构建 |
| GET | `/api/kb/{kbId}/graph/status` | 构建状态 |
| GET | `/api/kb/{kbId}/graph/nodes` | 节点列表 |
| GET | `/api/kb/{kbId}/graph/edges` | 边列表 |
| GET | `/api/kb/{kbId}/graph/search` | 搜索图谱 |
| GET | `/api/kb/{kbId}/graph/stats` | 统计信息 |
| DELETE | `/api/kb/{kbId}/graph` | 清空图谱 |

### 基准测试

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/kb/{kbId}/benchmarks` | 列表 |
| GET | `/api/kb/{kbId}/benchmarks/{id}` | 详情 |
| GET | `/api/kb/{kbId}/benchmarks/{id}/questions` | 问题列表 |
| POST | `/api/kb/{kbId}/benchmarks` | 创建 |
| POST | `/api/kb/{kbId}/benchmarks/generate` | LLM 自动生成问题 |
| POST | `/api/kb/{kbId}/benchmarks/{id}/run` | 执行 |
| DELETE | `/api/kb/{kbId}/benchmarks/{id}` | 删除 |

### 评测

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/kb/{kbId}/evaluations` | 列表 |
| GET | `/api/kb/{kbId}/evaluations/{id}` | 详情 |
| POST | `/api/kb/{kbId}/evaluations` | 创建 |
| POST | `/api/kb/{kbId}/evaluations/{id}/run` | 执行 |
| DELETE | `/api/kb/{kbId}/evaluations/{id}` | 删除 |

## 模块依赖

| 方向 | 模块 | 说明 |
|------|------|------|
| 依赖 | common, security, infra, ai, publish | — |
| 被依赖 | knowledge, bootstrap（2 个模块） | |

## 关键类说明

| 类 | 说明 |
|----|------|
| `GraphService` | 图谱构建、查询、统计。通过 `GraphStore` 接口操作底图层 |
| `BenchmarkService` | 基准 CRUD、LLM 自动生成 QA、执行基准检索计算指标 |
| `GraphController` | `/api/kb/{kbId}/graph` 端点 |
| `BenchmarkController` | `/api/kb/{kbId}/benchmarks` 端点 |
| `EvaluationController` | `/api/kb/{kbId}/evaluations` 端点 |
| `KbBenchmark` | 基准测试集实体 |
| `KbBenchmarkQuestion` | 测试问题实体 |
| `KbEvaluationResult` | 评测结果（含 recall@k） |
