package com.fastrag.module.iam.service;
import com.fastrag.module.iam.model.*;
public interface AuthService {
    LoginResponse login(LoginRequest req);
    LoginResponse.UserInfoDto getUserInfo(String userId);
    void logout(String token);
    void sendCode(SendCodeRequest req);
    void register(RegisterRequest req);
    void resetPassword(ResetPasswordRequest req);

    /** 微信小程序登录：小程序端调用，用 code 换取 openid，注册或登录用户 */
    LoginResponse wechatLogin(WechatLoginRequest req);

    /** 生成小程序码场景值（供前端生成小程序码使用） */
    WechatQrScene generateQrScene();

    /** 前端轮询扫码登录状态：返回 token（已扫码确认）或 null（等待中） */
    String pollQrLoginStatus(String scene);

    /** 小程序扫码确认登录：小程序端调用，将 token 写入 Redis 等前端轮询取走 */
    void confirmQrLogin(String scene, LoginResponse loginResponse);
}
