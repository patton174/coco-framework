package io.github.coco.feature.datapermission.sql;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.Alias.AliasColumn;
import net.sf.jsqlparser.expression.MySQLIndexHint;
import net.sf.jsqlparser.expression.SQLServerHints;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.create.table.ColDataType;
import net.sf.jsqlparser.statement.select.Pivot;
import net.sf.jsqlparser.statement.select.SampleClause;
import net.sf.jsqlparser.statement.select.UnPivot;

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
        Table copy = copyNameParts(table);
        copy.setAlias(copyAlias(table.getAlias()));
        copy.setSampleClause(copySampleClause(table.getSampleClause()));
        copy.setPivot(copySerializable(table.getPivot(), Pivot.class));
        copy.setUnPivot(copySerializable(table.getUnPivot(), UnPivot.class));
        copy.setHint(copyIndexHint(table.getIndexHint()));
        copy.setSqlServerHints(copySqlServerHints(table.getSqlServerHints()));
        return copy;
    }

    private static Table copyNameParts(Table source) {
        List<String> nameParts = new ArrayList<>(source.getNameParts());
        Collections.reverse(nameParts);
        return new Table(nameParts);
    }

    private static Alias copyAlias(Alias source) {
        if (source == null) {
            return null;
        }
        Alias copy = new Alias(source.getName(), source.isUseAs());
        List<AliasColumn> columns = source.getAliasColumns();
        if (columns != null) {
            List<AliasColumn> copiedColumns = new ArrayList<>(columns.size());
            for (AliasColumn column : columns) {
                copiedColumns.add(column == null ? null
                        : new AliasColumn(column.name, copyColDataType(column.colDataType)));
            }
            copy.setAliasColumns(copiedColumns);
        }
        return copy;
    }

    private static ColDataType copyColDataType(ColDataType source) {
        if (source == null) {
            return null;
        }
        ColDataType copy = new ColDataType(source.getDataType());
        copy.setArgumentsStringList(copyList(source.getArgumentsStringList()));
        copy.setCharacterSet(source.getCharacterSet());
        copy.setArrayData(copyList(source.getArrayData()));
        return copy;
    }

    private static SampleClause copySampleClause(SampleClause source) {
        if (source == null) {
            return null;
        }
        return new SampleClause()
                .setKeyword(source.getKeyword())
                .setMethod(source.getMethod())
                .setPercentageArgument(copyNumber(source.getPercentageArgument()))
                .setRepeatArgument(copyNumber(source.getRepeatArgument()))
                .setSeedArgument(copyNumber(source.getSeedArgument()));
    }

    private static MySQLIndexHint copyIndexHint(MySQLIndexHint source) {
        if (source == null) {
            return null;
        }
        return new MySQLIndexHint(source.getAction(), source.getIndexQualifier(), copyList(source.getIndexNames()));
    }

    private static SQLServerHints copySqlServerHints(SQLServerHints source) {
        if (source == null) {
            return null;
        }
        SQLServerHints copy = new SQLServerHints();
        copy.setNoLock(source.getNoLock());
        copy.setIndexName(source.getIndexName());
        return copy;
    }

    private static Number copyNumber(Number source) {
        if (source == null) {
            return null;
        }
        return copySerializable(source, Number.class);
    }

    private static <T extends Serializable> T copySerializable(T source, Class<T> type) {
        if (source == null) {
            return null;
        }
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(source);
            output.flush();
            try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                return type.cast(input.readObject());
            }
        }
        catch (IOException | ClassNotFoundException ex) {
            throw new IllegalStateException("Unable to copy JSQLParser table state", ex);
        }
    }

    private static <T> List<T> copyList(List<T> source) {
        return source == null ? null : new ArrayList<>(source);
    }
}
