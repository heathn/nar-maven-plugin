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
/*
 * FREEHEP
 */
package com.github.maven_nar.cpptasks.sun;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import com.github.maven_nar.cpptasks.CUtil;
import com.github.maven_nar.cpptasks.OptimizationEnum;
import com.github.maven_nar.cpptasks.compiler.LinkType;
import com.github.maven_nar.cpptasks.compiler.Linker;
import com.github.maven_nar.cpptasks.gcc.GccCompatibleCCompiler;

/**
 * Adapter for the Sun (r) Forte (tm) F77 compiler
 *
 * @author Mark Donszelmann
 */
public final class ForteF77Compiler extends GccCompatibleCCompiler {
  private static final ForteF77Compiler instance = new ForteF77Compiler("f77");

  /**
   * Gets singleton instance of this class
   */
  public static ForteF77Compiler getInstance() {
    return instance;
  }

  private List<Path> includePath;

  /**
   * Private constructor. Use ForteF77Compiler.getInstance() to get singleton
   * instance of this class.
   */
  private ForteF77Compiler(final String command) {
    super(command, "-V", false, null, false, null);
  }

  @Override
  public void addImpliedArgs(final Vector<String> args, final boolean debug, final boolean multithreaded,
      final boolean exceptions, final LinkType linkType, final Boolean rtti, final OptimizationEnum optimization) {
    args.addElement("-c");
    if (debug) {
      args.addElement("-g");
    }
    if (optimization != null) {
      if (optimization.isSpeed()) {
        args.addElement("-xO2");
      }
    }
    if (multithreaded) {
      args.addElement("-mt");
    }
    if (linkType.isSharedLibrary()) {
      args.addElement("-KPIC");
    }

  }

  @Override
  public void addWarningSwitch(final Vector<String> args, final int level) {
    switch (level) {
      case 0:
        args.addElement("-w");
        break;
      case 1:
      case 2:
        args.addElement("+w");
        break;
      case 3:
      case 4:
      case 5:
        args.addElement("+w2");
        break;
    }
  }

  @Override
  public List<Path> getEnvironmentIncludePath() {
    if (this.includePath == null) {
      this.includePath = new ArrayList<>();
      final Path f77Loc = CUtil.getExecutableLocation("f77");
      if (f77Loc != null) {
        final Path compilerIncludeDir = f77Loc.resolveSibling("include").toAbsolutePath();
        if (Files.exists(compilerIncludeDir)) {
          this.includePath.add(compilerIncludeDir);
        }
      }
      this.includePath.add(Path.of("/usr/include"));
    }
    return this.includePath;
  }

  @Override
  public Linker getLinker(final LinkType linkType) {
    return ForteCCLinker.getInstance().getLinker(linkType);
  }

  @Override
  public int getMaximumCommandLength() {
    return Integer.MAX_VALUE;
  }
}
