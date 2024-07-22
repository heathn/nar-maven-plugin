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
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.function.Failable;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.util.FileUtils;

/**
 * Initial layout which expands a nar file into:
 *
 * <pre>
 * nar/includue
 * nar/bin
 * nar/lib
 * </pre>
 *
 * this layout was abandoned because there is no one-to-one relation between the
 * nar file and its directory structure.
 * Therefore SNAPSHOTS could not be fully deleted when replaced.
 *
 * @author Mark Donszelmann (Mark.Donszelmann@gmail.com)
 */
public class NarLayout20 extends AbstractNarLayout {
  private final NarFileLayout fileLayout;

  public NarLayout20(final Log log) {
    super(log);
    this.fileLayout = new NarFileLayout10();
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.github.maven_nar.NarLayout#attachNars(java.io.File,
   * org.apache.maven.project.MavenProjectHelper,
   * org.apache.maven.project.MavenProject, com.github.maven_nar.NarInfo)
   */
  @Override
  public final void attachNars(final Path baseDir, final ArchiverManager archiverManager,
      final MavenProjectHelper projectHelper, final MavenProject project) throws MojoExecutionException {
    if (Files.exists(getIncludeDirectory(baseDir, project.getArtifactId(), project.getVersion()))) {
      attachNar(archiverManager, projectHelper, project, "noarch", baseDir, "include/**");
    }

    try {
      Files.list(baseDir.resolve("bin")).forEach(Failable.asConsumer(f ->
        attachNar(archiverManager, projectHelper, project, f + "-" + Library.EXECUTABLE, baseDir, "bin/" + f + "/**"))
      );

      final Path libDir = baseDir.resolve("lib");
      Files.list(libDir).flatMap(Failable.asFunction(
            dir -> Files.list(libDir.resolve(dir.getFileName()))))
        .forEach(Failable.asConsumer(lib -> {
            String aol = lib.getName(lib.getNameCount()-2).toString();
            String type = lib.getFileName().toString();
            attachNar(archiverManager, projectHelper, project, aol + "-" + type, baseDir, "lib/" + aol + "/" + type + "/**");
      }));
    } catch (IOException | RuntimeException e) {
      throw new MojoExecutionException(e);
    }

  }

