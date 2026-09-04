---
title: Object Storage
---

# Object Storage

Coco Object Storage (`coco-storage`) provides an implementation-agnostic object read/write SPI, along with a ready-to-use, secure streaming local reference implementation. It is responsible only for object content and its stable metadata; it **does not provide an HTTP Controller, authorization, an attachment metadata table, or an object key naming strategy** — those are left for the business application to decide. The module is bound to the `coco.storage` namespace and is disabled by default: the auto-configuration is only created when `coco.storage.enabled=true`.

## Overview

The storage capability is made up of three layers:

- **The `CocoObjectStorage` SPI**: defines the five methods `put` / `open` / `stat` / `exists` / `delete`, and is the only interface business code needs to deal with. A business application can declare its own Bean (such as an S3 or OSS implementation) to replace the built-in local implementation.
- **The content-addressed local reference implementation `LocalCocoObjectStorage`**: object keys only participate in internal mapping and never appear in local file paths; blobs are named with a random UUID, content digests use SHA-256, publishing uses atomic operations, and orphan files are reclaimed in the background according to a grace period.
- **The upload content validation chain**: `CocoStorageValidationBeanPostProcessor` wraps **every** `CocoObjectStorage` Bean in the container into a `ValidatingCocoObjectStorage`, performing magic-number signature validation and scanner integration before the actual write, so a business application's own S3 implementation automatically gains the same validation capability.

### SPI contract highlights

| Method | Description |
| --- | --- |
| `CocoObjectMetadata put(CocoObjectPutRequest)` | Streams the object write and returns the persisted stable metadata |
| `CocoObjectResource open(String key)` | Opens a resource that can be read on demand, without reading the whole object into memory at `open` time |
| `CocoObjectMetadata stat(String key)` | Queries object metadata |
| `boolean exists(String key)` | Checks whether an object exists |
| `boolean delete(String key)` | Idempotent delete; returns `false` rather than throwing when the object no longer exists |

`CocoObjectPutRequest` only accepts the object key (`key`) decided by the business application, and **does not accept the original file name as a basis for storage location**; `contentLength` is only used to reject obviously oversized requests early, and the implementation still validates against the number of bytes actually read. `CocoObjectMetadata` is generated and persisted by the storage implementation, where `sha256` is forced to be a lowercase 64-character hexadecimal digest. Business applications should not treat a client-submitted file name or length directly as trusted metadata.

## How to enable and integrate

The module is disabled by default and must be explicitly turned on:

```yaml
coco:
  storage:
    enabled: true          # false by default; when off, no storage Bean is created
    local:
      root: /data/coco/objects   # storage root directory for the local reference implementation
```

Once enabled, and in the absence of a business-provided custom `CocoObjectStorage` Bean, the framework creates a `LocalCocoObjectStorage`. Injection works the same as for any ordinary Bean:

```java
@Service
public class AttachmentService {

    private final CocoObjectStorage storage;

    public AttachmentService(CocoObjectStorage storage) {
        this.storage = storage;
    }

    public CocoObjectMetadata upload(String key, InputStream content, long length, String contentType) {
        CocoObjectPutRequest request = CocoObjectPutRequest.of(key, content, length, contentType);
        return this.storage.put(request);
    }
}
```

Whether the injected Bean is the local implementation or a business-provided custom implementation, as long as `coco.storage.validation.enabled` is `true` (the default), what you get is the Bean wrapped by `ValidatingCocoObjectStorage`.

## Usage examples

### Writing and the overwrite policy

Overwrite behavior is controlled by `CocoStorageOverwritePolicy`; when the request does not specify one explicitly, the configured default is used (`REJECT` by default):

```java
// Explicitly use the replace policy to atomically replace the metadata reference of a published object
CocoObjectPutRequest request = new CocoObjectPutRequest(
        "avatars/2026/user-1024.png",
        content,
        contentLength,
        "image/png",
        CocoStorageOverwritePolicy.REPLACE);
CocoObjectMetadata metadata = storage.put(request);
```

`REJECT` throws a `CocoStorageException` carrying the `OBJECT_ALREADY_EXISTS` code when it encounters an existing object; `REPLACE` atomically replaces the manifest, and the old blob is marked as an orphan to await background reclamation.

### Reading

```java
try (CocoObjectResource resource = storage.open("avatars/2026/user-1024.png")) {
    CocoObjectMetadata metadata = resource.metadata();
    Resource content = resource.resource();   // a Spring Resource; its input stream can be opened on demand
    // write content back to the HTTP response, etc.
}
```

`CocoObjectResource` implements `AutoCloseable`; closing it with try-with-resources releases the read snapshot that has not yet been opened, and the local implementation uses this boundary to defer reclamation of blobs that have been overwritten or deleted.

## Security features of the local implementation

`LocalCocoObjectStorage` is a reference implementation hardened against Web input. Its positioning is to **defend against Web input and configuration that mistakenly points to linked paths — not against a local malicious process that already has write access to the storage root directory**. Its core features are:

