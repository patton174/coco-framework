package io.github.coco.sample.basic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coco.common.accesslog.CocoAccessLog;
import io.github.coco.common.accesslog.CocoAccessLogRecorder;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;

/**
 * Coco 基础示例应用集成测试。
 * <p>
 * 验证示例接口可以真实触发 Coco Web 的统一响应、异常处理、请求上下文和访问日志基础设施。
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
     * 普通业务接口返回值会被 Coco 包装为统一成功响应。
     * </p>
     * @throws Exception MockMvc 调用失败时抛出
     */
    @Test
    void wrapsSampleHelloResponse() throws Exception {
        SampleHttpResponse response = get("/sample/hello?name=Coco", "sample-trace");

        assertEquals(200, response.status());
        assertEquals("sample-trace", response.header("X-Trace-Id"));
        assertTrue(response.body().path("success").booleanValue());
        assertEquals("coco.success", response.body().path("code").textValue());
        assertEquals("操作成功", response.body().path("message").textValue());
        assertEquals("sample-trace", response.body().path("traceId").textValue());
        assertEquals("/sample/hello", response.body().path("path").textValue());
        assertEquals("Coco", response.body().path("data").path("name").textValue());
        assertEquals("Hello Coco", response.body().path("data").path("message").textValue());
    }

    /**
     * <p>
     * 示例接口可以读取 Coco 请求上下文中的 TraceId、HTTP 方法和请求路径。
     * </p>
     * @throws Exception MockMvc 调用失败时抛出
     */
    @Test
    void exposesCurrentRequestContext() throws Exception {
        SampleHttpResponse response = get("/sample/context", "context-trace");

        assertEquals(200, response.status());
        assertTrue(response.body().path("success").booleanValue());
        assertEquals("context-trace", response.body().path("traceId").textValue());
        assertEquals("context-trace", response.body().path("data").path("traceId").textValue());
        assertEquals("GET", response.body().path("data").path("method").textValue());
        assertEquals("/sample/context", response.body().path("data").path("path").textValue());
    }

    /**
     * <p>
     * 示例异常会进入 Coco Web 全局异常处理器，并返回国际化后的统一错误响应。
     * </p>
     * @throws Exception MockMvc 调用失败时抛出
     */
    @Test
    void returnsUnifiedErrorResponse() throws Exception {
        SampleHttpResponse response = get("/sample/error", "error-trace");

        assertEquals(400, response.status());
        assertTrue(!response.body().path("success").booleanValue());
        assertEquals("coco.error.invalid-argument", response.body().path("code").textValue());
        assertEquals("参数不合法：sampleName", response.body().path("message").textValue());
        assertEquals("error-trace", response.body().path("traceId").textValue());
        assertEquals("/sample/error", response.body().path("path").textValue());
    }

    /**
     * <p>
     * 测试作用域访问日志记录器可以拿到业务请求完成后的访问日志。
     * </p>
     * @throws Exception MockMvc 调用失败时抛出
     */
    @Test
    void recordsAccessLogThroughBusinessRequest() throws Exception {
        SampleHttpResponse helloResponse = get("/sample/hello", "access-trace");

        assertEquals(200, helloResponse.status());
        CocoAccessLog accessLog = this.accessLogRecorder.latest()
                .orElseThrow(() -> new AssertionError("Access log was not recorded."));
        assertEquals("access-trace", accessLog.traceId());
        assertEquals("GET", accessLog.method().orElse(null));
        assertEquals("/sample/hello", accessLog.path().orElse(null));
        assertEquals(200, accessLog.status());
        assertTrue(accessLog.success());
    }

    /**
     * <p>
     * 发送 GET 请求并解析 JSON 响应。
     * </p>
     * @param path 请求路径和查询参数
     * @param traceId 请求 TraceId；为空时不写入请求头
     * @return HTTP 响应快照
     * @throws Exception 请求失败或 JSON 解析失败时抛出
     */
    private SampleHttpResponse get(String path, String traceId) throws Exception {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + this.port + path))
                .GET();
        if (traceId != null && !traceId.isBlank()) {
            requestBuilder.header("X-Trace-Id", traceId);
        }
        HttpResponse<String> response = this.httpClient.send(requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString());
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
