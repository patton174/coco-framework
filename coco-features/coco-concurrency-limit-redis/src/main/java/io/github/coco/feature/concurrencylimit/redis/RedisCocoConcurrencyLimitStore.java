package io.github.coco.feature.concurrencylimit.redis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.coco.feature.concurrencylimit.CocoConcurrencyLimitAcquisition;
import io.github.coco.feature.concurrencylimit.CocoConcurrencyLimitConstraint;
import io.github.coco.feature.concurrencylimit.CocoConcurrencyLimitPermit;
import io.github.coco.feature.concurrencylimit.CocoConcurrencyLimitRejectionReason;
import io.github.coco.feature.concurrencylimit.CocoConcurrencyLimitRequest;
import io.github.coco.feature.concurrencylimit.CocoConcurrencyLimitSnapshot;
import io.github.coco.feature.concurrencylimit.CocoConcurrencyLimitStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/** 基于 Redis Lua 的跨实例原子 Coco 并发许可存储。 */
public final class RedisCocoConcurrencyLimitStore implements CocoConcurrencyLimitStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisCocoConcurrencyLimitStore.class);

    private final RedisConcurrencyLimitExecutor executor;
    private final String stateKey;
    private final long leaseMillis;
    private final ConcurrentHashMap<String, Permit> permits = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ScheduledExecutorService renewer;

    /** 创建 Redis 存储并启动单一守护续租调度器。 */
    public RedisCocoConcurrencyLimitStore(RedisConnectionFactory connectionFactory,
            CocoConcurrencyLimitRedisProperties properties, String springApplicationName) {
        this(new RedisConnectionConcurrencyLimitExecutor(connectionFactory), properties, springApplicationName, true);
    }

    RedisCocoConcurrencyLimitStore(RedisConcurrencyLimitExecutor executor,
            CocoConcurrencyLimitRedisProperties properties, String springApplicationName, boolean scheduleRenewal) {
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        CocoConcurrencyLimitRedisProperties checked = Objects.requireNonNull(properties, "properties must not be null");
        checked.validate(springApplicationName);
        String namespace = checked.getAppNamespace() == null || checked.getAppNamespace().isBlank()
                ? springApplicationName : checked.getAppNamespace();
        this.stateKey = checked.getKeyPrefix() + "{" + digest(namespace) + "}:state";
        this.leaseMillis = checked.getLeaseDuration().toMillis();
        this.renewer = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "coco-concurrency-limit-redis-renew");
            thread.setDaemon(true);
            return thread;
        });
        if (scheduleRenewal) {
            long interval = checked.getRenewInterval().toMillis();
            this.renewer.scheduleWithFixedDelay(this::renewAll, interval, interval, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public CocoConcurrencyLimitAcquisition acquire(CocoConcurrencyLimitRequest request) {
        ensureOpen();
        List<CocoConcurrencyLimitConstraint> constraints = ordered(Objects.requireNonNull(request,
                "request must not be null").constraints());
        String token = UUID.randomUUID().toString();
        List<String> keys = keys(constraints);
        List<String> arguments = new ArrayList<>();
        arguments.add(token);
        arguments.add(Long.toString(this.leaseMillis));
        arguments.add(Integer.toString(constraints.size()));
        constraints.forEach(constraint -> arguments.add(Integer.toString(constraint.limit())));
        constraints.forEach(constraint -> arguments.add(digest(constraint.dimension().name() + '\0' + constraint.key())));
        ParsedReply reply = parse(this.executor.execute(RedisConcurrencyLimitOperation.ACQUIRE, keys, arguments), constraints);
        if (!reply.granted()) {
            return CocoConcurrencyLimitAcquisition.rejected(reply.snapshots(),
                    constraints.get(reply.rejectedIndex()).dimension(), CocoConcurrencyLimitRejectionReason.LIMIT_REACHED);
        }
        Permit permit = new Permit(this, token, keys);
        this.permits.put(token, permit);
        return CocoConcurrencyLimitAcquisition.granted(permit, reply.snapshots());
    }

    @Override
    public void release(CocoConcurrencyLimitPermit permit) {
        Permit owned = ownedPermit(permit);
        if (owned.renewalFailed.get()) {
            throw new IllegalStateException("Redis concurrency-limit permit renewal is no longer safe");
        }
        releaseOwned(owned);
    }

    @Override
    public void close() {
        if (!this.closed.compareAndSet(false, true)) {
            return;
        }
        this.renewer.shutdownNow();
        for (Permit permit : List.copyOf(this.permits.values())) {
            try {
                releaseOwned(permit);
            }
            catch (RuntimeException exception) {
                LOGGER.warn("Unable to release an active Redis concurrency-limit permit during close");
            }
        }
    }

    void renewNowForTests() {
        renewAll();
    }

    private void renewAll() {
        if (this.closed.get()) {
            return;
        }
        for (Permit permit : this.permits.values()) {
            if (permit.released.get()) {
                continue;
            }
            try {
                String result = this.executor.execute(RedisConcurrencyLimitOperation.RENEW, permit.keys,
                        List.of(permit.token, Long.toString(this.leaseMillis)));
                if (!"1".equals(result)) {
                    permit.renewalFailed.set(true);
                }
            }
            catch (RuntimeException exception) {
                permit.renewalFailed.set(true);
                LOGGER.warn("Redis concurrency-limit permit renewal failed; subsequent release will fail closed");
            }
        }
    }

    private void releaseOwned(Permit permit) {
        if (!permit.released.compareAndSet(false, true)) {
            return;
        }
        this.permits.remove(permit.token);
        this.executor.execute(RedisConcurrencyLimitOperation.RELEASE, permit.keys, List.of(permit.token));
    }

    private Permit ownedPermit(CocoConcurrencyLimitPermit permit) {
        if (!(permit instanceof Permit owned) || owned.owner != this) {
            throw new IllegalArgumentException("permit was not created by this concurrency-limit Redis store");
        }
        return owned;
    }

    private List<String> keys(List<CocoConcurrencyLimitConstraint> constraints) {
        List<String> keys = new ArrayList<>();
        keys.add(this.stateKey);
        constraints.forEach(constraint -> keys.add(this.stateKey + ":d:"
                + digest(constraint.dimension().name() + '\0' + constraint.key())));
        return List.copyOf(keys);
    }

    private static List<CocoConcurrencyLimitConstraint> ordered(List<CocoConcurrencyLimitConstraint> constraints) {
        return constraints.stream().sorted(Comparator.comparingInt(value -> value.dimension().ordinal())).toList();
    }

    private static ParsedReply parse(String reply, List<CocoConcurrencyLimitConstraint> constraints) {
        if (reply == null || reply.length() < 2 || reply.charAt(1) != ':') {
            throw invalidReply();
        }
        String[] parts = reply.split(":", -1);
        boolean granted = "G".equals(parts[0]);
        int offset = granted ? 1 : 2;
        if ((!granted && !"R".equals(parts[0])) || parts.length != constraints.size() + offset) {
            throw invalidReply();
        }
        int rejected = -1;
        if (!granted) {
            rejected = decimal(parts[1]) - 1;
            if (rejected < 0 || rejected >= constraints.size()) {
                throw invalidReply();
            }
        }
        List<CocoConcurrencyLimitSnapshot> snapshots = new ArrayList<>();
        for (int index = 0; index < constraints.size(); index++) {
            String[] snapshot = parts[index + offset].split(",", -1);
            if (snapshot.length != 2 || decimal(snapshot[0]) != constraints.get(index).limit()) {
                throw invalidReply();
            }
            snapshots.add(new CocoConcurrencyLimitSnapshot(constraints.get(index).dimension(), decimal(snapshot[0]),
                    decimal(snapshot[1])));
        }
        return new ParsedReply(granted, rejected, List.copyOf(snapshots));
    }

    private static int decimal(String value) {
        if (value.isEmpty() || value.length() > 1 && value.charAt(0) == '0') {
            throw invalidReply();
        }
        try {
            return Integer.parseInt(value);
        }
        catch (NumberFormatException exception) {
            throw invalidReply();
        }
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static IllegalStateException invalidReply() {
        return new IllegalStateException("Redis concurrency-limit script returned an invalid response");
    }

    private void ensureOpen() {
        if (this.closed.get()) {
            throw new IllegalStateException("Redis concurrency-limit store is closed");
        }
    }

    private record ParsedReply(boolean granted, int rejectedIndex, List<CocoConcurrencyLimitSnapshot> snapshots) {
    }

    private static final class Permit implements CocoConcurrencyLimitPermit {

        private final RedisCocoConcurrencyLimitStore owner;

        private final String token;

        private final List<String> keys;

        private final AtomicBoolean released = new AtomicBoolean();

        private final AtomicBoolean renewalFailed = new AtomicBoolean();

        private Permit(RedisCocoConcurrencyLimitStore owner, String token, List<String> keys) {
            this.owner = owner;
            this.token = token;
            this.keys = keys;
        }
    }
}
