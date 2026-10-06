package com.fastrag.security.filter;

import com.fastrag.common.service.ApiTokenValidator;
import com.fastrag.common.service.TokenAuthInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * QF-3（idd-review 快修）：ApiTokenAuthFilter 按实体 permission 字段授权。
 * read token → 仅授 kb:read；write/空（向后兼容）→ 授 kb:manage；无效 token → 不放行认证。
 */
@ExtendWith(MockitoExtension.class)
class ApiTokenAuthFilterTest {

    @Mock
    private ApiTokenValidator apiTokenValidator;

    private ApiTokenAuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ApiTokenAuthFilter();
        org.springframework.test.util.ReflectionTestUtils
                .setField(filter, "apiTokenValidator", apiTokenValidator);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest requestWithToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private List<String> authoritiesOf(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
    }

    @Test
    void readToken_grantsKbReadOnly_notKbManage() throws Exception {
        when(apiTokenValidator.validateAndGetAuthInfo("frag_read"))
                .thenReturn(TokenAuthInfo.of("token-1", "read"));

        MockHttpServletRequest request = requestWithToken("frag_read");
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(authoritiesOf(auth)).containsExactly("kb:read");
        LoginUser user = (LoginUser) auth.getPrincipal();
        assertThat(user.getPermissions()).containsExactly("kb:read");
    }

    @Test
    void writeToken_grantsKbManage() throws Exception {
        when(apiTokenValidator.validateAndGetAuthInfo("frag_write"))
                .thenReturn(TokenAuthInfo.of("token-2", "write"));

        filter.doFilter(requestWithToken("frag_write"), new MockHttpServletResponse(), new MockFilterChain());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(authoritiesOf(auth)).containsExactly("kb:manage");
    }

    @Test
    void blankPermission_grantsKbManage_forBackwardCompatibility() throws Exception {
        when(apiTokenValidator.validateAndGetAuthInfo("frag_legacy"))
                .thenReturn(TokenAuthInfo.of("token-3", null));

        filter.doFilter(requestWithToken("frag_legacy"), new MockHttpServletResponse(), new MockFilterChain());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(authoritiesOf(auth)).containsExactly("kb:manage");
    }

    @Test
    void invalidToken_doesNotAuthenticate() throws Exception {
        when(apiTokenValidator.validateAndGetAuthInfo("frag_bad")).thenReturn(null);

        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(requestWithToken("frag_bad"), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull(); // 链继续放行（交由后续过滤器/授权层拒绝）
    }
}
