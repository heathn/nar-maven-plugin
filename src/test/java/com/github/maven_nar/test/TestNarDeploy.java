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
import java.util.stream.Collectors;

import junit.framework.TestCase;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Assert;

import com.github.maven_nar.NarDeployMojo;

/**
 * Tests choosing the deploy scope and the AOL-specific artifacts to deploy.
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

  public final void testDeployScopeFollowsPrimaryAol() throws Exception {
    Assert.assertEquals("all", NarDeployMojo.deployScope(null, "amd64-Linux-gpp", "amd64-Linux-gpp"));
    // spelled either way
    Assert.assertEquals("all", NarDeployMojo.deployScope(null, "amd64-Linux-g++", "amd64-Linux-gpp"));
    Assert.assertEquals("aol", NarDeployMojo.deployScope(null, "amd64-Linux-gpp", "amd64-Windows-msvc"));
  }

  public final void testConfiguredDeployScopeWins() throws Exception {
    Assert.assertEquals("aol", NarDeployMojo.deployScope("aol", "amd64-Linux-gpp", "amd64-Linux-gpp"));
    Assert.assertEquals("all", NarDeployMojo.deployScope("all", "amd64-Linux-gpp", "amd64-Windows-msvc"));
  }

  public final void testDeployScopeDefaultsToAll() throws Exception {
    Assert.assertEquals("all", NarDeployMojo.deployScope(null, null, "amd64-Linux-gpp"));
    Assert.assertEquals("all", NarDeployMojo.deployScope(null, " ", null));
  }

  public final void testPrimaryAolWithAVersionedLinker() throws Exception {
    Assert.assertEquals("all", NarDeployMojo.deployScope(null, "x86_64-Linux-gpp-12", "x86_64-Linux-gpp-12"));
  }

  public final void testMalformedPrimaryAol() {
    for (final String aol : new String[] { "amd64-Linux", "amd64", "amd64--gpp" }) {
      try {
        NarDeployMojo.deployScope(null, aol, "amd64-Linux-gpp");
        fail("accepted primaryAol " + aol);
      } catch (final MojoExecutionException expected) {
        assertTrue(expected.getMessage(), expected.getMessage().contains("arch-os-linker"));
      }
    }
  }

  public final void testBadDeployScopes() {
    try {
      NarDeployMojo.deployScope("some", null, "amd64-Linux-gpp");
      fail("accepted deployScope some");
    } catch (final MojoExecutionException expected) {
      assertTrue(expected.getMessage(), expected.getMessage().contains("all or aol"));
    }
    try {
      NarDeployMojo.deployScope(null, "amd64-Linux-gpp", null);
      fail("accepted primaryAol without the AOL being built");
    } catch (final MojoExecutionException expected) {
      assertTrue(expected.getMessage(), expected.getMessage().contains("nar.aol"));
    }
  }

  public final void testAlreadyDeployed() throws Exception {
    assertTrue(NarDeployMojo.alreadyDeployed(List.of("a:pom", "a:nar"), List.of(), "file:repo"));
    assertFalse(NarDeployMojo.alreadyDeployed(List.of(), List.of("a:pom", "a:nar"), "file:repo"));
    // nothing to check
    assertFalse(NarDeployMojo.alreadyDeployed(List.of(), List.of(), "file:repo"));
  }

  public final void testPartlyDeployed() {
    try {
      NarDeployMojo.alreadyDeployed(List.of("a:pom"), List.of("a:nar"), "file:repo");
      fail("accepted a partly deployed version");
    } catch (final MojoExecutionException expected) {
      assertTrue(expected.getMessage(), expected.getMessage().contains("[a:pom] but not [a:nar]"));
    }
  }
}
