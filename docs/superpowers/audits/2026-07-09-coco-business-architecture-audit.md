# Coco Framework 业务架构审计 —— 客户端 API / 分层 / 契约

- **审计日期：** 2026-07-09
- **审计范围：** coco-api-core（5 类）+ coco-samples/coco-sample-basic（14 主类 + 2 测试）+ coco-common-exception 的 `CocoBusinessCode` / `CocoErrorCode` / `CocoCommonErrorCode`
- **审计方法：** 通读 sample 全部源文件 + coco-api-core 全部源文件 + 与 AGENTS.md "business-facing simplicity / one starter / clear override points" 原则对齐
- **结论先说：** sample 是教科书式的 DDD 四层架构，领域层**真正做到了 framework-agnostic**；但 coco-api-core 的 5 个契约类中有 3 个**几乎没被任何业务使用**，是**典型的"为未来可能性过度设计"**。同时 sample 暴露了几个会被复制到所有业务项目里的反模式（`@Transactional` 缺失、i18n bundle 命名/内容错位、Controller 4 个 secure 端点只是 URL 装饰）。

---

## 总览矩阵

| 维度 | 健康 | 失衡 | 严重问题 |
| --- | --- | --- | --- |
| sample 四层架构 | ✅ | | |
| 领域层 framework-agnostic | ✅（核心无 Spring/Coco 依赖） | | |
| 仓储契约接口 | | ⚠️ ISP 违反 | `SampleOrderRepository` 混入产品/订单双职责 |
| 应用服务事务边界 | | ❌ 缺失 | `@Transactional` 完全没有 |
| Controller 与应用服务分层 | ✅ | | |
| Coco 异常契约在领域层使用 | ✅（仅 `CocoBusinessCode` 接口） | | |
| coco-api-core 契约使用率 | | ❌ 严重失衡 | `CocoConfigurer` 业务侧**0 个实现** |
| coco-api-core 是否过度设计 | | ⚠️ 3/5 类近乎空 SPI | `CocoFeatureRegistry` 内部 scratch 对象泄漏为接口 |
| sample i18n bundle 命名 | | ❌ 默认与 en_US 内容相同 / 与文件名不符 | 1 处 |
| Controller secure 端点设计 | | ⚠️ 4 个 POST 仅 URL 不同 | 业务真正鉴权靠过滤器 |
| 业务错误码在领域层实现 | ✅（`SampleBusinessErrorCode implements CocoBusinessCode`） | | |
| README 与示例一致性 | ✅ | | |

---

## 1. Sample 分层架构剖析

### 1.1 分层与依赖图

```
interfaces.rest        ←  import: SampleOrderApplicationService
  (Controller + DTOs)
        │
        ▼
application.order      ←  import: SampleOrderRepository, SampleBusinessErrorCode,
  (Service + 异常编排)         CocoCommonErrorCode
        │
        ▼
domain.order           ←  import: CocoBusinessCode (接口契约，仅此一处)
  (Entity + Repository契约 + 业务码)
        ▲
        │
infrastructure.order   ←  import: SampleOrderRepository (实现)
  (InMemory Repository 实现)
```

### 1.2 每层的 Coco 依赖（精确统计）

| 层 | Spring 依赖 | Coco 依赖 | framework-agnostic? |
| --- | --- | --- | --- |
| domain.order | **0** | `CocoBusinessCode` 接口（仅 `SampleBusinessErrorCode` 实现） | ✅ 几乎纯领域 |
| application.order | `@Service` | `CocoCommonErrorCode.INVALID_ARGUMENT.request()` 抛参 | ⚠️ 但合理 |
| infrastructure.order | `@Repository` | `SampleBusinessErrorCode.PRODUCT_NOT_FOUND.notFound()` / `INSUFFICIENT_STOCK.conflict()` | ⚠️ 反模式 |
| interfaces.rest | `@RestController` / `@RequestMapping` / etc. | **0** | ✅ 仅 Spring Web |

