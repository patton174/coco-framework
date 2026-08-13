package io.github.coco.storage.s3;

import java.net.URI;

import io.github.coco.feature.storage.CocoObjectStorage;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

/** Auto-configuration for the explicitly enabled S3 object storage adapter. */
@AutoConfiguration
@ConditionalOnProperty(prefix = "coco.storage.s3", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(CocoStorageS3Properties.class)
public class CocoStorageS3AutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean({ S3Client.class, CocoObjectStorage.class })
    S3Client cocoStorageS3Client(CocoStorageS3Properties properties) {
        properties.validate();
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(properties.getRegion().trim()))
                .httpClientBuilder(UrlConnectionHttpClient.builder()
                        .connectionTimeout(properties.getConnectTimeout())
                        .socketTimeout(properties.getReadTimeout()))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(properties.getApiCallTimeout())
                        .apiCallAttemptTimeout(properties.getApiCallAttemptTimeout())
                        .build())
                .forcePathStyle(properties.resolvedPathStyle())
                .credentialsProvider(credentialsProvider(properties));
        URI endpoint = properties.getEndpoint();
        if (endpoint != null) {
            builder.endpointOverride(endpoint);
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean(CocoObjectStorage.class)
    S3CocoObjectStorage cocoObjectStorage(S3Client s3Client, CocoStorageS3Properties properties) {
        properties.validate();
        return new S3CocoObjectStorage(s3Client, properties);
    }

    static AwsCredentialsProvider credentialsProvider(CocoStorageS3Properties properties) {
        if (!properties.hasStaticCredentials()) {
            return DefaultCredentialsProvider.create();
        }
        return StaticCredentialsProvider.create(properties.getSessionToken() == null || properties.getSessionToken().isBlank()
                ? AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())
                : AwsSessionCredentials.create(properties.getAccessKey(), properties.getSecretKey(), properties.getSessionToken()));
    }
}
