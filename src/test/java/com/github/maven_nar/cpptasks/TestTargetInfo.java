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
package com.github.maven_nar.cpptasks;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import junit.framework.TestCase;

import org.apache.tools.ant.BuildException;

import com.github.maven_nar.cpptasks.compiler.CompilerConfiguration;
import com.github.maven_nar.cpptasks.compiler.ProgressMonitor;

/**
 * A description of a file built or to be built
 */
public class TestTargetInfo extends TestCase {
  private class DummyConfiguration implements CompilerConfiguration {
    @Override
    public int bid(final Path filename) {
      return 1;
    }

    public void close() {
    }

    @Override
    public void compile(final CCTask task, final Path workingDir, final Path[] source, final boolean relentless,
        final ProgressMonitor monitor) throws BuildException {
      throw new BuildException("Not implemented");
    }

    @Override
    public CompilerConfiguration[] createPrecompileConfigurations(final Path file, final Path[] exceptFiles) {
      return null;
    }

    @Override
    public String getIdentifier() {
      return "dummy";
    }

    public String[] getIncludeDirectories() {
      return new String[0];
    }

    @Override
    public String getIncludePathIdentifier() {
      return "dummyIncludePath";
    }

    @Override
    public Path[] getOutputFileNames(final Path inputFile, final VersionInfo versionInfo) {
      return new Path[0];
    }

    @Override
    public CompilerParam getParam(final String name) {
      return null;
    }

    @Override
    public List<ProcessorParam> getParams() {
      return Collections.emptyList();
    }

    @Override
    public boolean getRebuild() {
      return false;
    }

    @Override
    public boolean isPrecompileGeneration() {
      return true;
    }

    @Override
    public DependencyInfo parseIncludes(final CCTask task, final Path baseDir, final Path file) {
      return null;
    }
  }

  public TestTargetInfo(final String name) {
    super(name);
  }

  public void testConstructorNullConfig() {
    try {
      new TargetInfo(null, List.of(Path.of("")), null, Path.of(""), false);
      fail("Didn't throw exception");
    } catch (final NullPointerException ex) {
    }
  }

  public void testConstructorNullOutput() {
    final CompilerConfiguration config = new DummyConfiguration();
    try {
      new TargetInfo(config, List.of(Path.of("")), null, null, false);
      fail("Didn't throw exception");
    } catch (final NullPointerException ex) {
    }
  }

  public void testConstructorNullSource() {
    final CompilerConfiguration config = new DummyConfiguration();
    try {
      new TargetInfo(config, null, null, Path.of(""), false);
      fail("Didn't throw exception");
    } catch (final NullPointerException ex) {
    }
  }

  public void testGetRebuild() {
    final CompilerConfiguration config = new DummyConfiguration();
    TargetInfo targetInfo = new TargetInfo(config, List.of(Path.of("FoO.BaR")),
        null, Path.of("foo.o"), false);
    assertEquals(false, targetInfo.getRebuild());
    targetInfo = new TargetInfo(config, List.of(Path.of("FoO.BaR")), null,
        Path.of("foo.o"), true);
    assertEquals(true, targetInfo.getRebuild());
  }

  public void testGetSource() {
    final CompilerConfiguration config = new DummyConfiguration();
    final TargetInfo targetInfo = new TargetInfo(config,
        List.of(Path.of("FoO.BaR")), null, Path.of("foo.o"), false);
    final String source = targetInfo.getSources().get(0).getFileName().toString();
    assertEquals(source, "FoO.BaR");
  }

  public void testHasSameSource() {
    final CompilerConfiguration config = new DummyConfiguration();
    final TargetInfo targetInfo = new TargetInfo(config,
        List.of(Path.of("foo.bar")), null, Path.of("foo.o"), false);
    boolean hasSame = targetInfo.getSources().get(0).equals(Path.of("foo.bar"));
    assertTrue(hasSame);
    hasSame = targetInfo.getSources().get(0).equals(Path.of("boo.far"));
    assertEquals(hasSame, false);
  }

  public void testMustRebuild() {
    final CompilerConfiguration config = new DummyConfiguration();
    final TargetInfo targetInfo = new TargetInfo(config,
        List.of(Path.of("FoO.BaR")), null, Path.of("foo.o"), false);
    assertEquals(false, targetInfo.getRebuild());
    targetInfo.mustRebuild();
    assertEquals(true, targetInfo.getRebuild());
  }
}
