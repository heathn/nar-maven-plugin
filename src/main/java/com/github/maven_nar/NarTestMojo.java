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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.artifact.filter.collection.ScopeFilter;
import org.apache.maven.surefire.shared.lang3.function.Failable;

import org.eclipse.aether.artifact.Artifact;

/**
 * Tests NAR files. Runs Native Tests and executables if produced.
 *
 * @author Mark Donszelmann
 */
@Mojo(name = "nar-test", defaultPhase = LifecyclePhase.TEST, requiresProject = true,
  requiresDependencyResolution = ResolutionScope.TEST)
public class NarTestMojo extends AbstractCompileMojo {
  /**
   * The classpath elements of the project being tested.
   */
  @Parameter(defaultValue = "${project.testClasspathElements}", required = true, readonly = true)
  private List<String> classpathElements;

  /**
   * Directory for test resources. Defaults to src/test/resources
   */
  @Parameter(defaultValue = "${basedir}/src/test/resources", required = true)
  private File testResourceDirectory;

  private List<String> generateEnvironment(Map<String, String> environmentVariables) throws MojoExecutionException, MojoFailureException {
    List<String> env = new ArrayList<>();

    // Add the default configured environment
    env.addAll(generateEnvironment());

    // Add manually specified environment variables
    for (Map.Entry<String, String> envVar : environmentVariables.entrySet()) {
      getLog().debug("Adding environment variable: " + envVar.getKey() + " with value: " + envVar.getValue());
      env.add(envVar.getKey() + "=" + envVar.getValue());
    }

    return env;
  }

  private List<String> generateEnvironment() throws MojoExecutionException, MojoFailureException {
    final List<String> env = new ArrayList<>();

    final Set<Path> sharedPaths;
    try {
      // add all shared libraries of this package
      sharedPaths = getLibraries().stream()
        .filter(lib -> lib.getType().equals(Library.SHARED))
        .map(Failable.asFunction(lib -> getLayout().getLibDirectory(getTargetDirectory(),
            getMavenProject().getArtifactId(),
            getMavenProject().getVersion(), getAOL().toString(), lib.getType())))
        .peek(path -> getLog().debug("Adding path to shared library: " + path))
        .collect(Collectors.toSet());
    } catch (RuntimeException e) {
      throw new MojoExecutionException(e);
    }

    // add dependent shared libraries
    final String classifier = getAOL() + "-shared";
    final List<NarArtifact> narArtifacts = getNarArtifacts();
    final List<Artifact> dependencies = getNarManager().getAttachedNarDependencies(narArtifacts, classifier);
    for (final Artifact dependency : dependencies) {
      getLog().debug("Looking for dependency " + dependency);

      // FIXME reported to maven developer list, isSnapshot
      // changes behaviour
      // of getBaseVersion, called in pathOf.
      dependency.isSnapshot();

      final Path libDirectory = getLayout()
          .getLibDirectory(getUnpackDirectory(), dependency.getArtifactId(), dependency.getBaseVersion(),
              getAOL().toString(), Library.SHARED);
      sharedPaths.add(libDirectory);
    }

    // set environment
    if (sharedPaths.size() > 0) {
      String sharedPath = sharedPaths.stream()
        .map(Path::toString)
        .collect(Collectors.joining(File.pathSeparator));

      final String sharedEnv = NarUtil.addLibraryPathToEnv(sharedPath, null, getOS());
      env.add(sharedEnv);
    }

    // necessary to find WinSxS
    if (getOS().equals(OS.WINDOWS)) {
      env.add("SystemRoot=" + NarUtil.getEnv("SystemRoot", "SystemRoot", "C:\\Windows"));
    } else {
      // binPath is added to the PATH on Windows but not on a non-Windows
      // env.  Add the bin dir to the path in these cases to be able to
      // locate any test resources.
      // Add executable directory
      // NOTE should we use layout here ?
      Path binPath = getTestTargetDirectory().resolve(
          Path.of("bin", getAOL().toString()));
      String path = NarUtil.getEnv("PATH", "Path", "");
      if (path.length() > 0) path += File.pathSeparator;
      env.add("PATH=" + path + binPath);
    }

    // add CLASSPATH
    env.add("CLASSPATH=" + classpathElements.stream()
      .collect(Collectors.joining(File.pathSeparator)));

    return env;
  }

