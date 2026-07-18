package io.github.coco.feature.datapermission.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import javax.sql.DataSource;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.coco.common.autoconfigure.CocoCommonAutoConfiguration;
import io.github.coco.exception.type.CocoForbiddenException;
import io.github.coco.exception.type.CocoRequestException;
import io.github.coco.feature.datapermission.CocoDataPermissionAutoConfiguration;
import io.github.coco.feature.datapermission.context.CocoDataPermissionContext;
import io.github.coco.feature.datapermission.context.CocoDataPermissionContextHolder;
import io.github.coco.feature.datapermission.context.CocoDataPermissionRule;
import io.github.coco.feature.datapermission.context.CocoDataScope;
import io.github.coco.feature.datapermission.integration.fixture.ComposedDocument;
import io.github.coco.feature.datapermission.integration.fixture.ComposedDocumentMapper;
import io.github.coco.feature.datapermission.integration.fixture.RecordingDataSource;
import io.github.coco.feature.datapermission.mybatisplus.CocoDataPermissionMybatisPlusAutoConfiguration;
import io.github.coco.feature.mybatisplus.CocoMybatisPlusAutoConfiguration;
import io.github.coco.feature.tenant.CocoTenantAutoConfiguration;
import io.github.coco.feature.tenant.context.CocoTenantContext;
import io.github.coco.feature.tenant.context.CocoTenantContextHolder;
import io.github.coco.feature.tenant.sql.CocoTenantMybatisPlusAutoConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

class CocoPersistenceComposedSqlIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    MybatisPlusAutoConfiguration.class,
                    CocoCommonAutoConfiguration.class,
                    CocoMybatisPlusAutoConfiguration.class,
                    CocoTenantAutoConfiguration.class,
                    CocoTenantMybatisPlusAutoConfiguration.class,
                    CocoDataPermissionAutoConfiguration.class,
                    CocoDataPermissionMybatisPlusAutoConfiguration.class))
            .withUserConfiguration(FixtureConfiguration.class)
            .withPropertyValues(
                    "coco.common.i18n.basename=coco-messages",
                    "coco.mybatis-plus.pagination.db-type=h2",
                    "coco.data-permission.sql.enabled=true",
                    "coco.data-permission.sql.resources.document.tables[0]=documents",
                    "coco.data-permission.sql.resources.document.tables[1]=\"tenant_a\".\"documents\"",
                    "coco.data-permission.sql.resources.document.column=department_id",
                    "coco.data-permission.sql.resources.document.column-type=long",
                    "coco.tenant.sql.interceptor-ignore.allowed-mapped-statements[0]="
                            + "io.github.coco.feature.datapermission.integration.fixture."
                            + "ComposedDocumentMapper.selectIgnoringTenant");

    @AfterEach
    void clearContexts() {
        CocoTenantContextHolder.clear();
        CocoDataPermissionContextHolder.clear();
    }

    @Test
    void selectComposesTenantAndDataPermissionInExecutedSql() {
        this.contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            RecordingDataSource dataSource = context.getBean(RecordingDataSource.class);
            initializeDatabase(dataSource);
            ComposedDocumentMapper mapper = context.getBean(ComposedDocumentMapper.class);

            List<ComposedDocument> documents = withContexts(() -> mapper.selectList(
                    Wrappers.<ComposedDocument>lambdaQuery()
                            .eq(ComposedDocument::getArchived, false)
                            .orderByAsc(ComposedDocument::getId)));

            assertThat(documents).extracting(ComposedDocument::getId).containsExactly(1L);
            assertThat(dataSource.preparedSql()).singleElement().satisfies(sql -> assertThat(sql)
                    .containsIgnoringCase("tenant_id = 'tenant-a'")
                    .containsIgnoringCase("documents.department_id IN (10)"));
        });
    }

    @Test
    void updateComposesTenantAndDataPermissionInExecutedSql() {
        this.contextRunner.run(context -> {
            RecordingDataSource dataSource = context.getBean(RecordingDataSource.class);
            initializeDatabase(dataSource);
            ComposedDocumentMapper mapper = context.getBean(ComposedDocumentMapper.class);

            int updated = withContexts(() -> mapper.renameVisibleDocuments("renamed"));
            String executedSql = onlyPreparedSql(dataSource);
            List<Long> renamedIds = new JdbcTemplate(dataSource)
                    .queryForList("SELECT id FROM documents WHERE title = 'renamed' ORDER BY id", Long.class);

            assertThat(updated).isOne();
            assertThat(renamedIds).containsExactly(1L);
            assertThat(executedSql)
                    .containsIgnoringCase("tenant_id = 'tenant-a'")
                    .containsIgnoringCase("documents.department_id IN (10)");
        });
    }

    @Test
    void deleteComposesTenantAndDataPermissionInExecutedSql() {
        this.contextRunner.run(context -> {
            RecordingDataSource dataSource = context.getBean(RecordingDataSource.class);
            initializeDatabase(dataSource);
            ComposedDocumentMapper mapper = context.getBean(ComposedDocumentMapper.class);

            int deleted = withContexts(mapper::deleteArchivedDocuments);
            String executedSql = onlyPreparedSql(dataSource);
            List<Long> remainingIds = new JdbcTemplate(dataSource)
                    .queryForList("SELECT id FROM documents ORDER BY id", Long.class);

            assertThat(deleted).isOne();
            assertThat(remainingIds).containsExactly(1L, 2L, 3L, 5L, 6L);
            assertThat(executedSql)
                    .containsIgnoringCase("tenant_id = 'tenant-a'")
                    .containsIgnoringCase("documents.department_id IN (10)");
        });
    }

    @Test
    void joinComposesTenantForEveryTableAndDataPermissionForResourceTable() {
        this.contextRunner.run(context -> {
            RecordingDataSource dataSource = context.getBean(RecordingDataSource.class);
            initializeDatabase(dataSource);
            ComposedDocumentMapper mapper = context.getBean(ComposedDocumentMapper.class);

            List<ComposedDocument> documents = withContexts(() -> mapper.selectByTagJoin("blue"));
            String executedSql = onlyPreparedSql(dataSource);

            assertThat(documents).extracting(ComposedDocument::getId).containsExactly(1L);
            assertThat(executedSql)
                    .containsIgnoringCase("d.department_id IN (10)")
                    .containsIgnoringCase("d.tenant_id = 'tenant-a'")
                    .containsIgnoringCase("t.tenant_id = 'tenant-a'");
        });
    }

    @Test
    void subqueryComposesTenantForEveryLevelAndDataPermissionForResourceTable() {
        this.contextRunner.run(context -> {
            RecordingDataSource dataSource = context.getBean(RecordingDataSource.class);
            initializeDatabase(dataSource);
            ComposedDocumentMapper mapper = context.getBean(ComposedDocumentMapper.class);

            List<ComposedDocument> documents = withContexts(() -> mapper.selectByTagSubquery("blue"));
            String executedSql = onlyPreparedSql(dataSource);

            assertThat(documents).extracting(ComposedDocument::getId).containsExactly(1L);
            assertThat(executedSql)
                    .containsIgnoringCase("d.department_id IN (10)")
                    .containsIgnoringCase("d.tenant_id = 'tenant-a'")
                    .containsIgnoringCase("t.tenant_id = 'tenant-a'");
        });
    }

    @Test
    void quotedSchemaJoinComposesTenantAndDataPermissionInExecutedSql() {
        this.contextRunner.run(context -> {
            RecordingDataSource dataSource = context.getBean(RecordingDataSource.class);
            initializeDatabase(dataSource);
            ComposedDocumentMapper mapper = context.getBean(ComposedDocumentMapper.class);

            List<ComposedDocument> documents = withContexts(() -> mapper.selectQuotedByTagJoin("blue"));
            String executedSql = onlyPreparedSql(dataSource);

            assertThat(documents).extracting(ComposedDocument::getId).containsExactly(1L);
            assertThat(executedSql)
                    .containsIgnoringCase("tenant_id = 'tenant-a'")
                    .containsIgnoringCase("department_id IN (10)")
                    .contains("\"tenant_a\".\"documents\"");
        });
    }

    @Test
    void quotedSchemaSubqueryComposesTenantAndDataPermissionInExecutedSql() {
        this.contextRunner.run(context -> {
            RecordingDataSource dataSource = context.getBean(RecordingDataSource.class);
            initializeDatabase(dataSource);
            ComposedDocumentMapper mapper = context.getBean(ComposedDocumentMapper.class);

            List<ComposedDocument> documents = withContexts(() -> mapper.selectQuotedByTagSubquery("blue"));
            String executedSql = onlyPreparedSql(dataSource);

            assertThat(documents).extracting(ComposedDocument::getId).containsExactly(1L);
            assertThat(executedSql)
                    .containsIgnoringCase("tenant_id = 'tenant-a'")
                    .containsIgnoringCase("department_id IN (10)")
                    .contains("\"tenant_a\".\"document_tags\"");
        });
    }

    @Test
    void paginationAppliesBothPredicatesToCountAndPageSql() {
        this.contextRunner.run(context -> {
            RecordingDataSource dataSource = context.getBean(RecordingDataSource.class);
            initializeDatabase(dataSource);
            ComposedDocumentMapper mapper = context.getBean(ComposedDocumentMapper.class);

            Page<ComposedDocument> page = withContexts(() -> mapper.selectPage(new Page<>(2, 1),
                    Wrappers.<ComposedDocument>lambdaQuery().orderByAsc(ComposedDocument::getId)));

            assertThat(page.getTotal()).isEqualTo(2L);
            assertThat(page.getRecords()).extracting(ComposedDocument::getId).containsExactly(4L);
            assertThat(dataSource.preparedSql()).hasSize(2).allSatisfy(sql -> assertThat(sql)
                    .containsIgnoringCase("tenant_id = 'tenant-a'")
                    .containsIgnoringCase("documents.department_id IN (10)"));
            assertThat(dataSource.preparedSql()).anySatisfy(sql -> assertThat(sql)
                    .containsIgnoringCase("COUNT(*)"));
            assertThat(dataSource.preparedSql()).anySatisfy(sql -> assertThat(sql)
                    .containsIgnoringCase("LIMIT ? OFFSET ?"));
        });
    }

    @Test
    void allowlistedMapperTenantIgnoreKeepsDataPermissionActive() {
        this.contextRunner.run(context -> {
            RecordingDataSource dataSource = context.getBean(RecordingDataSource.class);
            initializeDatabase(dataSource);
            ComposedDocumentMapper mapper = context.getBean(ComposedDocumentMapper.class);
            CocoDataPermissionContext permissionContext = permissionContext();

            List<ComposedDocument> documents = CocoDataPermissionContextHolder.callWithContext(permissionContext,
                    mapper::selectIgnoringTenant);
            String executedSql = onlyPreparedSql(dataSource);

            assertThat(documents).extracting(ComposedDocument::getId).containsExactly(1L, 3L);
            assertThat(executedSql)
                    .containsIgnoringCase("documents.department_id IN (10)")
                    .doesNotContain("tenant_id = 'tenant-a'");
        });
    }

    @Test
    void unlistedMapperTenantIgnoreIsBlockedBeforeJdbcExecution() {
        this.contextRunner.run(context -> {
            RecordingDataSource dataSource = context.getBean(RecordingDataSource.class);
            initializeDatabase(dataSource);
            ComposedDocumentMapper mapper = context.getBean(ComposedDocumentMapper.class);

            Throwable failure = catchThrowable(() -> withContexts(mapper::countWithUnlistedTenantIgnore));

            assertThat(rootCause(failure))
                    .isInstanceOf(CocoForbiddenException.class)
                    .hasMessage("coco.feature.tenant.error.interceptor-ignore-blocked");
            assertThat(dataSource.preparedSql()).isEmpty();
        });
    }

    @Test
    void missingTenantContextFailsClosedBeforeJdbcExecution() {
        this.contextRunner.run(context -> {
            RecordingDataSource dataSource = context.getBean(RecordingDataSource.class);
            initializeDatabase(dataSource);
            ComposedDocumentMapper mapper = context.getBean(ComposedDocumentMapper.class);

            Throwable failure = catchThrowable(() -> CocoDataPermissionContextHolder.callWithContext(
                    permissionContext(), () -> mapper.selectList(null)));

            assertThat(rootCause(failure))
                    .isInstanceOf(CocoRequestException.class)
                    .hasMessage("coco.feature.tenant.error.context-missing");
            assertThat(dataSource.preparedSql()).isEmpty();
        });
    }

    @Test
    void missingDataPermissionContextFailsClosedBeforeJdbcExecution() {
        this.contextRunner.run(context -> {
            RecordingDataSource dataSource = context.getBean(RecordingDataSource.class);
            initializeDatabase(dataSource);
            ComposedDocumentMapper mapper = context.getBean(ComposedDocumentMapper.class);

            Throwable failure = catchThrowable(() -> CocoTenantContextHolder.callWithContext(tenantContext(),
                    () -> mapper.selectList(null)));

            assertThat(rootCause(failure))
                    .isInstanceOf(CocoForbiddenException.class)
                    .hasMessage("coco.feature.data-permission.error.context-missing");
            assertThat(dataSource.preparedSql()).isEmpty();
        });
    }

    @Test
    void missingResourceRuleExecutesDenyPredicateAndReturnsNoBusinessRows() {
        this.contextRunner.run(context -> {
            RecordingDataSource dataSource = context.getBean(RecordingDataSource.class);
            initializeDatabase(dataSource);
            ComposedDocumentMapper mapper = context.getBean(ComposedDocumentMapper.class);

            List<ComposedDocument> documents = CocoTenantContextHolder.callWithContext(tenantContext(),
                    () -> CocoDataPermissionContextHolder.callWithContext(CocoDataPermissionContext.empty(),
                            () -> mapper.selectList(null)));
            String executedSql = onlyPreparedSql(dataSource);

            assertThat(documents).isEmpty();
            assertThat(executedSql)
                    .containsIgnoringCase("1 = 0")
                    .containsIgnoringCase("tenant_id = 'tenant-a'");
        });
    }

    private static void initializeDatabase(DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
                CREATE TABLE documents (
                    id BIGINT PRIMARY KEY,
                    tenant_id VARCHAR(64) NOT NULL,
                    department_id BIGINT NOT NULL,
                    title VARCHAR(128) NOT NULL,
                    archived BOOLEAN NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE document_tags (
                    document_id BIGINT NOT NULL,
                    tenant_id VARCHAR(64) NOT NULL,
                    tag VARCHAR(64) NOT NULL
                )
                """);
        jdbcTemplate.batchUpdate("""
                INSERT INTO documents (id, tenant_id, department_id, title, archived)
                VALUES (?, ?, ?, ?, ?)
                """, List.of(
                new Object[] {1L, "tenant-a", 10L, "allowed", false},
                new Object[] {2L, "tenant-a", 20L, "other department", false},
                new Object[] {3L, "tenant-b", 10L, "other tenant", false},
                new Object[] {4L, "tenant-a", 10L, "archived", true},
                new Object[] {5L, "tenant-b", 10L, "other tenant archived", true},
                new Object[] {6L, "tenant-a", 20L, "other department archived", true}));
        jdbcTemplate.batchUpdate("""
                INSERT INTO document_tags (document_id, tenant_id, tag)
                VALUES (?, ?, ?)
                """, List.of(
                new Object[] {1L, "tenant-a", "blue"},
                new Object[] {2L, "tenant-a", "blue"},
                new Object[] {3L, "tenant-b", "blue"},
                new Object[] {4L, "tenant-a", "blue"}));
        jdbcTemplate.execute("CREATE SCHEMA \"tenant_a\"");
        jdbcTemplate.execute("""
                CREATE TABLE "tenant_a"."documents" AS
                SELECT * FROM documents
                """);
        jdbcTemplate.execute("""
                CREATE TABLE "tenant_a"."document_tags" AS
                SELECT * FROM document_tags
                """);
        ((RecordingDataSource) dataSource).clearPreparedSql();
    }

    private static <T> T withContexts(Supplier<T> operation) {
        return CocoTenantContextHolder.callWithContext(tenantContext(),
                () -> CocoDataPermissionContextHolder.callWithContext(permissionContext(), operation));
    }

    private static CocoTenantContext tenantContext() {
        return CocoTenantContext.of("tenant-a", "Tenant A");
    }

    private static CocoDataPermissionContext permissionContext() {
        return CocoDataPermissionContext.of(Set.of(
                new CocoDataPermissionRule("document", CocoDataScope.CUSTOM, Set.of("10"))));
    }

    private static String onlyPreparedSql(RecordingDataSource dataSource) {
        assertThat(dataSource.preparedSql()).hasSize(1);
        return dataSource.preparedSql().get(0);
    }

    private static Throwable rootCause(Throwable failure) {
        assertThat(failure).isNotNull();
        Throwable current = failure;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    @Configuration(proxyBeanMethods = false)
    @MapperScan(basePackageClasses = ComposedDocumentMapper.class)
    static class FixtureConfiguration {

        @Bean
        RecordingDataSource dataSource() {
            return new RecordingDataSource();
        }
    }
}
