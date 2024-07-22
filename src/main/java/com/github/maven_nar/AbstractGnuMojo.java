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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * Abstract GNU Mojo keeps configuration
 *
 * @author Mark Donszelmann
 */
public abstract class AbstractGnuMojo extends AbstractResourcesMojo {
  /**
   * Use GNU goals on Windows
   */
  @Parameter(defaultValue = "nar.gnu.useonwindows", required = true)
  private boolean gnuUseOnWindows;

  /**
   * Source directory for GNU style project
   */
  @Parameter(defaultValue = "${basedir}/src/gnu")
  private File gnuSourceDirectory;

  /**
   * Directory in which gnu sources are copied and "configured"
   */
  @Parameter(defaultValue = "${project.build.directory}/nar/gnu")
  private File gnuTargetDirectory;
  
  @Component
  private RepositorySystem repoSystem;

  @Parameter(defaultValue="${repositorySystemSession}", readonly = true)
  private RepositorySystemSession repoSession;

  @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true)
  private List<RemoteRepository> projectRepos;


  /**
   * @return
   * @throws MojoFailureException
   * @throws MojoExecutionException
   */
  private Path getGnuAOLDirectory() throws MojoFailureException, MojoExecutionException {
    return this.gnuTargetDirectory.toPath().resolve(getAOL().toString());
  }

  /**
   * @return
   * @throws MojoFailureException
   * @throws MojoExecutionException
   */
  protected final Path getGnuAOLSourceDirectory() throws MojoFailureException, MojoExecutionException {
    return getGnuAOLDirectory().resolve("src");
  }

  /**
   * @return
   * @throws MojoFailureException
   * @throws MojoExecutionException
   */
  protected final Path getGnuAOLTargetDirectory() throws MojoFailureException, MojoExecutionException {
    return getGnuAOLDirectory().resolve("target");
  }

  protected final Path getGnuSourceDirectory() {
    return this.gnuSourceDirectory.toPath();
  }

  /**
   * Returns true if we do not want to use GNU on Windows
   * 
   * @return
   */
  protected final boolean useGnu() {
    return this.gnuUseOnWindows || !NarUtil.isWindows();
  }

  protected final NarManager getNarManager() throws MojoFailureException, MojoExecutionException {
    return new NarManager(getLog(), repoSystem, repoSession, projectRepos, getMavenProject(), getArchitecture(), getOS(), getLinker());
  }

  protected List<String> getIncludeDirs() throws MojoExecutionException, MojoFailureException {
    // add dependency include paths (copied from NarCompileMojo.java)
    List<String> includeDirs = new ArrayList<String>();
    for (Object dep : getNarManager().getNarDependencies("compile")) {
      NarArtifact narDependency = (NarArtifact) dep;
      // FIXME, handle multiple includes from one NAR
      String binding = narDependency.getNarInfo().getBinding(getAOL(), Library.STATIC);
      getLog().debug("Looking for " + narDependency + " found binding " + binding);
      if (!binding.equals(Library.JNI)) {
        Path unpackDirectory = getUnpackDirectory();
        Path include = getLayout().getIncludeDirectory(unpackDirectory,
            narDependency.getArtifactId(), narDependency.getVersion());
        getLog().debug("Looking for include directory: " + include);
        if (Files.exists(include)) {
          includeDirs.add(include.toString());
        } else {
          throw new MojoExecutionException(
              "NAR: unable to locate include path: " + include);
        }
      }
    }
    return includeDirs;
  }

  protected List<Path> getLibDirs() throws MojoExecutionException, MojoFailureException {
    // add dependency include paths (copied from NarCompileMojo.java)
    Path unpackDirectory = getUnpackDirectory();
    List<Path> libDirs = new ArrayList<>();
    for (NarArtifact narDependency : getNarManager().getNarDependencies("compile")) {
      // FIXME, handle multiple includes from one NAR
      String binding = narDependency.getNarInfo().getBinding(getAOL(), Library.STATIC);
      getLog().debug("Looking for " + narDependency + " found binding " + binding);
      if (!binding.equals(Library.JNI)) {
        if (narDependency.getNarInfo().getBinding(getAOL(), Library.SHARED).equals(Library.EXECUTABLE))
          continue;

        Path libDir = getLayout().getLibDirectory(unpackDirectory,
            narDependency.getArtifactId(), narDependency.getVersion(),
            getAOL().toString(),
            narDependency.getNarInfo().getBinding(getAOL(), Library.SHARED));
        if (Files.exists(libDir)) {
          libDirs.add(libDir);
        } else {
          throw new MojoExecutionException(
              "NAR: unable to locate lib path: " + libDir);
        }
      }
    }
    return libDirs;
  }

  protected List<String> getDependentLibs() throws MojoExecutionException, MojoFailureException {
    // add dependency include paths (copied from NarCompileMojo.java)
    List<String> libs = new ArrayList<String>();
    for (Object dep : getNarManager().getNarDependencies("compile")) {
      NarArtifact narDependency = (NarArtifact) dep;
      // FIXME, handle multiple includes from one NAR
      String binding = narDependency.getNarInfo().getBinding(getAOL(), Library.STATIC);
      getLog().debug("Looking for " + narDependency + " found binding " + binding);
      if (!binding.equals(Library.JNI)) {
        if (narDependency.getNarInfo().getBinding(getAOL(), Library.SHARED).equals(Library.EXECUTABLE))
          continue;

        Path unpackDirectory = getUnpackDirectory();
        Path libDir = getLayout().getLibDirectory(unpackDirectory,
            narDependency.getArtifactId(), narDependency.getVersion(),
            getAOL().toString(),
            narDependency.getNarInfo().getBinding(getAOL(), Library.SHARED));
        if (Files.exists(libDir)) {
          List<String> libNames = Arrays.asList(narDependency.getNarInfo().getLibs(getAOL()).split(" "));
          for (int idx = 0; idx < libNames.size(); idx++) {
            String linkName = getLinkName(libDir, (String)libNames.get(idx));
            if (linkName != null) {
              libs.add(linkName);
            }
          }
        } else {
          throw new MojoExecutionException(
              "NAR: unable to locate lib path: " + libDir);
        }
      }
    }
    return libs;
  }

  private String getLinkName(Path libDir, String libName) throws MojoFailureException, MojoExecutionException {
    String linkerName = libName;
    if (getAOL().getOS() == OS.WINDOWS) {
      Path f = libDir.resolve(linkerName + ".lib");
      if (Files.exists(f)) {
        linkerName += ".lib";
      } else {
        linkerName = null;
      }
    }
    return linkerName;
  }
}
