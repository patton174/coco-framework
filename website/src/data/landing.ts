type Pair = readonly [string, string];
interface LandingContent {
  slogan: string; positioning: string; description: string; intro: string;
  start: string; explore: string; release: string; versionNote: string;
  starterTitle: string; setupLink: string; factsLabel: string; featurePlanLink: string;
  samplesLabel: string; samples: readonly Pair[];
  architectureLink: string; guideLink: string;
  stats: readonly Pair[]; painTitle: string; painIntro: string; pains: readonly Pair[];
  featuresTitle: string; featuresIntro: string;
  features: readonly (readonly [string, string, readonly string[], string])[];
  compareTitle: string; before: string; after: string; beforeText: string; afterText: string;
  scopeNote: string; architectureTitle: string; layers: readonly Pair[];
  ecosystemTitle: string; ecosystem: readonly (readonly [string, string, string])[];
  dependency: string; quickTitle: string; steps: readonly Pair[];
  communityTitle: string; communityText: string; community: readonly string[]; star: string;
}

// Both locales implement the same schema while retaining literal feature paths.
export const landing = {
  zh: {
    slogan: '少写基础设施，多写业务。',
    positioning: 'Spring Boot 服务端基础设施框架',
    description: 'Coco Framework 为 Spring Boot 应用提供统一响应、异常处理、限流、幂等、多租户与数据权限。一个 Starter 接入，按需启用，保留普通 Java 业务代码。',
    intro: '一个 Starter 接入统一响应、异常处理与 TraceId。按需选择数据、流控和平台能力，业务代码仍是普通 Java 与 Spring。',
    start: '快速开始', explore: '功能导览', release: '稳定版 v2.0.3',
    starterTitle: '从一个 Starter 接入', setupLink: '完整接入步骤', factsLabel: '项目基本信息',
    featurePlanLink: '功能开关与配置', architectureLink: '架构与设计边界', guideLink: '打开快速开始指南',
    samplesLabel: '接入示例',
    samples: [['pom.xml', '引入 Starter'], ['application.yml', '按需开启幂等'], ['OrderController.java', '业务接口仍是普通 Spring']],
    versionNote: '本页介绍当前主干的 17 项功能标识，包含 Codegen 兼容项；2.0.3 已发布，具体开关与配置请查阅文档。',
    stats: [['17', '主干功能标识'], ['1', 'Starter 接入'], ['Java 17+', '业务编译目标'], ['Apache-2.0', '开源许可']],
    painTitle: '这些工作，你不必再做一遍。',
    painIntro: '每个项目都有自己的业务，但很多准备工作是相同的。',
    pains: [
      ['又在复制基础代码？', '统一响应、异常处理和 TraceId 自动接入，少维护一套跨项目重复代码。'],
      ['配置越来越难梳理？', '用 YAML 或注解选择能力，构建时裁剪关闭的模块，运行时沿用同一份功能计划。'],
      ['从单机走向多实例？', '限流、幂等和锁提供可替换的存储接口；结合部署方式显式选择 Redis 等实现。'],
      ['框架开始约束业务？', 'Controller 还是普通 Spring 代码。用户、组织、认证提供者和事务边界由你决定。'],
    ],
    featuresTitle: '功能导览',
    featuresIntro: '默认值有明确边界，需要定制时有清晰的替换入口。',
    features: [
      ['Web 请求', '每个接口行为一致', ['统一响应与异常', 'TraceId 日志', '签名与防重放'], '/features/web-runtime'],
      ['数据与权限', '隔离落在查询链路上', ['MyBatis-Plus 分页', '租户 SQL 隔离', '数据权限'], '/features/mybatis-plus'],
      ['流控与可靠性', '高频与重复请求有边界', ['限流与幂等', '锁与调度', '存储可替换'], '/features/rate-limit'],
      ['文件与平台', '通用能力随项目生长', ['文件存储', '两级缓存与消息', '通知与验证码'], '/features/storage'],
      ['审计与可观测性', '重要操作留下线索', ['审计事件', '日志上下文传播', 'OpenAPI'], '/features/audit'],
      ['功能与扩展', '只带需要的能力', ['功能开关', '构建期裁剪', 'Bean 与 SPI 替换'], '/feature-toggles'],
    ],
    compareTitle: '少维护一套装配，多交付一个功能。',
    before: '手动组装时', after: '接入 Coco 后',
    beforeText: '以下为职责示意：每个项目都要维护这些集成点。',
    afterText: '使用 coco-parent 管理版本后，引入 Starter。业务仍使用普通 Spring。',
    scopeNote: '统一响应、全局异常与 TraceId 默认生效。数据库、Redis、认证、租户及其他外部服务仍需按需配置。',
    architectureTitle: '清晰分层，业务保持自由。',
    layers: [['业务应用', '领域模型 · Controller · 事务'], ['Starter', '统一接入 · 自动装配'], ['Features', '按需选择的基础设施能力'], ['Foundation', '公共契约 · 上下文 · 日志']],
    ecosystemTitle: '一个生态，三份清晰的职责。',
    ecosystem: [
      ['Framework', '服务端基础设施。独立运行，不依赖 Admin 或 Generate。', 'coco-framework'],
      ['Admin', '基于 Framework 的 ERP 产品，持有用户、组织与业务流程。', 'coco-admin'],
      ['Generate', '开发期生成普通源码，生成结果由业务仓库维护。', 'coco-generate'],
    ],
    dependency: 'Admin → Framework（运行时）；Generate → 业务源码（开发期）。',
    quickTitle: '从一个接口开始。',
    steps: [
      ['引入依赖', '采用 coco-parent，添加 Starter；版本以正式发布记录为准。'],
      ['选择能力', '通过 YAML 调整功能开关，配置需要的数据库或外部服务。'],
      ['启动应用', '编写普通 Spring Controller，运行 Spring Boot 应用。'],
      ['验证响应', '请求接口，检查统一响应、异常输出与 TraceId。'],
    ],
    communityTitle: '社区与贡献',
    communityText: '遇到接入问题、想补一个能力，或者发现文档可以更清楚？欢迎一起完善 Coco。',
    community: ['提问与讨论', '报告问题', '参与贡献'],
    star: '如果 Coco 对你有帮助，欢迎在 GitHub 上 Star。',
  },
  en: {
    slogan: 'Less infrastructure. More product.',
    positioning: 'Server infrastructure for Spring Boot',
    description: 'Coco Framework brings consistent responses, exception handling, rate limiting, idempotency, multi-tenancy and data permissions to Spring Boot. One starter, optional features, plain Java business code.',
    intro: 'One Starter connects responses, exception handling and TraceId. Choose data, reliability and platform features as needed. Your business code stays ordinary Java and Spring.',
    start: 'Quick start', explore: 'Explore features', release: 'Stable v2.0.3',
    starterTitle: 'Connect with one Starter', setupLink: 'Full setup guide', factsLabel: 'Project information',
    featurePlanLink: 'Feature switches and configuration', architectureLink: 'Architecture and boundaries', guideLink: 'Open the quick-start guide',
    samplesLabel: 'Setup examples',
    samples: [['pom.xml', 'Add the Starter'], ['application.yml', 'Enable idempotency when needed'], ['OrderController.java', 'Endpoints stay ordinary Spring']],
    versionNote: 'This page covers 17 feature identifiers on main, including the Codegen compatibility feature. Version 2.0.3 is available; see the docs for module switches and configuration.',
    stats: [['17', 'Feature identifiers on main'], ['1', 'Starter dependency'], ['Java 17+', 'Application compile target'], ['Apache-2.0', 'Open-source license']],
    painTitle: 'You have done this work before.',
    painIntro: 'Every product is different. Much of the setup is not.',
    pains: [
      ['Copying the same setup again?', 'Response wrapping, exception handling and TraceId integration come together, reducing code you maintain across projects.'],
      ['Configuration getting tangled?', 'Choose features in YAML or annotations. Build-time pruning and runtime activation follow the same feature plan.'],
      ['Moving beyond one instance?', 'Rate limiting, idempotency and locks expose replaceable stores. Explicitly choose Redis or another implementation for your deployment.'],
      ['A framework dictating your domain?', 'Controllers stay ordinary Spring code. You own users, organizations, authentication providers and transactions.'],
    ],
    featuresTitle: 'Feature guide',
    featuresIntro: 'Explicit defaults, documented boundaries and clear replacement points.',
    features: [
      ['Web requests', 'Consistent behavior on every endpoint', ['Responses and errors', 'TraceId logs', 'Signing and replay guard'], '/features/web-runtime'],
      ['Data and permissions', 'Isolation lives in the query path', ['MyBatis-Plus paging', 'Tenant SQL isolation', 'Data permissions'], '/features/mybatis-plus'],
      ['Traffic and reliability', 'Limits for hot and repeated requests', ['Rate limit and idempotency', 'Locks and scheduling', 'Swappable stores'], '/features/rate-limit'],
      ['Files and platform', 'Shared services that grow with you', ['File storage', 'Two-level cache, messaging', 'Notifications, captcha'], '/features/storage'],
      ['Audit and visibility', 'Key actions leave a trail', ['Audit events', 'Log context', 'OpenAPI'], '/features/audit'],
      ['Features and extensions', 'Ship only what you need', ['Feature switches', 'Build-time pruning', 'Bean and SPI overrides'], '/feature-toggles'],
    ],
    compareTitle: 'Less assembly to maintain. More features to ship.',
    before: 'Manual assembly', after: 'With Coco',
    beforeText: 'Illustrative responsibilities: each project maintains these integration points.',
    afterText: 'Use coco-parent for version management, then add the starter. Business code stays ordinary Spring.',
    scopeNote: 'Response wrapping, exception handling and TraceId work by default. Databases, Redis, authentication, tenants and other external services still need explicit configuration.',
    architectureTitle: 'Clear layers. Room for your domain.',
    layers: [['Business application', 'Domain · Controllers · Transactions'], ['Starter', 'One entry point · Auto-configuration'], ['Features', 'Infrastructure selected for your application'], ['Foundation', 'Contracts · Context · Logging']],
    ecosystemTitle: 'One ecosystem. Three clear responsibilities.',
    ecosystem: [
      ['Framework', 'Server infrastructure. Independent of Admin and Generate.', 'coco-framework'],
      ['Admin', 'An ERP product on Framework, owning users, organizations and business workflows.', 'coco-admin'],
      ['Generate', 'Generates ordinary source at development time. Your business repository owns the output.', 'coco-generate'],
    ],
    dependency: 'Admin → Framework at runtime; Generate → business source at development time.',
    quickTitle: 'Start with one endpoint.',
    steps: [
      ['Add dependencies', 'Use coco-parent and add the starter. Choose a version from the release history.'],
      ['Select features', 'Adjust feature switches in YAML and configure the database or services you need.'],
      ['Run the application', 'Write an ordinary Spring controller and start your Spring Boot application.'],
      ['Check the response', 'Call the endpoint and inspect the response envelope, errors and TraceId.'],
    ],
    communityTitle: 'Community and contributions',
    communityText: 'Need help integrating, have a feature in mind, or found a confusing page? Help us make Coco better.',
    community: ['Ask and discuss', 'Report an issue', 'Contribute'],
    star: 'If Coco helps your work, consider starring the project on GitHub.',
  },
} as const satisfies Record<'zh' | 'en', LandingContent>;
