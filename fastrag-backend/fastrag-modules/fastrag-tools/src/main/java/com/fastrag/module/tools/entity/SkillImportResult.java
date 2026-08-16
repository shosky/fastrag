package com.fastrag.module.tools.entity;

/**
 * 技能导入结果 DTO，用于技能安装/导入操作的响应。
 *
 * <p>非数据库实体，仅作为 API 响应对象使用。
 * 包含导入是否成功、技能 slug/名称或错误信息。</p>
 *
 * <h3>核心字段：</h3>
 * <ul>
 *   <li>{@code success} - 是否导入成功</li>
 *   <li>{@code slug} - 技能标识符（成功时）</li>
 *   <li>{@code name} - 技能名称（成功时）</li>
 *   <li>{@code error} - 错误信息（失败时）</li>
 * </ul>
 *
 * <p>提供便捷静态工厂方法 {@link #ok(String, String)} 和 {@link #fail(String)}。</p>
 */
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SkillImportResult {
    private boolean success;
    private String slug;
    private String name;
    private String error;

    public static SkillImportResult ok(String slug, String name) {
        return new SkillImportResult(true, slug, name, null);
    }

    public static SkillImportResult fail(String error) {
        return new SkillImportResult(false, null, null, error);
    }
}
