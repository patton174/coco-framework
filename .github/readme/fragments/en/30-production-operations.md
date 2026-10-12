## Before production

Some defaults are chosen for a quick first run, not for a cluster. Check these three:

| Feature | Default | What to do |
|---|---|---|
| **SQL guard** | Off | Review your SQL first, then enable `block-attack` / `illegal-sql`. It may reject valid statements it cannot verify. [Docs](https://cocoframework.dev/en/features/mybatis-plus) |
| **Replay protection** | In-memory, one process only | With more than one instance, switch to the JDBC store or your own. Coco does not create tables; you manage the schema. [Docs](https://cocoframework.dev/en/features/request-security) |
| **Async logging** | Bounded queue; errors are always written synchronously | Plug a `CocoAsyncLogDropListener` into your metrics to see dropped logs. It reports overload, it does not guarantee delivery. [Docs](https://cocoframework.dev/en/features/infra) |

Also, only response wrapping, exception handling and TraceId work with zero setup. Most other features are opt-in, and some need Redis or a database. Each feature page lists its defaults.
