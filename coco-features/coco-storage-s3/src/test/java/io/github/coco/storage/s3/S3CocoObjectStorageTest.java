package io.github.coco.storage.s3;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.coco.feature.storage.CocoObjectListResult;
import io.github.coco.feature.storage.CocoObjectReadResult;
import io.github.coco.feature.storage.CocoObjectStat;
import io.github.coco.feature.storage.CocoObjectWriteRequest;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Object;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

class S3CocoObjectStorageTest {

    @Test
    void putsKnownAndUnknownBodiesWithConditionalWriteAndDoesNotCloseCallerInput() throws Exception {
        RecordingS3 client = new RecordingS3();
        S3CocoObjectStorage storage = storage(client.client(), "ns");
        CloseAwareInputStream known = new CloseAwareInputStream("known".getBytes());
        storage.put(new CocoObjectWriteRequest("a.txt", known, 5L, "text/plain", Map.of("Name", "Chinese value")));
        CloseAwareInputStream unknown = new CloseAwareInputStream("unknown".getBytes());
        storage.put(new CocoObjectWriteRequest("b.txt", unknown, null, null, Map.of()));

        assertThat(client.putRequests).allSatisfy(request -> assertThat(request.key()).startsWith("ns/"));
        assertThat(client.putRequests.get(0).ifNoneMatch()).isEqualTo("*");
        assertThat(client.putBodies).containsExactly("known".getBytes(), "unknown".getBytes());
        assertThat(known.closed).isFalse();
        assertThat(unknown.closed).isFalse();
        assertThat(client.putRequests.get(0).metadata()).containsKey(S3MetadataCodec.HEADER);
    }

    @Test
    void readsStatsDeletesAndListsInConfiguredNamespace() throws Exception {
        RecordingS3 client = new RecordingS3();
        client.getResponse = GetObjectResponse.builder().contentLength(3L).contentType("text/plain")
                .metadata(S3MetadataCodec.encode(Map.of("Upper", "value", "unicode", "中文")))
                .lastModified(Instant.parse("2026-01-01T00:00:00Z")).build();
        client.headResponse = HeadObjectResponse.builder().contentLength(3L).contentType("text/plain")
                .metadata(client.getResponse.metadata()).lastModified(client.getResponse.lastModified()).eTag("etag-1").build();
        client.listResponse = ListObjectsV2Response.builder().contents(
                S3Object.builder().key("ns/a.txt").size(1L).lastModified(Instant.EPOCH).build(),
                S3Object.builder().key("other/hidden.txt").size(2L).lastModified(Instant.EPOCH).build())
                .nextContinuationToken("token-2").build();
        S3CocoObjectStorage storage = storage(client.client(), "ns");

        CocoObjectReadResult result = storage.get("a.txt").orElseThrow();
        assertThat(result.inputStream().readAllBytes()).isEqualTo("abc".getBytes());
        result.close();
        assertThat(client.responseClosed.get()).isTrue();
        CocoObjectStat stat = storage.stat("a.txt");
        CocoObjectListResult list = storage.list("", 10, "token-1");

        assertThat(stat.metadata().metadata()).containsEntry("unicode", "中文");
        assertThat(client.getRequest.key()).isEqualTo("ns/a.txt");
        assertThat(client.listRequest.prefix()).isEqualTo("ns/");
        assertThat(client.listRequest.continuationToken()).isEqualTo("token-1");
        assertThat(list.objects()).extracting(value -> value.metadata().key()).containsExactly("a.txt");
        assertThat(list.continuationToken()).isEqualTo("token-2");
        assertThat(storage.delete("a.txt")).isTrue();
        assertThat(client.deleteRequest.ifMatch()).isEqualTo("etag-1");
    }

    @Test
    void returnsAbsentForNotFoundAndSanitizesSdkFailures() throws Exception {
        RecordingS3 client = new RecordingS3();
        client.getFailure = NoSuchKeyException.builder().statusCode(404).message("secret-token Authorization endpoint?x=1").build();
        S3CocoObjectStorage storage = storage(client.client(), "ns");
        assertThat(storage.get("missing.txt")).isEmpty();
        client.getFailure = software.amazon.awssdk.services.s3.model.S3Exception.builder().statusCode(500)
                .message("AKIA secret Authorization https://host/?signature=x").build();
        assertThatIOException().isThrownBy(() -> storage.get("sensitive.txt"))
                .withMessageNotContaining("AKIA").withMessageNotContaining("secret")
                .withMessageNotContaining("Authorization").withMessageNotContaining("signature")
                .withMessageNotContaining("host").withMessageContaining("get", "status=500");
    }