  /**
   * List the dependencies needed for tests executions and for executables
   * executions, those dependencies are used
   * to declare the paths of shared libraries for execution.
   */
  @Override
  protected ScopeFilter getArtifactScopeFilter() {
    return new ScopeFilter(org.apache.maven.artifact.Artifact.SCOPE_TEST, null );
  }

  @Override
  protected Path getUnpackDirectory() {
    return getTestUnpackDirectory() == null ? super.getUnpackDirectory() : getTestUnpackDirectory();
  }

  @Override
  public final void narExecute() throws MojoExecutionException, MojoFailureException {
    if (this.skipTests || this.dryRun) {
      getLog().info("Tests are skipped");
    } else {

      // run all tests
      for (final Test test : getTests()) {
        runTest(test);
      }

      for (final Library element : getLibraries()) {
        runExecutable(element);
      }
    }
  }

  private void runExecutable(final Library library) throws MojoExecutionException, MojoFailureException {
    if (library.getType().equals(Library.EXECUTABLE) && library.shouldRun()) {
      final MavenProject project = getMavenProject();
      // FIXME NAR-90, we could make sure we get the final name from layout
      final String extension = getOS().equals(OS.WINDOWS) ? ".exe" : "";
      final Path executable = getLayout().getBinDirectory(getTargetDirectory(),
          getMavenProject().getArtifactId(), getMavenProject().getVersion(),
          getAOL().toString()).resolve(project.getArtifactId() + extension);
      if (Files.notExists(executable)) {
        getLog().warn("Skipping non-existing executable " + executable);
        return;
      }
      getLog().info("Running executable " + executable);
      final List<String> args = library.getArgs();
      final int result = NarUtil.runCommand(executable.toString(), args,
          null, generateEnvironment(), getLog());
      if (result != 0) {
        throw new MojoFailureException("Test " + executable + " failed with exit code: " + result + " 0x"
            + Integer.toHexString(result));
      }
    }
  }

  private void runTest(final Test test) throws MojoExecutionException, MojoFailureException {
    // run if requested
    if (test.shouldRun()) {
      // NOTE should we use layout here ?
      final String name = test.getName() + (getOS().equals(OS.WINDOWS) ? ".exe" : "");
      Path path = getTestTargetDirectory().resolve(
          Path.of("bin", getAOL().toString(), name));
      if (Files.notExists(path)) {
        getLog().warn("Skipping non-existing test " + path);
        return;
      }

      final Path workingDir = getTestTargetDirectory().resolve("test-reports");
      try {
        Files.createDirectories(workingDir);
      } catch (IOException e) {
        throw new MojoExecutionException(e);
      }

      // Copy test resources
      try {
        int copied = 0;
        if (Files.exists(this.testResourceDirectory.toPath())) {
          copied += NarUtil.copyDirectoryStructure(this.testResourceDirectory.toPath(), workingDir, null,
              NarUtil.DEFAULT_EXCLUDES);
        }
        getLog().info("Copied " + copied + " test resources");
      } catch (final IOException e) {
        throw new MojoExecutionException("NAR: Could not copy test resources", e);
      }

      getLog().info("Running test " + name + " in " + workingDir);

      final List<String> args = test.getArgs();
      final int result = NarUtil.runCommand(path.toString(), args,
          workingDir, generateEnvironment(test.getEnvironmentVariables()), getLog());
      if (result != 0) {
        throw new MojoFailureException("Test " + name + " failed with exit code: " + result + " 0x"
            + Integer.toHexString(result));
      }
    }
  }
}
