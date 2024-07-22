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
 * @author Curt Arnold
 */
public final class DependencyInfo {
  /**
   * Last modified time of this file or anything that it depends on.
   * 
   * Not persisted since almost any change could invalidate it. Initialized
   * to long.MIN_VALUE on construction.
   */
  // FREEHEP
  // private long compositeLastModified;
  private final String includePathIdentifier;
  private final List<Path> includes;
  private final Path source;
  private final FileTime sourceLastModified;
  private final List<Path> sysIncludes;
  // FREEHEP
  private Object tag = null;

  public DependencyInfo(final String includePathIdentifier, final Path source, final FileTime sourceLastModified,
      final List<Path> includes, final List<Path> sysIncludes) {
    if (source == null) {
      throw new NullPointerException("source");
    }
    if (includePathIdentifier == null) {
      throw new NullPointerException("includePathIdentifier");
    }
    this.source = source;
    this.sourceLastModified = sourceLastModified;
    this.includePathIdentifier = includePathIdentifier;
    this.includes = new ArrayList<>(includes);
    // BEGINFREEHEP
    // if (includes.size() == 0) {
    // compositeLastModified = sourceLastModified;
    // } else {
    // includes.copyInto(this.includes);
    // compositeLastModified = Long.MIN_VALUE;
    // }
    // ENDFREEHEP
    this.sysIncludes = new ArrayList<>(sysIncludes);
    // FREEHEP
  }

  // ENDFREEHEP
  public String getIncludePathIdentifier() {
    return this.includePathIdentifier;
  }

  public List<Path> getIncludes() {
    return Collections.unmodifiableList(this.includes);
  }

  public Path getSource() {
    return this.source;
  }

  public FileTime getSourceLastModified() {
    return this.sourceLastModified;
  }

  public List<Path> getSysIncludes() {
    return Collections.unmodifiableList(this.sysIncludes);
  }

  // BEGINFREEHEP
  /**
   * Returns true, if dependency info is tagged with object t.
   * 
   * @param t
   *          object to compare with
   * 
   * @return boolean, true, if tagged with t, otherwise false
   */
  public boolean hasTag(final Object t) {
    return this.tag == t;
  }

  // public void setCompositeLastModified(long lastMod) {
  // compositeLastModified = lastMod;
  // }
  // ENDFREEHEP
  /**
   * Returns the latest modification date of the source or anything that it
   * depends on.
   * 
   * @returns the composite lastModified time, returns Long.MIN_VALUE if not
   *          set
   */
  // BEGINFREEHEP
  // public long getCompositeLastModified() {
  // return compositeLastModified;
  // }
  public void setTag(final Object t) {
    this.tag = t;
  }
}
