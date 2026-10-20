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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

import junit.framework.TestCase;

import org.junit.Assert;

import org.apache.maven.plugin.logging.SystemStreamLog;

import com.github.maven_nar.AOL;
import com.github.maven_nar.Library;
import com.github.maven_nar.NarDescriptor;
import com.github.maven_nar.NarDescriptor.LinkPattern;
import com.github.maven_nar.NarInfo;
import com.github.maven_nar.NarUtil;

/**
 * Tests deriving and writing the descriptor of an AOL-specific NAR.
 */
public class TestNarDescriptor extends TestCase {
  private Path dir;

  @Override
  protected void setUp() throws Exception {
    this.dir = Files.createTempDirectory("descriptor");
  }

  @Override
  protected void tearDown() throws Exception {
    try (Stream<Path> paths = Files.walk(this.dir)) {
      paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
    }
  }

  private void touch(final String... names) throws Exception {
    for (final String name : names) {
      Files.createFile(this.dir.resolve(name));
    }
  }

  public final void testLinuxShared() throws Exception {
    touch("libfoo-1.0.so", "libfoo-1.0.so.1", "libbar.so.2.3.4", "notalib.txt", "libbaz.a");
    Assert.assertEquals("bar foo-1.0", NarDescriptor.linkNames(this.dir, List.of(LinkPattern.of("lib", "so*"))));
  }

  public final void testLinuxStatic() throws Exception {
    touch("libfoo.a", "libfoo.so");
    Assert.assertEquals("foo", NarDescriptor.linkNames(this.dir, List.of(LinkPattern.of("lib", "a"))));
  }

  public final void testWindowsImportLibraryWithDottedName() throws Exception {
    // leptonica's own build names its library after the upstream version
    touch("leptonica-1.87.0.lib", "leptonica-1.87.0.dll", "leptonica-1.87.0.pdb");
    Assert.assertEquals("leptonica-1.87.0", NarDescriptor.linkNames(this.dir, List.of(LinkPattern.of("", "lib"))));
  }

  public final void testNameContainingExtension() throws Exception {
    touch("libsomething.so", "libso.so.1");
    Assert.assertEquals("so something", NarDescriptor.linkNames(this.dir, List.of(LinkPattern.of("lib", "so*"))));
  }

  public final void testMissingDirectoryOrExtension() throws Exception {
    Assert.assertEquals("", NarDescriptor.linkNames(this.dir.resolve("none"), List.of(LinkPattern.of("lib", "so"))));
    touch("libfoo.so");
    Assert.assertEquals("", NarDescriptor.linkNames(this.dir, List.of(LinkPattern.of("lib", null))));
    Assert.assertEquals("libfoo", NarDescriptor.linkNames(this.dir, List.of(LinkPattern.of(null, "so"))));
  }

  public final void testWriteIsReproducible() throws Exception {
    final Properties a = new Properties();
    a.setProperty("output", "foo");
    a.setProperty("libs.names", "foo bar");
    a.setProperty("nar.descriptor.version", "2");
    final Properties b = new Properties();
    b.setProperty("nar.descriptor.version", "2");
    b.setProperty("libs.names", "foo bar");
    b.setProperty("output", "foo");
    final Path fa = this.dir.resolve("a/d.properties");
    final Path fb = this.dir.resolve("b/d.properties");
    NarUtil.storeProperties(fa, a);
    NarUtil.storeProperties(fb, b);
    Assert.assertArrayEquals(Files.readAllBytes(fa), Files.readAllBytes(fb));
    Assert.assertEquals("libs.names=foo bar\nnar.descriptor.version=2\noutput=foo\n", Files.readString(fa));
    final Properties read = new Properties();
    try (var in = Files.newInputStream(fa)) {
      read.load(in);
    }
    Assert.assertEquals(a, read);
  }

  private static final AOL LINUX = new AOL("amd64-Linux-gpp");

  private static final AOL LINUX_GCC = new AOL("amd64-Linux-gcc");

  private static final AOL WINDOWS = new AOL("amd64-Windows-msvc");

  private static NarInfo narInfo(final AOL aol, final String output) throws Exception {
    final NarInfo narInfo = new NarInfo("com.example", "lib", "1.0", new SystemStreamLog());
    narInfo.setOutput(aol, output);
    return narInfo;
  }

  private Properties describe(final NarInfo narInfo, final AOL aol, final String type, final String prefix,
      final String extension) throws Exception {
    return NarDescriptor.describe(narInfo, aol, type, this.dir,
        NarDescriptor.linkPatterns(aol.toString(), type, prefix, extension), new SystemStreamLog());
  }

