package io.github.coco.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Coco 功能装配 Maven Goal。
 * <p>
 * 骨架阶段只提供 no-op goal，后续用于根据配置装配启用的功能模块。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-maven-plugin}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@Mojo(name = "features", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class CocoFeaturesMojo extends AbstractMojo {

    @Override
    public void execute() throws MojoExecutionException {
        getLog().info("Coco feature assembly is not implemented in the skeleton stage.");
    }
}
