package io.swagger.v3.oas.models.info;

/**
 * Swagger Info 测试桩。
 */
public class Info {

    private String title;

    private String version;

    private String description;

    /**
     * <p>
     * 返回标题。
     * </p>
     * @return 标题
     */
    public String getTitle() {
        return this.title;
    }

    /**
     * <p>
     * 设置标题。
     * </p>
     * @param title 标题
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * <p>
     * 返回版本。
     * </p>
     * @return 版本
     */
    public String getVersion() {
        return this.version;
    }

    /**
     * <p>
     * 设置版本。
     * </p>
     * @param version 版本
     */
    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * <p>
     * 返回描述。
     * </p>
     * @return 描述
     */
    public String getDescription() {
        return this.description;
    }

    /**
     * <p>
     * 设置描述。
     * </p>
     * @param description 描述
     */
    public void setDescription(String description) {
        this.description = description;
    }
}
