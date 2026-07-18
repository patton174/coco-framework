package io.github.coco.fixture;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/** Dumps the MavenProject dependency and classpath views used by later lifecycle mojos. */
@Mojo(name = "dump-project", requiresDependencyResolution = ResolutionScope.TEST, threadSafe = true)
public final class DumpProjectMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(required = true)
    private File outputFile;

    @Override
    public void execute() throws MojoExecutionException {
        try {
            Files.createDirectories(this.outputFile.toPath().getParent());
            Files.writeString(this.outputFile.toPath(), "dependencies=" + dependencies(this.project.getDependencies())
                    + System.lineSeparator() + "dependencyOrder="
                    + dependencyOrder(this.project.getDependencies())
                    + System.lineSeparator() + "artifacts=" + artifacts(this.project.getArtifacts())
                    + System.lineSeparator() + "dependencyArtifacts="
                    + artifacts(this.project.getDependencyArtifacts())
                    + System.lineSeparator() + "artifactOrder="
                    + artifactOrder(this.project.getArtifacts())
                    + System.lineSeparator() + "compileClasspath="
                    + classpathOrder(this.project.getCompileClasspathElements())
                    + System.lineSeparator() + "compileClasspathOrder="
                    + classpathOrder(this.project.getCompileClasspathElements())
                    + System.lineSeparator() + "runtimeClasspathOrder="
                    + classpathOrder(this.project.getRuntimeClasspathElements())
                    + System.lineSeparator() + "testClasspath="
                    + classpath(this.project.getTestClasspathElements())
                    + System.lineSeparator() + "testClasspathOrder="
                    + classpathOrder(this.project.getTestClasspathElements())
                    + System.lineSeparator(), StandardCharsets.UTF_8);
        }
        catch (IOException | DependencyResolutionRequiredException ex) {
            throw new MojoExecutionException("Failed to dump MavenProject views.", ex);
        }
    }

    private static String dependencies(List<Dependency> dependencies) {
        return dependencies.stream()
                .map(dependency -> dependency.getGroupId() + ":" + dependency.getArtifactId() + ":"
                        + dependency.getVersion() + ":" + dependency.getScope())
                .sorted()
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static String dependencyOrder(List<Dependency> dependencies) {
        return dependencies.stream()
                .map(dependency -> dependency.getGroupId() + ":" + dependency.getArtifactId() + ":"
                        + dependency.getVersion() + ":" + dependency.getScope())
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static String artifacts(Set<Artifact> artifacts) {
        if (artifacts == null) {
            return "null";
        }
        return artifacts.stream()
                .map(artifact -> artifact.getGroupId() + ":" + artifact.getArtifactId() + ":"
                        + artifact.getBaseVersion() + ":" + artifact.getScope())
                .sorted()
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static String classpath(Collection<String> entries) {
        return entries.stream().sorted().collect(Collectors.joining(",", "[", "]"));
    }

    private static String artifactOrder(Set<Artifact> artifacts) {
        if (artifacts == null) {
            return "null";
        }
        return artifacts.stream()
                .map(artifact -> artifact.getGroupId() + ":" + artifact.getArtifactId() + ":"
                        + artifact.getBaseVersion() + ":" + artifact.getScope())
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static String classpathOrder(Collection<String> entries) {
        return entries.stream().collect(Collectors.joining(",", "[", "]"));
    }
}
