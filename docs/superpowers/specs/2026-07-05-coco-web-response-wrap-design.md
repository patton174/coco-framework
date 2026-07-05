# Coco Web Response Wrap Design

## 目标

本阶段为 `coco-feature-web` 增加正常响应自动包装能力，让业务 Controller 只返回业务数据，框架负责输出统一的 `CocoApiResponse` 结构。

该能力补齐当前 Web 模块的另一半边界：异常响应已经通过 `CocoWebExceptionHandler` 统一返回，正常响应应通过 Spring MVC 的响应体增强机制统一处理。

## 用户体验

业务代码默认不需要显式调用包装工具：

```java
@GetMapping("/users/{id}")
public UserDetail getUser(@PathVariable Long id) {
    return userService.getUser(id);
}
```

实际响应结构：

```json
{
  "success": true,
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 1,
    "name": "Coco"
  },
  "traceId": "trace-id",
  "path": "/users/1"
}
```

不需要包装的接口使用注解显式声明：

```java
@CocoIgnoreResponseWrap
@GetMapping("/health")
public Map<String, String> health() {
    return Map.of("status", "UP");
}
```

`@CocoIgnoreResponseWrap` 适合放在 Controller 类或方法上，因为它描述的是 Web 返回边界。普通业务工具方法不应该通过注解控制响应包装。

## 模块边界

实现放在 `coco-features/coco-feature-web`。

原因：

- `ResponseBodyAdvice`、`ServerHttpRequest`、`HttpMessageConverter` 都属于 Web/MVC 技术边界。
- `CocoApiResponse` 当前已经属于 Web 响应模型，本阶段不额外抽到 common。
- `coco-common-i18n` 只负责消息解析，不知道 Servlet 或 MVC。
- `coco-common-exception` 只负责异常语义，不负责 HTTP 响应格式。

新增包建议：

```text
io.github.coco.feature.web.response
```

新增类型：

```text
CocoResponseWrapAdvice
CocoIgnoreResponseWrap
CocoResponseWrapProperties
CocoSystemCodeProvider
CocoSystemCodes
```

`CocoApiResponse` 保持在同一包下，并补充 success 工厂方法。

## 配置

配置挂在现有 `coco.web` 下：

```yaml
coco:
  web:
    response-wrap:
      enabled: true
      success-message-code: coco.web.response.success
```

默认启用，符合框架“引入即可用”的定位。业务项目只在不需要该能力时显式关闭：

```yaml
coco:
  web:
    response-wrap:
      enabled: false
```

属性说明：

- `enabled`：是否注册正常响应包装。
- `success-message-code`：成功消息的国际化编码，默认 `coco.web.response.success`。

成功消息文本放在 `coco-feature-web-messages*.properties`，由 `CocoMessageService` 解析。

成功、参数错误、未认证、无权限、资源不存在、资源冲突和内部错误等系统响应码由 `CocoSystemCodeProvider`
提供，默认分别为 `200`、`400`、`401`、`403`、`404`、`409`、`500`。业务项目需要覆盖系统响应码时，声明
自己的 `CocoSystemCodeProvider` Bean 即可：

```java
@Bean
CocoSystemCodeProvider cocoSystemCodeProvider() {
    return CocoSystemCodes.builder()
            .success(0)
            .invalidArgument(100400)
            .notFound(100404)
            .build();
}
```

## 包装规则

会包装：

- 普通 `@RestController` 或 `@ResponseBody` 方法返回的对象。
- `null` 返回值，包装为 `data: null`。
- 集合、Map、record、POJO 等普通 JSON 响应。

不会包装：

- 已经是 `CocoApiResponse` 的返回值。
- 方法或类上标注 `@CocoIgnoreResponseWrap` 的返回值。
- `ResponseEntity<?>`，因为它通常表达调用方自定义状态码、header 或 body。
- `Resource`、`byte[]`、流式响应、下载响应。
- Spring 内部错误响应或已经由异常处理器生成的异常响应。

`String` 返回值需要单独处理。由于 Spring MVC 对 String 使用字符串消息转换器，直接返回对象会导致类型不匹配。设计上应该支持字符串返回值：将包装后的 `CocoApiResponse` 序列化为 JSON 字符串再返回。

## 数据流

正常响应：

```text
Controller 返回值
-> CocoResponseWrapAdvice
-> 判断是否应该跳过
-> CocoMessageService 解析成功消息
-> CocoSystemCodeProvider 解析成功响应码
-> CocoTraceContext 读取或创建 traceId
-> CocoApiResponse.success(...)
-> HttpMessageConverter 输出 JSON
```

异常响应保持现有流程：

```text
CocoException
-> CocoWebExceptionHandler
-> CocoMessageService 解析异常消息
-> 业务码优先，否则按异常类型、Coco 内置消息码或 HTTP 状态解析系统响应码
-> CocoApiResponse.error(...)
```

两条路径都复用 `CocoApiResponse`，但实现互不抢职责。

## 错误处理

`CocoResponseWrapProperties` 应保证空配置回到默认值。

`success-message-code` 为空时，启动阶段不强制失败；包装时使用默认值兜底，避免配置错误导致接口完全不可用。

`CocoMessageService` 缺少成功消息资源时，按 i18n 既有规则回退为消息编码。

如果响应类型无法安全包装，优先跳过，而不是破坏下载、流式和框架内部响应。

## 测试

新增或扩展测试覆盖：

- 自动配置默认注册 `CocoResponseWrapAdvice`。
- `coco.web.response-wrap.enabled=false` 时不注册 advice。
- 普通对象返回值会包装为成功 `CocoApiResponse`。
- `String` 返回值会被包装成 JSON 字符串。
- `CocoApiResponse` 返回值不会二次包装。
- 方法级 `@CocoIgnoreResponseWrap` 会跳过包装。
- 类级 `@CocoIgnoreResponseWrap` 会跳过包装。
- `ResponseEntity<?>` 不包装。
- 成功消息通过 `CocoMessageService` 从 Web 模块消息资源解析。
- 自定义 `CocoSystemCodeProvider` 可以覆盖默认成功码和默认异常码。
- 业务自定义 `CocoBusinessCode` 优先于系统默认码。
- 配置元数据包含 `coco.web.response-wrap.enabled` 和 `coco.web.response-wrap.success-message-code`。

## 本阶段不做

- 不实现非 `CocoException` 的全局异常兜底。
- 不实现参数校验异常的字段级错误模型。
- 不抽 `CocoApiResponse` 到 common。
- 不支持 WebFlux。
- 不提供静态包装工具作为主要使用方式。

这些可以在 Web 基础响应闭环稳定后继续拆成后续任务。

## 验收标准

- 引入 starter 且启用 Web 功能后，正常 REST 返回值默认输出 `CocoApiResponse`。
- 标注 `@CocoIgnoreResponseWrap` 后可以按类或方法跳过包装。
- 关闭 `coco.web.response-wrap.enabled` 后不注册包装 advice。
- 成功响应携带 `success=true`、数字成功 code、国际化成功 message、data、traceId、path。
- 异常响应现有行为不退化。
- `mvn verify` 通过。
- `mvn -DskipTests install javadoc:javadoc` 通过。
