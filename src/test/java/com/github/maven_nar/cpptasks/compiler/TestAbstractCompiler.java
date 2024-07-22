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

import java.nio.file.Path;

import org.apache.tools.ant.BuildException;

import com.github.maven_nar.cpptasks.CCTask;
import com.github.maven_nar.cpptasks.CompilerDef;
import com.github.maven_nar.cpptasks.ProcessorDef;
import com.github.maven_nar.cpptasks.VersionInfo;
import com.github.maven_nar.cpptasks.parser.CParser;
import com.github.maven_nar.cpptasks.parser.Parser;

/**
 * Test for abstract compiler class
 *
 * Override create to test concrete compiler implementions
 */
public class TestAbstractCompiler extends TestAbstractProcessor {
  private class DummyAbstractCompiler extends AbstractCompiler {
    public DummyAbstractCompiler() {
      super(new String[] {
          ".cpp", ".c"
      }, new String[] {
          ".hpp", ".h", ".inl"
      }, ".o");
    }

    public void compile(final CCTask task, final Path[] srcfile, final Path[] outputfile,
        final CompilerConfiguration config) throws BuildException {
      throw new BuildException("Not implemented");
    }

    @Override
    public CompilerConfiguration createConfiguration(final CCTask task, final LinkType linkType,
        final ProcessorDef[] def1, final CompilerDef def2,
        final com.github.maven_nar.cpptasks.TargetDef targetPlatform, final VersionInfo versionInfo) {
      return null;
    }

    @Override
    public Parser createParser(final Path file) {
      return new CParser();
    }

    @Override
    public String getIdentifier() {
      return "dummy";
    }

    @Override
    public Linker getLinker(final LinkType type) {
      return null;
    }
  }

  public TestAbstractCompiler(final String name) {
    super(name);
  }

  @Override
  protected AbstractProcessor create() {
    return new DummyAbstractCompiler();
  }

  public void failingtestGetOutputFileName1() {
    final AbstractProcessor compiler = create();
    Path[] output = compiler.getOutputFileNames(Path.of("c:/foo\\bar\\hello.c"), null);
    assertEquals("hello" + getObjectExtension(), output[0]);
    output = compiler.getOutputFileNames(Path.of("c:/foo\\bar/hello.c"), null);
    assertEquals("hello" + getObjectExtension(), output[0]);
    output = compiler.getOutputFileNames(Path.of("hello.c"), null);
    assertEquals("hello" + getObjectExtension(), output[0]);
    output = compiler.getOutputFileNames(Path.of("c:/foo\\bar\\hello.h"), null);
    assertEquals(0, output.length);
    output = compiler.getOutputFileNames(Path.of("c:/foo\\bar/hello.h"), null);
    assertEquals(0, output.length);
  }

  protected String getObjectExtension() {
    return ".o";
  }

  public void testCanParseTlb() {
    final AbstractCompiler compiler = (AbstractCompiler) create();
    assertEquals(false, compiler.canParse(Path.of("sample.tlb")));
  }
}
