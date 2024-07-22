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

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.stream.Collectors;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.maven.surefire.shared.lang3.function.Failable;
import org.apache.tools.ant.BuildException;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.github.maven_nar.cpptasks.compiler.ProcessorConfiguration;

/**
 * A history of the compiler and linker settings used to build the files in the
 * same directory as the history.
 *
 * @author Curt Arnold
 */
public final class TargetHistoryTable {
  /**
   * This class handles populates the TargetHistory hashtable in response to
   * SAX parse events
   */
  private class TargetHistoryTableHandler extends DefaultHandler {
    private final Path baseDir;
    private String config;
    private final Hashtable<Path, TargetHistory> history;
    private Path output;
    private FileTime outputLastModified;
    private final Vector<SourceHistory> sources = new Vector<>();

    /**
     * Constructor
     *
     * @param history
     *          hashtable of TargetHistory keyed by output name
     */
    private TargetHistoryTableHandler(final Hashtable<Path, TargetHistory> history, final Path baseDir) {
      this.history = history;
      this.config = null;
      this.output = null;
      this.baseDir = baseDir;
    }

    @Override
    public void endElement(final String namespaceURI, final String localName,
        final String qName) throws SAXException {

      // if </target> then create TargetHistory object and add to hashtable
      // if corresponding output file exists and has the same timestamp
      if (qName.equals("target")) {
        if (this.config != null && this.output != null) {
          final Path existingFile = this.baseDir.resolve(this.output);
  
          // if the corresponding files doesn't exist or has a different
          // modification time, then discard this record
          if (Files.exists(existingFile)) {

            // would have expected exact time stamps but have observed slight
            // differences in return value for multiple evaluations of
            // lastModified(). Check if times are within a second.
            FileTime existingLastModified;
            try {
              existingLastModified = Files.getLastModifiedTime(existingFile);
            } catch (IOException e) {
              existingLastModified = this.outputLastModified;
            }
            if (!CUtil.isSignificantlyBefore(existingLastModified, this.outputLastModified)
                && !CUtil.isSignificantlyAfter(existingLastModified, this.outputLastModified)) {
              final TargetHistory targetHistory = new TargetHistory(this.config, this.output,
                  this.outputLastModified, this.sources);
              this.history.put(this.output, targetHistory);
            }
          }
        }
        this.output = null;
        this.sources.setSize(0);
      } else {

        // reset config so targets not within a processor element don't pick
        // up a previous processors signature
        if (qName.equals("processor")) {
          this.config = null;
        }
      }
    }

    /**
     * startElement handler
     */
    @Override
    public void startElement(final String namespaceURI, final String localName, final String qName,
        final Attributes atts) throws SAXException {
      //
      // if sourceElement
      //
      if (qName.equals("source")) {
        final Path sourceFile = Path.of(atts.getValue("file"));
        final FileTime sourceLastModified = FileTime.from(Instant.parse(atts.getValue("lastModified")));
        this.sources.addElement(new SourceHistory(sourceFile, sourceLastModified));
      } else {
        //
        // if <target> element,
        // grab file name and lastModified values
        // TargetHistory object will be created in endElement
        //
        if (qName.equals("target")) {
          this.sources.setSize(0);
          this.output = Path.of(atts.getValue("file"));
          this.outputLastModified = FileTime.from(Instant.parse(atts.getValue("lastModified")));
        } else {
          //
          // if <processor> element,
          // grab signature attribute
          //
          if (qName.equals("processor")) {
            this.config = atts.getValue("signature");
          }
        }
      }
    }
  }

  /**
   * Flag indicating whether the cache should be written back to file.
   */
  private boolean dirty;
  /**
   * a hashtable of TargetHistory's keyed by output file name
   */
  private final Hashtable<Path, TargetHistory> history = new Hashtable<>();
  /**
   * The file the cache was loaded from.
   */
  private final Path historyFile;
  private final Path outputDir;

