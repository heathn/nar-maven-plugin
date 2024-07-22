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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.JavaClass;
import org.apache.maven.model.Profile;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.surefire.shared.lang3.function.Failable;

import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.cli.Commandline;

/**
 * @author Mark Donszelmann
 */
public final class NarUtil {
  private static final class StreamGobbler extends Thread {
    private final InputStream is;

    private final TextStream ts;

    private StreamGobbler(final InputStream is, final TextStream ts) {
      this.is = is;
      this.ts = ts;
    }

    @Override
    public void run() {
      try {
        final BufferedReader reader = new BufferedReader(new InputStreamReader(this.is));
        String line = null;
        while ((line = reader.readLine()) != null) {
          this.ts.println(line);
        }
        reader.close();
      } catch (final IOException e) {
        // e.printStackTrace()
        final StackTraceElement[] stackTrace = e.getStackTrace();
        for (final StackTraceElement element : stackTrace) {
          this.ts.println(element.toString());
        }
      }
    }
  }

  public static final String DEFAULT_EXCLUDES = "**/*~,**/#*#,**/.#*,**/%*%,**/._*,"
      + "**/CVS,**/CVS/**,**/.cvsignore," + "**/SCCS,**/SCCS/**,**/vssver.scc," + "**/.svn,**/.svn/**,**/.DS_Store";

  public static String addLibraryPathToEnv(final String path, final Map<String, String> environment, final String os) {
    String pathName = null;
    char separator = ' ';
    switch (os) {
      case OS.WINDOWS:
        pathName = "PATH";
        separator = ';';
        break;
      case OS.MACOSX:
        pathName = "DYLD_LIBRARY_PATH";
        separator = ':';
        break;
      case OS.AIX:
        pathName = "LIBPATH";
        separator = ':';
        break;
      default:
        pathName = "LD_LIBRARY_PATH";
        separator = ':';
        break;
    }

    String value = environment != null ? environment.get(pathName) : null;
    if (value == null) {
      value = NarUtil.getEnv(pathName, pathName, null);
    }

    String libPath = path;
    libPath = libPath.replace(File.pathSeparatorChar, separator);
    if (value != null) {
      value = libPath + separator + value; // items under test first on path
    } else {
      value = libPath;
    }
    if (environment != null) {
      environment.put(pathName, value);
    }
    return pathName + "=" + value;
  }

  /**
   * (Darren) this code lifted from mvn help:active-profiles plugin Recurses
   * into the project's parent poms to find the active profiles of the
   * specified project and all its parents.
   * 
   * @param project
   *          The project to start with
   * @return A list of active profiles
   */
  static List<Profile> collectActiveProfiles(final MavenProject project) {
    final List<Profile> profiles = project.getActiveProfiles();

    if (project.hasParent()) {
      profiles.addAll(collectActiveProfiles(project.getParent()));
    }

    return profiles;
  }

