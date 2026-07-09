package io.swagger.v3.oas.models;

import io.swagger.v3.oas.models.info.Info;

/**
 * Swagger OpenAPI 测试桩。
 */
public class OpenAPI {

    private Info info;

    /**
     * <p>
     * 返回文档信息。
     * </p>
     * @return 文档信息
     */
    public Info getInfo() {
        return this.info;
    }

    /**
     * <p>
     * 设置文档信息。
     * </p>
     * @param info 文档信息
     */
    public void setInfo(Info info) {
        this.info = info;
    }
}