**关键观察：**
- **领域层确实做到了 framework-agnostic**：domain 包内只有 `CocoBusinessCode` 一个接口依赖（4 行代码）。`SampleOrder`、`SampleProduct` 是 record；`SampleOrderRepository` 是纯 Java 接口。这与 AGENTS.md 第 12 行 "do not hide CRUD behind runtime magic, do not expose entities automatically as APIs" **完全一致**。
- **基础设施层却抛业务异常**：见 § 2.3，这是真正的反模式。
- **应用层把 Coco 异常契约当 utility 用**：`CocoCommonErrorCode.INVALID_ARGUMENT.request(fieldName)` —— 这种"用框架枚举抛参校验错误"的模式可读性其实**低于**一个简单的 `throw new IllegalArgumentException(fieldName)`。但作者选择了复用框架的统一异常体系，所以这是**有意识的取舍**，不是问题。

---

## 2. 主要发现（按严重度）

### 【业务架构 / 高】B1. `SampleOrderApplicationService.createOrder` 缺 `@Transactional` 边界

**位置：** `coco-samples/coco-sample-basic/src/main/java/io/github/coco/sample/basic/application/order/SampleOrderApplicationService.java:63-70`

```java
public SampleOrder createOrder(String buyerName, String sku, int quantity) {
    String checkedBuyerName = requireText(buyerName, "buyerName");
    String checkedSku = requireText(sku, "sku");
    if (quantity <= 0) {
        throw SampleBusinessErrorCode.INVALID_ORDER_QUANTITY.request("quantity");
    }
    return this.orderRepository.createOrder(checkedBuyerName, checkedSku, quantity);
}
```

`InMemorySampleOrderRepository.createOrder` 用 `synchronized` 解决并发；如果替换为 MyBatis-Plus 实现（README 强调的迁移路径），**库存扣减 + 订单插入将不在同一事务里** —— 高并发下出现"超卖"或"订单存在但库存未扣"的脏状态。

**问题：**
- 业务方如果照搬 sample 复制这套 Application Service 结构，事务边界就成了"靠基础设施层的隐式约定"；
- 实际业务代码必然要加 `@Transactional`，但 sample 没演示；
- 与 AGENTS.md "Business developers are expected to understand normal Java, Spring Boot, ... Coco should reduce boilerplate, not remove engineering ownership" 表面一致（业务方应自己加），但**示例应展示"正确做法"**。

**建议（S）：** 给 `SampleOrderApplicationService` 加 `@Transactional`（在仓储接口上配，或类级别），并在 JavaDoc 标注 "业务方应自行管理事务边界；内存实现靠 synchronized 演示，生产实现必须依赖数据库事务"。

---

### 【业务架构 / 高】B2. `InMemorySampleOrderRepository` 抛业务异常，违反"仓储只做持久化"分层原则

**位置：** `coco-samples/coco-sample-basic/src/main/java/io/github/coco/sample/basic/infrastructure/order/InMemorySampleOrderRepository.java:74-89`

```java
@Override
public synchronized SampleOrder createOrder(String buyerName, String sku, int quantity) {
    ProductState product = this.products.get(sku);
    if (product == null) {
        throw SampleBusinessErrorCode.PRODUCT_NOT_FOUND.notFound(sku);  // ← 仓储层抛业务异常
    }
    if (product.availableStock < quantity) {
        throw SampleBusinessErrorCode.INSUFFICIENT_STOCK.conflict(sku, product.availableStock, quantity);
    }
    product.availableStock = product.availableStock - quantity;
    ...
}
```

**问题：** 仓储实现中抛 `PRODUCT_NOT_FOUND`、`INSUFFICIENT_STOCK` —— 这两个本质上是**业务校验**（产品是否存在、库存是否充足），应该由 Application Service 在调用仓储前完成。

按 sample 自己的分层意图：
- `domain.SampleOrderRepository` 接口只声明"我能创建订单"，**不承诺校验**；
- `infrastructure.InMemorySampleOrderRepository` 是该接口的内存实现，**只该做内存操作**；
- `application.SampleOrderApplicationService` 才该做"下单前置校验"。

**现在的实现把校验逻辑下沉到仓储，导致：**
1. 业务方想复用 sample 的 application 层结构时，照搬过去会形成"仓储抛业务异常"的反模式；
2. 单元测试 application 服务时，无法在不 mock 仓储的前提下覆盖"库存不足"分支；
3. **违反 DDD "Repository should not enforce business rules" 原则**。

