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

import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import junit.framework.TestCase;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Assert;

import com.github.maven_nar.NarDeployMojo;
import com.github.maven_nar.NarUtil;

/**
 * Tests choosing the AOL-specific artifacts to deploy, and parsing
 * project.build.outputTimestamp.
 */
public class TestNarDeploy extends TestCase {

  private static Artifact artifact(final String type, final String classifier) {
    return new DefaultArtifact("com.example", "lib", "1.0", "compile", type, classifier,
        new DefaultArtifactHandler(type));
  }

  public final void testAolArtifacts() {
    final List<Artifact> attached = List.of(
        artifact("nar", "noarch"),
        artifact("nar", "amd64-Linux-gpp-shared"),
        artifact("nar", "amd64-Linux-gpp-jni"),
        artifact("jar", "sources"),
        // an assembly classified with the AOL
        artifact("jar", "amd64-Linux-gpp"),
        // another AOL's, not built here
        artifact("jar", "amd64-Windows-msvc"));
    Assert.assertEquals(List.of("amd64-Linux-gpp-shared", "amd64-Linux-gpp-jni", "amd64-Linux-gpp"),
        NarDeployMojo.aolArtifacts(attached).stream().map(Artifact::getClassifier).collect(Collectors.toList()));
  }

  public final void testNoAolArtifacts() {
    Assert.assertEquals(List.of(), NarDeployMojo.aolArtifacts(List.of(artifact("nar", "noarch"),
        artifact("jar", "sources"))));
  }

  public final void testOutputTimestamp() {
    Assert.assertNull(NarUtil.getOutputTimestamp(null));
    Assert.assertNull(NarUtil.getOutputTimestamp(" "));
    Assert.assertNull(NarUtil.getOutputTimestamp("a"));
    Assert.assertEquals(FileTime.from(Instant.ofEpochSecond(1700000000)), NarUtil.getOutputTimestamp("1700000000"));
    Assert.assertEquals(FileTime.from(Instant.parse("2026-10-18T12:00:00Z")),
        NarUtil.getOutputTimestamp("2026-10-18T12:00:00Z"));
    Assert.assertEquals(FileTime.from(Instant.parse("2026-10-18T06:00:00Z")),
        NarUtil.getOutputTimestamp("2026-10-18T00:00:00-06:00"));
    try {
      NarUtil.getOutputTimestamp("yesterday");
      fail("parsed an invalid timestamp");
    } catch (final IllegalArgumentException expected) {
      // expected
    }
  }

  public final void testParseRepository() throws Exception {
    Assert.assertEquals("https://repo.example/maven", NarDeployMojo.parseRepository("id::https://repo.example/maven").getUrl());
    Assert.assertEquals("id", NarDeployMojo.parseRepository("id::default::file:///tmp/repo").getId());
    // compressed IPv6 addresses contain the separator too
    Assert.assertEquals("http://[::1]:8081/repository",
        NarDeployMojo.parseRepository("local::http://[::1]:8081/repository").getUrl());
    Assert.assertEquals("http://[::1]:8081/repository",
        NarDeployMojo.parseRepository("local::default::http://[::1]:8081/repository").getUrl());
    // padded parts are trimmed, as maven-deploy-plugin does
    final org.eclipse.aether.repository.RemoteRepository padded = NarDeployMojo
        .parseRepository(" releases :: https://repo.example/maven ");
    Assert.assertEquals("releases", padded.getId());
    Assert.assertEquals("https://repo.example/maven", padded.getUrl());
    final org.eclipse.aether.repository.RemoteRepository legacy = NarDeployMojo
        .parseRepository("releases :: default :: http://[::1]:8081/repository ");
    Assert.assertEquals("releases", legacy.getId());
    Assert.assertEquals("http://[::1]:8081/repository", legacy.getUrl());
    for (final String bad : new String[] { "id", "id::", "id::legacy::file:///tmp", "::file:///tmp", "a::b::c::d",
        " ::file:///tmp", "id:: ", "id::default:: " }) {
      try {
        NarDeployMojo.parseRepository(bad);
        fail("accepted " + bad);
      } catch (final MojoExecutionException expected) {
        // expected
      }
    }
  }

  public final void testOutputTimestampRange() {
    for (final String bad : new String[] { "1979-12-31T00:00:00Z", "2100-01-01T00:00:00Z", "100" }) {
      try {
        NarUtil.getOutputTimestamp(bad);
        fail("accepted " + bad);
      } catch (final IllegalArgumentException expected) {
        // expected
      }
    }
  }

  public final void testSupportsConditionalSkip() {
    for (final String old : new String[] { "2.8.2", "3.0.0-M1" }) {
      Assert.assertFalse(old, NarDeployMojo.supportsConditionalSkip(old));
    }
    for (final String current : new String[] { "3.0.0-M2", "3.0.0", "3.1.2", "4.0.0-beta-3" }) {
      Assert.assertTrue(current, NarDeployMojo.supportsConditionalSkip(current));
    }
  }
}
