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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;


public abstract class AbstractCMakeMojo extends AbstractResourcesMojo {

  /**
   * Source directory for GNU style project
   *
   */
  @Parameter(defaultValue = "${basedir}/src/cmake", required = true)
  private File cmakeSourceDirectory;

  /**
   * Directory in which gnu sources are copied and "configured"
   *
   */
  @Parameter(defaultValue = "${project.build.directory}/nar/cmake", required = true)
  private File cmakeTargetDirectory;

  /**
   * Arguments to pass to GNU configure.
   *
   */
  @Parameter(property = "nar.cmake.args")
  private String cmakeArgs;

  /**
   * Returns true if we do want to use CMake
   *
   * @return
   */
  protected final boolean useCMake() {
    // TODO: Should check for existence of CMake program
    return true;
  }

  /**
   * @return
   * @throws MojoFailureException
   * @throws MojoExecutionException
   */
  private File getCMakeAOLDirectory() throws MojoFailureException, MojoExecutionException {
    return new File(cmakeTargetDirectory, getAOL().toString());
  }

  /**
   * @return
   * @throws MojoFailureException
   * @throws MojoExecutionException
   */
  protected final File getCMakeAOLSourceDirectory() throws MojoFailureException, MojoExecutionException {
    return new File(getCMakeAOLDirectory(), "src");
  }

  /**
   * @return
   * @throws MojoFailureException
   * @throws MojoExecutionException
   */
  protected final File getCMakeAOLTargetDirectory() throws MojoFailureException, MojoExecutionException {
    return new File(getCMakeAOLDirectory(), "build");
  }

  protected final File getCMakeSourceDirectory() {
    return cmakeSourceDirectory;
  }
}
