package com.fastrag.module.knowledge.service.impl;

import com.fastrag.common.service.KbOperationService;
import com.fastrag.module.knowledge.model.KbDto;
import com.fastrag.module.knowledge.service.KbService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KbOperationServiceImpl implements KbOperationService {

    private final KbService kbService;

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listKnowledgeBases(String userId) {
        try {
            Map<String, Object> result = kbService.list(null, null, 1, 100);
            Object listObj = result.get("list");
            if (listObj instanceof List) {
                List<KbDto> kbList = (List<KbDto>) listObj;
                return kbList.stream().map(kb -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", kb.getId());
                    map.put("name", kb.getName());
                    map.put("description", kb.getDescription());
                    map.put("category", kb.getCategory());
                    return map;
                }).collect(Collectors.toList());
            }
            return List.of();
        } catch (Exception e) {
            log.error("[KbOperationService] 列出知识库失败", e);
            return List.of();
        }
    }

    @Override
    public List<Map<String, Object>> queryKnowledgeBase(String kbId, String query, int topK) {
        try {
            log.info("[KbOperationService] 检索知识库: kbId={}, query={}, topK={}", kbId, query, topK);

            // TODO: 集成 RetrievalService 进行真实检索
            // 当前返回占位提示，待 RetrievalService 接入后替换
            return List.of(
                Map.of(
                    "content", "知识库 '" + kbId + "' 中关于 '" + query + "' 的检索结果（检索服务集成中）",
                    "score", 0.95,
                    "source", "KB:" + kbId
                )
            );
        } catch (Exception e) {
            log.error("[KbOperationService] 检索知识库失败", e);
            return List.of();
        }
    }
}
