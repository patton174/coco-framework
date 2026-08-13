package io.github.coco.feature.httpclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coco.i18n.CocoMessage;
import io.github.coco.i18n.CocoMessageService;
import io.github.coco.context.trace.CocoTraceContext;
import io.github.coco.feature.web.CocoWebProperties;
import io.github.coco.feature.web.body.CocoCachedBodyHttpServletRequest;
import io.github.coco.feature.web.body.CocoCachedRequestBody;
import io.github.coco.feature.web.body.DefaultCocoRequestBodyResolver;
import io.github.coco.feature.web.context.DefaultCocoRequestCookieResolver;
import io.github.coco.feature.web.context.DefaultCocoRequestHeaderResolver;
import io.github.coco.feature.web.context.DefaultCocoRequestParameterResolver;
import io.github.coco.feature.web.context.DefaultCocoWebRequestCanonicalizer;
import io.github.coco.feature.web.context.DefaultCocoWebRequestContextResolver;
import io.github.coco.feature.web.context.payload.DefaultCocoPayloadParameterResolver;
import io.github.coco.feature.web.exception.CocoFilterExceptionResponseWriter;
import io.github.coco.feature.web.exception.CocoWebExceptionHandler;
import io.github.coco.feature.web.exception.DefaultCocoExceptionHttpStatusResolver;
import io.github.coco.feature.web.request.metadata.DefaultCocoWebRequestSecurityInputResolver;
import io.github.coco.feature.web.request.metadata.DefaultCocoWebRequestSecurityMetadataResolver;
import io.github.coco.feature.web.response.CocoSystemCodes;
import io.github.coco.feature.web.signature.CocoSignatureFilter;
import io.github.coco.feature.web.signature.CocoSignatureSecret;
import io.github.coco.feature.web.signature.HmacSha256CocoSignatureVerifier;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;

/**
 * Coco HTTP 客户端自动配置测试。
 */
class CocoHttpClientAutoConfigurationTest {

