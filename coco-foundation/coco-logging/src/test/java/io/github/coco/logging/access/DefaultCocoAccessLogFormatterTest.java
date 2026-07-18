package io.github.coco.logging.access;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 *   <li>模块：{@code coco-logging}</li>
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

    @Test
    void escapesControlCharactersAtTextFormattingBoundary() {
        CocoAccessLog accessLog = CocoAccessLog.of("trace-\u001b-value\u0085\u2028\u2029\ud800high\udc00low", "get",
                "/orders\r\nforged\t\u0000tail", 500, 7L, false, "Failure\r\nforged\u007f", "10.0.0.8", "source\tvalue",
                "Agent\r\nforged", "text/plain", "name=line\nbreak&tab=\t&escape=\u001bend",
                Map.of("x-test\r\nname", "line1\nline2\t\u0001end"),
                null, null, null, null, null, null,
                Map.of("unsafe\rname", List.of("value\nforged\t\u001bend")));
        DefaultCocoAccessLogFormatter formatter = new DefaultCocoAccessLogFormatter();

        String text = normalize(formatter.format(accessLog, new CocoAccessLogProperties()));

        assertTrue(text.contains("trace-\\u001b-value\\u0085\\u2028\\u2029\\ud800high\\udc00low"));
        assertTrue(text.contains("/orders\\r\\nforged\\t\\u0000tail?name=line\\nbreak&tab=\\t&escape=\\u001bend"),
                text);
        assertTrue(text.contains("x-test\\r\\nname=line1\\nline2\\t\\u0001end"));
        assertTrue(text.contains("unsafe\\rname=value\\nforged\\t\\u001bend"));
        assertTrue(text.contains("Failure\\r\\nforged\\u007f"));
        assertFalse(text.contains("\nforged"));
        assertFalse(text.contains("\t"));
        assertFalse(text.contains("\u0000"));
        assertFalse(text.contains("\u0001"));
        assertFalse(text.contains("\u001b"));
        assertFalse(text.contains("\u007f"));
        assertFalse(text.contains("\u0085"));
        assertFalse(text.contains("\u2028"));
        assertFalse(text.contains("\u2029"));
        assertFalse(text.contains("\ud800high"));
        assertFalse(text.contains("\udc00low"));
    }

    @Test
    void keepsJsonEscapingSingleAndEscapesControlCharactersAndTextSeparators() {
        CocoAccessLog accessLog = CocoAccessLog.of("trace-json", "get",
                "/orders\r\nnext\t\u0001\u0085\u2028\u2029\ud800high\udc00low",
                200, 1L, true, null);
        CocoAccessLogProperties properties = new CocoAccessLogProperties();
        properties.setStyle(CocoAccessLogStyle.JSON);
        DefaultCocoAccessLogFormatter formatter = new DefaultCocoAccessLogFormatter();

        String json = formatter.format(accessLog, properties);

        assertTrue(json.contains("\"path\":\"/orders\\r\\nnext\\t\\u0001\\u0085\\u2028\\u2029\\ud800high\\udc00low\""));
        assertFalse(json.contains("/orders\\\\r\\\\nnext"));
        assertFalse(json.contains("\r"));
        assertFalse(json.contains("\n"));
        assertFalse(json.contains("\t"));
        assertFalse(json.contains("\u0001"));
        assertFalse(json.contains("\u0085"));
        assertFalse(json.contains("\u2028"));
        assertFalse(json.contains("\u2029"));
        assertFalse(json.contains("\ud800high"));
        assertFalse(json.contains("\udc00low"));
    }

    private static String normalize(String value) {
        return value == null ? null : value.replace("\r\n", "\n");
    }
}