  public static int copyDirectoryStructure(final Path sourceDirectory, final Path destinationDirectory,
      final String includes, final String excludes) throws IOException {
    if (Files.notExists(sourceDirectory)) {
      throw new IOException("Source directory doesn't exists (" + sourceDirectory.toAbsolutePath() + ").");
    }

    // Call copyDirectoryLayout first to make sure any empty directories
    // are included when copying the directory tree.
    final String[] inc = includes == null ? null : includes.split(",");
    final String[] exc = excludes == null ? null : excludes.split(",");
    FileUtils.copyDirectoryLayout(sourceDirectory.toFile(), destinationDirectory.toFile(), inc, exc);
    List<File> files = FileUtils.getFiles(sourceDirectory.toFile(), includes, excludes);
    final String sourcePath = sourceDirectory.toAbsolutePath().toString();

    int copied = 0;
    for (final File file1 : files) {
      final Path file = file1.toPath();
      String dest = file.toAbsolutePath().toString();
      dest = dest.substring(sourcePath.length() + 1);
      final Path destination = destinationDirectory.resolve(dest);
      if (Files.isRegularFile(file)) {
        // destination = destination.getParentFile();
        // use FileUtils from commons-io, because it preserves timestamps
        org.apache.commons.io.FileUtils.copyFile(file.toFile(), destination.toFile());
        copied++;

        // copy executable bit
        try {
          // 1.6 only so coded using introspection
          // destination.setExecutable( file.canExecute(), false );
          final Method canExecute = file.getClass().getDeclaredMethod("canExecute");
          final Method setExecutable = destination.getClass()
              .getDeclaredMethod("setExecutable", boolean.class, boolean.class);
          setExecutable
              .invoke(destination, canExecute.invoke(file), Boolean.FALSE);
        } catch (final SecurityException | InvocationTargetException | IllegalAccessException | IllegalArgumentException | NoSuchMethodException e) {
          // ignored
        }
      } else if (Files.isDirectory(file)) {
        if (Files.notExists(destination)) {
          try {
            Files.createDirectories(destination);
          } catch (IOException e) {
            throw new IOException("Could not create destination directory '" + destination.toAbsolutePath() + "'.", e);
          }
        }
        copied += copyDirectoryStructure(file, destination, includes, excludes);
      } else {
        throw new IOException("Unknown file type: " + file.toAbsolutePath());
      }
    }
    return copied;
  }

  public static void deleteDirectory(final Path dir) throws MojoExecutionException {
    try {
      try (Stream<Path> pathStream = Files.walk(dir)) {
        pathStream.sorted(Comparator.reverseOrder())
          .map(Path::toFile)
          .forEach(File::delete);
      }
    } catch (IOException e) {
      throw new MojoExecutionException(e);
    }
  }
  
  static Set<Path> findInstallNameToolCandidates(final Path[] files, final Log log)
      throws MojoExecutionException, MojoFailureException {
    final Set<Path> candidates = new HashSet<>();

    for (final Path file : files) {

      if (Files.notExists(file)) {
        continue;
      }

      if (Files.isDirectory(file)) {
        try {
          candidates.addAll(findInstallNameToolCandidates(Files.list(file).toArray(Path[]::new), log));
        } catch (IOException e) {
          throw new MojoExecutionException(e);
        }
      }

      if (Files.isRegularFile(file) && Files.isWritable(file)
          && (file.toString().endsWith(".so") || file.toString().endsWith(".dylib") || file.toString().endsWith(".jnilib"))) {
        candidates.add(file);
      }
    }

    return candidates;
  }

  // FIXME, should go to AOL.
  /*
   * NOT USED ?
   * public static String getAOLKey( String architecture, String os, Linker
   * linker )
   * throws MojoFailureException, MojoExecutionException
   * {
   * // construct AOL key prefix
   * return getArchitecture( architecture ) + "." + getOS( os ) + "." +
   * getLinkerName( architecture, os, linker )
   * + ".";
   * }
   */

  public static AOL getAOL(final MavenProject project, final String architecture, final String os, final Linker linker,
      final String aol, final Log log) throws MojoFailureException, MojoExecutionException {

    /*
    To support a linker that is not the default linker specified in the aol.properties
    * */
    String aol_linker;

    if(linker != null && linker.getName() != null)
    {
      log.debug("linker original name: " + linker.getName());
      aol_linker = linker.getName();
    }
    else
    {
      log.debug("linker original name not exist ");
      aol_linker = getLinkerName(project, architecture, os,linker, log);
    }
    log.debug("aol_linker: " + aol_linker);

    return aol == null ? new AOL(getArchitecture(architecture), getOS(os), aol_linker) : new AOL(aol);
  }

  public static String getAOLKey(final String aol) {
    // FIXME, this may not always work correctly
    return replace("-", ".", aol);
  }

  public static String getArchitecture(final String architecture) {
    if (architecture == null) {
      return System.getProperty("os.arch");
    }
    return architecture;
  }

  /**
   * Returns the Bcel Class corresponding to the given class filename
   * 
   * @param filename
   *          the absolute file name of the class
   * @return the Bcel Class.
   * @throws IOException
   */
  public static JavaClass getBcelClass(final String filename) throws IOException {
    final ClassParser parser = new ClassParser(filename);
    return parser.parse();
  }

