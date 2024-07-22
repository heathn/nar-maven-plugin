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

import junit.framework.TestCase;

/**
 */
public abstract class TestCompilerConfiguration extends TestCase {
  public TestCompilerConfiguration(final String name) {
    super(name);
  }

  protected abstract CompilerConfiguration create();

  public String getObjectFileExtension() {
    return ".o";
  }

  public void testBid() {
    final CompilerConfiguration compiler = create();
    int bid = compiler.bid(Path.of("c:/foo\\bar\\hello.c"));
    assertEquals(100, bid);
    bid = compiler.bid(Path.of("c:/foo\\bar/hello.c"));
    assertEquals(100, bid);
    bid = compiler.bid(Path.of("c:/foo\\bar\\hello.h"));
    assertEquals(1, bid);
    bid = compiler.bid(Path.of("c:/foo\\bar/hello.h"));
    assertEquals(1, bid);
    bid = compiler.bid(Path.of("c:/foo\\bar/hello.pas"));
    assertEquals(0, bid);
    bid = compiler.bid(Path.of("c:/foo\\bar/hello.java"));
    assertEquals(0, bid);
  }

  public void testGetOutputFileName1() {
    final CompilerConfiguration compiler = create();
    final Path input = Path.of("c:/foo\\bar\\hello.c");
    //
    // may cause IllegalStateException since
    // setPlatformInfo has not been called
    try {
      final Path[] output = compiler.getOutputFileNames(input, null);
    } catch (final java.lang.IllegalStateException ex) {
    }
  }

  public void testGetOutputFileName2() {
    final CompilerConfiguration compiler = create();
//    String[] output = compiler.getOutputFileNames("c:\\foo\\bar\\hello.c", null);  Windows only, on *nix gets treated as filename not pathed.
    Path[] output = compiler.getOutputFileNames(Path.of("c:/foo/bar/hello.c"), null);
    Path[] output2 = compiler.getOutputFileNames(Path.of("c:/foo/bar/fake/../hello.c"), null);
    assertEquals(output[0], output2[0]); // files in same location get mangled same way - full path

    output = compiler.getOutputFileNames(Path.of("hello.c"), null);
    assertNotSame(output[0], output2[0]); // files in different folders get mangled in different way
    
    output2 = compiler.getOutputFileNames(Path.of("fake/../hello.c"), null);
    assertEquals(output[0], output2[0]); // files in same location get mangled same way - relative path
    
    output = compiler.getOutputFileNames(Path.of("c:/foo/bar/hello.h"), null);
    assertEquals(0, output.length);
    output = compiler.getOutputFileNames(Path.of("c:/foo/bar/fake/../hello.h"), null);
    assertEquals(0, output.length);
  }
}
