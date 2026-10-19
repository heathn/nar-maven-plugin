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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

/**
 * An AOL the project ships, and the library types it ships for it, as
 * declared in the plugin's targets configuration:
 *
 * <pre>
 * &lt;targets&gt;
 *   &lt;target&gt;
 *     &lt;aol&gt;amd64-Windows-msvc&lt;/aol&gt;
 *     &lt;types&gt;
 *       &lt;type&gt;shared&lt;/type&gt;
 *       &lt;type&gt;static&lt;/type&gt;
 *     &lt;/types&gt;
 *   &lt;/target&gt;
 * &lt;/targets&gt;
 * </pre>
 *
 * The AOLs are usually built on different machines, each deploying the
 * main nar.properties, which consumers read to find the AOL-specific NARs.
 * With the targets declared, every build describes all of them there (with
 * layout 21).  Values that depend on the machine, such as syslibs.names,
 * are only in each AOL-specific NAR's descriptor.
 */
public class Target {

  /**
   * The library types a target can ship.
   */
  public static final List<String> TYPES = List.of(Library.SHARED, Library.STATIC, Library.JNI, Library.EXECUTABLE,
      Library.PLUGIN);

  private String aol;

  private List<String> types = new ArrayList<>();

  public String getAol() {
    return this.aol;
  }

  public void setAol(final String aol) {
    this.aol = aol;
  }

  public List<String> getTypes() {
    return this.types;
  }

  public void setTypes(final List<String> types) {
    this.types = types;
  }

