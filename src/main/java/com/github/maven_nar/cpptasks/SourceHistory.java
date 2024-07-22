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

/**
 * The history of a source file used to build a target
 *
 * @author Curt Arnold
 */
public final class SourceHistory {
  private final FileTime lastModified;
  private final Path relativePath;

  /**
   * Constructor
   */
  public SourceHistory(final Path relativePath, final FileTime lastModified) {
    if (relativePath == null) {
      throw new NullPointerException("relativePath");
    }
    this.relativePath = relativePath;
    this.lastModified = lastModified;
  }

  public Path getAbsolutePath(final Path baseDir) {
    return baseDir.resolve(this.relativePath).toAbsolutePath();
  }

  public FileTime getLastModified() {
    return this.lastModified;
  }

  public Path getRelativePath() {
    return this.relativePath;
  }
}
