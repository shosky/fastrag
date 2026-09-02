package com.fastrag.module.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.platform.entity.SensitiveWord;
import com.fastrag.module.platform.mapper.SensitiveWordMapper;
import com.fastrag.module.platform.service.SensitiveWordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SensitiveWordServiceImpl implements SensitiveWordService {

    private final SensitiveWordMapper sensitiveWordMapper;

    /** 启用词表缓存：会话每条消息都会做输入/输出检查，写失效读加载，避免逐消息全表查询 */
    private volatile List<SensitiveWord> enabledCache;

    @Override
    public List<SensitiveWord> list() {
        List<SensitiveWord> cached = enabledCache;
        if (cached == null) {
            cached = sensitiveWordMapper.selectList(
                    new LambdaQueryWrapper<SensitiveWord>().eq(SensitiveWord::getEnabled, 1)
            );
            enabledCache = cached;
        }
        return cached;
    }

    @Override
    public void invalidate() {
        enabledCache = null;
    }

    @Override
    public Optional<String> checkAndFilter(String text, String mode) {
        if (text == null || text.isEmpty()) {
            return Optional.empty();
        }

        List<SensitiveWord> words = list();
        String result = text;

        for (SensitiveWord sw : words) {
            String word = sw.getWord();
            if (word == null || word.isEmpty()) {
                continue;
            }
            if (result.contains(word)) {
                switch (mode) {
                    case "reject":
                        return Optional.of("输入包含敏感词: " + word);
                    case "replace":
                        String replacement = sw.getReplacement();
                        result = result.replace(word, replacement != null ? replacement : "");
                        break;
                    case "mask":
                        // Mask: keep first char, rest as *
                        String masked = word.charAt(0) + "*".repeat(Math.max(0, word.length() - 1));
                        result = result.replace(word, masked);
                        break;
                    default:
                        break;
                }
            }
        }

        if ("reject".equals(mode)) {
            return Optional.empty();
        }
        return Optional.of(result);
    }

    @Override
    public Optional<String> checkInput(String text) {
        if (text == null || text.isEmpty()) {
            return Optional.empty();
        }

        for (SensitiveWord sw : list()) {
            if (isBlockInput(sw) && sw.getWord() != null && !sw.getWord().isEmpty()
                    && text.contains(sw.getWord())) {
                // 命中词不回显给用户；优先使用词条配置的「指定回复」
                String reply = sw.getReplacement();
                return Optional.of(reply != null && !reply.isBlank()
                        ? reply : "您的输入包含受限内容，无法处理");
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<String> filterOutput(String text) {
        if (text == null || text.isEmpty()) {
            return Optional.empty();
        }

        List<SensitiveWord> words = list();
        String result = text;
        boolean found = false;

        for (SensitiveWord sw : words) {
            if (isReplaceAnswer(sw) && sw.getWord() != null && !sw.getWord().isEmpty()
                    && result.contains(sw.getWord())) {
                String replacement = sw.getReplacement();
                result = result.replace(sw.getWord(), replacement != null ? replacement : "***");
                found = true;
            }
        }

        return found ? Optional.of(result) : Optional.empty();
    }

    @Override
    public void save(SensitiveWord sw) {
        sw.setCreatedAt(LocalDateTime.now());
        sensitiveWordMapper.insert(sw);
        invalidate();
    }

    @Override
    public void update(SensitiveWord sw) {
        sensitiveWordMapper.updateById(sw);
        invalidate();
    }

    @Override
    public void delete(Long id) {
        sensitiveWordMapper.deleteById(id);
        invalidate();
    }

    @Override
    public SensitiveWord getById(Long id) {
        return sensitiveWordMapper.selectById(id);
    }

    private boolean isBlockInput(SensitiveWord sw) {
        return parseCategory(sw.getCategory()).blockInput;
    }

    private boolean isReplaceAnswer(SensitiveWord sw) {
        return parseCategory(sw.getCategory()).replaceAnswer;
    }

    private CategoryFlags parseCategory(String categoryJson) {
        CategoryFlags flags = new CategoryFlags();
        if (categoryJson != null && !categoryJson.isEmpty()) {
            flags.blockInput = categoryJson.contains("\"blockInput\":true");
            flags.blockSearch = categoryJson.contains("\"blockSearch\":true");
            flags.replaceAnswer = categoryJson.contains("\"replaceAnswer\":true");
        }
        return flags;
    }

    private static class CategoryFlags {
        boolean blockInput;
        boolean blockSearch;
        boolean replaceAnswer;
    }
}