    private static final List<String> CUSTOMIZER_CALLS = new CopyOnWriteArrayList<>();
    private static final List<String> PROVIDER_CALLS = new CopyOnWriteArrayList<>();
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CocoHttpClientAutoConfiguration.class));
    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<HttpExchange> exchange = new AtomicReference<>();
    private final AtomicReference<byte[]> requestBody = new AtomicReference<>(new byte[0]);
    private final AtomicReference<InboundSignatureVerifier> inboundVerifier = new AtomicReference<>();
    private final AtomicReference<String> inboundFailure = new AtomicReference<>();

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
            VerificationResult verification = this.inboundVerifier.get() == null
                    ? new VerificationResult(true, "")
                    : this.inboundVerifier.get().verify(current, this.requestBody.get(), RequestMutation.none());
            this.inboundFailure.set(verification.failure());
            boolean signatureAccepted = verification.accepted();
            byte[] body = current.getRequestURI().getPath().equals("/failure")
                    ? "x".repeat(2048).getBytes(StandardCharsets.UTF_8) : "ok".getBytes(StandardCharsets.UTF_8);
            int status = current.getRequestURI().getPath().equals("/failure") ? 502
                    : signatureAccepted ? 200 : 401;
            current.sendResponseHeaders(status, body.length);
            current.getResponseBody().write(body);
            current.close();
        });
        this.server.start();
        this.baseUrl = "http://127.0.0.1:" + this.server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        CocoTraceContext.clear();
        this.inboundVerifier.set(null);
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
        String secret = "0123456789abcdef";
        this.inboundVerifier.set(inboundVerifier(secret, Set.of("content-type", "x-trace-id")));
        this.contextRunner.withPropertyValues(joinProperties(signingProperties("inventory", "app-a", "key-a", secret),
                new String[] { "coco.web.context.canonical-header-names[0]=content-type",
                        "coco.web.context.canonical-header-names[1]=x-trace-id" }))
                .run(context -> {
                    CocoTraceContext.setTraceId("signed-trace");
                    RestClient client = context.getBean(CocoHttpClients.class).get("inventory");
                    assertThat(client.get().uri("/orders/%E4%B8%AD%E6%96%87?tag=a%20b&tag=%E4%B8%AD%E6%96%87")
                            .retrieve().body(String.class)).isEqualTo("ok");
                    assertThat(this.inboundVerifier.get().verify(this.exchange.get(), this.requestBody.get(),
                            RequestMutation.none()).accepted()).isTrue();
                    assertThat(this.exchange.get().getRequestHeaders().getFirst("X-Trace-Id"))
                            .isEqualTo("signed-trace");
                    assertThat(this.exchange.get().getRequestHeaders().getFirst("X-Coco-Timestamp"))
                            .matches("\\d{13}");
                    assertThat(this.exchange.get().getRequestHeaders().getFirst("X-Coco-Nonce")).isNotBlank();
                    assertThat(this.exchange.get().getRequestHeaders().getFirst("X-Coco-Sign-Algorithm"))
                            .isEqualTo("HMAC-SHA256");
                    assertThat(client.post().uri("/orders?kind=json").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .body("{\"name\":\"\u4e2d\u6587\"}").retrieve().body(String.class))
                            .as(this.inboundFailure.get()).isEqualTo("ok");
                    assertThat(this.inboundVerifier.get().verify(this.exchange.get(), this.requestBody.get(),
                            RequestMutation.none()).accepted()).isTrue();
                });
    }

    @Test
    void rejectsTamperedHeaderBodyOrQueryAgainstTheSameCanonicalForm() {
        String secret = "0123456789abcdef";
        this.inboundVerifier.set(inboundVerifier(secret, Set.of("content-type")));
        this.contextRunner.withPropertyValues(signingProperties("inventory", "app-a", "key-a", secret))
                .run(context -> {
                    RestClient client = context.getBean(CocoHttpClients.class).get("inventory");
                    client.post().uri("/orders?state=open").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .body("{\"id\":1}").retrieve().toBodilessEntity();
                    HttpExchange request = this.exchange.get();
                    assertThat(this.inboundVerifier.get().verify(request, this.requestBody.get(), RequestMutation.none())
                            .accepted()).isTrue();
                    assertThat(inboundVerifier("wrong-secret-value", Set.of("content-type"))
                            .verify(request, this.requestBody.get(), RequestMutation.none()).accepted()).isFalse();
                    assertThat(this.inboundVerifier.get().verify(request, this.requestBody.get(),
                            RequestMutation.body("{\"id\":2}")).accepted()).isFalse();
                    assertThat(this.inboundVerifier.get().verify(request, this.requestBody.get(),
                            RequestMutation.header("X-Coco-App-Id", "other")).accepted()).isFalse();
                    assertThat(this.inboundVerifier.get().verify(request, this.requestBody.get(),
                            RequestMutation.query("state=closed")).accepted()).isFalse();
                });
    }

    @Test
    void isolatesClientCredentialsSupportsBusinessProviderAndLeavesDisabledClientUnsigned() {
        this.inboundVerifier.set(null);
        this.contextRunner.withPropertyValues(joinProperties(signingProperties("one", "app-one", "key-one", "0123456789abcdef"),
                signingProperties("two", "app-two", "key-two", "fedcba9876543210"),
                new String[] { "coco.http.clients.disabled.base-url=" + this.baseUrl,
                        "coco.http.clients.disabled.connect-timeout=1s", "coco.http.clients.disabled.read-timeout=2s" }))
                .run(context -> {
                    CocoHttpClients clients = context.getBean(CocoHttpClients.class);
                    clients.get("one").get().uri("/one").retrieve().toBodilessEntity();
                    assertThat(inboundVerifier("0123456789abcdef", Set.of()).verify(this.exchange.get(),
                            this.requestBody.get(), RequestMutation.none()).accepted()).isTrue();
                    clients.get("two").get().uri("/two").retrieve().toBodilessEntity();
                    assertThat(inboundVerifier("fedcba9876543210", Set.of()).verify(this.exchange.get(),
                            this.requestBody.get(), RequestMutation.none()).accepted()).isTrue();
                    assertThat(this.exchange.get().getRequestHeaders().getFirst("X-Coco-App-Id")).isEqualTo("app-two");
                    clients.get("disabled").get().uri("/disabled").retrieve().toBodilessEntity();
                    assertThat(this.exchange.get().getRequestHeaders().getFirst("X-Coco-Sign")).isNull();
                });
        this.contextRunner.withPropertyValues(joinProperties(clientProperties("inventory"),
                new String[] { "coco.http.clients.inventory.signing.enabled=true" }))
                .withUserConfiguration(SigningProviderConfiguration.class).run(context -> {
                    context.getBean(CocoHttpClients.class).get("inventory").get().uri("/provider").retrieve()
                            .toBodilessEntity();
                    assertThat(inboundVerifier("provider-secret-123", Set.of()).verify(this.exchange.get(),
                            this.requestBody.get(), RequestMutation.none()).accepted()).isTrue();
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

    @Test
    void resolvesOrderedProvidersPerClientAndFallsBackToPropertiesOnZeroMatches() {
        PROVIDER_CALLS.clear();
        this.contextRunner.withPropertyValues(joinProperties(
                signingProperties("inventory", "property-app", "property-key", "property-secret-1"),
                signingProperties("billing", "billing-property", "billing-key", "billing-secret-1")))
                .withUserConfiguration(OrderedSigningProvidersConfiguration.class).run(context -> {
                    CocoHttpClients clients = context.getBean(CocoHttpClients.class);
                    clients.get("inventory").get().uri("/provider-order").retrieve().toBodilessEntity();
                    assertThat(this.exchange.get().getRequestHeaders().getFirst("X-Coco-App-Id"))
                            .isEqualTo("ordered-app");
                    clients.get("billing").get().uri("/provider-fallback").retrieve().toBodilessEntity();
                    assertThat(this.exchange.get().getRequestHeaders().getFirst("X-Coco-App-Id"))
                            .isEqualTo("billing-property");
                    assertThat(PROVIDER_CALLS).containsExactly("first:inventory", "second:inventory",
                            "first:billing", "second:billing");
                });
    }

    @Test
    void explicitClientCanonicalHeadersOverrideProcessDefaultsAndKeepProtocolHeaders() {
        String secret = "0123456789abcdef";
        this.inboundVerifier.set(inboundVerifier(secret, Set.of("x-client-only")));
        this.contextRunner.withPropertyValues(joinProperties(
                signingProperties("inventory", "app-a", "key-a", secret),
                new String[] { "coco.web.context.canonical-header-names[0]=x-process-only",
                        "coco.http.clients.inventory.signing.canonical-header-names[0]=x-client-only" }))
                .withUserConfiguration(ClientCanonicalHeaderConfiguration.class).run(context -> {
                    assertThat(context.getBean(CocoHttpClients.class).get("inventory").get()
                            .uri("/client-contract").retrieve().body(String.class)).isEqualTo("ok");
                    assertThat(this.exchange.get().getRequestHeaders().getFirst("X-Client-Only"))
                            .isEqualTo("client-contract");
                    assertThat(this.exchange.get().getRequestHeaders().getFirst("X-Coco-App-Id"))
                            .isEqualTo("app-a");
                });
    }

    @Test
    void failsClosedWhenMultipleProvidersMatchOrProviderThrowsWithoutLeakingSecret() {
        String secret = "provider-secret-must-not-leak";
        this.contextRunner.withPropertyValues(signingProperties("inventory", "property-app", "property-key",
                        "property-secret-1"))
                .withUserConfiguration(AmbiguousSigningProvidersConfiguration.class).run(context -> {
                    assertThat(context).hasFailed().getFailure().satisfies(ex ->
                            assertThat(stackTrace(ex)).contains("Multiple Coco HTTP client signing credential providers",
                                            "inventory", "firstMatchingProvider", "secondMatchingProvider")
                                    .doesNotContain(secret));
                });
        this.contextRunner.withPropertyValues(signingProperties("inventory", "property-app", "property-key",
                        "property-secret-1"))
                .withUserConfiguration(ThrowingSigningProviderConfiguration.class).run(context -> {
                    assertThat(context).hasFailed().getFailure().satisfies(ex ->
                            assertThat(stackTrace(ex)).contains("failed for client 'inventory'",
                                            "throwingSigningProvider")
                                    .doesNotContain(secret));
                });
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

    private static String stackTrace(Throwable throwable) {
        java.io.StringWriter writer = new java.io.StringWriter();
        throwable.printStackTrace(new java.io.PrintWriter(writer));
        return writer.toString();
    }

    private static InboundSignatureVerifier inboundVerifier(String secret, Set<String> canonicalHeaderNames) {
        CocoWebProperties properties = new CocoWebProperties();
        properties.getContext().setCanonicalHeaderNames(canonicalHeaderNames);
        properties.getSignature().setTimestampValidationEnabled(false);
        properties.getSignature().setRequired(true);
        DefaultCocoRequestHeaderResolver headerResolver =
                new DefaultCocoRequestHeaderResolver(properties.getContext());
        DefaultCocoRequestCookieResolver cookieResolver =
                new DefaultCocoRequestCookieResolver(properties.getContext());
        DefaultCocoPayloadParameterResolver payloadResolver =
                new DefaultCocoPayloadParameterResolver(properties.getContext().getParameter());
        DefaultCocoRequestParameterResolver parameterResolver = new DefaultCocoRequestParameterResolver(
                properties.getContext().getParameter(), payloadResolver);
        DefaultCocoRequestBodyResolver bodyResolver = new DefaultCocoRequestBodyResolver();
        DefaultCocoWebRequestSecurityInputResolver inputResolver =
                new DefaultCocoWebRequestSecurityInputResolver(properties.getContext(), headerResolver,
                        cookieResolver, parameterResolver, properties.getSignature(), properties.getEncryption(),
                        properties.getReplay(), bodyResolver, properties.getTrace(), null);
        DefaultCocoWebRequestSecurityMetadataResolver metadataResolver =
                new DefaultCocoWebRequestSecurityMetadataResolver(properties.getSignature(),
                        properties.getEncryption(), properties.getReplay());
        DefaultCocoWebRequestContextResolver contextResolver = new DefaultCocoWebRequestContextResolver(
                properties.getContext(), null, null, headerResolver, cookieResolver, null, parameterResolver,
                inputResolver, metadataResolver, bodyResolver);
        DefaultCocoWebRequestCanonicalizer canonicalizer = new DefaultCocoWebRequestCanonicalizer(
                properties.getContext().getCanonicalization(), properties.getTrace(), null);
        CocoSignatureFilter filter = new CocoSignatureFilter(properties.getSignature(),
                request -> Optional.of(new CocoSignatureSecret(request.appId(), request.keyId(), secret)),
                new HmacSha256CocoSignatureVerifier(), contextResolver, canonicalizer, exceptionWriter(),
                metadataResolver, null, Clock.systemUTC());
        return new InboundSignatureVerifier(filter);
    }

    private static CocoFilterExceptionResponseWriter exceptionWriter() {
        return new CocoFilterExceptionResponseWriter(new CocoWebExceptionHandler(new StaticMessageService(),
                new DefaultCocoExceptionHttpStatusResolver(), CocoSystemCodes.defaults()), new ObjectMapper());
    }

    private static MockHttpServletRequest servletRequest(HttpExchange exchange, byte[] body,
            RequestMutation mutation) {
        String query = mutation.query() == null ? exchange.getRequestURI().getRawQuery() : mutation.query();
        MockHttpServletRequest request = new MockHttpServletRequest(exchange.getRequestMethod(),
                exchange.getRequestURI().getRawPath());
        request.setQueryString(query);
        request.setScheme("http");
        request.setServerName("127.0.0.1");
        request.setServerPort(exchange.getLocalAddress().getPort());
        exchange.getRequestHeaders().forEach((name, values) -> values.forEach(value -> request.addHeader(name, value)));
        if (mutation.headerName() != null) {
            request.removeHeader(mutation.headerName());
            request.addHeader(mutation.headerName(), mutation.headerValue());
        }
        byte[] effectiveBody = mutation.body() == null ? body
                : mutation.body().getBytes(StandardCharsets.UTF_8);
        request.setContent(effectiveBody);
        return request;
    }

    private record InboundSignatureVerifier(CocoSignatureFilter filter) {

        VerificationResult verify(HttpExchange exchange, byte[] body, RequestMutation mutation) {
            MockHttpServletRequest rawRequest = servletRequest(exchange, body, mutation);
            HttpServletRequest request = new CocoCachedBodyHttpServletRequest(rawRequest,
                    CocoCachedRequestBody.cached(rawRequest.getContentAsByteArray()));
            MockHttpServletResponse response = new MockHttpServletResponse();
            java.util.concurrent.atomic.AtomicBoolean reachedApplication =
                    new java.util.concurrent.atomic.AtomicBoolean();
            try {
                this.filter.doFilter(request, response,
                        (currentRequest, currentResponse) -> reachedApplication.set(true));
                boolean accepted = reachedApplication.get() && response.getStatus() < 400;
                return new VerificationResult(accepted, response.getContentAsString());
            }
            catch (IOException | ServletException ex) {
                throw new IllegalStateException("Inbound signature verification failed", ex);
            }
            finally {
                CocoTraceContext.clear();
            }
        }
    }

    private record VerificationResult(boolean accepted, String failure) {
    }

    private record RequestMutation(String body, String headerName, String headerValue, String query) {

        static RequestMutation none() {
            return new RequestMutation(null, null, null, null);
        }

        static RequestMutation body(String body) {
            return new RequestMutation(body, null, null, null);
        }

        static RequestMutation header(String name, String value) {
            return new RequestMutation(null, name, value, null);
        }

        static RequestMutation query(String query) {
            return new RequestMutation(null, null, null, query);
        }
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

    @Configuration(proxyBeanMethods = false)
    static class OrderedSigningProvidersConfiguration {
        @Bean @Order(1) CocoHttpClientSigningCredentialProvider firstProvider() {
            return name -> {
                PROVIDER_CALLS.add("first:" + name);
                return Optional.empty();
            };
        }
        @Bean @Order(2) CocoHttpClientSigningCredentialProvider secondProvider() {
            return name -> {
                PROVIDER_CALLS.add("second:" + name);
                return "inventory".equals(name)
                        ? Optional.of(new CocoHttpClientSigningCredential("ordered-app", "ordered-key",
                                "ordered-secret-123", "HMAC-SHA256"))
                        : Optional.empty();
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class AmbiguousSigningProvidersConfiguration {
        @Bean CocoHttpClientSigningCredentialProvider firstMatchingProvider() {
            return name -> Optional.of(new CocoHttpClientSigningCredential("first-app", "first-key",
                    "provider-secret-must-not-leak", "HMAC-SHA256"));
        }
        @Bean CocoHttpClientSigningCredentialProvider secondMatchingProvider() {
            return name -> Optional.of(new CocoHttpClientSigningCredential("second-app", "second-key",
                    "provider-secret-must-not-leak", "HMAC-SHA256"));
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ThrowingSigningProviderConfiguration {
        @Bean CocoHttpClientSigningCredentialProvider throwingSigningProvider() {
            return name -> { throw new IllegalStateException("provider-secret-must-not-leak"); };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ClientCanonicalHeaderConfiguration {
        @Bean CocoHttpClientCustomizer clientCanonicalHeader() {
            return (name, builder) -> builder.defaultHeader("X-Client-Only", "client-contract");
        }
    }

    private static final class StaticMessageService implements CocoMessageService {
        @Override public String getMessage(String code, Object... args) { return code; }
        @Override public String getMessage(String code, Locale locale, Object... args) { return code; }
        @Override public String getMessageOrDefault(String code, String message, Object... args) { return message; }
        @Override public String getMessageOrDefault(String code, String message, Locale locale, Object... args) {
            return message;
        }
        @Override public String resolve(CocoMessage message) { return message == null ? "" : message.code(); }
        @Override public String resolve(CocoMessage message, Locale locale) { return resolve(message); }
    }
}
