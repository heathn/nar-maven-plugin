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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.bcel.classfile.ClassFormatException;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.toolchain.Toolchain;
import org.apache.maven.toolchain.ToolchainManager;
import org.codehaus.plexus.compiler.util.scan.InclusionScanException;
import org.codehaus.plexus.compiler.util.scan.SourceInclusionScanner;
import org.codehaus.plexus.compiler.util.scan.StaleSourceScanner;
import org.codehaus.plexus.compiler.util.scan.mapping.SingleTargetSourceMapping;
import org.codehaus.plexus.compiler.util.scan.mapping.SuffixMapping;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;

/**
 * Sets up the javah configuration
 *
 * @author Mark Donszelmann
 */
public class Javah {

  /**
   * Javah command to run.
   */
  @Parameter(defaultValue = "javah")
  private String name = "javah";
  
  /**
   * Skip javah.
   */
  @Parameter
  private boolean skip = false;

  /**
   * Add boot class paths. By default none.
   */
  @Parameter
  private List<File> bootClassPaths = new ArrayList<>();

  /**
   * Add class paths. By default the classDirectory directory is included and
   * all dependent classes.
   */
  @Parameter
  private List<String> classPaths = new ArrayList<>();

  /**
   * The target directory into which to generate the output.
   */
  @Parameter(defaultValue = "${project.build.directory}/nar/javah-include", required = true)
  private File jniDirectory;

  /**
   * The class directory to scan for class files with native interfaces.
   */
  @Parameter(defaultValue = "${project.build.directory}/classes", required = true)
  private File classDirectory;

  /**
   * The set of files/patterns to include Defaults to "**\/*.class"
   */
  @Parameter
  private Set<String> includes = new HashSet<>();

  /**
   * A list of exclusion filters.
   */
  @Parameter
  private Set<String> excludes = new HashSet<>();

  /**
   * A list of class names e.g. from java.sql.* that are also passed to javah.
   */
  @Parameter
  private Set<String> extraClasses = new HashSet<>();

  /**
   * The granularity in milliseconds of the last modification date for testing
   * whether a source needs recompilation
   */
  @Parameter(defaultValue = "0", required = true)
  private int staleMillis = 0;

  /**
   * The directory to store the timestampfile for the processed aid files.
   * Defaults to jniDirectory.
   */
  @Parameter(defaultValue = "${project.build.directory}/nar/javah-include")
  private File timestampDirectory;

  /**
   * The timestampfile for the processed class files. Defaults to name of javah.
   */
  @Parameter
  private File timestampFile;

  private AbstractNarMojo mojo;

  public Javah() {
  }

  public final void execute() throws MojoExecutionException, MojoFailureException {
      
    String jreVersion = System.getProperty("java.version");
    int majorVer = Integer.parseInt(jreVersion.split("\\.")[0]);
    if (majorVer >= 10 || skip) {
        this.mojo.getLog().info("javah skipped");
        return;
    }  
    
    getClassDirectory().mkdirs();

    try {
      final SourceInclusionScanner scanner = new StaleSourceScanner(this.staleMillis, getIncludes(), this.excludes);
      if (Files.exists(getTimestampDirectory())) {
        scanner.addSourceMapping(new SingleTargetSourceMapping(".class", getTimestampFile().getPath()));
      } else {
        scanner.addSourceMapping(new SuffixMapping(".class", ".dummy"));
      }

      final Set<File> classes = scanner.getIncludedSources(getClassDirectory(), getTimestampDirectory().toFile());

      if (!classes.isEmpty()) {
        final Set<String> files = new HashSet<>();
        for (final File aClass : classes) {
          final String file = aClass.getPath();
          final JavaClass clazz = NarUtil.getBcelClass(file);
          final Method[] method = clazz.getMethods();
          for (final Method element : method) {
            if (element.isNative()) {
              files.add(clazz.getClassName());
            }
          }
        }

        if (!files.isEmpty()) {
          Files.createDirectories(getJniDirectory());
          Files.createDirectories(getTimestampDirectory());

          final Path javah = getJavah();

          this.mojo.getLog().info("Running " + javah + " compiler on " + files.size() + " classes...");
          final int result = NarUtil.runCommand(javah.toString(), generateArgs(files), null, null, this.mojo.getLog());
          if (result != 0) {
            throw new MojoFailureException(javah + " failed with exit code " + result + " 0x"
                + Integer.toHexString(result));
          }
          FileUtils.fileWrite(getTimestampDirectory() + "/" + getTimestampFile(), "");
        }
      }
    } catch (final InclusionScanException e) {
      throw new MojoExecutionException("JAVAH: Class scanning failed", e);
    } catch (final IOException e) {
      throw new MojoExecutionException("JAVAH: IO Exception", e);
    } catch (final ClassFormatException e) {
      throw new MojoExecutionException("JAVAH: Class could not be inspected", e);
    }
  }

