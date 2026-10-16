package io.github.coco.feature.mybatisplus.datasource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.coco.exception.type.CocoRequestException;
import io.github.coco.exception.type.CocoSystemException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Coco 动态多数据源基础组件单元测试。
 * <p>
 * 覆盖凭据脱敏、失败回收、负载均衡器、路由键解析器与嵌套上下文栈等无需 Spring 容器的行为。
 * </p>
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
class CocoDataSourceSupportTest {

    @AfterEach
    void tearDown() {
        CocoDataSourceContextHolder.clear();
    }

    @Test
    void definitionToStringRedactsPasswordAndPropertyValues() {
        Map<String, String> properties = new HashMap<>();
        properties.put("secret-token", "token-value");
        CocoDataSourceDefinition definition = new CocoDataSourceDefinition(
                "master", null, "jdbc:test://host/db", "app", "s3cr3t", null, null, properties);

        properties.put("late", "mutation");

        assertThat(definition.toString())
                .contains("master", "jdbc:test://host/db", "app", "******", "secret-token")
                .doesNotContain("s3cr3t")
                .doesNotContain("token-value");
        assertThat(definition.properties()).containsOnlyKeys("secret-token");
    }

    @Test
    void definitionRejectsBlankName() {
        assertThatThrownBy(() -> CocoDataSourceDefinition.of("  ", "jdbc:test://host/db", null, null))
                .isInstanceOf(CocoRequestException.class)
                .hasMessage("coco.feature.mybatis-plus.error.invalid-datasource-key");
    }

    @Test
    void creatorRejectsDefinitionWithoutUrl() {
        CocoDataSourceCreator creator = new DefaultCocoDataSourceCreator();

        assertThatThrownBy(() -> creator.createDataSource(
                new CocoDataSourceDefinition("master", null, null, null, null, null, null, Map.of())))
                .isInstanceOf(CocoRequestException.class)
                .hasMessage("coco.feature.mybatis-plus.error.invalid-datasource-definition");
    }

    @Test
    void providerClosesAlreadyCreatedDataSourcesWhenALaterOneFails() {
        CocoMybatisPlusDataSourceProperties properties = new CocoMybatisPlusDataSourceProperties();
        Map<String, CocoDataSourceDefinitionProperties> sources = new LinkedHashMap<>();
        sources.put("master", urlOnly("jdbc:test://host/master"));
        sources.put("broken", new CocoDataSourceDefinitionProperties());
        properties.setSources(sources);
        List<ClosableDataSource> created = new ArrayList<>();
        CocoDataSourceCreator creator = definition -> {
            if (definition.url() == null) {
                throw new IllegalStateException("broken definition");
            }
            ClosableDataSource dataSource = new ClosableDataSource();
            created.add(dataSource);
            return dataSource;
        };

        assertThatThrownBy(() -> new PropertiesCocoDataSourceProvider(properties, creator).loadDataSources())
                .isInstanceOf(IllegalStateException.class);

        assertThat(created).hasSize(1);
        assertThat(created.get(0).closed.get()).isTrue();
    }

    @Test
    void routingDataSourceClosesLoadedPoolsWhenPrimaryIsMissing() {
        ClosableDataSource loaded = new ClosableDataSource();
        CocoDataSourceProvider provider = () -> Map.of("slave_1", loaded);

        assertThatThrownBy(() -> new CocoRoutingDataSource(new CocoMybatisPlusDataSourceProperties(),
                List.of(provider), new RoundRobinCocoDataSourceLoadBalancer(),
                new ContextCocoDataSourceRouter()))
                .isInstanceOf(CocoSystemException.class)
                .hasMessage("coco.feature.mybatis-plus.error.primary-datasource-missing");
        assertThat(loaded.closed.get()).isTrue();
    }

    @Test
    void routingDataSourceClosesReplacedPoolWhenLaterProviderRedefinesName() {
        ClosableDataSource first = new ClosableDataSource();
        ClosableDataSource second = new ClosableDataSource();
        CocoDataSourceProvider firstProvider = () -> Map.of("master", first);
        CocoDataSourceProvider secondProvider = () -> Map.of("master", second);

        CocoRoutingDataSource routing = new CocoRoutingDataSource(new CocoMybatisPlusDataSourceProperties(),
                List.of(firstProvider, secondProvider), new RoundRobinCocoDataSourceLoadBalancer(),
                new ContextCocoDataSourceRouter());

        assertThat(first.closed.get()).isTrue();
        assertThat(second.closed.get()).isFalse();
        assertThat(routing.getDataSource("master")).containsSame(second);
        routing.close();
        assertThat(second.closed.get()).isTrue();
    }