  public static String getEnv(final String envKey, final String alternateSystemProperty, final String defaultValue) {
    String envValue = null;
    try {
      envValue = System.getenv(envKey);
      if (envValue == null && alternateSystemProperty != null) {
        envValue = System.getProperty(alternateSystemProperty);
      }
    } catch (final Error e) {
      // JDK 1.4?
      if (alternateSystemProperty != null) {
        envValue = System.getProperty(alternateSystemProperty);
      }
    }

    if (envValue == null) {
      envValue = defaultValue;
    }

    return envValue;
  }

  /**
   * Returns the header file name (javah) corresponding to the given class file
   * name
   * 
   * @param filename
   *          the absolute file name of the class
   * @return the header file name.
   */
  public static String getHeaderName(final String basename, final String filename) {
    final String base = basename.replaceAll("\\\\", "/");
    final String file = filename.replaceAll("\\\\", "/");
    if (!file.startsWith(base)) {
      throw new IllegalArgumentException("Error " + file + " does not start with " + base);
    }
    String header = file.substring(base.length() + 1);
    header = header.replaceAll("/", "_");
    header = header.replaceAll("\\.class", ".h");
    return header;
  }

  public static Path getJavaHome(final Path javaHome, final String os) {
    Path home = javaHome;
    // adjust JavaHome
    if (home == null) {
      home = Path.of(System.getProperty("java.home"));
      if (home.getFileName().toString().equals("jre")) {
        // we want the JDK base directory, not the JRE subfolder
        home = home.getParent();
      }
    }
    return home;
  }

  public static Linker getLinker(final Linker linker, final Log log) {
    Linker link = linker;
    if (link == null) {
      link = new Linker(log);
    }
    return link;
  }

  public static String getLinkerName(final MavenProject project, final String architecture, final String os,
      final Linker linker, final Log log) throws MojoFailureException, MojoExecutionException {
    return getLinker(linker, log).getName(NarProperties.getInstance(project),
        getArchitecture(architecture) + "." + getOS(os) + ".");
  }

  public static String getOS(final String defaultOs) {
    String os = defaultOs;
    // adjust OS if not given
    if (os == null) {
      os = System.getProperty("os.name");
      final String name = os.toLowerCase();
      if (name.startsWith("windows")) {
        os = OS.WINDOWS;
      }
      if (name.startsWith("linux")) {
        os = OS.LINUX;
      }
      if (name.startsWith("freebsd")) {
        os = OS.FREEBSD;
      }
      if (name.equals("mac os x")) {
        os = OS.MACOSX;
      }
	 if ( name.equals( "AIX" ) )
	{
		os = OS.AIX;
	}
	if ( name.equals( "SunOS" ) )
	{
		os = OS.SUNOS;
	}
    }
    return os;
  }

  public static boolean isWindows() {
    return Objects.equals(getOS(null), OS.WINDOWS);
  }

  public static void makeExecutable(final Path file, final Log log) throws MojoExecutionException, MojoFailureException {
    if (Files.notExists(file)) {
      return;
    }

    try {
      Files.walk(file).forEach(f -> {
        try {
          if (Files.isRegularFile(f) && Files.isReadable(f) && Files.isWritable(f) && !Files.isHidden(f)) {
            file.toFile().setExecutable(true);
          }
        } catch (IOException e) {
          log.warn(String.format("Unabled to test hidden attribute for file: {}", f));
        }
      });
    } catch (IOException e) {
      throw new MojoExecutionException(e);
    }
  }

