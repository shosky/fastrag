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
    private final SysRolePermissionMapper rpMapper; private final EmailVerificationMapper emailVerificationMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil; private final StringRedisTemplate redisTemplate;
    private final EmailService emailService;

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

    @Override
    public void sendCode(SendCodeRequest req) {
        String email = req.getEmail().trim().toLowerCase();
        String purpose = req.getPurpose();

        // 频率限制：同邮箱60秒内不可重复发送
        String rateLimitKey = "email:rate:" + email + ":" + purpose;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(rateLimitKey))) {
            throw BusinessException.badRequest("验证码发送过于频繁，请60秒后重试");
        }

        // 查询默认注册角色
        SysRole defaultRole = roleMapper.selectOne(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleKey, "kb_user"));
        String defaultRoleId = defaultRole != null ? defaultRole.getId() : null;

        // 生成6位随机验证码
        String code = String.format("%06d", new Random().nextInt(1000000));

        // 保存验证码记录
        EmailVerification record = new EmailVerification();
        record.setEmail(email);
        record.setCode(code);
        record.setPurpose(purpose);
        record.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        record.setUsed(false);
        emailVerificationMapper.insert(record);

        // 设置60秒频率限制
        redisTemplate.opsForValue().set(rateLimitKey, "1", 60, TimeUnit.SECONDS);

        // 异步发送邮件
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

        // 验证码校验
        verifyAndConsumeCode(email, req.getCode(), "register");

        // 检查用户名唯一性
        Long usernameCount = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (usernameCount > 0) {
            throw BusinessException.badRequest("用户名已被注册");
        }

        // 检查邮箱唯一性
        Long emailCount = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getEmail, email));
        if (emailCount > 0) {
            throw BusinessException.badRequest("邮箱已被注册");
        }

        // 查询默认注册角色
        SysRole defaultRole = roleMapper.selectOne(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleKey, "kb_user"));
        String defaultRoleId = defaultRole != null ? defaultRole.getId() : null;

        // 创建用户
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setRoleId(defaultRoleId);
        user.setStatus("enabled");
        user.setStorageQuota(10737418240L); // 10GB
        user.setStorageUsed(0L);
        userMapper.insert(user);
    }

    @Override
    public void resetPassword(ResetPasswordRequest req) {
        String email = req.getEmail().trim().toLowerCase();

        // 验证码校验
        verifyAndConsumeCode(email, req.getCode(), "reset");

        // 根据邮箱查找用户
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getEmail, email));
        if (user == null) {
            throw BusinessException.badRequest("该邮箱未注册");
        }

        // 更新密码
        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        userMapper.updateById(user);
    }

    /**
     * 校验验证码并标记为已使用
     */
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

        if (record == null) {
            throw BusinessException.badRequest("验证码无效或已过期");
        }

        // 标记为已使用
        record.setUsed(true);
        emailVerificationMapper.updateById(record);
    }

    private List<String> loadPerms(String roleId) {
        if(roleId==null) return List.of();
        SysRole r = roleMapper.selectById(roleId); if(r!=null&&"super_admin".equals(r.getRoleKey())) return List.of("*");
        return rpMapper.selectList(new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId,roleId)).stream().map(SysRolePermission::getPermissionKey).collect(Collectors.toList());
    }
}
