package io.github.coco.feature.datapermission.sql;

import java.util.Objects;

import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.schema.Database;
import net.sf.jsqlparser.schema.Table;

/**
 * JSQLParser 表对象快照工具。
 *
 * @author patton174
 * @since 1.0.0
 */
final class CocoDataPermissionSqlTableSnapshot {

    private CocoDataPermissionSqlTableSnapshot() {
    }

    static Table copyOf(Table source) {
        Table table = Objects.requireNonNull(source, "table must not be null");
        Database database = table.getDatabase();
        Database databaseCopy = database == null ? null : new Database(database.getDatabaseName());
        Table copy = new Table(databaseCopy, table.getSchemaName(), table.getName());
        Alias alias = table.getAlias();
        if (alias != null) {
            copy.setAlias(new Alias(alias.getName(), alias.isUseAs()));
        }
        return copy;
    }
}
