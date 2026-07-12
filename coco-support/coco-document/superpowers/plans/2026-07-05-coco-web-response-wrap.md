# Coco Web Response Wrap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add default-on Spring MVC response wrapping for normal REST responses in `coco-feature-web`.

**Architecture:** `coco-feature-web` owns the MVC integration because the feature depends on `ResponseBodyAdvice`, Servlet request metadata, and HTTP message conversion. `CocoApiResponse` remains the Web response model and gains a success factory. A new `CocoResponseWrapAdvice` wraps eligible controller return values, while `@CocoIgnoreResponseWrap` and configuration provide explicit escape hatches.

**Tech Stack:** Java 17, Spring Boot 4.1, Spring MVC `ResponseBodyAdvice`, Jackson `ObjectMapper`, JUnit 5, Spring Boot test runners.

## Global Constraints

- Keep implementation inside `coco-features/coco-feature-web`.
- Use JavaDoc HTML tags and Chinese comments where comments are useful.
- Class-level JavaDoc must include author `patton174` and repository `https://github.com/patton174/coco-framework`.
- Use TDD: write failing tests before production code for each behavior.
- Do not move `CocoApiResponse` to common in this stage.
- Do not implement non-`CocoException` global exception handling in this stage.
- Default behavior is enabled; business projects only configure disabled cases.
- Run `mvn verify` and `mvn -DskipTests install javadoc:javadoc` before publishing.

---

## File Structure

- Modify `coco-features/coco-feature-web/src/main/java/io/github/coco/feature/web/response/CocoApiResponse.java`
  - Add `success(...)` factory and keep constructor validation.
- Create `coco-features/coco-feature-web/src/main/java/io/github/coco/feature/web/response/CocoIgnoreResponseWrap.java`
  - Method/type annotation used by controllers to opt out of wrapping.
- Create `coco-features/coco-feature-web/src/main/java/io/github/coco/feature/web/response/CocoResponseWrapProperties.java`
  - Holds `enabled` and `successMessageCode`.
- Create `coco-features/coco-feature-web/src/main/java/io/github/coco/feature/web/response/CocoSystemCodeProvider.java`
  - Provides default system response codes.
- Create `coco-features/coco-feature-web/src/main/java/io/github/coco/feature/web/response/CocoSystemCodes.java`
  - Provides default codes and builder-based overrides.
- Create `coco-features/coco-feature-web/src/main/java/io/github/coco/feature/web/response/CocoResponseWrapAdvice.java`
  - Implements `ResponseBodyAdvice<Object>` and owns wrap/skip logic.
- Modify `coco-features/coco-feature-web/src/main/java/io/github/coco/feature/web/CocoWebProperties.java`
  - Add nested `responseWrap` property.
- Modify `coco-features/coco-feature-web/src/main/java/io/github/coco/feature/web/CocoWebAutoConfiguration.java`
  - Register `CocoResponseWrapAdvice` when servlet web app and wrapping enabled.
- Modify `coco-features/coco-feature-web/src/main/resources/coco-feature-web-messages*.properties`
  - Add success message key.
- Modify tests:
  - `coco-features/coco-feature-web/src/test/java/io/github/coco/feature/web/CocoWebAutoConfigurationTest.java`
  - `coco-features/coco-feature-web/src/test/java/io/github/coco/feature/web/CocoWebConfigurationMetadataTest.java`

## Task 1: Response Model And Properties

**Files:**
- Modify: `coco-features/coco-feature-web/src/main/java/io/github/coco/feature/web/response/CocoApiResponse.java`
- Create: `coco-features/coco-feature-web/src/main/java/io/github/coco/feature/web/response/CocoResponseWrapProperties.java`
- Modify: `coco-features/coco-feature-web/src/main/java/io/github/coco/feature/web/CocoWebProperties.java`
- Test: `coco-features/coco-feature-web/src/test/java/io/github/coco/feature/web/CocoWebAutoConfigurationTest.java`

