package io.github.coco.feature.cors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Coco Servlet CORS 自动配置测试。
 *
 * @author patton174
 * @since 2.0.1
 */
class CocoCorsAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CocoCorsAutoConfiguration.class));

    @Test
    void staysDisabledByDefault() {
        this.contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean("cocoCorsConfigurationSource");
            assertThat(context).doesNotHaveBean("cocoCorsFilterRegistration");
        });
    }

    @Test
    void backsOffWhenWebFeatureIsDisabled() {
        this.contextRunner
                .withPropertyValues(
                        "coco.cors.enabled=true",
                        "coco.cors.allowed-origins[0]=https://console.example.com",
                        "coco.features.disabled[0]=web")
                .run(context -> {
                    assertThat(context).doesNotHaveBean("cocoCorsConfigurationSource");
                    assertThat(context).doesNotHaveBean("cocoCorsFilterRegistration");
                });
    }

    @Test
    void appliesSpringCorsSemanticsToPreflightAndActualRequests() {
        this.contextRunner
                .withPropertyValues(
                        "coco.cors.enabled=true",
                        "coco.cors.allowed-origins[0]=https://console.example.com",
                        "coco.cors.allowed-methods[0]=GET",
                        "coco.cors.allowed-headers[0]=X-Request-Id",
                        "coco.cors.exposed-headers[0]=X-Trace-Id",
                        "coco.cors.allow-credentials=true",
                        "coco.cors.max-age=600",
                        "coco.cors.path-patterns[0]=/api/**")
                .run(context -> {
                    MockMvc mockMvc = mockMvc(context.getBean("cocoCorsFilterRegistration",
                            FilterRegistrationBean.class));

                    mockMvc.perform(options("/api/orders")
                                    .header("Origin", "https://console.example.com")
                                    .header("Access-Control-Request-Method", "GET")
                                    .header("Access-Control-Request-Headers", "X-Request-Id"))
                            .andExpect(status().isOk())
                            .andExpect(header().string("Access-Control-Allow-Origin",
                                    "https://console.example.com"))
                            .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                            .andExpect(header().string("Access-Control-Allow-Methods", "GET"))
                            .andExpect(header().string("Access-Control-Allow-Headers", "X-Request-Id"))
                            .andExpect(header().string("Access-Control-Max-Age", "600"));

                    mockMvc.perform(get("/api/orders")
                                    .header("Origin", "https://console.example.com"))
                            .andExpect(status().isOk())
                            .andExpect(content().string("ok"))
                            .andExpect(header().string("Access-Control-Allow-Origin",
                                    "https://console.example.com"))
                            .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                            .andExpect(header().string("Access-Control-Expose-Headers", "X-Trace-Id"));
                });
    }

    @Test
    void rejectsUnmatchedOriginAndOnlyAppliesToConfiguredPaths() {
        this.contextRunner
                .withPropertyValues(
                        "coco.cors.enabled=true",
                        "coco.cors.allowed-origins[0]=https://console.example.com",
                        "coco.cors.path-patterns[0]=/api/**")
                .run(context -> {
                    MockMvc mockMvc = mockMvc(context.getBean("cocoCorsFilterRegistration",
                            FilterRegistrationBean.class));

                    mockMvc.perform(get("/api/orders").header("Origin", "https://unknown.example.com"))
                            .andExpect(status().isForbidden());

                    mockMvc.perform(get("/public/orders").header("Origin", "https://console.example.com"))
                            .andExpect(status().isOk())
                            .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
                });
    }

    @Test
    void appliesAnOriginPatternWithoutAnExactOrigin() {
        this.contextRunner
                .withPropertyValues(
                        "coco.cors.enabled=true",
                        "coco.cors.allowed-origin-patterns[0]=https://*.example.com",
                        "coco.cors.allowed-methods[0]=GET")
                .run(context -> {
                    MockMvc mockMvc = mockMvc(context.getBean("cocoCorsFilterRegistration",
                            FilterRegistrationBean.class));

                    mockMvc.perform(get("/api/orders").header("Origin", "https://console.example.com"))
                            .andExpect(status().isOk())
                            .andExpect(header().string("Access-Control-Allow-Origin",
                                    "https://console.example.com"));
                });
    }

    @Test
    void failsFastWhenEnabledWithoutAnOrigin() {
        this.contextRunner
                .withPropertyValues("coco.cors.enabled=true")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseInstanceOf(IllegalStateException.class)
                        .hasStackTraceContaining("allowed-origins or allowed-origin-patterns"));
    }

    @Test
    void rejectsWildcardOriginsAndOriginPatternsWhenCredentialsAreEnabled() {
        this.contextRunner
                .withPropertyValues(
                        "coco.cors.enabled=true",
                        "coco.cors.allowed-origins[0]=*",
                        "coco.cors.allow-credentials=true")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseInstanceOf(IllegalStateException.class)
                        .hasStackTraceContaining("allow-credentials cannot be combined"));

        this.contextRunner
                .withPropertyValues(
                        "coco.cors.enabled=true",
                        "coco.cors.allowed-origin-patterns[0]=https://*",
                        "coco.cors.allow-credentials=true")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseInstanceOf(IllegalStateException.class)
                        .hasStackTraceContaining("allow-credentials cannot be combined"));

        this.contextRunner
                .withPropertyValues(
                        "coco.cors.enabled=true",
                        "coco.cors.allowed-origin-patterns[0]=https://*.example.com",
                        "coco.cors.allow-credentials=true")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseInstanceOf(IllegalStateException.class)
                        .hasStackTraceContaining("allow-credentials cannot be combined"));

        this.contextRunner
                .withPropertyValues(
                        "coco.cors.enabled=true",
                        "coco.cors.allowed-origin-patterns[0]=https://api.example.com:*",
                        "coco.cors.allow-credentials=true")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseInstanceOf(IllegalStateException.class)
                        .hasStackTraceContaining("allow-credentials cannot be combined"));
    }

    @Test
    void omitsCredentialHeadersForWildcardPatternActualAndPreflightRequests() {
        this.contextRunner
                .withPropertyValues(
                        "coco.cors.enabled=true",
                        "coco.cors.allowed-origin-patterns[0]=https://*",
                        "coco.cors.allowed-methods[0]=GET",
                        "coco.cors.allowed-headers[0]=X-Request-Id",
                        "coco.cors.allow-credentials=false")
                .run(context -> {
                    MockMvc mockMvc = mockMvc(context.getBean("cocoCorsFilterRegistration",
                            FilterRegistrationBean.class));

                    mockMvc.perform(options("/api/orders")
                                    .header("Origin", "https://attacker.invalid")
                                    .header("Access-Control-Request-Method", "GET")
                                    .header("Access-Control-Request-Headers", "X-Request-Id"))
                            .andExpect(status().isOk())
                            .andExpect(header().string("Access-Control-Allow-Origin", "https://attacker.invalid"))
                            .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));

                    mockMvc.perform(get("/api/orders").header("Origin", "https://attacker.invalid"))
                            .andExpect(status().isOk())
                            .andExpect(header().string("Access-Control-Allow-Origin", "https://attacker.invalid"))
                            .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
                });
    }

    @Test
    void rejectsDuplicateOriginsAndInvalidPathPatterns() {
        this.contextRunner
                .withPropertyValues(
                        "coco.cors.enabled=true",
                        "coco.cors.allowed-origins[0]=https://console.example.com",
                        "coco.cors.allowed-origins[1]=https://console.example.com")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasStackTraceContaining("allowed-origins contains duplicates"));

        this.contextRunner
                .withPropertyValues(
                        "coco.cors.enabled=true",
                        "coco.cors.allowed-origins[0]=https://console.example.com",
                        "coco.cors.path-patterns[0]=api/**")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasStackTraceContaining("path-patterns must start with '/'"));

        this.contextRunner
                .withPropertyValues(
                        "coco.cors.enabled=true",
                        "coco.cors.allowed-origins[0]=https://console.example.com/")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasStackTraceContaining("allowed-origins contains an invalid value"));
    }

    @Test
    void backsOffForApplicationCorsConfigurationSource() {
        this.contextRunner
                .withUserConfiguration(ApplicationCorsConfigurationSource.class)
                .withPropertyValues("coco.cors.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(CorsConfigurationSource.class);
                    assertThat(context).doesNotHaveBean("cocoCorsConfigurationSource");
                    assertThat(context).doesNotHaveBean("cocoCorsFilterRegistration");
                });
    }

    @Test
    void backsOffForApplicationCorsFilter() {
        this.contextRunner
                .withUserConfiguration(ApplicationCorsFilter.class)
                .withPropertyValues("coco.cors.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(CorsFilter.class);
                    assertThat(context).doesNotHaveBean("cocoCorsConfigurationSource");
                    assertThat(context).doesNotHaveBean("cocoCorsFilterRegistration");
                });
    }

    @Test
    void backsOffForApplicationCorsFilterRegistration() {
        this.contextRunner
                .withUserConfiguration(ApplicationCorsFilterRegistration.class)
                .withPropertyValues("coco.cors.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(FilterRegistrationBean.class);
                    assertThat(context).doesNotHaveBean("cocoCorsConfigurationSource");
                    assertThat(context).doesNotHaveBean("cocoCorsFilterRegistration");
                });
    }

    @Test
    void doesNotBackOffForNativeMvcCorsPolicies() {
        this.contextRunner
                .withUserConfiguration(ApplicationMvcCorsPolicy.class)
                .withPropertyValues(
                        "coco.cors.enabled=true",
                        "coco.cors.allowed-origins[0]=https://console.example.com")
                .run(context -> {
                    assertThat(context).hasBean("cocoCorsConfigurationSource");
                    assertThat(context).hasBean("cocoCorsFilterRegistration");
                });
    }

    private static MockMvc mockMvc(FilterRegistrationBean<?> registration) {
        return MockMvcBuilders.standaloneSetup(new CorsController())
                .addFilters(registration.getFilter())
                .build();
    }

    @Configuration(proxyBeanMethods = false)
    static class ApplicationCorsConfigurationSource {

        @Bean
        CorsConfigurationSource applicationCorsConfigurationSource() {
            return request -> null;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ApplicationCorsFilter {

        @Bean
        CorsFilter applicationCorsFilter() {
            return new CorsFilter(request -> null);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ApplicationCorsFilterRegistration {

        @Bean
        FilterRegistrationBean<CorsFilter> applicationCorsFilterRegistration() {
            return new FilterRegistrationBean<>(new CorsFilter(request -> null));
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ApplicationMvcCorsPolicy implements WebMvcConfigurer {

        @Override
        public void addCorsMappings(CorsRegistry registry) {
            registry.addMapping("/mvc/**").allowedOrigins("https://mvc.example.com");
        }
    }

    @RestController
    static class CorsController {

        @GetMapping({ "/api/orders", "/public/orders" })
        String orders() {
            return "ok";
        }
    }
}
