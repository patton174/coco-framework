package io.github.coco.feature.web.cors;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.web.filter.CorsFilter;

/**
 * {@link CocoCorsAutoConfiguration} 单元测试。
 *
 * @author patton174
 * @since 1.1.0
 */
class CocoCorsAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CocoCorsAutoConfiguration.class));

    @Test
    void doesNotRegisterCorsFilterByDefault() {
        this.contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(CorsFilter.class);
        });
    }

    @Test
    void registersCorsFilterWhenEnabled() {
        this.contextRunner
                .withPropertyValues("coco.web.cors.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(CorsFilter.class);
                });
    }

    @Test
    void customPropertiesBindCorrectly() {
        this.contextRunner
                .withPropertyValues(
                        "coco.web.cors.enabled=true",
                        "coco.web.cors.allowed-origins=https://example.com,https://app.example.com",
                        "coco.web.cors.allowed-methods=GET,POST",
                        "coco.web.cors.allowed-headers=Authorization,Content-Type",
                        "coco.web.cors.exposed-headers=X-Custom-Header",
                        "coco.web.cors.allow-credentials=true",
                        "coco.web.cors.max-age=3600")
                .run(context -> {
                    assertThat(context).hasSingleBean(CorsFilter.class);
                    assertThat(context).hasSingleBean(CocoCorsProperties.class);
                    CocoCorsProperties properties = context.getBean(CocoCorsProperties.class);
                    assertThat(properties.getAllowedOrigins())
                            .containsExactly("https://example.com", "https://app.example.com");
                    assertThat(properties.getAllowedMethods())
                            .containsExactly("GET", "POST");
                    assertThat(properties.getAllowedHeaders())
                            .containsExactly("Authorization", "Content-Type");
                    assertThat(properties.getExposedHeaders())
                            .containsExactly("X-Custom-Header");
                    assertThat(properties.isAllowCredentials()).isTrue();
                    assertThat(properties.getMaxAge()).isEqualTo(3600L);
                });
    }
}
