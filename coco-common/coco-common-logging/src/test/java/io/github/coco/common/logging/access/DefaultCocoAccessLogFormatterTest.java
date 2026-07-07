package io.github.coco.common.logging.access;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Coco 默认访问日志格式化器测试。
 * <p>
 * 验证文本与 JSON 两种输出样式。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-common-logging}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class DefaultCocoAccessLogFormatterTest {

    @Test
    void formatsTextWithSectionedRequestAndResponse() {
        CocoAccessLog accessLog = CocoAccessLog.of("trace-1001", "post", "/sample/orders",
                201, 42L, true, null, "10.0.0.8", "10.0.0.8-source", "PostmanRuntime/7.37",
                "application/json", "sku=COCO-STARTER&token=******",
                Map.of("content-type", "application/json"),
                "sha256-1", 128L, "transport", "browser-1", "parsed", "forwarded",
                Map.of("sku", List.of("COCO-STARTER"), "token", List.of("******")));
        CocoAccessLogProperties properties = new CocoAccessLogProperties();
        DefaultCocoAccessLogFormatter formatter = new DefaultCocoAccessLogFormatter();

        assertEquals("▸ request\n"
                        + "  traceId            trace-1001\n"
                        + "  method             POST\n"
                        + "  path               /sample/orders?sku=COCO-STARTER&token=******\n"
                        + "  clientIp           10.0.0.8\n"
                        + "  clientIpSource     10.0.0.8-source\n"
                        + "  userAgent          \"PostmanRuntime/7.37\"\n"
                        + "  contentType        application/json\n"
                        + "  targetSource       forwarded\n"
                        + "  payloadParseStatus parsed\n"
                        + "  browserFingerprint browser-1\n"
                        + "  bodyStage          transport\n"
                        + "  bodyLength         128\n"
                        + "  bodySha256         sha256-1\n"
                        + "  headers            content-type=application/json\n"
                        + "  params             sku=COCO-STARTER&token=******\n"
                        + "◂ response\n"
                        + "  traceId            trace-1001\n"
                        + "  status             201\n"
                        + "  duration           42ms\n"
                        + "  success            true",
                normalize(formatter.format(accessLog, properties)));
    }

    @Test
    void formatsTextEntriesAsIndependentRequestAndResponseMessages() {
        CocoAccessLog accessLog = CocoAccessLog.of("trace-1001", "get", "/sample/products",
                200, 12L, true, null);
        DefaultCocoAccessLogFormatter formatter = new DefaultCocoAccessLogFormatter();

        List<String> entries = formatter.formatEntries(accessLog, new CocoAccessLogProperties());

        assertEquals(2, entries.size());
        assertTrue(entries.get(0).startsWith("▸ request"));
        assertTrue(entries.get(1).startsWith("◂ response"));
    }

    @Test
    void formatsJsonWithExpandedFields() {
        CocoAccessLog accessLog = CocoAccessLog.of("trace-1001", "get", "/sample/products",
                200, 12L, true, null, "10.0.0.8", "10.0.0.8-source", "CodexCheck/1.0",
                "application/json", null, Map.of("x-coco-app-id", "sample-app"),
                "sha256-1", 12L, "transport", "browser-1", "parsed", "forwarded", Map.of());
        CocoAccessLogProperties properties = new CocoAccessLogProperties();
        properties.setStyle(CocoAccessLogStyle.JSON);
        DefaultCocoAccessLogFormatter formatter = new DefaultCocoAccessLogFormatter();

        assertEquals("{\"traceId\":\"trace-1001\",\"method\":\"GET\",\"path\":\"/sample/products\","
                        + "\"clientIp\":\"10.0.0.8\",\"clientIpSource\":\"10.0.0.8-source\","
                        + "\"userAgent\":\"CodexCheck/1.0\",\"contentType\":\"application/json\","
                        + "\"headers\":{\"x-coco-app-id\":\"sample-app\"},"
                        + "\"requestBodySha256\":\"sha256-1\",\"requestBodyLength\":12,"
                        + "\"requestBodyStage\":\"transport\",\"browserFingerprint\":\"browser-1\","
                        + "\"payloadParseStatus\":\"parsed\",\"requestTargetSource\":\"forwarded\","
                        + "\"parameters\":{},\"status\":200,\"durationMs\":12,\"success\":true}",
                normalize(formatter.format(accessLog, properties)));
    }

    private static String normalize(String value) {
        return value == null ? null : value.replace("\r\n", "\n");
    }
}
