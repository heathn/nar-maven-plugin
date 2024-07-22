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

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ListIterator;
import java.util.Objects;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;

import org.apache.commons.lang3.function.Failable;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.shared.artifact.filter.collection.ArtifactFilterException;
import org.apache.maven.shared.artifact.filter.collection.ArtifactIdFilter;
import org.apache.maven.shared.artifact.filter.collection.FilterArtifacts;
import org.apache.maven.shared.artifact.filter.collection.GroupIdFilter;
import org.apache.maven.shared.artifact.filter.collection.ScopeFilter;

import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.util.StringUtils;

import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.collection.CollectRequest;

import org.eclipse.aether.util.graph.selector.AndDependencySelector;
import org.eclipse.aether.util.graph.selector.OptionalDependencySelector;
import org.eclipse.aether.util.graph.selector.ScopeDependencySelector;

import org.eclipse.aether.util.graph.transformer.NoopDependencyGraphTransformer;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.graph.Dependency;

import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.DefaultRepositorySystemSession;

/**
 * @author Mark Donszelmann
 */
public abstract class AbstractDependencyMojo extends AbstractNarMojo {

  /**
   * Comma separated list of Artifact names to exclude.
   * 
   * @since 2.0
   */
  @Parameter(property = "excludeArtifactIds", defaultValue = "")
  protected String excludeArtifactIds;

  /**
   * Comma separated list of Artifact names to include.
   * 
   * @since 2.0
   */
  @Parameter(property = "includeArtifactIds", defaultValue = "")
  protected String includeArtifactIds;

  /**
   * Comma separated list of GroupId Names to exclude.
   * 
   * @since 2.0
   */
  @Parameter(property = "excludeGroupIds", defaultValue = "")
  protected String excludeGroupIds;

  /**
   * Comma separated list of GroupIds to include.
   * 
   * @since 2.0
   */
  @Parameter(property = "includeGroupIds", defaultValue = "")
  protected String includeGroupIds;

  /**
   * The computed dependency tree root node of the Maven project.
   */
  protected DependencyNode rootNode;

  /**
   * The dependency tree builder to use.
   */
  @Component( hint = "default" )
  protected DependencyGraphBuilder dependencyGraphBuilder;

  /**
   * The Repository object that dispatches the verbose dependency graph collection request.
   * @since 3.5.2
   */
  @Component  
  private RepositorySystem repoSystem;

  /**
   * The Session object for controling/configuring the verbose dependency graph collection request.
   * @since 3.5.2
   */
  @Parameter(defaultValue = "${repositorySystemSession}")
  private RepositorySystemSession repoSession;

  /**
   * The List of repositories queried by the verbose dependency graph collection request.
   * @since 3.5.2
   */  
  @Parameter(defaultValue = "${project.remoteProjectRepositories}")
  private List<RemoteRepository> projectRepos;

  // in very large projects where n >= 2 libraries are created, getNarArtifacts()
  // will be call n times. The time distance between the first and second calls
  // may be arbitrarily large since CCTask::execute is asynchronous. If it becomes
  // too large, getMavenProject().getArtifacts() may differ.
  // This should make sense since the the list of artifacts should not differ
  // between the first and second libraries.
  private final List<NarArtifact> narDependencies = new LinkedList<>();

  /**
   * Gets the project's full dependency tree prior to dependency mediation. This is required if
   * we want to know where to push libraries in the linker line.
   * @return {@link org.eclipse.aether.graph.DependencyNode Root node} of the projects verbose dependency tree.
   * @since 3.5.2
  */
  protected DependencyNode getVerboseDependencyTree()
  {
    // Create CollectRequest object that will be submitted to collect the dependencies
    CollectRequest collectReq = new CollectRequest();

    // Get artifact this Maven project is attempting to build
    Artifact art = new DefaultArtifact(getMavenProject().getArtifact().getId());
    
    DefaultRepositorySystemSession session = new DefaultRepositorySystemSession(repoSession);

    // Set the No-Op Graph transformer so tree stays intact
    session.setDependencyGraphTransformer(new NoopDependencyGraphTransformer());
    
    // Create Aether graph dependency object from params extracted above
    Dependency dep = new Dependency(art, getMavenProject().getArtifact().getScope());
    
    // Set the root of the request, in this case the current project will be the root
    collectReq.setRoot(dep);

    // Set the repos the collectReq will hit
    collectReq.setRepositories(projectRepos);

    // Get test scope dependencies if we are in the testCompile phase
    if (this instanceof NarTestCompileMojo)
    {
      DependencySelector dependencySelector = new AndDependencySelector(new OptionalDependencySelector(), new ScopeDependencySelector(dep.getScope()));
      session.setDependencySelector(dependencySelector);
    }

    try {
      return repoSystem.collectDependencies(session, collectReq).getRoot();
    } catch (DependencyCollectionException exception) {
      this.getLog().warn("Could not collect dependencies from repo system", exception);
      return null;
    }
  }

