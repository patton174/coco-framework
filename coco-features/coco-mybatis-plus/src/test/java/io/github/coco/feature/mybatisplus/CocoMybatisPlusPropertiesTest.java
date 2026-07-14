package io.github.coco.feature.mybatisplus;

import static org.assertj.core.api.Assertions.assertThat;

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
    void nestedViewsPreserveMutableApiWithoutExposingTheBackingBeans() {
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

        assertThat(firstPagination).isNotSameAs(secondPagination);
        assertThat(secondPagination.isEnabled()).isFalse();
        assertThat(secondPagination.getDbType()).isEqualTo("mysql");
        assertThat(secondPagination.isOverflow()).isTrue();
        assertThat(secondPagination.getMaxLimit()).isEqualTo(200L);
        assertThat(secondPagination.isOptimizeJoin()).isFalse();
        assertThat(firstSqlGuard).isNotSameAs(secondSqlGuard);
        assertThat(secondSqlGuard.isBlockAttackEnabled()).isTrue();
        assertThat(secondSqlGuard.isIllegalSqlEnabled()).isTrue();
    }

    @Test
    void nestedPropertySettersCopyCallerOwnedBeans() {
        CocoMybatisPlusPaginationProperties pagination = new CocoMybatisPlusPaginationProperties();
        pagination.setEnabled(false);
        pagination.setDbType("postgresql");
        pagination.setOverflow(true);
        pagination.setMaxLimit(500L);
        pagination.setOptimizeJoin(false);
        CocoMybatisPlusSqlGuardProperties sqlGuard = new CocoMybatisPlusSqlGuardProperties();
        sqlGuard.setBlockAttackEnabled(true);
        sqlGuard.setIllegalSqlEnabled(true);
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
    void bindsEveryNestedPropertyThroughTheViews() {
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

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CocoMybatisPlusProperties.class)
    static class PropertiesConfiguration {
    }
}
