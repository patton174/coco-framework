package io.github.coco.feature.httpclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.coco.context.trace.CocoTraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;

/**
 * Coco HTTP 客户端自动配置测试。
 */
class CocoHttpClientAutoConfigurationTest {

    private static final List<String> CUSTOMIZER_CALLS = new CopyOnWriteArrayList<>();
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CocoHttpClientAutoConfiguration.class));
    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<HttpExchange> exchange = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        this.server.createContext("/", current -> {
            this.exchange.set(current);
            if (current.getRequestURI().getPath().equals("/slow")) {
                try {
                    TimeUnit.MILLISECONDS.sleep(300);
                }
                catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] body = current.getRequestURI().getPath().equals("/failure")
                    ? "x".repeat(2048).getBytes(StandardCharsets.UTF_8) : "ok".getBytes(StandardCharsets.UTF_8);
            current.sendResponseHeaders(current.getRequestURI().getPath().equals("/failure") ? 502 : 200, body.length);
            current.getResponseBody().write(body);
            current.close();
        });
        this.server.start();
        this.baseUrl = "http://127.0.0.1:" + this.server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        CocoTraceContext.clear();
        this.server.stop(0);
    }

    @Test
    void createsNamedClientWithBaseUrlDefaultHeadersAndTrace() {
        this.contextRunner.withPropertyValues("coco.http.clients.inventory.base-url=" + this.baseUrl,
                "coco.http.clients.inventory.connect-timeout=1s", "coco.http.clients.inventory.read-timeout=2s",
                "coco.http.clients.inventory.default-headers.X-Client=inventory")
                .run(context -> {
                    CocoTraceContext.setTraceId("trace-1");
                    assertThat(context.getBean(CocoHttpClients.class).get("inventory").get().uri("/orders").retrieve()
                            .body(String.class)).isEqualTo("ok");
                    assertThat(this.exchange.get().getRequestHeaders().getFirst("X-Client")).isEqualTo("inventory");
                    assertThat(this.exchange.get().getRequestHeaders().getFirst("X-Trace-Id")).isEqualTo("trace-1");
                });
    }

    @Test
    void preservesExplicitTraceHeaderAndAllowsMissingTrace() {
        this.contextRunner.withPropertyValues(clientProperties("inventory")).run(context -> {
            RestClient client = context.getBean(CocoHttpClients.class).get("inventory");
            client.get().uri("/first").header("X-Trace-Id", "business").retrieve().toBodilessEntity();
            assertThat(this.exchange.get().getRequestHeaders().getFirst("X-Trace-Id")).isEqualTo("business");
            client.get().uri("/second").retrieve().toBodilessEntity();
            assertThat(this.exchange.get().getRequestHeaders().getFirst("X-Trace-Id")).isNull();
        });
    }

    @Test
    void failsFastForUnknownOrInvalidConfigurationAndCanBeDisabled() {
        this.contextRunner.withPropertyValues(clientProperties("inventory")).run(context ->
                assertThatThrownBy(() -> context.getBean(CocoHttpClients.class).get("missing"))
                        .isInstanceOf(IllegalArgumentException.class));
        this.contextRunner.withPropertyValues("coco.http.clients.inventory.base-url=not-a-url").run(context ->
                assertThat(context).hasFailed().getFailure().hasStackTraceContaining("base-url"));
        this.contextRunner.withPropertyValues("coco.http.clients.inventory.base-url=" + this.baseUrl,
                "coco.http.clients.inventory.default-headers.Authorization=secret").run(context ->
                assertThat(context).hasFailed().getFailure().hasStackTraceContaining("must not configure"));
        this.contextRunner.withPropertyValues("coco.http.enabled=false").run(context ->
                assertThat(context).doesNotHaveBean(CocoHttpClients.class));
        this.contextRunner.run(context -> {
            CocoHttpClients clients = context.getBean(CocoHttpClients.class);
            assertThatThrownBy(() -> clients.get("missing")).isInstanceOf(IllegalArgumentException.class);
        });
    }

    @Test
    void rejectsBaseUrlWithQuery() {
        this.contextRunner.withPropertyValues("coco.http.clients.inventory.base-url=" + this.baseUrl + "?fixed=true")
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasStackTraceContaining("without user-info, query, or fragment"));
    }

    @Test
    void rejectsBaseUrlWithFragment() {
        this.contextRunner.withPropertyValues("coco.http.clients.inventory.base-url=" + this.baseUrl + "#fixed")
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasStackTraceContaining("without user-info, query, or fragment"));
    }

    @Test
    void acceptsTimeoutsAtFiveMinuteBoundary() {
        this.contextRunner.withPropertyValues("coco.http.clients.inventory.base-url=" + this.baseUrl,
                "coco.http.clients.inventory.connect-timeout=5m", "coco.http.clients.inventory.read-timeout=5m")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(CocoHttpClients.class));
    }

    @Test
    void rejectsConnectAndReadTimeoutsAboveFiveMinutes() {
        this.contextRunner.withPropertyValues("coco.http.clients.inventory.base-url=" + this.baseUrl,
                "coco.http.clients.inventory.connect-timeout=300001ms")
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasStackTraceContaining("connect-timeout must not exceed 5 minutes"));
        this.contextRunner.withPropertyValues("coco.http.clients.inventory.base-url=" + this.baseUrl,
                "coco.http.clients.inventory.read-timeout=300001ms")
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasStackTraceContaining("read-timeout must not exceed 5 minutes"));
    }

    @Test
    void appliesConfiguredReadTimeout() {
        this.contextRunner.withPropertyValues("coco.http.clients.inventory.base-url=" + this.baseUrl,
                "coco.http.clients.inventory.connect-timeout=1s", "coco.http.clients.inventory.read-timeout=50ms")
                .run(context -> assertThatThrownBy(() -> context.getBean(CocoHttpClients.class).get("inventory").get()
                        .uri("/slow").retrieve().body(String.class)).isInstanceOf(ResourceAccessException.class));
    }

    @Test
    void mapsErrorsWithSanitizedUriAndBoundedSummaryAndSupportsOverrides() {
        this.contextRunner.withPropertyValues(clientProperties("inventory")).run(context -> {
            assertThatThrownBy(() -> context.getBean(CocoHttpClients.class).get("inventory").get()
                    .uri("/failure?secret=value#fragment").retrieve().body(String.class))
                    .isInstanceOf(CocoHttpClientException.class).satisfies(ex -> {
                        CocoHttpClientException failure = (CocoHttpClientException) ex;
                        assertThat(failure.getUri()).doesNotContain("secret", "fragment");
                        assertThat(failure.getResponseSummary()).hasSizeLessThanOrEqualTo(515);
                    });
        });
        this.contextRunner.withPropertyValues(clientProperties("inventory"))
                .withUserConfiguration(ErrorMapperConfiguration.class).run(context ->
                        assertThatThrownBy(() -> context.getBean(CocoHttpClients.class).get("inventory").get()
                                .uri("/failure").retrieve().body(String.class)).hasMessage("mapped"));
    }

    @Test
    void appliesCustomizersInOrderAndAllowsApplicationRegistryOverride() {
        CUSTOMIZER_CALLS.clear();
        this.contextRunner.withPropertyValues(clientProperties("inventory"))
                .withUserConfiguration(CustomizerConfiguration.class).run(context -> {
                    assertThat(CUSTOMIZER_CALLS).containsExactly("first", "second");
                });
        this.contextRunner.withUserConfiguration(RegistryConfiguration.class).run(context ->
                assertThat(context.getBean(CocoHttpClients.class)).isSameAs(context.getBean("applicationClients")));
    }

    private String[] clientProperties(String name) {
        return new String[] { "coco.http.clients." + name + ".base-url=" + this.baseUrl,
                "coco.http.clients." + name + ".connect-timeout=1s", "coco.http.clients." + name + ".read-timeout=2s" };
    }

    @Configuration(proxyBeanMethods = false)
    static class ErrorMapperConfiguration {
        @Bean CocoHttpErrorMapper mapper() { return (name, request, response) -> new IllegalStateException("mapped"); }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomizerConfiguration {
        @Bean @Order(1) CocoHttpClientCustomizer first() { return (name, builder) -> CUSTOMIZER_CALLS.add("first"); }
        @Bean @Order(2) CocoHttpClientCustomizer second() { return (name, builder) -> CUSTOMIZER_CALLS.add("second"); }
    }

    @Configuration(proxyBeanMethods = false)
    static class RegistryConfiguration {
        @Bean CocoHttpClients applicationClients() { return name -> RestClient.create(); }
    }
}
