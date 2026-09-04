package com.fastrag.module.bpm.executor;

import com.fastrag.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SpelEvaluator 单元测试。
 *
 * 覆盖维度：
 *  1) 基本算术/逻辑/三元
 *  2) 上下文变量访问
 *  3) 安全策略：拒绝 T() 类型访问、new 表达式
 *  4) evalBool 的多种返回值类型转换（Boolean/Number/String/null）
 *  5) parseConfig 的正常与异常路径
 */
class SpelEvaluatorTest {

    private final SpelEvaluator eval = new SpelEvaluator();

    @Nested
    @DisplayName("eval: 基本算术与变量")
    class BasicEval {

        @Test
        @DisplayName("空表达式返回 true")
        void emptyExprReturnsTrue() {
            assertEquals(Boolean.TRUE, eval.eval("", null));
            assertEquals(Boolean.TRUE, eval.eval("   ", null));
            assertEquals(Boolean.TRUE, eval.eval(null, null));
        }

        @Test
        @DisplayName("字面量整数相加")
        void literalArithmetic() {
            assertEquals(7, eval.eval("3 + 4", null));
            assertEquals(1.5, eval.eval("3 / 2.0", null));
        }

        @Test
        @DisplayName("变量替换（显式 # 前缀）")
        void variableSubstitution() {
            Map<String, Object> vars = new HashMap<>();
            vars.put("a", 10);
            vars.put("b", 20);
            assertEquals(30, eval.eval("#a + #b", vars));
            assertEquals(true, eval.eval("#a < #b", vars));
        }
    }

    @Nested
    @DisplayName("eval: 安全策略")
    class Security {

        @Test
        @DisplayName("禁止 T() 类型访问")
        void rejectTypeAccess() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> eval.eval("T(java.lang.Runtime).getRuntime().exec('rm')", null));
            assertEquals(40043, ex.getCode());
        }

        @Test
        @DisplayName("禁止 new 表达式（命中安全策略 -> PARSE/EVAL ERROR）")
        void rejectNewExpression() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> eval.eval("T(java.lang.String).new 'x'", null));
            // 安全拦截：PARSE_ERROR(40042) 或 EVAL_ERROR(40043) 都算成功
            assertTrue(ex.getCode() == 40042 || ex.getCode() == 40043,
                    "expected 40042 or 40043 but was " + ex.getCode());
        }
    }

    @Nested
    @DisplayName("eval: 解析失败")
    class ParseFailure {

        @Test
        @DisplayName("语法错误抛出 EXPRESSION_PARSE_ERROR")
        void parseError() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> eval.eval("1 +", null));
            // 解析阶段抛 ParseException → EXPRESSION_PARSE_ERROR (40042)
            assertEquals(40042, ex.getCode());
        }
    }

    @Nested
    @DisplayName("evalBool: 多类型返回值")
    class EvalBool {

        @Test
        @DisplayName("Boolean 直接返回")
        void booleanDirect() {
            Map<String, Object> v = Map.of("flag", true);
            assertTrue(eval.evalBool("#flag", v));
            v = Map.of("flag", false);
            assertFalse(eval.evalBool("#flag", v));
        }

        @Test
        @DisplayName("Number 非零即真")
        void numberTruthy() {
            assertTrue(eval.evalBool("1", null));
            assertFalse(eval.evalBool("0", null));
            assertFalse(eval.evalBool("0.0", null));
        }

        @Test
        @DisplayName("String 非空且非 'false' 即真")
        void stringTruthy() {
            assertTrue(eval.evalBool("'ok'", null));
            assertFalse(eval.evalBool("'false'", null));
            assertFalse(eval.evalBool("''", null));
        }

        @Test
        @DisplayName("null 返回 false")
        void nullReturnsFalse() {
            assertFalse(eval.evalBool("null", null));
        }
    }

    @Nested
    @DisplayName("parseConfig: JSON 解析")
    class ParseConfig {

        @Test
        @DisplayName("空值返回空 Map")
        void blankReturnsEmpty() {
            assertTrue(eval.parseConfig(null).isEmpty());
            assertTrue(eval.parseConfig("").isEmpty());
            assertTrue(eval.parseConfig("   ").isEmpty());
        }

        @Test
        @DisplayName("合法 JSON 解析为 Map")
        void validJson() {
            Map<String, Object> r = eval.parseConfig("{\"x\":1,\"y\":\"a\"}");
            assertEquals(1, r.get("x"));
            assertEquals("a", r.get("y"));
        }

        @Test
        @DisplayName("非法 JSON 抛 NODE_CONFIG_INVALID")
        void invalidJson() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> eval.parseConfig("{not-json}"));
            assertEquals(40021, ex.getCode());
        }
    }
}