package io.github.coco.feature.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Instant;
import java.util.Locale;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coco.common.autoconfigure.CocoCommonAutoConfiguration;
import io.github.coco.feature.web.CocoWebAutoConfiguration;
import io.github.coco.feature.web.exception.CocoExceptionHttpStatusResolver;
import io.github.coco.feature.idempotency.servlet.CocoIdempotencyFilter;
import io.github.coco.feature.idempotency.store.CocoIdempotencyAcquireResult;
import io.github.coco.feature.idempotency.store.CocoIdempotencyLease;
import io.github.coco.feature.idempotency.store.CocoIdempotencyRequest;
import io.github.coco.feature.idempotency.store.CocoIdempotencyStore;
import io.github.coco.feature.idempotency.store.CocoIdempotencyStoredResponse;
import io.github.coco.feature.idempotency.store.InMemoryCocoIdempotencyStore;
import io.github.coco.feature.security.context.CocoSecurityContext;
import io.github.coco.feature.security.context.CocoSecurityContextHolder;
import io.github.coco.feature.security.context.CocoSecurityPrincipal;
import io.github.coco.feature.web.exception.CocoFilterExceptionResponseWriter;
import io.github.coco.feature.web.exception.CocoWebExceptionHandler;
import io.github.coco.feature.web.exception.DefaultCocoExceptionHttpStatusResolver;
import io.github.coco.feature.web.response.CocoSystemCodes;
import io.github.coco.i18n.CocoMessage;
import io.github.coco.i18n.CocoMessageService;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CocoIdempotencyAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CocoIdempotencyAutoConfiguration.class))
            .withUserConfiguration(WebWriterConfiguration.class);

    private final WebApplicationContextRunner realCocoWebContextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CocoCommonAutoConfiguration.class,
                    CocoWebAutoConfiguration.class, CocoIdempotencyAutoConfiguration.class))
            .withPropertyValues("coco.common.i18n.basename=coco-messages");

    @AfterEach
    void clearSecurityContext() {
        CocoSecurityContextHolder.clear();
    }

    @Test
    void disabledByDefault() {
        this.contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(CocoIdempotencyStore.class);
            assertThat(context).doesNotHaveBean("cocoIdempotencyFilterRegistration");
        });
    }

    @Test
    void enabledRoutesRegisterBoundedStoreAndSynchronousFilter() {
        this.contextRunner.withPropertyValues(enabledRouteProperties()).run(context -> {
            assertThat(context).hasSingleBean(CocoIdempotencyProperties.class);
            assertThat(context).hasSingleBean(CocoIdempotencyRouteMatcher.class);
            assertThat(context).hasSingleBean(CocoIdempotencyScopeResolver.class);
            assertThat(context).hasSingleBean(CocoIdempotencyStore.class);
            assertThat(context.getBean(CocoIdempotencyStore.class))
                    .isInstanceOf(InMemoryCocoIdempotencyStore.class);

            @SuppressWarnings("unchecked")
            FilterRegistrationBean<CocoIdempotencyFilter> registration = context.getBean(
                    "cocoIdempotencyFilterRegistration", FilterRegistrationBean.class);
            assertThat(registration.isAsyncSupported()).isFalse();
            assertThat(registration.getFilter()).isInstanceOf(CocoIdempotencyFilter.class);
        });
    }

    @Test
    void webDisabledSuppressesIdempotencyAutoConfiguration() {
        this.contextRunner.withPropertyValues(enabledRouteProperties())
                .withPropertyValues("coco.features.disabled[0]=web")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(CocoIdempotencyProperties.class);
                    assertThat(context).doesNotHaveBean(CocoIdempotencyStore.class);
                    assertThat(context).doesNotHaveBean("cocoIdempotencyFilterRegistration");
                });
    }

    @Test
    void defaultScopeResolverUsesOnlyAuthenticatedSecurityContext() {
        this.contextRunner.withPropertyValues(enabledRouteProperties()).run(context -> {
            CocoIdempotencyScopeResolver resolver = context.getBean(CocoIdempotencyScopeResolver.class);
            CocoSecurityContextHolder.set(CocoSecurityContext.authenticated(
                    CocoSecurityPrincipal.of("principal-42", "Principal 42")));

            assertThat(resolver.resolve(new MockHttpServletRequest())).isEqualTo("principal-42");
        });
    }

    @Test
    void businessScopeResolverReplacesSecurityContextDefault() {
        this.contextRunner.withUserConfiguration(CustomScopeConfiguration.class)
                .withPropertyValues(enabledRouteProperties())
                .run(context -> assertThat(context.getBean(CocoIdempotencyScopeResolver.class)
                        .resolve(new MockHttpServletRequest())).isEqualTo("business-scope"));
    }

    @Test
    void customStoreReplacesOnlyReferenceImplementation() {
        this.contextRunner.withUserConfiguration(CustomStoreConfiguration.class)
                .withPropertyValues(enabledRouteProperties())
                .run(context -> {
                    assertThat(context).hasSingleBean(CocoIdempotencyStore.class);
                    assertThat(context.getBean(CocoIdempotencyStore.class)).isInstanceOf(TestStore.class);
                    assertThat(context).hasBean("cocoIdempotencyFilterRegistration");
                });
    }

    @Test
    void enabledWithoutRoutesFailsStartup() {
        this.contextRunner.withPropertyValues("coco.idempotency.enabled=true")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseInstanceOf(IllegalArgumentException.class)
                        .rootCause().hasMessageContaining("routes must contain at least one route"));
    }

    @Test
    void realCocoWebWriterResolvesPayloadMismatchTo422() {
        this.realCocoWebContextRunner.withPropertyValues(enabledRouteProperties()).run(context -> {
            assertThat(context).hasSingleBean(CocoFilterExceptionResponseWriter.class);
            assertThat(context.getBean(CocoExceptionHttpStatusResolver.class))
                    .isInstanceOf(CocoIdempotencyExceptionHttpStatusResolver.class);

            authenticate("real-422-principal");
            CocoIdempotencyFilter filter = registeredFilter(context);
            MockHttpServletResponse first = new MockHttpServletResponse();
            assertThatCode(() -> filter.doFilter(request("real-422", "one"), first,
                    (request, response) -> response.getWriter().write("done")))
                    .doesNotThrowAnyException();

            CommittingHttpServletResponse mismatch = new CommittingHttpServletResponse();
            assertThatCode(() -> filter.doFilter(request("real-422", "two"), mismatch,
                    (request, response) -> response.getWriter().write("unexpected")))
                    .doesNotThrowAnyException();

            assertThat(mismatch.getStatus()).isEqualTo(422);
            assertThat(mismatch.getContentType()).startsWith("application/json");
            assertThat(mismatch.isCommitted()).isTrue();
            assertThat(mismatch.statusWasSetAfterCommit()).isFalse();
            assertThat(mismatch.contentAsString())
                    .contains("\"code\":422")
                    .contains("\"success\":false");
        });
    }

    @Test
    void realCocoWebWriterResolvesStoreFailureTo503() {
        this.realCocoWebContextRunner.withUserConfiguration(FailingStoreConfiguration.class)
                .withPropertyValues(enabledRouteProperties()).run(context -> {
                    authenticate("real-503-principal");
                    CocoIdempotencyFilter filter = registeredFilter(context);
                    CommittingHttpServletResponse response = new CommittingHttpServletResponse();

                    assertThatCode(() -> filter.doFilter(request("real-503", "same"), response,
                            (request, servletResponse) -> servletResponse.getWriter().write("unexpected")))
                            .doesNotThrowAnyException();

                    assertThat(response.getStatus()).isEqualTo(503);
                    assertThat(response.getContentType()).startsWith("application/json");
                    assertThat(response.isCommitted()).isTrue();
                    assertThat(response.statusWasSetAfterCommit()).isFalse();
                    assertThat(response.contentAsString())
                            .contains("\"code\":503")
                            .contains("\"success\":false");
                });
    }

    @Test
    void realCocoWebWriterResolvesCapacityExceededTo503() {
        this.realCocoWebContextRunner.withPropertyValues(enabledRouteProperties())
                .withPropertyValues("coco.idempotency.max-entries=1")
                .run(context -> {
                    authenticate("real-capacity-principal");
                    CocoIdempotencyFilter filter = registeredFilter(context);
                    assertThatCode(() -> filter.doFilter(request("real-capacity-first", "same"),
                            new MockHttpServletResponse(),
                            (request, response) -> response.getWriter().write("done")))
                            .doesNotThrowAnyException();

                    CommittingHttpServletResponse response = new CommittingHttpServletResponse();
                    assertThatCode(() -> filter.doFilter(request("real-capacity-second", "same"), response,
                            (request, servletResponse) -> servletResponse.getWriter().write("unexpected")))
                            .doesNotThrowAnyException();

                    assertThat(response.getStatus()).isEqualTo(503);
                    assertThat(response.getContentType()).startsWith("application/json");
                    assertThat(response.isCommitted()).isTrue();
                    assertThat(response.statusWasSetAfterCommit()).isFalse();
                    assertThat(response.contentAsString())
                            .contains("\"code\":503")
                            .contains("\"success\":false");
                });
    }

    private static String[] enabledRouteProperties() {
        return new String[] {
                "coco.idempotency.enabled=true",
                "coco.idempotency.routes[0].methods[0]=POST",
                "coco.idempotency.routes[0].path-patterns[0]=/orders/**"
        };
    }

    @SuppressWarnings("unchecked")
    private static CocoIdempotencyFilter registeredFilter(org.springframework.context.ApplicationContext context) {
        FilterRegistrationBean<CocoIdempotencyFilter> registration = context.getBean(
                "cocoIdempotencyFilterRegistration", FilterRegistrationBean.class);
        return registration.getFilter();
    }

    private static MockHttpServletRequest request(String key, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/orders/create");
        request.addHeader("Idempotency-Key", key);
        request.setContent(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return request;
    }

    private static void authenticate(String principalId) {
        CocoSecurityContextHolder.set(CocoSecurityContext.authenticated(
                CocoSecurityPrincipal.of(principalId, principalId)));
    }

    @Configuration(proxyBeanMethods = false)
    static class WebWriterConfiguration {

        @Bean
        CocoFilterExceptionResponseWriter cocoFilterExceptionResponseWriter() {
            return new CocoFilterExceptionResponseWriter(new CocoWebExceptionHandler(new StaticMessageService(),
                    new DefaultCocoExceptionHttpStatusResolver(), CocoSystemCodes.defaults()), new ObjectMapper());
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomScopeConfiguration {

        @Bean
        CocoIdempotencyScopeResolver customIdempotencyScopeResolver() {
            return request -> "business-scope";
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomStoreConfiguration {

        @Bean
        CocoIdempotencyStore customIdempotencyStore() {
            return new TestStore();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class FailingStoreConfiguration {

        @Bean
        CocoIdempotencyStore failingIdempotencyStore() {
            return new TestStore() {
                @Override
                public CocoIdempotencyAcquireResult acquire(CocoIdempotencyRequest request, Instant now,
                        Instant expiresAt) {
                    throw new IllegalStateException("store unavailable");
                }
            };
        }
    }

    private static class TestStore implements CocoIdempotencyStore {

        @Override
        public CocoIdempotencyAcquireResult acquire(CocoIdempotencyRequest request, Instant now, Instant expiresAt) {
            throw new UnsupportedOperationException("not used by auto-configuration test");
        }

        @Override
        public boolean complete(CocoIdempotencyLease lease, CocoIdempotencyStoredResponse response, Instant now) {
            return false;
        }

        @Override
        public boolean fail(CocoIdempotencyLease lease, Instant now) {
            return false;
        }
    }

    private static final class CommittingHttpServletResponse extends HttpServletResponseWrapper {

        private final MockHttpServletResponse delegate;

        private ServletOutputStream outputStream;

        private boolean statusSetAfterCommit;

        private CommittingHttpServletResponse() {
            this(new MockHttpServletResponse());
        }

        private CommittingHttpServletResponse(MockHttpServletResponse delegate) {
            super(delegate);
            this.delegate = delegate;
        }

        @Override
        public void setStatus(int status) {
            if (isCommitted()) {
                this.statusSetAfterCommit = true;
            }
            super.setStatus(status);
        }

        @Override
        public ServletOutputStream getOutputStream() throws java.io.IOException {
            if (this.outputStream == null) {
                this.outputStream = new CommitOnWriteServletOutputStream(this.delegate.getOutputStream(), this.delegate);
            }
            return this.outputStream;
        }

        private String contentAsString() {
            try {
                return this.delegate.getContentAsString();
            }
            catch (java.io.UnsupportedEncodingException ex) {
                throw new AssertionError(ex);
            }
        }

        private boolean statusWasSetAfterCommit() {
            return this.statusSetAfterCommit;
        }
    }

    private static final class CommitOnWriteServletOutputStream extends ServletOutputStream {

        private final ServletOutputStream delegate;

        private final MockHttpServletResponse response;

        private CommitOnWriteServletOutputStream(ServletOutputStream delegate, MockHttpServletResponse response) {
            this.delegate = delegate;
            this.response = response;
        }

        @Override
        public boolean isReady() {
            return this.delegate.isReady();
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
            this.delegate.setWriteListener(writeListener);
        }

        @Override
        public void write(int value) throws java.io.IOException {
            this.delegate.write(value);
            this.response.flushBuffer();
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws java.io.IOException {
            this.delegate.write(bytes, offset, length);
            this.response.flushBuffer();
        }
    }

    private static final class StaticMessageService implements CocoMessageService {

        @Override
        public String getMessage(String code, Object... args) {
            return code;
        }

        @Override
        public String getMessage(String code, Locale locale, Object... args) {
            return code;
        }

        @Override
        public String getMessageOrDefault(String code, String defaultMessage, Object... args) {
            return code;
        }

        @Override
        public String getMessageOrDefault(String code, String defaultMessage, Locale locale, Object... args) {
            return code;
        }

        @Override
        public String resolve(CocoMessage message) {
            return message == null ? "" : message.code();
        }

        @Override
        public String resolve(CocoMessage message, Locale locale) {
            return resolve(message);
        }
    }
}
