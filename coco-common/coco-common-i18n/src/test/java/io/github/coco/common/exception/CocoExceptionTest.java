package io.github.coco.common.exception;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Coco 框架异常测试。
 * <p>
 * 验证框架异常只保存消息编码、默认文本和参数，不在异常内部解析国际化文本。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-common-i18n}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class CocoExceptionTest {

    @Test
    void preservesCodeDefaultMessageAndArguments() {
        CocoException exception = new CocoException("coco.error.invalid-argument", "参数 {0} 不合法", "name");

        assertEquals("coco.error.invalid-argument", exception.code());
        assertEquals("参数 {0} 不合法", exception.defaultMessage());
        assertEquals("参数 {0} 不合法", exception.getMessage());
        assertArrayEquals(new Object[] {"name"}, exception.args());
    }

    @Test
    void fallsBackToCodeWhenDefaultMessageIsBlank() {
        CocoException exception = new CocoException("coco.error.unknown");

        assertEquals("coco.error.unknown", exception.getMessage());
    }

    @Test
    void preservesCause() {
        IllegalStateException cause = new IllegalStateException("boom");
        CocoException exception = new CocoException("coco.error.unknown", "未知错误", cause);

        assertSame(cause, exception.getCause());
    }

    @Test
    void rejectsBlankCode() {
        assertThrows(IllegalArgumentException.class, () -> new CocoException(" ", "默认消息"));
    }

    @Test
    void protectsArgumentsFromExternalMutation() {
        Object[] args = new Object[] {"before"};
        CocoException exception = new CocoException("coco.error.invalid-argument", "参数不合法", args);

        args[0] = "after";

        assertArrayEquals(new Object[] {"before"}, exception.args());
    }
}
