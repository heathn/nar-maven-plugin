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
package com.github.maven_nar.cpptasks.compiler;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.types.Environment;

import com.github.maven_nar.cpptasks.CCTask;
import com.github.maven_nar.cpptasks.CUtil;
import com.github.maven_nar.cpptasks.LinkerDef;
import com.github.maven_nar.cpptasks.ProcessorDef;
import com.github.maven_nar.cpptasks.ProcessorParam;
import com.github.maven_nar.cpptasks.TargetDef;
import com.github.maven_nar.cpptasks.VersionInfo;
import com.github.maven_nar.cpptasks.types.LibrarySet;
import com.google.common.collect.Lists;

/**
 * An abstract Linker implementation that performs the link via an external
 * command.
 *
 * @author Adam Murdoch
 */
public abstract class CommandLineLinker extends AbstractLinker {
  private String command;
  private String prefix;
  private Environment env = null;
  private String identifier;
  private final String identifierArg;
  private final boolean isLibtool;
  private final CommandLineLinker libtoolLinker;
  private final boolean newEnvironment = false;
  private final String outputSuffix;
  private List<String[]> commands;
  private boolean dryRun;

  // FREEHEP
  private final int maxPathLength = 250;

  /** Creates a command line linker invocation */
  public CommandLineLinker(final String command, final String identifierArg, final String[] extensions,
      final String[] ignoredExtensions, final String outputSuffix, final boolean isLibtool,
      final CommandLineLinker libtoolLinker) {
    super(extensions, ignoredExtensions);
    this.command = command;
    this.identifierArg = identifierArg;
    this.outputSuffix = outputSuffix;
    this.isLibtool = isLibtool;
    this.libtoolLinker = libtoolLinker;
  }

  protected void addBase(final CCTask task, final long base, final List<String> args) {
    // NB: Do nothing by default.
  }

  protected void addEntry(final CCTask task, final String entry, final List<String> args) {
    // NB: Do nothing by default.
  }

  protected void addFixed(final CCTask task, final Boolean fixed, final List<String> args) {
    // NB: Do nothing by default.
  }

  protected void addImpliedArgs(final CCTask task, final boolean debug, final LinkType linkType,
      final List<String> args) {
    // NB: Do nothing by default.
  }

  protected void addIncremental(final CCTask task, final boolean incremental, final List<String> args) {
    // NB: Do nothing by default.
  }

  protected void addLibraryDirectory(final Path libraryDirectory, final List<String> preargs) {
    if (libraryDirectory != null && Files.exists(libraryDirectory)) {
      final Path currentDir = Path.of(".").getParent();
      Path path = libraryDirectory.toAbsolutePath();
      if (currentDir != null) {
        path = currentDir.relativize(libraryDirectory);
      }
      addLibraryPath(preargs, path);
    }
  }

  protected void addLibraryPath(final List<String> preargs, final Path path) {
  }

  //
  // Windows processors handle these through file list
  //
  protected String[] addLibrarySets(final CCTask task, final List<LibrarySet> libsets, final List<String> preargs,
      final List<String> midargs, final List<String> endargs) {
    return null;
  }

  protected void addMap(final CCTask task, final boolean map, final List<String> args) {
    // NB: Do nothing by default.
  }

  protected void addStack(final CCTask task, final int stack, final List<String> args) {
    // NB: Do nothing by default.
  }

