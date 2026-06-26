# 文档处理流程设计文档（修订版）

## 1. 概述

### 1.1 目标
实现从文档上传到可检索的完整处理流程，包括文件存储、文档解析、文本切片、向量化、图谱提取等环节。

### 1.2 现状分析

| 组件 | 状态 | 实际方法签名 |
|------|------|-------------|
| MinioService | ✅ 可用 | `upload(String objectKey, InputStream stream, String contentType)` → String |
| MilvusService | ✅ 可用 | `insert(String collection, List<String> ids, List<List<Float>> vectors, String kbId, String fileId, List<Long> chunkIndices)` |
| ESIndexService | ✅ 可用 | `indexDocument(String indexName, String id, Map<String, Object> doc)` |
| Neo4jService | ⚠️ 需扩展 | 现有：`getGraphData()`, `expandGraph()` 需新增：`createEntity()`, `createRelation()` |
| EmbeddingService | ✅ 可用 | `embed(String model, List<String> texts)` → `List<List<Float>>` |
| LlmService | ✅ 可用 | `chat(String model, String prompt)` → String |
| RabbitMQ | ✅ 可用 | 队列已定义，`MessagePublisher` 已有 `publishIngestion()`, `publishGraphBuild()` |
| 消费者 | ❌ 未实现 | 无 `@RabbitListener` |
| 文档解析 | ❌ 未实现 | 需新增 pdfbox, poi-ooxml 依赖 |

---

## 2. 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        文档处理流程                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  用户上传文件                                                    │
│       ↓                                                         │
│  ┌──────────────────┐                                           │
│  │ FileServiceImpl  │ 1. minioService.upload() 存储文件         │
│  │                  │ 2. 保存元数据到 MySQL (status=pending)     │
│  │                  │ 3. messagePublisher.publishIngestion()    │
│  └──────────────────┘                                           │
│       ↓                                                         │
│  ┌──────────────────────────────────────────────────┐           │
│  │ IngestionConsumer (@RabbitListener)              │           │
│  │                                                  │           │
│  │  依赖注入：                                       │           │
│  │  - MinioService                                  │           │
│  │  - DocumentParser                                │           │
│  │  - ChunkingService                               │           │
│  │  - StorageService                                │           │
│  │  - MessagePublisher                              │           │
│  │  - KbFileMapper                                  │           │
│  │                                                  │           │
│  │  流程：                                          │           │
│  │  1. minioService.download() 下载文件              │           │
│  │  2. documentParser.parse() 解析文档               │           │
│  │  3. chunkingService.chunk() 文本切片              │           │
│  │  4. storageService.storeChunks() 统一存储         │           │
│  │  5. messagePublisher.publishGraphBuild() 触发图谱 │           │
│  └──────────────────────────────────────────────────┘           │
│       ↓                                                         │
│  ┌──────────────────────────────────────────────────┐           │
│  │ GraphBuildConsumer (@RabbitListener)             │           │
│  │                                                  │           │
│  │  依赖注入：                                       │           │
│  │  - LlmService                                    │           │
│  │  - Neo4jService (需新增 createEntity/createRelation) │       │
│  │  - KbChunkMapper                                 │           │
│  │  - KbGraphIndexMapper                            │           │
│  │                                                  │           │
│  │  流程：                                          │           │
│  │  1. 查询文件的所有 chunks                         │           │
│  │  2. LLM 提取实体和关系                           │           │
│  │  3. neo4jService.createEntity() 创建实体节点      │           │
│  │  4. neo4jService.createRelation() 创建关系        │           │
│  └──────────────────────────────────────────────────┘           │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 3. 详细设计（修正版）

### 3.1 文件上传流程

#### 3.1.1 修改 FileServiceImpl.upload()

**变更点**：
- 注入 `MinioService` 和 `MessagePublisher`（替代直接使用 `RabbitTemplate`）
- 调用 `minioService.upload()` 上传文件
- 调用 `messagePublisher.publishIngestion()` 发送消息