  /**
   * Creates a target history table from history.xml in the output directory,
   * if it exists. Otherwise, initializes the history table empty.
   *
   * @param task
   *          task used for logging history load errors
   * @param outputDir
   *          output directory for task
   */
  public TargetHistoryTable(final CCTask task, final Path outputDir) throws BuildException {

    if (outputDir == null) {
      throw new NullPointerException("outputDir");
    }
    if (Files.notExists(outputDir)) {
      throw new BuildException("Output directory does not exist");
    }
    if (!Files.isDirectory(outputDir)) {
      throw new BuildException("Output directory is not a directory");
    }
    this.outputDir = outputDir;

    //
    // load any existing history from file
    // suppressing any records whose corresponding
    // file does not exist, is zero-length or
    // last modified dates differ
    this.historyFile = outputDir.resolve("history.xml");

    if (Files.exists(this.historyFile)) {
      final SAXParserFactory factory = SAXParserFactory.newInstance();
      factory.setValidating(false);
      try {
        final SAXParser parser = factory.newSAXParser();
        parser.parse(Files.newInputStream(this.historyFile), new TargetHistoryTableHandler(this.history, outputDir));
      } catch (final Exception ex) {
        //
        // a failure on loading this history is not critical
        // but should be logged
        task.log("Error reading history.xml: " + ex.toString());
      }
    } else {

      // create empty history file for identifying new files by last modified
      // timestamp comperation (to compare with System.currentTimeMillis()
      // don't work on Unix, because it measures timestamps only in seconds).
      try {
        final Path temp = Files.createTempFile(outputDir, "history.xml", Long.toString(System.nanoTime()));
        try (BufferedWriter writer = Files.newBufferedWriter(temp)) {
          writer.write("<history/>");
        }
        Files.move(temp, this.historyFile);
      } catch (final IOException ex) {
        throw new BuildException("Can't create history file", ex);
      }
    }
  }

  public void commit() throws IOException {
    //
    // if not dirty, no need to update file
    //
    if (this.dirty) {
      //
      // build (small) hashtable of config id's in history
      //
      final Set<String> configs = this.history.values().stream()
          .map(TargetHistory::getProcessorConfiguration)
          .collect(Collectors.toSet());
      final OutputStream outStream = Files.newOutputStream(historyFile);
      OutputStreamWriter outWriter;
      //
      // early VM's don't support UTF-8 encoding
      // try and fallback to the default encoding
      // otherwise
      String encodingName = "UTF-8";
      try {
        outWriter = new OutputStreamWriter(outStream, "UTF-8");
      } catch (final UnsupportedEncodingException ex) {
        outWriter = new OutputStreamWriter(outStream);
        encodingName = outWriter.getEncoding();
      }
      final BufferedWriter writer = new BufferedWriter(outWriter);
      writer.write("<?xml version='1.0' encoding='");
      writer.write(encodingName);
      writer.write("'?>\n");
      writer.write("<history>\n");
      final StringBuffer buf = new StringBuffer(200);
      for (String configId : configs) {
        buf.setLength(0);
        buf.append("   <processor signature=\"");
        buf.append(CUtil.xmlAttribEncode(configId));
        buf.append("\">\n");
        writer.write(buf.toString());
        for (TargetHistory targetHistory : this.history.values()) {
          if (targetHistory.getProcessorConfiguration().equals(configId)) {
            buf.setLength(0);
            buf.append("      <target file=\"");
            buf.append(CUtil.xmlAttribEncode(targetHistory.getOutput().toString()));
            buf.append("\" lastModified=\"");
            buf.append(targetHistory.getOutputLastModified());
            buf.append("\">\n");
            writer.write(buf.toString());
            for (final SourceHistory sourceHistory : targetHistory.getSources()) {
              buf.setLength(0);
              buf.append("         <source file=\"");
              buf.append(CUtil.xmlAttribEncode(sourceHistory.getRelativePath().toString()));
              buf.append("\" lastModified=\"");
              buf.append(sourceHistory.getLastModified());
              buf.append("\"/>\n");
              writer.write(buf.toString());
            }
            writer.write("      </target>\n");
          }
        }
        writer.write("   </processor>\n");
      }
      writer.write("</history>\n");
      writer.close();
      this.dirty = false;
    }
  }

  public TargetHistory get(final String configId, final Path outputName) {
    TargetHistory targetHistory = this.history.get(outputName);
    if (targetHistory != null && !targetHistory.getProcessorConfiguration().equals(configId)) {
        targetHistory = null;
    }
    return targetHistory;
  }

