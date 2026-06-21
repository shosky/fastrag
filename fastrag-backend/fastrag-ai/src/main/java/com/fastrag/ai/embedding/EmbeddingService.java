package com.fastrag.ai.embedding;

import com.fastrag.ai.model.EmbeddingRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {
    private final WebClient aiWebClient;
    private final ObjectMapper objectMapper;

    public List<Float> embed(String model, String text) {
        return embed(model, List.of(text)).get(0);
    }

    public List<List<Float>> embed(String model, List<String> texts) {
        EmbeddingRequest req = new EmbeddingRequest();
        req.setModel(model); req.setInput(texts);
        try {
            String resp = aiWebClient.post().uri("/v1/embeddings").bodyValue(req)
                    .retrieve().bodyToMono(String.class).block();
            JsonNode root = objectMapper.readTree(resp);
            List<List<Float>> embeddings = new ArrayList<>();
            for (JsonNode item : root.path("data")) {
                List<Float> vec = new ArrayList<>();
                for (JsonNode v : item.path("embedding")) vec.add(v.floatValue());
                embeddings.add(vec);
            }
            return embeddings;
        } catch (Exception e) { log.error("Embedding failed", e); throw new RuntimeException("向量化失败", e); }
    }
}
