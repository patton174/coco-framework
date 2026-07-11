## 能力范围

<table>
  <tr>
    <td width="33%">
      <p><img src="https://img.shields.io/badge/Web-Servlet%20Runtime-2563eb?style=flat-square" alt="Web"/></p>
      <strong>Web 运行时</strong><br/>
      统一响应、异常响应、链路标识、请求上下文、访问日志、请求签名、请求加密，以及进程内或共享 JDBC 防重放。
    </td>
    <td width="33%">
      <p><img src="https://img.shields.io/badge/Security-Context%20Foundation-7c3aed?style=flat-square" alt="Security"/></p>
      <strong>安全基础</strong><br/>
      安全上下文门面、解析 SPI、Web 上下文桥接、可信请求头适配、断言工具和上下文传播原语。
    </td>
    <td width="33%">
      <p><img src="https://img.shields.io/badge/Data-MyBatis--Plus-0891b2?style=flat-square" alt="Data"/></p>
      <strong>数据集成</strong><br/>
      MyBatis-Plus 拦截器组装、分页、SQL 防护、租户 SQL 隔离和数据权限 SQL 条件。
    </td>
  </tr>
  <tr>
    <td width="33%">
      <p><img src="https://img.shields.io/badge/Config-Feature%20Control-f97316?style=flat-square" alt="Feature Control"/></p>
      <strong>功能控制</strong><br/>
      父 POM、BOM、单 starter、声明式功能选择、依赖感知的功能计划和运行时功能条件。
    </td>
    <td width="33%">
      <p><img src="https://img.shields.io/badge/Audit-Event%20Pipeline-16a34a?style=flat-square" alt="Audit"/></p>
      <strong>审计流水线</strong><br/>
      默认结构化审计日志，以及格式化器和记录器 SPI、发布器、失败策略和访问日志适配器。
    </td>
    <td width="33%">
      <p><img src="https://img.shields.io/badge/Codegen-Source%20Generation-475569?style=flat-square" alt="Codegen"/></p>
      <strong>显式源码生成</strong><br/>
      可替换模板生成器、内置 CRUD 源码模板和安全写入；隐藏式运行时 CRUD Controller 明确不在范围内。
    </td>
  </tr>
</table>

## 边界

<table>
  <thead>
    <tr>
      <th width="50%">Coco 负责封装</th>
      <th width="50%">业务应用负责</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>starter 装配和自动配置组合</td>
      <td>领域模型和 API 语义</td>
    </tr>
    <tr>
      <td>功能启停、依赖传播和运行时功能门控</td>
      <td>Controller 形态和服务编排</td>
    </tr>
    <tr>
      <td>统一响应、类型化异常、国际化、链路上下文和访问日志</td>
      <td>事务边界和自定义持久化设计</td>
    </tr>
    <tr>
      <td>请求签名、请求加密、防重放、安全上下文生命周期桥接、审计钩子、租户 SQL 和数据权限 SQL</td>
      <td>认证提供方、用户模型、组织模型、角色模型和生成后的 CRUD 代码</td>
    </tr>
  </tbody>
</table>

CRUD 应该走代码生成，而不是运行时暴露实体。生成后的代码应当是可读的 Java 源码，业务项目可以保留、修改、删除或替换。

## 扩展边界

<table>
  <thead>
    <tr>
      <th width="25%">领域</th>
      <th width="38%">已交付边界</th>
      <th width="37%">业务应用或路线图负责</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>Replay</td>
      <td>进程内默认实现、显式共享 JDBC 参考实现、原子键占用、过期清理和可替换 Store SPI。</td>
      <td>数据库迁移与可用性、集群时钟同步、业务事务和 exactly-once 语义。</td>
    </tr>
    <tr>
      <td>Security</td>
      <td>上下文门面、解析 SPI、Servlet 上下文桥接、可信请求头适配、断言工具和上下文传播原语。</td>
      <td>认证提供方、RBAC/ABAC 模型、会话、令牌和用户存储。</td>
    </tr>
    <tr>
      <td>Audit</td>
      <td>事件契约、发布器、默认尽力而为的结构化日志、格式化器和记录器 SPI、失败策略和访问日志适配器。</td>
      <td>数据库落库、MQ 投递、合规报表和保留策略。</td>
    </tr>
    <tr>
      <td>OpenAPI</td>
      <td>元数据提供器、配置边界，以及业务项目已引入 SpringDoc 时的可选元数据适配器。</td>
      <td>文档渲染、UI 集成和接口级文档策略。</td>
    </tr>
    <tr>
      <td>Codegen</td>
      <td>生成器 SPI、内置 CRUD 模板、显式 Maven goal、覆盖保护和自定义模板位置。</td>
      <td>项目专属模板、业务规则，以及生成后的 CRUD 源码维护。</td>
    </tr>
  </tbody>
</table>
