package com.fastrag.common.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * API Token 验证结果载荷：tokenId + permission 范围（read/write）。
 * <p>
 * 供 security 过滤器按 permission 决定授权范围（QF-3）：read → kb:read，
 * write/空（向后兼容）→ kb:manage。token 无效时验证器返回 null。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenAuthInfo {

    /** 关联的 token id */
    private String tokenId;

    /** 权限范围：read（只读）/ write（读写）/ 空表示历史数据（按读写兼容处理） */
    private String permission;

    public static TokenAuthInfo of(String tokenId, String permission) {
        return new TokenAuthInfo(tokenId, permission);
    }
}