```java
@Slf4j @Service @RequiredArgsConstructor
public class FileServiceImpl implements FileService {
    private final KbFileMapper fileMapper;
    private final MinioService minioService;           // 新增
    private final MessagePublisher messagePublisher;   // 新增（替代 RabbitTemplate）
    private final KbParseStrategyMapper strategyMapper; // 新增

    @Override
    public FileDto upload(String kbId, MultipartFile file) {
        // 1. 生成 objectKey
        String objectKey = kbId + "/" + IdUtil.fastSimpleUUID();

        // 2. 上传文件到 MinIO
        try {
            minioService.upload(objectKey, file.getInputStream(), file.getContentType());
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败", e);
        }

        // 3. 保存元数据
        KbFile f = new KbFile();
        f.setKbId(kbId);
        f.setName(file.getOriginalFilename());
        f.setExtension(FileUtil.extName(file.getOriginalFilename()));
        f.setSize(file.getSize());
        f.setCategory(detectCategory(file.getOriginalFilename()));
        f.setObjectKey(objectKey);
        f.setStatus("pending");
        f.setProgress(0);
        f.setChunkCount(0);
        fileMapper.insert(f);

        // 4. 获取解析策略
        String strategyId = resolveStrategy(kbId, f.getExtension());

        // 5. 发送消息（使用 MessagePublisher）
        Map<String, Object> msg = new HashMap<>();
        msg.put("fileId", f.getId());
        msg.put("kbId", kbId);
        msg.put("objectKey", objectKey);
        msg.put("strategyId", strategyId);
        messagePublisher.publishIngestion(msg);

        return toDto(f);
    }
}
```

---

### 3.2 消息消费者

#### 3.2.1 IngestionConsumer（修正依赖注入）

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class IngestionConsumer {

    private final MinioService minioService;          // 修正：添加注入
    private final DocumentParser documentParser;
    private final ChunkingService chunkingService;
    private final StorageService storageService;
    private final MessagePublisher messagePublisher;  // 修正：使用 MessagePublisher
    private final KbFileMapper fileMapper;

    @RabbitListener(queues = RabbitMQConfig.INGESTION_QUEUE)
    public void handleIngestion(Map<String, Object> message) {
        String fileId = (String) message.get("fileId");
        String kbId = (String) message.get("kbId");
        String objectKey = (String) message.get("objectKey");
        String strategyId = (String) message.get("strategyId");

        log.info("Start processing file: {}", fileId);

        try {
            // 1. 更新状态为 processing
            updateStatus(fileId, "processing", 10, "downloading");

            // 2. 从 MinIO 下载文件
            InputStream fileStream = minioService.download(objectKey);

            // 3. 解析文档
            updateStatus(fileId, "processing", 30, "parsing");
            ParseResult parseResult = documentParser.parse(fileStream, strategyId);

            // 4. 文本切片
            updateStatus(fileId, "processing", 60, "chunking");
            List<ChunkData> chunks = chunkingService.chunk(parseResult.getText(), strategyId);

            // 5. 存储切片
            updateStatus(fileId, "processing", 80, "storing");
            storageService.storeChunks(kbId, fileId, chunks);

            // 6. 更新状态为 completed
            updateStatus(fileId, "completed", 100, "done");

            // 7. 发送图谱构建消息（使用 MessagePublisher）
            messagePublisher.publishGraphBuild(Map.of(
                "kbId", kbId,
                "fileId", fileId
            ));

            log.info("File processing completed: {}", fileId);

        } catch (Exception e) {
            log.error("File processing failed: {}", fileId, e);
            updateStatus(fileId, "failed", 0, "error: " + e.getMessage());
        }
    }

    private void updateStatus(String fileId, String status, int progress, String stage) {
        KbFile f = fileMapper.selectById(fileId);
        if (f != null) {
            f.setStatus(status);
            f.setProgress(progress);
            f.setStage(stage);
            fileMapper.updateById(f);
        }
    }
}
```

---

### 3.3 文档解析器

#### 3.3.1 依赖新增（pom.xml）

```xml
<!-- fastrag-knowledge/pom.xml -->
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <version>3.0.1</version>
</dependency>
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.2.5</version>
</dependency>
```

#### 3.3.2 解析器实现

```java
public interface DocumentParser {
    ParseResult parse(InputStream fileStream, String strategyId);
}

