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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.Assert;

import com.github.maven_nar.AOL;
import com.github.maven_nar.Library;
import com.github.maven_nar.NarInfo;
import com.github.maven_nar.Target;
import com.github.maven_nar.TargetDeclaration;

/**
 * Tests describing declared targets in the main nar.properties.
 */
public class TestTarget extends TestCase {
  private static final String COORDINATE = "com.example:lib:nar";

  private static final AOL LINUX = new AOL("amd64-Linux-gpp");

  private static final AOL WINDOWS = new AOL("amd64-Windows-msvc");

  private static Target target(final String aol, final String... types) {
    final Target target = new Target();
    target.setAol(aol);
    target.setTypes(new ArrayList<>(List.of(types)));
    return target;
  }

  private static NarInfo narInfo() throws Exception {
    return new NarInfo("com.example", "lib", "1.0", new SystemStreamLog());
  }

  private static void declare(final List<Target> targets, final NarInfo narInfo) throws Exception {
    declare(targets, narInfo, Set.of(), Set.of(), null);
  }

  // as for a project com.example:lib:1.0 building library lib-1.0 and
  // executable lib
  private static void declare(final List<Target> targets, final NarInfo narInfo, final Set<String> presetKeys,
      final Set<String> builtTypes, final String libsName) throws Exception {
    TargetDeclaration.declare(Target.typesByAol(targets), narInfo, presetKeys, builtTypes,
        new TargetDeclaration.Defaults(COORDINATE, "lib-1.0", "lib", libsName));
  }

  // a log keeping its warnings
  private static SystemStreamLog warningsInto(final List<String> warnings) {
    return new SystemStreamLog() {
      @Override
      public void warn(final CharSequence content) {
        warnings.add(content.toString());
      }
    };
  }

  private static List<Target> targets() {
    return List.of(target("amd64-Linux-gpp", "shared", "jni"),
        target("amd64-Windows-msvc", "shared", "static", "jni"));
  }

  public final void testPreferredBindingWithoutSharedOrStatic() throws Exception {
    // as the build for the target prefers it, not the first type declared
    final NarInfo narInfo = narInfo();
    declare(List.of(target("amd64-Linux-gpp", "executable", "jni")), narInfo);
    Assert.assertEquals("jni", narInfo.getInfo().getProperty("amd64-Linux-gpp.libs.binding"));
  }

  public final void testEveryTargetIsDescribed() throws Exception {
    final NarInfo narInfo = narInfo();
    declare(targets(), narInfo);
    Assert.assertEquals(List.of(COORDINATE + ":amd64-Windows-msvc-static"),
        narInfo.getAttachedNars(WINDOWS, Library.STATIC));
    Assert.assertEquals(List.of(COORDINATE + ":amd64-Linux-gpp-shared"),
        narInfo.getAttachedNars(LINUX, Library.SHARED));
    Assert.assertEquals("shared", narInfo.getBinding(WINDOWS, null));
    Assert.assertEquals("lib-1.0", narInfo.getOutput(WINDOWS, null));
    Assert.assertEquals("lib-1.0", narInfo.getOutput(LINUX, null));
  }

  public final void testUndeclaredTypeIsEmptyForThatAol() throws Exception {
    final NarInfo narInfo = narInfo();
    declare(targets(), narInfo);
    // Linux ships no static library, though the generic key names one
    Assert.assertEquals("", narInfo.getProperty(LINUX, "nar.static"));
    Assert.assertEquals(COORDINATE + ":${aol}-static", narInfo.getProperty(null, "nar.static"));
  }

  public final void testPresetKeysWin() throws Exception {
    final NarInfo narInfo = narInfo();
    narInfo.setBinding(WINDOWS, "static");
    narInfo.setOutput(WINDOWS, "custom");
    narInfo.getInfo().setProperty("amd64-Windows-msvc.nar.shared", "other:lib:nar:x");
    declare(targets(), narInfo, narInfo.getInfo().stringPropertyNames(), Set.of(), null);
    Assert.assertEquals("static", narInfo.getBinding(WINDOWS, null));
    Assert.assertEquals("custom", narInfo.getOutput(WINDOWS, null));
    Assert.assertEquals("other:lib:nar:x", narInfo.getProperty(WINDOWS, "nar.shared"));
  }

