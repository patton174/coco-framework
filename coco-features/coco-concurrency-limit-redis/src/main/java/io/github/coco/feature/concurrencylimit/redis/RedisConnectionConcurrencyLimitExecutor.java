package io.github.coco.feature.concurrencylimit.redis;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.ReturnType;

/** 使用 Spring Data Redis 连接执行生产 Lua 脚本。 */
final class RedisConnectionConcurrencyLimitExecutor implements RedisConcurrencyLimitExecutor {

    private final RedisConnectionFactory connectionFactory;

    RedisConnectionConcurrencyLimitExecutor(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "connectionFactory must not be null");
    }

    @Override
    public String execute(RedisConcurrencyLimitOperation operation, List<String> keys, List<String> arguments) {
        RedisConnection connection = Objects.requireNonNull(this.connectionFactory.getConnection(),
                "Redis connection factory returned no connection");
        try {
            byte[][] values = new byte[keys.size() + arguments.size()][];
            for (int index = 0; index < keys.size(); index++) {
                values[index] = keys.get(index).getBytes(StandardCharsets.UTF_8);
            }
            for (int index = 0; index < arguments.size(); index++) {
                values[keys.size() + index] = arguments.get(index).getBytes(StandardCharsets.UTF_8);
            }
            Object result = connection.scriptingCommands().eval(script(operation), ReturnType.VALUE, keys.size(), values);
            if (!(result instanceof byte[] bytes)) {
                throw new IllegalStateException("Redis concurrency-limit script returned an invalid response");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
        finally {
            connection.close();
        }
    }

    private static byte[] script(RedisConcurrencyLimitOperation operation) {
        String resource = "io/github/coco/feature/concurrencylimit/redis/"
                + operation.name().toLowerCase(java.util.Locale.ROOT) + ".lua";
        try (var input = new ClassPathResource(resource).getInputStream()) {
            return input.readAllBytes();
        }
        catch (IOException exception) {
            throw new IllegalStateException("Cannot load Redis concurrency-limit script", exception);
        }
    }
}
