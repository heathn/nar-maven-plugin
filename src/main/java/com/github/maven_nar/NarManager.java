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
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.surefire.shared.lang3.function.Failable;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

/**
 * @author Mark Donszelmann (Mark.Donszelmann@gmail.com)
 */
public class NarManager {

  private final Log log;

  private final MavenProject project;

  private final RepositorySystem repoSystem;

  private final RepositorySystemSession repoSession;

  private final List<RemoteRepository> repositories;

  private final AOL defaultAOL;

  private final String linkerName;

  private final String[] narTypes = {
      NarConstants.NAR_NO_ARCH, Library.STATIC, Library.SHARED, Library.JNI, Library.PLUGIN
  };

  public NarManager(final Log log, final RepositorySystem repoSystem, final RepositorySystemSession repoSession, final List<RemoteRepository> repositories, final MavenProject project,
      final String architecture, final String os, final Linker linker)
      throws MojoFailureException, MojoExecutionException {
    this.log = log;
    this.repoSystem = repoSystem;
    this.repoSession = repoSession;
    this.repositories = repositories;
    this.project = project;
    this.defaultAOL = NarUtil.getAOL(project, architecture, os, linker, null, log);
    this.linkerName = NarUtil.getLinkerName(project, architecture, os, linker, log);
  }

  private ArtifactResult resolve(Artifact artifact) throws ArtifactResolutionException {
    ArtifactRequest ar = new ArtifactRequest(artifact, repositories, null);
    return repoSystem.resolveArtifact(repoSession, ar);
  }

/**
  public final void downloadAttachedNars(final List/-* <NarArtifacts> *-/narArtifacts, final List remoteRepositories,
      final ArtifactResolver resolver, final String classifier) throws MojoExecutionException, MojoFailureException {
    // FIXME this may not be the right way to do this.... -U ignored and
    // also SNAPSHOT not used
    final List dependencies = getAttachedNarDependencies(narArtifacts, classifier);

    this.log.debug("Download called with classifier: " + classifier + " for NarDependencies {");
    for (final Object dependency2 : dependencies) {
      this.log.debug("  - " + dependency2);
    }
    this.log.debug("}");

    for (final Object dependency1 : dependencies) {
      final Artifact dependency = (Artifact) dependency1;
      try {
        this.log.debug("Resolving " + dependency);
        resolver.resolve(dependency, remoteRepositories, this.repository);
      } catch (final ArtifactNotFoundException e) {
        final String message = "nar not found " + dependency.getId();
        throw new MojoExecutionException(message, e);
      } catch (final ArtifactResolutionException e) {
        final String message = "nar cannot resolve " + dependency.getId();
        throw new MojoExecutionException(message, e);
      }
    }
  }
*/

  private List<Artifact> getAttachedNarDependencies(
      final NarArtifact dependency,
      final AOL aol,
      final String type) throws MojoExecutionException, MojoFailureException {

    log.debug("GetNarDependencies for " + dependency + ", aol: " + aol + ", type: " + type);
    final NarInfo narInfo = getNarInfo(dependency);
    final AOL aolString = narInfo.getAOL(aol);
    // FIXME Move this to NarInfo....
    List<ArtifactRequest> req = narInfo.getAttachedNars(aol, type).stream()
        .peek(dep -> log.debug("    Checking: " + dep))
        .filter(Predicate.not(String::isEmpty))
        .map(dep -> {
          // Set the AOL, if any
          if (aolString != null) {
            dep = NarUtil.replace("${aol}", aolString.toString(), dep);
          }
          // Add version if not already included
          long cnt = dep.chars().filter(ch -> ch == ':').count() + 1;
          if (cnt < 5) {
            dep += ":" + dependency.getBaseVersion();
          }
          return dep;
        })
        .map(dep -> new ArtifactRequest(new DefaultArtifact(dep), repositories, null))
        .collect(Collectors.toList());

    try {
      final List<Artifact> artifactList = repoSystem.resolveArtifacts(repoSession, req).stream()
        .map(ArtifactResult::getArtifact)
        .collect(Collectors.toList());
      return artifactList;
    } catch(ArtifactResolutionException e) {
      throw new MojoExecutionException("Could resolve artifacts", e);
    }
  }

  public final List<Artifact> getAttachedNarDependencies(final List<NarArtifact> narArtifacts)
      throws MojoExecutionException, MojoFailureException {
    return getAttachedNarDependencies(narArtifacts, (String) null);
  }

  /**
   * Returns a list of all attached nar dependencies for a specific binding and
   * "noarch", but not where "local" is
   * specified
   * 
   * @param scope
   *          compile, test, runtime, ....
   * @param aol
   *          either a valid aol, noarch or null. In case of null both the
   *          default getAOL() and noarch dependencies
   *          are returned.
   * @param type
   *          noarch, static, shared, jni, or null. In case of null the default
   *          binding found in narInfo is used.
   * @return
   * @throws MojoExecutionException
   * @throws MojoFailureException
   */
  public final List<Artifact> getAttachedNarDependencies(
      final List<NarArtifact> narArtifacts,
      final AOL archOsLinker,
      final String type) throws MojoExecutionException, MojoFailureException {

    boolean noarch = false;
    AOL aol = archOsLinker;
    if (aol == null) {
      noarch = true;
      aol = this.defaultAOL;
    }

    final List<Artifact> artifactList = new ArrayList<>();
    for (final NarArtifact narArtifact : narArtifacts) {
      final NarInfo narInfo = getNarInfo(narArtifact);
      if (noarch) {
        artifactList.addAll(getAttachedNarDependencies(narArtifact, null, NarConstants.NAR_NO_ARCH));
      }

      // use preferred binding, unless non existing.
      final String binding = narInfo.getBinding(aol, type != null ? type : Library.STATIC);

      // FIXME kludge, but does not work anymore since AOL is now a class
      if (aol.equals(NarConstants.NAR_NO_ARCH)) {
        // FIXME no handling of local
        artifactList.addAll(getAttachedNarDependencies(narArtifact, null, NarConstants.NAR_NO_ARCH));
      } else {
        artifactList.addAll(getAttachedNarDependencies(narArtifact, aol, binding));
      }
    }
    return artifactList;
  }

