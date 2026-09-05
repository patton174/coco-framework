package io.github.coco.captcha;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 进程内验证码答案存储参考实现。
 * <p>
 * 用 {@link ConcurrentHashMap} 保存答案与过期时刻。{@link #consume} 取出即删除,实现单次核验;过期条目
 * 在读取时惰性剔除。仅适合单实例或开发环境——状态不跨实例、不持久化。多实例应换成基于 Redis 等共享存储
 * 的实现。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-captcha}</li>
 * </ul>
 * @author patton174
 * @since 2.1.0
 */
public final class InMemoryCocoCaptchaStore implements CocoCaptchaStore {

    private final ConcurrentMap<String, Entry> entries = new ConcurrentHashMap<>();

    private final Clock clock;

    /**
     * 用系统 UTC 时钟创建。
     */
    public InMemoryCocoCaptchaStore() {
        this(Clock.systemUTC());
    }

    /**
     * 用指定时钟创建(便于测试)。
     * @param clock 时钟
     */
    public InMemoryCocoCaptchaStore(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public void store(String captchaId, String answer, Duration ttl) {
        Objects.requireNonNull(captchaId, "captchaId must not be null");
        Objects.requireNonNull(answer, "answer must not be null");
        Objects.requireNonNull(ttl, "ttl must not be null");
        this.entries.put(captchaId, new Entry(answer, this.clock.instant().plus(ttl)));
    }

    @Override
    public String consume(String captchaId) {
        if (captchaId == null) {
            return null;
        }
        Entry entry = this.entries.remove(captchaId);
        if (entry == null || entry.expiresAt().isBefore(this.clock.instant())) {
            return null;
        }
        return entry.answer();
    }

    /**
     * 当前保存的条目数(含尚未惰性剔除的过期项),供测试观测。
     * @return 条目数
     */
    public int size() {
        return this.entries.size();
    }

    private record Entry(String answer, Instant expiresAt) {
    }
}
