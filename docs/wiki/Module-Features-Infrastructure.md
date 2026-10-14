# 基础设施类 Feature：rate-limit / idempotency / lock / cache / captcha / scheduling / storage / messaging / notification

> 本页回答：`coco-features/` 里 9 个基础设施类模块各自提供什么 SPI、默认实现、自动配置条件与配置属性。
> 依据：各模块 `pom.xml`、`src/main/java`、`META-INF` 实读，内容对应仓库 `main` 分支。这些模块的 `@since` 与官网文档标注的版本可能比最近发布的稳定版新，**所在版本以 GitHub Releases 与 [官网发布说明](https://github.com/patton174/coco-framework/tree/main/website/docs/releases) 为准**，本页不断言发布版本。

## 0. 总览

| Feature id | 模块 | 在 starter 里？ | Maven 依赖中的 Coco 模块 | `enabled` 默认 | 运行期用 `@ConditionalOnCocoFeature`？ | 包根 |
| --- | --- | --- | --- | --- | --- | --- |
| `rate-limit` | `coco-rate-limit` | **否**（构建期补加） | `coco-spring-boot-autoconfigure`、`coco-context`、`coco-exception`、`coco-i18n` | `false` | 是 | `io.github.coco.feature.ratelimit` |
| `idempotency` | `coco-idempotency` | **否**（构建期补加） | `coco-web`、`coco-i18n` | `false` | 是 | `io.github.coco.feature.idempotency` |
| `lock` | `coco-lock` | 是 | `coco-exception`、`coco-i18n` | `false` | 否 | `io.github.coco.feature.lock` |
| `cache` | `coco-cache` | 是 | `coco-exception`、`coco-i18n` | `false` | 否 | `io.github.coco.cache` |
| `captcha` | `coco-captcha` | 是 | `coco-exception`、`coco-i18n` | `false` | 否 | `io.github.coco.captcha` |
| `scheduling` | `coco-scheduling` | 是 | `coco-context`、`coco-i18n` | `true`（`matchIfMissing`） | 否 | `io.github.coco.scheduling` |
| `storage` | `coco-storage` | 是 | `coco-exception`、`coco-i18n` | `false` | 否 | `io.github.coco.storage` |
| `messaging` | `coco-messaging` | 是 | `coco-context`、`coco-i18n` | `true`（`matchIfMissing`） | 否 | `io.github.coco.messaging` |
| `notification` | `coco-notification` | 是 | `coco-exception`、`coco-i18n` | `false` | 否 | `io.github.coco.notification` |

读表要点：

- **包根不统一**：`rate-limit`、`idempotency`、`lock` 在 `io.github.coco.feature.*` 下，其余六个在 `io.github.coco.<name>` 下。推荐哪种风格，设计意图未见文档说明，行为以各模块 `src/main/java` 的包路径为准；新增模块时与最近的同类模块保持一致，并在 PR 描述里说明（见 [约定与规范](Conventions)）。
- **`@ConditionalOnCocoFeature` 的缺失**：只有 rate-limit 与 idempotency 在自动配置上使用它；其余 7 个靠各自的 `enabled` 属性开关。因此对这 7 个模块，`coco.features.disabled` 在构建期影响依赖装配、裁剪与清单，运行期开关靠各自的 `coco.<name>.enabled`。设计意图未见文档说明，行为以各模块自动配置类源码为准，详见 [Feature 机制](Feature-System)。
- 默认关闭的模块（`enabled=false`）需要显式 `coco.<name>.enabled=true` 才会装配；`scheduling` 与 `messaging` 默认开启。
- **只有 Servlet 应用**才会注册 rate-limit 与 idempotency 的过滤器 / 拦截器。

### 0.1 Redis 存储“三件套”模式

lock、cache、captcha 各有三个自动配置：`Coco<X>RedisAutoConfiguration`（`before` 主配置，条件：`enabled` + `store-type=<redis|two-level>` + `StringRedisTemplate` 类存在）、`Coco<X>RedisMissingDependencyAutoConfiguration`（当 Redis 类缺失却选了 Redis 存储时 fail-fast）、主配置。rate-limit 与 idempotency 只有 Redis 配置 + 主配置（没有 MissingDependency 配置）。默认存储是进程内（`IN_MEMORY` / `LOCAL`），不适合多实例。

Redis 实现使用的 `StringRedisTemplate`：优先取 `redis.template-bean-name` 指定的 Bean（不存在或类型不对会抛 `BeanCreationException`）；未指定时要求容器里只有一个 `StringRedisTemplate`，或只有一个 `@Primary`，否则启动失败。`coco-web` 的重放存储以及 rate-limit、idempotency、lock、cache、captcha 的 Redis 自动配置都带有签名一致的 `resolve(...)` 辅助方法，属性里也都有同名的 `redis.template-bean-name`。

### 0.2 `store-type` 的写法

条件注解里的 `havingValue` 是小写连字符字面量：`in-memory`、`redis`、`two-level`。这些模块的枚举（`IN_MEMORY`、`REDIS`、`TWO_LEVEL`）绑定宽松，但 `@ConditionalOnProperty` 不走枚举绑定：Spring Boot 的 [`OnPropertyCondition`](https://github.com/spring-projects/spring-boot/blob/main/core/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/condition/OnPropertyCondition.java) 用 `equalsIgnoreCase` 比较属性的原始字符串。结果是：

- 忽略大小写：`REDIS`、`Redis` 都能匹配 `redis`；`IN-MEMORY` 能匹配 `in-memory`。
- 不会把下划线换成连字符：`IN_MEMORY`、`TWO_LEVEL` 这类枚举常量写法**不会**匹配 `in-memory`、`two-level`，对应的条件 Bean 不会创建。
- 这些模块的源码里没有额外的归一化。

**配置文件里请按小写连字符书写。**

### 0.3 消息资源包

| 模块 | 资源包（`src/main/resources`） |
| --- | --- |
| rate-limit | `coco-rate-limit-messages`（默认、`_en_US`、`_zh_CN`） |
| idempotency | `coco-idempotency-messages`（默认、`_zh_CN`） |
| lock | `coco-lock-messages`（默认、`_zh_CN`） |
| scheduling | `coco-scheduling-messages`（默认、`_zh_CN`） |
| storage | `coco-storage-messages`（默认、`_zh_CN`） |
| messaging | `coco-messaging-messages`（默认、`_en`） |
| notification | `coco-notification-messages`（默认、`_en`） |
| captcha | `coco-captcha-messages`（默认、`_en`） |
| cache | **没有**消息资源，自动配置里也没有 `CocoMessageBundleRegistrar` |

> 资源包的语言后缀并不统一（`_zh_CN` / `_en_US` / `_en`）。是否需要补齐，设计意图未见文档说明，行为以各模块 `src/main/resources` 为准。

## 1. coco-rate-limit

**坐标与路径**：`coco-features/coco-rate-limit`。**职责**：可选的 Servlet 限流，key 与原子存储契约可替换（pom）。依赖另有 `spring-webmvc`、`jackson-databind`、`jakarta.servlet-api`；`spotbugs-annotations`（provided）；Redis 为 optional。

**触发方式**：两条路径。① 过滤器 `CocoRateLimitFilter`：按 `routes[].matcher` 匹配请求；② MVC 拦截器 `CocoRateLimitMvcInterceptor`：处理方法 / 类上的 `@CocoRateLimited("路由 id")`，路由 id 必须在 `routes` 中声明，否则抛 `IllegalStateException`（`@CocoRateLimited references an unknown route`）。

| 类型 | 说明 |
| --- | --- |
| `CocoRateLimited`（注解，`@Target({TYPE, METHOD})`） | `value()` / `route()` 互为别名 |
| `CocoRateLimitKeyResolver`（接口）/ `DefaultCocoRateLimitKeyResolver` | `CocoRateLimitKey resolve(HttpServletRequest, CocoRateLimitRoute)`；默认实现只在 remote address 精确匹配 `trusted-proxy.remote-addresses` 时才解析 `X-Forwarded-For` |
| `CocoRateLimitRouteMatcher`（接口）/ `DefaultCocoRateLimitRouteMatcher` | 按请求或路由 id 找路由 |
| `CocoRateLimitStore`（接口）/ `InMemoryCocoRateLimitStore` / `RedisCocoRateLimitStore` | `CocoRateLimitDecision acquire(CocoRateLimitPermit)`，原子判定 |
| `CocoRateLimitAlgorithm`（枚举） | `FIXED_WINDOW`、`SLIDING_WINDOW`、`TOKEN_BUCKET` |
| `CocoRateLimitRoute` | 配置路由：`id`、`matcher`（`CocoRateLimitRequestMatchRule`）、`limit`（默认 100）、`window-seconds`（默认 60）、`algorithm`（默认 `FIXED_WINDOW`） |
| `CocoRateLimitRequestHandler`、`CocoRateLimitResponseWriter`、`CocoRateLimitFilter`、`CocoRateLimitMvcInterceptor` | 具体类（final），其中 handler 与 writer 是带 `@ConditionalOnMissingBean` 的 Bean |
| `CocoRateLimitDecision`、`CocoRateLimitPermit`、`CocoRateLimitKey`、`CocoRateLimitStoreType`（`IN_MEMORY`、`REDIS`）、`CocoRateLimitErrorCode` | 模型与错误码 |

**自动配置**：`CocoRateLimitRedisAutoConfiguration`、`CocoRateLimitAutoConfiguration`（`imports` 中顺序如此）。主配置条件：`@ConditionalOnCocoFeature(RATE_LIMIT)` + `coco.rate-limit.enabled=true`（无 `matchIfMissing`）。Bean：`cocoRateLimitMessageBundleRegistrar`、`cocoRateLimitClock`（可替换）、`CocoRateLimitKeyResolver`、`CocoRateLimitStore`（`store-type` 缺省 `in-memory`，`destroyMethod=close`）、`CocoRateLimitRouteMatcher`（Servlet）、`CocoRateLimitResponseWriter`、`CocoRateLimitRequestHandler`、`cocoRateLimitFilterRegistration`、`cocoRateLimitMvcConfigurer`。

**配置属性（`coco.rate-limit`）**：

| 键 | 默认值 |
| --- | --- |
| `enabled` | `false` |
| `store-type` | `IN_MEMORY`（另有 `REDIS`） |
| `routes` | 空列表（元素字段见上） |
| `in-memory.max-entries` | `10000` |
| `in-memory.cleanup-interval-seconds` | `60` |
| `redis.key-prefix` | `"coco:rate-limit:"` |
| `redis.template-bean-name` | 未设置 |
| `filter.excluded-path-patterns` | `/actuator`、`/actuator/**`、`/health`、`/health/**` |
| `trusted-proxy.remote-addresses` | 空（安全默认值：不读取任何转发头） |

**依赖 / 测试**：见总览；5 个 `*Test`。**用户文档**：[rate-limit](https://github.com/patton174/coco-framework/blob/main/website/docs/features/rate-limit.md)。

## 2. coco-idempotency

**坐标与路径**：`coco-features/coco-idempotency`。**职责**：可选的 Servlet 写请求幂等保护，租约存储可替换（pom）。依赖 `coco-web`（Feature 依赖也是 `web`）。

**语义**（[`module-layout.md`](https://github.com/patton174/coco-framework/blob/main/coco-support/coco-document/architecture/module-layout.md)）：不缓存、不回放首次响应；仅正常完成的 `2xx/3xx` 保留租约到 TTL，异常以及所有 `4xx/5xx` 都释放租约，允许安全重试。

**触发方式**：`CocoIdempotencyMvcInterceptor` 只处理标注了 `@CocoIdempotent`（方法或类）的 `HandlerMethod`，且请求方法在 `allowed-methods` 中。获取租约失败时分别写出 `DUPLICATE`、`UNAVAILABLE` 或 `INVALID_KEY` 错误（`CocoIdempotencyErrorCode`）。拦截器顺序 `HIGHEST_PRECEDENCE + 10`（源码注释：限流先执行，幂等随后获得租约）。

| 类型 | 说明 |
| --- | --- |
| `CocoIdempotent`（注解） | `namespace()`（默认空）、`ttlSeconds()`（默认 `-1`，使用全局 TTL） |
| `CocoIdempotencyKeyResolver` / `DefaultCocoIdempotencyKeyResolver` | `CocoIdempotencyKey resolve(request, handlerMethod, intent)`；非法 key 抛 `CocoIdempotencyKeyException` |
| `CocoIdempotencyOperationResolver` / `DefaultCocoIdempotencyOperationResolver` | `String resolve(request, handlerMethod)`，决定“同一个操作”的标识 |
| `CocoIdempotencyStore` / `InMemoryCocoIdempotencyStore` / `RedisCocoIdempotencyStore` | `AcquireResult acquire(CocoIdempotencyLease)`、`void release(CocoIdempotencyLease)` |
| `CocoIdempotencyResponseWriter` / `DefaultCocoIdempotencyResponseWriter` | 错误响应写出，默认复用 `coco-web` 的 `CocoWebErrorResponseWriter` |
| `CocoIdempotencyLease`（record）、`CocoIdempotencyKey`、`CocoIdempotencyKeyException`、`CocoIdempotencyStoreType`、`CocoIdempotencyErrorCode` | 模型、枚举与错误码 |

**自动配置**：`CocoIdempotencyRedisAutoConfiguration`、`CocoIdempotencyAutoConfiguration`。主配置：`@AutoConfigureBefore(CocoWebContextAutoConfiguration)`、`@ConditionalOnCocoFeature(IDEMPOTENCY)`、`coco.idempotency.enabled=true`、Servlet。Bean：`cocoIdempotencyMessageBundleRegistrar`、`cocoIdempotencyClock`、`CocoIdempotencyOperationResolver`、`CocoIdempotencyKeyResolver`、`CocoIdempotencyStore`（缺省 `in-memory`）、`CocoIdempotencyResponseWriter`、`cocoIdempotencySensitiveHeaderContributor`（向 `coco-web` 登记 `Idempotency-Key` 头为敏感头）、`cocoIdempotencyMvcConfigurer`。

**配置属性（`coco.idempotency`）**：

| 键 | 默认值 |
| --- | --- |
| `enabled` | `false` |
| `header-name` | `"Idempotency-Key"` |
| `ttl` | `24h` |
| `max-key-length` | `128` |
| `max-entries` | `100000` |
| `cleanup-interval` | `1m` |
| `store-type` | `IN_MEMORY`（另有 `REDIS`） |
| `allowed-methods` | `POST`、`PUT`、`PATCH`、`DELETE` |
| `redis.key-prefix` | `"coco:idempotency:"` |
| `redis.template-bean-name` | 未设置 |

**测试**：4 个 `*Test`。**用户文档**：[idempotency](https://github.com/patton174/coco-framework/blob/main/website/docs/features/idempotency.md)。

## 3. coco-lock

**坐标与路径**：`coco-features/coco-lock`。**职责**：可选的方法级分布式锁，原子存储可替换（pom）。依赖 `spring-aop`、`aspectjweaver`、`spring-expression`。

| 类型 | 说明 |
| --- | --- |
| `CocoLock`（注解） | `key()`（必填表达式）、`leaseMillis()`、`waitMillis()`、`pollIntervalMillis()`，后三者默认 `-1`（使用全局默认） |
| `CocoLockAspect`（`MethodInterceptor`）、`CocoLockPointcut` | 对 `@CocoLock` 同步方法加锁，`finally` 里释放；由 `cocoLockAdvisor`（`DefaultPointcutAdvisor`）接入 AOP，顺序 `aspect-order` |
| `CocoLockManager`（接口）/ `DefaultCocoLockManager` | `CocoLockResult tryAcquire(CocoLockRequest)` |
| `CocoLockKeyResolver`（接口）/ `DefaultCocoLockKeyResolver` | `String resolve(String keyExpression, Object target, Method method, Object[] arguments)` |
| `CocoLockStore`（接口）/ `InMemoryCocoLockStore` / `RedisCocoLockStore` | `acquire(lease)`、`renew(lease)`、`release(lease)` |
| `CocoLockHandle`（`AutoCloseable`：`lease()`、`reentrant()`、`lost()`）、`CocoLockLease`、`CocoLockRequest`、`CocoLockResult`、`CocoLockStoreType`、`CocoLockErrorCode` | 模型与错误码 |

**自动配置**：`CocoLockRedisAutoConfiguration`、`CocoLockRedisMissingDependencyAutoConfiguration`、`CocoLockAutoConfiguration`。主配置条件仅 `coco.lock.enabled=true`。Bean：`cocoLockMessageBundleRegistrar`、`cocoLockClock`、`CocoLockStore`（缺省 `in-memory`）、`CocoLockKeyResolver`、`CocoLockManager`、`CocoLockAspect`、`cocoLockAdvisor`。

**配置属性（`coco.lock`）**：

| 键 | 默认值 |
| --- | --- |
| `enabled` | `false` |
| `lease` | `30s` |
| `wait` | `0` |
| `poll-interval` | `50ms` |
| `watchdog-enabled` | `true` |
| `watchdog-interval` | `10s` |
| `max-entries` | `100000` |
| `cleanup-interval` | `1m` |
| `max-key-length` | `256` |
| `aspect-order` | `Ordered.LOWEST_PRECEDENCE - 100` |
| `store-type` | `IN_MEMORY`（另有 `REDIS`） |
| `redis.key-prefix` | `"coco:lock:"` |
| `redis.template-bean-name` | 未设置 |

**测试**：4 个 `*Test`。**用户文档**：[lock](https://github.com/patton174/coco-framework/blob/main/website/docs/features/lock.md)。

## 4. coco-cache

**坐标与路径**：`coco-features/coco-cache`。**职责**：两级缓存（Caffeine L1 + Redis L2），带 pub/sub 失效与穿透 / 击穿防护（pom）。依赖 `spring-context-support`、`caffeine`；Redis 为 optional。

| 类型 | 说明 |
| --- | --- |
| `CocoCacheManager`（final，实现 Spring `CacheManager`） | 管理 `CocoTwoLevelCache` |
| `CocoTwoLevelCache`（final，继承 `AbstractValueAdaptingCache`） | 两级缓存实现 |
| `CocoCacheL2Store`（接口）/ `RedisCocoCacheL2Store` | `get(cacheName, key)`、`put(cacheName, key, value, ttlMillis)`、`evict(cacheName, key)`、`clear(cacheName)`；**L2 扩展点** |
| `CocoCacheInvalidationPublisher`（接口：`publish(message)`、`sourceId()`）/ `RedisCocoCacheInvalidationPublisher` | **失效广播扩展点** |
| `CocoCacheInvalidationListener`、`CocoCacheInvalidationMessage` | 失效消息的监听与载体 |
| `CocoCacheStoreType`（枚举） | `LOCAL`、`TWO_LEVEL` |

**自动配置**：`CocoCacheRedisAutoConfiguration`、`CocoCacheRedisMissingDependencyAutoConfiguration`、`CocoCacheAutoConfiguration`。主配置：`coco.cache.enabled=true` + `@EnableCaching`；Bean `CocoCacheManager`，条件 `@ConditionalOnMissingBean(CacheManager)`，即业务已有 `CacheManager` 时不会创建。Redis 配置在 `store-type=two-level` 时提供 `cocoCacheL2RedisTemplate`、`CocoCacheL2Store`、`CocoCacheInvalidationPublisher`、`cocoCacheInvalidationContainer`。

**配置属性（`coco.cache`）**：

| 键 | 默认值 |
| --- | --- |
| `enabled` | `false` |
| `store-type` | `LOCAL`（两级缓存写作 `two-level`） |
| `maximum-size` | `10000` |
| `expire-after-write` | `10m` |
| `cache-null-values` | `true` |
| `null-value-ttl` | `30s` |
| `redis.key-prefix` | `"coco:cache:"` |
| `redis.invalidation-channel` | `"coco:cache:invalidation"` |
| `redis.template-bean-name` | 未设置 |

**测试**：2 个 `*Test`。**用户文档**：[cache](https://github.com/patton174/coco-framework/blob/main/website/docs/features/cache.md)。

## 5. coco-captcha

**坐标与路径**：`coco-features/coco-captcha`（`@since 2.1.0`）。**职责**：可替换的验证码 SPI（图形、滑块、短信验证码），带本地参考实现与可插拔答案存储（pom）。

| 类型 | 说明 |
| --- | --- |
| `CocoCaptchaService`（final） | 对外入口：生成、校验验证码 |
| `CocoCaptchaGenerator`（接口）/ `ImageCocoCaptchaGenerator`、`SliderCocoCaptchaGenerator`、`SmsCodeCocoCaptchaGenerator` | `supportedType()`、`generate(captchaId)`、`matches(submitted, storedAnswer)`；**自定义题型的扩展点** |
| `CocoCaptchaStore`（接口）/ `InMemoryCocoCaptchaStore` / `RedisCocoCaptchaStore` | `store(captchaId, answer, ttl)`、`consume(captchaId)`；**答案存储扩展点** |
| `CocoCaptcha`（record：`captchaId`、`type`、`challenge`、`answer`）、`CocoCaptchaType`（`IMAGE`、`SLIDER`、`SMS_CODE`）、`CocoCaptchaStoreType`（`IN_MEMORY`、`REDIS`） | 模型 |

**自动配置**：`CocoCaptchaRedisAutoConfiguration`、`CocoCaptchaRedisMissingDependencyAutoConfiguration`、`CocoCaptchaAutoConfiguration`。主配置条件 `coco.captcha.enabled=true`；Bean：`cocoCaptchaMessageBundleRegistrar`、`CocoCaptchaStore`、`CocoCaptchaService`。

**配置属性（`coco.captcha`）**：`enabled=false`、`ttl=2m`、`image-enabled=true`、`slider-enabled=true`、`sms-code-enabled=true`、`image-length=4`、`sms-code-length=6`、`slider-tolerance=5`、`store-type=IN_MEMORY`、`redis.key-prefix="coco:captcha:"`、`redis.template-bean-name`（未设置）。

**测试**：3 个 `*Test`。**用户文档**：[captcha](https://github.com/patton174/coco-framework/blob/main/website/docs/features/captcha.md)。

## 6. coco-scheduling

**坐标与路径**：`coco-features/coco-scheduling`。**职责**：带注解支持的本地动态任务调度（pom）。依赖 `spring-aop`。

| 类型 | 说明 |
| --- | --- |
| `CocoScheduled`（注解） | `name()`、`cron()`、`fixedDelay()`、`fixedRate()`、`zone()`、`initialDelay()`（均为字符串，默认空）、`enabled()`（默认 `true`） |
| `CocoScheduledTaskRegistrar`（`SmartInitializingSingleton`） | 扫描 Bean 上的 `@CocoScheduled` 并注册任务 |
| `CocoTaskRegistry`（接口） | `register`、`replace`、`cancel(name)`、`list()`、`status(name)`：运行期动态管理 |
| `CocoTaskScheduler`（接口，继承 `CocoTaskRegistry` 与 `AutoCloseable`）/ `DefaultCocoTaskScheduler` | 调度器；可通过 `@ConditionalOnMissingBean(CocoTaskScheduler)` 替换 |
| `CocoTaskExecutionGuard`（接口：`tryAcquire(taskName)`、`release(taskName)`）/ `InMemoryCocoTaskExecutionGuard` | **扩展点**：多实例下做执行互斥（默认只在进程内） |
| `CocoTaskExecutionObserver`（接口：`onExecution(event)`）、`CocoTaskExecutionEvent`、`CocoTaskExecutionOutcome`（`NONE`、`STARTED`、`SUCCEEDED`、`FAILED`、`SKIPPED`） | **扩展点**：观察执行结果 |
| `CocoTaskDefinition`、`CocoTaskDefinitionValidator`、`CocoTaskOverlapPolicy`（`ALLOW`、`SKIP`）、`CocoTaskScheduleType`（`CRON`、`FIXED_DELAY`、`FIXED_RATE`）、`CocoTaskStatus`、`CocoSchedulingException`、`CocoSchedulingMessage`、`CocoSchedulingMessageResolver` | 模型与异常 |

**自动配置**：`io.github.coco.scheduling.CocoSchedulingAutoConfiguration`，条件 `coco.scheduling.enabled`（`matchIfMissing=true`）。Bean：消息注册器、`CocoSchedulingMessageResolver`、`CocoTaskDefinitionValidator`、`cocoTaskSchedulerExecutor`（`ThreadPoolTaskScheduler`，`@ConditionalOnMissingBean(TaskScheduler)`）、`CocoTaskExecutionGuard`、`CocoTaskScheduler`、`CocoScheduledTaskRegistrar`。

**配置属性（`coco.scheduling`）**：`enabled=true`、`pool-size=1`、`thread-name-prefix="coco-scheduling-"`、`shutdown.await-termination=30s`、`shutdown.interrupt=false`。

**测试**：2 个 `*Test`（另有 1 个辅助 Java 文件）。**用户文档**：[scheduling](https://github.com/patton174/coco-framework/blob/main/website/docs/features/scheduling.md)。

## 7. coco-storage

**坐标与路径**：`coco-features/coco-storage`。**职责**：可替换的对象存储 SPI，附带安全的本地参考实现（pom）。

| 类型 | 说明 |
| --- | --- |
| `CocoObjectStorage`（接口） | `put(CocoObjectPutRequest)`、`open(key)`、`stat(key)`、`exists(key)`、`delete(key)`；**存储扩展点** |
| `LocalCocoObjectStorage`（`storage.local`，final） | 本地参考实现，根目录由 `local.root` 指定 |
| `ValidatingCocoObjectStorage` | 在任意 `CocoObjectStorage` 外包一层内容校验 |
| `CocoStorageValidationBeanPostProcessor` | 当 `validation.enabled`（缺省 true）时，把容器里每个 `CocoObjectStorage` Bean（含业务自己的实现）包成 `ValidatingCocoObjectStorage` |
| `CocoContentValidator`（接口：`validate(CocoContentProbe)`）/ `CocoSignatureContentValidator` | **内容校验扩展点**，默认按文件签名（魔数）校验 |
| `CocoFileScanner`（接口：`scan(CocoContentProbe)`）/ `NoOpCocoFileScanner` | **病毒 / 内容扫描扩展点**，默认空操作 |
| `CocoObjectPutRequest`、`CocoObjectMetadata`、`CocoObjectResource`、`CocoContentProbe`、`CocoFileSignature`、`CocoFileSignatures`、`CocoStorageOverwritePolicy`（`REJECT`、`REPLACE`）、`CocoStorageException`、`CocoStorageErrorCode` | 模型与异常 |

**自动配置**：`io.github.coco.storage.CocoStorageAutoConfiguration`，条件 `coco.storage.enabled=true`。Bean：消息注册器、`CocoObjectStorage`（默认 `LocalCocoObjectStorage`）、`CocoContentValidator`、`CocoFileScanner`、静态方法 Bean `CocoStorageValidationBeanPostProcessor`。

**配置属性（`coco.storage`）**：

| 键 | 默认值 |
| --- | --- |
| `enabled` | `false` |
| `overwrite-policy` | `REJECT` |
| `max-size-bytes` | `10485760`（10 MiB，`DEFAULT_MAX_SIZE_BYTES`） |
| `allowed-extensions` | `jpg`、`jpeg`、`png`、`gif`、`webp`、`pdf`、`txt`、`csv` |
| `allowed-content-types` | 空 |
| `local.root` | 无（`Path`） |
| `local.orphan-grace-period` | `5m` |
| `local.gc-interval` | `5m` |
| `validation.enabled` | `true` |
| `validation.probe-size` | `512` |
| `validation.reject-dangerous-signatures` | `true` |
| `validation.require-signature-match` | `true` |

**测试**：5 个 `*Test`。**用户文档**：[storage](https://github.com/patton174/coco-framework/blob/main/website/docs/features/storage.md)。

## 8. coco-messaging

**坐标与路径**：`coco-features/coco-messaging`。**职责**：进程内消息基础设施与传输 SPI（pom）。默认只做进程内投递，声明 `CocoMessageTransport` Bean 即可替换传输实现。

| 类型 | 说明 |
| --- | --- |
| `CocoMessagePublisher`（接口） | `publish(CocoMessageEnvelope)`；默认方法 `publish(topic, payload)`；默认实现 `internal.DefaultCocoMessagePublisher` |
| `CocoMessageTransport`（接口，`AutoCloseable`） | `publish(envelope)`、`subscribe(topic, handler)`、`close()`；默认实现 `internal.LocalCocoMessageTransport`；**传输扩展点** |
| `CocoMessageHandler`（接口） | `topic()`、`handle(envelope)` |
| `CocoMessageListener`（**注解**） | `topic()`、`order()`（默认 `0`），标在 Bean 方法上；由 `internal.CocoMessageListenerRegistrar` 注册 |
| `CocoMessageEnvelope`、`CocoMessageSubscription`（`AutoCloseable`）、`CocoMessagingException`、`support.CocoMessagingMessages` | 信封、订阅句柄与异常、消息文本辅助 |
| 策略枚举 | `CocoMessageDeliveryMode`（`SYNC`、`ASYNC`）、`CocoMessageFailurePolicy`（`FAIL_FAST`、`LOG_AND_CONTINUE`）、`CocoMessageNoSubscriberPolicy`（`IGNORE`、`FAIL`）、`CocoMessageAsyncShutdownPolicy`（`DRAIN`、`DISCARD`） |

**自动配置**：`io.github.coco.messaging.CocoMessagingAutoConfiguration`，条件 `coco.messaging.enabled`（`matchIfMissing=true`）。Bean：消息注册器、`CocoMessageTransport`（`destroyMethod=close`）、`CocoMessagePublisher`、`CocoMessageListenerRegistrar`。

**配置属性（`coco.messaging`）**：`enabled=true`、`delivery-mode=SYNC`、`failure-policy=FAIL_FAST`、`no-subscriber-policy=IGNORE`、`async.queue-capacity=256`、`async.shutdown-await=30s`、`async.shutdown-policy=DRAIN`。

**测试**：1 个 `*Test`。**用户文档**：[messaging](https://github.com/patton174/coco-framework/blob/main/website/docs/features/messaging.md)。

## 9. coco-notification

**坐标与路径**：`coco-features/coco-notification`（`@since 2.1.0`）。**职责**：可替换的通知通道 SPI（短信、邮件、站内信），带本地参考实现；云厂商 SDK 由应用自带（pom）。

| 类型 | 说明 |
| --- | --- |
| `CocoNotificationChannel`（接口） | `supportedType()`、`send(CocoNotification)`；**通道扩展点** |
| `CocoNotificationService`（final） | 按通道类型路由发送 |
| `LoggingCocoNotificationChannel`、`InMemoryInAppCocoNotificationChannel` | 开发用参考通道（不真正发送 / 不跨实例、不持久化） |
| `CocoNotification`（record：`channelType`、`recipient`、`subject`、`content`、`attributes`）、`CocoNotificationResult`（record：`success`、`channelType`、`providerMessageId`、`detail`）、`CocoNotificationChannelType`（`SMS`、`EMAIL`、`IN_APP`） | 模型 |

**自动配置**：`io.github.coco.notification.CocoNotificationAutoConfiguration`，条件 `coco.notification.enabled=true`。`cocoNotificationService` 先收集业务提供的 `CocoNotificationChannel`；某类型（SMS / EMAIL）没有业务通道且 `logging-fallback=true` 时补日志通道，IN_APP 没有业务通道且 `in-memory-in-app=true` 时补内存通道；每次补齐都会输出 WARN，提醒生产环境必须注册真实通道。

**配置属性（`coco.notification`）**：`enabled=false`、`logging-fallback=true`、`in-memory-in-app=true`。

**测试**：2 个 `*Test`。**用户文档**：[notification](https://github.com/patton174/coco-framework/blob/main/website/docs/features/notification.md)。官网另有汇总页 [infra](https://github.com/patton174/coco-framework/blob/main/website/docs/features/infra.md)。

## 10. 相关页面

- [模块参考总览](Module-Overview)
- [数据与安全类 Feature](Module-Features-Data-And-Security)
- [coco-web：Web 运行时](Module-Web)
- [Spring 组合层](Module-Spring)
- [Feature 机制：构建期与运行期](Feature-System)
- [指南：新增一个 Feature 模块](Guide-Add-Feature-Module)
- [指南：新增或调整自动配置](Guide-Add-Auto-Configuration)
- [约定与规范](Conventions)
