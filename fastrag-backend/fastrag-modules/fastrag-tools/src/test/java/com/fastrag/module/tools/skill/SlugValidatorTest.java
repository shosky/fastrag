package com.fastrag.module.tools.skill;

import com.fastrag.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SlugValidator 白名单校验单元测试.
 * 规则：^[a-z0-9][a-z0-9._-]{0,63}$（小写字母/数字开头，仅含 a-z 0-9 . _ -，长度 1-64）。
 */
class SlugValidatorTest {

    // ========== isValid 合法矩阵 ==========

    @Test
    void testIsValid_AcceptsSingleChar() {
        assertTrue(SlugValidator.isValid("a"));
    }

    @Test
    void testIsValid_AcceptsDigitStart() {
        assertTrue(SlugValidator.isValid("0skill"));
    }

    @Test
    void testIsValid_AcceptsAllowedSpecialChars() {
        assertTrue(SlugValidator.isValid("a.b_c-d"));
    }

    @Test
    void testIsValid_AcceptsMaxLength64() {
        assertTrue(SlugValidator.isValid("a" + "b".repeat(63)));
    }

    // ========== isValid 非法矩阵 ==========

    @Test
    void testIsValid_RejectsPathTraversal() {
        assertFalse(SlugValidator.isValid("../x"));
        assertFalse(SlugValidator.isValid("a/../b"));
        assertFalse(SlugValidator.isValid("a/b"));
    }

    @Test
    void testIsValid_RejectsUppercase() {
        assertFalse(SlugValidator.isValid("ABC"));
        assertFalse(SlugValidator.isValid("aBc"));
    }

    @Test
    void testIsValid_RejectsChinese() {
        assertFalse(SlugValidator.isValid("联网搜索"));
    }

    @Test
    void testIsValid_RejectsTooLong() {
        assertFalse(SlugValidator.isValid("a" + "b".repeat(64)));
    }

    @Test
    void testIsValid_RejectsEmptyAndNull() {
        assertFalse(SlugValidator.isValid(""));
        assertFalse(SlugValidator.isValid(null));
    }

    @Test
    void testIsValid_RejectsLeadingDashAndDot() {
        assertFalse(SlugValidator.isValid("-abc"));
        assertFalse(SlugValidator.isValid(".abc"));
        assertFalse(SlugValidator.isValid("_abc"));
    }

    @Test
    void testIsValid_RejectsWhitespace() {
        assertFalse(SlugValidator.isValid("a b"));
        assertFalse(SlugValidator.isValid(" abc"));
    }

    // ========== validate：非法抛 BusinessException，消息含规则与实际值 ==========

    @Test
    void testValidate_AcceptedReturnsSameValue() {
        assertEquals("web-search", SlugValidator.validate("web-search"));
    }

    @Test
    void testValidate_RejectedThrowsWithRuleAndValue() {
        BusinessException e = assertThrows(BusinessException.class,
            () -> SlugValidator.validate("../evil"));
        assertEquals(400, e.getCode());
        assertTrue(e.getMessage().contains("../evil"), "消息应包含实际值");
        assertTrue(e.getMessage().contains("a-z"), "消息应包含规则说明");
    }

    // ========== normalize：name → 合法 slug，无法派生时返回 null ==========

    @Test
    void testNormalize_LowercasesAndJoinsWordsWithDash() {
        assertEquals("my-cool-skill", SlugValidator.normalize("My Cool Skill"));
    }

    @Test
    void testNormalize_TrimsSurroundingWhitespaceAndDashes() {
        assertEquals("test-skill", SlugValidator.normalize("  Test  Skill  "));
        assertEquals("abc", SlugValidator.normalize("-Abc-"));
    }

    @Test
    void testNormalize_CollapsesConsecutiveIllegalCharsIntoSingleDash() {
        assertEquals("a-b", SlugValidator.normalize("a!!!b"));
    }

    @Test
    void testNormalize_TruncatesTo64() {
        String longName = "abcdefghij".repeat(8); // 80 个合法字符
        String normalized = SlugValidator.normalize(longName);
        assertNotNull(normalized);
        assertEquals(64, normalized.length());
    }

    @Test
    void testNormalize_ReturnsNullWhenNothingUsable() {
        assertNull(SlugValidator.normalize("中文技能"));
        assertNull(SlugValidator.normalize("!!!"));
        assertNull(SlugValidator.normalize(""));
        assertNull(SlugValidator.normalize(null));
    }
}
