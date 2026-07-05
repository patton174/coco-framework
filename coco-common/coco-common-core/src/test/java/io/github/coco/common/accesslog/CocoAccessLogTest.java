package io.github.coco.common.accesslog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Coco 接口访问日志测试。
 * <p>
 * 验证接口访问日志事件可以保存请求上下文、响应状态、耗时和异常类型，供审计模块或业务侧 recorder 统一处理。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-common-core}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class CocoAccessLogTest {

    @Test
    void createsAccessLogWithNormalizedRequestInformation() {
        CocoAccessLog accessLog = CocoAccessLog.of(" trace-001 ", " post ", " /api/users ", 201, 12L,
                true, null, " 10.0.0.8 ", " PostmanRuntime/7.37 ", " name=Coco ",
                Map.of("name", List.of(" Coco "), "empty", List.of(" ")));

        assertEquals("trace-001", accessLog.traceId());
        assertEquals("POST", accessLog.method().orElseThrow());
        assertEquals("/api/users", accessLog.path().orElseThrow());
        assertEquals("10.0.0.8", accessLog.clientIp().orElseThrow());
        assertEquals("PostmanRuntime/7.37", accessLog.userAgent().orElseThrow());
        assertEquals("name=Coco", accessLog.queryString().orElseThrow());
        assertEquals(List.of("Coco"), accessLog.requestParameters().get("name"));
        assertEquals(List.of(""), accessLog.requestParameters().get("empty"));
        assertEquals(201, accessLog.status());
        assertEquals(12L, accessLog.durationMillis());
        assertTrue(accessLog.success());
        assertTrue(accessLog.exceptionType().isEmpty());
    }

    @Test
    void createsFailedAccessLogWithExceptionType() {
        CocoAccessLog accessLog = CocoAccessLog.of("trace-001", "GET", "/api/users", 500, 5L,
                false, "jakarta.servlet.ServletException");

        assertFalse(accessLog.success());
        assertEquals("jakarta.servlet.ServletException", accessLog.exceptionType().orElseThrow());
    }

    @Test
    void rejectsBlankTraceId() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> CocoAccessLog.of(" ", "GET", "/api/users", 200, 1L, true, null));

        assertEquals("traceId must not be blank", exception.getMessage());
    }

    @Test
    void rejectsNegativeDuration() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> CocoAccessLog.of("trace-001", "GET", "/api/users", 200, -1L, true, null));

        assertEquals("durationMillis must not be negative", exception.getMessage());
    }
}
