package io.github.coco.common.exception;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.coco.common.exception.type.CocoConflictException;
import io.github.coco.common.exception.type.CocoForbiddenException;
import io.github.coco.common.exception.type.CocoNotFoundException;
import io.github.coco.common.exception.type.CocoRequestException;
import io.github.coco.common.exception.type.CocoSystemException;
import io.github.coco.common.exception.type.CocoUnauthorizedException;
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
 *   <li>模块：{@code coco-common-exception}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class CocoExceptionTest {

    @Test
    void createsExceptionFromErrorCodeContract() {
        CocoException exception = CocoCommonErrorCode.INVALID_ARGUMENT.exception("name");

        assertEquals("coco.error.invalid-argument", exception.code());
        assertEquals("Invalid argument: {0}", exception.defaultMessage());
        assertArrayEquals(new Object[] {"name"}, exception.args());
    }

    @Test
    void constructorAcceptsErrorCodeContract() {
        CocoException exception = new CocoException(CocoCommonErrorCode.UNKNOWN);

        assertEquals("coco.error.unknown", exception.code());
        assertEquals("Unknown error", exception.defaultMessage());
        assertEquals("Unknown error", exception.getMessage());
    }

    @Test
    void preservesCauseAndArgumentsFromErrorCodeContract() {
        IllegalStateException cause = new IllegalStateException("boom");
        CocoException exception = CocoCommonErrorCode.INVALID_ARGUMENT.exception(cause, "name");

        assertSame(cause, exception.getCause());
        assertEquals("coco.error.invalid-argument", exception.code());
        assertArrayEquals(new Object[] {"name"}, exception.args());
    }

    @Test
    void createsTypedExceptionsFromErrorCodeContract() {
        assertInstanceOf(CocoRequestException.class,
                CocoCommonErrorCode.INVALID_ARGUMENT.request("name"));
        assertInstanceOf(CocoUnauthorizedException.class,
                CocoCommonErrorCode.UNAUTHORIZED.unauthorized());
        assertInstanceOf(CocoForbiddenException.class,
                CocoCommonErrorCode.FORBIDDEN.forbidden());
        assertInstanceOf(CocoNotFoundException.class,
                CocoCommonErrorCode.NOT_FOUND.notFound("user"));
        assertInstanceOf(CocoConflictException.class,
                CocoCommonErrorCode.CONFLICT.conflict("username"));
        assertInstanceOf(CocoSystemException.class,
                CocoCommonErrorCode.INTERNAL_ERROR.system());
    }

    @Test
    void typedExceptionsAreOrganizedInTypePackage() {
        assertEquals("io.github.coco.common.exception.type", CocoRequestException.class.getPackageName());
        assertEquals("io.github.coco.common.exception.type", CocoUnauthorizedException.class.getPackageName());
        assertEquals("io.github.coco.common.exception.type", CocoForbiddenException.class.getPackageName());
        assertEquals("io.github.coco.common.exception.type", CocoNotFoundException.class.getPackageName());
        assertEquals("io.github.coco.common.exception.type", CocoConflictException.class.getPackageName());
        assertEquals("io.github.coco.common.exception.type", CocoSystemException.class.getPackageName());
    }

    @Test
    void typedExceptionsPreserveCodeDefaultMessageCauseAndArguments() {
        IllegalStateException cause = new IllegalStateException("boom");
        CocoSystemException exception = CocoCommonErrorCode.INTERNAL_ERROR.system(cause, "database");

        assertSame(cause, exception.getCause());
        assertEquals("coco.error.internal-error", exception.code());
        assertEquals("Internal server error", exception.defaultMessage());
        assertArrayEquals(new Object[] {"database"}, exception.args());
    }

    @Test
    void createsTypedExceptionsFromStaticFactory() {
        CocoRequestException exception = CocoExceptions.request(CocoCommonErrorCode.INVALID_ARGUMENT, "name");

        assertEquals("coco.error.invalid-argument", exception.code());
        assertEquals("Invalid argument: {0}", exception.defaultMessage());
        assertArrayEquals(new Object[] {"name"}, exception.args());
    }

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
