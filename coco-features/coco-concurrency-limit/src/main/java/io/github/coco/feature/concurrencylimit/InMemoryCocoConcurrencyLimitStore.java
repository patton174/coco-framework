package io.github.coco.feature.concurrencylimit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 容量受限的进程内 Coco 在途请求并发存储。
 * <p>
 * 每次多维申请和释放都在同一短时锁内完成，保证全局、路由和解析键计数全有或全无地更新。
 * 计数归零后立即删除活动键，活动键总数不会超过配置容量。
 * </p>
 */
public final class InMemoryCocoConcurrencyLimitStore implements CocoConcurrencyLimitStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryCocoConcurrencyLimitStore.class);

    private static final AtomicBoolean CLUSTER_WARNING_LOGGED = new AtomicBoolean();

    private final ReentrantLock lock = new ReentrantLock();

    private final Map<String, Integer> counts = new HashMap<>();

    private final AtomicBoolean closed = new AtomicBoolean();

    private final int maxEntries;

    /**
     * 创建容量受限的进程内并发存储。
     * @param properties 并发限制配置
     */
    public InMemoryCocoConcurrencyLimitStore(CocoConcurrencyLimitProperties properties) {
        CocoConcurrencyLimitProperties.InMemory inMemory = properties == null
                ? new CocoConcurrencyLimitProperties().getInMemory() : properties.getInMemory();
        if (inMemory.getMaxEntries() <= 0) {
            throw new IllegalArgumentException("coco.concurrency-limit.in-memory.max-entries must be positive");
        }
        this.maxEntries = inMemory.getMaxEntries();
        if (CLUSTER_WARNING_LOGGED.compareAndSet(false, true)) {
            LOGGER.warn("Coco concurrency-limit is using process-local storage; configure a shared "
                    + "CocoConcurrencyLimitStore for multi-instance production deployments");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CocoConcurrencyLimitAcquisition acquire(CocoConcurrencyLimitRequest request) {
        CocoConcurrencyLimitRequest checkedRequest = Objects.requireNonNull(request, "request must not be null");
        this.lock.lock();
        try {
            List<CocoConcurrencyLimitConstraint> constraints = checkedRequest.constraints();
            if (this.closed.get()) {
                CocoConcurrencyLimitDimension dimension = constraints.get(0).dimension();
                return CocoConcurrencyLimitAcquisition.rejected(snapshots(constraints), dimension,
                        CocoConcurrencyLimitRejectionReason.UNAVAILABLE);
            }

            for (CocoConcurrencyLimitConstraint constraint : constraints) {
                if (count(constraint) >= constraint.limit()) {
                    return CocoConcurrencyLimitAcquisition.rejected(snapshots(constraints),
                            constraint.dimension(), CocoConcurrencyLimitRejectionReason.LIMIT_REACHED);
                }
            }

            CocoConcurrencyLimitConstraint firstNewConstraint = null;
            int newEntries = 0;
            for (CocoConcurrencyLimitConstraint constraint : constraints) {
                if (!this.counts.containsKey(constraint.storeKey())) {
                    newEntries++;
                    if (firstNewConstraint == null) {
                        firstNewConstraint = constraint;
                    }
                }
            }
            if (this.counts.size() + newEntries > this.maxEntries) {
                CocoConcurrencyLimitDimension dimension = firstNewConstraint == null
                        ? constraints.get(0).dimension() : firstNewConstraint.dimension();
                return CocoConcurrencyLimitAcquisition.rejected(snapshots(constraints), dimension,
                        CocoConcurrencyLimitRejectionReason.CAPACITY_EXHAUSTED);
            }

            for (CocoConcurrencyLimitConstraint constraint : constraints) {
                this.counts.merge(constraint.storeKey(), 1, Integer::sum);
            }
            InMemoryPermit permit = new InMemoryPermit(this, constraints);
            return CocoConcurrencyLimitAcquisition.granted(permit, snapshots(constraints));
        }
        finally {
            this.lock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void release(CocoConcurrencyLimitPermit permit) {
        Objects.requireNonNull(permit, "permit must not be null");
        if (!(permit instanceof InMemoryPermit inMemoryPermit) || inMemoryPermit.owner != this) {
            throw new IllegalArgumentException("permit was not created by this concurrency-limit store");
        }
        if (!inMemoryPermit.released.compareAndSet(false, true)) {
            return;
        }
        this.lock.lock();
        try {
            for (CocoConcurrencyLimitConstraint constraint : inMemoryPermit.constraints) {
                String key = constraint.storeKey();
                Integer count = this.counts.get(key);
                if (count == null || count <= 1) {
                    this.counts.remove(key);
                }
                else {
                    this.counts.put(key, count - 1);
                }
            }
        }
        finally {
            this.lock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        if (!this.closed.compareAndSet(false, true)) {
            return;
        }
        this.lock.lock();
        try {
            this.counts.clear();
        }
        finally {
            this.lock.unlock();
        }
    }

    int activeEntries() {
        this.lock.lock();
        try {
            return this.counts.size();
        }
        finally {
            this.lock.unlock();
        }
    }

    @SuppressFBWarnings(value = "CWO_CLOSED_WITHOUT_OPENED",
            justification = "ReentrantLock is acquired directly before the guarded try-finally block")
    int currentCount(CocoConcurrencyLimitConstraint constraint) {
        Objects.requireNonNull(constraint, "constraint must not be null");
        this.lock.lock();
        try {
            return count(constraint);
        }
        finally {
            this.lock.unlock();
        }
    }

    static void resetClusterWarningForTests() {
        CLUSTER_WARNING_LOGGED.set(false);
    }

    private int count(CocoConcurrencyLimitConstraint constraint) {
        return this.counts.getOrDefault(constraint.storeKey(), 0);
    }

    private List<CocoConcurrencyLimitSnapshot> snapshots(List<CocoConcurrencyLimitConstraint> constraints) {
        List<CocoConcurrencyLimitSnapshot> snapshots = new ArrayList<>(constraints.size());
        for (CocoConcurrencyLimitConstraint constraint : constraints) {
            int current = count(constraint);
            int remaining = Math.max(0, constraint.limit() - current);
            snapshots.add(new CocoConcurrencyLimitSnapshot(constraint.dimension(), constraint.limit(), remaining));
        }
        return List.copyOf(snapshots);
    }

    private static final class InMemoryPermit implements CocoConcurrencyLimitPermit {

        private final InMemoryCocoConcurrencyLimitStore owner;

        private final List<CocoConcurrencyLimitConstraint> constraints;

        private final AtomicBoolean released = new AtomicBoolean();

        private InMemoryPermit(InMemoryCocoConcurrencyLimitStore owner,
                List<CocoConcurrencyLimitConstraint> constraints) {
            this.owner = owner;
            this.constraints = List.copyOf(constraints);
        }
    }
}
