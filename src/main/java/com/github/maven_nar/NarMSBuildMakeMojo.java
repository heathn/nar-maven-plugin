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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.util.FileUtils;

/**
 * Runs MSBuild
 *
 * Uses the following inherited methods from AbstractGnuMojo:
 * getGnuSourceDirectory(), getIncludeDirs(), getLibDirs(), getDependentLibs()
 * @author Heath Nielson
 */
@Mojo(name = "nar-win-msbuild", defaultPhase = LifecyclePhase.COMPILE,
    requiresProject = true,
    requiresDependencyResolution = ResolutionScope.COMPILE)
public class NarMSBuildMakeMojo extends AbstractGnuMojo {
  /**
   * Space delimited list of arguments to pass to make
   *
   */
  @Parameter
  private String msProjectArgs;

  /**
   * Comma delimited list of environment variables to setup before running make
   *
   */
  @Parameter
  private String makeEnv;

  /**
   * Name of Makefile
   *
   */
  @Parameter
  private String msProjectFile;

  /**
   * Boolean to control if we should run make install after the make
   *
   */
  @Parameter(defaultValue = "false")
  private boolean gnuMakeInstallSkip;

  @Parameter
  private String additionalIncludes;


  private static final String includeTag = "<AdditionalIncludeDirectories>";
  private static final String linkDirTag = "<AdditionalLibraryDirectories>";
  private static final String libTag = "<AdditionalDependencies>";

  @Override
  public final void narExecute() throws MojoExecutionException, MojoFailureException {
    if (!getOS().equals(OS.WINDOWS) || msProjectFile == null) {
      return;
    }

    File targetDir = new File(getTargetDirectory() + File.separator + "msbuild");
    // Copy everything under src/main to target
    getLog().info("Copying sources");

    try {
      FileUtils.mkdir(targetDir.getPath());
      NarUtil.copyDirectoryStructure(getGnuSourceDirectory(), targetDir, null, null);
    } catch (IOException e) {
      throw new MojoExecutionException("Failed to copy GNU sources", e);
    }

    final String[] env = new String[] {
      "PATH=" + getMsvc().getPathVariable().getValue()
    };

    Linker linker = getLinker();
    String strVersion = linker.getVersion(this);
    int version = Integer.parseInt(strVersion.substring(0, strVersion.indexOf('.')));
    if (version >= 10) { // VCUpgrade was included beginning with VC10
      StringBuffer projectContents = null;

      // Read the first part of the file to determine if vcupgrade
      // really does need to run
      boolean isValidMsBuildFile = false;
      try {
        projectContents = readFile(new File(targetDir, msProjectFile));
        if (projectContents.indexOf("msbuild/2003") > 0) {
          isValidMsBuildFile = true;
        }
      } catch (Exception e) {
        throw new MojoExecutionException(
            "Unable to read project file: " + e.getMessage(), e);
      }

      // Convert
      if (!isValidMsBuildFile/*projectfile.endsWith("vcxproj")*/) {

        // vcupgrade can only handle dsp files with the CR-LF endings
        // Reading in the project file has automatically performed the
        // conversion as a convenience write out the project file.  It
        // may not be necessary but it's nice to be sure.
        try {
          FileOutputStream fos = new FileOutputStream(new File(targetDir, msProjectFile));
          fos.write(projectContents.toString().getBytes());
          fos.close();
        } catch (Exception e) {
          throw new MojoExecutionException(
              "Unable to update project file: " + e.getMessage(), e);
        }

        getLog().info("Running VCUpgrade");
        final int result = NarUtil.runCommand("vcupgrade", new String[] {
            msProjectFile
        }, targetDir, env, getLog());
        if (result != 0) {
          throw new MojoExecutionException("'VCUpgrade' errcode: " + result);
        }
        msProjectFile = msProjectFile.substring(0, msProjectFile.lastIndexOf('.')) + ".vcxproj";

        // Reread converted project file
        try {
          projectContents = readFile(new File(targetDir, msProjectFile));
        } catch (Exception e) {
          throw new MojoExecutionException(
              "Unable to read project file: " + e.getMessage(), e);
        }

        // VCUpgrade under certain conditions does not set the
        // PlatformToolset for certain projects.  If the value is unset
        // the default value is v100 which is the platform toolset
        // for MSVC v.2010.  To work around that default, insert a
        // default rule which will set the PlatformToolset value to
        // the value of the the installed version.  NOTE: This assumes
        // the platform toolset version follows the linker major
        // version; i.e. v. 10 -> v100, v. 11 -> v110, v. 12 -> 120, ...
        String globalPropsTag = "<PropertyGroup Label=\"Globals\">";
        int idx = projectContents.indexOf(globalPropsTag);
        if (idx != -1) {
          String platformToolset = "\n" +
              "<PlatformToolset>v" + Integer.toString(version) + "0</PlatformToolset>";
          idx += globalPropsTag.length();
          projectContents.insert(idx, platformToolset);
        }
      }

      StringBuffer additionalIncludeDirs = new StringBuffer();
      if (additionalIncludes != null) {
        additionalIncludeDirs.append(additionalIncludes).append(";");
      }
      List<String> includeDirs = getIncludeDirs();
      if (includeDirs.size() > 0) {
        for (int i = 0; i < includeDirs.size(); i++) {
          additionalIncludeDirs.append(includeDirs.get(i)).append(";");
        }
      }

      if (additionalIncludeDirs.length() > 0) {
        int idx = projectContents.indexOf(includeTag);
        if (idx == -1) { // Add additional include directories
          //TODO: Should really parse XML
          idx = projectContents.indexOf("<ClCompile>");
          String additionalIncludes = "\n"
              + "<AdditionalIncludeDirectories>"
              + additionalIncludeDirs.toString()
              + "</AdditionalIncludeDirectories>\n";
          while (idx >=0) {
            idx += "<ClCompile>".length();
            projectContents.insert(idx, additionalIncludes);
            idx += additionalIncludes.length();
            idx = projectContents.indexOf("<ClCompile>", idx);
          }
        }
        while (idx >= 0) {
          idx += includeTag.length();
          projectContents.insert(idx, additionalIncludeDirs.toString());
          idx = projectContents.indexOf(includeTag, idx);
        }
      }

      List<File> libDirs = getLibDirs();
      if (libDirs.size() > 0) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < libDirs.size(); i++) {
          sb.append(libDirs.get(i)).append(";");
        }
        int idx = projectContents.indexOf(linkDirTag);
        if (idx == -1) {
          //TODO: Should really parse XML
          idx = projectContents.indexOf("<Link>");
          String additionalLibDirs = "\n"
              + "<AdditionalLibraryDirectories>"
              + sb.toString()
              + "</AdditionalLibraryDirectories>\n";
          while (idx >=0) {
            idx += "<Link>".length();
            projectContents.insert(idx, additionalLibDirs);
            idx += additionalLibDirs.length();
            idx = projectContents.indexOf("<Link>", idx);
          }
        }
        while (idx >= 0) {
          idx += linkDirTag.length();
          projectContents.insert(idx, sb.toString());
          idx = projectContents.indexOf(linkDirTag, idx);
        }
      }

