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

import java.util.List;
import java.util.Set;

import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.util.StringUtil;

public class Proc extends TclTree
{
  String name;
  List<String> args;
  Sequence body;

  /**
   *
   * @param name
   * @param usedFunctionNames used to ensure we're not generating duplicate
   *                          functions
   * @param args
   * @param body
   */
  public Proc(String name, Set<String> usedFunctionNames,
                          List<String> args, Sequence body)
  {
    assert(!usedFunctionNames.contains(name));
    checkTclFunctionName(name);
    usedFunctionNames.add(name);
    this.name = name;
    this.args = args;
    this.body = body;
  }

  public Proc(String name, Set<String> usedFunctionNames,
      List<String> args) {
    this(name, usedFunctionNames, args, new Sequence());
  }

  public String name() {
    return name;
  }

  public Sequence getBody() {
    return body;
  }

  /**
   * Check that there are no invalid characters
   */
  private static void checkTclFunctionName(String name) {
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if (Character.isLetter(c) || Character.isDigit(c) ||
          c == ':' || c == '_' || c == '=' || c == '-' ||
          c == '<' || c == '>') {
        // Whitelist of characters
      } else {
        throw new STCRuntimeError("Bad character '" + c +
                                  "' in tcl function name " + name);
      }
    }
  }

  @Override
  public void appendTo(StringBuilder sb)
  {
    indent(sb);
    sb.append("\nproc ");
    sb.append(name);
    sb.append(" { ");
    sb.append(StringUtil.concat(args));
    sb.append(" } {\n");
    body.setIndentation(indentation+indentWidth);
    body.appendTo(sb);
    sb.append("}\n\n");
  }
}
