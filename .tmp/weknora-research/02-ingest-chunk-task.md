# 调研任务：WeKnora 文档解析与分片流水线（维度 2/5）

## 背景
为 FastRAG 对标调研。FastRAG 现状锚点：Java 侧自研 8 格式解析（PDF/DOCX/PPTX/XLSX/文本/图片OCR/音频ASR/视频双路），MediaExtractor/FFmpeg，父子两级分片（kb_chunk，title/heading_path 结构感知），KB 级解析策略（模板/扩展名自动匹配/文档级覆盖），OcrNoiseFilter 行级清洗，两阶段上传（MinIO+pending→RabbitMQ 异步），AI 分片（SSE+PDF layout 分析+区域框选）。本维度聚焦 WeKnora 的**解析与分块**，架构/检索/Agent 由其他任务负责。

## 调研对象（只读）
- `D:\Workspace\python\github\WeKnora\docreader\`（parser/、splitter/、models/、proto/、main.py、config.py、tests/、testdata/）
- `D:\Workspace\python\github\WeKnora\internal\textconv\`、`internal\filetransport\`、`internal\datasource\`（数据源同步：飞书/Notion/GitLab 等如何接入）
- `internal/application` 或 handler 中与文档上传/处理/分块相关的服务（找到再读）

## 必答问题
1. 支持的文档格式完整清单；每类格式用什么解析引擎（anydoc? pypdfium2? python-docx? openpyl?）; OCR 方案是什么？
2. docreader 的解析结果数据结构（proto 定义）：块/元素类型（标题/段落/表格/图片…）、元数据如何表达？
3. `splitter/` 分块策略全集：固定长度？递归？标题感知？语义？表格/图片如何成块？有没有父子分块？多模态块（图片在 chunk 中的表示）？chunk 大小/重叠如何配置（全局/KB 级/文档级）？
4. 图片处理：嵌入图片是否提取、存储、参与检索/引用（多模态召回）？
5. 异步处理模型：解析任务如何排队（任务队列? 状态机? 重试?）；对比 FastRAG 的 RabbitMQ 两阶段。
6. `internal/datasource/` 外部数据源同步：支持哪些源、增量同步机制、目录结构保留怎么实现？
7. 解析配置：有没有类似 FastRAG"解析策略"的 KB 级/文档级配置与模板？
8. 音频/视频处理有没有？
9. 解析质量防线：OCR 噪声过滤、版面分析、跨页合并之类有没有？

## 输出要求（中文 markdown）
- **格式×引擎×分块矩阵**（一张表）
- **分块策略详解**（带 `文件:行号` 证据与关键代码摘录）
- **优点清单**（机制→证据→为什么好→对 FastRAG 的借鉴意义）
- **弱点/风险**
- 禁止臆测；读不到就说明
