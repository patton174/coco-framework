package io.github.coco.common.i18n;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Coco 国际化配置属性。
 * <p>
 * 绑定 {@code coco.common.i18n} 命名空间，控制消息资源包、默认语言和缺省消息策略。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-common-i18n}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public class CocoI18nProperties {

    private List<String> basename = new ArrayList<>(List.of("coco-messages"));

    private Locale defaultLocale = Locale.SIMPLIFIED_CHINESE;

    private boolean fallbackToSystemLocale;

    private boolean useCodeAsDefaultMessage = true;

    /**
     * <p>
     * 返回消息资源 basename 列表。
     * </p>
     * @return 消息资源 basename 列表
     */
    public List<String> getBasename() {
        return this.basename;
    }

    /**
     * <p>
     * 设置消息资源 basename 列表。
     * </p>
     * @param basename 消息资源 basename 列表
     */
    public void setBasename(List<String> basename) {
        this.basename = basename == null || basename.isEmpty()
                ? new ArrayList<>(List.of("coco-messages"))
                : new ArrayList<>(basename);
    }

    /**
     * <p>
     * 返回默认语言。
     * </p>
     * @return 默认语言
     */
    public Locale getDefaultLocale() {
        return this.defaultLocale;
    }

    /**
     * <p>
     * 设置默认语言。
     * </p>
     * @param defaultLocale 默认语言
     */
    public void setDefaultLocale(Locale defaultLocale) {
        this.defaultLocale = defaultLocale == null ? Locale.SIMPLIFIED_CHINESE : defaultLocale;
    }

    /**
     * <p>
     * 返回是否回退到系统语言。
     * </p>
     * @return 启用系统语言回退时返回 {@code true}
     */
    public boolean isFallbackToSystemLocale() {
        return this.fallbackToSystemLocale;
    }

    /**
     * <p>
     * 设置是否回退到系统语言。
     * </p>
     * @param fallbackToSystemLocale 是否回退到系统语言
     */
    public void setFallbackToSystemLocale(boolean fallbackToSystemLocale) {
        this.fallbackToSystemLocale = fallbackToSystemLocale;
    }

    /**
     * <p>
     * 返回消息资源缺失时是否使用编码作为默认消息。
     * </p>
     * @return 启用编码兜底时返回 {@code true}
     */
    public boolean isUseCodeAsDefaultMessage() {
        return this.useCodeAsDefaultMessage;
    }

    /**
     * <p>
     * 设置消息资源缺失时是否使用编码作为默认消息。
     * </p>
     * @param useCodeAsDefaultMessage 是否使用编码作为默认消息
     */
    public void setUseCodeAsDefaultMessage(boolean useCodeAsDefaultMessage) {
        this.useCodeAsDefaultMessage = useCodeAsDefaultMessage;
    }
}
