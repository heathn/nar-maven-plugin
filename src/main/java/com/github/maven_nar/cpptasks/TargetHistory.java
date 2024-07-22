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

import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A description of a file built or to be built
 */
public final class TargetHistory {
  private final String config;
  private final Path output;
  private final FileTime outputLastModified;
  private final List<SourceHistory> sources;

  /**
   * Constructor from build step
   */
  public TargetHistory(final String config, final Path output, final FileTime outputLastModified,
      final List<SourceHistory> sources) {
    if (config == null) {
      throw new NullPointerException("config");
    }
    if (sources == null) {
      throw new NullPointerException("source");
    }
    if (output == null) {
      throw new NullPointerException("output");
    }
    this.config = config;
    this.output = output;
    this.outputLastModified = outputLastModified;
    this.sources = new ArrayList<>(sources);
  }

  public Path getOutput() {
    return this.output;
  }

  public FileTime getOutputLastModified() {
    return this.outputLastModified;
  }

  public String getProcessorConfiguration() {
    return this.config;
  }

  public List<SourceHistory> getSources() {
    return Collections.unmodifiableList(this.sources);
  }
}
