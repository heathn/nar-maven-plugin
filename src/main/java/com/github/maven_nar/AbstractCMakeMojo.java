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
import java.util.Arrays;
import java.util.function.Predicate;

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

  private Path cmakeExe = null;

  /**
   * Returns true if we do want to use CMake
   *
   * @return
   */
  protected final boolean useCMake() {
    if (cmakeExe == null) {
      if (NarUtil.isWindows()) {
        try {
          cmakeExe = Path.of(NarUtil.registryGet32StringValue(
            com.sun.jna.platform.win32.WinReg.HKEY_LOCAL_MACHINE,
            "SOFTWARE\\Kitware\\CMake", "InstallDir"), "bin", "cmake.exe");
        } catch (com.sun.jna.platform.win32.Win32Exception e) {
          // Registry key wasn't found.
          cmakeExe = Path.of("cmake.exe");
        }
      } else {
        cmakeExe = Arrays.stream(System.getenv("PATH").split(File.pathSeparator))
          .filter(Predicate.not(String::isEmpty))
          .map(Path::of)
          .filter(Files::exists)
          .flatMap(p -> {
            try {
              return Files.list(p);
            } catch (IOException e) {
              getLog().error("Unable to get list of files", e);
              throw new RuntimeException(e);
            }})
          .filter(p -> p.getFileName().toString().startsWith("cmake"))
          .findFirst()
          .orElse(null);
      }
    }
    return cmakeExe != null ? Files.exists(cmakeExe) : false;
  }

  /**
   * @return
   * @throws MojoFailureException
   * @throws MojoExecutionException
   */
  private Path getCMakeAOLDirectory() throws MojoFailureException, MojoExecutionException {
    return cmakeTargetDirectory.toPath().resolve(getAOL().toString());
  }

  /**
   * @return
   * @throws MojoFailureException
   * @throws MojoExecutionException
   */
  protected final Path getCMakeAOLSourceDirectory() throws MojoFailureException, MojoExecutionException {
    return getCMakeAOLDirectory().resolve("src");
  }

  /**
   * @return
   * @throws MojoFailureException
   * @throws MojoExecutionException
   */
  protected final Path getCMakeAOLTargetDirectory() throws MojoFailureException, MojoExecutionException {
    return getCMakeAOLDirectory().resolve("build");
  }

  protected final Path getCMakeSourceDirectory() {
    return cmakeSourceDirectory.toPath();
  }

  protected final Path getCMakeExeFile() {
    return cmakeExe;
  }

}