  public final List<Artifact> getAttachedNarDependencies(
      final List<NarArtifact> narArtifacts, final String classifier)
      throws MojoExecutionException, MojoFailureException {
    AOL aol = null;
    String type = null;
    if (classifier != null) {
      final int dash = classifier.lastIndexOf('-');
      if (dash < 0) {
        aol = new AOL(classifier);
      } else {
        aol = new AOL(classifier.substring(0, dash));
        type = classifier.substring(dash + 1);
      }
    }
    return getAttachedNarDependencies(narArtifacts, aol, type);
  }

  // public final List/* <AttachedNarArtifact> */getAttachedNarDependencies(final List/*
  //                                                                                   * <
  //                                                                                   * NarArtifacts
  //                                                                                   * >
  //                                                                                   */narArtifacts,
  //     final String[] classifiers) throws MojoExecutionException, MojoFailureException {

  //   final List artifactList = new ArrayList();

  //   if (classifiers != null && classifiers.length > 0) {

  //     for (final String classifier : classifiers) {
  //       artifactList.addAll(getAttachedNarDependencies(narArtifacts, classifier));
  //     }
  //   } else {
  //     artifactList.addAll(getAttachedNarDependencies(narArtifacts, (String) null));
  //   }

  //   return artifactList;
  // }

  public List<Artifact> getDependencies(final List<String> scopes) {
    final List<Artifact> artifacts = this.project.getArtifacts().stream()
      .filter(a -> scopes.contains(a.getScope()))
      .map(a -> new DefaultArtifact(a.getId()))
      .collect(Collectors.toList());
    return artifacts;
  }

  /**
   * Returns dependencies which are dependent on NAR files (i.e. contain
   * NarInfo)
   */
  public final List<NarArtifact> getNarDependencies(final List<String> scopes) throws MojoExecutionException {
    try {
      return getDependencies(scopes).stream()
        .filter(a -> "nar".equalsIgnoreCase(a.getExtension()))
        .map(Failable.asFunction(a -> {
          this.log.debug("Examining artifact for NarInfo: " + a);
          final NarInfo narInfo = getNarInfo(a);
          if (narInfo != null) {
            this.log.debug("    - added as NarDependency");
            return Optional.of(new NarArtifact(a, narInfo));
          }
          return Optional.<NarArtifact>empty();
        }))
        .flatMap(Optional::stream)
        .collect(Collectors.toList());
    } catch (RuntimeException e) {
      throw new MojoExecutionException(e);
    }
  }

  /**
   * Returns dependencies which are dependent on NAR files (i.e. contain
   * NarInfo)
   */
  public final List<NarArtifact> getNarDependencies(final String scope) throws MojoExecutionException {
    final List<String> scopes = new ArrayList<String>();
    scopes.add(scope);
    return getNarDependencies(scopes);
  }

  public final Path getNarFile(final Artifact dependency) throws MojoFailureException {
    ArtifactResult ar;
    try {
      ar = resolve(dependency);
    } catch (ArtifactResolutionException e) {
      throw new MojoFailureException(e);
    }
    // TODO: Introduced in 1.9.6: ar.getLocalArtifactResult().getPath();
    return ar.getArtifact().getFile().toPath();
  }

  public final NarInfo getNarInfo(final Artifact dependency) throws MojoExecutionException, MojoFailureException {
    final Path file = getNarFile(dependency);

    if (Files.notExists(file)) {
      return null;
    }

    try(JarFile jar = new JarFile(file.toFile())) {
      final NarInfo info = new NarInfo(dependency.getGroupId(), dependency.getArtifactId(),
          dependency.getBaseVersion(), this.log);
      if (!info.exists(jar)) {
        return null;
      }
      info.read(jar);
      return info;
    } catch (final IOException e) {
      throw new MojoExecutionException("Error while reading " + file, e);
    }
  }

  public final void unpackAttachedNars(final List<NarArtifact> narArtifacts,
      final ArchiverManager archiverManager, final String classifier, final String os, final NarLayout layout,
      final Path unpackDir, boolean skipRanlib) throws MojoExecutionException, MojoFailureException {

    this.log.debug("Unpack called for OS: " + os + ", classifier: " + classifier + " for NarArtifacts {");
    for (final Object narArtifact : narArtifacts) {
      this.log.debug("  - " + narArtifact);
    }
    this.log.debug("}");
    // FIXME, kludge to get to download the -noarch, based on classifier
    try {
      getAttachedNarDependencies(narArtifacts, classifier).stream()
        .forEach(Failable.asConsumer(dependency -> {
          this.log.debug("Unpack " + dependency + " to " + unpackDir);
          final Path file = getNarFile(dependency);

          layout.unpackNar(unpackDir, archiverManager, file, os, this.linkerName, this.defaultAOL, skipRanlib);
        }));
      } catch (RuntimeException e) {
        throw new MojoExecutionException(e);
      }
  }
}
