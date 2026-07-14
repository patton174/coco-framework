package io.github.coco.feature.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.coco.feature.openapi.springdoc.CocoSpringDocOpenApiCustomizerFactoryBean;
import io.github.coco.feature.openapi.springdoc.SpringDocOpenApiRuntimeHints;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;

/**
 * SpringDoc AOT 运行时提示测试。
 *
 * @author patton174
 * @since 1.0.0
 */
class SpringDocOpenApiRuntimeHintsTest {

    @Test
    void registersReflectionAndProxyHintsForCompatibleSpringDoc() {
        RuntimeHints hints = new RuntimeHints();

        new SpringDocOpenApiRuntimeHints().registerHints(hints, getClass().getClassLoader());

        assertThat(hints.reflection().getTypeHint(TypeReference.of(
                CocoSpringDocOpenApiCustomizerFactoryBean.OPEN_API_CLASS))).isNotNull();
        assertThat(hints.reflection().getTypeHint(TypeReference.of(
                CocoSpringDocOpenApiCustomizerFactoryBean.INFO_CLASS))).isNotNull();
        assertThat(hints.proxies().jdkProxyHints())
                .anySatisfy(hint -> assertThat(hint.getProxiedInterfaces())
                        .contains(TypeReference.of(CocoSpringDocOpenApiCustomizerFactoryBean.OPEN_API_CUSTOMIZER_CLASS)));
    }
}
