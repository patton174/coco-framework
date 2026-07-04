package io.github.coco.common.exception;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import io.github.coco.common.exception.type.CocoRequestException;
import org.junit.jupiter.api.Test;

/**
 * Coco 静态断言工具测试。
 * <p>
 * 验证通用断言工具在校验失败时使用 Coco 异常编码抛出 {@link CocoException}，便于框架内部统一接入国际化消息。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-common-exception}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class CocoAssertTest {

    @Test
    void notNullReturnsOriginalValue() {
        Object value = new Object();

        Object result = CocoAssert.notNull(value, CocoCommonErrorCode.INVALID_ARGUMENT, "value");

        assertSame(value, result);
    }

    @Test
    void notNullThrowsCocoExceptionWithCodeAndArguments() {
        CocoException exception = assertThrows(CocoException.class,
                () -> CocoAssert.notNull(null, CocoCommonErrorCode.INVALID_ARGUMENT, "value"));

        assertEquals("coco.error.invalid-argument", exception.code());
        assertEquals("Invalid argument: {0}", exception.defaultMessage());
        assertArrayEquals(new Object[] {"value"}, exception.args());
    }

    @Test
    void hasTextReturnsOriginalText() {
        String value = " coco ";

        String result = CocoAssert.hasText(value, CocoCommonErrorCode.INVALID_ARGUMENT, "name");

        assertSame(value, result);
    }

    @Test
    void hasTextRejectsBlankText() {
        CocoException exception = assertThrows(CocoException.class,
                () -> CocoAssert.hasText(" ", CocoCommonErrorCode.INVALID_ARGUMENT, "name"));

        assertEquals("coco.error.invalid-argument", exception.code());
        assertArrayEquals(new Object[] {"name"}, exception.args());
    }

    @Test
    void isTrueRejectsFalseExpression() {
        CocoException exception = assertThrows(CocoException.class,
                () -> CocoAssert.isTrue(false, CocoCommonErrorCode.INVALID_ARGUMENT, "enabled"));

        assertEquals("coco.error.invalid-argument", exception.code());
        assertArrayEquals(new Object[] {"enabled"}, exception.args());
    }

    @Test
    void stateRejectsFalseExpression() {
        CocoException exception = assertThrows(CocoException.class,
                () -> CocoAssert.state(false, CocoCommonErrorCode.UNKNOWN));

        assertEquals("coco.error.unknown", exception.code());
    }

    @Test
    void notEmptyReturnsOriginalCollection() {
        List<String> value = List.of("coco");

        List<String> result = CocoAssert.notEmpty(value, CocoCommonErrorCode.INVALID_ARGUMENT, "items");

        assertSame(value, result);
    }

    @Test
    void notEmptyRejectsEmptyCollection() {
        CocoException exception = assertThrows(CocoException.class,
                () -> CocoAssert.notEmpty(List.of(), CocoCommonErrorCode.INVALID_ARGUMENT, "items"));

        assertEquals("coco.error.invalid-argument", exception.code());
        assertArrayEquals(new Object[] {"items"}, exception.args());
    }

    @Test
    void notEmptyRejectsEmptyMap() {
        CocoException exception = assertThrows(CocoException.class,
                () -> CocoAssert.notEmpty(Map.of(), CocoCommonErrorCode.INVALID_ARGUMENT, "items"));

        assertEquals("coco.error.invalid-argument", exception.code());
        assertArrayEquals(new Object[] {"items"}, exception.args());
    }

    @Test
    void notEmptyRejectsEmptyArray() {
        CocoException exception = assertThrows(CocoException.class,
                () -> CocoAssert.notEmpty(new String[0], CocoCommonErrorCode.INVALID_ARGUMENT, "items"));

        assertEquals("coco.error.invalid-argument", exception.code());
        assertArrayEquals(new Object[] {"items"}, exception.args());
    }

    @Test
    void rejectsNullErrorCodeWithCocoRequestException() {
        CocoRequestException exception = assertThrows(CocoRequestException.class,
                () -> CocoAssert.notNull(null, null));

        assertEquals("coco.error.missing-error-code", exception.code());
        assertEquals("Error code must not be null", exception.defaultMessage());
    }
}