  @Override
  protected LinkerConfiguration createConfiguration(final CCTask task, final LinkType linkType,
      final ProcessorDef[] baseDefs, final LinkerDef specificDef, final TargetDef targetPlatform,
      final VersionInfo versionInfo) {

    final List<String> preargs = new ArrayList<>();
    final List<String> midargs = new ArrayList<>();
    final List<String> endargs = new ArrayList<>();
    final List<List<String>> args = List.of(
        preargs, midargs, endargs
    );

    this.prefix  = specificDef.getLinkerPrefix();

    final List<LinkerDef> defaultProviders = new ArrayList<>();
    defaultProviders.add(specificDef);
    defaultProviders.addAll(Arrays.asList(baseDefs).stream()
          // Downcast ProcessorDef -> LinkerDef
          .map(LinkerDef.class::cast)
          .collect(Collectors.toList()));

    //
    // add command line arguments inherited from <cc> element
    // any "extends" and finally the specific CompilerDef
    Lists.reverse(defaultProviders).stream()
        .flatMap(linkerDef -> linkerDef.getActiveProcessorArgs().stream())
        .forEach(cmdArg -> args.get(cmdArg.getLocation()).add(cmdArg.getValue()));

    Lists.reverse(defaultProviders).stream()
        .flatMap(linkerDef -> linkerDef.getLibraryDirectories().stream())
        .distinct()
        .forEach(libDir -> addLibraryDirectory(libDir, preargs));

    //
    // add command line arguments inherited from <cc> element
    // any "extends" and finally the specific CompilerDef
    final List<ProcessorParam> params = Lists.reverse(defaultProviders).stream()
        .flatMap(linkerDef -> linkerDef.getActiveProcessorParams().stream())
        .collect(Collectors.toList());

    final boolean debug = specificDef.getDebug(baseDefs, 0);

    final Path startupObject = getStartupObject(linkType);

    addImpliedArgs(task, debug, linkType, preargs);
    addIncremental(task, specificDef.getIncremental(defaultProviders, 1), preargs);
    addFixed(task, specificDef.getFixed(defaultProviders, 1), preargs);
    addMap(task, specificDef.getMap(defaultProviders, 1), preargs);
    addBase(task, specificDef.getBase(defaultProviders, 1), preargs);
    addStack(task, specificDef.getStack(defaultProviders, 1), preargs);
    addEntry(task, specificDef.getEntry(defaultProviders, 1), preargs);

    final List<LibrarySet> libsets = specificDef.getActiveLibrarySets(defaultProviders, 1);
    String[] libnames = addLibrarySets(task, libsets, preargs, midargs, endargs);

    final String configId = Stream.concat(
          Stream.of(getIdentifier()),
          args.stream().flatMap(List::stream))
        .collect(Collectors.joining(" "));

    final String[][] options = new String[][] {
      Stream.concat(args.get(0).stream(), args.get(1).stream()).toArray(String[]::new),
      args.get(2).stream().toArray(String[]::new)
    };

    // if this linker doesn't have an env, and there is a more generically
    // definition for environment, use it.
    if (null != specificDef.getEnv() && null == this.env) {
      this.env = specificDef.getEnv();
    }
    for (final ProcessorDef processorDef : baseDefs) {
      final Environment environment = processorDef.getEnv();
      if (null != environment && null == this.env) {
        this.env = environment;
      }
    }
    final boolean rebuild = specificDef.getRebuild(baseDefs, 0);
    final boolean map = specificDef.getMap(defaultProviders, 1);
    final String toolPath = specificDef.getToolPath();
    
    setCommands(specificDef.getCommands());
    setDryRun(specificDef.isDryRun());

    // task.log("libnames:"+libnames.length, Project.MSG_VERBOSE);
    return new CommandLineLinkerConfiguration(this, configId, options, params, rebuild, map, debug, libnames,
        startupObject, toolPath);
  }

  /**
   * Allows drived linker to decorate linker option.
   * Override by GccLinker to prepend a "-Wl," to
   * pass option to through gcc to linker.
   *
   * @param buf
   *          buffer that may be used and abused in the decoration process,
   *          must not be null.
   * @param arg
   *          linker argument
   */
  protected String decorateLinkerOption(final StringBuffer buf, final String arg) {
    return arg;
  }

  protected final String getCommand() {
    if (this.prefix != null && (!this.prefix.isEmpty())) {
      return this.prefix + this.command;
    } else {
      return this.command;
    }
  }

  protected abstract String getCommandFileSwitch(Path commandFile);

  public Path getCommandWithPath(final CommandLineLinkerConfiguration config) {
    if (config.getCommandPath() != null) {
      final Path command = Path.of(config.getCommandPath(), this.getCommand());
      return command.normalize();
    } else {
      return Path.of(this.getCommand());
    }
  }

  @Override
  public String getIdentifier() {
    if (this.identifier == null) {
      if (this.identifierArg == null) {
        this.identifier = getIdentifier(new String[] {
          this.getCommand()
        }, this.getCommand());
      } else {
        this.identifier = getIdentifier(new String[] {
          this.getCommand(), this.identifierArg
        }, this.getCommand());
      }
    }
    return this.identifier;
  }

  public final CommandLineLinker getLibtoolLinker() {
    if (this.libtoolLinker != null) {
      return this.libtoolLinker;
    }
    return this;
  }

  protected abstract int getMaximumCommandLength();

  @Override
  public Path[] getOutputFileNames(final Path baseName, final VersionInfo versionInfo) {
    return new Path[] {
      Path.of(baseName.toString() + this.outputSuffix)
    };
  }

  protected String[] getOutputFileSwitch(final CCTask task, final Path outputFile) {
    // FREEHEP BEGIN
    if (isWindows() && outputFile.toAbsolutePath().toString().length() > this.maxPathLength) {
      throw new BuildException("Absolute path too long, " + outputFile.toAbsolutePath().toString().length() + " > " + this.maxPathLength + ": '"
          + outputFile);
    }
    // FREEHEP END
    return getOutputFileSwitch(outputFile);
  }

  protected abstract String[] getOutputFileSwitch(Path outputFile);

  protected Path getStartupObject(final LinkType linkType) {
    return null;
  }

  /**
   * Performs a link using a command line linker
   *
   */
  public void link(final CCTask task, final Path outputFile, final List<Path> sourceFiles,
      final CommandLineLinkerConfiguration config) throws BuildException {
    final Path parentDir = outputFile.getParent();
    String[] execArgs = prepareArguments(task, parentDir, outputFile.getFileName(), sourceFiles, config);
    int commandLength = 0;
    for (final String execArg : execArgs) {
      commandLength += execArg.length() + 1;
    }

    //
    // if command length exceeds maximum
    // then create a temporary
    // file containing everything but the command name
    if (commandLength >= this.getMaximumCommandLength()) {
      try {
        execArgs = prepareResponseFile(outputFile, execArgs);
      } catch (final IOException ex) {
        throw new BuildException(ex);
      }
    }

    final int retval = runCommand(task, parentDir, execArgs);
    //
    // if the process returned a failure code then
    // throw an BuildException
    //
    if (retval != 0) {
      //
      // construct the exception
      //
      throw new BuildException(getCommandWithPath(config) + " failed with return code " + retval, task.getLocation());
    }

  }

