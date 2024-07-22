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
package com.github.maven_nar.cpptasks.gcc;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import junit.framework.TestCase;

import com.github.maven_nar.cpptasks.CCTask;
import com.github.maven_nar.cpptasks.CUtil;
import com.github.maven_nar.cpptasks.OutputTypeEnum;
import com.github.maven_nar.cpptasks.compiler.LinkType;
import com.github.maven_nar.cpptasks.types.LibrarySet;
import com.github.maven_nar.cpptasks.types.LibraryTypeEnum;

/**
 * Test ld linker adapter abstract base class
 *
 * Override create to test concrete compiler implementions
 */
public class TestAbstractLdLinker extends TestCase {
  private final String realOSName;

  public TestAbstractLdLinker(final String name) {
    super(name);
    this.realOSName = System.getProperty("os.name");
  }

  protected AbstractLdLinker getLinker() {
    return GccLinker.getInstance();
  }

  @Override
  protected void tearDown() throws java.lang.Exception {
    System.setProperty("os.name", this.realOSName);
  }

  /**
   * Checks for proper arguments for plugin generation on Darwin
   * 
   * See [ 676276 ] Enhanced support for Mac OS X
   */
  public void testAddImpliedArgsDarwinPlugin() {
    System.setProperty("os.name", "Mac OS X");
    final AbstractLdLinker linker = getLinker();
    final Vector args = new Vector();
    final LinkType pluginType = new LinkType();
    final OutputTypeEnum pluginOutType = new OutputTypeEnum();
    pluginOutType.setValue("plugin");
    pluginType.setOutputType(pluginOutType);
    linker.addImpliedArgs(null, false, pluginType, args);
    assertEquals(1, args.size());
    assertEquals("-bundle", args.elementAt(0));
  }

  /**
   * Checks for proper arguments for shared generation on Darwin
   * 
   * See [ 676276 ] Enhanced support for Mac OS X
   */
  public void testAddImpliedArgsDarwinShared() {
    System.setProperty("os.name", "Mac OS X");
    final AbstractLdLinker linker = getLinker();
    final Vector args = new Vector();
    final LinkType pluginType = new LinkType();
    final OutputTypeEnum pluginOutType = new OutputTypeEnum();
    pluginOutType.setValue("shared");
    pluginType.setOutputType(pluginOutType);
    linker.addImpliedArgs(null, false, pluginType, args);
    // FIXME NAR-103
    // BEGINFREEHEP
    assertEquals(1, args.size());
    assertEquals("-dynamiclib", args.elementAt(0));
    // ENDFREEHEP
  }

  /**
   * Checks for proper arguments for plugin generation on Darwin
   * 
   * See [ 676276 ] Enhanced support for Mac OS X
   */
  public void testAddImpliedArgsNonDarwinPlugin() {
    System.setProperty("os.name", "VAX/VMS");
    final AbstractLdLinker linker = getLinker();
    final Vector args = new Vector();
    final LinkType pluginType = new LinkType();
    final OutputTypeEnum pluginOutType = new OutputTypeEnum();
    pluginOutType.setValue("plugin");
    pluginType.setOutputType(pluginOutType);
    linker.addImpliedArgs(null, false, pluginType, args);
    assertEquals(1, args.size());
    assertEquals("-shared", args.elementAt(0));
  }

  /**
   * Checks for proper arguments for shared generation on Darwin
   * 
   * See [ 676276 ] Enhanced support for Mac OS X
   */
  public void testAddImpliedArgsNonDarwinShared() {
    System.setProperty("os.name", "VAX/VMS");
    final AbstractLdLinker linker = getLinker();
    final Vector args = new Vector();
    final LinkType pluginType = new LinkType();
    final OutputTypeEnum pluginOutType = new OutputTypeEnum();
    pluginOutType.setValue("shared");
    pluginType.setOutputType(pluginOutType);
    linker.addImpliedArgs(null, false, pluginType, args);
    assertEquals(1, args.size());
    assertEquals("-shared", args.elementAt(0));
  }