  private List<String> generateArgs(final Set<String> classes) throws MojoExecutionException {

    final List<String> args = new ArrayList<>();
    
    if (!this.bootClassPaths.isEmpty()) {
      args.add("-bootclasspath");
      args.add(StringUtils.join(this.bootClassPaths.iterator(), File.pathSeparator));
    }

    args.add("-classpath");
    args.add(StringUtils.join(getClassPaths().iterator(), File.pathSeparator));

    args.add("-d");
    args.add(getJniDirectory().toString());

    if (this.mojo.getLog().isDebugEnabled()) {
      args.add("-verbose");
    }

    if (classes != null) {
      for (final String aClass : classes) {
        args.add(aClass);
      }
    }

    if (this.extraClasses != null) {
      for (final String extraClass : this.extraClasses) {
        args.add(extraClass);
      }
    }

    return args;
  }

  protected final File getClassDirectory() {
    if (this.classDirectory == null) {
      this.classDirectory = new File(this.mojo.getMavenProject().getBuild().getDirectory(), "classes");
    }
    return this.classDirectory;
  }

  protected final List<String> getClassPaths() throws MojoExecutionException {
    if (this.classPaths.isEmpty()) {
      try {
        this.classPaths.addAll(this.mojo.getMavenProject().getCompileClasspathElements());
      } catch (final DependencyResolutionRequiredException e) {
        throw new MojoExecutionException("JAVAH, cannot get classpath", e);
      }
    }
    return this.classPaths;
  }

  protected final Set<String> getIncludes() {
    NarUtil.removeNulls(this.includes);
    if (this.includes.isEmpty()) {
      this.includes.add("**/*.class");
    }
    return this.includes;
  }

  private Path getJavah() throws MojoExecutionException, MojoFailureException {
    Path javah = null;

    // try toolchain
    final Toolchain toolchain = getToolchain();
    if (toolchain != null) {
      javah = Path.of(toolchain.findTool("javah"));
    }

    // try java home
    if (javah == null) {
      final Path javahFile = this.mojo.getJavaHome(this.mojo.getAOL()).resolve("bin");
      javah = javahFile.resolve(this.name).toAbsolutePath();
    }

    // forget it...
    if (javah == null) {
      throw new MojoExecutionException("NAR: Cannot find 'javah' in Toolchain or on JavaHome");
    }

    return javah;
  }

  protected final Path getJniDirectory() {
    if (this.jniDirectory == null) {
      this.jniDirectory = new File(this.mojo.getMavenProject().getBuild().getDirectory(), "nar/javah-include");
    }
    return this.jniDirectory.toPath();
  }

  protected final Path getTimestampDirectory() {
    if (this.timestampDirectory == null) {
      this.timestampDirectory = getJniDirectory().toFile();
    }
    return this.timestampDirectory.toPath();
  }

  protected final File getTimestampFile() {
    if (this.timestampFile == null) {
      this.timestampFile = new File(this.name);
    }
    return this.timestampFile;
  }

  // TODO remove the part with ToolchainManager lookup once we depend on
  // 2.0.9 (have it as prerequisite). Define as regular component field then.
  private Toolchain getToolchain() {
    Toolchain toolChain = null;
    final ToolchainManager toolchainManager = ((NarJavahMojo) this.mojo).getToolchainManager();

    if (toolchainManager != null) {
      toolChain = toolchainManager.getToolchainFromBuildContext("jdk", ((NarJavahMojo) this.mojo).getSession());
    }
    return toolChain;
  }

  public final void setAbstractCompileMojo(final AbstractNarMojo abstractNarMojo) {
    this.mojo = abstractNarMojo;
  }
}
