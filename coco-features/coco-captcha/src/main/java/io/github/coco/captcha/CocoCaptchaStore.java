package io.github.coco.captcha;

import java.time.Duration;

/**
 * 验证码答案存储 SPI。
 * <p>
 * 按 {@code captchaId} 存取答案并带 TTL 过期。{@link #consume} 是"取出即删除"的单次语义:一个验证码
 * 只能核验成功一次,防止答案被重放。抽成接口是为了让服务层不依赖具体存储即可单测;框架自带进程内参考
 * 实现,多实例部署应换成基于 Redis 等共享存储的实现。
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
public interface CocoCaptchaStore {

    /**
     * 存入答案并设置存活时间。
     * @param captchaId 验证码唯一标识
     * @param answer 待保留的答案
     * @param ttl 存活时间
     */
    void store(String captchaId, String answer, Duration ttl);

    /**
     * 取出并删除某验证码的答案(单次消费)。
     * @param captchaId 验证码唯一标识
     * @return 未过期的答案;不存在或已过期时为 {@code null}
     */
    String consume(String captchaId);
}
