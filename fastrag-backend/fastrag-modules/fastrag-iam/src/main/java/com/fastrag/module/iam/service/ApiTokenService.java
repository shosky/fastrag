package com.fastrag.module.iam.service;

import com.fastrag.module.iam.entity.SysApiToken;
import com.fastrag.module.iam.model.ApiTokenCreateRequest;
import com.fastrag.module.iam.model.ApiTokenDto;

import java.util.List;

/**
 * API Token 管理服务（平台级全局 Token）
 */
public interface ApiTokenService {

    /** 获取全部 Token 列表（token 值脱敏） */
    List<ApiTokenDto> listTokens();

    /** 创建 Token，返回含完整 token 值的 DTO */
    ApiTokenDto createToken(String operatorId, ApiTokenCreateRequest request);

    /** 撤销（删除）Token */
    void revokeToken(String tokenId);

    /** 根据 token 值查找有效的 Token 记录 */
    SysApiToken findValidToken(String tokenValue);
}
