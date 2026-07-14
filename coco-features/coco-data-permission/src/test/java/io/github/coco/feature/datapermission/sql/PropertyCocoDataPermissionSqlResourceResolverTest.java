package io.github.coco.feature.datapermission.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github.coco.context.internal.CocoSqlIdentifierNormalizer;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.parser.feature.Feature;
import net.sf.jsqlparser.parser.feature.FeatureConfiguration;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.Select;
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
        addResource(properties, " sample-order ", resource(" `SAMPLE_ORDER` "));
        PropertyCocoDataPermissionSqlResourceResolver resolver =
                new PropertyCocoDataPermissionSqlResourceResolver(properties);

        assertThat(resolver.resolve(new CocoDataPermissionSqlResourceContext(new Table("sample_order"),
                "SampleMapper.selectOrders"))).contains("sample-order");
    }

    @Test
    void resolvesResourceBySchemaQualifiedTableName() {
        CocoDataPermissionSqlProperties properties = new CocoDataPermissionSqlProperties();
        addResource(properties, "sample-order", resource("tenant_a.sample_order"));
        PropertyCocoDataPermissionSqlResourceResolver resolver =
                new PropertyCocoDataPermissionSqlResourceResolver(properties);

        assertThat(resolver.resolve(new CocoDataPermissionSqlResourceContext(new Table("tenant_a", "sample_order"),
                "SampleMapper.selectOrders"))).contains("sample-order");
    }

    @Test
    void resolvesQuotedSchemaQualifiedTableParsedByJSqlParser() throws Exception {
        CocoDataPermissionSqlProperties properties = new CocoDataPermissionSqlProperties();
        addResource(properties, "sample-order", resource("`tenant_a`.\"sample_order\""));
        PropertyCocoDataPermissionSqlResourceResolver resolver =
                new PropertyCocoDataPermissionSqlResourceResolver(properties);
        Select select = (Select) CCJSqlParserUtil.parse("SELECT id FROM \"TENANT_A\".\"SAMPLE_ORDER\"");
        Table table = (Table) select.getPlainSelect().getFromItem();

        assertThat(resolver.resolve(new CocoDataPermissionSqlResourceContext(table,
                "SampleMapper.selectOrders"))).contains("sample-order");
    }

    @Test
    void keepsQuotedDotsAsOneIdentifierSegment() {
        CocoDataPermissionSqlProperties properties = new CocoDataPermissionSqlProperties();
        addResource(properties, "literal-dot", resource("\"tenant.a\""));
        addResource(properties, "qualified", resource("tenant.a"));
        PropertyCocoDataPermissionSqlResourceResolver resolver =
                new PropertyCocoDataPermissionSqlResourceResolver(properties);

        assertThat(resolver.resolve(new CocoDataPermissionSqlResourceContext(new Table("\"TENANT.A\""),
                "SampleMapper.selectOrders"))).contains("literal-dot");
        assertThat(resolver.resolve(new CocoDataPermissionSqlResourceContext(new Table("TENANT", "A"),
                "SampleMapper.selectOrders"))).contains("qualified");
    }

    @Test
    void resolvesSquareBracketQualifiedTableWhenParserFeatureIsEnabled() throws Exception {
        CocoDataPermissionSqlProperties properties = new CocoDataPermissionSqlProperties();
        addResource(properties, "sample-order", resource("[tenant_a].[sample_order]"));
        PropertyCocoDataPermissionSqlResourceResolver resolver =
                new PropertyCocoDataPermissionSqlResourceResolver(properties);
        Select select = (Select) CCJSqlParserUtil.parse("SELECT id FROM [TENANT_A].[SAMPLE_ORDER]", parser ->
                parser.withConfiguration(new FeatureConfiguration()
                        .setValue(Feature.allowSquareBracketQuotation, true)));
        Table table = (Table) select.getPlainSelect().getFromItem();

        assertThat(resolver.resolve(new CocoDataPermissionSqlResourceContext(table,
                "SampleMapper.selectOrders"))).contains("sample-order");
    }

    @Test
    void resolvesWithoutMybatisPlusClassesOnTheRuntimeClasspath() throws Exception {
        URL[] classpath = {
                codeSource(PropertyCocoDataPermissionSqlResourceResolver.class),
                codeSource(CocoSqlIdentifierNormalizer.class),
                codeSource(Table.class)
        };
        try (URLClassLoader classLoader = new URLClassLoader(classpath, ClassLoader.getPlatformClassLoader())) {
            assertThatThrownBy(() -> classLoader.loadClass(
                    "io.github.coco.feature.mybatisplus.internal.CocoSqlIdentifierNormalizer"))
                    .isInstanceOf(ClassNotFoundException.class);

            Class<?> propertiesType = classLoader.loadClass(
                    "io.github.coco.feature.datapermission.sql.CocoDataPermissionSqlProperties");
            Object properties = propertiesType.getConstructor().newInstance();
            Class<?> resourceType = classLoader.loadClass(
                    "io.github.coco.feature.datapermission.sql.CocoDataPermissionSqlResourceProperties");
            Object resource = resourceType.getConstructor().newInstance();
            resourceType.getMethod("setTables", List.class).invoke(resource, List.of("sample_order"));
            Map<String, Object> configuredResources = new java.util.LinkedHashMap<>(
                    resources(propertiesType, properties));
            configuredResources.put("sample-order", resource);
            propertiesType.getMethod("setResources", Map.class).invoke(properties, configuredResources);

            Class<?> resolverType = classLoader.loadClass(
                    "io.github.coco.feature.datapermission.sql.PropertyCocoDataPermissionSqlResourceResolver");
            Object resolver = resolverType.getConstructor(propertiesType).newInstance(properties);
            Class<?> tableType = classLoader.loadClass("net.sf.jsqlparser.schema.Table");
            Object table = tableType.getConstructor(String.class).newInstance("SAMPLE_ORDER");
            Class<?> contextType = classLoader.loadClass(
                    "io.github.coco.feature.datapermission.sql.CocoDataPermissionSqlResourceContext");
            Object context = contextType.getConstructor(tableType, String.class)
                    .newInstance(table, "SampleMapper.selectOrders");

            Object result = resolverType.getMethod("resolve", contextType).invoke(resolver, context);
            assertThat(result).isEqualTo(Optional.of("sample-order"));
        }
    }

    @Test
    void ignoresBlankResourceKeysAndUnknownTables() {
        CocoDataPermissionSqlProperties properties = new CocoDataPermissionSqlProperties();
        addResource(properties, " ", resource("sample_order"));
        addResource(properties, "sample-product", resource("sample_product"));
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

    private static void addResource(CocoDataPermissionSqlProperties properties, String resource,
            CocoDataPermissionSqlResourceProperties resourceProperties) {
        Map<String, CocoDataPermissionSqlResourceProperties> resources = new java.util.LinkedHashMap<>(
                properties.getResources());
        resources.put(resource, resourceProperties);
        properties.setResources(resources);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> resources(Class<?> propertiesType, Object properties) throws Exception {
        return (Map<String, Object>) propertiesType.getMethod("getResources").invoke(properties);
    }

    private static URL codeSource(Class<?> type) {
        return type.getProtectionDomain().getCodeSource().getLocation();
    }
}