  public final void testBindingPreference() throws Exception {
    final NarInfo narInfo = narInfo();
    declare(List.of(target("a-B-c", "jni", "static"), target("d-E-f", "executable")), narInfo);
    Assert.assertEquals("static", narInfo.getBinding(new AOL("a-B-c"), null));
    Assert.assertEquals("executable", narInfo.getBinding(new AOL("d-E-f"), null));
    Assert.assertEquals("lib", narInfo.getOutput(new AOL("d-E-f"), null));
  }

  public final void testSameResultOnEveryMachine() throws Exception {
    // what each build generated for its own AOL is replaced by what's declared
    final NarInfo linux = narInfo();
    linux.setBinding(LINUX, "jni");
    linux.setOutput(LINUX, "lib");
    final NarInfo windows = narInfo();
    windows.setBinding(WINDOWS, "static");
    declare(targets(), linux);
    declare(targets(), windows);
    Assert.assertEquals(linux.getInfo(), windows.getInfo());
  }

  public final void testUndeclaredBuildsAreReported() throws Exception {
    final List<String> warnings = new ArrayList<>();
    final SystemStreamLog log = warningsInto(warnings);
    TargetDeclaration.checkBuilt(Target.typesByAol(targets()),
        Map.of("amd64-Linux-gpp", Set.of("shared", "static"), "x86-Windows-msvc", Set.of("shared")), log);
    Assert.assertEquals(2, warnings.size());
    Assert.assertTrue(warnings.toString(), warnings.toString().contains("amd64-Linux-gpp-static"));
    Assert.assertTrue(warnings.toString(), warnings.toString().contains("x86-Windows-msvc-shared"));
  }

  public final void testEveryOtherTypeIsEmpty() throws Exception {
    // a static library built here, though no target ships one
    final NarInfo narInfo = narInfo();
    declare(List.of(target("amd64-Linux-gpp", "shared")), narInfo, Set.of(), Set.of("shared", "static"), null);
    for (final String type : new String[] { "static", "jni", "executable", "plugin" }) {
      Assert.assertEquals(type, "", narInfo.getProperty(LINUX, "nar." + type));
    }
  }

  public final void testMappedAolGetsTheTargetsKeys() throws Exception {
    final NarInfo narInfo = narInfo();
    narInfo.getInfo().setProperty("x86_64-Linux-gpp", "amd64-Linux-gpp");
    declare(targets(), narInfo);
    final AOL consumer = new AOL("x86_64-Linux-gpp");
    Assert.assertEquals("", narInfo.getProperty(consumer, "nar.static"));
    Assert.assertEquals(List.of(COORDINATE + ":amd64-Linux-gpp-shared"),
        narInfo.getAttachedNars(consumer, Library.SHARED));
    Assert.assertEquals("shared", narInfo.getBinding(consumer, null));
    Assert.assertEquals("lib-1.0", narInfo.getOutput(consumer, null));
  }

  public final void testLibsNameForEveryTarget() throws Exception {
    final NarInfo narInfo = narInfo();
    declare(targets(), narInfo, Set.of(), Set.of(), "a b");
    Assert.assertEquals("a b", narInfo.getLibs(LINUX));
    Assert.assertEquals("a b", narInfo.getLibs(WINDOWS));
  }

  public final void testIncompleteTargetRejected() throws Exception {
    for (final Target target : new Target[] { target(null, "shared"), target("amd64-Linux-gpp") }) {
      try {
        declare(List.of(target), narInfo());
        fail("accepted " + target.getAol() + " " + target.getTypes());
      } catch (final MojoExecutionException expected) {
        // expected
      }
    }
  }

  public final void testSpellingOfAolInCheckBuilt() throws Exception {
    final List<String> warnings = new ArrayList<>();
    final SystemStreamLog log = warningsInto(warnings);
    TargetDeclaration.checkBuilt(Target.typesByAol(List.of(target("amd64-Linux-g++", "shared"))),
        Map.of("amd64-Linux-gpp", Set.of("shared")), log);
    Assert.assertEquals(List.of(), warnings);
  }

  public final void testMappingToUnnormalizedAol() throws Exception {
    final NarInfo narInfo = narInfo();
    narInfo.getInfo().setProperty("x86_64-Linux-gpp", "amd64-Linux-g++");
    declare(targets(), narInfo);
    Assert.assertEquals("", narInfo.getProperty(new AOL("x86_64-Linux-gpp"), "nar.static"));
  }

