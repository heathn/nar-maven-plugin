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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.surefire.shared.lang3.function.Failable;
import org.apache.tools.ant.types.Environment.Variable;
import org.codehaus.plexus.util.StringUtils;

import com.github.maven_nar.cpptasks.CCTask;
import com.github.maven_nar.cpptasks.CompilerDef;
import com.github.maven_nar.cpptasks.LinkerDef;
import com.github.maven_nar.cpptasks.types.SystemIncludePath;


public class Msvc {

  // the home location of visual studio
  private Path home;

  private Log log = null;

  private final Set<String> paths = new LinkedHashSet<>();

  /**
   * VisualStudio Linker version. Required. The values should be:
   * <ul>
   * <li>7.1 for VS 2003</li>
   * <li>8.0 for VS 2005</li>
   * <li>9.0 for VS 2008</li>
   * <li>10.0 for VS 2010</li>
   * <li>11.0 for VS 2012</li>
   * <li>12.0 for VS 2013</li>
   * <li>14.0 for VS 2015</li>
   * <li>15.0 for VS 2017</li>
   * <li>16.0 for VS 2019</li>
   * </ul>
   */
  private String version = "";

  private Path windowsSdkHome;

  private String windowsSdkVersion = "";

  private String tempPath;

  private boolean force_requested_arch = false;

  private boolean active = false;

  private Path windowsHome = null;

  // Target architecture to use.  Determined by the configured mojo.
  private String mojoArchitecture;

  // The folder that contains the set of bin/include/lib folders
  private Path msvctoolhome;
  private Path toolPathWindowsSDK;
  private Path toolPathLinker;
  private List<Path> sdkIncludes = new ArrayList<>();
  private List<Path> sdkLibs = new ArrayList<>();
  private Set<String> libsRequired = Stream.of("ucrt", "um", "shared", "winrt")
      .collect(Collectors.toSet());

  private enum CrossCompilers {
    x86, x64
    // , arm
    , x86_x64, x86_arm, x64_x86, x64_arm
    // arm cross compilers to x86/x64?
  }

  static boolean isMSVC(final AbstractNarMojo mojo) {
    return isMSVC(mojo.getLinker().getName());
  }

  static boolean isMSVC(final String name) {
    return "msvc".equalsIgnoreCase(name);
  }

  public Msvc(final AbstractNarMojo mojo) throws MojoFailureException, MojoExecutionException {
    log = mojo.getLog();
    active = NarUtil.isWindows() && OS.WINDOWS.equals(mojo.getOS()) && isMSVC(mojo);
    if (active) {
        windowsHome = Path.of(System.getenv("SystemRoot"));
        mojoArchitecture = mojo.getArchitecture();

        // complex objects don't support configuration directly from properties
        // so the same configuration is now duplicated to the mojo to allow
        // configuration or property setting.  Alternate might be to just read
        // the property, however that wouldn't get documented in API
        // version = mojo.getMavenProject().getProperties().getProperty("nar.windows.msvc.version", version);
        final String versionProperty = mojo.getWindowsMsvcVersion();
        if (versionProperty != null) {
          version = versionProperty;
        }

        final String msvcDir = mojo.getWindowsMsvcDir();
        if (msvcDir != null) {
          home = Path.of(msvcDir);
        } else {
          initVisualStudio();
          if (!version.isEmpty()) {
            log.debug(String.format(" Using VisualStudio %1s home %2s ", version, home));
          } else {
            final TextStream out = new StringTextStream();
            final TextStream err = new StringTextStream();
            final TextStream dbg = new StringTextStream();

            NarUtil.runCommand("link",
                List.of("/?"), null, null, out, err, dbg, null, true);
            final Pattern p = Pattern.compile("(\\d+\\.\\d+)\\.\\d+(\\.\\d+)?");
            final Matcher m = p.matcher(out.toString());
            if (m.find()) {
              version = m.group(1);
              log.debug(String
                  .format(" VisualStudio Not found but link runs and reports version %1s (%2s)", version, m.group(0)));
              // just use whatever is configured in the environment.
              return;
            } else {
              throw new MojoExecutionException(
                  "msvc.version not specified and no VS7 SxS or VS<Version>COMNTOOLS environment variable can be found");
            }
          }
        }

        msvctoolhome = VCToolHome();

        final String windowsSdkVersionProperty = mojo.getWindowsSdkVersion();
        if (windowsSdkVersionProperty != null) {
          windowsSdkVersion = windowsSdkVersionProperty;
        }

        final String sdkDir = mojo.getWindowsSdkDir();
        if (sdkDir != null) {
          windowsSdkHome = Path.of(sdkDir);
        } else {
          initWindowsSdk();
        }

        final String osArchitecture = NarUtil.getArchitecture(null);
        // On 64 bit OS either 32 or 64 bit tools can be used
        // * 32 bit tools in hostx86/
        // * 64 bit tools in hostx64/

        // default use compiler tools match os
        // - os x86: mojo(x86 / x86_x64);
        // - os x64: mojo(x64_x86 / x64);
        // use only 32 bit compiler tools - treat x64 as if os is x86
        // - mojo(x86 / x86_x64)
        // force_requested_arch - on 64 bit host match mojo host tools to target
        // platform
        // - os x64: mojo(x86 / x64)
        // - os x86: mojo(x86 / x86_x64) same as default
        CrossCompilers compiler;
        if (force_requested_arch) {
          if ("amd64".equals(mojoArchitecture)) {
            if ("amd64".equals(osArchitecture)) {
              compiler = CrossCompilers.x64;
            } else {
              compiler = CrossCompilers.x86_x64;
            }
            // } else if ("arm".equals(mojoArchitecture)) {
            // compiler = CrossCompilers.arm;
          } else {// else if ("x86".equals(mojoArchitecture))
            compiler = CrossCompilers.x86;
          }
        } else {
          if ("amd64".equals(osArchitecture)) {
            if ("amd64".equals(mojoArchitecture)) {
              compiler = CrossCompilers.x64;
            } else if ("arm".equals(mojoArchitecture)) {
              compiler = CrossCompilers.x64_arm;
            } else {
              compiler = CrossCompilers.x64_x86;
            }
          } else {
            if ("amd64".equals(mojoArchitecture)) {
              compiler = CrossCompilers.x86_x64;
            } else if ("arm".equals(mojoArchitecture)) {
              compiler = CrossCompilers.x86_arm;
            } else {
              compiler = CrossCompilers.x86;
            }
          } // todo arm
        }

        initPath(compiler);
        addWindowsSDKPaths();
        addWindowsPaths();
    }
  }

