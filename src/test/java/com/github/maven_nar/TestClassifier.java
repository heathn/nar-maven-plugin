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
package com.github.maven_nar;

import junit.framework.TestCase;

public class TestClassifier extends TestCase {

  public final void testParse() {
    final Classifier classifier = Classifier.parse("amd64-Linux-gpp-shared");
    assertEquals("amd64-Linux-gpp", classifier.aol());
    assertEquals("shared", classifier.type());
    assertEquals("amd64-Linux-gpp-shared", classifier.toString());
    // a linker with a dash in it stays part of the AOL
    assertEquals("x86_64-MacOSX-g++-12", Classifier.parse("x86_64-MacOSX-g++-12-static").aol());
  }

  public final void testNotAClassifier() {
    assertNull(Classifier.parse(null));
    assertNull(Classifier.parse("noarch"));
    assertNull(Classifier.parse("-shared"));
  }
}
