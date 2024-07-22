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
package com.github.maven_nar.cpptasks.gcc.cross;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import org.apache.tools.ant.BuildException;

import com.github.maven_nar.cpptasks.CCTask;
import com.github.maven_nar.cpptasks.LinkerParam;
import com.github.maven_nar.cpptasks.compiler.CommandLineLinkerConfiguration;
import com.github.maven_nar.cpptasks.compiler.LinkType;
import com.github.maven_nar.cpptasks.compiler.Linker;
import com.github.maven_nar.cpptasks.gcc.AbstractLdLinker;

/**
 * Adapter for the 'ld' linker
 *
 * @author Curt Arnold
 */
public final class LdLinker extends AbstractLdLinker {
  private static final String[] libtoolObjFiles = new String[] {
      ".fo", ".a", ".lib", ".dll", ".so", ".sl"
  };
  private static final String[] objFiles = new String[] {
      ".o", ".a", ".lib", ".dll", ".so", ".sl"
  };
  private static final String[] discardFiles = new String[0];
  private static final LdLinker dllLinker = new LdLinker("ld", objFiles, discardFiles, "lib", ".so", false,
      new LdLinker("ld", objFiles, discardFiles, "lib", ".so", true, null));
  private static final LdLinker instance = new LdLinker("ld", objFiles, discardFiles, "", "", false, null);

  public static LdLinker getInstance() {
    return instance;
  }

  private File[] libDirs;

  private LdLinker(final String command, final String[] extensions, final String[] ignoredExtensions,
      final String outputPrefix, final String outputSuffix, final boolean isLibtool, final LdLinker libtoolLinker) {
    super(command, "-version", extensions, ignoredExtensions, outputPrefix, outputSuffix, isLibtool, libtoolLinker);
  }

  @Override
  protected Object clone() throws CloneNotSupportedException {
    final LdLinker clone = (LdLinker) super.clone();
    return clone;
  }

  @Override
  public Linker getLinker(final LinkType type) {
    if (type.isStaticLibrary()) {
      return GccLibrarian.getInstance();
    }
    if (type.isSharedLibrary()) {
      return dllLinker;
    }
    return instance;
  }

  @Override
  public void link(final CCTask task, final Path outputFile, final List<Path> sourceFiles,
      final CommandLineLinkerConfiguration config) throws BuildException {
    try {
      final LdLinker clone = (LdLinker) this.clone();
      final LinkerParam param = config.getParam("target");
      if (param != null) {
        clone.setCommand(param.getValue() + "-" + this.getCommand());
      }
      clone.superlink(task, outputFile, sourceFiles, config);
    } catch (final CloneNotSupportedException e) {
      superlink(task, outputFile, sourceFiles, config);
    }
  }

  private void superlink(final CCTask task, final Path outputFile, final List<Path> sourceFiles,
      final CommandLineLinkerConfiguration config) throws BuildException {
    super.link(task, outputFile, sourceFiles, config);
  }
}