  public Path getHome() {
    return home;
  }

  public Path getMSBuild() {
    return Path.of(home.toString(), "MSBuild", "Current", "Bin", "MSBuild.exe");
  }

  public String getVersion() {
    return version;
  }

  public String getWindowsSdkVersion() {
    return windowsSdkVersion;
  }

  public Variable getPathVariable() {
    if (paths.isEmpty())
      return null;
    final Variable pathVariable = new Variable();
    pathVariable.setKey("PATH");
    pathVariable.setValue(StringUtils.join(paths.iterator(), File.pathSeparator));
    return pathVariable;
  }

  @Override
  public String toString() {
    return "VS Home-" + home + "\nSDKHome-" + windowsSdkHome;
  }

  public Path getToolPath() {
    return toolPathLinker;
  }

  public Path getSDKToolPath() {
    return toolPathWindowsSDK;
  }

  public void setToolPath(CompilerDef compilerDef, String name) {
    if ("res".equals(name) || "mc".equals(name) || "idl".equals(name)) {
      compilerDef.setToolPath(toolPathWindowsSDK.toString());
    } else {
      compilerDef.setToolPath(toolPathLinker.toString());
    }
  }

  public void configureCCTask(CCTask task) throws MojoExecutionException {
    if (active) {
      addIncludePath(task, msvctoolhome, "include");
      addIncludePath(task, msvctoolhome, "atlmfc/include");
      if (compareVersion(windowsSdkVersion, "7.1A") <= 0) {
        if (version.equals("8.0")) {
          // For VS 2005 the version of SDK is 2.0, but it needs more paths
          for (Path sdkInclude : sdkIncludes) {
            addIncludePathToTask(task, sdkInclude);
            log.debug(" configureCCTask add to Path-- " + sdkInclude.toAbsolutePath());
          }
        } else {
          addIncludePath(task, windowsSdkHome, "include");
        }
      } else {
        for (Path sdkInclude : sdkIncludes) {
          addIncludePathToTask(task, sdkInclude);
        }
      }

      task.addEnv(getPathVariable());
      // TODO: supporting running with clean environment - addEnv sets
      // newEnvironemnt by default
      // task.setNewenvironment(false);
      Variable envVariable = new Variable();
      // cl needs SystemRoot env var set, otherwise D8037 is raised (bogus
      // message)
      // - https://msdn.microsoft.com/en-us/library/bb385201.aspx
      // -
      // http://stackoverflow.com/questions/10560779/cl-exe-when-launched-via-createprocess-does-not-seem-to-have-write-permissions
      envVariable.setKey("SystemRoot");
      envVariable.setValue(windowsHome.toAbsolutePath().toString());
      task.addEnv(envVariable);
      // cl needs TMP otherwise D8050 is raised c1xx.dll
      envVariable = new Variable();
      envVariable.setKey("TMP");
      envVariable.setValue(getTempPath());
      task.addEnv(envVariable);
    }
  }

