package com.fastrag.ai.rerank;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RerankService {
    private final WebClient aiWebClient;

    public List<Map<String, Object>> rerank(String model, String query, List<String> documents, int topK) {
        try {
            Map<String, Object> req = Map.of("model", model, "query", query, "documents", documents, "top_n", topK);
            aiWebClient.post().uri("/v1/rerank").bodyValue(req).retrieve().bodyToMono(String.class).block();
            return List.of();
        } catch (Exception e) { log.error("Rerank failed", e); return List.of(); }
    }
}
