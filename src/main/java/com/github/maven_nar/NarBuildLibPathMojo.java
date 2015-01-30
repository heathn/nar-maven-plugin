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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.shared.artifact.filter.collection.ScopeFilter;

/**
 * Builds a path to dependent libraries for use in java.library.path.
 *
 * @author Heath Nielson
 *
 * @TODO Merge with NarVcpropsMojo
 */
@Mojo(name = "nar-build-libpath",
    defaultPhase = LifecyclePhase.GENERATE_RESOURCES,
    requiresProject = true,
    requiresDependencyResolution = ResolutionScope.COMPILE)
public class NarBuildLibPathMojo extends AbstractDependencyMojo {

  @Override
  public final void narExecute() throws MojoExecutionException, MojoFailureException {
    List<File> libs = getLibs();

    if (libs.size() == 0) {
      getLog().info("No dependencies found.");
    }

    StringBuffer sb = new StringBuffer();
    for (Iterator<File> i = libs.iterator(); i.hasNext();) {
      File lib = i.next();
      sb.append(lib);
      if (i.hasNext()) {
        sb.append(File.pathSeparator);
      }
    }

    getLog().debug("Setting nar.library.path to: " + sb.toString());
    getMavenProject().getProperties().setProperty("nar.library.path", sb.toString());

  }

  private List<File> getLibs() throws MojoExecutionException, MojoFailureException {
    List<File> libs = new ArrayList<File>();

    List<NarArtifact> dependencies = getNarManager().getNarDependencies("compile");
    for (NarArtifact dependency : dependencies) {
      String binding = dependency.getNarInfo().getBinding(getAOL(), Library.SHARED);
      // Only interested in shared libraries
      if (binding.equals(Library.SHARED)) {
        File depLibPathEntry = getLibraryPath(dependency);
        if (depLibPathEntry.exists()) {
          libs.add(depLibPathEntry);
        }
      }
    }
    return libs;
  }

  /**
   * List the dependencies needed for compilation.
   */
  @Override
  protected ScopeFilter getArtifactScopeFilter() {
    return new ScopeFilter(Artifact.SCOPE_COMPILE, null);
  }
}