  public static void makeLink(final Path file, final Log log) throws MojoExecutionException, MojoFailureException {
    if (Files.notExists(file)) {
      return;
    }

    try {
      Files.walk(file)
        .filter(Failable.asPredicate(f -> Files.isRegularFile(f) && Files.isReadable(f) && Files.isWritable(f) && !Files.isHidden(f)))
        .filter(f -> f.getFileName().toString().matches(".*\\.so(\\.\\d+)*$"))
        .forEach(Failable.asConsumer(f -> {
        
          // Create the soname if it doesn't exists
          StringTextStream out = new StringTextStream();
          runCommand("objdump",
              List.of("-p", f.toAbsolutePath().toString()),
              null,
              null,
              out,
              new TextStream() {
                @Override
                public void println(String text) {
                  log.error(text);
                }
              },
              new TextStream() {
                @Override
                public void println(String text) {
                  log.debug(text);
                }
              }, log, true);

          Pattern p = Pattern.compile("SONAME\\s+(\\S+\\.so[?:\\.\\d+]+)?");
          Matcher m = p.matcher(out.toString());
          // It is possible that there is no SONAME set
          String soname = null;
          if (m.find()) {
            soname = m.group(1);
          }

          if (soname != null) {
            final Path sofile = f.resolveSibling(soname);
            if (Files.notExists(sofile)) {
              // ln lib.so lib.so.xx
              Files.createLink(sofile, f);
            }
    
            // Create the link name if it doesn't exist
            if (!soname.endsWith(".so")) {
              String linkName = soname.substring(0, soname.indexOf(".so")+3);
              final Path linkLib = f.resolveSibling(linkName);
              if (Files.notExists(linkLib)) {
                // ln lib.so lib.so.xx
                Files.createLink(linkLib, f);
              }
            }
          }
        }));
    } catch (IOException | RuntimeException e) {
      throw new MojoExecutionException(e);
    }
  }

  /* for jdk 1.4 */
  private static String quote(final String s) {
    final String escQ = "\\Q";
    final String escE = "\\E";

    int slashEIndex = s.indexOf(escE);
    if (slashEIndex == -1) {
      return escQ + s + escE;
    }

    final StringBuffer sb = new StringBuffer(s.length() * 2);
    sb.append(escQ);
    slashEIndex = 0;
    int current = 0;
    while ((slashEIndex = s.indexOf(escE, current)) != -1) {
      sb.append(s.substring(current, slashEIndex));
      current = slashEIndex + 2;
      sb.append(escE);
      sb.append("\\");
      sb.append(escE);
      sb.append(escQ);
    }
    sb.append(s.substring(current, s.length()));
    sb.append(escE);
    return sb.toString();
  }

