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
package com.github.maven_nar.cpptasks.types;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.List;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;

/**
 * Tests for the DefineArgument class
 */
public class TestDefineArgument extends TestCase {
  public TestDefineArgument(final String name) {
    super(name);
  }

  public void testIsActive1() {
    final DefineArgument arg = new DefineArgument();
    final Project project = new Project();
    try {
      final boolean isActive = arg.isActive(project);
    } catch (final BuildException ex) {
      return;
    }
    fail("isActive should throw exception if name is not set");
  }

  public void testIsActive2() {
    final DefineArgument arg = new DefineArgument();
    arg.setName("TEST");
    final Project project = new Project();
    project.setProperty("cond", "");
    arg.setIf("cond");
    assertTrue(arg.isActive(project));
  }

  public void testIsActive3() {
    final DefineArgument arg = new DefineArgument();
    arg.setName("TEST");
    final Project project = new Project();
    arg.setIf("cond");
    assertTrue(!arg.isActive(project));
  }

  public void testIsActive4() {
    final DefineArgument arg = new DefineArgument();
    arg.setName("TEST");
    final Project project = new Project();
    project.setProperty("cond", "false");
    arg.setIf("cond");
    try {
      final boolean isActive = arg.isActive(project);
    } catch (final BuildException ex) {
      return;
    }
    fail("Should throw exception for suspicious value");
  }

  public void testIsActive5() {
    final DefineArgument arg = new DefineArgument();
    arg.setName("TEST");
    final Project project = new Project();
    project.setProperty("cond", "");
    arg.setUnless("cond");
    assertTrue(!arg.isActive(project));
  }

  public void testIsActive6() {
    final DefineArgument arg = new DefineArgument();
    arg.setName("TEST");
    final Project project = new Project();
    arg.setUnless("cond");
    assertTrue(arg.isActive(project));
  }

  public void testIsActive7() {
    final DefineArgument arg = new DefineArgument();
    arg.setName("TEST");
    final Project project = new Project();
    project.setProperty("cond", "false");
    arg.setUnless("cond");
    try {
      final boolean isActive = arg.isActive(project);
    } catch (final BuildException ex) {
      return;
    }
    fail("Should throw exception for suspicious value");
  }

  public void testIsActive8() {
    final DefineArgument arg = new DefineArgument();
    arg.setName("TEST");
    final Project project = new Project();
    project.setProperty("cond", "");
    arg.setIf("cond");
    arg.setUnless("cond");
    assertTrue(!arg.isActive(project));
  }

  public void testMerge() {
    final List<UndefineArgument> base = new ArrayList<>();
    final List<UndefineArgument> specific = new ArrayList<>();
    UndefineArgument arg = new DefineArgument();
    arg.setName("foo");
    base.add(arg);
    arg = new UndefineArgument();
    arg.setName("hello");
    base.add(arg);
    arg = new DefineArgument();
    arg.setName("hello");
    specific.add(arg);
    arg = new UndefineArgument();
    arg.setName("world");
    specific.add(arg);
    final List<UndefineArgument> merged = UndefineArgument.merge(base, specific);
    assertEquals(3, merged.size());
    assertEquals("foo", merged.get(0).getName());
    assertEquals(true, merged.get(0).isDefine());
    assertEquals("hello", merged.get(1).getName());
    assertEquals(true, merged.get(1).isDefine());
    assertEquals("world", merged.get(2).getName());
    assertEquals(false, merged.get(2).isDefine());
  }
}
