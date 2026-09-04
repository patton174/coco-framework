---
title: 对象存储
---

# 对象存储

Coco 对象存储（`coco-storage`）提供一套与实现解耦的对象读写 SPI，以及一个可直接使用的安全流式本地参考实现。它只负责对象内容及其稳定元数据，**不提供 HTTP Controller、授权、附件元数据表和对象键命名策略**——这些由业务应用自行决定。模块绑定 `coco.storage` 命名空间，并默认关闭：只有在 `coco.storage.enabled=true` 时才创建自动配置。

## 功能简介

存储能力由三层组成：

- **`CocoObjectStorage` SPI**：定义 `put` / `open` / `stat` / `exists` / `delete` 五个方法，是业务代码唯一需要面对的接口。业务方可以声明自己的 Bean（如 S3、OSS 实现）取代内置的本地实现。
- **内容寻址本地参考实现 `LocalCocoObjectStorage`**：对象键只参与内部映射，不参与本地文件路径；blob 使用随机 UUID 命名、内容摘要走 SHA-256、发布使用原子操作、后台按宽限期回收孤儿文件。
- **上传内容校验链**：`CocoStorageValidationBeanPostProcessor` 把容器中的**每一个** `CocoObjectStorage` Bean 包装成 `ValidatingCocoObjectStorage`，在真正写入前做魔数签名校验与扫描器接入，因此业务自己的 S3 实现同样自动获得校验能力。

### SPI 契约要点

| 方法 | 说明 |
| --- | --- |
| `CocoObjectMetadata put(CocoObjectPutRequest)` | 流式写入对象，返回已持久化的稳定元数据 |
| `CocoObjectResource open(String key)` | 打开可按需读取的资源，不在 `open` 时把对象整体读入内存 |
| `CocoObjectMetadata stat(String key)` | 查询对象元数据 |
| `boolean exists(String key)` | 判断对象是否存在 |
| `boolean delete(String key)` | 幂等删除，对象已不存在时返回 `false` 而非抛异常 |

`CocoObjectPutRequest` 只接受业务方决定的对象键（`key`），**不接受原始文件名作为存储定位依据**；`contentLength` 仅用于提前拒绝明显超限的请求，实现仍按实际读取字节数校验。`CocoObjectMetadata` 由存储实现生成并持久化，其中 `sha256` 强制为小写十六进制 64 位摘要，业务方不应把客户端提交的文件名或长度直接当作可信元数据。

## 如何启用接入

模块默认关闭，需要显式开启：

```yaml
coco:
  storage:
    enabled: true          # 默认 false，不开启则不创建任何存储 Bean
    local:
      root: /data/coco/objects   # 本地参考实现的存储根目录
```

开启后，在没有业务自定义 `CocoObjectStorage` Bean 的情况下，框架会创建 `LocalCocoObjectStorage`。注入方式与普通 Bean 一致：

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

无论注入到的是本地实现还是业务自定义实现，只要 `coco.storage.validation.enabled` 为 `true`（默认），拿到的都是被 `ValidatingCocoObjectStorage` 包装后的 Bean。

## 使用示例

### 写入与覆盖策略

覆盖行为由 `CocoStorageOverwritePolicy` 控制，请求未显式指定时使用配置的默认值（默认 `REJECT`）：

```java
// 显式使用替换策略，原子替换已发布对象的元数据引用
CocoObjectPutRequest request = new CocoObjectPutRequest(
        "avatars/2026/user-1024.png",
        content,
        contentLength,
        "image/png",
        CocoStorageOverwritePolicy.REPLACE);
CocoObjectMetadata metadata = storage.put(request);
```

`REJECT` 遇到已存在对象会抛出携带 `OBJECT_ALREADY_EXISTS` 编码的 `CocoStorageException`；`REPLACE` 会原子替换 manifest，旧 blob 被标记为孤儿，等待后台回收。

### 读取

```java
try (CocoObjectResource resource = storage.open("avatars/2026/user-1024.png")) {
    CocoObjectMetadata metadata = resource.metadata();
    Resource content = resource.resource();   // Spring Resource，可按需打开输入流
    // 将 content 写回 HTTP 响应等
}
```

`CocoObjectResource` 实现了 `AutoCloseable`，用 try-with-resources 关闭尚未打开的读取快照，本地实现借此边界延迟回收已被覆盖或删除的 blob。

## 本地实现的安全特性

`LocalCocoObjectStorage` 是一个面向 Web 输入加固的参考实现。它的定位是**防御 Web 输入和配置误指向链接路径，不防御已经拥有存储根目录写权限的本地恶意进程**。核心特性如下：