  /* for jdk 1.4 */
  private static String quoteReplacement(final String s) {
    if (s.indexOf('\\') == -1 && s.indexOf('$') == -1) {
      return s;
    }
    final StringBuffer sb = new StringBuffer();
    for (int i = 0; i < s.length(); i++) {
      final char c = s.charAt(i);
      if (c == '\\') {
        sb.append('\\');
        sb.append('\\');
      } else if (c == '$') {
        sb.append('\\');
        sb.append('$');
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  static void removeNulls(final Collection<?> collection) {
    for (final Iterator<?> iter = collection.iterator(); iter.hasNext();) {
      if (iter.next() == null) {
        iter.remove();
      }
    }
  }

  /**
   * Replaces target with replacement in string. For jdk 1.4 compatiblity.
   * 
   * @param target
   * @param replacement
   * @param string
   * @return
   */
  public static String replace(final CharSequence target, final CharSequence replacement, final String string) {
    return Pattern.compile(quote(target.toString())/*
                                                    * , Pattern.LITERAL jdk 1.4
                                                    */).matcher(string).replaceAll(
    /* Matcher. jdk 1.4 */quoteReplacement(replacement.toString()));
  }

  public static int runCommand(final String cmd, final List<String> args, final Path workingDirectory, final List<String> env,
      final Log log) throws MojoExecutionException, MojoFailureException {
    if (log.isInfoEnabled()) {
      final StringBuilder argLine = new StringBuilder();
      if (args != null) {
        for (final String arg : args) {
          argLine.append(" ").append(arg);
        }
      }
      if (workingDirectory != null) {
        log.info("+ cd " + workingDirectory.toAbsolutePath());
      }
      log.info("+ " + cmd + argLine);
    }
    return runCommand(cmd, args, workingDirectory, env, new TextStream() {
      @Override
      public void println(final String text) {
        log.info(text);
      }
    }, new TextStream() {
      @Override
      public void println(final String text) {
        log.error(text);
      }

    }, new TextStream() {
      @Override
      public void println(final String text) {
        log.debug(text);
      }
    }, log);
  }

  public static int runCommand(final String cmd, final List<String> args, final Path workingDirectory, final List<String> env,
      final TextStream out, final TextStream err, final TextStream dbg, final Log log)
      throws MojoExecutionException, MojoFailureException {
    return runCommand(cmd, args, workingDirectory, env, out, err, dbg, log, false);
  }

  public static int runCommand(final String cmd, final List<String> args, final Path workingDirectory, final List<String> env,
      final TextStream out, final TextStream err, final TextStream dbg, final Log log, final boolean expectFailure)
      throws MojoExecutionException, MojoFailureException {
    final Commandline cmdLine = new Commandline();

    try {
      dbg.println("RunCommand: " + cmd);
      cmdLine.setExecutable(cmd);
      if (args != null) {
        for (final String arg : args) {
          dbg.println("  '" + arg + "'");
        }
        cmdLine.addArguments(args.stream().toArray(String[]::new));
      }
      if (workingDirectory != null) {
        dbg.println("in: " + workingDirectory);
        cmdLine.setWorkingDirectory(workingDirectory.toAbsolutePath().toString());
      }

      if (env != null) {
        dbg.println("with Env:");
        for (final String element : env) {
          final String[] nameValue = element.split("=", 2);
          if (nameValue.length < 2) {
            throw new MojoFailureException("   Misformed env: '" + element + "'");
          }
          dbg.println("   '" + nameValue[0] + "=" + nameValue[1] + "'");
          cmdLine.addEnvironment(nameValue[0], nameValue[1]);
        }
      }

      final Process process = cmdLine.execute();
      final StreamGobbler errorGobbler = new StreamGobbler(process.getErrorStream(), err);
      final StreamGobbler outputGobbler = new StreamGobbler(process.getInputStream(), out);

      errorGobbler.start();
      outputGobbler.start();
      process.waitFor();
      final int exitValue = process.exitValue();
      dbg.println("ExitValue: " + exitValue);
      final int timeout = 5000;
      errorGobbler.join(timeout);
      outputGobbler.join(timeout);
      if (exitValue != 0 && !expectFailure) {
        if (log == null) {
          System.err.println(err.toString());
          System.err.println(out.toString());
          System.err.println(dbg.toString());
        } else {
          log.warn(err.toString());
          log.warn(out.toString());
          log.warn(dbg.toString());
        }
        throw new MojoExecutionException("exit code: " + exitValue);
      }
      return exitValue;
    } catch (final MojoExecutionException e) {
      throw e;
    } catch (final Exception e) {
      throw new MojoExecutionException("Could not launch " + cmdLine, e);
    }
  }

  static void runInstallNameTool(final Path[] files, final Log log) throws MojoExecutionException, MojoFailureException {
    final Set<Path> libs = findInstallNameToolCandidates(files, log);

    for (final Path subjectFile : libs) {
      final String subjectName = subjectFile.getFileName().toString();
      final String subjectPath = subjectFile.toString();

      final int idResult = runCommand("install_name_tool",
          List.of("-id", subjectPath, subjectPath), null, null, log);

      if (idResult != 0) {
        throw new MojoExecutionException(
            "Failed to execute 'install_name_tool -id " + subjectPath + " " + subjectPath + "'" + " return code: \'"
                + idResult + "\'.");
      }

      for (final Path dependentFile : libs) {
        final String dependentPath = dependentFile.toString();

        if (Objects.equals(dependentPath, subjectPath)) {
          continue;
        }

        final int changeResult = runCommand("install_name_tool",
            List.of("-change", subjectName, subjectPath, dependentPath),
            null, null, log);

        if (changeResult != 0) {
          throw new MojoExecutionException(
              "Failed to execute 'install_name_tool -change " + subjectName + " " + subjectPath + " " + dependentPath
                  + "'" + " return code: \'" + changeResult + "\'.");
        }
      }
    }
  }

  public static void runRanlib(final Path file, final Log log) throws MojoExecutionException, MojoFailureException {
    if (Files.notExists(file)) {
      return;
    }

    try {
      Files.walk(file)
        .filter(Failable.asPredicate(f -> Files.isRegularFile(f) && Files.isWritable(f) && !Files.isHidden(f)))
        .filter(f -> f.toString().endsWith(".a"))
        .forEach(Failable.asConsumer(f -> {
          // ranlib file
          final int result = runCommand("ranlib",
              List.of(f.toString()), null, null, log);
          if (result != 0) {
            throw new RuntimeException("Failed to execute 'ranlib " + file + "'" + " return code: \'"
                + result + "\'.");
          }
        })
      );
    } catch (IOException | RuntimeException e) {
      throw new MojoExecutionException(e);
    }
    
  }

  /**
   * Produces a human-readable string of the given object which has fields
   * annotated with the Maven {@link Parameter} annotation.
   * 
   * @param o The object for which a human-readable string is desired.
   * @return A human-readable string, with each {@code @Parameter} field on a
   *         separate line rendered as a key/value pair.
   */
  public static String prettyMavenString(final Object o) {
    final StringBuilder sb = new StringBuilder();
    sb.append(o.getClass().getName()).append(":\n");
    for (final Field f : o.getClass().getDeclaredFields()) {
      if (f.getAnnotation(Parameter.class) == null) continue;
      sb.append("\t").append(f.getName()).append("=").append(fieldValue(f, o)).append("\n");
    }
    return sb.toString();
  }

  private static Object fieldValue(final Field f, final Object o) {
    try {
      return f.get(o);
    }
    catch (final IllegalArgumentException | IllegalAccessException exc) {
      return "<ERROR>";
    }
  }
  
  public static String commandArrayToCommand(String[] commandArray) {
    if (commandArray == null) return "";
    StringBuilder builder = new StringBuilder();
    for (String arg : commandArray) {
      builder.append(arg);
      builder.append(" ");
    }
    
    return builder.toString();
  }

  public static void writeCommandFile(Path file, List<String[]> commands) throws MojoExecutionException {
    try (PrintWriter compileCommandWriter = new PrintWriter(Files.newBufferedWriter(file))) {
      for (String[] commandArr : commands) {
        String command = NarUtil.commandArrayToCommand(commandArr);
        compileCommandWriter.println(command);
      }
    } catch (IOException e) {
      throw new MojoExecutionException("Unable to write command history to " + file, e);
    }
  }

  /**
   * Get a registry REG_SZ value.
   *
   * @param root  Root key.
   * @param key   Registry path.
   * @param value Name of the value to retrieve.
   * @return String value.
   */
  public static String registryGet32StringValue(
    com.sun.jna.platform.win32.WinReg.HKEY root, String key, String value)
      throws com.sun.jna.platform.win32.Win32Exception {

    if (!isWindows()) {
      return null;
    }

    com.sun.jna.platform.win32.WinReg.HKEYByReference phkKey = new com.sun.jna.platform.win32.WinReg.HKEYByReference();
    int rc = com.sun.jna.platform.win32.Advapi32.INSTANCE.RegOpenKeyEx(root, key, 0,
        com.sun.jna.platform.win32.WinNT.KEY_READ, phkKey);
    if (rc != com.sun.jna.platform.win32.W32Errors.ERROR_SUCCESS) {
      throw new com.sun.jna.platform.win32.Win32Exception(rc);
    }
    try {
      return com.sun.jna.platform.win32.Advapi32Util.registryGetStringValue(phkKey.getValue(), value);
    } finally {
      rc = com.sun.jna.platform.win32.Advapi32.INSTANCE.RegCloseKey(phkKey.getValue());
      if (rc != com.sun.jna.platform.win32.W32Errors.ERROR_SUCCESS) {
        throw new com.sun.jna.platform.win32.Win32Exception(rc);
      }
    }
  }

  private NarUtil() {
    // never instantiate
  }
}