  public void configureLinker(final LinkerDef linker) throws MojoExecutionException {
    if (active) {

      // Windows SDK
      String sdkArch = mojoArchitecture;
      if ("amd64".equals(mojoArchitecture)) {
        sdkArch = "x64";
      }

      // Visual Studio
      if (compareVersion(version, "15.0") < 0) {
        if ("x86".equals(mojoArchitecture)) {
          linker.addLibraryDirectory(msvctoolhome, "lib");
          linker.addLibraryDirectory(msvctoolhome, "atlmfc/lib");
        } else {
          linker.addLibraryDirectory(msvctoolhome, "lib/" + mojoArchitecture);
          linker.addLibraryDirectory(msvctoolhome, "atlmfc/lib/" + mojoArchitecture);
        }
      } else {
        linker.addLibraryDirectory(msvctoolhome, "lib/" + sdkArch);
        linker.addLibraryDirectory(msvctoolhome, "atlmfc/lib/" + sdkArch);
      }

      // 6 lib ?+ lib/x86 or lib/x64
      if (compareVersion(windowsSdkVersion, "8.0") < 0) {
        if ("x86".equals(mojoArchitecture)) {
          linker.addLibraryDirectory(windowsSdkHome, "lib");
        } else {
          linker.addLibraryDirectory(windowsSdkHome, "lib/" + sdkArch);
        }
      } else {
        for (Path sdkLib : sdkLibs) {
          linker.addLibraryDirectory(sdkLib, sdkArch);
        }
      }
    }
  }

  private boolean addIncludePath(final CCTask task, final Path base, final String subDirectory)
      throws MojoExecutionException {
    if (base == null) {
      return false;
    }
    final Path file = base.resolve(subDirectory);
    if (Files.exists(file))
      return addIncludePathToTask(task, file);

    return false;
  }

  private boolean addIncludePathToTask(final CCTask task, final Path file) throws MojoExecutionException {
    final SystemIncludePath includePath = task.createSysIncludePath();
    final String fullPath = file.toAbsolutePath().toString();
    includePath.setPath(fullPath);
    return true;
  }

  private boolean addPath(final Path path) {
    if (path != null) {
      if (Files.exists(path)) {
        paths.add(path.toAbsolutePath().toString());
        return true;
      }
    }
    return false;
  }

  private String getTempPath() {
    if (null == tempPath) {
      tempPath = System.getenv("TMP");
      if (tempPath == null)
        tempPath = System.getenv("TEMP");
      if (tempPath == null)
        tempPath = "C:\\Temp";
    }
    return tempPath;
  }

  private void initPath(CrossCompilers compiler) throws MojoExecutionException {

    Boolean found = true;
    if (compareVersion(version, "15.0") < 0) {
      switch (compiler) {
        case x86:
          // compile using x86 tools.
          toolPathLinker = msvctoolhome.resolve("bin");
          break;
        case x86_x64:
          // cross compile x64 using x86 tools
          toolPathLinker = msvctoolhome.resolve("bin/x86_amd64");
          addPath(msvctoolhome.resolve("bin"));
          break;
        case x86_arm:
          // cross compile arm using x86 tools
          toolPathLinker = msvctoolhome.resolve("bin/x86_arm");
          addPath(msvctoolhome.resolve("bin"));
          break;
        case x64:
          // compile using x64 tools
          toolPathLinker = msvctoolhome.resolve("bin/amd64");
          break;
        case x64_x86:
          // cross compile x86 using x64 tools
          toolPathLinker = msvctoolhome.resolve("bin/amd64_x86");
          addPath(msvctoolhome.resolve("bin/amd64"));
          break;
        case x64_arm:
          // cross compile arm using x64 tools
          toolPathLinker = msvctoolhome.resolve("bin/amd64_arm");
          addPath(msvctoolhome.resolve("bin/amd64"));
          break;
      }
    } else {
      switch (compiler) {
        case x86: // compile using x86 tools.
          toolPathLinker = msvctoolhome.resolve("bin/HostX86/x86");
          break;
        case x86_x64:
          // cross compile x64 using x86 tools
          toolPathLinker = msvctoolhome.resolve("bin/HostX86/x64");
          addPath(msvctoolhome.resolve("bin/HostX86/x86"));
          break;
        case x86_arm:
          // cross compile arm using x86 tools
          toolPathLinker = msvctoolhome.resolve("bin/HostX86/arm");
          addPath(msvctoolhome.resolve("bin/HostX86/x86"));
          break;
        case x64:
          // compile using x64 tools
          toolPathLinker = msvctoolhome.resolve("bin/HostX64/x64");
          break;
        case x64_x86:
          // cross compile x86 using x64 tools
          toolPathLinker = msvctoolhome.resolve("bin/HostX64/x86");
          addPath(msvctoolhome.resolve("bin/HostX64/x64"));
          break;
        case x64_arm:
          // cross compile arm using x64 tools
          toolPathLinker = msvctoolhome.resolve("bin/HostX64/arm");
          addPath(msvctoolhome.resolve("bin/HostX64/x64"));
          break;
      }
      found = addPath(toolPathLinker);
    }
    if (!found) {
      throw new MojoExecutionException("Unable to find bin folder for architecture " + compiler.name() + ".\n");
    }

    // tools that are more generic
    if (compareVersion(version, "15.0") < 0) {
      addPath(msvctoolhome.resolve("VCPackages"));
      addPath(home.resolve("Common7/Tools"));
      addPath(home.resolve("Common7/IDE"));
    } else {
      addPath(home.resolve("Common7/IDE/VC/VCPackages"));
      addPath(home.resolve("Common7/IDE/"));
      addPath(home.resolve("Common7/Tools"));
    }
  }

