package io.github.coco.storage.s3;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.reflect.Proxy;
import java.nio.channels.ClosedByInterruptException;
import java.nio.file.FileAlreadyExistsException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.coco.feature.storage.CocoObjectListResult;
import io.github.coco.feature.storage.CocoObjectReadResult;
import io.github.coco.feature.storage.CocoObjectStat;
import io.github.coco.feature.storage.CocoObjectWriteRequest;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
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
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIOException;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class S3CocoObjectStorageTest {

    @Test
    void stagesKnownAndUnknownBodiesAndKeepsCallerStreamsOpen() throws Exception {
        RecordingS3 client = new RecordingS3();
        S3CocoObjectStorage storage = storage(client.client(), "bucket", "ns");
        CloseAwareInputStream known = new CloseAwareInputStream("known".getBytes());
        storage.put(new CocoObjectWriteRequest("a.txt", known, 5L, "text/plain",
                Map.of("Name", "Chinese value")));
        CloseAwareInputStream unknown = new CloseAwareInputStream("unknown".getBytes());
        storage.put(new CocoObjectWriteRequest("b.txt", unknown, null, null, Map.of()));

        assertThat(client.putRequests).allSatisfy(request -> {
            assertThat(request.key()).startsWith("ns/");
            assertThat(request.ifNoneMatch()).isEqualTo("*");
        });
        assertThat(client.putBodies).containsExactly("known".getBytes(), "unknown".getBytes());
        assertThat(known.closed).isFalse();
        assertThat(unknown.closed).isFalse();
        assertThat(client.putRequests.get(0).metadata()).containsKey(S3MetadataCodec.HEADER);
    }

    @Test
    void mapsConditionalWriteConflictsToFileAlreadyExists() {
        RecordingS3 client = new RecordingS3();
        S3CocoObjectStorage storage = storage(client.client(), "bucket", "ns");
        client.putFailure = s3Failure(412, null, "bucket key credentials endpoint");

        assertThatExceptionOfType(FileAlreadyExistsException.class)
                .isThrownBy(() -> storage.put(writeRequest("object.txt")))
                .withMessage("S3 put");

        client.putFailure = s3Failure(400, "PreconditionFailed", "different sensitive message");
        assertThatExceptionOfType(FileAlreadyExistsException.class)
                .isThrownBy(() -> storage.put(writeRequest("object.txt")))
                .withMessage("S3 put");
    }

    @Test
    void readsStatsDeletesAndListsWithOpaqueProviderTokenAndStableOrder() throws Exception {
        RecordingS3 client = new RecordingS3();
        client.getResponse = GetObjectResponse.builder().contentLength(3L).contentType("text/plain")
                .metadata(S3MetadataCodec.encode(Map.of("Upper", "value", "unicode", "Chinese value")))
                .lastModified(Instant.parse("2026-01-01T00:00:00Z")).build();
        client.headResponse = HeadObjectResponse.builder().contentLength(3L).contentType("text/plain")
                .metadata(client.getResponse.metadata()).lastModified(client.getResponse.lastModified())
                .eTag("etag-1").build();
        client.listResponse = ListObjectsV2Response.builder().contents(
                object("ns/b.txt", 2L), object("ns/a.txt", 1L))
                .isTruncated(true).nextContinuationToken("provider-secret-token").build();
        S3CocoObjectStorage storage = storage(client.client(), "bucket", "ns");

        CocoObjectReadResult result = storage.get("a.txt").orElseThrow();
        assertThat(result.inputStream().readAllBytes()).isEqualTo("abc".getBytes());
        result.close();
        assertThat(client.responseClosed.get()).isTrue();
        CocoObjectStat stat = storage.stat("a.txt");
        CocoObjectListResult firstPage = storage.list("", 10, null);

        assertThat(stat.metadata().metadata()).containsEntry("unicode", "Chinese value");
        assertThat(firstPage.objects()).extracting(value -> value.metadata().key())
                .containsExactly("a.txt", "b.txt");
        assertThat(firstPage.continuationToken()).doesNotContain("provider-secret-token");

        client.listResponse = ListObjectsV2Response.builder().contents(object("ns/c.txt", 3L)).build();
        storage.list("", 10, firstPage.continuationToken());
        assertThat(client.listRequest.continuationToken()).isEqualTo("provider-secret-token");
        assertThat(storage.delete("a.txt")).isTrue();
        assertThat(client.deleteRequest.ifMatch()).isEqualTo("etag-1");
    }

    @Test
    void deleteUsesHeadExistenceAndTreatsConditionalMismatchAsNotDeleted() throws Exception {
        RecordingS3 client = new RecordingS3();
        client.headResponse = HeadObjectResponse.builder().contentLength(1L).build();
        S3CocoObjectStorage storage = storage(client.client(), "bucket", "ns");
        assertThat(storage.delete("a.txt")).isTrue();
        assertThat(client.deleteRequest.ifMatch()).isNull();

        client.deleteFailure = s3Failure(412, null, "sensitive");
        assertThat(storage.delete("a.txt")).isFalse();

        client.deleteFailure = s3Failure(400, "PreconditionFailed", "sensitive");
        assertThat(storage.delete("a.txt")).isFalse();
    }

    @Test
    void mapsOnlyObjectNotFoundCodesAndRejectsBucketOrBare404() throws Exception {
        RecordingS3 client = new RecordingS3();
        S3CocoObjectStorage storage = storage(client.client(), "bucket", "ns");
        client.getFailure = NoSuchKeyException.builder().statusCode(404).build();
        assertThat(storage.get("missing.txt")).isEmpty();

        client.getFailure = s3Failure(404, "NotFound", "compatible backend");
        assertThat(storage.get("missing.txt")).isEmpty();

        client.getFailure = s3Failure(404, "NoSuchKey", "compatible backend");
        assertThat(storage.get("missing.txt")).isEmpty();

        client.getFailure = s3Failure(404, "NoSuchBucket", "bucket missing");
        assertThatIOException().isThrownBy(() -> storage.get("missing.txt"))
                .withMessage("S3 get failed with HTTP status 404");

        client.getFailure = s3Failure(404, null, "bare 404");
        assertThatIOException().isThrownBy(() -> storage.get("missing.txt"))
                .withMessage("S3 get failed with HTTP status 404");

        client.getFailure = s3Failure(403, "AccessDenied", "denied");
        assertThatIOException().isThrownBy(() -> storage.get("missing.txt"))
                .withMessage("S3 get failed with HTTP status 403");
    }

    @Test
    void translatedFailuresContainOnlyOperationAndOptionalStatus() {
        RecordingS3 client = new RecordingS3();
        client.getFailure = s3Failure(500, "InternalError",
                "sensitive-bucket sensitive-key access secret session https://endpoint/?signature=x");
        S3CocoObjectStorage storage = storage(client.client(), "sensitive-bucket", "sensitive-prefix");

        assertThatIOException().isThrownBy(() -> storage.get("sensitive-key"))
                .withMessage("S3 get failed with HTTP status 500");
    }

    @Test
    void continuationTokenIsCanonicalOpaqueAndBoundToAllContexts() throws Exception {
        RecordingS3 client = new RecordingS3();
        client.listResponse = ListObjectsV2Response.builder().contents(object("config-a/request-a/item", 1L))
                .isTruncated(true).nextContinuationToken("provider-token").build();
        S3CocoObjectStorage source = storage(client.client(), "bucket-a", "config-a");
        String token = source.list("request-a/", 10, null).continuationToken();
        assertThat(token).doesNotContain("provider-token");

        assertThatIllegalArgumentException().isThrownBy(() -> storage(client.client(), "bucket-b", "config-a")
                .list("request-a/", 10, token));
        assertThatIllegalArgumentException().isThrownBy(() -> storage(client.client(), "bucket-a", "config-b")
                .list("request-a/", 10, token));
        assertThatIllegalArgumentException().isThrownBy(() -> source.list("request-b/", 10, token));
        assertThatIllegalArgumentException().isThrownBy(() ->
                source.list("request-a/", 10, "coco-s3-list.not-base64!"));

        String nonCanonicalJson = "{\"bucket\":\"bucket-a\",\"version\":1,"
                + "\"keyPrefix\":\"config-a\",\"requestPrefix\":\"request-a/\","
                + "\"providerToken\":\"provider-token\"}";
        String nonCanonicalToken = "coco-s3-list." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(nonCanonicalJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThatIllegalArgumentException().isThrownBy(() -> source.list("request-a/", 10, nonCanonicalToken));
    }

    @Test
    void metadataRejectsControlsOnEncodeAndProviderCraftedDecode() throws Exception {
        assertThatIOException().isThrownBy(() -> S3MetadataCodec.encode(Map.of("bad\nkey", "value")))
                .withMessageContaining("control");
        assertThatIOException().isThrownBy(() -> S3MetadataCodec.encode(Map.of("key", "bad\u0000value")))
                .withMessageContaining("control");

        RecordingS3 client = new RecordingS3();
        client.getResponse = GetObjectResponse.builder().contentLength(3L).lastModified(Instant.EPOCH)
                .metadata(Map.of(S3MetadataCodec.HEADER, craftedMetadata("key", "bad\nvalue"))).build();
        S3CocoObjectStorage storage = storage(client.client(), "bucket", "ns");
        assertThatIOException().isThrownBy(() -> storage.get("a.txt"))
                .withMessageContaining("control");
        assertThat(client.responseClosed.get()).isTrue();
    }

    @Test
    void listFailsClosedForProviderBoundaryViolationsAndIncompleteMetadata() {
        RecordingS3 client = new RecordingS3();
        S3CocoObjectStorage storage = storage(client.client(), "bucket", "ns");

        client.listResponse = ListObjectsV2Response.builder().contents(object("other/a", 1L)).build();
        assertThatIOException().isThrownBy(() -> storage.list("", 1, null)).withMessageContaining("namespace");

        client.listResponse = ListObjectsV2Response.builder().contents(object("ns/outside", 1L)).build();
        assertThatIOException().isThrownBy(() -> storage.list("requested/", 1, null))
                .withMessageContaining("namespace");

        client.listResponse = ListObjectsV2Response.builder().contents(object("ns/a", 1L), object("ns/b", 1L)).build();
        assertThatIOException().isThrownBy(() -> storage.list("", 1, null)).withMessageContaining("too many");

        client.listResponse = ListObjectsV2Response.builder().contents(
                S3Object.builder().key("ns/a").lastModified(Instant.EPOCH).build()).build();
        assertThatIOException().isThrownBy(() -> storage.list("", 1, null)).withMessageContaining("incomplete");

        client.listResponse = ListObjectsV2Response.builder().contents(
                S3Object.builder().key("ns/a").size(1L).build()).build();
        assertThatIOException().isThrownBy(() -> storage.list("", 1, null)).withMessageContaining("incomplete");

        client.listResponse = ListObjectsV2Response.builder().contents(object("ns/a", 1L))
                .isTruncated(true).build();
        assertThatIOException().isThrownBy(() -> storage.list("", 1, null)).withMessageContaining("continuation");

        client.listResponse = ListObjectsV2Response.builder().contents(object("ns/a", 1L))
                .isTruncated(true).nextContinuationToken("x".repeat(16 * 1024)).build();
        assertThatIOException().isThrownBy(() -> storage.list("", 1, null)).withMessageContaining("continuation");
    }

    @Test
    void restoresInterruptForAllSupportedInterruptionCauses() {
        assertInterruptRestored(new InterruptedException("interrupted"));
        assertInterruptRestored(new InterruptedIOException("interrupted io"));
        assertInterruptRestored(new ClosedByInterruptException());
    }

    @Test
    void rejectsMismatchedOrOversizedStreamsBeforeUpload() {
        RecordingS3 client = new RecordingS3();
        CocoStorageS3Properties properties = properties("bucket", "ns");
        properties.setMaxObjectSize(3);
        S3CocoObjectStorage storage = new S3CocoObjectStorage(client.client(), properties);
        assertThatIOException().isThrownBy(() -> storage.put(new CocoObjectWriteRequest("a",
                new ByteArrayInputStream("four".getBytes()), null, null, Map.of())))
                .withMessageContaining("maximum size");
        S3CocoObjectStorage mismatchStorage = storage(client.client(), "bucket", "ns");
        assertThatIOException().isThrownBy(() -> mismatchStorage.put(new CocoObjectWriteRequest("a",
                new ByteArrayInputStream("two".getBytes()), 4L, null, Map.of())))
                .withMessageContaining("content length mismatch");
        assertThat(client.putRequests).isEmpty();
    }

    @Test
    void metadataRoundTripsUnicodeAndFailsAtStrictSizeBoundary() throws Exception {
        assertThat(S3MetadataCodec.decode(S3MetadataCodec.encode(
                Map.of("CaseSensitive", "value", "\u4e2d\u6587\u952e", "\u4e2d\u6587\u503c"))))
                .containsEntry("CaseSensitive", "value").containsEntry("\u4e2d\u6587\u952e", "\u4e2d\u6587\u503c");
        Map<String, String> oversized = new LinkedHashMap<>();
        oversized.put("key", "x".repeat(2000));
        assertThatIOException().isThrownBy(() -> S3MetadataCodec.encode(oversized))
                .withMessageContaining("encoded size");
    }

    private static void assertInterruptRestored(Throwable cause) {
        Thread.interrupted();
        try {
            RecordingS3 client = new RecordingS3();
            client.getFailure = SdkClientException.builder().message("sensitive")
                    .cause(new IOException("wrapper", cause)).build();
            S3CocoObjectStorage storage = storage(client.client(), "bucket", "ns");
            assertThatIOException().isThrownBy(() -> storage.get("key"))
                    .withMessage("S3 get failed");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        }
        finally {
            Thread.interrupted();
        }
    }

    private static CocoObjectWriteRequest writeRequest(String key) {
        return new CocoObjectWriteRequest(key, new ByteArrayInputStream("body".getBytes()), 4L,
                null, Map.of());
    }

    private static S3Object object(String key, Long size) {
        return S3Object.builder().key(key).size(size).lastModified(Instant.EPOCH).build();
    }

    private static String craftedMetadata(String key, String value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(1);
            writeMetadataText(output, key);
            writeMetadataText(output, value);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
    }

    private static void writeMetadataText(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        output.writeShort(bytes.length);
        output.write(bytes);
    }

    private static S3Exception s3Failure(int status, String errorCode, String message) {
        S3Exception.Builder builder = S3Exception.builder();
        builder.statusCode(status);
        builder.message(message);
        if (errorCode != null) {
            builder.awsErrorDetails(AwsErrorDetails.builder().errorCode(errorCode).errorMessage(message).build());
        }
        return (S3Exception) builder.build();
    }

    private static S3CocoObjectStorage storage(S3Client client, String bucket, String prefix) {
        return new S3CocoObjectStorage(client, properties(bucket, prefix));
    }

    private static CocoStorageS3Properties properties(String bucket, String prefix) {
        CocoStorageS3Properties properties = new CocoStorageS3Properties();
        properties.setBucket(bucket);
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
        private GetObjectRequest getRequest;
        private DeleteObjectRequest deleteRequest;
        private ListObjectsV2Request listRequest;
        private GetObjectResponse getResponse = GetObjectResponse.builder().contentLength(3L)
                .lastModified(Instant.EPOCH).build();
        private HeadObjectResponse headResponse = HeadObjectResponse.builder().contentLength(3L)
                .lastModified(Instant.EPOCH).eTag("etag").build();
        private ListObjectsV2Response listResponse = ListObjectsV2Response.builder().build();
        private RuntimeException putFailure;
        private RuntimeException getFailure;
        private RuntimeException headFailure;
        private RuntimeException deleteFailure;
        private RuntimeException listFailure;
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
            if (this.putFailure != null) { throw this.putFailure; }
            PutObjectRequest request = (PutObjectRequest) arguments[0];
            this.putRequests.add(request);
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

        private HeadObjectResponse head(Object[] arguments) {
            if (this.headFailure != null) { throw this.headFailure; }
            return this.headResponse;
        }

        private DeleteObjectResponse delete(Object[] arguments) {
            if (this.deleteFailure != null) { throw this.deleteFailure; }
            this.deleteRequest = (DeleteObjectRequest) arguments[0];
            return DeleteObjectResponse.builder().build();
        }

        private ListObjectsV2Response list(Object[] arguments) {
            if (this.listFailure != null) { throw this.listFailure; }
            this.listRequest = (ListObjectsV2Request) arguments[0];
            return this.listResponse;
        }
    }
}