  public final void testPresetGenericBinding() throws Exception {
    final NarInfo narInfo = narInfo();
    narInfo.setBinding(null, "static");
    declare(targets(), narInfo, Set.of("libs.binding"), Set.of(), null);
    // Windows ships a static library, Linux doesn't
    Assert.assertEquals("static", narInfo.getBinding(WINDOWS, null));
    Assert.assertEquals("shared", narInfo.getBinding(LINUX, null));
  }

  public final void testPresetGenericCoordinateApplies() throws Exception {
    final NarInfo narInfo = narInfo();
    narInfo.getInfo().setProperty("nar.shared", "other:bundle:nar:${aol}-shared");
    declare(targets(), narInfo, Set.of("nar.shared"), Set.of(), null);
    Assert.assertEquals(List.of("other:bundle:nar:${aol}-shared"), narInfo.getAttachedNars(LINUX, Library.SHARED));
    // types a target doesn't ship are still excluded
    Assert.assertEquals("", narInfo.getProperty(LINUX, "nar.static"));
  }

  public final void testAliasGetsTheTargetsPresetValues() throws Exception {
    final NarInfo narInfo = narInfo();
    narInfo.getInfo().setProperty("x86_64-Linux-gpp", "amd64-Linux-gpp");
    narInfo.setOutput(LINUX, "custom");
    narInfo.getInfo().setProperty("x86_64-Linux-gpp.libs.binding", "jni");
    declare(targets(), narInfo, Set.of("amd64-Linux-gpp.output", "x86_64-Linux-gpp.libs.binding"), Set.of(), null);
    final AOL alias = new AOL("x86_64-Linux-gpp");
    Assert.assertEquals("custom", narInfo.getOutput(alias, null));
    // the alias's own preset wins
    Assert.assertEquals("jni", narInfo.getBinding(alias, null));
  }

  public final void testRedeclaringRefreshesGeneratedEntries() throws Exception {
    // target/classes holds what the previous build generated
    final NarInfo narInfo = narInfo();
    narInfo.getInfo().setProperty("nar.static", "other:bundle:nar:${aol}-static");
    final Set<String> preset = Set.of("nar.static");
    declare(List.of(target("amd64-Linux-gpp", "shared")), narInfo, preset, Set.of(), null);
    Assert.assertEquals("", narInfo.getProperty(LINUX, "nar.static"));
    declare(List.of(target("amd64-Linux-gpp", "shared", "static")), narInfo, preset, Set.of(), null);
    Assert.assertEquals(List.of("other:bundle:nar:${aol}-static"), narInfo.getAttachedNars(LINUX, Library.STATIC));
  }

  public final void testInvalidTypeOrDuplicateRejected() throws Exception {
    for (final List<Target> targets : List.of(List.of(target("amd64-Linux-gpp", "shard")),
        List.of(target("amd64-Linux-gpp", "none")),
        List.of(target("amd64-Linux-gpp", "shared"), target("amd64-Linux-g++", "static")))) {
      try {
        declare(targets, narInfo());
        fail("accepted " + targets.size() + " targets");
      } catch (final MojoExecutionException expected) {
        // expected
      }
    }
  }

  public final void testPresetMissingFromLoadedProperties() throws Exception {
    // the preset came from a resource whose copy another one replaced
    final NarInfo narInfo = narInfo();
    declare(targets(), narInfo, Set.of("nar.static"), Set.of(), null);
    Assert.assertEquals(List.of(COORDINATE + ":amd64-Windows-msvc-static"),
        narInfo.getAttachedNars(WINDOWS, Library.STATIC));
  }

  public final void testMappingWithHyphenatedLinker() throws Exception {
    final NarInfo narInfo = narInfo();
    narInfo.getInfo().setProperty("x86_64-Linux-gpp-12", "amd64-Linux-gpp");
    declare(targets(), narInfo);
    Assert.assertEquals("", narInfo.getProperty(new AOL("x86_64-Linux-gpp-12"), "nar.static"));
  }

  public final void testMappingWithDottedLinker() throws Exception {
    final NarInfo narInfo = narInfo();
    narInfo.getInfo().setProperty("x86_64-Linux-gpp-12.2", "amd64-Linux-gpp");
    // an AOL-prefixed property isn't a mapping
    narInfo.getInfo().setProperty("amd64-Linux-gpp.libs.names", "lib-1.0");
    declare(targets(), narInfo);
    Assert.assertEquals("", narInfo.getProperty(new AOL("x86_64-Linux-gpp-12.2"), "nar.static"));
    Assert.assertNull(narInfo.getInfo().getProperty("amd64-Linux-gpp.libs.names.nar.static"));
  }
}