  private void addWindowsSDKPaths() throws MojoExecutionException {
    final String osArchitecture = NarUtil.getArchitecture(null);
    final String versionPart = compareVersion(windowsSdkVersion, "10") < 0 ? "" : windowsSdkVersion;

    // 64 bit tools if present are preferred
    if (compareVersion(windowsSdkVersion, "7.1A") <= 0) {
      if ("amd64".equals(osArchitecture)) {
        addPath(windowsSdkHome.resolve("bin/x64"));
      }
      addPath(windowsSdkHome.resolve("bin"));
    } else {
      if ("amd64".equals(osArchitecture)) {
        addPath(windowsSdkHome.resolve(Path.of("bin", versionPart, "x64")));
      }
      addPath(windowsSdkHome.resolve(Path.of("bin", versionPart, "x86")));
    }

    if ("amd64".equals(mojoArchitecture)) {
      toolPathWindowsSDK = windowsSdkHome.resolve(Path.of("bin", versionPart, "x64")).toAbsolutePath();
    } else if (compareVersion(windowsSdkVersion, "7.1A") <= 0) {
      toolPathWindowsSDK = windowsSdkHome.resolve("bin").toAbsolutePath();
    } else {
      toolPathWindowsSDK = windowsSdkHome.resolve(Path.of("bin", versionPart, "x86")).toAbsolutePath();
    }

    log.debug(String.format(" Using WindowSDK bin %1s", toolPathWindowsSDK));
  }

  private void addWindowsPaths() throws MojoExecutionException {
    // clearing the path, add back the windows system folders
    addPath(windowsHome.resolve("System32"));
    addPath(windowsHome);
    addPath(windowsHome.resolve("System32/wbem"));
  }

  private static TreeMap<String, Object> visualStudioVS7SxS(com.sun.jna.platform.win32.WinReg.HKEY root, String key) {
    com.sun.jna.platform.win32.WinReg.HKEYByReference phkKey = new com.sun.jna.platform.win32.WinReg.HKEYByReference();
    int rc = com.sun.jna.platform.win32.Advapi32.INSTANCE.RegOpenKeyEx(root, key, 0,
        com.sun.jna.platform.win32.WinNT.KEY_READ | com.sun.jna.platform.win32.WinNT.KEY_WOW64_32KEY, phkKey);
    if (rc != com.sun.jna.platform.win32.W32Errors.ERROR_SUCCESS) {
      throw new com.sun.jna.platform.win32.Win32Exception(rc);
    }
    try {
      return com.sun.jna.platform.win32.Advapi32Util.registryGetValues(phkKey.getValue());
    } finally {
      rc = com.sun.jna.platform.win32.Advapi32.INSTANCE.RegCloseKey(phkKey.getValue());
      if (rc != com.sun.jna.platform.win32.W32Errors.ERROR_SUCCESS) {
        throw new com.sun.jna.platform.win32.Win32Exception(rc);
      }
    }
  }

