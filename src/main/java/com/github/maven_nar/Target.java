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
import java.util.TreeSet;

import org.apache.maven.plugin.MojoExecutionException;

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
 * layout 21, see {@link TargetDeclaration}).  Values that depend on the
 * machine, such as syslibs.names, are only in each AOL-specific NAR's
 * descriptor.
 * <p>
 * Most projects ship the same types everywhere, so the AOLs can instead be
 * listed once in the root pom, each shipping the types the build produces:
 *
 * <pre>
 * &lt;nar.targetAols&gt;amd64-Linux-gpp,amd64-Windows-msvc&lt;/nar.targetAols&gt;
 * </pre>
 *
 * A module then declares a target only for an AOL whose types differ from
 * those built by the primary AOL's build, the one that deploys the main
 * nar.properties (see nar.primaryAol).
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
   * The declared targets' types by AOL, the AOLs as AOL writes them (so
   * amd64-Linux-g++ is amd64-Linux-gpp).
   *
   * @param targets the declared targets
   * @return the types, in declaration order
   * @throws MojoExecutionException if a target lacks its AOL or types, has
   *           an unknown type, or is declared twice
   */
  public static Map<String, Set<String>> typesByAol(final Collection<Target> targets) throws MojoExecutionException {
    final Map<String, Set<String>> typesByAol = new LinkedHashMap<>();
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
      if (typesByAol.containsKey(aol)) {
        throw new MojoExecutionException("Target " + aol + " is declared more than once");
      }
      typesByAol.put(aol, new LinkedHashSet<>(target.getTypes()));
    }
    return typesByAol;
  }

  /**
   * The types by AOL of the declared targets and of targetAols: each of
   * targetAols ships the types built here unless a target for it says
   * otherwise.
   *
   * @param targets the declared targets
   * @param targetAols the AOLs the project ships, possibly empty
   * @param builtTypes the types built here
   * @return the types, targetAols' first, in their order
   * @throws MojoExecutionException as for {@link #typesByAol(Collection)}, or
   *           if a targetAols entry is empty
   */
  public static Map<String, Set<String>> typesByAol(final Collection<Target> targets,
      final Collection<String> targetAols, final Set<String> builtTypes) throws MojoExecutionException {
    final Map<String, Set<String>> declared = typesByAol(targets);
    final Map<String, Set<String>> typesByAol = new LinkedHashMap<>();
    for (final String targetAol : targetAols) {
      if (targetAol == null || targetAol.trim().isEmpty()) {
        throw new MojoExecutionException("targetAols has an empty entry");
      }
      final String aol = new AOL(targetAol.trim()).toString();
      typesByAol.put(aol, declared.getOrDefault(aol, new TreeSet<>(builtTypes)));
    }
    declared.forEach(typesByAol::putIfAbsent);
    // an AOL inferred from a build that built nothing ships nothing
    typesByAol.values().removeIf(Set::isEmpty);
    return typesByAol;
  }
}
