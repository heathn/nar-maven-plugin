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
package com.github.maven_nar.cpptasks;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Execute;
import org.apache.tools.ant.types.Commandline;
import org.apache.tools.ant.types.Environment;

/**
 * Some utilities used by the CC and Link tasks.
 *
 * @author Adam Murdoch
 * @author Curt Arnold
 */
public class CUtil {
  /**
   * A class that splits a white-space, comma-separated list into a String
   * array. Used for task attributes.
   */
  public static final class StringArrayBuilder {
    private final List<String> _value;

    public StringArrayBuilder(final String value) {
      // Split the defines up
      _value = Arrays.stream(value.split(",|\\s"))
          .map(String::trim)
          .filter(Predicate.not(String::isEmpty))
          .collect(Collectors.toList());
    }

    public List<String> getValue() {
      return this._value;
    }
  }

  public final static int FILETIME_EPSILON = 500;

  /**
   * Checks a array of names for non existent or non directory entries and
   * nulls them out.
   *
   * @return Count of non-null elements
   */
  public static int checkDirectoryArray(final Path[] names) {
    int count = 0;
    for (int i = 0; i < names.length; i++) {
      if (names[i] != null) {
        if (Files.exists(names[i]) && Files.isDirectory(names[i])) {
          count++;
        } else {
          names[i] = null;
        }
      }
    }
    return count;
  }

  /**
   * Extracts the basename of a file, removing the extension, if present
   */
  public static String getBasename(final Path file) {
    // Remove the extension
    String basename = file.getFileName().toString();
    final int pos = basename.lastIndexOf('.');
    if (pos != -1) {
      basename = basename.substring(0, pos);
    }
    return basename;
  }

  /**
   * Gets the parent directory for the executable file name using the current
   * directory and system executable path
   *
   * @param exeName
   *          Name of executable such as "cl.exe"
   * @return parent directory or null if not located
   */
  public static Path getExecutableLocation(final String exeName) {
    //
    // must add current working directory to the
    // from of the path from the "path" environment variable
    final Path currentDir = Path.of(System.getProperty("user.dir"));
    if (Files.exists(currentDir.resolve(exeName))) {
      return currentDir;
    }
    final List<Path> envPath = CUtil.getPathFromEnvironment("PATH", File.pathSeparator);
    for (final Path element : envPath) {
      if (Files.exists(element.resolve(exeName))) {
        return element;
      }
    }
    return null;
  }

  /**
   * Extracts the parent of a file
   */
  public static String getParentPath(final String path) {
    final int pos = path.lastIndexOf(File.separator);
    if (pos <= 0) {
      return null;
    }
    return path.substring(0, pos);
  }

  /**
   * Returns an array of File for each existing directory in the specified
   * environment variable
   *
   * @param envVariable
   *          environment variable name such as "LIB" or "INCLUDE"
   * @param delim
   *          delimitor used to separate parts of the path, typically ";"
   *          or ":"
   * @return array of File's for each part that is an existing directory
   */
  public static List<Path> getPathFromEnvironment(final String envVariable, final String delim) {
    // OS/4000 does not support the env command.
    if (System.getProperty("os.name").equals("OS/400")) {
      return Collections.emptyList();
    }
    Map<String, String> env = Execute.getEnvironmentVariables();
    return parsePath(env.getOrDefault(envVariable, ""), delim);
  }

