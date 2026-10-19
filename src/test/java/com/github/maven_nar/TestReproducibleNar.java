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

import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import junit.framework.TestCase;

import org.apache.maven.model.Build;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.archiver.manager.ArchiverManager;

/**
 * A reproducible NAR's entries are in the same order however the files were
 * created.
 */
public class TestReproducibleNar extends TestCase {

  public final void testEntryOrder() throws Exception {
    final Path dir = Files.createTempDirectory("nar-order");
    final List<String> names = new ArrayList<>();
    for (int i = 0; i < 40; i++) {
      names.add(String.format("f%02d.h", i));
    }
    Collections.shuffle(names, new Random(1));
    final Path include = Files.createDirectories(dir.resolve("include"));
    for (final String name : names) {
      Files.write(include.resolve(name), name.getBytes("UTF-8"));
    }
    final MavenProject project = new MavenProject();
    final Build build = new Build();
    build.setDirectory(dir.resolve("target").toString());
    build.setFinalName("order");
    project.setBuild(build);
    final ArchiverManager archivers = (ArchiverManager) Proxy.newProxyInstance(getClass().getClassLoader(),
        new Class<?>[] { ArchiverManager.class }, (proxy, method, args) -> new NarArchiver());
    final MavenProjectHelper helper = (MavenProjectHelper) Proxy.newProxyInstance(getClass().getClassLoader(),
        new Class<?>[] { MavenProjectHelper.class }, (proxy, method, args) -> null);
    Files.createDirectories(dir.resolve("target"));
    final NarLayout21 layout = new NarLayout21(new SystemStreamLog());
    layout.setOutputTimestamp(FileTime.from(Instant.parse("2026-10-18T12:00:00Z")));
    layout.attachNar(archivers, helper, project, "noarch", dir, "include/*.h");

    final List<String> entries = new ArrayList<>();
    try (ZipFile zip = new ZipFile(new File(build.getDirectory(), "order-noarch.nar"))) {
      for (final ZipEntry entry : Collections.list(zip.entries())) {
        if (!entry.isDirectory()) {
          entries.add(entry.getName());
        }
      }
    }
    final List<String> sorted = new ArrayList<>(entries);
    Collections.sort(sorted);
    assertEquals(40, entries.size());
    assertEquals(sorted, entries);
  }
}
