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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * What maven-deploy-plugin's deploy goal will do in a build, read from the
 * project model as that plugin would see it: whether it skips, where it
 * deploys, whether it defers deploying.  nar-deploy, which deploys some
 * artifacts itself and stops maven-deploy-plugin from deploying the rest,
 * must deploy where it would and stop when it can't be sure.
 * <p>
 * Maven merges the plugin's configuration into each execution's, honoring
 * combine.self, so an execution's configuration is read on its own.  The
 * plugin's own applies only to the lifecycle's default deploy, which runs
 * when the pom declares no deploy execution.
 */
final class DeployPluginModel {

  /**
   * Where a deploy execution deploys.
   *
   * @param id the repository id, for its credentials
   * @param url the repository URL
   */
  record Destination(String id, String url) {

    RemoteRepository toRemoteRepository() {
      return new RemoteRepository.Builder(this.id, "default", this.url).build();
    }

    @Override
    public String toString() {
      return this.id + "::" + this.url;
    }
  }

  // chooses deploy executions; reading a skip can fail
  private interface ExecutionFilter {
    boolean test(PluginExecution execution) throws MojoExecutionException;
  }

  private static final String GROUP_ID = "org.apache.maven.plugins";

  private static final String ARTIFACT_ID = "maven-deploy-plugin";

  // the goal, and the phase it's bound to by default
  private static final String DEPLOY = "deploy";

  private static final String SKIP = "skip";

  private static final String SKIP_PROPERTY = "maven.deploy.skip";

  // the first version that reads skip as true, false, releases or snapshots
  private static final String CONDITIONAL_SKIP_VERSION = "3.0.0-M2";

  // The default lifecycle's phases, up to deploy
  private static final List<String> PHASES_TO_DEPLOY = List.of("validate", "initialize", "generate-sources",
      "process-sources", "generate-resources", "process-resources", "compile", "process-classes",
      "generate-test-sources", "process-test-sources", "generate-test-resources", "process-test-resources",
      "test-compile", "process-test-classes", "test", "prepare-package", "package", "pre-integration-test",
      "integration-test", "post-integration-test", "verify", "install", "deploy");

  // a layout is a plain word, unlike a URL's scheme, which ends with a colon
  private static final Pattern LAYOUT = Pattern.compile("\\s*([A-Za-z0-9_-]+)\\s*::(.*)");

  // expressions that would evaluate as nar-deploy's, not maven-deploy-plugin's
  private static final Pattern PLUGIN_SCOPED = Pattern.compile("\\$\\{(plugin|mojo|mojoExecution)[.}]");

  private final MavenProject project;

  private final Properties userProperties;

  private final Properties systemProperties;

  // maven-deploy-plugin's, so that ${...} evaluates as for that plugin
  private final ExpressionEvaluator evaluator;

  private final boolean snapshot;

  /**
   * @param project the project
   * @param userProperties the session's user properties (-D)
   * @param systemProperties the session's system properties
   * @param evaluator evaluates expressions as for maven-deploy-plugin
   */
  DeployPluginModel(final MavenProject project, final Properties userProperties, final Properties systemProperties,
      final ExpressionEvaluator evaluator) {
    this.project = project;
    this.userProperties = userProperties;
    this.systemProperties = systemProperties;
    this.evaluator = evaluator;
    this.snapshot = project.getArtifact().isSnapshot();
  }

  /**
   * Fails for a maven-deploy-plugin before 3.0.0-M2, which reads skip as a
   * boolean, so would deploy despite releases or snapshots.
   *
   * @throws MojoExecutionException if the plugin is older
   */
  void requireSupportedVersion() throws MojoExecutionException {
    final Plugin plugin = deployPlugin();
    final String version = plugin == null ? null : plugin.getVersion();
    if (version != null && !supportsDeployPlugin(version)) {
      throw new MojoExecutionException("nar.deployScope=aol needs maven-deploy-plugin " + CONDITIONAL_SKIP_VERSION
          + " or later, not " + version);
    }
  }

