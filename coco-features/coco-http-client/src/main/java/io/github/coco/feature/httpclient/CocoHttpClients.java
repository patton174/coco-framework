package io.github.coco.feature.httpclient;

import org.springframework.web.client.RestClient;

/**
 * Coco 命名 HTTP 客户端入口。
 */
public interface CocoHttpClients {

    /**
     * 返回已配置的命名 {@link RestClient}。
     * @param name 客户端名称
     * @return 命名客户端
     * @throws IllegalArgumentException 名称未配置时抛出
     */
    RestClient get(String name);
}
