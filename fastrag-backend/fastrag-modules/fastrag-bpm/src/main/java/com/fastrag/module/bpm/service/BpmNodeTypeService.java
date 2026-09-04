package com.fastrag.module.bpm.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fastrag.module.bpm.dto.NodeTypeVO;
import com.fastrag.module.bpm.entity.BpmNodeTypeMeta;

import java.util.List;

public interface BpmNodeTypeService extends IService<BpmNodeTypeMeta> {
    /** 全部启用的节点类型元数据(供画布节点库使用) */
    List<NodeTypeVO> listEnabled();
    /** 按 type 查询单个 */
    NodeTypeVO getByType(String type);
}