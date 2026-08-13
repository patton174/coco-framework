package io.github.coco.security.apikey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.coco.feature.security.context.CocoSecurityContextHolder;
import io.github.coco.feature.security.context.CocoSecurityPrincipal;
import io.github.coco.feature.security.web.CocoSecurityWebFilter;
import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CocoApiKeyWebSecurityContextResolverTest {

    private static final String API_KEY = "api-key-123";

    private static final String API_KEY_SHA_256 = "074c1fd1ac9d1c67ec22e8ae841db4c570a2740372e70b0bc3c763416cac9ca0";

    @Test
    void resolvesConfiguredPrincipalAndCachesAuthenticationAcrossDispatches() {
        CocoApiKeyProperties properties = enabledProperties();
        AtomicInteger verifications = new AtomicInteger();
        CocoApiKeyVerifier verifier = key -> {
            verifications.incrementAndGet();
            return new DefaultCocoApiKeyVerifier(properties.getCredentials()).verify(key);
        };
        CocoApiKeyWebSecurityContextResolver resolver = new CocoApiKeyWebSecurityContextResolver(properties, verifier);
        MockHttpServletRequest request = requestWith(API_KEY);

        assertThat(resolver.resolve(request)).get().satisfies(context -> {
            assertThat(context.authenticated()).isTrue();
            assertThat(context.principal().principalId()).isEqualTo("integration-service");
            assertThat(context.principal().principalName()).isEqualTo("Orders integration");
            assertThat(context.principal().roles()).containsExactly("INTEGRATION");
            assertThat(context.principal().permissions()).containsExactly("orders:read");
            assertThat(context.principal().attributes()).containsEntry("tenant", "internal")
                    .doesNotContainKey("orders-service");
        });
        assertThat(resolver.resolve(request)).isPresent();
        assertThat(verifications).hasValue(1);
    }

    @Test
    void rejectsMissingInvalidOversizedAndAmbiguousHeadersWithoutExposingCredentials() {
        CocoApiKeyWebSecurityContextResolver resolver = new CocoApiKeyWebSecurityContextResolver(enabledProperties(),
                new DefaultCocoApiKeyVerifier(enabledProperties().getCredentials()));
        MockHttpServletRequest invalid = requestWith("not-the-key");
        MockHttpServletRequest oversized = requestWith("x".repeat(513));
        MockHttpServletRequest ambiguous = requestWith(API_KEY);
        ambiguous.addHeader("X-API-Key", "second-key");

        assertRejected(resolver, new MockHttpServletRequest("GET", "/orders"));
        assertRejected(resolver, invalid);
        assertRejected(resolver, oversized);
        assertRejected(resolver, ambiguous);
    }

    @Test
    void allowsMissingKeyWhenNotRequiredButStillRejectsPresentedInvalidKey() {
        CocoApiKeyProperties properties = enabledProperties();
        properties.setRequired(false);
        properties.afterPropertiesSet();
        CocoApiKeyWebSecurityContextResolver resolver = new CocoApiKeyWebSecurityContextResolver(properties,
                new DefaultCocoApiKeyVerifier(properties.getCredentials()));

        assertThat(resolver.resolve(new MockHttpServletRequest("GET", "/orders"))).isEmpty();
        assertRejected(resolver, requestWith("not-the-key"));
        assertRejected(resolver, requestWith(" "));
        assertRejected(resolver, requestWith("x".repeat(513)));
    }

    @Test
    void usesConstantTimeDigestComparisonAndDoesNotRetainCredentialId() {
        CocoApiKeyProperties properties = enabledProperties();
        DefaultCocoApiKeyVerifier verifier = new DefaultCocoApiKeyVerifier(properties.getCredentials());

        assertThat(verifier.verify(API_KEY)).get()
                .extracting(CocoSecurityPrincipal::attributes)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .doesNotContainKey("orders-service");
        assertThat(verifier.verify("different-key")).isEmpty();
    }

    @Test
    void defaultVerifierBytecodeUsesMessageDigestIsEqual() throws Exception {
        String classResource = "/" + DefaultCocoApiKeyVerifier.class.getName().replace('.', '/') + ".class";
        try (java.io.InputStream input = DefaultCocoApiKeyVerifier.class.getResourceAsStream(classResource)) {
            assertThat(input).isNotNull();
            String bytecode = new String(input.readAllBytes(), StandardCharsets.ISO_8859_1);
            assertThat(bytecode).contains("java/security/MessageDigest", "isEqual");
        }
    }

    @Test
    void reusesRequestAuthenticationForAsyncDispatchAndCleansCoreContext() throws Exception {
        CocoApiKeyProperties properties = enabledProperties();
        AtomicInteger verifications = new AtomicInteger();
        CocoApiKeyVerifier verifier = key -> {
            verifications.incrementAndGet();
            return new DefaultCocoApiKeyVerifier(properties.getCredentials()).verify(key);
        };
        CocoSecurityWebFilter filter = new CocoSecurityWebFilter(
                new CocoApiKeyWebSecurityContextResolver(properties, verifier));
        MockHttpServletRequest request = requestWith(API_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> assertThat(
                CocoSecurityContextHolder.requireCurrent().principal().principalId()).isEqualTo("integration-service"));
        request.setDispatcherType(DispatcherType.ASYNC);
        filter.doFilter(request, response, (servletRequest, servletResponse) -> assertThat(
                CocoSecurityContextHolder.requireCurrent().principal().principalId()).isEqualTo("integration-service"));

        assertThat(verifications).hasValue(1);
        assertThat(CocoSecurityContextHolder.current()).isEmpty();
    }

    private static void assertRejected(CocoApiKeyWebSecurityContextResolver resolver, MockHttpServletRequest request) {
        assertThatThrownBy(() -> resolver.resolve(request))
                .isInstanceOf(CocoApiKeyAuthenticationException.class)
                .hasMessageNotContaining(API_KEY)
                .hasMessageNotContaining(API_KEY_SHA_256);
    }

    private static MockHttpServletRequest requestWith(String key) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders");
        request.addHeader("X-API-Key", key);
        return request;
    }

    static CocoApiKeyProperties enabledProperties() {
        CocoApiKeyProperties properties = new CocoApiKeyProperties();
        properties.setEnabled(true);
        CocoApiKeyProperties.Credential credential = new CocoApiKeyProperties.Credential();
        credential.setSha256(API_KEY_SHA_256.toUpperCase());
        credential.setPrincipalId("integration-service");
        credential.setPrincipalName("Orders integration");
        credential.setRoles(java.util.Set.of("INTEGRATION"));
        credential.setPermissions(java.util.Set.of("orders:read"));
        credential.setAttributes(Map.of("tenant", "internal"));
        properties.setCredentials(Map.of("orders-service", credential));
        properties.afterPropertiesSet();
        return properties;
    }
}
