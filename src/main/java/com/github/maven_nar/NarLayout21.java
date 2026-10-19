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
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.function.Failable;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
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
    final Set<String> presetKeys = mojo == null || mojo.getTargets().isEmpty() ? Set.of()
        : readPresetKeys(project, narInfo);
    if (Files.exists(getNoArchDirectory(baseDir, project.getArtifactId(), project.getVersion()))) {
      narInfo.setNar(null, NarConstants.NAR_NO_ARCH, project.getGroupId() + ":" + project.getArtifactId() + ":"
          + NarConstants.NAR_TYPE + ":" + NarConstants.NAR_NO_ARCH);
    }

    final List<String> classifiers = classifiers(baseDir, project);

    if (!classifiers.isEmpty()) {

      final String presetDfltBinding = narInfo.getBinding(null, null);
      final Map<AOL, String> presetAOLBinding = new HashMap<>();
      for (final String classifier : classifiers) {
        final Classifier parsed = Classifier.parse(classifier);
        if (parsed == null) {
          continue;
        }
        final String type = parsed.type();
        final AOL aol = new AOL(parsed.aol());

        if (!presetAOLBinding.containsKey(aol)) {
           presetAOLBinding.put(aol, narInfo.getBinding(aol, null));
        }

        if (narInfo.getOutput(aol, null) == null) {
          narInfo.setOutput(aol, mojo.getOutput(!Library.EXECUTABLE.equals(type)));
        }

        // with targets declared, an <aol>.libs.names the project sets wins,
        // as it does on the other AOLs' builds
        if (mojo.getLibsName() != null
            && (mojo.getTargets().isEmpty() || !presetKeys.contains(NarInfo.aolKey(aol.toString(), NarInfo.LIBS_NAMES)))) {
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
        if (narInfo.getProperty(aol, NarInfo.narKey(type), (String) null) == null) {
          narInfo.setNar(null, type, NarInfo.genericNar(project.getGroupId() + ":"
              + project.getArtifactId() + ":" + NarConstants.NAR_TYPE, type));
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

    if (mojo != null && !mojo.getTargets().isEmpty()) {
      declareTargets(project, narInfo, mojo, presetKeys, classifiers);
    }
  }

  /**
   * The keys the project's own nar.properties sets, which declared targets
   * don't replace: read from the source file the last resource supplying it
   * copies, since target/classes may hold what a previous build generated.
   */
  private Set<String> readPresetKeys(final MavenProject project, final NarInfo narInfo)
      throws MojoExecutionException {
    Path source = null;
    for (final Resource resource : project.getBuild().getResources()) {
      final String path = ResourceSource.of(resource, narInfo.getNarInfoFileName());
      final Path file = path == null ? null
          : project.getBasedir().toPath().resolve(resource.getDirectory()).resolve(path);
      if (file != null && Files.isRegularFile(file)) {
        source = file;
      }
    }
    return source == null ? Set.of()
        : new NarInfo(project.getGroupId(), project.getArtifactId(), project.getVersion(), getLog(), source)
            .getInfo().stringPropertyNames();
  }

  // Describes every declared target, not just those built here
  private void declareTargets(final MavenProject project, final NarInfo narInfo, final AbstractCompileMojo mojo,
      final Set<String> presetKeys, final List<String> classifiers) throws MojoExecutionException {
    final Map<String, Set<String>> built = new HashMap<>();
    for (final String classifier : classifiers) {
      final Classifier parsed = Classifier.parse(classifier);
      if (parsed != null) {
        built.computeIfAbsent(parsed.aol(), aol -> new LinkedHashSet<>()).add(parsed.type());
      }
    }
    final Set<String> builtTypes = new HashSet<>();
    built.values().forEach(builtTypes::addAll);
    Target.declare(mojo.getTargets(), narInfo, presetKeys, builtTypes, project.getGroupId() + ":"
        + project.getArtifactId() + ":" + NarConstants.NAR_TYPE, mojo.getOutput(true), mojo.getOutput(false),
        mojo.getLibsName());
    Target.checkBuilt(mojo.getTargets(), built, getLog());
  }

  @Override
  public void writeDescriptors(final Path baseDir, final MavenProject project, final NarInfo narInfo)
      throws MojoExecutionException, MojoFailureException {
    final NarProperties narProperties = NarProperties.getInstance(project);
    for (final String classifier : classifiers(baseDir, project)) {
      final Classifier parsed = Classifier.parse(classifier);
      if (parsed == null) {
        continue;
      }
      final AOL aol = new AOL(parsed.aol());
      final String type = parsed.type();
      // only shared and static libraries are described by their files
      final Path libDir = Library.SHARED.equals(type) || Library.STATIC.equals(type)
          ? getLibDirectory(baseDir, project.getArtifactId(), project.getVersion(), parsed.aol(), type) : null;
      final String key = aol.getKey();
      final Path file = getAolDirectory(baseDir, project.getArtifactId(), project.getVersion(), parsed.aol(), type)
          .resolve(narInfo.getNarInfoFileName()).resolveSibling(NarInfo.getDescriptorFileName(parsed.aol(), type));
      try {
        final Properties descriptor = NarDescriptor.describe(narInfo, aol, type, libDir,
            NarDescriptor.linkPatterns(parsed.aol(), type, narProperties.getProperty(key + ".lib.prefix"),
                narProperties.getProperty(key + "." + type + ".extension")), getLog());
        getLog().debug("Writing " + file + ": " + descriptor);
        NarUtil.storeProperties(file, descriptor);
      } catch (final IOException e) {
        throw new MojoExecutionException("Cannot write the descriptor " + file, e);
      }
    }
  }

  /**
   * The classifiers of this project's AOL-specific directories in baseDir,
   * named &lt;artifactId&gt;-&lt;version&gt;-&lt;classifier&gt;, other than
   * noarch.
   */
  private static List<String> classifiers(final Path baseDir, final MavenProject project)
      throws MojoExecutionException {
    final String prefix = project.getArtifactId() + "-" + project.getVersion() + "-";
    try (Stream<Path> entries = Files.list(baseDir)) {
      return entries.filter(Files::isDirectory).map(entry -> entry.getFileName().toString())
          .filter(name -> name.startsWith(prefix) && name.length() > prefix.length())
          .map(name -> name.substring(prefix.length()))
          .filter(classifier -> !classifier.equals(NarConstants.NAR_NO_ARCH))
          .collect(Collectors.toList());
    } catch (final IOException e) {
      throw new MojoExecutionException(e);
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
