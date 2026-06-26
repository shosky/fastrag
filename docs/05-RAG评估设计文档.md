# RAG 评估设计文档

## 1. 功能概述

RAG 评估用于衡量检索增强生成的质量，包括召回率、答案准确率等指标。

## 2. 数据模型

### 2.1 数据库表 `kb_evaluation`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(32) PK | 评估ID |
| kb_id | VARCHAR(32) FK | 知识库ID |
| name | VARCHAR(128) | 评估名称 |
| status | VARCHAR(16) | 状态(pending/running/completed/failed) |
| benchmark | VARCHAR(128) | 使用的基准 |
| run_id | VARCHAR(64) | 执行运行ID |
| answer_model | VARCHAR(128) | 答案模型 |
| judge_model | VARCHAR(128) | 评判模型 |
| benchmark_count | INT | 基准问题数 |
| data_count | INT | 数据总数 |
| completed_count | INT | 已完成数 |
| duration | BIGINT | 执行时长(ms) |
| overall_score | DECIMAL | 总分 |
| recall_at_1/3/5/10 | DECIMAL | 召回率指标 |
| answer_accuracy | DECIMAL | 答案准确率 |

### 2.2 数据库表 `kb_evaluation_result`

| 字段 | 类型 | 说明 |
|------|------|------|
| evaluation_id | VARCHAR(32) FK | 评估ID |
| question | TEXT | 问题 |
| generated_answer | TEXT | 生成的答案 |
| retrieval_metrics | JSON | 检索指标 |
| is_correct | TINYINT | 是否正确 |
| judge_reason | TEXT | 评判理由 |

## 3. API 接口

### 3.1 评估列表
```
GET /api/kb/{kbId}/evaluations
```

### 3.2 评估详情
```
GET /api/kb/{kbId}/evaluations/{id}
```

### 3.3 运行评估
```
POST /api/kb/{kbId}/evaluations/run
```

### 3.4 删除评估
```
DELETE /api/kb/{kbId}/evaluations/{id}
```

## 4. 当前状态

**后端：** ⚠️ CRUD 可用，评估执行未实现
**前端：** ✅ 已对接 API

## 5. 当前状态

**后端：** ⚠️ CRUD 可用，`run` 端点只创建 pending 记录，实际评估执行（LLM 生成答案 + 评判）未实现
**前端：** ✅ 已对接 API

## 6. 待实现

- [ ] 评估执行引擎（LLM 生成答案 + 评判）
- [ ] 召回率计算
