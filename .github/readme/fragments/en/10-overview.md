## What is Coco

Most Spring Boot services end up with the same glue code. Coco ships it once, as a single starter:

- **Same response shape everywhere.** Controllers return plain objects; Coco wraps them.
- **Exceptions become clean responses.** One global handler, correct HTTP status codes.
- **Every request gets a TraceId.** It is read from or written to the `X-Trace-Id` header.
- **Common features are ready to switch on.** Rate limiting, idempotency, locks, cache, captcha, tenant isolation and more. Turn them on in YAML when you need them.
- **Your business code stays plain Spring.** Coco does not decide your user, role or permission model.
