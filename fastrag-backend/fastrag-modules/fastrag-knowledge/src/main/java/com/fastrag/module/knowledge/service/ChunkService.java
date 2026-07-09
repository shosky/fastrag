package com.fastrag.module.knowledge.service;

import com.fastrag.module.knowledge.entity.KbChunk;
import com.fastrag.module.knowledge.model.ChunkCreateRequest;
import com.fastrag.module.knowledge.model.ChunkDto;

import java.util.List;
import java.util.Map;

public interface ChunkService {
    /** 分页查询分片列表 */
    Map<String, Object> list(String kbId, String fileId, int page, int pageSize);

    /** 查询知识库总分片数 */
    long getCount(String kbId);

    /** 查询单个分片详情 */
    ChunkDto getById(String kbId, String id);

    /** 新增分片（含向量同步） */
    ChunkDto create(String kbId, ChunkCreateRequest req);

    /** 更新分片（含向量同步） */
    ChunkDto update(String kbId, String id, Map<String, Object> fields);

    /** 删除单个分片（含向量同步） */
    void delete(String kbId, String id);

    /** 批量删除分片（含向量同步） */
    void batchDelete(String kbId, List<String> ids);
}
