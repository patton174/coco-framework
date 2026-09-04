---
title: 分页与排序
---

# 分页与排序

## 功能简介

Coco 把分页和排序做成了一条对业务层透明的链路：前端通过标准的 HTTP 查询参数发起分页请求，框架在 Web 入口解析并校验参数，写入线程级上下文，再由 MyBatis-Plus 拦截器在查询执行前自动注入到 `IPage` 对象。Repository 层甚至无需接收分页参数，业务代码只需返回一个持久层无关的 `CocoPage`。

整条链路的关键设计目标是**安全**：排序字段经过两道白名单校验，既防止 SQL 注入，也防止通过排序参数探测表结构。

涉及模块：

- `coco-api`：持久层无关的 `CocoPage`、`CocoPageRequest`。
- `coco-context`：线程级上下文 `CocoPageContext`、`CocoPageContextHolder`、排序项 `CocoSortOrder`。
- `coco-feature-web`：入口拦截器 `CocoPageInterceptor` 及其配置 `CocoPageProperties`。
- `coco-feature-mybatis-plus`：上下文注入拦截器 `CocoPageContextInnerInterceptor`、桥接工具 `CocoPages`、排序白名单注解 `@CocoSortable`。

## 完整链路

以请求 `GET /products?page=1&size=20&sort=name,asc&sort=price,desc` 为例：

1. **HTTP 查询参数**：`page`、`size` 控制分页，`sort` 可重复出现，格式为 `字段名,方向`，方向省略或非 `desc` 时视为升序。
2. **`CocoPageInterceptor` 解析（Web 入口）**：在 `preHandle` 中读取参数，做边界校正（`page < 1`、`size < 1` 回落默认值，`size > maxSize` 截断为上限），并对排序字段做**字符白名单**校验——仅允许字母、数字、下划线（正则 `[A-Za-z0-9_]+`），不匹配的排序项直接丢弃。解析结果封装为 `CocoPageContext` 写入 `CocoPageContextHolder`。请求结束时在 `afterCompletion` 中清除上下文，防止线程池复用导致数据串线。
3. **`CocoPageContext`（ThreadLocal 上下文）**：不可变记录，保存 `page`、`size`、`orders`，构造时校验 `page >= 1`、`size >= 1`。
4. **`CocoPageContextInnerInterceptor` 注入（MyBatis-Plus 层）**：在 `beforeQuery` 中检查 Mapper 方法参数里是否存在 `IPage`，若当前线程存在分页上下文，则自动把 `page`、`size` 填入 `IPage`，并把排序项经过 `@CocoSortable` **字段白名单**过滤和列名映射后追加到 `Page` 的 `OrderItem`。Repository 层因此无需手动构造分页参数。
5. **返回 `CocoPage`**：查询完成后，通过 `CocoPages.toCocoPage(iPage)` 把 MyBatis-Plus 的 `IPage` 转换为持久层无关的 `CocoPage` 返回给上层。

## 排序白名单 `@CocoSortable`

排序是最容易被利用来注入 SQL 或探测结构的入口，因此 Coco 采用**声明式白名单**：只有在 Entity 字段上显式标注 `@CocoSortable` 的字段才允许参与 `ORDER BY`。

- 未命中白名单的排序字段被**静默丢弃**（不报错、不抛异常），避免向调用方泄露哪些字段可排序。
- 若 Entity 上没有任何 `@CocoSortable` 注解，则**所有**排序请求都被忽略。
- 白名单映射由 `CocoSortableFieldResolver` 解析并按 Entity 类缓存，映射规则为“排序参数名 → 数据库列名”：
  - **排序参数名（前端传入的名字）**：`@CocoSortable#value()` 非空时取它，否则取 Java 字段名。
  - **数据库列名（实际参与 ORDER BY）**：`@TableField#value()` 非空时取它，否则取 Java 字段名。

这套映射让前端看到的字段名与真实列名解耦：前端排序参数即使写成业务语义名，也会被安全地映射到真实列，未授权字段则被丢弃。

## 使用示例

### Entity：声明可排序字段

