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
import java.util.Properties;

import junit.framework.TestCase;

import org.junit.Assert;

import com.github.maven_nar.ArgLine;

/**
 * Tests adding the NAR library directories to an explicit java.library.path.
 */
public class TestJavaLibraryPath extends TestCase {
  private static final String S = File.pathSeparator;

  public final void testNoExplicitPath() {
    Assert.assertNull(ArgLine.extendLibraryPath(null, "/a"));
    Assert.assertEquals("-Xmx1g", ArgLine.extendLibraryPath("-Xmx1g", "/a"));
  }

  public final void testAppendedToExplicitPath() {
    Assert.assertEquals("-Xmx1g -Djava.library.path=/opt/vendor/lib" + S + "/a" + S + "/b -ea",
        ArgLine.extendLibraryPath("-Xmx1g -Djava.library.path=/opt/vendor/lib -ea", "/a" + S + "/b"));
  }

  public final void testQuotedAndSpaces() {
    Assert.assertEquals("\"-Djava.library.path=/opt/my lib" + S + "/a\"",
        ArgLine.extendLibraryPath("-Djava.library.path=\"/opt/my lib\"", "/a"));
    Assert.assertEquals("\"-Djava.library.path=/opt/lib" + S + "/my dir\"",
        ArgLine.extendLibraryPath("-Djava.library.path=/opt/lib", "/my dir"));
  }

  public final void testEmptyExplicitPath() {
    Assert.assertEquals("-Djava.library.path=/a", ArgLine.extendLibraryPath("-Djava.library.path=", "/a"));
  }

  public final void testWholeArgumentQuoted() {
    Assert.assertEquals("-ea \"-Djava.library.path=/opt/my lib" + S + "/a\" -Xmx1g",
        ArgLine.extendLibraryPath("-ea \"-Djava.library.path=/opt/my lib\" -Xmx1g", "/a"));
    Assert.assertEquals("\"-Djava.library.path=/opt/my lib" + S + "/a\"",
        ArgLine.extendLibraryPath("'-Djava.library.path=/opt/my lib'", "/a"));
  }

  public final void testLastDefinitionExtended() {
    Assert.assertEquals("-Djava.library.path=/parent -Djava.library.path=/child" + S + "/a",
        ArgLine.extendLibraryPath("-Djava.library.path=/parent -Djava.library.path=/child", "/a"));
  }

  public final void testQuotesInPath() {
    Assert.assertEquals("\"-Djava.library.path=/opt/O'Brien/lib" + S + "/a\"",
        ArgLine.extendLibraryPath("-Djava.library.path=\"/opt/O'Brien/lib\"", "/a"));
    Assert.assertEquals("'-Djava.library.path=/opt/say \"hi\"" + S + "/a'",
        ArgLine.extendLibraryPath("'-Djava.library.path=/opt/say \"hi\"'", "/a"));
  }

  public final void testBareDefinition() {
    Assert.assertEquals("-ea -Djava.library.path=/a",
        ArgLine.extendLibraryPath("-ea -Djava.library.path", "/a"));
    Assert.assertEquals("-Djava.library.path=/vendor -Djava.library.path=/a",
        ArgLine.extendLibraryPath("-Djava.library.path=/vendor -Djava.library.path", "/a"));
    // a different property that merely starts the same way
    Assert.assertEquals("-Djava.library.pathx=/b",
        ArgLine.extendLibraryPath("-Djava.library.pathx=/b", "/a"));
  }

  public final void testLateProperties() {
    final Properties properties = new Properties();
    properties.setProperty("nativeArgs", "-Djava.library.path=/vendor");
    properties.setProperty("argLine", "-javaagent:\"C:/Users/John Doe/jacoco.jar\"=destfile=x");
    Assert.assertEquals("-Xmx1g -Djava.library.path=/vendor",
        ArgLine.expandLateProperties("-Xmx1g @{nativeArgs}", properties));
    Assert.assertEquals("-javaagent:\"C:/Users/John Doe/jacoco.jar\"=destfile=x @{undefined} @{",
        ArgLine.expandLateProperties("@{argLine} @{undefined} @{", properties));
    Assert.assertNull(ArgLine.expandLateProperties(null, properties));
    // then the expanded override is extended
    Assert.assertEquals("-Djava.library.path=/vendor" + S + "/a", ArgLine.extendLibraryPath(
        ArgLine.expandLateProperties("@{nativeArgs}", properties), "/a"));
  }

  public final void testUnrelatedArgumentsUnchanged() {
    final String jacoco = "-javaagent:\"C:\\Users\\John Doe\\jacoco.jar\"=destfile=x -Dfoo=-Djava.library.path=x";
    Assert.assertEquals(jacoco, ArgLine.extendLibraryPath(jacoco, "/a"));
  }

  public final void testEscapedQuotes() {
    // an escaped quote before the definition doesn't swallow it
    Assert.assertEquals("-Dnote=\"a\\\"b\" -Djava.library.path=/vendor" + S + "/a",
        ArgLine.extendLibraryPath("-Dnote=\"a\\\"b\" -Djava.library.path=/vendor", "/a"));
    // nor does one inside it absorb the next option
    Assert.assertEquals("'-Djava.library.path=/v\\\"x" + S + "/a' -ea",
        ArgLine.extendLibraryPath("-Djava.library.path=/v\\\"x -ea", "/a"));
  }

  public final void testBothQuotes() {
    // Surefire can't take an argument with both kinds of quote
    Assert.assertNull(ArgLine.extendLibraryPath("'-Djava.library.path=/opt/say \"hi\"'", "/O'Brien"));
  }
}
