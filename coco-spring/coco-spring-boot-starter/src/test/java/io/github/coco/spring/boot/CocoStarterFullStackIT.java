package io.github.coco.spring.boot;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import javax.sql.DataSource;

import io.github.coco.api.feature.CocoFeature;
import io.github.coco.audit.core.CocoAuditPublisher;
import io.github.coco.datapermission.context.CocoDataPermissionContextResolver;
import io.github.coco.openapi.core.CocoOpenApiMetadataProvider;
import io.github.coco.security.context.CocoSecurityContextResolver;
import io.github.coco.spring.boot.autoconfigure.feature.CocoFeatureManager;
import io.github.coco.tenant.context.CocoTenantContextResolver;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

class CocoStarterFullStackIT {

    @Test
    void startsWithAllStandardFeatures() {
        SpringApplication application = new SpringApplication(FullStackApplication.class);
        application.setWebApplicationType(WebApplicationType.SERVLET);
        application.setLogStartupInfo(false);
        application.setDefaultProperties(Map.of(
                "server.port", "0",
                "spring.main.banner-mode", "off",
                "spring.datasource.url", "jdbc:h2:mem:coco-starter;DB_CLOSE_DELAY=-1",
                "spring.datasource.username", "sa",
                "spring.datasource.password", "",
                "spring.sql.init.mode", "never"));

        try (ConfigurableApplicationContext context = application.run()) {
            CocoFeatureManager featureManager = context.getBean(CocoFeatureManager.class);

            assertTrue(featureManager.enabledFeatures().containsAll(java.util.EnumSet.allOf(CocoFeature.class)));
            assertNotNull(context.getBean(DataSource.class));
            assertNotNull(context.getBean(CocoSecurityContextResolver.class));
            assertNotNull(context.getBean(CocoTenantContextResolver.class));
            assertNotNull(context.getBean(CocoDataPermissionContextResolver.class));
            assertNotNull(context.getBean(CocoAuditPublisher.class));
            assertNotNull(context.getBean(CocoOpenApiMetadataProvider.class));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class FullStackApplication {
    }
}
