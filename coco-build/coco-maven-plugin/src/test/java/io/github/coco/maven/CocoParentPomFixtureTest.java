package io.github.coco.maven;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Repository;
import org.apache.maven.model.building.DefaultModelBuilderFactory;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.FileModelSource;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelSource;
import org.apache.maven.model.resolution.InvalidRepositoryException;
import org.apache.maven.model.resolution.ModelResolver;
import org.apache.maven.model.resolution.UnresolvableModelException;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.Test;

/**
 * 真实 Coco 父 POM 功能插件配置 fixture 测试。
 * <p>
 * 通过 Maven 模型构建器加载仓库内的 {@code coco-parent}，验证业务版本与 Coco 版本不同时仍向功能插件
 * 注入 Coco 版本。
 * </p>
 * @author patton174
 * @since 2.0.0
 */
class CocoParentPomFixtureTest {

    @Test
    void realParentInjectsCocoVersionWhenBusinessVersionDiffers() throws Exception {
        Path repositoryRoot = Path.of("../..").toAbsolutePath().normalize();
        Path fixturePom = Path.of("src/test/resources/fixtures/real-coco-parent/pom.xml")
                .toAbsolutePath().normalize();
        Properties properties = new Properties();
        properties.putAll(System.getProperties());
        properties.setProperty("revision", "1.0.0-SNAPSHOT");
        DefaultModelBuildingRequest request = new DefaultModelBuildingRequest()
                .setPomFile(fixturePom.toFile())
                .setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL)
                .setProcessPlugins(true)
                .setSystemProperties(properties)
                .setUserProperties(properties)
                .setModelResolver(new LocalModelResolver(repositoryRoot));

        var effectiveModel = new DefaultModelBuilderFactory().newInstance().build(request).getEffectiveModel();
        var plugin = effectiveModel.getBuild().getPlugins().stream()
                .filter(candidate -> "io.github.patton174".equals(candidate.getGroupId()))
                .filter(candidate -> "coco-maven-plugin".equals(candidate.getArtifactId()))
                .findFirst()
                .orElseThrow();
        Xpp3Dom configuration = (Xpp3Dom) plugin.getConfiguration();

        assertThat(effectiveModel.getVersion()).isEqualTo("99.7.3");
        assertThat(effectiveModel.getProperties().getProperty("coco.version")).isEqualTo("1.0.0-SNAPSHOT");
        assertThat(configuration.getChild("featureVersion").getValue()).isEqualTo("1.0.0-SNAPSHOT");
        assertThat(effectiveModel.getDependencies().stream()
                .filter(dependency -> Set.of(
                        "coco-audit-jdbc", "coco-replay-redis", "coco-rate-limit", "coco-observability")
                        .contains(dependency.getArtifactId()))
                .map(Dependency::getVersion)
                .collect(Collectors.toSet()))
                .containsExactly("1.0.0-SNAPSHOT");
    }

    private static final class LocalModelResolver implements ModelResolver {

        private final Path repositoryRoot;

        private LocalModelResolver(Path repositoryRoot) {
            this.repositoryRoot = repositoryRoot;
        }

        @Override
        public ModelSource resolveModel(String groupId, String artifactId, String version)
                throws UnresolvableModelException {
            Path modelPath = switch (artifactId) {
                case "coco-framework" -> this.repositoryRoot.resolve("pom.xml");
                case "coco-parent" -> this.repositoryRoot.resolve("coco-build/coco-parent/pom.xml");
                case "coco-dependencies" -> this.repositoryRoot.resolve("coco-build/coco-dependencies/pom.xml");
                default -> Path.of(System.getProperty("user.home"), ".m2", "repository")
                        .resolve(groupId.replace('.', '/'))
                        .resolve(artifactId)
                        .resolve(version)
                        .resolve(artifactId + "-" + version + ".pom");
            };
            if (!Files.isRegularFile(modelPath)) {
                throw new UnresolvableModelException("Model POM does not exist: " + modelPath,
                        groupId, artifactId, version);
            }
            return new FileModelSource(modelPath.toFile());
        }

        @Override
        public ModelSource resolveModel(Parent parent) throws UnresolvableModelException {
            return resolveModel(parent.getGroupId(), parent.getArtifactId(), parent.getVersion());
        }

        @Override
        public ModelSource resolveModel(Dependency dependency) throws UnresolvableModelException {
            return resolveModel(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion());
        }

        @Override
        public void addRepository(Repository repository) throws InvalidRepositoryException {
        }

        @Override
        public void addRepository(Repository repository, boolean replace) throws InvalidRepositoryException {
        }

        @Override
        public ModelResolver newCopy() {
            return new LocalModelResolver(this.repositoryRoot);
        }
    }
}
