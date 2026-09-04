## Production notes

A few defaults are deliberately conservative, because the safe choice for a first adoption is not the right choice for a cluster. Each is off or process-local until you opt in:

| Concern | Default | For production |
|---------|---------|----------------|
| **SQL guard** | Disabled, so existing maintenance SQL keeps working | Review your SQL, then enable `block-attack` / `illegal-sql` — the guard may reject legitimate statements it cannot validate |
| **Replay protection** | `InMemoryCocoReplayStore`, process-local | Switch to the JDBC store (or your own) so reservations are atomic across instances. Coco runs no migrations — you own the schema |
| **Async logging** | Bounded queue; `ERROR` and exceptions always synchronous | Replace `CocoAsyncLogDropListener` to feed drop counts into your metrics. This is overload observability, not durable delivery |

**→ [SQL guard](https://patton174.github.io/coco-framework/features/mybatis-plus)** · **[Replay protection](https://patton174.github.io/coco-framework/features/request-security)** · **[Logging and infrastructure](https://patton174.github.io/coco-framework/features/infra)**
