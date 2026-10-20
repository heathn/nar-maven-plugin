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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.plugin.logging.Log;

import com.github.maven_nar.cpptasks.CUtil;

/**
 * The descriptor of an AOL-specific NAR: what the build that produced it
 * packaged, so that consumers don't have to rely on the main nar.properties,
 * which may come from another build.  See
 * {@link NarInfo#overlayDescriptor(Path, AOL)}.
 */
public final class NarDescriptor {

  /**
   * How a linker finds a library's files: prefix + name + "." + extension,
   * such as lib + foo + .so.
   *
   * @param prefix the file name prefix, possibly empty
   * @param extension the extension, or null for none (the pattern matches
   *          nothing)
   * @param versioned whether version suffixes match too: libfoo.so.1.2
   * @param ignoreCase whether the linker ignores case, as MSVC's does
   */
  public record LinkPattern(String prefix, String extension, boolean versioned, boolean ignoreCase) {

    /**
     * A pattern as aol.properties gives it, where an extension ending in
     * "*" (so*) also matches version suffixes.
     *
     * @param prefix the prefix, or null for none
     * @param extension the extension, or null
     * @return the pattern
     */
    public static LinkPattern of(final String prefix, final String extension) {
      final boolean versioned = extension != null && extension.endsWith("*");
      return new LinkPattern(prefix == null ? "" : prefix,
          versioned ? extension.substring(0, extension.length() - 1) : extension, versioned, false);
    }

    LinkPattern withVersioned(final boolean versioned) {
      return new LinkPattern(this.prefix, this.extension, versioned, this.ignoreCase);
    }

    // group 1 is the name; null when the pattern matches nothing
    Pattern regex() {
      if (this.extension == null || this.extension.isEmpty()) {
        return null;
      }
      return Pattern.compile(Pattern.quote(this.prefix) + "(.+)\\." + Pattern.quote(this.extension)
          + (this.versioned ? "(\\.[0-9][0-9.]*)?" : ""), this.ignoreCase ? Pattern.CASE_INSENSITIVE : 0);
    }
  }

  private NarDescriptor() {
  }