- **Object key and local path isolation**: an object key is first validated through `normalizeKey` (which rejects backslashes, absolute paths, drive-letter prefixes, `.`/`..` segments, and unsafe file-name characters), then hashed with SHA-256 to obtain the `keyHash`. The local path is built solely from the first two segments of the `keyHash` into a two-level shard directory; the object key itself never appears in the file path, eliminating path traversal at the root.
- **Content addressing and blob naming**: object content is written to a `.bin` blob named with a random `UUID`, and the metadata digest is a SHA-256 computed in a streaming fashion. The manifest (`<keyHash>.properties`) records the metadata and references the blob.
- **Link and reparse-point defense**: attribute checks on the root directory, shard directories, and files all use `LinkOption.NOFOLLOW_LINKS` throughout, rejecting symbolic links, reparse points such as Windows junctions, and directory entries of unexpected types; it also uses `toRealPath()` to compare real paths and avoid false positives caused by platform aliases (such as macOS `/var` versus `/private/var`).
- **Atomic publishing**: a blob is first written to a temporary file and then atomically moved into its final location; the manifest is likewise written to a temporary file first — the `REJECT` policy uses `createLink` to guarantee the "do not overwrite" semantics, and the `REPLACE` policy uses an atomic move to substitute it. If any step fails, temporary files are cleaned up and no half-finished object is published.
- **Orphan GC**: overwritten or deleted blobs are tagged with `.orphan`, and are only reclaimed once they exceed the grace period (`orphan-grace-period`, default 5 minutes) and are no longer referenced by any manifest or read snapshot. Reclamation runs once at construction time, then runs on a daemon thread according to `gc-interval` (default 5 minutes; setting it to `0` means reclamation only at startup and shutdown), and runs once more at shutdown.
- **Striped locks**: 256 `ReentrantLock`s are sharded by `keyHash`, guaranteeing that reads, writes, deletes, and manifest publishing for the same object key are mutually exclusive; GC acquires all stripe locks in a fixed order, so it never interleaves with business operations.
- **Size and type constraints**: writes are validated against the actual byte stream, and content exceeding `max-size-bytes` throws `CONTENT_TOO_LARGE`; when `allowedContentTypes` is non-empty the content type is validated (supporting `*/*` and `type/*` wildcards), and when `allowedExtensions` is non-empty the extension is validated.

## Magic-number signature validation

`CocoSignatureContentValidator` is the default content validator. It first reads a number of bytes from the head of the content (`probe-size`, default 512), then performs a two-step check whose **order cannot be reversed**:

1. **Dangerous-signature blacklist (unconditional rejection, taking precedence over the extension whitelist)**: all uploaded content is uniformly compared against the dangerous-signature table, and a match throws `DANGEROUS_CONTENT`. This check is independent of the extension, so it can identify an executable renamed to `.png`. If the extension were compared first, an executable renamed to a whitelisted extension would be let through because its extension is legal.

   | Dangerous signature | Magic number |
   | --- | --- |
   | PE executable (EXE/DLL) | `4D 5A` |
   | ELF executable | `7F 45 4C 46` |
   | Java class file | `CA FE BA BE` |
   | Shell script shebang | `23 21` (`#!`) |

2. **Extension consistency**: if `require-signature-match` is `true`, the content magic number is compared against the extension derived from the object key, and a mismatch throws `SIGNATURE_MISMATCH`. The built-in signatures cover `jpg`/`jpeg`, `png`, `gif`, `webp`, `pdf`, `mp4`, and the ZIP family (`zip`/`docx`/`xlsx`/`pptx` share the same ZIP magic number).

Before delegating the write, `ValidatingCocoObjectStorage` reads the head bytes to validate and scan, then splices the already-read bytes back to the front of the stream via a `SequenceInputStream`, so the decorated implementation still receives the complete content. The entire process **does not buffer the whole uploaded content into memory**. Read-oriented methods (`open`/`stat`/`exists`/`delete`) are delegated directly with no extra processing.

## Scanner SPI

`CocoFileScanner` is the integration point for malware detection; **the framework itself does not implement virus scanning**. Real detection depends on an external engine (such as ClamAV) and its continuously updated virus database, and the framework cannot provide effective detection without introducing an external dependency and taking on the responsibility of maintaining a signature database. The default implementation `NoOpCocoFileScanner` performs no checks. When a business application integrates an external engine, it declares its own `CocoFileScanner` Bean, throwing `CocoStorageException` on a reject decision and returning normally to let the content through:

```java
@Component
public class ClamAvFileScanner implements CocoFileScanner {

    @Override
    public void scan(CocoContentProbe probe) {
        // probe.probeBytes() is only the head bytes; a full scan requires the business side to separately read the complete content and integrate with the external engine
        if (isMalicious(probe)) {
            throw new CocoStorageException(CocoStorageErrorCode.SCAN_REJECTED, probe.key());
        }
    }
}
```

## Integrating custom storage (S3 SPI skeleton example)

A business application introduces the corresponding SDK on its own and declares a `CocoObjectStorage` Bean to replace the local implementation; `@ConditionalOnMissingBean(CocoObjectStorage.class)` ensures the local implementation automatically steps aside. This Bean is likewise wrapped by the validation decorator, so there is no need to re-implement validation logic:

