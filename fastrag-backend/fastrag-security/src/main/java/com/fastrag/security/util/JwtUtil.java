package com.fastrag.security.util;

/**
 * JWT（JSON Web Token）工具类，提供 Token 的生成、解析和有效性验证能力。
 *
 * <p>核心职责：封装基于 JJWT 库的 JWT Token 全生命周期操作，为系统的认证机制提供底层支持。
 *
 * <p>关键实现逻辑：
 * <ul>
 *   <li>使用 HMAC-SHA 算法签名，密钥来自配置项 {@code jwt.secret}
 *       （默认值仅用于开发环境，生产环境必须更换为足够长度的随机密钥）</li>
 *   <li>Token 有效期通过配置项 {@code jwt.expiration} 控制，默认 86400000ms（24 小时）</li>
 *   <li>Token Payload 包含：subject(userId)、username、orgId、roles、permissions</li>
 *   <li>验证逻辑包括签名校验和过期时间检查</li>
 * </ul>
 *
 * <p>提供的工具方法：
 * <ul>
 *   <li>{@code generateToken} — 生成 JWT Token（支持带/不带 orgId 两个重载）</li>
 *   <li>{@code parseToken} — 解析 Token 并返回 Claims 对象</li>
 *   <li>{@code getUserId} — 从 Token 中提取用户 ID</li>
 *   <li>{@code isTokenValid} — 验证 Token 是否有效（签名正确且未过期）</li>
 * </ul>
 *
 * <p>与其他模块的交互：被 {@link com.fastrag.security.filter.JwtAuthFilter} 调用进行 Token 验证；
 * 被认证服务（fastrag-iam 模块）调用生成登录 Token。
 */

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Component
public class JwtUtil {
    @Value("${jwt.secret:FastRAGDefaultSecretKey2024!PleaseChangeInProductionAndMakeItAtLeast32BytesLong!}")
    private String secret;

    @Value("${jwt.expiration:86400000}")
    private long expiration;

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String userId, String username, List<String> roles, List<String> permissions) {
        return generateToken(userId, username, null, roles, permissions);
    }

    public String generateToken(String userId, String username, String orgId, List<String> roles, List<String> permissions) {
        return Jwts.builder()
                .subject(userId)
                .claim("username", username)
                .claim("orgId", orgId == null ? "" : orgId)
                .claim("roles", roles)
                .claim("permissions", permissions)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getKey())
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String getUserId(String token) {
        return parseToken(token).getSubject();
    }

    public boolean isTokenValid(String token) {
        try {
            Claims c = parseToken(token);
            return !c.getExpiration().before(new Date());
        } catch (JwtException e) {
            return false;
        }
    }
}
