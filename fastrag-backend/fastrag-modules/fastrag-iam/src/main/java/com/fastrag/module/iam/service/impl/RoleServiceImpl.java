package com.fastrag.module.iam.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.common.exception.BusinessException;
import com.fastrag.module.iam.entity.*; import com.fastrag.module.iam.mapper.*;
import com.fastrag.module.iam.model.*; import com.fastrag.module.iam.service.RoleService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import java.util.*; import java.util.stream.Collectors;
@Service @RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {
    private final SysRoleMapper roleMapper; private final SysRolePermissionMapper rpMapper;
    @Override public List<RoleDto> listRoles() { return roleMapper.selectList(null).stream().map(this::toDto).collect(Collectors.toList()); }
    @Override public RoleDto getRole(String id) { var r=roleMapper.selectById(id); if(r==null)throw BusinessException.notFound("角色不存在"); return toDto(r); }
    @Override @Transactional public RoleDto createRole(RoleCreateRequest req) {
        var r=new SysRole(); r.setName(req.getName()); r.setDescription(req.getDescription()); r.setRoleKey("custom_"+System.currentTimeMillis()); r.setIsDefault(0); r.setIsSystem(0);
        roleMapper.insert(r); savePerms(r.getId(),req.getPermissions()); return toDto(r);
    }
    @Override @Transactional public RoleDto updateRole(String id,RoleCreateRequest req) {
        var r=roleMapper.selectById(id); if(r==null)throw BusinessException.notFound("角色不存在");
        r.setName(req.getName()); r.setDescription(req.getDescription()); roleMapper.updateById(r);
        rpMapper.delete(new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId,id)); savePerms(id,req.getPermissions()); return toDto(r);
    }
    @Override public void deleteRole(String id) { var r=roleMapper.selectById(id); if(r!=null&&r.getIsSystem()==1)throw BusinessException.badRequest("系统角色不可删除"); roleMapper.deleteById(id); }
    @Override public void setDefault(String id) {
        roleMapper.update(new SysRole(){{setIsDefault(0);}},new LambdaQueryWrapper<SysRole>().eq(SysRole::getIsDefault,1));
        var r=roleMapper.selectById(id); if(r!=null){r.setIsDefault(1);roleMapper.updateById(r);}
    }
    private void savePerms(String roleId,List<String> perms){ if(perms==null)return; perms.forEach(p->{var rp=new SysRolePermission();rp.setRoleId(roleId);rp.setPermissionKey(p);rpMapper.insert(rp);}); }
    private RoleDto toDto(SysRole r){ var d=new RoleDto();d.setId(r.getId());d.setRoleKey(r.getRoleKey());d.setName(r.getName());d.setDescription(r.getDescription());
        d.setDefault(r.getIsDefault()==1);d.setSystem(r.getIsSystem()==1);d.setCreatedAt(r.getCreatedAt());d.setUpdatedAt(r.getUpdatedAt());
        d.setPermissions(rpMapper.selectList(new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId,r.getId())).stream().map(SysRolePermission::getPermissionKey).collect(Collectors.toList())); return d; }
}
