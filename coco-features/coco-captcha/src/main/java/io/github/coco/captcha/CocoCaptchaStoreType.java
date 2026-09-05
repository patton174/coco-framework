package io.github.coco.captcha;

/**
 * 验证码答案存储类型。
 * <p>
 * 决定框架自动装配哪种 {@link CocoCaptchaStore}。单实例部署用 {@link #IN_MEMORY} 即可;多实例部署必须用
 * {@link #REDIS},否则验证码在 A 实例生成、B 实例校验时取不到答案,校验必然失败。
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
public enum CocoCaptchaStoreType {

    /** 进程内存储,仅适用于单实例。 */
    IN_MEMORY,

    /** 基于 Redis 的共享存储,适用于多实例。 */
    REDIS
}