  private void initVisualStudio() throws MojoFailureException, MojoExecutionException {
    log.debug(" -- Searching for usable VisualStudio ");

    /* New env values used by VS 2019
    VCIDEInstallDir=C:\Program Files (x86)\Microsoft Visual Studio\2019\Community\Common7\IDE\VC\
    VCINSTALLDIR=C:\Program Files (x86)\Microsoft Visual Studio\2019\Community\VC\
    VCToolsInstallDir=C:\Program Files (x86)\Microsoft Visual Studio\2019\Community\VC\Tools\MSVC\14.28.29333\
    VCToolsRedistDir=C:\Program Files (x86)\Microsoft Visual Studio\2019\Community\VC\Redist\MSVC\14.28.29325\
    VCToolsVersion=14.28.29333
    VisualStudioVersion=16.0
    VS160COMNTOOLS=C:\Program Files (x86)\Microsoft Visual Studio\2019\Community\Common7\Tools\
    VSCMD_ARG_app_plat=Desktop
    VSCMD_ARG_HOST_ARCH=x64
    VSCMD_ARG_TGT_ARCH=x64
    VSCMD_VER=16.8.5
    VSINSTALLDIR=C:\Program Files (x86)\Microsoft Visual Studio\2019\Community\
    WindowsLibPath=C:\Program Files (x86)\Windows Kits\10\UnionMetadata\10.0.18362.0;C:\Program Files (x86)\Windows Kits\10\References\10.0.18362.0
    WindowsSdkBinPath=C:\Program Files (x86)\Windows Kits\10\bin\
    WindowsSdkDir=C:\Program Files (x86)\Windows Kits\10\
    WindowsSDKLibVersion=10.0.18362.0\
    WindowsSdkVerBinPath=C:\Program Files (x86)\Windows Kits\10\bin\10.0.18362.0\
    WindowsSDKVersion=10.0.18362.0\ 
    */
    // don't attempt to subvert the version setting from the pom
    if (version == null || version.trim().length() < 1) {
      // examine the VS version variable before attempting to read older registry entries which are not supported in later VS versions
      String envVisualStudioVersion = System.getenv("VisualStudioVersion");
      if (envVisualStudioVersion != null && envVisualStudioVersion.length() > 3) {
        this.version = envVisualStudioVersion;
      }
    }
    log.debug("Requested Linker version is  \"" + version + "\"");
    if (version != null && version.trim().length() > 1) {
      String internalVersion;
      Pattern r = Pattern.compile("(\\d+)\\.*(\\d)");
      Matcher matcher = r.matcher(version);
      if (matcher.find()) {
        internalVersion = matcher.group(1) + matcher.group(2);
        version = matcher.group(1) + "." + matcher.group(2);
      } else {
        throw new MojoExecutionException("msvc.version must be the internal version in the form 10.0 or 120");
      }
      if (home == null) {
        // HKLM (32 bit) - HKLM\SOFTWARE\Microsoft\VisualStudio\SxS\VS7
        // @<Major.Minor>
        try {
          home = Path.of(NarUtil.registryGet32StringValue(
            com.sun.jna.platform.win32.WinReg.HKEY_LOCAL_MACHINE,
              "SOFTWARE\\Microsoft\\VisualStudio\\SxS\\VS7", version));
        } catch (Exception e) {
          // is there interest in knowing about an Win32Exception here for newer VS versions?
        }
      }
      if (home == null || Files.notExists(home)) {
        final String commontToolsVar = System.getenv("VS" + internalVersion + "COMNTOOLS");
        if (commontToolsVar != null && commontToolsVar.trim().length() > 0) {
          final Path commonToolsDirectory = Path.of(commontToolsVar);
          if (Files.exists(commonToolsDirectory)) {
            home = commonToolsDirectory.getParent().getParent();
          }
        }
      }
      log.debug(String.format(" VisualStudio %1s (%2s) found %3s ", version, internalVersion, home));
    } else {
      // reset
      this.version = "";
      // Check for vswhere first.  It has been included with VisualStudio
      // since at least 2017.
      if (!initFromVSWhere()) {
        // First search registry for installed items, more reliable than environment.
        for (final Entry<String, Object> entry : visualStudioVS7SxS(com.sun.jna.platform.win32.WinReg.HKEY_LOCAL_MACHINE,
            "SOFTWARE\\Microsoft\\VisualStudio\\SxS\\VS7").entrySet()) {
          final String newestVersion = entry.getKey();
          final String value = entry.getValue().toString();
          log.debug(String.format(" VisualStudio %1s found SxS %3s ", newestVersion, value));
          if (versionStringComparator.compare(newestVersion, this.version) > 0) {
            final Path vsDirectory = Path.of(value);
            if (Files.exists(vsDirectory)) {
              this.version = newestVersion;
              home = vsDirectory;
            }
          }
        }
      }

      // Search environment for common tools which is within VS.
      final Pattern versionPattern = Pattern.compile("VS(\\d+)(\\d)COMNTOOLS");
      for (final Entry<String, String> entry : System.getenv().entrySet()) {
        final String key = entry.getKey();
        final String value = entry.getValue();
        final Matcher matcher = versionPattern.matcher(key);
        if (matcher.matches()) {
          final String newestVersion = matcher.group(1) + "." + matcher.group(2);
          log.debug(String.format(" VisualStudio %1s (%2s) common tools found %3s ", newestVersion,
              matcher.group(1) + matcher.group(2), value));
          if (versionStringComparator.compare(newestVersion, this.version) > 0) {
            final Path commonToolsDirectory = Path.of(value);
            if (Files.exists(commonToolsDirectory)) {
              this.version = newestVersion;
              home = commonToolsDirectory.getParent().getParent();
            }
          }
        }
      }
    }
  }

