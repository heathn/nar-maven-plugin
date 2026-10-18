/*
 * #%L
 * Native ARchive plugin for Maven
 * %%
 * Copyright (C) 2002 - 2014 NAR Maven Plugin developers.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package com.github.maven_nar;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.deployment.DeploymentException;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * Chooses what the deploy phase deploys.  With deployScope "all" (the
 * default) it does nothing, and maven-deploy-plugin deploys everything as
 * usual.  With "aol" it deploys only the AOL-specific artifacts this build
 * attached (classifier &lt;aol&gt;-&lt;type&gt;, or the AOL itself, for
 * the AOLs built here) and skips maven-deploy-plugin, so that when each AOL
 * is built on its own machine, only one build deploys the pom, the main
 * artifact, the noarch NAR and the other attachments, which every build
 * would otherwise deploy again under the same version.  It deploys to the
 * repository maven-deploy-plugin would: altSnapshotDeploymentRepository or
 * altReleaseDeploymentRepository, altDeploymentRepository, or the
 * distributionManagement repository.
 * <p>
 * Other modules in the reactor, such as a parent pom, are still deployed
 * by every build; skip them with maven.deploy.skip in a profile of their
 * pom.  maven.deploy.skip set on the command line, or a skip configured on
 * maven-deploy-plugin, would also keep this goal from stopping
 * maven-deploy-plugin, so with "aol" the build fails if one is set to
 * anything but true.
 */
@Mojo(name = "nar-deploy", defaultPhase = LifecyclePhase.DEPLOY, requiresProject = true, threadSafe = true)
public class NarDeployMojo extends AbstractMojo {

  /**
   * "all" to deploy everything, or "aol" to deploy only this build's
   * AOL-specific artifacts.
   */
  @Parameter(property = "nar.deployScope", defaultValue = "all")
  private String deployScope;

  @Parameter(property = "maven.deploy.skip", defaultValue = "false")
  private String skip;

  @Parameter(property = "altDeploymentRepository")
  private String altDeploymentRepository;

  @Parameter(property = "altReleaseDeploymentRepository")
  private String altReleaseDeploymentRepository;

  @Parameter(property = "altSnapshotDeploymentRepository")
  private String altSnapshotDeploymentRepository;

  @Parameter(defaultValue = "${project}", readonly = true, required = true)
  private MavenProject project;

  @Parameter(defaultValue = "${repositorySystemSession}", readonly = true, required = true)
  private RepositorySystemSession repoSession;

  @Parameter(defaultValue = "${session}", readonly = true, required = true)
  private MavenSession session;

  @Component
  private RepositorySystem repoSystem;

  @Override
  public void execute() throws MojoExecutionException {
    if ("all".equals(this.deployScope)) {
      return;
    }
    if (!"aol".equals(this.deployScope)) {
      throw new MojoExecutionException("nar.deployScope must be all or aol, not " + this.deployScope);
    }
    // What each maven-deploy-plugin deploy execution's skip is set to: in its
    // configuration, the plugin's, or on the command line.  Unset, the
    // project property set below makes it skip.
    final boolean snapshot = this.project.getArtifact().isSnapshot();
    final String userSkip = this.session.getUserProperties().getProperty("maven.deploy.skip",
        this.session.getSystemProperties().getProperty("maven.deploy.skip"));
    final List<String> explicitSkips = deployExecutionSkips(userSkip);
    final List<String> blocking = explicitSkips.stream()
        .filter(value -> value != null && !skips(value, snapshot)).collect(Collectors.toList());
    if (!blocking.isEmpty()) {
      throw new MojoExecutionException("nar.deployScope=aol can't stop maven-deploy-plugin from deploying the "
          + "shared artifacts while maven.deploy.skip is set to " + blocking.get(0)
          + " on the command line or in its configuration");
    }
    final boolean disabled = !explicitSkips.isEmpty() && explicitSkips.stream().allMatch(value -> value != null);
    if (disabled || skips(this.skip, snapshot)) {
      getLog().info("Skipping the deployment of the AOL-specific artifacts");
      return;
    }

    final List<Artifact> artifacts = aolArtifacts(this.project.getAttachedArtifacts());
    if (artifacts.isEmpty()) {
      getLog().warn("No AOL-specific artifacts to deploy");
    } else {
      final RemoteRepository repository = this.repoSystem.newDeploymentRepository(this.repoSession,
          deploymentRepository());
      final DeployRequest request = new DeployRequest();
      request.setRepository(repository);
      for (final Artifact artifact : artifacts) {
        if (artifact.getFile() == null) {
          getLog().warn("Not deploying " + artifact + ", which has no file");
          continue;
        }
        getLog().info("Deploying " + artifact.getFile().getName() + " to " + repository.getUrl());
        request.addArtifact(new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(),
            artifact.getClassifier(), artifact.getArtifactHandler().getExtension(), artifact.getVersion())
            .setFile(artifact.getFile()));
      }
      try {
        this.repoSystem.deploy(this.repoSession, request);
      } catch (final DeploymentException e) {
        throw new MojoExecutionException("Could not deploy the AOL-specific artifacts", e);
      }
    }