  /**
   * The names to link the libraries in a directory with, as libs.names
   * lists them: the name of each file matching one of the patterns.
   *
   * @param libDir the directory
   * @param patterns the patterns the linker finds library files by
   * @return the space-separated names, sorted, or an empty string
   * @throws IOException if the directory can't be listed
   */
  public static String linkNames(final Path libDir, final List<LinkPattern> patterns) throws IOException {
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
  public static Map<String, TreeSet<String>> linkAliases(final Path libDir, final List<LinkPattern> patterns)
      throws IOException {
    final Map<String, TreeSet<String>> aliases = new TreeMap<>();
    if (!Files.isDirectory(libDir)) {
      return aliases;
    }
    final List<Pattern> regexes = patterns.stream().map(LinkPattern::regex).filter(Objects::nonNull)
        .collect(Collectors.toList());
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
   * name.dll.a, name.dll or libname.dll, and a static one's libname.a; others the
   * prefix and extension aol.properties gives, prefix lib if none.
   *
   * @param aol the AOL, arch-os-linker
   * @param type shared or static
   * @param prefix the lib.prefix aol.properties gives, or null
   * @param extension the extension aol.properties gives for the type
   * @return the patterns
   */
  public static List<LinkPattern> linkPatterns(final String aol, final String type, final String prefix,
      final String extension) {
    final AOL parsed = new AOL(aol);
    if (OS.WINDOWS.equals(parsed.getOS())) {
      if (parsed.hasLinker("msvc") || parsed.hasLinker("icl")) {
        return List.of(new LinkPattern(prefix == null ? "" : prefix, "lib", false, true));
      }
      return Library.SHARED.equals(type)
          ? List.of(LinkPattern.of("lib", "dll.a"), LinkPattern.of("", "dll.a"), LinkPattern.of("", "dll"),
              LinkPattern.of("lib", "dll"))
          : List.of(LinkPattern.of("lib", "a"));
    }
    return List.of(LinkPattern.of(prefix == null ? "lib" : prefix, extension));
  }

  /**
   * The descriptor of one AOL-specific NAR.
   * <ul>
   * <li>output: as the main nar.properties has it for the AOL.</li>
   * <li>libs.names, for shared and static libraries: when configured, each
   * name as the linker finds it among the files packaged (see
   * {@link #resolveLibsNames}); otherwise the output when its library is among the library
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
      final List<LinkPattern> patterns, final Log log) throws IOException {
    final Properties descriptor = new Properties();
    descriptor.setProperty(NarInfo.DESCRIPTOR_VERSION_KEY, String.valueOf(NarInfo.DESCRIPTOR_VERSION));
    final String output = narInfo.getOutput(aol, null);
    if (output != null) {
      descriptor.setProperty(NarInfo.OUTPUT, output);
    }
    if (Library.SHARED.equals(type) || Library.STATIC.equals(type)) {
      final String configured = narInfo.getProperty(aol, NarInfo.LIBS_NAMES);
      final String names = configured != null ? resolveLibsNames(configured, libDir, patterns, aol, type, log)
          : inferLibsNames(output, libDir, patterns, aol, type, log);
      if (names != null) {
        descriptor.setProperty(NarInfo.LIBS_NAMES, names);
      }
    }
    for (final String key : new String[] { NarInfo.SYSLIBS_NAMES, NarInfo.LINKER_OPTIONS }) {
      final String value = narInfo.getProperty(aol, key);
      descriptor.setProperty(key, value == null ? "" : value);
    }
    return descriptor;
  }

  /**
   * The libs.names configured, by libsName or nar.properties, each as the
   * linker finds it among a NAR's library files, so that one libsName serves
   * every AOL: webp is libwebp on MSVC, whose import library is libwebp.lib,
   * and libwebp is webp for a GNU linker.  A name matching no file is kept,
   * with a warning (consumers linking it fail unless it's found elsewhere,
   * as a system library is); without library files there is nothing to
   * check.
   *
   * @param configured the names, separated by commas or whitespace
   * @param libDir the directory of the NAR's libraries
   * @param patterns the patterns the linker finds library files by
   * @param aol the NAR's AOL
   * @param type the NAR's library type
   * @param log for warnings
   * @return the names, space-separated, in the configured order
   * @throws IOException if the directory can't be listed
   */
  public static String resolveLibsNames(final String configured, final Path libDir, final List<LinkPattern> patterns,
      final AOL aol, final String type, final Log log) throws IOException {
    final TreeSet<String> available = new TreeSet<>();
    linkAliases(libDir, patterns).values().forEach(available::addAll);
    final List<String> names = new CUtil.StringArrayBuilder(configured).getValue();
    if (available.isEmpty()) {
      return String.join(" ", names);
    }
    final boolean ignoreCase = patterns.stream().anyMatch(LinkPattern::ignoreCase);
    // in order, repeats included: a static library may have to be linked
    // again after one that refers to it
    final List<String> resolved = new ArrayList<>();
    final List<String> missing = new ArrayList<>();
    // separated by commas or whitespace, as consumers read them
    for (final String name : names) {
      final String unprefixed = name.startsWith("lib") ? name.substring("lib".length()) : name;
      final String match = Stream.of(name, "lib" + name, unprefixed)
          .flatMap(candidate -> available.stream()
              .filter(file -> ignoreCase ? file.equalsIgnoreCase(candidate) : file.equals(candidate)))
          .findFirst().orElse(null);
      resolved.add(match == null ? name : match);
      if (match == null) {
        missing.add(name);
      }
    }
    if (!missing.isEmpty()) {
      log.warn("The " + aol + " " + type + " libs.names " + String.join(" ", missing)
          + " isn't among the libraries packaged, " + available + ", so consumers linking it may fail; a system"
          + " library belongs in syslibs");
    }
    return String.join(" ", resolved);
  }

  /**
   * The names to link a NAR's libraries by when libsName isn't configured:
   * the output, when its library is among the files, so consumers link what
   * they link now whatever the main nar.properties says.  Otherwise, as when
   * a third-party build names its own library, the one name all the files
   * share: unversioned, as the linker takes libfoo.so but not libfoo.so.1,
   * which only a link made at unpack (from its SONAME) makes linkable, and
   * with no versioned file that could be the output's by its SONAME.  Each
   * file counts by its shortest name, renamed for librenamed.dll.
   *
   * @return the names, or null (with a warning if there are several)
   */
  private static String inferLibsNames(final String output, final Path libDir, final List<LinkPattern> patterns,
      final AOL aol, final String type, final Log log) throws IOException {
    final boolean ignoreCase = patterns.stream().anyMatch(LinkPattern::ignoreCase);
    if (output != null && linkAliases(libDir, patterns).values().stream().flatMap(TreeSet::stream)
        .anyMatch(name -> ignoreCase ? name.equalsIgnoreCase(output) : name.equals(output))) {
      return output;
    }
    final TreeSet<String> unversioned = shortestNames(linkAliases(libDir, withVersioned(patterns, false)));
    // so, as some AOLs give it, versioned too: libfoo.so.1
    final TreeSet<String> all = shortestNames(linkAliases(libDir, patterns.stream()
        .map(p -> "so".equals(p.extension()) ? p.withVersioned(true) : p).collect(Collectors.toList())));
    if (unversioned.size() == 1 && all.equals(unversioned)) {
      return unversioned.first();
    }
    if (all.size() > 1) {
      log.warn("The " + aol + " " + type + " libraries " + all + " don't include "
          + (output == null ? "the output" : output) + "; set libsName so that consumers link the right ones");
    }
    return null;
  }

  private static List<LinkPattern> withVersioned(final List<LinkPattern> patterns, final boolean versioned) {
    return patterns.stream().map(p -> p.withVersioned(versioned)).collect(Collectors.toList());
  }

  // each file's shortest name: renamed for librenamed.dll
  private static TreeSet<String> shortestNames(final Map<String, TreeSet<String>> aliases) {
    final TreeSet<String> names = new TreeSet<>();
    aliases.values().forEach(fileAliases -> names.add(fileAliases.stream()
        .min(Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder())).get()));
    return names;
  }

}
