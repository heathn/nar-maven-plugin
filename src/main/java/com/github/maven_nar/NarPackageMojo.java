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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.archiver.manager.ArchiverManager;

/**
 * Jar up the NAR files and attach them to the projects main artifact (for
 * installation and deployment).
 *
 * @author Mark Donszelmann
 */
@Mojo(name = "nar-package", defaultPhase = LifecyclePhase.PACKAGE, requiresProject = true)
public class NarPackageMojo extends AbstractNarMojo {
  /**
   * To look up Archiver/UnArchiver implementations
   */
  @Component(role = org.codehaus.plexus.archiver.manager.ArchiverManager.class)
  private ArchiverManager archiverManager;

  /**
   * Used for attaching the artifact in the project
   */
  @Component
  private MavenProjectHelper projectHelper;

  /**
   * The time to give every entry of the NARs, as Maven's archivers do,
   * evaluated like their parameter: seconds since the epoch or an ISO 8601
   * date and time with an offset.  Unset, or a single character, leaves
   * the NARs as they are.
   */
  @Parameter(defaultValue = "${project.build.outputTimestamp}")
  private String outputTimestamp;

  // TODO: this is working of what is present rather than what was requested to
  // be built, POM ~/= artifacts!
  @Override
  public final void narExecute() throws MojoExecutionException, MojoFailureException {
    if (getLayout() instanceof AbstractNarLayout) {
      try {
        ((AbstractNarLayout) getLayout()).setOutputTimestamp(NarUtil.getOutputTimestamp(this.outputTimestamp));
      } catch (final IllegalArgumentException e) {
        throw new MojoExecutionException(e.getMessage(), e);
      }
    }
    // let the layout decide which nars to attach
    getLayout().attachNars(getTargetDirectory(), this.archiverManager, this.projectHelper, getMavenProject());

  }
}
