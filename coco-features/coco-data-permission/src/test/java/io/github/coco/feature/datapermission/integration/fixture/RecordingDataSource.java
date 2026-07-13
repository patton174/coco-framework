package io.github.coco.feature.datapermission.integration.fixture;

import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;

public final class RecordingDataSource implements DataSource {

    private final JdbcDataSource delegate;

    private final List<String> preparedSql = new CopyOnWriteArrayList<>();

    public RecordingDataSource() {
        this.delegate = new JdbcDataSource();
        this.delegate.setURL("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        this.delegate.setUser("sa");
        this.delegate.setPassword("");
    }

    public List<String> preparedSql() {
        return List.copyOf(this.preparedSql);
    }

    public void clearPreparedSql() {
        this.preparedSql.clear();
    }

    @Override
    public Connection getConnection() throws SQLException {
        return recordingConnection(this.delegate.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return recordingConnection(this.delegate.getConnection(username, password));
    }

    private Connection recordingConnection(Connection connection) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class}, (proxy, method, arguments) -> {
                    if ((method.getName().equals("prepareStatement") || method.getName().equals("prepareCall"))
                            && arguments != null && arguments.length > 0 && arguments[0] instanceof String sql) {
                        this.preparedSql.add(sql);
                    }
                    try {
                        return method.invoke(connection, arguments);
                    }
                    catch (InvocationTargetException ex) {
                        throw ex.getCause();
                    }
                });
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
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return this.delegate.getParentLogger();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        return this.delegate.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this) || this.delegate.isWrapperFor(iface);
    }
}
