package io.github.coco.feature.httpclient;

import java.util.Map;
import java.util.Objects;

import org.springframework.web.client.RestClient;

/**
 * 默认 Coco 命名 HTTP 客户端注册表。
 */
public final class DefaultCocoHttpClients implements CocoHttpClients {

    private final Map<String, RestClient> clients;

    /**
     * 创建只读命名客户端注册表。
     * @param clients 命名客户端
     */
    public DefaultCocoHttpClients(Map<String, RestClient> clients) {
        this.clients = Map.copyOf(Objects.requireNonNull(clients, "clients must not be null"));
    }

    @Override
    public RestClient get(String name) {
        RestClient client = this.clients.get(name);
        if (client == null) {
            throw new IllegalArgumentException("No Coco HTTP client configured with name '" + name + "'");
        }
        return client;
    }
}
