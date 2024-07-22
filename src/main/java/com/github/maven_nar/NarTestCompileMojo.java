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
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.shared.artifact.filter.collection.ScopeFilter;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;

import com.github.maven_nar.cpptasks.CCTask;
import com.github.maven_nar.cpptasks.CUtil;
import com.github.maven_nar.cpptasks.CompilerDef;
import com.github.maven_nar.cpptasks.LinkerDef;
import com.github.maven_nar.cpptasks.OutputTypeEnum;
import com.github.maven_nar.cpptasks.RuntimeType;
import com.github.maven_nar.cpptasks.SubsystemEnum;
import com.github.maven_nar.cpptasks.types.LibrarySet;
import com.github.maven_nar.cpptasks.types.LibraryTypeEnum;
import com.github.maven_nar.cpptasks.types.LinkerArgument;

/**
 * Compiles native test source files.
 * 
 * @author Mark Donszelmann
 */
@Mojo(name = "nar-testCompile", defaultPhase = LifecyclePhase.TEST_COMPILE,
  requiresDependencyResolution = ResolutionScope.TEST)
public class NarTestCompileMojo extends AbstractCompileMojo {
  /**
   * Skip running of NAR integration test plugins.
   */
  @Parameter(property = "skipNar")
  protected boolean skipNar;

