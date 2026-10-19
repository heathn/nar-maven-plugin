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
 * Chooses what the deploy phase deploys, for projects whose AOLs are built
 * on separate machines, each of which would otherwise deploy the pom, the
 * main artifact, the noarch NAR and the other attachments again under the
 * same version.
 * <ul>
 * <li>deployScope "all", the default: nothing; maven-deploy-plugin deploys
 * everything as usual.</li>
 * <li>"aol": deploys only the AOL-specific artifacts this build attached
 * (classified &lt;aol&gt;-&lt;type&gt;, or with an AOL built here or
 * prefixed with one) and stops maven-deploy-plugin, so one build deploys
 * the rest.  They go where maven-deploy-plugin would deploy:
 * altSnapshotDeploymentRepository or altReleaseDeploymentRepository,
 * altDeploymentRepository, or distributionManagement.</li>
 * </ul>
 * With "aol" the build fails before deploying anything when nar-deploy
 * can't be sure maven-deploy-plugin won't deploy: a skip set to false on the
 * command line or on that plugin, a deploy execution bound to an earlier
 * phase, deployAtEnd, or maven-deploy-plugin before 3.0.0-M2, which reads
 * skip as a boolean.  A skip that skips (true, releases or snapshots) skips
 * this goal too.  Other modules in the reactor, such as a parent pom, are
 * still deployed by every build; skip them with maven.deploy.skip in a
 * profile of their pom.
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
   * Skips this goal: true, false, releases or snapshots, as
   * maven-deploy-plugin reads maven.deploy.skip.  With "aol", skipping also
   * skips maven-deploy-plugin.
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
    if (!"aol".equals(this.deployScope)) {
      throw new MojoExecutionException("nar.deployScope must be all or aol, not " + this.deployScope);
    }
    final DeployPluginModel deployPlugin = new DeployPluginModel(this.project, this.session.getUserProperties(),
        this.session.getSystemProperties(), new PluginParameterExpressionEvaluator(this.session, this.mojoExecution));
    deployPlugin.requireSupportedVersion();
    if (deployPlugin.skipsDeploying(this.deploySkip)) {
      getLog().info("Skipping the deployment of the AOL-specific artifacts");
      skipDeployPlugin();
      return;
    }
    if (deployPlugin.deploysAtEnd()) {
      // deferring until every module has built, which this goal, deploying
      // now, would bypass
      throw new MojoExecutionException("nar.deployScope=aol doesn't support maven-deploy-plugin's deployAtEnd");
    }

    final List<Artifact> artifacts = aolArtifacts(this.project.getAttachedArtifacts());
    if (artifacts.isEmpty()) {
      getLog().warn("No AOL-specific artifacts to deploy");
    } else {
      deploy(artifacts, deployPlugin.destination().toRemoteRepository());
    }
    getLog().info("Leaving the pom, main artifact and other attachments to another build");
    skipDeployPlugin();
  }

  private void deploy(final List<Artifact> artifacts, final RemoteRepository destination)
      throws MojoExecutionException {
    final RemoteRepository repository = this.repoSystem.newDeploymentRepository(this.repoSession, destination);
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

  /**
   * Stops maven-deploy-plugin deploying.  The nar lifecycle binds this goal
   * ahead of deploy:deploy in the deploy phase, and maven-deploy-plugin reads
   * its skip, ${maven.deploy.skip}, when it runs, from this project property
   * unless the command line or its configuration sets it, which
   * {@link DeployPluginModel#skipsDeploying} has ruled out.
   */
  private void skipDeployPlugin() {
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