**建议（M）：** 把 `PRODUCT_NOT_FOUND` / `INSUFFICIENT_STOCK` 校验移到 `SampleOrderApplicationService.createOrder`。仓储实现只保留 `Optional<ProductState> findBySku(sku)` / `decrementStock(productState, quantity)` 等纯数据访问操作。

---

### 【API 设计 / 高】B3. coco-api-core 中 3/5 契约类在生产代码中**0 实现**

**位置：**
- `coco-api/coco-api-core/.../CocoConfigurer.java`
- `coco-api/coco-api-core/.../CocoFeatureRegistry.java`
- `coco-api/coco-api-core/.../DefaultCocoFeatureRegistry.java`

**全仓搜索结果：**
| 契约 | 生产引用方 | 测试引用方 | 业务项目实现 |
| --- | --- | --- | --- |
| `CocoConfigurer` | `coco-config` (2 处) | `coco-config` 测试 (1 处) | **0**（sample 没实现） |
| `CocoFeatureRegistry` | `coco-config` (1 处), `coco-feature-runtime` (3 处) | `coco-feature-runtime` 测试 | **0** |
| `DefaultCocoFeatureRegistry` | `coco-config` (1 处) | `coco-api-core` 测试 | **0**（隐藏实现类） |
| `CocoFeatures` 注解 | `coco-config` (1 处) | `coco-config` 测试 | **0** |
| `CocoFeature` 枚举 | 26 处 | 多 | 枚举，业务不实现 |

**问题：**

1. **`CocoConfigurer` 是个被假装是 SPI 的空 SPI**：
   - 整个接口只有 1 个方法 `default void configureFeatures(CocoFeatureRegistry features) {}` —— **default no-op**；
   - 业务项目想配置 feature 开关，要么用 yaml（`coco.features.disabled: [...]`），要么在配置类上加 `@CocoFeatures`；
   - **两种实现方式覆盖了 `CocoConfigurer` 的全部意图**，且 yaml/注解都是声明式、更易读；
   - sample **没有** `CocoConfigurer` 实现，进一步证明它没被业务使用。

2. **`CocoFeatureRegistry` 是"scratch 对象泄漏成接口"的反模式**：
   - 它本质是 `Mutable Set<CocoFeature> enabled + disabled`；
   - `DefaultCocoFeatureRegistry` 是个**纯 mutable POJO**，没在任何业务上下文被持有；
   - 仅在 `CocoFeatureSelectionCollector.collect()` 中**创建-使用-丢弃**（scratch object），每次调用都 `new DefaultCocoFeatureRegistry()`；
   - 这是一个**过程内的临时变量**，不是稳定的领域对象 —— 不应该出现在公共 API 里。

3. **`@CocoFeatures` 注解与 `CocoConfigurer` 重复**：
   - 两者目的完全一样（声明启用的 feature）；
   - 业务方选哪个？文档没有强制约定；
   - 当前实现：collector 两者都收，set 取并集 → 这意味着同一个业务项目可能同时用 yaml + 注解 + configurer 三个源，**配置优先级变得隐式**。

**建议（M）：**
- **方案 A（推荐，删 SPI）**：
  - 删 `CocoConfigurer`、`CocoFeatureRegistry`、`DefaultCocoFeatureRegistry`；
  - 业务项目只用 yaml + `@CocoFeatures`；
  - `CocoFeatureSelectionCollector` 内部用一个 private final record `CocoFeatureSelection(Set<CocoFeature> enabled, Set<CocoFeature> disabled)` 即可；
  - 砍掉约 130 行代码 + 1 个公开 SPI。
- **方案 B（保留 SPI 但收敛）**：
  - `CocoFeatureRegistry` 改为不可变（builder 模式）：`new CocoFeatureSelection.Builder().enable(...).disable(...).build()`；
  - `CocoConfigurer` 删除，让 `@CocoFeatures` 成为唯一入口；
  - 业务方在配置类上加 `@EnableFeatures(...)` / `@DisableFeatures(...)` 即可。

与 AGENTS.md "Keep public APIs small and stable. Add SPI only when there is a real replacement point." 一致 —— 当前 SPI **没有真实替换需求**。

---

### 【API 设计 / 中】B4. `CocoFeature` 枚举**封闭**，无法扩展第三方 feature

**位置：** `coco-api/coco-api-core/.../feature/CocoFeature.java`

