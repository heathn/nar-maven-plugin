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
package com.github.maven_nar.cpptasks.gcc;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import com.github.maven_nar.cpptasks.CCTask;
import com.github.maven_nar.cpptasks.CUtil;
import com.github.maven_nar.cpptasks.compiler.CaptureStreamHandler;
import com.github.maven_nar.cpptasks.compiler.LinkType;
import com.github.maven_nar.cpptasks.compiler.Linker;
import com.github.maven_nar.cpptasks.types.LibrarySet;

/**
 * Adapter for the g++ variant of the GCC linker
 *
 * @author Stephen M. Webb <stephen.webb@bregmasoft.com>
 */
public class ClangLinker extends AbstractLdLinker {
  public static final String CLANGPP_COMMAND = "clang++";

  protected static final String[] discardFiles = new String[0];
  protected static final String[] objFiles = new String[] {
      ".o", ".a", ".lib", ".dll", ".so", ".sl"
  };
  private final static String libPrefix = "libraries: =";
  protected static final String[] libtoolObjFiles = new String[] {
      ".fo", ".a", ".lib", ".dll", ".so", ".sl"
  };
  private static String[] linkerOptions = new String[] {
      "-bundle", "-dylib", "-dynamic", "-dynamiclib", "-nostartfiles", "-nostdlib", "-prebind", "-s", "-static",
      "-shared", "-symbolic", "-Xlinker", "-static-libgcc", "-shared-libgcc", "-p", "-pg", "-pthread"
  };
  // FREEHEP refactored dllLinker into soLinker
  private static final ClangLinker soLinker = new ClangLinker(CLANGPP_COMMAND, objFiles, discardFiles, "lib", ".so", false,
      new ClangLinker(CLANGPP_COMMAND, objFiles, discardFiles, "lib", ".so", true, null));
  private static final ClangLinker instance = new ClangLinker("clang", objFiles, discardFiles, "", "", false, null);
  private static final ClangLinker machDllLinker = new ClangLinker(CLANGPP_COMMAND, objFiles, discardFiles, "lib", ".dylib",
      false, null);
  private static final ClangLinker machPluginLinker = new ClangLinker(CLANGPP_COMMAND, objFiles, discardFiles, "lib",
      ".bundle", false, null);
  // FREEHEP
  private static final ClangLinker machJNILinker = new ClangLinker(CLANGPP_COMMAND, objFiles, discardFiles, "lib", ".jnilib",
      false, null);
  // FREEHEP added dllLinker for windows
  private static final ClangLinker dllLinker = new ClangLinker(CLANGPP_COMMAND, objFiles, discardFiles, "", ".dll", false, null);


  public static ClangLinker getInstance() {
    return instance;
  }

  private List<Path> libDirs;
  private String runtimeLibrary;
  // FREEEHEP
  private String gccLibrary, gfortranLibrary, gfortranMainLibrary;

  protected ClangLinker(final String command, final String[] extensions, final String[] ignoredExtensions,
      final String outputPrefix, final String outputSuffix, final boolean isLibtool, final ClangLinker libtoolLinker) {
    super(command, "-v", extensions, ignoredExtensions, outputPrefix, outputSuffix, isLibtool, libtoolLinker);
  }

  @Override
  protected void addImpliedArgs(final CCTask task, final boolean debug, final LinkType linkType,
      final List<String> args) {
    super.addImpliedArgs(task, debug, linkType, args);
    if (getIdentifier().contains("mingw")) {
      if (linkType.isSubsystemConsole()) {
        args.add("-mconsole");
      }
      if (linkType.isSubsystemGUI()) {
        args.add("-mwindows");
      }
    }
    // BEGINFREEHEP link or not with libstdc++
    // for MacOS X see:
    // http://developer.apple.com/documentation/DeveloperTools/Conceptual/CppRuntimeEnv/Articles/LibCPPDeployment.html
    this.gfortranLibrary = null;
    if (linkType.linkFortran()) {
      if (linkType.isStaticRuntime()) {
        final String[] cmdin = new String[] {
            "gfortran", "-print-file-name=libgfortran.a"
        };
        final String[] cmdout = CaptureStreamHandler.run(cmdin);
        if (cmdout.length > 0 && cmdout[0].indexOf('/') >= 0) {
          this.gfortranLibrary = cmdout[0];
        }
      } else {
        this.gfortranLibrary = "-lgfortran";
      }
    }

    this.gfortranMainLibrary = null;
    if (linkType.linkFortran()) {
      if (linkType.isExecutable() && linkType.linkFortranMain() && !isDarwin()) {
        if (linkType.isStaticRuntime()) {
          final String[] cmdin = new String[] {
              "gfortran", "-print-file-name=libgfortranbegin.a"
          };
          final String[] cmdout = CaptureStreamHandler.run(cmdin);
          if (cmdout.length > 0 && cmdout[0].indexOf('/') >= 0) {
            this.gfortranMainLibrary = cmdout[0];
          }
        } else {
          this.gfortranMainLibrary = "-lgfortranbegin";
        }
      }
    }

    this.runtimeLibrary = null;
    if (linkType.linkCPP()) {
      if (linkType.isStaticRuntime()) {
        if (isDarwin()) {
          task.log("Warning: clang cannot statically link to C++");
        } else {
          final String[] cmdin = new String[] {
              "g++", "-print-file-name=libstdc++.a"
          };
          final String[] cmdout = CaptureStreamHandler.run(cmdin);
          if (cmdout.length > 0 && cmdout[0].indexOf('/') >= 0) {
            this.runtimeLibrary = cmdout[0];
          }
        }
      } else {
        this.runtimeLibrary = "-lc++";
      }
    }

    this.gccLibrary = null;
    if (linkType.isStaticRuntime()) {
      task.log("Warning: clang cannot statically link libgcc");
    } else {
      if (linkType.linkCPP()) {
        // NOTE: added -fexceptions here for MacOS X
        this.gccLibrary = "-fexceptions";
      } else {
        task.log("Warning: clang cannot dynamically link libgcc");
      }
    }
    // ENDFREEHEP
  }

