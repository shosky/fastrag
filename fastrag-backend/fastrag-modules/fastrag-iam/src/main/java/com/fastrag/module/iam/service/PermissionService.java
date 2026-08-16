package com.fastrag.module.iam.service;

import com.fastrag.module.iam.model.PermissionDto;
import com.fastrag.module.iam.model.PermissionCreateRequest;
import java.util.List;

/**
 * 权限管理服务接口。
 *
 * <p>定义系统权限的查询和管理操作，包括权限列表查询、权限树构建、
 * 权限的创建、更新和删除。权限信息用于控制菜单可见性和API访问控制。</p>
 */
public interface PermissionService {
    List<PermissionDto> listPermissions();
    List<PermissionDto> getPermissionTree();
    PermissionDto createPermission(PermissionCreateRequest req);
    PermissionDto updatePermission(Long id, PermissionCreateRequest req);
    void deletePermission(Long id);
}
