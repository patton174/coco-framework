## 能力范围

<table>
  <tr>
    <td width="33%">
      <p><img src="https://img.shields.io/badge/Web-Servlet%20Runtime-2563eb?style=flat-square" alt="Web"/></p>
      <strong>Web 运行时</strong><br/>
      统一响应、异常响应、链路标识、请求上下文、访问日志、请求签名、请求加密和防重放。
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
      <p><img src="https://img.shields.io/badge/Reliability-Flow%20Control-be123c?style=flat-square" alt="Reliability"/></p>
      <strong>流控与可靠性</strong><br/>
      限流、幂等、分布式锁、调度——每项都有进程内默认实现和可替换的存储 SPI。
    </td>
    <td width="33%">
      <p><img src="https://img.shields.io/badge/Platform-Storage%20%26%20Audit-16a34a?style=flat-square" alt="Platform"/></p>
      <strong>平台能力</strong><br/>
      对象存储 SPI（含内容寻址的本地参考实现）、结构化审计流水线和 OpenAPI 元数据。
    </td>
    <td width="33%">
      <p><img src="https://img.shields.io/badge/Config-Feature%20Control-f97316?style=flat-square" alt="Feature Control"/></p>
      <strong>功能控制</strong><br/>
      父 POM、BOM、单 starter、声明式功能选择、依赖感知的功能计划和运行时功能条件。
    </td>
  </tr>
</table>

**→ [能力参考](https://patton174.github.io/coco-framework/features/web-runtime)** —— 逐个功能的配置项与 SPI。

## 边界

框架负责**基础设施**；业务应用负责**领域模型、API 语义、认证提供者、用户/角色/组织模型**。

这条边界是刻意的：框架不猜测你的业务，只把重复的、跨项目一致的基础设施做成可替换的黑盒。每个 SPI 都可以用一个 `@Bean` 覆盖为你自己的实现。

CRUD 属于代码生成，不是运行时实体暴露——生成的是业务项目可保留、可修改、可删除的普通 Java 源码。

**→ [边界与设计哲学](https://patton174.github.io/coco-framework/overview)** —— 双方各自负责什么，以及什么明确不在范围内。
