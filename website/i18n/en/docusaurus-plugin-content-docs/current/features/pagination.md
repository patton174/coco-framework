---
title: Pagination and Sorting
---

# Pagination and Sorting

## Overview

Coco turns pagination and sorting into a pipeline that is transparent to the business layer: the frontend initiates a paginated request through standard HTTP query parameters, the framework parses and validates the parameters at the Web entry point, writes them into a thread-level context, and a MyBatis-Plus interceptor then automatically injects them into the `IPage` object before the query executes. The Repository layer does not even need to receive pagination parameters; business code only needs to return a persistence-agnostic `CocoPage`.

The key design goal of the whole pipeline is **safety**: sort fields pass through two rounds of allowlist validation, which both prevents SQL injection and prevents probing the table structure through sort parameters.

Modules involved:

- `coco-api`: the persistence-agnostic `CocoPage` and `CocoPageRequest`.
- `coco-context`: the thread-level context `CocoPageContext`, `CocoPageContextHolder`, and the sort item `CocoSortOrder`.
- `coco-feature-web`: the entry interceptor `CocoPageInterceptor` and its configuration `CocoPageProperties`.
- `coco-feature-mybatis-plus`: the context injection interceptor `CocoPageContextInnerInterceptor`, the bridging utility `CocoPages`, and the sort allowlist annotation `@CocoSortable`.

## The Complete Pipeline

Take the request `GET /products?page=1&size=20&sort=name,asc&sort=price,desc` as an example:

1. **HTTP query parameters**: `page` and `size` control pagination; `sort` may appear multiple times, with the format `fieldName,direction`. When the direction is omitted or is not `desc`, it is treated as ascending.
2. **Parsing by `CocoPageInterceptor` (Web entry point)**: in `preHandle` it reads the parameters, applies boundary correction (`page < 1` and `size < 1` fall back to the defaults, `size > maxSize` is truncated to the upper limit), and applies a **character allowlist** validation to sort fields — only letters, digits, and underscores are allowed (regex `[A-Za-z0-9_]+`), and sort items that do not match are discarded outright. The parsed result is wrapped into a `CocoPageContext` and written to `CocoPageContextHolder`. When the request ends, the context is cleared in `afterCompletion` to prevent data leaking across threads due to thread pool reuse.
3. **`CocoPageContext` (ThreadLocal context)**: an immutable record holding `page`, `size`, and `orders`, validating `page >= 1` and `size >= 1` at construction time.
4. **Injection by `CocoPageContextInnerInterceptor` (MyBatis-Plus layer)**: in `beforeQuery` it checks whether an `IPage` exists among the Mapper method arguments; if the current thread has a pagination context, it automatically fills `page` and `size` into the `IPage`, and appends the sort items to the `Page`'s `OrderItem` after they have passed the `@CocoSortable` **field allowlist** filtering and column-name mapping. The Repository layer therefore does not need to build pagination parameters manually.
5. **Returning a `CocoPage`**: once the query completes, `CocoPages.toCocoPage(iPage)` converts MyBatis-Plus's `IPage` into a persistence-agnostic `CocoPage` returned to the upper layer.

## The Sort Allowlist `@CocoSortable`

Sorting is the entry point most easily exploited to inject SQL or probe structure, so Coco adopts a **declarative allowlist**: only fields explicitly annotated with `@CocoSortable` on the Entity are allowed to participate in `ORDER BY`.

- Sort fields that do not hit the allowlist are **silently discarded** (no error, no exception), avoiding disclosing to the caller which fields are sortable.
- If there is no `@CocoSortable` annotation on the Entity, then **all** sort requests are ignored.
- The allowlist mapping is resolved by `CocoSortableFieldResolver` and cached per Entity class, with the mapping rule "sort parameter name -> database column name":
  - **Sort parameter name (the name passed by the frontend)**: taken from `@CocoSortable#value()` when it is non-empty, otherwise from the Java field name.
  - **Database column name (what actually participates in ORDER BY)**: taken from `@TableField#value()` when it is non-empty, otherwise from the Java field name.

This mapping decouples the field names the frontend sees from the real column names: even if the frontend writes the sort parameter as a business-semantic name, it is safely mapped to the real column, while unauthorized fields are discarded.

## Usage Examples

### Entity: declaring sortable fields

