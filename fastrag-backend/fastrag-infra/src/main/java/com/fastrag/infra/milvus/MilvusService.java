package com.fastrag.infra.milvus;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.*;

/**
 * Milvus 向量数据库服务 (Stub 实现)
 * 向量存储/检索功能已禁用，仅保留接口兼容性。
 */
@Slf4j
@Service
public class MilvusService {

    public void createCollection(String name, int dimension) {
        log.debug("Milvus disabled - skip createCollection: {}", name);
    }

    public void insert(String collection, List<String> ids, List<List<Float>> vectors, String kbId, String fileId, List<Long> chunkIndices) {
        log.debug("Milvus disabled - skip insert into: {}", collection);
    }

    public List<Map<String, Object>> search(String collection, List<Float> queryVector, int topK) {
        log.debug("Milvus disabled - returning empty results for: {}", collection);
        return Collections.emptyList();
    }

    public void deleteByFileId(String collection, String fileId) {
        log.debug("Milvus disabled - skip deleteByFileId: {} in {}", fileId, collection);
    }
}
