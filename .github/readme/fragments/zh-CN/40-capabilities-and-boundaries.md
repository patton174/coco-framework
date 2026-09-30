## 能力范围

<table>
  <tr>
    <td width="33%"><strong>🌐 Web 请求</strong><p>让每个接口遵循一致约定。</p><ul><li>统一响应与异常</li><li>TraceId 与访问日志</li><li>签名、加密与防重放</li></ul></td>
    <td width="33%"><strong>🗃 数据与权限</strong><p>将数据隔离放入查询链路。</p><ul><li>MyBatis-Plus 与分页</li><li>租户 SQL 隔离</li><li>数据权限条件</li></ul></td>
    <td width="33%"><strong>⏱ 流控与可靠性</strong><p>为频繁或重复的请求划清边界。</p><ul><li>限流与幂等</li><li>锁与调度</li><li>存储实现可替换</li></ul></td>
  </tr>
  <tr>
    <td width="33%"><strong>📦 文件与平台</strong><p>通用服务能力随应用成长。</p><ul><li>文件存储与缓存</li><li>消息与通知</li><li>验证码</li></ul></td>
    <td width="33%"><strong>🔎 审计与可观测性</strong><p>为重要操作留下线索。</p><ul><li>结构化审计事件</li><li>日志与上下文</li><li>OpenAPI 元数据</li></ul></td>
    <td width="33%"><strong>🧩 功能与扩展</strong><p>只带上需要的能力。</p><ul><li>声明式功能开关</li><li>构建期裁剪</li><li>Bean 与 SPI 替换</li></ul></td>
  </tr>
</table>

## 边界

框架负责**基础设施**；业务应用负责**领域模型、API 语义、认证提供者、用户/角色/组织模型**。

这条边界是刻意的：框架不猜测你的业务，只把重复的、跨项目一致的基础设施做成可替换的黑盒。每个 SPI 都可以用一个 `@Bean` 覆盖为你自己的实现。

CRUD 属于代码生成，不是运行时实体暴露——生成的是业务项目可保留、可修改、可删除的普通 Java 源码。

**→ [边界与设计哲学](https://cocoframwork.dev/overview)** —— 双方各自负责什么，以及什么明确不在范围内。
