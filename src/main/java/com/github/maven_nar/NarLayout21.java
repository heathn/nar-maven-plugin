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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.surefire.shared.lang3.function.Failable;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.util.FileUtils;

/**
 * Layout which expands a nar file into:
 *
 * <pre>
 * nar/noarch/include
 * nar/aol/<aol>-<type>/bin
 * nar/aol/<aol>-<type>/lib
 * </pre>
 *
 * This loayout has a one-to-one relation with the aol-type version of the nar.
 *
 * @author Mark Donszelmann (Mark.Donszelmann@gmail.com)
 */
public class NarLayout21 extends AbstractNarLayout {
  private final NarFileLayout fileLayout;

  public NarLayout21(final Log log) {
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
    if (Files.exists(getNoArchDirectory(baseDir, project.getArtifactId(), project.getVersion()))) {
      attachNar(archiverManager, projectHelper, project, NarConstants.NAR_NO_ARCH,
          getNoArchDirectory(baseDir, project.getArtifactId(), project.getVersion()), "*/**");
    }

    // list all directories in basedir, scan them for classifiers
    try {
      final String artifactIdVersion = project.getArtifactId() + "-" + project.getVersion();
      Files.list(baseDir)
        // Include only entries belonging to this project
        .filter(dir -> dir.getFileName().toString().startsWith(artifactIdVersion))
        // Skip noarch
        .filter(dir -> !dir.getFileName().toString().endsWith(NarConstants.NAR_NO_ARCH))
        .forEach(Failable.asConsumer(dir -> {
          String classifier = dir.getFileName().toString().substring(artifactIdVersion.length() + 1);
          Path path = baseDir.resolve(dir);
          attachNar(archiverManager, projectHelper, project, classifier, path, "*/**");
        }));
    } catch (IOException | RuntimeException e) {
      throw new MojoExecutionException(e);
    }
  }

