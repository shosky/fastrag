package com.fastrag.module.iam.service;
import com.fastrag.module.iam.model.*; import java.util.List;
public interface RoleService { List<RoleDto> listRoles(); RoleDto getRole(String id); RoleDto createRole(RoleCreateRequest req); RoleDto updateRole(String id,RoleCreateRequest req); void deleteRole(String id); void setDefault(String id); }
