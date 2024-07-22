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
package com.github.maven_nar.cpptasks.borland;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import com.github.maven_nar.cpptasks.CCTask;
import com.github.maven_nar.cpptasks.TargetMatcher;
import com.github.maven_nar.cpptasks.VersionInfo;
import com.github.maven_nar.cpptasks.compiler.CommandLineLinker;
import com.github.maven_nar.cpptasks.compiler.CommandLineLinkerConfiguration;
import com.github.maven_nar.cpptasks.compiler.LinkType;
import com.github.maven_nar.cpptasks.compiler.Linker;
import com.github.maven_nar.cpptasks.platforms.WindowsPlatform;
import com.github.maven_nar.cpptasks.types.LibraryTypeEnum;

/**
 * Adapter for the Borland(r) ilink32 linker
 *
 * @author Curt Arnold
 */
public final class BorlandLinker extends CommandLineLinker {
  private static final BorlandLinker dllLinker = new BorlandLinker(".dll");
  private static final BorlandLinker instance = new BorlandLinker(".exe");

  public static BorlandLinker getInstance() {
    return instance;
  }

  private BorlandLinker(final String outputSuffix) {
    super("ilink32", "-r", new String[] {
        ".obj", ".lib", ".res"
    }, new String[] {
        ".map", ".pdb", ".lnk"
    }, outputSuffix, false, null);
  }

  protected void addBase(final long base, final Vector<String> args) {
    if (base >= 0) {
      final String baseAddr = Long.toHexString(base);
      args.addElement("-b:" + baseAddr);
    }
  }

  protected void addEntry(final String entry, final Vector<String> args) {
  }

  protected void addFixed(final Boolean fixed, final Vector<String> args) {
  }

  protected void addImpliedArgs(final boolean debug, final LinkType linkType, final Vector<String> args) {
    if (linkType.isExecutable()) {
      if (linkType.isSubsystemConsole()) {
        args.addElement("/ap");
      } else {
        if (linkType.isSubsystemGUI()) {
          args.addElement("/Tpe");
        }
      }
    }
    if (linkType.isSharedLibrary()) {
      args.addElement("/Tpd");
      args.addElement("/Gi");
    }
    if (debug) {
      args.addElement("-v");
    }
  }

  protected void addIncremental(final boolean incremental, final Vector<String> args) {
  }

  protected void addMap(final boolean map, final Vector<String> args) {
    if (!map) {
      args.addElement("-x");
    }
  }

  protected void addStack(final int stack, final Vector<String> args) {
    if (stack >= 0) {
      final String stackStr = Integer.toHexString(stack);
      args.addElement("-S:" + stackStr);
    }
  }

  /**
   * Adds source or object files to the bidded fileset to
   * support version information.
   *
   * @param versionInfo
   *          version information
   * @param linkType
   *          link type
   * @param isDebug
   *          true if debug build
   * @param outputFile
   *          name of generated executable
   * @param objDir
   *          directory for generated files
   * @param matcher
   *          bidded fileset
   */
  @Override
  public void addVersionFiles(final VersionInfo versionInfo, final LinkType linkType, final Path outputFile,
      final boolean isDebug, final Path objDir, final TargetMatcher matcher) throws IOException {
    WindowsPlatform.addVersionFiles(versionInfo, linkType, outputFile, isDebug, objDir, matcher);
  }

  @Override
  public String getCommandFileSwitch(final Path commandFile) {
    return "@" + commandFile;
  }

  @Override
  public String getIdentifier() {
    return "Borland Linker";
  }

  @Override
  public List<Path> getLibraryPath() {
    return BorlandProcessor.getEnvironmentPath("ilink32", 'L', new String[] {
      "..\\lib"
    });
  }

  @Override
  public String[] getLibraryPatterns(final String[] libnames, final LibraryTypeEnum libType) {
    return BorlandProcessor.getLibraryPatterns(libnames, libType);
  }

  @Override
  public Linker getLinker(final LinkType type) {
    if (type.isStaticLibrary()) {
      return BorlandLibrarian.getInstance();
    }
    if (type.isSharedLibrary()) {
      return dllLinker;
    }
    return instance;
  }

  @Override
  public int getMaximumCommandLength() {
    return 1024;
  }

  @Override
  public String[] getOutputFileSwitch(final Path outFile) {
    return BorlandProcessor.getOutputFileSwitch(outFile);
  }

