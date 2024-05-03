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
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.model.FileSet;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.archiver.manager.NoSuchArchiverException;
import org.codehaus.plexus.util.FileUtils;

/**
 * Keeps track of resources
 *
 * @author Mark Donszelmann
 */
public abstract class AbstractResourcesMojo extends AbstractNarMojo {
  /**
   * Binary directory
   */
  @Parameter(defaultValue = "bin", required = true)
  protected String resourceBinDir;

  /**
   * Include directory
   */
  @Parameter
  private FileSet resourceIncludes;

  /**
   * Library directory
   */
  @Parameter(defaultValue = "lib", required = true)
  protected String resourceLibDir;

  /**
   * To look up Archiver/UnArchiver implementations
   */
  @Component(role = org.codehaus.plexus.archiver.manager.ArchiverManager.class)
  private ArchiverManager archiverManager;

  private static final FileSet defaultIncludes = new FileSet();

  {
    defaultIncludes.setDirectory("include");
    defaultIncludes.addInclude("**");
    defaultIncludes.addExclude(NarUtil.DEFAULT_EXCLUDES);
  }

  protected final int copyBinaries(final File srcDir, final String aol)
      throws IOException, MojoExecutionException, MojoFailureException {
    int copied = 0;

    // copy binaries
    final File binDir = new File(srcDir, this.resourceBinDir);
    if (binDir.exists()) {
      final File binDstDir = getLayout().getBinDirectory(getTargetDirectory(), getMavenProject().getArtifactId(),
          getMavenProject().getVersion(), aol);
      getLog().debug("Copying binaries from " + binDir + " to " + binDstDir);
      copied += NarUtil.copyDirectoryStructure(binDir, binDstDir, null, NarUtil.DEFAULT_EXCLUDES);
    }

    return copied;
  }

  protected final int copyIncludes(final File srcDir) throws IOException, MojoExecutionException, MojoFailureException {
    if (resourceIncludes == null) {
      resourceIncludes = defaultIncludes;
    }
    int copied = 0;
    String sourcePath = srcDir.getAbsolutePath();
    final File includeDstDir = getLayout().getIncludeDirectory(
        getTargetDirectory(), getMavenProject().getArtifactId(),
        getMavenProject().getVersion());

    // copy includes
    if (resourceIncludes.getDirectory() != null) {
      sourcePath += File.separator + resourceIncludes.getDirectory();
    }
    resourceIncludes.setDirectory(sourcePath);
    final File includeDir = new File(sourcePath);
    if (includeDir.exists()) {
      final List<File> files = FileSetTransformer.toFileList(resourceIncludes);
      for (File file : files) {
        String dest = file.getAbsolutePath();
        dest = dest.substring(sourcePath.length() + 1);
        File destination = new File(includeDstDir, dest);
        getLog().debug("Copying " + file + " to " + destination);
        FileUtils.copyFile(file, destination);
        copied++;
      }
    }

    return copied;
  }

