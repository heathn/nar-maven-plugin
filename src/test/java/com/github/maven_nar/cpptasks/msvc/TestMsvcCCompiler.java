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
package com.github.maven_nar.cpptasks.msvc;

import java.io.File;
import java.util.Vector;

import junit.framework.TestCase;

/**
 * Test Microsoft C/C++ compiler adapter
 * 
 */
public class TestMsvcCCompiler extends TestCase {
  public TestMsvcCCompiler(final String name) {
    super(name);
  }

  public void testDebug() {
    final MsvcCCompiler compiler = MsvcCCompiler.getInstance();
    final Vector args = new Vector();
    final File objDir = new File("dummy");
    compiler.setObjDir(objDir);
    compiler.addDebugSwitch(args);
    compiler.addPathSwitch(args);
    assertEquals(6, args.size());
    assertEquals("/Zi", args.elementAt(0));
    assertEquals("/FS", args.elementAt(1));
    assertEquals("/Od", args.elementAt(2));
    assertEquals("/RTC1", args.elementAt(3));
    assertEquals("/D_DEBUG", args.elementAt(4));
    assertEquals("/Fd" + objDir.getAbsolutePath() + File.separator, args.elementAt(5));
  }
}