@Service
@RequiredArgsConstructor
public class DocumentParserImpl implements DocumentParser {

    private final KbParseStrategyMapper strategyMapper;
    private final LlmService llmService;

    @Override
    public ParseResult parse(InputStream fileStream, String strategyId) {
        KbParseStrategy strategy = strategyMapper.selectById(strategyId);
        String method = strategy != null ? strategy.getParseMethod() : "default";

        return switch (method) {
            case "pdf" -> parsePdf(fileStream, strategy);
            case "pptx" -> parsePptx(fileStream, strategy);
            case "audio" -> parseAudio(fileStream, strategy);
            default -> parseDefault(fileStream, strategy);
        };
    }

    private ParseResult parsePdf(InputStream stream, KbParseStrategy strategy) {
        try (PDDocument doc = PDDocument.load(stream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            int pages = doc.getNumberOfPages();

            // 如果配置了 LLM，使用 LLM 优化文本
            if (strategy != null && strategy.getLlmModel() != null) {
                text = enhanceWithLlm(text, strategy.getLlmModel());
            }

            return ParseResult.builder().text(text).pages(pages).build();
        } catch (Exception e) {
            throw new RuntimeException("PDF parsing failed", e);
        }
    }

    private ParseResult parseDefault(InputStream stream, KbParseStrategy strategy) {
        String text = IoUtil.read(stream, StandardCharsets.UTF_8);
        return ParseResult.builder().text(text).pages(1).build();
    }
}
```

---

### 3.4 存储服务（修正类型匹配）

#### 3.4.1 修正点
- `EmbeddingService.embed()` 需要 `model` 参数
- `MilvusService.insert()` 的 vectors 类型是 `List<List<Float>>`，chunkIndices 类型是 `List<Long>`
- `ESIndexService.indexDocument()` 签名是 `(String indexName, String id, Map<String, Object> doc)`

```java
@Service
@RequiredArgsConstructor
public class StorageServiceImpl implements StorageService {

    private final KbChunkMapper chunkMapper;
    private final ESIndexService esIndexService;
    private final MilvusService milvusService;
    private final EmbeddingService embeddingService;
    private final KbFileMapper fileMapper;
    private final KnowledgeBaseMapper kbMapper;

    @Override
    public void storeChunks(String kbId, String fileId, List<ChunkData> chunks) {
        KnowledgeBase kb = kbMapper.selectById(kbId);
        int dimension = kb.getDimension() != null ? kb.getDimension() : 1024;
        String embeddingModel = kb.getEmbeddingModel(); // 修正：获取模型名

        // 确保 Milvus collection 存在
        String collection = "kb_" + kbId;
        milvusService.createCollection(collection, dimension);

        // 批量生成 Embedding（修正：传入 model 参数）
        List<String> texts = chunks.stream()
            .map(ChunkData::getContent)
            .collect(Collectors.toList());
        List<List<Float>> vectors = embeddingService.embed(embeddingModel, texts); // 修正

        // 1. 存储到 MySQL
        for (int i = 0; i < chunks.size(); i++) {
            ChunkData chunk = chunks.get(i);
            KbChunk kc = new KbChunk();
            kc.setId(fileId + "_chunk_" + i);
            kc.setKbId(kbId);
            kc.setFileId(fileId);
            kc.setFileName(file.getName()); // 修正：填充 fileName
            kc.setChunkIndex(i);
            kc.setContent(chunk.getContent());
            kc.setEmbeddingId(chunk.getId());
            kc.setVectorStored(1);
            chunkMapper.insert(kc);
        }

        // 2. 存储到 Milvus（修正：类型匹配）
        List<String> ids = new ArrayList<>();
        List<Long> indices = new ArrayList<>(); // 修正：Long 类型
        for (int i = 0; i < chunks.size(); i++) {
            ids.add(fileId + "_chunk_" + i);
            indices.add((long) i);
        }
        milvusService.insert(collection, ids, vectors, kbId, fileId, indices);

        // 3. 存储到 Elasticsearch（修正：使用正确的签名）
        String esIndex = "kb_" + kbId;
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> doc = Map.of(
                "content", chunks.get(i).getContent(),
                "kbId", kbId,
                "fileId", fileId,
                "chunkIndex", i
            );
            esIndexService.indexDocument(esIndex, fileId + "_chunk_" + i, doc);
        }

