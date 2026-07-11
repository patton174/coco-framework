# Coco Common I18n Design

## Goal

Build the first infrastructure capability under `coco-common/coco-common-i18n`: internationalized message resolution for Coco Framework.

`coco-common` is an aggregate module for common infrastructure. Internationalization is the first concrete child module and is published as `coco-common-i18n`. Future infrastructure such as request context, trace context, ID generation, time utilities, and common value objects can be added as common child modules when they become necessary.

## Module Boundary

`coco-common` is not a business feature module. It is the aggregate for framework foundations used by other Coco modules.

Its child modules should contain stable, low-level infrastructure that can be reused by web, exception handling, response wrapping, audit logging, OpenAPI, and future feature modules. They should not contain MyBatis-Plus integration, audit business behavior, tenant logic, security decisions, or concrete web endpoint behavior.

`coco-config` remains responsible for binding feature selection. `coco-common-i18n` owns the `coco.common.i18n` property model because it belongs to the i18n infrastructure itself, while Spring Boot auto-configuration and property metadata should still be wired cleanly through normal Boot configuration mechanisms.

## Public And Internal Boundaries

`coco-common-i18n` has two audiences:

1. Business projects and external Coco users.
2. Other Coco framework modules.

Public packages are stable extension points and can be used by business projects:

```text
io.github.coco.common.i18n
io.github.coco.common.exception
```

Internal packages are framework implementation details and should not be used by business projects:

```text
io.github.coco.common.internal
io.github.coco.common.autoconfigure
```

Public APIs should be small and stable. Internal classes can contain factories, validators, resource bundle helpers, and other tools used by Coco itself. If an internal tool becomes useful for business projects, it should be promoted into a public package deliberately instead of being exposed accidentally.

## First Capability: I18n

The first `coco-common-i18n` capability provides a small message service on top of Spring's `MessageSource`.

Business code and later Coco modules should use `CocoMessageService` instead of directly depending on resource bundle mechanics. This gives the framework one stable place to define message fallback rules and locale selection.

Framework exceptions and framework message prompts must also use this i18n foundation. Exceptions should store message codes, arguments, and default messages. Translation should happen through `CocoMessageService`, usually in a later exception handler or response adapter. The exception object itself should not need the Spring container to construct a localized string.

## User Experience

Business projects still only need the existing starter dependency:

```xml
<dependency>
    <groupId>io.github.patton174</groupId>
    <artifactId>coco-spring-boot-starter</artifactId>
</dependency>
```

Internationalization should be enabled by default because it is infrastructure, not a feature plugin.

The default configuration should work without any explicit configuration. If a project wants to customize it, it can use:

```yaml
coco:
  common:
    i18n:
      basename:
        - messages
        - coco-messages
      default-locale: zh-CN
      fallback-to-system-locale: false
      use-code-as-default-message: true
```

Resource file convention:

```text
src/main/resources/messages.properties
src/main/resources/messages_zh_CN.properties
src/main/resources/messages_en_US.properties
src/main/resources/coco-messages.properties
src/main/resources/coco-messages_zh_CN.properties
```

`messages*` is the business project's default message bundle. `coco-messages*` is the framework's default message bundle. Business projects may define the same code in `messages*` to override a framework prompt when the basename order keeps `messages` before `coco-messages`.

Java usage:

```java
String message = cocoMessageService.getMessage("user.not-found", userId);
String enMessage = cocoMessageService.getMessage("user.not-found", Locale.US, userId);
String fallback = cocoMessageService.getMessageOrDefault("unknown.code", "默认消息");
```

## Public API

Create the following package in `coco-common/coco-common-i18n`:

```text
io.github.coco.common.i18n
```

Public service:

```java
public interface CocoMessageService {

    String getMessage(String code, Object... args);

    String getMessage(String code, Locale locale, Object... args);

    String getMessageOrDefault(String code, String defaultMessage, Object... args);

    String getMessageOrDefault(String code, String defaultMessage, Locale locale, Object... args);
}
```

Default implementation:

```text
DefaultCocoMessageService
```

The implementation should delegate to Spring `MessageSource` and use a `CocoLocaleResolver` for the no-locale overloads.

Locale resolver:

```java
public interface CocoLocaleResolver {

    Locale resolveLocale();
}
```

Default locale resolver:

```text
DefaultCocoLocaleResolver
```

For this first stage, the default resolver returns the configured default locale. Request-header based locale detection belongs to the later web layer, because `coco-common-i18n` should not depend on Servlet APIs.

## Framework Exceptions And Prompts

Create the following package in `coco-common/coco-common-i18n`:

```text
io.github.coco.common.exception
```

Public exception type:

```java
public class CocoException extends RuntimeException {

    String code();

    Object[] args();

    String defaultMessage();
}
```

The concrete Java implementation should expose these values through normal Java methods. Constructor overloads should support:

```java
new CocoException("coco.error.unknown");
new CocoException("coco.error.unknown", "未知错误");
new CocoException("coco.error.invalid-argument", "参数不合法", argumentName);
new CocoException("coco.error.unknown", "未知错误", cause);
```

Public message descriptor:

```java
public record CocoMessage(String code, String defaultMessage, Object... args) {
}
```

`CocoMessage` is for framework prompts that are not necessarily exceptions. It lets Coco modules pass a message code and arguments across module boundaries without resolving the final text too early.

