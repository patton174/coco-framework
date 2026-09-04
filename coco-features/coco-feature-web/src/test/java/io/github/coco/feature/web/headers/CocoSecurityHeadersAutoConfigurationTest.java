package io.github.coco.feature.web.headers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;

/**
 * {@link CocoSecurityHeadersAutoConfiguration} 单元测试。
 *
 * @author patton174
 * @since 1.1.0
 */
class CocoSecurityHeadersAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CocoSecurityHeadersAutoConfiguration.class));

    @Test
    void registersSecurityHeadersFilterByDefault() {
        this.contextRunner.run(context -> {
            assertThat(context).hasSingleBean(FilterRegistrationBean.class);
            assertThat(context).hasSingleBean(CocoSecurityHeadersProperties.class);
            assertThat(registration(context).getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
        });
    }

    @Test
    void doesNotRegisterSecurityHeadersFilterWhenDisabled() {
        this.contextRunner
                .withPropertyValues("coco.web.security-headers.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(FilterRegistrationBean.class);
                    assertThat(context).doesNotHaveBean(CocoSecurityHeadersProperties.class);
                });
    }

    @Test
    void appliesCustomOrder() {
        this.contextRunner
                .withPropertyValues("coco.web.security-headers.order=42")
                .run(context -> {
                    assertThat(registration(context).getOrder()).isEqualTo(42);
                });
    }

    @Test
    void bindsCustomContentSecurityPolicy() {
        this.contextRunner
                .withPropertyValues(
                        "coco.web.security-headers.content-security-policy=default-src 'none'")
                .run(context -> {
                    assertThat(context).hasSingleBean(CocoSecurityHeadersProperties.class);
                    CocoSecurityHeadersProperties properties = context
                            .getBean(CocoSecurityHeadersProperties.class);
                    assertThat(properties.getContentSecurityPolicy()).isEqualTo("default-src 'none'");
                });
    }

    @SuppressWarnings("rawtypes")
    private static FilterRegistrationBean registration(
            org.springframework.context.ApplicationContext context) {
        return context.getBean(FilterRegistrationBean.class);
    }
}
