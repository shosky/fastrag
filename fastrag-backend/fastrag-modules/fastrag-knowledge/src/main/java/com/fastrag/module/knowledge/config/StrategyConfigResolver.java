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
 * 解析策略配置统一解析器，负责将策略配置合并为统一的 {@link ParseStrategyConfig} 对象。
 *
 * <p>核心职责：
 * 实现覆盖链配置合并，优先级从高到低为：上传请求参数 > 选中策略 > KB 默认策略 > 系统默认值。
 * 本类负责「系统默认值 + 策略 advanced 配置」的合并，所有消费方（解析器、分片器、索引、图谱构建）
 * 统一调用本入口获取配置，不再各自解析 Map。
 *
 * <p>关键实现逻辑：
 * <ul>
 *   <li>系统默认层：从 platform 模块的 SysConfig 读取通用配置（如 chunkSize、overlap），缺失时使用代码兜底值</li>
 *   <li>策略层：根据 strategyId 查询 kb_parse_strategy 表，解析 advanced JSON 字段</li>
 *   <li>支持新分组结构（parse/chunk/index）和旧平铺字段（chunkLength/delimiter/tableMode）两种格式，自动兼容回退</li>
 *   <li>applyGroup 方法按分组（ParseConfig/ChunkConfig/IndexConfig）分别合并配置项</li>
 *   <li>applyLegacyFields 方法处理旧版本策略的平铺字段，在新分组结构缺失时回退使用</li>
 * </ul>
 *
 * <p>与其他模块的交互：
 * <ul>
 *   <li>platform 模块（{@link ConfigManageService}）— 获取系统级配置默认值</li>
 *   <li>knowledge 模块（{@link KbParseStrategyMapper}）— 查询解析策略记录</li>
 * </ul>
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
        return resolve(strategyId != null ? strategyMapper.selectById(strategyId) : null);
    }

    /**
     * 按显式策略对象解析配置（预览场景：临时构造的策略对象，未落库）。
     */
    public ParseStrategyConfig resolve(KbParseStrategy strategy) {
        ParseStrategyConfig config = new ParseStrategyConfig();

        // 1. 系统默认层（SysConfig 未配置时使用代码兜底，与前端 DEFAULT_ADVANCED 对齐）
        config.getChunk().setChunkLength(getIntConfig("general_chunk_size", 1000));
        config.getChunk().setOverlap(getIntConfig("general_chunk_overlap", 100));

        // 2. 策略层
        if (strategy != null && StringUtils.hasText(strategy.getAdvanced())) {
            applyAdvanced(config, strategy.getAdvanced());
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
            setIfPresent(group, "keyframeIntervalSeconds", v -> parse.setKeyframeIntervalSeconds(((Number) v).intValue()));
            setIfPresent(group, "keyframeHashThreshold", v -> parse.setKeyframeHashThreshold(((Number) v).doubleValue()));
        } else if (target instanceof ParseStrategyConfig.ChunkConfig chunk) {
            setIfPresent(group, "strategy", v -> chunk.setStrategy(String.valueOf(v)));
            setIfPresent(group, "chunkLength", v -> chunk.setChunkLength(((Number) v).intValue()));
            setIfPresent(group, "overlap", v -> chunk.setOverlap(((Number) v).intValue()));
            setIfPresent(group, "titlePrefix", v -> chunk.setTitlePrefix(toBoolean(v)));
            setIfPresent(group, "headingPath", v -> chunk.setHeadingPath(toBoolean(v)));
            setIfPresent(group, "delimiters", v -> chunk.setDelimiters(toStringList(v)));
            setIfPresent(group, "parentMaxChunkLength", v -> chunk.setParentMaxChunkLength(((Number) v).intValue()));
            setIfPresent(group, "parentAggLevel", v -> chunk.setParentAggLevel(String.valueOf(v)));
            setIfPresent(group, "semanticThreshold", v -> chunk.setSemanticThreshold(((Number) v).intValue()));
            setIfPresent(group, "embeddingModel", v -> chunk.setEmbeddingModel(String.valueOf(v)));
        } else if (target instanceof ParseStrategyConfig.IndexConfig index) {
            setIfPresent(group, "embedFields", v -> index.setEmbedFields(toStringList(v)));
        }
    }

    /** 兼容旧平铺字段（旧版本策略的 advanced 为一级 Map，如 chunkLength / delimiter / tableMode） */
    @SuppressWarnings("unchecked")
    private void applyLegacyFields(ParseStrategyConfig config, Map<String, Object> adv) {
        ParseStrategyConfig.ChunkConfig chunk = config.getChunk();
        ParseStrategyConfig.ParseConfig parse = config.getParse();

        if (!adv.containsKey("chunk")) {
            if (adv.containsKey("strategy")) {
                chunk.setStrategy(String.valueOf(adv.get("strategy")));
            }
            if (adv.containsKey("chunkLength")) {
                chunk.setChunkLength(((Number) adv.get("chunkLength")).intValue());
            }
            if (adv.containsKey("overlap")) {
                chunk.setOverlap(((Number) adv.get("overlap")).intValue());
            }
            if (adv.containsKey("delimiters") && adv.get("delimiters") != null) {
                chunk.setDelimiters(toStringList(adv.get("delimiters")));
            }
            // 旧字段为单数 delimiter（字符串），映射进新数组结构
            if (adv.containsKey("delimiter") && adv.get("delimiter") != null) {
                chunk.setDelimiters(List.of(String.valueOf(adv.get("delimiter"))));
            }
            if (adv.containsKey("titlePrefix")) {
                chunk.setTitlePrefix(toBoolean(adv.get("titlePrefix")));
            }
            if (adv.containsKey("headingPath")) {
                chunk.setHeadingPath(toBoolean(adv.get("headingPath")));
            }
            if (adv.containsKey("parentMaxChunkLength")) {
                chunk.setParentMaxChunkLength(((Number) adv.get("parentMaxChunkLength")).intValue());
            }
            if (adv.containsKey("parentAggLevel")) {
                chunk.setParentAggLevel(String.valueOf(adv.get("parentAggLevel")));
            }
            if (adv.containsKey("semanticThreshold")) {
                chunk.setSemanticThreshold(((Number) adv.get("semanticThreshold")).intValue());
            }
            if (adv.containsKey("embeddingModel")) {
                chunk.setEmbeddingModel(String.valueOf(adv.get("embeddingModel")));
            }
        }
        if (!adv.containsKey("parse")) {
            if (adv.containsKey("tableMode")) {
                parse.setTableMode(String.valueOf(adv.get("tableMode")));
            }
            if (adv.containsKey("keyframeIntervalSeconds")) {
                parse.setKeyframeIntervalSeconds(((Number) adv.get("keyframeIntervalSeconds")).intValue());
            }
            if (adv.containsKey("keyframeHashThreshold")) {
                parse.setKeyframeHashThreshold(((Number) adv.get("keyframeHashThreshold")).doubleValue());
            }
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
