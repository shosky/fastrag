# fastrag-knowledge -- 知识库核心模块

> RAG 系统中最大、最核心的业务模块，负责知识库全生命周期管理与文档摄入管道。

## 模块职责

- **知识库 CRUD**：创建、查询、更新、删除知识库，支持分类、标签、权限管理
- **文件管理**：上传、下载、预览、复制、跨库移动、软删除/回收站
- **文档解析**：支持 PDF、DOCX、PPTX、XLSX、TXT/MD、图片(OCR)、音频(ASR)、视频(ASR+关键帧OCR) 的多模态解析
- **文本分块**：基于规则的分块引擎，支持 PDF 页感知分块、音视频时间轴分段分块、重叠切分
- **向量存储**：分片 Embedding 生成与 Milvus 向量库写入
- **QA 对管理**：手动/自动 QA 问答对的新增、确认、删除
- **解析策略**：每个知识库可配置多种解析策略（LLM 增强、VLM 模型、高级参数）
- **知识图谱构建**：异步 LLM 实体/关系抽取，Neo4j 图存储，支持增量构建
- **发布管理**：知识发布/撤销、发布计划、版本管理、知识重置
- **文件夹组织**：知识库内文件按树形文件夹分类管理
- **标签体系**：全局标签管理与知识库-标签关联
- **分类管理**：知识库分类 CRUD，含使用量统计

## 对外 REST 端点

### 知识库 `/api/kb`

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/kb` | 分页列表（keyword/category 筛选） |
| GET | `/api/kb/categories` | 获取所有分类 |
| GET | `/api/kb/{id}` | 获取知识库详情 |
| POST | `/api/kb` | 创建知识库 |
| PUT | `/api/kb/{id}` | 更新知识库信息 |
| DELETE | `/api/kb/{id}` | 删除知识库 |

### 文件 `/api/kb/{kbId}/files`

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/kb/{kbId}/files` | 文件列表 |
| GET | `/api/kb/{kbId}/files/deleted` | 已删除文件（回收站） |
| POST | `/api/kb/{kbId}/files` | 上传文件（multipart） |
| POST | `/api/kb/{kbId}/files/{id}/process` | 触发文件处理（chunk/qa 模式） |
| POST | `/api/kb/{kbId}/files/{id}/retry` | 重新处理失败文件 |
| PUT | `/api/kb/{kbId}/files/{id}` | 更新文件信息 |
| DELETE | `/api/kb/{kbId}/files/{id}` | 软删除文件 |
| POST | `/api/kb/{kbId}/files/{id}/restore` | 从回收站恢复 |
| DELETE | `/api/kb/{kbId}/files/{id}/permanent` | 永久删除 |
| DELETE | `/api/kb/{kbId}/files/recycle-bin` | 清空回收站 |
| POST | `/api/kb/{kbId}/files/{id}/copy` | 复制文件 |
| POST | `/api/kb/{kbId}/files/{id}/move` | 跨知识库移动文件 |
| GET | `/api/kb/{kbId}/files/{id}/processing-status` | 查询处理进度 |
| GET | `/api/kb/{kbId}/files/{id}/preview` | 预览分块结果 |
| GET | `/api/kb/{kbId}/files/{id}/download` | 下载原文件 |
| GET | `/api/kb/{kbId}/files/{id}/segments/{chunkIndex}` | 下载音频切片 |
| GET | `/api/kb/{kbId}/files/{id}/images/{imageKey}` | 下载 PDF 页面图片 |