**Interfaces:**
- Produces: `CocoApiResponse.success(int code, String message, T data, String traceId, String path)`
- Produces: `CocoResponseWrapProperties#isEnabled()`, `#setEnabled(boolean)`, `#getSuccessMessageCode()`, `#setSuccessMessageCode(String)`
- Produces: `CocoWebProperties#getResponseWrap()` and `#setResponseWrap(CocoResponseWrapProperties)`

- [ ] **Step 1: Write failing tests for success response and default properties**

Add tests to `CocoWebAutoConfigurationTest`:

```java
@Test
void createsSuccessResponseModel() {
    CocoApiResponse<String> response = CocoApiResponse.success(
            200, "操作成功", "payload", "trace-id", "/api/users");

    assertTrue(response.success());
    assertEquals(200, response.code());
    assertEquals("操作成功", response.message());
    assertEquals("payload", response.data());
    assertEquals("trace-id", response.traceId());
    assertEquals("/api/users", response.path());
}

@Test
void responseWrapPropertiesUseDefaultsAndResetNullNestedValue() {
    CocoWebProperties properties = new CocoWebProperties();

    assertTrue(properties.getResponseWrap().isEnabled());
    assertEquals("coco.web.response.success", properties.getResponseWrap().getSuccessMessageCode());

    properties.setResponseWrap(null);

    assertTrue(properties.getResponseWrap().isEnabled());
}
```

- [ ] **Step 2: Run red test**

Run:

```powershell
$env:JAVA_HOME='D:\Programs\Java\jdk_21'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; mvn -pl :coco-feature-web -am test
```

Expected: compilation fails because `CocoApiResponse.success(...)` and `CocoWebProperties#getResponseWrap()` do not exist.

- [ ] **Step 3: Implement response model and property classes**

Add `CocoApiResponse.success(...)`:

```java
public static <T> CocoApiResponse<T> success(int code, String message, T data, String traceId, String path) {
    return new CocoApiResponse<>(true, code, message, data, traceId, path);
}
```

Create `CocoResponseWrapProperties`:

```java
public class CocoResponseWrapProperties {

    private static final String DEFAULT_SUCCESS_MESSAGE_CODE = "coco.web.response.success";

    private boolean enabled = true;

    private String successMessageCode = DEFAULT_SUCCESS_MESSAGE_CODE;

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSuccessMessageCode() {
        return hasText(this.successMessageCode)
                ? this.successMessageCode.trim()
                : DEFAULT_SUCCESS_MESSAGE_CODE;
    }

    public void setSuccessMessageCode(String successMessageCode) {
        this.successMessageCode = successMessageCode;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
```

Add nested property to `CocoWebProperties`:

```java
@NestedConfigurationProperty
private CocoResponseWrapProperties responseWrap = new CocoResponseWrapProperties();

public CocoResponseWrapProperties getResponseWrap() {
    return this.responseWrap;
}

public void setResponseWrap(CocoResponseWrapProperties responseWrap) {
    this.responseWrap = responseWrap == null ? new CocoResponseWrapProperties() : responseWrap;
}
```

- [ ] **Step 4: Run green test**

Run:

```powershell
$env:JAVA_HOME='D:\Programs\Java\jdk_21'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; mvn -pl :coco-feature-web -am test
```

Expected: existing tests and new Task 1 tests pass.

- [ ] **Step 5: Commit Task 1**

```powershell
git add coco-features/coco-feature-web/src/main/java/io/github/coco/feature/web/response/CocoApiResponse.java coco-features/coco-feature-web/src/main/java/io/github/coco/feature/web/response/CocoResponseWrapProperties.java coco-features/coco-feature-web/src/main/java/io/github/coco/feature/web/CocoWebProperties.java coco-features/coco-feature-web/src/test/java/io/github/coco/feature/web/CocoWebAutoConfigurationTest.java
git commit -m "feat: add web response wrap properties"
```

## Task 2: Response Wrap Advice

**Files:**
- Create: `coco-features/coco-feature-web/src/main/java/io/github/coco/feature/web/response/CocoIgnoreResponseWrap.java`
- Create: `coco-features/coco-feature-web/src/main/java/io/github/coco/feature/web/response/CocoResponseWrapAdvice.java`
- Test: `coco-features/coco-feature-web/src/test/java/io/github/coco/feature/web/CocoWebAutoConfigurationTest.java`

