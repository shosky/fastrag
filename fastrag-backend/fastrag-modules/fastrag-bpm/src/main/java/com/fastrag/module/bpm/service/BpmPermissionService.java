package com.fastrag.module.bpm.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fastrag.module.bpm.dto.PermissionRequest;
import com.fastrag.module.bpm.entity.BpmFlowPermission;

import java.util.List;

public interface BpmPermissionService extends IService<BpmFlowPermission> {
    /** 查询某个流程的全部授权 */
    List<BpmFlowPermission> listByFlow(String flowDefId);
    /** 授予权限(已存在则忽略) */
    void grant(String flowDefId, PermissionRequest req, String grantedBy);
    /** 撤销权限 */
    void revoke(String flowDefId, String subjectType, String subjectId, String permission);
    /** 鉴权判断:subjectId 对该流程是否拥有指定权限,ownerId 自动拥有所有权限 */
    boolean hasPermission(String flowDefId, String subjectId, String permission);
}