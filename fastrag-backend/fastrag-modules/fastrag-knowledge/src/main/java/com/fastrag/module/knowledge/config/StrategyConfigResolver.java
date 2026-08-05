package com.fastrag.module.knowledge.config;

import cn.hutool.json.JSONUtil;
import com.fastrag.module.knowledge.entity.KbParseStrategy;
import com.fastrag.module.knowledge.mapper.KbParseStrategyMapper;
import com.fastrag.module.knowledge.model.ParseStrategyConfig;
import com.fastrag.module.platform.entity.SysConfig;
import com.fastrag.module.platform.service.ConfigManageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 解析策略配置统一解析入口（覆盖链解析）。
 *
 * <p>覆盖链（从高到低）：上传请求参数 > 选中策略 > KB 默认策略 > 系统默认值。</p>
 * <p>本类负责「系统默认值 + 策略 advanced（新分组结构 + 旧平铺字段兼容）」的合并，
 * 所有消费方（解析器、分片器、索引、图谱构建）只调用本入口，不再各自手拆 Map。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StrategyConfigResolver {

    private final KbParseStrategyMapper strategyMapper;
    private final ConfigManageService configService;

    /**
     * 解析策略配置（无请求覆盖）。
     *
     * @param strategyId 策略 ID，可为 null（此时仅使用系统默认值）
     */
    public ParseStrategyConfig resolve(String strategyId) {
        ParseStrategyConfig config = new ParseStrategyConfig();

        // 1. 系统默认层（SysConfig 未配置时使用代码兜底，与前端 DEFAULT_ADVANCED 对齐）
        config.getChunk().setChunkLength(getIntConfig("general_chunk_size", 2000));
        config.getChunk().setOverlap(getIntConfig("general_chunk_overlap", 100));

        // 2. 策略层
        if (strategyId != null) {
            KbParseStrategy strategy = strategyMapper.selectById(strategyId);
            if (strategy != null && StringUtils.hasText(strategy.getAdvanced())) {
                applyAdvanced(config, strategy.getAdvanced());
            }
        }

        return config;
    }

    // ========== 配置合并 ==========

    /**
     * 将策略 advanced JSON 合并进 config。
     * 新分组结构（parse/chunk/index）优先，旧平铺字段兼容回退。
     */
    private void applyAdvanced(ParseStrategyConfig config, String advancedJson) {
        try {
            Map<String, Object> adv = JSONUtil.toBean(advancedJson, Map.class);
            if (adv == null || adv.isEmpty()) return;

            applyGroup(config.getParse(), adv.get("parse"));
            applyGroup(config.getChunk(), adv.get("chunk"));
            applyGroup(config.getIndex(), adv.get("index"));

            // 兼容旧平铺字段（新结构缺失时回退）
            applyLegacyFields(config, adv);
        } catch (Exception e) {
            log.warn("Failed to parse strategy advanced config, using defaults: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void applyGroup(Object target, Object groupValue) {
        if (!(groupValue instanceof Map)) return;
        Map<String, Object> group = (Map<String, Object>) groupValue;
        if (target instanceof ParseStrategyConfig.ParseConfig parse) {
            setIfPresent(group, "tableMode", v -> parse.setTableMode(String.valueOf(v)));
            setIfPresent(group, "enablePptWholePage", v -> parse.setEnablePptWholePage(toBoolean(v)));
            setIfPresent(group, "keyframeIntervalSeconds", v -> parse.setKeyframeIntervalSeconds(((Number) v).intValue()));
            setIfPresent(group, "keyframeHashThreshold", v -> parse.setKeyframeHashThreshold(((Number) v).doubleValue()));
            setIfPresent(group, "enableDocSummary", v -> parse.setEnableDocSummary(toBoolean(v)));
        } else if (target instanceof ParseStrategyConfig.ChunkConfig chunk) {
            setIfPresent(group, "chunkLength", v -> chunk.setChunkLength(((Number) v).intValue()));
            setIfPresent(group, "overlap", v -> chunk.setOverlap(((Number) v).intValue()));
            setIfPresent(group, "titlePrefix", v -> chunk.setTitlePrefix(toBoolean(v)));
            setIfPresent(group, "headingPath", v -> chunk.setHeadingPath(toBoolean(v)));
            setIfPresent(group, "delimiters", v -> chunk.setDelimiters(toStringList(v)));
        } else if (target instanceof ParseStrategyConfig.IndexConfig index) {
            setIfPresent(group, "embedFields", v -> index.setEmbedFields(toStringList(v)));
        }
    }

    /** 兼容旧平铺字段（旧版本策略的 advanced 为一级 Map，如 chunkLength / delimiter / tableMode） */
    @SuppressWarnings("unchecked")
    private void applyLegacyFields(ParseStrategyConfig config, Map<String, Object> adv) {
        ParseStrategyConfig.ChunkConfig chunk = config.getChunk();
        ParseStrategyConfig.ParseConfig parse = config.getParse();

        if (!adv.containsKey("chunk") && adv.containsKey("chunkLength")) {
            chunk.setChunkLength(((Number) adv.get("chunkLength")).intValue());
        }
        if (!adv.containsKey("chunk") && adv.containsKey("overlap")) {
            chunk.setOverlap(((Number) adv.get("overlap")).intValue());
        }
        // 旧字段为单数 delimiter（字符串），映射进新数组结构
        if (!adv.containsKey("chunk") && adv.containsKey("delimiter") && adv.get("delimiter") != null) {
            chunk.setDelimiters(List.of(String.valueOf(adv.get("delimiter"))));
        }
        if (!adv.containsKey("parse") && adv.containsKey("tableMode")) {
            parse.setTableMode(String.valueOf(adv.get("tableMode")));
        }
        if (!adv.containsKey("parse") && adv.containsKey("enablePptWholePage")) {
            parse.setEnablePptWholePage(toBoolean(adv.get("enablePptWholePage")));
        }
        if (!adv.containsKey("parse") && adv.containsKey("enableDocSummary")) {
            parse.setEnableDocSummary(toBoolean(adv.get("enableDocSummary")));
        }
        if (!adv.containsKey("parse") && adv.containsKey("keyframeIntervalSeconds")) {
            parse.setKeyframeIntervalSeconds(((Number) adv.get("keyframeIntervalSeconds")).intValue());
        }
        if (!adv.containsKey("parse") && adv.containsKey("keyframeHashThreshold")) {
            parse.setKeyframeHashThreshold(((Number) adv.get("keyframeHashThreshold")).doubleValue());
        }
    }

    // ========== 工具 ==========

    private int getIntConfig(String configKey, int defaultValue) {
        try {
            SysConfig config = configService.getConfig(configKey);
            if (config != null && StringUtils.hasText(config.getConfigValue())) {
                return Integer.parseInt(config.getConfigValue().trim());
            }
        } catch (Exception e) {
            log.debug("Failed to read config {}={}, using default {}", configKey, defaultValue);
        }
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    private static List<String> toStringList(Object value) {
        if (value instanceof List) {
            List<String> result = new ArrayList<>();
            for (Object o : (List<Object>) value) {
                if (o != null) result.add(String.valueOf(o));
            }
            return result;
        }
        if (value != null) return List.of(String.valueOf(value));
        return new ArrayList<>();
    }

    private static boolean toBoolean(Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).intValue() == 1;
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static void setIfPresent(Map<String, Object> group, String key, java.util.function.Consumer<Object> setter) {
        if (group.containsKey(key) && group.get(key) != null) {
            try {
                setter.accept(group.get(key));
            } catch (Exception e) {
                log.warn("Invalid strategy config value for key '{}': {}", key, group.get(key));
            }
        }
    }
}
