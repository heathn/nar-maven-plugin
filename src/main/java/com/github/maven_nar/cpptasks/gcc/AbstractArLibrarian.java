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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.apache.tools.ant.BuildException;

import com.github.maven_nar.cpptasks.CCTask;
import com.github.maven_nar.cpptasks.VersionInfo;
import com.github.maven_nar.cpptasks.compiler.CommandLineLinker;
import com.github.maven_nar.cpptasks.compiler.CommandLineLinkerConfiguration;
import com.github.maven_nar.cpptasks.types.LibraryTypeEnum;

/**
 * Adapter for the "ar" tool
 *
 * @author Adam Murdoch
 * @author Curt Arnold
 */
public abstract class AbstractArLibrarian extends CommandLineLinker {
  private final/* final */
  String outputPrefix;

  protected AbstractArLibrarian(final String command, final String identificationArg, final String[] inputExtensions,
      final String[] ignoredExtensions, final String outputPrefix, final String outputExtension,
      final boolean isLibtool, final AbstractArLibrarian libtoolLibrarian) {
    super(command, identificationArg, inputExtensions, ignoredExtensions, outputExtension, isLibtool, libtoolLibrarian);
    this.outputPrefix = outputPrefix;
  }

  @Override
  public String getCommandFileSwitch(final Path commandFile) {
    return null;
  }

  @Override
  public List<Path> getLibraryPath() {
    return Collections.emptyList();
  }

  @Override
  public String[] getLibraryPatterns(final String[] libnames, final LibraryTypeEnum libType) {
    return new String[0];
  }

  @Override
  public int getMaximumCommandLength() {
    return Integer.MAX_VALUE;
  }

  @Override
  public Path[] getOutputFileNames(final Path baseName, final VersionInfo versionInfo) {
    final Path[] baseNames = super.getOutputFileNames(baseName, versionInfo);
    if (this.outputPrefix.length() > 0) {
      for (int i = 0; i < baseNames.length; i++) {
        baseNames[i] = Path.of(this.outputPrefix + baseNames[i]);
      }
    }
    return baseNames;
  }

  @Override
  public String[] getOutputFileSwitch(final Path outputFile) {
    return GccProcessor.getOutputFileSwitch("rvs", outputFile);
  }

  @Override
  public boolean isCaseSensitive() {
    return true;
  }

  @Override
  public void link(final CCTask task, final Path outputFile, final List<Path> sourceFiles,
      final CommandLineLinkerConfiguration config) throws BuildException {
    //
    // if there is an existing library then
    // we must delete it before executing "ar"
    try {
      Files.deleteIfExists(outputFile);
    } catch (IOException e) {
        throw new BuildException("Unable to delete " + outputFile);
    }
    //
    // delegate to CommandLineLinker
    //
    super.link(task, outputFile, sourceFiles, config);
  }
}
