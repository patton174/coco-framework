## Capabilities

<table>
  <tr>
    <td width="33%"><strong>🌐 Consistent web requests</strong><p>Give every endpoint consistent behavior.</p><ul><li>Responses and exceptions</li><li>TraceId and access logs</li><li>Signatures, encryption and replay protection</li></ul></td>
    <td width="33%"><strong>🗃 Data and permissions</strong><p>Integrate isolation into the query path.</p><ul><li>MyBatis-Plus and pagination</li><li>Tenant SQL isolation</li><li>Data-permission conditions</li></ul></td>
    <td width="33%"><strong>⏱ Traffic and reliability</strong><p>Put boundaries around frequent and repeated requests.</p><ul><li>Rate limiting and idempotency</li><li>Locks and scheduling</li><li>Replaceable stores</li></ul></td>
  </tr>
  <tr>
    <td width="33%"><strong>📦 Files and platform</strong><p>Let shared services grow with the application.</p><ul><li>Files and cache</li><li>Messaging and notifications</li><li>Captcha</li></ul></td>
    <td width="33%"><strong>🔎 Audit and visibility</strong><p>Leave useful evidence of important actions.</p><ul><li>Structured audit events</li><li>Logs and context</li><li>OpenAPI metadata</li></ul></td>
    <td width="33%"><strong>🧩 Features and extensions</strong><p>Ship the capabilities you need.</p><ul><li>Declarative feature selection</li><li>Build-time pruning</li><li>Bean and SPI overrides</li></ul></td>
  </tr>
</table>

## Boundary

Coco owns **infrastructure**. Your application owns the **domain model, API semantics, authentication provider, and user/role/organization models**.

Use Spring beans and the documented extension interfaces to replace integrations. CRUD generation produces ordinary Java source that your application owns; it does not expose entities as APIs at runtime.

**→ [Boundary and design philosophy](https://cocoframwork.dev/en/overview)** — responsibilities and scope.
