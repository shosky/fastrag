package com.fastrag.module.iam.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper; import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fastrag.common.exception.BusinessException; import com.fastrag.common.response.PageResult;
import com.fastrag.module.iam.entity.*; import com.fastrag.module.iam.mapper.*;
import com.fastrag.module.iam.model.*; import com.fastrag.module.iam.service.PersonnelService;
import lombok.RequiredArgsConstructor; import org.springframework.security.crypto.password.PasswordEncoder; import org.springframework.stereotype.Service;
import java.util.*; import java.util.stream.Collectors;
@Service @RequiredArgsConstructor
public class PersonnelServiceImpl implements PersonnelService {
    private final SysUserMapper userMapper; private final SysRoleMapper roleMapper; private final SysOrgMapper orgMapper; private final PasswordEncoder pe;
    @Override public PageResult<PersonnelDto> listPersonnel(int page,int pageSize,String keyword) {
        var w=new LambdaQueryWrapper<SysUser>(); if(keyword!=null&&!keyword.isBlank()) w.like(SysUser::getUsername,keyword).or().like(SysUser::getRealName,keyword);
        w.orderByDesc(SysUser::getCreatedAt); var r=userMapper.selectPage(new Page<>(page,pageSize),w);
        return PageResult.of(r.getRecords().stream().map(this::toDto).collect(Collectors.toList()),r.getTotal(),page,pageSize);
    }
    @Override public PersonnelDto createPersonnel(PersonnelCreateRequest req) {
        if(userMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername,req.getUsername()))!=null) throw BusinessException.badRequest("用户名已存在");
        var u=new SysUser(); u.setUsername(req.getUsername()); u.setRealName(req.getRealName()); u.setPhone(req.getPhone()); u.setEmail(req.getEmail());
        u.setPasswordHash(pe.encode(req.getPassword())); u.setRoleId(req.getRoleId()); u.setOrgId(req.getOrgId()); u.setStatus("enabled"); userMapper.insert(u); return toDto(u);
    }
    @Override public PersonnelDto updatePersonnel(String id,PersonnelCreateRequest req) {
        var u=userMapper.selectById(id); if(u==null)throw BusinessException.notFound("用户不存在");
        u.setRealName(req.getRealName()); u.setPhone(req.getPhone()); u.setEmail(req.getEmail());
        if(req.getPassword()!=null&&!req.getPassword().isBlank()) u.setPasswordHash(pe.encode(req.getPassword()));
        userMapper.updateById(u); return toDto(u);
    }
    @Override public void assignRole(String userId,String roleId) { var u=userMapper.selectById(userId); if(u!=null){u.setRoleId(roleId);userMapper.updateById(u);} }
    @Override public PersonnelDto findByUsername(String username) { var u=userMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername,username)); return u!=null?toDto(u):null; }
    private PersonnelDto toDto(SysUser u) {
        var d=new PersonnelDto(); d.setId(u.getId()); d.setUsername(u.getUsername()); d.setRealName(u.getRealName());
        d.setPhone(u.getPhone()); d.setEmail(u.getEmail()); d.setStatus(u.getStatus()); d.setRoleId(u.getRoleId()); d.setCreatedAt(u.getCreatedAt());
        if(u.getRoleId()!=null){var r=roleMapper.selectById(u.getRoleId());d.setRoleName(r!=null?r.getName():null);}
        if(u.getOrgId()!=null){var o=orgMapper.selectById(u.getOrgId());d.setOrgName(o!=null?o.getName():null);}
        return d;
    }
}