Default framework message bundle:

```text
coco-common/coco-common-i18n/src/main/resources/coco-messages.properties
coco-common/coco-common-i18n/src/main/resources/coco-messages_zh_CN.properties
coco-common/coco-common-i18n/src/main/resources/coco-messages_en_US.properties
```

Initial framework message codes:

```text
coco.error.unknown
coco.error.invalid-argument
coco.error.missing-message-code
```

These are infrastructure-level defaults only. Business feature modules should add their own codes when those features are implemented.

## Configuration

Create:

```text
io.github.coco.common.i18n.CocoI18nProperties
io.github.coco.common.CocoCommonProperties
```

Property namespace:

```yaml
coco.common.i18n
```

Properties:

```text
coco.common.i18n.basename
coco.common.i18n.default-locale
coco.common.i18n.fallback-to-system-locale
coco.common.i18n.use-code-as-default-message
```

Defaults:

```text
basename = ["messages", "coco-messages"]
default-locale = Locale.SIMPLIFIED_CHINESE
fallback-to-system-locale = false
use-code-as-default-message = true
```

The configuration metadata generated by `spring-boot-configuration-processor` must expose every property above so IDEs can resolve them.

## Auto-Configuration

Create:

```text
io.github.coco.common.autoconfigure.CocoCommonAutoConfiguration
```

The auto-configuration should:

1. Enable `CocoCommonProperties`.
2. Create a Coco-owned `MessageSource` bean when no bean with the Coco message source name exists.
3. Create `CocoLocaleResolver` when missing.
4. Create `CocoMessageService` when missing.
5. Register itself through `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

The Coco message source should use `ResourceBundleMessageSource`, UTF-8 encoding, configured basenames, and configured fallback behavior.

It should not replace the application's primary Spring `messageSource` bean in this first stage. Avoiding replacement keeps the first implementation predictable and prevents collisions with business projects that already configure Spring messages.

## Maven Wiring

Add `coco-common` and `coco-common-i18n` to:

```text
root pom modules: coco-common aggregate
root dependencyManagement: coco-common-i18n
coco-bom dependencyManagement: coco-common-i18n
coco-spring-boot-starter dependencies: coco-common-i18n
```

`coco-common-i18n` dependencies:

```text
coco-api
spring-boot
spring-boot-autoconfigure
spring-boot-configuration-processor optional
spring-context
junit-jupiter test
spring-boot-test test
assertj-core test
```

`coco-common-i18n` should not depend on `coco-config` for this first stage. Keeping it independent avoids a cycle where config owns feature selection while common owns reusable infrastructure. If later we decide all nested property models must live in `coco-config`, we can move only the property model without moving the i18n service.

## Data Flow

Message resolution flow:

```text
business code or Coco module
-> CocoMessageService
-> CocoLocaleResolver
-> Coco MessageSource
-> messages*.properties or coco-messages*.properties
-> resolved message or fallback
```

When no explicit locale is passed, `CocoMessageService` uses `CocoLocaleResolver`.

When an explicit locale is passed, `CocoMessageService` uses that locale directly.

When a code is missing:

```text
use-code-as-default-message = true  -> return the message code
use-code-as-default-message = false -> use Spring NoSuchMessageException behavior unless a default message was supplied
```

Framework exception message flow:

```text
CocoException or CocoMessage
-> code, args, defaultMessage
-> CocoMessageService
-> localized text for the active locale
```

`CocoException#getMessage()` should return the default message when present, otherwise the code. It should not perform locale resolution.

## Error Handling

`getMessage` should reject blank message codes with `IllegalArgumentException`.

`getMessageOrDefault` should reject blank message codes and allow a blank default message.

`CocoException` and `CocoMessage` should reject blank message codes with `IllegalArgumentException`.

Missing resource files should not fail startup. Spring's message source should simply resolve missing codes according to the fallback rules.

## Testing

Tests should cover:

1. `DefaultCocoMessageService` resolves a Chinese message from resource bundles.
2. `DefaultCocoMessageService` resolves an English message when an explicit locale is passed.
3. Missing code returns the code when `use-code-as-default-message` is enabled.
4. `CocoCommonAutoConfiguration` creates `CocoMessageService` and related beans.
5. Application property overrides change the basenames and default locale.
6. Generated configuration metadata exposes `coco.common.i18n.basename` and related properties.
7. `CocoException` preserves code, arguments, default message, and cause.
8. `CocoMessage` rejects blank codes and can be resolved by `CocoMessageService`.

## Out Of Scope

This stage does not implement:

- Unified response body translation.
- Request header locale resolution.
- Thread-local locale context.
- Database-backed message storage.
- Frontend language-pack export.
- Annotation-driven i18n.
- Global web exception handling.

These should be added only after the common service is stable and a concrete framework feature needs them.

## Acceptance Criteria

- `coco-common` exists as a Maven aggregate module, and `coco-common-i18n` is included through `coco-spring-boot-starter`.
- Business projects can inject `CocoMessageService` after importing the starter.
- Coco framework exceptions and prompts can carry i18n message codes without resolving text too early.
- Default messages resolve from classpath resource bundles.
- `coco.common.i18n.*` properties are visible in generated Spring Boot configuration metadata.
- Existing `coco-config` tests continue passing.
- Full Maven `verify` and JavaDoc generation pass.
