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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.BiConsumer;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.shared.artifact.filter.collection.ScopeFilter;

/**
 * Generates a .props file for include in a Visual Studio 2010 project file
 * (vcxproj).
 *
 * @author Heath Nielson
 *
 */
@Mojo(name = "nar-vcprops", requiresProject = true,
    requiresDependencyResolution = ResolutionScope.COMPILE)
public class NarVcpropsMojo extends AbstractCompileMojo {

  /**
   * Directory in which VC props are created
   */
  @Parameter(defaultValue = "${project.build.directory}/nar/vc", required = true)
  private File vcPropsTargetDirectory;

  private String vcPropsName = "nar.props";

  @Override
  public final void narExecute() throws MojoExecutionException, MojoFailureException {

    // Only do this if MSVC++ compiler is being used.
    if (!getOS().equals(OS.WINDOWS)) {
      getLog().debug("Skipping -- not running on Windows");
      return;
    }

    vcPropsTargetDirectory.mkdirs();

    try {
      writePropsFile(getIncludeDirs(), getLibs());
    } catch (IOException e) {
      throw new MojoExecutionException("Could not write '" + vcPropsName
          + "'", e);
    }

  }

  // Copied from NarCompileMojo
  private List<Path> getIncludeDirs() throws MojoExecutionException, MojoFailureException {
    List<Path> includeDirs = new ArrayList<>();
    for (Object dep : getNarManager().getNarDependencies("compile")) {
      NarArtifact narDependency = (NarArtifact) dep;
      // FIXME, handle multiple includes from one NAR
      String binding = narDependency.getNarInfo().getBinding(getAOL(),
          Library.STATIC);
      getLog().debug(
          "Looking for " + narDependency + " found binding " + binding);
      if (!binding.equals(Library.JNI)) {
        Path unpackDirectory = getUnpackDirectory();
        Path include = getLayout().getIncludeDirectory(unpackDirectory,
            narDependency.getArtifactId(), narDependency.getBaseVersion());
        getLog().debug("Looking for include directory: " + include);
        if (Files.exists(include)) {
          includeDirs.add(include);
        } else {
          throw new MojoExecutionException(
              "NAR: unable to locate include path: " + include);
        }
      }
    }
    return includeDirs;
  }

  private List<Path> getLibs() throws MojoExecutionException, MojoFailureException {
    List<Path> libs = new ArrayList<>();

    List<String> depLibOrder = getDependencyLibOrder();
    List<NarArtifact> depLibs = getNarManager().getNarDependencies("compile");

    // reorder the libraries that come from the nar dependencies
    // to comply with the order specified by the user
    if ((depLibOrder != null) && !depLibOrder.isEmpty()) {
      List<NarArtifact> tmp = new LinkedList<NarArtifact>();

      for (String depToOrderName : depLibOrder) {
        for (Iterator<NarArtifact> j = depLibs.iterator(); j.hasNext();) {
          NarArtifact dep = (NarArtifact) j.next();
          String depName = dep.getGroupId() + ":" + dep.getArtifactId();

          if (depName.equals(depToOrderName)) {
            tmp.add(dep);
            j.remove();
          }
        }
      }

      tmp.addAll(depLibs);
      depLibs = tmp;
    }

    for (NarArtifact dependency : depLibs) {
      // FIXME no handling of "local"

      // FIXME, no way to override this at this stage
      String binding = dependency.getNarInfo().getBinding(getAOL(),
          Library.NONE);
      getLog().debug("Using Binding: " + binding);
      AOL aol = getAOL();
      aol = dependency.getNarInfo().getAOL(getAOL());
      getLog().debug("Using Library AOL: " + aol.toString());

      if (!binding.equals(Library.JNI) && !binding.equals(Library.NONE)
          && !binding.equals(Library.EXECUTABLE)) {
        Path unpackDirectory = getUnpackDirectory();

        Path dir = getLayout().getLibDirectory(unpackDirectory,
            dependency.getArtifactId(), dependency.getBaseVersion(),
            aol.toString(), binding);

        getLog().debug("Looking for Library Directory: " + dir);
        if (Files.exists(dir)) {
          // FIXME, no way to override
          List<String> libNames = Arrays.asList(dependency.getNarInfo()
              .getLibs(getAOL()).split(" "));
          for (String libName : libNames) {
            Path f = dir.resolve(getLinkName(libName));
            if (Files.exists(f)) {
              libs.add(f);
            } else {
              // Not having an import library is not necessary fatal.
              // When the library is a run-time dependency it might not
              // include an import library to link to.
              getLog().debug("NAR: unable to locate library: " + f);
            }
          }
        } else {
          getLog().debug("Library Directory " + dir + " does NOT exist.");
        }

      }
    }
    return libs;
  }

  private void writePropsFile(List<Path> includeDirs, List<Path> libs) throws IOException {
    // Convert the includeDirs list to a semi-colon separated string
    String includeStr = includeDirs.stream()
      .map(dir -> dir.toAbsolutePath().toString())
      .collect(Collectors.joining(";"));

    // From the list of files in libs create two semi-colon separated
    // strings: a list of directories where the libraries are located and
    // a list of the library file names.
    Map.Entry<String, String> paths = libs.stream()
      .collect(Collectors.teeing(
        Collector.of(
          () -> new StringJoiner(";"),
          new BiConsumer<StringJoiner, Path>() {
            public void accept(StringJoiner sj, Path p) { sj.add(p.getParent().toString()); };
          },
          StringJoiner::merge,
          StringJoiner::toString),
        Collector.of(
          () -> new StringJoiner(";"),
          new BiConsumer<StringJoiner, Path>() {
            public void accept(StringJoiner sj, Path p) { sj.add(p.getFileName().toString()); };
          },
          StringJoiner::merge,
          StringJoiner::toString),
        Map::entry));
/*
    StringBuffer libDirStr = new StringBuffer();
    StringBuffer libStr = new StringBuffer();
    for (Iterator<File> i = libs.iterator(); i.hasNext();) {
      File lib = i.next();
      libDirStr.append(lib.getParent());
      libStr.append(lib.getName());
      if (i.hasNext()) {
        libDirStr.append(";");
        libStr.append(";");
      }
    }
*/
    FileOutputStream fos = new FileOutputStream(new File(
        vcPropsTargetDirectory, vcPropsName));
    PrintWriter p = new PrintWriter(fos);
    p.println("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
    p.println("<Project ToolsVersion=\"4.0\" xmlns=\"http://schemas.microsoft.com/developer/msbuild/2003\">");
    p.println("  <ImportGroup Label=\"PropertySheets\" />");
    p.println("  <PropertyGroup Label=\"UserMacros\">");
    p.println("    <NARIncludes>" + includeStr + "</NARIncludes>");
    p.println("    <NARLibs>" + paths.getValue() + "</NARLibs>");
    p.println("    <NARLibDirs>" + paths.getKey() + "</NARLibDirs>");
    p.println("  </PropertyGroup>");
    p.println("  <PropertyGroup />");
    p.println("  <ItemDefinitionGroup />");
    p.println("  <ItemGroup />");
    p.println("</Project>");
    p.close();
  }

  /**
   * List the dependencies needed for compilation.
   */
  @Override
  protected ScopeFilter getArtifactScopeFilter() {
    return new ScopeFilter(Artifact.SCOPE_COMPILE, null);
  }
}
