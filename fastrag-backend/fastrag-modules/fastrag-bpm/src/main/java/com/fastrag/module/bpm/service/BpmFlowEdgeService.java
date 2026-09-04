package com.fastrag.module.bpm.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fastrag.module.bpm.dto.EdgeRequest;
import com.fastrag.module.bpm.dto.EdgeVO;
import com.fastrag.module.bpm.entity.BpmFlowEdge;

import java.util.List;

public interface BpmFlowEdgeService extends IService<BpmFlowEdge> {
    List<EdgeVO> listByVersion(String versionId);
    EdgeVO get(String edgeId);
    EdgeVO create(String versionId, EdgeRequest req);
    EdgeVO update(String edgeId, EdgeRequest req);
    void delete(String edgeId);
    /** 按 source/target 删除边(节点删除前清理) */
    void deleteByNodeKey(String versionId, String nodeKey);
}