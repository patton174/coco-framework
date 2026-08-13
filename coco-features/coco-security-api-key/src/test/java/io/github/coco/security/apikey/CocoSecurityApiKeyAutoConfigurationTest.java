package io.github.coco.security.apikey;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coco.common.autoconfigure.CocoCommonAutoConfiguration;
import io.github.coco.feature.security.CocoSecurityAutoConfiguration;
import io.github.coco.feature.security.context.CocoSecurityContext;
import io.github.coco.feature.security.web.CocoSecurityWebFilter;
import io.github.coco.feature.security.web.CocoWebSecurityContextResolver;
import io.github.coco.feature.web.CocoWebAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.mock.web.MockHttpServletRequest;

class CocoSecurityApiKeyAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    CocoCommonAutoConfiguration.class,
                    CocoWebAutoConfiguration.class,
                    CocoSecurityApiKeyAutoConfiguration.class,
                    CocoSecurityAutoConfiguration.class))
            .withPropertyValues(
                    "coco.common.i18n.basename=coco-messages",
                    "coco.security.api-key.enabled=true",
                    "coco.security.api-key.credentials.orders-service.sha256="
                            + "074c1fd1ac9d1c67ec22e8ae841db4c570a2740372e70b0bc3c763416cac9ca0",
                    "coco.security.api-key.credentials.orders-service.principal-id=integration-service");

    @Test
    void disabledModuleHasNoApiKeyBeans() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CocoSecurityApiKeyAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(CocoApiKeyVerifier.class);
                    assertThat(context).doesNotHaveBean("cocoApiKeyAuthenticationFilterRegistration");
                });
    }

    @Test
    void registersDefaultVerifierResolverAndFilterBeforeCoreSecurityBridge() {
        this.contextRunner.run(context -> {
            assertThat(context).hasSingleBean(CocoApiKeyVerifier.class);
            assertThat(context).hasSingleBean(CocoWebSecurityContextResolver.class);
            assertThat(context).hasBean("cocoApiKeyAuthenticationFilterRegistration");
            FilterRegistrationBean<?> apiKeyFilter = context.getBean("cocoApiKeyAuthenticationFilterRegistration",
                    FilterRegistrationBean.class);
            FilterRegistrationBean<?> coreFilter = context.getBean("cocoSecurityWebFilterRegistration",
                    FilterRegistrationBean.class);
            assertThat(apiKeyFilter.getOrder()).isLessThan(coreFilter.getOrder());
            assertThat(apiKeyFilter).isNotNull();
        });
    }

    @Test
    void userVerifierOverridesDefault() {
        CocoApiKeyVerifier verifier = key -> java.util.Optional.of(
                io.github.coco.feature.security.context.CocoSecurityPrincipal.of("custom", "Custom"));
        this.contextRunner.withBean(CocoApiKeyVerifier.class, () -> verifier).run(context -> {
            assertThat(context).hasSingleBean(CocoApiKeyVerifier.class);
            assertThat(context.getBean(CocoApiKeyVerifier.class)).isSameAs(verifier);
            CocoWebSecurityContextResolver resolver = context.getBean(CocoWebSecurityContextResolver.class);
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders");
            request.addHeader("X-API-Key", "any-key");
            assertThat(resolver.resolve(request)).get()
                    .extracting(CocoSecurityContext::principal)
                    .extracting(principal -> principal.principalId())
                    .isEqualTo("custom");
        });
    }

    @Test
    void userResolverMakesEntireDefaultApiKeyWiringBackOff() {
        CocoWebSecurityContextResolver resolver = request -> java.util.Optional.empty();
        this.contextRunner.withBean(CocoWebSecurityContextResolver.class, () -> resolver).run(context -> {
            assertThat(context).hasSingleBean(CocoWebSecurityContextResolver.class);
            assertThat(context.getBean(CocoWebSecurityContextResolver.class)).isSameAs(resolver);
            assertThat(context).doesNotHaveBean("cocoApiKeyAuthenticationFilterRegistration");
        });
    }

    @Test
    void failsFastWhenJwtIsAlsoEnabled() {
        this.contextRunner.withPropertyValues("coco.security.jwt.enabled=true").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasMessageContaining("Authentication mechanisms conflict")
                    .hasMessageNotContaining("api-key-123")
                    .hasMessageNotContaining("074c1fd1ac9d1c67ec22e8ae841db4c570a2740372e70b0bc3c763416cac9ca0");
        });
    }
}
