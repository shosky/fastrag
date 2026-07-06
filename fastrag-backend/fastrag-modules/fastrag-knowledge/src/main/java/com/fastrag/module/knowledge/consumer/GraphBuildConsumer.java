package com.fastrag.module.knowledge.consumer;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.ai.llm.LlmService;
import com.fastrag.common.handler.GraphBuildHandler;
import com.fastrag.infra.graph.GraphStore;
import com.fastrag.module.knowledge.entity.KbChunk;
import com.fastrag.module.knowledge.entity.KbFile;
import com.fastrag.module.knowledge.entity.KbParseStrategy;
import com.fastrag.module.knowledge.mapper.KbFileMapper;
import com.fastrag.module.knowledge.mapper.KbParseStrategyMapper;
import com.fastrag.module.graph.entity.KbGraphIndex;
import com.fastrag.module.graph.mapper.KbGraphIndexMapper;
import com.fastrag.module.platform.entity.ModelRecord;
import com.fastrag.module.platform.mapper.ModelRecordMapper;
import com.fastrag.module.knowledge.mapper.KbChunkMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识图谱构建服务 (RabbitMQ Consumer)
 *
 * <p>从 chunks 中提取实体和关系，通过 GraphStore 写入存储。
 * 支持单文件(fileId)和全库(kbId)构建模式。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphBuildConsumer implements GraphBuildHandler {

    private final KbChunkMapper chunkMapper;
    private final GraphStore graphStore;
    private final LlmService llmService;
    private final KbGraphIndexMapper graphIndexMapper;
    private final KbFileMapper fileMapper;
    private final KbParseStrategyMapper parseStrategyMapper;
    private final ModelRecordMapper modelRecordMapper;

    @Data
    static class EntityRelationResult {
        private List<EntityItem> entities = new ArrayList<>();
        private List<RelationItem> relations = new ArrayList<>();
    }

    @Data
    static class EntityItem {
        private String name;
        private String type;
    }

    @Data
    static class RelationItem {
        private String source;
        private String target;
        private String label;
    }

    @Override
    @RabbitListener(queues = "fastrag.graph-build.queue")
    public void handleGraphBuild(Map<String, Object> message) {
        String kbId = (String) message.get("kbId");
        // 兼容新旧消息格式：支持单 fileId 和多 fileIds
        String fileId = (String) message.get("fileId");
        List<String> fileIds = null;
        if (message.containsKey("fileIds")) {
            Object raw = message.get("fileIds");
            if (raw instanceof List) {
                fileIds = ((List<?>) raw).stream().map(Object::toString).collect(Collectors.toList());
            }
        }
        // 若只有单 fileId，转为列表统一处理
        if (fileId != null && !fileId.isEmpty() && fileIds == null) {
            fileIds = List.of(fileId);
        }

        String mode = message.containsKey("mode") ? String.valueOf(message.get("mode")) : "full";

        log.info("Start building graph for kb: {}, fileIds: {}, mode: {}", kbId, fileIds, mode);

        // 获取 LLM 配置（用于实体/关系提取）
        LlmConfig llmConfig = resolveLlmConfig(fileIds);

        try {
            // 查询需要处理的 chunks
            List<KbChunk> chunks = queryChunks(kbId, fileIds, mode);
            int total = chunks.size();

            if (total == 0) {
                log.info("No chunks found for graph build, kb: {}, fileIds: {}", kbId, fileIds);
                updateGraphStatus(kbId, "completed", 100, 0, 0, total, total);
                return;
            }

            updateGraphStatus(kbId, "building", 0, 0, 0, 0, total);

            int processed = 0;
            int entityCount = 0;
            int relationCount = 0;
            int failedChunks = 0;

            for (KbChunk chunk : chunks) {
                try {
                    EntityRelationResult result = extractEntitiesAndRelations(
                            chunk.getContent(), llmConfig.getModel(), llmConfig.getApiUrl(), llmConfig.getApiKey());

                    // 批量写入实体（GraphStore 内部去重）
                    for (EntityItem entity : result.getEntities()) {
                        graphStore.createEntity(kbId, entity.getName(), entity.getType());
                        entityCount++;
                    }

                    // 批量写入关系（GraphStore 内部去重）
                    for (RelationItem rel : result.getRelations()) {
                        graphStore.createRelation(kbId, rel.getSource(), rel.getTarget(), rel.getLabel());
                        relationCount++;
                    }
                } catch (Exception e) {
                    failedChunks++;
                    log.warn("Failed to process chunk {}: {}", chunk.getId(), e.getMessage());
                }

                processed++;
                if (processed % 10 == 0 || processed == total) {
                    updateGraphProgress(kbId, processed, total, entityCount, relationCount, failedChunks);
                }
            }

            updateGraphStatus(kbId, "completed", 100, entityCount, relationCount, total, total - failedChunks);
            log.info("Graph build completed for kb: {}, entities: {}, relations: {}, failed: {}/{}",
                    kbId, entityCount, relationCount, failedChunks, total);

        } catch (Exception e) {
            log.error("Graph build failed for kb: {}, fileIds: {}", kbId, fileIds, e);
            updateGraphStatus(kbId, "failed", 0, 0, 0, 0, 0);
        }
    }

    /**
     * 解析 LLM 配置：优先从 file 对应的 parse strategy 获取，fallback 到默认网关
     */
    private LlmConfig resolveLlmConfig(List<String> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return new LlmConfig(null, null, null);
        }

        // 取第一个文件对应的策略
        KbFile file = fileMapper.selectById(fileIds.get(0));
        if (file != null && file.getParseStrategyId() != null) {
            KbParseStrategy strategy = parseStrategyMapper.selectById(file.getParseStrategyId());
            if (strategy != null && strategy.getLlmModel() != null && !strategy.getLlmModel().isEmpty()) {
                String llmModel = strategy.getLlmModel();
                ModelRecord modelRecord = modelRecordMapper.selectOne(
                        new LambdaQueryWrapper<ModelRecord>()
                                .eq(ModelRecord::getCode, llmModel)
                                .eq(ModelRecord::getStatus, "online")
                                .last("LIMIT 1"));
                if (modelRecord != null) {
                    log.info("Graph build using LLM model from strategy: {}", llmModel);
                    return new LlmConfig(llmModel, modelRecord.getApiUrl(), modelRecord.getApiKeyRef());
                }
            }
        }
        return new LlmConfig(null, null, null);
    }

    /**
     * 查询需要构建图谱的 chunks
     */
    private List<KbChunk> queryChunks(String kbId, List<String> fileIds, String mode) {
        LambdaQueryWrapper<KbChunk> query = new LambdaQueryWrapper<KbChunk>()
                .eq(KbChunk::getKbId, kbId);

        // 如果指定了文件列表，只查这些文件
        if (fileIds != null && !fileIds.isEmpty()) {
            // 逐文件查询（避免 IN 子句过长）
            List<KbChunk> allChunks = new ArrayList<>();
            for (String fid : fileIds) {
                allChunks.addAll(chunkMapper.selectList(
                        new LambdaQueryWrapper<KbChunk>().eq(KbChunk::getKbId, kbId).eq(KbChunk::getFileId, fid)));
            }
            return allChunks;
        }

        // 全库模式：full 查所有，incremental 也查所有（增量需上层过滤）
        return chunkMapper.selectList(query);
    }

    /**
     * 从文本中提取实体和关系（调用 LLM）
     */
    private EntityRelationResult extractEntitiesAndRelations(String text, String llmModel, String apiUrl, String apiKey) {
        // 未配置 LLM 时跳过抽取（避免默认 fallback 到 Ollama）
        if (apiUrl == null || apiUrl.isBlank()) {
            log.debug("No LLM configured for entity extraction, returning empty result");
            return new EntityRelationResult();
        }

        String model = llmModel != null ? llmModel : "default";
        String url = apiUrl;
        String key = apiKey;

        String prompt = """
                请从以下文本中提取实体和关系，返回JSON格式：
                {"entities":[{"name":"实体名","type":"实体类型"}],"relations":[{"source":"源实体","target":"目标实体","label":"关系类型"}]}
                只返回JSON，不要其他内容。如果文本太短或没有明确实体，返回空数组。

                实体类型建议使用：人物、组织、地点、概念、事件、产品、技术 等。
                关系类型建议使用：属于、位于、参与、创建、使用、相关于 等。

                文本：
                """ + text.substring(0, Math.min(text.length(), 2000));

        String response = llmService.chat(model, prompt, url, key, null);
        try {
            String json = response.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("```json?", "").replaceAll("```", "").trim();
            }
            return JSONUtil.toBean(json, EntityRelationResult.class);
        } catch (Exception e) {
            log.warn("Failed to parse LLM response for entity extraction: {}", e.getMessage());
            return new EntityRelationResult();
        }
    }

    /**
     * 更新图谱构建状态（UPSERT）
     */
    private void updateGraphStatus(String kbId, String status, int progress,
                                   int entityCount, int relationCount, int totalChunks, int builtChunks) {
        try {
            KbGraphIndex index = graphIndexMapper.selectById(kbId);
            if (index == null) {
                index = new KbGraphIndex();
                index.setKbId(kbId);
                index.setStatus(status);
                index.setBuildProgress(progress);
                index.setEntityExtractProgress(progress);
                index.setRelationExtractProgress(progress);
                index.setEntityCount(entityCount);
                index.setRelationCount(relationCount);
                index.setTotalChunks(totalChunks);
                index.setBuiltChunks(builtChunks);
                graphIndexMapper.insert(index);
            } else {
                index.setStatus(status);
                index.setBuildProgress(progress);
                index.setEntityExtractProgress(progress);
                index.setRelationExtractProgress(progress);
                index.setEntityCount(entityCount);
                index.setRelationCount(relationCount);
                index.setTotalChunks(totalChunks);
                index.setBuiltChunks(builtChunks);
                graphIndexMapper.updateById(index);
            }
        } catch (Exception e) {
            log.warn("Failed to update graph status: {}", e.getMessage());
        }
    }

    private void updateGraphProgress(String kbId, int processed, int total,
                                     int entityCount, int relationCount, int failedChunks) {
        int progress = total > 0 ? (int) ((double) processed / total * 100) : 0;
        updateGraphStatus(kbId, "building", progress, entityCount, relationCount, total, processed - failedChunks);
    }

    /**
     * LLM 配置内部 DTO
     */
    @Data
    private static class LlmConfig {
        private final String model;
        private final String apiUrl;
        private final String apiKey;
    }
}