  /**
   * Serializes the dependency tree of current maven project to a string of comma separated list
   * of groupId:artifactId traversing nodes in Level-Order way (also called BFS algorithm)
   * 
   * @param pushDepsToLowestOrder when {@code true} enables linker re-ordering logic such that libraries
   * that are lower in the dependency hierarchy appear lower in the list, maximizing symbol resolution.
   * @return {@link String Dependency tree string} of comma separated list of groupId:artifactId
   * @throws MojoExecutionException
   */
  protected List<DependencyNode> dependencyTreeOrderStr(boolean pushDepsToLowestOrder, boolean specifyDirectDeps) throws MojoExecutionException
  {
    return repoSystem.flattenDependencyNodes(repoSession, rootNode, null);
/**
    String depLevelOrderStr = "";
    DependencyNode libTreeRootNode;

    try {
      libTreeRootNode = getRootNodeDependecyTree();
    } catch (MojoExecutionException exception) {
      this.getLog().warn("linker Nar Default DependencyLibOrder is not used");
      return depLevelOrderStr;
    }

    this.getLog().debug("{");
    this.getLog().debug("Dependency Lib Order to be used::");

    /* Check if we should try to push libraries to their lowest place in the 
     * link order. We will use the full dependency tree from Aether (unmediated)
     * to determine this order. 
     */
/***
    if (pushDepsToLowestOrder || specifyDirectDeps)
    {
      org.eclipse.aether.graph.DependencyNode verboseTreeRootNode = getVerboseDependencyTree ();
      List <String> verboseDepList;
      try {
          // Get verbose (full) list of dependencies
          verboseDepList = depLevelVerboseList(verboseTreeRootNode);
      } catch (MojoExecutionException e) {
          this.getLog().warn("Exception caught while getting verbose dependency list: ", e);
          throw e;
      }

      // Create set that tracks if we found a duplicate library in the list
      Set<String> reducedDepSet = new HashSet <String> ();
      
      /* Traverse full dependency list in REVERSE order. The dependencies at the
       * end of the list signify the ones that are most heavily depended on 
       * and therefore need to occur later in the link order.
       */
/**
      for (int i = verboseDepList.size()-1; i >= 0; i--)
      {
        String depStr = verboseDepList.get(i);

        /* Create link order by pushing new dep to the front of the list. Do not
         * insert dep if it was added already (i.e. if adding to ReducedDepSet fails)
         */
/**
        if (reducedDepSet.add(depStr))
        {
          this.getLog().debug(depStr);

          if (!depLevelOrderStr.isEmpty()){
            depLevelOrderStr= depStr + "," + depLevelOrderStr;
          }else{
            depLevelOrderStr= depStr;
          }
        }
      }

      if (specifyDirectDeps)
      {
        // Get first level of deps, when specifyAllDirectDeps is true those can be the only ones linked.
        Set<String> directDepsSet = getDirectDepsSet(verboseTreeRootNode);

        // Trim all deps from verboseDepList that are not in the directDepsSet, warn if they are found.
        Iterator <String> it = reducedDepSet.iterator();
        while(it.hasNext()){
            String dep = it.next();
            if(!directDepsSet.contains(dep)){
                this.getLog().warn("Stray dependency: " + dep + " found. This may cause build failures.");
                reducedDepSet.remove(it);
            }
        }
      }

    }
    else
    {

      List<DependencyNode> NodeList = depLevelOrderList(libTreeRootNode);

      for (DependencyNode node : NodeList) 
      {
        if ( node != null )
        {
          String[] nodestring = node.toString().split(":");
          String usestring = nodestring[0] + ":" + nodestring[1];

          this.getLog().debug(usestring);

          if (!depLevelOrderStr.isEmpty()){
            depLevelOrderStr= depLevelOrderStr + "," + usestring;
          }else{
            depLevelOrderStr= usestring;
          } 
        }
      }
    }
      
    this.getLog().debug("}");
    return depLevelOrderStr;
*/
  }

