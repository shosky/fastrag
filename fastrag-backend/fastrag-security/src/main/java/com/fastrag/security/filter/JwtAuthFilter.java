package com.fastrag.security.filter;

/**
 * JWT Bearer Token 认证过滤器，负责从请求头中提取和验证 JWT Token 并建立认证上下文。
 *
 * <p>核心职责：拦截所有 HTTP 请求，从 Authorization 头中提取 JWT Token，
 * 验证有效性后将用户信息写入 Spring Security 上下文。
 *
 * <p>过滤链位置：在 {@link ApiTokenAuthFilter} 之后、{@code UsernamePasswordAuthenticationFilter} 之前执行。
 *
 * <p>处理逻辑：
 * <ol>
 *   <li>从 Authorization 头提取 Bearer Token</li>
 *   <li>检查 Token 是否在 Redis 黑名单中（用户登出时将 Token 加入黑名单，通过
 *       {@code jwt:blacklist:{token}} Key 查询，key 存在则表示已失效）</li>
 *   <li>通过 {@link com.fastrag.security.util.JwtUtil} 解析 Token，提取 userId、username、orgId、roles、permissions</li>
 *   <li>构建 {@link LoginUser} 认证对象和 {@link org.springframework.security.core.Authentication}，
 *       写入 {@code SecurityContextHolder}</li>
 * </ol>
 *
 * <p>特殊处理：通过重写 {@code shouldNotFilterAsyncDispatch()} 返回 true，
 * 跳过 SSE 连接的异步 dispatch 请求的鉴权，避免 response 已 committed 时抛出 AccessDeniedException。
 *
 * <p>与其他模块的交互：依赖 {@link com.fastrag.security.util.JwtUtil} 进行 Token 解析；
 * 依赖 {@link org.springframework.data.redis.core.StringRedisTemplate} 查询 Token 黑名单。
 */

import com.fastrag.security.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        // SSE 的 async dispatch（连接已建立后 Tomcat 的完成回调）不需要重新鉴权，
        // 此时 response 已 committed，重新鉴权会导致 AccessDeniedException
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            // Check blacklist
            if (Boolean.TRUE.equals(redisTemplate.hasKey("jwt:blacklist:" + token))) {
                chain.doFilter(request, response);
                return;
            }
            if (jwtUtil.isTokenValid(token)) {
                Claims claims = jwtUtil.parseToken(token);
                String userId = claims.getSubject();
                String username = claims.get("username", String.class);
                String orgId = claims.get("orgId", String.class);
                @SuppressWarnings("unchecked")
                List<String> roles = claims.get("roles", List.class);
                @SuppressWarnings("unchecked")
                List<String> permissions = claims.get("permissions", List.class);

                LoginUser loginUser = LoginUser.builder()
                        .userId(userId)
                        .username(username)
                        .orgId(orgId)
                        .roles(roles != null ? roles : List.of())
                        .permissions(permissions != null ? permissions : List.of())
                        .build();
                List<SimpleGrantedAuthority> authorities = (permissions != null ? permissions : List.<String>of())
                        .stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList());
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(loginUser, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        chain.doFilter(request, response);
    }
}
