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
package com.github.maven_nar.cpptasks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import com.github.maven_nar.cpptasks.compiler.ProcessorConfiguration;

/**
 * Tests for TargetHistoryTable
 *
 * @author CurtA
 */
public class TestTargetHistoryTable extends TestXMLConsumer {
  public static class MockProcessorConfiguration implements ProcessorConfiguration {
    public MockProcessorConfiguration() {
    }

    @Override
    public int bid(final Path fileName) {
      return 100;
    }

    @Override
    public String getIdentifier() {
      return "Mock Configuration";
    }

    @Override
    public Path[] getOutputFileNames(final Path baseName, final VersionInfo versionInfo) {
      return new Path[] {
        baseName
      };
    }

    @Override
    public List<ProcessorParam> getParams() {
      return Collections.emptyList();
    }

    @Override
    public boolean getRebuild() {
      return false;
    }
  }

  /**
   * Constructor
   * 
   * @param name
   *          test case name
   * @see junit.framework.TestCase#TestCase(String)
   */
  public TestTargetHistoryTable(final String name) {
    super(name);
  }

  /**
   * Tests loading a stock history file
   * 
   * @throws IOException
   */
  public void testLoadOpenshore() throws IOException {
    try {
      copyResourceToTmpDir("openshore/history.xml", "history.xml");
      final CCTask task = new CCTask();
      final String tmpDir = System.getProperty("java.io.tmpdir");
      final TargetHistoryTable history = new TargetHistoryTable(task, Path.of(tmpDir));
    } finally {
      deleteTmpFile("history.xml");
    }
  }

  /**
   * Tests loading a stock history file
   * 
   * @throws IOException
   */
  public void testLoadXerces() throws IOException {
    try {
      copyResourceToTmpDir("xerces-c/history.xml", "history.xml");
      final CCTask task = new CCTask();
      final String tmpDir = System.getProperty("java.io.tmpdir");
      final TargetHistoryTable history = new TargetHistoryTable(task, Path.of(tmpDir));
    } finally {
      deleteTmpFile("history.xml");
    }
  }

  /**
   * Tests for bug fixed by patch [ 650397 ] Fix: Needless rebuilds on Unix
   * 
   * @throws IOException
   */
  public void testUpdateTimeResolution() throws IOException {
    Path compiledFile = null;
    Path historyFile = null;
    try {
      //
      // delete any history file that might exist
      // in the test output directory
      final Path tempDir = Path.of(System.getProperty("java.io.tmpdir"));
      historyFile = tempDir.resolve("history.xml");
      Files.deleteIfExists(historyFile);
      final TargetHistoryTable table = new TargetHistoryTable(null, tempDir);
      //
      // create a dummy compiled unit
      //
      compiledFile = tempDir.resolve("dummy.o");
      Files.createFile(compiledFile);
  
      //
      // update the table
      //
      table.update(new MockProcessorConfiguration(), new Path[] {
        Path.of("dummy.o")
      }, null);
      //
      // commit. If "compiled" file was judged to be
      // valid we should have a history file.
      //
      table.commit();
      historyFile = table.getHistoryFile();
      assertTrue("History file was not created", Files.exists(historyFile));
      assertTrue("History file was empty", Files.size(historyFile) > 10);
    } finally {
      Files.deleteIfExists(compiledFile);
      Files.deleteIfExists(historyFile);
    }
  }
}
