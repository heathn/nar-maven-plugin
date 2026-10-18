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
package com.github.maven_nar.test;

import java.util.List;

import junit.framework.TestCase;

import org.apache.maven.model.Resource;
import org.junit.Assert;

import com.github.maven_nar.NarLayout21;

/**
 * Tests finding the resource file that becomes a project's nar.properties.
 */
public class TestResourceSource extends TestCase {
  private static final String DESTINATION = "META-INF/nar/com.example/lib/nar.properties";

  private static Resource resource(final String targetPath, final List<String> includes, final List<String> excludes) {
    final Resource resource = new Resource();
    resource.setDirectory("src/main/resources");
    resource.setTargetPath(targetPath);
    includes.forEach(resource::addInclude);
    excludes.forEach(resource::addExclude);
    return resource;
  }

  public final void testPlainResource() {
    Assert.assertEquals(DESTINATION, NarLayout21.resourceSource(resource(null, List.of(), List.of()), DESTINATION));
  }

  public final void testTargetPath() {
    Assert.assertEquals("nar.properties",
        NarLayout21.resourceSource(resource("META-INF/nar/com.example/lib", List.of(), List.of()), DESTINATION));
    Assert.assertEquals("nar.properties",
        NarLayout21.resourceSource(resource("/META-INF/nar/com.example/lib/", List.of(), List.of()), DESTINATION));
    Assert.assertNull(NarLayout21.resourceSource(resource("other", List.of(), List.of()), DESTINATION));
  }

  public final void testIncludesAndExcludes() {
    Assert.assertEquals(DESTINATION,
        NarLayout21.resourceSource(resource(null, List.of("**/*.properties"), List.of()), DESTINATION));
    Assert.assertNull(NarLayout21.resourceSource(resource(null, List.of("**/*.xml"), List.of()), DESTINATION));
    Assert.assertNull(NarLayout21.resourceSource(resource(null, List.of(), List.of("META-INF/")), DESTINATION));
    Assert.assertEquals("nar.properties", NarLayout21.resourceSource(
        resource("META-INF/nar/com.example/lib", List.of("*.properties"), List.of()), DESTINATION));
  }

  public final void testAntStyleDoubleStar() {
    Assert.assertEquals(DESTINATION,
        NarLayout21.resourceSource(resource(null, List.of("**/META-INF/**"), List.of()), DESTINATION));
    Assert.assertEquals(DESTINATION,
        NarLayout21.resourceSource(resource(null, List.of("META-INF/**/nar/**"), List.of()), DESTINATION));
    Assert.assertNull(NarLayout21.resourceSource(resource(null, List.of(), List.of("**/nar.properties")),
        DESTINATION));
  }
}