**Interfaces:**
- Consumes: `CocoApiResponse.success(...)`
- Consumes: `CocoResponseWrapProperties`
- Produces: `@CocoIgnoreResponseWrap`
- Produces: `CocoResponseWrapAdvice implements ResponseBodyAdvice<Object>`

- [ ] **Step 1: Write failing tests for wrapping and skip behavior**

Add tests to `CocoWebAutoConfigurationTest`:

```java
@Test
void wrapsObjectResponseBody() {
    this.webContextRunner.run(context -> {
        CocoResponseWrapAdvice advice = context.getBean(CocoResponseWrapAdvice.class);
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/api/users");
        ServerHttpRequest request = new ServletServerHttpRequest(servletRequest);

        Object body = advice.beforeBodyWrite(Map.of("name", "Coco"), methodParameter("objectBody"),
                MediaType.APPLICATION_JSON, MappingJackson2HttpMessageConverter.class, request,
                new ServletServerHttpResponse(new MockHttpServletResponse()));

        assertTrue(body instanceof CocoApiResponse<?>);
        CocoApiResponse<?> response = (CocoApiResponse<?>) body;
        assertTrue(response.success());
        assertEquals(200, response.code());
        assertEquals("操作成功", response.message());
        assertEquals(Map.of("name", "Coco"), response.data());
        assertEquals("/api/users", response.path());
    });
}

@Test
void wrapsStringResponseBodyAsJsonString() throws Exception {
    this.webContextRunner.run(context -> {
        CocoResponseWrapAdvice advice = context.getBean(CocoResponseWrapAdvice.class);
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/api/text");
        ServerHttpRequest request = new ServletServerHttpRequest(servletRequest);

        Object body = advice.beforeBodyWrite("hello", methodParameter("stringBody"),
                MediaType.TEXT_PLAIN, StringHttpMessageConverter.class, request,
                new ServletServerHttpResponse(new MockHttpServletResponse()));

        assertTrue(body instanceof String);
        assertTrue(((String) body).contains("\"success\":true"));
        assertTrue(((String) body).contains("\"data\":\"hello\""));
    });
}

@Test
void skipsAlreadyWrappedBodyAndIgnoredMethodsAndResponseEntity() {
    this.webContextRunner.run(context -> {
        CocoResponseWrapAdvice advice = context.getBean(CocoResponseWrapAdvice.class);

        assertFalse(advice.supports(methodParameter("wrappedBody"),
                MappingJackson2HttpMessageConverter.class));
        assertFalse(advice.supports(methodParameter("ignoredBody"),
                MappingJackson2HttpMessageConverter.class));
        assertFalse(advice.supports(methodParameter("responseEntityBody"),
                MappingJackson2HttpMessageConverter.class));
    });
}
```

Add helper controller methods inside the test class:

```java
Object objectBody() {
    return Map.of("name", "Coco");
}

String stringBody() {
    return "hello";
}

CocoApiResponse<String> wrappedBody() {
    return CocoApiResponse.success(200, "操作成功", "hello", null, null);
}

@CocoIgnoreResponseWrap
Object ignoredBody() {
    return Map.of("ignored", true);
}

ResponseEntity<String> responseEntityBody() {
    return ResponseEntity.ok("hello");
}

private MethodParameter methodParameter(String methodName) throws NoSuchMethodException {
    Method method = CocoWebAutoConfigurationTest.class.getDeclaredMethod(methodName);
    return new MethodParameter(method, -1);
}
```

- [ ] **Step 2: Run red test**

Run:

```powershell
$env:JAVA_HOME='D:\Programs\Java\jdk_21'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; mvn -pl :coco-feature-web -am test
```

Expected: compilation fails because `CocoResponseWrapAdvice` and `CocoIgnoreResponseWrap` do not exist.

- [ ] **Step 3: Implement annotation and advice**

Create `CocoIgnoreResponseWrap`:

```java
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CocoIgnoreResponseWrap {
}
```

