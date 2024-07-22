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
package com.github.maven_nar.cpptasks.msvc;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.github.maven_nar.cpptasks.CCTask;
import com.github.maven_nar.cpptasks.CUtil;
import com.github.maven_nar.cpptasks.TargetMatcher;
import com.github.maven_nar.cpptasks.VersionInfo;
import com.github.maven_nar.cpptasks.compiler.CommandLineLinker;
import com.github.maven_nar.cpptasks.compiler.LinkType;
import com.github.maven_nar.cpptasks.platforms.WindowsPlatform;
import com.github.maven_nar.cpptasks.types.LibrarySet;
import com.github.maven_nar.cpptasks.types.LibraryTypeEnum;

/**
 * Abstract base class for linkers that try to mimic the command line arguments
 * for the Microsoft (r) Incremental Linker
 *
 * @author Curt Arnold
 */
public abstract class MsvcCompatibleLinker extends CommandLineLinker {
  public MsvcCompatibleLinker(final String command, final String identifierArg, final String outputSuffix) {
    super(command, identifierArg, new String[] {
        ".obj", ".lib", ".res"
    }, new String[] {
        ".map", ".pdb", ".lnk", ".dll", ".tlb", ".rc", ".h"
    }, outputSuffix, false, null);
  }

  private List<Path> libPaths = new ArrayList<>(CUtil.getPathFromEnvironment("LIB", ";"));
  
  @Override
  protected void addBase(final CCTask task, final long base, final List<String> args) {
    if (base >= 0) {
      final String baseAddr = Long.toHexString(base);
      args.add("/BASE:0x" + baseAddr);
    }
  }

  @Override
  protected void addEntry(final CCTask task, final String entry, final List<String> args) {
    if (entry != null) {
      args.add("/ENTRY:" + entry);
    }
  }

  @Override
  protected void addFixed(final CCTask task, final Boolean fixed, final List<String> args) {
    if (fixed != null) {
      if (fixed.booleanValue()) {
        args.add("/FIXED");
      } else {
        args.add("/FIXED:NO");
      }
    }
  }

  @Override
  protected void addImpliedArgs(final CCTask task, final boolean debug, final LinkType linkType,
      final List<String> args) {
    args.add("/NOLOGO");
    if (debug) {
      args.add("/DEBUG");
    }
    if (linkType.isSharedLibrary()) {
      args.add("/DLL");
    }
    //
    // The following lines were commented out
    // from v 1.5 to v 1.12 with no explanation
    //
    if (linkType.isSubsystemGUI()) {
      args.add("/SUBSYSTEM:WINDOWS");
    } else {
      if (linkType.isSubsystemConsole()) {
        args.add("/SUBSYSTEM:CONSOLE");
      }
    }
  }

  @Override
  protected void addIncremental(final CCTask task, final boolean incremental, final List<String> args) {
    if (incremental) {
      args.add("/INCREMENTAL:YES");
    } else {
      args.add("/INCREMENTAL:NO");
    }
  }

  @Override
  protected void addLibraryPath(final List<String> preargs, final Path path) {
    preargs.add("/LIBPATH:" + path);
    libPaths.add(0, path);
  }

  @Override
  protected String[] addLibrarySets(final CCTask task, final List<LibrarySet> libsets, final List<String> preargs,
      final List<String> midargs, final List<String> endargs) {
    for (final LibrarySet set : libsets) {
      final Path libdir = set.getDir(null);
      addLibraryDirectory(libdir, preargs);
    }
    return null;
  }

  @Override
  protected void addMap(final CCTask task, final boolean map, final List<String> args) {
    if (map) {
      args.add("/MAP");
    }
  }

  @Override
  protected void addStack(final CCTask task, final int stack, final List<String> args) {
    if (stack >= 0) {
      final String stackStr = Integer.toHexString(stack);
      args.add("/STACK:0x" + stackStr);
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
  public List<Path> getLibraryPath() {
    return Collections.unmodifiableList(libPaths);
  }

  @Override
  public String[] getLibraryPatterns(final String[] libnames, final LibraryTypeEnum libType) {
    final StringBuffer buf = new StringBuffer();
    final String[] patterns = new String[libnames.length];
    for (int i = 0; i < libnames.length; i++) {
      buf.setLength(0);
      buf.append(libnames[i]);
      buf.append(".lib");
      patterns[i] = buf.toString();
    }
    return patterns;
  }

  @Override
  public int getMaximumCommandLength() {
    // FREEHEP stay on the safe side
    return 32000; // 32767;
  }

  @Override
  public String[] getOutputFileSwitch(final Path outputFile) {
    return new String[] {
      "/OUT:" + outputFile
    };
  }

  @Override
  public boolean isCaseSensitive() {
    return false;
  }
}
