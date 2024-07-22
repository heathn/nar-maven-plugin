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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Move the GNU style output in the correct directories for nar-package
 *
 * @author Heath Nielson
 */
@Mojo(name = "nar-cmake-process", defaultPhase = LifecyclePhase.PROCESS_CLASSES, requiresProject = true)
public class NarCMakeProcess extends AbstractCMakeMojo {

  @Override
  public final void narExecute() throws MojoExecutionException, MojoFailureException {
    Path srcDir = getCMakeAOLTargetDirectory();
    if (Files.exists(srcDir)) {
      getLog().info("Running CMake process");

      // CMake appears to install .dlls in the bin folder.  Copy them
      // into the lib folder so that they are included in the
      // shared NAR artifact copy.
      if (getAOL().getOS() == OS.WINDOWS) {
        Path binDir = srcDir.resolve(getResourceBinDir());
        Path libDir = srcDir.resolve(getResourceLibDir());
        if (Files.exists(binDir) && Files.exists(libDir)) {
          try {
            NarUtil.copyDirectoryStructure(binDir, libDir,
                "**/*.dll", NarUtil.DEFAULT_EXCLUDES);
          } catch (IOException e) {
            throw new MojoExecutionException(
                "NAR: Could not copy resources for " + getAOL(), e);
          }
        }
      }

      copyResources(srcDir, getAOL().toString());
    }
  }
}