### 文件夹 `/api/kb/{kbId}/folders`

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/kb/{kbId}/folders` | 文件夹树 |
| POST | `/api/kb/{kbId}/folders` | 创建文件夹 |
| GET | `/api/kb/{kbId}/folders/{id}/name` | 获取文件夹名称 |
| PUT | `/api/kb/{kbId}/folders/{id}` | 重命名文件夹 |
| DELETE | `/api/kb/{kbId}/folders/{id}` | 删除文件夹 |

### 分片 `/api/kb/{kbId}/chunks`

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/kb/{kbId}/chunks` | 分页查询分片 |
| GET | `/api/kb/{kbId}/chunks/count` | 总分片数 |
| GET | `/api/kb/{kbId}/chunks/{id}` | 分片详情 |
| POST | `/api/kb/{kbId}/chunks` | 手动新增分片 |
| PUT | `/api/kb/{kbId}/chunks/{id}` | 更新分片内容 |
| DELETE | `/api/kb/{kbId}/chunks/{id}` | 删除分片 |
| DELETE | `/api/kb/{kbId}/chunks/batch` | 批量删除分片 |

### QA 对 `/api/kb/{kbId}/qa-pairs`

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/kb/{kbId}/qa-pairs` | QA 对列表 |
| POST | `/api/kb/{kbId}/qa-pairs` | 创建 QA 对 |
| PUT | `/api/kb/{kbId}/qa-pairs/{id}` | 更新 QA 对 |
| DELETE | `/api/kb/{kbId}/qa-pairs/{id}` | 删除 QA 对 |
| POST | `/api/kb/{kbId}/qa-pairs/{id}/confirm` | 确认 QA 对 |

### 解析策略 `/api/kb/{kbId}/parse-strategies`

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/kb/{kbId}/parse-strategies` | 策略列表 |
| GET | `/api/kb/{kbId}/parse-strategies/{id}` | 策略详情 |
| POST | `/api/kb/{kbId}/parse-strategies` | 创建策略 |
| PUT | `/api/kb/{kbId}/parse-strategies/{id}` | 更新策略 |
| DELETE | `/api/kb/{kbId}/parse-strategies/{id}` | 删除策略 |
| POST | `/api/kb/{kbId}/parse-strategies/{id}/set-default` | 设为默认策略 |
| GET | `/api/kb/{kbId}/parse-strategies/resolve` | 按扩展名解析策略 |
| POST | `/api/kb/{kbId}/parse-strategies/conflicts` | 检测策略冲突 |

### 发布管理 `/api/kb/{kbId}/publish`

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/kb/{kbId}/publish/history` | 发布历史 |
| POST | `/api/kb/{kbId}/publish/{knowledgeId}` | 发布知识 |
| POST | `/api/kb/{kbId}/publish/{knowledgeId}/revoke` | 撤销发布 |
| POST | `/api/kb/{kbId}/publish/plans` | 创建发布计划 |
| GET | `/api/kb/{kbId}/publish/plans` | 发布计划列表 |
| GET | `/api/kb/{kbId}/publish/plans/{planId}/execution` | 计划执行详情 |
| GET | `/api/kb/{kbId}/publish/online-version` | 线上版本 |
| GET | `/api/kb/{kbId}/publish/offline-version` | 线下版本 |
| GET | `/api/kb/{kbId}/publish/strategy-effect` | 策略效果 |

### 全局端点

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/kb-categories` | 知识库分类列表 |
| POST | `/api/kb-categories` | 创建分类 |
| PUT | `/api/kb-categories/{id}` | 更新分类 |
| DELETE | `/api/kb-categories/{id}` | 删除分类 |
| GET | `/api/kb-tags` | 全局标签列表 |
| GET | `/api/parse-strategy-templates` | 解析策略模板列表 |

## 模块依赖

| 方向 | 模块 | 说明 |
|------|------|------|
| 依赖 | fastrag-common | 通用工具、注解(@Loggable)、枚举、ApiResponse |
| 依赖 | fastrag-security | 用户认证、SecurityUtil |
| 依赖 | fastrag-infra | MinIO 对象存储、RabbitMQ 消息队列、Milvus 向量库、GraphStore |
| 依赖 | fastrag-ai | LLM/Embedding/VLM 服务、OCR 识别、ASR 语音转写 |
| 依赖 | fastrag-platform | 模型管理(ModelRecord)、系统配置(SysConfig)、通知(SysNotification) |
| 依赖 | fastrag-publish | 日志服务(LogService)、发布相关 |
| 被依赖 | fastrag-retrieval | 检索模块消费 Chunk 数据 |
| 被依赖 | fastrag-operation | 运营模块读取知识库/文件统计 |
| 被依赖 | fastrag-bootstrap | 启动模块做初始化 |