  @Override
  protected Path getStartupObject(final LinkType linkType) {
    if (linkType.isSharedLibrary()) {
      return Path.of("c0d32.obj");
    }
    if (linkType.isSubsystemGUI()) {
      return Path.of("c0w32.obj");
    }
    if (linkType.isSubsystemConsole()) {
      return Path.of("c0x32.obj");
    }
    return null;
  }

  @Override
  public boolean isCaseSensitive() {
    return BorlandProcessor.isCaseSensitive();
  }

  /**
   * Prepares argument list for exec command.
   *
   * @param outputDir
   *          linker output directory
   * @param outputName
   *          linker output name
   * @param sourceFiles
   *          linker input files (.obj, .o, .res)
   * @param config
   *          linker configuration
   * @return arguments for runTask
   */
  @Override
  protected String[] prepareArguments(final CCTask task, final Path outputDir, final Path outputName,
      final List<Path> sourceFiles, final CommandLineLinkerConfiguration config) {
    final String[] preargs = config.getPreArguments();
    final String[] endargs = config.getEndArguments();
    final List<String> execArgs = new ArrayList<>();

    execArgs.add(this.getCommand().toString());
    for (final String prearg : preargs) {
      execArgs.add(prearg);
    }
    for (final String endarg : endargs) {
      execArgs.add(endarg);
    }
    //
    // see if the input files have any known startup obj files
    //
    Path startup = null;
    for (final Path sourceFile : sourceFiles) {
      final String filename = sourceFile.getFileName().toString().toLowerCase();
      if (startup != null && filename.substring(0, 2).equals("c0") && filename.substring(3, 5).equals("32")
          && filename.substring(filename.length() - 4).equals(".obj")) {
        startup = sourceFile;
      }
    }
    //
    // c0w32.obj, c0x32.obj or c0d32.obj depending on
    // link type
    if (startup == null) {
      startup = config.getStartupObject();
    }
    execArgs.add(startup.toString());
    final List<Path> resFiles = new ArrayList<>();
    final List<String> libFiles = new ArrayList<>();
    Path defFile = null;
    for (final Path sourceFile : sourceFiles) {
      final String last4 = sourceFile.toString().substring(sourceFile.toString().length() - 4).toLowerCase();
      if (last4.equals(".def")) {
        defFile = sourceFile;
      } else {
        if (last4.equals(".res")) {
          resFiles.add(sourceFile);
        } else {
          if (last4.equals(".lib")) {
            libFiles.add(quoteFilename(sourceFile));
          } else {
            execArgs.add(quoteFilename(sourceFile));
          }
        }
      }
    }
    //
    // output file name
    //
    final Path outputFileName = outputDir.resolve(outputName);
    execArgs.add("," + quoteFilename(outputFileName));
    if (config.getMap()) {
      final int lastPeriod = outputFileName.toString().lastIndexOf('.');
      Path mapName;
      if (lastPeriod < outputFileName.toString().length() - 4) {
        mapName = Path.of(outputFileName.getFileName() + ".map");
      } else {
        mapName = Path.of(outputFileName.toString().substring(0, lastPeriod) + ".map");
      }
      execArgs.add("," + quoteFilename(mapName) + ",");
    } else {
      execArgs.add(",,");
    }
    //
    // add all the libraries
    //
    boolean hasImport32 = false;
    final boolean hasCw32 = false;
    for (String libName : libFiles) {
      if (libName.equalsIgnoreCase("import32.lib")) {
        hasImport32 = true;
      }
      if (libName.equalsIgnoreCase("cw32.lib")) {
        hasImport32 = true;
      }
      execArgs.add(libName);
    }
    if (!hasCw32) {
      execArgs.add("cw32.lib");
    }
    if (!hasImport32) {
      execArgs.add("import32.lib");
    }
    if (defFile == null) {
      execArgs.add(",,");
    } else {
      execArgs.add("," + quoteFilename(defFile) + ",");
    }
    for (Path resName : resFiles) {
      execArgs.add(quoteFilename(resName));
    }

    return execArgs.toArray(String[]::new);
  }

  /**
   * Prepares argument list to execute the linker using a response file.
   *
   * @param outputFile
   *          linker output file
   * @param args
   *          output of prepareArguments
   * @return arguments for runTask
   */
  @Override
  protected String[] prepareResponseFile(final Path outputFile, final String[] args) throws IOException {
    final String[] cmdargs = BorlandProcessor.prepareResponseFile(outputFile, args, " + \n");
    cmdargs[cmdargs.length - 1] = getCommandFileSwitch(Path.of(cmdargs[cmdargs.length - 1]));
    return cmdargs;
  }

}
