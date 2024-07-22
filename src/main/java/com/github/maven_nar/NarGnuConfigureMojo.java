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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Copies the GNU style source files to a target area, autogens and configures
 * them.
 * 
 * @author Mark Donszelmann
 */
@Mojo(name = "nar-gnu-configure", requiresProject = true, defaultPhase = LifecyclePhase.PROCESS_SOURCES,
    requiresDependencyResolution = ResolutionScope.COMPILE)
public class NarGnuConfigureMojo extends AbstractGnuMojo {

  private static final String AUTOGEN = "autogen.sh";

  private static final String BUILDCONF = "buildconf";

  private static final String CONFIGURE = "configure";

  /**
   * If true, we run <code>./configure</code> in the source directory instead of
   * copying the
   * source code to the <code>target/</code> directory first (this saves disk
   * space but
   * violates Maven's paradigm of keeping generated files inside the
   * <code>target/</code> directory structure.
   */
  @Parameter(property = "nar.gnu.configure.in-place")
  private boolean gnuConfigureInPlace;

  /**
   * Skip running of autogen.sh (aka buildconf).
   */
  @Parameter(property = "nar.gnu.autogen.skip")
  private boolean gnuAutogenSkip;

  /**
   * Skip running of configure and therefore also autogen.sh
   */
  @Parameter(property = "nar.gnu.configure.skip")
  private boolean gnuConfigureSkip;

  /**
   * Arguments to pass to GNU configure.
   */
  @Parameter(property = "nar.gnu.configure.args", defaultValue = "")
  private String gnuConfigureArgs;

  @Parameter(property = "nar.gnu.configure.env", defaultValue = "")
  private String gnuConfigureEnv;

  /**
   * Arguments to pass to GNU buildconf.
   */
  @Parameter(property = "nar.gnu.buildconf.args", defaultValue = "")
  private String gnuBuildconfArgs;

  @Parameter
  private String cflags;

  @Parameter
  private String cppflags;

  @Parameter
  private String ldflags;

  public NarGnuConfigureMojo() {
  }

