package com.fastrag.security.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * OnlyOffice Document Server JWT 工具类。
 *
 * <p>OnlyOffice Document Server（7.x）服务端使用 NodeJS 的 {@code jsonwebtoken} 库做
 * 验证（不是自定义 2 段格式）。其验证方式是：
 * <pre>
 *   jwt.verify(token, secret)  // 标准 JWS 校验
 * </pre>
 *
 * <p>token 格式 = 标准 3 段 JWS：
 * <pre>
 *   &lt;base64url(header)&gt;.&lt;base64url(payload)&gt;.&lt;base64url(signature)&gt;
 *   header    = {"typ":"JWT","alg":"HS256"}
 *   payload   = FastRAG 组装的 OO editor config（Map 序列化为 JSON 后作为 subject claim）
 *   signature = HMAC-SHA256(header_b64 + "." + payload_b64, secret)
 * </pre>
 *
 * <p>实测证据（OO 服务端日志）：
 * <pre>
 *   checkJwt error: name = JsonWebTokenError message = jwt malformed
 * </pre>
 * — 这是 jsonwebtoken 库报的错,表示 token 不是标准 JWS 格式（之前我们用的 2 段格式）。
 *
 * <p>用途：签名 OO 编辑器 config（{@code config.token} 字段）、document.url 的查询 token、
 * callback body 的 token。服务端和客户端共用同一密钥（与 Document Server
 * {@code JWT_SECRET} 环境变量保持一致）。
 */
@Slf4j
@Component
public class OnlyOfficeJwtUtil {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${onlyoffice.jwt-secret:fastrag-onlyoffice-dev-secret-change-me}")
    private String secret;

    /** OnlyOffice 时钟漂移容忍（与服务端 jsonwebtoken 默认一致） */
    private static final long LEEWAY_SECONDS = 300L;

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 签发 OnlyOffice 兼容的标准 JWS token。
     *
     * @param payload   POJO/Map，序列化为 JSON 后作为 JWT subject claim
     * @param ttlMillis 有效期（毫秒），会写入 payload 的 {@code exp} 字段
     * @return 标准 3 段 JWT 字符串
     */
    public String signPayload(Object payload, long ttlMillis) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            return Jwts.builder()
                    .header().add("typ", "JWT").and()
                    .subject(json)
                    .issuedAt(new Date())
                    .expiration(new Date(System.currentTimeMillis() + ttlMillis))
                    .signWith(getKey(), Jwts.SIG.HS256)
                    .compact();
        } catch (Exception e) {
            throw new OnlyOfficeTokenException("Failed to sign OnlyOffice payload", e);
        }
    }

    /**
     * 签发 {@code document.url} 用的查询参数 token（OO 服务端拉取原始文件时附
     * {@code Authorization: Bearer}）。scope 字段用于服务端识别用途。
     */
    public String signRawFileToken(String kbId, String fileId, long ttlSeconds) {
        Map<String, Object> claims = new java.util.HashMap<>();
        claims.put("kbId", kbId);
        claims.put("fileId", fileId);
        claims.put("scope", "onlyoffice-raw");
        return signPayload(claims, ttlSeconds * 1000L);
    }

    /**
     * 验签 token 并返回 Map 形式的 payload。
     *
     * <p>使用 JJWT 标准 JWS 校验（HS256 + leeway），从 subject claim 解析回 Map。
     */
    public Map<String, Object> verify(String token) {
        try {
            String subject = Jwts.parser()
                    .verifyWith(getKey())
                    .clockSkewSeconds(LEEWAY_SECONDS)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
            if (subject == null || subject.isEmpty()) {
                throw new OnlyOfficeTokenException("Empty OnlyOffice token subject");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(subject, Map.class);
            return result;
        } catch (io.jsonwebtoken.JwtException e) {
            throw new OnlyOfficeTokenException("Invalid OnlyOffice token: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new OnlyOfficeTokenException("Failed to verify OnlyOffice token", e);
        }
    }

    /** OnlyOffice token 校验失败时抛出的异常 */
    public static class OnlyOfficeTokenException extends RuntimeException {
        public OnlyOfficeTokenException(String message) {
            super(message);
        }
        public OnlyOfficeTokenException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
