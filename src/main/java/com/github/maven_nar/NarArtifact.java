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

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.util.artifact.DelegatingArtifact;

/**
 * @author Mark Donszelmann
 */
public class NarArtifact extends DelegatingArtifact {

  private final NarInfo narInfo;

  public NarArtifact(final Artifact dependency, final NarInfo narInfo) {
    super(dependency);
    this.narInfo = narInfo;
  }

  public String getBaseFilename() {
    return getArtifactId() + "-" + getBaseVersion() + "-" + getClassifier();
  }

  public final NarInfo getNarInfo() {
    return this.narInfo;
  }

  @Override
  protected DelegatingArtifact newInstance(Artifact delegate) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'newInstance'");
  }

}
