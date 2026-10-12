## 端到端验收

业务和 HTTP 验收测试在 [`coco-admin/framework-acceptance`](https://github.com/patton174/coco-admin/tree/main/framework-acceptance)：

- **Basic**（无数据库）：响应、异常、i18n、Trace、签名、加密、防重放。
- **Full**（H2 + MyBatis-Plus）：安全断言、租户 SQL 隔离、数据权限过滤、审计事件。