    // maven-deploy-plugin runs next in the deploy phase
    getLog().info("Leaving the pom, main artifact and other attachments to another build");
    this.project.getProperties().setProperty("maven.deploy.skip", "true");
  }

  /**
   * The attached artifacts specific to an AOL built here: the
   * &lt;aol&gt;-&lt;type&gt; NARs, and anything classified with one of
   * their AOLs or prefixed with it.
   *
   * @param attached the project's attached artifacts
   * @return the AOL-specific ones
   */
  public static List<Artifact> aolArtifacts(final List<Artifact> attached) {
    final Set<String> aols = new LinkedHashSet<>();
    for (final Artifact artifact : attached) {
      final String classifier = artifact.getClassifier();
      if (NarConstants.NAR_TYPE.equals(artifact.getType()) && classifier != null
          && !NarConstants.NAR_NO_ARCH.equals(classifier) && classifier.lastIndexOf('-') > 0) {
        aols.add(classifier.substring(0, classifier.lastIndexOf('-')));
      }
    }
    return attached.stream()
        .filter(artifact -> artifact.getClassifier() != null)
        .filter(artifact -> aols.stream().anyMatch(aol -> artifact.getClassifier().equals(aol)
            || artifact.getClassifier().startsWith(aol + "-")))
        .collect(Collectors.toList());
  }

  /**
   * Parses a deployment repository given as id::url, or the older
   * id::default::url.
   *
   * @param repository the repository
   * @return the repository
   * @throws MojoExecutionException if it isn't in either form
   */
  public static RemoteRepository parseRepository(final String repository) throws MojoExecutionException {
    final int separator = repository.indexOf("::");
    String url = separator < 0 ? "" : repository.substring(separator + 2);
    // a layout is a plain word, unlike a URL's scheme, which ends with a colon
    final java.util.regex.Matcher layout = java.util.regex.Pattern.compile("([A-Za-z0-9_-]+)::(.*)").matcher(url);
    if (layout.matches()) {
      if (!"default".equals(layout.group(1))) {
        throw new MojoExecutionException("Invalid deployment repository " + repository + ", expected id::url");
      }
      url = layout.group(2);
    }
    if (separator <= 0 || url.isEmpty()) {
      throw new MojoExecutionException("Invalid deployment repository " + repository + ", expected id::url");
    }
    return new RemoteRepository.Builder(repository.substring(0, separator), "default", url).build();
  }

  /**
   * @return whether a maven.deploy.skip value skips deploying this project
   */
  private static boolean skips(final String value, final boolean snapshot) {
    return "true".equals(value) || ("releases".equals(value) && !snapshot) || ("snapshots".equals(value) && snapshot);
  }

  private RemoteRepository deploymentRepository() throws MojoExecutionException {
    // configured on maven-deploy-plugin, as it would use them, or as properties
    final String snapshotRepository = deployConfiguration("altSnapshotDeploymentRepository",
        this.altSnapshotDeploymentRepository);
    final String releaseRepository = deployConfiguration("altReleaseDeploymentRepository",
        this.altReleaseDeploymentRepository);
    String alt = this.project.getArtifact().isSnapshot() ? snapshotRepository : releaseRepository;
    if (alt == null || alt.isEmpty()) {
      alt = deployConfiguration("altDeploymentRepository", this.altDeploymentRepository);
    }
    if (alt != null && !alt.isEmpty()) {
      return parseRepository(alt);
    }
    final ArtifactRepository repository = this.project.getDistributionManagementArtifactRepository();
    if (repository == null) {
      throw new MojoExecutionException("No deployment repository: set distributionManagement or "
          + "altDeploymentRepository");
    }
    return new RemoteRepository.Builder(repository.getId(), "default", repository.getUrl()).build();
  }

  /**
   * The skip value each maven-deploy-plugin deploy execution will see when
   * it's set explicitly: in the execution's configuration, the plugin's, or
   * on the command line; null where it isn't.
   */
  private List<String> deployExecutionSkips(final String userSkip) {
    final List<String> skips = new java.util.ArrayList<>();
    final Plugin plugin = deployPlugin();
    if (plugin == null) {
      skips.add(userSkip);
      return skips;
    }
    final String pluginSkip = valueOf(plugin.getConfiguration(), "skip");
    for (final PluginExecution execution : plugin.getExecutions()) {
      if (execution.getGoals().contains("deploy")) {
        final String executionSkip = valueOf(execution.getConfiguration(), "skip");
        skips.add(executionSkip != null ? executionSkip : pluginSkip != null ? pluginSkip : userSkip);
      }
    }
    if (skips.isEmpty()) {
      skips.add(pluginSkip != null ? pluginSkip : userSkip);
    }
    return skips;
  }

  /**
   * A parameter configured on maven-deploy-plugin, for the plugin or its
   * default-deploy execution, which wins over the property, as in that
   * plugin; otherwise the property's value.
   */
  private String deployConfiguration(final String name, final String property) {
    final Plugin plugin = deployPlugin();
    if (plugin != null) {
      for (final PluginExecution execution : plugin.getExecutions()) {
        final String value = valueOf(execution.getConfiguration(), name);
        if ("default-deploy".equals(execution.getId()) && value != null) {
          return value;
        }
      }
      final String value = valueOf(plugin.getConfiguration(), name);
      if (value != null) {
        return value;
      }
    }
    return property;
  }

  private Plugin deployPlugin() {
    for (final Plugin plugin : this.project.getBuildPlugins()) {
      if ("org.apache.maven.plugins".equals(plugin.getGroupId())
          && "maven-deploy-plugin".equals(plugin.getArtifactId())) {
        return plugin;
      }
    }
    return null;
  }

  // The configuration is an Xpp3Dom, from a library this plugin doesn't
  // compile against.
  private static String valueOf(final Object configuration, final String name) {
    if (configuration == null) {
      return null;
    }
    try {
      final Object skip = configuration.getClass().getMethod("getChild", String.class).invoke(configuration, name);
      final Object value = skip == null ? null : skip.getClass().getMethod("getValue").invoke(skip);
      return value == null ? null : value.toString().trim();
    } catch (final ReflectiveOperationException e) {
      return null;
    }
  }
}
