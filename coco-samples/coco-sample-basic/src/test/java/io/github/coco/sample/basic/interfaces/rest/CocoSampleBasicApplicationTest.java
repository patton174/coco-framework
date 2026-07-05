package io.github.coco.sample.basic.interfaces.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coco.common.accesslog.CocoAccessLog;
import io.github.coco.common.accesslog.CocoAccessLogRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Coco 示例业务应用集成测试。
 * <p>
 * 通过商品查询、创建订单和订单查询验证业务项目接入 Coco Starter 后的统一响应、统一异常、请求追踪和访问日志扩展点。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-sample-basic}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class CocoSampleBasicApplicationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestAccessLogRecorder accessLogRecorder;

    private ObjectMapper objectMapper;

    private HttpClient httpClient;

    /**
     * <p>
     * 初始化 HTTP 客户端，并清理上一个用例留下的测试访问日志快照。
     * </p>
     */
    @BeforeEach
    void setUp() {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.accessLogRecorder.clear();
    }

    /**
     * <p>
     * 商品查询接口返回值会被 Coco 包装为统一成功响应。
     * </p>
     * @throws Exception HTTP 调用或 JSON 解析失败时抛出
     */
    @Test
    void listsProductsWithUnifiedResponse() throws Exception {
        SampleHttpResponse response = get("/sample/products", "products-trace");

        assertEquals(200, response.status());
        assertEquals("products-trace", response.header("X-Trace-Id"));
        assertTrue(response.body().path("success").booleanValue());
        assertEquals("coco.success", response.body().path("code").textValue());
        assertEquals("操作成功", response.body().path("message").textValue());
        assertEquals("products-trace", response.body().path("traceId").textValue());
        assertEquals("/sample/products", response.body().path("path").textValue());
        assertEquals("COCO-STARTER", response.body().path("data").get(0).path("sku").textValue());
        assertEquals(5, response.body().path("data").get(0).path("availableStock").intValue());
    }

    /**
     * <p>
     * 下单接口会扣减库存并允许按订单编号查询订单详情。
     * </p>
     * @throws Exception HTTP 调用或 JSON 解析失败时抛出
     */
    @Test
    void createsOrderAndLoadsOrderDetail() throws Exception {
        SampleHttpResponse created = post("/sample/orders", "create-order-trace",
                Map.of("buyerName", "Patton", "sku", "COCO-STARTER", "quantity", 2));

        assertEquals(200, created.status());
        assertTrue(created.body().path("success").booleanValue());
        assertEquals("ORD-1001", created.body().path("data").path("orderId").textValue());
        assertEquals("CREATED", created.body().path("data").path("status").textValue());
        assertEquals(19800, created.body().path("data").path("totalAmount").longValue());
        assertEquals(3, created.body().path("data").path("remainingStock").intValue());

        SampleHttpResponse loaded = get("/sample/orders/ORD-1001", "load-order-trace");

        assertEquals(200, loaded.status());
        assertTrue(loaded.body().path("success").booleanValue());
        assertEquals("ORD-1001", loaded.body().path("data").path("orderId").textValue());
        assertEquals("Patton", loaded.body().path("data").path("buyerName").textValue());
    }

    /**
     * <p>
     * 库存不足会进入 Coco Web 全局异常处理器，并返回国际化后的统一错误响应。
     * </p>
     * @throws Exception HTTP 调用或 JSON 解析失败时抛出
     */
    @Test
    void returnsUnifiedStockErrorResponse() throws Exception {
        SampleHttpResponse response = post("/sample/orders", "stock-error-trace", "zh-CN",
                Map.of("buyerName", "Patton", "sku", "COCO-STARTER", "quantity", 99));

        assertEquals(409, response.status());
        assertFalse(response.body().path("success").booleanValue());
        assertEquals("sample.order.insufficient-stock", response.body().path("code").textValue());
        assertEquals("商品 COCO-STARTER 库存不足，当前库存 5，请求数量 99",
                response.body().path("message").textValue());
        assertEquals("stock-error-trace", response.body().path("traceId").textValue());
        assertEquals("/sample/orders", response.body().path("path").textValue());

        SampleHttpResponse englishResponse = post("/sample/orders", "stock-error-en-trace", "en-US",
                Map.of("buyerName", "Patton", "sku", "COCO-STARTER", "quantity", 99));

        assertEquals(409, englishResponse.status());
        assertFalse(englishResponse.body().path("success").booleanValue());
        assertEquals("sample.order.insufficient-stock", englishResponse.body().path("code").textValue());
        assertEquals("Product COCO-STARTER has insufficient stock, current stock 5, requested quantity 99",
                englishResponse.body().path("message").textValue());
        assertEquals("stock-error-en-trace", englishResponse.body().path("traceId").textValue());
        assertEquals("/sample/orders", englishResponse.body().path("path").textValue());
    }

    /**
     * <p>
     * 测试作用域访问日志记录器可以拿到业务请求完成后的访问日志。
     * </p>
     * @throws Exception HTTP 调用或 JSON 解析失败时抛出
     */
    @Test
    void recordsAccessLogThroughBusinessRequest() throws Exception {
        SampleHttpResponse response = get("/sample/products", "access-trace");

        assertEquals(200, response.status());
        CocoAccessLog accessLog = this.accessLogRecorder.latest()
                .orElseThrow(() -> new AssertionError("Access log was not recorded."));
        assertEquals("access-trace", accessLog.traceId());
        assertEquals("GET", accessLog.method().orElse(null));
        assertEquals("/sample/products", accessLog.path().orElse(null));
        assertEquals(200, accessLog.status());
        assertTrue(accessLog.success());
    }

    /**
     * <p>
     * 发送 GET 请求并解析 JSON 响应。
     * </p>
     * @param path 请求路径和查询参数
     * @param traceId 请求 TraceId
     * @return HTTP 响应快照
     * @throws Exception 请求失败或 JSON 解析失败时抛出
     */
    private SampleHttpResponse get(String path, String traceId) throws Exception {
        HttpRequest request = baseRequest(path, traceId)
                .GET()
                .build();
        return send(request);
    }

    /**
     * <p>
     * 发送 POST JSON 请求并解析 JSON 响应。
     * </p>
     * @param path 请求路径
     * @param traceId 请求 TraceId
     * @param body JSON 请求体对象
     * @return HTTP 响应快照
     * @throws Exception 请求失败或 JSON 解析失败时抛出
     */
    private SampleHttpResponse post(String path, String traceId, Object body) throws Exception {
        HttpRequest request = baseRequest(path, traceId)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(this.objectMapper.writeValueAsString(body)))
                .build();
        return send(request);
    }

    /**
     * <p>
     * 发送带语言请求头的 POST JSON 请求，并解析 JSON 响应。
     * </p>
     * @param path 请求路径
     * @param traceId 请求 TraceId
     * @param locale {@code Accept-Language} 请求头
     * @param body JSON 请求体对象
     * @return HTTP 响应快照
     * @throws Exception 请求失败或 JSON 解析失败时抛出
     */
    private SampleHttpResponse post(String path, String traceId, String locale, Object body) throws Exception {
        HttpRequest request = baseRequest(path, traceId)
                .header("Accept-Language", locale)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(this.objectMapper.writeValueAsString(body)))
                .build();
        return send(request);
    }

    private HttpRequest.Builder baseRequest(String path, String traceId) {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + this.port + path))
                .header("Accept", "application/json");
        if (traceId != null && !traceId.isBlank()) {
            requestBuilder.header("X-Trace-Id", traceId);
        }
        return requestBuilder;
    }

    private SampleHttpResponse send(HttpRequest request) throws Exception {
        HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return new SampleHttpResponse(response.statusCode(), response.headers(),
                this.objectMapper.readTree(response.body()));
    }

    private record SampleHttpResponse(int status, HttpHeaders headers, JsonNode body) {

        /**
         * <p>
         * 读取第一个响应头值。
         * </p>
         * @param name 响应头名称
         * @return 响应头值
         */
        private String header(String name) {
            return this.headers.firstValue(name).orElse(null);
        }
    }

    /**
     * <p>
     * 示例集成测试专用配置。
     * </p>
     * <p>
     * 业务示例主代码不声明访问日志记录器；这里仅在测试作用域注册探针，用于验证 Coco Web
     * 过滤器确实发布了访问日志事件。
     * </p>
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class AccessLogTestConfiguration {

        /**
         * <p>
         * 创建测试作用域访问日志记录器。
         * </p>
         * @return 测试访问日志记录器
         */
        @Bean
        TestAccessLogRecorder testAccessLogRecorder() {
            return new TestAccessLogRecorder();
        }
    }

    /**
     * <p>
     * 测试作用域访问日志记录器。
     * </p>
     * <p>
     * 该类型不属于示例业务代码，只用于测试断言 Coco Web 访问日志扩展点是否被触发。
     * </p>
     */
    static final class TestAccessLogRecorder implements CocoAccessLogRecorder {

        private final AtomicReference<CocoAccessLog> latestAccessLog = new AtomicReference<>();

        /**
         * {@inheritDoc}
         */
        @Override
        public void record(CocoAccessLog accessLog) {
            this.latestAccessLog.set(Objects.requireNonNull(accessLog, "accessLog must not be null"));
        }

        /**
         * <p>
         * 返回最近一次测试访问日志。
         * </p>
         * @return 最近一次测试访问日志；尚未记录时为空
         */
        Optional<CocoAccessLog> latest() {
            return Optional.ofNullable(this.latestAccessLog.get());
        }

        /**
         * <p>
         * 清理最近一次测试访问日志。
         * </p>
         */
        void clear() {
            this.latestAccessLog.set(null);
        }
    }
}
