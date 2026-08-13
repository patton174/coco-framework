package io.github.coco.feature.storage;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CocoStorageAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(CocoStorageAutoConfiguration.class));
    @Test void disabledStorageDoesNotRequireRoot() { runner.withPropertyValues("coco.storage.enabled=false").run(context -> assertThat(context).doesNotHaveBean(CocoObjectStorage.class)); }
    @Test void failsWithoutRequiredRoot() { runner.run(context -> assertThat(context).hasFailed()); }
    @Test void backsOffForApplicationStorage() { CocoObjectStorage custom = new CocoObjectStorage() { public CocoObjectStat put(CocoObjectWriteRequest request) { return CocoObjectStat.notFound(request.key()); } public java.util.Optional<CocoObjectReadResult> get(String key) { return java.util.Optional.empty(); } public CocoObjectStat stat(String key) { return CocoObjectStat.notFound(key); } public boolean delete(String key) { return false; } public CocoObjectListResult list(String prefix, int limit, String token) { return new CocoObjectListResult(java.util.List.of(), null); } }; runner.withBean(CocoObjectStorage.class, () -> custom).run(context -> assertThat(context.getBean(CocoObjectStorage.class)).isSameAs(custom)); }
}
