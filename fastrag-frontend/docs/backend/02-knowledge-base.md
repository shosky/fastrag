# 模块二：知识库与文件加工

> 对应前端：`src/views/knowledge/*`、`src/views/knowledge-process/*`、`src/views/knowledge/detail/components/*`
> 数据契约：`src/mock/knowledge-bases.ts`、`src/mock/files.ts`、`src/mock/parse-strategy.ts`、`src/mock/qa-pairs.ts`、`src/mock/chunks.ts`、`src/types/knowledge.ts`

---

## 一、业务概述

知识库是系统的核心数据资产，承担「文档→分片→向量→可检索」全链路：

| 子域 | 能力 |
|------|------|
| 知识库管理 | KB CRUD、分类统计、维度（随 Embedding 模型）、团队/个人类型 |
| 文件管理 | 上传、解析、分片、向量化、重命名、移动、复制、软删除、回收站、彻底删除 |
| 文件夹 | 树形文件夹、增删改、按扩展名定位 |
| 解析策略 | 按扩展名配置解析方法（默认/PPT/PDF/视频/音频）、高级分块参数、冲突检测 |
| 加工流程 | 可视化加工流程编排（`/process`） |
| QA 对 | 问答对 CRUD、AI 抽取、确认 |
| 分片管理 | 分片查看/编辑 |

### 1.1 加工流水线（关键）

文件上传后按 **类别**（document/image/audio/video）走不同流水线阶段（对齐 `files.ts#PROCESS_STAGES`）：

| 类别 | 阶段 |
|------|------|
| document | 文本提取 → 分块 → Embedding → 存储 |
| image | OCR → 描述生成 → Embedding → 存储 |
| audio | ASR → 文本清理 → 分块 → Embedding → 存储 |
| video | 关键帧抽取 → 帧理解 → ASR → 合并 → 分块 → Embedding → 存储 |

每个阶段有 progress（0-100）、stage 名称、status（pending/processing/completed/failed）。

---

## 二、数据模型（Entity）

### 2.1 `kb` 知识库（对齐 `KnowledgeBase`）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | varchar(32) PK | |
| name | varchar(128) | |
| description | text | |
| category | varchar(32) | 产品文档/技术文档/客户案例/培训资料 |
| tags | varchar(512) | 逗号分隔 |
| embedding_model | varchar(64) | 模型 code |
| dimension | int | 向量维度（v4→1024, v3→768） |
| creator | varchar(32) | |
| type | enum('团队','个人') | 由 permission 派生 |
| permission | enum('private','team','public') | |
| used_size / total_size | varchar(32) | 存储用量 |
| retrieval_config | json | 完整 `RetrievalSettingConfig`（见模块3） |
| file_type_config | json | `{ documents, audio, video, images }` |
| parse_mode / split_mode | enum | auto/manual |
| created_at | datetime | |

### 2.2 `kb_file` 文件（对齐 `KnowledgeFile`）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | varchar(32) PK | |
| kb_id | varchar(32) | 索引 |
| name | varchar(255) | |
| category | enum('document','image','audio','video') | |
| extension | varchar(16) | |
| size | bigint | 字节 |
| object_key | varchar(255) | MinIO 对象 key |
| status | enum('pending','processing','completed','failed') | |
| progress | int | 0-100 |
| stage | varchar(64) | 当前阶段名 |
| duration | int | 音视频秒数 |
| pages | int | 文档页数 |
| parse_strategy_id / parse_strategy_name | varchar | |
| chunk_count | int | |
| folder_id | varchar(32) | |
| deleted_at | datetime | 软删除（回收站） |
| created_at / updated_at | datetime | |

### 2.3 `kb_folder` 文件夹（邻接表，对齐 `FolderNode`）

| 字段 | 类型 |
|------|------|
| id | varchar(32) PK |
| kb_id | varchar(32) |
| name | varchar(128) |
| parent_id | varchar(32) | 默认 'root' |
| sort | int |

### 2.4 `kb_parse_strategy` 解析策略（对齐 `ParseStrategy`）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | varchar(32) PK | |
| kb_id | varchar(32) | |
| name / description | varchar | |
| extensions | varchar(512) | 逗号分隔 |
| parse_method | enum('default','pptx','pdf','video','audio') | |
| is_default | tinyint | 每个 KB 唯一 |
| advanced | json | `ParseStrategyAdvanced`（splitMethod/chunkLength/delimiters/indexFields/enableDocSummary/enablePptWholePage/tableMode） |
| created_at / updated_at | datetime | |