  /**
   * Whether nothing is to be deployed: every deploy execution skips by its
   * own skip or the command line's, or nar-deploy's own skip says so.
   *
   * @param ownSkip nar-deploy's skip
   * @return whether to skip
   * @throws MojoExecutionException if an execution would deploy whatever
   *           nar-deploy does: one that has deployed in an earlier phase, or
   *           one whose skip is explicitly false
   */
  boolean skipsDeploying(final String ownSkip) throws MojoExecutionException {
    final String userSkip = skipValue(commandLineProperty(SKIP_PROPERTY));
    final List<String> explicit = explicitSkips(userSkip);
    for (final String skip : explicit) {
      if (skip != null && !skips(skip)) {
        throw new MojoExecutionException("nar.deployScope=aol can't stop maven-deploy-plugin from deploying the "
            + "shared artifacts while maven.deploy.skip is set to " + skip
            + " on the command line or in its configuration");
      }
    }
    // unset, the project property nar-deploy sets makes it skip
    return !explicit.isEmpty() && explicit.stream().allMatch(Objects::nonNull) || skips(ownSkip);
  }

  /**
   * @return whether maven-deploy-plugin defers deploying until every module
   *         has built (deployAtEnd)
   * @throws MojoExecutionException if its deploy executions disagree
   */
  boolean deploysAtEnd() throws MojoExecutionException {
    final String deployAtEnd = sameForDeployPhase("deployAtEnd", evaluate(rawProperty("deployAtEnd")));
    return Boolean.parseBoolean(deployAtEnd);
  }

  /**
   * Where maven-deploy-plugin's executions that deploy would deploy: those
   * in the deploy phase that don't skip, or the lifecycle's default.
   *
   * @return the repository
   * @throws MojoExecutionException if they deploy to different ones, or
   *           there's none
   */
  Destination destination() throws MojoExecutionException {
    final List<Object> configurations = configurations(
        execution -> runsInDeployPhase(execution) && !skips(skipValue(configValue(execution.getConfiguration(), SKIP))));
    final Set<Destination> destinations = new LinkedHashSet<>();
    for (final Object configuration : configurations) {
      destinations.add(destination(configuration));
    }
    if (destinations.size() > 1) {
      throw new MojoExecutionException("maven-deploy-plugin's deploy executions deploy to different repositories, "
          + destinations.stream().map(d -> d == null ? "none" : d.toString()).collect(Collectors.toList())
          + "; nar.deployScope=aol deploys to one");
    }
    final Destination destination = destinations.iterator().next();
    if (destination == null) {
      throw new MojoExecutionException("No deployment repository: set distributionManagement or "
          + "altDeploymentRepository");
    }
    return destination;
  }

  /**
   * Parses a deployment repository given as id::url, or the older
   * id::default::url, trimming each part as maven-deploy-plugin does.
   *
   * @param repository the repository
   * @return the repository
   * @throws MojoExecutionException if it isn't in either form
   */
  static Destination parseRepository(final String repository) throws MojoExecutionException {
    final int separator = repository.indexOf("::");
    final String id = separator < 0 ? "" : repository.substring(0, separator).trim();
    String url = separator < 0 ? "" : repository.substring(separator + 2);
    final Matcher layout = LAYOUT.matcher(url);
    if (layout.matches()) {
      if (!"default".equals(layout.group(1))) {
        throw new MojoExecutionException("Invalid deployment repository " + repository + ", expected id::url");
      }
      url = layout.group(2);
    }
    url = url.trim();
    if (id.isEmpty() || url.isEmpty()) {
      throw new MojoExecutionException("Invalid deployment repository " + repository + ", expected id::url");
    }
    return new Destination(id, url);
  }

  /**
   * @param version a maven-deploy-plugin version
   * @return whether nar.deployScope=aol supports it: 3.0.0-M2 or later, which
   *         takes maven.deploy.skip=releases or snapshots
   */
  static boolean supportsDeployPlugin(final String version) {
    return new ComparableVersion(version).compareTo(new ComparableVersion(CONDITIONAL_SKIP_VERSION)) >= 0;
  }