- **对象键与本地路径隔离**：对象键先经过 `normalizeKey` 校验（拒绝反斜杠、绝对路径、盘符前缀、`.`/`..` 段和不安全文件名字符），再做 SHA-256 得到 `keyHash`。本地路径只由 `keyHash` 的前两段拼出两级 shard 目录，对象键本身从不出现在文件路径里，从根源上排除路径穿越。
- **内容寻址与 blob 命名**：对象内容写入以随机 `UUID` 命名的 `.bin` blob，元数据摘要为流式计算的 SHA-256。manifest（`<keyHash>.properties`）记录元数据并引用 blob。
- **链接与 reparse point 防御**：根目录、shard 目录和文件的属性校验全程使用 `LinkOption.NOFOLLOW_LINKS`，拒绝符号链接、Windows junction 等 reparse point 和非预期类型的目录项；同时用 `toRealPath()` 比较真实路径以规避平台别名（如 macOS `/var` 与 `/private/var`）造成的误判。
- **原子发布**：blob 先写入临时文件再原子移动到最终位置；manifest 也是先写临时文件，`REJECT` 策略用 `createLink` 保证“不覆盖”语义、`REPLACE` 策略用原子移动替换，任一环节失败都会清理临时文件，不会发布半成品。
- **孤儿 GC**：被覆盖或删除的 blob 打上 `.orphan` 标记，超过宽限期（`orphan-grace-period`，默认 5 分钟）且不再被任何 manifest 或读取快照引用时才回收。回收在构造时执行一次，随后按 `gc-interval`（默认 5 分钟，设为 `0` 表示仅在启动和关闭时回收）由守护线程执行，关闭时再执行一次。
- **条带锁**：256 个 `ReentrantLock` 按 `keyHash` 分片，保证同一对象键的读写删与 manifest 发布互斥；GC 按固定顺序获取所有条带锁，因此不会与业务操作交叉。
- **大小与类型约束**：写入时按实际字节流校验，超过 `max-size-bytes` 抛 `CONTENT_TOO_LARGE`；`allowedContentTypes` 非空时校验内容类型（支持 `*/*`、`type/*` 通配），`allowedExtensions` 非空时校验扩展名。

## 魔数签名校验

`CocoSignatureContentValidator` 是默认内容校验器，先读取内容头部若干字节（`probe-size`，默认 512），再执行两步判断，**顺序不可颠倒**：

1. **危险签名黑名单（无条件拒绝，优先于扩展名白名单）**：对所有上传内容统一比对危险签名表，命中即抛 `DANGEROUS_CONTENT`。该检查与扩展名无关，因此能识别改名为 `.png` 的可执行文件。若先比对扩展名，一个改名成白名单扩展名的可执行文件就会因为扩展名合法而被放行。

   | 危险签名 | 魔数 |
   | --- | --- |
   | PE executable（EXE/DLL） | `4D 5A` |
   | ELF executable | `7F 45 4C 46` |
   | Java class file | `CA FE BA BE` |
   | Shell script shebang | `23 21`（`#!`） |

2. **扩展名一致性**：若 `require-signature-match` 为 `true`，比对内容魔数与对象键推导出的扩展名是否一致，不一致抛 `SIGNATURE_MISMATCH`。内置签名覆盖 `jpg`/`jpeg`、`png`、`gif`、`webp`、`pdf`、`mp4` 以及 ZIP 系（`zip`/`docx`/`xlsx`/`pptx` 共享同一 ZIP 魔数）。

`ValidatingCocoObjectStorage` 在委托写入前读取头部字节做校验和扫描，随后把已读字节通过 `SequenceInputStream` 拼回流首部，因此被装饰实现仍拿到完整内容，整个过程**不会把上传内容全部缓存到内存**。读取类方法（`open`/`stat`/`exists`/`delete`）直接委托、不做额外处理。

## 扫描器 SPI

`CocoFileScanner` 是恶意软件检测的接入点，**框架本身不实现病毒扫描**。真实检测依赖外部引擎（如 ClamAV）及其持续更新的病毒库，框架无法在不引入外部依赖和特征库维护责任的前提下提供有效检测。默认实现 `NoOpCocoFileScanner` 不做任何检查。业务方接入外部引擎时，声明自己的 `CocoFileScanner` Bean，判定拒绝时抛 `CocoStorageException`、正常返回即放行：

```java
@Component
public class ClamAvFileScanner implements CocoFileScanner {

    @Override
    public void scan(CocoContentProbe probe) {
        // probe.probeBytes() 只是头部字节；完整扫描需要业务侧另行读取完整内容对接外部引擎
        if (isMalicious(probe)) {
            throw new CocoStorageException(CocoStorageErrorCode.SCAN_REJECTED, probe.key());
        }
    }
}
```

## 接入自定义存储（S3 SPI 示例骨架）

业务方自行引入对应 SDK，声明一个 `CocoObjectStorage` Bean 即可取代本地实现；`@ConditionalOnMissingBean(CocoObjectStorage.class)` 保证本地实现自动退位。该 Bean 同样会被校验装饰器包装，无需重复接入校验逻辑：

