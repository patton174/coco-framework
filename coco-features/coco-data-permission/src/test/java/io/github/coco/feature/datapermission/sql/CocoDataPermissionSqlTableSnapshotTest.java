package io.github.coco.feature.datapermission.sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.Alias.AliasColumn;
import net.sf.jsqlparser.expression.MySQLIndexHint;
import net.sf.jsqlparser.expression.SQLServerHints;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Database;
import net.sf.jsqlparser.schema.Server;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.create.table.ColDataType;
import net.sf.jsqlparser.statement.select.Pivot;
import net.sf.jsqlparser.statement.select.SampleClause;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.UnPivot;
import org.junit.jupiter.api.Test;

/**
 * JSQLParser 表快照测试。
 *
 * @author patton174
 * @since 1.0.0
 */
class CocoDataPermissionSqlTableSnapshotTest {

    @Test
    void preservesFourPartQuotedTableParsedByJSqlParser() throws Exception {
        Select select = (Select) CCJSqlParserUtil.parse(
                "SELECT * FROM \"SERVER_A\".\"DATABASE_A\".\"SCHEMA_A\".\"DOCUMENT_A\" AS d");
        Table parsed = (Table) select.getPlainSelect().getFromItem();
        CocoDataPermissionSqlResourceContext context = new CocoDataPermissionSqlResourceContext(parsed,
                "DocumentMapper.selectDocuments");

        parsed.setName("changed_document");

        assertThat(context.table().getFullyQualifiedName())
                .isEqualTo("\"SERVER_A\".\"DATABASE_A\".\"SCHEMA_A\".\"DOCUMENT_A\"");
        assertThat(context.table().getAlias().getName()).isEqualTo("d");
    }

    @Test
    void preservesFullTableStateAndDefensivelyCopiesGetterResults() {
        Table source = tableWithFullState();
        CocoDataPermissionSqlResourceContext context = new CocoDataPermissionSqlResourceContext(source,
                "DocumentMapper.selectDocuments");
        Table snapshot = context.table();

        assertThat(snapshot.getFullyQualifiedName()).isEqualTo("\"SERVER_A\".\"DATABASE_A\".\"SCHEMA_A\".\"DOCUMENT_A\"");
        assertThat(snapshot.getName()).isEqualTo("\"DOCUMENT_A\"");
        assertThat(snapshot.getAlias().getName()).isEqualTo("d");
        assertThat(snapshot.getAlias().isUseAs()).isTrue();
        assertThat(snapshot.getAlias().getAliasColumns()).singleElement().satisfies(column -> {
            assertThat(column.name).isEqualTo("document_id");
            assertThat(column.colDataType.getDataType()).isEqualTo("VARCHAR");
            assertThat(column.colDataType.getArgumentsStringList()).containsExactly("36");
        });
        assertThat(snapshot.getSampleClause().getKeyword()).isEqualTo(SampleClause.SampleKeyword.TABLESAMPLE);
        assertThat(snapshot.getSampleClause().getMethod()).isEqualTo(SampleClause.SampleMethod.BERNOULLI);
        assertThat(snapshot.getSampleClause().getPercentageArgument()).isEqualTo(25);
        assertThat(snapshot.getSampleClause().getRepeatArgument()).isEqualTo(7);
        assertThat(snapshot.getSampleClause().getSeedArgument()).isEqualTo(11);
        assertThat(snapshot.getPivot()).isNotSameAs(source.getPivot());
        assertThat(snapshot.getPivot().getAlias().getName()).isEqualTo("pivoted");
        assertThat(snapshot.getUnPivot()).isNotSameAs(source.getUnPivot());
        assertThat(snapshot.getUnPivot().getIncludeNulls()).isTrue();
        assertThat(snapshot.getUnPivot().getAlias().getName()).isEqualTo("unpivoted");
        assertThat(snapshot.getIndexHint()).isNotSameAs(source.getIndexHint());
        assertThat(snapshot.getIndexHint().getAction()).isEqualTo("USE");
        assertThat(snapshot.getIndexHint().getIndexNames()).containsExactly("idx_document");
        assertThat(snapshot.getSqlServerHints()).isNotSameAs(source.getSqlServerHints());
        assertThat(snapshot.getSqlServerHints().getNoLock()).isTrue();
        assertThat(snapshot.getSqlServerHints().getIndexName()).isEqualTo("idx_document");

        source.setName("changed_document");
        source.getAlias().setName("changed_alias");
        source.getAlias().getAliasColumns().get(0).colDataType.setDataType("INTEGER");
        source.getSampleClause().setPercentageArgument(99);
        source.getPivot().setAlias(new Alias("changed_pivot"));
        source.getUnPivot().setAlias(new Alias("changed_unpivot"));
        source.setHint(new MySQLIndexHint("IGNORE", "INDEX", List.of("changed_index")));
        source.getSqlServerHints().setIndexName("changed_index");

        snapshot.setName("returned_table_mutation");
        snapshot.getAlias().setName("returned_alias_mutation");

        Table laterSnapshot = context.table();
        assertThat(laterSnapshot.getFullyQualifiedName())
                .isEqualTo("\"SERVER_A\".\"DATABASE_A\".\"SCHEMA_A\".\"DOCUMENT_A\"");
        assertThat(laterSnapshot.getAlias().getName()).isEqualTo("d");
        assertThat(laterSnapshot.getAlias().getAliasColumns().get(0).colDataType.getDataType()).isEqualTo("VARCHAR");
        assertThat(laterSnapshot.getSampleClause().getPercentageArgument()).isEqualTo(25);
        assertThat(laterSnapshot.getPivot().getAlias().getName()).isEqualTo("pivoted");
        assertThat(laterSnapshot.getUnPivot().getAlias().getName()).isEqualTo("unpivoted");
        assertThat(laterSnapshot.getIndexHint().getAction()).isEqualTo("USE");
        assertThat(laterSnapshot.getSqlServerHints().getIndexName()).isEqualTo("idx_document");
    }

    private static Table tableWithFullState() {
        Table table = new Table(new Database(new Server("\"SERVER_A\""), "\"DATABASE_A\""),
                "\"SCHEMA_A\"", "\"DOCUMENT_A\"");
        Alias alias = new Alias("d", true);
        ColDataType aliasColumnType = new ColDataType("VARCHAR");
        aliasColumnType.setArgumentsStringList(new ArrayList<>(List.of("36")));
        alias.setAliasColumns(new ArrayList<>(List.of(new AliasColumn("document_id", aliasColumnType))));
        table.setAlias(alias);
        table.setSampleClause(new SampleClause()
                .setKeyword(SampleClause.SampleKeyword.TABLESAMPLE)
                .setMethod(SampleClause.SampleMethod.BERNOULLI)
                .setPercentageArgument(25)
                .setRepeatArgument(7)
                .setSeedArgument(11));
        Pivot pivot = new Pivot();
        pivot.setAlias(new Alias("pivoted", true));
        table.setPivot(pivot);
        UnPivot unPivot = new UnPivot();
        unPivot.setIncludeNulls(true);
        unPivot.setAlias(new Alias("unpivoted", true));
        table.setUnPivot(unPivot);
        table.setHint(new MySQLIndexHint("USE", "INDEX", new ArrayList<>(List.of("idx_document"))));
        SQLServerHints sqlServerHints = new SQLServerHints();
        sqlServerHints.setNoLock(true);
        sqlServerHints.setIndexName("idx_document");
        table.setSqlServerHints(sqlServerHints);
        return table;
    }
}
