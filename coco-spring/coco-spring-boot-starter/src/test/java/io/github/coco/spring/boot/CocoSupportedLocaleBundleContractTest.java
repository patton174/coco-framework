package io.github.coco.spring.boot;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;

import org.junit.jupiter.api.Test;

class CocoSupportedLocaleBundleContractTest {

    private static final Map<String, String> REPRESENTATIVE_MESSAGES = Map.ofEntries(
            Map.entry("coco-messages", "coco.error.unknown"),
            Map.entry("coco-feature-web-messages", "coco.feature.web.ready"),
            Map.entry("coco-feature-security-messages", "coco.feature.security.ready"),
            Map.entry("coco-feature-audit-messages", "coco.feature.audit.ready"),
            Map.entry("coco-feature-tenant-messages", "coco.feature.tenant.ready"),
            Map.entry("coco-feature-mybatis-plus-messages", "coco.feature.mybatis-plus.ready"),
            Map.entry("coco-feature-openapi-messages", "coco.feature.openapi.ready"),
            Map.entry("coco-feature-codegen-messages", "coco.feature.codegen.ready"),
            Map.entry("coco-feature-data-permission-messages", "coco.feature.data-permission.ready"),
            Map.entry("coco-feature-registry-messages", "coco.feature.registry.not-found"),
            Map.entry("coco-feature-runtime-messages", "coco.feature.runtime.ready"));

    @Test
    void providesRepresentativeMessagesForEverySupportedLocale() {
        for (Map.Entry<String, String> entry : REPRESENTATIVE_MESSAGES.entrySet()) {
            ResourceBundle root = bundle(entry.getKey(), Locale.ROOT);
            String chinese = bundle(entry.getKey(), Locale.SIMPLIFIED_CHINESE).getString(entry.getValue());
            String english = bundle(entry.getKey(), Locale.US).getString(entry.getValue());

            assertThat(chinese).as(entry.getKey()).isNotEqualTo(root.getString(entry.getValue()));
            assertThat(english).as(entry.getKey()).isEqualTo(root.getString(entry.getValue()));
        }
    }

    @Test
    void keepsBaseChineseAndEnglishBundleKeysAligned() {
        for (String basename : REPRESENTATIVE_MESSAGES.keySet()) {
            Set<String> rootKeys = bundle(basename, Locale.ROOT).keySet();
            assertThat(bundle(basename, Locale.SIMPLIFIED_CHINESE).keySet())
                    .as(basename + " zh-CN")
                    .containsExactlyInAnyOrderElementsOf(rootKeys);
            assertThat(bundle(basename, Locale.US).keySet())
                    .as(basename + " en-US")
                    .containsExactlyInAnyOrderElementsOf(rootKeys);
        }
    }

    @Test
    void doesNotPublishGenericChineseBundlesForCommonWebOrSecurity() {
        ClassLoader classLoader = getClass().getClassLoader();

        assertThat(classLoader.getResource("coco-messages_zh.properties")).isNull();
        assertThat(classLoader.getResource("coco-feature-web-messages_zh.properties")).isNull();
        assertThat(classLoader.getResource("coco-feature-security-messages_zh.properties")).isNull();
    }

    private static ResourceBundle bundle(String basename, Locale locale) {
        return ResourceBundle.getBundle(basename, locale);
    }

}
