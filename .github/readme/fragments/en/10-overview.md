## Why Coco

Every product has its own domain. Much of the setup can be shared.

| Work you may be repeating | What Coco provides |
|---|---|
| Copying response, exception and logging code | Starter integration for response wrapping, global exceptions and TraceId |
| Managing growing configuration | YAML / annotation feature selection with a consistent build and runtime plan |
| Moving from one instance to many | Replaceable rate-limit, idempotency and lock stores; explicitly configure Redis or other services |
| Adapting your domain to a framework | Ordinary Spring controllers; you own authentication, organizations and transactions |

| **17** feature IDs on main | **1** starter | **Java 17+** | **Apache-2.0** |
|---|---|---|---|
| Includes Codegen compatibility | With parent / BOM | Application compile target | Open-source license |

> Latest stable: **v2.0.2**. This README describes main; new cache, notification and captcha capabilities are not all in the stable release. External services and multi-instance stores need explicit configuration; not every feature works without setup.

## Less assembly to maintain

**Manual assembly:** illustrative responsibilities, not a runnable example.

```java
@Configuration
class WebInfrastructure {
    // ResponseBodyAdvice: response consistency
    // RestControllerAdvice: exception handling
    // TraceId filter: request correlation
    // Access-log filter: request logging
    // Context propagation: asynchronous work
}
```

**With Coco:** use the parent and one starter below. The framework handles those integration points; business code stays ordinary Java / Spring.

## Choosing an approach

| Dimension | Assemble with Spring Boot | Coco Framework |
|---|---|---|
| Default behavior | Compose response, exception and logging conventions | Response wrapping, exceptions and TraceId connected by default |
| Capabilities | Select and integrate ecosystem libraries | Combine infrastructure with feature switches |
| Extensions | Spring beans and extension interfaces | Spring extension points plus focused SPIs |
| Business model | Design your own | Design your own users, roles and organizations |
| Engineering governance | Establish your team's workflow | Repository CI, Agent review and protected merges |

Choose based on dependencies, domain boundaries and deployment needs. This compares integration approaches, not benchmark results.

---
