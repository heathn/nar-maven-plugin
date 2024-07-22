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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Copies the CMake style source files to a target area, runs CMake to
 * generate the Makefile(s).
 *
 * @author Heath Nielson
 */
@Mojo(name = "nar-cmake-configure",
    defaultPhase = LifecyclePhase.PROCESS_SOURCES,
    requiresProject = true)
public class NarCMakeConfigureMojo extends AbstractCMakeMojo {

  /**
   * Arguments to pass to CMake.
   *
   */
  @Parameter(property = "nar.cmake.configure.args")
  private String cmakeConfigureArgs;


  @Override
  public final void narExecute() throws MojoExecutionException, MojoFailureException {

    if (!useCMake()) {
      return;
    }

    Path targetDir = getCMakeAOLSourceDirectory();
    if (Files.exists(getCMakeSourceDirectory())) {
      validateCMake();
      getLog().info("Copying CMake project");

      try {
        Files.createDirectories(targetDir);
        NarUtil.copyDirectoryStructure(getCMakeSourceDirectory(),
            targetDir, null, null);
      } catch (IOException e) {
        throw new MojoExecutionException("Failed to copy CMake project", e);
      }

      getLog().info("Running CMake");

      List<String> args = Stream.ofNullable(cmakeConfigureArgs)
          .map(s -> s.split(" "))
          .flatMap(Arrays::stream)
          .collect(Collectors.toList());

      args.add("-DCMAKE_INSTALL_PREFIX=" +
          getCMakeAOLTargetDirectory().toAbsolutePath().toString());
      args.add(targetDir.toAbsolutePath().toString());

      getLog().info("args: " + args);

      int result = NarUtil.runCommand(
          getCMakeExeFile().toAbsolutePath().toString(),
          args,
          targetDir,
          null,
          getLog());
      if (result != 0) {
        throw new MojoExecutionException("'" + getCMakeExeFile()
            + "' errorcode: " + result);
      }
    }

  }

  private void validateCMake() throws MojoExecutionException, MojoFailureException {
    int result = NarUtil.runCommand(getCMakeExeFile().toAbsolutePath().toString(),
        List.of("-version"), null, null, getLog());
    if (result != 0) {
      throw new MojoExecutionException("'" + getCMakeExeFile() + "'" +
          "does not appear to be installed.  Please install " +
          "package from http://cmake.org");
    }
  }

}