  public Path getHistoryFile() {
    return this.historyFile;
  }

  public void markForRebuild(final Map<Path, TargetInfo> targetInfos) {
    for (final TargetInfo targetInfo : targetInfos.values()) {
      markForRebuild(targetInfo);
    }
  }

  // FREEHEP added synchronized
  public synchronized void markForRebuild(final TargetInfo targetInfo) {
    //
    // if it must already be rebuilt, no need to check further
    //
    if (!targetInfo.getRebuild()) {
      final TargetHistory history = get(targetInfo.getConfiguration().toString(), targetInfo.getOutput().getFileName());
      if (history == null) {
        targetInfo.mustRebuild();
      } else {
        final List<SourceHistory> sourceHistories = history.getSources();
        final List<Path> sources = targetInfo.getSources();
        if (sourceHistories.size() != sources.size()) {
          targetInfo.mustRebuild();
        } else {
          final Hashtable<Path, Path> sourceMap = new Hashtable<>(sources.size());
          for (final Path source : sources) {
            sourceMap.put(source.toAbsolutePath(), source);
          }
          for (final SourceHistory sourceHistory : sourceHistories) {
            //
            // relative file name, must absolutize it on output
            // directory
            //
            final Path absPath = sourceHistory.getAbsolutePath(this.outputDir);
            Path match = sourceMap.get(absPath);
            try {
              if (match == null || Files.getLastModifiedTime(match).compareTo(sourceHistory.getLastModified()) != 0) {
                targetInfo.mustRebuild();
                break;
              }
            } catch (IOException e) {
              targetInfo.mustRebuild();
            }
          }
        }
      }
    }
  }

  public void update(final ProcessorConfiguration config, final Path[] sources, final VersionInfo versionInfo) {
    final String configId = config.getIdentifier();
    final Path[] onesource = new Path[1];
    for (final Path source : sources) {
      onesource[0] = source;
      Path[] outputNames = config.getOutputFileNames(source, versionInfo);
      for (final Path outputName : outputNames) {
        update(configId, outputName, onesource);
      }
    }
  }

  // FREEHEP added synchronized
  private synchronized void update(final String configId, final Path outputName, final Path[] sources) {
    final Path outputFile = this.outputDir.resolve(outputName);
    //
    // if output file doesn't exist or predates the start of the
    // compile step (most likely a compilation error) then
    // do not write add a history entry
    //
    try {
      if (Files.exists(outputFile) && !CUtil.isSignificantlyBefore(Files.getLastModifiedTime(outputFile), Files.getLastModifiedTime(this.historyFile))) {
        this.dirty = true;
        this.history.remove(outputName);
        final List<SourceHistory> sourceHistories = Arrays.stream(sources)
            .map(Failable.asFunction(source -> {
              Path relativePath = this.outputDir.resolve(source);
              FileTime lastModified = Files.getLastModifiedTime(relativePath);
              return new SourceHistory(relativePath, lastModified);
            }))
            .collect(Collectors.toList());
        TargetHistory newHistory = new TargetHistory(configId, outputName, Files.getLastModifiedTime(outputFile),
              sourceHistories);
        this.history.put(outputName, newHistory);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  // FREEHEP added synchronized
  public synchronized void update(final TargetInfo linkTarget) {
    final Path outputFile = linkTarget.getOutput();
    final Path outputName = outputFile.getFileName();
    //
    // if output file doesn't exist or predates the start of the
    // compile or link step (most likely a compilation error) then
    // do not write add a history entry
    //
    try {
      if (Files.exists(outputName) && !CUtil.isSignificantlyBefore(Files.getLastModifiedTime(outputFile), Files.getLastModifiedTime(this.historyFile))) {
        this.dirty = true;
        this.history.remove(outputName);
        final List<SourceHistory> sourceHistories = linkTarget.getSourceHistories(this.outputDir.toAbsolutePath());
        final TargetHistory newHistory = new TargetHistory(linkTarget.getConfiguration().getIdentifier(), outputName,
            Files.getLastModifiedTime(outputFile), sourceHistories);
        this.history.put(outputName, newHistory);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