## 关键类说明

### 控制器层 (controller)

| 类名 | 路径前缀 | 职责 |
|------|----------|------|
| KbController | `/api/kb` | 知识库 CRUD |
| FileController | `/api/kb/{kbId}/files` | 文件上传/下载/处理/管理 |
| FolderController | `/api/kb/{kbId}/folders` | 文件夹树管理 |
| ChunkController | `/api/kb/{kbId}/chunks` | 分片查询/增删改 |
| QaPairController | `/api/kb/{kbId}/qa-pairs` | QA 问答对管理 |
| ParseStrategyController | `/api/kb/{kbId}/parse-strategies` | 解析策略管理 |
| ParseStrategyTemplateController | `/api/parse-strategy-templates` | 策略模板（内置） |
| KbCategoryController | `/api/kb-categories` | 知识库分类管理 |
| KbTagController | `/api/kb-tags` | 全局标签管理 |
| PublishManageController | `/api/kb/{kbId}/publish` | 发布/撤销/计划/重置 |

### 实体层 (entity)

| 类名 | 表名 | 说明 |
|------|------|------|
| KnowledgeBase | kb | 知识库主表 |
| KbFile | kb_file | 文件记录 |
| KbFolder | kb_folder | 文件夹 |
| KbChunk | kb_chunk | 文本分片 |
| KbParseStrategy | kb_parse_strategy | 解析策略 |
| KbQaPair | kb_qa_pair | QA 问答对 |
| KbCategory | kb_category | 知识库分类 |
| KbTag | kb_tag | 标签 |
| KbTagRelation | kb_tag_relation | 标签关联 |
| KbPublishHistory | kb_publish_history | 发布历史 |
| KbPublishPlan | kb_publish_plan | 发布计划 |
| KbListener | kb_listener | 监听器 |
| KbListenerLog | kb_listener_log | 监听器日志 |
| KbComplianceRule | kb_compliance_rule | 合规规则 |
| KbQualityRule | kb_quality_rule | 质量规则 |
| KbResetConfig | kb_reset_config | 重置配置 |
| KbReviewNode | kb_review_node | 审批节点 |
| KbReviewStrategy | kb_review_strategy | 审批策略 |
| KbReviewTemplate | kb_review_template | 审批模板 |

### 服务层 (service)

| 接口 | 实现类 | 职责 |
|------|--------|------|
| KbService | KbServiceImpl | 知识库 CRUD |
| FileService | FileServiceImpl | 文件上传/处理/删除/复制/移动 |
| FolderService | FolderServiceImpl | 文件夹树 CRUD |
| ChunkService | ChunkServiceImpl | 分片 CRUD（含向量同步） |
| QaPairService | QaPairServiceImpl | QA 对管理 |
| ParseStrategyService | ParseStrategyServiceImpl | 解析策略管理 |
| PublishManageService | PublishManageServiceImpl | 发布/撤销/计划/重置 |

### 消费者 & 基础设施 (consumer / chunking / parser / storage)

| 类名 | 职责 |
|------|------|
| IngestionConsumer | RabbitMQ 文档摄入消费者（下载->解析->分块->存储） |
| GraphBuildConsumer | RabbitMQ 图谱构建消费者（LLM 实体/关系抽取） |
| DocumentParserImpl | 多格式文档解析实现 |
| ChunkingServiceImpl | 规则分块引擎 |
| StorageServiceImpl | 分片持久化（MySQL + Milvus） |
