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
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.PluginParameterExpressionEvaluator;
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
 * maven-deploy-plugin, so with "aol" the build fails if one would leave it
 * deploying: one that skips (true, releases or snapshots) skips this goal
 * too.  "aol" needs maven-deploy-plugin 3.0.0-M2 or later, which reads
 * releases and snapshots as conditional skips.  It also fails with
 * maven-deploy-plugin's deployAtEnd, which this goal, deploying right
 * away, would bypass, and with a deploy execution bound to an earlier phase.
 */
@Mojo(name = "nar-deploy", defaultPhase = LifecyclePhase.DEPLOY, requiresProject = true, threadSafe = true)
public class NarDeployMojo extends AbstractMojo {

  /**
   * "all" to deploy everything, or "aol" to deploy only this build's
   * AOL-specific artifacts.
   */
  @Parameter(property = "nar.deployScope", defaultValue = "all")
  private String deployScope;

  /**
   * maven-deploy-plugin's skip, which this goal follows.
   */
  @Parameter(property = "maven.deploy.skip", defaultValue = "false")
  private String deploySkip;

  @Parameter(defaultValue = "${project}", readonly = true, required = true)
  private MavenProject project;

  @Parameter(defaultValue = "${repositorySystemSession}", readonly = true, required = true)
  private RepositorySystemSession repoSession;

  @Parameter(defaultValue = "${session}", readonly = true, required = true)
  private MavenSession session;

  @Parameter(defaultValue = "${mojoExecution}", readonly = true, required = true)
  private MojoExecution mojoExecution;

  @Component
  private RepositorySystem repoSystem;

  @Override
  public void execute() throws MojoExecutionException {
    if ("all".equals(this.deployScope)) {
      return;
    }
    final DeployPluginModel model = new DeployPluginModel(this.project, this.session.getUserProperties(),
        this.session.getSystemProperties(), new PluginParameterExpressionEvaluator(this.session, this.mojoExecution));
    if (!"aol".equals(this.deployScope)) {
      throw new MojoExecutionException("nar.deployScope must be all or aol, not " + this.deployScope);
    }
    // older versions read skip as a boolean, so would deploy despite
    // releases or snapshots
    final Plugin deployPlugin = model.deployPlugin();
    final String version = deployPlugin == null ? null : deployPlugin.getVersion();
    if (version != null && !DeployPluginModel.supportsDeployPlugin(version)) {
      throw new MojoExecutionException("nar.deployScope=aol needs maven-deploy-plugin 3.0.0-M2 or later, not "
          + version);
    }
    // What each maven-deploy-plugin deploy execution's skip is set to: in its
    // configuration (with the plugin's merged in) or on the command line.
    // Unset, the project property set below makes it skip.
    final boolean snapshot = this.project.getArtifact().isSnapshot();
    final String userSkip = model.skipValue(this.session.getUserProperties().getProperty("maven.deploy.skip",
        this.session.getSystemProperties().getProperty("maven.deploy.skip")));
    final List<String> explicitSkips = model.deployExecutionSkips(userSkip);
    final List<String> blocking = explicitSkips.stream()
        .filter(value -> value != null && !DeployPluginModel.skips(value, snapshot)).collect(Collectors.toList());
    if (!blocking.isEmpty()) {
      throw new MojoExecutionException("nar.deployScope=aol can't stop maven-deploy-plugin from deploying the "
          + "shared artifacts while maven.deploy.skip is set to " + blocking.get(0)
          + " on the command line or in its configuration");
    }
    final boolean disabled = !explicitSkips.isEmpty() && explicitSkips.stream().allMatch(value -> value != null);
    if (disabled || DeployPluginModel.skips(this.deploySkip, snapshot)) {
      getLog().info("Skipping the deployment of the AOL-specific artifacts");
      // and of everything else, which maven-deploy-plugin would deploy
      this.project.getProperties().setProperty("maven.deploy.skip", "true");
      return;
    }
    // deploying at the end defers deploying until every module has built,
    // which this goal, deploying now, would bypass
    final String deployAtEnd = model.deployConfiguration("deployAtEnd",
        model.evaluate(model.rawProperty("deployAtEnd")));
    if (deployAtEnd != null && Boolean.parseBoolean(deployAtEnd)) {
      throw new MojoExecutionException("nar.deployScope=aol doesn't support maven-deploy-plugin's deployAtEnd");
    }

    final List<Artifact> artifacts = aolArtifacts(this.project.getAttachedArtifacts());
    if (artifacts.isEmpty()) {
      getLog().warn("No AOL-specific artifacts to deploy");
    } else {
      final RemoteRepository repository = this.repoSystem.newDeploymentRepository(this.repoSession,
          model.deploymentRepository());
      final DeployRequest request = new DeployRequest();
      request.setRepository(repository);
      for (final Artifact artifact : artifacts) {
        if (artifact.getFile() == null) {
          // rather than deploy only some of them and report success
          throw new MojoExecutionException("Can't deploy " + artifact + ", which has no file");
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
      final Classifier classifier = Classifier.parse(artifact.getClassifier());
      if (NarConstants.NAR_TYPE.equals(artifact.getType()) && classifier != null
          && !NarConstants.NAR_NO_ARCH.equals(artifact.getClassifier())) {
        aols.add(classifier.aol());
      }
    }
    return attached.stream()
        .filter(artifact -> artifact.getClassifier() != null)
        .filter(artifact -> aols.stream().anyMatch(aol -> artifact.getClassifier().equals(aol)
            || artifact.getClassifier().startsWith(aol + "-")))
        .collect(Collectors.toList());
  }

}