  public void testAddLibrarySetDirSwitch() {
    final AbstractLdLinker linker = getLinker();
    final CCTask task = new CCTask();

    /* throws an Exception in setLibs otherwise */
    LibrarySet set = new LibrarySet();
    set.setProject(new org.apache.tools.ant.Project());
    set.setDir(Path.of("/foo"));
    set.setLibs(new CUtil.StringArrayBuilder("bart,cart,dart"));
    final List<LibrarySet> sets = List.of(set);

    final List<String> preargs = new ArrayList<>();
    final List<String> midargs = new ArrayList<>();
    final List<String> endargs = new ArrayList<>();
    final String[] rc = linker.addLibrarySets(task, sets, preargs, midargs, endargs);
    final String libdirSwitch = endargs.get(0);
    assertEquals(libdirSwitch.substring(0, 2), "-L");
    //
    // can't have space after -L or will break Mac OS X
    //
    assertTrue(!libdirSwitch.substring(2, 3).equals(" "));
    assertEquals(libdirSwitch.substring(libdirSwitch.length() - 3), "foo");
  }

  public void testAddLibrarySetLibFrameworkDarwin() {
    System.setProperty("os.name", "Mac OS X");
    final AbstractLdLinker linker = getLinker();
    final CCTask task = new CCTask();

    /* throws an Exception in setLibs otherwise */
    LibrarySet set = new LibrarySet();
    set.setProject(new org.apache.tools.ant.Project());
    set.setDir(Path.of("/foo"));
    final LibraryTypeEnum libType = new LibraryTypeEnum();
    libType.setValue("framework");
    set.setType(libType);
    set.setLibs(new CUtil.StringArrayBuilder("bart,cart,dart"));
    final List<LibrarySet> sets = List.of(set);

    final List<String> preargs = new ArrayList<>();
    final List<String> midargs = new ArrayList<>();
    final List<String> endargs = new ArrayList<>();
    final String[] rc = linker.addLibrarySets(task, sets, preargs, midargs, endargs);
    // FIXME NAR-103
    // BEGINFREEHEP
    /*
     * assertEquals("-F", ((String) endargs.elementAt(0)).substring(0, 2));
     * assertEquals("-framework bart", (String) endargs.elementAt(1));
     * assertEquals("-framework cart", (String) endargs.elementAt(2));
     * assertEquals("-framework dart", (String) endargs.elementAt(3));
     * assertEquals(endargs.size(), 4);
     */
    assertEquals("-F", endargs.get(0).substring(0, 2));
    assertEquals("-framework", endargs.get(1));
    assertEquals("bart", endargs.get(2));
    assertEquals("cart", endargs.get(3));
    assertEquals("dart", endargs.get(4));
    assertEquals(endargs.size(), 5);
    // ENDFREEHEP
  }

  public void testAddLibrarySetLibFrameworkNonDarwin() {
    System.setProperty("os.name", "VAX/VMS");
    final AbstractLdLinker linker = getLinker();
    final CCTask task = new CCTask();

    /* throws an Exception in setLibs otherwise */
    LibrarySet set = new LibrarySet();
    set.setProject(new org.apache.tools.ant.Project());
    set.setDir(Path.of("/foo"));
    final LibraryTypeEnum libType = new LibraryTypeEnum();
    libType.setValue("framework");
    set.setType(libType);
    set.setLibs(new CUtil.StringArrayBuilder("bart,cart,dart"));
    final List<LibrarySet> sets = List.of(set);

    final List<String> preargs = new ArrayList<>();
    final List<String> midargs = new ArrayList<>();
    final List<String> endargs = new ArrayList<>();
    final String[] rc = linker.addLibrarySets(task, sets, preargs, midargs, endargs);
    assertEquals("-L", endargs.get(0).substring(0, 2));
    // FIXME NAR-103
    // BEGINFREEHEP
    // assertEquals("-Bdynamic", (String) endargs.elementAt(1));
    assertEquals("-lbart", endargs.get(1));
    assertEquals("-lcart", endargs.get(2));
    assertEquals("-ldart", endargs.get(3));
    assertEquals(endargs.size(), 4);
    // ENDFREEHEP
  }

