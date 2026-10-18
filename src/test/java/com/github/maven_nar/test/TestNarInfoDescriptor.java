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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import junit.framework.TestCase;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.Assert;

import com.github.maven_nar.AOL;
import com.github.maven_nar.NarInfo;

/**
 * Tests reading the descriptor of an AOL-specific NAR.
 */
public class TestNarInfoDescriptor extends TestCase {
  private static final AOL LINUX = new AOL("amd64-Linux-gpp");

  private Path dir;

  @Override
  protected void setUp() throws Exception {
    this.dir = Files.createTempDirectory("narinfo");
  }

  @Override
  protected void tearDown() throws Exception {
    try (var paths = Files.walk(this.dir)) {
      paths.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
    }
  }

  private NarInfo mainInfo() throws MojoExecutionException {
    final NarInfo narInfo = new NarInfo("com.example", "lib", "1.0", new SystemStreamLog());
    narInfo.setLibs(LINUX, "lib-1.0");
    narInfo.setBinding(LINUX, "shared");
    return narInfo;
  }

  private Path descriptor(final String content) throws IOException {
    return Files.writeString(this.dir.resolve(NarInfo.getDescriptorFileName(LINUX.toString(), "shared")), content);
  }

  public final void testDescriptorFileName() {
    Assert.assertEquals("amd64-Linux-gpp-shared.properties", NarInfo.getDescriptorFileName("amd64-Linux-gpp", "shared"));
  }

  public final void testMissingDescriptor() throws Exception {
    final NarInfo narInfo = mainInfo();
    Assert.assertFalse(narInfo.overlayDescriptor(this.dir.resolve("none.properties"), LINUX));
    Assert.assertFalse(narInfo.overlayDescriptor(null, LINUX));
    Assert.assertEquals("lib-1.0", narInfo.getLibs(LINUX));
    Assert.assertFalse(narInfo.hasDescriptorKey("libs.names"));
  }

  public final void testDescriptorWins() throws Exception {
    final NarInfo narInfo = mainInfo();
    Assert.assertTrue(narInfo.overlayDescriptor(descriptor(
        "nar.descriptor.version=2\nlibs.names=lib-1.0-p1\n"), LINUX));
    Assert.assertEquals("lib-1.0-p1", narInfo.getLibs(LINUX));
    // keys the descriptor doesn't have are kept
    Assert.assertEquals("shared", narInfo.getBinding(LINUX, null));
    // the version isn't a NAR property
    Assert.assertNull(narInfo.getInfo().getProperty(NarInfo.DESCRIPTOR_VERSION_KEY));
    Assert.assertTrue(narInfo.hasDescriptorKey("libs.names"));
    Assert.assertFalse(narInfo.hasDescriptorKey("linker.options"));
  }

  public final void testDescriptorWinsOverGenericAndPrefixedKeys() throws Exception {
    final NarInfo narInfo = mainInfo();
    narInfo.getInfo().setProperty("libs.names", "generic");
    narInfo.getInfo().setProperty("amd64-Linux-gpp.output", "main-output");
    Assert.assertTrue(narInfo.overlayDescriptor(descriptor(
        "nar.descriptor.version=2\nlibs.names=lib-1.0-p1\noutput=nar-output\n"), LINUX));
    Assert.assertEquals("lib-1.0-p1", narInfo.getLibs(LINUX));
    Assert.assertEquals("nar-output", narInfo.getOutput(LINUX, null));
  }

  public final void testMappedAol() throws Exception {
    // the consumer looks properties up with its own AOL, which the
    // dependency's nar.properties may map to the AOL its NAR was built for
    final AOL consumer = new AOL("x86_64-Linux-gpp");
    final NarInfo narInfo = mainInfo();
    Assert.assertTrue(narInfo.overlayDescriptor(descriptor("nar.descriptor.version=2\nlibs.names=lib-1.0-p1\n"),
        consumer));
    Assert.assertEquals("lib-1.0-p1", narInfo.getLibs(consumer));
  }

  public final void testUnversionedFileIgnored() throws Exception {
    final NarInfo narInfo = mainInfo();
    Assert.assertFalse(narInfo.overlayDescriptor(descriptor("libs.names=other\n"), LINUX));
    Assert.assertEquals("lib-1.0", narInfo.getLibs(LINUX));
  }

  public final void testBadVersionIgnored() throws Exception {
    final NarInfo narInfo = mainInfo();
    Assert.assertFalse(narInfo.overlayDescriptor(descriptor(
        "nar.descriptor.version=two\nlibs.names=other\n"), LINUX));
    Assert.assertEquals("lib-1.0", narInfo.getLibs(LINUX));
  }

  public final void testNewerVersionUsed() throws Exception {
    final NarInfo narInfo = mainInfo();
    Assert.assertTrue(narInfo.overlayDescriptor(descriptor(
        "nar.descriptor.version=3\nlibs.names=lib-1.0-p2\n"), LINUX));
    Assert.assertEquals("lib-1.0-p2", narInfo.getLibs(LINUX));
  }
}
