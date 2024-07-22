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
package com.github.maven_nar.cpptasks.sun;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import com.github.maven_nar.cpptasks.CUtil;
import com.github.maven_nar.cpptasks.compiler.LinkType;
import com.github.maven_nar.cpptasks.compiler.Linker;
import com.github.maven_nar.cpptasks.gcc.AbstractLdLinker;

/**
 * Adapter for Sun (r) Forte(tm) C++ Linker
 *
 * @author Curt Arnold
 */
public final class ForteCCLinker extends AbstractLdLinker {
  private static final String[] discardFiles = new String[] {
      ".dll", ".so", ".sl"
  };
  private static final String[] objFiles = new String[] {
      ".o", ".a", ".lib"
  };
  private static final ForteCCLinker arLinker = new ForteCCLinker("CC", objFiles, discardFiles, "lib", ".a");
  private static final ForteCCLinker dllLinker = new ForteCCLinker("CC", objFiles, discardFiles, "lib", ".so");
  private static final ForteCCLinker instance = new ForteCCLinker("CC", objFiles, discardFiles, "", "");

  public static ForteCCLinker getInstance() {
    return instance;
  }

  private List<Path> libDirs;

  private ForteCCLinker(final String command, final String[] extensions, final String[] ignoredExtensions,
      final String outputPrefix, final String outputSuffix) {
    super(command, "-V", extensions, ignoredExtensions, outputPrefix, outputSuffix, false, null);
  }

  public void addImpliedArgs(final boolean debug, final LinkType linkType, final Vector<String> args) {
    if (debug) {
      args.addElement("-g");
    }
    if (linkType.isStaticRuntime()) {
      // FREEHEP changed -static
      args.addElement("-staticlib=%all");
    }
    if (linkType.isSharedLibrary()) {
      args.addElement("-G");
    }
    if (linkType.isStaticLibrary()) {
      args.addElement("-xar");
    }
  }

  public void addIncremental(final boolean incremental, final Vector<String> args) {
    /*
     * if (incremental) { args.addElement("-xidlon"); } else {
     * args.addElement("-xidloff"); }
     */
  }

  /**
   * Returns library path.
   * 
   */
  @Override
  public List<Path> getLibraryPath() {
    if (this.libDirs == null) {
      this.libDirs = new ArrayList<>();
      final Path CCloc = CUtil.getExecutableLocation("CC");
      if (CCloc != null) {
        final Path compilerLib = CCloc.resolveSibling("lib").toAbsolutePath();
        if (Files.exists(compilerLib)) {
          this.libDirs.add(compilerLib);
        }
      }
      this.libDirs.add(Path.of("/usr/lib"));
    }
    return this.libDirs;
  }

  @Override
  public Linker getLinker(final LinkType type) {
    if (type.isStaticLibrary()) {
      return arLinker;
    }
    if (type.isSharedLibrary()) {
      return dllLinker;
    }
    return instance;
  }
}
