package io.github.coco.storage.s3;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.github.coco.feature.storage.CocoObjectKey;
import io.github.coco.feature.storage.CocoObjectListResult;
import io.github.coco.feature.storage.CocoObjectMetadata;
import io.github.coco.feature.storage.CocoObjectReadResult;
import io.github.coco.feature.storage.CocoObjectStat;
import io.github.coco.feature.storage.CocoObjectStorage;
import io.github.coco.feature.storage.CocoObjectWriteRequest;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** Coco object storage implementation backed by synchronous AWS SDK S3 operations. */
public final class S3CocoObjectStorage implements CocoObjectStorage {

    private final S3Client s3Client;

    private final String bucket;

    private final String keyPrefix;

    private final boolean overwrite;

    private final long maxObjectSize;

    private final int listMaxSize;

    public S3CocoObjectStorage(S3Client s3Client, CocoStorageS3Properties properties) {
        this.s3Client = Objects.requireNonNull(s3Client, "s3Client must not be null");
        Objects.requireNonNull(properties, "properties must not be null").validate();
        this.bucket = properties.getBucket().trim();
        this.keyPrefix = properties.normalizedKeyPrefix();
        this.overwrite = properties.isOverwrite();
        this.maxObjectSize = properties.getMaxObjectSize();
        this.listMaxSize = properties.getListMaxSize();
    }

    @Override
    public CocoObjectStat put(CocoObjectWriteRequest request) throws IOException {
        Objects.requireNonNull(request, "request must not be null");
        String key = CocoObjectKey.validate(request.key());
        if (request.contentLength() != null && (request.contentLength() < 0 || request.contentLength() > this.maxObjectSize)) {
            throw new IOException("put object exceeds maximum size");
        }
        Map<String, String> metadata = S3MetadataCodec.encode(request.metadata());
        PutObjectRequest.Builder builder = PutObjectRequest.builder().bucket(this.bucket).key(remoteKey(key))
                .contentType(request.contentType()).metadata(metadata);
        if (!this.overwrite) {
            builder.ifNoneMatch("*");
        }
        PutObjectRequest putRequest = builder.build();
        try {
            StagedObject staged = stage(request.inputStream(), request.contentLength());
            try {
                putStaged(putRequest, staged);
                return CocoObjectStat.found(new CocoObjectMetadata(key, staged.length(), request.contentType(),
                        request.metadata(), Instant.now()));
            } finally {
                Files.deleteIfExists(staged.path());
            }
        } catch (SdkException exception) {
            throw translate("put", key, exception);
        }
    }

    @Override
    public Optional<CocoObjectReadResult> get(String key) throws IOException {
        String checkedKey = CocoObjectKey.validate(key);
        ResponseInputStream<GetObjectResponse> stream = null;
        try {
            stream = this.s3Client.getObject(GetObjectRequest.builder().bucket(this.bucket).key(remoteKey(checkedKey)).build());
            GetObjectResponse response = stream.response();
            return Optional.of(new CocoObjectReadResult(metadata(checkedKey, response.contentLength(), response.contentType(),
                    response.metadata(), response.lastModified()), stream));
        } catch (SdkException exception) {
            closeQuietly(stream);
            if (notFound(exception)) {
                return Optional.empty();
            }
            throw translate("get", checkedKey, exception);
        } catch (IOException | RuntimeException exception) {
            closeQuietly(stream);
            throw exception;
        }
    }