  private Path getAolDirectory(final Path baseDir, final String artifactId, final String version, final String aol,
      final String type) {
    return baseDir.resolve(artifactId + "-" + version + "-" + aol + "-" + type);
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.github.maven_nar.NarLayout#getLibDir(java.io.File,
   * com.github.maven_nar.AOL,
   * java.lang.String)
   */
  @Override
  public final Path
      getBinDirectory(final Path baseDir, final String artifactId, final String version, final String aol) {
    Path dir = getAolDirectory(baseDir, artifactId, version, aol, Library.EXECUTABLE);
    return dir.resolve(this.fileLayout.getBinDirectory(aol));
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.github.maven_nar.NarLayout#getIncludeDirectory(java.io.File)
   */
  @Override
  public final Path getIncludeDirectory(final Path baseDir, final String artifactId, final String version) {
    return getNoArchDirectory(baseDir, artifactId, version).resolve(this.fileLayout.getIncludeDirectory());
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.github.maven_nar.NarLayout#getLibDir(java.io.File,
   * com.github.maven_nar.AOL,
   * java.lang.String)
   */
  @Override
  public final Path getLibDirectory(final Path baseDir, final String artifactId, final String version,
      final String aol, final String type) throws MojoExecutionException {
    if (type.equals(Library.EXECUTABLE)) {
      throw new MojoExecutionException("NAR: for type EXECUTABLE call getBinDirectory instead of getLibDirectory");
    }

    Path dir = getAolDirectory(baseDir, artifactId, version, aol, type);
    return dir.resolve(this.fileLayout.getLibDirectory(aol, type));
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

    Path aolDirectory = getAolDirectory(baseDir, artifactId, version, aol, type);
    return aolDirectory.resolve(this.fileLayout.getNarInfoFile(groupId, artifactId, type));
  }

  @Override
  public Path getNarUnpackDirectory(final Path baseUnpackDirectory, final Path narFile) {
    return baseUnpackDirectory.resolve(FileUtils.basename(narFile.toString(), "."
        + NarConstants.NAR_EXTENSION));
  }

  @Override
  public Path getNoArchDirectory(final Path baseDir, final String artifactId, final String version) {
    return baseDir.resolve(artifactId + "-" + version + "-" + NarConstants.NAR_NO_ARCH);
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
      final AbstractCompileMojo mojo) throws MojoExecutionException, MojoFailureException {
    if (Files.exists(getNoArchDirectory(baseDir, project.getArtifactId(), project.getVersion()))) {
      narInfo.setNar(null, NarConstants.NAR_NO_ARCH, project.getGroupId() + ":" + project.getArtifactId() + ":"
          + NarConstants.NAR_TYPE + ":" + NarConstants.NAR_NO_ARCH);
    }

    final String artifactIdVersion = project.getArtifactId() + "-" + project.getVersion();
    final List<String> classifiers;
    try {
      // list all directories in basedir, scan them for classifiers
      classifiers = Files.list(baseDir)
        // skip entries not belonging to this project
        .filter(dir -> dir.getFileName().toString().startsWith(artifactIdVersion))
        .map(dir -> dir.getFileName().toString().substring(artifactIdVersion.length() + 1))
        // skip noarch here
        .filter(classifier -> !classifier.equals(NarConstants.NAR_NO_ARCH))
        .collect(Collectors.toList());
    } catch (IOException e) {
      throw new MojoExecutionException(e);
    }

    if (!classifiers.isEmpty()) {

      final String presetDfltBinding = narInfo.getBinding(null, null);
      final Map<AOL, String> presetAOLBinding = new HashMap<>();
      for (final String classifier : classifiers) {
        final int lastDash = classifier.lastIndexOf('-');
        final String type = classifier.substring(lastDash + 1);
        final AOL aol = new AOL(classifier.substring(0, lastDash));

        if (!presetAOLBinding.containsKey(aol)) {
           presetAOLBinding.put(aol, narInfo.getBinding(aol, null));
        }

        if (narInfo.getOutput(aol, null) == null) {
          narInfo.setOutput(aol, mojo.getOutput(!Library.EXECUTABLE.equals(type)));
        }

        if (mojo.getLibsName() != null) {
          narInfo.setLibs(aol, mojo.getLibsName());
        }
        
        narInfo.setIncludesType(null, mojo.getIncludesType());

        // We prefer shared to jni/executable/static/none,
        if (type.equals(Library.SHARED)) // overwrite whatever we had
        {
          // Don't override the default/aol binding if it was preset in
          // nar.properties
          if (presetAOLBinding.get(aol) == null) {
            narInfo.setBinding(aol, type);
          }
          if (presetDfltBinding == null) {
            narInfo.setBinding(null, type);
          }
        } else {
          // if the binding is already set, then don't write it for
          // jni/executable/none.
		  String current = narInfo.getBinding(aol, null);
          if ( current == null) {
            narInfo.setBinding(aol, type);
          } else if (!Library.SHARED.equals(current)
			  && type.equals(Library.STATIC)) {
            //static lib is preferred over other remaining types; see #231

            // Don't override the default/aol binding if it was preset in
            // nar.properties
            if (presetAOLBinding.get(aol) == null) {
              narInfo.setBinding(aol, type);
            }
            if (presetDfltBinding == null) {
              narInfo.setBinding(null, type);
            }
          }

          if (narInfo.getBinding(null, null) == null) {
            narInfo.setBinding(null, type);
          }
        }

        // If the more-specific platform key has been set, skip setting the
        // generic key.
        if (narInfo.getProperty(aol, NarConstants.NAR_TYPE + "." + type,
            (String)null) == null) {
          narInfo.setNar(null, type, project.getGroupId() + ":" +
              project.getArtifactId() + ":" + NarConstants.NAR_TYPE + ":" +
              "${aol}" + "-" + type);
        }
        
        // set the system includes
        Set<String> flattenedSysLibs = new LinkedHashSet<>();
        String sysLibSet = mojo.getLinker().getSysLibSet();
        List<SysLib> sysLibList = mojo.getLinker().getSysLibs();
        if (sysLibList == null) sysLibList = new ArrayList<>();
        
        Set<SysLib> dependencySysLibSet = new HashSet<>();
        // add syslibs from all attached artifacts
        for (NarArtifact artifact : mojo.getNarArtifacts()) {
          dependencySysLibSet.addAll(mojo.getDependecySysLib(artifact.getNarInfo()));
        }
        sysLibList.addAll(dependencySysLibSet);
        
        if (sysLibSet != null) {
          String[] split = sysLibSet.split(",");
          
          for (String s : split) {
            flattenedSysLibs.add(s.trim());
          }
        }
        
        if (sysLibList != null && !sysLibList.isEmpty()) {
          for (SysLib s : sysLibList) {
            flattenedSysLibs.add(s.getName() + ":" + s.getType());
          }
        }
        
        if (!flattenedSysLibs.isEmpty()) {
          Iterator<String> iter = flattenedSysLibs.iterator();
          
          StringBuilder b = new StringBuilder();
          while (iter.hasNext()) {
            b.append(iter.next());
            if (iter.hasNext()) b.append(", ");
          }
          
          getLog().debug("Added syslib to narInfo: " + b.toString());
          narInfo.setSysLibs(aol, b.toString());
        }

      }

      // setting this first stops the per type config because getOutput check
      // for aol defaults to this generic one...
      if (mojo != null && narInfo.getOutput(null, null) == null) {
        narInfo.setOutput(null, mojo.getOutput(true));
      }
    }
  }

  @Override
  public void unpackNar(final Path unpackDirectory, final ArchiverManager archiverManager, final Path file,
      final String os, final String linkerName, final AOL defaultAOL, final boolean skipRanlib)
      throws MojoExecutionException, MojoFailureException {

    final Path dir = getNarUnpackDirectory(unpackDirectory, file);

    boolean process = false;

    try {
      if (Files.notExists(unpackDirectory)) {
        Files.createDirectories(unpackDirectory);
        process = true;
      } else if (Files.notExists(dir)) {
        process = true;
      } else if (Files.getLastModifiedTime(file).compareTo(Files.getLastModifiedTime(dir)) > 0) {
        NarUtil.deleteDirectory(dir);
        process = true;
      } else if (Files.list(dir).count() == 0) {
        // a previously failed cleanup which failed deleting all may have left a
        // state where dir modified > file modified but not unpacked.
        process = true;
      }
    } catch (IOException e) {
      throw new MojoExecutionException(e);
    }

    if (process) {
      unpackNarAndProcess(archiverManager, file, dir, os, linkerName, defaultAOL, skipRanlib);
    }
  }

}