  /**
   * Gets the set strings of the form "&lt;groupId&gt;:&lt;artifactId&gt;" representing the specified nodes direct dependencies (immediate children).
   *
   * @param rootNode {@link org.eclipse.aether.graph.DependencyNode root node} of the tree to fetch the direct dependencies from.
   * @return {@link HashSet<String>} of the form "&lt;groupId&gt;:&lt;artifactId&gt;" containing all direct dependencies of the parameter specified by rootNode.
   * @since 3.5.3
   */
  protected HashSet<String> getDirectDepsSet(org.eclipse.aether.graph.DependencyNode rootNode)
  {
    HashSet<String> directDepsSet = new HashSet<String> ();
    List <org.eclipse.aether.graph.DependencyNode> directDepsList = rootNode.getChildren();
    ListIterator <org.eclipse.aether.graph.DependencyNode> iter = directDepsList.listIterator();

    // Accumulate set that represents the collection of direct deps
    while (iter.hasNext()){
      org.eclipse.aether.artifact.Artifact art = iter.next().getArtifact();
      directDepsSet.add(createArtifactString(art));
    }

    return directDepsSet;
  }
  
  /**
   * Get List of Nodes of Dependency tree serialised by traversing nodes
   * in Level-Order way (also called BFS algorithm)
   *
   * @param rootNode root node of the project Dependency tree
   * @return the dependency tree string of comma separated list
   * of groupId:artifactId
   */
  private List<DependencyNode> depLevelOrderList(DependencyNode rootNode)
  {

    // Create list to store aggregate list of all nodes in tree
    List<DependencyNode> aggDepNodeList = new LinkedList<>();
    
    // Create list that stores current breadth
    List<DependencyNode> nodeChildList = rootNode.getChildren();
    //LevelOrderList.add(rootNode);

    while (!nodeChildList.isEmpty()) {
      nodeChildList = levelTraverseTreeList(nodeChildList, aggDepNodeList);
    }
    
    return aggDepNodeList;
  }

  /**
   * helper function for traversing nodes
   * in Level-Order way (also called BFS algorithm)
   */
  private List<DependencyNode> levelTraverseTreeList(List<DependencyNode>  nodeList, List<DependencyNode> aggDepNodeList )
  {
    aggDepNodeList.addAll(nodeList);
    List<DependencyNode> NodeChildList = new ArrayList<DependencyNode>();
    for (DependencyNode node : nodeList) {
      if ( (node != null) && (node.getChildren() != null) ) {
        NodeChildList.addAll(node.getChildren());
      }
    }

    return NodeChildList;
  }

  /**
   * Get verbose List of Nodes in Dependency tree serialized by using a BFS algorithm
   * (copied/refactored from the depLevelOrderList method)
   *
   * @param rootNode {@link org.eclipse.aether.graph.DependencyNode root node} of the project's dependency tree
   * @return {@link List} of strings of the form "&lt;groupId&gt;:&lt;artifactId&gt;" representing the verbose (full) dependency tree.
   * @throws MojoExecutionException
   * @since 3.5.2
   */
  private List<String> depLevelVerboseList(org.eclipse.aether.graph.DependencyNode rootNode) throws MojoExecutionException
  {

    // Create list to store aggregate of all nodes in the dependency tree BFS
    List <org.eclipse.aether.graph.DependencyNode> AggDepNodeList = 
        new ArrayList<org.eclipse.aether.graph.DependencyNode> ();
    
    // Create list that stores current breadth
    Set <org.eclipse.aether.graph.DependencyNode> NodeChildList = 
      new LinkedHashSet<org.eclipse.aether.graph.DependencyNode>(rootNode.getChildren());

    // Iterate over each breadth to aggregate the dependency list
    while (!NodeChildList.isEmpty()) {
      NodeChildList = levelTraverseVerboseTreeList(NodeChildList, AggDepNodeList, rootNode);
    }
    
    List <String> FullDepList = new ArrayList<String> ();
    
    // Construct list of Strings representing the deps in the form "<groupId>:<artifactId>"
    for (ListIterator<org.eclipse.aether.graph.DependencyNode> it = AggDepNodeList.listIterator (); it.hasNext ();)
    {
      FullDepList.add(createArtifactString(it.next().getArtifact()));
    }
    return FullDepList;
  }

