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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.plugin.logging.Log;

/**
 * Writes the declared targets into the main nar.properties, so that every
 * machine writes the same keys whatever it built.  See {@link Target}.
 */
public final class TargetDeclaration {

  /**
   * What the project gives every target.
   *
   * @param coordinate the project's groupId:artifactId:nar
   * @param libraryOutput the output name of a library
   * @param executableOutput the output name of an executable
   * @param libsName the configured libs.names, or null
   */
  public record Defaults(String coordinate, String libraryOutput, String executableOutput, String libsName) {
  }

  private TargetDeclaration() {
  }

  /**
   * Writes, for each declared AOL:
   * <ul>
   * <li>&lt;aol&gt;.nar.&lt;type&gt; for each type: the NAR for a type it
   * ships (the generic nar.&lt;type&gt; if the project sets one), empty for
   * any other, so the generic nar.&lt;type&gt; doesn't claim it for this
   * AOL;</li>
   * <li>&lt;aol&gt;.libs.binding: the project's generic libs.binding if the
   * target ships that type, otherwise its preferred type, see
   * {@link Library#preferredBinding};</li>
   * <li>&lt;aol&gt;.output, and &lt;aol&gt;.libs.names when configured.</li>
   * </ul>
   * Each replaces what was generated for the AOL built here, but not a key
   * set in the project's own nar.properties.  An AOL that nar.properties
   * maps to a target gets the target's resulting keys too, except those the
   * project sets for it.  The generic nar.&lt;type&gt; templates are written
   * for the declared types, for consumers on undeclared AOLs.
   *
   * @param typesByAol the declared targets, see {@link Target#typesByAol}
   * @param narInfo the project's NarInfo
   * @param presetKeys the keys set in the project's own nar.properties
   * @param builtTypes the types built here, which targets may not ship
   * @param defaults what the project gives every target
   */
  public static void declare(final Map<String, Set<String>> typesByAol, final NarInfo narInfo,
      final Set<String> presetKeys, final Set<String> builtTypes, final Defaults defaults) {
    final Set<String> allTypes = new LinkedHashSet<>(Target.TYPES);
    allTypes.addAll(builtTypes);
    final Set<String> declaredTypes = new LinkedHashSet<>();
    typesByAol.values().forEach(declaredTypes::addAll);
    allTypes.addAll(declaredTypes);
    final Map<String, String> aliases = aliases(narInfo, typesByAol.keySet());
    // a generic libs.binding in the project's nar.properties, for the
    // targets that ship that type
    final String presetBinding = presetKeys.contains(NarInfo.LIBS_BINDING) ? narInfo.getBinding(null, null) : null;

    typesByAol.forEach((aol, types) -> {
      for (final String type : allTypes) {
        final String generic = NarInfo.narKey(type);
        // a generic nar.<type> the project sets applies to the types it
        // ships; if another resource replaced that file, as if it weren't set
        final String preset = presetKeys.contains(generic) ? narInfo.getInfo().getProperty(generic) : null;
        final String nar;
        if (!types.contains(type)) {
          nar = "";
        } else if (preset != null) {
          nar = preset;
        } else {
          nar = defaults.coordinate() + ":" + aol + "-" + type;
        }
        set(narInfo, presetKeys, NarInfo.aolKey(aol, generic), nar);
      }
      set(narInfo, presetKeys, NarInfo.aolKey(aol, NarInfo.LIBS_BINDING),
          presetBinding != null && types.contains(presetBinding) ? presetBinding : Library.preferredBinding(types));
      final boolean libraries = types.stream().anyMatch(type -> !Library.EXECUTABLE.equals(type));
      set(narInfo, presetKeys, NarInfo.aolKey(aol, NarInfo.OUTPUT),
          libraries ? defaults.libraryOutput() : defaults.executableOutput());
      if (defaults.libsName() != null) {
        set(narInfo, presetKeys, NarInfo.aolKey(aol, NarInfo.LIBS_NAMES), defaults.libsName());
      }
    });
    // an alias gets its target's keys as they end up, preset ones included
    final List<String> suffixes = new ArrayList<>();
    allTypes.forEach(type -> suffixes.add(NarInfo.narKey(type)));
    suffixes.addAll(List.of(NarInfo.LIBS_BINDING, NarInfo.OUTPUT, NarInfo.LIBS_NAMES));
    aliases.forEach((alias, aol) -> {
      for (final String suffix : suffixes) {
        final String value = narInfo.getInfo().getProperty(NarInfo.aolKey(aol, suffix));
        if (value != null) {
          set(narInfo, presetKeys, NarInfo.aolKey(alias, suffix), value);
        }
      }
    });

    for (final String type : declaredTypes) {
      final String key = NarInfo.narKey(type);
      if (narInfo.getInfo().getProperty(key) == null) {
        narInfo.getInfo().setProperty(key, NarInfo.genericNar(defaults.coordinate(), type));
      }
    }
  }

  /**
   * Warns about the AOL-specific NARs built here that the targets don't
   * declare.
   *
   * @param typesByAol the declared targets, see {@link Target#typesByAol}
   * @param built the types built here for each AOL
   * @param log for the warnings
   */
  public static void checkBuilt(final Map<String, Set<String>> typesByAol, final Map<String, Set<String>> built,
      final Log log) {
    built.forEach((aol, types) -> {
      final Set<String> declared = typesByAol.getOrDefault(new AOL(aol).toString(), Set.of());
      for (final String type : types) {
        if (!declared.contains(type)) {
          log.warn("The " + aol + "-" + type + " NAR built here isn't declared in targets or targetAols");
        }
      }
    });
  }

  // The AOLs the project's nar.properties maps to a declared one, as
  // <alias>=<aol>, with the declared AOL each maps to
  private static Map<String, String> aliases(final NarInfo narInfo, final Set<String> declared) {
    final Map<String, String> aliases = new LinkedHashMap<>();
    for (final String key : narInfo.getInfo().stringPropertyNames()) {
      final String value = narInfo.getInfo().getProperty(key).trim();
      if (isAolMapping(key, value) && !declared.contains(key)) {
        // as NarInfo.getAOL reads it, so amd64-Linux-g++ is amd64-Linux-gpp
        final String aol = new AOL(value).toString();
        if (declared.contains(aol)) {
          aliases.put(key, aol);
        }
      }
    }
    return aliases;
  }

  // An AOL mapped to another: both arch-os-linker, split on their first two
  // dashes as AOL does (so linkers like gpp-12.2 count); an AOL-prefixed
  // property's value isn't one
  private static boolean isAolMapping(final String key, final String value) {
    return key.split("-", 3).length == 3 && value.split("-", 3).length == 3;
  }

  private static void set(final NarInfo narInfo, final Set<String> presetKeys, final String key,
      final String value) {
    if (!presetKeys.contains(key)) {
      narInfo.getInfo().setProperty(key, value);
    }
  }
}
