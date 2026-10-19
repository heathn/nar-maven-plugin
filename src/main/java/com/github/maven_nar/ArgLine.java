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
import java.util.Properties;

/**
 * Reading and extending a Surefire argLine as Surefire does: it expands
 * @{...} just before forking and splits the line on whitespace outside
 * quotes (maven-shared-utils' translateCommandline).
 */
public final class ArgLine {

  // The JVM option that sets the library path
  private static final String LIBRARY_PATH = "-Djava.library.path";

  private ArgLine() {
  }

  /**
   * Expands the @{...} properties in an argLine, as Surefire does late,
   * leaving those that aren't defined.
   *
   * @param argLine the argLine, or null
   * @param properties the properties
   * @return the expanded argLine
   */
  public static String expandLateProperties(final String argLine, final Properties properties) {
    if (argLine == null || argLine.indexOf("@{") < 0) {
      return argLine;
    }
    final StringBuilder expanded = new StringBuilder();
    int i = 0;
    while (i < argLine.length()) {
      final int start = argLine.indexOf("@{", i);
      final int end = start < 0 ? -1 : argLine.indexOf('}', start);
      if (end < 0) {
        expanded.append(argLine, i, argLine.length());
        break;
      }
      final String value = properties.getProperty(argLine.substring(start + 2, end));
      expanded.append(argLine, i, start).append(value != null ? value : argLine.substring(start, end + 1));
      i = end + 1;
    }
    return expanded.toString();
  }

  /**
   * Where an argLine sets java.library.path.
   *
   * @param start the argument's first character
   * @param end the character after it
   * @param value the path it sets, possibly empty
   */
  record LibraryPath(int start, int end, String value) {
  }

  /**
   * Finds the last argument in an argLine, quoted in whole or in part, that
   * sets java.library.path; the JVM uses the last one.
   *
   * @param argLine the argLine, or null
   * @return where it is, or null if there's none
   */
  static LibraryPath findLibraryPath(final String argLine) {
    if (argLine == null) {
      return null;
    }
    LibraryPath last = null;
    int i = 0;
    while (i < argLine.length()) {
      while (i < argLine.length() && Character.isWhitespace(argLine.charAt(i))) {
        i++;
      }
      if (i == argLine.length()) {
        break;
      }
      final int tokenStart = i;
      final StringBuilder token = new StringBuilder();
      char quote = 0;
      while (i < argLine.length() && (quote != 0 || !Character.isWhitespace(argLine.charAt(i)))) {
        final char c = argLine.charAt(i++);
        // as in Surefire, a quote after a backslash is literal, backslash and all
        if (c == '\\' && i < argLine.length() && (argLine.charAt(i) == '"' || argLine.charAt(i) == '\'')) {
          token.append(c).append(argLine.charAt(i++));
        } else if (quote == 0 && (c == '"' || c == '\'')) {
          quote = c;
        } else if (c == quote) {
          quote = 0;
        } else {
          token.append(c);
        }
      }
      // without "=" the JVM sets it empty
      if (token.toString().startsWith(LIBRARY_PATH + "=") || token.toString().equals(LIBRARY_PATH)) {
        last = new LibraryPath(tokenStart, i,
            token.length() <= LIBRARY_PATH.length() ? "" : token.substring(LIBRARY_PATH.length() + 1));
      }
    }
    return last;
  }

  /**
   * Adds directories to the last -Djava.library.path in an argLine, which
   * would otherwise hide the ones in the environment.  The argument is
   * quoted if it has spaces or quotes; one with both kinds of quote, which
   * Surefire can't take, can't be extended.
   *
   * @param argLine the argLine, or null
   * @param directories the directories, separated by the path separator
   * @return the argLine, unchanged if it doesn't set java.library.path, or
   *         null if that argument has both kinds of quote
   */
  public static String extendLibraryPath(final String argLine, final String directories) {
    final LibraryPath last = findLibraryPath(argLine);
    if (last == null) {
      return argLine;
    }
    final String path = last.value().isEmpty() ? directories : last.value() + File.pathSeparator + directories;
    String argument = LIBRARY_PATH + "=" + path;
    // Surefire splits argLine on whitespace outside quotes, taking a quote
    // after a backslash literally; the argument written here never ends in a
    // backslash, as a NAR directory comes last
    if (argument.chars().anyMatch(c -> Character.isWhitespace(c) || c == '"' || c == '\'')) {
      if (argument.indexOf('"') < 0) {
        argument = "\"" + argument + "\"";
      } else if (argument.indexOf('\'') < 0) {
        argument = "'" + argument + "'";
      } else {
        return null;
      }
    }
    return argLine.substring(0, last.start()) + argument + argLine.substring(last.end());
  }
}
