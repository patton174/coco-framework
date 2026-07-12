package io.github.coco.consumer;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import io.github.coco.api.feature.CocoFeature;
import io.github.coco.feature.audit.CocoAuditAutoConfiguration;
import io.github.coco.feature.datapermission.CocoDataPermissionAutoConfiguration;
import io.github.coco.feature.model.CocoFeaturePlan;
import io.github.coco.feature.mybatisplus.CocoMybatisPlusAutoConfiguration;
import io.github.coco.feature.openapi.CocoOpenApiAutoConfiguration;
import io.github.coco.feature.security.CocoSecurityAutoConfiguration;
import io.github.coco.feature.tenant.CocoTenantAutoConfiguration;
import io.github.coco.feature.web.CocoWebAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.assertj.AssertableWebApplicationContext;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.ConfigurableWebApplicationContext;

public final class RuntimeFeatureRegistrationConsumer {

    private static final List<FeatureRegistration> FEATURE_REGISTRATIONS = List.of(
            new FeatureRegistration(CocoFeature.WEB, CocoWebAutoConfiguration.class),
            new FeatureRegistration(CocoFeature.MYBATIS_PLUS, CocoMybatisPlusAutoConfiguration.class),
            new FeatureRegistration(CocoFeature.TENANT, CocoTenantAutoConfiguration.class),
            new FeatureRegistration(CocoFeature.DATA_PERMISSION, CocoDataPermissionAutoConfiguration.class),
            new FeatureRegistration(CocoFeature.AUDIT, CocoAuditAutoConfiguration.class),
            new FeatureRegistration(CocoFeature.SECURITY, CocoSecurityAutoConfiguration.class),
            new FeatureRegistration(CocoFeature.OPENAPI, CocoOpenApiAutoConfiguration.class));

    private RuntimeFeatureRegistrationConsumer() {
    }

    public static void main(String[] arguments) {
        String profile = arguments.length == 1 ? arguments[0] : "unspecified";
        try {
            new WebApplicationContextRunner()
                    .withPropertyValues(
                            "spring.autoconfigure.exclude="
                                    + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
                    .withUserConfiguration(ProbeApplication.class)
                    .run(context -> verifyContext(context, profile));
        }
        catch (Throwable failure) {
            reportFailure(profile, failure);
            System.exit(1);
        }
    }

    private static void verifyContext(AssertableWebApplicationContext context, String profile) {
        if (context.getStartupFailure() != null) {
            throw new AssertionError("Servlet Web ApplicationContext refresh failed", context.getStartupFailure());
        }

        ConfigurableApplicationContext sourceContext = context.getSourceApplicationContext();
        if (!(sourceContext instanceof ConfigurableWebApplicationContext webContext)) {
            throw new AssertionError("Expected a ConfigurableWebApplicationContext but got "
                    + sourceContext.getClass().getName());
        }
        if (webContext.getServletContext() == null) {
            throw new AssertionError("Refreshed WebApplicationContext has no ServletContext");
        }

        Map<String, CocoFeaturePlan> featurePlanBeans = context.getBeansOfType(CocoFeaturePlan.class, false, false);
        if (featurePlanBeans.size() != 1) {
            throw new AssertionError("Expected one CocoFeaturePlan bean but found " + featurePlanBeans.keySet());
        }
        CocoFeaturePlan featurePlan = featurePlanBeans.values().iterator().next();

        List<String> autoConfigurationBeans = FEATURE_REGISTRATIONS.stream()
                .map(registration -> verifyRegistration(context, featurePlan, registration))
                .toList();
        System.out.println("COCO_RUNTIME_REGISTRATION_OK profile=" + profile
                + " context=" + sourceContext.getClass().getName()
                + " featurePlanBeans=" + featurePlanBeans.keySet()
                + " features=" + FEATURE_REGISTRATIONS.stream()
                        .map(registration -> registration.feature().id())
                        .toList()
                + " autoConfigurations=" + autoConfigurationBeans);
    }

    private static String verifyRegistration(AssertableWebApplicationContext context, CocoFeaturePlan featurePlan,
            FeatureRegistration registration) {
        long definitionCount = featurePlan.definitions().stream()
                .filter(definition -> definition.feature() == registration.feature())
                .count();
        if (definitionCount != 1) {
            throw new AssertionError("Expected one feature-plan registration for " + registration.feature().id()
                    + " but found " + definitionCount);
        }
        if (!featurePlan.isEnabled(registration.feature())) {
            throw new AssertionError("Feature is not enabled in the refreshed context: "
                    + registration.feature().id());
        }

        String[] beanNames = context.getBeanNamesForType(registration.autoConfigurationType(), false, false);
        if (beanNames.length != 1) {
            throw new AssertionError("Expected one " + registration.autoConfigurationType().getName()
                    + " bean but found " + Arrays.toString(beanNames));
        }
        return registration.feature().id() + "=" + beanNames[0];
    }

    private static void reportFailure(String profile, Throwable failure) {
        Throwable rootCause = failure;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        System.err.println("COCO_RUNTIME_REGISTRATION_FAILED profile=" + profile
                + " rootCause=" + rootCause.getClass().getName() + ": " + rootCause.getMessage());
        int depth = 0;
        for (Throwable cause = failure; cause != null && depth < 32; cause = cause.getCause()) {
            System.err.println("cause[" + depth + "]=" + cause.getClass().getName() + ": " + cause.getMessage());
            depth++;
        }
        failure.printStackTrace(System.err);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class ProbeApplication {
    }

    private record FeatureRegistration(CocoFeature feature, Class<?> autoConfigurationType) {
    }
}
