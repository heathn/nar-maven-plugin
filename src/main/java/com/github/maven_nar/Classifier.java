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

/**
 * The classifier of an AOL-specific NAR, &lt;aol&gt;-&lt;type&gt;, such as
 * amd64-Linux-gpp-shared.  AOLs contain dashes too, so the type is what
 * follows the last one.
 *
 * @param aol the AOL
 * @param type the library type
 */
record Classifier(String aol, String type) {

  /**
   * @param classifier a classifier
   * @return its AOL and type, or null if it has no dash after its first
   *         character
   */
  static Classifier parse(final String classifier) {
    final int dash = classifier == null ? -1 : classifier.lastIndexOf('-');
    return dash <= 0 ? null : new Classifier(classifier.substring(0, dash), classifier.substring(dash + 1));
  }

  @Override
  public String toString() {
    return this.aol + "-" + this.type;
  }
}