  @Override
  protected String[] addLibrarySets(final CCTask task, final List<LibrarySet> libsets, final List<String> preargs,
      final List<String> midargs, final List<String> endargs) {
    final String[] rs = super.addLibrarySets(task, libsets, preargs, midargs, endargs);
    // BEGINFREEHEP
    if (this.gfortranLibrary != null) {
      endargs.add(this.gfortranLibrary);
    }
    if (this.gfortranMainLibrary != null) {
      endargs.add(this.gfortranMainLibrary);
    }
    if (this.gccLibrary != null) {
      endargs.add(this.gccLibrary);
    }
    // ENDFREEHEP
    if (this.runtimeLibrary != null) {
      endargs.add(this.runtimeLibrary);
    }
    return rs;
  }

  /**
   * Allows drived linker to decorate linker option. Override by GppLinker to
   * prepend a "-Wl," to pass option to through gcc to linker.
   *
   * @param buf
   *          buffer that may be used and abused in the decoration process
   *          must not be null.
   * @param arg
   *          linker argument
   */
  @Override
  public String decorateLinkerOption(final StringBuffer buf, final String arg) {
    String decoratedArg = arg;
    if (arg.length() > 1 && arg.charAt(0) == '-') {
      switch (arg.charAt(1)) {
      //
      // passed automatically by GCC
      //
        case 'g':
        case 'f':
        case 'F':
          /* Darwin */
        case 'm':
        case 'O':
        case 'W':
        case 'l':
        case 'L':
        case 'u':
        case 'B':
          break;
        default:
          boolean known = false;
          for (final String linkerOption : linkerOptions) {
            if (linkerOption.equals(arg)) {
              known = true;
              break;
            }
          }
          if (!known) {
            buf.setLength(0);
            buf.append("-Wl,");
            buf.append(arg);
            decoratedArg = buf.toString();
          }
          break;
      }
    }
    return decoratedArg;
  }

  /**
   * Returns library path.
   *
   */
  @Override
  public List<Path> getLibraryPath() {
    if (this.libDirs == null) {
      final Vector<String> dirs = new Vector<>();
      // Ask GCC where it will look for its libraries.
      final String[] args = new String[] {
          "g++", "-print-search-dirs"
      };
      final String[] cmdout = CaptureStreamHandler.run(args);
      for (int i = 0; i < cmdout.length; ++i) {
        final int prefixIndex = cmdout[i].indexOf(libPrefix);
        if (prefixIndex >= 0) {
          // Special case DOS-type GCCs like MinGW or Cygwin
          int s = prefixIndex + libPrefix.length();
          int t = cmdout[i].indexOf(';', s);
          while (t > 0) {
            dirs.addElement(cmdout[i].substring(s, t));
            s = t + 1;
            t = cmdout[i].indexOf(';', s);
          }
          dirs.addElement(cmdout[i].substring(s));
          ++i;
          for (; i < cmdout.length; ++i) {
            dirs.addElement(cmdout[i]);
          }
        }
      }
      // Eliminate all but actual directories.
      final Path[] libpath = new Path[dirs.size()];
      dirs.copyInto(libpath);
      final int count = CUtil.checkDirectoryArray(libpath);
      // Build return list.
      this.libDirs = new ArrayList<>(count);
      for (final Path element : libpath) {
        if (element != null) {
          this.libDirs.add(element);
        }
      }
    }
    return this.libDirs;
  }

  @Override
  public Linker getLinker(final LinkType type) {
    if (type.isStaticLibrary()) {
      return GccLibrarian.getInstance();
    }
    // BEGINFREEHEP
    if (type.isJNIModule()) {
      return isDarwin() ? machJNILinker : isWindows() ? dllLinker : soLinker;
    }
    if (type.isPluginModule()) {
      return isDarwin() ? machPluginLinker : isWindows() ? dllLinker : soLinker;
    }
    if (type.isSharedLibrary()) {
      return isDarwin() ? machDllLinker : isWindows() ? dllLinker : soLinker;
    }
    // ENDFREEHEP
    return instance;
  }

}
