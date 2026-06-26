# 文档 CRUD 设计文档

## 1. 功能概述

文档是知识库中的文件资源，支持上传、解析、切片、向量化等处理流程。

## 2. 数据模型

### 2.1 数据库表 `kb_file`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(32) PK | 文件ID |
| kb_id | VARCHAR(32) FK | 所属知识库 |
| name | VARCHAR(256) | 文件名 |
| category | VARCHAR(16) | 分类(document/image/audio/video) |
| extension | VARCHAR(16) | 扩展名 |
| size | BIGINT | 文件大小(bytes) |
| object_key | VARCHAR(512) | MinIO存储路径 |
| status | VARCHAR(16) | 状态(pending/processing/completed/failed) |
| progress | INT | 处理进度(0-100) |
| stage | VARCHAR(64) | 当前处理阶段 |
| chunk_count | INT | 切片数量 |
| folder_id | VARCHAR(32) | 所属文件夹 |
| view_count | BIGINT | 浏览次数 |
| deleted_at | DATETIME | 软删除时间 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

## 3. API 接口

### 3.1 获取文件列表

```
GET /api/kb/{kbId}/files
```

### 3.2 获取已删除文件（回收站）

```
GET /api/kb/{kbId}/files/deleted
```

### 3.3 上传文件

```
POST /api/kb/{kbId}/files
Content-Type: multipart/form-data
```

**处理流程：**
1. 上传文件到 MinIO
2. 保存元数据到 MySQL (status=pending)
3. 发送消息到 RabbitMQ ingestion.queue
4. IngestionConsumer 异步处理：解析 → 切片 → 存储

### 3.3 更新文件

```
PUT /api/kb/{kbId}/files/{id}
```

### 3.4 软删除

```
DELETE /api/kb/{kbId}/files/{id}
```

### 3.5 恢复文件

```
POST /api/kb/{kbId}/files/{id}/restore
```

### 3.6 永久删除

```
DELETE /api/kb/{kbId}/files/{id}/permanent
```

### 3.7 复制文件

```
POST /api/kb/{kbId}/files/{id}/copy
```

### 3.9 获取处理状态

```
GET /api/kb/{kbId}/files/{id}/processing-status
```

### 3.10 清空回收站

```
DELETE /api/kb/{kbId}/files/recycle-bin
```

## 4. 文件处理流程

```
上传 → MinIO存储 → MySQL(pending) → RabbitMQ
                                         ↓
                              IngestionConsumer
                                         ↓
                              DocumentParser(解析)
                                         ↓
                              ChunkingService(切片)
                                         ↓
                              StorageService(MySQL+ES+Milvus)
                                         ↓
                              GraphBuildConsumer(Neo4j)
```

## 5. 当前状态

**后端：** ✅ 完整实现（含 MinIO 上传、RabbitMQ 消费者）
**前端：** ✅ 已对接 API