  /**
   * The skip each deploy execution that mvn deploy runs will see when it's
   * set explicitly, in its configuration or on the command line; null where
   * it isn't.  Executions in an earlier phase count, as they deploy before
   * nar-deploy runs.
   */
  List<String> explicitSkips(final String userSkip) throws MojoExecutionException {
    final List<String> skips = new ArrayList<>();
    final Plugin plugin = deployPlugin();
    if (plugin == null || plugin.getExecutions().stream().noneMatch(DeployPluginModel::deploys)) {
      // the lifecycle's default deploy
      final String pluginSkip = plugin == null ? null : skipValue(configValue(plugin.getConfiguration(), SKIP));
      skips.add(pluginSkip != null ? pluginSkip : userSkip);
      return skips;
    }
    for (final PluginExecution execution : plugin.getExecutions()) {
      if (deploys(execution) && runsBeforeOrInDeployPhase(execution)) {
        final String executionSkip = skipValue(configValue(execution.getConfiguration(), SKIP));
        final String skip = executionSkip != null ? executionSkip : userSkip;
        requireNotDeployedEarlier(execution, skip);
        skips.add(skip);
      }
    }
    return skips;
  }

  // One bound to an earlier phase has deployed everything before nar-deploy
  // runs, unless it skipped: by its own settings or the project's
  // maven.deploy.skip, which nar-deploy hasn't set yet
  private void requireNotDeployedEarlier(final PluginExecution execution, final String skip)
      throws MojoExecutionException {
    final String phase = execution.getPhase();
    final String effective = skip != null ? skip : evaluate(this.project.getProperties().getProperty(SKIP_PROPERTY));
    if (phase != null && !DEPLOY.equals(phase) && !skips(effective)) {
      throw new MojoExecutionException("maven-deploy-plugin's " + execution.getId() + " execution in the "
          + phase + " phase, before this goal, has deployed everything, which nar.deployScope=aol can't prevent");
    }
  }

  /**
   * A parameter configured for the deploy-phase executions, skipping or not,
   * or for the lifecycle's default deploy, which wins over the property, as
   * in maven-deploy-plugin; otherwise the property's value.
   *
   * @throws MojoExecutionException if those executions are configured
   *           differently, as nar-deploy deploys once
   */
  String sameForDeployPhase(final String name, final String property) throws MojoExecutionException {
    final Set<String> values = new LinkedHashSet<>();
    for (final Object configuration : configurations(DeployPluginModel::runsInDeployPhase)) {
      final String value = evaluate(configValue(configuration, name));
      values.add(value != null ? value : property);
    }
    values.remove(null);
    if (values.size() > 1) {
      throw new MojoExecutionException("maven-deploy-plugin's deploy executions set " + name
          + " differently, " + values + "; nar.deployScope=aol deploys to one repository");
    }
    return values.isEmpty() ? null : values.iterator().next();
  }

  // The configurations of the deploy executions chosen, or the plugin's own,
  // as for the lifecycle's default deploy, when there's none (null without the
  // plugin)
  private List<Object> configurations(final ExecutionFilter chosen) throws MojoExecutionException {
    final Plugin plugin = deployPlugin();
    final List<Object> configurations = new ArrayList<>();
    if (plugin != null) {
      for (final PluginExecution execution : plugin.getExecutions()) {
        if (deploys(execution) && chosen.test(execution)) {
          configurations.add(execution.getConfiguration());
        }
      }
    }
    if (configurations.isEmpty()) {
      configurations.add(plugin == null ? null : plugin.getConfiguration());
    }
    return configurations;
  }

  /**
   * The repository a deploy execution with this configuration deploys to, as
   * maven-deploy-plugin chooses it: the snapshot or release repository, else
   * altDeploymentRepository (each configured, else the property), else
   * distributionManagement; null if there's none.
   */
  private Destination destination(final Object configuration) throws MojoExecutionException {
    final String specific = this.snapshot ? "altSnapshotDeploymentRepository" : "altReleaseDeploymentRepository";
    String alt = configured(configuration, specific);
    if (alt == null || alt.isEmpty()) {
      alt = configured(configuration, "altDeploymentRepository");
    }
    if (alt != null && !alt.isEmpty()) {
      return parseRepository(alt);
    }
    final ArtifactRepository repository = this.project.getDistributionManagementArtifactRepository();
    return repository == null ? null : new Destination(repository.getId(), repository.getUrl());
  }