  public final void testOutputPresentKeepsLinkingOutput() throws Exception {
    // a bundled library next to the project's own mustn't be linked too
    touch("liblib-1.0.so", "libbundled.so.3");
    final Properties d = describe(narInfo(LINUX, "lib-1.0"), LINUX, Library.SHARED, "lib", "so*");
    Assert.assertEquals("2", d.getProperty(NarInfo.DESCRIPTOR_VERSION_KEY));
    Assert.assertEquals("lib-1.0", d.getProperty("output"));
    // the output, so that a libs.names in the main nar.properties doesn't apply
    Assert.assertEquals("lib-1.0", d.getProperty("libs.names"));
    Assert.assertEquals("", d.getProperty("syslibs.names"));
    Assert.assertEquals("", d.getProperty("linker.options"));
  }

  public final void testRenamedLibraryIsFound() throws Exception {
    touch("leptonica-1.87.0.lib", "leptonica-1.87.0.dll");
    final Properties d = describe(narInfo(WINDOWS, "leptonica-1.87.0-p1"), WINDOWS, Library.SHARED, "", "lib");
    Assert.assertEquals("leptonica-1.87.0", d.getProperty("libs.names"));
  }

  public final void testConfiguredLibsNamesWins() throws Exception {
    touch("librenamed.so");
    final NarInfo narInfo = narInfo(LINUX, "lib-1.0");
    narInfo.setLibs(LINUX, "b a");
    Assert.assertEquals("b a", describe(narInfo, LINUX, Library.SHARED, "lib", "so*").getProperty("libs.names"));
    final NarInfo generic = narInfo(LINUX, "lib-1.0");
    generic.getInfo().setProperty("libs.names", "c");
    Assert.assertEquals("c", describe(generic, LINUX, Library.STATIC, "lib", "a").getProperty("libs.names"));
  }

  public final void testLibsNameResolvedPerAol() throws Exception {
    // one libsName for every AOL: libwebp's NAR also has libraries
    // consumers shouldn't link
    touch("libwebp.lib", "libsharpyuv.lib", "libwebpdecoder.lib");
    final NarInfo windows = narInfo(WINDOWS, "libwebp-1.6.0");
    windows.setLibs(WINDOWS, "webp sharpyuv");
    Assert.assertEquals("libwebp libsharpyuv",
        describe(windows, WINDOWS, Library.SHARED, "", "dll").getProperty("libs.names"));
    final Path linuxDir = Files.createDirectories(this.dir.resolve("linux"));
    for (final String name : new String[] { "libwebp.so", "libsharpyuv.so", "libwebpdecoder.so" }) {
      Files.createFile(linuxDir.resolve(name));
    }
    final NarInfo linux = narInfo(LINUX, "libwebp-1.6.0");
    linux.setLibs(LINUX, "libwebp sharpyuv");
    Assert.assertEquals("webp sharpyuv", NarDescriptor.describe(linux, LINUX, Library.SHARED, linuxDir,
        NarDescriptor.linkPatterns(LINUX.toString(), Library.SHARED, "lib", "so*"), new SystemStreamLog())
        .getProperty("libs.names"));
  }

  public final void testPrefixedLibsNameOnMsvc() throws Exception {
    // MSVC has no lib prefix: libfoo given for a GNU linker is foo.lib here;
    // a name given both ways is linked once
    touch("foo.lib", "bar.lib");
    final NarInfo narInfo = narInfo(WINDOWS, "foo-1.0");
    narInfo.setLibs(WINDOWS, "libfoo foo bar");
    Assert.assertEquals("foo bar", describe(narInfo, WINDOWS, Library.SHARED, "", "dll").getProperty("libs.names"));
  }

  public final void testStaleLibsNamesWarned() throws Exception {
    // zlib's preset zlib, where the build makes libprivz
    touch("libprivz.so");
    final NarInfo narInfo = narInfo(LINUX, "zlib-1.3.2");
    narInfo.setLibs(LINUX, "zlib");
    final List<String> warnings = new ArrayList<>();
    final Properties descriptor = NarDescriptor.describe(narInfo, LINUX, Library.SHARED, this.dir,
        NarDescriptor.linkPatterns(LINUX.toString(), Library.SHARED, "lib", "so*"), new SystemStreamLog() {
          @Override
          public void warn(final CharSequence content) {
            warnings.add(content.toString());
          }
        });
    Assert.assertEquals("zlib", descriptor.getProperty("libs.names"));
    Assert.assertEquals(1, warnings.size());
    Assert.assertTrue(warnings.get(0),
        warnings.get(0).contains("zlib isn't among") && warnings.get(0).contains("privz"));
  }

