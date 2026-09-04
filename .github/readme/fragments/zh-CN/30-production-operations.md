## 生产注意事项

有几项默认值是刻意保守的——首次接入的安全选择，未必适合集群。它们在你显式启用前保持关闭或进程内：

| 关注点 | 默认 | 上生产时 |
|--------|------|---------|
| **SQL 防护** | 关闭，保证既有运维 SQL 不被打断 | 先复核你的 SQL，再启用 `block-attack` / `illegal-sql`——防护可能拒绝它无法可靠校验的合法语句 |
| **防重放** | `InMemoryCocoReplayStore`，仅进程内有效 | 换成 JDBC 存储（或自己的实现），让键预留在多实例间原子。框架不执行迁移，表结构由你负责 |
| **异步日志** | 有界队列；`ERROR` 与携带异常的记录始终同步写 | 替换 `CocoAsyncLogDropListener`，把丢弃计数接入你的监控。这是过载可观测性，不是投递保证 |

**→ [SQL 防护](https://patton174.github.io/coco-framework/features/mybatis-plus)** · **[防重放](https://patton174.github.io/coco-framework/features/request-security)** · **[日志与基础设施](https://patton174.github.io/coco-framework/features/infra)**