### 2.5 `kb_chunk` 分片（对齐 `MockChunk`）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | varchar(64) PK | 格式 `${fileId}_chunk_${index}` |
| kb_id / file_id | varchar(32) | |
| file_name | varchar(255) | 冗余便于检索展示 |
| chunk_index | int | |
| content | mediumtext | 分片原文 |
| embedding_id | varchar(64) | Milvus 中的向量主键 |
| vector_stored | tinyint | 是否已入向量库 |

> 向量本体存 Milvus（collection = kb_id），分片原文存 MySQL + ES（供 BM25 与高亮）。

### 2.6 `kb_qa_pair` QA 对（对齐 `QaPair`）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | varchar(32) PK | |
| kb_id / file_id / file_name | varchar | |
| question / answer | text | |
| source | enum('manual','ai') | |
| status | enum('draft','confirmed') | |
| created_at | datetime | |

### 2.7 `kb_process_pipeline` 加工流程（对齐 workflow 用，知识加工页）

复用模块6的工作流定义结构，此处存储「知识加工流程」实例。

---

## 三、接口设计（API）

### 3.1 知识库

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/kb` | KB 列表 | 已认证 |
| GET | `/kb/categories` | 分类统计（含 count） | 已认证 |
| GET | `/kb/{id}` | KB 详情 | 已认证 |
| POST | `/kb` | 创建（含 retrieval_config） | `kb:create` |
| PUT | `/kb/{id}` | 更新（embeddingModel 不可改） | `kb:edit` |
| DELETE | `/kb/{id}` | 删除 | `kb:delete` |

**POST `/kb` 请求体**（对齐 `KnowledgeBaseForm`）：
```json
{
  "name": "...", "category": "...", "description": "...", "tags": [],
  "permission": "private|team|public",
  "embeddingModel": "text-embedding-v4",
  "parseMode": "auto", "splitMode": "auto",
  "fileTypeConfig": { "documents": true, "audio": true, "video": true, "images": true },
  "retrievalConfig": { /* RetrievalSettingConfig，见模块3 */ }
}
```
逻辑：根据 embeddingModel 派生 dimension（v4→1024, v3→768）；根据 permission 派生 type（public/team→团队, private→个人）。

### 3.2 文件管理

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/kb/{kbId}/files` | 文件列表（排除已删除） | KB viewer |
| GET | `/kb/{kbId}/files/deleted` | 回收站列表 | KB editor |
| POST | `/kb/{kbId}/files` | 上传（multipart） | KB editor |
| PUT | `/kb/{kbId}/files/{id}` | 更新（重命名/移动/换策略） | KB editor |
| DELETE | `/kb/{kbId}/files/{id}` | 软删除→回收站 | KB editor |
| POST | `/kb/{kbId}/files/{id}/restore` | 恢复 | KB editor |
| DELETE | `/kb/{kbId}/files/{id}/permanent` | 彻底删除 | KB editor |
| DELETE | `/kb/{kbId}/files/recycle-bin` | 清空回收站 | KB owner |
| POST | `/kb/{kbId}/files/{id}/copy` | 复制 | KB editor |
| POST | `/kb/{kbId}/files/{id}/retry` | 重新加工 | KB editor |
| PUT | `/kb/{kbId}/files/{id}/strategy` | 切换解析策略 | KB editor |
| GET | `/kb/{kbId}/files/{id}/processing-status` | 加工进度轮询 | KB viewer |
| GET | `/kb/{kbId}/chunks` | 分片列表（支持 fileId 过滤） | KB viewer |

**POST `/kb/{kbId}/files`（multipart）**：上传二进制 → 存 MinIO → 解析策略（`resolveByExtension`）→ 创建 pending 文件 → 发 MQ 触发异步加工 → 返回文件元数据。前端目前 mock 只传元数据，**后端必须实现真实二进制上传**。

### 3.3 文件夹

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/kb/{kbId}/folders` | 文件夹树 |
| POST | `/kb/{kbId}/folders` | 新建 `{ name, parentId='root' }` |
| GET | `/kb/{kbId}/folders/{id}/name` | 文件夹名 |

### 3.4 解析策略

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/kb/{kbId}/parse-strategies` | 策略列表 |
| GET | `/kb/{kbId}/parse-strategies/{id}` | 详情 |
| POST | `/kb/{kbId}/parse-strategies` | 新建 |
| PUT | `/kb/{kbId}/parse-strategies/{id}` | 更新 |
| DELETE | `/kb/{kbId}/parse-strategies/{id}` | 删除（默认不可删） |
| POST | `/kb/{kbId}/parse-strategies/{id}/set-default` | 设默认（唯一） |
| GET | `/kb/{kbId}/parse-strategies/resolve?extension=` | 按扩展名解析（非默认优先） |
| POST | `/kb/{kbId}/parse-strategies/conflicts` | 冲突检测 `{ extensions[], excludeId? }` |

