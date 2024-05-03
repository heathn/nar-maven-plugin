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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.surefire.AbstractSurefireMojo;
import org.apache.maven.plugin.surefire.Summary;
import org.apache.maven.plugin.surefire.SurefireHelper;
import org.apache.maven.plugin.surefire.SurefireReportParameters;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.surefire.suite.RunResult;
import org.codehaus.plexus.util.StringUtils;

/**
 * Run integration tests using Surefire. This goal was copied from Maven's
 * surefire plugin to accomodate a few things
 * for the NAR plugin:
 * <P>
 * 1. To test a jar file with its native module we can only run after the
 * package phase, so we use the integration-test phase.
 * </P>
 * <P>
 * 2. We need to set java.library.path to an AOL (architecture-os-linker)
 * specific value, but AOL is only known in the NAR plugin and thus cannot be
 * set from the pom.
 * </P>
 * <P>
 * 3. To have the java.library.path definition picked up by java we need the
 * "pertest" forkmode. To use this goal you need to put the test sources in the
 * regular test directories but disable the running of the tests by the
 * maven-surefire-plugin by setting maven.test.skip.exec to false in your pom.
 * </P>
 *
 * @author Jason van Zyl (modified by Mark Donszelmann, noted by DUNS)
 * @version $Id: SurefirePlugin.java 652773 2008-05-02 05:58:54Z dfabulich $
 *          Mods by Duns for NAR
 */
@Mojo(name = "nar-integration-test",
    defaultPhase = LifecyclePhase.INTEGRATION_TEST, threadSafe = true,
    requiresDependencyResolution = ResolutionScope.TEST)