Create `CocoResponseWrapAdvice` with constructor:

```java
public CocoResponseWrapAdvice(CocoMessageService messageService, CocoResponseWrapProperties properties,
        CocoSystemCodeProvider codeProvider, ObjectMapper objectMapper) {
    this.messageService = Objects.requireNonNull(messageService, "messageService must not be null");
    this.properties = Objects.requireNonNull(properties, "properties must not be null");
    this.codeProvider = Objects.requireNonNull(codeProvider, "codeProvider must not be null");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
}
```

Core rules:

```java
@Override
public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
    if (returnType.hasMethodAnnotation(CocoIgnoreResponseWrap.class)) {
        return false;
    }
    Class<?> containingClass = returnType.getContainingClass();
    if (containingClass.isAnnotationPresent(CocoIgnoreResponseWrap.class)) {
        return false;
    }
    Class<?> parameterType = returnType.getParameterType();
    return !CocoApiResponse.class.isAssignableFrom(parameterType)
            && !ResponseEntity.class.isAssignableFrom(parameterType)
            && !Resource.class.isAssignableFrom(parameterType)
            && !byte[].class.equals(parameterType);
}
```

Core wrapping:

```java
@Override
public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
        Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request,
        ServerHttpResponse response) {
    CocoApiResponse<Object> wrapped = CocoApiResponse.success(this.codeProvider.success(),
            this.messageService.getMessage(this.properties.getSuccessMessageCode()), body,
            CocoTraceContext.getOrCreateTraceId(), resolvePath(request));
    if (StringHttpMessageConverter.class.isAssignableFrom(selectedConverterType)) {
        try {
            return this.objectMapper.writeValueAsString(wrapped);
        }
        catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize Coco response", ex);
        }
    }
    return wrapped;
}
```

- [ ] **Step 4: Run green test**

Run:

```powershell
$env:JAVA_HOME='D:\Programs\Java\jdk_21'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; mvn -pl :coco-feature-web -am test
```

Expected: Task 2 tests pass.

- [ ] **Step 5: Commit Task 2**

```powershell
git add coco-features/coco-feature-web/src/main/java/io/github/coco/feature/web/response/CocoIgnoreResponseWrap.java coco-features/coco-feature-web/src/main/java/io/github/coco/feature/web/response/CocoResponseWrapAdvice.java coco-features/coco-feature-web/src/test/java/io/github/coco/feature/web/CocoWebAutoConfigurationTest.java
git commit -m "feat: add web response wrap advice"
```

## Task 3: Auto-Configuration, Messages, And Metadata

**Files:**
- Modify: `coco-features/coco-feature-web/src/main/java/io/github/coco/feature/web/CocoWebAutoConfiguration.java`
- Modify: `coco-features/coco-feature-web/src/main/resources/coco-feature-web-messages.properties`
- Modify: `coco-features/coco-feature-web/src/main/resources/coco-feature-web-messages_en_US.properties`
- Modify: `coco-features/coco-feature-web/src/main/resources/coco-feature-web-messages_zh_CN.properties`
- Modify: `coco-features/coco-feature-web/src/test/java/io/github/coco/feature/web/CocoWebAutoConfigurationTest.java`
- Modify: `coco-features/coco-feature-web/src/test/java/io/github/coco/feature/web/CocoWebConfigurationMetadataTest.java`

**Interfaces:**
- Consumes: `CocoResponseWrapAdvice`
- Produces: bean `cocoResponseWrapAdvice`
- Produces: resource key `coco.web.response.success`
- Produces: metadata entries `coco.web.response-wrap.enabled`, `coco.web.response-wrap.success-message-code`

- [ ] **Step 1: Write failing auto-configuration and metadata tests**

Add tests:

