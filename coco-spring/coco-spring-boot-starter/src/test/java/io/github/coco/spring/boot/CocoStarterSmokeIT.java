package io.github.coco.spring.boot;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import io.github.coco.i18n.CocoMessageService;
import io.github.coco.feature.web.context.CocoWebRequestContextResolver;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

class CocoStarterSmokeIT {

    @Test
    void startsServletApplicationWithStarterDefaults() {
        SpringApplication application = new SpringApplication(StarterSmokeApplication.class);
        application.setWebApplicationType(WebApplicationType.SERVLET);
        application.setLogStartupInfo(false);
        application.setDefaultProperties(Map.of(
                "server.port", "0",
                "spring.main.banner-mode", "off",
                "coco.features.disabled[0]", "mybatis-plus",
                "coco.features.disabled[1]", "tenant",
                "coco.features.disabled[2]", "data-permission"));

        try (ConfigurableApplicationContext context = application.run()) {
            assertTrue(context.containsBean("cocoStartupBanner"));
            assertNotNull(context.getBean(CocoMessageService.class));
            assertNotNull(context.getBean(CocoWebRequestContextResolver.class));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class StarterSmokeApplication {
    }
}