  // A parameter as configured, else as its property, evaluated
  private String configured(final Object configuration, final String name) throws MojoExecutionException {
    final String value = evaluate(configValue(configuration, name));
    return value != null ? value : evaluate(rawProperty(name));
  }

  /**
   * A property as set, before Maven evaluates expressions in it for this
   * goal, which would be nar-deploy's rather than maven-deploy-plugin's: on
   * the command line, else in the project.
   */
  String rawProperty(final String name) {
    final String value = commandLineProperty(name);
    return value != null ? value : this.project.getProperties().getProperty(name);
  }

  // A user property (-D), else a system property
  private String commandLineProperty(final String name) {
    final String value = this.userProperties.getProperty(name);
    return value != null ? value : this.systemProperties.getProperty(name);
  }

  // A skip value as maven-deploy-plugin reads it: a boolean, or releases or
  // snapshots; the property isn't trimmed, configured values are (see
  // configValue)
  private boolean skips(final String value) {
    return Boolean.parseBoolean(value) || ("releases".equals(value) && !this.snapshot)
        || ("snapshots".equals(value) && this.snapshot);
  }

  /**
   * A skip value, evaluated; one set to an expression that doesn't resolve is
   * maven-deploy-plugin's default, false, rather than unset.
   */
  String skipValue(final String value) throws MojoExecutionException {
    final String evaluated = evaluate(value);
    return value != null && evaluated == null ? "false" : evaluated;
  }

  /**
   * Evaluates the expressions Maven leaves in plugin configuration until the
   * plugin runs, such as ${session.executionRootDirectory}.
   *
   * @throws MojoExecutionException for one that would evaluate as
   *           nar-deploy's rather than maven-deploy-plugin's
   */
  String evaluate(final String value) throws MojoExecutionException {
    if (value == null || value.indexOf("${") < 0) {
      return value;
    }
    if (PLUGIN_SCOPED.matcher(value).find()) {
      throw new MojoExecutionException("Can't evaluate maven-deploy-plugin's " + value + " for nar.deployScope=aol; "
          + "use an expression that doesn't depend on the plugin");
    }
    try {
      final Object evaluated = this.evaluator.evaluate(value);
      return evaluated == null ? null : evaluated.toString();
    } catch (final ExpressionEvaluationException e) {
      throw new MojoExecutionException("Can't evaluate maven-deploy-plugin's " + value, e);
    }
  }

  Plugin deployPlugin() {
    for (final Plugin plugin : this.project.getBuildPlugins()) {
      if (GROUP_ID.equals(plugin.getGroupId()) && ARTIFACT_ID.equals(plugin.getArtifactId())) {
        return plugin;
      }
    }
    return null;
  }

  private static boolean deploys(final PluginExecution execution) {
    return execution.getGoals().contains(DEPLOY);
  }

  private static boolean runsInDeployPhase(final PluginExecution execution) {
    return execution.getPhase() == null || DEPLOY.equals(execution.getPhase());
  }

  // run by mvn deploy: not bound to none or a later phase
  private static boolean runsBeforeOrInDeployPhase(final PluginExecution execution) {
    return execution.getPhase() == null || PHASES_TO_DEPLOY.contains(execution.getPhase());
  }

  // A configured value, trimmed.  The configuration is an Xpp3Dom, read by
  // reflection so that this plugin needn't compile against the plexus-xml
  // Maven provides.
  private static String configValue(final Object configuration, final String name) throws MojoExecutionException {
    if (configuration == null) {
      return null;
    }
    try {
      final Object child = configuration.getClass().getMethod("getChild", String.class).invoke(configuration, name);
      final Object value = child == null ? null : child.getClass().getMethod("getValue").invoke(child);
      return value == null ? null : value.toString().trim();
    } catch (final ReflectiveOperationException e) {
      // fail rather than act as if it weren't configured
      throw new MojoExecutionException("Can't read maven-deploy-plugin's " + name + " configuration", e);
    }
  }
}
