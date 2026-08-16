package com.fastrag.module.iam.config;

/**
 * 微信小程序配置属性类。
 *
 * <p>通过 Spring Boot 的 {@link ConfigurationProperties} 机制，从配置文件中读取
 * 以 {@code wechat.miniapp} 为前缀的配置项，为微信小程序登录、扫码等功能提供
 * 必要的 appId 和 appSecret 参数。
 *
 * <p>核心职责：
 * <ul>
 *   <li>绑定 {@code wechat.miniapp.app-id} —— 微信小程序的唯一标识</li>
 *   <li>绑定 {@code wechat.miniapp.app-secret} —— 微信小程序的接口调用密钥</li>
 * </ul>
 *
 * <p>与 {@code AuthServiceImpl} 中的微信登录逻辑配合使用，用于调用微信
 * code2Session 接口换取用户 openId / unionId。
 */
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "wechat.miniapp")
public class WechatMiniAppProperties {
    private String appId;
    private String appSecret;
}
