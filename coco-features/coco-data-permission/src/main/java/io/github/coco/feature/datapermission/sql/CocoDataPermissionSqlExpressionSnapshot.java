package io.github.coco.feature.datapermission.sql;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import net.sf.jsqlparser.expression.Expression;

/**
 * JSQLParser 表达式对象快照工具。
 * <p>
 * JSQLParser 4.9 未提供通用 {@link Expression} 克隆 API。此处仅在同一方法内对传入的
 * JSQLParser AST 写入并读回内存字节流，形成不与输入或 getter 返回值共享节点的快照；
 * 不解析或重建 SQL 文本，因此不会改变自定义 SPI 可见的表达式语义。
 * </p>
 *
 * @author patton174
 * @since 1.0.0
 */
final class CocoDataPermissionSqlExpressionSnapshot {

    private CocoDataPermissionSqlExpressionSnapshot() {
    }

    static Expression copyOf(Expression source) {
        if (source == null) {
            return null;
        }
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(source);
            output.flush();
            try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                return (Expression) input.readObject();
            }
        }
        catch (IOException | ClassNotFoundException ex) {
            throw new IllegalStateException("Unable to copy JSQLParser expression state", ex);
        }
    }
}
