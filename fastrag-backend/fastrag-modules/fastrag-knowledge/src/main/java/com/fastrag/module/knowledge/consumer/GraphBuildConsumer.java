package com.fastrag.module.knowledge.consumer;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.ai.llm.LlmService;
import com.fastrag.common.handler.GraphBuildHandler;
import com.fastrag.infra.neo4j.Neo4jService;
import com.fastrag.module.knowledge.entity.KbChunk;
import com.fastrag.module.knowledge.entity.KbFile;
import com.fastrag.module.knowledge.entity.KbParseStrategy;
import com.fastrag.module.knowledge.mapper.KbFileMapper;
import com.fastrag.module.knowledge.mapper.KbParseStrategyMapper;
import com.fastrag.module.platform.entity.ModelRecord;
import com.fastrag.module.platform.mapper.ModelRecordMapper;
import com.fastrag.module.graph.entity.KbGraphIndex;
import com.fastrag.module.knowledge.mapper.KbChunkMapper;
import com.fastrag.module.graph.mapper.KbGraphIndexMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 知识图谱构建服务 (原 RabbitMQ Consumer，现改为直接调用)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphBuildConsumer implements GraphBuildHandler {

    private final KbChunkMapper chunkMapper;
    private final Neo4jService neo4jService;
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
    public void handleGraphBuild(Map<String, Object> message) {
        String kbId = (String) message.get("kbId");
        String fileId = (String) message.get("fileId");

        log.info("Start building graph for kb: {}, file: {}", kbId, fileId);

        // 获取解析策略中配置的 LLM 模型和 API 配置
        String llmModel = null;
        String apiUrl = null;
        String apiKey = null;

        if (fileId != null && !fileId.isEmpty()) {
            KbFile file = fileMapper.selectById(fileId);
            if (file != null && file.getParseStrategyId() != null) {
                KbParseStrategy strategy = parseStrategyMapper.selectById(file.getParseStrategyId());
                if (strategy != null && strategy.getLlmModel() != null && !strategy.getLlmModel().isEmpty()) {
                    llmModel = strategy.getLlmModel();
                    log.info("Using LLM model from parse strategy: {}", llmModel);

                    // 从 model 表获取 API URL 和 API Key
                    LambdaQueryWrapper<ModelRecord> modelQuery = new LambdaQueryWrapper<ModelRecord>()
                            .eq(ModelRecord::getCode, llmModel)
                            .eq(ModelRecord::getStatus, "online")
                            .last("LIMIT 1");
                    ModelRecord modelRecord = modelRecordMapper.selectOne(modelQuery);
                    if (modelRecord != null) {
                        apiUrl = modelRecord.getApiUrl();
                        apiKey = modelRecord.getApiKeyRef();
                        log.info("Found model config - apiUrl: {}, hasApiKey: {}", apiUrl, apiKey != null && !apiKey.isEmpty());
                    } else {
                        log.warn("Model not found or offline: {}", llmModel);
                    }
                }
            }
        }

        try {
            updateGraphStatus(kbId, "building", 0, 0, 0);

            // 查询切片：如果有 fileId 则只查该文件，否则查整个知识库
            LambdaQueryWrapper<KbChunk> query = new LambdaQueryWrapper<KbChunk>()
                    .eq(KbChunk::getKbId, kbId);
            if (fileId != null && !fileId.isEmpty()) {
                query.eq(KbChunk::getFileId, fileId);
            }

            List<KbChunk> chunks = chunkMapper.selectList(query);

            int total = chunks.size();
            int processed = 0;
            int entityCount = 0;
            int relationCount = 0;

            for (KbChunk chunk : chunks) {
                try {
                    EntityRelationResult result = extractEntitiesAndRelations(chunk.getContent(), llmModel, apiUrl, apiKey);

                    for (EntityItem entity : result.getEntities()) {
                        neo4jService.createEntity(kbId, entity.getName(), entity.getType());
                        entityCount++;
                    }

                    for (RelationItem rel : result.getRelations()) {
                        neo4jService.createRelation(kbId, rel.getSource(), rel.getTarget(), rel.getLabel());
                        relationCount++;
                    }
                } catch (Exception e) {
                    log.warn("Failed to process chunk {}: {}", chunk.getId(), e.getMessage());
                }

                processed++;
                if (processed % 10 == 0 || processed == total) {
                    updateGraphProgress(kbId, processed, total, entityCount, relationCount);
                }
            }

            updateGraphStatus(kbId, "completed", 100, entityCount, relationCount);
            log.info("Graph build completed for file: {}, entities: {}, relations: {}", fileId, entityCount, relationCount);

        } catch (Exception e) {
            log.error("Graph build failed for file: {}", fileId, e);
            updateGraphStatus(kbId, "failed", 0, 0, 0);
        }
    }

    private EntityRelationResult extractEntitiesAndRelations(String text, String llmModel, String apiUrl, String apiKey) {
        String prompt = """
                请从以下文本中提取实体和关系，返回JSON格式：
                {"entities":[{"name":"实体名","type":"实体类型"}],"relations":[{"source":"源实体","target":"目标实体","label":"关系类型"}]}
                只返回JSON，不要其他内容。如果文本太短或没有明确实体，返回空数组。

                文本：
                """ + text.substring(0, Math.min(text.length(), 2000));

        String response = llmService.chat(llmModel, prompt, apiUrl, apiKey);
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

    private void updateGraphStatus(String kbId, String status, int progress, int entityCount, int relationCount) {
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
                graphIndexMapper.insert(index);
            } else {
                index.setStatus(status);
                index.setBuildProgress(progress);
                index.setEntityExtractProgress(progress);
                index.setRelationExtractProgress(progress);
                index.setEntityCount(entityCount);
                index.setRelationCount(relationCount);
                graphIndexMapper.updateById(index);
            }
        } catch (Exception e) {
            log.warn("Failed to update graph status: {}", e.getMessage());
        }
    }

    private void updateGraphProgress(String kbId, int processed, int total, int entityCount, int relationCount) {
        int progress = total > 0 ? (int) ((double) processed / total * 100) : 0;
        updateGraphStatus(kbId, "building", progress, entityCount, relationCount);
    }
}
