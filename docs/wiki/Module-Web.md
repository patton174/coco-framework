# coco-web：Web 运行时

> 本页回答：`coco-features/coco-web`（框架里最大的模块，144 个主类）有哪些子能力、过滤器顺序、可替换的扩展点、自动配置注册、以及 `coco.web.*` 配置属性与默认值。
> 依据：`pom.xml`、`src/main/java` 与 `META-INF` 实读，内容对应仓库 `main` 分支；密钥映射只写键名与结构，不写任何密钥值。

## 1. 概览

| 项 | 内容 |
| --- | --- |
| 坐标 / 路径 | `io.github.patton174:coco-web`，`coco-features/coco-web` |
| 职责 | pom 描述为“统一响应与异常处理的 Web 功能实现”；[`AGENTS.md`](https://github.com/patton174/coco-framework/blob/main/AGENTS.md) 的范围更完整：Servlet Web 集成，含响应封装、异常响应处理、Trace、请求上下文、访问日志、签名、加密、进程内重放保护，以及显式选择的 JDBC 重放存储参考实现 |
| 依赖 | `coco-context`、`coco-exception`、`coco-i18n`、`coco-logging`、`coco-spring-boot-autoconfigure`；`spring-web`、`spring-webmvc`、`jackson-databind`、`jakarta.servlet-api`、`slf4j-api` |
| optional | `spring-boot-starter-data-redis`、`spring-boot-jdbc`、`spring-jdbc`（Redis / JDBC 重放存储才需要） |
| 测试 | `src/test` 22 个 `*Test` 类；测试依赖含 `h2` |
| Feature | `web`，运行期由 `@ConditionalOnCocoFeature(CocoFeature.WEB)` 控制；模块级没有总开关，各子能力有自己的 `enabled` |
| 包 | `io.github.coco.feature.web.*`（Java 包名带 `feature`，与 artifactId `coco-web` 不同） |

`CocoWebFeature` 是只有私有构造器的空标记类。

## 2. 请求处理链路与过滤器顺序

下表为各 `FilterRegistrationBean` 的 `setOrder` 实读值（数字越小越靠前）。`coco-security` 的过滤器也在这个序列里。

| 顺序 | Bean（`@ConditionalOnMissingBean(name=...)`） | 过滤器 | 默认是否注册 |
| --- | --- | --- | --- |
| `HIGHEST_PRECEDENCE` | `cocoRequestBodyCachingFilterRegistration` | `CocoRequestBodyCachingFilter` | 是（`coco.web.request-body.enabled`，缺省 true） |
| `HIGHEST_PRECEDENCE`（可由 `order` 调整） | `cocoSecurityHeadersFilterRegistration` | `CocoSecurityHeadersFilter` | 是（`coco.web.security-headers.enabled`，缺省 true） |
| `+1` | `cocoTraceFilterRegistration` | `CocoTraceFilter` | 是（`coco.web.trace.enabled`，缺省 true） |
| `+2` | `cocoReplayRequestShapeFilterRegistration` | `CocoReplayRequestShapeFilter` | 是（`coco.web.replay.enabled`，缺省 true） |
| `+3` | `cocoSignatureFilterRegistration` | `CocoSignatureFilter` | 是（`coco.web.signature.enabled`，缺省 true） |
| `+4` | `cocoEncryptionFilterRegistration` | `CocoEncryptionFilter` | 是（`coco.web.encryption.enabled`，缺省 true） |
| `+5` | `cocoReplayFilterRegistration`；`coco-security` 的 `cocoSecurityWebFilterRegistration` | `CocoReplayFilter`；`CocoSecurityWebFilter` | 是 |

签名、加密、重放的 `required` 默认都是 `false`：只有请求带了相应元数据才会校验，不会拒绝没有签名的请求。各过滤器仅在 `@ConditionalOnWebApplication(type = SERVLET)` 下注册。

签名、加密、重放三者各有一组 `matcher.required|ignored` 规则列表（默认空，类型 `CocoWebRequestMatchRule`，由 `CocoWebRequestMatcher` 判定），用来按路径等条件强制或排除校验。匹配语义取自 `DefaultCocoWebRequestMatcher` 与 `CocoWebRequestMatchRule` 源码：

- 一条规则含 `methods`（HTTP 方法集合，统一转大写，大小写无关）与 `path-patterns`（Spring `AntPathMatcher` 模式，缺少前导 `/` 时自动补上）；两者同时配置时必须同时匹配，任意一侧为空表示不限制该侧。
- 两侧都为空的规则会被丢弃；规则列表为空时永远不命中；列表中任一规则命中即视为命中。
- 匹配路径取请求 URI，去掉 context-path 之后参与匹配。
- `ignored` 命中的请求会跳过对应的签名 / 加密 / 重放过滤逻辑；`required` 命中的请求等同于该功能 `required=true`，即缺少元数据时拒绝请求。

另外有一个 Spring MVC 拦截器：`CocoPageInterceptor`（`web.page`），由 `cocoPageMvcConfigurer` 注册，把 `page` / `size` / `sort` 查询参数写入 `CocoPageContextHolder`，`afterCompletion` 里清除，防止线程池复用泄漏。

## 3. 自动配置与注册

`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`：

| 顺序 | 自动配置 | 说明 |
| --- | --- | --- |
| 1 | `io.github.coco.feature.web.CocoWebJdbcReplayAutoConfiguration` | `before = CocoWebAutoConfiguration`、`after = JdbcTemplateAutoConfiguration`；条件：`@ConditionalOnCocoFeature(WEB)`、`JdbcOperations` 类存在且单一候选、`coco.web.replay.store-type=jdbc`；Bean `JdbcCocoReplayStore`（`destroyMethod=close`，`@ConditionalOnMissingBean(CocoReplayStore)`） |
| 2 | `io.github.coco.feature.web.CocoWebRedisReplayAutoConfiguration` | `before = CocoWebAutoConfiguration`；条件：`store-type=redis` 且 `StringRedisTemplate` 存在；Bean `redisCocoReplayStore` |
| 3 | `io.github.coco.feature.web.CocoWebAutoConfiguration` | `before = CocoCommonAutoConfiguration`；`@EnableConfigurationProperties({CocoWebProperties, CocoCommonProperties})`；`@Import` 9 个内部配置 |
| 4 | `io.github.coco.feature.web.cors.CocoCorsAutoConfiguration` | `coco.web.cors.enabled=true` 才生效，默认关闭 |
| 5 | `io.github.coco.feature.web.page.CocoPageAutoConfiguration` | `coco.web.page.enabled` 缺省 true，仅 Servlet |
| 6 | `io.github.coco.feature.web.headers.CocoSecurityHeadersAutoConfiguration` | `coco.web.security-headers.enabled` 缺省 true，需 `Filter` 类 |

`CocoWebAutoConfiguration` `@Import` 的 9 个内部配置（同包，不在 imports 文件中）：

| 配置类 | 主要 Bean |
| --- | --- |
| `CocoWebI18nAutoConfiguration` | `cocoWebLocaleResolver`（`CocoLocaleResolver`）、`cocoWebMessageBundleRegistrar`（资源包 `coco-feature-web-messages`） |
| `CocoWebResponseAutoConfiguration` | `CocoSystemCodeProvider`、`CocoResponseBodyFactory`、`CocoResponseWrapAdvice`（条件 `coco.web.response-wrap.enabled`） |
| `CocoWebExceptionAutoConfiguration` | `CocoExceptionHttpStatusResolver`、`CocoWebExceptionHandler`、`CocoFilterExceptionResponseWriter`、`CocoWebErrorResponseWriter` |
| `CocoWebRequestBodyAutoConfiguration` | `CocoRequestBodyResolver`、请求体缓存过滤器注册 |
| `CocoWebContextAutoConfiguration` | `CocoClientIpResolver`、`CocoBrowserFingerprintResolver`、`CocoRequestHeaderResolver`、`CocoRequestCookieResolver`、`CocoPayloadParameterResolver`、`CocoRequestParameterResolver`、`CocoWebRequestTargetResolver`、`CocoWebRequestSecurityInputResolver`、`CocoWebRequestSecurityMetadataResolver`、`CocoWebRequestContextResolver`、`CocoWebRequestCanonicalizer`、`CocoWebRequestMatcher` |
| `CocoWebTraceAutoConfiguration` | `CocoTraceIdValidator`、Trace 过滤器注册（注入 `CocoAccessLogRecorder`） |
| `CocoWebSignatureAutoConfiguration` | `CocoSignatureSecretResolver`、`CocoSignatureVerifier`、签名过滤器注册 |
| `CocoWebEncryptionAutoConfiguration` | `CocoEncryptionKeyResolver`、`CocoRequestDecryptor`、加密过滤器注册 |
| `CocoWebReplayAutoConfiguration` | `CocoReplayKeyResolver`、`CocoReplayStore`（`store-type` 缺省 `in-memory`）、两个重放过滤器注册 |

上面带 `@ConditionalOnMissingBean` 的 Bean 都可以用同类型 Bean 替换。

## 4. 包与公开类型

> 下表只列公开类型。`CocoReplayRequestShape`、`CocoReplayCleanupLifecycle`、`CocoEncryptionCodecs` 是包私有类，不属于公开 API。

| 包（`io.github.coco.feature.web.` 之后） | 主要类型 |
| --- | --- |
| `response` | `CocoApiResponse<T>`（record：`success`、`code`、`message`、`data`、`traceId`、`path`，后两者 `@JsonInclude(NON_NULL)`；`success(...)` / `error(...)` 静态工厂）、`CocoResponseWrapAdvice`（`@RestControllerAdvice`，`ResponseBodyAdvice<Object>`）、`CocoIgnoreResponseWrap`（注解，`@Target({TYPE, METHOD})`）、`CocoResponseBodyFactory` / `DefaultCocoResponseBodyFactory`、`CocoResponseMetadata`、`CocoResponseMetadataMode`（`NONE`、`COOKIE`、`TRACE`、`DEBUG`）、`CocoResponsePayload`、`CocoSystemCodeProvider`（`success()`、`invalidArgument()`、`unauthorized()`、`forbidden()`、`notFound()`、`conflict()`、`internalError()`）/ `CocoSystemCodes`、`CocoResponseProperties`、`CocoResponseWrapProperties` |
| `exception` | `CocoWebExceptionHandler`（`@RestControllerAdvice`：处理 `CocoException`、参数类异常、`NoHandlerFoundException`、`HttpRequestMethodNotSupportedException`、兜底 `Exception`）、`CocoExceptionHttpStatusResolver` / `DefaultCocoExceptionHttpStatusResolver`、`CocoWebErrorResponseWriter`、`CocoFilterExceptionResponseWriter`、`CocoFieldError`、`CocoPayloadTooLargeException`（继承 `CocoRequestException`） |
| `trace` | `CocoTraceFilter`、`CocoTraceIdValidator` / `DefaultCocoTraceIdValidator`、`CocoTraceProperties` |
| `i18n` | `CocoWebLocaleResolver` |
| `body` | `CocoRequestBodyCachingFilter`、`CocoCachedBodyHttpServletRequest`、`CocoCachedRequestBody`、`CocoRequestBodyResolver` / `DefaultCocoRequestBodyResolver`、`CocoRequestBodyCachingMode`（`SECURITY_HEADERS`、`ALWAYS`）、`CocoRequestBodyStage`、`CocoRequestBodyMetadata`、`CocoResolvedRequestBody`、`CocoRequestBodyProperties` |
| `context` | `CocoWebRequestContextResolver` / `DefaultCocoWebRequestContextResolver`、`CocoWebRequestContextPhase`（`TRANSPORT_CAPTURED`、`SIGNATURE_VERIFIED`、`DECRYPTED`、`REPLAY_VERIFIED`）、`CocoClientIpResolver`（+`CocoClientIpResolution`、`CocoClientIpSource`）、`CocoBrowserFingerprintResolver`（+`CocoBrowserFingerprint`）、`CocoRequestHeaderResolver`、`CocoRequestCookieResolver`、`CocoRequestParameterResolver`、`CocoSensitiveRequestHeaderContributor`、`CocoWebRequestCanonicalizer`（+`CocoWebRequestCanonicalForm`、`CocoWebRequestCanonicalizationContext`、`...Purpose`）、`CocoWebRequestMatcher`（+`CocoWebRequestMatchRule`）、`CocoWebRequestSnapshot`、`CocoWebContextProperties`、`CocoWebParameterProperties`、`CocoWebRequestMatcherProperties`、`CocoWebRequestCanonicalizationProperties` 等，各有 `Default*` 实现 |
| `context.payload` | `CocoPayloadParameterResolver` / `DefaultCocoPayloadParameterResolver`、`CocoWebPayloadParseResult`、`CocoWebPayloadParseStatus`、`CocoPayloadParameterProperties` |
| `context.target` | `CocoWebRequestTargetResolver` / `DefaultCocoWebRequestTargetResolver`、`CocoWebRequestTarget`、`CocoWebRequestTargetResolution`、`CocoWebRequestTargetSource`、`CocoWebRequestTargetProperties` |
| `request.metadata` | `CocoWebRequestSecurityMetadataResolver`、`CocoWebRequestSecurityInputResolver`（各有 `Default*`）、`CocoWebRequestSecurityMetadata`、`CocoWebRequestSecurityInput`、`CocoWebSecurityMetadataSource`（含 `HEADER`） |
| `signature` | `CocoSignatureFilter`、`CocoSignatureVerifier` / `HmacSha256CocoSignatureVerifier`、`CocoSignatureSecretResolver` / `PropertiesCocoSignatureSecretResolver`、`CocoSignatureRequest`、`CocoSignatureSecret`、`CocoSignatureVerificationContext`、`CocoSignatureProperties` |
| `encryption` | `CocoEncryptionFilter`、`CocoRequestDecryptor` / `AesGcmCocoRequestDecryptor`、`CocoEncryptionKeyResolver` / `PropertiesCocoEncryptionKeyResolver`、`CocoEncryptionKey`、`CocoEncryptedRequest`、`CocoRequestDecryptionContext`、`CocoRequestDecryptException`、`CocoEncryptionAssociatedData`、`CocoCryptoTextEncoding`、`CocoEncryptionProperties` |
| `replay` | `CocoReplayFilter`、`CocoReplayRequestShapeFilter`、`CocoReplayStore`（唯一方法 `boolean reserve(CocoReplayKey key, Instant expiresAt)`）、`InMemoryCocoReplayStore` / `JdbcCocoReplayStore` / `RedisCocoReplayStore`、`CocoReplayKeyResolver` / `DefaultCocoReplayKeyResolver`、`CocoReplayKey`、`CocoReplayStoreType`（`IN_MEMORY`、`JDBC`、`REDIS`）、`CocoReplayProperties` |
| `cors` | `CocoCorsAutoConfiguration`、`CocoCorsProperties` |
| `headers` | `CocoSecurityHeadersAutoConfiguration`、`CocoSecurityHeadersFilter`、`CocoSecurityHeadersProperties` |
| `page` | `CocoPageAutoConfiguration`、`CocoPageInterceptor`、`CocoPageProperties` |
| `accesslog` | `CocoAccessLogCaptureProperties`（仅 `enabled`） |

统一响应由 `CocoResponseWrapAdvice` 把正常返回值包成 `CocoApiResponse`。`String` 转换器场景下会手工序列化成 JSON 字符串。已经是 `CocoApiResponse` 的返回值不会被二次包装；方法或类上标注 `@CocoIgnoreResponseWrap` 可跳过；`coco.web.response-wrap.max-body-bytes` ≥ 0 时，已知长度超过该值的响应体也会跳过包装。设计背景见规格 `coco-support/coco-document/superpowers/specs/2026-07-05-coco-web-response-wrap-design.md`。

## 5. 扩展点

下列接口都带 `Default*` 或 `Properties*` 默认实现，并通过 `@ConditionalOnMissingBean` 暴露，业务声明同类型 Bean 即可替换。

| 想替换的行为 | 接口 / Bean 类型 |
| --- | --- |
| 系统响应码 | `CocoSystemCodeProvider` |
| 响应体结构 | `CocoResponseBodyFactory` |
| 异常 → HTTP 状态 | `CocoExceptionHttpStatusResolver` |
| TraceId 合法性校验 | `CocoTraceIdValidator` |
| 请求体读取与解码 | `CocoRequestBodyResolver` |
| 客户端 IP、浏览器指纹、Header / Cookie / 参数、请求目标解析 | `CocoClientIpResolver`、`CocoBrowserFingerprintResolver`、`CocoRequestHeaderResolver`、`CocoRequestCookieResolver`、`CocoRequestParameterResolver`、`CocoWebRequestTargetResolver`、`CocoPayloadParameterResolver` |
| 请求上下文构建 / 规范化 / 匹配 | `CocoWebRequestContextResolver`、`CocoWebRequestCanonicalizer`、`CocoWebRequestMatcher` |
| 安全元数据来源 | `CocoWebRequestSecurityInputResolver`、`CocoWebRequestSecurityMetadataResolver` |
| 签名密钥 / 算法 | `CocoSignatureSecretResolver`、`CocoSignatureVerifier` |
| 解密密钥 / 算法 | `CocoEncryptionKeyResolver`、`CocoRequestDecryptor` |
| 重放键 / 存储 | `CocoReplayKeyResolver`、`CocoReplayStore`（也可以按 `store-type` 选 JDBC / Redis 实现） |
| 敏感请求头补充 | `CocoSensitiveRequestHeaderContributor`（函数式接口，`coco-idempotency` 用它登记 `Idempotency-Key` 头） |
| 过滤器自身 | 声明同名 `FilterRegistrationBean` Bean（名称见 §2 表） |

> 签名 / 加密密钥属于敏感配置：`coco.web.signature.secrets`、`coco.web.encryption.keys` 是 `Map<String, String>`，默认空。文档与示例里只写键名与结构，不要提交真实密钥；生产建议自定义 `CocoSignatureSecretResolver` / `CocoEncryptionKeyResolver` 从密钥管理系统读取。

## 6. 配置属性

主入口 `CocoWebProperties`（前缀 `coco.web`）的子树：`trace`、`response`、`response-wrap`、`access-log`、`request-body`、`signature`、`encryption`、`replay`、`context`。`cors`、`security-headers`、`page` 是**独立的** `@ConfigurationProperties` 前缀。模块带有 `META-INF/additional-spring-configuration-metadata.json`。

### 6.1 响应、Trace、访问日志

| 前缀 | 键 → 默认值 |
| --- | --- |
| `coco.web.response` | `metadata-mode=NONE`（`NONE` / `COOKIE` / `TRACE` / `DEBUG`） |
| `coco.web.response-wrap` | `enabled=true`、`success-message-code="coco.web.response.success"`、`max-body-bytes=-1`（负数表示不限制） |
| `coco.web.trace` | `enabled=true`、`header-name="X-Trace-Id"`、`mdc-key="traceId"`、`response-header-enabled=true`、`response-cookie-enabled=false`、`cookie-name="COCO_TRACE_ID"`、`cookie-path="/"`、`cookie-max-age=-1`、`cookie-http-only=false`、`cookie-secure=false`、`cookie-same-site="Lax"`、`max-length=128`、`allowed-pattern="[A-Za-z0-9._:-]+"` |
| `coco.web.access-log` | `enabled=true`（仅控制 Web 侧采集；输出格式见 `coco.logging.access-log.*`，在 [基础模块](Module-Foundation)） |

### 6.2 请求体缓存

| 键 | 默认值 |
| --- | --- |
| `coco.web.request-body.enabled` | `true` |
| `...mode` | `SECURITY_HEADERS`（另有 `ALWAYS`） |
| `...max-cache-bytes` | `1048576`（1 MiB） |
| `...cache-methods` | `POST`、`PUT`、`PATCH`、`DELETE` |
| `...trigger-header-names` | `content-md5`、`x-coco-sign`、`x-coco-signature`、`x-coco-encrypted` |
| `...included-content-types` | `application/json`、`application/*+json`、`text/plain`、`application/xml`、`text/xml` |
| `...excluded-content-type-prefixes` | `multipart/`、`application/octet-stream` |

### 6.3 请求上下文（`coco.web.context.*`）

| 键 | 默认值 |
| --- | --- |
| `client-ip-header-names` | `Forwarded`、`X-Forwarded-For`、`X-Real-IP`、`Proxy-Client-IP`、`WL-Proxy-Client-IP`、`HTTP_CLIENT_IP`、`HTTP_X_FORWARDED_FOR` |
| `trusted-proxy-cidrs` | 空 |
| `include-headers` | `true` |
| `included-header-names` | `host`、`content-type`、`user-agent`、`accept`、`accept-language`、`referer`、`origin` |
| `masked-header-names` | `authorization`、`cookie`、`set-cookie`、`x-api-key`、`x-auth-token` |
| `security-header-names` | `content-md5`、`content-type`、`x-coco-app-id`、`x-coco-timestamp`、`x-coco-nonce`、`x-coco-sign`、`x-coco-signature`、`x-coco-encrypted`、`x-coco-key-id`、`x-coco-iv`、`x-coco-algorithm`、`x-coco-sign-algorithm` |
| `canonical-header-names` | `content-md5`、`content-type`、`x-coco-app-id`、`x-coco-timestamp`、`x-coco-nonce`、`x-coco-key-id`、`x-coco-iv`、`x-coco-algorithm`、`x-coco-sign-algorithm` |
| `fingerprint-header-names` | `user-agent`、`accept`、`accept-language`、`dnt`、`sec-ch-ua`、`sec-ch-ua-mobile`、`sec-ch-ua-platform`、`sec-ch-ua-platform-version`、`sec-ch-ua-arch`、`sec-ch-ua-bitness`、`sec-ch-ua-model`、`sec-ch-ua-full-version-list` |
| `max-header-value-length` | `256` |
| `included-cookie-names` / `canonical-cookie-names` | 空 |
| `masked-cookie-names` | `jsessionid`、`session`、`sessionid`、`token`、`access_token`、`refresh_token` |
| `max-cookie-value-length` | `128` |
| `parameter.include-parameters` / `parameter.max-parameter-value-length` | `true` / `256` |
| `parameter.masked-parameter-names` | `password`、`passwd`、`pwd`、`secret`、`token`、`access_token`、`refresh_token`、`authorization` |
| `parameter.payload.enabled` | `true`；`included-content-types`：`application/json`、`application/*+json`、`application/x-www-form-urlencoded`；`max-json-depth=8`；`max-parameter-count=128` |
| `target.proto-header-names` | `X-Forwarded-Proto`、`X-Forwarded-Protocol`、`X-Forwarded-Ssl`、`Front-End-Https` |
| `target.host-header-names` / `port-header-names` / `prefix-header-names` | `X-Forwarded-Host`、`X-Original-Host` / `X-Forwarded-Port` / `X-Forwarded-Prefix`、`X-Forwarded-PathBase`、`X-Original-Prefix` |
| `target.apply-forwarded-prefix` | `true` |
| `canonicalization.version` | `"coco-v2"`；`include-version=false`、`include-purpose=false`、`include-method=true`、`include-path=true`、`include-query-string=true`、`include-headers=true`、`include-cookies=false`、`include-browser-fingerprint=false`、`include-browser-fingerprint-signals=false`、`include-parameters=true`、`include-parameter-sources=true`、`include-body-sha256=true`、`include-body-length=true`、`sort-parameter-values=true`、`parameter-value-separator=","` |

`trusted-proxy-cidrs` 默认为空：不信任任何代理，客户端 IP 的转发头解析是否生效取决于该设置，部署在网关后面时需要显式配置。

### 6.4 签名、加密、重放

| 前缀 | 键 → 默认值 |
| --- | --- |
| `coco.web.signature` | `enabled=true`、`required=false`、`timestamp-required=true`、`timestamp-validation-enabled=true`、`max-clock-skew-seconds=300`、`metadata-source=HEADER`、`default-algorithm="HMAC-SHA256"`、`secrets`（Map，默认空）、`matcher.required|ignored`（规则列表，默认空）。头名：`X-Coco-App-Id`、`X-Coco-Key-Id`、`X-Coco-Timestamp`、`X-Coco-Nonce`、`X-Coco-Sign`（回退 `X-Coco-Signature`）、`X-Coco-Sign-Algorithm`；对应参数名：`appId`、`keyId`、`timestamp`、`nonce`、`sign`（回退 `signature`）、`signAlgorithm` |
| `coco.web.encryption` | `enabled=true`、`required=false`、`metadata-source=HEADER`、`default-algorithm="AES-GCM"`、`gcm-tag-length-bits=128`、`key-encoding` / `iv-encoding` / `payload-encoding=BASE64`、`keys`（Map，默认空）、`matcher.*`。头名：`X-Coco-Encrypted`、`X-Coco-App-Id`、`X-Coco-Key-Id`、`X-Coco-IV`、`X-Coco-Algorithm`；参数名：`encrypted`、`appId`、`keyId`、`iv`、`algorithm`、`payload` |
| `coco.web.replay` | `enabled=true`、`required=false`、`protect-signed-requests=true`、`protect-encrypted-requests=true`、`include-method=true`、`include-path=true`、`store-type=IN_MEMORY`、`metadata-source=HEADER`、`ttl-seconds=300`、`cleanup-interval-seconds=60`、`max-clock-skew-seconds=300`、`jdbc.table-name="coco_replay_key"`、`redis.key-prefix="coco:replay:"`、`matcher.*`；元数据头与参数名同签名（`X-Coco-App-Id` / `X-Coco-Key-Id` / `X-Coco-Timestamp` / `X-Coco-Nonce`） |

重放存储的取舍：默认 `IN_MEMORY` 是进程内存储，多实例部署需要换成共享存储。`JDBC` 是显式选择的参考实现（见规格 `2026-07-10-coco-jdbc-replay-store.md`）；`REDIS` 使用 Lua 脚本。`redis` 子树另有 `template-bean-name`（指定要使用的 `StringRedisTemplate` Bean 名，默认未设置）。

### 6.5 CORS、安全响应头、分页

| 前缀 | 键 → 默认值 |
| --- | --- |
| `coco.web.cors` | `enabled=false`、`allowed-origins=["*"]`、`allowed-methods=[GET, POST, PUT, DELETE, OPTIONS]`、`allowed-headers=["*"]`、`exposed-headers=[]`、`allow-credentials=false`、`max-age=1800` |
| `coco.web.security-headers` | `enabled=true`、`order=Ordered.HIGHEST_PRECEDENCE`、`content-type-options="nosniff"`、`frame-options="DENY"`、`referrer-policy="strict-origin-when-cross-origin"`；`content-security-policy`、`permissions-policy`、`strict-transport-security` 默认不设置 |
| `coco.web.page` | `enabled=true`、`page-parameter-name="page"`、`size-parameter-name="size"`、`default-page=1`、`default-size=20`、`max-size=100`、`sort-parameter-name="sort"` |

分页拦截器对 `page` / `size` 做边界校验（`page < 1` 与 `size` 超过 `max-size` 的处理见 `CocoPageInterceptor` 源码），`sort` 参数形如 `字段,desc`，解析为 `CocoSortOrder` 列表（缺省升序）。与 `coco-mybatis-plus` 的配合见 [数据与安全类 Feature](Module-Features-Data-And-Security)。

## 7. 测试

`coco-features/coco-web/src/test` 下 22 个 `*Test`。运行单模块测试：`mvn -pl :coco-web -am test`（需要 JDK 21，见 [快速开始](Getting-Started)）。

## 8. 官网与规格链接

- 官网用户文档：[web-runtime](https://github.com/patton174/coco-framework/blob/main/website/docs/features/web-runtime.md)、[request-security](https://github.com/patton174/coco-framework/blob/main/website/docs/features/request-security.md)、[pagination](https://github.com/patton174/coco-framework/blob/main/website/docs/features/pagination.md)
- 设计规格：`coco-support/coco-document/superpowers/specs/2026-07-05-coco-web-response-wrap-design.md`、`2026-07-10-coco-jdbc-replay-store.md`、`2026-07-08-coco-web-server-framework-boundary.md`

## 9. 相关页面

- [模块参考总览](Module-Overview)
- [基础模块：coco-foundation](Module-Foundation)
- [Spring 组合层](Module-Spring)
- [数据与安全类 Feature](Module-Features-Data-And-Security)
- [基础设施类 Feature](Module-Features-Infrastructure)
- [架构总览](Architecture)
- [约定与规范](Conventions)