    @Test
    void rejectsMismatchedOrOversizedStreamsAndCleansTemporaryStaging() throws Exception {
        RecordingS3 client = new RecordingS3();
        CocoStorageS3Properties properties = properties("ns");
        properties.setMaxObjectSize(3);
        S3CocoObjectStorage storage = new S3CocoObjectStorage(client.client(), properties);
        assertThatIOException().isThrownBy(() -> storage.put(new CocoObjectWriteRequest("a", new ByteArrayInputStream("four".getBytes()), null, null, Map.of())))
                .withMessageContaining("maximum size");
        S3CocoObjectStorage mismatchStorage = storage(client.client(), "ns");
        assertThatIOException().isThrownBy(() -> mismatchStorage.put(new CocoObjectWriteRequest("a", new ByteArrayInputStream("two".getBytes()), 4L, null, Map.of())))
                .withMessageContaining("content length mismatch");
        assertThat(client.putRequests).isEmpty();
    }

    @Test
    void metadataCodecRoundTripsUnicodeAndFailsBeforeUploadAtBoundary() throws Exception {
        assertThat(S3MetadataCodec.decode(S3MetadataCodec.encode(Map.of("CaseSensitive", "value", "中文", "value 中文"))))
                .containsEntry("CaseSensitive", "value").containsEntry("中文", "value 中文");
        Map<String, String> oversized = new LinkedHashMap<>();
        oversized.put("key", "x".repeat(2000));
        assertThatIOException().isThrownBy(() -> S3MetadataCodec.encode(oversized)).withMessageContaining("encoded size");
    }

    private static S3CocoObjectStorage storage(S3Client client, String prefix) {
        CocoStorageS3Properties properties = properties(prefix);
        return new S3CocoObjectStorage(client, properties);
    }

    private static CocoStorageS3Properties properties(String prefix) {
        CocoStorageS3Properties properties = new CocoStorageS3Properties();
        properties.setBucket("bucket");
        properties.setRegion("us-east-1");
        properties.setKeyPrefix(prefix);
        properties.setListMaxSize(1000);
        return properties;
    }

    private static final class CloseAwareInputStream extends ByteArrayInputStream {
        private boolean closed;
        private CloseAwareInputStream(byte[] bytes) { super(bytes); }
        @Override public void close() { this.closed = true; }
    }

    private static final class RecordingS3 {
        private final List<PutObjectRequest> putRequests = new ArrayList<>();
        private final List<byte[]> putBodies = new ArrayList<>();
        private PutObjectRequest putRequest;
        private GetObjectRequest getRequest;
        private HeadObjectRequest headRequest;
        private DeleteObjectRequest deleteRequest;
        private ListObjectsV2Request listRequest;
        private GetObjectResponse getResponse = GetObjectResponse.builder().contentLength(3L).build();
        private HeadObjectResponse headResponse = HeadObjectResponse.builder().contentLength(3L).eTag("etag").build();
        private ListObjectsV2Response listResponse = ListObjectsV2Response.builder().build();
        private RuntimeException getFailure;
        private final AtomicBoolean responseClosed = new AtomicBoolean();

        private S3Client client() {
            return (S3Client) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { S3Client.class },
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "putObject" -> put(arguments);
                        case "getObject" -> get(arguments);
                        case "headObject" -> head(arguments);
                        case "deleteObject" -> delete(arguments);
                        case "listObjectsV2" -> list(arguments);
                        case "serviceName" -> "S3";
                        case "close" -> null;
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }

        private PutObjectResponse put(Object[] arguments) throws IOException {
            this.putRequest = (PutObjectRequest) arguments[0];
            this.putRequests.add(this.putRequest);
            RequestBody body = (RequestBody) arguments[1];
            try (var input = body.contentStreamProvider().newStream()) {
                this.putBodies.add(input.readAllBytes());
            }
            return PutObjectResponse.builder().eTag("etag").build();
        }

        private ResponseInputStream<GetObjectResponse> get(Object[] arguments) {
            if (this.getFailure != null) { throw this.getFailure; }
            this.getRequest = (GetObjectRequest) arguments[0];
            return new ResponseInputStream<>(this.getResponse, new ByteArrayInputStream("abc".getBytes()) {
                @Override public void close() throws IOException {
                    responseClosed.set(true);
                    super.close();
                }
            });
        }

        private HeadObjectResponse head(Object[] arguments) { this.headRequest = (HeadObjectRequest) arguments[0]; return this.headResponse; }
        private DeleteObjectResponse delete(Object[] arguments) { this.deleteRequest = (DeleteObjectRequest) arguments[0]; return DeleteObjectResponse.builder().build(); }
        private ListObjectsV2Response list(Object[] arguments) { this.listRequest = (ListObjectsV2Request) arguments[0]; return this.listResponse; }
    }
}
