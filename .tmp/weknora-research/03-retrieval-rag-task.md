# 调研任务：WeKnora 检索与 RAG 管线（维度 3/5）

## 背景
为 FastRAG 对标调研。FastRAG 现状锚点：Milvus 向量 + MySQL LIKE 关键词混合 + Rerank 重排；QA 对通道加权 0.3；查询改写/同义扩展为 stub（query_rule 零消费、suggest 占位）；图检索通道有降级容错；评测走自带精简检索管线；已知"截断召回"问题。本维度聚焦 WeKnora 的**检索/RAG 回答链路**（含知识图谱/Wiki 如果有），解析分块/Agent 运行时/工程化由其他任务负责。

## 调研对象（只读）
- `D:\Workspace\python\github\WeKnora\internal\searchutil\`、`internal\reranking\`、`internal\embedpolicy\`、`internal\modelcontext\`
- `internal/application`（或 service 层）中的检索/对话/知识库检索服务；`internal/handler` 检索与 chat 端点；`internal/models` 中 chunk/knowledge 相关模型
- 根目录 `rerank_server_demo.py`；config 中检索相关配置
- 知识图谱/Wiki 相关实现（README 提到 Wiki 整理、知识图谱——找到代码证据）

## 必答问题
1. 向量检索实现：用什么向量库/索引？embedding 如何管理（embedpolicy 是什么）？
2. 混合检索：向量+全文/关键词如何融合（权重? RRF?）？全文检索用什么（Postgres FTS? 其他）？
3. 重排：`reranking/` 支持哪些 reranker、如何接入、失败降级？
4. 查询理解：有没有查询改写/同义扩展/多查询/HyDE 之类？
5. 引用溯源：回答如何携带原文引用（chunk→原文定位）、前端引用呈现的后端支撑？
6. 多知识库检索、检索参数（topK/阈值/权重）如何配置（系统级/知识库级/会话级）？
7. 知识图谱：有没有 GraphRAG？Wiki 自动整理的实现机制？
8. 对话链路：检索→组装 prompt→流式回答的编排；多轮对话与历史压缩；记忆（跨会话长期记忆 README 提到——机制是什么）？
9. 评测/质量度量有没有？
10. 分块编辑/比对/回滚（README 提到）如何实现？

## 输出要求（中文 markdown）
- **检索管线图**（文字版：查询进来每一步过什么）
- **核心机制逐条**（带 `文件:行号` 证据）
- **优点清单**（机制→证据→为什么好→对 FastRAG 借鉴意义）
- **弱点/风险**
- 禁止臆测；区分"代码证实"与"仅文档/README 宣称"