```java
@Bean
public CocoObjectStorage s3ObjectStorage(/* business-provided S3 client and bucket configuration */) {
    return new CocoObjectStorage() {
        @Override
        public CocoObjectMetadata put(CocoObjectPutRequest request) {
            // 1. read request.content() and stream-upload to the object store
            // 2. compute SHA-256, determine contentType and size
            // 3. return new CocoObjectMetadata(request.key(), size, contentType, sha256, Instant.now())
            throw new UnsupportedOperationException("integrate a specific SDK");
        }

        @Override public CocoObjectResource open(String key) { /* ... */ throw new UnsupportedOperationException(); }
        @Override public CocoObjectMetadata stat(String key) { /* ... */ throw new UnsupportedOperationException(); }
        @Override public boolean exists(String key) { /* ... */ throw new UnsupportedOperationException(); }
        @Override public boolean delete(String key) { /* ... */ throw new UnsupportedOperationException(); }
    };
}
```

## Key configuration options

Bound to the prefix `coco.storage` (corresponding to `CocoStorageProperties`):

| Option | Type | Default | Description |
| --- | --- | --- | --- |
| `coco.storage.enabled` | `boolean` | `false` | Whether to enable storage auto-configuration; when off, no storage Bean is created |
| `coco.storage.max-size-bytes` | `long` | `10485760` (10 MiB) | Maximum allowed upload byte count; must be greater than zero |
| `coco.storage.allowed-content-types` | `Set<String>` | empty set (no restriction) | Allowed content types, supporting `*/*` and `type/*` wildcards; empty means no restriction |
| `coco.storage.allowed-extensions` | `Set<String>` | `jpg, jpeg, png, gif, webp, pdf, txt, csv` | Allowed-extension whitelist (secure default, without leading dots); empty means no restriction |
| `coco.storage.overwrite-policy` | `CocoStorageOverwritePolicy` | `REJECT` | Default overwrite policy: `REJECT` refuses an existing object, `REPLACE` replaces it atomically |
| `coco.storage.local.root` | `Path` | none | Storage root directory for the local reference implementation |
| `coco.storage.local.orphan-grace-period` | `Duration` | `5m` | Grace period for reclaiming internal files no longer referenced by a manifest; cannot be negative |
| `coco.storage.local.gc-interval` | `Duration` | `5m` | Background orphan-reclamation interval; `0` means reclamation only at startup and shutdown |
| `coco.storage.validation.enabled` | `boolean` | `true` | Whether to enable the upload content validation decorator |
| `coco.storage.validation.probe-size` | `int` | `512` | Number of bytes to probe from the content head; a non-positive value falls back to the default |
| `coco.storage.validation.reject-dangerous-signatures` | `boolean` | `true` | Whether to reject content that matches a dangerous signature |
| `coco.storage.validation.require-signature-match` | `boolean` | `true` | Whether to require the content magic number to match the extension |

The default extension whitelist **deliberately excludes** the following types: `docx`/`xlsx`/`pptx` (based on the ZIP container, whose magic number is exactly the same as an ordinary archive, making the actual content indistinguishable), `zip` (an arbitrary-content container that also carries a zip-bomb risk), and `svg` (which can embed JavaScript and is an XSS vector). When a business genuinely needs these types, it should, while opening up the whitelist, add its own container-content parsing or rendering-isolation measures.

## Boundary considerations

**1. Uploads bypass the signature / encryption / replay-protection chain.** File uploads are usually submitted as multipart, whereas the Web module's request-body reading, signature validation, encryption/decryption, and replay protection are all built on body caching, and multipart requests are excluded from that caching chain. Therefore an upload endpoint that goes through object storage does not pass through this body-level security validation, and the business application must design its own authorization and abuse-prevention measures for upload endpoints.

**2. The framework does not perform virus scanning.** `CocoFileScanner` is only an integration point, and the default `NoOpCocoFileScanner` performs no checks. Magic-number validation can only identify content with obvious characteristics, such as "an executable disguised as a document," and is not equivalent to malware detection. Real virus scanning requires integrating an external engine such as ClamAV yourself and maintaining a signature database.

**3. Magic-number validation has inherent blind spots.**
- Extensions such as `txt`, `csv`, `json`, `xml`, and `md` have no reliable magic number, are not in the signature table, and `matchesExtension` returns `true` for them unconditionally — such files are in practice constrained only by the extension whitelist, and magic-number validation provides them no additional protection.
- ZIP-family formats (`docx`, `xlsx`, `pptx`, `zip`) share exactly the same magic number, so a signature match can only confirm "this is a ZIP container" and cannot prove it is the declared kind of Office document, nor distinguish it from an ordinary ZIP archive. When a precise determination is needed, the internal structure of the container must be parsed.

**4. The local implementation's threat model has boundaries.** `LocalCocoObjectStorage` defends against Web input and configuration that mistakenly points to linked paths, but does not defend against a local malicious process that already has write access to the storage root directory. In production it should be used together with directory permissions at the operating-system level.

