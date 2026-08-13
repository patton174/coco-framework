package io.github.coco.feature.concurrencylimit.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.springframework.core.io.ClassPathResource;

class RedisConcurrencyLimitLuaContractTest {

    @Test
    void acquireExecutesAtomicallyAgainstRedisStateAndExpiresLeases() throws IOException {
        LuaRedis redis = new LuaRedis(1_000L);
        List<String> keys = List.of("coco:{app}:state", "coco:{app}:state:d:route", "coco:{app}:state:d:global");

        assertThat(redis.run("acquire.lua", keys, List.of("first", "30000", "2", "1", "2", "route", "global")))
                .isEqualTo("G:1,0:2,1");
        assertThat(redis.zcard(keys.get(1))).isOne();
        assertThat(redis.zcard(keys.get(2))).isOne();
        assertThat(redis.run("acquire.lua", keys, List.of("second", "30000", "2", "1", "2", "route", "global")))
                .isEqualTo("R:1:1,0:2,1");
        assertThat(redis.zcard(keys.get(1))).isOne();
        assertThat(redis.zcard(keys.get(2))).isOne();

        redis.advance(30_001L);
        assertThat(redis.run("acquire.lua", keys, List.of("recovered", "30000", "2", "1", "2", "route", "global")))
                .isEqualTo("G:1,0:2,1");
        assertThat(redis.timeCalls).isEqualTo(3);
    }

    @Test
    void renewAndReleaseExecuteWithTokenComparison() throws IOException {
        LuaRedis redis = new LuaRedis(1_000L);
        List<String> keys = List.of("coco:{app}:state", "coco:{app}:state:d:route");
        redis.run("acquire.lua", keys, List.of("owner", "30000", "1", "1", "route"));

        assertThat(redis.run("renew.lua", keys, List.of("foreign", "30000"))).isEqualTo("0");
        redis.advance(20_000L);
        assertThat(redis.run("renew.lua", keys, List.of("owner", "30000"))).isEqualTo("1");
        redis.advance(20_000L);
        assertThat(redis.run("acquire.lua", keys, List.of("blocked", "30000", "1", "1", "route")))
                .isEqualTo("R:1:1,0");
        assertThat(redis.run("release.lua", keys, List.of("foreign"))).isEqualTo("0");
        assertThat(redis.zcard(keys.get(1))).isOne();
        assertThat(redis.run("release.lua", keys, List.of("owner"))).isEqualTo("1");
        assertThat(redis.run("release.lua", keys, List.of("owner"))).isEqualTo("0");
        assertThat(redis.zcard(keys.get(1))).isZero();
    }

    private static final class LuaRedis {
        private final Map<String, Map<String, Long>> sortedSets = new HashMap<>();
        private final Map<String, Map<String, String>> hashes = new HashMap<>();
        private long nowMillis;
        private int timeCalls;

        private LuaRedis(long nowMillis) {
            this.nowMillis = nowMillis;
        }

        private String run(String script, List<String> keys, List<String> arguments) throws IOException {
            Globals globals = JsePlatform.standardGlobals();
            LuaTable redis = new LuaTable();
            redis.set("call", new RedisCall(this));
            globals.set("redis", redis);
            globals.set("KEYS", table(keys));
            globals.set("ARGV", table(arguments));
            return globals.load(source(script), script).call().tojstring();
        }

        private void advance(long millis) {
            this.nowMillis += millis;
        }

        private int zcard(String key) {
            return this.sortedSets.getOrDefault(key, Map.of()).size();
        }

        private LuaValue call(Varargs values) {
            String command = values.arg1().tojstring();
            return switch (command) {
                case "TIME" -> time();
                case "ZRANGEBYSCORE" -> rangeByScore(values.arg(2).tojstring(), values.arg(3).tojstring(),
                        values.arg(4).tojstring());
                case "ZCARD" -> LuaValue.valueOf(zcard(values.arg(2).tojstring()));
                case "ZADD" -> zadd(values.arg(2).tojstring(), values.arg(3).tolong(), values.arg(4).tojstring());
                case "ZREM" -> zrem(values.arg(2).tojstring(), values.arg(3).tojstring());
                case "ZSCORE" -> zscore(values.arg(2).tojstring(), values.arg(3).tojstring());
                case "HGET" -> hget(values.arg(2).tojstring(), values.arg(3).tojstring());
                case "HSET" -> hset(values.arg(2).tojstring(), values.arg(3).tojstring(), values.arg(4).tojstring());
                case "HDEL" -> hdel(values.arg(2).tojstring(), values.arg(3).tojstring());
                case "PEXPIRE" -> LuaValue.ONE;
                default -> throw new IllegalArgumentException("unsupported Redis command: " + command);
            };
        }

        private LuaValue time() {
            this.timeCalls++;
            LuaTable time = new LuaTable();
            time.set(1, LuaValue.valueOf(this.nowMillis / 1_000L));
            time.set(2, LuaValue.valueOf((this.nowMillis % 1_000L) * 1_000L));
            return time;
        }

        private LuaValue rangeByScore(String key, String minimum, String maximum) {
            long max = Long.parseLong(maximum);
            List<String> members = this.sortedSets.getOrDefault(key, Map.of()).entrySet().stream()
                    .filter(entry -> "-inf".equals(minimum) || entry.getValue() >= Long.parseLong(minimum))
                    .filter(entry -> entry.getValue() <= max).map(Map.Entry::getKey).sorted().toList();
            return table(members);
        }

        private LuaValue zadd(String key, long score, String member) {
            this.sortedSets.computeIfAbsent(key, ignored -> new LinkedHashMap<>()).put(member, score);
            return LuaValue.ONE;
        }

        private LuaValue zrem(String key, String member) {
            Map<String, Long> values = this.sortedSets.get(key);
            return LuaValue.valueOf(values != null && values.remove(member) != null ? 1 : 0);
        }

        private LuaValue zscore(String key, String member) {
            Long score = this.sortedSets.getOrDefault(key, Map.of()).get(member);
            return score == null ? LuaValue.FALSE : LuaValue.valueOf(score);
        }

        private LuaValue hget(String key, String field) {
            String value = this.hashes.getOrDefault(key, Map.of()).get(field);
            return value == null ? LuaValue.NIL : LuaValue.valueOf(value);
        }

        private LuaValue hset(String key, String field, String value) {
            this.hashes.computeIfAbsent(key, ignored -> new HashMap<>()).put(field, value);
            return LuaValue.ONE;
        }

        private LuaValue hdel(String key, String field) {
            Map<String, String> values = this.hashes.get(key);
            return LuaValue.valueOf(values != null && values.remove(field) != null ? 1 : 0);
        }

        private static LuaTable table(List<String> values) {
            LuaTable table = new LuaTable();
            for (int index = 0; index < values.size(); index++) {
                table.set(index + 1, LuaValue.valueOf(values.get(index)));
            }
            return table;
        }
    }

    private static final class RedisCall extends VarArgFunction {
        private final LuaRedis redis;

        private RedisCall(LuaRedis redis) {
            this.redis = redis;
        }

        @Override
        public Varargs invoke(Varargs arguments) {
            return this.redis.call(arguments);
        }
    }

    private static String source(String name) throws IOException {
        return new String(new ClassPathResource("io/github/coco/feature/concurrencylimit/redis/" + name)
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
}