    @Test
    void roundRobinCyclesPerGroupAndRandomStaysWithinCandidates() {
        RoundRobinCocoDataSourceLoadBalancer roundRobin = new RoundRobinCocoDataSourceLoadBalancer();
        List<String> candidates = List.of("slave_1", "slave_2", "slave_3");

        assertThat(List.of(
                roundRobin.choose("slave", candidates), roundRobin.choose("slave", candidates),
                roundRobin.choose("slave", candidates), roundRobin.choose("slave", candidates)))
                .containsExactly("slave_1", "slave_2", "slave_3", "slave_1");
        assertThat(roundRobin.choose("other", List.of("a", "b"))).isEqualTo("a");

        RandomCocoDataSourceLoadBalancer random = new RandomCocoDataSourceLoadBalancer();
        for (int index = 0; index < 50; index++) {
            assertThat(candidates).contains(random.choose("slave", candidates));
        }
        assertThatThrownBy(() -> roundRobin.choose("slave", List.of()))
                .isInstanceOf(CocoSystemException.class);
        assertThatThrownBy(() -> random.choose("slave", List.of()))
                .isInstanceOf(CocoSystemException.class);
    }

    @Test
    void keyResolverHandlesLiteralSpelAndRejectsInvalidKeys() throws NoSuchMethodException {
        CocoMybatisPlusDataSourceProperties properties = new CocoMybatisPlusDataSourceProperties();
        properties.setMaxKeyLength(10);
        CocoDataSourceKeyResolver resolver = new DefaultCocoDataSourceKeyResolver(properties);
        Method method = Probe.class.getDeclaredMethod("route", String.class);
        Object[] arguments = {"tenant"};

        assertThat(resolver.resolve(" master ", null, method, arguments)).isEqualTo("master");
        assertThat(resolver.resolve("#tenantKey", new Probe(), method, arguments)).isEqualTo("tenant");
        assertThat(resolver.resolve("#{#tenantKey}", new Probe(), method, arguments)).isEqualTo("tenant");
        assertThatThrownBy(() -> resolver.resolve("", null, method, arguments))
                .isInstanceOf(CocoRequestException.class);
        assertThatThrownBy(() -> resolver.resolve("#tenantKey", new Probe(), method, new Object[] {null}))
                .isInstanceOf(CocoRequestException.class);
        assertThatThrownBy(() -> resolver.resolve("#tenantKey", new Probe(), method, new Object[] {"x".repeat(11)}))
                .isInstanceOf(CocoRequestException.class);
        assertThatThrownBy(() -> resolver.resolve("#unknown.missing()", new Probe(), method, arguments))
                .isInstanceOf(CocoRequestException.class);
    }

    @Test
    void contextHolderRestoresNestedStackAndIgnoresDoubleClose() {
        CocoDataSourceScope outer = CocoDataSourceContextHolder.push("slave_1");
        CocoDataSourceScope inner = CocoDataSourceContextHolder.push("report");

        assertThat(CocoDataSourceContextHolder.snapshotStack()).containsExactly("report", "slave_1");
        inner.close();
        inner.close();
        assertThat(CocoDataSourceContextHolder.current()).contains("slave_1");
        outer.close();
        assertThat(CocoDataSourceContextHolder.current()).isEmpty();
        assertThatThrownBy(() -> CocoDataSourceContextHolder.push(" "))
                .isInstanceOf(CocoRequestException.class);
    }

    @Test
    void contextHolderRestoresStackWhenRunnableFails() {
        assertThatThrownBy(() -> CocoDataSourceContextHolder.runWith("report", () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(CocoDataSourceContextHolder.current()).isEmpty();
        assertThat(CocoDataSourceContextHolder.callWith("slave_1",
                () -> CocoDataSourceContextHolder.current().orElse("none"))).isEqualTo("slave_1");
    }

    private static CocoDataSourceDefinitionProperties urlOnly(String url) {
        CocoDataSourceDefinitionProperties properties = new CocoDataSourceDefinitionProperties();
        properties.setUrl(url);
        return properties;
    }

    static final class Probe {

        String route(String tenantKey) {
            return tenantKey;
        }
    }

    static final class ClosableDataSource implements DataSource, AutoCloseable {

        private final AtomicBoolean closed = new AtomicBoolean(false);

        @Override
        public void close() {
            this.closed.set(true);
        }

        @Override
        public Connection getConnection() {
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[] {Connection.class}, (proxy, method, args) -> null);
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
            throw new SQLException("unsupported");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
