package io.github.coco.common.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import io.github.coco.common.trace.CocoTraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Coco 请求上下文持有器测试。
 * <p>
 * 验证请求上下文可以保存 TraceId、HTTP 方法、请求路径和扩展属性，并与 Trace 上下文保持同步。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-common-context}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class CocoRequestContextHolderTest {

    @AfterEach
    void clearContext() {
        CocoRequestContextHolder.clear();
    }

    @Test
    void setRequestContextStoresSnapshotAndSynchronizesTraceId() {
        CocoRequestContext context = CocoRequestContext.of(
                " trace-001 ", " get ", " /api/users ", Map.of(
                        "tenantId", " tenant-a ",
                        CocoRequestContextAttributes.CLIENT_IP, " 10.0.0.8 ",
                        CocoRequestContextAttributes.USER_AGENT, " PostmanRuntime/7.37 ",
                        CocoRequestContextAttributes.QUERY_STRING, " name=Coco ",
                        CocoRequestContextAttributes.LOCALE, " zh-CN ",
                        CocoRequestContextAttributes.BROWSER_FINGERPRINT, " fp-001 ",
                        CocoRequestContextAttributes.REQUEST_BODY_SHA256, " body-sha-001 ",
                        CocoRequestContextAttributes.header("Accept-Language"), " zh-CN ",
                        CocoRequestContextAttributes.parameter("name"), " Coco "));

        CocoRequestContextHolder.set(context);

        CocoRequestContext current = CocoRequestContextHolder.current().orElseThrow();
        assertEquals("trace-001", current.traceId());
        assertEquals("GET", current.method().orElseThrow());
        assertEquals("/api/users", current.path().orElseThrow());
        assertEquals("tenant-a", current.attribute("tenantId").orElseThrow());
        assertEquals("10.0.0.8", current.clientIp().orElseThrow());
        assertEquals("PostmanRuntime/7.37", current.userAgent().orElseThrow());
        assertEquals("name=Coco", current.queryString().orElseThrow());
        assertEquals("zh-CN", current.locale().orElseThrow());
        assertEquals("fp-001", current.browserFingerprint().orElseThrow());
        assertEquals("body-sha-001", current.requestBodySha256().orElseThrow());
        assertEquals("zh-CN", current.header("accept-language").orElseThrow());
        assertEquals("Coco", current.parameter("name").orElseThrow());
        assertEquals("trace-001", CocoTraceContext.currentTraceId().orElseThrow());
    }

    @Test
    void clearRemovesRequestContextAndTraceContext() {
        CocoRequestContextHolder.set(CocoRequestContext.of("trace-001", "GET", "/api/users"));

        CocoRequestContextHolder.clear();

        assertTrue(CocoRequestContextHolder.current().isEmpty());
        assertTrue(CocoTraceContext.currentTraceId().isEmpty());
    }

    @Test
    void runWithContextRestoresPreviousContextWhenRunnableThrows() {
        CocoRequestContext outer = CocoRequestContext.of("outer", "GET", "/outer");
        CocoRequestContext inner = CocoRequestContext.of("inner", "POST", "/inner");
        CocoRequestContextHolder.set(outer);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> CocoRequestContextHolder.runWithContext(inner, () -> {
                    assertEquals("inner", CocoRequestContextHolder.current().orElseThrow().traceId());
                    assertEquals("inner", CocoTraceContext.currentTraceId().orElseThrow());
                    throw new IllegalStateException("boom");
                }));

        assertEquals("boom", exception.getMessage());
        assertEquals("outer", CocoRequestContextHolder.current().orElseThrow().traceId());
        assertEquals("outer", CocoTraceContext.currentTraceId().orElseThrow());
    }

    @Test
    void requestContextRejectsBlankTraceId() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> CocoRequestContext.of(" ", "GET", "/api/users"));

        assertEquals("traceId must not be blank", exception.getMessage());
    }
}
