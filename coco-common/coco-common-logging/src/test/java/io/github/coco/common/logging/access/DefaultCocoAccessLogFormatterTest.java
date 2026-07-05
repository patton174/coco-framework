package io.github.coco.common.logging.access;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Coco 默认访问日志格式化器测试。
 * <p>
 * 验证访问日志使用请求与响应方向箭头输出，并包含请求参数、客户端 IP 和 User-Agent 等关键排障字段。
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
    void formatsTextWithRequestAndResponseArrows() {
        CocoAccessLog accessLog = CocoAccessLog.of("trace-1001", "post", "/sample/orders",
                201, 42L, true, null, "10.0.0.8", "PostmanRuntime/7.37",
                "sku=COCO-STARTER&token=******",
                Map.of("sku", List.of("COCO-STARTER"), "token", List.of("******")));
        CocoAccessLogProperties properties = new CocoAccessLogProperties();
        DefaultCocoAccessLogFormatter formatter = new DefaultCocoAccessLogFormatter();

        assertEquals("▸ request  POST /sample/orders?sku=COCO-STARTER&token=****** | trace=trace-1001 "
                        + "ip=10.0.0.8 ua=\"PostmanRuntime/7.37\" params=\"sku=COCO-STARTER&token=******\" "
                        + "◂ response 201 42ms success=true",
                formatter.format(accessLog, properties));
    }

    @Test
    void formatsJsonWithoutArrowDecoration() {
        CocoAccessLog accessLog = CocoAccessLog.of("trace-1001", "get", "/sample/products",
                200, 12L, true, null, "10.0.0.8", "CodexCheck/1.0", null, Map.of());
        CocoAccessLogProperties properties = new CocoAccessLogProperties();
        properties.setStyle(CocoAccessLogStyle.JSON);
        DefaultCocoAccessLogFormatter formatter = new DefaultCocoAccessLogFormatter();

        assertEquals("{\"traceId\":\"trace-1001\",\"method\":\"GET\",\"path\":\"/sample/products\","
                        + "\"clientIp\":\"10.0.0.8\",\"queryString\":\"\",\"parameters\":{},"
                        + "\"userAgent\":\"CodexCheck/1.0\",\"status\":200,\"durationMs\":12,\"success\":true}",
                formatter.format(accessLog, properties));
    }
}
