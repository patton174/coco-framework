package io.github.coco.feature.datapermission;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import io.github.coco.feature.datapermission.context.CocoDataPermissionContext;
import io.github.coco.feature.datapermission.context.CocoDataPermissionContextHolder;
import io.github.coco.feature.datapermission.context.CocoDataPermissionRule;
import io.github.coco.feature.datapermission.context.CocoDataScope;
import io.github.coco.feature.datapermission.mybatisplus.CocoMybatisPlusDataPermissionHandler;
import io.github.coco.feature.datapermission.sql.CocoDataPermissionSqlColumnType;
import io.github.coco.feature.datapermission.sql.CocoDataPermissionSqlProperties;
import io.github.coco.feature.datapermission.sql.CocoDataPermissionSqlResourceProperties;
import io.github.coco.feature.datapermission.sql.DefaultCocoDataPermissionSqlPredicateProvider;
import io.github.coco.feature.datapermission.sql.PropertyCocoDataPermissionSqlResourceResolver;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * 数据权限 SQL 值类型 MyBatis-Plus 集成测试。
 * <p>
 * 通过 H2 执行经 MyBatis-Plus 数据权限拦截器改写后的查询，验证配置类型而非数据库元数据决定 SQL 字面量类型。
 * </p>
 *
 * @author patton174
 * @since 1.0.0
 */
class CocoDataPermissionTypedValueMybatisPlusIntegrationTest {

    @Test
    void filtersSchemaQualifiedH2TablesWithExplicitTypedValues() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        createTables(dataSource);
        CocoDataPermissionSqlProperties properties = properties();
        SqlSessionFactory sqlSessionFactory = sqlSessionFactory(dataSource, properties);
        CocoDataPermissionContext context = CocoDataPermissionContext.of(Set.of(
                rule("typed-string", "allowed"),
                rule("typed-integer", "20"),
                rule("typed-decimal", "2.50"),
                rule("typed-boolean", "true")));