public class NarIntegrationTestMojo extends AbstractSurefireMojo
    implements SurefireReportParameters {
  // Copied from AbstractNarMojo
  /**
   * Layout to be used for building and unpacking artifacts
   * 
   */
  @Parameter(property = "nar.layout", defaultValue = "com.github.maven_nar.NarLayout21", required = true)
  private String layout;

  private NarLayout narLayout;

  private final NarLayout getLayout() throws MojoExecutionException {
    if (narLayout == null) {
      narLayout = AbstractNarLayout.getLayout(layout, getLog());
    }
    return narLayout;
  }

  /**
   * The Architecture for the nar, Some choices are: "x86", "i386", "amd64", "ppc", "sparc", ... Defaults to a derived
   * value from ${os.arch}
   * 
   */
  @Parameter(property = "os.arch", required = true)
  private String architecture;

  /**
   * The Operating System for the nar. Some choices are: "Windows", "Linux", "MacOSX", "SunOS", ... Defaults to a
   * derived value from ${os.name} FIXME table missing
   * 
   */
  @Parameter
  private String os;

  private String getOS() {
    return os;
  }

  /**
   * Linker
   * 
   */
  @Parameter
  private Linker linker;

  /**
   * Architecture-OS-Linker name. Defaults to: arch-os-linker.
   * 
   */
  @Parameter
  private String aol;

  private AOL aolId;

  private final AOL getAOL() throws MojoFailureException, MojoExecutionException {
    return aolId;
  }

  /**
   * Target directory for Nar file construction.
   */
  @Parameter(defaultValue = "${project.build.directory}/nar")
  private File targetDirectory;

  protected final File getTargetDirectory() {
    return targetDirectory;
  }

  /**
   * Target directory for Nar file unpacking.
   */
  @Parameter(defaultValue = "${project.build.directory}/nar")
  private File unpackDirectory;

  private final File getUnpackDirectory() {
    return unpackDirectory;
  }

  // Copied from AbstractDependencyMojo
  private final NarManager getNarManager() throws MojoFailureException, MojoExecutionException {
    return new NarManager(getLog(), getLocalRepository(), getProject(), architecture, os, linker);
  }

  // Copied from AbstractCompileMojo
  /**
   * List of libraries to create
   * 
   */
  @Parameter
  private List<Library> libraries;

  private final List<Library> getLibraries() {
    if (libraries == null) {
      libraries = Collections.emptyList();
    }
    return libraries;
  }

  // DUNS added test for JNI module
  private boolean testJNIModule() {
    for (Iterator<Library> i = getLibraries().iterator(); i.hasNext();) {
      Library lib = i.next();
      if (lib.getType().equals(Library.JNI)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Set this to 'true' to skip running tests, but still compile them. Its use is NOT RECOMMENDED, but quite
   * convenient on occasion.
   * 
   * @since 2.4
   */
  @Parameter(property = "skipNarTests")
  private boolean skipNarTests;

  /**
   * DEPRECATED This old parameter is just like skipTests, but bound to the old property maven.test.skip.exec. Use
   * -DskipTests instead; it's shorter.
   * 
   * @deprecated
   * @since 2.3
   */
  @Parameter(property = "nar.test.skip.exec")
  private boolean skipNarExec;

  /**
   * Skip running of NAR integration test plugin
   * 
   */
  @Parameter(property = "skipNar", defaultValue = "false")
  private boolean skipNar;

  /**
   * Set this to true to ignore a failure during testing. Its use is NOT RECOMMENDED, but quite convenient on
   * occasion.
   * 
   */
  @Parameter(property = "nar.test.failure.ignore", defaultValue = "false")
  private boolean testFailureIgnore;

  /**
   * Base directory where all reports are written to.
   * 
   */
  @Parameter(defaultValue = "${project.build.directory}/surefire-reports")
  private File reportsDirectory;

  /**
   * Specify this parameter to run individual tests by file name, overriding the <code>includes/excludes</code>
   * parameters. Each pattern you specify here will be used to create an include pattern formatted like
   * <code>**&#47;${test}.java</code>, so you can just type "-Dtest=MyTest" to run a single test called
   * "foo/MyTest.java". This parameter will override the TestNG suiteXmlFiles parameter.
   * 
   */
  @Parameter(property = "test")
  private String test;

  /**
   * Set this to "true" to cause a failure if the none of the tests specified in -Dtest=... are run. Defaults to
   * "true".
   * 
   * @since 2.12
   */
  @Parameter(property = "surefire.failIfNoSpecifiedTests")
  private Boolean failIfNoSpecifiedTests;

  /**
   * Option to print summary of test suites or just print the test cases that has errors.
   * 
   */
  @Parameter(property = "surefire.printSummary", defaultValue = "true")
  private boolean printSummary;

  /**
   * Selects the formatting for the test report to be generated. Can be set as brief or plain.
   * 
   */
  @Parameter(property = "surefire.reportFormat", defaultValue = "brief")
  private String reportFormat;

  /**
   * Option to generate a file test report or just output the test report to the console.
   * 
   */
  @Parameter(property = "surefire.useFile", defaultValue = "true")
  private boolean useFile;

  /**
   * Set this to "true" to redirect the unit test standard output to a file (found in
   * reportsDirectory/testName-output.txt).
   * 
   * @since 2.3
   */
  @Parameter(property = "nar.test.redirectTestOutputToFile", defaultValue = "false")
  private boolean redirectTestOutputToFile;

  /**
   * Attach a debugger to the forked JVM. If set to "true", the process will suspend and wait for a debugger to attach
   * on port 5005. If set to some other string, that string will be appended to the argLine, allowing you to configure
   * arbitrary debuggability options (without overwriting the other options specified in the argLine).
   * 
   * @since 2.4
   */
  @Parameter(property = "maven.surefire.debug")
  private String debugForkedProcess;

  /**
   * Kill the forked test process after a certain number of seconds. If set to 0, wait forever for the process, never
   * timing out.
   * 
   * @since 2.4
   */
  @Parameter(property = "surefire.timeout")
  private int forkedProcessTimeoutInSeconds;

  /**
   * Option to pass dependencies to the system's classloader instead of using an isolated class loader when forking.
   * Prevents problems with JDKs which implement the service provider lookup mechanism by using the system's
   * classloader. Default value is "true".
   * 
   * @since 2.3
   */
  @Parameter(property = "surefire.useSystemClassLoader", defaultValue = "true")
  private Boolean useSystemClassLoader;

  /**
   * By default, Surefire forks your tests using a manifest-only jar; set this parameter to "false" to force it to
   * launch your tests with a plain old Java classpath. (See
   * http://maven.apache.org/plugins/maven-surefire-plugin/examples/class-loading.html for a more detailed explanation
   * of manifest-only jars and their benefits.) Default value is "true". Beware, setting this to "false" may cause
   * your tests to fail on Windows if your classpath is too long.
   * 
   * @since 2.4.3
   */
  @Parameter(property = "surefire.useManifestOnlyJar", defaultValue = "true")
  private boolean useManifestOnlyJar;

  @Parameter(defaultValue = "${project}", readonly = true)
  private MavenProject mavenProject;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    linker = NarUtil.getLinker(linker, getLog());

    architecture = NarUtil.getArchitecture(architecture);
    os = NarUtil.getOS(os);
    aolId = NarUtil.getAOL(mavenProject, architecture, os, linker, aol, getLog());

    if (project.getPackaging().equals( "nar" ) || ( getNarManager().getNarDependencies("test").size() > 0)) {
      setForkMode("pertest");
    }

    if (argLine == null) {
      argLine = "";
    }

    StringBuffer javaLibraryPath = new StringBuffer();
    if (testJNIModule()) {
      // Add libraries to java.library.path for testing
      File jniLibraryPathEntry = getLayout().getLibDirectory(
          getTargetDirectory(), getProject().getArtifactId(),
          getProject().getVersion(), getAOL().toString(), Library.JNI);
      if (jniLibraryPathEntry.exists()) {
        getLog().debug("Adding library directory to java.library.path: " + jniLibraryPathEntry);
        if (javaLibraryPath.length() > 0) {
          javaLibraryPath.append(File.pathSeparator);
        }
        javaLibraryPath.append( jniLibraryPathEntry );
      }

      File sharedLibraryPathEntry = getLayout().getLibDirectory(
          getTargetDirectory(), getProject().getArtifactId(),
          getProject().getVersion(), getAOL().toString(), Library.SHARED);
      if (sharedLibraryPathEntry.exists()) {
          getLog().debug("Adding library directory to java.library.path: " + sharedLibraryPathEntry);
        if (javaLibraryPath.length() > 0) {
            javaLibraryPath.append(File.pathSeparator);
         }
        javaLibraryPath.append(sharedLibraryPathEntry);
      }

      // add jar file to classpath, as one may want to read a
      // properties file for artifactId and version
      String narFile = "target/" + project.getArtifactId() + "-" + project.getVersion() + ".jar";
      getLog().debug("Adding to surefire test classpath: " + narFile);
      setAdditionalClasspathElements(Collections.singletonList(narFile));
    }

    List<NarArtifact> dependencies = getNarManager().getNarDependencies("compile");
    for (NarArtifact dependency : dependencies) {
      // FIXME this should be overridable
      // NarInfo info = dependency.getNarInfo();
      // String binding = info.getBinding(getAOL(), Library.STATIC);
      // NOTE: fixed to shared, jni
      String[] bindings = { Library.SHARED, Library.JNI };
      for (int j = 0; j < bindings.length; j++) {
        String binding = bindings[j];
        if (!binding.equals(Library.STATIC)) {
          File depLibPathEntry = getLayout().getLibDirectory(
              getUnpackDirectory(), dependency.getArtifactId(),
              dependency.getVersion(), getAOL().toString(), binding);
          if (depLibPathEntry.exists()) {
            getLog().debug("Adding dependency directory to java.library.path: " + depLibPathEntry);
            if (javaLibraryPath.length() > 0) {
              javaLibraryPath.append(File.pathSeparator);
            }
            javaLibraryPath.append(depLibPathEntry);
          }
        }
      }
    }

    // add final javalibrary path
    if (javaLibraryPath.length() > 0) {
      // NOTE java.library.path only works for the jni lib itself, and
      // not for its dependent shareables.
      // NOTE: java.library.path does not work with arguments with
      // spaces as
      // SureFireBooter splits the line in parts and then quotes
      // it wrongly
      NarUtil.addLibraryPathToEnv(javaLibraryPath.toString(), environmentVariables, getOS());
    }

    // necessary to find WinSxS
    if (getOS().equals(OS.WINDOWS)) {
      environmentVariables.put("SystemRoot", NarUtil.getEnv("SystemRoot", "SystemRoot", "C:\\Windows"));
    }
    super.execute();
  }

  @Override
  protected void handleSummary(Summary summary) throws MojoExecutionException, MojoFailureException {
    assertNoException(summary);
    assertNoFailureOrTimeout(summary);
    writeSummary(summary);
  }

  private void assertNoException(Summary summary) throws MojoExecutionException {
    if (!summary.isErrorFree()) {
      Exception cause = summary.getFirstException();
      throw new MojoExecutionException(cause.getMessage(), cause);
    }
  }

  private void assertNoFailureOrTimeout(Summary summary) throws MojoExecutionException {
    if (summary.isFailureOrTimeout()) {
      throw new MojoExecutionException("Failure or timeout");
    }
  }

  private void writeSummary(Summary summary) throws MojoFailureException {
    RunResult result = summary.getResultOfLastSuccessfulRun();
    SurefireHelper.reportExecution(this, result, getLog());
  }

  @Override
  protected boolean isSkipExecution() {
    return skipNar || skipNarTests || skipNarExec;
  }

  @Override
  public String getPluginName() {
    return "nar";
  }

  @Override
  protected String[] getDefaultIncludes() {
    return new String[]{ "**/Test*.java", "**/*Test.java", "**/*TestCase.java" };
  }

  @Override
  public boolean isSkipTests() {
    return skipTests;
  }

  @Override
  public void setSkipTests(boolean skipTests) {
    this.skipTests = skipTests;
  }

  /**
   * @return SurefirePlugin Returns the skipExec.
   */
  @Override
  public boolean isSkipExec() {
    return this.skipNarTests;
  }

  /**
   * @param skipExec the skipExec to set
   */
  @Override
  public void setSkipExec(boolean skipExec) {
    this.skipNarTests = skipExec;
  }

  @Override
  public boolean isSkip() {
    return skip;
  }

  @Override
  public void setSkip(boolean skip) {
    this.skip = skip;
  }

  @Override
  public boolean isTestFailureIgnore() {
    return testFailureIgnore;
  }

  @Override
  public void setTestFailureIgnore(boolean testFailureIgnore) {
    this.testFailureIgnore = testFailureIgnore;
  }

  @Override
  public File getBasedir() {
    return basedir;
  }

  @Override
  public void setBasedir(File basedir) {
    this.basedir = basedir;
  }

  @Override
  public File getTestClassesDirectory() {
    return testClassesDirectory;
  }

  @Override
  public void setTestClassesDirectory(File testClassesDirectory) {
    this.testClassesDirectory = testClassesDirectory;
  }

  @Override
  public File getClassesDirectory() {
    return classesDirectory;
  }

  @Override
  public void setClassesDirectory(File classesDirectory) {
    this.classesDirectory = classesDirectory;
  }

  @Override
  public List<String> getClasspathDependencyExcludes() {
    return classpathDependencyExcludes;
  }

  @Override
  public void setClasspathDependencyExcludes(List<String> classpathDependencyExcludes) {
    this.classpathDependencyExcludes = classpathDependencyExcludes;
  }

  @Override
  public String getClasspathDependencyScopeExclude() {
    return classpathDependencyScopeExclude;
  }

  @Override
  public void setClasspathDependencyScopeExclude(String classpathDependencyScopeExclude) {
    this.classpathDependencyScopeExclude = classpathDependencyScopeExclude;
  }

  @Override
  public List<String> getAdditionalClasspathElements() {
    return additionalClasspathElements;
  }

  @Override
  public void setAdditionalClasspathElements(List<String> additionalClasspathElements) {
    this.additionalClasspathElements = additionalClasspathElements;
  }

  @Override
  public File getReportsDirectory() {
    return reportsDirectory;
  }

  @Override
  public void setReportsDirectory(File reportsDirectory) {
    this.reportsDirectory = reportsDirectory;
  }

  @Override
  public String getTest() {
    if (StringUtils.isBlank(test)) {
      return null;
    }
    String[] testArray = StringUtils.split(test, ",");
    StringBuilder tests = new StringBuilder();
    for (String aTestArray : testArray) {
      String singleTest = aTestArray;
      int index = singleTest.indexOf('#');
      if (index >= 0) {
        // the way version 2.7.3.  support single test method
        singleTest = singleTest.substring(0, index);
      }
      tests.append(singleTest);
      tests.append(",");
    }
    return tests.toString();
  }

  /**
   * @since 2.7.3
   */
  @Override
  public String getTestMethod() {
    if (StringUtils.isBlank(test)) {
      return null;
    }
    //modified by rainLee, see http://jira.codehaus.org/browse/SUREFIRE-745
    int index = this.test.indexOf('#');
    int index2 = this.test.indexOf(",");
    if (index >= 0) {
      if (index2 < 0) {
        String testStrAfterFirstSharp = this.test.substring(index + 1, this.test.length());
        if (!testStrAfterFirstSharp.contains("+")) {
          //the original way
          return testStrAfterFirstSharp;
        } else {
          return this.test;
        }
      } else {
        return this.test;
      }
    }
    return null;
  }

  @Override
  public boolean isUseSystemClassLoader() {
    return useSystemClassLoader;
  }

  @Override
  public void setUseSystemClassLoader(boolean useSystemClassLoader) {
    this.useSystemClassLoader = useSystemClassLoader;
  }

  @Override
  public boolean isUseManifestOnlyJar() {
    return useManifestOnlyJar;
  }

  @Override
  public void setUseManifestOnlyJar(boolean useManifestOnlyJar) {
    this.useManifestOnlyJar = useManifestOnlyJar;
  }

  @Override
  public Boolean getFailIfNoSpecifiedTests() {
    return failIfNoSpecifiedTests;
  }

  @Override
  public void setFailIfNoSpecifiedTests(Boolean failIfNoSpecifiedTests) {
    this.failIfNoSpecifiedTests = failIfNoSpecifiedTests;
  }

  @Override
  public boolean isPrintSummary() {
    return printSummary;
  }

  @Override
  public void setPrintSummary(boolean printSummary) {
    this.printSummary = printSummary;
  }

  @Override
  public String getReportFormat() {
    return reportFormat;
  }

  @Override
  public void setReportFormat(String reportFormat) {
    this.reportFormat = reportFormat;
  }

  @Override
  public boolean isUseFile() {
    return useFile;
  }

  @Override
  public void setUseFile(boolean useFile) {
    this.useFile = useFile;
 }

  @Override
  public String getDebugForkedProcess() {
    return debugForkedProcess;
  }

  @Override
  public void setDebugForkedProcess(String debugForkedProcess) {
    this.debugForkedProcess = debugForkedProcess;
  }

  @Override
  public int getForkedProcessTimeoutInSeconds() {
    return forkedProcessTimeoutInSeconds;
  }

  @Override
  public void setForkedProcessTimeoutInSeconds(int forkedProcessTimeoutInSeconds) {
    this.forkedProcessTimeoutInSeconds = forkedProcessTimeoutInSeconds;
  }

  @Override
  public void setTest(String test) {
    this.test = test;
  }

  @Override
  public boolean isRedirectTestOutputToFile() {
    return redirectTestOutputToFile;
  }

  @Override
  public void setRedirectTestOutputToFile(boolean redirectTestOutputToFile) {
    this.redirectTestOutputToFile = redirectTestOutputToFile;
  }
}