  private boolean initFromVSWhere() throws MojoExecutionException,
      MojoFailureException {
    final TextStream out = new StringTextStream();
    final TextStream err = new StringTextStream();
    final TextStream dbg = new StringTextStream();

    String programDir = System.getenv("PROGRAMFILES(X86)");
    final Path installDir = Path.of(programDir,
      "Microsoft Visual Studio", "Installer");

    try {
      // NOTE: Remember that MSVC may be installed but it may not have
      // installed the C++ components needed by this plugin.  Running vswhere
      // and requiring the Microsoft.VisualStudio.Component.VC.Tools.x86.x64
      // component will only list those MSVC environments which have the
      // C++ build system installed.
      // TODO: Allow the version to be overridden by property.
      NarUtil.runCommand("vswhere",
          List.of("-latest", "-requires",
              "Microsoft.VisualStudio.Component.VC.Tools.x86.x64"),
          installDir, null, out, err, dbg, null, false);
    } catch (MojoExecutionException ex) {
      return false;
    }
    try (BufferedReader reader =
        new BufferedReader(new StringReader(out.toString()))) {
      String line = null;
      while ((line = reader.readLine()) != null) {
        if (line.startsWith("installationPath")) {
          home = Path.of(line.split(": ")[1]);
        } else if (line.startsWith("installationVersion")) {
          version = line.split(": ")[1];
        }
      }
    } catch (IOException ex) {
      throw new MojoExecutionException(ex, "Unable to parse vswhere output",
        "Unable to parse vswhere output");
    }

    if (home == null || version == null) {
      return false;
    }
    return true;
  }

  private final Comparator<String> versionStringComparator = new Comparator<String>() {
    @Override public int compare(String o1, String o2) {
      DefaultArtifactVersion version1 = new DefaultArtifactVersion(o1);
      DefaultArtifactVersion version2 = new DefaultArtifactVersion(o2);
      return version1.compareTo(version2);
    }
  };

  private final Comparator<Path> versionComparator = new Comparator<Path>() {
    @Override
    public int compare(Path o1, Path o2) {
      // will be sorted smallest first, so we need to invert the order of
      // the objects
      String firstDir = o2.getFileName().toString(), secondDir = o1.getFileName().toString();
      if (firstDir.charAt(0) == 'v') {
        // remove 'v' and 'A' at the end
        firstDir = firstDir.substring(1, firstDir.length() - 1);
        secondDir = secondDir.substring(1, secondDir.length() - 1);
      }
      // impossible that two dirs are the same
      String[] firstVersionString = firstDir.split("\\."), secondVersionString = secondDir.split("\\.");

      int maxIdx = Math.min(firstVersionString.length, secondVersionString.length);
      int deltaVer;
      try {
        for (int i = 0; i < maxIdx; i++)
          if ((deltaVer = Integer.parseInt(firstVersionString[i]) - Integer.parseInt(secondVersionString[i])) != 0)
            return deltaVer;

      } catch (NumberFormatException e) {
        return firstDir.compareTo(secondDir);
      }
      if (firstVersionString.length > maxIdx) // 10.0.150 > 10.0
        return 1;
      else if (secondVersionString.length > maxIdx) // 10.0 < 10.0.150
        return -1;
      return 0; // impossible that they are the same
    }
  };
  private boolean foundSDK = false;