      List<String> libs = getDependentLibs();
      if (libs.size() > 0) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < libs.size(); i++) {
          sb.append(libs.get(i)).append(";");
        }
        int idx = projectContents.indexOf(libTag);
        if (idx == -1) {
          // TODO: Should really parse XML
          idx = projectContents.indexOf("<Link>");
          String additionalLibs = "\n"
              + "<AdditionalDependencies>"
              + sb.toString()
              + "</AdditionalDependencies>\n";
          while (idx >= 0) {
            idx += "<Link>".length();
            projectContents.insert(idx, additionalLibs);
            idx += additionalLibs.length();
            idx = projectContents.indexOf("<Link>", idx);
          }
        }
        while (idx >= 0) {
          idx += libTag.length();
          projectContents.insert(idx, sb.toString());
          idx = projectContents.indexOf(libTag, idx);
        }
      }

      // Write out updated project file
      try {
        FileOutputStream fos = new FileOutputStream(new File(targetDir, msProjectFile));
        fos.write(projectContents.toString().getBytes());
        fos.close();
      } catch (Exception e) {
        throw new MojoExecutionException(
            "Unable to update project file: " + e.getMessage(), e);
      }

      getLog().info("Running MSBuild");
      String[] args = null;
      if (msProjectArgs != null) {
        args = msProjectArgs.split(" ");
        String[] newArgs = new String[args.length+1];
        System.arraycopy(args, 0, newArgs, 1, args.length);
        args = newArgs;
      } else {
        args = new String[1];
      }
      args[0] = msProjectFile;
      final int result = NarUtil.runCommand(getMsvc().getMSBuild().toString(),
        args, targetDir, env, getLog());
      if (result != 0) {
        throw new MojoExecutionException("'MSBuild' errorcode: " + result);
      }
    }

    copyResources(targetDir, getAOL().toString());
  }

  private StringBuffer readFile(File file) throws Exception {
    StringBuffer projectContents = null;
    try {
      projectContents = new StringBuffer();
      BufferedReader reader = new BufferedReader(new FileReader(file));
      String text = null;
      while ((text = reader.readLine()) != null) {
        projectContents.append(text).append(System.getProperty("line.separator"));
      }
      reader.close();
    } catch (Exception e) {
      throw new MojoExecutionException(
          "Unable to read project file: " + e.getMessage(), e);
    }
    return projectContents;
  }
}
