package io.github.coco.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static java.util.Map.entry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.coco.context.trace.CocoTraceContext;
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
 *   <li>模块：{@code coco-context}</li>
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
                " trace-001 ", " get ", " /api/users ", Map.ofEntries(
                        entry("tenantId", " tenant-a "),
                        entry(CocoRequestContextAttributes.CLIENT_IP, " 10.0.0.8 "),
                        entry(CocoRequestContextAttributes.CLIENT_IP_SOURCE, " FORWARDED_HEADER "),
                        entry(CocoRequestContextAttributes.CLIENT_IP_SOURCE_HEADER, " X-Forwarded-For "),
                        entry(CocoRequestContextAttributes.CLIENT_IP_CHAIN, " 10.0.0.8,10.0.0.9 "),
                        entry(CocoRequestContextAttributes.CLIENT_IP_REMOTE_ADDRESS, " 127.0.0.1 "),
                        entry(CocoRequestContextAttributes.CLIENT_IP_TRUSTED_PROXY, " true "),
                        entry(CocoRequestContextAttributes.USER_AGENT, " PostmanRuntime/7.37 "),
                        entry(CocoRequestContextAttributes.QUERY_STRING, " name=Coco "),
                        entry(CocoRequestContextAttributes.LOCALE, " zh-CN "),
                        entry(CocoRequestContextAttributes.SCHEME, " https "),
                        entry(CocoRequestContextAttributes.HOST, " api.coco.dev "),
                        entry(CocoRequestContextAttributes.PORT, " 8443 "),
                        entry(CocoRequestContextAttributes.CONTENT_TYPE, " application/json "),
                        entry(CocoRequestContextAttributes.BROWSER_FINGERPRINT, " fp-001 "),
                        entry(CocoRequestContextAttributes.browserFingerprintSignal("Sec-CH-UA"),
                                " \"Chromium\" "),
                        entry(CocoRequestContextAttributes.REQUEST_BODY_SHA256, " body-sha-001 "),
                        entry(CocoRequestContextAttributes.REQUEST_BODY_TRANSPORT_SHA256, " transport-sha-001 "),
                        entry(CocoRequestContextAttributes.REQUEST_BODY_EFFECTIVE_SHA256, " effective-sha-001 "),
                        entry(CocoRequestContextAttributes.REQUEST_BODY_TRANSPORT_LENGTH, " 256 "),
                        entry(CocoRequestContextAttributes.REQUEST_BODY_EFFECTIVE_LENGTH, " 128 "),
                        entry(CocoRequestContextAttributes.REQUEST_BODY_STAGE, " decrypted "),
                        entry(CocoRequestContextAttributes.SECURITY_APP_ID, " app-001 "),
                        entry(CocoRequestContextAttributes.SECURITY_KEY_ID, " key-001 "),
                        entry(CocoRequestContextAttributes.REQUEST_SIGNED, " true "),
                        entry(CocoRequestContextAttributes.REQUEST_ENCRYPTED, " true "),
                        entry(CocoRequestContextAttributes.REQUEST_REPLAY_PROTECTED, " true "),
                        entry(CocoRequestContextAttributes.SIGNATURE_ALGORITHM, " HMAC-SHA256 "),
                        entry(CocoRequestContextAttributes.ENCRYPTION_ALGORITHM, " AES-GCM "),
                        entry(CocoRequestContextAttributes.header("Accept-Language"), " zh-CN "),
                        entry(CocoRequestContextAttributes.cookie("COCO_TRACE"), " trace-cookie "),
                        entry(CocoRequestContextAttributes.parameter("name"), " Coco "),
                        entry(CocoRequestContextAttributes.queryParameter("q"), " web "),
                        entry(CocoRequestContextAttributes.payloadParameter("sku"), " COCO-STARTER ")));

        CocoRequestContextHolder.set(context);

        CocoRequestContext current = CocoRequestContextHolder.current().orElseThrow();
        assertEquals("trace-001", current.traceId());
        assertEquals("GET", current.method().orElseThrow());
        assertEquals("/api/users", current.path().orElseThrow());
        assertEquals("tenant-a", current.attribute("tenantId").orElseThrow());
        assertEquals("10.0.0.8", current.clientIp().orElseThrow());
        assertEquals("FORWARDED_HEADER", current.clientIpSource().orElseThrow());
        assertEquals("X-Forwarded-For", current.clientIpSourceHeader().orElseThrow());
        assertEquals("127.0.0.1", current.clientIpRemoteAddress().orElseThrow());
        assertTrue(current.clientIpTrustedProxy());
        assertEquals(List.of("10.0.0.8", "10.0.0.9"), current.clientIpChain());
        assertEquals("PostmanRuntime/7.37", current.userAgent().orElseThrow());
        assertEquals("name=Coco", current.queryString().orElseThrow());
        assertEquals("zh-CN", current.locale().orElseThrow());
        assertEquals("https", current.scheme().orElseThrow());
        assertEquals("api.coco.dev", current.host().orElseThrow());
        assertEquals(8443, current.port().orElseThrow());
        assertEquals("application/json", current.contentType().orElseThrow());
        assertEquals("fp-001", current.browserFingerprint().orElseThrow());
        assertEquals("\"Chromium\"", current.browserFingerprintSignal("sec-ch-ua").orElseThrow());
        assertEquals(Map.of("sec-ch-ua", "\"Chromium\""), current.browserFingerprintSignals());
        assertEquals("body-sha-001", current.requestBodySha256().orElseThrow());
        assertEquals("transport-sha-001", current.requestBodyTransportSha256().orElseThrow());
        assertEquals("effective-sha-001", current.requestBodyEffectiveSha256().orElseThrow());
        assertEquals(256L, current.requestBodyTransportLength().orElseThrow());
        assertEquals(128L, current.requestBodyEffectiveLength().orElseThrow());
        assertEquals("decrypted", current.requestBodyStage().orElseThrow());
        assertFalse(current.securityAppId().isPresent());
        assertFalse(current.securityKeyId().isPresent());
        assertTrue(current.requestSigned());
        assertTrue(current.requestEncrypted());
        assertTrue(current.requestReplayProtected());
        assertEquals("HMAC-SHA256", current.signatureAlgorithm().orElseThrow());
        assertEquals("AES-GCM", current.encryptionAlgorithm().orElseThrow());
        assertEquals("zh-CN", current.header("accept-language").orElseThrow());
        assertEquals(Map.of("accept-language", "zh-CN"), current.headers());
        assertEquals("trace-cookie", current.cookie("COCO_TRACE").orElseThrow());
        assertEquals(Map.of("COCO_TRACE", "trace-cookie"), current.cookies());
        assertEquals("Coco", current.parameter("name").orElseThrow());
        assertEquals(List.of("Coco"), current.parameterValues("name").orElseThrow());
        assertEquals(Map.of("name", List.of("Coco")), current.parameters());
        assertEquals("web", current.queryParameter("q").orElseThrow());
        assertEquals(List.of("web"), current.queryParameterValues("q").orElseThrow());
        assertEquals(Map.of("q", List.of("web")), current.queryParameters());
        assertEquals("COCO-STARTER", current.payloadParameter("sku").orElseThrow());
        assertEquals(List.of("COCO-STARTER"), current.payloadParameterValues("sku").orElseThrow());
        assertEquals(Map.of("sku", List.of("COCO-STARTER")), current.payloadParameters());
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
    void capturedRequestContextWrapsCallableAcrossThreads() throws Exception {
        CocoRequestContextHolder.set(CocoRequestContext.of("captured", "GET", "/captured"));
        Callable<String> callable = CocoRequestContextHolder.wrap(() -> {
            CocoRequestContext current = CocoRequestContextHolder.current().orElseThrow();
            assertEquals("captured", CocoTraceContext.currentTraceId().orElseThrow());
            return current.traceId() + ":" + current.path().orElseThrow();
        });
        CocoRequestContextHolder.set(CocoRequestContext.of("main", "GET", "/main"));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            assertEquals("captured:/captured", executor.submit(callable).get());
            assertTrue(executor.submit(() -> CocoRequestContextHolder.current().isEmpty()
                    && CocoTraceContext.currentTraceId().isEmpty()).get());
        }
        finally {
            executor.shutdownNow();
        }
        assertEquals("main", CocoRequestContextHolder.current().orElseThrow().traceId());
        assertEquals("main", CocoTraceContext.currentTraceId().orElseThrow());
    }

    @Test
    void composedSnapshotsCloseInReverseOrder() {
        List<String> events = new ArrayList<>();
        CocoContextSnapshot first = () -> {
            events.add("first-open");
            return () -> events.add("first-close");
        };
        CocoContextSnapshot second = () -> {
            events.add("second-open");
            return () -> events.add("second-close");
        };

        try (CocoContextScope ignored = CocoContextSnapshot.compose(first, second).restore()) {
            events.add("body");
        }

        assertEquals(List.of("first-open", "second-open", "body", "second-close", "first-close"), events);
    }

    @Test
    void requestContextIgnoresInvalidRequestBodyLength() {
        CocoRequestContext context = CocoRequestContext.of("trace-001", "POST", "/api/users", Map.of(
                CocoRequestContextAttributes.REQUEST_BODY_TRANSPORT_LENGTH, "NaN",
                CocoRequestContextAttributes.REQUEST_BODY_EFFECTIVE_LENGTH, "broken"));

        assertTrue(context.requestBodyTransportLength().isEmpty());
        assertTrue(context.requestBodyEffectiveLength().isEmpty());
    }

    @Test
    void requestContextRejectsBlankTraceId() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> CocoRequestContext.of(" ", "GET", "/api/users"));

        assertEquals("traceId must not be blank", exception.getMessage());
    }

    @Test
    void requestContextPreservesStructuredMultiValueAttributes() {
        CocoRequestContext context = CocoRequestContext.of("trace-structured", "POST", "/api/orders", Map.of(
                CocoRequestContextAttributes.parameter("tags"),
                CocoRequestContextValueCodec.encodeList(List.of("web,sign", "aes|gcm")),
                CocoRequestContextAttributes.CLIENT_IP_CHAIN,
                CocoRequestContextValueCodec.encodeList(List.of("2001:db8::1", "10.0.0.8")),
                "businessTag", "orders"));

        assertEquals("orders", context.attribute("businessTag").orElseThrow());
        assertEquals("web,sign,aes|gcm", context.parameter("tags").orElseThrow());
        assertEquals(List.of("web,sign", "aes|gcm"), context.parameterValues("tags").orElseThrow());
        assertEquals(List.of("2001:db8::1", "10.0.0.8"), context.clientIpChain());
    }
}
