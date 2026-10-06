package com.fastrag.common.service;

/**
 * API Token 验证器接口，定义在 common 模块中，
 * 由 fastrag-iam 模块提供实现，供 security 模块的过滤器调用。
 * <p>
 * 使用方通过 @Autowired(required = false) 注入，
 * 当 iam 模块未引入时该 bean 不存在，过滤器自动跳过 Token 验证。
 */
public interface ApiTokenValidator {

    /**
     * 验证 API Token 是否有效，返回 tokenId 与 permission 范围。
     *
     * @param tokenValue 原始 token 字符串
     * @return 认证信息（tokenId + permission），token 无效则返回 null
     */
    TokenAuthInfo validateAndGetAuthInfo(String tokenValue);

    /**
     * 验证 API Token 是否有效，返回关联的 Token ID。
     *
     * @param tokenValue 原始 token 字符串
     * @return 关联的 token id，如果 token 无效则返回 null
     */
    default String validateAndGetTokenId(String tokenValue) {
        TokenAuthInfo info = validateAndGetAuthInfo(tokenValue);
        return info != null ? info.getTokenId() : null;
    }
}
