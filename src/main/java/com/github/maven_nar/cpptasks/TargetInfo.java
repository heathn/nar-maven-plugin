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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.maven.surefire.shared.lang3.function.Failable;

import com.github.maven_nar.cpptasks.compiler.ProcessorConfiguration;

/**
 * A description of a file built or to be built
 */
public final class TargetInfo {
  private final ProcessorConfiguration config;
  private final Path output;
  private boolean rebuild;
  private final List<Path> sources;
  private List<Path> sysSources;

  public TargetInfo(final ProcessorConfiguration config, final List<Path> sources, final List<Path> sysSources,
      final Path output, boolean rebuild) {
    if (config == null) {
      throw new NullPointerException("config");
    }
    if (sources == null) {
      throw new NullPointerException("sources");
    }
    if (output == null) {
      throw new NullPointerException("output");
    }
    this.config = config;
    this.sources = new ArrayList<>(sources);
    if (sysSources == null) {
      this.sysSources = Collections.emptyList();
    } else {
      this.sysSources = new ArrayList<>(sysSources);
    }
    this.output = output;
    this.rebuild = rebuild;
    //
    // if the output doesn't exist, must rebuild it
    //
    if (Files.notExists(output)) {
      rebuild = true;
    }
  }

  public List<Path> getAllSourcePaths() {
    List<Path> paths = new ArrayList<>();
    paths.addAll(this.sysSources);
    paths.addAll(this.sources);
    return paths;
  }

  public List<Path> getAllSources() {
    List<Path> allSources = new ArrayList<>();
    allSources.addAll(this.sysSources);
    allSources.addAll(this.sources);
    return allSources;
  }

  public ProcessorConfiguration getConfiguration() {
    return this.config;
  }

  public Path getOutput() {
    return this.output;
  }

  public boolean getRebuild() {
    return this.rebuild;
  }

  /**
   * Returns an array of SourceHistory objects (contains relative path and
   * last modified time) for the source[s] of this target
   */
  public List<SourceHistory> getSourceHistories(final Path basePath) {
    return this.sources.stream()
        .map(Failable.asFunction(source -> {
          Path relativePath = basePath.relativize(source);
          FileTime lastModified = Files.getLastModifiedTime(source);
          return new SourceHistory(relativePath, lastModified);
        }))
        .collect(Collectors.toList());
  }

  public List<String> getSourcePaths() {
    return this.sources.stream()
        .map(Path::toString)
        .collect(Collectors.toList());
  }

  public List<Path> getSources() {
    return Collections.unmodifiableList(this.sources);
  }

  public List<String> getSysSourcePaths() {
    return this.sysSources.stream()
        .map(Path::toString)
        .collect(Collectors.toList());
  }

  public List<Path> getSysSources() {
    return Collections.unmodifiableList(this.sysSources);
  }

  public void mustRebuild() {
    this.rebuild = true;
  }
}