  public final void testMissingPrefixDefaultsToLib() throws Exception {
    // aol.properties has no lib.prefix for the gcc linkers
    touch("liblib-1.0.so");
    Assert.assertEquals("lib-1.0", describe(narInfo(LINUX_GCC, "lib-1.0"), LINUX_GCC, Library.SHARED, null, "so*")
        .getProperty("libs.names"));
    touch("librenamed.so");
    Files.delete(this.dir.resolve("liblib-1.0.so"));
    Assert.assertEquals("renamed", describe(narInfo(LINUX_GCC, "lib-1.0"), LINUX_GCC, Library.SHARED, null, "so*")
        .getProperty("libs.names"));
  }

  public final void testOnlyLibrariesGetLibsNames() throws Exception {
    touch("librenamed.so");
    for (final String type : new String[] { Library.JNI, Library.EXECUTABLE, Library.PLUGIN }) {
      Assert.assertNull(type, describe(narInfo(LINUX, "lib-1.0"), LINUX, type, "lib", "so*").getProperty("libs.names"));
    }
  }

  public final void testSysLibsAndOptions() throws Exception {
    final NarInfo narInfo = narInfo(LINUX, "lib-1.0");
    narInfo.setSysLibs(LINUX, "pthread:shared");
    narInfo.getInfo().setProperty("linker.options", "-Wl,--as-needed");
    final Properties d = describe(narInfo, LINUX, Library.SHARED, "lib", "so*");
    Assert.assertEquals("pthread:shared", d.getProperty("syslibs.names"));
    Assert.assertEquals("-Wl,--as-needed", d.getProperty("linker.options"));
  }

  public final void testWindowsGccStatic() throws Exception {
    // aol.properties has no lib.prefix for Windows gcc, which names static libraries libfoo.a
    touch("librenamed.a");
    final AOL windowsGcc = new AOL("amd64-Windows-gcc");
    Assert.assertEquals("renamed", describe(narInfo(windowsGcc, "lib-1.0"), windowsGcc, Library.STATIC, null, "a")
        .getProperty("libs.names"));
  }

  public final void testUnicodeRoundTrip() throws Exception {
    final Properties p = new Properties();
    p.setProperty("linker.options", "-L/opt/\u5e93");
    p.setProperty("output", "caf\u00e9");
    final Path file = this.dir.resolve("u/d.properties");
    NarUtil.storeProperties(file, p);
    final Properties read = new Properties();
    try (var in = Files.newInputStream(file)) {
      read.load(in);
    }
    Assert.assertEquals(p, read);
  }

  public final void testDescriptorOutputBeatsInheritedLibsNames() throws Exception {
    // the main nar.properties, from another AOL's build, names other libraries
    touch("liblib-1.0.so");
    final Properties d = describe(narInfo(LINUX, "lib-1.0"), LINUX, Library.SHARED, "lib", "so*");
    final NarInfo consumer = narInfo(LINUX, "lib-1.0");
    consumer.getInfo().setProperty("libs.names", "other");
    final Path file = this.dir.resolve("m/amd64-Linux-gpp-shared.properties");
    NarUtil.storeProperties(file, d);
    Assert.assertTrue(consumer.overlayDescriptor(file, LINUX));
    Assert.assertEquals("lib-1.0", consumer.getLibs(LINUX));
  }

  private static final AOL MINGW = new AOL("amd64-Windows-gpp");

  public final void testMingwSharedOutputDllWithBundledArchive() throws Exception {
    // the output DLL is directly linkable; a bundled static archive isn't linked
    touch("lib-1.0.dll", "libbundled.a");
    Assert.assertEquals("lib-1.0",
        describe(narInfo(MINGW, "lib-1.0"), MINGW, Library.SHARED, null, "dll").getProperty("libs.names"));
  }

  public final void testMingwImportLibraries() throws Exception {
    touch("liblib-1.0.dll.a", "lib-1.0.dll", "libbundled.dll.a", "bundled.dll");
    Assert.assertEquals("lib-1.0",
        describe(narInfo(MINGW, "lib-1.0"), MINGW, Library.SHARED, null, "dll").getProperty("libs.names"));
  }

  public final void testMingwRenamedImportLibrary() throws Exception {
    touch("librenamed.dll.a", "renamed.dll");
    Assert.assertEquals("renamed",
        describe(narInfo(MINGW, "lib-1.0"), MINGW, Library.SHARED, null, "dll").getProperty("libs.names"));
  }

  public final void testMsvcIgnoresCase() throws Exception {
    touch("FOO.LIB", "bundled.lib");
    Assert.assertEquals("foo", describe(narInfo(WINDOWS, "foo"), WINDOWS, Library.SHARED, "", "dll")
        .getProperty("libs.names"));
    Files.delete(this.dir.resolve("FOO.LIB"));
    touch("FOO.lib");
    Assert.assertEquals("foo", describe(narInfo(WINDOWS, "foo"), WINDOWS, Library.SHARED, "", "dll")
        .getProperty("libs.names"));
  }

