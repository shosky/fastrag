package com.fastrag.module.knowledge.chunking;

import cn.hutool.json.JSONUtil;
import com.fastrag.module.knowledge.entity.KbParseStrategy;
import com.fastrag.module.knowledge.mapper.KbParseStrategyMapper;
import com.fastrag.module.knowledge.parser.ParseResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkingServiceImpl implements ChunkingService {

    private final KbParseStrategyMapper strategyMapper;

    @Override
    public List<ChunkData> chunk(String text, String strategyId) {
        KbParseStrategy strategy = strategyId != null ? strategyMapper.selectById(strategyId) : null;

        int chunkLength = 500;
        int overlap = 50;
        String delimiter = "\n\n";

        if (strategy != null && strategy.getAdvanced() != null) {
            try {
                Map<String, Object> advanced = JSONUtil.toBean(strategy.getAdvanced(), Map.class);
                if (advanced.containsKey("chunkLength")) chunkLength = ((Number) advanced.get("chunkLength")).intValue();
                if (advanced.containsKey("overlap")) overlap = ((Number) advanced.get("overlap")).intValue();
                if (advanced.containsKey("delimiter")) delimiter = (String) advanced.get("delimiter");
            } catch (Exception e) {
                log.warn("Failed to parse strategy advanced config", e);
            }
        }

        return ruleBasedChunk(text, chunkLength, delimiter, overlap);
    }

    @Override
    public List<ChunkData> chunkBySegments(List<ParseResult.ChunkTimeSegment> segments) {
        List<ChunkData> chunks = new ArrayList<>();
        if (segments == null || segments.isEmpty()) {
            return chunks;
        }

        for (int i = 0; i < segments.size(); i++) {
            ParseResult.ChunkTimeSegment seg = segments.get(i);
            String text = seg.getText() != null ? seg.getText().trim() : "";
            if (text.isEmpty()) {
                continue;
            }

            chunks.add(ChunkData.builder()
                    .id("chunk_" + i)
                    .index(i)
                    .content(text)
                    .startTime(seg.getStartTime())
                    .endTime(seg.getEndTime())
                    .build());
        }

        log.info("Created {} time-based chunks from {} segments", chunks.size(), segments.size());
        return chunks;
    }

    private List<ChunkData> ruleBasedChunk(String text, int chunkLength, String delimiter, int overlap) {
        List<ChunkData> chunks = new ArrayList<>();

        if (text == null || text.isEmpty()) {
            return chunks;
        }

        String[] parts = text.split(delimiter.isEmpty() ? "\n" : delimiter, -1);
        StringBuilder currentChunk = new StringBuilder();
        int index = 0;

        for (String part : parts) {
            if (currentChunk.length() + part.length() > chunkLength && currentChunk.length() > 0) {
                chunks.add(ChunkData.builder()
                        .id("chunk_" + index)
                        .index(index)
                        .content(currentChunk.toString().trim())
                        .build());
                index++;
                String chunkText = currentChunk.toString();
                String overlapText = chunkText.substring(Math.max(0, chunkText.length() - overlap));
                currentChunk = new StringBuilder(overlapText);
            }
            if (currentChunk.length() > 0) {
                currentChunk.append(delimiter);
            }
            currentChunk.append(part);
        }

        if (currentChunk.length() > 0) {
            String content = currentChunk.toString().trim();
            if (!content.isEmpty()) {
                chunks.add(ChunkData.builder()
                        .id("chunk_" + index)
                        .index(index)
                        .content(content)
                        .build());
            }
        }

        return chunks;
    }
}
