/*
 * Copyright 2013 University of Chicago and Argonne National Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package exm.stc.tclbackend.tree;

import org.apache.commons.lang3.StringUtils;

/**
 * The TclTree class hierarchy represents all Tcl constructs
 * necessary for Turbine code generation
 *
 * TclTree is the most abstract Tcl expression
 *
 * @author wozniak
 * */
public abstract class TclTree
{
  int indentation = 0;
  static int indentWidth = 4;

  public abstract void appendTo(StringBuilder sb);

  /**
   * Append the body to the StringBuilder inside
   * curly braces.
   * @param sb
   */
  public void appendToAsBlock(StringBuilder sb) {
    sb.append("{\n");
    increaseIndent();
    appendTo(sb);
    decreaseIndent();
    indent(sb);
    sb.append("}");
  }

  public void indent(StringBuilder sb)
  {
    sb.append(StringUtils.repeat(' ', indentation));
  }

  public void setIndentation(int i)
  {
    indentation = i;
  }

  public void increaseIndent()
  {
    indentation += indentWidth;
  }

  public void decreaseIndent()
  {
    indentation -= indentWidth;
  }

  @Override
  public String toString()
  {
    StringBuilder sb = new StringBuilder(2048);
    appendTo(sb);
    return sb.toString();
  }
}