  /**
   * Writes the main nar.properties keys for every declared target, so that
   * every machine writes the same ones whatever it built:
   * &lt;aol&gt;.nar.&lt;type&gt; for each type it ships (the generic
   * nar.&lt;type&gt; if the project sets one) and empty for every
   * other type (so the generic nar.&lt;type&gt; doesn't claim it for this
   * AOL), &lt;aol&gt;.libs.binding (the project's generic libs.binding if the
   * target ships that type, otherwise shared, static or its first type), &lt;aol&gt;.output and,
   * when configured,
   * &lt;aol&gt;.libs.names.  Each replaces what was generated for the AOL
   * built here, but not a key set in the project's own nar.properties.  An
   * AOL that nar.properties maps to a target gets the target's resulting
   * keys too, except those the project sets for it.
   * The generic nar.&lt;type&gt; templates are written for the declared
   * types, for consumers on undeclared AOLs.
   *
   * @param targets the declared targets
   * @param narInfo the project's NarInfo
   * @param presetKeys the keys set in the project's own nar.properties
   * @param builtTypes the types built here, which targets may not ship
   * @param coordinate the project's groupId:artifactId:nar
   * @param libraryOutput the output name of a library
   * @param executableOutput the output name of an executable
   * @param libsName the configured libs.names, or null
   * @throws MojoExecutionException if a target lacks its AOL or types, has
   *           an unknown type, or is declared twice
   */
  public static void declare(final Collection<Target> targets, final NarInfo narInfo, final Set<String> presetKeys,
      final Set<String> builtTypes, final String coordinate, final String libraryOutput,
      final String executableOutput, final String libsName) throws MojoExecutionException {
    final Set<String> allTypes = new LinkedHashSet<>(TYPES);
    allTypes.addAll(builtTypes);
    final Set<String> declaredTypes = new LinkedHashSet<>();
    final Map<String, Target> byAol = new LinkedHashMap<>();
    for (final Target target : targets) {
      if (target.getAol() == null || target.getAol().trim().isEmpty() || target.getTypes() == null
          || target.getTypes().isEmpty()) {
        throw new MojoExecutionException("Each target needs an aol and at least one type");
      }
      for (final String type : target.getTypes()) {
        if (!TYPES.contains(type)) {
          throw new MojoExecutionException("Target " + target.getAol() + " has type " + type + ", not one of " + TYPES);
        }
      }
      final String aol = new AOL(target.getAol().trim()).toString();
      if (byAol.containsKey(aol)) {
        throw new MojoExecutionException("Target " + aol + " is declared more than once");
      }
      allTypes.addAll(target.getTypes());
      declaredTypes.addAll(target.getTypes());
      byAol.put(aol, target);
    }

    // AOLs that the project's nar.properties maps to a declared target
    final Map<String, Target> mapped = new LinkedHashMap<>();
    for (final String key : narInfo.getInfo().stringPropertyNames()) {
      final String value = narInfo.getInfo().getProperty(key).trim();
      // AOL splits arch-os-linker on its first two dashes, so linkers like
      // gpp-12.2 count; a key is a mapping when its value names a target,
      // which an AOL-prefixed property's value doesn't
      if (key.split("-", 3).length == 3 && value.split("-", 3).length == 3 && !byAol.containsKey(key)) {
        // as NarInfo.getAOL reads it, so amd64-Linux-g++ is amd64-Linux-gpp
        final Target target = byAol.get(new AOL(value).toString());
        if (target != null) {
          mapped.put(key, target);
        }
      }
    }
    // a generic libs.binding in the project's nar.properties, for the
    // targets that ship that type
    final String presetBinding = presetKeys.contains(NarInfo.LIBS_BINDING) ? narInfo.getBinding(null, null) : null;

    final List<String> suffixes = new ArrayList<>();
    allTypes.forEach(type -> suffixes.add(NarInfo.narKey(type)));
    suffixes.addAll(List.of(NarInfo.LIBS_BINDING, NarInfo.OUTPUT, NarInfo.LIBS_NAMES));
    byAol.forEach((aol, target) -> {
      for (final String type : allTypes) {
        // a generic nar.<type> the project sets applies to the types it ships
        final String generic = NarInfo.narKey(type);
        final String value;
        if (!target.getTypes().contains(type)) {
          value = "";
        } else if (presetKeys.contains(generic)) {
          // as the project's nar.properties has it; if that file was
          // replaced by another resource's, as if it weren't set
          final String preset = narInfo.getInfo().getProperty(generic);
          value = preset != null ? preset : coordinate + ":" + aol + "-" + type;
        } else {
          value = coordinate + ":" + aol + "-" + type;
        }
        set(narInfo, presetKeys, NarInfo.aolKey(aol, generic), value);
      }
      set(narInfo, presetKeys, NarInfo.aolKey(aol, NarInfo.LIBS_BINDING),
          presetBinding != null && target.getTypes().contains(presetBinding)
          ? presetBinding : preferredBinding(target.getTypes()));
      final boolean libraries = target.getTypes().stream().anyMatch(type -> !Library.EXECUTABLE.equals(type));
      set(narInfo, presetKeys, NarInfo.aolKey(aol, NarInfo.OUTPUT), libraries ? libraryOutput : executableOutput);
      if (libsName != null) {
        set(narInfo, presetKeys, NarInfo.aolKey(aol, NarInfo.LIBS_NAMES), libsName);
      }
    });
    // an AOL mapped to a target gets the target's entries as they end up
    mapped.forEach((alias, target) -> {
      final String aol = new AOL(target.getAol().trim()).toString();
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
        narInfo.getInfo().setProperty(key, NarInfo.genericNar(coordinate, type));
      }
    }
  }

  private static void set(final NarInfo narInfo, final Set<String> presetKeys, final String key,
      final String value) {
    if (!presetKeys.contains(key)) {
      narInfo.getInfo().setProperty(key, value);
    }
  }

  /**
   * Warns about the AOL-specific NARs built here that the targets don't
   * declare.
   *
   * @param targets the declared targets
   * @param built the types built here for each AOL
   * @param log for the warnings
   */
  public static void checkBuilt(final Collection<Target> targets, final Map<String, Set<String>> built,
      final Log log) {
    built.forEach((aol, types) -> {
      final Target target = targets.stream()
          .filter(t -> t.getAol() != null && new AOL(t.getAol().trim()).toString().equals(new AOL(aol).toString()))
          .findFirst().orElse(null);
      for (final String type : types) {
        if (target == null || !target.getTypes().contains(type)) {
          log.warn("The " + aol + "-" + type + " NAR built here isn't declared in targets");
        }
      }
    });
  }

  // As prepareNarInfo prefers them: shared, then static, then whatever else.
  private static String preferredBinding(final List<String> types) {
    if (types.contains(Library.SHARED)) {
      return Library.SHARED;
    }
    if (types.contains(Library.STATIC)) {
      return Library.STATIC;
    }
    return types.isEmpty() ? Library.NONE : types.get(0);
  }
}
