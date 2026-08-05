package com.fastrag.module.iam.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.common.exception.BusinessException;
import com.fastrag.infra.mail.EmailService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fastrag.module.iam.config.WechatMiniAppProperties;
import com.fastrag.module.iam.entity.*; import com.fastrag.module.iam.mapper.*;
import com.fastrag.module.iam.model.*; import com.fastrag.module.iam.service.AuthService;
import com.fastrag.security.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service @RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final SysUserMapper userMapper; private final SysRoleMapper roleMapper;
    private final SysRolePermissionMapper rpMapper; private final SysUserRoleMapper userRoleMapper;
    private final EmailVerificationMapper emailVerificationMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil; private final StringRedisTemplate redisTemplate;
    private final EmailService emailService;
    private final WechatMiniAppProperties wechatProps;
    private final ObjectMapper objectMapper;

    @Override public LoginResponse login(LoginRequest req) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, req.getUsername()));
        if (user == null) throw BusinessException.badRequest("用户名或密码错误");
        if ("disabled".equals(user.getStatus())) throw BusinessException.forbidden("账号已被禁用");
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) throw BusinessException.badRequest("用户名或密码错误");

        List<String> roleKeys = loadRoleKeys(user.getId());
        List<String> perms = loadPermsByUser(user.getId());

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getOrgId(), roleKeys, perms);
        var ui = LoginResponse.UserInfoDto.builder().id(user.getId()).username(user.getUsername())
            .realName(user.getRealName()).phone(user.getPhone()).email(user.getEmail())
            .orgId(user.getOrgId())
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
            .orgId(u.getOrgId())
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

    // ======================== 微信小程序登录 ========================

    private static final String WECHAT_JSCODE_URL = "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code";
    private static final String WECHAT_QRCODE_URL = "https://api.weixin.qq.com/wxa/getwxacodeunlimit?access_token=%s";
    private static final String WECHAT_TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=%s&secret=%s";
    private static final String QR_SCENE_PREFIX = "wechat:qr:scene:";
    private static final String WECHAT_ACCESS_TOKEN_KEY = "wechat:access_token";
    private static final int QR_SCENE_EXPIRE_SECONDS = 300; // 5 分钟

    @Override
    public LoginResponse wechatLogin(WechatLoginRequest req) {
        String openid = code2Openid(req.getCode());
        if (openid == null) {
            throw BusinessException.badRequest("微信登录失败，请重试");
        }

        // 查找已绑定该 openid 的用户
        SysUser user = userMapper.selectOne(
            new LambdaQueryWrapper<SysUser>().eq(SysUser::getOpenid, openid));

        if (user != null) {
            // 已有用户，直接登录
            if ("disabled".equals(user.getStatus())) {
                throw BusinessException.forbidden("账号已被禁用");
            }
            // 如果小程序传了昵称，更新
            if (req.getNickName() != null && !req.getNickName().isBlank()) {
                user.setRealName(req.getNickName());
                userMapper.updateById(user);
            }
            return buildLoginResponse(user);
        }

        // 新用户，自动注册
        SysRole defaultRole = roleMapper.selectOne(
            new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleKey, "kb_user"));
        String defaultRoleId = defaultRole != null ? defaultRole.getId() : null;

        String username = "wx_" + openid.substring(0, Math.min(8, openid.length()));
        // 确保用户名唯一
        Long count = userMapper.selectCount(
            new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (count > 0) {
            username = username + "_" + UUID.randomUUID().toString().substring(0, 4);
        }

        SysUser newUser = new SysUser();
        newUser.setUsername(username);
        newUser.setRealName(req.getNickName() != null ? req.getNickName() : "微信用户");
        newUser.setOpenid(openid);
        newUser.setRoleId(defaultRoleId);
        newUser.setStatus("enabled");
        newUser.setStorageQuota(10737418240L);
        newUser.setStorageUsed(0L);
        userMapper.insert(newUser);

        // 分配默认角色
        if (defaultRoleId != null) {
            var ur = new SysUserRole();
            ur.setUserId(newUser.getId());
            ur.setRoleId(defaultRoleId);
            userRoleMapper.insert(ur);
        }

        log.info("[Auth] WeChat auto-registered user: {}", newUser.getUsername());
        return buildLoginResponse(newUser);
    }

    @Override
    public WechatQrScene generateQrScene() {
        String scene = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        redisTemplate.opsForValue().set(QR_SCENE_PREFIX + scene, "", QR_SCENE_EXPIRE_SECONDS, TimeUnit.SECONDS);

        String qrImageBase64 = null;
        try {
            qrImageBase64 = generateMiniAppQrCode(scene);
        } catch (Exception e) {
            log.warn("[Auth] Failed to generate WeChat mini app QR code, will use fallback", e);
        }

        return WechatQrScene.builder().scene(scene).expiresIn(QR_SCENE_EXPIRE_SECONDS)
            .qrImageBase64(qrImageBase64).build();
    }

    @Override
    public String pollQrLoginStatus(String scene) {
        String key = QR_SCENE_PREFIX + scene;
        String token = redisTemplate.opsForValue().get(key);
        if (token == null) {
            // key 不存在 = 已过期
            return "expired";
        }
        if (token.isEmpty()) {
            // 空值 = 还没扫码
            return null;
        }
        // 非空 = 小程序已确认登录，返回 token
        return token;
    }

    /** 小程序扫码确认登录：小程序端调用，将 token 写入 Redis 等前端轮询取走 */
    public void confirmQrLogin(String scene, LoginResponse loginResponse) {
        String key = QR_SCENE_PREFIX + scene;
        String existing = redisTemplate.opsForValue().get(key);
        if (existing == null) {
            throw BusinessException.badRequest("二维码已过期，请刷新");
        }
        // 将 token 写入 Redis，前端轮询会拿到
        redisTemplate.opsForValue().set(key, loginResponse.getToken(), 60, TimeUnit.SECONDS);
    }

    /** 获取微信 access_token（带 Redis 缓存） */
    private String getAccessToken() {
        // 先从缓存读
        String cached = redisTemplate.opsForValue().get(WECHAT_ACCESS_TOKEN_KEY);
        if (cached != null && !cached.isBlank()) return cached;

        String url = String.format(WECHAT_TOKEN_URL, wechatProps.getAppId(), wechatProps.getAppSecret());
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = objectMapper.readTree(response.body());
            String token = json.path("access_token").asText(null);
            if (token != null && !token.isBlank()) {
                int expiresIn = json.path("expires_in").asInt(7200);
                // 提前 5 分钟过期
                redisTemplate.opsForValue().set(WECHAT_ACCESS_TOKEN_KEY, token, expiresIn - 300, TimeUnit.SECONDS);
                return token;
            }
            log.warn("[Auth] Failed to get WeChat access_token: {}", response.body());
            return null;
        } catch (Exception e) {
            log.error("[Auth] Error getting WeChat access_token", e);
            return null;
        }
    }

    /** 调用微信 getwxacodeunlimit 生成小程序码，返回 base64 */
    private String generateMiniAppQrCode(String scene) throws Exception {
        String accessToken = getAccessToken();
        if (accessToken == null) return null;

        String url = String.format(WECHAT_QRCODE_URL, accessToken);
        String body = objectMapper.writeValueAsString(Map.of(
            "scene", scene,
            "page", "pages/login/index",
            "width", 280,
            "auto_color", false,
            "is_hyaline", true
        ));

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

        // 微信返回图片时 Content-Type 是 image/*；出错时是 application/json
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        if (contentType.startsWith("image/")) {
            return Base64.getEncoder().encodeToString(response.body());
        }
        // 返回了 JSON 错误
        log.warn("[Auth] WeChat QR code API error: {}", new String(response.body()));
        return null;
    }

    /** 调用微信 jscode2session 接口，用 code 换取 openid */
    private String code2Openid(String code) {
        String url = String.format(WECHAT_JSCODE_URL, wechatProps.getAppId(), wechatProps.getAppSecret(), code);
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = objectMapper.readTree(response.body());
            String openid = json.path("openid").asText(null);
            if (openid == null || openid.isBlank()) {
                log.warn("[Auth] WeChat jscode2session failed: {}", response.body());
                return null;
            }
            return openid;
        } catch (Exception e) {
            log.error("[Auth] WeChat jscode2session error", e);
            return null;
        }
    }

    /** 构建 LoginResponse（复用角色/权限加载逻辑） */
    private LoginResponse buildLoginResponse(SysUser user) {
        List<String> roleKeys = loadRoleKeys(user.getId());
        List<String> perms = loadPermsByUser(user.getId());
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getOrgId(), roleKeys, perms);
        var ui = LoginResponse.UserInfoDto.builder()
            .id(user.getId()).username(user.getUsername())
            .realName(user.getRealName()).phone(user.getPhone()).email(user.getEmail())
            .orgId(user.getOrgId())
            .roles(roleKeys).permissions(perms).build();
        return LoginResponse.builder().token(token).userInfo(ui).build();
    }
}
