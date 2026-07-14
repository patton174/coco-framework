package io.github.coco.feature.mybatisplus;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import io.github.coco.feature.mybatisplus.pagination.CocoMybatisPlusPaginationProperties;
import io.github.coco.feature.mybatisplus.sqlguard.CocoMybatisPlusSqlGuardProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class CocoMybatisPlusPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void nestedPropertyGettersRetainLiveJavaBeanMutationCompatibility() {
        CocoMybatisPlusProperties properties = new CocoMybatisPlusProperties();
        CocoMybatisPlusPaginationProperties firstPagination = properties.getPagination();
        CocoMybatisPlusPaginationProperties secondPagination = properties.getPagination();
        CocoMybatisPlusSqlGuardProperties firstSqlGuard = properties.getSqlGuard();
        CocoMybatisPlusSqlGuardProperties secondSqlGuard = properties.getSqlGuard();

        firstPagination.setEnabled(false);
        firstPagination.setDbType(" mysql ");
        firstPagination.setOverflow(true);
        firstPagination.setMaxLimit(200L);
        firstPagination.setOptimizeJoin(false);
        firstSqlGuard.setBlockAttackEnabled(true);
        firstSqlGuard.setIllegalSqlEnabled(true);

        assertThat(firstPagination).isSameAs(secondPagination);
        assertThat(secondPagination.isEnabled()).isFalse();
        assertThat(secondPagination.getDbType()).isEqualTo("mysql");
        assertThat(secondPagination.isOverflow()).isTrue();
        assertThat(secondPagination.getMaxLimit()).isEqualTo(200L);
        assertThat(secondPagination.isOptimizeJoin()).isFalse();
        assertThat(firstSqlGuard).isSameAs(secondSqlGuard);
        assertThat(secondSqlGuard.isBlockAttackEnabled()).isTrue();
        assertThat(secondSqlGuard.isIllegalSqlEnabled()).isTrue();
    }

    @Test
    void nestedPropertySettersCopyCallerOwnedBeans() {
        CocoMybatisPlusPaginationProperties pagination = pagination(false, "postgresql", true, 500L, false);
        CocoMybatisPlusSqlGuardProperties sqlGuard = sqlGuard(true, true);
        CocoMybatisPlusProperties properties = new CocoMybatisPlusProperties();

        properties.setPagination(pagination);
        properties.setSqlGuard(sqlGuard);
        pagination.setEnabled(true);
        pagination.setDbType("mysql");
        pagination.setOverflow(false);
        pagination.setMaxLimit(1L);
        pagination.setOptimizeJoin(true);
        sqlGuard.setBlockAttackEnabled(false);
        sqlGuard.setIllegalSqlEnabled(false);

        CocoMybatisPlusPaginationProperties storedPagination = properties.getPagination();
        assertThat(storedPagination.isEnabled()).isFalse();
        assertThat(storedPagination.getDbType()).isEqualTo("postgresql");
        assertThat(storedPagination.isOverflow()).isTrue();
        assertThat(storedPagination.getMaxLimit()).isEqualTo(500L);
        assertThat(storedPagination.isOptimizeJoin()).isFalse();
        assertThat(properties.getSqlGuard().isBlockAttackEnabled()).isTrue();
        assertThat(properties.getSqlGuard().isIllegalSqlEnabled()).isTrue();
    }

    @Test
    void cachedNestedPropertiesRemainDetachedAfterRootReplacement() {
        CocoMybatisPlusProperties properties = new CocoMybatisPlusProperties();
        CocoMybatisPlusPaginationProperties cachedPagination = properties.getPagination();
        CocoMybatisPlusSqlGuardProperties cachedSqlGuard = properties.getSqlGuard();
        CocoMybatisPlusPaginationProperties replacementPagination = pagination(false, "postgresql", true, 500L, false);
        CocoMybatisPlusSqlGuardProperties replacementSqlGuard = sqlGuard(true, true);

        properties.setPagination(replacementPagination);
        properties.setSqlGuard(replacementSqlGuard);
        cachedPagination.setEnabled(true);
        cachedPagination.setDbType("mysql");
        cachedSqlGuard.setBlockAttackEnabled(false);
        cachedSqlGuard.setIllegalSqlEnabled(false);

        assertThat(properties.getPagination()).isNotSameAs(cachedPagination);
        assertThat(properties.getPagination().isEnabled()).isFalse();
        assertThat(properties.getPagination().getDbType()).isEqualTo("postgresql");
        assertThat(properties.getSqlGuard()).isNotSameAs(cachedSqlGuard);
        assertThat(properties.getSqlGuard().isBlockAttackEnabled()).isTrue();
        assertThat(properties.getSqlGuard().isIllegalSqlEnabled()).isTrue();
    }

    @Test
    void nullRootReplacementUsesDefaultsAndDetachesCachedNestedProperties() {
        CocoMybatisPlusProperties properties = new CocoMybatisPlusProperties();
        CocoMybatisPlusPaginationProperties cachedPagination = properties.getPagination();
        CocoMybatisPlusSqlGuardProperties cachedSqlGuard = properties.getSqlGuard();

        properties.setPagination(null);
        properties.setSqlGuard(null);
        cachedPagination.setEnabled(false);
        cachedSqlGuard.setBlockAttackEnabled(true);

        assertThat(properties.getPagination()).isNotSameAs(cachedPagination);
        assertThat(properties.getPagination().isEnabled()).isTrue();
        assertThat(properties.getSqlGuard()).isNotSameAs(cachedSqlGuard);
        assertThat(properties.getSqlGuard().isBlockAttackEnabled()).isFalse();
    }

    @Test
    void snapshotsAreIndependentFromTheLiveNestedProperties() {
        CocoMybatisPlusProperties properties = new CocoMybatisPlusProperties();
        properties.setPagination(pagination(false, "postgresql", true, 500L, false));
        properties.setSqlGuard(sqlGuard(true, true));

        CocoMybatisPlusPaginationProperties paginationSnapshot = properties.paginationSnapshot();
        CocoMybatisPlusSqlGuardProperties sqlGuardSnapshot = properties.sqlGuardSnapshot();
        paginationSnapshot.setEnabled(true);
        paginationSnapshot.setDbType("mysql");
        sqlGuardSnapshot.setBlockAttackEnabled(false);
        sqlGuardSnapshot.setIllegalSqlEnabled(false);

        assertThat(properties.getPagination()).isNotSameAs(paginationSnapshot);
        assertThat(properties.getPagination().isEnabled()).isFalse();
        assertThat(properties.getPagination().getDbType()).isEqualTo("postgresql");
        assertThat(properties.getSqlGuard()).isNotSameAs(sqlGuardSnapshot);
        assertThat(properties.getSqlGuard().isBlockAttackEnabled()).isTrue();
        assertThat(properties.getSqlGuard().isIllegalSqlEnabled()).isTrue();
    }

    @Test
    void rootSettersPublishOnlyCompleteNestedPropertyStates() throws InterruptedException {
        CocoMybatisPlusProperties properties = new CocoMybatisPlusProperties();
        CocoMybatisPlusPaginationProperties firstPagination = pagination(false, "first", true, 100L, false);
        CocoMybatisPlusPaginationProperties secondPagination = pagination(true, "second", false, 200L, true);
        CocoMybatisPlusSqlGuardProperties firstSqlGuard = sqlGuard(false, true);
        CocoMybatisPlusSqlGuardProperties secondSqlGuard = sqlGuard(true, false);
        properties.setPagination(firstPagination);
        properties.setSqlGuard(firstSqlGuard);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<AssertionError> failure = new AtomicReference<>();
        Thread writer = new Thread(() -> {
            await(start);
            for (int index = 0; index < 100_000; index++) {
                properties.setPagination(firstPagination);
                properties.setSqlGuard(firstSqlGuard);
                properties.setPagination(secondPagination);
                properties.setSqlGuard(secondSqlGuard);
            }
        });
        Thread reader = new Thread(() -> {
            await(start);
            for (int index = 0; index < 100_000 && failure.get() == null; index++) {
                CocoMybatisPlusPaginationProperties pagination = properties.getPagination();
                if (!isFirstPagination(pagination) && !isSecondPagination(pagination)) {
                    failure.compareAndSet(null, new AssertionError("Observed mixed pagination state"));
                    return;
                }
                CocoMybatisPlusSqlGuardProperties sqlGuard = properties.getSqlGuard();
                if (!isFirstSqlGuard(sqlGuard) && !isSecondSqlGuard(sqlGuard)) {
                    failure.compareAndSet(null, new AssertionError("Observed mixed SQL guard state"));
                    return;
                }
            }
        });

        writer.start();
        reader.start();
        start.countDown();
        writer.join();
        reader.join();

        assertThat(failure.get()).isNull();
    }

    @Test
    void bindsEveryNestedPropertyThroughLiveJavaBeans() {
        this.contextRunner
                .withPropertyValues(
                        "coco.mybatis-plus.pagination.enabled=false",
                        "coco.mybatis-plus.pagination.db-type=mysql",
                        "coco.mybatis-plus.pagination.overflow=true",
                        "coco.mybatis-plus.pagination.max-limit=200",
                        "coco.mybatis-plus.pagination.optimize-join=false",
                        "coco.mybatis-plus.sql-guard.block-attack-enabled=true",
                        "coco.mybatis-plus.sql-guard.illegal-sql-enabled=true")
                .run(context -> {
                    CocoMybatisPlusProperties properties = context.getBean(CocoMybatisPlusProperties.class);
                    CocoMybatisPlusPaginationProperties pagination = properties.getPagination();

                    assertThat(pagination.isEnabled()).isFalse();
                    assertThat(pagination.getDbType()).isEqualTo("mysql");
                    assertThat(pagination.isOverflow()).isTrue();
                    assertThat(pagination.getMaxLimit()).isEqualTo(200L);
                    assertThat(pagination.isOptimizeJoin()).isFalse();
                    assertThat(properties.getSqlGuard().isBlockAttackEnabled()).isTrue();
                    assertThat(properties.getSqlGuard().isIllegalSqlEnabled()).isTrue();
                });
    }

    @Test
    void retainsOriginalJavaBeanConstructorAndAccessorSignatures() throws ReflectiveOperationException {
        Class<?> propertiesType = Class.forName("io.github.coco.feature.mybatisplus.CocoMybatisPlusProperties");

        assertThat(propertiesType.getConstructor().newInstance()).isInstanceOf(CocoMybatisPlusProperties.class);
        assertAccessor(propertiesType.getMethod("getPagination"),
                propertiesType.getMethod("setPagination", CocoMybatisPlusPaginationProperties.class),
                CocoMybatisPlusPaginationProperties.class);
        assertAccessor(propertiesType.getMethod("getSqlGuard"),
                propertiesType.getMethod("setSqlGuard", CocoMybatisPlusSqlGuardProperties.class),
                CocoMybatisPlusSqlGuardProperties.class);
    }

    private static void assertAccessor(Method getter, Method setter, Class<?> nestedPropertyType) {
        assertThat(getter.getReturnType()).isEqualTo(nestedPropertyType);
        assertThat(setter.getReturnType()).isEqualTo(void.class);
        assertThat(setter.getParameterTypes()).containsExactly(nestedPropertyType);
    }

    private static CocoMybatisPlusPaginationProperties pagination(boolean enabled, String dbType, boolean overflow,
            Long maxLimit, boolean optimizeJoin) {
        CocoMybatisPlusPaginationProperties properties = new CocoMybatisPlusPaginationProperties();
        properties.setEnabled(enabled);
        properties.setDbType(dbType);
        properties.setOverflow(overflow);
        properties.setMaxLimit(maxLimit);
        properties.setOptimizeJoin(optimizeJoin);
        return properties;
    }

    private static CocoMybatisPlusSqlGuardProperties sqlGuard(boolean blockAttackEnabled, boolean illegalSqlEnabled) {
        CocoMybatisPlusSqlGuardProperties properties = new CocoMybatisPlusSqlGuardProperties();
        properties.setBlockAttackEnabled(blockAttackEnabled);
        properties.setIllegalSqlEnabled(illegalSqlEnabled);
        return properties;
    }

    private static boolean isFirstPagination(CocoMybatisPlusPaginationProperties properties) {
        return !properties.isEnabled() && "first".equals(properties.getDbType()) && properties.isOverflow()
                && Long.valueOf(100L).equals(properties.getMaxLimit()) && !properties.isOptimizeJoin();
    }

    private static boolean isSecondPagination(CocoMybatisPlusPaginationProperties properties) {
        return properties.isEnabled() && "second".equals(properties.getDbType()) && !properties.isOverflow()
                && Long.valueOf(200L).equals(properties.getMaxLimit()) && properties.isOptimizeJoin();
    }

    private static boolean isFirstSqlGuard(CocoMybatisPlusSqlGuardProperties properties) {
        return !properties.isBlockAttackEnabled() && properties.isIllegalSqlEnabled();
    }

    private static boolean isSecondSqlGuard(CocoMybatisPlusSqlGuardProperties properties) {
        return properties.isBlockAttackEnabled() && !properties.isIllegalSqlEnabled();
    }

    private static void await(CountDownLatch start) {
        try {
            start.await();
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while awaiting concurrent test start", exception);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CocoMybatisPlusProperties.class)
    static class PropertiesConfiguration {
    }
}
