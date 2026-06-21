package com.fastrag.infra.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ESIndexService {
    private final ElasticsearchClient esClient;

    public void createIndex(String indexName) {
        try {
            boolean exists = esClient.indices().exists(e -> e.index(indexName)).value();
            if (!exists) {
                esClient.indices().create(c -> c.index(indexName)
                    .settings(s -> s.numberOfShards("1").numberOfReplicas("0"))
                    .mappings(m -> m.properties("content", p -> p.text(t -> t.analyzer("standard")))
                        .properties("kbId", p -> p.keyword(k -> k))
                        .properties("fileId", p -> p.keyword(k -> k))
                        .properties("chunkIndex", p -> p.integer(i -> i))));
            }
        } catch (IOException e) { log.error("Failed to create ES index: {}", indexName, e); }
    }

    public void indexDocument(String indexName, String id, Map<String, Object> doc) {
        try { esClient.index(i -> i.index(indexName).id(id).document(doc)); }
        catch (IOException e) { log.error("Failed to index document", e); }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> search(String indexName, String query, int topK) {
        try {
            SearchResponse<Map> resp = esClient.search(s -> s.index(indexName)
                .query(q -> q.multiMatch(m -> m.query(query).fields("content")))
                .highlight(h -> h.fields("content", hf -> hf.preTags("<mark>").postTags("</mark>")))
                .size(topK), Map.class);
            List<Map<String, Object>> results = new ArrayList<>();
            for (Hit<Map> hit : resp.hits().hits()) {
                Map<String, Object> r = new HashMap<>(hit.source());
                r.put("id", hit.id()); r.put("score", hit.score());
                if (hit.highlight() != null && hit.highlight().containsKey("content"))
                    r.put("highlights", hit.highlight().get("content"));
                results.add(r);
            }
            return results;
        } catch (IOException e) { log.error("Search failed", e); return List.of(); }
    }

    public void deleteByQuery(String indexName, String field, String value) {
        try { esClient.deleteByQuery(d -> d.index(indexName).query(q -> q.term(t -> t.field(field).value(value)))); }
        catch (IOException e) { log.error("Failed to delete by query", e); }
    }
}