  @Override
  public final void narExecute() throws MojoExecutionException, MojoFailureException {

    if (!useGnu()) {
      return;
    }

    final Path sourceDir = getGnuSourceDirectory();
    if (Files.exists(sourceDir)) {
      Path targetDir;

      if (!this.gnuConfigureInPlace) {
        targetDir = getGnuAOLSourceDirectory();

        getLog().info("Copying GNU sources");

        try {
          Files.createDirectories(targetDir);
          NarUtil.copyDirectoryStructure(sourceDir, targetDir, null, null);
        } catch (final IOException e) {
          throw new MojoExecutionException("Failed to copy GNU sources", e);
        }

        if (!this.gnuConfigureSkip && !this.gnuAutogenSkip) {
          final Path autogen = targetDir.resolve(AUTOGEN);
          final Path buildconf = targetDir.resolve(BUILDCONF);
          final Path configureac = targetDir.resolve(CONFIGURE + ".ac");
          final Path configurein = targetDir.resolve(CONFIGURE + ".in");
          if (Files.exists(autogen)) {
            getLog().info("Running GNU " + AUTOGEN);
            runAutogen(autogen, targetDir, null);
          } else if (Files.exists(buildconf)) {
            getLog().info("Running GNU " + BUILDCONF);
            String gnuBuildconfArgsArray[] = null;
            if (this.gnuBuildconfArgs != null) {
              gnuBuildconfArgsArray = this.gnuBuildconfArgs.split("\\s");
            }
            runAutogen(buildconf, targetDir, gnuBuildconfArgsArray);
          } else if (Files.exists(configureac) || Files.exists(configurein)) {
            final int result = NarUtil.runCommand("autoreconf",
                List.of("-fi"), targetDir, null, getLog());
            if (result != 0) {
              throw new MojoExecutionException("'" + autogen.getFileName()
                  + "' errorcode: " + result);
            }
          }
        }
      } else {
        targetDir = sourceDir;
      }

      final Path configure = targetDir.resolve(CONFIGURE);
      if (!this.gnuConfigureSkip && Files.exists(configure)) {
        getLog().info("Running GNU " + CONFIGURE);

        NarUtil.makeExecutable(configure, getLog());

        // Build the environment string
        List<String> env = new ArrayList<String>();
        if (getLinker().getName().startsWith("clang")) {
           env.add("CC=clang");
           env.add("CPP=clang-cpp");
           env.add("CXX=clang++");
        }

        // Add any specifically set env values
        if (gnuConfigureEnv != null) {
          env.addAll(Arrays.asList(gnuConfigureEnv.split(",")));
        }

        // If the cflags property is set, add it to the CFLAGS env variable
        StringBuffer cflagsStr = new StringBuffer("CFLAGS=");
        if (cflags != null) {
          cflagsStr.append(cflags).append(" ");
        }

        // Add dependent libs to the CFLAGS env variable
        final List<String> includeDirs = getIncludeDirs();
        if (includeDirs.size() > 0) {
          for (int i = 0; i < includeDirs.size(); i++) {
            cflagsStr.append("-I").append(includeDirs.get(i)).append(" ");
          }
        }

        // If the CFLAGS has a value add it to the environment
        if (cflagsStr.length() > "CFLAGS=".length()) {
          env.add(cflagsStr.toString());
        }

        // If the CPPFLAGS property is set, add it to the environment
        if (cppflags != null) {
          StringBuffer sb = new StringBuffer("CPPFLAGS=").append(cppflags);
          env.add(sb.toString());
        }

        // Build and add the LD_LIBRARY_PATH and LDFLAGS env variables
        final List<Path> libDirs = getLibDirs();
        final List<String> libs = getDependentLibs();
        if (libDirs.size() > 0) {
          StringBuffer ldLibPath = new StringBuffer("LD_LIBRARY_PATH=");
          StringBuffer sb = new StringBuffer("LDFLAGS=");
          if (ldflags != null) {
            sb.append(ldflags).append(" ");
          }
          for (int i = 0; i < libDirs.size(); i++) {
            sb.append("-L").append(libDirs.get(i)).append(" ");
            ldLibPath.append(libDirs.get(i)).append(":");
          }

          if (libs.size() > 0) {
            for (int i = 0; i < libs.size(); i++) {
              sb.append("-l").append(libs.get(i)).append(" ");
            }
          }
          env.add(ldLibPath.toString());
          env.add(sb.toString());
        }

        // create the array to hold constant and additional args
        List<String> args = Stream.ofNullable(this.gnuConfigureArgs)
            .map(s -> s.split(" "))
            .flatMap(Arrays::stream)
            .collect(Collectors.toList());

        // first 2 args are constant
        args.add(0, configure.toAbsolutePath().toString());
        args.add(1, "--prefix=" + getGnuAOLTargetDirectory().toAbsolutePath().toString());

        final Path buildDir = getGnuAOLSourceDirectory();
        try {
          Files.createDirectories(buildDir);
        } catch (IOException e) {
          throw new MojoExecutionException(e);
        }

        getLog().info("args: " + args);
        final int result = NarUtil.runCommand("sh", args, buildDir,
            env, getLog());
        if (result != 0) {
          throw new MojoExecutionException("'" + CONFIGURE + "' errorcode: " + result);
        }
      }
    }
  }

  private void runAutogen(final Path autogen, final Path targetDir, final String args[])
      throws MojoExecutionException, MojoFailureException {
    // fix missing config directory
    final Path configDir = targetDir.resolve("config");
    if (Files.notExists(configDir)) {
      try {
        Files.createDirectories(configDir);
      } catch (IOException e) {
        throw new MojoExecutionException(e);
      }
    }

    NarUtil.makeExecutable(autogen, getLog());
    getLog().debug("running sh ./" + autogen.getFileName());

    List<String> arguments = new ArrayList<>();
    if (args != null) {
      Collections.addAll(arguments, args);
    }
    arguments.add(0, "./" + autogen.getFileName());

    getLog().info("args: " + arguments);

    final int result = NarUtil.runCommand("sh", arguments, targetDir, null, getLog());
    if (result != 0) {
      throw new MojoExecutionException("'" + autogen.getFileName() + "' errorcode: " + result);
    }
  }

}
