package io.github.coco.feature.idempotency.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.coco.feature.idempotency.store.CocoIdempotencyAcquireStatus;
import io.github.coco.feature.idempotency.store.CocoIdempotencyRequest;
import io.github.coco.feature.idempotency.store.CocoIdempotencyStoredResponse;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class JdbcCocoIdempotencyDialectDdlTest {

    private static final Instant NOW = Instant.parse("2026-08-14T10:00:00Z");

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "h2, '', META-INF/coco/idempotency-jdbc-h2.sql",
            "postgresql, ';MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE', META-INF/coco/idempotency-jdbc-postgresql.sql",
            "mysql, ';MODE=MySQL;DATABASE_TO_LOWER=TRUE', META-INF/coco/idempotency-jdbc-mysql.sql"
    })
    void dialectDdlSupportsAcquireCompleteReplay(String dialect, String mode, String resourcePath) throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:ddl_" + dialect + "_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1" + mode, "sa", "");
        try (var connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(resourcePath));
        }
        JdbcCocoIdempotencyStore store = new JdbcCocoIdempotencyStore(dataSource,
                new CocoIdempotencyJdbcProperties());
        CocoIdempotencyRequest request = request(dialect);
        var acquired = store.acquire(request, NOW, NOW.plusSeconds(60));
        assertThat(acquired.status()).isEqualTo(CocoIdempotencyAcquireStatus.ACQUIRED);
        assertThat(store.complete(acquired.lease().orElseThrow(),
                new CocoIdempotencyStoredResponse(201, Map.of("X-Dialect", List.of(dialect)),
                        new byte[] { 0, 1, -1 }), NOW.plusSeconds(1))).isTrue();
        var replay = store.acquire(request, NOW.plusSeconds(2), NOW.plusSeconds(60));
        assertThat(replay.status()).isEqualTo(CocoIdempotencyAcquireStatus.REPLAY);
        assertThat(replay.response().orElseThrow().headers()).containsEntry("X-Dialect", List.of(dialect));
        assertThat(replay.response().orElseThrow().body()).containsExactly((byte) 0, (byte) 1, (byte) -1);
    }

    private static CocoIdempotencyRequest request(String dialect) {
        return new CocoIdempotencyRequest(hash("key-" + dialect), hash("payload-" + dialect));
    }

    private static String hash(String value) {
        return String.format("%064x", new java.math.BigInteger(1, value.getBytes(StandardCharsets.UTF_8)));
    }
}
