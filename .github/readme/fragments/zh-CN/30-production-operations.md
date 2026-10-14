## 上生产前

有些默认值是为了第一次跑起来方便，不是为集群准备的。请检查这三项：

| 功能 | 默认 | 你需要做什么 |
|---|---|---|
| **SQL 防护** | 关闭 | 先检查你的 SQL，再开启 `block-attack` / `illegal-sql`。它可能拒绝一些它无法校验的合法语句。[文档](https://cocoframework.dev/features/mybatis-plus) |
| **防重放** | 内存存储，仅单进程有效 | 多实例部署时，换成 JDBC 存储或你自己的实现。Coco 不会建表，表结构由你管理。[文档](https://cocoframework.dev/features/request-security) |
| **异步日志** | 有界队列；错误日志始终同步写入 | 把 `CocoAsyncLogDropListener` 接到你的监控，就能看到被丢弃的日志。它反映过载情况，不保证日志一定送达。[文档](https://cocoframework.dev/features/infra) |

另外要注意：只有统一响应、异常处理和 TraceId 是零配置可用的。其他功能大多需要手动开启，有些还需要 Redis 或数据库。每个功能页都写明了默认值。