  private void initWindowsSdk() throws MojoExecutionException {
    // In ancient times the Windows SDK was part of the Visual Studio installation
    //  - version identity was by release qaurter, or by visual studio service pack level
    // In the middle ages there was a Standalone and a Visual Studio variation (denoted by A)
    // - version identify was specific to windows version, 6.0//6.1/7.0/7.1/8.0/8.1
    // Recently there is only standalone, the management is incremental to match evergreen releases
    // - version 10.  with many more specific subfolders

    // VS 2005 - There are built in SDK files included in the VS installation
    // TODO: for some reason this is hard coded to only use the WindowsSDK installed with VS...
    if (compareVersion(version, "8.0") <= 0) { // builtInWindowsSDK

      Path VCINSTALLDIR = home.resolve("VC");

      legacySDK(VCINSTALLDIR.resolve("PlatformSDK"));
      // Additionally include the .Net includes
      Path SDKIncludeDir = Path.of(VCINSTALLDIR.toAbsolutePath().toString(), "SDK", "v2.0", "include");
      sdkIncludes.add(SDKIncludeDir);
    } else {
      if (windowsSdkVersion != null && windowsSdkVersion.trim().equals(""))
        windowsSdkVersion = null;

      log.debug(" -- Searching for usable WindowSDK ");
      // newer first: 10 -> 8.1 -> 8.0 -> 7.1 and look for libs specified

      for (final Path directory : Arrays.asList(
          Path.of("C:/Program Files (x86)/Windows Kits"),
          Path.of("C:/Program Files (x86)/Microsoft SDKs/Windows"),
          Path.of("C:/Program Files/Windows Kits"),
          Path.of("C:/Program Files/Microsoft SDKs/Windows"))) {
        if (Files.exists(directory)) {
          try {
            Files.list(directory)
                .sorted(versionComparator)
                .filter(f -> Files.exists(f.resolve("Include")))
                .forEach(Failable.asConsumer(kitDirectory -> {
                  // legacy SDK
                  String kitVersion = kitDirectory.getFileName().toString();
                  if (kitVersion.charAt(0) == 'v') {
                    kitVersion = kitVersion.substring(1);
                  }

                  if (windowsSdkVersion != null && compareVersion(kitVersion, windowsSdkVersion) != 0)
                    return; // skip versions not identical to exact version

                  log.debug(String.format(" WindowSDK %1s found %2s", kitVersion, kitDirectory.toAbsolutePath()));
                  if (kitVersion.matches("\\d+\\.\\d+?[A-Z]?")) {
                    // windows <= 8.1
                    legacySDK(kitDirectory);
                  } else if (kitVersion.matches("\\d+?")) {
                    // windows 10 SDK supports
                    addNewSDKLibraries(kitDirectory);
                  }
                }));
          } catch (IOException | RuntimeException e) {
            throw new MojoExecutionException(e);
          }

          if (libsRequired.size() == 0) // need it here to break out of the
            // outer loop
            break;
        }
      }

      /*
      if (!foundSDK) { // Search for SDK with lower versions
        for (final Path directory : Arrays.asList(
            Paths.get("C:/Program Files (x86)/Windows Kits"),
            Paths.get("C:/Program Files (x86)/Microsoft SDKs/Windows"),
            Paths.get("C:/Program Files/Windows Kits"),
            Paths.get("C:/Program Files/Microsoft SDKs/Windows"))) {
          if (Files.exists(directory)) {
            try {
              Files.list(directory)
                  .sorted(versionComparator)
                  .filter(f -> Files.exists(f.resolve("Include")))
                  .forEach(Utils.throwingConsumerWrapper(kitDirectory -> {
                    // legacy SDK
                    String kitVersion = kitDirectory.getFileName().toString();
                    if (kitVersion.charAt(0) == 'v') {
                      kitVersion = kitVersion.substring(1);
                    }

                    if (windowsSdkVersion != null && compareVersion(kitVersion, windowsSdkVersion) > 0) {
                      return; // skip versions higher than the previous version
                    }

                    log.debug(String.format(" WindowSDK %1s found %2s", kitVersion, kitDirectory.toAbsolutePath()));
                    if (kitVersion.matches("\\d+\\.\\d+?[A-Z]?")) {
                      // windows <= 8.1
                      legacySDK(kitDirectory);
                    } else if (kitVersion.matches("\\d+?")) {
                      // windows 10 SDK supports
                      addNewSDKLibraries(kitDirectory);
                    }
                  }));
            } catch (IOException | RuntimeException e) {
              throw new MojoExecutionException(e);
            }
            if (libsRequired.size() == 0) // need it here to break out of the
              // outer loop
              break;
          }
        }
      }*/
    }

    if (!foundSDK)
      throw new MojoExecutionException("msvc.windowsSdkVersion not specified and versions cannot be found");
    log.debug(String.format(" Using WindowSDK %1s found %2s", windowsSdkVersion, windowsSdkHome));
  }

  private void addNewSDKLibraries(final Path kitDirectory) throws MojoExecutionException {
    // multiple installs
    Optional<Path> kitVersionDirectory;
    try {
      kitVersionDirectory = Files.list(kitDirectory.resolve("Include"))
          .sorted(versionComparator)
          .filter(dir -> Files.exists(dir.resolve("ucrt")))
          .findFirst();
    } catch (IOException e) {
      throw new MojoExecutionException(e);
    }

    if (kitVersionDirectory.isPresent()) {
      String version = kitVersionDirectory.get().getFileName().toString();
      log.debug(String.format(" Latest Win %1s KitDir at %2s", kitVersionDirectory.get().getFileName(),
          kitVersionDirectory.get().toAbsolutePath()));
      // add the libraries found:
      Path includeDir = kitDirectory.resolve(Path.of("Include", version));
      Path libDir = kitDirectory.resolve(Path.of("Lib", version));
      windowsSdkVersion = version;
      addSDKLibs(includeDir, libDir);
      setKit(kitDirectory);
    }
  }

  private void setKit(Path home) {
    if (!foundSDK) {
      if (windowsSdkVersion == null)
        windowsSdkVersion = home.getFileName().toString();
      if (windowsSdkHome == null)
        windowsSdkHome = home;
      foundSDK = true;
    }
  }

  private void legacySDK(final Path kitDirectory) throws MojoExecutionException {
    Path includeDir = kitDirectory.resolve("Include");
    Path libDir = kitDirectory.resolve("Lib");
    if (Files.exists(includeDir) && Files.exists(libDir)) {
      try {
        Path usableLibDir = Files.list(libDir)
            .filter(f -> Files.exists(f.resolve("um")))
            .findFirst()
            .orElse(Files.list(libDir).findFirst().orElseThrow());
        addSDKLibs(includeDir, usableLibDir);
        setKit(kitDirectory);
      } catch (IOException e) {
        throw new MojoExecutionException(e);
      }
    }
  }