  /**
   * helper function for traversing nodes from the Aether package
   * in Level-Order way.
   * (copied/refactored from the levelTraverseTreeList method)
   *
   * @param nodeList {@link List} representing current breadth of nodes to be visited
   * @param aggDepNodeList {@link List} representing aggregate nodes current breadth is added to.
   * @param rootNode {@link org.eclipse.aether.graph.DependencyNode root node} of the project dependency tree used to check for circular dependencies.
   * @return {@link List} of dependency nodes representing the next breadth to visit.
   * @throws MojoExecutionException
   * @since 3.5.2
   */
  private Set<org.eclipse.aether.graph.DependencyNode> levelTraverseVerboseTreeList(
        Set<org.eclipse.aether.graph.DependencyNode>  nodeList, 
        List <org.eclipse.aether.graph.DependencyNode> aggDepNodeList,
        org.eclipse.aether.graph.DependencyNode rootNode) throws MojoExecutionException
  {
    // First remove duplicates in nodeList
    aggDepNodeList.removeAll(nodeList);

    aggDepNodeList.addAll(nodeList);
    
    Set<org.eclipse.aether.graph.DependencyNode> NodeChildList = 
        new LinkedHashSet<org.eclipse.aether.graph.DependencyNode>();
    
    for (org.eclipse.aether.graph.DependencyNode node : nodeList) {
      if (nodeArtifactsMatch(rootNode, node)){
        throw new MojoExecutionException("Circular dependency detected in project: " + getMavenProject().toString());
      }
      if ( (node != null) && (node.getChildren() != null) ) {
        NodeChildList.addAll(node.getChildren());
      }
    }

    return NodeChildList;
  }

  /**
   * Convenience function for constructing a String representing an artifact in the form "<groupId>:<artifactId>"
   * 
   * @param artifact {@link org.eclipse.aether.Artifact artifact} to construct string from
   * @return {@link String} in the form "<groupId>:<artifactId>" representing the artifact
   * @since 3.5.3
   */
  private String createArtifactString(Artifact artifact)
  {
    return new String(artifact.getGroupId() + ":" + artifact.getArtifactId());
  }

  /**
   * Convenience function for determining if the 2 specified dependency
   * nodes' artifacts match (i.e. if their group and artifact ids match)
   * 
   * @param nodeA {@link org.eclipse.aether.graph.DependencyNode dependency node} to match against
   * @param nodeB {@link org.eclipse.aether.graph.DependencyNode dependency node} to match against
   * @return {@code true} if the groupId and artifactId of the provided DependencyNode objects match, {@code false} otherwise.
   * @since 3.5.2
   */

  private boolean nodeArtifactsMatch (DependencyNode nodeA,
                                      DependencyNode nodeB)
  {
    if (nodeA == null || nodeB == null) {
      return false;
    }
      
    return nodeA.getArtifact().getGroupId ().equals(nodeB.getArtifact().getGroupId()) && 
        nodeA.getArtifact().getArtifactId ().equals(nodeB.getArtifact().getArtifactId());
  }

