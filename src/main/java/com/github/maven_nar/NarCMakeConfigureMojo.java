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
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.FileUtils;

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

  private static final String CMAKE = "cmake";

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

    File targetDir = getCMakeAOLSourceDirectory();
    if (getCMakeSourceDirectory().exists()) {
      validateCMake();
      getLog().info("Copying CMake project");

      try {
        FileUtils.mkdir(targetDir.getPath());
        NarUtil.copyDirectoryStructure(getCMakeSourceDirectory(),
            targetDir, null, null);
      } catch (IOException e) {
        throw new MojoExecutionException("Failed to copy CMake project", e);
      }

      getLog().info("Running CMake");

      String[] args;
      if (cmakeConfigureArgs != null) {
        String[] a = cmakeConfigureArgs.split(" ");
        args = new String[a.length + 2];
        for (int i = 0; i < a.length; i++) {
          args[i] = a[i];
        }
      } else {
        args = new String[2];
      }

      List<String> env = new ArrayList<String>();

      args[args.length-2] = "-DCMAKE_INSTALL_PREFIX=" +
          getCMakeAOLTargetDirectory().getAbsolutePath();
      args[args.length-1] = targetDir.getAbsolutePath();

      getLog().info("args: " + arraysToString(args));
      int result = NarUtil.runCommand(CMAKE, args, targetDir, 
          (String[])env.toArray(new String[env.size()]), getLog());
      if (result != 0) {
        throw new MojoExecutionException("'" + CMAKE + "' errorcode: " +
            result);
      }
    }

  }

  private void validateCMake() throws MojoExecutionException, MojoFailureException {
    int result = NarUtil.runCommand(CMAKE, new String[] { "-version" },
        null, null, getLog());
    if (result != 0) {
      throw new MojoExecutionException("'" + CMAKE + "'" +
          "does not appear to be installed.  Please install " +
          "package from http://www.cmake.org");
    }
  }

  // JDK 1.4 compatibility
  private static String arraysToString(Object[] a) {
    if (a == null)
      return "null";
    int iMax = a.length - 1;
    if (iMax == -1)
      return "[]";

    StringBuilder b = new StringBuilder();
    b.append('[');
    for (int i = 0;; i++) {
      b.append(String.valueOf(a[i]));
      if (i == iMax)
        return b.append(']').toString();
      b.append(", ");
    }
  }

}
