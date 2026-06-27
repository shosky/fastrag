package com.fastrag.infra.elasticsearch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.*;

/**
 * Elasticsearch 全文检索服务 (Stub 实现)
 * 全文检索功能已禁用，仅保留接口兼容性。
 */
@Slf4j
@Service
public class ESIndexService {

    public void createIndex(String indexName) {
        log.debug("Elasticsearch disabled - skip createIndex: {}", indexName);
    }

    public void indexDocument(String indexName, String id, Map<String, Object> doc) {
        log.debug("Elasticsearch disabled - skip indexDocument: {}", id);
    }

    public List<Map<String, Object>> search(String indexName, String query, int topK) {
        log.debug("Elasticsearch disabled - returning empty results for: {}", indexName);
        return Collections.emptyList();
    }

    public void deleteByQuery(String indexName, String field, String value) {
        log.debug("Elasticsearch disabled - skip deleteByQuery: {}={}", field, value);
    }
}
