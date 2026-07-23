package com.fastrag.module.iam.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.common.exception.BusinessException;
import com.fastrag.infra.mail.EmailService;
import com.fastrag.module.iam.entity.*; import com.fastrag.module.iam.mapper.*;
import com.fastrag.module.iam.model.*; import com.fastrag.module.iam.service.AuthService;
import com.fastrag.security.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final SysUserMapper userMapper; private final SysRoleMapper roleMapper;
    private final SysRolePermissionMapper rpMapper; private final SysUserRoleMapper userRoleMapper;
    private final EmailVerificationMapper emailVerificationMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil; private final StringRedisTemplate redisTemplate;
    private final EmailService emailService;

    @Override public LoginResponse login(LoginRequest req) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, req.getUsername()));
        if (user == null) throw BusinessException.badRequest("用户名或密码错误");
        if ("disabled".equals(user.getStatus())) throw BusinessException.forbidden("账号已被禁用");
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) throw BusinessException.badRequest("用户名或密码错误");

        List<String> roleKeys = loadRoleKeys(user.getId());
        List<String> perms = loadPermsByUser(user.getId());

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), roleKeys, perms);
        var ui = LoginResponse.UserInfoDto.builder().id(user.getId()).username(user.getUsername())
            .realName(user.getRealName()).phone(user.getPhone()).email(user.getEmail())
            .roles(roleKeys).permissions(perms).build();
        return LoginResponse.builder().token(token).userInfo(ui).build();
    }

    @Override public LoginResponse.UserInfoDto getUserInfo(String userId) {
        SysUser u = userMapper.selectById(userId);
        if (u == null) throw BusinessException.notFound("用户不存在");
        List<String> roleKeys = loadRoleKeys(u.getId());
        List<String> perms = loadPermsByUser(u.getId());
        return LoginResponse.UserInfoDto.builder().id(u.getId()).username(u.getUsername())
            .realName(u.getRealName()).phone(u.getPhone()).email(u.getEmail())
            .roles(roleKeys).permissions(perms).build();
    }

    @Override public void logout(String token) {
        if (token != null) redisTemplate.opsForValue().set("jwt:blacklist:" + token, "1", 24, TimeUnit.HOURS);
    }

    @Override
    public void sendCode(SendCodeRequest req) {
        String email = req.getEmail().trim().toLowerCase();
        String purpose = req.getPurpose();

        String rateLimitKey = "email:rate:" + email + ":" + purpose;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(rateLimitKey))) {
            throw BusinessException.badRequest("验证码发送过于频繁，请60秒后重试");
        }

        SysRole defaultRole = roleMapper.selectOne(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleKey, "kb_user"));

        String code = String.format("%06d", new Random().nextInt(1000000));

        EmailVerification record = new EmailVerification();
        record.setEmail(email); record.setCode(code); record.setPurpose(purpose);
        record.setExpiresAt(LocalDateTime.now().plusMinutes(5)); record.setUsed(false);
        emailVerificationMapper.insert(record);

        redisTemplate.opsForValue().set(rateLimitKey, "1", 60, TimeUnit.SECONDS);

        if ("register".equals(purpose)) {
            emailService.sendRegisterCode(email, code);
        } else if ("reset".equals(purpose)) {
            emailService.sendResetPasswordCode(email, code);
        }
    }

    @Override
    public void register(RegisterRequest req) {
        String email = req.getEmail().trim().toLowerCase();
        String username = req.getUsername().trim();

        verifyAndConsumeCode(email, req.getCode(), "register");

        Long usernameCount = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (usernameCount > 0) throw BusinessException.badRequest("用户名已被注册");

        Long emailCount = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getEmail, email));
        if (emailCount > 0) throw BusinessException.badRequest("邮箱已被注册");

        SysRole defaultRole = roleMapper.selectOne(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleKey, "kb_user"));
        String defaultRoleId = defaultRole != null ? defaultRole.getId() : null;

        SysUser user = new SysUser();
        user.setUsername(username); user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setRoleId(defaultRoleId); user.setStatus("enabled");
        user.setStorageQuota(10737418240L); user.setStorageUsed(0L);
        userMapper.insert(user);

        // 注册时给用户分配默认角色
        if (defaultRoleId != null) {
            var ur = new SysUserRole();
            ur.setUserId(user.getId()); ur.setRoleId(defaultRoleId);
            userRoleMapper.insert(ur);
        }
    }

    @Override
    public void resetPassword(ResetPasswordRequest req) {
        String email = req.getEmail().trim().toLowerCase();
        verifyAndConsumeCode(email, req.getCode(), "reset");

        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getEmail, email));
        if (user == null) throw BusinessException.badRequest("该邮箱未注册");

        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        userMapper.updateById(user);
    }

    private void verifyAndConsumeCode(String email, String code, String purpose) {
        EmailVerification record = emailVerificationMapper.selectOne(
            new LambdaQueryWrapper<EmailVerification>()
                .eq(EmailVerification::getEmail, email)
                .eq(EmailVerification::getCode, code)
                .eq(EmailVerification::getPurpose, purpose)
                .eq(EmailVerification::getUsed, false)
                .gt(EmailVerification::getExpiresAt, LocalDateTime.now())
                .orderByDesc(EmailVerification::getCreatedAt)
                .last("LIMIT 1"));
        if (record == null) throw BusinessException.badRequest("验证码无效或已过期");
        record.setUsed(true);
        emailVerificationMapper.updateById(record);
    }

    /** 加载用户的所有角色 key（通过 sys_user_role 中间表） */
    private List<String> loadRoleKeys(String userId) {
        var userRoles = userRoleMapper.selectList(
            new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (userRoles.isEmpty()) return List.of();
        var roleIds = userRoles.stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
        var roles = roleMapper.selectBatchIds(roleIds);
        return roles.stream().map(SysRole::getRoleKey).collect(Collectors.toList());
    }

    /** 加载用户的所有权限（合并所有角色的权限，去重） */
    private List<String> loadPermsByUser(String userId) {
        var userRoles = userRoleMapper.selectList(
            new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (userRoles.isEmpty()) return List.of();
        // 检查是否包含 super_admin
        var roleIds = userRoles.stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
        var roles = roleMapper.selectBatchIds(roleIds);
        boolean isSuperAdmin = roles.stream().anyMatch(r -> "super_admin".equals(r.getRoleKey()));
        if (isSuperAdmin) return List.of("*");
        // 合并所有角色的权限并去重
        Set<String> permSet = new LinkedHashSet<>();
        for (String roleId : roleIds) {
            permSet.addAll(rpMapper.selectList(
                new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId, roleId))
                .stream().map(SysRolePermission::getPermissionKey).collect(Collectors.toList()));
        }
        return new ArrayList<>(permSet);
    }
}
