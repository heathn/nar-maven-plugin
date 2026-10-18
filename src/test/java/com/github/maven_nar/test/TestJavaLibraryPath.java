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

import java.io.File;

import junit.framework.TestCase;

import org.junit.Assert;

import com.github.maven_nar.NarIntegrationTestMojo;

/**
 * Tests adding the NAR library directories to an explicit java.library.path.
 */
public class TestJavaLibraryPath extends TestCase {
  private static final String S = File.pathSeparator;

  public final void testNoExplicitPath() {
    Assert.assertNull(NarIntegrationTestMojo.addToJavaLibraryPath(null, "/a"));
    Assert.assertEquals("-Xmx1g", NarIntegrationTestMojo.addToJavaLibraryPath("-Xmx1g", "/a"));
  }

  public final void testAppendedToExplicitPath() {
    Assert.assertEquals("-Xmx1g -Djava.library.path=/opt/vendor/lib" + S + "/a" + S + "/b -ea",
        NarIntegrationTestMojo.addToJavaLibraryPath("-Xmx1g -Djava.library.path=/opt/vendor/lib -ea", "/a" + S + "/b"));
  }

  public final void testQuotedAndSpaces() {
    Assert.assertEquals("\"-Djava.library.path=/opt/my lib" + S + "/a\"",
        NarIntegrationTestMojo.addToJavaLibraryPath("-Djava.library.path=\"/opt/my lib\"", "/a"));
    Assert.assertEquals("\"-Djava.library.path=/opt/lib" + S + "/my dir\"",
        NarIntegrationTestMojo.addToJavaLibraryPath("-Djava.library.path=/opt/lib", "/my dir"));
  }

  public final void testEmptyExplicitPath() {
    Assert.assertEquals("-Djava.library.path=/a", NarIntegrationTestMojo.addToJavaLibraryPath("-Djava.library.path=", "/a"));
  }

  public final void testWholeArgumentQuoted() {
    Assert.assertEquals("-ea \"-Djava.library.path=/opt/my lib" + S + "/a\" -Xmx1g",
        NarIntegrationTestMojo.addToJavaLibraryPath("-ea \"-Djava.library.path=/opt/my lib\" -Xmx1g", "/a"));
    Assert.assertEquals("\"-Djava.library.path=/opt/my lib" + S + "/a\"",
        NarIntegrationTestMojo.addToJavaLibraryPath("'-Djava.library.path=/opt/my lib'", "/a"));
  }

  public final void testLastDefinitionExtended() {
    Assert.assertEquals("-Djava.library.path=/parent -Djava.library.path=/child" + S + "/a",
        NarIntegrationTestMojo.addToJavaLibraryPath("-Djava.library.path=/parent -Djava.library.path=/child", "/a"));
  }

  public final void testQuotesInPath() {
    Assert.assertEquals("\"-Djava.library.path=/opt/O'Brien/lib" + S + "/a\"",
        NarIntegrationTestMojo.addToJavaLibraryPath("-Djava.library.path=\"/opt/O'Brien/lib\"", "/a"));
    Assert.assertEquals("'-Djava.library.path=/opt/say \"hi\"" + S + "/a'",
        NarIntegrationTestMojo.addToJavaLibraryPath("'-Djava.library.path=/opt/say \"hi\"'", "/a"));
  }

  public final void testBareDefinition() {
    Assert.assertEquals("-ea -Djava.library.path=/a",
        NarIntegrationTestMojo.addToJavaLibraryPath("-ea -Djava.library.path", "/a"));
    Assert.assertEquals("-Djava.library.path=/vendor -Djava.library.path=/a",
        NarIntegrationTestMojo.addToJavaLibraryPath("-Djava.library.path=/vendor -Djava.library.path", "/a"));
    // a different property that merely starts the same way
    Assert.assertEquals("-Djava.library.pathx=/b",
        NarIntegrationTestMojo.addToJavaLibraryPath("-Djava.library.pathx=/b", "/a"));
  }

  public final void testLateProperties() {
    final java.util.Properties properties = new java.util.Properties();
    properties.setProperty("nativeArgs", "-Djava.library.path=/vendor");
    properties.setProperty("argLine", "-javaagent:\"C:/Users/John Doe/jacoco.jar\"=destfile=x");
    Assert.assertEquals("-Xmx1g -Djava.library.path=/vendor",
        NarIntegrationTestMojo.expandLateProperties("-Xmx1g @{nativeArgs}", properties));
    Assert.assertEquals("-javaagent:\"C:/Users/John Doe/jacoco.jar\"=destfile=x @{undefined} @{",
        NarIntegrationTestMojo.expandLateProperties("@{argLine} @{undefined} @{", properties));
    Assert.assertNull(NarIntegrationTestMojo.expandLateProperties(null, properties));
    // then the expanded override is extended
    Assert.assertEquals("-Djava.library.path=/vendor" + S + "/a", NarIntegrationTestMojo.addToJavaLibraryPath(
        NarIntegrationTestMojo.expandLateProperties("@{nativeArgs}", properties), "/a"));
  }

  public final void testUnrelatedArgumentsUnchanged() {
    final String jacoco = "-javaagent:\"C:\\Users\\John Doe\\jacoco.jar\"=destfile=x -Dfoo=-Djava.library.path=x";
    Assert.assertEquals(jacoco, NarIntegrationTestMojo.addToJavaLibraryPath(jacoco, "/a"));
  }
}
