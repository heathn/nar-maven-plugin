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
package com.github.maven_nar.test;

import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

import com.github.maven_nar.Library;

/**
 * The type consumers bind to among an AOL's, whatever order they're in.
 */
public class TestPreferredBinding extends TestCase {

  public final void testSharedFirst() {
    assertEquals("shared", Library.preferredBinding(List.of("executable", "jni", "static", "shared")));
  }

  public final void testStaticOverTheRest() {
    assertEquals("static", Library.preferredBinding(List.of("executable", "plugin", "jni", "static")));
  }

  public final void testExecutableLast() {
    assertEquals("jni", Library.preferredBinding(List.of("executable", "jni")));
    assertEquals("plugin", Library.preferredBinding(Set.of("executable", "plugin")));
    assertEquals("executable", Library.preferredBinding(List.of("executable")));
  }

  public final void testUnknownTypesAfterKnownOnes() {
    assertEquals("executable", Library.preferredBinding(List.of("none", "executable")));
    assertEquals("a", Library.preferredBinding(List.of("b", "a")));
  }
}