  /*
   * (non-Javadoc)
   * 
   * @see com.github.maven_nar.NarLayout#getBinDirectory(java.io.File,
   * java.lang.String)
   */
  @Override
  public final Path
      getBinDirectory(final Path baseDir, final String artifactId, final String version, final String aol) {
    return baseDir.resolve(this.fileLayout.getBinDirectory(aol));
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.github.maven_nar.NarLayout#getIncludeDirectory(java.io.File)
   */
  @Override
  public final Path getIncludeDirectory(final Path baseDir, final String artifactId, final String version) {
    return baseDir.resolve(this.fileLayout.getIncludeDirectory());
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.github.maven_nar.NarLayout#getLibDir(java.io.File,
   * com.github.maven_nar.AOL, String type)
   */
  @Override
  public final Path getLibDirectory(final Path baseDir, final String artifactId, final String version,
      final String aol, final String type) throws MojoFailureException {
    if (type.equals(Library.EXECUTABLE)) {
      throw new MojoFailureException("INTERNAL ERROR, Replace call to getLibDirectory with getBinDirectory");
    }

    return baseDir.resolve(this.fileLayout.getLibDirectory(aol, type));
  }

  /*
* (non-Javadoc)
*
* @see com.github.maven_nar.NarLayout#getNarInfoDirectory(java.io.File,
 * java.lang.String
 * java.lang.String
 * java.lang.String
 * com.github.maven_nar.AOL,
 * java.lang.String)
*/
  @Override
  public final Path getNarInfoDirectory(final Path baseDir, final String groupId, final String artifactId, final String version,
                                         final String aol, final String type) throws MojoExecutionException {

  // This functionality is not supported for older layouts, return an empty file to be passive.
    getLog().debug("NarLayout20 doesn't support writing NarInfo to project classifier directories,use NarLayout21 instead.");
    return null;
  }

  @Override
  public Path getNarUnpackDirectory(final Path baseUnpackDirectory, final Path narFile) {
    return baseUnpackDirectory.resolve(FileUtils.basename(narFile.toString(), "."
        + NarConstants.NAR_EXTENSION));
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.github.maven_nar.NarLayout#getNoArchDirectory(java.io.File)
   */
  @Override
  public Path getNoArchDirectory(final Path baseDir, final String artifactId, final String version)
      throws MojoExecutionException, MojoFailureException {
    return baseDir;
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.github.maven_nar.NarLayout#attachNars(java.io.File,
   * org.apache.maven.project.MavenProjectHelper,
   * org.apache.maven.project.MavenProject, com.github.maven_nar.NarInfo)
   */
  @Override
  public final void prepareNarInfo(final Path baseDir, final MavenProject project, final NarInfo narInfo,
      final AbstractCompileMojo mojo) throws MojoExecutionException {
    if (Files.exists(getIncludeDirectory(baseDir, project.getArtifactId(), project.getVersion()))) {
      narInfo.setNar(null, "noarch", project.getGroupId() + ":" + project.getArtifactId() + ":" + NarConstants.NAR_TYPE
          + ":" + "noarch");
    }

    narInfo.setNar(null, Library.EXECUTABLE, project.getGroupId() + ":" + project.getArtifactId() + ":"
        + NarConstants.NAR_TYPE + ":" + "${aol}" + "-" + Library.EXECUTABLE);
    narInfo.setBinding(null, Library.EXECUTABLE);

    try {
      // TODO: chose not to apply new file naming for outfile in case of
      // backwards compatability, may need to reconsider
      Files.list(baseDir.resolve("bin")).forEach(file -> 
          narInfo.setBinding(new AOL(file.getFileName().toString()), Library.EXECUTABLE));

      
      final Path libDir = baseDir.resolve("lib");
      Files.list(libDir)
        .forEach(Failable.asConsumer(dir -> {
          List<String> bindingTypes = Files.list(libDir.resolve(dir))
            .map(f -> f.getFileName().toString())
            .peek(type -> {
              narInfo.setNar(null, type, project.getGroupId() + ":" + project.getArtifactId() + ":"
                  + NarConstants.NAR_TYPE + ":" + "${aol}" + "-" + type);
            }).collect(Collectors.toList());

          String bindingType = null;
          if (bindingTypes.contains(Library.SHARED)) {
            bindingType = Library.SHARED;
          } else {
            bindingType = bindingTypes.get(0);
          }

          final AOL aol = new AOL(dir.getFileName().toString());
          if (mojo.getLibsName() != null) {
            narInfo.setLibs(aol, mojo.getLibsName());
          }
          if (narInfo.getBinding(aol, null) == null) {
            narInfo.setBinding(aol, bindingType != null ? bindingType : Library.NONE);
          }
          if (narInfo.getBinding(null, null) == null) {
            narInfo.setBinding(null, bindingType != null ? bindingType : Library.NONE);
          }
      }));
    } catch (IOException | RuntimeException e) {
      throw new MojoExecutionException(e);
    }
  }

  @Override
  public void unpackNar(final Path unpackDir, final ArchiverManager archiverManager, final Path file, final String os,
      final String linkerName, final AOL defaultAOL, final boolean skipRanlib) throws MojoExecutionException, MojoFailureException {
    final Path flagFile = unpackDir.resolve(FileUtils.basename(file.toString(), "." + NarConstants.NAR_EXTENSION)
        + ".flag");

    boolean process = false;
    try {
      if (Files.notExists(unpackDir)) {
        Files.createDirectories(unpackDir);
        process = true;
      } else if (Files.notExists(flagFile)) {
        process = true;
      } else if (Files.getLastModifiedTime(file).compareTo(Files.getLastModifiedTime(flagFile)) > 0) {
        process = true;
      }
    } catch (IOException e) {
      throw new MojoExecutionException(e);
    }

    if (process) {
      try {
        unpackNarAndProcess(archiverManager, file, unpackDir, os, linkerName, defaultAOL, skipRanlib);
        Files.deleteIfExists(flagFile);
        Files.createFile(flagFile);
      } catch (final IOException e) {
        throw new MojoFailureException("Cannot create flag file: " + flagFile, e);
      }
    }
  }
}
