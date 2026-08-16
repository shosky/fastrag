package com.fastrag.security.filter;

/**
 * API Token 认证过滤器，处理以 {@code frag_} 前缀开头的 Bearer Token 认证。
 *
 * <p>核心职责：识别并验证 API Token（区别于 JWT Token），为程序化调用（如第三方集成、系统间调用）
 * 提供认证能力。
 *
 * <p>过滤链位置：在 {@link JwtAuthFilter} 之前、{@code UsernamePasswordAuthenticationFilter} 之前执行。
 * 如果请求已被其他过滤器完成认证，或 Token 不是 frag_ 前缀，则跳过交由后续过滤器处理。
 *
 * <p>处理逻辑：
 * <ol>
 *   <li>检查 SecurityContext 中是否已存在认证信息，存在则跳过</li>
 *   <li>检查 {@link com.fastrag.common.service.ApiTokenValidator} 是否可用（iam 模块引入时才注入），
 *       不可用则跳过</li>
 *   <li>从 Authorization 头提取 Bearer Token，判断是否以 "frag_" 开头</li>
 *   <li>委托 ApiTokenValidator 验证 Token 有效性并获取 tokenId</li>
 *   <li>验证成功后创建平台级全局认证对象，principal 为 "api-token:{tokenId}"，
 *       授予 "kb:manage" 权限，写入 SecurityContext</li>
 * </ol>
 *
 * <p>与其他模块的交互：依赖 fastrag-iam 模块的 {@link com.fastrag.common.service.ApiTokenValidator}
 * 进行 Token 验证（通过 {@code @Autowired(required = false)} 可选注入，iam 模块未引入时自动禁用）。
 */
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