  private void createTest(final Project antProject, final Test test)
      throws MojoExecutionException, MojoFailureException {
    final String type = "test";

    // configure task
    final CCTask task = new CCTask();
    task.setProject(antProject);

    // subsystem
    final SubsystemEnum subSystem = new SubsystemEnum();
    subSystem.setValue("console");
    task.setSubsystem(subSystem);

    // outtype
    final OutputTypeEnum outTypeEnum = new OutputTypeEnum();
    outTypeEnum.setValue(test.getType());
    task.setOuttype(outTypeEnum);

    // outDir
    Path outDir = getTestTargetDirectory().resolve(
      Path.of("bin", getAOL().toString()));

    // outFile
    final Path outFile = outDir.resolve(test.getName());
    getLog().debug("NAR - output: '" + outFile + "'");
    task.setOutfile(outFile);

    // object directory
    Path objDir = getTestTargetDirectory().resolve(
      Path.of("obj", getAOL().toString()));
    task.setObjdir(objDir);

    try {
      Files.createDirectories(outDir);
      Files.createDirectories(objDir);
    } catch (IOException e) {
      throw new MojoExecutionException(e);
    }

    // failOnError, libtool
    task.setFailonerror(failOnError(getAOL()));
    task.setLibtool(useLibtool(getAOL()));

    // runtime
    final RuntimeType runtimeType = new RuntimeType();
    runtimeType.setValue(getRuntime(getAOL()));
    task.setRuntime(runtimeType);

    // add C++ compiler
    final Cpp cpp = getCpp();
    if (cpp != null) {
      final CompilerDef cppCompiler = getCpp().getTestCompiler(type, test.getName());
      if (cppCompiler != null) {
        cppCompiler.setCommands(testCompileCommands);
        cppCompiler.setDryRun(dryRun);
        task.addConfiguredCompiler(cppCompiler);
      }
    }

    // add C compiler
    final C c = getC();
    if (c != null) {
      final CompilerDef cCompiler = c.getTestCompiler(type, test.getName());
      if (cCompiler != null) {
        cCompiler.setCommands(testCompileCommands);
        cCompiler.setDryRun(dryRun);
        task.addConfiguredCompiler(cCompiler);
      }
    }

    // add Fortran compiler
    final Fortran fortran = getFortran();
    if (fortran != null) {
      final CompilerDef fortranCompiler = getFortran().getTestCompiler(type, test.getName());
      if (fortranCompiler != null) {
        fortranCompiler.setCommands(testCompileCommands);
        fortranCompiler.setDebug(dryRun);
        task.addConfiguredCompiler(fortranCompiler);
      }
    }

    // add java include paths
    getJava().addIncludePaths(task, type);

    getMsvc().configureCCTask(task);

    List<NarArtifact> dependencies = getNarArtifacts();
    List<String> linkPaths = new ArrayList<String>();

    // If we're restricting deps to direct deps ONLY then trim transitive deps
    if (directDepsOnly) {
      HashSet<String> directDepsSet = getDirectDepsSet(getVerboseDependencyTree());
      ListIterator<NarArtifact> depsIt = dependencies.listIterator();

      // Trim all deps from dependencies that are not in the directDepsSet, warn if they are found.
      while (depsIt.hasNext()) {
        NarInfo dep = depsIt.next().getNarInfo();
        if (!directDepsSet.contains(dep.getGroupId() + ":" + dep.getArtifactId())) {
          this.getLog().debug("Stray dependency: " + dep + " found. This may cause build failures.");
          depsIt.remove();
          // If this transitive dependency was a shared object, add it to the linkPaths list.
          String depType = dep.getBinding(null, null);
          if (Objects.equals(depType, Library.SHARED)) {
            Path soDir = getLayout().getLibDirectory(getTargetDirectory(), dep.getArtifactId(), dep.getVersion(), getAOL().toString(), depType);
            if (Files.exists(soDir)) {
              linkPaths.add(soDir.toAbsolutePath().toString());
            }
          }
        }
      }
    }
    
    // add dependency include paths
    for (final NarArtifact artifact : dependencies) {

      // check if it exists in the normal unpack directory
      Path include = getLayout()
          .getIncludeDirectory(getUnpackDirectory(), artifact.getArtifactId(), artifact.getBaseVersion());
      if (Files.notExists(include)) {
        // otherwise try the test unpack directory
        include = getLayout()
            .getIncludeDirectory(getTestUnpackDirectory(), artifact.getArtifactId(), artifact.getBaseVersion());
      }
      if (Files.exists(include)) {
        String includesType = artifact.getNarInfo().getIncludesType(null);
        if (includesType.equals("system")) {
          task.createSysIncludePath().setPath(include.toString());
        }
        else {
          task.createIncludePath().setPath(include.toString());
        }
      }
    }

    // add javah generated include path
    final Path jniIncludeDir = getJavah().getJniDirectory();
    if (Files.exists(jniIncludeDir)) {
      task.createIncludePath().setPath(jniIncludeDir.toString());
    }

    // add linker
    final LinkerDef linkerDefinition = getLinker().getTestLinker(this, task, getOS(), getAOL().getKey() + ".linker.",
        type, linkPaths);
    linkerDefinition.setCommands(testLinkCommands);
    linkerDefinition.setDryRun(dryRun);
    task.addConfiguredLinker(linkerDefinition);

    final Path includeDir = getLayout().getIncludeDirectory(getTargetDirectory(), getMavenProject().getArtifactId(),
        getMavenProject().getVersion());

    String linkType = test.getLink( getLibraries() );
    final Path libDir = getLayout().getLibDirectory(getTargetDirectory(), getMavenProject().getArtifactId(),
        getMavenProject().getVersion(), getAOL().toString(), linkType);

    // add include of this package
    if (Files.exists(includeDir)) {
      task.createIncludePath().setLocation(includeDir.toFile());
    }

    // add library of this package
    if (Files.exists(libDir)) {
      final LibrarySet libSet = new LibrarySet();
      libSet.setProject(antProject);
      final String libs = getNarInfo().getProperty(getAOL(), "libs.names", getOutput(true));

      getLog().debug("Searching for parent to link with " + libs);
      libSet.setLibs(new CUtil.StringArrayBuilder(libs));
      final LibraryTypeEnum libType = new LibraryTypeEnum();
      libType.setValue(linkType);
      libSet.setType(libType);
      libSet.setDir(libDir);
      task.addLibset(libSet);
    }

    // add dependency libraries
    final List<String> depLibOrder = getDependencyLibOrder();

    // reorder the libraries that come from the nar dependencies
    // to comply with the order specified by the user
    if (depLibOrder != null && !depLibOrder.isEmpty()) {

      final List<NarArtifact> tmp = new LinkedList<>();

      for (final String depToOrderName : depLibOrder) {

        for (final Iterator<NarArtifact> j = dependencies.iterator(); j.hasNext(); ) {

          final NarArtifact dep = j.next();
          final String depName = dep.getGroupId() + ":" + dep.getArtifactId();

          if (depName.equals(depToOrderName)) {

            tmp.add(dep);
            j.remove();
          }
        }
      }

      tmp.addAll(dependencies);
      dependencies = tmp;
    }
    
    Set<SysLib> dependencySysLibs = new LinkedHashSet<>();

    for (final NarArtifact dependency : dependencies) {
      // FIXME no handling of "local"

      final String binding = getBinding(test, dependency);
      getLog().debug("Using Binding: " + binding);
      AOL aol = getAOL();
      aol = dependency.getNarInfo().getAOL(getAOL());
      getLog().debug("Using Library AOL: " + aol.toString());

      // We dont link against the following library types :
      // - JNI, they are Java libraries
      // - executable, they are not libraries
      // - none, they are not libraries ... I gess
      // Static libraries should be linked. Even though the libraries
      // themselves will have been tested already, the test code could
      // use methods or classes defined in them.
      if (!binding.equals(Library.JNI) && !binding.equals(Library.NONE) && !binding.equals(Library.EXECUTABLE)) {
        // check if it exists in the normal unpack directory
        Path dir = getLayout()
            .getLibDirectory(getUnpackDirectory(), dependency.getArtifactId(), dependency.getBaseVersion(),
                aol.toString(), binding);
        getLog().debug("Looking for Library Directory: " + dir);
        if (Files.notExists(dir)) {
          getLog().debug("Library Directory " + dir + " does NOT exist.");

          // otherwise try the test unpack directory
          dir = getLayout()
              .getLibDirectory(getTestUnpackDirectory(), dependency.getArtifactId(), dependency.getBaseVersion(),
                  aol.toString(), binding);
          getLog().debug("Looking for Library Directory: " + dir);
        }
        if (Files.exists(dir)) {
          final LibrarySet libSet = new LibrarySet();
          libSet.setProject(antProject);

          // Load nar properties file from aol specific directory
          final Path aolNarInfoFile = getLayout()
                  .getNarInfoDirectory(getUnpackDirectory(), dependency.getGroupId(), dependency.getArtifactId(),
                          dependency.getBaseVersion(), aol.toString(), binding);

          // Read nar properties file as NarInfo
          NarInfo aolNarInfo = new NarInfo(dependency.getGroupId(), dependency.getArtifactId(),
                  dependency.getBaseVersion(), getLog(), aolNarInfoFile);

          // Write to log about custom nar properties found in aol directory.
          if(!aolNarInfo.getInfo().isEmpty()) {
            getLog().debug(String.format ("Custom NAR properties identified: %s-%s-%s-%s-%s",
                    dependency.getGroupId(),
                    dependency.getArtifactId(),
                    dependency.getBaseVersion(),
                    aol.toString(),
                    binding));
          }
          else {
            getLog().debug(String.format ("Custom NAR properties not identified: %s-%s-%s-%s-%s",
                    dependency.getGroupId(),
                    dependency.getArtifactId(),
                    dependency.getBaseVersion(),
                    aol.toString(),
                    binding));
          }

          // overlay aol nar properties file on top of the default one.
          aolNarInfo.mergeProperties(dependency.getNarInfo().getInfo());

          // FIXME, no way to override
          final String libs = aolNarInfo.getLibs(getAOL());
          if (libs != null && !libs.equals("")) {
            getLog().debug("Using LIBS = " + libs);
            libSet.setLibs(new CUtil.StringArrayBuilder(libs));
            libSet.setDir(dir);
            task.addLibset(libSet);
          }

          dependencySysLibs.addAll(getDependecySysLib(aolNarInfo));
        } else {
          getLog().debug("Library Directory " + dir + " does NOT exist.");
        }

        // FIXME, look again at this, for multiple dependencies we may need to
        // remove duplicates
        final String options = dependency.getNarInfo().getOptions(getAOL());
        if (options != null && !options.equals("")) {
          getLog().debug("Using OPTIONS = " + options);
          final LinkerArgument arg = new LinkerArgument();
          arg.setValue(options);
          linkerDefinition.addConfiguredLinkerArg(arg);
        }
      }
    }
    
    if (syslibsFromDependencies) {
      for (SysLib s : dependencySysLibs) {
        task.addSyslibset(s.getSysLibSet(antProject));
      }
    }

    // Add JVM to linker
    getJava().addRuntime(task, getJavaHome(getAOL()), getOS(), getAOL().getKey() + ".java.");

    // execute
    try {
      task.execute();
    } catch (final BuildException e) {
      throw new MojoExecutionException("NAR: Test-Compile failed", e);
    }
  }

