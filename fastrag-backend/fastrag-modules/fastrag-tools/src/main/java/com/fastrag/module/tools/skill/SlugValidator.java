package com.fastrag.module.tools.skill;

import com.fastrag.common.exception.BusinessException;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 技能 Slug 白名单校验器。
 *
 * <p>技能 Slug 是技能在文件系统目录（{dataDir}/skills/{slug}）与数据库中的统一标识，
 * 必须严格白名单校验以防路径遍历与非法目录名。</p>
 *
 * <p>规则：{@code ^[a-z0-9][a-z0-9._-]{0,63}$} —— 小写字母或数字开头，
 * 仅含小写字母、数字、点、下划线、连字符，总长度 1-64。</p>
 *
 * <p>提供的工具方法：
 * <ul>
 *   <li>{@code isValid} — 判断 slug 是否合法</li>
 *   <li>{@code validate} — 校验非法时抛 {@link BusinessException}（400，消息含规则与实际值），合法时原样返回</li>
 *   <li>{@code normalize} — 从名称派生合法 slug（小写、空白/连续非法字符转 -、掐头尾 -、截 64），无法派生时返回 null</li>
 * </ul>
 */
public final class SlugValidator {

    /** 合法 slug 白名单：小写字母/数字开头，仅含 a-z 0-9 . _ -，长度 1-64 */
    public static final String PATTERN = "^[a-z0-9][a-z0-9._-]{0,63}$";

    private static final Pattern REGEX = Pattern.compile(PATTERN);

    /** 规则说明（拼入错误消息） */
    private static final String RULE_HINT = "（规则：小写字母或数字开头，仅含 a-z、0-9、.、_、-，长度 1-64）";

    private SlugValidator() {}

    /** 判断 slug 是否合法 */
    public static boolean isValid(String slug) {
        return slug != null && REGEX.matcher(slug).matches();
    }

    /**
     * 校验 slug，非法时抛 BusinessException（400），合法时原样返回（便于内联赋值）。
     */
    public static String validate(String slug) {
        if (!isValid(slug)) {
            throw BusinessException.badRequest("非法的技能 slug: \"" + slug + "\"" + RULE_HINT);
        }
        return slug;
    }

    /**
     * 从名称派生合法 slug：小写、空白/连续非法字符转 -、掐头尾 -、截 64。
     * 结果仍非法时返回 null（调用方应提示显式提供 slug）。
     */
    public static String normalize(String name) {
        if (name == null) return null;
        String lowered = name.toLowerCase(Locale.ROOT);
        String dashed = lowered.replaceAll("[^a-z0-9._-]+", "-");
        String trimmed = dashed.replaceAll("^-+|-+$", "");
        String result = trimmed.length() > 64 ? trimmed.substring(0, 64) : trimmed;
        result = result.replaceAll("^-+|-+$", "");
        return isValid(result) ? result : null;
    }
}