```java
@Bean
public CocoObjectStorage s3ObjectStorage(/* 业务自行注入 S3 客户端与桶配置 */) {
    return new CocoObjectStorage() {
        @Override
        public CocoObjectMetadata put(CocoObjectPutRequest request) {
            // 1. 读取 request.content() 流式上传到对象存储
            // 2. 计算 SHA-256、确定 contentType 与 size
            // 3. 返回 new CocoObjectMetadata(request.key(), size, contentType, sha256, Instant.now())
            throw new UnsupportedOperationException("接入具体 SDK");
        }

        @Override public CocoObjectResource open(String key) { /* ... */ throw new UnsupportedOperationException(); }
        @Override public CocoObjectMetadata stat(String key) { /* ... */ throw new UnsupportedOperationException(); }
        @Override public boolean exists(String key) { /* ... */ throw new UnsupportedOperationException(); }
        @Override public boolean delete(String key) { /* ... */ throw new UnsupportedOperationException(); }
    };
}
```

## 关键配置项

绑定前缀 `coco.storage`（对应 `CocoStorageProperties`）：

| 配置项 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `coco.storage.enabled` | `boolean` | `false` | 是否启用存储自动配置；关闭则不创建任何存储 Bean |
| `coco.storage.max-size-bytes` | `long` | `10485760`（10 MiB） | 允许的最大上传字节数，必须大于零 |
| `coco.storage.allowed-content-types` | `Set<String>` | 空集（不限制） | 允许的内容类型，支持 `*/*`、`type/*` 通配；为空表示不限制 |
| `coco.storage.allowed-extensions` | `Set<String>` | `jpg, jpeg, png, gif, webp, pdf, txt, csv` | 允许的扩展名白名单（安全默认，不含点号）；为空表示不限制 |
| `coco.storage.overwrite-policy` | `CocoStorageOverwritePolicy` | `REJECT` | 默认覆盖策略：`REJECT` 拒绝已存在对象，`REPLACE` 原子替换 |
| `coco.storage.local.root` | `Path` | 无 | 本地参考实现的存储根目录 |
| `coco.storage.local.orphan-grace-period` | `Duration` | `5m` | 不再被 manifest 引用的内部文件回收宽限期，不能为负 |
| `coco.storage.local.gc-interval` | `Duration` | `5m` | 后台孤儿回收间隔；`0` 表示仅在启动和关闭时回收 |
| `coco.storage.validation.enabled` | `boolean` | `true` | 是否启用上传内容校验装饰器 |
| `coco.storage.validation.probe-size` | `int` | `512` | 内容头部探测字节数，非正数回退默认值 |
| `coco.storage.validation.reject-dangerous-signatures` | `boolean` | `true` | 是否拒绝命中危险签名的内容 |
| `coco.storage.validation.require-signature-match` | `boolean` | `true` | 是否要求内容魔数与扩展名一致 |

默认扩展名白名单**有意排除**了以下类型：`docx`/`xlsx`/`pptx`（基于 ZIP 容器，魔数与普通压缩包完全相同，无法区分实际内容）、`zip`（任意内容容器，且存在解压炸弹风险）、`svg`（可内嵌 JavaScript，是 XSS 载体）。业务确需这些类型时，应在放开白名单的同时自行补充容器内容解析或渲染隔离措施。

## 边界注意事项

**1. 上传绕过了签名 / 加密 / 防重放链路。** 文件上传通常以 multipart 形式提交，而 Web 模块的请求体读取、签名校验、加解密和防重放是基于 body 缓存实现的，multipart 请求被排除在该缓存链路之外。因此走对象存储的上传接口不会经过这套 body 级安全校验，业务方需要自行为上传接口设计授权与防滥用措施。

**2. 框架不做病毒扫描。** `CocoFileScanner` 只是接入点，默认 `NoOpCocoFileScanner` 不做任何检查。魔数校验只能识别“伪装成文档的可执行文件”这类特征明显的内容，不等同于恶意软件检测。需要真正的病毒扫描必须自行接入 ClamAV 等外部引擎并维护特征库。

**3. 魔数校验存在固有盲区。**
- `txt`、`csv`、`json`、`xml`、`md` 等扩展名没有可靠魔数，不在签名表中，`matchesExtension` 对它们一律返回 `true`——这类文件实际上只受扩展名白名单约束，魔数校验不提供任何额外保护。
- ZIP 系格式（`docx`、`xlsx`、`pptx`、`zip`）共享完全相同的魔数，签名命中只能确认“这是一个 ZIP 容器”，无法证明它是声明的那种 Office 文档，也无法与普通 ZIP 压缩包区分。需要精确判定时必须解析容器内部结构。

**4. 本地实现的威胁模型有边界。** `LocalCocoObjectStorage` 防御 Web 输入和配置误指向链接路径，但不防御已经拥有存储根目录写权限的本地恶意进程。生产环境应结合操作系统层面的目录权限一起使用。