  /**
   * List the dependencies needed for tests compilations, those dependencies are
   * used to get the include paths needed
   * for compilation and to get the libraries paths and names needed for
   * linking.
   */
  @Override
  protected ScopeFilter getArtifactScopeFilter() {
    // Was Artifact.SCOPE_TEST  - runtime??
    return new ScopeFilter( Artifact.SCOPE_TEST, null );
  }

  @Override
  protected Path getUnpackDirectory() {
    return getTestUnpackDirectory() == null ? super.getUnpackDirectory() : getTestUnpackDirectory();
  }

  @Override
  public final void narExecute() throws MojoExecutionException, MojoFailureException {
    if (this.skipTests) {
      getLog().info("Not compiling test sources");
    } else {

      // make sure destination is there
      try {
        Files.createDirectories(getTestTargetDirectory());
      } catch (IOException e) {
        throw new MojoExecutionException(e);
      }

      for (final Test test : getTests()) {
        createTest(getAntProject(), test);
      }
      
      if (replay != null) {
        Path compileCommandFile = replay.getOutputDirectory().resolve(NarConstants.REPLAY_TEST_COMPILE_NAME);
        NarUtil.writeCommandFile(compileCommandFile, testCompileCommands);
        
        Path linkCommandFile = replay.getOutputDirectory().resolve(NarConstants.REPLAY_TEST_LINK_NAME);
        NarUtil.writeCommandFile(linkCommandFile, testLinkCommands);
      }
    }
  }

}
