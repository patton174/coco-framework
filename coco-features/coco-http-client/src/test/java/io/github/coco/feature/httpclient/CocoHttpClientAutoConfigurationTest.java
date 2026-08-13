package io.github.coco.feature.httpclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.coco.context.trace.CocoTraceContext;
import io.github.coco.feature.web.context.CocoWebRequestCanonicalizationContext;
import io.github.coco.feature.web.context.CocoWebRequestCanonicalizationPurpose;
import io.github.coco.feature.web.context.DefaultCocoWebRequestCanonicalizer;
import io.github.coco.feature.web.request.metadata.CocoWebRequestSecurityInput;
import io.github.coco.feature.web.request.metadata.CocoWebRequestSecurityMetadata;
import io.github.coco.feature.web.signature.CocoSignatureRequest;
import io.github.coco.feature.web.signature.CocoSignatureSecret;
import io.github.coco.feature.web.signature.CocoSignatureVerificationContext;
import io.github.coco.feature.web.signature.HmacSha256CocoSignatureVerifier;
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
    private final AtomicReference<byte[]> requestBody = new AtomicReference<>(new byte[0]);

    @BeforeEach
    void startServer() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        this.server.createContext("/", current -> {
            this.exchange.set(current);
            this.requestBody.set(current.getRequestBody().readAllBytes());
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

    @Test
    void signsGetAndUtf8JsonForTheInboundCanonicalProtocol() {
        this.contextRunner.withPropertyValues(signingProperties("inventory", "app-a", "key-a", "0123456789abcdef"))
                .run(context -> {
                    RestClient client = context.getBean(CocoHttpClients.class).get("inventory");
                    assertThat(client.get().uri("/orders/%E4%B8%AD%E6%96%87?tag=a%20b&tag=%E4%B8%AD%E6%96%87")
                            .retrieve().body(String.class)).isEqualTo("ok");
                    assertThat(inboundSignatureValid(this.exchange.get(), "0123456789abcdef")).isTrue();
                    assertThat(this.exchange.get().getRequestHeaders().getFirst("X-Coco-Timestamp"))
                            .matches("\\d{13}");
                    assertThat(this.exchange.get().getRequestHeaders().getFirst("X-Coco-Nonce")).isNotBlank();
                    assertThat(this.exchange.get().getRequestHeaders().getFirst("X-Coco-Sign-Algorithm"))
                            .isEqualTo("HMAC-SHA256");
                    assertThat(client.post().uri("/orders?kind=json").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .body("{\"name\":\"\u4e2d\u6587\"}").retrieve().body(String.class)).isEqualTo("ok");
                    assertThat(inboundSignatureValid(this.exchange.get(), "0123456789abcdef")).isTrue();
                });
    }

    @Test
    void rejectsTamperedHeaderBodyOrQueryAgainstTheSameCanonicalForm() {
        this.contextRunner.withPropertyValues(signingProperties("inventory", "app-a", "key-a", "0123456789abcdef"))
                .run(context -> {
                    RestClient client = context.getBean(CocoHttpClients.class).get("inventory");
                    client.post().uri("/orders?state=open").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .body("{\"id\":1}").retrieve().toBodilessEntity();
                    assertThat(inboundSignatureValid(this.exchange.get(), "0123456789abcdef")).isTrue();
                    assertThat(inboundSignatureValid(this.exchange.get(), "wrong-secret-value")).isFalse();
                    assertThat(inboundSignatureValid(this.exchange.get(), "0123456789abcdef", "{\"id\":2}", null, null)).isFalse();
                    assertThat(inboundSignatureValid(this.exchange.get(), "0123456789abcdef", null, "X-Coco-App-Id", "other"))
                            .isFalse();
                    assertThat(inboundSignatureValid(this.exchange.get(), "0123456789abcdef", null, null, null,
                            "state=closed")).isFalse();
                });
    }

    @Test
    void isolatesClientCredentialsSupportsBusinessProviderAndLeavesDisabledClientUnsigned() {
        this.contextRunner.withPropertyValues(joinProperties(signingProperties("one", "app-one", "key-one", "0123456789abcdef"),
                signingProperties("two", "app-two", "key-two", "fedcba9876543210"),
                new String[] { "coco.http.clients.disabled.base-url=" + this.baseUrl,
                        "coco.http.clients.disabled.connect-timeout=1s", "coco.http.clients.disabled.read-timeout=2s" }))
                .run(context -> {
                    CocoHttpClients clients = context.getBean(CocoHttpClients.class);
                    clients.get("one").get().uri("/one").retrieve().toBodilessEntity();
                    assertThat(inboundSignatureValid(this.exchange.get(), "0123456789abcdef")).isTrue();
                    clients.get("two").get().uri("/two").retrieve().toBodilessEntity();
                    assertThat(inboundSignatureValid(this.exchange.get(), "fedcba9876543210")).isTrue();
                    assertThat(this.exchange.get().getRequestHeaders().getFirst("X-Coco-App-Id")).isEqualTo("app-two");
                    clients.get("disabled").get().uri("/disabled").retrieve().toBodilessEntity();
                    assertThat(this.exchange.get().getRequestHeaders().getFirst("X-Coco-Sign")).isNull();
                });
        this.contextRunner.withPropertyValues(signingProperties("inventory", "ignored", "ignored", "0123456789abcdef"))
                .withUserConfiguration(SigningProviderConfiguration.class).run(context -> {
                    context.getBean(CocoHttpClients.class).get("inventory").get().uri("/provider").retrieve()
                            .toBodilessEntity();
                    assertThat(inboundSignatureValid(this.exchange.get(), "provider-secret-123")).isTrue();
                    assertThat(this.exchange.get().getRequestHeaders().getFirst("X-Coco-App-Id")).isEqualTo("provider-app");
                });
    }

    @Test
    void failsFastForConflictingSigningHeadersAndNeverExposesSecret() {
        String secret = "0123456789abcdef";
        this.contextRunner.withPropertyValues(signingProperties("inventory", "app-a", "key-a", secret))
                .run(context -> assertThatThrownBy(() -> context.getBean(CocoHttpClients.class).get("inventory").get()
                        .uri("/conflict").header("X-Coco-App-Id", "business").retrieve().toBodilessEntity())
                        .hasMessageContaining("already configured").satisfies(ex ->
                                assertThat(ex.getMessage()).doesNotContain(secret)));
        this.contextRunner.withPropertyValues("coco.http.clients.inventory.base-url=" + this.baseUrl,
                "coco.http.clients.inventory.signing.enabled=true", "coco.http.clients.inventory.signing.app-id=app",
                "coco.http.clients.inventory.signing.key-id=key", "coco.http.clients.inventory.signing.secret=short")
                .run(context -> assertThat(context).hasFailed().getFailure().satisfies(ex ->
                        assertThat(ex.getMessage()).doesNotContain("short")));
    }

    private String[] clientProperties(String name) {
        return new String[] { "coco.http.clients." + name + ".base-url=" + this.baseUrl,
                "coco.http.clients." + name + ".connect-timeout=1s", "coco.http.clients." + name + ".read-timeout=2s" };
    }

    private String[] signingProperties(String name, String appId, String keyId, String secret) {
        return new String[] { "coco.http.clients." + name + ".base-url=" + this.baseUrl,
                "coco.http.clients." + name + ".connect-timeout=1s", "coco.http.clients." + name + ".read-timeout=2s",
                "coco.http.clients." + name + ".signing.enabled=true", "coco.http.clients." + name + ".signing.app-id=" + appId,
                "coco.http.clients." + name + ".signing.key-id=" + keyId, "coco.http.clients." + name + ".signing.secret=" + secret };
    }

    private static String[] joinProperties(String[]... values) {
        return java.util.Arrays.stream(values).flatMap(java.util.Arrays::stream).toArray(String[]::new);
    }

    private boolean inboundSignatureValid(HttpExchange request, String secret) {
        return inboundSignatureValid(request, secret, null, null, null);
    }

    private boolean inboundSignatureValid(HttpExchange request, String secret, String replacementBody,
            String replacementHeaderName, String replacementHeaderValue) {
        return inboundSignatureValid(request, secret, replacementBody, replacementHeaderName, replacementHeaderValue,
                null);
    }

    private boolean inboundSignatureValid(HttpExchange request, String secret, String replacementBody,
            String replacementHeaderName, String replacementHeaderValue, String replacementQuery) {
        byte[] body = replacementBody == null ? this.requestBody.get()
                    : replacementBody.getBytes(StandardCharsets.UTF_8);
            Map<String, List<String>> headers = new LinkedHashMap<>();
            Set.of("content-md5", "content-type", "x-coco-app-id", "x-coco-timestamp", "x-coco-nonce",
                    "x-coco-key-id", "x-coco-sign-algorithm").forEach(name -> {
                        List<String> values = request.getRequestHeaders().get(name);
                        if (values != null && !values.isEmpty()) headers.put(name, new ArrayList<>(values));
                    });
            if (replacementHeaderName != null) headers.put(replacementHeaderName.toLowerCase(), List.of(replacementHeaderValue));
            String query = replacementQuery == null ? request.getRequestURI().getRawQuery() : replacementQuery;
            CocoWebRequestSecurityInput input = new CocoWebRequestSecurityInput(request.getRequestMethod(),
                    request.getRequestURI().getRawPath(), query, queryParameters(query), Map.of(), Map.of(), Map.of(),
                    join(headers), sha256(body), (long) body.length, true, headers, Map.of());
            String canonical = new DefaultCocoWebRequestCanonicalizer().canonicalize(new CocoWebRequestCanonicalizationContext(
                    CocoWebRequestCanonicalizationPurpose.SIGNATURE, input, CocoWebRequestSecurityMetadata.empty(), null)).text();
            String signature = request.getRequestHeaders().getFirst("X-Coco-Sign");
        return new HmacSha256CocoSignatureVerifier().verify(new CocoSignatureVerificationContext(
                    new CocoSignatureRequest(request.getRequestHeaders().getFirst("X-Coco-App-Id"),
                            request.getRequestHeaders().getFirst("X-Coco-Key-Id"),
                            request.getRequestHeaders().getFirst("X-Coco-Timestamp"),
                            request.getRequestHeaders().getFirst("X-Coco-Nonce"),
                            request.getRequestHeaders().getFirst("X-Coco-Sign-Algorithm"), signature, canonical, null),
                new CocoSignatureSecret("app", "key", secret)));
    }

    private static Map<String, List<String>> queryParameters(String query) {
        Map<String, List<String>> parameters = new LinkedHashMap<>();
        if (query == null) return parameters;
        for (String pair : query.split("&", -1)) {
            int separator = pair.indexOf('=');
            String name = separator < 0 ? pair : pair.substring(0, separator);
            if (!name.isBlank()) parameters.computeIfAbsent(name, ignored -> new ArrayList<>())
                    .add(separator < 0 ? "" : pair.substring(separator + 1));
        }
        return parameters;
    }

    private static Map<String, String> join(Map<String, List<String>> headers) {
        Map<String, String> values = new LinkedHashMap<>();
        headers.forEach((name, value) -> values.put(name, String.join(",", value)));
        return values;
    }

    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
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

    @Configuration(proxyBeanMethods = false)
    static class SigningProviderConfiguration {
        @Bean CocoHttpClientSigningCredentialProvider signingProvider() {
            return name -> java.util.Optional.of(new CocoHttpClientSigningCredential("provider-app", "provider-key",
                    "provider-secret-123", "HMAC-SHA256"));
        }
    }
}
