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
 * Runs make on the CMake style generated Makefile
 *
 * @author Heath Nielson
 */
@Mojo(name = "nar-cmake", defaultPhase = LifecyclePhase.COMPILE, requiresProject = true)
public class NarCMakeMojo extends AbstractCMakeMojo {

  @Parameter(defaultValue = "make")
  private String make;

  /**
   * Space delimited list of arguments to pass to make
   *
   */
  @Parameter
  private String cmakeMakeArgs;

  /**
   * Name of Makefile
   *
   */
  @Parameter(property = "nar.cmake.project.file", defaultValue = "ALL_BUILD.vcxproj")
  private String cmakeProjectFile;

  /**
   * Comma delimited list of environment variables to setup before running make
   *
   */
  @Parameter
  private String cmakeMakeEnv;

  /**
   * Boolean to control if we should run make install after the make
   *
   */
  @Parameter(property = "nar.cmake.make.install.skip", defaultValue = "false")
  private boolean cmakeMakeInstallSkip;


  @Override
  public final void narExecute() throws MojoExecutionException, MojoFailureException {
    if (!useCMake()) {
      return;
    }

    Path srcDir = getCMakeAOLSourceDirectory();
    if (Files.exists(srcDir)) {
      String cmd;
      List<String> args = null;

      if (getAOL().getOS().equals(OS.WINDOWS)) {
        getLog().info("Running MSBuild");
        cmd = getMsvc().getMSBuild().toAbsolutePath().toString();
        args = Stream.concat(Stream.ofNullable(cmakeMakeArgs), Stream.of(cmakeProjectFile))
            .map(s -> s.split(" "))
            .flatMap(Arrays::stream)
            .collect(Collectors.toList());
      } else {
        getLog().info("Running GNU make");
        cmd = make;
        args = Stream.ofNullable(cmakeMakeArgs)
            .map(s -> s.split(" "))
            .flatMap(Arrays::stream)
            .collect(Collectors.toList());
      }
      int result = NarUtil.runCommand(cmd, args, srcDir, null, getLog());
      if (result != 0) {
        throw new MojoExecutionException("'make' errorcode: " + result);
      }

      if (!cmakeMakeInstallSkip) {
        getLog().info("Running make install");
        if (getAOL().getOS().equals(OS.WINDOWS)) {
          args = Stream.concat(Stream.ofNullable(cmakeMakeArgs),
            Stream.of("INSTALL.vcxproj"))
              .map(s -> s.split(" "))
              .flatMap(Arrays::stream)
              .collect(Collectors.toList());
        } else {
          args = Stream.concat(Stream.ofNullable(cmakeMakeArgs),
            Stream.of("install"))
              .map(s -> s.split(" "))
              .flatMap(Arrays::stream)
              .collect(Collectors.toList());
        }
        result = NarUtil.runCommand(cmd, args, srcDir, null, getLog());
        if ( result != 0 ) {
          throw new MojoExecutionException("'make install' errorcode: " + result);
        }
      }
    }
  }
}
