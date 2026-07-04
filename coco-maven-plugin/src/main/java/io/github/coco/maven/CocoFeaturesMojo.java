package io.github.coco.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * No-op feature assembly goal for the skeleton stage.
 */
@Mojo(name = "features", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class CocoFeaturesMojo extends AbstractMojo {

    @Override
    public void execute() throws MojoExecutionException {
        getLog().info("Coco feature assembly is not implemented in the skeleton stage.");
    }
}
