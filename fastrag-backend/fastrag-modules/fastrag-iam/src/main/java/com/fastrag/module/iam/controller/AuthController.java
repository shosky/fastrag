package com.fastrag.module.iam.controller;

import com.fastrag.common.response.ApiResponse;
import com.fastrag.common.util.IpUtil;
import com.fastrag.common.util.UserAgentUtil;
import com.fastrag.module.iam.model.*;
import com.fastrag.module.iam.service.AuthService;
import com.fastrag.module.operation.service.SysLoginLogService;
import com.fastrag.security.util.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SysLoginLogService loginLogService;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest r, HttpServletRequest request) {
        String ip = IpUtil.getClientIp(request);
        UserAgentUtil.UaInfo ua = UserAgentUtil.parse(request.getHeader("User-Agent"));
        try {
            LoginResponse response = authService.login(r);
            // 登录成功日志
            LoginResponse.UserInfoDto user = response.getUserInfo();
            loginLogService.addLoginLog(
                    user != null ? user.getId() : null,
                    user != null ? user.getUsername() : r.getUsername(),
                    ip, ua.device(), ua.os(), ua.browser(),
                    "success", null);
            return ApiResponse.success(response);
        } catch (Exception e) {
            // 登录失败日志
            loginLogService.addLoginLog(
                    null, r.getUsername(), ip, ua.device(), ua.os(), ua.browser(),
                    "failed", e.getMessage());
            log.warn("[Auth] Login failed for {}: {}", r.getUsername(), e.getMessage());
            throw e;
        }
    }

    @GetMapping("/userinfo")
    public ApiResponse<LoginResponse.UserInfoDto> info() {
        return ApiResponse.success(authService.getUserInfo(SecurityUtil.getCurrentUserId()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            authService.logout(token.substring(7));
        }
        // 登出日志
        String ip = IpUtil.getClientIp(request);
        try {
            String userId = SecurityUtil.getCurrentUserId();
            String username = SecurityUtil.getCurrentUser().getUsername();
            UserAgentUtil.UaInfo ua = UserAgentUtil.parse(request.getHeader("User-Agent"));
            loginLogService.addLoginLog(userId, username, ip, ua.device(), ua.os(), ua.browser(), "logout", null);
        } catch (Exception e) {
            log.warn("[Auth] Failed to record logout log: {}", e.getMessage());
        }
        return ApiResponse.success();
    }

    @PostMapping("/send-code")
    public ApiResponse<Void> sendCode(@Valid @RequestBody SendCodeRequest r) {
        authService.sendCode(r);
        return ApiResponse.success();
    }

    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest r, HttpServletRequest request) {
        authService.register(r);
        // 注册日志
        String ip = IpUtil.getClientIp(request);
        UserAgentUtil.UaInfo ua = UserAgentUtil.parse(request.getHeader("User-Agent"));
        loginLogService.addLoginLog(null, r.getUsername(), ip, ua.device(), ua.os(), ua.browser(), "register", null);
        return ApiResponse.success();
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest r) {
        authService.resetPassword(r);
        return ApiResponse.success();
    }

    // ======================== 微信小程序登录 ========================

    /** 生成扫码登录场景值（前端调，用于拼小程序码 URL） */
    @GetMapping("/wechat/qr-scene")
    public ApiResponse<WechatQrScene> generateQrScene() {
        return ApiResponse.success(authService.generateQrScene());
    }

    /** 前端轮询扫码状态：返回 { status: 'waiting'|'expired'|'scanned', token? } */
    @GetMapping("/wechat/qr-status")
    public ApiResponse<?> pollQrStatus(@RequestParam String scene) {
        String result = authService.pollQrLoginStatus(scene);
        if ("expired".equals(result)) {
            return ApiResponse.success(Map.of("status", "expired"));
        }
        if (result == null) {
            return ApiResponse.success(Map.of("status", "waiting"));
        }
        return ApiResponse.success(Map.of("status", "scanned", "token", result));
    }

    /** 小程序端微信登录（小程序 code 换 openid，自动注册或登录） */
    @PostMapping("/wechat/login")
    public ApiResponse<LoginResponse> wechatLogin(@Valid @RequestBody WechatLoginRequest r, HttpServletRequest request) {
        String ip = IpUtil.getClientIp(request);
        try {
            LoginResponse response = authService.wechatLogin(r);
            LoginResponse.UserInfoDto user = response.getUserInfo();
            loginLogService.addLoginLog(
                    user != null ? user.getId() : null,
                    user != null ? user.getUsername() : "wechat_user",
                    ip, "小程序", "WeChat", "WeChat",
                    "success", null);
            return ApiResponse.success(response);
        } catch (Exception e) {
            loginLogService.addLoginLog(
                    null, "wechat_user", ip, "小程序", "WeChat", "WeChat",
                    "failed", e.getMessage());
            throw e;
        }
    }

    /** 小程序端扫码确认登录：小程序拿到 scene + code 后调用 */
    @PostMapping("/wechat/qr-confirm")
    public ApiResponse<LoginResponse> wechatQrConfirm(@Valid @RequestBody WechatQrConfirmRequest r, HttpServletRequest request) {
        String ip = IpUtil.getClientIp(request);
        // 将 WechatQrConfirmRequest 转换为 WechatLoginRequest
        WechatLoginRequest loginReq = new WechatLoginRequest();
        loginReq.setCode(r.getCode());
        loginReq.setNickName(r.getNickName());
        LoginResponse response = authService.wechatLogin(loginReq);
        authService.confirmQrLogin(r.getScene(), response);
        // 登录日志
        try {
            LoginResponse.UserInfoDto user = response.getUserInfo();
            loginLogService.addLoginLog(
                    user != null ? user.getId() : null,
                    user != null ? user.getUsername() : "wechat_user",
                    ip, "小程序", "WeChat", "WeChat",
                    "success", null);
        } catch (Exception ignored) {}
        return ApiResponse.success(response);
    }
}
