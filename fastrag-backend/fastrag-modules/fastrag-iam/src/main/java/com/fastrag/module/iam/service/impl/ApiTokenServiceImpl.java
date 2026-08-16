package com.fastrag.module.iam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.common.exception.BusinessException;
import com.fastrag.module.iam.entity.SysApiToken;
import com.fastrag.module.iam.mapper.SysApiTokenMapper;
import com.fastrag.module.iam.model.ApiTokenCreateRequest;
import com.fastrag.module.iam.model.ApiTokenDto;
import com.fastrag.module.iam.service.ApiTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * API令牌管理服务实现类。
 *
 * <p>负责平台级API令牌的创建、查询、吊销（删除）和有效性验证。
 * API令牌用于外部系统通过API访问FastRAG平台，格式为 frag_{uuid}。
 * 支持设置令牌过期时间，过期令牌自动失效。令牌的验证在API网关层完成。</p>
 *
 * <p>核心功能：</p>
 * <ul>
 *   <li>createToken - 生成新的API令牌，支持设置过期时间</li>
 *   <li>listTokens - 查询令牌列表（按创建时间倒序）</li>
 *   <li>revokeToken - 吊销（物理删除）指定的API令牌</li>
 *   <li>findValidToken - 验证令牌有效性（是否存在、未吊销、未过期）</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ApiTokenServiceImpl implements ApiTokenService {

    private final SysApiTokenMapper apiTokenMapper;

    @Override
    public List<ApiTokenDto> listTokens() {
        return apiTokenMapper.selectList(
                new LambdaQueryWrapper<SysApiToken>()
                        .orderByDesc(SysApiToken::getCreatedAt)
        ).stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ApiTokenDto createToken(String operatorId, ApiTokenCreateRequest request) {
        SysApiToken entity = new SysApiToken();
        entity.setName(request.getName());
        entity.setPermission(request.getPermission() != null ? request.getPermission() : "read");
        entity.setCreatedBy(operatorId);
        entity.setRevoked(false);

        // 生成平台级 token 值：frag_<uuid>
        String rawToken = "frag_" + UUID.randomUUID().toString().replace("-", "");
        entity.setToken(rawToken);

        // 设置过期时间
        if (request.getExpiresIn() != null && request.getExpiresIn() > 0) {
            entity.setExpiresAt(LocalDateTime.now().plusSeconds(request.getExpiresIn()));
        }
        // expiresIn 为 null 表示永不过期，expiresAt 保持 null

        apiTokenMapper.insert(entity);
        return toDto(entity);
    }

    @Override
    @Transactional
    public void revokeToken(String tokenId) {
        SysApiToken token = apiTokenMapper.selectById(tokenId);
        if (token == null) {
            throw BusinessException.notFound("Token 不存在");
        }
        // 物理删除
        apiTokenMapper.deleteById(tokenId);
    }

    @Override
    public SysApiToken findValidToken(String tokenValue) {
        return apiTokenMapper.selectOne(
                new LambdaQueryWrapper<SysApiToken>()
                        .eq(SysApiToken::getToken, tokenValue)
                        .eq(SysApiToken::getRevoked, false)
                        .and(w -> w.isNull(SysApiToken::getExpiresAt)
                                .or().ge(SysApiToken::getExpiresAt, LocalDateTime.now()))
        );
    }

    private ApiTokenDto toDto(SysApiToken entity) {
        ApiTokenDto dto = new ApiTokenDto();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setToken(entity.getToken()); // 调用方决定是否脱敏
        dto.setPermission(entity.getPermission());
        dto.setExpiresAt(entity.getExpiresAt());
        dto.setExpired(entity.getExpiresAt() != null && entity.getExpiresAt().isBefore(LocalDateTime.now()));
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }
}