```java
public enum CocoFeature {
    WEB, MYBATIS_PLUS, AUDIT, SECURITY, TENANT, DATA_PERMISSION, OPENAPI, CODEGEN;
}
```

**问题：**
- 业务项目想加自己的 feature（如"飞书通知"、"OSS 存储"），**必须改框架源码**；
- 与 `coco-feature-codegen` / `coco-feature-openapi` / `coco-feature-audit` 三个"扩展点 SPI 模块"的设计哲学**直接矛盾** —— 那三个模块明明是"让业务接入自家实现"的，现在被 `CocoFeature` 锁死在框架内；
- 唯一记录业务 feature 的机制是 yaml `coco.features.disabled: [...]`，但**不能注册新 feature**。

**建议（M）：** 把 `CocoFeature` 从封闭 enum 改为：
- 保留内置 enum（`StandardFeature`）作为 `CocoFeature` 接口的默认实现；
- 业务方实现 `CocoFeature` 接口自定义 feature id；
- `@ConditionalOnCocoFeature` 改为接受 `String id` + `Class<? extends CocoFeature>`（向后兼容）。

---

### 【业务架构 / 中】B5. `SampleOrderRepository` 接口违反 ISP —— 商品查询与订单管理混杂

**位置：** `coco-samples/coco-sample-basic/src/main/java/io/github/coco/sample/basic/domain/order/SampleOrderRepository.java:22-51`

```java
public interface SampleOrderRepository {
    List<SampleProduct> findProducts();       // ← 应该是 ProductRepository
    SampleOrder createOrder(String buyerName, String sku, int quantity);
    Optional<SampleOrder> findOrder(String orderId);
}
```

**问题：** "OrderRepository" 同时管 product 查询 + order 创建/查询。两个完全不相关的领域对象共用一个接口。

**为什么这是 sample 的关键问题：** sample 是 README 唯一的"标准接入示例"，业务方会照搬这个结构。如果他们也建一个 `OrderRepository` 同时管 user / order / product / payment，**整个项目都会重复这个反模式**。

**建议（S）：** 拆为两个接口：
- `ProductRepository.findProducts()` / `findBySku(sku)` / `decrementStock(sku, qty)`；
- `OrderRepository.createOrder(...)` / `findById(orderId)`；
- `InMemorySampleOrderRepository` 实现两个接口（或拆为两个 in-memory 实现）。

---

### 【i18n / 中】B6. sample 的 messages.properties / messages_en_US.properties 内容**完全相同**（中文）

**位置：**
- `coco-samples/coco-sample-basic/src/main/resources/messages.properties` （4 行英文）
- `coco-samples/coco-sample-basic/src/main/resources/messages_en_US.properties` （4 行英文）
- `coco-samples/coco-sample-basic/src/main/resources/messages_zh_CN.properties` （4 行中文）

```bash
$ diff messages.properties messages_en_US.properties   # → 无差异
$ diff messages.properties messages_zh_CN.properties   # → 4 行全不同
```

但 application.yml 把 `default-locale: zh-CN` 且 `fallback-to-system-locale: false` —— 实际默认 locale 永远是 zh-CN，`messages.properties`（"默认 fallback"）**永远不会被读到**。

**问题：** 这个 3 文件配置实际上是"浪费 + 误导"：
1. `messages.properties` 是英文，但默认 locale 是 zh-CN → 永远不会用上；
2. `messages_en_US.properties` 和默认完全相同 → 维护时改一个忘了改另一个就会失同步；
3. 与第一轮审计 § A2 提到的 security 模块的 `default == en_US` 是同一类问题。

**建议（S）：** 二选一：
- 删 `messages.properties`，只用 `messages_zh_CN.properties` + `messages_en_US.properties`，并把 `default-locale` 改为 `en-US`；
- 或保留默认 fallback，把 `messages.properties` 改为与 `zh_CN` 同内容，并明确把 `default-locale` 设为 `zh-CN`。

当前配置是"两个英文 fallback + 一个中文 actual" —— **自相矛盾**。

---

### 【Controller 设计 / 中】B7. `SampleOrderController` 4 个 POST 端点都是 URL 装饰

**位置：** `SampleOrderController.java:67-106`

