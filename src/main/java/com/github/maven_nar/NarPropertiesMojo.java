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

import java.nio.file.Path;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.shared.artifact.filter.collection.ScopeFilter;

/**
 * Sets the following properties for each NAR dependency:
 * <ul>
 *   <li>${groupId:artifactId:include} - path to the noarch dependency.</li>
 *   <li>${groupId:artifactId:binding} - path to the platform-dependent
 *       dependency.  Currently binding can be either "static", "shared", or
 *       "executable".  If the platform-dependent dependency includes multiple
 *       files, only the first defined in the nar.properties file is
 *       returned.</li>
 *   <li>${groupId:artifactId:binding:dir} - path to the folder of the
 *       platform-dependent dependency.</li>
 * </ul>
 *
 * @author Heath Nielson
 *
 */
@Mojo(name = "properties", defaultPhase = LifecyclePhase.INITIALIZE,
    requiresProject = true,
    requiresDependencyResolution = ResolutionScope.COMPILE)
public class NarPropertiesMojo extends AbstractDependencyMojo {

  @Override
  public void narExecute() throws MojoFailureException, MojoExecutionException {
    List<NarArtifact> dependencies = getNarManager().getNarDependencies("compile");
    for (NarArtifact dependency : dependencies) {

      // Set the include property
      Path includePath = getIncludePath(dependency);
      String property = dependency.getGroupId() + ":"
          + dependency.getArtifactId() + ":include";
      getLog().debug(
          "Setting " + property + " to: " + includePath.toString());
      getMavenProject().getProperties().setProperty(property,
          includePath.toString());

      // Set the lib property
      String binding = dependency.getNarInfo().getBinding(getAOL(),
          Library.SHARED);
      property = dependency.getGroupId() + ":" + dependency.getArtifactId()
          + ":" + binding;
      // TODO: How to handle multiple libraries?
      String filename = null;
      String libName = dependency.getNarInfo().getLibs(getAOL()).split(" ")[0];
      String prefix = NarProperties.getInstance(getMavenProject()).getProperty(getAOL().getKey() + ".lib.prefix");
      String ext = NarProperties.getInstance(getMavenProject()).getProperty(getAOL().getKey() + "." + binding + ".extension");

      // Some of the values for the extension property contain a wildcard to
      // match versioned shared objects e.g. libfile.so.2.3.  In this
      // case assume the link name is what is wanted e.g. libfile.so and
      // remove the wildcard.
      ext = ext.replaceAll("\\*", "");

      // On Windows return the import library instead of the library 
      if (getAOL().getOS() == OS.WINDOWS) {
        filename = libName + ".lib";
      } else {
        filename = prefix + libName + "." + ext;
      }
      Path libPath = getLibraryPath(dependency).resolve(filename);
      getLog().debug("Setting " + property + " to: " + libPath.toString());
      getMavenProject().getProperties().setProperty(property,
          libPath.toString());
      getMavenProject().getProperties().setProperty(property + ":dir",
          libPath.getParent().toString());
    }

  }

  /**
   * List the dependencies needed for compilation.
   */
  @Override
  protected ScopeFilter getArtifactScopeFilter() {
    return new ScopeFilter(org.apache.maven.artifact.Artifact.SCOPE_COMPILE, null);
  }

}
