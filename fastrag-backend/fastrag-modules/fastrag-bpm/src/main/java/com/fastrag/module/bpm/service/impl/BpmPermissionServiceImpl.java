package com.fastrag.module.bpm.service.impl;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fastrag.module.bpm.dto.PermissionRequest;
import com.fastrag.module.bpm.entity.BpmFlowDef;
import com.fastrag.module.bpm.entity.BpmFlowPermission;
import com.fastrag.module.bpm.enums.BpmErrorCode;
import com.fastrag.module.bpm.mapper.BpmFlowDefMapper;
import com.fastrag.module.bpm.mapper.BpmFlowPermissionMapper;
import com.fastrag.module.bpm.service.BpmPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service @RequiredArgsConstructor
public class BpmPermissionServiceImpl extends ServiceImpl<BpmFlowPermissionMapper, BpmFlowPermission> implements BpmPermissionService {
    private final BpmFlowDefMapper defMapper;

    @Override public List<BpmFlowPermission> listByFlow(String flowDefId) {
        return baseMapper.selectList(new LambdaQueryWrapper<BpmFlowPermission>().eq(BpmFlowPermission::getFlowDefId, flowDefId));
    }
    @Override public void grant(String flowDefId, PermissionRequest req, String grantedBy) {
        if (StrUtil.isBlank(req.getSubjectType()) || StrUtil.isBlank(req.getSubjectId()) || StrUtil.isBlank(req.getPermission()))
            throw BpmErrorCode.NODE_CONFIG_INVALID.of("subjectType/subjectId/permission 必填");
        // 已存在则忽略(uk_perm)
        BpmFlowPermission exist = baseMapper.selectOne(new LambdaQueryWrapper<BpmFlowPermission>()
                .eq(BpmFlowPermission::getFlowDefId, flowDefId)
                .eq(BpmFlowPermission::getSubjectType, req.getSubjectType())
                .eq(BpmFlowPermission::getSubjectId, req.getSubjectId())
                .eq(BpmFlowPermission::getPermission, req.getPermission()));
        if (exist != null) return;
        BpmFlowPermission p = new BpmFlowPermission();
        p.setFlowDefId(flowDefId);
        p.setSubjectType(req.getSubjectType());
        p.setSubjectId(req.getSubjectId());
        p.setPermission(req.getPermission());
        p.setGrantedBy(grantedBy);
        baseMapper.insert(p);
    }
    @Override public void revoke(String flowDefId, String subjectType, String subjectId, String permission) {
        baseMapper.delete(new LambdaQueryWrapper<BpmFlowPermission>()
                .eq(BpmFlowPermission::getFlowDefId, flowDefId)
                .eq(BpmFlowPermission::getSubjectType, subjectType)
                .eq(BpmFlowPermission::getSubjectId, subjectId)
                .eq(BpmFlowPermission::getPermission, permission));
    }
    @Override public boolean hasPermission(String flowDefId, String subjectId, String permission) {
        if (StrUtil.isBlank(subjectId)) return false;
        BpmFlowDef def = defMapper.selectById(flowDefId);
        if (def != null && subjectId.equals(def.getOwnerId())) return true;
        BpmFlowPermission p = baseMapper.selectOne(new LambdaQueryWrapper<BpmFlowPermission>()
                .eq(BpmFlowPermission::getFlowDefId, flowDefId)
                .eq(BpmFlowPermission::getSubjectId, subjectId)
                .eq(BpmFlowPermission::getPermission, permission));
        return p != null;
    }
}