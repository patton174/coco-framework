package io.github.coco.context.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Coco Spring 上下文传播配置.
 *
 * @author patton174
 * @since 1.0.0
 */
@ConfigurationProperties("coco.context.propagation")
public class CocoContextPropagationProperties {

  private boolean enabled = true;

  /**
   * 返回是否启用 Spring 异步上下文传播.
   *
   * @return 是否启用异步上下文传播
   */
  public boolean isEnabled() {
    return this.enabled;
  }

  /**
   * 设置是否启用 Spring 异步上下文传播.
   *
   * @param enabled 是否启用异步上下文传播
   */
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
}