```java
@PostMapping("/orders")                      public SampleOrderResponse createOrder(req) { ... }
@PostMapping("/secure/signature/orders")     public SampleOrderResponse createSignatureOrder(req) { ... }
@PostMapping("/secure/replay/orders")        public SampleOrderResponse createReplayOrder(req) { ... }
@PostMapping("/secure/encryption/orders")    public SampleOrderResponse createEncryptionOrder(req) { ... }
```

4 个方法体**完全相同**（都调用 `createOrderResponse(req)`）。

**问题：** Reader 第一次看到会以为这 4 个端点有不同的业务语义，实际只是 URL 装饰。真正鉴权靠的是 coco-feature-web 的过滤器（按 `coco.web.signature.matcher.required.path-patterns` 匹配）。

**更严重的问题：** 如果业务方照搬，他们可能在 `createSignatureOrder` / `createReplayOrder` 里写不同的业务逻辑——结果**安全过滤器是路径级匹配，端点名字根本不参与鉴权决策**。开发者会被误导。

**建议（S）：** 显式注释 + 拆方法或合并端点：
- 合并为 1 个端点 `/orders`，通过 `coco.web.signature.matcher.path-patterns` 配置 `/orders` 路径匹配；
- 或者保留 4 端点但在 JavaDoc 上明确写："All 4 endpoints share the same handler; security is enforced by coco-feature-web filters based on path-patterns in application.yml"。

---

### 【api-spi / 中】B8. `CocoFeatureSelection` 三个数据载体概念重叠（与第二份审计 C13 重叠）

**位置：**
- `coco-feature-registry/.../CocoFeatureSelection.java`
- `coco-feature-registry/.../CocoFeatureManifest.java`
- `coco-feature-registry/.../CocoFeaturePlan.java`
- `coco-config/.../CocoProperties.java`

**问题：** 同一份"启用/禁用 feature 列表"在**4 个不同的类**里表达，且语义差异微妙：
- `CocoFeatureSelection` —— 代码级（configurer / 注解）
- `CocoFeatureManifest` —— 构建期（写盘 JSON）
- `CocoFeaturePlan` —— 运行期（已合并 + 依赖传播）
- `CocoProperties` —— 配置级（yaml）

**业务方读哪个？** 文档没说。但 sample 的 application.yml 配的是 `coco.features.disabled` —— 映射到 `CocoProperties`。如果同时加了 `@CocoFeatures(enabled=...)` 注解 —— 映射到 `CocoFeatureSelection`。**合并规则在 `CocoFeatureSelectionCollector` + `CocoConfigAutoConfiguration` 中隐式实现**（与第一份审计 C14 重叠）。

**建议（M）：** 把 4 个类合并为 1 个 immutable record `CocoFeatureSelection(Set<CocoFeature> enabled, Set<CocoFeature> disabled, Map<CocoFeature, Set<CocoFeature>> dependencies)` + 1 个 builder。其余 3 个类作为反序列化适配层。

---

### 【业务架构 / 低】B9. sample `domain.order` 与 `interfaces.rest` 缺 package-info.java

**位置：**
- `domain.order/package-info.java` ✅（已存在）
- `interfaces.rest/package-info.java` ✅（已存在）
- `application.order/package-info.java` ✅（已存在）
- `infrastructure.order/package-info.java` ✅（已存在）

**重新核查：** sample 4 个包**全部有** `package-info.java` ✅ —— 这条发现**作废**，是早期判断错误。

---

### 【业务架构 / 低】B10. sample `application.yml` 硬编码 Windows 路径（与第一份审计 D13 重叠）

```yaml
coco:
  logging:
    node-renderer:
      command: "D:\\Program Files\\nodejs\\node.exe"   # ← Windows-specific
```

已在第一份审计标记。**未修复**。

---

### 【异常契约 / 低】B11. `CocoErrorCode` 接口暴露 6 类异常的 default factory —— API 表面过大

**位置：** `coco-common/coco-common-exception/.../CocoErrorCode.java:36-188`

```java
public interface CocoErrorCode extends CocoMessageCode {
    default CocoException exception(...) {...}
    default CocoRequestException request(...) {...}
    default CocoUnauthorizedException unauthorized(...) {...}
    default CocoForbiddenException forbidden(...) {...}
    default CocoNotFoundException notFound(...) {...}
    default CocoConflictException conflict(...) {...}
    default CocoSystemException system(...) {...}
    // 14 个 default 方法（每个 2 个重载）
}
```

