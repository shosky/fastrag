package com.fastrag.module.bpm.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fastrag.module.bpm.dto.NodeRequest;
import com.fastrag.module.bpm.dto.NodeVO;
import com.fastrag.module.bpm.entity.BpmFlowNode;

import java.util.List;

public interface BpmFlowNodeService extends IService<BpmFlowNode> {
    /** 列出版本下全部节点 */
    List<NodeVO> listByVersion(String versionId);
    /** 按 nodeKey 查询单个 */
    NodeVO getByKey(String versionId, String nodeKey);
    /** 创建节点 */
    NodeVO create(String versionId, NodeRequest req);
    /** 更新节点(name/position/config/timeout/retry/onFailure/failureBranchNodeKey/enabled) */
    NodeVO update(String versionId, String nodeKey, NodeRequest req);
    /** 仅更新位置 */
    NodeVO move(String versionId, String nodeKey, Integer x, Integer y);
    /** 仅更新配置 JSON */
    NodeVO updateConfig(String versionId, String nodeKey, String config);
    /** 删除节点(同时清理以该节点为 source/target 的边) */
    void delete(String versionId, String nodeKey);
}