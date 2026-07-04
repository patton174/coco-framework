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

    private List<String> basename = new ArrayList<>(List.of("messages", "coco-messages"));

    private Locale defaultLocale = Locale.SIMPLIFIED_CHINESE;

    private boolean fallbackToSystemLocale;

    private boolean useCodeAsDefaultMessage = true;

    public List<String> getBasename() {
        return this.basename;
    }

    public void setBasename(List<String> basename) {
        this.basename = basename == null || basename.isEmpty()
                ? new ArrayList<>(List.of("messages", "coco-messages"))
                : new ArrayList<>(basename);
    }

    public Locale getDefaultLocale() {
        return this.defaultLocale;
    }

    public void setDefaultLocale(Locale defaultLocale) {
        this.defaultLocale = defaultLocale == null ? Locale.SIMPLIFIED_CHINESE : defaultLocale;
    }

    public boolean isFallbackToSystemLocale() {
        return this.fallbackToSystemLocale;
    }

    public void setFallbackToSystemLocale(boolean fallbackToSystemLocale) {
        this.fallbackToSystemLocale = fallbackToSystemLocale;
    }

    public boolean isUseCodeAsDefaultMessage() {
        return this.useCodeAsDefaultMessage;
    }

    public void setUseCodeAsDefaultMessage(boolean useCodeAsDefaultMessage) {
        this.useCodeAsDefaultMessage = useCodeAsDefaultMessage;
    }
}
