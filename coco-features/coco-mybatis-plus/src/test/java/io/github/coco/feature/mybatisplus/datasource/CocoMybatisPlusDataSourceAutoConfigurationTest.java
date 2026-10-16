package io.github.coco.feature.mybatisplus.datasource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.zaxxer.hikari.HikariDataSource;
import io.github.coco.common.autoconfigure.CocoCommonAutoConfiguration;
import io.github.coco.exception.type.CocoConflictException;
import io.github.coco.exception.type.CocoRequestException;
import io.github.coco.exception.type.CocoSystemException;
import io.github.coco.feature.mybatisplus.CocoMybatisPlusAutoConfiguration;
import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Coco MyBatis-Plus 动态多数据源自动配置与运行时路由测试。
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-mybatis-plus}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@ExtendWith(OutputCaptureExtension.class)
class CocoMybatisPlusDataSourceAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    CocoCommonAutoConfiguration.class,
                    CocoMybatisPlusDataSourceAutoConfiguration.class,
                    CocoMybatisPlusAutoConfiguration.class))
            .withPropertyValues("coco.common.i18n.basename=coco-messages");

    @AfterEach
    void tearDown() {
        CocoDataSourceContextHolder.clear();
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void staysDisabledByDefault() {
        this.contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(CocoRoutingDataSource.class);
            assertThat(context).doesNotHaveBean(CocoDataSourceAspect.class);
        });
    }

    @Test
    void ordersAutoConfigurationBeforeDataSourceAndMybatisPlus() {
        AutoConfiguration annotation = CocoMybatisPlusDataSourceAutoConfiguration.class
                .getAnnotation(AutoConfiguration.class);

        assertThat(annotation.before())
                .containsExactly(DataSourceAutoConfiguration.class, MybatisPlusAutoConfiguration.class);
    }

    @Test
    void createsPrimaryRoutingDataSourceAndBindsHikariProperties() {
        this.contextRunner
                .withPropertyValues(
                        "coco.mybatis-plus.datasource.enabled=true",
                        "coco.mybatis-plus.datasource.primary=master",
                        "coco.mybatis-plus.datasource.sources.master.url=jdbc:coco-test://127.0.0.1:5432/master_db",
                        "coco.mybatis-plus.datasource.sources.master.driver-class-name="
                                + StubJdbcDriver.class.getName(),
                        "coco.mybatis-plus.datasource.sources.master.username=postgres",
                        "coco.mybatis-plus.datasource.sources.master.password=secret",
                        "coco.mybatis-plus.datasource.sources.master.properties.maximum-pool-size=15",
                        "coco.mybatis-plus.datasource.sources.slave_1.url=jdbc:coco-test://127.0.0.1:5432/slave_1_db",
                        "coco.mybatis-plus.datasource.sources.slave_2.url=jdbc:coco-test://127.0.0.1:5432/slave_2_db")
                .run(context -> {
                    assertThat(context).hasSingleBean(DataSource.class);
                    assertThat(context).hasSingleBean(CocoRoutingDataSource.class);
                    CocoRoutingDataSource routingDataSource = context.getBean(CocoRoutingDataSource.class);

                    assertThat(context.getBean("dataSource")).isSameAs(routingDataSource);
                    assertThat(routingDataSource.getPrimary()).isEqualTo("master");
                    assertThat(routingDataSource.getDataSourceNames())
                            .containsExactly("master", "slave_1", "slave_2");
                    assertThat(routingDataSource.getGroupDataSourceNames())
                            .containsEntry("slave", List.of("slave_1", "slave_2"));

                    DataSource master = routingDataSource.getDataSource("master").orElseThrow();
                    assertThat(master).isInstanceOf(HikariDataSource.class);
                    HikariDataSource hikariMaster = (HikariDataSource) master;
                    assertThat(hikariMaster.getJdbcUrl()).isEqualTo("jdbc:coco-test://127.0.0.1:5432/master_db");
                    assertThat(hikariMaster.getDriverClassName()).isEqualTo(StubJdbcDriver.class.getName());
                    assertThat(hikariMaster.getMaximumPoolSize()).isEqualTo(15);
                    assertThat(hikariMaster.getPoolName()).isEqualTo("CocoHikariPool-master");
                });
    }

    @Test
    void routesDeclarativeNestedSpelAndMapperInterfaceCalls() {
        this.contextRunner
                .withUserConfiguration(AopAndRoutingTestConfiguration.class)
                .withPropertyValues(
                        "coco.mybatis-plus.datasource.enabled=true",
                        "coco.mybatis-plus.datasource.primary=master")
                .run(context -> {
                    RoutingProbeService service = context.getBean(RoutingProbeService.class);
                    SampleMapper mapper = context.getBean(SampleMapper.class);

                    assertThat(service.defaultRoute()).isEqualTo("conn:master");
                    assertThat(service.classDefaultRoute()).isEqualTo("conn:slave_1");
                    assertThat(service.methodOverrideRoute()).isEqualTo("conn:report");
                    assertThat(service.spelRoute("analytics")).isEqualTo("conn:analytics");
                    assertThat(service.nestedRoute()).containsExactly(
                            "conn:slave_1",
                            "conn:report",
                            "conn:slave_1");
                    assertThat(CocoDataSourceContextHolder.current()).isEmpty();

                    assertThat(mapper.selectFromClassDefault()).isEqualTo("conn:slave_1");
                    assertThat(mapper.selectFromReport()).isEqualTo("conn:report");
                    assertThat(mapper.selectByTenant("analytics")).isEqualTo("conn:analytics");
                });
    }

    @Test
    void balancesGroupCandidatesAndSupportsRuntimeRegistrationAndRemoval() {
        this.contextRunner
                .withUserConfiguration(AopAndRoutingTestConfiguration.class)
                .withPropertyValues(
                        "coco.mybatis-plus.datasource.enabled=true",
                        "coco.mybatis-plus.datasource.primary=master")
                .run(context -> {
                    CocoDataSourceRegistry registry = context.getBean(CocoDataSourceRegistry.class);
                    RoutingProbeService service = context.getBean(RoutingProbeService.class);

                    assertThat(service.slaveGroupRoute()).isEqualTo("conn:slave_1");
                    assertThat(service.slaveGroupRoute()).isEqualTo("conn:slave_2");
                    assertThat(service.slaveGroupRoute()).isEqualTo("conn:slave_1");

                    StubDataSource dynamicSource = new StubDataSource("dynamic_node");
                    registry.registerDataSource("slave_3", "slave", dynamicSource);
                    assertThat(registry.containsDataSource("slave_3")).isTrue();
                    assertThat(registry.getGroupDataSourceNames().get("slave"))
                            .containsExactly("slave_1", "slave_2", "slave_3");

                    assertThat(registry.unregisterDataSource("slave_3")).isTrue();
                    assertThat(dynamicSource.isClosed()).isTrue();
                    assertThat(registry.containsDataSource("slave_3")).isFalse();

                    assertThatThrownBy(() -> registry.unregisterDataSource("master"))
                            .isInstanceOf(CocoConflictException.class)
                            .hasMessage("coco.feature.mybatis-plus.error.primary-datasource-unremovable");
                });
    }

    @Test
    void enforcesMissingDataSourcePolicies(CapturedOutput output) {
        this.contextRunner
                .withUserConfiguration(AopAndRoutingTestConfiguration.class)
                .withPropertyValues(
                        "coco.mybatis-plus.datasource.enabled=true",
                        "coco.mybatis-plus.datasource.primary=master",
                        "coco.mybatis-plus.datasource.missing-policy=fail")
                .run(context -> {
                    RoutingProbeService service = context.getBean(RoutingProbeService.class);

                    assertThatThrownBy(() -> service.spelRoute("non_existent"))
                            .isInstanceOf(CocoSystemException.class)
                            .hasMessage("coco.feature.mybatis-plus.error.datasource-not-found");
                });

        this.contextRunner
                .withUserConfiguration(AopAndRoutingTestConfiguration.class)
                .withPropertyValues(
                        "coco.mybatis-plus.datasource.enabled=true",
                        "coco.mybatis-plus.datasource.primary=master",
                        "coco.mybatis-plus.datasource.missing-policy=fallback-primary")
                .run(context -> {
                    RoutingProbeService service = context.getBean(RoutingProbeService.class);

                    assertThat(service.spelRoute("non_existent")).isEqualTo("conn:master");
                    assertThat(output).contains("falling back to primary datasource 'master'");
                });
    }

    @Test
    void enforcesTransactionConflictPolicies(CapturedOutput output) {
        this.contextRunner
                .withUserConfiguration(AopAndRoutingTestConfiguration.class)
                .withPropertyValues(
                        "coco.mybatis-plus.datasource.enabled=true",
                        "coco.mybatis-plus.datasource.primary=master",
                        "coco.mybatis-plus.datasource.transaction-policy=reject")
                .run(context -> {
                    RoutingProbeService service = context.getBean(RoutingProbeService.class);
                    TransactionSynchronizationManager.setActualTransactionActive(true);
                    try {
                        assertThat(service.sameMasterRoute()).isEqualTo("conn:master");
                        assertThatThrownBy(service::methodOverrideRoute)
                                .isInstanceOf(CocoConflictException.class)
                                .hasMessage("coco.feature.mybatis-plus.error.datasource-transaction-conflict");
                    }
                    finally {
                        TransactionSynchronizationManager.setActualTransactionActive(false);
                    }
                });

        this.contextRunner
                .withUserConfiguration(AopAndRoutingTestConfiguration.class)
                .withPropertyValues(
                        "coco.mybatis-plus.datasource.enabled=true",
                        "coco.mybatis-plus.datasource.primary=master",
                        "coco.mybatis-plus.datasource.transaction-policy=warn")
                .run(context -> {
                    RoutingProbeService service = context.getBean(RoutingProbeService.class);
                    TransactionSynchronizationManager.setActualTransactionActive(true);
                    try {
                        assertThat(service.methodOverrideRoute()).isEqualTo("conn:report");
                        assertThat(output).contains("detected datasource switch from 'master' to 'report'");
                    }
                    finally {
                        TransactionSynchronizationManager.setActualTransactionActive(false);
                    }
                });
    }

    @Test
    void rejectsBlankOrOverlongSpelKeysAndMissingPrimaryOnStartup() {
        this.contextRunner
                .withUserConfiguration(AopAndRoutingTestConfiguration.class)
                .withPropertyValues(
                        "coco.mybatis-plus.datasource.enabled=true",
                        "coco.mybatis-plus.datasource.primary=master",
                        "coco.mybatis-plus.datasource.max-key-length=8")
                .run(context -> {
                    RoutingProbeService service = context.getBean(RoutingProbeService.class);

                    assertThatThrownBy(() -> service.spelRoute("   "))
                            .isInstanceOf(CocoRequestException.class)
                            .hasMessage("coco.feature.mybatis-plus.error.invalid-datasource-key");
                    assertThatThrownBy(() -> service.spelRoute("key_longer_than_8"))
                            .isInstanceOf(CocoRequestException.class)
                            .hasMessage("coco.feature.mybatis-plus.error.invalid-datasource-key");
                });

        this.contextRunner
                .withPropertyValues(
                        "coco.mybatis-plus.datasource.enabled=true",
                        "coco.mybatis-plus.datasource.primary=master")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(CocoSystemException.class)
                            .hasMessage("coco.feature.mybatis-plus.error.primary-datasource-missing");
                });
    }

    @Test
    void propagatesContextStackAcrossWrappedTasksAndSupportsCustomRouterSpi() throws Exception {
        CocoDataSourceContextHolder.runWith("slave_1", () -> {
            CocoDataSourceContextHolder.runWith("report", () -> {
                Runnable wrappedRunnable = CocoDataSourceContextHolder.wrap((Runnable) () -> {
                    assertThat(CocoDataSourceContextHolder.snapshotStack())
                            .containsExactly("report", "slave_1");
                });
                Callable<String> wrappedCallable = CocoDataSourceContextHolder.wrap((Callable<String>) () ->
                        CocoDataSourceContextHolder.current().orElse("none"));
                Supplier<String> wrappedSupplier = CocoDataSourceContextHolder.wrapSupplier(() ->
                        CocoDataSourceContextHolder.current().orElse("none"));

                CocoDataSourceContextHolder.clear();
                assertThat(CocoDataSourceContextHolder.current()).isEmpty();

                wrappedRunnable.run();
                try {
                    assertThat(wrappedCallable.call()).isEqualTo("report");
                }
                catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
                assertThat(wrappedSupplier.get()).isEqualTo("report");
                assertThat(CocoDataSourceContextHolder.current()).isEmpty();
            });
        });

        this.contextRunner
                .withUserConfiguration(AopAndRoutingTestConfiguration.class, CustomRouterConfiguration.class)
                .withPropertyValues(
                        "coco.mybatis-plus.datasource.enabled=true",
                        "coco.mybatis-plus.datasource.primary=master")
                .run(context -> {
                    RoutingProbeService service = context.getBean(RoutingProbeService.class);

                    assertThat(service.defaultRoute()).isEqualTo("conn:analytics");
                    assertThat(service.methodOverrideRoute()).isEqualTo("conn:report");
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class AopAndRoutingTestConfiguration {

        @Bean
        static DefaultAdvisorAutoProxyCreator defaultAdvisorAutoProxyCreator() {
            DefaultAdvisorAutoProxyCreator creator = new DefaultAdvisorAutoProxyCreator();
            creator.setProxyTargetClass(true);
            return creator;
        }

        @Bean
        CocoDataSourceProvider testDataSourceProvider() {
            return new CocoDataSourceProvider() {
                @Override
                public Map<String, DataSource> loadDataSources() {
                    Map<String, DataSource> sources = new java.util.LinkedHashMap<>();
                    sources.put("master", new StubDataSource("master"));
                    sources.put("slave_1", new StubDataSource("slave_1"));
                    sources.put("slave_2", new StubDataSource("slave_2"));
                    sources.put("report", new StubDataSource("report"));
                    sources.put("analytics", new StubDataSource("analytics"));
                    return sources;
                }

                @Override
                public Map<String, String> loadDataSourceGroups() {
                    Map<String, String> groups = new java.util.LinkedHashMap<>();
                    groups.put("slave_1", "slave");
                    groups.put("slave_2", "slave");
                    return groups;
                }
            };
        }

        @Bean
        NestedProbeService nestedProbeService(DataSource dataSource) {
            return new NestedProbeService(dataSource);
        }

        @Bean
        RoutingProbeService routingProbeService(DataSource dataSource, NestedProbeService nestedProbeService) {
            return new RoutingProbeService(dataSource, nestedProbeService);
        }

        @Bean
        FactoryBean<SampleMapper> sampleMapper(DataSource dataSource) {
            return new FactoryBean<>() {
                @Override
                public SampleMapper getObject() {
                    InvocationHandler handler = (proxy, method, args) -> {
                        if (method.getDeclaringClass() == Object.class) {
                            return switch (method.getName()) {
                                case "toString" -> "SampleMapperProxy";
                                case "hashCode" -> System.identityHashCode(proxy);
                                case "equals" -> proxy == args[0];
                                default -> null;
                            };
                        }
                        return currentConnectionId(dataSource);
                    };
                    return (SampleMapper) Proxy.newProxyInstance(
                            SampleMapper.class.getClassLoader(),
                            new Class<?>[] {SampleMapper.class},
                            handler);
                }

                @Override
                public Class<?> getObjectType() {
                    return SampleMapper.class;
                }
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomRouterConfiguration {

        @Bean
        CocoDataSourceRouter customDataSourceRouter() {
            return () -> CocoDataSourceContextHolder.current().or(() -> Optional.of("analytics"));
        }
    }

    @CocoDataSource("slave_1")
    interface SampleMapper {

        String selectFromClassDefault();

        @CocoDataSource("report")
        String selectFromReport();

        @CocoDataSource("#tenantKey")
        String selectByTenant(String tenantKey);
    }

    @CocoDataSource("slave_1")
    static class NestedProbeService {

        private final DataSource dataSource;

        NestedProbeService(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        public String classDefaultRoute() {
            return currentConnectionId(this.dataSource);
        }

        @CocoDataSource("report")
        public String queryReport() {
            return currentConnectionId(this.dataSource);
        }
    }

    static class RoutingProbeService {

        private final DataSource dataSource;

        private final NestedProbeService nestedProbeService;

        RoutingProbeService(DataSource dataSource, NestedProbeService nestedProbeService) {
            this.dataSource = dataSource;
            this.nestedProbeService = nestedProbeService;
        }

        public String defaultRoute() {
            return currentConnectionId(this.dataSource);
        }

        public String classDefaultRoute() {
            return this.nestedProbeService.classDefaultRoute();
        }

        @CocoDataSource("master")
        public String sameMasterRoute() {
            return currentConnectionId(this.dataSource);
        }

        @CocoDataSource("report")
        public String methodOverrideRoute() {
            return currentConnectionId(this.dataSource);
        }

        @CocoDataSource("slave")
        public String slaveGroupRoute() {
            return currentConnectionId(this.dataSource);
        }

        @CocoDataSource("#targetKey")
        public String spelRoute(String targetKey) {
            return currentConnectionId(this.dataSource);
        }

        @CocoDataSource("slave_1")
        public List<String> nestedRoute() {
            List<String> steps = new ArrayList<>();
            steps.add(currentConnectionId(this.dataSource));
            steps.add(this.nestedProbeService.queryReport());
            steps.add(currentConnectionId(this.dataSource));
            return steps;
        }
    }

    private static String currentConnectionId(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            return connection.getCatalog();
        }
        catch (SQLException ex) {
            throw new IllegalStateException(ex);
        }
    }

    static final class StubDataSource implements DataSource, AutoCloseable {

        private final String id;

        private final AtomicBoolean closed = new AtomicBoolean(false);

        StubDataSource(String id) {
            this.id = id;
        }

        boolean isClosed() {
            return this.closed.get();
        }

        @Override
        public void close() {
            this.closed.set(true);
        }

        @Override
        public Connection getConnection() {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[] {Connection.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getCatalog" -> "conn:" + this.id;
                        case "close" -> null;
                        case "isClosed" -> false;
                        default -> null;
                    });
        }

        @Override
        public Connection getConnection(String username, String password) {
            return getConnection();
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getGlobal();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) {
                return iface.cast(this);
            }
            throw new SQLException("Unsupported unwrap type: " + iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return iface.isInstance(this);
        }
    }

    public static final class StubJdbcDriver implements java.sql.Driver {

        @Override
        public Connection connect(String url, java.util.Properties info) {
            return null;
        }

        @Override
        public boolean acceptsURL(String url) {
            return url != null && url.startsWith("jdbc:coco-test:");
        }

        @Override
        public java.sql.DriverPropertyInfo[] getPropertyInfo(String url, java.util.Properties info) {
            return new java.sql.DriverPropertyInfo[0];
        }

        @Override
        public int getMajorVersion() {
            return 1;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public boolean jdbcCompliant() {
            return false;
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getGlobal();
        }
    }
}
