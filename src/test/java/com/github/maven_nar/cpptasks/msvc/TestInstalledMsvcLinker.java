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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Test for Microsoft Developer Studio linker
 *
 * Override create to test concrete compiler implementions
 */
public class TestInstalledMsvcLinker extends TestMsvcLinker {
  public TestInstalledMsvcLinker(final String name) {
    super(name);
  }

  public void failingtestGetLibraryPath() {
    final List<Path> libpath = MsvcLinker.getInstance().getLibraryPath();
    //
    // unless you tweak the library path
    // it should have more thean three entries
    assertTrue(libpath.size() >= 2);
    //
    // check if these files can be found
    //
    final String[] libnames = new String[] {
        "kernel32.lib", "advapi32.lib", "msvcrt.lib", "mfc42.lib", "mfc70.lib"
    };
    final boolean[] libfound = new boolean[libnames.length];
    for (final Path element : libpath) {
      for (int j = 0; j < libnames.length; j++) {
        final Path libfile = element.resolve(libnames[j]);
        if (Files.exists(libfile)) {
          libfound[j] = true;
        }
      }
    }
    assertTrue("kernel32 not found", libfound[0]);
    assertTrue("advapi32 not found", libfound[1]);
    assertTrue("msvcrt not found", libfound[2]);
    if (!(libfound[3] || libfound[4])) {
      fail("mfc42.lib or mfc70.lib not found");
    }
  }
}