        // 4. 更新文件的 chunkCount
        KbFile file = fileMapper.selectById(fileId);
        if (file != null) {
            file.setChunkCount(chunks.size());
            fileMapper.updateById(file);
        }
    }
}
```

---

### 3.5 Neo4j 服务扩展

#### 3.5.1 新增方法

```java
// Neo4jService.java 新增方法

/**
 * 创建或合并实体节点
 */
public void createEntity(String kbId, String name, String type) {
    try (Session session = driver.session()) {
        session.writeTransaction(tx -> {
            tx.run(
                "MERGE (e:Entity {name: $name, kbId: $kbId}) " +
                "SET e.type = $type",
                Values.parameters("name", name, "kbId", kbId, "type", type)
            );
            return null;
        });
    }
}

/**
 * 创建实体间关系
 */
public void createRelation(String kbId, String source, String target, String label) {
    try (Session session = driver.session()) {
        session.writeTransaction(tx -> {
            tx.run(
                "MATCH (a:Entity {name: $source, kbId: $kbId}) " +
                "MATCH (b:Entity {name: $target, kbId: $kbId}) " +
                "MERGE (a)-[r:" + sanitize(label) + "]->(b)",
                Values.parameters("source", source, "target", target, "kbId", kbId)
            );
            return null;
        });
    }
}

private String sanitize(String label) {
    return label.replaceAll("[^a-zA-Z0-9_]", "_");
}
```

---

### 3.6 图谱构建消费者

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class GraphBuildConsumer {

    private final KbChunkMapper chunkMapper;
    private final Neo4jService neo4jService;
    private final LlmService llmService;
    private final KbGraphIndexMapper graphIndexMapper;

    @RabbitListener(queues = RabbitMQConfig.GRAPH_BUILD_QUEUE)
    public void handleGraphBuild(Map<String, Object> message) {
        String kbId = (String) message.get("kbId");
        String fileId = (String) message.get("fileId");

        log.info("Start building graph for file: {}", fileId);

        try {
            updateGraphStatus(kbId, "building", 0);

            List<KbChunk> chunks = chunkMapper.selectList(
                new LambdaQueryWrapper<KbChunk>()
                    .eq(KbChunk::getKbId, kbId)
                    .eq(KbChunk::getFileId, fileId)
            );

            int total = chunks.size();
            int processed = 0;
            int entityCount = 0;
            int relationCount = 0;

            for (KbChunk chunk : chunks) {
                EntityRelationResult result = extractEntitiesAndRelations(chunk.getContent());

                for (Entity entity : result.getEntities()) {
                    neo4jService.createEntity(kbId, entity.getName(), entity.getType());
                    entityCount++;
                }

                for (Relation rel : result.getRelations()) {
                    neo4jService.createRelation(kbId, rel.getSource(), rel.getTarget(), rel.getLabel());
                    relationCount++;
                }

                processed++;
                updateGraphProgress(kbId, processed, total, entityCount, relationCount);
            }

            updateGraphStatus(kbId, "completed", 100);
            log.info("Graph build completed for file: {}, entities: {}, relations: {}",
                fileId, entityCount, relationCount);

        } catch (Exception e) {
            log.error("Graph build failed for file: {}", fileId, e);
            updateGraphStatus(kbId, "failed", 0);
        }
    }

    private EntityRelationResult extractEntitiesAndRelations(String text) {
        String prompt = """
            请从以下文本中提取实体和关系，返回JSON格式：
            {"entities":[{"name":"实体名","type":"实体类型"}],"relations":[{"source":"源实体","target":"目标实体","label":"关系类型"}]}
            只返回JSON，不要其他内容。

            文本：
            """ + text.substring(0, Math.min(text.length(), 2000));

        String response = llmService.chat(null, prompt);
        try {
            return JSONUtil.toBean(response, EntityRelationResult.class);
        } catch (Exception e) {
            log.warn("Failed to parse LLM response: {}", response);
            return new EntityRelationResult();
        }
    }
}
```

