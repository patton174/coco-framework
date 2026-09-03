---
title: 请求安全（加密/签名/防重放）
---

# 请求安全（加密 / 签名 / 防重放）

Coco Web 在请求进入业务代码之前，提供三道可独立开关的请求安全过滤器：AES-GCM 请求解密、HMAC-SHA256 签名校验、防重放。三者都绑定 `coco.web` 命名空间，依赖前一章介绍的请求体缓存来重复读取原始请求体。

它们协作的典型顺序是：请求体缓存 → 解密 → 验签 → 防重放 → 业务逻辑。

## AES-GCM 请求解密

### 功能简介

请求解密过滤器使用 `AES/GCM/NoPadding` 对请求体密文执行认证解密，由 `AesGcmCocoRequestDecryptor` 实现。GCM 是带认证的加密模式：解密时会一并校验认证标签，认证失败即视为解密失败并拒绝请求，因此密文一旦被篡改无法通过。

客户端通过请求头声明加密材料：加密标记（默认 `X-Coco-Encrypted`）、应用标识（`X-Coco-App-Id`）、密钥标识（`X-Coco-Key-Id`）、IV（`X-Coco-IV`）、算法（`X-Coco-Algorithm`）。框架用 `appId` 或 `appId:keyId` 从本地密钥映射中查找 AES 密钥，后者优先级更高。密钥、IV、密文默认按 Base64 解码。

### 如何启用与配置

由 `coco.web.encryption` 控制，默认启用（但需配置密钥后才会真正生效）。

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

### 关键配置项

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `coco.web.encryption.enabled` | `true` | 是否启用 AES 解密设施 |
| `coco.web.encryption.required` | `false` | 是否要求所有请求都必须加密 |
| `coco.web.encryption.default-algorithm` | `AES-GCM` | 请求未声明算法时的默认解密算法 |
| `coco.web.encryption.gcm-tag-length-bits` | `128` | GCM 认证标签长度（bit），小于等于零时恢复默认 |
| `coco.web.encryption.key-encoding` | `BASE64` | 密钥文本编码 |
| `coco.web.encryption.iv-encoding` | `BASE64` | IV 文本编码 |
| `coco.web.encryption.payload-encoding` | `BASE64` | 密文请求体文本编码 |
| `coco.web.encryption.keys` | 空 | 本地 AES 密钥映射，键为 `appId` 或 `appId:keyId` |
| `coco.web.encryption.encrypted-header-name` | `X-Coco-Encrypted` | 加密标记请求头 |
| `coco.web.encryption.app-id-header-name` | `X-Coco-App-Id` | 应用标识请求头 |
| `coco.web.encryption.key-id-header-name` | `X-Coco-Key-Id` | 密钥标识请求头 |
| `coco.web.encryption.iv-header-name` | `X-Coco-IV` | IV 请求头 |
| `coco.web.encryption.algorithm-header-name` | `X-Coco-Algorithm` | 算法请求头 |

## HMAC-SHA256 签名校验

### 功能简介

签名校验过滤器用共享密钥对规范化后的请求文本计算 HMAC-SHA256，由 `HmacSha256CocoSignatureVerifier` 实现。它同时支持十六进制和 Base64 两种签名文本格式，并使用 `MessageDigest.isEqual` 做常量时间比较，避免时序侧信道泄露。

签名材料同样从请求头读取：应用标识（`X-Coco-App-Id`）、密钥标识（`X-Coco-Key-Id`）、时间戳（`X-Coco-Timestamp`）、随机串（`X-Coco-Nonce`）、签名（`X-Coco-Sign`，兜底 `X-Coco-Signature`）、算法（`X-Coco-Sign-Algorithm`）。密钥同样从 `appId` 或 `appId:keyId` 本地映射查找。

### 时间戳与时钟偏差

签名请求默认要求携带时间戳，并校验时间戳是否落在允许的时钟偏差窗口内（默认 300 秒）。这既能限制签名的有效期，也为防重放提供时间边界。客户端与服务端时钟差异超出 `max-clock-skew-seconds` 的请求会被拒绝，因此部署时应保证双方时钟同步（如 NTP）。

### 如何启用与配置

由 `coco.web.signature` 控制，默认启用（需配置密钥后生效）。

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

