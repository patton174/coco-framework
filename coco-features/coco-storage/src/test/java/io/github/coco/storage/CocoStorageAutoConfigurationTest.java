package io.github.coco.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import io.github.coco.i18n.CocoMessageBundleRegistrar;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 对象存储自动配置测试。
 */
class CocoStorageAutoConfigurationTest {

    @TempDir
    Path temporaryDirectory;

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CocoStorageAutoConfiguration.class));

    @Test
    void disabledConfigurationCreatesNoStorageInfrastructure() {
        this.contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(CocoObjectStorage.class);
            assertThat(context).doesNotHaveBean(CocoStorageProperties.class);
        });
    }

    @Test
    void enabledLocalReferenceImplementationRequiresRoot() {
        this.contextRunner.withPropertyValues("coco.storage.enabled=true").run(context -> {
            assertThat(context.getStartupFailure()).isNotNull();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(CocoStorageException.class);
        });
    }

    @Test
    void enabledConfigurationCreatesTheLocalReferenceImplementation() {
        this.contextRunner.withPropertyValues("coco.storage.enabled=true",
                "coco.storage.local.root=" + this.temporaryDirectory.resolve("storage").toAbsolutePath())
                .run(context -> {
                    assertThat(context).hasSingleBean(CocoObjectStorage.class);
                    assertThat(context.getBean(CocoObjectStorage.class))
                            .isInstanceOf(ValidatingCocoObjectStorage.class);
                    assertThat(context).hasSingleBean(CocoMessageBundleRegistrar.class);
                    List<String> bundles = new ArrayList<>();
                    context.getBean(CocoMessageBundleRegistrar.class).registerBundles(bundles::add);
                    assertThat(bundles).containsExactly("coco-storage-messages");
                    assertThat(ResourceBundle.getBundle("coco-storage-messages", Locale.SIMPLIFIED_CHINESE)
                            .getString(CocoStorageErrorCode.INVALID_KEY.code())).isEqualTo("对象键不合法。");
                });
    }

    @Test
    void applicationStorageBeanTakesPrecedenceWithoutLocalRoot() {
        this.contextRunner.withPropertyValues("coco.storage.enabled=true",
                "coco.storage.validation.enabled=false")
                .withUserConfiguration(CustomStorage.class)
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNull();
                    assertThat(context).hasSingleBean(CocoObjectStorage.class);
                    assertThat(context.getBean(CocoObjectStorage.class)).isSameAs(context.getBean("customStorage"));
                });
    }

    @Test
    void validationDecoratorWrapsApplicationStorageBean() {
        this.contextRunner.withPropertyValues("coco.storage.enabled=true")
                .withUserConfiguration(CustomStorage.class)
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNull();
                    assertThat(context.getBean(CocoObjectStorage.class))
                            .isInstanceOf(ValidatingCocoObjectStorage.class);
                });
    }

    @Test
    void disabledValidationLeavesTheLocalReferenceImplementationUnwrapped() {
        this.contextRunner.withPropertyValues("coco.storage.enabled=true",
                "coco.storage.validation.enabled=false",
                "coco.storage.local.root=" + this.temporaryDirectory.resolve("storage").toAbsolutePath())
                .run(context -> {
                    assertThat(context).doesNotHaveBean(CocoStorageValidationBeanPostProcessor.class);
                    assertThat(context.getBean(CocoObjectStorage.class))
                            .isInstanceOf(io.github.coco.storage.local.LocalCocoObjectStorage.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomStorage {

        @Bean
        CocoObjectStorage customStorage() {
            return new CocoObjectStorage() {
                @Override
                public CocoObjectMetadata put(CocoObjectPutRequest request) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public CocoObjectResource open(String key) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public CocoObjectMetadata stat(String key) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public boolean exists(String key) {
                    return false;
                }

                @Override
                public boolean delete(String key) {
                    return false;
                }
            };
        }
    }
}