```java
@Test
void createsResponseWrapAdviceByDefault() {
    this.webContextRunner.run(context -> assertTrue(context.containsBean("cocoResponseWrapAdvice")));
}

@Test
void disablesResponseWrapAdviceByProperty() {
    this.webContextRunner
            .withPropertyValues("coco.web.response-wrap.enabled=false")
            .run(context -> assertFalse(context.containsBean("cocoResponseWrapAdvice")));
}

@Test
void resolvesResponseWrapSuccessMessageFromWebBundle() {
    this.webContextRunner.run(context -> {
        CocoMessageService messageService = context.getBean(CocoMessageService.class);

        assertEquals("操作成功", messageService.getMessage("coco.web.response.success"));
    });
}
```

Update `CocoWebConfigurationMetadataTest`:

```java
assertTrue(content.contains("\"name\": \"coco.web.response-wrap.enabled\""));
assertTrue(content.contains("\"name\": \"coco.web.response-wrap.success-message-code\""));
```

- [ ] **Step 2: Run red test**

Run:

```powershell
$env:JAVA_HOME='D:\Programs\Java\jdk_21'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; mvn -pl :coco-feature-web -am test
```

Expected: tests fail because the bean, metadata, and success message are not present.

- [ ] **Step 3: Implement auto-configuration and messages**

Add bean:

```java
@Bean
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "coco.web.response-wrap", name = "enabled",
        havingValue = "true", matchIfMissing = true)
@ConditionalOnMissingBean
public CocoResponseWrapAdvice cocoResponseWrapAdvice(CocoMessageService messageService,
        CocoWebProperties properties, CocoSystemCodeProvider codeProvider, ObjectMapper objectMapper) {
    return new CocoResponseWrapAdvice(messageService, properties.getResponseWrap(), codeProvider, objectMapper);
}
```

Add messages:

```properties
coco.web.response.success=Operation succeeded.
```

```properties
coco.web.response.success=操作成功
```

- [ ] **Step 4: Run green test**

Run:

```powershell
$env:JAVA_HOME='D:\Programs\Java\jdk_21'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; mvn -pl :coco-feature-web -am test
```

Expected: Web module tests pass.

- [ ] **Step 5: Commit Task 3**

```powershell
git add coco-features/coco-feature-web/src/main/java/io/github/coco/feature/web/CocoWebAutoConfiguration.java coco-features/coco-feature-web/src/main/resources/coco-feature-web-messages.properties coco-features/coco-feature-web/src/main/resources/coco-feature-web-messages_en_US.properties coco-features/coco-feature-web/src/main/resources/coco-feature-web-messages_zh_CN.properties coco-features/coco-feature-web/src/test/java/io/github/coco/feature/web/CocoWebAutoConfigurationTest.java coco-features/coco-feature-web/src/test/java/io/github/coco/feature/web/CocoWebConfigurationMetadataTest.java
git commit -m "feat: auto configure web response wrapping"
```

## Task 4: Full Verification And Publishing

**Files:**
- Verify all changed files.

**Interfaces:**
- Consumes: completed Tasks 1-3.
- Produces: pushed branch and PR.

- [ ] **Step 1: Run whitespace check**

```powershell
git diff --check
```

Expected: no output.

- [ ] **Step 2: Run full Maven verification**

```powershell
$env:JAVA_HOME='D:\Programs\Java\jdk_21'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; mvn verify
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Run install and JavaDoc generation**

```powershell
$env:JAVA_HOME='D:\Programs\Java\jdk_21'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; mvn -DskipTests install javadoc:javadoc
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Push and create PR**

```powershell
git push -u origin codex-dev-20260705
gh pr create --base main --head codex-dev-20260705 --title "feat: add web response wrapping" --body-file <body-file>
```

Expected: PR URL is printed.

- [ ] **Step 5: Watch CI and merge when green**

```powershell
gh pr checks <pr-number> --watch
gh pr merge <pr-number> --merge --subject "feat: add web response wrapping" --body "Merge after GitHub Actions verify checks passed."
```

Expected: PR is merged and `main` CI passes.

## Self-Review

- Spec coverage: response wrapping, ignore annotation, configuration, String handling, skip rules, messages, metadata, validation, and publishing are covered.
- Placeholder scan: no unresolved markers or unspecified implementation steps remain.
- Type consistency: property names use `response-wrap`, Java property object is `responseWrap`, and default codes match the design spec.
