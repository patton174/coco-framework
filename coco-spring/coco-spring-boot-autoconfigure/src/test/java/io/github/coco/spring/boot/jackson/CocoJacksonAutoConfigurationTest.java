package io.github.coco.spring.boot.jackson;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CocoJacksonAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    CocoJacksonAutoConfiguration.class, JacksonAutoConfiguration.class));

    @Test
    void registersCocoJacksonCustomizerBean() {
        this.contextRunner.run(context ->
                assertTrue(context.containsBean("cocoJacksonCustomizer")));
    }

    @Test
    void defaultConfigApplied() {
        this.contextRunner.run(context -> {
            assertNotNull(context.getBean("cocoJacksonCustomizer"));
        });
    }
}