```java
@TableName("t_product")
public class Product {

    @TableId
    private Long id;

    // frontend sort=name is allowed, mapped to column product_name
    @CocoSortable
    @TableField("product_name")
    private String name;

    // frontend sort=price is allowed, mapped to column price (uses field name when @TableField is absent)
    @CocoSortable("price")
    private BigDecimal price;

    // not annotated with @CocoSortable: the frontend cannot sort by this field
    private String secret;

    // getters / setters ...
}
```

### Repository: pagination without awareness

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
     * Just build an IPage from the context; page/size/sorting are injected automatically by the interceptor.
     */
    public CocoPage<Product> query() {
        Page<Product> page = CocoPages.fromContext();
        productMapper.selectPage(page, null);
        return CocoPages.toCocoPage(page);
    }
}
```

If you do not rely on automatic context injection, you can also build the pagination object explicitly:

```java
Page<Product> page = CocoPages.of(1, 20);
productMapper.selectPage(page, null);
return CocoPages.toCocoPage(page);
```

### Controller: returning CocoPage directly

```java
@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductRepository productRepository;

    public ProductController(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    // GET /products?page=1&size=20&sort=name,asc&sort=price,desc
    // No need to declare any pagination/sort parameters; all are parsed by the interceptor
    @GetMapping
    public CocoPage<Product> list() {
        return productRepository.query();
    }
}
```

The response structure corresponds to `CocoPage`: it contains `items` (the current page's data), `total` (total record count), `page` (current page number), and `size` (page size), and it provides the derived methods `totalPages()` and `hasNext()`.

## Key Configuration

The Web entry configuration namespace is `coco.web.page` (bound by `CocoPageProperties`).

| Configuration item | Type | Default | Description |
|--------|------|--------|------|
| `enabled` | boolean | `true` | Whether to register `CocoPageInterceptor` (takes effect only in a Servlet Web environment) |
| `page-parameter-name` | String | `page` | The request parameter name for the page number |
| `size-parameter-name` | String | `size` | The request parameter name for the page size |
| `sort-parameter-name` | String | `sort` | The request parameter name for sorting (may appear multiple times) |
| `default-page` | long | `1` | The default value when the page number is missing or invalid |
| `default-size` | long | `20` | The default value when the page size is missing or invalid |
| `max-size` | long | `100` | The upper limit for page size; truncated to this value when exceeded |

The switch for the pagination context injection interceptor (in the `coco-feature-mybatis-plus` module):

| Configuration item | Type | Default | Description |
|--------|------|--------|------|
| `coco.mybatis-plus.pagination.context.enabled` | boolean | `true` | Whether to register `CocoPageContextInnerInterceptor` to automatically inject the pagination context |

> The switch for the underlying pagination interceptor `PaginationInnerInterceptor` and its parameters such as `db-type` and `max-limit` belong to `coco.mybatis-plus.pagination`; see [MyBatis-Plus Integration](./mybatis-plus.md) for details.

## Boundary Considerations

- **Two rounds of validation for sort fields**: `CocoPageInterceptor` first applies the character allowlist (only `[A-Za-z0-9_]` is allowed), then `CocoPageContextInnerInterceptor` applies the `@CocoSortable` field allowlist. Only fields that pass both rounds actually participate in `ORDER BY`; anything that does not hit is silently discarded.
- **No `@CocoSortable` means no sorting at all**: when the Entity has no `@CocoSortable` at all, even a valid `sort` passed by the frontend is entirely ignored; this is a secure default, and enabling sorting requires explicitly declaring the allowlist fields.
- **The context is thread-level**: `CocoPageContextHolder` is based on `ThreadLocal` and is cleaned up by the interceptor's `afterCompletion` when the request ends. If the query runs in an async thread, a thread pool, or under `@Async`, you need to propagate the context with `CocoPageContextHolder.capture()`, otherwise the child thread cannot read the pagination parameters.
- **Context injection depends on the presence of `IPage`**: `CocoPageContextInnerInterceptor` injects only when it finds an `IPage` among the Mapper method arguments; ordinary non-paginated queries are unaffected. Sort injection additionally requires the pagination object to be of MyBatis-Plus's `Page` type.
- **`CocoPages.fromContext()` requires the context to exist**: it throws an invalid-request-parameter exception (`INVALID_ARGUMENT`) when the current thread has no pagination context. Calls not triggered by the Web should use `CocoPages.of(page, size)` instead.
- **`size` upper-limit protection**: anything exceeding `max-size` is silently truncated to the upper limit, used to prevent oversized pagination from overwhelming the database.
