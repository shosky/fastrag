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