  private void addSDKLibs(Path includeDir, Path libdir) throws MojoExecutionException {
    try {
      Files.list(includeDir)
          .forEach(libIncludeDir -> {
            // <libName> <include path> <lib path>
            if (libsRequired.remove(libIncludeDir.getFileName().toString())) {
              log.debug(String
                  .format(" Using directory %1s for library %2s", libIncludeDir.toAbsolutePath(), libIncludeDir.getFileName()));
              sdkIncludes.add(libIncludeDir);
              sdkLibs.add(libdir.resolve(libIncludeDir.getFileName()));
            }
          });
    } catch (IOException e) {
      throw new MojoExecutionException(e);
    }
  }

  private Path VCToolHome() {
    if (compareVersion(version, "15.0") < 0) {
      return home.resolve("VC");
    } else {
      final Path msvcversionFile = Path.of(home.toString(), "VC", "Auxiliary", "Build", "Microsoft.VCToolsVersion.default.txt");
      String msvcversion = "14.10.25017"; // what to do if we can't read the
      // current value??
      try (BufferedReader brTest = Files.newBufferedReader(msvcversionFile)) {
        msvcversion = brTest.readLine().trim();
      } catch (FileNotFoundException e) {
        e.printStackTrace();
      } catch (IOException e) {
        e.printStackTrace();
      }
      return Path.of(home.toString(), "VC", "Tools", "MSVC", msvcversion);
    }
  }

  public int compareVersion(Object o1, Object o2) {
    String version1 = (String) o1;
    String version2 = (String) o2;

    VersionTokenizer tokenizer1 = new VersionTokenizer(version1);
    VersionTokenizer tokenizer2 = new VersionTokenizer(version2);

    int number1 = 0, number2 = 0;
    String suffix1 = "", suffix2 = "";

    while (tokenizer1.MoveNext()) {
      if (!tokenizer2.MoveNext()) {
        do {
          number1 = tokenizer1.getNumber();
          suffix1 = tokenizer1.getSuffix();
          if (number1 != 0 || suffix1.length() != 0) {
            // Version one is longer than number two, and non-zero
            return 1;
          }
        } while (tokenizer1.MoveNext());

        // Version one is longer than version two, but zero
        return 0;
      }

      number1 = tokenizer1.getNumber();
      suffix1 = tokenizer1.getSuffix();
      number2 = tokenizer2.getNumber();
      suffix2 = tokenizer2.getSuffix();

      if (number1 < number2) {
        // Number one is less than number two
        return -1;
      }
      if (number1 > number2) {
        // Number one is greater than number two
        return 1;
      }

      boolean empty1 = suffix1.length() == 0;
      boolean empty2 = suffix2.length() == 0;

      if (empty1 && empty2)
        continue; // No suffixes
      if (empty1)
        return 1; // First suffix is empty (1.2 > 1.2b)
      if (empty2)
        return -1; // Second suffix is empty (1.2a < 1.2)

      // Lexical comparison of suffixes
      int result = suffix1.compareTo(suffix2);
      if (result != 0)
        return result;

    }
    if (tokenizer2.MoveNext()) {
      do {
        number2 = tokenizer2.getNumber();
        suffix2 = tokenizer2.getSuffix();
        if (number2 != 0 || suffix2.length() != 0) {
          // Version one is longer than version two, and non-zero
          return -1;
        }
      } while (tokenizer2.MoveNext());

      // Version two is longer than version one, but zero
      return 0;
    }
    return 0;
  }

  // VersionTokenizer.java
  class VersionTokenizer {
    private final String _versionString;
    private final int _length;

    private int _position;
    private int _number;
    private String _suffix;
    private boolean _hasValue;

    public int getNumber() {
      return _number;
    }

    public String getSuffix() {
      return _suffix;
    }

    public boolean hasValue() {
      return _hasValue;
    }

    public VersionTokenizer(String versionString) {
      if (versionString == null)
        throw new IllegalArgumentException("versionString is null");

      _versionString = versionString;
      _length = versionString.length();
    }

    public boolean MoveNext() {
      _number = 0;
      _suffix = "";
      _hasValue = false;

      // No more characters
      if (_position >= _length)
        return false;

      _hasValue = true;

      while (_position < _length) {
        char c = _versionString.charAt(_position);
        if (c < '0' || c > '9')
          break;
        _number = _number * 10 + (c - '0');
        _position++;
      }

      int suffixStart = _position;

      while (_position < _length) {
        char c = _versionString.charAt(_position);
        if (c == '.')
          break;
        _position++;
      }

      _suffix = _versionString.substring(suffixStart, _position);

      if (_position < _length)
        _position++;

      return true;
    }
  }
}
