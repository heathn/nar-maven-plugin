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

import java.nio.file.Path;

import org.apache.maven.model.Resource;
import org.codehaus.plexus.util.SelectorUtils;

/**
 * Where Maven copies a resource's files from, so that the project's own
 * copy of a file can be told from what a previous build generated in
 * target/classes.
 */
public final class ResourceSource {

  private ResourceSource() {
  }

  /**
   * The path, in a resource directory, of the file the resource copies to a
   * destination in target/classes, taking its targetPath, includes and
   * excludes into account, or null if it doesn't copy one there.
   *
   * @param resource the resource
   * @param destination the destination, relative to target/classes
   * @return the source path relative to the resource's directory, or null
   */
  public static String of(final Resource resource, final String destination) {
    // relative to target/classes, without . segments
    String targetPath = resource.getTargetPath() == null ? "" : resource.getTargetPath().replace('\\', '/');
    while (targetPath.startsWith("/")) {
      targetPath = targetPath.substring(1);
    }
    targetPath = targetPath.isEmpty() ? "" : Path.of(targetPath).normalize().toString().replace('\\', '/');
    while (targetPath.endsWith("/")) {
      targetPath = targetPath.substring(0, targetPath.length() - 1);
    }
    final String source;
    if (targetPath.isEmpty()) {
      source = destination;
    } else if (destination.startsWith(targetPath + "/")) {
      source = destination.substring(targetPath.length() + 1);
    } else {
      return null;
    }
    final boolean included = resource.getIncludes().isEmpty()
        || resource.getIncludes().stream().anyMatch(pattern -> matches(pattern, source));
    final boolean excluded = resource.getExcludes().stream().anyMatch(pattern -> matches(pattern, source));
    return included && !excluded ? source : null;
  }

  // An Ant-style resource pattern, as Maven matches them, where a trailing
  // / means everything below
  private static boolean matches(final String pattern, final String path) {
    String normalized = pattern.replace('\\', '/');
    if (normalized.endsWith("/")) {
      normalized += "**";
    }
    return SelectorUtils.matchPath(normalized, path, "/", true);
  }
}
