package com.fastrag.module.graph.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.*;
import java.util.stream.Collectors;

/**
 * LLM 提取结果规范化器（参考 Yuxi ExtractionNormalizer）
 * <p>对 LLM 提取的实体和关系进行去重、默认值填充、关系端点解析。
 * 实体按 (normalized_name, label) 合并，属性取并集。</p>
 */
public final class ExtractionNormalizer {

    private ExtractionNormalizer() {}

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Attribute {
        private String text;
        private String label;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Entity {
        private String text;
        @JsonProperty("label")
        private String label = "Entity";
        private List<Attribute> attributes;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Relation {
        @JsonProperty("source")
        private Object source;
        @JsonProperty("target")
        private Object target;
        private String text;
        @JsonProperty("label")
        private String label = "RELATED_TO";
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ExtractionResult {
        private List<Entity> entities;
        private List<Relation> relations;
        private Map<String, Object> metadata;
    }

    /**
     * 规范化提取结果
     * <p>1. 实体去重：相同 (normalized_name, label) 的实体合并，属性取并集
     * 2. 关系端点解析：支持内联 Entity 对象或字符串引用
     * 3. 默认值填充：entity.label 默认 "Entity"，relation.label 默认 "RELATED_TO"</p>
     *
     * @param result LLM 原始提取结果
     * @return 规范化后的结果
     */
    public static ExtractionResult normalize(ExtractionResult result) {
        if (result == null) {
            result = new ExtractionResult();
            result.setEntities(new ArrayList<>());
            result.setRelations(new ArrayList<>());
            return result;
        }

        // 确保 entities 和 relations 不为 null（防止调用方 NPE）
        if (result.getEntities() == null) {
            result.setEntities(new ArrayList<>());
        }
        if (result.getRelations() == null) {
            result.setRelations(new ArrayList<>());
        }

        // 填充实体默认值并规范化
        List<Entity> entities = result.getEntities();

        // 按 (normalized_name, label) 去重合并
        Map<String, Entity> entityMap = new LinkedHashMap<>();
        for (Entity entity : entities) {
            if (entity.getText() == null || entity.getText().isBlank()) continue;
            String normalized = NameNormalizer.normalize(entity.getText());
            String label = (entity.getLabel() != null && !entity.getLabel().isBlank())
                    ? entity.getLabel() : "Entity";
            String key = normalized + ":" + label;

            Entity existing = entityMap.get(key);
            if (existing != null) {
                // 合并属性
                mergeAttributes(existing, entity);
            } else {
                entity.setText(entity.getText().trim());
                entity.setLabel(label);
                entityMap.put(key, entity);
            }
        }

        // 构建名称引用映射
        Map<String, Entity> refByText = new HashMap<>();
        Map<String, Entity> refById = new HashMap<>();
        for (Entity entity : entityMap.values()) {
            refByText.put(NameNormalizer.normalize(entity.getText()), entity);
        }

        // 解析关系端点
        List<Relation> relations = result.getRelations();
        if (relations == null) relations = new ArrayList<>();
        for (Relation rel : relations) {
            if (rel.getSource() == null || rel.getTarget() == null) continue;
            rel.setSource(resolveEntityRef(rel.getSource(), entityMap.values(), refByText, refById));
            rel.setTarget(resolveEntityRef(rel.getTarget(), entityMap.values(), refByText, refById));
            if (rel.getLabel() == null || rel.getLabel().isBlank()) {
                rel.setLabel("RELATED_TO");
            }
        }

        result.setEntities(new ArrayList<>(entityMap.values()));
        result.setRelations(relations);

        // 元数据
        if (result.getMetadata() == null) {
            result.setMetadata(new HashMap<>());
        }
        result.getMetadata().putIfAbsent("extractor_type", "llm");
        result.getMetadata().putIfAbsent("schema_version", 1);

        return result;
    }

    /**
     * 合并两个实体的属性列表（取并集）
     */
    private static void mergeAttributes(Entity target, Entity source) {
        if (source.getAttributes() == null || source.getAttributes().isEmpty()) return;
        if (target.getAttributes() == null) target.setAttributes(new ArrayList<>());

        Set<String> existing = target.getAttributes().stream()
                .map(a -> NameNormalizer.normalize(a.getText()) + ":" + (a.getLabel() != null ? a.getLabel() : ""))
                .collect(Collectors.toSet());

        for (Attribute attr : source.getAttributes()) {
            String attrKey = NameNormalizer.normalize(attr.getText()) + ":" + (attr.getLabel() != null ? attr.getLabel() : "");
            if (!existing.contains(attrKey)) {
                target.getAttributes().add(attr);
                existing.add(attrKey);
            }
        }
    }

    /**
     * 解析关系端点引用，支持内联 Entity 对象、字符串引用
     */
    private static String resolveEntityRef(Object ref, Collection<Entity> entities,
                                           Map<String, Entity> refByText, Map<String, Entity> refById) {
        if (ref instanceof String) {
            String name = ((String) ref).trim();
            // 先按标准化名称查找
            Entity found = refByText.get(NameNormalizer.normalize(name));
            if (found != null) return found.getText();
            // 再按 ID 查找
            found = refById.get(name);
            if (found != null) return found.getText();
            return name;
        } else if (ref instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) ref;
            Object text = map.get("text");
            Object label = map.get("label");
            String name = text != null ? text.toString().trim() : "";
            if (!name.isEmpty()) return name;
        }
        return ref != null ? ref.toString() : "";
    }
}
