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
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
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

  private NarDescriptor() {
  }

  /**
   * The names to link the libraries in a directory with, as libs.names
   * lists them: each file named prefix + name + "." + extension for one of
   * the patterns, where an extension ending in "*" also matches version
   * suffixes (libfoo.so.1.2).
   *
   * @param libDir the directory
   * @param patterns pairs of library file prefix (possibly empty) and extension
   * @return the space-separated names, sorted, or an empty string
   * @throws IOException if the directory can't be listed
   */
  public static String linkNames(final Path libDir, final List<String[]> patterns) throws IOException {
    if (!Files.isDirectory(libDir)) {
      return "";
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
      regexes.add(Pattern.compile(Pattern.quote(prefix) + "(.+)\\." + Pattern.quote(ext)
          + (versioned ? "(\\.[0-9][0-9.]*)?" : "")));
    }
    final TreeSet<String> names = new TreeSet<>();
    try (Stream<Path> files = Files.list(libDir)) {
      files.filter(Files::isRegularFile).map(file -> file.getFileName().toString()).forEach(name -> {
        for (final Pattern regex : regexes) {
          final Matcher m = regex.matcher(name);
          if (m.matches()) {
            names.add(m.group(1));
          }
        }
      });
    }
    return String.join(" ", names);
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
        return List.<String[]>of(new String[] { prefix == null ? "" : prefix, "lib" });
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
   * build, for example) the names of those files.</li>
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
        final String found = linkNames(libDir, patterns);
        final List<String> names = found.isEmpty() ? List.of() : Arrays.asList(found.split(" "));
        if (output != null && names.contains(output)) {
          descriptor.setProperty("libs.names", output);
        } else if (!names.isEmpty()) {
          descriptor.setProperty("libs.names", found);
          if (names.size() > 1) {
            log.warn("The " + aol + " " + type + " libraries don't include " + output + ", so consumers will link "
                + found + "; set libsName to choose the libraries and their order");
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
    Files.createDirectories(file.getParent());
    Files.writeString(file, body, StandardCharsets.ISO_8859_1);
  }
}