```java
@TableName("t_product")
public class Product {

    @TableId
    private Long id;

    // 前端 sort=name 允许，映射到列 product_name
    @CocoSortable
    @TableField("product_name")
    private String name;

    // 前端 sort=price 允许，映射到列 price（无 @TableField 时取字段名）
    @CocoSortable("price")
    private BigDecimal price;

    // 未标注 @CocoSortable：前端无法对该字段排序
    private String secret;

    // getters / setters ...
}
```

### Repository：无感知分页

```java
public interface ProductMapper extends BaseMapper<Product> {
}

@Repository
public class ProductRepository {

    private final ProductMapper productMapper;

    public ProductRepository(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    /**
     * 只需从上下文构造 IPage，page/size/排序由拦截器自动注入。
     */
    public CocoPage<Product> query() {
        Page<Product> page = CocoPages.fromContext();
        productMapper.selectPage(page, null);
        return CocoPages.toCocoPage(page);
    }
}
```

如果不依赖上下文自动注入，也可以显式构造分页对象：

```java
Page<Product> page = CocoPages.of(1, 20);
productMapper.selectPage(page, null);
return CocoPages.toCocoPage(page);
```

### Controller：直接返回 CocoPage

```java
@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductRepository productRepository;

    public ProductController(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    // GET /products?page=1&size=20&sort=name,asc&sort=price,desc
    // 无需声明任何分页/排序参数，均由拦截器解析
    @GetMapping
    public CocoPage<Product> list() {
        return productRepository.query();
    }
}
```

响应结构对应 `CocoPage`：包含 `items`（当前页数据）、`total`（总记录数）、`page`（当前页码）、`size`（每页大小），并提供 `totalPages()`、`hasNext()` 派生方法。

## 关键配置

Web 入口配置命名空间为 `coco.web.page`（由 `CocoPageProperties` 绑定）。

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `enabled` | boolean | `true` | 是否注册 `CocoPageInterceptor`（仅 Servlet Web 环境生效） |
| `page-parameter-name` | String | `page` | 页码请求参数名 |
| `size-parameter-name` | String | `size` | 每页大小请求参数名 |
| `sort-parameter-name` | String | `sort` | 排序请求参数名（可重复出现） |
| `default-page` | long | `1` | 页码缺失或非法时的默认值 |
| `default-size` | long | `20` | 每页大小缺失或非法时的默认值 |
| `max-size` | long | `100` | 每页大小上限，超过时截断为该值 |

分页上下文注入拦截器的开关（`coco-feature-mybatis-plus` 模块）：

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `coco.mybatis-plus.pagination.context.enabled` | boolean | `true` | 是否注册 `CocoPageContextInnerInterceptor` 自动注入分页上下文 |

> 底层分页拦截器 `PaginationInnerInterceptor` 的开关及其 `db-type`、`max-limit` 等参数属于 `coco.mybatis-plus.pagination`，详见 [MyBatis-Plus 集成](./mybatis-plus.md)。

## 边界注意事项

- **排序字段两道校验**：`CocoPageInterceptor` 先做字符白名单（只允许 `[A-Za-z0-9_]`），`CocoPageContextInnerInterceptor` 再做 `@CocoSortable` 字段白名单。两道都通过才会真正参与 `ORDER BY`，任何未命中都是静默丢弃。
- **未标注 `@CocoSortable` 则完全不排序**：Entity 没有任何 `@CocoSortable` 时，即使前端传了合法 `sort` 也会被全部忽略；这是安全默认值，接入排序功能需显式声明白名单字段。
- **上下文是线程级的**：`CocoPageContextHolder` 基于 `ThreadLocal`，请求结束由拦截器 `afterCompletion` 清理。若在异步线程、线程池或 `@Async` 中执行查询，需用 `CocoPageContextHolder.capture()` 传播上下文，否则子线程读不到分页参数。
- **上下文注入依赖 `IPage` 存在**：`CocoPageContextInnerInterceptor` 只有在 Mapper 方法参数中找到 `IPage` 时才注入；普通非分页查询不受影响。排序注入还要求分页对象是 MyBatis-Plus 的 `Page` 类型。
- **`CocoPages.fromContext()` 要求上下文存在**：当前线程没有分页上下文时抛出请求参数不合法异常（`INVALID_ARGUMENT`）。非 Web 触发的调用应改用 `CocoPages.of(page, size)`。
- **`size` 上限保护**：超过 `max-size` 会被静默截断为上限值，用于防止超大分页拖垮数据库。

