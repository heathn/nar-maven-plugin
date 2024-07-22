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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.tools.ant.BuildException;

import com.github.maven_nar.cpptasks.CCTask;
import com.github.maven_nar.cpptasks.CUtil;
import com.github.maven_nar.cpptasks.compiler.CommandLineLinker;
import com.github.maven_nar.cpptasks.compiler.CommandLineLinkerConfiguration;
import com.github.maven_nar.cpptasks.compiler.LinkType;
import com.github.maven_nar.cpptasks.compiler.Linker;
import com.github.maven_nar.cpptasks.types.LibraryTypeEnum;

/**
 * Adapter for the Borland(r) tlib Librarian
 *
 * @author Curt Arnold
 */
public class BorlandLibrarian extends CommandLineLinker {
  private static final BorlandLibrarian instance = new BorlandLibrarian();

  public static BorlandLibrarian getInstance() {
    return instance;
  }

  private BorlandLibrarian() {
    super("tlib", "--version", new String[] {
      ".obj"
    }, new String[0], ".lib", false, null);
  }

  @Override
  protected String getCommandFileSwitch(final Path cmdFile) {
    //
    // tlib requires quotes around paths containing -
    // ilink32 doesn't like them
    final StringBuffer buf = new StringBuffer("@");
    BorlandProcessor.quoteFile(buf, cmdFile);
    return buf.toString();
  }

  /**
   * Gets identifier for the linker.
   * 
   * TLIB will lockup when attempting to get version
   * information. Since the Librarian version isn't critical
   * just return a stock response.
   */
  @Override
  public String getIdentifier() {
    return "TLIB 4.5 Copyright (c) 1987, 1999 Inprise Corporation";
  }

  @Override
  public List<Path> getLibraryPath() {
    return CUtil.getPathFromEnvironment("LIB", ";");
  }

  @Override
  public String[] getLibraryPatterns(final String[] libnames, final LibraryTypeEnum libType) {
    return BorlandProcessor.getLibraryPatterns(libnames, libType);
  }

  @Override
  public Linker getLinker(final LinkType type) {
    return BorlandLinker.getInstance().getLinker(type);
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
  public boolean isCaseSensitive() {
    return BorlandProcessor.isCaseSensitive();
  }

  /**
   * Builds a library
   *
   */
  @Override
  public void link(final CCTask task, final Path outputFile, final List<Path> sourceFiles,
      final CommandLineLinkerConfiguration config) throws BuildException {
    //
    // delete any existing library
    try {
      Files.delete(outputFile);
    } catch (IOException e) {
      throw new BuildException(e);
    }
  
    //
    // build a new library
    super.link(task, outputFile, sourceFiles, config);
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
    final StringBuffer buf = new StringBuffer();
    final List<String> executeArgs = new ArrayList<>();

    executeArgs.add(this.getCommand().toString());
    final Path outputFileName = outputDir.resolve(outputName);
    executeArgs.add(quoteFilename(buf, outputFileName));

    for (final String prearg : preargs) {
      executeArgs.add(prearg);
    }

    //
    // add a place-holder for page size
    //
    final int pageSizeIndex = executeArgs.size();
    executeArgs.add(null);

    int objBytes = 0;

    for (final Path sourceFile : sourceFiles) {
      final String last4 = sourceFile.toString().substring(sourceFile.toString().length() - 4).toLowerCase();
      if (last4.equals(".def")) {
      } else {
        if (last4.equals(".res")) {
        } else {
          if (last4.equals(".lib")) {
          } else {
            executeArgs.add("+" + quoteFilename(buf, sourceFile));
            try {
              objBytes += Files.size(sourceFile);
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          }
        }
      }
    }

    for (final String endarg : endargs) {
      executeArgs.add(endarg);
    }

    final int minPageSize = objBytes >> 16;
    int pageSize = 0;
    for (int i = 4; i <= 15; i++) {
      pageSize = 1 << i;
      if (pageSize > minPageSize) {
        break;
      }
    }
    executeArgs.set(pageSizeIndex, "/P" + Integer.toString(pageSize));

    return executeArgs.toArray(String[]::new);
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
    final String[] cmdargs = BorlandProcessor.prepareResponseFile(outputFile, args, " & \n");
    cmdargs[cmdargs.length - 1] = getCommandFileSwitch(Path.of(cmdargs[cmdargs.length - 1]));
    return cmdargs;
  }

  /**
   * Encloses problematic file names within quotes.
   * 
   * @param buf
   *          string buffer
   * @param filename
   *          source file name
   * @return filename potentially enclosed in quotes.
   */
  @Override
  protected String quoteFilename(final StringBuffer buf, final Path filename) {
    buf.setLength(0);
    BorlandProcessor.quoteFile(buf, filename);
    return buf.toString();
  }

}
