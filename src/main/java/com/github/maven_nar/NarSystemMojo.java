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
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;
import org.sonatype.plexus.build.incremental.BuildContext;

/**
 * Generates a NarSystem class with static methods to use inside the java part
 * of the library. Runs in generate-resources rather than generate-sources to
 * allow the maven-swig-plugin (which runs in generate-sources) to configure the
 * nar plugin and to let it generate a proper system file.
 *
 * @author Mark Donszelmann
 */
@Mojo(name = "nar-system-generate", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, requiresProject = true)
public class NarSystemMojo extends AbstractNarMojo {

  /** @component */
  private BuildContext buildContext;

  private String generateExtraMethods() throws MojoFailureException {
    final StringBuilder builder = new StringBuilder();

    builder.append("\n    private static String[] getAOLs() {\n");
    builder
        .append("        final String ao = System.getProperty(\"os.arch\") + \"-\" + System.getProperty(\"os.name\").replaceAll(\" \", \"\");\n");

    // build map: AO -> AOLs
    final Map<String, List<String>> aoMap = new LinkedHashMap<>();
    for (final String aol : NarProperties.getInstance(getMavenProject()).getKnownAOLs()) {
      final int dash = aol.lastIndexOf('-');
      final String ao = aol.substring(0, dash);
      List<String> list = aoMap.get(ao);
      if (list == null) {
        aoMap.put(ao, list = new ArrayList<>());
      }
      list.add(aol);
    }

    builder.append("\n        // choose the list of known AOLs for the current platform\n");
    String delimiter = "        ";
    for (final Map.Entry<String, List<String>> entry : aoMap.entrySet()) {
      builder.append(delimiter);
      delimiter = " else ";

      builder.append("if (ao.startsWith(\"").append(entry.getKey()).append("\")) {\n");
      builder.append("            return new String[] {\n");
      String delimiter2 = "              ";
      for (final String aol : entry.getValue()) {
        builder.append(delimiter2).append("\"").append(aol).append("\"");
        delimiter2 = ", ";
      }
      builder.append("\n            };");
      builder.append("\n        }");
    }
    builder.append(" else {\n");
    builder.append("            throw new RuntimeException(\"Unhandled architecture/OS: \" + ao);\n");
    builder.append("        }\n");
    builder.append("    }\n");
    builder.append("\n");
    builder.append("    private static String[] getMappedLibraryNames(String fileName) {\n");
    builder.append("        String mapped = System.mapLibraryName(fileName);\n");
    builder
    .append("        final String ao = System.getProperty(\"os.arch\") + \"-\" + System.getProperty(\"os.name\").replaceAll(\" \", \"\");\n");
    builder.append("    	if (ao.startsWith(\"x86_64-MacOSX\")){\n");
    builder.append("    		// .jnilib or .dylib depends on JDK version\n");
    builder.append("    		mapped = mapped.substring(0, mapped.lastIndexOf('.'));\n");
    builder.append("    		return new String[]{mapped+\".dylib\", mapped+\".jnilib\"};\n");
    builder.append("    	}\n");
    builder.append("    	return new String[]{mapped};\n");
    builder.append("    }\n");
    builder.append("\n");
    builder
        .append("    private static File getUnpackedLibPath(final ClassLoader loader, final String[] aols, final String fileName, final String[] mappedNames) {\n");
    builder.append("        final String classPath = NarSystem.class.getName().replace('.', '/') + \".class\";\n");
    builder.append("        final URL url = loader.getResource(classPath);\n");
    builder.append("        if (url == null || !\"file\".equals(url.getProtocol())) return null;\n");
    builder.append("        final String path = url.getPath();\n");
    builder
        .append("        final String prefix = path.substring(0, path.length() - classPath.length()) + \"../nar/\" + fileName + \"-\";\n");
    builder.append("        for (final String aol : aols) {\n");
    builder.append("            for(final String mapped : mappedNames) {\n");
    builder
        .append("                final File file = new File(prefix + aol + \"-jni/lib/\" + aol + \"/jni/\" + mapped);\n");
    builder.append("                if (file.isFile()) return file;\n");
    builder.append("                final File fileShared = new File(prefix + aol + \"-shared/lib/\" + aol + \"/shared/\" + mapped);\n");
    builder.append("                if (fileShared.isFile()) return fileShared;\n");
    builder.append("            }\n");
    builder.append("        }\n");
    builder.append("        return null;\n");
    builder.append("    }\n");
    builder.append("\n");
    builder
        .append("    private static String getLibPath(final ClassLoader loader, final String[] aols, final String[] mappedNames) {\n");
    builder.append("        for (final String aol : aols) {\n");
    builder.append("            final String libPath = \"lib/\" + aol + \"/jni/\";\n");
    builder.append("            final String libPathShared = \"lib/\" + aol + \"/shared/\";\n");
    builder.append("            for(final String mapped : mappedNames) {\n");
    builder.append("                if (loader.getResource(libPath + mapped) != null) return libPath;\n");
    builder.append("                if (loader.getResource(libPathShared + mapped) != null) return libPathShared;\n");
    builder.append("            }\n");
    builder.append("        }\n");
    builder.append("        throw new RuntimeException(\"Library '\" + mappedNames[0] + \"' not found!\");\n");
    builder.append("    }\n");

    return builder.toString();
  }

