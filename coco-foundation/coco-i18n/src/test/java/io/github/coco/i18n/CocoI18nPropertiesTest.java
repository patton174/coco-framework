package io.github.coco.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.coco.CocoCommonProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/**
 * Coco 国际化配置兼容性测试。
 *
 * @author patton174
 * @since 1.0.0
 */
class CocoI18nPropertiesTest {

    @Test
    void commonPropertiesKeepsLiveI18nBeanIdentityForBinder() {
        CocoCommonProperties properties = new CocoCommonProperties();
        CocoI18nProperties i18n = properties.getI18n();

        i18n.setDefaultLocale(java.util.Locale.US);

        assertThat(properties.getI18n()).isSameAs(i18n);
        assertThat(properties.getI18n().getDefaultLocale()).isEqualTo(java.util.Locale.US);
    }

    @Test
    void keepsLiveBasenameBackingListForExistingConsumers() {
        CocoI18nProperties properties = new CocoI18nProperties();
        List<String> firstReference = properties.getBasename();

        firstReference.add("application-messages");
        assertThat(properties.getBasename())
                .isSameAs(firstReference)
                .containsExactly("coco-messages", "application-messages");

        firstReference.remove("coco-messages");
        assertThat(properties.getBasename())
                .isSameAs(firstReference)
                .containsExactly("application-messages");
    }

    @Test
    void setterCopiesCallerListAndPreservesNullsDuplicatesAndOrder() {
        List<String> callerOwned = new ArrayList<>();
        callerOwned.add("second-messages");
        callerOwned.add(null);
        callerOwned.add("first-messages");
        callerOwned.add("second-messages");
        CocoI18nProperties properties = new CocoI18nProperties();

        properties.setBasename(callerOwned);
        callerOwned.clear();

        assertThat(properties.getBasename())
                .containsExactly("second-messages", null, "first-messages", "second-messages");
    }

    @Test
    void nullAndEmptySetterValuesRestoreDefaultAndReplaceThePreviousBackingList() {
        CocoI18nProperties properties = new CocoI18nProperties();
        List<String> previous = properties.getBasename();
        previous.add("application-messages");

        properties.setBasename(null);

        assertThat(properties.getBasename()).isNotSameAs(previous).containsExactly("coco-messages");
        previous.clear();
        assertThat(properties.getBasename()).containsExactly("coco-messages");

        properties.setBasename(List.of());
        assertThat(properties.getBasename()).containsExactly("coco-messages");
    }

    @Test
    void springBinderPreservesConfiguredBasenameOrderAndLiveMutation() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("coco.common.i18n.basename[0]", "application-errors");
        values.put("coco.common.i18n.basename[1]", "application-messages");
        Binder binder = new Binder(new MapConfigurationPropertySource(values));

        CocoI18nProperties properties = binder.bind("coco.common.i18n",
                Bindable.of(CocoI18nProperties.class))
                .orElseThrow(() -> new AssertionError("Expected i18n properties to bind"));

        assertThat(properties.getBasename()).containsExactly("application-errors", "application-messages");
        properties.getBasename().add(1, "shared-messages");
        properties.getBasename().remove("application-errors");
        assertThat(properties.getBasename()).containsExactly("shared-messages", "application-messages");
    }
}
