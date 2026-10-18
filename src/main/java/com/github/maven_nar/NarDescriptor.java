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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.plugin.logging.Log;

/**
 * The descriptor of an AOL-specific NAR: what the build that produced it
 * packaged, so that consumers don't have to rely on the main nar.properties,
 * which may come from another build.  See
 * {@link NarInfo#overlayDescriptor(Path, AOL)}.
 */
public final class NarDescriptor {

  /**
   * Marks a {@link #linkNames} pattern of a linker that ignores case.
   */
  public static final String IGNORE_CASE = "ignoreCase";

  private NarDescriptor() {
  }

  /**
   * The names to link the libraries in a directory with, as libs.names
   * lists them: each file named prefix + name + "." + extension for one of
   * the patterns, where an extension ending in "*" also matches version
   * suffixes (libfoo.so.1.2).
   *
   * @param libDir the directory
   * @param patterns pairs of library file prefix (possibly empty) and
   *          extension, with a third element "ignoreCase" for a linker that
   *          ignores case
   * @return the space-separated names, sorted, or an empty string
   * @throws IOException if the directory can't be listed
   */
  public static String linkNames(final Path libDir, final List<String[]> patterns) throws IOException {
    final TreeSet<String> names = new TreeSet<>();
    linkAliases(libDir, patterns).values().forEach(names::addAll);
    return String.join(" ", names);
  }

  /**
   * The names each library file in a directory can be linked by, for
   * {@link #linkNames}; a file matching several patterns (librenamed.dll as
   * name.dll and libname.dll) has several.
   *
   * @param libDir the directory
   * @param patterns as for {@link #linkNames}
   * @return the names, by file name
   * @throws IOException if the directory can't be listed
   */
  public static Map<String, TreeSet<String>> linkAliases(final Path libDir, final List<String[]> patterns)
      throws IOException {
    final Map<String, TreeSet<String>> aliases = new TreeMap<>();
    if (!Files.isDirectory(libDir)) {
      return aliases;
    }
    final List<Pattern> regexes = new ArrayList<>();
    for (final String[] pattern : patterns) {
      final String prefix = pattern[0] == null ? "" : pattern[0];
      final String extension = pattern[1];
      if (extension == null || extension.isEmpty()) {
        continue;
      }
      final boolean versioned = extension.endsWith("*");
      final String ext = versioned ? extension.substring(0, extension.length() - 1) : extension;
      final boolean ignoreCase = pattern.length > 2 && IGNORE_CASE.equals(pattern[2]);
      regexes.add(Pattern.compile(Pattern.quote(prefix) + "(.+)\\." + Pattern.quote(ext)
          + (versioned ? "(\\.[0-9][0-9.]*)?" : ""), ignoreCase ? Pattern.CASE_INSENSITIVE : 0));
    }
    try (Stream<Path> files = Files.list(libDir)) {
      files.filter(Files::isRegularFile).map(file -> file.getFileName().toString()).forEach(name -> {
        for (final Pattern regex : regexes) {
          final Matcher m = regex.matcher(name);
          if (m.matches()) {
            aliases.computeIfAbsent(name, n -> new TreeSet<>()).add(m.group(1));
          }
        }
      });
    }
    return aliases;
  }

  /**
   * The patterns a linker finds a library's files by, for
   * {@link #linkNames}: MSVC links name.lib (an import library, for a
   * shared one); a GNU linker on Windows a shared library's libname.dll.a,
   * name.dll or libname.dll, and a static one's libname.a; others the
   * prefix and extension aol.properties gives, prefix lib if none.
   *
   * @param aol the AOL, arch-os-linker
   * @param type shared or static
   * @param prefix the lib.prefix aol.properties gives, or null
   * @param extension the extension aol.properties gives for the type
   * @return pairs of prefix and extension
   */
  public static List<String[]> linkPatterns(final String aol, final String type, final String prefix,
      final String extension) {
    final String[] parts = aol.split("-");
    final String linker = parts[parts.length - 1];
    if (parts.length > 1 && OS.WINDOWS.equals(parts[1])) {
      if ("msvc".equals(linker) || "icl".equals(linker)) {
        return List.<String[]>of(new String[] { prefix == null ? "" : prefix, "lib", IGNORE_CASE });
      }
      return Library.SHARED.equals(type)
          ? List.of(new String[] { "lib", "dll.a" }, new String[] { "", "dll" }, new String[] { "lib", "dll" })
          : List.<String[]>of(new String[] { "lib", "a" });
    }
    return List.<String[]>of(new String[] { prefix == null ? "lib" : prefix, extension });
  }

