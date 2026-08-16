package com.fastrag.module.knowledge.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fastrag.common.exception.BusinessException;
import com.fastrag.infra.milvus.MilvusService;
import com.fastrag.infra.minio.MinioService;
import com.fastrag.infra.rabbitmq.MessagePublisher;
import com.fastrag.module.graph.service.GraphService;
import com.fastrag.module.knowledge.chunking.ChunkData;
import com.fastrag.module.knowledge.chunking.ChunkingService;
import com.fastrag.module.knowledge.config.StrategyConfigResolver;
import com.fastrag.module.knowledge.entity.KbChunk;
import com.fastrag.module.knowledge.entity.KbFile;
import com.fastrag.module.knowledge.entity.KbParseStrategy;
import com.fastrag.module.knowledge.entity.KbQaPair;
import com.fastrag.module.knowledge.entity.KnowledgeBase;
import com.fastrag.module.knowledge.mapper.KbChunkMapper;
import com.fastrag.module.knowledge.mapper.KbFileMapper;
import com.fastrag.module.knowledge.mapper.KbParseStrategyMapper;
import com.fastrag.module.knowledge.mapper.KbQaPairMapper;
import com.fastrag.module.knowledge.mapper.KnowledgeBaseMapper;
import com.fastrag.module.knowledge.model.FileDto;
import com.fastrag.module.knowledge.model.FileProcessRequest;
import com.fastrag.module.knowledge.model.ParseStrategyDto;
import com.fastrag.module.knowledge.model.ParseStrategyRequest;
import com.fastrag.module.knowledge.parser.DocumentParser;
import com.fastrag.module.knowledge.parser.ParseResult;
import com.fastrag.module.knowledge.service.FileService;
import com.fastrag.module.knowledge.service.ParseStrategyService;
import com.fastrag.module.publish.service.LogService;
import com.fastrag.security.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识库文件管理服务实现类。
 *
 * <p>实现 {@link FileService} 接口，负责知识库文件的完整生命周期管理。
 * 文件存储在MinIO中，元数据存储在 kb_file 表中，处理流程通过RabbitMQ消息
 * 异步触发解析、分块和向量化。</p>
 *
 * <p>核心功能：</p>
 * <ul>
 *   <li>upload - 上传文件到MinIO并保存元数据，自动识别文件分类（document/image/audio/video）</li>
 *   <li>process - 触发文件处理：解析策略匹配 → 图谱开关继承（KB级）→ 发送RabbitMQ摄入消息</li>
 *   <li>list/listDeleted - 文件列表和回收站列表（软删除过滤）</li>
 *   <li>delete/restore/permanentDelete/emptyRecycleBin - 软删除、恢复、彻底删除、清空回收站；
 *       彻底删除时清理chunks、Milvus向量、知识图谱、MinIO文件、QA对</li>
 *   <li>copy/moveToKb - 文件复制和跨知识库移动（移动后触发重新处理）</li>
 *   <li>previewChunks - 分块预览：下载文件→解析→分块→返回前20个分块（内容截断300字符）</li>
 *   <li>resolveStrategy - 按扩展名匹配解析策略（默认策略优先）</li>
 *   <li>resolveDefaultGraphBuild - 解析图谱构建默认开关：KB级graphAutoBuild为1时启用</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {
    private final KbFileMapper fileMapper;
    private final KbChunkMapper chunkMapper;
    private final KbQaPairMapper qaPairMapper;
    private final MinioService minioService;
    private final MessagePublisher messagePublisher;
    private final KbParseStrategyMapper strategyMapper;
    private final JdbcTemplate jdbcTemplate;
    private final DocumentParser documentParser;
    private final ChunkingService chunkingService;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final MilvusService milvusService;
    private final GraphService graphService;
    private final ParseStrategyService parseStrategyService;
    private final StrategyConfigResolver configResolver;

    @Override
    public List<FileDto> list(String kbId) {
        // 过滤已删除的文件
        return fileMapper.selectList(new LambdaQueryWrapper<KbFile>()
                .eq(KbFile::getKbId, kbId)
                .isNull(KbFile::getDeletedAt)
                .orderByDesc(KbFile::getCreatedAt))
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public List<FileDto> listDeleted(String kbId) {
        // 查询已删除的文件需要绕过逻辑删除
        // 使用自定义 SQL 或直接查询
        return fileMapper.selectList(new LambdaQueryWrapper<KbFile>()
                .eq(KbFile::getKbId, kbId)
                .isNotNull(KbFile::getDeletedAt)
                .orderByDesc(KbFile::getDeletedAt))
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public FileDto upload(String kbId, MultipartFile file, String folderId) {
        String objectKey = kbId + "/" + IdUtil.fastSimpleUUID();

        // 上传文件到 MinIO
        try {
            minioService.upload(objectKey, file.getInputStream(), file.getContentType());
        } catch (IOException e) {
            throw new RuntimeException("文件上传到存储失败", e);
        }

        // 保存元数据
        String extension = FileUtil.extName(file.getOriginalFilename());
        KbFile f = new KbFile();
        f.setKbId(kbId);
        f.setName(file.getOriginalFilename());
        f.setExtension(extension);
        f.setSize(file.getSize());
        f.setCategory(detectCategory(extension));
        f.setObjectKey(objectKey);
        f.setStatus("pending");
        f.setProgress(0);
        f.setChunkCount(0);
        if (folderId != null && !folderId.isEmpty()) {
            f.setFolderId(folderId);
        }
        fileMapper.insert(f);

        log.info("File uploaded: {}", f.getId());
        return toDto(f);
    }

    @Override
    public void process(String kbId, String fileId, FileProcessRequest req) {
        KbFile f = fileMapper.selectOne(new LambdaQueryWrapper<KbFile>()
                .eq(KbFile::getId, fileId)
                .eq(KbFile::getKbId, kbId));
        if (f == null) throw new RuntimeException("File not found");

        String processingMode = req.getProcessingMode() != null ? req.getProcessingMode() : "chunk";

        // 获取解析策略：上传向导指定优先，否则按扩展名自动匹配
        String strategyId = resolveRequestedStrategy(kbId, f.getId(), f.getExtension(), req.getParseStrategyId());

        // 持久化处理模式
        f.setProcessingMode(processingMode);
        // 持久化解析策略 ID（后续图谱构建等需要读取策略中的 LLM 模型配置）
        f.setParseStrategyId(strategyId);
        // 持久化图谱开关：
        // - 文件从未被用户显式修改过（createdAt == updatedAt）：0 是 DB 默认值，从 KB 级 + 策略级继承
        // - 文件已被用户显式修改过（toggle 开关等）：尊重用户的选择
        boolean userExplicitlySet = f.getUpdatedAt() != null
                && f.getCreatedAt() != null
                && f.getUpdatedAt().isAfter(f.getCreatedAt());
        if (!userExplicitlySet && (f.getEnableGraphBuild() == null || f.getEnableGraphBuild() == 0)) {
            Integer resolvedGraphBuild = resolveDefaultGraphBuild(kbId, strategyId);
            log.info("[GraphSwitch] Setting enableGraphBuild for file {}: current={}, resolved={}, userExplicitlySet={}",
                    fileId, f.getEnableGraphBuild(), resolvedGraphBuild, userExplicitlySet);
            f.setEnableGraphBuild(resolvedGraphBuild);
        } else {
            log.info("[GraphSwitch] File {} enableGraphBuild kept as-is: value={}, userExplicitlySet={}",
                    fileId, f.getEnableGraphBuild(), userExplicitlySet);
        }
        // 持久化上传向导的处理配置（引擎/语言/编码/优先级/重试/媒体配置），重试与重新分片时复用
        f.setProcessingConfig(JSONUtil.toJsonStr(req));
        fileMapper.updateById(f);

        // 发送消息到 RabbitMQ 触发处理
        Map<String, Object> msg = new HashMap<>();
        msg.put("fileId", f.getId());
        msg.put("kbId", kbId);
        msg.put("objectKey", f.getObjectKey());
        msg.put("strategyId", strategyId);
        msg.put("operator", SecurityUtil.getCurrentUser() != null
                ? SecurityUtil.getCurrentUser().getUsername() : "system");
        msg.put("processingMode", processingMode);
        msg.put("enableGraphBuild", f.getEnableGraphBuild());
        msg.put("processingConfig", JSONUtil.toJsonStr(req));
        messagePublisher.publishIngestion(msg);

        log.info("File processing triggered: {}, mode: {}, strategyId: {}, enableGraphBuild: {}",
                f.getId(), processingMode, strategyId, f.getEnableGraphBuild());
    }

    /**
     * 解析策略选择：上传向导指定 parseStrategyId 且属于该知识库时优先使用，
     * 否则按扩展名自动匹配（resolveStrategy 覆盖链）。
     * 文件级专属自定义策略（file_id = 该文件）同样放行——自定义面板保存后重切走此路径。
     */
    private String resolveRequestedStrategy(String kbId, String fileId, String extension, String requestedId) {
        if (requestedId != null && !requestedId.isBlank()) {
            KbParseStrategy s = strategyMapper.selectOne(new LambdaQueryWrapper<KbParseStrategy>()
                    .eq(KbParseStrategy::getKbId, kbId)
                    .eq(KbParseStrategy::getId, requestedId)
                    .and(w -> w.isNull(KbParseStrategy::getFileId)
                            .or().eq(KbParseStrategy::getFileId, fileId)));
            if (s != null) {
                return s.getId();
            }
            log.warn("Requested parseStrategyId {} not found in kb {}, falling back to auto match", requestedId, kbId);
        }
        return resolveStrategy(kbId, extension);
    }

    /**
     * 从持久化的 processing_config 重建处理请求（retry / re-chunk 复用原引擎配置），
     * 无历史配置时构造最小默认请求。
     *
     * <p>策略真源修正：kb_file.parse_strategy_id 是「当前绑定」的唯一真源，
     * 快照中内嵌的 parseStrategyId 是上传时的历史值，重建时一律以文件字段覆盖，
     * 防止换绑后重切仍使用旧策略（ADR-0001）。
     */
    private FileProcessRequest rebuildProcessRequest(KbFile f) {
        FileProcessRequest req;
        if (StrUtil.isNotBlank(f.getProcessingConfig())) {
            try {
                req = JSONUtil.toBean(f.getProcessingConfig(), FileProcessRequest.class);
            } catch (Exception e) {
                log.warn("Failed to parse processing_config for file {}, using defaults: {}", f.getId(), e.getMessage());
                req = new FileProcessRequest();
                req.setProcessingMode(f.getProcessingMode() != null ? f.getProcessingMode() : "chunk");
            }
        } else {
            req = new FileProcessRequest();
            req.setProcessingMode(f.getProcessingMode() != null ? f.getProcessingMode() : "chunk");
        }
        // 当前绑定覆盖快照值；process() 会把最终 resolved 策略重新写回 processing_config，快照随之更新
        req.setParseStrategyId(f.getParseStrategyId());
        return req;
    }

    /**
     * 解析该文件的默认图谱开关值：KB 级 graphAutoBuild
     * 为 1 时返回 1，否则返回 0
     */
    private Integer resolveDefaultGraphBuild(String kbId, String strategyId) {
        log.info("[GraphSwitch] Resolving default graph build for kbId={}", kbId);

        KnowledgeBase kb = knowledgeBaseMapper.selectById(kbId);
        if (kb != null && Integer.valueOf(1).equals(kb.getGraphAutoBuild())) {
            log.info("[GraphSwitch] KB graphAutoBuild=1, graph build enabled");
            return 1;
        }
        log.info("[GraphSwitch] KB graphAutoBuild is 0 or null, graph build disabled");
        return 0;
    }

    @Override
    public FileDto retryFile(String kbId, String fileId) {
        KbFile f = fileMapper.selectOne(new LambdaQueryWrapper<KbFile>()
                .eq(KbFile::getId, fileId)
                .eq(KbFile::getKbId, kbId));
        if (f == null) throw new RuntimeException("File not found");

        log.info("Retrying file: {}, kbId: {}, currentStatus: {}", fileId, kbId, f.getStatus());

        // 1. 清理旧数据
        cleanupOldData(kbId, fileId, f.getProcessingMode());

        // 2. 重置文件状态
        f.setStatus("pending");
        f.setProgress(0);
        f.setStage("");
        f.setChunkCount(0);
        // 如果 processingMode 尚未设置（首次重试），从当前策略解析
        if (f.getProcessingMode() == null || f.getProcessingMode().isBlank()) {
            String strategyId = resolveStrategy(kbId, f.getExtension());
            f.setProcessingMode("chunk");
            if (f.getEnableGraphBuild() == null) {
                f.setEnableGraphBuild(resolveDefaultGraphBuild(kbId, strategyId));
            }
        }
        fileMapper.updateById(f);

        // 3. 重新触发处理（走标准 process 流程，会发 RabbitMQ 消息；复用原引擎配置）
        process(kbId, fileId, rebuildProcessRequest(f));

        log.info("File retry initiated: {}, mode: {}", fileId, f.getProcessingMode());
        return toDto(f);
    }

    /**
     * 重新分片：删除旧分片/向量/图谱后，按策略绑定重跑「解析→分片→向量化」。
     * 保留 MinIO 原文件与 QA 对（QA 对为用户手工数据）。
     *
     * <p>策略三态（ADR-0001：绑定变更与重切原子完成，不允许只改绑定）：
     * <ul>
     *   <li>strategyId 参数为 null（未传）→ 沿用当前绑定</li>
     *   <li>strategyId 为非空 id → 换绑（校验归属本 KB）+ 重切</li>
     *   <li>strategyId 为空串 → 清除覆盖、恢复扩展名自动匹配 + 重切</li>
     * </ul>
     *
     * @param strategyId 目标策略，三态语义见上
     */
    @Override
    public FileDto reChunkFile(String kbId, String fileId, String strategyId) {
        KbFile f = fileMapper.selectOne(new LambdaQueryWrapper<KbFile>()
                .eq(KbFile::getId, fileId)
                .eq(KbFile::getKbId, kbId));
        if (f == null) throw BusinessException.notFound("文件不存在");

        // 守卫：回收站文件 / 处理中 / QA 模式
        if (f.getDeletedAt() != null) {
            throw BusinessException.badRequest("文件已在回收站中，请先恢复后再重新分片");
        }
        if ("processing".equals(f.getStatus())) {
            throw BusinessException.badRequest("文件正在处理中，请等待处理完成后再重新分片");
        }
        if ("qa".equals(f.getProcessingMode())) {
            throw BusinessException.badRequest("QA 模式文件不支持重新分片（QA 对不受分片策略影响）");
        }

        log.info("Re-chunking file: {}, kbId: {}, currentStatus: {}, strategyOverride: {}",
                fileId, kbId, f.getStatus(), strategyId == null ? "(keep)" : ("\"" + strategyId + "\""));

        // 换绑（如有）：校验归属后更新当前绑定，rebuildProcessRequest 以文件字段为准重建请求。
        // 放行知识库级策略与本文件的专属自定义策略（file_id = 该文件）
        if (strategyId != null) {
            if (strategyId.isBlank()) {
                f.setParseStrategyId(null);   // 清除覆盖 → 扩展名自动匹配
            } else {
                KbParseStrategy s = strategyMapper.selectOne(new LambdaQueryWrapper<KbParseStrategy>()
                        .eq(KbParseStrategy::getKbId, kbId)
                        .eq(KbParseStrategy::getId, strategyId)
                        .and(w -> w.isNull(KbParseStrategy::getFileId)
                                .or().eq(KbParseStrategy::getFileId, fileId)));
                if (s == null) {
                    throw BusinessException.badRequest("解析策略不存在或不属于该知识库");
                }
                f.setParseStrategyId(s.getId());
            }
        }

        // 1. 清理旧分片与向量
        int chunkDeleted = chunkMapper.delete(new LambdaQueryWrapper<KbChunk>()
                .eq(KbChunk::getKbId, kbId)
                .eq(KbChunk::getFileId, fileId));
        log.info("[ReChunk] Deleted {} old chunks for file: {}", chunkDeleted, fileId);

        String collection = "kb_" + kbId.replace("-", "_");
        try {
            milvusService.deleteByFileId(collection, fileId);
            log.info("[ReChunk] Milvus vectors cleaned for file: {}", fileId);
        } catch (Exception e) {
            log.warn("[ReChunk] Milvus cleanup failed for file {}: {}", fileId, e.getMessage());
        }
        try {
            graphService.deleteFileGraph(kbId, fileId);
            log.info("[ReChunk] Graph data cleaned for file: {}", fileId);
        } catch (Exception e) {
            log.warn("[ReChunk] Graph cleanup failed for file {}: {}", fileId, e.getMessage());
        }

        // 2. 重置文件状态，走标准 process 流程（重新解析 + 分片 + 向量化，触发 RabbitMQ；复用原引擎配置）
        f.setStatus("pending");
        f.setProgress(0);
        f.setStage("");
        f.setChunkCount(0);
        fileMapper.updateById(f);
        process(kbId, fileId, rebuildProcessRequest(f));

        log.info("File re-chunk initiated: {}, mode: {}", fileId, f.getProcessingMode());
        return toDto(f);
    }

    /**
     * 保存文件级专属自定义策略并重新分片（原子完成，ADR-0001）。
     * 保存为绑定 file_id 的隐藏策略（不出现在策略管理列表、不参与扩展名自动匹配），
     * 随后按该策略重切（reChunkFile 校验放行 file_id = 该文件的策略）。
     */
    @Override
    public FileDto saveFileStrategy(String kbId, String fileId, ParseStrategyRequest req) {
        ParseStrategyDto saved = parseStrategyService.saveFileStrategy(kbId, fileId, req);
        log.info("[FileStrategy] Saved file-level strategy {} for file {} in kb {}", saved.getId(), fileId, kbId);
        return reChunkFile(kbId, fileId, saved.getId());
    }

    /**
     * 清理文件数据：MySQL chunks、Milvus 向量、知识图谱、QA 对
     */
    private void cleanupFileData(String kbId, String fileId) {
        // 清理 MySQL kb_chunk 记录
        int chunkDeleted = chunkMapper.delete(new LambdaQueryWrapper<KbChunk>()
                .eq(KbChunk::getKbId, kbId)
                .eq(KbChunk::getFileId, fileId));
        log.info("[Delete] Deleted {} chunks for file: {}", chunkDeleted, fileId);

        // 清理 Milvus 向量（按 collection + fileId）。
        // collection 名与 StorageServiceImpl/storeChunks、reChunkFile 保持一致（连字符转下划线），
        // 否则 kbId 含连字符时会删错 collection，向量永久泄漏
        String collection = "kb_" + kbId.replace("-", "_");
        try {
            milvusService.deleteByFileId(collection, fileId);
        } catch (Exception e) {
            log.warn("[Delete] Milvus cleanup failed for file {}: {}", fileId, e.getMessage());
        }

        // 清理知识图谱数据（含孤儿实体回收，Yuxi 方式；同时同步 KbGraphIndex 缓存计数）
        try {
            graphService.deleteFileGraph(kbId, fileId);
            log.info("[Delete] Graph data cleaned for file: {}", fileId);
        } catch (Exception e) {
            log.warn("[Delete] Graph cleanup failed for file {}: {}", fileId, e.getMessage());
        }

        // 清理 MinIO 存储的文件
        try {
            KbFile f = fileMapper.selectById(fileId);
            if (f != null && f.getObjectKey() != null) {
                minioService.delete(f.getObjectKey());              // 原始上传文件
            }
            minioService.deleteByPrefix(kbId + "/" + fileId);      // 图片/分段等衍生文件
        } catch (Exception e) {
            log.warn("[Delete] MinIO cleanup failed for file {}: {}", fileId, e.getMessage());
        }

        // 清理 QA 对
        try {
            int qaDeleted = qaPairMapper.delete(new LambdaQueryWrapper<KbQaPair>()
                    .eq(KbQaPair::getKbId, kbId)
                    .eq(KbQaPair::getFileId, fileId));
            if (qaDeleted > 0) {
                log.info("[Delete] Deleted {} QA pairs for file: {}", qaDeleted, fileId);
            }
        } catch (Exception e) {
            log.warn("[Delete] QA pair cleanup failed for file {}: {}", fileId, e.getMessage());
        }

        // 清理文件级专属自定义策略（随文件删除级联）
        try {
            parseStrategyService.deleteFileStrategy(kbId, fileId);
            log.info("[Delete] File-level parse strategy cleaned for file: {}", fileId);
        } catch (Exception e) {
            log.warn("[Delete] File-level parse strategy cleanup failed for file {}: {}", fileId, e.getMessage());
        }
    }

    /**
     * 清理文件旧的处理数据：MySQL chunks、Milvus 向量、QA 对（用于重试场景）
     */
    private void cleanupOldData(String kbId, String fileId, String processingMode) {
        cleanupFileData(kbId, fileId);

        // 如果是 QA 模式，清理旧 QA 对
        if ("qa".equals(processingMode)) {
            int qaDeleted = qaPairMapper.delete(new LambdaQueryWrapper<KbQaPair>()
                    .eq(KbQaPair::getKbId, kbId)
                    .eq(KbQaPair::getFileId, fileId));
            log.info("[Retry] Deleted {} old QA pairs for file: {}", qaDeleted, fileId);
        }
    }

    @Override
    public FileDto update(String kbId, String fileId, Map<String, Object> patch) {
        var f = fileMapper.selectOne(new LambdaQueryWrapper<KbFile>()
                .eq(KbFile::getKbId, kbId).eq(KbFile::getId, fileId).isNull(KbFile::getDeletedAt));
        if (f == null) throw BusinessException.notFound("文件不存在");
        // ADR-0001：策略绑定变更必须与重新分片原子完成，禁止元数据接口改绑（防止绑定与分片结果漂移）
        if (patch.containsKey("parseStrategyId")) {
            Object v = patch.get("parseStrategyId");
            String requested = v == null ? null : String.valueOf(v);
            if (!Objects.equals(requested, f.getParseStrategyId())) {
                throw BusinessException.badRequest("解析策略变更须与重新分片一起完成，请调用 re-chunk 接口");
            }
        }
        if (patch.containsKey("name")) f.setName((String) patch.get("name"));
        if (patch.containsKey("folderId")) f.setFolderId((String) patch.get("folderId"));
        if (patch.containsKey("enableGraphBuild")) {
            Object val = patch.get("enableGraphBuild");
            int intVal = val instanceof Number ? ((Number) val).intValue() : (Boolean.TRUE.equals(val) ? 1 : 0);
            f.setEnableGraphBuild(intVal);
        }
        fileMapper.updateById(f);
        return toDto(f);
    }

    @Override
    public void delete(String kbId, String fileId) {
        // 软删除：使用原生 SQL 确保 deleted_at 被更新
        // 清理知识图谱数据（保留 chunks/Milvus，以便 restore 后可重建图谱）
        try {
            graphService.deleteFileGraph(kbId, fileId);
            log.info("[Delete] Graph data cleaned for soft-deleted file: {}", fileId);
        } catch (Exception e) {
            log.warn("[Delete] Graph cleanup failed for file {}: {}", fileId, e.getMessage());
        }
        jdbcTemplate.update("UPDATE kb_file SET deleted_at = NOW() WHERE id = ? AND kb_id = ?", fileId, kbId);
    }

    @Override
    public void restore(String kbId, String fileId) {
        // 恢复：清除 deletedAt
        fileMapper.update(null, new LambdaUpdateWrapper<KbFile>()
                .eq(KbFile::getId, fileId)
                .eq(KbFile::getKbId, kbId)
                .set(KbFile::getDeletedAt, null));
    }

    @Override
    public void permanentDelete(String kbId, String fileId) {
        // 先清理关联数据，再删除文件记录
        cleanupFileData(kbId, fileId);
        fileMapper.deleteById(fileId);
    }

    @Override
    public void emptyRecycleBin(String kbId) {
        fileMapper.selectList(new LambdaQueryWrapper<KbFile>()
                .eq(KbFile::getKbId, kbId).isNotNull(KbFile::getDeletedAt))
                .forEach(f -> {
                    cleanupFileData(kbId, f.getId());
                    fileMapper.deleteById(f.getId());
                });
    }

    @Override
    public FileDto copy(String kbId, String fileId) {
        var s = fileMapper.selectById(fileId);
        if (s == null) throw new RuntimeException("File not found");
        var c = new KbFile();
        c.setKbId(kbId);
        c.setName(s.getName() + " (copy)");
        c.setCategory(s.getCategory());
        c.setExtension(s.getExtension());
        c.setSize(s.getSize());
        c.setObjectKey(s.getObjectKey());
        c.setStatus("pending");
        c.setProgress(0);
        c.setChunkCount(0);
        fileMapper.insert(c);
        return toDto(c);
    }

    @Override
    @Transactional
    public FileDto moveToKb(String sourceKbId, String fileId, String targetKbId, String targetFolderId) {
        // 查找文件
        var f = fileMapper.selectOne(new LambdaQueryWrapper<KbFile>()
                .eq(KbFile::getId, fileId)
                .eq(KbFile::getKbId, sourceKbId)
                .isNull(KbFile::getDeletedAt));
        if (f == null) throw new RuntimeException("File not found in source KB");

        // 校验目标知识库存在
        var targetKb = knowledgeBaseMapper.selectById(targetKbId);
        if (targetKb == null) throw new RuntimeException("目标知识库不存在");

        // 校验目标 KB 下没有同名文件
        long dupCount = fileMapper.selectCount(new LambdaQueryWrapper<KbFile>()
                .eq(KbFile::getKbId, targetKbId)
                .eq(KbFile::getName, f.getName())
                .isNull(KbFile::getDeletedAt));
        if (dupCount > 0) {
            throw new RuntimeException("目标知识库下已存在同名文件: " + f.getName());
        }

        // 1. 清理旧 KB 的所有关联数据（chunks、Milvus 向量、图谱）
        cleanupFileData(sourceKbId, fileId);

        // 2. 清理 QA 对
        qaPairMapper.delete(new LambdaQueryWrapper<KbQaPair>()
                .eq(KbQaPair::getKbId, sourceKbId)
                .eq(KbQaPair::getFileId, fileId));

        // 3. 复制 MinIO 文件内容到目标 KB 路径
        String sourceKey = sourceKbId + "/" + fileId;
        String destKey = targetKbId + "/" + fileId;
        if (!sourceKey.equals(destKey)) {
            minioService.copy(sourceKey, destKey);
            minioService.delete(sourceKey);
        }

        // 4. 更新文件元数据到目标 KB
        f.setKbId(targetKbId);
        f.setFolderId(targetFolderId != null && !targetFolderId.isBlank()
                && !"root".equals(targetFolderId) ? targetFolderId : null);
        f.setStatus("pending");
        f.setProgress(0);
        f.setStage(null);
        f.setChunkCount(0);
        f.setProcessingMode(null);
        fileMapper.updateById(f);

        // 5. 触发重新处理（异步）
        String strategyId = resolveStrategy(targetKbId, f.getExtension());
        Map<String, Object> msg = new HashMap<>();
        msg.put("fileId", f.getId());
        msg.put("kbId", targetKbId);
        msg.put("objectKey", f.getObjectKey());
        msg.put("strategyId", strategyId);
        msg.put("operator", SecurityUtil.getCurrentUser() != null
                ? SecurityUtil.getCurrentUser().getUsername() : "system");
        msg.put("processingMode", "chunk");
        try {
            messagePublisher.publishIngestion(msg);
            log.info("[MoveToKb] Sent process message for file: {} to KB: {}", fileId, targetKbId);
        } catch (Exception e) {
            log.warn("[MoveToKb] Failed to trigger re-processing for file {}: {}", fileId, e.getMessage());
        }

        log.info("[MoveToKb] Moved file {} from KB {} to KB {}, folder={}", fileId, sourceKbId, targetKbId, targetFolderId);
        return toDto(f);
    }

    @Override
    public Map<String, Object> getProcessingStatus(String kbId, String fileId) {
        var f = fileMapper.selectById(fileId);
        if (f == null) throw new RuntimeException("File not found");
        var r = new HashMap<String, Object>();
        r.put("fileId", f.getId());
        r.put("status", f.getStatus());
        r.put("progress", f.getProgress() != null ? f.getProgress() : 0);
        r.put("stage", f.getStage() != null ? f.getStage() : "");
        return r;
    }

    @Override
    public Map<String, Object> previewChunks(String kbId, String fileId, String strategyId,
                                             Map<String, Object> customConfig) {
        KbFile f = fileMapper.selectOne(new LambdaQueryWrapper<KbFile>()
                .eq(KbFile::getId, fileId)
                .eq(KbFile::getKbId, kbId));
        if (f == null) throw new RuntimeException("File not found");

        // 自定义临时策略（未落库预览，来自对话框「自定义」面板）：parseMethod + advanced
        KbParseStrategy tmpStrategy = null;
        if (customConfig != null && !customConfig.isEmpty()) {
            tmpStrategy = new KbParseStrategy();
            Object pm = customConfig.get("parseMethod");
            tmpStrategy.setParseMethod(pm instanceof String s && !s.isBlank() ? s : null);
            Object adv = customConfig.get("advanced");
            if (adv instanceof Map<?, ?> m) {
                tmpStrategy.setAdvanced(JSONUtil.toJsonStr(m));
            }
        }
        // 未指定策略时按扩展名自动匹配（自定义配置优先于策略 id）
        if (tmpStrategy == null && (strategyId == null || strategyId.isBlank())) {
            strategyId = resolveStrategy(kbId, f.getExtension());
        }

        try {
            // 下载文件
            InputStream fileStream = minioService.download(f.getObjectKey());
            byte[] fileBytes = fileStream.readAllBytes();
            fileStream.close();

            // 解析文档：自定义配置用临时策略对象（含自定义解析方式），跳过 LLM 增强；
            // 否则按扩展名自动解析（预览时传 null 避免 enhanceWithLlm 被触发）
            ParseResult parseResult = tmpStrategy != null
                    ? documentParser.parse(new ByteArrayInputStream(fileBytes), f.getExtension(), tmpStrategy, null)
                    : documentParser.parse(new ByteArrayInputStream(fileBytes), f.getExtension(), null);

            // 分块：自定义配置按临时策略的 config 走规则分片（其他策略类型的预览结果可能与实际略有差异）
            List<ChunkData> allChunks;
            boolean hasSegments = parseResult.getSegments() != null && !parseResult.getSegments().isEmpty();
            if (hasSegments) {
                allChunks = chunkingService.chunkBySegments(parseResult.getSegments(), strategyId);
            } else if (tmpStrategy != null) {
                allChunks = chunkingService.chunk(parseResult.getText(), configResolver.resolve(tmpStrategy));
            } else {
                allChunks = chunkingService.chunk(parseResult.getText(), strategyId);
            }

            // 限制预览数量为前 20 个
            int previewLimit = Math.min(allChunks.size(), 20);
            List<Map<String, Object>> previewChunks = new ArrayList<>();
            for (int i = 0; i < previewLimit; i++) {
                ChunkData chunk = allChunks.get(i);
                Map<String, Object> chunkMap = new LinkedHashMap<>();
                chunkMap.put("index", chunk.getIndex());
                // 生成标题
                String title = buildChunkTitle(chunk);
                chunkMap.put("title", title);
                // 内容截断到 300 字符
                String content = chunk.getContent();
                if (content != null && content.length() > 300) {
                    content = content.substring(0, 300) + "...";
                }
                chunkMap.put("content", content);
                previewChunks.add(chunkMap);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("fileId", fileId);
            result.put("fileName", f.getName());
            result.put("pages", parseResult.getPages());
            result.put("totalChunks", allChunks.size());
            result.put("previewChunks", previewChunks);
            return result;
        } catch (IOException e) {
            throw new RuntimeException("文件预览失败: " + e.getMessage(), e);
        }
    }

    private String buildChunkTitle(ChunkData chunk) {
        if (chunk.getChunkType() != null && "image".equals(chunk.getChunkType())) {
            return chunk.getPageNumber() != null
                    ? "图片（第" + chunk.getPageNumber() + "页）"
                    : "图片";
        }
        if (chunk.getStartTime() != null && chunk.getEndTime() != null) {
            return String.format("#%d %.0fs - %.0fs", chunk.getIndex() + 1,
                    chunk.getStartTime(), chunk.getEndTime());
        }
        if (chunk.getPageNumber() != null) {
            return chunk.getPageRange() != null
                    ? "第 " + chunk.getPageRange() + " 页"
                    : "第 " + chunk.getPageNumber() + " 页";
        }
        return "第 " + (chunk.getIndex() + 1) + " 部分";
    }

    private String detectCategory(String ext) {
        if (ext == null) return "document";
        ext = ext.toLowerCase();
        if (Set.of("pdf", "docx", "doc", "xlsx", "xls", "pptx", "ppt", "txt", "md", "csv").contains(ext)) return "document";
        if (Set.of("jpg", "jpeg", "png", "gif", "bmp", "webp").contains(ext)) return "image";
        if (Set.of("mp3", "wav", "m4a", "aac", "ogg").contains(ext)) return "audio";
        if (Set.of("mp4", "avi", "mov", "mkv", "flv").contains(ext)) return "video";
        return "document";
    }

    private String resolveStrategy(String kbId, String extension) {
        try {
            String extWithDot = "." + extension;

        // 1. 找扩展名匹配 + 默认策略（仅知识库级策略参与自动匹配，文件级专属策略排除）
        List<KbParseStrategy> all = strategyMapper.selectList(
                new LambdaQueryWrapper<KbParseStrategy>()
                        .eq(KbParseStrategy::getKbId, kbId)
                        .isNull(KbParseStrategy::getFileId));
            for (KbParseStrategy s : all) {
                if (s.getIsDefault() != null && s.getIsDefault() == 1
                        && matchesExtension(s.getExtensions(), extWithDot)) {
                    return s.getId();
                }
            }

            // 2. 找扩展名匹配（非默认）
            for (KbParseStrategy s : all) {
                if (matchesExtension(s.getExtensions(), extWithDot)) {
                    return s.getId();
                }
            }

            // 3. 找默认策略（不按扩展名）
            for (KbParseStrategy s : all) {
                if (s.getIsDefault() != null && s.getIsDefault() == 1) {
                    return s.getId();
                }
            }

            // 4. 返回第一个
            if (!all.isEmpty()) return all.get(0).getId();
        } catch (Exception e) {
            log.warn("Failed to resolve strategy", e);
        }
        return null;
    }

    /**
     * 判断扩展名是否在策略的 extensions JSON 数组中
     */
    private boolean matchesExtension(String extensionsJson, String extWithDot) {
        if (extensionsJson == null || extensionsJson.isBlank()) return false;
        try {
            JSONArray arr = JSONUtil.parseArray(extensionsJson);
            for (int i = 0; i < arr.size(); i++) {
                if (extWithDot.equals(arr.getStr(i))) return true;
            }
        } catch (Exception e) {
            log.debug("Failed to parse extensions JSON: {}", extensionsJson);
        }
        return false;
    }

    private FileDto toDto(KbFile f) {
        var d = new FileDto();
        d.setId(f.getId());
        d.setName(f.getName());
        d.setCategory(f.getCategory());
        d.setExtension(f.getExtension());
        d.setSize(f.getSize());
        d.setUrl("/api/kb/" + f.getKbId() + "/files/" + f.getId() + "/download");
        d.setStatus(f.getStatus());
        d.setProgress(f.getProgress());
        d.setStage(f.getStage());
        d.setChunkCount(f.getChunkCount());
        d.setProcessingMode(f.getProcessingMode());
        d.setEnableGraphBuild(f.getEnableGraphBuild());
        d.setFolderId(f.getFolderId());
        d.setDeletedAt(f.getDeletedAt());
        d.setCreatedAt(f.getCreatedAt());
        d.setUpdatedAt(f.getUpdatedAt());
        // 解析策略 ID + 名称 + 高级配置（分片策略设置对话框据此回填；含文件级专属策略）
        d.setParseStrategyId(f.getParseStrategyId());
        if (f.getParseStrategyId() != null) {
            KbParseStrategy strategy = strategyMapper.selectById(f.getParseStrategyId());
            if (strategy != null) {
                d.setParseStrategyName(strategy.getName());
                if (StrUtil.isNotBlank(strategy.getAdvanced())) {
                    try {
                        d.setParseStrategyAdvanced(JSONUtil.parseObj(strategy.getAdvanced()));
                    } catch (Exception e) {
                        log.debug("Failed to parse strategy advanced for file {}", f.getId());
                    }
                }
            }
        }
        return d;
    }
}
