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

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import junit.framework.TestCase;

import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.eclipse.aether.repository.RemoteRepository;
import org.junit.Assert;

/**
 * What nar-deploy reads of maven-deploy-plugin's configuration, from
 * hand-built project models.  As in Maven, an execution's configuration
 * already has the plugin's merged in.
 */
public class TestDeployPluginModel extends TestCase {

  private static final Pattern EXPRESSION = Pattern.compile("\\$\\{([^}]+)\\}");

  // ${name} from the given values, null when undefined, as Maven's
  // evaluator does for a whole-value expression
  private static ExpressionEvaluator evaluator(final Map<String, String> values) {
    return new ExpressionEvaluator() {
      @Override
      public Object evaluate(final String expression) {
        final Matcher m = EXPRESSION.matcher(expression);
        return m.matches() ? values.get(m.group(1)) : expression;
      }

      @Override
      public File alignToBaseDirectory(final File file) {
        return file;
      }
    };
  }

  private static Xpp3Dom configuration(final String... nameValues) {
    final Xpp3Dom configuration = new Xpp3Dom("configuration");
    for (int i = 0; i < nameValues.length; i += 2) {
      final Xpp3Dom child = new Xpp3Dom(nameValues[i]);
      child.setValue(nameValues[i + 1]);
      configuration.addChild(child);
    }
    return configuration;
  }

  private static PluginExecution execution(final String id, final String phase, final Xpp3Dom configuration) {
    final PluginExecution execution = new PluginExecution();
    execution.setId(id);
    execution.setPhase(phase);
    execution.addGoal("deploy");
    execution.setConfiguration(configuration);
    return execution;
  }

  private static Plugin deployPlugin(final String version, final Xpp3Dom configuration,
      final PluginExecution... executions) {
    final Plugin plugin = new Plugin();
    plugin.setGroupId("org.apache.maven.plugins");
    plugin.setArtifactId("maven-deploy-plugin");
    plugin.setVersion(version);
    plugin.setConfiguration(configuration);
    for (final PluginExecution execution : executions) {
      plugin.addExecution(execution);
    }
    return plugin;
  }

  private static MavenProject project(final String version, final Plugin plugin, final String... properties) {
    final Model model = new Model();
    model.setGroupId("g");
    model.setArtifactId("a");
    model.setVersion(version);
    model.setBuild(new Build());
    if (plugin != null) {
      model.getBuild().addPlugin(plugin);
    }
    for (int i = 0; i < properties.length; i += 2) {
      model.getProperties().setProperty(properties[i], properties[i + 1]);
    }
    final MavenProject project = new MavenProject(model);
    project.setArtifact(new DefaultArtifact("g", "a", version, "compile", "nar", null,
        new DefaultArtifactHandler("nar")));
    return project;
  }

  private static DeployPluginModel model(final MavenProject project) {
    return new DeployPluginModel(project, new Properties(), new Properties(), evaluator(Map.of()));
  }

  public final void testNoDeployPlugin() throws Exception {
    final DeployPluginModel model = model(project("1.0", null));
    Assert.assertNull(model.deployPlugin());
    Assert.assertEquals(List.of("true"), model.deployExecutionSkips("true"));
  }

  public final void testExecutionSkips() throws Exception {
    // the execution's own skip, else the command line's
    final DeployPluginModel model = model(project("1.0", deployPlugin("3.1.1", null,
        execution("default-deploy", "deploy", configuration("skip", "releases")),
        execution("publish", null, configuration()))));
    Assert.assertEquals(List.of("releases", "false"), model.deployExecutionSkips("false"));
  }

  public final void testExecutionNotRun() throws Exception {
    // bound to none, a phase mvn deploy doesn't reach
    final DeployPluginModel model = model(project("1.0", deployPlugin("3.1.1", null,
        execution("default-deploy", "none", configuration("skip", "false")))));
    Assert.assertTrue(model.deployExecutionSkips(null).isEmpty());
  }