---

## 4. 文件结构

```
fastrag-backend/
├── fastrag-infra/src/main/java/com/fastrag/infra/
│   ├── neo4j/Neo4jService.java              # 需新增 createEntity/createRelation
│   └── rabbitmq/MessagePublisher.java        # 已有，使用它替代直接 RabbitTemplate
│
├── fastrag-modules/fastrag-knowledge/
│   ├── pom.xml                               # 需新增 pdfbox, poi-ooxml 依赖
│   └── src/main/java/com/fastrag/module/knowledge/
│       ├── service/impl/FileServiceImpl.java # 需修改：注入 MinioService, MessagePublisher
│       ├── consumer/
│       │   ├── IngestionConsumer.java        # 新建
│       │   └── GraphBuildConsumer.java       # 新建
│       ├── parser/
│       │   ├── DocumentParser.java           # 新建接口
│       │   └── DocumentParserImpl.java       # 新建实现
│       ├── chunking/
│       │   ├── ChunkingService.java          # 新建接口
│       │   └── ChunkingServiceImpl.java      # 新建实现
│       └── storage/
│           ├── StorageService.java           # 新建接口
│           └── StorageServiceImpl.java       # 新建实现
```

---

## 5. 修正清单

| # | 问题 | 修正 |
|---|------|------|
| 1 | MilvusService.insert vectors 类型不匹配 | `List<float[]>` → `List<List<Float>>` |
| 2 | MilvusService.insert chunkIndices 类型不匹配 | `List<Integer>` → `List<Long>` |
| 3 | ESIndexService.indexDocument 签名不匹配 | 使用 `(indexName, id, Map)` 签名 |
| 4 | Neo4jService 缺少 createEntity/createRelation | 新增这两个方法 |
| 5 | EmbeddingService.embed 缺少 model 参数 | 从 KnowledgeBase 获取 embeddingModel |
| 6 | IngestionConsumer 未注入 MinioService | 添加依赖注入 |
| 7 | IngestionConsumer 未注入 MessagePublisher | 使用 MessagePublisher 替代 RabbitTemplate |
| 8 | FileServiceImpl 使用 RabbitTemplate | 改用 MessagePublisher |
| 9 | KbChunk 未填充 fileName | 添加 fileName 填充 |
| 10 | pdfbox/poi-ooxml 依赖缺失 | 添加到 fastrag-knowledge/pom.xml |

---

## 6. 配置项（application.yml 新增）

```yaml
document:
  processing:
    default-chunk-length: 500
    default-overlap: 50
    max-chunk-length: 2000
    concurrent-consumers: 3
```

---

## 7. 实施计划

### Phase 1: 基础流程（1-2天）
- [ ] 修改 FileServiceImpl（注入 MinioService, MessagePublisher）
- [ ] 实现 IngestionConsumer
- [ ] 实现 DocumentParser（支持 txt/md/csv）
- [ ] 实现 ChunkingService（规则切片）
- [ ] 实现 StorageService（MySQL + ES + Milvus）

### Phase 2: 文档解析（2-3天）
- [ ] 添加 pdfbox, poi-ooxml 依赖
- [ ] 实现 PDF 解析
- [ ] 实现 Word/Excel/PPT 解析

### Phase 3: 图谱构建（2-3天）
- [ ] Neo4jService 新增 createEntity/createRelation
- [ ] 实现 GraphBuildConsumer
- [ ] 实现实体/关系提取

### Phase 4: 优化完善（1-2天）
- [ ] 实现处理进度实时更新
- [ ] 实现错误重试机制
- [ ] 添加监控和日志
