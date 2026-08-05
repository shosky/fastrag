package com.fastrag.security.filter;

import com.fastrag.common.service.ApiTokenValidator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * API Token 认证过滤器。
 * <p>
 * 处理以 {@code frag_} 前缀开头的 Bearer Token（区别于 JWT）。
 * 当 Authorization 头携带 frag_ 开头的 token 时，委托给
 * {@link ApiTokenValidator} 验证并设置认证上下文。
 * <p>
 * 该过滤器在 {@link JwtAuthFilter} 之前执行，
 * 如果 token 不是 frag_ 开头则跳过，交给 JWT 过滤器处理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiTokenAuthFilter extends OncePerRequestFilter {

    private static final String TOKEN_PREFIX = "frag_";

    @Autowired(required = false)
    private ApiTokenValidator apiTokenValidator;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // 如果已经存在认证（JWT 已处理），跳过
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            chain.doFilter(request, response);
            return;
        }

        // 如果 ApiTokenValidator 未注入（iam 模块未引入），跳过
        if (apiTokenValidator == null) {
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (token.startsWith(TOKEN_PREFIX)) {
                try {
                    String tokenId = apiTokenValidator.validateAndGetTokenId(token);
                    if (tokenId != null) {
                        // 认证成功：平台级全局 Token，创建一个以 tokenId 为 principal、权限为 kb:manage 的认证对象
                        LoginUser apiUser = LoginUser.builder()
                                .userId("api-token:" + tokenId)
                                .username("api-token")
                                .roles(List.of("api_token"))
                                .permissions(List.of("kb:manage"))
                                .build();
                        UsernamePasswordAuthenticationToken auth =
                                new UsernamePasswordAuthenticationToken(
                                        apiUser, null,
                                        List.of(new SimpleGrantedAuthority("kb:manage"))
                                );
                        SecurityContextHolder.getContext().setAuthentication(auth);
                        log.debug("API Token authenticated for tokenId={}", tokenId);
                    } else {
                        log.debug("API Token validation failed: token not found or expired");
                    }
                } catch (Exception e) {
                    log.warn("API Token validation error: {}", e.getMessage());
                }
            }
        }
        chain.doFilter(request, response);
    }
}