  public final void testEarlierExecution() {
    // one bound before deploy has deployed everything by the time nar-deploy runs
    final DeployPluginModel model = model(project("1.0", deployPlugin("3.1.1", null,
        execution("early", "install", configuration()))));
    try {
      model.deployExecutionSkips(null);
      fail("accepted an execution before the deploy phase");
    } catch (final MojoExecutionException expected) {
      Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("early execution in the install phase"));
    }
    // unless it skips
    final DeployPluginModel skipping = model(project("1.0", deployPlugin("3.1.1", null,
        execution("early", "install", configuration("skip", "true")))));
    try {
      Assert.assertEquals(List.of("true"), skipping.deployExecutionSkips(null));
    } catch (final MojoExecutionException e) {
      fail(e.getMessage());
    }
  }

  public final void testDestination() throws Exception {
    // the release repository wins over altDeploymentRepository for a release
    final DeployPluginModel release = model(project("1.0", null,
        "altDeploymentRepository", "any::file:///any", "altReleaseDeploymentRepository", "rel::file:///rel",
        "altSnapshotDeploymentRepository", "snap::file:///snap"));
    final RemoteRepository repository = release.deploymentRepository();
    Assert.assertEquals("rel", repository.getId());
    Assert.assertEquals("file:///rel", repository.getUrl());
    // and the snapshot one for a snapshot
    Assert.assertEquals("snap", model(project("1.0-SNAPSHOT", null,
        "altDeploymentRepository", "any::file:///any", "altSnapshotDeploymentRepository", "snap::file:///snap"))
        .deploymentRepository().getId());
    // configured on the plugin, it wins over the property
    Assert.assertEquals("conf", model(project("1.0", deployPlugin("3.1.1",
        configuration("altDeploymentRepository", "conf::file:///conf")), "altDeploymentRepository", "any::file:///any"))
        .deploymentRepository().getId());
  }

  public final void testConflictingDestinations() {
    final DeployPluginModel model = model(project("1.0", deployPlugin("3.1.1", null,
        execution("a", "deploy", configuration("altDeploymentRepository", "a::file:///a")),
        execution("b", "deploy", configuration("altDeploymentRepository", "b::file:///b")))));
    try {
      model.deploymentRepository();
      fail("accepted two destinations");
    } catch (final MojoExecutionException expected) {
      Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("different repositories"));
    }
  }

  public final void testNoDestination() {
    try {
      model(project("1.0", null)).deploymentRepository();
      fail("found a repository");
    } catch (final MojoExecutionException expected) {
      Assert.assertTrue(expected.getMessage(), expected.getMessage().startsWith("No deployment repository"));
    }
  }

  public final void testDeployAtEnd() throws Exception {
    // as configured on the deploy-phase executions, else the property
    Assert.assertEquals("true", model(project("1.0", deployPlugin("3.1.1", null,
        execution("default-deploy", "deploy", configuration("deployAtEnd", "true"))))).deployConfiguration(
            "deployAtEnd", null));
    Assert.assertEquals("false", model(project("1.0", null)).deployConfiguration("deployAtEnd", "false"));
    try {
      model(project("1.0", deployPlugin("3.1.1", null,
          execution("a", "deploy", configuration("deployAtEnd", "true")),
          execution("b", "deploy", configuration("deployAtEnd", "false"))))).deployConfiguration("deployAtEnd", null);
      fail("accepted different deployAtEnd settings");
    } catch (final MojoExecutionException expected) {
      // expected
    }
  }

  public final void testExpressions() throws Exception {
    final DeployPluginModel model = new DeployPluginModel(project("1.0", null), new Properties(), new Properties(),
        evaluator(Map.of("dir", "/repo")));
    Assert.assertEquals("/repo", model.evaluate("${dir}"));
    Assert.assertEquals("plain", model.evaluate("plain"));
    // a skip whose expression doesn't resolve is maven-deploy-plugin's default
    Assert.assertEquals("false", model.skipValue("${undefined}"));
    Assert.assertNull(model.skipValue(null));
    // these would be nar-deploy's, not maven-deploy-plugin's
    for (final String scoped : new String[] { "${plugin.artifactId}", "${mojo.version}", "x/${mojoExecution}" }) {
      try {
        model.evaluate(scoped);
        fail("evaluated " + scoped);
      } catch (final MojoExecutionException expected) {
        // expected
      }
    }
  }

  public final void testRawProperty() {
    // the command line wins over the project, before any evaluation
    final Properties user = new Properties();
    user.setProperty("altDeploymentRepository", "cli::${x}");
    final DeployPluginModel model = new DeployPluginModel(project("1.0", null, "altDeploymentRepository", "pom::x"),
        user, new Properties(), evaluator(Map.of()));
    Assert.assertEquals("cli::${x}", model.rawProperty("altDeploymentRepository"));
    Assert.assertEquals("pom::x", model(project("1.0", null, "altDeploymentRepository", "pom::x"))
        .rawProperty("altDeploymentRepository"));
  }

  public final void testParseRepository() throws Exception {
    Assert.assertEquals("https://repo.example/maven",
        DeployPluginModel.parseRepository("id::https://repo.example/maven").getUrl());
    Assert.assertEquals("id", DeployPluginModel.parseRepository("id::default::file:///tmp/repo").getId());
    // compressed IPv6 addresses contain the separator too
    Assert.assertEquals("http://[::1]:8081/repository",
        DeployPluginModel.parseRepository("local::http://[::1]:8081/repository").getUrl());
    Assert.assertEquals("http://[::1]:8081/repository",
        DeployPluginModel.parseRepository("local::default::http://[::1]:8081/repository").getUrl());
    // padded parts are trimmed, as maven-deploy-plugin does
    final RemoteRepository padded = DeployPluginModel
        .parseRepository(" releases :: https://repo.example/maven ");
    Assert.assertEquals("releases", padded.getId());
    Assert.assertEquals("https://repo.example/maven", padded.getUrl());
    final RemoteRepository legacy = DeployPluginModel
        .parseRepository("releases :: default :: http://[::1]:8081/repository ");
    Assert.assertEquals("releases", legacy.getId());
    Assert.assertEquals("http://[::1]:8081/repository", legacy.getUrl());
    // only one layout is taken, as maven-deploy-plugin does
    Assert.assertEquals("default::http://h/",
        DeployPluginModel.parseRepository("id::default::default::http://h/").getUrl());
    for (final String bad : new String[] { "id", "id::", "id::legacy::file:///tmp", "::file:///tmp", "a::b::c::d",
        " ::file:///tmp", "id:: ", "id::default:: " }) {
      try {
        DeployPluginModel.parseRepository(bad);
        fail("accepted " + bad);
      } catch (final MojoExecutionException expected) {
        // expected
      }
    }
  }

  public final void testSupportsDeployPlugin() {
    for (final String old : new String[] { "2.8.2", "3.0.0-M1" }) {
      Assert.assertFalse(old, DeployPluginModel.supportsDeployPlugin(old));
    }
    for (final String current : new String[] { "3.0.0-M2", "3.0.0", "3.1.2", "4.0.0-beta-3" }) {
      Assert.assertTrue(current, DeployPluginModel.supportsDeployPlugin(current));
    }
  }
}
