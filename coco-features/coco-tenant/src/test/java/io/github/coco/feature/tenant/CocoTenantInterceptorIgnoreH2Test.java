package io.github.coco.feature.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import javax.sql.DataSource;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import io.github.coco.exception.type.CocoForbiddenException;
import io.github.coco.feature.tenant.context.CocoTenantContext;
import io.github.coco.feature.tenant.context.CocoTenantContextHolder;
import io.github.coco.feature.tenant.sql.CocoTenantIdExpressionResolver;
import io.github.coco.feature.tenant.sql.CocoTenantInterceptorIgnoreDecision;
import io.github.coco.feature.tenant.sql.CocoTenantInterceptorIgnoreEvent;
import io.github.coco.feature.tenant.sql.CocoTenantInterceptorIgnoreGuard;
import io.github.coco.feature.tenant.sql.CocoTenantLineHandler;
import io.github.coco.feature.tenant.sql.CocoTenantSqlProperties;
import net.sf.jsqlparser.expression.StringValue;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Coco 租户 SQL 隔离旁路 H2 集成测试。
 * <p>
 * 通过真实 Mapper、MyBatis-Plus 拦截器链和 H2 数据库验证租户旁路治理行为。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-tenant}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class CocoTenantInterceptorIgnoreH2Test {

    @AfterEach
    void clearThreadState() {
        CocoTenantContextHolder.clear();
    }

    @Test
    void blocksUnallowlistedBypassBeforeOpeningJdbcConnection() {
        JdbcDataSource dataSource = initializedDataSource();
        CountingDataSource countingDataSource = new CountingDataSource(dataSource);
        SqlSessionFactory sqlSessionFactory = sqlSessionFactory(countingDataSource,
                new CocoTenantSqlProperties(), new CopyOnWriteArrayList<>());

        assertThatThrownBy(() -> selectAllIgnoringTenant(sqlSessionFactory))
                .isInstanceOf(PersistenceException.class)
                .hasRootCauseInstanceOf(CocoForbiddenException.class)
                .hasRootCauseMessage("coco.feature.tenant.error.interceptor-ignore-blocked");
        assertThat(countingDataSource.connectionRequests()).isZero();
    }

    @Test
    void allowsExactAllowlistedBypassAndPublishesStructuredEvent() {
        CocoTenantSqlProperties properties = new CocoTenantSqlProperties();
        properties.getInterceptorIgnore().getAllowedMappedStatements()
                .add(TenantRecordMapper.class.getName() + ".selectAllIgnoringTenant");
        List<CocoTenantInterceptorIgnoreEvent> events = new CopyOnWriteArrayList<>();
        SqlSessionFactory sqlSessionFactory = sqlSessionFactory(initializedDataSource(), properties, events);

        assertThat(selectAllIgnoringTenant(sqlSessionFactory)).containsExactly("A", "B");
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.mappedStatementId())
                    .isEqualTo(TenantRecordMapper.class.getName() + ".selectAllIgnoringTenant");
            assertThat(event.decision()).isEqualTo(CocoTenantInterceptorIgnoreDecision.ALLOWED);
        });
    }

    @Test
    void appliesTenantIsolationAndRestoresNestedAndWorkerThreadContexts() throws Exception {
        SqlSessionFactory sqlSessionFactory = sqlSessionFactory(initializedDataSource(),
                new CocoTenantSqlProperties(), new CopyOnWriteArrayList<>());
        CocoTenantContext tenantA = CocoTenantContext.of("tenant-a", "Tenant A");
        CocoTenantContext tenantB = CocoTenantContext.of("tenant-b", "Tenant B");
        CocoTenantContextHolder.set(tenantA);

        assertThat(selectTenantScoped(sqlSessionFactory)).containsExactly("A");
        assertThat(CocoTenantContextHolder.callWithContext(tenantB,
                () -> selectTenantScoped(sqlSessionFactory))).containsExactly("B");
        assertThat(CocoTenantContextHolder.requireCurrent()).isEqualTo(tenantA);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            assertThat(executor.submit(CocoTenantContextHolder.wrap(
                    () -> selectTenantScoped(sqlSessionFactory))).get()).containsExactly("A");
            assertThat(executor.submit(() -> CocoTenantContextHolder.current().isEmpty()).get()).isTrue();
        }
        finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void preservesCompatibilityWhenBlockingIsExplicitlyDisabled() {
        CocoTenantSqlProperties properties = new CocoTenantSqlProperties();
        properties.getInterceptorIgnore().setBlockUnlisted(false);
        List<CocoTenantInterceptorIgnoreEvent> events = new CopyOnWriteArrayList<>();
        SqlSessionFactory sqlSessionFactory = sqlSessionFactory(initializedDataSource(), properties, events);

        assertThat(selectAllIgnoringTenant(sqlSessionFactory)).containsExactly("A", "B");
        assertThat(events).singleElement()
                .extracting(CocoTenantInterceptorIgnoreEvent::decision)
                .isEqualTo(CocoTenantInterceptorIgnoreDecision.ALLOWED);
    }

    private static JdbcDataSource initializedDataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:tenant_bypass_" + System.nanoTime()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        dataSource.setUser("sa");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("create table tenant_record (id bigint primary key, tenant_id varchar(64), name varchar(64))");
            statement.executeUpdate("insert into tenant_record (id, tenant_id, name) values (1, 'tenant-a', 'A')");
            statement.executeUpdate("insert into tenant_record (id, tenant_id, name) values (2, 'tenant-b', 'B')");
        }
        catch (SQLException exception) {
            throw new IllegalStateException("Unable to initialize H2 tenant test data", exception);
        }
        return dataSource;
    }

    private static SqlSessionFactory sqlSessionFactory(DataSource dataSource, CocoTenantSqlProperties properties,
            List<CocoTenantInterceptorIgnoreEvent> events) {
        MybatisConfiguration configuration = new MybatisConfiguration(new Environment("test",
                new JdbcTransactionFactory(), dataSource));
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new CocoTenantInterceptorIgnoreGuard(properties, events::add));
        CocoTenantIdExpressionResolver expressionResolver = tenantContext -> new StringValue(tenantContext.tenantId());
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new CocoTenantLineHandler(properties,
                CocoTenantContextHolder::current, expressionResolver)));
        configuration.addInterceptor(interceptor);
        configuration.addMapper(TenantRecordMapper.class);
        return new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    private static List<String> selectTenantScoped(SqlSessionFactory sqlSessionFactory) {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            return sqlSession.getMapper(TenantRecordMapper.class).selectTenantScoped();
        }
    }

    private static List<String> selectAllIgnoringTenant(SqlSessionFactory sqlSessionFactory) {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            return sqlSession.getMapper(TenantRecordMapper.class).selectAllIgnoringTenant();
        }
    }

    interface TenantRecordMapper {

        @Select("select name from tenant_record order by id")
        List<String> selectTenantScoped();

        @InterceptorIgnore(tenantLine = "true")
        @Select("select name from tenant_record order by id")
        List<String> selectAllIgnoringTenant();
    }

    private static final class CountingDataSource implements DataSource {

        private final DataSource delegate;

        private final AtomicInteger connectionRequests = new AtomicInteger();

        private CountingDataSource(DataSource delegate) {
            this.delegate = delegate;
        }

        int connectionRequests() {
            return this.connectionRequests.get();
        }

        @Override
        public Connection getConnection() throws SQLException {
            this.connectionRequests.incrementAndGet();
            return this.delegate.getConnection();
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            this.connectionRequests.incrementAndGet();
            return this.delegate.getConnection(username, password);
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return this.delegate.getLogWriter();
        }

        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
            this.delegate.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            this.delegate.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return this.delegate.getLoginTimeout();
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getGlobal();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            return this.delegate.unwrap(iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return this.delegate.isWrapperFor(iface);
        }
    }
}