        CocoDataPermissionContextHolder.runWithContext(context, () -> {
            try (SqlSession session = sqlSessionFactory.openSession()) {
                TypedValueMapper mapper = session.getMapper(TypedValueMapper.class);

                assertThat(mapper.stringIds()).containsExactly(1L);
                assertThat(mapper.integerIds()).containsExactly(2L);
                assertThat(mapper.decimalIds()).containsExactly(2L);
                assertThat(mapper.booleanIds()).containsExactly(1L);
            }
        });
    }

    @Test
    void deniesMysqlBackslashEscapeAttackBeforeH2Execution() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        createTables(dataSource);
        CocoDataPermissionSqlProperties properties = properties();
        SqlSessionFactory sqlSessionFactory = sqlSessionFactory(dataSource, properties);
        CocoDataPermissionContext context = CocoDataPermissionContext.of(Set.of(
                rule("typed-string", "a\\' OR 1=1 --"),
                rule("typed-integer", "20"),
                rule("typed-decimal", "2.50"),
                rule("typed-boolean", "true")));

        List<Long> result = CocoDataPermissionContextHolder.callWithContext(context, () -> {
            try (SqlSession session = sqlSessionFactory.openSession()) {
                return session.getMapper(TypedValueMapper.class).stringIds();
            }
        });

        assertThat(result).isEmpty();
    }

    @Test
    void filtersUnicodeStringValuesThroughH2AndMybatisPlus() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        createTables(dataSource);
        SqlSessionFactory sqlSessionFactory = sqlSessionFactory(dataSource, properties());
        CocoDataPermissionContext context = CocoDataPermissionContext.of(Set.of(
                rule("typed-string", "研发部-東京")));

        List<Long> result = CocoDataPermissionContextHolder.callWithContext(context, () -> {
            try (SqlSession session = sqlSessionFactory.openSession()) {
                return session.getMapper(TypedValueMapper.class).stringIds();
            }
        });

        assertThat(result).containsExactly(3L);
    }

    private static void createTables(DriverManagerDataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("CREATE SCHEMA permission_schema");
        jdbcTemplate.execute(
                "CREATE TABLE permission_schema.typed_string (id BIGINT PRIMARY KEY, scope_value VARCHAR(64))");
        jdbcTemplate.execute(
                "CREATE TABLE permission_schema.typed_integer (id BIGINT PRIMARY KEY, scope_value INTEGER)");
        jdbcTemplate.execute(
                "CREATE TABLE permission_schema.typed_decimal (id BIGINT PRIMARY KEY, scope_value DECIMAL(10, 2))");
        jdbcTemplate.execute(
                "CREATE TABLE permission_schema.typed_boolean (id BIGINT PRIMARY KEY, scope_value BOOLEAN)");
        jdbcTemplate.execute(
                "INSERT INTO permission_schema.typed_string VALUES (1, 'allowed'), (2, 'blocked'), (3, '研发部-東京')");
        jdbcTemplate.execute("INSERT INTO permission_schema.typed_integer VALUES (1, 10), (2, 20)");
        jdbcTemplate.execute("INSERT INTO permission_schema.typed_decimal VALUES (1, 1.25), (2, 2.50)");
        jdbcTemplate.execute("INSERT INTO permission_schema.typed_boolean VALUES (1, TRUE), (2, FALSE)");
    }

    private static CocoDataPermissionSqlProperties properties() {
        CocoDataPermissionSqlProperties properties = new CocoDataPermissionSqlProperties();
        addResource(properties, "typed-string", "permission_schema.typed_string",
                CocoDataPermissionSqlColumnType.STRING);
        addResource(properties, "typed-integer", "permission_schema.typed_integer",
                CocoDataPermissionSqlColumnType.INTEGER);
        addResource(properties, "typed-decimal", "permission_schema.typed_decimal",
                CocoDataPermissionSqlColumnType.DECIMAL);
        addResource(properties, "typed-boolean", "permission_schema.typed_boolean",
                CocoDataPermissionSqlColumnType.BOOLEAN);
        return properties;
    }

    private static void addResource(CocoDataPermissionSqlProperties properties, String resourceName, String table,
            CocoDataPermissionSqlColumnType columnType) {
        CocoDataPermissionSqlResourceProperties resource = new CocoDataPermissionSqlResourceProperties();
        resource.setTables(List.of(table));
        resource.setColumn("scope_value");
        resource.setColumnType(columnType);
        properties.getResources().put(resourceName, resource);
    }

    private static SqlSessionFactory sqlSessionFactory(DriverManagerDataSource dataSource,
            CocoDataPermissionSqlProperties properties) throws Exception {
        CocoMybatisPlusDataPermissionHandler handler = new CocoMybatisPlusDataPermissionHandler(properties,
                CocoDataPermissionContextHolder::current, new PropertyCocoDataPermissionSqlResourceResolver(properties),
                new DefaultCocoDataPermissionSqlPredicateProvider());
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new DataPermissionInterceptor(handler));
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.addMapper(TypedValueMapper.class);
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(configuration);
        factoryBean.setPlugins(interceptor);
        return factoryBean.getObject();
    }

    private static CocoDataPermissionRule rule(String resource, String value) {
        return new CocoDataPermissionRule(resource, CocoDataScope.CUSTOM, Set.of(value));
    }

    interface TypedValueMapper {

        @Select("SELECT id FROM permission_schema.typed_string ORDER BY id")
        List<Long> stringIds();

        @Select("SELECT id FROM permission_schema.typed_integer ORDER BY id")
        List<Long> integerIds();

        @Select("SELECT id FROM permission_schema.typed_decimal ORDER BY id")
        List<Long> decimalIds();

        @Select("SELECT id FROM permission_schema.typed_boolean ORDER BY id")
        List<Long> booleanIds();
    }
}
