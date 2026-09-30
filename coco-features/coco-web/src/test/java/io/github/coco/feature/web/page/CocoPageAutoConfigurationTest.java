package io.github.coco.feature.web.page;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * {@link CocoPageAutoConfiguration} 单元测试。
 *
 * @author patton174
 * @since 1.1.0
 */
class CocoPageAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CocoPageAutoConfiguration.class));

    @Test
    void defaultRegistersWebMvcConfigurer() {
        this.contextRunner.run(context -> {
            assertThat(context).hasBean("cocoPageMvcConfigurer");
            assertThat(context).hasSingleBean(WebMvcConfigurer.class);
        });
    }

    @Test
    void disabledWithPropertyDoesNotRegisterBean() {
        this.contextRunner
                .withPropertyValues("coco.web.page.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean("cocoPageMvcConfigurer");
                    assertThat(context).doesNotHaveBean(WebMvcConfigurer.class);
                });
    }
}
