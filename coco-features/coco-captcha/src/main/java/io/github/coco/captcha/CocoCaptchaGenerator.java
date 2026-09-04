package io.github.coco.captcha;

/**
 * 验证码生成器 SPI。
 * <p>
 * 一个生成器声明它支持的 {@link CocoCaptchaType},负责生成挑战 + 答案,并定义"提交值是否匹配存储答案"
 * 的比对策略(图形/短信是精确比对,滑块是容差比对)。比对逻辑随生成器走,因为只有生成器知道自己答案的
 * 格式。框架自带图形/短信/滑块参考实现,业务可注册自定义生成器覆盖同类型。
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
public interface CocoCaptchaGenerator {

    /**
     * 本生成器支持的类型。
     * @return 验证码类型
     */
    CocoCaptchaType supportedType();

    /**
     * 生成一个验证码。
     * @param captchaId 调用方分配的唯一标识
     * @return 含挑战与答案的验证码
     */
    CocoCaptcha generate(String captchaId);

    /**
     * 按本类型的策略比对提交值与存储答案。
     * @param submitted 客户端提交的值
     * @param storedAnswer 生成时保留的答案
     * @return 匹配返回 {@code true}
     */
    boolean matches(String submitted, String storedAnswer);
}
