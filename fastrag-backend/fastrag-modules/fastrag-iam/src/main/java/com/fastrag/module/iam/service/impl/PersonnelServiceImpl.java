package com.fastrag.module.iam.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper; import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fastrag.common.exception.BusinessException; import com.fastrag.common.response.PageResult;
import com.fastrag.module.iam.entity.*; import com.fastrag.module.iam.mapper.*;
import com.fastrag.module.iam.model.*; import com.fastrag.module.iam.service.PersonnelService;
import lombok.RequiredArgsConstructor; import org.springframework.security.crypto.password.PasswordEncoder; import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*; import java.util.stream.Collectors;
@Service @RequiredArgsConstructor
public class PersonnelServiceImpl implements PersonnelService {
    private final SysUserMapper userMapper; private final SysRoleMapper roleMapper;
    private final SysOrgMapper orgMapper; private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder pe;

    @Override public PageResult<PersonnelDto> listPersonnel(int page, int pageSize, String keyword) {
        var w = new LambdaQueryWrapper<SysUser>();
        if (keyword != null && !keyword.isBlank())
            w.like(SysUser::getUsername, keyword).or().like(SysUser::getRealName, keyword);
        w.orderByDesc(SysUser::getCreatedAt);
        var r = userMapper.selectPage(new Page<>(page, pageSize), w);
        return PageResult.of(
            r.getRecords().stream().map(this::toDto).collect(Collectors.toList()),
            r.getTotal(), page, pageSize);
    }

    @Override @Transactional
    public PersonnelDto createPersonnel(PersonnelCreateRequest req) {
        if (userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, req.getUsername())) != null)
            throw BusinessException.badRequest("用户名已存在");
        var u = new SysUser();
        u.setUsername(req.getUsername()); u.setRealName(req.getRealName());
        u.setPhone(req.getPhone()); u.setEmail(req.getEmail());
        String pwd = (req.getPassword() != null && !req.getPassword().isBlank())
            ? req.getPassword() : "123456";
        u.setPasswordHash(pe.encode(pwd));
        u.setOrgId(req.getOrgId()); u.setStatus("enabled");
        userMapper.insert(u);
        // 保存用户角色关联
        if (req.getRoleIds() != null) {
            for (String roleId : req.getRoleIds()) {
                var ur = new SysUserRole();
                ur.setUserId(u.getId()); ur.setRoleId(roleId);
                userRoleMapper.insert(ur);
            }
        }
        return toDto(u);
    }

    @Override public PersonnelDto updatePersonnel(String id, PersonnelCreateRequest req) {
        var u = userMapper.selectById(id);
        if (u == null) throw BusinessException.notFound("用户不存在");
        u.setRealName(req.getRealName()); u.setPhone(req.getPhone()); u.setEmail(req.getEmail());
        if (req.getOrgId() != null) u.setOrgId(req.getOrgId());
        if (req.getPassword() != null && !req.getPassword().isBlank())
            u.setPasswordHash(pe.encode(req.getPassword()));
        userMapper.updateById(u);
        return toDto(u);
    }

    @Override @Transactional
    public void assignRoles(String userId, List<String> roleIds) {
        var u = userMapper.selectById(userId);
        if (u == null) throw BusinessException.notFound("用户不存在");
        // 先删除旧关联
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
            .eq(SysUserRole::getUserId, userId));
        // 插入新关联
        if (roleIds != null) {
            for (String roleId : roleIds) {
                var ur = new SysUserRole();
                ur.setUserId(userId); ur.setRoleId(roleId);
                userRoleMapper.insert(ur);
            }
        }
    }

    @Override public void updateStatus(String userId, String status) {
        var u = userMapper.selectById(userId);
        if (u == null) throw BusinessException.notFound("用户不存在");
        u.setStatus(status);
        userMapper.updateById(u);
    }

    @Override public PersonnelDto findByUsername(String username) {
        var u = userMapper.selectOne(
            new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        return u != null ? toDto(u) : null;
    }

    private PersonnelDto toDto(SysUser u) {
        var d = new PersonnelDto();
        d.setId(u.getId()); d.setUsername(u.getUsername()); d.setRealName(u.getRealName());
        d.setPhone(u.getPhone()); d.setEmail(u.getEmail());
        d.setStatus(u.getStatus()); d.setCreatedAt(u.getCreatedAt());
        if (u.getOrgId() != null) {
            var o = orgMapper.selectById(u.getOrgId());
            d.setOrgName(o != null ? o.getName() : null);
        }
        // 通过 sys_user_role 查询用户的所有角色
        var userRoles = userRoleMapper.selectList(
            new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, u.getId()));
        if (!userRoles.isEmpty()) {
            var rIds = userRoles.stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
            d.setRoleIds(rIds);
            // 批量查询角色名称，消除 N+1
            var roles = roleMapper.selectBatchIds(rIds);
            d.setRoleNames(roles.stream().map(SysRole::getName).collect(Collectors.toList()));
        } else {
            d.setRoleIds(List.of());
            d.setRoleNames(List.of());
        }
        return d;
    }
}
