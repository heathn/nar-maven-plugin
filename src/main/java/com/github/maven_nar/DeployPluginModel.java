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

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
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
 */
final class DeployPluginModel {

  // The default lifecycle's phases, up to deploy
  private static final List<String> PHASES_TO_DEPLOY = List.of("validate", "initialize", "generate-sources",
      "process-sources", "generate-resources", "process-resources", "compile", "process-classes",
      "generate-test-sources", "process-test-sources", "generate-test-resources", "process-test-resources",
      "test-compile", "process-test-classes", "test", "prepare-package", "package", "pre-integration-test",
      "integration-test", "post-integration-test", "verify", "install", "deploy");

  private final MavenProject project;

  private final Properties userProperties;

  private final Properties systemProperties;

  // maven-deploy-plugin's, so that ${...} evaluates as for that plugin
  private final ExpressionEvaluator evaluator;

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
    // maven-deploy-plugin trims each part
    final int separator = repository.indexOf("::");
    final String id = separator < 0 ? "" : repository.substring(0, separator).trim();
    String url = separator < 0 ? "" : repository.substring(separator + 2);
    // a layout is a plain word, unlike a URL's scheme, which ends with a colon
    final java.util.regex.Matcher layout = java.util.regex.Pattern.compile("\\s*([A-Za-z0-9_-]+)\\s*::(.*)")
        .matcher(url);
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
    return new RemoteRepository.Builder(id, "default", url).build();
  }

  /**
   * @return whether a maven.deploy.skip value skips deploying this project
   */
  static boolean skips(final String value, final boolean snapshot) {
    // as maven-deploy-plugin reads it: a boolean, or releases or snapshots
    // untrimmed, as maven-deploy-plugin reads it
    return Boolean.parseBoolean(value) || ("releases".equals(value) && !snapshot)
        || ("snapshots".equals(value) && snapshot);
  }

  RemoteRepository deploymentRepository() throws MojoExecutionException {
    // where each maven-deploy-plugin execution that runs in the deploy phase,
    // and doesn't skip itself, would deploy, which must be the same for all
    final Plugin plugin = deployPlugin();
    final List<Object> configurations = new ArrayList<>();
    if (plugin != null) {
      final boolean snapshot = this.project.getArtifact().isSnapshot();
      for (final PluginExecution execution : plugin.getExecutions()) {
        final String phase = execution.getPhase();
        if (execution.getGoals().contains("deploy") && (phase == null || "deploy".equals(phase))
            && !skips(skipValue(valueOf(execution.getConfiguration(), "skip")), snapshot)) {
          configurations.add(execution.getConfiguration());
        }
      }
    }
    // Maven merges the plugin's configuration into each execution's (honoring
    // combine.self), so the plugin's own is only for when there's none
    if (configurations.isEmpty()) {
      configurations.add(plugin == null ? null : plugin.getConfiguration());
    }
    // as id and URL: a RemoteRepository's equality covers more
    final Set<Map.Entry<String, String>> destinations = new LinkedHashSet<>();
    for (final Object configuration : configurations) {
      destinations.add(destination(configuration));
    }
    if (destinations.size() > 1) {
      throw new MojoExecutionException("maven-deploy-plugin's deploy executions deploy to different repositories, "
          + destinations.stream().map(d -> d == null ? "none" : d.getKey() + "::" + d.getValue())
              .collect(Collectors.toList())
          + "; nar.deployScope=aol deploys to one");
    }
    final Map.Entry<String, String> destination = destinations.iterator().next();
    if (destination == null) {
      throw new MojoExecutionException("No deployment repository: set distributionManagement or "
          + "altDeploymentRepository");
    }
    return new RemoteRepository.Builder(destination.getKey(), "default", destination.getValue()).build();
  }

  /**
   * The repository a deploy execution with this configuration deploys to, as
   * maven-deploy-plugin chooses it: the snapshot or release repository, else
   * altDeploymentRepository (each configured, else the property), else
   * distributionManagement; as its id and URL, or null if there's none.
   */
  private Map.Entry<String, String> destination(final Object configuration) throws MojoExecutionException {
    final boolean snapshot = this.project.getArtifact().isSnapshot();
    String alt = configured(configuration, snapshot ? "altSnapshotDeploymentRepository"
        : "altReleaseDeploymentRepository", rawProperty(snapshot ? "altSnapshotDeploymentRepository"
        : "altReleaseDeploymentRepository"));
    if (alt == null || alt.isEmpty()) {
      alt = configured(configuration, "altDeploymentRepository", rawProperty("altDeploymentRepository"));
    }
    if (alt != null && !alt.isEmpty()) {
      final RemoteRepository repository = parseRepository(alt);
      return new AbstractMap.SimpleImmutableEntry<>(repository.getId(), repository.getUrl());
    }
    final ArtifactRepository repository = this.project.getDistributionManagementArtifactRepository();
    return repository == null ? null : new AbstractMap.SimpleImmutableEntry<>(repository.getId(), repository.getUrl());
  }

  private String configured(final Object configuration, final String name, final String property)
      throws MojoExecutionException {
    final String value = evaluate(valueOf(configuration, name));
    return value != null ? value : evaluate(property);
  }

  /**
   * A property as set, before Maven evaluates expressions in it for this
   * goal, which would be nar-deploy's rather than maven-deploy-plugin's: on
   * the command line (user or system property), else in the project.
   */
  String rawProperty(final String name) {
    String value = this.userProperties.getProperty(name);
    if (value == null) {
      value = this.systemProperties.getProperty(name);
    }
    return value != null ? value : this.project.getProperties().getProperty(name);
  }

  /**
   * @param version a maven-deploy-plugin version
   * @return whether nar.deployScope=aol supports it: 3.0.0-M2 or later, which
   *         takes maven.deploy.skip=releases or snapshots
   */
  public static boolean supportsDeployPlugin(final String version) {
    return new ComparableVersion(version).compareTo(new ComparableVersion("3.0.0-M2")) >= 0;
  }

  /**
   * The skip value each maven-deploy-plugin deploy execution will see when
   * it's set explicitly: in the execution's configuration (with the plugin's
   * merged in) or on the command line; null where it isn't.
   */
  List<String> deployExecutionSkips(final String userSkip) throws MojoExecutionException {
    final List<String> skips = new java.util.ArrayList<>();
    final Plugin plugin = deployPlugin();
    if (plugin == null) {
      skips.add(userSkip);
      return skips;
    }
    for (final PluginExecution execution : plugin.getExecutions()) {
      // one bound to none, or to a phase mvn deploy doesn't run, never runs
      final String phase = execution.getPhase();
      if (execution.getGoals().contains("deploy") && (phase == null || PHASES_TO_DEPLOY.contains(phase))) {
        final String executionSkip = skipValue(valueOf(execution.getConfiguration(), "skip"));
        // the execution's configuration already has the plugin's merged in
        final String skip = executionSkip != null ? executionSkip : userSkip;
        // One bound to an earlier phase would deploy everything before this
        // goal runs, unless it skipped, by its own settings or the project's
        // maven.deploy.skip, which this goal hasn't changed yet
        final String earlierSkip = skip != null ? skip
            : evaluate(this.project.getProperties().getProperty("maven.deploy.skip"));
        if (phase != null && !"deploy".equals(phase) && !skips(earlierSkip, this.project.getArtifact().isSnapshot())) {
          throw new MojoExecutionException("maven-deploy-plugin's " + execution.getId() + " execution in the "
              + execution.getPhase() + " phase, before this goal, has deployed everything, which nar.deployScope=aol "
              + "can't prevent");
        }
        skips.add(skip);
      }
    }
    // with no deploy execution at all, the lifecycle's would use these
    if (plugin.getExecutions().stream().noneMatch(execution -> execution.getGoals().contains("deploy"))) {
      final String pluginSkip = skipValue(valueOf(plugin.getConfiguration(), "skip"));
      skips.add(pluginSkip != null ? pluginSkip : userSkip);
    }
    return skips;
  }

  /**
   * A parameter configured on maven-deploy-plugin for the deploy executions
   * that will run in the deploy phase (in each execution's configuration, with
   * the plugin's merged in; the plugin's only when there's no such execution),
   * which wins over the property, as in that plugin; otherwise the
   * property's value.  Fails if those executions are configured differently,
   * as this goal deploys once, to one repository.
   */
  String deployConfiguration(final String name, final String property) throws MojoExecutionException {
    final Plugin plugin = deployPlugin();
    if (plugin == null) {
      return property;
    }
    final java.util.Set<String> values = new java.util.LinkedHashSet<>();
    boolean active = false;
    for (final PluginExecution execution : plugin.getExecutions()) {
      final String phase = execution.getPhase();
      if (execution.getGoals().contains("deploy") && (phase == null || "deploy".equals(phase))) {
        active = true;
        final String value = evaluate(valueOf(execution.getConfiguration(), name));
        values.add(value != null ? value : property);
      }
    }
    if (!active) {
      final String pluginValue = evaluate(valueOf(plugin.getConfiguration(), name));
      values.add(pluginValue != null ? pluginValue : property);
    }
    values.remove(null);
    if (values.size() > 1) {
      throw new MojoExecutionException("maven-deploy-plugin's deploy executions set " + name
          + " differently, " + values + "; nar.deployScope=aol deploys to one repository");
    }
    return values.isEmpty() ? null : values.iterator().next();
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
   */
  String evaluate(final String value) throws MojoExecutionException {
    if (value == null || value.indexOf("${") < 0) {
      return value;
    }
    // these would be nar-deploy's, not maven-deploy-plugin's
    if (java.util.regex.Pattern.compile("\\$\\{(plugin|mojo|mojoExecution)[.}]").matcher(value).find()) {
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
      if ("org.apache.maven.plugins".equals(plugin.getGroupId())
          && "maven-deploy-plugin".equals(plugin.getArtifactId())) {
        return plugin;
      }
    }
    return null;
  }

  // The configuration is an Xpp3Dom, from a library this plugin doesn't
  // compile against.
  private static String valueOf(final Object configuration, final String name) throws MojoExecutionException {
    if (configuration == null) {
      return null;
    }
    try {
      final Object skip = configuration.getClass().getMethod("getChild", String.class).invoke(configuration, name);
      final Object value = skip == null ? null : skip.getClass().getMethod("getValue").invoke(skip);
      return value == null ? null : value.toString().trim();
    } catch (final ReflectiveOperationException e) {
      // fail rather than act as if it weren't configured
      throw new MojoExecutionException("Can't read maven-deploy-plugin's " + name + " configuration", e);
    }
  }
}
