package com.fastrag.module.tools.entity;

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