  /**
   * The descriptor of one AOL-specific NAR.
   * <ul>
   * <li>output: as the main nar.properties has it for the AOL.</li>
   * <li>libs.names, for shared and static libraries: when configured, as
   * configured; otherwise the output when its library is among the library
   * files packaged, so consumers link what they link now whatever the main
   * nar.properties says; otherwise (a library renamed by a third-party
   * build, for example) the name of the one library file there, unversioned;
   * with several, none, and a warning to set libsName.</li>
   * <li>syslibs.names and linker.options: as the main nar.properties has
   * them for the AOL, empty if it has none, so that a value another AOL's
   * build put in the main nar.properties doesn't apply.</li>
   * </ul>
   *
   * @param narInfo the project's NarInfo, after prepareNarInfo
   * @param aol the NAR's AOL
   * @param type the NAR's library type
   * @param libDir the directory of the NAR's libraries
   * @param patterns the patterns the linker finds library files by, see
   *          {@link #linkPatterns}
   * @param log for warnings
   * @return the descriptor's properties
   * @throws IOException if the library directory can't be listed
   */
  public static Properties describe(final NarInfo narInfo, final AOL aol, final String type, final Path libDir,
      final List<String[]> patterns, final Log log) throws IOException {
    final Properties descriptor = new Properties();
    descriptor.setProperty(NarInfo.DESCRIPTOR_VERSION_KEY, String.valueOf(NarInfo.DESCRIPTOR_VERSION));
    final String output = narInfo.getOutput(aol, null);
    if (output != null) {
      descriptor.setProperty("output", output);
    }
    if (Library.SHARED.equals(type) || Library.STATIC.equals(type)) {
      final String configured = narInfo.getProperty(aol, "libs.names");
      if (configured != null) {
        descriptor.setProperty("libs.names", configured);
      } else {
        final Map<String, TreeSet<String>> aliases = linkAliases(libDir, patterns);
        final boolean ignoreCase = patterns.stream().anyMatch(p -> p.length > 2 && IGNORE_CASE.equals(p[2]));
        if (output != null && aliases.values().stream().flatMap(TreeSet::stream)
            .anyMatch(name -> ignoreCase ? name.equalsIgnoreCase(output) : name.equals(output))) {
          descriptor.setProperty("libs.names", output);
        } else {
          // The output's library isn't there, as when a third-party build
          // names its own.  Only an unambiguous replacement is recorded: one
          // name, of files the linker takes as they are (not libfoo.so.1,
          // which only a link made at unpack, from its SONAME, makes linkable),
          // each file by its shortest name (renamed for librenamed.dll).
          final TreeSet<String> names = new TreeSet<>();
          linkAliases(libDir, unversioned(patterns)).values().forEach(fileAliases -> names.add(fileAliases.stream()
              .min(Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder())).get()));
          if (names.size() == 1) {
            descriptor.setProperty("libs.names", names.first());
          } else if (names.size() > 1) {
            log.warn("The " + aol + " " + type + " libraries " + names + " don't include "
                + (output == null ? "the output" : output) + "; set libsName so that consumers link the right ones");
          }
        }
      }
    }
    for (final String key : new String[] { "syslibs.names", "linker.options" }) {
      final String value = narInfo.getProperty(aol, key);
      descriptor.setProperty(key, value == null ? "" : value);
    }
    return descriptor;
  }

  private static List<String[]> unversioned(final List<String[]> patterns) {
    final List<String[]> unversioned = new ArrayList<>();
    for (final String[] pattern : patterns) {
      final String[] copy = pattern.clone();
      if (copy[1] != null && copy[1].endsWith("*")) {
        copy[1] = copy[1].substring(0, copy[1].length() - 1);
      }
      unversioned.add(copy);
    }
    return unversioned;
  }

  /**
   * Writes properties with sorted keys and no timestamp, so that the same
   * properties always give the same bytes.
   *
   * @param file the file to write
   * @param properties the properties
   * @throws IOException if the file can't be written
   */
  public static void write(final Path file, final Properties properties) throws IOException {
    // store to a stream, which escapes characters outside ISO 8859-1
    final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    properties.store(bytes, null);
    // drop the timestamp comment that Properties.store writes first
    final String body = new String(bytes.toByteArray(), StandardCharsets.ISO_8859_1).lines()
        .filter(line -> !line.startsWith("#"))
        .sorted()
        .collect(Collectors.joining("\n", "", "\n"));
    // a bare file name has no parent
    Files.createDirectories(file.toAbsolutePath().getParent());
    Files.writeString(file, body, StandardCharsets.ISO_8859_1);
  }
}