  /**
   * Returns a relative path for the targetFile relative to the base
   * directory.
   *
   * @param base
   *          base directory as returned by File.getCanonicalPath()
   * @param targetFile
   *          target file
   * @return relative path of target file. Returns targetFile if there were
   *         no commonalities between the base and the target
   *
   */
  public static String getRelativePath(final String base, final File targetFile) {
    try {
      //
      // remove trailing file separator
      //
      String canonicalBase = base;
      if (base.charAt(base.length() - 1) != File.separatorChar) {
        canonicalBase = base + File.separatorChar;
      }
      //
      // get canonical name of target
      //
      String canonicalTarget;
      if (System.getProperty("os.name").equals("OS/400")) {
        canonicalTarget = targetFile.getPath();
      } else {
        canonicalTarget = targetFile.getCanonicalPath();
      }
      if (canonicalBase.startsWith(canonicalTarget + File.separatorChar)) {
        canonicalTarget = canonicalTarget + File.separator;
      }
      if (canonicalTarget.equals(canonicalBase)) {
        return ".";
      }
      //
      // see if the prefixes are the same
      //
      if (substringMatch(canonicalBase, 0, 2, "\\\\")) {
        //
        // UNC file name, if target file doesn't also start with same
        // server name, don't go there
        final int endPrefix = canonicalBase.indexOf('\\', 2);
        final String prefix1 = canonicalBase.substring(0, endPrefix);
        final String prefix2 = canonicalTarget.substring(0, endPrefix);
        if (!prefix1.equals(prefix2)) {
          return canonicalTarget;
        }
      } else {
        if (substringMatch(canonicalBase, 1, 3, ":\\")) {
          final int endPrefix = 2;
          final String prefix1 = canonicalBase.substring(0, endPrefix);
          final String prefix2 = canonicalTarget.substring(0, endPrefix);
          if (!prefix1.equals(prefix2)) {
            return canonicalTarget;
          }
        } else {
        	if (canonicalBase.charAt(0) == '/' && canonicalTarget.charAt(0) != '/') {
        		return canonicalTarget;
        	}
        }
      }
      final char separator = File.separatorChar;
      int lastCommonSeparator = -1;
      int minLength = canonicalBase.length();
      if (canonicalTarget.length() < minLength) {
        minLength = canonicalTarget.length();
      }
      //
      // walk to the shorter of the two paths
      // finding the last separator they have in common
      for (int i = 0; i < minLength; i++) {
        if (canonicalTarget.charAt(i) == canonicalBase.charAt(i)) {
          if (canonicalTarget.charAt(i) == separator) {
            lastCommonSeparator = i;
          }
        } else {
          break;
        }
      }
      final StringBuffer relativePath = new StringBuffer(50);
      //
      // walk from the first difference to the end of the base
      // adding "../" for each separator encountered
      //
      for (int i = lastCommonSeparator + 1; i < canonicalBase.length(); i++) {
        if (canonicalBase.charAt(i) == separator) {
          if (relativePath.length() > 0) {
            relativePath.append(separator);
          }
          relativePath.append("..");
        }
      }
      if (canonicalTarget.length() > lastCommonSeparator + 1) {
        if (relativePath.length() > 0) {
          relativePath.append(separator);
        }
        relativePath.append(canonicalTarget.substring(lastCommonSeparator + 1));
      }
      return relativePath.toString();
    } catch (final IOException ex) {
    }
    return targetFile.toString();
  }

  public static boolean isActive(final Project p, final String ifCond, final String unlessCond) throws BuildException {
    if (ifCond != null) {
      final String ifValue = p.getProperty(ifCond);
      if (ifValue == null) {
        return false;
      } else {
        if (ifValue.equals("false") || ifValue.equals("no")) {
          throw new BuildException("if condition \"" + ifCond + "\" has suspicious value \"" + ifValue);
        }
      }
    }
    if (unlessCond != null) {
      final String unlessValue = p.getProperty(unlessCond);
      if (unlessValue != null) {
        if (unlessValue.equals("false") || unlessValue.equals("no")) {
          throw new BuildException("unless condition \"" + unlessCond + "\" has suspicious value \"" + unlessValue);
        }
        return false;
      }
    }
    return true;
  }

  /**
   * Determines whether time1 is later than time2
   * to a degree that file system time truncation is not significant.
   *
   * @param time1
   *          first time value
   * @param time2
   *          second time value
   * @return boolean if first time value is later than second time value.
   *         If the values are within the rounding error of the file system
   *         return false.
   */
  public static boolean isSignificantlyAfter(final FileTime time1, final FileTime time2) {
    return time1.toMillis() > time2.toMillis() + FILETIME_EPSILON;
  }

  /**
   * Determines whether time1 is earlier than time2
   * to a degree that file system time truncation is not significant.
   *
   * @param time1
   *          first time value
   * @param time2
   *          second time value
   * @return boolean if first time value is earlier than second time value.
   *         If the values are within the rounding error of the file system
   *         return false.
   */
  public static boolean isSignificantlyBefore(final FileTime time1, final FileTime time2) {
    return time1.toMillis() + FILETIME_EPSILON < time2.toMillis();
  }