  public void testAddLibrarySetLibSwitch() {
    final AbstractLdLinker linker = getLinker();
    final CCTask task = new CCTask();

    /* throws an Exception in setLibs otherwise */
    LibrarySet set = new LibrarySet();
    set.setProject(new org.apache.tools.ant.Project());
    set.setDir(Path.of("/foo"));
    set.setLibs(new CUtil.StringArrayBuilder("bart,cart,dart"));
    final List<LibrarySet> sets = List.of(set);

    final List<String> preargs = new ArrayList<>();
    final List<String> midargs = new ArrayList<>();
    final List<String> endargs = new ArrayList<>();
    final String[] rc = linker.addLibrarySets(task, sets, preargs, midargs, endargs);
    assertEquals("-lbart", endargs.get(1));
    assertEquals("-lcart", endargs.get(2));
    assertEquals("-ldart", endargs.get(3));
    assertEquals(endargs.size(), 4);
  }

  public void testAddLibraryStatic() {
    final AbstractLdLinker linker = getLinker();
    final CCTask task = new CCTask();

    /* throws an Exception in setLibs otherwise */
    final List<LibrarySet> sets = new ArrayList<>();
    LibrarySet set = new LibrarySet();
    set.setProject(new org.apache.tools.ant.Project());
    set.setLibs(new CUtil.StringArrayBuilder("bart"));
    sets.add(set);
    set = new LibrarySet();
    set.setProject(new org.apache.tools.ant.Project());
    set.setLibs(new CUtil.StringArrayBuilder("cart"));
    final LibraryTypeEnum libType = new LibraryTypeEnum();
    libType.setValue("static");
    set.setType(libType);
    sets.add(set);
    set = new LibrarySet();
    set.setProject(new org.apache.tools.ant.Project());
    set.setLibs(new CUtil.StringArrayBuilder("dart"));
    sets.add(set);

    final List<String> preargs = new ArrayList<>();
    final List<String> midargs = new ArrayList<>();
    final List<String> endargs = new ArrayList<>();
    final String[] rc = linker.addLibrarySets(task, sets, preargs, midargs, endargs);
    // for (int i=0; i<endargs.size(); i++) System.err.println(endargs.get( i
    // ));
    // NAR-103
    // BEGINFREEHEP
    if (System.getProperty("os.name").equals("Mac OS X")) {
      assertEquals("-lbart", endargs.get(0));
      assertEquals("-lcart", endargs.get(1));
      assertEquals("-ldart", endargs.get(2));
      assertEquals(endargs.size(), 3);
    } else {
      assertEquals("-lbart", endargs.get(0));
      assertEquals("-Bstatic", endargs.get(1));
      assertEquals("-lcart", endargs.get(2));
      assertEquals("-Bdynamic", endargs.get(3));
      assertEquals("-ldart", endargs.get(4));
      assertEquals(endargs.size(), 5);
    }
    // ENDFREEHEP
  }

  public void testLibReturnValue() {
    final AbstractLdLinker linker = getLinker();
    final CCTask task = new CCTask();

    /* throws an Exception in setLibs otherwise */
    LibrarySet set = new LibrarySet();
    set.setProject(new org.apache.tools.ant.Project());
    set.setDir(Path.of("/foo"));
    set.setLibs(new CUtil.StringArrayBuilder("bart,cart,dart"));
    final List<LibrarySet> sets = List.of(set);

    final List<String> preargs = new ArrayList<>();
    final List<String> midargs = new ArrayList<>();
    final List<String> endargs = new ArrayList<>();
    final String[] rc = linker.addLibrarySets(task, sets, preargs, midargs, endargs);
    assertEquals(3, rc.length);
    assertEquals("bart", rc[0]);
    assertEquals("cart", rc[1]);
    assertEquals("dart", rc[2]);
  }
}
