## Features

| Area | What you get |
|---|---|
| Web | Unified response, global exceptions, TraceId, access log, CORS, security headers, field-level validation errors |
| Request security | Signature, encryption, replay protection |
| Data | MyBatis-Plus, pagination, tenant SQL isolation, data-permission filters |
| Traffic control | Rate limiting (sliding window, token bucket), idempotency, distributed locks, scheduling |
| Platform | File storage, local and Redis cache, messaging, notification channels, captcha (image, slider, SMS) |
| Observability | Audit events, OpenAPI metadata |

All of this ships in the latest release, **v3.0.0**.

## What Coco does not do

Coco handles infrastructure. Your application keeps:

- the domain model and API design
- login and authentication
- the user, role and organization model
- database schema and migrations

Every extension point is a normal Spring `@Bean`, so you can replace any default with your own. CRUD generation, when you want it, produces ordinary Java source that you own. It does not expose entities at runtime. More in [Overview](https://cocoframework.dev/en/overview).
