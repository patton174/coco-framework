package io.github.coco.storage.s3;

import io.github.coco.feature.storage.CocoObjectStorage;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class CocoStorageS3AutoConfigurationTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CocoStorageS3AutoConfiguration.class));

    @Test
    void disabledHasZeroBehavior() {
        this.context.run(result -> {
            assertThat(result).doesNotHaveBean(S3Client.class);
            assertThat(result).doesNotHaveBean(CocoObjectStorage.class);
        });
    }

    @Test
    void enabledUsesBusinessClientAndBacksOffForBusinessStorage() {
        S3Client client = (S3Client) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { S3Client.class }, (proxy, method, arguments) -> null);
        CocoObjectStorage storage = new CocoObjectStorage() {
            @Override public io.github.coco.feature.storage.CocoObjectStat put(io.github.coco.feature.storage.CocoObjectWriteRequest request) { return null; }
            @Override public java.util.Optional<io.github.coco.feature.storage.CocoObjectReadResult> get(String key) { return java.util.Optional.empty(); }
            @Override public io.github.coco.feature.storage.CocoObjectStat stat(String key) { return null; }
            @Override public boolean delete(String key) { return false; }
            @Override public io.github.coco.feature.storage.CocoObjectListResult list(String prefix, int limit, String token) { return null; }
        };
        this.context.withPropertyValues("coco.storage.s3.enabled=true", "coco.storage.s3.bucket=bucket", "coco.storage.s3.region=us-east-1")
                .withBean(S3Client.class, () -> client).withBean(CocoObjectStorage.class, () -> storage)
                .run(result -> {
                    assertThat(result).hasSingleBean(S3Client.class).hasSingleBean(CocoObjectStorage.class);
                    assertThat(result.getBean(CocoObjectStorage.class)).isSameAs(storage);
                });
    }

    @Test
    void businessStorageAlonePreventsModuleClientAndAdapterCreation() {
        CocoObjectStorage storage = new EmptyStorage();
        this.context.withPropertyValues("coco.storage.s3.enabled=true", "coco.storage.s3.bucket=bucket", "coco.storage.s3.region=us-east-1")
                .withBean(CocoObjectStorage.class, () -> storage)
                .run(result -> {
                    assertThat(result).doesNotHaveBean(S3Client.class);
                    assertThat(result).hasSingleBean(CocoObjectStorage.class);
                });
    }

    @Test
    void validatesEndpointCredentialsPrefixAndLimitsWithoutExposingSecrets() {
        CocoStorageS3Properties properties = new CocoStorageS3Properties();
        properties.setBucket("bucket");
        properties.setRegion("region");
        properties.setEndpoint(java.net.URI.create("https://user@example.com/path?token=secret"));
        assertThatIllegalArgumentException().isThrownBy(properties::validate).withMessageContaining("endpoint");
        properties.setEndpoint(null);
        properties.setAccessKey("access");
        assertThatIllegalArgumentException().isThrownBy(properties::validate).withMessageContaining("together");
        assertThat(properties.toString()).doesNotContain("access", "secret");
    }

    @Test
    void resolvesPathStyleAndCredentialProviderWithoutLeakingCredentialValues() {
        CocoStorageS3Properties properties = new CocoStorageS3Properties();
        properties.setBucket("bucket");
        properties.setRegion("region");
        assertThat(properties.resolvedPathStyle()).isFalse();
        assertThat(CocoStorageS3AutoConfiguration.credentialsProvider(properties)).isInstanceOf(DefaultCredentialsProvider.class);
        properties.setEndpoint(java.net.URI.create("http://localhost:9000"));
        properties.setAccessKey("example-access");
        properties.setSecretKey("example-secret");
        assertThat(properties.resolvedPathStyle()).isTrue();
        assertThat(CocoStorageS3AutoConfiguration.credentialsProvider(properties)).isInstanceOf(StaticCredentialsProvider.class);
        assertThat(properties.toString()).doesNotContain("example-access", "example-secret");
    }

    private static final class EmptyStorage implements CocoObjectStorage {
        @Override public io.github.coco.feature.storage.CocoObjectStat put(io.github.coco.feature.storage.CocoObjectWriteRequest request) { return null; }
        @Override public java.util.Optional<io.github.coco.feature.storage.CocoObjectReadResult> get(String key) { return java.util.Optional.empty(); }
        @Override public io.github.coco.feature.storage.CocoObjectStat stat(String key) { return null; }
        @Override public boolean delete(String key) { return false; }
        @Override public io.github.coco.feature.storage.CocoObjectListResult list(String prefix, int limit, String token) { return null; }
    }
}
