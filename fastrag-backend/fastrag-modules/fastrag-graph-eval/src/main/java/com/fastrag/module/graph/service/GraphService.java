package com.fastrag.module.graph.service;

import java.util.*;

public interface GraphService {
    Map<String,Object> getGraphData(String kbId,Integer maxNodes,Boolean excludeChunks);
    Map<String,Object> getGraphStats(String kbId);
    Map<String,Object> getIndexStatus(String kbId);
    void buildIndex(String kbId,String mode,List<String> fileIds);
    void retryBuild(String kbId);
    Map<String,Object> getBuildStatus(String kbId);
    Map<String,Object> getSettings(String kbId);
    void saveSettings(String kbId,Map<String,Object> settings);

    /** 关键词子图查询 */
    Map<String,Object> searchNodes(String kbId, String keyword, Integer maxNodes);

    /** 获取实体类型标签列表 */
    List<String> getLabels(String kbId);

    /** 删除文件关联的图谱数据 */
    void deleteFileGraph(String kbId, String fileId);

    /** Personalized PageRank 排序（检索增强用） */
    List<Map<String,Object>> rankChunksByPpr(String kbId, List<String> entityNames, int topK);
}