  protected final int copyLibraries(final File srcDir, final String aol)
      throws MojoFailureException, IOException, MojoExecutionException {
    int copied = 0;

    // copy libraries
    File baseLibDir = new File(srcDir, this.resourceLibDir);
    if (baseLibDir.exists()) {
      // TODO: copyLibraries is used on more than just this artifact - this
      // check needs to be placed elsewhere
      if (getLibraries().isEmpty()) {
        getLog().warn("Appear to have library resources, but not Libraries are defined");
      }
      // create all types of libs
      for (final Object element : getLibraries()) {
        final Library library = (Library) element;
        final String type = library.getType();

        File libDir = baseLibDir;
        final File typedLibDir = new File(libDir, type);
        if (typedLibDir.exists()) {
          libDir = typedLibDir;
        }

        final File libDstDir = getLayout().getLibDirectory(getTargetDirectory(), getMavenProject().getArtifactId(),
            getMavenProject().getVersion(), aol, type);
        getLog().debug("Copying libraries from " + libDir + " to " + libDstDir);

        // filter files for lib
        String includes = "**/*."
            + NarProperties.getInstance(getMavenProject()).getProperty(
                NarUtil.getAOLKey(aol) + "." + type + ".extension");

        if (new AOL(aol).getOS().equals(OS.WINDOWS)) {
          // Dynamic libraries on Windows come in 2 parts a (.dll) file and a
          // (.lib) import library.  The dynamic import library can be confused
          // with a static library because they share the same (.lib)
          // extension.  There are a couple of ways to disambiguate between the
          // two.  First, if there is a similarly named companion .dll file
          // with the .lib file, it is a fairly good guess that the .lib file
          // is an import library.  Second, running "lib /list <.lib file>"
          // will print a list of object files if the lib file is a static
          // library.
          Set<String> dllFiles = Files.list(Paths.get(libDir.getAbsolutePath()))
            .filter(path -> path.toString().endsWith(".dll"))
            .map(path -> {
              String s = path.getFileName().toString();
              return s.substring(0, s.length() - 4);
            })
            .collect(Collectors.toSet());
          Set<String> libFiles = Files.list(Paths.get(libDir.getAbsolutePath()))
            .filter(path -> path.toString().endsWith(".lib"))
            .map(path -> {
              String s = path.getFileName().toString();
              return s.substring(0, s.length() - 4);
            })
            .collect(Collectors.toSet());

          Set<String> filter = new HashSet<>(dllFiles);
          List<String> keepLibs = new ArrayList<>();
          if (libsName != null) {
            keepLibs.addAll(Arrays.asList(libsName.split(",")));
          }
          if (type.equals(Library.STATIC)) {
            // Set difference, only include libFiles which are not found
            // in the dll files set.
            filter.removeAll(keepLibs);
            libFiles.removeAll(filter);
            libFiles = libFiles.stream()
              .map(filename -> filename + ".lib")
              .collect(Collectors.toSet());
            includes = String.join(",", libFiles);
          } else if (type.equals(Library.SHARED)) {
            // Set intersection, only include lib (files which have a
            // corresponding dll file (and vice versa).
            filter.add(libsName);
            libFiles.retainAll(filter);
            libFiles = libFiles.stream()
              .map(filename -> filename + ".lib")
              .collect(Collectors.toSet());
            includes = String.join(",", libFiles);
            dllFiles = dllFiles.stream()
              .map(filename -> filename + ".dll")
              .collect(Collectors.toSet());
            includes += "," + String.join(",", dllFiles);
          }

        }
        copied += NarUtil.copyDirectoryStructure(libDir, libDstDir, includes, NarUtil.DEFAULT_EXCLUDES);
      }
    }

    return copied;
  }

  protected final void copyResources(final File srcDir, final String aol)
      throws MojoExecutionException, MojoFailureException {
    int copied = 0;
    try {
      copied += copyIncludes(srcDir);

      copied += copyBinaries(srcDir, aol);

      copied += copyLibraries(srcDir, aol);

      // unpack jar files
      final File classesDirectory = new File(getOutputDirectory(), "classes");
      classesDirectory.mkdirs();
      final List<File> jars = FileUtils.getFiles(srcDir, "**/*.jar", null);
      for (final File jar : jars) {
        getLog().debug("Unpacking jar " + jar);
        UnArchiver unArchiver;
        unArchiver = this.archiverManager.getUnArchiver(NarConstants.NAR_ROLE_HINT);
        unArchiver.setSourceFile(jar);
        unArchiver.setDestDirectory(classesDirectory);
        unArchiver.extract();
      }
    } catch (final IOException e) {
      throw new MojoExecutionException("NAR: Could not copy resources for " + aol, e);
    } catch (final NoSuchArchiverException e) {
      throw new MojoExecutionException("NAR: Could not find archiver for " + aol, e);
    } catch (final ArchiverException e) {
      throw new MojoExecutionException("NAR: Could not unarchive jar file for " + aol, e);
    }
    getLog().info("Copied " + copied + " resources for " + aol);
  }

}
