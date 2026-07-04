package io.github.coco.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * # Coco 功能装配 Maven Goal
 *
 * - **作者**: [patton174](https://github.com/patton174)
 * - **仓库**: [coco-framework](https://github.com/patton174/coco-framework)
 * - **模块**: `coco-maven-plugin`
 *
 * 骨架阶段只提供 no-op goal，后续用于根据配置装配启用的功能模块。
 *
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