### 关键配置项

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `coco.web.signature.enabled` | `true` | 是否启用签名校验设施 |
| `coco.web.signature.required` | `false` | 是否要求所有请求必须携带签名 |
| `coco.web.signature.timestamp-required` | `true` | 签名请求是否必须携带时间戳 |
| `coco.web.signature.timestamp-validation-enabled` | `true` | 是否校验签名时间戳窗口 |
| `coco.web.signature.max-clock-skew-seconds` | `300` | 允许的客户端与服务端时间差（秒），小于等于零时恢复默认 |
| `coco.web.signature.default-algorithm` | `HMAC-SHA256` | 默认签名算法 |
| `coco.web.signature.secrets` | 空 | 本地签名密钥映射，键为 `appId` 或 `appId:keyId` |
| `coco.web.signature.signature-header-name` | `X-Coco-Sign` | 签名请求头 |
| `coco.web.signature.signature-fallback-header-name` | `X-Coco-Signature` | 签名兜底请求头 |
| `coco.web.signature.timestamp-header-name` | `X-Coco-Timestamp` | 时间戳请求头 |
| `coco.web.signature.nonce-header-name` | `X-Coco-Nonce` | 随机串请求头 |

## 防重放

### 功能简介

防重放过滤器基于请求携带的 `appId`、`keyId`、时间戳、随机串（可选叠加 HTTP 方法和请求路径）拼装防重放键，在重放窗口内每个键只允许占用一次，从而拦截被截获后原样重放的请求。它默认保护已签名和已加密的请求。

占用状态由 `CocoReplayStore` 保存，框架内置两种实现：

- **内存存储 `InMemoryCocoReplayStore`**（默认，`store-type: in-memory`）：进程内 `ConcurrentHashMap`，带后台定时清理过期键，适合单进程应用和本地开发。
- **JDBC 存储 `JdbcCocoReplayStore`**（`store-type: jdbc`）：使用业务项目提供的 `JdbcOperations` 和预建表，通过防重放键摘要（SHA-256）的唯一约束实现跨实例原子占用。**框架不会创建表、迁移结构或管理数据源**，表结构需业务预建（键 `replay_key_hash`、过期时间 `expires_at_epoch_millis`）。

（此外还提供基于 Spring Data Redis 的 `redis` 存储类型。）

### 集群部署边界

内存存储是**进程本地**的，多个实例之间互不感知，无法在集群中真正防重放。`InMemoryCocoReplayStore` 启动时会打印告警提示这一点。集群部署必须把 `store-type` 切换为 `jdbc`（或 `redis`），或自行提供共享的 `CocoReplayStore` 实现。

### 如何启用与配置

由 `coco.web.replay` 控制，默认启用。

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

### 关键配置项

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `coco.web.replay.enabled` | `true` | 是否启用防重放设施 |
| `coco.web.replay.required` | `false` | 是否要求所有请求都通过防重放校验 |
| `coco.web.replay.store-type` | `in-memory` | 存储类型：`in-memory` / `jdbc` / `redis` |
| `coco.web.replay.protect-signed-requests` | `true` | 是否保护已签名请求 |
| `coco.web.replay.protect-encrypted-requests` | `true` | 是否保护已加密请求 |
| `coco.web.replay.include-method` | `true` | 防重放键是否包含 HTTP 方法 |
| `coco.web.replay.include-path` | `true` | 防重放键是否包含请求路径 |
| `coco.web.replay.ttl-seconds` | `300` | 重放窗口秒数，小于等于零时恢复默认 |
| `coco.web.replay.cleanup-interval-seconds` | `60` | 内存/JDBC 存储过期键清理间隔秒数 |
| `coco.web.replay.max-clock-skew-seconds` | `300` | 允许的客户端时间戳最大时钟偏差秒数，小于零时恢复默认 |
| `coco.web.replay.jdbc.table-name` | `coco_replay_key` | JDBC 存储的业务预建表名（校验为合法 SQL 标识符） |
| `coco.web.replay.redis.key-prefix` | `coco:replay:` | Redis 存储键前缀 |

## 共同的重要边界：multipart 绕过

三道过滤器都依赖请求体缓存重复读取原始请求体，而请求体缓存默认排除 `multipart/`（文件上传）和 `application/octet-stream`。这意味着**文件上传请求会绕过 AES 解密、签名校验和防重放过滤器**。

设计原因见「Web 运行时」章：文件上传体积大、不适合整体读入内存。若业务需要对上传接口做加密、验签或防重放保护，应采用独立于请求体缓存的方案（例如对元数据而非文件体签名，或在网关侧处理）。

