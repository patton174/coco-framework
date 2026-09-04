---
title: Request Security (Encryption / Signing / Replay Protection)
---

# Request Security (Encryption / Signing / Replay Protection)

Before a request reaches business code, Coco Web provides three independently toggleable request security filters: AES-GCM request decryption, HMAC-SHA256 signature verification, and replay protection. All three are bound to the `coco.web` namespace and rely on the request body caching introduced in the previous chapter to re-read the original request body.

Their typical collaboration order is: request body caching → decryption → signature verification → replay protection → business logic.

## AES-GCM Request Decryption

### Overview

The request decryption filter uses `AES/GCM/NoPadding` to perform authenticated decryption of the request body ciphertext, implemented by `AesGcmCocoRequestDecryptor`. GCM is an authenticated encryption mode: decryption also verifies the authentication tag, and an authentication failure is treated as a decryption failure and rejects the request, so tampered ciphertext can never pass.

The client declares the encryption material via request headers: the encryption flag (default `X-Coco-Encrypted`), the application identifier (`X-Coco-App-Id`), the key identifier (`X-Coco-Key-Id`), the IV (`X-Coco-IV`), and the algorithm (`X-Coco-Algorithm`). The framework looks up the AES key from the local key map using `appId` or `appId:keyId`, with the latter taking precedence. The key, IV, and ciphertext are Base64-decoded by default.

### How to Enable and Configure

Controlled by `coco.web.encryption`, enabled by default (but it only truly takes effect once keys are configured).

```yaml
coco:
  web:
    encryption:
      enabled: true
      required: false
      default-algorithm: AES-GCM
      gcm-tag-length-bits: 128
      key-encoding: BASE64
      iv-encoding: BASE64
      payload-encoding: BASE64
      keys:
        my-app: "Base64EncodedAesKey=="
        my-app:key-2024: "AnotherBase64Key=="
```

### Key Configuration Items

| Configuration Item | Default | Description |
| --- | --- | --- |
| `coco.web.encryption.enabled` | `true` | Whether to enable the AES decryption facility |
| `coco.web.encryption.required` | `false` | Whether all requests must be encrypted |
| `coco.web.encryption.default-algorithm` | `AES-GCM` | Default decryption algorithm when the request does not declare one |
| `coco.web.encryption.gcm-tag-length-bits` | `128` | GCM authentication tag length (bits); reverts to default when less than or equal to zero |
| `coco.web.encryption.key-encoding` | `BASE64` | Key text encoding |
| `coco.web.encryption.iv-encoding` | `BASE64` | IV text encoding |
| `coco.web.encryption.payload-encoding` | `BASE64` | Ciphertext request body text encoding |
| `coco.web.encryption.keys` | empty | Local AES key map, keyed by `appId` or `appId:keyId` |
| `coco.web.encryption.encrypted-header-name` | `X-Coco-Encrypted` | Encryption flag request header |
| `coco.web.encryption.app-id-header-name` | `X-Coco-App-Id` | Application identifier request header |
| `coco.web.encryption.key-id-header-name` | `X-Coco-Key-Id` | Key identifier request header |
| `coco.web.encryption.iv-header-name` | `X-Coco-IV` | IV request header |
| `coco.web.encryption.algorithm-header-name` | `X-Coco-Algorithm` | Algorithm request header |

## HMAC-SHA256 Signature Verification

### Overview

The signature verification filter computes HMAC-SHA256 over the normalized request text using a shared secret, implemented by `HmacSha256CocoSignatureVerifier`. It supports both hexadecimal and Base64 signature text formats, and uses `MessageDigest.isEqual` for constant-time comparison to avoid timing side-channel leaks.

The signature material is likewise read from request headers: the application identifier (`X-Coco-App-Id`), the key identifier (`X-Coco-Key-Id`), the timestamp (`X-Coco-Timestamp`), the nonce (`X-Coco-Nonce`), the signature (`X-Coco-Sign`, falling back to `X-Coco-Signature`), and the algorithm (`X-Coco-Sign-Algorithm`). The secret is likewise looked up from the local `appId` or `appId:keyId` map.

### Timestamp and Clock Skew

Signed requests are required to carry a timestamp by default, and the timestamp is validated to fall within the allowed clock skew window (300 seconds by default). This both limits the validity period of a signature and provides a time boundary for replay protection. Requests whose client-server clock difference exceeds `max-clock-skew-seconds` are rejected, so both sides' clocks should be kept in sync at deployment time (e.g. via NTP).

### How to Enable and Configure

Controlled by `coco.web.signature`, enabled by default (takes effect once secrets are configured).

```yaml
coco:
  web:
    signature:
      enabled: true
      required: false
      timestamp-required: true
      timestamp-validation-enabled: true
      max-clock-skew-seconds: 300
      default-algorithm: HMAC-SHA256
      secrets:
        my-app: "shared-secret-value"
        my-app:key-2024: "another-secret"
```

### Key Configuration Items

