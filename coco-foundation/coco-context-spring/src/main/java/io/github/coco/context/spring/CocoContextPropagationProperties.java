package io.github.coco.context.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Coco Spring 上下文传播配置。 */
@ConfigurationProperties("coco.context.propagation")
public class CocoContextPropagationProperties {
    private boolean enabled = true;
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
