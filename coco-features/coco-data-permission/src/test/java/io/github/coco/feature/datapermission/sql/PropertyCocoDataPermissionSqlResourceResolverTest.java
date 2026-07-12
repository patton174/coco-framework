package io.github.coco.feature.datapermission.sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import net.sf.jsqlparser.schema.Table;
import org.junit.jupiter.api.Test;

/**
 * 基于配置属性的数据权限 SQL 资源解析器测试。
 * <p>
 * 验证资源映射会按普通表名和 schema-qualified 表名解析到业务资源。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-data-permission}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class PropertyCocoDataPermissionSqlResourceResolverTest {

    @Test
    void resolvesResourceByNormalizedTableName() {
        CocoDataPermissionSqlProperties properties = new CocoDataPermissionSqlProperties();
        properties.getResources().put(" sample-order ", resource(" `SAMPLE_ORDER` "));
        PropertyCocoDataPermissionSqlResourceResolver resolver =
                new PropertyCocoDataPermissionSqlResourceResolver(properties);

        assertThat(resolver.resolve(new CocoDataPermissionSqlResourceContext(new Table("sample_order"),
                "SampleMapper.selectOrders"))).contains("sample-order");
    }

    @Test
    void resolvesResourceBySchemaQualifiedTableName() {
        CocoDataPermissionSqlProperties properties = new CocoDataPermissionSqlProperties();
        properties.getResources().put("sample-order", resource("tenant_a.sample_order"));
        PropertyCocoDataPermissionSqlResourceResolver resolver =
                new PropertyCocoDataPermissionSqlResourceResolver(properties);

        assertThat(resolver.resolve(new CocoDataPermissionSqlResourceContext(new Table("tenant_a", "sample_order"),
                "SampleMapper.selectOrders"))).contains("sample-order");
    }

    @Test
    void ignoresBlankResourceKeysAndUnknownTables() {
        CocoDataPermissionSqlProperties properties = new CocoDataPermissionSqlProperties();
        properties.getResources().put(" ", resource("sample_order"));
        properties.getResources().put("sample-product", resource("sample_product"));
        PropertyCocoDataPermissionSqlResourceResolver resolver =
                new PropertyCocoDataPermissionSqlResourceResolver(properties);

        assertThat(resolver.resolve(new CocoDataPermissionSqlResourceContext(new Table("sample_order"),
                "SampleMapper.selectOrders"))).isEmpty();
    }

    private static CocoDataPermissionSqlResourceProperties resource(String table) {
        CocoDataPermissionSqlResourceProperties resource = new CocoDataPermissionSqlResourceProperties();
        resource.setTables(List.of(table));
        return resource;
    }
}