| Configuration Item | Default | Description |
| --- | --- | --- |
| `coco.web.signature.enabled` | `true` | Whether to enable the signature verification facility |
| `coco.web.signature.required` | `false` | Whether all requests must carry a signature |
| `coco.web.signature.timestamp-required` | `true` | Whether signed requests must carry a timestamp |
| `coco.web.signature.timestamp-validation-enabled` | `true` | Whether to validate the signature timestamp window |
| `coco.web.signature.max-clock-skew-seconds` | `300` | Allowed client-server time difference (seconds); reverts to default when less than or equal to zero |
| `coco.web.signature.default-algorithm` | `HMAC-SHA256` | Default signature algorithm |
| `coco.web.signature.secrets` | empty | Local signing key map, keyed by `appId` or `appId:keyId` |
| `coco.web.signature.signature-header-name` | `X-Coco-Sign` | Signature request header |
| `coco.web.signature.signature-fallback-header-name` | `X-Coco-Signature` | Fallback signature request header |
| `coco.web.signature.timestamp-header-name` | `X-Coco-Timestamp` | Timestamp request header |
| `coco.web.signature.nonce-header-name` | `X-Coco-Nonce` | Nonce request header |

## Replay Protection

### Overview

The replay protection filter assembles a replay-protection key from the `appId`, `keyId`, timestamp, and nonce carried by the request (optionally combined with the HTTP method and request path). Within the replay window, each key may only be occupied once, thereby intercepting requests that are intercepted and replayed verbatim. By default it protects signed and encrypted requests.

The occupancy state is stored by `CocoReplayStore`, and the framework ships with two implementations:

- **In-memory store `InMemoryCocoReplayStore`** (default, `store-type: in-memory`): an in-process `ConcurrentHashMap` with a background scheduled cleanup of expired keys, suitable for single-process applications and local development.
- **JDBC store `JdbcCocoReplayStore`** (`store-type: jdbc`): uses the `JdbcOperations` and a pre-created table provided by the business project, achieving cross-instance atomic occupancy via a unique constraint on the replay-protection key digest (SHA-256). **The framework does not create tables, migrate schemas, or manage the data source**; the table schema must be pre-created by the business (with the `replay_key_hash` key and the `expires_at_epoch_millis` expiration time).

(In addition, a `redis` store type based on Spring Data Redis is provided.)

### Cluster Deployment Boundary

The in-memory store is **process-local**; multiple instances are unaware of one another and cannot truly perform replay protection across a cluster. `InMemoryCocoReplayStore` prints a warning at startup to highlight this. Cluster deployments must switch `store-type` to `jdbc` (or `redis`), or provide their own shared `CocoReplayStore` implementation.

### How to Enable and Configure

Controlled by `coco.web.replay`, enabled by default.

```yaml
coco:
  web:
    replay:
      enabled: true
      required: false
      store-type: in-memory   # in-memory / jdbc / redis
      protect-signed-requests: true
      protect-encrypted-requests: true
      include-method: true
      include-path: true
      ttl-seconds: 300
      cleanup-interval-seconds: 60
      max-clock-skew-seconds: 300
      jdbc:
        table-name: coco_replay_key
      redis:
        key-prefix: "coco:replay:"
```

### Key Configuration Items

| Configuration Item | Default | Description |
| --- | --- | --- |
| `coco.web.replay.enabled` | `true` | Whether to enable the replay protection facility |
| `coco.web.replay.required` | `false` | Whether all requests must pass replay protection validation |
| `coco.web.replay.store-type` | `in-memory` | Store type: `in-memory` / `jdbc` / `redis` |
| `coco.web.replay.protect-signed-requests` | `true` | Whether to protect signed requests |
| `coco.web.replay.protect-encrypted-requests` | `true` | Whether to protect encrypted requests |
| `coco.web.replay.include-method` | `true` | Whether the replay-protection key includes the HTTP method |
| `coco.web.replay.include-path` | `true` | Whether the replay-protection key includes the request path |
| `coco.web.replay.ttl-seconds` | `300` | Replay window in seconds; reverts to default when less than or equal to zero |
| `coco.web.replay.cleanup-interval-seconds` | `60` | Expired-key cleanup interval (seconds) for the in-memory/JDBC store |
| `coco.web.replay.max-clock-skew-seconds` | `300` | Maximum allowed clock skew (seconds) for the client timestamp; reverts to default when negative |
| `coco.web.replay.jdbc.table-name` | `coco_replay_key` | Name of the pre-created business table for the JDBC store (validated as a legal SQL identifier) |
| `coco.web.replay.redis.key-prefix` | `coco:replay:` | Redis store key prefix |

## A Shared, Important Boundary: multipart Bypass

All three filters rely on request body caching to re-read the original request body, and request body caching excludes `multipart/` (file uploads) and `application/octet-stream` by default. This means **file upload requests bypass the AES decryption, signature verification, and replay protection filters**.

The design rationale is described in the "Web Runtime" chapter: file uploads are large and unsuitable for reading fully into memory. If your business needs encryption, signature verification, or replay protection for upload endpoints, adopt an approach independent of request body caching (for example, sign the metadata rather than the file body, or handle it at the gateway layer).
