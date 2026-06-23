package com.fastrag.module.iam.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.common.exception.BusinessException;
import com.fastrag.module.iam.entity.*; import com.fastrag.module.iam.mapper.*;
import com.fastrag.module.iam.model.*; import com.fastrag.module.iam.service.AuthService;
import com.fastrag.security.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.*; import java.util.concurrent.TimeUnit; import java.util.stream.Collectors;
@Service @RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final SysUserMapper userMapper; private final SysRoleMapper roleMapper;
    private final SysRolePermissionMapper rpMapper; private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil; private final StringRedisTemplate redisTemplate;
    @Override public LoginResponse login(LoginRequest req) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername,req.getUsername()));
        if (user==null) throw BusinessException.badRequest("用户名或密码错误");
        if ("disabled".equals(user.getStatus())) throw BusinessException.forbidden("账号已被禁用");
        if (!passwordEncoder.matches(req.getPassword(),user.getPasswordHash())) throw BusinessException.badRequest("用户名或密码错误");
        SysRole role = roleMapper.selectById(user.getRoleId());
        List<String> roleKeys = role!=null?List.of(role.getRoleKey()):List.of();
        List<String> perms = loadPerms(user.getRoleId());
        String token = jwtUtil.generateToken(user.getId(),user.getUsername(),roleKeys,perms);
        var ui = LoginResponse.UserInfoDto.builder().id(user.getId()).username(user.getUsername())
            .realName(user.getRealName()).phone(user.getPhone()).email(user.getEmail()).roles(roleKeys).permissions(perms).build();
        return LoginResponse.builder().token(token).userInfo(ui).build();
    }
    @Override public LoginResponse.UserInfoDto getUserInfo(String userId) {
        SysUser u = userMapper.selectById(userId); if(u==null) throw BusinessException.notFound("用户不存在");
        SysRole r = roleMapper.selectById(u.getRoleId());
        return LoginResponse.UserInfoDto.builder().id(u.getId()).username(u.getUsername()).realName(u.getRealName())
            .phone(u.getPhone()).email(u.getEmail()).roles(r!=null?List.of(r.getRoleKey()):List.of()).permissions(loadPerms(u.getRoleId())).build();
    }
    @Override public void logout(String token) { if(token!=null) redisTemplate.opsForValue().set("jwt:blacklist:"+token,"1",24,TimeUnit.HOURS); }
    private List<String> loadPerms(String roleId) {
        if(roleId==null) return List.of();
        SysRole r = roleMapper.selectById(roleId); if(r!=null&&"super_admin".equals(r.getRoleKey())) return List.of("*");
        return rpMapper.selectList(new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId,roleId)).stream().map(SysRolePermission::getPermissionKey).collect(Collectors.toList());
    }
}