  //idea from dependency:tree mojo
  /**
   * Get root node of the current Maven project Dependency tree generated by
   * maven.shared dependency graph builder.
   *
   * @return root node of the project Dependency tree
   * @throws MojoExecutionException
   */
  /*
  protected DependencyNode getRootNodeDependecyTree() throws MojoExecutionException{
    try {
      ArtifactFilter artifactFilter = null;

      // works for only maven 3. Use of dependency graph component not handled for maven 2
      // as current version of NAR already requires Maven 3.x
      ProjectBuildingRequest buildingRequest =
                new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
      buildingRequest.setProject(getMavenProject());

      rootNode = dependencyGraphBuilder.buildDependencyGraph(buildingRequest, artifactFilter);

    } catch (DependencyGraphBuilderException exception) {
      throw new MojoExecutionException("Cannot build project dependency graph", exception);
    }

    return rootNode;
  }*/

  /**
   * To look up Archiver/UnArchiver implementations
   */
  @Component(role = org.codehaus.plexus.archiver.manager.ArchiverManager.class)
  protected ArchiverManager archiverManager;

  public final void downloadAttachedNars(final List<Artifact> dependencies)
      throws MojoExecutionException, MojoFailureException {
    getLog().debug("Download for NarDependencies {");
    for (final Artifact attachedNarArtifact : dependencies) {
      getLog().debug("  - " + attachedNarArtifact);
    }
    getLog().debug("}");

    for (final Artifact attachedNarArtifact : dependencies) {
      try {
        getLog().debug("Resolving " + attachedNarArtifact);
        ArtifactRequest ar = new ArtifactRequest(attachedNarArtifact, projectRepos, null);
        ArtifactResult res = repoSystem.resolveArtifact(repoSession, ar);
        if (res.getArtifact() == null) {
          final String message = "nar not found " + attachedNarArtifact;
          throw new MojoExecutionException(message);
        }
      } catch (final ArtifactResolutionException e) {
        final String message = "nar cannot resolve " + attachedNarArtifact;
        throw new MojoExecutionException(message, e);
      }
    }
  }

  public final List<Artifact> getAllAttachedNarArtifacts(final List<NarArtifact> narArtifacts,
      List<? extends Executable> libraries) throws MojoExecutionException, MojoFailureException {
    final List<Artifact> artifactList = new ArrayList<>();
    for (NarArtifact dependency : narArtifacts) {
      if ("NAR".equalsIgnoreCase(getMavenProject().getPackaging())) {
        final List<String> bindings = getBindings(libraries, dependency);

        // TODO: dependency.getFile(); find out what the stored pom says
        // about this - what nars should exist, what layout are they
        // using...
        for (final String binding : bindings) {
          artifactList.addAll(getAttachedNarArtifacts(dependency, /* library. */
              getAOL(), binding));
        }
      } else {
        artifactList.addAll(getAttachedNarArtifacts(dependency, getAOL(), Library.EXECUTABLE));
        artifactList.addAll(getAttachedNarArtifacts(dependency, getAOL(), Library.SHARED));
        artifactList.addAll(getAttachedNarArtifacts(dependency, getAOL(), Library.JNI));
        artifactList.addAll(getAttachedNarArtifacts(dependency, getAOL(), Library.STATIC));
      }
      artifactList.addAll(getAttachedNarArtifacts(dependency, null, NarConstants.NAR_NO_ARCH));
    }
    return artifactList;
  }

  protected final ArchiverManager getArchiverManager() {
    return this.archiverManager;
  }

  /**
   * Returns the artifacts which must be taken in account for the Mojo.
   * 
   * @return Artifacts
   */
  protected abstract ScopeFilter getArtifactScopeFilter();

  /**
   * Returns the attached NAR Artifacts (AOL and noarch artifacts) from the NAR
   * dependencies artifacts of the project.
   * The artifacts which will be processed are those returned by the method
   * getArtifacts() which must be implemented
   * in each class which extends AbstractDependencyMojo.
   * 
   * @return Attached NAR Artifacts
   * @throws MojoFailureException
   * @throws MojoExecutionException
   * 
   * @see getArtifacts
   */
  protected List<Artifact> getAttachedNarArtifacts(List<? extends Executable> libraries)
      throws MojoFailureException, MojoExecutionException {
    getLog().info("Getting Nar dependencies");
    final List<NarArtifact> narArtifacts = getNarArtifacts();
    final List<Artifact> attachedNarArtifacts = getAllAttachedNarArtifacts(narArtifacts, libraries);
    return attachedNarArtifacts;
  }

