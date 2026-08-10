package io.github.coco.exception;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import io.github.coco.exception.type.CocoConflictException;
import io.github.coco.exception.type.CocoForbiddenException;
import io.github.coco.exception.type.CocoNotFoundException;
import io.github.coco.exception.type.CocoRequestException;
import io.github.coco.exception.type.CocoSystemException;
import io.github.coco.exception.type.CocoUnauthorizedException;
import io.github.coco.i18n.CocoMessage;
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
 *   <li>模块：{@code coco-exception}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class CocoExceptionTest {

    @Test
    void createsExceptionFromErrorCodeContract() {
        CocoException exception = CocoCommonErrorCode.INVALID_ARGUMENT.exception("name");

        assertEquals("coco.error.invalid-argument", exception.code());
        assertEquals("coco.error.invalid-argument", exception.defaultMessage());
        assertArrayEquals(new Object[] {"name"}, exception.args());
    }

    @Test
    void constructorAcceptsErrorCodeContract() {
        CocoException exception = new CocoException(CocoCommonErrorCode.UNKNOWN);

        assertEquals("coco.error.unknown", exception.code());
        assertEquals("coco.error.unknown", exception.defaultMessage());
        assertEquals("coco.error.unknown", exception.getMessage());
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
        assertEquals("io.github.coco.exception.type", CocoRequestException.class.getPackageName());
        assertEquals("io.github.coco.exception.type", CocoUnauthorizedException.class.getPackageName());
        assertEquals("io.github.coco.exception.type", CocoForbiddenException.class.getPackageName());
        assertEquals("io.github.coco.exception.type", CocoNotFoundException.class.getPackageName());
        assertEquals("io.github.coco.exception.type", CocoConflictException.class.getPackageName());
        assertEquals("io.github.coco.exception.type", CocoSystemException.class.getPackageName());
    }

    @Test
    void typedExceptionsPreserveCodeDefaultMessageCauseAndArguments() {
        IllegalStateException cause = new IllegalStateException("boom");
        CocoSystemException exception = CocoCommonErrorCode.INTERNAL_ERROR.system(cause, "database");

        assertSame(cause, exception.getCause());
        assertEquals("coco.error.internal-error", exception.code());
        assertEquals("coco.error.internal-error", exception.defaultMessage());
        assertArrayEquals(new Object[] {"database"}, exception.args());
    }

    @Test
    void typedErrorCodeFactoriesPreserveCauseAndArguments() {
        IllegalStateException cause = new IllegalStateException("boom");

        assertErrorCodeException(CocoCommonErrorCode.INVALID_ARGUMENT.request(cause, "REQ-1"), cause, "REQ-1");
        assertErrorCodeException(CocoCommonErrorCode.UNAUTHORIZED.unauthorized(cause, "AUTH-1"), cause, "AUTH-1");
        assertErrorCodeException(CocoCommonErrorCode.FORBIDDEN.forbidden(cause, "FORBIDDEN-1"), cause,
                "FORBIDDEN-1");
        assertErrorCodeException(CocoCommonErrorCode.NOT_FOUND.notFound(cause, "NOT-1"), cause, "NOT-1");
        assertErrorCodeException(CocoCommonErrorCode.CONFLICT.conflict(cause, "CONFLICT-1"), cause, "CONFLICT-1");
        assertErrorCodeException(CocoCommonErrorCode.INTERNAL_ERROR.system(cause, "SYSTEM-1"), cause, "SYSTEM-1");
    }

    @Test
    void createsTypedExceptionsFromStaticFactory() {
        CocoRequestException exception = CocoExceptions.request(CocoCommonErrorCode.INVALID_ARGUMENT, "name");

        assertEquals("coco.error.invalid-argument", exception.code());
        assertEquals("coco.error.invalid-argument", exception.defaultMessage());
        assertArrayEquals(new Object[] {"name"}, exception.args());
    }

    @Test
    void businessCodeMethodsTreatThrowableAsMessageArgument() {
        IllegalStateException argument = new IllegalStateException("message argument");

        assertThrowableMessageArgument(TestBusinessCode.ORDER_NOT_FOUND.request(argument), argument);
        assertThrowableMessageArgument(TestBusinessCode.UNAUTHORIZED.unauthorized(argument), argument);
        assertThrowableMessageArgument(TestBusinessCode.FORBIDDEN.forbidden(argument), argument);
        assertThrowableMessageArgument(TestBusinessCode.NOT_FOUND.notFound(argument), argument);
        assertThrowableMessageArgument(TestBusinessCode.CONFLICT.conflict(argument), argument);
        assertThrowableMessageArgument(TestBusinessCode.INTERNAL_ERROR.system(argument), argument);
    }

    @Test
    void staticBusinessCodeFactoriesTreatThrowableAsMessageArgument() {
        IllegalStateException argument = new IllegalStateException("message argument");

        assertThrowableMessageArgument(CocoBusinessExceptions.request(TestBusinessCode.ORDER_NOT_FOUND, argument),
                argument);
        assertThrowableMessageArgument(CocoBusinessExceptions.unauthorized(TestBusinessCode.UNAUTHORIZED, argument),
                argument);
        assertThrowableMessageArgument(CocoBusinessExceptions.forbidden(TestBusinessCode.FORBIDDEN, argument),
                argument);
        assertThrowableMessageArgument(CocoBusinessExceptions.notFound(TestBusinessCode.NOT_FOUND, argument), argument);
        assertThrowableMessageArgument(CocoBusinessExceptions.conflict(TestBusinessCode.CONFLICT, argument), argument);
        assertThrowableMessageArgument(CocoBusinessExceptions.system(TestBusinessCode.INTERNAL_ERROR, argument), argument);
    }

    @Test
    void staticMessageCodeFactoriesTreatThrowableAsMessageArgument() {
        IllegalStateException argument = new IllegalStateException("message argument");

        assertThrowableMessageArgument(CocoBusinessExceptions.request("sample.request", argument), argument);
        assertThrowableMessageArgument(CocoBusinessExceptions.unauthorized("sample.unauthorized", argument), argument);
        assertThrowableMessageArgument(CocoBusinessExceptions.forbidden("sample.forbidden", argument), argument);
        assertThrowableMessageArgument(CocoBusinessExceptions.notFound("sample.not-found", argument), argument);
        assertThrowableMessageArgument(CocoBusinessExceptions.conflict("sample.conflict", argument), argument);
        assertThrowableMessageArgument(CocoBusinessExceptions.system("sample.system", argument), argument);
    }

    @Test
    void staticBusinessCodeWithCauseFactoriesPreserveCauseAndArguments() {
        IllegalStateException cause = new IllegalStateException("boom");

        assertBusinessCodeException(
                CocoBusinessExceptions.requestWithCause(TestBusinessCode.ORDER_NOT_FOUND, cause, "REQ-1"),
                cause, 1001, "sample.order.not-found", "REQ-1");
        assertBusinessCodeException(
                CocoBusinessExceptions.unauthorizedWithCause(TestBusinessCode.UNAUTHORIZED, cause, "AUTH-1"),
                cause, 1002, "sample.unauthorized", "AUTH-1");
        assertBusinessCodeException(
                CocoBusinessExceptions.forbiddenWithCause(TestBusinessCode.FORBIDDEN, cause, "FORBIDDEN-1"),
                cause, 1003, "sample.forbidden", "FORBIDDEN-1");
        assertBusinessCodeException(CocoBusinessExceptions.notFoundWithCause(TestBusinessCode.NOT_FOUND, cause, "NOT-1"),
                cause, 1004, "sample.not-found", "NOT-1");
        assertBusinessCodeException(CocoBusinessExceptions.conflictWithCause(TestBusinessCode.CONFLICT, cause,
                "CONFLICT-1"), cause, 1005, "sample.conflict", "CONFLICT-1");
        assertBusinessCodeException(CocoBusinessExceptions.systemWithCause(TestBusinessCode.INTERNAL_ERROR, cause,
                "SYSTEM-1"), cause, 1006, "sample.internal-error", "SYSTEM-1");
    }

    @Test
    void staticMessageCodeWithCauseFactoriesPreserveCauseAndArguments() {
        IllegalStateException cause = new IllegalStateException("boom");

        assertMessageCodeException(CocoBusinessExceptions.requestWithCause("sample.request", cause, "REQ-1"),
                cause, "sample.request", "REQ-1");
        assertMessageCodeException(CocoBusinessExceptions.unauthorizedWithCause("sample.unauthorized", cause, "AUTH-1"),
                cause, "sample.unauthorized", "AUTH-1");
        assertMessageCodeException(CocoBusinessExceptions.forbiddenWithCause("sample.forbidden", cause, "FORBIDDEN-1"),
                cause, "sample.forbidden", "FORBIDDEN-1");
        assertMessageCodeException(CocoBusinessExceptions.notFoundWithCause("sample.not-found", cause, "NOT-1"),
                cause, "sample.not-found", "NOT-1");
        assertMessageCodeException(CocoBusinessExceptions.conflictWithCause("sample.conflict", cause, "CONFLICT-1"),
                cause, "sample.conflict", "CONFLICT-1");
        assertMessageCodeException(CocoBusinessExceptions.systemWithCause("sample.system", cause, "SYSTEM-1"),
                cause, "sample.system", "SYSTEM-1");
    }

    @Test
    void businessCodeWithCauseMethodsPreserveCauseAndArguments() {
        IllegalStateException cause = new IllegalStateException("boom");

        assertBusinessCodeException(TestBusinessCode.ORDER_NOT_FOUND.requestWithCause(cause, "REQ-1"), cause,
                1001, "sample.order.not-found", "REQ-1");
        assertBusinessCodeException(TestBusinessCode.UNAUTHORIZED.unauthorizedWithCause(cause, "AUTH-1"), cause,
                1002, "sample.unauthorized", "AUTH-1");
        assertBusinessCodeException(TestBusinessCode.FORBIDDEN.forbiddenWithCause(cause, "FORBIDDEN-1"), cause,
                1003, "sample.forbidden", "FORBIDDEN-1");
        assertBusinessCodeException(TestBusinessCode.NOT_FOUND.notFoundWithCause(cause, "NOT-1"), cause,
                1004, "sample.not-found", "NOT-1");
        assertBusinessCodeException(TestBusinessCode.CONFLICT.conflictWithCause(cause, "CONFLICT-1"), cause,
                1005, "sample.conflict", "CONFLICT-1");
        assertBusinessCodeException(TestBusinessCode.INTERNAL_ERROR.systemWithCause(cause, "SYSTEM-1"), cause,
                1006, "sample.internal-error", "SYSTEM-1");
    }

    @Test
    void createsTypedExceptionFromBusinessCodeContract() {
        CocoNotFoundException exception = CocoBusinessExceptions.notFound(TestBusinessCode.ORDER_NOT_FOUND, "ORD-1001");

        assertEquals(1001, exception.businessCode().orElseThrow());
        assertEquals("sample.order.not-found", exception.code());
        assertEquals("sample.order.not-found", exception.defaultMessage());
        assertArrayEquals(new Object[] {"ORD-1001"}, exception.args());
    }

    @Test
    void createsTypedExceptionFromMessageCodeWithoutBusinessCode() {
        CocoNotFoundException exception = CocoBusinessExceptions.notFound("sample.order.not-found", "ORD-1001");

        assertTrue(exception.businessCode().isEmpty());
        assertEquals("sample.order.not-found", exception.code());
        assertEquals("sample.order.not-found", exception.defaultMessage());
        assertArrayEquals(new Object[] {"ORD-1001"}, exception.args());
    }

    @Test
    void commonErrorCodeDefaultMessagesUseCodeFallbackOnly() {
        for (CocoCommonErrorCode errorCode : CocoCommonErrorCode.values()) {
            assertEquals(errorCode.code(), errorCode.defaultMessage());
        }
    }

    @Test
    void exceptionModuleDoesNotExposeMessageGuardSupportPackage() {
        try {
            Class.forName("io.github.coco.exception.support.CocoExceptionGuards");
            fail("exception module must not own i18n guard support");
        }
        catch (ClassNotFoundException ex) {
            assertEquals("io.github.coco.exception.support.CocoExceptionGuards", ex.getMessage());
        }
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
        CocoRequestException exception = assertThrows(CocoRequestException.class,
                () -> new CocoException(" ", "默认消息"));

        assertEquals("coco.error.missing-message-code", exception.code());
        assertEquals("coco.error.missing-message-code", exception.defaultMessage());
    }

    @Test
    void rejectsNullErrorCodeWithCocoRequestException() {
        CocoRequestException exception = assertThrows(CocoRequestException.class,
                () -> new CocoConflictException((CocoErrorCode) null));

        assertEquals("coco.error.missing-error-code", exception.code());
        assertEquals("coco.error.missing-error-code", exception.defaultMessage());
    }

    @Test
    void legacyConstructorsRetainPublicDescriptors() throws NoSuchMethodException {
        assertNotNull(CocoException.class.getConstructor(CocoErrorCode.class, Object[].class));
        assertNotNull(CocoException.class.getConstructor(CocoErrorCode.class, Throwable.class, Object[].class));
        assertNotNull(CocoException.class.getConstructor(CocoBusinessCode.class, Object[].class));
        assertNotNull(CocoException.class.getConstructor(CocoBusinessCode.class, Throwable.class, Object[].class));
        assertNotNull(CocoException.class.getConstructor(String.class));
        assertNotNull(CocoException.class.getConstructor(String.class, String.class));
        assertNotNull(CocoException.class.getConstructor(String.class, String.class, Object[].class));
        assertNotNull(CocoException.class.getConstructor(String.class, String.class, Throwable.class));
        assertNotNull(CocoException.class.getConstructor(String.class, String.class, Throwable.class, Object[].class));
    }

    @Test
    void constructorsRetainFailFastValidationForInvalidInputs() {
        assertMissingErrorCode(() -> new CocoException((CocoErrorCode) null));
        assertMissingErrorCode(() -> new CocoException((CocoErrorCode) null, new IllegalStateException("cause")));
        assertMissingErrorCode(() -> new CocoException((CocoBusinessCode) null));
        assertMissingErrorCode(() -> new CocoException((CocoBusinessCode) null, new IllegalStateException("cause")));
        assertMissingMessageCode(() -> new CocoException(" ", "default"));
        assertMissingMessageCode(() -> new CocoException(" ", "default", new IllegalStateException("cause")));
    }

    @Test
    void staticFactoryRejectsNullErrorCodeWithCocoRequestException() {
        CocoRequestException exception = assertThrows(CocoRequestException.class,
                () -> CocoExceptions.conflict(null));

        assertEquals("coco.error.missing-error-code", exception.code());
        assertEquals("coco.error.missing-error-code", exception.defaultMessage());
    }

    @Test
    void protectsArgumentsFromExternalMutation() {
        Object[] args = new Object[] {"before"};
        CocoException exception = new CocoException("coco.error.invalid-argument", "参数不合法", args);

        args[0] = "after";

        assertArrayEquals(new Object[] {"before"}, exception.args());
    }

    @Test
    void exportsMessageDescriptorForI18nResolution() {
        CocoException exception = new CocoException("coco.error.invalid-argument", "参数 {0} 不合法", "name");

        CocoMessage message = exception.message();

        assertEquals("coco.error.invalid-argument", message.code());
        assertEquals("参数 {0} 不合法", message.defaultMessage());
        assertArrayEquals(new Object[] {"name"}, message.args());
    }

    @Test
    void serializesAndRestoresExceptionState() throws IOException, ClassNotFoundException {
        IllegalStateException cause = new IllegalStateException("database unavailable");
        CocoException exception = new CocoException(TestBusinessCode.ORDER_NOT_FOUND, cause, "ORD-1001");

        CocoException restored = deserialize(serialize(exception));

        assertEquals(1001, restored.businessCode().orElseThrow());
        assertEquals("sample.order.not-found", restored.code());
        assertEquals("sample.order.not-found", restored.defaultMessage());
        assertArrayEquals(new Object[] {"ORD-1001"}, restored.args());
        assertInstanceOf(IllegalStateException.class, restored.getCause());
        assertEquals("database unavailable", restored.getCause().getMessage());
    }

    private static void assertMissingErrorCode(ThrowingRunnable invocation) {
        CocoRequestException exception = assertThrows(CocoRequestException.class, invocation::run);
        assertEquals("coco.error.missing-error-code", exception.code());
    }

    private static void assertErrorCodeException(CocoException exception, Throwable cause, Object... args) {
        assertSame(cause, exception.getCause());
        assertArrayEquals(args, exception.args());
    }

    private static void assertThrowableMessageArgument(CocoException exception, Throwable argument) {
        assertNull(exception.getCause());
        assertArrayEquals(new Object[] {argument}, exception.args());
    }

    private static void assertBusinessCodeException(CocoException exception, Throwable cause, int businessCode,
            String messageCode, Object... args) {
        assertSame(cause, exception.getCause());
        assertEquals(businessCode, exception.businessCode().orElseThrow());
        assertEquals(messageCode, exception.code());
        assertEquals(messageCode, exception.defaultMessage());
        assertArrayEquals(args, exception.args());
    }

    private static void assertMessageCodeException(CocoException exception, Throwable cause, String messageCode,
            Object... args) {
        assertSame(cause, exception.getCause());
        assertTrue(exception.businessCode().isEmpty());
        assertEquals(messageCode, exception.code());
        assertEquals(messageCode, exception.defaultMessage());
        assertArrayEquals(args, exception.args());
    }

    private static void assertMissingMessageCode(ThrowingRunnable invocation) {
        CocoRequestException exception = assertThrows(CocoRequestException.class, invocation::run);
        assertEquals("coco.error.missing-message-code", exception.code());
    }

    private static byte[] serialize(CocoException exception) throws IOException {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(exception);
            return bytes.toByteArray();
        }
    }

    private static CocoException deserialize(byte[] serialized) throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return (CocoException) input.readObject();
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {

        void run();
    }

    private enum TestBusinessCode implements CocoBusinessCode {

        ORDER_NOT_FOUND(1001, "sample.order.not-found"),

        UNAUTHORIZED(1002, "sample.unauthorized"),

        FORBIDDEN(1003, "sample.forbidden"),

        NOT_FOUND(1004, "sample.not-found"),

        CONFLICT(1005, "sample.conflict"),

        INTERNAL_ERROR(1006, "sample.internal-error");

        private final int code;

        private final String messageCode;

        TestBusinessCode(int code, String messageCode) {
            this.code = code;
            this.messageCode = messageCode;
        }

        @Override
        public int code() {
            return this.code;
        }

        @Override
        public String messageCode() {
            return this.messageCode;
        }
    }
}
