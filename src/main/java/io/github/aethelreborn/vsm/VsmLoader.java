package io.github.aethelreborn.vsm;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * Paper plugin loader (spec 3.1, 4.1, 17.1).
 *
 * <p>Third-party libraries are resolved by the server instead of being shaded into the jar, which keeps the
 * plugin small and lets Paper cache the artifacts in its own {@code libraries/} folder. The versions must
 * stay in sync with the {@code compileOnly} entries in {@code build.gradle.kts}.
 */
public final class VsmLoader implements PluginLoader {

    public static final String HIKARI_VERSION = "6.3.0";
    public static final String SQLITE_VERSION = "3.50.3.0";
    public static final String MYSQL_VERSION = "9.4.0";

    @Override
    public void classloader(PluginClasspathBuilder classpathBuilder) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();
        resolver.addRepository(new RemoteRepository.Builder(
                "papermc", "default", "https://repo.papermc.io/repository/maven-public/").build());
        resolver.addRepository(new RemoteRepository.Builder(
                "central", "default", MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR).build());

        resolver.addDependency(new Dependency(new DefaultArtifact("com.zaxxer:HikariCP:" + HIKARI_VERSION), null));
        resolver.addDependency(new Dependency(
                new DefaultArtifact("org.xerial:sqlite-jdbc:" + SQLITE_VERSION), null));
        resolver.addDependency(new Dependency(
                new DefaultArtifact("com.mysql:mysql-connector-j:" + MYSQL_VERSION), null));

        classpathBuilder.addLibrary(resolver);
    }
}