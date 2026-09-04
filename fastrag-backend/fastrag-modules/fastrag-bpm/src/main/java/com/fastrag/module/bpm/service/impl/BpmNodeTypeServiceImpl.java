package com.fastrag.module.bpm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fastrag.module.bpm.dto.NodeTypeVO;
import com.fastrag.module.bpm.entity.BpmNodeTypeMeta;
import com.fastrag.module.bpm.enums.BpmErrorCode;
import com.fastrag.module.bpm.mapper.BpmNodeTypeMetaMapper;
import com.fastrag.module.bpm.service.BpmNodeTypeService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 节点类型元数据服务实现。
 *
 * 数据来源：
 * - 真实数据由 init-scripts/migration-20260902-bpm-data.sql 写入 bpm_node_type_meta 表
 * - 表里覆盖了 9 种内置节点类型(start/end/user_input/llm/kb_retrieval/intent/http/condition/subflow)
 *
 * 行为契约：
 * - listEnabled() 按 sortOrder 升序返回所有启用节点，供画布左侧节点库使用
 * - getByType() 返回单个节点元数据；找不到时按 BpmErrorCode.NODE_TYPE_NOT_FOUND 返回 null（Controller 兜底）
 */
@Service
public class BpmNodeTypeServiceImpl
        extends ServiceImpl<BpmNodeTypeMetaMapper, BpmNodeTypeMeta>
        implements BpmNodeTypeService {

    @Override
    public List<NodeTypeVO> listEnabled() {
        LambdaQueryWrapper<BpmNodeTypeMeta> q = new LambdaQueryWrapper<>();
        q.eq(BpmNodeTypeMeta::getEnabled, true)
         .orderByAsc(BpmNodeTypeMeta::getSortOrder);
        return this.list(q).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public NodeTypeVO getByType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        BpmNodeTypeMeta meta = this.getById(type);
        return meta == null ? null : toVO(meta);
    }

    private NodeTypeVO toVO(BpmNodeTypeMeta meta) {
        NodeTypeVO vo = new NodeTypeVO();
        BeanUtils.copyProperties(meta, vo);
        return vo;
    }
}