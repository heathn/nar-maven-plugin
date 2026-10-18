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
import java.util.Comparator;
import java.util.stream.Stream;

import junit.framework.TestCase;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.project.MavenProject;
import org.junit.Assert;

import com.github.maven_nar.AOL;
import com.github.maven_nar.AbstractNarLayout;
import com.github.maven_nar.Library;
import com.github.maven_nar.NarCompileMojo;
import com.github.maven_nar.NarFileLayout;
import com.github.maven_nar.NarFileLayout10;
import com.github.maven_nar.NarInfo;
import com.github.maven_nar.NarLayout;
import com.github.maven_nar.NarLayout20;

/**
 * @author Mark Donszelmann (Mark.Donszelmann@gmail.com)
 */
public class TestNarLayout20 extends TestCase {
  private NarFileLayout fileLayout;

  private Log log;

  private NarLayout layout;

  private Path baseDir;

  private String aol;

  private String type;

  private Path narDir;

  /*
   * (non-Javadoc)
   * 
   * @see junit.framework.TestCase#setUp()
   */
  @Override
  protected void setUp() throws Exception {
    this.log = new SystemStreamLog();
    this.fileLayout = new NarFileLayout10();
    this.layout = new NarLayout20(this.log);
    this.baseDir = Path.of("/Users/maven");
    this.aol = "x86_64-MacOSX-g++";
    this.type = Library.SHARED;
  }

  @Override
  protected void tearDown() throws Exception {
    if (this.narDir != null) {
      try (Stream<Path> paths = Files.walk(this.narDir)) {
        paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
      }
    }
  }

  /**
   * Test method for
   * {@link com.github.maven_nar.NarLayout20#getBinDirectory(java.io.File, java.lang.String)}
   * .
   * 
   * @throws MojoFailureException
   * @throws MojoExecutionException
   */
  public final void testGetBinDirectory() throws MojoExecutionException, MojoFailureException {
    Assert.assertEquals(this.baseDir.resolve(this.fileLayout.getBinDirectory(this.aol)),
        this.layout.getBinDirectory(this.baseDir, null, null, this.aol));
  }

  /**
   * Test method for
   * {@link com.github.maven_nar.NarLayout20#getIncludeDirectory(java.io.File)}.
   * 
   * @throws MojoFailureException
   * @throws MojoExecutionException
   */
  public final void testGetIncludeDirectory() throws MojoExecutionException, MojoFailureException {
    Assert.assertEquals(this.baseDir.resolve(this.fileLayout.getIncludeDirectory()),
        this.layout.getIncludeDirectory(this.baseDir, null, null));
  }

  public final void testGetLayout() throws MojoExecutionException {
    AbstractNarLayout.getLayout("NarLayout20", this.log);
  }

  /**
   * Test method for
   * {@link com.github.maven_nar.NarLayout20#getLibDirectory(java.io.File, java.lang.String, java.lang.String)}
   * .
   * 
   * @throws MojoFailureException
   * @throws MojoExecutionException
   */
  public final void testGetLibDirectory() throws MojoExecutionException, MojoFailureException {
    Assert.assertEquals(this.baseDir.resolve(this.fileLayout.getLibDirectory(this.aol, this.type)),
        this.layout.getLibDirectory(this.baseDir, null, null, this.aol, this.type));
  }

  private static final AOL LINUX = new AOL("amd64-Linux-gpp");

  private static final AOL WINDOWS = new AOL("amd64-Windows-msvc");

  /**
   * Creates the given directories under a new NAR directory, runs
   * {@link NarLayout20#prepareNarInfo} on it and returns the result.
   */
  private NarInfo prepareNarInfo(final NarInfo narInfo, final String... dirs)
      throws IOException, MojoExecutionException {
    this.narDir = Files.createTempDirectory("nar");
    for (final String dir : dirs) {
      Files.createDirectories(this.narDir.resolve(dir));
    }
    final MavenProject project = new MavenProject();
    project.setGroupId("com.example");
    project.setArtifactId("lib");
    project.setVersion("1.0");
    ((NarLayout20) this.layout).prepareNarInfo(this.narDir, project, narInfo, new NarCompileMojo());
    return narInfo;
  }

  private NarInfo newNarInfo() throws MojoExecutionException {
    return new NarInfo("com.example", "lib", "1.0", this.log);
  }

  private static String binding(final NarInfo narInfo, final AOL aol) {
    return narInfo.getExactProperty(aol, "libs.binding", null);
  }

  public final void testEmptyLibDirectoryAlongsideSharedLibrary() throws Exception {
    final NarInfo narInfo = prepareNarInfo(newNarInfo(), "lib/" + LINUX, "lib/" + WINDOWS + "/" + Library.SHARED);
    Assert.assertEquals(Library.NONE, binding(narInfo, LINUX));
    Assert.assertEquals(Library.SHARED, binding(narInfo, WINDOWS));
  }

  public final void testExecutableAlongsideOtherAolLibrary() throws Exception {
    final NarInfo narInfo = prepareNarInfo(newNarInfo(), "bin/" + WINDOWS, "lib/" + LINUX + "/" + Library.SHARED);
    Assert.assertEquals(Library.EXECUTABLE, binding(narInfo, WINDOWS));
    Assert.assertEquals(Library.SHARED, binding(narInfo, LINUX));
  }

  public final void testConfiguredDefaultBindingIsRespected() throws Exception {
    final NarInfo narInfo = newNarInfo();
    narInfo.setBinding(null, Library.STATIC);
    prepareNarInfo(narInfo, "lib/" + LINUX + "/" + Library.SHARED, "lib/" + WINDOWS + "/" + Library.SHARED);
    Assert.assertNull(binding(narInfo, LINUX));
    Assert.assertNull(binding(narInfo, WINDOWS));
    Assert.assertEquals(Library.STATIC, narInfo.getBinding(LINUX, null));
    Assert.assertEquals(Library.STATIC, narInfo.getBinding(WINDOWS, null));
  }

  public final void testConfiguredAolBindingIsRespected() throws Exception {
    final NarInfo narInfo = newNarInfo();
    narInfo.setBinding(LINUX, Library.STATIC);
    prepareNarInfo(narInfo, "lib/" + LINUX + "/" + Library.SHARED, "lib/" + WINDOWS + "/" + Library.SHARED);
    Assert.assertEquals(Library.STATIC, binding(narInfo, LINUX));
    Assert.assertEquals(Library.SHARED, binding(narInfo, WINDOWS));
  }
}