**问题：** 接口提供 14 个默认方法 —— 业务方只需要 1-2 个（一般是 `request()` + `notFound()` + `conflict()`）。但接口让所有实现类都"继承"了全部 14 个。

这与 sample 中 `SampleBusinessErrorCode` 的实际用法一致（`request()` / `notFound()` / `conflict()` 都用到），**所以当前是合理的**，但如果未来业务方要扩展自己类型的异常（如 `CocoPaymentRequiredException`），需要在接口里加方法，**所有实现类全部受影响** —— 接口膨胀。

**建议（S）：** 把 14 个 default 方法从接口移到 `CocoExceptions` 工具类（`CocoExceptions.notFound(code, args)`），`CocoErrorCode` 接口只留 `code()` + `messageCode()` + 1 个 `exception(args)` 工厂。

---

## 3. coco-api-core 契约使用率详细分析

### 3.1 全仓 grep 引用统计

```
CocoConfigurer:
  - coco-config (2 处): CocoConfigAutoConfiguration, CocoFeatureSelectionCollector
  - coco-config 测试 (1 处): CocoConfigAutoConfigurationTest
  - 业务项目 (0 处)  ←—— 空 SPI

CocoFeatureRegistry:
  - coco-config (1 处)
  - coco-feature-runtime (3 处)
  - coco-feature-runtime 测试
  - 业务项目 (0 处)

DefaultCocoFeatureRegistry:
  - coco-config (1 处)
  - coco-api-core 测试
  - 业务项目 (0 处)

CocoFeatures 注解:
  - coco-config (1 处)
  - coco-config 测试 (1 处)
  - coco-maven-plugin (3 处，扫描器读取)
  - 业务项目 (0 处)  ←—— 0 业务使用

CocoFeature 枚举:
  - 26 处引用（包括 sample 间接通过 yaml）
  - 业务项目 (yaml 配置)
```

### 3.2 与 AGENTS.md 原则的对照

> "Keep public APIs small and stable. Add SPI only when there is a real replacement point."

| 契约 | 是否"real replacement point"? | 证据 |
| --- | --- | --- |
| `CocoConfigurer` | ❌ | yaml + 注解已覆盖；sample 0 实现 |
| `CocoFeatureRegistry` | ❌ | 内部 scratch 对象；`CocoFeatureSelection` immutable record 可替代 |
| `DefaultCocoFeatureRegistry` | ❌ | 唯一调用方是 collector 的 1 行 |
| `@CocoFeatures` | ✅ | 业务方在配置类上声明 feature 开关，**有真实用例** |
| `CocoFeature` | ✅ | 枚举是 feature id 的稳定标识 |

**结论：** 5 个契约类中，**2 个应保留**（`@CocoFeatures`、`CocoFeature`），**3 个应删除**（`CocoConfigurer`、`CocoFeatureRegistry`、`DefaultCocoFeatureRegistry`）。

---

## 4. Sample 分层架构评分

| 项 | 评分 | 备注 |
| --- | --- | --- |
| 领域层 framework-agnostic | ⭐⭐⭐⭐⭐ | 完美，仅 `CocoBusinessCode` 一个接口依赖 |
| 仓储接口粒度 | ⭐⭐ | ISP 违反（B5） |
| 应用服务职责 | ⭐⭐⭐ | 编排清晰但事务边界缺失（B1） |
| 应用服务事务管理 | ⭐ | 完全没演示（B1） |
| Controller 路由设计 | ⭐⭐ | 4 个 secure 端点只是 URL 装饰（B7） |
| 业务错误码 + i18n | ⭐⭐⭐⭐ | 模式正确，i18n bundle 命名错位（B6） |
| 集成测试覆盖 | ⭐⭐⭐⭐ | 端到端好，覆盖响应/i18n/trace/accesslog |
| 架构一致性测试 | ⭐⭐⭐⭐⭐ | `CocoSampleBusinessIntegrationTest` 用代码钉死了分层约束 |

---

## 5. 行动建议（按 ROI 排序）

### 5.1 Q3（v1.0.x 必修）

