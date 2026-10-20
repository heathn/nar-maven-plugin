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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import junit.framework.TestCase;

import org.apache.maven.project.MavenProject;

/**
 * The project's AOL-specific directories in target/nar are its directories
 * only.
 */
public class TestAolDirectories extends TestCase {
  private Path dir;

  @Override
  protected void setUp() throws Exception {
    this.dir = Files.createTempDirectory("nar-aol-dirs");
  }

  @Override
  protected void tearDown() throws Exception {
    try (Stream<Path> paths = Files.walk(this.dir)) {
      paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
    }
  }

  public final void testOnlyTheProjectsDirectories() throws Exception {
    Files.createDirectory(this.dir.resolve("lib-1.0-amd64-Linux-gpp-shared"));
    Files.createDirectory(this.dir.resolve("lib-1.0-amd64-Linux-gpp-jni"));
    Files.createDirectory(this.dir.resolve("lib-1.0-noarch"));
    // another version's, and a stale NAR
    Files.createDirectory(this.dir.resolve("lib-1.0.1-amd64-Linux-gpp-shared"));
    Files.createFile(this.dir.resolve("lib-1.0-amd64-Linux-gpp-static.nar"));
    final MavenProject project = new MavenProject();
    project.setArtifactId("lib");
    project.setVersion("1.0");
    assertEquals(List.of("amd64-Linux-gpp-jni", "amd64-Linux-gpp-shared"),
        List.copyOf(AbstractNarLayout.aolDirectories(this.dir, project).keySet()));
    assertEquals(this.dir.resolve("lib-1.0-amd64-Linux-gpp-shared"),
        AbstractNarLayout.aolDirectories(this.dir, project).get("amd64-Linux-gpp-shared"));
  }
}
