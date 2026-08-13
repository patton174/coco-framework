package io.github.coco.storage.s3;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
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

/**
 * Coco object storage implementation backed by synchronous AWS SDK S3 operations.
 * <p>
 * Create-only writes require backend support for S3 conditional writes. A successful response is accepted according
 * to the S3 protocol; an S3-compatible backend that silently ignores {@code If-None-Match} cannot be detected.
 * </p>
 */
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
            restoreInterrupt(exception);
            if (!this.overwrite && preconditionFailed(exception)) {
                throw new FileAlreadyExistsException("S3 put");
            }
            throw translate("put", exception);
        } catch (IOException exception) {
            restoreInterrupt(exception);
            throw exception;
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
            restoreInterrupt(exception);
            if (objectNotFound(exception)) {
                return Optional.empty();
            }
            throw translate("get", exception);
        } catch (IOException | RuntimeException exception) {
            closeQuietly(stream);
            restoreInterrupt(exception);
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
            restoreInterrupt(exception);
            if (objectNotFound(exception)) {
                return CocoObjectStat.notFound(checkedKey);
            }
            throw translate("stat", exception);
        } catch (IOException exception) {
            restoreInterrupt(exception);
            throw exception;
        }
    }

    /**
     * Checks existence with HEAD and then deletes the object. When HEAD supplies an ETag, the adapter sends
     * {@code If-Match} as a best-effort guard. This SPI does not promise atomic compare-delete behavior, and an
     * S3-compatible backend that ignores the condition can delete a concurrent replacement without detection.
     */
    @Override
    public boolean delete(String key) throws IOException {
        String checkedKey = CocoObjectKey.validate(key);
        try {
            HeadObjectResponse head = this.s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(this.bucket).key(remoteKey(checkedKey)).build());
            DeleteObjectRequest.Builder request = DeleteObjectRequest.builder()
                    .bucket(this.bucket).key(remoteKey(checkedKey));
            if (head.eTag() != null && !head.eTag().isBlank()) {
                request.ifMatch(head.eTag());
            }
            this.s3Client.deleteObject(request.build());
            return true;
        } catch (SdkException exception) {
            restoreInterrupt(exception);
            if (objectNotFound(exception) || preconditionFailed(exception)) {
                return false;
            }
            throw translate("delete", exception);
        }
    }

    @Override
    public CocoObjectListResult list(String prefix, int limit, String continuationToken) throws IOException {
        String checkedPrefix = prefix == null ? "" : prefix;
        validatePrefix(checkedPrefix);
        if (limit < 1 || limit > this.listMaxSize) {
            throw new IllegalArgumentException("invalid list limit");
        }
        String providerToken = S3ContinuationTokenCodec.decode(continuationToken, this.bucket,
                this.keyPrefix, checkedPrefix);
        try {
            ListObjectsV2Response response = this.s3Client.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(this.bucket).prefix(remotePrefix(checkedPrefix)).maxKeys(limit)
                    .continuationToken(providerToken).build());
            if (response.contents().size() > limit) {
                throw new IOException("S3 list returned too many objects");
            }
            List<CocoObjectStat> values = new ArrayList<>();
            for (software.amazon.awssdk.services.s3.model.S3Object item : response.contents()) {
                if (item == null || item.key() == null) {
                    throw new IOException("S3 list returned an object without a key");
                }
                String logicalKey = localKey(item.key());
                if (logicalKey == null || !logicalKey.startsWith(checkedPrefix)) {
                    throw new IOException("S3 list returned an object outside the requested namespace");
                }
                try {
                    CocoObjectKey.validate(logicalKey);
                }
                catch (IllegalArgumentException exception) {
                    throw new IOException("S3 list returned an invalid object key");
                }
                if (item.size() == null || item.size() < 0 || item.lastModified() == null) {
                    throw new IOException("S3 list returned incomplete object metadata");
                }
                values.add(CocoObjectStat.found(new CocoObjectMetadata(logicalKey, item.size(), null, Map.of(),
                        item.lastModified())));
            }
            values.sort(Comparator.comparing(value -> value.metadata().key()));
            String providerNextToken = response.nextContinuationToken();
            boolean hasNextToken = providerNextToken != null && !providerNextToken.isEmpty();
            if (Boolean.TRUE.equals(response.isTruncated()) != hasNextToken) {
                throw new IOException("S3 list returned inconsistent continuation state");
            }
            String nextToken = hasNextToken
                    ? encodeProviderToken(checkedPrefix, providerNextToken)
                    : null;
            return new CocoObjectListResult(List.copyOf(values), nextToken);
        } catch (SdkException exception) {
            throw translate("list", exception);
        } catch (IOException exception) {
            restoreInterrupt(exception);
            throw exception;
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

    private String encodeProviderToken(String requestPrefix, String providerToken) throws IOException {
        try {
            return S3ContinuationTokenCodec.encode(this.bucket, this.keyPrefix, requestPrefix, providerToken);
        }
        catch (IllegalArgumentException exception) {
            throw new IOException("S3 list returned an invalid continuation token");
        }
    }

    private static void validatePrefix(String prefix) {
        if (!prefix.isEmpty()) {
            CocoObjectKey.validate(prefix.endsWith("/") ? prefix + "x" : prefix);
        }
    }

    private static IOException translate(String operation, SdkException exception) {
        restoreInterrupt(exception);
        int status = status(exception);
        return new IOException("S3 " + operation + " failed" + (status > 0 ? " with HTTP status " + status : ""));
    }

    private static boolean objectNotFound(SdkException exception) {
        String errorCode = errorCode(exception);
        return exception instanceof NoSuchKeyException || "NoSuchKey".equals(errorCode)
                || "NotFound".equals(errorCode);
    }

    private static boolean preconditionFailed(SdkException exception) {
        return status(exception) == 412 || "PreconditionFailed".equals(errorCode(exception));
    }

    private static String errorCode(SdkException exception) {
        if (exception instanceof AwsServiceException serviceException
                && serviceException.awsErrorDetails() != null) {
            return serviceException.awsErrorDetails().errorCode();
        }
        return null;
    }

    private static int status(SdkException exception) {
        return exception instanceof AwsServiceException serviceException ? serviceException.statusCode() : 0;
    }

    private static void restoreInterrupt(Throwable value) {
        for (Throwable current = value; current != null; current = current.getCause()) {
            if (current instanceof InterruptedException || current instanceof InterruptedIOException
                    || current instanceof ClosedByInterruptException) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static void closeQuietly(InputStream input) {
        if (input != null) {
            try {
                input.close();
            } catch (IOException exception) {
                restoreInterrupt(exception);
                // A failed response is already being translated.
            }
        }
    }

    private record StagedObject(Path path, long length) {
    }

}