  /**
   * Determines if source file has a system path,
   * that is part of the compiler or platform.
   * 
   * @param source
   *          source, may not be null.
   * @return true is source file appears to be system library
   *         and its path should be discarded.
   */
  public static boolean isSystemPath(final Path source) {
    final String lcPath = source.toAbsolutePath().toString().toLowerCase(java.util.Locale.US);
    return lcPath.contains("platformsdk") || lcPath.contains("windows kits") || lcPath.contains("microsoft")
        || Objects.equals(lcPath, "/usr/include")
        || Objects.equals(lcPath, "/usr/lib") || Objects.equals(lcPath, "/usr/local/include")
        || Objects.equals(lcPath, "/usr/local/lib");
  }

  /**
   * Parse a string containing directories into an File[]
   *
   * @param path
   *          path string, for example ".;c:\something\include"
   * @param delim
   *          delimiter, typically ; or :
   */
  public static List<Path> parsePath(final String path, final String delim) {
    return Arrays.stream(path.split(delim))
        .filter(Predicate.not(String::isEmpty))
        .map(Path::of)
        .collect(Collectors.toList());
  }

  /**
   * This method is exposed so test classes can overload and test the
   * arguments without actually spawning the compiler
   */
  public static int runCommand(final CCTask task, final Path workingDir, final String[] cmdline,
      final boolean newEnvironment, final Environment env) throws BuildException {
    try {
      task.log(Commandline.toString(cmdline), task.getCommandLogLevel());

     /* final Execute exe = new Execute(new LogStreamHandler(task, Project.MSG_INFO, Project.MSG_ERR));
      if (System.getProperty("os.name").equals("OS/390")) {
        exe.setVMLauncher(false);
      }
      exe.setAntRun(task.getProject());
      exe.setCommandline(cmdline);
      exe.setWorkingDirectory(workingDir);
      if (env != null) {
        final String[] environment = env.getVariables();
        if (environment != null) {
          for (final String element : environment) {
            task.log("Setting environment variable: " + element, Project.MSG_VERBOSE);
          }
        }
        exe.setEnvironment(environment);
      }
      exe.setNewenvironment(newEnvironment);
      return exe.execute();
            */

    Environment defEvn = env;
    if (defEvn == null) {
      defEvn = new Environment();
    }
	  return CommandExecution.runCommand(cmdline,workingDir,task,defEvn.getVariablesVector());
    } catch (final java.io.IOException exc) {
      throw new BuildException("Could not launch " + cmdline[0] + ": " + exc, task.getLocation());
    }
  }

  /**
   * Compares the contents of 2 arrays for equaliy.
   */
  public static boolean sameList(final Object[] a, final Object[] b) {
    if (a == null || b == null || a.length != b.length) {
      return false;
    }
    for (int i = 0; i < a.length; i++) {
      if (!a[i].equals(b[i])) {
        return false;
      }
    }
    return true;
  }

  private static boolean
      substringMatch(final String src, final int beginIndex, final int endIndex, final String target) {
    if (src.length() < endIndex) {
      return false;
    }
    return src.substring(beginIndex, endIndex).equals(target);
  }

  public static String toUnixPath(final String path) {
    if (File.separatorChar != '/' && path.indexOf(File.separatorChar) != -1) {
      return path.replace(File.separator, "/");
    }
    return path;
  }

  public static String toWindowsPath(final String path) {
    if (File.separatorChar != '\\' && path.indexOf(File.separatorChar) != -1) {
      return path.replace(File.separator, "\\");
    }
    return path;
  }

  /**
   * Replaces any embedded quotes in the string so that the value can be
   * placed in an attribute in an XML file
   *
   * @param attrValue
   *          value to be expressed
   * @return equivalent attribute literal
   *
   */
  public static String xmlAttribEncode(final String attrValue) {
    final StringBuffer buf = new StringBuffer(attrValue);
    int quotePos;

    for (quotePos = -1; (quotePos = buf.indexOf("\"", quotePos + 1)) >= 0;) {
      buf.deleteCharAt(quotePos);
      buf.insert(quotePos, "&quot;");
      quotePos += 5;
    }

    for (quotePos = -1; (quotePos = buf.indexOf("<", quotePos + 1)) >= 0;) {
      buf.deleteCharAt(quotePos);
      buf.insert(quotePos, "&lt;");
      quotePos += 3;
    }

    for (quotePos = -1; (quotePos = buf.indexOf(">", quotePos + 1)) >= 0;) {
      buf.deleteCharAt(quotePos);
      buf.insert(quotePos, "&gt;");
      quotePos += 3;
    }

    return buf.toString();
  }

}
