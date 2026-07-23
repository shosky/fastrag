package com.fastrag.module.iam.service;
import com.fastrag.module.iam.model.PermissionDto; import com.fastrag.module.iam.model.PermissionCreateRequest;
import java.util.List;

public interface PermissionService {
    List<PermissionDto> listPermissions();
    List<PermissionDto> getPermissionTree();
    PermissionDto createPermission(PermissionCreateRequest req);
    PermissionDto updatePermission(Long id, PermissionCreateRequest req);
    void deletePermission(Long id);
}