  private List<Artifact> getAttachedNarArtifacts(
      final NarArtifact dependency,
      final AOL aol,
      final String type) throws MojoExecutionException, MojoFailureException {

    getLog().debug("GetNarDependencies for " + dependency + ", aol: " + aol + ", type: " + type);
    final NarInfo narInfo = dependency.getNarInfo();
    final AOL aolString = narInfo.getAOL(aol);
    // FIXME Move this to NarInfo....
    List<ArtifactRequest> req = narInfo.getAttachedNars(aol, type).stream()
        .peek(dep -> getLog().debug("    Checking: " + dep))
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
        .map(dep -> new ArtifactRequest(new DefaultArtifact(dep), projectRepos, null))
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

  protected List<String> getBindings(List<? extends Executable> libraries, NarArtifact dependency)
      throws MojoFailureException, MojoExecutionException {


    List<String> bindings = Stream.ofNullable(libraries)
        .flatMap(List::stream)
        .map(lib -> ((Executable)lib).getBinding(dependency))
        .filter(Objects::nonNull)
        .distinct()
        .collect(Collectors.toList());

    // - if it is specified but the atrifact is not available should fail.
    // otherwise
    // how does the artifact specify it should be used by default
    // -
    // whats the preference for this type of library to use (shared - shared,
    // static - static...)

    // library.getType()
    if (bindings.isEmpty())
      bindings.add(dependency.getNarInfo().getBinding(getAOL(), Library.STATIC));

    return Collections.unmodifiableList(bindings);
  }

  protected String getBinding(Executable exec, NarArtifact dependency)
      throws MojoFailureException, MojoExecutionException {

    // how does this project specify the dependency is used
    String binding = exec.getBinding(dependency);

    // - if it is specified but the atrifact is not available should fail.
    // otherwise
    // how does the artifact specify it should be used by default
    // -
    // whats the preference for this type of library to use (shared - shared,
    // static - static...)

    // library.getType()
    if (binding == null)
      binding = dependency.getNarInfo().getBinding(getAOL(), Library.STATIC);

    return binding;
  }

  /**
   * Returns dependencies which are dependent on NAR files (i.e. contain
   * NarInfo)
   */
  public final List<NarArtifact> getNarArtifacts() throws MojoExecutionException {
    if (!narDependencies.isEmpty()) {
      return narDependencies;
    }

    FilterArtifacts filter = new FilterArtifacts();
    filter.addFilter(new GroupIdFilter(cleanToBeTokenizedString(this.includeGroupIds),
        cleanToBeTokenizedString(this.excludeGroupIds)));
    
    filter.addFilter(new ArtifactIdFilter(cleanToBeTokenizedString(this.includeArtifactIds),
        cleanToBeTokenizedString(this.excludeArtifactIds)));

    filter.addFilter(getArtifactScopeFilter());
    
    final Set<ArtifactRequest> dependencies;
    try {
      dependencies = filter.filter(getMavenProject().getArtifacts()).stream()
        .map(a -> new ArtifactRequest(new DefaultArtifact(a.getId()), projectRepos, null))
        .collect(Collectors.toSet());
    } catch (ArtifactFilterException e) {
      throw new MojoExecutionException(e.getMessage(), e);
    }
    
    List<ArtifactResult> results = null;
    try {
      results = repoSystem.resolveArtifacts(repoSession, dependencies);
    } catch(ArtifactResolutionException e) {
      throw new MojoExecutionException("Could resolve artifacts", e);
    }

    for (final ArtifactResult element : results) {
      final Artifact artifact = element.getArtifact();

      if ("nar".equalsIgnoreCase(artifact.getExtension())) {
        getLog().debug("Examining artifact for NarInfo: " + artifact);

        final NarInfo narInfo = getNarInfo(artifact);
        if (narInfo != null) {
          getLog().debug("    - added as NarDependency");
          narDependencies.add(new NarArtifact(artifact, narInfo));
        }
      }
    }
    getLog().debug("Dependencies contained " + narDependencies.size() + " NAR artifacts.");
    return narDependencies;
  }

  public final NarInfo getNarInfo(final Artifact dependency) throws MojoExecutionException {

    if (Files.isDirectory(dependency.getFile().toPath())) {
      getLog().debug("Dependency is not packaged: " + dependency.getPath());

      return new NarInfo(dependency.getGroupId(), dependency.getArtifactId(), dependency.getBaseVersion(), getLog(),
          dependency.getPath());
    }

    if (!Files.exists(dependency.getFile().toPath())) {
      getLog().debug("Dependency nar file does not exist: " + dependency.getPath());
      return null;  
    }

    try(ZipInputStream zipStream = new ZipInputStream(new FileInputStream(dependency.getFile()))) {
      if (zipStream.getNextEntry() == null) {
        getLog().debug("Skipping unreadable artifact: " + dependency.getFile().getPath());
        return null;
      }
    } catch (IOException e) {
      throw new MojoExecutionException("Error while testing for zip file " + dependency.getPath(), e);
    }

    try (JarFile jar = new JarFile(dependency.getFile())) {
      final NarInfo info = new NarInfo(dependency.getGroupId(), dependency.getArtifactId(),
          dependency.getBaseVersion(), getLog());
      if (!info.exists(jar)) {
        getLog().debug("Dependency nar file does not contain this artifact: " + dependency.getPath());
        return null;
      }
      info.read(jar);
      return info;
    } catch (final IOException e) {
      throw new MojoExecutionException("Error while reading " + dependency.getPath(), e);
    }
  }

  protected final NarManager getNarManager() throws MojoFailureException, MojoExecutionException {
    return new NarManager(getLog(), repoSystem, repoSession, projectRepos, getMavenProject(), getArchitecture(), getOS(), getLinker());
  }

  protected final List<RemoteRepository> getRemoteRepositories() {
    return this.projectRepos;
  }

  public final void unpackAttachedNars(final List<Artifact> dependencies)
      throws MojoExecutionException, MojoFailureException {
    final Path unpackDir = getUnpackDirectory();

    getLog().info(String.format("Unpacking %1$d dependencies to %2$s", dependencies.size(), unpackDir));

    try {
      dependencies.stream().forEach(Failable.asConsumer(artifact -> {
        final Path file = artifact.getFile().toPath();
        getLog().debug(String.format("Unpack %1$s (%2$s) to %3$s", artifact, file, unpackDir));
        // TODO: each dependency may have it's own (earlier) version of layout -
        // if it is unknown then we should report an error to update the nar
        // package
        // NarLayout layout = AbstractNarLayout.getLayout( "NarLayout21"/* TODO:
        // dependency.getLayout() */, getLog() );
        // we should then target the layout to match the layout for this nar which
        // is the workspace we are in.
        final NarLayout layout = getLayout();
        // TODO: the dependency may be specified against a different linker
        // (version)?
        // AOL aol = dependency.getClassifier(); Trim
        layout.unpackNar(unpackDir, this.archiverManager, file, getOS(), getLinker().getName(), getAOL(), isSkipRanlib());
      }));
    } catch (RuntimeException e) {
      throw new MojoExecutionException(e);
    }
  }

  //
  // clean up configuration string before it can be tokenized
  //
  private static String cleanToBeTokenizedString(String str) {
    String ret = "";
    if (!StringUtils.isEmpty(str)) {
      // remove initial and ending spaces, plus all spaces next to commas
      ret = str.trim().replaceAll("[\\s]*,[\\s]*", ",");
    }

    return ret;
  }

  protected Path getIncludePath(NarArtifact artifact) throws MojoExecutionException, MojoFailureException {

    return getLayout().getIncludeDirectory(getUnpackDirectory(),
        artifact.getArtifactId(), artifact.getVersion());
  }

  protected Path getLibraryPath(NarArtifact artifact) throws MojoExecutionException, MojoFailureException {

    return getLayout().getLibDirectory(getUnpackDirectory(),
        artifact.getArtifactId(), artifact.getVersion(),
        getAOL().toString(),
        artifact.getNarInfo().getBinding(getAOL(), Library.SHARED));
  }

  protected String getLinkName(String libName) throws MojoFailureException, MojoExecutionException {
    String linkName = libName;
    if (getAOL().getOS() == OS.WINDOWS) {
      linkName = libName + ".lib";
    }
    return linkName;
  }

}
