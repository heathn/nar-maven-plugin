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
package com.github.maven_nar.cpptasks.hp;

import junit.framework.TestCase;

import java.nio.file.Path;

import com.github.maven_nar.cpptasks.compiler.AbstractProcessor;

/**
 * Test HP aCC compiler adapter
 * 
 */
// TODO Since aCCCompiler extends GccCompatibleCCompiler, this test
// should probably extend TestGccCompatibleCCompiler.
public class TestaCCCompiler extends TestCase {
  public TestaCCCompiler(final String name) {
    super(name);
  }

  public void testBidAssembly() {
    final aCCCompiler compiler = aCCCompiler.getInstance();
    assertEquals(AbstractProcessor.DEFAULT_PROCESS_BID, compiler.bid(Path.of("foo.s")));
  }

  public void testBidC() {
    final aCCCompiler compiler = aCCCompiler.getInstance();
    assertEquals(AbstractProcessor.DEFAULT_PROCESS_BID, compiler.bid(Path.of("foo.c")));
  }

  public void testBidCpp() {
    final aCCCompiler compiler = aCCCompiler.getInstance();
    assertEquals(AbstractProcessor.DEFAULT_PROCESS_BID, compiler.bid(Path.of("foo.C")));
  }

  public void testBidCpp2() {
    final aCCCompiler compiler = aCCCompiler.getInstance();
    assertEquals(AbstractProcessor.DEFAULT_PROCESS_BID, compiler.bid(Path.of("foo.cc")));
  }

  public void testBidCpp3() {
    final aCCCompiler compiler = aCCCompiler.getInstance();
    assertEquals(AbstractProcessor.DEFAULT_PROCESS_BID, compiler.bid(Path.of("foo.CC")));
  }

  public void testBidCpp4() {
    final aCCCompiler compiler = aCCCompiler.getInstance();
    assertEquals(AbstractProcessor.DEFAULT_PROCESS_BID, compiler.bid(Path.of("foo.cxx")));
  }

  public void testBidCpp5() {
    final aCCCompiler compiler = aCCCompiler.getInstance();
    assertEquals(AbstractProcessor.DEFAULT_PROCESS_BID, compiler.bid(Path.of("foo.CXX")));
  }

  public void testBidCpp6() {
    final aCCCompiler compiler = aCCCompiler.getInstance();
    assertEquals(AbstractProcessor.DEFAULT_PROCESS_BID, compiler.bid(Path.of("foo.cpp")));
  }

  public void testBidCpp7() {
    final aCCCompiler compiler = aCCCompiler.getInstance();
    assertEquals(AbstractProcessor.DEFAULT_PROCESS_BID, compiler.bid(Path.of("foo.CPP")));
  }

  public void testBidCpp8() {
    final aCCCompiler compiler = aCCCompiler.getInstance();
    assertEquals(AbstractProcessor.DEFAULT_PROCESS_BID, compiler.bid(Path.of("foo.c++")));
  }

  public void testBidCpp9() {
    final aCCCompiler compiler = aCCCompiler.getInstance();
    assertEquals(AbstractProcessor.DEFAULT_PROCESS_BID, compiler.bid(Path.of("foo.C++")));
  }

  public void testBidPreprocessed() {
    final aCCCompiler compiler = aCCCompiler.getInstance();
    assertEquals(AbstractProcessor.DEFAULT_PROCESS_BID, compiler.bid(Path.of("foo.i")));
  }
}