### 3.5 QA 对

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/kb/{kbId}/qa-pairs` | 列表 |
| POST | `/kb/{kbId}/qa-pairs` | 新增 `{ question, answer, source, fileId? }` |
| PUT | `/kb/{kbId}/qa-pairs/{id}` | 更新 |
| DELETE | `/kb/{kbId}/qa-pairs/{id}` | 删除 |
| POST | `/kb/{kbId}/qa-pairs/{id}/confirm` | 确认（status→confirmed） |
| POST | `/kb/{kbId}/qa-extract` | AI 抽取 `{ fileIds[] }` → 候选 QA |

---

## 四、核心逻辑设计

### 4.1 异步加工流水线（核心）

文件上传后异步执行，使用 **MQ + 任务表 + 轮询/SSE**：

```java
@Service
public class FileIngestionManager {
    @Transactional
    public KbFile upload(String kbId, MultipartFile mp) {
        String objectKey = minio.upload(kbId, mp);
        ParseStrategy strategy = strategyService.resolveByExtension(kbId, ext);
        KbFile file = KbFile.pending(kbId, name, category, objectKey, strategy);
        fileMapper.insert(file);
        // 发 MQ 触发加工
        rabbitTemplate.convertAndSend("ingestion.queue", new IngestTask(file.getId()));
        return file;
    }
}

@RabbitListener(queues = "ingestion.queue")
public class IngestionConsumer {
    public void onIngest(IngestTask task) {
        KbFile f = fileMapper.selectById(task.getFileId());
        try {
            List<Stage> stages = PROCESS_STAGES.get(f.getCategory()); // 按类别取阶段
            for (Stage s : stages) {
                fileMapper.updateStage(f.getId(), s.getName(), PROCESSING);
                s.execute(f);              // OCR/ASR/分块/Embedding
                fileMapper.updateProgress(f.getId(), s.endProgress(), s.getName(), null);
            }
            fileMapper.updateStatus(f.getId(), COMPLETED, 100);
        } catch (Exception e) {
            fileMapper.updateStatus(f.getId(), FAILED, null);
        }
    }
}
```

进度上报：每个阶段完成后更新 `progress/stage`，前端通过 `GET /files/{id}/processing-status` 轮询（或 SSE 推送）。

### 4.2 分块与向量写入

- 分块：按 `ParseStrategyAdvanced`（fixed length / delimiter / 表格模式）切分，生成 `chunk_id = {fileId}_chunk_{index}`。
- Embedding：批量调 `fastrag-ai` 的 Embedding 接口，写入 Milvus（collection=kbId，字段：pk=chunk_id, vector, file_id）。
- 全文：分片原文同步写 ES（index=kb_chunks_{kbId}），供 BM25。

### 4.3 解析策略解析与冲突检测

`resolveByExtension`：先找匹配扩展名的非默认策略，无则回退默认策略（对齐前端 mock 优先级）。
`detectConflicts`：返回与给定扩展名集合有交集的其它策略（排除 excludeId），用于前端提示冲突。

### 4.4 软删除与回收站

- 删除 → 设 `deleted_at`，文件移出列表进入回收站；写操作日志（见模块5）。
- 恢复 → 清 `deleted_at`。
- 清空回收站 → 物理删除文件 + 分片 + Milvus/ES 数据 + MinIO 对象。

---

## 五、前端覆盖核对表

| 前端 mock 函数 | 后端接口 |
|----------------|----------|
| `getKnowledgeBases/getKnowledgeBase/createKnowledgeBase/updateKnowledgeBase/deleteKnowledgeBase/getCategories` | `/kb*` |
| `getFiles/getDeletedFiles/addFile/updateFile/deleteFile/restoreFile/permanentlyDeleteFile/emptyRecycleBin/copyFile/advanceProcessing` | `/kb/{kbId}/files*` |
| `getFolders/createFolder/findFolderName` | `/kb/{kbId}/folders*` |
| `getStrategies/getStrategy/createStrategy/updateStrategy/deleteStrategy/setDefault/resolveByExtension/detectConflicts` | `/kb/{kbId}/parse-strategies*` |
| `getQaPairs/addQaPair/updateQaPair/deleteQaPair/confirmQaPair/extractQaFromChunks` | `/kb/{kbId}/qa-pairs*`、`/qa-extract` |
| `mockChunks/getChunkCount` | `/kb/{kbId}/chunks`、`/chunks/count`（见模块3） |