| # | 工作 | 工作量 | 影响 |
| --- | --- | --- | --- |
| 1 | **B3** 删除 `CocoConfigurer` / `CocoFeatureRegistry` / `DefaultCocoFeatureRegistry`，收敛为 `@CocoFeatures` + yaml | M（~3 天） | API 表面减小 60% |
| 2 | **B1** sample 加 `@Transactional` 演示 | S（~半天） | 业务方"正确做法"参考 |
| 3 | **B2** 把 `PRODUCT_NOT_FOUND` / `INSUFFICIENT_STOCK` 校验从仓储移到 application service | S（~1 天） | 分层示范 |
| 4 | **B6** 修 sample i18n bundle（删除重复的 en_US 或反转 default-locale） | S（~1 小时） | 消除误导 |
| 5 | **B7** sample Controller 4 个端点合并或显式注释 | S（~半天） | 消除误导 |

### 5.2 Q4（v1.1 改进）

| # | 工作 | 工作量 | 影响 |
| --- | --- | --- | --- |
| 6 | **B4** `CocoFeature` 从封闭 enum 改为接口 + 标准实现 | M（~3 天） | 解锁第三方 feature |
| 7 | **B5** sample `SampleOrderRepository` 拆为 `Order` + `Product` 接口 | S | ISP 示范 |
| 8 | **B8** 合并 `CocoFeatureSelection` / `CocoFeatureManifest` / `CocoFeaturePlan` / `CocoProperties` 4 个数据载体 | M（~3 天） | 简化配置流转 |
| 9 | **B11** `CocoErrorCode` 接口瘦身，default factory 移到 `CocoExceptions` 工具类 | S（~1 天） | 接口稳定性 |

---

## 6. 与前两份审计的关系

| 主题 | 第一份（功能 / 安全） | 第二份（模块 / 子包） | 本份（业务架构） |
| --- | --- | --- | --- |
| starter 装包 | A1 高 | M1 高 | — |
| 数据权限数值列 | A3 高 | — | — |
| 上下文 ThreadLocal | A5 高 | — | — |
| 注解 + yaml 优先级 | C14 中 | — | B3 高（更深根因） |
| CocoFeature 封闭 | D3 低 | — | B4 中 |
| 包命名冲突 | D5 低 | M9 中 | — |
| i18n bundle 错位 | A2 中 | — | B6 中 |
| 业务侧 SPI 使用率 | — | — | B3 高 |
| Controller 端点混淆 | — | — | B7 中 |
| 仓储分层反模式 | — | — | B2 高 / B5 中 |

**核心洞察：** 第一份与本份有 3 处**根因级重叠**：
- **B3 ↔ C14**：CocoFeature 配置 SPI 与 yaml/注解的优先级混乱，根因都是"三个数据载体并存"；
- **A2 ↔ B6**：i18n bundle 命名错位在两个模块都存在（security 模块 + sample），根因是 **"默认 fallback 写英文 + 实际 default-locale 是中文"** 的反模式；
- **D5 ↔ M9**：`web.security.metadata` 与 `coco-feature-security` 命名重叠已确认。

---

## 7. 一句话总结

Coco 的 sample 是**真正做到了领域层 framework-agnostic 的好示范**（这很难得），但 coco-api-core 中 5 个契约类有 3 个**几乎没被任何业务使用**——它们是被"将来可能用得上"的幻觉驱动的过度设计。最值得动手的两件事：**删除 3 个空 SPI（B3）**与**修 sample 的事务 / 仓储分层反模式（B1+B2）**，合计约 1 周工作量，能让 framework 的 API 表面与 sample 范例都清爽一档。

---

## 附录 A. 审计方法学

- 通读 sample 14 个主类 + 2 个测试类
- 通读 coco-api-core 5 个主类 + 1 个测试
- 通读 coco-common-exception 中 6 个核心契约类（`CocoException`、`CocoErrorCode`、`CocoBusinessCode`、`CocoCommonErrorCode`、`CocoConflictException` 等）
- 全仓 grep `CocoConfigurer` / `CocoFeatureRegistry` / `CocoFeatures` / `@Transactional` 引用统计
- 对每条发现做"业务方照搬会发生什么"的反推

附录 B. 数据采集脚本同前两份审计。