  /**
   * Prepares argument list for exec command. Will return null
   * if command line would exceed allowable command line buffer.
   *
   * @param task
   *          compilation task.
   * @param outputFile
   *          linker output file
   * @param sourceFiles
   *          linker input files (.obj, .o, .res)
   * @param config
   *          linker configuration
   * @return arguments for runTask
   */
  protected String[] prepareArguments(final CCTask task, final Path outputDir, final Path outputFile,
      final List<Path> sourceFiles, final CommandLineLinkerConfiguration config) {

    final String[] preargs = config.getPreArguments();
    final String[] endargs = config.getEndArguments();
    final String outputSwitch[] = getOutputFileSwitch(task, outputFile);
    final List<String> allArgs = new ArrayList<>();

    if (this.isLibtool) {
      allArgs.add("libtool");
    }
    allArgs.add(getCommandWithPath(config).toString());
    final StringBuffer buf = new StringBuffer();

    for (final String prearg : preargs) {
      allArgs.add(task.isDecorateLinkerOptions() ? decorateLinkerOption(buf, prearg) : prearg);
    }

    for (final String element : outputSwitch) {
      allArgs.add(element);
    }
    for (final Path sourceFile : sourceFiles) {
      allArgs.add(prepareFilename(buf, outputDir, sourceFile));
    }
    for (final String endarg : endargs) {
      allArgs.add(task.isDecorateLinkerOptions() ? decorateLinkerOption(buf, endarg) : endarg);
    }

    return allArgs.toArray(String[]::new);
  }

  /**
   * Processes filename into argument form
   *
   */
  protected String prepareFilename(final StringBuffer buf, final Path outputDir, final Path sourceFile) {
    // FREEHEP BEGIN exit if absolute path is too long. Max length on relative
    // paths in windows is even shorter.
    if (isWindows() && sourceFile.toAbsolutePath().toString().length() > this.maxPathLength) {
      throw new BuildException("Absolute path too long, " + sourceFile.toAbsolutePath().toString().length() + " > " + this.maxPathLength + ": '"
          + sourceFile);
    }
    // FREEHEP END
    return quoteFilename(buf, sourceFile);
  }

  /**
   * Prepares argument list to execute the linker using a
   * response file.
   *
   * @param outputFile
   *          linker output file
   * @param args
   *          output of prepareArguments
   * @return arguments for runTask
   */
  protected String[] prepareResponseFile(final Path outputFile, final String[] args) throws IOException {
    final String baseName = outputFile.getFileName().toString();
    final Path commandFile = outputFile.resolveSibling(baseName + ".rsp");

    try (final BufferedWriter writer = Files.newBufferedWriter(commandFile)) {
      int execArgCount = 1;
      if (this.isLibtool) {
        execArgCount++;
      }
      final String[] execArgs = new String[execArgCount + 1];
      System.arraycopy(args, 0, execArgs, 0, execArgCount);
      execArgs[execArgCount] = getCommandFileSwitch(commandFile);
      for (int i = execArgCount; i < args.length; i++) {
        //
        // if embedded space and not quoted then
        // quote argument
        if (args[i].contains(" ") && args[i].charAt(0) != '\"') {
          writer.write('\"');
          writer.write(args[i]);
          writer.write("\"\n");
        } else {
          writer.write(args[i]);
          writer.write('\n');
        }
      }

      return execArgs;
    }
  }

  protected String quoteFilename(final StringBuffer buf, final Path filename) {
    if (filename.toString().indexOf(' ') >= 0) {
      buf.setLength(0);
      buf.append('\"');
      buf.append(filename);
      buf.append('\"');
      return buf.toString();
    }
    return filename.toString();
  }

  protected String quoteFilename(final Path filename) {
    if (filename.toString().indexOf(' ') >= 0) {
      StringBuffer sb = new StringBuffer();
      sb.append('\"');
      sb.append(filename);
      sb.append('\"');
      return sb.toString();
    }
    return filename.toString();
  }

  /**
   * This method is exposed so test classes can overload
   * and test the arguments without actually spawning the
   * compiler
   */
  protected int runCommand(final CCTask task, final Path workingDir, final String[] cmdline) throws BuildException {
    commands.add(cmdline);
    if (dryRun) return 0;
    return CUtil.runCommand(task, workingDir, cmdline, this.newEnvironment, this.env);
  }

  protected final void setCommand(final String command) {
    this.command = command;
  }

  public void setCommands(List<String[]> commands) {
    this.commands = commands;
  }

  public boolean isDryRun() {
    return dryRun;
  }

  public void setDryRun(boolean dryRun) {
    this.dryRun = dryRun;
  }

}