  private boolean hasNativeLibLoaderAsDependency() {
    return getNativeLibLoaderVersion() != null;
  }

  private String getNativeLibLoaderVersion() {
    for (MavenProject project = getMavenProject(); project != null; project = project.getParent()) {
      final List<Dependency> dependencies = project.getDependencies();
      for (final Dependency dependency : dependencies) {
        final String artifactId = dependency.getArtifactId();
        if ("native-lib-loader".equals(artifactId)) {
          return dependency.getVersion();
        }
      }
    }
    return null;
  }

  @Override
  public final void narExecute() throws MojoExecutionException, MojoFailureException {
    // get packageName if specified for JNI.
    String packageName = null;
    String narSystemName = null;
    Path narSystemDirectory = null;
    boolean jniFound = false;
    for (final Library library : getLibraries()) {
      if (library.getType().equals(Library.JNI) || library.getType().equals(Library.SHARED)) {
        packageName = library.getNarSystemPackage();
        narSystemName = library.getNarSystemName();
        narSystemDirectory = getTargetDirectory().resolve(library.getNarSystemDirectory());
        jniFound = true;
      }
    }

    if (!jniFound || packageName == null) {
      if (!jniFound) {
        getLog().debug("NAR: not building a shared or JNI library, so not generating NarSystem class.");
      } else {
        getLog().warn("NAR: no system package specified; unable to generate NarSystem class.");
      }
      return;
    }

    final Path fullDir = narSystemDirectory.resolve(packageName.replace('.', '/'));
    try {
      // make sure destination is there
      Files.createDirectories(narSystemDirectory);
      Files.createDirectories(fullDir);
    } catch (IOException e) {
      throw new MojoExecutionException(e);
    }
    getMavenProject().addCompileSourceRoot(narSystemDirectory.toString());

    final Path narSystem = fullDir.resolve(narSystemName + ".java");
    getLog().info("Generating " + narSystem);
    // initialize string variable to be used in NarSystem.java
    final String importString, loadLibraryString, extraMethods, output = getOutput(true);
    if (hasNativeLibLoaderAsDependency()) {
      getLog().info("Using 'native-lib-loader'");
      importString = """

        import java.io.File;
        import java.net.URL;
        import org.scijava.nativelib.DefaultJniExtractor;
        import org.scijava.nativelib.JniExtractor;
        """;
      loadLibraryString = String.format("""
        final String fileName = "%1$s";
        // first try if the library is on the configured library path
        try {
          System.loadLibrary("%1$s");
          return;
        } catch (Exception | UnsatisfiedLinkError e) {
        }
        final String[] mappedNames = getMappedLibraryNames(fileName);
        final String[] aols = getAOLs();
        final ClassLoader loader = NarSystem.class.getClassLoader();
        final File unpacked = getUnpackedLibPath(loader, aols, fileName, mappedNames);
        if (unpacked != null) {
          System.load(unpacked.getPath());
        } else {
          try {
            final String libPath = getLibPath(loader, aols, mappedNames);
            final JniExtractor extractor = %2$s
            final File extracted = extractor.extractJni(libPath, fileName);
            System.load(extracted.getAbsolutePath());
          } catch (final Exception e) {
            e.printStackTrace();
            throw e instanceof RuntimeException ?
                (RuntimeException) e : new RuntimeException(e);
          }
        }
      """, output, getJniExtractorCreationStatement());
      extraMethods = generateExtraMethods();
    } else {
      getLog().info("Not using 'native-lib-loader' (because it is not a dependency)");
      importString = "";
      loadLibraryString = "System.loadLibrary(\"" + output + "\");";
      extraMethods = "";
    }

    try (final PrintWriter p = new PrintWriter(Files.newOutputStream(narSystem))) {
      p.print(String.format("""
        // DO NOT EDIT: Generated by NarSystemGenerate.
        package %1$s;
        %2$s

        /**
         * Generated class to load the correct version of the jni library
         *
         * @author nar-maven-plugin
         */
        public final class NarSystem {

          private NarSystem() {
          }

          /**
           * Load jni library: %3$s
           *
           * @author nar-maven-plugin
           */
          public static void loadLibrary() {
            %4$s
          }

          public static String getLibraryName() {
            return "%3$s";
          }

          public static int runUnitTests() {
            return new NarSystem().runUnitTestsNative();
          }

          public native int runUnitTestsNative();
          %5$s
        }
        """, packageName, importString, output, loadLibraryString, extraMethods));
    } catch (final IOException e) {
      throw new MojoExecutionException("Could not write '" + narSystemName + "'", e);
    }

    if (this.buildContext != null) {
      this.buildContext.refresh(narSystem.toFile());
    }
  }

  private String getJniExtractorCreationStatement() {
    String nativeLoaderVersion = getNativeLibLoaderVersion();
    if (nativeLoaderVersion == null) {
      throw new AssertionError("getJniExtractorCreationStatement() called, but nativel-lib-loader plugin is absent, or its version is unknown");
    }
    // versions 1.x.x are unavailable on Maven Central and Github, not checking against them
    // new format was introduced in "2.3.0"
    boolean oldFormat = nativeLoaderVersion.matches("2\\.[012]\\..*");
    return oldFormat
        ? "new DefaultJniExtractor(NarSystem.class, System.getProperty(\"java.io.tmpdir\"));"
        : "new DefaultJniExtractor(NarSystem.class);";
  }
}