    @Override
    public CocoObjectStat stat(String key) throws IOException {
        String checkedKey = CocoObjectKey.validate(key);
        try {
            HeadObjectResponse response = this.s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(this.bucket).key(remoteKey(checkedKey)).build());
            return CocoObjectStat.found(metadata(checkedKey, response.contentLength(), response.contentType(),
                    response.metadata(), response.lastModified()));
        } catch (SdkException exception) {
            if (notFound(exception)) {
                return CocoObjectStat.notFound(checkedKey);
            }
            throw translate("stat", checkedKey, exception);
        }
    }

    /**
     * Deletes with the current ETag as an S3 conditional request. A service that ignores the conditional header can
     * still race a concurrent overwrite; such an incompatible service is not treated as offering atomic deletion.
     */
    @Override
    public boolean delete(String key) throws IOException {
        String checkedKey = CocoObjectKey.validate(key);
        try {
            HeadObjectResponse head = this.s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(this.bucket).key(remoteKey(checkedKey)).build());
            if (head.eTag() == null || head.eTag().isBlank()) {
                throw new IOException("delete cannot obtain a conditional ETag");
            }
            this.s3Client.deleteObject(DeleteObjectRequest.builder().bucket(this.bucket).key(remoteKey(checkedKey))
                    .ifMatch(head.eTag()).build());
            return true;
        } catch (SdkException exception) {
            if (notFound(exception) || status(exception) == 412) {
                return false;
            }
            throw translate("delete", checkedKey, exception);
        }
    }

    @Override
    public CocoObjectListResult list(String prefix, int limit, String continuationToken) throws IOException {
        String checkedPrefix = prefix == null ? "" : prefix;
        validatePrefix(checkedPrefix);
        if (limit < 1 || limit > this.listMaxSize) {
            throw new IllegalArgumentException("invalid list limit");
        }
        try {
            ListObjectsV2Response response = this.s3Client.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(this.bucket).prefix(remotePrefix(checkedPrefix)).maxKeys(limit)
                    .continuationToken(continuationToken).build());
            List<CocoObjectStat> values = new ArrayList<>();
            response.contents().forEach(item -> {
                String logicalKey = localKey(item.key());
                if (logicalKey != null) {
                    values.add(CocoObjectStat.found(new CocoObjectMetadata(logicalKey, item.size(), null, Map.of(),
                            item.lastModified())));
                }
            });
            return new CocoObjectListResult(List.copyOf(values), response.nextContinuationToken());
        } catch (SdkException exception) {
            throw translate("list", checkedPrefix, exception);
        }
    }

    private StagedObject stage(InputStream input, Long declaredLength) throws IOException {
        Path staging = Files.createTempFile("coco-storage-s3-", ".staging");
        try {
            long length = copyBounded(input, staging);
            if (declaredLength != null && declaredLength != length) {
                throw new IOException("content length mismatch");
            }
            return new StagedObject(staging, length);
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(staging);
            throw exception;
        }
    }

    private void putStaged(PutObjectRequest request, StagedObject staged) throws IOException {
        try (InputStream input = Files.newInputStream(staged.path())) {
            this.s3Client.putObject(request, RequestBody.fromInputStream(input, staged.length()));
        }
    }

    private long copyBounded(InputStream input, Path staging) throws IOException {
        long length = 0;
        byte[] buffer = new byte[8192];
        try (var output = Files.newOutputStream(staging)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (length > this.maxObjectSize - read) {
                    throw new IOException("put object exceeds maximum size");
                }
                length += read;
                output.write(buffer, 0, read);
            }
        }
        return length;
    }

    private CocoObjectMetadata metadata(String key, Long length, String contentType, Map<String, String> headers,
            Instant lastModified) throws IOException {
        return new CocoObjectMetadata(key, length == null ? 0 : length, contentType, S3MetadataCodec.decode(headers),
                lastModified == null ? Instant.EPOCH : lastModified);
    }

    private String remoteKey(String key) {
        return this.keyPrefix.isEmpty() ? key : this.keyPrefix + "/" + key;
    }

    private String remotePrefix(String prefix) {
        if (this.keyPrefix.isEmpty()) {
            return prefix;
        }
        return prefix.isEmpty() ? this.keyPrefix + "/" : this.keyPrefix + "/" + prefix;
    }

    private String localKey(String remoteKey) {
        if (this.keyPrefix.isEmpty()) {
            return remoteKey;
        }
        String expectedPrefix = this.keyPrefix + "/";
        return remoteKey.startsWith(expectedPrefix) ? remoteKey.substring(expectedPrefix.length()) : null;
    }

    private static void validatePrefix(String prefix) {
        if (!prefix.isEmpty()) {
            CocoObjectKey.validate(prefix.endsWith("/") ? prefix + "x" : prefix);
        }
    }

    private IOException translate(String operation, String key, SdkException exception) {
        if (interrupted(exception)) {
            Thread.currentThread().interrupt();
        }
        return new IOException("S3 " + operation + " failed for bucket=" + safeBucket() + ", key=" + safeKey(key)
                + ", status=" + status(exception));
    }

    private String safeBucket() {
        return safeKey(this.bucket);
    }

    private static String safeKey(String key) {
        return Integer.toUnsignedString(key.hashCode(), 16);
    }

    private static boolean notFound(SdkException exception) {
        return exception instanceof NoSuchKeyException || status(exception) == 404;
    }

    private static int status(SdkException exception) {
        return exception instanceof AwsServiceException serviceException ? serviceException.statusCode() : 0;
    }

    private static boolean interrupted(Throwable value) {
        for (Throwable current = value; current != null; current = current.getCause()) {
            if (current instanceof InterruptedException) {
                return true;
            }
        }
        return false;
    }

    private static void closeQuietly(InputStream input) {
        if (input != null) {
            try {
                input.close();
            } catch (IOException ignored) {
                // A failed response is already being translated.
            }
        }
    }

    private record StagedObject(Path path, long length) {
    }

}
