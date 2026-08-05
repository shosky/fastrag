package com.fastrag.module.iam.service.impl;

import com.fastrag.common.service.ApiTokenValidator;
import com.fastrag.module.iam.entity.SysApiToken;
import com.fastrag.module.iam.service.ApiTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * API Token 验证器实现，委托给 ApiTokenService.findValidToken()。
 */
@Component
@RequiredArgsConstructor
public class ApiTokenValidatorImpl implements ApiTokenValidator {

    private final ApiTokenService apiTokenService;

    @Override
    public String validateAndGetTokenId(String tokenValue) {
        SysApiToken token = apiTokenService.findValidToken(tokenValue);
        return token != null ? token.getId() : null;
    }
}