  public final void testWriteBareFileName() throws Exception {
    final Path file = Path.of("descriptor-test-" + System.nanoTime() + ".properties");
    try {
      final Properties p = new Properties();
      p.setProperty("output", "foo");
      NarUtil.storeProperties(file, p);
      Assert.assertTrue(Files.exists(file));
    } finally {
      Files.deleteIfExists(file);
    }
  }

  public final void testMingwPrefixedRenamedDllLinkedOnce() throws Exception {
    touch("librenamed.dll");
    Assert.assertEquals("renamed",
        describe(narInfo(MINGW, "lib-1.0"), MINGW, Library.SHARED, null, "dll").getProperty("libs.names"));
  }

  public final void testSeveralLibrariesWithoutTheOutput() throws Exception {
    // a bundle of prebuilt libraries: consumers keep linking what they did
    touch("opencv_world455.lib", "opencv_world455d.lib");
    Assert.assertNull(describe(narInfo(WINDOWS, "bundle-1.0"), WINDOWS, Library.SHARED, "", "dll")
        .getProperty("libs.names"));
    // an import library and a DLL with different names
    final Path mingwDir = Files.createDirectories(this.dir.resolve("mingw"));
    Files.createFile(mingwDir.resolve("libpng16.dll.a"));
    Files.createFile(mingwDir.resolve("libpng16-16.dll"));
    final String names = NarDescriptor.linkNames(mingwDir,
        NarDescriptor.linkPatterns("amd64-Windows-gpp", Library.SHARED, null, "dll"));
    Assert.assertTrue(names, names.contains("png16 ") && names.endsWith("png16-16"));
    final Properties mingw = NarDescriptor.describe(narInfo(MINGW, "png-1.0"), MINGW, Library.SHARED, mingwDir,
        NarDescriptor.linkPatterns("amd64-Windows-gpp", Library.SHARED, null, "dll"), new SystemStreamLog());
    Assert.assertNull(mingw.getProperty("libs.names"));
  }

  public final void testVersionedOnlyLibraryNotNamed() throws Exception {
    // librenamed.so.1 is linkable only by its SONAME's link, made at unpack
    touch("librenamed.so.1");
    Assert.assertNull(describe(narInfo(LINUX, "foo"), LINUX, Library.SHARED, "lib", "so*").getProperty("libs.names"));
    touch("librenamed.so");
    Assert.assertEquals("renamed",
        describe(narInfo(LINUX, "foo"), LINUX, Library.SHARED, "lib", "so*").getProperty("libs.names"));
  }

  public final void testMacDylib() throws Exception {
    final AOL mac = new AOL("aarch64-MacOSX-gpp");
    touch("libfoo-1.0.dylib", "libfoo-1.0.1.dylib");
    Assert.assertEquals("foo-1.0",
        describe(narInfo(mac, "foo-1.0"), mac, Library.SHARED, "lib", "dylib").getProperty("libs.names"));
  }

  public final void testVersionedLibraryMakesItAmbiguous() throws Exception {
    // librenamed.so.1 may carry the output's SONAME, made linkable at unpack
    touch("librenamed.so.1", "libbundled.so");
    Assert.assertNull(describe(narInfo(LINUX, "foo"), LINUX, Library.SHARED, "lib", "so*").getProperty("libs.names"));
  }

  public final void testMingwUnprefixedImportLibrary() throws Exception {
    // MinGW finds -lfoo through foo.dll.a too
    touch("foo.dll.a", "bundled.dll");
    Assert.assertEquals("foo",
        describe(narInfo(MINGW, "foo"), MINGW, Library.SHARED, null, "dll").getProperty("libs.names"));
  }

  public final void testVersionedLibraryAmbiguousWithPlainSo() throws Exception {
    // some AOLs give the shared extension as so, not so*
    touch("librenamed.so.1", "libbundled.so");
    Assert.assertNull(describe(narInfo(LINUX, "foo"), LINUX, Library.SHARED, "lib", "so").getProperty("libs.names"));
  }

  public final void testLinkPatternOf() {
    // as aol.properties gives extensions: so* also takes version suffixes
    Assert.assertEquals(new LinkPattern("lib", "so", true, false), LinkPattern.of("lib", "so*"));
    Assert.assertEquals(new LinkPattern("lib", "so", false, false), LinkPattern.of("lib", "so"));
    Assert.assertEquals(new LinkPattern("", "a", false, false), LinkPattern.of(null, "a"));
    Assert.assertEquals(new LinkPattern("lib", null, false, false), LinkPattern.of("lib", null));
  }
}
